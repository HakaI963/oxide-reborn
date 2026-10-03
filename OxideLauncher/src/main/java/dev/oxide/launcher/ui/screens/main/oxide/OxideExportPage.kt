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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonSyntaxException
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.R
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.version.download.DownloadFailedException
import dev.oxide.launcher.game.version.export.ExportInfo
import dev.oxide.launcher.game.version.export.PackExporter
import dev.oxide.launcher.game.version.export.PackType
import dev.oxide.launcher.game.version.export.data.FileSelectionData
import dev.oxide.launcher.game.version.export.data.Selected
import dev.oxide.launcher.game.version.export.data.getSelectedFiles
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.platform.getMaxMemoryForSettings
import dev.oxide.launcher.utils.string.isEmptyOrBlank
import dev.oxide.launcher.utils.string.toSingleLine
import dev.oxide.launcher.viewmodel.sendKeepScreen
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException

private const val EXPORT_TAG = "OxideExportPage"

// ---------------------------------------------------------------------------
// 纯逻辑
// ---------------------------------------------------------------------------

/**
 * 默认不参与打包的文件 / 目录名
 *
 * 与旧导出流程的 `selectBlackList` 逐项一致：Fabric 运行库、各家启动器的配置、
 * 存档、Realms 与账号缓存、日志——这些都是玩家自己的东西，不该跟着整合包走。
 */
internal val oxideExportSelectBlackList: List<String> = listOf(
    // Fabric 的一些运行库，不需要打包
    ".fabric",
    // 游戏配置文件默认不选择
    "options.txt",
    // 包含 natives 的文件夹，一般是某些驱动文件，没有打包的必要
    "natives",
    "downloads",
    // 各启动器的配置文件
    "PCL", BuildKeys.LAUNCHER_IDENTIFIER, "fclversion.cfg",
    // 一般来说不需要打包游戏存档
    "saves",
    // Realms 配置
    "realms_persistence.json",
    // 登录过的账号 uuid 缓存
    "usercache.json",
    // XXX 日志
    ".log", "logs",
)

/** 名字里命中黑名单的条目默认不勾选，其余默认勾上 */
internal fun oxideExportDefaultSelected(fileName: String, blackList: List<String>): Boolean =
    blackList.none { fileName.contains(it) }

/** 整合包名称的清洗：压成单行并去掉文件系统不允许的字符，与旧输入框逐字一致 */
internal fun oxideExportSanitizeName(raw: String): String = raw
    .toSingleLine()
    .replace("[\\\\\"?:*/<>|]".toRegex(), "")

/**
 * 换导出类型时重建的默认信息
 *
 * 与旧流程 `ExportModpackViewModel.defaultInfo` 完全一致，包括 `packCurseForge`
 * 那条推导：Modrinth 与 CurseForge 两种格式默认都会带上 CurseForge 远端资源，
 * MCBBS / MultiMC 则不带。
 */
internal fun oxideExportDefaultInfo(
    packType: PackType,
    gamePath: File,
    versionName: String,
    mcVersion: String,
    loader: ExportInfo.LoaderVersion?,
    gameArgs: String,
    javaArgs: String,
): ExportInfo {
    val packModrinth = packType == PackType.Modrinth
    val packCurseForge = packType == PackType.Modrinth || packType == PackType.CurseForge
    return ExportInfo(
        gamePath = gamePath,
        name = versionName,
        version = "1.0",
        mcVersion = mcVersion,
        loader = loader,
        gameArgs = gameArgs,
        javaArgs = javaArgs,
        packType = packType,
        packModrinth = packModrinth,
        packCurseForge = packCurseForge,
    )
}

/** 名称 / 版本必填，格式要求作者时作者也必填 —— 与旧页面那个按钮的启用条件一致 */
internal fun oxideExportCanContinue(info: ExportInfo): Boolean {
    val options = info.packType.options
    return !info.name.isEmptyOrBlank() &&
            !info.version.isEmptyOrBlank() &&
            (!options.requireAuthor || !info.author.isEmptyOrBlank())
}

/** CurseForge 远端资源开关什么时候可以拨：CurseForge 格式恒可，Modrinth 要先勾 Modrinth */
internal fun oxideExportCurseForgeEnabled(info: ExportInfo): Boolean =
    info.packType == PackType.CurseForge ||
            (info.packType == PackType.Modrinth && info.packModrinth)

/** Modrinth 格式下那一项说的是"顺带带上 CurseForge"，其余格式说的是"打包 CurseForge" */
internal fun oxideExportCurseForgeIsExtra(info: ExportInfo): Boolean = info.packType == PackType.Modrinth

