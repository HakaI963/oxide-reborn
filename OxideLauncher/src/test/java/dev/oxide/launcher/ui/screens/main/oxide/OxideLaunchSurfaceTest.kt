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
import dev.oxide.launcher.ui.screens.content.elements.LaunchGameOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动表面上显示哪一块
 *
 * 这一段在设备上表现为"Home 一直透上来、一个巨大的 Starting 浮在上面"。
 * 根因在呈现层而不在启动链路：v1.6.0 那块面板没有不透明底
 * （[dev.oxide.launcher.ui.theme.Oxide.SurfaceBase] 在暗色下只有 `0x170A0A0A`），
 * 而标题用的正是 `oxide_sec_launch_title`——它的内容就是 "Starting"，
 * 被当成 hero 标题按 [OxideMetrics.heroTitleDp]（Compact 档 29sp，上限 42sp 再乘界面缩放）
 * 画出来，下面还跟着一个同样写着 "Starting" 的 22sp 页面标题；
 * hero 那一列又用 `Arrangement.Bottom` 且不裁切，矮屏上内容直接溢出面板。
 *
 * 因此这里钉死的是"同一次启动只有一种呈现"这条性质，以及
 * "后端给不出确定进度时面板上就不出现那根条"。
 */
class OxideLaunchSurfaceTest {

    private fun stages(
        vararg entries: Pair<TaskStage, Float>,
    ): List<OxideLaunchStageSnapshot> = entries.mapIndexed { index, (stage, progress) ->
        OxideLaunchStageSnapshot(id = "stage-$index", stage = stage, progress = progress)
    }

    // -----------------------------------------------------------------------
    // 哪一块表面
    // -----------------------------------------------------------------------

    @Test
    fun nothingIsShownWhenNoLaunchIsInFlight() {
        assertEquals(
            OxideLaunchSurface.Hidden,
            oxideLaunchSurface(flowActive = false, operation = OxidePreflightOperation.Idle),
        )
        assertEquals(
            OxideLaunchSurface.Hidden,
            oxideLaunchSurface(
                flowActive = false,
                operation = oxidePreflightOperationOf(LaunchGameOperation.None),
            ),
        )
    }

    @Test
    fun anActiveFlowAloneShowsTheProgressSurface() {
        OxidePreflightOperation.entries.forEach { operation ->
            assertEquals(
                "不该被遮住：$operation",
                OxideLaunchSurface.Progress,
                oxideLaunchSurface(flowActive = true, operation = operation),
            )
        }
    }

    /**
     * 回归：流程在跑时，一条还没被复位掉的 pre-flight 不得盖住阶段列表
     *
     * `LaunchGameViewModel.onReloginRequired` / `onRefreshFailed` 是"先写 operation、
     * 再 cancel()"，因此存在一帧 `flow != null` 与"要问用户"的 operation 同时成立。
     * 那一帧里 pre-flight 若优先，面板会整块换掉阶段列表，取消键也跟着消失
     * （`oxideLaunchCancelSupported` 要求 Progress 表面）。operation 一复位，
     * 同一件事仍然是 Preflight——两种呈现不会同时出现。
     */
    @Test
    fun aLiveFlowOutranksALeftoverPreflightOperation() {
        val asking = OxidePreflightOperation.entries.filter { oxidePreflightAsks(it) }
        assertTrue(asking.isNotEmpty())
        asking.forEach { operation ->
            assertEquals(
                "流程在跑时不该换成 pre-flight：$operation",
                OxideLaunchSurface.Progress,
                oxideLaunchSurface(flowActive = true, operation = operation),
            )
            assertEquals(
                "流程一停，同一个 operation 立刻变成 pre-flight：$operation",
                OxideLaunchSurface.Preflight,
                oxideLaunchSurface(flowActive = false, operation = operation),
            )
        }
    }

