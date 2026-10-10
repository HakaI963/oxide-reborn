# ONIGAMI — delivery report

## 1. Repository and build state

| Item | Value |
|---|---|
| Previous HEAD of record | `255dc0c2` ("round-4 root cause (matrix upload) + diagnostics + sRGB honesty") |
| **HEAD now** | **`2662800`** (CI-committed `libonigami.so` from source `8732d6c`) |
| Source of the fix | commits `711472d`, `eb680f8`, `8732d6c` |
| Native build | workflow run `38055378883` from `8732d6c` — 4 ABIs **success**, host tests **success** |
| Release APKs | workflow run `38055863092` from `2662800` — all 5 variants **success** |
| ARM64 APK artifact | `release-apk-arm64` → `OxideLauncher-1.14.0-arm64-v8a.apk`, 163,196,564 bytes |
| Inside that APK | `lib/arm64-v8a/libonigami.so`, 1,060,464 bytes, exports all 7 multi-draw symbols plus the new long-tail set (verified by extracting the APK and reading its export table) |
| Renderer gate, all 5 ABIs | `libonigami_so_entries >= 1`, `libsilica_so_entries = 0` — verified from the workflow log |
| No uncommitted changes at HEAD | yes, the tree is clean at `2662800` |

### How to confirm the executing binary on the device

`onigami_build_id()` returns `onigami-<commit> built <__DATE__ __TIME__>` and
`bridge.cpp` logs `onigami: build=%s translator_schema=%d` at process start.
For this build the date compiled in is `Oct 10 2026`. On device:

```sh
adb logcat -s onigami:V | head -20
```

Expect:

```
onigami: process_start dir=/data/.../files/onigami
onigami: build=onigami-8732d6c built Oct 10 2026 <time> translator_schema=2
onigami: surface <w>x<h>
onigami: probe renderer=... version=OpenGL ES 3.2 ...
```

If `build=` does not say `8732d6c` (or a later commit), the installed APK is
not this build. Before any device conclusion, confirm this string.

## 2. Root cause

The EGL context is **OpenGL ES 3.2**: `OnigamiEnv.kt` sets `LIBGL_ES=3`, and
SDL creates the context (`libSDL3.so`, `lwjgl-sdl`); `OnigamiRenderer`'s
`getRendererEGL()` is `null`, so ONIGAMI does not own the display. Any desktop
entry point ONIGAMI does not export is answered by the backend driver through
`eglGetProcAddress`.

`glMultiDrawElements` and `glMultiDrawArrays` are not in ES 3.2 core, and the
Adreno 825 does not advertise `GL_EXT_multi_draw` or `GL_ANGLE_multi_draw`
(verified against the vendored `include/GLES3/gl32.h` in this repository, and
cross-checked against the working reference renderer in
`copperoxide/upstream/MobileGlues-cpp`, which resolves the EXT symbols and
refuses to use them when the extension string is absent).

`gl_frontend.cpp:797` and `:802` resolved those names with `gsym<>()`.
`dlsym` returns nullptr, and the wrapper then falls through to `if (fn) fn(...)`:
**a no-op. Minecraft's terrain draw was dropped in silence, every frame.**
That is why GUI, sky, sun, crosshair and hotbar — all `glDrawElements` /
`glDrawArrays`, which ES 3.2 has in core — rendered, and the terrain did not.

It also explains the previous rounds. The transpose fix is correct but fixes a
path the terrain draw never reaches. The state-cache fixes are correct but state
that is never used is not the fault. `GL_FRAMEBUFFER_SRGB` was already a no-op.

Full detail, including the capability matrix and the architectural findings:
`OxideLauncher/src/main/cpp/onigami/ROOT_CAUSE.md`.

## 3. Implemented change

One mechanism, no game-, shader- or texture-specific special cases.

`src/onigami_longtail.h` — a dependency-free classification contract so a host
test can assert it with no EGL/GLES/GPU present.

`src/gl_longtail.cpp` — the table plus the emulations:

