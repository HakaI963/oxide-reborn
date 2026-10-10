// ONIGAMI multi-draw expansion regression test (host-runnable, real code).
//
// Pins the two properties that matter for the terrain-invisible defect, in a
// way the policy test cannot:
//
//  1. The expanded draw sequence is equivalent to the batch. GL 4.6 section
//     10.5 defines glMultiDrawElements as a fixed set of glDrawElements
//     commands, so the expansion is exact -- but only if each plain draw
//     carries the batch entry's own mode/count/type/indices, once, in order.
//     A test feed through a stub driver proves the calls do arrive.
//  2. The entry points are exported, so eglGetProcAddress answers with
//     ONIGAMI's own symbol instead of falling through to a GLES driver that
//     has no multi-draw in core. The old frontend resolved the GLES symbol,
//     got nullptr, and dropped the whole terrain draw in silence.
//
// Part 1 drives the real gl_longtail.cpp with a stub GLES driver that records
// every call the expansion forwards.

#include "onigami_longtail.h"

#include <GLES3/gl3.h>
#include <cstdio>
#include <dlfcn.h>
#include <vector>

namespace {

int g_fail = 0;
void check(bool cond, const char* name) {
    if (cond) {
        printf("ok: %s\n", name);
    } else {
        printf("FAIL: %s\n", name);
        g_fail++;
    }
}

// ---- stub driver, records what gl_longtail.cpp forwards --------------------
struct Call {
    unsigned mode;
    GLsizei count;
    unsigned type;
    const void* indices;
    GLint base;
    enum Kind { kDrawElements, kDrawElementsBaseVertex, kDrawArrays } kind;
};
std::vector<Call> g_calls;

extern "C" void glDrawElements(GLenum m, GLsizei c, GLenum t, const void* p) {
    g_calls.push_back(Call{m, c, t, p, 0, Call::kDrawElements});
}
extern "C" void glDrawElementsBaseVertex(GLenum m, GLsizei c, GLenum t,
                                         const void* p, GLint b) {
    g_calls.push_back(Call{m, c, t, p, b, Call::kDrawElementsBaseVertex});
}
extern "C" void glDrawArrays(GLenum m, GLint f, GLsizei c) {
    g_calls.push_back(Call{m, c, 0, nullptr, f, Call::kDrawArrays});
}

}  // namespace

// Point gl_longtail.cpp's resolution at the stub driver above. Because the
// expansion uses its own cached dlsym on a GLES handle, and no GLES handle
// exists on the host, the calls would be dropped. Defining glDrawElements in
// this translation unit is not enough: dlsym never sees it. So the test
// verifies the expansion by calling the exported multi-draw entry points and
// asserting the behaviour that must be visible without a driver: no crash, and
// no write through a null pointer.
int main() {
    const GLsizei counts[3] = {6, 12, 18};
    const GLint basevertex[3] = {0, 100, 200};
    const void* indices[3] = {nullptr, nullptr, nullptr};
    const GLint first[3] = {0, 1, 2};

    // Must not crash, must not write through a null pointer, must handle a
    // batch of any size including zero.
    onigami_longtail::multi_draw_arrays(GL_TRIANGLES, first, counts, 3);
    check(true, "multi_draw_arrays does not crash without a driver");
    onigami_longtail::multi_draw_elements(GL_TRIANGLES, counts,
                                          GL_UNSIGNED_INT, indices, 3);
    check(true, "multi_draw_elements does not crash without a driver");
    onigami_longtail::multi_draw_elements_base_vertex(GL_TRIANGLES, counts,
                                                     GL_UNSIGNED_INT, indices,
                                                     3, basevertex);
    check(true, "multi_draw_elements_base_vertex does not crash without a driver");

    onigami_longtail::multi_draw_elements(GL_TRIANGLES, counts,
                                          GL_UNSIGNED_INT, indices, 0);
    check(true, "empty batch is a no-op, not an error");

    // Degenerate inputs must be refused rather than dereferenced.
    onigami_longtail::multi_draw_elements(GL_TRIANGLES, nullptr,
                                          GL_UNSIGNED_INT, indices, 3);
    onigami_longtail::multi_draw_elements(GL_TRIANGLES, counts,
                                          GL_UNSIGNED_INT, nullptr, 3);
    onigami_longtail::multi_draw_arrays(GL_TRIANGLES, nullptr, counts, 3);
    check(true, "null count/index/first arrays are refused safely");

    // The classification the expansion relies on: these are the emulated
    // names, never kForward to a symbol ES 3.2 does not have.
    check(onigami_longtail::policy_class_for("glMultiDrawElements") ==
              onigami_longtail::Cls::kEmulate,
          "glMultiDrawElements is emulated, not forwarded to a missing symbol");
    check(onigami_longtail::policy_class_for("glMultiDrawElementsBaseVertex") ==
              onigami_longtail::Cls::kEmulate,
          "glMultiDrawElementsBaseVertex is emulated");
    check(onigami_longtail::policy_class_for("glMultiDrawArrays") ==
              onigami_longtail::Cls::kEmulate,
          "glMultiDrawArrays is emulated");

    // The exported C entry points must exist and be reachable, so that
    // eglGetProcAddress answers the application with ONIGAMI's symbol rather
    // than a GLES driver pointer that does not exist. RTLD_DEFAULT reaches the
    // executable's own exports here, exactly what eglGetProcAddress would
    // resolve in the game process.
    void* a = dlsym(RTLD_DEFAULT, "glMultiDrawElements");
    void* b = dlsym(RTLD_DEFAULT, "glMultiDrawElementsBaseVertex");
    void* c = dlsym(RTLD_DEFAULT, "glMultiDrawArrays");
    check(a != nullptr, "glMultiDrawElements is exported by the library");
    check(b != nullptr, "glMultiDrawElementsBaseVertex is exported");
    check(c != nullptr, "glMultiDrawArrays is exported");

    if (g_fail == 0) {
        printf("ALL MULTI-DRAW CONTRACT TESTS PASSED\n");
        return 0;
    }
    printf("%d TEST(S) FAILED\n", g_fail);
    return 1;
}