    /** 按下 Play 到第一条阶段出现之间有一段真实空窗（检查里有挂起的 ensureVulkanSupported） */
    @Test
    fun theCheckingWindowShowsTheProgressSurfaceBeforeAnyStageExists() {
        assertEquals(
            OxideLaunchSurface.Progress,
            oxideLaunchSurface(flowActive = false, operation = OxidePreflightOperation.Checking),
        )
        assertEquals(
            OxideLaunchSurface.Progress,
            oxideLaunchSurface(
                flowActive = false,
                operation = oxidePreflightOperationOf(LaunchGameOperation.TryLaunch(version = null)),
            ),
        )
    }

    /** 每一条要人拿主意的检查都必须落在 Preflight 上，不能退回阶段列表 */
    @Test
    fun everyAskingOperationShowsThePreflightSurface() {
        val asking = OxidePreflightOperation.entries.filter { oxidePreflightAsks(it) }
        assertEquals(
            OxidePreflightBranch.entries.filterNot { it == OxidePreflightBranch.RealLaunch }
                .filterNot { oxidePreflightToastOnly(it) }
                .toSet(),
            asking.mapNotNull { oxidePreflightBranchOf(it) }.toSet(),
        )
        assertTrue(asking.isNotEmpty())
        asking.forEach {
            assertEquals(
                "不该退回阶段列表：$it",
                OxideLaunchSurface.Preflight,
                oxideLaunchSurface(flowActive = false, operation = it),
            )
        }
    }

    /** 没有实例与没有账号只弹一次 toast，因此不该在表面上占一帧 */
    @Test
    fun theToastOnlyBranchesNeverClaimTheSurface() {
        assertTrue(oxidePreflightToastOnly(OxidePreflightBranch.NoVersion))
        assertTrue(oxidePreflightToastOnly(OxidePreflightBranch.NoAccount))
        assertFalse(oxidePreflightAsks(OxidePreflightOperation.NoVersion))
        assertFalse(oxidePreflightAsks(OxidePreflightOperation.NoAccount))
    }

    /**
     * 三种表面两两不同
     *
     * 这条不变量钉住"不可能同时出现两种呈现"：Hidden、Preflight、Progress
     * 是互斥的三分支，任何一次启动只落在其中一支上。
     */
    @Test
    fun theThreeSurfacesAreDistinct() {
        assertEquals(3, OxideLaunchSurface.entries.size)
        assertEquals(3, OxideLaunchSurface.entries.toSet().size)
        assertNotEquals(
            OxideLaunchSurface.Hidden,
            oxideLaunchSurface(flowActive = false, operation = OxidePreflightOperation.Checking),
        )
    }

    // -----------------------------------------------------------------------
    // 准备状态
    // -----------------------------------------------------------------------

    @Test
    fun checkingOutranksEveryStageReading() {
        assertEquals(
            OxideLaunchPhase.Checking,
            oxideLaunchPhase(OxidePreflightOperation.Checking, stages(TaskStage.RUNNING to 0.5f)),
        )
        assertEquals(
            OxideLaunchPhase.Checking,
            oxideLaunchPhase(
                OxidePreflightOperation.UnsupportedRenderer,
                stages(TaskStage.RUNNING to 0.5f),
            ),
        )
    }

    @Test
    fun anEmptyStageListIsStillChecking() {
        assertEquals(
            OxideLaunchPhase.Checking,
            oxideLaunchPhase(OxidePreflightOperation.RealLaunch, emptyList()),
        )
    }

    @Test
    fun aRunningStageMeansRunning() {
        assertEquals(
            OxideLaunchPhase.Running,
            oxideLaunchPhase(
                OxidePreflightOperation.RealLaunch,
                stages(TaskStage.COMPLETED to 1f, TaskStage.RUNNING to 0.4f),
            ),
        )
    }

