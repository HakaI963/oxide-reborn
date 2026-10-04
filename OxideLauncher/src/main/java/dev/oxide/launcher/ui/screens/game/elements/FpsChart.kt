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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 游戏内帧率历史图表
 *
 * 纵轴为帧率（范围向外取整为"好数"后分 [GameFpsAxisSegments] 段标注刻度），
 * 横轴为时间（最多记录 15 个时间节点，不显示具体时间）；
 * 最后一个点旁标注当前帧率。
 *
 * 换掉的是画法，不是数据：
 *
 * - 颜色全部来自 [Oxide]。原来的 `MaterialTheme.colorScheme.primary` 跟着系统
 *   动态取色走，在一块正在跑的游戏画面上会飘成任意一种颜色；曲线因此改成中性
 *   前景色加一层发丝线网格，**没有任何一处靠颜色区分含义**。
 * - 本体不再自己画底。它的外层就是悬浮球那块面板，再压一层 60% 的
 *   `secondaryContainer` 只会在近黑语言里糊出一块灰。
 * - 刻度数字与分割线终于对得上了。原来 6 个 12dp 的标注框在 120dp 高的图里是
 *   `SpaceBetween` 铺开的，中心落在 6 / 27.6 / 49.2 …，而分割线落在
 *   6 / 28.8 / 51.6 …，误差一路累积到 1.2dp。现在标注框与网格共用同一个"格子"
 *   （见 [fpsLabelCellHeight]），两者精确重合。
 * - 尺寸不再写死 180x120，而是跟着游戏窗口收（见 [gameFpsChartSize]）。
 * - 曲线旁的当前帧率以前只有 `drawText` 画出来，读屏软件念不到；
 *   现在画在无障碍树上的描述里，那块 `Canvas` 自己报当前帧率。
 *
 * 这一块每秒至少重画一次（帧率采样每秒一次），展开/收起动画期间是每帧，
 * 因此它还必须满足：点坐标走一块复用的 [FloatArray]、曲线走一块复用的
 * [Path]、当前帧率的文字只在数值真的变了才重新测量。
 */
@Composable
fun FpsChart(
    history: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    chartSize: GameFpsChartSize,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    // 纵轴刻度数字：范围向外取整为"好数"后均分若干段（含两端）
    val axis = remember(fpsMax, fpsMin) { gameFpsAxis(fpsMin, fpsMax) }
    val ticks = remember(axis) { gameFpsAxisTicks(axis) }
    val current = history.lastOrNull()
    // 文字只在数值真的变了才重新测量：这一帧里动画每画一帧都会走到这里
    val currentLabel = remember(current, FpsLabelStyle) {
        if (current != null) {
            textMeasurer.measure(text = current.toString(), style = FpsLabelStyle)
        } else {
            null
        }
    }

    val labelCell = fpsLabelCellHeight(chartSize.height)

    Row(
        modifier = modifier
            .width(chartSize.width)
            .height(chartSize.height)
            .clip(Oxide.RadiusSmall)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusSmall)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // 纵轴帧率标注：最高帧在上，最低帧在下。
        // 不用 SpaceBetween——格子高度就是 高/(段数+1)，相乘恰好铺满，
        // 与网格线共用同一个公式
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.End
        ) {
            ticks.reversed().forEach { tick ->
                Box(
                    modifier = Modifier.height(labelCell),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = tick.toString(),
                        style = FpsLabelStyle,
                        color = Oxide.FgFaint,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // 帧率曲线与网格
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    // 曲线旁的当前帧率是画出来的，读屏念不到；这里自己报一次
                    if (current != null) {
                        Modifier.semantics { contentDescription = current.toString() }
                    } else {
                        Modifier
                    }
                )
        ) {
            drawFpsChart(
                history = history,
                axis = axis,
                cellHeight = labelCell,
                measured = currentLabel,
            )
        }
    }
}

/** 纵轴标注的样式；等宽，整列才不会左右跳。写成 getter 是为了界面缩放一改就跟着变 */
private val FpsLabelStyle: TextStyle get() = Oxide.Type.Mono

/**
 * 一格标注框的高度
 *
 * [GameFpsAxisSegments] 条分割线因此把高度分成 6 格，标注框也正好 6 个；
 * 每一格的中心 `(k + 0.5) * 高 / 6` 同时是第 k 条分割线的位置与第 k 个标注的
 * 中心，两者精确重合。
 */
internal fun fpsLabelCellHeight(chartHeight: Dp): Dp =
    chartHeight / (GameFpsAxisSegments + 1)

