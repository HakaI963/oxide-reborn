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

package dev.oxide.launcher.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.oxide.inputmap.keycodes.ControlEventKeyName
import dev.oxide.inputmap.keycodes.ControlEventKeycode
import dev.oxide.layercontroller.data.JOYSTICK_DEAD_ZONE_RANGE
import dev.oxide.layercontroller.data.JOYSTICK_LOCK_THRESHOLD_RANGE
import dev.oxide.layercontroller.data.JoystickTriggerMode
import dev.oxide.layercontroller.data.MACRO_MAX_INTERVAL_MS
import dev.oxide.layercontroller.data.MACRO_MIN_INTERVAL_MS
import dev.oxide.layercontroller.data.TextAlignment
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.data.clampMacroIntervalMs
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.event.MAX_CLICK_EVENT_DELAY_MS
import dev.oxide.layercontroller.event.MAX_KEY_COMBO_EVENTS
import dev.oxide.layercontroller.event.clampClickEventDelayMs
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.layercontroller.observable.ObservableClickEventsProvider
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableJoystickData
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
import dev.oxide.layercontroller.observable.ObservableNormalData
import dev.oxide.layercontroller.observable.ObservableTextData
import dev.oxide.layercontroller.observable.ObservableTranslatableString
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.layercontroller.observable.clickEventsProvider
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.control.Keyboard
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN_SINGLE
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP_SINGLE
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SWITCH_IME
import dev.oxide.launcher.ui.control.event.LAUNCHER_EVENT_SWITCH_MENU
import dev.oxide.launcher.ui.screens.main.control_editor.EditorChevron
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutListItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutSelectItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutSliderItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutSwitchItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutTextItem
import dev.oxide.launcher.ui.screens.main.control_editor.editorCellName
import dev.oxide.launcher.ui.screens.main.control_editor.getTriggerModeText
import dev.oxide.launcher.ui.screens.main.control_editor.getVisibilityText
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell
import dev.oxide.launcher.ui.screens.main.oxide.OxideIconButton
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 控件编辑页：一张真正的对话框，里面是一份从上到下直接滚完的表单
 *
 * 之前那一版是整屏覆盖层里再摆一个横向的标签列加导航：标签列的每一行都写了
 * 整行宽，导航那一块按权重只分到零宽，于是四个分类看得见、每一页的内容区
 * 都是黑的；盖在前面的那块整屏透明点击层还把点面板的手势提前吃掉了。
 *
 * 这一版把分类改成一份表单里的折叠分区（标题仍是原来那几个分类的文案），
 * 载体换成真正的对话框窗口：对话框自己带窗口与遮罩，
 * 不存在兄弟节点抢触摸的问题。
 *
 * 宏重复与按键延迟都住在"点击事件"分区里：宏是开关加间隔滑杆，
 * 延迟是每个按键各自的滑杆，用的都是数据层同一套夹紧与校验。
 */
