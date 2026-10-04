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
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import dev.oxide.launcher.ui.screens.main.oxide.OxideGuiScaleDefaultPercent
import dev.oxide.launcher.ui.screens.main.oxide.oxideGuiScaleFactor

/**
 * 游戏内浮层的纯逻辑
 *
 * 这个文件里没有任何 Composable，也不读任何设置、也不碰 Android：
 * 面板几何、帧率轴、读数的出现条件与滚动高度策略都是纯函数，
 * 因此可以在不启动 Compose 的情况下逐条钉死（见 `src/test/java/dev/oxide/launcher/ui/screens/game/`）。
 *
 * 为什么必须单独抽出来，这三块浮层全都盖在一块**正在运行的游戏**上：
 *
 * - 游戏窗口可以非常小（分屏、自由窗口、自定义分辨率加黑边），
 *   启动器那套 640x360 的下限在这里完全不成立。把"窗口多大 → 面板多大"写成
 *   纯函数，面板才不会拿着写死的 dp 去裁一块很小的窗口。
 * - 滚动区的高度**先**被夹住、再在里面滚。顺序反过来就是 v1.5.0 那个 P0 崩溃：
 *   一个几百行的日志把弹窗顶到屏幕外面去。
 * - 帧率图每秒重画一次，因此每一条都会跑到的东西都必须先被钉住：
 *   纵轴区间绝不为空（除到零就会画出一堆 NaN），刻度严格递增。
 */

/** 面板与窗口边缘之间留出的空白，占窗口最短边的比例 */
const val GameOverlayEdgeMarginFraction: Float = 0.05f

/** 面板与窗口边缘留白的夹紧区间（未乘界面缩放，单位 dp） */
const val GameOverlayEdgeMarginMin: Float = 6f
const val GameOverlayEdgeMarginMax: Float = 16f

/** 面板的宽度上限（未乘界面缩放，单位 dp）。对话框不是页面，再宽也不超过它 */
const val GameOverlayPanelMaxWidth: Float = 420f

/** 面板的高度上限（未乘界面缩放，单位 dp） */
const val GameOverlayPanelMaxHeight: Float = 520f

/** 标题栏与底部按钮栏这两块**固定**高度之和（未乘界面缩放，单位 dp） */
const val GameOverlayChromeHeight: Float = 92f

/** 滚动内容区的高度下限：说明文字与按钮永远看得见，也因此不会有 0 高的列表 */
const val GameOverlayMinContentHeight: Float = 48f

/**
 * 退化窗口（容器量到 0，也就是面板刚创建还没布局的那一帧）的兜底值
 *
 * 0 高的面板会把标题与按钮一起量成 0，读屏与触摸目标检查也跟着失效，
 * 因此宁可给 1dp。
 */
val GameOverlayAbsoluteMin: Dp = 1.dp

/** 弹窗里可点的动作至少这么高。再小也还是能按到，而不必瞄准 4dp 的字 */
val GameOverlayMinTouchTarget: Dp = 40.dp

/**
 * 一组已经算好的游戏内浮层尺寸
 *
 * 全部由**真实窗口尺寸**推导，浮层里不写死任何一个与窗口有关的 dp。
 * 乘过界面缩放的只有与窗口无关的那一半（行距、按钮高），
 * 面板本身始终被夹在窗口以内——放大到 150% 时面板会贴住边，而不是溢出屏幕。
 */
@Immutable
data class GameOverlayBounds(
    /** 面板与窗口四边的距离 */
    val edgeMargin: Dp,
    /** 面板宽度上限 */
    val panelMaxWidth: Dp,
    /** 面板高度上限 */
    val panelMaxHeight: Dp,
    /** 标题栏与按钮栏的固定高度之和，不参与滚动 */
    val chromeHeight: Dp,
    /** 滚动内容区的高度上限。滚动必须用它夹住自己，而不是反过来 */
    val contentMaxHeight: Dp,
    /** 面板内边距，跟着窗口变小 */
    val padding: Dp,
    /** 元素之间的间距 */
    val rowGap: Dp,
    /** 动作行与按钮的最小高度 */
    val buttonHeight: Dp,
    val guiScale: Float,
) {
    /**
     * 面板实际用掉的高度
     *
     * 恒等于 `max(panelMaxHeight, minContent + chrome)`：
     * 正常窗口下就是面板上限；窗口矮到连标题栏加按钮栏都装不下时，
     * 由内容区那个下限把总高顶起来，代价是内容区会被面板裁掉一截——
     * 但它绝不会变成 0，因此列表至少还能画出一项、还能滚。
     */
    val totalHeight: Dp get() = chromeHeight + contentMaxHeight
}

