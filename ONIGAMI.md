# ONIGAMI — Oxide-integrated OpenGL renderer (independent backend)

ONIGAMI is Oxide's own desktop-GL-on-ES translation project, developed inside
this repository for Oxide Launcher. It loads ONLY `libonigami.so`. It never
loads, wraps, forwards to, or falls back to any other renderer's library. While
stages remain under construction, selecting ONIGAMI for an unproven path fails
loudly in the launch log instead of silently substituting another backend.

Silica (the previous attempt) has been removed from the active source tree and
the release package: its sources, Kotlin integration, workflow, docs and
prebuilt binaries are gone. Git history retains the record; the build does not.

## Architecture

```
Minecraft / LWJGL
  -> Oxide integration layer (process, surface, scheduling)
  -> ONIGAMI native (libonigami.so: EGL interposition + GL entry + shader pipeline)
  -> Android OpenGL ES / Adreno
```

Kotlin side: `OxideLauncher/.../game/renderer/onigami/` (identity, env, tuning,
pipeline seams, retryable probe cache) plus
`.../game/renderer/renderers/OnigamiRenderer.kt`.

## Rendering path

- LWJGL resolves core GL through `-Dorg.lwjgl.opengl.libname=libonigami.so`;
  the launcher pre-loads the backend with `dlopen` and logs the file.
- The launcher publishes no EGL override (stays null), so SDL and the Oxide
  bridge resolve system EGL; `libonigami.so` interposes the EGL entry points
  natively (display, init, configs, context, current, swap) and chains to the
  host driver underneath. Exactly one display path exists.
- GL calls pass the entry layer (state coalescing, null-safe queries with a
  retryable probe cache) and the shader pipeline before reaching the driver.

## The empty-version-string fix (root cause, not a version spoof)

1.13.0 crashed in `GL.createCapabilities` on `GL_VERSION = ""`: the old layer
wrapped context lifecycle only, sealed its one-shot probe on first failure, and
answered later queries from the empty cache. ONIGAMI instead:

- owns the full EGL display path, so the context LWJGL queries is one ONIGAMI
  created and made current;
- probes renderer/version ONLY on non-null, non-empty answers and retries on
  every subsequent `eglMakeCurrent` until real strings arrive — failure is never
  sealed;
- answers `glGetString` with NULL when no context is current on the calling
  thread (honest, diagnosable), the live backend string when one is, and a
  desktop-style version mapping that always carries the `ONIGAMI translated`
  suffix plus the backend string (4.6 claim only with verified ES 3.2,
  otherwise a conservative 3.3 claim). No fabricated capabilities, no fake
  success, no error-flag consumption.

## Shader pipeline

Desktop GLSL → version/profile detect → glslang parse+link → SPIR-V →
SPIRV-Cross to ESSL 3.10 (highp fragment precision) → driver compile. ESSL
passes through; other stages forward untranslated and say so. Translation is
cached in memory by deterministic key; programs consult the persistent vault
before linking and store fresh binaries after, under a budget with eviction.

## Verification (blocking)

- `libonigami.so` exports the required EGL/GL/version symbols per ABI and
  contains no mobileglues/copperoxide/silica symbols (CI enforces).
- APK contains `libonigami.so` and no `libsilica.so`; ONIGAMI loads exactly it.
- No foreign env keys emitted; `onigami.json` validates against the schema.
- Vanilla 26.2 → 26.3 → Sodium → Sodium Extra → Iris+packs, each with real
  in-world rendering evidence. See docs/ONIGAMI_RESEARCH.md for the plan.

## Honest status

- Vanilla path first; Iris support is staged, not present at landing.
- Upscaling / frame synthesis: researched, gated, no toggles until a measured
  backend exists.
- No performance numbers are claimed anywhere in code, docs, or UI.


## ONIGAMI (Vulkan) positioning (v1.15.0)

Display name is ONIGAMI (Vulkan) everywhere in the launcher; ids, flavor,
native library and data dir are unchanged so existing selections keep working.
ONIGAMI requires the Vulkan graphics API:

- Global pick (Settings renderer drawer or legacy renderer settings): a
  confirm dialog (Cancel / Switch to Vulkan) switches the renderer and the
  global graphics API together. Cancelling keeps the previous renderer.
- Instance pick (new or legacy instance settings): the instance renderer is
  selected and that instance's graphics API is pinned to Vulkan. Globals and
