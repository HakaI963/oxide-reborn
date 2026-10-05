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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.oxide.launcher.R
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.mod.ModLoaderVerdict
import dev.oxide.launcher.game.version.mod.update.SelectableModManifest
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle

/**
 * 模组管理表面
 *
 * 这一块是重建的：旧形状（一个 `OxideContentEntry` 里挤一句 `displayName` 和一句
 * `detail`，图标读了却从不画）在设备上的表现就是"列表很糟"。这里按后端**真的**能做的事
 * 重新排了一遍，后端每一项能力都有地方落脚：
 *
 * | 后端 | 这里的位置 |
 * |---|---|
 * | `LocalMod.icon` / `ModProject.iconUrl` | 行首图标（优先项目封面） |
 * | `LocalMod.name` / `ModProject.title` | 行标题 |
 * | `LocalMod.version` / `authors` / `description` | 副标题行 + 详情层 |
 * | `LocalMod.fileSize` / `file.lastModified()` | 副标题行 |
 * | `ModFile.loaders` + `VersionInfo.loaderInfos` | 加载器徽章 + 兼容性那一行 |
 * | `LocalMod.file.isEnabled()` | 行尾开关（可逆） |
 * | `LocalMod.delete()` | 行尾删除 / 批量删除（带确认） |
 * | `AllModReader` | 搜索、状态筛选、五种排序、单选与多选 |
 * | `RemoteMod.load` | 远端信息 + 行内强制刷新 |
 * | `ModUpdater` / `ModManifest` | 单个与批量"检查更新" |
 * | `PlatformVersion.platformDependencies()` | 详情层的依赖清单 |
 * | `matchInstalledMods` | 详情层里"这条依赖装没装" |
 *
 * 两条硬纪律：
 *  - 组合与测量期间不碰磁盘、不发网络。全部读与写都在 [OxideModsViewModel] 的 IO 上。
 *  - 同一方向上不出现第二个纵向滚动容器：外壳 [OxideSubWindow] 用
 *    `scrollable = false`，纵向空间全部交给那一个 [LazyColumn]。
 *
 * v1.7.0 设备截图里的第三个缺陷（"菜单太小、选中一个模组之后就没地方选第二个、
 * 按钮堆在列表上面"）全部由 [oxideModsPanelLayout] 这一笔账决定：
 *
 *  - 面板比别的子窗口大（[OxideSubWindowWideWidthFraction] /
 *    [OxideSubWindowWideHeightFraction]）并且**占满**它的高度上限，
 *    列表那一列才真的拿得到剩下的高度。
 *  - 批量动作横过来放进列表右侧的窄栏（[OxideModsBulkRail]）。选中任何一个模组
 *    都不会再从列表那里拿走一行纵向空间，因此"选了第一个就没法选第二个"消失。
 *    没有选中时这一栏整个不画，未选中的列表拿回全部宽度。
 */
@Composable
fun OxideModsSurface(
    version: Version,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    /** 非空时这一块包在子窗口外壳里，并带一个真的关闭按钮 */
    onClose: (() -> Unit)? = null,
) {
    // 先在组合期取到活动那一份 EventViewModel：viewModel 的初始化块不是 @Composable，
// 不能在里面调 stringResource / remember 之类的东西
val eventViewModel = rememberOxideEventViewModel()
val viewModel: OxideModsViewModel = viewModel(
        key = "OxideMods-${version.getVersionName()}"
    ) { OxideModsViewModel(version = version, eventViewModel = eventViewModel) }

    if (onClose != null) {
        OxideSubWindow(
            title = stringResource(R.string.oxide_mod_title, version.getVersionName()),
            onClose = onClose,
            maxPanelWidth = OxideModsPanelMaxWidth,
            // 只有模组管理这一块是"读一张列表"的，面板给宽一点、高一点；
            // 别的子窗口仍然拿到 OxideSubWindow 的默认比例
            widthFraction = OxideSubWindowWideWidthFraction,
            heightFraction = OxideSubWindowWideHeightFraction,
            // 占满高度上限：列表那一列靠 weight(1f) 拿剩下的高度，
            // 面板包住内容时那个权重量到的是 0
            fillHeight = true,
            // 外壳不滚：纵向滚动只留给里面那一个列表，避免同方向嵌套
            scrollable = false,
            modifier = modifier,
        ) {
            OxideModsContent(metrics = metrics, viewModel = viewModel)
        }
    } else {
        // 实例设置那一页自己已经有顶栏与返回，再套一层子窗口就是两层关闭；
        // 这一路因此只给内容，外壳由调用方提供
        Box(modifier = modifier.fillMaxSize()) {
            OxideModsContent(metrics = metrics, viewModel = viewModel)
        }
    }
}

