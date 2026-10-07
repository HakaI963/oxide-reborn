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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 新菜单的三条触摸铁律，外加退出确认的 Oxide 化
 *
 * 用户在设备上看到的三件事：
 *
 * - ct1：控制层、Add 按钮、Save 与 Exit 全都点不动。原因是面板之前盖着一块
 *   整窗遮罩，它用 `editorConsumeTouches` 把每一按提前吃掉了。
 *   现在面板根部就是面板本身，遮罩与整窗消费者都不许回来。
 * - ct2：控件编辑页里四个分类看得见，内容区全黑。原因是标签列与导航摆在同一
 *   横向行里，标签列量走整行宽，导航按权重只分到零宽；盖在前面的整屏透明
 *   点击层还把手势提前吃掉了。现在编辑页是真正的对话框窗口，
 *   里面是一份直接滚完的表单，不再有标签列加导航的结构。
 * - ct3：退出确认是 Material 弹窗，层属性里的输入框是 Material 的描边输入框。
 *   两处都换成 Oxide 的。
 *
 * 断言跑在抹掉注释与字符串之后的文本上，被测文件里的注释在解释那些旧名字，
 * 那段历史不该满足一条仍然存在的断言。
 *
 * 每一份源码都在函数里读，没有顶层 val：顶层初始值抛一次异常会把这一整个类的
 * 每一个测试变成 ExceptionInInitializerError。
 */
class EditorMenuDispatchGuardTest {

    @Test
    fun `面板里不再有整窗遮罩与整窗触摸消费者`() {
        for (file in editorUiFiles()) {
            val code = file.readText().stripped()
            assertFalse(
                "${file.name} must not consume touches for the whole window any more",
                code.contains("editorConsumeTouches"),
            )
            assertFalse(
                "${file.name} must not build the full-window scrim any more",
                code.contains("EditorScrim"),
            )
        }
    }

    @Test
    fun `停靠面板根部就是面板本身`() {
        val code = locateEditor("OxideEditorDock.kt").readText().stripped()
        assertTrue(
            "the dock must keep its frame, header and footer",
            code.contains("EditorDockFrame(") &&
                code.contains("EditorDockHeader(") &&
                code.contains("EditorFooterRow("),
        )
        assertFalse(
            "no full-window box may wrap the panel any more",
            code.contains("fillMaxSize()") && code.contains("EditorScrim("),
        )
    }

    @Test
    fun `控件编辑页不再是标签列加零宽导航`() {
        val code = locateEditor("edit_widget/EditWidgetDialog.kt").readText().stripped()
        for (gone in listOf(
            "NavDisplay(",
            "rememberTitledNavBackStack",
            "backStack.clearWith",
            "EditWidgetTabLayout(",
            "EditWidgetNavigation(",
        )) {
            assertFalse(
                "EditWidgetDialog must not split tabs and body any more: $gone",
                code.contains(gone),
            )
        }
        assertFalse(
            "no invisible full-screen tap catcher may sit ahead of the panel",
            code.contains("alpha(0f"),
        )
        assertTrue(
            "the widget sheet must be a real dialog window now",
            code.contains("OxideDialogShell("),
        )
        assertTrue(
            "the click-event section must still carry macro and per-key delay",
            code.contains("control_editor_edit_event_macro") &&
                code.contains("control_editor_edit_event_key_delay"),
        )
    }

    @Test
    fun `退出确认不再走 Material 弹窗`() {
        val code = locateViewModel().readText().stripped()
        for (gone in listOf(
            "MaterialAlertDialogBuilder",
            ".setPositiveButton(",
            ".setNegativeButton(",
            ".showThemed()",
        )) {
            assertFalse(
                "EditorViewModel must not build a Material dialog any more: $gone",
                code.contains(gone),
            )
        }
        assertTrue(
            "the exit question must be remembered as state for the Oxide dialog",
            code.contains("exitConfirmVisible"),
        )
        assertTrue(
            "confirming must run the caller supplied exit action",
            code.contains("fun confirmPendingExit") && code.contains("pendingExitAction"),
        )
        assertTrue(
            "the Oxide confirm dialog must be rendered from the editor screen",
            locateEditor("ControlEditor.kt").readText().stripped().contains("OxideConfirmDialog("),
        )
    }

    /**
     * 重写过的菜单表面不再有 Material 输入框
     *
     * 查的是显式清单而不是整个包：外观与译文对话框里的名称输入框
     * （`edit_style/EditStyleDialog.kt`、`edit_translatable/EditTranslatableText.kt`）
     * 不在本次菜单重写的范围内，上一轮已经把它们记为保留，
     * 因此不许出现在这里的只有菜单自己这几份。
     */
    @Test
    fun `重写过的菜单表面不再有 Material 输入框`() {
        for (file in rewrittenMenuFiles()) {
            val code = file.readText().stripped()
            for (gone in listOf(
                "OwnOutlinedTextField",
                "OutlinedTextField(",
                "MaterialAlertDialogBuilder",
            )) {
                assertFalse(
                    "${file.name} must not reach the Material input: $gone",
                    code.contains(gone),
                )
            }
        }
        val layer = locateEditor("edit_layer/EditControlLayerDialog.kt").readText().stripped()
        assertTrue(
            "the layer name field must be the Oxide input now",
            layer.contains("OxideSecInput("),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers：索引源码的辅助全部是 String 扩展或显式收源码的函数
    // -----------------------------------------------------------------------

    private fun editorUiFiles(): List<File> = listOf(
        "OxideControlEditorUi.kt",
        "OxideEditorDock.kt",
        "OxideEditorDockControls.kt",
    ).map { locateEditor(it) }

    /**
     * 本次从头重写过的菜单表面
     *
     * 刻意写成显式清单：包里还留着外观与译文对话框的 Material 输入框，
     * 它们是上一轮明确记为保留的，不在本次菜单重写的范围内，
     * 把它们写进禁止名单只会逼着人绕开断言而不是改代码。
     */
    private fun rewrittenMenuFiles(): List<File> = listOf(
        "OxideControlEditorUi.kt",
        "OxideEditorDock.kt",
        "OxideEditorDockControls.kt",
        "OxideEditorInspector.kt",
        "ControlEditor.kt",
        "edit_widget/EditWidgetDialog.kt",
        "edit_layer/EditControlLayerDialog.kt",
    ).map { locateEditor(it) }

    private fun locateEditor(relative: String): File = locateSource(
        "java/dev/oxide/launcher/ui/screens/main/control_editor/$relative",
    )

    private fun locateViewModel(): File = locateSource(
        "java/dev/oxide/launcher/viewmodel/EditorViewModel.kt",
    )

    private fun String.stripped(): String = this
        .replace(codeRawString, "\"\"")
        .replace(codeString, "\"\"")
        .replace(codeBlockComment, " ")
        .replace(codeLineComment, "")

    private companion object {
        private val codeRawString by lazy { Regex("\"\"\"[\\s\\S]*?\"\"\"") }
        private val codeString by lazy { Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"") }
        private val codeBlockComment by lazy { Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL) }
        private val codeLineComment by lazy { Regex("""//[^\n]*""") }
    }
}
