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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行高
 *
 * 参考稿是两行网格。行高必须先保证卡片内容放得下，否则底部一行会被裁掉；
 * 空间够的时候再按剩余高度平分，于是所有卡片落在同一条基线上。
 */
class InstanceRowHeightTest {

    /** 卡片内容的真实最小高度，与页面里的常量一致 */
    private val minRow = 37f + 36f + 28f + 22f + 11f

    @Test
    fun splitsAvailableHeightBetweenTwoRows() {
        // 300dp 可用、9dp 间距：每行 (300-9)/2 = 145.5dp
        val row = instanceRowHeight(
            availableHeightDp = 300f,
            cardGapDp = 9f,
            rows = 2,
            minRowHeightDp = minRow,
        )
        assertEquals(145.5f, row, 0.01f)
    }

    @Test
    fun twoRowsPlusTheGapFitExactlyInsideTheAvailableHeight() {
        val available = 300f
        val gap = 9f
        val row = instanceRowHeight(available, gap, rows = 2, minRowHeightDp = minRow)
        assertTrue("two rows must not overflow the available height", row * 2f + gap <= available)
    }

    @Test
    fun neverGoesBelowTheCardContentMinimum() {
        // 矮屏上宁可让网格滚动，也不把卡片底部裁掉
        val row = instanceRowHeight(
            availableHeightDp = 120f,
            cardGapDp = 9f,
            rows = 2,
            minRowHeightDp = minRow,
        )
        assertEquals(minRow, row, 0.001f)
    }

    @Test
    fun extremeShortScreenStillReturnsTheMinimum() {
        val row = instanceRowHeight(
            availableHeightDp = 0f,
            cardGapDp = 13f,
            rows = 2,
            minRowHeightDp = minRow,
        )
        assertEquals(minRow, row, 0.001f)
    }

    @Test
    fun zeroOrNegativeRowsDegradesToTheMinimum() {
        assertEquals(
            minRow,
            instanceRowHeight(300f, 9f, rows = 0, minRowHeightDp = minRow),
            0.001f,
        )
        assertEquals(
            minRow,
            instanceRowHeight(300f, 9f, rows = -3, minRowHeightDp = minRow),
            0.001f,
        )
    }

    @Test
    fun negativeGapIsTreatedAsZero() {
        val row = instanceRowHeight(
            availableHeightDp = 300f,
            cardGapDp = -20f,
            rows = 2,
            minRowHeightDp = minRow,
        )
        assertEquals(150f, row, 0.01f)
    }

    @Test
    fun negativeMinimumIsClampedToZero() {
        val row = instanceRowHeight(
            availableHeightDp = 300f,
            cardGapDp = 9f,
            rows = 2,
            minRowHeightDp = -50f,
        )
        assertEquals(145.5f, row, 0.01f)
    }

    @Test
    fun threeRowsUseOneFewerGap() {
        // rows-1 个间距，不是 rows 个：少算一个会让整张网格多出一个间距的高度。
        // 这里把下限压到 0，确保走的是平分分支而不是兜底分支。
        val row = instanceRowHeight(
            availableHeightDp = 400f,
            cardGapDp = 10f,
            rows = 3,
            minRowHeightDp = 0f,
        )
        assertEquals(130f, row, 0.01f)
    }

    @Test
    fun twoRowsUseOneGapBetweenThemAndNoneAround() {
        // 多算一个间距会让整张网格比可用高度矮一截，卡片底部就空出一条
        val row = instanceRowHeight(
            availableHeightDp = 400f,
            cardGapDp = 10f,
            rows = 2,
            minRowHeightDp = 0f,
        )
        assertEquals(195f, row, 0.01f)
    }
}

/**
 * 网格列数
 *
 * [OxideMetrics.gridColumns] 是既有实现，这里只钉住实例页真正依赖的性质：
 * 列数必须落在 metrics 自己声明的区间内，并且在任何宽度下都不小于一列，
 * 因此小屏不会被压成零列、大屏也不会排出比设计上限更多的列。
 */
class InstanceGridColumnsTest {

