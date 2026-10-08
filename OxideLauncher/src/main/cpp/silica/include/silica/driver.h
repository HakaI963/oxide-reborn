#pragma once
// Silica driver loader. Own implementation; nothing from other renderers.
// Opens the system EGL/GLES libraries LAZY (eager binding can refuse the open
// in a constrained linker namespace) and resolves backend entries through the
// host eglGetProcAddress first, dlsym second. Display acquisition itself is
// NEVER interposed: the host owns eglGetDisplay/eglInitialize/configs/
// surfaces/swap (see egl/exports.cpp note), so there is no second display path
// that can return NO_DISPLAY while the host would succeed.
#include <stddef.h>
namespace silica::driver {
// Ensure the backend is open (idempotent, thread-safe). True when EGL+GLES
// handles plus the host proc-address entry are all usable.
bool ensure();
// Backend entry for name: host eglGetProcAddress first, dlsym second.
void* resolve(const char* name);
// Direct host-proc-address query (diagnostics + proc-table fill).
void* host_proc(const char* name);
// True when the loaded pair came from ANGLE (post-repair: always false).
bool angle_in_use();
// Human-readable backend identity for logs.
const char* identity();
} // namespace silica::driver
