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

import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskHistory
import dev.oxide.launcher.coroutine.TaskOutcome
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.taskHistoryPrepend
import dev.oxide.launcher.coroutine.toHistory
import dev.oxide.launcher.ui.androidText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OxideTaskPanelTest {

    /**
     * Task.updateProgress 约定负值代表"进度不可知"，并且会把 -1f 原样留着。
     * 如果把它当成 0 或者当成一个真百分比，用户看到的就是假的进度条。
     */
    @Test
    fun negativeProgressMeansUnknown() {
        assertNull(oxideTaskProgressPercent(-1f))
        assertNull(oxideTaskProgressPercent(-0.5f))
    }

    @Test
    fun progressPercentIsTruncatedNotRounded() {
        assertEquals(0, oxideTaskProgressPercent(0f))
        assertEquals(49, oxideTaskProgressPercent(0.499f))
        assertEquals(50, oxideTaskProgressPercent(0.5f))
        assertEquals(99, oxideTaskProgressPercent(0.999f))
        assertEquals(100, oxideTaskProgressPercent(1f))
    }

    @Test
    fun progressPercentIsClamped() {
        // updateProgress 本身会夹取，但面板不该依赖调用方一定先夹过
        assertEquals(100, oxideTaskProgressPercent(1.4f))
        assertEquals(0, oxideTaskProgressPercent(0f))
    }

    @Test
    fun rateIsHiddenWhenUnknownOrZero() {
        assertNull(oxideTaskRateText(null))
        assertNull(oxideTaskRateText(0L))
        assertNull(oxideTaskRateText(-1L))
    }

    @Test
    fun rateIsFormattedAsPerSecond() {
        assertEquals("2.00 KB/s", oxideTaskRateText(2048L))
        assertEquals("1.00 MB/s", oxideTaskRateText(1024L * 1024L))
    }

    @Test
    fun everyStageHasItsOwnLabel() {
        val labels = listOf(
            oxideTaskStageLabel(TaskStage.PREPARING),
            oxideTaskStageLabel(TaskStage.RUNNING),
            oxideTaskStageLabel(TaskStage.COMPLETED),
        )
        assertEquals(labels.size, labels.distinct().size)
    }

    // -----------------------------------------------------------------------
    // 三节：排队 / 运行 / 历史
    //
    // 用户报的是"in task memu have history, running option also add queued menu"。
    // 之前面板只有一张扁平列表，跑完的任务直接从 `TaskSystem.tasksFlow` 里消失，于是
    // "装完了没有"这件事永远没有答案。下面这几条把三个分类各自钉死。
    // -----------------------------------------------------------------------

    /**
     * 一个还没开始的任务是**排队**，不是运行
     *
     * 提交进 `TaskSystem` 但协程还没把阶段推到 RUNNING 时，结局仍然是 Queued。
     * 版本安装正是这样：阶段要等 `GameInstaller` 建好任务流之后才有，
     * 而"已提交、正在等"这一段必须有地方显示，否则安装刚开始的那一刻面板是空的。
     */
    @Test
    fun aTaskThatNeverStartedIsQueued() {
        val task = queuedTask()

        assertEquals(TaskOutcome.Queued, task.outcome.value)
        assertEquals(OxideTaskSection.Queued, taskSectionOf(OxideTaskEntry.Live(task)))
    }

    /** 一个已经跑到一半的任务是**运行** */
    @Test
    fun aTaskMidStageIsRunning() {
        val task = queuedTask().apply { updateStage(TaskStage.RUNNING) }

        assertEquals(TaskOutcome.Running, task.outcome.value)
        assertEquals(OxideTaskSection.Running, taskSectionOf(OxideTaskEntry.Live(task)))
    }

    /**
     * 一个抛了异常的任务在**历史**里，而且仍然是"失败"
     *
     * 这是 v1.7.0 那个缺口的正面形式：`TaskSystem` 捕获异常时并不把阶段改成某个失败值
     * （`TaskStage` 根本没有那一个），任务随后就从列表里消失了。合成一列"已结束"就抹掉了
     * "做成了"与"做完过"的区别，因此失败必须单独可辨。
     */
    @Test
    fun aTaskThatThrewIsHistoryWithFailure() {
        val task = queuedTask().apply {
            updateStage(TaskStage.RUNNING)
            markOutcome(TaskOutcome.Failed)
        }

        assertEquals(OxideTaskSection.History, taskSectionOf(OxideTaskEntry.Live(task)))
        assertEquals(TaskOutcome.Failed, task.toHistory().outcome)
        // 进度照留：那正是事后想看的（"下到 42% 就断了"）
        assertEquals(0.42f, task.toHistory().progress)
    }

    /** 三节：一条排队、一条运行、三条收尾，各自落到自己那一节 */
    @Test
    fun entriesAreSplitIntoQueuedRunningAndHistory() {
        val sections = oxideTaskSectionsOf(
            listOf(
                queuedTask("q").toTaskEntry(),
                queuedTask("r").apply { updateStage(TaskStage.RUNNING) }.toTaskEntry(),
                settled("h1", TaskOutcome.Succeeded),
                settled("h2", TaskOutcome.Failed),
                settled("h3", TaskOutcome.Cancelled),
            )
        )

        assertEquals(listOf("q"), sections.queued.map { it.id })
        assertEquals(listOf("r"), sections.running.map { it.id })
        assertEquals(listOf("h1", "h2", "h3"), sections.history.map { it.id })
        assertFalse(sections.empty)
    }

    /**
     * 历史保持最新的在最前
     *
     * `taskHistoryPrepend` 是这一条唯一的实现，所以"最新在前"不是靠一次排序凑出来的：
     * 收尾时把快照放到最前，容量满了挤掉的是**最旧的那一条**。
     */
    @Test
    fun historyKeepsNewestFirst() {
        var history = emptyList<TaskHistory>()
        listOf("first", "second", "third").forEach { id ->
            history = taskHistoryPrepend(history, settledRecord(id, TaskOutcome.Succeeded))
        }

        assertEquals(listOf("third", "second", "first"), history.map { it.id })
    }

    /** 超过上限时丢的是最旧的，而不是最旧的之外随便丢一条 */
    @Test
    fun historyKeepsNewestWhenItOverflows() {
        var history = emptyList<TaskHistory>()
        listOf("a", "b", "c", "d").forEach { id ->
            history = taskHistoryPrepend(history, settledRecord(id, TaskOutcome.Succeeded), limit = 3)
        }

        assertEquals(listOf("d", "c", "b"), history.map { it.id })
        assertEquals(3, history.size)
    }

    /**
     * 三节全空时才用整块空状态
     *
     * 只要历史里还有东西，面板就不该说"没有在跑的东西"——那是另一回事，而用户要的
     * 恰恰是收工之后还能回来看一眼。
     */
    @Test
    fun sectionsAreOnlyEmptyWhenNothingIsLeftAtAll() {
        assertTrue(oxideTaskSectionsOf(emptyList()).empty)
        assertTrue(oxideTaskSectionsOf(listOf(settled("h", TaskOutcome.Failed))).empty.not())
        assertFalse(oxideTaskSectionsOf(listOf(queuedTask().toTaskEntry())).empty)
    }

    /** 五个结局各自有自己的标签，不共用一个 */
    @Test
    fun everyOutcomeHasItsOwnLabel() {
        val labels = TaskOutcome.entries.map { oxideTaskOutcomeLabel(it) }
        assertEquals(labels.size, labels.distinct().size)
    }

    /** 三节标题也各自不同，否则两节会长得一模一样 */
    @Test
    fun everySectionHasItsOwnLabel() {
        val labels = OxideTaskSection.entries.map { oxideTaskSectionLabel(it) }
        assertEquals(labels.size, labels.distinct().size)
    }

    /**
     * 历史里"从没有过进度"的那两种结局不画进度条
     *
     * 画一条空的进度槽读起来就是"下过 0%"，那是假的。被取消与从未开始的两条都没有
     * 可展示的进度，所以整列不显示。
     */
    @Test
    fun historyProgressIsAbsentForOutcomesThatNeverMeasuredAnything() {
        // 两层要分开看，混在一起断言会永远不成立：
        //
        // - `oxideTaskHistoryProgress` 对 Succeeded/Failed 是**原样返回** record.progress，
        //   所以"从没报过进度"的那条给的是 -1f，不是 null；
        // - 把 -1f 变成"不画进度条"的是 `oxideTaskProgressPercent`（它判 < 0f）。
        //
        // 因此下面三条都断言到 `oxideTaskProgressPercent` 这一层，那才是"画不画"的判定。
        assertNull(
            "a succeeded record that never reported progress must not draw a bar",
            oxideTaskProgressPercent(
                oxideTaskHistoryProgress(settledRecord("s", TaskOutcome.Succeeded, progress = -1f)) ?: -1f,
            ),
        )
        assertNull(
            "a cancelled record has nothing to measure, so it must not draw a bar",
            oxideTaskProgressPercent(
                oxideTaskHistoryProgress(settledRecord("c", TaskOutcome.Cancelled)) ?: -1f,
            ),
        )
        assertNull(
            "a queued record is not running yet",
            oxideTaskProgressPercent(
                oxideTaskHistoryProgress(settledRecord("q", TaskOutcome.Queued)) ?: -1f,
            ),
        )
        assertEquals(
            100,
            oxideTaskProgressPercent(
                oxideTaskHistoryProgress(settledRecord("s", TaskOutcome.Succeeded, progress = 1f)) ?: -1f,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // 三节的数据来自哪里，以及"关掉就丢"这件事被真正堵住了
    //
    // 上面那几条只证分类本身是对的。这一节证的是**这份数据活得够久**：
    // 用户的原话是"in task memu have history"，而历史此前只活在一张
    // 一次性列表里——关掉面板、点掉发现页那条提示，它就没了。
    // 这些都是"读源码就能钉死"的事实，因此写成源码断言而不是仪器测试。
    // -----------------------------------------------------------------------

    /**
     * 三节是**一份**数据分出来的，不是三套账
     *
     * 面板里只允许有一次分类调用。三次各自的过滤看起来等价，但它们会在
     * "历史同时出现在在跑列表里"这种情形下分叉——那时候两节各画一次。
     */
    @Test
    fun theThreeSectionsComeFromOneClassificationCall() {
        val panel = code(readSource("ui/screens/main/oxide/OxideTaskPanel.kt"))
        assertEquals(
            "the panel must classify exactly once, not filter three times",
            1,
            panel.split("oxideTaskSectionsOf(").size - 1,
        )
        assertTrue(
            "the drawer must feed the same classification with live tasks and settled records",
            panel.contains("tasks.map { it.toTaskEntry() } + history.map { it.toTaskEntry() }"),
        )
    }

    /**
     * 历史住在任务系统这个进程级单例上，而不是面板自己的状态里
     *
     * 面板是画完就丢的：它一旦从组合里消失，任何记在它内部的 `remember` 都没了。
     * 所以历史必须住在 `TaskSystem` 上，外壳再把它读回来喂给面板。
     */
    @Test
    fun historyLivesOnTheTaskSystemAndNotOnThePanel() {
        val system = code(readSource("coroutine/TaskSystem.kt"))
        assertTrue(
            "TaskSystem must publish the settled records",
            system.contains("val historyFlow = _historyFlow.asStateFlow()"),
        )
        val shell = code(readSource("ui/screens/main/oxide/OxideMainShell.kt"))
        assertTrue(
            "the shell must read the history off the task system",
            shell.contains("TaskSystem.historyFlow.collectAsStateWithLifecycle()"),
        )
        assertTrue(
            "and hand it to the drawer",
            shell.contains("history = taskHistory,"),
        )
    }

    /**
     * 关掉面板、停掉所有任务，都不会清掉历史
     *
     * `stopAll` 清的是"还在跑什么"。历史是"做过什么"，进程结束本来就会一起消失，
     * 清掉它只会让"我刚才那次到底成没成"永远答不上来。
     */
    @Test
    fun closingThePanelAndStoppingTasksNeverEraseTheHistory() {
        val system = code(readSource("coroutine/TaskSystem.kt"))
        val stopAll = system.blockOf("fun stopAll()")
        assertFalse(
            "stopAll must not clear the history; it is a record of what already happened",
            stopAll.contains("_historyFlow"),
        )
        assertTrue(
            "an in-flight task must leave a trace when everything is stopped, not vanish",
            stopAll.contains("forEach { recordHistory(it) }"),
        )
        val discover = code(readSource("ui/screens/main/oxide/OxideDiscoverPage.kt"))
        for (dismiss in listOf("fun dismissInstallState()", "fun dismissInstallError()")) {
            assertFalse(
                "dismissing the notice must not reach into the task system: $dismiss",
                discover.blockOf(dismiss).contains("TaskSystem"),
            )
        }
    }

    /**
     * "装一个 Minecraft 版本"进的是同一份模型
     *
     * 版本安装走 `GameInstaller` 自带的 `TaskFlowExecutor`，永远不会经过
     * `TaskSystem.submitTask`。如果没有人把它登记进来，任务面板的"排队"一节
     * 就只会显示下载，看不见正在装的版本——那正是用户要的东西。
     */
    @Test
    fun versionInstallsJoinTheSameTaskModel() {
        val system = code(readSource("coroutine/TaskSystem.kt"))
        assertTrue(
            "TaskSystem must accept a task it does not execute itself",
            system.contains("fun trackExternalTask(task: Task): String?"),
        )
        assertTrue(
            "an externally tracked task starts out queued",
            system.contains("fun startTrackedTask(id: String)"),
        )
        assertTrue(
            "and is collected into the same history when it settles",
            system.contains("fun finishTrackedTask(id: String, outcome: TaskOutcome)"),
        )
        assertTrue(
            "the install page must actually register its install",
            code(readSource("ui/screens/main/oxide/OxideInstallVersionPage.kt")).contains("TaskSystem.trackExternalTask("),
        )
    }

    /**
     * 面板可以由程序打开，走的是顶栏按钮写的同一个设置
     *
     * 发现页右下角那条安装提示没有面板状态可读，它要的只是把面板叫出来。
     * 因此这一条是"打开"而不是"切换"，但两处写的是同一个设置——否则
     * 面板就有两份互不相干的真相。
     *
     * 两处都收敛到 `MainScreen` 里的那两个具名函数上：顶栏按钮写
     * `changeTasksExpandedState()`，页面叫出面板写 `openTaskPanel()`。断言认的是
     * 这两个函数各自写了哪个设置键，而不是认某一行字面量——写法会变，两条路径
     * 共用同一个键这件事不会。
     */
    @Test
    fun thePanelOpensProgrammaticallyThroughTheSameSettingAsTheTopBar() {
        val main = code(readSource("ui/screens/main/MainScreen.kt"))
        val toggle = main.substringAfter("fun changeTasksExpandedState()").substringBefore("\n    }")
        val open = main.substringAfter("fun openTaskPanel()").substringBefore("\n    }")
        assertTrue(
            "the top bar toggle must write launcherTaskMenuExpanded; got $toggle",
            toggle.contains("AllSettings.launcherTaskMenuExpanded.save("),
        )
        assertTrue(
            "the programmatic open must write that same setting; got $open",
            open.contains("AllSettings.launcherTaskMenuExpanded.save("),
        )
        assertTrue(
            "the programmatic open must not toggle: it has no panel state to read",
            !open.contains("!isTaskMenuExpanded"),
        )
        assertTrue(
            "both call sites must go through those two functions, not inline their own writes",
            main.contains("onToggleTasks = ::changeTasksExpandedState,") &&
                main.contains("onOpenTasks = ::openTaskPanel,"),
        )
        assertTrue(
            "the top bar button must still work",
            main.contains("fun changeTasksExpandedState()"),
        )
        val shell = code(readSource("ui/screens/main/oxide/OxideMainShell.kt"))
        assertTrue(
            "the shell must expose the programmatic open on the host actions",
            shell.contains("val openTaskPanel: () -> Unit,"),
        )
        assertTrue(
            "and pass the host callback straight through",
            shell.contains("openTaskPanel = onOpenTasks,"),
        )
        assertTrue(
            "the notice must consume its own tap by opening the panel",
            code(readSource("ui/screens/main/oxide/OxideDiscoverPage.kt")).contains("openTasks = hostActions.openTaskPanel,"),
        )
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /**
     * 一个构造好但从未提交的任务
     *
     * `Task.runTask` 只**构造**，不启动任何协程，所以这里没有挂起函数、没有派发器、
     * 也没有 `TaskSystem` 的任何记账。默认结局就是 Queued，正是"提交了但还没跑"的状态。
     */
    private fun queuedTask(id: String = "task"): Task =
        Task.runTask(id = id, task = {}).apply { updateProgress(0.42f) }

    private fun settledRecord(
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
    )

    /** 已经收尾的条目：只有一份快照，不带任何活着的流 */
    private fun settled(id: String, outcome: TaskOutcome): OxideTaskEntry =
        OxideTaskEntry.Settled(settledRecord(id, outcome, progress = if (outcome.finished) 1f else -1f))

    // -----------------------------------------------------------------------
    // 源码断言的小工具
    //
    // **索引源码文本的函数一律是 String 的扩展。** 写成顶层函数再在测试体里
    // 调用它自己读文件是行不通的：那样 `source` 既不是参数也不是接收者，编译不通过。
    // CI 上已经因此挂过一次，所以这里一律 `private fun String.xxx(...)`。
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
        error("unbalanced braces starting at offset $start")
    }

    /**
     * 从 [needle] 那一处声明起、到它那个大括号块结束的整段源码
     *
     * 只取声明那一处，因此这个 `indexOf` 不会命中同名调用：
     * 调用写成 `Something(`，而这里找的是 `fun Something(`。
     */
    private fun String.blockOf(needle: String): String {
        val start = indexOf(needle)
        assertTrue("expected to find `$needle`", start >= 0)
        val brace = indexOf('{', start)
        assertTrue("expected `$needle` to have a body", brace >= 0)
        return substring(start, balancedEndFrom(brace) + 1)
    }

    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
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