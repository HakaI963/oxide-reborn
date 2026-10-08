// Silica program vault: persistent program-binary store. Own implementation.
// Deterministic keys (FNV-1a over pipeline version + target + sorted shader
// content hashes), thread-safe, budget-enforced with oldest-first eviction.
// Layout per entry: <4-byte format LE><binary>. Directory:
//   $SILICA_DATA_DIR/program_vault/<16-hex-key>.bin
// A missing/unreadable vault only disables reuse, never rendering.
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utime.h>
namespace silica::cache {
namespace {
std::mutex g_mu;
std::string g_dir;
long long g_budget = 64LL * 1024 * 1024;
bool g_ready = false;
bool g_usable = false;
void ensure_dir() {
    if (g_ready) return;
    g_ready = true;
    const char* base = getenv("SILICA_DATA_DIR");
    if (!base || !*base) return;
    g_dir = std::string(base) + "/program_vault";
    mkdir(g_dir.c_str(), 0700);
    struct stat st;
    g_usable = (stat(g_dir.c_str(), &st) == 0);
}
long long dir_bytes() {
    long long total = 0;
    DIR* d = opendir(g_dir.c_str());
    if (!d) return 0;
    int counted = 0;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        struct stat st;
        std::string p = g_dir + "/" + e->d_name;
        if (stat(p.c_str(), &st) == 0) total += st.st_size;
        if (++counted > 4096) break;
    }
    closedir(d);
    return total;
}
void evict_to_fit(long long need) {
    // Oldest-first by mtime until need bytes are free or the vault is empty.
    for (int pass = 0; pass < 2; pass++) {
        long long total = dir_bytes();
        if (total + need <= g_budget) return;
        DIR* d = opendir(g_dir.c_str());
        if (!d) return;
        std::string oldest;
        long long oldest_mtime = 0;
        bool first = true;
        int counted = 0;
        while (dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            std::string p = g_dir + "/" + e->d_name;
            struct stat st;
            if (stat(p.c_str(), &st) != 0) continue;
            if (first || st.st_mtime < oldest_mtime) {
                oldest = p;
                oldest_mtime = st.st_mtime;
                first = false;
            }
            if (++counted > 4096) break;
        }
        closedir(d);
        if (oldest.empty()) return;
        unlink(oldest.c_str());
    }
}
std::string path_for(uint64_t key) {
    char name[32];
    snprintf(name, sizeof(name), "%016llx.bin", (unsigned long long)key);
    return g_dir + "/" + name;
}
} // namespace
uint64_t fnv1a64(const void* data, size_t len, uint64_t seed = 1469598103934665603ULL);
void set_budget_mb(int mb) {
    std::lock_guard<std::mutex> l(g_mu);
    if (mb < 0) mb = 0;
    if (mb > 512) mb = 512;
    g_budget = (long long)mb * 1024 * 1024;
}
long long budget_bytes() {
    std::lock_guard<std::mutex> l(g_mu);
    return g_budget;
}
// Returns true + fills format/binary on vault hit.
bool vault_get(uint64_t key, uint32_t& format, std::vector<unsigned char>& binary) {
    std::lock_guard<std::mutex> l(g_mu);
    ensure_dir();
    if (!g_usable || g_budget == 0) return false;
    FILE* f = fopen(path_for(key).c_str(), "rb");
    if (!f) return false;
    unsigned char hdr[4];
    bool ok = fread(hdr, 1, 4, f) == 4;
    if (ok) {
        format = (uint32_t)hdr[0] | ((uint32_t)hdr[1] << 8) | ((uint32_t)hdr[2] << 16) | ((uint32_t)hdr[3] << 24);
        fseek(f, 0, SEEK_END);
        long n = ftell(f) - 4;
        if (n <= 0 || n > 64 * 1024 * 1024) ok = false;
        if (ok) {
            binary.resize((size_t)n);
            fseek(f, 4, SEEK_SET);
            ok = fread(binary.data(), 1, (size_t)n, f) == (size_t)n;
        }
    }
    fclose(f);
    return ok;
}
void vault_put(uint64_t key, uint32_t format, const void* data, size_t len) {
    if (!data || len == 0 || len > 64 * 1024 * 1024) return;
    std::lock_guard<std::mutex> l(g_mu);
    ensure_dir();
    if (!g_usable || g_budget == 0) return;
    evict_to_fit((long long)len + 4);
    std::string tmp = path_for(key) + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return;
    unsigned char hdr[4] = {(unsigned char)(format & 255), (unsigned char)((format >> 8) & 255),
                            (unsigned char)((format >> 16) & 255), (unsigned char)((format >> 24) & 255)};
    bool ok = fwrite(hdr, 1, 4, f) == 4 && fwrite(data, 1, len, f) == len;
    fclose(f);
    if (ok) rename(tmp.c_str(), path_for(key).c_str());
    else unlink(tmp.c_str());
}
} // namespace silica::cache
