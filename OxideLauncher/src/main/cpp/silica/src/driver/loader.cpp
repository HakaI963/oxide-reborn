// Silica driver loader. Own code.
// Contract (reference-derived, reimplemented):
// - RTLD_LAZY | RTLD_LOCAL like the working loader: eager binding is what can
//   refuse the open in a constrained linker namespace; lazy binding defers
//   symbol resolution to first call, which is all a game needs.
// - Resolve order per symbol: host eglGetProcAddress first (the driver's own
//   dispatch, correct for that implementation), dlsym on the opened handles
//   second. This is how the working renderer reaches every backend entry
//   without assuming dlsym visibility.
// - Single-driver rule retained: ANGLE split across the two halves collapses
//   to the system driver with a log line (that split answers null strings).
// - Diagnostics: every open and every first-resolution logs path, flags and
//   outcome. Pointers only (%p); no app, path-beyond-soname, or user data.
#include "silica/driver.h"
#include "silica/silica.h"
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <android/log.h>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::driver {
namespace {
std::once_flag g_once;
std::mutex g_mu;
void* g_egl = nullptr;
void* g_gles = nullptr;
void* g_host_proc = nullptr; // host eglGetProcAddress
bool g_angle = false;
std::string g_identity = "unopened";
bool g_repaired = false;
bool looks_angle(const char* p) {
    if (!p) return false;
    std::string s(p);
    for (auto& c : s) c = (char)tolower(c);
    return s.find("angle") != std::string::npos;
}
void* try_open(const char* name) {
    // LAZY like the working loader: NOW can refuse the open outright in a
    // constrained namespace even when every needed symbol would resolve.
    // (Log priority is branched: it must be a compile-time constant.)
    void* h = dlopen(name, RTLD_LAZY | RTLD_LOCAL);
    if (h) SLOG(DEBUG, "silica: dlopen(%s, LAZY|LOCAL) -> %p", name ? name : "(null)", h);
    else SLOG(ERROR, "silica: dlopen(%s, LAZY|LOCAL) -> (null): %s", name ? name : "(null)", dlerror());
    return h;
}
typedef void (*proc_t)(void);
void init_once() {
    const char* egl_ov = getenv("SILICA_EGL_PATH");
    const char* gles_ov = getenv("SILICA_GLES_PATH");
    const char* egl_names[] = {egl_ov, "libEGL.so", nullptr};
    const char* gles_names[] = {gles_ov, "libGLESv3.so", "libGLESv2.so", nullptr};
    const char* egl_used = nullptr;
    const char* gles_used = nullptr;
    for (int i = 0; egl_names[i] && !g_egl; i++) {
        if (!egl_names[i] || !*egl_names[i]) continue;
        g_egl = try_open(egl_names[i]);
        if (g_egl) egl_used = egl_names[i];
    }
    for (int i = 0; gles_names[i] && !g_gles; i++) {
        if (!gles_names[i] || !*gles_names[i]) continue;
        g_gles = try_open(gles_names[i]);
        if (g_gles) gles_used = gles_names[i];
    }
    bool egl_a = looks_angle(egl_used);
    bool gles_a = looks_angle(gles_used);
    if (g_egl && g_gles && (egl_a != gles_a) && !g_repaired) {
        SLOG(ERROR, "silica: ANGLE supplied only the %s half; using the system driver for both",
             gles_a ? "GLES" : "EGL");
        if (gles_a) {
            dlclose(g_gles);
            g_gles = try_open("libGLESv2.so");
            gles_used = "libGLESv2.so";
        } else {
            dlclose(g_egl);
            g_egl = try_open("libEGL.so");
            egl_used = "libEGL.so";
        }
        g_repaired = true;
        egl_a = gles_a = false;
    }
    g_angle = egl_a && gles_a;
    if (g_egl) {
        g_host_proc = dlsym(g_egl, "eglGetProcAddress");
        if (g_host_proc) SLOG(DEBUG, "silica: host eglGetProcAddress -> %p", g_host_proc);
        else SLOG(ERROR, "silica: host eglGetProcAddress missing: %s", dlerror());
    }
    char buf[256];
    snprintf(buf, sizeof(buf), "egl=%s gles=%s angle=%d hostproc=%d",
             egl_used ? egl_used : "(none)", gles_used ? gles_used : "(none)",
             g_angle ? 1 : 0, g_host_proc ? 1 : 0);
    g_identity = buf;
    SLOG(INFO, "silica: build %s backend %s", silica_build_id(), g_identity.c_str());
    if (!g_egl || !g_gles || !g_host_proc) {
        SLOG(ERROR, "silica: backend incomplete (%s); EGL bootstrap cannot proceed", g_identity.c_str());
    }
}
} // namespace
bool ensure() {
    std::call_once(g_once, init_once);
    return g_egl && g_gles && g_host_proc;
}
void* host_proc(const char* name) {
    if (!ensure() || !name) return nullptr;
    typedef void (*P)(void);
    typedef P (*GPA)(const char*);
    auto gpa = (GPA)g_host_proc;
    void* p = (void*)gpa(name);
    return p;
}
void* resolve(const char* name) {
    if (!ensure() || !name) return nullptr;
    // dlsym-first on the opened backend handles, host dispatch second. This is
    // the working renderer's contract (its proc_address is a plain dlsym): a
    // core entry such as eglBindAPI is guaranteed present in the backend's own
    // dynamic symbol table, while a host eglGetProcAddress may refuse core
    // entries on some implementations. Extensions, which dlsym cannot see,
    // still resolve through the host dispatch fallback.
    void* p = g_egl ? dlsym(g_egl, name) : nullptr;
    if (p) return p;
    p = g_gles ? dlsym(g_gles, name) : nullptr;
    if (p) return p;
    return host_proc(name);
}
bool angle_in_use() { ensure(); return g_angle; }
const char* identity() { ensure(); return g_identity.c_str(); }
} // namespace silica::driver
