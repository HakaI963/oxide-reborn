// Silica shader translation pipeline. Own design on glslang + SPIRV-Cross.
// Policy (measured, not assumed): dedup variants, async compile where safe,
// program binary cache (see cache/), no compute-for-compute's-sake.
#include <android/log.h>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::shader {
// Stage 5 implements: parse GLSL -> SPIR-V (glslang) -> GLES GLSL
// (SPIRV-Cross, Adreno-friendly output) -> driver compile -> cache store.
// Skeleton links both libraries so the builder verifies the dependency set;
// the full path lands with vanilla-Minecraft bring-up (stage 10) + Iris (11).
void note_stage(const char* s) { SLOG(DEBUG, "silica shader stage: %s", s); }
} // namespace silica::shader
