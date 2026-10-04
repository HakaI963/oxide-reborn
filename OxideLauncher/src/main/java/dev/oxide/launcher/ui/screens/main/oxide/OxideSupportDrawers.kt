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

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.layercontroller.utils.snap.SnapMode
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.account.isAuthServerAccount
import dev.oxide.launcher.game.account.isLocalAccount
import dev.oxide.launcher.game.account.isMicrosoftAccount
import dev.oxide.launcher.game.control.ControlManager
import dev.oxide.launcher.game.multirt.Runtime
import dev.oxide.launcher.game.multirt.RuntimesManager
import dev.oxide.launcher.game.path.GamePathManager
import dev.oxide.launcher.game.plugin.driver.DriverPluginManager
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.version.installed.GraphicsApi
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.path.URL_GITHUB_DRIVER_PLUGINS
import dev.oxide.launcher.path.URL_GITHUB_RENDERER_PLUGINS
import dev.oxide.launcher.path.URL_PROJECT
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.ActionMenuSide
import dev.oxide.launcher.setting.enums.BackgroundBlur
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import dev.oxide.launcher.setting.enums.GamepadInputMode
import dev.oxide.launcher.setting.enums.MouseControlMode
import dev.oxide.launcher.setting.enums.ResolutionRule
import dev.oxide.launcher.setting.unit.floatRange
import dev.oxide.launcher.setting.unit.min
import dev.oxide.launcher.ui.activities.startEditorActivity
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.SimpleAlertDialog
import dev.oxide.launcher.ui.components.SimpleEditDialog
import dev.oxide.launcher.ui.components.toColorOrNull
import dev.oxide.launcher.ui.components.toHex
import dev.oxide.launcher.ui.control.HotbarRule
import dev.oxide.launcher.ui.control.gamepad.JoystickMode
import dev.oxide.launcher.ui.guide.GuideKeys
import dev.oxide.launcher.ui.screens.content.navigateToLogView
import dev.oxide.launcher.ui.theme.ColorThemeType
import dev.oxide.launcher.utils.customResolutionRange
import dev.oxide.launcher.utils.device.checkVulkanSupport
import dev.oxide.launcher.utils.ensureCustomResolutionInitialized
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.formatKeyCode
import dev.oxide.launcher.utils.getRealScreenSize
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.platform.getMaxMemoryForSettings
import dev.oxide.launcher.utils.settings.SettingsExport
import dev.oxide.launcher.utils.settings.SettingsTransferUtils
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.ScreenBackStackViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LOGS_TASK_ID = "OXIDE_SETTINGS_ZIP_LOGS"
private const val LOG_TAG = "OxideSettingsPage"

// ---------------------------------------------------------------------------
// 由 metrics 推导出来的尺寸
//
// 页面与抽屉都不写死 dp，这些量全部从 [OxideMetrics] 现有字段派生：
// 紧凑档下 cardGap / navItemHeight 更小，行距与控件尺寸会跟着一起收紧。
// ---------------------------------------------------------------------------

/** 行与行之间的间距 */
internal val OxideMetrics.rowGap: Dp get() = cardGap / 4

/** 分类列表里一项的高度 */
internal val OxideMetrics.categoryTabHeight: Dp get() = navItemHeight * 0.78f

/** 下拉选择器的宽度 */
internal val OxideMetrics.selectorWidth: Dp get() = (cardMinWidth * 0.42f).coerceIn(96.dp, 220.dp)

/** 数值加减按钮的边长 */
internal val OxideMetrics.stepperButton: Dp get() = navItemHeight * 0.66f

/** 分组之间额外留出的间距 */
internal val OxideMetrics.groupGap: Dp get() = sectionGap / 2

// ---------------------------------------------------------------------------
// 启动器既有操作的桥接
//
// 设置页与抽屉本身不碰导航栈，能直接调用的（检查更新、文件管理器、控制编辑器、引导、
// 日志查看）直接调用；仍然是整块页面的部分（账号、关于、控制、手柄、Java 管理、
// 控制布局管理）交给外壳提供的 [LocalOxideHostActions]，路径与旧 UI 完全一致。
// ---------------------------------------------------------------------------

internal class OxideLauncherBridge(
    /** 打开启动器里仍然存在的整块设置页面 */
    val openSettingsSection: (OxideSettingsSection) -> Unit,
    val openAccountManager: () -> Unit,
    /** 打开既有的日志查看器 */
    val openLogView: (String) -> Unit,
    val checkUpdate: () -> Unit,
    val openLink: (String) -> Unit,
    val openFileManager: (String) -> Unit,
    val showToast: (Int) -> Unit,
    val startEditor: (File) -> Unit,
    val replayGuide: () -> Unit,
)

@Composable
internal fun rememberOxideLauncherBridge(): OxideLauncherBridge {
    val context = LocalContext.current
    val host = LocalOxideHostActions.current
    val events = viewModel<EventViewModel>()
    val backStack = viewModel<ScreenBackStackViewModel>()

    return remember(context, host, events, backStack) {
        OxideLauncherBridge(
            openSettingsSection = { section -> host.openSettingsSection(section) },
            openAccountManager = { host.openAccountManager() },
            // The Oxide log screen already exists, so this must not push NormalNavKey.LogView,
            // which renders the original log viewer.
            openLogView = { path -> host.openLog(path) },
            checkUpdate = { events.sendEvent(EventViewModel.Event.CheckUpdate) },
            openLink = { link -> host.openLink(link) },
            // Must go through the host action: sending OpenFileManager starts the legacy
            // FileManagerActivity, which is the old Material file browser. Nine call sites
            // across the Oxide GUI funnel through here.
            openFileManager = { path -> host.openFiles(path) },
            showToast = { res -> events.sendToast(androidText(res)) },
            startEditor = { file -> startEditorActivity(context, file) },
            replayGuide = { events.sendEvent(EventViewModel.Event.Guide.StartGuide(GuideKeys.Main)) },
        )
    }
}

