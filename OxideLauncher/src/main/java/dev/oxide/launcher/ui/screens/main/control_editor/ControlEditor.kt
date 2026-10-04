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
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.guide.guideNode
import dev.oxide.layercontroller.ControlEditorLayer
import dev.oxide.layercontroller.data.ButtonSize
import dev.oxide.layercontroller.data.CenterPosition
import dev.oxide.layercontroller.data.DefaultDirectionEvents
import dev.oxide.layercontroller.data.DefaultLockEvents
import dev.oxide.layercontroller.data.JoystickData
import dev.oxide.layercontroller.data.NormalData
import dev.oxide.layercontroller.data.TextData
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.data.createAdaptiveButtonSize
import dev.oxide.layercontroller.data.createWidgetWithUUID
import dev.oxide.layercontroller.data.lang.createTranslatable
import dev.oxide.layercontroller.layout.createNewLayer
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.ui.components.MenuState
import dev.oxide.launcher.ui.components.rememberBoxSize
import dev.oxide.launcher.ui.guide.GuideKeys
import dev.oxide.launcher.ui.screens.main.control_editor.edit_joystick.EditJoystickStyleDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_style.EditButtonStyleDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_widget.EditWidgetDialog
import dev.oxide.launcher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import dev.oxide.launcher.ui.screens.main.oxide.Oxide
import dev.oxide.launcher.viewmodel.EditorViewModel
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

/**
 * 控制布局编辑器
 *
 * 版面：整块屏幕都是画布，面板**压**在启动边缘而不是弹在中间（见
 * `OxideEditorDock.kt`）。画布由 `ControlEditorLayer` 渲染——那是后端，一行未改；
 * 停靠面板、检视器、控件网格、新建菜单、悬浮球都是 Oxide 的。
 *
 * 触摸的分工（画布是一块正在接收游戏输入的活表面，这一层不能搞错）：
 *
 * - 面板**开着**时，遮罩与面板吃掉落在它们身上的事件，剩下的那半块画布照旧可拖。
 * - 面板**关着**时，悬浮球吃掉自己那一小块，其余全部落到画布。
 * - 画布上那排快捷按钮由 `ControlEditorLayer` 摆在选中控件下方；它们同样自己
 *   消费事件，因此点它们不会顺带被画布当成"点背景"而清掉选择。
 *
 * @param exit 保存后执行的退出
 * @param menuExit 通过菜单直接调用的"直接退出"
 */
