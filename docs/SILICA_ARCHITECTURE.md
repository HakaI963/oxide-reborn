# Silica architecture (v1.19.0)

Silica is a new renderer built from ONIGAMI plus win-fg frame generation,
placed where each piece actually runs. This note records the full diagnosis
(launcher -> game -> renderer -> present) so the placement is reviewable.

## Launch flow (measured in source)

1. `VMActivity` (`:game` process — the game never shares the launcher UI
   PID) drives `GameLauncher`: JVM args (`-Dorg.lwjgl.opengl.libname=<lib>`,
   always `-Dorg.lwjgl.vulkan.libname=libvulkan.so`), `/initEnv` env map
   (`POJAV_RENDERER=<rendererId>`, `DRIVER_PATH`, renderer env), DLC/open
   of the renderer library (hard fail for first-party renderers, no silent
   substitution).
2. Graphics API choice (`VersionConfig.graphicsApi`, global or per
   instance) reaches the game ONLY as MC
   `options.txt:preferredGraphicsBackend` (`GameHandler`). No JVM arg, no
   env, no mod. `VULKAN` there means Minecraft 26.2+ renders its own Vulkan
   (Brass/RenderPearl); older versions ignore it.
3. The JNI bridge (`ctxbridges`) picks its table by `POJAV_RENDERER`:
   `opengles*` (all builtin ids incl. Silica's `opengles3_oxide_silica`)
   -> GL bridge over system EGL. Zink/virgl ids are legacy-plugin only.
4. LWJGL resolves GL through the renderer lib; Vulkan (if the game creates
   one) resolves through the system loader, with the launcher's
   `libvulkan.so` interposition keeping SDL+LWJGL on one handle.

## Where frame generation sits (and why)

- The GLES present path is owned end-to-end by the bridge+driver; a Vulkan
  layer can never see those frames. The previous EGL-blend backend is
  removed (it could not reach the real presents).
- win-fg (`libwin_fg.so`, implicit layer `VK_LAYER_WIN_framegen`)
  intercepts the game's OWN Vulkan swapchain
  (`CreateSwapchain/DestroySwapchain/QueuePresent/AcquireNextImage`,
  complete present path with extra images + pacing, device-proven on Adreno
  750). That is the only place generated frames can enter the real display
  path — hence Silica requires the MC-Vulkan path for FG and runs as pure
  translation otherwise (stated in the UI, never faked).
- Activation package (written by `SilicaRenderer` at launch):
  manifest JSON with ABSOLUTE `.so` path into `<silica>/layers/`,
  `VK_ADD_LAYER_PATH` there, `WIN_FG_ENABLE/MULT/MODEL/PERF_PRESET/PACING`
  env for init values, `WIN_FG_CONF` conf.toml (hot-reloaded live, no
  restart), `WIN_FG_STATS_DIR` for the meter channel.
- Translation core stays `libonigami.so` (refcounted dlopen); the silica
  data dir namespaces `onigami.json`/vault/log so nothing collides with
  ONIGAMI. No `libsilica.so` exists (release gate still asserts its
  absence); the gate now requires `libwin_fg.so` in 64-bit APKs instead.
- Upstream (MIT The412Banner + AMD FidelityFX notice, both retained) is
  vendored verbatim under `cpp/winfg/upstream/`; Silica-local changes are
  marked and listed in `SILICA_PATCHES.md` (P1: FPS stats file only).

## Honest unknowns (device-unproven)

- Android loader discovery of the bundled implicit layer (belt:
  absolute-path manifest + `VK_ADD_LAYER_PATH` + enable env).
- Whether Brass initializes Vulkan successfully on Adreno 825 here.
- SurfaceFlinger present behavior for extra frames (win-fg's own caveat was
  about Bannerlator's compositor; pacing option exists for this).
- 3x/4x cost on mobile; diag counters will show it.

## Verify on device

- In-game Renderer tab (Silica): diagnostics line shows
  `so=`/`manifest=` package proof plus live
  `native/displayed/gen/drop [reason]` counters.
- `adb logcat -s win-fg:V`: `CreateInstance ok`, `DEVICE-CTX`,
  `framegen init ok`, `2x insert live`.
- Advanced FPS meter: Native (CallbackBridge) vs Displayed (layer counts,
  never native x multiplier).