/** 打包并分享启动器日志，和旧设置页里的操作完全一致 */
internal fun shareLauncherLogs(context: Context) {
    TaskSystem.submitTask(
        Task.runTask(
            id = LOGS_TASK_ID,
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(androidText(R.string.settings_launcher_log_share_packing))
                val archive = File(PathManager.DIR_CACHE, "logs.zip")
                Logger.pack(archive)
                task.updateProgress(1f)
                task.updateMessage(null)
                // 分享面板要起 Activity，必须回到主线程
                withContext(Dispatchers.Main) {
                    shareFile(context = context, file = archive)
                }
            },
            onError = { e -> Logger.error(LOG_TAG, "Failed to package log files.", e) },
        )
    )
}

// ---------------------------------------------------------------------------
// 复用的紧凑行
// ---------------------------------------------------------------------------

/** 一个设置分组的底板：小节标题 + 一块紧凑行 */
@Composable
internal fun OxideSettingsGroup(
    title: String,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OxideSection(title = title, modifier = modifier, trailing = trailing) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.rowGap,
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                content()
            }
        }
    }
}

/** 开关行：整行可点，开关状态由整行的可切换语义暴露给无障碍服务 */
@Composable
internal fun OxideToggleRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    OxideSettingRow(
        label = label,
        hint = hint,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = {
            // 状态由整行承载，这个开关本身不再重复朗读，避免同一信息被念两遍
            Box(modifier = Modifier.clearAndSetSemantics { }) {
                OxideToggle(
                    checked = checked,
                    // 禁用行必须整个按下去都不写设置：[OxideToggle] 没有 enabled 参数，
                    // 只关掉整行的 toggleable 会让开关自己仍然能点——那样用户点一个
                    // 已经变灰的开关，值会悄悄变掉而整行却毫无反应。
                    onCheckedChange = { next ->
                        if (enabled) onCheckedChange(next)
                    },
                )
            }
        },
    )
}

/** 选择器行：右侧是启动器统一下拉控件，左侧是标签与说明 */
@Composable
internal fun <E> OxideEnumRow(
    label: String,
    hint: String? = null,
    metrics: OxideMetrics,
    entries: List<E>,
    selected: E,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    nameOf: @Composable (E) -> String,
    onSelect: (E) -> Unit,
) {
    val names = entries.map { nameOf(it) }
    OxideSettingRow(
        label = label,
        hint = hint,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            OxideDropdown(
                label = "",
                options = names,
                selectedIndex = entries.indexOf(selected).coerceAtLeast(0),
                onSelect = { index -> entries.getOrNull(index)?.let(onSelect) },
                enabled = enabled,
                modifier = Modifier.width(metrics.selectorWidth),
            )
        },
    )
}

/** 数值行：加减按钮 + 当前值，范围与步进由调用方给出 */
@Composable
internal fun OxideIntRow(
    label: String,
    hint: String? = null,
    metrics: OxideMetrics,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    suffix: String = "",
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    OxideSettingRow(
        label = label,
        hint = hint,
        value = remember(value, suffix) { if (suffix.isBlank()) "$value" else "$value$suffix" },
        enabled = enabled,
        modifier = modifier,
        trailing = {
            OxideIconAction(
                glyph = "-",
                description = stringResource(R.string.oxide_set_decrease, label),
                size = metrics.stepperButton,
                enabled = enabled && value > range.first,
            ) {
                onValueChange((value - step).coerceAtLeast(range.first))
            }
            Spacer(Modifier.width(metrics.rowGap))
            OxideIconAction(
                glyph = "+",
                description = stringResource(R.string.oxide_set_increase, label),
                size = metrics.stepperButton,
                enabled = enabled && value < range.last,
            ) {
                onValueChange((value + step).coerceAtMost(range.last))
            }
        },
    )
}

/** 文本行：点击后用启动器自带的编辑对话框改写 */
@Composable
internal fun OxideTextRow(
    title: String,
    hint: String? = null,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onSave: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    OxideSettingRow(
        label = title,
        hint = hint,
        value = value.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.oxide_set_empty_value, title),
        modifier = modifier,
        onClick = { editing = true },
    )

    if (editing) {
        SimpleEditDialog(
            title = title,
            value = draft,
            onValueChange = { draft = it },
            singleLine = singleLine,
            onDismissRequest = { editing = false },
            onConfirm = {
                onSave(draft)
                editing = false
            },
        )
    }
}

/** 操作行：既没有开关也没有数值，只有一个去处 */
@Composable
internal fun OxideActionRow(
    label: String,
    hint: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OxideSettingRow(
        label = label,
        hint = hint,
        value = value,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick,
    )
}

/** 图标按钮：补上无障碍语义，图形按钮本身不接受 contentDescription */
@Composable
internal fun OxideIconAction(
    glyph: String,
    description: String,
    size: Dp,
    enabled: Boolean = true,
    action: () -> Unit,
) {
    Box(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            if (enabled) {
                onClick(label = null) {
                    action()
                    true
                }
            }
        }
    ) {
        OxideIconButton(onClick = action, glyph = glyph, enabled = enabled, size = size)
    }
}

// ---------------------------------------------------------------------------
// 账号
// ---------------------------------------------------------------------------

