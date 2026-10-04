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
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.utils.file.formatFileSize

/**
 * 任务面板
 *
 * 这一块原本是 MainActivity 上那个 30% 宽的旧 Zalith 侧滑卡片，直接压在 Oxide 外壳上面，
 * 是新外壳里最显眼的一处旧界面。现在它是一个正常的 Oxide 抽屉。
 *
 * 下面三个纯函数单独拆出来，是为了能在不启动 Compose 的前提下测：
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
 */
@Composable
internal fun OxideTaskDrawer(
    tasks: List<Task>,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_tasks_title),
    ) {
        if (tasks.isEmpty()) {
            OxideEmptyState(
                title = stringResource(R.string.oxide_tasks_empty),
                detail = stringResource(R.string.oxide_tasks_empty_detail),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    OxideTaskRow(task = task)
                }
            }
        }
    }
}

@Composable
private fun OxideTaskRow(task: Task) {
    val progress by task.progress.collectAsStateWithLifecycle()
    val message by task.message.collectAsStateWithLifecycle()
    val rate by task.rateBytesPerSec.collectAsStateWithLifecycle()
    val stage by task.stage.collectAsStateWithLifecycle()

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
                    text = message?.let { resolveAndroidString(it) }
                        ?: AnnotatedString(
                            stringResource(oxideTaskStageLabel(stage))
                        ),
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    oxideTaskProgressPercent(progress)?.let {
                        TaskCaption(text = "$it%")
                    }
                    oxideTaskRateText(rate)?.let {
                        TaskCaption(text = it)
                    }
                }
            }
            OxideIconButton(
                onClick = { TaskSystem.cancelTask(task.id) },
                glyph = "✕",
            )
        }
        // 进度不可知时 Task 里存的是 -1f，交给 0f 让它显示成空槽，而不是当成满格
        OxideProgressBar(progress = if (progress < 0f) 0f else progress)
    }
}

@Composable
private fun TaskCaption(text: String) {
    Text(
        text = text,
        color = Oxide.FgMuted,
        fontSize = Oxide.Type.Label.fontSize,
        lineHeight = Oxide.Type.Label.lineHeight,
        modifier = Modifier.padding(0.dp),
    )
}