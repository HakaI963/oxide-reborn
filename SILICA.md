# SILICA — Oxide-integrated OpenGL renderer (phase 1, 1.13.0)

Silica is NOT Copper Oxide renamed. Copper Oxide / MobileGlues source was used
ONLY as a reference for how Minecraft LWJGL/OpenGL calls, EGL, shaders,
framebuffers, textures, programs, uniforms and driver interaction work.

## Upstream fix ported FIRST (required behavior, not optional)

Source: MobileGlues-plugin 5974f49 -> MobileGlues c07ae39..fcdf914 + 8bcf28a.

Dev-build black screen on Minecraft 26.2 (26.3 depends on the same dev fixes):
a host whose context was made current through another EGL made GL calls with
nothing current; the backend answered null GL_RENDERER and the library crashed
in strlen(nullptr), compounded by silent EGL failures and half-ANGLE loads
(GLES from ANGLE + EGL from system driver or vice versa).

Ported into the native tree Silica drives (OxideLauncher/src/main/cpp/copperoxide/upstream):
- gl/getter.cpp: probe-time g_probe_renderer/g_probe_version + backend_string()
  fallback with LOG_E instead of crash; set_es_version caches probe copies and
  logs null GL_VERSION; getGLESName uses the same fallback.
- egl/egl.cpp: rearmBackendError() (read backend error for the log line, put it
  back for the app's eglGetError) + LOG_E on every create/bind/makeCurrent
  failure path (ES refused, desktop rejected pre-backend, BindAPI failed,
  desktop backend refused, makeCurrent failed with "no current context" cause).
- gles/loader.cpp: ANGLE half-load guard (both halves from the same place or
  neither; on split, drop ANGLE and use system driver for both + log).
- gl/framebuffer.cpp: Apple ARB-alias guard (no-op on Android).

Kotlin mirror: SilicaProbe caches the per-process EGL probe strings and answers
from them with accounting when live returns null (never crashes on null).

## Oxide <-> Silica pipeline (new architecture)

Launcher owns: process/game lifecycle, EGL probe, surface/swapchain geometry
(GameDisplayLayout), launch env + dlopen, config authoring, settings UI.
Silica owns: renderer identity/env, probe-cache policy, tuning presets,
per-stage contracts (SilicaPipeline.kt: lifecycle, EGL context, swapchain,
scheduler, resources, memory/cache, resolution, pacing, framegen, controls).

Silica stays OpenGL/OpenGL-ES so Iris shaderpacks keep working. No Vulkan-only
path. Compute is not assumed faster.

## Shader regression analysis (10-13 FPS vs 45-50 FPS with BSL, hypothesis)

Upstream "unsupported launcher" clamp forced angle off + compute ext off +
shader cache off (maxGlslCacheSize=0) for any launcher without a recognized
flag/dir. Oxide hit that clamp without the private-dir switch, so every shader
recompiled every launch with no binary cache, plus full-res fullscreen passes
and redundant state changes. Silica ALWAYS sets its data dir (custom dir alone
bypasses the clamp) and enables the GLSL cache by default — measured
improvement expected, but NO FPS CLAIMED without on-device measurement.
Plan: frame-time variance + sustained FPS with BSL @1080p on Adreno, cache
hit rate, compile count, bandwidth (fullscreen passes, texture/format).

Rendering-path redesign targets (measured, in order): program binary cache,
permutation dedup, uniform/descriptor caching, redundant state elimination,
framebuffer/render-target reuse, render-pass merging, resolution scaling.
Libraries only if they give measured architectural advantage (none added in
phase 1).

## Upscaling / frame generation (phase 1 honesty)

- Upscaling: render-scale reduction + FSR spatial upscale (real FSR1 backend
  key). Off by default; on PERFORMANCE or when user opts in. Must reduce total
  cost or stay off.
- Frame generation: NOT exposed in phase 1 UI. No Adreno/GL backend here offers
  measured-positive motion-estimation synthesis; a toggle without a backend
  would be fake. Investigation (motion vectors, depth reprojection, async
  synthesis, pacing) continues; it ships only when measured-positive,
  capability-driven and optional.

## Runtime controls

Settings -> Renderer -> Silica (new section, phase 1): performance mode,
shader cache MB, spatial upscale. All restart-required (config read at context
init) and labeled as such. In-game panel appears ONLY when Silica is selected;
phase 1 shows restart-required state + cache stats; live-apply arrives with
stages that prove safe.

## Cleanup contract (NOT done in 1.13.0)

Copper Oxide impl, MobileGlues source, copperoxide.yml, settings, jniLibs
binaries, branding and LGPL notices STAY until Silica native is actually
independent. Removing a license notice while derived code remains is forbidden.
Pre-APK checklist (for that future removal, not now): no mobileglues/copperoxide
source, no old .so, no old loader path, Silica packaged/loaded on all ABIs.