/** 账号抽屉：当前账号、账号列表、账号管理器入口与备份恢复 */
@Composable
fun OxideAccountDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()
    val scope = rememberCoroutineScope()

    val accounts by AccountsManager.accountsFlow.collectAsStateWithLifecycle()
    val currentAccount by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val authServers by AccountsManager.authServersFlow.collectAsStateWithLifecycle()

    var exportWarning by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<SettingsExport?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            val export = text?.let { raw -> runCatching { SettingsTransferUtils.decode(raw) }.getOrNull() }
            if (export == null) {
                bridge.showToast(R.string.settings_import_failed)
                return@launch
            }
            val (accountCount, serverCount) = AccountsManager.importFromBackup(export)
            val settingCount = SettingsTransferUtils.restoreSettings(export.settings)
            bridge.showToast(
                if (accountCount + serverCount + settingCount > 0) {
                    R.string.settings_import_success
                } else {
                    R.string.settings_import_failed
                }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(SettingsTransferUtils.encode(export).toByteArray(Charsets.UTF_8))
            } ?: error("Could not open the destination for writing")
        }.isSuccess
        bridge.showToast(if (ok) R.string.settings_export_success else R.string.settings_export_failed)
    }

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_accounts),
    ) {
        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_signed_in),
            metrics = metrics,
        ) {
            val active = currentAccount
            if (active == null) {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_set_no_account),
                    detail = stringResource(R.string.oxide_set_no_account_detail),
                )
            } else {
                OxideSettingRow(
                    label = active.username,
                    hint = stringResource(R.string.oxide_set_account_type, oxideAccountTypeName(active)),
                )
            }
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_accounts),
            metrics = metrics,
            trailing = { OxideBadge(text = stringResource(R.string.oxide_set_count, accounts.size)) },
        ) {
            if (accounts.isEmpty()) {
                OxideEmptyState(title = stringResource(R.string.oxide_set_no_account))
            } else {
                accounts.forEach { account ->
                    val inUse = account.uniqueUUID == currentAccount?.uniqueUUID
                    OxideSettingRow(
                        label = account.username,
                        hint = stringResource(
                            R.string.oxide_set_account_type,
                            oxideAccountTypeName(account),
                        ),
                        trailing = {
                            // 正在使用的账号除了徽章之外还有一行文案，不单靠颜色区分
                            if (inUse) OxideBadge(text = stringResource(R.string.oxide_set_in_use))
                        },
                        onClick = { AccountsManager.setCurrentAccount(account) },
                    )
                }
            }
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_backup),
            metrics = metrics,
        ) {
            OxideActionRow(
                label = stringResource(R.string.settings_export_accounts),
                hint = stringResource(R.string.oxide_set_backup_summary),
                onClick = { exportWarning = true },
            )
            OxideActionRow(
                label = stringResource(R.string.settings_import_accounts),
                hint = stringResource(R.string.oxide_set_restore_summary),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_account_actions),
            metrics = metrics,
        ) {
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_manage_accounts),
                hint = stringResource(R.string.oxide_set_action_manage_accounts_detail),
                value = stringResource(R.string.oxide_set_servers_count, authServers.size),
                onClick = {
                    onDismiss()
                    bridge.openAccountManager()
                },
            )
        }
    }

    if (exportWarning) {
        SimpleAlertDialog(
            title = stringResource(R.string.settings_export_accounts),
            text = stringResource(R.string.settings_export_accounts_warning),
            onConfirm = {
                exportWarning = false
                scope.launch {
                    runCatching {
                        SettingsTransferUtils.buildExport(
                            accounts = AccountsManager.accountsFlow.value,
                            authServers = AccountsManager.authServersFlow.value,
                        )
                    }.onSuccess { export ->
                        pendingExport = export
                        exportLauncher.launch(SettingsTransferUtils.BACKUP_FILE_NAME)
                    }.onFailure {
                        bridge.showToast(R.string.settings_export_failed)
                    }
                }
            },
            onDismiss = { exportWarning = false },
        )
    }
}

@Composable
private fun oxideAccountTypeName(account: Account): String = when {
    account.isMicrosoftAccount() -> stringResource(R.string.account_type_microsoft)
    account.isAuthServerAccount() -> stringResource(R.string.oxide_set_account_type_auth_server)
    account.isLocalAccount() -> stringResource(R.string.account_type_local)
    else -> stringResource(R.string.oxide_set_account_type_other)
}

// ---------------------------------------------------------------------------
// Java / 运行时
// ---------------------------------------------------------------------------

