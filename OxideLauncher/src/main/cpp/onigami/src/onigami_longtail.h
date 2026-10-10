// ONIGAMI long-tail entry-point policy.
//
// The classification is deliberately in its own dependency-free header so it
// can be asserted by a host test that has no EGL/GLES headers, no driver, and
// no GPU. The policy is the contract; the wrappers in gl_longtail.cpp are just
// how each class is carried out.
//
//   kForward  ES 3.2 has this exact entry point. Forward it.
//   kEsName   ES 3.2 has it under a different name. Forward to that symbol.
//   kEmulate  No ES equivalent, but an exact emulation exists. Emulate.
//   kAbsent   No ES equivalent and no honest emulation. Log once, do nothing.
//              Never answer a null pointer: that is a crash, and a crashed
//              launcher cannot report anything.
//
// The multi-draw entry points are declared here too, because gl_frontend.cpp
// delegates to them: one definition, two call sites, no per-name duplication.
#pragma once
#include <cstddef>

typedef unsigned int GLenum;
typedef int GLsizei;
typedef int GLint;

namespace onigami_longtail {

enum class Cls { kForward, kEsName, kEmulate, kAbsent };

struct Policy {
    const char* name;    // desktop name the application calls
    Cls cls;
    const char* target;  // driver symbol to resolve for kForward / kEsName
    const char* reason;  // justification: logged on device, asserted on host
};

// Defined in gl_longtail.cpp so there is exactly one table.
const Policy* policy_table(size_t* count);

// The class a desktop name is carried out as. kAbsent for a name this layer
// does not classify at all, which is distinct from "unsupported": an
// unclassified name is a gap to be closed, not a decision.
Cls policy_class_for(const char* name);

// Multi-draw expansion (exact; see the note in gl_longtail.cpp).
void multi_draw_arrays(GLenum mode, const GLint* first, const GLsizei* count,
                       GLsizei drawcount);
void multi_draw_elements(GLenum mode, const GLsizei* count, GLenum type,
                         const void* const* indices, GLsizei drawcount);
void multi_draw_elements_base_vertex(GLenum mode, const GLsizei* count,
                                     GLenum type, const void* const* indices,
                                     GLsizei drawcount,
                                     const GLint* basevertex);

}  // namespace onigami_longtail
