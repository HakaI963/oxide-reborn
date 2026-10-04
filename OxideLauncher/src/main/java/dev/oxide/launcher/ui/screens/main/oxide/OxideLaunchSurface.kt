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

import dev.oxide.launcher.coroutine.TaskStage
import kotlin.math.roundToInt

/**
 * 启动这一段的纯逻辑：显示哪一块表面、面板上写哪几个字段、进度怎么画、面板多大
 *
 * 这一段之所以全部抽成纯函数，是因为它恰好是**最容易看错**的一部分：
 * 「按 Play 之后屏幕上到底该有几样东西」如果散落在 Composable 的 `if` 分支里，
 * 就会出现三种同时在场的呈现——底下没被盖住的 Home、一块巨大的 "Starting"、
 * 以及一个浮在游戏画面上的旧提示条。判据收在这里之后，
 * 「同一次启动只有一种答案」就是可以被单测钉死的性质，而不是靠肉眼。
 *
 * 这个文件刻意不 import 任何 Compose 的东西（[OxideLaunchPanelGeometry] 只接收
 * 已经算好的 [OxideMetrics]），因此测试只需要 JUnit。
 */

// ---------------------------------------------------------------------------
// 哪一块表面
// ---------------------------------------------------------------------------

/**
 * 一次启动期间屏幕上可以出现的表面
 *
 * 只有三种，[Hidden] 之外的两种互相排斥：要么是等用户拿主意的 pre-flight，
 * 要么是跑起来的阶段列表。**没有第三种**——旧实现里"Home 还在、上面浮着一个
 * 旧通知"的叠层正是从这里来的。
 */
internal enum class OxideLaunchSurface {
    /** 没有人在启动：不画任何东西，Home 保持原样 */
    Hidden,

    /** 有一条检查没通过：面板上写清楚是哪一条，并给出那几个选择 */
    Preflight,

    /** 启动在跑：面板上列出真实的阶段与真实进度 */
    Progress,
}

/**
 * 这一次启动此刻该显示哪一块表面
 *
 * 判据是一条链：[oxideLaunchVisible] 先决定「要不要盖上来」，
 * 然后**流程在不在跑**决定「里面写的是什么」，最后 [oxidePreflightAsks]
 * 只在没有流程可报的时候才有机会把内容换成那几条要人拿主意的检查。
 * 三者都是纯函数，所以「没有任何一种输入会同时得到 Preflight 和 Progress」
 * 这件事在测试里是显然的。
 */
internal fun oxideLaunchSurface(
    flowActive: Boolean,
    operation: OxidePreflightOperation,
): OxideLaunchSurface = when {
    !oxideLaunchVisible(flowActive, operation) -> OxideLaunchSurface.Hidden
    // 流程在跑时阶段列表就是全部真相。operation 只在 pre-flight 那几帧里被写成
    // "要问用户"的那几条，而 [dev.oxide.launcher.viewmodel.LaunchGameViewModel] 在
    // 写入它之后立刻 cancel()，因此"流程还在跑 + 停在一条 pre-flight 上"只是
    // 一次赋值与一次清空之间的残帧。让它盖住阶段列表只会在那一帧里换掉整块内容，
    // 并且顺带把取消键也一起换掉（见 [oxideLaunchCancelSupported]）。
    flowActive -> OxideLaunchSurface.Progress
    oxidePreflightAsks(operation) -> OxideLaunchSurface.Preflight
    else -> OxideLaunchSurface.Progress
}

/**
 * 这一条是不是「要问用户」
 *
 * 没有实例与没有账号那两条在旧实现里只弹一次 toast 并立刻把 operation 复位，
 * 因此它们不该在面板上闪一行：那一帧只会让人以为还有别的事没做完。
 */
internal fun oxidePreflightAsks(operation: OxidePreflightOperation): Boolean {
    val branch = oxidePreflightBranchOf(operation) ?: return false
    return !oxidePreflightToastOnly(branch)
}

