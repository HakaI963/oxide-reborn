# ONIGAMI research — Oxide-owned GL-on-ES renderer

Status: design-stage research document. Native implementation lands alongside
this doc; every device-facing claim below is labeled. No FPS numbers are
claimed anywhere — none have been measured.

Sources checked (Oct 2026): Khronos OpenGL / OpenGL ES / EGL registries,
LWJGL docs and issue tracker, Android NDK docs, shaders.properties (Iris
reference), SPIRV-Cross repository, plus public device reporting for the
target SoC family. Each claim below cites the primary source class, not this
prompt.

## A. How Minecraft Java initializes OpenGL through LWJGL

- Minecraft's Blaze3D (`GlDevice.<init>`) calls LWJGL `GL.createCapabilities()`,
  which reads the current context's `GL_VERSION` via `glGetString` and parses it
  with `APIUtil.apiParseVersion`. An empty string throws
  `IllegalArgumentException: Malformed API version string []` — the exact 1.13.0
  crash. (LWJGL source + issue tracker: empty/missing version is fatal; a NULL
  context is a different, earlier failure.)
- LWJGL resolves GL entry points through the library named by
  `-Dorg.lwjgl.opengl.libname`. Pointing that name at `libonigami.so` puts
  ONIGAMI between the game and the driver for every core GL call. The GLES
  binding (`org.lwjgl.opengles`) is a separate API surface and is NOT what the
  desktop game calls.
- Consequence: the translation layer MUST answer `glGetString(GL_VERSION)`
  with a parseable desktop-style version string whenever a context is current,
  and MUST return NULL (never `"\""`) when no context is current so the failure
  is diagnosable instead of a malformed-string crash.

## B. SDL / EGL / JNI integration in Android launchers

- SDL owns window/surface creation and asks EGL for display, configs and
  surfaces. Oxide's JNI bridge (`egl_bridge.c`, `ctxbridges/`) resolves
  `POJAVEXEC_EGL` for its own display handling; with no launcher-level EGL
  override from the renderer, SDL + bridge resolve system EGL end to end.
- The renderer is pre-loaded with `dlopen` before game start (`GameLauncher.dlopenEngine`)
  and the resolved file is logged. A missing library fails loudly; substituting
  another backend silently is forbidden.
- EGL error state is per-thread and single-read: any layer that reads
  `eglGetError` merely to log it consumes the application's own read and forges
  `EGL_SUCCESS`. Diagnostics therefore travel in log strings with decoded
  attributes, never in a consumed flag. (EGL spec, § error handling.)
- Context requests from desktop-minded clients carry version/profile/flag
  attributes plus `EGL_CONTEXT_CLIENT_VERSION`; a GLES-only backend refuses
  desktop profile masks, so the layer must translate the request to an ES
  request and bind `EGL_OPENGL_ES_API` before creation.
- Backend entry resolution order: host `eglGetProcAddress` first (the driver's
  own dispatch), `dlsym` on opened handles second. Both halves from one driver;
  an ANGLE/system split answers null strings.

## C. What desktop GL Minecraft 26.2 / 26.3, Sodium and Iris require

- Vanilla 26.x needs a desktop-compatibility context plus the full
  shader/program/framebuffer/texture/buffer/uniform/draw call surface the game
  actually invokes. Public requirement baselines cite OpenGL 3.2-class hardware
  for the legacy path, with 26.2+ adding an experimental Vulkan renderer alongside
  (Hypixel/Minecraft system-spec reporting, Oct 2026).
- Sodium does not change the required GL surface; it raises draw-call
  throughput and state-change frequency, so redundant-state elimination
  (bind/cap/viewport coalescing) is structural, not optional.
- Iris drives multi-pass pipelines, shadow maps and composite targets: correct
  program/framebuffer/texture lifetime across surface loss, multiple render
  targets, and honest extension/capability answers (a faked extension string
  turns a clean feature-disable into a native crash).
- Iris advanced stages (shaders.properties reference): compute shaders need
  OpenGL 4.3 / `GL_ARB_compute_shader` + `#version 430`; SSBOs need the SSBO
  feature path (std430, up to 128MB guaranteed); image load/store and shader
  storage buffers are 4.x-class features that must be probed, never assumed.

## D. Desktop-to-ES translation strategy

- Per-stage `#version`/profile detection; ESSL sources pass through untouched.
- Desktop GLSL: parse + link with glslang → SPIR-V → SPIRV-Cross to ESSL 3.10
  (explicit `highp` fragment precision matching the backend contract) → driver
  compile. Failures are never submitted; the info log carries the real error.
