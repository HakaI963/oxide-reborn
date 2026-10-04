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
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * OxideMark 的几何单测。
 *
 * 断言的对象是**实测值**，来源是 1024px 美术稿（/emulated/icon.png）四条
 * 0.5 覆盖率等值线的最小二乘拟合（7158 个边界点，rms 0.28px）：
 *
 *   等值线                半边长   描边    圆角半径（上/右/左/下）
 *   外环 · 外缘            236.499  19.130  103.6 / 114.4 / 112.2 / 117.3
 *   外环 · 内缘            217.369         90.6 /  94.4 /  94.9 /  96.1
 *   内菱形 · 外缘          139.512  14.841   50.8 /  51.1 /  48.6 /  50.8
 *   内菱形 · 内缘          124.672         36.7 /  36.1 /  34.2 /  35.8
 *
 * 圆角半径取四角均值（外轮廓 111.868 / 288.123 = 0.38826，
 * 内轮廓 50.323 / 176.456 = 0.28519）；四角本身并不相等，差 ±6%，
 * 见 OxideLogo.kt 的说明。
 *
 * 这些断言只碰纯函数和数据类，不碰 [androidx.compose.ui.graphics.Path]——
 * Path 在本地 JVM 单测里没有 Skia backing，不能用。
 */
class OxideMarkGeometryTest {

    // ---- 图形必须撑满给它的方框：开场动画和侧栏静止尺寸都靠这条 ----------

    @Test
    fun outerBoundingBoxIsHalfTheSideDiagonal() {
        // 半对角线占边长的一半 —— 也就是图形的外接菱形正好是方框的内接菱形。
        // 侧栏静止尺寸是 Oxide.MarkSize(37dp)，开场动画按 MarkSize * scale 推算，
        // 这条一旦变了，落位和飞行轨迹会一起错。
        assertEquals(0.5f, OxideMarkHalfDiagonalRatio, 0f)
        for (side in floatArrayOf(16f, 37f, 64f, 100f, 432f)) {
            val g = oxideMarkGeometry(side)
            assertEquals(
                "mark must fill the box at side=$side",
                side * OxideMarkHalfDiagonalRatio,
                g.outer.outerHalfDiagonal,
                1e-3f,
            )
        }
    }

    @Test
    fun outerStrokeAtTheSidebarRestSize() {
        // 37dp 静止尺寸（Oxide.MarkSize）下：外环描边 1.228px，内环描边 0.953px。
        // 内环是亚像素的，这是美术稿自己的比例（描边只有外接菱形宽度的 2.58%），
        // 靠抗锯齿仍然读得出来；侧栏里不额外加粗，否则就和图标对不上了。
        val g = oxideMarkGeometry(37f)
        assertEquals(1.22822f, g.outer.strokeWidth, 1e-4f)
        assertEquals(0.95286f, g.inner.strokeWidth, 1e-4f)
    }

    // ---- 圆角：这是圆角**正方形**转 45°，不是正八边形，也不是圆 ----------

    @Test
    fun cornerRadiusRatioMatchesTheArtwork() {
        // 外轮廓圆角半径 / 外缘半对角线 = 111.868 / 288.123
        assertEquals(0.38826f, OxideMarkCornerRadiusRatio, 1e-5f)
        val g = oxideMarkGeometry(100f)
        val outerCorner = g.outer.cornerRadius + g.outer.strokeWidth / 2f
        assertEquals(
            OxideMarkCornerRadiusRatio,
            outerCorner / g.outer.outerHalfDiagonal,
            1e-5f,
        )
    }

    @Test
    fun cornerRadiusIsRoundedButNotACircle() {
        // 圆角半径必须大于 0（是圆角），也必须小于半边长（还是正方形，不是圆）。
        for (side in floatArrayOf(16f, 37f, 100f)) {
            for (ring in listOf(oxideMarkGeometry(side).outer, oxideMarkGeometry(side).inner)) {
                assertTrue("corner radius must be positive", ring.cornerRadius > 0f)
                assertTrue(
                    "corner radius ${ring.cornerRadius} must stay below the half side " +
                        "${ring.centreLineHalfSide}",
                    ring.cornerRadius < ring.centreLineHalfSide,
                )
            }
        }
    }

