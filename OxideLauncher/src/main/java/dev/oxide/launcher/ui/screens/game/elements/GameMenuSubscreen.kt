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

package dev.oxide.launcher.ui.screens.game.elements

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.renderer.renderers.SilicaRenderer
import dev.oxide.launcher.game.sdl.SdlBridge
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import dev.oxide.launcher.setting.enums.GamepadInputMode
import dev.oxide.launcher.setting.enums.GestureActionType
import dev.oxide.launcher.setting.enums.MouseControlMode
import dev.oxide.launcher.setting.enums.ResolutionRule
import dev.oxide.launcher.setting.unit.BooleanSettingUnit
import dev.oxide.launcher.setting.unit.EnumSettingUnit
import dev.oxide.launcher.setting.unit.IntSettingUnit
import dev.oxide.launcher.setting.unit.floatRange
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.MenuState
import dev.oxide.launcher.ui.components.rememberBoxSize
import dev.oxide.launcher.ui.control.HotbarRule
import dev.oxide.launcher.ui.control.gyroscope.isGyroscopeAvailable
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.customResolutionRange
import dev.oxide.launcher.utils.ensureCustomResolutionInitialized
import dev.oxide.launcher.viewmodel.GamepadViewModel
import kotlin.math.roundToInt
/**
 * 游戏内菜单
 *
 * 画在一块**正在运行的游戏**上，所以它是一层可以关掉的紧凑浮层，而不是一次全屏接管：
 *
 * - 面板宽高、边距与内边距全部由真实窗口尺寸推出（见 [gameMenuMetricsFor]），
 *   启动器那套 640x360 的下限在这里不成立，窗口多窄面板就多窄；
 * - 点遮罩、点标题栏的关闭或系统返回都能关掉它；菜单关着的时候整棵内容树
 *   根本不参与组合，所以帧率捕获每秒几十次的重组也碰不到它；
 * - 面板上的手势一律被吃掉（[consumeTouches]），不会顺手漏给游戏。
 *
 * 版面从原来那种两列五页的分页器换成一块面板 + 一条分区栏：分页器会把全部控件
 * 一次性排版，在游戏运行时的布局开销是可以看出来的；分区栏只组合当前分区，
 * 其余五块的控件连组合都不发生。
 *
 * 选项一项没少：分区、开关、滑杆、单选与动作都与改造前逐条对应，
 * 分辨率那一段仍然走 `AllSettings` → `onRefreshWindowSize` → `RefreshSize` 这条链。
 */
