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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.layercontroller.data.HideLayerWhen
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.layercontroller.utils.snap.SnapMode
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadgeTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell

/**
 * 编辑器的停靠面板
 *
 * 画布上那块正在接收游戏输入的活表面不能被盖住，所以编辑器把控制面板**压**在画布
 * 一侧、贴着启动边缘，而不是弹一个居中的对话框。压进去的宽度由
 * [ControlEditorMetrics] 按窗口宽度算出来，因此在 640x360 的下限上仍然给画布留下
 * 一半以上。
 *
 * 面板自己只有**一个**滚动容器（那条 [LazyColumn]）：区块标题、控件层、控件网格、
 * 位置与尺寸检视器、新建、预览与保存都在里面按顺序往下铺。因此不存在嵌套滚动，
 * 也不需要在展开某一块时把别的块推走。
 *
 * 面板**开着**时，画布那一层整个变成只读的（见 `ControlEditor.kt` 里传给
 * `ControlEditorLayer` 的 `interactive`）：背景点击、控件的拖动/点选与两个缩放
 * 手柄都不再安装指针输入。面板这一侧不挂任何整窗的触摸消费者——
 * 面板根部只是一块面板，面板之外的区域根本没有面板这一层的节点，
 * 因此不存在"遮罩与行抢同一按"的竞争：每一行只消费落到自己身上的事件。
 * 面板之外的点击仍然照旧交给画布。
 *
 * "做决定"的动作（改名、删层、选建哪一种）各自住在自己的一张小页里
 * （见 [EditorMenuSheet]），一次只打开一张，全部由真正的对话框窗口承载，
 * 不在面板里叠床架屋。
 */
@Composable
internal fun EditorDock(
    dockOpen: Boolean,
    layers: List<ObservableControlLayer>,
    selectedLayer: ObservableControlLayer?,
    selectedWidget: ObservableWidget?,
    widgetsInLayer: List<ObservableWidget>,
    isPreviewMode: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    closeScreen: () -> Unit,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    onLayerReorder: (from: Int, to: Int) -> Unit,
    isLayerFocus: Boolean,
    onLayerFocusChanged: (Boolean) -> Unit,
    onCreateLayer: () -> Unit,
    onLayerAttributes: (ObservableControlLayer) -> Unit,
    onToggleLayerVisibility: (ObservableControlLayer) -> Unit,
    onWidgetSelected: (ObservableWidget, ObservableControlLayer) -> Unit,
    onWidgetOpened: (ObservableWidget, ObservableControlLayer) -> Unit,
    onAddControl: (EditorControlKind) -> Unit,
    onOpenStyleList: () -> Unit,
    onOpenJoystickStyleList: () -> Unit,
    onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario,
    onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen,
    onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
    onSave: () -> Unit,
    saveAndExit: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onLayerRename: (ObservableControlLayer, String) -> Unit = { layer, name -> layer.name = name },
    onLayerDuplicate: (ObservableControlLayer) -> Unit = {},
    onLayerDelete: (ObservableControlLayer) -> Unit = {},
) {
    if (!dockOpen) return

    val metrics = editorMetrics()

    // 根部就是面板本身，不再包一层整窗的盒子：之前那层整窗盒子里的遮罩
    // 盖在面板之前，把落到每一行上的按下事件提前吃掉了。
    // 面板之外的触摸这里根本没有节点去接，自然落到画布上。
    EditorDockFrame(
        modifier = modifier
            .width(metrics.dockWidth)
            .padding(
                start = metrics.dockMargin,
                top = metrics.dockMargin,
                bottom = metrics.dockMargin,
            ),
        header = {
            EditorDockHeader(
                selectedLayerName = selectedLayer?.name,
                layerCount = layers.size,
                controlCount = widgetsInLayer.size,
                isPreviewMode = isPreviewMode,
                onClose = closeScreen,
            )
        },
        body = {
            EditorDockBody(
                layers = layers,
                selectedLayer = selectedLayer,
                selectedWidget = selectedWidget,
                widgetsInLayer = widgetsInLayer,
                isPreviewMode = isPreviewMode,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                onLayerSelected = onLayerSelected,
                onLayerReorder = onLayerReorder,
                isLayerFocus = isLayerFocus,
                onLayerFocusChanged = onLayerFocusChanged,
                onCreateLayer = onCreateLayer,
                onLayerAttributes = onLayerAttributes,
                onToggleLayerVisibility = onToggleLayerVisibility,
                onLayerRename = onLayerRename,
                onLayerDuplicate = onLayerDuplicate,
                onLayerDelete = onLayerDelete,
                onWidgetSelected = onWidgetSelected,
                onWidgetOpened = onWidgetOpened,
                onAddControl = onAddControl,
                onOpenStyleList = onOpenStyleList,
                onOpenJoystickStyleList = onOpenJoystickStyleList,
                onPreviewChanged = onPreviewChanged,
                previewScenario = previewScenario,
                onPreviewScenarioChanged = onPreviewScenarioChanged,
                previewHideLayerWhen = previewHideLayerWhen,
                onPreviewHideLayerChanged = onPreviewHideLayerChanged,
                onSave = onSave,
                saveAndExit = saveAndExit,
                onExit = onExit,
            )
        },
        // 底栏永远是可见的，因此引导不挂在这里——它挂在列表末尾那个
        // "Save" 分区上，两边指向的是同一组动作
        footer = {
            EditorFooterRow(modifier = Modifier.padding(horizontal = metrics.dockPadding)) {
                EditorFooterButton(
                    text = stringResource(R.string.generic_save),
                    onClick = onSave,
                    primary = true,
                )
                EditorFooterButton(
                    text = stringResource(R.string.control_editor_menu_save_and_exit),
                    onClick = saveAndExit,
                )
                EditorFooterButton(
                    text = stringResource(R.string.control_editor_exit_confirm),
                    onClick = onExit,
                )
            }
        },
    )
}

