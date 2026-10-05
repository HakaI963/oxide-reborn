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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.oxide.inputmap.keycodes.ControlEventKeyName
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.event.MAX_CLICK_EVENT_DELAY_MS
import dev.oxide.layercontroller.event.MAX_KEY_COMBO_EVENTS
import dev.oxide.layercontroller.event.clampClickEventDelayMs
import dev.oxide.layercontroller.observable.ObservableClickEventsProvider
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.MarqueeText
import dev.oxide.launcher.ui.control.Keyboard
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutSliderItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutTextItem
import dev.oxide.launcher.ui.theme.itemColor
import dev.oxide.launcher.ui.theme.onItemColor

/**
 * 按键事件编辑
 */
@Composable
fun KeyEventEdit(
    provider: ObservableClickEventsProvider,
    modifier: Modifier = Modifier,
    containerColor: Color = itemColor(false),
    contentColor: Color = onItemColor(),
) {
    var showKeyboard by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .nonInteractiveScrollbar(
                state = scrollState.scrollIndicatorState!!,
                orientation = Orientation.Vertical,
            )
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        state = scrollState,
    ) {
        val keyEvents = provider.clickEvents.filter { it.type == ClickEvent.Type.Key }
        val canAddKey = keyEvents.size < MAX_KEY_COMBO_EVENTS

        item {
            InfoLayoutTextItem(
                modifier = Modifier.fillMaxWidth(),
                // 组合键有上限，所以这一行自己把"还能加几个"说出来，而不是等点了没反应
                title = "${stringResource(R.string.control_editor_edit_event_key_new)}" +
                        " (${keyEvents.size}/$MAX_KEY_COMBO_EVENTS)",
                onClick = {
                    if (canAddKey) showKeyboard = true
                },
                enabled = canAddKey,
                color = containerColor,
                contentColor = contentColor,
                showArrow = false
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(keyEvents) { event ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EditKeyItem(
                    modifier = Modifier.fillMaxWidth(),
                    keyEvent = event,
                    onDelayChange = { delayMs ->
                        provider.onReplaceEvent(event.copy(delayMs = clampClickEventDelayMs(delayMs)))
                    },
                    onDelete = {
                        provider.onRemoveEvent(event)
                    },
                    color = containerColor,
                    contentColor = contentColor,
                )
            }
        }
    }

    if (showKeyboard) {
        Keyboard(
            onDismissRequest = {
                showKeyboard = false
            },
            isTapMode = true,
            onTap = { selectedKey ->
                // 上限在这里再判一次：keyboard 的回调与点击那一行不是同一条路径
                val current = provider.clickEvents.count { it.type == ClickEvent.Type.Key }
                if (current >= MAX_KEY_COMBO_EVENTS) {
                    showKeyboard = false
                    return@Keyboard
                }
                val event = ClickEvent(type = ClickEvent.Type.Key, key = selectedKey)
                provider.onAddEvent(event)
                showKeyboard = false
            }
        )
    }
}

/**
 * 一个按键绑定及其延迟
 *
 * 延迟是这一条自己的属性，所以滑杆就挂在它下面，而不是整页一个总延迟：
 * 组合键里"先按 w，等 200ms 再按空格"正是这样一项一项排出来的。
 */
@Composable
private fun EditKeyItem(
    modifier: Modifier = Modifier,
    keyEvent: ClickEvent,
    onDelayChange: (Int) -> Unit,
    onDelete: () -> Unit,
    color: Color = itemColor(false),
    contentColor: Color = onItemColor(),
) {
    val name = remember(keyEvent.key) { ControlEventKeyName.getNameByKey(keyEvent.key) }

    InfoLayoutItem(
        modifier = modifier,
        onClick = {},
        color = color,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarqueeText(
                text = stringResource(R.string.control_editor_edit_event_key_value, name ?: keyEvent.key),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outlined),
                contentDescription = stringResource(R.string.generic_delete)
            )
        }
    }

    // 单位跟在数值后面（EditorSliderItem 的 suffix），范围就是派发时真正接受的那一段。
    // 这一行说的就是上面那一个键的延迟，所以用该键自己的延迟条目，不复用"按键值"那一行。
    InfoLayoutSliderItem(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.control_editor_edit_event_key_delay),
        value = keyEvent.delayMs.toFloat(),
        onValueChange = { onDelayChange(it.toInt()) },
        valueRange = 0f..MAX_CLICK_EVENT_DELAY_MS.toFloat(),
        decimalFormat = "#0",
        suffix = "ms"
    )
}