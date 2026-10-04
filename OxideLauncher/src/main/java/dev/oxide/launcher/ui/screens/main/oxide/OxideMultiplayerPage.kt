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

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.context.COPY_LABEL_SERVER_IP
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.game.version.multiplayer.AllServers
import dev.oxide.launcher.game.version.multiplayer.ServerData
import dev.oxide.launcher.notification.NotificationManager
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.path.URL_EASYTIER
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.terracotta.Terracotta
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.copyText
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.stripColorCodes
import dev.oxide.launcher.viewmodel.LaunchGameViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "OxideMultiplayerPage"

/**
 * 联机面板
 *
 * 取代既有的 Zalith 联机界面。这一块表面是一块居中的紧凑面板（[OxidePanelShell]），
 * 宽高由真实可用区域与 metrics 共同决定（[oxidePanelBoundsFor]），面板内部滚动，
 * 底部一行常驻动作。
 *
 * 它承载的全是启动器这一侧真实存在的东西：
 *
 * - **服务器**：某个实例游戏目录下的 `servers.dat`，走的是游戏侧同一套
 *   [AllServers] / [ServerData]（名称、地址、Ping 出来的状态与人数）；
 *   添加、编辑、删除都写回同一个文件，连接则走 [LaunchGameViewModel.quickPlayServer]，
 *   也就是旧界面"快速游玩服务器"的那同一条启动链路。
 * - **运行时**：开关 Terracotta、自定义 EasyTier 节点、分享联机核心日志，
 *   全部直接读写 [AllSettings] 里既有的设置项。
 * - **说明**：用户须知与房主 / 房客两套步骤。
 *
 * 真正的房间流程——扫码等待、成为房主、复制邀请码、加入房间——
 * 活在游戏进程里的 `ui/screens/game/multiplayer/`，它依赖游戏上下文，
 * 因此**不能**在启动器这一侧重建；这一块面板负责把它需要的开关与说明准备好。
 *
 * Ping 与磁盘读写都不在组合期：列表由一个 ViewModel 在 IO 上加载，
 * 每一行的状态直接读 [ServerData.operation] 那份快照状态。
 */
