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
import dev.oxide.layercontroller.data.ButtonPosition
import dev.oxide.layercontroller.data.ButtonSize
import dev.oxide.layercontroller.data.CenterPosition
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.observable.ObservableJoystickData
import dev.oxide.layercontroller.observable.ObservableNormalData
import dev.oxide.layercontroller.observable.ObservableTextData
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.launcher.R
import kotlin.math.roundToInt

/**
 * 三种控件的读写适配
 *
 * `ObservableWidget` 的位置、大小与可见场景都是 `internal` 的抽象成员，编辑器拿不到；
 * 三种实现又各有各的字段名（摇杆是 `sizeDp` / `sizePercentage`，另两种裹在 `buttonSize`
 * 里）。这一族把差别全部收在这里，于是检视器与网格摘要都只写一遍逻辑，
 * 将来加第四种控件只需要在这里多写一个分支。
 *
 * 全部是平凡的字段转发，不含任何判断，因此没有单独的单测——
 * 有意义的那部分（范围换算、步进、九宫格）都在 `OxideControlEditorLogic.kt` 里，
 * 那里是纯函数。
 */

/** 网格里那一格显示的名字 */
@Composable
internal fun ObservableWidget.editorCellName(): String = when (this) {
    is ObservableNormalData -> text.translate().ifBlank {
        stringResource(R.string.control_editor_edit_button_default)
    }

    is ObservableTextData -> text.translate().ifBlank {
        stringResource(R.string.control_editor_edit_text_default)
    }

    is ObservableJoystickData -> stringResource(R.string.oxide_ce_kind_joystick)
    // 新增控件类型时这里必须一起改：漏掉就会退化成按键的默认名，
    // 与它在画布上的样子对不上
    else -> stringResource(R.string.oxide_ce_kind_button)
}

/** 网格里那一格显示的摘要：位置 + 尺寸类型 */
@Composable
internal fun ObservableWidget.editorCellSummary(): String {
    val position = editorPosition()
    val typeText = stringResource(editorWidgetSizeType().toStringRes())
    return "${editorPositionReadOut(position.x, position.y)} · $typeText"
}

/** 控件类型的那枚字形 */
internal fun ObservableWidget.editorKindGlyph(): String = when (editorKind()) {
    EditorWidgetKind.Button -> "▣"
    EditorWidgetKind.Text -> "▤"
    EditorWidgetKind.Joystick -> "✳"
}

/** 这个控件的存储位置 */
internal fun ObservableWidget.editorPosition(): ButtonPosition = when (this) {
    is ObservableNormalData -> position
    is ObservableTextData -> position
    is ObservableJoystickData -> position
    // 新增控件类型时这里必须一起改，否则位置读数会永远停在 (0, 0)
    else -> CenterPosition
}

/** 写位置。两个轴都给，因此拖动手柄与定位板走的是同一条路径 */
internal fun ObservableWidget.editorSetPosition(x: Int, y: Int) {
    val clampedX = x.coerceIn(0, EditorStoredMax)
    val clampedY = y.coerceIn(0, EditorStoredMax)
    when (this) {
        is ObservableNormalData -> position = position.copy(x = clampedX, y = clampedY)
        is ObservableTextData -> position = position.copy(x = clampedX, y = clampedY)
        is ObservableJoystickData -> position = position.copy(x = clampedX, y = clampedY)
    }
}

/** 尺寸类型 */
internal fun ObservableWidget.editorWidgetSizeType(): ButtonSize.Type = when (this) {
    is ObservableNormalData -> buttonSize.type
    is ObservableTextData -> buttonSize.type
    is ObservableJoystickData -> sizeType
    else -> ButtonSize.Type.Percentage
}

/** 写尺寸类型 */
internal fun ObservableWidget.editorSetSizeType(type: ButtonSize.Type) {
    when (this) {
        is ObservableNormalData -> buttonSize = buttonSize.copy(type = type)
        is ObservableTextData -> buttonSize = buttonSize.copy(type = type)
        is ObservableJoystickData -> sizeType = type
    }
}

/** dp 尺寸（宽） */
internal fun ObservableWidget.editorSizeWidthDp(): Float = when (this) {
    is ObservableNormalData -> buttonSize.widthDp
    is ObservableTextData -> buttonSize.widthDp
    is ObservableJoystickData -> sizeDp
    else -> 50f
}

