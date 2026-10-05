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

package dev.oxide.layercontroller.event

import dev.oxide.layercontroller.observable.Modifiable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单个点击事件在派发前最多允许等待的时长（毫秒）
 */
const val MAX_CLICK_EVENT_DELAY_MS: Int = 5000

/**
 * 一个控件上最多允许绑定的按键事件数量
 *
 * 组合键的下限由数据层保证不了：布局文件是人可以手改的，因此数据层照单全收，
 * 这个上限只在编辑器里用来拦住"再加一个"的动作。
 */
const val MAX_KEY_COMBO_EVENTS: Int = 5

/**
 * 夹紧一个点击事件的延迟时长
 *
 * 读文件、滑杆、行内输入三条路都走它：负数当作 0（立即派发），超过上限按上限派发，
 * 非数字按 0 处理，因此没有任何一条路能让控件卡住不发事件。
 */
fun clampClickEventDelayMs(value: Int): Int =
    value.coerceIn(0, MAX_CLICK_EVENT_DELAY_MS)

/** [clampClickEventDelayMs] 的浮点入口，供滑杆使用 */
fun clampClickEventDelayMs(value: Float): Int =
    if (value.isNaN()) 0 else value.toInt().coerceIn(0, MAX_CLICK_EVENT_DELAY_MS)

/**
 * 解析行内输入的延迟时长
 *
 * 返回 null 表示这不是一次合法输入（空、非数字、负数、超过上限），
 * 此时不允许提交，而不是悄悄夹到边界上。
 */
fun clickEventDelayMsIn(text: String): Int? {
    val parsed = text.trim().toIntOrNull() ?: return null
    return if (parsed in 0..MAX_CLICK_EVENT_DELAY_MS) parsed else null
}

/**
 * 按键点击事件
 * @param type 绑定的点击事件类型
 * @param key 事件唯一标识/事件值
 * @param delayMs 派发该事件前等待的时长（毫秒），0 表示立即派发；取值 0..5000
 */
@Serializable
data class ClickEvent(
    @SerialName("type")
    val type: Type,
    @SerialName("key")
    val key: String,
    @SerialName("delayMs")
    val delayMs: Int = 0
): Modifiable<ClickEvent> {
    @Serializable
    enum class Type {
        /**
         * 点击触发按键
         */
        @SerialName("key")
        Key,

        /**
         * 点击触发启动器事件
         */
        @SerialName("launcher_event")
        LauncherEvent,

        /**
         * 点击开关控件层
         */
        @SerialName("switch_layer")
        SwitchLayer,

        /**
         * 点击强制显示控件层
         */
        @SerialName("show_layer")
        ShowLayer,

        /**
         * 点击强制隐藏控件层
         */
        @SerialName("hide_layer")
        HideLayer,

        /**
         * 点击发送聊天消息
         */
        @SerialName("send_text")
        SendText;

        /**
         * 该点击事件类型是否关于控件层
         */
        fun isAboutLayers(): Boolean =
            this == SwitchLayer ||
                    this == ShowLayer ||
                    this == HideLayer
    }

    /**
     * 该点击事件是否关于控件层
     */
    fun isAboutLayers(): Boolean = type.isAboutLayers()

    override fun isModified(other: ClickEvent): Boolean {
        return this.type != other.type ||
                this.key != other.key ||
                this.delayMs != other.delayMs
    }
}