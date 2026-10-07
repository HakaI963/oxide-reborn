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
 * 下载分类里原生库插件那一节的移除守卫
 *
 * 依据是 r1.jpg 的设备反馈：设置 > 下载里那只 PLUGINS 盒子只剩一个空态
 * 加一个外部链接。开关本身仍在旧游戏设置页里，键与运行时读取都没动，
 * 关于面板也还列着插件项目——拿掉的只是这一节，而不是插件管理本身。
 *
 * 镜像（仅中国大陆）、搜索默认与浏览三节不在本次范围内，必须原样保留。
 */
class OxideSettingsDownloadsTest {

    private val page: String by lazy {
        readMainSource("ui/screens/main/oxide/OxideSettingsPage.kt")
    }

    private val strings: String by lazy { readStringsXml() }

    @Test
    fun theNativeLibPluginsGroupIsGoneFromTheSettingsPage() {
        for (pattern in listOf(
            "oxide_set_no_plugin",
            "oxide_set_action_dl_native_lib_plugin",
            "oxide_set_plugin_from",
            "NativeLibPluginRow",
            "NativePluginManager",
            "URL_GITHUB_NATIVE_LIB_PLUGINS",
        )) {
            assertFalse(
                pattern + " must not remain in the settings page",
                page.contains(pattern),
            )
        }
        // 这一节的标题字符串本身是抽屉的渲染器 / 驱动插件分组共用的，不能跟着删，
        // 但设置页里不该再引用它
        assertFalse(
            "the settings page must not reference the plugins section title any more",
            page.contains("oxide_set_section_plugins"),
        )
    }

    @Test
    fun theRestOfDownloadsStaysMirrorsSearchAndBrowse() {
        for (pattern in listOf(
            "oxide_set_section_mirrors",
            "oxide_set_section_search",
            "oxide_set_section_browse",
            "isChinaMainland",
            "searchModPlatform",
            "searchModpackPlatform",
            "searchResourcePackPlatform",
            "searchShadersPlatform",
            "OxidePage.Discover",
        )) {
            assertTrue(
                pattern + " must stay in the downloads category",
                page.contains(pattern),
            )
        }
    }

    @Test
    fun theRemovedPluginStringsAreGoneExceptTheSharedSectionTitle() {
        // 逐条 grep 过：这四个在源码里只剩设置页的引用，引用删了字符串就悬空，
        // 因此跟着删。oxide_set_section_plugins 还被抽屉用着，必须留下。
        for (name in listOf(
            "oxide_set_no_plugin",
            "oxide_set_plugin_from",
            "oxide_set_action_dl_native_lib_plugin",
            "oxide_set_action_dl_native_lib_plugin_detail",
        )) {
            assertFalse(
                name + " must be removed from strings",
                strings.contains("name=\"" + name + "\""),
            )
        }
        assertTrue(
            "oxide_set_section_plugins is still used by the renderer drawer plugin group",
            strings.contains("name=\"oxide_set_section_plugins\""),
        )
    }

    @Test
    fun theNativeLibPluginKeyItselfStaysDeclared() {
        // 行可以删，键永远不删：运行时与旧游戏设置页还在读写它
        val all = readMainSource("setting/AllSettings.kt")
        assertTrue(
            "disableNativeLibPlugins must stay declared in AllSettings",
            all.contains("val disableNativeLibPlugins"),
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
