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

import dev.oxide.launcher.coroutine.TASK_HISTORY_LIMIT
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskHistory
import dev.oxide.launcher.coroutine.TaskKind
import dev.oxide.launcher.coroutine.TaskOutcome
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.taskHistoryPrepend
import dev.oxide.launcher.coroutine.toHistory
import dev.oxide.launcher.ui.androidText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 下载中 / 已完成两节
 *
 * 用户要的是任务菜单里"下载中"与"已完成"两个子类：下完了就去已完成那一边，
 * 实例与版本的下载在没开始时先排在下载中（Queued），而不是面板上一片空白。
 * 结构只有一种：下载类进下载中 / 已完成，一般任务还是排队 / 运行中 / 历史，
 * 全都由同一份 `oxideTaskSectionsOf` 分类喂出来，没有第二套账。
 */
class OxideTaskDownloadSectionsTest {

    /** 还没开始的下载是下载中，不是排队那一边 */
    @Test
    fun queuedDownloadIsDownloading() {
        val task = downloadTask("dl-queued")

        assertEquals(TaskOutcome.Queued, task.outcome.value)
        assertEquals(OxideTaskSection.Downloading, taskSectionOf(task))
        assertEquals(OxideTaskSection.Downloading, taskSectionOf(task.toTaskEntry()))
    }

    /** 正在跑的下载也是下载中：排队与运行中在这一节里合并 */
    @Test
    fun runningDownloadIsDownloading() {
        val task = downloadTask("dl-running").apply { updateStage(TaskStage.RUNNING) }

        assertEquals(TaskOutcome.Running, task.outcome.value)
        assertEquals(OxideTaskSection.Downloading, taskSectionOf(task.toTaskEntry()))
    }

    /** 下完的下载去已完成那一边 */
    @Test
    fun succeededDownloadRecordIsComplete() {
        val entry = downloadSettled("dl-done", TaskOutcome.Succeeded)

        assertEquals(OxideTaskSection.Complete, taskSectionOf(entry))
    }

    /**
     * 炸掉的下载也在已完成里，但标成失败
     *
     * 失败必须看得见：这一节不是"成功"，而是"不再下载了"——发现页那条队列提示
     * 收尾时也是 Complete 与 Failed 两条路。因此失败的行靠结局标记区分，
     * 而不是凭空消失或混进历史。
     */
    @Test
    fun failedDownloadRecordIsCompleteMarkedFailed() {
        val entry = downloadSettled("dl-failed", TaskOutcome.Failed)

        assertEquals(OxideTaskSection.Complete, taskSectionOf(entry))
        assertEquals(TaskOutcome.Failed, (entry as OxideTaskEntry.Settled).record.outcome)
        assertTrue(
            "a failed download must not wear the succeeded caption",
            oxideTaskOutcomeLabel(TaskOutcome.Failed) != oxideTaskOutcomeLabel(TaskOutcome.Succeeded),
        )
    }

    /** 用户取消的下载同样收进已完成，标记是 Cancelled 而不是 Done */
    @Test
    fun cancelledDownloadRecordIsComplete() {
        val entry = downloadSettled("dl-cancelled", TaskOutcome.Cancelled)

        assertEquals(OxideTaskSection.Complete, taskSectionOf(entry))
        assertTrue(
            "a cancelled download must not wear the succeeded caption",
            oxideTaskOutcomeLabel(TaskOutcome.Cancelled) != oxideTaskOutcomeLabel(TaskOutcome.Succeeded),
        )
    }

    /** 一般任务不动：还是排队 / 运行中 / 历史，下载中与已完成两节是空的 */
    @Test
    fun generalTasksKeepOldSections() {
        val sections = oxideTaskSectionsOf(
            listOf(
                generalTask("g-queued").toTaskEntry(),
                generalTask("g-running").apply { updateStage(TaskStage.RUNNING) }.toTaskEntry(),
                generalSettled("g-history", TaskOutcome.Succeeded),
            )
        )

        assertEquals(listOf("g-queued"), sections.queued.map { it.id })
        assertEquals(listOf("g-running"), sections.running.map { it.id })
        assertEquals(listOf("g-history"), sections.history.map { it.id })
        assertTrue(sections.downloading.isEmpty())
        assertTrue(sections.complete.isEmpty())
        assertTrue(!sections.empty)
    }

