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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.layercontroller.data.lang.createTranslatable
import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.layout.EmptyLayoutInfo
import dev.oxide.layercontroller.utils.AUTHOR_NAME_LENGTH
import dev.oxide.layercontroller.utils.NAME_LENGTH
import dev.oxide.layercontroller.utils.VERSION_NAME_LENGTH
import dev.oxide.layercontroller.utils.newRandomFileName
import dev.oxide.layercontroller.utils.saveToFile
import dev.oxide.launcher.BuildConfig
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.R
import dev.oxide.launcher.contract.extensionToMimeType
import dev.oxide.launcher.game.control.ControlData
import dev.oxide.launcher.game.control.ControlManager
import dev.oxide.launcher.game.plugin.PluginLoader
import dev.oxide.launcher.library.LibraryInfo
import dev.oxide.launcher.library.libraryData
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.path.URL_COMMUNITY
import dev.oxide.launcher.path.URL_GITHUB_DRIVER_PLUGINS
import dev.oxide.launcher.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import dev.oxide.launcher.path.URL_GITHUB_RENDERER_PLUGINS
import dev.oxide.launcher.path.URL_MCMOD
import dev.oxide.launcher.path.URL_PLUS
import dev.oxide.launcher.path.URL_PROJECT
import dev.oxide.launcher.path.URL_WEBLATE
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.activities.startEditorActivity
import dev.oxide.launcher.ui.components.SimpleAlertDialog
import dev.oxide.launcher.ui.components.SimpleEditDialog
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val ABOUT_TAG = "OxideAboutPanels"

/** MCIM 的镜像站地址：致谢里给出的就是它自己的页面，旧界面也是指向这里 */
private const val URL_MCIM: String = "https://www.mcimirror.top/sponsor"

// ---------------------------------------------------------------------------
// 关于面板的纯逻辑
//
// 下面这几样都不读组合状态，因此可以被单元测试逐条钉死：
// 致谢名单、署名义务，以及"产品版本 ≠ 构建身份"这条硬规则。
// ---------------------------------------------------------------------------

/**
 * 一条致谢 / 署名
 *
 * @param key 稳定标识，测试按它断言"某一条必须在名单里"
 * @param titleRes 标题；固定的项目名也走字符串资源，不在代码里写字面量
 * @param detailRes 说明
 * @param takesLauncherName [detailRes] 里带 `%s`，需要填启动器名；不带就别填，
 *   否则 Android 的格式化会把多余的参数丢掉并在 lint 上报错
 * @param url 项目主页，没有就不给按钮——不给按钮好过给一个编出来的地址
 * @param licenseRaw 可以打开的 `R.raw` 协议全文，0 表示没有
 */
internal data class OxideAboutEntry(
    val key: String,
    val titleRes: Int,
    val detailRes: Int,
    val takesLauncherName: Boolean = true,
    val url: String? = null,
    val licenseRaw: Int = 0,
)

/**
 * 必须出现在关于面板里的账号 / 认证代码署名
 *
 * GPLv3 §5(a)/§6：账号与认证行为改编自 [URL_PLUS]，署名字符串 `about_launcher_account_code_text`
 * 写明了 Star1xr。这条是**义务**而不是旧产品 branding，因此它进名单时单独一个键，
 * 并且被 [oxideAboutEntries] 放在最前面——任何精简名单的改动都会先撞到它。
 */
internal const val OXIDE_ABOUT_ACCOUNT_CODE_KEY: String = "account-code"

/**
 * 关于面板要列出的致谢名单
 *
 * 名单只增不减：任何一条被删掉都意味着少了 GPL 要求的署名，或者少了别人允许我们
 * 复用代码的凭据。名单里刻意**没有**任何捐赠入口——旧产品把作者自己的头像与捐赠页
 * 摆在致谢的第一条，那是那个产品的创作者展示，与本项目无关。
 *
 * 纯函数：链接全部取自 `path/UrlManager.kt` 里的常量，测试可以逐条比对。
 */