// ---------------------------------------------------------------------------
// 版面（纯函数）
//
// 这一块面板的三个数——外壳吃掉的 chrome、批量栏有多宽、列表还剩多高——全都可以
// 脱离组合算出来，因此"选中任何东西都不会让列表变矮"这句话可以钉在单测里，
// 而不是靠人眼在截图上数行。
//
// 文字的行高不读 `Oxide.Type`：那几份跟着设置变，在没有 MMKV 的 JVM 单测里读不到。
// 这里把基准行高各存一份（与 `OxideLogPage` 里的 LOG_*_BASE 同一做法），再乘
// [OxideMetrics.guiScale]——几何与排版因此仍然是同一个比例。
// ---------------------------------------------------------------------------

/**
 * 模组面板的版面
 *
 * @param panelWidth 面板宽度上限
 * @param panelHeight 面板高度上限
 * @param chromeHeight 列表上方那一整块占掉的高度，见 [oxideModsChromeHeight]
 * @param railWidth 批量栏的宽度；没有选中时是 0.dp（那一栏整个不画）
 * @param listHeight 列表真正拿到的高度
 * @param rowHeight 一行模组的高度，见 [oxideModsRowHeight]
 */
@Immutable
internal data class OxideModsPanelLayout(
    val panelWidth: Dp,
    val panelHeight: Dp,
    val chromeHeight: Dp,
    val railWidth: Dp,
    val listHeight: Dp,
    val rowHeight: Dp,
) {
    /**
     * 列表这一块装得下几行模组
     *
     * 向下取整而不是向上：装不下第四行的那几像素不算"装得下"。
     */
    val visibleRows: Int
        get() = if (rowHeight <= 0.dp) 0 else (listHeight / rowHeight).toInt()
}

/** 模组这一块的面板宽度上限；比默认的 620dp 宽，但仍是一条上限而不是整窗 */
internal val OxideModsPanelMaxWidth: Dp = 860.dp

/**
 * 模组面板的版面
 *
 * [selected] 只影响 [OxideModsPanelLayout.railWidth]——批量栏是横向的，它不吃纵向
 * 空间。因此**列表高度与是否选中无关**，这正是本次要修的那条回归。
 *
 * [instanceFacts] 只决定那行 "This instance: … · loader Fabric" 在不在；
 * 它最多 [MODS_FACTS_LINES] 行小标签。
 */
internal fun oxideModsPanelLayout(
    availableWidth: Dp,
    availableHeight: Dp,
    metrics: OxideMetrics,
    selected: Boolean,
    instanceFacts: Boolean = false,
): OxideModsPanelLayout {
    val bounds = oxideSubWindowPanelBounds(
        availableWidth = availableWidth,
        availableHeight = availableHeight,
        maxPanelWidth = OxideModsPanelMaxWidth,
        widthFraction = OxideSubWindowWideWidthFraction,
        heightFraction = OxideSubWindowWideHeightFraction,
    )
    val chrome = oxideModsChromeHeight(metrics, instanceFacts)
    return OxideModsPanelLayout(
        panelWidth = bounds.width,
        panelHeight = bounds.height,
        chromeHeight = chrome,
        railWidth = if (selected) oxideModsRailWidth(metrics) else 0.dp,
        listHeight = (bounds.height - chrome).coerceAtLeast(0.dp),
        rowHeight = oxideModsRowHeight(metrics),
    )
}

/**
 * 列表上方那一整块占多高
 *
 * 外壳 + 面板自己的工具条，两部分都算：
 *
 *  - 外壳：[OxideSubWindowTitleBarHeight]（8dp 上下留白加一枚 24dp 的
 *    [OxideIconButton]）、那条 1dp 分隔线、内容上下各 [OxideSubWindowContentPadding]。
 *  - 工具条：小节标题那一行（实例信息并排写在它下面，因此标题行的高度是
 *    "标题行高 + 至多 [MODS_FACTS_LINES] 行小标签"与那枚 24dp 刷新按钮里更高的）、
 *    搜索 + 排序那一行、状态筛选 + 全选那一行，以及它们之间的 [OxideMetrics.secRowGap]。
 *
 * 不计入的只有**一次性反馈条**（错误与结果）：它们出现时本来就会占位置，但那是
 * 一次性的状态，不是这一块的常态版面。批量栏同样不在这里——它是横向的，
 * 见 [oxideModsPanelLayout]。
 */
