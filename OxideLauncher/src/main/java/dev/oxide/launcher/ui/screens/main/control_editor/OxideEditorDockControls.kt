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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.oxide.Oxide
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadgeTone
import kotlin.math.roundToInt

/**
 * 停靠面板里的四块"选择器"
 *
 * 定位板、九宫格、步进、以及层与控件两行条目。四块都用同一套画法：底板与指示
 * 尽量一遍画完，手势自己实现，状态另外交给 `selectable` / `toggleable` 的
 * selected 与一行朗读说明。因此选中态除了颜色，还有形状、位置与朗读三层线索——
 * 高对比度模式、色觉差异与灰度打印下都读得出来。
 */

// ---------------------------------------------------------------------------
// 定位板
// ---------------------------------------------------------------------------

/**
 * 拖动定位板
 *
 * 整块屏幕被缩成这一个矩形，旋钮的位置就是控件中心在屏幕上的比例。
 * 这不是新功能——画布上本来就能拖控件（见 `ControlEditorLayer` 的手柄），
 * 但在画布上精确拖到某个位置几乎做不到，因此这里给的是另一条路：
 * 大致拖到位，再靠步进与直接输入收尾。
 *
 * [onMove] 在拖动过程中被连续调用（只改内存状态），[onMoveFinished] 在抬手时调用一次，
 * 落盘与刷新都放在那里。
 */
@Composable
internal fun EditorPositionPad(
    label: String,
    position: EditorStoredPosition,
    onMove: (EditorStoredPosition) -> Unit,
    onMoveFinished: (EditorStoredPosition) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val metrics = editorMetrics()
    val knobRadius = metrics.trackKnobRadius * 1.6f
    val fractionX = remember(position.x) { editorFractionFromStored(position.x) }
    val fractionY = remember(position.y) { editorFractionFromStored(position.y) }

    // 手势协程的 key 里没有回调，因此它不会随选中项重启，
    // 回调必须跟着组合走，否则拖到的还是上一个控件
    val currentMove by rememberUpdatedState(onMove)
    val currentFinish by rememberUpdatedState(onMoveFinished)

    val readOut = remember(position) {
        "${(fractionX * 100f).roundToInt()}% , ${(fractionY * 100f).roundToInt()}%"
    }
    val padDescription = remember(label, readOut) { "$label: $readOut" }

    val shape = Oxide.RadiusControl

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.padHeight)
            .clip(shape)
            .background(Oxide.SurfaceBase)
            .border(BorderStroke(1.dp, if (enabled) Oxide.Line else Oxide.LineFaint), shape)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        var current = Offset.Zero
                        var moved = false
                        fun apply(x: Float, y: Float) {
                            current = editorPositionFromPadPoint(x, y, size.width.toFloat(), size.height.toFloat())
                            currentMove(current)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            moved = false
                            val pointerId = down.id
                            apply(down.position.x, down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                if (change.position != change.previousPosition) {
                                    moved = true
                                    apply(change.position.x, change.position.y)
                                }
                                change.consume()
                            }
                            if (moved) currentFinish(current)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .semantics {
                role = Role.Button
                contentDescription = padDescription
                stateDescription = readOut
            }
    ) {
        val knob = knobRadius.toPx()

        // 三分线与中心十字：给"大概在哪"一个可读的参照
        val guide = if (enabled) Oxide.Line else Oxide.LineFaint
        for (fraction in listOf(1f / 3f, 2f / 3f)) {
            drawLine(
                color = guide,
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = 1f,
            )
            drawLine(
                color = guide,
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = 1f,
            )
        }

        val point = editorPadKnob(
            fractionX = fractionX,
            fractionY = fractionY,
            width = size.width,
            height = size.height,
            knobRadius = knob,
        )
        // 旋钮与准星：外圈是准星（更细），内芯是旋钮，因此不靠颜色也能看出落在哪
        drawCircle(
            color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            radius = knob,
            center = Offset(point.x, point.y),
            style = Stroke(width = 1f),
        )
        drawCircle(
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            radius = knob * 0.45f,
            center = Offset(point.x, point.y),
        )
    }
}

/**
 * 定位板的抬头：左边一行小字说明它在编辑什么，右边一个读数
 *
 * 读数放在抬头而不是塞进板里，是因为板内已经被九宫格参考线与旋钮占满了；
 * 而读数必须在拖动时一直看得见，因此它跟板平级、在板上方。
 */