- No regex-only translation: a string rewriter cannot cover the desktop grammar
  and miscompiles packs silently. The compiler-stack route fails loudly instead.
- Results cached in memory under a deterministic key (source hash + stage +
  target); programs consult the persistent vault before linking and store fresh
  outputs after, under a byte budget with oldest-first eviction.
- Non-vertex/fragment stages forward untranslated in phase 1 with an explicit
  log line; claiming translation without a path would be fake.

## E. Iris shaderpack requirements (staged, gated)

- Shadow maps, composite framebuffers, multi-target chains: completeness
  handling + target reuse across frames.
- Sampler objects, buffer mapping, instanced/multi-draw, fence sync: export
  long tail, each landing with its own test.
- Upscaling (below-native render + spatial upscale) and frame synthesis:
  researched, capability-driven, optional, off by default; shipped only with a
  measured net frame-time win. No toggle exists before its backend.

## F. Target GPU/driver capabilities (POCO F7, Adreno 825, Android 16/API 36)

- Public device reporting (Snapdragon 8s Gen 4 family, 2025): OpenGL ES 3.2,
  Vulkan 1.3-capable, OpenCL 3.0-class. There is NO desktop OpenGL on the
  device — every desktop context/version answer is a translation-layer claim
  and must carry the translation suffix and a capability matrix.
- Backend entries resolve via plain `dlsym` on the opened handle first, host
  dispatch second. Single driver for EGL + GLES halves.
- UNTESTED on-device: exact driver version strings, extension lists,
  config/match behavior. The matrix below is a detection plan, not results.

## G. Translation approaches and tooling considered

- glslang (GLSL → SPIR-V) + SPIRV-Cross (SPIR-V → ESSL): Khronos projects,
  permissive licenses (see docs/ONIGAMI_THIRD_PARTY.md), pinned by commit hash.
  Pins move only deliberately, never by floating branch.
- Rejected for phase 1: hand-written GLSL rewriter (coverage), Vulkan leg
  (Iris packs stay GL-based; doubles untested surface).

## Architecture

```
Minecraft / LWJGL
  -> -Dorg.lwjgl.opengl.libname=libonigami.so (+ pre-load dlopen, loud on miss)
  -> libonigami.so: EGL interposition (display path, chained to host driver)
  -> libonigami.so: GL entry layer (state coalescing, null-safe queries)
  -> shader pipeline (glslang -> SPIR-V -> SPIRV-Cross -> ESSL 3.10)
  -> program vault (persistent cache, budget-evicted)
  -> Android OpenGL ES / Adreno
```

Kotlin side (`.../renderer/onigami/`): identity, env, tuning, pipeline seams,
retryable probe cache. Launcher owns process/surface/scheduling; ONIGAMI owns
translation policy. Oxide-tree integrated; no standalone renderer distribution.

## Dependency choices

- glslang + SPIRV-Cross by pinned commit (compiler stack only; no EGL/GL
  runtime behavior). Everything else is Oxide-owned code.
- No Copper Oxide / MobileGlues / GL4ES / Zink / Silica sources, binaries or
  symbols at any layer. CI fails the build if foreign renderer symbols appear.

## Capability matrix (detection plan — NOT results)

| Capability | Source of truth | Behaviour if absent |
|---|---|---|
| ES 3.2 context | driver EGL probe | loud launch-log failure, no fallback |
| Desktop 4.6 version claim | verified ES 3.2 + translation suffix | conservative 3.3 translation claim |
| Program binaries | driver + vault | recompile path, logged |
| Geometry/tessellation/compute | translation stage | forward untranslated + log (phase 1) |
| Sampler/instanced/sync long tail | export table | staged landings with tests |
| Upscaling / frame synthesis | measured backend | no toggle until backend exists |

## Limitations (honest)

- Iris pack support is staged after vanilla bring-up, not present at landing.
- No device measurements of any kind yet; no FPS claims in code, docs, or UI.
- Compute/SSBO/image paths need 4.3-class backend proof on-device.

## Device test plan (ordered, ordered by the mission)

1. Vanilla 26.2 → 2. vanilla 26.3 → 3. 26.2+Sodium → 4. 26.2+Sodium Extra →
   5. 26.2+Iris+pack → 6. 26.3+Iris+pack. Each: world render, movement, camera,
   textures, lighting, GUI, framebuffers, shader compile/link/execute, lifecycle
   recovery. Multiple packs with different feature sets. PASS/FAIL/NOT TESTED
   recorded per run; menu-only is not a pass.