internal fun oxideModsChromeHeight(metrics: OxideMetrics, instanceFacts: Boolean): Dp {
    val headerRow = maxOf(
        MODS_HEADER_ACTION,
        oxideModsLineHeight(MODS_ROW_TITLE_BASE, metrics) +
            if (instanceFacts) {
                // 实例信息那几行也是小标签，与行尾那几行同一份基准行高
                oxideModsLineHeight(MODS_LABEL_BASE, metrics) * MODS_FACTS_LINES
            } else {
                0.dp
            },
    )
    val searchRow = maxOf(metrics.secInputHeight, MODS_ROW_ACTION)
    val filterRow = maxOf(
        oxideModsLineHeight(MODS_ROW_TITLE_BASE, metrics) + metrics.secRowGap * 2,
        MODS_ROW_ACTION,
    )
    return OxideSubWindowTitleBarHeight + MODS_SHELL_DIVIDER +
        OxideSubWindowContentPadding * 2 +
        headerRow + metrics.secRowGap +
        searchRow + metrics.secRowGap +
        filterRow + metrics.secRowGap
}

/**
 * 批量栏有多宽
 *
 * 取分类列那一档宽度（[OxideMetrics.guiScale] 与宽度档都已经算在里面），而不是
 * 写死一个 dp：放大界面时这一栏跟着一起长，按钮上的字不会挤在一起；缩小界面时
 * 它也不会缩到装不下"取消选择"。
 */
internal fun oxideModsRailWidth(metrics: OxideMetrics): Dp =
    (155f * metrics.guiScale).coerceIn(132f, 260f).dp

/**
 * 一行模组有多高
 *
 * 行里并排的是：左侧图标（[OxideMetrics.navItemHeight]）、一列文字
 * （[Oxide.Type.BodyStrong] 的标题 + [Oxide.Type.MicroLabel] 的副标题，各一行）、
 * 若干枚 28dp 的图标按钮、以及选中态那枚 10dp 的勾选框。取最高的那个，加行上下
 * 各 [OxideMetrics.secRowGap]。
 *
 * 100% 下图标与那枚 28dp 的按钮最高（38dp），两行文字只有 21dp——把两者取大而不是
 * 只算文字，这一行才不会被低估成两行文字的高度。
 *
 * 刻意不把"已禁用""加载器不匹配"那几行按最坏情况算进来：那几行是**这一行**多出来的
 * 说明，把它们摊进所有行会让"能显示几行"这个数小得没有意义。
 */
internal fun oxideModsRowHeight(metrics: OxideMetrics): Dp {
    val lines = oxideModsLineHeight(MODS_ROW_TITLE_BASE, metrics) +
        oxideModsLineHeight(MODS_LABEL_BASE, metrics)
    return maxOf(metrics.navItemHeight, lines, MODS_ROW_ACTION) + metrics.secRowGap * 2
}

/** 文字的基准行高乘上界面缩放，与 [OxideMetrics] 里的几何同一个系数 */
private fun oxideModsLineHeight(base: TextStyle, metrics: OxideMetrics): Dp =
    oxideScaledTextStyle(base, metrics.guiScale).lineHeight.value.dp

/** 行标题的基准行高，与 `Oxide.Type.Body` 一致 */
private val MODS_ROW_TITLE_BASE = TextStyle(fontSize = 8.sp, lineHeight = 12.sp)

/** 小标签的基准行高，与 `Oxide.Type.MicroLabel` 一致 */
private val MODS_LABEL_BASE = TextStyle(fontSize = 6.sp, lineHeight = 9.sp)

/** 外壳那条 1dp 分隔线 */
private val MODS_SHELL_DIVIDER: Dp = 1.dp

/** 实例信息那一行最多几行 */
private const val MODS_FACTS_LINES: Int = 2

/** 小节标题那一行右侧那枚刷新按钮的边长，`OxideIconButton` 的默认值 */
private val MODS_HEADER_ACTION: Dp = 24.dp

/** 行尾图标按钮的边长，`OxideButton` 的固定高度 */
private val MODS_ROW_ACTION: Dp = 28.dp

// ---------------------------------------------------------------------------
// 内容
// ---------------------------------------------------------------------------

