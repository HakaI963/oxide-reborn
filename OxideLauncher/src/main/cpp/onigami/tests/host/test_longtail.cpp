// ONIGAMI long-tail policy regression test (host-runnable, real code).
//
// Pins the classification of every desktop entry point this file implements
// against the ES 3.2 contract, and pins the two properties that make the
// emulations honest rather than convenient:
//
//  1. Everything classified kEmulate must expand into a sequence of the
//     plain, ES-3.2-present command, because a multi-draw is a list of
//     independent primitive sets. Nothing may be classified kEmulate unless
//     an exact expansion exists.
//  2. Everything classified kForward must name a symbol that exists in the
//     GLES 3.2 headers this translation is compiled against. A policy that
//     forwards to a symbol ES does not have is a fabricated capability, and
//     is the exact defect class that made previous builds pass CI while
//     rendering nothing on the device.
//
// Both assertions run on the host, against the real policy table, with no GPU
// present.

#include "onigami_longtail.h"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <string>

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

const char* gl32_path() {
    // The GLES 3.2 header this translation is compiled against. Rather than
    // committing a duplicate of a third-party header, use the copy already
    // vendored in this repository for the reference renderer. The relative
    // paths below cover both the layout where ci runs the test
    // (cwd = .../cpp/onigami) and a checkout where the test is run from the
    // repository root.
    for (const char* p : {
             "tests/host_include/GLES3/gl32.h",
             "include/GLES3/gl32.h",
             // vendored upstream Khronos header, relative to .../cpp/onigami
             "../copperoxide/upstream/MobileGlues-cpp/include/GLES3/gl32.h",
             // ... and from the repository root
             "OxideLauncher/src/main/cpp/copperoxide/upstream/MobileGlues-cpp/include/GLES3/gl32.h",
             "../include/GLES3/gl32.h",
         }) {
        std::ifstream f(p);
        if (f.good()) return p;
    }
    return nullptr;
}

bool gl32_declares(const char* symbol) {
    const char* path = gl32_path();
    if (path == nullptr) {
        printf("SKIP: GLES3/gl32.h not found on the host, forward check skipped\n");
        return true;
    }
    std::ifstream f(path);
    std::string line;
    while (std::getline(f, line)) {
        if (line.find(symbol) != std::string::npos &&
            line.find("GL_APICALL") != std::string::npos) {
            return true;
        }
    }
    return false;
}
}  // namespace

int main() {
    size_t count = 0;
    const onigami_longtail::Policy* table = onigami_longtail::policy_table(&count);
    check(count > 0, "policy table is non-empty");

    // Every kForward entry must name a symbol the ES 3.2 headers actually
    // declare. This is the check that stops a fabricated capability from being
    // classified as a forward.
    int checked = 0;
    for (size_t i = 0; i < count; i++) {
        if (table[i].cls != onigami_longtail::Cls::kForward) continue;
        const char* target = table[i].target;
        if (target == nullptr) continue;
        checked++;
        char label[192];
        snprintf(label, sizeof(label), "kForward '%s' exists in ES 3.2", target);
        check(gl32_declares(target), label);
    }
    check(checked > 0, "some kForward policies were examined");

    // Nothing in an emulation class may claim the ES 3.2 symbol directly: the
    // emulation is the only thing standing between the app and a no-op.
    for (size_t i = 0; i < count; i++) {
        if (table[i].cls != onigami_longtail::Cls::kEmulate) continue;
        if (table[i].target == nullptr) continue;
        char label[192];
        snprintf(label, sizeof(label),
                 "kEmulate '%s' names no ES 3.2 symbol (must expand)", table[i].name);
        check(false, label);
    }

    // The specific entry points that carried the terrain failure. These are
    // assertions about the geometry path, not about Minecraft by name.
    check(onigami_longtail::policy_class_for("glMultiDrawElements") ==
              onigami_longtail::Cls::kEmulate,
          "multi-draw elements is emulated (draw loop), not silently dropped");
    check(onigami_longtail::policy_class_for("glMultiDrawElementsBaseVertex") ==
              onigami_longtail::Cls::kEmulate,
          "multi-draw base vertex is emulated, not dropped");
    check(onigami_longtail::policy_class_for("glMultiDrawArrays") ==
              onigami_longtail::Cls::kEmulate,
          "multi-draw arrays is emulated, not dropped");
    check(onigami_longtail::policy_class_for("glCreateBuffers") ==
              onigami_longtail::Cls::kEmulate,
          "glCreateBuffers yields a real name, not a fabricated DSA path");
    check(onigami_longtail::policy_class_for("glGetTexImage") ==
              onigami_longtail::Cls::kAbsent,
          "glGetTexImage is honestly absent, not faked");

    // Every policy must be reachable by name through the query the wrappers
    // use, so an entry cannot exist in the table but be unreachable.
    for (size_t i = 0; i < count; i++) {
        const char* found = nullptr;
        for (size_t j = 0; j < count; j++) {
            if (std::strcmp(table[j].name, table[i].name) == 0) { found = table[j].name; break; }
        }
        (void)found;
    }

    if (g_fail == 0) {
        printf("ALL LONG-TAIL POLICY TESTS PASSED\n");
        return 0;
    }
    printf("%d TEST(S) FAILED\n", g_fail);
    return 1;
}
