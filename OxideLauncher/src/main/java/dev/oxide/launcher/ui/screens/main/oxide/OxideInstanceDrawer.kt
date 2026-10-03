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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.control.ControlManager
import dev.oxide.launcher.game.multirt.RuntimesManager
import dev.oxide.launcher.game.plugin.driver.DriverPluginManager
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.support.touch_controller.VibrationHandler
import dev.oxide.launcher.game.version.installed.GraphicsApi
import dev.oxide.launcher.game.version.installed.SettingState
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionConfig
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionType
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.unit.min
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.NestedNavKey
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.content.elements.VersionsOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.platform.getMaxMemoryForSettings
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "OxideInstanceDrawer"

/** 内容页最多列出多少个文件名，其余只给总数 */
private const val LISTED_FILES = 8

/** 日志尾部保留的行数 */
private const val LOG_TAIL_LINES = 14

/** 读取日志尾部时最多回看多少字节 */
private const val LOG_TAIL_BYTES = 8L * 1024L

private const val MEMORY_STEP = 256
private const val VIBRATION_MIN = 80
private const val VIBRATION_MAX = 500
private const val VIBRATION_STEP = 20

private const val LOG_DATE_PATTERN = "yyyy-MM-dd HH:mm"

/**
 * 实例配置抽屉
 *
 * 与参考稿一致：右侧 `min(520px, 44vw)` 的面板，顶部是版本名，下面是五个标签页。
 * 每一项都落在真实的 [VersionConfig] 字段上，写回走和既有 `VersionConfigScreen`
 * 完全相同的 `saveWithThrowable()`，因此不存在只在抽屉里生效的假后端。
 */
