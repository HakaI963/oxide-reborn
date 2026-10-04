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

import androidx.compose.runtime.mutableIntStateOf
import dev.oxide.launcher.utils.file.checkFilenameValidity
import dev.oxide.launcher.utils.file.InvalidFilenameException
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.game.download.assets.DependencyRequest
import dev.oxide.launcher.game.download.assets.downloadDependenciesForVersions
import dev.oxide.launcher.game.download.assets.downloadSingleForVersions
import dev.oxide.launcher.game.download.assets.mapExceptionToMessage
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformProject
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchData
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchFilter
import dev.oxide.launcher.game.download.assets.platform.PlatformSortField
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.curseForgeModLoaderFilters
import dev.oxide.launcher.game.download.assets.platform.getProjectByVersion
import dev.oxide.launcher.game.download.assets.platform.getVersionById
import dev.oxide.launcher.game.download.assets.platform.getVersions
import dev.oxide.launcher.game.download.assets.platform.modrinth.models.modrinthModLoaderFilters
import dev.oxide.launcher.game.download.assets.platform.searchAssets
import dev.oxide.launcher.game.download.assets.utils.ModTranslations
import dev.oxide.launcher.game.download.assets.utils.getMcmodTitle
import dev.oxide.launcher.game.download.modpack.install.ModPackInfo
import dev.oxide.launcher.game.download.modpack.install.ModPackInstaller
import dev.oxide.launcher.game.path.getGameHome
import dev.oxide.launcher.game.version.download.DOWNLOADER_TAG
import dev.oxide.launcher.game.version.download.DownloadMode
import dev.oxide.launcher.game.version.download.MinecraftDownloader
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.game.versioninfo.MinecraftVersion
import dev.oxide.launcher.game.versioninfo.MinecraftVersions
import dev.oxide.launcher.game.versioninfo.popularVersions
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.unit.EnumSettingUnit
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.imePanAnchor
import dev.oxide.launcher.ui.screens.content.download.DownloadModViewModel
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsIcon
import dev.oxide.launcher.ui.screens.content.download.assets.elements.initAll
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.toAndroidString
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.formatNumberByLocale
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "OxideDiscoverPage"

/** 同时读取的依赖数量上限，防止异常元数据把确认界面撑爆 */
private const val MAX_DISCOVER_DEPENDENCIES = 64

// ---------------------------------------------------------------------------
// 尺寸：一律由 metrics 推导，页面不写死任何一个用于布局的 dp
// ---------------------------------------------------------------------------

/** 控件高度，与 [OxideDropdown] 内部的高度保持一致 */
private val OxideMetrics.controlHeight: Dp get() = topBarHeight * 0.61f

/** 控件内部的横向留白 */
private val OxideMetrics.controlPadding: Dp get() = pagePaddingH * 0.3f

/**
 * 左侧类别栏宽度：参考稿 `.discoverLayout{grid-template-columns:145px minmax(0,1fr)}`
 * 把它写死成 145px，不随侧栏宽度或可用宽度变化。
 *
 * 之前这里按 `sidebarWidth * 0.58` 推导，只有 107dp，比参考稿窄了一大截；
 * 固定成 145dp 后在 640x360 这一档（内容区 453dp）右侧仍剩 299dp，两列结果放得下。
 */
/** The reference fixes this rail at 145px; it has to follow the interface scale like every
 *  other dimension, otherwise raising the scale silently shrinks only this one column. */
internal val OxideMetrics.categoryRailWidth: Dp get() = 145.dp * guiScale

/** 类别栏中一项的高度 */
private val OxideMetrics.categoryItemHeight: Dp get() = navItemHeight * 0.79f

/** 结果卡片左侧图标的边长 */
private val OxideMetrics.resultIconSize: Dp get() = navItemHeight * 0.82f

/** 筛选下拉的宽度 */
private val OxideMetrics.filterControlWidth: Dp get() = cardMinWidth * 0.5f

/** 搜索框的宽度 */
private val OxideMetrics.searchFieldWidth: Dp get() = cardMinWidth * 0.86f

// ---------------------------------------------------------------------------
// 数据层
// ---------------------------------------------------------------------------

/**
 * 类别栏里的一类资源
 *
 * 全部来自真实的 [PlatformClasses]，平台与加载器过滤能力也和资源搜索页一致：
 * 存档只有 CurseForge 一个平台，只有模组与整合包才按加载器过滤。
 */
private enum class DiscoverCategory(
    @StringRes val labelRes: Int,
    val classes: PlatformClasses,
    @StringRes val typeLabelRes: Int,
    val loaderFilterable: Boolean
) {
    MODS(
        labelRes = R.string.oxide_dis_tab_mods,
        classes = PlatformClasses.MOD,
        typeLabelRes = R.string.download_category_mod,
        loaderFilterable = true
    ),
    MODPACKS(
        labelRes = R.string.oxide_dis_tab_modpacks,
        classes = PlatformClasses.MOD_PACK,
        typeLabelRes = R.string.download_category_modpack,
        loaderFilterable = true
    ),
    SHADERS(
        labelRes = R.string.oxide_dis_tab_shaders,
        classes = PlatformClasses.SHADERS,
        typeLabelRes = R.string.download_category_shaders,
        loaderFilterable = false
    ),
    RESOURCE_PACKS(
        labelRes = R.string.oxide_dis_tab_resource_packs,
        classes = PlatformClasses.RESOURCE_PACK,
        typeLabelRes = R.string.download_category_resource_pack,
        loaderFilterable = false
    ),
    MAPS(
        labelRes = R.string.oxide_dis_tab_maps,
        classes = PlatformClasses.SAVES,
        typeLabelRes = R.string.download_category_saves,
        loaderFilterable = false
    )
}

/** 安装动作的状态 */
private sealed interface DiscoverInstall {
    data object Idle : DiscoverInstall

    /** 正在向平台查询可安装的文件 */
    data object Resolving : DiscoverInstall

    /** 没有选中任何实例，无法安装 */
    data object NeedsInstance : DiscoverInstall

    /** 平台上的项目没有符合当前过滤条件的文件 */
    data object NoFile : DiscoverInstall

    /** 已经交给任务系统开始下载 */
    data class Queued(val fileName: String) : DiscoverInstall

    /** 整合包安装完成，新建了实例 */
    data class Created(val instanceName: String) : DiscoverInstall

    data class Failed(val message: AndroidStringText) : DiscoverInstall

    data object Cancelled : DiscoverInstall
}

/** 项目全部文件版本的读取结果：失败必须与"没有文件"分开 */
private sealed interface DiscoverFilesResult {
    data class Ok(val versions: List<PlatformVersion>) : DiscoverFilesResult
    data object Empty : DiscoverFilesResult
    data class Failed(val message: AndroidStringText) : DiscoverFilesResult
}

/** 项目详情抽屉的状态 */
private data class DiscoverDetail(
    val item: DiscoverItem,
    val project: DiscoverProjectState,
    val files: DiscoverFilesState,
    val dependencies: DiscoverDependenciesState
)

private sealed interface DiscoverProjectState {
    data object Loading : DiscoverProjectState
    data class Loaded(val project: PlatformProject) : DiscoverProjectState
    data class Failed(val message: AndroidStringText) : DiscoverProjectState
}

private sealed interface DiscoverFilesState {
    data object Loading : DiscoverFilesState
    data class Loaded(val versions: List<PlatformVersion>) : DiscoverFilesState
    data object Empty : DiscoverFilesState
    data class Failed(val message: AndroidStringText) : DiscoverFilesState
}

/** 详情抽屉里依赖列表的状态 */
private sealed interface DiscoverDependenciesState {
    data object Loading : DiscoverDependenciesState
    data object Empty : DiscoverDependenciesState
    data class Loaded(val rows: List<DiscoverDependencyRow>) : DiscoverDependenciesState
    data class Failed(val message: AndroidStringText) : DiscoverDependenciesState
}

/**
 * 一个依赖在界面上的完整状态
 *
 * 每一条各自持有读取结果、勾选、下载错误与可重试的请求：其中一条失败不能把其余的
 * 判死，所以状态是逐条的一行，而不是一份统一的"依赖安装失败"。
 *
 * @param request 已经解析出的真实依赖请求；重试直接复用它，不重新猜
 */
private data class DiscoverDependencyRow(
    val dependency: DiscoverDependency,
    val state: DiscoverDependencyState,
    val selected: Boolean,
    val request: DependencyRequest? = null,
    val downloadError: AndroidStringText? = null,
)

/** 安装前确认层的三种真实状态：解析中、可确认、失败 */
private sealed interface DiscoverSheet {
    val item: DiscoverItem

    data class Resolving(override val item: DiscoverItem) : DiscoverSheet

    data class Ready(val content: DiscoverInstallSheet) : DiscoverSheet {
        override val item: DiscoverItem get() = content.item
    }

    data class Failed(
        override val item: DiscoverItem,
        val message: AndroidStringText,
    ) : DiscoverSheet
}

/**
 * 安装前确认的全部内容
 *
 * 打开这一层时一个字节都还没有下载：这里收集的是用户的三个决定——
 * 装到哪个 Minecraft 版本、带哪些依赖、整合包叫什么名字。
 *
 * @param chosenVersionName 用户改过的目标版本；null 表示仍跟随自动检测
 */
private data class DiscoverInstallSheet(
    val item: DiscoverItem,
    /** 已为这个目标版本解析出的项目文件版本 */
    val version: PlatformVersion,
    val classes: PlatformClasses,
    /** 自动检测到的目标版本，来自当前选中的实例 */
    val detectedVersionName: String?,
    val chosenVersionName: String?,
    /** 整合包要创建的实例名 */
    val instanceName: String,
    val dependencies: List<DiscoverDependencyRow>,
)

/** 安装提示条的内容 */
private data class DiscoverNotice(
    val title: String,
    val detail: AndroidStringText?,
    val action: (@Composable () -> Unit)? = null
)

/**
 * 发现页的状态持有者
 *
 * 只管状态与一次性动作：翻页搜索、安装、把整合包交给 [ModPackInstaller]。
 * 所有网络调用都在 [viewModelScope] 里跑；换条件时会取消上一次在途的请求——
 * 否则旧关键词的响应会落进新列表。
 */
private class OxideDiscoverViewModel : ViewModel() {

    var category by mutableStateOf(DiscoverCategory.MODS)
        private set

    // 初始来源取自设置里的"初始搜索平台"，与资源搜索页共用同一份配置
    var platform by mutableStateOf(DiscoverCategory.MODS.initialPlatform())
        private set
    var query by mutableStateOf("")
        private set
    var gameVersion by mutableStateOf("")
        private set
    var modloader by mutableStateOf<PlatformDisplayLabel?>(null)
        private set
    var sortField by mutableStateOf(PlatformSortField.RELEVANCE)
        private set

    /** 只显示当前实例里已经装好的项目 */
    var onlyInstalled by mutableStateOf(false)
        private set

    /**
     * 结果列表
     *
     * 无限滚动时结果是**累积**在这里的：换条件清空并回到 index 0，往下滚则接上新一页。
     */
    var feed by mutableStateOf(DiscoverFeed())
        private set

    /** 当前自动检测到的目标版本，即当前选中实例的版本名 */
    var detectedVersionName by mutableStateOf<String?>(null)
        private set

    var install by mutableStateOf<DiscoverInstall>(DiscoverInstall.Idle)
        private set

    /** 安装器回报的真实错误，原样展示 */
    var installError by mutableStateOf<ErrorViewModel.ThrowableMessage?>(null)
        private set

    var detail by mutableStateOf<DiscoverDetail?>(null)
        private set

    /** 安装前确认层，只有模组与整合包会打开 */
    var sheet by mutableStateOf<DiscoverSheet?>(null)
        private set

    /** 整合包实例名的问题（空、已存在、非法文件名）；由状态层算，界面不读磁盘 */
    var instanceNameProblem by mutableStateOf<AndroidStringText?>(null)
        private set

    var installer by mutableStateOf<ModPackInstaller?>(null)
        private set

    var awaitingMobileData by mutableStateOf(false)
        private set

    /** 本地已装的实例版本名 */
    var installedVersions by mutableStateOf<List<String>>(emptyList())
        private set

    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var sheetJob: Job? = null
    private var projectJob: Job? = null
    private var filesJob: Job? = null
    private var dependenciesJob: Job? = null
    private var mobileDataContinuation: Continuation<Boolean>? = null

