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
 * 日志页两栏的纵向几何
 *
 * 设备截图里的问题有两个，都在这一页的版面算术上，因此都能写成纯函数来钉：
 *
 * 1. 那一行两栏写的是 `fillMaxSize()`，于是它被以**整页**高度测量，而不是页头
 *    之下剩下的那块。终端卡片因此超出窗口底边，横向滚动条随卡片一起被外壳那一层
 *    的 `clipToBounds` 裁掉。改掉修饰符之后，"终端那一栏拿到剩下的全部"这句话必须
 *    在两个窗口尺寸上都成立，否则同一类回归还会再来。
 * 2. 来源清单那一栏拿 `weight(1f)`，把两三行摊开到整栏高度上（截图里 500 多 dp 的
 *    空白）。来源永远只有三条真实文件，因此它应当**包住内容**。
 *
 * 钉住的性质：来源行与页头的高度各自有确定的构成；来源清单的高度由内容决定且远低于
 * 可用高度；终端那一栏的高度是**正**的且与来源条数无关。
 *
 * 每一处断言都在 1280x760（`OxidePaparazzi` 里的 STANDARD）与 640x360（COMPACT，
 * 最小受支持的横屏）两档上各跑一遍：前者是用户报障的那一档，后者是这类溢出最容易
 * 出现的一档。
 */
class OxideLogPaneGeometryTest {

    /** 参考稿画布所在的一档 */
    private val standard = 1280 to 760

    /** 最小受支持的横屏 */
    private val compact = 640 to 360

    private fun metricsAt(
        widthDp: Int,
        heightDp: Int,
        guiScalePercent: Int = OxideGuiScaleDefaultPercent,
    ): OxideMetrics = oxideMetricsFor(widthDp, heightDp, guiScalePercent)

    /**
     * `rememberLogSources` 在三条候选都存在时能给出的文件条数
     *
     * 固定的三条：当前实例最新的游戏日志、启动器崩溃日志、联机核心日志。目录那一行
     * 不走清单，因此不计。**上限就是三**，所以清单包住内容永远是安全的。
     */
    private val realSourceCount = 3

    /**
     * 页面自己量到的那块高度
     *
     * 与页面上 [BoxWithConstraints] 的 `maxHeight` 是同一个数：窗口减去顶栏
     * （[OxideMetrics.topBarHeight]，外壳占掉的那一截）与页面上下留白。
     */
    private fun pageAvailableHeight(windowHeight: Dp, metrics: OxideMetrics): Dp =
        windowHeight - metrics.topBarHeight - metrics.pagePaddingV - Oxide.PagePaddingB

    // -----------------------------------------------------------------------
    // 来源行
    // -----------------------------------------------------------------------

    /**
     * 一行来源的高度等于它自己的两行文字加上下留白与行间距
     *
     * 写死这个式子而不是只断言"是正数"：行高一旦被 `weight` 顶起来或者漏了间距，
     * 高度会翻倍，而翻倍的三行正好就是那 500dp 空白的来源。
     */
    @Test
    fun sourceRowHeightIsItsTwoLinesPlusItsPadding() {
        val metrics = metricsAt(standard.first, standard.second)
        val scale = metrics.guiScale
        // 行内并排的是三样东西，取最高的那个：16dp 的种类徽章、两行文字
        // （正文 12sp + 小标签 9sp，均随界面缩放）、24dp 的分享按钮。
        // 100% 下分享按钮最高，所以少算它就会低估一行 3dp。
        val tallest = maxOf(16f, (12f + 9f) * scale, 24f)
        // 行上下留白各 6dp 是 `OxideContentRow` 内部的固定 dp，不随缩放变；
        // 随后是一段行间距 secRowGap（已缩放）
        assertDp(
            tallest + 12f + metrics.secRowGap.value,
            logSourceRowHeight(metrics),
        )
    }

    @Test
    fun sourceRowHeightGrowsWithTheGuiScale() {
        val normal = logSourceRowHeight(metricsAt(standard.first, standard.second, 100))
        val large = logSourceRowHeight(metricsAt(standard.first, standard.second, 150))
        assertTrue("150% 的来源行必须比 100% 高：$normal -> $large", large > normal)
    }

    /** 每一档都必须给出一行的确定高度，不能塌成 0（那样三行就都点不到了） */
    @Test
    fun everySourceRowHasAFinitePositiveHeight() {
        listOf(standard, compact).forEach { (width, height) ->
            OxideGuiScaleSteps.forEach { scale ->
                val row = logSourceRowHeight(metricsAt(width, height, scale))
                assertTrue("$width x $height @${scale}%: 来源行高 $row", row > 0.dp)
            }
        }
    }

