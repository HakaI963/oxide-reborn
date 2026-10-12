#pragma once
// SilicaFrameGen own GLES frame-generation engine (v1.19.0+).
//
// Clean-room implementation for the only present path that provably runs:
// the JNI bridge swap (system EGL). GLES 3.1 compute optical flow
// (hierarchical block-match + median, published techniques) + warp-blend
// synthesis. No third-party FG code. Win-fg stays untouched for the true
// Vulkan path. Called from the bridge; self-gates on JNI configuration
// (default off = direct swap, zero behavior change otherwise).
#include <EGL/egl.h>

#ifdef __cplusplus
extern "C" {
#endif

// Present hook for the JNI bridge. Runs retention + (mult-1) interpolated
// presents around real_swap. Returns the NATIVE swap result. Generated
// frames are counted only on confirmed swaps.
EGLBoolean silica_fg_present_hook(EGLDisplay d, EGLSurface s,
    EGLBoolean (*real_swap)(EGLDisplay, EGLSurface));

#ifdef __cplusplus
}
#endif
