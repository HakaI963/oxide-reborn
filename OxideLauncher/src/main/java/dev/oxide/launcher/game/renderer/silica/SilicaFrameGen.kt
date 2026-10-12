/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.silica

import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * Silica frame-generation state (win-fg Vulkan layer).
 *
 * The layer publishes $SILICA_DATA_DIR/winfg_stats.json (~1Hz, see
 * SILICA_PATCHES.md P1): {"enabled","multiplier","native_frames",
 * "displayed_frames"(=native+gen, measured),"generated_frames","dropped",
 * "reason"}. That file is the only cross-process channel (no extra JNI).
 * Displayed FPS is NEVER native x multiplier: it comes from counter deltas.
 */
object SilicaFrameGen {
    /** Multipliers win-fg implements (2-4, flow recomputed per frame). */
    val SUPPORTED_MULTIPLIERS: List<Int> = listOf(2, 3, 4)

    fun isEnabled(): Boolean =
        AllSettings.silicaFgEnabled.getValue() &&
            AllSettings.silicaFgMult.getValue() in SUPPORTED_MULTIPLIERS

    fun selectedMultiplier(): Int = AllSettings.silicaFgMult.getValue()

    fun statusText(): String = when {
        !AllSettings.silicaFgEnabled.getValue() -> "Frame Generation: OFF"
        selectedMultiplier() !in SUPPORTED_MULTIPLIERS ->
            "Frame Generation: unsupported multiplier"
        else -> "Frame Generation: ON (" + selectedMultiplier() + "x)"
    }

    fun statsFile(): File = File(SilicaEnv.dataDir(), "winfg_stats.json")

    /** Latest layer-published stats, or null when absent/stale (>3s). */
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
                val q = "\"" + key + "\":\""
                val p = j.indexOf(q); if (p < 0) return ""
                val s = p + q.length
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
                reason = strAfter("reason"),
            )
        }.getOrNull()
    }

    /**
     * One-line diagnostics for the in-game Renderer tab (screenshot-able).
     * Config + layer package proof (manifest/.so presence) + live counters.
     */
    fun diagnosticsText(nowMs: Long = System.currentTimeMillis()): String {
        val cfg = "cfg=" + (if (isEnabled()) "ON(" + selectedMultiplier() + "x)" else "OFF")
        val soOk = runCatching {
            System.loadLibrary(SilicaIdentity.WINFG_LIBRARY.removePrefix("lib").removeSuffix(".so"))
            true
        }.getOrDefault(false)
        val manOk = runCatching { File(SilicaEnv.layersDir(), SilicaEnv.MANIFEST_NAME).exists() }.getOrDefault(false)
        return diagnosticsText(cfg, readStats(nowMs), soOk, manOk)
    }

    /** Pure diagnostics formatting. No estimates: absent stats say so. */
    fun diagnosticsText(cfg: String, stats: FrameGenStats?, soOk: Boolean, manOk: Boolean): String {
        val pkg = "so=" + (if (soOk) "ok" else "MISSING") +
            ",manifest=" + (if (manOk) "ok" else "absent")
        if (stats == null) {
            return cfg + " | " + pkg + " | layer idle (no Vulkan swapchain seen yet)"
        }
        return cfg + " | " + pkg + " | native=" + stats.nativeFrames +
            " displayed=" + stats.displayedFrames +
            " gen=" + stats.generatedFrames +
            " drop=" + stats.dropped +
            " [" + stats.reason + "]"
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
    val reason: String = "",
)
