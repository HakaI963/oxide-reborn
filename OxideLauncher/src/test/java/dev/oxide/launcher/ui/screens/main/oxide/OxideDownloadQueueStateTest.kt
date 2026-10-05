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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Queued for download" 提示条状态机的单测
 *
 * 用户报的现象是"下载任何东西都会出现 Queued for download，但它不会自己消失，除非手动关掉，
 * 而且看不到任何进度"。之前这一层只有一个 `Queued` 状态：任务系统那边的真实进度
 * （`Task.progress`）与真实结束信号（`TaskSystem` 的任务结束监听）一次都没有被接过，
 * 所以除了手点叉号没有任何别的出口。
 *
 * 这里把整张转换表钉死，逐条都是界面上真实会走到的一步：
 *
 *  1. 刚提交：QUEUED，每一个任务都算在跑；
 *  2. 下载中：DOWNLOADING，带着**任务报出来的那个真实比例**；
 *  3. 任务把进度标成 -1f（字节下完、正在拷进实例目录）：INDETERMINATE，
 *     绝不换成任何假数字；
 *  4. 最后一个任务收尾：返回 null，提示条离场；
 *  5. 某一条失败：也算收尾，但**不会**把其余仍在下载的东西一起判死；
 *  6. 手点叉号：幂等。
 */
class OxideDownloadQueueStateTest {

    private val queued = requireNotNull(
        discoverQueueRow(
            previous = null,
            fileName = "sodium-1.0.0.jar",
            tasks = 1,
            stage = DiscoverQueueStage.Queued,
        )
    )

    // ---- 刚提交 ----------------------------------------------------------

    @Test
    fun aFreshQueueIsQueuedWithEveryTaskPending() {
        assertEquals(DiscoverQueueStage.Queued, queued.stage)
        assertEquals(1, queued.total)
        assertEquals(1, queued.pending)
        assertEquals(0, queued.failures)
        assertFalse(queued.resolved)
        // 刚提交时确实什么都不知道，因此是不确定而不是 0%
        assertNull(queued.fraction)
        assertTrue(queued.indeterminate)
    }

    @Test
    fun anEmptySubmissionStillCountsAsOneTask() {
        val row = requireNotNull(
            discoverQueueRow(null, "a.jar", tasks = 0, stage = DiscoverQueueStage.Queued)
        )
        assertEquals(1, row.total)
        assertEquals(1, row.pending)
    }

    // ---- 下载中 ----------------------------------------------------------