/** 这次导出到底会不会去抓远端资源，用来决定要不要显示那两条提示 */
internal fun oxideExportPacksRemote(info: ExportInfo): Boolean = when (info.packType) {
    PackType.Modrinth -> info.packModrinth
    PackType.CurseForge -> info.packCurseForge
    else -> false
}

/** 提示里要念出来的平台名，按勾选顺序 */
internal fun oxideExportRemotePlatforms(info: ExportInfo): List<String> = buildList {
    if (info.packModrinth) add(Platform.MODRINTH.displayName)
    if (info.packCurseForge) add(Platform.CURSEFORGE.displayName)
}

/** 导出流程的三步 */
internal enum class OxideExportStep(val titleRes: Int) {
    Type(R.string.oxide_exp_step_type),
    Info(R.string.oxide_exp_step_info),
    Files(R.string.oxide_exp_step_files),
}

/** 必填项都填好之后才能去挑文件 */
internal fun oxideExportStepReachable(step: OxideExportStep, info: ExportInfo?): Boolean =
    when (step) {
        OxideExportStep.Type -> true
        OxideExportStep.Info -> info != null
        OxideExportStep.Files -> info != null && oxideExportCanContinue(info)
    }

/** 文件树展开后要显示的节点 */
internal sealed interface OxideExportNode {
    val indentation: Int

    /** 懒列表的 key，必须在一次摊平的结果里互不相同 */
    val key: String

    data class Entry(val data: FileSelectionData, override val indentation: Int) : OxideExportNode {
        override val key: String get() = data.file.absolutePath
    }

    /** 空目录只给一行提示，不给一个选不动的复选框 */
    data class EmptyHint(override val key: String, override val indentation: Int) : OxideExportNode
}

/**
 * 把文件树摊平成按显示顺序排好的行
 *
 * 与旧导出页的 `rememberVisibleNodes` 逐条对应：目录先于它的子项，
 * 展开状态由节点自己持有，空目录给一条提示行。
 * 纯函数，因此可以在单测里把顺序与缩进钉死。
 */
internal fun oxideExportVisibleNodes(roots: List<FileSelectionData>): List<OxideExportNode> {
    val result = ArrayList<OxideExportNode>(roots.size)
    val stack = ArrayDeque<Pair<FileSelectionData, Int>>()

    for (i in roots.indices.reversed()) stack.addLast(roots[i] to 0)

    while (stack.isNotEmpty()) {
        val (node, indentation) = stack.removeLast()
        result.add(OxideExportNode.Entry(node, indentation))

        val child = node.child
        if (child != null && node.expand.value) {
            val childIndentation = indentation + 1
            if (child.isEmpty()) {
                result.add(
                    OxideExportNode.EmptyHint(
                        key = "parent:" + node.file.absolutePath + ",indentation=" + indentation,
                        indentation = childIndentation,
                    )
                )
            } else {
                for (i in child.indices.reversed()) stack.addLast(child[i] to childIndentation)
            }
        }
    }

    return result
}

// ---------------------------------------------------------------------------
// 状态机与 ViewModel
// ---------------------------------------------------------------------------

private sealed interface OxideExportOperation {
    data object None : OxideExportOperation
    data object Exporting : OxideExportOperation
    data object Finished : OxideExportOperation
    data class Failed(val throwable: Throwable) : OxideExportOperation
}

/**
 * 整合包导出
 *
 * 逐行对应旧流程的 `ExportModpackViewModel`：同一份 [ExportInfo]、同一套文件树、
 * 同一个 [PackExporter]、同一套选区与取消处理。目录遍历在 `Dispatchers.Default` 上跑，
 * 组合期只读 `allFiles`，因此再大的实例目录也不会卡住界面。
 */
