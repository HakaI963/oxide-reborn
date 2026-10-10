// ONIGAMI state coalescing implementation. Own code.
#include "state_cache.h"
#include "onigami/config.h"
namespace onigami {
StateCache& StateCache::instance() {
    static StateCache s;
    return s;
}
StateCache& state_cache() { return StateCache::instance(); }
unsigned long long StateCache::hits() {
    std::lock_guard<std::mutex> l(m_);
    return hits_;
}
unsigned long long StateCache::skips() {
    std::lock_guard<std::mutex> l(m_);
    return skips_;
}
bool StateCache::coalescing() const { return current_config().state_coalescing; }
void StateCache::note_skip() {
    std::lock_guard<std::mutex> l(m_);
    skips_++;
}
bool StateCache::check_use_program(GLuint p) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    if (has_prog_ && prog_ == p) {
        hits_++;
        return true;
    }
    prog_ = p;
    has_prog_ = true;
    return false;
}
bool StateCache::check_bind_buffer(GLenum t, GLuint b) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    if (t == GL_ARRAY_BUFFER) {
        if (has_ab_ && ab_ == b) {
            hits_++;
            return true;
        }
        ab_ = b;
        has_ab_ = true;
        return false;
    }
    // ELEMENT_ARRAY_BUFFER is VAO-scoped backend state (unlike the
    // context-global ARRAY_BUFFER binding), so a global last-value cache
    // cannot mirror it across VAO switches: skipping a re-bind draws with
    // the wrong index buffer (streaks / slivers / covering triangles).
    // Index binds are therefore always forwarded.
    if (t == GL_ELEMENT_ARRAY_BUFFER) return false;
    return false;
}
// TEXTURE_2D bindings are per-texture-unit state: the same id bound on
// two different units must both reach the driver. The active unit is
// recorded by note_active_unit (called from the glActiveTexture wrapper).
bool StateCache::check_bind_texture(GLenum t, GLuint x) {
    if (!coalescing()) return false;
    if (t != GL_TEXTURE_2D) return false;
    std::lock_guard<std::mutex> l(m_);
    GLuint key = (active_unit_ << 8) | 0xE1u;
    auto it = tex2d_by_unit_.find(key);
    if (it != tex2d_by_unit_.end() && it->second == x) {
        hits_++;
        return true;
    }
    tex2d_by_unit_[key] = x;
    return false;
}
void StateCache::note_active_unit(GLenum unit) {
    if (unit < 0x84C0u || unit >= 0x84C0u + 32) return;
    std::lock_guard<std::mutex> l(m_);
    active_unit_ = unit - 0x84C0u;
}
GLuint StateCache::bound_vertex_array() {
    std::lock_guard<std::mutex> l(m_);
    return has_vao_ ? vao_ : 0;
}
// DRAW and READ bindings are independent state in GL; tracking them
// separately is required, otherwise a READ bind can suppress a later DRAW
// bind (or vice versa) and draws silently land on the wrong framebuffer.
bool StateCache::check_bind_framebuffer(GLenum target, GLuint f) {
    if (!coalescing()) return false;
    bool* has;
    GLuint* cur;
    if (target == 0x8CA8u) { has = &has_read_fbo_; cur = &read_fbo_; }
    else if (target == 0x8CA9u) { has = &has_draw_fbo_; cur = &draw_fbo_; }
    else { has = &has_draw_fbo_; cur = &draw_fbo_; }
    std::lock_guard<std::mutex> l(m_);
    if (*has && *cur == f) {
        hits_++;
        return true;
    }
    *cur = f;
    *has = true;
    if (target == 0x8D40u) { has_draw_fbo_ = true; draw_fbo_ = f; has_read_fbo_ = true; read_fbo_ = f; }
    return false;
}
bool StateCache::check_bind_vertex_array(GLuint v) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    if (has_vao_ && vao_ == v) {
        hits_++;
        return true;
    }
    vao_ = v;
    has_vao_ = true;
    return false;
}
bool StateCache::check_cap(GLenum c, bool e) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    auto it = caps_.find(c);
    if (it != caps_.end() && it->second == e) {
        hits_++;
        return true;
    }
    caps_[c] = e;
    return false;
}
bool StateCache::check_viewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    if (has_vp_ && vpx_ == x && vpy_ == y && vpw_ == w && vph_ == h) {
        hits_++;
        return true;
    }
    vpx_ = x;
    vpy_ = y;
    vpw_ = w;
    vph_ = h;
    has_vp_ = true;
    return false;
}
GLuint StateCache::bound_framebuffer() {
    // DRAW binding: the target that draw-buffer state applies to.
    std::lock_guard<std::mutex> l(m_);
    return has_draw_fbo_ ? draw_fbo_ : 0;
}
void StateCache::invalidate() {
    std::lock_guard<std::mutex> l(m_);
    has_prog_ = has_ab_ = has_eb_ = has_draw_fbo_ = has_read_fbo_ = has_vao_ = has_vp_ = false;
    tex2d_by_unit_.clear();
    caps_.clear();
}
} // namespace onigami
