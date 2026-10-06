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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.plugin.natives.NativePlugin
import dev.oxide.launcher.game.plugin.natives.NativePluginManager
import dev.oxide.launcher.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import dev.oxide.launcher.path.URL_PROJECT
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.ActionMenuSide
import dev.oxide.launcher.setting.enums.AppLanguage
import dev.oxide.launcher.setting.enums.DarkMode
import dev.oxide.launcher.setting.enums.MirrorSourceType
import dev.oxide.launcher.setting.enums.applyLanguage
import dev.oxide.launcher.setting.unit.floatRange
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.theme.ColorThemeType
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.utils.animation.TransitionAnimationType
import dev.oxide.launcher.utils.isChinaMainland
import dev.oxide.launcher.viewmodel.LocalBackgroundViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设置页的分类
 *
 * 参考稿是"左侧分类 + 右侧分组面板"，分类名与顺序沿用参考稿，
 * 只是去掉了属于另一个项目的 Recorder，并把每一类映射到启动器真实存在的功能上。
 */
internal enum class OxideSettingsCategory(
    val titleRes: Int,
    val summaryRes: Int,
) {
    General(R.string.oxide_set_cat_general, R.string.oxide_set_summary_general),
    Game(R.string.oxide_set_cat_game, R.string.oxide_set_summary_game),
    Java(R.string.oxide_set_cat_java, R.string.oxide_set_summary_java),
    Renderer(R.string.oxide_set_cat_renderer, R.string.oxide_set_summary_renderer),
    Graphics(R.string.oxide_set_cat_graphics, R.string.oxide_set_summary_graphics),
    Controls(R.string.oxide_set_cat_controls, R.string.oxide_set_summary_controls),
    Downloads(R.string.oxide_set_cat_downloads, R.string.oxide_set_summary_downloads),
    Appearance(R.string.oxide_set_cat_appearance, R.string.oxide_set_summary_appearance),
    Accounts(R.string.oxide_set_cat_accounts, R.string.oxide_set_summary_accounts),
    Storage(R.string.oxide_set_cat_storage, R.string.oxide_set_summary_storage),
    Advanced(R.string.oxide_set_cat_advanced, R.string.oxide_set_summary_advanced),
}

/** 由抽屉承载的分类：面板本身只给摘要与入口，细节在抽屉里 */
internal enum class OxideSettingsDrawer {
    Account, Java, Renderer, Storage, Advanced,
}

/**
 * 一次"打开抽屉"的请求
 *
 * Renderer 抽屉同时承载 Renderer 与 Graphics 两类，两类的入口按钮是同一个，
 * 所以要记住是从哪一类进来的：Graphics 应当直接落在"图形"那一页，
 * 否则用户点了 Graphics 却看到渲染器列表，会以为按钮没生效。
 */
internal data class OxideDrawerRequest(
    val drawer: OxideSettingsDrawer,
    /** 抽屉内部的初始标签页 */
    val initialTab: Int = 0,
)

private fun OxideSettingsCategory.drawer(): OxideSettingsDrawer? = when (this) {
    OxideSettingsCategory.Accounts -> OxideSettingsDrawer.Account
    OxideSettingsCategory.Java -> OxideSettingsDrawer.Java
    OxideSettingsCategory.Renderer, OxideSettingsCategory.Graphics -> OxideSettingsDrawer.Renderer
    OxideSettingsCategory.Storage -> OxideSettingsDrawer.Storage
    OxideSettingsCategory.Advanced -> OxideSettingsDrawer.Advanced
    OxideSettingsCategory.General,
    OxideSettingsCategory.Game,
    OxideSettingsCategory.Controls,
    OxideSettingsCategory.Downloads,
    OxideSettingsCategory.Appearance,
    -> null
}

/** 同一抽屉里，这两类分别落在第几页标签 */
private fun OxideSettingsCategory.drawerTab(): Int = when (this) {
    OxideSettingsCategory.Graphics -> OxideRendererTabs.GRAPHICS
    OxideSettingsCategory.Renderer -> OxideRendererTabs.RENDERER
    else -> 0
}

/**
 * Android 12 以下没有动态取色
 *
 * [dev.oxide.launcher.ui.theme.OxideTheme] 在 API 31 以下会跳过 Dynamic 分支并落到
 * 默认配色，也就是说旧设备上选中它等于什么都没选。旧设置页用禁用单项的方式处理，
 * 这里直接把那一项从列表里去掉，避免出现"能点、但看不出变化"的控件。
 */