internal fun oxideAboutEntries(): List<OxideAboutEntry> = listOf(
    // GPLv3 §5(a)/§6：改编来的账号代码必须署名
    OxideAboutEntry(
        key = OXIDE_ABOUT_ACCOUNT_CODE_KEY,
        titleRes = R.string.about_launcher_account_code_title,
        detailRes = R.string.about_launcher_account_code_text,
        takesLauncherName = false,
        url = URL_PLUS,
    ),
    OxideAboutEntry(
        key = "pojav",
        titleRes = R.string.oxide_about_ack_pojav_title,
        detailRes = R.string.about_acknowledgements_pojav_text,
        url = "https://github.com/PojavLauncherTeam/PojavLauncher",
        licenseRaw = R.raw.lgpl_3_license,
    ),
    OxideAboutEntry(
        key = "pcl2",
        titleRes = R.string.oxide_about_ack_pcl2_title,
        detailRes = R.string.about_acknowledgements_pcl_text,
        url = "https://github.com/Meloong-Git/PCL",
    ),
    OxideAboutEntry(
        key = "fcl",
        titleRes = R.string.oxide_about_ack_fcl_title,
        detailRes = R.string.about_acknowledgements_fcl_text,
        url = "https://github.com/FCL-Team/FoldCraftLauncher",
        licenseRaw = R.raw.fcl_license,
    ),
    OxideAboutEntry(
        key = "hmcl",
        titleRes = R.string.oxide_about_ack_hmcl_title,
        detailRes = R.string.about_acknowledgements_hmcl_text,
        url = "https://github.com/HMCL-dev/HMCL",
        licenseRaw = R.raw.hmcl_license,
    ),
    OxideAboutEntry(
        key = "mcmod",
        titleRes = R.string.about_acknowledgements_mcmod,
        detailRes = R.string.about_acknowledgements_mcmod_text,
        url = URL_MCMOD,
    ),
    // BMCL：保留镜像源致谢，但不再有作者头像与捐赠按钮
    OxideAboutEntry(
        key = "bmcl",
        titleRes = R.string.oxide_about_ack_bmcl_title,
        detailRes = R.string.about_acknowledgements_bangbang93_text,
    ),
    OxideAboutEntry(
        key = "mcim",
        titleRes = R.string.oxide_about_ack_mcim_title,
        detailRes = R.string.about_acknowledgements_mcim_text,
        url = URL_MCIM,
    ),
    OxideAboutEntry(
        key = "weblate",
        titleRes = R.string.about_acknowledgements_weblate_community,
        detailRes = R.string.about_acknowledgements_weblate_community_text,
        takesLauncherName = false,
        url = URL_WEBLATE,
    ),
    OxideAboutEntry(
        key = "github-community",
        titleRes = R.string.about_acknowledgements_github_community,
        detailRes = R.string.about_acknowledgements_github_community_text,
        takesLauncherName = false,
        url = URL_COMMUNITY,
    ),
)

/**
 * 插件项目的致谢
 *
 * 渲染器、Vulkan 驱动与本地库都是可选插件，它们各自的代码属于各自的项目，
 * 因此这三行同样属于"我们复用了别人的东西"，必须列出。
 */
internal fun oxideAboutPluginProjectEntries(): List<OxideAboutEntry> = listOf(
    OxideAboutEntry(
        key = "renderer-plugins",
        titleRes = R.string.oxide_about_ack_renderer_plugins_title,
        detailRes = R.string.oxide_about_ack_renderer_plugins_detail,
        url = URL_GITHUB_RENDERER_PLUGINS,
    ),
    OxideAboutEntry(
        key = "driver-plugins",
        titleRes = R.string.oxide_about_ack_driver_plugins_title,
        detailRes = R.string.oxide_about_ack_driver_plugins_detail,
        url = URL_GITHUB_DRIVER_PLUGINS,
    ),
    OxideAboutEntry(
        key = "native-lib-plugins",
        titleRes = R.string.oxide_about_ack_native_lib_plugins_title,
        detailRes = R.string.oxide_about_ack_native_lib_plugins_detail,
        url = URL_GITHUB_NATIVE_LIB_PLUGINS,
    ),
)

