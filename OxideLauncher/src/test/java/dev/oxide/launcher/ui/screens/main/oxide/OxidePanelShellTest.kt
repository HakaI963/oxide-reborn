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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 居中面板的尺寸策略
 *
 * 这几条钉的是那条策略真正要保证的东西，而不是某一个分辨率上的具体像素：
 * 面板永远不超过窗口、永远是有界的有限值、并且在窗口变大时**不会**跟着长满整屏
 * （否则它就退回成一个全屏方块了，那正是这一轮要改掉的形状）。
 */
class OxidePanelShellTest {

    /**
     * 断言两个 dp 落在同一个值上
     *
     * 上限是拿 metrics 里的 dp 乘一个系数算出来的，而 `Dp` 存的是 Float：
     * `220dp × 2.4f` 在 Float 里是 528.00003，不是 528。所以这里比数值并留一点容差，
     * 而不是比两个 `Dp` 是否相等。
     */
    private fun assertDp(expected: Dp, actual: Dp, label: String) {
        assertEquals(
            "$label: expected ${expected.value}dp but was ${actual.value}dp",
            expected.value,
            actual.value,
            0.01f,
        )
    }

    /** 常见横屏尺寸下的实际结果，逐条钉住 */
    @Test
    fun boundsAtSixFortyByThreeSixty() {
        val metrics = oxideMetricsFor(640, 360)
        val bounds = oxidePanelBoundsFor(640.dp, 360.dp, metrics)

        // 紧凑档：cardMinWidth 220dp × 2.4 = 528dp，窗口扣掉两侧留白后还剩 598dp，取小者
        assertDp(528.dp, bounds.width, "640x360 width")
        // navItemHeight 38dp × 16 = 608dp，窗口扣掉上下留白后只剩 318dp，取小者
        assertDp(318.dp, bounds.height, "640x360 height")
        assertEquals(metrics.pagePaddingH, bounds.gutter)
    }

    @Test
    fun boundsAtTwelveEightyBySevenTwenty() {
        val metrics = oxideMetricsFor(1280, 720)
        val bounds = oxidePanelBoundsFor(1280.dp, 720.dp, metrics)

        // 扩展档：cardMinWidth 285dp × 2.4 = 684dp，窗口装得下，所以取这一档
        assertDp(684.dp, bounds.width, "1280x720 width")
        // navItemHeight 38dp × 16 = 608dp，窗口扣掉留白后还剩 666dp，所以面板不满高
        assertDp(608.dp, bounds.height, "1280x720 height")
        // 面板没有铺满窗口，两侧都还留着 gutter
        assertTrue(bounds.width < 1280.dp - bounds.gutter * 2)
        assertTrue(bounds.height < 720.dp - bounds.gutter * 2)
    }

    /**
     * 真实横屏窗口上的结果
     *
     * 面板拿到的是宿主扣掉侧栏与顶栏之后剩下的那块，而不是整块屏幕，
     * 所以这里把那一层也算进来：640x360 的窗口里面板是 453×272，
     * 1280x720 的窗口里是 684×608。前者窄到只放得下一列，后者放得下两列。
     */
    @Test
    fun boundsInsideTheDestinationAreaOfARealWindow() {
        val small = oxideMetricsFor(640, 360)
        val smallAreaWidth = 640.dp - small.sidebarWidth
        val smallAreaHeight = 360.dp - small.topBarHeight
        val smallBounds = oxidePanelBoundsFor(smallAreaWidth, smallAreaHeight, small)
        assertDp(453.dp, smallBounds.width, "640x360 window width")
        assertDp(272.dp, smallBounds.height, "640x360 window height")
        assertFalse(oxidePanelTwoColumns(smallBounds.width, small))

        val large = oxideMetricsFor(1280, 720)
        val largeAreaWidth = 1280.dp - large.sidebarWidth
        val largeAreaHeight = 720.dp - large.topBarHeight
        val largeBounds = oxidePanelBoundsFor(largeAreaWidth, largeAreaHeight, large)
        assertDp(684.dp, largeBounds.width, "1280x720 window width")
        assertDp(608.dp, largeBounds.height, "1280x720 window height")
        assertTrue(oxidePanelTwoColumns(largeBounds.width, large))
    }