    /**
     * 收尾即搬家：下载中那一行成功之后出现在已完成
     *
     * 这就是 `TaskSystem.onTaskEnded` 做的两件事（在跑列表摘掉、历史最前面补一条快照）
     * 在面板上的投影：同一份输入按收尾前后各分一次，两次各落一边。
     */
    @Test
    fun completionMovesDownloadToCompleteSide() {
        val task = downloadTask("dl-life").apply { updateStage(TaskStage.RUNNING) }
        assertEquals(
            OxideTaskSection.Downloading,
            taskSectionOf(oxideTaskSectionsOf(listOf(task.toTaskEntry())).downloading.single()),
        )

        task.markOutcome(TaskOutcome.Succeeded)
        val snapshot = task.toHistory()
        val after = oxideTaskSectionsOf(listOf(snapshot.toTaskEntry()))

        assertTrue(after.downloading.isEmpty())
        assertEquals(listOf("dl-life"), after.complete.map { it.id })
    }

    /** 五节混在一起时各归其位，且各自保持传入顺序 */
    @Test
    fun mixedEntriesLandInTheirOwnSectionsInOrder() {
        val sections = oxideTaskSectionsOf(
            listOf(
                downloadTask("dl-q").toTaskEntry(),
                generalTask("g-q").toTaskEntry(),
                downloadTask("dl-r").apply { updateStage(TaskStage.RUNNING) }.toTaskEntry(),
                generalTask("g-r").apply { updateStage(TaskStage.RUNNING) }.toTaskEntry(),
                downloadSettled("dl-ok", TaskOutcome.Succeeded),
                downloadSettled("dl-bad", TaskOutcome.Failed),
                generalSettled("g-ok", TaskOutcome.Succeeded),
            )
        )

        assertEquals(listOf("dl-q", "dl-r"), sections.downloading.map { it.id })
        assertEquals(listOf("dl-ok", "dl-bad"), sections.complete.map { it.id })
        assertEquals(listOf("g-q"), sections.queued.map { it.id })
        assertEquals(listOf("g-r"), sections.running.map { it.id })
        assertEquals(listOf("g-ok"), sections.history.map { it.id })
    }

    /**
     * 类别跟着任务进历史：下载收尾不会掉进历史那一边
     *
     * `toHistory` 必须把 kind 带过去，否则"下载中收尾"与"历史多一条"只发生一件，
     * 面板就会出现同一件事两处都没有。
     */
    @Test
    fun downloadKindSurvivesIntoHistorySnapshot() {
        assertEquals(TaskKind.Download, downloadTask("k").toHistory().kind)
        assertEquals(TaskKind.General, generalTask("k").toHistory().kind)
    }

    /** 历史上限与最新在前原样保留：五节只是换了分法，不碰记账 */
    @Test
    fun historyCapAndNewestFirstAreUntouched() {
        assertEquals(40, TASK_HISTORY_LIMIT)
        var history = emptyList<TaskHistory>()
        repeat(TASK_HISTORY_LIMIT + 5) { index ->
            history = taskHistoryPrepend(history, downloadRecord("dl-" + index, TaskOutcome.Succeeded))
        }

        assertEquals(TASK_HISTORY_LIMIT, history.size)
        assertEquals("dl-44", history.first().id)
        assertEquals(
            "overflowing must drop the oldest, not a random one",
            "dl-5", history.last().id,
        )
    }

    /** 五节标题各不相同，否则两节会长得一模一样 */
    @Test
    fun everySectionHasItsOwnLabelFive() {
        val labels = OxideTaskSection.entries.map { oxideTaskSectionLabel(it) }
        assertEquals(labels.size, labels.distinct().size)
    }

    // -----------------------------------------------------------------------
    // 源码断言：面板的接线与桥接的调用
    //
    // 下面几条读的是源码文本，不是运行行为：要钉的是"只有一处分类"与
    // "桥接调了哪三个函数"，这两件事单测跑不出来。
    // -----------------------------------------------------------------------

    /** 面板仍然只分类一次，喂的还是在跑加历史那一份数据 */
    @Test
    fun panelStillClassifiesOnceFromOneDataSource() {
        val panel = code(readSource("ui/screens/main/oxide/OxideTaskPanel.kt"))
        assertEquals(
            "the panel must classify exactly once, not filter five times",
            1, panel.split("oxideTaskSectionsOf(").size - 1,
        )
        assertTrue(
            "the drawer must feed the same classification with live tasks and settled records",
            panel.contains("tasks.map { it.toTaskEntry() } + history.map { it.toTaskEntry() }"),
        )
    }

