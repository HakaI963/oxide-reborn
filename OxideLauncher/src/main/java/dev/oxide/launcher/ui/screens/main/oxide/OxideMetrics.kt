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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle

/**
 * 参考稿的断点，单位就是它自己的 CSS px；本项目按 1px = 1dp 直译
 *
 * 参考稿只有两条媒体查询，它们同时决定侧栏宽度、侧栏内边距、品牌槽下间距
 * 和页面左右留白，因此这里把它们收成三个常量，**所有**几何都从这三条推出来，
 * 侧栏、内容列与页面留白永远是同一套比例。
 */
object OxideBreakpoints {
    /** `@media(max-width:900px)`：侧栏 145px、内边距 12px、品牌槽下间距 34px */
    const val CompactMax = 900

    /** `@media(max-width:1120px)`：侧栏 164px、内边距 12px、页面留白 21px */
    const val MediumMax = 1120

    /** 再往上就是默认档：侧栏 184px、内边距 15px、页面留白 27px */
    const val ExpandedMax = 1280
}

/**
 * 横屏下的尺寸档位
 *
 * 界面不写死任何一个分辨率，而是按**可用宽度**落档。所有页面只从 [OxideMetrics] 取尺寸，
 * 因此同一份代码在小手机横屏和大平板横屏上都能保持相同的视觉比例，
 * 而不会出现裁切、重叠或者大得离谱的控件。
 */
enum class OxideWidthClass {
    /** 参考稿 ≤900px：侧栏 145px */
    Compact,

    /** 参考稿 900-1120px：侧栏 164px */
    Medium,

    /** 参考稿 1120-1280px：侧栏 184px */
    Expanded,

    /** 参考稿 1280px 以上：侧栏 184px */
    Large,
}

/** 侧栏里共有几个主页面，选中指示条按它逐格移动 */
val OxideNavItemCount: Int = OxidePage.entries.size

/**
 * 按可用宽度落档
 *
 * 断点与参考稿的媒体查询一一对应，档位名字也直接对应参考稿的三套侧栏几何。
 */
fun oxideWidthClassFor(widthDp: Int): OxideWidthClass = when {
    widthDp <= OxideBreakpoints.CompactMax -> OxideWidthClass.Compact
    widthDp <= OxideBreakpoints.MediumMax -> OxideWidthClass.Medium
    widthDp <= OxideBreakpoints.ExpandedMax -> OxideWidthClass.Expanded
    else -> OxideWidthClass.Large
}

/** 侧栏宽度：参考稿 `--sidebar` 的三档 */
fun oxideSidebarWidthFor(widthClass: OxideWidthClass): Dp = when (widthClass) {
    OxideWidthClass.Compact -> Oxide.SidebarWidthCompact
    OxideWidthClass.Medium -> Oxide.SidebarWidthMedium
    OxideWidthClass.Expanded, OxideWidthClass.Large -> Oxide.SidebarWidth
}

/**
 * 侧栏内边距
 *
 * 默认 15px，参考稿在 ≤1120px 处把它收到 12px。这 3px 直接决定导航列宽度，
 * 所以它必须和侧栏宽度一起落档，不能写死。
 */
fun oxideSidebarPaddingHFor(widthClass: OxideWidthClass): Dp =
    if (widthClass == OxideWidthClass.Expanded || widthClass == OxideWidthClass.Large) {
        Oxide.SidebarPaddingH
    } else {
        Oxide.SidebarPaddingHNarrow
    }

/** 品牌槽到第一项导航的间距：默认 36px，≤900px 收到 34px */
fun oxideBrandGapFor(widthClass: OxideWidthClass): Dp =
    if (widthClass == OxideWidthClass.Compact) Oxide.BrandGapNarrow else Oxide.BrandGap

/** 页面左右留白：默认 27px，≤1120px 收到 21px */
fun oxidePagePaddingHFor(widthClass: OxideWidthClass): Dp =
    if (widthClass == OxideWidthClass.Expanded || widthClass == OxideWidthClass.Large) {
        Oxide.PagePaddingH
    } else {
        Oxide.PagePaddingHNarrow
    }

