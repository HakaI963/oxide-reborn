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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class OxideMarkGeometryTest {

    @Test
    fun octagonHasEightVertices() {
        assertEquals(8, octagonVertices(0f, 0f, 10f).size)
    }

    @Test
    fun everyVertexSitsOnTheCircumcircle() {
        val r = 37f
        octagonVertices(5f, 9f, r).forEach { p ->
            assertEquals(r, hypot(p.x - 5f, p.y - 9f), 0.02f)
        }
    }

    /**
     * 平边必须朝上/下/左/右，四角切角落在对角线上——美术稿里上边是平的，
     * 顶点起点取 22.5° 正是为了这个。取最靠近"正上"的顶点看 x 偏移即可。
     */
    @Test
    fun flatSidesFaceTheAxes() {
        val pts = octagonVertices(0f, 0f, 100f)
        // 顶点分布：±22.5°、±67.5°、±112.5°、±157.5°（y 向下）
        val mostUp = pts.minBy { it.y }
        // 正上方的顶点应当左右对称地偏开，而不是正落在 x=0 上
        assertTrue("expected a chamfer at the top, got ${mostUp.x}", abs(mostUp.x) > 1f)
        // 最左/最右的顶点同理
        val mostLeft = pts.minBy { it.x }
        val mostRight = pts.maxBy { it.x }
        assertTrue(abs(mostLeft.y) > 1f)
        assertTrue(abs(mostRight.y) > 1f)
    }

    @Test
    fun verticesAreEvenlySpacedBy45Degrees() {
        val pts = octagonVertices(0f, 0f, 10f)
        val angles = pts.map {
            Math.toDegrees(kotlin.math.atan2(it.y.toDouble(), it.x.toDouble()))
        }
        for (i in 0 until angles.size - 1) {
            assertEquals(45.0, angles[i + 1] - angles[i], 0.001)
        }
        // 首尾相接：最后一个顶点回到第一个要绕整整一圈
        assertEquals(315.0, angles[0] + 360.0 - angles[angles.size - 1], 0.001)
    }

    @Test
    fun outerRadiusFillsTheBox() {
        // 0.5 意味着外接圆的四个顶点正好落在方框四边的中点上
        assertEquals(0.5f, OxideMarkOuterRadiusRatio, 0f)
    }

    @Test
    fun holeIsSmallerThanHalfTheOuterRing() {
        assertTrue(OxideMarkInnerRadiusRatio < 0.5f)
        assertTrue(OxideMarkInnerRadiusRatio > 0.3f)
        // 环本身必须真的有厚度，不能退化成一条线
        assertTrue(OxideMarkOuterRadiusRatio - OxideMarkInnerRadiusRatio > 0.2f)
    }

    @Test
    fun octagonApexMatchesThirtySevenDpAtDefaultSize() {
        // 侧栏静止尺寸就是 Oxide.MarkSize(37dp)，外接圆半径应为其一半
        assertEquals(18.5f, 37f * OxideMarkOuterRadiusRatio, 0.001f)
    }

    @Test
    fun topEdgeOfTheOctagonIsHorizontal() {
        // y 最小的两个顶点 y 相同 -> 上边是平的，而不是尖角
        val pts = octagonVertices(0f, 0f, 100f)
        val top = pts.sortedBy { it.y }
        assertEquals(top[0].y, top[1].y, 0.001f)
        assertTrue("top edge must be the highest points", top[0].y < 0f)
        // 同理四边都要是平的：x 最小/最大处各有两个顶点 y 相同
        val byX = pts.sortedBy { it.x }
        assertEquals(byX[0].y, byX[1].y, 0.001f)
        assertEquals(byX[byX.size - 1].y, byX[byX.size - 2].y, 0.001f)
    }

    @Test
    fun radiusIsHonouredForBothRings() {
        // 洞的外接圆半径必须严格小于外轮廓，否则环会翻面
        val outer = 100f
        val inner = outer * OxideMarkInnerRadiusRatio
        assertTrue(inner < outer)
        assertEquals(
            outer.toDouble(),
            octagonVertices(0f, 0f, outer).first().let { hypot(it.x, it.y) },
            0.001,
        )
    }

    @Test
    fun symmetryHoldsAcrossAllFourAxes() {
        val pts = octagonVertices(0f, 0f, 50f)
        // 上下镜像
        pts.forEachIndexed { i, p ->
            val m = pts[(pts.size - i) % pts.size]
            assertEquals(p.x, m.x, 0.001f)
            assertEquals(abs(p.y), abs(m.y), 0.001f)
        }
        // 左右镜像：角度 theta -> 180-theta，顶点下标 0..7 上就是 i -> 3-i
        pts.forEachIndexed { i, p ->
            val m = pts[(3 - i + pts.size) % pts.size]
            assertEquals(abs(p.x), abs(m.x), 0.001f)
            assertEquals(p.y, m.y, 0.001f)
        }
    }

    @Test
    fun trigonometryHelpersAgreeWithTheGeneratedVertices() {
        val p = octagonVertices(0f, 0f, 20f)[0]
        val a = Math.toRadians(22.5)
        assertEquals(20.0 * cos(a), p.x.toDouble(), 0.001)
        assertEquals(20.0 * sin(a), p.y.toDouble(), 0.001)
    }
}