    /** 上一次搜索用的条件，用来判断这次响应还该不该落地 */
    private var lastQuery: DiscoverQuery? = null

    /** 条目标题要按当前语言与 mcmod 译名解析，只有组合期做得到，所以由界面递进来 */
    private var titleResolver: ((PlatformSearchData, ModTranslations.McMod?) -> String)? = null

    /** 当前是否有筛选条件生效 */
    val hasFilters: Boolean
        get() = query.isNotBlank() || gameVersion.isNotBlank() || modloader != null ||
                sortField != PlatformSortField.RELEVANCE

    fun provideTitleResolver(resolver: (PlatformSearchData, ModTranslations.McMod?) -> String) {
        titleResolver = resolver
    }

    fun updateQuery(value: String) {
        query = value
    }

    /** 回车或按下搜索按钮时才真正发起搜索，输入过程中不会反复请求 */
    fun submitQuery(value: String) {
        query = value
        search()
    }

    fun selectCategory(value: DiscoverCategory) {
        if (category == value) return
        category = value
        onlyInstalled = false
        // 切类别时来源平台回到该类别自己的设置值；只有一个平台的类别固定为 CurseForge
        platform = value.initialPlatform()
        // 该类别不支持加载器过滤时，旧的加载器过滤不再有意义
        if (!value.loaderFilterable) modloader = null
        modloader = modloader?.takeIf { it in loadersFor(platform) }
    }

    fun toggleOnlyInstalled(value: Boolean) {
        onlyInstalled = value
    }

    fun selectPlatform(value: Platform) {
        if (platform == value) return
        platform = value
        modloader = modloader?.takeIf { it in loadersFor(value) }
        // 来源平台要落到设置里：设置页与资源搜索页读的是同一份
        category.platformSetting()?.save(value)
    }

    fun selectGameVersion(value: String) {
        gameVersion = value
    }

    fun selectModloader(value: PlatformDisplayLabel?) {
        // 只接受当前平台真实支持的加载器：两个平台的枚举是不同的类型，
        // 一个平台的加载器在另一个平台上无法转换成请求参数，发过去只会静默失效
        modloader = value?.takeIf { it in loadersFor(platform) }
    }

    fun selectSort(value: PlatformSortField) {
        sortField = value
    }

    fun clearFilters() {
        query = ""
        gameVersion = ""
        modloader = null
        sortField = PlatformSortField.RELEVANCE
    }

    // ---- 翻页 ------------------------------------------------------------

    /** 当前这一次搜索的条件全集 */
    private fun currentQuery() = DiscoverQuery(
        platform = platform,
        classes = category.classes,
        searchName = query,
        gameVersion = gameVersion,
        modloader = modloader,
        sortField = sortField,
    )

    /**
     * 换条件就换一批结果：取消在途请求、清空累计列表、从 index 0 重新开始
     *
     * 取消与清空缺一不可——只清列表的话，旧关键词的响应仍会落进新列表里。
     */
    fun search() {
        searchJob?.cancel()
        val next = currentQuery()
        discoverResetReason(lastQuery, next)?.let { reason ->
            Logger.info(TAG, "Restarting the result list because of: $reason")
        }
        lastQuery = next
        feed = feed.start()
        fetch(0)
    }

    /** 取下一页；已经在取或已经到底时这次调用什么也不做 */
    fun loadMore() {
        val started = feed.beginNextPage() ?: return
        feed = started
        fetch(DiscoverPaging.nextIndex(started.index, started.pageSize))
    }

    /**
     * 重试
     *
     * 已经有结果就只重试失败的那一页（同一个 index），否则整轮重来。
     * 两种情况都不会把失败说成"没有结果"。
     */
    fun retry() {
        searchJob?.cancel()
        if (feed.items.isEmpty()) {
            feed = feed.start()
            fetch(0)
        } else {
            loadMore()
        }
    }

    /** 本地已装版本名，供"要不要连游戏版本一起装"使用 */
    fun refreshInstalledVersions() {
        installedVersions = VersionsManager.versions.value.map { it.getVersionName() }
    }

    /** 当前选中的实例变了：自动检测到的目标版本跟着换 */
    fun onCurrentInstanceChanged(name: String?) {
        detectedVersionName = name
    }

    /**
     * 真正发起一次搜索
     *
     * [index] 是服务端那边的起始索引。落地前再核对一次条件：
     * 条件已经被换掉时这次响应直接丢掉，不碰列表。
     */
    private fun fetch(index: Int) {
        val request = lastQuery ?: return
        val appending = feed.items.isNotEmpty()
        searchJob = viewModelScope.launch {
            searchAssets(
                searchPlatform = request.platform,
                searchFilter = PlatformSearchFilter(
                    searchName = request.searchName,
                    gameVersion = request.gameVersion,
                    sortField = request.sortField,
                    modloader = request.modloader,
                    index = index,
                    limit = feed.pageSize,
                ),
                platformClasses = request.classes,
                onSuccess = { result ->
                    if (lastQuery != request) {
                        Logger.debug(TAG, "Discarded a stale page at index=$index")
                        return@searchAssets
                    }
                    val page = result.getAssetsPage(request.classes)
                    feed = feed.append(
                        page = page.toDiscoverPage { data, mcmod ->
                            titleResolver?.invoke(data, mcmod) ?: data.platformTitle()
                        },
                        classes = request.classes,
                    )
                },
                onError = { error ->
                    if (lastQuery != request) {
                        Logger.debug(TAG, "Discarded a stale failure at index=$index")
                        return@searchAssets
                    }
                    Logger.warning(TAG, "Search failed at index=$index", null)
                    feed = if (appending) {
                        feed.failNextPage(error.message)
                    } else {
                        feed.failFirstPage(error.message)
                    }
                }
            )
        }
    }

    // ---- 安装 ------------------------------------------------------------

    /**
     * 点"安装"
     *
     * 模组与整合包先开确认层，一个字节都还没下载；
     * 其余类别是整包放进某个实例的固定目录，没有版本与依赖的概念，直接装。
     */
    fun install(item: DiscoverItem) {
        if (install.busy()) return
        if (discoverUsesInstallSheet(item.classes)) {
            openSheet(item)
            return
        }
        if (VersionsManager.currentVersion.value == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            install = DiscoverInstall.Resolving
            when (val resolved = readAllVersions(item)) {
                is DiscoverFilesResult.Failed -> install = DiscoverInstall.Failed(resolved.message)
                is DiscoverFilesResult.Empty -> install = DiscoverInstall.NoFile
                is DiscoverFilesResult.Ok -> submitFile(
                    version = pickVersionFor(resolved.versions, gameVersion, loaderName())
                        ?: resolved.versions.first(),
                    classes = item.classes,
                    projectId = item.data.platformId(),
                )
            }
        }
    }

