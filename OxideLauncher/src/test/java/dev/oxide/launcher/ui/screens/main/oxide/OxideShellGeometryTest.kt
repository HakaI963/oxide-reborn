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
import dev.oxide.launcher.ui.theme.Oxide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 侧栏与页面几何的单测
 *
 * 这里的每个期望值都直接来自参考稿 `/tmp/opencode/ref/ref.html` 的 CSS：
 * 侧栏 `--sidebar:184px` / 164px / 145px、`padding:20px 15px 15px`、
 * `border-right:1px`、`.brandSlot{height:34px;margin:0 4px 36px}`（≤900px 时 34px）、
 * `.nav{gap:3px}`、`.navButton{height:38px;padding:0 10px}`、
 * `.navRail{height:38px}` + `translate3d(0,index*41px,0)`、
 * `.topbar{height:46px;padding:0 27px}`、`.page{padding:10px 27px 17px}`。
 */
class OxideShellGeometryTest {

    // ---- 落档 ---------------------------------------------------------------

    @Test
    fun widthClassesFollowTheReferenceBreakpoints() {
        // @media(max-width:900px) / (max-width:1120px)
        assertEquals(OxideWidthClass.Compact, oxideWidthClassFor(480))
        assertEquals(OxideWidthClass.Compact, oxideWidthClassFor(640))
        assertEquals(OxideWidthClass.Compact, oxideWidthClassFor(900))
        assertEquals(OxideWidthClass.Medium, oxideWidthClassFor(901))
        assertEquals(OxideWidthClass.Medium, oxideWidthClassFor(1120))
        assertEquals(OxideWidthClass.Expanded, oxideWidthClassFor(1121))
        assertEquals(OxideWidthClass.Expanded, oxideWidthClassFor(1280))
        assertEquals(OxideWidthClass.Large, oxideWidthClassFor(1281))
        assertEquals(OxideWidthClass.Large, oxideWidthClassFor(2560))
    }

    @Test
    fun sidebarWidthsMatchTheReference() {
        assertDp(145f, oxideSidebarWidthFor(OxideWidthClass.Compact))
        assertDp(164f, oxideSidebarWidthFor(OxideWidthClass.Medium))
        assertDp(184f, oxideSidebarWidthFor(OxideWidthClass.Expanded))
        assertDp(184f, oxideSidebarWidthFor(OxideWidthClass.Large))
    }

    /**
     * 侧栏内边距随宽度收窄，但只有两档：
     * 默认 15px，参考稿在 ≤1120px 处改成 12px。
     */
    @Test
    fun sidebarPaddingMatchesTheReference() {
        assertDp(12f, oxideSidebarPaddingHFor(OxideWidthClass.Compact))
        assertDp(12f, oxideSidebarPaddingHFor(OxideWidthClass.Medium))
        assertDp(15f, oxideSidebarPaddingHFor(OxideWidthClass.Expanded))
        assertDp(15f, oxideSidebarPaddingHFor(OxideWidthClass.Large))
    }

    @Test
    fun brandGapIsNarrowerOnlyOnTheCompactBand() {
        assertDp(34f, oxideBrandGapFor(OxideWidthClass.Compact))
        assertDp(36f, oxideBrandGapFor(OxideWidthClass.Medium))
        assertDp(36f, oxideBrandGapFor(OxideWidthClass.Expanded))
        assertDp(36f, oxideBrandGapFor(OxideWidthClass.Large))
    }

    @Test
    fun pagePaddingMatchesTheReference() {
        assertDp(21f, oxidePagePaddingHFor(OxideWidthClass.Compact))
        assertDp(21f, oxidePagePaddingHFor(OxideWidthClass.Medium))
        assertDp(27f, oxidePagePaddingHFor(OxideWidthClass.Expanded))
        assertDp(27f, oxidePagePaddingHFor(OxideWidthClass.Large))
        // 上下留白始终是 10/17
        val wide = oxideMetricsFor(1280, 760)
        assertDp(10f, wide.pagePaddingV)
        assertDp(17f, Oxide.PagePaddingB)
    }

    // ---- 侧栏列宽 -----------------------------------------------------------

    /**
     * 导航列宽度要把 1px 分隔线算进侧栏宽度里（参考稿 `box-sizing:border-box`）
     */
    @Test
    fun navColumnWidthIncludesTheOnePixelBorder() {
        // 184 - 15 - 15 - 1
        assertDp(153f, oxideMetricsFor(1280, 760).sidebarNavWidth)
        // 164 - 12 - 12 - 1
        assertDp(139f, oxideMetricsFor(1000, 700).sidebarNavWidth)
        // 145 - 12 - 12 - 1
        assertDp(120f, oxideMetricsFor(800, 480).sidebarNavWidth)
    }

