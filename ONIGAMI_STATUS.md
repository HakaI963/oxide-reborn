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

## How to classify a run

PASS — game reached a rendered world with the listed feature verified in the
log/screenshot. FAIL — executed and failed, evidence attached. NOT TESTED —
not executed. Nothing may be upgraded from NOT TESTED without evidence.