/**
 * 可以打开协议全文的那几条
 *
 * 只列出真实存在于 `res/raw` 的协议；没有对应文本的条目不会在这里出现，
 * 因此面板上不会出现一个点开是空白的按钮。
 */
internal fun oxideAboutLicenseEntries(entries: List<OxideAboutEntry>): List<OxideAboutEntry> =
    entries.filter { it.licenseRaw != 0 }

/**
 * 关于面板显示的产品版本
 *
 * 必须是 [BuildKeys.LAUNCHER_DISPLAY_VERSION]：GitHub 上的发行号与
 * `BuildConfig.VERSION_NAME` 是**构建身份**，同一份源码上它们会各不相同，
 * 拿来当"启动器版本"报给用户等于报错版本。[buildVersionName] 只是被显式传进来
 * 好让这条规则可以被测试钉住，函数本身完全不理它。
 */
internal fun oxideAboutProductVersion(
    displayVersion: String,
    @Suppress("UNUSED_PARAMETER") buildVersionName: String,
): String = displayVersion

/** 控制布局那一块在面板里占多大宽度：[availableWidth] 够宽才并排 */
internal fun oxideControlLayoutsSideBySide(availableWidth: Dp, cardMinWidth: Dp): Boolean =
    availableWidth >= cardMinWidth * 1.8f

// ---------------------------------------------------------------------------
// 关于面板
// ---------------------------------------------------------------------------

/**
 * 关于面板
 *
 * 取代 `AboutInfoScreen`（以及它背后的 `NestedNavKey.Settings.AboutInfo` 嵌套栈）：
 * 旧界面带的是它上游那套图标顶栏与粉色强调色强调轨，和新界面的配色是两套语言。
 *
 * 面板里仍然必须说清楚三件事：
 * 1. 这是什么产品、**哪个版本**（[oxideAboutProductVersion]，只看产品版本，不看构建身份）；
 * 2. 检查更新与项目链接这两个真实去处；
 * 3. 完整的致谢与署名——包括改编来的账号代码（[OXIDE_ABOUT_ACCOUNT_CODE_KEY]）。
 *
 * 所有尺寸来自 [metrics]，页面自身不写死 dp；竖向滚动，因此在 640x360 的小横屏上
 * 也只是需要滚动，不会裁切或重叠。
 */
