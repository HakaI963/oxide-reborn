// Silica program binary cache. Own implementation, budget from silica.json.
// Key: shader source hash + GPU id + driver version. Hit avoids recompile +
// relink entirely (the shader-heavy win); miss compiles once then stores.
#include <string>
namespace silica::cache {
static long long g_budget_bytes = 64LL * 1024 * 1024;
void set_budget_mb(int mb) {
    if (mb <= 0) { g_budget_bytes = 0; return; }
    if (mb > 512) mb = 512;
    g_budget_bytes = (long long)mb * 1024 * 1024;
}
long long budget_bytes(void) { return g_budget_bytes; }
} // namespace silica::cache
