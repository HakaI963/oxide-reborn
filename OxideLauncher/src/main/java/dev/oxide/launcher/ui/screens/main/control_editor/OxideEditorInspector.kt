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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.oxide.layercontroller.data.ButtonSize
import dev.oxide.layercontroller.data.JOYSTICK_MIN_SIZE_DP
import dev.oxide.layercontroller.data.JOYSTICK_MIN_SIZE_PERCENTAGE
import dev.oxide.layercontroller.data.MIN_SIZE_DP
import dev.oxide.layercontroller.data.SIZE_PERCENTAGE_EDITOR
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.launcher.R
import kotlin.math.roundToInt

/**
 * 选中控件的检视器
 *
 * 这是整个编辑器里最要紧的一块，也是旧版最不好用的一块：位置与尺寸各挂在一个
 * Material 滑杆上，要精确值就得点开一个对话框，而那个对话框是盖在**正在接收
 * 游戏输入的画布**上面的。
 *
 * 换掉之后的形状：
 *
 * - **位置**：一块定位板（拖动）+ 两行行内数字（X / Y）+ 一行步进按钮 + 九宫格对齐。
 *   四条路径分别对应"大概挪一下"、"敲准数"、"一格一格修"、"一下摆正"。
 * - **尺寸**：一条滑杆（拖个大概）+ 一行行内数字（敲准数），两者共用同一个范围与
 *   同一个提交时机。宽高分开的两类控件给两个独立滑杆，摇杆只有一个边长。
 * - **可见场景**：就地展开的三选一，不弹窗。
 * - **其余**：完整设置仍然保留在"全部设置"里，那后面是旧的分区对话框，
 *   本轮没有重画（见报告）。
 */
