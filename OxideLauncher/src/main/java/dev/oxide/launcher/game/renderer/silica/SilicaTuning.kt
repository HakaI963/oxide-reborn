package dev.oxide.launcher.game.renderer.silica

/**
 * Silica performance profiles. Every mode maps to fields of Silica's own
 * silica.json schema (profile/program_vault_mb/state_coalescing/diagnostics)
 * that libsilica.so reads at init. No placebo toggles.
 *
 * - AUTO (default): starts balanced until per-GPU measurements exist.
 * - BALANCED: user vault budget, coalescing and diagnostics as set.
 * - PERFORMANCE: same knobs; the profile name lets the backend prefer
 *   submission-trimming paths once they land (no effect invented today).
 * - QUALITY: vault floor of 64MB so program reuse stays on.
 *
 * Upscaling and frame generation are NOT options here: no measured backend
 * exists, and a toggle without one would be fake. See SILICA.md.
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

    /**
     * Own silica.json schema for libsilica.so. NOT any other backend's config
     * schema: sharing one would couple Silica to that backend. Restart
     * required (read at context init).
     */
    fun toConfigJson(profile: SilicaPerformanceMode, r: Resolved): String =
        "{\"silica_version\":1" +
            ",\"profile\":\"" + profile.name + "\"" +
            ",\"program_vault_mb\":" + r.vaultMb +
            ",\"state_coalescing\":" + (if (r.coalescing) 1 else 0) +
            ",\"diagnostics\":" + (if (r.diagnostics) 1 else 0) + "}"
}