/** Java 抽屉：启动器使用的 Java 环境、内存与自动选择策略 */
@Composable
fun OxideJavaDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()

    // 运行时列表来自磁盘，必须放到协程里读，不能在组合阶段做文件操作
    var runtimes by remember { mutableStateOf(emptyList<Runtime>()) }
    var scanning by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshToken) {
        scanning = true
        runtimes = withContext(Dispatchers.IO) {
            runCatching { RuntimesManager.getRuntimes(forceLoad = true) }.getOrDefault(emptyList())
        }
        scanning = false
    }

    val compatible = remember(runtimes) { runtimes.filter { it.isCompatible() } }
    val autoPick = AllSettings.autoPickJavaRuntime.state
    val selectedRuntime = AllSettings.javaRuntime.state
    // 内存分配没有默认值，所以下限要从设置单元自己取，写回时仍然走 AllSettings.ramAllocation.save(...)
    val minRam = AllSettings.ramAllocation.min

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_java),
    ) {
        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_runtime),
            metrics = metrics,
            trailing = {
                OxideIconAction(
                    glyph = "↻",
                    description = stringResource(R.string.generic_refresh),
                    size = metrics.stepperButton,
                    enabled = !scanning,
                ) { refreshToken++ }
            },
        ) {
            if (scanning) {
                OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
            } else if (compatible.isEmpty()) {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_set_no_runtime),
                    detail = stringResource(R.string.oxide_set_no_runtime_detail),
                )
            } else {
                OxideEnumRow(
                    label = stringResource(R.string.settings_game_java_runtime_title),
                    hint = stringResource(R.string.settings_game_java_runtime_summary),
                    metrics = metrics,
                    entries = compatible,
                    selected = compatible.firstOrNull { it.name == selectedRuntime } ?: compatible.first(),
                    enabled = !autoPick,
                    nameOf = { it.name },
                    onSelect = { AllSettings.javaRuntime.save(it.name) },
                )
            }

            OxideToggleRow(
                label = stringResource(R.string.settings_game_auto_pick_java_runtime_title),
                hint = stringResource(R.string.settings_game_auto_pick_java_runtime_summary),
                checked = autoPick,
                onCheckedChange = { AllSettings.autoPickJavaRuntime.save(it) },
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_memory),
            metrics = metrics,
        ) {
            val maxMemory = remember(context) { getMaxMemoryForSettings(context) }
            OxideIntRow(
                label = stringResource(R.string.settings_game_java_memory_title),
                hint = stringResource(R.string.settings_game_java_memory_summary),
                metrics = metrics,
                value = AllSettings.ramAllocation.state ?: minRam,
                range = minRam..maxOf(minRam, maxMemory),
                step = 128,
                suffix = " MB",
                onValueChange = { AllSettings.ramAllocation.save(it) },
            )
            OxideTextRow(
                title = stringResource(R.string.settings_game_jvm_args_title),
                hint = stringResource(R.string.settings_game_jvm_args_summary),
                value = AllSettings.jvmArgs.state,
                singleLine = false,
                onSave = { AllSettings.jvmArgs.save(it) },
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_runtime_actions),
            metrics = metrics,
        ) {
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_manage_runtimes),
                hint = stringResource(R.string.oxide_set_action_manage_runtimes_detail),
                value = stringResource(R.string.oxide_set_count, runtimes.size),
                onClick = {
                    onDismiss()
                    bridge.openSettingsSection(OxideSettingsSection.JavaManager)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 渲染器 / 图形
// ---------------------------------------------------------------------------

/**
 * 渲染器抽屉的标签页下标
 *
 * 公开成常量而不是散落的字面量，是为了让设置页能说清"从 Graphics 进来就落在图形那一页"。
 */
internal object OxideRendererTabs {
    const val RENDERER = 0
    const val GRAPHICS = 1
    const val PERFORMANCE = 2
}

/** 渲染器抽屉：渲染器与驱动、图形 API 与分辨率、性能相关的开关 */
@Composable
fun OxideRendererDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 落在哪一页标签
     *
     * 同一个抽屉同时是设置页里 Renderer 与 Graphics 两类的入口，标签页顺序保持固定，
     * 由调用方指定起点：Graphics 直接落在"图形"上，Renderer 落在"渲染器"上。
     */
    initialTab: Int = OxideRendererTabs.RENDERER,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var pluginToken by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.oxide_set_tab_renderer),
        stringResource(R.string.oxide_set_tab_graphics),
        stringResource(R.string.oxide_set_tab_performance),
    )

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_renderer),
    ) {
        OxideDrawerTabs(tabs = tabs, selectedIndex = tab, onSelect = { tab = it })

        when (tab) {
            0 -> {
                val renderers = remember(pluginToken) { Renderers.getRenderers() }
                val drivers = remember(pluginToken) { DriverPluginManager.getDriverList() }

                OxideSettingsGroup(
                    title = stringResource(R.string.oxide_set_section_renderer),
                    metrics = metrics,
                ) {
                    if (renderers.isEmpty()) {
                        OxideEmptyState(title = stringResource(R.string.oxide_set_no_renderer))
                    } else {
                        OxideEnumRow(
                            label = stringResource(R.string.settings_renderer_global_renderer_title),
                            hint = stringResource(R.string.settings_renderer_global_renderer_summary),
                            metrics = metrics,
                            entries = renderers,
                            selected = renderers.firstOrNull {
                                it.getUniqueIdentifier() == AllSettings.renderer.state
                            } ?: renderers.first(),
                            nameOf = { it.getRendererName() },
                            onSelect = { AllSettings.renderer.save(it.getUniqueIdentifier()) },
                        )
                    }

                    if (drivers.isNotEmpty()) {
                        OxideEnumRow(
                            label = stringResource(R.string.settings_renderer_global_vulkan_driver_title),
                            hint = stringResource(R.string.oxide_set_section_driver),
                            metrics = metrics,
                            entries = drivers,
                            selected = drivers.firstOrNull { it.id == AllSettings.vulkanDriver.state }
                                ?: drivers.first(),
                            nameOf = { it.name },
                            onSelect = { AllSettings.vulkanDriver.save(it.id) },
                        )
                    }

                    OxideEnumRow(
                        label = stringResource(R.string.settings_game_graphics_api_title),
                        hint = stringResource(R.string.settings_game_graphics_api_summary),
                        metrics = metrics,
                        entries = GraphicsApi.entries,
                        selected = AllSettings.graphicsApi.state,
                        nameOf = { oxideGraphicsApiName(it) },
                        onSelect = { AllSettings.graphicsApi.save(it) },
                    )
                }

                OxideSettingsGroup(
                    title = stringResource(R.string.oxide_set_section_plugins),
                    metrics = metrics,
                ) {
                    OxideActionRow(
                        label = stringResource(R.string.oxide_set_action_dl_renderer_plugin),
                        hint = stringResource(R.string.oxide_set_action_dl_renderer_plugin_detail),
                        onClick = {
                            pluginToken++
                            bridge.openLink(URL_GITHUB_RENDERER_PLUGINS)
                        },
                    )
                    OxideActionRow(
                        label = stringResource(R.string.oxide_set_action_dl_driver_plugin),
                        hint = stringResource(R.string.oxide_set_action_dl_driver_plugin_detail),
                        onClick = {
                            pluginToken++
                            bridge.openLink(URL_GITHUB_DRIVER_PLUGINS)
                        },
                    )
                }
            }

            1 -> {
                val rule = AllSettings.resolutionRule.state
                OxideSettingsGroup(
                    title = stringResource(R.string.oxide_set_section_graphics),
                    metrics = metrics,
                ) {
                    OxideEnumRow(
                        label = stringResource(R.string.settings_renderer_resolution_rule_title),
                        hint = stringResource(R.string.settings_renderer_resolution_rule_summary),
                        metrics = metrics,
                        entries = ResolutionRule.entries,
                        selected = rule,
                        nameOf = { stringResource(it.nameRes) },
                        onSelect = { picked ->
                            if (picked == ResolutionRule.CUSTOM) {
                                ensureCustomResolutionInitialized(context)
                            }
                            AllSettings.resolutionRule.save(picked)
                        },
                    )

                    if (rule == ResolutionRule.PERCENTAGE) {
                        OxideIntRow(
                            label = stringResource(R.string.settings_renderer_resolution_scale_title),
                            hint = stringResource(R.string.settings_renderer_resolution_scale_summary),
                            metrics = metrics,
                            value = AllSettings.resolutionRatio.state,
                            range = AllSettings.resolutionRatio.floatRange.toIntRange(),
                            step = 5,
                            suffix = "%",
                            onValueChange = { AllSettings.resolutionRatio.save(it) },
                        )
                    } else {
                        val screen = remember(context) { getRealScreenSize(context) }
                        OxideIntRow(
                            label = stringResource(R.string.settings_renderer_resolution_custom_width),
                            hint = stringResource(R.string.settings_renderer_resolution_custom_summary),
                            metrics = metrics,
                            value = AllSettings.customResolutionWidth.state,
                            range = customResolutionRange(screen.width),
                            step = 16,
                            onValueChange = { AllSettings.customResolutionWidth.save(it) },
                        )
                        OxideIntRow(
                            label = stringResource(R.string.settings_renderer_resolution_custom_height),
                            metrics = metrics,
                            value = AllSettings.customResolutionHeight.state,
                            range = customResolutionRange(screen.height),
                            step = 16,
                            onValueChange = { AllSettings.customResolutionHeight.save(it) },
                        )
                    }

                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_full_screen_title),
                        hint = stringResource(R.string.settings_renderer_full_screen_summary),
                        checked = AllSettings.gameFullScreen.state,
                        onCheckedChange = { AllSettings.gameFullScreen.save(it) },
                    )

                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_surface_title),
                        hint = stringResource(R.string.settings_renderer_surface_summary),
                        checked = AllSettings.useSurfaceView.state,
                        onCheckedChange = { AllSettings.useSurfaceView.save(it) },
                    )
                }
            }

            else -> {
                val vulkanSupported = remember(context) { checkVulkanSupport(context.packageManager) }
                OxideSettingsGroup(
                    title = stringResource(R.string.oxide_set_section_performance),
                    metrics = metrics,
                ) {
                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_sustained_performance_title),
                        hint = stringResource(R.string.settings_renderer_sustained_performance_summary),
                        checked = AllSettings.sustainedPerformance.state,
                        onCheckedChange = { AllSettings.sustainedPerformance.save(it) },
                    )

                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_vulkan_driver_system_title),
                        hint = stringResource(R.string.settings_renderer_vulkan_driver_system_summary),
                        checked = AllSettings.zinkPreferSystemDriver.state,
                        enabled = vulkanSupported,
                        onCheckedChange = { AllSettings.zinkPreferSystemDriver.save(it) },
                    )

                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_vsync_in_zink_title),
                        hint = stringResource(R.string.settings_renderer_vsync_in_zink_summary),
                        checked = AllSettings.vsyncInZink.state,
                        onCheckedChange = { AllSettings.vsyncInZink.save(it) },
                    )

                    OxideToggleRow(
                        label = stringResource(R.string.settings_renderer_shader_dump_title),
                        hint = stringResource(R.string.settings_renderer_shader_dump_summary),
                        checked = AllSettings.dumpShaders.state,
                        onCheckedChange = { AllSettings.dumpShaders.save(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun oxideGraphicsApiName(api: GraphicsApi): String = when (api) {
    GraphicsApi.DEFAULT -> stringResource(R.string.settings_game_graphics_api_default)
    GraphicsApi.DEFAULT_OPENGL -> stringResource(R.string.settings_game_graphics_api_default_opengl)
    else -> api.displayName
}

/** 吸附范围用启动器自己的字符串，而不是枚举名 FullScreen / Local */
@Composable
internal fun oxideSnapModeName(mode: SnapMode): String = when (mode) {
    SnapMode.FullScreen -> stringResource(R.string.control_editor_menu_widget_snap_mode_fullscreen)
    SnapMode.Local -> stringResource(R.string.control_editor_menu_widget_snap_mode_local)
}

// ---------------------------------------------------------------------------
// 存储
// ---------------------------------------------------------------------------

/** 存储抽屉：游戏目录、日志保留与打包分享、目录入口与占用统计 */
@Composable
fun OxideStorageDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()

    val paths by GamePathManager.gamePathData.collectAsStateWithLifecycle()
    val currentGamePath by GamePathManager.currentPath.collectAsStateWithLifecycle()

    var sizes by remember { mutableStateOf(OxideStorageSizes()) }
    var measuring by remember { mutableStateOf(true) }
    LaunchedEffect(currentGamePath) {
        measuring = true
        sizes = withContext(Dispatchers.IO) {
            OxideStorageSizes(
                data = directorySize(PathManager.DIR_FILES_EXTERNAL),
                game = directorySize(File(currentGamePath)),
                cache = directorySize(PathManager.DIR_CACHE),
            )
        }
        measuring = false
    }

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_storage),
    ) {
        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_paths),
            metrics = metrics,
        ) {
            if (paths.isEmpty()) {
                OxideEmptyState(title = stringResource(R.string.oxide_set_no_game_path))
            } else {
                OxideEnumRow(
                    label = stringResource(R.string.oxide_set_game_folder),
                    hint = currentGamePath,
                    metrics = metrics,
                    entries = paths,
                    selected = paths.firstOrNull { it.id == AllSettings.currentGamePathId.state }
                        ?: paths.first(),
                    nameOf = { it.title.ifBlank { it.id } },
                    onSelect = { runCatching { GamePathManager.saveCurrentPath(it.id) } },
                )
            }
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_logs),
            metrics = metrics,
        ) {
            OxideIntRow(
                label = stringResource(R.string.settings_launcher_log_retention_days_title),
                hint = stringResource(R.string.settings_launcher_log_retention_days_summary),
                metrics = metrics,
                value = AllSettings.launcherLogRetentionDays.state,
                range = AllSettings.launcherLogRetentionDays.floatRange.toIntRange(),
                suffix = " " + stringResource(R.string.unit_day),
                onValueChange = { AllSettings.launcherLogRetentionDays.save(it) },
            )
            OxideActionRow(
                label = stringResource(R.string.settings_launcher_log_share_title),
                hint = stringResource(R.string.settings_launcher_log_share_summary),
                onClick = { shareLauncherLogs(context) },
            )
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_open_logs_folder),
                hint = PathManager.DIR_LAUNCHER_LOGS.absolutePath,
                onClick = { bridge.openFileManager(PathManager.DIR_LAUNCHER_LOGS.absolutePath) },
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_usage),
            metrics = metrics,
        ) {
            OxideSettingRow(
                label = stringResource(R.string.oxide_set_usage_data),
                value = if (measuring) stringResource(R.string.oxide_set_measuring) else formatFileSize(sizes.data),
            )
            OxideSettingRow(
                label = stringResource(R.string.oxide_set_usage_game),
                value = if (measuring) stringResource(R.string.oxide_set_measuring) else formatFileSize(sizes.game),
            )
            OxideSettingRow(
                label = stringResource(R.string.oxide_set_usage_cache),
                value = if (measuring) stringResource(R.string.oxide_set_measuring) else formatFileSize(sizes.cache),
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_storage_actions),
            metrics = metrics,
        ) {
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_open_data_folder),
                hint = PathManager.DIR_FILES_EXTERNAL.absolutePath,
                onClick = { bridge.openFileManager(PathManager.DIR_FILES_EXTERNAL.absolutePath) },
            )
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_open_game_folder),
                hint = currentGamePath,
                onClick = { bridge.openFileManager(currentGamePath) },
            )
        }
    }
}

