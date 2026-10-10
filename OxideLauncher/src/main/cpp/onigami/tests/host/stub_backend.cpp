// Host link stubs for the ONIGAMI long-tail policy test.
//
// Mirrors tests/host/stub_config.cpp: supplies just enough of the ONIGAMI
// backend for the policy table to be linked and asserted on a host with no
// EGL, no GLES and no driver present. Nothing here is a mock of GL behaviour;
// every symbol resolves to "no driver", which is exactly the state the policy
// table must still be valid for.
#include "onigami/backend.h"

#include <cstdarg>
#include <cstdio>

namespace onigami {

bool ensure_gles_loaded() { return false; }
bool ensure_egl_loaded() { return false; }

GlesProcs& gles_procs() {
    static GlesProcs p;
    return p;
}

EglProcs& egl_procs() {
    static EglProcs p;
    return p;
}

std::string data_dir() { return "/tmp"; }

void diag_write(const char*) {}
void diag_printf(const char*, ...) {}
void set_last_diag(const char*) {}
std::mutex& egl_mutex() { static std::mutex m; return m; }
std::string& cached_renderer() { static std::string s; return s; }
std::string& cached_version() { static std::string s; return s; }
void try_probe_renderer_version() {}
void* own_proc(const char*) { return nullptr; }

}  // namespace onigami
