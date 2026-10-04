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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「装一个版本」面板的纯逻辑
 *
 * 这里钉住的是三件事，它们都是真机上最容易坏、也最不该靠肉眼看的地方：
 * - 步骤模型：现在在哪一步、能不能往前走、哪一步点不开
 * - 面板尺寸：按内容定大小、上限随窗口变，因此既不会铺满一大片也不会溢出
 * - 进度：后端报"不确定"时必须是不确定，而不是被补一个假的百分比
 */
class OxideInstallFlowLogicTest {

    private fun flow(
        gameVersion: String? = null,
        step: OxideInstallStep = OxideInstallStep.Version,
        supportsLoaded: Boolean = false,
        installing: Boolean = false,
        succeeded: Boolean = false,
    ) = OxideInstallFlowState(
        gameVersion = gameVersion,
        step = step,
        supportsLoaded = supportsLoaded,
        installing = installing,
        succeeded = succeeded,
    )

    // ---- 步骤状态 ---------------------------------------------------------

    @Test
    fun firstStepIsActiveUntilAVersionIsChosen() {
        assertEquals(
            listOf(
                OxideInstallStepState.Active,
                OxideInstallStepState.Blocked,
                OxideInstallStepState.Blocked,
            ),
            oxideInstallStepStates(flow()),
        )
    }

    @Test
    fun loadersAndInstallOpenUpOnceAVersionIsChosen() {
        // 「有版本但还停在第一步」是用户自己退回来改版本的那一段：
        // 后两步已经走得到（不再是 Blocked），而第一步仍然是当前这一步，因此是 Active。
        assertEquals(
            listOf(
                OxideInstallStepState.Active,
                OxideInstallStepState.Pending,
                OxideInstallStepState.Pending,
            ),
            oxideInstallStepStates(flow(gameVersion = "1.21.1")),
        )
        // 选了版本就会立刻前进到第二步，这时第一步才真的走完
        assertEquals(
            listOf(
                OxideInstallStepState.Done,
                OxideInstallStepState.Active,
                OxideInstallStepState.Pending,
            ),
            oxideInstallStepStates(
                flow(gameVersion = "1.21.1", step = OxideInstallStep.Loader)
            ),
        )
    }

    /**
     * 回归：第 N 步只有在 `step` 已经走过它之后才是 Done
     *
     * 三步共用一条规则（"当前这一步永远 Active，往回走一步就重新变成进行中"），
     * 所以任何一步都不会在用户正站在它上面时留着上一轮的 ✓。
     */
    @Test
    fun aStepIsDoneOnlyAfterTheStepAfterItWasReached() {
        val reached = listOf(
            OxideInstallStep.Version,
            OxideInstallStep.Loader,
            OxideInstallStep.Install,
        )
        reached.forEachIndexed { index, step ->
            val states = oxideInstallStepStates(flow(gameVersion = "1.21.1", step = step))
            for (earlier in reached.indices.filter { it < index }) {
                assertEquals(
                    "站在第 ${index + 1} 步时，第 ${earlier + 1} 步必须是 Done",
                    OxideInstallStepState.Done,
                    states[earlier],
                )
            }
            assertEquals(
                "当前这一步必须是 Active",
                OxideInstallStepState.Active,
                states[index],
            )
        }
    }

    @Test
    fun theLoaderStepIsDoneOnlyAfterTheInstallStepWasReached() {
        // 停在第二步：它是进行中，不是完成——用户随时会回来改
        assertEquals(
            OxideInstallStepState.Active,
            oxideInstallStepStates(
                flow(gameVersion = "1.21.1", step = OxideInstallStep.Loader)
            )[1],
        )
        // 走到第三步：第二步才算走完
        assertEquals(
            OxideInstallStepState.Done,
            oxideInstallStepStates(
                flow(
                    gameVersion = "1.21.1",
                    step = OxideInstallStep.Install,
                    supportsLoaded = true,
                )
            )[1],
        )
    }

    @Test
    fun installIsActiveWhileInstallingAndDoneOnlyAfterItLands() {
        val running = oxideInstallStepStates(
            flow(
                gameVersion = "1.21.1",
                step = OxideInstallStep.Install,
                supportsLoaded = true,
                installing = true,
            )
        )
        assertEquals(OxideInstallStepState.Active, running[2])

        val done = oxideInstallStepStates(
            flow(
                gameVersion = "1.21.1",
                step = OxideInstallStep.Install,
                supportsLoaded = true,
                succeeded = true,
            )
        )
        assertEquals(OxideInstallStepState.Done, done[2])
    }

