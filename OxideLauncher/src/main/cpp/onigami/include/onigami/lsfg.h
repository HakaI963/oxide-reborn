#pragma once
// ONIGAMI frame generation backend (v1.17.0).
//
// Provenance: interpolation design (frame history, EMA pacing,
// bidirectional warp/blend, t=k/N timing) is derived from LSFG-VK
// (https://github.com/Snap888/lsfg-vk, MIT, Copyright (c) 2024 LSFG-VK
// Contributors). The Vulkan implicit-layer plumbing
// (VK_LAYER_LSFG_frame_generation, vkCreateSwapchainKHR/vkQueuePresentKHR
// interception) is NOT reused: ONIGAMI owns EGL/GLES contexts and no Vulkan
// swapchain, and upstream's own synth present is an unfinished TODO.
// Integration point: the game's real present path is the JNI bridge
// (ctxbridges/gl_bridge.c), which resolves EGL from the system driver, so
// libonigami's own eglSwapBuffers wrapper never fires. The bridge therefore
// calls onigami_hook_swap_buffers() around its backend swap (gated by the
// ONIGAMI_PRESENT_HOOK env, fallback = direct swap). Display, contexts and
// surfaces stay 100% on the proven bridge+driver path; only pre/post swap
// work is added. Backend below: GPU-side retention (blit, no CPU readback)
// plus real interpolated presents (blend of retained frames, never a
// duplicate). Multipliers 2x/3x/4x.
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

// Called when onigami.json is (re)applied; also invoked automatically (~1Hz)
// when the file changes, so in-game FG changes apply live, no restart.
void on_config_applied();
// Surface lifetime: resize/recreate must drop retained frames.
void on_surface_changed(int w, int h);
void on_surface_lost();
void on_process_end();

// Present hook: call after each backend eglSwapBuffers with the REAL result.
// Counts only successful presents; failures increment dropped.
bool on_swap_buffers(bool swap_ok, int w, int h);

// Full Nx path. precapture() runs BEFORE the backend swap and retains the
// outgoing frame; postgenerate() runs AFTER a successful swap and presents
// mult-1 interpolated frames, each counted only on success. At most mult-1
// generated presents per native frame: no queue can accumulate.
void precapture(EGLDisplay d, int w, int h);
bool postgenerate(EGLDisplay d, EGLSurface s, int w, int h);

bool supported();
bool enabled();
int multiplier();
std::string status_text();
Counters counters();
std::string last_reason();

}  // namespace lsfg
}  // namespace onigami
