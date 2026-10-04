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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import kotlinx.coroutines.launch

/**
 * 「装一个版本」这块面板自己的零件
 *
 * 面板不是页面：它按内容定大小、居中、四角离窗口留空当，因此这里的每一块都只声明
 * **上限**（`heightIn` / `weight(fill = false)`），不声明 `fillMaxSize`——
 * 一旦有哪一块撑满了，面板立刻又变回铺满整块内容区的那一大片空白。
 *
 * 步骤轨与进度块全部读 [OxideInstallFlowLogic] 里的纯函数，本文件只负责把它们画出来。
 */

// ---------------------------------------------------------------------------
// 步骤轨
// ---------------------------------------------------------------------------

/**
 * 一步前面的记号
 *
 * 四种状态各有各的字形，因此"走到哪了"不只体现在底色上：做完是勾，进行中是序号，
 * 排队也是序号（更暗），走不到是一道横杠。看不清颜色也能读出进度。
 * 纯函数，四种输入各有一个字形，没有第五种。
 */
internal fun oxideInstallStepMarker(state: OxideInstallStepState, index: Int): String = when (state) {
    OxideInstallStepState.Done -> "✓"
    OxideInstallStepState.Blocked -> "–"
    OxideInstallStepState.Active, OxideInstallStepState.Pending -> (index + 1).toString()
}

/** 一步的状态文案，同时用于画在步骤上与念给读屏软件 */
@Composable
internal fun oxideInstallStepStateLabel(state: OxideInstallStepState): String = stringResource(
    when (state) {
        OxideInstallStepState.Done -> R.string.oxide_inst_step_state_done
        OxideInstallStepState.Active -> R.string.oxide_inst_step_state_active
        OxideInstallStepState.Pending -> R.string.oxide_inst_step_state_pending
        OxideInstallStepState.Blocked -> R.string.oxide_inst_step_state_blocked
    },
)

/**
 * 三步轨道
 *
 * 面板宽度有限，因此始终是横排；每一步都同时给出序号、名字与状态文字，
 * 并以 `Role.Tab` + selected + stateDescription 暴露给无障碍服务。
 * 不可点的那一步保持可读但置灰，而不是消失——"还不能选加载器"必须看得见。
 */
