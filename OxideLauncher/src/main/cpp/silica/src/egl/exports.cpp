// Silica EGL exports. Own implementation against NDK <EGL/egl.h>.
// 5974f49-equivalent behavior, rewritten for Silica:
// - backend error rearm: read the failing call's error for the log line, then
//   re-queue it so the application's own eglGetError still observes it;
// - every create/bind/makeCurrent refusal is LOGGED (a retrying host otherwise
//   leaves no record, then makes GL calls with nothing current);
// - successful makeCurrent probes backend RENDERER/VERSION once per process
//   into the probe cache (null-safe query source for glGetString).
#include "silica/driver.h"
#include "silica/probe.h"
#include "silica/shim_config.h"
#include <EGL/egl.h>
#include <mutex>
#include <string>
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
        // Called right after a successful makeCurrent: backend has a context.
        r = (const char*)f(0x1F01); // GL_RENDERER
        v = (const char*)f(0x1F02); // GL_VERSION
    }
    note_probe(r, v);
    if (!v && config::diagnostics())
        SLOG(ERROR, "silica: backend answered GL_VERSION with null during probe; strings fall back to cache");
}
} // namespace
} // namespace silica::egl
// ---- generic forwarder helper ----
template <typename F>
static F be(const char* n) { return (F)silica::driver::resolve(n); }
#define SE_PROBE() do { silica::config::load_once(); silica::driver::ensure(); } while (0)
extern "C" {
S_API EGLint eglGetError(void) {
    SE_PROBE();
    std::lock_guard<std::mutex> l(silica::egl::g_mu);
    if (silica::egl::g_front_err != EGL_SUCCESS) {
        EGLint e = silica::egl::g_front_err;
        silica::egl::g_front_err = EGL_SUCCESS;
        return e;
    }
    auto f = be<EGLint (*)(void)>("eglGetError");
    return f ? f() : EGL_SUCCESS;
}
S_API EGLDisplay eglGetDisplay(EGLNativeDisplayType d) {
    SE_PROBE();
    auto f = be<EGLDisplay (*)(EGLNativeDisplayType)>("eglGetDisplay");
    return f ? f(d) : EGL_NO_DISPLAY;
}
S_API EGLBoolean eglInitialize(EGLDisplay d, EGLint* a, EGLint* b) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLint*, EGLint*)>("eglInitialize");
    return f ? f(d, a, b) : EGL_FALSE;
}
S_API EGLBoolean eglTerminate(EGLDisplay d) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay)>("eglTerminate");
    return f ? f(d) : EGL_FALSE;
}
S_API const char* eglQueryString(EGLDisplay d, EGLint n) {
    SE_PROBE();
    auto f = be<const char* (*)(EGLDisplay, EGLint)>("eglQueryString");
    return f ? f(d, n) : nullptr;
}
S_API EGLBoolean eglGetConfigs(EGLDisplay d, EGLConfig* c, EGLint s, EGLint* n) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLConfig*, EGLint, EGLint*)>("eglGetConfigs");
    return f ? f(d, c, s, n) : EGL_FALSE;
}
S_API EGLBoolean eglChooseConfig(EGLDisplay d, const EGLint* a, EGLConfig* c, EGLint s, EGLint* n) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*)>("eglChooseConfig");
    return f ? f(d, a, c, s, n) : EGL_FALSE;
}
S_API EGLBoolean eglGetConfigAttrib(EGLDisplay d, EGLConfig c, EGLint a, EGLint* v) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib");
    return f ? f(d, c, a, v) : EGL_FALSE;
}
S_API EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint* a) {
    SE_PROBE();
    auto f = be<EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*)>("eglCreateWindowSurface");
    EGLSurface s = f ? f(d, c, w, a) : EGL_NO_SURFACE;
    if (s == EGL_NO_SURFACE) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglCreateWindowSurface refused (backend error kept for eglGetError)");
        silica::egl::rearm();
    }
    return s;
}
S_API EGLSurface eglCreatePbufferSurface(EGLDisplay d, EGLConfig c, const EGLint* a) {
    SE_PROBE();
    auto f = be<EGLSurface (*)(EGLDisplay, EGLConfig, const EGLint*)>("eglCreatePbufferSurface");
    return f ? f(d, c, a) : EGL_NO_SURFACE;
}
S_API EGLSurface eglCreatePixmapSurface(EGLDisplay d, EGLConfig c, EGLNativePixmapType p, const EGLint* a) {
    SE_PROBE();
    auto f = be<EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativePixmapType, const EGLint*)>("eglCreatePixmapSurface");
    return f ? f(d, c, p, a) : EGL_NO_SURFACE;
}
S_API EGLBoolean eglDestroySurface(EGLDisplay d, EGLSurface s) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface");
    return f ? f(d, s) : EGL_FALSE;
}
S_API EGLBoolean eglQuerySurface(EGLDisplay d, EGLSurface s, EGLint a, EGLint* v) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint*)>("eglQuerySurface");
    return f ? f(d, s, a, v) : EGL_FALSE;
}
S_API EGLBoolean eglBindAPI(EGLenum api) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLenum)>("eglBindAPI");
    return f ? f(api) : EGL_FALSE;
}
S_API EGLenum eglQueryAPI(void) {
    SE_PROBE();
    auto f = be<EGLenum (*)(void)>("eglQueryAPI");
    return f ? f() : 0;
}
S_API EGLBoolean eglWaitClient(void) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(void)>("eglWaitClient");
    return f ? f() : EGL_FALSE;
}
S_API EGLBoolean eglReleaseThread(void) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(void)>("eglReleaseThread");
    return f ? f() : EGL_FALSE;
}
S_API EGLSurface eglCreatePbufferFromClientBuffer(EGLDisplay d, EGLenum b, EGLClientBuffer buf, EGLConfig c, const EGLint* a) {
    SE_PROBE();
    auto f = be<EGLSurface (*)(EGLDisplay, EGLenum, EGLClientBuffer, EGLConfig, const EGLint*)>("eglCreatePbufferFromClientBuffer");
    return f ? f(d, b, buf, c, a) : EGL_NO_SURFACE;
}
S_API EGLBoolean eglSurfaceAttrib(EGLDisplay d, EGLSurface s, EGLint a, EGLint v) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint)>("eglSurfaceAttrib");
    return f ? f(d, s, a, v) : EGL_FALSE;
}
S_API EGLBoolean eglBindTexImage(EGLDisplay d, EGLSurface s, EGLint b) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint)>("eglBindTexImage");
    return f ? f(d, s, b) : EGL_FALSE;
}
S_API EGLBoolean eglReleaseTexImage(EGLDisplay d, EGLSurface s, EGLint b) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint)>("eglReleaseTexImage");
    return f ? f(d, s, b) : EGL_FALSE;
}
S_API EGLBoolean eglWaitNative(EGLint e) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLint)>("eglWaitNative");
    return f ? f(e) : EGL_FALSE;
}
S_API EGLContext eglCreateContext(EGLDisplay d, EGLConfig c, EGLContext s, const EGLint* a) {
    SE_PROBE();
    auto f = be<EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*)>("eglCreateContext");
    EGLContext ctx = f ? f(d, c, s, a) : EGL_NO_CONTEXT;
    if (ctx == EGL_NO_CONTEXT) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglCreateContext refused by backend (error kept for eglGetError)");
        silica::egl::rearm();
    }
    return ctx;
}
S_API EGLBoolean eglDestroyContext(EGLDisplay d, EGLContext c) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLContext)>("eglDestroyContext");
    return f ? f(d, c) : EGL_FALSE;
}
S_API EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface dr, EGLSurface rd, EGLContext c) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)>("eglMakeCurrent");
    EGLBoolean r = f ? f(d, dr, rd, c) : EGL_FALSE;
    if (r != EGL_TRUE) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglMakeCurrent failed; GL calls on this thread reach the backend without a current context (error kept for eglGetError)");
        silica::egl::rearm();
    } else if (c != EGL_NO_CONTEXT) {
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        silica::egl::probe_once();
    }
    return r;
}
S_API EGLContext eglGetCurrentContext(void) {
    SE_PROBE();
    auto f = be<EGLContext (*)(void)>("eglGetCurrentContext");
    return f ? f() : EGL_NO_CONTEXT;
}
S_API EGLSurface eglGetCurrentSurface(EGLint r) {
    SE_PROBE();
    auto f = be<EGLSurface (*)(EGLint)>("eglGetCurrentSurface");
    return f ? f(r) : EGL_NO_SURFACE;
}
S_API EGLDisplay eglGetCurrentDisplay(void) {
    SE_PROBE();
    auto f = be<EGLDisplay (*)(void)>("eglGetCurrentDisplay");
    return f ? f() : EGL_NO_DISPLAY;
}
S_API EGLBoolean eglQueryContext(EGLDisplay d, EGLContext c, EGLint a, EGLint* v) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLContext, EGLint, EGLint*)>("eglQueryContext");
    return f ? f(d, c, a, v) : EGL_FALSE;
}
S_API EGLBoolean eglWaitGL(void) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(void)>("eglWaitGL");
    return f ? f() : EGL_FALSE;
}
S_API EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglSwapBuffers");
    return f ? f(d, s) : EGL_FALSE;
}
S_API EGLBoolean eglCopyBuffers(EGLDisplay d, EGLSurface s, EGLNativePixmapType t) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLNativePixmapType)>("eglCopyBuffers");
    return f ? f(d, s, t) : EGL_FALSE;
}
S_API EGLBoolean eglSwapInterval(EGLDisplay d, EGLint i) {
    SE_PROBE();
    auto f = be<EGLBoolean (*)(EGLDisplay, EGLint)>("eglSwapInterval");
    return f ? f(d, i) : EGL_FALSE;
}
} // extern C