    /** 全都跑完但流程还没结束（游戏进程正在接手）—— 那是 Handoff，不是 Running */
    @Test
    fun everyStageCompletedMeansHandoff() {
        assertEquals(
            OxideLaunchPhase.Handoff,
            oxideLaunchPhase(
                OxidePreflightOperation.RealLaunch,
                stages(TaskStage.COMPLETED to 1f, TaskStage.COMPLETED to 1f),
            ),
        )
    }

    /**
     * 回归：跑起来之后读到的是 `Idle`，因此准备状态只能由阶段读数决定
     *
     * `LauncherElements` 在 `LaunchGameViewModel.start()` 之后立刻把 operation 复位成
     * `None`，所以整个阶段列表期间 operation 都是 [OxidePreflightOperation.Idle]。
     * 这一条把"没有任何一条 pre-flight 时，阶段说什么就是什么"钉死：
     * Idle 与 RealLaunch 得到的准备状态必须完全一致。
     */
    @Test
    fun thePhaseFollowsTheStagesOnceTheOperationHasBeenReset() {
        assertEquals(
            OxideLaunchPhase.Running,
            oxideLaunchPhase(OxidePreflightOperation.Idle, stages(TaskStage.RUNNING to 0.4f)),
        )
        assertEquals(
            OxideLaunchPhase.Handoff,
            oxideLaunchPhase(OxidePreflightOperation.Idle, stages(TaskStage.COMPLETED to 1f)),
        )
        // 与 RealLaunch 走的是同一条判据：两条 operation 只差在 pre-flight 上
        for (list in listOf(
            stages(TaskStage.RUNNING to 0.4f),
            stages(TaskStage.PREPARING to -1f),
            stages(TaskStage.COMPLETED to 1f),
            emptyList(),
        )) {
            assertEquals(
                "RealLaunch 与 Idle 在阶段上必须一致",
                oxideLaunchPhase(OxidePreflightOperation.RealLaunch, list),
                oxideLaunchPhase(OxidePreflightOperation.Idle, list),
            )
        }
    }

    @Test
    fun stagesThatHaveNotStartedYetMeanPreparing() {
        assertEquals(
            OxideLaunchPhase.Preparing,
            oxideLaunchPhase(
                OxidePreflightOperation.RealLaunch,
                stages(TaskStage.PREPARING to -1f, TaskStage.PREPARING to -1f),
            ),
        )
    }

    /** 四种状态互不重叠，且每一种都有真实输入能到达 */
    @Test
    fun theFourPhasesAreDistinctAndAllReachable() {
        assertEquals(4, OxideLaunchPhase.entries.toSet().size)
        assertEquals(
            OxideLaunchPhase.entries.toSet(),
            listOf(
                oxideLaunchPhase(OxidePreflightOperation.Checking, emptyList()),
                oxideLaunchPhase(
                    OxidePreflightOperation.RealLaunch,
                    stages(TaskStage.PREPARING to -1f),
                ),
                oxideLaunchPhase(
                    OxidePreflightOperation.RealLaunch,
                    stages(TaskStage.RUNNING to 0.3f),
                ),
                oxideLaunchPhase(
                    OxidePreflightOperation.RealLaunch,
                    stages(TaskStage.COMPLETED to 1f),
                ),
            ).toSet(),
        )
    }

    // -----------------------------------------------------------------------
    // 进度：后端给不出确定进度时，面板上就不该有那根条
    // -----------------------------------------------------------------------

    @Test
    fun noStagesMeansNoBar() {
        assertEquals(OxideLaunchPanelProgress.None, oxideLaunchPanelProgress(emptyList()))
    }

    /** [dev.oxide.launcher.coroutine.Task.progress] 的初值就是 -1，因此绝不能画成 0% */
    @Test
    fun anUnreportedProgressIsNotRenderedAsZeroPercent() {
        val progress = oxideLaunchPanelProgress(stages(TaskStage.RUNNING to -1f))
        assertFalse(progress is OxideLaunchPanelProgress.Stage)
        assertEquals(OxideLaunchPanelProgress.Stages(completed = 0, total = 1), progress)
    }