private data class OxideStorageSizes(
    val data: Long = 0L,
    val game: Long = 0L,
    val cache: Long = 0L,
)

/** 目录体积统计，必须在 IO 线程调用 */
private fun directorySize(dir: File): Long = runCatching {
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

// ---------------------------------------------------------------------------
// 高级
// ---------------------------------------------------------------------------

/** 高级抽屉：输入与叠加层细节、快捷栏判定、布局编辑器，以及全部启动器入口 */
@Composable
fun OxideAdvancedDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bridge = rememberOxideLauncherBridge()
    var tab by rememberSaveable { mutableStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.oxide_set_tab_details),
        stringResource(R.string.oxide_set_tab_actions),
    )

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_advanced),
    ) {
        OxideDrawerTabs(tabs = tabs, selectedIndex = tab, onSelect = { tab = it })

        if (tab == 0) {
            OxideAdvancedDetails(metrics = metrics)
        } else {
            OxideAdvancedActions(metrics = metrics, bridge = bridge, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun OxideAdvancedDetails(metrics: OxideMetrics) {
    val events = viewModel<EventViewModel>()
    var binding by remember { mutableStateOf(false) }
    val keyCode = AllSettings.physicalKeyImeCode.state

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_input),
        metrics = metrics,
    ) {
        OxideSettingRow(
            label = stringResource(R.string.settings_control_physical_key_bind_ime_title),
            hint = stringResource(R.string.settings_control_physical_key_bind_ime_summary),
            value = if (keyCode == null) {
                stringResource(R.string.settings_control_physical_key_bind_ime_un_bind)
            } else {
                formatKeyCode(keyCode)
            },
            onClick = { binding = true },
            trailing = {
                if (keyCode != null) {
                    OxideIconAction(
                        glyph = "×",
                        description = stringResource(R.string.generic_reset),
                        size = metrics.stepperButton,
                    ) { AllSettings.physicalKeyImeCode.save(null) }
                }
            },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_sdl_auto_ime),
            hint = stringResource(R.string.oxide_set_sdl_auto_ime_detail),
            checked = AllSettings.sdlAutoShowIme.state,
            onCheckedChange = { AllSettings.sdlAutoShowIme.save(it) },
        )
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_overlay),
        metrics = metrics,
    ) {
        OxideToggleRow(
            label = stringResource(R.string.oxide_set_show_fps),
            hint = stringResource(R.string.oxide_set_show_fps_detail),
            checked = AllSettings.showFPS.state,
            onCheckedChange = { AllSettings.showFPS.save(it) },
        )

        OxideEnumRow(
            label = stringResource(R.string.oxide_set_fps_mode),
            hint = stringResource(R.string.oxide_set_fps_mode_detail),
            metrics = metrics,
            entries = FpsDisplayMode.entries,
            selected = AllSettings.fpsDisplayMode.state,
            nameOf = { stringResource(it.nameRes) },
            onSelect = { AllSettings.fpsDisplayMode.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_show_memory),
            hint = stringResource(R.string.oxide_set_show_memory_detail),
            checked = AllSettings.showMemory.state,
            onCheckedChange = { AllSettings.showMemory.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_show_menu_ball),
            hint = stringResource(R.string.oxide_set_show_menu_ball_detail),
            checked = AllSettings.showMenuBall.state,
            onCheckedChange = { AllSettings.showMenuBall.save(it) },
        )

        OxideIntRow(
            label = stringResource(R.string.oxide_set_menu_ball_opacity),
            metrics = metrics,
            value = AllSettings.menuBallOpacity.state,
            range = AllSettings.menuBallOpacity.floatRange.toIntRange(),
            step = 5,
            suffix = "%",
            onValueChange = { AllSettings.menuBallOpacity.save(it) },
        )
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_hotbar),
        metrics = metrics,
    ) {
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_hotbar_rule),
            hint = stringResource(R.string.oxide_set_hotbar_rule_detail),
            metrics = metrics,
            entries = HotbarRule.entries,
            selected = AllSettings.hotbarRule.state,
            nameOf = { stringResource(it.nameRes) },
            onSelect = { AllSettings.hotbarRule.save(it) },
        )

        OxideIntRow(
            label = stringResource(R.string.oxide_set_hotbar_width),
            metrics = metrics,
            value = AllSettings.hotbarWidth.state,
            range = AllSettings.hotbarWidth.floatRange.toIntRange(),
            step = 10,
            suffix = "%",
            onValueChange = { AllSettings.hotbarWidth.save(it) },
        )

        OxideIntRow(
            label = stringResource(R.string.oxide_set_hotbar_height),
            metrics = metrics,
            value = AllSettings.hotbarHeight.state,
            range = AllSettings.hotbarHeight.floatRange.toIntRange(),
            step = 10,
            suffix = "%",
            onValueChange = { AllSettings.hotbarHeight.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_hotbar_double_click),
            hint = stringResource(R.string.oxide_set_hotbar_double_click_detail),
            checked = AllSettings.hotbarDoubleClick.state,
            onCheckedChange = { AllSettings.hotbarDoubleClick.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_hotbar_long_click),
            hint = stringResource(R.string.oxide_set_hotbar_long_click_detail),
            checked = AllSettings.hotbarLongClick.state,
            onCheckedChange = { AllSettings.hotbarLongClick.save(it) },
        )

        OxideIntRow(
            label = stringResource(R.string.oxide_set_hotbar_long_click_delay),
            metrics = metrics,
            value = AllSettings.hotbarLongClickDelay.state,
            range = AllSettings.hotbarLongClickDelay.floatRange.toIntRange(),
            step = 20,
            suffix = " ms",
            onValueChange = { AllSettings.hotbarLongClickDelay.save(it) },
        )
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_layout),
        metrics = metrics,
    ) {
        OxideIntRow(
            label = stringResource(R.string.oxide_set_controls_opacity),
            metrics = metrics,
            value = AllSettings.controlsOpacity.state,
            range = AllSettings.controlsOpacity.floatRange.toIntRange(),
            step = 5,
            suffix = "%",
            onValueChange = { AllSettings.controlsOpacity.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_editor_snap),
            hint = stringResource(R.string.oxide_set_editor_snap_detail),
            checked = AllSettings.editorEnableWidgetSnap.state,
            onCheckedChange = { AllSettings.editorEnableWidgetSnap.save(it) },
        )

        OxideToggleRow(
            label = stringResource(R.string.oxide_set_editor_snap_all_layers),
            hint = stringResource(R.string.oxide_set_editor_snap_all_layers_detail),
            checked = AllSettings.editorSnapInAllLayers.state,
            enabled = AllSettings.editorEnableWidgetSnap.state,
            onCheckedChange = { AllSettings.editorSnapInAllLayers.save(it) },
        )

        OxideEnumRow(
            label = stringResource(R.string.oxide_set_editor_snap_mode),
            metrics = metrics,
            entries = SnapMode.entries,
            selected = AllSettings.editorWidgetSnapMode.state,
            enabled = AllSettings.editorEnableWidgetSnap.state,
            nameOf = { oxideSnapModeName(it) },
            onSelect = { AllSettings.editorWidgetSnapMode.save(it) },
        )
    }

    // 按键捕获：等待期间开始收集 MainActivity 回传的按键，离开组合时关闭捕获
    if (binding) {
        LaunchedEffect(Unit) {
            events.sendEvent(EventViewModel.Event.Key.StartKeyCapture)
            events.events
                .filterIsInstance<EventViewModel.Event.Key.OnKeyDown>()
                .collect { event ->
                    binding = false
                    AllSettings.physicalKeyImeCode.save(event.key.keyCode)
                }
        }
        DisposableEffect(Unit) {
            onDispose { events.sendEvent(EventViewModel.Event.Key.StopKeyCapture) }
        }
    }
}