internal fun colorThemeEntries(sdkInt: Int): List<ColorThemeType> =
    if (sdkInt >= Build.VERSION_CODES.S) {
        ColorThemeType.entries.toList()
    } else {
        ColorThemeType.entries.filter { it != ColorThemeType.DYNAMIC }
    }

/**
 * "清除已设置的壁纸"这一行该不该出现
 *
 * 颜色主题与整块壁纸控件已经从这一页移走了（见 `AppearanceCategory` 的说明），
 * 但**已经设过的壁纸必须还有办法清掉**：那一行是这条设置剩下的唯一出口，
 * 没有它，界面上就再没有一处能把启动器的背景改回空白。
 *
 * 反过来，没设壁纸时它必须**不出现**，而不是留一枚点下去什么也不会发生的灰行——
 * 这条是 `OxideSettingsRowVisibilityTest` 对整本设置页立下的规矩。
 * 纯函数，因此两种状态都可以直接单测。
 */
internal fun oxideWallpaperClearVisible(hasValidWallpaper: Boolean): Boolean = hasValidWallpaper

/**
 * 设置页
 *
 * 结构与参考稿一致：左侧一列分类，右侧一块分组面板，分类之间是紧凑的行、开关与选择器。
 * 宽度足够时左右并列，不够时分类折叠成顶部一条横向标签、面板占满剩余高度，
 * 因此从 560dp 的小手机横屏到大平板横屏都不会裁切或重叠。
 * 所有尺寸都来自 [metrics]，页面自身不写死 dp。
 */
@Composable
fun OxideSettingsPage(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 把用户选的颜色主题递给 Oxide。调色板是全局状态而不是 CompositionLocal，
    // 所以在设置里换主题之后，整个界面上用到 Oxide 颜色的地方都会一起变。
    // 界面根上还需要再包一层，见 dev.oxide.launcher.ui.theme.ProvideOxideChrome。
    ProvideOxideChrome {
        OxideSettingsPageContent(metrics = metrics, onNavigate = onNavigate, modifier = modifier)
    }
}

