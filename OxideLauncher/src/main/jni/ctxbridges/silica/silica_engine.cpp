// Silica own-FG engine, linked into libpojavexec (v1.19.0+ rewrite).
//
// Clean-room GLES 3.1 compute: quarter-res luma -> 8x8 SAD block-match
// (+refine) -> 3x3 median -> motion-warped blend at t=k/N with
// disocclusion guard. No third-party FG code. Called directly by the
// bridge (linked, never dlopened); config pulled over JNI from
// game-process singletons; stats to the same dir string. Default off.
#include "silica/silica_engine.h"

#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <jni.h>

#include <cstdio>
#include <ctime>
#include <fstream>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <dlfcn.h>

namespace silica_engine {
namespace {

#define SFG_FN(ret, name, ...) typedef ret (*P_##name)(__VA_ARGS__)
SFG_FN(GLenum, glGetError, void);
SFG_FN(void, glGetIntegerv, GLenum pname, GLint* data);
SFG_FN(const GLubyte*, glGetString, GLenum name);
SFG_FN(GLboolean, glIsEnabled, GLenum cap);
SFG_FN(void, glEnable, GLenum cap);
SFG_FN(void, glDisable, GLenum cap);
SFG_FN(void, glViewport, GLint x, GLint y, GLsizei w, GLsizei h);
SFG_FN(void, glActiveTexture, GLenum texture);
SFG_FN(void, glGenTextures, GLsizei n, GLuint* t);
SFG_FN(void, glBindTexture, GLenum target, GLuint t);
SFG_FN(void, glTexParameteri, GLenum target, GLenum pname, GLint p);
SFG_FN(void, glTexImage2D, GLenum t, GLint l, GLint internal, GLsizei w,
        GLsizei h, GLint b, GLenum fmt, GLenum ty, const void* p);
SFG_FN(void, glDeleteTextures, GLsizei n, const GLuint* t);
SFG_FN(void, glBindImageTexture, GLuint u, GLuint t, GLint l, GLboolean ly,
        GLint lr, GLenum a, GLenum f);
SFG_FN(void, glGenSamplers, GLsizei n, GLuint* s);
SFG_FN(void, glSamplerParameteri, GLuint s, GLenum p, GLint v);
SFG_FN(void, glBindSampler, GLuint u, GLuint s);
SFG_FN(void, glDeleteSamplers, GLsizei n, const GLuint* s);
SFG_FN(void, glGenFramebuffers, GLsizei n, GLuint* ids);
SFG_FN(void, glBindFramebuffer, GLenum target, GLuint f);
SFG_FN(void, glFramebufferTexture2D, GLenum target, GLenum attachment,
        GLenum textarget, GLuint texture, GLint level);
SFG_FN(GLenum, glCheckFramebufferStatus, GLenum target);
SFG_FN(void, glDeleteFramebuffers, GLsizei n, const GLuint* ids);
SFG_FN(void, glBlitFramebuffer, GLint sx0, GLint sy0, GLint sx1, GLint sy1,
        GLint dx0, GLint dy0, GLint dx1, GLint dy1, GLbitfield mask,
        GLenum filter);
SFG_FN(void, glGenBuffers, GLsizei n, GLuint* buffers);
SFG_FN(void, glBindBuffer, GLenum target, GLuint buffer);
SFG_FN(void, glBufferData, GLenum target, GLsizeiptr size, const void* data,
        GLenum usage);
SFG_FN(void, glDeleteBuffers, GLsizei n, const GLuint* buffers);
SFG_FN(void, glGenVertexArrays, GLsizei n, GLuint* arrays);
SFG_FN(void, glBindVertexArray, GLuint array);
SFG_FN(void, glDeleteVertexArrays, GLsizei n, const GLuint* arrays);
SFG_FN(void, glEnableVertexAttribArray, GLuint index);
SFG_FN(void, glVertexAttribPointer, GLuint index, GLint size, GLenum type,
        GLboolean normalized, GLsizei stride, const void* pointer);
SFG_FN(GLuint, glCreateShader, GLenum type);
SFG_FN(void, glShaderSource, GLuint shader, GLsizei count,
        const GLchar* const* string, const GLint* length);
SFG_FN(void, glCompileShader, GLuint shader);
SFG_FN(void, glGetShaderiv, GLuint shader, GLenum pname, GLint* params);
SFG_FN(void, glGetShaderInfoLog, GLuint shader, GLsizei bufsize,
        GLsizei* length, GLchar* infolog);
SFG_FN(void, glDeleteShader, GLuint shader);
SFG_FN(GLuint, glCreateProgram, void);
SFG_FN(void, glAttachShader, GLuint program, GLuint shader);
SFG_FN(void, glLinkProgram, GLuint program);
SFG_FN(void, glGetProgramiv, GLuint program, GLenum pname, GLint* params);
SFG_FN(void, glGetProgramInfoLog, GLuint program, GLsizei bufsize,
        GLsizei* length, GLchar* infolog);
SFG_FN(void, glDeleteProgram, GLuint program);
SFG_FN(void, glUseProgram, GLuint program);
SFG_FN(GLint, glGetUniformLocation, GLuint program, const GLchar* name);
SFG_FN(void, glUniform1i, GLint location, GLint v0);
SFG_FN(void, glUniform1f, GLint location, GLfloat v0);
SFG_FN(void, glUniform2i, GLint location, GLint v0, GLint v1);
SFG_FN(void, glDispatchCompute, GLuint x, GLuint y, GLuint z);
SFG_FN(void, glMemoryBarrier, GLbitfield b);
SFG_FN(void, glDrawArrays, GLenum mode, GLint first, GLsizei count);
#undef SFG_FN
typedef EGLBoolean (*P_eglSwapBuffers)(EGLDisplay, EGLSurface);
typedef EGLContext (*P_eglGetCurrentContext)(void);
typedef EGLBoolean (*P_eglQuerySurface)(EGLDisplay, EGLSurface, EGLint, EGLint*);

struct Gl {
  P_glGetError GetError = nullptr;
  P_glGetIntegerv GetIntegerv = nullptr;
  P_glGetString GetString = nullptr;
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
  P_glBindImageTexture BindImageTexture = nullptr;
  P_glGenSamplers GenSamplers = nullptr;
  P_glSamplerParameteri SamplerParameteri = nullptr;
  P_glBindSampler BindSampler = nullptr;
  P_glDeleteSamplers DeleteSamplers = nullptr;
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
  P_glUniform2i Uniform2i = nullptr;
  P_glDispatchCompute DispatchCompute = nullptr;
  P_glMemoryBarrier MemoryBarrier = nullptr;
  P_glDrawArrays DrawArrays = nullptr;
  P_eglSwapBuffers SwapBuffers = nullptr;
  P_eglGetCurrentContext GetCurrentContext = nullptr;
  P_eglQuerySurface QuerySurface = nullptr;
};

const char kBox[] =
    "#version 310 es\n"
    "layout(local_size_x=8, local_size_y=8) in;\n"
    "layout(binding=0, rgba8) uniform readonly highp image2D uSrc;\n"
    "layout(binding=3, r8) uniform writeonly highp image2D uDst;\n"
    "uniform ivec2 uDstSize;\n"
    "void main(){\n"
    "ivec2 p=ivec2(gl_GlobalInvocationID.xy);\n"
    "if(p.x>=uDstSize.x||p.y>=uDstSize.y) return;\n"
    "ivec2 b=p*4;\n"
    "float s=0.0;\n"
    "for(int y=0;y<4;y++)for(int x=0;x<4;x++){\n"
    "vec4 c=imageLoad(uSrc,clamp(b+ivec2(x,y),ivec2(0),uDstSize*4-1));\n"
    "s+=dot(c.rgb,vec3(0.299,0.587,0.114));}\n"
    "imageStore(uDst,p,vec4(s/16.0,0.0,0.0,1.0));}\n";
const char kFlow[] =
    "#version 310 es\n"
    "layout(local_size_x=8, local_size_y=8) in;\n"
    "layout(binding=2, r8) uniform readonly highp image2D uPrev;\n"
    "layout(binding=3, r8) uniform readonly highp image2D uCurr;\n"
    "layout(binding=4, rg16f) uniform writeonly highp image2D uFlow;\n"
    "uniform ivec2 uSize;\n"
    "uniform ivec2 uFlowSize;\n"
    "float px(ivec2 p, int w){\n"
    "p=clamp(p,ivec2(0),uSize-1);\n"
    "return (w==0?imageLoad(uPrev,p):imageLoad(uCurr,p)).r;}\n"
    "void main(){\n"
    "ivec2 bp=ivec2(gl_GlobalInvocationID.xy)*8;\n"
    "ivec2 op=bp/8;\n"
    "if(op.x>=uFlowSize.x||op.y>=uFlowSize.y) return;\n"
    "float best=1e9; ivec2 bv=ivec2(0);\n"
    "for(int dy=-8;dy<=8;dy+=2)for(int dx=-8;dx<=8;dx+=2){\n"
    "float sad=0.0;\n"
    "for(int y=0;y<8;y+=2)for(int x=0;x<8;x+=2)\n"
    "sad+=abs(px(bp+ivec2(x,y),0)-px(bp+ivec2(x+dx,y+dy),1));\n"
    "if(sad<best){best=sad;bv=ivec2(dx,dy);}}\n"
    "for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){\n"
    "float sad=0.0;\n"
    "for(int y=0;y<8;y+=2)for(int x=0;x<8;x+=2)\n"
    "sad+=abs(px(bp+ivec2(x,y),0)-px(bp+ivec2(x+dx+bv.x,y+dy+bv.y),1));\n"
    "if(sad<best){best=sad;bv+=ivec2(dx,dy);}}\n"
    "imageStore(uFlow,op,vec4(vec2(bv),0.0,1.0));}\n";
const char kMedian[] =
    "#version 310 es\n"
    "layout(local_size_x=8, local_size_y=8) in;\n"
    "layout(binding=4, rg16f) uniform readonly highp image2D uSrc;\n"
    "layout(binding=5, rg16f) uniform writeonly highp image2D uDst;\n"
    "uniform ivec2 uSize;\n"
    "float med9(float a0,float a1,float a2,float a3,float a4,float a5,float a6,float a7,float a8){\n"
    "float v[9];v[0]=a0;v[1]=a1;v[2]=a2;v[3]=a3;v[4]=a4;v[5]=a5;v[6]=a6;v[7]=a7;v[8]=a8;\n"
    "for(int i=0;i<9;i++)for(int j=i+1;j<9;j++)if(v[j]<v[i]){float t=v[i];v[i]=v[j];v[j]=t;}\n"
    "return v[4];}\n"
    "void main(){\n"
    "ivec2 p=ivec2(gl_GlobalInvocationID.xy);\n"
    "if(p.x>=uSize.x||p.y>=uSize.y) return;\n"
    "float xs[9]; float ys[9]; int k=0;\n"
    "for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){\n"
    "vec2 f=imageLoad(uSrc,clamp(p+ivec2(dx,dy),ivec2(0),uSize-1)).rg;\n"
    "xs[k]=f.x; ys[k]=f.y; k++;}\n"
    "imageStore(uDst,p,vec4(med9(xs[0],xs[1],xs[2],xs[3],xs[4],xs[5],xs[6],xs[7],xs[8]),med9(ys[0],ys[1],ys[2],ys[3],ys[4],ys[5],ys[6],ys[7],ys[8]),0.0,1.0));}\n";
const char kWarpVert[] =
    "#version 300 es\n"
    "layout(location=0) in vec2 aPos;\n"
    "out vec2 vUV;\n"
    "void main(){vUV=aPos*0.5+0.5;gl_Position=vec4(aPos,0.,1.);}\n";
const char kWarpFrag[] =
    "#version 300 es\n"
    "precision highp float;\n"
    "uniform sampler2D uPrev;\n"
    "uniform sampler2D uCurr;\n"
    "uniform sampler2D uFlow;\n"
    "uniform vec2 uPx2Uv;\n"
    "uniform float uT;\n"
    "uniform float uGuard;\n"
    "in vec2 vUV;\n"
    "out vec4 oC;\n"
    "void main(){\n"
    "vec2 f=texture(uFlow,vUV).rg;\n"
    "vec2 o=f*uPx2Uv;\n"
    "vec4 a=texture(uPrev,vUV-o*uT);\n"
    "vec4 b=texture(uCurr,vUV+o*(1.0-uT));\n"
    "float dl=abs(dot(b.rgb-a.rgb,vec3(0.3333)));\n"
    "float m=mix(uT,1.0,smoothstep(0.10,0.35,dl)*uGuard);\n"
    "oC=mix(a,b,m);}\n";

std::mutex g_mu;
bool g_enabled = false;
int g_mult = 2;
std::string g_dataDir;
std::string g_reason = "idle (awaiting bind)";
unsigned long long g_native = 0, g_displayed = 0, g_gen = 0, g_drop = 0;
double g_genMs = 0.0, g_nativeDtMs = 0.0;
long long g_lastSwapNs = 0, g_lastPublishNs = 0, g_lastCfgNs = 0;
bool g_loggedReady = false, g_loggedGen = false;

Gl g_gl;
bool g_resolved = false;
EGLContext g_ctx = EGL_NO_CONTEXT;
int g_w = 0, g_h = 0, g_qw = 0, g_qh = 0, g_fw = 0, g_fh = 0;
GLuint g_tex[2] = {0, 0};
GLuint g_luma[2] = {0, 0};
GLuint g_flow[2] = {0, 0};
GLuint g_fbo = 0, g_vbo = 0, g_vao = 0, g_samp = 0, g_sampFlow = 0;
GLuint g_pBox = 0, g_pFlow = 0, g_pMed = 0, g_pWarp = 0;
GLint g_boxSize = -1, g_flowSize = -1, g_flowGrid = -1, g_medSize = -1;
GLint g_wGuard = -1, g_wT = -1;
int g_turn = 0, g_have = 0, g_confails = 0;
const int kMaxFails = 3;

// ---- JNI config pull (cached; refreshed ~1Hz from the swap path) ----
static JavaVM* g_vm = nullptr;
static jclass g_bridgeClass = nullptr;
static jmethodID g_mDir = nullptr, g_mEn = nullptr, g_mMult = nullptr;
static std::string g_cfgDir;
static bool g_cfgEn = false;
static int g_cfgMult = 2;

static JNIEnv* jni_env() {
  if (!g_vm) return nullptr;
  JNIEnv* e = nullptr;
  if (g_vm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6) != JNI_OK)
    return nullptr;
  return e;
}

static void pull_config_locked() {
  long long now = mono_ns();
  if (now - g_lastCfgNs < 1000000000LL || !g_bridgeClass) return;
  g_lastCfgNs = now;
  JNIEnv* e = jni_env();
  if (!e) return;  // render thread detached: keep last config
  if (e->ExceptionCheck()) e->ExceptionClear();
  jstring d = (jstring)e->CallStaticObjectMethod(g_bridgeClass, g_mDir);
  bool en = e->CallStaticBooleanMethod(g_bridgeClass, g_mEn) == JNI_TRUE;
  int mult = e->CallStaticIntMethod(g_bridgeClass, g_mMult);
  if (e->ExceptionCheck()) {
    e->ExceptionClear();
    return;  // keep last known-good config
  }
  std::string dir;
  if (d) {
    const char* c = e->GetStringUTFChars(d, nullptr);
    if (c) dir = c;
    e->ReleaseStringUTFChars(d, c);
    e->DeleteLocalRef(d);
  }
  bool want = en && mult >= 2 && mult <= 4;
  if (want != g_enabled || (want && mult != g_mult) ||
      (!g_dataDir.empty() && dir != g_dataDir && !dir.empty())) {
    teardown_gl_locked();
    g_confails = 0;
    g_loggedReady = false;
    g_loggedGen = false;
  }
  if (!dir.empty()) g_dataDir = dir;
  g_enabled = want;
  g_mult = mult < 2 ? 2 : (mult > 4 ? 4 : mult);
  g_reason = want ? "active" : "disabled by configuration";
}

long long mono_ns() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (long long)ts.tv_sec * 1000000000LL + (long long)ts.tv_nsec;
}