    /** 从窄到宽，覆盖分屏窄条到 4K，横向的每个整数档位都取样 */
    private val tiers = listOf(
        320, 480, 560, 640, 720, 800, 900, 960, 1024, 1120,
        1180, 1280, 1366, 1600, 1920, 2560, 3440,
    ).map { widthDp -> oxideMetricsFor(widthDp, heightDp = 480) }

    private fun columnsAt(metrics: OxideMetrics, contentWidthDp: Float): Int =
        metrics.gridColumns(contentWidthDp.dp)

    /** 这么窄的话连一张最小卡片都放不下，应该只剩一列 */
    private val tooNarrow = listOf(0f, 1f, 40f, 120f, 200f)

    private fun OxideMetrics.minWidthFor(columns: Int): Float =
        cardMinWidth.value * columns + cardGap.value * (columns - 1)

    @Test
    fun columnsNeverDropBelowOne() {
        // 小屏横屏、分屏切出来的窄条：内容区可能比一张卡片还窄，
        // 这时必须退成一列，而不是零列把网格弄空
        tiers.forEach { metrics ->
            tooNarrow.forEach { width ->
                assertTrue(
                    "${metrics.widthClass} rendered ${columnsAt(metrics, width)} columns at ${width}dp",
                    columnsAt(metrics, width) >= metrics.minCardColumns,
                )
            }
        }
    }

    @Test
    fun columnsNeverExceedTheDeclaredCeiling() {
        tiers.forEach { metrics ->
            listOf(320f, 600f, 900f, 1200f, 2000f, 4000f).forEach { width ->
                assertTrue(
                    "${metrics.widthClass} exceeded ${metrics.maxCardColumns} columns at ${width}dp",
                    columnsAt(metrics, width) <= metrics.maxCardColumns,
                )
            }
        }
    }

    @Test
    fun columnsNeverOverSubscribeTheAvailableWidth() {
        // 卡片宽度不得小于 metrics 自己声明的最小值，否则卡片内容会被压掉。
        // 唯一可以例外的情况是"再少一列连最小卡片也放不下"，也就是只能挤成一列。
        tiers.forEach { metrics ->
            listOf(260f, 500f, 700f, 950f, 1300f, 1900f).forEach { width ->
                val columns = columnsAt(metrics, width)
                if (metrics.minWidthFor(columns) <= width) return@forEach

                val fewer = columns - 1
                assertTrue(
                    "${metrics.widthClass} over-subscribed ${width}dp with $columns columns",
                    fewer < metrics.minCardColumns || metrics.minWidthFor(fewer) > width,
                )
            }
        }
    }

    @Test
    fun wideningNeverLosesAColumn() {
        // 窗口被拉宽时列数只能增加或不变，绝不能倒退
        tiers.forEach { metrics ->
            var previous = 0
            listOf(0f, 200f, 400f, 700f, 1000f, 1400f, 2000f).forEach { width ->
                val columns = columnsAt(metrics, width)
                assertTrue(
                    "${metrics.widthClass} lost a column when widening to ${width}dp",
                    columns >= previous,
                )
                previous = columns
            }
        }
    }

    @Test
    fun aLargeTabletGetsMoreColumnsThanASmallPhone() {
        // 同样按实测宽度算，大屏就该比小屏多一列，否则布局没有跟着窗口变
        val phone = oxideMetricsFor(560, 480)
        val tablet = oxideMetricsFor(1280, 800)
        assertTrue(
            "a tablet must fit more columns than a phone",
            tablet.gridColumns(1180.dp) > phone.gridColumns(380.dp),
        )
    }

    @Test
    fun everyTierFitsAtLeastOneCardInItsOwnWidth() {
        // 各自的宽度扣掉侧栏与页面留白后，至少要放得下一列
        listOf(560, 900, 1120, 1280, 1920).forEach { widthDp ->
            val metrics = oxideMetricsFor(widthDp, 480)
            val content = (widthDp - metrics.sidebarWidth.value.toInt()) -
                metrics.pagePaddingH.value.toInt()
            val columns = metrics.gridColumns(content.dp)
            assertTrue(
                "$widthDp must render at least one card, got $columns",
                columns >= 1,
            )
        }
    }
}
