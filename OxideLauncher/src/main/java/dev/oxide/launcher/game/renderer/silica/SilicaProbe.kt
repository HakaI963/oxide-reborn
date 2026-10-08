package dev.oxide.launcher.game.renderer.silica

/**
 * Kotlin-side port of the upstream fcdf914 principle.
 *
 * The native fix keeps probe-time GL_RENDERER/GL_VERSION copies and answers from
 * them (with a log) when no context is current instead of crashing on null.
 * This object is the Kotlin mirror: it caches the one EGL probe per process and
 * hands out null-safe strings for auto-tuning/UI, logging every fallback so a
 * "wrong GPU" selection is visible instead of silent.
 */
object SilicaProbe {
    @Volatile private var cachedRenderer: String? = null
    @Volatile private var cachedVersion: String? = null
    @Volatile private var fallbackCount: Int = 0

    fun noteProbe(renderer: String?, version: String?) {
        if (!renderer.isNullOrEmpty()) cachedRenderer = renderer
        if (!version.isNullOrEmpty()) cachedVersion = version
    }

    fun rendererOrProbe(live: String?): String {
        if (!live.isNullOrEmpty()) return live
        fallbackCount++
        return cachedRenderer?.takeIf { it.isNotEmpty() } ?: "<unknown>"
    }

    fun versionOrProbe(live: String?): String {
        if (!live.isNullOrEmpty()) return live
        fallbackCount++
        return cachedVersion?.takeIf { it.isNotEmpty() } ?: "<unknown>"
    }

    fun isAdreno(renderer: String?): Boolean =
        (renderer ?: cachedRenderer ?: "").contains("adreno", ignoreCase = true)

    fun fallbackCount(): Int = fallbackCount
}