@Composable
fun EditWidgetDialog(
    visible: Boolean,
    data: SelectedWidgetData?,
    styles: List<ObservableButtonStyle>,
    joystickStyles: List<ObservableJoystickStyle>,
    onDismissRequest: () -> Unit,
    onDelete: (ObservableWidget, ObservableControlLayer) -> Unit,
    onClone: (ObservableWidget, ObservableControlLayer) -> Unit,
    onEditWidgetText: (ObservableTranslatableString) -> Unit,
    switchControlLayers: (ObservableClickEventsProvider, ClickEvent.Type) -> Unit,
    sendText: (ObservableClickEventsProvider) -> Unit,
    openStyleList: () -> Unit,
    openJoystickStyleList: () -> Unit,
) {
    if (!visible || data == null) return
    val widget = data.data
    val layer = data.layer

    OxideDialogShell(
        title = widget.editorCellName(),
        onDismissRequest = onDismissRequest,
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (widget) {
                    is ObservableNormalData -> {
                        NormalInfoSection(widget = widget)
                        NormalClickEventSection(
                            widget = widget,
                            switchControlLayers = switchControlLayers,
                            sendText = sendText,
                        )
                        TextStyleSection(
                            onEditWidgetText = { onEditWidgetText(widget.text) },
                            textAlignment = widget.textAlignment,
                            onTextAlignmentChanged = { widget.textAlignment = it },
                            textBold = widget.textBold,
                            onTextBoldChanged = { widget.textBold = it },
                            textItalic = widget.textItalic,
                            onTextItalicChanged = { widget.textItalic = it },
                            textUnderline = widget.textUnderline,
                            onTextUnderlineChanged = { widget.textUnderline = it },
                        )
                        ButtonStyleSection(
                            styles = styles,
                            buttonStyle = widget.buttonStyle,
                            onButtonStyleChanged = { widget.buttonStyle = it },
                            openStyleList = openStyleList,
                        )
                    }

                    is ObservableTextData -> {
                        VisibilitySection(
                            visibilityType = widget.visibilityType,
                            onVisibilityTypeChanged = { widget.visibilityType = it },
                        )
                        TextStyleSection(
                            onEditWidgetText = { onEditWidgetText(widget.text) },
                            textAlignment = widget.textAlignment,
                            onTextAlignmentChanged = { widget.textAlignment = it },
                            textBold = widget.textBold,
                            onTextBoldChanged = { widget.textBold = it },
                            textItalic = widget.textItalic,
                            onTextItalicChanged = { widget.textItalic = it },
                            textUnderline = widget.textUnderline,
                            onTextUnderlineChanged = { widget.textUnderline = it },
                        )
                        ButtonStyleSection(
                            styles = styles,
                            buttonStyle = widget.buttonStyle,
                            onButtonStyleChanged = { widget.buttonStyle = it },
                            openStyleList = openStyleList,
                        )
                    }

                    is ObservableJoystickData -> {
                        VisibilitySection(
                            visibilityType = widget.visibilityType,
                            onVisibilityTypeChanged = { widget.visibilityType = it },
                        )
                        JoystickConfigSection(data = widget)
                        JoystickEventsSection(
                            data = widget,
                            switchControlLayers = switchControlLayers,
                            sendText = sendText,
                        )
                        JoystickStyleSection(
                            data = widget,
                            joystickStyles = joystickStyles,
                            openJoystickStyleList = openJoystickStyleList,
                        )
                    }
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_delete),
                onClick = { onDelete(widget, layer) },
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                onClick = { onClone(widget, layer) },
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_close),
                onClick = onDismissRequest,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 表单里的一个折叠分区
 *
 * 标题就是原来分类页的文案，因此原来四个分类的键与字符串一个没动：
 * 只是不再按标签切页，而是按分区上下铺开。
 */
@Composable
private fun EditSheetSection(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Oxide.RadiusControl)
                .clickable(role = Role.Tab, onClick = onToggle)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EditorChevron(expanded = open)
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 基本信息
// ---------------------------------------------------------------------------

/** 可见场景：三种控件都有，因此单独一份 */
@Composable
private fun VisibilitySection(
    visibilityType: VisibilityType,
    onVisibilityTypeChanged: (VisibilityType) -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.Info.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_visibility),
            items = VisibilityType.entries,
            selectedItem = visibilityType,
            onItemSelected = onVisibilityTypeChanged,
            getItemText = { it.getVisibilityText() },
        )
    }
}

/**
 * 普通按键的基本信息：可见场景加宏重复
 *
 * 宏的开关与间隔是同一个字段：0 就是关，大于 0 就是开。
 * 打开时给下界而不是 0，关闭时回到 0；间隔走数据层同一套夹紧。
 */
