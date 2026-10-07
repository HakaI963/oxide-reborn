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

import org.junit.Assert.assertEquals
import org.junit.Test

class CopperOxideDeviceTuningTest {
    @Test
    fun detectsAdrenoAndMali() {
        assertEquals(
            CopperOxideGpuVendor.ADRENO,
            CopperOxideDeviceTuning.detectGpuVendor("Qualcomm", "Adreno (TM) 750")
        )
        assertEquals(
            CopperOxideGpuVendor.MALI,
            CopperOxideDeviceTuning.detectGpuVendor("ARM", "Mali-G720")
        )
        assertEquals(
            CopperOxideGpuVendor.UNKNOWN,
            CopperOxideDeviceTuning.detectGpuVendor("", "")
        )
    }

    @Test
    fun zeroCacheIsNeverRaised() {
        assertEquals(0, CopperOxideDeviceTuning.effectiveCacheMb(0, 8192))
        assertEquals(0, CopperOxideDeviceTuning.effectiveCacheMb(-4, 2048))
    }

    @Test
    fun lowRamCapsCacheAt32() {
        assertEquals(32, CopperOxideDeviceTuning.effectiveCacheMb(64, 2048))
        assertEquals(32, CopperOxideDeviceTuning.effectiveCacheMb(512, 3072))
        assertEquals(64, CopperOxideDeviceTuning.effectiveCacheMb(64, 4096))
    }

    @Test
    fun defaultsFollowRamClass() {
        assertEquals(32, CopperOxideDeviceTuning.defaultCacheMbForDevice(2048))
        assertEquals(64, CopperOxideDeviceTuning.defaultCacheMbForDevice(6144))
        assertEquals(128, CopperOxideDeviceTuning.defaultCacheMbForDevice(12288))
        assertEquals(64, CopperOxideDeviceTuning.defaultCacheMbForDevice(null))
    }

    @Test
    fun fsrClampsToSchema() {
        assertEquals(0, CopperOxideDeviceTuning.effectiveFsr(-1))
        assertEquals(4, CopperOxideDeviceTuning.effectiveFsr(99))
        assertEquals(2, CopperOxideDeviceTuning.effectiveFsr(2))
    }
}
