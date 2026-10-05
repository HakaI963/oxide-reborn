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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import dev.oxide.launcher.ui.theme.Oxide
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.layout.createNewLayer
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.ProgressDialog
import dev.oxide.launcher.ui.components.SimpleAlertDialog
import dev.oxide.launcher.ui.components.SimpleEditDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_joystick.JoystickStyleListDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_layer.EditControlLayerDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_layer.EditSwitchLayersVisibilityDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_style.StyleListDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_translatable.EditTranslatableTextDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_widget.SelectLayers
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.EditorViewModel
import kotlin.math.roundToInt

/**
 * 悬浮球，以及那些还留在旧分区对话框里的操作
 *
 * 这一份里只有两件事：
 *
 * 1. **悬浮球**。过去用的是 `ui/components/Draggabble.kt` 的 `FloatingBall`，
 *    它画的是 Material 的 `Surface`。这里换成 Oxide 的一块描边方块，圆角与停靠
 *    面板一致；触摸行为不变（拖动移动、点击切换），仍然自己消费事件。
 * 2. **仍然是旧对话框的那些操作**：保存、保存失败、层的属性与删除、外观列表、
 *    复制到哪些层、编辑文本、发送文本、切换层可见性。停靠面板里只留了入口，
 *    内容仍由这些对话框承载（见报告里"没能重建"那一节）。
 */

// ---------------------------------------------------------------------------
// 悬浮球
// ---------------------------------------------------------------------------

/**
 * 打开或收起停靠面板的悬浮球
 *
 * [position] 是 ViewModel 里那对字段，单位是像素，null 表示还没落位。
 * [containerWidth] / [containerHeight] 是**外层**作用域量到的可用尺寸——以前这里
 * 自己又套了一层 `BoxWithConstraints(modifier.size(ballSize))`，于是内部的
 * `maxWidth`/`maxHeight` 恒等于球自身的边长，`maxX`/`maxY` 也就恒为 0：默认位置被
 * 夹成 `Offset.Zero`，拖动的增量同样被夹成 0，球因此永远停在左上角也永远拖不动。
 * 尺寸改由调用方从它自己的 `BoxWithConstraintsScope` 传进来。
 *
 * 停靠区由 [editorBallSafeBounds] 算，并且扣掉 `WindowInsets.safeDrawing`——圆角、
 * 刘海与系统栏都在里面，所以球既不会被拖到圆角底下，也不会停在系统栏上。
 *
 * @param position 已落位的位置，null 表示还没落位（走 [editorBallDefaultPosition]）
 * @param containerWidth 外层容器的宽度
 * @param containerHeight 外层容器的高度
 */
