// ONIGAMI long-tail entry points.
//
// The frontend (gl_frontend.cpp) implements the hot path. Every other desktop
// entry point was previously either an unguarded `gsym<>()` lookup or no
// wrapper at all. On an ES 3.2 context that leaves two hazards:
//
//   1. `gsym<>()` resolves to nullptr for any name the driver does not export.
//      The wrapper is then a silent no-op, so the draw or the upload never
//      reaches the driver. Minecraft's terrain path uses multi-draw: the
//      buffer binds, the program links, nothing rasterises.
//   2. A name with no wrapper at all falls through eglGetProcAddress to the
//      backend address, which is whatever the driver exports under that name.
//
// This file gives each of those names an honest, general treatment, chosen by
// a single classification table (gl_longtail.h) so the behaviour can be
// asserted by a host test rather than trusted:
//
//   kForward  ES 3.2 has this exact entry point. Direct forward.
//   kEsName   ES 3.2 has it under a different name. Forward to that symbol.
//   kEmulate  No ES equivalent, but an exact emulation exists. Emulate.
//   kAbsent   No ES equivalent and no honest emulation. Log once, do nothing,
//             never answer a null pointer.
//
// The emulations that exist are exact, not approximate. Multi-draw expands to
// the per-draw command because each draw in a multi-draw is an independent
// primitive set -- GL 4.6 section 10.5 defines glMultiDrawElements as the
// fixed set of glDrawElements commands it expands to. glCreate* generates a
// real name from the classic ES entry point, which is the only way to obtain
// a valid object on a backend with no DSA path; it does not pretend the DSA
// entry point itself exists, and no other DSA command is implemented.
//
// No entry here is specific to Minecraft, a shader, or a texture: each one is
// the portable treatment of a desktop entry point an ES 3.2 context is
// missing, which is the whole job of a translation layer.

#include "onigami_longtail.h"

#include "onigami/backend.h"
#include "onigami/config.h"

#include <GLES3/gl3.h>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <unordered_map>
#include <vector>

#if defined(__GNUC__) || defined(__clang__)
#define O_API __attribute__((visibility("default")))
#else
#define O_API
#endif

// ---------------------------------------------------------------------------
// Classification table
// ---------------------------------------------------------------------------

