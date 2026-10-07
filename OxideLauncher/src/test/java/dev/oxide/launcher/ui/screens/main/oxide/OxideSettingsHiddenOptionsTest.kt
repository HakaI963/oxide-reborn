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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
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
 * 藏在 AllSettings 里但 Oxide 界面没暴露的选项
 *
 * 审计确认有界面、但此前没有行的只有两处：showSponsorship（常规分类里的
 * 赞助提醒开关）与新一代渲染器插件的环境变量配置入口（渲染器抽屉里复用
 * 旧渲染器设置页那一只 RendererV2ConfigDialog）。
 *
 * 下面这些不是选项，不给行：gamepadInputModePrompted、menuBallPos、
 * lastIgnoredVersion、lastUpgradeCheck、finishedGame、terracottaNoticeVer、
 * currentAccount 都是内部状态或一次性标记。launcherTaskMenuExpanded 与
 * currentGamePathId 已有早于本次的合法用法（前者是常规分类里的现成开关，
 * 后者是存储抽屉选中态的只读比对），因此不在"不给行"断言里——它们不是
 * 本次加的，本次也不删它们。
 *
 * 同样不在本次范围内：v1.9.0 按用户要求从外观页拿走的那几项
 * （launcherColorTheme、launcherCustomColor、launcherCustomPaletteStyle、
 * launcherBackgroundOpacity、videoBackgroundVolume、backgroundBlur、
 * backgroundBlurType）。"补齐隐藏选项"不覆盖那次移除，外观页注释里写明了，
 * 键本身也都还在 AllSettings 里。
 */
class OxideSettingsHiddenOptionsTest {

    private val page: String by lazy {
        readMainSource("ui/screens/main/oxide/OxideSettingsPage.kt")
    }

    private val drawers: String by lazy {
        readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
    }

    private val strings: String by lazy { readStringsXml() }

    @Test
    fun theSponsorTipToggleIsWiredThroughSave() {
        assertTrue(
            "the sponsor tip row must read the setting",
            page.contains("AllSettings.showSponsorship.state"),
        )
        assertTrue(
            "the sponsor tip row must persist through save",
            page.contains("AllSettings.showSponsorship.save("),
        )
        assertTrue(
            "the sponsor tip row needs its label",
            strings.contains("name=\"oxide_set_show_sponsor_tip\""),
        )
        assertTrue(
            "the sponsor tip row needs its hint",
            strings.contains("name=\"oxide_set_show_sponsor_tip_detail\""),
        )
    }

    @Test
    fun theRendererDrawerReusesTheExistingEnvConfigDialog() {
        // 只复用现成的对话框与现成的抽屉写法：行只在真的有可配项时出现，
        // 状态只活在抽屉里，没有新管线。
        assertTrue(
            "the drawer must host the existing V2 config dialog",
            drawers.contains("RendererV2ConfigDialog("),
        )
        assertTrue(
            "the dialog units must come from the selected V2 plugin",
            drawers.contains("getConfigurableUnits()"),
        )
        assertTrue(
            "only V2 plugins have configurable units",
            drawers.contains("filterIsInstance<RendererV2Data>()"),
        )
        assertTrue(
            "the row must disappear when there is nothing to configure",
            drawers.contains("if (v2PluginEnvUnits != null)"),
        )
        assertTrue(
            "the configure row needs its hint",
            strings.contains("name=\"oxide_set_renderer_env_configure_detail\""),
        )
    }

    @Test
    fun internalStateKeysAreNotSurfacedAsOptionRows() {
        val sources = page + drawers
        for (key in listOf(
            "gamepadInputModePrompted",
            "menuBallPos",
            "lastIgnoredVersion",
            "lastUpgradeCheck",
            "finishedGame",
            "terracottaNoticeVer",
        )) {
            assertFalse(
                key + " is internal state, not an option row",
                sources.contains("AllSettings." + key),
            )
        }
        // AccountsManager.currentAccountFlow 是账号抽屉本来就在用的账号流，
        // 不是 AllSettings.currentAccount 存的那份启动器状态
        assertFalse(
            "AllSettings.currentAccount is internal state, not an option row",
            sources.contains("AllSettings.currentAccount"),
        )
    }

    @Test
    fun theAppearanceRemovalsStayRemovedAndSaySo() {
        for (key in listOf(
            "AllSettings.launcherColorTheme",
            "AllSettings.launcherCustomColor",
            "AllSettings.launcherCustomPaletteStyle",
            "AllSettings.launcherBackgroundOpacity",
            "AllSettings.videoBackgroundVolume",
            "AllSettings.backgroundBlur",
            "AllSettings.backgroundBlurType",
        )) {
            assertFalse(
                key + " was removed in v1.9.0 and must not come back",
                page.contains(key),
            )
        }
        assertTrue(
            "the appearance page must record why the rows stay gone",
            page.contains("v1.9.0"),
        )
    }

    private fun readMainSource(relativePath: String): String = locate(
        "src/main/java/dev/oxide/launcher/" + relativePath
    ).readText()

    private fun readStringsXml(): String = locate(
        "src/main/res/values/strings.xml"
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