@Composable
internal fun EditorBall(
    modifier: Modifier = Modifier,
    position: Offset?,
    onPositionChanged: (Offset) -> Unit,
    opened: Boolean,
    onClick: () -> Unit,
    containerWidth: Dp,
    containerHeight: Dp,
) {
    val metrics = editorMetrics()
    val ballSize = metrics.ballSize
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val currentPosition by rememberUpdatedState(position)
    val currentOnClick by rememberUpdatedState(onClick)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val openText = stringResource(R.string.oxide_ce_open_dock)
    val closeText = stringResource(R.string.oxide_ce_close_dock)

    val safeDrawing = WindowInsets.safeDrawing
    val ballPx = with(density) { ballSize.toPx() }
    val bounds = with(density) {
        editorBallSafeBounds(
            available = Size(containerWidth.toPx(), containerHeight.toPx()),
            ball = ballPx,
            insets = EditorBallInsets(
                left = safeDrawing.getLeft(density).toFloat(),
                top = safeDrawing.getTop(density).toFloat(),
                right = safeDrawing.getRight(density).toFloat(),
                bottom = safeDrawing.getBottom(density).toFloat(),
            ),
        )
    }

    // 每次组合都重算。以前这一段被 `remember(measured, maxX)` 冻住，于是拖动写回
    // ViewModel 之后 anchored 也不会变——即使 maxX/maxY 不是 0，球同样拖不动
    val anchored = editorBallClamp(
        bounds = bounds,
        ball = ballPx,
        position = currentPosition ?: editorBallDefaultPosition(bounds, ballPx),
    )

    Box(
        modifier = modifier
            // 量到自身之前不画，否则第一帧会在默认位置上闪一下
            .alpha(if (measured == IntSize.Zero) 0f else 1f)
            .offset {
                IntOffset(
                    x = anchored.x.roundToInt(),
                    y = anchored.y.roundToInt(),
                )
            }
            .size(ballSize)
            .onSizeChanged { measured = it }
            .clip(Oxide.RadiusChip)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusChip)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { currentOnClick() },
            )
            .pointerInput(bounds, ballPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = down.position
                    var dragging = false
                    var current = anchored
                    // drag 返回 false 表示手势被取消，此时不触发点击
                    val completed = drag(down.id) { change ->
                        val delta = change.positionChange()
                        if (!dragging &&
                            (change.position - start).getDistance() > viewConfiguration.touchSlop
                        ) {
                            dragging = true
                        }
                        if (dragging) {
                            val step = if (isRtl) Offset(-delta.x, delta.y) else delta
                            current = editorBallClamp(
                                bounds = bounds,
                                ball = ballPx,
                                position = current + step,
                            )
                            onPositionChanged(current)
                        }
                        change.consume()
                    }
                    if (completed && !dragging) currentOnClick()
                }
            }
            .semantics {
                role = Role.Tab
                stateDescription = if (opened) closeText else openText
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ballSize * 0.5f),
            painter = painterResource(
                if (opened) R.drawable.ic_menu_open else R.drawable.ic_menu
            ),
            contentDescription = null,
            tint = if (opened) Oxide.Fg else Oxide.FgMuted,
        )
    }
}

// ---------------------------------------------------------------------------
// 仍然存在的那些对话框
// ---------------------------------------------------------------------------

