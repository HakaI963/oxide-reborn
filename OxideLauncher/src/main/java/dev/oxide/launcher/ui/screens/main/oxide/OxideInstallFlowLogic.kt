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

import kotlin.math.roundToInt

/**
 * 「装一个版本」向导的纯逻辑
 *
 * 这个文件里没有 Compose、没有 Android API、也不读任何状态源：它只回答三件事——
 * 现在在哪一步、这一步能不能往前走、面板该画多大，以及后端报出来的进度该怎么显示。
 *
 * 之所以把它们从界面里抽出来，是因为这四件事恰恰是最容易改坏、又最不该靠肉眼看的地方：
 * 步骤可达性决定了用户会不会被卡在一条走不通的路上，面板尺寸决定了会不会出现大片空白，
 * 而进度一旦被"补"出一个假的百分比，用户看到的就再也不是真实下载状态了。
 * 抽出来之后它们可以在没有 Android 的单元测试里逐条钉死。
 */

// ---------------------------------------------------------------------------
// 步骤模型
// ---------------------------------------------------------------------------

/** 一步在某一刻的呈现状态。四种都画出来，也都念给读屏软件，不靠颜色区分 */
internal enum class OxideInstallStepState {
    /** 还没走到 */
    Pending,

    /** 正在这一步 */
    Active,

    /** 已经走完 */
    Done,

    /** 走不到：前面的前提还不成立 */
    Blocked,
}

/**
 * 三步向导在某一刻的真实状态
 *
 * 每一个字段都必须来自真实来源：选了哪个 Minecraft 版本来自选择回调，
 * 加载器列表到没到来自 [dev.oxide.launcher.ui.screens.content.download.game.AddonState]，
 * 装没装上来自安装器的回调。没有任何一项是从"界面上看起来像"推出来的。
 *
 * 刻意**不**收"选了几个加载器"与"版本名可不可用"：这两件事只决定第三步里那一个
 * 安装按钮亮不亮，不决定步骤本身能不能走。收进来只会让人误以为它们也参与可达性判定。
 */
internal data class OxideInstallFlowState(
    /** 已选中的 Minecraft 版本，空表示还没选 */
    val gameVersion: String? = null,
    /** 当前停在哪一步 */
    val step: OxideInstallStep = OxideInstallStep.Version,
    /** 加载器列表是否已经按当前 Minecraft 版本拉到 */
    val supportsLoaded: Boolean = false,
    /** 安装器是否正在跑 */
    val installing: Boolean = false,
    /** 这一次安装是否已经成功落盘 */
    val succeeded: Boolean = false,
) {
    /** 有没有一个真的 Minecraft 版本。后面的每一步都从这一条推出来 */
    val hasVersion: Boolean get() = !gameVersion.isNullOrBlank()
}

/**
 * 三步在某一刻各自的呈现状态
 *
 * 「当前这一步」永远优先报 Active：用户退回来改加载器时，那一步就该再次变成进行中，
 * 而不是留着上一轮的 Done。第一步同样如此——选好版本之后 `step` 立刻前进到第二步，
 * 于是「有版本且 `step` 又是 Version」只可能是用户自己退回来了，那一步就该是 Active，
 * 而不是提前打上勾。（此前第一步把 `hasVersion` 排在 `step == Version` 前面，
 * 于是退回来改版本时第一步留着上一轮的 ✓，与第二步的规则也自相矛盾。）
 *
 * Blocked 优先于其余三种——"还没有版本所以根本走不到"
 * 这件事必须看得见，而不是干脆把这一步藏起来。
 *
 * 于是规则可以一句话说完：第 N 步是 Done，当且仅当 `step` 已经走到第 N+1 步。
 */
internal fun oxideInstallStepStates(state: OxideInstallFlowState): List<OxideInstallStepState> {
    val loaderPassed = state.step == OxideInstallStep.Install
    return listOf(
        when {
            state.step == OxideInstallStep.Version -> OxideInstallStepState.Active
            state.hasVersion -> OxideInstallStepState.Done
            else -> OxideInstallStepState.Pending
        },
        when {
            !state.hasVersion -> OxideInstallStepState.Blocked
            state.step == OxideInstallStep.Loader -> OxideInstallStepState.Active
            loaderPassed -> OxideInstallStepState.Done
            else -> OxideInstallStepState.Pending
        },
        when {
            !state.hasVersion -> OxideInstallStepState.Blocked
            state.succeeded -> OxideInstallStepState.Done
            state.installing -> OxideInstallStepState.Active
            state.step == OxideInstallStep.Install -> OxideInstallStepState.Active
            else -> OxideInstallStepState.Pending
        },
    )
}

