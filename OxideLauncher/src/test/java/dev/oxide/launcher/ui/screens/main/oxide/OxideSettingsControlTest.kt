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

import dev.oxide.launcher.ui.theme.ColorThemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 新设置页的写入路径守卫
 *
 * v1.4.0 的反馈是"控件在、但不生效"。设置单元有两条写入口：
 * `save(v)` 会落盘，`updateState(v)` 只改内存里的 state，
 * 进程一杀就没了——而且因为 state 变了，行上的读数照样跟着变，
 * 从界面上完全看不出没落盘。这类改动极易被后续的重构顺手引入，
 * 所以这里直接读源码把它钉住：这两个文件里所有对 AllSettings 的调用
 * 只允许出现 `save(`。
 */
class OxideSettingsControlTest {

    private val sources: Map<String, String> = listOf(
        "OxideSettingsPage.kt",
        "OxideSupportDrawers.kt",
    ).associateWith { name -> readSource(name) }

    /** 所有 `AllSettings.xxx.yyy(` 的写法 */
    private val calls = Regex("""AllSettings\.(\w+)\.(\w+)\(""").findAll(sources.values.joinToString("\n"))
        .map { it.groupValues[1] to it.groupValues[2] }
        .toList()

    private val settings = Regex("""AllSettings\.(\w+)""").findAll(sources.values.joinToString("\n"))
        .map { it.groupValues[1] }
        .distinct()
        .sorted()

    @Test
    fun `every write to a setting unit goes through save and never updateState`() {
        val offenders = calls.filter { (_, method) -> method != "save" }
        assertTrue(
            "settings must only be written with save(...); found " +
                offenders.joinToString { "${it.first}.${it.second}(" } +
                " -- updateState only touches memory, so the value is lost on restart",
            offenders.isEmpty()
        )
    }

    @Test
    fun `no setting is read with getValue because it is not observable`() {
        // getValue() 只从存储里取一次，不会订阅变化；组合里读它不会重组，
        // 控件就会显示旧值。这里要求全部走 .state。
        val offenders = calls.filter { (_, method) -> method == "getValue" }
        assertTrue("read settings through .state, not .getValue()", offenders.isEmpty())

        val source = sources.values.joinToString("\n")
        assertFalse("getValue() bypasses the observable state", source.contains(".getValue()"))
    }

    @Test
    fun `every referenced setting is actually written by these files`() {
        // currentGamePathId 走 GamePathManager.saveCurrentPath()：它内部自己判断
        // 是否真的变了才落盘，绕过它直接 save 会漏掉刷新版本列表那一步
        val writtenElsewhere = setOf("currentGamePathId")
        val written = calls.map { it.first }.toSet() + writtenElsewhere
        // settings is a Sequence, which has no isEmpty(); materialise it - the failure message
        // below needs the values anyway.
        val onlyRead = settings.filter { it !in written }.toList()
        assertTrue(
            "these settings are read but never written here, so the row would be inert: " +
                onlyRead.joinToString(),
            onlyRead.isEmpty()
        )
    }

    /**
     * 用户点名的那几个控件必须真的写在设置注册表里
     *
     * 深色模式、颜色主题、配色风格与自定义色是 v1.4.0 里“看着能点、什么都不变”的四个，
     * 逐条钉住它们，避免以后有人把 `.save(...)` 换成局部 remember。
     *
     * 后三个的控件已按 v1.8.0 的反馈从外观页移走（颜色主题行与整块壁纸），它们不再是
     * “控件”，也就不再有“点了没反应”这回事；设置项本身全部留在 AllSettings 里，
     * 主题实现（Theme.kt / NativeThemeUtils.kt）还在读，“键还在、随时能加回来”那半边
     * 由 OxideSettingsRowVisibilityTest 钉住。这里只留深色模式——它仍然是页面上真实
     * 存在、且必须真的写盘的控件。
     */
    @Test
    fun `the appearance controls the bug report named are wired to real settings`() {
        // 深色模式仍是页面上真实存在的控件，逐条钉住它，避免以后有人把 `.save(...)`
        // 换成局部 remember。自定义色的初值读在取色对话框里，所以按两个文件一起判断。
        val source = sources.values.joinToString("\n")
        for (key in listOf(
            "launcherDarkMode",
        )) {
            assertTrue("$key must be read through .state", source.contains("AllSettings.$key.state"))
            assertTrue("$key must be written through .save(...)", source.contains("AllSettings.$key.save("))
        }
    }

