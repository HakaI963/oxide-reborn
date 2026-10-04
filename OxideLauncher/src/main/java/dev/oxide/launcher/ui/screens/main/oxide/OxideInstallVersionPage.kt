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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonSyntaxException
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskLogOutput
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.game.addons.modloader.AddonVersion
import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.addons.modloader.cleanroom.CleanroomVersion
import dev.oxide.launcher.game.addons.modloader.cleanroom.CleanroomVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.FabricLikeVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricAPIVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricAPIVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltAPIVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersions
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import dev.oxide.launcher.game.addons.modloader.modlike.ModVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersions
import dev.oxide.launcher.game.download.game.GameDownloadInfo
import dev.oxide.launcher.game.download.game.GameInstaller
import dev.oxide.launcher.game.download.game.optifine.CantFetchingOptiFineUrlException
import dev.oxide.launcher.game.download.game.optifine.OptiFineForge17IncompatibleException
import dev.oxide.launcher.game.download.jvm_server.JvmCrashException
import dev.oxide.launcher.game.download.jvm_server.isProcessStartRefused
import dev.oxide.launcher.game.version.download.DownloadFailedException
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.game.version.installed.utils.isBiggerVer
import dev.oxide.launcher.game.versioninfo.MinecraftVersion
import dev.oxide.launcher.game.versioninfo.MinecraftVersions
import dev.oxide.launcher.game.versioninfo.models.isType
import dev.oxide.launcher.notification.NotificationManager
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.content.download.game.AddonList
import dev.oxide.launcher.ui.screens.content.download.game.AddonState
import dev.oxide.launcher.ui.screens.content.download.game.CurrentAddon
import dev.oxide.launcher.ui.screens.content.download.game.LoaderVerSupports
import dev.oxide.launcher.ui.screens.content.download.game.isOptiFineCompatibleWithForge
import dev.oxide.launcher.ui.screens.content.download.game.runWithState
import dev.oxide.launcher.ui.screens.content.elements.isFilenameInvalid
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.getTimeAgo
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.network.isUsingMobileData
import dev.oxide.launcher.utils.network.toLocal
import dev.oxide.launcher.viewmodel.sendKeepScreen
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException

private const val INSTALL_TAG = "OxideInstallVersionPage"

// ---------------------------------------------------------------------------
// 纯逻辑
//
// 这一段里没有 Compose、也没有 Android API，因此可以直接单测：
// 版本过滤、加载器组合、版本名自动生成、API 未选警告、步骤可达性。
// ---------------------------------------------------------------------------

/**
 * 版本过滤条件
 *
 * 与旧选择页 `SelectGameVersionScreen` 的过滤条件逐项对应，默认只放正式版本。
 */
internal data class OxideVersionFilter(
    val release: Boolean = true,
    val snapshot: Boolean = false,
    val aprilFools: Boolean = false,
    val old: Boolean = false,
    val id: String = "",
)

/**
 * 按条件过滤版本列表
 *
 * 类型判定直接复用后端 [isType]（未知版本归入"远古版"），版本号再单独做一次包含匹配，
 * 与旧选择页的 `filterVersions` 完全一致。
 */
internal fun filterOxideVersions(
    versions: List<MinecraftVersion>,
    filter: OxideVersionFilter,
): List<MinecraftVersion> = versions
    .filter { version ->
        version.isType(
            release = filter.release,
            snapshot = filter.snapshot,
            aprilFools = filter.aprilFools,
            old = filter.old,
        )
    }
    .filter { version ->
        filter.id.isBlank() || version.version.id.contains(filter.id)
    }

/**
 * 安装流程的三步
 *
 * 步骤本身就是导航：后面两步都要先有 Minecraft 版本，因此可达性由
 * [oxideInstallStepReachable] 判定，而不是由每一块界面各自决定。
 */
internal enum class OxideInstallStep(val titleRes: Int) {
    Version(R.string.oxide_inst_step_version),
    Loader(R.string.oxide_inst_step_loader),
    Install(R.string.oxide_inst_step_install),
}

/** 还没选 Minecraft 版本时，后面的步骤都不可达 */
internal fun oxideInstallStepReachable(step: OxideInstallStep, gameVersion: String?): Boolean =
    when (step) {
        OxideInstallStep.Version -> true
        OxideInstallStep.Loader, OxideInstallStep.Install -> !gameVersion.isNullOrBlank()
    }

/**
 * 某个 Minecraft 版本上会出现哪些加载器
 *
 * `rememberLoaderVerSupports` 是同一套判定，只是它把结果记在组合期状态里；
 * 这里是它的纯函数形式，供单测直接钉住"哪个版本能选哪些加载器"这条契约，
 * 界面本身也只走这一份判定，两个入口不会分叉。
 *
 * 顺序与旧安装页一致：OptiFine、Forge、NeoForge、Cleanroom、Fabric 系、Quilt 系，
 * API 模组永远紧跟在自己的加载器后面。
 */
internal fun oxideLoaderVerSupports(mcVer: String): LoaderVerSupports {
    val fabricLike = mcVer.isBiggerVer("1.13.2")
    return LoaderVerSupports(
        isNeoForgeSupports = mcVer.isBiggerVer("1.20"),
        isFabricSupports = fabricLike,
        isQuiltSupports = fabricLike,
        isCleanroomSupports = mcVer == "1.12.2",
        isLegacyFabricSupports = !fabricLike,
    )
}

/**
 * 一个加载器 / API 模组在安装页里的位置
 *
 * [apiOf] 不为 null 表示它依附的加载器：Fabric 系加载器与它的 API 是"选一个就配一对"，
 * 因此两步在界面上也是相邻的两行。
 */
internal data class OxideAddonSlot(
    val loader: ModLoader,
    val apiOf: ModLoader? = null,
)

/** 某个 Minecraft 版本上真实可用的加载器组，顺序即展示顺序 */
internal fun oxideAddonPlan(mcVer: String): List<OxideAddonSlot> {
    val supports = oxideLoaderVerSupports(mcVer)
    return buildList {
        add(OxideAddonSlot(ModLoader.OPTIFINE))
        add(OxideAddonSlot(ModLoader.FORGE))
        if (supports.isNeoForgeSupports) add(OxideAddonSlot(ModLoader.NEOFORGE))
        if (supports.isCleanroomSupports) add(OxideAddonSlot(ModLoader.CLEANROOM))
        if (supports.isFabricSupports) {
            add(OxideAddonSlot(ModLoader.FABRIC))
            add(OxideAddonSlot(ModLoader.FABRIC_API, apiOf = ModLoader.FABRIC))
        }
        if (supports.isLegacyFabricSupports) {
            add(OxideAddonSlot(ModLoader.LEGACY_FABRIC))
            add(OxideAddonSlot(ModLoader.LEGACY_FABRIC_API, apiOf = ModLoader.LEGACY_FABRIC))
        }
        if (supports.isQuiltSupports) {
            add(OxideAddonSlot(ModLoader.QUILT))
            add(OxideAddonSlot(ModLoader.QUILT_API, apiOf = ModLoader.QUILT))
        }
    }
}

/** 加载器已选、API 列表已到、但 API 一个都没选时应当提醒 */
internal fun oxideNeedsApiWarning(
    loaderChosen: Boolean,
    apiChosen: Boolean,
    apiListLoaded: Boolean,
    apiListEmpty: Boolean,
): Boolean = loaderChosen && !apiChosen && apiListLoaded && !apiListEmpty

/**
 * 版本名称的自动生成
 *
 * 与旧安装页 `AutoChangeVersionName` 的拼接顺序逐条一致：OptiFine 与 Forge 同时选中时
 * 写成 `<Minecraft 版本> Forge <版本>-OptiFine <版本>`，只选一个加载器就是
 * `<Minecraft 版本> <加载器> <版本>`，一个都没选就是 Minecraft 版本本身。
 */
internal fun oxideInstallVersionName(
    gameVersion: String,
    optifine: OptiFineVersion?,
    forge: ForgeVersion?,
    neoforge: NeoForgeVersion?,
    fabric: FabricVersion?,
    legacyFabric: LegacyFabricVersion?,
    quilt: QuiltVersion?,
    cleanroom: CleanroomVersion?,
): String {
    fun modloader(name: String, version: String) = "$name $version"
    fun optiFineOf(value: OptiFineVersion) = modloader(ModLoader.OPTIFINE.displayName, value.realVersion)
    fun forgeOf(value: ForgeVersion) = modloader(ModLoader.FORGE.displayName, value.versionName)

    val modloaderValue = when {
        optifine != null && forge != null -> forgeOf(forge) + "-" + optiFineOf(optifine)
        optifine != null -> optiFineOf(optifine)
        forge != null -> forgeOf(forge)
        neoforge != null -> modloader(ModLoader.NEOFORGE.displayName, neoforge.versionName)
        fabric != null -> modloader(ModLoader.FABRIC.displayName, fabric.version)
        legacyFabric != null -> modloader(ModLoader.LEGACY_FABRIC.displayName, legacyFabric.version)
        quilt != null -> modloader(ModLoader.QUILT.displayName, quilt.version)
        cleanroom != null -> modloader(ModLoader.CLEANROOM.displayName, cleanroom.version)
        else -> return gameVersion
    }
    return "$gameVersion $modloaderValue"
}

/**
 * 把已选加载器组装成真正的安装请求
 *
 * 与旧安装页逐字段一致：不支持该 Minecraft 版本的加载器直接不参与，
 * 因此 1.12.2 上不可能组装出一个带 Fabric 的组合。
 */
internal fun oxideGameDownloadInfo(
    gameVersion: String,
    customVersionName: String,
    supports: LoaderVerSupports,
    current: CurrentAddon,
): GameDownloadInfo = GameDownloadInfo(
    gameVersion = gameVersion,
    customVersionName = customVersionName,
    optifine = current.optifineVersion.value,
    forge = current.forgeVersion.value,
    neoforge = current.neoforgeVersion.value.takeIf { supports.isNeoForgeSupports },
    fabric = current.fabricVersion.value.takeIf { supports.isFabricSupports },
    fabricAPI = current.fabricAPIVersion.value.takeIf { supports.isFabricSupports },
    legacyFabric = current.legacyFabricVersion.value.takeIf { supports.isLegacyFabricSupports },
    legacyFabricAPI = current.legacyFabricAPIVersion.value.takeIf { supports.isLegacyFabricSupports },
    quilt = current.quiltVersion.value.takeIf { supports.isQuiltSupports },
    quiltAPI = current.quiltAPIVersion.value.takeIf { supports.isQuiltSupports },
    cleanroom = current.cleanroomVersion.value.takeIf { supports.isCleanroomSupports },
)

