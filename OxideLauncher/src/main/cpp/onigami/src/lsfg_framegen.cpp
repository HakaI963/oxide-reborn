// ONIGAMI frame generation backend: full Nx interpolated-present path (v1.17.0).
//
// LSFG-VK provenance (MIT, Copyright (c) 2024 LSFG-VK Contributors):
// history depth, EMA pacing (4ms..200ms clamp), t=k/N timing and warp/blend
// interpolation concepts are adapted from layer/optical_flow.*,
// layer/present_scheduler.* and shaders/{pyramid,motion_vectors,warp,blend,
// ui_mask}.comp. The Vulkan swapchain interception is intentionally not
// carried over (see docs/ONIGAMI_LSFG_V1_17_0.md). The EGL analogue lives
// here: GPU-side frame retention (blit, no CPU readback) + real interpolated
// presents, reached via the JNI bridge hook (onigami_hook_swap_buffers),
// because the bridge resolves system EGL directly and libonigami's own
// eglSwapBuffers wrapper never fires on the real present path.
//
// Quality staging (honest): v1.17.0 dispatches BLEND + a per-pixel
// scene-change guard (artifact clamp). The 4-level hierarchical Lucas-Kanade
// motion stage (LSFG terms: PYRAMID_LEVELS=4, BLOCK_SIZE=8, MAX_ITERATIONS=5)
// is NOT dispatched on mobile GPUs pending Adreno 825 profiling; the shader
// already takes uT/uGuard and is structured so a motion-vector texture plugs
// into a warp without changing the present path. Exposed: 2x/3x/4x.
#include "onigami/lsfg.h"

#include "onigami/backend.h"
#include "onigami/config.h"
#include "onigami/bridge.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <cstdio>
#include <ctime>
#include <fstream>
#include <mutex>
#include <string>
#include <sys/stat.h>

