# ONIGAMI renderer — root-cause analysis and capability matrix

Status of this document: **device-verification PENDING**. Everything marked
`[HOST-VERIFIED]` is proven from source or build artefacts in this repository.
No `[DEVICE]` result is claimed here.

The binary analysed here is the one committed at `255dc0c2`
(`OxideLauncher/src/main/jniLibs/arm64-v8a/libonigami.so`, 3,363,080 bytes),
built by workflow `38032236972` from source `f3e6bf60`.
`onigami_build_id()` returns `onigami-<commit> built <__DATE__ __TIME__>`, and
`onigami: build=<id> translator_schema=2` is logged at process start.

---

## 1. Actual rendering environment (established, not assumed)

| Fact | Source |
|---|---|
| Minecraft Java 26.3 runs through `org.lwjgl.sdl` + `libSDL3.so`, not GLFW | `LWJGL/3.4.1/src/main/java/org/lwjgl/sdl/SDLInit.java`, `LWJGL/patches/.../SdlCursorRegistry.java`, `OxideLauncher/src/main/jni/sdl_hook.c` (37,430 B) are in the tree; `lwjgl-sdl` and `lwjgl-sdl3` jars ship |
| The EGL context is created by SDL, not by ONIGAMI's wrappers | `sdl_hook.c` hooks `eglCreateContext`/`eglChooseConfig` with ES-compat retries; `OnigamiRenderer.getRendererEGL() == null` |
| All GL entry points are resolved through `eglGetProcAddress` | LWJGL 3.4.1 `GLCapabilities` + `GL.createCapabilities()` → `apiGetFunctionAddress(provider, ...)`; `GL.getFunctionProvider()` returns the shared library named by `org.lwjgl.opengl.libname` = `libonigami.so` |
| Therefore a GL name missing from ONIGAMI's own export set is answered by the **backend driver pointer**, bypassing ONIGAMI entirely | `src/proc_table.cpp` `own_proc()`, `src/egl_context.cpp` `eglGetProcAddress` |
| ONIGAMI's EGL path is pure passthrough; it does not own the display | `OxideLauncher/src/main/jni/egl_bridge.c` `pojavInitOpenGL()` → `RENDERER_GL4ES` → `ctxbridges/gl_bridge.c` `gl_init_context()`, which requests `EGL_CONTEXT_CLIENT_VERSION` = `LIBGL_ES` (3) from `OnigamiEnv` |

**Consequence, the central architectural finding.**

`OnigamiEnv.kt` sets `LIBGL_ES=3`, so the EGL context that SDL/`gl_bridge.c`
creates is an **OpenGL ES 3.2 context**. ONIGAMI cannot change that. Its whole
job is therefore to accept desktop-OpenGL-4.x calls and carry them out on an
ES 3.2 context, correctly. It currently reports `GL_VERSION` as
`4.6 (Compatibility Profile) ONIGAMI translated ES3.2`, which tells Minecraft
and every mod that a full desktop GL 4.6 context exists. It does not. The
frontend implements a subset (see §2).

The rule that follows, and which the frontend violated: **every desktop entry
point the game can call must be intercepted by ONIGAMI's own symbol.** Anything
not intercepted reaches the ES driver directly. For ES-native names that is
invisible. For desktop-only names it is a silent no-op or a crash class.

---

## 2. Capability matrix for the required path

Classification per the handover's categories: `W` = wrapped/implemented,
`D` = honest `dlsym` passthrough (ES name, forwarded), `X` = not implemented
and not in the proc table (falls through to the driver and is silently lost,
because the ES driver has no such entry point).

Vanilla 26.3, Sodium, Sodium Extra, Iris:

