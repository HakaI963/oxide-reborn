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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.oxide.layercontroller.data.ButtonPosition
import dev.oxide.layercontroller.data.ButtonSize
import dev.oxide.layercontroller.data.MacroRepeatHolder
import dev.oxide.layercontroller.data.NormalData
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.data.cloneNew
import dev.oxide.layercontroller.data.filterValidEvent
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.event.EventHandler

/**
 * 可观察的NormalData包装类
 */
class ObservableNormalData(data: NormalData) : ObservableWidget(), MacroRepeatHolder {
    val text = ObservableTranslatableString(data.text)
    val uuid: String = data.uuid
    var position by mutableStateOf(data.position)
    var buttonSize by mutableStateOf(data.buttonSize)
    var buttonStyle by mutableStateOf(data.buttonStyle)
    var textAlignment by mutableStateOf(data.textAlignment)
    var textBold by mutableStateOf(data.textBold)
    var textItalic by mutableStateOf(data.textItalic)
    var textUnderline by mutableStateOf(data.textUnderline)
    var visibilityType by mutableStateOf(data.visibilityType)
    var clickEvents by mutableStateOf(data.clickEvents)
    var isSwipple by mutableStateOf(data.isSwipple)
    var isPenetrable by mutableStateOf(data.isPenetrable)
    var isToggleable by mutableStateOf(data.isToggleable)

    /**
     * 按住时自动重复发送按键的间隔，0 表示关闭
     */
    override var macroIntervalMs by mutableStateOf(data.macroIntervalMs)

    override val behavior: InteractionBehavior
        get() = InteractionBehavior.from(
            isSwipple = isSwipple,
            isToggleable = isToggleable
        )

    /**
     * 当前是否处于按下状态
     */
    var isPressed by mutableStateOf(false)
        private set

    /**
     * 开始触摸事件处理
     */
    private fun pressStart(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        when (behavior) {
            is InteractionBehavior.Toggle -> isPressed = !isPressed
            else -> {
                if (isPressed) return
                isPressed = true
            }
        }
        eventHandler.onKeyPressed(
            clickEvents = clickEvents,
            isPressed = isPressed,
            owner = this,
            macroIntervalMs = macroIntervalMs
        ) { event ->
            eventHandler.onSwitchLayer(
                clickEvent = event,
                allLayers = allLayers,
                switch = { layer ->
                    layer.hide = !layer.hide
                },
                show = { layer ->
                    layer.hide = false
                },
                hide = { layer ->
                    layer.hide = true
                }
            )
            true
        }
    }

    override val internalRenderPosition: ButtonPosition
        get() = position

    override fun putRenderPosition(position: ButtonPosition) {
        this.position = position
    }

    override fun putWidgetSize(size: ButtonSize) {
        this.buttonSize = size
    }

    override val styleId: String?
        get() = buttonStyle

    override val widgetSize: ButtonSize
        get() = buttonSize

    override fun onCompositionStart(eventHandler: EventHandler?) {

    }

    override fun onCompositionDispose(eventHandler: EventHandler?) {
        if (isPressed) {
            //fix: 若本身未按下，不应该输出抬起事件
            isPressed = false
            // 抬起顺带把这一路控件仍在等的延迟派发与宏重复收掉
            eventHandler?.onKeyPressed(clickEvents, isPressed, owner = this)
        }
    }

    override fun onCheckVisibilityType(): VisibilityType {
        return visibilityType
    }

    override fun supportsDeepTouchDetection(): Boolean {
        //如果有不可穿透按钮，只保留最顶层的一个不可穿透按钮及其上层的所有可穿透按钮
        return !isSwipple || !(isSwipple && isPenetrable)
    }

    override fun canProcess(): Boolean {
        //作为特性存在，筛除即可穿透又可滑动的按钮
        //因为我发现我怎么都修不好:(
        return isPenetrable && isSwipple
    }

    override fun onTouchEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        activeWidgets: List<ObservableWidget>,
        addThis: () -> Unit,
        consumeEvent: (Boolean) -> Unit
    ) {
        if (activeWidgets.isEmpty()) {
            //新的按下事件
            addThis()
            consumeEvent(!isPenetrable)
            pressStart(eventHandler, allLayers)
        } else if (this !in activeWidgets && behavior.canBeSwipedTo) {
            //滑动联动
            //该控件允许被滑入，且活跃控件中无阻止滑动链的类型
            if (activeWidgets.none { it.behavior.blocksSwipeChain }) {
                addThis()
                pressStart(eventHandler, allLayers)
            }
        }
    }

    override fun isReleaseOnOutOfBounds(): Boolean = behavior.releaseOnOutOfBounds

    override fun onPointerBackInBounds(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        if (behavior.releaseOnOutOfBounds) {
            pressStart(eventHandler, allLayers)
        }
    }

    override fun onReleaseEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        if (behavior is InteractionBehavior.Toggle || !isPressed) return
        isPressed = false
        // 抬起是宏重复与延迟派发的终点：事件处理器在这里收掉这一路控件仍在等的调用
        eventHandler.onKeyPressed(clickEvents, isPressed, owner = this)
    }

    fun addEvent(event: ClickEvent) {
        // 已有同一个绑定就不再加：加一个延迟不同的副本进去，编辑器改延迟时会改不动
        if (clickEvents.none { it.type == event.type && it.key == event.key }) {
            clickEvents = clickEvents + event
        }
    }

    fun removeEvent(event: ClickEvent) {
        removeEvent(event.type, event.key)
    }

    /**
     * 就地替换一个点击事件（用于改延迟这类保持位置与类型的改动）
     *
     * [event] 按类型与按键值定位，因此改完延迟之后它仍然算"同一个绑定"，
     * 后面 [addEvent] 的去重也不会把它当成一个新绑定丢掉。
     */
    fun replaceEvent(event: ClickEvent) {
        val index = clickEvents.indexOfFirst { it.type == event.type && it.key == event.key }
        if (index >= 0) {
            clickEvents = clickEvents.toMutableList().apply { set(index, event) }
        }
    }

    fun removeEvent(eventType: ClickEvent.Type, key: String) {
        clickEvents = clickEvents.filterNot { it.type == eventType && it.key == key }
    }

    /**
     * 移除所有匹配类型的点击事件
     */
    fun removeAllEvent(eventType: ClickEvent.Type) {
        clickEvents = clickEvents.filterNot { it.type == eventType }
    }

    fun removeAllEvent(events: Collection<ClickEvent>) {
        val keysToRemove = events.map { it.type to it.key }.toSet()
        clickEvents = clickEvents.filterNot { (it.type to it.key) in keysToRemove }
    }

    fun packNormal(): NormalData {
        return NormalData(
            text = text.pack(),
            uuid = uuid,
            position = position,
            buttonSize = buttonSize,
            buttonStyle = buttonStyle,
            textAlignment = textAlignment,
            textBold = textBold,
            textItalic = textItalic,
            textUnderline = textUnderline,
            visibilityType = visibilityType,
            _clickEvents = clickEvents.filterValidEvent(),
            isSwipple = isSwipple,
            isPenetrable = isPenetrable,
            isToggleable = isToggleable,
            macroIntervalMs = macroIntervalMs
        )
    }
}

fun ObservableNormalData.cloneNormal(): ObservableNormalData {
    return ObservableNormalData(packNormal().cloneNew())
}