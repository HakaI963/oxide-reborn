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
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonSyntaxException
import dev.oxide.launcher.R
import dev.oxide.launcher.context.copyLocalFile
import dev.oxide.launcher.contract.MediaPickerContract
import dev.oxide.launcher.game.addons.modloader.AddonVersion
import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.addons.modloader.cleanroom.CleanroomVersion
import dev.oxide.launcher.game.addons.modloader.cleanroom.CleanroomVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersions
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersions
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersions
import dev.oxide.launcher.game.download.game.GameDownloadInfo
import dev.oxide.launcher.game.download.game.optifine.CantFetchingOptiFineUrlException
import dev.oxide.launcher.game.download.game.optifine.OptiFineForge17IncompatibleException
import dev.oxide.launcher.game.download.jvm_server.JvmCrashException
import dev.oxide.launcher.game.download.jvm_server.isProcessStartRefused
import dev.oxide.launcher.game.version.download.DownloadFailedException
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionInfo
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.game.download.game.models.LaunchFor
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
import dev.oxide.launcher.utils.GSON
import dev.oxide.launcher.utils.image.isImageFile
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.ModifyDiffs
import dev.oxide.launcher.viewmodel.ModifyOperation
import dev.oxide.launcher.viewmodel.ModifyPayload
import dev.oxide.launcher.viewmodel.ModifyVersionViewModel
import dev.oxide.launcher.viewmodel.sendKeepScreen
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException

private const val MODIFY_TAG = "OxideModifyVersionPage"

// ---------------------------------------------------------------------------
// 纯逻辑
//
// 这一段里没有 Compose、没有 Android，也没有磁盘，因此可以直接单测：
// 哪些加载器会参与修改、变更内容怎么算、改名放不放行、步骤可不可达、
// 以及最终组装出去的安装请求是不是逐字段等价于旧向导。
// ---------------------------------------------------------------------------

/** 一个已安装的加载器：与 [VersionInfo.LoaderInfo] 同样的两元信息，但不带 Parcelable */
internal data class OxideModLoader(
    val loader: ModLoader,
    val version: String,
)

/**
 * 某个加载器在这一页上的选择状态
 *
 * [matched] 相当于旧向导的 `attemptedPreselects`：[attached] 的版本列表到齐之后
 * 是否真的尝试过为它预选。[unmatched] 是 `unmatchedLoaders`，即装着的这一版
 * 在列表里找不到对应物、必须原样保留。
 */
internal data class OxideModSelection(
    val version: String? = null,
    val matched: Boolean = false,
    val unmatched: Boolean = false,
)

/** 算变更内容所需的那一份输入 */
internal data class OxideModifyState(
    val originalGameVersion: String,
    val targetGameVersion: String,
    val installed: List<OxideModLoader>,
    val selections: Map<ModLoader, OxideModSelection>,
)

/**
 * 修改流程里会出现哪些加载器
 *
 * 与旧向导逐项一致：只有加载器本体，**没有** API 模组——旧向导的修改请求里
 * 一个 API 字段都没写，因此这里也把安装页的 [oxideAddonPlan] 里的 API 项去掉。
 */
internal fun oxideModPlan(mcVer: String): List<OxideAddonSlot> =
    oxideAddonPlan(mcVer).filter { slot -> slot.apiOf == null }

/** 参与变更判定的加载器，顺序与旧向导那一份固定列表一致 */
internal val oxideModLoaderOrder: List<ModLoader> = listOf(
    ModLoader.OPTIFINE, ModLoader.FORGE, ModLoader.NEOFORGE,
    ModLoader.FABRIC, ModLoader.LEGACY_FABRIC, ModLoader.QUILT, ModLoader.CLEANROOM,
)

/**
 * 会为哪些加载器拉取版本列表
 *
 * 与旧 `reloadableLoaders` 等价：OptiFine 与 Forge 永远要，其余按目标 Minecraft
 * 版本是否支持而定。列表之外的已安装加载器（例如 LiteLoader）无法参与预选，
 * 一律直接算"未识别"。
 */
internal fun oxideModReloadableLoaders(mcVer: String): Set<ModLoader> =
    oxideModPlan(mcVer).map { slot -> slot.loader }.toSet()

/**
 * 生成这一次修改的变更内容
 *
 * 与旧 `ModifyAddonsViewModel.updateDiffs` 逐条对应：
 * - 目标 Minecraft 版本不同就记一次版本变更；
 * - 装着的加载器里，没被识别的那一个在**没有**换 Minecraft 版本时默认原样保留，
 *   只有用户主动选了不等价的版本才算变更；换版本之后它视为被移除；
 * - 认得出的加载器没选就是移除，选了但不同就是变更；
 * - 没装过的加载器只要选了就是新增。
 *
 * 没有任何变更时返回 null，旧向导的"开始修改"按钮也是这么禁用的。
 * [equivalent] 负责"选中的版本与已装版本是否算同一个"（OptiFine 有额外规则）。
 */
internal fun oxideModDiffs(
    state: OxideModifyState,
    equivalent: (OxideModLoader, String) -> Boolean,
): ModifyDiffs? {
    val diffs = buildList {
        if (state.targetGameVersion != state.originalGameVersion) {
            add(ModifyDiffs.McChange(state.originalGameVersion, state.targetGameVersion))
        }

        val keepUnmatched = state.targetGameVersion == state.originalGameVersion
        state.installed.forEach { installed ->
            val selection = state.selections[installed.loader] ?: return@forEach
            // 列表还没到齐的加载器不给结论，否则会凭空生成一次"移除"
            if (!selection.matched) return@forEach
            val selected = selection.version

            if (keepUnmatched && selection.unmatched) {
                if (selected != null && !equivalent(installed, selected)) {
                    add(
                        ModifyDiffs.LoaderChange(
                            modloader = installed.loader,
                            original = installed.version,
                            updateTo = selected,
                        )
                    )
                }
                return@forEach
            }

            when {
                selected == null -> add(ModifyDiffs.LoaderRemove(modloader = installed.loader))
                !equivalent(installed, selected) -> add(
                    ModifyDiffs.LoaderChange(
                        modloader = installed.loader,
                        original = installed.version,
                        updateTo = selected,
                    )
                )
            }
        }

        oxideModLoaderOrder.forEach { loader ->
            if (state.installed.none { it.loader == loader }) {
                state.selections[loader]?.version?.let { version ->
                    add(ModifyDiffs.LoaderInstall(modloader = loader, version = version))
                }
            }
        }
    }
    return diffs.takeIf { it.isNotEmpty() }?.let { list -> ModifyDiffs(list) }
}

/** 修改流程的四步 */
internal enum class OxideModStep(val titleRes: Int) {
    Version(R.string.oxide_mod_step_version),
    Loader(R.string.oxide_mod_step_loader),
    Instance(R.string.oxide_mod_step_instance),
    Apply(R.string.oxide_mod_step_apply),
}

/**
 * 步骤可达性
 *
 * 第一步永远可达：换 Minecraft 版本不需要任何前置条件。
 * 中间两步要求有一个目标版本，而这一页的目标版本永远有值——没选时就是它原本
 * 那个版本。实例元数据读不出来（[instanceReadable] 为 false）时后面两步都不可达，
 * 因为没有 Minecraft 版本就没有从哪来的原值。
 */
internal fun oxideModStepReachable(
    step: OxideModStep,
    targetGameVersion: String?,
    instanceReadable: Boolean,
): Boolean {
    val hasTarget = !targetGameVersion.isNullOrBlank() && instanceReadable
    return when (step) {
        OxideModStep.Version -> true
        OxideModStep.Loader, OxideModStep.Instance, OxideModStep.Apply -> hasTarget
    }
}

/** 改名的判定结果 */
internal enum class OxideModNameVerdict {
    /** 没有改名，因此不受任何校验约束 */
    Kept,

    /** 改名，且通过了全部校验 */
    Ok,

    /** 名字是空的 */
    Empty,

    /** 名字里有非法字符或长度不对，不能当文件夹名 */
    Invalid,

    /** 已经有另一个实例叫这个名字 */
    Conflict,
}

/**
 * 改名判定
 *
 * 与旧确认对话框逐项一致：空名不行、文件名非法不行、改成已存在的版本名不行；
 * 没改名则永远放行（`isVersionExists` 的结果在那条分支上根本不该看）。
 */
