/*
 * Zalith Launcher 2
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

package dev.oxide.launcher.coroutine

import dev.oxide.launcher.keepalive.TaskKeepAlive
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.utils.network.isInterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务收尾时记进历史的那一份快照
 *
 * **为什么必须是一份快照而不是 `Task` 本身**：任务结束之后 `Task` 已经不在任何列表里了，
 * 它仍然可读（那几个 `StateFlow` 只是不再被消费），但把一个"已经被移走、仍可被 `updateProgress`
 * 改写"的对象挂在历史上，历史那一行就会在别的代码还在推进它的时候跟着变。
 * 收尾那一刻把要显示的字段取成普通值，历史因此是不可再写的。
 *
 * @param title 提交方填的任务标题；没填时面板退回阶段/消息
 * @param message 收尾那一刻任务自己写的说明（真实的文件名与字节数）
 * @param outcome 收尾的原因：成功、抛异常、还是被用户取消
 */
data class TaskHistory(
    val id: String,
    val title: AndroidStringText?,
    val message: AndroidStringText?,
    /** `Task.progress` 的原值；-1f 表示进度不可知 */
    val progress: Float,
    val rateBytesPerSec: Long?,
    val outcome: TaskOutcome,
)

/**
 * 历史最多留多少条
 *
 * 只在内存里，因此必须封顶：一条失败的任务永远留在那里的话，这个列表没有理由有上限之外的行为，
 * 而没有上限的列表正是把一个抽屉拖成几百行的那种东西。
 */
const val TASK_HISTORY_LIMIT: Int = 40

/**
 * 把一条新历史放到最前面，并按上限截断
 *
 * 纯函数，所以"最新的在最前"与"超出上限丢掉最旧的"这两条都能在没有协程、没有 Compose、
 * 也没有时钟的情况下断言。**不排时间**：同一毫秒里收尾的两条任务无法靠时间戳分先后，
 * 而"提交/收尾的先后"本来就是确定的，直接按到达顺序记。
 */
fun taskHistoryPrepend(history: List<TaskHistory>, entry: TaskHistory, limit: Int = TASK_HISTORY_LIMIT): List<TaskHistory> =
    (listOf(entry) + history).take(limit.coerceAtLeast(0))

/** 把一条在跑的任务收成一份历史快照 */
fun Task.toHistory(): TaskHistory = TaskHistory(
    id = id,
    title = title.value,
    message = message.value,
    progress = progress.value,
    rateBytesPerSec = rateBytesPerSec.value,
    // 走到历史就意味着"这件事已经不在跑了"。收尾时仍然是 Queued/Running 的只有一种情况：
    // 协程被系统中断或网络被掐断（`submitTask` 对这两种直接 return，不写结局）。
    // 那时并没有任何人给出结论，所以记成 Cancelled —— 这是三选一里唯一不撒谎的一个。
    outcome = if (outcome.value.finished) outcome.value else TaskOutcome.Cancelled,
)

object TaskSystem {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _tasksFlow: MutableStateFlow<List<Task>> = MutableStateFlow(emptyList())
    val tasksFlow = _tasksFlow.asStateFlow()

    /**
     * 已收尾的任务，最新的在最前
     *
     * **这是任务面板三节里的"历史"那一份数据**，与 [tasksFlow] 同源：同一次
     * `onTaskEnded` 既把任务从 [tasksFlow] 移走，也在这里补一条快照。
     * 因此"三节"不是三套账，而是同一份任务模型在三个时刻上的投影。
     *
     * 只在内存里（见 [TASK_HISTORY_LIMIT] 的说明），进程结束即消失——没有新增存储机制，
     * 也没有迁移。
     */
    private val _historyFlow: MutableStateFlow<List<TaskHistory>> = MutableStateFlow(emptyList())
    val historyFlow = _historyFlow.asStateFlow()

    private val allJobs = ConcurrentHashMap<String, Job>()
    private val allListeners = ConcurrentHashMap<String, () -> Unit>()

    /**
     * 已经记过历史的任务 id
     *
     * 取消一条任务时 [onTaskEnded] 会被走**两遍**：[cancelTask] 自己走一遍，
     * 随后那个 Job 的 `invokeOnCompletion` 再走一遍（`removeTask` 天然幂等，所以以前没人
     * 看见这个问题，但"同一个 id 在历史里出现两次"是用户看得见的）。这一个集合让历史只记一次。
     */
    private val recordedHistory = ConcurrentHashMap.newKeySet<String>()

    /**
     * 提交并立即运行任务
     */
    fun submitTask(task: Task) {
        if (containsTask(task)) return
        addTask(task)
        //持有保活，避免启动器切至后台后任务被系统中断
        TaskKeepAlive.acquire()

        allJobs[task.id] = scope.launch(task.dispatcher) {
            try {
                task.updateStage(TaskStage.RUNNING)
                task.task(this@launch, task)
                task.updateStage(TaskStage.COMPLETED)
            } catch (th: Throwable) {
                if (th is CancellationException || th.isInterruptedIOException()) return@launch
                // 阶段里没有"失败"这一个值（见 TaskOutcome），失败只经结局写进来
                task.markOutcome(TaskOutcome.Failed)
                task.onError(th)
            } finally {
                task.onFinally()
            }
        }.also { job ->
            job.invokeOnCompletion {
                try {
                    onTaskEnded(task)
                } finally {
                    //确保保活一定被释放，避免任务异常导致前台服务无法停止
                    TaskKeepAlive.release()
                }
            }
        }
    }