    /** 详情抽屉里选定的具体文件 */
    fun installVersion(item: DiscoverItem, version: PlatformVersion) {
        if (install.busy()) return
        if (discoverUsesInstallSheet(item.classes)) {
            openSheet(item, pinnedVersion = version)
            return
        }
        if (VersionsManager.currentVersion.value == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        submitFile(version, item.classes, item.data.platformId())
    }

    /** 读项目的全部文件版本；失败如实上报，绝不退化成"没有文件" */
    private suspend fun readAllVersions(item: DiscoverItem): DiscoverFilesResult {
        val projectId = item.data.platformId()
        val target = item.data.platform()
        val raw = try {
            getVersions(projectID = projectId, platform = target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to load the file list of ${item.title}", e)
            return DiscoverFilesResult.Failed(mapExceptionToMessage(e))
        }
        val versions = try {
            raw.initAll(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to initialise the files of ${item.title}", e)
            return DiscoverFilesResult.Failed(mapExceptionToMessage(e))
        }
        if (versions.isEmpty()) return DiscoverFilesResult.Empty
        return DiscoverFilesResult.Ok(versions)
    }

    private fun loaderName(): String? = modloader?.getDisplayName()

    // ---- 安装前确认 -------------------------------------------------------

    /**
     * 打开确认层
     *
     * 先解析项目文件与全部依赖，这一步只读不下载；
     * 用户确认之后才开始真正的下载。
     */
    fun openSheet(item: DiscoverItem, pinnedVersion: PlatformVersion? = null) {
        if (!discoverUsesInstallSheet(item.classes)) return
        sheetJob?.cancel()
        // target 是目标**游戏版本名**，与被查看的资源版本无关：
        // pinnedVersion 是那个模组/整合包自己的 PlatformVersion，拿它当目标版本是错的
        val target: String? = VersionsManager.currentVersion.value?.getVersionName()
        sheet = DiscoverSheet.Resolving(item)
        sheetJob = viewModelScope.launch {
            sheet = if (pinnedVersion != null) {
                buildSheet(item, pinnedVersion, target)
            } else {
                resolveSheet(item, target)
            }
        }
    }

    /** 用户在确认层里改了目标版本：重新按那个版本解析文件与依赖 */
    fun selectSheetVersion(name: String?) {
        val current = sheet ?: return
        sheetJob?.cancel()
        sheet = DiscoverSheet.Resolving(current.item)
        sheetJob = viewModelScope.launch {
            sheet = resolveSheet(current.item, name)
        }
    }

    /** 勾选或取消一个依赖 */
    fun toggleSheetDependency(key: String, checked: Boolean) {
        val ready = sheet as? DiscoverSheet.Ready ?: return
        sheet = DiscoverSheet.Ready(
            ready.content.copy(
                dependencies = ready.content.dependencies.map { row ->
                    if (row.dependency.key == key) row.withToggled(checked) else row
                }
            )
        )
    }

    /** 只重试一个依赖的读取，其余依赖的状态原样保留 */
    fun retrySheetDependency(key: String) {
        val ready = sheet as? DiscoverSheet.Ready ?: return
        val target = ready.content.dependencies.firstOrNull { it.dependency.key == key } ?: return
        sheet = DiscoverSheet.Ready(
            ready.content.copy(
                dependencies = ready.content.dependencies.map {
                    if (it.dependency.key == key) it.copy(state = DiscoverDependencyState.Resolving) else it
                }
            )
        )
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val (state, request) = resolveDependency(
                dependency = target.dependency,
                classes = ready.content.classes,
                mcVersionName = ready.content.minecraftVersionName(),
            )
            val current = sheet as? DiscoverSheet.Ready ?: return@launch
            sheet = DiscoverSheet.Ready(
                current.content.copy(
                    dependencies = current.content.dependencies.map {
                        if (it.dependency.key == key) it.copy(state = state, request = request) else it
                    }
                )
            )
        }
    }

    fun setSheetInstanceName(value: String) {
        val ready = sheet as? DiscoverSheet.Ready ?: return
        sheet = DiscoverSheet.Ready(ready.content.copy(instanceName = value))
        instanceNameProblem = if (ready.content.classes == PlatformClasses.MOD_PACK) {
            checkInstanceName(value)
        } else {
            null
        }
    }

    fun dismissSheet() {
        sheetJob?.cancel()
        sheet = null
        instanceNameProblem = null
    }

    /** 按目标 Minecraft 版本挑项目文件，再解析它的全部依赖 */
    private suspend fun resolveSheet(item: DiscoverItem, targetVersionName: String?): DiscoverSheet {
        val mcVersion = targetVersionName?.let { name ->
            VersionsManager.versions.value.firstOrNull { it.getVersionName() == name }
        }?.let { minecraftVersionOf(it) }

        return when (val files = readAllVersions(item)) {
            is DiscoverFilesResult.Failed -> DiscoverSheet.Failed(item, files.message)
            is DiscoverFilesResult.Empty -> DiscoverSheet.Failed(
                item,
                androidText(R.string.oxide_dis_install_no_file_title),
            )

            is DiscoverFilesResult.Ok -> {
                val version = pickVersionFor(files.versions, mcVersion, loaderName())
                if (version == null) {
                    DiscoverSheet.Failed(
                        item,
                        androidText(R.string.oxide_dis_sheet_no_file_for_version, mcVersion.orEmpty()),
                    )
                } else {
                    buildSheet(item, version, targetVersionName)
                }
            }
        }
    }

    private suspend fun buildSheet(
        item: DiscoverItem,
        version: PlatformVersion,
        targetVersionName: String?,
    ): DiscoverSheet {
        val classes = item.classes
        val dependencies = discoverDependenciesOf(version).take(MAX_DISCOVER_DEPENDENCIES)
        val defaultSelected = discoverDefaultSelection(dependencies)

        val rows = dependencies.map { dependency ->
            val (state, request) = resolveDependency(
                dependency = dependency,
                classes = classes,
                mcVersionName = targetVersionName?.let { name ->
                    VersionsManager.versions.value.firstOrNull { it.getVersionName() == name }
                }?.let { minecraftVersionOf(it) },
            )
            DiscoverDependencyRow(
                dependency = dependency,
                state = state,
                selected = dependency.key in defaultSelected,
                request = request,
            )
        }

        val instanceName = if (classes == PlatformClasses.MOD_PACK) uniqueInstanceName(item.title) else ""
        instanceNameProblem = if (classes == PlatformClasses.MOD_PACK) checkInstanceName(instanceName) else null

        return DiscoverSheet.Ready(
            DiscoverInstallSheet(
                item = item,
                version = version,
                classes = classes,
                detectedVersionName = VersionsManager.currentVersion.value?.getVersionName(),
                chosenVersionName = targetVersionName,
                instanceName = instanceName,
                dependencies = rows,
            )
        )
    }

    /**
     * 解析一个依赖在目标 Minecraft 版本下的具体文件
     *
     * 只给了精确版本 id（Modrinth）就直接取那个版本；只给了项目 id（CurseForge）
     * 就拉全部版本再挑一个与目标版本匹配的——与依赖安装链路自己做的事一致，
     * 因此界面上显示的版本就是会被装上的那个。
     */
    private suspend fun resolveDependency(
        dependency: DiscoverDependency,
        classes: PlatformClasses,
        mcVersionName: String?,
    ): Pair<DiscoverDependencyState, DependencyRequest?> = try {
        when {
            dependency.versionId != null -> {
                val version = getVersionById(
                    versionId = dependency.versionId,
                    platform = dependency.platform,
                    // CurseForge 的版本要按 (项目, 文件) 定位，缺项目就查不出来
                    projectId = dependency.projectId,
                    printLog = false,
                )
                val projectId = version.platformProjectId().takeIf { it.isNotBlank() }
                if (projectId == null) {
                    DiscoverDependencyState.NoCompatibleVersion to null
                } else {
                    resolvedState(version) to DependencyRequest(
                        platform = dependency.platform,
                        projectId = projectId,
                        // Modrinth 认这个精确 id
                        versionId = version.platformId(),
                        classes = classes,
                        projectTitle = version.platformDisplayName(),
                    )
                }
            }

            dependency.projectId != null -> {
                val all = getVersions(
                    projectID = dependency.projectId,
                    platform = dependency.platform,
                ).initAll(dependency.projectId)
                val picked = pickVersionFor(all, mcVersionName, loaderName())
                if (picked == null) {
                    DiscoverDependencyState.NoCompatibleVersion to null
                } else {
                    resolvedState(picked) to DependencyRequest(
                        platform = dependency.platform,
                        projectId = dependency.projectId,
                        // CurseForge 的依赖不带精确版本 id，交给依赖链路按目标游戏版本挑
                        versionId = if (dependency.platform == Platform.MODRINTH) {
                            picked.platformId()
                        } else {
                            null
                        },
                        classes = classes,
                        projectTitle = picked.platformDisplayName(),
                    )
                }
            }

            // 两个 id 都没有的依赖只能展示，装不了
            else -> DiscoverDependencyState.NoCompatibleVersion to null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warning(TAG, "Failed to resolve the dependency ${dependency.key}", e)
        DiscoverDependencyState.Failed(mapExceptionToMessage(e)) to null
    }

    /** 已解析出的依赖版本在界面上的样子：版本号 + 它支持的 Minecraft 版本 */
    private fun resolvedState(version: PlatformVersion) = DiscoverDependencyState.Resolved(
        versionLabel = version.platformVersion().ifBlank { version.platformDisplayName() },
        gameVersions = version.platformGameVersion().joinToString(", "),
    )

    // ---- 提交安装 ---------------------------------------------------------

    /**
     * 按确认层上的选择执行这一次安装
     *
     * 模组：项目文件交给 [downloadSingleForVersions]，勾上的依赖逐条交给
     * [downloadDependenciesForVersions]——每条一个任务，因此一个失败不会连累其余几条，
     * 各自都有独立的错误与重试。
     */
    fun confirmInstall(context: Context) {
        val ready = (sheet as? DiscoverSheet.Ready)?.content ?: return
        val plan = ready.plan()

        if (ready.classes == PlatformClasses.MOD_PACK) {
            startModpackInstall(context, ready)
            return
        }

        val targetName = plan.targetVersionName
        if (targetName == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        val existing = VersionsManager.versions.value.firstOrNull { it.getVersionName() == targetName }
        if (existing == null) {
            // 用户确认过要连游戏版本一起装：先装游戏，装好再装这个资源
            if (plan.installGameVersion) {
                installGameThenAssets(context, targetName, ready)
            } else {
                install = DiscoverInstall.NeedsInstance
            }
            return
        }
        submitAssets(ready, existing, plan)
    }

    /** 连游戏版本一起装：游戏任务结束后刷新版本列表，再装资源 */
    private fun installGameThenAssets(
        context: Context,
        versionName: String,
        ready: DiscoverInstallSheet,
    ) {
        sheet = null
        if (TaskSystem.containsTask(DOWNLOADER_TAG)) {
            install = DiscoverInstall.Failed(
                androidText(R.string.oxide_dis_sheet_game_busy)
            )
            return
        }
        install = DiscoverInstall.Resolving
        val gameTask = MinecraftDownloader(
            context = context,
            version = versionName,
            customName = versionName,
            gameHome = getGameHome(),
            mode = DownloadMode.DOWNLOAD,
            onThrowable = { throw it },
        ).getDownloadTask()
        TaskSystem.submitTask(gameTask) {
            VersionsManager.refresh("$TAG: after installing the game version", versionName)
            refreshInstalledVersions()
            val installed = VersionsManager.versions.value.firstOrNull { it.getVersionName() == versionName }
            if (installed == null) {
                install = DiscoverInstall.Failed(
                    androidText(R.string.oxide_dis_sheet_game_install_failed, versionName)
                )
                return@submitTask
            }
            submitAssets(ready, installed, ready.plan())
        }
    }

    /** 项目文件 + 勾选的依赖，一起提交 */
    private fun submitAssets(
        sheetContent: DiscoverInstallSheet,
        target: Version,
        plan: DiscoverInstallPlan,
    ) {
        sheet = null
        submitFile(sheetContent.version, sheetContent.classes, sheetContent.item.data.platformId(), target)

        sheetContent.dependencies
            .filter { it.dependency.key in plan.dependencyKeys && it.request != null }
            .forEach { row ->
                row.request?.let { request -> submitDependency(request, target, row.dependency.key) }
            }

        install = DiscoverInstall.Queued(sheetContent.version.platformDisplayName())
    }

    /** 详情抽屉里的"全部下载"：目标取当前实例，依赖逐条提交 */
    fun downloadAllFromDetail() {
        val detail = detail ?: return
        val rows = (detail.dependencies as? DiscoverDependenciesState.Loaded)?.rows ?: return
        val target = VersionsManager.currentVersion.value ?: run {
            install = DiscoverInstall.NeedsInstance
            return
        }
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            install = DiscoverInstall.Resolving
            when (val files = readAllVersions(detail.item)) {
                is DiscoverFilesResult.Failed -> {
                    install = DiscoverInstall.Failed(files.message)
                    return@launch
                }

                is DiscoverFilesResult.Empty -> {
                    install = DiscoverInstall.NoFile
                    return@launch
                }

                is DiscoverFilesResult.Ok -> {
                    val version = pickVersionFor(files.versions, gameVersion, loaderName())
                        ?: files.versions.first()
                    submitFile(version, detail.item.classes, detail.item.data.platformId(), target)
                    rows.filter { it.selected && it.request != null }.forEach { row ->
                        row.request?.let { request -> submitDependency(request, target, row.dependency.key) }
                    }
                    install = DiscoverInstall.Queued(version.platformDisplayName())
                }
            }
        }
    }

    /**
     * 提交一个依赖
     *
     * 每个依赖单独一次调用，因此每个依赖都是一个独立任务：
     * 失败通过 [submitError] 只落在它自己那一行，其余依赖照旧在跑。
     */
    private fun submitDependency(request: DependencyRequest, target: Version, key: String) {
        downloadDependenciesForVersions(
            requests = listOf(request),
            versions = listOf(target),
            submitError = { error ->
                installError = error
                markDependencyFailed(key, error.message)
            },
        )
    }

    /** 只重试一个依赖的下载 */
    fun retryDependencyDownload(key: String) {
        val rows = (detail?.dependencies as? DiscoverDependenciesState.Loaded)?.rows ?: return
        val request = rows.firstOrNull { it.dependency.key == key }?.request ?: return
        val target = VersionsManager.currentVersion.value ?: run {
            install = DiscoverInstall.NeedsInstance
            return
        }
        markDependencyFailed(key, null)
        submitDependency(request, target, key)
    }

    /** 把某个依赖的下载错误记在它自己那一行上 */
    private fun markDependencyFailed(key: String, message: AndroidStringText?) {
        val loaded = detail?.dependencies as? DiscoverDependenciesState.Loaded
        if (loaded != null) {
            detail = detail?.copy(
                dependencies = DiscoverDependenciesState.Loaded(
                    loaded.rows.map { row ->
                        if (row.dependency.key == key) row.copy(downloadError = message) else row
                    }
                )
            )
        }
        val ready = sheet as? DiscoverSheet.Ready
        if (ready != null) {
            sheet = DiscoverSheet.Ready(
                ready.content.copy(
                    dependencies = ready.content.dependencies.map { row ->
                        if (row.dependency.key == key) row.copy(downloadError = message) else row
                    }
                )
            )
        }
    }

    /** 交给单文件安装器 */
    private fun submitFile(
        version: PlatformVersion,
        classes: PlatformClasses,
        projectId: String,
        target: Version? = VersionsManager.currentVersion.value,
    ) {
        if (target == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        downloadSingleForVersions(
            version = version,
            versions = listOf(target),
            folder = classes.versionFolder.folderName,
            submitError = { installError = it }
        )
        Logger.info(TAG, "Queued ${version.platformDisplayName()} of $projectId for ${target.getVersionName()}")
    }

    // ---- 整合包安装 -------------------------------------------------------

    /** 确认层里确认整合包：交给安装器装成一个新实例 */
    private fun startModpackInstall(context: Context, sheetContent: DiscoverInstallSheet) {
        val draft = sheetContent
        sheet = null
        instanceNameProblem = null

        val created = ModPackInstaller(
            context = context,
            version = draft.version,
            iconUrl = draft.item.data.platformIconUrl(),
            scope = viewModelScope,
            waitForVersionName = { _: ModPackInfo -> draft.instanceName },
            waitForConfirmMobileData = ::awaitMobileDataDecision
        )
        installer = created
        created.installModPack(
            onInstalled = { name ->
                installer = null
                VersionsManager.refresh("$TAG: ModPackInstaller.onInstalled", name)
                refreshInstalledVersions()
                install = DiscoverInstall.Created(name)
            },
            onCancelled = {
                installer = null
                install = DiscoverInstall.Cancelled
            },
            onError = { th ->
                installer = null
                Logger.error(TAG, "Failed to install the modpack ${draft.item.title}", th)
                install = DiscoverInstall.Failed(mapExceptionToMessage(th))
            }
        )
    }

    fun cancelModpackInstall() {
        installer?.cancelInstall()
        installer = null
        install = DiscoverInstall.Cancelled
    }

    /** 移动网络确认：安装器会挂起在这里，直到用户给出答案 */
    private suspend fun awaitMobileDataDecision(): Boolean = suspendCancellableCoroutine { cont ->
        mobileDataContinuation = cont
        awaitingMobileData = true
    }

    fun answerMobileData(use: Boolean) {
        mobileDataContinuation?.resume(use)
        mobileDataContinuation = null
        awaitingMobileData = false
    }

    // ---- 项目详情 --------------------------------------------------------

    fun openDetail(item: DiscoverItem) {
        projectJob?.cancel()
        filesJob?.cancel()
        dependenciesJob?.cancel()
        detail = DiscoverDetail(
            item = item,
            project = DiscoverProjectState.Loading,
            files = DiscoverFilesState.Loading,
            dependencies = DiscoverDependenciesState.Loading
        )
        loadProject(item)
        loadFiles(item)
        loadDependencies(item)
    }

    fun closeDetail() {
        projectJob?.cancel()
        filesJob?.cancel()
        dependenciesJob?.cancel()
        detail = null
    }

    private fun loadProject(item: DiscoverItem) {
        val projectId = item.data.platformId()
        val target = item.data.platform()
        projectJob = viewModelScope.launch {
            val state = try {
                DiscoverProjectState.Loaded(
                    getProjectByVersion(projectId = projectId, platform = target)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the project $projectId", e)
                DiscoverProjectState.Failed(mapExceptionToMessage(e))
            }
            if (detail?.item?.key != item.key) return@launch
            detail = detail?.copy(project = state)
        }
    }

    private fun loadFiles(item: DiscoverItem) {
        val projectId = item.data.platformId()
        val target = item.data.platform()
        filesJob = viewModelScope.launch {
            val state = try {
                val versions = getVersions(projectID = projectId, platform = target).initAll(projectId)
                if (versions.isEmpty()) DiscoverFilesState.Empty else DiscoverFilesState.Loaded(versions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the files of $projectId", e)
                DiscoverFilesState.Failed(mapExceptionToMessage(e))
            }
            if (detail?.item?.key != item.key) return@launch
            detail = detail?.copy(files = state)
        }
    }

    private fun loadDependencies(item: DiscoverItem) {
        val projectId = item.data.platformId()
        val target = item.data.platform()
        val classes = item.classes
        dependenciesJob = viewModelScope.launch {
            val state = try {
                when (val files = readAllVersions(item)) {
                    is DiscoverFilesResult.Failed -> DiscoverDependenciesState.Failed(files.message)
                    is DiscoverFilesResult.Empty -> DiscoverDependenciesState.Empty
                    is DiscoverFilesResult.Ok -> {
                        val version = pickVersionFor(files.versions, gameVersion, loaderName())
                            ?: files.versions.first()
                        val dependencies = discoverDependenciesOf(version).take(MAX_DISCOVER_DEPENDENCIES)
                        val defaultSelected = discoverDefaultSelection(dependencies)
                        DiscoverDependenciesState.Loaded(
                            dependencies.map { dependency ->
                                val (rowState, request) =
                                    resolveDependency(dependency, classes, gameVersion)
                                DiscoverDependencyRow(
                                    dependency = dependency,
                                    state = rowState,
                                    selected = dependency.key in defaultSelected,
                                    request = request,
                                )
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the dependencies of $projectId", e)
                DiscoverDependenciesState.Failed(mapExceptionToMessage(e))
            }
            if (detail?.item?.key != item.key) return@launch
            detail = detail?.copy(dependencies = state)
        }
    }

    /** 详情抽屉里勾选一个依赖 */
    fun toggleDetailDependency(key: String, checked: Boolean) {
        val loaded = detail?.dependencies as? DiscoverDependenciesState.Loaded ?: return
        detail = detail?.copy(
            dependencies = DiscoverDependenciesState.Loaded(
                loaded.rows.map { row ->
                    if (row.dependency.key == key) row.withToggled(checked) else row
                }
            )
        )
    }

    fun dismissInstallState() {
        install = DiscoverInstall.Idle
    }

    fun dismissInstallError() {
        installError = null
    }

    override fun onCleared() {
        searchJob?.cancel()
        resolveJob?.cancel()
        sheetJob?.cancel()
        projectJob?.cancel()
        filesJob?.cancel()
        dependenciesJob?.cancel()
        installer?.cancelInstall()
        mobileDataContinuation = null
    }
}

/** 勾选或取消这一个依赖，其他行原样不动 */
private fun DiscoverDependencyRow.withToggled(checked: Boolean): DiscoverDependencyRow {
    val selected = discoverToggleSelection(setOf(dependency.key), dependency, checked)
    return copy(selected = dependency.key in selected)
}

/** 确认层里最终要装到哪个 Minecraft 版本 */
private fun DiscoverInstallSheet.targetVersionName(): String? =
    discoverResolveVersion(chosenVersionName, detectedVersionName).name

/** 确认层里那个实例真实的 Minecraft 版本名 */
private fun DiscoverInstallSheet.minecraftVersionName(): String? =
    targetVersionName()?.let { name ->
        VersionsManager.versions.value.firstOrNull { it.getVersionName() == name }
    }?.let { minecraftVersionOf(it) }

/** 确认层上这份选择对应的执行计划 */
private fun DiscoverInstallSheet.plan(): DiscoverInstallPlan = discoverBuildPlan(
    classes = classes,
    detectedVersion = detectedVersionName,
    chosenVersion = targetVersionName(),
    installedVersions = installedVersionNames(),
    dependencies = dependencies.map { it.dependency },
    selectedDependencyKeys = dependencies.filter { it.selected }.mapTo(LinkedHashSet()) { it.dependency.key },
    instanceName = instanceName,
)

/** 安装进行中时不允许重复触发 */
private fun DiscoverInstall.busy(): Boolean = when (this) {
    is DiscoverInstall.Resolving, is DiscoverInstall.Queued -> true
    else -> false
}

/** 只有 CurseForge 提供存档 */
private fun PlatformClasses.supportsModrinth(): Boolean = this != PlatformClasses.SAVES

/**
 * 按 Minecraft 版本与加载器挑一个文件版本
 *
 * [versions] 已由 [initAll] 按发布时间倒序排好，所以第一个命中的就是最新的那个；
 * 一个都匹配不上时返回 null，交给调用方决定是退回最新还是如实报"这个版本没有匹配的文件"。
 */
private fun pickVersionFor(
    versions: List<PlatformVersion>,
    mcVersion: String?,
    loaderName: String?,
): PlatformVersion? {
    if (versions.isEmpty()) return null
    val wantedMc = mcVersion?.takeIf { it.isNotBlank() }
    return versions.firstOrNull { version ->
        val mcMatches = wantedMc == null || version.platformGameVersion().contains(wantedMc)
        val loaderMatches = loaderName.isNullOrBlank() ||
                version.platformLoaders().any { it.getDisplayName().equals(loaderName, true) }
        mcMatches && loaderMatches
    }
}

/** 实例名 → 它真实的 Minecraft 版本名；自定义实例名不等于 Minecraft 版本 */
private fun minecraftVersionOf(version: Version): String =
    version.getVersionInfo()?.minecraftVersion?.takeIf { it.isNotBlank() }
        ?: version.getVersionName()

/** 本地已装的实例版本名 */
private fun installedVersionNames(): Set<String> =
    VersionsManager.versions.value.mapTo(LinkedHashSet()) { it.getVersionName() }

/** 整合包实例名的问题：空、重名、非法文件名 */
private fun checkInstanceName(value: String): AndroidStringText? = when {
    value.isBlank() -> androidText(R.string.oxide_dis_modpack_name_required)
    VersionsManager.isVersionExists(value, true) -> androidText(R.string.oxide_dis_modpack_name_exists)
    else -> invalidFilenameText(value)
}

/**
 * 文件名非法时的提示
 *
 * 这里没有复用 `isFilenameInvalid`：它带 @Composable，因为它内部用 stringResource
 * 取文案。而本函数是从一个普通函数和一个挂起函数里调用的，不能要求组合环境。
 *
 * 改成只判断、只回 AndroidStringText（StringRes 形式），真正的文案留给渲染它的那一步去取，
 * 于是判断重新变回纯函数。文案与参数和旧实现完全一致。
 */
private fun invalidFilenameText(value: String): AndroidStringText? = try {
    checkFilenameValidity(value)
    null
} catch (e: InvalidFilenameException) {
    when {
        e.containsIllegalCharacters() ->
            androidText(R.string.generic_input_invalid_character, e.illegalCharacters ?: "")

        e.isInvalidLength -> androidText(R.string.file_invalid_length, e.invalidLength ?: 0, 255)
        e.isLeadingOrTrailingSpace ->
            androidText(R.string.file_invalid_leading_or_trailing_space)

        else -> null
    }
}

/**
 * 每个类别在设置里对应的"初始搜索平台"，与资源搜索页共用同一份配置
 *
 * 存档只有 CurseForge 一个平台，没有对应的设置项，因此这里是 null
 */
private fun DiscoverCategory.platformSetting(): EnumSettingUnit<Platform>? = when (this) {
    DiscoverCategory.MODS -> AllSettings.searchModPlatform
    DiscoverCategory.MODPACKS -> AllSettings.searchModpackPlatform
    DiscoverCategory.RESOURCE_PACKS -> AllSettings.searchResourcePackPlatform
    DiscoverCategory.SHADERS -> AllSettings.searchShadersPlatform
    DiscoverCategory.MAPS -> null
}

/**
 * 进入该类别时的初始来源平台
 *
 * 取自设置里的"初始搜索平台"；只有一个平台的类别（存档）固定为 CurseForge。
 */
private fun DiscoverCategory.initialPlatform(): Platform {
    if (!classes.supportsModrinth()) return Platform.CURSEFORGE
    return platformSetting()?.getValue() ?: Platform.CURSEFORGE
}

/** 类别的短标签，与资源页的类别文案保持一致 */
@StringRes
private fun PlatformClasses.typeLabelRes(): Int = when (this) {
    PlatformClasses.MOD -> R.string.download_category_mod
    PlatformClasses.MOD_PACK -> R.string.download_category_modpack
    PlatformClasses.RESOURCE_PACK -> R.string.download_category_resource_pack
    PlatformClasses.SAVES -> R.string.download_category_saves
    PlatformClasses.SHADERS -> R.string.download_category_shaders
}

/** 与资源搜索页一致：两个平台各自可选的模组加载器 */
private fun loadersFor(platform: Platform): List<PlatformDisplayLabel> = when (platform) {
    Platform.CURSEFORGE -> curseForgeModLoaderFilters
    Platform.MODRINTH -> modrinthModLoaderFilters
}

/** 依据已安装的版本列表生成一个不冲突的整合包实例名 */
private fun uniqueInstanceName(title: String): String {
    val base = title.trim().ifEmpty { "modpack" }
    if (!VersionsManager.isVersionExists(base, true)) return base
    var suffix = 2
    while (VersionsManager.isVersionExists("$base ($suffix)", true)) {
        suffix++
    }
    return "$base ($suffix)"
}

/** 任务阶段对应的短标签 */
@StringRes
private fun TaskStage.labelRes(): Int = when (this) {
    TaskStage.PREPARING -> R.string.oxide_dis_task_stage_preparing
    TaskStage.RUNNING -> R.string.oxide_dis_task_stage_running
    TaskStage.COMPLETED -> R.string.oxide_dis_task_stage_completed
}

/** 依赖类型的短标签 */
@StringRes
private fun PlatformDependencyType.typeLabelRes(): Int = when (this) {
    PlatformDependencyType.REQUIRED -> R.string.oxide_dis_dep_type_required
    PlatformDependencyType.OPTIONAL -> R.string.oxide_dis_dep_type_optional
    PlatformDependencyType.EMBEDDED -> R.string.oxide_dis_dep_type_embedded
    PlatformDependencyType.INCOMPATIBLE -> R.string.oxide_dis_dep_type_incompatible
    PlatformDependencyType.TOOL -> R.string.oxide_dis_dep_type_tool
    PlatformDependencyType.INCLUDE -> R.string.oxide_dis_dep_type_include
}

/** 依赖在列表里的标识：平台只保证给出项目 ID 或版本 ID 之一，两个都没有就不显示名字 */
private fun DiscoverDependency.displayName(): String =
    projectId?.takeIf { it.isNotBlank() }
        ?: versionId?.takeIf { it.isNotBlank() }
        ?: ""

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

/**
 * 发现页
 *
 * 结构照参考稿：左侧类别栏 + 右侧「搜索与筛选行 + 结果网格」。
 * 数据全部来自真实的平台搜索（CurseForge / Modrinth），结果**累积**并按需翻页；
 * 安装动作先经过安装前确认层，网格里的每一条都是平台上的真实项目。
 */
@Composable
fun OxideDiscoverPage(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: OxideDiscoverViewModel = viewModel(key = "OxideDiscoverPage") {
        OxideDiscoverViewModel()
    }
    val installed: DownloadModViewModel = viewModel(key = "OxideDiscoverInstalled") {
        DownloadModViewModel()
    }

    val context = LocalContext.current
    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val minecraftVersions by MinecraftVersions.allVersions.collectAsStateWithLifecycle()
    val localVersions by VersionsManager.versions.collectAsStateWithLifecycle()
    val installedMods = installed.installedByProject

    // 条目标题要走 mcmod 译名，翻译表只有界面这一侧才有，所以把函数递进状态层
    LaunchedEffect(context) {
        viewModel.provideTitleResolver { data, mcmod ->
            mcmod.getMcmodTitle(data.platformTitle(), context)
        }
    }

    // 版本表只在第一次进入时读取，之后复用进程内缓存
    LaunchedEffect(Unit) {
        try {
            MinecraftVersions.refreshVersions(force = false)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to refresh the Minecraft version manifest", e)
        }
    }

    // 已安装标注依赖当前实例的模组指纹扫描，与下载页用的是同一套能力
    LaunchedEffect(viewModel.platform) {
        installed.onPlatformChanged(viewModel.platform)
    }
    LaunchedEffect(currentVersion?.getVersionName()) {
        installed.scan(currentVersion)
        viewModel.onCurrentInstanceChanged(currentVersion?.getVersionName())
    }
    LaunchedEffect(localVersions.size) {
        viewModel.refreshInstalledVersions()
    }

    // 条件真的变了才重新搜索，组合本身不会触发任何请求。
    // query 不在键里：输入过程中不搜索，只有回车或按下搜索按钮才 search()
    LaunchedEffect(
        viewModel.category,
        viewModel.platform,
        viewModel.gameVersion,
        viewModel.modloader,
        viewModel.sortField
    ) {
        viewModel.search()
    }

    val installedIds = remember(installedMods, viewModel.platform) {
        installedMods.filterValues { it.platform == viewModel.platform }.keys
    }

    val visibleItems = remember(viewModel.feed, viewModel.onlyInstalled, installedIds) {
        if (viewModel.onlyInstalled) {
            viewModel.feed.items.filter { it.data.platformId() in installedIds }
        } else {
            viewModel.feed.items
        }
    }

    OxidePageColumn(modifier = modifier, metrics = metrics) {
        DiscoverHeader(metrics = metrics, onRefresh = viewModel::search)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = metrics.sectionGap)
        ) {
            OxideReveal(visible = true, index = 1) {
                DiscoverCategoryRail(
                    modifier = Modifier
                        .width(metrics.categoryRailWidth)
                        .fillMaxHeight(),
                    metrics = metrics,
                    selected = viewModel.category,
                    onlyInstalled = viewModel.onlyInstalled,
                    onSelect = viewModel::selectCategory,
                    onSelectInstalled = { viewModel.toggleOnlyInstalled(true) }
                )
            }

            Spacer(Modifier.width(metrics.cardGap))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                DiscoverFilterBar(
                    modifier = Modifier.fillMaxWidth(),
                    metrics = metrics,
                    viewModel = viewModel,
                    minecraftVersions = minecraftVersions
                )

                Spacer(Modifier.height(metrics.cardGap))

                OxideReveal(visible = true, index = 3, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DiscoverResultsHeader(
                            count = visibleItems.size,
                            pages = viewModel.feed.pages,
                            scanningInstalled = installed.matching,
                            onlyInstalled = viewModel.onlyInstalled
                        )
                        Spacer(Modifier.height(metrics.cardGap * 0.7f))
                        DiscoverResultsGrid(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            metrics = metrics,
                            feed = viewModel.feed,
                            visible = visibleItems,
                            category = viewModel.category,
                            installedIds = installedIds,
                            onlyInstalled = viewModel.onlyInstalled,
                            busy = viewModel.install.busy(),
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMore,
                            onOpen = viewModel::openDetail,
                            onInstall = viewModel::install
                        )
                    }
                }
            }
        }
    }

    DiscoverDetailHost(
        metrics = metrics,
        detail = viewModel.detail,
        currentVersionName = currentVersion?.getVersionName(),
        busy = viewModel.install.busy(),
        onDismiss = viewModel::closeDetail,
        onInstall = viewModel::install,
        onInstallVersion = viewModel::installVersion,
        onToggleDependency = viewModel::toggleDetailDependency,
        onRetryDependency = viewModel::retryDependencyDownload,
        onDownloadAll = viewModel::downloadAllFromDetail
    )

    DiscoverInstallSheetHost(
        metrics = metrics,
        sheet = viewModel.sheet,
        localVersions = localVersions,
        minecraftVersions = minecraftVersions,
        instanceNameProblem = viewModel.instanceNameProblem,
        busy = viewModel.install.busy(),
        onDismiss = viewModel::dismissSheet,
        onSelectVersion = viewModel::selectSheetVersion,
        onToggleDependency = viewModel::toggleSheetDependency,
        onRetryDependency = viewModel::retrySheetDependency,
        onInstanceNameChange = viewModel::setSheetInstanceName,
        onGoInstances = { onNavigate(OxidePage.Instances) },
        onConfirm = { viewModel.confirmInstall(context) }
    )

    DiscoverInstallDrawer(
        metrics = metrics,
        installer = viewModel.installer,
        onCancel = viewModel::cancelModpackInstall
    )

    if (viewModel.awaitingMobileData) {
        DiscoverMobileDataDialog(
            metrics = metrics,
            onDeny = { viewModel.answerMobileData(false) },
            onAllow = { viewModel.answerMobileData(true) }
        )
    }

    DiscoverInstallNotice(
        metrics = metrics,
        install = viewModel.install,
        error = viewModel.installError,
        onGoInstances = { onNavigate(OxidePage.Instances) },
        onDismiss = viewModel::dismissInstallState,
        onDismissError = viewModel::dismissInstallError
    )
}

/** 页头：大标题 + 一行说明 + 刷新 */
@Composable
private fun DiscoverHeader(metrics: OxideMetrics, onRefresh: () -> Unit) {
    OxideReveal(visible = true, index = 0) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OxidePageTitle(
                text = stringResource(R.string.oxide_dis_title),
                trailing = {
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_refresh),
                        onClick = onRefresh,
                        tone = OxideButtonTone.Secondary
                    )
                }
            )
            Spacer(Modifier.height(metrics.pagePaddingV * 0.3f))
            Text(
                text = stringResource(R.string.oxide_dis_subtitle).uppercase(),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 左侧类别栏 */
@Composable
private fun DiscoverCategoryRail(
    selected: DiscoverCategory,
    onlyInstalled: Boolean,
    onSelect: (DiscoverCategory) -> Unit,
    onSelectInstalled: () -> Unit,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    OxideSurface(modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DiscoverCategory.entries.forEach { category ->
                DiscoverCategoryTab(
                    text = stringResource(category.labelRes),
                    selected = category == selected && !onlyInstalled,
                    metrics = metrics,
                    onClick = { onSelect(category) }
                )
            }

            Spacer(Modifier.height(metrics.cardGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.controlPadding)
                    .height(1.dp)
                    .background(Oxide.Line)
            )
            Spacer(Modifier.height(metrics.cardGap))

            DiscoverCategoryTab(
                text = stringResource(R.string.oxide_dis_tab_installed),
                selected = onlyInstalled,
                metrics = metrics,
                onClick = onSelectInstalled
            )
        }
    }
}

@Composable
private fun DiscoverCategoryTab(
    text: String,
    selected: Boolean,
    metrics: OxideMetrics,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.categoryItemHeight)
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .selectable(
                selected = selected,
                enabled = true,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = metrics.controlPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = if (selected) Oxide.Fg else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 结果区顶部：条数 + 已翻页数 + 已安装扫描状态 */
@Composable
private fun DiscoverResultsHeader(
    count: Int,
    pages: Int,
    scanningInstalled: Boolean,
    onlyInstalled: Boolean
) {
    OxideSectionLabel(
        text = stringResource(R.string.oxide_dis_result_count, count),
        trailing = {
            when {
                scanningInstalled -> OxideBadge(
                    text = stringResource(R.string.oxide_dis_installed_scanning),
                    tone = OxideBadgeTone.Warn
                )

                onlyInstalled -> OxideBadge(
                    text = stringResource(R.string.oxide_dis_tab_installed),
                    tone = OxideBadgeTone.Active
                )

                pages > 1 -> OxideBadge(
                    text = stringResource(R.string.oxide_dis_result_pages, pages),
                    tone = OxideBadgeTone.Neutral
                )
            }
        }
    )
}

/**
 * 搜索与筛选行
 *
 * 四个下拉与参考稿一一对应：Minecraft 版本、模组加载器、来源平台、排序方式。
 * 整行用 [FlowRow]，窄屏上自然换行，永远不会横向裁切。
 */
@Composable
private fun DiscoverFilterBar(
    viewModel: OxideDiscoverViewModel,
    minecraftVersions: List<MinecraftVersion>,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    val anyVersion = stringResource(R.string.oxide_dis_filter_any_version)
    val anyLoader = stringResource(R.string.oxide_dis_filter_any_loader)

    // CurseForge 只能按正式版过滤；版本表还没加载出来时先给出应用内置的热门版本
    val versionOptions = remember(
        minecraftVersions,
        viewModel.platform,
        viewModel.gameVersion,
        anyVersion
    ) {
        val real = minecraftVersions
            .filter { viewModel.platform != Platform.CURSEFORGE || it.type == MinecraftVersion.Type.Release }
            .map { it.version.id }
            .ifEmpty { popularVersions }
        buildList {
            add(anyVersion)
            addAll(real)
            val current = viewModel.gameVersion
            if (current.isNotBlank() && current !in this) add(current)
        }
    }
    val versionIndex = if (viewModel.gameVersion.isBlank()) {
        0
    } else {
        versionOptions.indexOf(viewModel.gameVersion).coerceAtLeast(0)
    }

    // 加载器：下标 0 是"任意加载器"，之后与 loaders 一一对应，
    // 所以 loaderOptions[i] 对应的就是 loaders[i - 1]
    val loaders = remember(viewModel.platform) { loadersFor(viewModel.platform) }
    val loaderOptions = remember(loaders, anyLoader) {
        buildList {
            add(anyLoader)
            loaders.forEach { add(it.getDisplayName()) }
        }
    }
    // 直接用下标，不用显示名反查：两个平台的枚举是不同的类型但共用
    // "Forge"/"Fabric" 这类同名显示名，比名字在跨平台残留时会命中错误的项，
    // 控件就会显示一个其实并没有被选中的加载器
    val loaderIndex = viewModel.modloader
        ?.let { selected -> loaders.indexOf(selected).takeIf { it >= 0 }?.plus(1) }
        ?: 0

    val sourceOptions = remember(viewModel.category) {
        if (viewModel.category.classes.supportsModrinth()) {
            Platform.entries.map { it.displayName }
        } else {
            listOf(Platform.CURSEFORGE.displayName)
        }
    }
    val sourceIndex = Platform.entries.indexOf(viewModel.platform).coerceAtLeast(0)

    val sortOptions = PlatformSortField.entries.map { stringResource(it.getDisplayName()) }
    val sortIndex = PlatformSortField.entries.indexOf(viewModel.sortField).coerceAtLeast(0)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
        verticalArrangement = Arrangement.spacedBy(metrics.cardGap)
    ) {
        DiscoverField(
            modifier = Modifier.width(metrics.searchFieldWidth),
            metrics = metrics,
            value = viewModel.query,
            onValueChange = { viewModel.updateQuery(it) },
            onSubmit = { viewModel.submitQuery(viewModel.query) },
            placeholder = stringResource(R.string.oxide_dis_search_hint)
        )

        OxideDropdown(
            modifier = Modifier.width(metrics.filterControlWidth),
            label = stringResource(R.string.oxide_dis_filter_version),
            options = versionOptions,
            selectedIndex = versionIndex,
            onSelect = { index ->
                viewModel.selectGameVersion(if (index == 0) "" else versionOptions.getOrNull(index).orEmpty())
            }
        )

        OxideDropdown(
            modifier = Modifier.width(metrics.filterControlWidth),
            label = stringResource(R.string.oxide_dis_filter_loader),
            options = loaderOptions,
            selectedIndex = loaderIndex,
            enabled = viewModel.category.loaderFilterable,
            onSelect = { index ->
                viewModel.selectModloader(if (index == 0) null else loaders.getOrNull(index - 1))
            }
        )

        OxideDropdown(
            modifier = Modifier.width(metrics.filterControlWidth),
            label = stringResource(R.string.oxide_dis_filter_source),
            options = sourceOptions,
            selectedIndex = sourceIndex,
            enabled = viewModel.category.classes.supportsModrinth(),
            onSelect = { index -> Platform.entries.getOrNull(index)?.let(viewModel::selectPlatform) }
        )

        OxideDropdown(
            modifier = Modifier.width(metrics.filterControlWidth),
            label = stringResource(R.string.oxide_dis_filter_sort),
            options = sortOptions,
            selectedIndex = sortIndex,
            onSelect = { index -> PlatformSortField.entries.getOrNull(index)?.let(viewModel::selectSort) }
        )

        if (viewModel.hasFilters) {
            OxideButton(
                text = stringResource(R.string.oxide_dis_clear_filters),
                onClick = viewModel::clearFilters,
                tone = OxideButtonTone.Ghost
            )
        }
    }
}

/**
 * 单行文本输入
 *
 * 外观与 [OxideDropdown] 完全一致（同样的高度、圆角、底色与描边），
 * 因此它们放在同一行时不会错位；宽度也由 [metrics] 推导。
 */
@Composable
private fun DiscoverField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .height(metrics.controlHeight)
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, if (focused) Oxide.Line2 else Oxide.Line), Oxide.RadiusControl)
            .imePanAnchor()
            .padding(horizontal = metrics.controlPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = Oxide.Type.Body.copy(color = Oxide.Fg),
            cursorBrush = SolidColor(Oxide.FgMuted),
            singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus(true)
                    onSubmit()
                },
                onDone = {
                    focusManager.clearFocus(true)
                    onSubmit()
                }
            ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.Body.fontSize,
                            lineHeight = Oxide.Type.Body.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/**
 * 结果网格的列数上限
 *
 * 参考稿 `.resultsGrid{grid-template-columns:repeat(2,minmax(0,1fr))}` 恒为两列。
 * 共享的 [OxideMetrics.gridColumns] 上限是 3，那是给实例网格
 * （`repeat(3,minmax(0,1fr))`）准备的，所以结果网格自己再夹一层，不动共享上限。
 */
private const val DISCOVER_RESULT_MAX_COLUMNS = 2

/**
 * 结果网格的列数
 *
 * 下限与按宽度推算的部分仍然交给 [OxideMetrics.gridColumns]（窗口被切分、
 * 折叠屏展开时列数会跟着变），只是上限收到 [DISCOVER_RESULT_MAX_COLUMNS]。
 */
internal fun discoverResultColumns(metrics: OxideMetrics, contentWidth: Dp): Int =
    metrics.gridColumns(contentWidth).coerceAtMost(DISCOVER_RESULT_MAX_COLUMNS)

/**
 * 结果网格
 *
 * 列数由 [discoverResultColumns] 按实际内容宽度算出，但封顶两列，与参考稿一致。
 * 五种状态各自独立：首次加载、正在翻下一页、确实没有结果、失败、没有更多。
 * 失败永远带着重试，而且**不会**被渲染成一个空列表。
 */
@Composable
private fun DiscoverResultsGrid(
    feed: DiscoverFeed,
    visible: List<DiscoverItem>,
    category: DiscoverCategory,
    installedIds: Set<String>,
    onlyInstalled: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (DiscoverItem) -> Unit,
    onInstall: (DiscoverItem) -> Unit,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    // 接近末尾就去取下一页：翻页是一次网络往返，等滚到底再发会让列表先停住再跳
    val shouldLoadMore by remember(feed, visible.size, onlyInstalled) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            discoverShouldPrefetch(lastVisible, visible.size, feed.phase, feed.endOfResults) ||
                    // 「已安装」筛选后可能一条都不剩，只能靠继续翻来找
                    (visible.isEmpty() && feed.canLoadMore())
        }
    }
    LaunchedEffect(shouldLoadMore, feed.pages, visible.size) {
        if (shouldLoadMore) onLoadMore()
    }

    when (feed.view()) {
        DiscoverFeedView.Idle, DiscoverFeedView.Loading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                OxideLoadingRow(text = stringResource(R.string.oxide_dis_loading))
            }
        }

