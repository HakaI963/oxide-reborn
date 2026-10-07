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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "全部下载"结束后提示条卡住的回归单测
 *
 * 报障的现象：单文件下载结束后提示条自己消失，"全部下载"（主文件加依赖，
 * 一次提交多个任务）结束后提示条一直挂着，只能手点叉号。
 *
 * 根因不在状态机：[discoverQueueRow] 里多任务本来就是"最后一个收尾才返回
 * null"。断的是接线：主文件任务在提交时挂了收尾监听，依赖任务走
 * `downloadDependenciesForVersions`，原来既没有传收尾回调，也没有登记
 * `TaskSystem` 的任务结束监听，于是依赖任务跑完永远没有人记账，
 * 计数到不了零，提示条到不了离场。
 *
 * 这里钉死两件事：多任务在各种收尾组合下都会离场，以及依赖提交函数体里
 * 真的有那条接线（后者只能读源码断言，见 OxideLegacyDialogGuardTest 的说明）。
 */
class OxideDiscoverQueueDismissTest {

    // ---- 单文件：对照组 ----------------------------------------------------

    @Test
    fun singleFileCompletingDismissesTheNotice() {
        val queued = requireNotNull(
            discoverQueueRow(null, "sodium-1.0.0.jar", tasks = 1, stage = DiscoverQueueStage.Queued)
        )
        assertNull(
            "单文件跑完提示条就该离场，这是原来唯一走得通的那条路",
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
    }

    // ---- 全部下载：全部成功 -------------------------------------------------

    @Test
    fun downloadAllDismissesWhenEveryTaskSucceeds() {
        // 主文件加两个依赖：这正是"全部下载"交给 startQueue 的形状
        val queued = requireNotNull(
            discoverQueueRow(
                null,
                "sodium-extra-fabric-0.9.4+mc26.3.jar",
                tasks = 3,
                stage = DiscoverQueueStage.Queued
            )
        )
        assertEquals(3, queued.pending)

        // 主文件的真实进度照常落下，不影响计数
        val downloading = requireNotNull(
            discoverQueueRow(
                queued,
                queued.fileName,
                stage = DiscoverQueueStage.Downloading,
                progress = 0.5f
            )
        )
        assertEquals(3, downloading.pending)
        val installing = requireNotNull(
            discoverQueueRow(downloading, queued.fileName, stage = DiscoverQueueStage.Installing)
        )
        assertEquals(3, installing.pending)

        // 三个任务逐个收尾：前两个只减数，最后一个才离场
        val afterFirst = requireNotNull(
            discoverQueueRow(installing, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(2, afterFirst.pending)
        val afterSecond = requireNotNull(
            discoverQueueRow(afterFirst, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(1, afterSecond.pending)
        assertNull(
            "三个任务全部收尾，提示条必须自己消失而不是等手点叉号",
            discoverQueueRow(afterSecond, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
    }

    // ---- 全部下载：其中一个失败 ----------------------------------------------

    @Test
    fun downloadAllWithOneFailureStillDismisses() {
        val queued = requireNotNull(
            discoverQueueRow(null, "sodium-extra-fabric-0.9.4+mc26.3.jar", tasks = 3, stage = DiscoverQueueStage.Queued)
        )
        val afterFailure = requireNotNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Failed, failed = true)
        )
        assertEquals(2, afterFailure.pending)
        assertEquals(1, afterFailure.failures)
        assertFalse("失败的那一条不能把其余仍在跑的东西一起判死", afterFailure.resolved)

        val afterSecond = requireNotNull(
            discoverQueueRow(afterFailure, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
        assertEquals(1, afterSecond.pending)
        assertEquals(1, afterSecond.failures)
        assertNull(
            "有一条失败、其余成功，整批落定后提示条同样不能赖在屏幕上",
            discoverQueueRow(afterSecond, queued.fileName, stage = DiscoverQueueStage.Complete)
        )
    }

    // ---- 手点叉号之后再下 ----------------------------------------------------

    @Test
    fun dismissThenNewDownloadShowsTheNoticeAgain() {
        val queued = requireNotNull(
            discoverQueueRow(null, "sodium-1.0.0.jar", tasks = 1, stage = DiscoverQueueStage.Queued)
        )
        assertNull(
            discoverQueueRow(queued, queued.fileName, stage = DiscoverQueueStage.Dismissed)
        )
        // 叉号收掉的是上一批：新一次提交就是新的一件事，提示条照常出现
        val reopened = discoverQueueRow(
            null,
            "another-mod-2.0.0.jar",
            tasks = 2,
            stage = DiscoverQueueStage.Queued
        )
        assertNotNull("手点叉号不能让后面的下载再也挂不上提示条", reopened)
        assertEquals(2, reopened!!.pending)
        assertFalse(reopened.resolved)
    }

    // ---- 接线守卫：依赖任务的收尾必须报给队列 ----------------------------------

    @Test
    fun everyDependencyTaskReportsItsEndingToTheQueue() {
        val body = submitDependencyBody(pageSource())
        assertTrue(
            "依赖提交必须拿到建好的任务，否则收尾监听无处可挂",
            body.contains("onTaskCreated")
        )
        assertTrue(
            "依赖任务收尾必须登记到任务系统，否则跑完没有人记账",
            body.contains("putTaskEndedListener")
        )
        assertTrue(
            "依赖任务的收尾必须走队列同一记账入口，否则多任务计数到不了零",
            body.contains("onQueueTaskEnded")
        )
    }

    @Test
    fun dependencyEndingCarriesTheRealOutcome() {
        val body = submitDependencyBody(pageSource())
        assertTrue(
            "依赖的成败必须按任务真实阶段判定，而不是写死成功或失败",
            body.contains("TaskStage.COMPLETED")
        )
    }

    // ---- 读源码的 helpers ----------------------------------------------------

    private fun pageSource(): String =
        codeOf(locate("ui/screens/main/oxide/OxideDiscoverPage.kt").readText())

    /**
     * `submitDependency` 整个函数体的源码
     *
     * 只看这一个函数：主文件提交本来就有收尾监听，整文件断言会把它当成
     * 依赖的接线，造成"修好了"的假阳性。
     */
    private fun submitDependencyBody(source: String): String {
        val anchor = "fun submitDependency("
        val start = source.indexOf(anchor)
        assertTrue("could not find submitDependency", start >= 0)
        val rest = source.substring(start)
        val next = Regex("\n    (private )?fun ").find(rest, anchor.length)
        assertTrue("could not find the end of submitDependency", next != null)
        return rest.substring(0, next!!.range.first)
    }

    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    private fun locate(relativePath: String): File {
        val root = "src/main/java/dev/oxide/launcher/" + relativePath
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(root)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate " + root + " from " + File("").absolutePath)
    }

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}