/** 面板顶部那一行：标题 + 三个数字，外加选中层的名字，以及一枚收起按钮 */
@Composable
private fun EditorDockHeader(
    selectedLayerName: String?,
    layerCount: Int,
    controlCount: Int,
    isPreviewMode: Boolean,
    onClose: () -> Unit,
) {
    val metrics = editorMetrics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dockPadding, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.control_editor_menu_title),
                color = Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EditorBadge(
                text = stringResource(R.string.oxide_ce_count_layers, layerCount.toString()),
                tone = if (layerCount == 0) OxideBadgeTone.Warn else OxideBadgeTone.Neutral,
            )
            Spacer(Modifier.width(4.dp))
            EditorBadge(
                text = stringResource(R.string.oxide_ce_count_controls, controlCount.toString()),
                tone = if (controlCount == 0) OxideBadgeTone.Neutral else OxideBadgeTone.Active,
            )
            if (isPreviewMode) {
                Spacer(Modifier.width(4.dp))
                EditorBadge(
                    text = stringResource(R.string.oxide_ce_preview_on),
                    tone = OxideBadgeTone.Warn,
                )
            }
            Spacer(Modifier.width(4.dp))
            // 收起面板的唯一显式出口：之前是点面板之外，现在那块整窗遮罩已经拿掉，
            // 点外面只会落到画布上，因此这里给一枚看得见的按钮
            EditorGlyphButton(
                glyph = "✕",
                description = stringResource(R.string.oxide_ce_close_dock),
                onClick = onClose,
            )
        }
        if (!selectedLayerName.isNullOrBlank()) {
            Text(
                text = selectedLayerName,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 面板里那一条唯一的滚动容器 */
@Composable
private fun EditorDockBody(
    onSave: () -> Unit,
    saveAndExit: () -> Unit,
    onExit: () -> Unit,
    layers: List<ObservableControlLayer>,
    selectedLayer: ObservableControlLayer?,
    selectedWidget: ObservableWidget?,
    widgetsInLayer: List<ObservableWidget>,
    isPreviewMode: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    onLayerReorder: (from: Int, to: Int) -> Unit,
    isLayerFocus: Boolean,
    onLayerFocusChanged: (Boolean) -> Unit,
    onCreateLayer: () -> Unit,
    onLayerAttributes: (ObservableControlLayer) -> Unit,
    onToggleLayerVisibility: (ObservableControlLayer) -> Unit,
    onLayerRename: (ObservableControlLayer, String) -> Unit,
    onLayerDuplicate: (ObservableControlLayer) -> Unit,
    onLayerDelete: (ObservableControlLayer) -> Unit,
    onWidgetSelected: (ObservableWidget, ObservableControlLayer) -> Unit,
    onWidgetOpened: (ObservableWidget, ObservableControlLayer) -> Unit,
    onAddControl: (EditorControlKind) -> Unit,
    onOpenStyleList: () -> Unit,
    onOpenJoystickStyleList: () -> Unit,
    onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario,
    onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen,
    onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
) {
    val metrics = editorMetrics()
    val listState = rememberLazyListState()

    // 一次只打开一张小页：新建选择器与某一层的更多操作互斥，
    // 关掉之后面板的状态原样不动
    var sheet by remember { mutableStateOf(EditorMenuSheet.None) }
    var sheetLayerUuid by remember { mutableStateOf<String?>(null) }
    // 那一层在页开着时被删掉：页回到没打开，而不是对着一个旧对象
    val liveSheetLayer = layers.firstOrNull { it.uuid == sheetLayerUuid }
        .takeIf { sheet == EditorMenuSheet.LayerActions }
    LaunchedEffect(layers, sheet) {
        if (sheet == EditorMenuSheet.LayerActions && liveSheetLayer == null) {
            sheet = EditorMenuSheet.None
            sheetLayerUuid = null
        }
    }

    // 换了一个选中的控件就滚到它那一段：否则在长列表里选中的东西可能落在视口外，
    // 检视器也就跟着看不见
    LaunchedEffect(selectedWidget) {
        if (selectedWidget == null) return@LaunchedEffect
        val index = widgetsInLayer.indexOfFirst { it === selectedWidget }
        if (index >= 0) {
            runCatching { listState.animateScrollToItem(index.coerceAtMost(listState.layoutInfo.totalItemsCount - 1)) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(metrics.dockPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
    ) {
        item(key = "section_layers") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_layers))
        }

        if (layers.isEmpty()) {
            item(key = "layers_empty") {
                EditorNoteRow(text = stringResource(R.string.oxide_ce_layers_empty))
            }
        }

        layers.forEachIndexed { index, layer ->
            item(key = "layer_${layer.uuid}") {
                EditorLayerRow(
                    name = layer.name,
                    selected = selectedLayer === layer,
                    hidden = layer.editorHide,
                    attributesText = stringResource(R.string.control_editor_layers_attribute),
                    visibilityText = stringResource(
                        if (layer.editorHide) R.string.oxide_ce_show_layer
                        else R.string.oxide_ce_hide_layer
                    ),
                    visibilityOnText = stringResource(R.string.oxide_ce_layer_hidden),
                    enabled = editorAllowsLayerEditing(isPreviewMode),
                    onSelect = {
                        onLayerSelected(if (selectedLayer === layer) null else layer)
                    },
                    onToggleVisibility = { onToggleLayerVisibility(layer) },
                    // 点"…"与长按进的是同一张小页：改名、复制、显隐、删除都在里面，
                    // 一次只做一件事，做完回到面板
                    onMenu = {
                        sheetLayerUuid = layer.uuid
                        sheet = EditorMenuSheet.LayerActions
                    },
                )
            }
            // 只有选中那一层才给换序按钮：每一行都放的话，窄面板上会被按钮占满
            if (selectedLayer === layer) {
                item(key = "layer_actions_${layer.uuid}") {
                    EditorLayerActions(
                        canMoveUp = index > 0,
                        canMoveDown = index < layers.lastIndex,
                        moveUpText = stringResource(R.string.oxide_ce_move_layer_up),
                        moveDownText = stringResource(R.string.oxide_ce_move_layer_down),
                        enabled = editorAllowsLayerEditing(isPreviewMode),
                        onMoveUp = { onLayerReorder(index, index - 1) },
                        onMoveDown = { onLayerReorder(index, index + 1) },
                    )
                }
            }
        }

        // 聚焦模式：只在画布上渲染选中那一层，其余层不参与。原来的它是一枚
        // 右上角的图标按钮，换成面板里的一行开关，触摸目标大得多，
        // 而且它与"预览模式"那行长得一样，读起来是一类东西
        item(key = "layer_focus") {
            EditorSwitchRow(
                label = stringResource(R.string.oxide_ce_layer_focus),
                hint = stringResource(R.string.oxide_ce_layer_focus_hint),
                checked = isLayerFocus,
                enabled = !isPreviewMode && selectedLayer != null,
                onCheckedChange = onLayerFocusChanged,
            )
        }

        item(key = "create_layer") {
            EditorActionRow(
                label = stringResource(R.string.control_editor_layers_create),
                enabled = editorAllowsLayerEditing(isPreviewMode),
                onClick = onCreateLayer,
            )
        }

        // ---- 控件网格 ----------------------------------------------------

        item(key = "section_controls") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_controls))
        }

        when {
            selectedLayer == null -> {
                item(key = "controls_no_layer") {
                    EditorNoteRow(text = stringResource(R.string.oxide_ce_controls_no_layer))
                }
            }

            widgetsInLayer.isEmpty() -> {
                item(key = "controls_empty") {
                    EditorEmptyRow(text = stringResource(R.string.oxide_ce_controls_empty))
                }
            }

            else -> {
                val columns = controlEditorGridColumns(metrics.contentWidth, metrics)
                controlEditorGridRowRanges(widgetsInLayer.size, columns).forEach { range ->
                    item(key = "controls_row_${range.first}") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.cellGap),
                        ) {
                            range.forEach { index ->
                                val widget = widgetsInLayer[index]
                                EditorControlCell(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(metrics.controlCellHeight),
                                    name = widget.editorCellName(),
                                    summary = widget.editorCellSummary(),
                                    kindGlyph = widget.editorKindGlyph(),
                                    selected = selectedWidget === widget,
                                    enabled = !isPreviewMode,
                                    onSelect = { onWidgetSelected(widget, selectedLayer) },
                                    onOpen = { onWidgetOpened(widget, selectedLayer) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- 选中控件的检视器 --------------------------------------------
        //
        // [editorShowsGeometrySection] 把"要不要显示这一块"这件事写成可测的判断，
        // 而不是散在条件里的 `!= null`
        val inspectorKind = selectedWidget?.editorKind()
        if (editorShowsGeometrySection(inspectorKind) && selectedWidget != null && selectedLayer != null) {
            item(key = "section_inspector") {
                EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_inspector))
            }
            item(key = "inspector") {
                EditorInspector(
                    widget = selectedWidget,
                    isPreviewMode = isPreviewMode,
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                    onOpenAdvanced = { onWidgetOpened(selectedWidget, selectedLayer) },
                )
            }
        }

        // ---- 新建控件 ----------------------------------------------------
        //
        // 只有一行：点开是一张选择器（按键 / 文本框 / 摇杆三选一）。
        // 建不了的时候这一行是灰的，并且原因就写在它下面——
        // 点不动的地方必须自己说出为什么，而不是悄悄没反应。

        item(key = "section_add") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_add))
        }

        val blocker = editorAddBlocker(
            layerCount = layers.size,
            hasSelectedLayer = selectedLayer != null,
            isPreviewMode = isPreviewMode,
        )
        val addAllowed = editorAllowsAddingControls(blocker)
        if (!addAllowed) {
            item(key = "add_blocked_reason") {
                EditorNoteRow(text = stringResource(blocker.toStringRes()))
            }
        }
        item(key = "add_controls") {
            EditorAddPickerRow(
                enabled = addAllowed,
                hint = if (addAllowed) null else stringResource(blocker.toStringRes()),
                onClick = { sheet = EditorMenuSheet.AddPicker },
            )
        }

        // ---- 外观与预览 --------------------------------------------------

        item(key = "section_styles") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_styles))
        }
        item(key = "styles") {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                EditorActionRow(
                    label = stringResource(R.string.control_editor_edit_style_config),
                    enabled = !isPreviewMode,
                    onClick = onOpenStyleList,
                )
                EditorActionRow(
                    label = stringResource(R.string.control_editor_edit_joystick_style_list),
                    enabled = !isPreviewMode,
                    onClick = onOpenJoystickStyleList,
                )
            }
        }

        item(key = "section_preview") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_preview))
        }
        item(key = "preview") {
            EditorPreviewBlock(
                isPreviewMode = isPreviewMode,
                scenario = previewScenario,
                onScenarioChanged = onPreviewScenarioChanged,
                hideLayerWhen = previewHideLayerWhen,
                onHideLayerWhenChanged = onPreviewHideLayerChanged,
                onPreviewChanged = onPreviewChanged,
            )
        }

        item(key = "section_snap") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_snap))
        }
        item(key = "snap") {
            EditorSnapBlock()
        }

        // 保存那一组。它挂在列表末尾而不是只在底栏，是为了引导能滚动到它；
        // 真正点得最多的保存/退出仍然在钉住的底栏上，两边是同一组动作
        item(key = "section_save") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_save))
        }
        item(key = "save") {
            EditorSaveBlock(onSave = onSave, saveAndExit = saveAndExit, onExit = onExit)
        }
    }

    // 小页一次只打开一张，全部由真正的对话框窗口承载：
    // 对话框自己带遮罩与窗口，不存在"面板里的兄弟节点抢触摸"的问题
    if (sheet == EditorMenuSheet.AddPicker) {
        EditorAddPickerSheet(
            blocker = editorAddPickerBlocker(
                layerCount = layers.size,
                hasSelectedLayer = selectedLayer != null,
                isPreviewMode = isPreviewMode,
            ),
            onDismiss = { sheet = EditorMenuSheet.None },
            onAdd = { kind ->
                sheet = EditorMenuSheet.None
                onAddControl(kind)
            },
        )
    }
    liveSheetLayer?.takeIf { sheet == EditorMenuSheet.LayerActions }?.let { layer ->
        EditorLayerActionsSheet(
            layer = layer,
            isPreviewMode = isPreviewMode,
            onDismiss = {
                sheet = EditorMenuSheet.None
                sheetLayerUuid = null
            },
            onAttributes = {
                sheet = EditorMenuSheet.None
                sheetLayerUuid = null
                onLayerAttributes(layer)
            },
            onToggleVisibility = { onToggleLayerVisibility(layer) },
            onRename = { name -> onLayerRename(layer, name) },
            onDuplicate = {
                sheet = EditorMenuSheet.None
                sheetLayerUuid = null
                onLayerDuplicate(layer)
            },
            onDelete = {
                sheet = EditorMenuSheet.None
                sheetLayerUuid = null
                onLayerDelete(layer)
            },
        )
    }
}

