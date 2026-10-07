/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.renderer.copperoxide

/**
 * Device-specific tuning policy for Copper Oxide.
 *
 * Pure JVM functions: no Build.*, no ActivityManager, no driver calls, so
 * every branch is unit-testable. Callers pass in the already-observed
 * device facts (GPU vendor strings, RAM class); this file only decides.
 *
 * Direction is always fail-safe: unknown devices get the neutral defaults,
 * 0 (disabled) is never overridden, and ranges are clamped to the verified
 * driver schema (0..512 MB cache, 0..4 FSR).
 */
enum class CopperOxideGpuVendor { ADRENO, MALI, POWERVR, XCIPSE, ARM, UNKNOWN }

object CopperOxideDeviceTuning {
    const val GLSL_CACHE_MB_MIN: Int = 0
    const val GLSL_CACHE_MB_MAX: Int = 512
    const val GLSL_CACHE_MB_DEFAULT: Int = 64
    const val GLSL_CACHE_MB_LOW_RAM: Int = 32
    const val GLSL_CACHE_MB_HIGH_RAM: Int = 128

    /** Case-insensitive vendor match on GL_VENDOR / GL_RENDERER strings. */
    fun detectGpuVendor(vendor: String, renderer: String): CopperOxideGpuVendor {
        val hay = (vendor + " " + renderer).lowercase()
        return when {
            "adreno" in hay || "qualcomm" in hay -> CopperOxideGpuVendor.ADRENO
            "mali" in hay -> CopperOxideGpuVendor.MALI
            "powervr" in hay || "img" in hay && "rogue" in hay -> CopperOxideGpuVendor.POWERVR
            "xclipse" in hay || "samsung" in hay && "xclipse" in hay -> CopperOxideGpuVendor.XCIPSE
            // ARM vendor string without Mali renderer still tunes like Mali.
            "arm" in hay -> CopperOxideGpuVendor.ARM
            else -> CopperOxideGpuVendor.UNKNOWN
        }
    }

    /** Neutral default cache size for a RAM class. 0/unknown RAM -> default. */
    fun defaultCacheMbForDevice(totalRamMb: Long?): Int {
        if (totalRamMb == null || totalRamMb <= 0) return GLSL_CACHE_MB_DEFAULT
        return when {
            totalRamMb <= 3072 -> GLSL_CACHE_MB_LOW_RAM
            totalRamMb >= 8192 -> GLSL_CACHE_MB_HIGH_RAM
            else -> GLSL_CACHE_MB_DEFAULT
        }
    }

    /**
     * Clamp a user cache choice and cap it on low-RAM devices.
     * 0 (cache off) is honored everywhere and never raised.
     */
    fun effectiveCacheMb(userMb: Int, totalRamMb: Long?): Int {
        if (userMb <= 0) return 0
        val clamped = userMb.coerceIn(GLSL_CACHE_MB_MIN, GLSL_CACHE_MB_MAX)
        if (totalRamMb != null && totalRamMb in 1..3072) {
            return minOf(clamped, GLSL_CACHE_MB_LOW_RAM)
        }
        return clamped
    }

    /** Clamp an FSR level to the verified 0..4 schema. No device override. */
    fun effectiveFsr(userFsr: Int): Int = userFsr.coerceIn(0, 4)
}
