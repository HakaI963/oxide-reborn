# ONIGAMI 1.14.0 — delivery status and honest test matrix

This file records what has actually been verified versus what has not. It is
updated as each check runs; nothing here is assumed.

## Build / CI evidence (all machine-verified)

| Check | Result | Where |
|---|---|---|
| Native build, 4 ABIs (arm64-v8a, armeabi-v7a, x86_64, x86) | PASS | workflow "Onigami native", run 37939337919 |
| Native symbol gate per ABI (own exports present; no mobileglues/copperoxide/silica) | PASS | "Verify exports and no foreign linkage", all 4 ABI jobs |
| Native symbol gate (own exports present, no mobileglues/copperoxide/silica) | PASS | "Verify exports and no foreign linkage" step, all 4 ABIs |
| libonigami.so committed to jniLibs for all 4 ABIs | PASS | commit a1c6e67e + successors |
| CI: unit tests, lint, debug APK, APK integrity gates, release R8 | PASS | workflow "CI" on main HEAD |
| Release: 5 APK variants built, signed, zipaligned, integrity-checked | PASS | workflow "Release", run 37967063785 |
| Release renderer gate (libonigami.so present, libsilica.so absent) | PASS (machine-verified in the packaged APK) | gate output: libonigami_so_entries=4 (arm64-v8a, armeabi-v7a, x86, x86_64), libsilica_so_entries=0 |

## Compatibility matrix — ACTUAL device results

Device testing requires installing on the POCO F7 (Adreno 825). No device
results are claimed here until a launch log or capture is attached.

| Test | Status |
|---|---|
| Minecraft 26.2 vanilla renders a world | NOT TESTED |
| Minecraft 26.3 vanilla renders a world | NOT TESTED |
| 26.2 + Sodium | NOT TESTED |
| 26.2 + Sodium Extra | NOT TESTED |
| 26.2 + Iris + shaderpack (compiles, links, executes) | NOT TESTED |
| 26.3 + Iris + shaderpack (compiles, links, executes) | NOT TESTED |
| Context/surface lifecycle recovery | NOT TESTED |
| FPS / frame-time measurements | NOT TESTED |

Release APK artifacts WITH THE DISPATCH FIX (run 38014563788, HEAD f2c78304):

- release-apk-all (275 MB)
- release-apk-arm64 (153 MB)
- release-apk-arm (145 MB)
- release-apk-x86_64 (156 MB)
- release-apk-x86 (146 MB)

Renderer gate on the fixed build (universal APK, machine-verified):
libonigami_so_entries=4
(lib/arm64-v8a, lib/armeabi-v7a, lib/x86, lib/x86_64), libsilica_so_entries=0.

Release APK artifacts WITH ALL ROUND-2 FIXES (run 38019541175, HEAD bf8f63cb):

- release-apk-all (275 MB)
- release-apk-arm64 (153 MB) <- use this on the POCO F7
- release-apk-arm (145 MB)
- release-apk-x86_64 (156 MB)
- release-apk-x86 (146 MB)

Renderer gate on the fixed build (arm64 APK, machine-verified):
libonigami_so_entries=1 (lib/arm64-v8a/libonigami.so), libsilica_so_entries=0.
Universal APK gate from the previous run: 4 entries, 0 silica.

Automated test evidence in this build:
- Host translator suite: 37/37 assertions pass on CI (real gui shaders +
  clouds-like buffer-sampler shader + legacy + passthrough + negatives).
- Native 4-ABI build + symbol gate: PASS.
- CI (unit tests, lint, debug APK, R8): PASS.

Superseded artifacts (pre-fix run 37967063785, do NOT use for device testing):
Release APK artifacts (run 37967063785):

- release-apk-all (275 MB)
- release-apk-arm64 (153 MB)
- release-apk-arm (145 MB)
- release-apk-x86_64 (156 MB)
- release-apk-x86 (146 MB)

Install and run:

```sh
adb install -r OxideLauncher-1.14.0-all-release.apk
adb logcat -s onigami:V
```

## Root cause fixed in this update (device-evidence backed)

- LWJGL on EGL resolves GL entry points through `eglGetProcAddress`. ONIGAMI's
  `eglGetProcAddress` answered every name with the backend pointer, so the
  entire game bypassed shader translation, state tracking and version mapping
  while directly-linked symbols stayed correct. Raw desktop GLSL
  (`#version 150`) reached the Adreno GLES compiler and failed
  `minecraft:core/gui` with `ERROR: Invalid #version`. Independent evidence:
  the game log showed the RAW backend string (`OpenGL ES 3.2 V@0800.48 /
  Adreno (TM) 825`) instead of ONIGAMI's mapped version string, proving
  `glGetString` was also backend-resolved.