@Composable
fun OxideAboutPanel(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    openLicense: (raw: Int) -> Unit,
) {
    val bridge = rememberOxideLauncherBridge()
    val launcherName = BuildKeys.LAUNCHER_NAME
    // 产品版本与构建身份是两回事：这里只显示产品版本
    val productVersion = remember(launcherName) {
        oxideAboutProductVersion(
            displayVersion = BuildKeys.LAUNCHER_DISPLAY_VERSION,
            buildVersionName = BuildConfig.VERSION_NAME,
        )
    }

    val entries = remember { oxideAboutEntries() }
    val pluginProjects = remember { oxideAboutPluginProjectEntries() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Oxide.Bg)
    ) {
        OxideAboutHeader(
            metrics = metrics,
            onDismiss = onDismiss,
            trailing = {
                OxideButton(
                    text = stringResource(R.string.oxide_set_action_check_update),
                    onClick = bridge.checkUpdate,
                    tone = OxideButtonTone.Secondary,
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = metrics.pagePaddingH,
                    end = metrics.pagePaddingH,
                    bottom = Oxide.PagePaddingB,
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.groupGap),
        ) {
            // ---- 这个产品是什么、是什么版本 ----
            OxideSettingsGroup(
                title = stringResource(R.string.oxide_about_section_launcher),
                metrics = metrics,
            ) {
                OxideSettingRow(
                    label = launcherName,
                    hint = stringResource(R.string.oxide_about_product_version_hint),
                    value = productVersion,
                )
                OxideTextBlock(
                    metrics = metrics,
                    title = stringResource(R.string.about_launcher_modified_title),
                    text = stringResource(R.string.about_launcher_modified_text),
                )
            }

            // ---- 两个真实去处 ----
            OxideSettingsGroup(
                title = stringResource(R.string.oxide_about_section_actions),
                metrics = metrics,
            ) {
                OxideActionRow(
                    label = stringResource(R.string.oxide_set_action_check_update),
                    hint = stringResource(R.string.oxide_set_action_check_update_detail),
                    onClick = bridge.checkUpdate,
                )
                OxideActionRow(
                    label = stringResource(R.string.about_launcher_project_link),
                    hint = stringResource(R.string.oxide_about_project_link_detail),
                    onClick = { bridge.openLink(URL_PROJECT) },
                )
            }

            // ---- 协议全文：只列 res/raw 里真实存在的那些 ----
            val licenseEntries = remember(entries) { oxideAboutLicenseEntries(entries) }
            if (licenseEntries.isNotEmpty()) {
                OxideSettingsGroup(
                    title = stringResource(R.string.oxide_about_section_licences),
                    metrics = metrics,
                ) {
                    licenseEntries.forEach { entry ->
                        OxideActionRow(
                            label = stringResource(entry.titleRes),
                            hint = aboutDetailText(entry, launcherName),
                            value = stringResource(R.string.oxide_about_read_licence),
                            onClick = { openLicense(entry.licenseRaw) },
                        )
                    }
                }
            }

            // ---- 致谢：名单只增不减 ----
            OxideSettingsGroup(
                title = stringResource(R.string.about_acknowledgements_title),
                metrics = metrics,
            ) {
                entries.forEach { entry ->
                    OxideAboutEntryRow(
                        entry = entry,
                        launcherName = launcherName,
                        openLicense = openLicense,
                        openLink = bridge.openLink,
                    )
                }
            }

            // ---- 插件项目 ----
            OxideSettingsGroup(
                title = stringResource(R.string.oxide_about_section_plugin_projects),
                metrics = metrics,
            ) {
                pluginProjects.forEach { entry ->
                    OxideAboutEntryRow(
                        entry = entry,
                        launcherName = launcherName,
                        openLicense = openLicense,
                        openLink = bridge.openLink,
                    )
                }
            }

            // ---- 已加载的 APK 插件：只列已经真的加载进来的 ----
            val plugins = remember { PluginLoader.allPlugins }
            if (plugins.isNotEmpty()) {
                OxideSettingsGroup(
                    title = stringResource(R.string.about_plugin_title),
                    metrics = metrics,
                ) {
                    plugins.forEach { plugin ->
                        OxideSettingRow(
                            label = plugin.appName,
                            hint = plugin.packageName,
                            value = plugin.appVersion.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }

            // ---- 依赖库：逐条列出协议与项目链接 ----
            OxideSettingsGroup(
                title = stringResource(R.string.about_library_title),
                metrics = metrics,
            ) {
                libraryData.forEach { info ->
                    OxideLibraryRow(
                        info = info,
                        openLicense = openLicense,
                        openLink = bridge.openLink,
                    )
                }
            }
        }
    }
}

/** 面板顶部：返回 + 标题 + 副标题 */
@Composable
private fun OxideAboutHeader(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    trailing: @Composable () -> Unit,
) {
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
            Spacer(Modifier.width(metrics.secRowGap * 2))
            OxidePageTitle(
                text = stringResource(R.string.settings_tab_info_about),
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        OxideSectionLabel(text = stringResource(R.string.oxide_about_page_subtitle))
        Spacer(Modifier.height(metrics.sectionGap))
    }
}

/**
 * 一条致谢
 *
 * 有项目链接就整行点开它，有协议全文就在右侧给一个按钮——两者都不是装饰：
 * 点下去真的会打开浏览器或启动器自己的协议阅读页。
 */
@Composable
private fun OxideAboutEntryRow(
    entry: OxideAboutEntry,
    launcherName: String,
    openLicense: (Int) -> Unit,
    openLink: (String) -> Unit,
) {
    OxideSettingRow(
        label = stringResource(entry.titleRes),
        hint = aboutDetailText(entry, launcherName),
        onClick = entry.url?.let { url -> { openLink(url) } },
        trailing = if (entry.licenseRaw != 0) {
            {
                OxideButton(
                    text = stringResource(R.string.oxide_about_read_licence),
                    onClick = { openLicense(entry.licenseRaw) },
                    tone = OxideButtonTone.Ghost,
                )
            }
        } else {
            null
        },
    )
}

/**
 * 一条致谢的说明文字
 *
 * 只有 [OxideAboutEntry.takesLauncherName] 为真的那几条才填参数：
 * 剩下几条的字符串里没有 `%s`，硬塞参数在 Android 上会被丢弃，但会招来格式化 lint。
 */
@Composable
private fun aboutDetailText(entry: OxideAboutEntry, launcherName: String): String =
    if (entry.takesLauncherName) {
        stringResource(entry.detailRes, launcherName)
    } else {
        stringResource(entry.detailRes)
    }

/** 一段说明文字：标题 + 正文，占满整行而不是挤在标签右侧 */
@Composable
private fun OxideTextBlock(
    metrics: OxideMetrics,
    title: String,
    text: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = metrics.secRowGap)) {
        Text(
            text = title,
            color = Oxide.Fg,
            fontSize = Oxide.Type.BodyStrong.fontSize,
            lineHeight = Oxide.Type.BodyStrong.lineHeight,
        )
        Spacer(Modifier.height(metrics.secRowGap))
        Text(
            text = text,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一条依赖库：名称 + 版权 + 协议（可打开全文）+ 项目链接 */
@Composable
private fun OxideLibraryRow(
    info: LibraryInfo,
    openLicense: (Int) -> Unit,
    openLink: (String) -> Unit,
) {
    OxideSettingRow(
        label = info.name,
        hint = info.copyrightInfo,
        value = stringResource(R.string.oxide_about_library_licensed, info.license.name),
        onClick = { openLink(info.webUrl) },
        trailing = {
            OxideButton(
                text = stringResource(R.string.oxide_about_read_licence),
                onClick = { openLicense(info.license.raw) },
                tone = OxideButtonTone.Ghost,
            )
        },
    )
}

// ---------------------------------------------------------------------------
// 控制布局管理
// ---------------------------------------------------------------------------

/** 创建新布局时依次要填的三步 */
private enum class ControlLayoutDraftStep { Name, Author, Version }

/**
 * 控制布局管理面板
 *
 * 取代 `ControlManageScreen`（以及它背后的 `NormalNavKey.Settings.ControlManager`）：
 * 旧界面同样是那套图标顶栏与粉色强调轨。
 *
 * 做的仍然是**文件管理**：选、复制、导入、新建、删除、分享、丢进编辑器，一项不少。
 * 列表来自 [ControlManager.dataList] 这个 StateFlow，组合期不读盘也不写盘；
 * 新建与复制触发的落盘动作都在 IO 上跑。
 *
 * 旧界面的右侧详情栏在这里变成"所选布局"那一组只读行（名称 / 作者 / 版本 / 描述）。
 * 旧界面里就地改写这些**可翻译**字段用的是编辑器自带的 Material 对话框，在这套近黑
 * 语言里会明显跳色，因此没有搬过来；这些字段仍然可以在布局编辑器里改。
 */
@Composable
fun OxideControlLayoutsPanel(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bridge = rememberOxideLauncherBridge()
    val eventViewModel = viewModel<EventViewModel>()
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    val dataList by ControlManager.dataList.collectAsStateWithLifecycle()
    val selected by ControlManager.selectedLayout.collectAsStateWithLifecycle()
    val isRefreshing by ControlManager.isRefreshing.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ControlData?>(null) }
    var draft by remember { mutableStateOf<ControlLayoutDraftStep?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftAuthor by remember { mutableStateOf("") }
    var draftVersion by remember { mutableStateOf("1.0") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            eventViewModel.sendEvent(EventViewModel.Event.ImportControls(uris))
        }
    }

    // 被选中的那一项一旦从磁盘上消失（在这里被删掉了），详情与确认条都要跟着收掉
    val alive = remember(dataList) { dataList.map { it.file.name }.toSet() }
    val active = remember(selected, alive) {
        selected?.takeIf { it.file.name in alive }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Oxide.Bg)) {
        val sideBySide = oxideControlLayoutsSideBySide(maxWidth, metrics.cardMinWidth)

        Column(modifier = Modifier.fillMaxSize()) {
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
                Spacer(Modifier.width(metrics.secRowGap * 2))
                OxidePageTitle(
                    text = stringResource(R.string.oxide_about_layouts_page_title),
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.generic_refresh),
                    onClick = { ControlManager.refresh() },
                    tone = OxideButtonTone.Ghost,
                    enabled = !isRefreshing,
                )
            }
            OxideSectionLabel(
                modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                text = stringResource(R.string.oxide_about_layouts_page_subtitle),
            )
            Spacer(Modifier.height(metrics.sectionGap))

            if (dataList.isEmpty()) {
                // 刷新时说的是"正在读布局目录"，不是"没有布局"——两件事不能共用一句话
                if (isRefreshing) {
                    OxideLoadingRow(
                        text = stringResource(R.string.generic_loading),
                        modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                    )
                } else {
                    OxideEmptyState(
                        title = stringResource(R.string.control_manage_list_empty),
                        modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                    )
                }
            } else if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideControlLayoutList(
                        metrics = metrics,
                        dataList = dataList,
                        selectedName = AllSettings.controlLayout.state,
                        onSelect = { data -> ControlManager.selectControl(data) },
                        onCopy = { data ->
                            scope.launch {
                                copyControlLayout(data, bridge::showToast)
                            }
                        },
                        onDelete = { data -> pendingDelete = data },
                        modifier = Modifier
                            .width(metrics.cardMinWidth)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(metrics.groupGap),
                    ) {
                        OxideControlLayoutInfo(
                            metrics = metrics,
                            data = active,
                            locale = locale,
                        )
                        OxideControlLayoutActions(
                            metrics = metrics,
                            data = active,
                            onShare = { data -> shareFile(context, data.file) },
                            onEdit = { data -> startEditorActivity(context, data.file) },
                            onImport = { importLauncher.launch("json".extensionToMimeType()) },
                            onCreate = {
                                draftName = ""
                                draftAuthor = ""
                                draftVersion = "1.0"
                                draft = ControlLayoutDraftStep.Name
                            },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(metrics.groupGap),
                ) {
                    OxideControlLayoutList(
                        metrics = metrics,
                        dataList = dataList,
                        selectedName = AllSettings.controlLayout.state,
                        onSelect = { data -> ControlManager.selectControl(data) },
                        onCopy = { data ->
                            scope.launch {
                                copyControlLayout(data, bridge::showToast)
                            }
                        },
                        onDelete = { data -> pendingDelete = data },
                    )
                    OxideControlLayoutInfo(
                        metrics = metrics,
                        data = active,
                        locale = locale,
                    )
                    OxideControlLayoutActions(
                        metrics = metrics,
                        data = active,
                        onShare = { data -> shareFile(context, data.file) },
                        onEdit = { data -> startEditorActivity(context, data.file) },
                        onImport = { importLauncher.launch("json".extensionToMimeType()) },
                        onCreate = {
                            draftName = ""
                            draftAuthor = ""
                            draftVersion = "1.0"
                            draft = ControlLayoutDraftStep.Name
                        },
                    )
                }
            }
        }

        val deleting = pendingDelete
        if (deleting != null) {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_warning),
                text = stringResource(
                    R.string.control_manage_delete_message,
                    if (deleting.isSupport) {
                        deleting.controlLayout.info.name.translate(locale)
                    } else {
                        deleting.file.name
                    }
                ),
                onConfirm = {
                    ControlManager.deleteControl(deleting)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }

        // 新建布局：三次就地确认，顺序与旧界面那一组对话框一致
        when (draft) {
            ControlLayoutDraftStep.Name -> SimpleEditDialog(
                title = stringResource(R.string.control_manage_create_new_title),
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text(stringResource(R.string.control_manage_create_new_name)) },
                isError = draftName.isBlank() || draftName.length > NAME_LENGTH,
                supportingText = {
                    Text(stringResource(R.string.generic_input_length, draftName.length, NAME_LENGTH))
                },
                singleLine = true,
                onDismissRequest = { draft = null },
                onConfirm = {
                    if (draftName.isNotBlank() && draftName.length <= NAME_LENGTH) {
                        draft = ControlLayoutDraftStep.Author
                    }
                },
            )

            ControlLayoutDraftStep.Author -> SimpleEditDialog(
                title = stringResource(R.string.control_manage_create_new_title),
                value = draftAuthor,
                onValueChange = { draftAuthor = it },
                label = { Text(stringResource(R.string.control_manage_create_new_author)) },
                isError = draftAuthor.length > AUTHOR_NAME_LENGTH,
                supportingText = {
                    Text(stringResource(R.string.generic_input_length, draftAuthor.length, AUTHOR_NAME_LENGTH))
                },
                singleLine = true,
                onDismissRequest = { draft = null },
                onCancel = { draft = null },
                onConfirm = { draft = ControlLayoutDraftStep.Version },
            )

            ControlLayoutDraftStep.Version -> SimpleEditDialog(
                title = stringResource(R.string.control_manage_create_new_title),
                value = draftVersion,
                onValueChange = { draftVersion = it },
                label = { Text(stringResource(R.string.control_manage_create_new_version_name)) },
                isError = draftVersion.length > VERSION_NAME_LENGTH,
                supportingText = {
                    Text(stringResource(R.string.generic_input_length, draftVersion.length, VERSION_NAME_LENGTH))
                },
                singleLine = true,
                onDismissRequest = { draft = null },
                onCancel = { draft = null },
                onConfirm = {
                    val name = draftName
                    val author = draftAuthor
                    val versionName = draftVersion
                    draft = null
                    scope.launch {
                        createControlLayout(
                            name = name,
                            author = author,
                            versionName = versionName,
                            onError = bridge::showToast,
                        )
                    }
                },
            )

            null -> {}
        }
    }
}