@Composable
fun GameMenuSubscreen(
    state: MenuState,
    sectionIndex: Int,
    onSectionChange: (Int) -> Unit,
    gamepadViewModel: GamepadViewModel,
    closeScreen: () -> Unit,
    onForceClose: () -> Unit,
    onSwitchLog: () -> Unit,
    enableTerracotta: Boolean,
    onOpenTerracottaMenu: () -> Unit,
    onRefreshWindowSize: () -> Unit,
    onInputMethod: () -> Unit,
    onSendKeycode: () -> Unit,
    onReplacementControl: () -> Unit,
    onEditLayout: () -> Unit,
    onShowToast: (AndroidStringText, Int) -> Unit,
) {
    val visible = state == MenuState.SHOW
    val density = LocalDensity.current
    val guiScalePercent = AllSettings.launcherGuiScale.state
    val context = LocalContext.current
    // 传感器在菜单开着期间不会变，问一次就够：组合阶段不去反复问框架
    val gyroscopeAvailable = remember(context) { isGyroscopeAvailable(context) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 窗口的**测量**尺寸，而不是 displayMetrics：分屏、折叠屏与横竖屏切换时
        // 前者会变而后者常常不变，面板必须跟着前者走
        val window = rememberBoxSize()
        val metrics = remember(window, density.density, guiScalePercent) {
            gameMenuMetricsFor(window.width, window.height, density.density, guiScalePercent)
        }
        val section = remember(sectionIndex) { GameMenuSection.fromIndex(sectionIndex) }
        val slidePx = with(density) { metrics.edgeMargin.roundToPx() }
        val scrimInteraction = remember { MutableInteractionSource() }

        // 遮罩：点空白处关掉菜单。它是菜单里唯一"点一下就走"的区域
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(Oxide.Motion.ScrimMs)),
            exit = fadeOut(animationSpec = tween(Oxide.Motion.ScrimMs)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Oxide.DrawerScrim)
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = closeScreen,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(animationSpec = tween(Oxide.Motion.PopoverFadeMs)) +
                slideInHorizontally(animationSpec = tween(Oxide.Motion.PopoverMs)) { slidePx },
            exit = fadeOut(animationSpec = tween(Oxide.Motion.PopoverFadeMs)) +
                slideOutHorizontally(animationSpec = tween(Oxide.Motion.PopoverMs)) { slidePx },
        ) {
            Column(
                modifier = Modifier
                    .padding(metrics.edgeMargin)
                    .width(metrics.panelWidth)
                    .height(metrics.panelHeight)
                    .clip(Oxide.RadiusDrawer)
                    .background(Oxide.DrawerBg)
                    .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusDrawer)
                    .consumeTouches()
            ) {
                GameMenuHeader(onClose = closeScreen)
                GameMenuSectionRail(
                    selected = section,
                    onSelect = onSectionChange,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Oxide.Line)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = metrics.contentPadding,
                        end = metrics.contentPadding,
                        top = 4.dp,
                        bottom = metrics.contentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (section) {
                        GameMenuSection.Game -> gameSection(
                            enableTerracotta = enableTerracotta,
                            onForceClose = onForceClose,
                            onSwitchLog = onSwitchLog,
                            onOpenTerracottaMenu = onOpenTerracottaMenu,
                            onRefreshWindowSize = onRefreshWindowSize,
                            onShowToast = onShowToast,
                            windowWidth = window.width,
                            windowHeight = window.height,
                            optionListMaxHeight = metrics.optionListMaxHeight,
                        )

                        GameMenuSection.Controls -> controlsSection(
                            closeScreen = closeScreen,
                            onInputMethod = onInputMethod,
                            onSendKeycode = onSendKeycode,
                            onReplacementControl = onReplacementControl,
                            onEditLayout = onEditLayout,
                        )

                        GameMenuSection.Mouse -> mouseSection()

                        GameMenuSection.Gamepad -> gamepadSection(
                            gamepadViewModel = gamepadViewModel,
                            optionListMaxHeight = metrics.optionListMaxHeight,
                        )

                        GameMenuSection.Gestures -> gesturesSection(
                            optionListMaxHeight = metrics.optionListMaxHeight,
                        )

                        GameMenuSection.Gyroscope -> gyroscopeSection(available = gyroscopeAvailable)
                    }
                }
            }
        }
    }
}

/** 标题栏：菜单名 + 关闭 */
@Composable
private fun GameMenuHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 7.dp, top = 7.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.game_menu_title),
            color = Oxide.Fg,
            fontSize = Oxide.Type.DrawerTitle.fontSize,
            lineHeight = Oxide.Type.DrawerTitle.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        GameMenuMiniButton(
            text = "✕",
            description = stringResource(R.string.generic_close),
            enabled = true,
            onClick = onClose,
        )
    }
}

/**
 * 分区栏
 *
 * 可以横向滑，但宽度被真实窗口夹过：正常窗口下六个分区一屏放得下，
 * 放不下时滑过去，而不是把标题挤成竖排。
 */
@Composable
private fun GameMenuSectionRail(
    selected: GameMenuSection,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameMenuSection.entries.forEach { section ->
            GameMenuSectionChip(
                label = stringResource(section.labelRes),
                selected = section == selected,
                onClick = { onSelect(section.ordinal) },
            )
        }
    }
}

/** 分区标签用的文案，全部取自已有的字符串资源 */
private val GameMenuSection.labelRes: Int
    get() = when (this) {
        GameMenuSection.Game -> R.string.settings_tab_game
        GameMenuSection.Controls -> R.string.settings_tab_control
        GameMenuSection.Mouse -> R.string.oxide_set_section_mouse
        GameMenuSection.Gamepad -> R.string.oxide_set_section_gamepad
        GameMenuSection.Gestures -> R.string.oxide_set_section_gestures
        GameMenuSection.Gyroscope -> R.string.oxide_set_section_gyroscope
    }

// ---------------------------------------------------------------------------
// 分区
//
// 分区函数本身不是 Composable：字符串、`LocalContext`、传感器与 Flow 都在
// 下面那些 `item {}` 里取，因此每一项只会因为自己关心的那几个设置而重组。
// ---------------------------------------------------------------------------

