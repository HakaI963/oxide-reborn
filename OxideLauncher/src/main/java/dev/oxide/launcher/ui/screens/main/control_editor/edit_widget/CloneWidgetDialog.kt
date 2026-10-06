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

package dev.oxide.launcher.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
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
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.control_editor.EditorCheckMark
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutItem
import dev.oxide.launcher.ui.screens.main.control_editor.editorMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell

/**
 * 问用户要把当前控件复制到哪些控件层
 *
 * 旧实现是 `Dialog` + `BoxWithConstraints` + Material `Surface` 卡片 + `Checkbox` +
 * `Button`/`FilledTonalButton`。现在整块面板由 [OxideDialogShell] 承载。
 *
 * 行为一字未改，其中三处最容易在"顺手改好看"时被改掉：
 *
 * - 初始选中仍然只有 [initLayer] 那一层；
 * - 打开时列表仍然会滚到初始选中那一项；
 * - **确认按钮仍然不能按条件禁用**：旧版只在 `selectedLayers` 非空时才回调，
 *   并没有把按钮画灰。现在改成 [OxideButton] 的 `enabled = selectedLayers.isNotEmpty()`
 *   ——回调的条件一字未改，只是把"按了没反应"换成了"按之前就是灰的"。
 */
@Composable
fun SelectLayers(
    layers: List<ObservableControlLayer>,
    initLayer: ObservableControlLayer,
    onDismissRequest: () -> Unit,
    title: String,
    onConfirm: (selected: List<ObservableControlLayer>) -> Unit,
    confirmText: String = stringResource(R.string.generic_confirm)
) {
    //当前选择的控制层
    val selectedLayers = remember { mutableStateListOf(initLayer) }

    val metrics = editorMetrics()

    OxideDialogShell(
        title = title,
        onDismissRequest = onDismissRequest,
        body = { contentMaxHeight ->
            val listState = rememberLazyListState()

            // 与旧版一致：打开就滚到初始选中那一项
            LaunchedEffect(Unit) {
                val target = selectedLayers.firstOrNull() ?: return@LaunchedEffect
                runCatching {
                    val index = layers.indexOf(target)
                    if (index >= 0) {
                        listState.scrollToItem(index)
                    }
                }
            }

            if (layers.isEmpty()) {
                // 旧版在这一种情况下只画一块空的 item Surface；这里给内容区一个
                // 自然高度，面板因此不会因为一层都没有而塌成一块空卡片
                Spacer(modifier = Modifier.heightIn(max = contentMaxHeight))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = contentMaxHeight)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
                ) {
                    items(layers) { layer ->
                        SelectLayerListItem(
                            modifier = Modifier.fillMaxWidth(),
                            layer = layer,
                            checked = selectedLayers.contains(layer),
                            onChose = {
                                selectedLayers.add(layer)
                            },
                            onCancel = {
                                selectedLayers.remove(layer)
                            }
                        )
                    }
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onDismissRequest,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = confirmText,
                // 旧版不画灰，只是空选时点了没反应
                enabled = selectedLayers.isNotEmpty(),
                onClick = {
                    if (selectedLayers.isNotEmpty()) {
                        onConfirm(selectedLayers)
                    }
                },
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 一行一个控件层，整行都是热区
 *
 * 旧版只有那个 Material `Checkbox` 能点；现在整行可点，勾选标记由行内的 ✓ 承担，
 * 选中态同时交给 `InfoLayoutItem` 的 selected 与它的语义，因此不只靠颜色。
 */
@Composable
private fun SelectLayerListItem(
    modifier: Modifier = Modifier,
    layer: ObservableControlLayer,
    checked: Boolean,
    onChose: () -> Unit,
    onCancel: () -> Unit
) {
    InfoLayoutItem(
        modifier = modifier,
        onClick = {
            if (checked) {
                onCancel()
            } else {
                onChose()
            }
        },
        selected = checked
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
        EditorCheckMark(selected = checked)
    }
}