/**
 * 矮屏档的导航项高度：装不下 [Oxide.NavItemHeight] 时整体压缩到这一档
 *
 * 常规档仍然用 [Oxide.NavItemHeight] 本身，不在这里另抄一份，
 * 否则参考稿改了项高而这一档还留在旧值上。
 */
val OxideShortScreenNavItemHeight: Dp = 32.dp

/** 导航项高度的绝对下限，再挤也不能比它更矮，否则项内会空得没有内容 */
val OxideMinNavItemHeight: Dp = 22.dp

/** 侧栏落款每一行的高度，与 [OxideShell] 里那三行 14sp 对应 */
val OxideSidebarFooterLineHeight: Dp = 14.4.dp

/** 侧栏落款有几行 */
const val OxideSidebarFooterLines: Int = 3

/**
 * 抽屉宽度相对屏幕的上限
 *
 * 参考稿是 44vw，放大 1.5 倍后正好是 66vw——所以把它定成抽屉的硬上限，
 * 放大到极限时抽屉也只盖住三分之二屏，侧栏和内容区还留得下。
 */
const val OxideDrawerMaxWidthFraction: Float = 0.44f * 1.5f

/**
 * 侧栏里与导航列无关的纵向空间
 *
 * 侧栏的上下内边距、品牌槽与底部落款都走 [Oxide] 里的固定值（它们是 logo 动画
 * 的锚点，缩放它们会让开场落点跳动），所以导航列能用的是"总高减去这一块"。
 */
private fun oxideSidebarFixedHeight(brandGap: Dp): Dp =
    Oxide.SidebarPaddingTop + Oxide.BrandSlotHeight + brandGap +
        Oxide.SidebarFooterPaddingTop + Oxide.SidebarBorder +
        OxideSidebarFooterLineHeight * OxideSidebarFooterLines +
        Oxide.SidebarPaddingBottom

/**
 * 导航项文字的行高，按缩放**系数**给出，纯函数
 *
 * 与 [Oxide.Type.Nav] 的 14sp 行高对应，同样乘一次界面缩放。放在这里而不是直接在
 * 界面里读字体，是因为项高与文字必须用同一个系数算，否则两者会在某一档上错开。
 * 参数是系数而不是百分比，因为 [OxideMetrics] 那一侧手上已经是系数了。
 *
 * 返回值是 dp 而不是排版单位（TextUnit）：这里给 [oxideNavItemHeightFor] 当高度下限用，
 * 而那条链路上全是 dp，比较和取整都必须在同一个单位里。按参考稿"1px = 1dp"的
 * 直译约定，取行高的数值当 dp 用；`Oxide.Type.Nav` 是 sp，这里不掺系统
 * 字号缩放，避免项高下限被用户字体设置带偏。
 */
fun oxideNavTextLineHeight(factor: Float): Dp =
    oxideScaledTextStyle(NAV_TEXT_BASE, factor).lineHeight.value.dp

/** 导航项文字的基准行高，与 [Oxide.Type.Nav] 一致；单独存一份是为了纯函数化 */
private val NAV_TEXT_BASE: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)

/**
 * 导航项高度：先按参考稿的档位定，再看缩放后的导航列还塞不塞得下
 *
 * 放大界面会同时放大导航项，而侧栏的上下内边距与底部落款是固定值，所以 150% 在
 * 小横屏上会真的装不下。这里把导航项压回"刚好装得下"的高度——宁可导航项矮一点，
 * 也不能让底部块被挤出屏幕。下限取 [OxideMinNavItemHeight] 与 [oxideNavTextLineHeight]
 * 里更高的那个，所以最极端的一档也只是更密的导航，项里的字仍然读得出来。
 *
 * 纯函数，参数只有尺寸与缩放，因此可以直接单测。
 */