/**
 * 由真实窗口尺寸算出游戏内面板的尺寸
 *
 * 纯函数：不读 [android.content.Context]，也不碰组合期状态。
 *
 * [windowWidthDp] / [windowHeightDp] 必须是这块面板**真正能占**的范围，
 * 单位 dp。对弹窗来说那不是显示器的尺寸，而是承载游戏的那个窗口：
 * 分屏与自由窗口下它可能只有屏幕的一小块，而弹窗自己的窗口仍然是满屏的。
 *
 * [guiScalePercent] 是用户的界面缩放，与启动器共用同一个系数
 * （见 `oxideGuiScaleFactor`），于是浮层里的框和字永远是同一个比例。
 *
 * 三条不变式，逐条都在 `GameOverlayLogicTest` 里断言：
 *
 * 1. 面板加左右留白**永远不超过窗口**；
 * 2. 每个数都是正的、有限的——`coerceIn` 在上下界颠倒时不会抛异常，
 *    但也不会换掉那个值，于是 NaN 会一路传到 `heightIn`，在布局阶段崩掉；
 * 3. 内容区**先**被夹住，因此它恒不小于 [GameOverlayMinContentHeight]。
 */
fun gameOverlayBoundsFor(
    windowWidthDp: Int,
    windowHeightDp: Int,
    guiScalePercent: Int = OxideGuiScaleDefaultPercent,
): GameOverlayBounds {
    val scale = oxideGuiScaleFactor(guiScalePercent)
    val width = windowWidthDp.coerceAtLeast(0).dp
    val height = windowHeightDp.coerceAtLeast(0).dp

    // 退化窗口：所有值都取兜底常量，仍然是正的、有限的
    if (width <= 0.dp || height <= 0.dp) {
        return GameOverlayBounds(
            edgeMargin = GameOverlayAbsoluteMin,
            panelMaxWidth = GameOverlayAbsoluteMin,
            panelMaxHeight = GameOverlayAbsoluteMin,
            chromeHeight = 0.dp,
            contentMaxHeight = GameOverlayMinContentHeight.dp,
            padding = 0.dp,
            rowGap = 0.dp,
            buttonHeight = GameOverlayAbsoluteMin,
            guiScale = scale,
        )
    }

    // 留白跟着窗口的最短边收缩：很小的窗口里写死 16dp 会把内容挤没
    val edgeMargin =
        (minOf(width.value, height.value) * GameOverlayEdgeMarginFraction)
            .coerceIn(GameOverlayEdgeMarginMin, GameOverlayEdgeMarginMax).dp * scale

    // 面板永远不超过"窗口减去四周留白"，与理想上限取小
    val availableWidth = (width - edgeMargin * 2f).coerceAtLeast(GameOverlayAbsoluteMin)
    val availableHeight = (height - edgeMargin * 2f).coerceAtLeast(GameOverlayAbsoluteMin)
    val panelMaxWidth = minOf(GameOverlayPanelMaxWidth.dp * scale, availableWidth)
    val panelMaxHeight = minOf(GameOverlayPanelMaxHeight.dp * scale, availableHeight)

    val chromeHeight = GameOverlayChromeHeight.dp * scale
    // 先减、再兜底：内容区被压到 0 时，LazyColumn 连一项都不会画，更谈不上滚动
    val contentMaxHeight =
        (panelMaxHeight - chromeHeight).coerceAtLeast(GameOverlayMinContentHeight.dp * scale)

    // 与窗口无关的那一半才乘界面缩放
    val pad = (minOf(width.value, 400f) * 0.03f).coerceIn(7f, 13f).dp * scale
    val rowGap = (pad * 0.6f).coerceAtLeast(2f.dp * scale)
    val buttonHeight = GameOverlayMinTouchTarget * scale

    return GameOverlayBounds(
        edgeMargin = edgeMargin,
        panelMaxWidth = panelMaxWidth,
        panelMaxHeight = panelMaxHeight,
        chromeHeight = chromeHeight,
        contentMaxHeight = contentMaxHeight,
        padding = pad,
        rowGap = rowGap,
        buttonHeight = buttonHeight,
        guiScale = scale,
    )
}

// ---------------------------------------------------------------------------
// 有界滚动
//
// 顺序就是这里全部的意义：**先夹高度，再在里面滚**。
// 一个几百行的日志或一份很长的目录清单，如果先滚后夹（或者根本不夹），
// 弹窗就会被顶到屏幕外面去，而那正是 v1.5.0 的 P0 崩溃。
// ---------------------------------------------------------------------------

/** 一个能当作高度上限的数：正的、有限的，不是 [Dp.Infinity] 也不是 [Dp.Unspecified] */
private fun Dp.isUsableBound(): Boolean =
    this > 0.dp && this != Dp.Infinity && this != Dp.Unspecified

/** 一个能参与比较的数（0 合法：空列表的内容区就是 0 高） */
private fun Dp.isFiniteNumber(): Boolean =
    this >= 0.dp && this != Dp.Infinity && this != Dp.Unspecified

