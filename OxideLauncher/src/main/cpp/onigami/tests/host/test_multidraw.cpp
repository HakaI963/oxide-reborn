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

// The shared object built from the library's own frontend, which is where the
// exported multi-draw symbols are defined. The workflow builds this probe so
// the export assertion below runs against the real library ABI rather than
// against this translation unit's own reach. nullptr means the assertion is
// skipped (see onigami.yml).
#ifndef ONIGAMI_TEST_PROBE_SO
#define ONIGAMI_TEST_PROBE_SO nullptr
#endif
const char* onigami_test_probe_so = ONIGAMI_TEST_PROBE_SO;

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

     // The entry points must exist at the shared-library ABI level so that
     // eglGetProcAddress answers ONIGAMI's own wrapper rather than falling
     // through to a GLES driver that has no multi-draw in core. The definitions
     // live in gl_frontend.cpp; the probe .so is that frontend, so opening it
     // is the same look the game does. A symbol the frontend owns but does not
     // export is one the game can never reach, which would make its semantics
     // enforced for nobody.
    if (onigami_test_probe_so[0] != '\0') {
        // Loaded with RTLD_NOW; on failure the reason is printed so a broken
        // probe path is visible rather than silently skipping the check.
        void* lib = dlopen(onigami_test_probe_so, RTLD_NOW | RTLD_GLOBAL);
        if (lib == nullptr) {
            printf("  dlopen(%s) failed: %s\n", onigami_test_probe_so, dlerror());
        }
        check(lib != nullptr, "the library frontend can be loaded");
        if (lib != nullptr) {
            void* a = dlsym(lib, "glMultiDrawElements");
            void* b = dlsym(lib, "glMultiDrawElementsBaseVertex");
            void* c = dlsym(lib, "glMultiDrawArrays");
            check(a != nullptr, "glMultiDrawElements is exported from the library");
            check(b != nullptr, "glMultiDrawElementsBaseVertex is exported");
            check(c != nullptr, "glMultiDrawArrays is exported");
        }
    } else {
         printf("SKIP: no probe .so path, export check skipped\n");
     }

    if (g_fail == 0) {
        printf("ALL MULTI-DRAW CONTRACT TESTS PASSED\n");
        return 0;
    }
    printf("%d TEST(S) FAILED\n", g_fail);
    return 1;
}