    @Test
    fun everyStepIsStillLabelledWhenNoVersionIsChosen() {
        // 三种状态各有各的字形：做完是勾，走不到是一道横杠
        assertEquals("✓", oxideInstallStepMarker(OxideInstallStepState.Done, 2))
        assertEquals("–", oxideInstallStepMarker(OxideInstallStepState.Blocked, 2))
        assertEquals("3", oxideInstallStepMarker(OxideInstallStepState.Active, 2))
        assertEquals("3", oxideInstallStepMarker(OxideInstallStepState.Pending, 2))
        assertEquals("1", oxideInstallStepMarker(OxideInstallStepState.Pending, 0))
    }

    // ---- 可达性与前进 -----------------------------------------------------

    @Test
    fun nextIsOnlyOfferedWhenThereIsSomethingToMoveTo() {
        assertNull("没选版本就无路可走", oxideInstallNextStep(flow()))
        assertEquals(
            OxideInstallStep.Loader,
            oxideInstallNextStep(flow(gameVersion = "1.21.1")),
        )
    }

    @Test
    fun theLoaderStepIsNotDoneUntilTheListForThisVersionHasArrived() {
        // 版本选了但加载器列表还在拉：按下一步只会落进一个转圈的详情面板
        assertNull(
            oxideInstallNextStep(
                flow(gameVersion = "1.21.1", step = OxideInstallStep.Loader)
            )
        )
        assertEquals(
            OxideInstallStep.Install,
            oxideInstallNextStep(
                flow(
                    gameVersion = "1.21.1",
                    step = OxideInstallStep.Loader,
                    supportsLoaded = true,
                )
            )
        )
    }

    @Test
    fun theInstallStepIsTheEndOfTheLine() {
        assertNull(
            oxideInstallNextStep(
                flow(
                    gameVersion = "1.21.1",
                    step = OxideInstallStep.Install,
                    supportsLoaded = true,
                )
            )
        )
    }

    @Test
    fun laterStepsAreNotSelectableWithoutAVersion() {
        val state = flow()
        assertTrue(oxideInstallStepSelectable(state, OxideInstallStep.Version))
        assertFalse(oxideInstallStepSelectable(state, OxideInstallStep.Loader))
        assertFalse(oxideInstallStepSelectable(state, OxideInstallStep.Install))
    }

    @Test
    fun theStepsAreLockedWhileInstallingAndAfterSuccess() {
        // 装到一半还能往回改选择，会让面板上的进度与实际装的那一份对不上
        val installing = flow(
            gameVersion = "1.21.1",
            supportsLoaded = true,
            installing = true,
        )
        assertFalse(oxideInstallStepSelectable(installing, OxideInstallStep.Loader))
        assertFalse(oxideInstallStepSelectable(installing, OxideInstallStep.Install))
        assertTrue(
            "第一步始终可以回去换版本",
            oxideInstallStepSelectable(installing, OxideInstallStep.Version),
        )

        val succeeded = flow(
            gameVersion = "1.21.1",
            supportsLoaded = true,
            succeeded = true,
        )
        assertFalse(oxideInstallStepSelectable(succeeded, OxideInstallStep.Loader))
        assertFalse(oxideInstallStepSelectable(succeeded, OxideInstallStep.Install))
    }

    // ---- 面板尺寸 ---------------------------------------------------------

    @Test
    fun thePanelNeverExceedsThreeCardWidths() {
        // 参考稿的网格最多三列，面板与它同一个尺度，
        // 因此面板不会在宽屏上变成一个比整页内容还宽的孤立对话框
        val bounds = oxideInstallPanelBounds(
            windowWidthDp = 1920f,
            windowHeightDp = 1080f,
            cardMinWidthDp = 285f,
            cardGapDp = 10f,
        )
        assertEquals(285f * 3f, bounds.maxWidthDp, 0.01f)
    }

    @Test
    fun aNarrowWindowClampsThePanelToTheWindowItself() {
        val bounds = oxideInstallPanelBounds(
            windowWidthDp = 420f,
            windowHeightDp = 300f,
            cardMinWidthDp = 220f,
            cardGapDp = 9f,
        )
        // 四周留白 = cardGap * 2 * 2，因此 420 - 36 = 384，
        // 而"最多三列"是 660，取小的那一个
        assertEquals(384f, bounds.maxWidthDp, 0.01f)
        assertEquals(300f - 36f, bounds.maxHeightDp, 0.01f)
    }

    @Test
    fun thePanelKeepsAMinimumUsableWidth() {
        // 窗口比卡片还窄时，仍然给标题、百分比和取消按钮留得下一行
        val bounds = oxideInstallPanelBounds(
            windowWidthDp = 120f,
            windowHeightDp = 100f,
            cardMinWidthDp = 220f,
            cardGapDp = 9f,
        )
        assertEquals(220f, bounds.maxWidthDp, 0.01f)
        assertEquals(220f, bounds.maxHeightDp, 0.01f)
    }

