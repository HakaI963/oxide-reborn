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

package dev.oxide.layercontroller.observable

import dev.oxide.layercontroller.data.JoystickDirection
import dev.oxide.layercontroller.event.ClickEvent

/**
 * 可观察控件的点击事件编辑提供器
 */
abstract class ObservableClickEventsProvider {
    abstract val clickEvents: List<ClickEvent>
    abstract fun onRemoveAllEvents(events: List<ClickEvent>)
    abstract fun onRemoveAllEvents(type: ClickEvent.Type)
    abstract fun onAddEvent(event: ClickEvent)
    abstract fun onRemoveEvent(event: ClickEvent)

    /**
     * 就地替换一个已存在的绑定（用于改延迟这类保持位置与类型的改动）
     *
     * 默认实现是"先删后加"，它会把这一条挪到列表末尾，而列表顺序就是组合键的
     * 派发顺序——改一次延迟就会把用户排好的组合打乱。持有方按类型与按键值
     * 覆写它来保住位置。
     */
    open fun onReplaceEvent(event: ClickEvent) {
        onRemoveEvent(event)
        onAddEvent(event)
    }
}

/**
 * 为普通按钮控件创建点击事件编辑提供器
 */
fun clickEventsProvider(data: ObservableNormalData): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = data.clickEvents
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            data.removeAllEvent(events)
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeAllEvent(type)
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addEvent(event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeEvent(event)
        }
        override fun onReplaceEvent(event: ClickEvent) {
            data.replaceEvent(event)
        }
    }
}

/**
 * 为摇杆控件创建锁定状态触发事件编辑提供其
 */
fun joystickLockEventsProvider(data: ObservableJoystickData): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = data.lockEvents
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            events.forEach { event ->
                data.removeLockEvent { it == event }
            }
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeLockEvent { it.type == type }
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addLockEvent(event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeLockEvent { it == event }
        }
        override fun onReplaceEvent(event: ClickEvent) {
            data.replaceLockEvent(event)
        }
    }
}

/**
 * 为摇杆控件创建方向触发事件编辑提供其
 */
fun joystickDirectionEventsProvider(
    data: ObservableJoystickData,
    direction: JoystickDirection?,
): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = direction?.let { data.directionEvents[it] } ?: emptyList()
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            events.forEach { event ->
                data.removeDirectionEvent(direction) { it == event }
            }
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeDirectionEvent(direction) { it.type == type }
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addDirectionEvent(direction, event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeDirectionEvent(direction) { it == event }
        }
        override fun onReplaceEvent(event: ClickEvent) {
            data.replaceDirectionEvent(direction, event)
        }
    }
}