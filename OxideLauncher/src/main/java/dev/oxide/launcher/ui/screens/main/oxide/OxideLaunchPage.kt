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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.game.account.accountErrorText
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.content.elements.LaunchGameOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.viewmodel.LaunchGameViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 启动页
 *
 * 取代旧的"Starting"任务弹窗：这里是玩家按下 Play 之后真正会看到的那一屏。
 *
 * **启动链路一个字都没有改。**
 * [dev.oxide.launcher.game.launch.GameLaunchFlow]、`GameLauncher`、
 * `Launcher.launchJvm` 以及快速加载的插桩（`LAUNCH T0`、`PREPARE EVAL`、
 * `PREPARE phase`、`GAME PROCESS first frame`）全部原样保留；
 * 这一页只读 [LaunchGameViewModel] 已经算出来的阶段列表与进度，
 * 并把 [LaunchGameViewModel.cancel] 交给界面上的取消按钮。
 * 换句话说：换掉的是**呈现**，不是启动。
 *
 * 因此界面上每一个数字都来自真实的阶段：
 * 阶段标题与顺序来自 [TitledTask]，进度、消息与速率来自任务自己的 `StateFlow`，
 * 预启动阶段来自 `launchGameOperation`。没有阶段时显示"正在准备"，
 * 不会凭空画出一根进度条。
 */
@Composable
fun OxideLaunchPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
) {
    val launchViewModel: LaunchGameViewModel = viewModel()
    val flow by launchViewModel.launchFlow.collectAsStateWithLifecycle()
    val operation by launchViewModel.launchGameOperation.collectAsStateWithLifecycle()
    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()

    // `flow` 是 `by` 委托出来的属性，Kotlin 不会对委托属性做智能转换，
    // 因此先绑到一个局部变量上，再在里面以非空类型读 tasksFlow
    val activeFlow = flow
    val tasks: List<TitledTask> = if (activeFlow != null) {
        activeFlow.tasksFlow.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

    // 阶段状态集中在一张表里：行与 hero 共用同一份读数，
    // 因此不会出现"行说完成了、进度条还停在上一格"的错位
    val stageSnapshot = rememberLaunchStages(tasks)

    // 新阶段进来就滚到底部，玩家不用手动追着跑
    val listState = rememberLazyListState()
    LaunchedEffect(tasks.size) {
        if (tasks.isNotEmpty()) listState.animateScrollToItem(tasks.lastIndex)
    }

    val stageLabel = launchStageLabel(operation, currentVersion)
    val errorText = launchStageError(operation)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= metrics.cardMinWidth * 1.3f

        Column(modifier = Modifier.fillMaxSize()) {
            OxidePageTitle(
                modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                text = stringResource(R.string.oxide_sec_launch_title),
                trailing = {
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_launch_cancel),
                        onClick = { launchViewModel.cancel() },
                        tone = OxideButtonTone.Secondary,
                        enabled = flow != null,
                    )
                },
            )
            OxideSectionLabel(
                modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                text = stringResource(R.string.oxide_sec_launch_kicker),
            )

            Spacer(Modifier.height(metrics.sectionGap))

            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideLaunchHero(
                        metrics = metrics,
                        stageLabel = stageLabel,
                        errorText = errorText,
                        total = tasks.size,
                        done = stageSnapshot.values.count { it == TaskStage.COMPLETED },
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                    )
                    OxideLaunchTaskList(
                        metrics = metrics,
                        tasks = tasks,
                        stageSnapshot = stageSnapshot,
                        listState = listState,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideLaunchHero(
                        metrics = metrics,
                        stageLabel = stageLabel,
                        errorText = errorText,
                        total = tasks.size,
                        done = stageSnapshot.values.count { it == TaskStage.COMPLETED },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f),
                    )
                    OxideLaunchTaskList(
                        metrics = metrics,
                        tasks = tasks,
                        stageSnapshot = stageSnapshot,
                        listState = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 收集每个阶段的当前状态
 *
 * 每个阶段起一个收集协程，写进同一张 [SnapshotStateMap]；
 * 协程都挂在 `LaunchedEffect` 的作用域里，阶段列表一变就会一起被取消。
 */
@Composable
private fun rememberLaunchStages(tasks: List<TitledTask>): SnapshotStateMap<String, TaskStage> {
    val snapshot = remember { mutableStateMapOf<String, TaskStage>() }
    LaunchedEffect(tasks) {
        snapshot.keys.toList().filter { key -> tasks.none { it.task.id == key } }
            .forEach { key -> snapshot.remove(key) }
        tasks.forEach { task ->
            launch {
                task.task.stage.collect { stage -> snapshot[task.task.id] = stage }
            }
        }
    }
    return snapshot
}

/**
 * 左侧：正在发生什么
 *
 * 标题字号跟着宽度走，与主页 hero 同一套比例；
 * 进度条是**已经完成的阶段数**除以阶段总数，因此不会编造一个百分比。
 */
@Composable
private fun OxideLaunchHero(
    metrics: OxideMetrics,
    stageLabel: String,
    errorText: String?,
    total: Int,
    done: Int,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.cardGap),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = stageLabel,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.cardGap))
            Text(
                text = stringResource(R.string.oxide_sec_launch_title),
                color = Oxide.Fg,
                fontSize = metrics.heroTitleDp.sp,
                lineHeight = metrics.heroTitleDp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.secRowGap))
            Text(
                text = errorText ?: stringResource(R.string.oxide_sec_launch_subtitle),
                color = if (errorText != null) Oxide.FgMuted else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (total > 0) {
                Spacer(Modifier.height(metrics.cardGap))
                OxideProgressBar(progress = done.toFloat() / total.toFloat())
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        R.string.oxide_sec_launch_stage_count,
                        done,
                        total,
                    ),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 右侧：真实阶段列表 */
@Composable
private fun OxideLaunchTaskList(
    metrics: OxideMetrics,
    tasks: List<TitledTask>,
    stageSnapshot: SnapshotStateMap<String, TaskStage>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_launch_tasks))
        Spacer(Modifier.height(metrics.secRowGap))
        if (tasks.isEmpty()) {
            OxideLoadingRow(stringResource(R.string.oxide_sec_launch_waiting))
            return@OxideSurface
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
        ) {
            items(items = tasks, key = { task -> task.task.id }) { task ->
                OxideLaunchTaskRow(
                    metrics = metrics,
                    task = task,
                    stage = stageSnapshot[task.task.id] ?: TaskStage.PREPARING,
                )
                Spacer(Modifier.height(metrics.secRowGap))
            }
        }
    }
}

