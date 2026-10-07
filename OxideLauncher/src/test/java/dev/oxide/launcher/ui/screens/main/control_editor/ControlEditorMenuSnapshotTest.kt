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

package dev.oxide.launcher.ui.screens.main.control_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.layercontroller.ControlEditorLayer
import dev.oxide.layercontroller.data.CenterPosition
import dev.oxide.layercontroller.data.HideLayerWhen
import dev.oxide.layercontroller.data.NormalData
import dev.oxide.layercontroller.data.VisibilityType
import dev.oxide.layercontroller.data.createAdaptiveButtonSize
import dev.oxide.layercontroller.data.lang.createTranslatable
import dev.oxide.layercontroller.layout.ControlLayer
import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableControlLayout
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.layercontroller.utils.snap.SnapMode
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.paparazziFor
import dev.oxide.launcher.ui.theme.Oxide
import org.junit.Rule
import org.junit.Test

/**
 * 新菜单的画面钉死测试
 *
 * 菜单是从头重写过的，因此钉的是重写之后的样子：
 *
 * - [ControlEditorMenu_Dock]：面板根部就是面板本身，不再包整窗盒子与遮罩。
 *   画布上那两块控件是故意摆在面板底下的——底色不透明这件事仍然由这一张钉住。
 * - [ControlEditorMenu_AddPicker]：新建选择器，能建与被拦住各一张。
 *   被拦住那一张必须把原因写出来，而不是悄悄没反应。
 * - [ControlEditorMenu_LayerActions]：某一层的更多操作（改名、复制、显隐、删除）。
 *
 * 数据与摆法沿用 `ControlEditorSnapshotTest` 同一条通道，
 * 用的全是生产控件，没有另开新 API。
 */
