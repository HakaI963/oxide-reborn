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
typedef EGLBoolean (*P_SwapBuffers)(EGLDisplay, EGLSurface);
typedef EGLContext (*P_GetCurrentContext)(void);
typedef EGLBoolean (*P_QuerySurface)(EGLDisplay, EGLSurface, EGLint, EGLint*);

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
  P_SwapBuffers SwapBuffers = nullptr;
  P_GetCurrentContext GetCurrentContext = nullptr;
  P_QuerySurface QuerySurface = nullptr;
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

void teardown_gl_locked();

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
  SFG_NEED(GetCurrentContext, "eglGetCurrentContext");
  SFG_NEED(QuerySurface, "eglQuerySurface");
  SFG_NEED(SwapBuffers, "eglSwapBuffers");
  g_resolved = true;
  return true;
}

void teardown_gl_locked() {
  if (g_vao) g_gl.DeleteVertexArrays(1, &g_vao);
  if (g_vbo) g_gl.DeleteBuffers(1, &g_vbo);
  GLuint progs[4] = {g_pBox, g_pFlow, g_pMed, g_pWarp};
  for (int i = 0; i < 4; ++i)
    if (progs[i]) g_gl.DeleteProgram(progs[i]);
  if (g_fbo) g_gl.DeleteFramebuffers(1, &g_fbo);
  if (g_samp) g_gl.DeleteSamplers(1, &g_samp);
  if (g_sampFlow) g_gl.DeleteSamplers(1, &g_sampFlow);
  GLuint texs[6] = {g_tex[0], g_tex[1], g_luma[0], g_luma[1], g_flow[0],
                    g_flow[1]};
  if (g_resolved) {
    drain_errors();
    g_gl.DeleteTextures(6, texs);
    drain_errors();
  }
  g_tex[0] = g_tex[1] = g_luma[0] = g_luma[1] = g_flow[0] = g_flow[1] = 0;
  g_fbo = g_vbo = g_vao = g_samp = g_sampFlow = 0;
  g_pBox = g_pFlow = g_pMed = g_pWarp = 0;
  g_boxSize = g_flowSize = g_flowGrid = g_medSize = -1;
  g_wGuard = g_wT = -1;
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
  snprintf(b, sizeof(b), "shader compile (%s): %.200s", tag, log);
  g_reason = b;
  return false;
}

GLuint link_compute(const char* src, const char* tag) {
  GLuint sh = g_gl.CreateShader(GL_COMPUTE_SHADER);
  if (!compile_stage(sh, src, tag)) {
    g_gl.DeleteShader(sh);
    return 0;
  }
  GLuint prog = g_gl.CreateProgram();
  g_gl.AttachShader(prog, sh);
  g_gl.LinkProgram(prog);
  g_gl.DeleteShader(sh);
  GLint st = 0;
  g_gl.GetProgramiv(prog, GL_LINK_STATUS, &st);
  if (!st) {
    char log[256] = {0};
    GLsizei n = 0;
    g_gl.GetProgramInfoLog(prog, (GLsizei)sizeof(log) - 1, &n, log);
    log[sizeof(log) - 1] = 0;
    char b[300];
    snprintf(b, sizeof(b), "program link (%s): %.200s", tag, log);
    g_reason = b;
    g_gl.DeleteProgram(prog);
    return 0;
  }
  return prog;
}

GLuint link_raster(const char* vsrc, const char* fsrc, const char* tag) {
  GLuint vs = g_gl.CreateShader(GL_VERTEX_SHADER);
  GLuint fs = g_gl.CreateShader(GL_FRAGMENT_SHADER);
  bool ok = compile_stage(vs, vsrc, tag) && compile_stage(fs, fsrc, tag);
  GLuint prog = 0;
  if (ok) {
    prog = g_gl.CreateProgram();
    g_gl.AttachShader(prog, vs);
    g_gl.AttachShader(prog, fs);
    g_gl.LinkProgram(prog);
    GLint st = 0;
    g_gl.GetProgramiv(prog, GL_LINK_STATUS, &st);
    if (!st) {
      char log[256] = {0};
      GLsizei n = 0;
      g_gl.GetProgramInfoLog(prog, (GLsizei)sizeof(log) - 1, &n, log);
      log[sizeof(log) - 1] = 0;
      char b[300];
      snprintf(b, sizeof(b), "program link (%s): %.200s", tag, log);
      g_reason = b;
      g_gl.DeleteProgram(prog);
      prog = 0;
    }
  }
  g_gl.DeleteShader(vs);
  g_gl.DeleteShader(fs);
  return prog;
}

