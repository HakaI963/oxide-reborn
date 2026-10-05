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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.paparazziFor
import dev.oxide.launcher.ui.theme.Oxide
import org.junit.Rule
import org.junit.Test

/**
 * 控制布局编辑器的画面钉死测试
 *
 * 这一族此前一张 golden 都没有，于是两块最该被钉住的毛病一路留到了设备上。
 *
 * 第一块是停靠面板"盖不住"画布。面板底色用的是 `Oxide.SurfaceBase`——深色下约 9%
 * 不透明，是给**卡片**用的 token。面板压着的恰恰是控制布局画布本身，于是画布上的
 * 控件从面板的每一行底下透出来，整个面板读起来像"控件浮在面板上面"，而每一行看
 * 上去都点不动（`controll-menu.jpg`）。改用 `Oxide.BgElevated` 之后底下什么都看不
 * 见，层次关系、圆角与描边一概没动。
 *
 * 第二块是悬浮球停在左上角、且拖不动。球的落位与夹取都算在一层套在**球自身**上的
 * `BoxWithConstraints` 里，内部的 `maxWidth` 恒等于球的边长，于是 `maxX`/`maxY` 恒
 * 为 0：默认位置被夹成 `Offset.Zero`，拖动增量同样被夹成 0（`controll-menu-button.jpg`）。
 * 现在安全区由 [editorBallSafeBounds] 从**外层**尺寸算，扣掉 `WindowInsets.safeDrawing`。
 *
 * 三张 golden，多的不做：
 *
 * - [ControlEditor_DockOpen]：面板开着、画布只读。画布上那两块控件是**故意**摆在面板
 *   底下的——底色一改回半透明，它们立刻会从面板里透出来，这一张就是钉这个的。
 * - [ControlEditor_ControlSelected]：选中一个控件并把检视器摆到首屏。
 * - [ControlEditor_DockOpen_Compact]：640x360 那一档。它是支持的下限，也是面板与控件
 *   格子最先被挤坏的一档。
 *
 * 数据全是字面量：两个 [ControlLayer]、两个 [NormalData]，uuid 也是写死的字符串，
 * 不走 `createWidgetWithUUID`（里面是 `UUID.randomUUID()`）。见 `OxidePaparazzi` 第 2 条
 * ——golden 里没有随机数。
 */
class ControlEditorSnapshotTest {

