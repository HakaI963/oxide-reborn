# Copper Oxide renderer

Copper Oxide is the product now: Oxide Launcher's independent Android
Minecraft renderer. Lineage is MobileGlues by MobileGL-Dev (LGPL-2.1, see
THIRD_PARTY.md); the Kotlin core, launch wiring and native bridge fixes in
this repository are Oxide's own. This is a rebuild, not a lightly modified
fork.

## What was inspected

- `OxideLauncher/src/main/jni/egl_bridge.c`: JNI/renderer dispatcher, FPS,
  swap interval, window lifecycle.
- `OxideLauncher/src/main/jni/ctxbridges/gl_bridge.c`: EGL display/config/
  context/surface, make-current, swap-buffers, 1x1 pbuffer fallback.
- `OxideLauncher/src/main/java/dev/oxide/launcher/game/launch/GameLauncher.kt`:
  `setRendererEnv`, EGL probe, LIBGL_ES resolution.
- `game/renderer/renderers/CopperOxideRenderer.kt` + `CopperOxideConfig.kt`:
  env construction, config.json authoring.
- `game/renderer/copperoxide/`: new independent core (this rebuild).

## Highest-impact bottlenecks found

1. `gl_swap_surface()` blocked the render thread for 750 ms
   (`usleep(750000)`) on every surface loss, even when Android had already
   released the window (rotation/split-screen). Replaced with a bounded
   early-exit poll: 8x50 ms, bail on dead window. Worst case ~400 ms,
   typical 50-100 ms. No behavior change on slow drivers.
2. `eglChooseConfig` was called twice per context (count then fetch).
   Single call for up to 32 configs: one fewer driver round-trip per context.
3. `malloc + memset` replaced with `calloc` (one call, same zeroing).
4. `calculateFPS()` used wall-clock `time(NULL)` (1 s granularity, NTP
   jumps) and plain ints shared across threads. Now
   `CLOCK_MONOTONIC` buckets with volatile counters (render-thread writer,
   Java reader for display only).
5. Missing NULL guards: `getenv("POJAV_RENDERER")` / `"LIBGL_ES")`
   dereferenced without checks (native crash if unset), `currentBundle`
   dereferenced in `gl_swap_buffers()` with no context yet, and
   `ANativeWindow_acquire(NULL)`. All guarded; unset renderer defaults to
   the Copper Oxide path with a log line instead of a crash.
6. `%p` log with an `EGLint` argument (undefined). Now `%04x`.
7. Launch path ran `eglInitialize + eglGetConfigs + eglTerminate` (driver
   load) on every launch. Cached per process via
   `CopperOxideCapabilities.EglProbeCache`.
8. `LIBGL_ES` fallback did `replace("opengles","").replace("_5","")`
   (two allocations, wrong for ids like `opengles3_oxide_copper`) and forced
   `"2"` on any probe failure. Now
   `CopperOxideCapabilities.resolveLibGlEs` (no churn, honest fallback to 3).
9. `authorCopperOxideConfig` rewrote `config.json` on every launch when
   tuning was on. Now skips unchanged content (one fewer flash write) and
   writes atomically (tmp + rename).
10. `CopperOxideRenderer.getRendererEnv` called `mkdirs()` unconditionally
    (syscall even when present). Now skipped when `isDirectory`.

## What was NOT done (on purpose)

- No GL version, vendor/renderer string or extension spoofing.
  `customGLVersion`, `hideMGEnvLevel>0`, fake caps: deliberately unoffered.
  Capabilities are detected, never claimed.
- No FPS claims. Runtime benchmarking needs a device running Minecraft;
  unavailable here. Gains above are code-level (fewer syscalls, fewer driver
  round-trips, shorter stalls) validated by unit tests, lint and builds, not
  by measured frame rates.
- The vendored `libmobileglues.so` binary is untouched (no source in this
  repo). All native changes are in Oxide's own JNI bridge, which is built
  from source here and covered by CI's NDK build.

## New independent core

- `game/renderer/copperoxide/CopperOxideIdentity.kt`: product constants,
  honest summary, log line. Renderer string in Minecraft comes from the
  driver; Oxide brands the picker, logs and `OXIDE_RENDERER_FLAVOR`, never
  fakes GL strings.
- `CopperOxideEnv.kt`: pure env construction (4 verified keys + optional
  `MG_DIR_PATH`), no IO, no churn.
- `CopperOxideCapabilities.kt`: substring-safe extension check, honest GLES
  resolution, per-process EGL probe cache.
- `CopperOxideDeviceTuning.kt`: pure GPU-vendor detect + RAM-aware cache
  policy (0 never raised, low-RAM capped at 32 MB).

## Validation

- GitHub Actions is authoritative. No local Gradle/NDK downloads required.
- `ci.yml`: unit tests + lint + debug APK + arm64 release (R8) on every PR.
- `paparazzi.yml`: screenshot verify on UI paths; goldens re-recorded via
  explicit dispatch after intentional UI changes (control editor simple mode).
- New unit tests: env/caps/device tuning, simple/advanced gating, new
  default layout. Existing `DefaultControlLayoutTest` still pins the legacy
  asset; `NewDefaultLayoutTest` pins `emulated/new.json`.

## Controls (bundled with this rebuild)

- Default layout is now `assets/emulated/new.json` (clean "new" identity,
  same verified schema). Legacy `default_layout.json` is the fallback, then
  the embedded fallback. Existing installs are untouched.
- Control editor defaults to Simple: layers select, grid, add, inspector,
  preview switch, save. Snapping, styles, focus, reorder and preview-device
  hide behind the Advanced switch. All features remain, one toggle away.