static void tex_rgba(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA,
                  GL_UNSIGNED_BYTE, nullptr);
}

static void tex_r8(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE,
                  nullptr);
}

static void tex_rg16f(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, w, h, 0, GL_RG, GL_HALF_FLOAT,
                  nullptr);
}

bool ensure_gl_locked(int w, int h) {
  if (!resolve_gl_locked()) return false;
  EGLContext cur =
      g_gl.GetCurrentContext ? g_gl.GetCurrentContext() : EGL_NO_CONTEXT;
  if (cur == EGL_NO_CONTEXT) return false;
  if (w <= 0 || h <= 0) return false;
  if (g_ctx == cur && g_w == w && g_h == h && g_pWarp != 0) return true;
  teardown_gl_locked();
  const GLubyte* ver = g_gl.GetString(GL_VERSION);
  if (!ver) return false;
  int major = 0, minor = 0;
  sscanf((const char*)ver, "OpenGL ES %d.%d", &major, &minor);
  if (major < 3 || (major == 3 && minor < 1)) {
    g_reason = "GLES 3.1+ required for compute flow";
    return false;
  }
  g_ctx = cur;
  g_w = w;
  g_h = h;
  g_qw = (w + 3) / 4;
  g_qh = (h + 3) / 4;
  g_fw = (g_qw + 7) / 8;
  g_fh = (g_qh + 7) / 8;
  drain_errors();
  g_gl.GenTextures(2, g_tex);
  for (int i = 0; i < 2; ++i) tex_rgba(g_tex[i], w, h);
  g_gl.GenTextures(2, g_luma);
  for (int i = 0; i < 2; ++i) tex_r8(g_luma[i], g_qw, g_qh);
  g_gl.GenTextures(2, g_flow);
  for (int i = 0; i < 2; ++i) tex_rg16f(g_flow[i], g_fw, g_fh);
  g_gl.BindTexture(GL_TEXTURE_2D, 0);
  g_gl.GenFramebuffers(1, &g_fbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[0], 0);
  bool fboOk = g_gl.CheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) ==
               GL_FRAMEBUFFER_COMPLETE;
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
  if (!fboOk || g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "retention FBO incomplete";
    teardown_gl_locked();
    return false;
  }
  g_pBox = link_compute(kBox, "box");
  g_pFlow = link_compute(kFlow, "flow");
  g_pMed = link_compute(kMedian, "med");
  g_pWarp = link_raster(kWarpVert, kWarpFrag, "warp");
  if (!g_pBox || !g_pFlow || !g_pMed || !g_pWarp) {
    teardown_gl_locked();
    return false;
  }
  g_boxSize = g_gl.GetUniformLocation(g_pBox, "uDstSize");
  g_flowSize = g_gl.GetUniformLocation(g_pFlow, "uSize");
  g_flowGrid = g_gl.GetUniformLocation(g_pFlow, "uFlowSize");
  g_medSize = g_gl.GetUniformLocation(g_pMed, "uSize");
  g_wGuard = g_gl.GetUniformLocation(g_pWarp, "uGuard");
  g_wT = g_gl.GetUniformLocation(g_pWarp, "uT");
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
  g_gl.GenSamplers(1, &g_samp);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.GenSamplers(1, &g_sampFlow);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.UseProgram(g_pWarp);
  GLint uP = g_gl.GetUniformLocation(g_pWarp, "uPrev");
  GLint uC = g_gl.GetUniformLocation(g_pWarp, "uCurr");
  GLint uF = g_gl.GetUniformLocation(g_pWarp, "uFlow");
  GLint uPx = g_gl.GetUniformLocation(g_pWarp, "uPx2Uv");
  if (uP >= 0) g_gl.Uniform1i(uP, 0);
  if (uC >= 0) g_gl.Uniform1i(uC, 1);
  if (uF >= 0) g_gl.Uniform1i(uF, 2);
  if (uPx >= 0) g_gl.Uniform2f(uPx, 4.0f / (float)w, 4.0f / (float)h);
  g_gl.UseProgram(0);
  if (g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "resource setup GL error";
    teardown_gl_locked();
    return false;
  }
  if (!g_loggedReady) {
    g_loggedReady = true;
    char b[192];
    snprintf(b, sizeof(b), "silica-fg: engine ready (%dx%d, flow %dx%d)", w, h,
             g_fw, g_fh);
    diag(b);
  }
  return true;
}

