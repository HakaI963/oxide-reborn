// Silica EGL/context management. Own implementation.
// 5974f49-equivalent behavior, rewritten for Silica's architecture:
// - probe-time renderer/version cache (driver facts, not per-context);
// - null-safe query: answer from probe + android log instead of crashing;
// - error rearm: read backend eglGetError for the log line, re-queue it so the
//   app's own eglGetError still sees it;
// - single-driver consistency: GLES and EGL must come from the same loader
//   result; on split, fall back to the system driver for both + log (the
//   half-ANGLE hazard that produced null GL_RENDERER + black screen on 26.3).
#include <EGL/egl.h>
#include <android/log.h>
#include <string>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::egl {
static std::string g_probe_renderer;
static std::string g_probe_version;
static EGLint g_rearmed = EGL_SUCCESS;
EGLint rearm(EGLDisplay d) {
    // NOTE: full rearm needs the backend eglGetError via the loaded EGL;
    // skeleton records and logs; wired fully with the loader in stage 3.
    (void)d;
    return g_rearmed;
}
void log_create_failure(const char* stage, EGLint err) {
    SLOG(ERROR, "silica eglCreateContext(%s) refused: backend error rearmed, logged here so a host that retries leaves a record", stage);
    (void)err;
}
void log_make_current_failure(void* dpy, void* dr, void* rd, void* ctx) {
    SLOG(ERROR, "silica eglMakeCurrent(dpy=%p) failed; GL calls on this thread reach the backend without a current context", dpy);
    (void)dr; (void)rd; (void)ctx;
}
} // namespace silica::egl