/**
 * OptiFine 列表按已选 Forge 过滤
 *
 * 选了 Forge 之后只有声明了兼容该 Forge 的 OptiFine 才允许选；
 * 没声明所需 Forge 版本的按不兼容处理——与旧列表的过滤规则完全相同。
 */
internal fun filterOptiFineAgainstForge(
    items: List<OptiFineVersion>,
    forge: ForgeVersion?,
): List<OptiFineVersion> = items.filter { optifine ->
    forge?.let { isOptiFineCompatibleWithForge(optifine, it) } ?: true
}

/** Forge 列表按已选 OptiFine 过滤 */
internal fun filterForgeAgainstOptiFine(
    items: List<ForgeVersion>,
    optifine: OptiFineVersion?,
): List<ForgeVersion> = items.filter { forge ->
    optifine?.let { isOptiFineCompatibleWithForge(it, forge) } ?: true
}

/** 单个 Forge 版本是否属于"无法自动安装"的那一类 */
internal fun forgeNeedsManualInstall(forge: ForgeVersion): Boolean =
    forge.category == "universal" || forge.category == "client"

/** Forge 列表里存在无法自动安装的版本时，如实说明这一点 */
internal fun forgeHasUninstallableEntries(items: List<ForgeVersion>?): Boolean =
    items?.any { forgeNeedsManualInstall(it) } == true

/** 这一类加载器的版本列表是否包含"需要手工安装"的条目 */
internal fun forgeInstallableVersions(items: List<ForgeVersion>): List<ForgeVersion> =
    items.filterNot { forgeNeedsManualInstall(it) }

// ---------------------------------------------------------------------------
// 三种真实状态
// ---------------------------------------------------------------------------

/**
 * 版本清单的三种状态
 *
 * 这三态与"修改版本"那一页共用：两页读的是同一份 [MinecraftVersions]，
 * 用的是同一套过滤，因此状态本身也应该只有一份。
 */
internal sealed interface OxideVersionState {
    data object Loading : OxideVersionState
    data class Ready(val versions: List<MinecraftVersion>) : OxideVersionState
    data class Failed(val message: AndroidStringText) : OxideVersionState
}

/** 安装动作的状态机，与旧安装页逐项对应 */
private sealed interface OxideInstallOperation {
    data object None : OxideInstallOperation
    data class WarningForNotification(val info: GameDownloadInfo) : OxideInstallOperation
    data class WarningForMobileData(val info: GameDownloadInfo) : OxideInstallOperation
    data object Installing : OxideInstallOperation
    data object AlreadyInstalled : OxideInstallOperation
    data class Failed(val th: Throwable) : OxideInstallOperation
    data object Succeeded : OxideInstallOperation
}

// ---------------------------------------------------------------------------
// ViewModel：只做状态与一次性动作，网络与磁盘都在 viewModelScope 里
// ---------------------------------------------------------------------------

/**
 * 版本列表
 *
 * 与旧选择页的 `VersionsViewModel` 逐行对应：同一个 [MinecraftVersions] 来源、
 * 同一套过滤、同一份错误翻译；只是结果交给整块 Oxide 表面，而不是旧对话框。
 * 「修改版本」那一页也直接用这一个，版本清单因此在整个新界面里只有一份实现。
 */
internal class OxideInstallVersionsViewModel : ViewModel() {
    var versionState by mutableStateOf<OxideVersionState>(OxideVersionState.Loading)
        private set

    var versionFilter by mutableStateOf(OxideVersionFilter())
        private set

    fun filterWith(filter: OxideVersionFilter) {
        versionFilter = filter
        viewModelScope.launch {
            versionState = OxideVersionState.Ready(
                filterOxideVersions(MinecraftVersions.allVersions.value, filter)
            )
        }
    }

    fun refresh(forceReload: Boolean = false) {
        viewModelScope.launch {
            versionState = OxideVersionState.Loading
            versionState = runCatching {
                MinecraftVersions.refreshVersions(forceReload)
                OxideVersionState.Ready(
                    filterOxideVersions(MinecraftVersions.allVersions.value, versionFilter)
                )
            }.getOrElse { e ->
                Logger.warning(INSTALL_TAG, "Failed to get version manifest!", e)
                OxideVersionState.Failed(installRequestErrorText(e))
            }
        }
    }

    init {
        refresh()
    }
}

/**
 * 加载器与 API 模组
 *
 * 请求方式、状态机与不兼容判定全部复用后端既有的实现：[AddonList] 存列表、
 * [CurrentAddon] 存选择与不兼容集合、`runWithState` 负责把异常翻译成 [AddonState]。
 * 只有 [bind] 会因为换了 Minecraft 版本而重新拉取——旧实现是把 ViewModel 的 key 拼上
 * 版本号，触发时机与结果完全一致。
 */
private class OxideInstallAddonsViewModel : ViewModel() {
    val addonList = AddonList()
    val currentAddon = CurrentAddon()

    /** 当前已为哪个 Minecraft 版本拉过列表 */
    private var loadedVersion: String? = null
    private var loadedSupports: LoaderVerSupports? = null

    /** 版本切换次数，让组合在换版本时重新取一次派生状态 */
    var bindCount by mutableIntStateOf(0)
        private set

    val supports: LoaderVerSupports? get() = loadedSupports

    fun bind(gameVersion: String) {
        val supports = oxideLoaderVerSupports(gameVersion)
        if (loadedVersion == gameVersion) return
        loadedVersion = gameVersion
        loadedSupports = supports
        bindCount++
        clearAllSelections()
        reloadOptiFine()
        reloadForge()
        if (supports.isNeoForgeSupports) reloadNeoForge()
        if (supports.isFabricSupports) {
            reloadFabric()
            reloadFabricAPI()
        }
        if (supports.isLegacyFabricSupports) {
            reloadLegacyFabric()
            reloadLegacyFabricAPI()
        }
        if (supports.isQuiltSupports) {
            reloadQuilt()
            reloadQuiltAPI()
        }
        if (supports.isCleanroomSupports) reloadCleanroom()
    }

    fun reloadOptiFine() = launchAddonReload(
        { currentAddon.optifineState = it },
        { OptiFineVersions.fetchOptiFineList(gameVersion = loadedVersion!!) },
        { addonList.optifineList = it },
    )

    fun reloadForge() = launchAddonReload(
        { currentAddon.forgeState = it },
        { ForgeVersions.fetchForgeList(loadedVersion!!) },
        { addonList.forgeList = it },
    )

    fun reloadNeoForge() = launchAddonReload(
        { currentAddon.neoforgeState = it },
        { NeoForgeVersions.fetchNeoForgeList(gameVersion = loadedVersion!!) },
        { addonList.neoforgeList = it },
    )

    fun reloadFabric() = launchAddonReload(
        { currentAddon.fabricState = it },
        { FabricVersions.fetchFabricLoaderList(loadedVersion!!) },
        { addonList.fabricList = it },
    )

    fun reloadFabricAPI() = launchAddonReload(
        { currentAddon.fabricAPIState = it },
        { FabricAPIVersions.fetchVersionList(loadedVersion!!) },
        { addonList.fabricAPIList = it },
    )

    fun reloadLegacyFabric() = launchAddonReload(
        { currentAddon.legacyFabricState = it },
        { LegacyFabricVersions.fetchFabricLoaderList(loadedVersion!!) },
        { addonList.legacyFabricList = it },
    )

    fun reloadLegacyFabricAPI() = launchAddonReload(
        { currentAddon.legacyFabricAPIState = it },
        { LegacyFabricAPIVersions.fetchVersionList(loadedVersion!!) },
        { addonList.legacyFabricAPIList = it },
    )

    fun reloadQuilt() = launchAddonReload(
        { currentAddon.quiltState = it },
        { QuiltVersions.fetchQuiltLoaderList(loadedVersion!!) },
        { addonList.quiltList = it },
    )

    fun reloadQuiltAPI() = launchAddonReload(
        { currentAddon.quiltAPIState = it },
        { QuiltAPIVersions.fetchVersionList(loadedVersion!!) },
        { addonList.quiltAPIList = it },
    )

    fun reloadCleanroom() = launchAddonReload(
        { currentAddon.cleanroomState = it },
        { CleanroomVersions.fetchLoaderList(loadedVersion!!) },
        { addonList.cleanroomList = it },
    )

    /** 错误条上的刷新：只重新拉取这一类 */
    fun reload(slot: OxideAddonSlot) {
        when (slot.loader) {
            ModLoader.OPTIFINE -> reloadOptiFine()
            ModLoader.FORGE -> reloadForge()
            ModLoader.NEOFORGE -> reloadNeoForge()
            ModLoader.FABRIC -> reloadFabric()
            ModLoader.FABRIC_API -> reloadFabricAPI()
            ModLoader.LEGACY_FABRIC -> reloadLegacyFabric()
            ModLoader.LEGACY_FABRIC_API -> reloadLegacyFabricAPI()
            ModLoader.QUILT -> reloadQuilt()
            ModLoader.QUILT_API -> reloadQuiltAPI()
            ModLoader.CLEANROOM -> reloadCleanroom()
            else -> Unit
        }
    }

    private fun <T> launchAddonReload(
        updateState: (AddonState) -> Unit,
        fetch: suspend () -> T?,
        onSuccess: (T?) -> Unit,
    ) {
        viewModelScope.launch {
            runWithState(updateState, fetch).also(onSuccess)
        }
    }

    // ---- 选择 ----

