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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.game.account.isMicrosoftAccount
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.ui.components.LocalMainActivity
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.content.elements.LaunchGameOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.viewmodel.LaunchGameViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 启动表面：整块盖住窗口，中间一块居中面板
 *
 * 这一层是 [OxideMainShell] 在 [oxideLaunchVisible] 为真时压在**最上面**的那一块，
 * 因此"按下 Play 之后屏幕上是什么"在这里只有一种答案：这一块。
 *
 * v1.6.0 的写法有两个问题，两者都被这一版的结构直接消掉：
 *
 * - **底下的 Home 一直看得见。** 旧版这块的根节点没有底色，面板本身用的
 *   [Oxide.SurfaceBase] 在暗色下只有 `0x170A0A0A`（约 9% 不透明），于是 Home 的
 *   hero、卡片与壁纸整块透上来，和面板抢视觉。现在的根节点直接铺 [Oxide.Bg]，
 *   并且在面板下面再压一层吞点击的阻断层，因此下面的页面既看不见也点不到——
 *   这同时也是"启动期间不该出现叠层导航"的答案：启动时根本没有下层可叠。
 * - **"Starting" 变成了一行巨字。** 旧版把 `oxide_sec_launch_title`（内容正是
 *   "Starting"）当 hero 标题，用 [OxideMetrics.heroTitleDp]（最大 42sp 再乘界面缩放）
 *   画在面板里，下面还跟着一个同样写着 "Starting" 的 22sp 页面标题；
 *   而 hero 那一列用的是 `Arrangement.Bottom` 且不裁切，矮屏上内容会向上溢出面板。
 *   现在面板里最大的字是实例名（[Oxide.Type.DrawerTitle]），"启动中" 只是一行小标签，
 *   字号全部有上限，也就没有"巨字"这一说。
 *
 * **面板与字段的尺寸全部来自 [OxideMetrics]**（见 [oxideLaunchPanelGeometry]），
 * 颜色全部来自 [Oxide]。640x360 上它是居中的一块紧凑板：宽高都被窗口夹住，
 * 阶段列表那一块被固定高度封顶并在内部滚动，因此几条还是几十条阶段都不会把它撑开，
 * 也拿不到 `maxHeight == Infinity`。
 *
 * **启动链路一个字都没有改。** [dev.oxide.launcher.game.launch.GameLaunchFlow]、
 * `GameLauncher`、`Launcher.launchJvm` 以及快速加载的插桩（`LAUNCH T0`、`PREPARE EVAL`、
 * `PREPARE phase`、`GAME PROCESS first frame`）全部原样保留；这一层只读
 * [LaunchGameViewModel] 已经算出来的阶段与进度，并把 [LaunchGameViewModel.cancel]
 * 交给面板上那个取消键。换掉的是**呈现**，不是启动，也没有任何为了好看而加的等待。
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

    val projectedOperation = oxidePreflightOperationOf(operation)
    val surface = oxideLaunchSurface(flowActive = flow != null, operation = projectedOperation)
    // 外壳已经按同一个判据决定要不要挂这一层；这里再挡一次是为了让"返回空"
    // 而不是"返回一块透明的东西"，那样它在任何宿主上都是安全的
    if (surface == OxideLaunchSurface.Hidden) return

    // 面板上的准备状态、阶段列表与那根条共用同一份读数，
    // 因此不会出现"行说完成了、进度条还停在上一格"的错位
    val readings = rememberLaunchStageReadings(tasks)
    val stages = tasks.map { task ->
        OxideLaunchStageSnapshot(
            id = task.task.id,
            stage = readings.stages[task.task.id] ?: TaskStage.PREPARING,
            progress = readings.progresses[task.task.id] ?: UNSET_PROGRESS,
        )
    }

    // 实例：operation 上带的那个最准（它就是这次要启动的），
    // 阶段已经跑起来时 operation 会被复位成 None，这时退回后端记录的当前实例
    val version = launchInstanceOf(operation) ?: currentVersion

    // 密码只跟着"同一个账号的第三方重新登录"这一次走：
    // 提交失败时 operation 会换成另一个实例，但密码不该被清掉（旧弹窗也没清）
    val passwordKey = (operation as? LaunchGameOperation.AccountRelogin)
        ?.takeIf { !it.account.isMicrosoftAccount() }
        ?.account
        ?.uniqueUUID
    var password by rememberSaveable(passwordKey) { mutableStateOf("") }

    // 后端需要的宿主引用都已经在这一层拿得到，因此不需要额外加宿主动作
    val activity = LocalMainActivity.current
    val eventViewModel = rememberOxideEventViewModel()
    val errorViewModel = rememberOxideErrorViewModel()
    val backStack = rememberOxideScreenBackStack()

    val preflightAsk = oxidePreflightBranchOf(projectedOperation)?.let { oxidePreflightAsk(it) }
    val preflightTexts = if (surface == OxideLaunchSurface.Preflight) {
        launchPreflightTexts(operation)
    } else {
        null
    }
    val preflight: OxideLaunchPreflightSlot? =
        if (preflightTexts != null && preflightAsk != null) {
            OxideLaunchPreflightSlot(
                ask = preflightAsk,
                texts = preflightTexts,
                password = password,
                onPasswordChange = { password = it },
                onAction = { action ->
                    performOxidePreflightAction(
                        action = action,
                        operation = operation,
                        password = password,
                        activity = activity,
                        eventViewModel = eventViewModel,
                        errorViewModel = errorViewModel,
                        launchGameViewModel = launchViewModel,
                        backStack = backStack,
                    )
                },
            )
        } else {
            null
        }
    val geometry = rememberLaunchPanelGeometry(metrics)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Oxide.Bg),
        contentAlignment = Alignment.Center,
    ) {
        // 阻断层：按 Play 之后下面的页面既看不见（根节点不透明）也不该还能被点到。
        // 它在面板**之前**声明，而 Compose 的 Main pass 从叶子往根派发，
        // 因此面板那一块仍然先拿到事件——面板内的滚动与按钮不受影响。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )

        OxideLaunchPanel(
            metrics = metrics,
            geometry = geometry,
            surface = surface,
            phase = oxideLaunchPhase(projectedOperation, stages),
            progress = oxideLaunchPanelProgress(stages),
            instanceName = version?.getVersionName()?.takeIf { it.isNotBlank() },
            minecraftVersion = version?.getVersionInfo()?.minecraftVersion?.takeIf { it.isNotBlank() },
            loaderLabel = launchLoaderLabel(version),
            cancelSupported = oxideLaunchCancelSupported(flow != null, projectedOperation),
            onCancel = { launchViewModel.cancel() },
            preflight = preflight,
            stages = stages,
            tasks = tasks,
        )
    }
}

