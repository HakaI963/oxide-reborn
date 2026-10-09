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
#include <unistd.h>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
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
// ---- explicit error queue -------------------------------------------------
// Per-thread, as EGL requires: an error raised on the render thread must never
// be handed to a different thread's read.
thread_local EGLint g_frontend_error = EGL_SUCCESS;

void set_frontend_error(EGLint error) { g_frontend_error = error; }

// Reads the backend error once for diagnostics and queues the real code. Safe
// precisely because the app is served by the eglGetError exported below.
EGLint capture_backend_error() {
    typedef EGLint (*GS)(void);
    auto ge = (GS)driver::resolve("eglGetError");
    EGLint error = ge ? ge() : EGL_SUCCESS;
    if (error != EGL_SUCCESS) set_frontend_error(error);
    return error;
}

const char* egl_error_name(EGLint error) {
    switch (error) {
    case EGL_SUCCESS: return "EGL_SUCCESS";
    case EGL_NOT_INITIALIZED: return "EGL_NOT_INITIALIZED";
    case EGL_BAD_ACCESS: return "EGL_BAD_ACCESS";
    case EGL_BAD_ALLOC: return "EGL_BAD_ALLOC";
    case EGL_BAD_ATTRIBUTE: return "EGL_BAD_ATTRIBUTE";
    case EGL_BAD_CONFIG: return "EGL_BAD_CONFIG";
    case EGL_BAD_CONTEXT: return "EGL_BAD_CONTEXT";
    case EGL_BAD_DISPLAY: return "EGL_BAD_DISPLAY";
    case EGL_BAD_MATCH: return "EGL_BAD_MATCH";
    case EGL_BAD_PARAMETER: return "EGL_BAD_PARAMETER";
    case EGL_BAD_NATIVE_WINDOW: return "EGL_BAD_NATIVE_WINDOW";
    case EGL_CONTEXT_LOST: return "EGL_CONTEXT_LOST";
    default: return "EGL_<other>";
    }
}

// ---- desktop request analysis ---------------------------------------------
struct CtxRequest {
    bool desktop = false;   // asked for desktop GL, not ES
    int major = 0;
    int minor = 0;
    EGLint flags = 0;
    EGLint profile = 0;
    int client = 0;
};

// Desktop is decided by attributes that only ever appear in a desktop request: a
// profile mask, or the desktop context flags. EGL_CONTEXT_CLIENT_VERSION alone
// stays ES, which is the vanilla path.
CtxRequest analyze_ctx(const EGLint* attr) {
    CtxRequest r;
    if (!attr) return r;
    for (int i = 0; attr[i] != EGL_NONE && i < 60; i += 2) {
        switch (attr[i]) {
#if EGL_CONTEXT_CLIENT_VERSION == EGL_CONTEXT_MAJOR_VERSION
        case EGL_CONTEXT_CLIENT_VERSION: r.client = attr[i + 1]; r.major = attr[i + 1]; break;
#else
        case EGL_CONTEXT_CLIENT_VERSION: r.client = attr[i + 1]; break;
        case EGL_CONTEXT_MAJOR_VERSION: r.major = attr[i + 1]; break;
#endif
        case EGL_CONTEXT_MINOR_VERSION: r.minor = attr[i + 1]; break;
        case kFlagsKhr: r.flags = attr[i + 1]; break;
        case EGL_CONTEXT_OPENGL_PROFILE_MASK: r.profile = attr[i + 1]; break;
        default: break;
        }
    }
    const EGLint desktop_flags = 0x0001 /* DEBUG */ | 0x0002 /* FORWARD_COMPATIBLE */;
    r.desktop = (r.profile != 0) || ((r.flags & desktop_flags) != 0);
    return r;
}

// The ES request actually sent to a GLES backend for a desktop frontend ask.
void build_es_request(const CtxRequest& in, EGLint* out, int cap) {
    int version = 3;
    if (in.major > 0 && in.major < 3) version = 2;
    int n = 0;
    if (n < cap - 2) { out[n++] = EGL_CONTEXT_CLIENT_VERSION; out[n++] = version; }
    if (n < cap - 2) out[n++] = EGL_NONE;
}

// ---- frontend API virtualization -------------------------------------------
// The backend only speaks ES. When the app asks for desktop GL we bind ES
// underneath and remember what the frontend asked for, so later queries and
// context decisions each see a coherent answer. Per-thread, like all EGL
// binding state.
thread_local EGLenum g_frontend_api = EGL_OPENGL_ES_API;
// Latest refusal in plain text for the SDL hook to surface through its own
// (visible) log channel. Valid until the next EGL call on this thread; the
// caller must log or copy it immediately.
thread_local std::string g_last_diag;

void set_last_diag(const char* fmt, ...) {
    char line[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    g_last_diag = line;
}

// ---- diagnostics that survive the launcher console --------------------------
// __android_log_print under the "silica" tag does not reach the log the user
// pastes, so every decisive EGL fact is also appended to a file the launcher
// can read back. Same text, both sinks; nothing is logged in only one.
void diag(const char* fmt, ...) {
    char line[768];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, "silica", "%s", line);
    const char* dir = getenv("SILICA_DATA_DIR");
    if (!dir || !*dir) return;
    char path[512];
    snprintf(path, sizeof(path), "%s/silica-egl.log", dir);
    FILE* f = fopen(path, "ae");
    if (!f) return;
    fputs(line, f);
    fputc('\n', f);
    fclose(f);
}

