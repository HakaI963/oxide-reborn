#pragma once
// Oxide <-> ONIGAMI lifecycle (mirrors Kotlin OnigamiPipeline contracts).
// The launcher owns process/lifecycle/surface/scheduling; ONIGAMI owns GL policy.
#include "onigami/onigami.h"
#ifdef __cplusplus
extern "C" {
#endif
ONIGAMI_API int onigami_on_process_start(const char* data_dir);
ONIGAMI_API int onigami_on_surface(void* native_window, int width, int height);
ONIGAMI_API int onigami_on_surface_lost(void);
ONIGAMI_API int onigami_on_process_end(void);
ONIGAMI_API int onigami_set_mode(int mode);
ONIGAMI_API int onigami_get_mode(void);
#ifdef __cplusplus
}
#endif