@Composable
fun OxideMultiplayerPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()
    val hostActions = LocalOxideHostActions.current
    // MainActivity 持有的就是这一个实例，所以从面板里连服务器与从首页按 Play
    // 走的是同一条链路，启动阶段的进度也会落在同一块启动表面上
    val launchViewModel: LaunchGameViewModel = viewModel()

    var notice by remember { mutableStateOf(MultiplayerNotice.None) }
    var editor by remember { mutableStateOf<OxideServerEditor>(OxideServerEditor.None) }
    var deleteTarget by remember { mutableStateOf<ServerData?>(null) }

    // 分享联机核心日志：文件不存在时如实告诉用户，而不是静默失败
    val shareCoreLog = {
        val logFile = PathManager.FILE_TERRACOTTA_LOG
        if (logFile.exists()) {
            shareFile(context, logFile)
        } else {
            eventViewModel.sendToast(androidText(R.string.oxide_sec_mp_share_log_missing))
        }
    }

    val enableTerracotta: (Boolean) -> Unit = { value ->
        AllSettings.enableTerracotta.save(value)
        // 打开之前必须先让人读过用户须知，与旧界面的前置流程一致
        if (value &&
            AllSettings.terracottaNoticeVer.getValue() <
            Terracotta.TERRACOTTA_USER_NOTICE_VERSION
        ) {
            notice = MultiplayerNotice.UserNotice
        }
    }
    val enabled = AllSettings.enableTerracotta.state

    // 服务器列表挂在某一个实例的游戏目录上：servers.dat 是每个实例一份，
    // 因此用户能在这一块面板里换实例，换完立刻读那一份。
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    var pickedVersionPath by rememberSaveable { mutableStateOf<String?>(null) }
    val usableVersions = remember(versions) { versions.filter { it.isValid() } }
    val selectedVersion = remember(usableVersions, pickedVersionPath, currentVersion) {
        pickedVersionPath
            ?.let { path -> usableVersions.firstOrNull { it.getVersionPath().absolutePath == path } }
            ?: currentVersion?.takeIf { candidate -> usableVersions.any { it === candidate } }
    }
    val serverList = rememberOxideServerListViewModel(selectedVersion)
    val servers by (serverList?.servers ?: emptyFlowServers).collectAsStateWithLifecycle()

    // 换实例或换文件之后，编辑器与待确认删除的对象都指向旧列表，必须收掉，
    // 否则用户点"确认"会作用在一个已经不在列表里的服务器上
    LaunchedEffect(selectedVersion) {
        editor = OxideServerEditor.None
        deleteTarget = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounds = oxidePanelBoundsFor(maxWidth, maxHeight, metrics)

        OxidePanelShell(
            title = stringResource(R.string.oxide_sec_mp_title),
            subtitle = stringResource(R.string.oxide_sec_mp_subtitle),
            metrics = metrics,
            bounds = bounds,
            onClose = onDismiss,
            footer = {
                OxideButton(
                    text = stringResource(R.string.generic_refresh),
                    onClick = { serverList?.load() },
                    enabled = serverList != null,
                )
                OxideButton(
                    text = stringResource(R.string.servers_list_add_server),
                    onClick = { editor = OxideServerEditor.Add },
                    tone = OxideButtonTone.Primary,
                    enabled = serverList != null,
                )
            },
        ) {
            when (notice) {
                MultiplayerNotice.None -> Unit

                MultiplayerNotice.UserNotice -> OxideSecConfirmBar(
                    metrics = metrics,
                    text = stringResource(R.string.terracotta_status_uninitialized_desc),
                    confirmText = stringResource(R.string.oxide_sec_mp_notice_accept),
                    dismissText = stringResource(R.string.oxide_sec_mp_notice_decline),
                    onConfirm = {
                        AllSettings.terracottaNoticeVer.save(
                            Terracotta.TERRACOTTA_USER_NOTICE_VERSION
                        )
                        notice = if (!NotificationManager.checkNotificationEnabled(context)) {
                            MultiplayerNotice.NotificationPermission
                        } else {
                            MultiplayerNotice.None
                        }
                    },
                    onDismiss = {
                        AllSettings.enableTerracotta.save(false)
                        notice = MultiplayerNotice.None
                    },
                )

                MultiplayerNotice.NotificationPermission -> OxideMpNotificationBar(
                    metrics = metrics,
                    onHandled = { notice = MultiplayerNotice.None },
                )
            }

            OxideServerSection(
                metrics = metrics,
                versions = usableVersions,
                selected = selectedVersion,
                onSelectVersion = { version ->
                    pickedVersionPath = version.getVersionPath().absolutePath
                },
                servers = servers,
                phase = serverList?.phase ?: OxideServerListPhase.NoInstance,
                saving = serverList?.saving == true,
                editor = editor,
                onEditor = { editor = it },
                deleteTarget = deleteTarget,
                onDeleteTarget = { deleteTarget = it },
                onConnect = { server ->
                    selectedVersion?.let { version ->
                        launchViewModel.quickPlayServer(version, server.originIp)
                    }
                },
                onRefresh = { server -> serverList?.ping(server, force = true) },
                onCopy = { server ->
                    serverList?.copyAddress(context, server.originIp)
                },
                onSave = { name, address ->
                    when (val target = editor) {
                        is OxideServerEditor.Add -> serverList?.addServer(name, address)
                        is OxideServerEditor.Edit -> serverList?.updateServer(target.server, name, address)
                        OxideServerEditor.None -> Unit
                    }
                    editor = OxideServerEditor.None
                },
                onDeleteConfirmed = { server ->
                    deleteTarget = null
                    serverList?.deleteServer(server)
                },
            )

            OxideMpRuntimeCard(
                metrics = metrics,
                enabled = enabled,
                nodesEnabled = AllSettings.enableTerracottaNodes.state,
                nodes = AllSettings.terracottaNodes.state,
                onEnable = enableTerracotta,
                onNodesEnabled = { value ->
                    AllSettings.enableTerracottaNodes.save(value)
                },
                onNodes = { value -> AllSettings.terracottaNodes.save(value) },
                onShareLog = shareCoreLog,
                onOpenEasyTier = { hostActions.openLink(URL_EASYTIER) },
            )

            OxideMpGuideCard(metrics = metrics)
        }
    }
}

