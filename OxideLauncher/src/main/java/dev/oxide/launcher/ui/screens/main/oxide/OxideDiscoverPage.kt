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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.oxide.launcher.game.download.assets.platform.getVersions
import dev.oxide.launcher.game.download.assets.platform.modrinth.models.modrinthModLoaderFilters
import dev.oxide.launcher.game.download.assets.platform.searchAssets
import dev.oxide.launcher.game.download.assets.utils.getMcmodTitle
import dev.oxide.launcher.game.download.modpack.install.ModPackInfo
import dev.oxide.launcher.game.download.modpack.install.ModPackInstaller
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
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsPage
import dev.oxide.launcher.ui.screens.content.download.assets.elements.initAll
import dev.oxide.launcher.ui.screens.content.elements.isFilenameInvalid
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

/** 一次搜索取回的结果条数，与资源搜索页保持一致 */
private const val RESULT_LIMIT = 20

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

/**
 * 一条搜索结果：字段都在搜索时取好，绘制时不再访问平台模型
 *
 * [classes] 是发起本次搜索时使用的类别，平台接口对一次搜索只返回这一类结果，
 * 因此它就是这条结果真实所属的类别，安装路径与详情标签都直接用它
 */
private data class DiscoverItem(
    val data: PlatformSearchData,
    val title: String,
    val classes: PlatformClasses,
    val loaderLabel: String?
) {
    val key: String get() = "${data.platform().name}/${data.platformId()}"
}

/** 搜索结果的三种真实状态：加载中、成功（含空结果）、失败 */
private sealed interface DiscoverResults {
    data object Loading : DiscoverResults
    data class Ready(val page: AssetsPage) : DiscoverResults
    data class Failed(val message: AndroidStringText) : DiscoverResults
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

/** 项目详情抽屉的状态 */
private data class DiscoverDetail(
    val item: DiscoverItem,
    val project: DiscoverProjectState,
    val files: DiscoverFilesState
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

/** 整合包安装前的确认内容 */
private data class ModpackDraft(
    val item: DiscoverItem,
    val version: PlatformVersion,
    val instanceName: String
)

/** 版本解析的结果 */
private sealed interface ResolveResult {
    data class Ok(val version: PlatformVersion) : ResolveResult
    data object NoFile : ResolveResult
    data class Failed(val message: AndroidStringText) : ResolveResult
}

/** 安装提示条的内容 */
private data class DiscoverNotice(
    val title: String,
    val detail: AndroidStringText?,
    val action: (@Composable () -> Unit)? = null
)

/**
 * 发现页的状态持有者
 *
 * 只管状态与一次性动作：搜索、安装、把整合包交给 [ModPackInstaller]。
 * 所有网络调用都在 [viewModelScope] 里跑，切页不会中断搜索，正在进行的整合包安装也不会丢。
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

    var results by mutableStateOf<DiscoverResults>(DiscoverResults.Loading)
        private set

    var install by mutableStateOf<DiscoverInstall>(DiscoverInstall.Idle)
        private set

    /** 安装器回报的真实错误，原样展示 */
    var installError by mutableStateOf<ErrorViewModel.ThrowableMessage?>(null)
        private set

    var detail by mutableStateOf<DiscoverDetail?>(null)
        private set

    var modpackDraft by mutableStateOf<ModpackDraft?>(null)
        private set

    var installer by mutableStateOf<ModPackInstaller?>(null)
        private set

    var awaitingMobileData by mutableStateOf(false)
        private set

    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var projectJob: Job? = null
    private var filesJob: Job? = null
    private var mobileDataContinuation: Continuation<Boolean>? = null

    /** 当前是否有筛选条件生效 */
    val hasFilters: Boolean
        get() = query.isNotBlank() || gameVersion.isNotBlank() || modloader != null ||
                sortField != PlatformSortField.RELEVANCE

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
        search()
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
        search()
    }

    fun selectGameVersion(value: String) {
        if (gameVersion == value) return
        gameVersion = value
        search()
    }

    fun selectModloader(value: PlatformDisplayLabel?) {
        if (modloader == value) return
        // 只接受当前平台真实支持的加载器：两个平台的枚举是不同的类型，
        // 一个平台的加载器在另一个平台上无法转换成请求参数，发过去只会静默失效
        val valid = value?.takeIf { it in loadersFor(platform) }
        modloader = valid
        if (valid != value) return
        search()
    }

    fun selectSort(value: PlatformSortField) {
        if (sortField == value) return
        sortField = value
        search()
    }

    fun clearFilters() {
        query = ""
        gameVersion = ""
        modloader = null
        sortField = PlatformSortField.RELEVANCE
        search()
    }