/**
 * 一个阶段
 *
 * 阶段名来自 [TitledTask.title]——它可能是字符串资源，因此走 [resolveAndroidString]
 * 而不是 `toString()`；进度与速率来自任务自己的 `StateFlow`。
 * 状态除了颜色之外还有一行文字，因此不会只靠颜色区分。
 */
@Composable
private fun OxideLaunchTaskRow(
    metrics: OxideMetrics,
    task: TitledTask,
    stage: TaskStage,
) {
    val title = resolveAndroidString(task.title).text
    val progress by task.task.progress.collectAsStateWithLifecycle()
    val message by task.task.message.collectAsStateWithLifecycle()
    val rate by task.task.rateBytesPerSec.collectAsStateWithLifecycle()

    val stageText = when (stage) {
        TaskStage.PREPARING -> stringResource(R.string.oxide_common_loading)
        TaskStage.RUNNING -> stringResource(R.string.oxide_sec_launch_indeterminate)
        TaskStage.COMPLETED -> stringResource(R.string.generic_done)
    }
    val messageText = message?.let { resolveAndroidString(it).text }.orEmpty()
    val rateBytes = rate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (stage == TaskStage.COMPLETED) Oxide.FgMuted else Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(metrics.secRowGap))
            Text(
                // 负进度代表"不确定"，这时给阶段文字而不是一个假的百分比
                text = if (progress >= 0f) {
                    stringResource(
                        R.string.oxide_sec_launch_progress_percent,
                        (progress * 100).toInt(),
                    )
                } else {
                    stageText
                },
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
        if (messageText.isNotEmpty()) {
            Text(
                text = messageText,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 进度不确定时不画条，避免一根永远停在 0% 的进度条
        if (progress >= 0f) {
            Spacer(Modifier.height(4.dp))
            OxideProgressBar(progress = progress)
        }
        if (rateBytes != null && rateBytes > 0L) {
            Text(
                text = stringResource(
                    R.string.oxide_sec_launch_rate,
                    formatFileSize(rateBytes),
                ),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
    }
}

/** 当前处于启动前的哪个阶段；没有阶段时如实说是"正在准备" */
@Composable
private fun launchStageLabel(
    operation: LaunchGameOperation,
    version: Version?,
): String = when (operation) {
    LaunchGameOperation.None -> stringResource(R.string.oxide_sec_launch_stage_check)
    LaunchGameOperation.NoVersion -> stringResource(R.string.oxide_sec_launch_op_no_version)
    is LaunchGameOperation.InvalidVersionName ->
        stringResource(R.string.oxide_sec_launch_op_invalid_name)

    LaunchGameOperation.NoAccount -> stringResource(R.string.oxide_sec_launch_op_no_account)
    is LaunchGameOperation.RendererNoStoragePermission ->
        stringResource(R.string.oxide_sec_launch_op_storage_permission)

    is LaunchGameOperation.UnsupportedRenderer -> stringResource(
        R.string.oxide_sec_launch_op_unsupported_renderer,
        operation.renderer.getRendererName(),
    )

    is LaunchGameOperation.UnsupportedPlugins ->
        stringResource(R.string.oxide_sec_launch_op_unsupported_plugins)

    is LaunchGameOperation.TryLaunch -> version?.getVersionName()
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.oxide_sec_launch_stage_check)

    is LaunchGameOperation.AccountRelogin -> stringResource(
        R.string.oxide_sec_launch_op_relogin,
        operation.account.username,
    )

    is LaunchGameOperation.AccountRefreshFailed -> stringResource(
        R.string.oxide_sec_launch_op_refresh_failed,
        operation.account.username,
    )

    is LaunchGameOperation.RealLaunch -> operation.version.getVersionName()
        .takeIf { it.isNotBlank() }
        ?: stringResource(R.string.oxide_sec_launch_stage_running)
}

/**
 * 当前阶段的错误描述
 *
 * 只有账号相关的两个阶段带真实异常，因此也只在这里把它翻译成可读文本；
 * 其余阶段没有可展示的异常，返回 null 让副标题回到正常的那一句。
 */
@Composable
private fun launchStageError(operation: LaunchGameOperation): String? = when (operation) {
    is LaunchGameOperation.AccountRelogin ->
        operation.error?.let { resolveAndroidString(accountErrorText(it)).text }

    is LaunchGameOperation.AccountRefreshFailed ->
        resolveAndroidString(accountErrorText(operation.error)).text

    else -> null
}