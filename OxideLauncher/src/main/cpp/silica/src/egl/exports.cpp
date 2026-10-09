// Silica EGL exports: context lifecycle ONLY. Own implementation.
// Display acquisition is deliberately NOT interposed: eglGetDisplay,
// eglInitialize, configs, surfaces and swap stay on the host EGL end to end
// (the launcher passes no EGL override for Silica, so SDL and the bridge
// resolve those straight from the system library, exactly like the working
// renderer contract). A second display path inside this library is what can
// return NO_DISPLAY while the host would succeed, so it does not exist here.
// What IS wrapped: context create/destroy/current (probe fill + state setup +
// refusal logs) and eglGetProcAddress (own wrappers first, host otherwise).
//
// ERROR DISCIPLINE (hard lesson, device-proven): these wrappers NEVER call the
// backend eglGetError. Silica does not export eglGetError, so the application
// reads errors from the host implementation; consuming the flag here for a log
// line is what once turned every real failure into a reported EGL_SUCCESS.
// Refusals are logged with their decoded arguments while the backend error
// flag stays queued for the app's own read. No errors are invented.
#include "silica/driver.h"
#include "silica/probe.h"
#include "silica/shim_config.h"
#include <EGL/egl.h>
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
// Context-attribute ids decoded for diagnostics. EGL 1.5 promoted the KHR
// version/profile ids to core, so the NDK header names are used directly;
// only the flags id keeps a local name (it stayed KHR-suffixed).
constexpr int kFlagsKhr = 0x30FC;
namespace silica::egl {
namespace {
std::mutex g_mu;
bool g_probed = false;
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
// Decodes the requested client version and profile from a context attrib list
// for failure logs. Values only; nothing sensitive can appear here.
std::string describe_ctx_attribs(const EGLint* attr) {
    if (!attr) return "null";
    int client = -1, major = -1, minor = -1, flags = -1, profile = -1;
    for (int i = 0; attr[i] != EGL_NONE; i += 2) {
        switch (attr[i]) {
            case EGL_CONTEXT_CLIENT_VERSION: client = attr[i + 1]; break;
            case EGL_CONTEXT_MAJOR_VERSION: major = attr[i + 1]; break;
            case EGL_CONTEXT_MINOR_VERSION: minor = attr[i + 1]; break;
            case kFlagsKhr: flags = attr[i + 1]; break;
            case EGL_CONTEXT_OPENGL_PROFILE_MASK: profile = attr[i + 1]; break;
            default: break;
        }
        if (i > 60) break; // never walk a hostile list
    }
    char buf[192];
    snprintf(buf, sizeof(buf), "client=%d major=%d minor=%d flags=0x%x profile=0x%x", client, major,
             minor, flags < 0 ? 0 : (unsigned)flags, profile < 0 ? 0 : (unsigned)profile);
    return buf;
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
    if (!f) {
        // Entry missing, not a backend refusal: say exactly that. The backend
        // error flag is untouched (nothing was called).
        SLOG(ERROR, "silica: eglCreateContext has no backend entry; failing without calling through");
        return EGL_NO_CONTEXT;
    }
    EGLContext ctx = f(dpy, cfg, share, attr);
    if (ctx == EGL_NO_CONTEXT) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        // No backend eglGetError call here by design: the flag stays queued
        // for the application's own read (see file header).
        SLOG(ERROR, "silica: eglCreateContext refused dpy=%p cfg=%p share=%s [%s]; backend error left queued",
             (void*)dpy, (void*)cfg, share == EGL_NO_CONTEXT ? "null" : "set",
             silica::egl::describe_ctx_attribs(attr).c_str());
    } else if (silica::config::diagnostics()) {
        SLOG(DEBUG, "silica: eglCreateContext ok ctx=%p dpy=%p share=%s [%s]", (void*)ctx, (void*)dpy,
             share == EGL_NO_CONTEXT ? "null" : "set", silica::egl::describe_ctx_attribs(attr).c_str());
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
    if (!f) {
        SLOG(ERROR, "silica: eglMakeCurrent has no backend entry; failing without calling through");
        return EGL_FALSE;
    }
    EGLBoolean r = f(dpy, draw, read, ctx);
    if (r != EGL_TRUE) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglMakeCurrent failed dpy=%p draw=%p read=%p ctx=%p; GL on this thread has no current context; backend error left queued",
             (void*)dpy, (void*)draw, (void*)read, (void*)ctx);
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