/** 保存、保存失败、层的属性与删除、外观列表的创建与删除 */
@Composable
internal fun EditorOperationDialogs(viewModel: EditorViewModel) {
    when (val operation = viewModel.editorOperation) {
        is EditorOperation.None,
        is EditorOperation.SelectButton,
        is EditorOperation.EditButtonStyle,
        is EditorOperation.EditJoystickStyle -> {}

        is EditorOperation.EditLayer -> {
            val layer = operation.layer
            // onCopy 不是 composable，默认层名在组合期取好再传进去
            val defaultLayerName = stringResource(R.string.control_editor_edit_layer_default)
            EditControlLayerDialog(
                layer = layer,
                onDismissRequest = {
                    viewModel.editorOperation = EditorOperation.None
                },
                onDelete = {
                    viewModel.editorOperation = EditorOperation.DeleteLayer(layer)
                },
                onMergeDownward = {
                    viewModel.observableLayout.mergeDownward(layer)
                },
                onCopy = {
                    // 复制出来的层拿一份打包后的内容，再挂到布局上。
                    // 名字沿用"新建层"的默认名，与旧版一致；这个回调不是 composable，
                    // 所以文案在组合期取好再传进来，不能在这里调 stringResource。
                    val base = layer.pack()
                    val copied = viewModel.observableLayout.addLayer(
                        layer = createNewLayer(
                            defaultLayerName = defaultLayerName
                        ).copy(
                            hide = base.hide,
                            hideWhenMouse = base.hideWhenMouse,
                            hideWhenGamepad = base.hideWhenGamepad,
                            visibilityType = base.visibilityType,
                            normalButtons = base.normalButtons,
                            textBoxes = base.textBoxes,
                            joystickButtons = base.joystickButtons
                        )
                    )
                    viewModel.editorOperation = EditorOperation.EditLayer(copied)
                },
                onHideChange = { hide ->
                    if (hide && viewModel.selectedWidget?.layer == layer) {
                        viewModel.selectedWidget = null
                    }
                },
            )
        }

        is EditorOperation.DeleteLayer -> {
            val layer = operation.layer
            SimpleAlertDialog(
                title = stringResource(R.string.generic_delete),
                text = stringResource(R.string.control_editor_layers_delete, layer.name),
                onDismiss = {
                    viewModel.editorOperation = EditorOperation.None
                },
                onConfirm = {
                    val isWidgetLayer = viewModel.selectedWidget?.layer == layer
                    viewModel.removeLayer(layer)
                    if (isWidgetLayer) {
                        viewModel.selectedWidget = null
                    }
                    viewModel.editorOperation = EditorOperation.None
                }
            )
        }

        is EditorOperation.OpenStyleList -> {
            val styles by viewModel.observableLayout.styles.collectAsStateWithLifecycle()
            StyleListDialog(
                styles = styles,
                onEditStyle = { style ->
                    viewModel.selectedStyle = style
                    viewModel.editorOperation = EditorOperation.EditButtonStyle
                },
                onCreate = {
                    viewModel.editorOperation = EditorOperation.CreateStyle
                },
                onClone = { style -> viewModel.cloneStyle(style) },
                onDelete = { style ->
                    viewModel.editorOperation = EditorOperation.DeleteButtonStyle(style)
                },
                onClose = {
                    viewModel.editorOperation = EditorOperation.None
                }
            )
        }

        is EditorOperation.CreateStyle -> {
            var name by remember { mutableStateOf("") }
            SimpleEditDialog(
                title = stringResource(R.string.control_editor_edit_style_config_name),
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                onDismissRequest = {
                    viewModel.editorOperation = EditorOperation.None
                },
                onConfirm = {
                    viewModel.createNewStyle(name)
                    viewModel.editorOperation = EditorOperation.OpenStyleList
                }
            )
        }

        is EditorOperation.DeleteButtonStyle -> {
            val style = operation.style
            SimpleAlertDialog(
                title = stringResource(R.string.generic_delete),
                text = stringResource(R.string.control_editor_edit_style_config_delete, style.name),
                onDismiss = {
                    viewModel.editorOperation = EditorOperation.None
                },
                onConfirm = {
                    viewModel.removeStyle(style)
                    viewModel.editorOperation = EditorOperation.None
                }
            )
        }

        is EditorOperation.DeleteJoystickStyle -> {
            val style = operation.style
            SimpleAlertDialog(
                title = stringResource(R.string.control_editor_special_joystick_style_delete_title),
                text = stringResource(R.string.control_editor_edit_joystick_style_list_delete, style.name),
                confirmText = stringResource(R.string.generic_delete),
                onConfirm = {
                    viewModel.removeJoystickStyle(style)
                    viewModel.editorOperation = EditorOperation.None
                },
                onDismiss = {
                    viewModel.editorOperation = EditorOperation.None
                }
            )
        }

        is EditorOperation.OpenJoystickStyleList -> {
            val joystickStyles by viewModel.observableLayout.joystickStyles.collectAsStateWithLifecycle()
            JoystickStyleListDialog(
                styles = joystickStyles,
                onEditStyle = { style ->
                    viewModel.selectedJoystickStyle = style
                    viewModel.editorOperation = EditorOperation.EditJoystickStyle
                },
                onCreate = {
                    viewModel.editorOperation = EditorOperation.CreateJoystickStyle
                },
                onClone = { style -> viewModel.cloneJoystickStyle(style) },
                onDelete = { style ->
                    viewModel.editorOperation = EditorOperation.DeleteJoystickStyle(style)
                },
                onClose = {
                    viewModel.editorOperation = EditorOperation.None
                }
            )
        }

        is EditorOperation.CreateJoystickStyle -> {
            var name by remember { mutableStateOf("") }
            SimpleEditDialog(
                title = stringResource(R.string.control_editor_edit_joystick_style_list_name),
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                onDismissRequest = {
                    viewModel.editorOperation = EditorOperation.None
                },
                onConfirm = {
                    viewModel.createNewJoystickStyle(name)
                    viewModel.editorOperation = EditorOperation.OpenJoystickStyleList
                }
            )
        }

        is EditorOperation.Saving -> {
            ProgressDialog(title = stringResource(R.string.control_manage_saving))
        }

        is EditorOperation.SaveFailed -> {
            SimpleAlertDialog(
                title = stringResource(R.string.control_manage_failed_to_save),
                text = operation.error.getMessageOrToString()
            ) {
                viewModel.editorOperation = EditorOperation.None
            }
        }
    }
}

