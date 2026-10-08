// Silica shim config. Own minimal parser (the file is written by our own
// Kotlin authoring; keys are fixed so a full JSON library is unnecessary).
#include "silica/shim_config.h"
#include <mutex>
#include <string>
#include <cstdio>
namespace silica::config {
namespace {
std::once_flag g_once;
std::string g_profile = "AUTO";
int g_vault_mb = 64;
bool g_dedup = true;
bool g_diag = false;
long find_num(const std::string& s, const char* key, long dflt) {
    auto p = s.find(key);
    if (p == std::string::npos) return dflt;
    long v = dflt;
    if (sscanf(s.c_str() + p + strlen(key), " : %ld", &v) == 1) return v;
    if (sscanf(s.c_str() + p + strlen(key), ":%ld", &v) == 1) return v;
    return dflt;
}
void init_once() {
    const char* dir = getenv("SILICA_DATA_DIR");
    if (!dir || !*dir) return;
    std::string path = std::string(dir) + "/silica.json";
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return;
    char buf[2048];
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[n] = 0;
    std::string s(buf);
    auto q = s.find("profile");
    if (q != std::string::npos) {
        if (s.find("PERFORMANCE", q) != std::string::npos) g_profile = "PERFORMANCE";
        else if (s.find("QUALITY", q) != std::string::npos) g_profile = "QUALITY";
        else if (s.find("BALANCED", q) != std::string::npos) g_profile = "BALANCED";
        else g_profile = "AUTO";
    }
    long v = find_num(s, "program_vault_mb", 64);
    if (v < 0) v = 0;
    if (v > 512) v = 512;
    g_vault_mb = (int)v;
    g_dedup = find_num(s, "state_coalescing", 1) != 0;
    g_diag = find_num(s, "diagnostics", 0) != 0;
}
} // namespace
void load_once() { std::call_once(g_once, init_once); }
int program_vault_mb() { load_once(); return g_vault_mb; }
bool dedup_enabled() { load_once(); return g_dedup; }
bool diagnostics() { load_once(); return g_diag; }
const char* profile() { load_once(); return g_profile.c_str(); }
} // namespace silica::config
