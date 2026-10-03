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

package dev.oxide.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Oxide Launcher 的设计系统
 *
 * 整个调色板是**无彩色的**：只有黑到白，以及白色在不同透明度下的叠加。
 * 唯一的"强调色"是接近纯白，因此界面读起来是光的而不是被 Minecraft 的配色带跑。
 *
 * 所有数值都来自参考稿的 CSS，并按 1px = 1dp 直译，因此可以在窄屏与大屏之间
 * 只按比例调整，而不需要另一套视觉语言。
 */
object Oxide {

    // ---- 颜色 -------------------------------------------------------------

    /** 页面底色 */
    val Bg = Color(0xFF050505)

    /** 侧栏 */
    val Sidebar = Color(0x0D050505) // rgba(5,5,5,.68)

    /** 卡片/面板的底层 */
    val SurfaceBase = Color(0x170A0A0A) // rgba(10,10,10,.72)

    /** 分隔线，1px 低透明度白 */
    val Line = Color(0x12FFFFFF) // rgba(255,255,255,.07)

    /** 更强的分隔线，用于弹出面板、选中卡片等需要抬高一层的边界 */
    val Line2 = Color(0x1FFFFFFF) // rgba(255,255,255,.12)

    /** 更弱的行分隔线 */
    val LineFaint = Color(0x0BFFFFFF) // rgba(255,255,255,.045)

    /** 默认边框 */
    val Border = Color(0xFF292929)

    val BgElevated = Color(0xFF0D0D0D)
    val BgButton = Color(0xFF101010)
    val BgButtonHover = Color(0xFF151515)
    val BgChip = Color(0xFF171717)
    val BgTabActive = Color(0xFF181818)
    val BgToggleOff = Color(0xFF292929)
    val BgToggleOn = Color(0xFFDDDDDD)
    val DrawerBg = Color(0xFA0B0B0B) // rgba(11,11,11,.98)
    val DrawerScrim = Color(0x8C000000) // rgba(0,0,0,.55)
    val PopoverBg = Color(0xFA0D0D0D) // rgba(13,13,13,.985)
    val ToastBg = Color(0xFF151515)

    /** 文字层级，从亮到暗 */
    val Fg = Color(0xFFEEEEEE)
    val FgStrong = Color(0xFFDDDDDD)
    val FgMuted = Color(0xFFAAAAAA)
    val FgDim = Color(0xFF616161)
    val FgFaint = Color(0xFF555555)
    val FgGhost = Color(0xFF4E4E4E)
    val FgNum = Color(0xFF3E3E3E)
    val FgNumActive = Color(0xFF8D8D8D)
    val WordmarkTail = Color(0xFF777777)
    val MarkBorder = Color(0xFF666666)
    val MarkInner = Color(0xFFA9A9A9)

    // ---- 尺寸 -------------------------------------------------------------

    /** 侧栏宽度。会按可用宽度收窄，但不低于 [SidebarMin] */
    val SidebarWidth: Dp = 184.dp
    val SidebarWidthMedium: Dp = 164.dp
    val SidebarWidthCompact: Dp = 145.dp
    val SidebarMin: Dp = 132.dp

    /** 顶栏高度 */
    val TopBar: Dp = 46.dp

    /** 卡片圆角 */
    val Radius: Dp = 15.dp

    /** 页面左右留白 */
    val PagePaddingH: Dp = 27.dp
    val PagePaddingT: Dp = 10.dp
    val PagePaddingB: Dp = 17.dp

    /** 导航项高度与间距，间距之和就是选中指示条的步进 */
    val NavItemHeight: Dp = 38.dp
    val NavGap: Dp = 3.dp

    /** 侧栏里品牌槽的高度，动画终点就是它的中心 */
    val BrandSlotHeight: Dp = 34.dp

    /** 标志图形的边长，与参考稿的 37px 对应 */
    val MarkSize: Dp = 37.dp

    // ---- 圆角 -------------------------------------------------------------

    val RadiusCard = RoundedCornerShape(15.dp)
    val RadiusPanel = RoundedCornerShape(14.dp)
    val RadiusDrawer = RoundedCornerShape(17.dp)
    val RadiusPopover = RoundedCornerShape(12.dp)
    val RadiusBlock = RoundedCornerShape(11.dp)
    val RadiusButton = RoundedCornerShape(9.dp)
    val RadiusChip = RoundedCornerShape(8.dp)
    val RadiusControl = RoundedCornerShape(7.dp)
    val RadiusSmall = RoundedCornerShape(6.dp)
    val RadiusBadge = RoundedCornerShape(5.dp)
    val RadiusToggle = RoundedCornerShape(50)

    // ---- 渐变与阴影 --------------------------------------------------------

    /** 面板的斜向高光叠在底层色上 */
    val SurfaceBrush = Brush.linearGradient(
        0f to Color(0x0AFFFFFF), // rgba(255,255,255,.038)
        0.53f to Color(0x02FFFFFF), // rgba(255,255,255,.008)
        1f to Color(0x1F000000) // rgba(0,0,0,.12)
    )