    fun select(slot: OxideAddonSlot, version: AddonVersion?) {
        when (slot.loader) {
            ModLoader.OPTIFINE -> currentAddon.optifineVersion.value = version as? OptiFineVersion
            ModLoader.FORGE -> currentAddon.forgeVersion.value = version as? ForgeVersion
            ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value = version as? NeoForgeVersion

            ModLoader.FABRIC -> {
                currentAddon.fabricVersion.value = version as? FabricVersion
                // 手动选了 Fabric 就自动配上最新的 Fabric API，与旧安装页一致
                if (version != null) {
                    currentAddon.fabricAPIVersion.value = addonList.fabricAPIList?.firstOrNull()
                }
            }

            ModLoader.FABRIC_API -> currentAddon.fabricAPIVersion.value = version as? ModVersion

            ModLoader.LEGACY_FABRIC -> {
                currentAddon.legacyFabricVersion.value = version as? LegacyFabricVersion
                if (version != null) {
                    currentAddon.legacyFabricAPIVersion.value = addonList.legacyFabricAPIList?.firstOrNull()
                }
            }

            ModLoader.LEGACY_FABRIC_API ->
                currentAddon.legacyFabricAPIVersion.value = version as? ModVersion

            ModLoader.QUILT -> {
                currentAddon.quiltVersion.value = version as? QuiltVersion
                if (version != null) {
                    currentAddon.quiltAPIVersion.value = addonList.quiltAPIList?.firstOrNull()
                }
            }

            ModLoader.QUILT_API -> currentAddon.quiltAPIVersion.value = version as? ModVersion
            ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value = version as? CleanroomVersion
            else -> Unit
        }
        refreshIncompatible()
    }

    fun clear(slot: OxideAddonSlot) {
        select(slot, null)
    }

    fun clearAllSelections() {
        currentAddon.optifineVersion.value = null
        currentAddon.forgeVersion.value = null
        currentAddon.neoforgeVersion.value = null
        currentAddon.fabricVersion.value = null
        currentAddon.fabricAPIVersion.value = null
        currentAddon.legacyFabricVersion.value = null
        currentAddon.legacyFabricAPIVersion.value = null
        currentAddon.quiltVersion.value = null
        currentAddon.quiltAPIVersion.value = null
        currentAddon.cleanroomVersion.value = null
    }

    /**
     * 重算不兼容集合，并把因此被挡住的项清空
     *
     * 旧列表是在每一项自己的 `LaunchedEffect` 里判一次；这里在选择或列表状态变化后
     * 对全部加载器各调一次，判定只依赖当前选择集合，因此结果与逐项调用一致。
     */
    fun reconcile(plan: List<OxideAddonSlot>) {
        refreshIncompatible()
        val blocked = blockedLoaders()
        if (blocked.isEmpty()) return
        plan.filter { it.loader in blocked }.forEach { clear(it) }
    }

    fun refreshIncompatible() {
        val addon = currentAddon
        val list = addonList
        addon.updateIncompatibleState(ModLoader.OPTIFINE, list)
        addon.updateIncompatibleState(ModLoader.FORGE, list)
        addon.updateIncompatibleState(ModLoader.NEOFORGE, list)
        addon.updateIncompatibleState(ModLoader.FABRIC, list)
        addon.updateIncompatibleState(ModLoader.FABRIC_API, list)
        addon.updateIncompatibleState(ModLoader.LEGACY_FABRIC, list)
        addon.updateIncompatibleState(ModLoader.LEGACY_FABRIC_API, list)
        addon.updateIncompatibleState(ModLoader.QUILT, list)
        addon.updateIncompatibleState(ModLoader.QUILT_API, list)
        addon.updateIncompatibleState(ModLoader.CLEANROOM, list)
    }

    /** 与当前选择冲突的加载器 */
    fun blockedLoaders(): Set<ModLoader> {
        val addon = currentAddon
        return buildSet {
            if (addon.incompatibleWithOptiFine.value.isNotEmpty()) add(ModLoader.OPTIFINE)
            if (addon.incompatibleWithForge.value.isNotEmpty()) add(ModLoader.FORGE)
            if (addon.incompatibleWithNeoForge.value.isNotEmpty()) add(ModLoader.NEOFORGE)
            if (addon.incompatibleWithFabric.value.isNotEmpty()) add(ModLoader.FABRIC)
            if (addon.incompatibleWithFabricAPI.value.isNotEmpty()) add(ModLoader.FABRIC_API)
            if (addon.incompatibleWithLegacyFabric.value.isNotEmpty()) add(ModLoader.LEGACY_FABRIC)
            if (addon.incompatibleWithLegacyFabricAPI.value.isNotEmpty()) {
                add(ModLoader.LEGACY_FABRIC_API)
            }
            if (addon.incompatibleWithQuilt.value.isNotEmpty()) add(ModLoader.QUILT)
            if (addon.incompatibleWithQuiltAPI.value.isNotEmpty()) add(ModLoader.QUILT_API)
            if (addon.incompatibleWithCleanroom.value.isNotEmpty()) add(ModLoader.CLEANROOM)
        }
    }

    /** 这一项当前的加载状态 */
    fun stateOf(slot: OxideAddonSlot): AddonState = when (slot.loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineState
        ModLoader.FORGE -> currentAddon.forgeState
        ModLoader.NEOFORGE -> currentAddon.neoforgeState
        ModLoader.FABRIC -> currentAddon.fabricState
        ModLoader.FABRIC_API -> currentAddon.fabricAPIState
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricState
        ModLoader.LEGACY_FABRIC_API -> currentAddon.legacyFabricAPIState
        ModLoader.QUILT -> currentAddon.quiltState
        ModLoader.QUILT_API -> currentAddon.quiltAPIState
        ModLoader.CLEANROOM -> currentAddon.cleanroomState
        else -> AddonState.None
    }

    /** 这一项当前选中的版本 */
    fun selectedOf(slot: OxideAddonSlot): AddonVersion? = when (slot.loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineVersion.value
        ModLoader.FORGE -> currentAddon.forgeVersion.value
        ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value
        ModLoader.FABRIC -> currentAddon.fabricVersion.value
        ModLoader.FABRIC_API -> currentAddon.fabricAPIVersion.value
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricVersion.value
        ModLoader.LEGACY_FABRIC_API -> currentAddon.legacyFabricAPIVersion.value
        ModLoader.QUILT -> currentAddon.quiltVersion.value
        ModLoader.QUILT_API -> currentAddon.quiltAPIVersion.value
        ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value
        else -> null
    }

    /**
     * 这一项的原始版本列表
     *
     * 过滤规则留在这里而不是界面里：OptiFine 与 Forge 互相过滤，
     * universal / client 的 Forge 无法自动安装，因此不进入可选列表。
     */
    fun versionsOf(slot: OxideAddonSlot): List<AddonVersion>? = when (slot.loader) {
        ModLoader.OPTIFINE -> addonList.optifineList
            ?.let { filterOptiFineAgainstForge(it, currentAddon.forgeVersion.value) }

        ModLoader.FORGE -> addonList.forgeList
            ?.let { filterForgeAgainstOptiFine(forgeInstallableVersions(it), currentAddon.optifineVersion.value) }

        ModLoader.NEOFORGE -> addonList.neoforgeList
        ModLoader.FABRIC -> addonList.fabricList
        ModLoader.FABRIC_API -> addonList.fabricAPIList
        ModLoader.LEGACY_FABRIC -> addonList.legacyFabricList
        ModLoader.LEGACY_FABRIC_API -> addonList.legacyFabricAPIList
        ModLoader.QUILT -> addonList.quiltList
        ModLoader.QUILT_API -> addonList.quiltAPIList
        ModLoader.CLEANROOM -> addonList.cleanroomList
        else -> null
    }
}

/**
 * 安装动作
 *
 * 与旧安装页的 `GameDownloadViewModel` 逐行对应：同一个 [GameInstaller]、
 * 同一组回调、同样的取消与版本名称重新探测。只有呈现换成了 Oxide 的整块表面。
 */
private class OxideInstallViewModel : ViewModel() {

    var operation by mutableStateOf<OxideInstallOperation>(OxideInstallOperation.None)
        private set

    var installer by mutableStateOf<GameInstaller?>(null)
        private set

    /** 版本名称存在性检查的刷新计数；安装结束后会变一次，用来重新探测 */
    var versionNameCheck by mutableIntStateOf(0)
        private set

    fun warnForNotification(info: GameDownloadInfo) {
        operation = OxideInstallOperation.WarningForNotification(info)
    }

    fun warnForMobileData(info: GameDownloadInfo) {
        operation = OxideInstallOperation.WarningForMobileData(info)
    }

    fun install(
        context: Context,
        info: GameDownloadInfo,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {},
    ) {
        operation = OxideInstallOperation.Installing
        installer = GameInstaller(context, info, viewModelScope).also {
            it.installGame(
                onInstalled = { version ->
                    installer = null
                    VersionsManager.refresh("[OxideInstall] GameInstaller.onInstalled", version)
                    operation = OxideInstallOperation.Succeeded
                    versionNameCheck++
                    onStop()
                },
                onError = { th ->
                    installer = null
                    operation = OxideInstallOperation.Failed(th)
                    versionNameCheck++
                    onStop()
                },
                onGameAlreadyInstalled = {
                    // 刚装完又点了一次安装：重置状态，否则下一次安装发不出去
                    operation = OxideInstallOperation.AlreadyInstalled
                    versionNameCheck++
                    onStop()
                },
            )
        }
        onStart()
    }

    fun cancel() {
        installer?.cancelInstall()
        installer = null
        operation = OxideInstallOperation.None
        versionNameCheck++
    }

    fun reset() {
        operation = OxideInstallOperation.None
    }

    override fun onCleared() {
        cancel()
    }
}

// ---------------------------------------------------------------------------
// 界面
// ---------------------------------------------------------------------------

/**
 * 安装 Minecraft 版本
 *
 * 取代 `DownloadGameScreen` → `SelectGameVersionScreen` → `DownloadGameWithAddonScreen`
 * 这一整条旧链路。三步就是这三件真实的事：选版本、选加载器与 API、命名并安装。
 *
 * 后端一个字都没有改：版本清单仍来自 [MinecraftVersions]，加载器列表仍走既有的
 * `*Versions.fetch*List`，[AddonList] / [CurrentAddon] / `runWithState` 与旧安装页
 * 共用同一份实现，安装仍由同一个 [GameInstaller] 按同一个 [GameDownloadInfo] 执行，
 * 取消也仍然作用在那条任务链上。换掉的只是呈现：Material 卡片与对话框换成
 * Oxide 的面板、分区行与进度条。
 */