    /** 发起当前条件下的搜索；取消上一次搜索，保证只有最后一次的结果会落地 */
    fun search() {
        searchJob?.cancel()
        val classes = category.classes
        searchJob = viewModelScope.launch {
            results = DiscoverResults.Loading
            searchAssets(
                searchPlatform = platform,
                searchFilter = PlatformSearchFilter(
                    searchName = query,
                    gameVersion = gameVersion,
                    sortField = sortField,
                    modloader = modloader,
                    index = 0,
                    limit = RESULT_LIMIT
                ),
                platformClasses = classes,
                onSuccess = { result ->
                    results = DiscoverResults.Ready(result.getAssetsPage(classes))
                },
                onError = { error ->
                    results = DiscoverResults.Failed(error.message)
                }
            )
        }
    }

    // ---- 安装 ------------------------------------------------------------

    fun install(item: DiscoverItem) {
        if (install.busy()) return

        // 整合包会被安装成独立的实例，不需要选中当前实例
        if (item.classes == PlatformClasses.MOD_PACK) {
            resolveThen(item) { version ->
                modpackDraft = ModpackDraft(
                    item = item,
                    version = version,
                    instanceName = uniqueInstanceName(item.title)
                )
                install = DiscoverInstall.Idle
            }
            return
        }

        if (VersionsManager.currentVersion.value == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }

        resolveThen(item) { version -> submitFile(version, item.classes) }
    }