@Composable
private fun OxideModsContent(
    metrics: OxideMetrics,
    viewModel: OxideModsViewModel,
) {
    // 导航由宿主决定：这里只描述"我想在发现页打开这一条"
    val host = LocalOxideHostActions.current
    val state = viewModel.state
    val query = viewModel.query
    val filter = viewModel.stateFilter
    val sort = viewModel.sort
    val ascending = viewModel.ascending
    val selected = viewModel.selected

    // 组合期只做算术：磁盘与网络的结果早已在 [OxideModsViewModel] 里算成行了
    val rows = remember(state.rows, query, filter, sort, ascending) {
        sortOxideMods(filterOxideMods(state.rows, query, filter), sort, ascending)
    }
    val selectedRows = remember(rows, selected) { oxideModsSelectedRows(rows, selected) }
    val counts = remember(state.rows) { oxideModsStateCounts(state.rows) }
    val everythingSelected = remember(rows, selected) { oxideModsEverythingSelected(rows, selected) }

    // 远端匹配由 ViewModel 在扫描之后自己带起来，组合期不发起任何请求
    val labels = oxideModsMetaLabels()
    val countsText = stringResource(R.string.oxide_mgr_count, state.rows.size)

    // 整块内容占满面板高度：下面那一行 `weight(1f)` 才拿得到确定的高度，
    // 否则面板包住内容时列表量到的是 0（这正是 v1.7.0 里"只剩两条模组"的成因）
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 反馈条 --------------------------------------------------------
        state.error?.let { detail ->
            OxideSecErrorRow(
                metrics = metrics,
                title = stringResource(R.string.generic_error),
                detail = detail,
                dismissText = stringResource(R.string.generic_cancel),
                onDismiss = viewModel::dismissError,
            )
            Spacer(Modifier.height(metrics.secRowGap))
        }
        state.outcome?.let { outcome ->
            OxideSecNoticeRow(
                metrics = metrics,
                text = oxideModsOutcomeText(outcome),
                dismissText = stringResource(R.string.generic_cancel),
                onDismiss = viewModel::dismissOutcome,
            )
            Spacer(Modifier.height(metrics.secRowGap))
        }

        // ---- 标题 + 计数 + 刷新 --------------------------------------------
        // 实例自己的加载器写在标题下面同一列里，而不是自己再占一整行：
        // 它此前多花掉一整行加一段 2dp 间距，而屏幕上真正稀缺的是列表的高度。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.oxide_mod_section),
                    color = Oxide.FgStrong,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 判定不兼容时加载器就在这里，不在每一行里重复
                state.instanceLoaders.takeIf { it.isNotEmpty() }?.let { loaders ->
                    Text(
                        text = stringResource(
                            R.string.oxide_mod_instance_facts,
                            state.minecraftVersion.orEmpty(),
                            loaders.joinToString(", "),
                        ),
                        color = Oxide.FgDim,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!state.loading) {
                OxideBadge(text = countsText)
                Spacer(Modifier.width(metrics.secRowGap))
            }
            OxideIconButton(
                onClick = viewModel::rescan,
                enabled = !state.loading && !state.busy && !state.updating,
                glyph = "↻",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.generic_refresh)
                ),
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        // ---- 搜索 + 排序 ----------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideSecInput(
                metrics = metrics,
                value = query,
                onValueChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.generic_search),
                modifier = Modifier.weight(1f),
            )
            OxideButton(
                text = stringResource(
                    R.string.oxide_mgr_sort_label,
                    oxideModSortLabel(sort, ascending),
                ),
                onClick = viewModel::cycleSort,
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        // ---- 状态筛选 + 全选 -------------------------------------------------
        // 全选那一枚从"自己占一整行"挪进筛选这一行：它此前独占 28dp 加一段间距，
        // 而它自己并不随选中状态出现或消失——把它放进本来就有的一行，选中与否
        // 布局完全一样，这一块的纵向高度因此是常数。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                OxideModState.All to counts.all,
                OxideModState.Enabled to counts.enabled,
                OxideModState.Disabled to counts.disabled,
            ).forEach { (candidate, count) ->
                OxideSecChip(
                    label = stringResource(
                        R.string.oxide_mod_filter_count,
                        stringResource(candidate.titleRes()),
                        count,
                    ),
                    selected = filter == candidate,
                    metrics = metrics,
                    onClick = { viewModel.updateStateFilter(candidate) },
                    modifier = Modifier.weight(1f),
                )
            }
            OxideButton(
                text = stringResource(
                    if (everythingSelected) {
                        R.string.oxide_mgr_action_clear_selection
                    } else {
                        R.string.oxide_mgr_action_select_all
                    }
                ),
                onClick = { viewModel.toggleSelectAll(rows) },
                enabled = rows.isNotEmpty(),
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        // ---- 列表 + 批量栏 ---------------------------------------------------
        // 列表与批量栏并排。批量栏不吃纵向空间，因此勾选任何一个模组都不会让列表
        // 变矮——这正是 v1.7.0 截图里"选中第一个之后就没地方选第二个"的成因。
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> OxideSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = metrics.secRowGap),
                    ) {
                        OxideLoadingRow(text = stringResource(R.string.oxide_mgr_loading))
                    }

                    state.rows.isEmpty() -> OxideSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = metrics.secRowGap),
                    ) {
                        OxideEmptyState(
                            title = stringResource(R.string.oxide_mgr_empty_mods),
                            detail = stringResource(R.string.oxide_mgr_empty_mods_detail),
                        )
                    }

                    rows.isEmpty() -> OxideSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = metrics.secRowGap),
                    ) {
                        OxideEmptyState(
                            title = stringResource(R.string.generic_no_matching_items),
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    ) {
                        // 稳定键：去掉 .disabled 的文件名，启用/禁用改名之后仍然是同一行
                        items(rows, key = { row -> row.key }) { row ->
                            OxideModRowItem(
                                metrics = metrics,
                                row = row,
                                selected = row.key in selected,
                                selectionMode = selectedRows.isNotEmpty(),
                                busy = state.busy,
                                onToggleSelect = { viewModel.toggleSelected(row.key) },
                                onToggleEnabled = { viewModel.setEnabled(row.key, !row.enabled) },
                                onOpenDetails = { viewModel.openDetails(row.key) },
                                onUpdate = { viewModel.startUpdate(listOf(row.key)) },
                                onDelete = { viewModel.requestDelete(listOf(row.key)) },
                            )
                        }
                    }
                }
            }

            // 没有选中时这一栏整个不出现，列表因此拿回全部宽度
            if (selectedRows.isNotEmpty()) {
                Spacer(Modifier.width(metrics.secRowGap))
                OxideModsBulkRail(
                    metrics = metrics,
                    updatableCount = selectedRows.count { it.updatable },
                    busy = state.busy,
                    updating = state.updating,
                    onEnable = { viewModel.setEnabledForSelection(true) },
                    onDisable = { viewModel.setEnabledForSelection(false) },
                    onUpdate = {
                        viewModel.startUpdate(selectedRows.filter { it.updatable }.map { it.key })
                    },
                    onDelete = viewModel::requestDeleteSelected,
                    onClearSelection = { viewModel.clearSelection(rows) },
                )
            }
        }
    }

    // ---- 确认层 -------------------------------------------------------------
    state.pendingDelete.takeIf { it.isNotEmpty() }?.let { targets ->
        val labelsText = oxideModsDeleteLabels(targets)
        OxideConfirmDialog(
            title = stringResource(R.string.generic_warning),
            message = stringResource(R.string.oxide_mod_delete_confirm, labelsText.joinToString("\n")),
            confirmText = stringResource(R.string.generic_delete),
            cancelText = stringResource(R.string.generic_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
            dismissByDialog = !state.busy,
        )
    }

    state.updateManifests?.let { manifests ->
        OxideModsUpdateDialog(
            metrics = metrics,
            manifests = manifests,
            onConfirm = viewModel::confirmUpdate,
            onCancel = viewModel::cancelUpdate,
        )
    }

    state.details?.let { details ->
        val row = state.rows.firstOrNull { it.key == details.key }
        if (row != null) {
            OxideModDetailsDialog(
                metrics = metrics,
                row = row,
                details = details,
                labels = labels,
                busy = state.busy,
                // "重新读一次远端信息"放在详情层而不是行尾：一行上已经有
                // 更新、详情、删除、开关四个动作，再塞一个会把 360dp 宽的行挤扁
                onRefreshRemote = { viewModel.refreshRemote(row) },
                // 没有平台身份时整条为 null，于是那枚按钮根本不画
                onOpenInDiscover = oxideModDiscoverRequest(row)?.let { request ->
                    { host.openDiscoverProject(request) }
                },
                onDismiss = viewModel::closeDetails,
            )
        }
    }
}

