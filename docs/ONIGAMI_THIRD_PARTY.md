# ONIGAMI third-party components

Only two third-party compiler libraries are used by the ONIGAMI native backend.
No renderer code (Copper Oxide, MobileGlues, GL4ES, Zink, Silica, or any other
GL layer) is reused at any layer — neither source, nor binaries, nor symbols.
The native CI workflow fails the build if foreign renderer symbols appear.

## Pins (vendored at build time via CMake FetchContent)

| Component | Pin (commit) | Upstream URL | License | Purpose |
|---|---|---|---|---|
| glslang | `f5f664dee8146676b04a332a7233959fc3ce9681` | https://github.com/KhronosGroup/glslang | BSD-3-Clause-style (`LICENSE.txt`) with Apache-2.0 headers on newer contributions — verify at vendor time | Parse + link desktop GLSL, emit SPIR-V |
| SPIRV-Cross | `a0fba56c34a6700f1724bf9b751da5b488a3775c` | https://github.com/KhronosGroup/SPIRV-Cross | Apache-2.0 (`LICENSE`) | Convert SPIR-V to ESSL for the ES driver |

> Nothing in this section is legal advice. License texts of the pinned commits
> apply as shipped upstream; their notices are preserved in the build tree and
> any distribution that includes them.

## Compliance notes

- Pins move only deliberately (a commit bump with a reason), never by floating
  branch or tag.
- The two libraries are a shader compiler stack only; they contribute no
  EGL/GL-runtime behavior.
- No LGPL/GPL-licensed renderer code is involved in ONIGAMI, so no
  corresponding source-distribution obligation arises from this stack beyond
  permissive-license notice preservation.
- Provenance: KhronosGroup upstream repositories, fetched directly by hash at
  native build time (see `OxideLauncher/src/main/cpp/onigami/CMakeLists.txt`).