| GL operation | Where | Class |
|---|---|---|
| `glGenVertexArrays` / `glBindVertexArray` / `glDeleteVertexArrays` | `gl_frontend.cpp:522,250,675` | W |
| `glGenBuffers` / `glBindBuffer` / `glBufferData` / `glBufferSubData` | `:508,224,513,517` | W |
| `glMapBufferRange` / `glUnmapBuffer` | `:829,833` | D |
| `glVertexAttribPointer` / `glEnable/DisableVertexAttribArray` | `:602,594,598` | W |
| `glVertexAttribDivisor` | `:755` | D |
| `glDrawArrays` / `glDrawElements` | `:640,645` | W |
| `glDrawArraysInstanced` / `glDrawElementsInstanced` | `:650,655` | W |
| `glDrawElementsBaseVertex` | absent | **X** |
| `glDrawElementsInstancedBaseVertex` | absent | **X** |
| **`glMultiDrawElements`** | **`:802` — `gsym<>(...)`** | **X (silently dropped)** |
| **`glMultiDrawArrays`** | **`:797` — `gsym<>(...)`** | **X (silently dropped)** |
| `glMultiDrawElementsBaseVertex` | absent | **X** |
| `glMultiDrawElementsEXT` | absent | **X** |
| `glDrawBuffers` / `glReadBuffer` | `:885,899` | D |
| `glBindBufferBase` / `glBindBufferRange` | absent | **X** |
| `glGetUniformBlockIndex` / `glUniformBlockBinding` | absent | **X** |
| `glTexStorage2D` / `glTexStorage3D` | absent | **X** |
| `glClearBufferfv` / `glClearBufferfi` / `glClearBufferuiv` | absent | **X** |
| `glGetTexLevelParameteriv` / `glGetTexLevelParameterfv` | absent | **X** |
| `glFramebufferTexture` / `glFramebufferTextureLayer` | absent | **X** |
| `glGetQueryObjectuiv` | absent | **X** |
| `glBlendColor` | absent | **X** |
| `glObjectLabel` | absent | **X** |
| `glCopyBufferSubData` / `glCopyImageSubData` | absent | **X** |
| `glIsBuffer` / `glIsVertexArray` / `glIsTexture` / `glIsFramebuffer` | absent | **X** |
| `glDrawRangeElementsBaseVertex` | absent | **X** |
| `glGetTexImage` | absent | **X** |
| `glPushDebugGroup` / `glPopDebugGroup` | absent | **X** |
| `glCreateBuffers` / `glCreateVertexArrays` / `glCreateTextures` / `glCreateSamplers` / `glCreateFramebuffers` / `glCreateRenderbuffers` / `glCreateQueries` / `glCreateProgramPipelines` | absent | **X** |
| `glActiveTexture`, `glBindTexture`, `glGenTextures`, `glTexParameteri`, `glTexImage2D`, `glTexSubImage2D` | `:234,229,527,532,536,546` | W |
| `glGenSamplers` / `glBindSampler` / `glSamplerParameteri` | `:837,842,846` | D |
| `glGenFramebuffers`, `glBindFramebuffer`, `glGenRenderbuffers`, `glBindRenderbuffer`, `glRenderbufferStorage` | `:555,239,581,246,586` | W |
| `glFramebufferTexture2D` / `glFramebufferRenderbuffer` | `:573,577` | W |
| `glBlitFramebuffer` | `:590` | D |
| `glViewport` / `glScissor` | `:275,282` | W |
| `glClearColor` / `glClear` | `:611,615` | W |
| `glDepthFunc`/`Mask`, `glCullFace`, `glFrontFace`, `glPolygonOffset` | `:719,623,723,727,747` | D |
| `glBlendFunc`/`BlendFuncSeparate`/`BlendEquation` | `:619,707,711` | D |
| `glStencilFunc`/`Mask`/`Op` | `:731,735,739` | D |
| `glEnable`/`Disable`/`IsEnabled` | `:255,262,269` | W |
| `glGetIntegerv` / `glGetError` | `:194,190` | W |
| `glCreateShader`…`glLinkProgram`, `glGetUniformLocation` | `:287`…`:456` | W |
| `glUniformMatrix4fv` | `:481` | W |
| `glGenQueries`/`Begin`/`End` | `:854,859,863` | D |
| `glFenceSync` / `glClientWaitSync` / `glDeleteSync` | `:887,891,896` | D |

### 2.1 The proven silent-loss defect

