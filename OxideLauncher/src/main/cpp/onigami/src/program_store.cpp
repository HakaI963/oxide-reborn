// ONIGAMI program vault: FNV-1a64 keys, byte budget, oldest-first eviction.
// Own code. All failures degrade to "recompile" (never fatal, never logged
// as errors in normal operation).
#include "onigami/vault.h"
#include "onigami/backend.h"
#include "onigami/config.h"
#include "onigami/translate.h"
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <algorithm>
#include <vector>
namespace onigami {
namespace {
std::string vault_dir() { return data_dir() + "/program_vault"; }
std::string vault_path(const std::string& key_hex) { return vault_dir() + "/" + key_hex + ".essl"; }
} // namespace
int translator_schema_version() { return 2; }
std::string vault_key_hex(const char* a, const char* b) {
    std::string cat;
    if (a) cat += a;
    cat += "\x1f";
    cat += "schema=" + std::to_string(translator_schema_version()) + "\x1f";
    if (b) cat += b;
    unsigned long long h = fnv1a64(cat.data(), cat.size());
    char buf[17];
    snprintf(buf, sizeof(buf), "%016llx", h);
    return std::string(buf);
}
bool vault_lookup(const std::string& key_hex, std::vector<char>& out) {
    out.clear();
    if (key_hex.empty()) return false;
    std::string p = vault_path(key_hex);
    FILE* f = fopen(p.c_str(), "rb");
    if (!f) return false;
    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) out.insert(out.end(), buf, buf + n);
    fclose(f);
    if (out.empty()) {
        unlink(p.c_str());
        return false;
    }
    utimensat(AT_FDCWD, p.c_str(), nullptr, 0); // touch for LRU recency
    return true;
}
void vault_store(const std::string& key_hex, const void* data, size_t len) {
    if (key_hex.empty() || !data || !len) return;
    long budget_mb = current_config().program_vault_mb;
    if (budget_mb <= 0) return; // 0 disables reuse (honest toggle)
    std::string dir = vault_dir();
    mkdir(dir.c_str(), 0755);
    std::string tmp = dir + "/." + key_hex + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return;
    size_t w = fwrite(data, 1, len, f);
    fclose(f);
    if (w != len) {
        unlink(tmp.c_str());
        return;
    }
    rename(tmp.c_str(), vault_path(key_hex).c_str());
    // Enforce budget: oldest mtime first.
    long budget = budget_mb * 1024L * 1024L;
    DIR* d = opendir(dir.c_str());
    if (!d) return;
    struct Entry {
        std::string path;
        long long mtime;
        long long size;
    };
    std::vector<Entry> entries;
    long long total = 0;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        std::string p = dir + "/" + e->d_name;
        struct stat st;
        if (stat(p.c_str(), &st) != 0) continue;
        if (!S_ISREG(st.st_mode)) continue;
        entries.push_back({p, (long long)st.st_mtime, (long long)st.st_size});
        total += st.st_size;
    }
    closedir(d);
    if (total <= budget) return;
    std::sort(entries.begin(), entries.end(), [](const Entry& a, const Entry& b) { return a.mtime < b.mtime; });
    for (const Entry& e : entries) {
        if (total <= budget) break;
        unlink(e.path.c_str());
        total -= e.size;
    }
}
} // namespace onigami
