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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.bridge.CursorShape
import dev.oxide.launcher.game.control.ControlManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.GamepadInputMode
import dev.oxide.launcher.setting.enums.GestureActionType
import dev.oxide.launcher.setting.enums.MouseControlMode
import dev.oxide.launcher.setting.unit.ParcelableSettingUnit
import dev.oxide.launcher.setting.unit.floatRange
import dev.oxide.launcher.ui.control.GamepadBindingKeyboard
import dev.oxide.launcher.ui.control.gamepad.GamepadMap
import dev.oxide.launcher.ui.control.gamepad.JoystickMode
import dev.oxide.launcher.ui.control.gamepad.getNameByGamepadEvent
import dev.oxide.launcher.ui.control.gyroscope.isGyroscopeAvailable
import dev.oxide.launcher.ui.control.mouse.CursorHotspot
import dev.oxide.launcher.ui.control.mouse.MouseHotspotEditorDialog
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.viewmodel.GAMEPAD_CONFIG_NAME_LENGTH
import dev.oxide.launcher.viewmodel.GamepadViewModel

private const val LOG_TAG = "OxideControlsPanel"

// ---------------------------------------------------------------------------
// 哪些行在什么前提下出现
//
// "这一行现在能不能用"是这一页最容易出错的地方：手势没开却让人去选长按动作、
// 陀螺仪关着却摆着灵敏度、SDL 直通模式下还在调映射死区——这些控件要么点了没反应，
// 要么让人以为设置坏了。旧设置页的做法是把它们留在那儿、只置灰。
//
// 这里反过来做：前提不满足就**根本不渲染**，并且把判断收成一个纯函数，
// 于是"关掉手势之后哪些行会消失"可以脱离 Compose 直接单测（见
// OxideControlsPanelLogicTest）。界面每一行的显示都查这张表，
// 纯函数与实际渲染因此不会各说各话。
// ---------------------------------------------------------------------------

/** 控制分类里的分组，仅用于给行归类与错峰进场排序 */
internal enum class OxideControlSection {
    Mouse, Hotspots, Gestures, Gyroscope, Gamepad, Bindings, Layouts,
}

/** 控制分类里的每一行 */
internal enum class OxideControlRow(val section: OxideControlSection) {
    MouseMode(OxideControlSection.Mouse),
    PhysicalMouseMode(OxideControlSection.Mouse),
    /** 仅点击模式有意义 */
    HideMouse(OxideControlSection.Mouse),
    /** 仅滑动模式有意义 */
    EnableMouseClick(OxideControlSection.Mouse),
    MouseSize(OxideControlSection.Mouse),
    CursorSensitivity(OxideControlSection.Mouse),
    MouseCaptureSensitivity(OxideControlSection.Mouse),
    MouseLongPressDelay(OxideControlSection.Mouse),

    GestureControl(OxideControlSection.Gestures),
    GestureTapAction(OxideControlSection.Gestures),
    GestureLongPressAction(OxideControlSection.Gestures),
    GestureLongPressDelay(OxideControlSection.Gestures),

    GyroscopeControl(OxideControlSection.Gyroscope),
    GyroscopeSensitivity(OxideControlSection.Gyroscope),
    GyroscopeSampleRate(OxideControlSection.Gyroscope),
    GyroscopeSmoothing(OxideControlSection.Gyroscope),
    /** 平滑本身还要开着，否则这个窗口没有任何作用 */
    GyroscopeSmoothingWindow(OxideControlSection.Gyroscope),
    GyroscopeInvertX(OxideControlSection.Gyroscope),
    GyroscopeInvertY(OxideControlSection.Gyroscope),

    GamepadControl(OxideControlSection.Gamepad),
    GamepadInputMode(OxideControlSection.Gamepad),
    GamepadMappingConfig(OxideControlSection.Gamepad),
    GamepadDeadZone(OxideControlSection.Gamepad),
    GamepadCursorSensitivity(OxideControlSection.Gamepad),
    GamepadCameraSensitivity(OxideControlSection.Gamepad),
    GamepadJoystickMode(OxideControlSection.Gamepad),

    GamepadBindings(OxideControlSection.Bindings),
    ControlLayouts(OxideControlSection.Layouts),
}

/**
 * 决定哪些行会出现的那几项前提
 *
 * 全部由 `AllSettings.*.state` 与设备能力填充，[visibleControlRows] 是纯函数，
 * 因此这些规则可以脱离 Compose 与设置存储直接单测。
 */