internal fun oxideModNameVerdict(
    name: String,
    currentName: String,
    conflict: Boolean,
    filenameInvalid: Boolean,
): OxideModNameVerdict {
    if (name == currentName) return OxideModNameVerdict.Kept
    return when {
        name.isEmpty() -> OxideModNameVerdict.Empty
        filenameInvalid -> OxideModNameVerdict.Invalid
        conflict -> OxideModNameVerdict.Conflict
        else -> OxideModNameVerdict.Ok
    }
}

/** 这次改名放不放行 */
internal fun oxideModNameAllowsRename(verdict: OxideModNameVerdict): Boolean =
    verdict == OxideModNameVerdict.Kept || verdict == OxideModNameVerdict.Ok

/**
 * 把当前选择组装成真正的修改请求
 *
 * 与旧向导逐字段一致：`customVersionName` 用的是**旧**名字（改名发生在安装成功
 * 之后，由 [ModifyVersionViewModel] 负责），目标 Minecraft 版本上不支持的加载器
 * 直接不参与，API 模组一个都不写。
 */
internal fun oxideModGameDownloadInfo(
    targetGameVersion: String,
    currentVersionName: String,
    supports: LoaderVerSupports,
    current: CurrentAddon,
): GameDownloadInfo = GameDownloadInfo(
    gameVersion = targetGameVersion,
    customVersionName = currentVersionName,
    optifine = current.optifineVersion.value,
    forge = current.forgeVersion.value,
    neoforge = current.neoforgeVersion.value.takeIf { supports.isNeoForgeSupports },
    fabric = current.fabricVersion.value.takeIf { supports.isFabricSupports },
    legacyFabric = current.legacyFabricVersion.value.takeIf { supports.isLegacyFabricSupports },
    quilt = current.quiltVersion.value.takeIf { supports.isQuiltSupports },
    cleanroom = current.cleanroomVersion.value.takeIf { supports.isCleanroomSupports },
)

// ---------------------------------------------------------------------------
// 真实状态
// ---------------------------------------------------------------------------

/** 实例图标的状态 */
private sealed interface OxideModIconState {
    /** 还没探到磁盘上有没有图标 */
    data object Probing : OxideModIconState

    /** 已经知道有没有自定义图标 */
    data class Ready(val exists: Boolean) : OxideModIconState

    /** 正在导入或清除 */
    data object Working : OxideModIconState

    /** 上一次导入失败，图标维持原样 */
    data class Failed(val message: AndroidStringText) : OxideModIconState
}

/** 从磁盘上读回来的那一份实例事实 */
private class ModifyVersionProbe(
    val versionInfo: VersionInfo?,
    val installed: List<OxideModLoader>,
)

/**
 * @return 从版本解析出当前已安装的全部加载器
 *
 * 与旧 `rememberInstalledLoaders` 逐条一致：先读安装时写下的 launchFor 元数据，
 * 再补上版本信息里的加载器，最后按加载器去重。
 */
private fun readInstalledLoaders(version: Version, versionInfo: VersionInfo): List<OxideModLoader> =
    buildList {
        runCatching {
            val jsonFile = File(version.getVersionPath(), "${version.getVersionName()}.json")
            GSON.fromJson(jsonFile.readText(), LaunchFor::class.java)
        }.onFailure { e ->
            Logger.warning(MODIFY_TAG, "Failed to parse the launchFor info of the version.", e)
        }.getOrNull()?.infos?.forEach { info ->
            if (info.name.equals("Minecraft", ignoreCase = true)) return@forEach
            ModLoader.entries
                .find { loader -> loader.displayName.equals(info.name, ignoreCase = true) }
                ?.let { loader -> add(OxideModLoader(loader, info.version)) }
        }
        versionInfo.loaderInfos.forEach { loaderInfo ->
            if (none { it.loader == loaderInfo.loader }) {
                add(OxideModLoader(loaderInfo.loader, loaderInfo.version))
            }
        }
    }.distinctBy { it.loader }

/** 组合期不许碰磁盘，因此整份探针在 IO 上一次取回 */
private suspend fun probeVersion(version: Version): ModifyVersionProbe = withContext(Dispatchers.IO) {
    val versionInfo = version.getVersionInfo()
    ModifyVersionProbe(
        versionInfo = versionInfo,
        installed = versionInfo?.let { info -> readInstalledLoaders(version, info) }.orEmpty(),
    )
}

// ---------------------------------------------------------------------------
// ViewModel：只做状态与一次性动作，网络与磁盘都在 viewModelScope 里
// ---------------------------------------------------------------------------

/**
 * 修改版的加载器状态
 *
 * 请求方式、预选顺序与不兼容判定全部照抄旧 `ModifyAddonsViewModel`：
 * 同一个 [AddonList] 存列表、同一个 [CurrentAddon] 存选择与不兼容集合、
 * 同一个 [runWithState] 把异常翻译成 [AddonState]；预选仍然按
 * [VersionInfo.PRIMARY_PRIORITY] 让主加载器先落地，低优先级的等它，
 * 这样最终填进去的选择是确定的。
 *
 * 与安装页那一版的差别只有两处：这里只处理加载器本体（没有 API 模组），
 * 以及变更内容由纯函数 [oxideModDiffs] 算，而不是在 ViewModel 里就地拼列表。
 */
