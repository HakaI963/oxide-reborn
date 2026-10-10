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
    void note_active_unit(GLenum unit);
    bool check_bind_framebuffer(GLenum target, GLuint f);
    bool check_bind_vertex_array(GLuint v);
    bool check_cap(GLenum cap, bool enable);
    bool check_viewport(GLint x, GLint y, GLsizei w, GLsizei h);
    void note_skip();
    void invalidate();
    GLuint bound_framebuffer();
    GLuint bound_vertex_array();
    GLuint bound_program();
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
    GLuint active_unit_ = 0;
    std::unordered_map<GLuint, GLuint> tex2d_by_unit_;
    bool has_draw_fbo_ = false;
    GLuint draw_fbo_ = 0;
    bool has_read_fbo_ = false;
    GLuint read_fbo_ = 0;
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
