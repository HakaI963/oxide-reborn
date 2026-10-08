#pragma once
// Oxide <-> Silica interface (mirrors Kotlin SilicaPipeline contracts).
// The launcher owns process/lifecycle/surface/scheduling; Silica owns GL policy.
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct SilicaSurface { int width; int height; int offset_x; int offset_y; } SilicaSurface;
int silica_on_process_start(const char* data_dir);
int silica_on_surface(const SilicaSurface* s);
int silica_on_surface_lost(void);
int silica_on_process_end(void);
int silica_set_mode(int mode, int cache_mb, int upscale);
#ifdef __cplusplus
}
#endif