        DiscoverFeedView.Error -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_error_title),
                    detail = feed.error?.toAndroidString(context),
                    action = {
                        OxideButton(
                            text = stringResource(R.string.oxide_dis_retry),
                            onClick = onRetry,
                            tone = OxideButtonTone.Primary
                        )
                    }
                )
            }
        }

        DiscoverFeedView.NoResults -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                if (onlyInstalled) {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_dis_empty_installed_title),
                        detail = stringResource(R.string.oxide_dis_empty_installed_detail),
                        action = {
                            OxideButton(
                                text = stringResource(R.string.oxide_dis_refresh),
                                onClick = onRetry,
                                tone = OxideButtonTone.Secondary
                            )
                        }
                    )
                } else {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_dis_empty_title),
                        detail = stringResource(R.string.oxide_dis_empty_detail),
                        action = {
                            OxideButton(
                                text = stringResource(R.string.oxide_dis_retry),
                                onClick = onRetry,
                                tone = OxideButtonTone.Secondary
                            )
                        }
                    )
                }
            }
        }

        DiscoverFeedView.List, DiscoverFeedView.ListWithError, DiscoverFeedView.LoadingNext -> {
            BoxWithConstraints(modifier = modifier) {
                val columns = discoverResultColumns(metrics, maxWidth)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.key }) { item ->
                        DiscoverResultCard(
                            modifier = Modifier.fillMaxWidth(),
                            metrics = metrics,
                            item = item,
                            category = category,
                            installed = item.data.platformId() in installedIds,
                            busy = busy,
                            onOpen = { onOpen(item) },
                            onInstall = { onInstall(item) }
                        )
                    }

                    // 尾部状态：翻页中 / 失败重试 / 到底了，三者各自独立
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DiscoverFeedFooter(
                            feed = feed,
                            context = context,
                            metrics = metrics,
                            onRetry = onRetry,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 列表尾部的状态行
 *
 * "正在翻下一页""这一页失败了，点这里重试""已经没有更多了"是三种不同的事实，
 * 所以三种文案分开写；翻页失败时列表仍然可用，只有这一行在提示失败。
 */
@Composable
private fun DiscoverFeedFooter(
    feed: DiscoverFeed,
    context: Context,
    metrics: OxideMetrics,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when (feed.view()) {
        DiscoverFeedView.ListWithError -> {
            OxideSurface(
                shape = Oxide.RadiusBlock,
                contentPadding = PaddingValues(all = metrics.cardGap * 0.8f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feed.error?.toAndroidString(context).orEmpty(),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(metrics.controlPadding))
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_paging_retry),
                        onClick = onRetry,
                        tone = OxideButtonTone.Primary
                    )
                }
            }
        }

        DiscoverFeedView.LoadingNext -> {
            OxideLoadingRow(text = stringResource(R.string.oxide_dis_paging_loading))
        }

        DiscoverFeedView.List -> {
            if (feed.endOfResults) {
                Text(
                    text = stringResource(R.string.oxide_dis_paging_end),
                    color = Oxide.FgGhost,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = metrics.controlPadding)
                )
            } else {
                // 还有下一页但用户还没滚过去：给一个明确的按钮，不用猜
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = metrics.controlPadding),
                    contentAlignment = Alignment.Center
                ) {
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_paging_more),
                        onClick = onLoadMore,
                        tone = OxideButtonTone.Secondary
                    )
                }
            }
        }

        else -> Unit
    }
}