    /**
     * 提交并立即运行任务
     * 若任务已存在，则忽略，但任务监听器会被覆盖
     * @param onEnded 任务结束时的监听器
     */
    fun submitTask(task: Task, onEnded: () -> Unit) {
        putTaskEndedListener(taskId = task.id, onEnded = onEnded)
        submitTask(task)
    }

    /**
     * 登记一个**不由本系统执行**的任务
     *
     * 版本安装走的是 `TaskFlowExecutor`（`GameInstaller` 自带一个），它自己管自己的阶段列表，
     * 因此这个任务从来不会出现在 `tasksFlow` 里——而用户要的正是在任务面板的"排队"一节里
     * 看见"正在装 1.20.1"。这里把同一种 [Task] 挂进同一份列表，于是三节仍然是**一个**模型：
     * 跑完仍然由 [finishTrackedTask] 收进同一份历史。
     *
     * 它一开始处于 [TaskOutcome.Queued]，调用方在安装真正开始时调一次
     * [startTrackedTask] 把它推到 Running。
     *
     * @return 任务 id；同一个 id 已经登记过时返回 null，调用方据此跳过重复登记
     */
    fun trackExternalTask(task: Task): String? {
        if (containsTask(task)) return null
        // 这一条不由本系统执行，所以既不持有保活也不进 allJobs：
        // 它没有 Job 可取消，取消要走它自己那条链路（调用方拿着 id 去 cancel）。
        addTask(task)
        return task.id
    }

    /** 外部任务真正开始了：从排队推进运行中 */
    fun startTrackedTask(id: String) {
        _tasksFlow.value.firstOrNull { it.id == id }?.updateStage(TaskStage.RUNNING)
    }

    /**
     * 外部任务收尾：移出在跑列表，记进历史
     *
     * [outcome] 必须是三种收尾之一；传别的（Queued / Running）就等于声明它还没结束，
     * 此时这里什么都不做——收尾一次之后又回到"在跑"里去的任务不是这套模型描述得了的。
     */
    fun finishTrackedTask(id: String, outcome: TaskOutcome) {
        if (!outcome.finished) return
        val task = _tasksFlow.value.firstOrNull { it.id == id } ?: return
        task.markOutcome(outcome)
        onTaskEnded(task)
    }

    /**
     * 添加任务结束的监听器，监听器会在任务的一切流程结束时被调用
     * 任务监听器执行时发生的异常将会被忽略
     * @param taskId 指定监听器应用到的哪个任务上
     */
    fun putTaskEndedListener(taskId: String, onEnded: () -> Unit) {
        allListeners[taskId] = onEnded
    }

    /**
     * 移除任务流程结束的监听器
     */
    fun removeTaskEndedListener(taskId: String) =
        allListeners.remove(taskId)

    /**
     * 一个任务收尾：移出在跑列表，并在历史里补一条快照
     *
     * 这两件事必须同一个函数里做，否则"跑的那一节少了一条"与"历史里多了一条"可以只发生一件，
     * 面板就会出现同一件事既在跑又在历史里，或者两处都没有。
     */
    private fun onTaskEnded(task: Task) {
        recordHistory(task)
        removeTask(task)
        runCatching {
            allListeners[task.id]?.invoke()
        }
        allJobs.remove(task.id)
        removeTaskEndedListener(task.id)
    }

    private fun onTaskCanceled(task: Task) {
        scope.launch {
            task.onCancel()
        }
        onTaskEnded(task)
    }

    /**
     * 取消任务
     */
    fun cancelTask(task: Task) {
        allJobs[task.id]?.cancel()
        task.markOutcome(TaskOutcome.Cancelled)
        onTaskCanceled(task)
    }

    /**
     * 取消任务
     */
    fun cancelTask(id: String) {
        allJobs[id]?.cancel()
        _tasksFlow.value.find { it.id == id }?.let {
            it.markOutcome(TaskOutcome.Cancelled)
            onTaskCanceled(it)
        }
    }

    /**
     * @return 是否包括任务
     */
    fun containsTask(task: Task) = _tasksFlow.value.contains(task)

    /**
     * @return 是否包括任务
     */
    fun containsTask(id: String) = _tasksFlow.value.any { it.id == id }

    /**
     * 停止所有任务
     *
     * 历史**不**清：它是"做过什么"的记录，不是"还在跑什么"。进程结束时这份内存本来就会
     * 一起消失，所以留着它没有任何代价，而清掉它会让"我刚才那次到底成没成"永远答不上来。
     *
     * 但"不清历史"不等于"在跑的那几条可以凭空消失"：它们此刻被移出在跑列表，而被移出
     * 本身就是一次收尾。因此这里先把每一条记成收尾（仍在跑或仍排队的都记成 Cancelled，
     * 见 [Task.toHistory]），否则一次停机就会让正在进行的那一条不留任何痕迹，
     * 恰好是这段注释承诺不要发生的那件事。
     */
    fun stopAll() {
        scope.cancel()
        // _tasksFlow.value 是一份不可变快照，而 recordHistory 只写 _historyFlow，
        // 所以这里一边遍历一边记账是安全的
        _tasksFlow.value.forEach { recordHistory(it) }
        _tasksFlow.update { emptyList() }
        allJobs.clear()
        allListeners.clear()
        TaskKeepAlive.reset()
    }

    /** 一条任务只记一次历史；同一个 id 重新登记时那份记录重新算数 */
    private fun recordHistory(task: Task) {
        if (!recordedHistory.add(task.id)) return
        val snapshot = task.toHistory()
        _historyFlow.update { taskHistoryPrepend(it, snapshot) }
    }

    private fun addTask(task: Task) {
        recordedHistory.remove(task.id)
        _tasksFlow.update { it + task }
    }

    private fun removeTask(task: Task) {
        _tasksFlow.update { it - task }
    }
}
