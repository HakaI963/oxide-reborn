// Host stub of the NDK GLES3/gl3.h for ONIGAMI host unit tests.
// Matches the NDK signatures used by backend.h. Never used for device builds.
#pragma once
typedef unsigned int GLenum;
typedef unsigned char GLboolean;
typedef unsigned int GLbitfield;
typedef void GLvoid;
typedef int GLint;
typedef unsigned int GLuint;
typedef int GLsizei;
typedef unsigned int GLubyte;   // NDK: unsigned char
typedef float GLfloat;
typedef double GLdouble;
typedef double GLclampd;
typedef float GLclampf;
typedef char GLchar;
typedef short GLshort;
typedef unsigned short GLushort;
typedef long int GLintptr;
typedef long int GLsizeiptr;
typedef long long GLint64;
typedef unsigned long long GLuint64;
typedef long long GLsizei64;
typedef void* GLsync;
#ifdef __cplusplus
extern "C" {
#endif
const GLubyte* glGetString(GLenum name);
const GLubyte* glGetStringi(GLenum name, GLuint index);
GLenum glGetError(void);
void glGetIntegerv(GLenum pname, GLint* data);
void glGetFloatv(GLenum pname, GLfloat* data);
void glGetBooleanv(GLenum pname, GLboolean* data);
GLuint glCreateShader(GLenum shaderType);
void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);
void glCompileShader(GLuint shader);
void glGetShaderiv(GLuint shader, GLenum pname, GLint* params);
void glGetShaderInfoLog(GLuint shader, GLsizei maxLength, GLsizei* length, GLchar* infoLog);
void glDeleteShader(GLuint shader);
GLboolean glIsShader(GLuint shader);
GLuint glCreateProgram(void);
void glAttachShader(GLuint program, GLuint shader);
void glDetachShader(GLuint program, GLuint shader);
void glLinkProgram(GLuint program);
void glGetProgramiv(GLuint program, GLenum pname, GLint* params);
void glGetProgramInfoLog(GLuint program, GLsizei maxLength, GLsizei* length, GLchar* infoLog);
void glDeleteProgram(GLuint program);
GLboolean glIsProgram(GLuint program);
void glUseProgram(GLuint program);
GLint glGetUniformLocation(GLuint program, const GLchar* name);
GLint glGetAttribLocation(GLuint program, const GLchar* name);
void glUniform1i(GLint location, GLint v0);
void glUniform1f(GLint location, GLfloat v0);
void glUniform4fv(GLint location, GLsizei count, const GLfloat* value);
void glUniformMatrix4fv(GLint location, GLsizei count, GLboolean transpose, const GLfloat* value);
void glGenBuffers(GLsizei n, GLuint* buffers);
void glBindBuffer(GLenum target, GLuint buffer);
void glBufferData(GLenum target, GLsizeiptr size, const void* data, GLenum usage);
void glBufferSubData(GLenum target, GLintptr offset, GLsizeiptr size, const void* data);
void glGenVertexArrays(GLsizei n, GLuint* arrays);
void glBindVertexArray(GLuint array);
void glGenTextures(GLsizei n, GLuint* textures);
void glBindTexture(GLenum target, GLuint texture);
void glActiveTexture(GLenum texture);
void glTexParameteri(GLenum target, GLenum pname, GLint param);
void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels);
void glTexSubImage2D(GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void* pixels);
void glGenerateMipmap(GLenum target);
void glGenFramebuffers(GLsizei n, GLuint* framebuffers);
void glBindFramebuffer(GLenum target, GLuint framebuffer);
GLenum glCheckFramebufferStatus(GLenum target);
void glFramebufferTexture2D(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level);
void glFramebufferRenderbuffer(GLenum target, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer);
void glGenRenderbuffers(GLsizei n, GLuint* renderbuffers);
void glBindRenderbuffer(GLenum target, GLuint renderbuffer);
void glRenderbufferStorage(GLenum target, GLenum internalformat, GLsizei width, GLsizei height);
void glBlitFramebuffer(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter);
void glEnableVertexAttribArray(GLuint index);
void glDisableVertexAttribArray(GLuint index);
void glVertexAttribPointer(GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void* pointer);
void glVertexAttrib4f(GLuint index, GLfloat x, GLfloat y, GLfloat z, GLfloat w);
void glViewport(GLint x, GLint y, GLsizei width, GLsizei height);
void glScissor(GLint x, GLint y, GLsizei width, GLsizei height);
void glClear(GLbitfield mask);
void glClearColor(GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha);
void glEnable(GLenum cap);
void glDisable(GLenum cap);
GLboolean glIsEnabled(GLenum cap);
void glBlendFunc(GLenum sfactor, GLenum dfactor);
void glDepthMask(GLboolean flag);
void glColorMask(GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha);
void glPixelStorei(GLenum pname, GLint param);
void glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void* pixels);
void glDrawArrays(GLenum mode, GLint first, GLsizei count);
void glDrawElements(GLenum mode, GLsizei count, GLenum type, const void* indices);
void glDrawArraysInstanced(GLenum mode, GLint first, GLsizei count, GLsizei instancecount);
void glDrawElementsInstanced(GLenum mode, GLsizei count, GLenum type, const void* indices, GLsizei instancecount);
void glFinish(void);
void glFlush(void);
#define GL_NO_ERROR 0
#define GL_TRUE 1
#define GL_FALSE 0
#define GL_VERTEX_SHADER 0x8B31
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_COMPUTE_SHADER 0x91B9
#define GL_COMPILE_STATUS 0x8B81
#define GL_INFO_LOG_LENGTH 0x8B84
#define GL_LINK_STATUS 0x8B82
#define GL_SHADER_TYPE 0x8B4F
#define GL_ARRAY_BUFFER 0x8892
#define GL_ELEMENT_ARRAY_BUFFER 0x8893
#define GL_STATIC_DRAW 0x88E4
#define GL_TEXTURE_2D 0x0DE1
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE_CUBE_MAP_SEAMLESS 0x884F
#define GL_PROGRAM_POINT_SIZE 0x8642
#define GL_FRAMEBUFFER_SRGB 0x8DB9
#define GL_PROXY_TEXTURE_2D 0x8064
#define GL_NUM_EXTENSIONS 0x821D
#define GL_EXTENSIONS 0x1F03
#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_VIEWPORT 0x0BA2
#define GL_COLOR_BUFFER_BIT 0x00004000
#define GL_DEPTH_BUFFER_BIT 0x00000100
#define GL_STENCIL_BUFFER_BIT 0x00000400
#define GL_DEPTH_TEST 0x0B71
#define GL_BLEND 0x0BE2
#define GL_CULL_FACE 0x0B44
#define GL_FRAMEBUFFER 0x8D40
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#define GL_READ_FRAMEBUFFER 0x8CA8
#define GL_FRAMEBUFFER_BINDING 0x8CA6
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_FRAMEBUFFER_UNSUPPORTED 0x8CD6
#ifdef __cplusplus
}
#endif
#define GL_WAIT_FAILED 0x911D
#define GL_TIMEOUT_EXPIRED 0x911B
#define GL_NONE 0
#define GL_BACK 0x0405
#define GL_FRONT 0x0404
#define GL_INVALID_INDEX 0xFFFFFFFFu
#define GL_TRIANGLES 0x0004
#define GL_UNSIGNED_INT 0x1405