fun oxideNavItemHeightFor(
    heightDp: Int,
    widthClass: OxideWidthClass,
    guiScalePercent: Int = OxideGuiScaleDefaultPercent,
): Dp {
    val scale = oxideGuiScaleFactor(guiScalePercent)
    val wanted =
        (if (heightDp < OxideShortScreenHeight) OxideShortScreenNavItemHeight else Oxide.NavItemHeight) * scale
    val gap = Oxide.NavGap * scale
    val budget = heightDp.dp - oxideSidebarFixedHeight(oxideBrandGapFor(widthClass) * scale)
    val affordable = budget / OxideNavItemCount - gap
    // 下限取"绝对下限"与"这一行文字"里更高的那个：再挤也不能挤到字自己溢出
    val floor = maxOf(OxideMinNavItemHeight, oxideNavTextLineHeight(scale))

    return minOf(wanted, affordable).coerceAtLeast(floor)
}

/** 导航项的纵坐标步进：项高 + 间距，选中指示条按它逐格移动 */
fun oxideNavStep(itemHeight: Dp, gap: Dp = Oxide.NavGap): Dp = itemHeight + gap

/** 第 [index] 项相对导航列顶部的偏移，选中指示条就停在这里 */
fun oxideNavRailOffset(index: Int, itemHeight: Dp, gap: Dp = Oxide.NavGap): Dp =
    oxideNavStep(itemHeight, gap) * index.coerceAtLeast(0)

/** 整个导航列的高度，用来给指示条当轨道 */
fun oxideNavTravel(itemCount: Int, itemHeight: Dp, gap: Dp = Oxide.NavGap): Dp =
    oxideNavStep(itemHeight, gap) * itemCount.coerceAtLeast(0)

/**
 * 侧栏里 logo 的静止缩放，也就是参考稿那套落位公式的纯函数版本
 *
 * 参考稿算开场 logo 落点用的是同一套公式：把 logo 缩到 `min(槽宽, 122px)`，
 * 再夹在 0.28~0.42 之间。**这个值必须和开场动画真正落在的缩放一致**，
 * 否则 logo 交接那一帧会跳一下。
 *
 * 注意侧栏**并不**读这个属性来画常驻 logo：开场动画量的是 logo 的真实宽度，
 * 算出的 `landingScale` 写进 `OxideBrandSlotState`，侧栏照抄那个值
 * （见 `OxideBrandSlotLogo`）。这里留着它是因为它是那条公式唯一可单测的写法，
 * 而 [BrandLogoStartWidthDp] 只有取对值，两边才会落在同一个数上。
 */
fun oxideBrandLogoScale(slotWidth: Dp): Float {
    val target = minOf(slotWidth.value, Oxide.Motion.BrandSlotMaxWidthDp)
    if (target <= 0f) return Oxide.Motion.IntroScaleMin
    return (target / BrandLogoStartWidthDp)
        .coerceIn(Oxide.Motion.IntroScaleMin, Oxide.Motion.IntroScaleMax)
}

/**
 * `OxideLogo` 在原始尺寸下的宽度：图形 37 + 间距 10 + 60sp 字标，实测约 200dp
 *
 * 这个数只是 [oxideBrandLogoScale] 里 `start.width` 的替身——参考稿里浏览器量的是
 * logo 的真实宽度，本项目开场动画也量（见 `OxideIntro`），所以只有量出来的那个说了算。
 *
 * 字标宽度是估的，估错一点就整条公式跟着错：早先把字标当成 250dp，总数成了 297dp，
 * 比实测高约 48%，于是 `122 / 297 = 0.41` 落在夹紧区间**内部**；而开场动画按实测的
 * 约 200dp 算，`122 / 200 = 0.61` 会被夹到 [IntroScaleMin, IntroScaleMax] 的上端
 * 0.42。两边因此差了一截，落地那一帧 logo 会跳一下——`OxideBrandSlot.kt` 里那句
 * "'图形 37 + 间距 10 + 字标 250dp' 这种估计值能差出四成"说的正是这件事。
 * 取实测值之后两边都落在 0.42，交接没有尺寸差。
 */
const val BrandLogoStartWidthDp: Float = 37f + 10f + 153f