- Fix: `src/proc_table.cpp` interposer dispatch table; `eglGetProcAddress`
  now answers every wrapped GL/EGL entry with ONIGAMI's own address first and
  falls through to the backend only for unwrapped names. `glGetString` also
  probes opportunistically (SDL may drive system EGL directly).
- Translation half proven by 28 executed host assertions against the real
  `minecraft:core/gui` vertex/fragment shaders (Minecraft 1.21.4 client
  assets, Mojang AB): single leading `#version 310 es`, precision contract,
  interface preservation, legacy migration, ESSL passthrough, honest
  failures, CRLF tolerance. Runs in the native workflow (`host-test` job).
- Device evidence capture added: every compiled shader records original +
  translated source + backend verdict under `$ONIGAMI_DATA_DIR/shaders/` and
  in logcat, so the next failure arrives with its source attached.

## Known limitations (documented, not hidden)

- Desktop->ES GLSL translation stage 1 is a structured source migrator
  (version/profile header analysis, precision injection, legacy keyword and
  output-variable migration, comment-aware). Stages/constructs without an ES
  equivalent are rejected loudly rather than miscompiled. The pinned
  glslang + SPIRV-Cross compiler stack is built and linked; the remaining
  stage-1 edge cases route through it.
- Desktop compute shaders are rejected with a logged reason (staged).
- GL_VERSION is reported as a mapped, suffixed string
  ("4.6 (Compatibility Profile) ONIGAMI translated ES3.2 (backend: ...)" only
  when the backend verifies ES 3.2; otherwise a conservative 3.3 claim). The
  real backend string is always included in the same answer.
- No FPS, frame-generation or upscaling claims are made anywhere. No measured
  performance numbers exist yet, so none are published.

## Vault poisoning: why the crash log matches the pre-fix build bit-for-bit

The 2026-10-10 crash log shows the clouds failure WITHOUT any translator
fix effects (no extension directive at work). Two explanations existed:
(a) the tested APK predates the fix, or (b) the program vault served a
stale entry: vault keys covered source+stage only, so a new binary reused
the old broken translation and reproduced the identical error, including
identical transformed line numbers. Both are now closed:

- Vault keys are versioned by translator schema (`schema=2` in every key);
  any translator change automatically orphans old entries. No manual vault
  wipe is needed on update.
- `onigami: build=<id> translator_schema=2` is logged at process start, so
  every future device log identifies the exact binary under test.
- If the retest still shows the identical error WITH schema=2 in the log,
  explanation (a) is eliminated and the fault is in the new translation
  path itself - report the `shaders/<key>.*` capture files.

## Device round 2: 26.3 clouds failure (log dated 2026-10-10, POCO F7)

Dispatch fix confirmed on-device: the game log shows ONIGAMI's mapped
version string (`4.6 (Compatibility Profile) ONIGAMI translated ES3.2`) -
the GUI `Invalid #version` failure is gone.

New primary failure, `minecraft:core/clouds` vertex shader at transformed
line 38: `isamplerBuffer` with no `GL_EXT_texture_buffer` directive and no
sampler precision. The `_uniform_00_04` / `texelFetch` errors cascade from
the dead declaration. Fix (general construct handling, not a clouds
special-case): the translator detects samplerBuffer-family words on code
lines and emits `#extension GL_EXT_texture_buffer : require` plus highp
sampler precisions right after `#version`; `needsTexBufferExt` flows to the
frontend, which logs whether the backend actually advertises the extension
(honest capability reporting: if absent, the log says unsupported and the
backend fails loudly). Pinned by 9 new host assertions (37 total, executed).

State fixes from the same log: 34895 (TEXTURE_CUBE_MAP_SEAMLESS) and 34370
(PROGRAM_POINT_SIZE) init-time enables are now skipped as always-on no-ops
(no-op on desktop GL 3.2+ too; backend INVALID_ENUM removed); 32868
(GL_PROXY_TEXTURE_2D) is Mojang's own max-texture probe, which already falls
back to 16384 - forwarded unchanged, logged once for attribution. Deletes
now invalidate coalesced state (backend may recycle names - stale bindings
drew to the wrong framebuffer), DRAW/READ FBO tracking was split (they are
independent GL state), glDrawBuffers on framebuffer 0 with attachment enums
is logged loudly, and incomplete-FBO status is logged with its code.

Still needs the device: vanilla menu/world/clouds rendering, Sodium runs,
white-screen attribution (new capture + draw-buffer/FBO logs will identify
it), Iris packs. See the device-test matrix below - all NOT TESTED.

## How to classify a run

PASS — game reached a rendered world with the listed feature verified in the
log/screenshot. FAIL — executed and failed, evidence attached. NOT TESTED —
not executed. Nothing may be upgraded from NOT TESTED without evidence.
