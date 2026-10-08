// Silica shader translation pipeline. Own design on pinned glslang +
// SPIRV-Cross (no version changes here). Runtime path for every shader:
//
//   Minecraft GLSL -> frontend (version/profile detect) -> glslang -> SPIR-V
//   -> SPIRV-Cross ESSL 310 -> backend glShaderSource/glCompileShader
//
// Policy:
// - Desktop GLSL is translated; ESSL sources pass through untouched.
// - Translated sources are cached in memory by deterministic key: the same
//   source never pays translation twice in a process.
// - Programs consult the persistent vault before linking: a vault hit loads
//   the backend binary and skips compile+link cost; a fresh link stores its
//   binary afterwards. Keys are deterministic across runs.
// - Translation failure never submits garbage: compile status/link status are
//   recorded false with the reason retained, and queries serve it.
// - Diagnostics log hashes, sizes, times, hit/miss and object ids. Full shader
//   source is never logged by default.
// - One process-wide mutex guards records and caches (correct first, fast
//   later). No network, no runtime dependency downloads.
#include "silica/shader_pipe.h"
#include "silica/driver.h"
#include "silica/shim_config.h"
#include "glslang/Public/ShaderLang.h"
#include "glslang/Public/ResourceLimits.h"
#include "SPIRV/GlslangToSpv.h"
#include "spirv_glsl.hpp"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <android/log.h>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::cache {
uint64_t fnv1a64(const void* data, size_t len, uint64_t seed) {
    const unsigned char* p = (const unsigned char*)data;
    uint64_t h = seed ? seed : 1469598103934665603ULL;
    for (size_t i = 0; i < len; i++) {
        h ^= p[i];
        h *= 1099511628211ULL;
    }
    return h;
}
void set_budget_mb(int mb);
long long budget_bytes();
bool vault_get(uint64_t key, uint32_t& format, std::vector<unsigned char>& binary);
void vault_put(uint64_t key, uint32_t format, const void* data, size_t len);
} // namespace silica::cache
namespace silica::shader {
namespace {
std::mutex g_mu;
std::once_flag g_glslang_once;
struct ShaderRec {
    GLenum type = 0;
    std::string orig;
    uint64_t origHash = 0;
    bool esSource = false;      // already ESSL: passthrough
    bool stageSupported = true; // vertex/fragment only for now
    bool translatedOk = false;
    std::string translated; // submitted source (== orig when passthrough)
    std::string transError; // first reason line only
    size_t spvBytes = 0;
    double translateMs = 0;
    double backendMs = 0;
    bool cacheHit = false;
    bool backendCompiled = false; // backend compile was attempted
    bool backendStatus = false;   // backend COMPILE_STATUS
};
struct ProgRec {
    std::vector<GLuint> attached;
    uint64_t key = 0;
    bool linked = false;
    bool linkStatus = false;
    bool vaultHit = false;
    double linkMs = 0;
    std::string linkError;
};
std::unordered_map<GLuint, ShaderRec> g_shaders;
std::unordered_map<GLuint, ProgRec> g_programs;
struct TransCacheEntry {
    std::string translated;
    bool ok = false;
    std::string error;
    size_t spvBytes = 0;
};
std::unordered_map<uint64_t, TransCacheEntry> g_transCache;
template <typename F>
F be(const char* n) { return (F)driver::resolve(n); }
const char* stage_tag(GLenum t) {
    return t == GL_VERTEX_SHADER ? "vs" : t == GL_FRAGMENT_SHADER ? "fs" : "??";
}
// First "#version <n> [profile]" outside comments. Returns 0 when absent.
int detect_version(const std::string& src, bool& isEs) {
    isEs = false;
    size_t i = 0;
    const size_t n = src.size();
    auto skip_ws_comments = [&]() {
        while (i < n) {
            if (isspace((unsigned char)src[i])) { i++; continue; }
            if (src[i] == '/' && i + 1 < n && src[i + 1] == '/') {
                while (i < n && src[i] != '
') i++;
                continue;
            }
            if (src[i] == '/' && i + 1 < n && src[i + 1] == '*') {
                i += 2;
                while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) i++;
                i += 2;
                continue;
            }
            break;
        }
    };
    for (int guard = 0; guard < 4; guard++) {
        skip_ws_comments();
        if (src.compare(i, 8, "#version") == 0) {
            i += 8;
            while (i < n && isspace((unsigned char)src[i])) i++;
            int v = 0;
            while (i < n && isdigit((unsigned char)src[i])) { v = v * 10 + (src[i] - '0'); i++; }
            while (i < n && isspace((unsigned char)src[i]) && src[i] != '
') i++;
            size_t e = i;
            while (e < n && src[e] != '
' && !isspace((unsigned char)src[e])) e++;
            std::string prof = src.substr(i, e - i);
            isEs = (prof == "es");
            return v;
        }
        if (i < n && src[i] == '#') {
            while (i < n && src[i] != '
') i++;
            continue;
        }
        break;
    }
    return 0;
}
std::string first_line(const std::string& s, size_t max = 200) {
    size_t e = s.find('
');
    std::string l = (e == std::string::npos) ? s : s.substr(0, e);
    if (l.size() > max) l.resize(max);
    return l;
}
double now_ms() {
    using clock = std::chrono::steady_clock;
    return std::chrono::duration<double, std::milli>(clock::now().time_since_epoch()).count();
}
// glslang desktop-GLSL -> SPIR-V, then Cross -> ESSL 310. Own pipeline around
// the pinned libraries (not their example code, not another renderer's path).
bool translate_desktop(const std::string& src, EShLanguage stage, int srcVer,
                       std::string& outEssl, size_t& spvBytes, std::string& error,
                       size_t& uniCount, size_t& imgCount) {
    std::call_once(g_glslang_once, []() { glslang::InitializeProcess(); });
    outEssl.clear();
    error.clear();
    uniCount = imgCount = 0;
    glslang::TShader shader(stage);
    const char* texts[1] = {src.c_str()};
    shader.setStrings(texts, 1);
    shader.setEnvInput(glslang::EShSourceGlsl, stage, glslang::EShClientVulkan, 100);
    shader.setEnvClient(glslang::EShClientVulkan, glslang::EShTargetVulkan_1_0);
    shader.setEnvTarget(glslang::EShTargetSpv, glslang::EShTargetSpv_1_0);
    const TBuiltInResource* res = glslang::GetDefaultResources();
    if (!shader.parse(res, 100, false, EShMsgDefault)) {
        error = first_line(shader.getInfoLog());
        if (error.empty()) error = "glslang parse failed";
        return false;
    }
    glslang::TProgram program;
    program.addShader(&shader);
    if (!program.link(EShMsgDefault)) {
        error = first_line(program.getInfoLog());
        if (error.empty()) error = "glslang link failed";
        return false;
    }
    std::vector<uint32_t> spirv;
    glslang::GlslangToSpv(*program.getIntermediate(stage), spirv);
    if (spirv.empty()) {
        error = "empty SPIR-V output";
        return false;
    }
    spvBytes = spirv.size() * 4;
    try {
        spirv_cross::CompilerGLSL cross(std::move(spirv));
        auto opts = cross.get_common_options();
        opts.version = 310;
        opts.es = true;
        opts.fragment.default_float_precision = spirv_cross::CompilerGLSL::Options::Precision::Highp;
        opts.fragment.default_int_precision = spirv_cross::CompilerGLSL::Options::Precision::Highp;
        cross.set_common_options(opts);
        outEssl = cross.compile();
        auto r = cross.get_shader_resources();
        uniCount = r.uniform_buffers.size() + r.storage_buffers.size();
        imgCount = r.sampled_images.size() + r.separate_images.size() + r.separate_samplers.size();
    } catch (const std::exception& e) {
        error = first_line(e.what());
        if (error.empty()) error = "spirv-cross failed";
        return false;
    }
    if (outEssl.empty()) {
        error = "empty ESSL output";
        return false;
    }
    (void)srcVer;
    return true;
}
uint64_t shader_key(GLenum type, const std::string& orig) {
    uint64_t h = cache::fnv1a64("silica-shader-v1", 16, 0);
    h = cache::fnv1a64(&type, sizeof(type), h);
    const char target[] = "essl310";
    h = cache::fnv1a64(target, sizeof(target) - 1, h);
    return cache::fnv1a64(orig.data(), orig.size(), h);
}
uint64_t program_key(const std::vector<uint64_t>& hashes) {
    std::vector<uint64_t> sorted = hashes;
    std::sort(sorted.begin(), sorted.end());
    uint64_t h = cache::fnv1a64("silica-prog-v1", 14, 0);
    const char target[] = "essl310";
    h = cache::fnv1a64(target, sizeof(target) - 1, h);
    for (uint64_t sh : sorted) h = cache::fnv1a64(&sh, sizeof(sh), h);
    return h;
}
void backend_submit_compile(GLuint name, const std::string& essl, ShaderRec& rec) {
    auto pSource = be<void (*)(GLuint, GLsizei, const GLchar* const*, const GLint*)>("glShaderSource");
    auto pCompile = be<void (*)(GLuint)>("glCompileShader");
    auto pGetiv = be<void (*)(GLuint, GLenum, GLint*)>("glGetShaderiv");
    if (!pSource || !pCompile) {
        rec.transError = "backend submit entry missing";
        return;
    }
    const GLchar* texts[1] = {(const GLchar*)essl.c_str()};
    GLint lens[1] = {(GLint)essl.size()};
    double t0 = now_ms();
    pSource(name, 1, texts, lens);
    pCompile(name);
    rec.backendMs = now_ms() - t0;
    rec.backendCompiled = true;
    if (pGetiv) {
        GLint st = 0;
        pGetiv(name, GL_COMPILE_STATUS, &st);
        rec.backendStatus = (st != 0);
    }
}
const char* hex16(char (&buf)[17], uint64_t v) {
    static const char* digits = "0123456789abcdef";
    for (int i = 15; i >= 0; i--) {
        buf[i] = digits[v & 15];
        v >>= 4;
    }
    buf[16] = 0;
    return buf;
}
} // namespace
GLuint create_shader(GLenum type) {
    auto f = be<GLuint (*)(GLenum)>("glCreateShader");
    GLuint name = f ? f(type) : 0;
    if (name) {
        std::lock_guard<std::mutex> l(g_mu);
        ShaderRec& r = g_shaders[name];
        r.type = type;
    }
    return name;
}
void delete_shader(GLuint s) {
    auto f = be<void (*)(GLuint)>("glDeleteShader");
    if (f) f(s);
    std::lock_guard<std::mutex> l(g_mu);
    g_shaders.erase(s);
}
GLuint create_program() {
    auto f = be<GLuint (*)(void)>("glCreateProgram");
    GLuint name = f ? f() : 0;
    if (name) {
        std::lock_guard<std::mutex> l(g_mu);
        g_programs[name];
    }
    return name;
}
void delete_program(GLuint p) {
    auto f = be<void (*)(GLuint)>("glDeleteProgram");
    if (f) f(p);
    std::lock_guard<std::mutex> l(g_mu);
    g_programs.erase(p);
}
void shader_source(GLuint s, GLsizei count, const GLchar* const* str, const GLint* len) {
    std::string cat;
    for (GLsizei i = 0; i < count; i++) {
        if (!str || !str[i]) continue;
        if (len && len[i] >= 0) cat.append(str[i], (size_t)len[i]);
        else cat.append(str[i]);
    }
    std::lock_guard<std::mutex> l(g_mu);
    auto it = g_shaders.find(s);
    if (it == g_shaders.end()) return; // unknown object: nothing recorded
    ShaderRec& r = it->second;
    r.orig = std::move(cat);
    r.origHash = cache::fnv1a64(r.orig.data(), r.orig.size(), 0);
    r.translatedOk = false;
    r.translated.clear();
    r.transError.clear();
    r.backendCompiled = false;
    r.backendStatus = false;
    r.cacheHit = false;
}
void compile_shader(GLuint s) {
    ShaderRec* rec = nullptr;
    {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_shaders.find(s);
        if (it == g_shaders.end()) {
            // Created outside our view (should not happen): compile directly.
            auto f = be<void (*)(GLuint)>("glCompileShader");
            if (f) f(s);
            return;
        }
        rec = &it->second;
    }
    EShLanguage stage = EShLangCount;
    if (rec->type == GL_VERTEX_SHADER) stage = EShLangVertex;
    else if (rec->type == GL_FRAGMENT_SHADER) stage = EShLangFragment;
    if (stage == EShLangCount) {
        // Geometry/tessellation/compute stay on the staged list: forward the
        // original source untranslated and say so once per object.
        backend_submit_compile(s, rec->orig, *rec);
        rec->translatedOk = rec->backendStatus;
        if (!rec->translatedOk) rec->transError = "stage forwarded untranslated (staged work)";
        char hb[17];
        SLOG(INFO, "silica: shader#%u %s orig=%s forwarded-untranslated backend=%.2fms status=%d", s,
             stage_tag(rec->type), hex16(hb, rec->origHash), rec->backendMs, rec->backendStatus ? 1 : 0);
        return;
    }
    bool isEs = false;
    int ver = detect_version(rec->orig, isEs);
    if (isEs) {
        // Already ESSL: passthrough, still recorded for diagnostics uniformity.
        backend_submit_compile(s, rec->orig, *rec);
        rec->translated = rec->orig;
        rec->translatedOk = rec->backendStatus;
        rec->cacheHit = false;
        if (!rec->translatedOk) rec->transError = "backend rejected ESSL source";
        char hb[17];
        SLOG(INFO, "silica: shader#%u %s orig=%s passthrough backend=%.2fms status=%d", s,
             stage_tag(rec->type), hex16(hb, rec->origHash), rec->backendMs, rec->backendStatus ? 1 : 0);
        return;
    }
    uint64_t key = shader_key(rec->type, rec->orig);
    {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_transCache.find(key);
        if (it != g_transCache.end()) {
            const TransCacheEntry& e = it->second;
            rec->translated = e.translated;
            rec->translatedOk = e.ok;
            rec->transError = e.error;
            rec->spvBytes = e.spvBytes;
            rec->cacheHit = true;
            rec->translateMs = 0;
        }
    }
    size_t uniCount = 0, imgCount = 0;
    if (!rec->cacheHit) {
        double t0 = now_ms();
        std::string essl, err;
        size_t spv = 0;
        bool ok = translate_desktop(rec->orig, stage, ver, essl, spv, err, uniCount, imgCount);
        rec->translateMs = now_ms() - t0;
        std::lock_guard<std::mutex> l(g_mu);
        rec->translated = essl;
        rec->translatedOk = ok;
        rec->transError = err;
        rec->spvBytes = spv;
        TransCacheEntry e;
        e.translated = essl;
        e.ok = ok;
        e.error = err;
        e.spvBytes = spv;
        g_transCache[key] = std::move(e);
    }
    char hb[17], tb[17];
    hex16(hb, rec->origHash);
    SLOG(INFO, "silica: shader#%u %s v%d orig=%s trans=%s spv=%zuB gles=%zuB uniforms=%zu images=%zu translate=%.2fms %s%s%s", s,
         stage_tag(rec->type), ver, hb, hex16(tb, cache::fnv1a64(rec->translated.data(), rec->translated.size(), 0)),
         rec->spvBytes, rec->translated.size(), uniCount, imgCount, rec->translateMs,
         rec->cacheHit ? "trans-cache-hit " : "", rec->translatedOk ? "" : "FAILED ",
         rec->translatedOk ? "" : rec->transError.c_str());
    if (!rec->translatedOk) return; // status false retained; no backend submit
    backend_submit_compile(s, rec->translated, *rec);
    SLOG(INFO, "silica: shader#%u backend=%.2fms status=%d", s, rec->backendMs, rec->backendStatus ? 1 : 0);
}
void get_shaderiv(GLuint s, GLenum p, GLint* v) {
    auto f = be<void (*)(GLuint, GLenum, GLint*)>("glGetShaderiv");
    if (!f || !v) return;
    if (p == GL_COMPILE_STATUS) {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_shaders.find(s);
        if (it != g_shaders.end() && (it->second.backendCompiled || !it->second.translatedOk)) {
            // Whole-pipeline status: translation failure is a compile failure
            // even though nothing reached the driver.
            *v = (it->second.translatedOk && it->second.backendStatus) ? 1 : 0;
            return;
        }
    }
    if (p == GL_INFO_LOG_LENGTH) {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_shaders.find(s);
        if (it != g_shaders.end() && !it->second.transError.empty()) {
            *v = (GLint)it->second.transError.size() + 1;
            return;
        }
    }
    f(s, p, v);
}
void get_shader_info_log(GLuint s, GLsizei n, GLsizei* len, GLchar* log) {
    auto f = be<void (*)(GLuint, GLsizei, GLsizei*, GLchar*)>("glGetShaderInfoLog");
    std::string ours;
    {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_shaders.find(s);
        if (it != g_shaders.end() && !it->second.transError.empty()) ours = it->second.transError;
    }
    if (ours.empty() || !log || n <= 0) {
        if (f) f(s, n, len, log);
        return;
    }
    // Ours first (it names the failing stage); backend detail appended when it fits.
    size_t copy = std::min((size_t)(n - 1), ours.size());
    memcpy(log, ours.c_str(), copy);
    size_t used = copy;
    if (f && used + 2 < (size_t)n) {
        log[used++] = '
';
        GLsizei rest = 0;
        f(s, n - (GLsizei)used, &rest, log + used);
        if (rest > 0) used += (size_t)rest;
    }
    log[used] = 0;
    if (len) *len = (GLsizei)used;
}
void attach_shader(GLuint p, GLuint s) {
    auto f = be<void (*)(GLuint, GLuint)>("glAttachShader");
    if (f) f(p, s);
    std::lock_guard<std::mutex> l(g_mu);
    auto it = g_programs.find(p);
    if (it == g_programs.end()) return;
    auto& v = it->second.attached;
    if (std::find(v.begin(), v.end(), s) == v.end()) v.push_back(s);
}
void detach_shader(GLuint p, GLuint s) {
    auto f = be<void (*)(GLuint, GLuint)>("glDetachShader");
    if (f) f(p, s);
    std::lock_guard<std::mutex> l(g_mu);
    auto it = g_programs.find(p);
    if (it == g_programs.end()) return;
    auto& v = it->second.attached;
    v.erase(std::remove(v.begin(), v.end(), s), v.end());
}
void link_program(GLuint p) {
    std::vector<uint64_t> hashes;
    {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_programs.find(p);
        if (it == g_programs.end()) {
            auto f = be<void (*)(GLuint)>("glLinkProgram");
            if (f) f(p);
            return;
        }
        for (GLuint s : it->second.attached) {
            auto si = g_shaders.find(s);
            if (si != g_shaders.end() && si->second.translatedOk) hashes.push_back(si->second.origHash);
        }
    }
    auto pLink = be<void (*)(GLuint)>("glLinkProgram");
    auto pGetiv = be<void (*)(GLuint, GLenum, GLint*)>("glGetProgramiv");
    auto pBinary = be<void (*)(GLuint, GLenum, const void*, GLsizei)>("glProgramBinary");
    if (!pLink) return;
    ProgRec* rec = nullptr;
    {
        std::lock_guard<std::mutex> l(g_mu);
        rec = &g_programs[p];
    }
    double t0 = now_ms();
    char kb[17];
    // Vault lookup first: a hit skips compile+link cost entirely.
    if (!hashes.empty() && pBinary && pGetiv) {
        uint64_t key = program_key(hashes);
        rec->key = key;
        uint32_t format = 0;
        std::vector<unsigned char> bin;
        if (cache::vault_get(key, format, bin)) {
            pBinary(p, format, bin.data(), (GLsizei)bin.size());
            GLint st = 0;
            pGetiv(p, GL_LINK_STATUS, &st);
            rec->linkMs = now_ms() - t0;
            rec->linked = true;
            rec->linkStatus = (st != 0);
            rec->vaultHit = rec->linkStatus;
            rec->linkError.clear();
            SLOG(INFO, "silica: program#%u key=%s link=%.2fms vault-hit status=%d", p, hex16(kb, key),
                 rec->linkMs, rec->linkStatus ? 1 : 0);
            if (rec->linkStatus) return;
            // Vault binary rejected: fall through to a real link below.
            rec->vaultHit = false;
        }
    }
    pLink(p);
    rec->linkMs = now_ms() - t0;
    rec->linked = true;
    rec->vaultHit = false;
    if (pGetiv) {
        GLint st = 0;
        pGetiv(p, GL_LINK_STATUS, &st);
        rec->linkStatus = (st != 0);
    }
    if (!rec->linkStatus) {
        auto pLog = be<void (*)(GLuint, GLsizei, GLsizei*, GLchar*)>("glGetProgramInfoLog");
        char buf[512] = {0};
        if (pLog) {
            GLsizei n = 0;
            pLog(p, sizeof(buf) - 1, &n, buf);
        }
        rec->linkError = first_line(buf[0] ? buf : std::string("link failed"));
        SLOG(INFO, "silica: program#%u link=%.2fms FAILED %s", p, rec->linkMs, rec->linkError.c_str());
        return;
    }
    SLOG(INFO, "silica: program#%u link=%.2fms linked", p, rec->linkMs);
    // Store the fresh binary for next run (when the backend offers binaries).
    auto pNumFmt = be<void (*)(GLenum, GLint*)>("glGetIntegerv");
    auto pGetBin = be<void (*)(GLuint, GLsizei, GLsizei*, GLenum*, void*)>("glGetProgramBinary");
    if (pNumFmt && pGetBin && !hashes.empty()) {
        GLint nfmt = 0;
        pNumFmt(GL_NUM_PROGRAM_BINARY_FORMATS, &nfmt);
        if (nfmt > 0) {
            GLint len = 0;
            pNumFmt(GL_PROGRAM_BINARY_LENGTH, &len);
            // Length is per-program; query on this program instead.
            auto pLen = be<void (*)(GLuint, GLenum, GLint*)>("glGetProgramiv");
            if (pLen) pLen(p, GL_PROGRAM_BINARY_LENGTH, &len);
            if (len > 0 && len < 64 * 1024 * 1024) {
                std::vector<unsigned char> bin((size_t)len);
                GLsizei got = 0;
                GLenum fmt = 0;
                pGetBin(p, len, &got, &fmt, bin.data());
                if (got > 0) {
                    cache::vault_put(rec->key ? rec->key : program_key(hashes), fmt, bin.data(), (size_t)got);
                    SLOG(INFO, "silica: program#%u vault-stored %dB", p, (int)got);
                }
            }
        }
    }
}
void get_programiv(GLuint p, GLenum q, GLint* v) {
    auto f = be<void (*)(GLuint, GLenum, GLint*)>("glGetProgramiv");
    if (!f || !v) return;
    if (q == GL_LINK_STATUS) {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_programs.find(p);
        if (it != g_programs.end() && it->second.linked) {
            *v = it->second.linkStatus ? 1 : 0;
            return;
        }
    }
    if (q == GL_INFO_LOG_LENGTH) {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_programs.find(p);
        if (it != g_programs.end() && !it->second.linkError.empty()) {
            *v = (GLint)it->second.linkError.size() + 1;
            return;
        }
    }
    f(p, q, v);
}
void get_program_info_log(GLuint p, GLsizei n, GLsizei* len, GLchar* log) {
    auto f = be<void (*)(GLuint, GLsizei, GLsizei*, GLchar*)>("glGetProgramInfoLog");
    std::string ours;
    {
        std::lock_guard<std::mutex> l(g_mu);
        auto it = g_programs.find(p);
        if (it != g_programs.end() && !it->second.linkError.empty()) ours = it->second.linkError;
    }
    if (ours.empty() || !log || n <= 0) {
        if (f) f(p, n, len, log);
        return;
    }
    size_t copy = std::min((size_t)(n - 1), ours.size());
    memcpy(log, ours.c_str(), copy);
    size_t used = copy;
    if (f && used + 2 < (size_t)n) {
        log[used++] = '
';
        GLsizei rest = 0;
        f(p, n - (GLsizei)used, &rest, log + used);
        if (rest > 0) used += (size_t)rest;
    }
    log[used] = 0;
    if (len) *len = (GLsizei)used;
}
} // namespace silica::shader
