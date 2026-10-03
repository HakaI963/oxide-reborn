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
 * 主页右栏下限与发现页几何的单测
 *
 * 期望值全部来自参考稿 `/tmp/opencode/ref/ref.html`：
 *  - `.home{grid-template-columns:minmax(0,1.45fr) minmax(270px,.56fr)}`，
 *    `@media(max-width:1120px)` 处收成 `minmax(0,1.35fr) minmax(235px,.65fr)`；
 *  - `.discoverLayout{grid-template-columns:145px minmax(0,1fr)}`；
 *  - `.resultsGrid{grid-template-columns:repeat(2,minmax(0,1fr))}`。
 *
 * 全部走纯函数，不碰任何组合环境，也不需要 Robolectric。
 */
class OxideHomeDiscoverGeometryTest {

    // ---- 主页右栏下限 -------------------------------------------------------

    /**
     * 右列写的是 `minmax(下限, fr)`：下限是硬保证。
     * 901~1120dp 这一档拿不到 270px，但 235px 必须给到；
     * 只按 weight 分（1.45f / 0.56f）会把它压到 192px。
     */
    @Test
    fun mediumBandKeepsTheTwoThirtyFiveDpFloor() {
        for (width in intArrayOf(901, 950, 1000, 1060, 1120)) {
            val metrics = oxideMetricsFor(width, 700)
            assertEquals(OxideWidthClass.Medium, metrics.widthClass)
            val columns = homeColumnsAt(width, metrics)
            assertTrue(
                "width=$width rail=${columns.rail.value}dp",
                columns.rail.value >= 235f - 0.01f
            )
        }
    }

    /** >1120px 走参考稿的默认档，下限抬到 270px */
    @Test
    fun wideBandsUseTheTwoSeventyDpFloor() {
        for (width in intArrayOf(1121, 1280, 1281, 1920)) {
            val metrics = oxideMetricsFor(width, 760)
            val columns = homeColumnsAt(width, metrics)
            assertTrue(
                "width=$width rail=${columns.rail.value}dp",
                columns.rail.value >= 270f - 0.01f
            )
        }
    }

    /**
     * 宽度正好等于"下限 + 间距"时右列吃满下限、左列为 0；
     * 再窄一点右列也不能撑破可用宽度，两列都不出现负宽。
     */
    @Test
    fun neitherColumnGoesNegativeBelowTheFloor() {
        val metrics = oxideMetricsFor(1000, 700)

        val at = oxideHomeColumnsFor(
            contentWidth = metrics.cardGap + 235.dp,
            gap = metrics.cardGap,
            widthClass = metrics.widthClass,
        )
        assertDp(235f, at.rail)
        assertDp(0f, at.hero)

        val under = oxideHomeColumnsFor(
            contentWidth = 200.dp,
            gap = metrics.cardGap,
            widthClass = metrics.widthClass,
        )
        assertDp(190f, under.rail)
        assertDp(0f, under.hero)

        val empty = oxideHomeColumnsFor(
            contentWidth = 0.dp,
            gap = metrics.cardGap,
            widthClass = metrics.widthClass,
        )
        assertDp(0f, empty.hero)
        assertDp(0f, empty.rail)
    }

    /**
     * 两列加上列间距必须刚好等于内容宽度：既不溢出，也不会有空白剩下。
     *
     * 这条同时钉住"左列不饿死"——它拿到的是下限之外按 fr 比例分的份额。
     */
    @Test
    fun theTwoColumnsExactlyFillTheContentWidth() {
        for (width in intArrayOf(901, 1000, 1120, 1121, 1280, 1920, 2560)) {
            val metrics = oxideMetricsFor(width, 760)
            val columns = homeColumnsAt(width, metrics)
            assertDp(
                "width=$width",
                homeContentWidth(width, metrics).value,
                (columns.hero + columns.rail + metrics.cardGap).value
            )
        }
    }

    /**
     * 左列始终留得下正文：hero 内边距是 `cardGap * 2`，
     * 副标题再占 72% 宽度，因此窄端也还有实打实的可用宽度。
     */
    @Test
    fun theHeroNeverStarvesAtTheNarrowEnd() {
        for (width in intArrayOf(901, 950, 1000, 1120)) {
            val metrics = oxideMetricsFor(width, 700)
            val columns = homeColumnsAt(width, metrics)
            val inner = columns.hero - metrics.cardGap * 4f
            assertTrue("width=$width inner=${inner.value}dp", inner.value >= 120f)
        }
    }

