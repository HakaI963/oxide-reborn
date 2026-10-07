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

package dev.oxide.launcher.ui.screens.main.oxide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copper Oxide 驱动调优行的接线
 *
 * 新键沿用 OxideSettingsControlTest 的不变量：行读 `.state`、写 `.save`，
 * 不读内部状态；调优子行藏在 Copper 专属节与总开关两道门后，对其它渲染器
 * 不可见。源码守卫钉住不安全的档位（NoError L1/L2、GL 版本伪装、环境等级、
 * multidraw）既没有键也没有行。
 */
class OxideCopperTuningTest {

    @Test
    fun newKeysAreDeclaredWithSafeDefaults() {
        val registry = readMainSource("setting/AllSettings.kt")
        for (declaration in listOf(
            "val copperOxideTuningEnabled = boolSetting(\"copperOxideTuningEnabled\", false)",
            "val copperOxideFsr = intSetting(\"copperOxideFsr\", 0, 0..4)",
            "val copperOxideGlslCacheMb = intSetting(\"copperOxideGlslCacheMb\", 64, 0..512)",
            "val copperOxideAngle = intSetting(\"copperOxideAngle\", 0, 0..3)",
            "val copperOxideNoError = intSetting(\"copperOxideNoError\", 0, 0..1)",
            "val copperOxideExtCompute = boolSetting(\"copperOxideExtCompute\", true)",
            "val copperOxideExtTimerQuery = boolSetting(\"copperOxideExtTimerQuery\", true)",
            "val copperOxideExtDsa = boolSetting(\"copperOxideExtDsa\", true)",
        )) {
            assertTrue(
                "missing or mistyped declaration: " + declaration,
                registry.contains(declaration),
            )
        }
    }

    @Test
    fun everyNewKeyIsReadThroughStateAndWrittenThroughSave() {
        val drawers = readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
        for (key in listOf(
            "copperOxideTuningEnabled",
            "copperOxideFsr",
            "copperOxideGlslCacheMb",
            "copperOxideAngle",
            "copperOxideNoError",
            "copperOxideExtCompute",
            "copperOxideExtTimerQuery",
            "copperOxideExtDsa",
        )) {
            assertTrue(
                key + " must be read through .state so the row recomposes",
                drawers.contains("AllSettings." + key + ".state"),
            )
            assertTrue(
                key + " must be persisted through .save(...) or the row is inert",
                drawers.contains("AllSettings." + key + ".save("),
            )
        }
    }

    @Test
    fun tuningRowsLiveBehindTheCopperGateAndTheMasterSwitch() {
        val drawers = readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
        assertTrue(
            "the section must stay gated on the copper predicate",
            drawers.contains("oxideCopperConfigVisible("),
        )
        assertTrue(
            "the tuning rows must stay gated on the master switch",
            drawers.contains("oxideCopperTuningRowsVisible("),
        )
    }

    @Test
    fun tuningRowsVisibleFollowsTheMasterSwitchOnly() {
        assertTrue(oxideCopperTuningRowsVisible(true))
        assertFalse(oxideCopperTuningRowsVisible(false))
    }

    @Test
    fun optionNamesCoverEveryOfferedValue() {
        assertEquals("Disabled", oxideCopperFsrName(0))
        assertEquals("Ultra Quality", oxideCopperFsrName(1))
        assertEquals("Quality", oxideCopperFsrName(2))
        assertEquals("Balanced", oxideCopperFsrName(3))
        assertEquals("Performance", oxideCopperFsrName(4))
        assertEquals("out-of-range FSR must read as off", "Disabled", oxideCopperFsrName(99))
        assertEquals("Driver default", oxideCopperAngleName(0))
        assertEquals("Prefer ANGLE", oxideCopperAngleName(1))
        assertEquals("Force ANGLE off", oxideCopperAngleName(2))
        assertEquals("Force ANGLE on", oxideCopperAngleName(3))
        assertEquals("Auto", oxideCopperNoErrorName(0))
        assertEquals("Disable", oxideCopperNoErrorName(1))
    }

    @Test
    fun noErrorOffersOnlyAutoAndDisable() {
        val registry = readMainSource("setting/AllSettings.kt")
        assertTrue(
            "the NoError range must stop at Disable so L1/L2 cheats stay unoffered",
            registry.contains("val copperOxideNoError = intSetting(\"copperOxideNoError\", 0, 0..1)"),
        )
        val drawers = readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
        assertTrue(
            "the NoError row must offer exactly Auto and Disable",
            drawers.contains("entries = listOf(0, 1)"),
        )
    }

    @Test
    fun cacheRowOffersZeroSoTheCacheCanBeTurnedOff() {
        val registry = readMainSource("setting/AllSettings.kt")
        assertTrue(
            "the cache range must start at 0 (off)",
            registry.contains("0..512"),
        )
    }

    @Test
    fun noUnsafeKeysAreOfferedAnywhere() {
        val registry = readMainSource("setting/AllSettings.kt")
        val drawers = readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
        val renderer = readMainSource("game/renderer/renderers/CopperOxideRenderer.kt")
        for (name in listOf(
            "customGLVersion",
            "hideMGEnvLevel",
            "multidrawOrder",
            "multidrawMode",
            "bufferCoherentAsFlush",
            "angleDepthClearFixMode",
        )) {
            assertFalse(
                name + " must have no setting key",
                registry.contains(name),
            )
            assertFalse(
                name + " must have no drawer row",
                drawers.contains(name),
            )
            assertFalse(
                name + " must not leak into the renderer env",
                renderer.contains(name),
            )
        }
    }

    @Test
    fun rendererAuthorsTheConfigIntoThePrivateDir() {
        val renderer = readMainSource("game/renderer/renderers/CopperOxideRenderer.kt")
        assertTrue(
            "launch must snapshot the tuning settings",
            renderer.contains("copperOxideTuningFromSettings()"),
        )
        assertTrue(
            "launch must author config.json into the resolved dir",
            renderer.contains("authorCopperOxideConfig("),
        )
    }

    private fun readMainSource(relativePath: String): String = locate(
        "src/main/java/dev/oxide/launcher/" + relativePath,
    ).readText()

    private fun locate(suffix: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(suffix)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate " + suffix + " from " + File("").absolutePath)
    }
}
