// SilicaFrameGen own GLES engine: hierarchical block-match optical flow +
// warp-blend synthesis at the bridge present path (v1.19.0+).
//
// Clean-room code: luma/quarter-downsample Gregorian... (see SILICA_OWN_FG
// design note in docs/). Published-technique implementation written for this
// project: NO third-party FG source, shaders, or weights. Requires GLES 3.1+
// (compute); anything older disables with a logged reason. All GL calls use
// raw backend procs resolved here (never through any wrapper); all game
// state is saved/restored; failures degrade to counting-only then disable.
#include "silica_fg/silica_fg.h"

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

namespace silica_fg {
namespace {

// ---------------------------------------------------------------------------
// Raw backend GLES entry points.
// ---------------------------------------------------------------------------
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
SFG_FN(void, glTexImage2D, GLenum t, GLint l, GLint f, GLsizei w, GLsizei h,
        GLint b, GLenum fmt, GLenum ty, const void* p);
SFG_FN(void, glDeleteTextures, GLsizei n, const GLuint* t);
SFG_FN(void, glBindImageTexture, GLuint u, GLuint t, GLint l, GLboolean ly,
        GLint lr, GLenum a, GLenum f);
SFG_FN(void, glGenSamplers, GLsizei n, GLuint* s);
SFG_FN(void, glSamplerParameteri, GLuint s, GLenum p, GLint v);
SFG_FN(void, glBindSampler, GLuint u, GLuint s);
SFG_FN(void, glDeleteSamplers, GLsizei n, const GLuint* s);
SFG_FN(void, glGenFramebuffers, GLsizei n, GLuint* ids);
SFG_FN(void, glBindFramebuffer, GLenum t, GLuint f);
SFG_FN(void, glFramebufferTexture2D, GLenum t, GLenum a, GLenum tt, GLuint te,
        GLint l);
SFG_FN(GLenum, glCheckFramebufferStatus, GLenum t);
SFG_FN(void, glDeleteFramebuffers, GLsizei n, const GLuint* ids);
SFG_FN(void, glBlitFramebuffer, GLint a, GLint b, GLint c, GLint d, GLint e,
        GLint f, GLint g, GLint h, GLbitfield m, GLenum fl);
SFG_FN(void, glGenBuffers, GLsizei n, GLuint* b);
SFG_FN(void, glBindBuffer, GLenum t, GLuint b);
SFG_FN(void, glBufferData, GLenum t, GLsizeiptr s, const void* d, GLenum u);
SFG_FN(void, glDeleteBuffers, GLsizei n, const GLuint* b);
SFG_FN(void, glGenVertexArrays, GLsizei n, GLuint* a);
SFG_FN(void, glBindVertexArray, GLuint a);
SFG_FN(void, glDeleteVertexArrays, GLsizei n, const GLuint* a);
SFG_FN(void, glEnableVertexAttribArray, GLuint i);
SFG_FN(void, glVertexAttribPointer, GLuint i, GLint s, GLenum t, GLboolean n,
        GLsizei st, const void* p);
SFG_FN(GLuint, glCreateShader, GLenum t);
SFG_FN(void, glShaderSource, GLuint s, GLsizei c, const GLchar* const* st,
        const GLint* l);
SFG_FN(void, glCompileShader, GLuint s);
SFG_FN(void, glGetShaderiv, GLuint s, GLenum p, GLint* v);
SFG_FN(void, glGetShaderInfoLog, GLuint s, GLsizei b, GLsizei* l, GLchar* o);
SFG_FN(void, glDeleteShader, GLuint s);
SFG_FN(GLuint, glCreateProgram, void);
SFG_FN(void, glAttachShader, GLuint p, GLuint s);
SFG_FN(void, glLinkProgram, GLuint p);
SFG_FN(void, glGetProgramiv, GLuint p, GLenum v, GLint* o);
SFG_FN(void, glGetProgramInfoLog, GLuint p, GLsizei b, GLsizei* l, GLchar* o);
SFG_FN(void, glDeleteProgram, GLuint p);
SFG_FN(void, glUseProgram, GLuint p);
SFG_FN(GLint, glGetUniformLocation, GLuint p, const GLchar* n);
SFG_FN(void, glUniform1i, GLint l, GLint v);
SFG_FN(void, glUniform1f, GLint l, GLfloat v);
SFG_FN(void, glUniform2f, GLint l, GLfloat a, GLfloat b);
SFG_FN(void, glUniform2i, GLint l, GLint a, GLint b);
SFG_FN(void, glDispatchCompute, GLuint x, GLuint y, GLuint z);
SFG_FN(void, glMemoryBarrier, GLbitfield b);
SFG_FN(void, glDrawArrays, GLenum m, GLint f, GLsizei c);
#undef SFG_FN

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
  P_glUniform2f Uniform2f = nullptr;
  P_glUniform2i Uniform2i = nullptr;
  P_glDispatchCompute DispatchCompute = nullptr;
  P_glMemoryBarrier MemoryBarrier = nullptr;
  P_glDrawArrays DrawArrays = nullptr;
};

// Own shaders (ESSL 310 es compute + 300 es warp). Original code.
const char kLuma[] =
    "#version 310 es\n"
    "layout(local_size_x=8, local_size_y=8) in;\n"
    "layout(binding=0, rgba8) uniform readonly highp image2D uSrc;\n"
    "layout(binding=1, r8) uniform writeonly highp image2D uDst;\n"
    "uniform ivec2 uSize;\n"
    "void main(){\n"
    "ivec2 p=ivec2(gl_GlobalInvocationID.xy);\n"
    "if(p.x>=uSize.x||p.y>=uSize.y) return;\n"
    "vec4 c=imageLoad(uSrc,p);\n"
    "float l=dot(c.rgb,vec3(0.299,0.587,0.114));\n"
    "imageStore(uDst,p,vec4(l,0.0,0.0,1.0));}\n";
const char kDown[] =
    "#version 310 es\n"
    "layout(local_size_x=8, local_size_y=8) in;\n"
    "layout(binding=2, r8) uniform readonly highp image2D uSrc;\n"
    "layout(binding=3, r8) uniform writeonly highp image2D uDst;\n"
    "uniform ivec2 uDstSize;\n"
    "void main(){\n"
    "ivec2 p=ivec2(gl_GlobalInvocationID.xy);\n"
    "if(p.x>=uDstSize.x||p.y>=uDstSize.y) return;\n"
    "ivec2 b=p*4;\n"
    "float s=0.0;\n"
    "for(int y=0;y<4;y++)for(int x=0;x<4;x++)\n"
    "s+=imageLoad(uSrc,clamp(b+ivec2(x,y),ivec2(0),uDstSize*4-1)).r;\n"
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
