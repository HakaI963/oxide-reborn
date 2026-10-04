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

package dev.oxide.launcher.ui.screens.game.elements

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.theme.Oxide
import kotlin.math.roundToInt

/**
 * 游戏内菜单的控件
 *
 * 这些控件全部由 [Oxide] 的记号（颜色、圆角、字号、动效）直接拼出来，
 * 没有一处 `MaterialTheme`：菜单画在运行中的游戏上，Material 的默认配色
 * 与圆角在这套近黑语言里会明显跳出来，而它的控件高度也比这里大一倍。
 *
 * 三条在这个场景下才算数的规则：
 *
 * 1. **不做每帧的工作**。格式化在 `remember` 里按输入缓存，数字解析只发生在
 *    一次提交之前，轨道用 `Canvas` 一遍画完而不是拼一串 `Box`。
 * 2. **不抢游戏的触摸**。每一行都消费自己收到的事件，面板根部另有一层
 *    [consumeTouches]，因此落在菜单上的手势不会漏给游戏。
 * 3. **状态不靠颜色说话**。开关用 `Role.Switch` 与 `toggleableState`，单选用
 *    `Role.RadioButton` 与 `selectable`，滑杆给出 `ProgressBarRangeInfo` 与
 *    `setProgress`；行内输入的错误除了描边还另有一行文字。
 */

// ---------------------------------------------------------------------------
// 文本
// ---------------------------------------------------------------------------

/** 小节标题：6sp 大写宽字距 */
@Composable
internal fun GameMenuGroupLabel(text: String, modifier: Modifier = Modifier) {
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
            .padding(top = 4.dp, bottom = 2.dp),
    )
}

/** 说明行（例如"这台设备没有陀螺仪"） */
@Composable
internal fun GameMenuNoteRow(text: String, modifier: Modifier = Modifier) {
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
// 开关
// ---------------------------------------------------------------------------

/**
 * 开关行
 *
 * 整行都是热区（[toggleable]），因此在游戏里不必瞄准一个 28x15 的小块；
 * 选中态由 [toggleable] 以 `Role.Switch` 与 `ToggleableState` 交给无障碍服务，
 * 滑块的位置只是第二重线索。
 */
@Composable
internal fun GameMenuSwitchRow(
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
        GameMenuSwitchMark(checked = checked)
    }
}

/**
 * 开关的样子
 *
 * 与 `OxideToggle` 同一套颜色与比例，但**不带点击**：点击由整行的 [toggleable] 负责，
 * 所以这里只是一段纯绘制，朗读时也不会多出一个没有名字的小热区。
 */
@Composable
private fun GameMenuSwitchMark(checked: Boolean) {
    val track by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(140),
        label = "gameMenuSwitch",
    )
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(50))
            .background(lerpGameMenuColor(Oxide.BgToggleOff, Oxide.BgToggleOn, track))
            .border(BorderStroke(1.dp, Oxide.Line), RoundedCornerShape(50)),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) Color(0xFF1A1A1A) else Oxide.FgFaint)
        )
    }
}

// ---------------------------------------------------------------------------
// 动作
// ---------------------------------------------------------------------------

/**
 * 动作行（切换输入法、发送键值、强制关闭……）
 *
 * 用 [clickable] 并带 `Role.Button`，朗读时是一个按钮而不是一段文字；
 * 末尾那块小方块只是"可点"的视觉提示，不是状态，因此不参与朗读。
 */
@Composable
internal fun GameMenuActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Oxide.FgFaint
                emphasis -> Oxide.FgStrong
                else -> Oxide.Fg
            },
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(Oxide.RadiusBadge)
                .background(if (enabled) Oxide.FgGhost else Oxide.Line)
        )
    }
}

// ---------------------------------------------------------------------------
// 单选
// ---------------------------------------------------------------------------

/**
 * 单选行：点开就在行下方就地列出候选项
 *
 * 不用下拉也不用弹窗——菜单叠在游戏上，一个 Material 弹窗会盖掉整块画面，
 * 就地展开视线也不用离开正在做的事。候选项多到铺不下时才给这一块自己的滚动，
 * 并且高度被 [maxListHeight] 夹住，因此不存在无界的嵌套滚动。
 */
