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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ITEM 1 —— 控制布局编辑器的首次引导已经整条移除
 *
 * 用户报的那张 `controltutorial.jpg` 就是这条引导的第二步：它在第一次打开编辑器时
 * 盖住整块画布，讲的是 Zalith 时代那个悬浮球怎么拖、怎么点，而 Oxide 停靠面板的实际
 * 操作与那句话对不上。
 *
 * 事实是"读源码"的事实而不是行为：`GuideHost` 要一个真实的 Activity 窗口，
 * `GuideProgress` 写在 MMKV 里，因此判据只能是源码。断言跑在**抹掉注释与字符串**之后
 * 的文本上——这一份与被测文件里的注释都在解释那些旧名字，而那段历史不该满足一条
 * "仍然存在"的断言。
 *
 * 每一样从源码算出来的东西都在函数里，**没有**任何顶层 `val` 初始值：顶层初始值抛一次
 * 异常，这个类里的每一个测试都会变成 `ExceptionInInitializerError`，一次真正的失败
 * 于是变成一整片红。
 */
class ControlEditorGuideRemovedTest {

    // -----------------------------------------------------------------------
    // Activity
    // -----------------------------------------------------------------------

    @Test
    fun `编辑器 Activity 不再挂 GuideHost`() {
        val code = codeOf(locateSource(EDITOR_ACTIVITY).readText())
        assertFalse(
            "the editor must not host a guide overlay any more; found a GuideHost reference",
            code.contains("GuideHost("),
        )
        assertFalse(
            "rememberAppGuides must not be called from the editor",
            code.contains("rememberAppGuides("),
        )
        assertFalse(
            "the first-run guide trigger must be gone",
            code.contains("startOnce("),
        )
        assertFalse(
            "NextTipLabel is the guide's tap/finish hint and has no host left in the editor",
            code.contains("NextTipLabel("),
        )
        assertFalse(
            "the Editor guide group must not be named here any more",
            code.contains("GuideKeys.Editor"),
        )
    }

    @Test
    fun `编辑器本身照旧被渲染`() {
        // 反向的一条：不能把编辑器连同引导一起删掉
        val code = codeOf(locateSource(EDITOR_ACTIVITY).readText())
        assertTrue(
            "the editor screen itself has to survive",
            code.contains("ControlEditor("),
        )
        assertTrue(
            "the save/exit wiring has to survive",
            code.contains("showExitEditorDialog("),
        )
    }

    /**
     * 引导锚点也一并摘掉了。
     *
     * 没有 GuideHost 之后，`guideNode` / `guideLazyList` 注册上去的锚点只会被丢掉，
     * 而它们还把 `GuideKeys` 与 `dev.oxide.guide` 拉进编辑器包里——那是一条通向已经不
     * 存在的那条引导的活引用。
     */
    @Test
    fun `编辑器包里不再有引导锚点`() {
        assertFalse(
            "the floating ball must not register a guide node",
            codeOf(locateSource(CONTROL_EDITOR).readText()).contains("guideNode("),
        )
        assertFalse(
            "the dock must not register a guide lazy-list anchor",
            codeOf(locateSource(EDITOR_DOCK).readText()).contains("guideLazyList("),
        )
    }

    // -----------------------------------------------------------------------
    // 持久化的"已看过"标记
    // -----------------------------------------------------------------------

    /**
     * 已经看过的那一批不会再看，而新装/升级装也不会看到。
     *
     * `started_Editor` 这个键现在既没有写入方也没有读取方：它是**惰性数据**，留在 MMKV
     * 里无害，也没有迁移的必要——这一版起根本不存在会播这条引导的调用点，因此"已看过"
     * 与"没看过"这两批安装看到的都是同一件事：没有引导。
     */
    @Test
    fun `编辑器包里已无任何引导触发点`() {
        val triggers = mutableListOf<String>()
        for (file in editorPackageFiles()) {
            val code = codeOf(file.readText())
            val fired = code.contains("startOnce(") ||
                code.contains("StartGuideOnce") ||
                code.contains("sendStartGuideOnce(") ||
                code.contains("guides.start(")
            if (fired) triggers += file.name
        }
        assertEquals(
            "no file in the control-editor package may start a guide any more, found $triggers",
            emptyList<String>(),
            triggers,
        )
    }