@Composable
internal fun EditorInspector(
    widget: ObservableWidget,
    isPreviewMode: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    val kind = widget.editorKind()
    // 检视器里的控件一定属于某个已选中的层，因此"能不能新建"那一项里
    // 与本块有关的只有预览这一条
    val canEdit = editorAllowsGeometryEditing(
        isPreviewMode = isPreviewMode,
        blocker = editorAddBlocker(layerCount = 1, hasSelectedLayer = true, isPreviewMode = isPreviewMode),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
    ) {
        // ---- 名字 -------------------------------------------------------
        EditorInfoRow(
            label = widget.editorCellName(),
            hint = stringResource(kind.toStringRes()),
            enabled = canEdit,
        )

        // ---- 位置 -------------------------------------------------------
        EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_position))

        val position = widget.editorPosition()
        val readOut = editorPositionReadOut(position.x, position.y)
        EditorPadHeader(
            label = stringResource(R.string.oxide_ce_position_pad),
            readOut = readOut,
        )
        EditorPositionPad(
            label = stringResource(R.string.oxide_ce_position_pad),
            position = EditorStoredPosition(position.x, position.y),
            enabled = canEdit,
            onMove = { moved ->
                widget.editorSetPosition(moved.x, moved.y)
            },
            onMoveFinished = {
                // 抬手时才落盘：拖动的每一帧都写文件是不能要的
                widget.editorSetPosition(it.x, it.y)
            },
        )

        // 步进：触屏上没有"精确拖到 49.83%"这回事，因此补一对 ±
        // 步长取存储刻度的 0.5%，也就是屏幕宽高的 0.5%——一格刚好是肉眼能分辨的一下
        EditorNudgeRow(
            label = stringResource(R.string.oxide_ce_nudge_step),
            enabled = canEdit,
            onNudge = { direction ->
                val current = widget.editorPosition()
                widget.editorSetPosition(
                    editorNudge(current.x, EditorNudgeStep, direction),
                    editorNudge(current.y, EditorNudgeStep, direction),
                )
            },
        )

        EditorAnchorGrid(
            current = EditorStoredPosition(position.x, position.y),
            enabled = canEdit,
            onPick = { anchor ->
                val target = editorAnchorPosition(anchor)
                widget.editorSetPosition(target.x, target.y)
            },
        )

        // X 与 Y 各一行行内输入：这是"精确"那一档
        EditorNumberRow(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.control_editor_edit_position_x),
            value = position.x / 100f,
            range = 0f..100f,
            integerOnly = false,
            suffix = "%",
            enabled = canEdit,
            onCommit = { committed ->
                val current = widget.editorPosition()
                widget.editorSetPosition((committed * 100f).roundToInt(), current.y)
            },
        )
        EditorNumberRow(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.control_editor_edit_position_y),
            value = position.y / 100f,
            range = 0f..100f,
            integerOnly = false,
            suffix = "%",
            enabled = canEdit,
            onCommit = { committed ->
                val current = widget.editorPosition()
                widget.editorSetPosition(current.x, (committed * 100f).roundToInt())
            },
        )

        // ---- 尺寸 -------------------------------------------------------
        EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_size))

        val sizeType = widget.editorWidgetSizeType()
        val typeOptions = editorSizeTypesFor(kind)
        EditorSegmentRow(
            label = stringResource(R.string.control_editor_edit_size_type),
            options = typeOptions,
            selected = sizeType,
            enabled = canEdit,
            optionText = { type -> stringResource(type.toStringRes()) },
            onSelect = { picked -> widget.editorSetSizeType(picked) },
        )

        // 包裹内容时宽高由内容决定，因此既没有滑杆也没有输入行
        if (editorShowsSizeFields(sizeType)) {
            if (editorShowsSeparateWidthHeight(kind)) {
                // 宽与高各自一条滑杆 + 一个输入行
                val widthRange = editorSizeDpRange(sizeType, kind, screenWidthDp)
                val heightRange = editorSizeDpRange(sizeType, kind, screenHeightDp)
                if (editorShowsAbsoluteSize(sizeType)) {
                    EditorSliderRow(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.control_editor_edit_size_width),
                        value = widget.editorSizeWidthDp(),
                        valueRange = widthRange,
                        suffix = "dp",
                        decimals = 0,
                        integerOnly = false,
                        enabled = canEdit,
                        onValueChange = { widget.editorSetSizeDp(it) },
                        onValueChangeFinished = { widget.editorSetSizeDp(it) },
                    )
                    EditorSliderRow(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.control_editor_edit_size_height),
                        value = widget.editorSizeHeightDp(),
                        valueRange = heightRange,
                        suffix = "dp",
                        decimals = 0,
                        integerOnly = false,
                        enabled = canEdit,
                        onValueChange = { widget.editorSetHeightDp(it) },
                        onValueChangeFinished = { widget.editorSetHeightDp(it) },
                    )
                } else if (editorShowsPercentSize(sizeType)) {
                    EditorSliderRow(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.control_editor_edit_size_width),
                        value = widget.editorSizeWidthPercent(),
                        valueRange = editorSizePercentRange(kind),
                        suffix = "%",
                        decimals = 1,
                        integerOnly = false,
                        enabled = canEdit,
                        onValueChange = { widget.editorSetSizePercent(it, width = true) },
                        onValueChangeFinished = { widget.editorSetSizePercent(it, width = true) },
                    )
                    EditorSliderRow(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.control_editor_edit_size_height),
                        value = widget.editorSizeHeightPercent(),
                        valueRange = editorSizePercentRange(kind),
                        suffix = "%",
                        decimals = 1,
                        integerOnly = false,
                        enabled = canEdit,
                        onValueChange = { widget.editorSetSizePercent(it, width = false) },
                        onValueChangeFinished = { widget.editorSetSizePercent(it, width = false) },
                    )
                }
            } else {
                // 摇杆只有一个边长
                val absolute = editorShowsAbsoluteSize(sizeType)
                val percent = editorShowsPercentSize(sizeType)
                EditorSliderRow(
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.control_editor_edit_size_joystick),
                    value = when {
                        absolute -> widget.editorSizeWidthDp()
                        percent -> widget.editorSizeWidthPercent()
                        else -> 0f
                    },
                    valueRange = when {
                        absolute -> editorSizeDpRange(sizeType, kind, screenHeightDp)
                        percent -> editorSizePercentRange(kind)
                        // 摇杆永远不会落到包裹内容上；这一支只是让类型收得住
                        else -> 1f..100f
                    },
                    suffix = if (absolute) "dp" else "%",
                    decimals = if (absolute) 0 else 1,
                    integerOnly = false,
                    enabled = canEdit,
                    onValueChange = { committed ->
                        if (absolute) {
                            widget.editorSetSizeDp(committed)
                        } else {
                            widget.editorSetSizePercent(committed, width = true)
                        }
                    },
                    onValueChangeFinished = { committed ->
                        if (absolute) {
                            widget.editorSetSizeDp(committed)
                        } else {
                            widget.editorSetSizePercent(committed, width = true)
                        }
                    },
                )
            }

            // 百分比尺寸才有"参考屏幕宽还是高"
            if (editorShowsSizeReference(sizeType)) {
                EditorSegmentRow(
                    label = stringResource(R.string.control_editor_edit_size_width_reference),
                    options = ButtonSize.Reference.entries,
                    selected = widget.editorWidthReference(),
                    enabled = canEdit,
                    optionText = { reference -> stringResource(reference.toStringRes()) },
                    onSelect = { picked -> widget.editorSetWidthReference(picked) },
                )
                EditorSegmentRow(
                    label = stringResource(R.string.control_editor_edit_size_height_reference),
                    options = ButtonSize.Reference.entries,
                    selected = widget.editorHeightReference(),
                    enabled = canEdit,
                    optionText = { reference -> stringResource(reference.toStringRes()) },
                    onSelect = { picked -> widget.editorSetHeightReference(picked) },
                )
            }
        } else {
            EditorNoteRow(
                text = stringResource(R.string.oxide_ce_size_wrap_content)
            )
        }

        // ---- 可见场景 ---------------------------------------------------
        EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_visibility))
        EditorSegmentRow(
            label = stringResource(R.string.control_editor_edit_visibility),
            options = VisibilityType.entries,
            selected = widget.editorVisibility(),
            enabled = canEdit,
            optionText = { visibility -> visibility.getVisibilityText() },
            onSelect = { picked -> widget.editorSetVisibility(picked) },
        )

        // ---- 其余设置 ---------------------------------------------------
        EditorHairLine()
        EditorActionRow(
            label = stringResource(R.string.oxide_ce_advanced_settings),
            hint = stringResource(R.string.oxide_ce_advanced_settings_hint),
            onClick = onOpenAdvanced,
        )
    }
}

