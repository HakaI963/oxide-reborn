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

package dev.oxide.layercontroller.data

import dev.oxide.layercontroller.data.lang.TranslatableString
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.observable.Modifiable
import dev.oxide.layercontroller.observable.isModified
import dev.oxide.layercontroller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 宏重复间隔的下界（毫秒）
 *
 * 低于 40ms 的间隔会让游戏还没来得及处理上一次按键就收到下一次，
 * 于是表现为"按键时灵时不灵"，因此把它当成非法输入而不是可用值。
 */
const val MACRO_MIN_INTERVAL_MS: Long = 40

/**
 * 宏重复间隔的上界（毫秒，即一分钟）
 */
const val MACRO_MAX_INTERVAL_MS: Long = 60_000

/**
 * 夹紧一个宏重复间隔
 *
 * 0 与负数一律折成 0，也就是"关闭宏重复"：宏的开关与间隔是同一个字段，
 * 把关不掉的间隔夹成 40 会让控件莫名其妙开始连发。
 */
fun clampMacroIntervalMs(value: Long): Long =
    if (value <= 0L) 0L else value.coerceIn(MACRO_MIN_INTERVAL_MS, MACRO_MAX_INTERVAL_MS)

/** [clampMacroIntervalMs] 的浮点入口，供滑杆使用 */
fun clampMacroIntervalMs(value: Float): Long =
    if (value.isNaN()) 0L else clampMacroIntervalMs(value.toLong())

/**
 * 解析行内输入的宏重复间隔
 *
 * 返回 null 表示这不是一次合法输入（空、非数字、负数、低于下界、超过上界），
 * 此时不允许提交，而不是悄悄夹到边界上。
 */
fun macroIntervalMsIn(text: String): Long? {
    val parsed = text.trim().toLongOrNull() ?: return null
    return if (parsed in MACRO_MIN_INTERVAL_MS..MACRO_MAX_INTERVAL_MS) parsed else null
}

/**
 * 支持宏重复的控件所持有的设置
 *
 * 宏重复的配置存在控件数据里（[NormalData.macroIntervalMs]），而编辑器拿到的是
 * 可观察包装，因此包装类实现这个接口之后编辑器才会显示宏重复的开关与间隔。
 */
interface MacroRepeatHolder {
    /** 0 表示关闭宏重复，取值区间 40..60000（由 [clampMacroIntervalMs] 保证） */
    var macroIntervalMs: Long
}

/**
 * @param clickEvents 点击事件组
 * @param isSwipple 滑动可与周围的按钮联动操作
 * @param isPenetrable 是否允许将触摸事件向下穿透
 * @param isToggleable 是否用开关的形式切换按下状态
 * @param macroIntervalMs 按住时自动重复发送按键的间隔（毫秒），0 表示关闭
 */
@Serializable
data class NormalData(
    @SerialName("text")
    val text: TranslatableString,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("position")
    val position: ButtonPosition,
    @SerialName("buttonSize")
    val buttonSize: ButtonSize,
    @SerialName("buttonStyle")
    val buttonStyle: String? = null,
    @SerialName("textAlignment")
    val textAlignment: TextAlignment = TextAlignment.Left,
    @SerialName("textBold")
    val textBold: Boolean = false,
    @SerialName("textItalic")
    val textItalic: Boolean = false,
    @SerialName("textUnderline")
    val textUnderline: Boolean = false,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType,
    @SerialName("clickEvents")
    private var _clickEvents: List<ClickEvent> = emptyList(),
    @SerialName("isSwipple")
    val isSwipple: Boolean,
    @SerialName("isPenetrable")
    val isPenetrable: Boolean,
    @SerialName("isToggleable")
    val isToggleable: Boolean,
    @SerialName("macroIntervalMs")
    val macroIntervalMs: Long = 0L
): Widget, Modifiable<NormalData> {
    val clickEvents: List<ClickEvent> get() = _clickEvents

    init {
        _clickEvents = _clickEvents.filterValidEvent()
    }

    override fun isModified(other: NormalData): Boolean {
        return this.text.isModified(other.text) ||
                this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.buttonSize.isModified(other.buttonSize) ||
                this.buttonStyle != other.buttonStyle ||
                this.textAlignment != other.textAlignment ||
                this.textBold != other.textBold ||
                this.textItalic != other.textItalic ||
                this.textUnderline != other.textUnderline ||
                this.visibilityType != other.visibilityType ||
                this._clickEvents.isModified(other._clickEvents) ||
                this.isSwipple != other.isSwipple ||
                this.isPenetrable != other.isPenetrable ||
                this.isToggleable != other.isToggleable ||
                this.macroIntervalMs != other.macroIntervalMs
    }
}

/**
 * 过滤出有效的点击事件
 */
internal fun List<ClickEvent>.filterValidEvent(): List<ClickEvent> {
    var foundValidSendText = false
    return filter { event ->
        if (event.type == ClickEvent.Type.SendText) {
            if (!foundValidSendText && event.key.isNotEmpty()) {
                foundValidSendText = true
                true
            } else {
                false
            }
        } else {
            true
        }
    }
}

/**
 * 克隆一个新的NormalData对象（UUID、位置不同）
 */
fun NormalData.cloneNew(): NormalData = NormalData(
    text = this.text,
    uuid = getAButtonUUID(),
    position = CenterPosition,
    buttonSize = buttonSize,
    buttonStyle = buttonStyle,
    textAlignment = textAlignment,
    textBold = textBold,
    textItalic = textItalic,
    textUnderline = textUnderline,
    visibilityType = visibilityType,
    _clickEvents = clickEvents,
    isSwipple = isSwipple,
    isPenetrable = isPenetrable,
    isToggleable = isToggleable,
    macroIntervalMs = macroIntervalMs
)