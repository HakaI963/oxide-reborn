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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.getProjectByVersion
import dev.oxide.launcher.game.download.assets.platform.getVersionById
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionInfo
import dev.oxide.launcher.game.version.mod.AllModReader
import dev.oxide.launcher.game.version.mod.RemoteMod
import dev.oxide.launcher.game.version.mod.isEnabled
import dev.oxide.launcher.game.version.mod.matchInstalledMods
import dev.oxide.launcher.game.version.mod.modBaseName
import dev.oxide.launcher.game.version.mod.modLoaderVerdict
import dev.oxide.launcher.game.version.mod.scanModFingerprints
import dev.oxide.launcher.game.version.mod.update.ModUpdater
import dev.oxide.launcher.game.version.mod.update.SelectableModManifest
import dev.oxide.launcher.game.version.mod.update.toSelectableList
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.sendKeepScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "OxideMods"

/** 一次最多几个模组在读远端信息，与旧界面一致 */
private const val MODS_REMOTE_CONCURRENCY = 8

/**
 * 详情层里的一条依赖
 *
 * 依赖信息只能来自平台：`PlatformVersion.platformDependencies()`，也就是模组
 * 自己在平台上标注的 required / optional / incompatible / embedded。
 * 本地后端不猜依赖，因此拿不到就是拿不到。
 */
data class OxideModDependency(
    val projectId: String,
    val title: String,
    val type: String,
    /** 这条依赖是不是已经在本实例的 mods 目录里（按平台项目 id 判断） */
    val installed: Boolean,
)

/** 详情层的全部内容 */
data class OxideModDetails(
    val key: String,
    val dependencies: List<OxideModDependency> = emptyList(),
    /** 依赖还在读 */
    val loading: Boolean = false,
    /** 依赖读失败了；"读失败"与"这个模组没声明依赖"必须分开说 */
    val failed: Boolean = false,
)

/**
 * 一次性结果
 *
 * 由状态持有者产出**事实**（几个成功、几个失败），由界面去查字符串。
 * 这样任何一条数字都带着它的语义，而不会出现一句硬编码的英文。
 */
sealed interface OxideModsOutcome {
    /** 没有可以改动的对象：选中的那些已经都在目标状态 */
    data object NothingToDo : OxideModsOutcome

    /** 改了 [changed] 个，其中 [skipped] 个没改成（多半是文件被占用） */
    data class Toggled(val changed: Int, val skipped: Int) : OxideModsOutcome

    /** 删掉了 [deleted] 个，[failed] 个没删掉 */
    data class Deleted(val deleted: Int, val failed: Int) : OxideModsOutcome

    data object Updated : OxideModsOutcome
    data object AlreadyUpToDate : OxideModsOutcome

    /** 这个实例没有可读的版本信息，因此既不能筛版本也不能更新 */
    data object NoVersionInfo : OxideModsOutcome
}

/** 模组表面的全部状态 */
data class OxideModsState(
    val loading: Boolean = true,
    val rows: List<OxideModRow> = emptyList(),
    val minecraftVersion: String? = null,
    val instanceLoaders: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val outcome: OxideModsOutcome? = null,
    /** 待确认删除的行；非空时确认层打开 */
    val pendingDelete: List<OxideModRow> = emptyList(),
    val details: OxideModDetails? = null,
    val updateManifests: List<SelectableModManifest>? = null,
    val updating: Boolean = false,
)

/**
 * 模组表面的状态持有者
 *
 * 三条纪律，每一条都对应一个已经真实发生过的缺陷：
 *
 *  1. **所有磁盘读写都在 [Dispatchers.IO] 上、并且可取消。** 组合与测量期间
 *     这块表面上不会有一次文件 IO 或网络往返。
 *  2. **每次写操作之后都重新扫描目录。** 列表永远来自文件系统，而不是某一份
 *     行快照的局部修改。启用/禁用是一次改名，改名之后"上一次读到的文件名"
 *     就不再指向那个文件了。
 *  3. **一次也不成功的删除 / 改名要说出来。**
 *     `FileUtils.deleteQuietly` 对不存在的路径返回 false 且不抛异常，
 *     把它当成成功，就是"删除点了没反应"的直接来源。
 */