@Composable
private fun OxideAdvancedActions(
    metrics: OxideMetrics,
    bridge: OxideLauncherBridge,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val layouts by ControlManager.dataList.collectAsStateWithLifecycle()

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_control_layouts),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.settings_tab_control_manage),
            hint = stringResource(R.string.oxide_set_action_control_layouts_detail),
            value = stringResource(R.string.oxide_set_count, layouts.size),
            onClick = {
                onDismiss()
                bridge.openSettingsSection(OxideSettingsSection.ControlManager)
            },
        )
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_control_editor),
            hint = stringResource(R.string.oxide_set_action_control_editor_detail),
            enabled = layouts.any { it.isSupport },
            onClick = {
                val target = ControlManager.selectedLayout.value
                    ?: layouts.firstOrNull { it.isSupport }
                if (target != null) bridge.startEditor(target.file)
            },
        )
        OxideActionRow(
            label = stringResource(R.string.settings_tab_control),
            hint = stringResource(R.string.oxide_set_action_full_controls_detail),
            onClick = {
                onDismiss()
                bridge.openSettingsSection(OxideSettingsSection.Control)
            },
        )
        OxideActionRow(
            label = stringResource(R.string.settings_tab_gamepad),
            hint = stringResource(R.string.oxide_set_action_full_gamepad_detail),
            onClick = {
                onDismiss()
                bridge.openSettingsSection(OxideSettingsSection.Gamepad)
            },
        )
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_diagnostics),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_view_crash_log),
            hint = PathManager.FILE_CRASH_REPORT.absolutePath,
            enabled = PathManager.FILE_CRASH_REPORT.exists(),
            onClick = {
                onDismiss()
                bridge.openLogView(PathManager.FILE_CRASH_REPORT.absolutePath)
            },
        )
        OxideActionRow(
            label = stringResource(R.string.settings_launcher_log_share_title),
            hint = stringResource(R.string.settings_launcher_log_share_summary),
            onClick = { shareLauncherLogs(context) },
        )
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_check_update),
            hint = stringResource(R.string.oxide_set_action_check_update_detail),
            onClick = {
                // 更新对话框会被盖在抽屉底下，所以先把抽屉收掉
                onDismiss()
                bridge.checkUpdate()
            },
        )
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_about_actions),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.settings_tab_info_about),
            hint = stringResource(R.string.oxide_set_action_about_detail),
            onClick = {
                onDismiss()
                bridge.openSettingsSection(OxideSettingsSection.About)
            },
        )
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_guides),
            hint = stringResource(R.string.oxide_set_action_guides_detail),
            onClick = bridge.replayGuide,
        )
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_community),
            hint = stringResource(R.string.oxide_set_action_community_detail),
            onClick = { bridge.openLink(URL_PROJECT) },
        )
    }
}

