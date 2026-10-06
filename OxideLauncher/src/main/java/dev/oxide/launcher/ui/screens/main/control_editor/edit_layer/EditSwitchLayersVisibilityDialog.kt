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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor.edit_layer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.observable.ObservableClickEventsProvider
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.control_editor.EditorCheckMark
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutItem
import dev.oxide.launcher.ui.screens.main.control_editor.editorMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 编辑点击事件：切换控件层可见性
 *
 * 旧实现是 `Dialog` + Material `Surface` 卡片 + Material `Checkbox` + `Button`。
 * 现在整块面板改由 [OxideDialogShell] 承载，勾选状态由 Oxide 那一族自己画。
 *
 * 行为一字未改，包括开头那一段**修正**：如果某个事件指向的控件层已经不存在了，
 * [data.onRemoveAllEvents] 会先把它们清掉；否则这里显示的勾选与真正生效的事件就对不上了。
 *
 * @param type 控制控件层的类型
 */
@Composable
fun EditSwitchLayersVisibilityDialog(
    data: ObservableClickEventsProvider,
    layers: List<ObservableControlLayer>,
    type: ClickEvent.Type,
    onDismissRequest: () -> Unit
) {
    LaunchedEffect(type) {
        if (!type.isAboutLayers()) error("This type {$type} is unrelated to the control layer.")
    }

    /**
     * 缓存哪些控件层被选中
     */
    val layerSelected = remember { mutableStateListOf<ObservableControlLayer>() }

    LaunchedEffect(data.clickEvents) {
        val layerUuids = layers.map { it.uuid }.toSet()
        val unsafeEvents = data.clickEvents.filter { event ->
            event.isAboutLayers() && event.key !in layerUuids //控件层已不存在
        }

        if (unsafeEvents.isNotEmpty()) {
            data.onRemoveAllEvents(unsafeEvents)
            return@LaunchedEffect
        }

        val validLayerEvents = data.clickEvents.filter {
            it.type == type
        }

        val eventLayerMap = validLayerEvents.associateBy { it.key }
        val selectedLayers = layers.filter { layer ->
            layer.uuid in eventLayerMap
        }

        layerSelected.clear()
        layerSelected.addAll(selectedLayers)
    }

    val metrics = editorMetrics()

    OxideDialogShell(
        title = stringResource(
            when (type) {
                ClickEvent.Type.ShowLayer -> R.string.control_editor_edit_show_layers
                ClickEvent.Type.HideLayer -> R.string.control_editor_edit_hide_layers
                else -> R.string.control_editor_edit_switch_layers
            }
        ),
        onDismissRequest = onDismissRequest,
        body = { contentMaxHeight ->
            val scrollState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth(),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
            ) {
                items(layers) { layer ->
                    LayerVisibilityItem(
                        modifier = Modifier.fillMaxWidth(),
                        layer = layer,
                        selected = layerSelected.contains(layer),
                        onSelectedChange = { selected ->
                            val event = ClickEvent(type, layer.uuid)
                            if (selected) {
                                data.onAddEvent(event)
                            } else {
                                data.onRemoveEvent(event)
                            }
                        }
                    )
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_close),
                onClick = onDismissRequest,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 一行一个控件层，整行都是热区
 *
 * 旧版这里是一个 Material `Checkbox` 加一行文字，只有那个方块能点；现在整行可点，
 * 勾选标记由 [EditorCheckMark] 画，`selected` 同时交给无障碍服务，因此不只靠颜色。
 */
@Composable
private fun LayerVisibilityItem(
    modifier: Modifier = Modifier,
    layer: ObservableControlLayer,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    InfoLayoutItem(
        modifier = modifier,
        onClick = {
            onSelectedChange(!selected)
        },
        selected = selected
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = layer.name,
            color = Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EditorCheckMark(selected = selected)
    }
}