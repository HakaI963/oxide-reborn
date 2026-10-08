package dev.oxide.launcher.game.renderer.silica

/**
 * Silica performance modes. Every mode maps to REAL backend config keys only
 * (maxGlslCacheSize, fsr1Setting, enableANGLE, enableNoError, ext toggles) that
 * the driven native tree reads at init. No placebo toggles.
 *
 * - AUTO (default): probe GPU; Adreno -> Balanced, otherwise Balanced.
 *   Sustained frame-time stability is the goal, not peak FPS.
 * - BALANCED: cache on (64MB), spatial upscale off, extensions on.
 * - PERFORMANCE: cache on (64MB), spatial upscale on (FSR Quality), for
 *   shader-heavy scenes where render-res reduction actually saves bandwidth.
 * - QUALITY: cache on (128MB), upscale off, full resolution.
 *
 * Upscaling here is render-resolution reduction + FSR upscale, which reduces
 * total cost. It never increases default cost (default is off).
 * Frame generation is NOT exposed as a toggle in phase 1: no Adreno/GL backend
 * in this tree offers measured-positive motion-estimation synthesis, and a toggle
 * without a backend would be fake. See SILICA.md investigation notes.
 */
enum class SilicaPerformanceMode { AUTO, BALANCED, PERFORMANCE, QUALITY }

object SilicaTuning {
    data class Resolved(
        val glslCacheMb: Int,
        val fsrLevel: Int, // 0=off, 2=quality (only real FSR levels used)
        val angle: Int, // 0=driver default (never force without measurement)
        val noError: Int, // 0=auto
        val extCompute: Boolean,
        val extTimerQuery: Boolean,
        val extDsa: Boolean,
    )

    fun resolve(mode: SilicaPerformanceMode, userCacheMb: Int, userUpscale: Boolean): Resolved {
        val m = mode // AUTO resolves to BALANCED until per-GPU measurements exist
        return when (m) {
            SilicaPerformanceMode.PERFORMANCE -> Resolved(
                glslCacheMb = userCacheMb.coerceIn(0, 512),
                fsrLevel = 2, extCompute = true, extTimerQuery = true, extDsa = true,
                angle = 0, noError = 0,
            )
            SilicaPerformanceMode.QUALITY -> Resolved(
                glslCacheMb = userCacheMb.coerceIn(0, 512).coerceAtLeast(64),
                fsrLevel = 0, extCompute = true, extTimerQuery = true, extDsa = true,
                angle = 0, noError = 0,
            )
            else -> Resolved(
                glslCacheMb = userCacheMb.coerceIn(0, 512),
                fsrLevel = if (userUpscale) 2 else 0,
                extCompute = true, extTimerQuery = true, extDsa = true,
                angle = 0, noError = 0,
            )
        }
    }

    /**
     * Own silica.json schema for libsilica.so. NOT the MobileGlues config.json
     * schema: sharing that schema would couple Silica to the old backend.
     * libsilica.so parses this file; until it exists the file documents intent
     * and is verified by unit tests. Restart required (read at context init).
     */
    fun toConfigJson(r: Resolved): String =
        "{\"silica_version\":1" +
            ",\"glsl_cache_mb\":" + r.glslCacheMb +
            ",\"upscale\":" + r.fsrLevel +
            ",\"angle\":" + r.angle +
            ",\"no_error\":" + r.noError +
            ",\"ext_compute\":" + (if (r.extCompute) 1 else 0) +
            ",\"ext_timer_query\":" + (if (r.extTimerQuery) 1 else 0) +
            ",\"ext_dsa\":" + (if (r.extDsa) 1 else 0) + "}"
}