@Composable
fun OxideInstallVersionPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val hostActions = LocalOxideHostActions.current
    val eventViewModel = rememberOxideEventViewModel()

    val versionsViewModel: OxideInstallVersionsViewModel =
        viewModel(key = "OxideInstallVersions") { OxideInstallVersionsViewModel() }
    val addonsViewModel: OxideInstallAddonsViewModel =
        viewModel(key = "OxideInstallAddons") { OxideInstallAddonsViewModel() }
    val installViewModel: OxideInstallViewModel =
        viewModel(key = "OxideInstallOperation") { OxideInstallViewModel() }

    // 选中的 Minecraft 版本：它决定后面两步有没有内容，因此是整条流程的根状态
    var gameVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable { mutableStateOf(OxideInstallStep.Version) }
    var activeSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    var nameEditedByUser by rememberSaveable { mutableStateOf(false) }

    val plan = remember(gameVersion) {
        gameVersion?.let { oxideAddonPlan(it) } ?: emptyList()
    }

    // 换版本就重新拉一遍加载器列表；bind 对同一版本是空操作
    LaunchedEffect(gameVersion) {
        gameVersion?.let { addonsViewModel.bind(it) }
    }

    // 列表到齐或选择变化后重算一次不兼容；组合本身不会发起任何请求
    val addon = addonsViewModel.currentAddon
    LaunchedEffect(
        addonsViewModel.bindCount,
        addonsViewModel.addonList.optifineList,
        addonsViewModel.addonList.forgeList,
        addonsViewModel.addonList.neoforgeList,
        addonsViewModel.addonList.fabricList,
        addonsViewModel.addonList.fabricAPIList,
        addonsViewModel.addonList.legacyFabricList,
        addonsViewModel.addonList.legacyFabricAPIList,
        addonsViewModel.addonList.quiltList,
        addonsViewModel.addonList.quiltAPIList,
        addonsViewModel.addonList.cleanroomList,
        addon.optifineState,
        addon.forgeState,
        addon.neoforgeState,
        addon.fabricState,
        addon.fabricAPIState,
        addon.legacyFabricState,
        addon.legacyFabricAPIState,
        addon.quiltState,
        addon.quiltAPIState,
        addon.cleanroomState,
        addon.optifineVersion.value,
        addon.forgeVersion.value,
        addon.neoforgeVersion.value,
        addon.fabricVersion.value,
        addon.fabricAPIVersion.value,
        addon.legacyFabricVersion.value,
        addon.legacyFabricAPIVersion.value,
        addon.quiltVersion.value,
        addon.quiltAPIVersion.value,
        addon.cleanroomVersion.value,
    ) {
        addonsViewModel.reconcile(plan)
    }

    // 版本名：用户改过之后就不再自动改写
    val autoName = remember(
        gameVersion,
        addon.optifineVersion.value,
        addon.forgeVersion.value,
        addon.neoforgeVersion.value,
        addon.fabricVersion.value,
        addon.legacyFabricVersion.value,
        addon.quiltVersion.value,
        addon.cleanroomVersion.value,
    ) {
        val version = gameVersion ?: return@remember ""
        oxideInstallVersionName(
            gameVersion = version,
            optifine = addon.optifineVersion.value,
            forge = addon.forgeVersion.value,
            neoforge = addon.neoforgeVersion.value,
            fabric = addon.fabricVersion.value,
            legacyFabric = addon.legacyFabricVersion.value,
            quilt = addon.quiltVersion.value,
            cleanroom = addon.cleanroomVersion.value,
        )
    }
    var nameValue by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(autoName) {
        if (!nameEditedByUser) nameValue = autoName
    }

    // 装完之后实例真的会出现在磁盘上。清掉选择、退回第一步并把焦点交给实例页，
    // 这些写入都发生在点击回调里，因此不会与重组互相触发
    val finishInstall: () -> Unit = {
        installViewModel.reset()
        addonsViewModel.clearAllSelections()
        nameEditedByUser = false
        gameVersion = null
        step = OxideInstallStep.Version
        hostActions.navigateTo(OxidePage.Instances)
        onDismiss()
    }

    val operation = installViewModel.operation
    val flow = OxideInstallFlowState(
        gameVersion = gameVersion,
        step = step,
        // bind 之前 supports 是 null，因此"加载器列表还没按这个版本拉过"
        // 与"拉过但结果是空"不会混成同一件事
        supportsLoaded = addonsViewModel.supports != null,
        installing = operation is OxideInstallOperation.Installing,
        succeeded = operation is OxideInstallOperation.Succeeded,
    )
    val stepStates = oxideInstallStepStates(flow)
    val nextStep = oxideInstallNextStep(flow)

    // 面板按内容定大小、居中：宽度最多三列（和页面网格同一个尺度），
    // 高度最多"窗口减去四周空当"。清单与详情都在这块高度里滚动，
    // 因此既不会出现铺满内容区的那一大片空白，也不会在 640x360 上把底部动作挤出去。
    OxideDestinationBackdrop(modifier = modifier, onDismiss = onDismiss) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val bounds = oxideInstallPanelBounds(
                windowWidthDp = maxWidth.value,
                windowHeightDp = maxHeight.value,
                cardMinWidthDp = metrics.cardMinWidth.value,
                cardGapDp = metrics.cardGap.value,
            )
            val listMaxHeight = oxideInstallStepListMaxHeight(
                panelMaxHeightDp = bounds.maxHeightDp,
                topBarDp = metrics.topBarHeight.value,
                navItemDp = metrics.navItemHeight.value,
                cardGapDp = metrics.cardGap.value,
            ).dp

            OxideSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = bounds.maxWidthDp.dp)
                    .heightIn(max = bounds.maxHeightDp.dp)
                    // 底板是"点外面关闭"，所以面板自己得先把这片点击吃掉
                    .oxideDestinationPanelInput(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OxideIconButton(
                        onClick = onDismiss,
                        glyph = "←",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_sec_topbar_back)
                        ),
                    )
                    Spacer(Modifier.width(metrics.secRowGap))
                    OxidePageTitle(
                        text = stringResource(R.string.oxide_inst_title),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(metrics.secRowGap))

                OxideInstallStepper(
                    metrics = metrics,
                    titles = OxideInstallStep.entries.map { stringResource(it.titleRes) },
                    states = stepStates,
                    currentIndex = OxideInstallStep.entries.indexOf(step),
                    onSelect = { index ->
                        val target = OxideInstallStep.entries[index]
                        if (oxideInstallStepSelectable(flow, target)) step = target
                    },
                )

                Spacer(Modifier.height(metrics.cardGap))

                // fill = false：内容多高面板就长多高，不多一分
                Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    when (step) {
                        OxideInstallStep.Version -> OxideInstallVersionStep(
                            metrics = metrics,
                            viewModel = versionsViewModel,
                            chosen = gameVersion,
                            onChoose = { version ->
                                gameVersion = version
                                nameEditedByUser = false
                                step = OxideInstallStep.Loader
                            },
                            onOpenLink = hostActions.openLink,
                            listMaxHeight = listMaxHeight,
                        )

                        OxideInstallStep.Loader -> OxideInstallLoaderStep(
                            metrics = metrics,
                            viewModel = addonsViewModel,
                            plan = plan,
                            activeSlotIndex = activeSlotIndex,
                            onOpenSlot = { activeSlotIndex = it },
                            listMaxHeight = listMaxHeight,
                        )

                        OxideInstallStep.Install -> OxideInstallConfirmStep(
                            metrics = metrics,
                            context = context,
                            gameVersion = gameVersion,
                            supports = addonsViewModel.supports,
                            viewModel = addonsViewModel,
                            installViewModel = installViewModel,
                            nameValue = nameValue,
                            nameErrorCheck = installViewModel.versionNameCheck,
                            listMaxHeight = listMaxHeight,
                            onNameChange = {
                                nameValue = it
                                nameEditedByUser = true
                            },
                            onGoVersions = { step = OxideInstallStep.Version },
                            onGoLoaders = { step = OxideInstallStep.Loader },
                            onKeepScreen = eventViewModel::sendKeepScreen,
                            onInstalled = finishInstall,
                        )
                    }
                }

                // 安装进行中：面板里只剩进度块自己的取消，这一行整个收起来，
                // 否则"上一步"会变成一条装到一半还能改选择的假出口
                if (operation !is OxideInstallOperation.Installing) {
                    Spacer(Modifier.height(metrics.cardGap))

                    OxideInstallFooter(
                        metrics = metrics,
                        step = step,
                        onDismiss = onDismiss,
                        onGoVersions = { step = OxideInstallStep.Version },
                        onGoLoaders = { step = OxideInstallStep.Loader },
                        nextStep = nextStep,
                        onGoNext = { nextStep?.let { step = it } },
                    )
                }
            }
        }
    }
}

/**
 * 面板底部那一行动作
 *
 * 每一步都恰好有一组能走的前路：第一、二步是"上一步 / 下一步"，第三步没有下一步
 * （再往前就是安装本身，而不是第四步）。
 * 走不了的那一步保持画出来但禁用——"还没选版本所以到不了下一步"这件事必须看得见，
 * 而不是让按钮凭空消失。
 */
@Composable
private fun OxideInstallFooter(
    metrics: OxideMetrics,
    step: OxideInstallStep,
    onDismiss: () -> Unit,
    onGoVersions: () -> Unit,
    onGoLoaders: () -> Unit,
    nextStep: OxideInstallStep?,
    onGoNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OxideButton(
            text = stringResource(R.string.generic_back),
            onClick = when (step) {
                OxideInstallStep.Version -> onDismiss
                OxideInstallStep.Loader -> onGoVersions
                OxideInstallStep.Install -> onGoLoaders
            },
            tone = OxideButtonTone.Ghost,
        )
        Spacer(Modifier.weight(1f))
        // 走不到下一步时它仍然画在那里，只是禁用：这一步为什么到头了要看得见
        OxideButton(
            text = stringResource(R.string.oxide_inst_next),
            onClick = onGoNext,
            tone = OxideButtonTone.Primary,
            enabled = nextStep != null,
        )
    }
}

// ---------------------------------------------------------------------------
// 第一步：选版本
// ---------------------------------------------------------------------------