private class OxideExportViewModel(
    private val mcVersion: String,
    private val versionName: String,
    private val gamePath: File,
    private val loader: ExportInfo.LoaderVersion?,
    private val gameArgs: String,
    private val javaArgs: String,
) : ViewModel() {

    private val _allFiles = MutableStateFlow<List<FileSelectionData>>(emptyList())
    val allFiles = _allFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow(false)
    val selectedFiles = _selectedFiles.asStateFlow()

    private val _isRefreshingFiles = MutableStateFlow(false)
    val isRefreshingFiles = _isRefreshingFiles.asStateFlow()

    private val _exportInfo = MutableStateFlow(defaultInfo())
    val exportInfo = _exportInfo.asStateFlow()

    private val _selectingFolder = MutableStateFlow(false)
    val selectingFolder = _selectingFolder.asStateFlow()

    fun editInfo(info: ExportInfo) {
        _exportInfo.update { info }
    }

    fun selectType(packType: PackType) {
        _exportInfo.update { defaultInfo(packType) }
    }

    fun updateSelecting(value: Boolean) {
        _selectingFolder.update { value }
    }

    private fun defaultInfo(packType: PackType = PackType.Modrinth): ExportInfo = oxideExportDefaultInfo(
        packType = packType,
        gamePath = gamePath,
        versionName = versionName,
        mcVersion = mcVersion,
        loader = loader,
        gameArgs = gameArgs,
        javaArgs = javaArgs,
    )

    private val _packExportOperation = MutableStateFlow<OxideExportOperation>(OxideExportOperation.None)
    val packExportOperation = _packExportOperation.asStateFlow()

    private val _packExporter = MutableStateFlow<PackExporter?>(null)
    val packExporter = _packExporter.asStateFlow()

    private val startMutex = Mutex()

    fun startExport(
        version: Version,
        context: Context,
        outputUri: Uri,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val info = startMutex.withLock {
                // 收集最终选中的文件同样要遍历整棵树，因此放在后台线程
                val files = withContext(Dispatchers.Default) {
                    allFiles.value.getSelectedFiles()
                }
                _exportInfo.value.copy(selectedFiles = files)
            }

            _packExportOperation.update { OxideExportOperation.Exporting }
            _packExporter.update {
                PackExporter(
                    context = context,
                    exportInfo = info,
                    scope = viewModelScope,
                ).also {
                    it.startExport(
                        outputUri = outputUri,
                        version = version,
                        onFinished = {
                            _packExporter.update { null }
                            _packExportOperation.update { OxideExportOperation.Finished }
                            onStop()
                            onFinished()
                        },
                        onError = { throwable ->
                            _packExporter.update { null }
                            _packExportOperation.update { OxideExportOperation.Failed(throwable) }
                            onStop()
                        },
                    )
                    onStart()
                }
            }
        }
    }

    fun cancelExport() {
        _packExporter.value?.cancel()
        _packExporter.update { null }
        _packExportOperation.update { OxideExportOperation.None }
    }

    fun updateOperation(operation: OxideExportOperation) {
        _packExportOperation.update { operation }
    }

    private var currentRefreshJob: Job? = null

    /** 刷新可选择的文件列表；[isInit] 为真时按黑名单预选 */
    fun refreshFiles(isInit: Boolean = true) {
        currentRefreshJob?.cancel()
        currentRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                _allFiles.update { emptyList() }
                _selectedFiles.update { false }
                _isRefreshingFiles.update { true }
            }
            val temp = packFiles(gamePath)

            if (isInit) {
                temp.forEach { data ->
                    if (!oxideExportDefaultSelected(data.file.name, oxideExportSelectBlackList)) {
                        data.updateSelectState(Selected.Unselected)
                        return@forEach
                    }
                    data.updateSelectState(Selected.Selected)
                }
            }

            withContext(Dispatchers.Main) {
                _allFiles.update { temp }
                _isRefreshingFiles.update { false }
            }

            refreshRootSelectSuspend()
        }
    }

    private var refreshRootJob: Job? = null

    fun refreshRootSelect() {
        refreshRootJob?.cancel()
        refreshRootJob = viewModelScope.launch(Dispatchers.Default) {
            refreshRootSelectSuspend()
        }
    }

    private suspend fun refreshRootSelectSuspend() {
        _selectedFiles.update { false }
        try {
            val count = FileSelectionData.refreshTreeSelect(_allFiles.value)
            // 有没有勾选任何文件，就是导出按钮能不能按
            _selectedFiles.update { count > 0 }
        } catch (e: CancellationException) {
            // 结构化并发：取消必须继续向上传播
            throw e
        }
    }

    /** 目标版本自己的产物不该被打进整合包 */
    private val packBlackList = listOf(
        "$versionName.json",
        "$versionName.jar",
    )

    private data class StackNode(
        val dir: File,
        val depth: Int,
        val container: MutableList<FileSelectionData>,
    )

    private fun packFiles(root: File): List<FileSelectionData> {
        if (!root.isDirectory) return emptyList()
        val result = mutableListOf<FileSelectionData>()

        val stack = ArrayDeque<StackNode>()
        stack.add(StackNode(root, 1, result))

        while (stack.isNotEmpty()) {
            val (dir, currentDepth, container) = stack.removeLast()
            val files = dir.listFiles() ?: continue

            val tempList = ArrayList<FileSelectionData>(files.size)

            for (file in files) {
                if (packBlackList.any { pattern -> file.name.contains(pattern) }) continue
                // 只有根目录的文件才能设置别名
                val alias = if (currentDepth == 1) aliasOf(file.name) else null

                if (file.isDirectory) {
                    val childList = mutableListOf<FileSelectionData>()
                    tempList.add(FileSelectionData(file = file, alias = alias, child = childList))
                    stack.add(
                        StackNode(
                            dir = file,
                            depth = currentDepth + 1,
                            container = childList,
                        )
                    )
                } else {
                    tempList.add(FileSelectionData(file = file, alias = alias, child = null))
                }
            }

            tempList.sort()
            container.addAll(tempList)
        }

        return result
    }

    /** 玩家看得懂的名称，只给根目录里那几个常见文件夹起别名 */
    private fun aliasOf(fileName: String): Int? = when (fileName) {
        "options.txt" -> R.string.versions_export_alias_options
        "resourcepacks" -> R.string.versions_export_alias_resource_packs
        "mods" -> R.string.versions_export_alias_mods
        "saves" -> R.string.versions_export_alias_saves
        "shaderpacks" -> R.string.versions_export_alias_shaderpacks
        "config" -> R.string.versions_export_alias_config
        BuildKeys.LAUNCHER_IDENTIFIER -> R.string.versions_export_alias_launcher
        else -> null
    }

    override fun onCleared() {
        currentRefreshJob?.cancel()
        currentRefreshJob = null
        refreshRootJob?.cancel()
        refreshRootJob = null
    }
}