    /**
     * 主界面那条引导没有被顺带删掉。
     *
     * `ui/guide` 与 `Guide` 模块都不在本次改动的范围内，这里只是钉住"删的是编辑器的
     * 那一条"，免得以后有人把 `rememberEditorGuides` 与 `GuideKeys.Editor` 一起当成
     * 死代码清掉时误伤主界面。
     */
    @Test
    fun `主界面的引导仍然由 MainActivity 承载`() {
        val main = codeOf(locateSource("java/dev/oxide/launcher/ui/activities/MainActivity.kt").readText())
        assertTrue(
            "the launcher home guide is a separate flow and must stay",
            main.contains("rememberAppGuides("),
        )
        assertTrue(
            "the launcher home guide host must stay",
            main.contains("GuideHost("),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * 编辑器包里全部 `.kt` 文件。
     *
     * 走目录而不是一张写死的清单：清单会漏掉新加的文件，于是"没有触发点"这条断言
     * 会在某天变得廉价。
     */
    private fun editorPackageFiles(): List<File> {
        val root = locateSource(CONTROL_EDITOR).parentFile ?: error("no control-editor package")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
    }

    private companion object {
        const val CONTROL_EDITOR = "java/dev/oxide/launcher/ui/screens/main/control_editor/ControlEditor.kt"
        const val EDITOR_DOCK = "java/dev/oxide/launcher/ui/screens/main/control_editor/OxideEditorDock.kt"
        const val EDITOR_ACTIVITY = "java/dev/oxide/launcher/ui/activities/ControlEditorActivity.kt"
    }
}

// ---------------------------------------------------------------------------
// 本包共用的源码读取工具
//
// 全部是**函数**，没有一个顶层 `val` 初始值：顶层初始值抛一次异常，同一个文件里的每
// 一个测试都会变成 ExceptionInInitializerError，真正的失败会被淹没。
// ---------------------------------------------------------------------------

/**
 * 抹掉字符串字面量与注释，只留下真正会被编译的东西
 *
 * 顺序要紧：先字符串后注释。字符串里的 `//`（一个 URL 里的）否则会开出一个行注释，
 * 把后面整段吃掉，于是真正的失败变成一次无声的通过。
 */
internal fun codeOf(source: String): String = source
    .replace(RAW_STRING, "\"\"")
    .replace(STRING, "\"\"")
    .replace(BLOCK_COMMENT, " ")
    .replace(LINE_COMMENT, "")

/**
 * [source] 里以 [anchor] 开头的那一段 `{ ... }`
 *
 * 字符串字面量与注释已经被 [codeOf] 抹掉，因此数花括号就够。
 *
 * 收 [source] 参数而不是隐式去读某个文件：索引源码文本的辅助函数一旦在别的调用点抛
 * 异常，那种失败会以 `ExceptionInInitializerError` 的形式出现在完全不相干的测试里。
 */
internal fun blockOf(source: String, anchor: String): String {
    val start = source.indexOf(anchor)
    assertTrue("could not find `$anchor`", start >= 0)
    val open = source.indexOf("{", start)
    assertTrue("could not find the body of `$anchor`", open >= 0)
    return source.balancedEndFrom(open)
}

/**
 * 从 [open]（一个 `{` 或 `(`）开始的那一段配平文本，含收尾的那个字符
 *
 * 写成 `String` 扩展而不是局部函数：这个仓库已经因为"辅助函数没有跨文件可见性"
 * 在 CI 上挂过两次整类测试。
 */
internal fun String.balancedEndFrom(open: Int): String {
    var depth = 0
    var index = open
    while (index < length) {
        when (this[index]) {
            '{', '(' -> depth++
            '}', ')' -> {
                depth--
                if (depth == 0) return substring(open, index + 1)
            }
        }
        index++
    }
    error("unbalanced group starting at $open")
}

/**
 * 在模块的 `src/main` 下定位一个文件
 *
 * 两种前缀都试一遍：单元测试的工作目录是模块目录（`src/main/…`），从仓库根目录跑时
 * 又是 `OxideLauncher/src/main/…`。定位不到就报错，而不是跳过——跳过的守卫不是守卫。
 */
internal fun locateSource(relativePath: String): File {
    var dir: File? = File("").absoluteFile
    repeat(8) {
        val base = dir ?: return@repeat
        for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
            val candidate = base.resolve(prefix + relativePath)
            if (candidate.isFile) return candidate
        }
        dir = base.parentFile
    }
    error("could not locate src/main/$relativePath from ${File("").absolutePath}")
}

// 用 `by lazy` 而不是直接初始化：顶层 `val` 初始值抛异常会把用到本文件的**每一个**
// 测试类拖成 ExceptionInInitializerError，而真正的问题会被完全淹没。
private val RAW_STRING by lazy { Regex("\"\"\"[\\s\\S]*?\"\"\"") }
private val STRING by lazy { Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"") }
private val BLOCK_COMMENT by lazy { Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL) }
private val LINE_COMMENT by lazy { Regex("""//[^\n]*""") }