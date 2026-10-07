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

import dev.oxide.launcher.ui.AndroidStringText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * 一条任务是哪一类活
 *
 * 任务面板要把"下载"与"别的活"画进不同的小节（下载中 / 已完成 vs 排队 / 运行 / 历史），
 * 而阶段与结局都答不了这个问题：一次版本安装与一次账号刷新在阶段上长得一模一样。
 * 因此另立这一个轴，而不是复用其中任何一个。
 *
 * 默认是 [General]：调用方不填时行为与以前完全一致，只有明确知道自己在下载的
 * 提交方（版本安装、游戏文件下载）才把它标成 [Download]。
 */
enum class TaskKind {
    /** 一般任务：账号、Java、导入导出等 */
    General,

    /** 下载类任务：版本安装、游戏文件、模组与资源下载 */
    Download,
}

class Task private constructor(
    val id: String,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val task: suspend CoroutineScope.(Task) -> Unit,
    val onError: suspend (Throwable) -> Unit = {},
    val onFinally: () -> Unit = {},
    val onCancel: () -> Unit = {},
    kind: TaskKind = TaskKind.General,
) {
    private val _stage = MutableStateFlow(TaskStage.PREPARING)
    /**
     * 任务阶段（TaskSystem可能用不到，主要服务于GameInstaller）
     */
    val stage = _stage.asStateFlow()

    private val _outcome = MutableStateFlow(TaskOutcome.Queued)
    /**
     * 任务结局；[TaskStage] 答不了"是成功了还是炸了"，这一条答得了（见 [TaskOutcome]）
     */
    val outcome = _outcome.asStateFlow()

    private val _title = MutableStateFlow<AndroidStringText?>(null)
    /**
     * 任务标题
     *
     * [task] 本身只是一段挂起函数，没有名字；任务面板在任务**结束之后**还要报这一条做过什么，
     * 而那时 `Task` 已经不在任何列表里了。因此标题是任务自己带的一份普通字段，由提交方
     * 填一次（例如"安装 1.20.1"），面板每一节都用它。
     */
    val title = _title.asStateFlow()

    private val _progress = MutableStateFlow(-1f)
    /** 任务进度状态 */
    val progress = _progress.asStateFlow()

    private val _message = MutableStateFlow<AndroidStringText?>(null)
    /** 任务消息状态 */
    val message = _message.asStateFlow()

    private val _rateBytesPerSec = MutableStateFlow<Long?>(null)
    /** 当前速率 Bytes */
    val rateBytesPerSec = _rateBytesPerSec.asStateFlow()

    /**
     * 这条任务是哪一类活
     *
     * 普通字段而不是流：它在提交之前就定下来（构造参数、[markAsDownload]），
     * 此后只升不降，因此分节这个纯函数可以直接读它，不需要订阅。
     */
    var kind: TaskKind = kind
        private set

    /**
     * 把这条任务标成下载类
     *
     * 只升不降，没有反向操作：一条任务不会"下着下着变成不是下载"。
     * 提交方在任务进任务系统之前调一次（见 `TaskSystem.submitDownloadTask` 与
     * `TaskSystem.trackExternalTask`），面板此后一直把它画在下载那一边。
     */
    fun markAsDownload() {
        kind = TaskKind.Download
    }

    /**
     * 更新任务阶段
     *
     * 阶段与 [outcome] 一起写：阶段只答"跑到哪一步了"，答不了"成没成"，
     * 所以这一次顺带把结局也推到与阶段对应的那个值上（见 [TaskStage.outcome]）。
     */
    fun updateStage(state: TaskStage) {
        this._stage.update { state }
        this._outcome.update { state.outcome }
    }

    /**
     * 直接写结局，不动阶段
     *
     * 用于阶段里根本没有对应值的那两种：抛出异常与用户取消。两者都要如实报出来，
     * 否则任务面板里"失败"这一节永远只能是空的。
     */
    fun markOutcome(outcome: TaskOutcome) {
        this._outcome.update { outcome }
    }

    /**
     * 填一次任务标题；面板在跑与历史两节都用它（见 [title]）
     */
    fun updateTitle(text: AndroidStringText?) {
        this._title.update { text }
    }

    /**
     * 更新进度，自动处理 NaN、isInfinite 的这种错误情况
     * @param percentage 进度百分比，-1f代表进度不确定
     */
    fun updateProgress(percentage: Float) {
        this._progress.update {
            (percentage.takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f)
        }
    }

    /**
     * 更新任务描述消息
     * @param text 任务描述消息
     */
    fun updateMessage(text: AndroidStringText?) {
        this._message.update { text }
    }

    /**
     * 更新任务比特速率
     */
    fun updateSpeed(bytes: Long) {
        this._rateBytesPerSec.update { bytes.takeIf { it >= 0L } }
    }

    /**
     * 清除任务比特速率
     */
    fun clearSpeed() {
        this._rateBytesPerSec.update { null }
    }

    override fun equals(other: Any?): Boolean = other is Task && other.id == this.id

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun runTask(
            id: String? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            task: suspend CoroutineScope.(Task) -> Unit,
            onError: suspend (Throwable) -> Unit = {},
            onFinally: () -> Unit = {},
            onCancel: () -> Unit = {},
            kind: TaskKind = TaskKind.General
        ): Task =
            Task(
                id = id ?: getRandomID(),
                dispatcher = dispatcher,
                task = task,
                onError = onError,
                onFinally = onFinally,
                onCancel = onCancel,
                kind = kind,
            )

        private fun getRandomID(): String = UUID.randomUUID().toString()
    }
}