/**
 * 「安装」与「修改版本」共用这一个版本选择步骤：两页要读的是同一份版本清单，
 * 用的是同一套类型开关与搜索，因此列表本身也应该只有一份。
 *
 * [listMaxHeight] 是清单能占的上限，装不下时自己在里面滚动。
 * 「修改版本」那一页把高度留给父容器的 `weight`，因此不传——`Dp.Unspecified`
 * 就是"不设上限"，两页共用这一个函数时行为与从前完全一致。
 */
@Composable
internal fun OxideInstallVersionStep(
    metrics: OxideMetrics,
    viewModel: OxideInstallVersionsViewModel,
    chosen: String?,
    onChoose: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    listMaxHeight: Dp = Dp.Unspecified,
) {
    val filter = viewModel.versionFilter

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        ) {
            OxideSecChip(
                label = stringResource(R.string.download_game_type_release),
                selected = filter.release,
                metrics = metrics,
                onClick = { viewModel.filterWith(filter.copy(release = !filter.release)) },
            )
            OxideSecChip(
                label = stringResource(R.string.download_game_type_snapshot),
                selected = filter.snapshot,
                metrics = metrics,
                onClick = { viewModel.filterWith(filter.copy(snapshot = !filter.snapshot)) },
            )
            OxideSecChip(
                label = stringResource(R.string.download_game_type_april_fools),
                selected = filter.aprilFools,
                metrics = metrics,
                onClick = { viewModel.filterWith(filter.copy(aprilFools = !filter.aprilFools)) },
            )
            OxideSecChip(
                label = stringResource(R.string.download_game_type_old),
                selected = filter.old,
                metrics = metrics,
                onClick = { viewModel.filterWith(filter.copy(old = !filter.old)) },
            )
        }

        Spacer(Modifier.height(metrics.cardGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        ) {
            OxideSecInput(
                metrics = metrics,
                modifier = Modifier.weight(1f),
                value = filter.id,
                onValueChange = { viewModel.filterWith(filter.copy(id = it)) },
                placeholder = stringResource(R.string.generic_search),
            )
            OxideIconButton(
                onClick = { viewModel.refresh(true) },
                glyph = "↻",
                modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_refresh)),
            )
        }

        Spacer(Modifier.height(metrics.cardGap))

        when (val state = viewModel.versionState) {
            is OxideVersionState.Loading ->
                OxideLoadingRow(stringResource(R.string.oxide_common_loading))

            is OxideVersionState.Failed -> OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.oxide_inst_versions_failed),
                detail = stringResource(
                    R.string.download_game_failed_to_get_versions,
                    resolveAndroidString(state.message).text,
                ),
                dismissText = stringResource(R.string.generic_refresh),
                onDismiss = { viewModel.refresh(true) },
            )

            is OxideVersionState.Ready -> {
                if (state.versions.isEmpty()) {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_inst_versions_empty),
                        detail = stringResource(R.string.oxide_inst_versions_empty_detail),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = listMaxHeight),
                    ) {
                        items(items = state.versions, key = { it.version.id }) { version ->
                            OxideInstallVersionRow(
                                metrics = metrics,
                                version = version,
                                selected = version.version.id == chosen,
                                onClick = { onChoose(version.version.id) },
                                onOpenLink = onOpenLink,
                            )
                            Spacer(Modifier.height(metrics.secRowGap))
                        }
                    }
                }
            }
        }
    }
}

/** 版本清单的一行：版本号 + 类型 + 真实发布时间 + 百科入口 */
@Composable
private fun OxideInstallVersionRow(
    metrics: OxideMetrics,
    version: MinecraftVersion,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val typeName = stringResource(versionTypeLabelRes(version))
    val summary = version.summary?.let { stringResource(it) }
    val releaseDate = formatDate(
        input = version.version.releaseTime,
        pattern = stringResource(R.string.date_format),
    )
    val wikiUrl = versionWikiUrl(version)

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
                text = version.version.id,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(releaseDate, summary).joinToString(" · "),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(metrics.secRowGap))
        OxideBadge(text = typeName)
        if (wikiUrl != null) {
            Spacer(Modifier.width(metrics.secRowGap))
            OxideIconButton(
                onClick = { onOpenLink(wikiUrl) },
                glyph = "↗",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_inst_version_wiki, version.version.id)
                ),
            )
        }
    }
}

private fun versionTypeLabelRes(version: MinecraftVersion): Int = when (version.type) {
    MinecraftVersion.Type.Release -> R.string.download_game_type_release
    MinecraftVersion.Type.Snapshot -> R.string.download_game_type_snapshot
    MinecraftVersion.Type.AprilFools -> R.string.download_game_type_april_fools
    MinecraftVersion.Type.OldBeta -> R.string.download_game_type_old_beta
    MinecraftVersion.Type.OldAlpha -> R.string.download_game_type_old_alpha
    MinecraftVersion.Type.Unknown -> R.string.generic_unknown
}

/** 百科链接与旧选择页一致：远古版与未知版本没有入口 */
@Composable
private fun versionWikiUrl(version: MinecraftVersion): String? {
    val suffix = version.urlSuffix ?: version.version.id
    return when (version.type) {
        MinecraftVersion.Type.Release -> stringResource(R.string.url_wiki_minecraft_game_release, suffix)
        MinecraftVersion.Type.Snapshot, MinecraftVersion.Type.AprilFools ->
            stringResource(R.string.url_wiki_minecraft_game_snapshot, suffix)

        else -> null
    }
}

// ---------------------------------------------------------------------------
// 第二步：加载器与 API 模组
// ---------------------------------------------------------------------------

@Composable
private fun OxideInstallLoaderStep(
    metrics: OxideMetrics,
    viewModel: OxideInstallAddonsViewModel,
    plan: List<OxideAddonSlot>,
    activeSlotIndex: Int,
    onOpenSlot: (Int) -> Unit,
    listMaxHeight: Dp,
) {
    if (plan.isEmpty()) {
        OxideEmptyState(
            title = stringResource(R.string.oxide_inst_no_version),
            detail = stringResource(R.string.oxide_inst_no_version_detail),
        )
        return
    }

    val activeIndex = activeSlotIndex.coerceIn(0, plan.lastIndex)
    val slot = plan[activeIndex]

    // Fabric 系三组"选了加载器却没选 API"的提醒，判据与旧安装页一致
    val list = viewModel.addonList
    val addon = viewModel.currentAddon
    val apiWarning = remember(
        addon.fabricVersion.value,
        addon.fabricAPIVersion.value,
        addon.fabricAPIState,
        list.fabricAPIList,
        addon.legacyFabricVersion.value,
        addon.legacyFabricAPIVersion.value,
        addon.legacyFabricAPIState,
        list.legacyFabricAPIList,
        addon.quiltVersion.value,
        addon.quiltAPIVersion.value,
        addon.quiltAPIState,
        list.quiltAPIList,
    ) {
        if (
            oxideNeedsApiWarning(
                loaderChosen = addon.fabricVersion.value != null,
                apiChosen = addon.fabricAPIVersion.value != null,
                apiListLoaded = addon.fabricAPIState == AddonState.None,
                apiListEmpty = list.fabricAPIList.isNullOrEmpty(),
            )
        ) {
            ModLoader.FABRIC_API
        } else if (
            oxideNeedsApiWarning(
                loaderChosen = addon.legacyFabricVersion.value != null,
                apiChosen = addon.legacyFabricAPIVersion.value != null,
                apiListLoaded = addon.legacyFabricAPIState == AddonState.None,
                apiListEmpty = list.legacyFabricAPIList.isNullOrEmpty(),
            )
        ) {
            ModLoader.LEGACY_FABRIC_API
        } else if (
            oxideNeedsApiWarning(
                loaderChosen = addon.quiltVersion.value != null,
                apiChosen = addon.quiltAPIVersion.value != null,
                apiListLoaded = addon.quiltAPIState == AddonState.None,
                apiListEmpty = list.quiltAPIList.isNullOrEmpty(),
            )
        ) {
            ModLoader.QUILT_API
        } else {
            null
        }
    }

    // 直接算而不是 remember：选择本身就是被订阅的状态，缓存下来反而会读到旧值
    val selectedCount = plan.count { viewModel.selectedOf(it) != null }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 面板最多三列宽，因此两栏布局只在真的放得下时才用，
        // 放不下就折成"横向标签 + 下方详情"
        val sideBySide = maxWidth >= metrics.cardMinWidth * 1.15f

        Column(modifier = Modifier.fillMaxWidth()) {
            apiWarning?.let { warning ->
                OxideSecErrorRow(
                    metrics = metrics,
                    title = stringResource(R.string.generic_warning),
                    detail = stringResource(
                        R.string.download_game_addon_warning_api,
                        warning.displayName,
                    ),
                    dismissText = stringResource(R.string.oxide_inst_addon_api_action),
                    onDismiss = {
                        onOpenSlot(plan.indexOfFirst { it.loader == warning }.coerceAtLeast(0))
                    },
                )
                Spacer(Modifier.height(metrics.cardGap))
            }

            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideInstallSlotList(
                        metrics = metrics,
                        viewModel = viewModel,
                        plan = plan,
                        activeIndex = activeIndex,
                        selectedCount = selectedCount,
                        onSelect = onOpenSlot,
                        modifier = Modifier
                            .width(metrics.cardMinWidth)
                            .heightIn(max = listMaxHeight),
                    )
                    OxideInstallSlotDetail(
                        metrics = metrics,
                        viewModel = viewModel,
                        slot = slot,
                        listMaxHeight = listMaxHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OxideInstallSlotChips(
                    metrics = metrics,
                    plan = plan,
                    viewModel = viewModel,
                    activeIndex = activeIndex,
                    onSelect = onOpenSlot,
                )
                Spacer(Modifier.height(metrics.cardGap))
                OxideInstallSlotDetail(
                    metrics = metrics,
                    viewModel = viewModel,
                    slot = slot,
                    listMaxHeight = listMaxHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 左侧：加载器清单 */
@Composable
private fun OxideInstallSlotList(
    metrics: OxideMetrics,
    viewModel: OxideInstallAddonsViewModel,
    plan: List<OxideAddonSlot>,
    activeIndex: Int,
    selectedCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        // 加载器多的时候这一列会比屏幕高，因此自己滚动而不是被裁掉
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_inst_slot_list),
            trailing = {
                OxideBadge(text = stringResource(R.string.oxide_inst_slot_count, selectedCount))
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))
        plan.forEachIndexed { index, slot ->
            OxideInstallSlotRow(
                metrics = metrics,
                viewModel = viewModel,
                slot = slot,
                selected = index == activeIndex,
                onClick = { onSelect(index) },
            )
            Spacer(Modifier.height(metrics.secRowGap))
        }
    }
}

/** 窄屏：顶部横向加载器标签 */
@Composable
private fun OxideInstallSlotChips(
    metrics: OxideMetrics,
    plan: List<OxideAddonSlot>,
    viewModel: OxideInstallAddonsViewModel,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
    ) {
        plan.forEachIndexed { index, slot ->
            val picked = viewModel.selectedOf(slot)
            OxideSecChip(
                label = if (picked == null) {
                    slot.loader.displayName
                } else {
                    "${slot.loader.displayName} · ${oxideAddonTitle(slot, picked)}"
                },
                selected = index == activeIndex,
                metrics = metrics,
                onClick = { onSelect(index) },
            )
        }
    }
}