/** 左侧：磁盘上真实存在的布局列表 */
@Composable
private fun OxideControlLayoutList(
    metrics: OxideMetrics,
    dataList: List<ControlData>,
    selectedName: String,
    onSelect: (ControlData) -> Unit,
    onCopy: (ControlData) -> Unit,
    onDelete: (ControlData) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSettingsGroup(
        title = stringResource(R.string.oxide_set_section_control_layouts),
        metrics = metrics,
        modifier = modifier,
    ) {
        dataList.forEach { data ->
            val isSelected = data.file.name == selectedName
            OxideSettingRow(
                label = controlLayoutName(data),
                hint = if (data.isSupport) {
                    data.controlLayout.info.versionName
                        .takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.generic_unspecified)
                } else {
                    stringResource(R.string.control_manage_info_unsupport)
                },
                enabled = data.isSupport,
                modifier = Modifier.selectable(
                    selected = isSelected,
                    enabled = data.isSupport,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = { onSelect(data) },
                ),
                trailing = {
                    OxideIconButton(
                        onClick = { onCopy(data) },
                        glyph = "⧉",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_about_layouts_copy)
                        ),
                    )
                    OxideIconButton(
                        onClick = { onDelete(data) },
                        glyph = "✕",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_about_layouts_delete)
                        ),
                    )
                },
            )
        }
    }
}

