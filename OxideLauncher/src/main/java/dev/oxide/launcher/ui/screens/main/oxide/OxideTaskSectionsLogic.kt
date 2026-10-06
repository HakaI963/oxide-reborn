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
import dev.oxide.launcher.coroutine.TaskOutcome
import dev.oxide.launcher.ui.AndroidStringText

/**
 * 任务面板的三节分法
 *
 * 面板只有**一份**数据——`TaskSystem` 的在跑列表加它自己留的收尾快照
 * （`TaskSystem.tasksFlow` 与 `TaskSystem.historyFlow`）——三节是这份数据在三个时刻上的投影：
 *
 *  - **排队**：交出去了、还没开始跑（[TaskOutcome.Queued]）。它仍然在在跑列表里，只是结局还
 *    是 Queued。版本安装也落在这里：它走的是 `GameInstaller` 自带的 `TaskFlowExecutor`，
 *    从来不会经过 `TaskSystem.submitTask`，于是由 `TaskSystem.trackExternalTask` 把同一种
 *    [Task] 挂进同一份列表——否则这一节只会显示下载，看不见正在装的 Minecraft 版本。
 *  - **运行**：正在跑（[TaskOutcome.Running]）。
 *  - **历史**：已经收尾的三种，成功 / 抛异常 / 被取消。顺序由 `taskHistoryPrepend` 钉死为
 *    **最新的在最前**，这里原样透传，不再排一次。
 *
 * 分节是纯函数，于是"一个从没开始过的任务算排队、一个跑到一半的算运行、一个抛了异常的在历史里
 * 且标成失败"这三条各自都能被一条单测钉死——不需要 Compose，也不需要真的跑一条协程。
 */

/** 面板的三节 */
internal enum class OxideTaskSection {
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

/** 面板三节的全部内容 */
internal data class OxideTaskSections(
    val running: List<OxideTaskEntry> = emptyList(),
    val queued: List<OxideTaskEntry> = emptyList(),
    val history: List<OxideTaskEntry> = emptyList(),
) {
    /** 三节全空：界面据此显示"没有在跑的东西"，而不是画三个空标题 */
    val empty: Boolean
        get() = running.isEmpty() && queued.isEmpty() && history.isEmpty()

    /** 某一节的条目；绘制那一步按分节标题依次取，空的那一节整节跳过 */
    fun of(section: OxideTaskSection): List<OxideTaskEntry> =
        when (section) {
            OxideTaskSection.Queued -> queued
            OxideTaskSection.Running -> running
            OxideTaskSection.History -> history
        }
}

/**
 * 结局 → 面板上属于哪一节
 *
 * 收尾的三种一律进历史，其中抛异常的那种在历史里仍然标成 Failed：
 * "做完过"与"做成了"是两件事，合成一列就把后者抹掉了。
 */
internal fun taskSectionOf(outcome: TaskOutcome): OxideTaskSection =
    when (outcome) {
        TaskOutcome.Queued -> OxideTaskSection.Queued
        TaskOutcome.Running -> OxideTaskSection.Running
        TaskOutcome.Succeeded, TaskOutcome.Failed, TaskOutcome.Cancelled -> OxideTaskSection.History
    }

/** 一条面板条目 → 它属于哪一节；收尾过的快照无论结局如何都在历史里 */
internal fun taskSectionOf(entry: OxideTaskEntry): OxideTaskSection =
    when (entry) {
        is OxideTaskEntry.Settled -> OxideTaskSection.History
        is OxideTaskEntry.Live -> taskSectionOf(entry.task.outcome.value)
    }

/**
 * 把一份条目分进三节
 *
 * **保持传入顺序**：在跑的那两节沿用任务系统的提交顺序，历史那一节沿用它自己的"最新在前"
 * 顺序。这里一次都不排——再排一次就等于给"最新的在前"加了第二个定义。
 */
internal fun oxideTaskSectionsOf(entries: List<OxideTaskEntry>): OxideTaskSections {
    val running = ArrayList<OxideTaskEntry>()
    val queued = ArrayList<OxideTaskEntry>()
    val history = ArrayList<OxideTaskEntry>()
    entries.forEach { entry ->
        when (taskSectionOf(entry)) {
            OxideTaskSection.Running -> running.add(entry)
            OxideTaskSection.Queued -> queued.add(entry)
            OxideTaskSection.History -> history.add(entry)
        }
    }
    return OxideTaskSections(running = running, queued = queued, history = history)
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

/** 三节各自的标题 */
internal fun oxideTaskSectionLabel(section: OxideTaskSection): Int = when (section) {
    OxideTaskSection.Queued -> R.string.oxide_task_stage_preparing
    OxideTaskSection.Running -> R.string.oxide_task_stage_running
    OxideTaskSection.History -> R.string.oxide_dis_task_stage_completed
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
