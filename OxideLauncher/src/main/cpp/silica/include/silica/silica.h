#pragma once
// Silica public C API. Own contract; nothing from MobileGlues.
#include <stddef.h>
#if defined(__GNUC__) || defined(__clang__)
#define SILICA_API __attribute__((visibility("default")))
#else
#define SILICA_API
#endif
#ifdef __cplusplus
extern "C" {
#endif
SILICA_API const char* silica_version(void);
// Probe-safe strings: never null; fall back to cached probe + log.
SILICA_API const char* silica_renderer_string(void);
SILICA_API const char* silica_version_string(void);
// Counters proving state dedup (perf lever): redundant calls skipped.
SILICA_API unsigned long long silica_state_hits(void);
SILICA_API unsigned long long silica_state_skips(void);
#ifdef __cplusplus
}
#endif
