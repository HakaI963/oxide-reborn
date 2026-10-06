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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.OwnOutlinedTextField
import dev.oxide.launcher.ui.components.SingleLineTextCheck
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutListItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutSwitchItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutTextItem
import dev.oxide.launcher.ui.screens.main.control_editor.getVisibilityText
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell

/**
 * 控件层属性对话框
 *
 * 旧实现是 `Dialog` + `ImePanContainer` + Material `Surface` 卡片 +
 * `Button`/`FilledTonalButton`：灰卡片、鲑鱼色按钮，与停靠面板完全两套语言。现在
 * 面板改由 [OxideDialogShell] 承载，与启动器里其余的对话框共用同一块面板。
 *
 * 内容、字符串、校验与回调一字未改：
 *
 * - 名称仍然先被 `SingleLineTextCheck` 单行化，并且立刻写回 `layer.name`；
 * - 可见场景、三个隐藏开关、下移合并、复制仍然是原来那几行，键与顺序都没动；
 * - 点"复制"之后滚动区仍然回到顶部（旧版靠 `scrollToTop` 这个 state）；
 * - 底部仍然是删除（描边）与关闭（实心）两枚，两枚都还在同一行；
 * - 点遮罩仍然关不掉（`dismissOnClickOutside = false`），返回键仍然能关——
 *   也就是 `dismissByDialog = false`：它只关掉遮罩点击，`dismissOnBackPress` 保持默认。
 *
 * 有一处**明确保留的旧东西**：名称输入框仍是 `ui/components` 的
 * `OwnOutlinedTextField`（Material `OutlinedTextField` 的一层壳）。它被别的界面共用，
 * 改它是一次输入控件的重做而不是对话框换皮，因此这一轮不动——换句话说，这一块面板
 * 内部仍有一处 Material 的输入框外观。
 */
@Composable
fun EditControlLayerDialog(
    layer: ObservableControlLayer,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    onMergeDownward: () -> Unit,
    onCopy: () -> Unit,
    onHideChange: (Boolean) -> Unit,
) {
    OxideDialogShell(
        title = stringResource(R.string.control_editor_layers_attribute),
        onDismissRequest = onDismissRequest,
        // 旧版 `dismissOnClickOutside = false`：点遮罩不关，返回键仍能关
        dismissByDialog = false,
        body = { contentMaxHeight ->
            val scrollState = rememberScrollState()

            var scrollToTop by remember { mutableStateOf(true) }
            LaunchedEffect(scrollToTop) {
                if (scrollToTop) {
                    runCatching {
                        scrollState.scrollTo(0)
                        scrollToTop = false
                    }
                }
            }

            Column(
                modifier = Modifier
                    // 先夹住高度，再在里面滚：面板给的 contentMaxHeight 就是上限
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleLineTextCheck(
                    text = layer.name,
                    onSingleLined = { layer.name = it }
                )

                //控件层名称
                OwnOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = layer.name,
                    onValueChange = {
                        layer.name = it
                    },
                    label = {
                        Text(stringResource(R.string.control_editor_layers_attribute_name))
                    },
                    singleLine = true,
                    // 旧版写的是 MaterialTheme.shapes.large，也就是 M3 默认的 16dp；
                    // 直接写死同一个数，这一块输入框因此看起来一模一样
                    shape = RoundedCornerShape(16.dp),
                )

                //可见场景
                InfoLayoutListItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_visibility),
                    items = VisibilityType.entries,
                    selectedItem = layer.visibilityType,
                    onItemSelected = { layer.visibilityType = it },
                    getItemText = { it.getVisibilityText() }
                )

                //默认隐藏控件层
                InfoLayoutSwitchItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_layers_attribute_hide),
                    value = layer.editorHide,
                    onValueChange = {
                        layer.editorHide = it
                        onHideChange(it)
                    }
                )

                //在实体鼠标操作时隐藏
                InfoLayoutSwitchItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_layers_attribute_hide_when_mouse),
                    value = layer.hideWhenMouse,
                    onValueChange = { layer.hideWhenMouse = it }
                )

                //在手柄操作时隐藏
                InfoLayoutSwitchItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_layers_attribute_hide_when_gamepad),
                    value = layer.hideWhenGamepad,
                    onValueChange = { layer.hideWhenGamepad = it }
                )

                //合并控件至下层
                InfoLayoutTextItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_layers_merge_downward),
                    icon = {
                        Icon(
                            modifier = Modifier
                                .rotate(180f)
                                .size(20.dp),
                            painter = painterResource(R.drawable.ic_merge),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        onMergeDownward()
                    }
                )

                //复制
                InfoLayoutTextItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.generic_copy),
                    icon = {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(R.drawable.ic_copy_all_outlined),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onCopy()
                        scrollToTop = true
                    }
                )
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_delete),
                onClick = onDelete,
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