/** dp 尺寸（高）。摇杆只有一个边长，两个轴给同一个值 */
internal fun ObservableWidget.editorSizeHeightDp(): Float = when (this) {
    is ObservableNormalData -> buttonSize.heightDp
    is ObservableTextData -> buttonSize.heightDp
    is ObservableJoystickData -> sizeDp
    else -> 50f
}

/** 写 dp 尺寸（宽）。摇杆那条路径只有一个数，因此高也一起写 */
internal fun ObservableWidget.editorSetSizeDp(value: Float) {
    when (this) {
        is ObservableNormalData -> buttonSize = buttonSize.copy(widthDp = value)
        is ObservableTextData -> buttonSize = buttonSize.copy(widthDp = value)
        is ObservableJoystickData -> sizeDp = value
    }
}

/** 写 dp 尺寸（高） */
internal fun ObservableWidget.editorSetHeightDp(value: Float) {
    when (this) {
        is ObservableNormalData -> buttonSize = buttonSize.copy(heightDp = value)
        is ObservableTextData -> buttonSize = buttonSize.copy(heightDp = value)
    }
}

/** 百分比尺寸（宽），编辑器口径是 1f..100f */
internal fun ObservableWidget.editorSizeWidthPercent(): Float = when (this) {
    is ObservableNormalData -> buttonSize.widthPercentage / 100f
    is ObservableTextData -> buttonSize.widthPercentage / 100f
    is ObservableJoystickData -> sizePercentage / 100f
    else -> 14f
}

/** 百分比尺寸（高） */
internal fun ObservableWidget.editorSizeHeightPercent(): Float = when (this) {
    is ObservableNormalData -> buttonSize.heightPercentage / 100f
    is ObservableTextData -> buttonSize.heightPercentage / 100f
    is ObservableJoystickData -> sizePercentage / 100f
    else -> 14f
}

/** 写百分比尺寸。[width] 为假时写高，摇杆只有一条路径所以两个方向都写 */
internal fun ObservableWidget.editorSetSizePercent(value: Float, width: Boolean) {
    val stored = (value * 100f).roundToInt()
    when (this) {
        is ObservableNormalData -> buttonSize = if (width) {
            buttonSize.copy(widthPercentage = stored)
        } else {
            buttonSize.copy(heightPercentage = stored)
        }

        is ObservableTextData -> buttonSize = if (width) {
            buttonSize.copy(widthPercentage = stored)
        } else {
            buttonSize.copy(heightPercentage = stored)
        }

        is ObservableJoystickData -> sizePercentage = stored
    }
}

/** 宽度参考边。摇杆没有这一项，返回屏幕高即可 */
internal fun ObservableWidget.editorWidthReference(): ButtonSize.Reference = when (this) {
    is ObservableNormalData -> buttonSize.widthReference
    is ObservableTextData -> buttonSize.widthReference
    is ObservableJoystickData -> ButtonSize.Reference.ScreenHeight
    else -> ButtonSize.Reference.ScreenHeight
}

/** 写宽度参考边 */
internal fun ObservableWidget.editorSetWidthReference(reference: ButtonSize.Reference) {
    when (this) {
        is ObservableNormalData -> buttonSize = buttonSize.copy(widthReference = reference)
        is ObservableTextData -> buttonSize = buttonSize.copy(widthReference = reference)
    }
}

/** 高度参考边 */
internal fun ObservableWidget.editorHeightReference(): ButtonSize.Reference = when (this) {
    is ObservableNormalData -> buttonSize.heightReference
    is ObservableTextData -> buttonSize.heightReference
    is ObservableJoystickData -> ButtonSize.Reference.ScreenHeight
    else -> ButtonSize.Reference.ScreenHeight
}

/** 写高度参考边 */
internal fun ObservableWidget.editorSetHeightReference(reference: ButtonSize.Reference) {
    when (this) {
        is ObservableNormalData -> buttonSize = buttonSize.copy(heightReference = reference)
        is ObservableTextData -> buttonSize = buttonSize.copy(heightReference = reference)
    }
}

/** 可见场景 */
@Composable
internal fun ObservableWidget.editorVisibility(): VisibilityType = when (this) {
    is ObservableNormalData -> visibilityType
    is ObservableTextData -> visibilityType
    is ObservableJoystickData -> visibilityType
    else -> VisibilityType.ALWAYS
}

/** 写可见场景 */
internal fun ObservableWidget.editorSetVisibility(value: VisibilityType) {
    when (this) {
        is ObservableNormalData -> visibilityType = value
        is ObservableTextData -> visibilityType = value
        is ObservableJoystickData -> visibilityType = value
    }
}