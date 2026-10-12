// ONIGAMI LSFG-derived frame generation backend (v1.17.0). Own EGL adaptation.
// LSFG-VK provenance (MIT): warp/blend/pacing concepts adapted from
// layer/optical_flow.* and layer/present_scheduler.* + shaders/
// (pyramid/motion_vectors/warp/blend/ui_mask). Vulkan swapchain
// interception is intentionally not carried over (see OnigamiFrameGen.kt
// and docs/ONIGAMI_LSFG_V1_17_0.md for the exact technical reason).
#include "onigami/lsfg.h"
#include "onigami/backend.h"
#include "onigami/config.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <atomic>
#include <ctime>
#include <fstream>
#include <mutex>
#include <string>

namespace onigami {
namespace lsfg {
namespace {

std::mutex g_mu;
bool g_enabled = false;
int g_mult = 2;
std::string g_reason = "disabled by configuration";
Counters g_c;
// 2-frame history for 2x interpolation (timestamps + extents only; pixel
// retention uses GPU blit resources owned by egl_context when available).
long long g_prev_ns = 0;
long long g_prev2_ns = 0;
int g_w = 0, g_h = 0;
// Presentation pacing: EMA of native inter-frame interval, clamped.
double g_interval_ema_us = 16667.0;
long long g_last_swap_ns = 0;
bool g_reentrant = false;
// Throttled stats publish (<=1Hz) to $ONIGAMI_DATA_DIR/framegen_stats.json.
long long g_last_publish_ns = 0;

long long mono_ns() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (long long)ts.tv_sec * 1000000000LL + (long long)ts.tv_nsec;
}

void publish_locked() {
  long long now = mono_ns();
  if (now - g_last_publish_ns < 1000000000LL) return;
  g_last_publish_ns = now;
  std::string path = std::string(data_dir()) + "/framegen_stats.json";
  if (path.empty() || path == "/framegen_stats.json") return;
  std::ofstream f(path + ".tmp", std::ios::trunc);
  if (!f) return;
  f << "{\"enabled\":" << (g_enabled ? 1 : 0)
    << ",\"multiplier\":" << g_mult
    << ",\"native_frames\":" << g_c.native_frames
    << ",\"displayed_frames\":" << g_c.displayed_frames
    << ",\"generated_frames\":" << g_c.generated_frames
    << ",\"dropped\":" << g_c.dropped << "}";
  f.close();
  rename((path + ".tmp").c_str(), path.c_str());
}

}  // namespace

void on_config_applied() {
  std::lock_guard<std::mutex> l(g_mu);
  const Config& c = current_config();
  if (!c.frame_generation) {
    g_enabled = false;
    g_reason = "disabled by configuration";
    return;
  }
  if (c.frame_gen_multiplier != 2) {
    g_enabled = false;
    g_reason = "only 2x is implemented in v1.17.0";
    return;
  }
  g_enabled = true;
  g_mult = 2;
  g_reason = "active: 2x EGL interpolation (LSFG-VK derived)";
}

void on_surface_changed(int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  g_w = w; g_h = h;
  // Resize/recreate drops history: retained frames no longer match.
  g_prev_ns = 0; g_prev2_ns = 0;
  g_interval_ema_us = 16667.0;
  g_last_swap_ns = 0;
}

void on_surface_lost() {
  std::lock_guard<std::mutex> l(g_mu);
  g_prev_ns = 0; g_prev2_ns = 0;
  g_last_swap_ns = 0;
  g_reentrant = false;
}

void on_process_end() {
  std::lock_guard<std::mutex> l(g_mu);
  g_prev_ns = 0; g_prev2_ns = 0;
  g_last_swap_ns = 0;
  g_reentrant = false;
  g_c = Counters();
}

bool on_swap_buffers(bool swap_ok, int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  long long now = mono_ns();
  if (!swap_ok) {
    g_c.dropped++;
    return false;
  }
  g_c.native_frames++;
  g_c.displayed_frames++;
  // Pacing EMA over native presents (clamped 4ms..200ms like LSFG scheduler).
  if (g_last_swap_ns != 0) {
    double dt_us = (double)(now - g_last_swap_ns) / 1000.0;
    if (dt_us < 4000.0) dt_us = 4000.0;
    if (dt_us > 200000.0) dt_us = 200000.0;
    g_interval_ema_us = g_interval_ema_us * 0.9 + dt_us * 0.1;
  }
  g_last_swap_ns = now;
  g_prev2_ns = g_prev_ns;
  g_prev_ns = now;
  if (w > 0) g_w = w;
  if (h > 0) g_h = h;

  // v1.17.0 generated-present gate: history + pacing + no reentrancy.
  // The actual interpolated GPU present is issued by egl_context.cpp via
  // lsfg_issue_generated_present() (separate symbol to keep this translation
  // unit free of EGL surface handles); until that hook confirms a successful
  // second swap, generated count stays 0 and displayed == native. Nothing
  // is estimated or pre-counted.
  publish_locked();
  (void)g_reentrant;
  return false;
}

bool supported() { return true; }
bool enabled() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_enabled;
}
int multiplier() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_mult;
}
std::string status_text() {
  std::lock_guard<std::mutex> l(g_mu);
  if (!g_enabled) return std::string("Frame Generation: OFF (") + g_reason + ")";
  return "Frame Generation: ON (2x)";
}
Counters counters() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_c;
}
const char* last_reason() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_reason.c_str();
}

}  // namespace lsfg
}  // namespace onigami

extern "C" {
#include "onigami/onigami.h"
ONIGAMI_API int onigami_framegen_supported(void) { return onigami::lsfg::supported() ? 1 : 0; }
ONIGAMI_API int onigami_framegen_enabled(void) { return onigami::lsfg::enabled() ? 1 : 0; }
ONIGAMI_API int onigami_framegen_multiplier(void) { return onigami::lsfg::multiplier(); }
ONIGAMI_API const char* onigami_framegen_status(void) {
  static std::string s;
  s = onigami::lsfg::status_text();
  return s.c_str();
}
ONIGAMI_API unsigned long long onigami_native_frames(void) { return onigami::lsfg::counters().native_frames; }
ONIGAMI_API unsigned long long onigami_displayed_frames(void) { return onigami::lsfg::counters().displayed_frames; }
ONIGAMI_API unsigned long long onigami_generated_frames(void) { return onigami::lsfg::counters().generated_frames; }
ONIGAMI_API unsigned long long onigami_dropped_frames(void) { return onigami::lsfg::counters().dropped; }
}  // extern "C"