/** 游戏本体：动作、菜单悬浮窗与画面分辨率 */
private fun LazyListScope.gameSection(
    enableTerracotta: Boolean,
    onForceClose: () -> Unit,
    onSwitchLog: () -> Unit,
    onOpenTerracottaMenu: () -> Unit,
    onRefreshWindowSize: () -> Unit,
    onShowToast: (AndroidStringText, Int) -> Unit,
    windowWidth: Int,
    windowHeight: Int,
    optionListMaxHeight: Dp,
) {
    val showMenuBall = AllSettings.showMenuBall.state

    group(R.string.oxide_set_section_quick_actions)
    action(
        key = "forceClose",
        labelRes = R.string.game_button_force_close,
        onClick = onForceClose,
        emphasis = true,
    )
    action("switchLog", R.string.game_menu_option_switch_log, onClick = onSwitchLog)
    if (enableTerracotta) {
        action("terracotta", R.string.terracotta_menu, onClick = onOpenTerracottaMenu)
    }

    group(R.string.oxide_set_section_overlay)
    switch(
        key = "showMenuBall",
        labelRes = R.string.game_menu_option_show_menu,
        unit = AllSettings.showMenuBall,
        onTurnedOff = {
            onShowToast(androidText(R.string.game_menu_option_show_menu_hided), Toast.LENGTH_LONG)
        },
    )
    intSlider(
        key = "menuBallOpacity",
        labelRes = R.string.game_menu_option_menu_ball_opacity,
        unit = AllSettings.menuBallOpacity,
        suffix = "%",
        enabled = showMenuBall,
    )
    switch("showFPS", R.string.game_menu_option_switch_fps, AllSettings.showFPS, enabled = showMenuBall)
    choice(
        key = "fpsDisplayMode",
        labelRes = R.string.game_menu_option_fps_display_mode,
        items = FpsDisplayMode.entries,
        unit = AllSettings.fpsDisplayMode,
        optionText = { stringResource(it.nameRes) },
        enabled = showMenuBall && AllSettings.showFPS.state,
        maxListHeight = optionListMaxHeight,
    )
    switch("showMemory", R.string.game_menu_option_switch_memory, AllSettings.showMemory, enabled = showMenuBall)

    group(R.string.oxide_set_section_graphics)
    resolutionRuleRow(optionListMaxHeight, onRefreshWindowSize)
    if (gameMenuShowsResolutionScale(AllSettings.resolutionRule.state)) {
        intSlider(
            key = "resolutionRatio",
            labelRes = R.string.settings_renderer_resolution_scale_title,
            unit = AllSettings.resolutionRatio,
            suffix = "%",
            enabled = true,
            onFinished = onRefreshWindowSize,
        )
    }
    if (gameMenuShowsCustomResolution(AllSettings.resolutionRule.state)) {
        // 范围跟着**测量到的**窗口重新推导：旋转之后立刻更新，
        // 不再像以前那样把 getRealScreenSize 的结果一直缓存着
        number(
            key = "customResolutionWidth",
            labelRes = R.string.settings_renderer_resolution_custom_width,
            unit = AllSettings.customResolutionWidth,
            permitted = customResolutionRange(windowWidth),
            onCommit = onRefreshWindowSize,
        )
        number(
            key = "customResolutionHeight",
            labelRes = R.string.settings_renderer_resolution_custom_height,
            unit = AllSettings.customResolutionHeight,
            permitted = customResolutionRange(windowHeight),
            onCommit = onRefreshWindowSize,
        )
    }
    silicaGameSection()
}

/**
 * In-game Silica panel: ONLY when Silica is the active renderer.
 *
 * Phase 1 honesty: Silica config (mode/cache/upscale) is read at context init,
 * so every Silica-specific row here is restart-required and shown read-only with
 * an explicit restart note. Live-applied rows are limited to what is proven
 * safe mid-game (global resolution scale above, via onRefreshWindowSize).
 * Frame generation and frame pacing are NOT offered as toggles: no measured
 * backend exists in phase 1, and a toggle without a backend would be fake.
 */