@Composable
private fun OxideSettingsPageContent(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bridge = rememberOxideLauncherBridge()

    var selected by rememberSaveable { mutableStateOf(OxideSettingsCategory.General) }
    var drawer by remember { mutableStateOf<OxideDrawerRequest?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        OxidePageColumn(metrics = metrics) {
            OxidePageTitle(text = stringResource(R.string.oxide_set_page_title))
            Spacer(Modifier.height(metrics.rowGap))
            Text(
                text = stringResource(R.string.oxide_set_page_subtitle),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.groupGap))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // 并列门槛完全由 metrics 推导：分类列放得下标签，面板还得剩下一张卡
                val sideBySide = maxWidth >= metrics.cardMinWidth * 1.6f

                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        OxideCategoryRail(
                            metrics = metrics,
                            current = selected,
                            onSelect = { selected = it },
                            modifier = Modifier
                                .width((155.dp * metrics.guiScale).coerceIn(132.dp, 260.dp))
                                .fillMaxHeight(),
                        )
                        OxideSettingsPanel(
                            metrics = metrics,
                            category = selected,
                            bridge = bridge,
                            onOpenDrawer = { request -> drawer = request },
                            onNavigate = onNavigate,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        OxideCategoryChips(
                            metrics = metrics,
                            current = selected,
                            onSelect = { selected = it },
                        )
                        OxideSettingsPanel(
                            metrics = metrics,
                            category = selected,
                            bridge = bridge,
                            onOpenDrawer = { request -> drawer = request },
                            onNavigate = onNavigate,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        val openDrawer = drawer
        when (openDrawer) {
            is OxideDrawerRequest -> when (openDrawer.drawer) {
                OxideSettingsDrawer.Account ->
                    OxideAccountDrawer(metrics = metrics, onDismiss = { drawer = null })

                OxideSettingsDrawer.Java ->
                    OxideJavaDrawer(metrics = metrics, onDismiss = { drawer = null })

                OxideSettingsDrawer.Renderer ->
                    OxideRendererDrawer(
                        metrics = metrics,
                        initialTab = openDrawer.initialTab,
                        onDismiss = { drawer = null },
                    )

                OxideSettingsDrawer.Storage ->
                    OxideStorageDrawer(metrics = metrics, onDismiss = { drawer = null })

                OxideSettingsDrawer.Advanced ->
                    OxideAdvancedDrawer(metrics = metrics, onDismiss = { drawer = null })
            }

            null -> {}
        }

        // 自定义主题色的对话框（OxideCustomColorDialog）与它的设置项
        // launcherCustomColor 都还在树里，只是"颜色主题"那一行已经从外观页移走，
        // 因此这里暂时没有入口；把那一行加回来时，删掉这段注释即可恢复。
    }
}

// ---------------------------------------------------------------------------
// 分类列表
// ---------------------------------------------------------------------------

/** 宽屏：左侧竖排分类 */
@Composable
private fun OxideCategoryRail(
    metrics: OxideMetrics,
    current: OxideSettingsCategory,
    onSelect: (OxideSettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSettingsCategory.entries.forEach { category ->
            OxideCategoryItem(
                label = stringResource(category.titleRes),
                isSelected = category == current,
                metrics = metrics,
                onClick = { onSelect(category) },
            )
        }
    }
}

/** 窄屏：顶部横向标签，不占用纵向空间 */
@Composable
private fun OxideCategoryChips(
    metrics: OxideMetrics,
    current: OxideSettingsCategory,
    onSelect: (OxideSettingsCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        OxideSettingsCategory.entries.forEach { category ->
            val isSelected = category == current
            OxideSurface(
                // selectable 同时给出选中状态与页签角色，标签不单靠颜色区分
                modifier = Modifier.selectable(
                    selected = isSelected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelect(category) },
                ),
                selected = isSelected,
                contentPadding = PaddingValues(
                    horizontal = metrics.cardGap,
                    vertical = metrics.rowGap,
                ),
            ) {
                Text(
                    text = stringResource(category.titleRes),
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
private fun OxideCategoryItem(
    label: String,
    isSelected: Boolean,
    metrics: OxideMetrics,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.categoryTabHeight)
            .clip(Oxide.RadiusControl)
            .background(if (isSelected) Oxide.BgTabActive else Color.Transparent)
            .selectable(
                selected = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = metrics.rowGap * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (isSelected) Oxide.Fg else Oxide.FgGhost,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 右侧面板
// ---------------------------------------------------------------------------

@Composable
internal fun OxideSettingsPanel(
    metrics: OxideMetrics,
    category: OxideSettingsCategory,
    bridge: OxideLauncherBridge,
    onOpenDrawer: (OxideDrawerRequest) -> Unit,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.groupGap),
    ) {
        when (category) {
            OxideSettingsCategory.General -> GeneralCategory(metrics, bridge)
            OxideSettingsCategory.Game -> GameCategory(metrics, onNavigate)
            // 控制这一类整块都在面板里展开，见 OxideControlsPanel
            OxideSettingsCategory.Controls -> OxideControlsPanel(metrics = metrics, bridge = bridge)
            OxideSettingsCategory.Downloads -> DownloadsCategory(metrics, bridge, onNavigate)
            OxideSettingsCategory.Appearance -> AppearanceCategory(metrics)
            else -> {
                val drawer = category.drawer()
                OxideDrawerCategorySummary(metrics = metrics, category = category)
                Group(
                    index = 1,
                    title = stringResource(R.string.oxide_set_section_details),
                    metrics = metrics,
                ) {
                    OxideButton(
                        text = stringResource(R.string.oxide_set_open, stringResource(category.titleRes)),
                        onClick = {
                            if (drawer != null) {
                                onOpenDrawer(
                                    OxideDrawerRequest(
                                        drawer = drawer,
                                        initialTab = category.drawerTab(),
                                    )
                                )
                            }
                        },
                        enabled = drawer != null,
                        tone = OxideButtonTone.Primary,
                    )
                    when (category) {
                        OxideSettingsCategory.Storage -> OxideActionRow(
                            label = stringResource(R.string.oxide_set_action_open_instances),
                            hint = stringResource(R.string.oxide_set_action_open_instances_detail),
                            onClick = { onNavigate(OxidePage.Instances) },
                        )

                        OxideSettingsCategory.Accounts -> OxideActionRow(
                            label = stringResource(R.string.oxide_set_action_open_home),
                            hint = stringResource(R.string.oxide_set_action_open_home_detail),
                            onClick = { onNavigate(OxidePage.Home) },
                        )

                        // Java / Renderer / Graphics / Advanced 的去处都在各自的抽屉里，
                        // 这里再摆一个入口只会和抽屉里的那一行重复，所以不渲染
                        else -> {}
                    }
                }
            }
        }
    }
}

/** 带错峰进场的一个分组 */
@Composable
private fun Group(
    index: Int,
    title: String,
    metrics: OxideMetrics,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OxideReveal(visible = true, index = index) {
        OxideSettingsGroup(
            title = title,
            metrics = metrics,
            trailing = trailing,
            content = content,
        )
    }
}

/** 抽屉分类的面板摘要 */
@Composable
private fun OxideDrawerCategorySummary(
    metrics: OxideMetrics,
    category: OxideSettingsCategory,
) {
    OxideReveal(visible = true, index = 0) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.cardGap,
            ),
        ) {
            Text(
                text = stringResource(category.summaryRes),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 分类：常规
// ---------------------------------------------------------------------------

@Composable
private fun GeneralCategory(metrics: OxideMetrics, bridge: OxideLauncherBridge) {
    Group(index = 0, title = stringResource(R.string.oxide_set_section_launcher), metrics = metrics) {
        OxideEnumRow(
            label = stringResource(R.string.settings_launcher_dark_mode_title),
            metrics = metrics,
            entries = DarkMode.entries,
            selected = AllSettings.launcherDarkMode.state,
            nameOf = { stringResource(it.textRes) },
            onSelect = { AllSettings.launcherDarkMode.save(it) },
        )
        OxideEnumRow(
            label = stringResource(R.string.settings_launcher_language),
            metrics = metrics,
            entries = AppLanguage.entries,
            selected = AllSettings.launcherLanguage.state,
            nameOf = { stringResource(it.textRes) },
            onSelect = {
                AllSettings.launcherLanguage.save(it)
                applyLanguage(it)
            },
        )
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_action_menu_side),
            hint = stringResource(R.string.oxide_set_action_menu_side_detail),
            metrics = metrics,
            entries = ActionMenuSide.entries,
            selected = AllSettings.launcherActionMenuSide.state,
            nameOf = { oxideActionMenuSideName(it) },
            onSelect = { AllSettings.launcherActionMenuSide.save(it) },
        )
        OxideToggleRow(
            label = stringResource(R.string.settings_launcher_full_screen_title),
            hint = stringResource(R.string.settings_launcher_full_screen_summary),
            checked = AllSettings.launcherFullScreen.state,
            onCheckedChange = { AllSettings.launcherFullScreen.save(it) },
        )
        OxideToggleRow(
            label = stringResource(R.string.settings_launcher_festivals_effects_title),
            hint = stringResource(R.string.settings_launcher_festivals_effects_summary),
            checked = AllSettings.launcherFestivalEffects.state,
            onCheckedChange = { AllSettings.launcherFestivalEffects.save(it) },
        )
        OxideToggleRow(
            label = stringResource(R.string.oxide_set_task_menu_expanded),
            hint = stringResource(R.string.oxide_set_task_menu_expanded_detail),
            checked = AllSettings.launcherTaskMenuExpanded.state,
            onCheckedChange = { AllSettings.launcherTaskMenuExpanded.save(it) },
        )
    }

    Group(index = 1, title = stringResource(R.string.oxide_set_section_motion), metrics = metrics) {
        OxideEnumRow(
            label = stringResource(R.string.settings_launcher_swap_animate_type_title),
            hint = stringResource(R.string.settings_launcher_swap_animate_type_summary),
            metrics = metrics,
            entries = TransitionAnimationType.entries,
            selected = AllSettings.launcherSwapAnimateType.state,
            nameOf = { stringResource(it.textRes) },
            onSelect = { AllSettings.launcherSwapAnimateType.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_launcher_animate_speed_title),
            hint = stringResource(R.string.settings_launcher_animate_speed_summary),
            metrics = metrics,
            value = AllSettings.launcherAnimateSpeed.state,
            range = AllSettings.launcherAnimateSpeed.floatRange.toIntRange(),
            suffix = "x",
            onValueChange = { AllSettings.launcherAnimateSpeed.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_launcher_animate_extent_title),
            hint = stringResource(R.string.settings_launcher_animate_extent_summary),
            metrics = metrics,
            value = AllSettings.launcherAnimateExtent.state,
            range = AllSettings.launcherAnimateExtent.floatRange.toIntRange(),
            suffix = "x",
            onValueChange = { AllSettings.launcherAnimateExtent.save(it) },
        )
    }

    Group(index = 2, title = stringResource(R.string.oxide_set_section_quick_actions), metrics = metrics) {
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_check_update),
            hint = stringResource(R.string.oxide_set_action_check_update_detail),
            onClick = bridge.checkUpdate,
        )
        // 旧启动器主界面的重播引导已移除：它的每一帧都锚在旧主界面上，
        // 而旧主界面从新外壳起就已经不可达，重播出来的只会是一串对不上位置的卡片。
        OxideActionRow(
            label = stringResource(R.string.settings_tab_info_about),
            hint = stringResource(R.string.oxide_set_action_about_detail),
            onClick = { bridge.openSettingsSection(OxideSettingsSection.About) },
        )
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_community),
            hint = stringResource(R.string.oxide_set_action_community_detail),
            onClick = { bridge.openLink(URL_PROJECT) },
        )
    }
}