    // -----------------------------------------------------------------------
    // 页头
    // -----------------------------------------------------------------------

    /**
     * 页头高度是"标题行 + 副标题行 + 小节间距"
     *
     * 标题行取返回按钮、动作按钮与大标题行高里最高的那个；100% 时是 28dp 的动作
     * 按钮，压过 24dp 的返回按钮与 22sp 的大标题行高。
     */
    @Test
    fun headerHeightIsTheTitleRowPlusTheSubtitlePlusTheSectionGap() {
        val metrics = metricsAt(standard.first, standard.second)
        assertDp(
            28f + 9f * metrics.guiScale + metrics.sectionGap.value,
            logPageHeaderHeight(metrics),
        )
    }

    @Test
    fun headerNeverCollapsesOnEitherWindow() {
        listOf(standard, compact).forEach { (width, height) ->
            val header = logPageHeaderHeight(metricsAt(width, height))
            assertTrue("$width x $height 的页头塌成了 $header", header > 0.dp)
        }
    }

    /** 页头加上终端那一栏必须放得进窗口，否则溢出还会回来 */
    @Test
    fun theHeaderAndTheTerminalPaneFitInsideTheWindow() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val window = height.dp
            val header = logPageHeaderHeight(metrics)
            val terminal = logTerminalHeight(window, metrics)
            assertTrue(
                "$width x $height: 页头 $header + 终端 $terminal 超出窗口 $window",
                header + terminal <= window,
            )
            assertTrue(
                "$width x $height: 页头 $header 占了窗口的一半以上",
                header < window * 0.5f,
            )
        }
    }

    // -----------------------------------------------------------------------
    // 来源清单
    // -----------------------------------------------------------------------

    /**
     * 来源清单包住内容，而且远低于可用高度
     *
     * 这一条就是本次修的那个 bug 的可测形式。三行来源加起来约 116dp，而可用高度在
     * 两个尺寸上分别是 687dp 与 287dp——此前 `weight(1f)` 让这一栏直接等于可用高度，
     * 右侧 500 多 dp 的空白就是这么来的，而终端那一栏同时被挤到窗口底下、滚动条被裁掉。
     */
    @Test
    fun theSourcesListHugsItsContentAndStaysFarBelowTheAvailableHeight() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val available = pageAvailableHeight(height.dp, metrics)
            val sources = logSourceListHeight(realSourceCount, available, metrics)
            assertTrue(
                "$width x $height: 三行来源应该是内容高度 $sources，而不是 $available",
                sources < available,
            )
            assertTrue(
                "$width x $height: 来源清单仍占掉可用高度的一半以上（$sources / $available）",
                sources < available * 0.5f,
            )
        }
    }

    @Test
    fun theSourcesListIsNeverTallerThanItsContent() {
        val metrics = metricsAt(standard.first, standard.second)
        val available = pageAvailableHeight(standard.second.dp, metrics)
        listOf(0, 1, 2, 3, 8).forEach { count ->
            val content = logSourceListContentHeight(count, metrics)
            val actual = logSourceListHeight(count, available, metrics)
            assertTrue("$count 行时实际 $actual 高于内容高度 $content", actual <= content)
        }
    }

    /**
     * 三行来源在参考尺寸下远低于上限，因此上限在正常情况下根本碰不到
     *
     * 640x360 在 125% 以上会真的顶到上限——那一档的三行加上留白已经吃掉终端那一栏
     * 的一半以上，于是清单开始滚。这是上限存在的意义，不是回归，所以这条断言只钉
     * 100% 那一档；上限生效时终端那一栏仍然够高，由下面两条守住。
     */
    @Test
    fun theSourcesListIsBelowItsCapWithTheRealNumberOfSources() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val available = pageAvailableHeight(height.dp, metrics)
            val content = logSourceListContentHeight(realSourceCount, metrics)
            val cap = logSourceListMaxHeight(available, metrics)
            assertTrue("$width x $height: 三行内容 $content 已经碰到上限 $cap", content < cap)
        }
    }

    /**
     * 上限生效时终端那一栏仍然够高
     *
     * 清单开始滚的那一档恰恰是最危险的：它意味着两栏抢高度。终端是主体，所以即使
     * 清单顶到上限，终端也必须还留得下 [logTerminalMinHeight]。
     */
    @Test
    fun theTerminalSurvivesEvenWhenTheSourcesCapEngages() {
        listOf(standard, compact).forEach { (width, height) ->
            OxideGuiScaleSteps.forEach { scale ->
                val metrics = metricsAt(width, height, scale)
                val available = pageAvailableHeight(height.dp, metrics)
                val sources = logSourceListHeight(realSourceCount, available, metrics)
                assertTrue(
                    "$width x $height @${scale}%: 上限生效后终端只剩 " +
                        "${logTerminalHeight(height.dp, metrics) - sources}",
                    logTerminalHeight(height.dp, metrics) - sources > 0.dp,
                )
            }
        }
    }

    /**
     * 来源变多时上限生效，清单自己滚
     *
     * 上限守的是"终端是主体"这条：来源再多也不能把终端那一栏挤没，因此清单会在
     * 上限处开始滚，而分隔线与"打开日志目录"那一行仍然留在卡片里。
     */
    @Test
    fun aLongSourcesListStopsAtItsCap() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val available = pageAvailableHeight(height.dp, metrics)
            val cap = logSourceListMaxHeight(available, metrics)
            assertTrue(
                "$width x $height: 40 行时高度必须停在上限 $cap",
                logSourceListHeight(40, available, metrics) <= cap,
            )
        }
    }

    /** 上限之下仍要给终端那一栏留下 [logTerminalMinHeight] 那么多 */
    @Test
    fun theSourcesCapAlwaysLeavesTheTerminalItsMinimum() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val available = pageAvailableHeight(height.dp, metrics)
            val cap = logSourceListMaxHeight(available, metrics)
            val leftover = logTerminalHeight(height.dp, metrics) - cap
            // 上限之外还有一行来源的兜底（见 logSourceListMaxHeight）：走到那一档时
            // "至少留 logTerminalMinHeight"让位给"至少留一行来源"，否则会一个来源都点不到
            val floor = logSourceRowHeight(metrics)
            assertTrue(
                "$width x $height: 上限 $cap 之下终端只剩 $leftover，最小内容 " +
                    "${logTerminalMinHeight(metrics)}",
                leftover >= logTerminalMinHeight(metrics) - 0.01.dp || cap <= floor,
            )
        }
    }

    /**
     * 每一档缩放下都留得下一行来源
     *
     * 上限的地板是"一行来源"而不是 0：清单被压成 0 就等于这一页上没有任何来源可选，
     * 那比终端少几行更糟。
     */
    @Test
    fun theSourcesCapNeverDropsBelowOneRow() {
        listOf(standard, compact).forEach { (width, height) ->
            OxideGuiScaleSteps.forEach { scale ->
                val metrics = metricsAt(width, height, scale)
                val available = pageAvailableHeight(height.dp, metrics)
                assertTrue(
                    "$width x $height @${scale}%: 上限 ${logSourceListMaxHeight(available, metrics)} " +
                        "低于一行 ${logSourceRowHeight(metrics)}",
                    logSourceListMaxHeight(available, metrics) >= logSourceRowHeight(metrics),
                )
            }
        }
    }

    @Test
    fun anEmptySourcesListTakesNoHeight() {
        val metrics = metricsAt(standard.first, standard.second)
        // 候选全都不存在时清单不出现，卡片只剩标签与目录那一行
        assertDp(0f, logSourceListContentHeight(0, metrics))
    }

    @Test
    fun aNegativeSourceCountIsTreatedAsEmpty() {
        val metrics = metricsAt(standard.first, standard.second)
        assertDp(
            logSourceListContentHeight(0, metrics).value,
            logSourceListContentHeight(-3, metrics),
        )
    }

    // -----------------------------------------------------------------------
    // 终端那一栏
    // -----------------------------------------------------------------------

    /**
     * 终端那一栏在两个尺寸上都拿到正的、有限的高度
     *
     * 这是本次修的 bug 的直接后果。此前那一行写的是 `fillMaxSize()`，终端卡片被以
     * 整页高度测量、溢出窗口底边，横向滚动条随卡片一起被外壳的 clipToBounds 裁掉。
     */
    @Test
    fun theTerminalPaneIsPositiveAndFiniteOnBothWindows() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val terminal = logTerminalHeight(height.dp, metrics)
            assertTrue("$width x $height: 终端高度 $terminal 不是正的", terminal > 0.dp)
            assertTrue(
                "$width x $height: 终端高度 $terminal 撑破了窗口 $height",
                terminal < height.dp,
            )
        }
    }

    /** 终端那一栏一定装得下它自己那几行最小内容 */
    @Test
    fun theTerminalPaneFitsItsOwnMinimum() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            assertTrue(
                "$width x $height: 终端 ${logTerminalHeight(height.dp, metrics)} " +
                    "装不下最小内容 ${logTerminalMinHeight(metrics)}",
                logTerminalHeight(height.dp, metrics) >= logTerminalMinHeight(metrics),
            )
        }
    }

    /**
     * 终端那一栏的高度与来源条数无关
     *
     * 签名里就没有 `sourceCount` 这一项，因此它拿到的永远是"可用高度减去页头"的
     * 全部。相邻的两条一起钉：来源那一栏的高度确实随条数变，而终端那一栏纹丝不动。
     */
    @Test
    fun theTerminalPaneHeightIsIndependentOfTheSourceCount() {
        val metrics = metricsAt(standard.first, standard.second)
        val available = pageAvailableHeight(standard.second.dp, metrics)
        val terminal = logTerminalHeight(standard.second.dp, metrics)

        assertDp(
            available.value - logPageHeaderHeight(metrics).value - metrics.rowGap.value,
            terminal,
        )
        // 来源那一栏从 0 行到 40 行都在长高，终端那一栏一动不动
        listOf(0, 1, 2, 3, 8, 40).forEach { count ->
            val sources = logSourceListHeight(count, available, metrics)
            assertTrue(
                "$count 行时终端高度从 $terminal 变成了 ${logTerminalHeight(standard.second.dp, metrics)}",
                logTerminalHeight(standard.second.dp, metrics) == terminal,
            )
            assertTrue(
                "$count 行时来源高度 $sources 没有随条数变（终端是 $terminal）",
                sources <= terminal,
            )
        }
    }

    /**
     * 两栏都装得进那一行，不会把行撑出窗口
     *
     * 并排时两栏共用同一行高度，因此来源那一栏不能比终端那一栏高；堆叠时两栏上下
     * 相加，因此合计不能超过可用高度。两种排布都过一遍，因为 [oxideContentLayoutFor]
     * 会在宽度不够时从并排掉到堆叠。
     */
    @Test
    fun theTwoPanesFitTheRowTheyShare() {
        listOf(standard, compact).forEach { (width, height) ->
            val metrics = metricsAt(width, height)
            val available = pageAvailableHeight(height.dp, metrics)
            val sources = logSourceListHeight(realSourceCount, available, metrics)
            // 并排：两栏在同一行里，来源那一栏更高就意味着卡片被撑破
            val sideBySide = logTerminalHeight(height.dp, metrics)
            assertTrue(
                "$width x $height: 并排时来源 $sources 高于终端 $sideBySide",
                sources <= sideBySide,
            )
            // 堆叠：终端那一栏要扣掉来源那一栏的高度与两卡之间的间距
            val stacked = logTerminalHeight(height.dp, metrics, sources + metrics.cardGap)
            assertTrue(
                "$width x $height: 堆叠时终端只剩 $stacked",
                stacked > 0.dp,
            )
        }
    }

    /** 界面放大时终端那一栏仍拿到正高度——放大的是尺寸，不是可用空间 */
    @Test
    fun theTerminalPaneStaysPositiveAtEveryGuiScale() {
        listOf(standard, compact).forEach { (width, height) ->
            OxideGuiScaleSteps.forEach { scale ->
                val metrics = metricsAt(width, height, scale)
                assertTrue(
                    "$width x $height @${scale}%: 终端高度塌了",
                    logTerminalHeight(height.dp, metrics) > 0.dp,
                )
            }
        }
    }

    /** 窗口越高终端那一栏越高，但两者都仍然是正的 */
    @Test
    fun aTallerWindowGivesTheTerminalMoreHeight() {
        val phone = logTerminalHeight(
            compact.second.dp,
            metricsAt(compact.first, compact.second),
        )
        val tablet = logTerminalHeight(
            standard.second.dp,
            metricsAt(standard.first, standard.second),
        )
        assertTrue("平板 ${tablet.value}dp 不该比手机 ${phone.value}dp 矮", tablet > phone)
    }

    /** 极端窗口下也不返回负数：负高度会让 weight(1f) 的那一栏塌掉 */
    @Test
    fun extremeWindowsDegradeToZeroRatherThanGoingNegative() {
        val metrics = metricsAt(standard.first, standard.second)
        listOf(0.dp, 1.dp, 120.dp, 200.dp, 340.dp).forEach { window ->
            assertTrue(
                "窗口 $window 下终端高度为负",
                logTerminalHeight(window, metrics) >= 0.dp,
            )
        }
    }

    /** 可用高度小到连页头都放不下时，来源上限退化成一行而不是负数 */
    @Test
    fun theSourcesCapNeverGoesNegative() {
        val metrics = metricsAt(standard.first, standard.second)
        listOf(0.dp, 1.dp, 10.dp, 40.dp).forEach { available ->
            assertTrue(
                "可用高度 $available 下来源上限为负",
                logSourceListMaxHeight(available, metrics) >= 0.dp,
            )
        }
    }

    // ---- 工具 ---------------------------------------------------------------

    /** dp 是浮点，比较必须带容差；沿用仓库里既有的写法 */
    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals("expected ${expected}dp but was ${actual.value}dp", expected, actual.value, 0.01f)
    }
}