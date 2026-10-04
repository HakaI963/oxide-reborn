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
 * 设置抽屉里"这一行到底出不出现"的判据
 *
 * 一行控件只有两种正当的形态：前置条件满足就出现并能改设置，前置条件不满足就
 * **不出现**。第三种是设备截图里最刺眼的那种——一行变灰的开关／选择器：
 * 占着位置、读不懂为什么点不动，而且用户没法从界面上判断它是不是 bug。
 *
 * 这几条判据都写成纯函数，因此可以逐个状态钉死；最后两条直接读源码，
 * 钉住它们不再被改回"变灰一行"。
 */
class OxideSettingsRowVisibilityTest {

    // ---- Java：运行时选择器 -------------------------------------------------

    @Test
    fun theJavaRuntimePickerOnlyShowsWhenItCanActuallyChangeSomething() {
        assertTrue(
            "a runtime is present, nothing is scanning and auto-pick is off",
            oxideJavaRuntimePickerVisible(
                scanning = false,
                hasRuntime = true,
                autoPick = false,
            ),
        )
    }

    @Test
    fun theJavaRuntimePickerIsHiddenWhileAutoPickOwnsTheChoice() {
        // 自动选开着的时候那个选择器确实改不了设置，但它不是"暂时不能用"，
        // 而是"这个选择此刻不归你管"：因此整行让位，不留灰行
        assertFalse(
            oxideJavaRuntimePickerVisible(
                scanning = false,
                hasRuntime = true,
                autoPick = true,
            )
        )
    }

    @Test
    fun theJavaRuntimePickerIsHiddenWhileScanningAndWhenThereIsNoRuntime() {
        assertFalse(
            oxideJavaRuntimePickerVisible(scanning = true, hasRuntime = true, autoPick = false)
        )
        assertFalse(
            oxideJavaRuntimePickerVisible(scanning = false, hasRuntime = false, autoPick = false)
        )
        assertFalse(
            oxideJavaRuntimePickerVisible(scanning = true, hasRuntime = false, autoPick = true)
        )
    }

    // ---- Renderer：Zink 系统驱动 -------------------------------------------

    @Test
    fun theZinkSettingsExistOnlyWhereVulkanExists() {
        // Zink 走的是 Vulkan。设备没有 Vulkan 支持时这两项都不存在，
        // 留着它们只是两枚点得动、存下来了、却永远不会被读到的开关。
        assertTrue(oxideZinkSettingVisible(vulkanSupported = true))
        assertFalse(oxideZinkSettingVisible(vulkanSupported = false))
    }

    // ---- Renderer / Java：存着的值拿不到 ------------------------------------

    @Test
    fun aStoredSelectionThatNoLongerExistsIsReportedAsUnresolved() {
        assertTrue(
            oxideStoredSelectionResolves("turnip", listOf("turnip", "mesa"))
        )
        assertFalse(
            "the plugin is gone, so the dropdown must say so instead of showing entry 0",
            oxideStoredSelectionResolves("fcl-vulkan", listOf("turnip", "mesa")),
        )
        assertFalse(oxideStoredSelectionResolves("turnip", emptyList()))
    }

    @Test
    fun theDrawersNoLongerRenderTheseRowsAsGreyedOutRows() {
        val source = readSource("OxideSupportDrawers.kt")
        for (pattern in listOf(
            "enabled = vulkanSupported",
            "enabled = !autoPick",
            "enabled = !scanning",
        )) {
            assertFalse(
                "$pattern is a greyed-out row; the row must disappear or be a real read-only row",
                source.contains(pattern),
            )
        }
        // 存着的值拿不到时，选择器必须退回那个标识，而不是列表第一项
        assertTrue(
            "the renderer row must pass the stored id through as the placeholder",
            source.contains("placeholder = storedRenderer"),
        )
        assertTrue(
            "the driver row must pass the stored id through as the placeholder",
            source.contains("placeholder = storedDriver"),
        )
        assertTrue(
            "the runtime row must pass the stored id through as the placeholder",
            source.contains("placeholder = selectedRuntime"),
        )
    }

    // ---- Storage：目录入口 ---------------------------------------------------

    @Test
    fun aFolderActionIsOfferedOnlyForAFolderThatExists() {
        assertTrue(oxideStorageFolderActionVisible("/storage/emulated/0/"))
        assertFalse(
            "no game folder is configured yet, so the row must not be offered",
            oxideStorageFolderActionVisible(""),
        )
        assertFalse(oxideStorageFolderActionVisible("   "))
    }

    @Test
    fun theStorageActionsRouteToTheOxideSurfacesNotTheLegacyFileBrowser() {
        val source = readSource("OxideSupportDrawers.kt")
        // openFileManager 走 host.openFiles，也就是 OxideDestination.Files；
        // EventViewModel.Event.OpenFileManager 才是旧的 FileManagerActivity
        assertFalse(
            "the drawers must not push the legacy file browser any more",
            source.contains("Event.OpenFileManager"),
        )
        assertFalse(
            "the drawers must not push the legacy log viewer any more",
            source.contains("navigateTo(NormalNavKey"),
        )
        assertTrue(
            "the storage actions must go through the Oxide bridge",
            source.contains("bridge.openFileManager("),
        )
        assertTrue(
            "the storage drawer should still reach the Oxide log viewer",
            source.contains("bridge.openLogView("),
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