/** 打开联机之前的两个前置流程 */
private enum class MultiplayerNotice { None, UserNotice, NotificationPermission }

/**
 * 通知权限
 *
 * 与 [dev.oxide.launcher.ui.components.NotificationCheck] 完全同一套判定：
 * 13 以下跳系统设置让用户自己开，13 及以上直接申请。
 * 只是把 Material 弹窗换成就地确认条，留在 Oxide 的语言里。
 */
@Composable
private fun OxideMpNotificationBar(
    metrics: OxideMetrics,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onHandled() }

    OxideSecConfirmBar(
        metrics = metrics,
        text = stringResource(R.string.notification_data_terracotta_message),
        confirmText = stringResource(R.string.notification_request),
        dismissText = stringResource(R.string.generic_ignore),
        onConfirm = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManager.openNotificationSettings(context)
                onHandled()
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onDismiss = { onHandled() },
    )
}

// ---------------------------------------------------------------------------
// 服务器列表
// ---------------------------------------------------------------------------

/** 列表处在哪一步：读文件、已读完，或者根本没有实例可读 */
internal enum class OxideServerListPhase {
    /** 没有任何可用实例，servers.dat 无处可读 */
    NoInstance,

    /** 正在读磁盘 */
    Loading,

    /** 文件已读完，列表可以显示 */
    Loaded,
}

/** 服务器在列表里的真实状态，逐条对应 [ServerData.Operation] */
internal enum class OxideServerStatus {
    /** 还没发起过 Ping */
    Unpinged,

    /** Ping 进行中 */
    Checking,

    /** Ping 成功，[ServerPingResult.status] 里有真的数据 */
    Online,

    /** Ping 失败：连不上、超时或者返回了看不懂的数据 */
    Unreachable,
}

/** 把 [ServerData.Operation] 映射成列表上要显示的状态。纯函数，不碰 IO */
internal fun oxideServerStatusOf(operation: ServerData.Operation): OxideServerStatus = when (operation) {
    is ServerData.Operation.Loading -> OxideServerStatus.Checking
    is ServerData.Operation.Loaded -> OxideServerStatus.Online
    is ServerData.Operation.Failed -> OxideServerStatus.Unreachable
}

/** 状态那一枚徽章的文案。状态因此不只靠颜色说话，读屏也能听到 */
internal fun oxideServerStatusLabelRes(status: OxideServerStatus): Int = when (status) {
    OxideServerStatus.Unpinged -> R.string.oxide_mp_server_status_unpinged
    OxideServerStatus.Checking -> R.string.oxide_mp_server_status_checking
    OxideServerStatus.Online -> R.string.oxide_mp_server_status_online
    OxideServerStatus.Unreachable -> R.string.oxide_mp_server_status_unreachable
}

/**
 * 状态徽章的语气
 *
 * 在线是"强调"、连不上是"警示"、其余是中性：三个语气在明暗两套配色下
 * 亮度都不同，但真正区分状态的是上面那一句文案，颜色只是第二重信息。
 */
internal fun oxideServerStatusTone(status: OxideServerStatus): OxideBadgeTone = when (status) {
    OxideServerStatus.Online -> OxideBadgeTone.Active
    OxideServerStatus.Unreachable -> OxideBadgeTone.Warn
    OxideServerStatus.Unpinged, OxideServerStatus.Checking -> OxideBadgeTone.Neutral
}

/**
 * 在线人数那一行
 *
 * 只用 Ping 回来的真值：`online < 0` 是原版协议里"服务器没有定义人数"的写法，
 * 这时不能显示成 "-1/20"。`max <= 0` 同理只显示在线人数。
 */