internal data class OxideControlPrerequisites(
    /** 虚拟鼠标当前是"点击"模式 */
    val mouseClickMode: Boolean,
    /** 虚拟鼠标当前是"滑动"模式 */
    val mouseSlideMode: Boolean,
    val gestureControl: Boolean,
    /** 设备上真的有陀螺仪传感器 */
    val gyroscopeAvailable: Boolean,
    val gyroscopeControl: Boolean,
    val gyroscopeSmoothing: Boolean,
    val gamepadControl: Boolean,
    /** 手柄走的是映射模式；SDL 直通由游戏自己去认手柄，没有映射可调 */
    val gamepadMapped: Boolean,
) {
    /** 映射相关的几行（旧设置页里的 remapEnabled） */
    val gamepadRemap: Boolean get() = gamepadControl && gamepadMapped
}

/** 这一行在给定前提下是否出现。纯函数，不读任何设置 */
internal fun OxideControlRow.isVisible(prerequisites: OxideControlPrerequisites): Boolean =
    when (this) {
        OxideControlRow.HideMouse -> prerequisites.mouseClickMode
        OxideControlRow.EnableMouseClick -> prerequisites.mouseSlideMode

        OxideControlRow.GestureTapAction,
        OxideControlRow.GestureLongPressAction,
        OxideControlRow.GestureLongPressDelay,
        -> prerequisites.gestureControl

        OxideControlRow.GyroscopeSensitivity,
        OxideControlRow.GyroscopeSampleRate,
        OxideControlRow.GyroscopeSmoothing,
        OxideControlRow.GyroscopeInvertX,
        OxideControlRow.GyroscopeInvertY,
        -> prerequisites.gyroscopeAvailable && prerequisites.gyroscopeControl

        OxideControlRow.GyroscopeSmoothingWindow ->
            prerequisites.gyroscopeAvailable && prerequisites.gyroscopeControl &&
                prerequisites.gyroscopeSmoothing

        OxideControlRow.GamepadInputMode -> prerequisites.gamepadControl
        OxideControlRow.GamepadMappingConfig,
        OxideControlRow.GamepadDeadZone,
        OxideControlRow.GamepadCursorSensitivity,
        OxideControlRow.GamepadCameraSensitivity,
        OxideControlRow.GamepadJoystickMode,
        -> prerequisites.gamepadRemap

        OxideControlRow.GamepadBindings -> prerequisites.gamepadRemap

        OxideControlRow.MouseMode,
        OxideControlRow.PhysicalMouseMode,
        OxideControlRow.MouseSize,
        OxideControlRow.CursorSensitivity,
        OxideControlRow.MouseCaptureSensitivity,
        OxideControlRow.MouseLongPressDelay,
        OxideControlRow.GestureControl,
        OxideControlRow.GyroscopeControl,
        OxideControlRow.GamepadControl,
        OxideControlRow.ControlLayouts,
        -> true
    }

/** 给定前提下出现的全部行，顺序就是枚举声明顺序，也就是界面从上到下的顺序 */
internal fun visibleControlRows(prerequisites: OxideControlPrerequisites): List<OxideControlRow> =
    OxideControlRow.entries.filter { it.isVisible(prerequisites) }

// ---------------------------------------------------------------------------
// 控制分类面板
//
// 面板本身是设置页右侧那一块，与其余分类共用分组、行与错峰进场；
// 只有"手柄绑定"另开一个抽屉，因为绑定表有十几行，塞在面板里会把设置项挤到很下面。
// ---------------------------------------------------------------------------

/** 一个虚拟鼠标指针热点：标题、指针形状，以及它写的是哪一个设置单元 */
private data class OxideHotspotRow(
    val titleRes: Int,
    val shape: CursorShape,
    val unit: ParcelableSettingUnit<CursorHotspot>,
)

/**
 * 控制分类：鼠标、手势、陀螺仪与手柄
 *
 * 与 Java / 渲染器两类不同，控制这一类没有抽屉，全部内容就在面板里展开——
 * 这样从分类栏切过来就能直接看到设置，不必再点一次"打开"。
 */