namespace onigami {
namespace lsfg {
namespace {

// --------------------------------------------------------------------------
// Raw backend GLES entry points (bypass ONIGAMI wrappers: our objects must
// never pass through shader translation or state tracking).
// --------------------------------------------------------------------------
#define O_LSFG_GL_FN(ret, name, ...) typedef ret (*P_##name)(__VA_ARGS__)
O_LSFG_GL_FN(GLenum, glGetError, void);
O_LSFG_GL_FN(void, glGetIntegerv, GLenum pname, GLint* data);
O_LSFG_GL_FN(GLboolean, glIsEnabled, GLenum cap);
O_LSFG_GL_FN(void, glEnable, GLenum cap);
O_LSFG_GL_FN(void, glDisable, GLenum cap);
O_LSFG_GL_FN(void, glViewport, GLint x, GLint y, GLsizei w, GLsizei h);
O_LSFG_GL_FN(void, glActiveTexture, GLenum texture);
O_LSFG_GL_FN(void, glGenTextures, GLsizei n, GLuint* textures);
O_LSFG_GL_FN(void, glBindTexture, GLenum target, GLuint texture);
O_LSFG_GL_FN(void, glTexParameteri, GLenum target, GLenum pname, GLint param);
O_LSFG_GL_FN(void, glTexImage2D, GLenum target, GLint level, GLint internalformat,
              GLsizei width, GLsizei height, GLint border, GLenum format,
              GLenum type, const void* pixels);
O_LSFG_GL_FN(void, glDeleteTextures, GLsizei n, const GLuint* textures);
O_LSFG_GL_FN(void, glGenFramebuffers, GLsizei n, GLuint* ids);
O_LSFG_GL_FN(void, glBindFramebuffer, GLenum target, GLuint fbo);
O_LSFG_GL_FN(void, glFramebufferTexture2D, GLenum target, GLenum attachment,
              GLenum textarget, GLuint texture, GLint level);
O_LSFG_GL_FN(GLenum, glCheckFramebufferStatus, GLenum target);
O_LSFG_GL_FN(void, glDeleteFramebuffers, GLsizei n, const GLuint* ids);
O_LSFG_GL_FN(void, glBlitFramebuffer, GLint sx0, GLint sy0, GLint sx1, GLint sy1,
              GLint dx0, GLint dy0, GLint dx1, GLint dy1, GLbitfield mask,
              GLenum filter);
O_LSFG_GL_FN(void, glGenBuffers, GLsizei n, GLuint* buffers);
O_LSFG_GL_FN(void, glBindBuffer, GLenum target, GLuint buffer);
O_LSFG_GL_FN(void, glBufferData, GLenum target, GLsizeiptr size,
              const void* data, GLenum usage);
O_LSFG_GL_FN(void, glDeleteBuffers, GLsizei n, const GLuint* buffers);
O_LSFG_GL_FN(void, glGenVertexArrays, GLsizei n, GLuint* arrays);
O_LSFG_GL_FN(void, glBindVertexArray, GLuint array);
O_LSFG_GL_FN(void, glDeleteVertexArrays, GLsizei n, const GLuint* arrays);
O_LSFG_GL_FN(void, glEnableVertexAttribArray, GLuint index);
O_LSFG_GL_FN(void, glVertexAttribPointer, GLuint index, GLint size, GLenum type,
              GLboolean normalized, GLsizei stride, const void* pointer);
O_LSFG_GL_FN(GLuint, glCreateShader, GLenum type);
O_LSFG_GL_FN(void, glShaderSource, GLuint shader, GLsizei count,
              const GLchar* const* string, const GLint* length);
O_LSFG_GL_FN(void, glCompileShader, GLuint shader);
O_LSFG_GL_FN(void, glGetShaderiv, GLuint shader, GLenum pname, GLint* params);
O_LSFG_GL_FN(void, glGetShaderInfoLog, GLuint shader, GLsizei bufsize,
              GLsizei* length, GLchar* infolog);
O_LSFG_GL_FN(void, glDeleteShader, GLuint shader);
O_LSFG_GL_FN(GLuint, glCreateProgram, void);
O_LSFG_GL_FN(void, glAttachShader, GLuint program, GLuint shader);
O_LSFG_GL_FN(void, glLinkProgram, GLuint program);
O_LSFG_GL_FN(void, glGetProgramiv, GLuint program, GLenum pname, GLint* params);
O_LSFG_GL_FN(void, glGetProgramInfoLog, GLuint program, GLsizei bufsize,
              GLsizei* length, GLchar* infolog);
O_LSFG_GL_FN(void, glDeleteProgram, GLuint program);
O_LSFG_GL_FN(void, glUseProgram, GLuint program);
O_LSFG_GL_FN(GLint, glGetUniformLocation, GLuint program, const GLchar* name);
O_LSFG_GL_FN(void, glUniform1i, GLint location, GLint v0);
O_LSFG_GL_FN(void, glUniform1f, GLint location, GLfloat v0);
O_LSFG_GL_FN(void, glDrawArrays, GLenum mode, GLint first, GLsizei count);
#undef O_LSFG_GL_FN

struct Gl {
  P_glGetError GetError = nullptr;
  P_glGetIntegerv GetIntegerv = nullptr;
  P_glIsEnabled IsEnabled = nullptr;
  P_glEnable Enable = nullptr;
  P_glDisable Disable = nullptr;
  P_glViewport Viewport = nullptr;
  P_glActiveTexture ActiveTexture = nullptr;
  P_glGenTextures GenTextures = nullptr;
  P_glBindTexture BindTexture = nullptr;
  P_glTexParameteri TexParameteri = nullptr;
  P_glTexImage2D TexImage2D = nullptr;
  P_glDeleteTextures DeleteTextures = nullptr;
  P_glGenFramebuffers GenFramebuffers = nullptr;
  P_glBindFramebuffer BindFramebuffer = nullptr;
  P_glFramebufferTexture2D FramebufferTexture2D = nullptr;
  P_glCheckFramebufferStatus CheckFramebufferStatus = nullptr;
  P_glDeleteFramebuffers DeleteFramebuffers = nullptr;
  P_glBlitFramebuffer BlitFramebuffer = nullptr;
  P_glGenBuffers GenBuffers = nullptr;
  P_glBindBuffer BindBuffer = nullptr;
  P_glBufferData BufferData = nullptr;
  P_glDeleteBuffers DeleteBuffers = nullptr;
  P_glGenVertexArrays GenVertexArrays = nullptr;
  P_glBindVertexArray BindVertexArray = nullptr;
  P_glDeleteVertexArrays DeleteVertexArrays = nullptr;
  P_glEnableVertexAttribArray EnableVertexAttribArray = nullptr;
  P_glVertexAttribPointer VertexAttribPointer = nullptr;
  P_glCreateShader CreateShader = nullptr;
  P_glShaderSource ShaderSource = nullptr;
  P_glCompileShader CompileShader = nullptr;
  P_glGetShaderiv GetShaderiv = nullptr;
  P_glGetShaderInfoLog GetShaderInfoLog = nullptr;
  P_glDeleteShader DeleteShader = nullptr;
  P_glCreateProgram CreateProgram = nullptr;
  P_glAttachShader AttachShader = nullptr;
  P_glLinkProgram LinkProgram = nullptr;
  P_glGetProgramiv GetProgramiv = nullptr;
  P_glGetProgramInfoLog GetProgramInfoLog = nullptr;
  P_glDeleteProgram DeleteProgram = nullptr;
  P_glUseProgram UseProgram = nullptr;
  P_glGetUniformLocation GetUniformLocation = nullptr;
  P_glUniform1i Uniform1i = nullptr;
  P_glUniform1f Uniform1f = nullptr;
  P_glDrawArrays DrawArrays = nullptr;
};

// Blend-interpolation shaders (ESSL 300 es), derived from LSFG-VK's
// warp/blend stages reduced to the dispatched subset: bidirectional sample
// of the retained pair at uT, plus a scene-change artifact guard that snaps
// to the current frame where the pair disagrees strongly (cuts, particles,
// fast HUD motion) instead of smearing a ghost. Kept as literals so no new
// build-time shader step is needed; the driver compiles them once per
// context via the raw backend procs (never through ONIGAMI translation).
const char kBlendVert[] =
    "#version 300 es\n"
    "layout(location=0) in vec2 aPos;\n"
    "out vec2 vUV;\n"
    "void main(){vUV=aPos*0.5+0.5;gl_Position=vec4(aPos,0.,1.);}\n";
const char kBlendFrag[] =
    "#version 300 es\n"
    "precision mediump float;\n"
    "uniform sampler2D uPrev;\n"
    "uniform sampler2D uCurr;\n"
    "uniform float uT;\n"
    "uniform float uGuard;\n"
    "in vec2 vUV;\n"
    "out vec4 oC;\n"
    "float luma(vec4 c){return dot(c.rgb,vec3(0.299,0.587,0.114));}\n"
    "void main(){vec4 a=texture(uPrev,vUV);\n"
    "vec4 b=texture(uCurr,vUV);\n"
    "float dl=abs(luma(b)-luma(a));\n"
    "float m=mix(uT,1.0,smoothstep(0.10,0.35,dl)*uGuard);\n"
    "oC=mix(a,b,m);}\n";

// LSFG motion-stage constants, documented for the staged LK dispatch.
// NOT yet used for dispatch (see file header): kept so the upgrade path is
// explicit and reviewable instead of a magic future diff.
const int kPyramidLevels = 4;
const int kBlockSize = 8;
const int kMaxIterations = 5;
const int kMaxConsecFails = 3;

// Forward: config apply/teardown run before the GL section is defined.
void teardown_gl_locked();

std::mutex g_mu;
bool g_enabled = false;
int g_mult = 2;
bool g_guard = true;
std::string g_reason = "disabled by configuration";
Counters g_c;
long long g_prev_ns = 0;
long long g_prev2_ns = 0;
int g_w = 0, g_h = 0;
double g_interval_ema_us = 16667.0;
long long g_last_swap_ns = 0;
long long g_last_publish_ns = 0;
long long g_last_reload_ns = 0;
long long g_cfg_mtime = 0;
bool g_logged_ready = false;
bool g_logged_gen = false;
// GL present state (valid only for g_ctx; dropped on context/size change).
Gl g_gl;
bool g_gl_resolved = false;
EGLContext g_ctx = EGL_NO_CONTEXT;
GLuint g_tex[2] = {0, 0};
GLuint g_fbo = 0, g_prog = 0, g_vbo = 0, g_vao = 0;
GLint g_uPrev = -1, g_uCurr = -1, g_uT = -1, g_uGuard = -1;
int g_turn = 0;    // slot that receives the next captured frame
int g_have = 0;    // valid retained frames (0..2)
int g_confails = 0;

long long mono_ns() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (long long)ts.tv_sec * 1000000000LL + (long long)ts.tv_nsec;
}

void publish_locked() {
  long long now = mono_ns();
  if (now - g_last_publish_ns < 1000000000LL) return;
  g_last_publish_ns = now;
  std::string path = std::string(data_dir()) + "/framegen_stats.json";
  if (path == "/framegen_stats.json") return;
  std::ofstream f(path + ".tmp", std::ios::trunc);
  if (!f) return;
  int gen_ms_x100 = (int)(g_c.gen_ms_ema * 100.0);
  f << "{\"enabled\":" << (g_enabled ? 1 : 0)
    << ",\"multiplier\":" << g_mult
    << ",\"native_frames\":" << g_c.native_frames
    << ",\"displayed_frames\":" << g_c.displayed_frames
    << ",\"generated_frames\":" << g_c.generated_frames
    << ",\"dropped\":" << g_c.dropped
    << ",\"gen_ms_x100\":" << gen_ms_x100
    << ",\"reason\":\"" << g_reason << "\"}";
  f.close();
  rename((path + ".tmp").c_str(), path.c_str());
}

// Only REAL errors (GL failures, failed backend swaps) count toward the
// fail streak. Setup skips (no context/size/procs yet, warmup) never do:
// "not ready" must not disable a healthy backend.
void note_fail_locked(const char* what) {
  if (++g_confails >= kMaxConsecFails && g_enabled) {
    g_enabled = false;
    g_reason = std::string("backend error (") + what +
               "); disabled, native path intact";
    onigami::diag_write(("onigami-fg: DISABLED: " + g_reason).c_str());
  }
}

void apply_config_locked(const Config& c, const char* why) {
  bool want = c.frame_generation && c.frame_gen_multiplier >= 2 &&
              c.frame_gen_multiplier <= 4;
  int mult = c.frame_gen_multiplier < 2   ? 2
             : c.frame_gen_multiplier > 4 ? 4
                                          : c.frame_gen_multiplier;
  bool guard = c.frame_gen_guard;
  bool changed = (want != g_enabled) || (want && mult != g_mult) ||
                 (want && guard != g_guard);
  if (!changed && g_enabled) return;  // steady state: keep history, no log
  teardown_gl_locked();
  g_confails = 0;
  g_logged_ready = false;
  g_logged_gen = false;
  g_mult = mult;
  g_guard = guard;
  if (!c.frame_generation) {
    g_enabled = false;
    g_reason = "disabled by configuration";
  } else if (c.frame_gen_multiplier < 2 || c.frame_gen_multiplier > 4) {
    g_enabled = false;
    g_reason = "multiplier outside 2x..4x";
  } else {
    g_enabled = true;
    g_reason = "active";
  }
  char b[256];
  snprintf(b, sizeof(b),
           "onigami-fg: config %s dir=%s enabled=%d mult=%dx guard=%d",
           why, data_dir().c_str(), g_enabled ? 1 : 0, g_mult,
           g_guard ? 1 : 0);
  onigami::diag_write(b);
}

// Live reload: the launcher rewrites onigami.json on every FG settings
// change (no restart). Re-apply at ~1Hz when the file actually changed.
void maybe_reload_locked() {
  long long now = mono_ns();
  if (now - g_last_reload_ns < 1000000000LL) return;
  g_last_reload_ns = now;
  struct stat st;
  std::string cfg = config_path();
  if (stat(cfg.c_str(), &st) != 0) return;
  if ((long long)st.st_mtime == g_cfg_mtime) return;
  g_cfg_mtime = (long long)st.st_mtime;
  Config c = load_config();
  apply_config_locked(c, "reloaded live");
  publish_locked();
}

void drain_errors() {
  for (int i = 0; i < 8; ++i) {
    if (g_gl.GetError() == GL_NO_ERROR) break;
  }
}

bool resolve_gl_locked() {
  if (g_gl_resolved) return true;
  if (!ensure_egl_loaded() || !egl_procs().GetProcAddress) {
    return false;  // backend not up yet: skip, do not count as failure
  }
  auto get = [](const char* n) {
    return reinterpret_cast<void*>(
        egl_procs().GetProcAddress(n));  // raw backend proc, never a wrapper
  };
#define O_LSFG_NEED(fn, str)                                \
  g_gl.fn = reinterpret_cast<P_gl##fn>(get(str));           \
  if (!g_gl.fn) {                                           \
    g_reason = std::string("GLES3 proc missing: ") + str +  \
               " (ES3 context required)";                  \
    return false;                                           \
  }
  O_LSFG_NEED(GetError, "glGetError");
  O_LSFG_NEED(GetIntegerv, "glGetIntegerv");
  O_LSFG_NEED(IsEnabled, "glIsEnabled");
  O_LSFG_NEED(Enable, "glEnable");
  O_LSFG_NEED(Disable, "glDisable");
  O_LSFG_NEED(Viewport, "glViewport");
  O_LSFG_NEED(ActiveTexture, "glActiveTexture");
  O_LSFG_NEED(GenTextures, "glGenTextures");
  O_LSFG_NEED(BindTexture, "glBindTexture");
  O_LSFG_NEED(TexParameteri, "glTexParameteri");
  O_LSFG_NEED(TexImage2D, "glTexImage2D");
  O_LSFG_NEED(DeleteTextures, "glDeleteTextures");
  O_LSFG_NEED(GenFramebuffers, "glGenFramebuffers");
  O_LSFG_NEED(BindFramebuffer, "glBindFramebuffer");
  O_LSFG_NEED(FramebufferTexture2D, "glFramebufferTexture2D");
  O_LSFG_NEED(CheckFramebufferStatus, "glCheckFramebufferStatus");
  O_LSFG_NEED(DeleteFramebuffers, "glDeleteFramebuffers");
  O_LSFG_NEED(BlitFramebuffer, "glBlitFramebuffer");
  O_LSFG_NEED(GenBuffers, "glGenBuffers");
  O_LSFG_NEED(BindBuffer, "glBindBuffer");
  O_LSFG_NEED(BufferData, "glBufferData");
  O_LSFG_NEED(DeleteBuffers, "glDeleteBuffers");
  O_LSFG_NEED(GenVertexArrays, "glGenVertexArrays");
  O_LSFG_NEED(BindVertexArray, "glBindVertexArray");
  O_LSFG_NEED(DeleteVertexArrays, "glDeleteVertexArrays");
  O_LSFG_NEED(EnableVertexAttribArray, "glEnableVertexAttribArray");
  O_LSFG_NEED(VertexAttribPointer, "glVertexAttribPointer");
  O_LSFG_NEED(CreateShader, "glCreateShader");
  O_LSFG_NEED(ShaderSource, "glShaderSource");
  O_LSFG_NEED(CompileShader, "glCompileShader");
  O_LSFG_NEED(GetShaderiv, "glGetShaderiv");
  O_LSFG_NEED(GetShaderInfoLog, "glGetShaderInfoLog");
  O_LSFG_NEED(DeleteShader, "glDeleteShader");
  O_LSFG_NEED(CreateProgram, "glCreateProgram");
  O_LSFG_NEED(AttachShader, "glAttachShader");
  O_LSFG_NEED(LinkProgram, "glLinkProgram");
  O_LSFG_NEED(GetProgramiv, "glGetProgramiv");
  O_LSFG_NEED(GetProgramInfoLog, "glGetProgramInfoLog");
  O_LSFG_NEED(DeleteProgram, "glDeleteProgram");
  O_LSFG_NEED(UseProgram, "glUseProgram");
  O_LSFG_NEED(GetUniformLocation, "glGetUniformLocation");
  O_LSFG_NEED(Uniform1i, "glUniform1i");
  O_LSFG_NEED(Uniform1f, "glUniform1f");
  O_LSFG_NEED(DrawArrays, "glDrawArrays");
#undef O_LSFG_NEED
  g_gl_resolved = true;
  return true;
}

void teardown_gl_locked() {
  if (g_gl_resolved) {
    drain_errors();
    if (g_vao) g_gl.DeleteVertexArrays(1, &g_vao);
    if (g_vbo) g_gl.DeleteBuffers(1, &g_vbo);
    if (g_prog) g_gl.DeleteProgram(g_prog);
    if (g_fbo) g_gl.DeleteFramebuffers(1, &g_fbo);
    if (g_tex[0] || g_tex[1]) g_gl.DeleteTextures(2, g_tex);
    drain_errors();
  }
  g_tex[0] = g_tex[1] = 0;
  g_fbo = g_prog = g_vbo = g_vao = 0;
  g_uPrev = g_uCurr = g_uT = g_uGuard = -1;
  g_ctx = EGL_NO_CONTEXT;
  g_turn = 0;
  g_have = 0;
}

bool compile_stage(GLuint sh, const char* src, const char* tag) {
  g_gl.ShaderSource(sh, 1, &src, nullptr);
  g_gl.CompileShader(sh);
  GLint ok = 0;
  g_gl.GetShaderiv(sh, GL_COMPILE_STATUS, &ok);
  if (ok) return true;
  char log[256] = {0};
  GLsizei n = 0;
  g_gl.GetShaderInfoLog(sh, (GLsizei)sizeof(log) - 1, &n, log);
  log[sizeof(log) - 1] = 0;
  char b[300];
  snprintf(b, sizeof(b), "fg shader compile (%s): %.200s", tag, log);
  g_reason = b;
  return false;
}

bool ensure_gl_locked(EGLDisplay d, int w, int h) {
  (void)d;
  if (!resolve_gl_locked()) return false;
  EGLContext cur = egl_procs().GetCurrentContext
                       ? egl_procs().GetCurrentContext()
                       : EGL_NO_CONTEXT;
  if (cur == EGL_NO_CONTEXT) return false;  // not ready: skip silently
  if (w <= 0 || h <= 0) return false;       // size unknown: skip silently
  if (g_ctx == cur && g_w == w && g_h == h && g_prog != 0 && g_fbo != 0) {
    return true;  // hot path: everything retained and reusable
  }
  // Context or size changed: drop everything and rebuild for (cur,w,h).
  teardown_gl_locked();
  g_ctx = cur;
  g_w = w;
  g_h = h;
  drain_errors();
  g_gl.GenTextures(2, g_tex);
  for (int i = 0; i < 2; ++i) {
    g_gl.BindTexture(GL_TEXTURE_2D, g_tex[i]);
    g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, nullptr);
  }
  g_gl.BindTexture(GL_TEXTURE_2D, 0);
  g_gl.GenFramebuffers(1, &g_fbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[0], 0);
  bool fbo_ok =
      g_gl.CheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) ==
      GL_FRAMEBUFFER_COMPLETE;
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
  if (!fbo_ok || g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "retention FBO incomplete";
    teardown_gl_locked();
    return false;
  }
  GLuint vs = g_gl.CreateShader(GL_VERTEX_SHADER);
  GLuint fs = g_gl.CreateShader(GL_FRAGMENT_SHADER);
  bool sh_ok = compile_stage(vs, kBlendVert, "vert") &&
               compile_stage(fs, kBlendFrag, "frag");
  GLuint prog = 0;
  if (sh_ok) {
    prog = g_gl.CreateProgram();
    g_gl.AttachShader(prog, vs);
    g_gl.AttachShader(prog, fs);
    g_gl.LinkProgram(prog);
    GLint ok = 0;
    g_gl.GetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
      char log[256] = {0};
      GLsizei n = 0;
      g_gl.GetProgramInfoLog(prog, (GLsizei)sizeof(log) - 1, &n, log);
      log[sizeof(log) - 1] = 0;
      char b[300];
      snprintf(b, sizeof(b), "fg program link: %.200s", log);
      g_reason = b;
      g_gl.DeleteProgram(prog);
      prog = 0;
    }
  }
  g_gl.DeleteShader(vs);
  g_gl.DeleteShader(fs);
  if (prog == 0) {
    teardown_gl_locked();
    return false;
  }
  g_prog = prog;
  g_uPrev = g_gl.GetUniformLocation(g_prog, "uPrev");
  g_uCurr = g_gl.GetUniformLocation(g_prog, "uCurr");
  g_uT = g_gl.GetUniformLocation(g_prog, "uT");
  g_uGuard = g_gl.GetUniformLocation(g_prog, "uGuard");
  static const GLfloat tri[6] = {-1.f, -1.f, 3.f, -1.f, -1.f, 3.f};
  g_gl.GenBuffers(1, &g_vbo);
  g_gl.GenVertexArrays(1, &g_vao);
  g_gl.BindVertexArray(g_vao);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, g_vbo);
  g_gl.BufferData(GL_ARRAY_BUFFER, sizeof(tri), tri, GL_STATIC_DRAW);
  g_gl.EnableVertexAttribArray(0);
  g_gl.VertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
  g_gl.BindVertexArray(0);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, 0);
  if (g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "fg resource setup GL error";
    teardown_gl_locked();
    return false;
  }
  if (!g_logged_ready) {
    g_logged_ready = true;
    char b[192];
    snprintf(b, sizeof(b), "onigami-fg: GL backend ready (%dx%d, guard=%d)",
             w, h, g_guard ? 1 : 0);
    onigami::diag_write(b);
  }
  return true;
}

}  // namespace