@Composable
private fun rememberOxideExportViewModel(version: Version): OxideExportViewModel = viewModel(
    key = version.toString() + "_OxideExport"
) {
    val info = version.getVersionInfo()!!

    OxideExportViewModel(
        mcVersion = info.minecraftVersion,
        versionName = version.getVersionName(),
        gamePath = version.getGameDir(),
        loader = info.primaryLoader?.let { loader ->
            ExportInfo.LoaderVersion(loader.loader, loader.version)
        },
        gameArgs = version.getGameArgs(),
        javaArgs = version.getJvmArgs(),
    )
}

// ---------------------------------------------------------------------------
// 由 metrics 推导出来的尺寸
// ---------------------------------------------------------------------------

/** 文件树里每一级的缩进 */
private val OxideMetrics.exportIndentStep: Dp get() = secControlHeight * 0.9f

/** 三态勾选框的边长，与 OxideFilesPage 里的那一枚同尺寸 */
private val OxideMetrics.exportCheckBox: Dp get() = secControlHeight * 0.38f

// ---------------------------------------------------------------------------
// 界面
// ---------------------------------------------------------------------------

/**
 * 导出整合包
 *
 * 取代 `VersionExportScreen` 及其三个子页（`ExportTypeSelectScreen`、
 * `ExportInfoScreen`、`ExportSelectFilesScreen`）。三件真实的事仍然是这三件：
 * 选导出格式、填元数据、挑要打包的文件。
 *
 * 后端一字未改：[ExportInfo] 仍是导出器真正吃的那份模型，文件树仍是
 * `FileSelectionData` 加 `getSelectedFiles()`，导出仍由同一个 [PackExporter] 执行，
 * 连"哪些文件默认不勾选"的黑名单都逐项照抄。唯一变化的是呈现。
 *
 * 那段必须保留的法律提示（哪些内容不得再分发）也照抄，
 * 用 `versions_export_tip_1` … `versions_export_tip_5` 原文。
 */