static void unbind_images() {
  for (GLuint u = 0; u < 8; ++u)
    g_gl.BindImageTexture(u, 0, 0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
}

static bool pass_box(GLuint srcTex, GLuint dstTex) {
  g_gl.BindImageTexture(0, srcTex, 0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
  g_gl.BindImageTexture(3, dstTex, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_R8);
  g_gl.UseProgram(g_pBox);
  if (g_boxSize >= 0) g_gl.Uniform2i(g_boxSize, g_qw, g_qh);
  g_gl.DispatchCompute((GLuint)((g_qw + 7) / 8), (GLuint)((g_qh + 7) / 8), 1);
  g_gl.MemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.UseProgram(0);
  return ok;
}

static bool pass_flow() {
  g_gl.BindImageTexture(2, g_luma[g_turn], 0, GL_FALSE, 0, GL_READ_ONLY, GL_R8);
  g_gl.BindImageTexture(3, g_luma[g_turn ^ 1], 0, GL_FALSE, 0, GL_READ_ONLY,
                        GL_R8);
  g_gl.BindImageTexture(4, g_flow[0], 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RG16F);
  g_gl.UseProgram(g_pFlow);
  if (g_flowSize >= 0) g_gl.Uniform2i(g_flowSize, g_qw, g_qh);
  if (g_flowGrid >= 0) g_gl.Uniform2i(g_flowGrid, g_fw, g_fh);
  g_gl.DispatchCompute((GLuint)((g_fw + 7) / 8), (GLuint)((g_fh + 7) / 8), 1);
  g_gl.MemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.UseProgram(0);
  return ok;
}

static bool pass_median() {
  g_gl.BindImageTexture(4, g_flow[0], 0, GL_FALSE, 0, GL_READ_ONLY, GL_RG16F);
  g_gl.BindImageTexture(5, g_flow[1], 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RG16F);
  g_gl.UseProgram(g_pMed);
  if (g_medSize >= 0) g_gl.Uniform2i(g_medSize, g_fw, g_fh);
  g_gl.DispatchCompute((GLuint)((g_fw + 7) / 8), (GLuint)((g_fh + 7) / 8), 1);
  g_gl.MemoryBarrier(
      (GLbitfield)(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                   GL_TEXTURE_FETCH_BARRIER_BIT));
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.UseProgram(0);
  return ok;
}

static bool run_flow_locked() {
  if (!pass_flow()) return false;
  if (!pass_median()) return false;
  unbind_images();
  return true;
}

struct SavedState {
  GLint prog = 0, vao = 0, vbo = 0, active = GL_TEXTURE0;
  GLint vp[4] = {0, 0, 0, 0};
  GLint rfbo = 0, dfbo = 0;
  GLint tex[3] = {0, 0, 0};
  GLint samp[3] = {0, 0, 0};
  GLboolean en[5] = {GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE};
  GLint imgName[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLevel[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLayered[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLayer[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgAccess[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgFormat[8] = {0, 0, 0, 0, 0, 0, 0, 0};
};

static void save_state(SavedState& s) {
  g_gl.GetIntegerv(GL_CURRENT_PROGRAM, &s.prog);
  g_gl.GetIntegerv(GL_VERTEX_ARRAY_BINDING, &s.vao);
  g_gl.GetIntegerv(GL_ARRAY_BUFFER_BINDING, &s.vbo);
  g_gl.GetIntegerv(GL_ACTIVE_TEXTURE, &s.active);
  g_gl.GetIntegerv(GL_VIEWPORT, s.vp);
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &s.rfbo);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &s.dfbo);
  for (int i = 0; i < 3; ++i) {
    g_gl.ActiveTexture((GLenum)(GL_TEXTURE0 + i));
    g_gl.GetIntegerv(GL_TEXTURE_BINDING_2D, &s.tex[i]);
    g_gl.GetIntegerv(GL_SAMPLER_BINDING, &s.samp[i]);
  }
  s.en[0] = g_gl.IsEnabled(GL_BLEND);
  s.en[1] = g_gl.IsEnabled(GL_SCISSOR_TEST);
  s.en[2] = g_gl.IsEnabled(GL_DEPTH_TEST);
  s.en[3] = g_gl.IsEnabled(GL_STENCIL_TEST);
  s.en[4] = g_gl.IsEnabled(GL_CULL_FACE);
  for (int i = 0; i < 8; ++i) {
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_NAME + i), &s.imgName[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LEVEL + i), &s.imgLevel[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LAYERED + i), &s.imgLayered[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LAYER + i), &s.imgLayer[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_ACCESS + i), &s.imgAccess[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_FORMAT + i), &s.imgFormat[i]);
  }
  drain_errors();
}

static void restore_state(const SavedState& s) {
  for (int i = 0; i < 3; ++i) {
    g_gl.ActiveTexture((GLenum)(GL_TEXTURE0 + i));
    g_gl.BindTexture(GL_TEXTURE_2D, (GLuint)s.tex[i]);
    g_gl.BindSampler((GLuint)i, (GLuint)s.samp[i]);
  }
  g_gl.ActiveTexture((GLenum)s.active);
  g_gl.BindVertexArray((GLuint)s.vao);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, (GLuint)s.vbo);
  g_gl.UseProgram((GLuint)s.prog);
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)s.rfbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)s.dfbo);
  g_gl.Viewport(s.vp[0], s.vp[1], s.vp[2], s.vp[3]);
  if (s.en[0]) g_gl.Enable(GL_BLEND);
  if (s.en[1]) g_gl.Enable(GL_SCISSOR_TEST);
  if (s.en[2]) g_gl.Enable(GL_DEPTH_TEST);
  if (s.en[3]) g_gl.Enable(GL_STENCIL_TEST);
  if (s.en[4]) g_gl.Enable(GL_CULL_FACE);
  for (int i = 0; i < 8; ++i) {
    g_gl.BindImageTexture(
        (GLuint)i, (GLuint)s.imgName[i], s.imgLevel[i],
        (GLboolean)s.imgLayered[i], s.imgLayer[i], (GLenum)s.imgAccess[i],
        (GLenum)(s.imgFormat[i] ? s.imgFormat[i] : GL_RGBA8));
  }
}