void on_config_applied() {
  std::lock_guard<std::mutex> l(g_mu);
  struct stat st;
  std::string cfg = config_path();
  if (stat(cfg.c_str(), &st) == 0) g_cfg_mtime = (long long)st.st_mtime;
  apply_config_locked(load_config(), "init");
}

void on_surface_changed(int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  teardown_gl_locked();
  g_w = w;
  g_h = h;
  g_interval_ema_us = 16667.0;
  g_last_swap_ns = 0;
  g_prev_ns = g_prev2_ns = 0;
}

void on_surface_lost() {
  std::lock_guard<std::mutex> l(g_mu);
  teardown_gl_locked();
  g_last_swap_ns = 0;
  g_prev_ns = g_prev2_ns = 0;
}

void on_process_end() {
  std::lock_guard<std::mutex> l(g_mu);
  teardown_gl_locked();
  g_last_swap_ns = 0;
  g_prev_ns = g_prev2_ns = 0;
  g_c = Counters();
}

bool on_swap_buffers(bool swap_ok, int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  maybe_reload_locked();
  long long now = mono_ns();
  if (!swap_ok) {
    g_c.dropped++;
    return false;
  }
  g_c.native_frames++;
  g_c.displayed_frames++;
  if (g_last_swap_ns != 0) {
    double dt_us = (double)(now - g_last_swap_ns) / 1000.0;
    if (dt_us < 4000.0) dt_us = 4000.0;
    if (dt_us > 200000.0) dt_us = 200000.0;
    g_interval_ema_us = g_interval_ema_us * 0.9 + dt_us * 0.1;
  }
  g_last_swap_ns = now;
  g_prev2_ns = g_prev_ns;
  g_prev_ns = now;
  if (w > 0) g_w = w;
  if (h > 0) g_h = h;
  publish_locked();
  return false;
}