// ---------------------------------------------------------------------------
// 准备状态
// ---------------------------------------------------------------------------

/**
 * 启动此刻处在哪一段
 *
 * 这不是阶段标题，而是**准备状态**：面板上那一行小字说的就是它。
 * [Checking] 是"还没跑出任何阶段"（含按下 Play 到第一条阶段出现之间那段空窗），
 * [Preparing] / [Running] / [Handoff] 则由真实的阶段读数决定。
 */
internal enum class OxideLaunchPhase {
    /** 按下 Play 之后、前置检查正在跑，还没有得出结论 */
    Checking,

    /** 检查已通过，阶段已经排好但还没有任何一条真正开始 */
    Preparing,

    /** 至少有一条阶段在跑 */
    Running,

    /** 全部阶段已完成，游戏进程正在接手 */
    Handoff,
}

/**
 * 把 (operation, 阶段读数) 折成准备状态
 *
 * 判据的顺序就是"更靠前的那一段优先"：任何一条要人拿主意的检查都还是 Checking，
 * 因为那时阶段列表还可能被取消（凭据被拒与刷新失败都会 `cancel()` 掉整条流程）。
 *
 * [OxidePreflightOperation.RealLaunch] **不在**这份名单里：它是"检查全部通过、
 * 流程已经起步"，此后就由阶段读数说了算。它与 [OxidePreflightOperation.Idle]
 * （[dev.oxide.launcher.viewmodel.LaunchGameViewModel] 在启动流程的那一帧会把
 * operation 复位成 None，因此跑起来的整个过程里读到的都是 Idle）一样，
 * 阶段读数才是唯一的真相来源——把 RealLaunch 一律钉成 Checking 会让
 * "正在跑"与"已经交接"这两段在面板上永远看不见。
 */
internal fun oxideLaunchPhase(
    operation: OxidePreflightOperation,
    stages: List<OxideLaunchStageSnapshot>,
): OxideLaunchPhase {
    val running = stages.count { it.stage == TaskStage.RUNNING }
    val completed = stages.count { it.stage == TaskStage.COMPLETED }
    return when {
        operation == OxidePreflightOperation.Checking -> OxideLaunchPhase.Checking
        oxidePreflightAsks(operation) -> OxideLaunchPhase.Checking
        // 还没有任何一条阶段可读：按下 Play 到第一条阶段出现之间的那段空窗，
        // 以及 [OxidePreflightOperation.RealLaunch] 与第一条阶段之间的那一帧
        stages.isEmpty() -> OxideLaunchPhase.Checking
        running > 0 -> OxideLaunchPhase.Running
        completed >= stages.size -> OxideLaunchPhase.Handoff
        else -> OxideLaunchPhase.Preparing
    }
}

// ---------------------------------------------------------------------------
// 进度
// ---------------------------------------------------------------------------

/**
 * 一个阶段的读数
 *
 * 只抄 [dev.oxide.launcher.coroutine.Task] 真正公开的三样东西：阶段、进度、是否有速率。
 * 刻意不含标题与消息——那两个是 [dev.oxide.launcher.ui.AndroidStringText]，
 * 要 Context 才能翻译，因此留在 Composable 里读。
 */
internal data class OxideLaunchStageSnapshot(
    val id: String,
    val stage: TaskStage,
    /** 后端报出来的进度；负数代表"不确定" */
    val progress: Float,
)

/** 后端给不给得出确定的进度 */
internal enum class OxideLaunchProgress {
    /** 根本没有阶段可报 */
    None,

    /** 阶段在跑，但它自己说进度不确定 */
    Indeterminate,

    /** 阶段报了一个 0..1 的真进度 */
    Determinate,
}

/**
 * 把一个阶段报出来的进度读成三态
 *
 * 负数与 NaN 都被归到 [OxideLaunchProgress.Indeterminate] 而不是 0%：
 * 后端用 -1 表示"不确定"，把它当成 0 就会画出一根永远停在原地的进度条。
 * `null`（没有任何阶段）才是 [OxideLaunchProgress.None]，那种情况下整块面板不画条。
 */
