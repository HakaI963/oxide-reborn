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
// ERROR DISCIPLINE (device-proven): refusals are logged with their decoded
// arguments, and the real backend code is captured exactly once into an explicit
// thread-local queue that the eglGetError exported below serves. No error is
// invented, and none is dropped.
//
// WHY THE QUEUE (device-proven): the application resolves eglGetError through
// POJAVEXEC_EGL, which lands in this library, so the read it performs is the one
// exported here, not the backend's own flag. Reading the backend flag only to
// log it and discard the code is exactly what reported EGL_SUCCESS forever.
//
// DESKTOP REQUEST TRANSLATION (device-proven): RenderPearl/Iris request a
// desktop core-profile context (MAJOR/MINOR + PROFILE_MASK + FLAGS) on a device
// with GLES only. The backend refuses such a request, and dropping only the KHR
// version attributes does not help, because PROFILE_MASK and the debug flag are
// unsupported too. A desktop request is therefore rewritten into a plain ES 3
// request and the API is bound to ES before creation. The context really is ES
// and the backend is never spoofed.
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
// This NDK aliases CLIENT_VERSION to MAJOR_VERSION (both 0x3098); other
// headers keep them apart. One shared case keeps both layouts compiling.
#if EGL_CONTEXT_CLIENT_VERSION == EGL_CONTEXT_MAJOR_VERSION
            case EGL_CONTEXT_CLIENT_VERSION: client = attr[i + 1]; major = attr[i + 1]; break;
#else
            case EGL_CONTEXT_CLIENT_VERSION: client = attr[i + 1]; break;
            case EGL_CONTEXT_MAJOR_VERSION: major = attr[i + 1]; break;
#endif
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
    SLOG(INFO, "SILICA_EGL_DIAG entry backend=%p dpy=%p cfg=%p share=%s attribs=[%s]",
         (void*)f, (void*)dpy, (void*)cfg, share == EGL_NO_CONTEXT ? "null" : "set",
         silica::egl::describe_ctx_attribs(attr).c_str());
    if (!f) {
        // Entry missing, not a backend refusal: say exactly that. Nothing was
        // called, so there is no backend code to queue.
        silica::egl::set_frontend_error(EGL_BAD_MATCH);
        SLOG(ERROR, "silica: eglCreateContext has no backend entry; failing without calling through");
        return EGL_NO_CONTEXT;
    }

    // Desktop requests are translated to ES (see file header). ES requests pass
    // through untouched, so the vanilla path is unchanged.
    const silica::egl::CtxRequest req = silica::egl::analyze_ctx(attr);
    const EGLint* backend_attr = attr;
    EGLint translated[3] = {EGL_NONE, EGL_NONE, EGL_NONE};
    if (req.desktop) {
        silica::egl::build_es_request(req, translated, 3);
        backend_attr = translated;
        SLOG(INFO, "silica: eglCreateContext desktop request %d.%d profile=0x%x flags=0x%x -> ES request [%s]",
             req.major, req.minor, (unsigned)req.profile, (unsigned)req.flags,
             silica::egl::describe_ctx_attribs(translated).c_str());
        // The driver must be in ES mode for the context about to be created;
        // without this the backend rejects an ES request while bound to GL.
        auto bind = be<EGLBoolean (*)(EGLenum)>("eglBindAPI");
        if (bind) {
            if (bind(EGL_OPENGL_ES_API) != EGL_TRUE) {
                const EGLint e = silica::egl::capture_backend_error();
                SLOG(ERROR, "silica: eglBindAPI(EGL_OPENGL_ES_API) refused with %s; not creating a context",
                     silica::egl::egl_error_name(e));
                return EGL_NO_CONTEXT;
            }
        } else {
            silica::egl::set_frontend_error(EGL_BAD_MATCH);
            SLOG(ERROR, "silica: eglBindAPI unavailable; cannot honour a desktop request as ES");
            return EGL_NO_CONTEXT;
        }
    }

    EGLContext ctx = f(dpy, cfg, share, backend_attr);
    SLOG(INFO, "SILICA_EGL_DIAG exit ctx=%p", (void*)ctx);
    if (ctx == EGL_NO_CONTEXT) {
        // Real backend code, read once into the queue the app reads from.
        const EGLint e = silica::egl::capture_backend_error();
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        SLOG(ERROR, "silica: eglCreateContext refused dpy=%p cfg=%p share=%s requested=[%s] sent=[%s] with %s",
             (void*)dpy, (void*)cfg, share == EGL_NO_CONTEXT ? "null" : "set",
             silica::egl::describe_ctx_attribs(attr).c_str(),
             silica::egl::describe_ctx_attribs(backend_attr).c_str(),
             silica::egl::egl_error_name(e));
    } else if (silica::config::diagnostics()) {
        SLOG(DEBUG, "silica: eglCreateContext ok ctx=%p dpy=%p share=%s [%s]", (void*)ctx, (void*)dpy,
             share == EGL_NO_CONTEXT ? "null" : "set", silica::egl::describe_ctx_attribs(attr).c_str());
    }
    return ctx;
}
// The application resolves eglGetError through POJAVEXEC_EGL, which lands in
// this library, so this is the read the app actually performs. A queued real
// code is returned once and cleared; otherwise the backend is asked directly.
S_API EGLint eglGetError(void) {
    SE_INIT();
    EGLint queued = silica::egl::g_frontend_error;
    if (queued != EGL_SUCCESS) {
        silica::egl::g_frontend_error = EGL_SUCCESS;
        // Drain the backend flag too: our queue replaces rather than queues
        // behind it, so a stale backend error cannot resurface later.
        auto ge = be<EGLint (*)(void)>("eglGetError");
        const EGLint stale = ge ? ge() : EGL_SUCCESS;
        SLOG(INFO, "silica: eglGetError -> %s (queued; backend had %s)",
             silica::egl::egl_error_name(queued), silica::egl::egl_error_name(stale));
        return queued;
    }
    auto ge = be<EGLint (*)(void)>("eglGetError");
    return ge ? ge() : EGL_SUCCESS;
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