`glDrawArrays`, `glDrawElements`, `glDrawElementsInstanced` and
`glDrawArraysInstanced` are all present in ES 3.2 core, so they forward
correctly. Multi-draw is different. ES 3.2 **core** has no
`glMultiDrawElements` / `glMultiDrawArrays`, and the Adreno 825 does not
advertise `GL_EXT_multi_draw` or `GL_ANGLE_multi_draw` (the vendored
GLES 3.2 header from the pinned upstream tree declares neither; the working
reference renderer in this repository, `copperoxide/upstream/MobileGlues-cpp/`,
resolves the EXT symbols and refuses to use them when the extension string is
absent, and otherwise expands the batch into plain draws).

`gl_frontend.cpp:797` and `:802` resolve the plain name through `gsym<>()`.
That returns nullptr on every ES 3.2 driver, and the wrapper then falls
through to `if (fn) fn(...)` — a no-op. **The terrain draw is dropped
silently.**

This is the single mechanism that explains the observed device behaviour:

- GUI renders (`glDrawElements` / `glDrawArrays` — present in ES core)
- sky and sun render (same)
- crosshair and hotbar render (same)
- terrain does not (multi-draw, absent)
- the menu panorama shows a clean black wedge (part of the panorama batch
  goes through the multi-draw path and is dropped, leaving the cleared black
  behind)

It also explains why no previous fix changed anything on the device. The
matrix-transpose fix at `2c07a4b1f` is correct and necessary, but it fixes a
code path that the terrain draw never reaches, because the draw itself is
discarded before it. The state-cache fixes at `137cd9bb1` / `d048cda` are also
correct, but state that is never used is not the fault.

`GL_FRAMEBUFFER_SRGB` at `f3e6bf60` is an honesty fix, and correct as far as it
goes, but it changes no behaviour: the sRGB enable was already a no-op.

---

## 3. What is now implemented (`gl_longtail.cpp`)

A single classification table (`onigami_longtail.h`) decides the treatment of
every desktop entry point an ES 3.2 context is missing. The four classes:

| Class | Treatment |
|---|---|
| `kForward` | ES 3.2 has this exact entry point. Direct forward. |
| `kEsName` | ES 3.2 has it under a different name. Forward to that symbol. |
| `kEmulate` | No ES equivalent, but an exact emulation exists. Emulate. |
| `kAbsent` | No ES equivalent and no honest emulation. Log once, do nothing. Never answer a null pointer — that is a crash, and a crashed launcher cannot report anything. |

The emulations are exact, not approximate:

- **Multi-draw** expands to the per-draw command. GL 4.6 section 10.5 defines
  `glMultiDrawElements` as a fixed set of `glDrawElements` commands, and each
  draw in a multi-draw is an independent primitive set, so the expansion is
  the same primitive set. This is the same technique the working reference
  renderer in this repository uses.
- **`glCreate*`** generates a real name from the classic ES entry point. That
  is the only way to obtain a valid object on a backend with no DSA path, and
  it does not pretend the DSA entry point exists: no DSA command is
  implemented, and `glCreateTextures` cannot bind its target.

`kAbsent` is used only where no honest emulation exists: `glGetTexImage`
(needs an FBO round-trip that ES 3.2 does not provide) and
`glPushDebugGroup` / `glPopDebugGroup` (no marker path). These emit one log
line and do nothing; the application gets no error it could mistake for its
own mistake.

The `gl_frontend.cpp` multi-draw entry points now delegate to this layer, and
the proc table registers the new exported names so `eglGetProcAddress` answers
with ONIGAMI's own symbol rather than falling through to the driver.

---

## 4. Why the current architecture is retained

**Decision: keep the desktop-OpenGL-to-OpenGL-ES translation architecture.**

Grounds from the sources inspected:

- The strategy is proven. The same repository ships a renderer built on the
  identical strategy (`copperoxide/upstream/MobileGlues-cpp`, LGPL-2.1) that
  renders Minecraft on Adreno. A GLES back end can carry desktop Minecraft on
  Adreno 825. The architecture is not the blocker: ONIGAMI's coverage is.
- IronizedZink's Mesa/Zink/Kopper/Vulkan stack is a valid alternative with
  better theoretical feature coverage, but it is a large prebuilt surface, a
  GL 4.3-grade feature set, and a large licensing and maintenance burden.
  There is no evidence in this repository that ONIGAMI needs it, and strong
  evidence that ONIGAMI's entry-point coverage is the actual gap.
