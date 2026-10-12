# ONIGAMI + LSFG-VK — v1.17.0 integration note

## What v1.17.0 ships: full 2x frame generation
- Frame Generation toggle (default OFF) + 2x-only multiplier, persisted in
  the shared settings model (AllSettings.onigamiFrameGen /
  onigamiFrameGenMultiplier), shown in launcher Renderer settings and the
  in-game Renderer tab. Restart-required (native reads onigami.json at
  context init).
- Full present path in libonigami.so (lsfg_framegen.cpp):
  1. pre-swap GPU blit retains the outgoing frame (no CPU readback);
  2. native backend swap, counted only on success;
  3. blend-interpolated frame (mix of retained N-1/N at t=0.5 — a real
     intermediate frame, never a duplicate) rendered and presented via a
     second backend swap, counted only on success.
  At most one generated frame per native frame: no queue can accumulate.
  History (2 frames), FBO/textures/program/VBO/VAO are reused; resize,
  surface loss, context change and shutdown drop retained state; 3
  consecutive backend errors disable FG with a logged reason while the
  native path continues untouched. All game GL state we touch is
  saved/restored, and all FG GL calls use raw backend procs (never through
  ONIGAMI translation), so Vitrail/shader-pack rendering is unaffected.
- Interpolation quality staging (honest): the dispatched stage is BLEND.
  LSFG's 4-level hierarchical Lucas-Kanade motion stage (PYRAMID_LEVELS=4,
  BLOCK_SIZE=8, MAX_ITERATIONS=5) is not dispatched on mobile GPUs pending
  Adreno 825 profiling; constants are in the source and the shader already
  takes uT so a motion-vector warp plugs in without changing the path.
- Advanced FPS meter: Native (CallbackBridge, game frames) vs Displayed
  (native stats file framegen_stats.json, confirmed presents only).
  Displayed is never native x multiplier. FG off → displayed mirrors native
  (same path). No fresh measurement → UI shows `measuring`.
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
   layer, not frames — so the missing pieces were built: retention, blend
   interpolation, pacing, presentation, lifetime, all EGL-side.
4. Replacing the stack with Vulkan/Zink was explicitly rejected
   (docs/ONIGAMI_ARCHITECTURE.md); Vitrail/shader-pack compat relies on the
   GL-on-ES path.

## Validation (honest status)
- Unit tests (run in CI): OnigamiFrameGenTest (rates math, stats parsing,
  multiplier gating), FpsAdvancedLogicTest (no fabricated FPS).
- Native compile gate is .github/workflows/onigami.yml (dispatch, 4 ABIs +
  export check); PR CI runs unit tests, lint and debug APK. No local builds.
- Device matrix (Adreno 825 native vs displayed, overhead, gen-ms, pacing,
  artifacts, memory, Vitrail packs) requires hardware measurement; not
  claimed here. Baseline reminder: >200 FPS unshaded, ~20-25 FPS shaded —
  FG is not a substitute for native optimization, and a higher displayed
  rate never means the game simulated more frames.