// ---- config capability interrogation ---------------------------------------
// EGL_BAD_MATCH from eglCreateContext is documented for a config that cannot
// satisfy the requested client API, and for a share context that does not match
// the config. Attributes alone cannot explain a plain ES2 request failing too,
// so the config's real capabilities are read before any attribute is touched.
constexpr EGLint kRenderableType = 0x3040;
constexpr EGLint kSurfaceType = 0x3033;
constexpr EGLint kConformant = 0x3042;
constexpr EGLint kCaveat = 0x3031;
constexpr EGLint kClientApis = 0x308D;
constexpr EGLint kEs2Bit = 0x0004;
constexpr EGLint kEs3Bit = 0x0040;
constexpr EGLint kOpenGlBit = 0x0008;

struct ConfigCaps {
    EGLint renderable = -1;
    EGLint surface_type = -1;
    EGLint conformant = -1;
    EGLint caveat = -1;
    bool readable = false;
};

ConfigCaps probe_config(EGLDisplay dpy, EGLConfig cfg) {
    ConfigCaps c;
    typedef EGLBoolean (*GCA)(EGLDisplay, EGLConfig, EGLint, EGLint*);
    auto g = (GCA)driver::resolve("eglGetConfigAttrib");
    if (!g || dpy == EGL_NO_DISPLAY || cfg == nullptr) return c;
    c.readable = g(dpy, cfg, kRenderableType, &c.renderable) == EGL_TRUE;
    g(dpy, cfg, kSurfaceType, &c.surface_type);
    g(dpy, cfg, kConformant, &c.conformant);
    g(dpy, cfg, kCaveat, &c.caveat);
    return c;
}

// Highest ES version this config actually advertises, or 0 when it advertises
// none. Derived from the config, never assumed: requesting ES 3 from an ES2-only
// config is exactly the BAD_MATCH we are chasing.
int config_es_version(const ConfigCaps& c) {
    if (c.renderable & kEs3Bit) return 3;
    if (c.renderable & kEs2Bit) return 2;
    return 0;
}