/**
 * 当前这一步能不能被直接点开
 *
 * 与 [oxideInstallStepReachable] 同一套判据：后两步都要先有 Minecraft 版本。
 * 装完之后这一步不允许再被点回——此时往回改选择已经没有意义，重新装一次才是。
 */
internal fun oxideInstallStepSelectable(
    state: OxideInstallFlowState,
    step: OxideInstallStep,
): Boolean {
    // 可达性仍由 oxideInstallStepReachable 判定：它是唯一一份"后两步要先有版本"的定义，
    // 界面不再另抄一遍，于是轨道上的置灰与"下一步"的禁用永远说同一件事
    if (!oxideInstallStepReachable(step, state.gameVersion)) return false
    // 装到一半或者刚装完还能往回改选择，会让面板上的选择与实际落盘的那一份对不上
    return !(step != OxideInstallStep.Version && (state.installing || state.succeeded))
}

/**
 * 「下一步」会走到哪一步；null 表示这一步还没有可走的前路
 *
 * 第一步只要求选中了版本；第二步额外要求加载器列表已经按这个版本拉到，
 * 否则"下一步"会落进一个还在转圈的详情面板里——那不是前进，是卡住。
 * 第三步没有下一步：它自己就是终点，往下走是安装按钮而不是第四步。
 */
internal fun oxideInstallNextStep(state: OxideInstallFlowState): OxideInstallStep? = when (state.step) {
    OxideInstallStep.Version ->
        OxideInstallStep.Loader.takeIf { state.hasVersion }

    OxideInstallStep.Loader ->
        OxideInstallStep.Install.takeIf { state.hasVersion && state.supportsLoaded }

    OxideInstallStep.Install -> null
}

// ---------------------------------------------------------------------------
// 面板尺寸
// ---------------------------------------------------------------------------

/** 面板宽度的上限：最多和参考稿的最大列数一样宽，再宽就只是把空白摊开 */
private const val INSTALL_PANEL_MAX_COLUMNS = 3f

/**
 * 面板的自适应尺寸上限
 *
 * 面板按内容定大小，因此这里给的是**上限**而不是定值：内容少的时候面板自己收窄，
 * 内容多的时候在上限内滚动，绝不拉伸到铺满整块内容区。
 */
internal data class OxideInstallPanelBounds(
    val maxWidthDp: Float,
    val maxHeightDp: Float,
)

/**
 * 面板在某个窗口尺寸下的尺寸上限
 *
 * 窗口四周留出的空当用 [cardGap] 量——那是页面里最细的一档间距，正好当边距。
 * 宽度上限取"最多三列"：与页面网格的最大列数一致，于是面板在任何窗口上都和
 * 下面的卡片是同一个尺度，不会出现一个比整页内容还宽的孤立对话框。
 *
 * 下限兜到 [cardMinWidth]：窗口再窄，也要给标题、百分比和取消按钮留下放得下一行的宽度。
 * 纯函数，因此窗口尺寸、界面缩放、断点全部体现为传入数的不同。
 */
internal fun oxideInstallPanelBounds(
    windowWidthDp: Float,
    windowHeightDp: Float,
    cardMinWidthDp: Float,
    cardGapDp: Float,
): OxideInstallPanelBounds {
    val inset = (cardGapDp * 2f).coerceAtLeast(0f)
    val floor = cardMinWidthDp.coerceAtLeast(0f)
    return OxideInstallPanelBounds(
        maxWidthDp = minOf(windowWidthDp - inset * 2f, floor * INSTALL_PANEL_MAX_COLUMNS)
            .coerceAtLeast(floor),
        maxHeightDp = (windowHeightDp - inset * 2f).coerceAtLeast(floor),
    )
}

/**
 * 步骤内容区还能占多高
 *
 * 扣掉的是面板上真实存在、且高度可数的那几块：标题行、步骤轨、底部动作行，
 * 以及它们之间的留白。这四块都由 metrics 给出，所以这里也不需要任何写死的数字。
 * 扣完不足两行时按两行兜底——再矮就会出现"每行只露一条缝"的滚动区。
 */
internal fun oxideInstallStepListMaxHeight(
    panelMaxHeightDp: Float,
    topBarDp: Float,
    navItemDp: Float,
    cardGapDp: Float,
): Float = (
    panelMaxHeightDp - topBarDp * 2f - navItemDp * 2f - cardGapDp * 4f
    ).coerceAtLeast(navItemDp * 2f)

// ---------------------------------------------------------------------------
// 进度
// ---------------------------------------------------------------------------

/** 一条真实任务在某一刻的状态 */
internal enum class OxideInstallTaskState {
    /** 排在后面，还没开始 */
    Pending,

    /** 正在跑 */
    Running,

    /** 已经跑完 */
    Done,
}

