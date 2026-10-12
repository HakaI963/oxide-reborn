// Own-engine logic: state, init, compute passes, present, stats, JNI.
#include "silica_core.inc"

namespace silica_fg {
namespace {

std::mutex g_mu;
bool g_configured = false;
bool g_enabled = false;
int g_mult = 2;
bool g_guard = true;
std::string g_dataDir;
std::string g_reason = "not configured yet";
unsigned long long g_native = 0, g_displayed = 0, g_gen = 0, g_drop = 0;
double g_genMs = 0.0;
long long g_lastSwapNs = 0, g_lastPublishNs = 0;
bool g_loggedReady = false, g_loggedGen = false;

Gl g_gl;
bool g_resolved = false;
EGLContext g_ctx = EGL_NO_CONTEXT;
int g_w = 0, g_h = 0, g_qw = 0, g_qh = 0, g_fw = 0, g_fh = 0;
GLuint g_tex[2] = {0, 0};       // retained RGBA8 full-res
GLuint g_luma[2] = {0, 0};      // retained R8 quarter-res luma
GLuint g_flow[2] = {0, 0};      // RG16F flow grid (0 match-out, 1 median-out)
GLuint g_fbo = 0, g_vbo = 0, g_vao = 0, g_samp = 0, g_sampFlow = 0;
GLuint g_pBox = 0, g_pFlow = 0, g_pMed = 0, g_pWarp = 0;
GLint g_boxSize = -1, g_flowSize = -1, g_flowGrid = -1, g_medSize = -1;
GLint g_wPrev = -1, g_wCurr = -1, g_wFlow = -1, g_wPx = -1, g_wT = -1,
      g_wGuard = -1;
int g_turn = 0, g_have = 0, g_confails = 0;
const int kMaxFails = 3;

long long mono_ns() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (long long)ts.tv_sec * 1000000000LL + (long long)ts.tv_nsec;
}

void diag(const char* m) {
  std::string dir = g_dataDir.empty() ? "/data/local/tmp/silica-fg" : g_dataDir;
  // best-effort file log (same UID files dir when configured)
  std::string fp = dir + "/silica-fg.log";
#if __has_include(<android/log.h>)
  __android_log_print(ANDROID_LOG_INFO, "silica-fg", "%s", m);
#endif
  if (FILE* f = fopen(fp.c_str(), "a")) {
    fprintf(f, "%s\n", m);
    fclose(f);
  }
}

void publish_locked() {
  long long now = mono_ns();
  if (now - g_lastPublishNs < 1000000000LL) return;
  g_lastPublishNs = now;
  if (g_dataDir.empty()) return;
  std::string dst = g_dataDir + "/silica_fg_stats.json";
  std::string tmp = dst + ".tmp";
  std::ofstream f(tmp.c_str(), std::ios::trunc);
  if (!f) return;
  f << "{\"enabled\":" << (g_enabled ? 1 : 0)
    << ",\"multiplier\":" << g_mult
    << ",\"native_frames\":" << g_native
    << ",\"displayed_frames\":" << g_displayed
    << ",\"generated_frames\":" << g_gen
    << ",\"dropped\":" << g_drop
    << ",\"reason\":\"" << g_reason << "\"}";
  f.close();
  rename(tmp.c_str(), dst.c_str());
}

void note_fail_locked(const char* what) {
  if (++g_confails >= kMaxFails && g_enabled) {
    g_enabled = false;
    g_reason = std::string("backend error (") + what +
               "); disabled, native path intact";
    char b[256];
    snprintf(b, sizeof(b), "silica-fg: DISABLED: %s", g_reason.c_str());
    diag(b);
  }
}

void drain_errors() {
  for (int i = 0; i < 8; ++i)
    if (g_gl.GetError() == GL_NO_ERROR) break;
}

bool resolve_gl_locked() {
  if (g_resolved) return true;
  void* eglH = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
  if (!eglH) {
    g_reason = "libEGL.so not loadable";
    return false;
  }
  auto get = [eglH](const char* n) {
    void* p = dlsym(eglH, n);
    if (!p) {
      void* gles = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_LOCAL);
      if (gles) p = dlsym(gles, n);
    }
    return p;
  };
#define SFG_NEED(fn, str)                             \
  g_gl.fn = reinterpret_cast<P_##fn>(get(str));       \
  if (!g_gl.fn) {                                     \
    g_reason = std::string("GLES proc missing: ") + str; \
    return false;                                     \
  }
  SFG_NEED(GetError, "glGetError");
  SFG_NEED(GetIntegerv, "glGetIntegerv");
  SFG_NEED(GetString, "glGetString");
  SFG_NEED(IsEnabled, "glIsEnabled");
  SFG_NEED(Enable, "glEnable");
  SFG_NEED(Disable, "glDisable");
  SFG_NEED(Viewport, "glViewport");
  SFG_NEED(ActiveTexture, "glActiveTexture");
  SFG_NEED(GenTextures, "glGenTextures");
  SFG_NEED(BindTexture, "glBindTexture");
  SFG_NEED(TexParameteri, "glTexParameteri");
  SFG_NEED(TexImage2D, "glTexImage2D");
  SFG_NEED(DeleteTextures, "glDeleteTextures");
  SFG_NEED(BindImageTexture, "glBindImageTexture");
  SFG_NEED(GenSamplers, "glGenSamplers");
  SFG_NEED(SamplerParameteri, "glSamplerParameteri");
  SFG_NEED(BindSampler, "glBindSampler");
  SFG_NEED(DeleteSamplers, "glDeleteSamplers");
  SFG_NEED(GenFramebuffers, "glGenFramebuffers");
  SFG_NEED(BindFramebuffer, "glBindFramebuffer");
  SFG_NEED(FramebufferTexture2D, "glFramebufferTexture2D");
  SFG_NEED(CheckFramebufferStatus, "glCheckFramebufferStatus");
  SFG_NEED(DeleteFramebuffers, "glDeleteFramebuffers");
  SFG_NEED(BlitFramebuffer, "glBlitFramebuffer");
  SFG_NEED(GenBuffers, "glGenBuffers");
  SFG_NEED(BindBuffer, "glBindBuffer");
  SFG_NEED(BufferData, "glBufferData");
  SFG_NEED(DeleteBuffers, "glDeleteBuffers");
  SFG_NEED(GenVertexArrays, "glGenVertexArrays");
  SFG_NEED(BindVertexArray, "glBindVertexArray");
  SFG_NEED(DeleteVertexArrays, "glDeleteVertexArrays");
  SFG_NEED(EnableVertexAttribArray, "glEnableVertexAttribArray");
  SFG_NEED(VertexAttribPointer, "glVertexAttribPointer");
  SFG_NEED(CreateShader, "glCreateShader");
  SFG_NEED(ShaderSource, "glShaderSource");
  SFG_NEED(CompileShader, "glCompileShader");
  SFG_NEED(GetShaderiv, "glGetShaderiv");
  SFG_NEED(GetShaderInfoLog, "glGetShaderInfoLog");
  SFG_NEED(DeleteShader, "glDeleteShader");
  SFG_NEED(CreateProgram, "glCreateProgram");
  SFG_NEED(AttachShader, "glAttachShader");
  SFG_NEED(LinkProgram, "glLinkProgram");
  SFG_NEED(GetProgramiv, "glGetProgramiv");
  SFG_NEED(GetProgramInfoLog, "glGetProgramInfoLog");
  SFG_NEED(DeleteProgram, "glDeleteProgram");
  SFG_NEED(UseProgram, "glUseProgram");
  SFG_NEED(GetUniformLocation, "glGetUniformLocation");
  SFG_NEED(Uniform1i, "glUniform1i");
  SFG_NEED(Uniform1f, "glUniform1f");
  SFG_NEED(Uniform2f, "glUniform2f");
  SFG_NEED(Uniform2i, "glUniform2i");
  SFG_NEED(DispatchCompute, "glDispatchCompute");
  SFG_NEED(MemoryBarrier, "glMemoryBarrier");
  SFG_NEED(DrawArrays, "glDrawArrays");
#undef SFG_NEED
  g_gl.GetCurrentContext =
      reinterpret_cast<P_eglGetCurrentContext>(get("eglGetCurrentContext"));
  g_gl.QuerySurface =
      reinterpret_cast<P_eglQuerySurface>(get("eglQuerySurface"));
  if (!g_gl.GetCurrentContext || !g_gl.QuerySurface) {
    g_reason = "EGL procs missing";
    return false;
  }
  g_resolved = true;
  return true;
}