private fun LazyListScope.silicaGameSection() {
    if (!Renderers.isCurrentRendererValid()) return
    if (Renderers.getCurrentRenderer() !== SilicaRenderer) return
    group(R.string.oxide_set_section_graphics, "silica")
    item(key = "silicaActive") {
        GameMenuNoteRow("Silica active: profile=" + AllSettings.silicaPerformanceMode.state +
            ", vault=" + AllSettings.silicaShaderCacheMb.state + "MB" +
            ", coalescing=" + (if (AllSettings.silicaCoalescing.state) "on" else "off") +
            ". Silica changes need a restart (read at context start).")
    }
    item(key = "silicaLimits") {
        GameMenuNoteRow("Restart needed for: profile, vault, coalescing, diagnostics. Frame generation and upscaling are not offered until measured-positive backends exist.")
    }
}

/** 分辨率规则：换规则时刷新游戏窗口，切到自定义时先把宽高填成真实窗口 */
private fun LazyListScope.resolutionRuleRow(
    maxListHeight: Dp,
    onRefreshWindowSize: () -> Unit,
) {
    item(key = "resolutionRule") {
        val context = LocalContext.current
        GameMenuChoiceRow(
            label = stringResource(R.string.settings_renderer_resolution_rule_title),
            options = ResolutionRule.entries,
            selected = AllSettings.resolutionRule.state,
            optionText = { stringResource(it.nameRes) },
            maxListHeight = maxListHeight,
            onSelect = { rule ->
                AllSettings.resolutionRule.save(rule)
                // 自定义分辨率尚未初始化时，以屏幕真实宽高填充
                if (rule == ResolutionRule.CUSTOM) {
                    ensureCustomResolutionInitialized(context)
                }
                onRefreshWindowSize()
            },
        )
    }
}

/** 输入与控制布局 */
private fun LazyListScope.controlsSection(
    closeScreen: () -> Unit,
    onInputMethod: () -> Unit,
    onSendKeycode: () -> Unit,
    onReplacementControl: () -> Unit,
    onEditLayout: () -> Unit,
) {
    group(R.string.oxide_set_section_input)
    action(
        key = "inputMethod",
        labelRes = R.string.game_menu_option_input_method,
        onClick = {
            onInputMethod()
            closeScreen()
        },
    )
    autoShowImeRow()

    group(R.string.oxide_set_section_control_actions)
    action(
        key = "sendKeycode",
        labelRes = R.string.game_menu_option_send_keycode,
        onClick = {
            onSendKeycode()
            closeScreen()
        },
    )
    action(
        key = "replacementControl",
        labelRes = R.string.game_menu_option_replacement_control,
        onClick = {
            onReplacementControl()
            closeScreen()
        },
    )
    action(
        key = "editLayout",
        labelRes = R.string.control_manage_info_edit,
        onClick = {
            onEditLayout()
            closeScreen()
        },
    )

    group(R.string.oxide_set_section_layout)
    intSlider(
        key = "controlsOpacity",
        labelRes = R.string.game_menu_option_controls_opacity,
        unit = AllSettings.controlsOpacity,
        suffix = "%",
        enabled = true,
    )
}

/** SDL 自动唤起输入法：开关是否可用取决于 SDL 有没有启用 */
private fun LazyListScope.autoShowImeRow() {
    item(key = "sdlAutoShowIme") {
        val sdlEnabled by SdlBridge.enabled.collectAsState()
        GameMenuSwitchRow(
            label = stringResource(R.string.game_menu_option_auto_show_ime),
            checked = AllSettings.sdlAutoShowIme.state,
            enabled = sdlEnabled,
            onCheckedChange = { AllSettings.sdlAutoShowIme.save(it) },
        )
    }
}