class ControlEditorMenuSnapshotTest {

    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    /** 面板开着：层行、新建行、底栏三枚都在首屏 */
    @Test
    fun ControlEditorMenu_Dock() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditorMenu_Dock", device) {
            EditorDockOpenScreen(device.screenWidth, device.screenHeight)
        }
    }

    /** 同一屏在 640x360 那一档：新建行与底栏仍然点得下 */
    @Test
    fun ControlEditorMenu_Dock_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("ControlEditorMenu_Dock_Compact", device) {
            EditorDockOpenScreen(device.screenWidth, device.screenHeight)
        }
    }

    /** 新建选择器：三个选项都能点 */
    @Test
    fun ControlEditorMenu_AddPicker() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditorMenu_AddPicker", device) {
            EditorShell(device) {
                EditorAddPickerSheet(
                    blocker = EditorAddBlocker.None,
                    onDismiss = {},
                    onAdd = {},
                )
            }
        }
    }

    /** 新建选择器被拦住：原因写在最上面，三个选项一起灰掉 */
    @Test
    fun ControlEditorMenu_AddPicker_Blocked() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditorMenu_AddPicker_Blocked", device) {
            EditorShell(device) {
                EditorAddPickerSheet(
                    blocker = EditorAddBlocker.NoSelectedLayer,
                    onDismiss = {},
                    onAdd = {},
                )
            }
        }
    }

    /** 某一层的更多操作：改名、复制、显隐、属性、删除各一行 */
    @Test
    fun ControlEditorMenu_LayerActions() {
        val device = OxidePaparazzi.STANDARD
        val layer = menuFixtureLayer(device.screenHeight)
        paparazzi.shot("ControlEditorMenu_LayerActions", device) {
            EditorShell(device) {
                EditorLayerActionsSheet(
                    layer = layer,
                    isPreviewMode = false,
                    onDismiss = {},
                    onAttributes = {},
                    onToggleVisibility = {},
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Composition：与 ControlEditorSnapshotTest 同一条通道
    // -----------------------------------------------------------------------

    @Composable
    private fun EditorDockOpenScreen(deviceWidthDp: Int, deviceHeightDp: Int) {
        val metrics = controlEditorMetricsFor(
            widthDp = deviceWidthDp,
            heightDp = deviceHeightDp,
            guiScalePercent = 100,
        )
        val fixture = menuFixture(deviceHeightDp)
        val layout = fixture.layout
        val layers = fixture.layers
        val selectedLayer = fixture.selectedLayer
        val widgetsInLayer = fixture.widgetsInLayer

        ProvideEditorMetrics(metrics) {
            EditorBackdrop {
                ControlEditorLayer(
                    observedLayout = layout,
                    selectedWidget = null,
                    onButtonTap = { _, _ -> },
                    onBackgroundClick = {},
                    floatingButtons = {},
                    enableSnap = false,
                    snapInAllLayers = false,
                    snapMode = SnapMode.FullScreen,
                    isDark = true,
                    interactive = false,
                )
                EditorDock(
                    dockOpen = true,
                    layers = layers,
                    selectedLayer = selectedLayer,
                    selectedWidget = null,
                    widgetsInLayer = widgetsInLayer,
                    isPreviewMode = false,
                    screenWidthDp = deviceWidthDp.toFloat(),
                    screenHeightDp = deviceHeightDp.toFloat(),
                    closeScreen = {},
                    onLayerSelected = {},
                    onLayerReorder = { _, _ -> },
                    isLayerFocus = false,
                    onLayerFocusChanged = {},
                    onCreateLayer = {},
                    onLayerAttributes = {},
                    onToggleLayerVisibility = {},
                    onWidgetSelected = { _, _ -> },
                    onWidgetOpened = { _, _ -> },
                    onAddControl = {},
                    onOpenStyleList = {},
                    onOpenJoystickStyleList = {},
                    onPreviewChanged = {},
                    previewScenario = PreviewScenario.InMenu,
                    onPreviewScenarioChanged = {},
                    previewHideLayerWhen = HideLayerWhen.None,
                    onPreviewHideLayerChanged = {},
                    onSave = {},
                    saveAndExit = {},
                    onExit = {},
                )
            }
        }
    }

    @Composable
    private fun EditorShell(
        device: DeviceConfig,
        content: @Composable () -> Unit,
    ) {
        ProvideEditorMetrics(
            controlEditorMetricsFor(
                widthDp = device.screenWidth,
                heightDp = device.screenHeight,
                guiScalePercent = 100,
            )
        ) {
            content()
        }
    }

    @Composable
    private fun EditorBackdrop(content: @Composable BoxScope.() -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(Oxide.Bg)) {
            content()
        }
    }

    // -----------------------------------------------------------------------
    // Data：两个控制层，上层两个按键，uuid 全部写死
    // -----------------------------------------------------------------------

    private data class MenuFixture(
        val layout: ObservableControlLayout,
        val layers: List<ObservableControlLayer>,
        val selectedLayer: ObservableControlLayer,
        val widgetsInLayer: List<ObservableWidget>,
    )

    private fun menuFixture(screenHeightDp: Int): MenuFixture {
        val layout = menuTestLayout(screenHeightDp)
        val layers = layout.layers.value
        val selectedLayer = layers.first()
        return MenuFixture(
            layout = layout,
            layers = layers,
            selectedLayer = selectedLayer,
            widgetsInLayer = selectedLayer.allWidgets(),
        )
    }

    private fun menuFixtureLayer(screenHeightDp: Int): ObservableControlLayer =
        menuTestLayout(screenHeightDp).layers.value.first()

    private fun menuTestLayout(screenHeightDp: Int): ObservableControlLayout {
        val buttonSize = createAdaptiveButtonSize(
            referenceLength = screenHeightDp,
            density = 1f,
        )
        val gui = NormalData(
            text = createTranslatable(default = "GUI"),
            uuid = "gui0000000001",
            position = CenterPosition,
            buttonSize = buttonSize,
            visibilityType = VisibilityType.ALWAYS,
            isSwipple = false,
            isPenetrable = false,
            isToggleable = false,
        )
        val esc = NormalData(
            text = createTranslatable(default = "Esc"),
            uuid = "esc00000000001",
            position = CenterPosition,
            buttonSize = buttonSize,
            visibilityType = VisibilityType.ALWAYS,
            isSwipple = false,
            isPenetrable = false,
            isToggleable = false,
        )
        val layers = listOf(
            ControlLayer(
                name = "overlays",
                uuid = "layer000000001",
                hide = false,
                visibilityType = VisibilityType.ALWAYS,
                normalButtons = listOf(gui, esc),
            ),
            ControlLayer(
                name = "movement",
                uuid = "layer000000002",
                hide = false,
                visibilityType = VisibilityType.ALWAYS,
            ),
        )
        return ObservableControlLayout(EmptyControlLayout.copy(layers = layers))
    }

    private fun ObservableControlLayer.allWidgets(): List<ObservableWidget> =
        textBoxes.value + normalButtons.value + joystickButtons.value
}
