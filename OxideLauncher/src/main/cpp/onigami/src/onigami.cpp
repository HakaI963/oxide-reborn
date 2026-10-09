// ONIGAMI entry: version + retryable-probe strings + counters. Own code.
#include "onigami/onigami.h"
#include "onigami/backend.h"
#include <mutex>
#include <string>
namespace {
std::mutex g_mu;
std::string g_out;
std::string g_diag;
} // namespace
namespace onigami {
void set_last_diag(const char* msg) {
    std::lock_guard<std::mutex> l(g_mu);
    g_diag = msg ? msg : "";
}
} // namespace onigami
extern "C" {
ONIGAMI_API int onigami_version(void) { return 1; }
#ifndef ONIGAMI_BUILD_COMMIT_STR
#define ONIGAMI_BUILD_COMMIT_STR "unknown"
#endif
ONIGAMI_API const char* onigami_build_id(void) {
    static std::string id;
    static std::once_flag once;
    std::call_once(once, []() {
        id = std::string("onigami-") + ONIGAMI_BUILD_COMMIT_STR + " built " + __DATE__ + " " + __TIME__;
    });
    return id.c_str();
}
// Probe strings: cached driver facts, never null, never "". A sentinel with
// an explicit marker (not a version-looking string) until probed, so LWJGL
// never receives the empty string that crashed apiParseVersion.
ONIGAMI_API const char* onigami_renderer_string(void) {
    std::lock_guard<std::mutex> l(g_mu);
    const std::string& c = onigami::cached_renderer();
    g_out = c.empty() ? "<onigami-unprobed>" : c;
    return g_out.c_str();
}
ONIGAMI_API const char* onigami_version_string(void) {
    std::lock_guard<std::mutex> l(g_mu);
    const std::string& v = onigami::cached_version();
    if (v.empty()) {
        g_out = "3.3 (Compatibility Profile) ONIGAMI translated (unprobed)";
        return g_out.c_str();
    }
    if (v.find("3.2") != std::string::npos)
        g_out = "4.6 (Compatibility Profile) ONIGAMI translated ES3.2 (backend: " + v + ")";
    else
        g_out = "3.3 (Compatibility Profile) ONIGAMI translated (backend: " + v + ")";
    return g_out.c_str();
}
ONIGAMI_API const char* onigami_last_diag(void) {
    std::lock_guard<std::mutex> l(g_mu);
    return g_diag.c_str();
}
} // extern "C"