/** 加载器清单的一行：名称 + 这一项的真实状态 */
@Composable
private fun OxideInstallSlotRow(
    metrics: OxideMetrics,
    viewModel: OxideInstallAddonsViewModel,
    slot: OxideAddonSlot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val addon = viewModel.currentAddon
    val state = viewModel.stateOf(slot)
    val picked = viewModel.selectedOf(slot)
    val versions = viewModel.versionsOf(slot)
    val blocked = slot.loader in viewModel.blockedLoaders()

    val status: String = when {
        state is AddonState.Loading -> stringResource(R.string.oxide_common_loading)
        blocked -> stringResource(
            R.string.download_game_addon_incompatible_with,
            incompatibleNames(addon, slot),
        )

        state is AddonState.Error -> stringResource(
            R.string.download_game_addon_list_load_error,
            resolveAndroidString(state.message).text,
        )

        picked != null -> stringResource(
            R.string.settings_element_selected,
            oxideAddonTitle(slot, picked),
        )

        // API 模组必须先有加载器，这句提示与旧列表的"请先选择 xxx"一致
        slot.apiOf != null && viewModel.selectedOf(OxideAddonSlot(slot.apiOf)) == null ->
            stringResource(R.string.download_game_addon_request_addon, slot.apiOf.displayName)

        versions.isNullOrEmpty() -> stringResource(R.string.download_game_addon_unavailable)
        slot.loader == ModLoader.FORGE && forgeHasUninstallableEntries(viewModel.addonList.forgeList) ->
            stringResource(R.string.download_game_addon_not_installable)

        else -> stringResource(R.string.download_game_addon_available)
    }

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
                role = Role.Tab,
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
                text = slot.loader.displayName,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (picked != null && !blocked) {
            Spacer(Modifier.width(metrics.secRowGap))
            OxideIconButton(
                onClick = { viewModel.clear(slot) },
                glyph = "✕",
                size = metrics.secControlHeight * 0.8f,
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_inst_clear_addon, slot.loader.displayName)
                ),
            )
        }
    }
}

/** 与当前选择冲突的那些加载器名 */
@Composable
private fun incompatibleNames(addon: CurrentAddon, slot: OxideAddonSlot): String {
    val names: Set<ModLoader> = when (slot.loader) {
        ModLoader.OPTIFINE -> addon.incompatibleWithOptiFine.value
        ModLoader.FORGE -> addon.incompatibleWithForge.value
        ModLoader.NEOFORGE -> addon.incompatibleWithNeoForge.value
        ModLoader.FABRIC -> addon.incompatibleWithFabric.value
        ModLoader.FABRIC_API -> addon.incompatibleWithFabricAPI.value
        ModLoader.LEGACY_FABRIC -> addon.incompatibleWithLegacyFabric.value
        ModLoader.LEGACY_FABRIC_API -> addon.incompatibleWithLegacyFabricAPI.value
        ModLoader.QUILT -> addon.incompatibleWithQuilt.value
        ModLoader.QUILT_API -> addon.incompatibleWithQuiltAPI.value
        ModLoader.CLEANROOM -> addon.incompatibleWithCleanroom.value
        else -> emptySet()
    }
    return names.joinToString(", ") { it.displayName }
}

/** 右侧：所选加载器的真实版本列表 */
@Composable
private fun OxideInstallSlotDetail(
    metrics: OxideMetrics,
    viewModel: OxideInstallAddonsViewModel,
    slot: OxideAddonSlot,
    listMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addon = viewModel.currentAddon
    val state = viewModel.stateOf(slot)
    val picked = viewModel.selectedOf(slot)
    val versions = viewModel.versionsOf(slot)
    val blocked = slot.loader in viewModel.blockedLoaders()
    val pickedKey = picked?.let { oxideAddonKey(slot, it) }

    val options = remember(versions, pickedKey) { oxideAddonOptions(slot, versions, context) }

    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(
            text = slot.loader.displayName,
            trailing = {
                when {
                    state is AddonState.Error -> OxideButton(
                        text = stringResource(R.string.generic_refresh),
                        onClick = { viewModel.reload(slot) },
                        tone = OxideButtonTone.Secondary,
                    )

                    picked != null && !blocked -> OxideIconButton(
                        onClick = { viewModel.clear(slot) },
                        glyph = "✕",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_inst_clear_addon, slot.loader.displayName)
                        ),
                    )

                    else -> Unit
                }
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))

        when {
            state is AddonState.Loading ->
                OxideLoadingRow(stringResource(R.string.oxide_common_loading))

            blocked -> OxideEmptyState(
                title = stringResource(
                    R.string.download_game_addon_incompatible_with,
                    incompatibleNames(addon, slot),
                ),
            )

            state is AddonState.Error -> OxideSecErrorRow(
                metrics = metrics,
                title = slot.loader.displayName,
                detail = stringResource(
                    R.string.download_game_addon_list_load_error,
                    resolveAndroidString(state.message).text,
                ),
                dismissText = stringResource(R.string.generic_refresh),
                onDismiss = { viewModel.reload(slot) },
            )

            versions.isNullOrEmpty() -> OxideEmptyState(
                title = stringResource(R.string.download_game_addon_unavailable),
                detail = stringResource(R.string.oxide_inst_addon_unavailable_detail),
            )

            slot.apiOf != null && viewModel.selectedOf(OxideAddonSlot(slot.apiOf)) == null ->
                OxideEmptyState(
                    title = stringResource(
                        R.string.download_game_addon_request_addon,
                        slot.apiOf.displayName,
                    ),
                    detail = stringResource(R.string.oxide_inst_addon_api_detail),
                )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
            ) {
                items(items = options, key = { it.key }) { option ->
                    if (option.selectable) {
                        OxideSecPickerRow(
                            label = option.title,
                            hint = option.detail,
                            selected = option.key == pickedKey,
                            onClick = {
                                viewModel.select(
                                    slot,
                                    if (option.key == pickedKey) null else option.version,
                                )
                            },
                        )
                    } else {
                        // 装不了的版本仍然如实列出来，只是不给选
                        OxideSettingRow(
                            label = option.title,
                            hint = option.detail,
                            enabled = false,
                        )
                    }
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
    }
}

/**
 * 一个加载器版本在列表里的一行
 *
 * 「安装」与「修改版本」共用：两页列的是同一批真实版本对象，
 * 标题与详情也取自同样的字段，因此摊平逻辑只写一份。
 */
internal data class OxideAddonOption(
    val key: String,
    val title: String,
    val detail: String?,
    val version: AddonVersion,
    /** universal / client 这类无法自动安装的 Forge 版本不允许选 */
    val selectable: Boolean = true,
)

/**
 * 把某一类的加载器版本摊平成可渲染的行
 *
 * 标题与详情都取自真实版本对象（发布时间、是否推荐、是否预览、所需 Forge…），
 * 不读磁盘也不发请求，因此可以放在组合期算一次。
 */
internal fun oxideAddonOptions(
    slot: OxideAddonSlot,
    versions: List<AddonVersion>?,
    context: Context,
): List<OxideAddonOption> {
    val release = context.getString(R.string.download_game_addon_release)
    val preview = context.getString(R.string.download_game_addon_preview)
    val debug = context.getString(R.string.download_game_addon_debug)
    val stable = context.getString(R.string.download_game_addon_stable)

    return when (slot.loader) {
        ModLoader.OPTIFINE -> versions.orEmpty().mapNotNull { version ->
            val optifine = version as? OptiFineVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = optifine.fileName,
                title = optifine.displayName,
                detail = buildList {
                    add(if (optifine.isPreview) preview else release)
                    optifine.releaseDate.takeIf { it.isNotEmpty() }?.let { add(it) }
                    optifine.forgeVersion?.let { required ->
                        add(
                            context.getString(
                                R.string.download_game_addon_compatible_with,
                                "${ModLoader.FORGE.displayName} $required",
                            )
                        )
                    }
                }.joinToString(" · "),
                version = optifine,
            )
        }

        ModLoader.FORGE -> versions.orEmpty().mapNotNull { version ->
            val forge = version as? ForgeVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = forge.versionName,
                title = forge.versionName,
                detail = buildList {
                    if (forge.isRecommended) {
                        add(
                            context.getString(
                                R.string.download_game_addon_recommended,
                                ModLoader.FORGE.displayName,
                            )
                        )
                    }
                    add(forge.releaseTime)
                }.joinToString(" · "),
                version = forge,
                selectable = !forgeNeedsManualInstall(forge),
            )
        }

        ModLoader.NEOFORGE -> versions.orEmpty().mapNotNull { version ->
            val neoforge = version as? NeoForgeVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = neoforge.versionName,
                title = neoforge.versionName,
                detail = if (neoforge.isBeta) debug else release,
                version = neoforge,
            )
        }

        ModLoader.FABRIC -> versions.orEmpty().mapNotNull { version ->
            val fabric = version as? FabricVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = fabric.version,
                title = fabric.version,
                detail = fabricLikeDetail(fabric, stable, debug),
                version = fabric,
            )
        }

        ModLoader.QUILT -> versions.orEmpty().mapNotNull { version ->
            val quilt = version as? QuiltVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = quilt.version,
                title = quilt.version,
                detail = fabricLikeDetail(quilt, stable, debug),
                version = quilt,
            )
        }

        ModLoader.LEGACY_FABRIC -> versions.orEmpty().mapNotNull { version ->
            val legacy = version as? LegacyFabricVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = legacy.version,
                title = legacy.version,
                detail = fabricLikeDetail(legacy, stable, debug),
                version = legacy,
            )
        }

        ModLoader.FABRIC_API, ModLoader.LEGACY_FABRIC_API, ModLoader.QUILT_API ->
            versions.orEmpty().mapNotNull { version ->
                val mod = version as? ModVersion ?: return@mapNotNull null
                OxideAddonOption(
                    key = mod.version.versionNumber,
                    title = mod.displayName,
                    detail = getTimeAgo(context = context, dateString = mod.version.datePublished),
                    version = mod,
                )
            }

        ModLoader.CLEANROOM -> versions.orEmpty().mapNotNull { version ->
            val cleanroom = version as? CleanroomVersion ?: return@mapNotNull null
            OxideAddonOption(
                key = cleanroom.version,
                title = cleanroom.version,
                detail = getTimeAgo(context = context, pastInstant = cleanroom.createdAt),
                version = cleanroom,
            )
        }

        else -> emptyList()
    }
}