// ---------------------------------------------------------------------------
// 设置取值展示
// ---------------------------------------------------------------------------

/** 浮点范围换算成整数范围，供加减按钮使用 */
internal fun ClosedFloatingPointRange<Float>.toIntRange(): IntRange =
    start.toInt()..endInclusive.toInt()

@Composable
internal fun oxideColorThemeName(type: ColorThemeType): String = when (type) {
    ColorThemeType.DYNAMIC -> stringResource(R.string.theme_color_dynamic)
    ColorThemeType.EMBERMIRE -> stringResource(R.string.theme_color_embermire)
    ColorThemeType.VELVET_ROSE -> stringResource(R.string.theme_color_velvet_rose)
    ColorThemeType.MISTWAVE -> stringResource(R.string.theme_color_mistwave)
    ColorThemeType.GLACIER -> stringResource(R.string.theme_color_glacier)
    ColorThemeType.VERDANTFIELD -> stringResource(R.string.theme_color_verdant_field)
    ColorThemeType.URBAN_ASH -> stringResource(R.string.theme_color_urban_ash)
    ColorThemeType.VERDANT_DAWN -> stringResource(R.string.theme_color_verdant_dawn)
    ColorThemeType.CUSTOM -> stringResource(R.string.generic_custom)
}

@Composable
internal fun oxideBackgroundBlurName(type: BackgroundBlur): String = when (type) {
    BackgroundBlur.Background -> stringResource(R.string.oxide_set_blur_background)
    BackgroundBlur.Foreground -> stringResource(R.string.oxide_set_blur_foreground)
}

