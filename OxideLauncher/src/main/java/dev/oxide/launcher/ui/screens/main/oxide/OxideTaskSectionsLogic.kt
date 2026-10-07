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

import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskHistory
import dev.oxide.launcher.coroutine.TaskKind
import dev.oxide.launcher.coroutine.TaskOutcome
import dev.oxide.launcher.ui.AndroidStringText

/**
 * 任务面板的五节分法
 *
 * 面板只有**一份**数据——`TaskSystem` 的在跑列表加它自己留的收尾快照
 * （`TaskSystem.tasksFlow` 与 `TaskSystem.historyFlow`）——五节是这份数据在两个维度上的投影：
 *
 *  - **下载中**：还没收尾的下载类任务，排队与运行中合并。版本安装也落在这里：它走的是
 *    `GameInstaller` 自带的 `TaskFlowExecutor`，从来不会经过 `TaskSystem.submitTask`，
 *    于是由 `TaskSystem.trackExternalTask` 把同一种 [Task] 挂进同一份列表——否则这一节只会
 *    显示下载，看不见正在装的 Minecraft 版本。排队还是运行中只差在结局是 Queued 还是
 *    Running，画成一节用户才不用猜"它到底动没动"。
 *  - **已完成**：已经收尾的下载类任务，成功 / 失败 / 取消都在这里，靠行尾的结局标记区分。
 *    这正是发现页那条队列提示的语义（`DiscoverQueueStage.Complete` 与 `Failed` 都收掉提示条），
 *    只是搬进了抽屉：失败的那一行留着当时的进度（"下到 42% 就断了"），而不是凭空消失。
 *    取消的也在这一节：它同样是"不再下载了"，结局标记会如实写成 Cancelled。
 *  - **排队**：交出去了、还没开始跑的一般任务（[TaskOutcome.Queued]）。
 *  - **运行中**：正在跑的一般任务（[TaskOutcome.Running]）。
 *  - **历史**：已经收尾的一般任务。顺序由 `taskHistoryPrepend` 钉死为
 *    **最新的在最前**，这里原样透传，不再排一次。
 *
 * "下载类还是一般"由 [TaskKind] 定，[Task.toHistory] 会把它带进快照，
 * 因此一条下载收尾之后不会掉进历史那一边。分节是纯函数，于是"一个从没开始过的下载算下载中、
 * 一个跑到一半的下载算下载中、一个抛了异常的下载在已完成里且标成失败"这几条各自都能被
 * 一条单测钉死——不需要 Compose，也不需要真的跑一条协程。
 */

/** 面板的五节，顺序即绘制顺序 */
internal enum class OxideTaskSection {
    Downloading,
    Complete,
    Queued,
    Running,
    History,
}

/**
 * 面板里的一条
 *
 * 分成两半是因为它们**会不会再变**不一样：[Live] 背后是任务的四个 StateFlow，绘制时要各自
 * 收集一次才能看到进度往前；[Settled] 背后是一份收尾那一刻定下来的快照，再也不会被改写，
 * 因此不订阅任何流。把两者混在一个数据类里，结果就是要么历史那几行白白订阅四没人写的流，
 * 要么在跑那几行不再更新——两者都曾经是真的缺陷。
 */
internal sealed interface OxideTaskEntry {
    val id: String

    /** 还在跑，或者已经交出去排队 */
    data class Live(val task: Task) : OxideTaskEntry {
        override val id: String get() = task.id
    }

    /** 已经收尾：成功、抛异常或被取消 */
    data class Settled(val record: TaskHistory) : OxideTaskEntry {
        override val id: String get() = record.id
    }
}

/** 面板五节的全部内容 */
internal data class OxideTaskSections(
    val downloading: List<OxideTaskEntry> = emptyList(),
    val complete: List<OxideTaskEntry> = emptyList(),
    val running: List<OxideTaskEntry> = emptyList(),
    val queued: List<OxideTaskEntry> = emptyList(),
    val history: List<OxideTaskEntry> = emptyList(),
) {
    /** 五节全空：界面据此显示"没有在跑的东西"，而不是画五个空标题 */
    val empty: Boolean
        get() = downloading.isEmpty() && complete.isEmpty() &&
            running.isEmpty() && queued.isEmpty() && history.isEmpty()

    /** 某一节的条目；绘制那一步按分节标题依次取，空的那一节整节跳过 */
    fun of(section: OxideTaskSection): List<OxideTaskEntry> =
        when (section) {
            OxideTaskSection.Downloading -> downloading
            OxideTaskSection.Complete -> complete
            OxideTaskSection.Queued -> queued
            OxideTaskSection.Running -> running
            OxideTaskSection.History -> history
        }
}

/**
 * 结局 → 面板上属于哪一节（一般任务用）
 *
 * 收尾的三种一律进历史，其中抛异常的那种在历史里仍然标成 Failed：
 * "做完过"与"做成了"是两件事，合成一列就把后者抹掉了。
 * 下载类任务不走这里，走下面的 [taskSectionOf]（按任务判）。
 */