internal fun oxideLaunchProgressOf(taskProgress: Float?): OxideLaunchProgress = when {
    taskProgress == null -> OxideLaunchProgress.None
    !taskProgress.isFinite() || taskProgress < 0f -> OxideLaunchProgress.Indeterminate
    else -> OxideLaunchProgress.Determinate
}

/** 面板上那根条到底画什么 */
internal sealed interface OxideLaunchPanelProgress {
    /** 不画条：既没有阶段，当前后端也没有给出任何确定进度 */
    data object None : OxideLaunchPanelProgress

    /** 当前阶段自己报了一个真进度，[fraction] 是 0..1，[percent] 是同一个数的整数写法 */
    data class Stage(val fraction: Float, val percent: Int) : OxideLaunchPanelProgress

    /** 后端没有给当前阶段的进度，退回"完成了几个阶段"这个同样真实的读数 */
    data class Stages(val completed: Int, val total: Int) : OxideLaunchPanelProgress {
        val fraction: Float
            get() = if (total <= 0) 0f else completed.coerceIn(0, total).toFloat() / total
    }
}

/**
 * 面板上那根条的取值
 *
 * 只有两种真实来源：当前阶段报出来的进度，或者已经完成的阶段数。
 * **没有第三种**——没有阶段、也没有任何确定进度时返回 [OxideLaunchPanelProgress.None]，
 * 于是界面上不画条，而不是画一根 0% 的条冒充进度。
 */
internal fun oxideLaunchPanelProgress(
    stages: List<OxideLaunchStageSnapshot>,
): OxideLaunchPanelProgress {
    if (stages.isEmpty()) return OxideLaunchPanelProgress.None

    // 当前阶段：优先正在跑的那条，其次是还没开始跑的那条（准备态）
    val current = stages.firstOrNull { it.stage == TaskStage.RUNNING }
        ?: stages.firstOrNull { it.stage == TaskStage.PREPARING }
    if (current != null && oxideLaunchProgressOf(current.progress) == OxideLaunchProgress.Determinate) {
        val fraction = current.progress.coerceIn(0f, 1f)
        return OxideLaunchPanelProgress.Stage(fraction = fraction, percent = (fraction * 100f).roundToInt())
    }

    val completed = stages.count { it.stage == TaskStage.COMPLETED }
    return OxideLaunchPanelProgress.Stages(completed = completed, total = stages.size)
}

// ---------------------------------------------------------------------------
// 取消
// ---------------------------------------------------------------------------

/**
 * 取消按钮该不该出现
 *
 * 只有真的有一条启动流程在跑时才出现：那时 [dev.oxide.launcher.viewmodel.LaunchGameViewModel.cancel]
 * 有东西可取消。仍在检查、或停在某条 pre-flight 上时 `flow` 是空的，按下去什么也不会发生，
 * 那种情况下要放弃走的是那一行自己的"取消"选择（见 [OxideLaunchPreflightAction.Abort]），
 * 因此这里返回 false，面板上也就不会出现一个按了没反应的第二条退路。
 */
internal fun oxideLaunchCancelSupported(
    flowActive: Boolean,
    operation: OxidePreflightOperation,
): Boolean = flowActive && oxideLaunchSurface(flowActive, operation) == OxideLaunchSurface.Progress

// ---------------------------------------------------------------------------
// 面板几何
// ---------------------------------------------------------------------------

/** 面板宽度相对卡片最小宽度的倍数，落在"紧凑/中等"这一档而不是铺满整屏 */
private const val OXIDE_LAUNCH_PANEL_WIDTH_FACTOR = 1.8f

/** 面板高度相对卡片最小宽度的倍数，给一块居中的板而不是一整列 */
private const val OXIDE_LAUNCH_PANEL_HEIGHT_FACTOR = 1.3f

