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
import org.junit.Assert.assertTrue
import org.junit.Test

class CopperOxideEnvTest {
    @Test
    fun baseEnvHasExactlyTheVerifiedKeys() {
        val base = CopperOxideEnv.baseEnv()
        assertEquals("3", base["LIBGL_ES"])
        assertEquals("libcopperoxide.so", base["LIBGL_EGL"])
        assertEquals("libmobileglues.so", CopperOxideIdentity.LEGACY_LIBRARY)
        assertEquals("1", base["MG_COUNT_LAUNCH"])
        assertEquals("copper-oxide", base["OXIDE_RENDERER_FLAVOR"])
        assertEquals(4, base.size)
    }

    @Test
    fun dataDirOffIsByteIdenticalToNoTuning() {
        assertTrue(CopperOxideEnv.dataDirEnv(false, "/x").isEmpty())
        assertTrue(CopperOxideEnv.dataDirEnv(true, "").isEmpty())
        assertTrue(CopperOxideEnv.dataDirEnv(true, "   ").isEmpty())
    }

    @Test
    fun dataDirOnEmitsExactlyOneVerifiedKey() {
        val env = CopperOxideEnv.dataDirEnv(true, "/files/mobileglues")
        assertEquals(mapOf("MG_DIR_PATH" to "/files/mobileglues"), env)
    }

    @Test
    fun mergedDoesOneAllocationAndKeepsBaseWhenDataDirEmpty() {
        val base = CopperOxideEnv.baseEnv()
        val merged = CopperOxideEnv.merged(base, emptyMap())
        assertEquals(base, merged)
        val withDir = CopperOxideEnv.merged(base, mapOf("MG_DIR_PATH" to "/p"))
        assertEquals("3", withDir["LIBGL_ES"])
        assertEquals("/p", withDir["MG_DIR_PATH"])
        assertEquals(5, withDir.size)
    }

    @Test
    fun identityIsStable() {
        assertEquals("opengles3_oxide_copper", CopperOxideIdentity.RENDERER_ID)
        assertEquals("Copper Oxide", CopperOxideIdentity.NAME)
        assertEquals("copper-oxide", CopperOxideIdentity.FLAVOR)
        assertTrue(CopperOxideIdentity.isCopperOxideId("opengles3_oxide_copper"))
    }
}