    /** 安装详情抽屉里选定的具体文件 */
    fun installVersion(item: DiscoverItem, version: PlatformVersion) {
        if (install.busy()) return
        if (item.classes == PlatformClasses.MOD_PACK) {
            modpackDraft = ModpackDraft(item, version, uniqueInstanceName(item.title))
            return
        }
        if (VersionsManager.currentVersion.value == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        submitFile(version, item.classes)
    }

    /** 查询项目可安装的文件；查询失败或没有匹配文件时给出真实的失败状态 */
    private fun resolveThen(item: DiscoverItem, onResolved: (PlatformVersion) -> Unit) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            install = DiscoverInstall.Resolving
            when (val resolved = resolveInstallable(item)) {
                is ResolveResult.Ok -> onResolved(resolved.version)
                is ResolveResult.NoFile -> install = DiscoverInstall.NoFile
                is ResolveResult.Failed -> install = DiscoverInstall.Failed(resolved.message)
            }
        }
    }

    private suspend fun resolveInstallable(item: DiscoverItem): ResolveResult {
        val projectId = item.data.platformId()
        val target = item.data.platform()

        val all = try {
            getVersions(projectID = projectId, platform = target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to load the file list of ${item.title}", e)
            return ResolveResult.Failed(mapExceptionToMessage(e))
        }

        val versions = try {
            all.initAll(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to initialise the files of ${item.title}", e)
            return ResolveResult.Failed(mapExceptionToMessage(e))
        }

        if (versions.isEmpty()) return ResolveResult.NoFile

        val mcVersion = gameVersion
        val loaderName = modloader?.getDisplayName()
        val matched = versions.filter { version ->
            val mcMatches = mcVersion.isBlank() || version.platformGameVersion().contains(mcVersion)
            val loaderMatches = loaderName == null ||
                    version.platformLoaders().any { it.getDisplayName().equals(loaderName, true) }
            mcMatches && loaderMatches
        }

        return ResolveResult.Ok((matched.ifEmpty { versions }).first())
    }

    /** 交给单文件安装器，顺带把必装依赖一起装上 */
    private fun submitFile(version: PlatformVersion, classes: PlatformClasses) {
        val target = VersionsManager.currentVersion.value
        if (target == null) {
            install = DiscoverInstall.NeedsInstance
            return
        }
        val targets = listOf(target)
        val submitError: (ErrorViewModel.ThrowableMessage) -> Unit = { installError = it }

        downloadSingleForVersions(
            version = version,
            versions = targets,
            folder = classes.versionFolder.folderName,
            submitError = submitError
        )

        val dependencies = version.platformDependencies()
            .filter { it.type == PlatformDependencyType.REQUIRED }
            .mapNotNull { dependency ->
                val projectId = dependency.projectId?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                DependencyRequest(
                    platform = dependency.platform,
                    projectId = projectId,
                    versionId = dependency.versionId,
                    classes = classes,
                    projectTitle = version.platformDisplayName()
                )
            }
        if (dependencies.isNotEmpty()) {
            downloadDependenciesForVersions(
                requests = dependencies,
                versions = targets,
                submitError = submitError
            )
        }

        install = DiscoverInstall.Queued(version.platformDisplayName())
    }

    // ---- 整合包安装 ------------------------------------------------------

    fun setModpackInstanceName(value: String) {
        modpackDraft = modpackDraft?.copy(instanceName = value)
    }

    fun dismissModpackDraft() {
        modpackDraft = null
    }

    /** 用户确认后创建整合包安装器，安装过程全部交给它自己完成 */
    fun confirmModpackInstall(context: Context) {
        val draft = modpackDraft ?: return
        modpackDraft = null

        val instanceName = draft.instanceName
        val created = ModPackInstaller(
            context = context,
            version = draft.version,
            iconUrl = draft.item.data.platformIconUrl(),
            scope = viewModelScope,
            waitForVersionName = { _: ModPackInfo -> instanceName },
            waitForConfirmMobileData = ::awaitMobileDataDecision
        )
        installer = created
        created.installModPack(
            onInstalled = { name ->
                installer = null
                VersionsManager.refresh("$TAG: ModPackInstaller.onInstalled", name)
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
        detail = DiscoverDetail(
            item = item,
            project = DiscoverProjectState.Loading,
            files = DiscoverFilesState.Loading
        )
        loadProject(item)
        loadFiles(item)
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

    fun dismissInstallState() {
        install = DiscoverInstall.Idle
    }

    fun dismissInstallError() {
        installError = null
    }

    override fun onCleared() {
        searchJob?.cancel()
        resolveJob?.cancel()
        projectJob?.cancel()
        filesJob?.cancel()
        installer?.cancelInstall()
        mobileDataContinuation = null
    }
}

/** 安装进行中时不允许重复触发 */
private fun DiscoverInstall.busy(): Boolean = when (this) {
    is DiscoverInstall.Resolving, is DiscoverInstall.Queued -> true
    else -> false
}

/** 只有 CurseForge 提供存档 */
private fun PlatformClasses.supportsModrinth(): Boolean = this != PlatformClasses.SAVES

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

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

/**
 * 发现页
 *
 * 结构照参考稿：左侧类别栏 + 右侧「搜索与筛选行 + 结果网格」。
 * 数据全部来自真实的平台搜索（CurseForge / Modrinth），安装动作调用现有的单文件安装器
 * 与整合包安装器，网格里的每一条都是平台上的真实项目，不会出现占位结果。
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
    val installedMods = installed.installedByProject

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
    }

    // 只有筛选条件真的变了才重新搜索，组合本身不会触发任何请求
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
                            count = viewModel.results.resultCount(),
                            scanningInstalled = installed.matching,
                            onlyInstalled = viewModel.onlyInstalled
                        )
                        Spacer(Modifier.height(metrics.cardGap * 0.7f))
                        DiscoverResultsGrid(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            metrics = metrics,
                            state = viewModel.results,
                            category = viewModel.category,
                            onlyInstalled = viewModel.onlyInstalled,
                            installedIds = installedIds,
                            busy = viewModel.install.busy(),
                            onRetry = viewModel::search,
                            onOpen = viewModel::openDetail,
                            onInstall = viewModel::install,
                            onGoInstances = { onNavigate(OxidePage.Instances) }
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
        onInstallVersion = viewModel::installVersion
    )

    DiscoverInstallDrawer(
        metrics = metrics,
        installer = viewModel.installer,
        onCancel = viewModel::cancelModpackInstall
    )

    viewModel.modpackDraft?.let { draft ->
        DiscoverModpackDialog(
            metrics = metrics,
            draft = draft,
            onNameChange = viewModel::setModpackInstanceName,
            onCancel = viewModel::dismissModpackDraft,
            onConfirm = { viewModel.confirmModpackInstall(context) }
        )
    }

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

/** 搜索结果里可见的条数 */
private fun DiscoverResults.resultCount(): Int = when (this) {
    is DiscoverResults.Ready -> page.data.size
    else -> 0
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

/** 结果区顶部：条数 + 已安装扫描状态 */
@Composable
private fun DiscoverResultsHeader(count: Int, scanningInstalled: Boolean, onlyInstalled: Boolean) {
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
 * 加载中、空结果与失败是三种各自独立的状态，不会互相折叠。
 */
@Composable
private fun DiscoverResultsGrid(
    state: DiscoverResults,
    category: DiscoverCategory,
    onlyInstalled: Boolean,
    installedIds: Set<String>,
    busy: Boolean,
    onRetry: () -> Unit,
    onOpen: (DiscoverItem) -> Unit,
    onInstall: (DiscoverItem) -> Unit,
    onGoInstances: () -> Unit,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    when (state) {
        is DiscoverResults.Loading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                OxideLoadingRow(text = stringResource(R.string.oxide_dis_loading))
            }
        }

        is DiscoverResults.Failed -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                OxideEmptyState(
                    title = stringResource(R.string.oxide_dis_error_title),
                    detail = state.message.toAndroidString(context),
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

        is DiscoverResults.Ready -> {
            val items = remember(state.page, context) {
                state.page.data.map { (data, mcmod) ->
                    DiscoverItem(
                        data = data,
                        title = mcmod.getMcmodTitle(data.platformTitle(), context),
                        classes = category.classes,
                        loaderLabel = data.platformModLoaders()
                            ?.firstOrNull()
                            ?.getDisplayName()
                            ?.takeIf { it.isNotBlank() }
                    )
                }
            }
            val visible = remember(items, onlyInstalled, installedIds) {
                if (onlyInstalled) {
                    items.filter { it.data.platformId() in installedIds }
                } else {
                    items
                }
            }

            if (visible.isEmpty()) {
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
            } else {
                BoxWithConstraints(modifier = modifier) {
                    val columns = discoverResultColumns(metrics, maxWidth)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
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
                    }
                }
            }
        }
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

/** 项目详情抽屉：概览与文件列表两个标签页 */
@Composable
private fun DiscoverDetailHost(
    detail: DiscoverDetail?,
    currentVersionName: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (DiscoverItem) -> Unit,
    onInstallVersion: (DiscoverItem, PlatformVersion) -> Unit,
    metrics: OxideMetrics
) {
    var tab by remember { mutableStateOf(0) }

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
                    stringResource(R.string.oxide_dis_drawer_tab_files)
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
                if (tab == 0) {
                    DiscoverOverview(
                        detail = current,
                        currentVersionName = currentVersionName,
                        busy = busy,
                        onInstall = { onInstall(current.item) },
                        metrics = metrics
                    )
                } else {
                    DiscoverFiles(
                        item = current.item,
                        files = current.files,
                        busy = busy,
                        onInstall = { version -> onInstallVersion(current.item, version) },
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

/** 整合包安装前的确认：显示将要创建的实例名，可修改 */
@Composable
private fun DiscoverModpackDialog(
    draft: ModpackDraft,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    metrics: OxideMetrics
) {
    val nameError = isFilenameInvalid(draft.instanceName)
    val exists = remember(draft.instanceName) {
        VersionsManager.isVersionExists(draft.instanceName, true)
    }
    val problem = when {
        draft.instanceName.isBlank() -> stringResource(R.string.oxide_dis_modpack_name_required)
        exists -> stringResource(R.string.oxide_dis_modpack_name_exists)
        else -> nameError?.takeIf { it.isNotBlank() }
    }
    val valid = problem == null

    OxideModalHost(metrics = metrics, onDismiss = onCancel) {
        OxideSectionLabel(text = stringResource(R.string.oxide_dis_modpack_dialog_title))
        Spacer(Modifier.height(metrics.cardGap * 0.8f))
        Text(
            text = draft.item.title,
            color = Oxide.Fg,
            fontSize = Oxide.Type.Title.fontSize,
            lineHeight = Oxide.Type.Title.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(metrics.controlPadding))
        Text(
            text = stringResource(R.string.oxide_dis_modpack_dialog_detail),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight
        )

        Spacer(Modifier.height(metrics.cardGap))
        OxideSectionLabel(text = stringResource(R.string.oxide_dis_modpack_name_label))
        Spacer(Modifier.height(metrics.controlPadding))
        DiscoverField(
            modifier = Modifier.fillMaxWidth(),
            metrics = metrics,
            value = draft.instanceName,
            onValueChange = onNameChange,
            onSubmit = { if (valid) onConfirm() },
            placeholder = draft.item.title,
            imeAction = ImeAction.Done
        )

        problem?.let {
            Spacer(Modifier.height(metrics.controlPadding))
            Text(
                text = it,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(metrics.cardGap))
        DiscoverDialogActions(
            metrics = metrics,
            confirmText = stringResource(R.string.oxide_dis_action_install),
            confirmEnabled = valid,
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

/** 移动网络确认：安装器会在这里挂起 */
@Composable
private fun DiscoverMobileDataDialog(
    metrics: OxideMetrics,
    onDeny: () -> Unit,
    onAllow: () -> Unit
) {
    OxideModalHost(metrics = metrics, onDismiss = onDeny) {
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

/** 居中的模态层：半透明遮罩 + 居中面板，全部使用 Oxide 的面板样式 */
@Composable
private fun OxideModalHost(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    panelWidthFraction: Float = 0.82f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Oxide.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
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
                modifier = Modifier.width(metrics.drawerWidth * panelWidthFraction),
                shape = Oxide.RadiusDrawer,
                contentPadding = PaddingValues(all = metrics.cardGap * 1.2f)
            ) {
                content()
            }
        }
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