/**
 * 滚动区实际分配到的高度：自然高度与上限取小
 *
 * 上限本身也会先被兜住（未指定或非正 → [GameOverlayAbsoluteMin]），
 * 因此返回值恒为有限值，`verticalScroll` 永远不会拿到 [Dp.Infinity]。
 *
 * 纯函数，可直接单测。
 */
fun gameOverlayScrollHeight(naturalHeight: Dp, maxHeight: Dp): Dp {
    val cap = if (maxHeight.isUsableBound()) maxHeight else GameOverlayAbsoluteMin
    val natural = if (naturalHeight.isFiniteNumber()) naturalHeight else cap
    return natural.coerceIn(0.dp, cap)
}

/** N 行需要的自然高度 */
fun gameOverlayListContentHeight(itemCount: Int, rowHeight: Dp): Dp =
    rowHeight * itemCount.coerceAtLeast(0)

/**
 * 列表实际分配到的高度：N 行的高度与内容区上限取小
 *
 * 这就是"有界滚动"本身——项目再多，内容区也不会超过上限，
 * 多出来的部分在里面滚掉，而不是把面板撑高。
 */
fun gameOverlayListHeight(itemCount: Int, maxHeight: Dp, rowHeight: Dp): Dp =
    gameOverlayScrollHeight(gameOverlayListContentHeight(itemCount, rowHeight), maxHeight)

/**
 * 这个列表会不会溢出、也就是需不需要挂滚动
 *
 * 定义成"分到的高度比自然高度小"，而不是直接比自然高度和上限：
 * 那样的话上限退化时也会被算成溢出，于是一个只有一行的列表也挂上一条滚动条。
 */
fun gameOverlayListOverflows(itemCount: Int, maxHeight: Dp, rowHeight: Dp): Boolean =
    gameOverlayListHeight(itemCount, maxHeight, rowHeight) <
        gameOverlayListContentHeight(itemCount, rowHeight)

// ---------------------------------------------------------------------------
// 帧率图
// ---------------------------------------------------------------------------

/** 纵轴分几段 */
const val GameFpsAxisSegments: Int = 5

/**
 * 纵轴刻度步长的候选"好数"，贴合帧率语境（1/2/5/10/15/20/30/60/120/240…）
 *
 * 私有的：调用点只应该要区间与刻度，不该自己挑步长。
 */
private val GAME_FPS_AXIS_STEPS = intArrayOf(1, 2, 5, 10, 15, 20, 30, 60, 120, 240)

/** 帧率图的纵轴区间 */
@Immutable
data class GameFpsAxis(val min: Int, val max: Int) {
    /** 轴的跨度。恒 ≥ [GameFpsAxisSegments]，因此除以段数不会得到 0 */
    val span: Int get() = max - min

    /** 帧率落在轴上的比例，区间外会被夹住 */
    fun fraction(fps: Int): Float =
        ((fps - min).toFloat() / span).coerceIn(0f, 1f)
}

/**
 * 纵轴的"好数"范围：均分 [GameFpsAxisSegments] 段后每个刻度都落在规整数字上，
 * 完整覆盖实际帧率范围，两端尽量对称地留出余量，且最低刻度不小于 0
 *
 * 与改造前逐条一致，只多了一条保证：**区间永远非空**。
 * 帧率跨度超过 1200 时没有候选步长能覆盖，原来会直接返回真实区间，
 * 而 fpsMin == fpsMax 时那就是一个宽度为 0 的区间——除到零会画出一整屏 NaN。
 *
 * 纯函数，可直接单测。
 */
fun gameFpsAxis(fpsMin: Int, fpsMax: Int): GameFpsAxis {
    val low = minOf(fpsMin, fpsMax).coerceAtLeast(0)
    val high = maxOf(fpsMin, fpsMax)
    val range = (high - low).coerceAtLeast(0)
    for (step in GAME_FPS_AXIS_STEPS) {
        val span = step * GameFpsAxisSegments
        if (span < range) continue
        val extra = span - range
        // 向下取整到步长网格（余量为负时向负无穷取整）
        val raw = low - extra / 2
        var axisMin = raw / step * step
        if (raw < 0 && raw % step != 0) axisMin -= step
        var axisMax = axisMin + span
        if (axisMax < high) {
            axisMax = (high + step - 1) / step * step
            axisMin = axisMax - span
        }
        if (axisMin < 0) {
            axisMin = 0
            axisMax = span
        }
        return GameFpsAxis(axisMin, axisMax)
    }
    // 没有候选步长能覆盖（跨度超过 240×5）：仍然按段数等分，因此刻度依然落在轴上。
    // 不再直接返回真实区间——那正是宽度可能为 0 的那一条路。
    val span = (range.coerceAtLeast(GameFpsAxisSegments) + GameFpsAxisSegments - 1) /
        GameFpsAxisSegments * GameFpsAxisSegments
    return GameFpsAxis(low, low + span)
}

