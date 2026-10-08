# Silica native backend (own implementation)

Independent renderer. No MobileGlues/Copper Oxide code is compiled here, linked
here, or called here. cpp/copperoxide/ is REFERENCE ONLY (see ARCH_MAP.md).

Stages: EGL/context + entry + state + caps + probe-safe strings (this skeleton,
fully implemented) -> shader pipeline (glslang/SPIRV-Cross, real path) ->
program cache -> framebuffers/textures/buffers/uniforms/sync/swap (staged
headers, implemented incrementally with measurements).

Library choice (evaluated, not popularity):
- glslang: maintained GLSL->SPIR-V frontend, matches Iris-era GLSL versions.
- SPIRV-Cross: maintained SPIR-V->GLES backend, quality output for Adreno.
- shaderc / Mesa components rejected for phase 1: extra weight with no measured
  advantage on Android/Adreno for this path; re-evaluate with profiles.

Built ONLY by .github/workflows/silica.yml (dispatch, 4 ABIs, NDK + CMake).
Never built locally.
