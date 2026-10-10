// ONIGAMI EGL interposition: FULL display-path ownership. Own implementation.
// Display, init, configs, context, current, swap, queries, errors, binding
// and proc resolution all chain to the single host driver loaded by
// backend.cpp. No second display path exists: the launcher publishes no EGL
// override, so every caller that reaches these symbols gets the same driver.
// ERROR DISCIPLINE: eglGetError is pure passthrough. Nothing in this file
// reads the backend flag except to return it, so the application's own read
// always observes the real code. Missing backend => EGL_FALSE / EGL_NO_* /
// EGL_NOT_INITIALIZED with a diag line. Nothing is invented.
#include "onigami/backend.h"
#include <EGL/egl.h>
#include <cstring>
#if defined(__GNUC__) || defined(__clang__)
#define O_API __attribute__((visibility("default")))
#else
#define O_API
#endif
using onigami::egl_procs;
using onigami::ensure_egl_loaded;
extern "C" {
O_API EGLDisplay eglGetDisplay(EGLNativeDisplayType id) {
    if (!ensure_egl_loaded() || !egl_procs().GetDisplay) {
        onigami::diag_write("onigami: eglGetDisplay with no backend");
        return EGL_NO_DISPLAY;
    }
    return egl_procs().GetDisplay(id);
}
O_API EGLBoolean eglInitialize(EGLDisplay d, EGLint* major, EGLint* minor) {
    if (!ensure_egl_loaded() || !egl_procs().Initialize) {
        onigami::diag_write("onigami: eglInitialize with no backend");
        return EGL_FALSE;
    }
    return egl_procs().Initialize(d, major, minor);
}
O_API EGLBoolean eglTerminate(EGLDisplay d) {
    if (!ensure_egl_loaded() || !egl_procs().Terminate) return EGL_FALSE;
    return egl_procs().Terminate(d);
}
O_API EGLBoolean eglChooseConfig(EGLDisplay d, const EGLint* attr, EGLConfig* cfg, EGLint n, EGLint* m) {
    if (!m) return EGL_FALSE;
    if (!ensure_egl_loaded() || !egl_procs().ChooseConfig) return EGL_FALSE;
    return egl_procs().ChooseConfig(d, attr, cfg, n, m);
}
O_API EGLBoolean eglGetConfigAttrib(EGLDisplay d, EGLConfig c, EGLint a, EGLint* v) {
    if (!v) return EGL_FALSE;
    if (!ensure_egl_loaded() || !egl_procs().GetConfigAttrib) return EGL_FALSE;
    return egl_procs().GetConfigAttrib(d, c, a, v);
}
O_API EGLContext eglCreateContext(EGLDisplay d, EGLConfig c, EGLContext s, const EGLint* a) {
    if (!ensure_egl_loaded() || !egl_procs().CreateContext) {
        onigami::diag_write("onigami: eglCreateContext with no backend");
        return EGL_NO_CONTEXT;
    }
    EGLContext ctx = egl_procs().CreateContext(d, c, s, a);
    if (ctx == EGL_NO_CONTEXT)
        onigami::diag_write("onigami: backend refused eglCreateContext (see app eglGetError)");
    return ctx;
}
O_API EGLBoolean eglDestroyContext(EGLDisplay d, EGLContext c) {
    if (!ensure_egl_loaded() || !egl_procs().DestroyContext) return EGL_FALSE;
    return egl_procs().DestroyContext(d, c);
}
O_API EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface draw, EGLSurface read, EGLContext c) {
    if (!ensure_egl_loaded() || !egl_procs().MakeCurrent) return EGL_FALSE;
    EGLBoolean ok = egl_procs().MakeCurrent(d, draw, read, c);
    if (ok == EGL_TRUE && c != EGL_NO_CONTEXT) {
        // Retryable probe: caches only non-empty answers, never seals failure.
        // No backend eglGetError read here: the app's flag stays intact.
        onigami::try_probe_renderer_version();
    } else if (ok != EGL_TRUE) {
        onigami::diag_write("onigami: backend refused eglMakeCurrent (see app eglGetError)");
    }
    return ok;
}
O_API EGLDisplay eglGetCurrentDisplay(void) {
    if (!ensure_egl_loaded() || !egl_procs().GetCurrentDisplay) return EGL_NO_DISPLAY;
    return egl_procs().GetCurrentDisplay();
}
O_API EGLContext eglGetCurrentContext(void) {
    if (!ensure_egl_loaded() || !egl_procs().GetCurrentContext) return EGL_NO_CONTEXT;
    return egl_procs().GetCurrentContext();
}
O_API EGLSurface eglGetCurrentSurface(EGLint r) {
    if (!ensure_egl_loaded() || !egl_procs().GetCurrentSurface) return EGL_NO_SURFACE;
    return egl_procs().GetCurrentSurface(r);
}
O_API EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s) {
    if (!ensure_egl_loaded() || !egl_procs().SwapBuffers) return EGL_FALSE;
    return egl_procs().SwapBuffers(d, s);
}
O_API const char* eglQueryString(EGLDisplay d, EGLint n) {
    if (!ensure_egl_loaded() || !egl_procs().QueryString) return nullptr;
    return egl_procs().QueryString(d, n);
}
O_API EGLint eglGetError(void) {
    if (!ensure_egl_loaded() || !egl_procs().GetError) return EGL_NOT_INITIALIZED;
    return egl_procs().GetError();
}
O_API EGLBoolean eglBindAPI(EGLenum a) {
    if (!ensure_egl_loaded() || !egl_procs().BindAPI) return EGL_FALSE;
    return egl_procs().BindAPI(a);
}
O_API EGLenum eglQueryAPI(void) {
    if (!ensure_egl_loaded() || !egl_procs().QueryAPI) return 0;
    return egl_procs().QueryAPI();
}
O_API EGLBoolean eglReleaseThread(void) {
    if (!ensure_egl_loaded() || !egl_procs().ReleaseThread) return EGL_FALSE;
    return egl_procs().ReleaseThread();
}
// Dispatch rule: ONIGAMI's own wrapped entry points are answered with
// ONIGAMI's own addresses FIRST (resolved from our own library handle); only
// unwrapped names fall through to the backend. This is load-bearing: LWJGL on
// EGL resolves its GL function pointers through eglGetProcAddress, so
// answering with backend pointers here silently bypasses shader translation,
// state tracking and version mapping for the entire game while the
// directly-linked symbols stay correct. That bypass is exactly what delivered
// raw desktop GLSL ("#version 150") to the Adreno GLES compiler and produced
// "Invalid #version" on minecraft:core/gui.
O_API __eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* n) {
    if (!n || !n[0]) return nullptr;
    if (void* own = onigami::own_proc(n)) return (__eglMustCastToProperFunctionPointerType)own;
    if (!ensure_egl_loaded() || !egl_procs().GetProcAddress) return nullptr;
    return egl_procs().GetProcAddress(n);
}
} // extern "C"
