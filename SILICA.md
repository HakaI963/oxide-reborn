# SILICA — Oxide-integrated OpenGL renderer (independent backend, under construction)

CORRECTION (2026-10-08): the earlier 1.13.0 phase-1 drove libcopperoxide.so from
Silica. That architecture (Minecraft -> Silica -> Copper Oxide -> MobileGlues) is
FORBIDDEN and has been removed. Silica loads ONLY libsilica.so built from
OxideLauncher/src/main/cpp/silica/. Selecting Silica before the backend renders
fails loudly in the launch log; it never falls back to another renderer.
Copper Oxide stays the default until libsilica.so actually runs Minecraft.

## Why 5974f49 fixes the 26.2-dev / 26.3 black screen (analysis, not a copy)

Upstream: MobileGlues-plugin 5974f49 pulls MobileGlues c07ae39..fcdf914 (+8bcf28a).

Root causes fixed:
1. NULL GL_RENDERER crash: with no context current on the calling thread,
   backend glGetString returns null; constructing std::string from null is
   strlen(nullptr) -> SIGSEGV inside the library's glGetString. A host whose
   context was made current through a different EGL than the layer drives hits
   this on its very first query (GL_RENDERER), dying with nothing in the log.
   Fix: keep probe-time copies (driver facts, not per-context facts) and answer
   from them with an explicit log line.