// ---------------------------------------------------------------------------
// 分类：游戏
// ---------------------------------------------------------------------------

@Composable
private fun GameCategory(metrics: OxideMetrics, onNavigate: (OxidePage) -> Unit) {
    Group(index = 0, title = stringResource(R.string.oxide_set_section_versions), metrics = metrics) {
        OxideToggleRow(
            label = stringResource(R.string.settings_game_version_isolation_title),
            hint = stringResource(R.string.settings_game_version_isolation_summary),
            checked = AllSettings.versionIsolation.state,
            onCheckedChange = { AllSettings.versionIsolation.save(it) },
        )
        OxideToggleRow(
            label = stringResource(R.string.settings_game_skip_game_integrity_check_title),
            hint = stringResource(R.string.settings_game_skip_game_integrity_check_summary),
            checked = AllSettings.skipGameIntegrityCheck.state,
            onCheckedChange = { AllSettings.skipGameIntegrityCheck.save(it) },
        )
        OxideTextRow(
            title = stringResource(R.string.settings_game_version_custom_info_title),
            hint = stringResource(R.string.settings_game_version_custom_info_summary),
            value = AllSettings.versionCustomInfo.state,
            onSave = { AllSettings.versionCustomInfo.save(it) },
        )
    }

    Group(index = 1, title = stringResource(R.string.oxide_set_section_launch), metrics = metrics) {
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_open_instances),
            hint = stringResource(R.string.oxide_set_action_open_instances_detail),
            onClick = { onNavigate(OxidePage.Instances) },
        )
    }

    Group(index = 2, title = stringResource(R.string.oxide_set_section_game_log), metrics = metrics) {
        OxideToggleRow(
            label = stringResource(R.string.settings_game_show_log_automatic_title),
            hint = stringResource(R.string.settings_game_show_log_automatic_summary),
            checked = AllSettings.showLogAutomatic.state,
            onCheckedChange = { AllSettings.showLogAutomatic.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_game_log_text_size_title),
            hint = stringResource(R.string.settings_game_log_text_size_summary),
            metrics = metrics,
            value = AllSettings.logTextSize.state,
            range = AllSettings.logTextSize.floatRange.toIntRange(),
            suffix = " sp",
            onValueChange = { AllSettings.logTextSize.save(it) },
        )
        OxideIntRow(
            label = stringResource(R.string.settings_game_log_buffer_flush_interval_title),
            hint = stringResource(R.string.settings_game_log_buffer_flush_interval_summary),
            metrics = metrics,
            value = AllSettings.logBufferFlushInterval.state,
            range = AllSettings.logBufferFlushInterval.floatRange.toIntRange(),
            step = 20,
            suffix = " ms",
            onValueChange = { AllSettings.logBufferFlushInterval.save(it) },
        )
    }

    // 游戏内浮层里与"日志"无关的那一项：画在游戏画面右下角的那块 Oxide 标识。
    // 它与帧率、内存、悬浮球是同一类东西，只是那些在高级抽屉的"游戏内浮层"分组里，
    // 而这一块跟着游戏本体走——游戏本体这一类本来就是关于"游戏运行时"的。
    // 复用已存在的分组名，而不是另起一个：分组名只有一处出处，
    // 同一屏里出现两个"游戏内浮层"分组会读成两套不同的东西。
    Group(index = 3, title = stringResource(R.string.oxide_set_section_overlay), metrics = metrics) {
        OxideToggleRow(
            label = stringResource(R.string.oxide_set_show_game_brand),
            hint = stringResource(R.string.oxide_set_show_game_brand_detail),
            checked = AllSettings.showGameBrand.state,
            onCheckedChange = { AllSettings.showGameBrand.save(it) },
        )
    }
}