@Composable
internal fun OxideInstallStepper(
    metrics: OxideMetrics,
    titles: List<String>,
    states: List<OxideInstallStepState>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
    ) {
        titles.forEachIndexed { index, title ->
            val state = states.getOrElse(index) { OxideInstallStepState.Blocked }
            val label = oxideInstallStepStateLabel(state)
            val isCurrent = index == currentIndex

            Row(
                modifier = Modifier
                    .weight(1f)
                    // min 而不是 height：这一格里是两行字，界面放大到两行装不下时
                    // 让它长高，而不是把状态那一行裁掉半截
                    .heightIn(min = metrics.secControlHeight)
                    .clip(Oxide.RadiusControl)
                    .background(if (isCurrent) Oxide.BgTabActive else Oxide.BgElevated)
                    .border(
                        BorderStroke(1.dp, if (isCurrent) Oxide.Line2 else Oxide.LineFaint),
                        Oxide.RadiusControl,
                    )
                    .selectable(
                        selected = isCurrent,
                        enabled = state != OxideInstallStepState.Blocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics { stateDescription = label }
                    .padding(horizontal = metrics.secControlPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = oxideInstallStepMarker(state, index),
                    color = when {
                        isCurrent -> Oxide.FgNumActive
                        state == OxideInstallStepState.Blocked -> Oxide.FgFaint
                        else -> Oxide.FgGhost
                    },
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                )
                Spacer(Modifier.width(metrics.secRowGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (state == OxideInstallStepState.Blocked) {
                            Oxide.FgFaint
                        } else {
                            Oxide.Fg
                        },
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = label,
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 安装进度
// ---------------------------------------------------------------------------

/**
 * 一组任务的实时状态
 *
 * 四个流在面板这一层收集一次，任务行和顶部汇总都从同一份读，因此不会出现
 * "标题行报 43%、下面那一行报 41%"这种事。任务列表变化时协程一起被取消，
 * 已经不在列表里的键顺手清掉，滚动很久之后也不会攒下一堆僵尸条目。
 */
internal class OxideInstallTaskLiveState {
    val stages = mutableStateMapOf<String, TaskStage>()
    val progresses = mutableStateMapOf<String, Float>()
    val messages = mutableStateMapOf<String, AndroidStringText?>()
    val rates = mutableStateMapOf<String, Long?>()

    /** 清掉不在列表里的键 */
    fun retain(keys: Set<String>) {
        stages.keys.toList().filterNot { it in keys }.forEach { stages.remove(it) }
        progresses.keys.toList().filterNot { it in keys }.forEach { progresses.remove(it) }
        messages.keys.toList().filterNot { it in keys }.forEach { messages.remove(it) }
        rates.keys.toList().filterNot { it in keys }.forEach { rates.remove(it) }
    }
}

/** 收集每一条任务的阶段 / 进度 / 消息 / 速率 */
@Composable
internal fun rememberInstallTaskLiveState(tasks: List<TitledTask>): OxideInstallTaskLiveState {
    val live = remember { OxideInstallTaskLiveState() }
    LaunchedEffect(tasks) {
        live.retain(tasks.map { it.task.id }.toSet())
        tasks.forEach { task ->
            val id = task.task.id
            launch { task.task.stage.collect { live.stages[id] = it } }
            launch { task.task.progress.collect { live.progresses[id] = it } }
            launch { task.task.message.collect { live.messages[id] = it } }
            launch { task.task.rateBytesPerSec.collect { live.rates[id] = it } }
        }
    }
    return live
}

/** 把后端的任务流翻成纯逻辑认得的快照；阶段才是"哪一条在跑"的唯一依据 */
internal fun oxideInstallTaskSnapshots(
    tasks: List<TitledTask>,
    live: OxideInstallTaskLiveState,
): List<OxideInstallTaskSnapshot> = tasks.map { task ->
    val id = task.task.id
    // 缺键按 PREPARING 处理：那正是"已经排进清单但还没开始"的样子，
    // 而不是"跑完了"，宁可晚一步显示状态，也不要提前宣布完成
    val stage = live.stages[id] ?: TaskStage.PREPARING
    val rawProgress = live.progresses[id] ?: -1f
    OxideInstallTaskSnapshot(
        id = id,
        state = when (stage) {
            TaskStage.COMPLETED -> OxideInstallTaskState.Done
            TaskStage.RUNNING -> OxideInstallTaskState.Running
            TaskStage.PREPARING -> OxideInstallTaskState.Pending
        },
        progress = rawProgress.takeIf { it >= 0f },
        rateBytesPerSec = live.rates[id],
    )
}

/**
 * 安装进度面板
 *
 * 自上而下就是用户真正想知道的那几件事：现在在第几步、正在干哪一件、干到多少、
 * 下得有多快、已经下了多少、还剩多少、哪一条排在后面、出错时怎么重来、怎么停。
 *
 * 每一项都直接来自后端：阶段来自 `Task.stage`，百分比来自 `Task.progress`，
 * 速率来自 `Task.rateBytesPerSec`，"多少文件 / 多少字节"来自 `Task.message`
 * （Minecraft 本体那一条由 `MinecraftDownloader` 用引擎快照格式化好）。
 * 后端报"不确定"时这里画的是空槽加"正在处理"这句话，而不是补一个假的数。
 */
@Composable
internal fun OxideInstallProgressPanel(
    metrics: OxideMetrics,
    tasks: List<TitledTask>,
    onCancel: () -> Unit,
    listMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val live = rememberInstallTaskLiveState(tasks)
    val snapshots = oxideInstallTaskSnapshots(tasks, live)
    val progress = oxideInstallProgress(snapshots)
    val runningAt = snapshots.indexOfFirst { it.id == progress.runningId }
    val runningTitle = runningAt.takeIf { it >= 0 }?.let { resolveAndroidString(tasks[it].title).text }
    // 后端还没把清单发过来时不该出现"第 0 步 / 共 0 步"，所以总数为 0 就不画这一行
    val counter: String? = progress.total.takeIf { it > 0 }?.let { total ->
        val current = runningAt
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: progress.completed.coerceAtMost(total)
        stringResource(R.string.oxide_inst_task_counter, current.coerceAtLeast(1), total)
    }

    OxideSurface(modifier = modifier, contentPadding = PaddingValues(all = metrics.cardGap)) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_inst_progress_section),
            trailing = {
                if (counter != null) {
                    Text(
                        text = counter,
                        color = Oxide.FgGhost,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                    )
                }
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))

        // 当前任务：正在跑的那一条，没有就退回到"后端正在准备"这件事本身
        Text(
            text = runningTitle ?: stringResource(R.string.oxide_sec_launch_waiting),
            color = Oxide.Fg,
            fontSize = Oxide.Type.BodyStrong.fontSize,
            lineHeight = Oxide.Type.BodyStrong.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 当前任务自己报出来的量："132/1400 files · 84.21 MB / 210.44 MB"。
        // 后端没有报消息的加载器下载就什么都不画，不拿"下载中"三个字凑数。
        progress.runningId?.let { id ->
            live.messages[id]?.let { message ->
                Text(
                    text = resolveAndroidString(message).text,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(metrics.secRowGap))

        val overall = progress.overall
        if (overall != null) {
            OxideProgressBar(progress = overall)
        } else {
            // 后端还没给出可用的分母：画一条空的轨，而不是一条停着不动的假进度
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(Oxide.RadiusToggle)
                    .background(Oxide.Line),
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progress.runningPercent
                    ?.let { stringResource(R.string.oxide_sec_launch_progress_percent, it) }
                    ?: stringResource(R.string.oxide_sec_launch_indeterminate),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
            )
            Spacer(Modifier.width(metrics.secRowGap))
            oxideInstallSpeed(progress.rateBytesPerSec)?.let { rate ->
                Text(
                    text = stringResource(R.string.oxide_sec_launch_rate, formatFileSize(rate)),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight)) {
            items(items = tasks, key = { it.task.id }) { task ->
                OxideInstallTaskRow(
                    metrics = metrics,
                    title = resolveAndroidString(task.title).text,
                    state = when (live.stages[task.task.id] ?: TaskStage.PREPARING) {
                        TaskStage.COMPLETED -> OxideInstallTaskState.Done
                        TaskStage.RUNNING -> OxideInstallTaskState.Running
                        TaskStage.PREPARING -> OxideInstallTaskState.Pending
                    },
                    percent = oxideInstallPercent(live.progresses[task.task.id]),
                    detail = live.messages[task.task.id]?.let { resolveAndroidString(it).text },
                    speed = oxideInstallSpeed(live.rates[task.task.id]),
                )
                Spacer(Modifier.height(metrics.secRowGap))
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onCancel,
            tone = OxideButtonTone.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 清单里的一条任务：记号 + 名字 + 状态 + 百分比 + 后端报的量 + 速率 */
@Composable
private fun OxideInstallTaskRow(
    metrics: OxideMetrics,
    title: String,
    state: OxideInstallTaskState,
    percent: Int?,
    detail: String?,
    speed: Long?,
) {
    val stateLabel = stringResource(
        when (state) {
            OxideInstallTaskState.Done -> R.string.oxide_inst_step_state_done
            OxideInstallTaskState.Running -> R.string.oxide_inst_step_state_active
            OxideInstallTaskState.Pending -> R.string.oxide_inst_step_state_pending
        },
    )
    // 速率先在这里取成字符串：stringResource 是 @Composable，不能放进 buildString 的 lambda
    val speedText = speed?.let { stringResource(R.string.oxide_sec_launch_rate, formatFileSize(it)) }
    val detailText = listOfNotNull(
        stateLabel,
        detail?.takeIf { it.isNotBlank() },
        speedText,
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = metrics.secRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(Oxide.RadiusToggle)
                .background(
                    when (state) {
                        OxideInstallTaskState.Done -> Oxide.FgMuted
                        OxideInstallTaskState.Running -> Oxide.Fg
                        OxideInstallTaskState.Pending -> Color.Transparent
                    },
                )
                .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusToggle),
        )
        Spacer(Modifier.width(metrics.secRowGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (state == OxideInstallTaskState.Done) Oxide.FgMuted else Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 状态、真实报出来的量、速率依次排开，缺哪一段就少一段，
                // 而不是补一句"下载中"来填位置
                text = detailText,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (percent != null) {
            Spacer(Modifier.width(metrics.secRowGap))
            Text(
                text = stringResource(R.string.oxide_sec_launch_progress_percent, percent),
                color = Oxide.FgGhost,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
    }
}