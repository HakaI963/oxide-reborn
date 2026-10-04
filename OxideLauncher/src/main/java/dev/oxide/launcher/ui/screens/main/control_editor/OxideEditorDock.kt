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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.guide.guideLazyList
import dev.oxide.layercontroller.data.HideLayerWhen
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.layercontroller.utils.snap.SnapMode
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.guide.GuideKeys
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadgeTone

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
 * 面板覆盖画布的那部分**必须**吃掉触摸（[editorConsumeTouches]），否则落在面板
 * 空白处的那一下会被画布当成玩家按了控件。面板之外的点击仍然照旧交给画布。
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
) {
    if (!dockOpen) return

    val metrics = editorMetrics()

    Box(modifier = modifier.fillMaxSize()) {
        EditorScrim(onClick = closeScreen)

        EditorDockFrame(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(metrics.dockWidth)
                .padding(
                    start = metrics.dockMargin,
                    top = metrics.dockMargin,
                    bottom = metrics.dockMargin,
                )
                .editorConsumeTouches(),
            header = {
                EditorDockHeader(
                    selectedLayerName = selectedLayer?.name,
                    layerCount = layers.size,
                    controlCount = widgetsInLayer.size,
                    isPreviewMode = isPreviewMode,
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
}

/** 面板顶部那一行：标题 + 三个数字，外加选中层的名字 */
@Composable
private fun EditorDockHeader(
    selectedLayerName: String?,
    layerCount: Int,
    controlCount: Int,
    isPreviewMode: Boolean,
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
        modifier = Modifier
            .fillMaxSize()
            // 引导要把某一分区滚进视口；控件层分区原来在右侧那一栏，
            // 现在整条面板是一根列表，因此把引导挂在这根列表上
            .guideLazyList(listState) { key ->
                when (key) {
                    GuideKeys.Editor.Step.LayerList -> "section_layers"
                    GuideKeys.Editor.Step.CreateLayer -> "create_layer"
                    GuideKeys.Editor.Step.AddButtons -> "add_controls"
                    GuideKeys.Editor.Step.AddStyles -> "styles"
                    GuideKeys.Editor.Step.Preview -> "preview"
                    GuideKeys.Editor.Step.Save -> "save"
                    else -> null
                }
            },
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
                    enabled = editorAllowsLayerEditing(!isPreviewMode),
                    onSelect = {
                        onLayerSelected(if (selectedLayer === layer) null else layer)
                    },
                    onAttributes = { onLayerAttributes(layer) },
                    onToggleVisibility = { onToggleLayerVisibility(layer) },
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
                        enabled = editorAllowsLayerEditing(!isPreviewMode),
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
                enabled = editorAllowsLayerEditing(!isPreviewMode),
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

        item(key = "section_add") {
            EditorGroupLabel(text = stringResource(R.string.oxide_ce_section_add))
        }

        val blocker = editorAddBlocker(
            layerCount = layers.size,
            hasSelectedLayer = selectedLayer != null,
            isPreviewMode = isPreviewMode,
        )
        item(key = "add_controls") {
            EditorAddControlBlock(
                blocker = blocker,
                enabled = editorAllowsAddingControls(blocker),
                onAdd = onAddControl,
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

/** 新建控件的三种类型，以及为什么不能新建 */
@Composable
private fun EditorAddControlBlock(
    blocker: EditorAddBlocker,
    enabled: Boolean,
    onAdd: (EditorControlKind) -> Unit,
) {
    val metrics = editorMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
        if (!enabled) {
            EditorNoteRow(text = stringResource(blocker.toStringRes()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.rowGap),
        ) {
            AddControlButton(
                modifier = Modifier.weight(1f),
                glyph = "▣",
                label = stringResource(R.string.control_editor_menu_new_widget_button),
                enabled = enabled,
                onClick = { onAdd(EditorControlKind.Button) },
            )
            AddControlButton(
                modifier = Modifier.weight(1f),
                glyph = "▤",
                label = stringResource(R.string.control_editor_menu_new_widget_text),
                enabled = enabled,
                onClick = { onAdd(EditorControlKind.Text) },
            )
            AddControlButton(
                modifier = Modifier.weight(1f),
                glyph = "✳",
                label = stringResource(R.string.control_editor_menu_new_widget_joystick),
                enabled = enabled,
                onClick = { onAdd(EditorControlKind.Joystick) },
            )
        }
    }
}

@Composable
private fun AddControlButton(
    glyph: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = editorMetrics()
    Box(
        modifier = modifier
            .height(metrics.rowHeight * 1.7f)
            .clip(Oxide.RadiusControl)
            .background(if (enabled) Oxide.BgButton else Color.Transparent)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = glyph,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
            )
            Text(
                text = label,
                color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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