// ONIGAMI interposer dispatch table. Own implementation.
//
// LWJGL on EGL resolves its GL function pointers through eglGetProcAddress,
// so this table MUST name every GL/EGL entry point this library wraps. Any
// wrapped name missing here (or failing self-resolution) falls back to the
// backend pointer and silently bypasses translation for that call. The table
// is checked against the real exports by the native workflow's symbol gate:
// every name below must resolve via our own handle (verified at runtime in
// diagnostics mode), and every exported wrapper should appear below.
#include "onigami/backend.h"
#include <dlfcn.h>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
namespace onigami {
namespace {
// Keep in sync with the O_API exports in egl_context.cpp / gl_frontend.cpp.
const char* kOwnNames[] = {
    "eglGetDisplay", "eglInitialize", "eglTerminate", "eglChooseConfig",
    "eglGetConfigAttrib", "eglCreateContext", "eglDestroyContext",
    "eglMakeCurrent", "eglGetCurrentDisplay", "eglGetCurrentContext",
    "eglGetCurrentSurface", "eglSwapBuffers", "eglQueryString",
    "eglGetError", "eglBindAPI", "eglQueryAPI", "eglReleaseThread",
    "eglGetProcAddress",
    "glGetString", "glGetStringi", "glGetError", "glGetIntegerv",
    "glGetFloatv", "glGetBooleanv",
    "glUseProgram", "glBindBuffer", "glBindTexture", "glActiveTexture",
    "glBindFramebuffer", "glBindRenderbuffer", "glBindVertexArray",
    "glEnable", "glDisable", "glIsEnabled", "glViewport", "glScissor",
    "glCreateShader", "glShaderSource", "glCompileShader", "glGetShaderiv",
    "glGetShaderInfoLog", "glDeleteShader", "glIsShader",
    "glCreateProgram", "glAttachShader", "glDetachShader", "glLinkProgram",
    "glGetProgramiv", "glGetProgramInfoLog", "glDeleteProgram", "glIsProgram",
    "glGetUniformLocation", "glGetAttribLocation", "glUniform1i",
    "glUniform1f", "glUniform4fv", "glUniformMatrix4fv",
    "glGenBuffers", "glBufferData", "glBufferSubData",
    "glGenVertexArrays", "glGenTextures", "glTexParameteri",
    "glTexImage2D", "glTexSubImage2D", "glGenerateMipmap",
    "glGenFramebuffers", "glCheckFramebufferStatus",
    "glFramebufferTexture2D", "glFramebufferRenderbuffer",
    "glGenRenderbuffers", "glRenderbufferStorage", "glBlitFramebuffer",
    "glEnableVertexAttribArray", "glDisableVertexAttribArray",
    "glVertexAttribPointer", "glVertexAttrib4f",
    "glClearColor", "glClear", "glBlendFunc", "glDepthMask", "glColorMask",
    "glPixelStorei", "glReadPixels",
    "glDrawArrays", "glDrawElements", "glDrawArraysInstanced",
    "glDrawElementsInstanced", "glFlush", "glFinish",
    "glDeleteBuffers", "glDeleteVertexArrays", "glDeleteTextures",
    "glDeleteFramebuffers", "glDeleteRenderbuffers",
    "glTexParameterf", "glClearDepthf", "glBlendFuncSeparate",
    "glBlendEquation", "glBlendEquationSeparate",
    "glDepthFunc", "glCullFace", "glFrontFace",
    "glStencilFunc", "glStencilMask", "glStencilOp",
    "glDepthRangef", "glPolygonOffset", "glLineWidth",
    "glVertexAttribDivisor",
    "glUniform2f", "glUniform3f", "glUniform1fv", "glUniform3fv",
    "glUniform1iv", "glUniformMatrix3fv",
    "glCompressedTexImage2D",
    "glDrawRangeElements", "glMultiDrawArrays", "glMultiDrawElements",
    "glBindAttribLocation", "glGetActiveUniform", "glGetActiveAttrib",
    "glProgramBinary", "glGetProgramBinary",
    "glMapBufferRange", "glUnmapBuffer",
    "glGenSamplers", "glBindSampler", "glSamplerParameteri",
    "glTexImage3D",
    "glGenQueries", "glBeginQuery", "glEndQuery",
    "glFenceSync", "glClientWaitSync", "glDeleteSync",
    "glDrawBuffer", "glDrawBuffers", "glReadBuffer",
    // Long-tail entry points (src/gl_longtail.cpp). These exist as exported
    // symbols precisely because eglGetProcAddress must never hand a desktop
    // name to the ES driver when ONIGAMI is the one that owns the semantics:
    // multi-draw is expanded by us, and the desktop-only names are refused by
    // us with a diagnostic rather than crash on a null driver pointer.
    "glMultiDrawElementsBaseVertex", "glMultiDrawElementsBaseVertexEXT",
    "glMultiDrawElementsEXT", "glMultiDrawArraysEXT",
    "glCreateBuffers", "glCreateVertexArrays", "glCreateTextures",
    "glCreateSamplers", "glCreateFramebuffers", "glCreateRenderbuffers",
    "glCreateQueries", "glCreateProgramPipelines",
    "glGetTexImage", "glPushDebugGroup", "glPopDebugGroup",
    nullptr,
};
std::mutex g_m;
void* g_self = nullptr;
bool g_self_tried = false;
bool g_self_logged = false;
std::unordered_map<std::string, void*> g_cache;
void* self_handle_locked() {
    if (!g_self_tried) {
        g_self_tried = true;
        // This library is already loaded (we are executing inside it), so a
        // RTLD_NOLOAD lookup by soname must succeed. RTLD_DEFAULT is the
        // fallback only; it can see other definitions, so it is never
        // preferred for wrapped names.
        g_self = dlopen("libonigami.so", RTLD_NOLOAD | RTLD_LOCAL);
    }
    return g_self;
}
} // namespace
void* own_proc(const char* name) {
    if (!name || !name[0]) return nullptr;
    {
        std::lock_guard<std::mutex> l(g_m);
        auto it = g_cache.find(name);
        if (it != g_cache.end()) return it->second;
    }
    void* found = nullptr;
    bool wrapped = false;
    for (const char** n = kOwnNames; *n; n++) {
        if (std::strcmp(*n, name) == 0) {
            wrapped = true;
            break;
        }
    }
    if (wrapped) {
        std::lock_guard<std::mutex> l(g_m);
        void* self = self_handle_locked();
        if (self) found = dlsym(self, name);
        if (!found && !g_self_logged) {
            g_self_logged = true;
            diag_printf("onigami: self-resolution failed for %s; proc falls back to backend (tracking bypassed for this entry)", name);
        }
        g_cache[name] = found;
    }
    return found;
}
} // namespace onigami
