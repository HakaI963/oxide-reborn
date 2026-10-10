# ONIGAMI architecture: reference study, audit, decision

Reference studied as ARCHITECTURE ONLY (no code copied, no dependency added,
no attribution owed): https://github.com/GoyDevv/IronizedZink (v1.1.0).

## 1. IronizedZink architecture (source references)

Path: LWJGL desktop GL -> launcher dlopen(`libglxshim.so`/`libEGL_mesa.so`)
-> Mesa `glapi` dispatch -> `libzink_dri.so` (Mesa 23.0.4 Zink Gallium:
GLSL -> NIR -> SPIR-V) -> Kopper WSI swapchain -> system Vulkan ICD
(Turnip/vendor) -> GPU. Renderer id
`opengles3_desktopgl_zink_kopper`; `MESA_LOADER_DRIVER_OVERRIDE=zink`
(`Presets.kt`, `NativeLibs.kt`). The JNI shim
(`ironized_zink.c:ironized_zink_init`, constructor) only injects
`MESA_*/GALLIUM_*/ZINK_*` env vars from an on-device config file; all
translation lives inside the prebuilt Mesa binaries (origin per CREDITS.md:
AngelAuraMC mesa_zink_kopper Android build). No APK-level glslang/SPIRV-Cross
(it is all inside Mesa). Shaders: full desktop GLSL accepted, real GL 4.6
via SPIR-V. Cost: ~11-13 MB native per ABI, full Mesa+Turnip stack, GLX-shim
vs EGL ownership questions, and every Mesa bug becomes our bug.

## 2. ONIGAMI audit (current tree, verified by reading the code)

- Dispatch: own-proc table (`src/proc_table.cpp`, ~125 names) answers
  wrapped GL/EGL entries with our own addresses first; backend fallback only
  for unwrapped names. Device-verified: game log shows the mapped version
  string, GUI shaders compile. No recursive dispatch (self-handle via
  RTLD_NOLOAD, backend handles separate).
- EGL: full display-path interposition chained to the single host driver;
  retryable probe that never seals failure (`src/egl_context.cpp`,
  `src/backend.cpp`).
- Translation: stage-1 structured migrator (version/profile analysis,
  precision contract, legacy keyword + output migration, buffer-sampler
  extension block, comment-aware, CRLF-tolerant) + vault keyed by
  source+stage+translator-schema (`src/shader_translate.cpp`,
  `src/program_store.cpp`). Pinned by 37 executed host assertions against
  the real gui shaders. glslang/SPIRV-Cross (vulkan-sdk-1.4.363.0) built and
  linked; compiler path staged, not yet wired.
- State: coalescing with counters, DRAW/READ FBO tracking split, deletes
  invalidate (recycled names), always-on cap no-ops (0x884F/0x8642),
  default-FBO draw-buffer + FBO-completeness + proxy-target attribution
  (`src/state_cache.*`, `src/gl_frontend.cpp`).
- Gaps (honest): desktop compute/geometry/tessellation stages reject loudly
  (staged); no device-rendering proof yet; long-tail entries are honest
  passthrough.

## 3. Decision matrix

| Criterion | A. GLES translation (current) | B. Vulkan/Zink stack |
|---|---|---|
| Vanilla 26.3 path | Proven to GUI+clouds-fix stage on-device | Would restart bring-up from zero |
| Sodium/Iris | Incremental; ES 3.1 covers compute/SSBO natively | Full GL 4.6, but whole Mesa integration first |
| Adreno 825 fit | Direct GLES 3.2 driver, no middleman | Turnip-on-Adreno + Zink overhead |
| Deps/licenses | glslang+SPIRV-Cross (permissive), own code | Full Mesa build (MIT, but must build/own it) or foreign prebuilts (forbidden) |
| APK/code size | ~2.6 MB .so, owned sources | ~12 MB+ prebuilt blobs + WSI work |
| Debuggability | Per-call logging, capture files | Mesa-internal failures, env-var tuning |
| Effort to menu | Weeks of targeted fixes (this round) | Months of stack integration |

DECISION: stay with A. Every device failure so far (empty version, dispatch
bypass, buffer-sampler extension, cap enums, FBO divergence) was addressable
inside the GLES path with mechanical, verifiable fixes - no evidence the path
is exhausted. A Vulkan rewrite is justified only by device proof that an
ES-unimplementable feature blocks vanilla/Iris; geometry/tessellation stages
are the known watch item and currently fail loudly, not silently.
Revisit only on such evidence.

## 4. Compatibility report (this build)

- Implemented: EGL ownership, dispatch, version mapping, translator stage-1
  (incl. texture-buffer block), vault (schema-versioned), state coalescing,
  DRAW/READ split, delete-invalidation, cap no-ops, draw-buffer/FBO/proxy
  attribution, backend ext advertisement logging.
- Automated-test verified: 37 translator assertions (CI host job), 4-ABI
  native build + symbol gate, CI unit tests/lint/APKs, release gates.
- Device-test verified: context creation, mapped version string, GUI shader
  compilation (previous round's log).
- Unsupported (loud failure, logged): desktop compute/geometry/tessellation
  stages; missing backend extensions (reported, never invented).
- Not yet tested: menu/world/clouds rendering, Sodium/Extra, Iris/packs,
  FPS. Requires the POCO F7 run of the new APK.