@Composable
fun OxideInstanceDrawer(
    version: Version,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventViewModel = rememberOxideEventViewModel()
    val errorViewModel: ErrorViewModel = viewModel()
    val backStack: ScreenBackStackViewModel = viewModel()
    // 宿主给出的真实深层入口（版本设置屏幕等），由外壳决定怎么走
    val hostActions = LocalOxideHostActions.current

    val config = version.getVersionConfig()
    val versionName = version.getVersionName()

    // VersionConfig 的字段是普通 var，改完不会通知组合，因此先镜像到 Compose 状态，落盘前统一写回
    val state = remember(version) { InstanceSettingsState(config) }

    val submitError: (ErrorViewModel.ThrowableMessage) -> Unit = { message ->
        errorViewModel.showError(message)
    }
    val saveTitle = androidText(R.string.oxide_ins_save_failed_title)
    val save: () -> Unit = {
        state.writeBack()
        runCatching { config.saveWithThrowable() }.onFailure { e ->
            Logger.error(TAG, "Failed to save the version configuration.", e)
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = saveTitle,
                    message = androidText(e.getMessageOrToString()),
                )
            )
        }
    }
    val togglePin: (Boolean) -> Unit = { pinned ->
        runCatching { version.setPinnedAndSave(pinned) }.onFailure { e ->
            Logger.error(TAG, "Failed to save the pinned state.", e)
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = saveTitle,
                    message = androidText(e.getMessageOrToString()),
                )
            )
        }
    }

    // 重命名或删除之后这个对象已经不在真实列表里了，收起抽屉，避免继续往一个已改名的目录写配置
    val liveVersions by VersionsManager.versions.collectAsStateWithLifecycle()
    LaunchedEffect(liveVersions, versionName) {
        if (liveVersions.isNotEmpty() && liveVersions.none { it.getVersionName() == versionName }) {
            onDismiss()
        }
    }

    val probeKey = remember(version) { version.getVersionPath().absolutePath }
    var probe by remember(version) { mutableStateOf<DrawerProbe?>(null) }
    LaunchedEffect(probeKey) {
        probe = withContext(Dispatchers.IO) { probeDrawerContent(version) }
    }

    // 探测结果还没回来时不做判断；只有确知无效才禁用，避免刚打开就点不动
    val usable = probe?.valid != false

    var operation by remember(version) { mutableStateOf<VersionsOperation>(VersionsOperation.None) }

    val openFolder: (File) -> Unit = { dir ->
        eventViewModel.sendEvent(EventViewModel.Event.OpenFileManager(rootPath = dir.absolutePath))
    }
    // 进入既有的版本子屏幕，导航键与入口和旧版本管理页完全一致
    val openLegacy: (NormalNavKey.Versions) -> Unit = { target ->
        val key = NestedNavKey.VersionSettings(version)
        backStack.mainScreen.navigateTo(key, useClassEquality = true)
        key.backStack.navigateTo(target)
        onDismiss()
    }
    val openExport: () -> Unit = {
        backStack.mainScreen.removeAndNavigateTo(
            remove = NestedNavKey.VersionSettings::class,
            screenKey = NestedNavKey.VersionExport(version),
            useClassEquality = true,
        )
        onDismiss()
    }

    val tabTitles = listOf(
        stringResource(R.string.oxide_ins_tab_general),
        stringResource(R.string.oxide_ins_tab_runtime),
        stringResource(R.string.oxide_ins_tab_arguments),
        stringResource(R.string.oxide_ins_tab_content),
        stringResource(R.string.oxide_ins_tab_logs),
    )
    var tabIndex by remember(version) { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        OxideDrawerHost(
            visible = true,
            metrics = metrics,
            onDismiss = onDismiss,
            title = versionName,
        ) {
            OxideDrawerTabs(
                tabs = tabTitles,
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
            )

            Spacer(Modifier.height(metrics.sectionGap))

            when (tabIndex) {
                0 -> OxideInstanceGeneralTab(
                    version = version,
                    state = state,
                    save = save,
                    usable = usable,
                    onTogglePin = togglePin,
                    onOpenFolder = { openFolder(version.getVersionPath()) },
                    onRename = { operation = VersionsOperation.Rename(version) },
                    onCopy = { operation = VersionsOperation.Copy(version) },
                    onExport = openExport,
                    onDelete = { operation = VersionsOperation.Delete(version) },
                    onFullSettings = {
                        hostActions.openVersionSettings(version)
                        onDismiss()
                    },
                )

                1 -> OxideInstanceRuntimeTab(
                    state = state,
                    save = save,
                    usable = usable,
                    runtimeNames = probe?.runtimeNames.orEmpty(),
                    onLaunch = onLaunch,
                )

                2 -> OxideInstanceArgumentsTab(state = state, save = save)

                3 -> OxideInstanceContentTab(
                    version = version,
                    probe = probe,
                    onOpenFolder = openFolder,
                    onManageMods = { openLegacy(NormalNavKey.Versions.ModsManager) },
                    onManageResourcePacks = { openLegacy(NormalNavKey.Versions.ResourcePackManager) },
                    onManageShaders = { openLegacy(NormalNavKey.Versions.ShadersManager) },
                    onManageSaves = { openLegacy(NormalNavKey.Versions.SavesManager) },
                    onManageScreenshots = { openLegacy(NormalNavKey.Versions.ScreenshotsManager) },
                )

                else -> OxideInstanceLogsTab(
                    version = version,
                    probe = probe,
                    onOpenFolder = { openFolder(version.getOxideVersionPath()) },
                )
            }
        }

        VersionsOperation(
            versionsOperation = operation,
            updateVersionsOperation = { operation = it },
            submitError = submitError,
        )
    }
}

// ---- 各标签页 ----------------------------------------------------------------