@Composable
internal fun EditorPadHeader(
    label: String,
    readOut: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = readOut,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 九宫格对齐
// ---------------------------------------------------------------------------

/**
 * 九宫格对齐按钮
 *
 * 三行三列，顺序就是 [EditorAnchor] 的声明顺序（逐行），因此界面上不必再排一次。
 * 选中态由实心方块与 `Role.RadioButton` 承担。
 */
@Composable
internal fun EditorAnchorGrid(
    current: EditorStoredPosition,
    onPick: (EditorAnchor) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val metrics = editorMetrics()
    val cell = metrics.miniButtonSize
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EditorAnchor.entries.chunked(EditorAnchor.ColumnCount).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { anchor ->
                    val target = editorAnchorPosition(anchor)
                    val isCurrent = target.x == current.x && target.y == current.y
                    EditorAnchorCell(
                        glyph = anchorGlyph(anchor),
                        description = anchorDescription(anchor),
                        selected = isCurrent,
                        enabled = enabled,
                        size = cell,
                        onClick = { onPick(anchor) },
                    )
                }
            }
        }
    }
}

/** 九宫格里那个点的字形：位置本身就是它的形状，不靠颜色区分 */
private fun anchorGlyph(anchor: EditorAnchor): String = when (anchor) {
    EditorAnchor.TopStart -> "↖"
    EditorAnchor.TopCenter -> "↑"
    EditorAnchor.TopEnd -> "↗"
    EditorAnchor.CenterStart -> "←"
    EditorAnchor.Center -> "⊙"
    EditorAnchor.CenterEnd -> "→"
    EditorAnchor.BottomStart -> "↙"
    EditorAnchor.BottomCenter -> "↓"
    EditorAnchor.BottomEnd -> "↘"
}

@Composable
private fun anchorDescription(anchor: EditorAnchor): String = when (anchor) {
    EditorAnchor.TopStart -> stringResource(R.string.oxide_ce_anchor_top_start)
    EditorAnchor.TopCenter -> stringResource(R.string.oxide_ce_anchor_top_center)
    EditorAnchor.TopEnd -> stringResource(R.string.oxide_ce_anchor_top_end)
    EditorAnchor.CenterStart -> stringResource(R.string.oxide_ce_anchor_center_start)
    EditorAnchor.Center -> stringResource(R.string.oxide_ce_anchor_center)
    EditorAnchor.CenterEnd -> stringResource(R.string.oxide_ce_anchor_center_end)
    EditorAnchor.BottomStart -> stringResource(R.string.oxide_ce_anchor_bottom_start)
    EditorAnchor.BottomCenter -> stringResource(R.string.oxide_ce_anchor_bottom_center)
    EditorAnchor.BottomEnd -> stringResource(R.string.oxide_ce_anchor_bottom_end)
}

@Composable
private fun EditorAnchorCell(
    glyph: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(Oxide.RadiusBadge)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusBadge,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = when {
                !enabled -> Oxide.Line
                selected -> Oxide.Fg
                else -> Oxide.FgGhost
            },
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 步进
// ---------------------------------------------------------------------------

/**
 * 步进按钮对：一行两个，分别减与加
 *
 * 触屏上拖动与直接输入之间的那档精度就靠它补上，因此步长是显式的，
 * 而不是把一次点击的像素位移换算成比例（那样会随按压位置漂移）。
 */
@Composable
internal fun EditorNudgeRow(
    label: String,
    onNudge: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val decrease = stringResource(R.string.oxide_ce_decrease)
    val increase = stringResource(R.string.oxide_ce_increase)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) Oxide.FgFaint else Oxide.Line,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        EditorGlyphButton(
            glyph = "−",
            description = decrease,
            enabled = enabled,
            onClick = { onNudge(-1) },
        )
        Spacer(Modifier.width(4.dp))
        EditorGlyphButton(
            glyph = "+",
            description = increase,
            enabled = enabled,
            onClick = { onNudge(1) },
        )
    }
}

// ---------------------------------------------------------------------------
// 控件层
// ---------------------------------------------------------------------------

/**
 * 停靠面板里的控件层条目
 *
 * 一行里三块热区：眼睛（切隐藏）、名字（选中，再点一次取消）、属性按钮。
 * 选中态除了强调色还有左侧指示条与实心方块；隐藏态另有一行"已隐藏"的字——
 * 只在眼睛上换图标是不够的，两枚图形的差别太小，色觉差异下更难分辨。
 */
