#pragma once
// ONIGAMI state coalescing: redundant calls never reach the driver. Own design.
#include <GLES3/gl3.h>
#include <mutex>
#include <unordered_map>
namespace onigami {
class StateCache {
 public:
    static StateCache& instance();
    unsigned long long hits();
    unsigned long long skips();
    bool coalescing() const;
    bool check_use_program(GLuint p);
    bool check_bind_buffer(GLenum target, GLuint b);
    bool check_bind_texture(GLenum target, GLuint t);
    bool check_bind_framebuffer(GLuint f);
    bool check_bind_vertex_array(GLuint v);
    bool check_cap(GLenum cap, bool enable);
    bool check_viewport(GLint x, GLint y, GLsizei w, GLsizei h);
    void note_skip();
    void invalidate();
 private:
    std::mutex m_;
    unsigned long long hits_ = 0;
    unsigned long long skips_ = 0;
    bool has_prog_ = false;
    GLuint prog_ = 0;
    bool has_ab_ = false;
    bool has_eb_ = false;
    GLuint ab_ = 0;
    GLuint eb_ = 0;
    bool has_tex_ = false;
    GLuint tex_ = 0;
    bool has_fbo_ = false;
    GLuint fbo_ = 0;
    bool has_vao_ = false;
    GLuint vao_ = 0;
    bool has_vp_ = false;
    GLint vpx_ = 0;
    GLint vpy_ = 0;
    GLsizei vpw_ = 0;
    GLsizei vph_ = 0;
    std::unordered_map<GLenum, bool> caps_;
};
StateCache& state_cache();
} // namespace onigami