/**
 * 由当前可用尺寸算出面板几何
 *
 * 可用尺寸读 [LocalConfiguration]（分屏、多窗口、折叠屏展开时它小于整块屏幕），
 * 其余全部由 [OxideMetrics] 推导，因此界面上不写死任何一个 dp。
 */
@Composable
internal fun rememberLaunchPanelGeometry(metrics: OxideMetrics): OxideLaunchPanelGeometry {
    val configuration = LocalConfiguration.current
    return remember(metrics, configuration.screenWidthDp, configuration.screenHeightDp) {
        oxideLaunchPanelGeometry(
            metrics = metrics,
            availableWidthDp = configuration.screenWidthDp,
            availableHeightDp = configuration.screenHeightDp,
        )
    }
}

/**
 * 这一次启动要启动的是哪个实例
 *
 * `TryLaunch` 里的实例可以为空（按下 Play 的那一瞬间还没有选中任何实例），
 * 那时 [dev.oxide.launcher.game.launch.GameLaunchFlow] 会自己转成 NoVersion，
 * 因此空就是空，不在这里替它编一个。
 */
private fun launchInstanceOf(operation: LaunchGameOperation): Version? = when (operation) {
    is LaunchGameOperation.TryLaunch -> operation.version
    is LaunchGameOperation.RealLaunch -> operation.version
    is LaunchGameOperation.RendererNoStoragePermission -> operation.version
    is LaunchGameOperation.UnsupportedRenderer -> operation.version
    is LaunchGameOperation.UnsupportedPlugins -> operation.version
    is LaunchGameOperation.AccountRelogin -> operation.version
    is LaunchGameOperation.AccountRefreshFailed -> operation.version
    is LaunchGameOperation.None,
    is LaunchGameOperation.NoAccount,
    is LaunchGameOperation.NoVersion,
    is LaunchGameOperation.InvalidVersionName -> null
}