// ---------------------------------------------------------------------------
// 界面缩放
//
// 参考稿是 1px = 1dp 的直译，没有"整体放大"这回事，所以用户要调大调小界面时，
// 唯一诚实的做法就是把同一份几何与同一套字号乘同一个系数：版面和排版永远是
// 同一个比例，不会出现"框长大了字没变"的错位。这三个常量与纯函数就是那个系数
// 的唯一来源，几何走它、字号走它（见 Oxide.Type），两边不会各算各的。
// ---------------------------------------------------------------------------

/** 界面缩放的下限，与 `AllSettings.launcherGuiScale` 的 valueRange 保持一致 */
const val OxideGuiScaleMinPercent: Int = 75

/** 界面缩放的上限，与 `AllSettings.launcherGuiScale` 的 valueRange 保持一致 */
const val OxideGuiScaleMaxPercent: Int = 150

/** 界面缩放的默认值：100% 就是参考稿的原尺寸 */
const val OxideGuiScaleDefaultPercent: Int = 100

/**
 * 下拉里给出的档位
 *
 * 每档 25%，100% 正好落在中间，两端就是上下限。刻意不给零碎档位：每换一个档位
 * 都要在所有宽度档上重算一遍列数，中间值换来的只是更难预判的版面。
 */
val OxideGuiScaleSteps: List<Int> =
    listOf(OxideGuiScaleMinPercent, 100, 125, OxideGuiScaleMaxPercent)

/**
 * 百分比 → 缩放系数，纯函数
 *
 * 越界夹回区间，因此调用方不必自己判断——和 IntSettingUnit 里的 coerceIn 是同
 * 一道保险，重复一次不改变结果，只是让纯函数本身在单测里也站得住。
 */
fun oxideGuiScaleFactor(percent: Int): Float =
    percent.coerceIn(OxideGuiScaleMinPercent, OxideGuiScaleMaxPercent) / 100f

/**
 * 一组已经算好的界面尺寸
 *
 * 全部由可用宽高推导，页面不得再自行计算像素或写死 dp。
 */