/** 虚拟鼠标 */
private fun LazyListScope.mouseSection() {
    val mode = AllSettings.mouseControlMode.state

    switch(
        key = "hideMouse",
        labelRes = R.string.settings_control_mouse_hide_title,
        unit = AllSettings.hideMouse,
        enabled = mode == MouseControlMode.CLICK,
    )
    switch(
        key = "enableMouseClick",
        labelRes = R.string.settings_control_mouse_enable_click_title,
        unit = AllSettings.enableMouseClick,
        enabled = mode == MouseControlMode.SLIDE,
    )
    choice(
        key = "mouseControlMode",
        labelRes = R.string.settings_control_mouse_control_mode_title,
        items = MouseControlMode.entries,
        unit = AllSettings.mouseControlMode,
        optionText = { stringResource(it.nameRes) },
    )
    intSlider("mouseSize", R.string.settings_control_mouse_size_title, AllSettings.mouseSize, suffix = "Dp")
    intSlider(
        "cursorSensitivity",
        R.string.settings_control_mouse_sensitivity_title,
        AllSettings.cursorSensitivity,
        suffix = "%",
    )
    intSlider(
        "mouseCaptureSensitivity",
        R.string.settings_control_mouse_capture_sensitivity_title,
        AllSettings.mouseCaptureSensitivity,
        suffix = "%",
    )
    intSlider(
        "mouseLongPressDelay",
        R.string.settings_control_mouse_long_press_delay_title,
        AllSettings.mouseLongPressDelay,
        suffix = "ms",
    )
}

/** 手柄 */
private fun LazyListScope.gamepadSection(
    gamepadViewModel: GamepadViewModel,
    optionListMaxHeight: Dp,
) {
    val enabled = AllSettings.gamepadControl.state
    // 重映射相关的设置只在映射模式下可用，与改造前一致
    val remapEnabled = enabled && AllSettings.gamepadInputMode.state == GamepadInputMode.Mapped

    switch("gamepadControl", R.string.settings_gamepad_title, AllSettings.gamepadControl)
    choice(
        key = "gamepadInputMode",
        labelRes = R.string.settings_gamepad_input_mode_title,
        items = GamepadInputMode.entries,
        unit = AllSettings.gamepadInputMode,
        optionText = { stringResource(it.titleRes) },
        enabled = enabled,
        maxListHeight = optionListMaxHeight,
    )
    intSlider(
        key = "gamepadDeadZoneScale",
        labelRes = R.string.settings_gamepad_deadzone_title,
        unit = AllSettings.gamepadDeadZoneScale,
        suffix = "%",
        enabled = remapEnabled,
    )
    intSlider(
        key = "gamepadCursorSensitivity",
        labelRes = R.string.settings_gamepad_cursor_sensitivity_title,
        unit = AllSettings.gamepadCursorSensitivity,
        suffix = "%",
        enabled = remapEnabled,
    )
    intSlider(
        key = "gamepadCameraSensitivity",
        labelRes = R.string.settings_gamepad_camera_sensitivity_title,
        unit = AllSettings.gamepadCameraSensitivity,
        suffix = "%",
        enabled = remapEnabled,
    )
    gamepadMappingRow(gamepadViewModel, optionListMaxHeight, remapEnabled)
}

/** 手柄映射配置：配置列表只在这一项里取一次，没有配置时给一行不可点的说明 */
private fun LazyListScope.gamepadMappingRow(
    gamepadViewModel: GamepadViewModel,
    maxListHeight: Dp,
    enabled: Boolean,
) {
    item(key = "gamepadMappingConfig") {
        val configs = remember(gamepadViewModel) { gamepadViewModel.getAllConfigKeys() }
        if (configs.isEmpty()) {
            GameMenuActionRow(
                label = stringResource(R.string.settings_gamepad_config_no_items),
                onClick = {},
                enabled = false,
            )
        } else {
            GameMenuChoiceRow(
                label = stringResource(R.string.settings_gamepad_config_title),
                options = configs,
                selected = AllSettings.gamepadMappingConfig.state,
                enabled = enabled,
                maxListHeight = maxListHeight,
                optionText = { it },
                onSelect = { name ->
                    AllSettings.gamepadMappingConfig.save(name)
                    gamepadViewModel.reloadAllMappings()
                },
            )
        }
    }
}

