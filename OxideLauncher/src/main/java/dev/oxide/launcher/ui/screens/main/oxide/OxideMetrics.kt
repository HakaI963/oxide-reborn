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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 横屏下的尺寸档位
 *
 * 界面不写死任何一个分辨率，而是按**可用宽度**落档。所有页面只从 [OxideMetrics] 取尺寸，
 * 因此同一份代码在小手机横屏和大平板横屏上都能保持相同的视觉比例，
 * 而不会出现裁切、重叠或者大得离谱的控件。
 */
enum class OxideWidthClass {
    /** 小手机横屏，典型可用宽度 560-660dp */
    Compact,

    /** 大手机横屏 / 小平板横屏，660-900dp */
    Medium,

    /** 平板横屏，900-1280dp */
    Expanded,

    /** 大平板横屏，1280dp 以上 */
    Large,
}

/**
 * 一组已经算好的界面尺寸
 *
 * 全部由可用宽高推导，页面不得再自行计算像素或写死 dp。
 */
@Immutable
data class OxideMetrics(
    val widthClass: OxideWidthClass,
    val sidebarWidth: Dp,
    val pagePaddingH: Dp,
    val pagePaddingV: Dp,
    val sectionGap: Dp,
    val cardGap: Dp,
    /** 网格里每一项的最小宽度，实际列数由可用宽度除以它再夹紧 */
    val cardMinWidth: Dp,
    val minCardColumns: Int,
    val maxCardColumns: Int,
    val drawerWidth: Dp,
    val topBarHeight: Dp,
    val navItemHeight: Dp,
    /** 紧凑档下整体缩放，避免小屏上溢出 */
    val density: Float,
) {
    /** 导航项的纵坐标步进：项高 + 间距，选中指示条按它逐格移动 */
    val navStep: Dp get() = navItemHeight + Oxide.NavGap

    /**
     * 按实际内容宽度算出网格列数
     *
     * 用"内容区宽度 / 卡片最小宽度"而不是固定列数，这样窗口被切分、
     * 折叠屏展开或外接显示器接入时列数都会自动跟着变。
     */
    fun gridColumns(contentWidth: Dp): Int {
        if (contentWidth <= 0.dp) return minCardColumns
        val fit = ((contentWidth + cardGap) / (cardMinWidth + cardGap)).toInt()
        return fit.coerceIn(minCardColumns, maxCardColumns)
    }

    /** Hero 标题字号随宽度缩放，比例关系与参考稿一致 */
    val heroTitleDp: Float
        get() = when (widthClass) {
            OxideWidthClass.Compact -> 26f
            OxideWidthClass.Medium -> 31f
            OxideWidthClass.Expanded -> 36f
            OxideWidthClass.Large -> 42f
        }

    /** 紧凑档下正文等小字号也一起缩放，保持层级关系 */
    fun scaled(base: Float): Float = base * density
}

/**
 * 由当前可用尺寸算出界面尺寸
 *
 * 读 [LocalConfiguration] 而不是屏幕物理尺寸：分屏、多窗口和折叠屏展开时
 * 可用区域会小于整块屏幕，用物理尺寸会导致内容被裁掉。
 */
@Composable
fun rememberOxideMetrics(): OxideMetrics {
    val configuration = LocalConfiguration.current

    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp

        val widthClass = when {
            widthDp < 660 -> OxideWidthClass.Compact
            widthDp < 900 -> OxideWidthClass.Medium
            widthDp < 1280 -> OxideWidthClass.Expanded
            else -> OxideWidthClass.Large
        }

        // 横屏可选高度很吃紧，矮屏额外压缩纵向尺寸，保证首屏能看到主要内容
        val shortScreen = heightDp < 380

        OxideMetrics(
            widthClass = widthClass,
            sidebarWidth = when (widthClass) {
                OxideWidthClass.Compact -> Oxide.SidebarWidthCompact
                OxideWidthClass.Medium -> Oxide.SidebarWidthMedium
                OxideWidthClass.Expanded, OxideWidthClass.Large -> Oxide.SidebarWidth
            },
            pagePaddingH = when (widthClass) {
                OxideWidthClass.Compact -> 16.dp
                OxideWidthClass.Medium -> 22.dp
                OxideWidthClass.Expanded -> Oxide.PagePaddingH
                OxideWidthClass.Large -> 32.dp
            },
            pagePaddingV = if (shortScreen) 6.dp else Oxide.PagePaddingT,
            sectionGap = when (widthClass) {
                OxideWidthClass.Compact -> 10.dp
                OxideWidthClass.Medium -> 13.dp
                else -> 16.dp
            },
            cardGap = when (widthClass) {
                OxideWidthClass.Compact -> 9.dp
                OxideWidthClass.Medium -> 11.dp
                else -> 13.dp
            },
            cardMinWidth = when (widthClass) {
                OxideWidthClass.Compact -> 250.dp
                OxideWidthClass.Medium -> 285.dp
                OxideWidthClass.Expanded -> 320.dp
                OxideWidthClass.Large -> 360.dp
            },
            // 下限永远是一行；上限只是上限，实际列数由 gridColumns 按内容宽度决定
            minCardColumns = 1,
            maxCardColumns = when (widthClass) {
                OxideWidthClass.Compact -> 2
                OxideWidthClass.Medium, OxideWidthClass.Expanded -> 3
                OxideWidthClass.Large -> 4
            },
            // 参考稿是 min(520px, 44vw)，这里按 dp 等价换算并保留同样的下限
            drawerWidth = minOf(520.dp, (widthDp * 0.44f).dp).coerceAtLeast(280.dp),
            topBarHeight = Oxide.TopBar,
            navItemHeight = if (shortScreen) 32.dp else Oxide.NavItemHeight,
            density = if (widthClass == OxideWidthClass.Compact) 0.94f else 1f,
        )
    }
}