/** 复制到哪些层、删除确认、编辑文本、发送文本、切换层可见性 */
@Composable
internal fun EditorWidgetOperationDialogs(viewModel: EditorViewModel) {
    val layers by viewModel.observableLayout.layers.collectAsStateWithLifecycle()
    when (val operation = viewModel.editorWidgetOperation) {
        is EditorWidgetOperation.None -> {}

        is EditorWidgetOperation.CloneButton -> {
            val data = operation.data
            val layer = operation.layer
            SelectLayers(
                layers = layers,
                initLayer = layer,
                onDismissRequest = {
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                },
                title = stringResource(R.string.control_editor_edit_dialog_clone_widget_title),
                confirmText = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                onConfirm = { targetLayers ->
                    viewModel.cloneWidgetToLayers(data, targetLayers)
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                }
            )
        }

        is EditorWidgetOperation.DeleteButton -> {
            val data = operation.data
            val layer = operation.layer
            SimpleAlertDialog(
                title = stringResource(R.string.generic_delete),
                text = stringResource(R.string.control_editor_edit_dialog_delete_widget),
                onDismiss = {
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                },
                onConfirm = {
                    viewModel.removeWidget(layer, data)
                    viewModel.selectedWidget = null
                    viewModel.editorOperation = EditorOperation.None
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                }
            )
        }

        is EditorWidgetOperation.EditWidgetText -> {
            EditTranslatableTextDialog(
                text = operation.string,
                singleLine = false,
                onClose = {
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                }
            )
        }

        is EditorWidgetOperation.SendText -> {
            val data = operation.data
            var value by remember {
                mutableStateOf(
                    data.clickEvents
                        .find { it.type == ClickEvent.Type.SendText }
                        ?.key
                        .orEmpty()
                )
            }
            SimpleEditDialog(
                title = stringResource(R.string.control_editor_edit_event_launcher_send_text),
                value = value,
                onValueChange = { new -> value = new },
                singleLine = true,
                onConfirm = {
                    // 清除所有发送文本事件，如果文本不为空则再添加
                    data.onRemoveAllEvents(ClickEvent.Type.SendText)
                    if (value.isNotEmpty()) {
                        data.onAddEvent(ClickEvent(ClickEvent.Type.SendText, value))
                    }
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                }
            )
        }

        is EditorWidgetOperation.SwitchLayersVisibility -> {
            val data = operation.data
            val type = operation.type
            EditSwitchLayersVisibilityDialog(
                data = data,
                layers = layers,
                type = type,
                onDismissRequest = {
                    viewModel.editorWidgetOperation = EditorWidgetOperation.None
                }
            )
        }
    }
}

/** 没有层、没选中层这两种提示 */
@Composable
internal fun EditorWarningOperationDialogs(viewModel: EditorViewModel) {
    when (val operation = viewModel.editorWarningOperation) {
        is EditorWarningOperation.None -> {}

        is EditorWarningOperation.WarningNoLayers -> {
            SimpleAlertDialog(
                title = stringResource(R.string.control_editor_menu_no_layers_title),
                text = stringResource(R.string.control_editor_menu_no_layers_message)
            ) {
                viewModel.editorWarningOperation = EditorWarningOperation.None
            }
        }

        is EditorWarningOperation.WarningNoSelectLayer -> {
            SimpleAlertDialog(
                title = stringResource(R.string.control_editor_menu_no_selected_layer_title),
                text = stringResource(R.string.control_editor_menu_no_selected_layer_message)
            ) {
                viewModel.editorWarningOperation = EditorWarningOperation.None
            }
        }
    }
}