/**
 * 保存那一组的三块
 *
 * 与底栏那三个按钮指向同一组动作。底栏是常驻的，这一份挂在列表末尾，
 * 因此引导能滚动到它，两边不会各自长出不同的行为。
 */
@Composable
private fun EditorSaveBlock(
    onSave: () -> Unit,
    saveAndExit: () -> Unit,
    onExit: () -> Unit,
) {
    val metrics = editorMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
        EditorActionRow(
            label = stringResource(R.string.generic_save),
            emphasis = true,
            onClick = onSave,
        )
        EditorActionRow(
            label = stringResource(R.string.control_editor_menu_save_and_exit),
            onClick = saveAndExit,
        )
        EditorActionRow(
            label = stringResource(R.string.control_editor_exit_confirm),
            hint = stringResource(R.string.oxide_ce_exit_hint),
            onClick = onExit,
        )
    }
}

/**
 * 选中那一层下面那排换序按钮
 *
 * 用一对上下箭头而不是长按拖动：面板只有 200 多 dp 宽，拖动手柄既难命中，
 * 也无法在摇杆那一行的下方留出空间。方向只有"上一格/下一格"两种，
 * 因此两个按钮就说尽了。
 */
@Composable
private fun EditorLayerActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpText: String,
    moveDownText: String,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorGlyphButton(
            glyph = "▲",
            description = moveUpText,
            enabled = enabled && canMoveUp,
            onClick = onMoveUp,
        )
        EditorGlyphButton(
            glyph = "▼",
            description = moveDownText,
            enabled = enabled && canMoveDown,
            onClick = onMoveDown,
        )
    }
}

