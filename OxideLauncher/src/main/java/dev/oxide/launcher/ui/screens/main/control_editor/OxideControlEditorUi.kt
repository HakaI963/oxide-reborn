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

import dev.oxide.launcher.ui.theme.Oxide
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.oxide.lerpColor
import kotlin.math.roundToInt

/**
 * 控制布局编辑器的控件
 *
 * 这些控件全部由 [Oxide] 的记号（颜色、圆角、字号、动效）直接拼出来，没有一处
 * `MaterialTheme`：编辑器压在**正在接收游戏输入**的画布上，Material 的默认配色与
 * 圆角在这套近黑语言里会明显跳出来，而它的控件高度也比这里大一倍。
 *
 * 四条在这个场景下才算数的规则：
 *
 * 1. **不做每帧的工作**。格式化按输入缓存，数字解析只发生在一次提交之前，
 *    轨道与定位板用 `Canvas` 一遍画完而不是拼一串 `Box`。
 * 2. **不抢画布的触摸**。每一行只消费自己收到的事件，[editorConsumeTouches] 只挂在
 *    停靠面板与遮罩这两块"整片都该吃掉"的地方，因此面板外的那一下点击仍然落到
 *    控制布局上，而不是被顺手吞掉。
 * 3. **状态不靠颜色说话**。开关用 `Role.Switch`，单选用 `Role.RadioButton`，滑杆给出
 *    `ProgressBarRangeInfo` 与 `setProgress`，选中项除了强调色还多一条指示条与
 *    一枚实心方块；行内输入的错误除了描边还另起一行文字。
 * 4. **触摸目标是真的**。行高、格子与步进按钮都从 [ControlEditorMetrics] 来，
 *    最挤的一档也有 26dp；行内输入的确认与取消是两块独立的小方块，不靠键盘。
 */

// ---------------------------------------------------------------------------
// 尺寸
// ---------------------------------------------------------------------------

/**
 * 当前编辑器的尺寸
 *
 * 用组合局部而不是逐层传参：滑杆、输入框、定位板都要用到行高与旋钮半径，
 * 让每个调用点都写一遍既啰嗦又容易在某一处漏乘界面缩放。
 * 默认值就是启动器的下限 640x360，因此任何忘记提供的地方仍然按最挤的一档算。
 */
val LocalEditorMetrics = staticCompositionLocalOf {
    controlEditorMetricsFor(widthDp = 640, heightDp = 360)
}

/** 提供编辑器尺寸 */
@Composable
internal fun ProvideEditorMetrics(
    metrics: ControlEditorMetrics,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalEditorMetrics provides metrics, content = content)
}

/** 取当前编辑器尺寸 */
@Composable
internal fun editorMetrics(): ControlEditorMetrics = LocalEditorMetrics.current

// ---------------------------------------------------------------------------
// 文本
// ---------------------------------------------------------------------------

/** 分区标题：6sp 大写宽字距 */
@Composable
internal fun EditorGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Oxide.FgDim,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .height(editorMetrics().groupLabelHeight),
    )
}

/** 说明行（例如"这个控制层还是空的"） */
@Composable
internal fun EditorNoteRow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Oxide.FgFaint,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------------------------
// 选中与展开的小标记
// ---------------------------------------------------------------------------

/**
 * 选中指示：左侧一条竖轨 + 中间一枚实心方块
 *
 * 只有强调色是不行的——高对比度模式、色觉差异与灰度打印都会把它抹掉。
 * 因此选中项同时多一条轨、一枚方块，并交给 `selectable(selected = …)`，
 * 朗读时也知道"现在选的是哪一个"。
 */
@Composable
internal fun EditorSelectedMark(selected: Boolean, modifier: Modifier = Modifier) {
    val metrics = editorMetrics()
    val width = if (selected) 2.dp else 0.dp
    Row(
        modifier = modifier.height(metrics.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(metrics.rowHeight)
                .clip(Oxide.RadiusBadge)
                .background(if (selected) Oxide.Fg else Color.Transparent),
        )
        Box(
            modifier = Modifier
                .size(if (selected) 4.dp else 0.dp)
                .clip(Oxide.RadiusBadge)
                .background(Oxide.Fg),
        )
    }
}

