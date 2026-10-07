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
 * ITEM 2 —— 编辑器里的对话框不再用 Material 的那一套
 *
 * 用户报的两张图（`controlpop.jpg` 的 "Joystick Style List"、`controlpop2.jpg` 的
 * "Control Appearance List"）是同一种毛病：弹窗还是 `ui/components/Dialogs.kt` 里那一套
 * `Dialog` + Material `Surface` 卡片，于是灰卡片加鲑鱼色按钮压在 Oxide 的停靠面板上。
 *
 * 这些事实是"读源码"的事实而不是行为，因此属于这一类而不是 Compose 测试：
 *
 * 1. `Dialog` 要一个真实的窗口，`AlertDialog` 的那一层连 `Dialog` 都不是——纯 JVM 单测
 *    走不到；
 * 2. 转换是**呈现**层面的：字符串、校验、回调都必须一字不变，而这些恰好只能从源码里
 *    读出来（见下面那几条"不许变"的断言）。
 *
 * 断言跑在抹掉注释与字符串之后的文本上。被测文件里的注释在解释那些旧名字，那段历史
 * 不该满足一条"仍然存在"的断言。
 *
 * 每一份源码都在**函数**里读，没有顶层 `val`：顶层初始值抛一次异常会把这一整个类的
 * 每一个测试变成 `ExceptionInInitializerError`。
 */
class ControlEditorLegacyDialogGuardTest {

    // -----------------------------------------------------------------------
    // 被转过的那些文件
    // -----------------------------------------------------------------------

    /**
     * 转成 Oxide 面板的那些对话框文件，一一对应。
     *
     * 刻意写成显式清单而不是扫整个目录：包里还留着 `ColorPickerDialog`（取色器是一整块
     * 第三方面板）与 `OwnOutlinedTextField`（Material 输入框的共用壳），它们不是这一轮
     * 的目标，把它们写进禁止名单只会逼着人绕开断言而不是改代码。报告里明确点出了这两
     * 处仍然保留的旧东西。
     */
    private fun convertedDialogFiles(): List<File> = listOf(
        "edit_joystick/JoystickStyleListDialog.kt",
        "edit_joystick/EditStyleDialog.kt",
        "edit_style/StyleListDialog.kt",
        "edit_style/EditStyleDialog.kt",
        "edit_layer/EditControlLayerDialog.kt",
        "edit_layer/EditSwitchLayersVisibilityDialog.kt",
        "edit_translatable/EditTranslatableText.kt",
        "edit_widget/CloneWidgetDialog.kt",
        "edit_widget/EditWidgetDialog.kt",
        "edit_widget/ClickEventEdit.kt",
        "edit_widget/EditJoystickEvents.kt",
        "edit_widget/EditJoystickStyle.kt",
        "edit_widget/EditWidgetStyle.kt",
        "edit_widget/KeyEventEdit.kt",
        "OxideEditorDialogs.kt",
        "OxideEditorDialogTabs.kt",
    ).map { locateEditor(it) }

    /**
     * 禁止出现在编辑器包里的那些旧组件
     *
     * 逐个点名，而不是笼统地禁 `material3.`：`Text` 与 `Icon` 是排版与图形，不是那套
     * 鲑鱼色语言；把它们一起禁掉，这条守卫就变成了"谁也别想改这里"而不是"别退回旧
     * 外观"。
     */
    private fun forbiddenTokens(): List<String> = listOf(
        "androidx.compose.material3.AlertDialog",
        "androidx.compose.material3.Button(",
        "androidx.compose.material3.FilledTonalButton",
        "androidx.compose.material3.TextButton",
        "androidx.compose.material3.OutlinedButton",
        "androidx.compose.material3.Surface",
        "androidx.compose.material3.IconButton",
        "androidx.compose.material3.Checkbox",
        "androidx.compose.material3.RadioButton",
        "androidx.compose.material3.NavigationRailItem",
        "androidx.compose.material3.SecondaryTabRow",
        "androidx.compose.material3.Tab(",
        "androidx.compose.material3.HorizontalDivider",
        "androidx.compose.material3.LinearProgressIndicator",
        // 启动器自己的调色板助手：跟着 Material 的 surfaceVariant / onSurface 走，
        // 也就是旧卡片底色
        "cardColor(",
        "onCardColor()",
        "itemColor(",
        "onItemColor()",
        // ui/components 里那五个 Simple*Dialog 与 ProgressDialog
        "dev.oxide.launcher.ui.components.SimpleAlertDialog",
        "dev.oxide.launcher.ui.components.SimpleEditDialog",
        "dev.oxide.launcher.ui.components.SimpleCheckEditDialog",
        "dev.oxide.launcher.ui.components.ProgressDialog",
        "dev.oxide.launcher.ui.components.rememberDialogMaxHeight",
        // Zalith 时代的对话框辅助件
        "MarqueeText",
    )