void precapture_locked(int w, int h) {
  if (!g_enabled) return;
  if (w <= 0 || h <= 0) return;
  if (g_pBox == 0 || g_fbo == 0) return;  // built post-swap: never hitch swap
  drain_errors();
  GLint saveR = 0, saveD = 0;
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &saveR);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &saveD);
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, 0);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[g_turn], 0);
  g_gl.BlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT,
                       GL_NEAREST);
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)saveR);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)saveD);
  if (!ok) {
    note_fail_locked("retain-blit");
    publish_locked();
    return;
  }
  if (!pass_box(g_tex[g_turn], g_luma[g_turn])) {
    note_fail_locked("retain-luma");
    publish_locked();
    return;
  }
  unbind_images();
  g_turn ^= 1;
  if (g_have < 2) g_have++;
}

bool postgenerate_locked(EGLDisplay d, EGLSurface s, int w, int h) {
  if (!g_enabled || g_have < 2) return false;  // warmup: nothing invented
  if (!ensure_gl_locked(w, h)) {
    publish_locked();
    return false;
  }
  // Yield before paying flow cost when already over budget: FG must never
  // crater native fps. Visible as reason; the meter keeps measuring.
  if (g_genMs > 0.0 && g_nativeDtMs > 0.0 && g_genMs > 0.8 * g_nativeDtMs) {
    g_reason = "yielding (gpu-bound)";
    publish_locked();
    return false;
  }
  long long t0 = mono_ns();
  SavedState saved;
  save_state(saved);
  bool flowOk = run_flow_locked();
  bool any_fail = !flowOk;
  int presented = 0;
  const int n = g_mult;
  if (flowOk) {
    GLuint older = g_tex[g_turn];      // frame N-1
    GLuint newer = g_tex[g_turn ^ 1];  // frame N
    g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    g_gl.Viewport(0, 0, g_w, g_h);
    g_gl.Disable(GL_BLEND);
    g_gl.Disable(GL_SCISSOR_TEST);
    g_gl.Disable(GL_DEPTH_TEST);
    g_gl.Disable(GL_STENCIL_TEST);
    g_gl.Disable(GL_CULL_FACE);
    g_gl.UseProgram(g_pWarp);
    g_gl.ActiveTexture(GL_TEXTURE0);
    g_gl.BindTexture(GL_TEXTURE_2D, older);
    g_gl.BindSampler(0, g_samp);
    g_gl.ActiveTexture(GL_TEXTURE1);
    g_gl.BindTexture(GL_TEXTURE_2D, newer);
    g_gl.BindSampler(1, g_samp);
    g_gl.ActiveTexture(GL_TEXTURE2);
    g_gl.BindTexture(GL_TEXTURE_2D, g_flow[1]);
    g_gl.BindSampler(2, g_sampFlow);
    if (g_wGuard >= 0) g_gl.Uniform1f(g_wGuard, 1.0f);
    g_gl.BindVertexArray(g_vao);
    for (int k = 1; k < n; ++k) {
      if (g_wT >= 0) g_gl.Uniform1f(g_wT, (float)k / (float)n);
      g_gl.DrawArrays(GL_TRIANGLES, 0, 3);
      if (g_gl.GetError() != GL_NO_ERROR) {
        any_fail = true;
        break;
      }
      // Late-skip: never queue into a full pipeline when behind.
      if (g_nativeDtMs > 0.0) {
        double elapsed = (double)(mono_ns() - g_lastSwapNs) / 1000000.0;
        if (elapsed > 0.75 * g_nativeDtMs) break;
      }
      EGLBoolean ok = EGL_FALSE;
      if (g_gl.SwapBuffers) ok = g_gl.SwapBuffers(d, s);
      if (ok == EGL_TRUE) {
        presented++;
      } else {
        any_fail = true;
        break;  // stop the chain: never present past a failure
      }
    }
  }
  restore_state(saved);
  unbind_images();
  g_gl.ActiveTexture(GL_TEXTURE0);
  long long t1 = mono_ns();
  double ms = (double)(t1 - t0) / 1000000.0;
  g_genMs = g_genMs == 0.0 ? ms : g_genMs * 0.9 + ms * 0.1;
  if (presented > 0) {
    g_reason = "active";
    g_gen += (unsigned long long)presented;
    g_displayed += (unsigned long long)presented;
    g_confails = 0;
    if (!g_loggedGen) {
      g_loggedGen = true;
      char b[192];
      snprintf(b, sizeof(b), "silica-fg: generated frames live (%dx, +%d/frame)",
               n, n - 1);
      diag(b);
    }
  }
  if (any_fail) {
    g_drop++;
    note_fail_locked("present");
  }
  publish_locked();
  return presented > 0;
}