/**
 * 这一行的"在发现页查看"该怎么走；没有可用身份时是 null，调用方据此把动作藏起来
 *
 * **类别跟着内容类别走**，不写死成模组：这一块是模组管理器，所以传的是
 * [PlatformClasses.MOD]；将来任何带平台身份的内容类别都自动落到自己那一栏。
 *
 * 开一个搜不出任何东西的空白搜索比没有这个按钮更糟，所以没有平台 id、也没有
 * slug/标题时直接返回 null。
 */
private fun oxideModDiscoverRequest(row: OxideModRow): OxideDiscoverRequest? = discoverOpenRequest(
    classes = PlatformClasses.MOD,
    platformName = row.platform,
    projectId = row.projectId,
    projectSlug = row.projectSlug,
    projectTitle = row.projectTitle ?: row.displayName,
)

// ---------------------------------------------------------------------------
// 批量栏
// ---------------------------------------------------------------------------

/**
 * 列表右侧那条批量动作栏
 *
 * v1.7.0 的批量条是**两整行 28dp 的按钮加一段间距**，只在有选中时出现：
 * 一勾选就把列表的高度砍掉约两行，于是"选中第一个之后就没地方选第二个"。
 *
 * 这里把同样这几个动作横过来放进列表右侧：宽度取 [oxideModsRailWidth]，
 * 高度由内容决定，与列表并排而不是压在它上面。每个动作的文案、启用条件与回调
 * 与此前逐字相同——变的只有摆放的位置。
 *
 * 它是横向的一栏，因此**不吃纵向空间**：这是 [oxideModsPanelLayout] 里
 * `selected` 只影响 `railWidth` 的原因。没有选中时调用方整个不画它，
 * 列表因此拿回全部宽度。
 *
 * 宽度用 [oxideModsRailWidth] 而不是写死：按钮上有"取消选择"这样的长文案，
 * 宽度必须跟着界面缩放走。
 */
