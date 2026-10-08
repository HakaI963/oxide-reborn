// Silica GL state tracking with deduplication. Own implementation.
// Every wrapper asks first: redundant calls never reach the driver. Hits and
// skips are counted and exported (silica_state_hits/skips) so profiling can
// prove the win instead of assuming it. Coalescing follows silica.json
// (state_coalescing) unless overridden via set_dedup.
#include "silica/state_in.h"
#include "silica/shim_config.h"
#include <unordered_map>
#include <mutex>
namespace silica::state {
namespace {
std::mutex g_mu;
std::unordered_map<unsigned, unsigned> g_buf;
std::unordered_map<unsigned long long, unsigned> g_tex; // (unit<<32)|target
std::unordered_map<unsigned, bool> g_caps;
unsigned g_prog = 0;
bool g_prog_set = false;
unsigned g_vao = 0;
bool g_vao_set = false;
unsigned g_unit = 0x84C0; // GL_TEXTURE0
int g_vp[4] = {0, 0, 0, 0};
bool g_vp_set = false;
int g_override = -1; // -1 follows silica.json
unsigned long long g_hits = 0, g_skips = 0;
bool eff() {
    if (g_override >= 0) return g_override != 0;
    return config::dedup_enabled();
}
} // namespace
void set_dedup(bool on) {
    std::lock_guard<std::mutex> l(g_mu);
    g_override = on ? 1 : 0;
}
bool use_program(unsigned p) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (eff() && g_prog_set && g_prog == p) { g_skips++; return false; }
    g_prog = p; g_prog_set = true;
    return true;
}
bool active_unit(unsigned unit) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    g_unit = unit;
    return true; // active-unit selects always reach the driver (cheap, stateful)
}
unsigned current_unit() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_unit;
}
bool bind_texture(unsigned unit, unsigned target, unsigned id) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (!eff()) return true;
    unsigned long long k = ((unsigned long long)unit << 32) | target;
    auto it = g_tex.find(k);
    if (it != g_tex.end() && it->second == id) { g_skips++; return false; }
    g_tex[k] = id;
    return true;
}
bool bind_buffer(unsigned target, unsigned id) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (!eff()) return true;
    auto it = g_buf.find(target);
    if (it != g_buf.end() && it->second == id) { g_skips++; return false; }
    g_buf[target] = id;
    return true;
}
bool bind_framebuffer(unsigned target, unsigned id) {
    return bind_buffer(0x8D40u + target, id); // separate key space, same logic
}
bool bind_renderbuffer(unsigned target, unsigned id) {
    return bind_buffer(0x9D00u + target, id);
}
bool bind_vertex_array(unsigned id) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (eff() && g_vao_set && g_vao == id) { g_skips++; return false; }
    g_vao = id; g_vao_set = true;
    return true;
}
bool cap(unsigned c, bool on) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (!eff()) return true;
    auto it = g_caps.find(c);
    if (it != g_caps.end() && it->second == on) { g_skips++; return false; }
    g_caps[c] = on;
    return true;
}
bool viewport(int x, int y, int w, int h) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    if (eff() && g_vp_set && g_vp[0] == x && g_vp[1] == y && g_vp[2] == w && g_vp[3] == h) { g_skips++; return false; }
    g_vp[0] = x; g_vp[1] = y; g_vp[2] = w; g_vp[3] = h; g_vp_set = true;
    return true;
}
unsigned long long hits() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_hits;
}
unsigned long long skips() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_skips;
}
} // namespace silica::state
extern "C" {
unsigned long long silica_state_hits(void) { return silica::state::hits(); }
unsigned long long silica_state_skips(void) { return silica::state::skips(); }
}