@Composable
private fun NormalInfoSection(widget: ObservableNormalData) {
    var open by remember(widget) { mutableStateOf(true) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.Info.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_visibility),
            items = VisibilityType.entries,
            selectedItem = widget.visibilityType,
            onItemSelected = { widget.visibilityType = it },
            getItemText = { it.getVisibilityText() },
        )
        val macroOn = widget.macroIntervalMs > 0L
        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_macro),
            value = macroOn,
            onValueChange = { checked ->
                widget.macroIntervalMs = if (checked) MACRO_MIN_INTERVAL_MS else 0L
            },
        )
        if (macroOn) {
            InfoLayoutSliderItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.control_editor_edit_event_macro_interval),
                value = widget.macroIntervalMs.toFloat(),
                onValueChange = { widget.macroIntervalMs = clampMacroIntervalMs(it) },
                valueRange = MACRO_MIN_INTERVAL_MS.toFloat()..MACRO_MAX_INTERVAL_MS.toFloat(),
                decimalFormat = "#0",
                suffix = "ms",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 点击事件：开关、按键加延迟、启动器事件、切层
// ---------------------------------------------------------------------------

@Composable
private fun NormalClickEventSection(
    widget: ObservableNormalData,
    switchControlLayers: (ObservableClickEventsProvider, ClickEvent.Type) -> Unit,
    sendText: (ObservableClickEventsProvider) -> Unit,
) {
    var open by remember(widget) { mutableStateOf(false) }
    val provider = remember(widget) { clickEventsProvider(widget) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.ClickEvent.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_swipple),
            value = widget.isSwipple,
            onValueChange = { widget.isSwipple = it },
        )
        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_penetrable),
            value = widget.isPenetrable,
            onValueChange = { widget.isPenetrable = it },
        )
        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_toggleable),
            value = widget.isToggleable,
            onValueChange = { widget.isToggleable = it },
        )
        KeyEventsBlock(provider = provider)
        LauncherEventsBlock(provider = provider, onSendText = { sendText(provider) })
        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_switch_layers),
            onClick = { switchControlLayers(provider, ClickEvent.Type.SwitchLayer) },
        )
        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_show_layers),
            onClick = { switchControlLayers(provider, ClickEvent.Type.ShowLayer) },
        )
        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_hide_layers),
            onClick = { switchControlLayers(provider, ClickEvent.Type.HideLayer) },
        )
    }
}

/**
 * 按键绑定：每个键各自带一条延迟
 *
 * 延迟是这一条自己的属性，因此滑杆就跟在它下面，
 * 而不是整页一个总延迟：组合键里先按后按的间隔正是这样一项一项排出来的。
 * 范围与夹紧走数据层同一套：0 表示立即派发，上限 5000 毫秒。
 */
@Composable
private fun KeyEventsBlock(provider: ObservableClickEventsProvider) {
    var showKeyboard by remember { mutableStateOf(false) }
    val keyEvents = provider.clickEvents.filter { it.type == ClickEvent.Type.Key }
    val canAddKey = keyEvents.size < MAX_KEY_COMBO_EVENTS
    InfoLayoutTextItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_key_new) +
            " (${keyEvents.size}/$MAX_KEY_COMBO_EVENTS)",
        onClick = { if (canAddKey) showKeyboard = true },
        enabled = canAddKey,
        showArrow = false,
    )
    keyEvents.forEach { event ->
        val name = remember(event.key) { ControlEventKeyName.getNameByKey(event.key) }
        InfoLayoutItem(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.control_editor_edit_event_key_value, name ?: event.key),
                color = Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OxideIconButton(
                onClick = { provider.onRemoveEvent(event) },
                glyph = "✕",
                contentDescription = stringResource(R.string.generic_delete),
            )
        }
        InfoLayoutSliderItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_key_delay),
            value = event.delayMs.toFloat(),
            onValueChange = { provider.onReplaceEvent(event.copy(delayMs = clampClickEventDelayMs(it.toInt()))) },
            valueRange = 0f..MAX_CLICK_EVENT_DELAY_MS.toFloat(),
            decimalFormat = "#0",
            suffix = "ms",
        )
    }
    if (showKeyboard) {
        Keyboard(
            onDismissRequest = { showKeyboard = false },
            isTapMode = true,
            onTap = { selectedKey ->
                val current = provider.clickEvents.count { it.type == ClickEvent.Type.Key }
                if (current >= MAX_KEY_COMBO_EVENTS) {
                    showKeyboard = false
                    return@Keyboard
                }
                provider.onAddEvent(ClickEvent(type = ClickEvent.Type.Key, key = selectedKey))
                showKeyboard = false
            },
        )
    }
}

