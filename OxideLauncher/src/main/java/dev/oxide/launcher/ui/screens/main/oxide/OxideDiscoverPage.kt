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
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.game.download.assets.DependencyRequest
import dev.oxide.launcher.game.download.assets.favorites.FavoriteProjectsRepository
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "OxideDiscoverPage"

/**
 * 依赖解析的并发度
 *
 * 与依赖下载链路自己的 `DEPENDENCY_PARALLELISM` 同量级：先把 N 条依赖的往返摊平，
 * 又不至于把平台打到一个会被限流的程度。
 */
private const val DEPENDENCY_READ_PARALLELISM = 4

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

/**
 * 依赖行左侧项目图标的边长
 *
 * 依赖行比结果卡片密得多——一行里还要放下名字、作者、类型、版本与次要信息，
 * 所以图标按结果卡片的同一个基准（[navItemHeight]）收窄，而不是另写一个 dp。
 */
private val OxideMetrics.dependencyIconSize: Dp get() = navItemHeight * 0.62f

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
 *
 * 它是 `public` 而不是 `internal`：实例内容管理器里"在发现页查看"那条宿主动作要带着
 * 它穿过 [OxideHostActions]，而那是一个 public 的类——public 的属性不能露出 internal 的类型。
 */
enum class DiscoverCategory(
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

    /** 已经交给任务系统开始下载；[queue] 带着真实的进度与真实的任务数 */
    data class Queued(val queue: DiscoverQueueRow) : DiscoverInstall

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

/**
 * 详情抽屉与确认层里依赖列表的状态
 *
 * [NoFileForTarget] 是这一轮补上的第四种：目标实例这一档压根没有兼容文件。
 * 它此前被并进了 [Empty]，于是界面上印出 "This file has no dependencies."——
 * 而用户点安装时走的确认层却不退回 `versions.first()`，于是两处给出相反的答案。
 * "没有依赖"与"没有文件给你装"是完全不同的两件事，必须各有一句。
 *
 * 这些类型是 `internal` 而不是 `private`：行模型里只有平台自己那份依赖关系的快照与
 * 依赖请求（都是普通数据），因此 `DiscoverDependencySection` 能在 layoutlib 下渲染，
 * 快照测试也才画得出 [NoFileForTarget]（见 `OxideDiscoverSnapshotTest`）。
 */
internal sealed interface DiscoverDependenciesState {
    data object Loading : DiscoverDependenciesState
    data object Empty : DiscoverDependenciesState

    /** 目标这一档没有兼容文件；[message] 是"为什么没有" */
    data class NoFileForTarget(val message: AndroidStringText) : DiscoverDependenciesState

    data class Loaded(val rows: List<DiscoverDependencyRow>) : DiscoverDependenciesState
    data class Failed(val message: AndroidStringText) : DiscoverDependenciesState
}

/**
 * 一个依赖在界面上的完整状态
 *
 * 每一条各自持有读取结果、勾选、下载错误与可重试的请求：其中一条失败不能把其余的
 * 判死，所以状态是逐条的一行，而不是一份统一的"依赖安装失败"。
 *
 * [card] 里的字段全部在解析时就算好，绘制与测量因此不碰平台模型、不读磁盘、不发网络。
 * 整行因此也只是普通数据，`internal` 之后可以交给快照测试去画。
 *
 * @param request 已经解析出的真实依赖请求；重试直接复用它，不重新猜
 */
internal data class DiscoverDependencyRow(
    val dependency: DiscoverDependency,
    val card: DiscoverDependencyCard,
    val selected: Boolean,
    val request: DependencyRequest? = null,
    val downloadError: AndroidStringText? = null,
)

/**
 * 一个依赖的解析结果
 *
 * [projectId] 是从解析出来的文件版本上拿到的——Modrinth 只给精确版本 id 时，
 * 项目 id 要等 `GET /version/{id}` 回来才知道，所以先解析版本、再查项目。
 */
private data class DiscoverDependencyResolution(
    val dependency: DiscoverDependency,
    val version: PlatformVersion?,
    val projectId: String?,
    val state: DiscoverDependencyState,
    val request: DependencyRequest?,
) {
    /** 去重之后才会去查项目；项目 id 为 null 的依赖无从查起 */
    val target: DiscoverDependencyTarget?
        get() = projectId?.takeIf { it.isNotBlank() }
            ?.let { DiscoverDependencyTarget(dependency.platform, it) }
}

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
 * @param version 已为这个目标解析出的项目文件版本；**目标这一档没有兼容文件时为 null**，
 *   此时这一层仍然要开着（用户得能改目标版本），只是不能确认
 * @param target 这一次安装解析出来的真实目标，确认层照它显示"装到哪一档"
 * @param chosenVersionName 用户改过的目标条目；null 表示仍跟随自动检测
 * @param noFileMessage 目标这一档没有兼容文件时的原因；确认层照它显示，且不允许确认
 */
private data class DiscoverInstallSheet(
    val item: DiscoverItem,
    /** 已为这个目标版本解析出的项目文件版本；没有兼容文件时为 null */
    val version: PlatformVersion?,
    val classes: PlatformClasses,
    /** 自动检测到的目标版本，来自当前选中的实例 */
    val detectedVersionName: String?,
    val chosenVersionName: String?,
    /** 这次安装解析出来的真实目标 */
    val target: DiscoverTarget,
    /** 目标这一档没有兼容文件时的原因；有兼容文件时为 null */
    val noFileMessage: AndroidStringText?,
    /** 整合包要创建的实例名 */
    val instanceName: String,
    val dependencies: List<DiscoverDependencyRow>,
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
     * 看收藏而不是看搜索结果
     *
     * 与 [onlyInstalled] 互斥：两者都是“不看网上那些”的另一种视角，
     * 同时开着只会让用户不知道自己看的到底是哪辞。
     */
    var favoritesMode by mutableStateOf(false)
        private set

    /** 收藏数据已经装载完；未装载时不列出一个空列表骗用户 */
    var favoritesLoaded by mutableStateOf(false)
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

    /**
     * 依赖项目元数据的会话内缓存
     *
     * 打开确认层、打开详情抽屉、在两个目标版本之间来回切，都会重新碰到同一批依赖项目。
     * 这份缓存按 `平台 + 项目 id` 记住已经读到的项目，第二次直接复用；
     * 失败的读取不写进去，所以"重试"是真的再发一次请求。
     */
    private val projectCache = DiscoverProjectCache()

    /**
     * 项目文件列表的会话内缓存
     *
     * 同一个项目的版本列表此前一次会话里要被取四遍（详情抽屉的文件页、详情抽屉的依赖页、
     * 确认层、抽屉里的"全部下载"），而每一遍都是一次分页往返**加**一次 `initAll`。
     * 命中这里之后全部复用，打开抽屉之后立刻点安装不会再发一次那一整套请求。
     */
    private val filesCache = DiscoverFilesCache<PlatformVersion>()

    /**
     * 正在飞的文件列表读取，键是项目键
     *
     * 详情抽屉会同时要文件列表与依赖列表，而两者要的是**同一份**列表。
     * 只有缓存不够：两个调用几乎同时发起，缓存这时还是空的，两边仍然各发一次请求。
     * 这里让它们等同一个 `Deferred`，因此整个会话里同一个项目只取一次。
     */
    private val filesLock = Mutex()
    private val inFlightFiles = HashMap<String, Deferred<DiscoverFilesResult>>()

    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var sheetJob: Job? = null
    private var projectJob: Job? = null
    private var filesJob: Job? = null
    private var mobileDataContinuation: Continuation<Boolean>? = null

    /** 这一次提交交给任务系统的任务，用来算出提示条该显示什么 */
    private val queueTasks = LinkedHashSet<Task>()
    /** 已经算过一次收尾的任务 id；两条路（监听器与补算）撞上时靠它去重 */
    private val queueEnded = LinkedHashSet<String>()
    private var queueJob: Job? = null
    private var cleared = false

    /** 任务自己写的那句说明（真实的文件名与字节数），提示条直接显示它 */
    var queueMessage by mutableStateOf<AndroidStringText?>(null)
        private set

    /** 上一次搜索用的条件，用来判断这次响应还该不该落地 */
    private var lastQuery: DiscoverQuery? = null

    /** 从实例内容管理器跳进来时等着被自动打开的那个项目键 */
    private var pendingProjectKey: String? = null

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
        if (value) favoritesMode = false
    }

    fun toggleFavoritesMode(value: Boolean) {
        favoritesMode = value
        if (value) {
            onlyInstalled = false
            ensureFavoritesLoaded()
        }
    }

    /**
     * 确保收藏已装载
     *
     * [FavoriteProjectsRepository] 自己会在第一次 `isFavorite` 时异步装载，
     * 这里显式调一次，避免列表先空一帧再自己长出来。
     */
    fun ensureFavoritesLoaded() {
        if (favoritesLoaded) return
        viewModelScope.launch {
            FavoriteProjectsRepository.ensureLoaded()
            favoritesLoaded = FavoriteProjectsRepository.initialized
            // 仓库写入完成后推一次：新增的那些星开始时是按空读的
            favoriteTick++
        }
    }

    /** 切换一条搜索结果的收藏态：落到后端的 MMKV 里，重启仍在 */
    fun toggleFavorite(item: DiscoverItem) {
        FavoriteProjectsRepository.toggle(item.data, item.classes)
        // 仓库里的写入是异步的，推一次让卡片重新回读一下真实态
        favoriteTick++
    }

    /** 收藏切换的计数器；界面用它作为重读的键 */
    var favoriteTick by mutableIntStateOf(0)
        private set

    /** 移出收藏 */
    fun removeFavorite(platform: Platform, projectId: String) {
        FavoriteProjectsRepository.unfavorite(platform, projectId)
        favoriteTick++
    }

    /**
     * 装一个收藏里的项目
     *
     * 收藏只存了缓存的元数据，文件版本仍必须向平台问一次；
     * 这一次复用与搜索结果完全相同的取版本与提交路径。
     */
    fun installFavorite(key: String) {
        if (install.busy()) return
        val entry = FavoriteProjectsRepository.projects.values.firstOrNull {
            discoverProjectKey(it.platform, it.project.projectId) == key
        } ?: return
        if (entry.invalid) return
        if (VersionsManager.currentVersion.value == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            install = DiscoverInstall.Resolving
            val versions = try {
                getVersions(
                    projectID = entry.project.projectId,
                    platform = entry.platform,
                ).initAll(entry.project.projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the files of a favourite project", e)
                install = DiscoverInstall.Failed(mapExceptionToMessage(e))
                return@launch
            }
            if (versions.isEmpty()) {
                install = DiscoverInstall.NoFile
                return@launch
            }
            val version = pickVersionFor(versions, playableTarget()) ?: versions.first()
            val task = submitFile(
                version = version,
                classes = entry.project.classes,
                projectId = entry.project.projectId,
            )
            startQueue(version.platformDisplayName(), listOfNotNull(task))
        }
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

    // ---- 从别处跳进来 -----------------------------------------------------

    /**
     * 打开某个平台项目，或者退成一次普通搜索
     *
     * 两条路的区别只在手上有没有平台身份：
     *  - 有：先切到**内容类别对应的那一栏**，把 [pendingProjectKey] 记下来，然后发一次真实的
     *    搜索；结果里出现那个项目就打开它的详情。直接按 id 打开需要一份 `PlatformSearchData`，
     *    而本地只有 `PlatformProject`——两者是平台层的两个不同形状，硬造一个等于让快照
     *    断言一份真实解析器永远产不出的数据。
     *  - 没有：只有 slug 或标题，于是把它填进搜索框；这是最后一级退路。
     *
     * 找不到就一直找不到：**不会**造一条假记录塞进结果，也不会拿别的项目顶上。
     * 页面本身不知道这条请求的来路，[OxideMainShell] 只负责导航与转发。
     */
    fun openFromRequest(request: OxideDiscoverRequest) {
        request.category?.let { selectCategory(it) }
        val platform = request.platform
        val projectId = request.projectId?.trim()?.takeIf { it.isNotEmpty() }
        pendingProjectKey = if (platform != null && projectId != null) {
            discoverProjectKey(platform, projectId)
        } else {
            null
        }
        query = request.searchTerm?.trim().orEmpty()
        search()
    }

    /**
     * 结果里出现了那个等着打开的项目就打开它，只消费一次
     *
     * 消费点只有一个：[fetch] 每次落地一页之后，因此它与真实分页是同一件事，
     * 不需要额外的一次轮询或延时。翻到尽头还没找到就放弃，并把键清掉，
     * 免得它在后面某一次不相干的搜索里突然自己弹出来。
     */
    private fun openPendingProjectIfReady() {
        val wanted = pendingProjectKey ?: return
        val item = feed.items.firstOrNull { it.key == wanted }
        if (item == null) {
            if (feed.endOfResults) pendingProjectKey = null
            return
        }
        pendingProjectKey = null
        openDetail(item)
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
                    // 从实例内容管理器跳进来时等着打开的那个项目，就在这一页里落地了
                    openPendingProjectIfReady()
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
                is DiscoverFilesResult.Ok -> {
                    // 直接安装的类别也照目标实例挑文件，而不是照搜索栏上的筛选条件
                    val version = pickVersionFor(resolved.versions, playableTarget())
                        ?: resolved.versions.first()
                    val task = submitFile(
                        version = version,
                        classes = item.classes,
                        projectId = item.data.platformId(),
                    )
                    startQueue(version.platformDisplayName(), listOfNotNull(task))
                }
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
        val task = submitFile(version, item.classes, item.data.platformId())
        startQueue(version.platformDisplayName(), listOfNotNull(task))
    }

    /**
     * 读项目的全部文件版本；失败如实上报，绝不退化成"没有文件"
     *
     * 三层复用，从便宜到贵：
     *  1. [filesCache] 里已经成功读到过的列表——直接给；
     *  2. [inFlightFiles] 里已经在飞的同一次读取——等它，不再发第二次请求。
     *     详情抽屉的文件页与依赖页要的是同一份列表，没有这一层它们会各发一次；
     *  3. 才真的去平台取，并且**立刻 `initAll`**（每个文件一次 `initFile`），
     *     所以复用必须是复用 `initAll` 之后的列表——缓存里存的正是它。
     *
     * 失败与空列表都不写缓存：否则"重试"拿到的还是上一次那个结果。
     */
    private suspend fun readAllVersions(item: DiscoverItem): DiscoverFilesResult {
        val key = item.key
        filesCache[key]?.let { cached -> return DiscoverFilesResult.Ok(cached) }

        val pending = filesLock.withLock {
            filesCache[key]?.let { cached -> return DiscoverFilesResult.Ok(cached) }
            inFlightFiles[key] ?: viewModelScope.async { loadAllVersions(item) }
                .also { inFlightFiles[key] = it }
        }
        return try {
            pending.await()
        } finally {
            filesLock.withLock<Unit> {
                if (inFlightFiles[key] === pending) inFlightFiles.remove(key)
            }
        }
    }

    /** 真正去平台取一次文件列表 */
    private suspend fun loadAllVersions(item: DiscoverItem): DiscoverFilesResult {
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
        filesCache[item.key] = versions
        return DiscoverFilesResult.Ok(versions)
    }

    /**
     * 当前选中的可玩实例作为安装目标
     *
     * 直接安装（光影/资源包/存档/地图）、详情抽屉的"全部下载"以及确认层的默认值
     * 都用它，因此"装到哪一档"在这几处永远是同一个答案。
     */
    private fun playableTarget(): DiscoverTarget = discoverResolveTarget(
        selectedInstance = null,
        pickedVersionName = null,
        playableInstance = playableInstanceTarget(),
        installedInstances = installedInstanceTargets(),
    )

    /**
     * 用户在确认层里选中的那一条作为目标
     *
     * 选中的条目本身就是一个已存在的实例时，它连同自己的 Minecraft 版本与加载器一起生效；
     * 否则它只是版本表里的一档 Minecraft 版本（本地还没有实例）。
     */
    private fun pickedTarget(name: String?): DiscoverTarget {
        val instances = installedInstanceTargets()
        return discoverResolveTarget(
            selectedInstance = name?.let { picked -> instances.firstOrNull { it.name == picked } },
            pickedVersionName = name,
            playableInstance = playableInstanceTarget(),
            installedInstances = instances,
        )
    }

    /** 目标这一档下没有兼容文件时的理由；[target] 为空是另一种情况 */
    private fun noFileMessage(target: DiscoverTarget): AndroidStringText =
        androidText(R.string.oxide_dis_sheet_no_file_for_version, target.minecraftVersion.orEmpty())

    // ---- 安装前确认 -------------------------------------------------------

    /**
     * 打开确认层
     *
     * 先解析项目文件与全部依赖，这一步只读不下载；
     * 用户确认之后才开始真正的下载。
     *
     * 目标来自当前选中的实例（一个字节都没选时的默认值），**不是** [pinnedVersion]：
     * pinnedVersion 是那个模组/整合包自己的文件版本，它回答的是"这个文件属于哪一档
     * Minecraft 版本"，拿它当目标就是拿模组的自述当用户的选择。
     *
     * [pinnedVersion] 仍然决定"装哪一个文件"，但要**先过一次目标判定**
     * （[pinnedSource]）：抽屉里的文件页与确认层可能看到的是不同的一档，
     * 那个文件装不进当前实例时，确认层要如实说而不是照样列它的依赖，
     * 否则这一层的 `File:` 行与下面的 `Minecraft version:` 行会自相矛盾。
     * 这一条路径也不再取文件列表——文件已经在手上，没什么可取的。
     */
    fun openSheet(item: DiscoverItem, pinnedVersion: PlatformVersion? = null) {
        if (!discoverUsesInstallSheet(item.classes)) return
        sheetJob?.cancel()
        val target = playableTarget()
        sheet = DiscoverSheet.Resolving(item)
        sheetJob = viewModelScope.launch {
            sheet = if (pinnedVersion != null) {
                buildSheet(item, pinnedSource(pinnedVersion, target), target, target.instanceName)
            } else {
                resolveSheet(item, target, target.instanceName)
            }
        }
    }

    /** 用户在确认层里改了目标版本：重新按那个版本解析文件与依赖 */
    fun selectSheetVersion(name: String?) {
        val current = sheet ?: return
        sheetJob?.cancel()
        val target = pickedTarget(name)
        sheet = DiscoverSheet.Resolving(current.item)
        sheetJob = viewModelScope.launch {
            sheet = resolveSheet(current.item, target, name)
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
        val row = ready.content.dependencies.firstOrNull { it.dependency.key == key } ?: return
        val installTarget = ready.content.target
        sheet = DiscoverSheet.Ready(
            ready.content.copy(
                dependencies = ready.content.dependencies.map {
                    if (it.dependency.key == key) it.withState(DiscoverDependencyState.Resolving) else it
                }
            )
        )
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val resolution = resolveDependency(row.dependency, ready.content.classes, installTarget)
            val outcome = readProjectOutcome(resolution)
            val card = discoverDependencyCard(
                dependency = resolution.dependency,
                outcome = outcome,
                version = resolution.version,
                state = resolution.state,
                targetGameVersion = installTarget.minecraftVersion,
            )
            val current = sheet as? DiscoverSheet.Ready ?: return@launch
            sheet = DiscoverSheet.Ready(
                current.content.copy(
                    dependencies = current.content.dependencies.map {
                        if (it.dependency.key == key) {
                            it.copy(card = card, request = resolution.request)
                        } else {
                            it
                        }
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

    /**
     * 按目标挑项目文件，再解析它的全部依赖
     *
     * [target] 已经是解析好的安装目标（实例 + 它真实的 Minecraft 版本 + 它自己的加载器），
     * 这里只负责按它挑文件。目标这一档没有兼容文件时**仍然把确认层开起来**：
     * 用户要的就是改目标版本，而改版本的控件只在这一层里。
     * 之前这里直接返回 [DiscoverSheet.Failed]，于是"当前实例不支持这个 mod"变成一条死路——
     * 用户既看不到目标版本，也改不了它。
     */
    private suspend fun resolveSheet(
        item: DiscoverItem,
        target: DiscoverTarget,
        pickedName: String?,
    ): DiscoverSheet {
        return when (val files = readAllVersions(item)) {
            is DiscoverFilesResult.Failed -> DiscoverSheet.Failed(item, files.message)
            is DiscoverFilesResult.Empty -> DiscoverSheet.Failed(
                item,
                androidText(R.string.oxide_dis_install_no_file_title),
            )

            is DiscoverFilesResult.Ok -> buildSheet(
                item = item,
                source = dependencySourceOf(files.versions, target),
                target = target,
                pickedName = pickedName,
            )
        }
    }

    /**
     * 依赖的唯一来源：挑目标这一档的文件，再读那个文件的依赖
     *
     * 详情抽屉的依赖标签页、这一层确认层、以及"全部下载"全都问它，
     * 因此"标签页说有依赖、点安装说没有"这件事在结构上就不可能再发生。
     */
    private fun dependencySourceOf(
        versions: List<PlatformVersion>,
        target: DiscoverTarget,
    ): DiscoverDependencySource<PlatformVersion> = discoverDependencySource(
        versions = versions,
        target = target,
        factsOf = ::fileFactsOf,
        dependenciesOf = ::dependenciesOf,
    )

    /**
     * 抽屉里点某个具体文件安装时的那一次来源判定
     *
     * 装得进目标就是那个文件与它的依赖；装不进就**不是**"这一档的文件"：
     * 确认层仍然开着（用户要的就是改目标版本），但不能确认，
     * 而且依赖那一块要说"目标这一档没有文件"而不是"这个文件没有依赖"。
     */
    private fun pinnedSource(
        pinned: PlatformVersion,
        target: DiscoverTarget,
    ): DiscoverDependencySource<PlatformVersion> =
        if (discoverFileFitsTarget(pinned, target, ::fileFactsOf)) {
            DiscoverDependencySource(
                version = pinned,
                dependencies = discoverDependenciesCapped(pinned),
                fallbackVersion = null,
                noFileForTarget = false,
                noFiles = false,
            )
        } else {
            Logger.info(
                TAG,
                "The pinned file does not fit ${target.minecraftVersion ?: target.instanceName}; " +
                        "keeping the install sheet open so the target can be changed."
            )
            DiscoverDependencySource(
                version = null,
                dependencies = emptyList(),
                fallbackVersion = pinned,
                noFileForTarget = true,
                noFiles = false,
            )
        }

    private suspend fun buildSheet(
        item: DiscoverItem,
        source: DiscoverDependencySource<PlatformVersion>,
        target: DiscoverTarget,
        pickedName: String?,
    ): DiscoverSheet {
        val classes = item.classes
        val version = source.version
        // 目标这一档没有兼容文件时没有依赖可列：依赖是那个文件自己标注的。
        // 这里不说成"没有依赖"——确认层照着 [DiscoverInstallSheet.noFileMessage] 说实话。
        val dependencies = source.dependencies
        val defaultSelected = discoverDefaultSelection(dependencies)

        val rows = buildDependencyRows(
            dependencies = dependencies,
            classes = classes,
            target = target,
            defaultSelected = defaultSelected,
        )

        val instanceName = if (classes == PlatformClasses.MOD_PACK) uniqueInstanceName(item.title) else ""
        instanceNameProblem = if (classes == PlatformClasses.MOD_PACK) checkInstanceName(instanceName) else null

        val noFileMessage = if (source.noFileForTarget) noFileMessage(target) else null

        return DiscoverSheet.Ready(
            DiscoverInstallSheet(
                item = item,
                version = version,
                classes = classes,
                detectedVersionName = playableInstanceTarget()?.name,
                chosenVersionName = pickedName ?: target.instanceName,
                target = target,
                noFileMessage = noFileMessage,
                instanceName = instanceName,
                dependencies = rows,
            )
        )
    }

    /**
     * 解析一批依赖，产出可以直接绘制的行
     *
     * 两步走，因为项目 id 有时只有在版本解析之后才拿得到：
     *  1. 逐条解析出与目标 Minecraft 版本匹配的文件版本，项目 id 从这个版本上读；
     *  2. 把这些项目 id 去重、截断之后**批量**读项目元数据（名字、图标、作者）。
     *
     * 两步都是**并发**的。此前两步各自是串行的 `map`，于是 N 条依赖要 2N 次顺序往返
     * 才画得出第一行——依赖卡里每一条的名字都要等前一条回来，这就是"点安装之后
     * 稍微停一下才出现"的成因。并发度按 [DEPENDENCY_READ_PARALLELISM] 收着，
     * 与依赖下载链路自己的 [DEPENDENCY_PARALLELISM] 同量级，不会把平台打爆。
     *
     * 整段在 [Dispatchers.IO] 上跑：这个函数由确认层与详情抽屉的 `viewModelScope` 调用，
     * 组合与测量因此不会被网络往返卡住。
     */
    private suspend fun buildDependencyRows(
        dependencies: List<DiscoverDependency>,
        classes: PlatformClasses,
        target: DiscoverTarget,
        defaultSelected: Set<String>,
    ): List<DiscoverDependencyRow> = withContext(Dispatchers.IO) {
        coroutineScope {
            val resolutions = dependencies
                .map { dependency -> async { resolveDependency(dependency, classes, target) } }
                .awaitAll()

            // 同一个项目可能出现在多条关系里，按 (平台, 项目 id) 去重后再按上限截断
            val targets = discoverProjectTargets(resolutions.mapNotNull { it.target })
            val resolvedKeys = discoverProjectKeys(targets)
            val reads = Semaphore(DEPENDENCY_READ_PARALLELISM)
            val projects = targets
                .map { projectTarget ->
                    async {
                        projectTarget.key to reads.withPermit { readDependencyProject(projectTarget) }
                    }
                }
                .awaitAll()
                .toMap()

            resolutions.map { resolution ->
                val dependencyTarget = resolution.target
                val outcome = when {
                    dependencyTarget == null -> DiscoverProjectOutcome.Unknown
                    dependencyTarget.key !in resolvedKeys -> DiscoverProjectOutcome.Skipped
                    else -> projects.getValue(dependencyTarget.key)
                }
                DiscoverDependencyRow(
                    dependency = resolution.dependency,
                    card = discoverDependencyCard(
                        dependency = resolution.dependency,
                        outcome = outcome,
                        version = resolution.version,
                        state = resolution.state,
                        targetGameVersion = target.minecraftVersion,
                    ),
                    selected = resolution.dependency.key in defaultSelected,
                    request = resolution.request,
                )
            }
        }
    }

    /**
     * 读一个依赖项目的元数据
     *
     * 命中会话缓存就不再发请求；成功的读取写回缓存，失败的**不写**，
     * 所以"重试"是真的重新查一次，而不是把上一次的错再拿出来。
     */
    private suspend fun readDependencyProject(target: DiscoverDependencyTarget): DiscoverProjectOutcome {
        return try {
            DiscoverProjectOutcome.Resolved(
                readProject(target.projectId, target.platform).toDiscoverDependencyProject()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to read the dependency project ${target.key}", e)
            DiscoverProjectOutcome.Failed(mapExceptionToMessage(e))
        }
    }

    /**
     * 单条重试时补读它的项目元数据
     *
     * 走的是与批量解析完全相同的读取与缓存，因此一次点击不会把别的项目重读一遍。
     * 版本解析本身已经失败的那一条不再叠一层项目读取的错——它已经有更具体的原因了。
     */
    private suspend fun readProjectOutcome(
        resolution: DiscoverDependencyResolution,
    ): DiscoverProjectOutcome = when {
        resolution.state is DiscoverDependencyState.Failed ->
            DiscoverProjectOutcome.Failed(
                (resolution.state as DiscoverDependencyState.Failed).message
            )

        else -> resolution.target?.let { readDependencyProject(it) }
            ?: DiscoverProjectOutcome.Unknown
    }

    /**
     * 解析一个依赖在目标下的具体文件
     *
     * 只给了精确版本 id（Modrinth）就直接取那个版本；只给了项目 id（CurseForge）
     * 就拉全部版本再挑一个与目标匹配的——与依赖安装链路自己做的事一致，
     * 因此界面上显示的版本就是会被装上的那个。
     *
     * 匹配用的是 [target] 自己的 Minecraft 版本与加载器，而不是搜索栏上的筛选条件：
     * 两者可以合法地不同（搜索回答的是"我想看什么"，目标回答的是"装得进哪个目录"），
     * 但只有后者决定依赖装不装得上。
     *
     * 任何异常都在这里收成 [DiscoverDependencyState.Failed]：读不出来的依赖
     * 只坏它自己的一条，其余依赖照旧可用。
     */
    private suspend fun resolveDependency(
        dependency: DiscoverDependency,
        classes: PlatformClasses,
        target: DiscoverTarget,
    ): DiscoverDependencyResolution = try {
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
                    DiscoverDependencyResolution(
                        dependency, null, null, DiscoverDependencyState.NoCompatibleVersion, null,
                    )
                } else {
                    // CurseForge 的 platformGameVersion/platformLoaders 走的是 initFile 之后才有的主文件，
                    // 不先初始化就去读会抛 UninitializedPropertyAccessException。依赖安装链路也是这么做的。
                    if (!version.initFile(projectId)) {
                        DiscoverDependencyResolution(
                            dependency, null, projectId, DiscoverDependencyState.NoCompatibleVersion, null,
                        )
                    } else {
                        DiscoverDependencyResolution(
                            dependency = dependency,
                            version = version,
                            projectId = projectId,
                            state = resolvedState(version),
                            request = DependencyRequest(
                                platform = dependency.platform,
                                projectId = projectId,
                                // Modrinth 认这个精确 id
                                versionId = version.platformId(),
                                classes = classes,
                                projectTitle = version.platformDisplayName(),
                            ),
                        )
                    }
                }
            }

            dependency.projectId != null -> {
                val all = getVersions(
                    projectID = dependency.projectId,
                    platform = dependency.platform,
                ).initAll(dependency.projectId)
                val picked = pickVersionFor(all, target)
                if (picked == null) {
                    DiscoverDependencyResolution(
                        dependency, null, dependency.projectId, DiscoverDependencyState.NoCompatibleVersion, null,
                    )
                } else {
                    DiscoverDependencyResolution(
                        dependency = dependency,
                        version = picked,
                        projectId = dependency.projectId,
                        state = resolvedState(picked),
                        request = DependencyRequest(
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
                        ),
                    )
                }
            }

            // 两个 id 都没有的依赖只能展示，装不了
            else -> DiscoverDependencyResolution(
                dependency, null, null, DiscoverDependencyState.NoCompatibleVersion, null,
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warning(TAG, "Failed to resolve the dependency ${dependency.key}", e)
        DiscoverDependencyResolution(
            dependency = dependency,
            version = null,
            projectId = dependency.projectId?.takeIf { it.isNotBlank() },
            state = DiscoverDependencyState.Failed(mapExceptionToMessage(e)),
            request = null,
        )
    }

    /** 已解析出的依赖版本在界面上的样子：版本号 + 它支持的 Minecraft 版本 */
    private fun resolvedState(version: PlatformVersion) = DiscoverDependencyState.Resolved(
        versionLabel = discoverVersionLabel(version).orEmpty(),
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

        // 目标这一档没有兼容文件：确认层不能关，用户得能改目标版本
        val file = ready.version
        if (file == null) {
            install = DiscoverInstall.Failed(ready.noFileMessage ?: noFileMessage(ready.target))
            return
        }

        if (ready.classes == PlatformClasses.MOD_PACK) {
            startModpackInstall(context, ready, file)
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
                installGameThenAssets(context, targetName, ready, file)
            } else {
                install = DiscoverInstall.NeedsInstance
            }
            return
        }
        submitAssets(ready, file, existing, plan)
    }

    /** 连游戏版本一起装：游戏任务结束后刷新版本列表，再装资源 */
    private fun installGameThenAssets(
        context: Context,
        versionName: String,
        ready: DiscoverInstallSheet,
        file: PlatformVersion,
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
            submitAssets(ready, file, installed, ready.plan())
        }
    }

    /** 项目文件 + 勾选的依赖，一起提交 */
    private fun submitAssets(
        sheetContent: DiscoverInstallSheet,
        file: PlatformVersion,
        target: Version,
        plan: DiscoverInstallPlan,
    ) {
        sheet = null
        val submitted = ArrayList<Task>(1 + plan.dependencyKeys.size)

        submitFile(file, sheetContent.classes, sheetContent.item.data.platformId(), target)
            ?.let { submitted += it }

        sheetContent.dependencies
            .filter { it.dependency.key in plan.dependencyKeys && it.request != null }
            .forEach { row ->
                row.request?.let { request ->
                    submitDependency(request, target, row.dependency.key)?.let { submitted += it }
                }
            }

        startQueue(file.platformDisplayName(), submitted)
    }

    /**
     * 详情抽屉里的"全部下载"：目标取当前实例，依赖逐条提交
     *
     * 依赖列表与这里要装的文件来自**同一次** [dependencySourceOf] 判定，
     * 因此抽屉里勾了什么、"全部下载"就装什么，两者不会再各答各的。
     */
    fun downloadAllFromDetail() {
        val detail = detail ?: return
        val rows = (detail.dependencies as? DiscoverDependenciesState.Loaded)?.rows ?: return
        val target = VersionsManager.currentVersion.value ?: run {
            install = DiscoverInstall.NeedsInstance
            return
        }
        val installTarget = playableTarget()
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
                    val source = dependencySourceOf(files.versions, installTarget)
                    val version = source.version
                    if (version == null) {
                        // 与确认层同一句理由：目标这一档没有兼容文件，而不是"没有依赖"
                        install = DiscoverInstall.Failed(
                            if (source.noFileForTarget) noFileMessage(installTarget) else androidText(
                                R.string.oxide_dis_install_no_file_title
                            )
                        )
                        return@launch
                    }
                    val submitted = ArrayList<Task>(1 + rows.size)
                    submitFile(version, detail.item.classes, detail.item.data.platformId(), target)
                        ?.let { submitted += it }
                    rows.filter { it.selected && it.request != null }.forEach { row ->
                        row.request?.let { request ->
                            submitDependency(request, target, row.dependency.key)?.let { submitted += it }
                        }
                    }
                    startQueue(version.platformDisplayName(), submitted)
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
    private fun submitDependency(request: DependencyRequest, target: Version, key: String): Task? =
        downloadDependenciesForVersions(
            requests = listOf(request),
            versions = listOf(target),
            submitError = { error ->
                installError = error
                markDependencyFailed(key, error.message)
            },
        )

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

    /** 交给单文件安装器；返回交给任务系统的那个任务，好让提示条能看着它 */
    private fun submitFile(
        version: PlatformVersion,
        classes: PlatformClasses,
        projectId: String,
        target: Version? = VersionsManager.currentVersion.value,
    ): Task? {
        if (target == null) {
            install = DiscoverInstall.NeedsInstance
            return null
        }
        val task = downloadSingleForVersions(
            version = version,
            versions = listOf(target),
            folder = classes.versionFolder.folderName,
            submitError = { installError = it },
            onEnded = { taskId, failed -> onQueueTaskEnded(taskId, failed) },
        )
        Logger.info(TAG, "Queued ${version.platformDisplayName()} of $projectId for ${target.getVersionName()}")
        return task
    }

    // ---- 下载提示条 -------------------------------------------------------

    /**
     * 开始跟踪这一次提交，并把提示条挂上
     *
     * [tasks] 是这一次真正交给任务系统的任务：主文件一个，勾上的依赖每个一个。
     * 一个都没有时提示条不出现——那说明根本没开始下载（例如目标实例已经不存在），
     * 挂一条永远不收的"Queued for download"才是原来那个缺陷。
     */
    private fun startQueue(fileName: String, tasks: List<Task>) {
        stopQueue()
        if (tasks.isEmpty()) {
            install = DiscoverInstall.Idle
            return
        }
        val row = discoverQueueRow(
            previous = null,
            fileName = fileName,
            tasks = tasks.size,
            stage = DiscoverQueueStage.Queued,
        ) ?: return
        install = DiscoverInstall.Queued(row)
        tasks.forEach { queueTasks += it }
        queueMessage = null

        // 提示条只跟着**主文件**那个任务的比例走：那是用户点的那个文件。
        // 依赖是附带的，它们的进度混进来会让那根细线说谎。
        val primary = tasks.first()
        queueJob = viewModelScope.launch {
            combine(primary.stage, primary.progress, primary.message) { stage, progress, message ->
                Triple(stage, progress, message)
            }.collect { (stage, progress, message) ->
                queueMessage = message
                onQueueProgress(discoverQueueStageOf(stage, progress), progress)
            }
        }

        // 任务系统收尾时会把监听器一起摘掉，所以在它跑完**之前**挂上的监听器才收得到。
        // 万一提交与这一次挂载之间任务就已经结束（很小的窗口），按任务自己报的阶段补一次，
        // 提示条才不会永远挂着——"任务结束"这一条信息本身就是可靠的。
        tasks.forEach { task ->
            if (discoverTaskEnded(task)) onQueueTaskEnded(task.id, task.stage.value != TaskStage.COMPLETED)
        }
    }

    /**
     * 这个任务是不是已经不在跑了
     *
     * 阶段是 COMPLETED 一定意味着它跑完了；仍在任务列表里就说明还在跑。
     * 两者都不是（阶段不是 COMPLETED，且已经不在列表里）就是已经收尾了——
     * 失败与取消都停在这个状态，因为 `TaskSystem` 只在没抛错时才把阶段置成 COMPLETED。
     */
    private fun discoverTaskEnded(task: Task): Boolean =
        task.stage.value == TaskStage.COMPLETED || !TaskSystem.containsTask(task.id)

    /** 收到主文件任务的真实进度 */
    private fun onQueueProgress(stage: DiscoverQueueStage, progress: Float) {
        // Complete/Failed 不在这里算：那条路带着真实的成败信息，而且已经在
        // [onQueueTaskEnded] 里计过一次。同一个任务在这里再减一次就少算一个。
        if (stage == DiscoverQueueStage.Complete || stage == DiscoverQueueStage.Failed) return
        val queued = install as? DiscoverInstall.Queued ?: return
        val next = discoverQueueRow(
            previous = queued.queue,
            fileName = queued.queue.fileName,
            stage = stage,
            progress = progress,
        ) ?: return
        // 进度没变就不重发一次状态：Compose 状态每次写都会让读它的界面重组
        if (next == queued.queue) return
        install = DiscoverInstall.Queued(next)
    }

    /**
     * 某一个任务收尾了（成功、失败、取消都会走到）
     *
     * [failed] 是任务自己报的：`TaskSystem` 只在跑完没抛错时把阶段置成
     * [TaskStage.COMPLETED]，其余情况阶段停在 RUNNING/PREPARING，所以阶段本身就是
     * 真实的成败信号，不需要任务系统额外开口子。
     *
     * 收尾只是把计数减一；真正收掉提示条的是最后一个任务走完之后。
     * 一条依赖失败因此不会把其余仍在下载的东西一起判死。
     *
     * 同一个任务只算一次：[startQueue] 会为"提交与挂载之间就已经结束"的那几个补一次，
     * 而它们的监听器也可能刚好在这中间触发。这里按任务 id 去重，两条路撞上也不会多减。
     */
    private fun onQueueTaskEnded(taskId: String, failed: Boolean) {
        // 回调发生在任务系统自己的线程上，状态必须回到主线程写
        viewModelScope.launch {
            if (cleared) return@launch
            if (taskId !in queueTasks) return@launch
            if (!queueEnded.add(taskId)) return@launch
            val queued = install as? DiscoverInstall.Queued ?: return@launch
            val next = discoverQueueRow(
                previous = queued.queue,
                fileName = queued.queue.fileName,
                stage = if (failed) DiscoverQueueStage.Failed else DiscoverQueueStage.Complete,
                failed = failed,
            )
            // null = 全部收尾：提示条该带着出场动画离场了
            install = next?.let { DiscoverInstall.Queued(it) } ?: DiscoverInstall.Idle
            if (next == null) stopQueue()
        }
    }

    /** 收掉进度订阅与还没结束的任务引用；手点叉号与状态层收尾都走它 */
    private fun stopQueue() {
        queueJob?.cancel()
        queueJob = null
        queueMessage = null
        queueTasks.forEach { TaskSystem.removeTaskEndedListener(it.id) }
        queueTasks.clear()
        queueEnded.clear()
    }

    // ---- 整合包安装 -------------------------------------------------------

    /** 确认层里确认整合包：交给安装器装成一个新实例 */
    private fun startModpackInstall(
        context: Context,
        sheetContent: DiscoverInstallSheet,
        file: PlatformVersion,
    ) {
        val draft = sheetContent
        sheet = null
        instanceNameProblem = null

        val created = ModPackInstaller(
            context = context,
            version = file,
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

    /**
     * 打开项目详情
     *
     * 项目元数据与文件列表各发一次请求，而**依赖列表不再单独去取**：
     * 依赖是某一个文件版本自己标注的，因此它必须等那份列表回来——此前它是自己去取了一遍，
     * 于是打开一个抽屉要发两套完全相同的分页 + `initAll` 请求。
     */
    fun openDetail(item: DiscoverItem) {
        projectJob?.cancel()
        filesJob?.cancel()
        detail = DiscoverDetail(
            item = item,
            project = DiscoverProjectState.Loading,
            files = DiscoverFilesState.Loading,
            dependencies = DiscoverDependenciesState.Loading
        )
        loadProject(item)
        loadFilesAndDependencies(item)
    }

    fun closeDetail() {
        projectJob?.cancel()
        filesJob?.cancel()
        detail = null
    }

    private fun loadProject(item: DiscoverItem) {
        val projectId = item.data.platformId()
        val target = item.data.platform()
        projectJob = viewModelScope.launch {
            val state = try {
                DiscoverProjectState.Loaded(readProject(projectId, target))
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

    /**
     * 读一次项目元数据，命中会话缓存就不再发请求
     *
     * 与依赖解析读项目走的是同一个缓存：同一个项目在一次会话里经常既出现在
     * 详情抽屉的头部、又出现在某条依赖的卡上，此前那是两次请求。
     *
     * 成功的结果写回缓存（它就是 [readDependencyProject] 用的那份），
     * 失败的不写——否则点"重试"拿到的还是上一次那个错。
     */
    private suspend fun readProject(projectId: String, platform: Platform): PlatformProject {
        val key = discoverProjectKey(platform, projectId)
        projectCache[key]?.let { return it }
        val project = getProjectByVersion(projectId = projectId, platform = platform)
        projectCache[key] = project
        return project
    }

    /**
     * 文件列表与依赖列表只取一次
     *
     * 文件列表一回来就先把文件那一页填上（它不再等依赖解析），依赖列表随后用**同一份**
     * 列表判定——既省掉一次整套请求，也保证抽屉里列出来的依赖与"全部下载"会装的完全一致。
     */
    private fun loadFilesAndDependencies(item: DiscoverItem) {
        filesJob = viewModelScope.launch {
            val state = try {
                readAllVersions(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the files of ${item.key}", e)
                DiscoverFilesResult.Failed(mapExceptionToMessage(e))
            }
            if (detail?.item?.key != item.key) return@launch
            detail = detail?.copy(files = state.toFilesState())

            val classes = item.classes
            val dependencies = try {
                when (state) {
                    is DiscoverFilesResult.Failed -> DiscoverDependenciesState.Failed(state.message)
                    is DiscoverFilesResult.Empty -> DiscoverDependenciesState.Empty
                    is DiscoverFilesResult.Ok -> {
                        // 抽屉列出的依赖就是"全部下载"会装的那一份，
                        // 所以这里按同一个目标（当前选中的实例）判定，而不是按搜索筛选条件
                        val target = playableTarget()
                        val source = dependencySourceOf(state.versions, target)
                        when {
                            source.noFiles -> DiscoverDependenciesState.Empty
                            // 目标这一档没有兼容文件：说这一句，而不是"这个文件没有依赖"
                            source.noFileForTarget -> DiscoverDependenciesState.NoFileForTarget(
                                noFileMessage(target)
                            )

                            source.genuinelyEmpty -> DiscoverDependenciesState.Empty
                            else -> DiscoverDependenciesState.Loaded(
                                buildDependencyRows(
                                    dependencies = source.dependencies,
                                    classes = classes,
                                    target = target,
                                    defaultSelected = discoverDefaultSelection(source.dependencies),
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load the dependencies of ${item.key}", e)
                DiscoverDependenciesState.Failed(mapExceptionToMessage(e))
            }
            if (detail?.item?.key != item.key) return@launch
            detail = detail?.copy(dependencies = dependencies)
        }
    }

    /** 文件列表的读取结果 → 抽屉里文件那一页的状态 */
    private fun DiscoverFilesResult.toFilesState(): DiscoverFilesState = when (this) {
        is DiscoverFilesResult.Ok -> DiscoverFilesState.Loaded(versions)
        is DiscoverFilesResult.Empty -> DiscoverFilesState.Empty
        is DiscoverFilesResult.Failed -> DiscoverFilesState.Failed(message)
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

    /**
     * 手点叉号：把提示条连同它的进度订阅一起收掉
     *
     * 幂等：收完之后任务结束回调仍在路上，而 [onQueueTaskEnded] 先看状态是不是 Queued，
     * 不是就直接返回，因此再点一次叉号、或者下载随后结束，都不会把提示条变回来。
     * 下载本身不受影响——叉号收的只是这一屏。
     */
    fun dismissInstallState() {
        stopQueue()
        install = DiscoverInstall.Idle
    }

    fun dismissInstallError() {
        installError = null
    }

    override fun onCleared() {
        cleared = true
        stopQueue()
        searchJob?.cancel()
        resolveJob?.cancel()
        sheetJob?.cancel()
        projectJob?.cancel()
        filesJob?.cancel()
        // 收尾不是挂起函数，因此这里不走 withLock：ViewModel 已经被清掉，
        // 没有任何协程还会再来拿这张表，直接清空即可
        inFlightFiles.clear()
        installer?.cancelInstall()
        mobileDataContinuation = null
    }
}

/** 勾选或取消这一个依赖，其他行原样不动 */
private fun DiscoverDependencyRow.withToggled(checked: Boolean): DiscoverDependencyRow {
    val selected = discoverToggleSelection(setOf(dependency.key), dependency, checked)
    return copy(selected = dependency.key in selected)
}

/** 换掉这一条的读取状态，卡片上的其余字段原样保留 */
private fun DiscoverDependencyRow.withState(state: DiscoverDependencyState): DiscoverDependencyRow =
    copy(card = card.copy(state = state))

/** 确认层里最终要装到哪个 Minecraft 版本 */
private fun DiscoverInstallSheet.targetVersionName(): String? =
    discoverResolveVersion(chosenVersionName, detectedVersionName).name

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

/**
 * 确认层里依赖那一块该显示什么
 *
 * [DiscoverInstallSheet.noFileMessage] 非空就是"目标这一档没有兼容文件"，
 * 此时依赖列表必须说同一件事，而不是退成一句 "This file has no dependencies."——
 * 依赖是那个文件自己标注的，没有文件就没有依赖可列，而这两者的区别正是本轮要修的东西。
 */
private fun DiscoverInstallSheet.dependencyState(): DiscoverDependenciesState = when {
    dependencies.isNotEmpty() -> DiscoverDependenciesState.Loaded(dependencies)
    noFileMessage != null -> DiscoverDependenciesState.NoFileForTarget(noFileMessage)
    else -> DiscoverDependenciesState.Empty
}

/** 安装进行中时不允许重复触发 */
private fun DiscoverInstall.busy(): Boolean = when (this) {
    // 只有"还在向平台查询"才是忙。已经交给任务系统的下载不再挡着下一次安装：
    // 之前 Queued 永远不收，于是下载早就结束了按钮却还是灰的。
    is DiscoverInstall.Resolving -> true
    else -> false
}

/** 只有 CurseForge 提供存档 */
private fun PlatformClasses.supportsModrinth(): Boolean = this != PlatformClasses.SAVES

/**
 * 按**目标**的 Minecraft 版本与加载器挑一个文件版本
 *
 * 两条都来自目标实例（[DiscoverTarget]），不是搜索栏上的筛选条件：
 * 搜索筛选回答的是"我想看哪些项目"，目标实例回答的是"装得进哪个目录"，
 * 用前者去筛后者挑出来的文件，到下载那一步会被判成不兼容。
 *
 * 规则本身在 [discoverCompatibleFile] 里，因此可以直接被单元测试钉死。
 */
private fun pickVersionFor(
    versions: List<PlatformVersion>,
    target: DiscoverTarget,
): PlatformVersion? = discoverCompatibleFile(
    versions = versions,
    minecraftVersion = target.minecraftVersion,
    targetLoaders = target.loaders,
    factsOf = ::fileFactsOf,
)

/** 一个文件版本自己声明的 Minecraft 版本与加载器 */
private fun fileFactsOf(version: PlatformVersion): DiscoverFileFacts = DiscoverFileFacts(
    gameVersions = version.platformGameVersion().toList(),
    loaders = version.platformLoaders().map { it.getDisplayName() },
)

/** 一个文件版本自己标注的依赖，去重并截断 */
private fun dependenciesOf(version: PlatformVersion): List<DiscoverDependency> =
    discoverDependenciesCapped(version)

/**
 * 实例名 → 它真实的 Minecraft 版本名
 *
 * **只用版本 JSON 里解析出来的那个值。** 实例名是版本文件夹的名字，自定义名、整合包名、
 * 加载器 id 都可能是它；把它塞进 Minecraft 版本的位置，平台上必然一个文件都匹配不上，
 * 于是用户看到"这个项目不支持 <实例名>"这种自己造出来的理由。
 * 解析不出来时是 null（"还不知道"），调用方据此不按 Minecraft 版本过滤，
 * 而不是拿实例名顶上。
 */
private fun minecraftVersionOf(version: Version): String? =
    version.getVersionInfo()?.minecraftVersion?.takeIf { it.isNotBlank() }

/** 一个已存在的实例作为安装目标 */
private fun Version.asDiscoverTarget(): DiscoverInstanceTarget = DiscoverInstanceTarget(
    name = getVersionName(),
    minecraftVersion = minecraftVersionOf(this),
    loaders = getVersionInfo()?.loaderInfos.orEmpty()
        .mapTo(LinkedHashSet()) { it.loader.displayName }
        .filterTo(LinkedHashSet()) { it.isNotBlank() },
)

/** 本地已装的实例，作为目标候选 */
private fun installedInstanceTargets(): List<DiscoverInstanceTarget> =
    VersionsManager.versions.value.map { it.asDiscoverTarget() }

/** 当前选中的可玩实例作为目标；一个都没装时是 null */
private fun playableInstanceTarget(): DiscoverInstanceTarget? =
    VersionsManager.currentVersion.value?.asDiscoverTarget()

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
    /**
     * 外壳转过来的"打开某个项目"请求
     *
     * 页面自己不知道谁在导航它，因此这一条只描述意图：切到内容类别对应的那一栏，
     * 然后打开那个项目，或者退成一次普通搜索。消费掉之后回调一次，宿主把请求清掉，
     * 于是下一次换请求不会被上一次的重放顶掉。
     */
    hostRequest: OxideDiscoverRequest? = null,
    onHostRequestConsumed: () -> Unit = {},
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

    // 收藏只有在进入发现页时装载一次：否则第一次点星号时还会被当成未收藏
    LaunchedEffect(Unit) {
        viewModel.ensureFavoritesLoaded()
    }

    // 外壳转过来的打开请求：消费一次就清掉，重组不会重复触发
    LaunchedEffect(hostRequest) {
        hostRequest?.let {
            viewModel.openFromRequest(it)
            onHostRequestConsumed()
        }
    }

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

    // 收藏只存了缓存的元数据，不是搜索结果；因此它自己一份列表，不跟着网上的分页走。
    //
    // 这里用 remember 而不是 derivedStateOf，是有意的：仓库里的读取发生在 remember 块里，
    // 组合不会观察到它们。因此每一次收藏变动（装载完、切换、移出）
    // 都由状态层推一次 favoriteTick，这里就会重算一次。
    val favoriteRows = remember(viewModel.favoritesMode, viewModel.favoriteTick) {
        if (!viewModel.favoritesMode) {
            emptyList()
        } else {
            oxideFavoriteRows(
                entries = FavoriteProjectsRepository.projects.values,
                keyOf = { platform, projectId -> discoverProjectKey(platform, projectId) },
                platformNameOf = { platform -> platform.displayName },
            )
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
                    favoritesMode = viewModel.favoritesMode,
                    onSelect = viewModel::selectCategory,
                    onSelectInstalled = { viewModel.toggleOnlyInstalled(true) },
                    onSelectFavorites = { viewModel.toggleFavoritesMode(true) },
                    onGoInstances = { onNavigate(OxidePage.Instances) }
                )
            }

            Spacer(Modifier.width(metrics.cardGap))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // 收藏不走网上搜索，因此这一屏的筛选条整体不出现：
                // 留着它只会让用户误以为收藏可以按平台筛选
                if (viewModel.favoritesMode) {
                    OxideFavoritesPanel(
                        metrics = metrics,
                        rows = favoriteRows,
                        loading = !viewModel.favoritesLoaded,
                        busy = viewModel.install.busy(),
                        onInstall = { row -> viewModel.installFavorite(row.key) },
                        onRemove = { row ->
                            viewModel.removeFavorite(row.platform, row.projectId)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@Column
                }

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
                            onInstall = viewModel::install,
                            favoritesTick = viewModel.favoriteTick,
                            onToggleFavorite = viewModel::toggleFavorite,
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
        notice = discoverNoticeRow(
            install = viewModel.install,
            error = viewModel.installError,
            taskMessage = viewModel.queueMessage,
        ),
        action = if (viewModel.install is DiscoverInstall.NeedsInstance) {
            {
                OxideButton(
                    text = stringResource(R.string.oxide_dis_install_go_instances),
                    onClick = { onNavigate(OxidePage.Instances) },
                    tone = OxideButtonTone.Primary
                )
            }
        } else {
            null
        },
        onDismiss = {
            if (viewModel.installError != null) {
                viewModel.dismissInstallError()
            } else {
                viewModel.dismissInstallState()
            }
        },
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
    favoritesMode: Boolean,
    onSelect: (DiscoverCategory) -> Unit,
    onSelectInstalled: () -> Unit,
    onSelectFavorites: () -> Unit,
    onGoInstances: () -> Unit,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    OxideSurface(modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DiscoverCategory.entries.forEach { category ->
                DiscoverCategoryTab(
                    text = stringResource(category.labelRes),
                    selected = category == selected && !onlyInstalled && !favoritesMode,
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

            Spacer(Modifier.height(metrics.cardGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.controlPadding)
                    .height(1.dp)
                    .background(Oxide.Line)
            )
            Spacer(Modifier.height(metrics.cardGap))

            // 收藏与「已安装」一样只看本地，因此同样独占结果区：
            // 两者都不与任何类别同时高亮，用户不会同时看着两份不同的列表
            DiscoverCategoryTab(
                text = stringResource(R.string.oxide_cap_dis_favorites),
                selected = favoritesMode,
                metrics = metrics,
                onClick = onSelectFavorites
            )

            Spacer(Modifier.height(metrics.cardGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.controlPadding)
                    .height(1.dp)
                    .background(Oxide.Line)
            )
            Spacer(Modifier.height(metrics.cardGap))

            // 从这台设备导入一个整合包。启动器能导出四种格式，
            // 而旧界面里的整合包导入入口在 Oxide 下已经不可达：
            // 导出在实例抽屉里，导入不在任何地方。
            OxideModpackImport(
                metrics = metrics,
                onFinished = { onGoInstances() },
                modifier = Modifier.padding(horizontal = metrics.controlPadding),
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
internal fun DiscoverResultsHeader(
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
internal fun DiscoverResultsGrid(
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
    favoritesTick: Int,
    onToggleFavorite: (DiscoverItem) -> Unit,
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
            // 收藏仓库的装载状态读在 remember 之外：它是 mutableState，读取即订阅，
            // 于是装载完成本身就能把卡片刷成真实收藏态，不必等下一次翻页或切换。
            val favoritesLoaded = FavoriteProjectsRepository.initialized
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
                            // 收藏态读一次快照：切换会推一次 favoritesTick，
                            // 下一次刷新回读真实值，因此不会回读到一个被缓存的旧值
                            favorite = remember(item.key, favoritesTick, favoritesLoaded) {
                                // 未装载时不问仓库：isFavorite 会顺带触发一次装载，而装载
                                // 要读 MMKV —— 在一张截图里既没有意义也没有 MMKV 可读。
                                // 未装载本来就没有收藏可言，false 就是答案。
                                favoritesLoaded && FavoriteProjectsRepository.isFavorite(
                                    platform = item.data.platform(),
                                    projectId = item.data.platformId(),
                                )
                            },
                            onOpen = { onOpen(item) },
                            onInstall = { onInstall(item) },
                            onToggleFavorite = { onToggleFavorite(item) },
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
    favorite: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onToggleFavorite: () -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 收藏是这一牌上唯一与安装无关的动作：它只改 MMKV 里的一条记录，
            // 不下载任何东西，因此它不受安装忙影响；且它必须带一句读屏文字，
            // 不能只靠一个星号表达
            OxideIconButton(
                onClick = onToggleFavorite,
                glyph = if (favorite) "\u2605" else "\u2606",
                modifier = Modifier.oxideIconDescription(
                    stringResource(
                        if (favorite) {
                            R.string.oxide_cap_dis_fav_remove
                        } else {
                            R.string.oxide_cap_dis_fav_add
                        }
                    )
                ),
            )
            Spacer(Modifier.width(metrics.cardGap * 0.6f))
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
internal fun DiscoverDependencySection(
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

            // 目标这一档没有兼容文件：标题复用"没有兼容文件"，细节是这一句既有文案。
            // 不能退回上面那个 Empty——"这个文件没有依赖"与"你选的这一档没有文件"
            // 会让用户往完全错误的方向去找原因。
            is DiscoverDependenciesState.NoFileForTarget -> {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_install_no_file_title),
                    detail = state.message.toAndroidString(context)
                )
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

/**
 * 一个依赖的行
 *
 * 画的是平台上真实存在的那个项目：图标、项目名、作者、依赖类型、来源平台、
 * 与目标 Minecraft 版本匹配的那个文件版本，以及这个文件支持的加载器。
 *
 * **不画编号**：项目名解析不出来时，标题位留白而不是拿 id 顶上去，
 * 编号只作为一行 faint 的次要信息（`Project 482378`）出现在失败或无名的那一条上。
 *
 * 读失败的这一条自己带错误与重试，来源平台始终作为标签在场——
 * "为什么这条没有名字"至少还有一个可以去核对的地方。
 */
@Composable
private fun DiscoverDependencyRowView(
    row: DiscoverDependencyRow,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
    metrics: OxideMetrics
) {
    val context = LocalContext.current
    val card = row.card
    val enabled = card.installable

    OxideSurface(
        shape = Oxide.RadiusBlock,
        contentPadding = PaddingValues(all = metrics.cardGap * 0.8f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标固定占位，图片加载与失败都不改变这一行的高度
            AssetsIcon(
                modifier = Modifier.clip(Oxide.RadiusSmall),
                size = metrics.dependencyIconSize,
                iconUrl = card.iconUrl
            )
            Spacer(Modifier.width(metrics.cardGap * 0.7f))

            Column(modifier = Modifier.weight(1f)) {
                // 名字解析不出来时这一行整个不画：留一个空的标题位，
                // 或者拿编号顶上去，都会让这一条看起来像是已经有名字了
                card.name?.let { name ->
                    Text(
                        text = name,
                        color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Modrinth 的项目接口不带作者名，这种依赖就没有作者行，而不是画一个空的
                card.author?.let { author ->
                    Spacer(Modifier.height(metrics.cardGap * 0.2f))
                    Text(
                        text = stringResource(R.string.oxide_dis_by, author),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(metrics.cardGap * 0.3f))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap * 0.4f)) {
                    OxideBadge(
                        text = stringResource(card.type.typeLabelRes()),
                        tone = if (card.type == PlatformDependencyType.REQUIRED) {
                            OxideBadgeTone.Warn
                        } else {
                            OxideBadgeTone.Neutral
                        }
                    )
                    OxideBadge(text = card.provider.displayName, tone = OxideBadgeTone.Neutral)
                    card.loader?.let {
                        OxideBadge(text = it, tone = OxideBadgeTone.Neutral)
                    }
                }

                // 兼容的文件版本 + 它支持的 Minecraft 版本
                val label = card.versionLabel
                val gameVersion = card.gameVersion
                val versionText = when {
                    label != null && gameVersion != null -> stringResource(
                        R.string.oxide_dis_dep_meta,
                        label,
                        stringResource(R.string.oxide_dis_dep_game_version, gameVersion)
                    )

                    label != null -> label
                    gameVersion != null -> stringResource(R.string.oxide_dis_dep_game_version, gameVersion)

                    else -> null
                }
                if (versionText != null) {
                    Spacer(Modifier.height(metrics.cardGap * 0.3f))
                    Text(
                        text = versionText,
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val detailText = when (val state = card.state) {
                    is DiscoverDependencyState.Resolving -> stringResource(R.string.oxide_dis_deps_resolving)
                    // 目标 Minecraft 版本已经在上面那一行报过了；
                    // 只有目标没匹配上、于是那一行缺了版本时，才补上文件自己声明的列表
                    is DiscoverDependencyState.Resolved ->
                        if (card.gameVersion == null) state.gameVersions.takeIf { it.isNotBlank() } else null
                    is DiscoverDependencyState.NoCompatibleVersion -> stringResource(
                        R.string.oxide_dis_dep_no_version
                    )

                    is DiscoverDependencyState.Failed -> state.message.toAndroidString(context)
                }
                if (detailText != null && detailText.isNotBlank()) {
                    Spacer(Modifier.height(metrics.cardGap * 0.3f))
                    Text(
                        text = detailText,
                        color = if (card.state is DiscoverDependencyState.Failed) Oxide.FgMuted else Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 编号只作次要信息：无名或失败时才露，永远不当标题
                if (card.showIdDetail) {
                    Spacer(Modifier.height(metrics.cardGap * 0.3f))
                    Text(
                        text = stringResource(R.string.oxide_dis_dep_project_id, card.idDetail.orEmpty()),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (card.retryable) {
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

            // 勾选状态由整行承载，开关本身不再重复朗读；
            // 无名时退回到编号，否则这里会朗读出一个空字符串
            Box(
                modifier = Modifier
                    .toggleable(
                        value = row.selected,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onToggle,
                    )
                    .semantics { contentDescription = card.name ?: card.idDetail.orEmpty() }
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
    val file = sheet.version

    OxideSectionLabel(text = stringResource(R.string.oxide_dis_sheet_file))
    Spacer(Modifier.height(metrics.controlPadding))
    if (file != null) {
        Text(
            text = file.platformDisplayName(),
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
                file.platformVersion(),
                formatFileSize(file.platformFileSize())
            ),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        // 这一档没有兼容文件：把真实原因摆在这里，下面仍然可以改目标版本
        Text(
            text = sheet.noFileMessage?.toAndroidString(context).orEmpty(),
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

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
                // 报的是**解析出来的 Minecraft 版本**，不是实例名：
                // 实例名可能是自定义名、整合包名或加载器 id，只有前者是文件真正要匹配的那一档
                choice.detected -> stringResource(
                    R.string.oxide_dis_sheet_version_auto,
                    sheet.target.minecraftVersion ?: choice.name.orEmpty()
                )

                else -> stringResource(
                    R.string.oxide_dis_sheet_version_chosen,
                    sheet.target.minecraftVersion ?: choice.name.orEmpty()
                )
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
            // 与详情抽屉同一套判定：目标这一档没有兼容文件时说那一句，不说"没有依赖"
            state = sheet.dependencyState(),
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

    // 这一档没有兼容文件时不能确认：没有文件就没有东西可装，确认只会关掉这一层并空手而归
    val confirmEnabled = if (isModpack) {
        instanceNameProblem == null && file != null && !busy
    } else {
        plan.hasTarget && file != null && !busy
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
internal fun DiscoverMobileDataDialog(
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

/**
 * 安装状态 → 提示条上要画的那一行
 *
 * 文案取自既有字符串，一个新的都不加：下载期间那行说明直接用任务自己写的
 * `download_assets_install_progress_downloading` / `..._installing`——
 * 那是链路上本来就在更新、而且已经本地化的那一句，写的是真实的文件名与字节数。
 */
@Composable
private fun discoverNoticeRow(
    install: DiscoverInstall,
    error: ErrorViewModel.ThrowableMessage?,
    taskMessage: AndroidStringText?,
): DiscoverNoticeRow? = when {
    error != null -> DiscoverNoticeRow(
        title = stringResource(R.string.oxide_dis_install_failed_title),
        detail = error.message,
        progress = null,
    )

    else -> when (install) {
        is DiscoverInstall.Idle -> null

        is DiscoverInstall.Resolving -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_resolving),
            detail = null,
            progress = null,
        )

        is DiscoverInstall.NeedsInstance -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_needs_instance_title),
            detail = androidText(R.string.oxide_dis_install_needs_instance_detail),
            progress = null,
        )

        is DiscoverInstall.NoFile -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_no_file_title),
            detail = androidText(R.string.oxide_dis_install_no_file_detail),
            progress = null,
        )

        is DiscoverInstall.Queued -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_queued_title),
            // 任务自己写的那句说明（真实的文件名与字节数），没有就退回既有那句
            detail = taskMessage ?: androidText(R.string.oxide_dis_install_queued_detail),
            // fraction 是 null 时画成空槽：任务说"不确定"，这里就不给一个假数字
            progress = install.queue.fraction ?: 0f,
        )

        is DiscoverInstall.Created -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_created_title),
            detail = androidText(R.string.oxide_dis_install_created_detail, install.instanceName),
            progress = null,
        )

        is DiscoverInstall.Failed -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_failed_title),
            detail = install.message,
            progress = null,
        )

        is DiscoverInstall.Cancelled -> DiscoverNoticeRow(
            title = stringResource(R.string.oxide_dis_install_cancelled_title),
            detail = androidText(R.string.oxide_dis_install_cancelled_detail),
            progress = null,
        )
    }
}

/**
 * 右下角的安装提示
 *
 * 只展示真实的安装状态与安装器回报的错误。下载期间那一条极细的进度线画的是
 * `Task.progress` 的**原值**：-1f 就是不确定，于是画成一条空槽而不是随便填一个数字。
 *
 * 进出都从右边滑：用户报的现象正是"下载完了它不走"，所以真正收掉提示条的是任务结束
 * 那一刻（`OxideDiscoverViewModel.onQueueTaskEnded`），而这一层只负责把离场演掉。
 *
 * 签名收的是 [DiscoverNoticeRow]（普通字段）而不是整个状态层：
 * 这一屏画的东西只有标题、说明、进度条与一枚叉号，
 * 让测试能直接构造这三样东西，就不必为了画一张金标准图去伪造平台模型
 * （`DiscoverInstallSheetHost` 至今仍然做不到，正是因为它的状态带着 `PlatformVersion`，
 * 见 `OxideDiscoverSnapshotTest` 顶部的说明）。
 */
@Composable
internal fun DiscoverInstallNotice(
    notice: DiscoverNoticeRow?,
    action: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
    metrics: OxideMetrics
) {
    // 离场动画期间内容仍然要留着，因此把最后一次非空的那一行记住
    var shown by remember { mutableStateOf(notice) }
    LaunchedEffect(notice) { if (notice != null) shown = notice }

    AnimatedVisibility(
        visible = notice != null,
        enter = slideInHorizontally(tween(Oxide.Motion.PopoverMs)) { it } +
                fadeIn(tween(Oxide.Motion.PopoverFadeMs)),
        exit = slideOutHorizontally(tween(Oxide.Motion.PopoverMs)) { it } +
                fadeOut(tween(Oxide.Motion.PopoverFadeMs)),
    ) {
        val row = shown ?: return@AnimatedVisibility
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
                            text = row.title,
                            color = Oxide.Fg,
                            fontSize = Oxide.Type.Body.fontSize,
                            lineHeight = Oxide.Type.Body.lineHeight,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        row.detail?.let {
                            Spacer(Modifier.height(2.dp))
                            AndroidStringText(
                                text = it,
                                style = Oxide.Type.MicroLabel.copy(color = Oxide.FgFaint),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 用户要的那一条极细的线，就在这行下面
                        if (row.showsProgress) {
                            Spacer(Modifier.height(metrics.cardGap * 0.6f))
                            OxideProgressBar(progress = row.progress ?: 0f)
                        }
                    }
                    action?.let {
                        Spacer(Modifier.width(metrics.controlPadding))
                        it()
                    }
                    Spacer(Modifier.width(metrics.controlPadding))
                    DiscoverCloseButton(metrics = metrics, onClick = onDismiss)
                }
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