internal fun taskSectionOf(outcome: TaskOutcome): OxideTaskSection =
    when (outcome) {
        TaskOutcome.Queued -> OxideTaskSection.Queued
        TaskOutcome.Running -> OxideTaskSection.Running
        TaskOutcome.Succeeded, TaskOutcome.Failed, TaskOutcome.Cancelled -> OxideTaskSection.History
    }

/**
 * 一条在跑的任务 → 它属于哪一节
 *
 * 下载类不分排队与运行中：两节合并成下载中。已经收尾却还挂在在跑列表里的
 * （收尾与摘除之间那一帧）直接算已完成，于是"完成"永远只发生在一边，
 * 不会在下载中闪一下再搬过去。
 */
internal fun taskSectionOf(task: Task): OxideTaskSection {
    if (task.kind != TaskKind.Download) return taskSectionOf(task.outcome.value)
    return if (task.outcome.value.finished) OxideTaskSection.Complete else OxideTaskSection.Downloading
}

/** 一条面板条目 → 它属于哪一节；收尾过的快照按类别进已完成或历史 */
internal fun taskSectionOf(entry: OxideTaskEntry): OxideTaskSection =
    when (entry) {
        is OxideTaskEntry.Settled ->
            if (entry.record.kind == TaskKind.Download) OxideTaskSection.Complete
            else OxideTaskSection.History
        is OxideTaskEntry.Live -> taskSectionOf(entry.task)
    }

/**
 * 把一份条目分进五节
 *
 * **保持传入顺序**：在跑的那几节沿用任务系统的提交顺序，已完成与历史沿用它自己的
 * "最新在前"顺序。这里一次都不排——再排一次就等于给"最新的在前"加了第二个定义。
 */
internal fun oxideTaskSectionsOf(entries: List<OxideTaskEntry>): OxideTaskSections {
    val downloading = ArrayList<OxideTaskEntry>()
    val complete = ArrayList<OxideTaskEntry>()
    val running = ArrayList<OxideTaskEntry>()
    val queued = ArrayList<OxideTaskEntry>()
    val history = ArrayList<OxideTaskEntry>()
    entries.forEach { entry ->
        when (taskSectionOf(entry)) {
            OxideTaskSection.Downloading -> downloading.add(entry)
            OxideTaskSection.Complete -> complete.add(entry)
            OxideTaskSection.Running -> running.add(entry)
            OxideTaskSection.Queued -> queued.add(entry)
            OxideTaskSection.History -> history.add(entry)
        }
    }
    return OxideTaskSections(
        downloading = downloading,
        complete = complete,
        running = running,
        queued = queued,
        history = history,
    )
}

/** 在跑的一条任务 → 面板条目；不订阅任何流，订阅发生在绘制那一步 */
internal fun Task.toTaskEntry(): OxideTaskEntry = OxideTaskEntry.Live(this)

/** 一条收尾快照 → 面板条目 */
internal fun TaskHistory.toTaskEntry(): OxideTaskEntry = OxideTaskEntry.Settled(this)

/** 结局对应的字符串资源；历史那一行的标记读它 */
internal fun oxideTaskOutcomeLabel(outcome: TaskOutcome): Int = when (outcome) {
    TaskOutcome.Succeeded -> R.string.oxide_task_stage_completed
    TaskOutcome.Failed -> R.string.generic_error
    TaskOutcome.Cancelled -> R.string.generic_cancel
    // 在跑与排队两节不显示结局标记，这里的值只作为"标题与说明都没有"时的兜底
    TaskOutcome.Queued -> R.string.oxide_task_stage_preparing
    TaskOutcome.Running -> R.string.oxide_task_stage_running
}

/** 五节各自的标题 */
internal fun oxideTaskSectionLabel(section: OxideTaskSection): Int = when (section) {
    // 下载中暂用通用"下载"兜底：strings 里没有不带参数的 Downloading，
    // 需要新增 `oxide_tasks_section_downloading`（Downloading）后换过去
    OxideTaskSection.Downloading -> R.string.generic_download
    OxideTaskSection.Complete -> R.string.oxide_dis_task_stage_completed
    OxideTaskSection.Queued -> R.string.oxide_task_stage_preparing
    OxideTaskSection.Running -> R.string.oxide_task_stage_running
    OxideTaskSection.History -> R.string.oxide_tasks_section_history
}

/**
 * 一条历史该展示多少进度
 *
 * 成功与失败的那两次留着当时的比例：那正是用户事后想看的（"下到 42% 就断了"）。
 * 被取消与从未开始的那两种没有可展示的进度，返回 null 而不是画一条空的进度槽——
 * 空槽读起来就是"下过 0%"，那是假的。
 */
internal fun oxideTaskHistoryProgress(record: TaskHistory): Float? =
    when (record.outcome) {
        TaskOutcome.Succeeded, TaskOutcome.Failed -> record.progress
        TaskOutcome.Queued, TaskOutcome.Running, TaskOutcome.Cancelled -> null
    }

/** 历史那一行的说明；标题已经说过同一句话时不再重复一遍 */
internal fun oxideTaskHistoryDetail(record: TaskHistory): AndroidStringText? =
    record.message?.takeIf { it != record.title }
