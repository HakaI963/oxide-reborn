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
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import dev.oxide.launcher.game.path.GamePath
import dev.oxide.launcher.game.version.installed.VersionsManager
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
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.theme.ColorThemeType
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.customResolutionRange
import dev.oxide.launcher.utils.device.checkVulkanSupport
import dev.oxide.launcher.utils.ensureCustomResolutionInitialized
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.formatKeyCode
import dev.oxide.launcher.utils.getRealScreenSize
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.platform.getMaxMemoryForSettings
import dev.oxide.launcher.utils.string.getMessageOrToString
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

/**
 * 选择器行：右侧是启动器统一下拉控件，左侧是标签与说明
 *
 * [selected] 允许为 null：那是"存着的那个值当前拿不到"——比如设置里记着某个渲染器插件，
 * 而那个插件此刻没装上。这种情况下下拉控件显示 [placeholder]（也就是存着的那个标识），
 * 而不是把列表第一项假装成当前值。谎报当前值比承认拿不到更糟：用户会以为自己在用
 * 那个渲染器。列表仍然是全部可选项，选一个就把设置改过来。
 */
@Composable
internal fun <E> OxideEnumRow(
    label: String,
    hint: String? = null,
    metrics: OxideMetrics,
    entries: List<E>,
    selected: E?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    /** [selected] 为 null 时显示的那一行文字，通常就是存着的那个标识 */
    placeholder: String = "",
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
                // -1 表示"当前值不在列表里"，面板退回 placeholder 而不是默认第一项
                selectedIndex = selected?.let { entries.indexOf(it) } ?: -1,
                onSelect = { index -> entries.getOrNull(index)?.let(onSelect) },
                enabled = enabled,
                placeholder = placeholder,
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

/**
 * Java 抽屉：启动器使用的 Java 环境、内存与自动选择策略
 *
 * 「选哪个运行时」与「要不要自动选」互斥：开了自动选，那一行选择器就**不出现**，
 * 而不是变灰留在原地——变灰的行在横屏里既占位置又读不懂为什么点不动。
 */
@Composable
fun OxideJavaDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

    // 自定义运行时：导入、删除，以及用某一档运行时跑一个 .jar
    val scope = rememberCoroutineScope()
    var runtimeError by remember { mutableStateOf<String?>(null) }
    var runtimeDeleteTarget by remember { mutableStateOf<Runtime?>(null) }
    // 跑 jar 之前挑一档运行时；挑完立刻打开文件选择器
    var jarRuntimePicker by remember { mutableStateOf(false) }
    var jarRuntimeChoice by remember { mutableStateOf<Runtime?>(null) }

    val importRuntime = oxideJavaRuntimeImportPicker(
        onImported = { refreshToken++ },
        onError = { runtimeError = it },
    )
    val runJar = oxideJavaJarRunner(runtimeProvider = { jarRuntimeChoice })

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.oxide_set_drawer_java),
    ) {
        runtimeError?.let { detail ->
            OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.generic_error),
                detail = detail,
                dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                onDismiss = { runtimeError = null },
            )
        }

        runtimeDeleteTarget?.let { target ->
            OxideSecConfirmBar(
                metrics = metrics,
                text = stringResource(R.string.multirt_runtime_delete_message, target.name),
                confirmText = stringResource(R.string.generic_delete),
                dismissText = stringResource(R.string.generic_cancel),
                onConfirm = {
                    runtimeDeleteTarget = null
                    scope.launch(Dispatchers.IO) {
                        runCatching { RuntimesManager.removeRuntime(target.name) }
                            .onFailure { runtimeError = it.getMessageOrToString() }
                    }
                    refreshToken++
                },
                onDismiss = { runtimeDeleteTarget = null },
            )
        }

        OxideSettingsGroup(
            title = stringResource(R.string.oxide_set_section_runtime),
            metrics = metrics,
            trailing = {
                // 正在扫盘时"重新扫描"没有意义，因此那一枚整个不出现，
                // 而不是留一枚按了也不会重扫的灰按钮
                if (!scanning) {
                    OxideIconAction(
                        glyph = "↻",
                        description = stringResource(R.string.generic_refresh),
                        size = metrics.stepperButton,
                    ) { refreshToken++ }
                }
            },
        ) {
            if (scanning) {
                OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
            } else if (compatible.isEmpty()) {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_set_no_runtime),
                    detail = stringResource(R.string.oxide_set_no_runtime_detail),
                )
            } else if (!oxideJavaRuntimePickerVisible(
                    scanning = scanning,
                    hasRuntime = compatible.isNotEmpty(),
                    autoPick = autoPick,
                )
            ) {
                // 自动选开着：这一行只报告"现在是哪一个"，因此是只读行而不是选择器
                OxideSettingRow(
                    label = stringResource(R.string.oxide_set_runtime_auto_chosen),
                    hint = stringResource(
                        R.string.oxide_set_runtime_auto_chosen_detail,
                        compatible.first().name,
                    ),
                )
            } else {
                OxideEnumRow(
                    label = stringResource(R.string.settings_game_java_runtime_title),
                    hint = stringResource(R.string.settings_game_java_runtime_summary),
                    metrics = metrics,
                    entries = compatible,
                    // 设置里记着的那个运行时此刻不在列表里，就把那个标识原样显示出来，
                    // 而不是把列表第一项假装成当前值
                    selected = compatible.firstOrNull { it.name == selectedRuntime },
                    placeholder = selectedRuntime,
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

        // 自定义运行时：导入一个 tar.xz、删掉一个自己装的、用某一档跑一个 .jar。
        // 清单与选择器是**两件事**：上面那一栏回答"用哪一档"，这一栏回答"装了哪些、
        // 哪些能删"，因此这里只读、只给删除，不重复一次选择。
        OxideSettingsGroup(
            title = stringResource(R.string.oxide_cap_java_manage),
            metrics = metrics,
            trailing = {
                OxideBadge(text = stringResource(R.string.oxide_set_count, runtimes.size))
            },
        ) {
            if (runtimes.isEmpty()) {
                OxideEmptyState(title = stringResource(R.string.oxide_set_no_runtime))
            } else {
                runtimes.forEach { runtime ->
                    OxideSettingRow(
                        label = runtime.name,
                        hint = runtime.versionString
                            ?: stringResource(R.string.multirt_runtime_corrupt),
                        value = if (runtime.isProvidedByLauncher) {
                            stringResource(R.string.multirt_runtime_provided_by_launcher)
                        } else {
                            null
                        },
                        // 自带的那几档不能删：不给出这一枚按钮，理由由这一行的
                        // "Built-in" 徽标给出，而不是留一枚灰着的叉让人猜
                        trailing = if (oxideJavaRuntimeDeleteVisible(runtime)) {
                            {
                                OxideIconAction(
                                    glyph = "\u2715",
                                    description = stringResource(R.string.generic_delete),
                                    size = metrics.stepperButton,
                                ) { runtimeDeleteTarget = runtime }
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            OxideSecDivider()

            // 扫描运行时的那几秒里这一行整行消失，而不是灰着。灰着的可点行读起来是
            // "可以点，只是现在不行"，而这里的理由是"正在扫描，扫完下面那份清单本身
            // 就是答案" —— 与上面那个选择器的处理保持一致。
            if (!scanning) {
                OxideActionRow(
                    label = stringResource(R.string.oxide_cap_java_import),
                    hint = stringResource(R.string.oxide_cap_java_import_detail),
                    onClick = importRuntime,
                )
            }
            // 跑 jar 要先定运行时：点这一行先挑一档，挑完立刻打开文件选择器。
            // 挑完文件再问用哪个 Java 是更差的做法——用户已经选好了文件。
            runJar?.let { launchJar ->
                OxideActionRow(
                    label = stringResource(R.string.oxide_cap_java_run_jar),
                    hint = stringResource(R.string.oxide_cap_java_run_jar_detail),
                    value = jarRuntimeChoice?.name
                        ?: stringResource(R.string.oxide_cap_java_run_jar_default),
                    onClick = { jarRuntimePicker = true },
                )
                if (jarRuntimePicker) {
                    OxideListDialog(
                        title = stringResource(R.string.oxide_cap_java_run_jar_pick),
                        options = listOf(
                            OxideDialogOption(
                                key = OXIDE_JAR_RUNTIME_AUTO,
                                label = stringResource(R.string.oxide_cap_java_run_jar_default),
                                detail = stringResource(
                                    R.string.settings_game_auto_pick_java_runtime_summary
                                ),
                            )
                        ) + compatible.map { runtime ->
                            OxideDialogOption(
                                key = runtime.name,
                                label = runtime.name,
                                detail = runtime.versionString,
                            )
                        },
                        currentKey = jarRuntimeChoice?.name ?: OXIDE_JAR_RUNTIME_AUTO,
                        confirmText = stringResource(R.string.generic_confirm),
                        metrics = metrics,
                        onOptionSelected = { key ->
                            jarRuntimeChoice = compatible.firstOrNull { it.name == key }
                            launchJar()
                        },
                        onDismiss = { jarRuntimePicker = false },
                    )
                }
            }
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
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
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
                        val storedRenderer = AllSettings.renderer.state
                        val rendererResolved = oxideStoredSelectionResolves(
                            storedId = storedRenderer,
                            candidates = renderers.map { it.getUniqueIdentifier() },
                        )
                        OxideEnumRow(
                            label = stringResource(R.string.settings_renderer_global_renderer_title),
                            hint = if (rendererResolved) {
                                stringResource(R.string.settings_renderer_global_renderer_summary)
                            } else {
                                // 设置里记着的渲染器插件此刻没装上：说出那个标识，
                                // 好过把列表第一项显示成"当前正在用的"
                                stringResource(R.string.oxide_set_renderer_missing, storedRenderer)
                            },
                            metrics = metrics,
                            entries = renderers,
                            selected = renderers.firstOrNull {
                                it.getUniqueIdentifier() == storedRenderer
                            },
                            placeholder = storedRenderer,
                            nameOf = { it.getRendererName() },
                            onSelect = { AllSettings.renderer.save(it.getUniqueIdentifier()) },
                        )
                    }

                    if (drivers.isNotEmpty()) {
                        val storedDriver = AllSettings.vulkanDriver.state
                        val driverResolved = oxideStoredSelectionResolves(
                            storedId = storedDriver,
                            candidates = drivers.map { it.id },
                        )
                        OxideEnumRow(
                            label = stringResource(R.string.settings_renderer_global_vulkan_driver_title),
                            hint = if (driverResolved) {
                                stringResource(R.string.oxide_set_section_driver)
                            } else {
                                stringResource(R.string.oxide_set_driver_missing, storedDriver)
                            },
                            metrics = metrics,
                            entries = drivers,
                            selected = drivers.firstOrNull { it.id == storedDriver },
                            placeholder = storedDriver,
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

                    // Zink 走的是 Vulkan：设备根本没有 Vulkan 支持时这两项都不存在，
                    // 留着它们只会得到两枚点得动、存下来了、却永远不会被读到的开关
                    if (oxideZinkSettingVisible(vulkanSupported)) {
                        OxideToggleRow(
                            label = stringResource(R.string.settings_renderer_vulkan_driver_system_title),
                            hint = stringResource(R.string.settings_renderer_vulkan_driver_system_summary),
                            checked = AllSettings.zinkPreferSystemDriver.state,
                            onCheckedChange = { AllSettings.zinkPreferSystemDriver.save(it) },
                        )

                        OxideToggleRow(
                            label = stringResource(R.string.settings_renderer_vsync_in_zink_title),
                            hint = stringResource(R.string.settings_renderer_vsync_in_zink_summary),
                            checked = AllSettings.vsyncInZink.state,
                            onCheckedChange = { AllSettings.vsyncInZink.save(it) },
                        )
                    }

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

// ---------------------------------------------------------------------------
// 行是否出现的判据（纯函数）
//
// 这两条都写成函数而不是把条件散在组合里，因为它们各自的失败模式是一样的：
// 判据错了就会出现"点得动、但没有任何效果"的控件，或者反过来把还能用的
// 控件藏起来。纯函数因此可以逐个宽度、逐个状态被钉死。
// ---------------------------------------------------------------------------

/**
 * 「选哪个 Java 运行时」那一行是否出现
 *
 * 三种情况各自不出现：还在扫盘、没有可用运行时、以及开着自动选。
 * 第三种是最容易被写成"变灰"的那种——自动选开着的时候那个选择器确实改不了，
 * 但它不是"暂时不能用"，而是"这个选择此刻不归你管"，因此整行让位给一行只读文案。
 */
internal fun oxideJavaRuntimePickerVisible(
    scanning: Boolean,
    hasRuntime: Boolean,
    autoPick: Boolean,
): Boolean = !scanning && hasRuntime && !autoPick

/**
 * 那些只对 Zink 有意义的设置（系统 Vulkan 驱动、Zink 内的垂直同步）是否出现
 *
 * Zink 走的是 Vulkan。设备根本没有 Vulkan 支持时这两项不存在，
 * 留着它们只会是两枚点得动、存下来了、却永远不会被读到的开关。
 *
 * 纯函数，因此可以逐个设备状态钉死。
 */
internal fun oxideZinkSettingVisible(vulkanSupported: Boolean): Boolean =
    vulkanSupported

/**
 * 设置里记着的那个值当前能不能在列表里找到
 *
 * 找不到时选择器必须显示那个标识本身，而不是列表第一项：
 * 谎报当前值比承认"拿不到"更糟。
 */
internal fun oxideStoredSelectionResolves(
    storedId: String,
    candidates: List<String>,
): Boolean = candidates.any { it == storedId }

/** 吸附范围用启动器自己的字符串，而不是枚举名 FullScreen / Local */
@Composable
internal fun oxideSnapModeName(mode: SnapMode): String = when (mode) {
    SnapMode.FullScreen -> stringResource(R.string.control_editor_menu_widget_snap_mode_fullscreen)
    SnapMode.Local -> stringResource(R.string.control_editor_menu_widget_snap_mode_local)
}

// ---------------------------------------------------------------------------
// 存储
// ---------------------------------------------------------------------------

/**
 * 存储抽屉：游戏目录（含改名与删除）、日志保留与打包分享、目录入口与占用统计
 *
 * 四个"打开某个目录"的动作全部走 [rememberOxideLauncherBridge] 的 `openFileManager`，
 * 它落到宿主的 `openFiles`，因此打开的是 Oxide 自己的文件页而不是旧的 Material 文件浏览器。
 */
@Composable
fun OxideStorageDrawer(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()

    // 取一次 contentResolver 就够了：它在 SAF 回调里用，而回调不是组合期，
    // 组合期才谈得上下一次 configuration 变了会不会读到旧值。
    val contentResolver = remember(context) { context.contentResolver }

    val paths by GamePathManager.gamePathData.collectAsStateWithLifecycle()
    val currentGamePath by GamePathManager.currentPath.collectAsStateWithLifecycle()

    // 改名与删除都直接落到 GamePathManager 上：它就是旧界面那三个按钮调用的同一条链路，
    // 所以这里不需要新的持久化，也不需要担心两份列表各说各话
    var renameTarget by remember { mutableStateOf<GamePath?>(null) }
    var deleteTarget by remember { mutableStateOf<GamePath?>(null) }
    var renameDraft by remember(renameTarget) { mutableStateOf(renameTarget?.title.orEmpty()) }

    // 新增一个游戏目录：先用 SAF 挑一个真实文件夹，再起名字
    var addPathStage by remember { mutableStateOf<OxideStorageAddStage?>(null) }
    var addPathDraft by remember { mutableStateOf("") }
    var addPathError by remember { mutableStateOf<String?>(null) }
    var addPathPending by remember { mutableStateOf<String?>(null) }
    val addScope = rememberCoroutineScope()
    // 文案在组合期取一次，回调与协程里只拿得到字符串，拿不到 stringResource
    val unsupportedPathMessage = stringResource(R.string.oxide_cap_storage_game_dir_unsupported)
    val duplicatePathMessage = stringResource(R.string.oxide_cap_storage_game_dir_conflict)
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val picked = docId
            ?.takeIf(::isPrimaryStorageDocument)
            ?.let { id ->
                oxideGamePathFromRelative(
                    relative = Uri.decode(id.removePrefix(OXIDE_PRIMARY_DOCUMENT_PREFIX)),
                    storageRoot = oxideExternalStorageRoot(),
                )
            }
        if (picked.isNullOrBlank()) {
            // 游戏目录后面全部按真实路径处理，因此只能覆盖这台设备主存储上的文件夹
            addPathError = unsupportedPathMessage
            addPathStage = null
        } else {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            addPathPending = picked
            addPathDraft = File(picked).name.ifBlank { picked }
            addPathStage = OxideStorageAddStage.Name
        }
    }

    // 清理冗余游戏资源：与已安装的实例数量相关，
    // 一个都没有时它会把整个 assets 删干净，因此那时不能让它跑
    val storageViewModel: OxideStorageViewModel = viewModel(key = "OxideStorageViewModel") {
        OxideStorageViewModel()
    }
    val cleanupRunning by storageViewModel.running.collectAsStateWithLifecycle()
    val cleanupTasks by storageViewModel.tasks.collectAsStateWithLifecycle()
    val cleanupFailed by storageViewModel.failed.collectAsStateWithLifecycle()
    val installedVersions by VersionsManager.versions.collectAsStateWithLifecycle()
    val cleanupNotice by storageViewModel.result.collectAsStateWithLifecycle()

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
                renameTarget?.let { target ->
                    OxideSecInput(
                        metrics = metrics,
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        placeholder = stringResource(R.string.oxide_st_path_rename_hint),
                        label = stringResource(R.string.generic_rename),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                        OxideButton(
                            text = stringResource(R.string.generic_cancel),
                            onClick = { renameTarget = null },
                            modifier = Modifier.weight(1f),
                        )
                        OxideButton(
                            text = stringResource(R.string.generic_confirm),
                            onClick = {
                                val title = renameDraft.trim()
                                renameTarget = null
                                if (title.isNotEmpty()) {
                                    // GamePathManager 在这一项已经被删掉时会抛，
                                    // 抛了就在原地说一句，而不是让抽屉静悄悄地没反应
                                    runCatching { GamePathManager.modifyTitle(target, title) }
                                        .onFailure { bridge.showToast(R.string.oxide_st_path_save_failed) }
                                }
                            },
                            enabled = renameDraft.isNotBlank(),
                            tone = OxideButtonTone.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                deleteTarget?.let { target ->
                    OxideSecConfirmBar(
                        metrics = metrics,
                        text = stringResource(R.string.versions_manage_game_path_delete_message),
                        confirmText = stringResource(R.string.generic_delete),
                        dismissText = stringResource(R.string.generic_cancel),
                        onConfirm = {
                            deleteTarget = null
                            runCatching { GamePathManager.removePath(target) }
                                .onFailure { bridge.showToast(R.string.oxide_st_path_save_failed) }
                        },
                        onDismiss = { deleteTarget = null },
                    )
                }

                // 游戏目录一个一项，而不是一个下拉：改名与删除都挂在具体某一项上，
                // 下拉里放不下这两件事。当前用哪一档由"Current"这行字说明，不只靠底色
                paths.forEachIndexed { index, path ->
                    if (index > 0) OxideSecDivider()
                    val selected = path.id == AllSettings.currentGamePathId.state
                    OxideSettingRow(
                        label = oxideStoragePathLabel(path),
                        hint = path.path,
                        value = if (selected) stringResource(R.string.oxide_st_path_current) else null,
                        onClick = {
                            // 与旧界面的列表逐条一致：默认那一个走 saveDefaultPath，
                            // 其余要先确认存储权限，saveCurrentPath 自己会抛
                            runCatching {
                                if (path.id == GamePathManager.DEFAULT_ID) {
                                    GamePathManager.saveDefaultPath()
                                } else {
                                    GamePathManager.saveCurrentPath(path.id)
                                }
                            }.onFailure { bridge.showToast(R.string.oxide_st_path_save_failed) }
                        },
                        trailing = if (oxideStoragePathEditable(path.id)) {
                            {
                                OxideIconAction(
                                    glyph = "✎",
                                    description = stringResource(R.string.generic_rename),
                                    size = metrics.stepperButton,
                                ) {
                                    renameDraft = path.title
                                    renameTarget = path
                                }
                                Spacer(Modifier.width(metrics.rowGap))
                                OxideIconAction(
                                    glyph = "✕",
                                    description = stringResource(R.string.generic_delete),
                                    size = metrics.stepperButton,
                                ) {
                                    deleteTarget = path
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // 新增一个游戏目录：先挑文件夹，再起名字，最后落到
        // GamePathManager.addNewPath——旧界面对同一件事做的也是这一步
        addPathError?.let { detail ->
            OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.generic_error),
                detail = detail,
                dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                onDismiss = { addPathError = null },
            )
        }
        if (oxideStorageAddVisible(addPathStage != null)) {
            OxideActionRow(
                label = stringResource(R.string.oxide_cap_storage_add_game_dir),
                hint = stringResource(R.string.versions_manage_game_path_storage_permissions),
                onClick = {
                    addPathError = null
                    treePicker.launch(null)
                },
            )
        }
        if (addPathStage == OxideStorageAddStage.Name) {
            OxideSecInput(
                metrics = metrics,
                value = addPathDraft,
                onValueChange = { addPathDraft = it },
                placeholder = stringResource(R.string.oxide_cap_storage_game_dir_name),
                label = stringResource(R.string.oxide_cap_storage_game_dir_name),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                OxideButton(
                    text = stringResource(R.string.generic_cancel),
                    onClick = {
                        addPathStage = null
                        addPathDraft = ""
                    },
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.generic_confirm),
                    onClick = {
                        val draft = addPathDraft.trim()
                        val path = addPathPending
                        if (draft.isEmpty() || path == null) return@OxideButton
                        addScope.launch {
                            // 落盘必须在 IO 上；GamePathManager.addNewPath 自己只是投纹理，
                            // 回调下来刷新那一步在它自己的 IO 作用域里
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    GamePathManager.addNewPath(title = draft, path = path)
                                }
                            }.onSuccess {
                                addPathStage = null
                                addPathDraft = ""
                                addPathPending = null
                                bridge.showToast(R.string.oxide_cap_storage_game_dir_added)
                            }.onFailure { error ->
                                addPathError = if (storageViewModel.isDuplicatePathConflict(error)) {
                                    duplicatePathMessage
                                } else {
                                    error.getMessageOrToString()
                                }
                                addPathStage = null
                            }
                        }
                    },
                    enabled = addPathDraft.isNotBlank(),
                    tone = OxideButtonTone.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 清理冗余游戏资源。GameAssetCleaner 会比对所有已安装版本需要的资源，
        // 然后删掉其余的；它本身没有“一个版本都没有就别跑”这道保护，
        // 所以按钮在那之前就已经拒绝可点了
        cleanupNotice?.let { result ->
            OxideSettingRow(
                label = stringResource(R.string.versions_manage_cleanup),
                value = stringResource(
                    R.string.versions_manage_cleanup_success,
                    result.files,
                    result.size,
                ),
                trailing = {
                    OxideIconAction(
                        glyph = "\u2715",
                        description = stringResource(R.string.generic_close),
                        size = metrics.stepperButton,
                    ) { storageViewModel.consumeResult() }
                },
            )
        }
        if (cleanupFailed.isNotEmpty()) {
            OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.versions_manage_cleanup_failed),
                detail = cleanupFailed.take(8).joinToString(", "),
                dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                onDismiss = { storageViewModel.cancel() },
            )
        }
        if (cleanupRunning) {
            OxideLoadingRow(
                // 任务标题在状态层存的是 AndroidStringText：只有组合里才解得开资源
                text = cleanupTasks.lastOrNull()?.let { resolveAndroidString(it).text }
                    ?: stringResource(R.string.oxide_cap_storage_cleanup_running),
            )
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = { storageViewModel.cancel() },
                tone = OxideButtonTone.Secondary,
            )
        } else {
            OxideActionRow(
                label = stringResource(R.string.versions_manage_cleanup),
                hint = stringResource(R.string.oxide_cap_storage_cleanup_detail),
                enabled = oxideStorageCleanupEnabled(installedVersions.size),
                onClick = {
                    storageViewModel.start(
                        onFinished = { },
                        onFailure = { bridge.showToast(R.string.versions_manage_cleanup_failed) },
                    )
                },
            )
            if (installedVersions.isEmpty()) {
                Text(
                    text = stringResource(R.string.oxide_cap_storage_cleanup_needs_instance),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
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
            // 日志阅读器是 Oxide 自己的那一块表面，不是旧的 LogView 路由：
            // bridge.openLogView 走的是 host.openLog，也就是 OxideDestination.Log
            OxideActionRow(
                label = stringResource(R.string.oxide_set_action_open_logs),
                hint = stringResource(R.string.oxide_set_action_open_logs_detail),
                onClick = {
                    // 日志页压在外壳之上，因此先把抽屉收掉，返回键才落在它上面
                    onDismiss()
                    bridge.openLogView("")
                },
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
            // 游戏目录可能一个都还没有：那一行此时不出现，而不是留一句点下去
            // 会打开空路径的行——文件页拿到空根目录只会报"打不开"，那是误导
            if (oxideStorageFolderActionVisible(currentGamePath)) {
                OxideActionRow(
                    label = stringResource(R.string.oxide_set_action_open_game_folder),
                    hint = currentGamePath,
                    onClick = { bridge.openFileManager(currentGamePath) },
                )
            }
        }
    }
}

/**
 * 「打开游戏目录」这一行是否出现
 *
 * 存储动作必须真的能打开一个存在的目录：游戏目录还没配出来时那一档是空路径，
 * 点下去只会让文件页报"打不开"。因此这一行的可见性是路径本身的纯函数。
 */
internal fun oxideStorageFolderActionVisible(gamePath: String): Boolean = gamePath.isNotBlank()

private data class OxideStorageSizes(
    val data: Long = 0L,
    val game: Long = 0L,
    val cache: Long = 0L,
)

/**
 * 默认那一个游戏目录能不能改名、能不能删
 *
 * 不能：它就是启动器自己的 `.minecraft` 位置，不是数据库里的一项，
 * 删掉它等于让"默认游戏目录"这一档从此不存在。旧界面的那一项也是这么禁用的。
 *
 * 纯函数（[GamePathManager.DEFAULT_ID] 是常量，编译期就内联了，
 * 因此这个判断既不会碰数据库也不会碰磁盘），所以可以直接单测。
 */
internal fun oxideStoragePathEditable(id: String): Boolean = id != GamePathManager.DEFAULT_ID

/** 某一档游戏目录在列表里显示的名字：默认那档用固定文案，其余用用户起的名字 */
@Composable
private fun oxideStoragePathLabel(path: GamePath): String =
    if (path.id == GamePathManager.DEFAULT_ID) {
        stringResource(R.string.versions_manage_game_path_default)
    } else {
        path.title.ifBlank { path.id }
    }

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
    var tab by rememberSaveable { mutableIntStateOf(0) }

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
    }

    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_diagnostics),
        metrics = metrics,
    ) {
        // 崩溃日志存不存在是一次磁盘 stat，不能放在组合阶段里读：
        // 它会在每次重组时都去碰一次文件系统。改成协程里读一次，之后只读这个布尔值。
        var crashLogExists by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            crashLogExists = withContext(Dispatchers.IO) {
                runCatching { PathManager.FILE_CRASH_REPORT.exists() }.getOrDefault(false)
            }
        }
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_view_crash_log),
            hint = PathManager.FILE_CRASH_REPORT.absolutePath,
            enabled = crashLogExists,
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