private fun fabricLikeDetail(version: FabricLikeVersion, stable: String, debug: String): String =
    if (version.stable) stable else debug

/** 选中态在列表里的身份：与 [oxideAddonOptions] 用同一套取值，清单栏与详情栏因此说同一件事 */
internal fun oxideAddonKey(slot: OxideAddonSlot, version: AddonVersion): String = when (slot.loader) {
    ModLoader.OPTIFINE -> (version as? OptiFineVersion)?.fileName.orEmpty()
    ModLoader.FORGE -> (version as? ForgeVersion)?.versionName.orEmpty()
    ModLoader.NEOFORGE -> (version as? NeoForgeVersion)?.versionName.orEmpty()
    ModLoader.FABRIC -> (version as? FabricVersion)?.version.orEmpty()
    ModLoader.QUILT -> (version as? QuiltVersion)?.version.orEmpty()
    ModLoader.LEGACY_FABRIC -> (version as? LegacyFabricVersion)?.version.orEmpty()
    ModLoader.CLEANROOM -> (version as? CleanroomVersion)?.version.orEmpty()
    else -> (version as? ModVersion)?.version?.versionNumber.orEmpty()
}

/** 行标题。「修改版本」那一页显示"当前装的是哪一版"时用的是同一份取值 */
internal fun oxideAddonTitle(slot: OxideAddonSlot, version: AddonVersion): String = when (slot.loader) {
    ModLoader.OPTIFINE -> (version as? OptiFineVersion)?.displayName.orEmpty()
    ModLoader.FORGE -> (version as? ForgeVersion)?.versionName.orEmpty()
    ModLoader.NEOFORGE -> (version as? NeoForgeVersion)?.versionName.orEmpty()
    ModLoader.FABRIC -> (version as? FabricVersion)?.version.orEmpty()
    ModLoader.QUILT -> (version as? QuiltVersion)?.version.orEmpty()
    ModLoader.LEGACY_FABRIC -> (version as? LegacyFabricVersion)?.version.orEmpty()
    ModLoader.CLEANROOM -> (version as? CleanroomVersion)?.version.orEmpty()
    else -> (version as? ModVersion)?.displayName.orEmpty()
}

// ---------------------------------------------------------------------------
// 第三步：命名、安装、进度与取消
// ---------------------------------------------------------------------------

@Composable
private fun OxideInstallConfirmStep(
    metrics: OxideMetrics,
    context: Context,
    gameVersion: String?,
    supports: LoaderVerSupports?,
    viewModel: OxideInstallAddonsViewModel,
    installViewModel: OxideInstallViewModel,
    nameValue: String,
    nameErrorCheck: Int,
    listMaxHeight: Dp,
    onNameChange: (String) -> Unit,
    onGoVersions: () -> Unit,
    onGoLoaders: () -> Unit,
    onKeepScreen: (Boolean) -> Unit,
    onInstalled: () -> Unit,
) {
    val version = gameVersion ?: return OxideEmptyState(
        title = stringResource(R.string.oxide_inst_no_version),
        detail = stringResource(R.string.oxide_inst_no_version_detail),
    )
    val support = supports ?: return OxideEmptyState(
        title = stringResource(R.string.oxide_inst_loading_loaders),
    )

    // 文件名非法字符的检查是纯字符串判定，不碰磁盘，因此留在组合期没有代价
    val filenameInvalidMessage = isFilenameInvalid(nameValue)

    // 版本是否已存在要读磁盘，因此放到 IO 上探测，组合期只读结果
    val existsProbe by produceState<Boolean?>(initialValue = null, nameValue, nameErrorCheck) {
        value = if (nameValue.isEmpty()) {
            false
        } else {
            withContext(Dispatchers.IO) { VersionsManager.isVersionExists(nameValue, true) }
        }
    }

    val isError = nameValue.isEmpty() || filenameInvalidMessage != null || existsProbe == true
    val errorMessage = when {
        filenameInvalidMessage != null -> filenameInvalidMessage
        existsProbe == true -> stringResource(R.string.versions_manage_install_exists)
        nameValue.isEmpty() -> stringResource(R.string.generic_cannot_empty)
        else -> null
    }

    // 探测结果还没回来之前一律当作不可用：宁可按钮晚一会儿亮，
    // 也不要让用户先按下安装、再被告知这个名字已经被占了
    val usable = !isError && existsProbe != null

    val operation = installViewModel.operation
    val installer = installViewModel.installer
    val taskFlow = installer?.tasksFlow
    val tasks: List<TitledTask> =
        if (taskFlow != null) taskFlow.collectAsStateWithLifecycle().value else emptyList()
    val canInstall = usable && operation is OxideInstallOperation.None

    val startInstall: (GameDownloadInfo) -> Unit = { info ->
        installViewModel.install(
            context = context,
            info = info,
            onStart = { onKeepScreen(true) },
            onStop = { onKeepScreen(false) },
        )
    }

    // 通知权限：与旧安装页一致，授权或忽略都继续安装
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val pending = installViewModel.operation
        if (pending is OxideInstallOperation.WarningForNotification) startInstall(pending.info)
    }

    // 把当前选择组装成安装请求，并按与旧安装页相同的顺序过两道提醒
    // （通知权限、流量）——重试走的是同一条路，因此不会绕过任何一道提醒
    val requestInstall: () -> Unit = request@{
        // 不是待安装状态就拒绝这次安装
        if (installViewModel.operation !is OxideInstallOperation.None) return@request
        val info = oxideGameDownloadInfo(
            gameVersion = version,
            customVersionName = nameValue,
            supports = support,
            current = viewModel.currentAddon,
        )
        if (!NotificationManager.checkNotificationEnabled(context)) {
            installViewModel.warnForNotification(info)
        } else if (isUsingMobileData(context)) {
            installViewModel.warnForMobileData(info)
        } else {
            startInstall(info)
        }
    }

    // 安装期间这一整块换成进度面板：确认表单、摘要与动作行都收起来，
    // 面板里只剩"现在在干什么、干到多少、怎么停"
    if (operation is OxideInstallOperation.Installing && installer != null) {
        OxideInstallProgressPanel(
            metrics = metrics,
            tasks = tasks,
            listMaxHeight = listMaxHeight,
            onCancel = {
                installViewModel.cancel()
                onKeepScreen(false)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = listMaxHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSettingRow(
                    label = stringResource(R.string.oxide_inst_chosen_version),
                    value = version,
                    onClick = onGoVersions,
                    trailing = {
                        Text(
                            text = stringResource(R.string.oxide_inst_edit_version_short),
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 1,
                        )
                    },
                )
                OxideSecInput(
                    metrics = metrics,
                    label = stringResource(R.string.download_game_version_name),
                    value = nameValue,
                    onValueChange = onNameChange,
                    placeholder = stringResource(R.string.download_game_version_name),
                    isError = isError,
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Oxide.FgStrong,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(text = stringResource(R.string.oxide_inst_summary))
                val plan = remember(version) { oxideAddonPlan(version) }
                plan.forEach { slot ->
                    val picked = viewModel.selectedOf(slot) ?: return@forEach
                    OxideSettingRow(
                        label = slot.loader.displayName,
                        value = oxideAddonTitle(slot, picked),
                        onClick = onGoLoaders,
                    )
                }
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        // 确认与失败都在原地说出来，不用会盖住整块内容的弹窗
        when (operation) {
            is OxideInstallOperation.WarningForNotification -> OxideSecConfirmBar(
                metrics = metrics,
                text = stringResource(R.string.notification_data_jvm_service_message),
                confirmText = stringResource(R.string.notification_request),
                dismissText = stringResource(R.string.generic_anyway),
                onConfirm = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        NotificationManager.openNotificationSettings(context)
                        startInstall(operation.info)
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = { startInstall(operation.info) },
            )

            is OxideInstallOperation.WarningForMobileData -> OxideSecConfirmBar(
                metrics = metrics,
                text = stringResource(R.string.download_install_warning_mobile_data),
                confirmText = stringResource(R.string.generic_anyway),
                dismissText = stringResource(R.string.generic_cancel),
                onConfirm = { startInstall(operation.info) },
                onDismiss = { installViewModel.reset() },
            )

            is OxideInstallOperation.Failed -> OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.download_install_error_title),
                detail = installErrorDetail(operation.th),
                dismissText = stringResource(R.string.oxide_inst_retry),
                // 重试走的是同一条装配 + 提醒的路，因此不是"直接再跑一遍"
                onDismiss = {
                    installViewModel.reset()
                    requestInstall()
                },
            )

            is OxideInstallOperation.AlreadyInstalled -> OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.download_install_error_title),
                detail = stringResource(R.string.versions_manage_install_exists),
                dismissText = stringResource(R.string.generic_confirm),
                onDismiss = { installViewModel.reset() },
            )

            is OxideInstallOperation.Succeeded -> OxideSecConfirmBar(
                metrics = metrics,
                text = stringResource(R.string.download_install_success_message),
                confirmText = stringResource(R.string.generic_done),
                dismissText = stringResource(R.string.generic_close),
                onConfirm = onInstalled,
                onDismiss = onInstalled,
            )

            else -> Unit
        }

        Spacer(Modifier.height(metrics.cardGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OxideButton(
                text = stringResource(R.string.download_install),
                tone = OxideButtonTone.Primary,
                enabled = canInstall,
                onClick = requestInstall,
            )
        }
    }
}

