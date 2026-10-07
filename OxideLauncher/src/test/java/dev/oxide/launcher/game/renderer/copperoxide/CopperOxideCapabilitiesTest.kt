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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopperOxideCapabilitiesTest {
    @Test
    fun hasExtensionIsSubstringSafe() {
        assertTrue(CopperOxideCapabilities.hasExtension("GL_EXT_foo GL_EXT_bar", "GL_EXT_foo"))
        assertTrue(CopperOxideCapabilities.hasExtension("GL_EXT_foo", "GL_EXT_foo"))
        // Prefix of a longer name must not match.
        assertFalse(CopperOxideCapabilities.hasExtension("GL_EXT_foobar", "GL_EXT_foo"))
        assertFalse(CopperOxideCapabilities.hasExtension("", "GL_EXT_foo"))
        assertFalse(CopperOxideCapabilities.hasExtension("GL_EXT_foo", ""))
    }

    @Test
    fun rendererIdPinsVersionWithoutSpoofing() {
        assertEquals(2, CopperOxideCapabilities.resolveGlesMajor("opengles2_oxide_holy", 3))
        assertEquals(3, CopperOxideCapabilities.resolveGlesMajor("opengles3_oxide_copper", 2))
        assertEquals(3, CopperOxideCapabilities.resolveGlesMajor("opengles3_oxide_ltw", -1))
    }

    @Test
    fun probeFailuresFallBackToThreeNotTwo() {
        // Old code forced "2" on any probe failure (-1/-2/-3); honest fallback is 3.
        assertEquals("3", CopperOxideCapabilities.resolveLibGlEs("vulkan_zink", -1))
        assertEquals("3", CopperOxideCapabilities.resolveLibGlEs("custom", -3))
        assertEquals("2", CopperOxideCapabilities.resolveLibGlEs("custom", 2))
        assertEquals("3", CopperOxideCapabilities.resolveLibGlEs("custom", 3))
    }

    @Test
    fun eglProbeCacheProbesOnce() {
        CopperOxideCapabilities.EglProbeCache.resetForTest()
        var calls = 0
        val first = CopperOxideCapabilities.EglProbeCache.getOrProbe { calls++; 3 }
        val second = CopperOxideCapabilities.EglProbeCache.getOrProbe { calls++; 2 }
        assertEquals(3, first)
        assertEquals(3, second)
        assertEquals(1, calls)
        CopperOxideCapabilities.EglProbeCache.resetForTest()
    }
}