2. Silent EGL failures: hosts that retry attributes or continue without a
   context left no record of the refused create/bind/makeCurrent. Fix: LOG every
   refusal path and rearm the backend error (read for the log, re-queue so the
   app's own eglGetError still observes it).
3. Half-ANGLE load: GLES from ANGLE + EGL from the system driver (or vice versa)
   via independent per-library fallback. Contexts are created in one
   implementation while GL calls go to the other, whose first answer is null
   GL_RENDERER. Fix: both halves from the same place or neither (drop ANGLE to
   system/system on split + log).
4. Apple ARB aliases (8bcf28a): Mach-O has no symbol aliases; guard them out
   (no-op on Android, keeps Apple builds compiling).

Silica equivalent (own code, same contracts): silica:: note_probe/safe_string
(probe cache + logged fallback, never null), egl::rearm + log_create_failure +
log_make_current_failure, single-driver consistency check, gl::guarded entry
(skips + logs with no current context instead of dereferencing null).
Minecraft 26.3 bring-up (stage 10) proves this path without MobileGlues.

## Architecture (final)

Minecraft/LWJGL -> Oxide integration layer -> Silica native (libsilica.so) ->
Silica shader compiler/translator -> Android OpenGL ES / Adreno.

Copper Oxide / MobileGlues: REFERENCE ONLY (cpp/copperoxide + ARCH_MAP.md).
Never linked, never called, never fallen back to from Silica.

## Native backend stages (status)

1. Reference map: DONE (cpp/silica/ARCH_MAP.md).
2. Independent interfaces: DONE (include/silica/*.h + Kotlin SilicaPipeline).
3. EGL/context + GL entry: SKELETON DONE, full loader wiring next.
4. Capability detection: skeleton (probe cache), Adreno tuning with measurement.
5. Shader pipeline: glslang + SPIRV-Cross linked; full GLSL->SPIR-V->GLES path
   with vanilla bring-up.
6. Program cache: design + budget done, file store with bring-up.
7-8. Buffers/textures/framebuffers/uniforms/state/draw/sync/swap: state-dedup
   started; rest staged with Iris as the compatibility target (no Vulkan).
9. 5974f49 behavior: implemented in own code (above), proven at stage 10.
10-13. Vanilla -> Iris -> BSL profiling -> optimize measured bottlenecks only.
14. Only then: remove old backend (with the license-preserving checklist).

## Shader-performance hypothesis (NOT a claim)

BSL 45-50 FPS (old 26.2) vs 10-13 FPS (current Copper Oxide): the prime suspect
is the unsupported-launcher clamp forcing cache off (recompile every launch) +
full-res passes + redundant state, NOT a single compile flag. Silica attacks it
structurally: program binary cache, variant dedup, state dedup, target reuse,
resolution scaling. Every optimization ships only with before/after frame-time
data on-device. No FPS numbers are claimed in code, docs, or UI.

## Frame generation / upscaling (researched, gated)

Shipped only when measured-positive, capability-driven, optional. Phase state:
upscaling = render-scale + spatial upscale behind explicit opt-in (off by
default); frame generation = no backend, no toggle (a checkbox without a
backend would be fake). Adreno motion-estimation/synthesis is evaluated for
legality + net frame-time before any implementation.

## Verification before any removal (blocking)

- libsilica.so: no mobileglues/copperoxide/mg_context symbols (silica.yml
  enforces), APK contains libsilica.so and Silica loads exactly it (linker +
  loader-path + runtime checks), no MG_* env from Silica, no copperoxide
  imports in silica sources. LGPL notices stay until zero derived code remains.

## Rendering path (Minecraft 26.3 target, exact code path)

Minecraft/LWJGL -> `-Dorg.lwjgl.opengl.libname=libsilica.so` (dlsym core GL) +
SDL (`SDL_EGL_LIBRARY`/`POJAVEXEC_EGL=libsilica.so`) + pre-load dlopen ->
libsilica.so exports (own code only):
- `src/driver/loader.cpp`: opens system libEGL + libGLESv3/v2, single-driver
  repair (ANGLE-split halves fall back to system + log), backend resolve.
- `src/egl/exports.cpp`: full EGL 1.4/1.5 core surface; error rearm (backend
  error read for the log, re-queued for the app's eglGetError); every
  create/bind/makeCurrent refusal logged; probe of backend RENDERER/VERSION on
  first successful makeCurrent into the probe cache.
- `src/gl/exports.cpp`: null-safe glGetString/glGetStringi (probe fallback +
  log, never null deref); dedup wrappers (UseProgram, binds, caps, viewport,
  active unit); ~70-entry forwarder table (shader/program/FBO/texture/uniform/
  draw/pixel); eglGetProcAddress serves own exports first, backend for the long
  tail (temporary, documented below).
- `src/gl/state.cpp`: coalescing tables + hits/skips counters
  (silica_state_hits/skips); follows silica.json unless overridden.
- `src/config/config.cpp`: parses $SILICA_DATA_DIR/silica.json (own schema:
  profile/program_vault_mb/state_coalescing/diagnostics).
- `src/cache/program_cache.cpp`: vault budget enforcement (file store lands
  with the shader-pipeline stage).

## 5974f49 behaviors implemented (own code, same contracts)

1. Probe-cache fallback for GL_RENDERER/GL_VERSION (+glGetStringi empty answer)
   with an explicit log line instead of strlen(nullptr).
2. EGL refusal logging on every failing path + error rearm preserved for the
   app's eglGetError.
3. Single-driver consistency (ANGLE-half repair + log).
4. makeCurrent failure states the cause (GL without a current context).
5. Apple ARB-alias guard equivalent: n/a on Android (no alias attributes used).

## Currently implemented GL/EGL surface

EGL: GetError(rearm-aware)/GetDisplay/Initialize/Terminate/QueryString/
GetConfigs/ChooseConfig/GetConfigAttrib/Create+Destroy+Query (window/pbuffer/
pixmap/client-buffer)/SurfaceAttrib/BindTexImage/ReleaseTexImage/WaitNative/
BindAPI/QueryAPI/WaitClient/ReleaseThread/CreateContext/DestroyContext/
MakeCurrent/GetCurrent*/QueryContext/WaitGL/SwapBuffers/CopyBuffers/
SwapInterval/GetProcAddress(own-first).
GL: null-safe queries, GetError/Integerv, dedup state calls, full shader/
program/link/log/uniform resource set, FBO/RBO/VAO/VBO create-bind-data,
texture upload/compressed/mipmap, vertex attribs (+divisor), DrawArrays/
Elements, viewport/scissor/clear/blend/depth/cull/pixelstore/readpixels/
flush/finish.

## Still missing for Iris/BSL

- Full shader translation path (glslang->SPIR-V->Cross wired into compile;
  libraries linked, path staged) and program-binary file store.
- Wrapped coverage of the eglGetProcAddress long tail (blend equations,
  stencil ops, multisample renderbuffers, buffer mapping, fence sync,
  instanced/multi-draw, sampler objects, 3D textures, occlusion queries).
- Fence-sync presentation + frame pacing; any upscaling/frame-gen backend.

## Temporary compatibility hacks (explicit)

- eglGetProcAddress falls through to backend pointers for unwrapped names:
  renders correctly, bypasses state tracking (counted in diagnostics).
- Forward-on-null-context for non-query GL calls (drivers no-op safely);
  only queries get the probe treatment.
- No program-binary reuse yet: shader compile cost is unoptimized until the
  pipeline stage lands; no performance claims are made.

## Display-path contract (device fix, 26.3 "Could not get EGL display")

Reference findings (behavior only, no code copied):
- LTW interposes context create/destroy/current + GL only; display, init,
  configs, surfaces and swap stay on the host EGL, and its renderer declares
  no EGL override so SDL and the bridge resolve system EGL directly.
- MobileGlues opens backend libs RTLD_LOCAL and funnels every call through its
  own resolved table with logged refusals and a repaired ANGLE split.
- Oxide's bridge resolves POJAVEXEC_EGL for its own display handling; with no
  EGL override from Silica it uses system EGL end to end.

Silica implements exactly that contract in its own code:
- getRendererEGL() is null: no second display path can return NO_DISPLAY.
- libsilica.so exports context lifecycle + eglGetProcAddress + GL only;
  eglGetDisplay/eglInitialize/eglChooseConfig/surfaces/swap are NOT exported
  (build fails if they appear), so all callers transparently use the host.
- Backend open is RTLD_LAZY|RTLD_LOCAL with host-proc-first resolution and a
  logged ANGLE-split repair; every open/resolution logs path, flags, outcome.
- Probe cache fills from wrapped makeCurrent AND from any live backend answer
  observed by the null-safe glGetString wrapper.