/** 右侧：所选布局的真实信息，不加任何推测字段 */
@Composable
private fun OxideControlLayoutInfo(
    metrics: OxideMetrics,
    data: ControlData?,
    locale: Locale,
) {
    OxideSettingsGroup(
        title = stringResource(R.string.oxide_about_layouts_section_info),
        metrics = metrics,
    ) {
        if (data == null) {
            // 没有选中项时说清是"还没选"，而不是渲染一组空值行
            OxideEmptyState(
                title = stringResource(R.string.control_manage_info_empty),
                detail = stringResource(R.string.oxide_about_layouts_pick_one),
            )
        } else {
            val info = data.controlLayout.info
            OxideSettingRow(
                label = stringResource(R.string.control_manage_create_new_name),
                value = info.name.translate(locale).ifBlank {
                    stringResource(R.string.generic_unspecified)
                },
            )
            OxideSettingRow(
                label = stringResource(R.string.control_manage_create_new_author),
                value = info.author.translate(locale).ifBlank {
                    stringResource(R.string.generic_unspecified)
                },
            )
            OxideSettingRow(
                label = stringResource(R.string.control_manage_create_new_version_name),
                value = info.versionName.ifBlank { stringResource(R.string.generic_unspecified) },
            )
            OxideTextBlock(
                metrics = metrics,
                title = stringResource(R.string.control_manage_info_description),
                text = info.description.translate(locale)
                    .ifBlank { stringResource(R.string.control_manage_info_description_empty) },
            )
        }
    }
}

