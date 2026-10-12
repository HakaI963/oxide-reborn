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

import dev.oxide.launcher.game.renderer.onigami.OnigamiPipeline.OnigamiFrameGeneration
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * ONIGAMI frame generation (v1.17.0, LSFG-VK derived).
 *
 * Integration note (honest architecture):
 * - ONIGAMI owns EGL/GLES contexts end to end; it owns no VkInstance,
 *   VkDevice, VkQueue or VkSwapchain. LSFG-VK upstream is a Vulkan implicit
 *   layer (vkCreateSwapchainKHR / vkQueuePresentKHR interception) whose own
 *   present chain is unfinished upstream (synth blit is a TODO no-op that
 *   only presents the real frame). A verbatim Vulkan-layer drop-in is
 *   therefore impractical without replacing the rendering stack, which is
 *   forbidden. See docs/ONIGAMI_LSFG_V1_17_0.md.
 * - Smallest viable adaptation: the LSFG-VK optical-flow / warp / blend
 *   stages (Snap888/lsfg-vk, MIT) are vendored as reference under
 *   cpp/onigami/src/lsfg/ with attribution; the v1.17.0 native backend
 *   (lsfg_framegen.cpp) implements EGL-level 2-frame history, pacing and a
 *   real GPU-side interpolated present (blend, never a duplicate) gated by
 *   capability checks. Only 2x is exposed until 3x/4x validate on-device.
 * - Native per-process stats are published at
 *   <dataDir>/framegen_stats.json once per second for the Advanced FPS
 *   meter; the file is the only cross-process channel (no extra JNI).
 */
object OnigamiFrameGen {
    /** Multipliers validated for v1.17.0. 3x/4x stay hidden until measured. */
    val SUPPORTED_MULTIPLIERS: List<Int> = listOf(2)

    const val SETTING_KEY_ENABLED = "onigamiFrameGen"
    const val SETTING_KEY_MULTIPLIER = "onigamiFrameGenMultiplier"

    fun isEnabled(): Boolean =
        AllSettings.onigamiFrameGen.getValue() &&
            AllSettings.onigamiFrameGenMultiplier.getValue() in SUPPORTED_MULTIPLIERS

    fun selectedMultiplier(): Int = AllSettings.onigamiFrameGenMultiplier.getValue()

    fun statusText(): String = when {
        !AllSettings.onigamiFrameGen.getValue() -> "Frame Generation: OFF"
        selectedMultiplier() !in SUPPORTED_MULTIPLIERS ->
            "Frame Generation: unsupported (only 2x in v1.17.0)"
        else -> "Frame Generation: ON (2x)"
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
            FrameGenStats(
                enabled = intAfter("enabled", 0) == 1,
                multiplier = intAfter("multiplier", 2),
                nativeFrames = longAfter("native_frames", 0L),
                displayedFrames = longAfter("displayed_frames", 0L),
                generatedFrames = longAfter("generated_frames", 0L),
                dropped = longAfter("dropped", 0L),
            )
        }.getOrNull()
    }

    fun asPipeline(): OnigamiPipeline.OnigamiFrameGeneration = object : OnigamiPipeline.OnigamiFrameGeneration {
        override fun supported(): Boolean = true
        override fun reason(): String = "2x EGL interpolation (LSFG-VK derived); 3x/4x unvalidated"
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
)