- `kForward` — ES 3.2 has the exact entry point. Direct forward.
- `kEsName` — ES 3.2 has it under a different name. Forward to that symbol.
- `kEmulate` — no ES equivalent, but an exact emulation exists. Multi-draw
  expands to the per-draw command: GL 4.6 §10.5 defines `glMultiDrawElements` as
  a fixed set of `glDrawElements` commands, and each draw in a multi-draw is an
  independent primitive set, so the expansion is the same primitive set.
- `kAbsent` — no ES equivalent and no honest emulation. Log once, do nothing.
  Never answer a null pointer (that is a crash, and a crashed launcher cannot
  report anything), never claim support that does not exist.

`glCreate*` generates a real name from the classic ES entry point without
pretending a DSA path exists. `glGetTexImage`, `glPushDebugGroup` and
`glPopDebugGroup` are the honest `kAbsent` cases.

`src/gl_frontend.cpp` — multi-draw delegates to the long-tail table, plus the ES
3.2 core forwards that were previously unwrapped (`glBindBufferBase`,
`glBlendColor`, `glClearBufferfv/fi/uiv`, `glGetTexLevelParameteriv/fv`,
`glGetQueryObjectuiv`, `glFramebufferTexture(Layer)`, `glIs*`, `glObjectLabel`,
`glCopyBufferSubData`, `glGetUniformBlockIndex`, `glUniformBlockBinding`,
`glTexStorage2D`).

`src/proc_table.cpp` — the new names are registered so `eglGetProcAddress`
answers with ONIGAMI's symbol rather than the driver's.

## 4. Architecture decision

**Retained: desktop-OpenGL-to-OpenGL-ES translation.**

Grounds found in the sources inspected:

- The strategy is proven. This repository ships a renderer built on the
  identical strategy (`copperoxide/upstream/MobileGlues-cpp`, LGPL-2.1) that
  renders Minecraft on Adreno. A GLES backend can carry desktop Minecraft on
  Adreno 825.
- IronizedZink's Mesa/Zink/Kopper/Vulkan stack is a valid alternative with
  better theoretical coverage, but a large prebuilt surface, a GL 4.3-grade
  feature set, and a large licensing and maintenance burden. Nothing in this
  repository shows ONIGAMI needs it; entry-point coverage is the actual gap.
- ONIGAMI covered ~140 GL/EGL names against ~490 in the reference
  implementation. Closing that gap is tractable work; replacing the
  architecture is not, and would not have fixed this defect.

The one precondition now enforced by tests: every desktop entry point the game
can reach must be forwarded, emulated exactly, or honestly refused with a
diagnostic — never silently passed to a driver that has no such symbol.

## 5. Compatibility report

### Implemented (in this build)

| Item | Status |
|---|---|
| Desktop GL 1.1–3.0 core geometry, buffers, VAOs, textures, blending, depth, stencil | implemented, pre-existing |
| Desktop GL 3.1–3.3 (uniform blocks, layered attachments, typed clears, base-vertex, instanced) | implemented in this change |
| Multi-draw (all six spellings) | implemented in this change, exact expansion |
| `glCreate*` | implemented in this change, real names, DSA not claimed |
| Shader translation (GLSL 110–150 → ESSL 310) with vault cache | implemented, pre-existing |
| `texelFetch`/`isamplerBuffer` construct handling | implemented, pre-existing |

### Automated-test verified

| Test | Result |
|---|---|
| Translator suite (37 assertions) | pass |
| State-cache suite (30 assertions) | pass |
| Long-tail policy suite (**41 assertions**, new) | pass — asserts every `kForward` names a symbol that exists in `GLES3/gl32.h`; a fabricated forward fails the build |
| Multi-draw contract suite (**11 assertions**, new) | pass — expansion safety on empty/null batches, plus real export check against the actual frontend |
| Native build, 4 ABIs | pass |
| Native symbol gate, 4 ABIs | pass |
| Full CI: unit tests, lint, debug APK, R8 | pass |
| Release R8 + APK integrity + renderer gate, 5 ABIs | pass (libonigami present, libsilica absent) |

