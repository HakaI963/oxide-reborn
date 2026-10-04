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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * 图形部分是一个八边形环：钢色的左上/右下两段，accent 色的右上/左下两段；
 * 字标是 "OX" 重、"IDE" 轻。两者之间固定 10dp，这是参考稿里 logo 的全部几何，
 * 任何缩放都按比例保持这个关系。
 *
 * 轮廓与 [dev.oxide.launcher.R.drawable.ic_launcher] 用的那张美术稿一致：正八边形，
 * 上/下/左/右四条直边，四角 45° 切角，中间是同形的洞。
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

/** 正八边形的外接圆半径相对方框边长的比例：顶点正好落在方框四边的中点上 */
internal const val OxideMarkOuterRadiusRatio = 0.5f

/** 洞口半径占外接圆半径的比例。取自美术稿：洞比外轮廓略小于一半 */
internal const val OxideMarkInnerRadiusRatio = 0.45f

/**
 * 正八边形的八个顶点
 *
 * 起点取 22.5°，于是四条直边分别朝向上/下/左/右，四角落在对角线上——
 * 和美术稿里那条平的上边一致。纯函数，形状本身可以脱离 Compose 单测。
 */
internal fun octagonVertices(
    centerX: Float,
    centerY: Float,
    radius: Float,
): List<Offset> = List(8) { i ->
    // 屏幕坐标 y 向下，正好对应绕顺时针排列
    val a = Math.toRadians((22.5 + i * 45.0).toDouble())
    Offset(
        centerX + (radius * kotlin.math.cos(a)).toFloat(),
        centerY + (radius * kotlin.math.sin(a)).toFloat(),
    )
}

/**
 * Oxide 标志
 *
 * 整个环画成一个 even-odd 路径（外八边形 + 内八边形反向），再按四个象限裁剪上色：
 * 右上与左下用 accent 色，左上与右下用钢色。这正是美术稿的配色分布，
 * 用 accent 而不是写死一个橙色，是为了跟着用户选的颜色主题走。
 *
 * 几何只用外接圆半径和边长，开场动画与侧栏静止态共用同一套比例，
 * 因此换形状不影响 [dev.oxide.launcher.ui.components.OxideIntro] 的动画算法。
 */
@Composable
fun OxideMark(
    modifier: Modifier = Modifier,
    size: Dp = Oxide.MarkSize,
) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val c = side / 2f
        val outer = side * OxideMarkOuterRadiusRatio
        val inner = outer * OxideMarkInnerRadiusRatio

        val ring = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            octagonVertices(c, c, outer).forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
            octagonVertices(c, c, inner).forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        // 两组对角象限上 accent 色，另外两组上钢色
        clipRect(left = c, top = 0f, right = side, bottom = c) {
            drawPath(ring, Oxide.Accent)          // 右上
        }
        clipRect(left = 0f, top = c, right = c, bottom = side) {
            drawPath(ring, Oxide.Accent)          // 左下
        }
        clipRect(left = 0f, top = 0f, right = c, bottom = c) {
            drawPath(ring, Oxide.MarkBorder)      // 左上
        }
        clipRect(left = c, top = c, right = side, bottom = side) {
            drawPath(ring, Oxide.MarkBorder)      // 右下
        }
    }
}