    /** 面板先画下载中与已完成：那是用户点开抽屉最想看的两节 */
    @Test
    fun panelDrawsDownloadingAndCompleteFirst() {
        val panel = code(readSource("ui/screens/main/oxide/OxideTaskPanel.kt"))
        val order = listOf(
            "OxideTaskSection.Downloading",
            "OxideTaskSection.Complete",
            "OxideTaskSection.Queued",
            "OxideTaskSection.Running",
            "OxideTaskSection.History",
        ).map { name ->
            assertTrue("the panel must draw section " + name, panel.contains(name))
            panel.indexOf(name)
        }
        assertEquals("sections must be drawn downloading-first", order, order.sorted())
    }

    /**
     * 外部登记默认就是下载类
     *
     * 安装页调的是单参数的 `trackExternalTask(task)`，它不传 kind；
     * 单参数重载必须委托给下载的那一条，版本安装那一行才能自动落在下载中那一边。
     */
    @Test
    fun externalTrackingDefaultsToDownloadKind() {
        val system = code(readSource("coroutine/TaskSystem.kt"))
        assertTrue(
            "the single-arg overload must stay for existing callers",
            system.contains("fun trackExternalTask(task: Task): String?"),
        )
        assertTrue(
            "the single-arg overload must default to download kind",
            system.contains("= trackExternalTask(task, isDownload = true)"),
        )
        assertTrue(
            "submitDownloadTask must exist for the discover call sites to adopt",
            system.contains("fun submitDownloadTask(task: Task)"),
        )
    }

    /**
     * GameInstaller 按安装页的同一套路桥接：登记、推进、收尾
     *
     * 只加跟踪调用，不碰下载行为： phases 的装配、重试、并发与线程都在原来的函数里，
     * 这里只断言多了哪几个调用。id 与安装页共用同一个前缀拼法，见下一条。
     */
    @Test
    fun gameInstallerBridgesWithTrackStartFinish() {
        val installer = code(readSource("game/download/game/GameInstaller.kt"))
        assertTrue(
            "installGame must register its download before the phases exist",
            installer.blockOf("fun installGame(").contains("TaskSystem.trackExternalTask("),
        )
        assertTrue(
            "installGame must push the row to running once the flow starts",
            installer.blockOf("fun installGame(").contains("TaskSystem.startTrackedTask("),
        )
        assertTrue(
            "installGame must settle the row on completion",
            installer.blockOf("fun installGame(").contains("finishTrackedDownload(TaskOutcome.Succeeded)"),
        )
        assertTrue(
            "installGame must settle the row on error",
            installer.blockOf("fun installGame(").contains("finishTrackedDownload(TaskOutcome.Failed)"),
        )
        assertTrue(
            "installGame must settle the row when the flow itself is cancelled",
            installer.blockOf("fun installGame(").contains("onCancel = {"),
        )
        assertTrue(
            "modifyVersion must bridge the same way",
            installer.blockOf("fun modifyVersion(").contains("TaskSystem.trackExternalTask("),
        )
        assertTrue(
            "cancelling must settle the row instead of leaving it in downloading",
            installer.blockOf("fun cancelInstall(").contains("finishTrackedDownload(TaskOutcome.Cancelled)"),
        )
    }