/** 与旧安装页逐项一致的错误翻译 */
@Composable
private fun installErrorDetail(th: Throwable): String = when (th) {
    is HttpRequestTimeoutException, is SocketTimeoutException, is TimeoutException ->
        stringResource(R.string.error_timeout)

    is UnknownHostException, is UnresolvedAddressException ->
        stringResource(R.string.error_network_unreachable)

    is ConnectException -> stringResource(R.string.error_connection_failed)
    is SerializationException, is JsonSyntaxException -> stringResource(R.string.error_parse_failed)
    is CantFetchingOptiFineUrlException ->
        stringResource(R.string.download_install_error_cant_fetch_optifine_download_url)

    is OptiFineForge17IncompatibleException ->
        stringResource(R.string.download_install_error_optifine_forge17_incompatible, th.buildof)

    is JvmCrashException -> stringResource(R.string.download_install_error_jvm_crash, th.code)
    is DownloadFailedException -> stringResource(R.string.download_install_error_download_failed)
    else -> when {
        th.isProcessStartRefused() -> stringResource(R.string.download_install_error_process_start)
        else -> th.localizedMessage ?: th.message ?: th::class.qualifiedName ?: "Unknown error"
    }
}

/** 版本清单失败时的错误翻译，与旧选择页一致 */
private fun installRequestErrorText(e: Throwable): AndroidStringText = when (e) {
    is HttpRequestTimeoutException -> androidText(R.string.error_timeout)
    is UnknownHostException, is UnresolvedAddressException ->
        androidText(R.string.error_network_unreachable)

    is ConnectException -> androidText(R.string.error_connection_failed)
    is ResponseException -> e.toLocal()
    else -> {
        Logger.error(INSTALL_TAG, "An unknown exception was caught!", e)
        androidText(e.localizedMessage ?: e.message ?: e::class.qualifiedName ?: "Unknown error")
    }
}

// ---------------------------------------------------------------------------
// 任务流面板：安装与导出共用
//
// 读的是 [TitledTask] 与 Task 自己的 StateFlow（阶段、进度、消息、速率），
// 画法与旧 TitleTaskFlowDialog 一样，只是换成 Oxide 的行与进度条。
// ---------------------------------------------------------------------------

/** 一条真实任务；进度为负代表不确定，这时不给假的百分比，也不画进度条 */
@Composable
private fun OxideTaskRow(
    metrics: OxideMetrics,
    task: TitledTask,
    stages: SnapshotStateMap<String, TaskStage>,
) {
    // resolveAndroidString 本身是 @Composable，不能放进 remember 的计算块里
    val title = resolveAndroidString(task.title).text
    val stage = stages[task.task.id] ?: TaskStage.PREPARING
    val progress by task.task.progress.collectAsStateWithLifecycle()
    val message by task.task.message.collectAsStateWithLifecycle()
    val rate by task.task.rateBytesPerSec.collectAsStateWithLifecycle()

    val stageText = when (stage) {
        TaskStage.PREPARING -> stringResource(R.string.oxide_common_loading)
        TaskStage.RUNNING -> stringResource(R.string.oxide_sec_launch_indeterminate)
        TaskStage.COMPLETED -> stringResource(R.string.generic_done)
    }
    val messageText = message?.let { resolveAndroidString(it).text }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.secRowGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (stage == TaskStage.COMPLETED) Oxide.FgMuted else Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(metrics.secRowGap))
            Text(
                text = if (progress >= 0f) {
                    stringResource(
                        R.string.oxide_sec_launch_progress_percent,
                        (progress * 100).toInt(),
                    )
                } else {
                    stageText
                },
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
        if (messageText.isNotEmpty()) {
            Text(
                text = messageText,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress >= 0f) {
            Spacer(Modifier.height(metrics.secRowGap))
            OxideProgressBar(progress = progress)
        }
        val rateBytes = rate
        if (rateBytes != null && rateBytes > 0L) {
            Text(
                text = stringResource(R.string.oxide_sec_launch_rate, formatFileSize(rateBytes)),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
    }
}

/** 收集每个任务的当前阶段；任务列表变化时协程一起被取消 */
@Composable
private fun rememberTaskStages(tasks: List<TitledTask>): SnapshotStateMap<String, TaskStage> {
    val stages = remember { mutableStateMapOf<String, TaskStage>() }
    LaunchedEffect(tasks) {
        stages.keys.toList().filter { key -> tasks.none { it.task.id == key } }
            .forEach { key -> stages.remove(key) }
        tasks.forEach { task ->
            launch {
                task.task.stage.collect { stage -> stages[task.task.id] = stage }
            }
        }
    }
    return stages
}

/**
 * 任务流面板：真实阶段 + 日志 + 取消
 *
 * [logOutput] 非空时额外把 JVM 的实时输出列出来，这正是旧安装弹窗
 * `TitleTaskFlowDialog(logOutput = …)` 提供的东西。
 */
@Composable
internal fun OxideTaskFlowPanel(
    metrics: OxideMetrics,
    title: String,
    tasks: List<TitledTask>,
    logOutput: TaskLogOutput?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stages = rememberTaskStages(tasks)
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(tasks.size) {
        if (tasks.isNotEmpty()) listState.animateScrollToItem(tasks.lastIndex)
    }

    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(text = title)
        Spacer(Modifier.height(metrics.secRowGap))
        if (tasks.isEmpty()) {
            OxideLoadingRow(stringResource(R.string.oxide_sec_launch_waiting))
            return@OxideSurface
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= metrics.cardMinWidth) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideTaskList(
                        metrics = metrics,
                        tasks = tasks,
                        stages = stages,
                        listState = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    OxideTaskLog(metrics = metrics, logOutput = logOutput)
                }
            } else {
                OxideTaskList(
                    metrics = metrics,
                    tasks = tasks,
                    stages = stages,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                )
            }
        }

        Spacer(Modifier.height(metrics.cardGap))
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onCancel,
            tone = OxideButtonTone.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OxideTaskList(
    metrics: OxideMetrics,
    tasks: List<TitledTask>,
    stages: SnapshotStateMap<String, TaskStage>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(items = tasks, key = { task -> task.task.id }) { task ->
            OxideTaskRow(metrics = metrics, task = task, stages = stages)
        }
    }
}

/** 安装期 JVM 的实时输出；没有会话时不占地方 */
@Composable
private fun OxideTaskLog(
    metrics: OxideMetrics,
    logOutput: TaskLogOutput?,
    modifier: Modifier = Modifier,
) {
    if (logOutput == null) return
    val lines by logOutput.lines.collectAsStateWithLifecycle()
    if (lines.isEmpty()) return

    Box(
        modifier = modifier
            .width(metrics.cardMinWidth)
            .fillMaxHeight()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .padding(metrics.secRowGap),
    ) {
        // 日志行可能重复，因此不拿内容当 key
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { _, line ->
                Text(
                    text = line,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Mono.fontSize,
                    lineHeight = Oxide.Type.Mono.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 两块流程共用的步骤轨道
// ---------------------------------------------------------------------------

/**
 * 步骤轨道 + 面板
 *
 * 与 [OxideSettingsPage] 的"分类栏 + 面板"同一套结构：宽度够就左右并列，
 * 不够就把步骤折成顶部一条横向标签、面板占满剩余高度，
 * 因此 640x360 到 1920x1080 都不会裁切或重叠。所有尺寸都来自 [OxideMetrics]。
 */
@Composable
internal fun OxideStepLayout(
    metrics: OxideMetrics,
    titles: List<String>,
    currentIndex: Int,
    reachable: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= metrics.cardMinWidth * 1.15f

        Column(modifier = Modifier.fillMaxSize()) {
            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideStepRail(
                        metrics = metrics,
                        titles = titles,
                        currentIndex = currentIndex,
                        reachable = reachable,
                        onSelect = onSelect,
                        modifier = Modifier
                            .width((150.dp * metrics.guiScale).coerceIn(120.dp, 240.dp))
                            .fillMaxHeight(),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { panel() }
                }
            } else {
                OxideStepChips(
                    metrics = metrics,
                    titles = titles,
                    currentIndex = currentIndex,
                    reachable = reachable,
                    onSelect = onSelect,
                )
                Spacer(Modifier.height(metrics.cardGap))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { panel() }
            }
        }
    }
}

/** 宽屏：左侧竖排步骤 */
@Composable
private fun OxideStepRail(
    metrics: OxideMetrics,
    titles: List<String>,
    currentIndex: Int,
    reachable: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        titles.forEachIndexed { index, title ->
            OxideStepItem(
                metrics = metrics,
                title = title,
                index = index,
                selected = index == currentIndex,
                enabled = reachable(index),
                onClick = { onSelect(index) },
            )
        }
    }
}

/** 窄屏：顶部横向步骤标签 */
@Composable
private fun OxideStepChips(
    metrics: OxideMetrics,
    titles: List<String>,
    currentIndex: Int,
    reachable: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = metrics.pagePaddingH),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        titles.forEachIndexed { index, title ->
            OxideSecChip(
                label = title,
                selected = index == currentIndex,
                metrics = metrics,
                onClick = { if (reachable(index)) onSelect(index) },
            )
        }
    }
}

/**
 * 步骤轨上的一项
 *
 * 选中态既给 `Role.Tab` 也给 selected，朗读时能知道现在在哪一步；
 * 不可达的步骤保留在轨道上但置灰，因为"还没有版本所以不能选加载器"
 * 这件事必须看得见，而不是干脆不出现。
 */
@Composable
private fun OxideStepItem(
    metrics: OxideMetrics,
    title: String,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.categoryTabHeight)
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = metrics.rowGap * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (selected) Oxide.FgNumActive else Oxide.FgNum,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
        Spacer(Modifier.width(metrics.rowGap))
        Text(
            text = title,
            color = when {
                selected -> Oxide.Fg
                enabled -> Oxide.FgGhost
                else -> Oxide.FgFaint
            },
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}