@Composable
internal fun OxideModsBulkRail(
    metrics: OxideMetrics,
    /** 选中的那些里有多少个能进更新流程；为 0 时"更新"不可点 */
    updatableCount: Int,
    busy: Boolean,
    updating: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideContentSurface(
        modifier = modifier.width(oxideModsRailWidth(metrics)),
        shape = Oxide.RadiusControl,
        contentPadding = PaddingValues(
            horizontal = metrics.secControlPadding,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideButton(
            text = stringResource(R.string.oxide_mod_bulk_enable),
            onClick = onEnable,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.oxide_mod_bulk_disable),
            onClick = onDisable,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.oxide_mgr_action_update),
            onClick = onUpdate,
            enabled = !updating && updatableCount > 0,
            // 更新是这一组里唯一的主动作，与此前那一处相同
            tone = OxideButtonTone.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.generic_delete),
            onClick = onDelete,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.oxide_mgr_action_clear_selection),
            onClick = onClearSelection,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// 一行
// ---------------------------------------------------------------------------

@Composable
private fun OxideModRowItem(
    metrics: OxideMetrics,
    row: OxideModRow,
    selected: Boolean,
    selectionMode: Boolean,
    busy: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onOpenDetails: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val loaders = remember(row) { oxideModsLoaderLabels(row) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            // 选中态由 toggleable 的语义暴露，因此不只靠底色区分
            .toggleable(
                value = selected,
                role = if (selectionMode) Role.Checkbox else Role.Button,
                onValueChange = { onToggleSelect() },
            )
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            OxideContentCheckBox(selected = selected)
            Spacer(Modifier.width(metrics.secRowGap))
        }

        OxideModIcon(row = row, size = metrics.navItemHeight)

        Spacer(Modifier.width(metrics.secRowGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName,
                color = if (row.enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 版本与作者是最常被搜的两样，紧跟着标题；其余元数据在详情层
            val subline = buildList {
                row.modVersion?.takeIf { it.isNotBlank() }?.let { add("v$it") }
                row.authors.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                    ?.let { add(it.joinToString(", ")) }
                add(formatModBytes(row.sizeBytes))
            }.joinToString(" · ")
            if (subline.isNotBlank()) {
                Text(
                    text = subline,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!row.enabled) {
                Text(
                    text = stringResource(R.string.oxide_mod_disabled),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.verdict == ModLoaderVerdict.Mismatch) {
                // 不兼容不只靠颜色：这里有一行文案，写明它要哪个加载器
                Text(
                    text = stringResource(
                        R.string.oxide_mod_loader_mismatch_row,
                        row.instanceLoaders.joinToString(", "),
                        loaders.joinToString(" + "),
                    ),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.loadingRemote) {
                Text(
                    text = stringResource(R.string.oxide_mgr_reading),
                    color = Oxide.FgDim,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(metrics.secRowGap))

        loaders.take(2).forEach { loader ->
            OxideBadge(text = loader)
            Spacer(Modifier.width(2.dp))
        }

        if (row.loadingRemote) {
            OxideBadge(text = stringResource(R.string.oxide_mod_reading_badge))
        }

        Spacer(Modifier.width(metrics.secRowGap))

        if (row.checkRemote) {
            OxideIconButton(
                onClick = onUpdate,
                enabled = !busy && row.updatable,
                glyph = "↑",
                size = metrics.secControlHeight,
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_mod_action_update_one)
                ),
            )
        }
        OxideIconButton(
            onClick = onOpenDetails,
            glyph = "ⓘ",
            size = metrics.secControlHeight,
            modifier = Modifier.oxideIconDescription(
                stringResource(R.string.mods_manage_info)
            ),
        )
        OxideIconButton(
            onClick = onDelete,
            enabled = !busy,
            glyph = "✕",
            size = metrics.secControlHeight,
            modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_delete)),
        )

        // 状态由这一小块承担：它本身是 Role.Switch，朗读时也知道是个开关。
        // 里面的开关自己不再重复朗读，否则同一信息会被念两遍。
        Box(
            modifier = Modifier.toggleable(
                value = row.enabled,
                enabled = !busy,
                role = Role.Switch,
                onValueChange = { onToggleEnabled() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.clearAndSetSemantics { }) {
                OxideToggle(
                    checked = row.enabled,
                    onCheckedChange = { onToggleEnabled() },
                )
            }
        }
    }
}

/**
 * 行首图标
 *
 * 优先用项目封面（`ModProject.iconUrl`），没有封面才用本地元数据里的图标字节
 * （`LocalMod.icon`）。两者都没有时画一块底板 + 首字母，而不是留一个空洞。
 *
 * 解码与下载都由 Coil 在自己的线程上做，组合期不碰像素数据。
 */
@Composable
private fun OxideModIcon(row: OxideModRow, size: Dp) {
    val shape = Oxide.RadiusBlock
    val model: Any? = remember(row.projectIconUrl, row.iconBytes) {
        row.projectIconUrl?.takeIf { it.isNotBlank() } ?: row.iconBytes
    }
    val tint = if (row.enabled) Color.White else Color(0xFF6A6A6A)

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Oxide.BgButtonHover, Oxide.BgElevated))
            )
            .border(BorderStroke(1.dp, Oxide.Line), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                // 禁用态不去色：去掉的是"可点/可读"的暗示，不是整个图标
                alpha = if (row.enabled) 1f else 0.45f,
            )
        } else {
            Text(
                text = row.displayName.trim().take(1).uppercase().ifEmpty { "?" },
                color = tint,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 更新确认
// ---------------------------------------------------------------------------

@Composable
private fun OxideModsUpdateDialog(
    metrics: OxideMetrics,
    manifests: List<SelectableModManifest>,
    onConfirm: (List<SelectableModManifest>) -> Unit,
    onCancel: () -> Unit,
) {
    OxideDialogShell(
        title = stringResource(R.string.mods_update),
        onDismissRequest = onCancel,
        dismissByDialog = false,
        metrics = metrics,
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.oxide_mgr_update_confirm_title, manifests.size),
                    color = Oxide.FgStrong,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                )
                Spacer(Modifier.height(metrics.secRowGap))
                manifests.forEach { manifest ->
                    val checked by manifest.selected.collectAsStateWithLifecycle()
                    OxideSecPickerRow(
                        label = manifest.data.project.title,
                        value = manifest.new.platformDisplayName(),
                        selected = checked,
                        onClick = { manifest.updateSelected(!checked) },
                    )
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onCancel,
            )
            OxideButton(
                text = stringResource(R.string.mods_update),
                onClick = {
                    // 勾选状态在按下这一刻才读，不缓存到重组里
                    onConfirm(manifests.filter { it.selected.value })
                },
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

// ---------------------------------------------------------------------------
// 详情
// ---------------------------------------------------------------------------

@Composable
private fun OxideModDetailsDialog(
    metrics: OxideMetrics,
    row: OxideModRow,
    details: OxideModDetails,
    labels: OxideModMetaLabels,
    busy: Boolean,
    onRefreshRemote: () -> Unit,
    /** 这一行在发现页里对应的那一条；null 表示没有可用身份，动作整个不画 */
    onOpenInDiscover: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OxideDialogShell(
        title = row.displayName,
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                oxideModsMeta(row, labels).forEach { meta ->
                    OxideSettingRow(label = meta.field.labelOf(labels), value = meta.value)
                }

                row.description?.takeIf { it.isNotBlank() }?.let { description ->
                    OxideSectionLabel(text = stringResource(R.string.mods_manage_description))
                    Text(
                        text = description,
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )
                }

                row.projectSlug?.takeIf { it.isNotBlank() }?.let { slug ->
                    OxideSectionLabel(text = stringResource(R.string.oxide_mod_section_project))
                    Text(
                        text = buildList {
                            row.platform?.let { add(it) }
                            add(slug)
                            row.remoteDatePublished?.takeIf { it.isNotBlank() }?.let {
                                add(stringResource(R.string.oxide_mod_published, it))
                            }
                        }.joinToString(" · "),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    )
                }

                OxideSectionLabel(text = stringResource(R.string.oxide_mod_section_dependencies))
                when {
                    details.loading -> OxideLoadingRow(text = stringResource(R.string.oxide_mgr_loading))
                    details.failed -> Text(
                        text = stringResource(R.string.oxide_mod_dependencies_failed),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )

                    details.dependencies.isEmpty() -> Text(
                        text = stringResource(R.string.oxide_mod_dependencies_none),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )

                    else -> details.dependencies.forEach { dep ->
                        val type = stringResource(R.string.oxide_mod_dependency_type, dep.type)
                        OxideSettingRow(
                            // 取不到项目名时，主标题是一句"平台没给名字"，数字 id 降到副标题：
                            // 它仍然有用（同一个项目的多条依赖靠它对上），但它不是名字。
                            label = if (dep.resolved) {
                                dep.title
                            } else {
                                stringResource(
                                    R.string.download_assets_dependency_project_unavailable
                                )
                            },
                            hint = if (dep.resolved) {
                                type
                            } else {
                                stringResource(R.string.oxide_dis_dep_project_id, dep.projectId) +
                                        " · " + type
                            },
                            value = if (dep.installed) {
                                stringResource(R.string.oxide_mod_dependency_installed)
                            } else {
                                stringResource(R.string.oxide_mod_dependency_missing)
                            },
                        )
                    }
                }
            }
        },
        actions = {
            if (row.checkRemote) {
                OxideButton(
                    text = stringResource(R.string.generic_refresh),
                    onClick = onRefreshRemote,
                    enabled = !busy,
                )
            }
            // 没有平台身份、也没有 slug/标题时整个不画：开一个空的搜索页比没有这个按钮更糟。
            // 文案复用"发现页"这个名字（已经本地化），不新增字符串。
            onOpenInDiscover?.let {
                OxideButton(
                    text = stringResource(R.string.oxide_nav_discover),
                    onClick = onOpenInDiscover,
                    enabled = !busy,
                )

            }
            OxideButton(
                text = stringResource(R.string.generic_close),
                onClick = onDismiss,
            )
        },
    )
}

// ---------------------------------------------------------------------------
// 小零件
// ---------------------------------------------------------------------------

/** 一条低调的一次性结果提示，和错误条成对出现 */
@Composable
private fun OxideSecNoticeRow(
    metrics: OxideMetrics,
    text: String,
    dismissText: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(text = dismissText, onClick = onDismiss, tone = OxideButtonTone.Ghost)
    }
}