/**
 * 结果卡片
 *
 * 参考稿是三列（图标 / 文本 / 操作），这里把操作放到第二行末尾：
 * 卡片在紧凑档下只有不到 200dp 宽，三列会把标题挤没，底部的操作行在任何宽度下都不会重叠。
 */
@Composable
private fun DiscoverResultCard(
    item: DiscoverItem,
    category: DiscoverCategory,
    installed: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconUrl = remember(item) { item.data.platformIconUrl() }
    val author = remember(item) { item.data.platformAuthor().takeIf { it.isNotBlank() } }
    val downloads = remember(item) { formatNumberByLocale(context, item.data.platformDownloadCount()) }

    OxideSurface(
        modifier = modifier,
        onClick = onOpen,
        shape = Oxide.RadiusCard,
        contentPadding = PaddingValues(all = metrics.cardGap)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AssetsIcon(
                modifier = Modifier.clip(Oxide.RadiusSmall),
                size = metrics.resultIconSize,
                iconUrl = iconUrl
            )
            Spacer(Modifier.width(metrics.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Title.fontSize,
                    lineHeight = Oxide.Type.Title.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(metrics.cardGap * 0.35f))
                Text(
                    text = author?.let {
                        stringResource(R.string.oxide_dis_by, it) + " · " + downloads
                    } ?: downloads,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(metrics.cardGap * 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap * 0.4f)) {
                    OxideBadge(
                        text = stringResource(category.classes.typeLabelRes()),
                        tone = OxideBadgeTone.Neutral
                    )
                    OxideBadge(
                        text = item.data.platform().displayName,
                        tone = OxideBadgeTone.Neutral
                    )
                    item.loaderLabel?.let {
                        OxideBadge(text = it, tone = OxideBadgeTone.Neutral)
                    }
                    if (installed) {
                        OxideBadge(
                            text = stringResource(R.string.oxide_dis_badge_installed),
                            tone = OxideBadgeTone.Active
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(metrics.cardGap * 0.7f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OxideButton(
                text = stringResource(R.string.oxide_dis_action_install),
                onClick = onInstall,
                enabled = !busy,
                tone = OxideButtonTone.Primary
            )
        }
    }
}

/** 项目详情抽屉：概览、文件与依赖三个标签页 */
@Composable
private fun DiscoverDetailHost(
    detail: DiscoverDetail?,
    currentVersionName: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (DiscoverItem) -> Unit,
    onInstallVersion: (DiscoverItem, PlatformVersion) -> Unit,
    onToggleDependency: (String, Boolean) -> Unit,
    onRetryDependency: (String) -> Unit,
    onDownloadAll: () -> Unit,
    metrics: OxideMetrics
) {
    var tab by remember { mutableIntStateOf(0) }

    OxideDrawerHost(
        visible = detail != null,
        metrics = metrics,
        onDismiss = onDismiss,
        title = detail?.item?.title.orEmpty(),
        content = {
            val current = detail ?: return@OxideDrawerHost

            OxideDrawerTabs(
                tabs = listOf(
                    stringResource(R.string.oxide_dis_drawer_tab_overview),
                    stringResource(R.string.oxide_dis_drawer_tab_files),
                    stringResource(R.string.oxide_dis_drawer_tab_dependencies)
                ),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(metrics.cardGap))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (tab) {
                    0 -> DiscoverOverview(
                        detail = current,
                        currentVersionName = currentVersionName,
                        busy = busy,
                        onInstall = { onInstall(current.item) },
                        metrics = metrics
                    )

                    1 -> DiscoverFiles(
                        item = current.item,
                        files = current.files,
                        busy = busy,
                        onInstall = { version -> onInstallVersion(current.item, version) },
                        metrics = metrics
                    )

                    else -> DiscoverDependencySection(
                        state = current.dependencies,
                        showDownloadAll = true,
                        busy = busy,
                        onToggle = onToggleDependency,
                        onRetry = onRetryDependency,
                        onDownloadAll = onDownloadAll,
                        metrics = metrics
                    )
                }
            }
        }
    )
}

/** 概览：项目的真实信息 + 安装入口 */
@Composable
private fun DiscoverOverview(
    detail: DiscoverDetail,
    currentVersionName: String?,
    busy: Boolean,
    onInstall: () -> Unit,
    metrics: OxideMetrics
) {
    val context = LocalContext.current
    val loaded = detail.project as? DiscoverProjectState.Loaded

    Column(modifier = Modifier.fillMaxWidth()) {
        if (loaded == null) {
            when (val state = detail.project) {
                is DiscoverProjectState.Loading -> {
                    OxideLoadingRow(text = stringResource(R.string.oxide_dis_detail_loading))
                }

                is DiscoverProjectState.Failed -> {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_dis_detail_failed),
                        detail = state.message.toAndroidString(context)
                    )
                }

                is DiscoverProjectState.Loaded -> Unit
            }
            return@Column
        }

        val project = loaded.project
        val classes = detail.item.classes
        val authors = remember(project) {
            project.platformAuthors().joinToString(", ").takeIf { it.isNotBlank() }
        }
        val downloads = remember(project) {
            formatNumberByLocale(context, project.platformDownloadCount())
        }
        val follows = remember(project) {
            project.platformFollows()?.let { formatNumberByLocale(context, it) }
        }
        val loaders = remember(project) {
            project.platformModLoaders()
                ?.mapNotNull { it.getDisplayName().takeIf { name -> name.isNotBlank() } }
        }
        val available = remember(project) { project.platformAvailable() }

        Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap * 0.4f)) {
            OxideBadge(
                text = stringResource(classes.typeLabelRes()),
                tone = OxideBadgeTone.Neutral
            )
            OxideBadge(
                text = detail.item.data.platform().displayName,
                tone = OxideBadgeTone.Neutral
            )
            loaders?.forEach {
                OxideBadge(text = it, tone = OxideBadgeTone.Neutral)
            }
            if (!available) {
                OxideBadge(
                    text = stringResource(R.string.oxide_dis_detail_file_unavailable),
                    tone = OxideBadgeTone.Warn
                )
            }
        }

        Spacer(Modifier.height(metrics.cardGap * 0.7f))

        OxideSettingRow(
            label = stringResource(R.string.oxide_dis_detail_downloads_label),
            value = downloads
        )
        follows?.let {
            OxideSettingRow(
                label = stringResource(R.string.oxide_dis_detail_follows_label),
                value = it
            )
        }
        authors?.let {
            OxideSettingRow(
                label = stringResource(R.string.oxide_dis_detail_authors_label),
                value = it
            )
        }
        OxideSettingRow(
            label = stringResource(R.string.oxide_dis_detail_target),
            value = currentVersionName ?: stringResource(R.string.oxide_dis_detail_target_none)
        )

        Spacer(Modifier.height(metrics.cardGap * 0.7f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OxideButton(
                text = stringResource(R.string.oxide_dis_action_install),
                onClick = onInstall,
                enabled = !busy && available,
                tone = OxideButtonTone.Primary
            )
        }

        Spacer(Modifier.height(metrics.sectionGap))

        OxideSectionLabel(text = stringResource(R.string.oxide_dis_detail_summary))
        Spacer(Modifier.height(metrics.controlPadding))
        val summary = remember(project) {
            project.platformSummary()?.takeIf { it.isNotBlank() }
        }
        Text(
            text = summary ?: stringResource(R.string.oxide_dis_detail_no_summary),
            color = if (summary != null) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight
        )
    }
}

