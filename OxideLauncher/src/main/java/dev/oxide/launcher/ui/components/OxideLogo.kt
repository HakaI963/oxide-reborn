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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
/**
 * Oxide 标志
 *
 * 图形部分是一个圆角菱形环，左半钢色、右半 accent 色；字标是 "OX" 重、"IDE" 轻。
 * 两者之间固定 10dp，这是参考稿里 logo 的全部几何，任何缩放都按比例保持这个关系。
 *
 * 轮廓与 [dev.oxide.launcher.R.drawable.ic_launcher] 用的那张美术稿一致：正方形
 * 转 45° 的圆角菱形，描边，中间是更小的同形菱形。比例见 [oxideMarkGeometry]。
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

// ---------------------------------------------------------------------------
// OxideMark 的几何 —— 全部量自 1024px 美术稿（/emulated/icon.png）的像素，
// 不是量自任何一次渲染。
//
// 图形是**两个同心的圆角正方形**，各转 45°，都只描边不填充。对美术稿四条
// 0.5 覆盖率等值线做最小二乘拟合（7158 个边界点，rms 0.28px，max 0.90px）：
//
//   等值线                   半边长    描边    圆角半径（上/右/左/下）
//   外环 · 外缘              236.499   19.130   103.6 / 114.4 / 112.2 / 117.3
//   外环 · 内缘              217.369           90.6 /  94.4 /  94.9 /  96.1
//   内菱形 · 外缘            139.512   14.841    50.8 /  51.1 /  48.6 /  50.8
//   内菱形 · 内缘            124.672            36.7 /  36.1 /  34.2 /  35.8
//
// 拟合的旋转角是 44.927°，即 45°（偏差 0.07°，在 288px 半径上是 0.37px，肉眼不可见），
// 所以这里直接取 45°。
//
// 四个角的圆角半径并不相等（外缘 103.6..117.3，±6%），但这个差异远小于
// 一条描边的宽度，重画时取四角的均值；只有位图资源才逐角保留（见
// res/mipmap-*/ic_launcher_foreground.webp 与 res/drawable/ic_launcher_monochrome.xml）。
//
// 下面每个比例都除以 [OxideMarkHalfDiagonalRatio] 对应的半对角线，因此与尺寸无关；
// 描边宽度也从半对角线里扣掉，于是 [OxideMarkRing.outerHalfDiagonal] 恒等于它。
// ---------------------------------------------------------------------------

/** 图形外接菱形的半对角线占方框边长的比例：0.5，图形正好撑满方框 */
internal const val OxideMarkHalfDiagonalRatio = 0.5f

/** 图形相对方框的旋转角：正方形转 45° 成菱形 */
internal const val OxideMarkRotationDegrees = 45f

/** 外轮廓圆角半径 / 半对角线。实测 111.868 / 288.123 */
internal const val OxideMarkCornerRadiusRatio = 0.38826f

/** 外环描边宽度 / 半对角线。实测 (236.499 - 217.369) / 288.123 */
internal const val OxideMarkStrokeRatio = 0.06639f

/** 内菱形半对角线 / 外菱形半对角线。实测 176.456 / 288.123 */
internal const val OxideMarkInnerHalfDiagonalRatio = 0.61243f

/** 内轮廓圆角半径 / 内菱形半对角线。实测 50.323 / 176.456 */
internal const val OxideMarkInnerCornerRadiusRatio = 0.28519f

/** 内环描边宽度 / 内菱形半对角线。实测 (139.512 - 124.672) / 176.456 */
internal const val OxideMarkInnerStrokeRatio = 0.08411f

/**
 * 内菱形相对外菱形中心的偏移，占半对角线的比例。
 *
 * 实测：给内环单独拟合一次中心，得到 (511.908, 492.183)，外环中心是
 * (512.103, 492.004)，差 (-0.195, +0.179) px，即半对角线的 0.092%。
 * 落在测量噪声以内（外环四角的圆角半径本身就摆 ±6%，带来的中心不确定度
 * 约 2px），所以内菱形按同心画：偏移取 0，实测值只由测试守住。
 * 美术稿里并不存在"向左上偏"的设计意图。
 */
internal const val OxideMarkMeasuredInnerOffsetRatio = 0.00092f