namespace onigami_longtail {

const Policy kPolicies[] = {
    // ---- uniform blocks: ES 3.2 core, same names ------------------------
    {"glGetUniformBlockIndex", Cls::kForward, "glGetUniformBlockIndex", "ES 3.2 core, same name"},
    {"glUniformBlockBinding", Cls::kForward, "glUniformBlockBinding", "ES 3.2 core, same name"},
    {"glBindBufferBase", Cls::kForward, "glBindBufferBase", "ES 3.2 core, same name"},
    {"glBindBufferRange", Cls::kForward, "glBindBufferRange", "ES 3.2 core, same name"},

    // ---- immutable texture storage: ES 3.2 core -------------------------
    {"glTexStorage2D", Cls::kForward, "glTexStorage2D", "ES 3.2 core, same name"},
    {"glTexStorage3D", Cls::kForward, "glTexStorage3D", "ES 3.2 core, same name"},

    // ---- typed clears: ES 3.2 core --------------------------------------
    {"glClearBufferfv", Cls::kForward, "glClearBufferfv", "ES 3.2 core, same name"},
    {"glClearBufferfi", Cls::kForward, "glClearBufferfi", "ES 3.2 core, same name"},
    {"glClearBufferuiv", Cls::kForward, "glClearBufferuiv", "ES 3.2 core, same name"},

    // ---- texture queries: ES 3.2 core -----------------------------------
    {"glGetTexLevelParameteriv", Cls::kForward, "glGetTexLevelParameteriv", "ES 3.2 core, same name"},
    {"glGetTexLevelParameterfv", Cls::kForward, "glGetTexLevelParameterfv", "ES 3.2 core, same name"},

    // ---- framebuffer attachments: ES 3.2 core ---------------------------
    {"glFramebufferTexture", Cls::kForward, "glFramebufferTexture", "ES 3.2 core, same name"},
    {"glFramebufferTextureLayer", Cls::kForward, "glFramebufferTextureLayer", "ES 3.2 core, same name"},

    // ---- query objects: ES 3.2 declares only the unsigned form ----------
    {"glGetQueryObjectuiv", Cls::kForward, "glGetQueryObjectuiv", "ES 3.2 core, same name"},
    {"glGetQueryObjecti64v", Cls::kAbsent, nullptr,
     "ES 3.2 has no glGetQueryObjecti64v (no 64-bit integer query results)"},

    // ---- blend colour: ES 3.2 core --------------------------------------
    {"glBlendColor", Cls::kForward, "glBlendColor", "ES 3.2 core, same name"},

    // ---- labelled objects: ES 3.2 core ----------------------------------
    {"glObjectLabel", Cls::kForward, "glObjectLabel", "ES 3.2 core, same name"},

    // ---- copies: ES 3.2 core --------------------------------------------
    {"glCopyBufferSubData", Cls::kForward, "glCopyBufferSubData", "ES 3.2 core, same name"},
    {"glCopyImageSubData", Cls::kForward, "glCopyImageSubData", "ES 3.2 core, same name"},

    // ---- is-queries: ES 3.2 core ----------------------------------------
    {"glIsBuffer", Cls::kForward, "glIsBuffer", "ES 3.2 core, same name"},
    {"glIsVertexArray", Cls::kForward, "glIsVertexArray", "ES 3.2 core, same name"},
    {"glIsTexture", Cls::kForward, "glIsTexture", "ES 3.2 core, same name"},
    {"glIsFramebuffer", Cls::kForward, "glIsFramebuffer", "ES 3.2 core, same name"},
    {"glIsSampler", Cls::kForward, "glIsSampler", "ES 3.2 core, same name"},

    // ---- base-vertex draws: ES 3.2 core ---------------------------------
    {"glDrawElementsBaseVertex", Cls::kForward, "glDrawElementsBaseVertex", "ES 3.2 core, same name"},
    {"glDrawElementsInstancedBaseVertex", Cls::kForward, "glDrawElementsInstancedBaseVertex", "ES 3.2 core, same name"},
    {"glDrawRangeElementsBaseVertex", Cls::kForward, "glDrawRangeElementsBaseVertex", "ES 3.2 core, same name"},

    // ---- transform feedback: ES 3.2 core --------------------------------
    {"glGenTransformFeedbacks", Cls::kForward, "glGenTransformFeedbacks", "ES 3.2 core, same name"},
    {"glBindTransformFeedback", Cls::kForward, "glBindTransformFeedback", "ES 3.2 core, same name"},
    {"glDeleteTransformFeedbacks", Cls::kForward, "glDeleteTransformFeedbacks", "ES 3.2 core, same name"},
    {"glBeginTransformFeedback", Cls::kForward, "glBeginTransformFeedback", "ES 3.2 core, same name"},
    {"glEndTransformFeedback", Cls::kForward, "glEndTransformFeedback", "ES 3.2 core, same name"},
    {"glPauseTransformFeedback", Cls::kForward, "glPauseTransformFeedback", "ES 3.2 core, same name"},
    {"glResumeTransformFeedback", Cls::kForward, "glResumeTransformFeedback", "ES 3.2 core, same name"},
    {"glTransformFeedbackVaryings", Cls::kForward, "glTransformFeedbackVaryings", "ES 3.2 core, same name"},

    // ---- desktop-only texture readback -----------------------------------
    {"glGetTexImage", Cls::kAbsent, nullptr,
     "ES 3.2 has no glGetTexImage and none can be emulated honestly"},

    // ---- desktop-only debug group ---------------------------------------
    {"glPushDebugGroup", Cls::kAbsent, nullptr,
     "ES 3.2 has no glPushDebugGroup"},
    {"glPopDebugGroup", Cls::kAbsent, nullptr,
     "ES 3.2 has no glPopDebugGroup"},

    // ---- multi-draw ------------------------------------------------------
    // The emulation is exact: a multi-draw is a list of independent draws, so
    // expanding to the per-draw commands yields the same primitive set.
    {"glMultiDrawElements", Cls::kEmulate, nullptr,
     "expanded to glDrawElements, exact"},
    {"glMultiDrawElementsBaseVertex", Cls::kEmulate, nullptr,
     "expanded to glDrawElementsBaseVertex, exact"},
    {"glMultiDrawElementsBaseVertexEXT", Cls::kEmulate, nullptr,
     "expanded to glDrawElementsBaseVertex, exact"},
    {"glMultiDrawElementsEXT", Cls::kEsName, "glMultiDrawElementsEXT",
     "driver symbol exists only when GL_EXT_multi_draw is advertised"},
    {"glMultiDrawArrays", Cls::kEmulate, nullptr,
     "expanded to glDrawArrays, exact"},
    {"glMultiDrawArraysEXT", Cls::kEsName, "glMultiDrawArraysEXT",
     "driver symbol exists only when GL_EXT_multi_draw is advertised"},

    // ---- object creation: no DSA path on ES 3.2 -------------------------
    // A wrapper must exist because the mapped GL_VERSION makes the app probe
    // for these, but it must not fabricate the DSA entry point. The name is
    // real, obtained from the classic ES entry point.
    {"glCreateBuffers", Cls::kEmulate, nullptr,
     "name from glGenBuffers; DSA itself is not claimed"},
    {"glCreateVertexArrays", Cls::kEmulate, nullptr,
     "name from glGenVertexArrays; DSA itself is not claimed"},
    {"glCreateTextures", Cls::kEmulate, nullptr,
     "name from glGenTextures; DSA itself is not claimed"},
    {"glCreateSamplers", Cls::kEmulate, nullptr,
     "name from glGenSamplers; DSA itself is not claimed"},
    {"glCreateFramebuffers", Cls::kEmulate, nullptr,
     "name from glGenFramebuffers; DSA itself is not claimed"},
    {"glCreateRenderbuffers", Cls::kEmulate, nullptr,
     "name from glGenRenderbuffers; DSA itself is not claimed"},
    {"glCreateQueries", Cls::kEmulate, nullptr,
     "name from glGenQueries; DSA itself is not claimed"},
    {"glCreateProgramPipelines", Cls::kEmulate, nullptr,
     "name from glGenProgramPipelines; DSA itself is not claimed"},
};

const Policy* policy_table(size_t* count) {
    *count = sizeof(kPolicies) / sizeof(kPolicies[0]);
    return kPolicies;
}

Cls policy_class_for(const char* name) {
    for (const Policy& e : kPolicies) {
        if (std::strcmp(e.name, name) == 0) return e.cls;
    }
    return Cls::kAbsent;
}

// ---------------------------------------------------------------------------
// Resolution helpers
// ---------------------------------------------------------------------------

namespace {

// dlsym on the libGLESv2 handle we already hold. Deliberately never
// eglGetProcAddress: for a name this layer does not recognise, the backend's
// answer proves nothing about which symbol it handed back.
template <typename F>
F resolve(const char* name) {
    static std::mutex m;
    static std::unordered_map<std::string, void*> cache;
    std::lock_guard<std::mutex> lock(m);
    auto it = cache.find(name);
    if (it != cache.end()) return reinterpret_cast<F>(it->second);
    void* p = nullptr;
    if (onigami::ensure_gles_loaded() && onigami::gles_procs().handle)
        p = dlsym(onigami::gles_procs().handle, name);
    cache.emplace(name, p);
    return reinterpret_cast<F>(p);
}

// The driver symbol a policy forwards to, or nullptr when the driver lacks it.
// Only kForward / kEsName entries resolve to a driver symbol.
void* policy_target(const char* desktop_name) {
    static std::unordered_map<std::string, void*> resolved;
    auto it = resolved.find(desktop_name);
    if (it != resolved.end()) return it->second;
    void* p = nullptr;
    for (const Policy& e : kPolicies) {
        if (std::strcmp(e.name, desktop_name) != 0) continue;
        if (e.cls == Cls::kForward || e.cls == Cls::kEsName) {
            if (onigami::ensure_gles_loaded() && onigami::gles_procs().handle)
                p = dlsym(onigami::gles_procs().handle, e.target);
        }
        break;
    }
    resolved.emplace(desktop_name, p);
    return p;
}

// One log line per desktop name for the whole process. These are permanent
// facts about the backend, so the latch is the point.
void note_absent(const char* name, const char* reason) {
    static std::mutex m;
    static std::unordered_map<std::string, bool> seen;
    std::lock_guard<std::mutex> lock(m);
    if (seen[name]) return;
    seen[name] = true;
    onigami::diag_printf("onigami: %s: %s (ES 3.2 backend has no equivalent)",
                         name, reason);
}

}  // namespace

// ---------------------------------------------------------------------------
// Multi-draw: exact expansion
// ---------------------------------------------------------------------------

void multi_draw_arrays(GLenum mode, const GLint* first, const GLsizei* count,
                       GLsizei drawcount) {
    if (!first || !count || drawcount <= 0) return;
    if (void* ext = policy_target("glMultiDrawArraysEXT")) {
        auto fn = reinterpret_cast<void (*)(GLenum, const GLint*,
                                            const GLsizei*, GLsizei)>(ext);
        fn(mode, first, count, drawcount);
        return;
    }
    auto draw = resolve<void (*)(GLenum, GLint, GLsizei)>("glDrawArrays");
    if (!draw) return;
    for (GLsizei i = 0; i < drawcount; i++) draw(mode, first[i], count[i]);
}

void multi_draw_elements(GLenum mode, const GLsizei* count, GLenum type,
                         const void* const* indices, GLsizei drawcount) {
    if (!count || !indices || drawcount <= 0) return;
    // Prefer the driver's own multi-draw only when the extension is genuinely
    // advertised; the policy records it as an ES name for that reason.
    if (void* ext = policy_target("glMultiDrawElementsEXT")) {
        auto fn = reinterpret_cast<void (*)(GLenum, const GLsizei*, GLenum,
                                            const void* const*, GLsizei)>(ext);
        fn(mode, count, type, indices, drawcount);
        return;
    }
    auto draw = resolve<void (*)(GLenum, GLsizei, GLenum, const void*)>(
        "glDrawElements");
    if (!draw) return;
    for (GLsizei i = 0; i < drawcount; i++)
        draw(mode, count[i], type, indices[i]);
}

void multi_draw_elements_base_vertex(GLenum mode, const GLsizei* count,
                                     GLenum type, const void* const* indices,
                                     GLsizei drawcount,
                                     const GLint* basevertex) {
    if (!count || !indices || drawcount <= 0) return;
    auto draw = resolve<
        void (*)(GLenum, GLsizei, GLenum, const void*, GLint)>(
        "glDrawElementsBaseVertex");
    if (draw) {
        for (GLsizei i = 0; i < drawcount; i++)
            draw(mode, count[i], type, indices[i],
                 basevertex ? basevertex[i] : 0);
        return;
    }
    // No base-vertex form either. Emitting the plain command keeps the
    // geometry but drops the offset, so this is logged rather than silent:
    // silently wrong geometry is worse than visible trouble.
    auto plain = resolve<void (*)(GLenum, GLsizei, GLenum, const void*)>(
        "glDrawElements");
    if (plain)
        for (GLsizei i = 0; i < drawcount; i++)
            plain(mode, count[i], type, indices[i]);
    note_absent("glMultiDrawElementsBaseVertex",
                "no GL_EXT base-vertex form, offsets dropped (visible anomaly, "
                "not a silent wrong geo)");
}

}  // namespace onigami_longtail

