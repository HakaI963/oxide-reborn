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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.setting.enums.ResolutionRule
import dev.oxide.launcher.ui.screens.main.oxide.OxideGuiScaleDefaultPercent
import dev.oxide.launcher.ui.screens.main.oxide.oxideGuiScaleFactor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/**
 * 游戏内菜单的纯逻辑
 *
 * 这个文件里没有任何 Composable，也不读任何设置：菜单的版面几何、比例换算、
 * 数字解析与可见性判断都是纯函数，因此可以在不启动 Compose、不碰 Android 的
 * 情况下逐条钉死（见 `src/test/java/dev/oxide/launcher/ui/screens/game/`）。
 *
 * 为什么必须单独抽出来：菜单画在**正在运行的游戏**上，它的面板尺寸只能来自
 * 真实窗口——启动器那套 640x360 的下限在这里不成立，窗口可能只有几百像素宽。
 * 把"窗口多大 → 面板多大"写成纯函数之后，这份几何既能单测，也能保证菜单不会
 * 拿着写死的 dp 去裁切一块很小的窗口。
 */

// ---------------------------------------------------------------------------
// 分区
// ---------------------------------------------------------------------------

/**
 * 菜单的分区
 *
 * 顺序就是分区栏从左到右的顺序，索引也是 `GameScreen` 里那个 `mutableIntStateOf`
 * 存的值：0 号是游戏本体（动作、悬浮窗与分辨率），其余五块是控制。
 */
enum class GameMenuSection {
    Game,
    Controls,
    Mouse,
    Gamepad,
    Gestures,
    Gyroscope,
    ;

    companion object {
        /** 分区总数，索引越界时退回 [Game] 而不是崩 */
        val count: Int get() = entries.size

        /** 把外部索引夹成合法分区 */
        fun fromIndex(index: Int): GameMenuSection = entries.getOrElse(index) { Game }
    }
}

/** 百分比规则下才需要那条比例滑杆 */
fun gameMenuShowsResolutionScale(rule: ResolutionRule): Boolean = rule == ResolutionRule.PERCENTAGE

/** 自定义规则下才需要宽高两个输入框 */
fun gameMenuShowsCustomResolution(rule: ResolutionRule): Boolean = rule == ResolutionRule.CUSTOM

// ---------------------------------------------------------------------------
// 版面几何
// ---------------------------------------------------------------------------

/** 面板宽度占窗口的比例 */
const val GameMenuPanelWidthFraction: Float = 0.62f

/** 面板宽度夹紧区间（未乘界面缩放，单位 dp） */
const val GameMenuPanelMinWidth: Float = 220f
const val GameMenuPanelMaxWidth: Float = 340f

/** 边距占窗口最短边的比例 */
const val GameMenuEdgeMarginFraction: Float = 0.035f

/** 边距夹紧区间（未乘界面缩放，单位 dp） */
const val GameMenuEdgeMarginMin: Float = 6f
const val GameMenuEdgeMarginMax: Float = 18f

/** 面板高度的下限：窗口极矮时也不会塌成一条缝 */
const val GameMenuPanelMinHeight: Float = 72f

/** 选项就地铺开时最多几行，超过就只给这一块自己的滚动 */
const val GameMenuInlineOptionRows: Int = 6

/**
 * 一组已经算好的菜单尺寸
 *
 * 全部由**真实窗口尺寸**推导，菜单里不写死任何一个与窗口有关的 dp。
 * 乘过 [guiScale] 的只有与窗口无关的那一半（行高、轨道粗细），
 * 面板本身始终被夹在窗口以内——放大到 150% 时面板会贴住边，而不是溢出屏幕。
 */
@Immutable
data class GameMenuMetrics(
    /** 面板与窗口四边的距离 */
    val edgeMargin: Dp,
    val panelWidth: Dp,
    val panelHeight: Dp,
    /** 分区栏与行高，只与界面缩放有关 */
    val controlHeight: Dp,
    /** 内容区四周内边距，跟随窗口变小 */
    val contentPadding: Dp,
    /** 就地展开的选项列表最多占多高，超过就自己滚动 */
    val optionListMaxHeight: Dp,
    val guiScale: Float,
) {
    /** 面板内一行的最小高度，选项行与标签行都用它对齐 */
    val rowHeight: Dp get() = controlHeight
}

/**
 * 由真实窗口尺寸算出菜单尺寸
 *
 * 纯函数：不读 [android.content.Context]，也不碰组合期状态。
 *
 * [windowWidthPx] / [windowHeightPx] 必须是**测量出来的**窗口像素尺寸，
 * 而不是 `displayMetrics`：分屏、折叠屏与横竖屏切换时前者会变而后者常常不变，
 * 菜单必须跟着前者走，否则一块很窄的窗口就会被写死的 dp 顶出裁切。
 *
 * [guiScalePercent] 是用户的界面缩放，与启动器共用同一个系数（见 `oxideGuiScaleFactor`），
 * 于是菜单里的框和字永远是同一个比例。
 */