/** 实测偏移的方向（未归一化），仅供测试断言符号与量级 */
internal val OxideMarkMeasuredInnerOffset = Offset(-0.195f, 0.179f)

/**
 * 美术稿里的横向渐变，取自描边内部不透明像素按 x 的中位数：
 * 左端 #C2C1C2，靠近中线处升到 #F0ECED，右端落到饱和橙 #FE6F01。
 * 渐变是纯左右向的（同一列上下取样颜色一致），不是对角渐变。
 */
private val OxideMarkGradientStops = arrayOf(
    0.00f to Color(0xFFC2C1C2),
    0.26f to Color(0xFFD9D7D8),
    0.49f to Color(0xFFF0ECED),
    0.80f to Color(0xFFFE6F01),
)

private const val OXIDE_MARK_SQRT_2 = 1.4142135f

/**
 * 一圈描边画出来的圆角正方形的参数（**未旋转**的坐标系，原点在中心）。
 *
 * [centreLineHalfSide] 与 [cornerRadius] 描述的是描边的**中心线**，所以描边
 * 宽度已经从中扣掉；[outerHalfDiagonal] 因此就是外缘到中心的距离。
 */
internal data class OxideMarkRing(
    val centreLineHalfSide: Float,
    val cornerRadius: Float,
    val strokeWidth: Float,
) {
    /** 描边中心线圆角矩形的半对角线（沿对角线到尖角的距离） */
    val centreLineHalfDiagonal: Float
        get() = (centreLineHalfSide - cornerRadius) * OXIDE_MARK_SQRT_2 + cornerRadius

    /** 含描边宽度的外缘半对角线 */
    val outerHalfDiagonal: Float
        get() = centreLineHalfDiagonal + strokeWidth / 2f
}

internal data class OxideMarkGeometry(
    val outer: OxideMarkRing,
    val inner: OxideMarkRing,
)

/**
 * 由方框边长算出两圈的圆角正方形参数。
 *
 * 圆角正方形（未旋转）半边长 a、圆角半径 r 时，沿对角线到尖角的距离是
 * (a - r) * sqrt(2) + r。反过来，给定外缘半对角线 hd 与圆角半径比例
 * cornerRatio、描边比例 strokeRatio：
 *
 *   stroke = hd * strokeRatio
 *   外缘圆角半径 = hd * cornerRadius，中心线圆角半径再减半个描边
 *   中心线半对角线 = hd - stroke / 2
 *   中心线半边长 = 中心线圆角半径 + (中心线半对角线 - 中心线圆角半径) / sqrt(2)
 *
 * 纯函数，不碰 Compose 运行时，所以几何可以脱离 Canvas 单测。
 */
internal fun oxideMarkGeometry(side: Float): OxideMarkGeometry {
    fun ring(
        outerHalfDiagonal: Float,
        cornerRadiusRatio: Float,
        strokeRatio: Float,
    ): OxideMarkRing {
        val stroke = outerHalfDiagonal * strokeRatio
        val corner = outerHalfDiagonal * cornerRadiusRatio - stroke / 2f
        val centreLineHd = outerHalfDiagonal - stroke / 2f
        val halfSide = corner + (centreLineHd - corner) / OXIDE_MARK_SQRT_2
        return OxideMarkRing(halfSide, corner, stroke)
    }

    val outerHd = side * OxideMarkHalfDiagonalRatio
    val innerHd = outerHd * OxideMarkInnerHalfDiagonalRatio
    return OxideMarkGeometry(
        outer = ring(outerHd, OxideMarkCornerRadiusRatio, OxideMarkStrokeRatio),
        inner = ring(innerHd, OxideMarkInnerCornerRadiusRatio, OxideMarkInnerStrokeRatio),
    )
}

/**
 * 一圈四个圆角的尖端，在**未旋转**坐标系里（原点=图形中心）。
 *
 * 圆角正方形半边长 a、圆角半径 r 时，圆角圆心在 (±(a-r), ±(a-r))，尖端再沿
 * 对角线外推 r/sqrt(2)。四个尖端的距离都等于 [OxideMarkRing.centreLineHalfDiagonal]，
 * 这正是菱形的四个"角"——不是八边形的八个顶点。
 */
