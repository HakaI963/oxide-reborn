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
        // 参考稿 `.instancesGrid{gap:9px}` 在 ≤900px 那一档是
        // `grid-template-rows:repeat(3,minmax(0,1fr))`，因此三行之间只有**两个**间距：
        // (400 − 2×10) / 3 = 380 / 3 = 126.667。写成 130 相当于只算了一个间距。
        // 这里把下限压到 0，确保走的是平分分支而不是兜底分支。
        val row = instanceRowHeight(
            availableHeightDp = 400f,
            cardGapDp = 10f,
            rows = 3,
            minRowHeightDp = 0f,
        )
        assertEquals(126.667f, row, 0.01f)
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
 * 每一条断言都从 [OxideMetrics.gridColumns] 与**实测内容宽度**出发：内容宽度就是
 * 实例页喂给它的那个值（整屏 − 侧栏 − 左右留白），因此这里没有任何一个列数是写死的，
 * 也没有一个探针宽度是凭空填的——探针的步长就是 `cardMinWidth + cardGap`，
 * 而那正是 `gridColumns` 拿来比较的量。
 *
 * 钉住的性质：小屏不会被排成零列；大屏也不会排得比参考稿的
 * `.instancesGrid`（>900px 三列、≤900px 两列）更多；卡片不会被压得比
 * [OxideMetrics.cardMinWidth] 还窄；窗口拉宽时列数只增不减；
 * 界面放大时内容区本身变窄，列数因此只减不增。
 */
class InstanceGridColumnsTest {

    /** 从分屏窄条到 4K，逐档取样 */
    private val widths = listOf(
        320, 480, 560, 640, 720, 800, 900, 960, 1024, 1120,
        1180, 1280, 1366, 1600, 1920, 2560, 3440,
    )

    private fun metricsAt(
        widthDp: Int,
        guiScalePercent: Int = OxideGuiScaleDefaultPercent,
    ): OxideMetrics = oxideMetricsFor(widthDp, heightDp = 480, guiScalePercent = guiScalePercent)

    /**
     * 实例网格真正拿到手的宽度
     *
     * 侧栏与左右留白都要先扣掉：[OxideMetrics.gridColumns] 的约定就是传进来的
     * 宽度已经不含留白，漏扣就会凭空多出一列。
     */
    private fun instanceContentWidth(metrics: OxideMetrics, widthDp: Int): Dp =
        widthDp.dp - metrics.sidebarWidth - metrics.pagePaddingH * 2

    /** 这一档在自己的屏幕上真正会排出的列数 */
    private fun columnsOnItsOwnScreen(
        widthDp: Int,
        guiScalePercent: Int = OxideGuiScaleDefaultPercent,
    ): Int {
        val metrics = metricsAt(widthDp, guiScalePercent)
        return metrics.gridColumns(instanceContentWidth(metrics, widthDp))
    }

    /**
     * 探针宽度：从 0 一直排到装满三列所需的宽度，全部由 metrics 自己推出来
     *
     * 前几档比一张最小卡片还窄（分屏窄条的情形），后面几档则一定排得出三列，
     * 因此 0 → 1 → 2 → 3 这几处变化都被跨过去了。
     */
    private fun OxideMetrics.probeWidths(): List<Dp> =
        (0..PROBE_STEPS).map { step -> (cardMinWidth + cardGap) * step * 3 / PROBE_STEPS }

    /** n 列至少要这么宽才排得下 n 张最小卡片 */
    private fun OxideMetrics.minWidthFor(columns: Int): Dp =
        cardMinWidth * columns + cardGap * (columns - 1)

