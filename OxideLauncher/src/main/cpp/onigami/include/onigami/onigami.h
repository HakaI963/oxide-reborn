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
// Frame generation (v1.17.0, LSFG-VK derived EGL backend).
// All counters count only successfully presented frames; nothing is estimated.
ONIGAMI_API int onigami_framegen_supported(void);
ONIGAMI_API int onigami_framegen_enabled(void);
ONIGAMI_API int onigami_framegen_multiplier(void);
ONIGAMI_API const char* onigami_framegen_status(void);
// Monotonic present counters (native = game frames, displayed = native +
// confirmed generated presents). gen_ms_ema is the EMA of interpolation cost.
ONIGAMI_API unsigned long long onigami_native_frames(void);
ONIGAMI_API unsigned long long onigami_displayed_frames(void);
ONIGAMI_API unsigned long long onigami_generated_frames(void);
ONIGAMI_API unsigned long long onigami_dropped_frames(void);
// Bridge present hook: the JNI bridge (system EGL path) calls this around
// its backend swap. Returns the native swap result; generated presents are
// issued inside and counted only on success.
ONIGAMI_API int onigami_hook_swap_buffers(void* display, void* surface,
    int (*real_swap)(void*, void*));
// Latest refusal in plain text. Valid until the next call on this thread.
ONIGAMI_API const char* onigami_last_diag(void);
#ifdef __cplusplus
}
#endif