/**
 * 绘制帧率图表
 *
 * 每个刻度一条水平分割线，坐标区左缘以纵轴分割线与数字分隔，
 * 帧率数值对应的点按纵轴范围（好数边界）映射，相邻点之间用带轻微圆滑度的
 * 折线相连，最后一个点旁标注它所代表的帧率。
 *
 * [cellHeight] 传的是一格标注框的高度而不是图的总高：纵向的半格内缩正是它
 * 的一半，因此曲线两端正好落在最下面那两条网格线上。
 */
private fun DrawScope.drawFpsChart(
    history: List<Int>,
    axis: GameFpsAxis,
    cellHeight: Dp,
    measured: TextLayoutResult?,
) {
    val pointRadius = 1.25.dp.toPx()
    val gridStroke = 1.dp.toPx()
    // 横向内缩半个点加半条分割线，避免曲线与点被边缘裁切
    val insetX = pointRadius + gridStroke / 2f
    // 纵向内缩半格：第 k 条分割线落在 (k + 0.5) / 6 的高度上
    val insetY = cellHeight.toPx() / 2f

    // 水平分割线：每个刻度一条，从纵轴延伸到右缘
    for (k in 0..GameFpsAxisSegments) {
        val y = insetY + k * (size.height - insetY * 2) / GameFpsAxisSegments
        drawLine(
            color = Oxide.Line,
            start = Offset(insetX, y),
            end = Offset(size.width, y),
            strokeWidth = gridStroke
        )
    }
    // 纵轴分割线：坐标区与数字的交界
    drawLine(
        color = Oxide.Line,
        start = Offset(insetX, insetY),
        end = Offset(insetX, size.height - insetY),
        strokeWidth = gridStroke
    )

    if (!fpsChartHasSeries(history)) return

    // 复用的两块缓冲：这张图挂在运行中的游戏上，动画期间是每帧重画。
    // 这里不新建列表也不新建 Path
    val coordinates = fpsChartBuffer
    val path = fpsChartPath
    val count = history.size.coerceAtMost(coordinates.size / 2)
    for (index in 0 until count) {
        val x = if (history.size == 1) {
            (insetX + size.width) / 2f
        } else {
            insetX + index * (size.width - insetX * 2) / (history.size - 1)
        }
        val y = insetY + (1f - axis.fraction(history[index])) * (size.height - insetY * 2)
        val slot = index * 2
        coordinates[slot] = x
        coordinates[slot + 1] = y
    }

    path.reset()
    path.moveTo(coordinates[0], coordinates[1])
    // 控制点沿水平方向偏移一小段距离，形成轻微圆滑的过渡
    for (i in 1 until count) {
        val startX = coordinates[(i - 1) * 2]
        val startY = coordinates[(i - 1) * 2 + 1]
        val endX = coordinates[i * 2]
        val endY = coordinates[i * 2 + 1]
        val offset = (endX - startX) * 0.25f
        path.cubicTo(
            x1 = startX + offset,
            y1 = startY,
            x2 = endX - offset,
            y2 = endY,
            x3 = endX,
            y3 = endY
        )
    }
    drawPath(
        path = path,
        color = Oxide.Fg,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    for (index in 0 until count) {
        drawCircle(
            color = Oxide.Fg,
            radius = pointRadius,
            center = Offset(coordinates[index * 2], coordinates[index * 2 + 1]),
        )
    }

    // 当前帧率标注：放在最后一个点的上/下方，避开来向线条，且不越过上下刻度线
    if (measured == null) return
    val lastX = coordinates[(count - 1) * 2]
    val lastY = coordinates[(count - 1) * 2 + 1]
    val gap = 2.dp.toPx()
    val canBelow = lastY + gap + measured.size.height <= size.height - insetY
    val canAbove = lastY - gap - measured.size.height >= insetY
    val prevY = if (count >= 2) coordinates[(count - 2) * 2 + 1] else null
    val placeBelow = when {
        canBelow && !canAbove -> true
        !canBelow && canAbove -> false
        prevY != null && prevY < lastY -> true
        prevY != null && prevY > lastY -> false
        else -> lastY <= size.height / 2f
    }
    val topLeft = if (placeBelow) {
        Offset(size.width - measured.size.width, lastY + gap)
    } else {
        Offset(size.width - measured.size.width, lastY - gap - measured.size.height)
    }
    drawText(textLayoutResult = measured, color = Oxide.FgStrong, topLeft = topLeft)
}

/**
 * 点坐标的复用缓冲
 *
 * 帧率历史最多 15 个时间节点（见 `GameScreen` 里的 `FPS_HISTORY_SIZE`），
 * 32 个 float 足够放 16 个点；超出部分由 `count` 掐掉，
 * 因此再长的历史也不会写越界。
 */
private val fpsChartBuffer = FloatArray(32)

/** 复用的曲线路径；`reset()` 之后可以立刻重新 `moveTo` */
private val fpsChartPath = Path()