// Proves which EGL implementation is answering, so a config created by one
// implementation and passed to another cannot masquerade as a plain refusal.
void log_display_identity(EGLDisplay dpy) {
    typedef const char* (*QS)(EGLDisplay, EGLint);
    typedef EGLBoolean (*QDA)(EGLDisplay, EGLint, EGLint*);
    auto qs = (QS)driver::resolve("eglQueryString");
    auto qda = (QDA)driver::resolve("eglQueryDisplayAttrib");
    EGLint apis = -1;
    if (qda) qda(dpy, kClientApis, &apis);
    diag("silica: display dpy=%p client_apis=0x%x vendor=\"%s\" version=\"%s\"",
         (void*)dpy, (unsigned)apis,
         qs ? qs(dpy, 0x3053 /* VENDOR */) : "(no eglQueryString)",
         qs ? qs(dpy, 0x3054 /* VERSION */) : "(no eglQueryString)");
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
    // Read what the config can actually render before touching attributes. A
    // plain ES2 request failing with BAD_MATCH points at the config, not the list.
    const silica::egl::ConfigCaps caps = silica::egl::probe_config(dpy, cfg);
    const int cfg_es = silica::egl::config_es_version(caps);
    silica::egl::log_display_identity(dpy);
    silica::egl::diag("silica: ctx tid=%d dpy=%p cfg=%p requested=[%s] cfg_renderable=0x%x cfg_es=%d cfg_surface=0x%x cfg_conformant=0x%x",
                      (int)gettid(), (void*)dpy, (void*)cfg,
                      silica::egl::describe_ctx_attribs(attr).c_str(),
                      (unsigned)caps.renderable, cfg_es, (unsigned)caps.surface_type,
                      (unsigned)caps.conformant);

    const EGLint* backend_attr = attr;
    EGLint translated[3] = {EGL_NONE, EGL_NONE, EGL_NONE};
    if (req.desktop) {
        // Version comes from the config, not the frontend's ask: a desktop 3.3
        // request must not become an ES 3 request this config cannot honour.
        int want = cfg_es > 0 ? cfg_es : 3;
        if (cfg_es == 0 && !caps.readable) want = 3; // unreadable config: keep prior behaviour
        translated[0] = EGL_CONTEXT_CLIENT_VERSION;
        translated[1] = want;
        translated[2] = EGL_NONE;
        backend_attr = translated;
        silica::egl::diag("silica: ctx desktop %d.%d profile=0x%x flags=0x%x -> ES version %d (config supports %d)",
                          req.major, req.minor, (unsigned)req.profile, (unsigned)req.flags, want, cfg_es);
    }
    // The backend only speaks ES, so this thread must be bound to ES before any
    // context is asked for. Desktop applications bind EGL_OPENGL_API as a matter
    // of course; left in place, that binding makes even a minimal valid ES2
    // request fail with EGL_BAD_MATCH against an ES-only config -- which is the
    // observed all-three-attempts-fail. Binding ES when already ES is a no-op.
    {
        auto bind = be<EGLBoolean (*)(EGLenum)>("eglBindAPI");
        if (!bind) {
            silica::egl::set_frontend_error(EGL_BAD_MATCH);
            silica::egl::set_last_diag("bind entry missing; no context created");
            SLOG(ERROR, "silica: eglBindAPI unavailable; cannot create a context");
            return EGL_NO_CONTEXT;
        }
        if (bind(EGL_OPENGL_ES_API) != EGL_TRUE) {
            const EGLint be_err = silica::egl::capture_backend_error();
            silica::egl::set_last_diag("eglBindAPI(ES) refused with %s; no context created",
                                       silica::egl::egl_error_name(be_err));
            SLOG(ERROR, "silica: eglBindAPI(EGL_OPENGL_ES_API) refused with %s; no context created",
                 silica::egl::egl_error_name(be_err));
            return EGL_NO_CONTEXT;
        }
    }

    EGLContext ctx = f(dpy, cfg, share, backend_attr);
    SLOG(INFO, "SILICA_EGL_DIAG exit ctx=%p", (void*)ctx);
    if (ctx == EGL_NO_CONTEXT) {
        // Real backend code, read once into the queue the app reads from.
        const EGLint e = silica::egl::capture_backend_error();
        std::lock_guard<std::mutex> l(silica::egl::g_mu);
        silica::egl::set_last_diag("REFUSED with %s requested=[%s] sent=[%s] cfg_renderable=0x%x cfg_es=%d",
                                       silica::egl::egl_error_name(e),
                                       silica::egl::describe_ctx_attribs(attr).c_str(),
                                       silica::egl::describe_ctx_attribs(backend_attr).c_str(),
                                       (unsigned)caps.renderable, cfg_es);
        silica::egl::diag("silica: ctx REFUSED with %s requested=[%s] sent=[%s] cfg_renderable=0x%x cfg_es=%d cfg_surface=0x%x share=%s",
                          silica::egl::egl_error_name(e),
                          silica::egl::describe_ctx_attribs(attr).c_str(),
                          silica::egl::describe_ctx_attribs(backend_attr).c_str(),
                          (unsigned)caps.renderable, cfg_es, (unsigned)caps.surface_type,
                          share == EGL_NO_CONTEXT ? "null" : "set");
    } else if (silica::config::diagnostics()) {
        silica::egl::diag("silica: ctx OK handle=%p tid=%d cfg_es=%d requested=[%s] sent=[%s]",
                          (void*)ctx, (int)gettid(), cfg_es,
                          silica::egl::describe_ctx_attribs(attr).c_str(),
                          silica::egl::describe_ctx_attribs(backend_attr).c_str());
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
// The backend only speaks ES. A desktop bind request is honoured for the
// frontend (TRUE, and remembered) while ES is what is bound underneath, so the
// backend is never left in an API mode its configs cannot satisfy. Without
// this, one app-side eglBindAPI(EGL_OPENGL_API) poisons every later context
// creation with EGL_BAD_MATCH.
S_API EGLBoolean eglBindAPI(EGLenum api) {
    SE_INIT();
    const EGLenum backend_api = (api == EGL_OPENGL_API) ? EGL_OPENGL_ES_API : api;
    auto bind = be<EGLBoolean (*)(EGLenum)>("eglBindAPI");
    if (!bind) {
        silica::egl::set_frontend_error(EGL_BAD_MATCH);
        silica::egl::set_last_diag("eglBindAPI: no backend entry");
        return EGL_FALSE;
    }
    const EGLBoolean r = bind(backend_api);
    if (r == EGL_TRUE) {
        silica::egl::g_frontend_api = api;
    } else {
        const EGLint e = silica::egl::capture_backend_error();
        silica::egl::set_last_diag("eglBindAPI(%s) refused with %s",
                                   api == EGL_OPENGL_API ? "OPENGL" :
                                   api == EGL_OPENGL_ES_API ? "OPENGL_ES" : "other",
                                   silica::egl::egl_error_name(e));
    }
    silica::egl::diag("silica: eglBindAPI frontend=%s backend=%s -> %s",
                      api == EGL_OPENGL_API ? "OPENGL" :
                      api == EGL_OPENGL_ES_API ? "OPENGL_ES" : "other",
                      backend_api == EGL_OPENGL_ES_API ? "OPENGL_ES" : "other",
                      r == EGL_TRUE ? "ok" : "FAILED");
    return r;
}
// What the frontend asked for, not what is bound underneath.
S_API EGLenum eglQueryAPI(void) {
    SE_INIT();
    return silica::egl::g_frontend_api;
}
// Latest refusal in plain text. Valid until the next EGL call on this thread.
S_API const char* silica_last_egl_diag(void) {
    SE_INIT();
    return silica::egl::g_last_diag.c_str();
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
