#pragma once
// ONIGAMI single-driver backend loader + retryable probe cache. Own design.
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <mutex>
#include <string>
namespace onigami {
std::string data_dir();
void diag_write(const char* msg);
void diag_printf(const char* fmt, ...);
void set_last_diag(const char* msg);
const char* bridge_data_dir_cstr();
std::mutex& egl_mutex();
std::string& cached_renderer();
std::string& cached_version();
void try_probe_renderer_version();
// Interposer dispatch: address of ONIGAMI's own wrapper for a GL/EGL entry
// point name, or nullptr when ONIGAMI does not wrap that name.
void* own_proc(const char* name);
struct EglProcs {
  void* handle = nullptr;
  decltype(&::eglGetDisplay) GetDisplay = nullptr;
  decltype(&::eglInitialize) Initialize = nullptr;
  decltype(&::eglTerminate) Terminate = nullptr;
  decltype(&::eglChooseConfig) ChooseConfig = nullptr;
  decltype(&::eglGetConfigAttrib) GetConfigAttrib = nullptr;
  decltype(&::eglCreateContext) CreateContext = nullptr;
  decltype(&::eglDestroyContext) DestroyContext = nullptr;
  decltype(&::eglMakeCurrent) MakeCurrent = nullptr;
  decltype(&::eglGetCurrentDisplay) GetCurrentDisplay = nullptr;
  decltype(&::eglGetCurrentContext) GetCurrentContext = nullptr;
  decltype(&::eglGetCurrentSurface) GetCurrentSurface = nullptr;
  decltype(&::eglSwapBuffers) SwapBuffers = nullptr;
  decltype(&::eglQueryString) QueryString = nullptr;
  decltype(&::eglGetError) GetError = nullptr;
  decltype(&::eglBindAPI) BindAPI = nullptr;
  decltype(&::eglQueryAPI) QueryAPI = nullptr;
  decltype(&::eglReleaseThread) ReleaseThread = nullptr;
  decltype(&::eglGetProcAddress) GetProcAddress = nullptr;
};
EglProcs& egl_procs();
bool ensure_egl_loaded();
struct GlesProcs {
  void* handle = nullptr;
  decltype(&::glGetString) GetString = nullptr;
  decltype(&::glGetStringi) GetStringi = nullptr;
  decltype(&::glGetError) GetError = nullptr;
  decltype(&::glGetIntegerv) GetIntegerv = nullptr;
  decltype(&::glGetFloatv) GetFloatv = nullptr;
  decltype(&::glGetBooleanv) GetBooleanv = nullptr;
  decltype(&::glCreateShader) CreateShader = nullptr;
  decltype(&::glShaderSource) ShaderSource = nullptr;
  decltype(&::glCompileShader) CompileShader = nullptr;
  decltype(&::glGetShaderiv) GetShaderiv = nullptr;
  decltype(&::glGetShaderInfoLog) GetShaderInfoLog = nullptr;
  decltype(&::glDeleteShader) DeleteShader = nullptr;
  decltype(&::glIsShader) IsShader = nullptr;
  decltype(&::glCreateProgram) CreateProgram = nullptr;
  decltype(&::glAttachShader) AttachShader = nullptr;
  decltype(&::glDetachShader) DetachShader = nullptr;
  decltype(&::glLinkProgram) LinkProgram = nullptr;
  decltype(&::glGetProgramiv) GetProgramiv = nullptr;
  decltype(&::glGetProgramInfoLog) GetProgramInfoLog = nullptr;
  decltype(&::glDeleteProgram) DeleteProgram = nullptr;
  decltype(&::glIsProgram) IsProgram = nullptr;
  decltype(&::glUseProgram) UseProgram = nullptr;
  decltype(&::glGetUniformLocation) GetUniformLocation = nullptr;
  decltype(&::glGetAttribLocation) GetAttribLocation = nullptr;
  decltype(&::glUniform1i) Uniform1i = nullptr;
  decltype(&::glUniform1f) Uniform1f = nullptr;
  decltype(&::glUniform4fv) Uniform4fv = nullptr;
  decltype(&::glUniformMatrix4fv) UniformMatrix4fv = nullptr;
  decltype(&::glGenBuffers) GenBuffers = nullptr;
  decltype(&::glBindBuffer) BindBuffer = nullptr;
  decltype(&::glBufferData) BufferData = nullptr;
  decltype(&::glBufferSubData) BufferSubData = nullptr;
  decltype(&::glGenVertexArrays) GenVertexArrays = nullptr;
  decltype(&::glBindVertexArray) BindVertexArray = nullptr;
  decltype(&::glGenTextures) GenTextures = nullptr;
  decltype(&::glBindTexture) BindTexture = nullptr;
  decltype(&::glActiveTexture) ActiveTexture = nullptr;
  decltype(&::glTexParameteri) TexParameteri = nullptr;
  decltype(&::glTexImage2D) TexImage2D = nullptr;
  decltype(&::glTexSubImage2D) TexSubImage2D = nullptr;
  decltype(&::glGenerateMipmap) GenerateMipmap = nullptr;
  decltype(&::glGenFramebuffers) GenFramebuffers = nullptr;
  decltype(&::glBindFramebuffer) BindFramebuffer = nullptr;
  decltype(&::glCheckFramebufferStatus) CheckFramebufferStatus = nullptr;
  decltype(&::glFramebufferTexture2D) FramebufferTexture2D = nullptr;
  decltype(&::glFramebufferRenderbuffer) FramebufferRenderbuffer = nullptr;
  decltype(&::glGenRenderbuffers) GenRenderbuffers = nullptr;
  decltype(&::glBindRenderbuffer) BindRenderbuffer = nullptr;
  decltype(&::glRenderbufferStorage) RenderbufferStorage = nullptr;
  decltype(&::glBlitFramebuffer) BlitFramebuffer = nullptr;
  decltype(&::glEnableVertexAttribArray) EnableVertexAttribArray = nullptr;
  decltype(&::glDisableVertexAttribArray) DisableVertexAttribArray = nullptr;
  decltype(&::glVertexAttribPointer) VertexAttribPointer = nullptr;
  decltype(&::glVertexAttrib4f) VertexAttrib4f = nullptr;
  decltype(&::glViewport) Viewport = nullptr;
  decltype(&::glScissor) Scissor = nullptr;
  decltype(&::glClear) Clear = nullptr;
  decltype(&::glClearColor) ClearColor = nullptr;
  decltype(&::glEnable) Enable = nullptr;
  decltype(&::glDisable) Disable = nullptr;
  decltype(&::glIsEnabled) IsEnabled = nullptr;
  decltype(&::glBlendFunc) BlendFunc = nullptr;
  decltype(&::glDepthMask) DepthMask = nullptr;
  decltype(&::glColorMask) ColorMask = nullptr;
  decltype(&::glPixelStorei) PixelStorei = nullptr;
  decltype(&::glReadPixels) ReadPixels = nullptr;
  decltype(&::glDrawArrays) DrawArrays = nullptr;
  decltype(&::glDrawElements) DrawElements = nullptr;
  decltype(&::glDrawArraysInstanced) DrawArraysInstanced = nullptr;
  decltype(&::glDrawElementsInstanced) DrawElementsInstanced = nullptr;
  decltype(&::glFinish) Finish = nullptr;
  decltype(&::glFlush) Flush = nullptr;
};
GlesProcs& gles_procs();
bool ensure_gles_loaded();
} // namespace onigami
