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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.control.ControlManager
import dev.oxide.launcher.game.multirt.RuntimesManager
import dev.oxide.launcher.game.plugin.driver.DriverPluginManager
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.support.touch_controller.VibrationHandler
import dev.oxide.launcher.game.version.installed.GraphicsApi
import dev.oxide.launcher.game.version.installed.SettingState
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionConfig
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionType
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.unit.min
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.content.elements.VersionsOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.platform.getMaxMemoryForSettings
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OxideVersionSettings"

private const val MEMORY_STEP = 256
private const val VIBRATION_MIN = 80
private const val VIBRATION_MAX = 500
private const val VIBRATION_STEP = 20

/** 概览与配置里最后给出的修改时间，用的也是实例列表那一套格式 */
private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"

/**
 * 实例设置
 *
 * 替代旧的 `VersionSettingsScreen` 那一整块标签页宿主：左侧（或顶部）是一列标签，
 * 右边是当前标签的面板。概览与配置两块用 Oxide 的写法重建在真实后端上，
 * 五类内容直接复用 [OxideContentPanel]，因此新界面的任何一处都不再进旧界面。
 *
 * 「修改版本 / 选择 Minecraft 版本」不在这里重做：这两块由另一个页面负责，
 * 这里只给出 [openModifyVersion] 这个出口，接口形状与旧界面一致。
 */