    @Test
    fun columnsNeverDropBelowOne() {
        // 小屏横屏、分屏切出来的窄条：内容区可能比一张卡片还窄，
        // 这时必须退成一列，而不是零列把网格弄空
        widths.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            metrics.probeWidths().forEach { probe ->
                assertTrue(
                    "${metrics.widthClass} rendered ${metrics.gridColumns(probe)} columns " +
                        "at ${probe.value}dp",
                    metrics.gridColumns(probe) >= metrics.minCardColumns,
                )
            }
        }
    }

    @Test
    fun columnsNeverExceedTheDeclaredCeiling() {
        widths.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            // 宽到装不下为止时也必须停在声明的上限，而不是无限往下排
            (metrics.probeWidths() + listOf(metrics.cardMinWidth * 100)).forEach { probe ->
                assertTrue(
                    "${metrics.widthClass} exceeded ${metrics.maxCardColumns} columns at ${probe.value}dp",
                    metrics.gridColumns(probe) <= metrics.maxCardColumns,
                )
            }
        }
    }

    @Test
    fun columnsNeverOverSubscribeTheAvailableWidth() {
        // 卡片宽度不得小于 metrics 自己声明的最小值，否则卡片内容会被压掉。
        // 唯一可以例外的情况是"再少一列连最小卡片也放不下"，也就是只能挤成一列。
        widths.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            metrics.probeWidths().forEach { probe ->
                val columns = metrics.gridColumns(probe)
                if (metrics.minWidthFor(columns) <= probe) return@forEach

                val fewer = columns - 1
                assertTrue(
                    "${metrics.widthClass} over-subscribed ${probe.value}dp with $columns columns",
                    fewer < metrics.minCardColumns || metrics.minWidthFor(fewer) > probe,
                )
            }
        }
    }

    @Test
    fun wideningNeverLosesAColumn() {
        // 窗口被拉宽时列数只能增加或不变，绝不能倒退
        widths.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            var previous = 0
            metrics.probeWidths().forEach { probe ->
                val columns = metrics.gridColumns(probe)
                assertTrue(
                    "${metrics.widthClass} lost a column when widening to ${probe.value}dp",
                    columns >= previous,
                )
                previous = columns
            }
        }
    }

    @Test
    fun everyTierFitsAtLeastOneCardInItsOwnWidth() {
        // 各自的宽度扣掉侧栏与左右留白之后，至少要放得下一列
        widths.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            val content = instanceContentWidth(metrics, widthDp)
            val columns = metrics.gridColumns(content)
            assertTrue(
                "$widthDp must render at least one card, got $columns (content=${content.value}dp)",
                columns >= 1,
            )
        }
    }

    @Test
    fun aLargeTabletGetsMoreColumnsThanASmallPhone() {
        // 同样按各自的实测内容宽度算：大屏就该比小屏多一列，否则布局没有跟着窗口变。
        // 560dp 那一档实测内容宽度 373dp，排得下一列；1280dp 那一档 1042dp，排得下三列。
        assertEquals(1, columnsOnItsOwnScreen(560))
        assertEquals(3, columnsOnItsOwnScreen(1280))
        assertTrue(
            "a tablet must fit more columns than a phone",
            columnsOnItsOwnScreen(1280) > columnsOnItsOwnScreen(560),
        )
    }

    @Test
    fun theSharedCeilingIsThreeAboveTheCompactBand() {
        // 参考稿 `.instancesGrid{grid-template-columns:repeat(3,minmax(0,1fr))}`，
        // 且 ≤900px 那条媒体查询把它收成两列；因此共享上限在三档里是 3，只有紧凑档是 2。
        widths.filter { it > OxideBreakpoints.CompactMax }.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            assertEquals("$widthDp 的共享上限必须是 3", 3, metrics.maxCardColumns)
            assertEquals(
                "宽到装不下为止时必须顶到共享上限 3",
                3,
                metrics.gridColumns(metrics.cardMinWidth * 100),
            )
        }
    }

    @Test
    fun theCompactBandFollowsTheReferencesTwoColumns() {
        widths.filter { it <= OxideBreakpoints.CompactMax }.forEach { widthDp ->
            val metrics = metricsAt(widthDp)
            assertEquals("$widthDp 的共享上限必须是 2", 2, metrics.maxCardColumns)
            assertEquals(
                "紧凑档最多两列",
                2,
                metrics.gridColumns(metrics.cardMinWidth * 100),
            )
        }
    }

    @Test
    fun theDiscoverPagesOwnCeilingStaysOutOfTheSharedFunction() {
        // 发现页的结果网格恒为参考稿的 `repeat(2,minmax(0,1fr))`，但那两列是发现页
        // 自己夹的：同一个宽度喂给共享函数必须仍然给得出三列（实例网格要三列）。
        val metrics = metricsAt(1920)
        val wide = metrics.cardMinWidth * 6
        assertEquals(3, metrics.gridColumns(wide))
        assertEquals(2, discoverResultColumns(metrics, wide))
    }

    @Test
    fun aBiggerGuiScaleNeverGainsAColumn() {
        // 放大界面时卡片最小宽度、侧栏与留白一起乘系数，而整屏的 dp 数不变，
        // 内容区因此变窄：同一块屏幕只可能落到更少的列数。
        widths.forEach { widthDp ->
            val columns = OxideGuiScaleSteps.map { scale -> columnsOnItsOwnScreen(widthDp, scale) }
            assertTrue(
                "界面放大后列数只减不增：$widthDp -> $columns",
                columns.zipWithNext().all { (smaller, bigger) -> bigger <= smaller },
            )
        }
        // 1120dp 这一档实测：100% 时内容区 914dp 排三列；150% 时侧栏与留白一起变大，
        // 内容区只剩 811dp，于是掉到两列。
        assertEquals(3, columnsOnItsOwnScreen(1120, OxideGuiScaleDefaultPercent))
        assertEquals(2, columnsOnItsOwnScreen(1120, OxideGuiScaleMaxPercent))
    }

    private companion object {
        /** 探针档数：步长是 0.3 张卡片，够跨过 0 → 1 → 2 → 3 每一处列数变化 */
        const val PROBE_STEPS = 30
    }
}
