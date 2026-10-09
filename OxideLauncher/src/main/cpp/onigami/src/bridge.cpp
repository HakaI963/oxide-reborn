// ONIGAMI process/surface lifecycle. Own code: stores launcher-owned state.
#include "onigami/bridge.h"
#include "onigami/backend.h"
#include "onigami/config.h"
#include <mutex>
#include <string>
namespace {
std::mutex g_m;
std::string g_data_dir;
void* g_window = nullptr;
int g_w = 0;
int g_h = 0;
int g_mode = 0;
bool g_has_surface = false;
} // namespace
extern "C" {
ONIGAMI_API int onigami_on_process_start(const char* data_dir) {
    std::lock_guard<std::mutex> l(g_m);
    g_data_dir = data_dir ? data_dir : "";
    onigami::diag_printf("onigami: process_start dir=%s", g_data_dir.c_str());
    onigami::apply_config(onigami::load_config());
    return 0;
}
ONIGAMI_API int onigami_on_surface(void* w, int width, int height) {
    std::lock_guard<std::mutex> l(g_m);
    g_window = w;
    g_w = width;
    g_h = height;
    g_has_surface = true;
    onigami::diag_printf("onigami: surface %dx%d", width, height);
    return 0;
}
ONIGAMI_API int onigami_on_surface_lost(void) {
    std::lock_guard<std::mutex> l(g_m);
    g_window = nullptr;
    g_has_surface = false;
    onigami::diag_write("onigami: surface_lost");
    return 0;
}
ONIGAMI_API int onigami_on_process_end(void) {
    std::lock_guard<std::mutex> l(g_m);
    g_window = nullptr;
    g_has_surface = false;
    onigami::diag_write("onigami: process_end");
    return 0;
}
ONIGAMI_API int onigami_set_mode(int mode) {
    std::lock_guard<std::mutex> l(g_m);
    if (mode != 0 && mode != 1) return -1;
    g_mode = mode;
    return 0;
}
ONIGAMI_API int onigami_get_mode(void) {
    std::lock_guard<std::mutex> l(g_m);
    return g_mode;
}
} // extern "C"
namespace onigami {
const char* bridge_data_dir_cstr() {
    std::lock_guard<std::mutex> l(g_m);
    return g_data_dir.c_str();
}
} // namespace onigami
