// Host stub of the NDK EGL headers for ONIGAMI host unit tests.
// Values are the EGL 1.5 registry values. Only what backend.h needs.
// Never used for device builds; the NDK headers take precedence there.
#pragma once
typedef void* EGLDisplay;
typedef void* EGLConfig;
typedef void* EGLContext;
typedef void* EGLSurface;
typedef void* EGLClientBuffer;
typedef unsigned int EGLenum;
typedef int EGLint;
typedef unsigned int EGLBoolean;
typedef void* EGLNativeDisplayType;
typedef void* EGLNativeWindowType;
#define EGL_NO_DISPLAY ((EGLDisplay)0)
#define EGL_NO_CONTEXT ((EGLContext)0)
#define EGL_NO_SURFACE ((EGLSurface)0)
#define EGL_FALSE 0
#define EGL_TRUE 1
typedef void (*__eglMustCastToProperFunctionPointerType)(void);
#ifdef __cplusplus
extern "C" {
#endif
EGLDisplay eglGetDisplay(EGLNativeDisplayType display_id);
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor);
EGLBoolean eglTerminate(EGLDisplay dpy);
EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list, EGLConfig* configs, EGLint config_size, EGLint* num_config);
EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value);
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list);
EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx);
EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
EGLDisplay eglGetCurrentDisplay(void);
EGLContext eglGetCurrentContext(void);
EGLSurface eglGetCurrentSurface(EGLint readdraw);
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface);
const char* eglQueryString(EGLDisplay dpy, EGLint name);
EGLint eglGetError(void);
EGLBoolean eglBindAPI(EGLenum api);
EGLenum eglQueryAPI(void);
EGLBoolean eglReleaseThread(void);
__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* procname);
#ifdef __cplusplus
}
#endif
#define EGL_NOT_INITIALIZED 0x3001
