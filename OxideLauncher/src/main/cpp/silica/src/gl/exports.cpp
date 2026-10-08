// Silica GLES exports. Own implementation against NDK <GLES3/gl3.h>.
// Policy: forward everything to the backend driver; add Silica value only where
// it is real today:
// - glGetString/glGetStringi: null-safe via the probe cache + log (5974f49 core,
//   rewritten). A null backend answer with no current context used to crash the
//   caller in strlen(nullptr); Silica answers from the cache instead.
// - hot state calls (UseProgram, binds, caps, viewport, active unit): redundant
//   calls are skipped before reaching the driver (counted, never silent in
//   diagnostics mode).
// - eglGetProcAddress: Silica's own exports first (via our own handle), then the
//   backend for the long tail (TEMPORARY compat: unwrapped calls bypass state
//   tracking but still render; the wrapped set grows with profiling).
#include "silica/driver.h"
#include "silica/probe.h"
#include "silica/state_in.h"
#include "silica/shader_pipe.h"
#include "silica/shim_config.h"
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <android/log.h>
#if defined(__GNUC__) || defined(__clang__)
#define S_API __attribute__((visibility("default")))
#else
#define S_API
#endif
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
#define SG_INIT() do { silica::config::load_once(); silica::driver::ensure(); } while (0)
template <typename F>
static F bg(const char* n) { return (F)silica::driver::resolve(n); }
extern "C" {
// ---- null-safe queries (5974f49 core behavior, own code) ----
S_API const GLubyte* glGetString(GLenum name) {
    SG_INIT();
    typedef const GLubyte* (*F)(GLenum);
    auto f = bg<F>("glGetString");
    const GLubyte* live = f ? f(name) : nullptr;
    if (live) {
        // Live answer: feed the probe cache too, so later no-context queries
        // inherit real driver facts even if no Silica-wrapped makeCurrent ran.
        if (name == GL_RENDERER || name == GL_VERSION) silica::note_probe(
            name == GL_RENDERER ? (const char*)live : nullptr,
            name == GL_VERSION ? (const char*)live : nullptr);
        return live;
    }
    // No context current (or backend refused): answer from the probe cache.
    const char* what = (name == GL_RENDERER) ? "RENDERER" : (name == GL_VERSION) ? "VERSION" : "OTHER";
    SLOG(ERROR, "silica: glGetString(%s) backend returned null (no current context); answering from probe cache", what);
    if (name == GL_RENDERER) return (const GLubyte*)silica::probe_renderer();
    if (name == GL_VERSION) return (const GLubyte*)silica::probe_version();
    static const GLubyte empty = 0;
    return &empty;
}
S_API const GLubyte* glGetStringi(GLenum name, GLuint index) {
    SG_INIT();
    typedef const GLubyte* (*F)(GLenum, GLuint);
    auto f = bg<F>("glGetStringi");
    const GLubyte* live = f ? f(name, index) : nullptr;
    if (live) return live;
    SLOG(ERROR, "silica: glGetStringi backend returned null; answering empty (extension queries need a current context)");
    static const GLubyte empty = 0;
    return &empty;
}
S_API GLenum glGetError(void) {
    SG_INIT();
    auto f = bg<GLenum (*)(void)>("glGetError");
    return f ? f() : (GLenum)GL_NO_ERROR;
}
S_API void glGetIntegerv(GLenum p, GLint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLint*)>("glGetIntegerv");
    if (f) f(p, v);
}
// ---- state-dedup wrappers (real, counted) ----
S_API void glUseProgram(GLuint p) {
    SG_INIT();
    if (!silica::state::use_program(p)) return;
    auto f = bg<void (*)(GLuint)>("glUseProgram");
    if (f) f(p);
}
S_API void glActiveTexture(GLenum t) {
    SG_INIT();
    silica::state::active_unit(t);
    auto f = bg<void (*)(GLenum)>("glActiveTexture");
    if (f) f(t);
}
S_API void glBindTexture(GLenum target, GLuint id) {
    SG_INIT();
    if (!silica::state::bind_texture(silica::state::current_unit(), target, id)) return;
    auto f = bg<void (*)(GLenum, GLuint)>("glBindTexture");
    if (f) f(target, id);
}
S_API void glBindBuffer(GLenum t, GLuint id) {
    SG_INIT();
    if (!silica::state::bind_buffer(t, id)) return;
    auto f = bg<void (*)(GLenum, GLuint)>("glBindBuffer");
    if (f) f(t, id);
}
S_API void glBindFramebuffer(GLenum t, GLuint id) {
    SG_INIT();
    if (!silica::state::bind_framebuffer(t, id)) return;
    auto f = bg<void (*)(GLenum, GLuint)>("glBindFramebuffer");
    if (f) f(t, id);
}
S_API void glBindRenderbuffer(GLenum t, GLuint id) {
    SG_INIT();
    if (!silica::state::bind_renderbuffer(t, id)) return;
    auto f = bg<void (*)(GLenum, GLuint)>("glBindRenderbuffer");
    if (f) f(t, id);
}
S_API void glBindVertexArray(GLuint id) {
    SG_INIT();
    if (!silica::state::bind_vertex_array(id)) return;
    auto f = bg<void (*)(GLuint)>("glBindVertexArray");
    if (f) f(id);
}
S_API void glEnable(GLenum c) {
    SG_INIT();
    if (!silica::state::cap(c, true)) return;
    auto f = bg<void (*)(GLenum)>("glEnable");
    if (f) f(c);
}
S_API void glDisable(GLenum c) {
    SG_INIT();
    if (!silica::state::cap(c, false)) return;
    auto f = bg<void (*)(GLenum)>("glDisable");
    if (f) f(c);
}
S_API void glViewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    SG_INIT();
    if (!silica::state::viewport(x, y, w, h)) return;
    auto f = bg<void (*)(GLint, GLint, GLsizei, GLsizei)>("glViewport");
    if (f) f(x, y, w, h);
}
// ---- pure forwarders (macro table) ----
#define SG_FWD_V0(name) S_API void name(void){ SG_INIT(); auto f = bg<void(*)(void)>(#name); if(f) f(); }
#define SG_FWD_V1E(name) S_API void name(GLenum a){ SG_INIT(); auto f = bg<void(*)(GLenum)>(#name); if(f) f(a); }
#define SG_FWD_V1U(name) S_API void name(GLuint a){ SG_INIT(); auto f = bg<void(*)(GLuint)>(#name); if(f) f(a); }
#define SG_FWD_V2EU(name) S_API void name(GLenum a, GLuint b){ SG_INIT(); auto f = bg<void(*)(GLenum,GLuint)>(#name); if(f) f(a,b); }
#define SG_FWD_V2UU(name) S_API void name(GLuint a, GLuint b){ SG_INIT(); auto f = bg<void(*)(GLuint,GLuint)>(#name); if(f) f(a,b); }
#define SG_FWD_GENDEL(name) S_API void name(GLsizei n, GLuint* v){ SG_INIT(); auto f = bg<void(*)(GLsizei,GLuint*)>(#name); if(f) f(n,v); }
#define SG_FWD_RU0(name) S_API GLuint name(void){ SG_INIT(); auto f = bg<GLuint(*)(void)>(#name); return f ? f() : 0; }
SG_FWD_V0(glFlush)
SG_FWD_V1E(glDepthFunc)
SG_FWD_V1E(glCullFace)
SG_FWD_V1E(glFrontFace)
S_API void glDepthMask(GLboolean v) {
    SG_INIT();
    auto f = bg<void (*)(GLboolean)>(__func__);
    if (f) f(v);
}
S_API void glDeleteProgram(GLuint p) {
    SG_INIT();
    silica::shader::delete_program(p);
}
S_API void glDeleteShader(GLuint s) {
    SG_INIT();
    silica::shader::delete_shader(s);
}
S_API void glClearStencil(GLint v) {
    SG_INIT();
    auto f = bg<void (*)(GLint)>(__func__);
    if (f) f(v);
}
S_API void glAttachShader(GLuint p, GLuint s) {
    SG_INIT();
    silica::shader::attach_shader(p, s);
}
S_API void glDetachShader(GLuint p, GLuint s) {
    SG_INIT();
    silica::shader::detach_shader(p, s);
}
SG_FWD_V2EU(glDetachShader)
S_API void glDeleteVertexArrays(GLsizei n, const GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, const GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glGenTextures(GLsizei n, GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glDeleteTextures(GLsizei n, const GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, const GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glGenBuffers(GLsizei n, GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glDeleteBuffers(GLsizei n, const GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, const GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glGenFramebuffers(GLsizei n, GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glDeleteFramebuffers(GLsizei n, const GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, const GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glGenRenderbuffers(GLsizei n, GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glDeleteRenderbuffers(GLsizei n, const GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, const GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API void glGenVertexArrays(GLsizei n, GLuint* v) {
    SG_INIT();
    auto f = bg<void (*)(GLsizei, GLuint*)>(__func__);
    if (f) f(n, v);
}
S_API GLuint glCreateProgram(void) {
    SG_INIT();
    return silica::shader::create_program();
}
S_API GLuint glCreateShader(GLenum t) {
    SG_INIT();
    return silica::shader::create_shader(t);
}
S_API void glShaderSource(GLuint s, GLsizei n, const GLchar* const* src, const GLint* len) {
    SG_INIT();
    silica::shader::shader_source(s, n, src, len);
}
S_API void glCompileShader(GLuint s) {
    SG_INIT();
    silica::shader::compile_shader(s);
}
S_API void glGetShaderiv(GLuint s, GLenum p, GLint* v) {
    SG_INIT();
    silica::shader::get_shaderiv(s, p, v);
}
S_API void glGetShaderInfoLog(GLuint s, GLsizei n, GLsizei* l, GLchar* m) {
    SG_INIT();
    silica::shader::get_shader_info_log(s, n, l, m);
}
S_API void glLinkProgram(GLuint p) {
    SG_INIT();
    silica::shader::link_program(p);
}
S_API void glGetProgramiv(GLuint p, GLenum q, GLint* v) {
    SG_INIT();
    silica::shader::get_programiv(p, q, v);
}
S_API void glGetProgramInfoLog(GLuint p, GLsizei n, GLsizei* l, GLchar* m) {
    SG_INIT();
    silica::shader::get_program_info_log(p, n, l, m);
}
// Program binaries: forwarded so LWJGL resolves them; the vault path inside
// link_program calls the backend directly and never depends on these.
S_API void glProgramBinary(GLuint p, GLenum f, const void* b, GLsizei n) {
    SG_INIT();
    auto fn = bg<void (*)(GLuint, GLenum, const void*, GLsizei)>("glProgramBinary");
    if (fn) fn(p, f, b, n);
}
S_API void glGetProgramBinary(GLuint p, GLsizei n, GLsizei* l, GLenum* f, void* b) {
    SG_INIT();
    auto fn = bg<void (*)(GLuint, GLsizei, GLsizei*, GLenum*, void*)>("glGetProgramBinary");
    if (fn) fn(p, n, l, f, b);
}
S_API GLint glGetUniformLocation(GLuint p, const GLchar* n) {
    SG_INIT();
    auto f = bg<GLint (*)(GLuint, const GLchar*)>("glGetUniformLocation");
    return f ? f(p, n) : -1;
}
S_API void glUniform1i(GLint l, GLint v) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLint)>("glUniform1i");
    if (f) f(l, v);
}
S_API void glUniform1f(GLint l, GLfloat v) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLfloat)>("glUniform1f");
    if (f) f(l, v);
}
S_API void glUniform2f(GLint l, GLfloat a, GLfloat b) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLfloat, GLfloat)>("glUniform2f");
    if (f) f(l, a, b);
}
S_API void glUniform3f(GLint l, GLfloat a, GLfloat b, GLfloat c) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLfloat, GLfloat, GLfloat)>("glUniform3f");
    if (f) f(l, a, b, c);
}
S_API void glUniform4f(GLint l, GLfloat a, GLfloat b, GLfloat c, GLfloat d) {
    SG_INIT();
    auto fn = bg<void (*)(GLint, GLfloat, GLfloat, GLfloat, GLfloat)>("glUniform4f");
    if (fn) fn(l, a, b, c, d);
}
S_API void glUniform1fv(GLint l, GLsizei n, const GLfloat* v) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLsizei, const GLfloat*)>("glUniform1fv");
    if (f) f(l, n, v);
}
S_API void glUniformMatrix4fv(GLint l, GLsizei n, GLboolean t, const GLfloat* v) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLsizei, GLboolean, const GLfloat*)>("glUniformMatrix4fv");
    if (f) f(l, n, t, v);
}
S_API void glVertexAttribPointer(GLuint i, GLint s, GLenum t, GLboolean n, GLsizei st, const void* p) {
    SG_INIT();
    auto f = bg<void (*)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*)>("glVertexAttribPointer");
    if (f) f(i, s, t, n, st, p);
}
S_API void glEnableVertexAttribArray(GLuint i) {
    SG_INIT();
    auto f = bg<void (*)(GLuint)>("glEnableVertexAttribArray");
    if (f) f(i);
}
S_API void glDisableVertexAttribArray(GLuint i) {
    SG_INIT();
    auto f = bg<void (*)(GLuint)>("glDisableVertexAttribArray");
    if (f) f(i);
}
S_API void glVertexAttribDivisor(GLuint i, GLuint d) {
    SG_INIT();
    auto f = bg<void (*)(GLuint, GLuint)>("glVertexAttribDivisor");
    if (f) f(i, d);
}
S_API void glDrawArrays(GLenum m, GLint first, GLsizei c) {
    SG_INIT();
    auto fn = bg<void (*)(GLenum, GLint, GLsizei)>("glDrawArrays");
    if (fn) fn(m, first, c);
}
S_API void glDrawElements(GLenum m, GLsizei c, GLenum t, const void* p) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLsizei, GLenum, const void*)>("glDrawElements");
    if (f) f(m, c, t, p);
}
S_API void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) {
    SG_INIT();
    auto f = bg<void (*)(GLfloat, GLfloat, GLfloat, GLfloat)>("glClearColor");
    if (f) f(r, g, b, a);
}
S_API void glClear(GLbitfield m) {
    SG_INIT();
    auto f = bg<void (*)(GLbitfield)>("glClear");
    if (f) f(m);
}
S_API void glClearDepthf(GLfloat d) {
    SG_INIT();
    auto f = bg<void (*)(GLfloat)>("glClearDepthf");
    if (f) f(d);
}
S_API void glBlendFunc(GLenum s, GLenum d) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum)>("glBlendFunc");
    if (f) f(s, d);
}
S_API void glBlendFuncSeparate(GLenum s, GLenum dr, GLenum ss, GLenum dd) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLenum, GLenum)>("glBlendFuncSeparate");
    if (f) f(s, dr, ss, dd);
}
S_API void glPixelStorei(GLenum p, GLint v) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLint)>("glPixelStorei");
    if (f) f(p, v);
}
S_API void glTexParameteri(GLenum t, GLenum p, GLint v) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLint)>("glTexParameteri");
    if (f) f(t, p, v);
}
S_API void glTexParameterf(GLenum t, GLenum p, GLfloat v) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLfloat)>("glTexParameterf");
    if (f) f(t, p, v);
}
S_API void glTexImage2D(GLenum t, GLint l, GLint in, GLsizei w, GLsizei h, GLint b, GLenum f, GLenum ty, const void* p) {
    SG_INIT();
    auto fn = bg<void (*)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*)>("glTexImage2D");
    if (fn) fn(t, l, in, w, h, b, f, ty, p);
}
S_API void glTexSubImage2D(GLenum t, GLint l, GLint x, GLint y, GLsizei w, GLsizei h, GLenum f, GLenum ty, const void* p) {
    SG_INIT();
    auto fn = bg<void (*)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, const void*)>("glTexSubImage2D");
    if (fn) fn(t, l, x, y, w, h, f, ty, p);
}
S_API void glCompressedTexImage2D(GLenum t, GLint l, GLenum in, GLsizei w, GLsizei h, GLint b, GLsizei n, const void* p) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLint, GLenum, GLsizei, GLsizei, GLint, GLsizei, const void*)>("glCompressedTexImage2D");
    if (f) f(t, l, in, w, h, b, n, p);
}
S_API void glGenerateMipmap(GLenum t) {
    SG_INIT();
    auto f = bg<void (*)(GLenum)>("glGenerateMipmap");
    if (f) f(t);
}
S_API void glFramebufferTexture2D(GLenum t, GLenum a, GLenum tt, GLuint x, GLint l) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLenum, GLuint, GLint)>("glFramebufferTexture2D");
    if (f) f(t, a, tt, x, l);
}
S_API void glFramebufferRenderbuffer(GLenum t, GLenum a, GLenum r, GLuint b) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLenum, GLuint)>("glFramebufferRenderbuffer");
    if (f) f(t, a, r, b);
}
S_API GLenum glCheckFramebufferStatus(GLenum t) {
    SG_INIT();
    auto f = bg<GLenum (*)(GLenum)>("glCheckFramebufferStatus");
    return f ? f(t) : 0;
}
S_API void glRenderbufferStorage(GLenum t, GLenum in, GLsizei w, GLsizei h) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLenum, GLsizei, GLsizei)>("glRenderbufferStorage");
    if (f) f(t, in, w, h);
}
S_API void glBufferData(GLenum t, GLsizeiptr s, const void* d, GLenum u) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLsizeiptr, const void*, GLenum)>(__func__);
    if (f) f(t, s, d, u);
}
S_API void glBufferSubData(GLenum t, GLintptr o, GLsizeiptr s, const void* d) {
    SG_INIT();
    auto f = bg<void (*)(GLenum, GLintptr, GLsizeiptr, const void*)>(__func__);
    if (f) f(t, o, s, d);
}
S_API void glReadPixels(GLint x, GLint y, GLsizei w, GLsizei h, GLenum f, GLenum t, void* p) {
    SG_INIT();
    auto fn = bg<void (*)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void*)>("glReadPixels");
    if (fn) fn(x, y, w, h, f, t, p);
}
S_API void glScissor(GLint x, GLint y, GLsizei w, GLsizei h) {
    SG_INIT();
    auto f = bg<void (*)(GLint, GLint, GLsizei, GLsizei)>("glScissor");
    if (f) f(x, y, w, h);
}
S_API void glColorMask(GLboolean r, GLboolean g, GLboolean b, GLboolean a) {
    SG_INIT();
    auto f = bg<void (*)(GLboolean, GLboolean, GLboolean, GLboolean)>("glColorMask");
    if (f) f(r, g, b, a);
}
S_API void glDepthRangef(GLfloat n, GLfloat f) {
    SG_INIT();
    auto ff = bg<void (*)(GLfloat, GLfloat)>("glDepthRangef");
    if (ff) ff(n, f);
}
S_API void glFinish(void) {
    SG_INIT();
    auto f = bg<void (*)(void)>("glFinish");
    if (f) f();
}
} // extern C