@Composable
fun BoxWithConstraintsScope.ControlEditor(
    viewModel: EditorViewModel,
    targetFile: File,
    exit: () -> Unit,
    menuExit: () -> Unit
) {
    val layers by viewModel.observableLayout.layers.collectAsStateWithLifecycle()
    val styles by viewModel.observableLayout.styles.collectAsStateWithLifecycle()
    val joystickStyles by viewModel.observableLayout.joystickStyles.collectAsStateWithLifecycle()

    /** 默认新建的控件层的名称 */
    val defaultLayerName = stringResource(R.string.control_editor_edit_layer_default)
    /** 默认新建的按键的名称 */
    val defaultButtonName = stringResource(R.string.control_editor_edit_button_default)
    /** 默认新建的文本框的名称 */
    val defaultTextName = stringResource(R.string.control_editor_edit_text_default)

    // 尺寸在组合期算一次：测量里不读任何设置，也不做任何 IO
    val metrics = rememberEditorMetrics()
    val density = LocalDensity.current
    val screenSize = rememberBoxSize()

    ProvideEditorMetrics(metrics) {
        if (viewModel.isPreviewMode) {
            PreviewControlBox(
                modifier = Modifier.fillMaxSize(),
                observableLayout = viewModel.observableLayout,
                previewScenario = viewModel.previewScenario,
                previewHideLayerWhen = viewModel.previewHideLayerWhen,
            )
        } else {
            ControlEditorLayer(
                observedLayout = viewModel.observableLayout,
                selectedWidget = viewModel.selectedWidget?.data,
                onButtonTap = { data, layer ->
                    val current = viewModel.selectedWidget?.data
                    viewModel.selectedWidget = SelectedWidgetData(data, layer)
                    if (current == data) {
                        // 选中后再点击一次，打开编辑菜单
                        viewModel.editorOperation = EditorOperation.SelectButton
                    }
                },
                onBackgroundClick = {
                    // 点击背景层时清除选中的控件
                    viewModel.selectedWidget = null
                },
                floatingButtons = {
                    // 画布上那排快捷操作：复制与删除。"全部设置"在检视器里，
                    // 因此这里不再重复一块
                    QuickAction(
                        text = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                        painter = painterResource(R.drawable.ic_file_copy_filled),
                        enabled = viewModel.selectedWidget != null,
                        onClick = {
                            val widget = viewModel.selectedWidget
                            if (widget != null) {
                                viewModel.editorWidgetOperation =
                                    EditorWidgetOperation.CloneButton(widget.data, widget.layer)
                            }
                        },
                    )
                    QuickAction(
                        text = stringResource(R.string.generic_delete),
                        painter = painterResource(R.drawable.ic_delete_filled),
                        enabled = viewModel.selectedWidget != null,
                        onClick = {
                            val widget = viewModel.selectedWidget
                            if (widget != null) {
                                viewModel.editorWidgetOperation =
                                    EditorWidgetOperation.DeleteButton(widget.data, widget.layer)
                            }
                        },
                    )
                },
                enableSnap = AllSettings.editorEnableWidgetSnap.state,
                snapInAllLayers = AllSettings.editorSnapInAllLayers.state,
                snapMode = AllSettings.editorWidgetSnapMode.state,
                focusedLayer = viewModel.selectedLayer?.takeIf { viewModel.isLayerFocus },
                isDark = isLauncherInDarkTheme()
            )
        }
    }

    ProvideEditorMetrics(metrics) {
        val dockOpen = viewModel.editorMenu == MenuState.SHOW
        val selectedLayer = viewModel.selectedLayer

        // 网格里要显示的是选中层的全部控件，因此这里订阅那三份 StateFlow。
        // 没有选中层时给一份空的：这里不能顺手选一层出来，"哪一层被选中"
        // 是用户的状态，不该由渲染决定
        val normalButtons by (selectedLayer?.normalButtons ?: emptyFlow())
            .collectAsStateWithLifecycle()
        val textBoxes by (selectedLayer?.textBoxes ?: emptyFlow()).collectAsStateWithLifecycle()
        val joystickButtons by (selectedLayer?.joystickButtons ?: emptyFlow())
            .collectAsStateWithLifecycle()

        val widgetsInLayer: List<ObservableWidget> =
            remember(normalButtons, textBoxes, joystickButtons) {
                // 文本框先、普通按键后、摇杆最后：与画布上的层次一致，
                // 因此网格里的次序与画布上的压盖关系是同一件事
                textBoxes + normalButtons + joystickButtons
            }

        EditorDock(
            dockOpen = dockOpen,
            layers = layers,
            selectedLayer = selectedLayer,
            selectedWidget = viewModel.selectedWidget?.data,
            widgetsInLayer = widgetsInLayer,
            isPreviewMode = viewModel.isPreviewMode,
            screenWidthDp = maxWidth.value,
            screenHeightDp = maxHeight.value,
            closeScreen = { viewModel.editorMenu = MenuState.HIDE },
            onLayerSelected = { layer -> viewModel.selectedLayer = layer },
            onLayerReorder = { from, to -> viewModel.observableLayout.reorder(from, to) },
            isLayerFocus = viewModel.isLayerFocus,
            onLayerFocusChanged = { viewModel.isLayerFocus = it },
            onCreateLayer = {
                val newLayer = viewModel.observableLayout.addLayer(
                    layer = createNewLayer(defaultLayerName = defaultLayerName)
                )
                viewModel.editorOperation = EditorOperation.EditLayer(newLayer)
            },
            onLayerAttributes = { layer ->
                viewModel.editorOperation = EditorOperation.EditLayer(layer)
            },
            onToggleLayerVisibility = { layer ->
                layer.editorHide = layer.editorHide.not()
                if (layer.editorHide && viewModel.selectedWidget?.layer == layer) {
                    viewModel.selectedWidget = null
                }
            },
            onWidgetSelected = { widget, layer ->
                viewModel.selectedWidget = SelectedWidgetData(widget, layer)
            },
            onWidgetOpened = { widget, layer ->
                viewModel.selectedWidget = SelectedWidgetData(widget, layer)
                viewModel.editorOperation = EditorOperation.SelectButton
            },
            onAddControl = { kind ->
                viewModel.addWidget(layers) { layer ->
                    when (kind) {
                        EditorControlKind.Button -> layer.addNormalButton(
                            createWidgetWithUUID { uuid ->
                                NormalData(
                                    text = createTranslatable(default = defaultButtonName),
                                    uuid = uuid,
                                    position = CenterPosition,
                                    buttonSize = createAdaptiveButtonSize(
                                        referenceLength = screenSize.height,
                                        density = density.density
                                    ),
                                    visibilityType = VisibilityType.ALWAYS,
                                    isSwipple = false,
                                    isPenetrable = false,
                                    isToggleable = false
                                )
                            }
                        )

                        EditorControlKind.Text -> layer.addTextBox(
                            createWidgetWithUUID { uuid ->
                                TextData(
                                    text = createTranslatable(default = defaultTextName),
                                    uuid = uuid,
                                    position = CenterPosition,
                                    // 文本框默认使用包裹内容
                                    buttonSize = createAdaptiveButtonSize(
                                        referenceLength = screenSize.height,
                                        density = density.density,
                                        type = ButtonSize.Type.WrapContent
                                    ),
                                    visibilityType = VisibilityType.ALWAYS
                                )
                            }
                        )

                        EditorControlKind.Joystick -> layer.addJoystickButton(
                            createWidgetWithUUID { uuid ->
                                JoystickData(
                                    uuid = uuid,
                                    position = CenterPosition,
                                    sizeType = ButtonSize.Type.Percentage,
                                    visibilityType = VisibilityType.ALWAYS,
                                    directionEvents = DefaultDirectionEvents,
                                    lockEvents = DefaultLockEvents,
                                )
                            }
                        )
                    }
                }
            },
            onOpenStyleList = {
                viewModel.editorOperation = EditorOperation.OpenStyleList
            },
            onOpenJoystickStyleList = {
                viewModel.editorOperation = EditorOperation.OpenJoystickStyleList
            },
            onPreviewChanged = { preview ->
                viewModel.applyEditorHide()
                viewModel.isPreviewMode = preview
            },
            previewScenario = viewModel.previewScenario,
            onPreviewScenarioChanged = { scenario ->
                viewModel.previewScenario = scenario
            },
            previewHideLayerWhen = viewModel.previewHideLayerWhen,
            onPreviewHideLayerChanged = { hideWhen ->
                viewModel.previewHideLayerWhen = hideWhen
            },
            onSave = {
                viewModel.save(targetFile, onSaved = {})
            },
            saveAndExit = {
                viewModel.save(targetFile, onSaved = exit)
            },
            onExit = menuExit,
        )

        EditorBall(
            modifier = Modifier.guideNode(
                key = GuideKeys.Editor.Step.MenuBall,
                holeRadius = 12.dp
            ),
            position = viewModel.editorBallPosition,
            onPositionChanged = { viewModel.editorBallPosition = it },
            opened = dockOpen,
            onClick = { viewModel.switchMenu() },
        )
    }

    // 控件编辑对话框（文本、事件、外观…）：外壳仍是旧的那套导航，
    // 但它内部每一个信息行都已经换成 Oxide 的了（见 `_Layout.kt`）。
    // 位置与尺寸这两块不在里面——它们已经在停靠面板的检视器里了，
    // 因此这里只作为"其余设置"的入口
    ProvideEditorMetrics(metrics) {
        EditWidgetDialog(
            data = viewModel.selectedWidget,
            visible = viewModel.editorOperation == EditorOperation.SelectButton,
            styles = styles,
            joystickStyles = joystickStyles,
            onDismissRequest = {
                viewModel.editorOperation = EditorOperation.None
            },
            onDelete = { data, layer ->
                viewModel.editorWidgetOperation = EditorWidgetOperation.DeleteButton(data, layer)
            },
            onClone = { data, layer ->
                viewModel.editorWidgetOperation = EditorWidgetOperation.CloneButton(data, layer)
            },
            onEditWidgetText = { string ->
                viewModel.editorWidgetOperation = EditorWidgetOperation.EditWidgetText(string)
            },
            switchControlLayers = { data, type ->
                viewModel.editorWidgetOperation = EditorWidgetOperation.SwitchLayersVisibility(data, type)
            },
            sendText = { data ->
                viewModel.editorWidgetOperation = EditorWidgetOperation.SendText(data)
            },
            openStyleList = {
                viewModel.editorOperation = EditorOperation.OpenStyleList
            },
            openJoystickStyleList = {
                viewModel.editorOperation = EditorOperation.OpenJoystickStyleList
            }
        )

        EditButtonStyleDialog(
            visible = viewModel.editorOperation == EditorOperation.EditButtonStyle,
            style = viewModel.selectedStyle,
            onClose = {
                viewModel.editorOperation = EditorOperation.None
            }
        )

        EditJoystickStyleDialog(
            visible = viewModel.editorOperation == EditorOperation.EditJoystickStyle,
            style = viewModel.selectedJoystickStyle,
            onClose = {
                viewModel.editorOperation = EditorOperation.None
            },
        )
    }

    // 剩下那些仍然是旧对话框的操作：保存、保存失败、层的属性、外观列表、复制、…
    ProvideEditorMetrics(metrics) {
        EditorOperationDialogs(viewModel = viewModel)
        EditorWidgetOperationDialogs(viewModel = viewModel)
        EditorWarningOperationDialogs(viewModel = viewModel)
    }
}

/**
 * 画布上那排快捷按钮中的一块
 *
 * 一块方形描边按钮：图标 + 文字。走 `Role.Button`，因此朗读时是一个按钮，
 * 而不是一段没有名字的图标。它自己消费事件，所以点它不会顺带被画布当成
 * "点背景"而把选择清掉。
 */
@Composable
private fun QuickAction(
    text: String,
    painter: Painter,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val metrics = editorMetrics()
    Row(
        modifier = Modifier
            .height(metrics.rowHeight)
            .clip(Oxide.RadiusButton)
            .background(if (enabled) Oxide.BgElevated else Oxide.SurfaceBase)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusButton)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(12.dp),
            painter = painter,
            contentDescription = null,
            tint = if (enabled) Oxide.Fg else Oxide.FgFaint,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}