@Composable
internal fun <T> GameMenuChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxListHeight: Dp = 160.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = optionText(selected)
    val expandText = stringResource(R.string.generic_expand)
    val collapseText = stringResource(R.string.generic_collapse)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
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
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(6.dp))
            GameMenuChevron(expanded = expanded, enabled = enabled)
        }

        AnimatedVisibility(
            visible = expanded && enabled && options.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeIn(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
            exit = shrinkVertically(animationSpec = tween(Oxide.Motion.PopoverMs)) +
                fadeOut(animationSpec = tween(Oxide.Motion.PopoverFadeMs)),
        ) {
            val scroll = gameMenuChoiceUsesOwnScroll(options.size)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp)
                    .then(if (scroll) Modifier.heightIn(max = maxListHeight) else Modifier)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                options.forEach { option ->
                    GameMenuOptionRow(
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

/**
 * 一个候选项
 *
 * 选中态由左侧那块方块承担，同时整行带 `Role.RadioButton` 与 selected，
 * 因此朗读时也知道"现在选的是哪一个"，不靠颜色说话。
 */
@Composable
private fun GameMenuOptionRow(
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

/** 展开/收起的箭头，两条斜线画出来，因此正反两个方向都读得出来 */
@Composable
private fun GameMenuChevron(expanded: Boolean, enabled: Boolean) {
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
// 滑杆
// ---------------------------------------------------------------------------

/**
 * 滑杆行
 *
 * 拖轨道改值；点右边的数值就展开一行行内输入，可以直接敲精确的数字——
 * 这正是旧的 `SliderValueEditDialog` 承担的事，现在换成与启动器同一语言的输入行，
 * 并且不必在运行中的游戏上盖起一个 Material 弹窗。
 *
 * [onValueChange] 在拖动过程中会被连续调用（只改内存状态，不落盘），
 * [onValueChangeFinished] 在抬手时调用一次，落盘与刷新都放在那里。
 */
@Composable
internal fun GameMenuSliderRow(
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
    val text = remember(value, suffix, decimals) { formatGameMenuValue(value, suffix, decimals) }
    val fraction = remember(value, valueRange) { gameMenuSliderFraction(value, valueRange) }
    val plainText = remember(value, decimals) { formatGameMenuValue(value, null, decimals) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .padding(horizontal = 6.dp, vertical = 4.dp)
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

        GameMenuTrack(
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
            GameMenuNumberField(
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
private fun GameMenuTrack(
    fraction: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    label: String,
    stateText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 手势协程只随 valueRange 重启，因此回调要跟着组合走，不能抓着创建时的那一份
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinish by rememberUpdatedState(onValueChangeFinished)
    val currentFraction by rememberUpdatedState(fraction)

    val knobRadius = 5.dp
    val trackHeight = 3.dp

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
                            currentChange(gameMenuSliderValue(f, valueRange))
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
                            currentFinish(gameMenuSliderValue(current, valueRange))
                        }
                    }
                } else Modifier
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                contentDescription = "$label: $stateText"
                setProgress { requested ->
                    val target = requested.coerceIn(0f, 1f)
                    currentChange(gameMenuSliderValue(target, valueRange))
                    currentFinish(gameMenuSliderValue(target, valueRange))
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
 * 固定宽高的整数输入（自定义分辨率的宽高）
 *
 * 与旧的 `IntInputField` 一样只收数字、最多六位，并夹进 [permitted]；
 * 区别是**提交时机**：按下确认、键盘 Done 或离开焦点才落盘。
 * 每敲一个数字就写一次 MMKV 并让游戏窗口重建一次，在运行中的游戏里是不能要的。
 */
@Composable
internal fun GameMenuNumberRow(
    label: String,
    value: Int,
    permitted: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val initialText = remember(value) { value.takeIf { it > 0 }?.toString().orEmpty() }
    val range = remember(permitted) { permitted.first.toFloat()..permitted.last.toFloat() }
    GameMenuNumberField(
        label = label,
        initialText = initialText,
        range = range,
        integerOnly = true,
        suffix = null,
        onCommit = { onCommit(it.roundToInt()) },
        onDismiss = {},
        modifier = modifier,
        enabled = enabled,
        hint = "${permitted.first} – ${permitted.last}",
    )
}

/**
 * 行内数字输入行
 *
 * 就是启动器那份"紧凑输入框 + 就地确认"的形状：输入框、确认、取消，
 * 以及出错时另起一行的文字说明。错误除了把描边抬到正文色之外还有这句文字，
 * 因此不会只靠颜色表达。
 */
@Composable
private fun GameMenuNumberField(
    label: String,
    initialText: String,
    range: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
    onCommit: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String? = null,
    hint: String? = null,
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(initialText) }
    val error = remember(text, range, integerOnly) { gameMenuNumberError(text, range, integerOnly) }

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
        val parsed = gameMenuNumberIn(text, range, integerOnly) ?: return
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
                    .height(24.dp)
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
            GameMenuMiniButton(
                text = "✓",
                description = confirmText,
                enabled = error == null && enabled,
                onClick = { commit() },
            )
            Spacer(Modifier.width(4.dp))
            GameMenuMiniButton(
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
                    GameMenuNumberError.NotANumber -> notANumber
                    GameMenuNumberError.TooSmall -> tooSmall
                    GameMenuNumberError.TooLarge -> tooLarge
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
internal fun GameMenuMiniButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
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

// ---------------------------------------------------------------------------
// 分区栏与杂项
// ---------------------------------------------------------------------------

/** 分区栏里的一个标签 */
@Composable
internal fun GameMenuSectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Oxide.RadiusChip)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusChip,
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Oxide.Fg else Oxide.FgGhost,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

/**
 * 把落到这块面板上的触摸全部吃掉
 *
 * 菜单是盖在游戏上的：面板根部的空白处如果没有消费事件，那一下点击会同时被
 * 游戏与背后的遮罩看见——游戏会以为玩家点了屏幕。这里只消费，不加任何语义，
 * 也不会吞掉子行自己收到的事件（子节点先于父节点拿到事件）。
 */
internal fun Modifier.consumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (!change.isConsumed) change.consume()
            }
        }
    }
}

/** [lerpColor] 的菜单内副本：菜单不依赖另一个界面的内部工具 */
private fun lerpGameMenuColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}