/** 字段名；[OxideModMetaLabels] 里的原文，不是另抄一份 */
private fun OxideModMetaField.labelOf(labels: OxideModMetaLabels): String = when (this) {
    OxideModMetaField.FileName -> labels.fileName
    OxideModMetaField.FileSize -> labels.fileSize
    OxideModMetaField.LocalName -> labels.localName
    OxideModMetaField.Version -> labels.version
    OxideModMetaField.Author -> labels.author
    OxideModMetaField.Loader -> labels.loader
    OxideModMetaField.GameVersion -> labels.gameVersion
    OxideModMetaField.Compatibility -> labels.compatible
}

@Composable
private fun oxideModsMetaLabels() = OxideModMetaLabels(
    fileName = stringResource(R.string.oxide_mod_field_file_name),
    fileSize = stringResource(R.string.oxide_mod_field_file_size),
    localName = stringResource(R.string.oxide_mod_field_local_name),
    version = stringResource(R.string.oxide_mod_field_version),
    author = stringResource(R.string.oxide_mod_field_author),
    loader = stringResource(R.string.oxide_mod_field_loader),
    gameVersion = stringResource(R.string.oxide_mod_field_game_version),
    compatible = stringResource(R.string.oxide_mod_field_compatibility),
    loaderless = stringResource(R.string.oxide_mod_compat_loaderless),
    noInstanceLoader = stringResource(R.string.oxide_mod_compat_no_instance_loader),
    mismatched = stringResource(R.string.oxide_mod_compat_mismatch),
    unknownFile = stringResource(R.string.oxide_mod_unknown_file),
)

