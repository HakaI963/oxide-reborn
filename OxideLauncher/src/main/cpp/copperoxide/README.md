# Copper Oxide native source

Vendored first-party source of [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues)
(LGPL-2.1-only) at commit `97558a69157b033b890958b1e49258c5b51a80c6` (`main`).

- Tree: `upstream/MobileGlues-cpp/**` excluding the git submodules below,
  plus upstream `LICENSE` at `upstream/LICENSE`.
- Excluded submodule contents (resolved at build time, pinned SHAs):
  - `MobileGlues-cpp/3rdparty/glslang` @ `f5f664dee8146676b04a332a7233959fc3ce9681`
  - `MobileGlues-cpp/3rdparty/SPIRV-Cross` @ `a0fba56c34a6700f1724bf9b751da5b488a3775c`
  - `MobileGlues-cpp/3rdparty/xxhash` @ `c2866db364b6ea3a11933e62235ddc166ba18565`
  - `MobileGlues-cpp/3rdparty/perfetto` @ `40b529923598b739b2892a536a7692eedbed5685` (unused, PROFILING=OFF)
  - `MobileGlues-cpp/include/ska` @ `21c1cec95abee1beef827e4a7c95f692875d9594`
- Copper Oxide modifications to this tree are listed in `THIRD_PARTY.md`.
  Everything else is byte-identical to upstream at the pinned commit.
- Built by `.github/workflows/copperoxide.yml` (GitHub Actions, NDK + CMake)
  into `libcopperoxide.so`; never compiled locally.