void on_swap_locked(bool swap_ok, int w, int h) {
  long long now = mono_ns();
  if (!swap_ok) {
    g_drop++;
    publish_locked();
    return;
  }
  if (g_lastSwapNs != 0) {
    double dt = (double)(now - g_lastSwapNs) / 1000000.0;
    if (dt > 0.0 && dt < 1000.0)
      g_nativeDtMs = (g_nativeDtMs == 0.0) ? dt : g_nativeDtMs * 0.9 + dt * 0.1;
  }
  g_native++;
  g_displayed++;
  g_lastSwapNs = now;
  (void)w;
  (void)h;
  publish_locked();
}

static void query_size_locked(EGLDisplay d, EGLSurface s, int& w, int& h) {
  w = 0;
  h = 0;
  if (!g_resolved) {
    // resolve procs (cheap after first) so size query works pre-init
    if (!resolve_gl_locked()) return;
  }
  if (!g_gl.QuerySurface) return;
  EGLint ew = 0, eh = 0;
  if (g_gl.QuerySurface(d, s, EGL_WIDTH, &ew)) w = (int)ew;
  if (g_gl.QuerySurface(d, s, EGL_HEIGHT, &eh)) h = (int)eh;
}

}  // namespace

// --- linked entries (C linkage) ---
extern "C" {

static long long g_cfgPollNs = 0;

static void pull_config_locked() {
  long long now = mono_ns();
  if (now - g_cfgPollNs < 1000000000LL || !g_bridgeClass) return;
  g_cfgPollNs = now;
  JNIEnv* e = jni_env();
  if (!e) return;
  if (e->ExceptionCheck()) e->ExceptionClear();
  jstring d = (jstring)e->CallStaticObjectMethod(g_bridgeClass, g_mDir);
  bool en = e->CallStaticBooleanMethod(g_bridgeClass, g_mEn) == JNI_TRUE;
  int mult = e->CallStaticIntMethod(g_bridgeClass, g_mMult);
  if (e->ExceptionCheck()) {
    e->ExceptionClear();
    return;
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
      (!dir.empty() && dir != g_dataDir)) {
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

void silica_engine_before_swap(EGLDisplay dpy, EGLSurface surf) {
  int w = 0, h = 0;
  {
    std::lock_guard<std::mutex> l(g_mu);
    pull_config_locked();
    query_size_locked(dpy, surf, w, h);
    precapture_locked(w, h);
  }
  (void)dpy;
  (void)surf;
}

void silica_engine_after_swap(EGLDisplay dpy, EGLSurface surf, int ok) {
  int w = 0, h = 0;
  {
    std::lock_guard<std::mutex> l(g_mu);
    query_size_locked(dpy, surf, w, h);
    on_swap_locked(ok != 0, w, h);
    if (ok) postgenerate_locked(dpy, surf, w, h);
  }
}

EGLBoolean silica_engine_wrapped_swap(EGLDisplay dpy, EGLSurface surf) {
  silica_engine_before_swap(dpy, surf);
  EGLBoolean ok = EGL_FALSE;
  {
    std::lock_guard<std::mutex> l(g_mu);
    if (g_gl.SwapBuffers) ok = g_gl.SwapBuffers(dpy, surf);
  }
  silica_engine_after_swap(dpy, surf, ok == EGL_TRUE);
  return ok;
}

void* silica_engine_swap_entry(void) {
  return reinterpret_cast<void*>(&silica_engine_wrapped_swap);
}

const char* silica_fg_build_id(void) {
  return "silica-fg-own-1.19";
}

jboolean Java_dev_oxide_launcher_bridge_SilicaFgBridge_nativeBind(
    JNIEnv* env, jclass clazz) {
  if (!env || !clazz) return JNI_FALSE;
  JavaVM* vm = nullptr;
  if (env->GetJavaVM(&vm) != JNI_OK || !vm) return JNI_FALSE;
  jclass ref = (jclass)env->NewGlobalRef(clazz);
  if (!ref) return JNI_FALSE;
  jmethodID mDir = env->GetStaticMethodID(ref, "engineDataDir",
                                          "()Ljava/lang/String;");
  jmethodID mEn = env->GetStaticMethodID(ref, "engineEnabled", "()Z");
  jmethodID mMult = env->GetStaticMethodID(ref, "engineMult", "()I");
  if (!mDir || !mEn || !mMult) {
    env->DeleteGlobalRef(ref);
    return JNI_FALSE;
  }
  std::lock_guard<std::mutex> l(g_mu);
  if (g_bridgeClass) env->DeleteGlobalRef(g_bridgeClass);
  g_vm = vm;
  g_bridgeClass = ref;
  g_mDir = mDir;
  g_mEn = mEn;
  g_mMult = mMult;
  diag("silica-fg: bridge bound (config pull live)");
  return JNI_TRUE;
}

}  // extern "C"
}  // namespace silica_engine