@Composable
internal fun OxideControlsPanel(
    metrics: OxideMetrics,
    bridge: OxideLauncherBridge,
) {
    val context = LocalContext.current
    val mouseMode = AllSettings.mouseControlMode.state
    // 陀螺仪是设备能力，不是设置：一次问完就够，读硬件传感器列表不该跟着重组跑
    val gyroscopeAvailable = remember(context) { isGyroscopeAvailable(context) }
    val prerequisites = OxideControlPrerequisites(
        mouseClickMode = mouseMode == MouseControlMode.CLICK,
        mouseSlideMode = mouseMode == MouseControlMode.SLIDE,
        gestureControl = AllSettings.gestureControl.state,
        gyroscopeAvailable = gyroscopeAvailable,
        gyroscopeControl = AllSettings.gyroscopeControl.state,
        gyroscopeSmoothing = AllSettings.gyroscopeSmoothing.state,
        gamepadControl = AllSettings.gamepadControl.state,
        gamepadMapped = AllSettings.gamepadInputMode.state == GamepadInputMode.Mapped,
    )
    val rows = remember(prerequisites) { visibleControlRows(prerequisites).toSet() }

    var editingHotspot by remember { mutableStateOf<OxideHotspotRow?>(null) }
    var bindingsOpen by remember { mutableStateOf(false) }

    OxideMouseGroup(metrics = metrics, rows = rows)
    OxideHotspotsGroup(metrics = metrics, onEdit = { editingHotspot = it })
    OxideGesturesGroup(metrics = metrics, rows = rows)
    OxideGyroscopeGroup(metrics = metrics, rows = rows, available = gyroscopeAvailable)
    OxideGamepadGroup(metrics = metrics, rows = rows)
    OxideGamepadBindingsGroup(metrics = metrics, rows = rows, onOpen = { bindingsOpen = true })
    OxideControlLayoutsGroup(metrics = metrics, bridge = bridge)

    // 热点编辑器是启动器本来就有的对话框，坐标读写真实设置单元
    val hotspot = editingHotspot
    if (hotspot != null) {
        MouseHotspotEditorDialog(
            hotspot = hotspot.unit,
            cursorShape = hotspot.shape,
            onClose = { editingHotspot = null },
        )
    }

    if (bindingsOpen) {
        OxideGamepadBindingsDrawer(metrics = metrics, onDismiss = { bindingsOpen = false })
    }
}

/** 带错峰进场的一个分组，与设置页其余分组用的是同一个底板 */
@Composable
private fun ControlGroup(
    index: Int,
    title: String,
    metrics: OxideMetrics,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OxideReveal(visible = true, index = index) {
        OxideSettingsGroup(title = title, metrics = metrics, trailing = trailing, content = content)
    }
}

// ---------------------------------------------------------------------------
// 鼠标
// ---------------------------------------------------------------------------

@Composable
private fun OxideMouseGroup(metrics: OxideMetrics, rows: Set<OxideControlRow>) {
    ControlGroup(index = 0, title = stringResource(R.string.oxide_set_section_mouse), metrics = metrics) {
        OxideEnumRow(
            label = stringResource(R.string.settings_control_mouse_control_mode_title),
            hint = stringResource(R.string.settings_control_mouse_control_mode_summary),
            metrics = metrics,
            entries = MouseControlMode.entries,
            selected = AllSettings.mouseControlMode.state,
            nameOf = { oxideMouseControlModeName(it) },
            onSelect = { AllSettings.mouseControlMode.save(it) },
        )
        OxideToggleRow(
            label = stringResource(R.string.settings_control_mouse_physical_mouse_mode_title),
            hint = stringResource(R.string.settings_control_mouse_physical_mouse_mode_summary),
            checked = AllSettings.physicalMouseMode.state,
            onCheckedChange = { AllSettings.physicalMouseMode.save(it) },
        )
        if (OxideControlRow.HideMouse in rows) {
            OxideToggleRow(
                label = stringResource(R.string.settings_control_mouse_hide_title),
                hint = stringResource(R.string.settings_control_mouse_hide_summary),
                checked = AllSettings.hideMouse.state,
                onCheckedChange = { AllSettings.hideMouse.save(it) },
            )
        }
        if (OxideControlRow.EnableMouseClick in rows) {
            OxideToggleRow(
                label = stringResource(R.string.settings_control_mouse_enable_click_title),
                hint = stringResource(R.string.settings_control_mouse_enable_click_summary),
                checked = AllSettings.enableMouseClick.state,
                onCheckedChange = { AllSettings.enableMouseClick.save(it) },
            )
        }
        OxideIntRow(
            label = stringResource(R.string.settings_control_mouse_size_title),
            metrics = metrics,
            value = AllSettings.mouseSize.state,
            range = AllSettings.mouseSize.floatRange.toIntRange(),
            suffix = " dp",
            onValueChange = { AllSettings.mouseSize.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_control_mouse_sensitivity_title),
            hint = stringResource(R.string.settings_control_mouse_sensitivity_summary),
            metrics = metrics,
            value = AllSettings.cursorSensitivity.state,
            range = AllSettings.cursorSensitivity.floatRange.toIntRange(),
            step = 5,
            suffix = "%",
            onValueChange = { AllSettings.cursorSensitivity.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_control_mouse_capture_sensitivity_title),
            hint = stringResource(R.string.settings_control_mouse_capture_sensitivity_summary),
            metrics = metrics,
            value = AllSettings.mouseCaptureSensitivity.state,
            range = AllSettings.mouseCaptureSensitivity.floatRange.toIntRange(),
            step = 5,
            suffix = "%",
            onValueChange = { AllSettings.mouseCaptureSensitivity.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_control_mouse_long_press_delay_title),
            hint = stringResource(R.string.settings_control_mouse_long_press_delay_summary),
            metrics = metrics,
            value = AllSettings.mouseLongPressDelay.state,
            range = AllSettings.mouseLongPressDelay.floatRange.toIntRange(),
            step = 20,
            suffix = " ms",
            onValueChange = { AllSettings.mouseLongPressDelay.save(it) },
        )
    }
}