internal fun oxideServerPlayerSummary(online: Int, max: Int): String = when {
    online < 0 -> ""
    max > 0 -> "$online/$max"
    else -> "$online"
}

/** 正在编辑（或者正在新增）哪一个服务器 */
internal sealed interface OxideServerEditor {
    data object None : OxideServerEditor

    data object Add : OxideServerEditor

    data class Edit(val server: ServerData) : OxideServerEditor
}

/**
 * 服务器列表的宿主
 *
 * 读盘、Ping、写盘全在 [OxideServerListViewModel] 的 IO 上，这里只负责画。
 * 每个实例一份 `servers.dat`，所以换实例就是换这个 ViewModel 的 key。
 */
@Composable
private fun rememberOxideServerListViewModel(version: Version?): OxideServerListViewModel? {
    val target = version ?: return null
    val path = target.getVersionPath().absolutePath
    return viewModel(key = "OxideServerList_$path") {
        OxideServerListViewModel(gamePath = target.getGameDir())
    }
}

/** 没有实例时给界面一个空列表，省得整条链路上到处判空 */
private val emptyFlowServers = MutableStateFlow<List<ServerData>>(emptyList())

/**
 * 服务器列表的 ViewModel
 *
 * 与旧界面里那一个同名职责的 ViewModel 走同一套后端：[AllServers] 读 `servers.dat`、
 * 写回同一个目录，[ServerData.load] 负责 Ping 与取回图标。
 * 增删改都排在一把互斥锁后面串行执行，所以"改完立刻再改"不会把两个写盘撞在一起。
 *
 * 刻意不把 [AllServers] 做成全局单例：它持有的是一份内存里的列表，
 * 两个界面同时持有同一个实例会各自写盘。
 */
internal class OxideServerListViewModel(gamePath: File) : ViewModel() {
    private val dataFile = File(gamePath, "servers.dat")
    private val allServers = AllServers()
    private val mutex = Mutex()

    private val _phase = MutableStateFlow(OxideServerListPhase.Loading)
    val phase = _phase.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerData>>(emptyList())
    val servers = _servers.asStateFlow()

    /** 正在写盘：写盘期间禁掉增删改，避免连点两次写两个队列 */
    var saving by mutableStateOf(false)
        private set

    private val pingJobs = mutableMapOf<ServerData, Job>()

    init {
        load()
    }

    /** 重新读一遍磁盘，并对读到的每一个服务器发起一次 Ping */
    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = mutex.withLock {
                _phase.value = OxideServerListPhase.Loading
                allServers.loadServers(dataFile)
                val list = allServers.serverList
                _servers.value = list
                _phase.value = OxideServerListPhase.Loaded
                list
            }
            // Ping 放在锁外面：它要占住网络十几秒，排队等它会把写盘一起堵住
            pingEach(loaded)
        }
    }

    /** 新增一个服务器：名字空着就用旧界面那一条默认名 */
    fun addServer(name: String, address: String) {
        saveServers(reason = "added server") {
            allServers.addServer(ServerData(name = name, originIp = address))
        }
    }

    /** 改名与改地址，落到同一个 [ServerData] 上，写回文件 */
    fun updateServer(server: ServerData, name: String, address: String) {
        saveServers(reason = "edited server") {
            server.name = name
            server.originIp = address
        }
    }

    /** 删除一个服务器，同时收掉它那个还在跑的 Ping */
    fun deleteServer(server: ServerData) {
        saveServers(reason = "deleted server") {
            allServers.removeServer(server)
            pingJobs.remove(server)?.cancel()
        }
    }

    /**
     * Ping 一个服务器
     *
     * [force] 为真时先收掉上一次的任务——地址刚改过时，不这么做会看到旧地址的结果。
     */
    fun ping(server: ServerData, force: Boolean = false) {
        if (force) {
            pingJobs.remove(server)?.cancel()
        }
        if (pingJobs.containsKey(server)) return
        pingJobs[server] = viewModelScope.launch {
            try {
                server.load { reason ->
                    // Ping 顺带取回了新图标：把它写回磁盘，列表里的图标才留得住
                    saveServers(reason = reason, reload = false)
                }
            } finally {
                pingJobs.remove(server)
            }
        }
    }

    /** 复制服务器地址到剪贴板，走启动器统一的那一套提示 */
    fun copyAddress(context: Context, address: String) {
        viewModelScope.launch(Dispatchers.Main) {
            copyText(label = COPY_LABEL_SERVER_IP, text = address, context = context)
        }
    }

    private fun pingEach(servers: List<ServerData>) {
        servers.forEach { server -> ping(server) }
    }

    private fun saveServers(
        reason: String,
        reload: Boolean = true,
        beforeSave: suspend () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                Logger.debug(LOG_TAG, "Saving the server list, reason = $reason, reload = $reload")
                withContext(Dispatchers.Main) { saving = true }
                runCatching {
                    beforeSave()
                    allServers.save(gamePath)
                    if (reload) {
                        _servers.value = allServers.serverList
                    }
                }.onFailure { e ->
                    Logger.error(LOG_TAG, "Couldn't save the server list.", e)
                }
                withContext(Dispatchers.Main) { saving = false }
            }
        }
    }

    override fun onCleared() {
        pingJobs.values.forEach { it.cancel() }
        pingJobs.clear()
        super.onCleared()
    }
}