void diag(const char* m) {
  std::string fp = g_dataDir.empty() ? "/data/local/tmp/silica-fg.log"
                                     : g_dataDir + "/silica-fg.log";
  if (FILE* f = fopen(fp.c_str(), "a")) {
    fprintf(f, "%s\n", m);
    fclose(f);
  }
  __android_log_print(ANDROID_LOG_INFO, "silica-fg", "%s", m);
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
    << ",\"gen_ms_x100\":" << (int)(g_genMs * 100.0)
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
  typedef void* (*getproc_fn)(const char*);
  getproc_fn pa = reinterpret_cast<getproc_fn>(dlsym(eglH, "eglGetProcAddress"));
  auto get = [&](const char* n) -> void* {
    void* p = nullptr;
    if (pa) p = pa(n);
    if (!p) p = dlsym(eglH, n);
    return p;
  };
#define SFG_NEED(fn, str)                           \
  g_gl.fn = reinterpret_cast<P_##fn>(get(str));     \
  if (!g_gl.fn) {                                   \
    g_reason = std::string("GLES proc missing: ") + str; \
    return false;                                   \
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
  SFG_NEED(Uniform2i, "glUniform2i");
  SFG_NEED(DispatchCompute, "glDispatchCompute");
  SFG_NEED(MemoryBarrier, "glMemoryBarrier");
  SFG_NEED(DrawArrays, "glDrawArrays");
#undef SFG_NEED
  SFG_NEED_EGL(GetCurrentContext, "eglGetCurrentContext");
  SFG_NEED_EGL(QuerySurface, "eglQuerySurface");
  SFG_NEED_EGL(SwapBuffers, "eglSwapBuffers");
  g_resolved = true;
  return true;
}

// __ENGINE_PART3__