/** 面板至少要装下几行导航项那么高，否则连一行阶段加标题都放不下 */
private const val OXIDE_LAUNCH_PANEL_MIN_ROWS = 5f

/** 阶段列表那一块占面板高度的比例 */
private const val OXIDE_LAUNCH_STAGE_VIEWPORT_FRACTION = 0.42f

/**
 * 面板的几何，全部由 [OxideMetrics] 推导
 *
 * [maxVisibleStageRows] 是给滚动区兜底的行数：界面上用它决定"这块列表值得占多高"，
 * 测试里则用来证明**放大界面也不会把列表压到 0 行**（否则最后一个阶段会看不见）。
 */
internal data class OxideLaunchPanelGeometry(
    val marginDp: Int,
    val widthDp: Int,
    val heightDp: Int,
    val stageViewportHeightDp: Int,
    val maxVisibleStageRows: Int,
) {
    /** 面板连同边距是否真的放得进可用区域；放大界面时这一条是硬约束 */
    fun fitsWithin(availableWidthDp: Int, availableHeightDp: Int): Boolean =
        widthDp + marginDp * 2 <= availableWidthDp && heightDp + marginDp * 2 <= availableHeightDp
}

/**
 * 居中面板该有多大
 *
 * 两条硬约束按顺序落：
 * 1. 面板加上边距必须放得进可用区域——**先**按可用区域夹一次，
 *    这样用户把界面放大到 150% 时它会变窄来适应，而不是被裁掉半截；
 * 2. 宽高都还有"紧凑/中等"的上限（相对卡片最小宽度），免得大屏上摊成一大张空板。
 *
 * 高度用**固定值**而不是 `heightIn`：面板里有一个可滚动的阶段列表，
 * 一旦高度不封顶，那一层就会被以 `maxHeight = Infinity` 测量，
 * 于是 `verticalScroll` 抛 `Vertically scrollable component was measured with an infinity maximum height`。
 */
internal fun oxideLaunchPanelGeometry(
    metrics: OxideMetrics,
    availableWidthDp: Int,
    availableHeightDp: Int,
): OxideLaunchPanelGeometry {
    val margin = metrics.pagePaddingV.value.roundToInt().coerceAtLeast(0)

    // 先按可用区域夹：放大之后它只会变小，不会顶出屏幕
    val width = (metrics.cardMinWidth.value * OXIDE_LAUNCH_PANEL_WIDTH_FACTOR)
        .roundToInt()
        .coerceAtMost(availableWidthDp - margin * 2)
        .coerceAtLeast(1)

    // 纵向同上：先按可用高度夹，再被"紧凑/中等"的上限收窄，
    // 最后用最小高度托底——因此窗口越矮面板越小，但永远装得下标题与一行阶段
    val room = (availableHeightDp - margin * 2).coerceAtLeast(1)
    val preferredHeight = (metrics.cardMinWidth.value * OXIDE_LAUNCH_PANEL_HEIGHT_FACTOR).roundToInt()
    val minHeight = (metrics.navItemHeight.value * OXIDE_LAUNCH_PANEL_MIN_ROWS)
        .roundToInt().coerceAtLeast(1)
    val height = minOf(room, maxOf(minHeight, preferredHeight)).coerceIn(1, room)

    val viewport = (height * OXIDE_LAUNCH_STAGE_VIEWPORT_FRACTION)
        .roundToInt().coerceAtLeast(1)
    val rowHeight = metrics.secControlHeight.value.roundToInt().coerceAtLeast(1)

    return OxideLaunchPanelGeometry(
        marginDp = margin,
        widthDp = width,
        heightDp = height,
        stageViewportHeightDp = viewport,
        // 至少一行：放大到极端档位时列表区域会变得很矮，但仍然要看得见当前那一条
        maxVisibleStageRows = (viewport / rowHeight).coerceAtLeast(1),
    )
}