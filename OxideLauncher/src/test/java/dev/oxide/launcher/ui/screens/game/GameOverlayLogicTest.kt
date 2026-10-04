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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏内浮层的纯逻辑
 *
 * 浮层画在一块**正在运行的游戏**上，因此有三件事必须能被钉死，
 * 而且都不需要启动 Compose：
 *
 * - **显示模式 → 哪几块出现**。悬浮球是默认就开着的界面
 *   （`AllSettings.showFPS` 默认 true），它显示什么必须只有一处出处；
 *   图表模式与内存模式组合起来时球上还剩不剩菜单图标，是一处会被顺手改坏的判断。
 * - **面板的有界高度**。游戏窗口可以非常小（分屏、自由窗口），
 *   面板加四周留白在任何窗口、任何界面缩放下都不允许超过窗口；
 *   退化窗口（0×0）也不能把面板算成 0——0 高的列表连一项都画不出来。
 * - **帧率轴**。它每秒重画一次，区间一旦为空，除法就会画出一屏 NaN。
 */
class GameOverlayLogicTest {

    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals(expected, actual.value, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 悬浮球的显示模式 → 可见性
    // -------------------------------------------------------------------------

    @Test
    fun `number mode shows the fps number and keeps the menu icon`() {
        val readouts = gameBallReadouts(60, FpsDisplayMode.NUMBER, showMemory = false)
        assertTrue(readouts.showFps)
        assertFalse(readouts.showFpsChart)
        assertFalse(readouts.showMemory)
        assertTrue(readouts.showMenuIcon)
        assertTrue(readouts.hasReadout)
    }

    @Test
    fun `chart mode shows the chart and gives up the menu icon`() {
        val readouts = gameBallReadouts(60, FpsDisplayMode.CHART, showMemory = false)
        assertTrue(readouts.showFps)
        assertTrue(readouts.showFpsChart)
        assertFalse(readouts.showMemory)
        // 图表本身就把球撑满了，再挂一个 28dp 的图标只会把 180dp 的图挤到屏幕外
        assertFalse(readouts.showMenuIcon)
    }

    @Test
    fun `memory alone drops the menu icon but keeps it when there is no readout`() {
        val withMemory = gameBallReadouts(null, FpsDisplayMode.NUMBER, showMemory = true)
        assertFalse(withMemory.showFps)
        assertFalse(withMemory.showFpsChart)
        assertTrue(withMemory.showMemory)
        assertFalse(withMemory.showMenuIcon)
        assertTrue(withMemory.hasReadout)

        val empty = gameBallReadouts(null, FpsDisplayMode.NUMBER, showMemory = false)
        assertFalse(empty.hasReadout)
        assertTrue(empty.showMenuIcon)
    }

    @Test
    fun `chart and memory compose - both rows show and the icon stays away`() {
        val readouts = gameBallReadouts(144, FpsDisplayMode.CHART, showMemory = true)
        assertTrue(readouts.showFps)
        assertTrue(readouts.showFpsChart)
        assertTrue(readouts.showMemory)
        assertFalse(readouts.showMenuIcon)
    }

    @Test
    fun `a zero reading is still a reading and is never hidden`() {
        // 0 是真实的读数；藏起来只会让人以为仪表坏了
        for (mode in FpsDisplayMode.entries) {
            val readouts = gameBallReadouts(0, mode, showMemory = false)
            assertTrue("mode $mode", readouts.showFps)
            assertEquals(mode == FpsDisplayMode.CHART, readouts.showFpsChart)
        }
    }

    @Test
    fun `no fps capture means no chart either`() {
        val readouts = gameBallReadouts(null, FpsDisplayMode.CHART, showMemory = false)
        assertFalse(readouts.showFps)
        assertFalse(readouts.showFpsChart)
        // 图模式的图标让位是"有图表时"的规则，没有图表时图标要回来
        assertTrue(readouts.showMenuIcon)
    }

    // -------------------------------------------------------------------------
    // 有界滚动
    // -------------------------------------------------------------------------

    @Test
    fun `a long log is clamped before it scrolls`() {
        val cap = 200.dp
        val natural = 5000.dp
        assertEquals(cap, gameOverlayScrollHeight(natural, cap))
        // 内容比上限短时不动它，因此短内容不会被拉高
        assertEquals(120.dp, gameOverlayScrollHeight(120.dp, cap))
        // 空内容可以是 0
        assertEquals(0.dp, gameOverlayScrollHeight(0.dp, cap))
    }

    @Test
    fun `an unusable height bound never reaches the scroll container as infinity`() {
        // 未指定的或无穷大的上限如果原样传下去，verticalScroll 会拿到 Infinity
        assertEquals(GameOverlayAbsoluteMin, gameOverlayScrollHeight(900.dp, Dp.Infinity))
        assertEquals(GameOverlayAbsoluteMin, gameOverlayScrollHeight(900.dp, Dp.Unspecified))
        assertEquals(GameOverlayAbsoluteMin, gameOverlayScrollHeight(900.dp, 0.dp))
        assertEquals(GameOverlayAbsoluteMin, gameOverlayScrollHeight(900.dp, (-5).dp))
    }

    @Test
    fun `a list grows with its rows until it hits the content cap`() {
        val row = 44.dp
        val cap = 200.dp
        assertEquals(88.dp, gameOverlayListHeight(2, cap, row))
        assertEquals(cap, gameOverlayListHeight(50, cap, row))
        assertEquals(0.dp, gameOverlayListHeight(0, cap, row))
        // 负的行数不存在于界面上，但被夹成 0 而不是产生负高度
        assertEquals(0.dp, gameOverlayListHeight(-3, cap, row))
    }

    @Test
    fun `the list overflows only when it cannot show every row`() {
        val row = 44.dp
        val cap = 200.dp
        // 4 行 = 176dp，装得下，因此不该挂滚动
        assertFalse(gameOverlayListOverflows(4, cap, row))
        // 5 行 = 220dp，超了
        assertTrue(gameOverlayListOverflows(5, cap, row))
        // 上限退化时不算溢出，否则一个只有一行的列表也挂上一条滚动条
        assertFalse(gameOverlayListOverflows(1, Dp.Infinity, row))
    }

    // -------------------------------------------------------------------------
    // 面板的有界高度
    // -------------------------------------------------------------------------

    @Test
    fun `the panel plus its margin never exceeds the window it is drawn in`() {
        val windows = listOf(
            1080 to 1920,
            640 to 360,
            480 to 320,
            360 to 240,
            320 to 180,
            240 to 160,
            200 to 120,
            160 to 90,
            120 to 80,
            96 to 64,
        )
        for (percent in listOf(75, 100, 125, 150)) {
            for ((width, height) in windows) {
                val b = gameOverlayBoundsFor(
                    windowWidthDp = width,
                    windowHeightDp = height,
                    guiScalePercent = percent,
                )
                val label = "${width}x$height @${percent}%"
                assertTrue(
                    "$label: width ${b.panelMaxWidth.value} + margin ${b.edgeMargin.value}",
                    b.panelMaxWidth.value + b.edgeMargin.value * 2f <= width + 0.01f,
                )
                assertTrue(
                    "$label: height ${b.panelMaxHeight.value} + margin ${b.edgeMargin.value}",
                    b.panelMaxHeight.value + b.edgeMargin.value * 2f <= height + 0.01f,
                )
                assertTrue("$label: every value is usable", b.isUsable())
            }
        }
    }

    @Test
    fun `a degenerate window yields finite positive values instead of zero`() {
        for (window in listOf(0 to 0, 0 to 480, 480 to 0, -1 to -1, -320 to -240)) {
            val b = gameOverlayBoundsFor(window.first, window.second)
            val label = "${window.first}x${window.second}"
            assertTrue("$label: edgeMargin", b.edgeMargin > 0.dp)
            assertTrue("$label: panelMaxWidth", b.panelMaxWidth > 0.dp)
            assertTrue("$label: panelMaxHeight", b.panelMaxHeight > 0.dp)
            assertTrue("$label: buttonHeight", b.buttonHeight > 0.dp)
            assertFalse("$label: padding", b.padding.value.isNaN())
        }
    }

    @Test
    fun `the content area is clamped before anything is allowed to scroll`() {
        // 一块很矮的窗口：内容区仍然必须够画出一行，否则列表连一项都不画
        val tiny = gameOverlayBoundsFor(240, 140)
        assertTrue(tiny.contentMaxHeight >= contentFloorOf(tiny))
        assertTrue(tiny.contentMaxHeight > 0.dp)
        // 正常窗口下：内容区 + 固定的两块 = 面板上限
        val normal = gameOverlayBoundsFor(1080, 1920)
        assertDp(GameOverlayChromeHeight, normal.chromeHeight.value)
        assertDp(
            normal.panelMaxHeight.value - normal.chromeHeight.value,
            normal.contentMaxHeight.value,
        )
        assertDp(normal.panelMaxHeight.value, normal.totalHeight.value)
    }

    @Test
    fun `the chrome and the content always add up to the panel height`() {
        // 这条等式是"先夹住再滚"能在面板上成立的前提：
        // 标题栏与底栏永远放得下，内容区拿到的是剩下的全部
        for (percent in listOf(75, 100, 125, 150)) {
            for (window in listOf(
                1080 to 1920, 640 to 360, 480 to 320, 360 to 240, 320 to 180,
                240 to 160, 200 to 120, 160 to 90, 120 to 80, 96 to 64,
            )) {
                val b = gameOverlayBoundsFor(window.first, window.second, percent)
                val label = "${window.first}x${window.second} @${percent}%"
                assertEquals(
                    label,
                    b.panelMaxHeight.value,
                    b.chromeHeight.value + b.contentMaxHeight.value,
                    0.01f,
                )
                // 固定的两块至多占七成，剩下的三成永远是内容的
                assertTrue(
                    "$label: chrome ${b.chromeHeight.value} of ${b.panelMaxHeight.value}",
                    b.chromeHeight.value <= b.panelMaxHeight.value * 0.70f + 0.01f,
                )
                assertTrue("$label: content ${b.contentMaxHeight.value}", b.contentMaxHeight > 0.dp)
            }
        }
    }

    @Test
    fun `a window too short for the chrome yields to the content area`() {
        // 90dp 高的窗口：写死的 112dp 固定块比面板还高。
        // 那时让位的必须是固定块，而不是内容区——否则被裁掉的是关闭与确认
        val tiny = gameOverlayBoundsFor(320, 90)
        assertTrue(
            "chrome ${tiny.chromeHeight.value} vs panel ${tiny.panelMaxHeight.value}",
            tiny.chromeHeight.value < GameOverlayChromeHeight,
        )
        assertTrue(tiny.contentMaxHeight > 0.dp)
        assertDp(tiny.panelMaxHeight.value, tiny.totalHeight.value)
        // 热区跟着窗口收，但不低于 24dp 的绝对下限
        assertTrue(tiny.buttonHeight >= GameOverlayMinTouchTargetFloor)
        assertTrue(tiny.buttonHeight < GameOverlayMinTouchTarget)
    }

    @Test
    fun `a bigger window never yields a smaller panel`() {
        var previousWidth = 0f
        var previousHeight = 0f
        for (width in listOf(120, 240, 360, 480, 640, 1080, 1440, 2560)) {
            val b = gameOverlayBoundsFor(width, 1200)
            assertTrue(
                "$width: ${b.panelMaxWidth.value} after ${previousWidth}",
                b.panelMaxWidth.value >= previousWidth,
            )
            previousWidth = b.panelMaxWidth.value
        }
        for (height in listOf(80, 160, 360, 640, 1200, 2400)) {
            val b = gameOverlayBoundsFor(1200, height)
            assertTrue(
                "$height: ${b.panelMaxHeight.value} after ${previousHeight}",
                b.panelMaxHeight.value >= previousHeight,
            )
            previousHeight = b.panelMaxHeight.value
        }
    }

    @Test
    fun `a roomy window still gets a dialog and not a page`() {
        val b = gameOverlayBoundsFor(2560, 2560)
        assertDp(GameOverlayPanelMaxWidth, b.panelMaxWidth.value)
        assertDp(GameOverlayPanelMaxHeight, b.panelMaxHeight.value)
        // 对话框不是页面：理想上限之内不再随窗口继续长
        assertTrue(b.panelMaxWidth < 2560.dp)
        assertTrue(b.panelMaxHeight < 2560.dp)
    }

    @Test
    fun `the gui scale grows the chrome and the touch targets together`() {
        val normal = gameOverlayBoundsFor(1080, 1920, guiScalePercent = 100)
        val large = gameOverlayBoundsFor(1080, 1920, guiScalePercent = 150)
        assertTrue(large.chromeHeight > normal.chromeHeight)
        assertTrue(large.buttonHeight > normal.buttonHeight)
        assertEquals(GameOverlayMinTouchTarget.value * 1.5f, large.buttonHeight.value, 0.01f)
        // 放大之后仍然装得进同一块窗口
        assertTrue(large.panelMaxWidth.value + large.edgeMargin.value * 2f <= 1080f + 0.01f)
    }

    // -------------------------------------------------------------------------
    // 帧率图
    // -------------------------------------------------------------------------

    @Test
    fun `the fps axis is never empty, even when fps is flat`() {
        // 改造前这里返回的是真实区间；fpsMin == fpsMax 时宽度为 0，
        // 除到零会画出一整屏 NaN
        for (value in listOf(0, 1, 30, 60, 144, 240, 1000)) {
            val axis = gameFpsAxis(value, value)
            assertTrue("$value: ${axis.min}..${axis.max}", axis.span > 0)
            assertTrue("$value: min", axis.min >= 0)
            assertTrue("$value: covers", axis.max >= value)
        }
    }

    @Test
    fun `the fps axis still covers a range no nice step can span`() {
        // 跨度超过 240×5，没有候选步长能覆盖；那条路原来直接返回真实区间
        val axis = gameFpsAxis(0, 100000)
        assertTrue(axis.span > 0)
        assertEquals(0, axis.min)
        assertTrue(axis.max >= 100000)
        assertTrue(axis.span >= GameFpsAxisSegments)
    }

    @Test
    fun `the fps axis maps every reading into the plot`() {
        // 轴不保证把数据**包**在里面（"好数"边界会向外取整），
        // 保证的是区间外的数据被夹到两端，而不是画到画布外面去
        val axis = gameFpsAxis(24, 96)
        assertTrue(axis.span > 0)
        for (fps in listOf(0, 12, 24, 60, 96, 240, 999)) {
            val f = axis.fraction(fps)
            assertTrue("fps=$fps -> $f", f.isFinite())
            assertTrue("fps=$fps -> $f", f in 0f..1f)
        }
        // 两端精确落在网格线上
        assertEquals(0f, axis.fraction(axis.min), 0.0001f)
        assertEquals(1f, axis.fraction(axis.max), 0.0001f)
        // 区间外夹住，不越界
        assertEquals(0f, axis.fraction(axis.min - 500), 0.0001f)
        assertEquals(1f, axis.fraction(axis.max + 500), 0.0001f)
    }

    @Test
    fun `fps ticks increase and land on the top of the axis`() {
        for (axis in listOf(
            gameFpsAxis(0, 0),
            gameFpsAxis(50, 70),
            gameFpsAxis(24, 96),
            gameFpsAxis(0, 100000),
        )) {
            val ticks = gameFpsAxisTicks(axis)
            assertEquals(GameFpsAxisSegments + 1, ticks.size)
            assertEquals(axis.min, ticks.first())
            assertEquals(axis.max, ticks.last())
            ticks.zipWithNext { a, b -> assertTrue("$a -> $b", b > a) }
        }
    }

    @Test
    fun `an absurd fps reading cannot overflow the axis arithmetic`() {
        for (pair in listOf(
            Int.MIN_VALUE to Int.MAX_VALUE,
            -5 to 100000,
            0 to Int.MAX_VALUE,
            500 to 400,
        )) {
            val axis = gameFpsAxis(pair.first, pair.second)
            val label = "${pair.first}..${pair.second}"
            assertTrue("$label: min ${axis.min}", axis.min >= 0)
            assertTrue("$label: span ${axis.span}", axis.span > 0)
            assertTrue("$label: span ${axis.span}", axis.span <= 200000)
            // 刻度也必须还是一列递增的整数
            val ticks = gameFpsAxisTicks(axis)
            assertEquals(GameFpsAxisSegments + 1, ticks.size)
            ticks.zipWithNext { a, b -> assertTrue("$label: $a -> $b", b > a) }
        }
    }

    @Test
    fun `the chart draws no series before the first sample`() {
        assertFalse(fpsChartHasSeries(emptyList()))
        assertTrue(fpsChartHasSeries(listOf(0)))
        assertTrue(fpsChartHasSeries(listOf(60, 30)))
    }

    @Test
    fun `chart ticks share the grid so the labels line up with the rules`() {
        // 每一格的中心是 (k + 0.5) * 绘图区高 / 6；k 条分割线与 k 个标注因此重合。
        // 参数是绘图区高度（已经扣掉描边与内边距），不是图框的外高
        val plotHeight = 120.dp
        val cell = fpsLabelCellHeight(plotHeight)
        assertDp(20f, cell.value)
        assertEquals(GameFpsAxisSegments + 1, (plotHeight / cell).value.toInt())
        val half = cell.value / 2f
        for (k in 0..GameFpsAxisSegments) {
            val rule = half + k * (plotHeight.value - half * 2f) / GameFpsAxisSegments
            val labelCentre = (k + 0.5f) * plotHeight.value / (GameFpsAxisSegments + 1)
            assertEquals("k=$k", labelCentre, rule, 0.01f)
        }
    }

    @Test
    fun `the chart never grows past the game window`() {
        for (window in listOf(1080 to 1920, 320 to 240, 200 to 160, 100 to 80, 0 to 0)) {
            val size = gameFpsChartSize(window.first, window.second)
            val label = "${window.first}x${window.second}"
            assertTrue("$label: ${size.width.value}", size.width > 0.dp)
            assertTrue("$label: ${size.height.value}", size.height > 0.dp)
            if (window.first > 0) {
                assertTrue(
                    "$label: width ${size.width.value}",
                    size.width.value <= window.first.toFloat(),
                )
                assertTrue(
                    "$label: height ${size.height.value}",
                    size.height.value <= window.second.toFloat(),
                )
            }
        }
        // 正常窗口下仍是原来那块 180x120
        val normal = gameFpsChartSize(1080, 1920)
        assertDp(GameFpsChartWidth, normal.width.value)
        assertDp(GameFpsChartHeight, normal.height.value)
    }

    // -------------------------------------------------------------------------
    // 内存读数
    // -------------------------------------------------------------------------

    @Test
    fun `the memory readout prints the real megabytes`() {
        assertEquals("1200MB/4096MB", formatGameOverlayMemory(1200, 4096))
        // 系统回收的瞬间偶尔给出负的差值，不能印成负数
        assertEquals("0MB/4096MB", formatGameOverlayMemory(-5, 4096))
        assertEquals("0MB/0MB", formatGameOverlayMemory(0, 0))
    }

    @Test
    fun `the memory fraction is clamped and survives an unknown total`() {
        assertEquals(0.5f, gameOverlayMemoryFraction(2048, 4096), 0.0001f)
        // 总量读不出来时是 0，不是 NaN：NaN 会让整块图消失
        assertEquals(0f, gameOverlayMemoryFraction(100, 0), 0.0001f)
        assertEquals(1f, gameOverlayMemoryFraction(9999, 4096), 0.0001f)
        assertEquals(0f, gameOverlayMemoryFraction(-1, 4096), 0.0001f)
    }

    // -------------------------------------------------------------------------
    // 多人联机底栏
    // -------------------------------------------------------------------------

    @Test
    fun `the log button turns into refresh only while the log is on screen`() {
        assertEquals(
            GameOverlayLogToggle(isRefresh = false, enabled = true),
            multiplayerLogToggle(showingLog = false, collectingLog = false),
        )
        assertEquals(
            GameOverlayLogToggle(isRefresh = true, enabled = true),
            multiplayerLogToggle(showingLog = true, collectingLog = false),
        )
    }

    @Test
    fun `the log button is dead while a log is being collected`() {
        // 与改造前的 enabled = logOperation !is CollectingLog 一致
        assertEquals(
            GameOverlayLogToggle(isRefresh = false, enabled = false),
            multiplayerLogToggle(showingLog = false, collectingLog = true),
        )
        assertEquals(
            GameOverlayLogToggle(isRefresh = true, enabled = false),
            multiplayerLogToggle(showingLog = true, collectingLog = true),
        )
    }

    /** 每个值都得是正的、有限的：`coerceIn` 遇到 NaN 既不抛也不换，NaN 会一路传到布局 */
    private fun GameOverlayBounds.isUsable(): Boolean {
        val values = listOf(
            edgeMargin.value,
            panelMaxWidth.value,
            panelMaxHeight.value,
            chromeHeight.value,
            contentMaxHeight.value,
            padding.value,
            rowGap.value,
            buttonHeight.value,
            guiScale,
        )
        return values.all { it.isFinite() && it >= 0f } &&
            edgeMargin > 0.dp &&
            panelMaxWidth > 0.dp &&
            panelMaxHeight > 0.dp &&
            contentMaxHeight > 0.dp &&
            contentMaxHeight >= contentFloorOf(this) &&
            buttonHeight >= GameOverlayMinTouchTargetFloor
    }

    /** 内容区的下限，与 `gameOverlayBoundsFor` 里的算法一致 */
    private fun contentFloorOf(bounds: GameOverlayBounds): Dp =
        (GameOverlayMinContentHeight.dp * bounds.guiScale)
            .coerceAtMost(bounds.panelMaxHeight * GameOverlayContentFloorMaxFraction)
}