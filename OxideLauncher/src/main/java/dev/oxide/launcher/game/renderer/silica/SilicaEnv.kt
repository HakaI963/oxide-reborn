package dev.oxide.launcher.game.renderer.silica

/**
 * Silica launch environment.
 *
 * Only keys the Silica pipeline actually reads are emitted (verified against
 * the native tree Silica drives + GameLauncher/SilicaProbe). No placebo keys.
 * Base map is fixed-size for zero per-launch churn; data-dir map is separate
 * so the common path allocates once.
 */
object SilicaEnv {
    const val KEY_LIBGL_ES = "LIBGL_ES"
    const val KEY_LIBGL_EGL = "LIBGL_EGL"
    const val KEY_COUNT_LAUNCH = "MG_COUNT_LAUNCH"
    const val KEY_FLAVOR = "OXIDE_RENDERER_FLAVOR"
    const val KEY_DATA_DIR = "MG_DIR_PATH"

    fun baseEnv(): Map<String, String> = mapOf(
        KEY_LIBGL_ES to "3",
        KEY_LIBGL_EGL to SilicaIdentity.EGL_LIBRARY,
        KEY_COUNT_LAUNCH to "1",
        KEY_FLAVOR to SilicaIdentity.FLAVOR,
    )

    /**
     * Silica ALWAYS sets its data dir (unlike Copper Oxide's optional switch).
     * A custom dir bypasses the upstream "unsupported launcher" clamp on its own,
     * so Silica gets full config (cache size, FSR, extensions) without any
     * native identity spoofing. Blank dir -> empty (never emit empty values).
     */
    fun dataDirEnv(privateDir: String): Map<String, String> {
        if (privateDir.isBlank()) return emptyMap()
        return mapOf(KEY_DATA_DIR to privateDir)
    }

    fun merged(privateDir: String): Map<String, String> =
        baseEnv() + dataDirEnv(privateDir)
}