    @Test
    fun aReportedProgressBecomesTheBar() {
        assertEquals(
            OxideLaunchPanelProgress.Stage(fraction = 0.42f, percent = 42),
            oxideLaunchPanelProgress(stages(TaskStage.RUNNING to 0.42f)),
        )
    }

    /** 正在跑的那一条优先于还没开始跑的那一条 */
    @Test
    fun theRunningStageWins() {
        assertEquals(
            OxideLaunchPanelProgress.Stage(fraction = 0.1f, percent = 10),
            oxideLaunchPanelProgress(
                stages(TaskStage.PREPARING to 0.9f, TaskStage.RUNNING to 0.1f)
            ),
        )
    }

    @Test
    fun withoutAnyReportedProgressTheCompletedCountIsUsed() {
        val progress = oxideLaunchPanelProgress(
            stages(
                TaskStage.COMPLETED to 1f,
                TaskStage.RUNNING to -1f,
                TaskStage.RUNNING to -1f,
            )
        )
        assertEquals(OxideLaunchPanelProgress.Stages(completed = 1, total = 3), progress)
        assertEquals(1f / 3f, (progress as OxideLaunchPanelProgress.Stages).fraction, 1e-6f)
    }

    /** 后端越界给进度时，条上画的是夹住的那个数，而不是画到面板外面去 */
    @Test
    fun anOutOfRangeProgressIsClamped() {
        assertEquals(
            OxideLaunchPanelProgress.Stage(fraction = 1f, percent = 100),
            oxideLaunchPanelProgress(stages(TaskStage.RUNNING to 4f)),
        )
        assertEquals(
            OxideLaunchPanelProgress.Stage(fraction = 0f, percent = 0),
            oxideLaunchPanelProgress(stages(TaskStage.RUNNING to 0f)),
        )
    }

    @Test
    fun theThreeProgressReadingsAreDistinct() {
        assertEquals(3, OxideLaunchProgress.entries.toSet().size)
        assertEquals(OxideLaunchProgress.None, oxideLaunchProgressOf(null))
        assertEquals(OxideLaunchProgress.Indeterminate, oxideLaunchProgressOf(-1f))
        assertEquals(OxideLaunchProgress.Indeterminate, oxideLaunchProgressOf(Float.NaN))
        assertEquals(OxideLaunchProgress.Determinate, oxideLaunchProgressOf(0f))
        assertEquals(OxideLaunchProgress.Determinate, oxideLaunchProgressOf(1f))
    }

    // -----------------------------------------------------------------------
    // 取消
    // -----------------------------------------------------------------------

    /**
     * 取消只在真的有东西可取消时出现
     *
     * 没有流程时 `LaunchGameViewModel.cancel()` 是空操作；停在某条 pre-flight 上时
     * 要放弃走的是那一行自己的 Abort。两个时刻都不该再摆一个按了没反应的键。
     */
    @Test
    fun cancelOnlyAppearsWhenThereIsSomethingToCancel() {
        assertFalse(
            oxideLaunchCancelSupported(
                flowActive = false,
                operation = OxidePreflightOperation.Idle,
            )
        )
        assertFalse(
            oxideLaunchCancelSupported(
                flowActive = false,
                operation = OxidePreflightOperation.UnsupportedRenderer,
            )
        )
        assertFalse(
            oxideLaunchCancelSupported(
                flowActive = false,
                operation = OxidePreflightOperation.AccountRefreshFailed,
            )
        )
        assertTrue(
            oxideLaunchCancelSupported(
                flowActive = true,
                operation = OxidePreflightOperation.RealLaunch,
            )
        )
        assertTrue(
            oxideLaunchCancelSupported(
                flowActive = true,
                operation = OxidePreflightOperation.Checking,
            )
        )
    }
}