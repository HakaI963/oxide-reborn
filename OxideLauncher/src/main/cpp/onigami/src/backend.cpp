// ONIGAMI single-driver loader + retryable probe. Own code.
// - RTLD_LAZY | RTLD_LOCAL: eager binding can refuse the open in a
//   constrained linker namespace; lazy defers to first call.
// - One libEGL.so handle + one libGLESv2/v3 handle. Never a driver list,
//   never an ANGLE split: a split answers null strings.
// - The probe caches ONLY non-null, non-empty answers and retries on every
//   later makeCurrent. Failure is never sealed (the 1.13.0 root cause).
// - This file NEVER calls the backend eglGetError: reading it here would
//   consume the application's own single-read flag.
#include "onigami/backend.h"
#include <dlfcn.h>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <sys/stat.h>
#include <sys/types.h>
#if __has_include(<android/log.h>)
#include <android/log.h>
#endif
namespace onigami {
namespace {
std::string g_renderer;
std::string g_version;
} // namespace
std::mutex& egl_mutex() {
    static std::mutex m;
    return m;
}
std::string& cached_renderer() { return g_renderer; }
std::string& cached_version() { return g_version; }
std::string data_dir() {
    if (const char* e = getenv("ONIGAMI_DATA_DIR"))
        if (e[0]) return std::string(e);
    const char* b = bridge_data_dir_cstr();
    if (b && b[0]) return std::string(b);
    return "/data/local/tmp/onigami";
}
void diag_write(const char* msg) {
    if (!msg) msg = "";
    set_last_diag(msg);
    std::string dir = data_dir();
    mkdir(dir.c_str(), 0755);
    std::string fp = dir + "/onigami-egl.log";
    if (FILE* f = fopen(fp.c_str(), "a")) {
        fprintf(f, "%s\n", msg);
        fclose(f);
    }
#if __has_include(<android/log.h>)
    __android_log_print(ANDROID_LOG_INFO, "onigami", "%s", msg);
#endif
}
void diag_printf(const char* fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    diag_write(buf);
}
EglProcs& egl_procs() {
    static EglProcs p;
    return p;
}
GlesProcs& gles_procs() {
    static GlesProcs p;
    return p;
}
#define ONIGAMI_LOAD(h, P, N) P.N = (decltype(P.N))dlsym(h, #N)
bool ensure_egl_loaded() {
    EglProcs& p = egl_procs();
    if (p.handle && p.GetError) return true;
    void* h = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
    if (!h) {
        set_last_diag("onigami: dlopen libEGL.so failed");
        return false;
    }
    p.handle = h;
    ONIGAMI_LOAD(h, p, eglGetDisplay);
    ONIGAMI_LOAD(h, p, eglInitialize);
    ONIGAMI_LOAD(h, p, eglTerminate);
    ONIGAMI_LOAD(h, p, eglChooseConfig);
    ONIGAMI_LOAD(h, p, eglGetConfigAttrib);
    ONIGAMI_LOAD(h, p, eglCreateContext);
    ONIGAMI_LOAD(h, p, eglDestroyContext);
    ONIGAMI_LOAD(h, p, eglMakeCurrent);
    ONIGAMI_LOAD(h, p, eglGetCurrentDisplay);
    ONIGAMI_LOAD(h, p, eglGetCurrentContext);
    ONIGAMI_LOAD(h, p, eglGetCurrentSurface);
    ONIGAMI_LOAD(h, p, eglSwapBuffers);
    ONIGAMI_LOAD(h, p, eglQueryString);
    ONIGAMI_LOAD(h, p, eglGetError);
    ONIGAMI_LOAD(h, p, eglBindAPI);
    ONIGAMI_LOAD(h, p, eglQueryAPI);
    ONIGAMI_LOAD(h, p, eglReleaseThread);
    ONIGAMI_LOAD(h, p, eglGetProcAddress);
    if (!p.GetError || !p.MakeCurrent || !p.GetDisplay) {
        diag_write("onigami: libEGL.so missing core symbols");
        return false;
    }
    return true;
}
bool ensure_gles_loaded() {
    GlesProcs& g = gles_procs();
    if (g.handle && g.GetString) return true;
    void* h = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
    if (!h) h = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_LOCAL);
    if (!h) {
        set_last_diag("onigami: dlopen libGLESv2.so failed");
        return false;
    }
    g.handle = h;
    ONIGAMI_LOAD(h, g, glGetString);
    ONIGAMI_LOAD(h, g, glGetStringi);
    ONIGAMI_LOAD(h, g, glGetError);
    ONIGAMI_LOAD(h, g, glGetIntegerv);
    ONIGAMI_LOAD(h, g, glGetFloatv);
    ONIGAMI_LOAD(h, g, glGetBooleanv);
    ONIGAMI_LOAD(h, g, glCreateShader);
    ONIGAMI_LOAD(h, g, glShaderSource);
    ONIGAMI_LOAD(h, g, glCompileShader);
    ONIGAMI_LOAD(h, g, glGetShaderiv);
    ONIGAMI_LOAD(h, g, glGetShaderInfoLog);
    ONIGAMI_LOAD(h, g, glDeleteShader);
    ONIGAMI_LOAD(h, g, glIsShader);
    ONIGAMI_LOAD(h, g, glCreateProgram);
    ONIGAMI_LOAD(h, g, glAttachShader);
    ONIGAMI_LOAD(h, g, glDetachShader);
    ONIGAMI_LOAD(h, g, glLinkProgram);
    ONIGAMI_LOAD(h, g, glGetProgramiv);
    ONIGAMI_LOAD(h, g, glGetProgramInfoLog);
    ONIGAMI_LOAD(h, g, glDeleteProgram);
    ONIGAMI_LOAD(h, g, glIsProgram);
    ONIGAMI_LOAD(h, g, glUseProgram);
    ONIGAMI_LOAD(h, g, glGetUniformLocation);
    ONIGAMI_LOAD(h, g, glGetAttribLocation);
    ONIGAMI_LOAD(h, g, glUniform1i);
    ONIGAMI_LOAD(h, g, glUniform1f);
    ONIGAMI_LOAD(h, g, glUniform4fv);
    ONIGAMI_LOAD(h, g, glUniformMatrix4fv);
    ONIGAMI_LOAD(h, g, glGenBuffers);
    ONIGAMI_LOAD(h, g, glBindBuffer);
    ONIGAMI_LOAD(h, g, glBufferData);
    ONIGAMI_LOAD(h, g, glBufferSubData);
    ONIGAMI_LOAD(h, g, glGenVertexArrays);
    ONIGAMI_LOAD(h, g, glBindVertexArray);
    ONIGAMI_LOAD(h, g, glGenTextures);
    ONIGAMI_LOAD(h, g, glBindTexture);
    ONIGAMI_LOAD(h, g, glActiveTexture);
    ONIGAMI_LOAD(h, g, glTexParameteri);
    ONIGAMI_LOAD(h, g, glTexImage2D);
    ONIGAMI_LOAD(h, g, glTexSubImage2D);
    ONIGAMI_LOAD(h, g, glGenerateMipmap);
    ONIGAMI_LOAD(h, g, glGenFramebuffers);
    ONIGAMI_LOAD(h, g, glBindFramebuffer);
    ONIGAMI_LOAD(h, g, glCheckFramebufferStatus);
    ONIGAMI_LOAD(h, g, glFramebufferTexture2D);
    ONIGAMI_LOAD(h, g, glFramebufferRenderbuffer);
    ONIGAMI_LOAD(h, g, glGenRenderbuffers);
    ONIGAMI_LOAD(h, g, glBindRenderbuffer);
    ONIGAMI_LOAD(h, g, glRenderbufferStorage);
    ONIGAMI_LOAD(h, g, glBlitFramebuffer);
    ONIGAMI_LOAD(h, g, glEnableVertexAttribArray);
    ONIGAMI_LOAD(h, g, glDisableVertexAttribArray);
    ONIGAMI_LOAD(h, g, glVertexAttribPointer);
    ONIGAMI_LOAD(h, g, glVertexAttrib4f);
    ONIGAMI_LOAD(h, g, glViewport);
    ONIGAMI_LOAD(h, g, glScissor);
    ONIGAMI_LOAD(h, g, glClear);
    ONIGAMI_LOAD(h, g, glClearColor);
    ONIGAMI_LOAD(h, g, glEnable);
    ONIGAMI_LOAD(h, g, glDisable);
    ONIGAMI_LOAD(h, g, glIsEnabled);
    ONIGAMI_LOAD(h, g, glBlendFunc);
    ONIGAMI_LOAD(h, g, glDepthMask);
    ONIGAMI_LOAD(h, g, glColorMask);
    ONIGAMI_LOAD(h, g, glPixelStorei);
    ONIGAMI_LOAD(h, g, glReadPixels);
    ONIGAMI_LOAD(h, g, glDrawArrays);
    ONIGAMI_LOAD(h, g, glDrawElements);
    ONIGAMI_LOAD(h, g, glDrawArraysInstanced);
    ONIGAMI_LOAD(h, g, glDrawElementsInstanced);
    ONIGAMI_LOAD(h, g, glFinish);
    ONIGAMI_LOAD(h, g, glFlush);
    return g.GetString != nullptr;
}
// Retryable probe: cache ONLY non-null, non-empty answers. Empty input leaves
// the cache empty so the next eglMakeCurrent retries. Never sealed.
void try_probe_renderer_version() {
    if (!g_renderer.empty() && !g_version.empty()) return;
    if (!ensure_gles_loaded()) return;
    GlesProcs& g = gles_procs();
    if (!g.GetString) return;
    const char* r = (const char*)g.GetString(GL_RENDERER);
    const char* v = (const char*)g.GetString(GL_VERSION);
    if (r && r[0]) g_renderer = r;
    if (v && v[0]) g_version = v;
    if (!g_renderer.empty() && !g_version.empty())
        diag_printf("onigami: probe renderer=%s version=%s", g_renderer.c_str(), g_version.c_str());
}
} // namespace onigami