/** 三个状态筛选各自的标题 */
private fun OxideModState.titleRes(): Int = when (this) {
    OxideModState.All -> R.string.oxide_mod_state_all
    OxideModState.Enabled -> R.string.oxide_mod_state_enabled
    OxideModState.Disabled -> R.string.oxide_mod_state_disabled
}

/** 一次性结果对应的文案 */
@Composable
private fun oxideModsOutcomeText(outcome: OxideModsOutcome): String = when (outcome) {
    is OxideModsOutcome.Toggled ->
        if (outcome.skipped == 0) {
            stringResource(R.string.oxide_mod_done_toggled, outcome.changed)
        } else {
            stringResource(R.string.oxide_mod_done_toggled_partial, outcome.changed, outcome.skipped)
        }

    is OxideModsOutcome.Deleted ->
        if (outcome.failed == 0) {
            stringResource(R.string.oxide_mod_done_deleted, outcome.deleted)
        } else {
            stringResource(R.string.oxide_mod_done_deleted_partial, outcome.deleted, outcome.failed)
        }

    OxideModsOutcome.Updated -> stringResource(R.string.oxide_mod_done_updated)
    OxideModsOutcome.AlreadyUpToDate -> stringResource(R.string.oxide_mod_done_up_to_date)
    OxideModsOutcome.NoVersionInfo -> stringResource(R.string.oxide_mod_no_version_info)
    OxideModsOutcome.NothingToDo -> stringResource(R.string.oxide_mod_nothing_to_do)
}