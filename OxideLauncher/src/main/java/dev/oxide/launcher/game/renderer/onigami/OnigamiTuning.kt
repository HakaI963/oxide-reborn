/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.onigami

/**
 * Onigami performance profiles.
 *
 * Every mode maps onto fields of the onigami.json schema that libonigami.so
 * reads at context init (profile / program_vault_mb / state_coalescing /
 * diagnostics). There are no decorative toggles: each field must change
 * backend behaviour or it does not belong here.
 *
 * - AUTO (default): balanced behaviour until per-GPU measurements justify a
 *   device-specific default.
 * - BALANCED: caller-supplied vault budget, coalescing and diagnostics as set.
 * - PERFORMANCE: same knobs; the profile name lets the backend prefer
 *   submission-trimming paths once they land (no effect invented today).
 * - QUALITY: retained for stored selections; no longer differentiates.
 * - OPTIMIZED: the only profile the writer emits since v1.16.0.
 *
 * Resolution scaling and frame generation are deliberately NOT options here:
 * no measured backend exists yet, and a checkbox without one would be fake.
 */
enum class OnigamiPerformanceMode { AUTO, BALANCED, PERFORMANCE, QUALITY, OPTIMIZED }

object OnigamiTuning {
    data class Resolved(
        val vaultMb: Int,
        val coalescing: Boolean,
        val diagnostics: Boolean,
        val renderScale: Int,
        val upscaleMode: Int,
    )

    fun resolve(
        vaultMb: Int,
        coalescing: Boolean,
        diagnostics: Boolean,
        renderScale: Int = 100,
        upscaleMode: Int = 0,
    ): Resolved {
        // v1.16.0: profiles no longer differentiate (writer pins OPTIMIZED).
        // Render scale and upscaler are direct controls with real backends.
        val v = vaultMb.coerceIn(0, 512)
        return Resolved(
            v,
            coalescing,
            diagnostics,
            renderScale.coerceIn(50, 100),
            upscaleMode.coerceIn(0, 1),
        )
    }

    /**
     * Own onigami.json schema for libonigami.so. This schema belongs to
     * Onigami alone: sharing a config format with another backend would
     * couple the two projects. Read once at context init (restart required).
     */
    fun toConfigJson(profile: OnigamiPerformanceMode, r: Resolved): String =
        "{\"onigami_version\":1" +
            ",\"profile\":\"" + profile.name + "\"" +
            ",\"program_vault_mb\":" + r.vaultMb +
            ",\"state_coalescing\":" + (if (r.coalescing) 1 else 0) +
            ",\"diagnostics\":" + (if (r.diagnostics) 1 else 0) +
            ",\"swap_interval\":" + 1 +
            ",\"render_scale\":" + (r.renderScale / 100f) +
            ",\"upscale_mode\":" + r.upscaleMode +
            ",\"frame_generation\":" + (if (r.frameGen) 1 else 0) +
            ",\"frame_gen_multiplier\":" + r.frameGenMultiplier + "}"
}