private class OxideModAddonsViewModel(
    /** 选定的目标 Minecraft 版本 */
    private val gameVersion: String,
    /** 实例原本的 Minecraft 版本 */
    private val originalGameVersion: String,
    /** 当前已安装的加载器 */
    private val installedLoaders: List<OxideModLoader>,
    /** 加载器支持情况，决定拉哪些加载器的版本列表 */
    val supports: LoaderVerSupports,
) : ViewModel() {

    val addonList = AddonList()
    val currentAddon = CurrentAddon()

    /** 列表到齐或选择变化之后自增，用来触发一次不兼容重算 */
    var revision by mutableIntStateOf(0)
        private set

    /** 当前这一次修改的变更内容，没有变更时为 null */
    var currentDiffs by mutableStateOf<ModifyDiffs?>(null)
        private set

    /** 无法与版本列表匹配的已安装加载器，用户未主动选择新版本时保留原样 */
    var unmatchedLoaders by mutableStateOf<Set<ModLoader>>(emptySet())
        private set

    /** 会加载版本列表的加载器 */
    private val reloadableLoaders = oxideModReloadableLoaders(gameVersion)

    private val plan = oxideModPlan(gameVersion)

    /** 已完成预选尝试的加载器 */
    private val attemptedPreselects = mutableSetOf<ModLoader>()

    /** 已加载版本列表、等待预选的加载器 */
    private val pendingPreselects = linkedMapOf<ModLoader, () -> Unit>()

    private fun findInstalled(loader: ModLoader): OxideModLoader? =
        installedLoaders.firstOrNull { it.loader == loader }

    /** 当前选中版本的编号，与旧向导 `selectedVersionOf` 同源 */
    private fun selectedVersionOf(loader: ModLoader): String? = when (loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineVersion.value?.getAddonVersion()
        ModLoader.FORGE -> currentAddon.forgeVersion.value?.getAddonVersion()
        ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value?.getAddonVersion()
        ModLoader.FABRIC -> currentAddon.fabricVersion.value?.getAddonVersion()
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricVersion.value?.getAddonVersion()
        ModLoader.QUILT -> currentAddon.quiltVersion.value?.getAddonVersion()
        ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value?.getAddonVersion()
        else -> null
    }

    /**
     * 判断选中的版本与已安装版本是否等价
     *
     * OptiFine 的已安装信息可能来自 Maven 坐标或旧版短版本串，因此要和
     * 版本列表里的条目做等价匹配而不是字符串相等——与旧实现逐条一致。
     */
    private fun selectedEqualsInstalled(installed: OxideModLoader, selected: String): Boolean {
        if (selected == installed.version) return true
        if (installed.loader != ModLoader.OPTIFINE) return false
        return addonList.optifineList?.any { optifine ->
            optifine.getAddonVersion() == selected && optifine.matchesInstalledVersion(installed.version)
        } == true
    }

    /** 当前选择状态，纯函数 [oxideModDiffs] 的输入 */
    private fun selections(): Map<ModLoader, OxideModSelection> = buildMap {
        plan.forEach { slot ->
            put(
                slot.loader,
                OxideModSelection(
                    version = selectedVersionOf(slot.loader),
                    matched = slot.loader in attemptedPreselects,
                    unmatched = slot.loader in unmatchedLoaders,
                ),
            )
        }
    }

    /** 重算变更内容 */
    fun updateDiffs() {
        currentDiffs = oxideModDiffs(
            state = OxideModifyState(
                originalGameVersion = originalGameVersion,
                targetGameVersion = gameVersion,
                installed = installedLoaders,
                selections = selections(),
            ),
            equivalent = ::selectedEqualsInstalled,
        )
    }

    // ---- 列表 ------------------------------------------------------------

    private fun <T> launchAddonReload(
        updateState: (AddonState) -> Unit,
        fetch: suspend () -> T?,
        afterLoaded: (T?) -> Unit,
    ) {
        viewModelScope.launch {
            runWithState(updateState, fetch).also { versions ->
                afterLoaded(versions)
                revision++
                updateDiffs()
            }
        }
    }

    private fun reloadOptiFine() = launchAddonReload(
        { currentAddon.optifineState = it },
        { OptiFineVersions.fetchOptiFineList(gameVersion = gameVersion) },
    ) { versions ->
        addonList.optifineList = versions
        enqueuePreselect(ModLoader.OPTIFINE) {
            findInstalled(ModLoader.OPTIFINE)?.let { installed ->
                preselectInstalled(
                    state = currentAddon.optifineVersion,
                    loader = ModLoader.OPTIFINE,
                    versions = versions,
                    installedVersion = installed.version,
                    matcher = { version, target -> version.matchesInstalledVersion(target) },
                    // 与已选 Forge 成对校验兼容性
                    validator = { version ->
                        currentAddon.forgeVersion.value?.let { forge ->
                            isOptiFineCompatibleWithForge(version, forge)
                        } ?: true
                    },
                )
            }
        }
    }

    private fun reloadForge() = launchAddonReload(
        { currentAddon.forgeState = it },
        { ForgeVersions.fetchForgeList(gameVersion) },
    ) { versions ->
        addonList.forgeList = versions
        enqueuePreselect(ModLoader.FORGE) {
            findInstalled(ModLoader.FORGE)?.let { installed ->
                preselectInstalled(
                    state = currentAddon.forgeVersion,
                    loader = ModLoader.FORGE,
                    versions = versions,
                    installedVersion = installed.version,
                    // 与已选 OptiFine 成对校验兼容性
                    validator = { version ->
                        currentAddon.optifineVersion.value?.let { optifine ->
                            isOptiFineCompatibleWithForge(optifine, version)
                        } ?: true
                    },
                )
            }
        }
    }

    private fun reloadNeoForge() = launchAddonReload(
        { currentAddon.neoforgeState = it },
        { NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion) },
    ) { versions ->
        addonList.neoforgeList = versions
        enqueuePreselect(ModLoader.NEOFORGE) {
            findInstalled(ModLoader.NEOFORGE)?.let { installed ->
                preselectInstalled(currentAddon.neoforgeVersion, ModLoader.NEOFORGE, versions, installed.version)
            }
        }
    }

    private fun reloadFabric() = launchAddonReload(
        { currentAddon.fabricState = it },
        { FabricVersions.fetchFabricLoaderList(gameVersion) },
    ) { versions ->
        addonList.fabricList = versions
        enqueuePreselect(ModLoader.FABRIC) {
            findInstalled(ModLoader.FABRIC)?.let { installed ->
                preselectInstalled(currentAddon.fabricVersion, ModLoader.FABRIC, versions, installed.version)
            }
        }
    }

    private fun reloadLegacyFabric() = launchAddonReload(
        { currentAddon.legacyFabricState = it },
        { LegacyFabricVersions.fetchFabricLoaderList(gameVersion) },
    ) { versions ->
        addonList.legacyFabricList = versions
        enqueuePreselect(ModLoader.LEGACY_FABRIC) {
            findInstalled(ModLoader.LEGACY_FABRIC)?.let { installed ->
                preselectInstalled(
                    currentAddon.legacyFabricVersion,
                    ModLoader.LEGACY_FABRIC,
                    versions,
                    installed.version,
                )
            }
        }
    }

    private fun reloadQuilt() = launchAddonReload(
        { currentAddon.quiltState = it },
        { QuiltVersions.fetchQuiltLoaderList(gameVersion) },
    ) { versions ->
        addonList.quiltList = versions
        enqueuePreselect(ModLoader.QUILT) {
            findInstalled(ModLoader.QUILT)?.let { installed ->
                preselectInstalled(currentAddon.quiltVersion, ModLoader.QUILT, versions, installed.version)
            }
        }
    }

    private fun reloadCleanroom() = launchAddonReload(
        { currentAddon.cleanroomState = it },
        { CleanroomVersions.fetchLoaderList(gameVersion) },
    ) { versions ->
        addonList.cleanroomList = versions
        enqueuePreselect(ModLoader.CLEANROOM) {
            findInstalled(ModLoader.CLEANROOM)?.let { installed ->
                preselectInstalled(currentAddon.cleanroomVersion, ModLoader.CLEANROOM, versions, installed.version)
            }
        }
    }

    /** 错误条上的刷新：只重新拉取这一类 */
    fun reload(slot: OxideAddonSlot) {
        when (slot.loader) {
            ModLoader.OPTIFINE -> reloadOptiFine()
            ModLoader.FORGE -> reloadForge()
            ModLoader.NEOFORGE -> reloadNeoForge()
            ModLoader.FABRIC -> reloadFabric()
            ModLoader.LEGACY_FABRIC -> reloadLegacyFabric()
            ModLoader.QUILT -> reloadQuilt()
            ModLoader.CLEANROOM -> reloadCleanroom()
            else -> Unit
        }
    }

    /**
     * 登记已加载版本列表的加载器，等待按优先级执行预选
     */
    private fun enqueuePreselect(loader: ModLoader, action: () -> Unit) {
        pendingPreselects[loader] = action
        attemptedPreselects.remove(loader) // 重新加载后允许再次尝试
        runPendingPreselects()
    }

    /**
     * 按主加载器优先级依次执行预选
     *
     * 主加载器先完成预选，其余加载器为它让位；更高优先级的列表尚未加载完成时，
     * 低优先级的那一个就等着，保证预选结果确定。与旧实现同一条规则。
     */
    private fun runPendingPreselects() {
        installedLoaders
            .sortedBy { installed ->
                VersionInfo.PRIMARY_PRIORITY.indexOf(installed.loader)
                    .takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }
            .forEach { installed ->
                val loader = installed.loader
                if (loader in attemptedPreselects) return@forEach
                val action = pendingPreselects[loader]
                if (action == null) {
                    if (loader in reloadableLoaders) return // 更高优先级的列表仍在加载，等待
                    // 无法加载版本列表的加载器，直接视为未识别
                    attemptedPreselects.add(loader)
                    unmatchedLoaders = unmatchedLoaders + loader
                    return@forEach
                }
                attemptedPreselects.add(loader)
                action()
            }
    }

    /**
     * 预选已安装的加载器版本
     *
     * 仅在当前这一项未选择版本、候选通过校验、且与已选择的其他加载器全部兼容时才填入；
     * 无法匹配时标记为未识别，修改时默认保留原样。
     */
    private fun <T : AddonVersion> preselectInstalled(
        state: MutableState<T?>,
        loader: ModLoader,
        versions: List<T>?,
        installedVersion: String,
        matcher: (T, String) -> Boolean = { version, installed -> version.isVersion(installed) },
        validator: (T) -> Boolean = { true },
    ) {
        if (state.value != null) return

        val candidate = versions?.firstOrNull { version -> matcher(version, installedVersion) }
        if (
            candidate == null ||
            !validator(candidate) ||
            !currentAddon.isCompatibleWithSelection(candidate, loader, addonList)
        ) {
            unmatchedLoaders = unmatchedLoaders + loader
            return
        }

        state.value = candidate
        unmatchedLoaders = unmatchedLoaders - loader
    }

    // ---- 选择 ------------------------------------------------------------

    fun select(slot: OxideAddonSlot, version: AddonVersion?) {
        when (slot.loader) {
            ModLoader.OPTIFINE -> currentAddon.optifineVersion.value = version as? OptiFineVersion
            ModLoader.FORGE -> currentAddon.forgeVersion.value = version as? ForgeVersion
            ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value = version as? NeoForgeVersion
            ModLoader.FABRIC -> currentAddon.fabricVersion.value = version as? FabricVersion
            ModLoader.LEGACY_FABRIC ->
                currentAddon.legacyFabricVersion.value = version as? LegacyFabricVersion

            ModLoader.QUILT -> currentAddon.quiltVersion.value = version as? QuiltVersion
            ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value = version as? CleanroomVersion
            else -> Unit
        }
        revision++
        refreshIncompatible()
        updateDiffs()
    }

    fun clear(slot: OxideAddonSlot) {
        select(slot, null)
    }

    /**
     * 重算不兼容集合，并把因此被挡住的项清空
     *
     * 旧列表是在每一项自己的 `LaunchedEffect` 里判一次；这里在选择或列表状态变化后
     * 对全部加载器各调一次，判定只依赖当前选择集合，因此结果与逐项调用一致。
     */
    fun reconcile() {
        refreshIncompatible()
        val blocked = blockedLoaders()
        if (blocked.isEmpty()) return
        plan.filter { slot -> slot.loader in blocked }
            .forEach { slot -> currentAddon.setSlotNull(slot) }
        revision++
        updateDiffs()
    }

    fun refreshIncompatible() {
        val addon = currentAddon
        val list = addonList
        addon.updateIncompatibleState(ModLoader.OPTIFINE, list)
        addon.updateIncompatibleState(ModLoader.FORGE, list)
        addon.updateIncompatibleState(ModLoader.NEOFORGE, list)
        addon.updateIncompatibleState(ModLoader.FABRIC, list)
        addon.updateIncompatibleState(ModLoader.LEGACY_FABRIC, list)
        addon.updateIncompatibleState(ModLoader.QUILT, list)
        addon.updateIncompatibleState(ModLoader.CLEANROOM, list)
    }

    /** 与当前选择冲突的那些加载器 */
    fun blockedLoaders(): Set<ModLoader> = buildSet {
        val addon = currentAddon
        if (addon.incompatibleWithOptiFine.value.isNotEmpty()) add(ModLoader.OPTIFINE)
        if (addon.incompatibleWithForge.value.isNotEmpty()) add(ModLoader.FORGE)
        if (addon.incompatibleWithNeoForge.value.isNotEmpty()) add(ModLoader.NEOFORGE)
        if (addon.incompatibleWithFabric.value.isNotEmpty()) add(ModLoader.FABRIC)
        if (addon.incompatibleWithLegacyFabric.value.isNotEmpty()) add(ModLoader.LEGACY_FABRIC)
        if (addon.incompatibleWithQuilt.value.isNotEmpty()) add(ModLoader.QUILT)
        if (addon.incompatibleWithCleanroom.value.isNotEmpty()) add(ModLoader.CLEANROOM)
    }

    /** 这一项当前的加载状态 */
    fun stateOf(slot: OxideAddonSlot): AddonState = when (slot.loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineState
        ModLoader.FORGE -> currentAddon.forgeState
        ModLoader.NEOFORGE -> currentAddon.neoforgeState
        ModLoader.FABRIC -> currentAddon.fabricState
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricState
        ModLoader.QUILT -> currentAddon.quiltState
        ModLoader.CLEANROOM -> currentAddon.cleanroomState
        else -> AddonState.None
    }

    /** 这一项当前选中的版本 */
    fun selectedOf(slot: OxideAddonSlot): AddonVersion? = when (slot.loader) {
        ModLoader.OPTIFINE -> currentAddon.optifineVersion.value
        ModLoader.FORGE -> currentAddon.forgeVersion.value
        ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value
        ModLoader.FABRIC -> currentAddon.fabricVersion.value
        ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricVersion.value
        ModLoader.QUILT -> currentAddon.quiltVersion.value
        ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value
        else -> null
    }

    /**
     * 这一项的原始版本列表
     *
     * OptiFine 与 Forge 仍然互相过滤，与旧列表的规则相同。Forge 的
     * universal / client 条目不剔除而是整段禁用，与旧修改向导
     * `checkForgeCompatibilityError` 的行为一致。
     */
    fun versionsOf(slot: OxideAddonSlot): List<AddonVersion>? = when (slot.loader) {
        ModLoader.OPTIFINE -> addonList.optifineList
            ?.let { list -> filterOptiFineAgainstForge(list, currentAddon.forgeVersion.value) }

        ModLoader.FORGE -> addonList.forgeList
            ?.let { list -> filterForgeAgainstOptiFine(list, currentAddon.optifineVersion.value) }

        ModLoader.NEOFORGE -> addonList.neoforgeList
        ModLoader.FABRIC -> addonList.fabricList
        ModLoader.LEGACY_FABRIC -> addonList.legacyFabricList
        ModLoader.QUILT -> addonList.quiltList
        ModLoader.CLEANROOM -> addonList.cleanroomList
        else -> null
    }

    init {
        // 不会被加载版本列表的已安装加载器，直接标记为未识别，避免阻塞其他加载器的预选
        installedLoaders.forEach { installed ->
            if (installed.loader !in reloadableLoaders) {
                attemptedPreselects.add(installed.loader)
                unmatchedLoaders = unmatchedLoaders + installed.loader
            }
        }
        reloadOptiFine()
        reloadForge()
        if (supports.isNeoForgeSupports) reloadNeoForge()
        if (supports.isFabricSupports) reloadFabric()
        if (supports.isLegacyFabricSupports) reloadLegacyFabric()
        if (supports.isQuiltSupports) reloadQuilt()
        if (supports.isCleanroomSupports) reloadCleanroom()
    }
}