// ---------------------------------------------------------------------------
// 分类：下载
// ---------------------------------------------------------------------------

@Composable
private fun DownloadsCategory(
    metrics: OxideMetrics,
    bridge: OxideLauncherBridge,
    onNavigate: (OxidePage) -> Unit,
) {
    // 镜像源只是为了改善中国大陆内陆的网络环境而存在的，境外开放反而会拖慢下载
    val isChinaMainland = remember { isChinaMainland() }
    var pluginToken by remember { mutableIntStateOf(0) }
    val nativePlugins = remember(pluginToken) { NativePluginManager.getPlugins() }
    val disabledPlugins = AllSettings.disableNativeLibPlugins.state

    var index = 0

    if (isChinaMainland) {
        Group(
            index = index++,
            title = stringResource(R.string.oxide_set_section_mirrors),
            metrics = metrics,
        ) {
            OxideEnumRow(
                label = stringResource(R.string.settings_launcher_mirror_game_source_title),
                metrics = metrics,
                entries = MirrorSourceType.entries,
                selected = AllSettings.gameDownloadSource.state,
                nameOf = { stringResource(it.textRes) },
                onSelect = { AllSettings.gameDownloadSource.save(it) },
            )
            OxideEnumRow(
                label = stringResource(R.string.settings_launcher_mirror_asset_platform_source_title),
                metrics = metrics,
                entries = MirrorSourceType.entries,
                selected = AllSettings.assetPlatformSource.state,
                nameOf = { stringResource(it.textRes) },
                onSelect = { AllSettings.assetPlatformSource.save(it) },
            )
        }
    }

    Group(
        index = index++,
        title = stringResource(R.string.oxide_set_section_plugins),
        metrics = metrics,
    ) {
        if (nativePlugins.isEmpty()) {
            OxideEmptyState(title = stringResource(R.string.oxide_set_no_plugin))
        } else {
            nativePlugins.forEach { plugin ->
                NativeLibPluginRow(
                    plugin = plugin,
                    disabled = plugin.packageName in disabledPlugins,
                    onToggle = { enabled ->
                        val current = AllSettings.disableNativeLibPlugins.state
                        AllSettings.disableNativeLibPlugins.save(
                            if (enabled) current - plugin.packageName else current + plugin.packageName
                        )
                    },
                )
            }
        }
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_dl_native_lib_plugin),
            hint = stringResource(R.string.oxide_set_action_dl_native_lib_plugin_detail),
            onClick = {
                pluginToken++
                bridge.openLink(URL_GITHUB_NATIVE_LIB_PLUGINS)
            },
        )
    }

    Group(
        index = index++,
        title = stringResource(R.string.oxide_set_section_search),
        metrics = metrics,
    ) {
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_search_mod),
            metrics = metrics,
            entries = Platform.entries,
            selected = AllSettings.searchModPlatform.state,
            nameOf = { it.displayName },
            onSelect = { AllSettings.searchModPlatform.save(it) },
        )
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_search_modpack),
            metrics = metrics,
            entries = Platform.entries,
            selected = AllSettings.searchModpackPlatform.state,
            nameOf = { it.displayName },
            onSelect = { AllSettings.searchModpackPlatform.save(it) },
        )
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_search_resource_pack),
            metrics = metrics,
            entries = Platform.entries,
            selected = AllSettings.searchResourcePackPlatform.state,
            nameOf = { it.displayName },
            onSelect = { AllSettings.searchResourcePackPlatform.save(it) },
        )
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_search_shaders),
            metrics = metrics,
            entries = Platform.entries,
            selected = AllSettings.searchShadersPlatform.state,
            nameOf = { it.displayName },
            onSelect = { AllSettings.searchShadersPlatform.save(it) },
        )
    }

    Group(
        index = index,
        title = stringResource(R.string.oxide_set_section_browse),
        metrics = metrics,
    ) {
        OxideActionRow(
            label = stringResource(R.string.oxide_set_action_open_discover),
            hint = stringResource(R.string.oxide_set_action_open_discover_detail),
            onClick = { onNavigate(OxidePage.Discover) },
        )
    }
}