/** 展开/收起的箭头，两条斜线画出来，因此正反两个方向都读得出来 */
@Composable
internal fun EditorChevron(expanded: Boolean, enabled: Boolean = true) {
    Canvas(
        modifier = Modifier
            .size(9.dp)
            .rotate(if (expanded) 180f else 0f)
    ) {
        val stroke = 1.dp.toPx()
        val color = if (enabled) Oxide.FgGhost else Oxide.Line
        drawLine(
            color = color,
            start = Offset(size.width * 0.12f, size.height * 0.36f),
            end = Offset(size.width * 0.5f, size.height * 0.72f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.72f),
            end = Offset(size.width * 0.88f, size.height * 0.36f),
            strokeWidth = stroke,
        )
    }
}

// ---------------------------------------------------------------------------
// 行
// ---------------------------------------------------------------------------

/**
 * "标签 + 值"的一行
 *
 * [onClick] 为 null 时整行不可点，只当标签用。值那一列右对齐，因此一列尺寸字段
 * 上下对齐；[hint] 会另起一行小字，说明范围或单位。
 */
@Composable
internal fun EditorInfoRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        value?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/**
 * 开关行
 *
 * 整行都是热区（`toggleable`），因此在触屏上不必瞄准一块 26x15 的小东西；
 * 选中态由 `Role.Switch` 与 `ToggleableState` 交给无障碍服务，滑块的位置只是
 * 第二重线索。
 */
@Composable
internal fun EditorSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        EditorSwitchMark(checked = checked, enabled = enabled)
    }
}

/** 开关的样子：纯绘制，点击由整行的 `toggleable` 负责 */
@Composable
private fun EditorSwitchMark(checked: Boolean, enabled: Boolean) {
    val track by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(140),
        label = "editorSwitch",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(14.dp)
            .clip(shape)
            .background(
                lerpColor(
                    if (enabled) Oxide.BgToggleOff else Oxide.Line,
                    if (enabled) Oxide.BgToggleOn else Oxide.Line,
                    track,
                )
            )
            .border(BorderStroke(1.dp, Oxide.Line), shape),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        checked -> Color(0xFF1A1A1A)
                        enabled -> Oxide.FgFaint
                        else -> Oxide.Line
                    }
                )
        )
    }
}

/**
 * 动作行
 *
 * 末尾那块小方块只是"可点"的视觉提示，不是状态，因此不参与朗读。
 */
@Composable
internal fun EditorActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    hint: String? = null,
) {
    val metrics = editorMetrics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.rowHeight.coerceAtLeast(26.dp))
            .clip(Oxide.RadiusControl)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = when {
                    !enabled -> Oxide.FgFaint
                    emphasis -> Oxide.FgStrong
                    else -> Oxide.Fg
                },
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(Oxide.RadiusBadge)
                .background(if (enabled) Oxide.FgGhost else Oxide.Line)
        )
    }
}

/**
 * 分段单选：2 到 4 个短选项时就地铺开，不藏起来
 *
 * 选项再多就交给 [EditorChoiceRow] 展开，否则一行会被挤到读不出来。
 */