@Composable
internal fun EditorLayerRow(
    name: String,
    selected: Boolean,
    hidden: Boolean,
    attributesText: String,
    visibilityText: String,
    visibilityOnText: String,
    onSelect: () -> Unit,
    onAttributes: () -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val metrics = editorMetrics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorSelectedMark(selected = selected)
        // 可见时是一个实心点、隐藏时是一个空心圈，因此两态的**形状**就不同，
        // 不必只靠描边与底色的差别
        EditorGlyphButton(
            glyph = if (hidden) "○" else "●",
            description = visibilityText,
            enabled = enabled,
            selected = !hidden,
            onClick = onToggleVisibility,
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hidden) {
                Text(
                    text = visibilityOnText,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        EditorGlyphButton(
            glyph = "⋯",
            description = attributesText,
            enabled = enabled,
            onClick = onAttributes,
        )
    }
}

// ---------------------------------------------------------------------------
// 控件
// ---------------------------------------------------------------------------

/**
 * 网格里的一个控件
 *
 * 两行：名字与位置摘要。选中态除了强调色还有左侧指示条与实心方块，
 * 并交给 `selectable(selected = …)`，因此朗读时也知道选的是哪一个。
 * [onOpen] 是"再点一次"的动作——画布上也是这个手势（选一次、再点一次开编辑）。
 */
@Composable
internal fun EditorControlCell(
    name: String,
    summary: String,
    kindGlyph: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val metrics = editorMetrics()
    Column(
        modifier = modifier
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = {
                    if (selected) onOpen() else onSelect()
                },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditorSelectedMark(selected = selected)
            Text(
                text = kindGlyph,
                color = Oxide.FgGhost,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                color = if (selected) Oxide.Fg else Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = summary,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 徽章
// ---------------------------------------------------------------------------

/** 停靠面板里的小徽章，复用启动器那一份的形状与字号 */
@Composable
internal fun EditorBadge(text: String, tone: OxideBadgeTone = OxideBadgeTone.Neutral) {
    Text(
        text = text,
        color = when (tone) {
            OxideBadgeTone.Neutral -> Oxide.FgDim
            OxideBadgeTone.Active -> Oxide.Fg
            OxideBadgeTone.Warn -> Oxide.FgMuted
        },
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 1,
        modifier = Modifier
            .clip(Oxide.RadiusBadge)
            .background(when (tone) {
                OxideBadgeTone.Neutral -> Oxide.BgChip
                OxideBadgeTone.Active -> Oxide.BgTabActive
                OxideBadgeTone.Warn -> Oxide.BgChip
            })
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** 一个可以立刻点开的空状态：没有控件时给一句说明，而不是一片空白 */
@Composable
internal fun EditorEmptyRow(text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!actionText.isNullOrEmpty() && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionText,
                color = Oxide.Fg,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                modifier = Modifier
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.BgButton)
                    .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBadge)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
    }
}

/** 停靠面板底部那排按钮的容器：一行等宽，标签各自截断 */
@Composable
internal fun EditorFooterRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val metrics = editorMetrics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.footerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** 底部那排按钮里的一个：等宽、按压有反馈、禁用时整块变灰 */
@Composable
internal fun EditorFooterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Oxide.RadiusButton
    Box(
        modifier = modifier
            .weight(1f)
            .height(editorMetrics().footerHeight - 6.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> Color.Transparent
                    pressed && primary -> Oxide.Line
                    pressed -> Oxide.BgButtonHover
                    primary -> Oxide.BgToggleOn
                    else -> Oxide.BgButton
                }
            )
            .border(
                BorderStroke(1.dp, if (primary && enabled) Color.Transparent else Oxide.Line),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Oxide.FgFaint
                primary -> Color(0xFF0B0B0B)
                else -> Oxide.Fg
            },
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一条水平分隔线：1px 发丝，跟启动器其余界面一致 */
@Composable
internal fun EditorHairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Oxide.Line)
    )
}

/** 面板顶部与底部各留一条固定高度的地方，中间的列表在它们之间滚动 */
@Composable
internal fun EditorDockFrame(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val metrics = editorMetrics()
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(Oxide.RadiusDrawer)
            .editorPanelBackground()
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusDrawer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.headerHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            header()
        }
        EditorHairLine()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            body()
        }
        EditorHairLine()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.footerHeight),
            contentAlignment = Alignment.Center,
        ) {
            footer()
        }
    }
}