    @Test
    fun downloadingCarriesTheExactRealFraction() {
        val row = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Downloading, progress = 0.42f)
        )
        assertEquals(DiscoverQueueStage.Downloading, row.stage)
        // 一个字节都不差：这一格就是任务报上来的那个浮点数
        assertEquals(0.42f, row.fraction!!, 0f)
        assertEquals(0.42f, row.progress, 0f)
        assertFalse(row.indeterminate)
        // 下载期间任务数不变
        assertEquals(1, row.pending)
        assertFalse(row.resolved)
    }

    @Test
    fun aFractionOutsideZeroToOneIsClampedRatherThanDrawnOutside() {
        val high = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Downloading, progress = 1.4f)
        )
        assertEquals(1f, high.fraction!!, 0f)
        val low = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Downloading, progress = -0.2f)
        )
        assertNull(low.fraction)
    }

    // ---- 不确定 ----------------------------------------------------------

    @Test
    fun anIndeterminateProgressIsNeverTurnedIntoAFraction() {
        val row = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Downloading, progress = -1f)
        )
        assertNull(row.fraction)
        assertTrue(row.indeterminate)
        // 原值留着，界面据此画空槽
        assertEquals(-1f, row.progress, 0f)
    }

    @Test
    fun installingIsIndeterminateRatherThanAlmostFull() {
        val downloading = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Downloading, progress = 0.9f)
        )
        val installing = requireNotNull(
            discoverQueueRow(downloading, queued.fileName, stage = DiscoverQueueStage.Installing)
        )
        assertEquals(DiscoverQueueStage.Installing, installing.stage)
        // 绝不写成 1f 假装"快好了"
        assertNull(installing.fraction)
        assertEquals(-1f, installing.progress, 0f)
    }

    // ---- 收尾 -----------------------------------------------------------

    @Test
    fun theLastTaskEndingResolvesTheNotice() {
        val row = discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Complete)
        assertNull("提示条应该在最后一个任务收尾时离场", row)
    }

    @Test
    fun aFailedTaskAlsoResolvesTheNotice() {
        val row = discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Failed, failed = true)
        assertNull("失败同样要收掉提示条", row)
    }

    @Test
    fun nTasksResolveOnlyWhenTheLastOneFinishes() {
        val three = requireNotNull(
            discoverQueueRow(null, "sodium.jar", tasks = 3, stage = DiscoverQueueStage.Queued)
        )
        val afterFirst = requireNotNull(
            discoverQueueRow(three, three.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(2, afterFirst.pending)
        assertFalse(afterFirst.resolved)
        val afterSecond = requireNotNull(
            discoverQueueRow(afterFirst, three.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(1, afterSecond.pending)
        assertFalse(afterSecond.resolved)
        assertNull(discoverQueueRow(afterSecond, three.fileName, stage = DiscoverQueueStage.Complete))
    }

    @Test
    fun oneFailureDoesNotStrandTheRest() {
        val three = requireNotNull(
            discoverQueueRow(null, "sodium.jar", tasks = 3, stage = DiscoverQueueStage.Queued)
        )
        val afterFailure = requireNotNull(
            discoverQueueRow(three, three.fileName, stage = DiscoverQueueStage.Failed, failed = true)
        )
        assertEquals(2, afterFailure.pending)
        assertEquals(1, afterFailure.failures)
        assertFalse("失败的那一条不该把其余仍在下载的东西一起判死", afterFailure.resolved)

        val afterSecond = requireNotNull(
            discoverQueueRow(afterFailure, three.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(1, afterSecond.pending)
        assertEquals(1, afterSecond.failures)
        assertNull(discoverQueueRow(afterSecond, three.fileName, stage = DiscoverQueueStage.Complete))
    }

    @Test
    fun aPendingCountNeverGoesNegative() {
        val two = requireNotNull(discoverQueueRow(null, "a.jar", tasks = 2, stage = DiscoverQueueStage.Queued))
        val afterFirst = requireNotNull(
            discoverQueueRow(two, two.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(1, afterFirst.pending)
        assertFalse(afterFirst.resolved)
        // 最后一条才归零，而且归零就返回 null（提示条离场）
        assertNull(discoverQueueRow(afterFirst, two.fileName, stage = DiscoverQueueStage.Complete))
    }

    // ---- 关闭 -----------------------------------------------------------

    @Test
    fun dismissingIsIdempotent() {
        assertNull(discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Dismissed))
        // 再点一次叉号：上一条已经是"没有"，于是还是同一件事
        assertNull(discoverQueueRow(null, queued.fileName, stage = DiscoverQueueStage.Dismissed))
    }

    @Test
    fun aLateProgressUpdateCannotResurrectAResolvedNotice() {
        // 已经归零的那一行：状态层在最后一个任务收尾时就是从这里决定离场的
        val finished = DiscoverQueueRow(
            fileName = queued.fileName,
            stage = DiscoverQueueStage.Complete,
            progress = 1f,
            total = 2,
            pending = 0,
            failures = 0,
        )
        // 迟到的进度更新不会把提示条变回来：已收尾的那一行再收到任何事件都返回
        // null，也就是"离场"，而不是把一个收尾状态重新挂回状态层
        assertNull(
            discoverQueueRow(finished, finished.fileName, stage = DiscoverQueueStage.Downloading, progress = 0.5f)
        )
        assertEquals(0, late.pending)
        assertEquals(DiscoverQueueStage.Complete, late.stage)
    }

    @Test
    fun aLateTaskEndCannotResurrectAResolvedNotice() {
        val finished = DiscoverQueueRow(
            fileName = queued.fileName,
            stage = DiscoverQueueStage.Complete,
            progress = -1f,
            total = 1,
            pending = 0,
            failures = 0,
        )
        assertNull(discoverQueueRow(finished, finished.fileName, stage = DiscoverQueueStage.Failed, failed = true))
    }

    // ---- 任务阶段怎么折成提示条的阶段 ------------------------------------

    @Test
    fun theRealTaskStageMapsToTheRightQueueStage() {
        // 任务刚建好：还没跑，提示条仍然是 Queued（哪怕进度还是初始的 -1f）
        assertEquals(
            DiscoverQueueStage.Queued,
            discoverQueueStageOf(TaskStage.PREPARING, -1f)
        )
        // 跑了且有真实比例
        assertEquals(
            DiscoverQueueStage.Downloading,
            discoverQueueStageOf(TaskStage.RUNNING, 0.42f)
        )
        // 跑了但进度被标成不确定：就是拷贝/安装阶段
        assertEquals(
            DiscoverQueueStage.Installing,
            discoverQueueStageOf(TaskStage.RUNNING, -1f)
        )
        assertEquals(
            DiscoverQueueStage.Complete,
            discoverQueueStageOf(TaskStage.COMPLETED, 1f)
        )
    }

    @Test
    fun aStageWithoutAQueueRowCannotOpenOne() {
        // 没有前一条就只剩"提交"这一步：迟到的进度事件不会凭空开出一条提示条
        assertNull(
            discoverQueueRow(null, "sodium.jar", stage = DiscoverQueueStage.Downloading, progress = 0.5f)
        )
        val opened: DiscoverQueueRow = requireNotNull(
            discoverQueueRow(null, "sodium.jar", stage = DiscoverQueueStage.Queued)
        )
        assertEquals(1, opened.pending)
    }
}