@Composable
internal fun <T> EditorSegmentRow(
    label: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 每一格里额外画的东西，通常是一个图标
     *
     * 有它就不用为了"这一项只有图标没有文字"再写一份控件：文字给朗读，图标给视觉。
     */
    optionContent: (@Composable (T) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .selectableGroup()
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) Oxide.FgFaint else Oxide.Line,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(Oxide.RadiusChip)
                        .background(if (isSelected) Oxide.BgTabActive else Color.Transparent)
                        .border(
                            BorderStroke(1.dp, if (isSelected) Oxide.Line2 else Oxide.Line),
                            Oxide.RadiusChip,
                        )
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (optionContent != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides when {
                                !enabled -> Oxide.FgFaint
                                isSelected -> Oxide.Fg
                                else -> Oxide.FgGhost
                            }
                        ) {
                            optionContent(option)
                        }
                    } else {
                        Text(
                            text = optionText(option),
                            color = when {
                                !enabled -> Oxide.Line
                                isSelected -> Oxide.Fg
                                else -> Oxide.FgGhost
                            },
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单选行：点开就在行下方就地列出候选项
 *
 * 不用下拉也不用弹窗——编辑器压在画布上，一个 Material 弹窗会盖掉整块正在编辑的
 * 画面，就地展开视线也不用离开正在做的事。候选多到铺不下时才给这一块自己的滚动，
 * 并且高度被夹住，因此不存在无界的嵌套滚动。
 */
@Composable
internal fun <T> EditorChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = optionText(selected)
    val expandText = stringResource(R.string.generic_expand)
    val collapseText = stringResource(R.string.generic_collapse)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .semantics {
                    role = Role.Tab
                    stateDescription = if (expanded) collapseText else expandText
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = current,
                color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
            Spacer(Modifier.width(6.dp))
            EditorChevron(expanded = expanded, enabled = enabled)
        }

        AnimatedVisibility(
            visible = expanded && enabled && options.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeIn(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
            exit = shrinkVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeOut(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp),
            ) {
                options.forEach { option ->
                    EditorOptionRow(
                        text = optionText(option),
                        selected = option == selected,
                        onClick = {
                            if (option != selected) onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 一个候选项：选中态由左侧那块方块承担，整行带 `Role.RadioButton` */
@Composable
private fun EditorOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusSmall)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(Oxide.RadiusBadge)
                .border(
                    BorderStroke(1.dp, if (selected) Oxide.FgMuted else Oxide.Line2),
                    Oxide.RadiusBadge,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(Oxide.RadiusBadge)
                        .background(Oxide.FgMuted)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = if (selected) Oxide.Fg else Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 滑杆
// ---------------------------------------------------------------------------

/**
 * 滑杆行
 *
 * 拖轨道改值；点右边的数值就展开一行行内输入，可以直接敲精确的数字——
 * 这正是旧的 `SliderValueEditDialog` 承担的事，现在换成与启动器同一语言的输入行，
 * 并且不必在编辑中的画布上盖起一个 Material 弹窗。
 *
 * [onValueChange] 在拖动过程中会被连续调用（只改内存状态，不落盘），
 * [onValueChangeFinished] 在抬手或提交时调用一次，落盘与刷新都放在那里。
 */
@Composable
internal fun EditorSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String? = null,
    decimals: Int = 0,
    integerOnly: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    val text = remember(value, suffix, decimals) { formatEditorValue(value, suffix, decimals) }
    val fraction = remember(value, valueRange) { editorSliderFraction(value, valueRange) }
    val plainText = remember(value, decimals) { formatEditorValue(value, null, decimals) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            val chipDescription = remember(label, text) { "$label: $text" }
            Box(
                modifier = Modifier
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.BgChip)
                    .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBadge)
                    .clickable(enabled = enabled) { editing = !editing }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = chipDescription
                    },
            ) {
                Text(
                    text = text,
                    color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
            }
        }

        EditorTrack(
            fraction = fraction,
            valueRange = valueRange,
            enabled = enabled,
            label = label,
            stateText = text,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        AnimatedVisibility(
            visible = editing && enabled,
            enter = expandVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeIn(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
            exit = shrinkVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeOut(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
        ) {
            EditorNumberField(
                label = label,
                initialText = plainText,
                range = valueRange,
                integerOnly = integerOnly,
                suffix = suffix,
                onCommit = { committed ->
                    editing = false
                    onValueChangeFinished(committed)
                },
                onDismiss = { editing = false },
            )
        }
    }
}

/**
 * 细轨道
 *
 * 一遍 `Canvas` 画完：底轨、已选段与滑块各一个形状，没有一串 `Box`，
 * 拖动时也就不会每帧重新排版。手势自己实现按下 / 移动 / 抬手，
 * 因此点击轨道等于跳到那一格——和 Material 的滑块一致，用的却是 Oxide 的颜色。
 */
@Composable
private fun EditorTrack(
    fraction: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    label: String,
    stateText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    // 手势协程只随 valueRange 重启，因此回调要跟着组合走，不能抓着创建时的那一份
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinish by rememberUpdatedState(onValueChangeFinished)
    val currentFraction by rememberUpdatedState(fraction)

    val knobRadius = metrics.trackKnobRadius
    val trackHeight = metrics.trackHeight

    Canvas(
        modifier = modifier
            .height(knobRadius * 2f)
            .then(
                if (enabled) {
                    Modifier.pointerInput(valueRange) {
                        var current = currentFraction
                        fun apply(x: Float) {
                            val f = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else current
                            current = f
                            currentChange(editorSliderValue(f, valueRange))
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val pointerId = down.id
                            apply(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                if (change.position != change.previousPosition) apply(change.position.x)
                                change.consume()
                            }
                            currentFinish(editorSliderValue(current, valueRange))
                        }
                    }
                } else {
                    Modifier
                }
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                contentDescription = "$label: $stateText"
                setProgress { requested ->
                    val target = requested.coerceIn(0f, 1f)
                    currentChange(editorSliderValue(target, valueRange))
                    currentFinish(editorSliderValue(target, valueRange))
                    true
                }
            }
    ) {
        val radius = trackHeight.toPx() / 2f
        val centerY = size.height / 2f
        val knob = knobRadius.toPx()

        drawRoundRect(
            color = Oxide.Line,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width, radius * 2f),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            topLeft = Offset(0f, centerY - radius),
            size = Size((size.width * fraction).coerceAtLeast(radius * 2f), radius * 2f),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawCircle(
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            radius = knob,
            // 轨道比滑块还窄时也不会崩：上下界在这里显式排好，
            // 而不是交给会抛异常的 coerceIn
            center = Offset(
                (size.width * fraction).coerceIn(
                    minOf(knob, size.width / 2f),
                    maxOf(size.width - knob, size.width / 2f),
                ),
                centerY,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// 数字输入
// ---------------------------------------------------------------------------

/**
 * 固定宽高的整数输入（宽高、存储刻度）
 *
 * [hint] 会作为占位文字显示，因此范围一直看得见，不必先试一次才知道能填多少。
 */
@Composable
internal fun EditorNumberRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    integerOnly: Boolean = true,
    suffix: String? = null,
    hint: String? = null,
) {
    val initialText = remember(value, decimalsOf(value)) {
        formatEditorValue(value, null, decimalsOf(value))
    }
    EditorNumberField(
        label = label,
        initialText = initialText,
        range = range,
        integerOnly = integerOnly,
        suffix = suffix,
        hint = hint,
        onCommit = onCommit,
        onDismiss = {},
        modifier = modifier,
        enabled = enabled,
    )
}

/** 一个值该显示几位小数：能整除就不给小数位，否则留两位 */
private fun decimalsOf(value: Float): Int =
    if (value == value.roundToInt().toFloat()) 0 else 2

/**
 * 行内数字输入行
 *
 * 输入框、确认、取消，以及出错时另起一行的文字说明。错误除了把描边抬到正文色
 * 之外还有这句文字，因此不会只靠颜色表达。
 */
@Composable
internal fun EditorNumberField(
    label: String,
    initialText: String,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    integerOnly: Boolean = true,
    suffix: String? = null,
    hint: String? = null,
) {
    val metrics = editorMetrics()
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(initialText) }
    val error = remember(text, range, integerOnly) { editorNumberError(text, range, integerOnly) }

    val tooSmall = stringResource(R.string.generic_input_too_small, range.start.toInt().toString())
    val tooLarge = stringResource(R.string.generic_input_too_large, range.endInclusive.toInt().toString())
    val notANumber = stringResource(R.string.generic_input_failed_to_number)
    val confirmText = stringResource(R.string.generic_confirm)
    val closeText = stringResource(R.string.generic_close)

    // 提交只发生一次：清焦点也会走一遍 commit（失焦提交），
    // 不挡住的话确认键与失焦会把同一次编辑提交两遍
    var done by remember { mutableStateOf(false) }

    fun commit() {
        if (done) return
        val parsed = editorNumberIn(text, range, integerOnly) ?: return
        done = true
        focusManager.clearFocus(true)
        onCommit(parsed)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!suffix.isNullOrEmpty()) {
                Text(
                    text = suffix,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(metrics.fieldHeight)
                    .clip(Oxide.RadiusControl)
                    .background(Oxide.BgButton)
                    .border(
                        BorderStroke(1.dp, if (error != null) Oxide.FgMuted else Oxide.Line),
                        Oxide.RadiusControl,
                    )
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input
                            .filter { if (integerOnly) it.isDigit() else it.isDigit() || it == '.' }
                            .take(8)
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = Oxide.Type.Body.copy(color = Oxide.Fg),
                    cursorBrush = SolidColor(Oxide.FgMuted),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state -> if (!state.isFocused) commit() }
                        .semantics { contentDescription = label },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty() && !hint.isNullOrEmpty()) {
                                Text(
                                    text = hint,
                                    color = Oxide.FgFaint,
                                    fontSize = Oxide.Type.MicroLabel.fontSize,
                                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            EditorMiniButton(
                text = "✓",
                description = confirmText,
                enabled = error == null && enabled,
                onClick = { commit() },
            )
            Spacer(Modifier.width(4.dp))
            EditorMiniButton(
                text = "✕",
                description = closeText,
                enabled = enabled,
                onClick = {
                    done = true
                    focusManager.clearFocus(true)
                    onDismiss()
                },
            )
        }
        if (error != null) {
            Text(
                text = when (error) {
                    EditorNumberError.NotANumber -> notANumber
                    EditorNumberError.TooSmall -> tooSmall
                    EditorNumberError.TooLarge -> tooLarge
                },
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 7.dp, top = 3.dp),
            )
        }
    }
}

/** 输入行尾部的小按钮：画的是字形而不是图标，所以必须显式给它朗读文本 */
@Composable
internal fun EditorMiniButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    Box(
        modifier = modifier
            .size(metrics.miniButtonSize)
            .clip(Oxide.RadiusBadge)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBadge)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
    }
}

/**
 * 一个方形按钮，画的是字形
 *
 * 触屏上比 28x28 更小的目标很难命中，因此这一类按钮的边长直接取
 * [ControlEditorMetrics.miniButtonSize]，最少也有 20dp，加上四周的内边距之后
 * 实际热区比看上去更大。
 */
@Composable
internal fun EditorGlyphButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = Oxide.RadiusBadge,
) {
    val metrics = editorMetrics()
    Box(
        modifier = modifier
            .size(metrics.miniButtonSize)
            .clip(shape)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 触摸
// ---------------------------------------------------------------------------

/**
 * 把落到这块面板上的触摸全部吃掉
 *
 * 停靠面板是盖在**控制布局画布**上的：面板根部的空白处如果没有消费事件，那一下
 * 点击会同时被画布与面板看见——画布会以为玩家在游戏里按了一下控件。这里只消费，
 * 不加任何语义，也不会吞掉子行自己收到的事件（子节点先于父节点拿到事件）。
 */
internal fun Modifier.editorConsumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (!change.isConsumed) change.consume()
            }
        }
    }
}

/**
 * 面板根部的遮罩：既吃掉触摸，也负责点空白关闭
 *
 * 与 [editorConsumeTouches] 分开是因为遮罩还要接点击；这一层放在面板**之下**，
 * 因此点面板本身不会落到它身上。
 */
@Composable
internal fun EditorScrim(onClick: () -> Unit) {
    val description = stringResource(R.string.oxide_ce_close_dock)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .editorConsumeTouches()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/** 停靠面板共用的填充色：一层低透明度的面 + 面板渐变 */
internal fun Modifier.editorPanelBackground(): Modifier = this
    .background(Oxide.SurfaceBase)
    .background(Oxide.SurfaceBrush)

/** 面板里带内边距的一列。给面板外那些仍然是旧结构的对话框用 */
@Composable
internal fun EditorPanelColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = editorMetrics()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(metrics.dockPadding),
        content = content,
    )
}