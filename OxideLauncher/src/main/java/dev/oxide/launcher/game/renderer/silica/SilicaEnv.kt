package dev.oxide.launcher.game.renderer.silica

/**
 * Silica launch environment.
 *
 * HARD RULE: only Oxide-integration keys Silica itself reads. No MG_* keys
 * (those belong to the MobileGlues-derived driver and would couple Silica to
 * it). SILICA_* keys are consumed by libsilica.so (own backend) once built;
 * until then they are recorded in the launch log for verification.
 */
object SilicaEnv {
    const val KEY_LIBGL_ES = "LIBGL_ES"
    const val KEY_FLAVOR = "OXIDE_RENDERER_FLAVOR"
    const val KEY_DATA_DIR = "SILICA_DATA_DIR"
    const val KEY_MODE = "SILICA_MODE"

    fun baseEnv(mode: String): Map<String, String> = mapOf(
        KEY_LIBGL_ES to "3",
        KEY_FLAVOR to SilicaIdentity.FLAVOR,
        KEY_MODE to mode,
    )

    fun dataDirEnv(privateDir: String): Map<String, String> {
        if (privateDir.isBlank()) return emptyMap()
        return mapOf(KEY_DATA_DIR to privateDir)
    }

    fun merged(mode: String, privateDir: String): Map<String, String> =
        baseEnv(mode) + dataDirEnv(privateDir)
}
