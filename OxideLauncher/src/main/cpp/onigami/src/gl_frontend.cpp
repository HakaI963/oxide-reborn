// ONIGAMI GL frontend: null-safe queries, state coalescing, shader hook.
// Own implementation against NDK <GLES3/gl3.h>. Policy:
// - Queries: NULL (never "") with no current context; live backend answers
//   otherwise; GL_VERSION carries the documented ONIGAMI-translated mapping.
// - Hot state (UseProgram, binds, caps, viewport): redundant calls skipped.
// - Shader/program: source is translated to ESSL (with vault reuse) before
//   reaching the driver. Translate failure => never submitted, honest log.
// - Long-tail entries (blend equations, stencil, multisample, mapping,
//   instanced/multi-draw, samplers, 3D textures, queries, sync): honest
//   dlsym passthrough, no invented behavior.
#include "onigami/backend.h"
#include "onigami/translate.h"
#include "onigami/vault.h"
#include "state_cache.h"
#include "onigami/config.h"
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <sys/stat.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#if defined(__GNUC__) || defined(__clang__)
#define O_API __attribute__((visibility("default")))
#else
#define O_API
#endif
namespace {
std::mutex g_sh_m;
struct ShRec {
    GLenum type = 0;
    std::string src;
    bool compiled = false;
};
std::unordered_map<GLuint, ShRec> g_shaders;
// Desktop-only capability enums that are unconditionally true in ES 3.x:
// 0x884F GL_TEXTURE_CUBE_MAP_SEAMLESS (cube maps are always seamlessly
// filtered in ES; the enable is a no-op even on desktop GL 3.2+) and 0x8642
// GL_PROGRAM_POINT_SIZE (point size always comes from the shader in ES and
// desktop core). Forwarding them makes the backend raise INVALID_ENUM for a
// call that changes nothing, so they are skipped with a once-per-entry log.
bool desktop_noop_cap(GLenum c) { return c == 0x884Fu || c == 0x8642u; }
void log_noop_cap_once(const char* fn, GLenum c) {
    static bool logged_seamless = false;
    static bool logged_ppoint = false;
    bool* flag = (c == 0x884Fu) ? &logged_seamless : &logged_ppoint;
    if (!*flag) {
        *flag = true;
        onigami::diag_printf("onigami: skipped always-on cap");
    }
}
bool is_proxy_target(GLenum t) { return t == 0x8063u || t == 0x8064u; }
bool backend_has_extension(const char* ext) {
    static std::mutex m;
    static std::unordered_map<std::string, bool> cache;
    std::lock_guard<std::mutex> l(m);
    auto it = cache.find(ext);
    if (it != cache.end()) return it->second;
    bool found = false;
    if (onigami::ensure_gles_loaded() && onigami::gles_procs().GetIntegerv &&
        onigami::gles_procs().GetStringi) {
        GLint n = 0;
        onigami::gles_procs().GetIntegerv(GL_NUM_EXTENSIONS, &n);
        for (GLint i = 0; i < n && i < 4096; i++) {
            const char* e = (const char*)onigami::gles_procs().GetStringi(GL_EXTENSIONS, (GLuint)i);
            if (e && std::strcmp(e, ext) == 0) { found = true; break; }
        }
    }
    cache[ext] = found;
    return found;
}
bool has_current_context() {
    if (!onigami::ensure_egl_loaded() || !onigami::egl_procs().GetCurrentContext) return false;
    return onigami::egl_procs().GetCurrentContext() != EGL_NO_CONTEXT;
}
template <typename F>
F gsym(const char* n) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().handle) return nullptr;
    return (F)dlsym(onigami::gles_procs().handle, n);
}
std::string first_line(const char* s) {
    if (!s) return "(null)";
    std::string out;
    for (const char* p = s; *p && *p != '\n' && out.size() < 160; p++) {
        if (*p != '\r') out += *p;
    }
    return out;
}
// Device evidence: original + translated source + backend verdict, keyed by
// the vault key so a failing shader can be identified from the launch log
// alone. Bounded (300 files) and diagnostics-gated.
void write_shader_capture(const std::string& key, const char* src, const char* essl, const char* log) {
    if (!onigami::current_config().diagnostics) return;
    std::string dir = onigami::data_dir() + "/shaders";
    mkdir(dir.c_str(), 0755);
    int count = 0;
    if (DIR* d = opendir(dir.c_str())) {
        while (readdir(d)) {
            if (++count > 300) break;
        }
        closedir(d);
        if (count > 300) return;
    }
    auto write = [&](const char* suffix, const char* data) {
        if (!data) return;
        std::string p = dir + "/" + key + suffix;
        if (FILE* f = fopen(p.c_str(), "wb")) {
            fwrite(data, 1, strlen(data), f);
            fclose(f);
        }
    };
    write(".orig.glsl", src);
    write(".essl.glsl", essl);
    write(".compile.log", log);
}
} // namespace
extern "C" {
// ---- honest queries ----
O_API const GLubyte* glGetString(GLenum name) {
    if (!has_current_context()) return nullptr;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetString) return nullptr;
    // Opportunistic: SDL may drive system EGL directly, so our wrapped
    // eglMakeCurrent (which also probes) is not guaranteed to run.
    onigami::try_probe_renderer_version();
    if (name == GL_VERSION) {
        const char* live = (const char*)onigami::gles_procs().GetString(GL_VERSION);
        if (!live || !live[0]) return nullptr;
        static thread_local std::string mapped;
        std::string b(live);
        if (b.find("3.2") != std::string::npos)
            mapped = "4.6 (Compatibility Profile) ONIGAMI translated ES3.2 (backend: " + b + ")";
        else
            mapped = "3.3 (Compatibility Profile) ONIGAMI translated (backend: " + b + ")";
        return (const GLubyte*)mapped.c_str();
    }
    return onigami::gles_procs().GetString(name);
}
O_API const GLubyte* glGetStringi(GLenum n, GLuint i) {
    if (!has_current_context()) return nullptr;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetStringi) return nullptr;
    return onigami::gles_procs().GetStringi(n, i);
}
O_API GLenum glGetError(void) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetError) return GL_NO_ERROR;
    return onigami::gles_procs().GetError();
}
O_API void glGetIntegerv(GLenum p, GLint* v) {
    if (!v) {
        onigami::state_cache().note_skip();
        return;
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetIntegerv) return;
    onigami::gles_procs().GetIntegerv(p, v);
}
O_API void glGetFloatv(GLenum p, GLfloat* v) {
    if (!v) {
        onigami::state_cache().note_skip();
        return;
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetFloatv) return;
    onigami::gles_procs().GetFloatv(p, v);
}
O_API void glGetBooleanv(GLenum p, GLboolean* v) {
    if (!v) {
        onigami::state_cache().note_skip();
        return;
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetBooleanv) return;
    onigami::gles_procs().GetBooleanv(p, v);
}
// ---- coalesced state ----
O_API void glUseProgram(GLuint p) {
    if (onigami::state_cache().check_use_program(p)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().UseProgram) return;
    onigami::gles_procs().UseProgram(p);
}
O_API void glBindBuffer(GLenum t, GLuint b) {
    if (onigami::state_cache().check_bind_buffer(t, b)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BindBuffer) return;
    onigami::gles_procs().BindBuffer(t, b);
}
O_API void glBindTexture(GLenum t, GLuint x) {
    if (onigami::state_cache().check_bind_texture(t, x)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BindTexture) return;
    onigami::gles_procs().BindTexture(t, x);
}
O_API void glActiveTexture(GLenum t) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().ActiveTexture) return;
    onigami::gles_procs().ActiveTexture(t);
}
O_API void glBindFramebuffer(GLenum t, GLuint f) {
    if (onigami::state_cache().check_bind_framebuffer(f)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BindFramebuffer) return;
    onigami::gles_procs().BindFramebuffer(t, f);
}
O_API void glBindRenderbuffer(GLenum t, GLuint r) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BindRenderbuffer) return;
    onigami::gles_procs().BindRenderbuffer(t, r);
}
O_API void glBindVertexArray(GLuint v) {
    if (onigami::state_cache().check_bind_vertex_array(v)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BindVertexArray) return;
    onigami::gles_procs().BindVertexArray(v);
}
O_API void glEnable(GLenum c) {
    if (desktop_noop_cap(c)) { log_noop_cap_once("glEnable", c); return; }
    if (onigami::state_cache().check_cap(c, true)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Enable) return;
    onigami::gles_procs().Enable(c);
}
O_API void glDisable(GLenum c) {
    if (desktop_noop_cap(c)) { log_noop_cap_once("glDisable", c); return; }
    if (onigami::state_cache().check_cap(c, false)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Disable) return;
    onigami::gles_procs().Disable(c);
}
O_API GLboolean glIsEnabled(GLenum c) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().IsEnabled) return GL_FALSE;
    if (desktop_noop_cap(c)) return GL_TRUE;
    return onigami::gles_procs().IsEnabled(c);
}
O_API void glViewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    if (onigami::state_cache().check_viewport(x, y, w, h)) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Viewport) return;
    onigami::gles_procs().Viewport(x, y, w, h);
}
O_API void glScissor(GLint x, GLint y, GLsizei w, GLsizei h) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Scissor) return;
    onigami::gles_procs().Scissor(x, y, w, h);
}
// ---- shader/program with translation + vault ----
O_API GLuint glCreateShader(GLenum t) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().CreateShader) return 0;
    GLuint s = onigami::gles_procs().CreateShader(t);
    if (s) {
        std::lock_guard<std::mutex> l(g_sh_m);
        g_shaders[s].type = t;
    }
    return s;
}
O_API void glShaderSource(GLuint s, GLsizei n, const GLchar* const* str, const GLint* len) {
    if (!s || !str) {
        onigami::state_cache().note_skip();
        return;
    }
    std::string cat;
    for (GLsizei i = 0; i < n; i++) {
        if (!str[i]) continue;
        if (len && len[i] >= 0)
            cat.append(str[i], (size_t)len[i]);
        else
            cat += str[i];
    }
    std::lock_guard<std::mutex> l(g_sh_m);
    g_shaders[s].src = cat;
}
O_API void glCompileShader(GLuint s) {
    if (!s) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().ShaderSource ||
        !onigami::gles_procs().CompileShader || !onigami::gles_procs().GetShaderiv)
        return;
    std::string src;
    GLenum type = 0;
    {
        std::lock_guard<std::mutex> l(g_sh_m);
        auto it = g_shaders.find(s);
        if (it == g_shaders.end()) return;
        src = it->second.src;
        type = it->second.type;
    }
    // Vault first: identical source reuses translated ESSL without rework.
    std::string key = onigami::vault_key_hex(src.c_str(), type == GL_VERTEX_SHADER ? "vs" : (type == GL_FRAGMENT_SHADER ? "fs" : "cs"));
    std::string essl;
    std::vector<char> hit;
    bool from_vault = false;
    if (onigami::vault_lookup(key, hit) && !hit.empty()) {
        essl.assign(hit.begin(), hit.end());
        from_vault = true;
    } else {
        onigami::TranslateResult tr = onigami::translate_shader(type, src.c_str());
        if (!tr.ok) {
            onigami::diag_printf("onigami: translate failed shader=%u: %s", s, tr.log.c_str());
            return; // never submitted; backend shader stays uncompiled (honest failure)
        }
        essl = tr.essl;
        if (tr.needsTexBufferExt) {
            bool present = backend_has_extension("GL_EXT_texture_buffer");
            onigami::diag_printf("onigami: texture-buffer directive emitted; backend advertises ext: %s", present ? "yes" : "NO-unsupported");
        }
        onigami::vault_store(key, essl.data(), essl.size());
    }
    const char* p = essl.c_str();
    onigami::gles_procs().ShaderSource(s, 1, &p, nullptr);
    onigami::gles_procs().CompileShader(s);
    GLint st = 0;
    onigami::gles_procs().GetShaderiv(s, GL_COMPILE_STATUS, &st);
    {
        std::lock_guard<std::mutex> l(g_sh_m);
        g_shaders[s].compiled = (st != 0);
    }
    // Backend verdict: Minecraft reads it via glGetShaderInfoLog (below);
    // ONIGAMI also records it so the device log shows the real verdict.
    std::string info;
    if (onigami::gles_procs().GetShaderInfoLog) {
        GLint need = 0;
        onigami::gles_procs().GetShaderiv(s, GL_INFO_LOG_LENGTH, &need);
        if (need > 1) {
            std::vector<char> buf((size_t)need);
            GLsizei got = 0;
            onigami::gles_procs().GetShaderInfoLog(s, need, &got, buf.data());
            if (got > 0) info.assign(buf.data(), (size_t)got);
        }
    }
    write_shader_capture(key, src.c_str(), essl.c_str(), info.empty() ? nullptr : info.c_str());
    onigami::diag_printf("onigami: compile shader=%u type=%s %s vault=%d srcline=%s", s,
        type == GL_VERTEX_SHADER ? "vs" : (type == GL_FRAGMENT_SHADER ? "fs" : "other"),
        st ? "ok" : "backend-fail", (int)from_vault, first_line(src.c_str()).c_str());
    if (!st && !info.empty())
        onigami::diag_printf("onigami: shader=%u backend log: %s", s, info.substr(0, 1800).c_str());
}
O_API void glGetShaderiv(GLuint s, GLenum p, GLint* v) {
    if (!v) {
        onigami::state_cache().note_skip();
        return;
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetShaderiv) return;
    onigami::gles_procs().GetShaderiv(s, p, v);
}
O_API void glGetShaderInfoLog(GLuint s, GLsizei m, GLsizei* l, GLchar* b) {
    if (!b && !l) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetShaderInfoLog) return;
    onigami::gles_procs().GetShaderInfoLog(s, m, l, b);
}
O_API void glDeleteShader(GLuint s) {
    {
        std::lock_guard<std::mutex> l(g_sh_m);
        g_shaders.erase(s);
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DeleteShader) return;
    onigami::gles_procs().DeleteShader(s);
}
O_API GLboolean glIsShader(GLuint s) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().IsShader) return GL_FALSE;
    return onigami::gles_procs().IsShader(s);
}
O_API GLuint glCreateProgram(void) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().CreateProgram) return 0;
    return onigami::gles_procs().CreateProgram();
}
O_API void glAttachShader(GLuint p, GLuint s) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().AttachShader) return;
    onigami::gles_procs().AttachShader(p, s);
}
O_API void glDetachShader(GLuint p, GLuint s) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DetachShader) return;
    onigami::gles_procs().DetachShader(p, s);
}
O_API void glLinkProgram(GLuint p) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().LinkProgram) return;
    onigami::gles_procs().LinkProgram(p);
    if (onigami::gles_procs().GetProgramiv && onigami::gles_procs().GetProgramInfoLog) {
        GLint st = 0;
        onigami::gles_procs().GetProgramiv(p, GL_LINK_STATUS, &st);
        if (!st) {
            GLint need = 0;
            onigami::gles_procs().GetProgramiv(p, GL_INFO_LOG_LENGTH, &need);
            std::string info;
            if (need > 1) {
                std::vector<char> buf((size_t)need);
                GLsizei got = 0;
                onigami::gles_procs().GetProgramInfoLog(p, need, &got, buf.data());
                if (got > 0) info.assign(buf.data(), (size_t)got);
            }
            onigami::diag_printf("onigami: link program=%u backend-fail log: %s", p,
                info.substr(0, 1800).c_str());
        }
    }
}
O_API void glGetProgramiv(GLuint p, GLenum q, GLint* v) {
    if (!v) {
        onigami::state_cache().note_skip();
        return;
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetProgramiv) return;
    onigami::gles_procs().GetProgramiv(p, q, v);
}
O_API void glGetProgramInfoLog(GLuint p, GLsizei m, GLsizei* l, GLchar* b) {
    if (!b && !l) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetProgramInfoLog) return;
    onigami::gles_procs().GetProgramInfoLog(p, m, l, b);
}
O_API void glDeleteProgram(GLuint p) {
    onigami::state_cache().invalidate();
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DeleteProgram) return;
    onigami::gles_procs().DeleteProgram(p);
}
O_API GLboolean glIsProgram(GLuint p) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().IsProgram) return GL_FALSE;
    return onigami::gles_procs().IsProgram(p);
}
O_API GLint glGetUniformLocation(GLuint p, const GLchar* n) {
    if (!n) return -1;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetUniformLocation) return -1;
    return onigami::gles_procs().GetUniformLocation(p, n);
}
O_API GLint glGetAttribLocation(GLuint p, const GLchar* n) {
    if (!n) return -1;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GetAttribLocation) return -1;
    return onigami::gles_procs().GetAttribLocation(p, n);
}
O_API void glUniform1i(GLint l, GLint v) {
    if (l < 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Uniform1i) return;
    onigami::gles_procs().Uniform1i(l, v);
}
O_API void glUniform1f(GLint l, GLfloat v) {
    if (l < 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Uniform1f) return;
    onigami::gles_procs().Uniform1f(l, v);
}
O_API void glUniform4fv(GLint l, GLsizei c, const GLfloat* v) {
    if (l < 0 || !v) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Uniform4fv) return;
    onigami::gles_procs().Uniform4fv(l, c, v);
}
O_API void glUniformMatrix4fv(GLint l, GLsizei c, GLboolean t, const GLfloat* v) {
    if (l < 0 || !v) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().UniformMatrix4fv) return;
    onigami::gles_procs().UniformMatrix4fv(l, c, t, v);
}
// ---- resources ----
O_API void glGenBuffers(GLsizei n, GLuint* b) {
    if (!b || n <= 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenBuffers) return;
    onigami::gles_procs().GenBuffers(n, b);
}
O_API void glBufferData(GLenum t, GLsizeiptr s, const void* d, GLenum u) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BufferData) return;
    onigami::gles_procs().BufferData(t, s, d, u);
}
O_API void glBufferSubData(GLenum t, GLintptr o, GLsizeiptr s, const void* d) {
    if (!d) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BufferSubData) return;
    onigami::gles_procs().BufferSubData(t, o, s, d);
}
O_API void glGenVertexArrays(GLsizei n, GLuint* a) {
    if (!a || n <= 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenVertexArrays) return;
    onigami::gles_procs().GenVertexArrays(n, a);
}
O_API void glGenTextures(GLsizei n, GLuint* t) {
    if (!t || n <= 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenTextures) return;
    onigami::gles_procs().GenTextures(n, t);
}
O_API void glTexParameteri(GLenum t, GLenum p, GLint v) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().TexParameteri) return;
    onigami::gles_procs().TexParameteri(t, p, v);
}
O_API void glTexImage2D(GLenum t, GLint l, GLint i, GLsizei w, GLsizei h, GLint b, GLenum f, GLenum y, const void* d) {
    if (is_proxy_target(t)) {
        static bool logged = false;
        if (!logged) { logged = true;
            onigami::diag_printf("onigami: proxy texture target seen; forwarded unchanged");
        }
    }
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().TexImage2D) return;
    onigami::gles_procs().TexImage2D(t, l, i, w, h, b, f, y, d);
}
O_API void glTexSubImage2D(GLenum t, GLint l, GLint x, GLint y, GLsizei w, GLsizei h, GLenum f, GLenum e, const void* d) {
    if (!d) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().TexSubImage2D) return;
    onigami::gles_procs().TexSubImage2D(t, l, x, y, w, h, f, e, d);
}
O_API void glGenerateMipmap(GLenum t) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenerateMipmap) return;
    onigami::gles_procs().GenerateMipmap(t);
}
O_API void glGenFramebuffers(GLsizei n, GLuint* f) {
    if (!f || n <= 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenFramebuffers) return;
    onigami::gles_procs().GenFramebuffers(n, f);
}
O_API GLenum glCheckFramebufferStatus(GLenum t) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().CheckFramebufferStatus)
        return GL_FRAMEBUFFER_UNSUPPORTED;
    GLenum st = onigami::gles_procs().CheckFramebufferStatus(t);
    if (st != GL_FRAMEBUFFER_COMPLETE) {
        static GLenum last_t = 0, last_s = 0;
        if (t != last_t || st != last_s) {
            last_t = t; last_s = st;
            onigami::diag_printf("onigami: framebuffer incomplete");
        }
    }
    return st;
}
O_API void glFramebufferTexture2D(GLenum t, GLenum a, GLenum x, GLuint r, GLint l) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().FramebufferTexture2D) return;
    onigami::gles_procs().FramebufferTexture2D(t, a, x, r, l);
}
O_API void glFramebufferRenderbuffer(GLenum t, GLenum a, GLenum r, GLuint b) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().FramebufferRenderbuffer) return;
    onigami::gles_procs().FramebufferRenderbuffer(t, a, r, b);
}
O_API void glGenRenderbuffers(GLsizei n, GLuint* r) {
    if (!r || n <= 0) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().GenRenderbuffers) return;
    onigami::gles_procs().GenRenderbuffers(n, r);
}
O_API void glRenderbufferStorage(GLenum t, GLenum i, GLsizei w, GLsizei h) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().RenderbufferStorage) return;
    onigami::gles_procs().RenderbufferStorage(t, i, w, h);
}
O_API void glBlitFramebuffer(GLint a, GLint b, GLint c, GLint d, GLint e, GLint f, GLint g, GLint h, GLbitfield m, GLenum n) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BlitFramebuffer) return;
    onigami::gles_procs().BlitFramebuffer(a, b, c, d, e, f, g, h, m, n);
}
O_API void glEnableVertexAttribArray(GLuint i) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().EnableVertexAttribArray) return;
    onigami::gles_procs().EnableVertexAttribArray(i);
}
O_API void glDisableVertexAttribArray(GLuint i) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DisableVertexAttribArray) return;
    onigami::gles_procs().DisableVertexAttribArray(i);
}
O_API void glVertexAttribPointer(GLuint i, GLint s, GLenum t, GLboolean n, GLsizei st, const void* p) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().VertexAttribPointer) return;
    onigami::gles_procs().VertexAttribPointer(i, s, t, n, st, p);
}
O_API void glVertexAttrib4f(GLuint i, GLfloat a, GLfloat b, GLfloat c, GLfloat d) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().VertexAttrib4f) return;
    onigami::gles_procs().VertexAttrib4f(i, a, b, c, d);
}
// ---- raster/draw/misc ----
O_API void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().ClearColor) return;
    onigami::gles_procs().ClearColor(r, g, b, a);
}
O_API void glClear(GLbitfield m) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Clear) return;
    onigami::gles_procs().Clear(m);
}
O_API void glBlendFunc(GLenum s, GLenum d) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().BlendFunc) return;
    onigami::gles_procs().BlendFunc(s, d);
}
O_API void glDepthMask(GLboolean v) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DepthMask) return;
    onigami::gles_procs().DepthMask(v);
}
O_API void glColorMask(GLboolean r, GLboolean g, GLboolean b, GLboolean a) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().ColorMask) return;
    onigami::gles_procs().ColorMask(r, g, b, a);
}
O_API void glPixelStorei(GLenum p, GLint v) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().PixelStorei) return;
    onigami::gles_procs().PixelStorei(p, v);
}
O_API void glReadPixels(GLint x, GLint y, GLsizei w, GLsizei h, GLenum f, GLenum t, void* d) {
    if (!d) return;
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().ReadPixels) return;
    onigami::gles_procs().ReadPixels(x, y, w, h, f, t, d);
}
O_API void glDrawArrays(GLenum m, GLint f, GLsizei c) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DrawArrays) return;
    onigami::gles_procs().DrawArrays(m, f, c);
}
O_API void glDrawElements(GLenum m, GLsizei c, GLenum t, const void* p) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DrawElements) return;
    onigami::gles_procs().DrawElements(m, c, t, p);
}
O_API void glDrawArraysInstanced(GLenum m, GLint f, GLsizei c, GLsizei n) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DrawArraysInstanced) return;
    onigami::gles_procs().DrawArraysInstanced(m, f, c, n);
}
O_API void glDrawElementsInstanced(GLenum m, GLsizei c, GLenum t, const void* p, GLsizei n) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().DrawElementsInstanced) return;
    onigami::gles_procs().DrawElementsInstanced(m, c, t, p, n);
}
O_API void glFlush(void) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Flush) return;
    onigami::gles_procs().Flush();
}
O_API void glFinish(void) {
    if (!onigami::ensure_gles_loaded() || !onigami::gles_procs().Finish) return;
    onigami::gles_procs().Finish();
}
// ---- long tail: honest dlsym passthrough, no invented behavior ----
O_API void glDeleteBuffers(GLsizei n, const GLuint* b) {
    onigami::state_cache().invalidate();
    if (!b || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, const GLuint*)>("glDeleteBuffers");
    if (fn) fn(n, b);
}
O_API void glDeleteVertexArrays(GLsizei n, const GLuint* a) {
    onigami::state_cache().invalidate();
    if (!a || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, const GLuint*)>("glDeleteVertexArrays");
    if (fn) fn(n, a);
}
O_API void glDeleteTextures(GLsizei n, const GLuint* t) {
    onigami::state_cache().invalidate();
    if (!t || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, const GLuint*)>("glDeleteTextures");
    if (fn) fn(n, t);
}
O_API void glDeleteFramebuffers(GLsizei n, const GLuint* f) {
    onigami::state_cache().invalidate();
    if (!f || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, const GLuint*)>("glDeleteFramebuffers");
    if (fn) fn(n, f);
}
O_API void glDeleteRenderbuffers(GLsizei n, const GLuint* r) {
    onigami::state_cache().invalidate();
    if (!r || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, const GLuint*)>("glDeleteRenderbuffers");
    if (fn) fn(n, r);
}
O_API void glTexParameterf(GLenum t, GLenum p, GLfloat v) {
    auto fn = gsym<void (*)(GLenum, GLenum, GLfloat)>("glTexParameterf");
    if (fn) fn(t, p, v);
}
O_API void glClearDepthf(GLfloat d) {
    auto fn = gsym<void (*)(GLfloat)>("glClearDepthf");
    if (fn) fn(d);
}
O_API void glBlendFuncSeparate(GLenum s, GLenum dr, GLenum ss, GLenum dd) {
    auto fn = gsym<void (*)(GLenum, GLenum, GLenum, GLenum)>("glBlendFuncSeparate");
    if (fn) fn(s, dr, ss, dd);
}
O_API void glBlendEquation(GLenum m) {
    auto fn = gsym<void (*)(GLenum)>("glBlendEquation");
    if (fn) fn(m);
}
O_API void glBlendEquationSeparate(GLenum a, GLenum b) {
    auto fn = gsym<void (*)(GLenum, GLenum)>("glBlendEquationSeparate");
    if (fn) fn(a, b);
}
O_API void glDepthFunc(GLenum f) {
    auto fn = gsym<void (*)(GLenum)>("glDepthFunc");
    if (fn) fn(f);
}
O_API void glCullFace(GLenum m) {
    auto fn = gsym<void (*)(GLenum)>("glCullFace");
    if (fn) fn(m);
}
O_API void glFrontFace(GLenum m) {
    auto fn = gsym<void (*)(GLenum)>("glFrontFace");
    if (fn) fn(m);
}
O_API void glStencilFunc(GLenum f, GLint r, GLuint m) {
    auto fn = gsym<void (*)(GLenum, GLint, GLuint)>("glStencilFunc");
    if (fn) fn(f, r, m);
}
O_API void glStencilMask(GLuint m) {
    auto fn = gsym<void (*)(GLuint)>("glStencilMask");
    if (fn) fn(m);
}
O_API void glStencilOp(GLenum a, GLenum b, GLenum c) {
    auto fn = gsym<void (*)(GLenum, GLenum, GLenum)>("glStencilOp");
    if (fn) fn(a, b, c);
}
O_API void glDepthRangef(GLfloat n, GLfloat f) {
    auto fn = gsym<void (*)(GLfloat, GLfloat)>("glDepthRangef");
    if (fn) fn(n, f);
}
O_API void glPolygonOffset(GLfloat f, GLfloat u) {
    auto fn = gsym<void (*)(GLfloat, GLfloat)>("glPolygonOffset");
    if (fn) fn(f, u);
}
O_API void glLineWidth(GLfloat w) {
    auto fn = gsym<void (*)(GLfloat)>("glLineWidth");
    if (fn) fn(w);
}
O_API void glVertexAttribDivisor(GLuint i, GLuint d) {
    auto fn = gsym<void (*)(GLuint, GLuint)>("glVertexAttribDivisor");
    if (fn) fn(i, d);
}
O_API void glUniform2f(GLint l, GLfloat a, GLfloat b) {
    if (l < 0) return;
    auto fn = gsym<void (*)(GLint, GLfloat, GLfloat)>("glUniform2f");
    if (fn) fn(l, a, b);
}
O_API void glUniform3f(GLint l, GLfloat a, GLfloat b, GLfloat c) {
    if (l < 0) return;
    auto fn = gsym<void (*)(GLint, GLfloat, GLfloat, GLfloat)>("glUniform3f");
    if (fn) fn(l, a, b, c);
}
O_API void glUniform1fv(GLint l, GLsizei n, const GLfloat* v) {
    if (l < 0 || !v) return;
    auto fn = gsym<void (*)(GLint, GLsizei, const GLfloat*)>("glUniform1fv");
    if (fn) fn(l, n, v);
}
O_API void glUniform3fv(GLint l, GLsizei n, const GLfloat* v) {
    if (l < 0 || !v) return;
    auto fn = gsym<void (*)(GLint, GLsizei, const GLfloat*)>("glUniform3fv");
    if (fn) fn(l, n, v);
}
O_API void glUniform1iv(GLint l, GLsizei n, const GLint* v) {
    if (l < 0 || !v) return;
    auto fn = gsym<void (*)(GLint, GLsizei, const GLint*)>("glUniform1iv");
    if (fn) fn(l, n, v);
}
O_API void glUniformMatrix3fv(GLint l, GLsizei n, GLboolean t, const GLfloat* v) {
    if (l < 0 || !v) return;
    auto fn = gsym<void (*)(GLint, GLsizei, GLboolean, const GLfloat*)>("glUniformMatrix3fv");
    if (fn) fn(l, n, t, v);
}
O_API void glCompressedTexImage2D(GLenum t, GLint l, GLenum i, GLsizei w, GLsizei h, GLint b, GLsizei n, const void* d) {
    auto fn = gsym<void (*)(GLenum, GLint, GLenum, GLsizei, GLsizei, GLint, GLsizei, const void*)>("glCompressedTexImage2D");
    if (fn) fn(t, l, i, w, h, b, n, d);
}
O_API void glDrawRangeElements(GLenum m, GLuint s, GLuint e, GLsizei c, GLenum t, const void* p) {
    auto fn = gsym<void (*)(GLenum, GLuint, GLuint, GLsizei, GLenum, const void*)>("glDrawRangeElements");
    if (fn) fn(m, s, e, c, t, p);
}
O_API void glMultiDrawArrays(GLenum m, const GLint* f, const GLsizei* c, GLsizei n) {
    if (!f || !c || n <= 0) return;
    auto fn = gsym<void (*)(GLenum, const GLint*, const GLsizei*, GLsizei)>("glMultiDrawArrays");
    if (fn) fn(m, f, c, n);
}
O_API void glMultiDrawElements(GLenum m, const GLsizei* c, GLenum t, const void* const* p, GLsizei n) {
    if (!c || !p || n <= 0) return;
    auto fn = gsym<void (*)(GLenum, const GLsizei*, GLenum, const void* const*, GLsizei)>("glMultiDrawElements");
    if (fn) fn(m, c, t, p, n);
}
O_API void glBindAttribLocation(GLuint p, GLuint i, const GLchar* n) {
    if (!n) return;
    auto fn = gsym<void (*)(GLuint, GLuint, const GLchar*)>("glBindAttribLocation");
    if (fn) fn(p, i, n);
}
O_API void glGetActiveUniform(GLuint p, GLuint i, GLsizei m, GLsizei* l, GLint* s, GLenum* t, GLchar* n) {
    auto fn = gsym<void (*)(GLuint, GLuint, GLsizei, GLsizei*, GLint*, GLenum*, GLchar*)>("glGetActiveUniform");
    if (fn) fn(p, i, m, l, s, t, n);
}
O_API void glGetActiveAttrib(GLuint p, GLuint i, GLsizei m, GLsizei* l, GLint* s, GLenum* t, GLchar* n) {
    auto fn = gsym<void (*)(GLuint, GLuint, GLsizei, GLsizei*, GLint*, GLenum*, GLchar*)>("glGetActiveAttrib");
    if (fn) fn(p, i, m, l, s, t, n);
}
O_API void glProgramBinary(GLuint p, GLenum f, const void* b, GLsizei n) {
    if (!b) return;
    auto fn = gsym<void (*)(GLuint, GLenum, const void*, GLsizei)>("glProgramBinary");
    if (fn) fn(p, f, b, n);
}
O_API void glGetProgramBinary(GLuint p, GLsizei m, GLsizei* l, GLenum* f, void* b) {
    auto fn = gsym<void (*)(GLuint, GLsizei, GLsizei*, GLenum*, void*)>("glGetProgramBinary");
    if (fn) fn(p, m, l, f, b);
}
O_API void* glMapBufferRange(GLenum t, GLintptr o, GLsizeiptr s, GLbitfield a) {
    auto fn = gsym<void* (*)(GLenum, GLintptr, GLsizeiptr, GLbitfield)>("glMapBufferRange");
    return fn ? fn(t, o, s, a) : nullptr;
}
O_API GLboolean glUnmapBuffer(GLenum t) {
    auto fn = gsym<GLboolean (*)(GLenum)>("glUnmapBuffer");
    return fn ? fn(t) : GL_FALSE;
}
O_API void glGenSamplers(GLsizei n, GLuint* s) {
    if (!s || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, GLuint*)>("glGenSamplers");
    if (fn) fn(n, s);
}
O_API void glBindSampler(GLuint u, GLuint s) {
    auto fn = gsym<void (*)(GLuint, GLuint)>("glBindSampler");
    if (fn) fn(u, s);
}
O_API void glSamplerParameteri(GLuint s, GLenum p, GLint v) {
    auto fn = gsym<void (*)(GLuint, GLenum, GLint)>("glSamplerParameteri");
    if (fn) fn(s, p, v);
}
O_API void glTexImage3D(GLenum t, GLint l, GLint i, GLsizei w, GLsizei h, GLsizei d, GLint b, GLenum f, GLenum y, const void* p) {
    auto fn = gsym<void (*)(GLenum, GLint, GLint, GLsizei, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*)>("glTexImage3D");
    if (fn) fn(t, l, i, w, h, d, b, f, y, p);
}
O_API void glGenQueries(GLsizei n, GLuint* q) {
    if (!q || n <= 0) return;
    auto fn = gsym<void (*)(GLsizei, GLuint*)>("glGenQueries");
    if (fn) fn(n, q);
}
O_API void glBeginQuery(GLenum t, GLuint q) {
    auto fn = gsym<void (*)(GLenum, GLuint)>("glBeginQuery");
    if (fn) fn(t, q);
}
O_API void glEndQuery(GLenum t) {
    auto fn = gsym<void (*)(GLenum)>("glEndQuery");
    if (fn) fn(t);
}
O_API GLsync glFenceSync(GLenum c, GLbitfield f) {
    auto fn = gsym<GLsync (*)(GLenum, GLbitfield)>("glFenceSync");
    return fn ? fn(c, f) : nullptr;
}
O_API GLenum glClientWaitSync(GLsync s, GLbitfield f, GLuint64 t) {
    if (!s) return GL_WAIT_FAILED;
    auto fn = gsym<GLenum (*)(GLsync, GLbitfield, GLuint64)>("glClientWaitSync");
    return fn ? fn(s, f, t) : GL_WAIT_FAILED;
}
O_API void glDeleteSync(GLsync s) {
    if (!s) return;
    auto fn = gsym<void (*)(GLsync)>("glDeleteSync");
    if (fn) fn(s);
}
} // extern "C"