    // ---- 发现页 -------------------------------------------------------------

    /**
     * 类别栏是参考稿写死的 145px，不随侧栏在三档之间变化。
     * 之前按 `sidebarWidth * 0.58` 推导只有 107dp。
     */
    @Test
    fun categoryRailIsTheReferenceFixedWidth() {
        for (width in intArrayOf(640, 800, 901, 1121, 1920)) {
            assertDp(145f, oxideMetricsFor(width, 700).categoryRailWidth)
        }
    }

    /**
     * 参考稿的结果网格恒为 `repeat(2,minmax(0,1fr))`。
     * 共享上限抬到 3 是给实例网格（`repeat(3,minmax(0,1fr))`）准备的，
     * 发现页自己再夹一层，所以结果网格永远出不了两列。
     */
    @Test
    fun discoverResultsNeverExceedTwoColumns() {
        for ((width, height) in listOf(640 to 360, 800 to 360, 1280 to 760, 1920 to 1080)) {
            val metrics = oxideMetricsFor(width, height)
            for (probe in intArrayOf(120, 300, 460, 887, 1527, 4000)) {
                val columns = discoverResultColumns(metrics, probe.dp)
                assertTrue("width=$width probe=$probe columns=$columns", columns in 1..2)
            }
        }
    }

    /** 1280 与 1920 这一档真实内容宽度下确实是两列，而不是被共享上限带成三列 */
    @Test
    fun discoverResultsAreTwoColumnsOnWideWindows() {
        for (width in intArrayOf(1280, 1920)) {
            val metrics = oxideMetricsFor(width, 760)
            // 内容区 = 整屏 − 侧栏 − 左右留白 − 145px 类别栏 − 列间距
            val results = discoverResultsWidth(width, metrics)
            // 共享上限在宽档是 3，会给出 3，所以这条断言的是发现页自己的封顶
            assertTrue("width=$width shared=${metrics.gridColumns(results)}", metrics.gridColumns(results) >= 2)
            assertEquals(2, discoverResultColumns(metrics, results))
        }
    }

    /**
     * 类别栏固定 145px 之后，窄到 640 的横屏仍然给右侧结果区留出可用宽度，
     * 而且那一档的结果网格不会退到零列。
     */
    @Test
    fun theResultsAreaStillFitsBesideTheFixedRail() {
        for (width in intArrayOf(640, 800)) {
            val metrics = oxideMetricsFor(width, 360)
            val results = discoverResultsWidth(width, metrics)
            assertTrue("width=$width results=${results.value}dp", results.value > 0f)
            assertTrue(
                "width=$width columns=${discoverResultColumns(metrics, results)}",
                discoverResultColumns(metrics, results) >= 1
            )
        }
    }

    // ---- 工具 ---------------------------------------------------------------

    /**
     * 主页的内容宽度
     *
     * 主页没有套 [dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn]，
     * 它的可用宽度就是整屏减掉侧栏；[oxideHomeColumnsFor] 只吃这个宽度，与
     * [OxideMetrics.gridColumns] 那条"必须先扣掉 pagePaddingH"的约定不同。
     */
    private fun homeContentWidth(width: Int, metrics: OxideMetrics): Dp =
        width.dp - metrics.sidebarWidth

    private fun homeColumnsAt(width: Int, metrics: OxideMetrics): OxideHomeColumns =
        oxideHomeColumnsFor(
            contentWidth = homeContentWidth(width, metrics),
            gap = metrics.cardGap,
            widthClass = metrics.widthClass,
        )

    /** 发现页右侧结果区的宽度：内容区减掉固定类别栏与列间距 */
    private fun discoverResultsWidth(width: Int, metrics: OxideMetrics): Dp =
        width.dp - metrics.sidebarWidth - metrics.pagePaddingH * 2f -
            metrics.categoryRailWidth - metrics.cardGap

    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals(
            "expected ${expected}dp but was ${actual.value}dp",
            expected,
            actual.value,
            0.01f,
        )
    }

    private fun assertDp(label: String, expected: Float, actual: Float) {
        assertEquals("$label: expected ${expected}dp but was ${actual}dp", expected, actual, 0.01f)
    }
}
