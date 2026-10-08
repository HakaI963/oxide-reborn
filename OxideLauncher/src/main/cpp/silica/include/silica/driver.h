#pragma once
// Silica driver loader. Own implementation; nothing from MobileGlues.
// Opens the system EGL/GLES libraries, resolves backend function pointers,
// and enforces the single-driver rule (both halves from the same place).
#include <stddef.h>
namespace silica::driver {
// Ensure the backend is open (idempotent, thread-safe). Returns true when
// both EGL and GLES handles are usable.
bool ensure();
// Resolve a backend symbol (egl* or gl*). Returns nullptr when unknown.
// When the name has a Silica wrapper, wrappers are preferred by our own
// eglGetProcAddress; this returns the raw backend pointer for forwarding.
void* resolve(const char* name);
// True when the loaded pair came from ANGLE halved against system (after the
// automatic repair both halves are the system driver and this is false).
bool angle_in_use();
// Human-readable backend identity for logs (file names that were opened).
const char* identity();
} // namespace silica::driver
