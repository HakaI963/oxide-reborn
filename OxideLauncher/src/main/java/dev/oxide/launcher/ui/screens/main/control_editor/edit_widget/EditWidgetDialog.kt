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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.layercontroller.observable.ObservableClickEventsProvider
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableJoystickData
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
import dev.oxide.layercontroller.observable.ObservableNormalData
import dev.oxide.layercontroller.observable.ObservableTranslatableString
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.EdgeDirection
import dev.oxide.launcher.ui.components.fadeEdge
import dev.oxide.launcher.ui.screens.TitledNavKey
import dev.oxide.launcher.ui.screens.clearWith
import dev.oxide.launcher.ui.screens.content.elements.CategoryItem
import dev.oxide.launcher.ui.screens.main.control_editor.editorMetrics
import dev.oxide.launcher.ui.screens.main.control_editor.editorPanelBackground
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.rememberSwapTween
import dev.oxide.launcher.ui.screens.rememberTitledNavBackStack
import dev.oxide.launcher.ui.screens.rememberTransitionSpec
import dev.oxide.launcher.ui.theme.Oxide

private enum class EditWidgetDialogState(val alpha: Float, val buttonText: Int) {
    /** 完全不透明 */
    OPAQUE(1.0f, R.string.control_editor_edit_dialog_open_preview) {
        override fun nextByUser(): EditWidgetDialogState = SEMI_TRANSPARENT_USER
    },
    /** 半透明 */
    SEMI_TRANSPARENT(0.3f, R.string.control_editor_edit_dialog_close_preview) {
        override fun nextByUser(): EditWidgetDialogState = OPAQUE
    },
    /** 半透明（用户主动选择） */
    SEMI_TRANSPARENT_USER(0.3f, R.string.control_editor_edit_dialog_close_preview){
        override fun nextByUser(): EditWidgetDialogState = OPAQUE
    };

    abstract fun nextByUser(): EditWidgetDialogState
}

