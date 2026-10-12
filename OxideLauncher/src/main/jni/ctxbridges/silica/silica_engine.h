#pragma once
// Silica own-FG engine, linked INTO libpojavexec (the JNI bridge).
// No dlopen/dlsym/env: the bridge calls these directly; config is pulled
// over JNI from game-process singletons. Default-off everywhere.
#include <EGL/egl.h>

#ifdef __cplusplus
extern "C" {
#endif

// Bridge present path: retain before the backend swap, count+generate after.
void silica_engine_before_swap(EGLDisplay dpy, EGLSurface surf);
void silica_engine_after_swap(EGLDisplay dpy, EGLSurface surf, int ok);
// dlsym-proxy path (SDL/LWJGL-EGL): full swap through the engine.
EGLBoolean silica_engine_wrapped_swap(EGLDisplay dpy, EGLSurface surf);
// Build marker for release-gate verification.
const char* silica_fg_build_id(void);

#ifdef __cplusplus
}
#endif