/**
 * 新建控件的那一行：只有一行，点开是三选一的选择器
 *
 * 建不了的时候整行是灰的，并且原因就写在行里与行下——
 * 点不动的地方必须自己说出为什么，而不是悄悄没反应。
 */
@Composable
private fun EditorAddPickerRow(
    enabled: Boolean,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.rowHeight.coerceAtLeast(44.dp))
            .clip(Oxide.RadiusControl)
            .background(if (enabled) Oxide.BgButton else Color.Transparent)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+",
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.oxide_ce_section_add),
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 新建控件的选择器：一张真正的对话框
 *
 * 三个选项共用同一道闸门：能建就三个都能点，不能建就三个一起灰，
 * 原因写在最上面。点完一种直接落到选中的层里，页随即关掉。
 */
@Composable
internal fun EditorAddPickerSheet(
    blocker: EditorAddBlocker,
    onDismiss: () -> Unit,
    onAdd: (EditorControlKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = editorAddPickerAllows(blocker)
    OxideDialogShell(
        title = stringResource(R.string.oxide_ce_section_add),
        onDismissRequest = onDismiss,
        body = { contentMaxHeight ->
            Column(
                modifier = modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!enabled) {
                    EditorNoteRow(text = stringResource(blocker.toStringRes()))
                }
                EditorActionRow(
                    label = stringResource(R.string.control_editor_menu_new_widget_button),
                    hint = stringResource(R.string.oxide_ce_kind_button),
                    enabled = enabled,
                    onClick = { onAdd(EditorControlKind.Button) },
                )
                EditorActionRow(
                    label = stringResource(R.string.control_editor_menu_new_widget_text),
                    hint = stringResource(R.string.oxide_ce_kind_text),
                    enabled = enabled,
                    onClick = { onAdd(EditorControlKind.Text) },
                )
                EditorActionRow(
                    label = stringResource(R.string.control_editor_menu_new_widget_joystick),
                    hint = stringResource(R.string.oxide_ce_kind_joystick),
                    enabled = enabled,
                    onClick = { onAdd(EditorControlKind.Joystick) },
                )
            }
        },
    )
}

