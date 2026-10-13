#pragma once
// ONIGAMI public C API. Own contract; nothing from any other renderer.
#include <stddef.h>
#if defined(__GNUC__) || defined(__clang__)
#define ONIGAMI_API __attribute__((visibility("default")))
#else
#define ONIGAMI_API
#endif
#ifdef __cplusplus
extern "C" {
#endif
ONIGAMI_API int onigami_version(void);
ONIGAMI_API const char* onigami_build_id(void);
// Retryable-probe strings: never null; "<unprobed>"-style sentinel only when
// no backend probe has succeeded yet (never an empty string).
ONIGAMI_API const char* onigami_renderer_string(void);
ONIGAMI_API const char* onigami_version_string(void);
// Counters proving state coalescing: redundant calls skipped.
ONIGAMI_API unsigned long long onigami_state_hits(void);
ONIGAMI_API unsigned long long onigami_state_skips(void);
// Latest refusal in plain text. Valid until the next call on this thread.
ONIGAMI_API const char* onigami_last_diag(void);
#ifdef __cplusplus
}
#endif
