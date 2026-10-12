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

import dev.oxide.launcher.game.renderer.onigami.OnigamiFrameGeneration
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * ONIGAMI frame generation (v1.17.0, LSFG-VK derived).
 *
 * Integration (how it actually reaches the display):
 * - The game's real present path is the JNI bridge (ctxbridges), which
 *   resolves system EGL directly, so libonigami's own eglSwapBuffers wrapper
 *   never fires there. When ONIGAMI is active the launcher sets
 *   ONIGAMI_PRESENT_HOOK and the bridge calls onigami_hook_swap_buffers()
 *   around its backend swap (fallback = direct swap). Display, contexts and
 *   surfaces stay on the proven bridge path; only pre/post swap work is
 *   added. The native backend retains frames GPU-side and presents mult-1
 *   real interpolated frames per native frame (2x/3x/4x), each counted only
 *   on a confirmed swap.
 * - Settings apply LIVE: every FG change rewrites onigami.json immediately
 *   (OnigamiRenderer.refreshNativeConfig) and the native backend re-reads it
 *   (~1Hz mtime watch). No restart for frame generation.
 * - Native per-process stats are published at
 *   <dataDir>/framegen_stats.json ~1Hz for the Advanced FPS meter; the file
 *   is the only cross-process channel (no extra JNI, no hot-path sync).
 * - Not applicable on phones (not exposed): VR stereo views, HDR swapchain
 *   formats. The dispatched interpolation stage is blend + scene-change
 *   guard; full Lucas-Kanade motion stays staged pending Adreno 825
 *   profiling. See docs/ONIGAMI_LSFG_V1_17_0.md.
 */
object OnigamiFrameGen {
    /** Multipliers with a real backend in v1.17.0. */
    val SUPPORTED_MULTIPLIERS: List<Int> = listOf(2, 3, 4)

    fun isEnabled(): Boolean =
        AllSettings.onigamiFrameGen.getValue() &&
            AllSettings.onigamiFrameGenMultiplier.getValue() in SUPPORTED_MULTIPLIERS

    fun selectedMultiplier(): Int = AllSettings.onigamiFrameGenMultiplier.getValue()

    fun statusText(): String = when {
        !AllSettings.onigamiFrameGen.getValue() -> "Frame Generation: OFF"
        selectedMultiplier() !in SUPPORTED_MULTIPLIERS ->
            "Frame Generation: unsupported multiplier"
        else -> "Frame Generation: ON (" + selectedMultiplier() + "x)"
    }

    fun dataDir(): File = File(PathManager.DIR_FILES_PRIVATE, OnigamiIdentity.DATA_DIR)

    fun statsFile(): File = File(dataDir(), "framegen_stats.json")

    /** Latest native-published stats, or null when absent/stale (>3s). */
    fun readStats(nowMs: Long = System.currentTimeMillis()): FrameGenStats? {
        val f = statsFile()
        if (!f.exists()) return null
        if (nowMs - f.lastModified() > 3000L) return null
        return runCatching {
            val j = f.readText()
            fun intAfter(key: String, d: Int): Int {
                val p = j.indexOf(key); if (p < 0) return d
                val c = j.indexOf(':', p); if (c < 0) return d
                return j.substring(c + 1).trim().takeWhile { it.isDigit() || it == '-' }.toIntOrNull() ?: d
            }
            fun longAfter(key: String, d: Long): Long {
                val p = j.indexOf(key); if (p < 0) return d
                val c = j.indexOf(':', p); if (c < 0) return d
                return j.substring(c + 1).trim().takeWhile { it.isDigit() }.toLongOrNull() ?: d
            }
            fun strAfter(key: String): String {
                val p = j.indexOf("\"" + key + "\":\""); if (p < 0) return ""
                val s = p + key.length + 4
                val e = j.indexOf('"', s); if (e < 0) return ""
                return j.substring(s, e)
            }
            FrameGenStats(
                enabled = intAfter("enabled", 0) == 1,
                multiplier = intAfter("multiplier", 2),
                nativeFrames = longAfter("native_frames", 0L),
                displayedFrames = longAfter("displayed_frames", 0L),
                generatedFrames = longAfter("generated_frames", 0L),
                dropped = longAfter("dropped", 0L),
                genMsX100 = longAfter("gen_ms_x100", 0L),
                reason = strAfter("reason"),
            )
        }.getOrNull()
    }

    fun asPipeline(): OnigamiFrameGeneration = object : OnigamiFrameGeneration {
        override fun supported(): Boolean = true
        override fun reason(): String = "2x/3x/4x EGL interpolation (LSFG-VK derived), live apply"
        override fun enabled(): Boolean = isEnabled()
    }

    /** Pure helper: per-second rates from monotonic counters. No estimates. */
    fun rates(prev: FrameGenStats, cur: FrameGenStats, dtSec: Double): Pair<Double, Double> {
        if (dtSec <= 0.0) return 0.0 to 0.0
        val native = (cur.nativeFrames - prev.nativeFrames).coerceAtLeast(0L) / dtSec
        val displayed = (cur.displayedFrames - prev.displayedFrames).coerceAtLeast(0L) / dtSec
        return native to displayed
    }
}

data class FrameGenStats(
    val enabled: Boolean,
    val multiplier: Int,
    val nativeFrames: Long,
    val displayedFrames: Long,
    val generatedFrames: Long,
    val dropped: Long,
    val genMsX100: Long = 0L,
    val reason: String = "",
)