/** 把某一类选择清空：不经过 [select]，因此不会多算一次不兼容 */
private fun CurrentAddon.setSlotNull(slot: OxideAddonSlot) {
    when (slot.loader) {
        ModLoader.OPTIFINE -> optifineVersion.value = null
        ModLoader.FORGE -> forgeVersion.value = null
        ModLoader.NEOFORGE -> neoforgeVersion.value = null
        ModLoader.FABRIC -> fabricVersion.value = null
        ModLoader.LEGACY_FABRIC -> legacyFabricVersion.value = null
        ModLoader.QUILT -> quiltVersion.value = null
        ModLoader.CLEANROOM -> cleanroomVersion.value = null
        else -> Unit
    }
}

/**
 * 实例图标
 *
 * 导入与清除都在 [viewModelScope] 里做，页面退出时协程一起被取消；
 * 非图片的文件会先删掉再报错，因此不会留下一个坏掉的图标。
 */
private class OxideModIconViewModel : ViewModel() {
    var state by mutableStateOf<OxideModIconState>(OxideModIconState.Probing)
        private set

    fun probe(iconFile: File) {
        viewModelScope.launch {
            state = OxideModIconState.Ready(withContext(Dispatchers.IO) { iconFile.exists() })
        }
    }

    fun import(context: Context, uri: Uri, iconFile: File) {
        viewModelScope.launch {
            state = OxideModIconState.Working
            val failure = runCatching {
                withContext(Dispatchers.IO) {
                    context.copyLocalFile(uri, iconFile)
                    if (!iconFile.isImageFile()) {
                        throw IOException("The selected file is not an image!")
                    }
                }
            }.exceptionOrNull()

            state = if (failure != null) {
                Logger.error(MODIFY_TAG, "Failed to import the instance icon!", failure)
                // 坏掉的图标不能留在磁盘上，否则这一版的图标就废了
                withContext(Dispatchers.IO) { FileUtils.deleteQuietly(iconFile) }
                OxideModIconState.Failed(
                    androidText(R.string.oxide_mod_icon_failed, failure.getMessageOrToString())
                )
            } else {
                OxideModIconState.Ready(withContext(Dispatchers.IO) { iconFile.exists() })
            }
        }
    }

    fun reset(iconFile: File) {
        viewModelScope.launch {
            state = OxideModIconState.Working
            withContext(Dispatchers.IO) { FileUtils.deleteQuietly(iconFile) }
            probe(iconFile)
        }
    }

    /** 错误条被关掉之后重新读一次磁盘，让这一行回到真实状态 */
    fun recheck(iconFile: File) {
        probe(iconFile)
    }
}