/** 启动器事件：一组开关加一个发送文本的出口，没有自己的滚动 */
@Composable
private fun LauncherEventsBlock(
    provider: ObservableClickEventsProvider,
    onSendText: () -> Unit,
) {
    val events = provider.clickEvents
    fun has(key: String): Boolean = events.any { it.type == ClickEvent.Type.LauncherEvent && it.key == key }
    fun toggle(value: Boolean, event: ClickEvent) {
        if (value) provider.onAddEvent(event) else provider.onRemoveEvent(event)
    }
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.game_menu_option_input_method),
        value = has(LAUNCHER_EVENT_SWITCH_IME),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SWITCH_IME)) },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_switch_menu),
        value = has(LAUNCHER_EVENT_SWITCH_MENU),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SWITCH_MENU)) },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_left),
        value = has(ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT),
        onValueChange = {
            toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT))
        },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_middle),
        value = has(ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE),
        onValueChange = {
            toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE))
        },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_right),
        value = has(ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT),
        onValueChange = {
            toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT))
        },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_up),
        value = has(LAUNCHER_EVENT_SCROLL_UP),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_UP)) },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_up_single),
        value = has(LAUNCHER_EVENT_SCROLL_UP_SINGLE),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_UP_SINGLE)) },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_down),
        value = has(LAUNCHER_EVENT_SCROLL_DOWN),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_DOWN)) },
    )
    InfoLayoutSwitchItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_down_single),
        value = has(LAUNCHER_EVENT_SCROLL_DOWN_SINGLE),
        onValueChange = { toggle(it, ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_DOWN_SINGLE)) },
    )
    InfoLayoutTextItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_launcher_send_text),
        onClick = onSendText,
    )
}

// ---------------------------------------------------------------------------
// 文本与外观
// ---------------------------------------------------------------------------

@Composable
private fun TextStyleSection(
    onEditWidgetText: () -> Unit,
    textAlignment: TextAlignment,
    onTextAlignmentChanged: (TextAlignment) -> Unit,
    textBold: Boolean,
    onTextBoldChanged: (Boolean) -> Unit,
    textItalic: Boolean,
    onTextItalicChanged: (Boolean) -> Unit,
    textUnderline: Boolean,
    onTextUnderlineChanged: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    EditSheetSection(
        title = stringResource(R.string.control_editor_edit_text),
        open = open,
        onToggle = { open = !open },
    ) {
        InfoLayoutTextItem(
            title = stringResource(R.string.control_editor_edit_text),
            onClick = onEditWidgetText,
        )
        InfoLayoutSelectItem(
            title = stringResource(R.string.control_editor_edit_text_alignment),
            options = TextAlignment.entries,
            current = textAlignment,
            onClick = { value -> if (textAlignment != value) onTextAlignmentChanged(value) },
            label = { item ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val icon = when (item) {
                        TextAlignment.Left -> R.drawable.ic_format_align_left
                        TextAlignment.Center -> R.drawable.ic_format_align_center
                        TextAlignment.Right -> R.drawable.ic_format_align_right
                    }
                    Icon(painter = painterResource(icon), contentDescription = null)
                }
            },
        )
        InfoLayoutSwitchItem(
            title = stringResource(R.string.control_editor_edit_text_bold),
            value = textBold,
            onValueChange = onTextBoldChanged,
        )
        InfoLayoutSwitchItem(
            title = stringResource(R.string.control_editor_edit_text_italic),
            value = textItalic,
            onValueChange = onTextItalicChanged,
        )
        InfoLayoutSwitchItem(
            title = stringResource(R.string.control_editor_edit_text_underline),
            value = textUnderline,
            onValueChange = onTextUnderlineChanged,
        )
    }
}