    @Test
    fun `no control writes a value it never reads back`() {
        // rememberSaveable 存的是界面自己的选择，不是设置；写进去却不读回来
        // 就等于控件看起来动了、设置没动。
        val source = sources.values.joinToString("\n")
        val saved = Regex("""rememberSaveable(?:Saveable)?\s*(?:\([^)]*\))?\s*\{\s*mutableStateOf""").findAll(source).count()
        assertTrue("expected the page and drawers to keep their tab/selection in rememberSaveable", saved > 0)
    }

    @Test
    fun `the settings the controls write are all declared by the registry`() {
        // 属性名写错能编译通过、运行也通过，只有用户在界面上找效果时才会发现
        val known = declaredSettingProperties()
        for (key in settings) {
            assertTrue("$key is not declared in AllSettings", key in known)
        }
    }

    /**
     * 键名和属性名并不总是同一个
     *
     * `val disableNativeLibPlugins = stringListSetting("nativeLibPlugins", ...)` 的存储键
     * 是 nativeLibPlugins，所以这里比对的是属性名，也就是 `AllSettings.xxx` 后面那一段。
     */
    private fun declaredSettingProperties(): Set<String> {
        val text = locate("setting/AllSettings.kt").readText()
        return Regex("""(?m)^\s+val\s+(\w+)\s*=\s*\w+Setting\(""").findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `the settings surface covers every category it advertises`() {
        val source = sources.getValue("OxideSettingsPage.kt")
        for (category in listOf("General", "Game", "Java", "Renderer", "Graphics",
            "Controls", "Downloads", "Appearance", "Accounts", "Storage", "Advanced")) {
            assertTrue(
                "category $category is missing from the rail",
                source.contains("$category(R.string.oxide_set_cat_")
            )
        }
    }

    @Test
    fun `dynamic colour is not offered below android 12`() {
        assertTrue(
            "Android 11 cannot resolve the dynamic palette, so it must not be selectable",
            ColorThemeType.DYNAMIC !in colorThemeEntries(sdkInt = 30)
        )
        assertTrue(
            "Android 12 and up keep the dynamic palette",
            ColorThemeType.DYNAMIC in colorThemeEntries(sdkInt = 31)
        )
        assertEquals(
            ColorThemeType.entries.size,
            colorThemeEntries(sdkInt = 31).size
        )
        assertEquals(
            ColorThemeType.entries.size - 1,
            colorThemeEntries(sdkInt = 23).size
        )
    }

    @Test
    fun `the offered colour themes stay stable for a given api level`() {
        assertEquals(colorThemeEntries(31), colorThemeEntries(34))
        assertEquals(colorThemeEntries(23), colorThemeEntries(30))
    }

    @Test
    fun `the renderer drawer tab indices line up with the tabs it renders`() {
        assertEquals(0, OxideRendererTabs.RENDERER)
        assertEquals(1, OxideRendererTabs.GRAPHICS)
        assertEquals(2, OxideRendererTabs.PERFORMANCE)

        val source = sources.getValue("OxideSupportDrawers.kt")
        assertTrue(
            "the renderer drawer must render exactly the three tabs these indices address",
            source.contains("oxide_set_tab_renderer") &&
                source.contains("oxide_set_tab_graphics") &&
                source.contains("oxide_set_tab_performance")
        )
    }

    private fun readSource(name: String): String = locate(
        "ui/screens/main/oxide/$name"
    ).readText()

    /**
     * 从当前工作目录往上找源文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯，
     * 找不到就直接报错——绝不能悄悄跳过，那样的测试等于没有。
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