@Composable
private fun NativeLibPluginRow(
    plugin: NativePlugin,
    disabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    OxideToggleRow(
        label = plugin.displayName,
        hint = stringResource(R.string.oxide_set_plugin_from, plugin.appName),
        checked = !disabled,
        onCheckedChange = onToggle,
    )
}

// ---------------------------------------------------------------------------
// 分类：外观
// ---------------------------------------------------------------------------

/**
 * 外观
 *
 * 这一页现在只留两样：**界面缩放**，以及**已经在用的壁纸的清除入口**。
 *
 * "颜色主题"与整块"壁纸"按用户要求从这一页移走了（v1.8.0 的截图见 issue），
 * 但**设置项本身一个都没有删**：`launcherColorTheme`、`launcherCustomColor`、
 * `launcherCustomPaletteStyle`、`launcherBackgroundOpacity`、`videoBackgroundVolume`、
 * `backgroundBlur`、`backgroundBlurType` 全部留在 `AllSettings` 里，
 * `ui/theme/Theme.kt`、`NativeThemeUtils.kt`、`BackgroundViewModel.kt`、
 * `BackgroundGlass.kt` 这些真正读它们的地方一行没动。删掉一行和删掉一套能用的
 * 配色／毛玻璃不是一回事：前者随时能加回来，后者要在二十多个文件里回滚。
 *
 * 保留清除入口是另一回事，而且是必须的：壁纸已经设过的账号，
 * 如果这一页连"清掉它"都没有，那张图就永远留在启动器上，而界面上再没有一处
 * 能把它拿掉——那就是把设置变成了只能进不能出的单向门。所以这一行**只在真的
 * 有壁纸时**出现（见 `oxideWallpaperClearVisible`），平时不留一枚点不动的灰行。
 */