void precapture(EGLDisplay d, int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  maybe_reload_locked();
  if (!g_enabled) return;
  if (!ensure_gl_locked(d, w, h)) return;  // not ready: silent skip
  drain_errors();
  GLint save_read = 0, save_draw = 0;
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &save_read);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &save_draw);
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, 0);  // outgoing game image
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[g_turn], 0);
  g_gl.BlitFramebuffer(0, 0, g_w, g_h, 0, 0, g_w, g_h, GL_COLOR_BUFFER_BIT,
                       GL_NEAREST);
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)save_read);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)save_draw);
  if (!ok) {
    note_fail_locked("retain-blit");
    publish_locked();
    return;
  }
  g_turn ^= 1;
  if (g_have < 2) g_have++;
}

bool postgenerate(EGLDisplay d, EGLSurface s, int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  if (!g_enabled || g_have < 2) return false;  // warmup: nothing invented
  if (!ensure_gl_locked(d, w, h)) return false;
  long long t0 = mono_ns();
  drain_errors();
  // Save every game state we touch; the game must never observe us.
  GLint save_prog = 0, save_vao = 0, save_vbo = 0, save_active = GL_TEXTURE0;
  GLint save_tex0 = 0, save_tex1 = 0, save_rfbo = 0, save_dfbo = 0;
  GLint save_vp[4] = {0, 0, 0, 0};
  GLboolean save_blend = GL_FALSE, save_scissor = GL_FALSE,
            save_depth = GL_FALSE, save_stencil = GL_FALSE,
            save_cull = GL_FALSE;
  g_gl.GetIntegerv(GL_CURRENT_PROGRAM, &save_prog);
  g_gl.GetIntegerv(GL_VERTEX_ARRAY_BINDING, &save_vao);
  g_gl.GetIntegerv(GL_ARRAY_BUFFER_BINDING, &save_vbo);
  g_gl.GetIntegerv(GL_ACTIVE_TEXTURE, &save_active);
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &save_rfbo);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &save_dfbo);
  g_gl.GetIntegerv(GL_VIEWPORT, save_vp);
  g_gl.ActiveTexture(GL_TEXTURE0);
  g_gl.GetIntegerv(GL_TEXTURE_BINDING_2D, &save_tex0);
  g_gl.ActiveTexture(GL_TEXTURE1);
  g_gl.GetIntegerv(GL_TEXTURE_BINDING_2D, &save_tex1);
  save_blend = g_gl.IsEnabled(GL_BLEND);
  save_scissor = g_gl.IsEnabled(GL_SCISSOR_TEST);
  save_depth = g_gl.IsEnabled(GL_DEPTH_TEST);
  save_stencil = g_gl.IsEnabled(GL_STENCIL_TEST);
  save_cull = g_gl.IsEnabled(GL_CULL_FACE);

  GLuint older = g_tex[g_turn];      // frame N-1
  GLuint newer = g_tex[g_turn ^ 1];  // frame N
  const int n = g_mult;
  bool any_fail = false;
  int presented = 0;
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
  g_gl.Viewport(0, 0, g_w, g_h);
  g_gl.Disable(GL_BLEND);
  g_gl.Disable(GL_SCISSOR_TEST);
  g_gl.Disable(GL_DEPTH_TEST);
  g_gl.Disable(GL_STENCIL_TEST);
  g_gl.Disable(GL_CULL_FACE);
  g_gl.UseProgram(g_prog);
  g_gl.ActiveTexture(GL_TEXTURE0);
  g_gl.BindTexture(GL_TEXTURE_2D, older);
  g_gl.ActiveTexture(GL_TEXTURE1);
  g_gl.BindTexture(GL_TEXTURE_2D, newer);
  if (g_uPrev >= 0) g_gl.Uniform1i(g_uPrev, 0);
  if (g_uCurr >= 0) g_gl.Uniform1i(g_uCurr, 1);
  if (g_uGuard >= 0) g_gl.Uniform1f(g_uGuard, g_guard ? 1.0f : 0.0f);
  g_gl.BindVertexArray(g_vao);
  for (int k = 1; k < n; ++k) {
    if (g_uT >= 0) g_gl.Uniform1f(g_uT, (float)k / (float)n);
    g_gl.DrawArrays(GL_TRIANGLES, 0, 3);
    // Direct backend swap: bypasses our own wrapper (no recursion, and the
    // wrapper pacing log intentionally keeps counting native swaps only).
    EGLBoolean ok2 = egl_procs().SwapBuffers
                         ? egl_procs().SwapBuffers(d, s)
                         : EGL_FALSE;
    if (ok2 == EGL_TRUE && g_gl.GetError() == GL_NO_ERROR) {
      presented++;
    } else {
      any_fail = true;
      break;  // stop the chain: never present past a failure
    }
  }

  // Restore game state before counting anything.
  g_gl.ActiveTexture(GL_TEXTURE0);
  g_gl.BindTexture(GL_TEXTURE_2D, (GLuint)save_tex0);
  g_gl.ActiveTexture(GL_TEXTURE1);
  g_gl.BindTexture(GL_TEXTURE_2D, (GLuint)save_tex1);
  g_gl.ActiveTexture((GLenum)save_active);
  g_gl.BindVertexArray((GLuint)save_vao);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, (GLuint)save_vbo);
  g_gl.UseProgram((GLuint)save_prog);
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)save_rfbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)save_dfbo);
  g_gl.Viewport(save_vp[0], save_vp[1], save_vp[2], save_vp[3]);
  if (save_blend) g_gl.Enable(GL_BLEND);
  if (save_scissor) g_gl.Enable(GL_SCISSOR_TEST);
  if (save_depth) g_gl.Enable(GL_DEPTH_TEST);
  if (save_stencil) g_gl.Enable(GL_STENCIL_TEST);
  if (save_cull) g_gl.Enable(GL_CULL_FACE);

  long long t1 = mono_ns();
  double ms = (double)(t1 - t0) / 1000000.0;
  g_c.gen_ms_ema = g_c.gen_ms_ema == 0.0 ? ms : g_c.gen_ms_ema * 0.9 + ms * 0.1;
  if (presented > 0) {
    g_c.generated_frames += (unsigned long long)presented;
    g_c.displayed_frames += (unsigned long long)presented;
    g_confails = 0;
    if (!g_logged_gen) {
      g_logged_gen = true;
      char b[192];
      snprintf(b, sizeof(b),
               "onigami-fg: generated frames live (%dx, +%d/frame)",
               n, n - 1);
      onigami::diag_write(b);
    }
  }
  if (any_fail) {
    g_c.dropped++;
    note_fail_locked("present");
  }
  publish_locked();
  (void)kPyramidLevels;
  (void)kBlockSize;
  (void)kMaxIterations;
  return presented > 0;
}

