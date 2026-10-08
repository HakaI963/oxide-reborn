// Silica GL state tracking with deduplication. Own implementation.
// Perf lever #1: skip redundant binds/mode sets before they reach the driver.
#include "silica/silica.h"
#include <unordered_map>
#include <mutex>
namespace silica::state {
static std::unordered_map<unsigned, unsigned> g_bound;
static std::mutex g_mu;
static unsigned long long g_hits = 0, g_skips = 0;
bool bind(unsigned target, unsigned id) {
    std::lock_guard<std::mutex> l(g_mu);
    g_hits++;
    auto it = g_bound.find(target);
    if (it != g_bound.end() && it->second == id) { g_skips++; return false; }
    g_bound[target] = id;
    return true;
}
} // namespace silica::state
extern "C" {
unsigned long long silica_state_hits(void) { return 0; }
unsigned long long silica_state_skips(void) { return 0; }
}
