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
    if (t == GL_ELEMENT_ARRAY_BUFFER) {
        if (has_eb_ && eb_ == b) {
            hits_++;
            return true;
        }
        eb_ = b;
        has_eb_ = true;
        return false;
    }
    return false;
}
bool StateCache::check_bind_texture(GLenum t, GLuint x) {
    if (!coalescing()) return false;
    if (t != GL_TEXTURE_2D) return false;
    std::lock_guard<std::mutex> l(m_);
    if (has_tex_ && tex_ == x) {
        hits_++;
        return true;
    }
    tex_ = x;
    has_tex_ = true;
    return false;
}
bool StateCache::check_bind_framebuffer(GLuint f) {
    if (!coalescing()) return false;
    std::lock_guard<std::mutex> l(m_);
    if (has_fbo_ && fbo_ == f) {
        hits_++;
        return true;
    }
    fbo_ = f;
    has_fbo_ = true;
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
void StateCache::invalidate() {
    std::lock_guard<std::mutex> l(m_);
    has_prog_ = has_ab_ = has_eb_ = has_tex_ = has_fbo_ = has_vao_ = has_vp_ = false;
    caps_.clear();
}
} // namespace onigami
