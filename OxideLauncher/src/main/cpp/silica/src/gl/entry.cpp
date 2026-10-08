// Silica GL entry-point layer. Own dispatch table (no MobileGlues loader).
// Every entry validates context-current state first: with nothing current the
// call is logged + skipped (stage 9 behavior), never a null dereference.
#include <android/log.h>
#define SLOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, "silica", __VA_ARGS__)
namespace silica::gl {
// Stage 3 fills the per-context dispatch table; skeleton exports the guard.
static bool s_have_context = false;
void note_context(bool have) { s_have_context = have; }
bool guarded(const char* what) {
    if (!s_have_context) {
        SLOG(ERROR, "silica %s with no current context; skipped (see eglMakeCurrent log for cause)", what);
        return false;
    }
    return true;
}
} // namespace silica::gl
