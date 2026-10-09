// ONIGAMI onigami.json loader. Own schema, minimal tolerant parser.
#include "onigami/config.h"
#include "onigami/backend.h"
#include <cstdlib>
#include <fstream>
#include <sstream>
namespace onigami {
namespace {
Config g_cfg;
int parse_int_after(const std::string& j, const char* key, int dflt) {
    size_t p = j.find(key);
    if (p == std::string::npos) return dflt;
    size_t c = j.find(':', p);
    if (c == std::string::npos) return dflt;
    return atoi(j.c_str() + c + 1);
}
bool parse_bool_after(const std::string& j, const char* key, bool dflt) {
    size_t p = j.find(key);
    if (p == std::string::npos) return dflt;
    size_t c = j.find(':', p);
    if (c == std::string::npos) return dflt;
    std::string v = j.substr(c + 1, 8);
    if (v.find("true") != std::string::npos) return true;
    if (v.find("false") != std::string::npos) return false;
    return dflt;
}
} // namespace
std::string config_path() { return data_dir() + "/onigami.json"; }
Config load_config() {
    Config c;
    std::ifstream f(config_path());
    if (!f) return c;
    std::stringstream ss;
    ss << f.rdbuf();
    std::string j = ss.str();
    c.onigami_version = parse_int_after(j, "onigami_version", 1);
    size_t pp = j.find("profile");
    if (pp != std::string::npos) {
        size_t cc = j.find(':', pp);
        size_t q1 = j.find('"', cc);
        size_t q2 = (q1 == std::string::npos) ? std::string::npos : j.find('"', q1 + 1);
        if (q1 != std::string::npos && q2 != std::string::npos) c.profile = j.substr(q1 + 1, q2 - q1 - 1);
    }
    c.program_vault_mb = parse_int_after(j, "program_vault_mb", 64);
    c.state_coalescing = parse_bool_after(j, "state_coalescing", true);
    c.diagnostics = parse_bool_after(j, "diagnostics", true);
    if (c.program_vault_mb < 0) c.program_vault_mb = 0;
    if (c.program_vault_mb > 1024) c.program_vault_mb = 1024;
    return c;
}
const Config& current_config() { return g_cfg; }
void apply_config(const Config& c) { g_cfg = c; }
} // namespace onigami