@Composable
fun OxideVersionSettingsPage(
    version: Version,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    openModifyVersion: (Version) -> Unit,
) {
    val eventViewModel = rememberOxideEventViewModel()
    val errorViewModel: ErrorViewModel = viewModel()

    var tab by remember { mutableStateOf(OxideInstanceTab.Overview) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.pagePaddingH)) {
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
                Spacer(Modifier.width(6.dp))
                OxidePageTitle(
                    text = version.getVersionName(),
                    modifier = Modifier.weight(1f),
                )
            }
            OxideSectionLabel(text = stringResource(R.string.oxide_mgr_settings_subtitle))
            Spacer(Modifier.height(metrics.sectionGap))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val sideBySide = maxWidth >= metrics.cardMinWidth * 1.6f
            val railWidth: Dp = (155f * metrics.guiScale).coerceIn(132f, 260f).dp

            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideInstanceTabRail(
                        metrics = metrics,
                        current = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.width(railWidth).fillMaxHeight(),
                    )
                    OxideInstanceTabPanel(
                        metrics = metrics,
                        version = version,
                        tab = tab,
                        eventViewModel = eventViewModel,
                        submitError = { errorViewModel.showError(it) },
                        openModifyVersion = openModifyVersion,
                        onGotoContent = { category ->
                            oxideInstanceTabForCategory(category)?.let { target -> tab = target }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideInstanceTabChips(
                        metrics = metrics,
                        current = tab,
                        onSelect = { tab = it },
                    )
                    OxideInstanceTabPanel(
                        metrics = metrics,
                        version = version,
                        tab = tab,
                        eventViewModel = eventViewModel,
                        submitError = { errorViewModel.showError(it) },
                        openModifyVersion = openModifyVersion,
                        onGotoContent = { category ->
                            oxideInstanceTabForCategory(category)?.let { target -> tab = target }
                        },
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 实例设置的标签页
 *
 * 顺序与旧版那一列标签一致；前两个是概览与配置，后五个直接对应一类内容。
 */
enum class OxideInstanceTab(val titleRes: Int) {
    Overview(R.string.oxide_mgr_tab_overview),
    Config(R.string.oxide_mgr_tab_config),
    Mods(R.string.oxide_mgr_cat_mods),
    ResourcePacks(R.string.oxide_mgr_cat_resource_packs),
    Shaders(R.string.oxide_mgr_cat_shaders),
    Saves(R.string.oxide_mgr_cat_saves),
    Screenshots(R.string.oxide_mgr_cat_screenshots),
    ;

    /** 这一页对应的内容分类；概览与配置不是内容页，返回 null */
    val contentCategory: OxideContentCategory?
        get() = oxideContentCategoryForTab(this)
}

/** 标签 → 内容分类，纯映射，因此可以直接单测 */
internal fun oxideContentCategoryForTab(tab: OxideInstanceTab): OxideContentCategory? = when (tab) {
    OxideInstanceTab.Mods -> OxideContentCategory.Mods
    OxideInstanceTab.ResourcePacks -> OxideContentCategory.ResourcePacks
    OxideInstanceTab.Shaders -> OxideContentCategory.Shaders
    OxideInstanceTab.Saves -> OxideContentCategory.Saves
    OxideInstanceTab.Screenshots -> OxideContentCategory.Screenshots
    OxideInstanceTab.Overview,
    OxideInstanceTab.Config,
    -> null
}

/** 内容分类 → 标签页，纯映射，因此可以直接单测 */
internal fun oxideInstanceTabForCategory(
    category: OxideContentCategory,
): OxideInstanceTab? = OxideInstanceTab.entries.firstOrNull { it.contentCategory == category }

@Composable
private fun OxideInstanceTabRail(
    metrics: OxideMetrics,
    current: OxideInstanceTab,
    onSelect: (OxideInstanceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideInstanceTab.entries.forEach { tab ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.categoryTabHeight)
                    .clip(Oxide.RadiusControl)
                    .background(if (tab == current) Oxide.BgTabActive else Color.Transparent)
                    .selectable(
                        selected = tab == current,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(horizontal = metrics.rowGap * 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(tab.titleRes),
                    color = if (tab == current) Oxide.Fg else Oxide.FgGhost,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OxideInstanceTabChips(
    metrics: OxideMetrics,
    current: OxideInstanceTab,
    onSelect: (OxideInstanceTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        OxideInstanceTab.entries.forEach { tab ->
            val isSelected = tab == current
            OxideSurface(
                modifier = Modifier.selectable(
                    selected = isSelected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelect(tab) },
                ),
                selected = isSelected,
                contentPadding = PaddingValues(
                    horizontal = metrics.cardGap,
                    vertical = metrics.rowGap,
                ),
            ) {
                Text(
                    text = stringResource(tab.titleRes),
                    color = if (isSelected) Oxide.Fg else Oxide.FgGhost,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OxideInstanceTabPanel(
    metrics: OxideMetrics,
    version: Version,
    tab: OxideInstanceTab,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    openModifyVersion: (Version) -> Unit,
    onGotoContent: (OxideContentCategory) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentCategory = oxideContentCategoryForTab(tab)
    if (contentCategory != null) {
        OxideContentPanel(
            metrics = metrics,
            version = version,
            category = contentCategory,
            eventViewModel = eventViewModel,
            submitError = submitError,
            // 内容面板自己带这一层横向留白，这里不能再加一次
            modifier = modifier,
        )
        return
    }

    when (tab) {
        OxideInstanceTab.Overview -> OxideVersionOverviewTab(
            metrics = metrics,
            version = version,
            eventViewModel = eventViewModel,
            submitError = submitError,
            openModifyVersion = openModifyVersion,
            onGotoContent = onGotoContent,
            onDismiss = onDismiss,
            modifier = modifier.padding(horizontal = metrics.pagePaddingH),
        )

        else -> OxideVersionConfigTab(
            metrics = metrics,
            version = version,
            eventViewModel = eventViewModel,
            submitError = submitError,
            modifier = modifier.padding(horizontal = metrics.pagePaddingH),
        )
    }
}

// ---------------------------------------------------------------------------
// 概览
// ---------------------------------------------------------------------------

/** 概览要读的那些真实状态，全部在 IO 上一次取回 */
private class VersionOverviewProbe(
    val valid: Boolean,
    val isolated: Boolean,
    val modsCount: Int,
    val resourcePacksCount: Int,
    val shadersCount: Int,
    val savesCount: Int,
    val screenshotsCount: Int,
    val iconExists: Boolean,
    val logModifiedAt: Long,
)

private fun probeVersionOverview(version: Version): VersionOverviewProbe {
    val gameDir = version.getGameDir()
    // 模组、资源包、光影、截图都是文件；存档是文件夹——数错目录类型就会得到一个假计数
    fun files(folder: VersionFolders): Int =
        folder.getDir(gameDir).listFiles()?.count { it.isFile } ?: 0

    fun dirs(folder: VersionFolders): Int =
        folder.getDir(gameDir).listFiles()?.count { it.isDirectory } ?: 0

    return VersionOverviewProbe(
        valid = version.isValid(),
        isolated = version.isIsolation(),
        modsCount = files(VersionFolders.MOD),
        resourcePacksCount = files(VersionFolders.RESOURCE_PACK),
        shadersCount = files(VersionFolders.SHADERS),
        savesCount = dirs(VersionFolders.SAVES),
        screenshotsCount = files(VersionFolders.SCREENSHOTS),
        iconExists = version.getVersionIconFile().exists(),
        logModifiedAt = version.getLatestLog().lastModified(),
    )
}

@Composable
private fun OxideVersionOverviewTab(
    metrics: OxideMetrics,
    version: Version,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    openModifyVersion: (Version) -> Unit,
    onGotoContent: (OxideContentCategory) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var probe by remember(version) { mutableStateOf<VersionOverviewProbe?>(null) }
    LaunchedEffect(version) {
        probe = withContext(Dispatchers.IO) { probeVersionOverview(version) }
    }

    var operation by remember(version) { mutableStateOf<VersionsOperation>(VersionsOperation.None) }

    val versionInfo = remember(version) { version.getVersionInfo() }
    val gameDir = remember(version) { version.getGameDir() }

    val openFolder: (File) -> Unit = { dir ->
        eventViewModel.sendEvent(EventViewModel.Event.OpenFileManager(rootPath = dir.absolutePath))
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.secGroupGap),
    ) {
        OxideSection(title = stringResource(R.string.oxide_ins_section_identity)) {
            OxideInstanceBlock {
                OxideSettingRow(
                    label = stringResource(R.string.oxide_ins_row_instance_name),
                    value = version.getVersionName(),
                )
                versionInfo?.minecraftVersion?.takeIf { it.isNotBlank() }?.let { minecraft ->
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_ins_row_minecraft_version),
                        value = minecraft,
                    )
                }
                val loaders = versionInfo?.loaderInfos
                    ?.map { loader -> "${loader.loader.displayName} ${loader.version}".trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                if (loaders.isNotEmpty()) {
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_ins_row_loader),
                        value = loaders.joinToString(" + "),
                    )
                }
                OxideSettingRow(
                    label = stringResource(R.string.oxide_ins_row_version_type),
                    value = stringResource(
                        when (version.versionType) {
                            VersionType.VANILLA -> R.string.oxide_ins_type_vanilla
                            VersionType.MODLOADERS -> R.string.oxide_ins_type_modded
                            VersionType.UNKNOWN -> R.string.oxide_ins_type_unknown
                        }
                    ),
                )
                OxideSettingRow(
                    label = stringResource(R.string.oxide_mgr_row_game_home),
                    value = version.getGameHome(),
                    onClick = { openFolder(File(version.getGameHome())) },
                )
                OxideSettingRow(
                    label = stringResource(R.string.oxide_mgr_row_game_dir),
                    value = gameDir.absolutePath,
                    onClick = { openFolder(gameDir) },
                )
                OxideSettingRow(
                    label = stringResource(R.string.oxide_ins_row_version_folder),
                    value = version.getVersionsFolder(),
                    onClick = { openFolder(version.getVersionPath()) },
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_mgr_section_state)) {
            OxideInstanceBlock {
                // 还没有探到时不写任何状态：宁可空着也不先给一个假的
                val current = probe
                if (current == null) {
                    OxideLoadingRow(text = stringResource(R.string.oxide_mgr_loading))
                } else {
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_mgr_row_validity),
                        value = stringResource(
                            if (current.valid) {
                                R.string.generic_enabled
                            } else {
                                R.string.oxide_ins_badge_invalid
                            }
                        ),
                    )
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_ins_row_isolation),
                        value = stringResource(
                            if (current.isolated) {
                                R.string.oxide_ins_badge_isolated
                            } else {
                                R.string.oxide_ins_badge_shared
                            }
                        ),
                    )
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_ins_row_memory),
                        value = stringResource(R.string.oxide_ins_mb, version.getRamAllocation()),
                    )
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_ins_row_log_file),
                        value = version.getLatestLog().name,
                        hint = if (current.logModifiedAt > 0L) {
                            formatDate(current.logModifiedAt, DATE_PATTERN)
                        } else {
                            stringResource(R.string.oxide_ins_log_missing)
                        },
                    )
                    // 图标是真的存在才给入口，不存在就整行不画
                    if (current.iconExists) {
                        OxideActionRow(
                            label = stringResource(R.string.oxide_mgr_action_open_icon),
                            hint = version.getVersionIconFile().absolutePath,
                            onClick = {
                                openFolder(version.getOxideVersionPath())
                            },
                        )
                    }
                }
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_tab_content)) {
            OxideInstanceBlock {
                val current = probe
                if (current == null) {
                    OxideLoadingRow(text = stringResource(R.string.oxide_mgr_loading))
                } else {
                    listOf(
                        OxideContentCategory.Mods to current.modsCount,
                        OxideContentCategory.ResourcePacks to current.resourcePacksCount,
                        OxideContentCategory.Shaders to current.shadersCount,
                        OxideContentCategory.Saves to current.savesCount,
                        OxideContentCategory.Screenshots to current.screenshotsCount,
                    ).forEach { (category, count) ->
                        OxideSettingRow(
                            label = stringResource(category.titleRes),
                            value = stringResource(R.string.oxide_mgr_count, count),
                            // 只有确实映射得到标签页的那一类才可点
                            onClick = if (oxideInstanceTabForCategory(category) != null) {
                                { onGotoContent(category) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_section_actions)) {
            OxideInstanceBlock {
                OxideButton(
                    text = stringResource(R.string.oxide_mgr_action_launch),
                    onClick = {
                        eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(version))
                    },
                    enabled = probe?.valid == true,
                    tone = OxideButtonTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(metrics.secRowGap))
                // 修改版本 / 选择 Minecraft 版本由另一块表面负责，这里只给出口
                OxideButton(
                    text = stringResource(R.string.oxide_mgr_action_modify_version),
                    onClick = { openModifyVersion(version) },
                    enabled = probe?.valid == true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(metrics.secRowGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OxideButton(
                        text = stringResource(R.string.oxide_ins_action_rename),
                        onClick = { operation = VersionsOperation.Rename(version) },
                        modifier = Modifier.weight(1f),
                    )
                    OxideButton(
                        text = stringResource(R.string.oxide_ins_action_copy),
                        onClick = { operation = VersionsOperation.Copy(version) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(metrics.secRowGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OxideButton(
                        text = stringResource(R.string.oxide_mgr_action_share_log),
                        onClick = {
                            eventViewModel.sendEvent(
                                EventViewModel.Event.LogShare.ShareGameLog(version.getLatestLog())
                            )
                        },
                        enabled = probe?.logModifiedAt?.let { it > 0L } == true,
                        modifier = Modifier.weight(1f),
                    )
                    // 删除是清理无效版本的唯一途径，因此不受有效性限制
                    OxideButton(
                        text = stringResource(R.string.oxide_ins_action_delete),
                        onClick = { operation = VersionsOperation.Delete(version) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    VersionsOperation(
        versionsOperation = operation,
        updateVersionsOperation = { operation = it },
        submitError = submitError,
    )

    // 改名或删除之后这个对象已经不在真实列表里了，收起这一页，
    // 免得继续往一个已改名的目录上读配置
    val liveVersions by VersionsManager.versions.collectAsStateWithLifecycle()
    val versionName = version.getVersionName()
    LaunchedEffect(liveVersions, versionName) {
        if (liveVersions.isNotEmpty() && liveVersions.none { it.getVersionName() == versionName }) {
            onDismiss()
        }
    }
}

/** 概览与配置共用的深色分组，与抽屉里的 `.block` 同一套 */
@Composable
private fun OxideInstanceBlock(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusBlock)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 配置
// ---------------------------------------------------------------------------

/**
 * 配置页正在编辑的那一份 [VersionConfig]
 *
 * 这些字段是普通 var，改完不会通知组合，所以先镜像到 Compose 状态，
 * 落盘前由 [writeBack] 一次性写回；只写回真正被改过的字段，
 * 免得把"跟随全局"这种空值悄悄变成显式值。
 */
@Stable
private class VersionSettingsState(private val config: VersionConfig) {

    var isolation by mutableStateOf(config.isolationType)
    var integrity by mutableStateOf(config.skipGameIntegrityCheck)
    var javaRuntime by mutableStateOf(config.javaRuntime)
    var ramAllocation by mutableIntStateOf(config.ramAllocation)
    var renderer by mutableStateOf(config.renderer)
    var graphicsApi by mutableStateOf(config.graphicsApi)
    var driver by mutableStateOf(config.driver)
    var control by mutableStateOf(config.control)
    var vibrateKind by mutableStateOf(
        config.touchVibrateKind ?: VibrationHandler.VibrateKind.default
    )
    var vibrateDuration by mutableIntStateOf(config.touchVibrateDuration)

    var jvmArgs by mutableStateOf(config.jvmArgs)
    var gameArgs by mutableStateOf(config.gameArgs)
    var customInfo by mutableStateOf(config.customInfo)
    var serverIp by mutableStateOf(config.serverIp)

    private val base = config.copy()
    private val baseVibrateKind = base.touchVibrateKind ?: VibrationHandler.VibrateKind.default

    /** 写回被改动的字段，然后由调用方落盘 */
    fun writeBack() {
        if (isolation != base.isolationType) config.isolationType = isolation
        if (integrity != base.skipGameIntegrityCheck) config.skipGameIntegrityCheck = integrity
        if (javaRuntime != base.javaRuntime) config.javaRuntime = javaRuntime
        if (ramAllocation != base.ramAllocation) config.ramAllocation = ramAllocation
        if (renderer != base.renderer) config.renderer = renderer
        if (graphicsApi != base.graphicsApi) config.graphicsApi = graphicsApi
        if (driver != base.driver) config.driver = driver
        if (control != base.control) config.control = control
        if (vibrateKind != baseVibrateKind) config.touchVibrateKind = vibrateKind
        if (vibrateDuration != base.touchVibrateDuration) config.touchVibrateDuration = vibrateDuration
        if (jvmArgs != base.jvmArgs) config.jvmArgs = jvmArgs
        if (gameArgs != base.gameArgs) config.gameArgs = gameArgs
        if (customInfo != base.customInfo) config.customInfo = customInfo
        if (serverIp != base.serverIp) config.serverIp = serverIp
    }
}

@Composable
private fun OxideVersionConfigTab(
    metrics: OxideMetrics,
    version: Version,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = remember(version) { version.getVersionConfig() }
    val state = remember(version) { VersionSettingsState(config) }

    val saveTitle = androidText(R.string.oxide_ins_save_failed_title)
    // 与既有 `VersionConfigScreen` 完全相同的落盘方式与失败提示
    val save: () -> Unit = {
        state.writeBack()
        runCatching { config.saveWithThrowable() }.onFailure { e ->
            Logger.error(TAG, "Failed to save the version configuration.", e)
            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = saveTitle,
                    message = androidText(e.getMessageOrToString()),
                )
            )
        }
    }

    val followGlobal = stringResource(R.string.oxide_ins_follow_global)
    val renderers = remember { Renderers.getRenderers() }
    val rendererIds = remember(renderers) {
        listOf("") + renderers.map { renderer -> renderer.getUniqueIdentifier() }
    }
    val rendererLabels = remember(renderers, followGlobal) {
        listOf(followGlobal) + renderers.map { renderer -> renderer.getRendererName() }
    }
    val drivers = remember { DriverPluginManager.getDriverList() }
    val driverIds = remember(drivers) { listOf("") + drivers.map { driver -> driver.id } }
    val driverLabels = remember(drivers, followGlobal) {
        listOf(followGlobal) + drivers.map { driver -> driver.name }
    }
    val controls by ControlManager.dataList.collectAsStateWithLifecycle()
    val controlList = remember(controls) { controls.filter { it.isSupport } }
    val controlIds = remember(controlList) { listOf("") + controlList.map { it.file.name } }
    val controlLabels = remember(controlList, followGlobal) {
        listOf(followGlobal) + controlList.map { it.controlLayout.info.name.translate() }
    }
    val runtimeNames = remember(version) {
        runCatching {
            RuntimesManager.getRuntimes()
                .filter { runtime -> runtime.isCompatible() }
                .map { runtime -> runtime.name }
        }.getOrDefault(emptyList())
    }

    val graphicsApis = remember { GraphicsApi.entries.toList() }
    val graphicsLabels = graphicsApis.map { api ->
        when (api) {
            GraphicsApi.DEFAULT -> stringResource(R.string.oxide_ins_graphics_default)
            GraphicsApi.DEFAULT_OPENGL -> stringResource(R.string.oxide_ins_graphics_default_opengl)
            else -> api.displayName
        }
    }

    val minMemory = AllSettings.ramAllocation.min
    val maxMemory = remember(context) { getMaxMemoryForSettings(context) }
    val memoryValues = remember(maxMemory, minMemory, state.ramAllocation) {
        val steps = (minMemory..maxMemory step MEMORY_STEP).toList()
        val current = state.ramAllocation
        if (current >= minMemory && current !in steps) (steps + current).sorted() else steps
    }
    val memoryLabels = memoryValues.map { value -> stringResource(R.string.oxide_ins_mb, value) }

    val vibrateKinds = remember { VibrationHandler.VibrateKind.entries.toList() }
    val vibrateLabels = vibrateKinds.map { kind ->
        stringResource(
            when (kind) {
                VibrationHandler.VibrateKind.ONE_SHOT -> R.string.oxide_ins_vibrate_one_shot
                VibrationHandler.VibrateKind.CLICK -> R.string.oxide_ins_vibrate_click
                VibrationHandler.VibrateKind.DOUBLE_CLICK -> R.string.oxide_ins_vibrate_double_click
                VibrationHandler.VibrateKind.HEAVY_CLICK -> R.string.oxide_ins_vibrate_heavy_click
                VibrationHandler.VibrateKind.TICK -> R.string.oxide_ins_vibrate_tick
            }
        )
    }
    val durationValues = remember(state.vibrateDuration) {
        val steps = (VIBRATION_MIN..VIBRATION_MAX step VIBRATION_STEP).toList()
        val current = state.vibrateDuration.coerceIn(VIBRATION_MIN, VIBRATION_MAX)
        if (current in steps) steps else (steps + current).sorted()
    }
    val durationLabels = durationValues.map { value -> stringResource(R.string.oxide_ins_ms, value) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.secGroupGap),
    ) {
        OxideSection(title = stringResource(R.string.oxide_mgr_section_version_config)) {
            OxideInstanceBlock {
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_isolation),
                    hint = stringResource(R.string.oxide_ins_hint_isolation),
                    options = SettingState.entries.map { entry -> stringResource(entry.textRes) },
                    selectedIndex = SettingState.entries.indexOf(state.isolation).coerceAtLeast(0),
                    onSelect = { index ->
                        state.isolation = SettingState.entries[index]
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_integrity),
                    hint = stringResource(R.string.oxide_ins_hint_integrity),
                    options = SettingState.entries.map { entry -> stringResource(entry.textRes) },
                    selectedIndex = SettingState.entries.indexOf(state.integrity).coerceAtLeast(0),
                    onSelect = { index ->
                        state.integrity = SettingState.entries[index]
                        save()
                    },
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_section_runtime)) {
            OxideInstanceBlock {
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_java),
                    hint = stringResource(R.string.oxide_ins_hint_java),
                    options = runtimeNames,
                    selectedIndex = runtimeNames.indexOf(state.javaRuntime).coerceAtLeast(0),
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.javaRuntime = runtimeNames.getOrElse(index) { "" }
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_memory),
                    hint = stringResource(R.string.oxide_ins_hint_memory),
                    options = memoryLabels,
                    // 第 0 项是"跟随全局"，因此真实档位要往后挪一格
                    selectedIndex = memoryValues.indexOf(state.ramAllocation)
                        .let { index -> if (index >= 0) index + 1 else 0 },
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.ramAllocation = memoryValues.getOrElse(index - 1) { -1 }
                        save()
                    },
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_section_graphics)) {
            OxideInstanceBlock {
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_renderer),
                    hint = stringResource(R.string.oxide_ins_hint_renderer),
                    options = rendererLabels,
                    selectedIndex = rendererIds.indexOfFirst { id -> id == state.renderer }
                        .let { index -> if (index >= 0) index else 0 },
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.renderer = rendererIds.getOrElse(index) { "" }
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_graphics_api),
                    hint = stringResource(R.string.oxide_ins_hint_graphics_api),
                    options = graphicsLabels,
                    selectedIndex = graphicsApis.indexOfFirst { api -> api == state.graphicsApi }
                        .let { index -> if (index >= 0) index else 0 },
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.graphicsApi = graphicsApis.getOrNull(index)
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_vulkan_driver),
                    hint = stringResource(R.string.oxide_ins_hint_vulkan_driver),
                    options = driverLabels,
                    selectedIndex = driverIds.indexOfFirst { id -> id == state.driver }
                        .let { index -> if (index >= 0) index else 0 },
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.driver = driverIds.getOrElse(index) { "" }
                        save()
                    },
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_section_controls)) {
            OxideInstanceBlock {
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_control),
                    hint = stringResource(R.string.oxide_ins_hint_control),
                    options = controlLabels,
                    selectedIndex = controlIds.indexOfFirst { id -> id == state.control }
                        .let { index -> if (index >= 0) index else 0 },
                    placeholder = followGlobal,
                    onSelect = { index ->
                        state.control = controlIds.getOrElse(index) { "" }
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceDropdownRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_vibration),
                    hint = stringResource(R.string.oxide_ins_hint_vibration),
                    options = vibrateLabels,
                    selectedIndex = vibrateKinds.indexOfFirst { kind -> kind == state.vibrateKind }
                        .let { index -> if (index >= 0) index else 0 },
                    onSelect = { index ->
                        state.vibrateKind = vibrateKinds[index]
                        save()
                    },
                )
                if (state.vibrateKind == VibrationHandler.VibrateKind.ONE_SHOT) {
                    Spacer(Modifier.height(metrics.secRowGap))
                    OxideInstanceDropdownRow(
                        metrics = metrics,
                        label = stringResource(R.string.oxide_ins_row_vibration_time),
                        hint = stringResource(R.string.oxide_ins_hint_vibration_time),
                        options = durationLabels,
                        selectedIndex = durationValues.indexOfFirst { value ->
                            value == state.vibrateDuration
                        }.let { index -> if (index >= 0) index else 0 },
                        onSelect = { index ->
                            state.vibrateDuration = durationValues[index]
                            save()
                        },
                    )
                }
            }
        }

        OxideSection(title = stringResource(R.string.oxide_ins_section_arguments)) {
            OxideInstanceBlock {
                OxideInstanceTextRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_jvm_args),
                    hint = stringResource(R.string.oxide_ins_hint_jvm_args),
                    value = state.jvmArgs,
                    onValueChange = { text ->
                        state.jvmArgs = text
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceTextRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_game_args),
                    hint = stringResource(R.string.oxide_ins_hint_game_args),
                    value = state.gameArgs,
                    onValueChange = { text ->
                        state.gameArgs = text
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceTextRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_custom_info),
                    hint = stringResource(R.string.oxide_ins_hint_custom_info),
                    value = state.customInfo,
                    onValueChange = { text ->
                        state.customInfo = text
                        save()
                    },
                )
                Spacer(Modifier.height(metrics.secRowGap))
                OxideInstanceTextRow(
                    metrics = metrics,
                    label = stringResource(R.string.oxide_ins_row_server_ip),
                    hint = stringResource(R.string.oxide_ins_hint_server_ip),
                    value = state.serverIp,
                    onValueChange = { text ->
                        state.serverIp = text
                        save()
                    },
                )
            }
        }

        OxideSection(title = stringResource(R.string.oxide_mgr_section_support)) {
            OxideInstanceBlock {
                OxideActionRow(
                    label = stringResource(R.string.game_vulkan_check_title),
                    hint = stringResource(R.string.game_vulkan_check_text),
                    onClick = {
                        eventViewModel.sendEvent(EventViewModel.Event.VulkanCheck(version))
                    },
                )
            }
        }
    }
}

/** 一个带说明的下拉项；选项面板走 Popup，不会被面板或卡片裁掉 */
@Composable
private fun OxideInstanceDropdownRow(
    metrics: OxideMetrics,
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String = "",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        OxideDropdown(
            label = "",
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
        )
        hint?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一个就地编辑的单行输入，值一变就交给调用方写回真实的配置对象 */
@Composable
private fun OxideInstanceTextRow(
    metrics: OxideMetrics,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.secInputHeight)
                .clip(Oxide.RadiusControl)
                .background(
                    Brush.verticalGradient(listOf(Oxide.BgButton, Oxide.BgElevated))
                )
                .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Oxide.Type.Body.copy(color = Oxide.Fg),
                cursorBrush = SolidColor(Oxide.FgMuted),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        hint?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}