fun gameMenuMetricsFor(
    windowWidthPx: Int,
    windowHeightPx: Int,
    density: Float,
    guiScalePercent: Int = OxideGuiScaleDefaultPercent,
): GameMenuMetrics {
    val scale = oxideGuiScaleFactor(guiScalePercent)
    val safeDensity = if (density > 0f) density else 1f
    val widthDp = (windowWidthPx / safeDensity).coerceAtLeast(1f).dp
    val heightDp = (windowHeightPx / safeDensity).coerceAtLeast(1f).dp

    // 边距跟着窗口的最短边收缩：很小的窗口里写死 18dp 会把内容挤没
    val edgeMargin = (min(widthDp.value, heightDp.value) * GameMenuEdgeMarginFraction)
        .coerceIn(GameMenuEdgeMarginMin, GameMenuEdgeMarginMax).dp * scale

    // 宽度先按比例给，再夹进夹紧区间，最后无论如何都不超过窗口
    val panelWidth = (
        (widthDp.value * GameMenuPanelWidthFraction)
            .coerceIn(GameMenuPanelMinWidth, GameMenuPanelMaxWidth) * scale
        ).dp.coerceAtMost((widthDp - edgeMargin * 2f).coerceAtLeast(1.dp))

    // 高度同理：窗口特别矮时不能出现"下限比上限还大"的区间，
    // 那会让 coerceIn 直接抛异常，于是面板反而在最小窗口上打不开
    val availableHeight = (heightDp - edgeMargin * 2f).coerceAtLeast(1.dp)
    val panelHeight = availableHeight.coerceAtLeast(GameMenuPanelMinHeight.dp.coerceAtMost(availableHeight))

    val controlHeight = 26.dp * scale
    val contentPadding = (min(widthDp.value, 360f) * 0.03f).coerceIn(7f, 12f).dp

    return GameMenuMetrics(
        edgeMargin = edgeMargin,
        panelWidth = panelWidth,
        panelHeight = panelHeight,
        controlHeight = controlHeight,
        contentPadding = contentPadding,
        // 选项列表最多占面板高度的 45%，至少装得下三行
        optionListMaxHeight = (panelHeight * 0.45f).coerceAtLeast(controlHeight * 3f),
        guiScale = scale,
    )
}

// ---------------------------------------------------------------------------
// 滑杆
// ---------------------------------------------------------------------------

/** 归一化比例：值在区间里的位置，区间退化成一点时返回 0 */
fun gameMenuSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/** 反算：归一化比例回到值，比例被夹在 0..1 */
fun gameMenuSliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return range.start + fraction.coerceIn(0f, 1f) * span
}

/** 选项就地铺开还是自己滚动 */
fun gameMenuChoiceUsesOwnScroll(
    itemCount: Int,
    maxInlineRows: Int = GameMenuInlineOptionRows,
): Boolean = itemCount > maxInlineRows

// ---------------------------------------------------------------------------
// 数字
// ---------------------------------------------------------------------------

/** 行内数字输入框可能给出的三种错误 */
enum class GameMenuNumberError { NotANumber, TooSmall, TooLarge }

/**
 * 解析行内数字输入的文本
 *
 * [integerOnly] 为真时只接受整数（分辨率宽高、毫秒数都是整数）；
 * 返回 null 表示文本为空、不是数字或越界，此时**不允许提交**——
 * 这与旧的 `SliderValueEditDialog` 的做法一致：非法的输入留在原地等人改。
 */
fun gameMenuNumberIn(
    text: String,
    range: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
): Float? {
    val parsed = (if (integerOnly) text.toIntOrNull()?.toFloat() else text.toFloatOrNull())
        ?: return null
    if (parsed.isNaN()) return null
    if (parsed < range.start) return null
    if (parsed > range.endInclusive) return null
    return parsed
}

/** [gameMenuNumberIn] 的错误分类，供行内输入框显示提示 */
fun gameMenuNumberError(
    text: String,
    range: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
): GameMenuNumberError? {
    val parsed = (if (integerOnly) text.toIntOrNull()?.toFloat() else text.toFloatOrNull())
        ?: return GameMenuNumberError.NotANumber
    if (parsed.isNaN()) return GameMenuNumberError.NotANumber
    if (parsed < range.start) return GameMenuNumberError.TooSmall
    if (parsed > range.endInclusive) return GameMenuNumberError.TooLarge
    return null
}

/**
 * 把滑杆或输入框里的值拼成要显示的文本
 *
 * 自己按十进制逐位拼，不走 [java.text.DecimalFormat] 也不走 [String.format]：
 * 后两者每次都要构造格式化器，而拖动滑杆时这个函数每帧都会被调到。
 * [decimals] 为 0 时就是原来的 `"$value$suffix"` 整数显示。
 */
fun formatGameMenuValue(value: Float, suffix: String?, decimals: Int = 0): String {
    if (decimals <= 0) {
        return value.roundToInt().toString() + suffix.orEmpty()
    }
    return formatFixed(value, decimals) + suffix.orEmpty()
}

/** 小数部分逐位拼出来的定点文本，负数带负号 */
private fun formatFixed(value: Float, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / factor
    val fraction = (magnitude % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}