/** 文件列表：安装指定的文件版本 */
@Composable
private fun DiscoverFiles(
    item: DiscoverItem,
    files: DiscoverFilesState,
    busy: Boolean,
    onInstall: (PlatformVersion) -> Unit,
    metrics: OxideMetrics
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        when (files) {
            is DiscoverFilesState.Loading -> {
                OxideLoadingRow(text = stringResource(R.string.oxide_dis_detail_files_loading))
            }

            is DiscoverFilesState.Empty -> {
                OxideEmptyState(title = stringResource(R.string.oxide_dis_detail_files_empty))
            }

            is DiscoverFilesState.Failed -> {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_detail_files_failed),
                    detail = files.message.toAndroidString(context)
                )
            }

            is DiscoverFilesState.Loaded -> {
                files.versions.forEach { version ->
                    DiscoverFileRow(
                        version = version,
                        busy = busy,
                        onInstall = { onInstall(version) },
                        metrics = metrics
                    )
                    Spacer(Modifier.height(metrics.cardGap * 0.6f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverFileRow(
    version: PlatformVersion,
    busy: Boolean,
    onInstall: () -> Unit,
    metrics: OxideMetrics
) {
    OxideSurface(
        shape = Oxide.RadiusBlock,
        contentPadding = PaddingValues(
            start = metrics.cardGap,
            end = metrics.cardGap,
            top = metrics.cardGap * 0.7f,
            bottom = metrics.cardGap * 0.7f
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.platformDisplayName(),
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.oxide_dis_detail_file_meta,
                        version.platformVersion(),
                        formatFileSize(version.platformFileSize())
                    ),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(metrics.cardGap * 0.7f))
            OxideButton(
                text = stringResource(R.string.oxide_dis_action_install),
                onClick = onInstall,
                enabled = !busy,
                tone = OxideButtonTone.Primary
            )
        }
    }
}

/**
 * 依赖列表
 *
 * 每一条都显示它真实的类型、真实解析到的版本，以及能不能勾选：
 * 已内嵌与明确互斥的依赖是灰的且点不动；读失败的单独给一个重试，
 * 其余条目照旧可用——一个失败不会静默吃掉整张列表。
 */
@Composable
private fun DiscoverDependencySection(
    state: DiscoverDependenciesState,
    showDownloadAll: Boolean,
    busy: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    onDownloadAll: () -> Unit,
    metrics: OxideMetrics,
    selectedCount: Int? = null,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is DiscoverDependenciesState.Loading -> {
                OxideLoadingRow(text = stringResource(R.string.oxide_dis_deps_loading))
            }

            is DiscoverDependenciesState.Empty -> {
                OxideEmptyState(title = stringResource(R.string.oxide_dis_deps_empty))
            }

            is DiscoverDependenciesState.Failed -> {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_deps_failed),
                    detail = state.message.toAndroidString(context)
                )
            }

            is DiscoverDependenciesState.Loaded -> {
                if (showDownloadAll) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap, Alignment.End)
                    ) {
                        OxideButton(
                            text = stringResource(R.string.oxide_dis_deps_download_all),
                            onClick = onDownloadAll,
                            enabled = !busy && state.rows.any { it.selected && it.request != null },
                            tone = OxideButtonTone.Primary
                        )
                    }
                    Spacer(Modifier.height(metrics.cardGap * 0.7f))
                }

                selectedCount?.let { count ->
                    Text(
                        text = stringResource(R.string.oxide_dis_deps_selected, count),
                        color = Oxide.FgDim,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(metrics.cardGap * 0.5f))
                }

                state.rows.forEach { row ->
                    DiscoverDependencyRowView(
                        row = row,
                        onToggle = { checked -> onToggle(row.dependency.key, checked) },
                        onRetry = { onRetry(row.dependency.key) },
                        metrics = metrics
                    )
                    Spacer(Modifier.height(metrics.cardGap * 0.5f))
                }
            }
        }
    }
}

