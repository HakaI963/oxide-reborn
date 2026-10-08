#pragma once
// Silica public C API. Own contract; nothing from MobileGlues.
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
const char* silica_version(void);
// Probe-safe strings: never null; fall back to cached probe + log.
const char* silica_renderer_string(void);
const char* silica_version_string(void);
// Counters proving state dedup (perf lever): redundant calls skipped.
unsigned long long silica_state_hits(void);
unsigned long long silica_state_skips(void);
#ifdef __cplusplus
}
#endif