/**
 * 某一层的更多操作：改名、复制、显隐、完整属性、删除
 *
 * 改名直接在这一页里改完：点开改名行，输入框就地展开，确认即写回。
 * 预览模式下整页只读——层在预览里不能改名换序删除，
 * 因此除了关闭之外每一行都是灰的，并注出原因。
 */
@Composable
internal fun EditorLayerActionsSheet(
    layer: ObservableControlLayer,
    isPreviewMode: Boolean,
    onDismiss: () -> Unit,
    onAttributes: () -> Unit,
    onToggleVisibility: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = editorAllowsLayerEditing(isPreviewMode)
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(layer.uuid) { mutableStateOf(layer.name) }
    OxideDialogShell(
        title = layer.name.ifBlank { stringResource(R.string.control_editor_layers_title) },
        onDismissRequest = onDismiss,
        body = { contentMaxHeight ->
            Column(
                modifier = modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isPreviewMode) {
                    EditorNoteRow(text = stringResource(R.string.oxide_ce_add_blocked_preview))
                }
                EditorActionRow(
                    label = stringResource(R.string.generic_rename),
                    hint = layer.name,
                    enabled = canEdit,
                    onClick = { renaming = !renaming },
                )
                if (renaming && canEdit) {
                    EditorLayerRenameField(
                        value = draft,
                        onValueChange = { draft = it },
                        onConfirm = {
                            onRename(draft)
                            renaming = false
                        },
                        onDismiss = { renaming = false },
                    )
                }
                EditorActionRow(
                    label = stringResource(R.string.generic_copy),
                    hint = layer.name,
                    enabled = canEdit,
                    onClick = onDuplicate,
                )
                EditorSwitchRow(
                    label = stringResource(
                        if (layer.editorHide) R.string.oxide_ce_show_layer
                        else R.string.oxide_ce_hide_layer
                    ),
                    hint = stringResource(R.string.oxide_ce_layer_hidden).takeIf { layer.editorHide },
                    checked = !layer.editorHide,
                    enabled = canEdit,
                    onCheckedChange = { onToggleVisibility() },
                )
                EditorActionRow(
                    label = stringResource(R.string.control_editor_layers_attribute),
                    enabled = canEdit,
                    onClick = onAttributes,
                )
                EditorActionRow(
                    label = stringResource(R.string.generic_delete),
                    enabled = canEdit,
                    onClick = onDelete,
                )
            }
        },
    )
}

