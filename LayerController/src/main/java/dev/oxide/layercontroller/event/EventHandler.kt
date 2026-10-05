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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.layercontroller.event

import dev.oxide.layercontroller.data.clampClickEventDelayMs
import dev.oxide.layercontroller.data.clampMacroIntervalMs
import dev.oxide.layercontroller.observable.ObservableControlLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

/**
 * 一次已安排好的延迟调用，取消它必须保证那一次调用不会再发生
 */
fun interface MacroTicket {
    fun cancel()
}

/**
 * 宏重复与按键延迟共用的时钟
 *
 * 生产环境用协程（[CoroutineMacroClock]），单测换成手动推进的假时钟，于是
 * [MacroRepeater] 与 [PressPipeline] 这两个状态机不依赖协程也能被完整地走一遍。
 */
interface MacroClock {
    /**
     * 安排 [delayMs] 毫秒后调用一次 [action]
     */
    fun schedule(delayMs: Long, action: () -> Unit): MacroTicket
}

/**
 * 按住期间自动重复的状态机
 *
 * 它只做一件事：把"再发一次"这件事排到下一个间隔上。间隔为 0（宏关闭）时
 * [press] 直接什么都不做，因此关闭宏的控件走的仍然是 1.7.0 的那一条路。
 *
 * 生命周期完全由真实的按下/抬起驱动：[press] 开始排队，[release] 立刻取消
 * 尚未触发的那一次；此后即便有一张已经作废的票被触发，[isRunning] 为 false，
 * 也不会再发出任何一次重复。
 */
class MacroRepeater(private val clock: MacroClock) {
    private var ticket: MacroTicket? = null
    private var intervalMs: Long = 0L
    private var running: Boolean = false
    private var onRepeat: (() -> Unit)? = null

    /**
     * 当前是否处于重复状态
     */
    val isRunning: Boolean get() = running

    /**
     * 手指按下：开始按 [intervalMs] 的间隔重复
     *
     * 夹不合法的间隔一律折成 0，也就是不重复——宏的开关与间隔是同一个字段，
     * 把"关"夹成一个可用的间隔会让控件在按住时莫名连发。
     */
    fun press(intervalMs: Long, onRepeat: () -> Unit) {
        if (running) return
        val interval = clampMacroIntervalMs(intervalMs)
        if (interval <= 0L) return
        this.intervalMs = interval
        this.onRepeat = onRepeat
        running = true
        arm()
    }

    private fun arm() {
        ticket = clock.schedule(intervalMs) {
            ticket = null
            if (!running) return@schedule
            onRepeat?.invoke()
            if (running) arm()
        }
    }

    /**
     * 手指抬起：立刻停止，尚未触发的那一次不再发出
     */
    fun release() {
        running = false
        ticket?.cancel()
        ticket = null
        onRepeat = null
    }
}

/**
 * 一个控件的延迟派发与宏重复
 *
 * 延迟派发是把一组点击事件按各自的 [ClickEvent.delayMs] 依次发出去：某一次要等，
 * 就先占住一个 [MacroTicket]，因此抬起时能一次性把还在等的那一次收掉。
 */
class PressPipeline(private val clock: MacroClock) {
    private var pending: MacroTicket? = null
    private var repeater: MacroRepeater? = null

    /**
     * 是否仍有等待中的派发或正在运行的重复
     */
    val isBusy: Boolean get() = pending != null || repeater?.isRunning == true

    /**
     * 收掉这一路控件上全部等待中的派发与重复
     */
    fun cancelAll() {
        pending?.cancel()
        pending = null
        repeater?.release()
        repeater = null
    }

    /**
     * 按下：按各自的延迟依次派发
     *
     * 全部延迟都是 0 时就在当前线程上同步发完，与本特性之前逐条相同——
     * 这样"没有设置延迟"的控件不会多绕一次调度。
     */
    fun dispatchPress(events: List<ClickEvent>, dispatch: (ClickEvent) -> Unit) {
        pending?.cancel()
        pending = null
        if (events.none { it.delayMs > 0 }) {
            events.forEach(dispatch)
            return
        }
        step(events, 0, dispatch)
    }

    private fun step(events: List<ClickEvent>, index: Int, dispatch: (ClickEvent) -> Unit) {
        val event = events[index]
        val waitMs = clampClickEventDelayMs(event.delayMs)
        val finish = {
            dispatch(event)
            if (index + 1 < events.size) {
                step(events, index + 1, dispatch)
            } else {
                pending = null
            }
        }
        if (waitMs <= 0) {
            finish()
        } else {
            pending = clock.schedule(waitMs.toLong(), finish)
        }
    }

    /**
     * 按下：开启按住自动重复
     *
     * [onRepeat] 是重复时要执行的动作，由调用方决定重复什么——这里只管节奏。
     */
    fun startMacroRepeat(intervalMs: Long, onRepeat: () -> Unit) {
        if (repeater?.isRunning == true) return
        val repeater = MacroRepeater(clock)
        this.repeater = repeater
        repeater.press(intervalMs, onRepeat)
    }
}

/**
 * 专用于处理控件触发事件的处理器
 * @param handle 处理事件
 * @param clock 延迟派发与宏重复使用的时钟
 */