    /**
     * 动画必须先落到终态再画帧。`InstantAnimationsRule` 把 `ValueAnimator` 的时长缩放
     * 设成 0，正是 Compose 在 Android 上读的那个 `MotionDurationScale`。见 `OxidePaparazzi`。
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    // -----------------------------------------------------------------------
    // Dock open
    // -----------------------------------------------------------------------

    /**
     * 停靠面板开着，压在画布上。
     *
     * [ControlEditorLayer] 的 `interactive = false` 就是生产上那一位
     * （`viewModel.editorMenu == MenuState.SHOW` 时传下来的），它让画布只画不收手势。
     * 这里仍然把它摆上画布：面板的不透明度正是"底下那些控件看不见"这件事。
     */
    @Test
    fun ControlEditor_DockOpen() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_DockOpen", device) {
            EditorDockOpenScreen(device.screenWidth, device.screenHeight)
        }
    }

    /**
     * 同一屏在 640x360 那一档。面板宽度按窗口算，控件格子因此退回一列，底栏那三个
     * 按钮也挤到一行——这一档是布局最容易坏的地方。
     */
    @Test
    fun ControlEditor_DockOpen_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("ControlEditor_DockOpen_Compact", device) {
            EditorDockOpenScreen(device.screenWidth, device.screenHeight)
        }
    }

    // -----------------------------------------------------------------------
    // One control selected
    // -----------------------------------------------------------------------

    /**
     * 选中一个控件，检视器摆到首屏。
     *
     * 检视器是这一族里唯一一块**生产上不在首屏**的内容：它在面板那条 `LazyColumn` 里
     * 排在控件网格之后，所以 `EditorDock` 自己渲染时它落在折叠线以下。这里改用同一块
     * 真的 [EditorDockFrame]（同一份不透明底色、同一套圆角与描边、同样由
     * [ProvideEditorMetrics] 提供尺寸）把真的 [EditorInspector] 摆到首屏，因此这一张
     * 仍然只由生产控件拼成，**排列**由测试决定而已。
     */
    @Test
    fun ControlEditor_ControlSelected() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlEditor_ControlSelected", device) {
            val metrics = controlEditorMetricsFor(
                widthDp = device.screenWidth,
                heightDp = device.screenHeight,
                guiScalePercent = 100,
            )
            // 取值放在组合之外：StateFlow 的 .value 不能在组合里读
            val fixture = editorFixture(device.screenHeight)
            val layout = fixture.layout
            val layer = fixture.selectedLayer
            val widget = fixture.widgetsInLayer.first()

            ProvideEditorMetrics(metrics) {
                EditorBackdrop {
                    ControlEditorLayer(
                        observedLayout = layout,
                        selectedWidget = widget,
                        onButtonTap = { _, _ -> },
                        onBackgroundClick = {},
                        floatingButtons = {},
                        enableSnap = false,
                        snapInAllLayers = false,
                        snapMode = SnapMode.FullScreen,
                        isDark = true,
                        // 画布可交互：检视器要看的是选中框与那对缩放手柄
                        interactive = true,
                    )
                    EditorDockFrame(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(metrics.dockWidth)
                            .padding(
                                start = metrics.dockMargin,
                                top = metrics.dockMargin,
                                bottom = metrics.dockMargin,
                            ),
                        header = {
                            EditorGroupLabel(
                                text = stringResource(R.string.oxide_ce_section_inspector),
                                modifier = Modifier.padding(horizontal = metrics.dockPadding),
                            )
                        },
                        footer = {
                            EditorFooterRow {
                                EditorFooterButton(
                                    text = stringResource(R.string.generic_save),
                                    onClick = {},
                                    primary = true,
                                )
                                EditorFooterButton(
                                    text = stringResource(R.string.control_editor_menu_save_and_exit),
                                    onClick = {},
                                )
                            }
                        },
                        body = {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(metrics.dockPadding),
                                verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
                            ) {
                                EditorGroupLabel(
                                    text = stringResource(R.string.oxide_ce_section_controls),
                                )
                                EditorControlCell(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(metrics.controlCellHeight),
                                    name = widget.editorCellName(),
                                    summary = widget.editorCellSummary(),
                                    kindGlyph = widget.editorKindGlyph(),
                                    selected = true,
                                    onSelect = {},
                                    onOpen = {},
                                )
                                EditorInspector(
                                    widget = widget,
                                    isPreviewMode = false,
                                    screenWidthDp = device.screenWidth.toFloat(),
                                    screenHeightDp = device.screenHeight.toFloat(),
                                    onOpenAdvanced = {},
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Composition
    // -----------------------------------------------------------------------

    /**
     * 面板开着的那一屏。用的全是生产控件：[ControlEditorLayer] 是画布本身，
     * [EditorDock] 是整个面板，[EditorBall] 是那颗悬浮球。
     */
    @Composable
    private fun EditorDockOpenScreen(deviceWidthDp: Int, deviceHeightDp: Int) {
        val metrics = controlEditorMetricsFor(
            widthDp = deviceWidthDp,
            heightDp = deviceHeightDp,
            guiScalePercent = 100,
        )
        // 同上：StateFlow 的 .value 不能在组合里读
        val fixture = editorFixture(deviceHeightDp)
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
                    // 面板开着：与生产同一位，画布只画不收手势
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
                EditorBall(
                    // null = 还没落位，于是走 editorBallDefaultPosition：
                    // 横向居中、贴顶。这正是要钉的那一位
                    position = null,
                    onPositionChanged = {},
                    opened = true,
                    onClick = {},
                    containerWidth = deviceWidthDp.dp,
                    containerHeight = deviceHeightDp.dp,
                )
            }
        }
    }

    /**
     * 画布底色。
     *
     * 与 `ControlEditorActivity` 里那层 `Surface(color = backgroundColor())` 同一个角色：
     * 编辑器压在一块底上。面板的不透明度不依赖底下画了什么，但底下必须**有**东西——
     * 否则"半透明的底色"与"不透明的底色"在 golden 上是同一张图，
     * [ControlEditor_DockOpen] 也就白拍了。
     *
     * @param content 画布上的那一层。收的是 [BoxScope]，因此里面可以用 `Modifier.align`
     *   把面板停到起始边上——与生产里那个包着整块画布的 `Box` 是同一个角色
     */
    @Composable
    private fun EditorBackdrop(content: @Composable BoxScope.() -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(Oxide.Bg)) {
            content()
        }
    }

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    /**
     * 两个控制层，上层两个 [NormalData]。
     *
     * uuid 是字面量而不是 `createWidgetWithUUID`：它里面是 `UUID.randomUUID()`，
     * 而 golden 必须逐位可复现。
     */
    /** 一次构造里要用的三样东西；取自 StateFlow，因此只能在组合之外读 */
    private data class EditorFixture(
        val layout: ObservableControlLayout,
        val layers: List<ObservableControlLayer>,
        val selectedLayer: ObservableControlLayer,
        val widgetsInLayer: List<ObservableWidget>,
    )

    /**
     * 建好夹具并把它要用的那几样一次取出来
     *
     * `ObservableControlLayout.layers` 是 StateFlow，在组合里读它的 `.value` 会被
     * lint 判为 `StateFlowValueCalledInComposition`：那样写等于把一次快照当响应式
     * 状态用。夹具本来就不该在组合过程中变化，所以在纯函数里取一次即可。
     */
    private fun editorFixture(screenHeightDp: Int): EditorFixture {
        val layout = editorTestLayout(screenHeightDp)
        val layers = layout.layers.value
        val selectedLayer = layers.first()
        return EditorFixture(
            layout = layout,
            layers = layers,
            selectedLayer = selectedLayer,
            widgetsInLayer = selectedLayer.allWidgets(),
        )
    }

    private fun editorTestLayout(screenHeightDp: Int): ObservableControlLayout {
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

    /** 网格里的次序与生产一致：文本框、普通按键、摇杆 */
    private fun ObservableControlLayer.allWidgets(): List<ObservableWidget> =
        textBoxes.value + normalButtons.value + joystickButtons.value
}
