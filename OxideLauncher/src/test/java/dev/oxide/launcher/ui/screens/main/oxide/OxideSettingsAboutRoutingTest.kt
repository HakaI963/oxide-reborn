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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
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
 * 设置分类的去处，以及旧引导的重播入口
 *
 * 设备上看到的 `about-screen.jpg` 就是这个漏洞本身：在新设置页里点"关于"，
 * 结果推开的是旧 Zalith 设置宿主——它带着自己上游的图标顶栏与粉色强调轨，
 * 而且描述的还是旧产品。所以哪几类分类**不该**再推进旧栈，必须钉住。
 *
 * 同一个文件里还钉住"旧主界面引导重播"不再有入口：新外壳的主界面没有旧引导
 * 锚点，重播出来只会是一串对不上位置的卡片。
 */
class OxideSettingsAboutRoutingTest {

    private val mainScreen = readSource("ui/screens/main/MainScreen.kt")
    private val settingsPage = readSource("ui/screens/main/oxide/OxideSettingsPage.kt")

    @Test
    fun aboutAndControlManagerNoLongerNavigateIntoTheOldSettingsStack() {
        // 这两类由 Oxide 自己的面板承接，navKey() 必须返回 null
        val oxideOwned = listOf(
            "OxideSettingsSection.About",
            "OxideSettingsSection.ControlManager",
        )
        for (section in oxideOwned) {
            assertTrue(
                "$section must not be mapped onto an old NormalNavKey.Settings entry",
                mainScreen.contains("$section -> null"),
            )
        }
        assertFalse(
            "about must not push NormalNavKey.Settings.AboutInfo any more",
            mainScreen.contains("NormalNavKey.Settings.AboutInfo"),
        )
        assertFalse(
            "control layouts must not push NormalNavKey.Settings.ControlManager any more",
            mainScreen.contains("NormalNavKey.Settings.ControlManager"),
        )
    }

    @Test
    fun javaRendererControlAndGamepadNoLongerNavigateIntoTheOldSettingsStack() {
        // 这四类现在都由 Oxide 自己的设置页就地承接：Java 与渲染器各有抽屉，
        // 控制与手柄整块在控制面板里。navKey() 必须返回 null，否则设备上看到的
        // java-settings.jpg / renderer.jpg / control.jpg 就是从这里漏回去的
        val settingsHosted = listOf(
            "OxideSettingsSection.Renderer",
            "OxideSettingsSection.JavaManager",
            "OxideSettingsSection.Control",
            "OxideSettingsSection.Gamepad",
        )
        for (section in settingsHosted) {
            assertTrue(
                "$section is hosted by the Oxide settings page, so it must map to null",
                mainScreen.contains("$section -> null"),
            )
        }
        for (legacy in listOf(
            "NormalNavKey.Settings.Renderer",
            "NormalNavKey.Settings.JavaManager",
            "NormalNavKey.Settings.Control",
            "NormalNavKey.Settings.Gamepad",
        )) {
            assertFalse("$legacy must not be reachable any more", mainScreen.contains(legacy))
        }
    }

    @Test
    fun everySectionTheSettingsPageHandsToTheHostHasSomewhereToGo() {
        // 每一类都必须落在两个去处之一：Oxide 自己的面板，或者仍然存在的旧栈。
        // 落在两者之外的点下去就是一次"点了没反应"。设置页与高级抽屉都要算进去，
        // 抽屉里同样有"关于"和"控制布局"的入口。
        val routed = Regex("""openSettingsSection\((OxideSettingsSection\.\w+)\)""")
            .findAll(settingsPage + readSource("ui/screens/main/oxide/OxideSupportDrawers.kt"))
            .map { it.groupValues[1].substringAfterLast('.') }
            .toSet()
        assertTrue(
            "expected the settings page to route several sections, got $routed",
            routed.isNotEmpty(),
        )
        // 旧设置栈已经不再接任何一类；能被调用的分类必须都在 navKey() 里返回 null，
        // 也就是由 Oxide 自己的整块面板或设置页某一栏承接
        val oxideHosted = setOf(
            "About",
            "ControlManager",
            "Renderer",
            "JavaManager",
            "Control",
            "Gamepad",
        )
        val orphans = routed - oxideHosted
        assertTrue(
            "these sections have neither an Oxide panel nor an old stack entry: $orphans",
            orphans.isEmpty(),
        )
        // 关于与控制布局这两类必须是外壳那块整块面板
        assertTrue(
            "about must be reachable from the settings page, got $routed",
            "About" in routed,
        )
    }

    /**
     * 设置页里不再有"重播主界面引导"这一行
     *
     * `oxide_set_action_guides` 是那一行的标签字符串。整个通用分类里都不该再出现它——
     * 不是换个说法继续留着，而是这一项本身消失。
     */
    @Test
    fun thereIsNoWayToReplayTheOldMainScreenGuideFromTheGeneralCategory() {
        assertFalse(
            "the settings page must not offer a guide replay",
            settingsPage.contains("oxide_set_action_guides"),
        )
        assertFalse(
            "the guide replay callback was only reachable from the removed row",
            settingsPage.contains("replayGuide"),
        )
    }

    /**
     * 首次启动不再重播那段引导
     *
     * `LauncherScreen` 早已不被渲染（`LauncherMain` 那一项现在是 `OxideMainShell`），
     * 但里面那次 `LaunchedEffect` 仍然是"首次启动重播"的字面实现，
     * 留着就等于这个行为还在源码里等着被重新接上。
     */
    @Test
    fun theLauncherScreenNoLongerReplaysTheGuideOnFirstLaunch() {
        val launcherScreen = readSource("ui/screens/content/LauncherScreen.kt")
        assertFalse(
            "the old first-launch guide replay must be gone",
            launcherScreen.contains("startGuideOnce"),
        )
        assertFalse(
            "the guide keys must not be handed to the host any more",
            launcherScreen.contains("sendStartGuideOnce"),
        )
    }

    /**
     * 被移除的那一段在本次改动拥有的文件里不留痕
     *
     * 只检查这三个文件：`MainActivity` 里的 `GuideHost` 与 `AppGuides` 里的引导定义
     * 仍然服务于编辑器引导，不能顺手删掉；而 `OxideSupportDrawers` 不在本次范围内，
     * 它的 Advanced 抽屉里那一行由另一个改动处理。
     */
    @Test
    fun theRemovedGuideIsGoneFromTheFilesThisChangeOwns() {
        for (source in listOf(
            mainScreen,
            settingsPage,
            readSource("ui/screens/content/LauncherScreen.kt"),
        )) {
            assertFalse("a guide start helper survived", source.contains("sendStartGuideOnce"))
            assertFalse("a guide host survived", source.contains("GuideHost"))
            assertFalse("a guide label helper survived", source.contains("NextTipLabel"))
        }
    }

    private fun readSource(relativePath: String): String = locate(relativePath).readText()

    /**
     * 从当前工作目录往上找源文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯；找不到直接报错，
     * 绝不悄悄跳过——那样的断言等于没有。
     */
    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                File("").absolutePath
        )
    }
}