    @Test
    fun theListAreaLeavesRoomForTheHeaderStepperAndFooter() {
        // 面板 324dp 高：标题行 + 步骤轨 + 底部动作行与它们之间的留白扣掉之后，
        // 清单拿到的就是剩下的那一块，绝不与那三块抢高度
        val list = oxideInstallStepListMaxHeight(
            panelMaxHeightDp = 324f,
            topBarDp = 46f,
            navItemDp = 38f,
            cardGapDp = 9f,
        )
        assertEquals(324f - 46f * 2f - 38f * 2f - 9f * 4f, list, 0.01f)
        assertTrue("清单必须放得下两行", list >= 38f * 2f)
    }

    @Test
    fun aTooShortPanelStillGivesTheListTwoRows() {
        // 扣成负数时兜到两行：再矮就会出现"每行只露一条缝"的滚动区
        val list = oxideInstallStepListMaxHeight(
            panelMaxHeightDp = 60f,
            topBarDp = 46f,
            navItemDp = 38f,
            cardGapDp = 9f,
        )
        assertEquals(76f, list, 0.01f)
    }

    // ---- 百分比 -----------------------------------------------------------

    @Test
    fun percentRoundsAndClamps() {
        assertEquals(0, oxideInstallPercent(0f))
        // 0.426 × 100 = 42.6，四舍五入进位到 43
        assertEquals(43, oxideInstallPercent(0.426f))
        assertEquals(100, oxideInstallPercent(1f))
        assertEquals(100, oxideInstallPercent(1.8f))
        // 负数是后端约定的"进度不可知"，因此没有百分比可言——与 -1f 同一条分支。
        // （这里原本写 0，与同文件里 oxideInstallPercent(-1f) 必须为 null 那条矛盾：
        //   画一个 0% 等于告诉用户"一个字节都还没下"，那是在编。）
        assertNull(oxideInstallPercent(-0.5f))
        // 真正的下界 0.0 仍然是 0%
        assertEquals(0, oxideInstallPercent(0.0001f))
    }

    @Test
    fun anIndeterminateProgressHasNoPercentAtAll() {
        // 后端报"不确定"时画一个 0% 等于告诉用户"一个字节都还没下"——那是在编
        assertNull(oxideInstallPercent(null))
        assertNull(oxideInstallPercent(-1f))
        assertNull(oxideInstallPercent(Float.NaN))
        assertNull(oxideInstallPercent(Float.POSITIVE_INFINITY))
    }

    // ---- 速率 -------------------------------------------------------------

    @Test
    fun speedIsOnlyReportedWhenTheBackendReportedOne() {
        assertEquals(1024L, oxideInstallSpeed(1024L))
        assertNull(oxideInstallSpeed(null))
        // 下载停下来时后端把速率清成 0；显示 "0 B/s" 是把"没有数据"说成了"数据是零"
        assertNull(oxideInstallSpeed(0L))
        assertNull(oxideInstallSpeed(-5L))
    }

    // ---- 已下载 / 总量 ----------------------------------------------------

    @Test
    fun theBackendVolumeTextIsForwardedVerbatim() {
        // 后端用 formatFileSize 写好的 "132/1400 files · 84.21 MB / 210.44 MB"
        // 原样转发：界面不重算字节数，两处数字因此不会对不上。
        // 三段按固定顺序排开，每段之间是 " · "——这里给了速率，速率也在里面
        // （"Waiting · 1.20 MB/s" 那条用例说的正是同一件事）。
        // 原来期望的字符串少了最后一段，与它自己那一条用例自相矛盾。
        val message = "132/1400 files · 84.21 MB / 210.44 MB"
        val line = oxideInstallDetailLine(
            stateLabel = "In progress",
            message = message,
            speedText = "4.20 MB/s",
        )
        assertEquals(
            "In progress · 132/1400 files · 84.21 MB / 210.44 MB · 4.20 MB/s",
            line,
        )
        // 后端那一段逐字出现：没有被重新拼写，也没有被拆开重排
        assertTrue("message must survive verbatim, was: $line", line.contains(message))
    }

    @Test
    fun aMissingVolumeSegmentIsOmittedRatherThanFaked() {
        // 后端没有报消息的加载器下载：那一段整个不画，不拿"下载中"三个字凑数
        assertEquals(
            "Waiting · 1.20 MB/s",
            oxideInstallDetailLine(
                stateLabel = "Waiting",
                message = null,
                speedText = "1.20 MB/s",
            ),
        )
        assertEquals(
            "Done",
            oxideInstallDetailLine(stateLabel = "Done", message = "  ", speedText = null),
        )
    }

