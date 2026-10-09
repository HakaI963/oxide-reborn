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
#define ONIGAMI_LOAD(h, P, M, N) P.M = (decltype(P.M))dlsym(h, #N)
bool ensure_egl_loaded() {
    EglProcs& p = egl_procs();
    if (p.handle && p.GetError) return true;
    void* h = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
    if (!h) {
        set_last_diag("onigami: dlopen libEGL.so failed");
        return false;
    }
    p.handle = h;
    ONIGAMI_LOAD(h, p, GetDisplay, eglGetDisplay);
    ONIGAMI_LOAD(h, p, Initialize, eglInitialize);
    ONIGAMI_LOAD(h, p, Terminate, eglTerminate);
    ONIGAMI_LOAD(h, p, ChooseConfig, eglChooseConfig);
    ONIGAMI_LOAD(h, p, GetConfigAttrib, eglGetConfigAttrib);
    ONIGAMI_LOAD(h, p, CreateContext, eglCreateContext);
    ONIGAMI_LOAD(h, p, DestroyContext, eglDestroyContext);
    ONIGAMI_LOAD(h, p, MakeCurrent, eglMakeCurrent);
    ONIGAMI_LOAD(h, p, GetCurrentDisplay, eglGetCurrentDisplay);
    ONIGAMI_LOAD(h, p, GetCurrentContext, eglGetCurrentContext);
    ONIGAMI_LOAD(h, p, GetCurrentSurface, eglGetCurrentSurface);
    ONIGAMI_LOAD(h, p, SwapBuffers, eglSwapBuffers);
    ONIGAMI_LOAD(h, p, QueryString, eglQueryString);
    ONIGAMI_LOAD(h, p, GetError, eglGetError);
    ONIGAMI_LOAD(h, p, BindAPI, eglBindAPI);
    ONIGAMI_LOAD(h, p, QueryAPI, eglQueryAPI);
    ONIGAMI_LOAD(h, p, ReleaseThread, eglReleaseThread);
    ONIGAMI_LOAD(h, p, GetProcAddress, eglGetProcAddress);
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
    ONIGAMI_LOAD(h, g, GetString, glGetString);
    ONIGAMI_LOAD(h, g, GetStringi, glGetStringi);
    ONIGAMI_LOAD(h, g, GetError, glGetError);
    ONIGAMI_LOAD(h, g, GetIntegerv, glGetIntegerv);
    ONIGAMI_LOAD(h, g, GetFloatv, glGetFloatv);
    ONIGAMI_LOAD(h, g, GetBooleanv, glGetBooleanv);
    ONIGAMI_LOAD(h, g, CreateShader, glCreateShader);
    ONIGAMI_LOAD(h, g, ShaderSource, glShaderSource);
    ONIGAMI_LOAD(h, g, CompileShader, glCompileShader);
    ONIGAMI_LOAD(h, g, GetShaderiv, glGetShaderiv);
    ONIGAMI_LOAD(h, g, GetShaderInfoLog, glGetShaderInfoLog);
    ONIGAMI_LOAD(h, g, DeleteShader, glDeleteShader);
    ONIGAMI_LOAD(h, g, IsShader, glIsShader);
    ONIGAMI_LOAD(h, g, CreateProgram, glCreateProgram);
    ONIGAMI_LOAD(h, g, AttachShader, glAttachShader);
    ONIGAMI_LOAD(h, g, DetachShader, glDetachShader);
    ONIGAMI_LOAD(h, g, LinkProgram, glLinkProgram);
    ONIGAMI_LOAD(h, g, GetProgramiv, glGetProgramiv);
    ONIGAMI_LOAD(h, g, GetProgramInfoLog, glGetProgramInfoLog);
    ONIGAMI_LOAD(h, g, DeleteProgram, glDeleteProgram);
    ONIGAMI_LOAD(h, g, IsProgram, glIsProgram);
    ONIGAMI_LOAD(h, g, UseProgram, glUseProgram);
    ONIGAMI_LOAD(h, g, GetUniformLocation, glGetUniformLocation);
    ONIGAMI_LOAD(h, g, GetAttribLocation, glGetAttribLocation);
    ONIGAMI_LOAD(h, g, Uniform1i, glUniform1i);
    ONIGAMI_LOAD(h, g, Uniform1f, glUniform1f);
    ONIGAMI_LOAD(h, g, Uniform4fv, glUniform4fv);
    ONIGAMI_LOAD(h, g, UniformMatrix4fv, glUniformMatrix4fv);
    ONIGAMI_LOAD(h, g, GenBuffers, glGenBuffers);
    ONIGAMI_LOAD(h, g, BindBuffer, glBindBuffer);
    ONIGAMI_LOAD(h, g, BufferData, glBufferData);
    ONIGAMI_LOAD(h, g, BufferSubData, glBufferSubData);
    ONIGAMI_LOAD(h, g, GenVertexArrays, glGenVertexArrays);
    ONIGAMI_LOAD(h, g, BindVertexArray, glBindVertexArray);
    ONIGAMI_LOAD(h, g, GenTextures, glGenTextures);
    ONIGAMI_LOAD(h, g, BindTexture, glBindTexture);
    ONIGAMI_LOAD(h, g, ActiveTexture, glActiveTexture);
    ONIGAMI_LOAD(h, g, TexParameteri, glTexParameteri);
    ONIGAMI_LOAD(h, g, TexImage2D, glTexImage2D);
    ONIGAMI_LOAD(h, g, TexSubImage2D, glTexSubImage2D);
    ONIGAMI_LOAD(h, g, GenerateMipmap, glGenerateMipmap);
    ONIGAMI_LOAD(h, g, GenFramebuffers, glGenFramebuffers);
    ONIGAMI_LOAD(h, g, BindFramebuffer, glBindFramebuffer);
    ONIGAMI_LOAD(h, g, CheckFramebufferStatus, glCheckFramebufferStatus);
    ONIGAMI_LOAD(h, g, FramebufferTexture2D, glFramebufferTexture2D);
    ONIGAMI_LOAD(h, g, FramebufferRenderbuffer, glFramebufferRenderbuffer);
    ONIGAMI_LOAD(h, g, GenRenderbuffers, glGenRenderbuffers);
    ONIGAMI_LOAD(h, g, BindRenderbuffer, glBindRenderbuffer);
    ONIGAMI_LOAD(h, g, RenderbufferStorage, glRenderbufferStorage);
    ONIGAMI_LOAD(h, g, BlitFramebuffer, glBlitFramebuffer);
    ONIGAMI_LOAD(h, g, EnableVertexAttribArray, glEnableVertexAttribArray);
    ONIGAMI_LOAD(h, g, DisableVertexAttribArray, glDisableVertexAttribArray);
    ONIGAMI_LOAD(h, g, VertexAttribPointer, glVertexAttribPointer);
    ONIGAMI_LOAD(h, g, VertexAttrib4f, glVertexAttrib4f);
    ONIGAMI_LOAD(h, g, Viewport, glViewport);
    ONIGAMI_LOAD(h, g, Scissor, glScissor);
    ONIGAMI_LOAD(h, g, Clear, glClear);
    ONIGAMI_LOAD(h, g, ClearColor, glClearColor);
    ONIGAMI_LOAD(h, g, Enable, glEnable);
    ONIGAMI_LOAD(h, g, Disable, glDisable);
    ONIGAMI_LOAD(h, g, IsEnabled, glIsEnabled);
    ONIGAMI_LOAD(h, g, BlendFunc, glBlendFunc);
    ONIGAMI_LOAD(h, g, DepthMask, glDepthMask);
    ONIGAMI_LOAD(h, g, ColorMask, glColorMask);
    ONIGAMI_LOAD(h, g, PixelStorei, glPixelStorei);
    ONIGAMI_LOAD(h, g, ReadPixels, glReadPixels);
    ONIGAMI_LOAD(h, g, DrawArrays, glDrawArrays);
    ONIGAMI_LOAD(h, g, DrawElements, glDrawElements);
    ONIGAMI_LOAD(h, g, DrawArraysInstanced, glDrawArraysInstanced);
    ONIGAMI_LOAD(h, g, DrawElementsInstanced, glDrawElementsInstanced);
    ONIGAMI_LOAD(h, g, Finish, glFinish);
    ONIGAMI_LOAD(h, g, Flush, glFlush);
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
