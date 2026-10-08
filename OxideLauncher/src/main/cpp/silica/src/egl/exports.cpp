// Silica EGL exports: context lifecycle ONLY. Own implementation.
// Display acquisition is deliberately NOT interposed: eglGetDisplay,
// eglInitialize, configs, surfaces and swap stay on the host EGL end to end
// (the launcher passes no EGL override for Silica, so SDL and the bridge
// resolve those straight from the system library, exactly like the working
// renderer contract). A second display path inside this library is what can
// return NO_DISPLAY while the host would succeed, so it does not exist here.
// What IS wrapped: context create/destroy/current (probe fill + state setup +
// refusal logs) and eglGetProcAddress (own wrappers first, host otherwise).
#include "silica/driver.h"
#include "silica/probe.h"
#include "silica/shim_config.h"
#include <EGL/egl.h>
#include <dlfcn.h>
#include <mutex>
#include <android/log.h>
#if defined(__GNUC__) || defined(__clang__)
#define S_API __attribute__((visibility("default")))
#else
#define S_API
#endif
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::egl {
namespace {
std::mutex g_mu;
EGLint g_front_err = EGL_SUCCESS;
bool g_probed = false;
EGLint rearm() {
    typedef EGLint (*F)(void);
    auto f = (F)driver::resolve("eglGetError");
    EGLint e = f ? f() : EGL_SUCCESS;
    if (e != EGL_SUCCESS) g_front_err = e;
    return e;
}
void probe_once() {
    if (g_probed) return;
    g_probed = true;
    typedef const unsigned char* (*GS)(unsigned);
    auto f = (GS)driver::resolve("glGetString");
    const char* r = nullptr;
    const char* v = nullptr;
    if (f) {
        r = (const char*)f(0x1F01); // GL_RENDERER, valid: just made current
        v = (const char*)f(0x1F02); // GL_VERSION
    }
    note_probe(r, v);
    if (!v)
        SLOG(ERROR, "silica: probe saw null GL_VERSION even with a current context");
    else if (config::diagnostics())
        SLOG(DEBUG, "silica: probe renderer/version cached");
}
} // namespace
} // namespace silica::egl
template <typename F>
static F be(const char* n) { return (F)silica::driver::resolve(n); }
#define SE_INIT() do { silica::config::load_once(); silica::driver::ensure(); } while (0)
extern "C" {
S_API EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig cfg, EGLContext share, const EGLint* attr) {
    SE_INIT();
    auto f = be<EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*)>("eglCreateContext");
    EGLContext ctx = f ? f(dpy, cfg, share, attr) : EGL_NO_CONTEXT;
    if (ctx == EGL_NO_CONTEXT) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglCreateContext refused by backend (dpy=%p); error kept for eglGetError", (void*)dpy);
        silica::egl::rearm();
    }
    return ctx;
}
S_API EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    SE_INIT();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLContext)>("eglDestroyContext");
    return f ? f(dpy, ctx) : EGL_FALSE;
}
S_API EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    SE_INIT();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)>("eglMakeCurrent");
    EGLBoolean r = f ? f(dpy, draw, read, ctx) : EGL_FALSE;
    if (r != EGL_TRUE) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglMakeCurrent failed; GL on this thread has no current context (error kept for eglGetError)");
        silica::egl::rearm();
    } else if (ctx != EGL_NO_CONTEXT) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        silica::egl::probe_once();
    }
    return r;
}
typedef void (*silica_proc_t)(void);
S_API silica_proc_t eglGetProcAddress(const char* name) {
    SE_INIT();
    if (!name || !*name) return nullptr;
    // Own wrappers first (covers every gl* export plus the three context
    // functions above) without a hand-maintained table.
    void* self = dlopen("libsilica.so", RTLD_NOLOAD | RTLD_LOCAL);
    if (self) {
        void* p = dlsym(self, name);
        dlclose(self);
        if (p) return (silica_proc_t)p;
    }
    // Host display/config/surface/swap entries live here: this library does
    // not define them, so callers transparently get the host implementation.
    // TEMPORARY compat for unwrapped gl* names: they render but bypass state
    // tracking (counted in diagnostics mode).
    static int miss_logged = 0;
    void* p = silica::driver::resolve(name);
    if (!p && silica::config::diagnostics() && miss_logged < 32) {
        miss_logged++;
        SLOG(ERROR, "silica: eglGetProcAddress(%s) unknown to backend too", name);
    }
    return (silica_proc_t)p;
}
} // extern C