/**
 * 加载器写什么
 *
 * 原版实例没有加载器：那一行整个不出现，而不是显示一个空的或"未知"的词。
 * 版本清单还没读出来（`versionInfo` 为 null）时同样不出现。
 */
private fun launchLoaderLabel(version: Version?): String? {
    val loader = version?.getVersionInfo()?.primaryLoader ?: return null
    val name = loader.loader.displayName.trim()
    if (name.isEmpty()) return null
    val loaderVersion = loader.version.trim()
    return if (loaderVersion.isEmpty()) name else "$name $loaderVersion"
}

/** pre-flight 那一段需要的四样东西，单独包一层让面板的参数保持可读 */
private class OxideLaunchPreflightSlot(
    val ask: OxidePreflightAsk,
    val texts: OxidePreflightTexts,
    val password: String,
    val onPasswordChange: (String) -> Unit,
    val onAction: (OxidePreflightAction) -> Unit,
)

/**
 * 每个阶段的阶段与进度读数
 *
 * 写进同一批快照状态，组合期读它就等于订阅它，因此准备状态、阶段行与底部那根条
 * 永远是同一份读数——不会出现"行说完成了、进度条还停在上一格"。
 */
private class LaunchStageReadings(
    val stages: androidx.compose.runtime.snapshots.SnapshotStateMap<String, TaskStage>,
    val progresses: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>,
)

/** 后端还没有报过任何进度时 [dev.oxide.launcher.coroutine.Task.progress] 的初值 */
private const val UNSET_PROGRESS = -1f

/**
 * 收集每个阶段的阶段与进度
 *
 * 每个阶段起两个收集协程，写进同一批 [androidx.compose.runtime.snapshots.SnapshotStateMap]；
 * 协程都挂在 `LaunchedEffect` 的作用域里，阶段列表一变就会一起被取消。
 */
@Composable
private fun rememberLaunchStageReadings(tasks: List<TitledTask>): LaunchStageReadings {
    val stages = remember { mutableStateMapOf<String, TaskStage>() }
    val progresses = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(tasks) {
        val ids = tasks.map { it.task.id }
        stages.keys.toList().filter { it !in ids }.forEach { stages.remove(it) }
        progresses.keys.toList().filter { it !in ids }.forEach { progresses.remove(it) }
        tasks.forEach { task ->
            launch { task.task.stage.collect { stages[task.task.id] = it } }
            launch { task.task.progress.collect { progresses[task.task.id] = it } }
        }
    }
    return remember(stages, progresses) { LaunchStageReadings(stages, progresses) }
}

/**
 * 面板本体
 *
 * 高度是**固定值**（[OxideLaunchPanelGeometry.heightDp]），不是 `heightIn`：
 * 里面有一个可竖向滚动的阶段列表，高度一旦不封顶，那一层就会被以
 * `maxHeight = Infinity` 测量并抛
 * `Vertically scrollable component was measured with an infinity maximum height`。
 * 固定高度同时让"面板永远放得进屏幕"这件事在 [oxideLaunchPanelGeometry] 里就可测。
 */
