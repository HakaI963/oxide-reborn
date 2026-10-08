#pragma once
// Silica shader pipeline interface (internal). Own design.
// Intercepts shader/program entry points so translation, caching and
// diagnostics happen in Silica code; the backend driver only ever sees
// ESSL it can compile. Types come from the NDK GLES3 headers.
#include <GLES3/gl3.h>
namespace silica::shader {
// Object lifetime (backend creation/deletion is forwarded inside).
GLuint create_shader(GLenum type);
void delete_shader(GLuint s);
GLuint create_program();
void delete_program(GLuint p);
// Source is stored here; submission to the backend happens at compile time.
void shader_source(GLuint s, GLsizei count, const GLchar* const* str, const GLint* len);
// Full path: cache lookup -> glslang -> SPIR-V -> SPIRV-Cross -> backend
// submit + compile. Never recompiles an already-translated source.
void compile_shader(GLuint s);
// Query interception: COMPILE_STATUS and INFO_LOG_LENGTH reflect the whole
// pipeline (translation + backend); everything else forwards.
void get_shaderiv(GLuint s, GLenum p, GLint* v);
void get_shader_info_log(GLuint s, GLsizei n, GLsizei* len, GLchar* log);
// Program assembly with vault-backed link skipping.
void attach_shader(GLuint p, GLuint s);
void detach_shader(GLuint p, GLuint s);
void link_program(GLuint p);
void get_programiv(GLuint p, GLenum q, GLint* v);
void get_program_info_log(GLuint p, GLsizei n, GLsizei* len, GLchar* log);
} // namespace silica::shader