/** 改名那一行的行内输入：确认即写回，取消即收起 */
@Composable
private fun EditorLayerRenameField(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    val confirmText = stringResource(R.string.generic_confirm)
    val closeText = stringResource(R.string.generic_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(metrics.fieldHeight)
                .clip(Oxide.RadiusControl)
                .background(Oxide.BgButton)
                .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Oxide.Type.Body.copy(color = Oxide.Fg),
                cursorBrush = SolidColor(Oxide.FgMuted),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        EditorMiniButton(text = "✓", description = confirmText, enabled = true, onClick = onConfirm)
        Spacer(Modifier.width(4.dp))
        EditorMiniButton(text = "✕", description = closeText, enabled = true, onClick = onDismiss)
    }
}

/** 预览那一组 */
@Composable
private fun EditorPreviewBlock(
    isPreviewMode: Boolean,
    scenario: PreviewScenario,
    onScenarioChanged: (PreviewScenario) -> Unit,
    hideLayerWhen: HideLayerWhen,
    onHideLayerWhenChanged: (HideLayerWhen) -> Unit,
    onPreviewChanged: (Boolean) -> Unit,
) {
    val metrics = editorMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
        EditorSwitchRow(
            label = stringResource(R.string.control_editor_menu_preview_mode),
            checked = isPreviewMode,
            onCheckedChange = onPreviewChanged,
        )
        EditorSegmentRow(
            label = stringResource(R.string.control_editor_menu_preview_mode_scenario),
            options = PreviewScenario.entries,
            selected = scenario,
            optionText = { stringResource(it.textRes) },
            onSelect = onScenarioChanged,
        )
        // 预览时才需要这一项：它只影响预览的隐藏判定，不影响存下来的布局
        if (isPreviewMode) {
            EditorSegmentRow(
                label = stringResource(R.string.oxide_ce_preview_device),
                options = listOf(
                    HideLayerWhen.None,
                    HideLayerWhen.WhenMouse,
                    HideLayerWhen.WhenGamepad,
                ),
                selected = hideLayerWhen,
                optionText = { option ->
                    stringResource(
                        when (option) {
                            HideLayerWhen.None -> R.string.oxide_ce_device_none
                            HideLayerWhen.WhenMouse -> R.string.oxide_ce_device_mouse
                            HideLayerWhen.WhenGamepad -> R.string.oxide_ce_device_gamepad
                        }
                    )
                },
                onSelect = onHideLayerWhenChanged,
            )
        }
    }
}