    /** 导航选中指示条 */
    val RailBrush = Brush.linearGradient(
        listOf(Color(0x13FFFFFF), Color(0x06FFFFFF)) // .075 -> .025
    )

    val AmbientBrush = Brush.verticalGradient(
        listOf(Color(0xFF070707), Color(0xFF050505), Color(0xFF040404))
    )

    // ---- 字号 -------------------------------------------------------------

    /**
     * 参考稿的字号跨度极窄：正文 8px，小标签 6-7px，标题 9-10px，
     * 大标题 22px，logo 字 60px。Android 的 sp 与 px 不是一回事，
     * 这里按 1sp ≈ 参考稿 1px 直译，保证比例关系不变。
     */
    object Type {
        /** 小标签：7px、大写、宽字距 */
        val Label = TextStyle(
            fontSize = 7.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.sp,
        )

        /** 更小的标签：6px */
        val MicroLabel = TextStyle(
            fontSize = 6.sp,
            lineHeight = 9.sp,
            letterSpacing = 0.7.sp,
        )

        /** 正文：8px，是整个界面出现最多的字号 */
        val Body = TextStyle(
            fontSize = 8.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal,
        )

        val BodyStrong = Body.copy(fontWeight = FontWeight.Medium)

        /** 次要正文 9px，用于卡片标题一类 */
        val Title = TextStyle(
            fontSize = 9.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

        /** 导航项文字 10px */
        val Nav = TextStyle(
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.05.sp,
        )

        /** 页面大标题 22px */
        val PageTitle = TextStyle(
            fontSize = 22.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )

        /** 抽屉标题 16px */
        val DrawerTitle = TextStyle(
            fontSize = 16.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.8).sp,
        )

        /** 实例卡上的版本号 22px */
        val InstanceVersion = TextStyle(
            fontSize = 22.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
        )

        /** Hero 标题更大，并随宽度缩放 */
        fun heroTitle(widthDp: Float) = TextStyle(
            fontSize = (29f + (widthDp - 700f).coerceIn(0f, 500f) * 0.03f).coerceIn(29f, 44f).sp,
            lineHeight = (29f + (widthDp - 700f).coerceIn(0f, 500f) * 0.03f).coerceIn(29f, 44f).sp * 0.96f,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.6).sp,
        )

        /** 等宽日志文本 */
        val Mono = TextStyle(
            fontSize = 7.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal,
        )
    }

    // ---- 动效 -------------------------------------------------------------

    /**
     * 参考稿用两条贝塞尔曲线承担几乎所有过渡：
     * `ease = cubic-bezier(.2,.82,.18,1)` 与 `ease2 = cubic-bezier(.16,.78,.2,1)`。
     * 这里保持同样的"几乎立刻动起来、最后缓慢停住"的性格。
     */
    object Motion {
        /** 进场/离场：620ms 与 500ms */
        const val PageEnterMs = 620
        const val PageLeaveMs = 500
        const val PageSettleMs = 510

        /** 页面进场的水平位移 */
        const val PageEnterOffset = 18f
        const val PageLeaveOffset = 12f

        /** 选中指示条 640ms，比页面进场略慢，收尾更稳 */
        const val RailMs = 640

        /** 子元素错峰：延迟 80ms，每级加 40ms，动画 440ms，起始透明度 0.7，位移 3px */
        const val RevealMs = 440
        const val RevealDelayFirstMs = 80
        const val RevealDelayStepMs = 40

        /** 抽屉 620ms，遮罩 300ms */
        const val DrawerMs = 620
        const val ScrimMs = 300
        const val DrawerOverhang = 24

        /** 下拉 280ms 变换、180ms 淡入 */
        const val PopoverMs = 280
        const val PopoverFadeMs = 180

        /** intro：3.15 秒旅程，2.45 秒开始揭幕 */
        const val IntroJourneyMs = 3150
        const val IntroCurtainRevealMs = 2450
        const val IntroCurtainFadeMs = 780

        /** 中心停留到 24%，13% 处有一个轻微放大 */
        const val IntroPulseAt = 0.13f
        const val IntroPulseScale = 1.035f
        const val IntroSettleAt = 0.24f
        const val IntroTravelAt = 0.70f

        /** 下划线：280ms 后扫入，1650ms 后收回 */
        const val IntroLineInDelayMs = 280
        const val IntroLineInMs = 550
        const val IntroLineOutDelayMs = 1650
        const val IntroLineOutMs = 550

        /** 品牌槽宽度上限，决定 logo 落地后的缩放 */
        const val BrandSlotMaxWidthDp = 122f
        const val IntroScaleMax = 0.42f
        const val IntroScaleMin = 0.28f
    }

    // ---- 排版辅助 ----------------------------------------------------------

    /** 单行截断，行数固定，避免布局在内容变化时跳动 */
    val SingleLine = androidx.compose.ui.text.style.TextOverflow.Ellipsis
}

/** 中文等没有 Inter 的字体时，让小字号仍然清晰可读 */
internal val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)