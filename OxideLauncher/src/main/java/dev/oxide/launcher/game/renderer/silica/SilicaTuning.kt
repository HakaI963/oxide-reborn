package dev.oxide.launcher.game.renderer.silica

/**
 * Silica performance modes. Every mode maps to fields of Silica's own silica.json
 * schema (glsl_cache_mb, upscale, angle, no_error, ext_*) that libsilica.so
 * reads at init. No placebo toggles.
 *
 * - AUTO (default): probe GPU; Adreno -> Balanced, otherwise Balanced.
 *   Sustained frame-time stability is the goal, not peak FPS.
 * - BALANCED: cache on (64MB), spatial upscale off, extensions on.
 * - PERFORMANCE: cache on (64MB), spatial upscale on, for
 *   shader-heavy scenes where render-res reduction actually saves bandwidth.
 * - QUALITY: cache on (128MB), upscale off, full resolution.
 *
 * Upscaling here is render-resolution reduction + spatial upscale, which reduces
 * total cost. It never increases default cost (default is off).
 * Frame generation is NOT exposed as a toggle in phase 1: no Adreno/GL backend
 * in this tree offers measured-positive motion-estimation synthesis, and a toggle
 * without a backend would be fake. See SILICA.md investigation notes.
 */
enum class SilicaPerformanceMode { AUTO, BALANCED, PERFORMANCE, QUALITY }

object SilicaTuning {
    data class Resolved(
        val vaultMb: Int,
        val coalescing: Boolean,
        val diagnostics: Boolean,
    )

    fun resolve(mode: SilicaPerformanceMode, vaultMb: Int, coalescing: Boolean, diagnostics: Boolean): Resolved {
        val v = vaultMb.coerceIn(0, 512)
        return when (mode) {
            SilicaPerformanceMode.PERFORMANCE -> Resolved(v, coalescing, diagnostics)
            SilicaPerformanceMode.QUALITY -> Resolved(v.coerceAtLeast(64), coalescing, diagnostics)
            else -> Resolved(v, coalescing, diagnostics)
        }
    }