// ---------------------------------------------------------------------------
// 虚拟鼠标指针热点
// ---------------------------------------------------------------------------

/**
 * 八种指针形状各自的热点
 *
 * 坐标本身由启动器已有的 [MouseHotspotEditorDialog] 编辑——它是游戏里判定
 * "指针压到了控件的哪一块"用的那一套，写的是 `AllSettings.*MouseHotspot`。
 * 这里只负责把八种形状排出来，并显示当前坐标。
 */
@Composable
private fun OxideHotspotsGroup(
    metrics: OxideMetrics,
    onEdit: (OxideHotspotRow) -> Unit,
) {
    val hotspots = listOf(
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_arrow_title,
            CursorShape.Arrow,
            AllSettings.arrowMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_link_title,
            CursorShape.Hand,
            AllSettings.linkMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_ibeam_title,
            CursorShape.IBeam,
            AllSettings.iBeamMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_crosshair_title,
            CursorShape.CrossHair,
            AllSettings.crossHairMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_resize_ns_title,
            CursorShape.ResizeNS,
            AllSettings.resizeNSMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_resize_ew_title,
            CursorShape.ResizeEW,
            AllSettings.resizeEWMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_resize_all_title,
            CursorShape.ResizeAll,
            AllSettings.resizeAllMouseHotspot,
        ),
        OxideHotspotRow(
            R.string.settings_control_mouse_pointer_not_allowed_title,
            CursorShape.NotAllowed,
            AllSettings.notAllowedMouseHotspot,
        ),
    )

    ControlGroup(
        index = 1,
        title = stringResource(R.string.oxide_set_section_mouse_hotspots),
        metrics = metrics,
    ) {
        hotspots.forEach { hotspot ->
            val current = hotspot.unit.state
            OxideActionRow(
                label = stringResource(hotspot.titleRes),
                hint = stringResource(R.string.oxide_set_hotspot_row_detail),
                value = stringResource(
                    R.string.oxide_set_hotspot_value,
                    current.xPercent,
                    current.yPercent,
                ),
                onClick = { onEdit(hotspot) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 手势
// ---------------------------------------------------------------------------

@Composable
private fun OxideGesturesGroup(metrics: OxideMetrics, rows: Set<OxideControlRow>) {
    ControlGroup(index = 2, title = stringResource(R.string.oxide_set_section_gestures), metrics = metrics) {
        OxideToggleRow(
            label = stringResource(R.string.settings_control_gesture_control_title),
            hint = stringResource(R.string.settings_control_gesture_control_summary),
            checked = AllSettings.gestureControl.state,
            onCheckedChange = { AllSettings.gestureControl.save(it) },
        )
        if (OxideControlRow.GestureTapAction in rows) {
            OxideEnumRow(
                label = stringResource(R.string.settings_control_gesture_tap_action_title),
                hint = stringResource(R.string.settings_control_gesture_tap_action_summary),
                metrics = metrics,
                entries = GestureActionType.entries,
                selected = AllSettings.gestureTapMouseAction.state,
                nameOf = { stringResource(it.nameRes) },
                onSelect = { AllSettings.gestureTapMouseAction.save(it) },
            )
        }
        if (OxideControlRow.GestureLongPressAction in rows) {
            OxideEnumRow(
                label = stringResource(R.string.settings_control_gesture_long_press_action_title),
                hint = stringResource(R.string.settings_control_gesture_long_press_action_summary),
                metrics = metrics,
                entries = GestureActionType.entries,
                selected = AllSettings.gestureLongPressMouseAction.state,
                nameOf = { stringResource(it.nameRes) },
                onSelect = { AllSettings.gestureLongPressMouseAction.save(it) },
            )
        }
        if (OxideControlRow.GestureLongPressDelay in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_control_gesture_long_press_delay_title),
                metrics = metrics,
                value = AllSettings.gestureLongPressDelay.state,
                range = AllSettings.gestureLongPressDelay.floatRange.toIntRange(),
                step = 20,
                suffix = " ms",
                onValueChange = { AllSettings.gestureLongPressDelay.save(it) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 陀螺仪
// ---------------------------------------------------------------------------

@Composable
private fun OxideGyroscopeGroup(
    metrics: OxideMetrics,
    rows: Set<OxideControlRow>,
    available: Boolean,
) {
    ControlGroup(
        index = 3,
        title = stringResource(R.string.oxide_set_section_gyroscope),
        metrics = metrics,
    ) {
        OxideToggleRow(
            label = stringResource(R.string.settings_control_gyroscope_title),
            // 没有传感器的设备上，开关摆在那儿只会让人以为装坏了
            hint = stringResource(
                if (available) {
                    R.string.settings_control_gyroscope_summary
                } else {
                    R.string.settings_control_gyroscope_unsupported
                }
            ),
            checked = AllSettings.gyroscopeControl.state,
            enabled = available,
            onCheckedChange = { AllSettings.gyroscopeControl.save(it) },
        )
        if (OxideControlRow.GyroscopeSensitivity in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_control_gyroscope_sensitivity_title),
                metrics = metrics,
                value = AllSettings.gyroscopeSensitivity.state,
                range = AllSettings.gyroscopeSensitivity.floatRange.toIntRange(),
                step = 5,
                suffix = "%",
                onValueChange = { AllSettings.gyroscopeSensitivity.save(it) },
            )
        }
        if (OxideControlRow.GyroscopeSampleRate in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_control_gyroscope_sample_rate_title),
                hint = stringResource(R.string.settings_control_gyroscope_sample_rate_summary),
                metrics = metrics,
                value = AllSettings.gyroscopeSampleRate.state,
                range = AllSettings.gyroscopeSampleRate.floatRange.toIntRange(),
                suffix = " ms",
                onValueChange = { AllSettings.gyroscopeSampleRate.save(it) },
            )
        }
        if (OxideControlRow.GyroscopeSmoothing in rows) {
            OxideToggleRow(
                label = stringResource(R.string.settings_control_gyroscope_smoothing_title),
                hint = stringResource(R.string.settings_control_gyroscope_smoothing_summary),
                checked = AllSettings.gyroscopeSmoothing.state,
                onCheckedChange = { AllSettings.gyroscopeSmoothing.save(it) },
            )
        }
        if (OxideControlRow.GyroscopeSmoothingWindow in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_control_gyroscope_smoothing_window_title),
                hint = stringResource(R.string.settings_control_gyroscope_smoothing_window_summary),
                metrics = metrics,
                value = AllSettings.gyroscopeSmoothingWindow.state,
                range = AllSettings.gyroscopeSmoothingWindow.floatRange.toIntRange(),
                onValueChange = { AllSettings.gyroscopeSmoothingWindow.save(it) },
            )
        }
        if (OxideControlRow.GyroscopeInvertX in rows) {
            OxideToggleRow(
                label = stringResource(R.string.settings_control_gyroscope_invert_x_title),
                hint = stringResource(R.string.settings_control_gyroscope_invert_x_summary),
                checked = AllSettings.gyroscopeInvertX.state,
                onCheckedChange = { AllSettings.gyroscopeInvertX.save(it) },
            )
        }
        if (OxideControlRow.GyroscopeInvertY in rows) {
            OxideToggleRow(
                label = stringResource(R.string.settings_control_gyroscope_invert_y_title),
                hint = stringResource(R.string.settings_control_gyroscope_invert_y_summary),
                checked = AllSettings.gyroscopeInvertY.state,
                onCheckedChange = { AllSettings.gyroscopeInvertY.save(it) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 手柄
// ---------------------------------------------------------------------------

@Composable
private fun OxideGamepadGroup(metrics: OxideMetrics, rows: Set<OxideControlRow>) {
    ControlGroup(index = 4, title = stringResource(R.string.oxide_set_section_gamepad), metrics = metrics) {
        OxideToggleRow(
            label = stringResource(R.string.oxide_set_gamepad_control),
            hint = stringResource(R.string.settings_gamepad_summary),
            checked = AllSettings.gamepadControl.state,
            onCheckedChange = { AllSettings.gamepadControl.save(it) },
        )
        if (OxideControlRow.GamepadInputMode in rows) {
            OxideEnumRow(
                label = stringResource(R.string.settings_gamepad_input_mode_title),
                metrics = metrics,
                entries = GamepadInputMode.entries,
                selected = AllSettings.gamepadInputMode.state,
                nameOf = { oxideGamepadInputModeName(it) },
                onSelect = { AllSettings.gamepadInputMode.save(it) },
            )
        }
        if (OxideControlRow.GamepadDeadZone in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_gamepad_deadzone_title),
                hint = stringResource(R.string.settings_gamepad_deadzone_summary),
                metrics = metrics,
                value = AllSettings.gamepadDeadZoneScale.state,
                range = AllSettings.gamepadDeadZoneScale.floatRange.toIntRange(),
                step = 5,
                suffix = "%",
                onValueChange = { AllSettings.gamepadDeadZoneScale.save(it) },
            )
        }
        if (OxideControlRow.GamepadCursorSensitivity in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_gamepad_cursor_sensitivity_title),
                hint = stringResource(R.string.settings_gamepad_cursor_sensitivity_summary),
                metrics = metrics,
                value = AllSettings.gamepadCursorSensitivity.state,
                range = AllSettings.gamepadCursorSensitivity.floatRange.toIntRange(),
                step = 5,
                suffix = "%",
                onValueChange = { AllSettings.gamepadCursorSensitivity.save(it) },
            )
        }
        if (OxideControlRow.GamepadCameraSensitivity in rows) {
            OxideIntRow(
                label = stringResource(R.string.settings_gamepad_camera_sensitivity_title),
                hint = stringResource(R.string.settings_gamepad_camera_sensitivity_summary),
                metrics = metrics,
                value = AllSettings.gamepadCameraSensitivity.state,
                range = AllSettings.gamepadCameraSensitivity.floatRange.toIntRange(),
                step = 5,
                suffix = "%",
                onValueChange = { AllSettings.gamepadCameraSensitivity.save(it) },
            )
        }
        if (OxideControlRow.GamepadJoystickMode in rows) {
            OxideEnumRow(
                label = stringResource(R.string.settings_gamepad_joystick_mode_title),
                hint = stringResource(R.string.settings_gamepad_joystick_mode_summary),
                metrics = metrics,
                entries = JoystickMode.entries,
                selected = AllSettings.joystickControlMode.state,
                nameOf = { oxideJoystickModeName(it) },
                onSelect = { AllSettings.joystickControlMode.save(it) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 手柄绑定的入口
// ---------------------------------------------------------------------------

@Composable
private fun OxideGamepadBindingsGroup(
    metrics: OxideMetrics,
    rows: Set<OxideControlRow>,
    onOpen: () -> Unit,
) {
    if (OxideControlRow.GamepadBindings in rows) {
        ControlGroup(
            index = 5,
            title = stringResource(R.string.oxide_set_section_gamepad_bindings),
            metrics = metrics,
        ) {
            // 映射配置本身是 MMKV 里的一张表，切换、新建与删除都在抽屉里做；
            // 面板这一行只报告当前用的是哪一套，并把人带过去
            if (OxideControlRow.GamepadMappingConfig in rows) {
                OxideActionRow(
                    label = stringResource(R.string.settings_gamepad_config_title),
                    hint = stringResource(R.string.settings_gamepad_config_summary),
                    value = AllSettings.gamepadMappingConfig.state,
                    onClick = onOpen,
                )
            }
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_gamepad_bindings),
                hint = stringResource(R.string.oxide_set_action_gamepad_bindings_detail),
                onClick = onOpen,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 控制布局与真实编辑器
// ---------------------------------------------------------------------------

/**
 * 控制布局：进真实的编辑器，而不是重建一个
 *
 * 布局文件本身由 `ControlEditorActivity` 编辑，它读的是同一份布局格式，
 * 在这里另写一套只会和它越走越远。
 */
@Composable
private fun OxideControlLayoutsGroup(
    metrics: OxideMetrics,
    bridge: OxideLauncherBridge,
) {
    val layouts by ControlManager.dataList.collectAsStateWithLifecycle()
    // 选中布局在别的界面里也会改，直接读 .value 不会重组，因此一并收成状态
    val selected by ControlManager.selectedLayout.collectAsStateWithLifecycle()
    // 布局是异步读出来的，因此这里不摆空状态——刚进页面时列表必然还是空的，
    // 空状态会闪一下。改成把"还没有布局"写进那一行的说明里，列表一到位就自然消失
    val editable = layouts.filter { it.isSupport }
    val target = selected ?: editable.firstOrNull()

    ControlGroup(
        index = 6,
        title = stringResource(R.string.oxide_set_section_control_layouts),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_control_editor),
            hint = stringResource(
                if (editable.isEmpty()) {
                    R.string.oxide_set_control_layout_missing
                } else {
                    R.string.oxide_set_action_control_editor_detail
                }
            ),
            value = target?.file?.name,
            enabled = target != null,
            onClick = { target?.let { bridge.startEditor(it.file) } },
        )
        // 布局的新建、复制、导入与删除由外壳那块控制布局面板承接
        OxideActionRow(
            label = stringResource(R.string.settings_tab_control_manage),
            hint = stringResource(R.string.oxide_set_action_control_layouts_detail),
            value = stringResource(R.string.oxide_set_count, layouts.size),
            onClick = { bridge.openSettingsSection(OxideSettingsSection.ControlManager) },
        )
    }
}

// ---------------------------------------------------------------------------
// 手柄绑定抽屉
//
// 绑定表有十六行，是这一类里唯一的"整块表面"，因此单独占一个抽屉。
// 键值本身由启动器已有的 [GamepadBindingKeyboard] 收集，映射读写走
// [GamepadViewModel]——映射格式是游戏按下去的那一套，这里只换皮，不重写。
// ---------------------------------------------------------------------------

/** 绑定抽屉的标签页：在游戏里 / 在菜单里 */
internal object OxideGamepadBindingTabs {
    const val IN_GAME = 0
    const val IN_MENU = 1
}

@Composable
private fun OxideGamepadBindingsDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
) {
    // GamepadViewModel 的构造会把映射从 MMKV 里读回来，所以它只在抽屉被打开、
    // 也就是用户主动点了入口之后才被创建，而不是在设置页一进来就碰磁盘
    val gamepad: GamepadViewModel = viewModel()
    val bridge = rememberOxideLauncherBridge()

    // 落在"游戏内"还是"菜单内"是这块抽屉自己的临时选择，跟外壳那些整块表面一样
    var tab by remember { mutableIntStateOf(OxideGamepadBindingTabs.IN_GAME) }
    // 映射改动不经过设置状态，只能靠翻这个令牌让抽屉重读一次
    var reloadToken by remember { mutableIntStateOf(0) }
    var binding by remember { mutableStateOf<GamepadMap?>(null) }
    var createProfile by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf(false) }

    val inGame = tab == OxideGamepadBindingTabs.IN_GAME
    val configs = remember(reloadToken) { gamepad.getAllConfigKeys() }
    val current = remember(reloadToken) { gamepad.currentMapping }
    val remapEnabled = AllSettings.gamepadControl.state &&
        AllSettings.gamepadInputMode.state == GamepadInputMode.Mapped

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        title = stringResource(R.string.oxide_set_drawer_gamepad_bindings),
    ) {
        OxideDrawerTabs(
            tabs = listOf(
                stringResource(R.string.settings_gamepad_mapping_in_game),
                stringResource(R.string.settings_gamepad_mapping_in_menu),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
        )

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_gamepad_profiles),
            metrics = metrics,
        ) {
            if (configs.isEmpty()) {
                OxideEmptyState(title = stringResource(R.string.settings_gamepad_config_no_items))
            } else {
                OxideEnumRow(
                    label = stringResource(R.string.settings_gamepad_config_title),
                    hint = stringResource(R.string.settings_gamepad_config_summary),
                    metrics = metrics,
                    entries = configs,
                    selected = configs.firstOrNull {
                        it == AllSettings.gamepadMappingConfig.state
                    } ?: configs.first(),
                    enabled = remapEnabled,
                    nameOf = { it },
                    onSelect = {
                        AllSettings.gamepadMappingConfig.save(it)
                        gamepad.reloadAllMappings()
                        reloadToken++
                    },
                )
            }
            OxideActionRow(
                label = stringResource(R.string.settings_gamepad_config_create),
                hint = stringResource(R.string.settings_gamepad_config_create_name),
                enabled = remapEnabled,
                onClick = { createProfile = true },
            )
            if (current != null) {
                OxideActionRow(
                    label = stringResource(R.string.settings_gamepad_config_delete),
                    value = current.name,
                    enabled = remapEnabled,
                    onClick = { deleteProfile = true },
                )
            }
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_gamepad_bindings),
            metrics = metrics,
        ) {
            GamepadMap.entries.forEach { map ->
                OxideGamepadBindingRow(
                    metrics = metrics,
                    map = map,
                    codes = remember(map, inGame, reloadToken) {
                        current?.findByMap(map, inGame)?.toList().orEmpty()
                    },
                    enabled = remapEnabled,
                    onBind = { binding = map },
                    onReset = {
                        gamepad.currentMapping?.resetMapping(map, inGame)
                        reloadToken++
                    },
                )
            }
        }
    }

    val pending = binding
    if (pending != null) {
        GamepadBindingKeyboard(
            selectedKeys = remember(pending, inGame, reloadToken) {
                gamepad.currentMapping?.findByMap(pending, inGame)?.toList().orEmpty()
            },
            onKeyAdd = { key ->
                val mapping = gamepad.currentMapping ?: return@GamepadBindingKeyboard
                val keys = mapping.findByMap(pending, inGame).orEmpty() + key
                mapping.saveMapping(pending, keys, inGame)
                reloadToken++
            },
            onKeyRemove = { key ->
                val mapping = gamepad.currentMapping ?: return@GamepadBindingKeyboard
                val keys = mapping.findByMap(pending, inGame).orEmpty() - key
                mapping.saveMapping(pending, keys, inGame)
                reloadToken++
            },
            onDismissRequest = { binding = null },
        )
    }

    if (createProfile) {
        var draft by remember { mutableStateOf("") }
        val duplicate = remember(draft) {
            draft.isNotBlank() && gamepad.containsConfig(draft.take(GAMEPAD_CONFIG_NAME_LENGTH))
        }
        OxideTextEntryDialog(
            title = stringResource(R.string.settings_gamepad_config_create),
            label = "${stringResource(R.string.settings_gamepad_config_create_name)} " +
                "(${draft.length}/$GAMEPAD_CONFIG_NAME_LENGTH)",
            value = draft,
            onValueChange = { draft = it.take(GAMEPAD_CONFIG_NAME_LENGTH) },
            maxLength = GAMEPAD_CONFIG_NAME_LENGTH,
            // 重名与空名都不给提交，提示与旧实现一致
            isValid = { !duplicate(it) && it.isNotBlank() },
            errorText = if (duplicate(draft)) {
                stringResource(R.string.settings_gamepad_config_create_contains)
            } else {
                null
            },
            confirmText = stringResource(R.string.generic_confirm),
            cancelText = stringResource(R.string.generic_cancel),
            onDismiss = { createProfile = false },
            onConfirm = {
                if (!duplicate(draft) && draft.isNotBlank()) {
                    gamepad.createNewConfig(
                        name = draft,
                        onContainsConfig = {
                            bridge.showToast(R.string.settings_gamepad_config_create_contains)
                            Logger.warning(
                                LOG_TAG,
                                "There is already a configuration with the same name"
                            )
                        },
                        onFinished = { reloadToken++ },
                    )
                }
                createProfile = false
            },
        )
    }

    if (deleteProfile) {
        val name = current?.name
        OxideConfirmDialog(
            title = stringResource(R.string.settings_gamepad_config_delete),
            message = stringResource(R.string.settings_gamepad_config_delete_message),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = {
                // 与旧设置页一致：MMKV 是内存映射的，编码一次走的是内存，
                // 放到 IO 上反而会让抽屉读 currentMapping 时跨线程
                if (remapEnabled && name != null) {
                    gamepad.deleteConfig(name)
                    reloadToken++
                }
                deleteProfile = false
            },
            onDismiss = { deleteProfile = false },
        )
    }
}

/** 一行绑定：左边是手柄按键图案，右边是它当前发出去的键，末尾是重置 */
@Composable
private fun OxideGamepadBindingRow(
    metrics: OxideMetrics,
    map: GamepadMap,
    codes: List<String>,
    enabled: Boolean,
    onBind: () -> Unit,
    onReset: () -> Unit,
) {
    // getNameByGamepadEvent 是 @Composable，因此只能在组合体里逐个取，
    // 不能塞进 joinToString 的那个普通 lambda
    val boundText = if (codes.isEmpty()) {
        stringResource(R.string.settings_gamepad_mapping_unbound)
    } else {
        val builder = StringBuilder()
        for ((index, code) in codes.withIndex()) {
            if (index > 0) builder.append(", ")
            builder.append(getNameByGamepadEvent(code))
        }
        builder.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .clickable(enabled = enabled, onClick = onBind)
            .padding(horizontal = metrics.rowGap * 2, vertical = metrics.rowGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.rowGap * 2),
    ) {
        Image(
            modifier = Modifier.size(metrics.stepperButton),
            painter = painterResource(map.getIconRes()),
            // 键位图案本身不承载信息，信息在右边的文字里，因此不朗读
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
        Text(
            text = boundText,
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OxideIconAction(
            glyph = "↺",
            description = stringResource(R.string.generic_reset),
            size = metrics.stepperButton,
            enabled = enabled,
        ) { onReset() }
    }
}
