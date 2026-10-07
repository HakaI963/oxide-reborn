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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.ui.theme.Oxide
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskHistory
import dev.oxide.launcher.coroutine.TaskOutcome
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.utils.file.formatFileSize

/**
 * 任务面板
 *
 * 这一块原本是 MainActivity 上那个 30% 宽的旧 Zalith 侧滑卡片，直接压在 Oxide 外壳上面，
 * 是新外壳里最显眼的一处旧界面。现在它是一个正常的 Oxide 抽屉。
 *
 * 五节（下载中 / 已完成 / 排队 / 运行中 / 历史）来自**一份**数据，不是五套账：[tasks] 是任务系统里"已经交出去、
 * 还没收尾"的那一份，[history] 是它自己留的收尾快照，两边都住在 `TaskSystem` 这个进程级
 * 单例上——所以关掉抽屉、手点叉号收掉发现页那条提示条、甚至换一页，历史都不会消失。
 * 分节规则在 `OxideTaskSectionsLogic.kt` 里，是纯函数，因此可以在不启动 Compose 的前提下测。
 * 下载类与一般任务的分界是 `TaskKind`：下载中收的是还没收尾的下载（排队与运行中合并），
 * 已完成收的是收尾了的下载（成功 / 失败 / 取消靠行尾标记区分），剩下的三节还是原来的排队 / 运行 / 历史。
 *
 * 下面三个纯函数单独拆出来，也是为了能在不启动 Compose 的前提下测：
 * `Task.updateProgress` 会把负值保留成 -1f，那表示"进度不可知"，不是 0%。
 */

/** 进度百分比；`null` 表示进度不可知 */
internal fun oxideTaskProgressPercent(progress: Float): Int? =
    if (progress < 0f) null else (progress * 100f).toInt().coerceIn(0, 100)

/** 速率文本；`null` 表示不显示速率，避免出现 "0 B/s" 这种没有意义的读数 */
internal fun oxideTaskRateText(bytesPerSec: Long?): String? =
    bytesPerSec?.takeIf { it > 0L }?.let { "${formatFileSize(it)}/s" }

/** 阶段对应的字符串资源；没有消息文本时可以拿它当兜底标题 */
internal fun oxideTaskStageLabel(stage: TaskStage): Int = when (stage) {
    TaskStage.PREPARING -> R.string.oxide_task_stage_preparing
    TaskStage.RUNNING -> R.string.oxide_task_stage_running
    TaskStage.COMPLETED -> R.string.oxide_task_stage_completed
}

/**
 * 任务抽屉
 *
 * 列表用 LazyColumn：任务数量没有上限，而竖向滚动已经被抽屉本体占着了，
 * 换成 Column 就会得到嵌套滚动容器。
 *
 * 五节的顺序即 [OxideTaskSection] 的声明顺序：下载中与已完成在前，
 * 那是用户点开这个抽屉最想看的两节。
 *
 * @param history 已经收尾的任务；为空是正常的（第一次打开抽屉时还没有）
 */
@Composable
internal fun OxideTaskDrawer(
    tasks: List<Task>,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    history: List<TaskHistory> = emptyList(),
) {
    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_tasks_title),
        // 列表自己就是 LazyColumn，所以抽屉不能再套一层纵向滚动：
        // 那样会把它的最大高度变成无穷大，测量期直接抛异常。
        scrollable = false,
    ) {
        TaskSections(
            sections = oxideTaskSectionsOf(
                tasks.map { it.toTaskEntry() } + history.map { it.toTaskEntry() }
            ),
        )
    }
}

/**
 * 五节
 *
 * 空的那一节整节不画：五个空标题（"Preparing" 底下什么都没有）比不画更让人以为这里本来该有东西。
 * 五节全空时才用整块空状态，那一句说的是"现在没有在跑的东西"，而不是"从来就没有过"——
 * 历史或已完成里还留着东西时这几句都不出现。
 */