/** 手势与物品栏 */
private fun LazyListScope.gesturesSection(optionListMaxHeight: Dp) {
    val gestureOn = AllSettings.gestureControl.state
    val customHotbar = AllSettings.hotbarRule.state == HotbarRule.Custom

    switch("gestureControl", R.string.settings_control_gesture_control_title, AllSettings.gestureControl)
    choice(
        key = "gestureTapMouseAction",
        labelRes = R.string.settings_control_gesture_tap_action_title,
        items = GestureActionType.entries,
        unit = AllSettings.gestureTapMouseAction,
        optionText = { stringResource(it.nameRes) },
        enabled = gestureOn,
        maxListHeight = optionListMaxHeight,
    )
    choice(
        key = "gestureLongPressMouseAction",
        labelRes = R.string.settings_control_gesture_long_press_action_title,
        items = GestureActionType.entries,
        unit = AllSettings.gestureLongPressMouseAction,
        optionText = { stringResource(it.nameRes) },
        enabled = gestureOn,
        maxListHeight = optionListMaxHeight,
    )
    intSlider(
        key = "gestureLongPressDelay",
        labelRes = R.string.settings_control_gesture_long_press_delay_title,
        unit = AllSettings.gestureLongPressDelay,
        suffix = "ms",
        enabled = gestureOn,
    )

    group(R.string.oxide_set_section_hotbar)
    choice(
        key = "hotbarRule",
        labelRes = R.string.game_menu_option_hotbar_rule,
        items = HotbarRule.entries,
        unit = AllSettings.hotbarRule,
        optionText = { stringResource(it.nameRes) },
        maxListHeight = optionListMaxHeight,
    )
    // 物品栏的宽高设置存的是 0..1000 的整数，界面上按百分比显示
    intSlider(
        key = "hotbarWidth",
        labelRes = R.string.game_menu_option_hotbar_width,
        unit = AllSettings.hotbarWidth,
        suffix = "%",
        enabled = customHotbar,
        divisor = 10f,
        valueRange = 0f..100f,
    )
    intSlider(
        key = "hotbarHeight",
        labelRes = R.string.game_menu_option_hotbar_height,
        unit = AllSettings.hotbarHeight,
        suffix = "%",
        enabled = customHotbar,
        divisor = 10f,
        valueRange = 0f..100f,
    )
    switch(
        "hotbarDoubleClick",
        R.string.game_menu_option_hotbar_double_click,
        AllSettings.hotbarDoubleClick,
    )
    switch(
        "hotbarLongClick",
        R.string.game_menu_option_hotbar_long_click,
        AllSettings.hotbarLongClick,
    )
    intSlider(
        key = "hotbarLongClickDelay",
        labelRes = R.string.game_menu_option_hotbar_long_click_delay,
        unit = AllSettings.hotbarLongClickDelay,
        suffix = "ms",
        enabled = AllSettings.hotbarLongClick.state,
    )
}

/**
 * 陀螺仪
 *
 * 传感器探测在菜单这一层做一次（见 [GameMenuSubscreen]），不在每一行里重复问框架。
 */
private fun LazyListScope.gyroscopeSection(available: Boolean) {
    val on = available && AllSettings.gyroscopeControl.state

    switch(
        key = "gyroscopeControl",
        labelRes = R.string.settings_control_gyroscope_title,
        unit = AllSettings.gyroscopeControl,
        enabled = available,
        noteRes = if (available) null else R.string.settings_control_gyroscope_unsupported,
    )
    intSlider(
        key = "gyroscopeSensitivity",
        labelRes = R.string.settings_control_gyroscope_sensitivity_title,
        unit = AllSettings.gyroscopeSensitivity,
        suffix = "%",
        enabled = on,
    )
    intSlider(
        key = "gyroscopeSampleRate",
        labelRes = R.string.settings_control_gyroscope_sample_rate_title,
        unit = AllSettings.gyroscopeSampleRate,
        suffix = "ms",
        enabled = on,
    )
    switch(
        key = "gyroscopeSmoothing",
        labelRes = R.string.settings_control_gyroscope_smoothing_title,
        unit = AllSettings.gyroscopeSmoothing,
        enabled = on,
    )
    intSlider(
        key = "gyroscopeSmoothingWindow",
        labelRes = R.string.settings_control_gyroscope_smoothing_window_title,
        unit = AllSettings.gyroscopeSmoothingWindow,
        suffix = null,
        enabled = on && AllSettings.gyroscopeSmoothing.state,
    )
    switch(
        key = "gyroscopeInvertX",
        labelRes = R.string.settings_control_gyroscope_invert_x_title,
        unit = AllSettings.gyroscopeInvertX,
        enabled = on,
    )
    switch(
        key = "gyroscopeInvertY",
        labelRes = R.string.settings_control_gyroscope_invert_y_title,
        unit = AllSettings.gyroscopeInvertY,
        enabled = on,
    )
}