@Composable
fun OxideExportPage(
    metrics: OxideMetrics,
    version: Version,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()

    // 版本在这一页被删掉或改名时整块表面跟着退回去，与旧导出页的监听器一致
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(version) {
        val listener = object : suspend () -> Unit {
            override suspend fun invoke() {
                currentOnDismiss()
            }
        }
        VersionsManager.registerListener(listener)
        onDispose { VersionsManager.unregisterListener(listener) }
    }

    val valid = remember(version) { version.isValid() }
    LaunchedEffect(valid) {
        if (!valid) onDismiss()
    }

    val viewModel = rememberOxideExportViewModel(version)

    val exportInfo by viewModel.exportInfo.collectAsStateWithLifecycle()
    val operation by viewModel.packExportOperation.collectAsStateWithLifecycle()
    val packExporter by viewModel.packExporter.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableStateOf(OxideExportStep.Type) }
    // 挑输出目录前的确认：这是唯一一次确认，因此必须明确回答而不是直接弹系统选择器
    var confirmFolder by rememberSaveable { mutableStateOf(false) }

    // 输出目录用系统选择器挑：拿到的 uri 会被持久化授权，之后写文件不再需要弹权限
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data?.let { picked ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            picked
        }
        if (uri != null) {
            viewModel.startExport(
                context = context,
                outputUri = uri,
                version = version,
                onStart = { eventViewModel.sendKeepScreen(true) },
                onStop = { eventViewModel.sendKeepScreen(false) },
                onFinished = { step = OxideExportStep.Type },
            )
        }
        viewModel.updateSelecting(false)
    }

    if (!valid) {
        OxideEmptyState(
            modifier = modifier.fillMaxSize(),
            title = stringResource(R.string.oxide_exp_invalid_instance),
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.pagePaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideIconButton(
                onClick = onDismiss,
                glyph = "←",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_sec_topbar_back)
                ),
            )
            Spacer(Modifier.width(6.dp))
            OxidePageTitle(
                text = stringResource(R.string.oxide_exp_title),
                modifier = Modifier.weight(1f),
                trailing = {
                    OxideBadge(text = version.getVersionName())
                },
            )
        }
        OxideSectionLabel(
            modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
            text = stringResource(R.string.oxide_exp_subtitle, version.getVersionName()),
        )

        Spacer(Modifier.height(metrics.sectionGap))

        OxideStepLayout(
            metrics = metrics,
            titles = OxideExportStep.entries.map { stringResource(it.titleRes) },
            currentIndex = OxideExportStep.entries.indexOf(step),
            reachable = { index -> oxideExportStepReachable(OxideExportStep.entries[index], exportInfo) },
            onSelect = { index -> step = OxideExportStep.entries[index] },
        ) {
            val taskFlow = packExporter?.taskFlow
            when {
                operation is OxideExportOperation.Exporting && taskFlow != null ->
                    OxideTaskFlowPanel(
                        metrics = metrics,
                        title = stringResource(R.string.versions_export),
                        tasks = taskFlow.collectAsStateWithLifecycle().value,
                        logOutput = null,
                        onCancel = {
                            viewModel.cancelExport()
                            eventViewModel.sendKeepScreen(false)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                else -> when (step) {
                    OxideExportStep.Type -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        OxideExportTypeStep(
                            metrics = metrics,
                            current = exportInfo.packType,
                            onPick = { type ->
                                viewModel.selectType(type)
                                step = OxideExportStep.Info
                            },
                        )
                    }

                    OxideExportStep.Info -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        OxideExportInfoStep(
                            metrics = metrics,
                            info = exportInfo,
                            onEdit = viewModel::editInfo,
                            onContinue = {
                                viewModel.refreshFiles()
                                viewModel.updateSelecting(false)
                                step = OxideExportStep.Files
                            },
                        )
                    }

                    OxideExportStep.Files -> OxideExportFilesStep(
                        metrics = metrics,
                        viewModel = viewModel,
                        onPickOutput = { confirmFolder = true },
                    )
                }
            }
        }

        // 完成与失败、以及挑目录的确认，都固定在这一条底部，不会被上面的长列表顶走
        if (confirmFolder) {
            OxideSecConfirmBar(
                metrics = metrics,
                modifier = Modifier.padding(
                    start = metrics.pagePaddingH,
                    end = metrics.pagePaddingH,
                    bottom = metrics.pagePaddingV,
                ),
                text = stringResource(R.string.versions_export_pack_select_folder_message),
                confirmText = stringResource(R.string.oxide_exp_confirm_folder),
                dismissText = stringResource(R.string.generic_cancel),
                onConfirm = {
                    confirmFolder = false
                    viewModel.updateSelecting(true)
                    safLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                },
                onDismiss = { confirmFolder = false },
            )
        } else {
            // operation 是委托属性，不能被智能转换：先绑到局部变量上再 when
            when (val currentOperation = operation) {
                is OxideExportOperation.Finished -> OxideSecConfirmBar(
                    metrics = metrics,
                    modifier = Modifier.padding(
                        start = metrics.pagePaddingH,
                        end = metrics.pagePaddingH,
                        bottom = metrics.pagePaddingV,
                    ),
                    text = stringResource(R.string.versions_export_task_finished),
                    confirmText = stringResource(R.string.generic_done),
                    dismissText = stringResource(R.string.generic_close),
                    onConfirm = { viewModel.updateOperation(OxideExportOperation.None) },
                    onDismiss = { viewModel.updateOperation(OxideExportOperation.None) },
                )

                is OxideExportOperation.Failed -> OxideSecErrorRow(
                    metrics = metrics,
                    modifier = Modifier.padding(
                        start = metrics.pagePaddingH,
                        end = metrics.pagePaddingH,
                        bottom = metrics.pagePaddingV,
                    ),
                    title = stringResource(R.string.versions_export_task_error_title),
                    detail = stringResource(
                        R.string.versions_export_task_error_message,
                    ) + "\n" + exportErrorDetail(currentOperation.throwable),
                    dismissText = stringResource(R.string.generic_confirm),
                    onDismiss = { viewModel.updateOperation(OxideExportOperation.None) },
                )

                else -> Unit
            }
        }
    }
}

