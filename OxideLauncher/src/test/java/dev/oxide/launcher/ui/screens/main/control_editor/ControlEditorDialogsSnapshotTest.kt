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

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.layercontroller.data.ButtonStyle
import dev.oxide.layercontroller.data.DefaultButtonStyleConfig
import dev.oxide.layercontroller.data.DefaultJoystickStyleConfig
import dev.oxide.layercontroller.data.JoystickStyle
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
import dev.oxide.launcher.ui.screens.main.control_editor.edit_joystick.JoystickStyleListDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_style.StyleListDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideConfirmDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideTextEntryDialog
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.paparazziFor
import org.junit.Rule
import org.junit.Test

/**
 * 编辑器对话框的 golden
 *
 * 用户报的两张图——`controlpop.jpg` 的 "Joystick Style List" 与 `controlpop2.jpg` 的
 * "Control Appearance List"——就是这一族里最后两张旧样式的弹窗：Material 的 `Surface`
 * 卡片，灰底加鲑鱼色按钮，压在 Oxide 的停靠面板上。源码那一侧的守卫在
 * [ControlEditorLegacyDialogGuardTest]；这一族钉的是**长什么样**。
 *
 * 拍的是**真的那两张弹窗**，不是拿证据拼出来的近似物，因此能钉住的是三件事：面板底色
 * 不透明、圆角与 1px 描边是 Oxide 的、底部两枚按钮是描边（Create）与实心（Close）两档。
 *
 * 七张，多的不做：
 *
 * - [JoystickStyleList_Empty]：`controlpop.jpg` 拍到的那一屏（列表为空，正文是一行可点
 *   的提示）。
 * - [JoystickStyleList_Populated]：同一张弹窗但有两行——每行的预览、名称与两枚图标按钮。
 * - [StyleList_Empty] / [StyleList_Populated]：`controlpop2.jpg` 那一对。
 * - [EditorConfirm_DeleteLayer]：删除控件层那条确认，与两张列表弹窗由同一份
 *   `EditorOperationDialogs` 挂出来。
 * - [EditorTextEntry_CreateStyle] 与它的空值那一档：新建外观名称的输入框。空值那一档存在的
 *   理由是**反向证据**——旧的输入框没有任何校验，空名字也能确认（见
 *   `OxideEditorDialogs.kt` 里的 `allowBlank = true`）。
 *
 * 数据全是字面量，uuid 也写死：夹具走 `DefaultButtonStyleConfig` 而不是
 * `createNewButtonStyle`（里面是 `randomUUID()`）——见 `OxidePaparazzi` 第 2 条，
 * golden 里没有随机数。
 *
 * 弹窗自身的文案仍然是 `stringResource(...)`，因此这几张 golden 顺带把"文案一个字没改"
 * 也钉住了：换了 key 或者漏了翻译，图片会跟着变。
 */
class ControlEditorDialogsSnapshotTest {

    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    // -----------------------------------------------------------------------
    // Joystick styles
    // -----------------------------------------------------------------------

