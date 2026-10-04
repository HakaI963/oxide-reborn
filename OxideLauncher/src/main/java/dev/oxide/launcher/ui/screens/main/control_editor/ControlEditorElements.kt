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

package dev.oxide.launcher.ui.screens.main.control_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.oxide.layercontroller.data.JoystickTriggerMode
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.layercontroller.observable.ObservableClickEventsProvider
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableJoystickData
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
import dev.oxide.layercontroller.observable.ObservableNormalData
import dev.oxide.layercontroller.observable.ObservableTextData
import dev.oxide.layercontroller.observable.ObservableTranslatableString
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.launcher.R
import dev.oxide.launcher.bridge.CURSOR_DISABLED
import dev.oxide.launcher.bridge.CURSOR_ENABLED

/**
 * 编辑器的状态量
 *
 * 这些类型是 `EditorViewModel` 的公开接口——它的 `editorOperation` /
 * `editorWidgetOperation` / `editorWarningOperation` 三个字段的类型就是它们，
 * 因此必须留在包里、名字与形状都不能动。改的只是**呈现**：
 * 过去它们各自对应一个 Material 弹窗或一块 Zalith 菜单，现在全部由停靠面板
 * （见 `OxideEditorDock.kt`）里的分区与行来承载。
 */

/**
 * 控制布局编辑器操作状态
 */
sealed interface EditorOperation {
    data object None : EditorOperation
    /** 选择了一个控件进行编辑 */
    data object SelectButton : EditorOperation
    /** 编辑控件层属性 */
    data class EditLayer(val layer: ObservableControlLayer) : EditorOperation
    /** 删除控件层 */
    data class DeleteLayer(val layer: ObservableControlLayer) : EditorOperation
    /** 打开控件外观列表 */
    data object OpenStyleList : EditorOperation
    /** 创建控件外观 */
    data object CreateStyle : EditorOperation
    /** 编辑控件外观 */
    data object EditButtonStyle : EditorOperation
    /** 删除控件外观 */
    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation
    /** 编辑摇杆样式 */
    data object EditJoystickStyle : EditorOperation
    /** 删除摇杆样式 */
    data class DeleteJoystickStyle(val style: ObservableJoystickStyle) : EditorOperation
    /** 打开摇杆样式列表 */
    data object OpenJoystickStyleList : EditorOperation
    /** 创建摇杆样式 */
    data object CreateJoystickStyle : EditorOperation
    /** 控制布局正在保存中 */
    data object Saving : EditorOperation
    /** 控制布局保存失败 */
    data class SaveFailed(val error: Throwable) : EditorOperation
}

/**
 * 控制布局编辑器对控件的操作状态
 */
sealed interface EditorWidgetOperation {
    data object None : EditorWidgetOperation
    /** 选择了一个控件, 并询问用户将其复制到哪些控制层 */
    data class CloneButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    /** 删除一个控件 */
    data class DeleteButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    /** 编辑控件的显示文本 */
    data class EditWidgetText(val string: ObservableTranslatableString) : EditorWidgetOperation
    /** 编辑切换控件层可见性事件 */
    data class SwitchLayersVisibility(
        val data: ObservableClickEventsProvider,
        val type: ClickEvent.Type
    ) : EditorWidgetOperation

    /** 编辑发送的文本 */
    data class SendText(val data: ObservableClickEventsProvider) : EditorWidgetOperation
}

/**
 * 控制布局编辑器的一些警告的操作状态
 */
sealed interface EditorWarningOperation {
    data object None : EditorWarningOperation
    /** 没有控件层，提醒用户添加 */
    data object WarningNoLayers : EditorWarningOperation
    /** 没有选择控件层，提醒用户选择 */
    data object WarningNoSelectLayer : EditorWarningOperation
}

/**
 * 预览控制布局的场景
 */
enum class PreviewScenario(
    val textRes: Int,
    val cursorMode: Int,
    val isCursorGrabbing: Boolean = cursorMode == CURSOR_DISABLED
) {
    InGame(R.string.control_editor_menu_preview_mode_in_game, cursorMode = CURSOR_DISABLED),
    InMenu(R.string.control_editor_menu_preview_mode_in_menu, cursorMode = CURSOR_ENABLED)
}

@Composable
fun VisibilityType.getVisibilityText(): String {
    val textRes = when (this) {
        VisibilityType.ALWAYS -> R.string.control_editor_edit_visibility_always
        VisibilityType.IN_GAME -> R.string.control_editor_edit_visibility_in_game
        VisibilityType.IN_MENU -> R.string.control_editor_edit_visibility_in_menu
    }
    return stringResource(textRes)
}

@Composable
fun JoystickTriggerMode.getTriggerModeText(): String {
    val textRes = when (this) {
        JoystickTriggerMode.DRAG -> R.string.control_editor_edit_joystick_trigger_mode_drag
        JoystickTriggerMode.TOUCH -> R.string.control_editor_edit_joystick_trigger_mode_touch
    }
    return stringResource(textRes)
}

/** 控件种类。这个函数是唯一需要认得三种控件类型的地方 */
internal fun ObservableWidget.editorKind(): EditorWidgetKind = when (this) {
    is ObservableNormalData -> EditorWidgetKind.Button
    is ObservableTextData -> EditorWidgetKind.Text
    is ObservableJoystickData -> EditorWidgetKind.Joystick
    // 以后新增控件类型时这里必须一起改：漏一个就会退回到按键的形状，
    // 与它在画布上的样子对不上
    else -> EditorWidgetKind.Button
}