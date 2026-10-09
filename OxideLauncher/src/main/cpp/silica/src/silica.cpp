// Silica entry: version + probe-safe strings + counters. Own code.
#include "silica/silica.h"
#include <string>
#include <mutex>
namespace silica {
static std::string g_probe_renderer;
static std::string g_probe_version;
static std::string g_out;
static std::mutex g_mu;
void note_probe(const char* r, const char* v) {
    std::lock_guard<std::mutex> l(g_mu);
    if (r && *r) g_probe_renderer = r;
    if (v && *v) g_probe_version = v;
}
const char* safe_string(const char* live, const char* what) {
    std::lock_guard<std::mutex> l(g_mu);
    if (live && *live) { g_out = live; return g_out.c_str(); }
    // Equivalent of upstream 5974f49 behavior, own implementation: answer from
    // the probe (driver properties, not per-context) instead of crashing.
    g_out = what[0] == 'R' ? g_probe_renderer : g_probe_version;
    if (g_out.empty()) g_out = "<unknown>";
    return g_out.c_str();
}
const char* probe_renderer() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_probe_renderer.c_str();
}
const char* probe_version() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_probe_version.c_str();
}
} // namespace silica
extern "C" {
const char* silica_version(void) { return "silica-0.1.0-dev (own backend, under construction)"; }
const char* silica_renderer_string(void) {
    std::lock_guard<std::mutex> l(silica::g_mu);
    if (silica::g_probe_renderer.empty()) return "<unknown>";
    silica::g_out = silica::g_probe_renderer;
    return silica::g_out.c_str();
}
#ifndef SILICA_BUILD_COMMIT_STR
#define SILICA_BUILD_COMMIT_STR "unknown"
#endif
extern "C" {
const char* silica_build_id(void) {
    static std::string id;
    static std::once_flag once;
    std::call_once(once, []() {
        id = std::string("silica-") + SILICA_BUILD_COMMIT_STR + " built " + __DATE__ + " " + __TIME__;
    });
    return id.c_str();
}
}
const char* silica_version_string(void) {
    std::lock_guard<std::mutex> l(silica::g_mu);
    if (silica::g_probe_version.empty()) return "<unknown>";
    silica::g_out = silica::g_probe_version;
    return silica::g_out.c_str();
}
}
