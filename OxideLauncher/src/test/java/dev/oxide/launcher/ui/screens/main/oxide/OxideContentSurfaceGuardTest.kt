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
 * 文件页与日志页的表面性质
 *
 * 这两块表面压在**外壳之上**，因此有三件事一旦错了就会在设备上看得见，
 * 而且都不是编译期能发现的：
 *
 * 1. 半透明的底。`OxideSurface` 的底色是 `rgba(10,10,10,.72)`，套在这一页上
 *    就等于让底下那一层（首页、实例页，或者旧文件浏览器）从卡片里透出来，
 *    于是同一屏里出现两层界面。
 * 2. 旧界面从某个入口漏回来。旧文件浏览器与旧日志查看器一旦可达，
 *    前面做的全部白费。
 * 3. 界面上出现第二份真相。此前文件页自己另存了一份 `multiSelect`，
 *    与 ViewModel 里的那份可以分叉，于是"看不见的选中"继续生效。
 *
 * 这些都是"读源码就能钉死"的事实，因此不写成 Compose 仪器测试。
 * 断言跑在**去掉注释之后**的源码上：注释里提到旧界面的名字是为了解释
 * 替换关系，那是历史，不是可达性。
 */
class OxideContentSurfaceGuardTest {

    private val files = code(readSource("OxideFilesPage.kt"))
    private val log = code(readSource("OxideLogPage.kt"))
    private val surface = code(readSource("OxideContentSurface.kt"))

    // ---- 不透明 -------------------------------------------------------------

    @Test
    fun bothPagesPaintTheirOwnOpaqueBackground() {
        for ((name, source) in listOf("files" to files, "logs" to log)) {
            assertTrue(
                "$name must paint an opaque root, otherwise the shell shows through it",
                source.contains(".background(Oxide.Bg)"),
            )
            assertFalse(
                "$name must not build its panels out of the translucent OxideSurface",
                source.contains("OxideSurface("),
            )
        }
    }

    @Test
    fun theSharedPanelIsBuiltFromAnOpaqueToken() {
        // 内容面板的底色必须是 [Oxide.BgElevated] / [Oxide.BgTabActive] 这两个
        // 不透明色，而不是半透明的 SurfaceBase；斜向高光照旧叠在上面。
        assertTrue(
            "the content panel needs an opaque base colour",
            surface.contains(".background(if (selected) Oxide.BgTabActive else Oxide.BgElevated)"),
        )
        assertFalse(
            "the content panel must not fall back to the translucent SurfaceBase",
            surface.contains("Oxide.SurfaceBase"),
        )
        assertTrue(
            "the content panel keeps the reference design's highlight",
            surface.contains(".background(Oxide.SurfaceBrush)"),
        )
    }

    @Test
    fun fileFolderTrashAndLogRowsAllGoThroughOneRow() {
        // 同一屏里"文件夹行"与"文件行"此前各写各的底色、圆角与两行文字，
        // 因此它们读起来不像同一类东西
        for ((name, source) in listOf("files" to files, "logs" to log)) {
            assertTrue(
                "$name must build its entries with the shared row",
                source.contains("OxideContentRow("),
            )
            assertFalse(
                "$name must not keep a private copy of the row",
                source.contains("private fun OxideSecCheckbox"),
            )
        }
    }

    // ---- 旧界面不再可达 -----------------------------------------------------

    @Test
    fun neitherPageNamesTheLegacyScreensOrActivities() {
        for ((name, source) in listOf("files" to files, "logs" to log)) {
            for (legacy in listOf(
                "FileManagerActivity",
                "FileManagerRootScreen",
                "FmMainPage",
                "FmTrashScreen",
                "LogViewScreen",
                "NormalNavKey.LogView",
                "Event.OpenFileManager",
            )) {
                assertFalse("$name must not reach the legacy $legacy", source.contains(legacy))
            }
        }
    }

    @Test
    fun theLegacyBrowserIsStillUnreachableFromTheStorageActions() {
        // 存储抽屉的目录入口必须走 host.openFiles（OxideDestination.Files），
        // 而不是 EventViewModel.Event.OpenFileManager（旧的 FileManagerActivity）
        val drawers = code(readSource("OxideSupportDrawers.kt"))
        assertFalse(
            "the storage actions must not start the legacy file browser",
            drawers.contains("Event.OpenFileManager"),
        )
        assertTrue(
            "the storage actions must go through the Oxide bridge",
            drawers.contains("bridge.openFileManager("),
        )
    }

    // ---- 只有一份真相 -------------------------------------------------------

    @Test
    fun theSelectionModeHasExactlyOneSourceOfTruth() {
        // 页面自己另存一份 multiSelect 时，换目录只会清掉本地那份，
        // ViewModel 里的那份还留着，"看不见的选中"因此继续生效
        assertFalse(
            "the files page must not keep its own multiSelect state",
            files.contains("var multiSelect by remember"),
        )
        assertTrue(
            "the multi-select mode must come from the view model state",
            files.contains("val multiSelect = state.multiSelect"),
        )
    }

    @Test
    fun thePathBarReplacesTheOldOneLineAbsolutePath() {
        // 此前路径是把整条绝对路径塞进一个两行省略的文本：既读不出自己在
        // 哪一层，也点不到任何上层
        assertTrue(files.contains("OxideContentPathBar("))
        assertTrue(files.contains("oxidePathCrumbsFor("))
        assertFalse(
            "no raw currentDir text left as the only way to see where you are",
            files.contains("text = raw?.currentDir?.toString()"),
        )
    }

    @Test
    fun neitherPageTouchesTheDiskDirectlyFromComposition() {
        // 条目、统计与日志正文都来自 ViewModel 的 StateFlow 或 produceState，
        // 组合期只读结果。直接在组合里问文件系统，会让首帧在主线程上做 IO——
        // 这正是 FmEntry、回收站清单与日志尾部读取都必须走协程的原因。
        for ((name, source) in listOf("files" to files, "logs" to log)) {
            for (banned in listOf(".exists()", ".listFiles(", ".isDirectory()")) {
                assertFalse(
                    "$name must not ask the file system while composing: $banned",
                    source.contains(banned),
                )
            }
        }
        assertFalse(
            "the files page has no business reading a file's contents at all",
            files.contains(".readText("),
        )
        // 日志正文只在 readLogTail 里读，而它被 produceState + IO 调度器包着
        assertTrue(log.contains("produceState<OxideLogContent?>"))
        assertTrue(log.contains("withContext(Dispatchers.IO)"))
        assertTrue(
            "reading the sources must also happen off the main thread",
            log.contains("probed.value"),
        )
    }

    /**
     * 去掉字符串与注释，只留下真正会被编译的代码
     *
     * 顺序要紧：先把字符串换掉，再去注释。否则一个字符串里的 `//`
     * （例如某个网址）会被当成行注释，把后面整行代码连同它的花括号一起吃掉。
     */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

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

    private companion object {
        /** 原始字符串：里面可以出现引号、换行与注释符号，必须先换掉 */
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}