other instances are untouched.
- Launch log records the renderer and the effective graphics API, and warns
  when ONIGAMI runs without Vulkan.

## Frame pacing and the ~20fps investigation

- `swap_interval` (onigami.json, 0..1, default 1, setting
  `onigamiSwapInterval`): floor applied in `eglSwapInterval`, so the default
  keeps vsync pacing even when the game asks for uncapped; 0 honors the game
  request. Restart required.
- With diagnostics on, `eglSwapBuffers` logs `frame pacing swaps=.. avg_ms=..
  slow_frames(>50ms)=..` every 600 swaps. Capture `adb logcat -s onigami:V`
  with render distance/resolution notes to root-cause low frame rates; no
  bottleneck is claimed without that data.

## Shader pipeline and Vitrail

- The stage-1 translator is unchanged by this release: desktop GLSL migrates
  to ESSL 310, ESSL sources pass through, failures stay loud, nothing is
  blocked. No Onigami change filters mod shaders, so Vitrail (Vulkan shader
  support, Iris-like) compatibility is preserved by design; on-device
  verification with a Vitrail pack is still required (see test plan).

## Upscaling and frame generation (investigated, not implemented)

- No upscaling or frame-generation controls ship: no measured backend exists,
  and a control without one would be fake.
- AMD FSR-style spatial upscaling is the feasible direction (the same pattern
  already ships in Copper Oxide / MobileGlues, LGPL-2.1); porting it needs a
  native resolve + blit stage with device measurements first.
- Temporal upscaling needs motion vectors the engine does not expose, and
  frame generation needs synthetic-frame pacing: both stay out until the
  interfaces and latency budget are proven on-device.


## v1.16.0: render scale, coalescing toggle, shader-path costs

### Render scale + bilinear present upscale (opt-in, default off)

New `onigami.json` keys `render_scale` (0.50-1.00, default 1.0) and
`upscale_mode` (0 off, 1 bilinear). When enabled with the default
framebuffer bound, `glViewport`/`glScissor` forward scaled rects while
`glGetIntegerv(VIEWPORT/SCISSOR_BOX)` keeps answering virtual (game-logic)
values; `eglSwapBuffers` stretches the drawn region to the full surface with
`glBlitFramebuffer(LINEAR)`, saving and restoring bindings and scissor state.
App-framebuffer rendering (shadow maps, world chains) is never resized.
Limitations, stated plainly: GUI drawn to the default framebuffer scales too
(no 3D/GUI split markers exist yet); benefit is workload-dependent — it saves
fragment work only where the game shades the default framebuffer, not where
the world is pre-composited at full resolution. Measure with the pacing log.

### Shader-path cost reductions (semantics unchanged)

- `StateCache` coalescing is now wired to its toggle (default off, as in
  v1.15.0): redundant UseProgram/binds/VAO/FBO/caps/viewport calls are
  skipped only when enabled. `ELEMENT_ARRAY_BUFFER` still always forwards
  (VAO-scoped); textures stay per-unit; DRAW/READ FBOs stay split.
- Hot `gsym<>` lookups (uniforms, blend, depth, stencil) are memoized: one
  linker walk per name instead of per call.
- `GL_EXT_sRGB` presence is cached instead of scanned per enable call.
- `glCompileShader` fetches the info log and writes captures only on failure;
  the one-line compile verdict stays.
- Draw diagnostics use an integer signature hash (no per-draw snprintf/alloc)
  with the same 3-logs-per-signature throttle.
- Program vault keys now include backend renderer/version strings and schema 3
  (old entries orphan, never poison); eviction sweeps at most every 16 stores.
- `eglSwapBuffers` frame-pacing stats (v1.15.0) remain the FPS instrument.

### Deliberately not shipped

- Frame generation: infeasible in this layer — no retained frames, no motion
  vectors, no depth pixels, no present ownership (SDL/host + SurfaceFlinger
  own vsync). No toggle, no counter changes.
- Temporal upscaling (FSR2-style): needs jittered projection, motion vectors,
  depth and history the engine does not expose. Not shipped.
- Universal shadow-map/quality knobs: the launcher has no uniform API into
  Sodium/Iris/Vitrail packs (only `shaderpacks/` file management). Global
  `options.txt` render distance etc. already exist and are untouched.
