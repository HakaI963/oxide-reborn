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

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.context.GlobalContext
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.DarkMode
import dev.oxide.launcher.ui.screens.main.oxide.oxideGuiScaleFactor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ---------------------------------------------------------------------------
// 对比度
//
// Oxide 的字号跨度极窄（6sp 的说明文字到 22sp 的大标题），说明文字与分类标签
// 都属于 WCAG 意义上的"小字"，因此必须按 4.5:1 校验，而不是按 3:1 的大字标准。
// 这里的三个函数是纯函数，不碰任何 Android API，所以可以直接写单测。
// ---------------------------------------------------------------------------

/** sRGB 相对亮度，WCAG 2.x 的定义 */
internal fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** 两个颜色的 WCAG 对比度，返回 1f..21f */
internal fun contrastRatio(foreground: Color, background: Color): Float {
    val a = relativeLuminance(foreground) + 0.05
    val b = relativeLuminance(background) + 0.05
    return (max(a, b) / min(a, b)).toFloat()
}

/**
 * 把一个可能来自系统动态取色的强调色压到在当前底色上读得清的亮度
 *
 * Material 的 `primary` 只保证和它自己的 surface 有 3:1，落到 Oxide 的近黑或近白
 * 底色上就可能掉到 1.5:1。这里朝远离底色的方向等比收缩 RGB，直到达到 [minRatio]，
 * 步进固定、循环有上限，所以结果确定可测；缩放对三个通道用的是同一个系数，
 * 因此深色主题下色相不变，浅色主题下也是整体压暗而不是换成别的颜色。
 */
internal fun readableAccent(
    accent: Color,
    dark: Boolean,
    minRatio: Float = MIN_TEXT_CONTRAST,
): Color {
    val background = if (dark) OxideChrome.Dark.bg else OxideChrome.Light.bg
    if (contrastRatio(accent, background) >= minRatio) return accent

    val target = if (dark) 1f else 0f
    var result = accent
    var factor = 1f
    // 最多 20 步，每步把 RGB 拉近 6%，足以覆盖从纯黄到近黑这种极端取色
    repeat(ACCENT_MAX_STEPS) {
        if (contrastRatio(result, background) >= minRatio) return result
        factor *= ACCENT_STEP_FACTOR
        result = Color(
            red = accent.red + (target - accent.red) * (1f - factor),
            green = accent.green + (target - accent.green) * (1f - factor),
            blue = accent.blue + (target - accent.blue) * (1f - factor),
            alpha = accent.alpha,
        )
    }
    return result
}

/** 小字（6sp 的说明、分类标签）在 Oxide 底色上的最低对比度 */
internal const val MIN_TEXT_CONTRAST = 4.5f

private const val ACCENT_MAX_STEPS = 20
private const val ACCENT_STEP_FACTOR = 0.94f

/**
 * 这套调色板是不是深色的
 *
 * 渐变要按明暗取两套常量色，所以这里用底色的亮度判断，而不是给数据类再加一个字段——
 * 底色本来就决定了整套的明暗，字段和它永远是同一个答案。
 */
private fun isDarkPalette(chrome: OxideChrome): Boolean =
    relativeLuminance(chrome.bg) < relativeLuminance(OxideChrome.Light.bg)

// ---------------------------------------------------------------------------
// 调色板
// ---------------------------------------------------------------------------

/**
 * 一整套 Oxide 颜色
 *
 * 深浅两套是同一组角色的镜像：底色、面板、分隔线与文字层级逐个对调，
 * 因此换主题时版面、圆角、层级和参考稿完全一致，只有明暗关系翻转。
 * 形状、字号与动效不在这里——它们与主题无关。
 */
