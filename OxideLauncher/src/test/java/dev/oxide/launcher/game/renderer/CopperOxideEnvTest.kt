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

package dev.oxide.launcher.game.renderer

import dev.oxide.launcher.game.renderer.renderers.COPPER_OXIDE_DATA_DIR_NAME
import dev.oxide.launcher.game.renderer.renderers.copperOxideDriverDataDirEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copper Oxide 选项到环境变量的映射
 *
 * 每个选项必须恰好产生它声称的环境变量；默认（关）时必须产生空表，
 * 这样已有用户的启动环境与此前逐字节一致。源码守卫钉住两件事：
 * 往进程里塞的键只能是反汇编验证过的那几个，渲染器的标识、
 * 名字与版本闸门一个都不能动。
 */
class CopperOxideEnvTest {

    @Test
    fun disabledOptionProducesNoExtraEnvSoExistingUsersSeeNoChange() {
        assertTrue(
            "default off must add nothing to the launch env",
            copperOxideDriverDataDirEnv(usePrivateDir = false, privateDir = "/files/mobileglues").isEmpty()
        )
    }

    @Test
    fun blankDirNeverProducesAnEmptyEnvValue() {
        assertTrue(
            "an empty MG_DIR_PATH would only confuse the driver, never emit it",
            copperOxideDriverDataDirEnv(usePrivateDir = true, privateDir = "  ").isEmpty()
        )
        assertTrue(
            "blank check must also cover the truly empty string",
            copperOxideDriverDataDirEnv(usePrivateDir = true, privateDir = "").isEmpty()
        )
    }

    @Test
    fun enabledOptionProducesExactlyOneVerifiedVariable() {
        val env = copperOxideDriverDataDirEnv(usePrivateDir = true, privateDir = "/files/mobileglues")
        assertEquals(
            "the option maps to exactly one env var, nothing else",
            mapOf("MG_DIR_PATH" to "/files/mobileglues"),
            env
        )
    }

    @Test
    fun privateDataDirNameIsStable() {
        assertEquals("mobileglues", COPPER_OXIDE_DATA_DIR_NAME)
    }

    @Test
    fun theRendererEnvMapOnlyContainsVerifiedKeys() {
        // Since the Copper Oxide rebuild the env is built by pure functions
        // instead of inline put() calls, so assert the maps directly: stronger
        // than scanning source, and it cannot go stale on a refactor.
        val keys = dev.oxide.launcher.game.renderer.copperoxide.CopperOxideEnv.baseEnv().keys +
            dev.oxide.launcher.game.renderer.copperoxide.CopperOxideEnv.dataDirEnv(true, "/x").keys
        val verified = setOf("LIBGL_ES", "LIBGL_EGL", "MG_COUNT_LAUNCH", "OXIDE_RENDERER_FLAVOR", "MG_DIR_PATH")
        assertFalse("expected at least the four base env entries", keys.isEmpty())
        assertTrue(
            "unverified env keys would be fake options, found " + (keys - verified).joinToString(),
            keys.all { it in verified }
        )
        assertTrue("LIBGL_ES baseline must stay", "LIBGL_ES" in keys)
        assertTrue("MG_COUNT_LAUNCH baseline must stay", "MG_COUNT_LAUNCH" in keys)
    }

    @Test
    fun identityNameAndVersionGatesAreUntouched() {
        // Identity now lives in CopperOxideIdentity (single source of truth);
        // the renderer delegates to it, so assert the constants and the live
        // object instead of grepping the renderer file.
        val id = dev.oxide.launcher.game.renderer.copperoxide.CopperOxideIdentity
        assertEquals("opengles3_oxide_copper", id.RENDERER_ID)
        assertEquals("52a0f58e-1694-4d47-9ce6-5fa0894413a7", id.UNIQUE_ID)
        assertEquals("Copper Oxide", id.NAME)
        assertEquals("26.3", id.MAX_MC_VERSION)
        assertEquals("libcopperoxide.so", id.NATIVE_LIBRARY)
        assertEquals("libmobileglues.so", id.LEGACY_LIBRARY)
        val r = dev.oxide.launcher.game.renderer.renderers.CopperOxideRenderer
        assertEquals(id.RENDERER_ID, r.getRendererId())
        assertEquals(id.NATIVE_LIBRARY, r.getRendererLibrary())
    }
}