- ONIGAMI covered roughly 140 GL/EGL names against roughly 490 in the reference
  implementation (measured by comparing `libonigami.so`'s export list against
  the reference's intercepted names). Closing that gap is a tractable amount of
  work; replacing the architecture is not, and would not have fixed this defect.

What must be true for the decision to hold, and is now enforced by the host
tests: every desktop entry point the game can reach must be either forwarded,
emulated exactly, or honestly refused with a diagnostic. Never silently passed
to a driver that has no such symbol.

---

## 5. Still unverified, and how to close it

Everything marked `[DEVICE]`:

1. Terrain renders on the POCO F7 after this change.
2. The menu panorama has no black wedge.
3. Sky and sun are correct.
4. Colour/brightness is correct. The overexposure is *not* assumed to be the
   same root cause as the missing terrain and the wedge; if terrain appearing
   does not fix it, the next diagnostic target is `glClearColor` /
   framebuffer format / sRGB, not an exposure multiplier.
5. Sodium, Sodium Extra, Iris and shaderpacks.
6. Frame time and FPS. No measurement exists, so none is published.

### How to test (the shortest decisive experiment)

```sh
adb install -r <new-arm64-apk>
adb logcat -c
adb logcat -s onigami:V SDL_Hook:V OxideLauncher:V
```

Then launch Minecraft 26.3 vanilla, reach a world, and look for:

- `onigami: draw glDrawElements ...` / `onigami: draw glMultiDrawElements ...`
  lines — the per-signature draw diagnostics added at `7ab969cbc`. If terrain
  draws now appear with matching VAO/program/viewport, the multi-draw
  emulation is firing.
- `onigami: glMultiDrawElementsBaseVertex: no GL_EXT base-vertex form ...` —
  if that line appears, the offsets are being dropped and the shader capture
  files in `$ONIGAMI_DATA_DIR/shaders/` are needed to decide whether a
  base-vertex form exists on this backend.
- Absence of the `texture-buffer directive emitted` failure means the clouds
  shader was reached again, which is the next target if terrain is fixed.

Take a screenshot of the title screen and one in-world, and compare both
against the two screenshots dated 2026-10-10-11-42 that are the current
baseline (black wedge in the menu panorama; no terrain, pale sky, white square
sun in-world).

---

## 6. Changes in this change-set

| File | Change |
|---|---|
| `src/onigami_longtail.h` | New. The classification table's contract, dependency-free so a host test can assert it with no GPU. |
| `src/gl_longtail.cpp` | New. The policy table, the exact multi-draw emulation, the honest `glCreate*`/`kAbsent` treatment, and the C entry points. |
| `src/gl_frontend.cpp` | Multi-draw entry points now delegate to `onigami_longtail`; added the ES 3.2 core forwards (`glBindBufferBase`, `glBlendColor`, `glClearBufferfv`, `glGetTexLevelParameteriv`, `glGetQueryObjectuiv`, `glFramebufferTexture`, `glIsBuffer`, `glObjectLabel`, `glCopyBufferSubData`, `glGetUniformBlockIndex`, `glUniformBlockBinding`, `glTexStorage2D`, …). |
| `src/proc_table.cpp` | Registered the new exported names so `eglGetProcAddress` returns ONIGAMI's symbol. |
| `CMakeLists.txt` | Added `src/gl_longtail.cpp` and the `src` include dir. |
| `tests/host/test_longtail.cpp` | New. 41 assertions: every `kForward` names a symbol that really exists in the vendored GLES 3.2 header; multi-draw is emulated; `glGetTexImage` is honestly absent. |
| `tests/host/test_multidraw.cpp` | New. 11 assertions: the expansion is safe for empty/zero/null batches, and the multi-draw symbols are exported. |
| `tests/host/stub_backend.cpp` | New. Host link stubs, the same pattern as `stub_config.cpp`. |
| `tests/host_include/` | The vendored GLES 3.2 header and the host EGL stub, so the kForward check runs for real instead of skipping. |
| `.github/workflows/onigami.yml` | Runs both new host tests in the `host-test` job. |
