#pragma once
// ONIGAMI frame generation backend (v1.17.0).
//
// Provenance: interpolation math (pyramid downscale, Lucas-Kanade motion,
// bidirectional warp, UI-aware blend) is derived from LSFG-VK
// (https://github.com/Snap888/lsfg-vk, MIT, Copyright (c) 2024 LSFG-VK
// Contributors). The Vulkan implicit-layer plumbing of LSFG-VK
// (vkCreateSwapchainKHR / vkQueuePresentKHR interception) is NOT reused:
// ONIGAMI owns EGL/GLES contexts and no VkInstance/VkDevice/VkSwapchain,
// and upstream's synth present path is itself an unfinished TODO. This
// backend therefore implements EGL-level history + pacing + a real GPU-side
// interpolated present (blend of retained frames, never a duplicate).
// Only 2x is enabled in v1.17.0; 3x/4x await on-device validation
// (Adreno 825 cost unknown).
#include <string>

namespace onigami {
namespace lsfg {

struct Counters {
  unsigned long long native_frames = 0;
  unsigned long long displayed_frames = 0;
  unsigned long long generated_frames = 0;
  unsigned long long dropped = 0;
  double gen_ms_ema = 0.0;
};

// Called when onigami.json is (re)applied.
void on_config_applied();
// Surface lifetime: resize/recreate must drop retained frames.
void on_surface_changed(int w, int h);
void on_surface_lost();
void on_process_end();

// Present hook: call after each eglSwapBuffers. swap_ok must be the REAL
// backend return. Returns true if a generated frame was also presented.
// Only successful presents are counted; failures increment dropped.
bool on_swap_buffers(bool swap_ok, int w, int h);

bool supported();
bool enabled();
int multiplier();
std::string status_text();
Counters counters();
const char* last_reason();

}  // namespace lsfg
}  // namespace onigami
