# ONIGAMI + LSFG-VK — v1.17.0 integration note

## What v1.17.0 ships: real 2x/3x/4x frame generation
- Frame Generation toggle (default OFF), multiplier 2x/3x/4x, artifact
  guard (default ON), persisted in the shared settings model, shown in
  launcher Renderer settings and the in-game Renderer tab. All three apply
  LIVE: every change rewrites onigami.json immediately and the native backend
  re-reads it (~1Hz mtime watch). No restart for frame generation. (Vault,
  render scale, upscaler, coalescing, diagnostics stay restart-required:
  they are context-init time.)
- Full present path in libonigami.so (lsfg_framegen.cpp), reached through
  the REAL present path: the JNI bridge (ctxbridges) resolves system EGL
  directly, so libonigami's own eglSwapBuffers wrapper never fires there.
  When ONIGAMI is active the launcher sets ONIGAMI_PRESENT_HOOK and the
  bridge calls onigami_hook_swap_buffers() around its backend swap
  (fallback = direct swap, bit-for-bit old behavior). Display, contexts and
  surfaces stay 100% on the proven bridge+driver path; only pre/post swap
  work is added:
  1. pre-swap GPU blit retains the outgoing frame (no CPU readback);
  2. native backend swap, counted only on success;
  3. mult-1 blend-interpolated frames (mix of retained N-1/N at t=k/N —
     real intermediate frames, never duplicates), each presented and counted
     only on success. The chain stops at the first failure.
  At most mult-1 generated frames per native frame: no queue can accumulate.
  History, FBO/textures/program/VBO/VAO are reused; resize, surface loss,
  context change and shutdown drop retained state. Only real GL/swap errors
  count toward auto-disable (3 in a row, logged); "not ready yet" skips
  never disable. All game GL state is saved/restored, and FG GL calls use
  raw backend procs (never through ONIGAMI translation), so Vitrail and
  shader-pack rendering is unaffected.
- Artifact guard (real, in-shader): where the retained pair disagrees
  strongly (scene cuts, particles, fast HUD motion) the output snaps to the
  current frame instead of smearing a ghost.
- Advanced FPS meter: Native (CallbackBridge, game-rendered) vs Displayed
  (native stats file framegen_stats.json, confirmed presents only) plus the
  actual FG state and multiplier, plus the backend reason line while no
  measurement exists yet. Displayed is never native x multiplier. FG off →
  displayed mirrors native (same path, still really counted).
- LSFG-VK MIT attribution in THIRD_PARTY.md + source headers.

## Why not a Vulkan-layer drop-in (exact technical reason)
1. ONIGAMI owns EGL/GLES contexts end to end but does NOT own the present
   call: the game swaps through the JNI bridge's system-EGL pointers.
2. LSFG-VK IS a Vulkan implicit layer (vkCreateSwapchainKHR /
   vkQueuePresentKHR interception, compute queue, swapchain images). None of
   those entry points fire in the ONIGAMI path.
3. Upstream's own synth present is unfinished ((void)slot no-op). Packaging
   the .so as-is would add an unconfigured layer, not frames — so the
   missing pieces were built EGL-side: bridge hook, retention, blend
   interpolation, pacing, presentation, lifetime.
4. Replacing the stack with Vulkan/Zink was explicitly rejected
   (docs/ONIGAMI_ARCHITECTURE.md); Vitrail compat relies on GL-on-ES.
5. Not applicable on phones, not exposed: VR stereo views, HDR swapchain
   formats. Full Lucas-Kanade motion (PYRAMID_LEVELS=4, BLOCK_SIZE=8,
   MAX_ITERATIONS=5, constants in source) stays staged pending Adreno 825
   profiling; the shader already takes uT/uGuard for a motion-warp upgrade.

## Verify on device
```sh
adb logcat -s onigami:V GLBridge:V | grep -iE "onigami-fg|present hook"
# expect after game start:
#   onigami-fg: config init dir=... enabled=0 mult=2x guard=1
# after enabling FG in the Renderer tab (~1s later):
#   onigami-fg: config reloaded live ... enabled=1 mult=3x guard=1
#   onigami-fg: GL backend ready (...x..., guard=1)
#   onigami-fg: generated frames live (3x, +2/frame)
# Advanced meter then shows Native vs Displayed from real counters.
# Any DISABLED line names the exact reason; native rendering continues.
```

## Validation (honest status)
- Unit tests (CI): rates math, stats parsing, multiplier gating.
- Native gate: onigami.yml (4 ABIs + export check + host probe incl. the new
  hook export). Kotlin/APK gate: ci.yml + release.yml. No local builds.
- Device matrix (Adreno 825 native vs displayed per multiplier, gen-ms,
  pacing, artifacts, memory, Vitrail packs) requires hardware measurement;
  not claimed here. Baseline reminder: FG never means the game simulated
  more frames.