@Immutable
data class OxideMetrics(
    val widthClass: OxideWidthClass,
    val sidebarWidth: Dp,
    /** 侧栏左右内边距，与 [sidebarWidth] 一起落档 */
    val sidebarPaddingH: Dp,
    /** 品牌槽到第一项导航的间距 */
    val brandGap: Dp,
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
    /** 紧凑档下整体缩放。参考稿不缩字号——它改的是列数，所以这里恒为 1 */
    val density: Float,
    /**
     * 用户选的界面缩放系数：1f = 100%
     *
     * 上面的每一个 dp 都已经是乘过它的结果；字号那一份由 `Oxide.Type` 在取值时
     * 乘同一个系数（见 [oxideGuiScaleFactor]），所以版面和排版永远是同一个比例。
     */
    val guiScale: Float = 1f,
) {
    /**
     * 导航列宽度
     *
     * 侧栏宽度里含着那条 1px 分隔线（参考稿 `box-sizing:border-box`），
     * 所以导航列 = 宽度 − 左右内边距 − 分隔线。三个减数都是缩放后的值。
     */
    val sidebarNavWidth: Dp
        get() = sidebarWidth - sidebarPaddingH * 2 - Oxide.SidebarBorder * guiScale

    /** 品牌槽宽度：导航列再左右内缩 4px */
    val brandSlotWidth: Dp
        get() = sidebarNavWidth - Oxide.BrandInsetH * guiScale * 2

    /**
     * 参考稿那套落位公式在侧栏这一档给出的静止缩放
     *
     * 侧栏常驻 logo 真正用的值是开场动画实测出来的 `OxideBrandSlotState.landingScale`，
     * 两者必须相等；相等这件事由 [BrandLogoStartWidthDp] 取实测宽度来保证。
     */
    val brandLogoScale: Float
        get() = oxideBrandLogoScale(brandSlotWidth)

    /**
     * 导航项文字的行高，也就是项高**至少**得有那么高
     *
     * 导航项高度会为了装下整列而让出空间，让到低于这一行文字时那一项自己就会溢出，
     * 因此它是 [oxideNavItemHeightFor] 的下限之一，也是"最极端的一档也不该看起来坏掉"
     * 这条要求的可测形式。
     */
    val navTextLineHeight: Dp get() = oxideNavTextLineHeight(guiScale)

    /** 导航项的纵坐标步进：项高 + 间距，选中指示条按它逐格移动 */
    val navStep: Dp get() = oxideNavStep(navItemHeight, Oxide.NavGap * guiScale)

    /** 第 [index] 项的指示条偏移 */
    fun navRailOffset(index: Int): Dp = oxideNavRailOffset(index, navItemHeight, Oxide.NavGap * guiScale)

    /** 导航列总高度，也就是指示条的行程 */
    val navTravel: Dp get() = oxideNavTravel(OxideNavItemCount, navItemHeight, Oxide.NavGap * guiScale)

    /**
     * 按实际内容宽度算出网格列数
     *
     * 用"内容区宽度 / 卡片最小宽度"而不是固定列数，这样窗口被切分、
     * 折叠屏展开或外接显示器接入时列数都会自动跟着变。
     * [contentWidth] 必须已经扣掉 [pagePaddingH]，否则会多算一列。
     *
     * 界面放大时列数会自己退让：[cardMinWidth] 与内容区宽度都乘了 [guiScale]，
     * 而内容区还因为侧栏与留白一起变大而变窄，所以同样的宽度在放大后装不下同样的
     * 列数——落到的列数更少，而不是卡片被裁掉半截。
     */
    fun gridColumns(contentWidth: Dp): Int {
        if (contentWidth <= 0.dp) return minCardColumns
        val fit = ((contentWidth + cardGap) / (cardMinWidth + cardGap)).toInt()
        return fit.coerceIn(minCardColumns, maxCardColumns)
    }

    /** Hero 标题字号随宽度缩放，比例关系与参考稿 `clamp(29px,3vw,44px)` 一致 */
    val heroTitleDp: Float
        get() = when (widthClass) {
            OxideWidthClass.Compact -> 29f
            OxideWidthClass.Medium -> 32f
            OxideWidthClass.Expanded -> 36f
            OxideWidthClass.Large -> 42f
        } * guiScale

    /**
     * 紧凑档下正文等小字号也一起缩放，保持层级关系
     *
     * 这里乘的是 [density]（宽度档带来的额外缩放），**不是** [guiScale]：字号本身
     * 在 `Oxide.Type` 取值时就已经乘过用户缩放了，再乘一次会把它叠成平方。
     */
    fun scaled(base: Float): Float = base * density
}

/**
 * 由可用尺寸算出界面尺寸
 *
 * 纯函数：不读 [androidx.content.Context]，也不碰组合期状态，
 * 因此可以在单元测试里把每一档的数值逐条钉死。
 *
 * [guiScalePercent] 是用户的界面缩放（`AllSettings.launcherGuiScale`）。
 * 默认 100% 时下面每一步都等于参考稿的原值，所以既有断言一条都不用改；
 * 非 100% 时**所有**尺寸乘同一个系数，列数则交给 [OxideMetrics.gridColumns] 重新算，
 * 放大的界面因此是"少一列"而不是"被裁掉"。
 */
