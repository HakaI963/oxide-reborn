// Silica driver loader. Own code.
// Single-driver rule (5974f49-equivalent, rewritten): the GLES and EGL halves
// must come from the same implementation. ANGLE split against the system driver
// (contexts created in one, GL calls answered by the other with null strings)
// is the worst outcome, so on a split the ANGLE half is closed and the system
// library is opened for it, with a log line. No MobileGlues code is involved.
#include "silica/driver.h"
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <android/log.h>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::driver {
namespace {
std::once_flag g_once;
void* g_egl = nullptr;
void* g_gles = nullptr;
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
    void* h = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    return h;
}
void init_once() {
    const char* egl_ov = getenv("SILICA_EGL_PATH");
    const char* gles_ov = getenv("SILICA_GLES_PATH");
    const char* egl_names[] = {egl_ov, "libEGL.so", nullptr};
    const char* gles_names[] = {gles_ov, "libGLESv3.so", "libGLESv2.so", nullptr};
    const char* egl_used = nullptr;
    const char* gles_used = nullptr;
    for (int i = 0; egl_names[i] && !g_egl; i++) {
        if (!egl_names[i]) continue;
        g_egl = try_open(egl_names[i]);
        if (g_egl) egl_used = egl_names[i];
    }
    for (int i = 0; gles_names[i] && !g_gles; i++) {
        if (!gles_names[i]) continue;
        g_gles = try_open(gles_names[i]);
        if (g_gles) gles_used = gles_names[i];
    }
    bool egl_a = looks_angle(egl_used);
    bool gles_a = looks_angle(gles_used);
    if (g_egl && g_gles && (egl_a != gles_a) && !g_repaired) {
        // Split brain: repair by dropping the ANGLE half to the system driver.
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
    char buf[256];
    snprintf(buf, sizeof(buf), "egl=%s gles=%s angle=%d",
             egl_used ? egl_used : "(none)", gles_used ? gles_used : "(none)", g_angle ? 1 : 0);
    g_identity = buf;
    if (!g_egl || !g_gles) {
        SLOG(ERROR, "silica: backend open failed (%s)", g_identity.c_str());
    }
}
} // namespace
bool ensure() {
    std::call_once(g_once, init_once);
    return g_egl && g_gles;
}
void* resolve(const char* name) {
    if (!ensure() || !name) return nullptr;
    void* p = dlsym(g_egl, name);
    if (p) return p;
    return dlsym(g_gles, name);
}
bool angle_in_use() { ensure(); return g_angle; }
const char* identity() { ensure(); return g_identity.c_str(); }
} // namespace silica::driver