    /** `controlpop.jpg`：一个摇杆样式都还没有的那一屏 */
    @Test
    fun JoystickStyleList_Empty() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_JoystickStyleList_Empty", device) {
            EditorShell(device) {
                JoystickStyleListDialog(
                    styles = emptyList(),
                    onEditStyle = {},
                    onCreate = {},
                    onClone = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
    }

    /** 同一张弹窗，两行：预览、名称、复制、删除 */
    @Test
    fun JoystickStyleList_Populated() {
        val device = OxidePaparazzi.STANDARD
        // 取值放在组合之外：夹具不该在组合过程中变化
        val styles = listOf(
            joystickStyle(name = "oxide-default", uuid = "joystickstyle01"),
            joystickStyle(name = "compact", uuid = "joystickstyle02"),
        )
        paparazzi.shot("ControlEditor_JoystickStyleList_Populated", device) {
            EditorShell(device) {
                JoystickStyleListDialog(
                    styles = styles,
                    onEditStyle = {},
                    onCreate = {},
                    onClone = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Button styles
    // -----------------------------------------------------------------------

    /** `controlpop2.jpg`：控件外观列表的空前状态 */
    @Test
    fun StyleList_Empty() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_StyleList_Empty", device) {
            EditorShell(device) {
                StyleListDialog(
                    styles = emptyList(),
                    onEditStyle = {},
                    onCreate = {},
                    onClone = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
    }

    /** 控件外观列表有两行——与截图里那两条 "oxide-default" 同形 */
    @Test
    fun StyleList_Populated() {
        val device = OxidePaparazzi.STANDARD
        val styles = listOf(
            buttonStyle(name = "oxide-default", uuid = "buttonstyle001"),
            buttonStyle(name = "oxide-default", uuid = "buttonstyle002"),
        )
        paparazzi.shot("ControlEditor_StyleList_Populated", device) {
            EditorShell(device) {
                StyleListDialog(
                    styles = styles,
                    onEditStyle = {},
                    onCreate = {},
                    onClone = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 编辑器里共用同两个对话框
    // -----------------------------------------------------------------------

    /**
     * 删除控件层的那条确认
     *
     * 钉住"确认是实心、取消是描边、正文落在有界的内容区里"。
     */
    @Test
    fun EditorConfirm_DeleteLayer() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_Confirm_DeleteLayer", device) { metrics ->
            OxideConfirmDialog(
                title = "Delete",
                message = "Delete layer \"movement\"?",
                confirmText = "Confirm",
                cancelText = "Cancel",
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    /** 新建控件外观名称——输入框里有内容那一档 */
    @Test
    fun EditorTextEntry_CreateStyle() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_TextEntry_CreateStyle", device) { metrics ->
            OxideTextEntryDialog(
                title = "Style name",
                label = "Style name",
                value = "oxide-default",
                onValueChange = {},
                confirmText = "Confirm",
                cancelText = "Cancel",
                allowBlank = true,
                singleLine = true,
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    /**
     * 空名字那一档：确认仍然**亮着**
     *
     * 这是上面那条 `allowBlank = true` 的反向证据——旧的输入框没有校验，空名字也能
     * 确认。谁把它删掉，这一张立刻与实现对不上。
     */
    @Test
    fun EditorTextEntry_CreateStyle_BlankStillConfirmable() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_TextEntry_CreateStyle_Blank", device) { metrics ->
            OxideTextEntryDialog(
                title = "Style name",
                label = "Style name",
                value = "",
                onValueChange = {},
                confirmText = "Confirm",
                cancelText = "Cancel",
                allowBlank = true,
                singleLine = true,
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Composition
    // -----------------------------------------------------------------------

    /**
     * 把编辑器自己的尺寸喂进去
     *
     * 两张列表弹窗的行高来自 `LocalEditorMetrics`，而它的默认值是 640x360 那一档。给它
     * 传与 [device] 同尺寸的那一份，"被测的尺寸就是断言的尺寸"这条才继续成立——用的就是
     * `ControlEditorSnapshotTest` 里同一条通道，没有另开新 API。
     */
    @Composable
    private fun EditorShell(
        device: DeviceConfig,
        content: @Composable () -> Unit,
    ) {
        ProvideEditorMetrics(
            controlEditorMetricsFor(
                widthDp = device.screenWidth,
                heightDp = device.screenHeight,
                guiScalePercent = 100,
            )
        ) {
            content()
        }
    }

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    /**
     * 一个摇杆外观，uuid 是字面量
     *
     * 配置取自 `DefaultJoystickStyleConfig`，因此这张图只需要关心列表那一行的排版，
     * 不会把"默认配色"也一起冻进 golden。
     */
    private fun joystickStyle(name: String, uuid: String): ObservableJoystickStyle = ObservableJoystickStyle(
        JoystickStyle(
            name = name,
            uuid = uuid,
            commonStyle = true,
            lightStyle = DefaultJoystickStyleConfig,
            darkStyle = DefaultJoystickStyleConfig,
        )
    )

    /** 一个控件外观，uuid 是字面量。同 [joystickStyle]，理由逐字相同 */
    private fun buttonStyle(name: String, uuid: String): ObservableButtonStyle = ObservableButtonStyle(
        ButtonStyle(
            name = name,
            uuid = uuid,
            animateSwap = false,
            commonStyle = true,
            lightStyle = DefaultButtonStyleConfig,
            darkStyle = DefaultButtonStyleConfig,
        )
    )
}