@Composable
private fun AppearanceCategory(
    metrics: OxideMetrics,
) {
    val scope = rememberCoroutineScope()
    val backgroundViewModel = LocalBackgroundViewModel.current
    val backgroundValid = backgroundViewModel?.isValid == true

    var confirmClear by remember { mutableStateOf(false) }

    // 界面缩放：选中值直接读 .state，所以改完立刻生效并立刻存盘
    val guiScalePercent = AllSettings.launcherGuiScale.state
    // 存储里的值可能来自旧版本或手改的备份，落不到档位上就退回 100%
    val guiScaleSelected = if (guiScalePercent in OxideGuiScaleSteps) {
        guiScalePercent
    } else {
        OxideGuiScaleDefaultPercent
    }

    Group(index = 0, title = stringResource(R.string.oxide_set_section_interface), metrics = metrics) {
        OxideEnumRow(
            label = stringResource(R.string.oxide_set_gui_scale),
            hint = stringResource(R.string.oxide_set_gui_scale_detail),
            metrics = metrics,
            entries = OxideGuiScaleSteps,
            selected = guiScaleSelected,
            nameOf = { stringResource(R.string.oxide_set_gui_scale_value, it) },
            onSelect = { AllSettings.launcherGuiScale.save(it) },
        )
    }

    if (oxideWallpaperClearVisible(backgroundValid)) {
        Group(
            index = 1,
            title = stringResource(R.string.oxide_set_section_background),
            metrics = metrics,
        ) {
            OxideActionRow(
                label = stringResource(R.string.settings_launcher_background_title),
                hint = stringResource(R.string.oxide_set_background_reset_detail),
                value = stringResource(R.string.generic_clear),
                onClick = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        OxideConfirmDialog(
            title = stringResource(R.string.generic_clear),
            message = stringResource(R.string.settings_launcher_background_reset_message),
            confirmText = stringResource(R.string.generic_clear),
            onConfirm = {
                confirmClear = false
                scope.launch { backgroundViewModel?.delete() }
            },
            onDismiss = { confirmClear = false },
        )
    }
}