    @Test
    fun `转过的那些对话框不再引用旧的 Material 组件`() {
        for (file in convertedDialogFiles()) {
            val code = codeOf(file.readText())
            for (token in forbiddenTokens()) {
                assertFalse(
                    "${file.name} must not build itself out of `$token`",
                    code.contains(token),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 转换到位了没有
    // -----------------------------------------------------------------------

    /**
     * 两张截图里那两张列表弹窗都由 Oxide 面板承载。
     *
     * `controlpop.jpg` / `controlpop2.jpg` 的标题文案一并在断言里，因此哪一张弹窗被
     * 换掉、哪一张漏掉，从失败消息里一眼看得出来。
     */
    @Test
    fun `两张截图里的列表弹窗都由 OxideDialogShell 承载`() {
        val joystick = codeOf(locateEditor("edit_joystick/JoystickStyleListDialog.kt").readText())
        assertTrue(
            "the Joystick Style List dialog must be an Oxide panel; its title string is gone too",
            joystick.contains("OxideDialogShell(") &&
                joystick.contains("control_editor_edit_joystick_style_list"),
        )
        assertTrue(
            "the empty-state row must still be there, worded exactly as before",
            joystick.contains("control_editor_edit_joystick_style_list_empty"),
        )

        val style = codeOf(locateEditor("edit_style/StyleListDialog.kt").readText())
        assertTrue(
            "the Control Appearance List dialog must be an Oxide panel; its title string is gone too",
            style.contains("OxideDialogShell(") &&
                style.contains("control_editor_edit_style_config"),
        )
        assertTrue(
            "the empty-state row must still be there, worded exactly as before",
            style.contains("control_editor_edit_style_config_empty"),
        )
    }

    /** 外观列表与摇杆外观列表仍然**关不掉**（返回键与点遮罩都不行），只能按"关闭" */
    @Test
    fun `两张列表弹窗仍然只由关闭按钮离开`() {
        for (relative in listOf(
            "edit_joystick/JoystickStyleListDialog.kt",
            "edit_style/StyleListDialog.kt",
        )) {
            val code = codeOf(locateEditor(relative).readText())
            assertTrue(
                "$relative must keep `dismissByDialog = false`",
                code.contains("dismissByDialog = false"),
            )
            assertTrue(
                "$relative must still wire the close path to its onClose callback",
                code.contains("onDismissRequest = onClose"),
            )
            assertTrue(
                "$relative must still offer Create",
                code.contains("control_manage_create_new"),
            )
            assertTrue(
                "$relative must still offer Close",
                code.contains("generic_close"),
            )
        }
    }

    // -----------------------------------------------------------------------
    // 换皮的时候最容易丢掉的那些能力
    // -----------------------------------------------------------------------

    /**
     * 输入类对话框不许新增校验。
     *
     * 旧的 `SimpleEditDialog` 一个校验都没有：空名字照样能确认，"发送文本"那一条更是
     * 靠"清空输入即删除该事件"。`OxideTextEntryDialog` 默认 `allowBlank = false`，会把
     * 确认按钮画灰——那是一次真实的行为回退，因此三处都必须显式写 `allowBlank = true`。
     */
    @Test
    fun `两处新建名称与发送文本仍然允许空值`() {
        val code = codeOf(locateEditor("OxideEditorDialogs.kt").readText())
        val entries = countOf(code, "OxideTextEntryDialog(")
        assertTrue("expected three text-entry dialogs, found $entries", entries >= 3)
        assertTrue(
            "every OxideTextEntryDialog in the editor must opt out of the blank check",
            countOf(code, "allowBlank = true") >= 3,
        )
    }

    /** 只有一枚按钮的那几张通知弹窗仍然不画取消 */
    @Test
    fun `单按钮的通知弹窗仍然只画一枚确认`() {
        val code = codeOf(locateEditor("OxideEditorDialogs.kt").readText())
        for (branch in listOf(
            "is EditorOperation.SaveFailed ->",
            "is EditorWarningOperation.WarningNoLayers ->",
            "is EditorWarningOperation.WarningNoSelectLayer ->",
        )) {
            val block = blockOf(code, branch)
            assertTrue(
                "$branch must stay a notice-only dialog, i.e. an empty cancelText",
                block.contains("cancelText = \"\""),
            )
            assertTrue(
                "$branch must still clear the operation it belongs to",
                block.contains("= EditorOperation.None") ||
                    block.contains("= EditorWarningOperation.None"),
            )
        }
    }

    /** 每一处删除/保存失败/警告的回调都还挂在原来的状态量上 */
    @Test
    fun `确认类回调仍然写到原来的状态量`() {
        val code = codeOf(locateEditor("OxideEditorDialogs.kt").readText())
        for (token in listOf(
            "viewModel.removeLayer(layer)",
            "viewModel.removeStyle(style)",
            "viewModel.removeJoystickStyle(style)",
            "viewModel.removeWidget(layer, data)",
            "viewModel.createNewStyle(name)",
            "viewModel.createNewJoystickStyle(name)",
            "viewModel.editorWarningOperation = EditorWarningOperation.None",
        )) {
            assertTrue("the dialog wiring must still contain `$token`", code.contains(token))
        }
        // 保存中：不可中断，也仍然不画取消
        val saving = blockOf(code, "is EditorOperation.Saving ->")
        assertTrue("saving must still be a task dialog", saving.contains("OxideTaskDialog("))
        assertTrue("saving must not offer a cancel", saving.contains("onCancel = null"))
    }

    /**
     * 剩下两块全屏覆盖层仍然不是 Dialog 窗口。
     *
     * 这一条钉住的是**没有**被"顺手"改成 `OxideDialogShell`：两个外观编辑器原来的
     * 注释就写着"不再真正使用 Dialog，真的会有性能问题"，套一层 `OxideDialogShell`
     * 会凭空多出一层自己的窗口，把那个历史包袱请回来。它们改的是面板本身
     * （不透明底、圆角、描边、按钮）。
     *
     * `edit_widget/EditWidgetDialog.kt` 曾经也在这份名单里，但在 v1.10.0 按用户
     * 要求把整个编辑菜单推倒重做时，它被有意改成了真正的 `OxideDialogShell`
     * 底表：原来那个"页签列 + 零宽 NavDisplay + 满屏吃触摸"的结构在真机上
     * 渲染出一个点什么都没反应的黑洞（ct2），修布局的前提就是给它一个真正的窗口。
     * 所以它从这份名单里毕业，归 `EditorMenuDispatchGuardTest` 管。
     */
    @Test
    fun `两块全屏覆盖层仍然不是 Dialog 窗口`() {
        for (relative in listOf(
            "edit_style/EditStyleDialog.kt",
            "edit_joystick/EditStyleDialog.kt",
        )) {
            val code = codeOf(locateEditor(relative).readText())
            assertFalse(
                "$relative must not be turned into a Dialog window",
                code.contains("OxideDialogShell("),
            )
            assertFalse(
                "$relative must not import the Compose Dialog window",
                code.contains("androidx.compose.ui.window.Dialog"),
            )
            assertTrue(
                "$relative must paint the Oxide panel background itself",
                code.contains("editorPanelBackground()"),
            )
        }
    }

    /** 三条标签栏都由编辑器自己那一条画，不再是 Material 的 SecondaryTabRow */
    @Test
    fun `标签栏统一走 EditorDialogTabRow`() {
        for (relative in listOf(
            "edit_widget/ClickEventEdit.kt",
            "edit_widget/EditJoystickEvents.kt",
            "edit_style/EditStyleDialog.kt",
            "edit_joystick/EditStyleDialog.kt",
        )) {
            assertTrue(
                "$relative must use the shared Oxide tab row",
                codeOf(locateEditor(relative).readText()).contains("EditorDialogTabRow("),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** [source] 里 [token] 出现的次数 */
    private fun countOf(source: String, token: String): Int {
        var count = 0
        var index = source.indexOf(token)
        while (index >= 0) {
            count++
            index = source.indexOf(token, index + token.length)
        }
        return count
    }

    private fun locateEditor(relative: String): File = locateSource(
        "java/dev/oxide/launcher/ui/screens/main/control_editor/$relative"
    )
}