@Composable
internal fun oxideActionMenuSideName(side: ActionMenuSide): String = when (side) {
    ActionMenuSide.START -> stringResource(R.string.oxide_set_side_start)
    ActionMenuSide.END -> stringResource(R.string.oxide_set_side_end)
}

@Composable
internal fun oxideMouseControlModeName(mode: MouseControlMode): String = stringResource(mode.nameRes)

@Composable
internal fun oxideGamepadInputModeName(mode: GamepadInputMode): String = stringResource(mode.titleRes)

@Composable
internal fun oxideJoystickModeName(mode: JoystickMode): String = stringResource(mode.titleRes)

/**
 * 自定义主题色：启动器自带的是完整的取色器组件，这里用同一套十六进制规则
 * 复现它的输入与写入路径，颜色不合规时不会写盘。
 */
@Composable
internal fun OxideCustomColorDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by remember {
        mutableStateOf(Color(AllSettings.launcherCustomColor.state).toHex())
    }
    val parsed = remember(draft) { draft.toColorOrNull() }

    SimpleEditDialog(
        title = stringResource(R.string.oxide_set_custom_color),
        value = draft,
        onValueChange = { draft = it },
        isError = parsed == null,
        supportingText = {
            if (parsed != null) {
                Text(text = parsed.toHex())
            }
        },
        onDismissRequest = onDismiss,
        onConfirm = {
            val color = parsed
            if (color != null) {
                onConfirm(color.toArgbInt())
                onDismiss()
            }
        },
    )
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)