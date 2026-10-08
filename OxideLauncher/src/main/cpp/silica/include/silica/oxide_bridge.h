#pragma once
// Oxide <-> Silica interface (mirrors Kotlin SilicaPipeline contracts).
// The launcher owns process/lifecycle/surface/scheduling; Silica owns GL policy.
#include <stdint.h>
#if defined(__GNUC__) || defined(__clang__)
#define SILICA_API __attribute__((visibility("default")))
#else
#define SILICA_API
#endif
#ifdef __cplusplus
extern "C" {
#endif
typedef struct SilicaSurface { int width; int height; int offset_x; int offset_y; } SilicaSurface;
SILICA_API int silica_on_process_start(const char* data_dir);
SILICA_API int silica_on_surface(const SilicaSurface* s);
SILICA_API int silica_on_surface_lost(void);
SILICA_API int silica_on_process_end(void);
SILICA_API int silica_set_mode(int mode, int cache_mb, int upscale);
#ifdef __cplusplus
}
#endif