internal class OxideModsViewModel(
    private val version: Version,
    private val eventViewModel: EventViewModel,
) : ViewModel() {

    var state by mutableStateOf(OxideModsState())
        private set

    var query by mutableStateOf("")
        private set
    var stateFilter by mutableStateOf(OxideModState.All)
        private set
    var sort by mutableStateOf(oxideModsDefaultSort().first)
        private set
    var ascending by mutableStateOf(oxideModsDefaultSort().second)
        private set
    var selected by mutableStateOf(emptyList<String>())
        private set

    /** 当前这一批扫描出来的模组对象；行是由它们算出来的 */
    private var remoteMods: List<RemoteMod> = emptyList()

    /** 本实例已装模组的平台项目 id；算一次就够，直到下一次扫描 */
    private var installedProjects: Set<String> = emptySet()

    /** 已经自动请求过远端信息的键；避免"读失败 → 重算行 → 再读"的死循环 */
    private val remoteRequested = mutableSetOf<String>()

    private val modsDir: File by lazy { VersionFolders.MOD.getDir(version.getGameDir()) }

    private var scanJob: Job? = null
    private var updater: ModUpdater? = null
    private var pendingUpdateWait: CancellableContinuation<List<SelectableModManifest>>? = null

    init {
        rescan()
    }

    // ---- 扫描 ---------------------------------------------------------------

    /**
     * 重新读一遍 mods 目录
     *
     * 整个过程在 IO 上、可取消；组合期只读已经拿回来的 [OxideModRow]。
     */
    fun rescan() = scan(showLoading = true)

    /**
     * 重扫目录
     *
     * @param showLoading false 时不把列表换成"正在读"：一次启用/禁用之后的
     *   重扫只是为了让行重新来自文件系统，把整张列表闪一下没有任何好处。
     */
    private fun scan(showLoading: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (showLoading) state = state.copy(loading = true, error = null)
            val info = version.getVersionInfo()
            val read = withContext(Dispatchers.IO) {
                runCatching { AllModReader(modsDir).readAllForRemote() }
            }
            read.onSuccess { mods ->
                remoteMods = mods
                installedProjects = emptySet()
                remoteRequested.clear()
                state = state.copy(
                    loading = false,
                    rows = mods.map { it.toRow(info) },
                    minecraftVersion = info?.minecraftVersion,
                    instanceLoaders = info.instanceLoaderNames(),
                )
                // 选中的是键（去掉 .disabled 的文件名），重扫之后依然有效；
                // 已经被外部删掉的那几条要从选中里去掉
                val alive = mods.mapTo(mutableSetOf()) { it.localMod.file.modBaseName() }
                if (selected.any { it !in alive }) {
                    selected = selected.filter { it in alive }
                }
                // 扫描本身就带起远端匹配，因此界面上不再单独发一次
                loadRemote(state.rows)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Logger.error(TAG, "Failed to read the mods folder.", e)
                state = state.copy(loading = false, error = e.getMessageOrToString())
            }
        }
    }

    // ---- 远端信息 -----------------------------------------------------------

    /**
     * 读这些行的远端信息
     *
     * 与旧界面一致：一次最多八个在读，可取消，逐行带一个"正在读"。
     *
     * 同一个键只自动请求一次（[remoteRequested]）：`RemoteMod.load` 在失败时
     * 不会把 `isLoaded` 置真，而它每次失败都会让行重新算一遍——不设这道闸门，
     * "读失败 → 重算行 → 界面发现还没 projectId → 再读一次"就是一个死循环。
     * 用户显式点的那次刷新走 [refreshRemote]，不受这道闸门限制。
     */
    fun loadRemote(rows: List<OxideModRow>) {
        if (rows.isEmpty()) return
        val fresh = rows.filter { it.checkRemote && it.key !in remoteRequested }
        if (fresh.isEmpty()) return
        fresh.forEach { remoteRequested.add(it.key) }
        val wanted = fresh.mapTo(mutableSetOf()) { it.key }
        viewModelScope.launch {
            val semaphore = Semaphore(MODS_REMOTE_CONCURRENCY)
            remoteMods.filter { it.localMod.file.modBaseName() in wanted }
                .filter { !it.isLoaded && !it.isLoading }
                .forEach { mod ->
                    launch {
                        semaphore.withPermit {
                            runCatching { mod.load(loadFromCache = true) }
                                .onFailure { e ->
                                    if (e !is CancellationException) {
                                        Logger.warning(
                                            TAG,
                                            "Failed to read remote info for ${mod.localMod.name}.",
                                            e,
                                        )
                                    }
                                }
                        }
                    }
                }
            // 远端信息是就地写进 RemoteMod 的可变状态，磁盘没动，因此只重算行
            republishRows()
        }
    }

    /** 强制重新匹配某一个模组在平台上的文件，绕过 MMKV 缓存 */
    fun refreshRemote(row: OxideModRow) {
        viewModelScope.launch {
            val mod = remoteMods.firstOrNull { it.localMod.file.modBaseName() == row.key }
                ?: return@launch
            state = state.copy(busy = true)
            runCatching { mod.load(loadFromCache = false) }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        Logger.warning(TAG, "Failed to refresh remote info for ${row.key}.", e)
                    }
                }
            republishRows()
            state = state.copy(busy = false)
        }
    }

    private fun republishRows() {
        val info = version.getVersionInfo()
        state = state.copy(rows = remoteMods.map { it.toRow(info) })
    }

    // ---- 启用 / 禁用 --------------------------------------------------------

    /**
     * 把某一行切到目标状态
     *
     * 目标是"界面上那个开关应该是什么"，不是"当前状态的反面"。因此重复点同一个
     * 方向是彻底的空操作，而不会因为文件已经在那一边就反向再搬一次。
     */
    fun setEnabled(key: String, enabled: Boolean) = toggle(listOf(key), enabled)

    /** 把选中的若干行切到目标状态；只动状态与目标相反的那些 */
    fun setEnabledForSelection(enabled: Boolean) {
        toggle(oxideModsRowsToToggle(selectedRows(state.rows), enabled).map { it.key }, enabled)
    }

    private fun toggle(keys: List<String>, enabled: Boolean) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(busy = true)
            // 再按磁盘上此刻的真实状态筛一次：行快照是上一次扫描的结果，
            // 期间文件可能已经被外部改过
            val attempted = withContext(Dispatchers.IO) {
                val wanted = keys.toSet()
                remoteMods
                    .filter { it.localMod.file.modBaseName() in wanted }
                    .filter { it.localMod.file.isEnabled() != enabled }
            }
            val changed = withContext(Dispatchers.IO) {
                attempted.count { it.localMod.setEnabled(enabled) }
            }
            val outcome = oxideModBulkOutcome(attempted = attempted.size, changed = changed)
            scan(showLoading = false)
            state = state.copy(
                busy = false,
                error = null,
                outcome = if (attempted.isEmpty()) {
                    OxideModsOutcome.NothingToDo
                } else {
                    OxideModsOutcome.Toggled(
                        changed = outcome.changed,
                        skipped = outcome.skipped,
                    )
                },
            )
        }
    }

    // ---- 删除 ---------------------------------------------------------------

    fun requestDelete(keys: List<String>) {
        val rows = selectedRows(state.rows).filter { it.key in keys }
        if (rows.isEmpty()) return
        state = state.copy(pendingDelete = rows)
    }

    fun requestDeleteSelected() {
        val rows = selectedRows(state.rows)
        if (rows.isEmpty()) return
        state = state.copy(pendingDelete = rows)
    }

    fun dismissDelete() {
        if (state.pendingDelete.isEmpty()) return
        state = state.copy(pendingDelete = emptyList())
    }

    /**
     * 确认删除
     *
     * 走的是 `LocalMod.delete`，也就是**此刻**磁盘上的真实路径；禁用态的模组
     * 在磁盘上叫 `x.jar.disabled`，按 `x.jar` 去删什么也删不掉。删不掉的那几条
     * 会被点名，而不是整批报成功。
     */
    fun confirmDelete() {
        val targets = state.pendingDelete
        if (targets.isEmpty()) return
        state = state.copy(pendingDelete = emptyList(), busy = true)
        viewModelScope.launch {
            // 目标由行里的**实时路径**给出，并且只在 mods 目录之内才动手；
            // 删除本身走 `LocalMod.delete()`，它删的是 [LocalMod.file] 此刻指向的
            // 那个文件。两条对齐不上（文件在确认层打开期间被外部改过）的那些
            // 会被算成失败，而不是悄悄报成功。
            val deleted = withContext(Dispatchers.IO) {
                val wanted = oxideModsDeletePaths(
                    targets.filter { isInsideModsDir(it.path) }
                ).toSet()
                remoteMods.count { mod ->
                    mod.localMod.file.absolutePath in wanted && mod.localMod.delete()
                }
            }
            scan(showLoading = false)
            state = state.copy(
                busy = false,
                error = null,
                outcome = OxideModsOutcome.Deleted(
                    deleted = deleted,
                    failed = (targets.size - deleted).coerceAtLeast(0),
                ),
            )
            val alive = state.rows.mapTo(mutableSetOf()) { it.key }
            selected = selected.filter { it in alive }
        }
    }

    /** 一次删除只允许落在 mods 目录内部 */
    private fun isInsideModsDir(path: String): Boolean = runCatching {
        val dir = modsDir.canonicalFile
        val target = File(path).canonicalFile
        target.parentFile == dir
    }.getOrDefault(false)

    // ---- 更新 ---------------------------------------------------------------

    /**
     * 检查并安装这些模组的新版本
     *
     * 下载前的加载器守卫在 `ModData.checkUpdate` 里：与本实例加载器不符的模组
     * 在那里就拿不到候选版本，一个字节都不会下载。
     */
    fun startUpdate(keys: List<String>) {
        if (state.updating) return
        val info = version.getVersionInfo()
        if (info == null) {
            state = state.copy(outcome = OxideModsOutcome.NoVersionInfo)
            return
        }
        val wanted = keys.toSet()
        val mods = remoteMods.filter { it.localMod.file.modBaseName() in wanted }
        if (mods.isEmpty()) return

        state = state.copy(updating = true, error = null)
        // 更新是一串下载，屏幕会暗下去；与旧界面一样在这期间保持常亮
        eventViewModel.sendKeepScreen(true)
        updater = ModUpdater(
            mods = mods,
            modsDir = modsDir,
            minecraft = info.minecraftVersion,
            modLoader = info.primaryLoader?.loader ?: ModLoader.UNKNOWN,
            scope = viewModelScope,
            waitForUserConfirm = { list ->
                suspendCancellableCoroutine { cont ->
                    state = state.copy(updateManifests = list.toSelectableList())
                    pendingUpdateWait = cont
                }
            },
        ).also { created ->
            created.updateAll(
                onUpdated = {
                    finishUpdate()
                    rescan()
                    state = state.copy(outcome = OxideModsOutcome.Updated)
                },
                onNoModUpdates = {
                    finishUpdate()
                    state = state.copy(outcome = OxideModsOutcome.AlreadyUpToDate)
                },
                onCancelled = { finishUpdate() },
                onError = { th ->
                    finishUpdate()
                    rescan()
                    Logger.error(TAG, "Failed to update mods.", th)
                    state = state.copy(error = th.getMessageOrToString())
                },
            )
        }
    }

    fun confirmUpdate(chosen: List<SelectableModManifest>) {
        val cont = pendingUpdateWait
        pendingUpdateWait = null
        state = state.copy(updateManifests = null)
        cont?.resume(chosen)
    }

    fun cancelUpdate() {
        val cont = pendingUpdateWait
        pendingUpdateWait = null
        state = state.copy(updateManifests = null)
        cont?.resume(emptyList())
    }

    private fun finishUpdate() {
        updater?.cancel()
        updater = null
        pendingUpdateWait = null
        eventViewModel.sendKeepScreen(false)
        state = state.copy(updating = false, updateManifests = null)
    }

    // ---- 详情与依赖 ---------------------------------------------------------

    /**
     * 打开某一行的详情
     *
     * 依赖的读取在 IO 上；读失败与"这个模组没声明依赖"在界面上是两种不同的说法。
     */
    fun openDetails(key: String) {
        val row = state.rows.firstOrNull { it.key == key } ?: return
        state = state.copy(details = OxideModDetails(key = key, loading = true))
        viewModelScope.launch {
            val deps = withContext(Dispatchers.IO) { loadDependencies(row) }
            val current = state.details ?: return@launch
            if (current.key != key) return@launch
            state = state.copy(
                details = current.copy(
                    loading = false,
                    failed = deps == null,
                    dependencies = deps.orEmpty(),
                )
            )
        }
    }

    fun closeDetails() {
        state = state.copy(details = null)
    }

    /**
     * 读这个模组在平台上标注的依赖
     *
     * [dev.oxide.launcher.game.version.mod.ModFile] 里没有依赖，依赖在平台的版本
     * 对象上，因此要按 file id 把那个版本取回来。
     */
    private suspend fun loadDependencies(row: OxideModRow): List<OxideModDependency>? {
        val projectId = row.projectId ?: return emptyList()
        val versionId = row.remoteVersionId ?: return emptyList()
        val platform = row.platformEnum() ?: return emptyList()
        return runCatching {
            val platformVersion = getVersionById(
                versionId = versionId,
                platform = platform,
                projectId = projectId,
                printLog = false,
            )
            val installed = resolveInstalledProjects()
            // 先把 (项目 id, 依赖类型) 收齐，再逐条去查标题：
            // 查标题是一次网络往返，放在 mapNotNull 的 lambda 里会让那个
            // lambda 变成一个挂起闭包，而它本身不是内联的
            val declared = platformVersion.platformDependencies()
                .mapNotNull { dep -> dep.projectId?.let { id -> id to dep.type } }
            declared.map { (id, type) ->
                OxideModDependency(
                    projectId = id,
                    title = depTitle(id, platform),
                    type = type.name.lowercase(),
                    installed = id in installed,
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Logger.warning(TAG, "Failed to read dependencies of ${row.key}.", e)
        }.getOrNull()
    }

    private suspend fun depTitle(projectId: String, platform: Platform): String =
        runCatching {
            getProjectByVersion(projectId = projectId, platform = platform, printLog = false)
                .platformTitle()
        }.getOrDefault(projectId)

    /**
     * 本实例 mods 目录里已经装着的平台项目 id
     *
     * 用的是 `scanModFingerprints` + `matchInstalledMods`——也就是安装链路判定
     * "这条依赖装没装"时用的同一条。算一次就缓存到下一次扫描，因为依赖面板
     * 不该在每次打开时都重新扫一遍盘、打一轮网络。
     */
    private suspend fun resolveInstalledProjects(): Set<String> {
        installedProjects.takeIf { it.isNotEmpty() }?.let { cached ->
            return cached
        }
        val resolved = runCatching {
            val fingerprints = scanModFingerprints(modsDir)
            val ids = mutableSetOf<String>()
            for (candidate in Platform.entries) {
                ids += matchInstalledMods(
                    fingerprints = fingerprints,
                    platform = candidate,
                ).byProject.keys
            }
            ids
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Logger.warning(TAG, "Failed to match installed mods.", e)
        }.getOrDefault(emptySet())
        installedProjects = resolved
        return resolved
    }

    // ---- 选择与筛选 ---------------------------------------------------------

    fun toggleSelected(key: String) {
        selected = oxideModsToggleSelection(selected, key)
    }

    fun toggleSelectAll(visible: List<OxideModRow>) {
        selected = if (oxideModsEverythingSelected(visible, selected)) {
            oxideModsClearVisibleSelection(visible, selected)
        } else {
            oxideModsSelectAll(visible, selected)
        }
    }

    fun clearSelection(visible: List<OxideModRow>) {
        selected = oxideModsClearVisibleSelection(visible, selected)
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun updateStateFilter(value: OxideModState) {
        stateFilter = value
    }

    fun cycleSort() {
        val next = nextOxideModSort(sort, ascending)
        sort = next.first
        ascending = next.second
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    fun dismissOutcome() {
        state = state.copy(outcome = null)
    }

    fun selectedRows(rows: List<OxideModRow>): List<OxideModRow> =
        oxideModsSelectedRows(rows, selected)

    // ---- 行映射 -------------------------------------------------------------

    private fun RemoteMod.toRow(info: VersionInfo?): OxideModRow {
        val local = localMod
        val file = local.file
        val remote = remoteFile
        val project = projectInfo
        val loaders = info?.loaderInfos.orEmpty().map { it.loader }
        return OxideModRow(
            // 键去掉 .disabled：启用/禁用是一次改名，键不能跟着改
            key = file.modBaseName(),
            path = file.absolutePath,
            fileName = file.name,
            displayName = project?.title?.takeIf { it.isNotBlank() } ?: local.name,
            localName = local.name,
            modId = local.id,
            modVersion = local.version,
            authors = local.authors,
            description = local.description,
            sizeBytes = local.fileSize,
            modifiedAt = file.lastModified(),
            // 启用态直接读路径：改名之后任何缓存下来的布尔值都是假的
            enabled = file.isEnabled(),
            notMod = local.notMod,
            localLoader = local.loader.displayName.takeIf { it.isNotBlank() },
            declaredLoaders = remote?.loaders.orEmpty().map { it.getDisplayName() },
            instanceMinecraftVersion = info?.minecraftVersion,
            instanceLoaders = loaders.map { it.displayName }.filter { it.isNotBlank() },
            verdict = modLoaderVerdict(loaders, remote?.loaders.orEmpty().toList()),
            platform = (project?.platform ?: remote?.platform)?.name,
            projectId = project?.id ?: remote?.projectId,
            projectTitle = project?.title,
            projectSlug = project?.slug,
            projectIconUrl = project?.iconUrl,
            remoteVersionId = remote?.id,
            remoteDatePublished = remote?.datePublished,
            iconBytes = local.icon,
            loadingRemote = isLoading,
            checkRemote = local.checkRemote,
        )
    }
}

/** 这个实例自己的加载器显示名；读不到版本信息时是空列表 */
private fun VersionInfo?.instanceLoaderNames(): List<String> =
    this?.loaderInfos.orEmpty().map { it.loader.displayName }.filter { it.isNotBlank() }

/** 把 [OxideModRow.platform] 的名字还原成平台枚举 */
private fun OxideModRow.platformEnum(): Platform? =
    platform?.let { name -> Platform.entries.firstOrNull { it.name == name } }