// ---------------------------------------------------------------------------
// 行
//
// 这些都是 LazyListScope 的扩展。设置的值一律在 `item {}` **里面**读，
// 因此改一项只会让那一项重组，不会把整个分区重新排一遍版；
// 标签也只以字符串 id 传进来，真正的 `stringResource` 同样在 `item {}` 里取。
// ---------------------------------------------------------------------------

/**
 * Settings group header with a structurally unique, stable Lazy key.
 *
 * The key is derived from the string resource plus the parent section that
 * owns this occurrence: the same resource may legitimately head two sections
 * (e.g. the graphics header and the Silica panel), and bare "group:$labelRes"
 * then crashes LazyColumn with a duplicate key. The suffix is a fixed section
 * id, never random and never a list index, so identity is stable across
 * recomposition and insertion order changes elsewhere cannot collide with it.
 */
private fun LazyListScope.group(labelRes: Int, section: String? = null) {
    // Key expression kept inline (no local val): the uniqueness test below
    // allows exactly string literals and the bare forwarder, nothing else.
    item(key = if (section == null) "group:$labelRes" else "group:$labelRes:$section") {
        GameMenuGroupLabel(stringResource(labelRes))
    }
}

private fun LazyListScope.action(
    key: String,
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasis: Boolean = false,
) {
    item(key = key) {
        GameMenuActionRow(
            label = stringResource(labelRes),
            onClick = onClick,
            enabled = enabled,
            emphasis = emphasis,
        )
    }
}

/** 布尔设置：整行可点，状态由 [toggleable] 暴露 */
private fun LazyListScope.switch(
    key: String,
    labelRes: Int,
    unit: BooleanSettingUnit,
    enabled: Boolean = true,
    noteRes: Int? = null,
    onTurnedOff: () -> Unit = {},
) {
    item(key = key) {
        GameMenuSwitchRow(
            label = stringResource(labelRes),
            checked = unit.state,
            enabled = enabled,
            hint = noteRes?.let { stringResource(it) },
            onCheckedChange = { value ->
                unit.save(value)
                if (!value) onTurnedOff()
            },
        )
    }
}

/**
 * 整数设置的滑杆
 *
 * [divisor] 只给"存的是千分比、界面上按百分比显示"的那两项用（物品栏宽高），
 * 其余是 1。范围默认取设置自己声明的区间，所以界面上能拖到的两端
 * 与落盘时会夹的两端永远是同一个区间。
 */
private fun LazyListScope.intSlider(
    key: String,
    labelRes: Int,
    unit: IntSettingUnit,
    suffix: String? = null,
    enabled: Boolean = true,
    divisor: Float = 1f,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    onFinished: () -> Unit = {},
) {
    item(key = key) {
        GameMenuSliderRow(
            label = stringResource(labelRes),
            value = unit.state / divisor,
            valueRange = valueRange ?: unit.floatRange,
            enabled = enabled,
            suffix = suffix,
            onValueChange = { unit.updateState((it * divisor).roundToInt()) },
            onValueChangeFinished = { committed ->
                unit.save((committed * divisor).roundToInt())
                onFinished()
            },
        )
    }
}

/** 单选设置 */
private fun <T : Enum<T>> LazyListScope.choice(
    key: String,
    labelRes: Int,
    items: List<T>,
    unit: EnumSettingUnit<T>,
    optionText: @Composable (T) -> String,
    enabled: Boolean = true,
    maxListHeight: Dp = 160.dp,
    onSelected: (T) -> Unit = {},
) {
    item(key = key) {
        GameMenuChoiceRow(
            label = stringResource(labelRes),
            options = items,
            selected = unit.state,
            enabled = enabled,
            maxListHeight = maxListHeight,
            optionText = optionText,
            onSelect = { option ->
                unit.save(option)
                onSelected(option)
            },
        )
    }
}

/** 整数输入（自定义分辨率的宽高）：范围由真实窗口推出 */
private fun LazyListScope.number(
    key: String,
    labelRes: Int,
    unit: IntSettingUnit,
    permitted: IntRange,
    onCommit: () -> Unit,
) {
    item(key = key) {
        GameMenuNumberRow(
            label = stringResource(labelRes),
            value = unit.state,
            permitted = permitted,
            enabled = true,
            onCommit = { committed ->
                unit.save(committed)
                onCommit()
            },
        )
    }
}