/** 位置读数："50% , 50%"，定位板标题、网格摘要与数字输入共用同一份写法 */
internal fun editorPositionReadOut(x: Int, y: Int): String =
    "${editorPercentText(x / 100f)} , ${editorPercentText(y / 100f)}"

/** 一个比例写成百分比文本 */
internal fun editorPercentText(fraction: Float): String =
    formatEditorValue(fraction * 100f, suffix = "%", decimals = 0)

/** 步进步长：存储刻度的 0.5% */
const val EditorNudgeStep: Int = EditorStoredMax / 200

/** 一条 dp 尺寸的取值范围：下界随控件种类，上界是屏幕那一边的边长 */
internal fun editorSizeDpRange(
    type: ButtonSize.Type,
    kind: EditorWidgetKind,
    screenLengthDp: Float,
): ClosedFloatingPointRange<Float> {
    val min = if (kind == EditorWidgetKind.Joystick) JOYSTICK_MIN_SIZE_DP else MIN_SIZE_DP
    // 屏幕特别窄时上界会小于下界（例如屏幕只有 3dp），那会让 coerceIn 抛异常，
    // 于是滑杆在最小的窗口上打不开。这里显式排好上下界。
    val max = screenLengthDp.coerceAtLeast(min)
    return min..max
}

/** 一条百分比尺寸的取值范围（编辑器口径：1f..100f，摇杆下界更高） */
internal fun editorSizePercentRange(kind: EditorWidgetKind): ClosedFloatingPointRange<Float> {
    val min = if (kind == EditorWidgetKind.Joystick) {
        JOYSTICK_MIN_SIZE_PERCENTAGE / 100f
    } else {
        SIZE_PERCENTAGE_EDITOR.start
    }
    val max = SIZE_PERCENTAGE_EDITOR.endInclusive
    return min.coerceAtMost(max)..max
}

/** 控件种类对应的那句名字 */
internal fun EditorWidgetKind.toStringRes(): Int = when (this) {
    EditorWidgetKind.Button -> R.string.oxide_ce_kind_button
    EditorWidgetKind.Text -> R.string.oxide_ce_kind_text
    EditorWidgetKind.Joystick -> R.string.oxide_ce_kind_joystick
}

/** 尺寸类型对应的那句名字 */
internal fun ButtonSize.Type.toStringRes(): Int = when (this) {
    ButtonSize.Type.Dp -> R.string.control_editor_edit_size_type_dp
    ButtonSize.Type.Percentage -> R.string.control_editor_edit_size_type_percentage
    ButtonSize.Type.WrapContent -> R.string.control_editor_edit_size_type_wrap_content
}

/** 参考边对应的那句名字 */
internal fun ButtonSize.Reference.toStringRes(): Int = when (this) {
    ButtonSize.Reference.ScreenWidth -> R.string.control_editor_edit_size_reference_screen_width
    ButtonSize.Reference.ScreenHeight -> R.string.control_editor_edit_size_reference_screen_height
}