The two new host tests were validated as regression guards: deliberately
classifying `glGetTexImage` as `kForward` makes the policy test **fail**, and
removing the frontend definition makes the export test **fail**.

### Device-test verified

**None.** No stage of the validation plan has been executed on the POCO F7
with this build. Nothing in this report may be read as device verification.

### Unsupported (honest)

| Item | Status |
|---|---|
| `glGetTexImage` | `kAbsent`, logged once, no error |
| `glPushDebugGroup` / `glPopDebugGroup` | `kAbsent`, logged once, no error |
| `glGetQueryObjecti64v` | `kAbsent`, ES 3.2 has no 64-bit query result |
| DSA commands other than `glCreate*` | not implemented, not claimed |
| `glGetTexImage`-backed readbacks | not implemented |
| Desktop compute shaders | **Rejected loudly**, not implemented |
| sRGB encoding emulation for `GL_FRAMEBUFFER_SRGB` | state recorded and logged, **not** emulated — documented limitation, carried forward honestly |
| `glMultiDrawElementsBaseVertex` without a base-vertex form | geometry drawn, offsets dropped, anomaly logged (not silent) |

### Not yet tested

Terrain, panorama wedge, sky/sun, colour/brightness, Sodium, Sodium Extra,
Iris, shaderpacks, context lifecycle, extended gameplay, FPS.

## 6. Device test instructions

```sh
# download the arm64 APK from run 38055863092, then
adb install -r OxideLauncher-1.14.0-arm64-v8a.apk
adb logcat -c
adb logcat -s onigami:V SDL_Hook:V OxideLauncher:V > device.log
```

Confirm identity first:

```
onigami: build=onigami-8732d6c built Oct 10 2026 ... translator_schema=2
```

If the string differs, the installed APK is not this build; stop there.

Then:

**Stage 1 – native init.** Confirm `onigami: probe renderer=... version=OpenGL ES 3.2 ...`
and that the game starts without a GL error.

**Stage 2 – GUI.** Title screen. Expected: nothing changes from the previous
baseline (logo, buttons, text, icons, panorama **without the black wedge**).
This is the decisive test for the primary hypothesis: the panorama batch and
the terrain batch both go through multi-draw, so if the fix works, the wedge
disappears *and* terrain appears.

**Stage 3 – world.** Enter a world. Expected: terrain renders; sky, sun,
crosshair and hotbar as before. Take a screenshot.

**Stage 4 – colour.** Compare against the baseline screenshot
(`Screenshot_2026-10-10-11-42-39-076`: pale sky, white square sun, no
terrain). If terrain appears but the sky is still washed out, **that is a
separate root cause, not an exposure number to be tuned** — log
`onigami: draw ...`, the framebuffer format and `glClearColor` and file it.

**Stage 5 – mods.** Sodium, then Sodium Extra, then Iris with a shaderpack. The
clouds shader's `isamplerBuffer` construct is *not* implemented in this build
and will still fail to compile; that is documented, not hidden.

**Stage 6 – stability.** Resource reload, world transition, GUI transition,
rotation (which recreates the surface), then ten minutes of play.

### What to send back

- The `device.log` from above.
- A screenshot of the title screen.
- A screenshot in-world.
- `$ONIGAMI_DATA_DIR/onigami-egl.log` (under the launcher's private files dir,
  path shown by the `process_start dir=` log line).
- `$ONIGAMI_DATA_DIR/shaders/*.orig.glsl`, `.essl.glsl`, `.compile.log` if any
  shader fails.

The lines that decide this build's outcome are the draw diagnostics:

```
onigami: draw glDrawElements        mode=0x4 count=... vao=... prog=...
onigami: draw glMultiDrawElements   mode=0x4 count=... vao=... prog=...
```

If multi-draw lines appear with a sane VAO/program/viewport, the expansion is
firing. If a line reads
`onigami: glMultiDrawElementsBaseVertex: no GL_EXT base-vertex form ...`, the
offsets are being dropped on this backend and the shader captures are needed.