class EventHandler(
    private val handle: (event: ClickEvent, pressed: Boolean) -> Unit = { _, _ -> },
    private val clock: MacroClock = defaultMacroClock
) {
    /**
     * 每个控件一路管道
     *
     * 键用引用相等而不是内容相等：两个控件完全可能绑定同一组按键，
     * 按内容索引会让它们互相取消对方的延迟与重复。
     */
    private val pipelines = IdentityHashMap<Any, PressPipeline>()

    /**
     * 普通的按钮按下事件
     * @param handle 决定是否处理该事件
     * @param owner 这一组事件属于谁，抬起时用它找回并取消那一路管道；为空时以事件列表本身为键
     * @param macroIntervalMs 按住时自动重复的间隔，0 表示不重复
     */
    internal fun onKeyPressed(
        clickEvents: List<ClickEvent>,
        isPressed: Boolean,
        handle: (ClickEvent) -> Boolean = { true },
        owner: Any? = null,
        macroIntervalMs: Long = 0L
    ) {
        val key = owner ?: clickEvents
        val pipeline = pipelineOf(key)

        if (!isPressed) {
            // 抬起立即收掉还在等的延迟与重复，再同步发出抬起事件：
            // 抬起一旦排队，按键就会在游戏里卡住放不掉
            pipeline.cancelAll()
            // 收干净之后这一路就没有活要干了，把它从表里摘掉。
            // 否则每次按下都往表里加一条，而被删掉的控件再也不会抬起，这张表只增不减
            if (!pipeline.isBusy) releasePipeline(key, pipeline)
            for (event in clickEvents) {
                if (handle(event)) handle(event, false)
            }
            return
        }

        pipeline.dispatchPress(clickEvents) { event ->
            if (handle(event)) handle(event, true)
        }

        if (macroIntervalMs > 0L) {
            pipeline.startMacroRepeat(macroIntervalMs) {
                // 重复的只是按键绑定本身：切换控件层、发送文本这类一次性的动作重复起来
                // 只会让层来回闪、聊天刷屏，所以不在重复范围里
                pipeline.dispatchPress(clickEvents.filter { it.type == ClickEvent.Type.Key }) { event ->
                    if (handle(event)) handle(event, true)
                }
            }
        }
    }

    /**
     * 收掉所有控件上等待中的延迟派发与正在运行的宏重复
     *
     * 抬起与控件销毁各自已经收过一遍自己那一路；这一条是给"整份布局被换掉"
     * 兜底的，调用方持有 [EventHandler] 的生命周期，因此由它来调。
     */
    fun cancelAllMacros() {
        synchronized(pipelines) {
            pipelines.values.forEach { it.cancelAll() }
            pipelines.clear()
        }
    }

    private fun pipelineOf(owner: Any): PressPipeline =
        synchronized(pipelines) {
            pipelines.getOrPut(owner) { PressPipeline(clock) }
        }

    /**
     * 把一路管道从表里摘掉
     *
     * 表以控件为键，而控件随时会被删除或整份布局被换掉；不摘掉的话这张表只增不减，
     * 一次会话里删掉的控件会连同它们的等待一起留在里面。
     */
    private fun releasePipeline(owner: Any, pipeline: PressPipeline) {
        synchronized(pipelines) {
            if (pipelines[owner] === pipeline) pipelines.remove(owner)
        }
    }

    /**
     * 处理切换布局隐藏显示
     */
    internal fun onSwitchLayer(
        clickEvent: ClickEvent,
        allLayers: List<ObservableControlLayer>,
        switch: (ObservableControlLayer) -> Unit,
        show: (ObservableControlLayer) -> Unit,
        hide: (ObservableControlLayer) -> Unit
    ) {
        fun findLayer() = allLayers.find { it.uuid == clickEvent.key }

        when (clickEvent.type) {
            ClickEvent.Type.SwitchLayer -> findLayer()?.let { switch(it) }
            ClickEvent.Type.ShowLayer -> findLayer()?.let { show(it) }
            ClickEvent.Type.HideLayer -> findLayer()?.let { hide(it) }
            else -> {}
        }
    }

    companion object {
        /**
         * 进程级的延迟作用域
         *
         * 它本身不跑任何循环：只有按住一个带延迟或宏的控件时才会挂上去一个等待，
         * 而那一个等待由抬起或 [cancelAllMacros] 收掉。
         */
        private val sharedScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * 协程实现的时钟
         */
        private val defaultMacroClock: MacroClock = CoroutineMacroClock(sharedScope)

        /**
         * 收掉共享作用域上仍在等待的全部调用（应用退出时调用）
         */
        fun shutdown() {
            sharedScope.cancel()
        }
    }
}

/**
 * 生产环境的延迟时钟：每次安排一个由作用域托管的 Job
 *
 * 取消一张票就是取消那个 Job，`delay` 会在那一刻醒不过来，因此"抬起后不再发一次"
 * 是结构上的保证，而不是靠时间差去赌。
 */
class CoroutineMacroClock(private val scope: CoroutineScope) : MacroClock {
    override fun schedule(delayMs: Long, action: () -> Unit): MacroTicket {
        val job: Job = scope.launch {
            delay(delayMs)
            action()
        }
        return MacroTicket { job.cancel() }
    }
}