/** 第一步：导出格式与那段必须保留的提示 */
@Composable
private fun OxideExportTypeStep(
    metrics: OxideMetrics,
    current: PackType,
    onPick: (PackType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(metrics.cardGap)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(text = stringResource(R.string.generic_tip))
                listOf(
                    R.string.versions_export_tip_1,
                    R.string.versions_export_tip_2,
                    R.string.versions_export_tip_3,
                    R.string.versions_export_tip_4,
                    R.string.versions_export_tip_5,
                ).forEach { tip ->
                    Text(
                        text = stringResource(tip),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    )
                }
            }
        }

        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(text = stringResource(R.string.oxide_exp_type_section))
                PackType.entries.forEach { type ->
                    OxideExportTypeRow(
                        metrics = metrics,
                        title = stringResource(packTypeTitleRes(type)),
                        summary = packTypeSummary(type),
                        selected = type == current,
                        onClick = { onPick(type) },
                    )
                }
            }
        }
    }
}

private fun packTypeTitleRes(type: PackType): Int = when (type) {
    PackType.MCBBS -> R.string.versions_export_type_mcbbs
    PackType.Modrinth -> R.string.versions_export_type_modrinth
    PackType.CurseForge -> R.string.versions_export_type_curseforge
    PackType.MultiMC -> R.string.versions_export_type_multimc
}

@Composable
private fun packTypeSummary(type: PackType): String = when (type) {
    PackType.MCBBS -> stringResource(
        R.string.versions_export_type_mcbbs_summary,
        BuildKeys.LAUNCHER_SHORT_NAME,
    )

    PackType.MultiMC -> stringResource(
        R.string.versions_export_type_multimc_summary,
        BuildKeys.LAUNCHER_SHORT_NAME,
    )

    else -> stringResource(R.string.versions_export_type_summary_common)
}

/** 导出格式的一行：单选，点下去就进入下一步 */
@Composable
private fun OxideExportTypeRow(
    metrics: OxideMetrics,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(metrics.secRowGap))
        OxideExportCheckMark(
            metrics = metrics,
            state = if (selected) Selected.Selected else Selected.Unselected,
        )
    }
}