/**
 * 纵轴刻度：含两端，从下往上递增，最后一个正好是轴的上界
 *
 * 步长是 `span / 段数`，而 [GameFpsAxis.span] 恒 ≥ 段数，所以它恒 ≥ 1——
 * 刻度因此严格递增，不会挤成一摞相同的数字。
 *
 * 纯函数，可直接单测。
 */
fun gameFpsAxisTicks(axis: GameFpsAxis): List<Int> {
    val step = (axis.span / GameFpsAxisSegments).coerceAtLeast(1)
    return (0..GameFpsAxisSegments).map { axis.min + step * it }
}

/** 帧率图有没有东西可画：没有历史点时只画网格，不画曲线，也不画当前帧率标注 */
fun fpsChartHasSeries(history: List<Int>): Boolean = history.isNotEmpty()

// ---------------------------------------------------------------------------
// 悬浮球上的读数
// ---------------------------------------------------------------------------

/** 悬浮球上这一帧到底出现哪几块 */
@Immutable
data class GameOverlayBallReadouts(
    /** 帧率读数出现 */
    val showFps: Boolean,
    /** 帧率以折线图出现而不是数字 */
    val showFpsChart: Boolean,
    /** 内存条出现 */
    val showMemory: Boolean,
    /** 菜单图标出现 */
    val showMenuIcon: Boolean,
) {
    /** 球上真的有读数；false 时只剩菜单图标 */
    val hasReadout: Boolean get() = showFps || showMemory
}

/**
 * 悬浮球的显示模式 → 哪几块出现
 *
 * 与改造前的判断逐条对应：
 *
 * - `gameFps` 为 null（帧率捕获没开或刚关掉）时**没有**帧率读数；
 * - 数字模式与图表模式都算帧率读数，区别只在画成什么；
 * - 图表与内存条本身就把球撑满了，于是菜单图标让位——图标在时球才只有一个
 *   图标那么宽，图表模式下再挂一个图标只会把那块 180dp 的图挤到屏幕外。
 *
 * [gameFps] 的值本身**不参与**判断：这里只决定显示与否，一个 0 帧照样显示，
 * 因为 0 是真实的读数，藏起来只会让人以为仪表坏了。
 *
 * 纯函数，可直接单测。
 */
fun gameBallReadouts(
    gameFps: Int?,
    fpsDisplayMode: FpsDisplayMode,
    showMemory: Boolean,
): GameOverlayBallReadouts {
    val showFps = gameFps != null
    val showFpsChart = showFps && fpsDisplayMode == FpsDisplayMode.CHART
    return GameOverlayBallReadouts(
        showFps = showFps,
        showFpsChart = showFpsChart,
        showMemory = showMemory,
        showMenuIcon = !showMemory && !showFpsChart,
    )
}

/**
 * 把内存读数写成 `已用MB/总MB`
 *
 * 自己拼字符串，不走 `String.format`：这个字符串每秒重算一次。
 * 负数按 0 处理——系统读数偶尔会在回收的瞬间给出负的差值。
 */
fun formatGameOverlayMemory(usedMb: Int, totalMb: Int): String =
    "${usedMb.coerceAtLeast(0)}MB/${totalMb.coerceAtLeast(0)}MB"

/**
 * 已用内存占总量的比例
 *
 * 总量读不出来（0）时返回 0，而不是除到 NaN——画条时 NaN 会让整块图消失。
 */
fun gameOverlayMemoryFraction(usedMb: Int, totalMb: Int): Float {
    if (totalMb <= 0) return 0f
    return (usedMb.coerceAtLeast(0).toFloat() / totalMb.toFloat()).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------
// 多人联机对话框底栏
// ---------------------------------------------------------------------------

/** 多人联机对话框底栏那个「日志 / 刷新」按钮现在的样子 */
@Immutable
data class GameOverlayLogToggle(
    /** 文字是"刷新"（已经开着日志）而不是"日志" */
    val isRefresh: Boolean,
    /** 正在收集日志时为 false：这时点它什么也不会发生 */
    val enabled: Boolean,
)

/**
 * 多人联机对话框底栏的日志按钮
 *
 * 与改造前的 `TextButton(enabled = logOperation !is CollectingLog)` 完全一致：
 * 已经在看日志时文字换成"刷新"，收集期间整个按钮不可点。
 * 把它写成纯函数，是为了"收集期间不可点"这条规则不会在某次改版里被顺手改掉。
 *
 * 纯函数，可直接单测。
 */
fun multiplayerLogToggle(
    showingLog: Boolean,
    collectingLog: Boolean,
): GameOverlayLogToggle = GameOverlayLogToggle(
    isRefresh = showingLog,
    enabled = !collectingLog,
)