/**
 * 控件编辑对话框
 *
 * **仍然不是 Dialog**：这一块一直是整屏 `AnimatedVisibility` 覆盖层（外面那句"不再
 * 真正使用 Dialog"说的是它自己的历史包袱——真开一个 Dialog 窗口确实有性能问题），
 * 所以它也不能换成 `OxideDialogShell`，那会凭空多出一层自己的窗口。改的只是外面
 * 那一层壳：Material 的 `Surface` 卡片换成 Oxide 面板同一套不透明底色、圆角与
 * 1px 描边，`Button`/`FilledTonalButton` 换成 `OxideButton`，`NavigationRailItem`
 * 换成一条 Oxide 的标签列。
 *
 * 行为与字符串一字未改：
 *
 * - 半透明预览的那三档（`OPAQUE` / `SEMI_TRANSPARENT` / `SEMI_TRANSPARENT_USER`）
 *   以及"用户主动切成半透明之后预览键不再切回去"的那两条早退，原样保留；
 * - 底部仍然是删除、复制、关闭三枚加一枚预览键，顺序与语气不变；
 * - 标签列仍然是 `categories` 里那几项，仍按 `backStack` 里的当前项标选中；
 * - 导航仍然是 Navigation3 的 `backStack.clearWith(key)`，`onBack` 依旧被忽略。
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
    val tween = rememberSwapTween()

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = visible,
        enter = fadeIn(animationSpec = tween),
        exit = fadeOut(animationSpec = tween)
    ) {
        val backStack = rememberTitledNavBackStack(EditWidgetCategory.Info)
        var dialogTransparent by remember { mutableStateOf(EditWidgetDialogState.OPAQUE) }

        val alpha by animateFloatAsState(
            dialogTransparent.alpha
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            //防止底下的控件被点击
            if (visible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismissRequest
                        )
                )
            }

            if (data != null) {
                val categories = remember(data) {
                    when (data.data) {
                        is ObservableNormalData -> editWidgetCategories
                        is ObservableJoystickData -> editJoystickCategories
                        else -> editWidgetCategories.filterNot { it.key == EditWidgetCategory.ClickEvent }
                    }
                }

                // 这一块过去是 Material 的 Surface 卡片（cardColor + extraLarge 圆角
                // + shadowElevation）。它不是 Dialog 窗口——下面那句"不再真正使用
                // Dialog"是有原因的——所以换成 Oxide 面板时**不能**用
                // OxideDialogShell（那会多出一层自己的窗口），而是把同一套不透明
                // 底色、圆角与 1px 描边直接画在这一层上。
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight()
                        .padding(all = 16.dp)
                        .clip(Oxide.RadiusDrawer)
                        .editorPanelBackground()
                        .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusDrawer),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        EditWidgetTabLayout(
                            modifier = Modifier.fillMaxHeight(),
                            items = categories,
                            currentKey = backStack.lastOrNull(),
                            navigateTo = { key ->
                                backStack.clearWith(key)
                            }
                        )

                        EditWidgetNavigation(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            backStack = backStack,
                            data = data.data,
                            styles = styles,
                            joystickStyles = joystickStyles,
                            switchControlLayers = switchControlLayers,
                            sendText = sendText,
                            openStyleList = openStyleList,
                            openJoystickStyleList = openJoystickStyleList,
                            onEditWidgetText = onEditWidgetText,
                            onPreviewRequested = {
                                if (dialogTransparent == EditWidgetDialogState.SEMI_TRANSPARENT_USER) return@EditWidgetNavigation
                                dialogTransparent = EditWidgetDialogState.SEMI_TRANSPARENT
                            },
                            onDismissRequested = {
                                if (dialogTransparent == EditWidgetDialogState.SEMI_TRANSPARENT_USER) return@EditWidgetNavigation
                                dialogTransparent = EditWidgetDialogState.OPAQUE
                            }
                        )
                    }
                    //底部操作栏
                    Row(
                        modifier = Modifier
                            .padding(all = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dialogTransparent != EditWidgetDialogState.SEMI_TRANSPARENT) {
                            OxideButton(
                                text = stringResource(dialogTransparent.buttonText),
                                onClick = {
                                    dialogTransparent = dialogTransparent.nextByUser()
                                },
                                tone = OxideButtonTone.Secondary,
                            )
                            Spacer(Modifier.width(16.dp))
                        } else {
                            //占位用，防止右侧按钮向左靠齐
                            Spacer(Modifier)
                        }

                        val scrollState = rememberScrollState()
                        LaunchedEffect(Unit) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                        Row(
                            modifier = Modifier
                                .fadeEdge(
                                    state = scrollState,
                                    direction = EdgeDirection.Horizontal
                                )
                                .horizontalScroll(state = scrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OxideButton(
                                text = stringResource(R.string.generic_delete),
                                onClick = {
                                    onDelete(data.data, data.layer)
                                },
                                tone = OxideButtonTone.Secondary,
                            )

                            OxideButton(
                                text = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                                onClick = {
                                    onClone(data.data, data.layer)
                                },
                                tone = OxideButtonTone.Secondary,
                            )

                            OxideButton(
                                text = stringResource(R.string.generic_close),
                                onClick = onDismissRequest,
                                tone = OxideButtonTone.Primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditWidgetTabLayout(
    modifier: Modifier = Modifier,
    items: List<CategoryItem>,
    currentKey: TitledNavKey?,
    navigateTo: (TitledNavKey) -> Unit
) {
    val metrics = editorMetrics()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        items.forEach { item ->
            val selected = currentKey == item.key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.rowHeight.coerceAtLeast(26.dp))
                    .clip(Oxide.RadiusControl)
                    .background(if (selected) Oxide.BgTabActive else Color.Transparent)
                    .border(
                        BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                        Oxide.RadiusControl,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { navigateTo(item.key) },
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides if (selected) Oxide.Fg else Oxide.FgMuted) {
                    item.icon()
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(item.textRes),
                    color = if (selected) Oxide.Fg else Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditWidgetNavigation(
    modifier: Modifier = Modifier,
    backStack: NavBackStack<TitledNavKey>,
    data: ObservableWidget,
    styles: List<ObservableButtonStyle>,
    joystickStyles: List<ObservableJoystickStyle>,
    onEditWidgetText: (ObservableTranslatableString) -> Unit,
    switchControlLayers: (ObservableClickEventsProvider, ClickEvent.Type) -> Unit,
    sendText: (ObservableClickEventsProvider) -> Unit,
    openStyleList: () -> Unit,
    openJoystickStyleList: () -> Unit,
    onPreviewRequested: () -> Unit,
    onDismissRequested: () -> Unit
) {
    val currentKey = backStack.lastOrNull()

    if (backStack.isNotEmpty()) {
        NavDisplay(
            modifier = modifier,
            backStack = backStack,
            onBack = { /* 忽略 */ },
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<EditWidgetCategory.Info> { key ->
                    EditWidgetInfo(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data,
                        onPreviewRequested = onPreviewRequested,
                        onDismissRequested = onDismissRequested
                    )
                }
                entry<EditWidgetCategory.TextStyle> { key ->
                    EditTextStyle(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data,
                        onEditWidgetText = onEditWidgetText
                    )
                }
                entry<EditWidgetCategory.ClickEvent> { key ->
                    EditWidgetClickEvent(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data as ObservableNormalData,
                        switchControlLayers = switchControlLayers,
                        sendText = sendText
                    )
                }
                entry<EditWidgetCategory.Style> { key ->
                    EditWidgetStyle(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data,
                        styles = styles,
                        openStyleList = openStyleList
                    )
                }
                entry<EditWidgetCategory.JoystickConfig> { key ->
                    EditJoystickConfig(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data as ObservableJoystickData
                    )
                }
                entry<EditWidgetCategory.DirectionEvents> { key ->
                    EditJoystickEvents(
                        data = data as ObservableJoystickData,
                        switchControlLayers = switchControlLayers,
                        sendText = sendText,
                    )
                }
                entry<EditWidgetCategory.JoystickStyle> { key ->
                    EditJoystickStyle(
                        screenKey = key,
                        currentKey = currentKey,
                        data = data as ObservableJoystickData,
                        joystickStyles = joystickStyles,
                        openJoystickStyleList = openJoystickStyleList,
                    )
                }
            }
        )
    }
}