// ---------------------------------------------------------------------------
// 界面
// ---------------------------------------------------------------------------

/**
 * 修改实例
 *
 * 取代 `VersionSettingsScreen` → `NormalNavKey.Versions.ModifyVersion`
 * （`ModifyVersionScreen`）连同它那个 `SelectGameVersionHost.VersionSettings`
 * 版本选择页。四步就是这一页真正要做的四件事：定目标 Minecraft 版本、
 * 改加载器、改实例本身（改名与图标）、把变更真正执行掉。
 *
 * 后端一个字都没有改：
 * - 版本清单仍然由 `MinecraftVersions` 提供，过滤与"选版本"这一步直接复用
 *   安装页的 [OxideInstallVersionStep]，因此两页看到的版本列表逐字一致；
 * - 加载器列表仍然走既有的 `*Versions.fetch*List`，[AddonList] / [CurrentAddon]
 *   与旧向导共用同一份实现，不兼容判定也仍是后端那一份；
 * - 修改仍然由 [ModifyVersionViewModel] 按同一个 [ModifyPayload] 执行，
 *   也就是说任务链、取消、以及成功之后的改名全都还是原来的那条路；
 * - 改名与图标的落盘仍然是 `VersionsManager` 与 `version.getVersionIconFile()`。
 *
 * 换掉的只是呈现：Material 卡片与对话框换成 Oxide 的面板、分区行与进度条。
 */
@Composable
fun OxideModifyVersionPage(
    version: Version,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val hostActions = LocalOxideHostActions.current
    val eventViewModel = rememberOxideEventViewModel()

    val versionsViewModel: OxideInstallVersionsViewModel =
        viewModel(key = "OxideModifyVersions") { OxideInstallVersionsViewModel() }
    val modifyViewModel: ModifyVersionViewModel =
        viewModel(key = "OxideModifyVersionOperation")
    val iconViewModel: OxideModIconViewModel =
        viewModel(key = "OxideModifyVersionIcon")

    // 实例元数据要读磁盘：整份探针在 IO 上取回，组合期只读结果
    var probe by remember(version) { mutableStateOf<ModifyVersionProbe?>(null) }
    LaunchedEffect(version) {
        probe = probeVersion(version)
    }

    val versionInfo = probe?.versionInfo
    val originalGameVersion = versionInfo?.minecraftVersion.orEmpty()

    // 目标版本：没选就是它原本那个版本，与旧向导的 selectedGameVersion ?: 原始版本同义
    var chosenGameVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val targetGameVersion = chosenGameVersion?.takeIf { it.isNotBlank() } ?: originalGameVersion

    // 上一次会话可能停在成功或失败上；重新进来时清掉，但绝不打断正在跑的修改
    LaunchedEffect(version) {
        if (modifyViewModel.installOperation !is ModifyOperation.Install) {
            modifyViewModel.installOperation = ModifyOperation.None
        }
    }

    val operation = modifyViewModel.installOperation
    val modifying = operation is ModifyOperation.Install

    // 正在修改时返回键不关这一页：与旧对话框 dismissOnClickOutside = false 一样，
    // 这一层后注册，因此先于外壳那一层拿到返回事件
    BackHandler(enabled = modifying) { }
    val dismiss: () -> Unit = if (modifying) ({}) else onDismiss

    val iconFile = remember(version) { version.getVersionIconFile() }
    LaunchedEffect(iconFile) { iconViewModel.probe(iconFile) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.pagePaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideIconButton(
                onClick = dismiss,
                enabled = !modifying,
                glyph = "←",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_sec_topbar_back)
                ),
            )
            Spacer(Modifier.width(6.dp))
            OxidePageTitle(
                text = stringResource(R.string.oxide_modify_title),
                modifier = Modifier.weight(1f),
                trailing = {
                    OxideBadge(text = version.getVersionName())
                },
            )
        }
        OxideSectionLabel(
            modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
            text = stringResource(R.string.oxide_mod_subtitle, version.getVersionName()),
        )

        Spacer(Modifier.height(metrics.sectionGap))

        // 这一层必须占掉剩余高度：Column 会把"无上界"的高度交给直接子项，
        // 里面那些 weight(1f) 就再也分不到高度了，列表会一路撑出屏幕
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val current = probe
            when {
                current == null -> OxideLoadingRow(
                    modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                    text = stringResource(R.string.oxide_mod_loading_instance),
                )

                versionInfo == null -> OxideEmptyState(
                    modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                    title = stringResource(R.string.oxide_mod_info_missing),
                    action = {
                        OxideButton(
                            text = stringResource(R.string.oxide_sec_topbar_back),
                            onClick = dismiss,
                        )
                    },
                )

                else -> OxideModifyVersionBody(
                    metrics = metrics,
                    context = context,
                    version = version,
                    originalGameVersion = originalGameVersion,
                    targetGameVersion = targetGameVersion,
                    onChooseGameVersion = { chosenGameVersion = it },
                    installedLoaders = current.installed,
                    versionsViewModel = versionsViewModel,
                    modifyViewModel = modifyViewModel,
                    iconViewModel = iconViewModel,
                    iconFile = iconFile,
                    onOpenLink = hostActions.openLink,
                    onFinished = {
                        modifyViewModel.installOperation = ModifyOperation.None
                        onDismiss()
                    },
                    onCancelModify = {
                        eventViewModel.sendKeepScreen(false)
                        modifyViewModel.cancel()
                    },
                    onModifyStarted = { eventViewModel.sendKeepScreen(true) },
                    onModifyStopped = { eventViewModel.sendKeepScreen(false) },
                )
            }
        }
    }
}

