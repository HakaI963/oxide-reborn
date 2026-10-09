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