/** 控件外观：就地单选，完整列表仍走原来的外观页 */
@Composable
private fun ButtonStyleSection(
    styles: List<ObservableButtonStyle>,
    buttonStyle: String?,
    onButtonStyleChanged: (String?) -> Unit,
    openStyleList: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.Style.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        val options = remember(styles) { listOf(null) + styles }
        val selected = remember(styles, buttonStyle) { styles.find { it.uuid == buttonStyle } }
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_category_style),
            items = options,
            selectedItem = selected,
            onItemSelected = { onButtonStyleChanged(it?.uuid) },
            getItemText = { item ->
                item?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.generic_unspecified)
            },
        )
        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_style_config),
            onClick = openStyleList,
        )
    }
}

// ---------------------------------------------------------------------------
// 摇杆：配置、方向事件、外观
// ---------------------------------------------------------------------------

@Composable
private fun JoystickConfigSection(data: ObservableJoystickData) {
    var open by remember(data) { mutableStateOf(true) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.JoystickConfig.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        InfoLayoutSliderItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_joystick_dead_zone),
            value = data.deadZoneRatio,
            onValueChange = { data.deadZoneRatio = it },
            valueRange = JOYSTICK_DEAD_ZONE_RANGE,
            decimalFormat = "#0.00",
        )
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_joystick_trigger_mode),
            items = JoystickTriggerMode.entries,
            selectedItem = data.triggerMode,
            onItemSelected = { data.triggerMode = it },
            getItemText = { it.getTriggerModeText() },
        )
        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_joystick_can_lock),
            value = data.canLock,
            onValueChange = { data.canLock = it },
        )
        if (data.canLock) {
            InfoLayoutSliderItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.control_editor_edit_joystick_lock_threshold),
                value = data.lockThreshold,
                onValueChange = { data.lockThreshold = it },
                valueRange = JOYSTICK_LOCK_THRESHOLD_RANGE,
                decimalFormat = "#0.00",
            )
        }
    }
}

/**
 * 方向事件：沿用原来的方向盘入口
 *
 * 它自己要一块固定高度：方向盘是按宽高比铺开的九宫格，
 * 在无限高的表单里量不到尺寸，因此这里给它一块固定高度。
 */
@Composable
private fun JoystickEventsSection(
    data: ObservableJoystickData,
    switchControlLayers: (ObservableClickEventsProvider, ClickEvent.Type) -> Unit,
    sendText: (ObservableClickEventsProvider) -> Unit,
) {
    var open by remember(data) { mutableStateOf(false) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.DirectionEvents.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            EditJoystickEvents(
                data = data,
                switchControlLayers = switchControlLayers,
                sendText = sendText,
            )
        }
    }
}

@Composable
private fun JoystickStyleSection(
    data: ObservableJoystickData,
    joystickStyles: List<ObservableJoystickStyle>,
    openJoystickStyleList: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    EditSheetSection(
        title = stringResource(EditWidgetCategory.JoystickStyle.titleRes),
        open = open,
        onToggle = { open = !open },
    ) {
        val options = remember(joystickStyles) { listOf(null) + joystickStyles }
        val selected = remember(joystickStyles, data.joystickStyleId) {
            joystickStyles.find { it.uuid == data.joystickStyleId }
        }
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_category_joystick_style),
            items = options,
            selectedItem = selected,
            onItemSelected = { data.joystickStyleId = it?.uuid },
            getItemText = { item ->
                item?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.generic_unspecified)
            },
        )
        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_joystick_style_list),
            onClick = openJoystickStyleList,
        )
    }
}

/** 分类键自带的标题：表单分区沿用原来四个分类的文案 */
private val EditWidgetCategory.titleRes: Int
    get() = when (this) {
        EditWidgetCategory.Info -> R.string.control_editor_edit_category_info
        EditWidgetCategory.TextStyle -> R.string.control_editor_edit_text
        EditWidgetCategory.ClickEvent -> R.string.control_editor_edit_category_event
        EditWidgetCategory.Style -> R.string.control_editor_edit_category_style
        EditWidgetCategory.JoystickConfig -> R.string.control_editor_edit_category_joystick_config
        EditWidgetCategory.DirectionEvents -> R.string.control_editor_edit_category_joystick_events
        EditWidgetCategory.JoystickStyle -> R.string.control_editor_edit_category_joystick_style
    }