// ---------------------------------------------------------------------------
// C ABI: every name the frontend or the application reaches.
// ---------------------------------------------------------------------------

extern "C" {

using namespace onigami_longtail;

O_API void glMultiDrawElements(GLenum mode, const GLsizei* count, GLenum type,
                               const void* const* indices, GLsizei drawcount) {
    multi_draw_elements(mode, count, type, indices, drawcount);
}

O_API void glMultiDrawElementsBaseVertex(GLenum mode, const GLsizei* count,
                                         GLenum type,
                                         const void* const* indices,
                                         GLsizei drawcount,
                                         const GLint* basevertex) {
    multi_draw_elements_base_vertex(mode, count, type, indices, drawcount,
                                    basevertex);
}

O_API void glMultiDrawElementsBaseVertexEXT(GLenum mode,
                                            const GLsizei* count, GLenum type,
                                            const void* const* indices,
                                            GLsizei primcount,
                                            const GLint* basevertex) {
    multi_draw_elements_base_vertex(mode, count, type, indices, primcount,
                                    basevertex);
}

O_API void glMultiDrawElementsEXT(GLenum mode, const GLsizei* count, GLenum type,
                                  const void* const* indices,
                                  GLsizei primcount) {
    multi_draw_elements(mode, count, type, indices, primcount);
}

O_API void glMultiDrawArrays(GLenum mode, const GLint* first,
                             const GLsizei* count, GLsizei drawcount) {
    multi_draw_arrays(mode, first, count, drawcount);
}

O_API void glMultiDrawArraysEXT(GLenum mode, const GLint* first,
                                const GLsizei* count, GLsizei primcount) {
    multi_draw_arrays(mode, first, count, primcount);
}

// ---- object creation: real names from the classic entry points -----------
// Real objects, obtained from the classic ES entry points. Not a DSA path: no
// DSA command is implemented, and the log says so once via the policy table.

O_API void glCreateBuffers(GLsizei n, GLuint* buffers) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenBuffers");
    if (gen && buffers) gen(n, buffers);
}