internal fun oxideMarkRingTips(ring: OxideMarkRing): List<Offset> {
    val k = ring.centreLineHalfSide - ring.cornerRadius
    val t = ring.cornerRadius / OXIDE_MARK_SQRT_2
    return listOf(
        Offset(-k - t, -k - t),
        Offset(k + t, -k - t),
        Offset(k + t, k + t),
        Offset(-k - t, k + t),
    )
}

/**
 * 把一个**以图形中心为原点**的点绕方框中心转 [degrees] 度，落回方框坐标。
 *
 * [oxideMarkRingTips] 给的就是这种点，所以拿它和本函数一起可以在不构造
 * [Path] 的前提下验证形状确实转成了菱形。纯函数，可单测。
 */
internal fun oxideMarkRotate(point: Offset, size: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val c = size / 2f
    return Offset(
        c + point.x * cos - point.y * sin,
        c + point.x * sin + point.y * cos,
    )
}

/**
 * 一圈的路径：未旋转的圆角正方形转 [degrees] 度，落在 [size] 方框正中。
 *
 * 四个圆角都是 90° 的四分之一圆，用三次贝塞尔逼近（四分之一圆的常数
 * 0.5522847…），比 arcTo 更省事也更不容易在不同的 Path 实现上走样。
 */
internal fun oxideMarkRingPath(
    ring: OxideMarkRing,
    size: Float,
    degrees: Float = OxideMarkRotationDegrees,
): Path {
    val a = ring.centreLineHalfSide
    val r = ring.cornerRadius
    val k = a - r
    val h = 0.5522847f * r

    // 旋转只有这一处实现，[oxideMarkRotate] 同时被单测直接调用
    fun p(x: Float, y: Float) = oxideMarkRotate(Offset(x, y), size, degrees)

    // 四段三次贝塞尔在未旋转坐标系里绕一圈：起点 / 第一控制点 / 第二控制点 / 终点。
    // 圆角是四分之一圆，两端切线分别沿 −x 与 −y、正交且等长。
    val seg = arrayOf(
        arrayOf(-a, -k, -a, -k - h, -k - h, -a, -k, -a),
        arrayOf(-k, -a, -k + h, -a, a, -k - h, a, -k),
        arrayOf(a, -k, a, -k + h, k + h, a, k, a),
        arrayOf(k, a, k - h, a, -a, k + h, -a, k),
    )

    return Path().apply {
        val s0 = p(seg[0][0], seg[0][1])
        moveTo(s0.x, s0.y)
        seg.forEach { s ->
            val t0 = p(s[2], s[3])
            val t1 = p(s[4], s[5])
            val e = p(s[6], s[7])
            cubicTo(t0.x, t0.y, t1.x, t1.y, e.x, e.y)
        }
        close()
    }
}

/**
 * Oxide 标志
 *
 * 两圈圆角菱形都用描边画（[oxideMarkRingPath] 返回的是中心线），颜色是一条
 * 纯左右向的渐变，和美术稿一致——不是按象限上色，所以和主题色无关，
 * 但明暗底上都是高对比的：左端是近白，右端是饱和橙。
 *
 * 几何只用方框边长和上面那几个比例，开场动画与侧栏静止态共用同一套比例，
 * 因此换形状不影响 [dev.oxide.launcher.ui.components.OxideIntro] 的动画算法。
 */
@Composable
fun OxideMark(
    modifier: Modifier = Modifier,
    size: Dp = Oxide.MarkSize,
) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val geometry = oxideMarkGeometry(side)
        val left = (side - side * OxideMarkHalfDiagonalRatio * 2f) / 2f
        // horizontalGradient 的色标是 vararg：数组必须展开（*），不能传成一个 List
        val brush = Brush.horizontalGradient(
            *OxideMarkGradientStops,
            startX = left,
            endX = side - left,
        )
        drawPath(
            path = oxideMarkRingPath(geometry.outer, side),
            brush = brush,
            style = Stroke(width = geometry.outer.strokeWidth),
        )
        drawPath(
            path = oxideMarkRingPath(geometry.inner, side),
            brush = brush,
            style = Stroke(width = geometry.inner.strokeWidth),
        )
    }
}
