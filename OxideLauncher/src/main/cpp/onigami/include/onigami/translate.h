#pragma once
// ONIGAMI stage-1 shader translation interface. Own design.
#include <GLES3/gl3.h>
#include <stddef.h>
#include <string>
namespace onigami {
enum class Stage { Vertex, Fragment, Compute, Unknown };
struct TranslateResult {
  bool ok = false;
  bool passthrough = false; // true when source was already ESSL
  std::string essl;
  std::string log;
};
bool is_essl_source(const char* src);
Stage stage_from_gl(GLenum type);
unsigned long long fnv1a64(const char* data, size_t len);
TranslateResult translate_shader(GLenum glType, const char* src);
} // namespace onigami