/** 吸附那一组 */
@Composable
private fun EditorSnapBlock() {
    val metrics = editorMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
        EditorSwitchRow(
            label = stringResource(R.string.control_editor_menu_widget_snap),
            checked = AllSettings.editorEnableWidgetSnap.state,
            onCheckedChange = { AllSettings.editorEnableWidgetSnap.save(it) },
        )
        EditorSwitchRow(
            label = stringResource(R.string.control_editor_menu_widget_snap_all_layers),
            checked = AllSettings.editorSnapInAllLayers.state,
            onCheckedChange = { AllSettings.editorSnapInAllLayers.save(it) },
            enabled = AllSettings.editorEnableWidgetSnap.state,
        )
        EditorSegmentRow(
            label = stringResource(R.string.control_editor_menu_widget_snap_mode),
            options = SnapMode.entries,
            selected = AllSettings.editorWidgetSnapMode.state,
            optionText = { mode ->
                stringResource(
                    when (mode) {
                        SnapMode.FullScreen -> R.string.control_editor_menu_widget_snap_mode_fullscreen
                        SnapMode.Local -> R.string.control_editor_menu_widget_snap_mode_local
                    }
                )
            },
            onSelect = { AllSettings.editorWidgetSnapMode.save(it) },
            enabled = AllSettings.editorEnableWidgetSnap.state,
        )
    }
}

/** 新建哪一类控件 */
internal enum class EditorControlKind { Button, Text, Joystick }

/** [EditorAddBlocker] 对应的那句提示 */
private fun EditorAddBlocker.toStringRes(): Int = when (this) {
    EditorAddBlocker.None -> R.string.oxide_ce_add_ready
    EditorAddBlocker.NoLayers -> R.string.control_editor_menu_no_layers_message
    EditorAddBlocker.NoSelectedLayer -> R.string.control_editor_menu_no_selected_layer_message
    EditorAddBlocker.Preview -> R.string.oxide_ce_add_blocked_preview
}