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
 * - QUALITY: vault floor of 64 MB so program reuse stays switched on.
 *
 * Resolution scaling and frame generation are deliberately NOT options here:
 * no measured backend exists yet, and a checkbox without one would be fake.
 */
enum class OnigamiPerformanceMode { AUTO, BALANCED, PERFORMANCE, QUALITY }

object OnigamiTuning {
    data class Resolved(
        val vaultMb: Int,
        val coalescing: Boolean,
        val diagnostics: Boolean,
    )

    fun resolve(
        mode: OnigamiPerformanceMode,
        vaultMb: Int,
        coalescing: Boolean,
        diagnostics: Boolean,
    ): Resolved {
        val v = vaultMb.coerceIn(0, 512)
        return when (mode) {
            OnigamiPerformanceMode.PERFORMANCE -> Resolved(v, coalescing, diagnostics)
            OnigamiPerformanceMode.QUALITY -> Resolved(v.coerceAtLeast(64), coalescing, diagnostics)
            else -> Resolved(v, coalescing, diagnostics)
        }
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
            ",\"diagnostics\":" + (if (r.diagnostics) 1 else 0) + "}"
}