    /** 品牌槽 = 导航列再左右内缩 4px，参考稿 `margin:0 4px` */
    @Test
    fun brandSlotIsInsetFourDp() {
        assertDp(145f, oxideMetricsFor(1280, 760).brandSlotWidth)
        assertDp(131f, oxideMetricsFor(1000, 700).brandSlotWidth)
        assertDp(112f, oxideMetricsFor(800, 480).brandSlotWidth)
    }

    // ---- 导航列 -------------------------------------------------------------

    /** 参考稿 `.nav{gap:3px}` + `.navButton{height:38px}` = 41px 一步 */
    @Test
    fun navStepIsFortyOne() {
        assertDp(41f, oxideNavStep(38.dp))
        assertDp(41f, oxideMetricsFor(1280, 760).navStep)
    }

    /** 参考稿 `navRail.style.transform = translate3d(0, index*41px, 0)` */
    @Test
    fun railOffsetStepsThroughEveryItem() {
        val metrics = oxideMetricsFor(1280, 760)
        assertDp(0f, metrics.navRailOffset(0))
        assertDp(41f, metrics.navRailOffset(1))
        assertDp(82f, metrics.navRailOffset(2))
        assertDp(123f, metrics.navRailOffset(3))
        // 与参考稿里写死的 41px 步进一致
        assertDp(41f * 2, oxideNavRailOffset(2, 38.dp))
        // 越界索引不能把指示条推到轨道外面
        assertDp(0f, oxideNavRailOffset(-1, 38.dp))
    }

    /** 轨道高度 = 4 项 + 3 个 3px 间隙 */
    @Test
    fun navTravelCoversEveryItem() {
        assertDp(164f, oxideNavTravel(4, 38.dp))
        assertDp(164f, oxideMetricsFor(1280, 760).navTravel)
        assertEquals(4, OxideNavItemCount)
    }

    // ---- logo 落点 ----------------------------------------------------------

    /**
     * 静止缩放必须和开场动画完全一致，否则 logo 落地时会跳一截。
     *
     * 公式与参考稿一致：目标宽度 = min(槽宽, 122px)，缩放夹在 0.28~0.42。
     */
    @Test
    fun brandLogoScaleMatchesTheIntroDestination() {
        val expected = 122f / BrandLogoStartWidthDp
        assertEquals(expected, oxideBrandLogoScale(145.dp), 0.0001f)
        assertEquals(expected, oxideBrandLogoScale(122.dp), 0.0001f)
        // 再宽的槽也被 122px 封顶
        assertEquals(expected, oxideBrandLogoScale(400.dp), 0.0001f)
        // 特别窄的槽不会缩到 0.28 以下
        assertEquals(Oxide.Motion.IntroScaleMin, oxideBrandLogoScale(40.dp), 0.0001f)
        assertEquals(Oxide.Motion.IntroScaleMin, oxideBrandLogoScale(0.dp), 0.0001f)

        // 每一档侧栏都能给出合法缩放
        for (width in intArrayOf(480, 640, 800, 901, 1121, 1281, 2560)) {
            val scale = oxideMetricsFor(width, 700).brandLogoScale
            assertTrue("width=$width scale=$scale", scale >= Oxide.Motion.IntroScaleMin)
            assertTrue("width=$width scale=$scale", scale <= Oxide.Motion.IntroScaleMax)
        }
    }

    // ---- 每一档的完整尺寸 ---------------------------------------------------

    @Test
    fun compactBandMatchesTheReference() {
        val m = oxideMetricsFor(640, 360)
        assertEquals(OxideWidthClass.Compact, m.widthClass)
        assertDp(145f, m.sidebarWidth)
        assertDp(12f, m.sidebarPaddingH)
        assertDp(34f, m.brandGap)
        assertDp(21f, m.pagePaddingH)
        assertDp(46f, m.topBarHeight)
        assertDp(38f, m.navItemHeight)
        assertDp(41f, m.navStep)
        assertEquals(2, m.maxCardColumns)
        // 参考稿没有缩字号这一档，宽度不够时改的是列数
        assertEquals(1f, m.density, 0.0001f)
        assertEquals(8f, m.scaled(Oxide.Type.Body.fontSize.value), 0.0001f)
        assertEquals(29f, m.heroTitleDp, 0.0001f)
        // 640 宽 → 内容区 453，两列而不是一个撑满的大卡片
        assertEquals(2, m.gridColumns(453.dp))
    }