/** 四步的全部内容 */
@Composable
private fun OxideModifyVersionBody(
    metrics: OxideMetrics,
    context: Context,
    version: Version,
    originalGameVersion: String,
    targetGameVersion: String,
    onChooseGameVersion: (String) -> Unit,
    installedLoaders: List<OxideModLoader>,
    versionsViewModel: OxideInstallVersionsViewModel,
    modifyViewModel: ModifyVersionViewModel,
    iconViewModel: OxideModIconViewModel,
    iconFile: File,
    onOpenLink: (String) -> Unit,
    onFinished: () -> Unit,
    onCancelModify: () -> Unit,
    onModifyStarted: () -> Unit,
    onModifyStopped: () -> Unit,
) {
    val supports = remember(targetGameVersion) { oxideLoaderVerSupports(targetGameVersion) }

    // 这一份只有在探针回来之后才建：目标 Minecraft 版本与已安装加载器都从那里来，
    // 早建一步就会拿空值去拉加载器列表。key 与旧实现一致，因此换目标版本等于换一份
    // 全新的加载器状态
    val addonsViewModel: OxideModAddonsViewModel = viewModel(
        key = "${version.getVersionName()}_ModifyVersion_$targetGameVersion"
    ) {
        OxideModAddonsViewModel(
            gameVersion = targetGameVersion,
            originalGameVersion = originalGameVersion,
            installedLoaders = installedLoaders,
            supports = supports,
        )
    }

    // 列表到齐或选择变化后重算一次不兼容；组合本身不会发起任何请求
    LaunchedEffect(addonsViewModel.revision) {
        addonsViewModel.reconcile()
    }

    var step by rememberSaveable { mutableStateOf(OxideModStep.Version) }
    var activeSlotIndex by rememberSaveable { mutableIntStateOf(0) }

    val currentName = remember(version) { version.getVersionName() }
    var nameValue by rememberSaveable { mutableStateOf(currentName) }

    val diffs = addonsViewModel.currentDiffs
    val plan = remember(targetGameVersion) { oxideModPlan(targetGameVersion) }

    OxideStepLayout(
        metrics = metrics,
        titles = OxideModStep.entries.map { entry -> stringResource(entry.titleRes) },
        currentIndex = OxideModStep.entries.indexOf(step),
        reachable = { index ->
            oxideModStepReachable(
                step = OxideModStep.entries[index],
                targetGameVersion = targetGameVersion,
                instanceReadable = true,
            )
        },
        onSelect = { index -> step = OxideModStep.entries[index] },
    ) {
        when (step) {
            OxideModStep.Version -> OxideModVersionStep(
                metrics = metrics,
                viewModel = versionsViewModel,
                originalGameVersion = originalGameVersion,
                targetGameVersion = targetGameVersion,
                onChoose = onChooseGameVersion,
                onOpenLink = onOpenLink,
            )

            OxideModStep.Loader -> OxideModLoaderStep(
                metrics = metrics,
                viewModel = addonsViewModel,
                installedLoaders = installedLoaders,
                originalGameVersion = originalGameVersion,
                targetGameVersion = targetGameVersion,
                plan = plan,
                activeSlotIndex = activeSlotIndex,
                onOpenSlot = { activeSlotIndex = it },
            )

            OxideModStep.Instance -> OxideModInstanceStep(
                metrics = metrics,
                context = context,
                versionName = currentName,
                nameValue = nameValue,
                iconViewModel = iconViewModel,
                iconFile = iconFile,
                onNameChange = { nameValue = it },
            )

            OxideModStep.Apply -> OxideModApplyStep(
                metrics = metrics,
                context = context,
                version = version,
                originalGameVersion = originalGameVersion,
                targetGameVersion = targetGameVersion,
                supports = supports,
                addonsViewModel = addonsViewModel,
                modifyViewModel = modifyViewModel,
                plan = plan,
                diffs = diffs,
                nameValue = nameValue,
                onGotoVersion = { step = OxideModStep.Version },
                onGotoLoader = { step = OxideModStep.Loader },
                onGotoInstance = { step = OxideModStep.Instance },
                onFinished = onFinished,
                onCancelModify = onCancelModify,
                onModifyStarted = onModifyStarted,
                onModifyStopped = onModifyStopped,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 第一步：目标 Minecraft 版本
// ---------------------------------------------------------------------------

@Composable
private fun OxideModVersionStep(
    metrics: OxideMetrics,
    viewModel: OxideInstallVersionsViewModel,
    originalGameVersion: String,
    targetGameVersion: String,
    onChoose: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSectionLabel(text = stringResource(R.string.oxide_mod_target_section))
                OxideSettingRow(
                    label = stringResource(R.string.oxide_inst_chosen_version),
                    value = targetGameVersion,
                    hint = if (targetGameVersion != originalGameVersion) {
                        stringResource(R.string.versions_modify_mc_changed_title)
                    } else {
                        stringResource(R.string.oxide_inst_edit_version)
                    },
                )
            }
        }

        // 换版本的后果必须写出来：旧加载器几乎必然匹配不上，会被移除
        if (targetGameVersion != originalGameVersion) {
            Spacer(Modifier.height(metrics.cardGap))
            OxideModNotice(
                metrics = metrics,
                title = stringResource(R.string.versions_modify_mc_changed_title),
                detail = stringResource(R.string.versions_modify_mc_changed_tip),
            )
        }

        Spacer(Modifier.height(metrics.cardGap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            OxideInstallVersionStep(
                metrics = metrics,
                viewModel = viewModel,
                chosen = targetGameVersion,
                onChoose = onChoose,
                onOpenLink = onOpenLink,
            )
        }
    }
}

/** 一条纯文字的提醒：不靠颜色单独承担状态，标题与正文都在 */
@Composable
private fun OxideModNotice(
    metrics: OxideMetrics,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusControl)
            .padding(
                start = metrics.secControlPadding,
                end = metrics.secRowGap,
                top = metrics.secRowGap,
                bottom = metrics.secRowGap,
            ),
    ) {
        Text(
            text = title,
            color = Oxide.FgStrong,
            fontSize = Oxide.Type.BodyStrong.fontSize,
            lineHeight = Oxide.Type.BodyStrong.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 第二步：加载器
// ---------------------------------------------------------------------------

@Composable
private fun OxideModLoaderStep(
    metrics: OxideMetrics,
    viewModel: OxideModAddonsViewModel,
    installedLoaders: List<OxideModLoader>,
    originalGameVersion: String,
    targetGameVersion: String,
    plan: List<OxideAddonSlot>,
    activeSlotIndex: Int,
    onOpenSlot: (Int) -> Unit,
) {
    // 换了 Minecraft 版本之后，装着的加载器一律视为被移除，因此不再提醒"保留原样"
    val unrecognized = if (targetGameVersion == originalGameVersion) {
        installedLoaders.filter { installed -> installed.loader in viewModel.unmatchedLoaders }
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (unrecognized.isNotEmpty()) {
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    OxideSectionLabel(text = stringResource(R.string.generic_warning))
                    Text(
                        text = stringResource(R.string.versions_modify_unrecognized_title),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    unrecognized.forEach { installed ->
                        OxideSettingRow(
                            label = installed.loader.displayName,
                            value = installed.version,
                            hint = stringResource(R.string.oxide_mod_loader_kept),
                        )
                    }
                }
            }
            Spacer(Modifier.height(metrics.cardGap))
        }

        if (plan.isEmpty()) {
            OxideEmptyState(
                title = stringResource(R.string.download_game_addon_unavailable),
                detail = stringResource(R.string.oxide_inst_addon_unavailable_detail),
            )
            return@Column
        }

        val activeIndex = activeSlotIndex.coerceIn(0, plan.lastIndex)
        val slot = plan[activeIndex]

        // 同上：必须显式分掉剩余高度，否则下面那两个面板会被撑出屏幕而不是各自滚动
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val sideBySide = maxWidth >= metrics.cardMinWidth * 1.15f

            Column(modifier = Modifier.fillMaxSize()) {
                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        OxideModSlotList(
                            metrics = metrics,
                            viewModel = viewModel,
                            installedLoaders = installedLoaders,
                            plan = plan,
                            activeIndex = activeIndex,
                            onSelect = onOpenSlot,
                            modifier = Modifier
                                .width(metrics.cardMinWidth)
                                .fillMaxHeight(),
                        )
                        OxideModSlotDetail(
                            metrics = metrics,
                            viewModel = viewModel,
                            installedLoaders = installedLoaders,
                            slot = slot,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    ) {
                        plan.forEachIndexed { index, item ->
                            val picked = viewModel.selectedOf(item)
                            OxideSecChip(
                                label = if (picked == null) {
                                    item.loader.displayName
                                } else {
                                    "${item.loader.displayName} · ${oxideAddonTitle(item, picked)}"
                                },
                                selected = index == activeIndex,
                                metrics = metrics,
                                onClick = { onOpenSlot(index) },
                            )
                        }
                    }
                    Spacer(Modifier.height(metrics.cardGap))
                    OxideModSlotDetail(
                        metrics = metrics,
                        viewModel = viewModel,
                        installedLoaders = installedLoaders,
                        slot = slot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/** 左侧：这一版上真实存在的加载器 */
@Composable
private fun OxideModSlotList(
    metrics: OxideMetrics,
    viewModel: OxideModAddonsViewModel,
    installedLoaders: List<OxideModLoader>,
    plan: List<OxideAddonSlot>,
    activeIndex: Int,
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
        OxideSectionLabel(text = stringResource(R.string.oxide_mod_loader_section))
        Spacer(Modifier.height(metrics.secRowGap))
        plan.forEachIndexed { index, slot ->
            OxideModSlotRow(
                metrics = metrics,
                viewModel = viewModel,
                installed = installedLoaders.firstOrNull { it.loader == slot.loader },
                slot = slot,
                selected = index == activeIndex,
                onClick = { onSelect(index) },
            )
            Spacer(Modifier.height(metrics.secRowGap))
        }
    }
}

/**
 * 清单里的一行：名称 + 这一项的真实状态
 *
 * 状态文本由 [modSlotStatus] 算，清单栏与详情栏共用，因此两处说的永远是同一件事。
 */
@Composable
private fun OxideModSlotRow(
    metrics: OxideMetrics,
    viewModel: OxideModAddonsViewModel,
    installed: OxideModLoader?,
    slot: OxideAddonSlot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val state = viewModel.stateOf(slot)
    val picked = viewModel.selectedOf(slot)
    val blocked = slot.loader in viewModel.blockedLoaders()
    val status = modSlotStatus(
        viewModel = viewModel,
        slot = slot,
        state = state,
        picked = picked,
        blocked = blocked,
        installed = installed,
    )

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.loader.displayName,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (installed != null) {
                    Spacer(Modifier.width(metrics.secRowGap))
                    // "装着的"由这个徽章与下面的文字一起说出来，不只靠颜色
                    OxideBadge(
                        text = stringResource(R.string.oxide_mod_badge_installed),
                        tone = OxideBadgeTone.Active,
                    )
                }
            }
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

/**
 * 某一类加载器此刻的真实状态
 *
 * 顺序与旧向导那一排 `AddonList` 的表头一致：加载中、不兼容、列表出错、
 * 已选中、装着的这一版认不出来、这一版没有可用条目、Forge 存在无法自动安装的条目、
 * 最后才是"可选"。
 */
@Composable
private fun modSlotStatus(
    viewModel: OxideModAddonsViewModel,
    slot: OxideAddonSlot,
    state: AddonState,
    picked: AddonVersion?,
    blocked: Boolean,
    installed: OxideModLoader?,
): String {
    val addon = viewModel.currentAddon
    val versions = viewModel.versionsOf(slot)
    return when {
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
            R.string.oxide_mod_loader_installed,
            oxideAddonTitle(slot, picked),
        )

        // 装着的这一版在列表里找不到对应物，因此默认原样保留；只说事实，不给假选择
        installed != null && slot.loader in viewModel.unmatchedLoaders ->
            stringResource(R.string.oxide_mod_loader_kept_short)

        versions.isNullOrEmpty() -> stringResource(R.string.download_game_addon_unavailable)
        slot.loader == ModLoader.FORGE && forgeHasUninstallableEntries(viewModel.addonList.forgeList) ->
            stringResource(R.string.download_game_addon_not_installable)

        else -> stringResource(R.string.download_game_addon_available)
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
        ModLoader.LEGACY_FABRIC -> addon.incompatibleWithLegacyFabric.value
        ModLoader.QUILT -> addon.incompatibleWithQuilt.value
        ModLoader.CLEANROOM -> addon.incompatibleWithCleanroom.value
        else -> emptySet()
    }
    return names.joinToString(", ") { loader -> loader.displayName }
}

/** 右侧：所选加载器的真实版本列表 */
@Composable
private fun OxideModSlotDetail(
    metrics: OxideMetrics,
    viewModel: OxideModAddonsViewModel,
    installedLoaders: List<OxideModLoader>,
    slot: OxideAddonSlot,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addon = viewModel.currentAddon
    val state = viewModel.stateOf(slot)
    val picked = viewModel.selectedOf(slot)
    val versions = viewModel.versionsOf(slot)
    val blocked = slot.loader in viewModel.blockedLoaders()
    val pickedKey = picked?.let { version -> oxideAddonKey(slot, version) }
    // universal / client 那类 Forge 无法自动安装：与旧向导一样，整段不给选
    val uninstallable = slot.loader == ModLoader.FORGE &&
        forgeHasUninstallableEntries(viewModel.addonList.forgeList)

    val options = remember(versions, pickedKey) { oxideAddonOptions(slot, versions, context) }
    val status = modSlotStatus(
        viewModel = viewModel,
        slot = slot,
        state = state,
        picked = picked,
        blocked = blocked,
        installed = installedLoaders.firstOrNull { it.loader == slot.loader },
    )

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

        // 窄屏把清单折成了标签，这一行是那一栏仅剩的状态出口，因此这里也说出来
        Text(
            text = status,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
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

            // 旧向导在这种情况下把整个 Forge 列表锁掉，这里照旧：只给事实，不给假选择
            uninstallable -> OxideModNotice(
                metrics = metrics,
                title = slot.loader.displayName,
                detail = stringResource(R.string.download_game_addon_not_installable),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(items = options, key = { option -> option.key }) { option ->
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

// ---------------------------------------------------------------------------
// 第三步：实例本身（改名与图标）
// ---------------------------------------------------------------------------

@Composable
private fun OxideModInstanceStep(
    metrics: OxideMetrics,
    context: Context,
    versionName: String,
    nameValue: String,
    iconViewModel: OxideModIconViewModel,
    iconFile: File,
    onNameChange: (String) -> Unit,
) {
    val filenameInvalid = isFilenameInvalid(nameValue)

    // 版本是否已存在要读磁盘，因此放到 IO 上探测，组合期只读结果
    val existsProbe by produceState<Boolean?>(initialValue = null, nameValue) {
        value = if (nameValue == versionName) {
            false
        } else {
            withContext(Dispatchers.IO) { VersionsManager.isVersionExists(nameValue, true) }
        }
    }

    val verdict = oxideModNameVerdict(
        name = nameValue,
        currentName = versionName,
        conflict = existsProbe == true,
        filenameInvalid = filenameInvalid != null,
    )
    val nameError: String? = when (verdict) {
        OxideModNameVerdict.Kept, OxideModNameVerdict.Ok -> null
        OxideModNameVerdict.Empty -> stringResource(R.string.generic_cannot_empty)
        OxideModNameVerdict.Invalid -> filenameInvalid
        OxideModNameVerdict.Conflict -> stringResource(R.string.versions_manage_install_exists)
    }

    val iconState = iconViewModel.state

    val iconLauncher = rememberLauncherForActivityResult(
        contract = MediaPickerContract(allowImages = true, allowVideos = false, allowMultiple = false),
    ) { uris ->
        // The picker always hands back a list, even with allowMultiple = false.
        uris?.firstOrNull()?.let { picked -> iconViewModel.import(context, picked, iconFile) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.secGroupGap),
    ) {
        OxideSection(title = stringResource(R.string.oxide_mod_instance_section)) {
            OxideModBlock(metrics) {
                OxideSecInput(
                    metrics = metrics,
                    label = stringResource(R.string.download_game_version_name),
                    value = nameValue,
                    onValueChange = onNameChange,
                    placeholder = stringResource(R.string.download_game_version_name),
                    isError = nameError != null,
                )
                nameError?.let { text ->
                    Text(
                        text = text,
                        color = Oxide.FgStrong,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.oxide_mod_name_tip),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_mod_icon_section)) {
            OxideModBlock(metrics) {
                val busy = iconState is OxideModIconState.Working || iconState is OxideModIconState.Probing
                val hasIcon = (iconState as? OxideModIconState.Ready)?.exists == true

                OxideSettingRow(
                    // 行标题就是磁盘上那个真实文件名
                    label = iconFile.name,
                    value = when (iconState) {
                        is OxideModIconState.Ready -> if (iconState.exists) {
                            stringResource(R.string.oxide_mod_icon_custom)
                        } else {
                            stringResource(R.string.oxide_mod_icon_none)
                        }

                        else -> stringResource(R.string.oxide_mod_icon_busy)
                    },
                    hint = if (iconState is OxideModIconState.Working) {
                        stringResource(R.string.oxide_common_loading)
                    } else {
                        iconFile.absolutePath
                    },
                    enabled = !busy,
                )

                Spacer(Modifier.height(metrics.secRowGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OxideButton(
                        text = stringResource(R.string.oxide_mod_action_pick_icon),
                        onClick = { iconLauncher.launch(Unit) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    // 真的存在自定义图标时才有得清除
                    OxideButton(
                        text = stringResource(R.string.oxide_mod_action_reset_icon),
                        onClick = { iconViewModel.reset(iconFile) },
                        enabled = hasIcon && !busy,
                        modifier = Modifier.weight(1f),
                    )
                }

                (iconState as? OxideModIconState.Failed)?.let { failed ->
                    Spacer(Modifier.height(metrics.secRowGap))
                    OxideSecErrorRow(
                        metrics = metrics,
                        title = stringResource(R.string.error_import_image),
                        detail = resolveAndroidString(failed.message).text,
                        dismissText = stringResource(R.string.generic_confirm),
                        onDismiss = { iconViewModel.recheck(iconFile) },
                    )
                }
            }
        }
    }
}

/** 实例设置里那套深色分组，与抽屉里的 `.block` 同一套 */
@Composable
private fun OxideModBlock(
    metrics: OxideMetrics,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusBlock)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock)
            .padding(
                start = metrics.secControlPadding,
                end = metrics.secControlPadding,
                top = metrics.secRowGap,
                bottom = metrics.secRowGap,
            ),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 第四步：确认、进度与取消
// ---------------------------------------------------------------------------

@Composable
private fun OxideModApplyStep(
    metrics: OxideMetrics,
    context: Context,
    version: Version,
    originalGameVersion: String,
    targetGameVersion: String,
    supports: LoaderVerSupports,
    addonsViewModel: OxideModAddonsViewModel,
    modifyViewModel: ModifyVersionViewModel,
    plan: List<OxideAddonSlot>,
    diffs: ModifyDiffs?,
    nameValue: String,
    onGotoVersion: () -> Unit,
    onGotoLoader: () -> Unit,
    onGotoInstance: () -> Unit,
    onFinished: () -> Unit,
    onCancelModify: () -> Unit,
    onModifyStarted: () -> Unit,
    onModifyStopped: () -> Unit,
) {
    val currentName = remember(version) { version.getVersionName() }
    val filenameInvalid = isFilenameInvalid(nameValue)
    val existsProbe by produceState<Boolean?>(initialValue = null, nameValue) {
        value = if (nameValue == currentName) {
            false
        } else {
            withContext(Dispatchers.IO) { VersionsManager.isVersionExists(nameValue, true) }
        }
    }
    val verdict = oxideModNameVerdict(
        name = nameValue,
        currentName = currentName,
        conflict = existsProbe == true,
        filenameInvalid = filenameInvalid != null,
    )

    val operation = modifyViewModel.installOperation
    val installer = modifyViewModel.installer
    val tasksFlow = installer?.tasksFlow
    val tasks = if (tasksFlow != null) tasksFlow.collectAsStateWithLifecycle().value else emptyList()
    val logFlow = installer?.logOutput
    val logOutput = if (logFlow != null) logFlow.collectAsStateWithLifecycle().value else null

    val canStart = diffs != null && operation is ModifyOperation.None && oxideModNameAllowsRename(verdict)

    val startModify: (ModifyPayload) -> Unit = { payload ->
        // 不是待修改状态就拒绝这次请求，旧宿主也是这么挡的
        if (modifyViewModel.installOperation is ModifyOperation.None) {
            if (!NotificationManager.checkNotificationEnabled(context)) {
                modifyViewModel.installOperation = ModifyOperation.WarningForNotification(payload)
            } else {
                onModifyStarted()
                modifyViewModel.modify(context, payload)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val pending = modifyViewModel.installOperation
        if (pending is ModifyOperation.WarningForNotification) {
            onModifyStarted()
            modifyViewModel.modify(context, pending.payload)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (operation is ModifyOperation.Install && installer != null) {
            OxideTaskFlowPanel(
                metrics = metrics,
                title = stringResource(R.string.versions_modify_version),
                tasks = tasks,
                logOutput = logOutput,
                onCancel = {
                    onCancelModify()
                    modifyViewModel.installOperation = ModifyOperation.None
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
        ) {
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    OxideSectionLabel(text = stringResource(R.string.versions_modify_confirm_message))
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_inst_chosen_version),
                        value = targetGameVersion,
                        hint = if (targetGameVersion != originalGameVersion) {
                            stringResource(R.string.versions_modify_mc_changed_title)
                        } else {
                            null
                        },
                        onClick = onGotoVersion,
                    )
                    plan.forEach { slot ->
                        val picked = addonsViewModel.selectedOf(slot) ?: return@forEach
                        OxideSettingRow(
                            label = slot.loader.displayName,
                            value = oxideAddonTitle(slot, picked),
                            onClick = onGotoLoader,
                        )
                    }
                    if (plan.none { slot -> addonsViewModel.selectedOf(slot) != null }) {
                        OxideSettingRow(
                            label = stringResource(R.string.oxide_mod_loader_section),
                            value = stringResource(R.string.oxide_mod_review_nothing_installed),
                            onClick = onGotoLoader,
                        )
                    }
                    OxideSettingRow(
                        label = stringResource(R.string.download_game_version_name),
                        value = if (verdict == OxideModNameVerdict.Kept) {
                            stringResource(R.string.oxide_mod_name_kept)
                        } else {
                            nameValue
                        },
                        hint = if (!oxideModNameAllowsRename(verdict)) {
                            when (verdict) {
                                OxideModNameVerdict.Empty -> stringResource(R.string.generic_cannot_empty)
                                OxideModNameVerdict.Invalid -> filenameInvalid
                                OxideModNameVerdict.Conflict ->
                                    stringResource(R.string.versions_manage_install_exists)

                                else -> null
                            }
                        } else {
                            stringResource(R.string.oxide_mod_name_tip)
                        },
                        onClick = onGotoInstance,
                    )
                }
            }

            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    OxideSectionLabel(text = stringResource(R.string.oxide_mod_review_section))
                    if (diffs == null) {
                        Text(
                            text = stringResource(R.string.oxide_mod_review_none),
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        diffs.list.forEach { diff ->
                            OxideModDiffRow(diff = diff)
                        }
                    }
                }
            }

            // 确认与失败都在原地说出来，不用会盖住整块内容的弹窗
            when (operation) {
                is ModifyOperation.WarningForNotification -> OxideSecConfirmBar(
                    metrics = metrics,
                    text = stringResource(R.string.notification_data_jvm_service_message),
                    confirmText = stringResource(R.string.notification_request),
                    dismissText = stringResource(R.string.generic_anyway),
                    onConfirm = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            NotificationManager.openNotificationSettings(context)
                            onModifyStarted()
                            modifyViewModel.modify(context, operation.payload)
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDismiss = { startModify(operation.payload) },
                )

                is ModifyOperation.Error -> OxideSecErrorRow(
                    metrics = metrics,
                    title = stringResource(R.string.download_install_error_title),
                    detail = stringResource(R.string.versions_modify_error_message) +
                        "\n" + modifyErrorDetail(operation.th),
                    dismissText = stringResource(R.string.generic_confirm),
                    onDismiss = {
                        onModifyStopped()
                        modifyViewModel.installOperation = ModifyOperation.None
                    },
                )

                is ModifyOperation.Success -> OxideSecConfirmBar(
                    metrics = metrics,
                    text = stringResource(R.string.versions_modify_success),
                    confirmText = stringResource(R.string.generic_done),
                    dismissText = stringResource(R.string.generic_close),
                    onConfirm = {
                        onModifyStopped()
                        onFinished()
                    },
                    onDismiss = {
                        onModifyStopped()
                        onFinished()
                    },
                )

                else -> Unit
            }
        }

        Spacer(Modifier.height(metrics.secRowGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // 取消 = 丢掉这一页上所有选择并回到实例设置，磁盘上什么都没动
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = {
                    modifyViewModel.installOperation = ModifyOperation.None
                    onModifyStopped()
                    onFinished()
                },
                enabled = operation !is ModifyOperation.Install,
                tone = OxideButtonTone.Ghost,
            )
            Spacer(Modifier.width(8.dp))
            OxideButton(
                text = stringResource(R.string.oxide_mod_start),
                tone = OxideButtonTone.Primary,
                enabled = canStart,
                onClick = {
                    val currentDiffs = diffs ?: return@OxideButton
                    startModify(
                        ModifyPayload(
                            info = oxideModGameDownloadInfo(
                                targetGameVersion = targetGameVersion,
                                currentVersionName = currentName,
                                supports = supports,
                                current = addonsViewModel.currentAddon,
                            ),
                            currentVersion = version,
                            newVersionName = nameValue,
                            diffs = currentDiffs,
                        )
                    )
                },
            )
        }
    }
}

/** 一条变更：装着的版本与新选的版本都说出来，不只靠颜色 */
@Composable
private fun OxideModDiffRow(diff: ModifyDiffs.Diff) {
    when (diff) {
        is ModifyDiffs.McChange -> OxideSettingRow(
            label = stringResource(R.string.oxide_inst_chosen_version),
            value = "${diff.original} → ${diff.updateTo}",
        )

        is ModifyDiffs.LoaderChange -> OxideSettingRow(
            label = diff.modloader.displayName,
            value = "${diff.original} → ${diff.updateTo}",
        )

        is ModifyDiffs.LoaderRemove -> OxideSettingRow(
            label = diff.modloader.displayName,
            value = stringResource(R.string.oxide_mod_loader_remove),
        )

        is ModifyDiffs.LoaderInstall -> OxideSettingRow(
            label = diff.modloader.displayName,
            value = stringResource(R.string.oxide_mod_loader_installed, diff.version),
        )
    }
}

/** 与旧 `ModifyVersionOperation` 逐项一致的错误翻译 */
@Composable
private fun modifyErrorDetail(th: Throwable): String = when (th) {
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