/** 服务器那一段：换实例、列表本体、增删改的表单 */
@Composable
private fun OxideServerSection(
    metrics: OxideMetrics,
    versions: List<Version>,
    selected: Version?,
    onSelectVersion: (Version) -> Unit,
    servers: List<ServerData>,
    phase: OxideServerListPhase,
    saving: Boolean,
    editor: OxideServerEditor,
    onEditor: (OxideServerEditor) -> Unit,
    deleteTarget: ServerData?,
    onDeleteTarget: (ServerData?) -> Unit,
    onConnect: (ServerData) -> Unit,
    onRefresh: (ServerData) -> Unit,
    onCopy: (ServerData) -> Unit,
    onSave: (String, String) -> Unit,
    onDeleteConfirmed: (ServerData) -> Unit,
) {
    OxideSection(
        title = stringResource(R.string.servers_list),
        trailing = if (phase == OxideServerListPhase.Loaded && servers.isNotEmpty()) {
            { OxideBadge(text = servers.size.toString()) }
        } else {
            null
        },
    ) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                if (versions.isEmpty()) {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_mp_no_instance),
                        detail = stringResource(R.string.oxide_mp_no_instance_detail),
                    )
                    return@Column
                }

                // servers.dat 是每个实例一份，所以这一行选的是"正在管哪一个实例的列表"
                OxideSettingRow(
                    label = stringResource(R.string.oxide_mp_instance),
                    hint = selected?.getGameDir()?.absolutePath,
                    value = selected?.getVersionName(),
                    enabled = !saving,
                    trailing = {
                        OxideDropdown(
                            label = "",
                            options = versions.map { it.getVersionName() },
                            selectedIndex = versions.indexOfFirst { it === selected },
                            enabled = !saving,
                            onSelect = { index ->
                                versions.getOrNull(index)?.let(onSelectVersion)
                            },
                            modifier = Modifier.width(metrics.selectorWidth),
                        )
                    },
                )

                if (editor != OxideServerEditor.None) {
                    OxideSecDivider()
                    OxideServerEditorCard(
                        metrics = metrics,
                        editor = editor,
                        onSave = onSave,
                        onDismiss = { onEditor(OxideServerEditor.None) },
                    )
                }

                deleteTarget?.let { target ->
                    OxideSecConfirmBar(
                        metrics = metrics,
                        text = stringResource(
                            R.string.servers_list_delete_server_text,
                            target.name.stripColorCodes(),
                        ),
                        confirmText = stringResource(R.string.servers_list_delete_server),
                        dismissText = stringResource(R.string.generic_cancel),
                        onConfirm = { onDeleteConfirmed(target) },
                        onDismiss = { onDeleteTarget(null) },
                    )
                }

                OxideSecDivider()

                when {
                    phase == OxideServerListPhase.NoInstance -> OxideEmptyState(
                        title = stringResource(R.string.oxide_mp_pick_instance),
                    )

                    phase == OxideServerListPhase.Loading -> OxideLoadingRow(
                        text = stringResource(R.string.oxide_mp_servers_loading)
                    )

                    servers.isEmpty() -> OxideEmptyState(
                        title = stringResource(R.string.servers_list_no_servers),
                        detail = stringResource(R.string.oxide_mp_servers_empty_detail),
                    )

                    else -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    ) {
                        // key 用地址：编辑之后地址变了，这一行因此是"另一行"而不是同一行改了内容，
                        // 正在编辑的表单与这一行不会互相串状态
                        servers.forEach { server ->
                            key(server.originIp) {
                                OxideServerRow(
                                    metrics = metrics,
                                    server = server,
                                    onConnect = { onConnect(server) },
                                    onRefresh = { onRefresh(server) },
                                    onCopy = { onCopy(server) },
                                    onEdit = { onEditor(OxideServerEditor.Edit(server)) },
                                    onDelete = { onDeleteTarget(server) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 新增 / 编辑一个服务器
 *
 * 校验与旧界面的 `ServerEditDialog` 一致：只查地址空不空，名字空了就用默认名。
 */
@Composable
private fun OxideServerEditorCard(
    metrics: OxideMetrics,
    editor: OxideServerEditor,
    onSave: (name: String, address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.servers_list_add_server_default_name)
    val initial = editor as? OxideServerEditor.Edit
    var name by remember(editor) {
        mutableStateOf(initial?.server?.name?.takeIf { it.isNotEmpty() } ?: defaultName)
    }
    var address by remember(editor) { mutableStateOf(initial?.server?.originIp.orEmpty()) }
    val addressBlank = remember(address) { address.isEmpty() || address.isBlank() }

    OxideSectionLabel(
        text = stringResource(
            if (initial == null) {
                R.string.servers_list_add_server
            } else {
                R.string.servers_list_edit_server
            }
        )
    )
    OxideSecInput(
        metrics = metrics,
        value = name,
        onValueChange = { name = it },
        placeholder = defaultName,
        label = stringResource(R.string.servers_list_add_server_name),
    )
    OxideSecInput(
        metrics = metrics,
        value = address,
        onValueChange = { address = it },
        placeholder = stringResource(R.string.servers_list_add_server_ip),
        label = stringResource(R.string.servers_list_add_server_ip),
        isError = addressBlank,
    )
    if (addressBlank) {
        Text(
            text = stringResource(R.string.generic_cannot_empty),
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.generic_confirm),
            onClick = {
                onSave(name.ifEmpty { defaultName }, address.trim())
            },
            enabled = !addressBlank,
            tone = OxideButtonTone.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 列表里的一行
 *
 * 整行点 = 连接这个服务器（也就是那一块面板上最大的一块可点区域）；
 * 右侧四个小按钮分别是重 Ping、复制地址、编辑与删除。
 * 状态由 [OxideServerStatus] 决定，并且写成文字，不只靠颜色。
 */
@Composable
private fun OxideServerRow(
    metrics: OxideMetrics,
    server: ServerData,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = oxideServerStatusOf(server.operation)
    val name = server.name.stripColorCodes()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Oxide.RadiusSmall)
                // 整行可点，因此"连接"落在一块足够大的区域上，而不是只靠一个小图标按钮；
                // selectable 同时把这一行以 Role.Button 交给无障碍服务
                .selectable(
                    selected = false,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onConnect,
                )
                .padding(vertical = metrics.secRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideSecAvatar(
                initial = name.trim().take(1).uppercase(),
                description = name,
                size = Oxide.MarkSize,
            )
            Spacer(Modifier.width(metrics.secRowGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = server.originIp,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(metrics.secRowGap))
            OxideBadge(
                text = stringResource(oxideServerStatusLabelRes(status)),
                tone = oxideServerStatusTone(status),
            )
        }

        val loaded = server.operation as? ServerData.Operation.Loaded
        val players = loaded?.let { it.result.status.players }
        val detail = when {
            players != null && oxideServerPlayerSummary(players.online, players.max).isNotEmpty() ->
                stringResource(
                    R.string.oxide_mp_server_players,
                    players.online,
                    players.max,
                )

            else -> null
        }
        if (detail != null) {
            Text(
                text = detail,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = metrics.secRowGap,
                alignment = Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideButton(
                text = stringResource(R.string.oxide_mp_server_connect),
                onClick = onConnect,
                tone = OxideButtonTone.Primary,
            )
            OxideIconButton(
                onClick = onRefresh,
                glyph = "↻",
                enabled = status != OxideServerStatus.Checking,
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.generic_refresh)
                ),
            )
            OxideIconButton(
                onClick = onCopy,
                glyph = "⧉",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.servers_list_copy_server_address)
                ),
            )
            OxideIconButton(
                onClick = onEdit,
                glyph = "✎",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.servers_list_edit_server)
                ),
            )
            OxideIconButton(
                onClick = onDelete,
                glyph = "✕",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.generic_delete)
                ),
            )
        }
    }
}

