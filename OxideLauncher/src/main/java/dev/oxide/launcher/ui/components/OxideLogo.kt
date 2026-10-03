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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * logo 的基准几何，取自参考稿：字标 60px，图形与字标之间固定 10px。
 *
 * 这两个值和图形边长（[Oxide.MarkSize]）一起定义了 logo 的全部比例。
 * 任何缩放都必须按同一比例乘这三个数——开场动画的起点、飞行中的缩放、
 * 以及最后落在侧栏里的静止尺寸，只有这样才是同一个 logo。
 */
val OxideLogoGap = 10.dp

/** 字标字号，参考稿的 60px */
val OxideLogoWordmark = 60.sp

/**
 * Oxide 标志
 *
 * 图形部分是一个 45° 旋转的圆角方框，内嵌一个更小的圆角方框；字标是 "OX" 重、"IDE" 轻。
 * 两者之间固定 10dp，这是参考稿里 logo 的全部几何，任何缩放都按比例保持这个关系。
 */
@Composable
fun OxideLogo(
    modifier: Modifier = Modifier,
    gap: Dp = OxideLogoGap,
    showWordmark: Boolean = true,
    markSize: Dp = Oxide.MarkSize,
    wordmarkSize: TextUnit = OxideLogoWordmark,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        OxideMark(size = markSize)
        if (showWordmark) {
            Row {
                Text(
                    text = "OX",
                    color = Oxide.Fg,
                    fontSize = wordmarkSize,
                    lineHeight = wordmarkSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = wordmarkSize * -0.11f,
                    maxLines = 1,
                )
                Text(
                    text = "IDE",
                    color = Oxide.WordmarkTail,
                    fontSize = wordmarkSize,
                    lineHeight = wordmarkSize,
                    fontWeight = FontWeight.Light,
                    letterSpacing = wordmarkSize * -0.11f,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun OxideMark(
    modifier: Modifier = Modifier,
    size: Dp = Oxide.MarkSize,
) {
    // 参考稿里描边固定为 1px，这里的描边按尺寸等比缩放，落地时才是 1dp
    val ratio = size.value / 37f
    Canvas(modifier = modifier.size(size).rotate(45f)) {
        val stroke = (1f * ratio).coerceAtLeast(0.35f).dp.toPx()
        val side = this.size.minDimension
        val radius = CornerRadius(side * (10f / 37f))

        drawRoundRect(
            color = Oxide.MarkBorder,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(side - stroke, side - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke)
        )

        // 内框 inset:7px、圆角 4px
        val inset = side * (7f / 37f)
        drawRoundRect(
            color = Oxide.MarkInner,
            topLeft = Offset(inset + stroke / 2f, inset + stroke / 2f),
            size = Size(side - inset * 2f - stroke, side - inset * 2f - stroke),
            cornerRadius = CornerRadius(side * (4f / 37f)),
            style = Stroke(width = stroke)
        )
    }
}