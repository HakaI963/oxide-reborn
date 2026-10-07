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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copper Oxide 专属配置节的可见性与接线
 *
 * 这一节只对 Copper Oxide 有意义：其它渲染器的驱动不读 MG_DIR_PATH，
 * 给它们看就是"点了没反应"的行。判据是纯函数，逐个标识钉死；
 * 源码守卫钉住这一节读写的是真实存在的设置键、且文案全部复用
 * strings.xml 里已有的 key（本节无权新增字符串）。
 */
class OxideCopperConfigTest {

    @Test
    fun sectionShowsForCopperOxideOnly() {
        assertTrue(
            "the copper uuid must open the section",
            oxideCopperConfigVisible("52a0f58e-1694-4d47-9ce6-5fa0894413a7")
        )
    }

    @Test
    fun sectionHidesForEverythingElse() {
        assertFalse(
            "empty stored renderer must not open the section",
            oxideCopperConfigVisible("")
        )
        assertFalse(
            "LTW must not see the copper section",
            oxideCopperConfigVisible("179855cc-0cf7-47e0-86c4-866ab9ca6d0d")
        )
        assertFalse(
            "Holy GL4ES must not see the copper section",
            oxideCopperConfigVisible("93083c36-6e9c-479f-a8f8-c5cb59c032f9")
        )
        assertFalse(
            "unknown ids must not see the copper section",
            oxideCopperConfigVisible("00000000-0000-4000-8000-000000000000")
        )
    }

    @Test
    fun drawerWiresTheOptionThroughStateAndSave() {
        val drawers = readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
        assertTrue(
            "the renderer drawer must gate the section on the copper predicate",
            drawers.contains("oxideCopperConfigVisible(")
        )
        assertTrue(
            "the row must read the setting through state",
            drawers.contains("AllSettings.copperOxidePrivateDataDir.state")
        )
        assertTrue(
            "the row must persist through save",
            drawers.contains("AllSettings.copperOxidePrivateDataDir.save(")
        )
    }

    @Test
    fun optionKeyIsDeclaredWithDefaultOff() {
        val registry = readMainSource("setting/AllSettings.kt")
        assertTrue(
            "the option must be declared in the registry",
            registry.contains("val copperOxidePrivateDataDir = boolSetting(\"copperOxidePrivateDataDir\", false)")
        )
    }

    @Test
    fun rowCopyReusesExistingStringKeys() {
        val strings = readResSource("src/main/res/values/strings.xml")
        for (key in listOf(
            "settings_renderer_config_title",
            "settings_renderer_env_title",
            "oxide_set_renderer_env_configure_detail"
        )) {
            assertTrue(
                "row copy must reuse existing string " + key,
                strings.contains("name=\"" + key + "\"")
            )
        }
    }

    private fun readMainSource(relativePath: String): String = locate(
        "src/main/java/dev/oxide/launcher/" + relativePath
    ).readText()

    private fun readResSource(suffix: String): String = locate(suffix).readText()

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