/** 文件级操作：导入、新建、分享、送进编辑器 */
@Composable
private fun OxideControlLayoutActions(
    metrics: OxideMetrics,
    data: ControlData?,
    onShare: (ControlData) -> Unit,
    onEdit: (ControlData) -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
) {
    OxideSettingsGroup(
        title = stringResource(R.string.oxide_about_layouts_section_actions),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.control_manage_import),
            hint = stringResource(R.string.oxide_about_layouts_import_detail),
            onClick = onImport,
        )
        OxideActionRow(
            label = stringResource(R.string.control_manage_create_new),
            hint = stringResource(R.string.oxide_about_layouts_create_detail),
            onClick = onCreate,
        )
        OxideActionRow(
            label = stringResource(R.string.generic_share),
            hint = stringResource(R.string.oxide_about_layouts_share_detail),
            enabled = data != null,
            onClick = { data?.let(onShare) },
        )
        OxideActionRow(
            label = stringResource(R.string.control_manage_info_edit),
            hint = stringResource(R.string.oxide_about_layouts_edit_detail),
            enabled = data?.isSupport == true,
            onClick = { data?.let(onEdit) },
        )
    }
}

/** 一个布局的名字：不认识的版本退回文件名，那才是用户唯一能认出来的东西 */
private fun controlLayoutName(data: ControlData): String =
    if (data.isSupport) data.controlLayout.info.name.default else data.file.name