/**
 * 运行时设置
 *
 * 三件事全部落到真实的设置项上：联机总开关、自定义节点列表（含它自己的开关）、
 * 分享联机核心日志。自定义节点只有在联机开着**且**它自己的开关也开着时才可编辑，
 * 否则那一整块输入框会消失，而不是留下一排不能动的框。
 */
@Composable
private fun OxideMpRuntimeCard(
    metrics: OxideMetrics,
    enabled: Boolean,
    nodesEnabled: Boolean,
    nodes: String,
    onEnable: (Boolean) -> Unit,
    onNodesEnabled: (Boolean) -> Unit,
    onNodes: (String) -> Unit,
    onShareLog: () -> Unit,
    onOpenEasyTier: () -> Unit,
) {
    OxideSection(title = stringResource(R.string.oxide_sec_mp_section_runtime)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideMpToggleRow(
                    label = stringResource(R.string.oxide_sec_mp_enable),
                    hint = stringResource(R.string.oxide_sec_mp_enable_hint),
                    checked = enabled,
                    onCheckedChange = onEnable,
                )
                OxideSecDivider()
                OxideMpToggleRow(
                    label = stringResource(R.string.oxide_sec_mp_nodes),
                    hint = stringResource(R.string.oxide_sec_mp_nodes_hint),
                    checked = nodesEnabled,
                    enabled = enabled,
                    onCheckedChange = onNodesEnabled,
                )
                if (enabled && nodesEnabled) {
                    OxideSecDivider()
                    OxideSecInput(
                        metrics = metrics,
                        value = nodes,
                        onValueChange = onNodes,
                        placeholder = stringResource(R.string.oxide_sec_mp_nodes_placeholder),
                        singleLine = false,
                    )
                }
                OxideSecDivider()
                OxideSettingRow(
                    label = stringResource(R.string.oxide_sec_mp_share_log),
                    hint = stringResource(R.string.oxide_sec_mp_share_log_hint),
                    enabled = enabled,
                    onClick = onShareLog,
                )
                OxideSettingRow(
                    label = stringResource(R.string.oxide_sec_mp_easytier),
                    hint = stringResource(R.string.oxide_sec_mp_easytier_hint),
                    onClick = onOpenEasyTier,
                    trailing = {
                        Text(
                            text = "›",
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.Body.fontSize,
                            lineHeight = Oxide.Type.Body.lineHeight,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

/**
 * 开关行
 *
 * 整行可点、开关状态由整行的 `Role.Switch` 与 checked 暴露给无障碍服务；
 * 开关本身不再重复朗读，避免同一信息被念两遍。
 */
@Composable
private fun OxideMpToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
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
            Box(modifier = Modifier.clearAndSetSemantics { }) {
                OxideToggle(
                    checked = checked,
                    // 禁用行必须整个按下去都不写设置：只关掉整行的 toggleable
                    会让开关自己仍然能点，那样值会悄悄变掉而整行毫无反应
                    onCheckedChange = { next ->
                        if (enabled) onCheckedChange(next)
                    },
                )
            }
        },
    )
}

/**
 * 说明：用户须知 / 房主 / 房客，三个页签
 *
 * 这里刻意没有再开一个滚动容器：面板内部已经有且只有那一个纵向滚动，
 * 说明文字直接流进去，和上面两段一起滚。
 */
@Composable
private fun OxideMpGuideCard(metrics: OxideMetrics) {
    val tabs = listOf(
        stringResource(R.string.oxide_sec_mp_tab_notice),
        stringResource(R.string.oxide_sec_mp_tab_host),
        stringResource(R.string.oxide_sec_mp_tab_guest),
    )
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    OxideSection(title = stringResource(R.string.oxide_mp_section_guide)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        OxideSecChip(
                            label = tab,
                            selected = index == tabIndex,
                            metrics = metrics,
                            onClick = { tabIndex = index },
                        )
                    }
                }
                OxideSecDivider()

                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    when (tabIndex) {
                        1 -> {
                            OxideMpGuideBlock(
                                title = stringResource(R.string.terracotta_tutorial_host_tip),
                                lines = listOf(
                                    R.string.terracotta_tutorial_step_enable_multiplayer,
                                    R.string.terracotta_tutorial_step_open_multiplayer_menu,
                                    R.string.terracotta_tutorial_host_step_become_host,
                                    R.string.terracotta_tutorial_host_step_open_lan,
                                    R.string.terracotta_tutorial_step_vpn_permission,
                                    R.string.terracotta_tutorial_host_step_copy_invite,
                                    R.string.terracotta_tutorial_host_step_send_invite,
                                ),
                            )
                            OxideMpGuideBlock(
                                title = stringResource(R.string.terracotta_tutorial_note_title),
                                lines = listOf(
                                    R.string.terracotta_tutorial_step_offline_account_support,
                                    R.string.terracotta_tutorial_step_interoperability,
                                ),
                            )
                        }

                        2 -> {
                            OxideMpGuideBlock(
                                title = stringResource(R.string.terracotta_tutorial_guest_tip),
                                lines = listOf(
                                    R.string.terracotta_tutorial_step_enable_multiplayer,
                                    R.string.terracotta_tutorial_step_open_multiplayer_menu,
                                    R.string.terracotta_tutorial_guest_step_become_guest,
                                    R.string.terracotta_tutorial_step_vpn_permission,
                                    R.string.terracotta_tutorial_guest_step_join_room,
                                ),
                            )
                            OxideMpGuideBlock(
                                title = stringResource(R.string.terracotta_tutorial_note_title),
                                lines = listOf(
                                    R.string.terracotta_tutorial_step_offline_account_support,
                                    R.string.terracotta_tutorial_step_interoperability,
                                    R.string.terracotta_tutorial_guest_step_alternate_server,
                                ),
                            )
                        }

                        else -> OxideMpGuideBlock(
                            title = stringResource(R.string.terracotta_confirm_title),
                            lines = listOf(
                                R.string.terracotta_confirm_software,
                                R.string.terracotta_confirm_p2p,
                                R.string.terracotta_confirm_law,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 说明里的一个小节：标题 + 若干步 */
@Composable
private fun OxideMpGuideBlock(title: String, lines: List<Int>) {
    Column {
        Text(
            text = title,
            color = Oxide.FgStrong,
            fontSize = Oxide.Type.Title.fontSize,
            lineHeight = Oxide.Type.Title.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        lines.forEach { lineRes ->
            Text(
                text = "· " + stringResource(lineRes),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
            )
        }
        Spacer(Modifier.height(5.dp))
    }
}