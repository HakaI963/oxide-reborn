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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 下拉面板的定位
 *
 * 这些用例全部围绕一个曾经的线上故障：面板的左边缘落在**控件的右边缘**上，
 * 于是面板看起来是从控件右边飞出去的一块，而不是从控件下面展开的。
 * 之所以会这样，是因为定位用的是 Popup 首帧传进来的内容尺寸，而那一帧它还是 0，
 * 于是 `控件右边缘 - 0` 就等于控件的右边缘本身。
 *
 * 现在定位不依赖那次测量，所以这里断言的是一套完整且确定的规则：
 * 与控件同侧边缘对齐、下方不够就翻上去、四边都留余量。
 */
class OxidePopoverPositionTest {

    private val window = IntSize(width = 1000, height = 800)
    private val margin = 8

    /** 控件在窗口中间，上下左右都很宽裕 */
    private val roomy = IntRect(left = 300, top = 300, right = 500, bottom = 340)

    private val panel = IntSize(width = 240, height = 200)

    @Test
    fun `opens below the control and lines up its left edge`() {
        val at = calculateOxidePopoverPosition(roomy, window, panel, margin)

        // 左边缘对齐，而不是控件的右边缘：这就是那次故障要断言住的行为
        assertEquals(roomy.left, at.x)
        assertEquals(roomy.bottom + margin, at.y)
    }

    @Test
    fun `right alignment lines up the panel right edge instead`() {
        val at = calculateOxidePopoverPosition(
            anchor = roomy,
            window = window,
            panel = panel,
            margin = margin,
            alignment = OxidePopoverAlignment.END
        )

        assertEquals(roomy.right, at.x + panel.width)
        assertEquals(roomy.bottom + margin, at.y)
    }

    @Test
    fun `flips above the control when there is not enough room below`() {
        // 只剩 60px，而面板要 200px
        val low = IntRect(left = 300, top = 700, right = 500, bottom = 740)

        val at = calculateOxidePopoverPosition(low, window, panel, margin)

        // 面板底边正好贴在控件上边缘之上，两者之间留同样的余量
        assertEquals(low.top - margin - panel.height, at.y)
        assertEquals(low.top - margin, at.y + panel.height)
    }

    @Test
    fun `stays below when there is more room below than above`() {
        // 上方只剩 100，下方有 500：下方更宽裕就不该翻
        val low = IntRect(left = 300, top = 260, right = 500, bottom = 300)

        val at = calculateOxidePopoverPosition(low, window, panel, margin)

        assertEquals(low.bottom + margin, at.y)
    }

    @Test
    fun `clamps against the right edge of the window`() {
        // 控件贴着窗口右缘
        val edge = IntRect(left = 900, top = 300, right = 990, bottom = 340)

        val at = calculateOxidePopoverPosition(edge, window, panel, margin)

        // 面板右边缘贴住窗口右缘并留出余量，因此面板不会被切掉
        assertEquals(window.width - margin, at.x + panel.width)
        assertEquals(edge.left.coerceAtMost(window.width - margin - panel.width), at.x)
    }

    @Test
    fun `clamps against the left edge of the window`() {
        // 控件自身都有一部分在窗口左边之外
        val edge = IntRect(left = -20, top = 300, right = 70, bottom = 340)

        val at = calculateOxidePopoverPosition(edge, window, panel, margin)

        assertEquals(margin, at.x)
    }

    @Test
    fun `a zero panel size still anchors to the control left edge`() {
        // Popup 首帧就是这个状态：内容还没量出来
        val at = calculateOxidePopoverPosition(roomy, window, IntSize.Zero, margin)

        assertEquals(roomy.left, at.x)
        assertEquals(roomy.bottom + margin, at.y)
    }

    @Test
    fun `a zero panel size never collapses onto the control right edge`() {
        // 这正是原来的故障：控件 300..500，空尺寸让 x 塌成了 500
        val at = calculateOxidePopoverPosition(roomy, window, IntSize.Zero, margin)

        assertEquals(roomy.left, at.x)
        if (roomy.left == roomy.right) return
        assert(roomy.right - at.x > 0) { "panel must start left of the control's right edge" }
    }

    @Test
    fun `a panel wider than the window is pinned inside the margins`() {
        val huge = IntSize(width = 4000, height = 4000)
        val at = calculateOxidePopoverPosition(roomy, window, huge, margin)

        // 面板比窗口还大时不可能完整可见，但仍然不能跑到 margin 之外
        assertEquals(margin, at.x)
        assertEquals(margin, at.y)
    }

    @Test
    fun `the panel always stays inside the window with a margin on all sides`() {
        val anchors = listOf(
            IntRect(0, 0, 40, 40),
            IntRect(960, 0, 1000, 40),
            IntRect(0, 760, 40, 800),
            IntRect(960, 760, 1000, 800),
            IntRect(480, 0, 520, 40),
            IntRect(480, 760, 520, 800),
            IntRect(480, 380, 520, 420),
            IntRect(-50, 380, 60, 420),
            IntRect(940, 380, 1050, 420)
        )
        val panels = listOf(
            IntSize(240, 200),
            IntSize(1, 1),
            IntSize(1000, 700),
            IntSize(240, 780)
        )

        for (anchor in anchors) {
            for (size in panels) {
                for (align in OxidePopoverAlignment.entries) {
                    val at = calculateOxidePopoverPosition(anchor, window, size, margin, align)
                    val label = "anchor=$anchor size=$size align=$align -> $at"

                    assert(at.x >= margin) { "left margin broken: $label" }
                    assert(at.y >= margin) { "top margin broken: $label" }
                    assert(at.x <= window.width - margin) { "right margin broken: $label" }
                    assert(at.y <= window.height - margin) { "bottom margin broken: $label" }
                }
            }
        }
    }

    @Test
    fun `positioning is independent of density because it only sees pixels`() {
        // 同样的几何按两倍像素重放一次：密度只改变输入的数值，不改变规则
        val small = calculateOxidePopoverPosition(roomy, window, panel, margin)
        val large = calculateOxidePopoverPosition(
            anchor = IntRect(
                roomy.left * 2,
                roomy.top * 2,
                roomy.right * 2,
                roomy.bottom * 2
            ),
            window = IntSize(window.width * 2, window.height * 2),
            panel = IntSize(panel.width * 2, panel.height * 2),
            margin = margin * 2
        )

        assertEquals(small.x * 2, large.x)
        assertEquals(small.y * 2, large.y)
    }

    @Test
    fun `letterboxed windows get the same alignment as full ones`() {
        // 窗口被切分后只是变小：同一个控件的相对位置仍然按同一套规则对齐
        val narrow = IntSize(width = 520, height = 400)
        val anchor = IntRect(left = 40, top = 120, right = 240, bottom = 160)
        val small = IntSize(width = 120, height = 100)

        val at = calculateOxidePopoverPosition(anchor, narrow, small, margin)

        assertEquals(anchor.left, at.x)
        assertEquals(anchor.bottom + margin, at.y)
    }

    @Test
    fun `a negative margin is treated as no margin rather than overflowing`() {
        val at = calculateOxidePopoverPosition(roomy, window, panel, -20)

        assertEquals(roomy.left, at.x)
        assertEquals(roomy.bottom, at.y)
    }
}