/**
 * 一条任务的真实快照
 *
 * [progress] 为 null 表示后端报的是"进度不确定"（`Task.updateProgress(-1f)`）。
 * 这一列**不允许**在界面里被补一个 0 或者按阶段均分出来的假数字。
 * [rateBytesPerSec] 同理：后端在下载结束时会把速率清成 null，这时就不该再显示 "/s"。
 */
internal data class OxideInstallTaskSnapshot(
    val id: String,
    val state: OxideInstallTaskState,
    val progress: Float?,
    val rateBytesPerSec: Long?,
)

/**
 * 整条安装流程在某一刻的真实进度
 *
 * [overall] 为 null 时面板画不确定态的进度条，而不是画一条停着不动的满格或空槽。
 * 只有两种情况它是确定的：所有任务都跑完了（1f），或者正在跑的那一条报出了真实百分比。
 * [runningPercent] 是当前任务自己的百分比，不做跨任务的加权平均——
 * 任务之间没有可比的字节量，加权出来的数只会好看，不会是真的。
 */
internal data class OxideInstallProgress(
    val completed: Int,
    val total: Int,
    val runningId: String?,
    val overall: Float?,
    val runningPercent: Int?,
    val rateBytesPerSec: Long?,
)

/**
 * 从任务快照算出面板要显示的进度
 *
 * 正在跑的任务按后端给的列表第一项取：任务流是顺序执行的，同时只会有一个 RUNNING，
 * 真出现两个时取第一个仍然是一个真实发生过的事实，不会凭空造出进度。
 * 空列表就是"后端还没把清单发过来"，此时全部字段保持空，面板显示"正在准备"。
 */
internal fun oxideInstallProgress(
    tasks: List<OxideInstallTaskSnapshot>,
): OxideInstallProgress {
    val total = tasks.size
    val completed = tasks.count { it.state == OxideInstallTaskState.Done }
    val running = tasks.firstOrNull { it.state == OxideInstallTaskState.Running }

    val overall: Float? = when {
        // 没有清单就没有分母，报一个数就是编
        total == 0 -> null
        completed == total -> 1f
        // 正在跑的那一条报出真实百分比时才算得出整条流程的进度；
        // 它报"不确定"（含 NaN / 越界）时整条流程也只能是不确定
        running != null -> running.progress
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.coerceIn(0f, 1f)
            ?.let { (completed + it) / total }

        // 两条任务交接的那个窗口：报"卡在 1/3"或"已完成 1/3"都是在编
        else -> null
    }

    return OxideInstallProgress(
        completed = completed,
        total = total,
        runningId = running?.id,
        overall = overall,
        runningPercent = running?.let { oxideInstallPercent(it.progress) },
        rateBytesPerSec = running?.rateBytesPerSec?.takeIf { it > 0L },
    )
}

/**
 * 百分比；后端说"不确定"时返回 null
 *
 * 负数就是后端约定的"不确定"，所以这里不把它当成 0%：画一条 0% 的进度条等于
 * 告诉用户"一个字节都还没下"，那是在编。后端也可能报 NaN 或越界的值，统一夹到 0..100。
 */
internal fun oxideInstallPercent(progress: Float?): Int? =
    progress
        ?.takeIf { it.isFinite() && it >= 0f }
        ?.let { (it.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100) }

/**
 * 传输速率（Bytes/秒）；后端没报或者报 0 时返回 null
 *
 * 同一个道理：下载停下来时后端会把速率清掉，这时显示 "0 B/s" 是把"没有数据"
 * 说成了"数据是零"。
 */
internal fun oxideInstallSpeed(bytesPerSec: Long?): Long? =
    bytesPerSec?.takeIf { it > 0L }

/**
 * 一条任务那一行副标题的排法
 *
 * 三段按固定顺序排开：状态、[message]、速率。缺哪一段就少一段，
 * 而不是补一句"下载中"来把位置填满——那一句话在没有任何真实数字的时候等于在编。
 *
 * [message] 原样转发：后端把"下了多少 / 还剩多少"直接写在里面了
 * （Minecraft 本体那一条由 `MinecraftDownloader` 用引擎快照格式化成
 * `132/1400 files · 84.21 MB / 210.44 MB`，单位走它一直在用的 `formatFileSize`），
 * 界面不重算，因此不会出现两处数字对不上。速率那一段同样只在后端报了非零速率时才有。
 *
 * 三段都是纯字符串拼接，所以可以在单元测试里逐条断言顺序与省略规则。
 */
internal fun oxideInstallDetailLine(
    stateLabel: String,
    message: String?,
    speedText: String?,
): String = listOfNotNull(
    stateLabel.trim().takeIf { it.isNotEmpty() },
    message?.trim()?.takeIf { it.isNotEmpty() },
    speedText?.trim()?.takeIf { it.isNotEmpty() },
).joinToString(" · ")