/** 一个依赖的行：类型 + 版本 + 勾选 + 独立的失败与重试 */
@Composable
private fun DiscoverDependencyRowView(
    row: DiscoverDependencyRow,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
    metrics: OxideMetrics
) {
    val context = LocalContext.current
    val dependency = row.dependency
    val enabled = dependency.installable

    OxideSurface(
        shape = Oxide.RadiusBlock,
        contentPadding = PaddingValues(all = metrics.cardGap * 0.8f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dependency.displayName(),
                    color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(metrics.cardGap * 0.25f))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap * 0.4f)) {
                    OxideBadge(
                        text = stringResource(dependency.type.typeLabelRes()),
                        tone = if (dependency.type == PlatformDependencyType.REQUIRED) {
                            OxideBadgeTone.Warn
                        } else {
                            OxideBadgeTone.Neutral
                        }
                    )
                    OxideBadge(text = dependency.platform.displayName, tone = OxideBadgeTone.Neutral)
                }

                val detailText = when (val state = row.state) {
                    is DiscoverDependencyState.Resolving -> stringResource(R.string.oxide_dis_deps_resolving)
                    is DiscoverDependencyState.Resolved -> stringResource(
                        R.string.oxide_dis_dep_meta,
                        state.versionLabel,
                        state.gameVersions
                    )

                    is DiscoverDependencyState.NoCompatibleVersion -> stringResource(
                        R.string.oxide_dis_dep_no_version
                    )

                    is DiscoverDependencyState.Failed -> state.message.toAndroidString(context)
                }
                if (detailText.isNotBlank()) {
                    Spacer(Modifier.height(metrics.cardGap * 0.3f))
                    Text(
                        text = detailText,
                        color = if (row.state is DiscoverDependencyState.Failed) Oxide.FgMuted else Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (row.state.retryable()) {
                    Spacer(Modifier.height(metrics.cardGap * 0.4f))
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_dep_retry),
                        onClick = onRetry,
                        tone = OxideButtonTone.Ghost
                    )
                }

                row.downloadError?.let { error ->
                    Spacer(Modifier.height(metrics.cardGap * 0.3f))
                    Text(
                        text = error.toAndroidString(context),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(metrics.cardGap * 0.4f))
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_dep_retry),
                        onClick = onRetry,
                        tone = OxideButtonTone.Secondary
                    )
                }
            }

            Spacer(Modifier.width(metrics.controlPadding))

            // 勾选状态由整行承载，开关本身不再重复朗读
            Box(
                modifier = Modifier
                    .toggleable(
                        value = row.selected,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onToggle,
                    )
                    .semantics { contentDescription = dependency.displayName() }
            ) {
                OxideToggle(
                    checked = row.selected,
                    onCheckedChange = { next ->
                        if (enabled) onToggle(next)
                    },
                )
            }
        }
    }
}

/**
 * 安装前确认层
 *
 * 只有模组与整合包会走到这里（门槛见 [discoverUsesInstallSheet]）：
 * 点"安装"先落到这一层，确认 Minecraft 版本与依赖之后才开始下载。
 * 光影、资源包、存档、地图不经过这里。
 */
@Composable
private fun DiscoverInstallSheetHost(
    sheet: DiscoverSheet?,
    localVersions: List<Version>,
    minecraftVersions: List<MinecraftVersion>,
    instanceNameProblem: AndroidStringText?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelectVersion: (String?) -> Unit,
    onToggleDependency: (String, Boolean) -> Unit,
    onRetryDependency: (String) -> Unit,
    onInstanceNameChange: (String) -> Unit,
    onGoInstances: () -> Unit,
    onConfirm: () -> Unit,
    metrics: OxideMetrics
) {
    if (sheet == null) return
    val context = LocalContext.current

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onDismiss,
        title = stringResource(R.string.oxide_dis_sheet_title)
    ) {
        when (sheet) {
            is DiscoverSheet.Resolving -> {
                OxideLoadingRow(text = stringResource(R.string.oxide_dis_sheet_resolving))
            }

            is DiscoverSheet.Failed -> {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_sheet_failed),
                    detail = sheet.message.toAndroidString(context)
                )
                Spacer(Modifier.height(metrics.cardGap))
                DiscoverDialogActions(
                    metrics = metrics,
                    confirmText = stringResource(R.string.oxide_dis_retry),
                    confirmEnabled = false,
                    onCancel = onDismiss,
                    onConfirm = {}
                )
            }

            is DiscoverSheet.Ready -> DiscoverInstallSheetBody(
                sheet = sheet.content,
                localVersions = localVersions,
                minecraftVersions = minecraftVersions,
                instanceNameProblem = instanceNameProblem,
                busy = busy,
                onDismiss = onDismiss,
                onSelectVersion = onSelectVersion,
                onToggleDependency = onToggleDependency,
                onRetryDependency = onRetryDependency,
                onInstanceNameChange = onInstanceNameChange,
                onGoInstances = onGoInstances,
                onConfirm = onConfirm,
                metrics = metrics
            )
        }
    }
}