@Immutable
data class OxideChrome(
    val bg: Color,
    val sidebar: Color,
    val surfaceBase: Color,
    val line: Color,
    val line2: Color,
    val lineFaint: Color,
    val border: Color,
    val bgElevated: Color,
    val bgButton: Color,
    val bgButtonHover: Color,
    val bgChip: Color,
    val bgTabActive: Color,
    val bgToggleOff: Color,
    val bgToggleOn: Color,
    val drawerBg: Color,
    val drawerScrim: Color,
    /**
     * 二级面板背后的底幕
     *
     * 刻意不是纯黑，也不是全透明：全透明会让被盖住的首页继续在下面主导视觉，
     * 纯黑则会把层级压平——面板、卡片、分组就再也分不出前后。
     * 0xF2 足以把下面的内容压到读不出来，同时保留一点点纵深。
     */
    val panelBackdrop: Color,
    val popoverBg: Color,
    val toastBg: Color,
    val fg: Color,
    val fgStrong: Color,
    val fgMuted: Color,
    val fgDim: Color,
    val fgFaint: Color,
    val fgGhost: Color,
    val fgNum: Color,
    val wordmarkTail: Color,
    val markBorder: Color,
    val markInner: Color,
    /** 强调色：跟着用户选的颜色主题走，底下保证读得清 */
    val accent: Color,
) {
    companion object {
        /**
         * 深色
         *
         * 页面底 #050505、面板 rgba(10,10,10,.72) 都来自参考稿的 CSS。
         * 文字层级从 17.6:1 收到 4.6:1：参考稿最暗的文字是 #555（2.7:1），
         * 但 Oxide 的说明文字只有 6sp，那个对比度在真机上读不出来，
         * 因此整条暗端都抬到 4.5:1 以上。
         */
        val Dark = OxideChrome(
            bg = Color(0xFF050505),
            sidebar = Color(0x0D050505),
            surfaceBase = Color(0x170A0A0A),
            line = Color(0x12FFFFFF),
            line2 = Color(0x1FFFFFFF),
            lineFaint = Color(0x0BFFFFFF),
            border = Color(0xFF292929),
            bgElevated = Color(0xFF0D0D0D),
            bgButton = Color(0xFF101010),
            bgButtonHover = Color(0xFF151515),
            bgChip = Color(0xFF171717),
            bgTabActive = Color(0xFF181818),
            bgToggleOff = Color(0xFF292929),
            // 开关的滑块与按钮的文字是一对写死的反色（见 OxideComponents），
            // 因此这一对在两种主题里都保持"浅轨 + 深块"，不然文字会读不出来
            bgToggleOn = Color(0xFFDDDDDD),
            drawerBg = Color(0xFA0B0B0B),
            drawerScrim = Color(0x8C000000),
            panelBackdrop = Color(0xF2050505),
            popoverBg = Color(0xFA0D0D0D),
            toastBg = Color(0xFF151515),
            fg = Color(0xFFEEEEEE),
            fgStrong = Color(0xFFDDDDDD),
            fgMuted = Color(0xFFAAAAAA),
            fgDim = Color(0xFF909090),
            fgFaint = Color(0xFF868686),
            fgGhost = Color(0xFF7E7E7E),
            fgNum = Color(0xFF787878),
            wordmarkTail = Color(0xFF8A8A8A),
            markBorder = Color(0xFF7A7A7A),
            markInner = Color(0xFFA9A9A9),
            accent = Color(0xFFDDDDDD),
        )

        /**
         * 浅色
         *
         * 深色那套的逐项对调：底色抬到近白，1px 分隔线变成低透明度黑，
         * 文字从 17.1:1 收到 4.5:1。面板仍然是半透明的白，所以叠在壁纸上还是透的。
         */
        val Light = OxideChrome(
            bg = Color(0xFFF6F6F6),
            sidebar = Color(0x0DF6F6F6),
            surfaceBase = Color(0x24FFFFFF),
            line = Color(0x14000000),
            line2 = Color(0x1F000000),
            lineFaint = Color(0x0D000000),
            border = Color(0xFFD6D6D6),
            bgElevated = Color(0xFFFFFFFF),
            bgButton = Color(0xFFFFFFFF),
            bgButtonHover = Color(0xFFF2F2F2),
            bgChip = Color(0xFFEFEFEF),
            bgTabActive = Color(0xFFE9E9E9),
            bgToggleOff = Color(0xFFD2D2D2),
            bgToggleOn = Color(0xFFDDDDDD),
            drawerBg = Color(0xFAFFFFFF),
            drawerScrim = Color(0x8C000000),
            panelBackdrop = Color(0xF2050505),
            popoverBg = Color(0xFFFFFFFF),
            toastBg = Color(0xFFFFFFFF),
            fg = Color(0xFF141414),
            fgStrong = Color(0xFF2B2B2B),
            fgMuted = Color(0xFF4A4A4A),
            fgDim = Color(0xFF5E5E5E),
            fgFaint = Color(0xFF656565),
            fgGhost = Color(0xFF6B6B6B),
            fgNum = Color(0xFF717171),
            wordmarkTail = Color(0xFF7E7E7E),
            markBorder = Color(0xFF8A8A8A),
            markInner = Color(0xFF4A4A4A),
            accent = Color(0xFF2B2B2B),
        )

        /** 按深浅取一套，并让强调色在当前底色上一定读得清 */
        fun of(dark: Boolean, accent: Color?): OxideChrome {
            val base = if (dark) Dark else Light
            val resolved = accent ?: base.accent
            return if (resolved == base.accent) {
                base
            } else {
                base.copy(accent = readableAccent(resolved, dark))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 当前生效的调色板
//
// 颜色不能是 object 里的常量：那样它们在进程启动时就固定下来，
// 深色模式和颜色主题改了以后 Oxide 界面一个像素都不会变——这正是 v1.4.0
// 里"控件在、但不生效"的根因。因此每个颜色都变成读取下面的派生状态，
// 组合阶段读它就等于订阅它，设置一变用到它的界面自动重组。
// ---------------------------------------------------------------------------

/** 系统当前是不是深色。读取系统配置，不需要组合环境。 */
private fun systemIsDark(): Boolean =
    (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/**
 * 系统深浅的初值，组合阶段读它就等于订阅它
 *
 * 这里必须 `by lazy`：文件级属性是在 `OxideKt` 的**静态初始化**里求值的，
 * 而 `systemIsDark()` 走的是 `Resources.getSystem()`——类一加载就去问框架，
 * 于是这个文件里那些本来不碰 Android 的纯函数（[contrastRatio]、
 * [relativeLuminance]、[readableAccent]、[oxideScaledTextStyle]）在单测里
 * 一被调用就炸在类初始化上（`returnDefaultValues` 下 `Resources.getSystem()`
 * 返回 null，随后 `ExceptionInInitializerError`）。
 *
 * 推迟到第一次真正读深浅时再问框架，那时一定已经在组合里（[chrome] 的派生
 * 计算或 [ProvideOxideChrome] 的副作用），框架可用，结果与原来一致。
 */
private val systemDark: MutableState<Boolean> by lazy { mutableStateOf(systemIsDark()) }

/**
 * 用户选的颜色主题给出的强调色
 *
 * null 表示还没有任何界面把 Material 的 `primary` 递进来，此时沿用无彩色的默认强调色。
 * 由 [ProvideOxideChrome] 写入。
 */
private val accentOverride = mutableStateOf<Color?>(null)

private val chrome: OxideChrome by derivedStateOf {
    OxideChrome.of(
        dark = when (AllSettings.launcherDarkMode.state) {
            DarkMode.Enable -> true
            DarkMode.Disable -> false
            // 跟随系统时读系统配置，而不是再问一次 Material
            DarkMode.FollowSystem -> systemDark.value
        },
        accent = accentOverride.value,
    )
}

private val systemDarkObserver = object : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) {
        systemDark.value =
            (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onLowMemory() = Unit

    override fun onTrimMemory(level: Int) = Unit
}

private var observingSystemDark = false

private fun ensureSystemDarkObserved() {
    if (observingSystemDark) return
    // GlobalContext 在 Application.onCreate 之后才有；拿不到就退回启动时读到的值
    observingSystemDark = runCatching {
        GlobalContext.registerComponentCallbacks(systemDarkObserver)
    }.isSuccess
}

/**
 * 把用户选的颜色主题递给 Oxide
 *
 * Oxide 的中性色不跟主题走——版面靠 1px 发丝线和极低对比的面板堆叠，
 * 换成饱和底色会直接毁掉这套关系。跟着主题走的只有强调色，
 * 它取自 Material 的 `primary`，因此动态取色、七套配色和自定义色都自动生效。
 *
 * 这个函数必须放在 Oxide 界面的根上（见 `OxideMainShell`）；设置页自己也调用了一次，
 * 所以在设置里换主题时界面立刻就变。
 */
@Composable
fun ProvideOxideChrome(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val primary = MaterialTheme.colorScheme.primary

    SideEffect {
        ensureSystemDarkObserved()
        systemDark.value =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        accentOverride.value = primary
    }

    content()
}

/**
 * Oxide Launcher 的设计系统
 *
 * 整个调色板是**无彩色的**：只有黑到白，以及白色在不同透明度下的叠加。
 * 唯一的"强调色"来自用户选的颜色主题，且一定被压到在当前底色上读得清的亮度，
 * 因此界面读起来是光的，而不是被 Minecraft 的配色带跑。
 *
 * 所有数值都来自参考稿的 CSS，并按 1px = 1dp 直译，因此可以在窄屏与大屏之间
 * 只按比例调整，而不需要另一套视觉语言。
 */
object Oxide {

    // ---- 颜色 -------------------------------------------------------------

    /** 页面底色 */
    val Bg: Color get() = chrome.bg

    /** 侧栏 */
    val Sidebar: Color get() = chrome.sidebar

    /** 卡片/面板的底层 */
    val SurfaceBase: Color get() = chrome.surfaceBase

    /** 分隔线，1px 低透明度 */
    val Line: Color get() = chrome.line

    /** 更强的分隔线，用于弹出面板、选中卡片等需要抬高一层的边界 */
    val Line2: Color get() = chrome.line2

    /** 更弱的行分隔线 */
    val LineFaint: Color get() = chrome.lineFaint

    /** 默认边框 */
    val Border: Color get() = chrome.border

    val BgElevated: Color get() = chrome.bgElevated
    val BgButton: Color get() = chrome.bgButton
    val BgButtonHover: Color get() = chrome.bgButtonHover
    val BgChip: Color get() = chrome.bgChip
    val BgTabActive: Color get() = chrome.bgTabActive
    val BgToggleOff: Color get() = chrome.bgToggleOff
    val BgToggleOn: Color get() = chrome.bgToggleOn
    val DrawerBg: Color get() = chrome.drawerBg
    val PanelBackdrop: Color get() = chrome.panelBackdrop
    val DrawerScrim: Color get() = chrome.drawerScrim
    val PopoverBg: Color get() = chrome.popoverBg
    val ToastBg: Color get() = chrome.toastBg

    /** 文字层级，从亮到暗 */
    val Fg: Color get() = chrome.fg
    val FgStrong: Color get() = chrome.fgStrong
    val FgMuted: Color get() = chrome.fgMuted
    val FgDim: Color get() = chrome.fgDim
    val FgFaint: Color get() = chrome.fgFaint
    val FgGhost: Color get() = chrome.fgGhost
    val FgNum: Color get() = chrome.fgNum

    /** 选中态的编号，跟随用户选的颜色主题 */
    val FgNumActive: Color get() = chrome.accent

    /** 强调色本身。落在底色、轨条这类需要成块使用的地方。 */
    val Accent: Color get() = chrome.accent

    val WordmarkTail: Color get() = chrome.wordmarkTail
    val MarkBorder: Color get() = chrome.markBorder
    val MarkInner: Color get() = chrome.markInner

    // ---- 尺寸 -------------------------------------------------------------

    /** 侧栏宽度。会按可用宽度收窄，但不低于 [SidebarMin] */
    val SidebarWidth: Dp = 184.dp
    val SidebarWidthMedium: Dp = 164.dp
    val SidebarWidthCompact: Dp = 145.dp
    val SidebarMin: Dp = 132.dp

    /**
     * 侧栏内部几何，全部直译参考稿的 `.sidebar` / `.brandSlot` / `.nav` / `.sidebarBottom`
     *
     * 侧栏宽度里**含着**右边那条 1px 分隔线（参考稿 `box-sizing:border-box`），
     * 所以导航列宽度 = 侧栏宽度 − 左右内边距 − 分隔线，而不是只减内边距。
     */

    /** 侧栏内边距，参考稿 `padding:20px 15px 15px` */
    val SidebarPaddingTop: Dp = 20.dp
    val SidebarPaddingH: Dp = 15.dp
    val SidebarPaddingBottom: Dp = 15.dp

    /** 侧栏右侧的分隔线，1px */
    val SidebarBorder: Dp = 1.dp

    /** 窄一档（≤1120px）时侧栏内边距收到 12px，页面左右留白收到 21px */
    val SidebarPaddingHNarrow: Dp = 12.dp
    val PagePaddingHNarrow: Dp = 21.dp

    /** 品牌槽相对导航列的内缩，参考稿 `margin:0 4px` */
    val BrandInsetH: Dp = 4.dp

    /** 品牌槽到第一项导航的间距 36px，≤900px 收到 34px */
    val BrandGap: Dp = 36.dp
    val BrandGapNarrow: Dp = 34.dp

    /** 导航项与选中指示条的圆角 10px */
    val NavItemRadius: Dp = 10.dp

    /** 导航项横向内边距 10px、编号列宽 22px（`padding:0 10px` 与 `22px 1fr auto`） */
    val NavItemPaddingH: Dp = 10.dp
    val NavNumColumnWidth: Dp = 22.dp

    /** 侧栏底部块：左右比导航列再内缩 5px，顶线之上留 12px */
    val SidebarFooterInsetH: Dp = 5.dp
    val SidebarFooterPaddingTop: Dp = 12.dp

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
    //
    // 渐变也依赖调色板，但 Brush 是有分配成本的，因此各自包一层派生状态：
    // 只有调色板真的变了才会重新构造，平时读到的还是同一个对象。

    /** 面板的斜向高光叠在底层色上 */
    private val surfaceBrush = derivedStateOf {
        val c = chrome
        Brush.linearGradient(
            0f to if (isDarkPalette(c)) Color(0x0AFFFFFF) else Color(0x8AFFFFFF),
            0.53f to if (isDarkPalette(c)) Color(0x02FFFFFF) else Color(0x30FFFFFF),
            1f to if (isDarkPalette(c)) Color(0x1F000000) else Color(0x14000000),
        )
    }
    val SurfaceBrush: Brush get() = surfaceBrush.value

    /** 导航选中指示条：色相跟着强调色走，透明度仍取参考稿 .navRail 的 .075 -> .025 */
    private val railBrush = derivedStateOf {
        val accent = chrome.accent
        Brush.linearGradient(
            listOf(accent.copy(alpha = 0.075f), accent.copy(alpha = 0.025f))
        )
    }
    val RailBrush: Brush get() = railBrush.value

    private val ambientBrush = derivedStateOf {
        val c = chrome
        if (isDarkPalette(c)) {
            Brush.verticalGradient(
                listOf(Color(0xFF070707), Color(0xFF050505), Color(0xFF040404))
            )
        } else {
            Brush.verticalGradient(
                listOf(Color(0xFFFBFBFB), Color(0xFFF6F6F6), Color(0xFFF1F1F1))
            )
        }
    }
    val AmbientBrush: Brush get() = ambientBrush.value

    // ---- 字号 -------------------------------------------------------------

    /**
     * 参考稿的字号跨度极窄：正文 8px，小标签 6-7px，标题 9-10px，
     * 大标题 22px，logo 字 60px。Android 的 sp 与 px 不是一回事，
     * 这里按 1sp ≈ 参考稿 1px 直译，保证比例关系不变。
     *
     * 下面每一条都是**基础**字号加一个 getter：getter 把用户选的界面缩放
     * （`AllSettings.launcherGuiScale`）乘上去，几何那一半在 [OxideMetrics]
     * 里乘同一个系数，所以"框"和"字"永远是同一个比例，调用点一个字都不用改。
     */
    object Type {
        /**
         * 把一个基准字号包成派生状态
         *
         * 读 `.state` 就是订阅它，因此设置一改，用到字号的界面会立刻重组，
         * 不需要重启，也不需要谁手动 invalidate。几何那一半在
         * `rememberOxideMetrics()` 里读同一个设置，两半永远同步。
         *
         * 每个基准样式只在**初始化时**建一次派生状态：系数不变时读到的是同一个
         * TextStyle 对象，所以组合不会因为"每次读都新建一个样式"而白白重组。
         */
        private fun scaledStyle(base: TextStyle): State<TextStyle> =
            derivedStateOf { oxideScaledTextStyle(base, oxideGuiScaleFactor(AllSettings.launcherGuiScale.state)) }

        /** 小标签：7px、大写、宽字距 */
        private val scaledLabel = scaledStyle(
            TextStyle(
                fontSize = 7.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
            )
        )

        val Label: TextStyle get() = scaledLabel.value

        /** 更小的标签：6px */
        private val scaledMicroLabel = scaledStyle(
            TextStyle(
                fontSize = 6.sp,
                lineHeight = 9.sp,
                letterSpacing = 0.7.sp,
            )
        )

        val MicroLabel: TextStyle get() = scaledMicroLabel.value

        /** 正文：8sp，是整个界面出现最多的字号 */
        private val scaledBody = scaledStyle(
            TextStyle(
                fontSize = 8.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        )

        val Body: TextStyle get() = scaledBody.value

        val BodyStrong: TextStyle get() = Body.copy(fontWeight = FontWeight.Medium)

        /** 次要正文 9px，用于卡片标题一类 */
        private val scaledTitle = scaledStyle(
            TextStyle(
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        )

        val Title: TextStyle get() = scaledTitle.value

        /** 导航项文字 10px */
        private val scaledNav = scaledStyle(
            TextStyle(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.05.sp,
            )
        )

        val Nav: TextStyle get() = scaledNav.value

        /** 页面大标题 22px */
        private val scaledPageTitle = scaledStyle(
            TextStyle(
                fontSize = 22.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.4).sp,
            )
        )

        val PageTitle: TextStyle get() = scaledPageTitle.value

        /** 抽屉标题 16px */
        private val scaledDrawerTitle = scaledStyle(
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.8).sp,
            )
        )

        val DrawerTitle: TextStyle get() = scaledDrawerTitle.value

        /** 实例卡上的版本号 22px */
        private val scaledInstanceVersion = scaledStyle(
            TextStyle(
                fontSize = 22.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
            )
        )

        val InstanceVersion: TextStyle get() = scaledInstanceVersion.value

        /**
         * Hero 标题更大，并随宽度缩放
         *
         * 宽度这一半是纯算术，每次调用都可能得到不同的基准，因此不走派生状态，
         * 只在返回前乘一次用户缩放——和其余所有字号走的是同一个函数。
         */
        fun heroTitle(widthDp: Float): TextStyle = oxideScaledTextStyle(
            TextStyle(
                fontSize = (29f + (widthDp - 700f).coerceIn(0f, 500f) * 0.03f).coerceIn(29f, 44f).sp,
                lineHeight = (29f + (widthDp - 700f).coerceIn(0f, 500f) * 0.03f).coerceIn(29f, 44f).sp * 0.96f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.6).sp,
            ),
            oxideGuiScaleFactor(AllSettings.launcherGuiScale.state),
        )

        /** 等宽日志文本 */
        private val scaledMono = scaledStyle(
            TextStyle(
                fontSize = 7.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        )

        val Mono: TextStyle get() = scaledMono.value
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

/**
 * 把一套基础字号按界面缩放系数放大或缩小
 *
 * 字号、行高与字距一起乘同一个系数，所以 6sp 到 22sp 这条坡道的**比例关系**完全不变，
 * 只是整体变大变小：放大不会把某一个字号单独撑开，缩小也不会把层级差压没。
 * 字距为负（大标题是 -1.4px）时乘完仍是负的，紧凑感不会被缩没。
 *
 * 纯函数：不读设置也不碰组合期状态，所以可以直接写单测。`Oxide.Type` 里每个样式
 * 都是"这个函数 + 一个来自 [oxideGuiScaleFactor] 的系数"，因此没有第二个缩放出口。
 */
/**
 * 按界面缩放系数放大一份文字样式
 *
 * [TextUnit.Unspecified] 不能参与算术：对它做乘法会抛 IllegalArgumentException。
 * 而 fontSize / lineHeight / letterSpacing 在没有显式赋值时**默认就是 Unspecified**
 * （letterWidth/letterHeight 同理），所以这里必须逐个判断，
 * 否则任何一份没写 letterSpacing 的样式在读取时都会直接崩掉。
 */
internal fun oxideScaledTextStyle(base: TextStyle, factor: Float): TextStyle = base.copy(
    fontSize = base.fontSize.scaleIfSpecified(factor),
    lineHeight = base.lineHeight.scaleIfSpecified(factor),
    letterSpacing = base.letterSpacing.scaleIfSpecified(factor),
)

/** 未指定的字距不能参与算术，否则抛 IllegalArgumentException */
private fun TextUnit.scaleIfSpecified(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor