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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.mod.ModLoaderVerdict
import dev.oxide.launcher.game.version.mod.update.SelectableModManifest
import dev.oxide.launcher.ui.theme.Oxide

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
            maxPanelWidth = 720.dp,
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
// 内容
// ---------------------------------------------------------------------------

@Composable
private fun OxideModsContent(
    metrics: OxideMetrics,
    viewModel: OxideModsViewModel,
) {
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

    Column(modifier = Modifier.fillMaxWidth()) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.oxide_mod_section),
                color = Oxide.FgStrong,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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

        // 实例自己的加载器；判定不兼容时它就在这里，不在每一行里重复
        state.instanceLoaders.takeIf { it.isNotEmpty() }?.let { loaders ->
            Spacer(Modifier.height(2.dp))
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

        // ---- 状态筛选 --------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
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
        }

        Spacer(Modifier.height(metrics.secRowGap))

        // ---- 批量条 ----------------------------------------------------------
        if (selectedRows.isNotEmpty()) {
            val updatable = selectedRows.filter { it.updatable }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                OxideButton(
                    text = stringResource(R.string.oxide_mod_bulk_enable),
                    onClick = { viewModel.setEnabledForSelection(true) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.oxide_mod_bulk_disable),
                    onClick = { viewModel.setEnabledForSelection(false) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(metrics.secRowGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                OxideButton(
                    text = stringResource(R.string.oxide_mgr_action_update),
                    onClick = { viewModel.startUpdate(updatable.map { it.key }) },
                    enabled = !state.updating && updatable.isNotEmpty(),
                    tone = OxideButtonTone.Primary,
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.generic_delete),
                    onClick = viewModel::requestDeleteSelected,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                OxideButton(
                    text = stringResource(R.string.oxide_mgr_action_clear_selection),
                    onClick = { viewModel.clearSelection(rows) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(metrics.secRowGap))
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
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(metrics.secRowGap))

        // ---- 列表 ------------------------------------------------------------
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                onDismiss = viewModel::closeDetails,
            )
        }
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
                        OxideSettingRow(
                            label = dep.title,
                            hint = stringResource(R.string.oxide_mod_dependency_type, dep.type),
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