@Composable
private fun DiscoverInstallSheetBody(
    sheet: DiscoverInstallSheet,
    localVersions: List<Version>,
    minecraftVersions: List<MinecraftVersion>,
    instanceNameProblem: AndroidStringText?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelectVersion: (String?) -> Unit,
    onToggleDependency: (String, Boolean) -> Unit,
    onRetryDependency: (String) -> Unit,
    onInstanceNameChange: (String) -> Unit,
    onGoInstances: () -> Unit,
    onConfirm: () -> Unit,
    metrics: OxideMetrics
) {
    val context = LocalContext.current
    val choice = discoverResolveVersion(sheet.chosenVersionName, sheet.detectedVersionName)
    val isModpack = sheet.classes == PlatformClasses.MOD_PACK
    val plan = sheet.plan()

    OxideSectionLabel(text = stringResource(R.string.oxide_dis_sheet_file))
    Spacer(Modifier.height(metrics.controlPadding))
    Text(
        text = sheet.version.platformDisplayName(),
        color = Oxide.Fg,
        fontSize = Oxide.Type.Body.fontSize,
        lineHeight = Oxide.Type.Body.lineHeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(metrics.cardGap * 0.3f))
    Text(
        text = stringResource(
            R.string.oxide_dis_sheet_file_meta,
            sheet.version.platformVersion(),
            formatFileSize(sheet.version.platformFileSize())
        ),
        color = Oxide.FgFaint,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    if (isModpack) {
        Spacer(Modifier.height(metrics.sectionGap))
        OxideSectionLabel(text = stringResource(R.string.oxide_dis_modpack_name_label))
        Spacer(Modifier.height(metrics.controlPadding))
        DiscoverField(
            modifier = Modifier.fillMaxWidth(),
            metrics = metrics,
            value = sheet.instanceName,
            onValueChange = onInstanceNameChange,
            onSubmit = { if (instanceNameProblem == null) onConfirm() },
            placeholder = sheet.item.title,
            imeAction = ImeAction.Done
        )
        instanceNameProblem?.let {
            Spacer(Modifier.height(metrics.controlPadding))
            Text(
                text = it.toAndroidString(context),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(metrics.controlPadding))
        Text(
            text = stringResource(R.string.oxide_dis_modpack_dialog_detail),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight
        )
    } else {
        Spacer(Modifier.height(metrics.sectionGap))
        OxideSectionLabel(text = stringResource(R.string.oxide_dis_sheet_version_label))
        Spacer(Modifier.height(metrics.controlPadding))

        // 目标版本：先列本地已装的实例，其余用版本表补齐
        val versionOptions = remember(localVersions, minecraftVersions) {
            val known = localVersions.mapTo(LinkedHashSet()) { it.getVersionName() }
            buildList {
                addAll(known)
                addAll(
                    minecraftVersions
                        .filter { it.version.id.isNotBlank() && it.version.id !in known }
                        .map { it.version.id }
                )
            }
        }
        val selectedIndex = choice.name?.let { versionOptions.indexOf(it) }?.takeIf { it >= 0 } ?: -1

        if (versionOptions.isEmpty()) {
            Text(
                text = stringResource(R.string.oxide_dis_sheet_no_version_list),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight
            )
        } else {
            OxideDropdown(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.oxide_dis_sheet_version_detected),
                options = versionOptions,
                selectedIndex = selectedIndex,
                enabled = !busy,
                placeholder = choice.name.orEmpty(),
                onSelect = { index -> onSelectVersion(versionOptions.getOrNull(index)) },
            )
        }

        Spacer(Modifier.height(metrics.cardGap * 0.5f))
        Text(
            text = when {
                choice.name == null -> stringResource(R.string.oxide_dis_sheet_no_instance)
                choice.detected -> stringResource(R.string.oxide_dis_sheet_version_auto, choice.name.orEmpty())
                else -> stringResource(R.string.oxide_dis_sheet_version_chosen, choice.name.orEmpty())
            },
            color = if (choice.name == null) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 目标版本本地没有：明说会连游戏版本一起装，并由确认按钮让用户点头
        if (plan.installGameVersion) {
            Spacer(Modifier.height(metrics.controlPadding))
            Text(
                text = stringResource(R.string.oxide_dis_sheet_game_missing, choice.name.orEmpty()),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (choice.name == null) {
            Spacer(Modifier.height(metrics.cardGap * 0.5f))
            OxideButton(
                text = stringResource(R.string.oxide_dis_install_go_instances),
                onClick = onGoInstances,
                tone = OxideButtonTone.Secondary
            )
        }

        Spacer(Modifier.height(metrics.sectionGap))
        DiscoverDependencySection(
            state = if (sheet.dependencies.isEmpty()) {
                DiscoverDependenciesState.Empty
            } else {
                DiscoverDependenciesState.Loaded(sheet.dependencies)
            },
            showDownloadAll = false,
            busy = busy,
            onToggle = onToggleDependency,
            onRetry = onRetryDependency,
            onDownloadAll = {},
            metrics = metrics,
            selectedCount = plan.dependencyKeys.size,
        )
    }

    Spacer(Modifier.height(metrics.sectionGap))

    val confirmEnabled = if (isModpack) {
        instanceNameProblem == null && !busy
    } else {
        plan.hasTarget && !busy
    }
    val confirmText = when {
        isModpack -> stringResource(R.string.oxide_dis_action_install)
        // 勾了依赖就说"全部下载"，一个依赖都没有就直接说"安装"
        plan.hasDependencies -> stringResource(R.string.oxide_dis_deps_download_all)
        plan.installGameVersion -> stringResource(R.string.oxide_dis_sheet_confirm_game, choice.name.orEmpty())
        else -> stringResource(R.string.oxide_dis_action_install)
    }

    DiscoverDialogActions(
        metrics = metrics,
        confirmText = confirmText,
        confirmEnabled = confirmEnabled,
        onCancel = onDismiss,
        onConfirm = onConfirm
    )
}

/** 整合包安装过程：任务流抽屉 */
@Composable
private fun DiscoverInstallDrawer(
    installer: ModPackInstaller?,
    metrics: OxideMetrics,
    onCancel: () -> Unit
) {
    if (installer == null) return

    val tasks by installer.tasksFlow.collectAsStateWithLifecycle()

    OxideDrawerHost(
        visible = true,
        metrics = metrics,
        onDismiss = onCancel,
        title = stringResource(R.string.oxide_dis_install_drawer_title)
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_dis_install_drawer_tasks))
        Spacer(Modifier.height(metrics.cardGap))

        if (tasks.isEmpty()) {
            OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                tasks.forEach { task ->
                    DiscoverTaskRow(task = task, metrics = metrics)
                    Spacer(Modifier.height(metrics.cardGap * 0.6f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverTaskRow(task: TitledTask, metrics: OxideMetrics) {
    val stage by task.task.stage.collectAsStateWithLifecycle()
    val progress by task.task.progress.collectAsStateWithLifecycle()
    val message by task.task.message.collectAsStateWithLifecycle()

    OxideSurface(
        shape = Oxide.RadiusBlock,
        contentPadding = PaddingValues(all = metrics.cardGap * 0.8f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AndroidStringText(
                text = task.title,
                modifier = Modifier.weight(1f),
                style = Oxide.Type.Body.copy(color = Oxide.Fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(metrics.controlPadding))
            OxideBadge(
                text = stringResource(stage.labelRes()),
                tone = if (stage == TaskStage.COMPLETED) OxideBadgeTone.Active else OxideBadgeTone.Neutral
            )
        }

        if (progress >= 0f) {
            Spacer(Modifier.height(metrics.cardGap * 0.6f))
            OxideProgressBar(progress = progress)
        }

        message?.let {
            Spacer(Modifier.height(metrics.cardGap * 0.4f))
            AndroidStringText(
                text = it,
                style = Oxide.Type.MicroLabel.copy(color = Oxide.FgFaint),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 移动网络确认：安装器会在这里挂起 */
@Composable
private fun DiscoverMobileDataDialog(
    metrics: OxideMetrics,
    onDeny: () -> Unit,
    onAllow: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Oxide.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDeny
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.pagePaddingH * 2f)
                .padding(vertical = metrics.pagePaddingV),
            contentAlignment = Alignment.Center
        ) {
            OxideSurface(
                modifier = Modifier.width(metrics.drawerWidth * 0.82f),
                shape = Oxide.RadiusDrawer,
                contentPadding = PaddingValues(all = metrics.cardGap * 1.2f)
            ) {
                OxideSectionLabel(text = stringResource(R.string.oxide_dis_mobile_title))
                Spacer(Modifier.height(metrics.cardGap * 0.8f))
                Text(
                    text = stringResource(R.string.oxide_dis_mobile_detail),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight
                )
                Spacer(Modifier.height(metrics.cardGap))
                DiscoverDialogActions(
                    metrics = metrics,
                    confirmText = stringResource(R.string.oxide_dis_mobile_allow),
                    confirmEnabled = true,
                    onCancel = onDeny,
                    onConfirm = onAllow
                )
            }
        }
    }
}

@Composable
private fun DiscoverDialogActions(
    confirmText: String,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    metrics: OxideMetrics
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap, Alignment.End)
    ) {
        OxideButton(
            text = stringResource(R.string.oxide_dis_cancel),
            onClick = onCancel,
            tone = OxideButtonTone.Ghost
        )
        OxideButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            tone = OxideButtonTone.Primary
        )
    }
}

/** 右下角的安装提示：只展示真实的安装状态与安装器回报的错误 */
@Composable
private fun DiscoverInstallNotice(
    install: DiscoverInstall,
    error: ErrorViewModel.ThrowableMessage?,
    onGoInstances: () -> Unit,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
    metrics: OxideMetrics
) {
    val notice: DiscoverNotice? = when {
        error != null -> DiscoverNotice(
            title = stringResource(R.string.oxide_dis_install_failed_title),
            detail = error.message
        )

        else -> when (install) {
            is DiscoverInstall.Idle -> null
            is DiscoverInstall.Resolving -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_resolving),
                detail = null
            )

            is DiscoverInstall.NeedsInstance -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_needs_instance_title),
                detail = androidText(R.string.oxide_dis_install_needs_instance_detail),
                action = {
                    OxideButton(
                        text = stringResource(R.string.oxide_dis_install_go_instances),
                        onClick = onGoInstances,
                        tone = OxideButtonTone.Primary
                    )
                }
            )

            is DiscoverInstall.NoFile -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_no_file_title),
                detail = androidText(R.string.oxide_dis_install_no_file_detail)
            )

            is DiscoverInstall.Queued -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_queued_title),
                detail = androidText(R.string.oxide_dis_install_queued_detail)
            )

            is DiscoverInstall.Created -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_created_title),
                detail = androidText(R.string.oxide_dis_install_created_detail, install.instanceName)
            )

            is DiscoverInstall.Failed -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_failed_title),
                detail = install.message
            )

            is DiscoverInstall.Cancelled -> DiscoverNotice(
                title = stringResource(R.string.oxide_dis_install_cancelled_title),
                detail = androidText(R.string.oxide_dis_install_cancelled_detail)
            )
        }
    }

    if (notice == null) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        OxideSurface(
            modifier = Modifier
                .padding(end = metrics.pagePaddingH, bottom = metrics.pagePaddingH)
                .widthIn(max = metrics.drawerWidth),
            shape = Oxide.RadiusBlock,
            contentPadding = PaddingValues(
                start = metrics.cardGap,
                end = metrics.controlPadding,
                top = metrics.cardGap * 0.8f,
                bottom = metrics.cardGap * 0.8f
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notice.title,
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    notice.detail?.let {
                        Spacer(Modifier.height(2.dp))
                        AndroidStringText(
                            text = it,
                            style = Oxide.Type.MicroLabel.copy(color = Oxide.FgFaint),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                notice.action?.let {
                    Spacer(Modifier.width(metrics.controlPadding))
                    it()
                }
                Spacer(Modifier.width(metrics.controlPadding))
                DiscoverCloseButton(
                    metrics = metrics,
                    onClick = if (error != null) onDismissError else onDismiss
                )
            }
        }
    }
}

/** 只有图标的关闭按钮：带点击语义与无障碍描述 */
@Composable
private fun DiscoverCloseButton(metrics: OxideMetrics, onClick: () -> Unit) {
    val label = stringResource(R.string.oxide_dis_dismiss)
    Box(
        modifier = Modifier
            .size(metrics.navItemHeight * 0.7f)
            .clip(Oxide.RadiusControl)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u2715",
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1
        )
    }
}