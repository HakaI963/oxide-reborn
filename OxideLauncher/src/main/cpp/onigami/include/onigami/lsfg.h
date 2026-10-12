#pragma once
// ONIGAMI frame generation backend (v1.17.0).
//
// Provenance: interpolation design (2-frame history, EMA pacing,
// bidirectional warp/blend, t=(synth+1)/multiplier timing) is derived from
// LSFG-VK (https://github.com/Snap888/lsfg-vk, MIT, Copyright (c) 2024
// LSFG-VK Contributors). The Vulkan implicit-layer plumbing
// (VK_LAYER_LSFG_frame_generation, vkCreateSwapchainKHR/vkQueuePresentKHR
// interception) is NOT reused: ONIGAMI owns EGL/GLES contexts and no Vulkan
// swapchain, and upstream's own synth present is an unfinished TODO. This
// backend implements the EGL analogue: GPU-side frame retention (blit, no
// CPU readback) plus a real interpolated present (blend of the two retained
// frames at t=0.5, never a duplicate). Only 2x is enabled in v1.17.0.
#include <EGL/egl.h>

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

// Present hook: call after each eglSwapBuffers with the REAL backend result.
// Counts only successful presents; failures increment dropped.
bool on_swap_buffers(bool swap_ok, int w, int h);

// Full 2x path (v1.17.0). GPU-side only, success-only accounting.
// precapture() runs BEFORE the native backend swap and retains the outgoing
// frame; postgenerate() runs AFTER a successful native swap, renders the
// blend-interpolated frame and issues a second backend swap directly.
// postgenerate() returns true iff a generated frame reached the display.
void precapture(EGLDisplay d, int w, int h);
bool postgenerate(EGLDisplay d, EGLSurface s, int w, int h);

bool supported();
bool enabled();
int multiplier();
std::string status_text();
Counters counters();

}  // namespace lsfg
}  // namespace onigami