    @Test
    fun mediumBandMatchesTheReference() {
        val m = oxideMetricsFor(1000, 700)
        assertEquals(OxideWidthClass.Medium, m.widthClass)
        assertDp(164f, m.sidebarWidth)
        assertDp(12f, m.sidebarPaddingH)
        assertDp(36f, m.brandGap)
        assertDp(21f, m.pagePaddingH)
        assertEquals(32f, m.heroTitleDp, 0.0001f)
        assertEquals(3, m.maxCardColumns)
        // 1000 宽 → 内容区 794，三列
        assertEquals(3, m.gridColumns(794.dp))
    }

    @Test
    fun expandedBandMatchesTheReference() {
        val m = oxideMetricsFor(1280, 760)
        assertEquals(OxideWidthClass.Expanded, m.widthClass)
        assertDp(184f, m.sidebarWidth)
        assertDp(15f, m.sidebarPaddingH)
        assertDp(36f, m.brandGap)
        assertDp(27f, m.pagePaddingH)
        assertDp(153f, m.sidebarNavWidth)
        assertEquals(36f, m.heroTitleDp, 0.0001f)
        assertEquals(3, m.maxCardColumns)
        // 1280 宽 → 内容区 1042，三列；列数不会因为卡片最小宽度过大而掉到两列
        assertEquals(3, m.gridColumns(1042.dp))
    }

    @Test
    fun largeBandMatchesTheReference() {
        val m = oxideMetricsFor(2560, 1600)
        assertEquals(OxideWidthClass.Large, m.widthClass)
        assertDp(184f, m.sidebarWidth)
        assertDp(15f, m.sidebarPaddingH)
        assertDp(27f, m.pagePaddingH)
        assertEquals(42f, m.heroTitleDp, 0.0001f)
        // 参考稿实例网格最多三列，再宽也不铺成四列
        assertEquals(3, m.maxCardColumns)
        assertEquals(3, m.gridColumns(2322.dp))
        assertDp(520f, m.drawerWidth)
    }

    // ---- 抽屉与矮屏 ---------------------------------------------------------

    /** 参考稿 `.drawer{width:min(520px,44vw)}` */
    @Test
    fun drawerWidthMatchesTheReference() {
        assertDp(520f, oxideMetricsFor(1280, 760).drawerWidth)
        assertDp(440f, oxideMetricsFor(1000, 700).drawerWidth)
        // 44vw 小于 280dp 时保底 280dp，不会被压扁
        assertDp(280f, oxideMetricsFor(600, 400).drawerWidth)
    }

    /**
     * 常规横屏一律用参考稿的 38dp 项高；只有真的装不下时才压缩
     */
    @Test
    fun onlyVeryShortScreensShrink() {
        assertDp(38f, oxideMetricsFor(1280, 380).navItemHeight)
        assertDp(38f, oxideMetricsFor(1280, 340).navItemHeight)
        assertDp(38f, oxideMetricsFor(640, OxideShortScreenHeight).navItemHeight)

        val short = oxideMetricsFor(640, 300)
        assertDp(32f, short.navItemHeight)
        assertDp(35f, short.navStep)
        assertDp(140f, short.navTravel)
        assertDp(6f, short.pagePaddingV)
    }

    /** 侧栏在常规横屏上必须装得下：品牌槽 + 间距 + 导航列 + 底部块 + 上下内边距 */
    @Test
    fun sidebarFitsAtTheShortScreenThreshold() {
        val m = oxideMetricsFor(640, OxideShortScreenHeight)
        val needed = Oxide.SidebarPaddingTop + Oxide.BrandSlotHeight + m.brandGap +
            m.navTravel + Oxide.SidebarFooterPaddingTop + Oxide.SidebarBorder +
            14.4.dp * 3 + Oxide.SidebarPaddingBottom
        assertTrue("needed=$needed", needed.value <= OxideShortScreenHeight)
    }

    // ---- 网格边界 -----------------------------------------------------------

    @Test
    fun gridColumnsNeverExceedsTheCapOrDropsBelowOne() {
        val m = oxideMetricsFor(1280, 760)
        assertEquals(1, m.gridColumns(0.dp))
        assertEquals(1, m.gridColumns(120.dp))
        assertEquals(2, m.gridColumns(600.dp))
        assertEquals(3, m.gridColumns(1042.dp))
        assertEquals(3, m.gridColumns(4000.dp))
    }

    // ---- 工具 ---------------------------------------------------------------

    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals("expected ${expected}dp but was ${actual.value}dp", expected, actual.value, 0.01f)
    }
}