@Composable
private fun OxideLaunchPanel(
    metrics: OxideMetrics,
    geometry: OxideLaunchPanelGeometry,
    surface: OxideLaunchSurface,
    phase: OxideLaunchPhase,
    progress: OxideLaunchPanelProgress,
    instanceName: String?,
    minecraftVersion: String?,
    loaderLabel: String?,
    cancelSupported: Boolean,
    onCancel: () -> Unit,
    preflight: OxideLaunchPreflightSlot?,
    stages: List<OxideLaunchStageSnapshot>,
    tasks: List<TitledTask>,
) {
    Box(
        modifier = Modifier
            .width(geometry.widthDp.dp)
            .height(geometry.heightDp.dp)
            .clip(Oxide.RadiusPanel)
            // DrawerBg 是这一套里唯一"接近不透明"的底板（暗色 0xFA），
            // 与 [OxidePanelShell] 同一块底：面板必须压得住底下的页面，
            // 否则就会退回 v1.6.0 那种两层抢视觉的状态
            .background(Oxide.DrawerBg)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel)
            .padding(metrics.cardGap),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OxideLaunchPanelHeader(
                metrics = metrics,
                phase = phase,
                instanceName = instanceName,
                minecraftVersion = minecraftVersion,
                loaderLabel = loaderLabel,
                cancelSupported = cancelSupported,
                onCancel = onCancel,
            )

            Spacer(Modifier.height(metrics.secRowGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (surface == OxideLaunchSurface.Preflight && preflight != null) {
                    OxideLaunchPreflight(
                        metrics = metrics,
                        ask = preflight.ask,
                        texts = preflight.texts,
                        password = preflight.password,
                        onPasswordChange = preflight.onPasswordChange,
                        onAction = preflight.onAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OxideLaunchStageList(
                        metrics = metrics,
                        geometry = geometry,
                        tasks = tasks,
                        stages = stages,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(metrics.secRowGap))

            OxideLaunchProgressFooter(metrics = metrics, progress = progress)
        }
    }
}

/**
 * 面板头部：准备状态 + 这一次要启动的是谁
 *
 * 实例名用 [Oxide.Type.DrawerTitle]（16sp）而不是 hero 字号：
 * 这一块面板的最大字必须是实例名本身，"启动中" 只作为一行小标签出现。
 */
@Composable
private fun OxideLaunchPanelHeader(
    metrics: OxideMetrics,
    phase: OxideLaunchPhase,
    instanceName: String?,
    minecraftVersion: String?,
    loaderLabel: String?,
    cancelSupported: Boolean,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideSectionLabel(
                text = launchPhaseLabel(phase),
                modifier = Modifier.weight(1f),
            )
            if (cancelSupported) {
                Spacer(Modifier.width(metrics.secRowGap))
                OxideButton(
                    text = stringResource(R.string.oxide_launch_cancel),
                    onClick = onCancel,
                    tone = OxideButtonTone.Ghost,
                )
            }
        }

        Spacer(Modifier.height(metrics.secRowGap))

        Text(
            text = instanceName ?: stringResource(R.string.oxide_launch_no_instance),
            color = Oxide.Fg,
            fontSize = Oxide.Type.DrawerTitle.fontSize,
            lineHeight = Oxide.Type.DrawerTitle.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val target = listOfNotNull(
            minecraftVersion?.let { stringResource(R.string.oxide_launch_minecraft, it) },
            loaderLabel,
        ).joinToString(" · ")
        if (target.isNotEmpty()) {
            Text(
                text = target,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 阶段列表
 *
 * 高度由 [OxideLaunchPanelGeometry.stageViewportHeightDp] 封顶，超出的部分在列表内部滚动，
 * 因此下载校验那一串阶段再多也只占那一块高度，不会把面板撑开，
 * 也永远不会拿到无限高度约束。新阶段进来滚到底部，玩家不用手动追着跑。
 */
@Composable
private fun OxideLaunchStageList(
    metrics: OxideMetrics,
    geometry: OxideLaunchPanelGeometry,
    tasks: List<TitledTask>,
    stages: List<OxideLaunchStageSnapshot>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(tasks.size) {
        if (tasks.isNotEmpty()) listState.animateScrollToItem(tasks.lastIndex)
    }

    LazyColumn(
        // height 而不是 fillMaxSize：列表必须自己占那一块**固定**高度。
        // 外层那个 weight(1f) 的父级虽然已经被面板的固定高度封住，但显式写死
        // 这一层的高度，滚动容器拿到的 maxHeight 就一定有限，不会是 Infinity。
        modifier = modifier
            .fillMaxWidth()
            .height(geometry.stageViewportHeightDp.dp),
        state = listState,
    ) {
        item(key = STAGE_LIST_HEADER_KEY) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OxideSectionLabel(text = stringResource(R.string.oxide_launch_stages))
                if (tasks.isEmpty()) {
                    OxideLoadingRow(stringResource(R.string.oxide_sec_launch_waiting))
                }
            }
        }
        itemsIndexed(
            items = tasks,
            // 阶段 id 在列表里唯一（TitledTask 的 task 以 id 相等），直接拿来当 key
            key = { _, task -> task.task.id },
        ) { index, task ->
            OxideLaunchStageRow(
                metrics = metrics,
                task = task,
                // stages 与 tasks 同序同长；越界只可能来自两帧之间的列表变动，
                // 那种情况下退回"还没有读数"而不是抛异常
                snapshot = stages.getOrNull(index)
                    ?: OxideLaunchStageSnapshot(
                        id = task.task.id,
                        stage = TaskStage.PREPARING,
                        progress = UNSET_PROGRESS,
                    ),
            )
        }
    }
}

/** 列表里那个固定表头的 key，和任务的 key 空间不会撞 */
private const val STAGE_LIST_HEADER_KEY = "oxide_launch_stage_header"

/**
 * 一个阶段
 *
 * 阶段名来自 [TitledTask.title]——它可能是字符串资源，因此走 [resolveAndroidString]
 * 而不是 `toString()`；进度与速率来自任务自己的读数。
 * 状态除了颜色之外还有一行文字，因此不会只靠颜色区分。
 */
@Composable
private fun OxideLaunchStageRow(
    metrics: OxideMetrics,
    task: TitledTask,
    snapshot: OxideLaunchStageSnapshot,
) {
    val title = resolveAndroidString(task.title).text
    val message by task.task.message.collectAsStateWithLifecycle()
    val rate by task.task.rateBytesPerSec.collectAsStateWithLifecycle()

    val determinate = oxideLaunchProgressOf(snapshot.progress) == OxideLaunchProgress.Determinate
    val stageText = when (snapshot.stage) {
        TaskStage.PREPARING -> stringResource(R.string.oxide_launch_state_preparing)
        TaskStage.RUNNING -> stringResource(R.string.oxide_launch_state_running)
        TaskStage.COMPLETED -> stringResource(R.string.generic_done)
    }
    val messageText = message?.let { resolveAndroidString(it).text }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = metrics.secRowGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (snapshot.stage == TaskStage.COMPLETED) Oxide.FgMuted else Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(metrics.secRowGap))
            Text(
                // 负进度代表"不确定"，这时给阶段文字而不是一个假的百分比
                text = if (determinate) {
                    stringResource(
                        R.string.oxide_sec_launch_progress_percent,
                        (snapshot.progress.coerceIn(0f, 1f) * 100f).toInt(),
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
        if (determinate) {
            Spacer(Modifier.height(metrics.rowGap))
            OxideProgressBar(progress = snapshot.progress.coerceIn(0f, 1f))
        }
        if (rate != null && rate > 0L) {
            Text(
                text = stringResource(R.string.oxide_sec_launch_rate, formatFileSize(rate)),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
    }
}

/**
 * 面板底部：进度
 *
 * 只画后端真的给得出的那一种：[OxideLaunchPanelProgress.None] 时整块都不出现，
 * 界面因此永远不会有"看起来在动、其实不知道在动什么"的一根条。
 */
@Composable
private fun OxideLaunchProgressFooter(
    metrics: OxideMetrics,
    progress: OxideLaunchPanelProgress,
) {
    when (progress) {
        OxideLaunchPanelProgress.None -> Unit

        is OxideLaunchPanelProgress.Stage -> {
            OxideProgressBar(progress = progress.fraction)
            Spacer(Modifier.height(metrics.rowGap))
            Text(
                text = stringResource(R.string.oxide_sec_launch_progress_percent, progress.percent),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }

        is OxideLaunchPanelProgress.Stages -> {
            OxideProgressBar(progress = progress.fraction)
            Spacer(Modifier.height(metrics.rowGap))
            Text(
                text = stringResource(
                    R.string.oxide_launch_stage_count,
                    progress.completed,
                    progress.total,
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

/** 准备状态在面板上写什么 */
@Composable
private fun launchPhaseLabel(phase: OxideLaunchPhase): String = when (phase) {
    OxideLaunchPhase.Checking -> stringResource(R.string.oxide_launch_state_checking)
    OxideLaunchPhase.Preparing -> stringResource(R.string.oxide_launch_state_preparing)
    OxideLaunchPhase.Running -> stringResource(R.string.oxide_launch_state_running)
    OxideLaunchPhase.Handoff -> stringResource(R.string.oxide_launch_state_handoff)
}