// Minimal GLES3 header stub for HOST unit tests of the ONIGAMI translator.
// Provides only the tokens translate.h / shader_translate.cpp need. Never
// used for device builds (the NDK header takes precedence there).
#pragma once
typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef unsigned char GLboolean;
typedef char GLchar;
typedef float GLfloat;
#define GL_VERTEX_SHADER 0x8B31
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_COMPILE_STATUS 0x8B81
#define GL_INFO_LOG_LENGTH 0x8B84
#define GL_LINK_STATUS 0x8B82