@Composable
private fun TaskSections(sections: OxideTaskSections) {
    if (sections.empty) {
        OxideEmptyState(
            title = stringResource(R.string.oxide_tasks_empty),
            detail = stringResource(R.string.oxide_tasks_empty_detail),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            OxideTaskSection.Downloading,
            OxideTaskSection.Complete,
            OxideTaskSection.Queued,
            OxideTaskSection.Running,
            OxideTaskSection.History,
        ).forEach { section ->
            val entries = sections.of(section)
            if (entries.isEmpty()) return@forEach
            item(key = "section-${section.name}") {
                OxideSectionLabel(text = stringResource(oxideTaskSectionLabel(section)))
            }
            items(entries, key = { "${section.name}-${it.id}" }) { entry ->
                TaskEntryRow(entry)
            }
        }
    }
}

@Composable
private fun TaskEntryRow(entry: OxideTaskEntry) {
    when (entry) {
        is OxideTaskEntry.Settled -> SettledTaskRow(entry.record)
        is OxideTaskEntry.Live -> LiveTaskRow(entry.task)
    }
}

/**
 * 在跑的那一条
 *
 * 四个流各自收集一次：进度条要跟着 [Task.progress] 往前走，而 `StateFlow.value` 本身不是
 * 组合期的依赖，直接读一次就只会画出一帧、再也不动了。
 */
@Composable
private fun LiveTaskRow(task: Task) {
    val title by task.title.collectAsStateWithLifecycle()
    val message by task.message.collectAsStateWithLifecycle()
    val progress by task.progress.collectAsStateWithLifecycle()
    val rate by task.rateBytesPerSec.collectAsStateWithLifecycle()
    val outcome by task.outcome.collectAsStateWithLifecycle()

    TaskRow(
        title = title ?: message,
        detail = null,
        progress = progress,
        rate = oxideTaskRateText(rate),
        outcome = outcome,
        cancellable = true,
        onCancel = { TaskSystem.cancelTask(task.id) },
    )
}

/**
 * 已经收尾的那一条
 *
 * 订阅任何流：快照是收尾那一刻定下来的，之后再也不会被改写。
 * 进度与速率只在成功与失败那两种结局下保留，被取消与从未开始的显示成空槽而不是 0%。
 */
@Composable
private fun SettledTaskRow(record: TaskHistory) {
    TaskRow(
        title = record.title ?: record.message,
        detail = oxideTaskHistoryDetail(record),
        progress = oxideTaskHistoryProgress(record),
        rate = oxideTaskRateText(record.rateBytesPerSec),
        outcome = record.outcome,
        cancellable = false,
        onCancel = {},
    )
}

/**
 * 一行任务，两个分支共用同一份排版
 *
 * 标题退回说明、再退回结局标签：三者都没有的那一行也得有字，否则它是一行空白，
 * 用户会以为自己点错了地方。
 */
@Composable
private fun TaskRow(
    title: AndroidStringText?,
    detail: AndroidStringText?,
    progress: Float?,
    rate: String?,
    outcome: TaskOutcome,
    cancellable: Boolean,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title?.let { resolveAndroidString(it) }
                        ?: AnnotatedString(stringResource(oxideTaskOutcomeLabel(outcome))),
                    color = if (outcome.finished) Oxide.FgMuted else Oxide.Fg,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let {
                    Text(
                        text = resolveAndroidString(it),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    oxideTaskProgressPercent(progress ?: -1f)?.let {
                        TaskCaption(text = "$it%")
                    }
                    rate?.let {
                        TaskCaption(text = it)
                    }
                    // 结局标记只在历史那一节出现：在跑与排队两节，右侧的叉号已经说明了状态
                    if (outcome.finished) {
                        TaskCaption(text = stringResource(oxideTaskOutcomeLabel(outcome)))
                    }
                }
            }
            if (cancellable) {
                OxideIconButton(
                    onClick = onCancel,
                    glyph = "\u2715",
                    contentDescription = stringResource(R.string.generic_cancel),
                )
            }
        }
        // 进度不可知时 Task 里存的是 -1f，交给 0f 让它显示成空槽，而不是当成满格
        progress?.let {
            OxideProgressBar(progress = if (it < 0f) 0f else it)
        }
    }
}

@Composable
private fun TaskCaption(text: String) {
    Text(
        text = text,
        color = Oxide.FgMuted,
        fontSize = Oxide.Type.Label.fontSize,
        lineHeight = Oxide.Type.Label.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