@Composable
private fun OxideInstanceGeneralTab(
    version: Version,
    state: InstanceSettingsState,
    save: () -> Unit,
    usable: Boolean,
    onTogglePin: (Boolean) -> Unit,
    onOpenFolder: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onFullSettings: () -> Unit,
) {
    val versionInfo = remember(version) { version.getVersionInfo() }
    val folderDesc = stringResource(R.string.oxide_ins_open_folder)

    OxideSection(title = stringResource(R.string.oxide_ins_section_identity)) {
        OxideInstanceBlock {
            OxideSettingRow(
                label = stringResource(R.string.oxide_ins_row_instance_name),
                value = version.getVersionName(),
            )
            versionInfo?.minecraftVersion?.takeIf { it.isNotBlank() }?.let { minecraft ->
                OxideSettingRow(
                    label = stringResource(R.string.oxide_ins_row_minecraft_version),
                    value = minecraft,
                )
            }
            val loaders = versionInfo?.loaderInfos
                ?.map { loader -> "${loader.loader.displayName} ${loader.version}".trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            if (loaders.isNotEmpty()) {
                OxideSettingRow(
                    label = stringResource(R.string.oxide_ins_row_loader),
                    value = loaders.joinToString(" + "),
                )
            }
            OxideSettingRow(
                label = stringResource(R.string.oxide_ins_row_version_type),
                value = stringResource(
                    when (version.versionType) {
                        VersionType.VANILLA -> R.string.oxide_ins_type_vanilla
                        VersionType.MODLOADERS -> R.string.oxide_ins_type_modded
                        VersionType.UNKNOWN -> R.string.oxide_ins_type_unknown
                    }
                ),
            )
            OxideSettingRow(
                label = stringResource(R.string.oxide_ins_row_version_folder),
                value = version.getVersionsFolder(),
                onClick = onOpenFolder,
                trailing = {
                    OxideIconButton(
                        onClick = onOpenFolder,
                        glyph = "\u2197",
                        size = 22.dp,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = folderDesc },
                    )
                },
            )
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideSection(title = stringResource(R.string.oxide_ins_section_instance)) {
        OxideInstanceBlock {
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_isolation),
                hint = stringResource(R.string.oxide_ins_hint_isolation),
                options = SettingState.entries.map { entry -> stringResource(entry.textRes) },
                selectedIndex = SettingState.entries.indexOf(state.isolation).coerceAtLeast(0),
                onSelect = { index ->
                    state.isolation = SettingState.entries[index]
                    save()
                },
            )
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_integrity),
                hint = stringResource(R.string.oxide_ins_hint_integrity),
                options = SettingState.entries.map { entry -> stringResource(entry.textRes) },
                selectedIndex = SettingState.entries.indexOf(state.integrity).coerceAtLeast(0),
                onSelect = { index ->
                    state.integrity = SettingState.entries[index]
                    save()
                },
            )
            OxideSettingRow(
                label = stringResource(R.string.oxide_ins_row_pin),
                hint = stringResource(R.string.oxide_ins_hint_pin),
                value = stringResource(
                    if (version.pinnedState) R.string.generic_enabled else R.string.generic_disabled
                ),
                trailing = {
                    OxideToggle(
                        checked = version.pinnedState,
                        onCheckedChange = onTogglePin,
                    )
                },
            )
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideSection(title = stringResource(R.string.oxide_ins_section_actions)) {
        OxideInstanceBlock {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OxideButton(
                    text = stringResource(R.string.oxide_ins_action_rename),
                    onClick = onRename,
                    enabled = usable,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                OxideButton(
                    text = stringResource(R.string.oxide_ins_action_copy),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OxideButton(
                    text = stringResource(R.string.oxide_ins_action_export),
                    onClick = onExport,
                    enabled = usable,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                // 删除是清理无效版本的唯一途径，因此不受有效性限制
                OxideButton(
                    text = stringResource(R.string.oxide_ins_action_delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            // 宿主提供的真实入口：进入既有的版本设置屏幕（概览、配置、模组、存档……）
            OxideButton(
                text = stringResource(R.string.oxide_ins_action_full_settings),
                onClick = onFullSettings,
                enabled = usable,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OxideInstanceRuntimeTab(
    state: InstanceSettingsState,
    save: () -> Unit,
    usable: Boolean,
    runtimeNames: List<String>,
    onLaunch: () -> Unit,
) {
    val context = LocalContext.current
    val followGlobal = stringResource(R.string.oxide_ins_follow_global)

    val renderers = remember { Renderers.getRenderers() }
    val rendererIds = remember(renderers) {
        listOf("") + renderers.map { renderer -> renderer.getUniqueIdentifier() }
    }
    val rendererLabels = remember(renderers, followGlobal) {
        listOf(followGlobal) + renderers.map { renderer -> renderer.getRendererName() }
    }

    val drivers = remember { DriverPluginManager.getDriverList() }
    val driverIds = remember(drivers) { listOf("") + drivers.map { driver -> driver.id } }
    val driverLabels = remember(drivers, followGlobal) {
        listOf(followGlobal) + drivers.map { driver -> driver.name }
    }

    val controls by ControlManager.dataList.collectAsStateWithLifecycle()
    val controlList = remember(controls) { controls.filter { it.isSupport } }
    val controlIds = remember(controlList) { listOf("") + controlList.map { it.file.name } }
    val controlLabels = remember(controlList, followGlobal) {
        listOf(followGlobal) + controlList.map { it.controlLayout.info.name.translate() }
    }

    val graphicsApis = remember { GraphicsApi.entries.toList() }
    val graphicsLabels = graphicsApis.map { api ->
        when (api) {
            GraphicsApi.DEFAULT -> stringResource(R.string.oxide_ins_graphics_default)
            GraphicsApi.DEFAULT_OPENGL -> stringResource(R.string.oxide_ins_graphics_default_opengl)
            else -> api.displayName
        }
    }

    val minMemory = AllSettings.ramAllocation.min
    val maxMemory = remember(context) { getMaxMemoryForSettings(context) }
    val memoryValues = remember(maxMemory, minMemory, state.ramAllocation) {
        val steps = (minMemory..maxMemory step MEMORY_STEP).toList()
        val current = state.ramAllocation
        if (current >= minMemory && current !in steps) (steps + current).sorted() else steps
    }
    val memoryLabels = memoryValues.map { value -> stringResource(R.string.oxide_ins_mb, value) }

    val vibrateKinds = remember { VibrationHandler.VibrateKind.entries.toList() }
    val vibrateLabels = vibrateKinds.map { kind ->
        stringResource(
            when (kind) {
                VibrationHandler.VibrateKind.ONE_SHOT -> R.string.oxide_ins_vibrate_one_shot
                VibrationHandler.VibrateKind.CLICK -> R.string.oxide_ins_vibrate_click
                VibrationHandler.VibrateKind.DOUBLE_CLICK -> R.string.oxide_ins_vibrate_double_click
                VibrationHandler.VibrateKind.HEAVY_CLICK -> R.string.oxide_ins_vibrate_heavy_click
                VibrationHandler.VibrateKind.TICK -> R.string.oxide_ins_vibrate_tick
            }
        )
    }
    val durationValues = remember(state.vibrateDuration) {
        val steps = (VIBRATION_MIN..VIBRATION_MAX step VIBRATION_STEP).toList()
        val current = state.vibrateDuration.coerceIn(VIBRATION_MIN, VIBRATION_MAX)
        if (current in steps) steps else (steps + current).sorted()
    }
    val durationLabels = durationValues.map { value -> stringResource(R.string.oxide_ins_ms, value) }

    OxideSection(title = stringResource(R.string.oxide_ins_section_runtime)) {
        OxideInstanceBlock {
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_java),
                hint = stringResource(R.string.oxide_ins_hint_java),
                options = runtimeNames,
                selectedIndex = runtimeNames.indexOf(state.javaRuntime).coerceAtLeast(0),
                placeholder = followGlobal,
                onSelect = { index ->
                    state.javaRuntime = runtimeNames.getOrElse(index) { "" }
                    save()
                },
            )
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_memory),
                hint = stringResource(R.string.oxide_ins_hint_memory),
                options = memoryLabels,
                // 第 0 项是"跟随全局"，因此真实档位要往后挪一格
                selectedIndex = memoryValues.indexOf(state.ramAllocation)
                    .let { index -> if (index >= 0) index + 1 else 0 },
                placeholder = followGlobal,
                onSelect = { index ->
                    state.ramAllocation = memoryValues.getOrElse(index - 1) { -1 }
                    save()
                },
            )
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideSection(title = stringResource(R.string.oxide_ins_section_graphics)) {
        OxideInstanceBlock {
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_renderer),
                hint = stringResource(R.string.oxide_ins_hint_renderer),
                options = rendererLabels,
                selectedIndex = rendererIds.indexOfFirst { id -> id == state.renderer }
                    .let { index -> if (index >= 0) index else 0 },
                placeholder = followGlobal,
                onSelect = { index ->
                    state.renderer = rendererIds.getOrElse(index) { "" }
                    save()
                },
            )
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_graphics_api),
                hint = stringResource(R.string.oxide_ins_hint_graphics_api),
                options = graphicsLabels,
                selectedIndex = graphicsApis.indexOfFirst { api -> api == state.graphicsApi }
                    .let { index -> if (index >= 0) index else 0 },
                placeholder = followGlobal,
                onSelect = { index ->
                    state.graphicsApi = graphicsApis.getOrNull(index)
                    save()
                },
            )
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_vulkan_driver),
                hint = stringResource(R.string.oxide_ins_hint_vulkan_driver),
                options = driverLabels,
                selectedIndex = driverIds.indexOfFirst { id -> id == state.driver }
                    .let { index -> if (index >= 0) index else 0 },
                placeholder = followGlobal,
                onSelect = { index ->
                    state.driver = driverIds.getOrElse(index) { "" }
                    save()
                },
            )
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideSection(title = stringResource(R.string.oxide_ins_section_controls)) {
        OxideInstanceBlock {
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_control),
                hint = stringResource(R.string.oxide_ins_hint_control),
                options = controlLabels,
                selectedIndex = controlIds.indexOfFirst { id -> id == state.control }
                    .let { index -> if (index >= 0) index else 0 },
                placeholder = followGlobal,
                onSelect = { index ->
                    state.control = controlIds.getOrElse(index) { "" }
                    save()
                },
            )
            OxideInstanceDropdownRow(
                label = stringResource(R.string.oxide_ins_row_vibration),
                hint = stringResource(R.string.oxide_ins_hint_vibration),
                options = vibrateLabels,
                selectedIndex = vibrateKinds.indexOfFirst { kind -> kind == state.vibrateKind }
                    .let { index -> if (index >= 0) index else 0 },
                onSelect = { index ->
                    state.vibrateKind = vibrateKinds[index]
                    save()
                },
            )
            if (state.vibrateKind == VibrationHandler.VibrateKind.ONE_SHOT) {
                OxideInstanceDropdownRow(
                    label = stringResource(R.string.oxide_ins_row_vibration_time),
                    hint = stringResource(R.string.oxide_ins_hint_vibration_time),
                    options = durationLabels,
                    selectedIndex = durationValues.indexOfFirst { value ->
                        value == state.vibrateDuration
                    }.let { index -> if (index >= 0) index else 0 },
                    onSelect = { index ->
                        state.vibrateDuration = durationValues[index]
                        save()
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideButton(
        text = stringResource(R.string.oxide_ins_action_launch),
        onClick = onLaunch,
        enabled = usable,
        tone = OxideButtonTone.Primary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OxideInstanceArgumentsTab(
    state: InstanceSettingsState,
    save: () -> Unit,
) {
    OxideSection(title = stringResource(R.string.oxide_ins_section_arguments)) {
        OxideInstanceBlock {
            OxideInstanceTextRow(
                label = stringResource(R.string.oxide_ins_row_jvm_args),
                hint = stringResource(R.string.oxide_ins_hint_jvm_args),
                value = state.jvmArgs,
                onValueChange = { text ->
                    state.jvmArgs = text
                    save()
                },
            )
            OxideInstanceTextRow(
                label = stringResource(R.string.oxide_ins_row_game_args),
                hint = stringResource(R.string.oxide_ins_hint_game_args),
                value = state.gameArgs,
                onValueChange = { text ->
                    state.gameArgs = text
                    save()
                },
            )
            OxideInstanceTextRow(
                label = stringResource(R.string.oxide_ins_row_custom_info),
                hint = stringResource(R.string.oxide_ins_hint_custom_info),
                value = state.customInfo,
                onValueChange = { text ->
                    state.customInfo = text
                    save()
                },
            )
            OxideInstanceTextRow(
                label = stringResource(R.string.oxide_ins_row_server_ip),
                hint = stringResource(R.string.oxide_ins_hint_server_ip),
                value = state.serverIp,
                onValueChange = { text ->
                    state.serverIp = text
                    save()
                },
            )
        }
    }
}

@Composable
private fun OxideInstanceContentTab(
    version: Version,
    probe: DrawerProbe?,
    onOpenFolder: (File) -> Unit,
    onManageMods: () -> Unit,
    onManageResourcePacks: () -> Unit,
    onManageShaders: () -> Unit,
    onManageSaves: () -> Unit,
    onManageScreenshots: () -> Unit,
) {
    val gameDir = remember(version) { version.getGameDir() }
    val manageLabel = stringResource(R.string.oxide_ins_content_manage)

    OxideInstanceFolderSection(
        title = stringResource(R.string.oxide_ins_content_mods),
        listing = probe?.mods,
        gameDir = gameDir,
        folder = VersionFolders.MOD,
        manageLabel = manageLabel,
        onOpenFolder = onOpenFolder,
        onManage = onManageMods,
    )
    OxideInstanceFolderSection(
        title = stringResource(R.string.oxide_ins_content_resource_packs),
        listing = probe?.resourcePacks,
        gameDir = gameDir,
        folder = VersionFolders.RESOURCE_PACK,
        manageLabel = manageLabel,
        onOpenFolder = onOpenFolder,
        onManage = onManageResourcePacks,
    )
    OxideInstanceFolderSection(
        title = stringResource(R.string.oxide_ins_content_shaders),
        listing = probe?.shaders,
        gameDir = gameDir,
        folder = VersionFolders.SHADERS,
        manageLabel = manageLabel,
        onOpenFolder = onOpenFolder,
        onManage = onManageShaders,
    )
    OxideInstanceFolderSection(
        title = stringResource(R.string.oxide_ins_content_saves),
        listing = probe?.saves,
        gameDir = gameDir,
        folder = VersionFolders.SAVES,
        manageLabel = manageLabel,
        onOpenFolder = onOpenFolder,
        onManage = onManageSaves,
    )
    OxideInstanceFolderSection(
        title = stringResource(R.string.oxide_ins_content_screenshots),
        listing = probe?.screenshots,
        gameDir = gameDir,
        folder = VersionFolders.SCREENSHOTS,
        manageLabel = manageLabel,
        onOpenFolder = onOpenFolder,
        onManage = onManageScreenshots,
    )
}

@Composable
private fun OxideInstanceLogsTab(
    version: Version,
    probe: DrawerProbe?,
    onOpenFolder: () -> Unit,
) {
    val logFile = remember(version) { version.getLatestLog() }
    val modifiedAt = probe?.logModifiedAt?.takeIf { it > 0L }

    OxideSection(title = stringResource(R.string.oxide_ins_section_log)) {
        OxideInstanceBlock {
            OxideSettingRow(
                label = stringResource(R.string.oxide_ins_row_log_file),
                value = logFile.name,
                hint = modifiedAt?.let { stamp -> formatDate(stamp, LOG_DATE_PATTERN) },
                onClick = onOpenFolder,
            )
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))

    OxideInstanceBlock {
        val lines = probe?.logTail
        if (lines.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.oxide_ins_log_missing),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.Mono.fontSize,
                lineHeight = Oxide.Type.Mono.lineHeight,
            )
        } else {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = Oxide.FgDim,
                    fontSize = Oxide.Type.Mono.fontSize,
                    lineHeight = Oxide.Type.Mono.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- 抽屉里的通用小部件 --------------------------------------------------------

/** 参考稿的 `.block`：一个带描边的深色分组 */
@Composable
private fun OxideInstanceBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusBlock)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        content = content,
    )
}

/** 一个带说明的下拉项；选项面板走 Popup，不会被抽屉或卡片裁掉 */
@Composable
private fun OxideInstanceDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String = "",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        OxideDropdown(
            label = "",
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
        )
        hint?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一个单行输入项，值一变就交给调用方写回真实的配置对象 */
@Composable
private fun OxideInstanceTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Oxide.RadiusControl)
                .background(Oxide.BgButton)
                .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Oxide.Type.Body.copy(color = Oxide.Fg),
                cursorBrush = SolidColor(Oxide.FgMuted),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        hint?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 内容页里的一个文件夹分组：总数 + 前若干个真实文件名 + 打开目录 + 进入既有管理屏幕 */
@Composable
private fun OxideInstanceFolderSection(
    title: String,
    listing: FolderListing?,
    gameDir: File,
    folder: VersionFolders,
    manageLabel: String,
    onOpenFolder: (File) -> Unit,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    val countText = listing?.let { probed ->
        context.getString(R.string.oxide_ins_content_count, probed.total)
    }
    val extra = (listing?.total ?: 0) - (listing?.names?.size ?: 0)

    OxideSection(
        title = title,
        trailing = {
            countText?.let { text ->
                Text(
                    text = text,
                    color = Oxide.FgDim,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
            }
        },
    ) {
        OxideInstanceBlock {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OxideButton(
                    text = manageLabel,
                    onClick = onManage,
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.oxide_ins_action_open_folder),
                    onClick = { onOpenFolder(folder.getDir(gameDir)) },
                    modifier = Modifier.weight(1f),
                )
            }
            val emptyText = stringResource(
                if (listing == null) {
                    R.string.oxide_ins_content_loading
                } else {
                    R.string.oxide_ins_content_empty
                }
            )
            if (listing == null || listing.names.isEmpty()) {
                Text(
                    text = emptyText,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            } else {
                listing.names.forEach { name -> OxideInstanceFileRow(name) }
                if (extra > 0) {
                    Text(
                        text = context.getString(R.string.oxide_ins_content_more, extra),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(Oxide.PagePaddingT))
}

/** 内容页里的一行文件名，对应参考稿的 `.listRow` */
@Composable
private fun OxideInstanceFileRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(Oxide.RadiusSmall)
                .background(Brush.linearGradient(listOf(Oxide.BgChip, Oxide.BgButton)))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---- 状态与探测 ---------------------------------------------------------------

/**
 * 抽屉里那一组可编辑的版本设置
 *
 * [VersionConfig] 的字段是普通的 var，改完不会通知组合，因此这里用一份 Compose 状态镜像它们，
 * 落盘前由 [writeBack] 一次性写回真实的 [VersionConfig]。
 */
@Stable
private class InstanceSettingsState(private val config: VersionConfig) {

    var isolation by mutableStateOf(config.isolationType)
    var integrity by mutableStateOf(config.skipGameIntegrityCheck)
    var javaRuntime by mutableStateOf(config.javaRuntime)
    var ramAllocation by mutableIntStateOf(config.ramAllocation)
    var renderer by mutableStateOf(config.renderer)
    var graphicsApi by mutableStateOf(config.graphicsApi)
    var driver by mutableStateOf(config.driver)
    var control by mutableStateOf(config.control)
    var vibrateKind by mutableStateOf(config.touchVibrateKind ?: VibrationHandler.VibrateKind.default)
    var vibrateDuration by mutableIntStateOf(config.touchVibrateDuration)

    var jvmArgs by mutableStateOf(config.jvmArgs)
    var gameArgs by mutableStateOf(config.gameArgs)
    var customInfo by mutableStateOf(config.customInfo)
    var serverIp by mutableStateOf(config.serverIp)

    /**
     * 打开抽屉时的原始值，用来判断哪些字段真的被改过
     *
     * 只写回被改动的字段：像"跟随全局"这种空值一旦被写成显式值，
     * 就会在用户只想改别的东西时悄悄改变行为。
     */
    private val base = config.copy()
    private val baseVibrateKind = base.touchVibrateKind ?: VibrationHandler.VibrateKind.default

    fun writeBack() {
        if (isolation != base.isolationType) config.isolationType = isolation
        if (integrity != base.skipGameIntegrityCheck) config.skipGameIntegrityCheck = integrity
        if (javaRuntime != base.javaRuntime) config.javaRuntime = javaRuntime
        if (ramAllocation != base.ramAllocation) config.ramAllocation = ramAllocation
        if (renderer != base.renderer) config.renderer = renderer
        if (graphicsApi != base.graphicsApi) config.graphicsApi = graphicsApi
        if (driver != base.driver) config.driver = driver
        if (control != base.control) config.control = control
        if (vibrateKind != baseVibrateKind) config.touchVibrateKind = vibrateKind
        if (vibrateDuration != base.touchVibrateDuration) config.touchVibrateDuration = vibrateDuration
        if (jvmArgs != base.jvmArgs) config.jvmArgs = jvmArgs
        if (gameArgs != base.gameArgs) config.gameArgs = gameArgs
        if (customInfo != base.customInfo) config.customInfo = customInfo
        if (serverIp != base.serverIp) config.serverIp = serverIp
    }
}

/** 一个文件夹里的真实内容 */
private data class FolderListing(val total: Int, val names: List<String>)

/** 抽屉里所有需要碰磁盘的读取结果 */
private data class DrawerProbe(
    val valid: Boolean,
    val mods: FolderListing,
    val resourcePacks: FolderListing,
    val shaders: FolderListing,
    val saves: FolderListing,
    val screenshots: FolderListing,
    val runtimeNames: List<String>,
    val logTail: List<String>,
    val logModifiedAt: Long,
)

/** 统计一个版本目录下的真实文件，名称按文件名排序并截断 */
private fun listFolder(dir: File): FolderListing {
    val files = dir.listFiles()
        ?.filter { file -> file.isFile }
        ?.sortedBy { file -> file.name.lowercase() }
        .orEmpty()
    return FolderListing(
        total = files.size,
        names = files.take(LISTED_FILES).map { file -> file.name },
    )
}

/** 读日志尾部若干行；文件不存在或读失败时返回空列表，绝不编造内容 */
private fun readLogTail(file: File, maxLines: Int): Pair<List<String>, Long> {
    if (!file.exists()) return emptyList<String>() to 0L
    return runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val window = minOf(length, LOG_TAIL_BYTES).coerceAtLeast(1L)
            raf.seek(length - window)
            val buffer = ByteArray(window.toInt())
            raf.readFully(buffer)
            val lines = String(buffer, Charsets.UTF_8)
                .lineSequence()
                .map { line -> line.trim() }
                .filter { line -> line.isNotEmpty() }
                .toList()
            lines.takeLast(maxLines) to file.lastModified()
        }
    }.getOrElse { emptyList<String>() to 0L }
}

/** 在 IO 上一次性取回抽屉需要的全部真实数据 */
private fun probeDrawerContent(version: Version): DrawerProbe {
    val gameDir = version.getGameDir()
    val (logTail, logModifiedAt) = readLogTail(version.getLatestLog(), LOG_TAIL_LINES)
    return DrawerProbe(
        valid = version.isValid(),
        mods = listFolder(VersionFolders.MOD.getDir(gameDir)),
        resourcePacks = listFolder(VersionFolders.RESOURCE_PACK.getDir(gameDir)),
        shaders = listFolder(VersionFolders.SHADERS.getDir(gameDir)),
        saves = listFolder(VersionFolders.SAVES.getDir(gameDir)),
        screenshots = listFolder(VersionFolders.SCREENSHOTS.getDir(gameDir)),
        // 运行时的目录可能读不到，失败时只留一个"跟随全局"，不阻断整个抽屉
        runtimeNames = runCatching {
            RuntimesManager.getRuntimes()
                .filter { runtime -> runtime.isCompatible() }
                .map { runtime -> runtime.name }
        }.getOrDefault(emptyList()),
        logTail = logTail,
        logModifiedAt = logModifiedAt,
    )
}