fun oxideMetricsFor(
    widthDp: Int,
    heightDp: Int,
    guiScalePercent: Int = OxideGuiScaleDefaultPercent,
): OxideMetrics {
    val widthClass = oxideWidthClassFor(widthDp)

    // 侧栏的完整高度要装下 品牌槽 + 导航列 + 底部块，
    // 不到 340dp 就按参考稿的比例整体压一点，而不是让底部块被挤出去
    val shortScreen = heightDp < OxideShortScreenHeight

    val scale = oxideGuiScaleFactor(guiScalePercent)

    // 先按参考稿算出 100% 的尺寸，再统一乘系数：这样系数永远只在一个地方生效，
    // 新加尺寸的人不会漏乘，而 100% 时乘 1f 是精确恒等，参考稿的比例分毫不动。
    fun Dp.scaled(): Dp = this * scale

    return OxideMetrics(
        widthClass = widthClass,
        sidebarWidth = oxideSidebarWidthFor(widthClass).scaled(),
        sidebarPaddingH = oxideSidebarPaddingHFor(widthClass).scaled(),
        brandGap = oxideBrandGapFor(widthClass).scaled(),
        pagePaddingH = oxidePagePaddingHFor(widthClass).scaled(),
        pagePaddingV = (if (shortScreen) 6.dp else Oxide.PagePaddingT).scaled(),
        sectionGap = when (widthClass) {
            OxideWidthClass.Compact -> 10.dp
            OxideWidthClass.Medium -> 13.dp
            else -> 16.dp
        }.scaled(),
        cardGap = when (widthClass) {
            OxideWidthClass.Compact -> 9.dp
            OxideWidthClass.Medium, OxideWidthClass.Expanded, OxideWidthClass.Large -> 10.dp
        }.scaled(),
        // 最小宽度按参考稿的列数反推：3 列要在 1120px 以上刚好放得下，
        // 2 列要在 900px 以上刚好放得下。这样列数永远和参考稿一致，
        // 内容区不会被拉成几个巨大的卡片而显得空。
        // 放大时它跟着放大，于是同一块屏幕自然落到更少的列数上
        cardMinWidth = when (widthClass) {
            OxideWidthClass.Compact -> 220.dp
            OxideWidthClass.Medium -> 235.dp
            OxideWidthClass.Expanded, OxideWidthClass.Large -> 285.dp
        }.scaled(),
        // 下限永远是一行；上限只是上限，实际列数由 gridColumns 按内容宽度决定
        minCardColumns = 1,
        maxCardColumns = when (widthClass) {
            OxideWidthClass.Compact -> 2
            OxideWidthClass.Medium, OxideWidthClass.Expanded, OxideWidthClass.Large -> 3
        },
        // 参考稿是 min(520px, 44vw)，这里按 dp 等价换算并保留同样的下限；
        // 缩放之后再夹一次上限：抽屉上限取 44vw × 1.5，也就是放大到极限时
        // 它最坏也只占屏幕的三分之二，不会盖住侧栏和整条内容区
        drawerWidth = (minOf(520.dp, (widthDp * 0.44f).dp).coerceAtLeast(280.dp) * scale)
            .coerceAtMost((widthDp * OxideDrawerMaxWidthFraction).dp),
        topBarHeight = Oxide.TopBar.scaled(),
        // 侧栏的上下内边距、品牌槽与落款是固定值（logo 动画的锚点就在那里），
        // 所以放大后导航项要自己让出空间：oxideNavItemHeightFor 里算的就是这件事
        navItemHeight = oxideNavItemHeightFor(heightDp, widthClass, guiScalePercent),
        density = 1f,
        guiScale = scale,
    )
}

/**
 * 矮到这个高度以下，侧栏与页面纵向尺寸才整体压缩
 *
 * 参考稿没有这一档；这里只是保证在特别矮的横屏上底部块不会被挤出屏幕，
 * 常规横屏（≥340dp 高）一律按参考稿的比例走。
 *
 * 界面放大时这个阈值不变——它量的是"屏幕有多矮"，和界面画得多大无关，
 * 所以放大之后侧栏装不下时是 [OxideMetrics.sidebarWidth] 之类一起变大，
 * 纵向滚动照旧，而不是把这一档也悄悄改掉。
 */
const val OxideShortScreenHeight: Int = 340

/**
 * 由当前可用尺寸算出界面尺寸
 *
 * 读 [LocalConfiguration] 而不是屏幕物理尺寸：分屏、多窗口和折叠屏展开时
 * 可用区域会小于整块屏幕，用物理尺寸会导致内容被裁掉。
 *
 * 界面缩放读的是 `AllSettings.launcherGuiScale.state`：在组合期读它就是订阅它，
 * 所以设置一改尺寸立刻重算，不需要重启，也不需要谁手动 invalidate。
 */
@Composable
fun rememberOxideMetrics(): OxideMetrics {
    val configuration = LocalConfiguration.current
    val guiScalePercent = AllSettings.launcherGuiScale.state

    return remember(configuration.screenWidthDp, configuration.screenHeightDp, guiScalePercent) {
        oxideMetricsFor(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            guiScalePercent,
        )
    }
}