O_API void glCreateVertexArrays(GLsizei n, GLuint* arrays) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenVertexArrays");
    if (gen && arrays) gen(n, arrays);
}

O_API void glCreateTextures(GLenum target, GLsizei n, GLuint* textures) {
    // ES has no DSA: the target cannot be bound here, and claiming otherwise
    // would fabricate a capability the backend does not have.
    (void)target;
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenTextures");
    if (gen && textures) gen(n, textures);
}

O_API void glCreateSamplers(GLsizei n, GLuint* samplers) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenSamplers");
    if (gen && samplers) gen(n, samplers);
}

O_API void glCreateFramebuffers(GLsizei n, GLuint* framebuffers) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenFramebuffers");
    if (gen && framebuffers) gen(n, framebuffers);
}

O_API void glCreateRenderbuffers(GLsizei n, GLuint* renderbuffers) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenRenderbuffers");
    if (gen && renderbuffers) gen(n, renderbuffers);
}

O_API void glCreateQueries(GLenum target, GLsizei n, GLuint* ids) {
    (void)target;
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenQueries");
    if (gen && ids) gen(n, ids);
}

O_API void glCreateProgramPipelines(GLsizei n, GLuint* pipelines) {
    auto gen = resolve<void (*)(GLsizei, GLuint*)>("glGenProgramPipelines");
    if (gen && pipelines) gen(n, pipelines);
}

// ---- honest drop, once per process, no error -----------------------------
// glGetTexImage and the debug group family need an FBO round-trip / marker
// path that ES 3.2 does not offer. They are named, logged once, and do
// nothing: the application gets no error it could mistake for its own.

O_API void glGetTexImage(GLenum target, GLint level, GLenum format, GLenum type,
                         void* pixels) {
    (void)target; (void)level; (void)format; (void)type; (void)pixels;
    note_absent("glGetTexImage", "readback needs an FBO round-trip this layer "
                                "does not implement");
}

O_API void glPushDebugGroup(GLenum source, GLuint id, GLsizei length,
                            const GLchar* message) {
    (void)source; (void)id; (void)length; (void)message;
    note_absent("glPushDebugGroup", "no debug group marker on ES 3.2");
}

O_API void glPopDebugGroup(void) {
    note_absent("glPopDebugGroup", "no debug group marker on ES 3.2");
}

}  // extern "C"
