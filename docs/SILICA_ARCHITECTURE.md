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

## Own GLES engine (v1.19.0+): placement proof

win-fg serves only real Vulkan swapchains. The GLES game path needed its
own engine, placed at the only interception point that provably runs every
displayed frame: the JNI bridge swap. Engine: libsilica_fg.so, clean-room
GLES 3.1 compute (quarter luma, block-match flow, median, warp-blend),
self-gated (default off), full state save/restore, JNI-explicit config,
game-process env export, translation reload. Meter: engine stats first,
win-fg fallback, else mirror/measuring. Verify: logcat silica-fg tag +
Renderer-tab diagnostics (counters + source).

## Device test protocol (own engine, no release needed)

1. Install the CI debug APK artifact (this code is NOT released).
2. Pick Silica, start the game, open menu -> Renderer tab.
3. Diagnostics line must flip from `src=none` to `src=gles` with rising
   `native/displayed/gen` counters once FG is toggled on.
4. `adb logcat -s silica-fg:V` must show `engine ready` then
   `generated frames live`. Any `DISABLED: <reason>` line names the exact
   cause (ES version, FBO, shader, swap).
5. Advanced FPS meter: Native vs Displayed separate, never multiplied.

## Own-engine interception matrix (v1.19.x)

The game can present through (a) the JNI bridge (GLFW path) or (b) SDL's
own EGL handle. Both are covered, each exactly once:

- (a) bridge `gl_swap_buffers` calls `silica_fg_present_hook` explicitly
  (resolved always, engine self-gates; default off = direct swap).
- (b) the process-wide dlsym proxy (`sdl_dlopen_hook.c`, ByteHook,
  established mechanism) wraps any dlsym-resolved `eglSwapBuffers`
  (SDL, LWJGL-EGL, …) with the engine trampoline
  (`silica_fg_wrap_swap`, single real-pointer slot: one driver in-process).
  Bridge-resolved pointers bypass libc dlsym, so no frame is ever wrapped
  twice. Engine-absent/misconfigured = original pointer, bit-identical.

## Config without env (file channel)

The `:game` process may never see launcher env. All engine state
converges through files in the shared UID dir instead:

- Every settings change (any process) writes `fg_engine.cfg`
  (enabled/mult/guard). The engine watches mtime ~1Hz; the file always
  wins, so OFF sticks within a second no matter who toggled it.
- Data dir bootstraps from `/proc/self/cmdline`
  (`<pkg>[:suffix]` -> `/data/user/0/<pkg>/files/silica`, marker-verified).
- JNI pushes remain for immediacy; files are the truth.

## Yield guards (FG must never crater native)

- Pre-flow skip when our EMA cost already exceeds 80% of the native
  interval; per-subframe late-skip past 75% of it. Reason surfaces live
  (`yielding (gpu-bound)`); the meter keeps measuring.
- 3 consecutive real errors disable with a logged reason; setup skips never
  count. Teardown frees everything on disable.

Test build: CI debug APK after libsilica_fg commit.