    /**
     * 同一次安装在面板上只有一行：两边拼的是同一个 id
     *
     * 安装页用私有的 `TASK_ID_PREFIX`，`GameInstaller` 用任务系统的构造器；
     * 值必须相等，否则同一次安装会出现"正在安装"与"正在下载"两行。
     * 后到的登记返回 null 并跳过自家的 start/finish，于是谁都不干扰谁。
     */
    @Test
    fun gameInstallerSharesTheInstallRowIdWithTheInstallPage() {
        val page = readSource("ui/screens/main/oxide/OxideInstallVersionPage.kt")
        assertTrue(
            "the install page contract this depends on must still hold",
            page.contains("TASK_ID_PREFIX = \"oxide-version-install:\""),
        )
        val system = readSource("coroutine/TaskSystem.kt")
        assertTrue(
            "the shared prefix must still hold",
            system.contains("VERSION_INSTALL_TASK_ID_PREFIX = \"oxide-version-install:\""),
        )
        val installer = code(readSource("game/download/game/GameInstaller.kt"))
        assertTrue(
            "GameInstaller must build its id from the shared constructor",
            installer.contains("versionInstallTaskId(info.customVersionName)"),
        )
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /** 一个构造好但从未提交的下载类任务：默认结局就是 Queued，正是"排队等下载"的状态 */
    private fun downloadTask(id: String = "download"): Task =
        Task.runTask(id = id, task = {}, kind = TaskKind.Download).apply { updateProgress(0.42f) }

    /** 一个构造好但从未提交的一般任务 */
    private fun generalTask(id: String = "task"): Task =
        Task.runTask(id = id, task = {}).apply { updateProgress(0.42f) }

    private fun downloadRecord(
        id: String,
        outcome: TaskOutcome,
        progress: Float = -1f,
    ): TaskHistory = TaskHistory(
        id = id,
        title = androidText("Downloading"),
        message = androidText("sodium-fabric-0.6.0.jar"),
        progress = progress,
        rateBytesPerSec = null,
        outcome = outcome,
        kind = TaskKind.Download,
    )

    private fun generalRecord(
        id: String,
        outcome: TaskOutcome,
        progress: Float = -1f,
    ): TaskHistory = TaskHistory(
        id = id,
        title = androidText("Refreshing"),
        message = androidText("skin"),
        progress = progress,
        rateBytesPerSec = null,
        outcome = outcome,
        kind = TaskKind.General,
    )

    /** 已经收尾的下载条目：只有一份快照，不带任何活着的流 */
    private fun downloadSettled(id: String, outcome: TaskOutcome): OxideTaskEntry =
        OxideTaskEntry.Settled(downloadRecord(id, outcome, progress = if (outcome.finished) 1f else -1f))

    /** 已经收尾的一般条目 */
    private fun generalSettled(id: String, outcome: TaskOutcome): OxideTaskEntry =
        OxideTaskEntry.Settled(generalRecord(id, outcome, progress = if (outcome.finished) 1f else -1f))

    // -----------------------------------------------------------------------
    // 源码断言的小工具
    //
    // **索引源码文本的函数一律是 String 的扩展。** 写成顶层函数再在测试体里
    // 调用它自己读文件是行不通的：那样 `source` 既不是参数也不是接收者，编译不通过。
    // -----------------------------------------------------------------------

    /** 去掉字符串与注释，只留下真正会被编译的代码 */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    private fun readSource(name: String): String = locate(name).readText()

    /**
     * 从 [start] 开始的那一处 `}` 的下标（[start] 指向配对的 `{`）
     *
     * 引号里的花括号不算：字符串虽然已经被 [code] 换空，但字符字面量还在，
     * 跳过它们才不会把 `{'}'}` 那种写法数成一次进出。
     */
    private fun String.balancedEndFrom(start: Int): Int {
        var depth = 0
        var index = start
        while (index < length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }

                '"', '\'' -> {
                    val quote = this[index]
                    index++
                    while (index < length && this[index] != quote) {
                        if (this[index] == '\\') index++
                        index++
                    }
                }
            }
            index++
        }
        error("unbalanced braces starting at offset " + start)
    }

    /**
     * 从 [needle] 那一处声明起、到它那个大括号块结束的整段源码
     *
     * 只取声明那一处，因此这个 `indexOf` 不会命中同名调用：
     * 调用写成 `Something(`，而这里找的是 `fun Something(`。
     */
    private fun String.blockOf(needle: String): String {
        val start = indexOf(needle)
        assertTrue("expected to find " + needle, start >= 0)
        // 先跳过参数列表：参数默认值里可能带着 lambda（如 `isRunning: () -> Unit = {}`），
        // 直接找第一个 `{` 会切进那个默认值，取到的只是签名而不是函数体。
        val params = parenEndFrom(indexOf('(', start))
        assertTrue("expected " + needle + " to have a parameter list", params >= 0)
        val brace = indexOf('{', params)
        assertTrue("expected " + needle + " to have a body", brace >= 0)
        return substring(start, balancedEndFrom(brace) + 1)
    }

    /**
     * 与 [balancedEndFrom] 配对的那一半：从 `(` 出发找配对的 `)`。
     *
     * 引号与注释已经被 [code] 抹掉，这里只数括号；单引号字符字面量里
     * 的括号由调用方保证不存在——本文件的两个被测函数签名里确实没有。
     */
    private fun String.parenEndFrom(open: Int): Int {
        var depth = 0
        var index = open
        while (index < this.length) {
            when (this[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }

    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/" + relativePath)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/java/dev/oxide/launcher/" + relativePath + " from " +
                File("").absolutePath
        )
    }

    private companion object {
        /** 原始字符串：里面可以出现引号、换行与注释符号，必须先换掉 */
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}