bool supported() { return true; }
bool enabled() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_enabled;
}
int multiplier() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_mult;
}
std::string status_text() {
  std::lock_guard<std::mutex> l(g_mu);
  if (!g_enabled) return std::string("Frame Generation: OFF (") + g_reason + ")";
  char b[192];
  snprintf(b, sizeof(b), "Frame Generation: ON (%dx, gen %.2fms)", g_mult,
           g_c.gen_ms_ema);
  return std::string(b);
}
Counters counters() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_c;
}
std::string last_reason() {
  std::lock_guard<std::mutex> l(g_mu);
  return g_reason;
}

}  // namespace lsfg
}  // namespace onigami

extern "C" {
#include "onigami/onigami.h"
ONIGAMI_API int onigami_framegen_supported(void) {
  return onigami::lsfg::supported() ? 1 : 0;
}
ONIGAMI_API int onigami_framegen_enabled(void) {
  return onigami::lsfg::enabled() ? 1 : 0;
}
ONIGAMI_API int onigami_framegen_multiplier(void) {
  return onigami::lsfg::multiplier();
}
ONIGAMI_API const char* onigami_framegen_status(void) {
  static std::string s;
  s = onigami::lsfg::status_text();
  return s.c_str();
}
ONIGAMI_API unsigned long long onigami_native_frames(void) {
  return onigami::lsfg::counters().native_frames;
}
ONIGAMI_API unsigned long long onigami_displayed_frames(void) {
  return onigami::lsfg::counters().displayed_frames;
}
ONIGAMI_API unsigned long long onigami_generated_frames(void) {
  return onigami::lsfg::counters().generated_frames;
}
ONIGAMI_API unsigned long long onigami_dropped_frames(void) {
  return onigami::lsfg::counters().dropped;
}
// Bridge present hook (see lsfg.h): the JNI bridge calls this around its
// backend swap so frame generation rides the REAL present path, which
// resolves system EGL directly and never passes libonigami's own wrapper.
ONIGAMI_API EGLBoolean onigami_hook_swap_buffers(
    EGLDisplay d, EGLSurface s,
    EGLBoolean (*real_swap)(EGLDisplay, EGLSurface)) {
  // Self-healing lifecycle: nothing in the launcher currently calls
  // onigami_on_process_start, which previously left the whole native config
  // (render scale, vault, coalescing, frame generation) dormant at defaults.
  // Run it once here so configured values actually take effect; data_dir()
  // falls back to the ONIGAMI_DATA_DIR env the launcher already exports.
  static std::once_flag hook_once;
  std::call_once(hook_once, []() {
    onigami_on_process_start(nullptr);
    struct stat st;
    std::string cfg = onigami::config_path();
    if (stat(cfg.c_str(), &st) != 0) {
      char b[256];
      snprintf(b, sizeof(b),
               "onigami-fg: bridge hook active, dir=%s (onigami.json absent: counting presents, FG off until configured)",
               onigami::data_dir().c_str());
      onigami::diag_write(b);
    } else {
      char b[192];
      snprintf(b, sizeof(b), "onigami-fg: bridge hook active, dir=%s",
               onigami::data_dir().c_str());
      onigami::diag_write(b);
    }
  });
  int w = 0, h = 0;
  if (onigami::ensure_egl_loaded() && onigami::egl_procs().QuerySurface) {
    EGLint ew = 0, eh = 0;
    if (onigami::egl_procs().QuerySurface(d, s, EGL_WIDTH, &ew)) w = (int)ew;
    if (onigami::egl_procs().QuerySurface(d, s, EGL_HEIGHT, &eh)) h = (int)eh;
  }
  onigami::lsfg::precapture(d, w, h);
  if (!real_swap) return EGL_FALSE;
  EGLBoolean ok = real_swap(d, s);
  onigami::lsfg::on_swap_buffers(ok == EGL_TRUE, w, h);
  if (ok == EGL_TRUE) onigami::lsfg::postgenerate(d, s, w, h);
  return ok;
}
}  // extern "C"
