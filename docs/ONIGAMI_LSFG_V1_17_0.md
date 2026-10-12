# ONIGAMI + LSFG-VK — v1.17.0 integration note

## What v1.17.0 ships
- Frame Generation toggle (default OFF) + 2x-only multiplier, persisted in
  the shared settings model (AllSettings.onigamiFrameGen /
  onigamiFrameGenMultiplier), shown in launcher Renderer settings and the
  in-game Renderer tab. All rows are restart-required (native reads
  onigami.json at context init).
- EGL-level backend (libonigami.so): 2-frame history, EMA pacing
  (4ms..200ms, LSFG scheduler-derived), monotonic native/displayed/
  generated/dropped counters that count ONLY successful presents, surface
  resize/recreate handling, clean disable path.
- Advanced FPS meter: Native (CallbackBridge, game frames) vs Displayed
  (native stats file framegen_stats.json, confirmed presents only).
  Displayed is never native x multiplier. When FG is off, displayed == native
  from the same present path. Stale/absent stats render as "measuring", not
  as a fabricated number.
- LSFG-VK MIT attribution in THIRD_PARTY.md + source headers.

## Why not a Vulkan-layer drop-in (exact technical reason)
1. ONIGAMI owns EGL/GLES contexts end to end (egl_context.cpp chains to the
   single host driver); it creates no VkInstance/VkDevice/VkQueue and owns
   no VkSwapchain. There is no Vulkan context to share with LSFG-VK.
2. LSFG-VK IS a Vulkan implicit layer: it hooks vkCreateSwapchainKHR /
   vkGetSwapchainImagesKHR / vkQueuePresentKHR and needs a compute queue +
   swapchain images. None of those entry points fire in the ONIGAMI path.
3. Upstream's own synth present is unfinished: frame_generator.cpp OnPresent
   ends in a "(void)slot" no-op with a TODO to blit slot.image into an
   acquired swapchain image and present per synth frame. Only the real frame
   is presented today. Packaging the .so as-is would add an unconfigured
   layer, not frames.
4. Replacing the stack with Vulkan/Zink was explicitly rejected
   (docs/ONIGAMI_ARCHITECTURE.md); Vitrail/shader-pack compat relies on the
   GL-on-ES path.

## Smallest viable adaptation (what was done)
- Vendored LSFG-VK stage logic as reference (pyramid/LK/warp/blend/pacing);
  implemented the EGL analogue in lsfg_framegen.cpp with the same pacing
  constants and history depth, GPU-side (no CPU readbacks), resources reused,
  history dropped on resize/recreate.
- v1.17.0 enables the counting/pacing/history path and a 2x interpolated
  present gate; the generated counter increments ONLY on a confirmed second
  successful swap. Until the GLES retained-frame blit validates on Adreno 825
  under shader load, generated stays 0 and the status string says so — the
  meter then correctly shows displayed == native.
- 3x/4x, HDR swapchain formats, UI-mask tuning and Mali paths are NOT
  exposed. Adreno 7xx README claims do not transfer to Adreno 825 without
  measurement.

## Validation (honest status)
- Focused unit tests: OnigamiFrameGenTest (rates math, stats parsing,
  multiplier gating), GameOverlayLogic advanced-format tests, native-present
  counter invariants reviewed in egl_context hook (success-only counting).
- Device matrix (native FPS off vs on, displayed FPS, overhead, pacing,
  artifacts, memory, Vitrail packs) requires Adreno 825 hardware and is NOT
  claimed here. CI builds (ci.yml on PR, onigami.yml dispatch for
  libonigami.so ABIs, release.yml for APKs) are the build gate; no local
  NDK builds were used.
- Baseline reminder: >200 FPS unshaded, ~20-25 FPS shaded. FG is not a
  substitute for native optimization; higher displayed rate never means the
  game simulated more frames.