    /** 面板必须始终是一块紧凑的面板：大屏上也不许长成整页 */
    @Test
    fun panelStaysCompactOnBigWindows() {
        listOf(1920 to 1080, 2560 to 1440, 3440 to 1440).forEach { (width, height) ->
            val metrics = oxideMetricsFor(width, height)
            val bounds = oxidePanelBoundsFor(width.dp, height.dp, metrics)
            val area = bounds.width.value * bounds.height.value
            val window = width.toFloat() * height.toFloat()
            assertTrue(
                "$width x $height: the panel covers ${area / window} of the window",
                area / window < 0.55f,
            )
        }
    }

    /**
     * 面板永远不超过窗口
     *
     * 这一条是硬约束：它保证面板里那个 `heightIn(max = ...)` 拿到的是一个有限的绝对值，
     * 于是里面的 `verticalScroll` 不会收到 `maxHeight == Infinity`
     * （那正是 v1.5.0 那个 P0 崩溃）。
     */
    @Test
    fun panelNeverExceedsTheWindow() {
        val sizes = listOf(
            320 to 240, 480 to 320, 640 to 360, 800 to 480, 1024 to 600,
            1280 to 720, 1920 to 1080, 2560 to 1440,
        )
        sizes.forEach { (width, height) ->
            listOf(OxideGuiScaleMinPercent, 100, OxideGuiScaleMaxPercent).forEach { scale ->
                val metrics = oxideMetricsFor(width, height, scale)
                val bounds = oxidePanelBoundsFor(width.dp, height.dp, metrics)
                assertTrue("$width x $height @${scale}%: width", bounds.width <= width.dp)
                assertTrue("$width x $height @${scale}%: height", bounds.height <= height.dp)
            }
        }
    }

    /** 有界：宽高都是有限的非负值，绝不出现 Infinity 或负数 */
    @Test
    fun panelBoundsAreAlwaysFiniteAndPositive() {
        val sizes = listOf(
            1 to 1, 20 to 20, 42 to 42, 100 to 60, 640 to 360, 1280 to 720,
        )
        sizes.forEach { (width, height) ->
            val metrics = oxideMetricsFor(width, height)
            val bounds = oxidePanelBoundsFor(width.dp, height.dp, metrics)
            assertTrue("$width x $height width", bounds.width.value.isFinite())
            assertTrue("$width x $height height", bounds.height.value.isFinite())
            assertTrue("$width x $height width >= 0", bounds.width.value >= 0f)
            assertTrue("$width x $height height >= 0", bounds.height.value >= 0f)
        }
    }

    /** 窗口放大时面板也放大，但不是按窗口同比例放大——上限来自 metrics */
    @Test
    fun panelGrowsWithTheWindowButStopsShortOfIt() {
        val small = oxidePanelBoundsFor(640.dp, 360.dp, oxideMetricsFor(640, 360))
        val large = oxidePanelBoundsFor(1280.dp, 720.dp, oxideMetricsFor(1280, 720))
        assertTrue(large.width > small.width)
        assertTrue(large.height > small.height)
    }

    /** 界面缩放放大时面板跟着放大：上限走的是 metrics，不是写死的 dp */
    @Test
    fun panelFollowsTheUiScale() {
        val normal = oxidePanelBoundsFor(1280.dp, 720.dp, oxideMetricsFor(1280, 720, 100))
        val big = oxidePanelBoundsFor(1280.dp, 720.dp, oxideMetricsFor(1280, 720, 150))
        assertTrue(big.width > normal.width)
        assertTrue(big.height > normal.height)
    }

    /**
     * 两列版式的判据
     *
     * 每一列都要留 1.1 个卡片宽：行里那排小图标按钮先吃掉固定宽度，
     * 列刚好一张卡片宽时名字就没地方放了。所以 640x360 的小横屏（面板 453dp）
     * 退回一列，而 1280x720（面板 684dp）才并排。
     */
    @Test
    fun twoColumnsOnlyWhenBothColumnsStayReadable() {
        val compact = oxideMetricsFor(640, 360)
        // 220dp × 2.2 + 9dp = 493dp 是紧凑档的门槛
        assertFalse(oxidePanelTwoColumns(453.dp, compact))
        assertTrue(oxidePanelTwoColumns(560.dp, compact))
        assertFalse(oxidePanelTwoColumns(300.dp, compact))

        val expanded = oxideMetricsFor(1280, 720)
        // 285dp × 2.2 + 10dp = 637dp 是扩展档的门槛
        assertTrue(oxidePanelTwoColumns(684.dp, expanded))
        assertFalse(oxidePanelTwoColumns(600.dp, expanded))
    }
}