    @Test
    fun ringHasFourTipsNotEight() {
        // 菱形只有四个角。八边形那种"八个顶点、45° 等距"的形状是错的美术稿。
        val g = oxideMarkGeometry(100f)
        assertEquals(4, oxideMarkRingTips(g.outer).size)
        assertEquals(4, oxideMarkRingTips(g.inner).size)
    }

    @Test
    fun rotatedTipsLandOnTheBoxAxes() {
        // 正方形的角本来在自身坐标的对角线上，转 45° 后就落在方框的四条轴上：
        // 上/下/左/右四个尖端正好在方框四边的中点上。这就是菱形。
        val side = 100f
        val g = oxideMarkGeometry(side)
        for (ring in listOf(g.outer, g.inner)) {
            for (tip in oxideMarkRingTips(ring)) {
                val p = oxideMarkRotate(tip, side, OxideMarkRotationDegrees)
                val dx = abs(p.x - side / 2f)
                val dy = abs(p.y - side / 2f)
                assertTrue(
                    "tip must sit on a box axis, was dx=$dx dy=$dy",
                    (dx < 1e-2f && dy > 1f) || (dy < 1e-2f && dx > 1f),
                )
                assertEquals(
                    ring.centreLineHalfDiagonal,
                    hypot(dx, dy),
                    1e-2f,
                )
            }
        }
        // 外环的尖端正好够到方框边长的一半
        assertEquals(side * OxideMarkHalfDiagonalRatio, g.outer.centreLineHalfDiagonal, 1e-2f)
    }

    @Test
    fun rotatedTipsAreNinetyDegreesApart() {
        // 相邻尖端 90°，不是 45°。
        val side = 100f
        val g = oxideMarkGeometry(side)
        val angles = oxideMarkRingTips(g.outer)
            .map { oxideMarkRotate(it, side, OxideMarkRotationDegrees) }
            .map { Math.toDegrees(Math.atan2((it.y - side / 2f).toDouble(), (it.x - side / 2f).toDouble())) }
            .map { if (it < 0) it + 360.0 else it }
            .sorted()
        for (i in 0 until angles.size - 1) {
            assertEquals(90.0, angles[i + 1] - angles[i], 0.05)
        }
        assertEquals(90.0, angles[0] + 360.0 - angles[angles.size - 1], 0.05)
    }

    @Test
    fun theSquareIsRotatedFortyFiveDegrees() {
        // 实测拟合角 44.927°，即 45°（偏差 0.07° 在 288px 半径上是 0.37px）。
        assertEquals(45f, OxideMarkRotationDegrees, 0f)
    }

    // ---- 描边 -------------------------------------------------------------

    @Test
    fun strokeRatioMatchesTheArtwork() {
        // 外环描边 / 外缘半对角线 = (236.499 - 217.369) / 288.123
        assertEquals(0.06639f, OxideMarkStrokeRatio, 1e-5f)
        val g = oxideMarkGeometry(100f)
        assertEquals(
            OxideMarkStrokeRatio,
            g.outer.strokeWidth / g.outer.outerHalfDiagonal,
            1e-5f,
        )
    }

    @Test
    fun theOuterRingHasRealWallThickness() {
        val g = oxideMarkGeometry(100f)
        val wall = g.outer.strokeWidth
        assertTrue("the ring must have real thickness, was $wall", wall > 3f)
        // 环不能翻面：内缘必须在外缘之内
        assertTrue(g.outer.outerHalfDiagonal - wall > 0f)
    }

    @Test
    fun innerStrokeRatioMatchesTheArtwork() {
        // 内环描边 / 内菱形半对角线 = (139.512 - 124.672) / 176.456
        assertEquals(0.08411f, OxideMarkInnerStrokeRatio, 1e-5f)
        val g = oxideMarkGeometry(100f)
        assertEquals(
            OxideMarkInnerStrokeRatio,
            g.inner.strokeWidth / g.inner.outerHalfDiagonal,
            1e-5f,
        )
    }

    // ---- 内菱形 -----------------------------------------------------------