/** 第二步：元数据 */
@Composable
private fun OxideExportInfoStep(
    metrics: OxideMetrics,
    info: ExportInfo,
    onEdit: (ExportInfo) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val options = info.packType.options
    val maxMemory = remember(context) { getMaxMemoryForSettings(context) }

    Column(verticalArrangement = Arrangement.spacedBy(metrics.cardGap)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(text = stringResource(R.string.oxide_exp_identity_section))
                OxideSecInput(
                    metrics = metrics,
                    label = stringResource(R.string.versions_export_pack_name),
                    value = info.name,
                    onValueChange = { onEdit(info.copy(name = oxideExportSanitizeName(it))) },
                    placeholder = stringResource(R.string.versions_export_pack_name),
                    isError = info.name.isEmptyOrBlank(),
                )
                OxideSecInput(
                    metrics = metrics,
                    label = stringResource(R.string.versions_export_pack_version),
                    value = info.version,
                    onValueChange = { onEdit(info.copy(version = it.toSingleLine())) },
                    placeholder = stringResource(R.string.versions_export_pack_version),
                    isError = info.version.isEmptyOrBlank(),
                )
                if (options.requireAuthor) {
                    OxideSecInput(
                        metrics = metrics,
                        label = stringResource(R.string.versions_export_pack_author),
                        value = info.author,
                        onValueChange = { onEdit(info.copy(author = it.toSingleLine())) },
                        placeholder = stringResource(R.string.versions_export_pack_author),
                        isError = info.author.isEmptyOrBlank(),
                    )
                }
            }
        }

        if (options.requireSummary || options.requireGameArgs || options.requireJavaArgs ||
            options.requireWebsiteUrl || options.requireMinMemory || options.requireMaxMemory
        ) {
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    OxideSectionLabel(text = stringResource(R.string.oxide_exp_content_section))
                    if (options.requireSummary) {
                        OxideSecInput(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_summary),
                            value = info.summary.orEmpty(),
                            onValueChange = { new ->
                                onEdit(info.copy(summary = new.takeIf { it.isNotBlank() }))
                            },
                            placeholder = stringResource(R.string.versions_export_pack_summary_hint),
                            singleLine = false,
                        )
                    }
                    if (options.requireGameArgs) {
                        OxideSecInput(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_game_args),
                            value = info.gameArgs,
                            onValueChange = { onEdit(info.copy(gameArgs = it.toSingleLine())) },
                            placeholder = stringResource(R.string.versions_export_pack_game_args),
                        )
                    }
                    if (options.requireJavaArgs) {
                        OxideSecInput(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_java_args),
                            value = info.javaArgs,
                            onValueChange = { onEdit(info.copy(javaArgs = it.toSingleLine())) },
                            placeholder = stringResource(R.string.versions_export_pack_java_args),
                        )
                    }
                    if (options.requireWebsiteUrl) {
                        OxideSecInput(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_website),
                            value = info.url,
                            onValueChange = { onEdit(info.copy(url = it.toSingleLine())) },
                            placeholder = stringResource(R.string.versions_export_pack_website),
                        )
                    }
                    if (options.requireMinMemory) {
                        OxideIntRow(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_min_memory),
                            value = info.minMemory,
                            range = 0..maxMemory,
                            suffix = "MB",
                            onValueChange = { onEdit(info.copy(minMemory = it)) },
                        )
                    }
                    if (options.requireMaxMemory) {
                        OxideIntRow(
                            metrics = metrics,
                            label = stringResource(R.string.versions_export_pack_max_memory),
                            value = info.maxMemory,
                            range = 0..maxMemory,
                            suffix = "MB",
                            onValueChange = { onEdit(info.copy(maxMemory = it)) },
                        )
                    }
                }
            }
        }

        if (options.requirePackModrinth || options.requirePackCurseForge) {
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    OxideSectionLabel(text = stringResource(R.string.oxide_exp_remote_section))
                    if (options.requirePackModrinth) {
                        OxideToggleRow(
                            label = stringResource(
                                R.string.versions_export_pack_pack_remote,
                                Platform.MODRINTH.displayName,
                            ),
                            checked = info.packModrinth,
                            onCheckedChange = { onEdit(info.copy(packModrinth = it)) },
                        )
                    }
                    if (options.requirePackCurseForge) {
                        OxideToggleRow(
                            label = if (oxideExportCurseForgeIsExtra(info)) {
                                stringResource(R.string.versions_export_pack_also_pack_curseforge)
                            } else {
                                stringResource(
                                    R.string.versions_export_pack_pack_remote,
                                    Platform.CURSEFORGE.displayName,
                                )
                            },
                            hint = stringResource(R.string.oxide_exp_remote_hint),
                            checked = info.packCurseForge,
                            enabled = oxideExportCurseForgeEnabled(info),
                            onCheckedChange = { onEdit(info.copy(packCurseForge = it)) },
                        )
                    }
                    if (oxideExportPacksRemote(info)) {
                        OxideSecErrorRow(
                            metrics = metrics,
                            title = stringResource(R.string.generic_tip),
                            detail = stringResource(
                                R.string.versions_export_tip_remote_1,
                                oxideExportRemotePlatforms(info).joinToString(" "),
                            ) + if (info.packType == PackType.Modrinth) {
                                "\n" + stringResource(R.string.versions_export_tip_remote_2)
                            } else {
                                ""
                            },
                            dismissText = stringResource(R.string.generic_confirm),
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OxideButton(
                text = stringResource(R.string.versions_export_pack_select_files),
                tone = OxideButtonTone.Primary,
                enabled = oxideExportCanContinue(info),
                onClick = onContinue,
            )
        }
    }
}

/** 第三步：挑要打包的文件 */
@Composable
private fun OxideExportFilesStep(
    metrics: OxideMetrics,
    viewModel: OxideExportViewModel,
    onPickOutput: () -> Unit,
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingFiles.collectAsStateWithLifecycle()
    val isSelectingFolder by viewModel.selectingFolder.collectAsStateWithLifecycle()

    // 展开状态在节点自己身上，因此这里只需要一个计数让摊平结果重算一次
    var expandTick by remember { mutableStateOf(0) }
    val nodes = remember(allFiles, expandTick) { oxideExportVisibleNodes(allFiles) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(
                    text = stringResource(R.string.versions_export_pack_files),
                    trailing = {
                        OxideBadge(
                            text = when {
                                isRefreshing -> stringResource(R.string.oxide_exp_files_refreshing)
                                selectedFiles -> stringResource(R.string.oxide_exp_files_selected)
                                else -> stringResource(R.string.oxide_exp_files_none)
                            },
                            tone = if (selectedFiles) {
                                OxideBadgeTone.Active
                            } else {
                                OxideBadgeTone.Neutral
                            },
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.oxide_exp_files_hint),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (allFiles.isEmpty() && !isRefreshing) {
            OxideEmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.oxide_exp_files_empty),
                detail = stringResource(R.string.oxide_exp_files_empty_detail),
                action = {
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_files_refresh),
                        onClick = { viewModel.refreshFiles() },
                        tone = OxideButtonTone.Secondary,
                    )
                },
            )
        } else {
            OxideSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    horizontal = metrics.cardGap,
                    vertical = metrics.secRowGap,
                ),
            ) {
                // 文件可能有成千上万个，因此用懒列表而不是一次性全部排版
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = nodes, key = { node -> node.key }) { node ->
                        OxideExportFileRow(
                            metrics = metrics,
                            node = node,
                            onToggle = { data, checked ->
                                data.updateSelectState(
                                    if (checked) Selected.Selected else Selected.Unselected
                                )
                                viewModel.refreshRootSelect()
                            },
                            onExpandChange = { data, expanded ->
                                data.expandDirs(expanded)
                                expandTick++
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OxideButton(
                text = stringResource(R.string.versions_export_pack_select_output),
                tone = OxideButtonTone.Primary,
                enabled = !isSelectingFolder && selectedFiles,
                onClick = onPickOutput,
            )
        }
    }
}