    @Test
    fun aZeroRateIsNotPutIntoTheDetailLine() {
        assertEquals(
            "In progress",
            oxideInstallDetailLine(
                stateLabel = "In progress",
                message = null,
                speedText = null,
            ),
        )
    }

    // ---- 整条流程的进度 ---------------------------------------------------

    private fun task(
        id: String,
        state: OxideInstallTaskState,
        progress: Float? = null,
        rate: Long? = null,
    ) = OxideInstallTaskSnapshot(
        id = id,
        state = state,
        progress = progress,
        rateBytesPerSec = rate,
    )

    @Test
    fun anEmptyTaskListMeansTheBackendHasNotSpokenYet() {
        val progress = oxideInstallProgress(emptyList())
        assertEquals(0, progress.total)
        assertNull(progress.runningId)
        assertNull(progress.overall)
        assertNull(progress.runningPercent)
        assertNull(progress.rateBytesPerSec)
    }

    @Test
    fun theRunningTaskIsTheOneTheBackendMarkedRunning() {
        val progress = oxideInstallProgress(
            listOf(
                task("clear", OxideInstallTaskState.Done),
                task("vanilla", OxideInstallTaskState.Running, progress = 0.25f, rate = 2048L),
                task("merge", OxideInstallTaskState.Pending),
            ),
        )
        assertEquals("vanilla", progress.runningId)
        assertEquals(25, progress.runningPercent)
        assertEquals(2048L, progress.rateBytesPerSec)
        assertEquals(1, progress.completed)
        assertEquals(3, progress.total)
    }

    @Test
    fun theOverallProgressCountsFinishedTasksPlusTheRunningOne() {
        val progress = oxideInstallProgress(
            listOf(
                task("clear", OxideInstallTaskState.Done),
                task("vanilla", OxideInstallTaskState.Done),
                task("forge", OxideInstallTaskState.Running, progress = 0.5f),
                task("merge", OxideInstallTaskState.Pending),
            ),
        )
        // (2 完成 + 0.5 / 4) = 0.625
        assertEquals(0.625f, progress.overall!!, 0.0001f)
    }

    @Test
    fun theOverallProgressIsIndeterminateWhileTheRunningTaskHasNoNumber() {
        // 正在跑的那一条还没报出百分比：整条流程此时也没有可用的分母
        val progress = oxideInstallProgress(
            listOf(
                task("clear", OxideInstallTaskState.Done),
                task("vanilla", OxideInstallTaskState.Running, progress = null),
                task("merge", OxideInstallTaskState.Pending),
            ),
        )
        assertNull(progress.overall)
        assertNull(progress.runningPercent)
        assertEquals(1, progress.completed)
    }

    @Test
    fun theOverallProgressIsIndeterminateBetweenTwoTasks() {
        // 上一条刚跑完、下一条还没被标记 RUNNING 的那个窗口：
        // 报"完成度 1/3"或"卡在 1/3"都是在编
        val progress = oxideInstallProgress(
            listOf(
                task("clear", OxideInstallTaskState.Done),
                task("vanilla", OxideInstallTaskState.Pending),
                task("merge", OxideInstallTaskState.Pending),
            ),
        )
        assertNull(progress.overall)
        assertNull(progress.runningId)
    }

    @Test
    fun everythingFinishedMeansOneHundredPercent() {
        val progress = oxideInstallProgress(
            listOf(
                task("clear", OxideInstallTaskState.Done),
                task("vanilla", OxideInstallTaskState.Done, progress = 1f),
            ),
        )
        assertEquals(1f, progress.overall!!, 0.0001f)
        assertNull("没有正在跑的任务时不该报一个百分比", progress.runningPercent)
    }

    @Test
    fun aZeroRateIsNotReportedAsSpeed() {
        // 下载停下来时后端把速率清成 0：显示 "0 B/s" 是把"没有数据"说成了"数据是零"
        assertNull(oxideInstallSpeed(0L))
        val progress = oxideInstallProgress(
            listOf(task("vanilla", OxideInstallTaskState.Running, progress = 0.1f, rate = 0L)),
        )
        assertEquals(0.1f, progress.overall!!, 0.0001f)
        assertNull(progress.rateBytesPerSec)
    }

    @Test
    fun severalRunningTasksResolveToTheFirstOne() {
        // 任务流是顺序执行的，真出现两个时取第一个仍然是一个真实发生过的事实
        val progress = oxideInstallProgress(
            listOf(
                task("first", OxideInstallTaskState.Running, progress = 0.2f),
                task("second", OxideInstallTaskState.Running, progress = 0.9f),
            ),
        )
        assertEquals("first", progress.runningId)
        assertEquals(20, progress.runningPercent)
    }
}