    @Test
    fun innerDiamondHalfDiagonalRatioMatchesTheArtwork() {
        // 176.456 / 288.123
        assertEquals(0.61243f, OxideMarkInnerHalfDiagonalRatio, 1e-5f)
        val g = oxideMarkGeometry(100f)
        assertEquals(
            OxideMarkInnerHalfDiagonalRatio,
            g.inner.outerHalfDiagonal / g.outer.outerHalfDiagonal,
            1e-4f,
        )
    }

    @Test
    fun innerCornerRadiusRatioMatchesTheArtwork() {
        // 50.323 / 176.456
        assertEquals(0.28519f, OxideMarkInnerCornerRadiusRatio, 1e-5f)
        val g = oxideMarkGeometry(100f)
        val innerCorner = g.inner.cornerRadius + g.inner.strokeWidth / 2f
        assertEquals(
            OxideMarkInnerCornerRadiusRatio,
            innerCorner / g.inner.outerHalfDiagonal,
            1e-5f,
        )
    }

    @Test
    fun innerDiamondIsConcentricWithTheOuterOne() {
        // 实测：给内环单独拟合中心得 (511.908, 492.183)，外环中心 (512.103, 492.004)。
        val measured = OxideMarkMeasuredInnerOffset
        assertEquals(-0.195f, measured.x, 1e-3f)
        assertEquals(0.179f, measured.y, 1e-3f)
        assertEquals(0.00092f, OxideMarkMeasuredInnerOffsetRatio, 1e-5f)

        // 画的时候按同心画：两圈尖端的平均位置都在方框正中，也就是偏移取 0。
        // 美术稿里没有"向左上偏"的设计意图。
        for (side in floatArrayOf(37f, 100f)) {
            val g = oxideMarkGeometry(side)
            listOf(g.outer to side, g.inner to side).forEach { (ring, s) ->
                val pts = oxideMarkRingTips(ring).map { oxideMarkRotate(it, s, OxideMarkRotationDegrees) }
                val mx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
                val my = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
                assertEquals("ring centre x at side=$s", s / 2f, mx, 1e-2f)
                assertEquals("ring centre y at side=$s", s / 2f, my, 1e-2f)
            }
        }
    }

    @Test
    fun measuredInnerOffsetIsBelowTheNoiseFloor() {
        // 0.092% 的偏移只有描边宽度的 1/7，比外轮廓四角圆角半径 ±6% 的摆动
        // （带来约 2px 的中心不确定度）小一个数量级 —— 所以把它抹平成同心是有
        // 依据的，而不是丢掉了一个设计意图。
        val g = oxideMarkGeometry(100f)
        val offsetPx = OxideMarkMeasuredInnerOffsetRatio * g.outer.outerHalfDiagonal
        assertEquals(0.046f, offsetPx, 1e-3f)
        assertTrue("offset must be far below the wall thickness", offsetPx < g.outer.strokeWidth / 7f)
        assertTrue("offset must be below 1% of the mark", offsetPx < g.outer.outerHalfDiagonal * 0.01f)
    }

    @Test
    fun theTwoRingsAreNestedWithAVisibleGap() {
        // 内菱形的外缘必须落在外环的内缘之内，而且要留出看得见的空隙。
        val g = oxideMarkGeometry(100f)
        val outerInnerEdge = g.outer.outerHalfDiagonal - g.outer.strokeWidth
        val gap = outerInnerEdge - g.inner.outerHalfDiagonal
        assertEquals(16.06f, gap, 5e-2f)
        assertTrue("rings must not touch", gap > 0f)
    }

    // ---- 与尺寸无关 -------------------------------------------------------

    @Test
    fun geometryIsScaleFree() {
        // 所有比例都挂在半对角线上，换任何边长都不该变形。
        val base = oxideMarkGeometry(1000f)
        for (side in floatArrayOf(12f, 37f, 96f, 108f, 432f)) {
            val g = oxideMarkGeometry(side)
            val k = side / 1000f
            assertEquals(base.outer.strokeWidth * k, g.outer.strokeWidth, 1e-2f)
            assertEquals(base.outer.cornerRadius * k, g.outer.cornerRadius, 1e-2f)
            assertEquals(base.inner.strokeWidth * k, g.inner.strokeWidth, 1e-2f)
            assertEquals(base.inner.cornerRadius * k, g.inner.cornerRadius, 1e-2f)
        }
    }
}