/** 文件树的一行：展开、勾选、别名与文件名 */
@Composable
private fun OxideExportFileRow(
    metrics: OxideMetrics,
    node: OxideExportNode,
    onToggle: (FileSelectionData, Boolean) -> Unit,
    onExpandChange: (FileSelectionData, Boolean) -> Unit,
) {
    when (node) {
        is OxideExportNode.EmptyHint -> Text(
            text = stringResource(R.string.versions_export_pack_dir_empty),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            modifier = Modifier.padding(start = node.indentation * metrics.exportIndentStep),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        is OxideExportNode.Entry -> {
            val data = node.data
            val selected by data.selected.collectAsStateWithLifecycle()
            val expand by data.expand.collectAsStateWithLifecycle()
            val child = remember(data) { data.child }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = node.indentation * metrics.exportIndentStep)
                    .toggleable(
                        value = selected == Selected.Selected,
                        role = Role.Checkbox,
                        onValueChange = { checked -> onToggle(data, checked) },
                    )
                    .padding(vertical = metrics.secRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (child != null) {
                    OxideIconButton(
                        onClick = { onExpandChange(data, !expand) },
                        glyph = if (expand) "▾" else "▸",
                        size = metrics.secControlHeight,
                        modifier = Modifier.oxideIconDescription(
                            stringResource(
                                if (expand) R.string.generic_collapse else R.string.generic_expand
                            )
                        ),
                    )
                } else {
                    // 仅用于视觉上的对齐
                    Spacer(Modifier.width(metrics.secControlHeight))
                }
                Spacer(Modifier.width(metrics.secRowGap))
                OxideExportCheckMark(metrics = metrics, state = selected)
                Spacer(Modifier.width(metrics.secRowGap))
                data.alias?.let { alias ->
                    Text(
                        text = stringResource(alias),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(metrics.secRowGap))
                }
                Text(
                    text = data.file.name,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 三态勾选框
 *
 * 全选是实心块、半选是一道横杠、没选只有边框，因此三种状态不靠颜色区分；
 * 整行带 `Role.Checkbox`，朗读时状态由行承载。
 */
@Composable
private fun OxideExportCheckMark(metrics: OxideMetrics, state: Selected) {
    val filled = state != Selected.Unselected
    Box(
        modifier = Modifier
            .width(metrics.exportCheckBox)
            .height(metrics.exportCheckBox)
            .clip(Oxide.RadiusBadge)
            .border(
                BorderStroke(1.dp, if (filled) Oxide.FgMuted else Oxide.Line2),
                Oxide.RadiusBadge,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            Selected.Selected -> Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(4.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )

            Selected.Indeterminate -> Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(2.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )

            Selected.Unselected -> Unit
        }
    }
}

/** 与旧导出流程逐项一致的错误翻译 */
@Composable
private fun exportErrorDetail(th: Throwable): String = when (th) {
    is HttpRequestTimeoutException, is SocketTimeoutException, is TimeoutException ->
        stringResource(R.string.error_timeout)

    is UnknownHostException, is UnresolvedAddressException ->
        stringResource(R.string.error_network_unreachable)

    is ConnectException -> stringResource(R.string.error_connection_failed)
    is SerializationException, is JsonSyntaxException -> stringResource(R.string.error_parse_failed)
    is DownloadFailedException -> stringResource(R.string.download_install_error_download_failed)
    else -> {
        Logger.error(EXPORT_TAG, "Failed to export the modpack!", th)
        th.localizedMessage ?: th.message ?: th::class.qualifiedName ?: "Unknown error"
    }
}