/** 把选中的布局另存一份，文件名随机，与旧界面的"复制"完全一致 */
private suspend fun copyControlLayout(data: ControlData, onError: (Int) -> Unit) {
    withContext(Dispatchers.IO) {
        val target = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")
        try {
            data.controlLayout.pack().saveToFile(target)
        } catch (e: Exception) {
            Logger.warning(ABOUT_TAG, "Failed to copy the control layout", e)
            onError(R.string.control_manage_failed_to_save)
            return@withContext
        }
        ControlManager.refresh()
    }
}

/**
 * 新建一份空布局
 *
 * 落盘在 IO 上，写完立刻 `refresh()`，列表与选中项都来自 [ControlManager] 自己的 StateFlow，
 * 因此这一页不需要自己去改任何界面状态。
 */
private suspend fun createControlLayout(
    name: String,
    author: String,
    versionName: String,
    onError: (Int) -> Unit,
) {
    withContext(Dispatchers.IO) {
        val layout = EmptyControlLayout.copy(
            info = EmptyLayoutInfo.copy(
                name = createTranslatable(default = name),
                author = createTranslatable(default = author),
                versionName = versionName,
            )
        )
        val target = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")
        try {
            layout.saveToFile(target)
        } catch (e: Exception) {
            Logger.warning(ABOUT_TAG, "Failed to save the new control layout", e)
            target.delete()
            onError(R.string.control_manage_failed_to_save)
            return@withContext
        }
        ControlManager.refresh()
    }
}
