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

import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.rememberHiltViewModelFactory
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.defaultViewModelCreationExtras
import androidx.lifecycle.defaultViewModelProviderFactory
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.filemanager.config.FmConfig
import dev.oxide.launcher.filemanager.logic.entry.FmEntry
import dev.oxide.launcher.filemanager.logic.trash.TrashItem
import dev.oxide.launcher.filemanager.viewmodel.EditorUiState
import dev.oxide.launcher.filemanager.viewmodel.FileManagerViewModel
import dev.oxide.launcher.filemanager.viewmodel.FmInitState
import dev.oxide.launcher.filemanager.viewmodel.SortConfig
import dev.oxide.launcher.filemanager.viewmodel.TrashViewState
import dev.oxide.launcher.filemanager.viewmodel.entryPathKey
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.ui.code_editor.EditorState
import dev.oxide.launcher.ui.code_editor.SoraEditor
import dev.oxide.launcher.ui.code_editor.TextMateRegistry
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEADark
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEALight
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.formatDate
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.nio.file.Path

/**
 * 文件页
 *
 * 取代 `FileManagerActivity` 里的 `FileManagerRootScreen` / `FmMainPage` /
 * `FmTrashScreen` / `FmEditorScreen`：浏览、导航、选择、新建文件夹、删除到回收站、
 * 回收站与文本编辑器全部搬进 Oxide 语言。
 *
 * 后端完全是既有的那个 [FileManagerViewModel]，一处都没有换：
 * 浏览、选择、回收站与编辑器仍由它的控制器负责 IO 与协程，
 * 这一页只负责把这些能力画出来，并把进度、失败与确认都留在界面上。
 *
 * 条目永远来自 `visibleEntries` 这一个真实列表；列表键取路径字符串
 * （`Path` 的 `hashCode` 会随实例变化，不能拿它当稳定键）。
 * 长任务由 `cancelCurrentTask()` 取消，因此复制、压缩这类操作随时可以中断。
 */
@Composable
fun OxideFilesPage(
    metrics: OxideMetrics,
    rootPath: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val viewModel = rememberOxideFilesViewModel(rootPath)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initState by viewModel.initState.collectAsStateWithLifecycle()
    val editorUi by viewModel.editorUi.collectAsStateWithLifecycle()

    var errorText by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf<String?>(null) }
    var deleteArmed by remember { mutableStateOf(false) }
    var editorPath by remember { mutableStateOf<Path?>(null) }

    // 初始化只在第一次进入这一页时做一次，ViewModel 自己也会拒绝重复初始化
    LaunchedEffect(viewModel) {
        viewModel.initialize()
        viewModel.errorEvents.collect { errorText = it }
    }

    // 选中的条目一旦消失（被删掉、被移走），删除确认就该跟着撤掉，
    // 而不是留一个指向旧条目的条子
    LaunchedEffect(state.selection, state.visibleEntries) {
        if (deleteArmed && state.selection.isEmpty()) deleteArmed = false
    }

    val trash = state.trashView as? TrashViewState.Opened
    val editing = editorPath
    val initFailed = initState as? FmInitState.Failed

    Box(modifier = modifier.fillMaxSize()) {
        when {
            initState is FmInitState.Pending -> OxideLoadingRow(
                text = stringResource(R.string.oxide_sec_files_loading),
                modifier = Modifier.align(Alignment.Center),
            )

            initFailed != null -> OxideSecErrorRow(
                metrics = metrics,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(all = metrics.pagePaddingH),
                title = stringResource(R.string.generic_error),
                detail = stringResource(
                    R.string.oxide_sec_files_init_failed,
                    initFailed.message,
                ),
                dismissText = stringResource(R.string.oxide_sec_topbar_back),
                onDismiss = onDismiss,
            )

            editing != null -> OxideFilesEditor(
                metrics = metrics,
                viewModel = viewModel,
                editorUi = editorUi,
                onBack = { editorPath = null },
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                OxideFilesHeader(
                    metrics = metrics,
                    subtitle = stringResource(
                        R.string.oxide_sec_files_subtitle,
                        state.folderCount,
                        state.fileCount,
                    ),
                    onDismiss = onDismiss,
                )

                errorText?.let { message ->
                    OxideSecErrorRow(
                        metrics = metrics,
                        modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                        title = stringResource(R.string.generic_error),
                        detail = message,
                        dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                        onDismiss = { errorText = null },
                    )
                    Spacer(Modifier.height(metrics.cardGap))
                }

                createName?.let { name ->
                    OxideSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = metrics.pagePaddingH),
                        contentPadding = PaddingValues(
                            horizontal = metrics.cardGap,
                            vertical = metrics.secRowGap,
                        ),
                    ) {
                        OxideSecInput(
                            metrics = metrics,
                            value = name,
                            onValueChange = { createName = it },
                            placeholder = stringResource(R.string.oxide_sec_files_new_name),
                            label = stringResource(R.string.oxide_sec_files_new_folder),
                            onDone = {
                                if (name.isNotBlank()) {
                                    createName = null
                                    viewModel.submitCreate(name, isFolder = true) { }
                                }
                            },
                        )
                        Spacer(Modifier.height(metrics.secRowGap))
                        Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                            OxideButton(
                                text = stringResource(R.string.generic_cancel),
                                onClick = { createName = null },
                                modifier = Modifier.weight(1f),
                            )
                            OxideButton(
                                text = stringResource(R.string.oxide_sec_files_create),
                                onClick = {
                                    createName = null
                                    viewModel.submitCreate(name, isFolder = true) { }
                                },
                                enabled = name.isNotBlank(),
                                tone = OxideButtonTone.Primary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(metrics.cardGap))
                }

                if (deleteArmed && state.selection.isNotEmpty()) {
                    OxideSecConfirmBar(
                        metrics = metrics,
                        modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                        text = stringResource(
                            R.string.oxide_sec_files_delete_message,
                            state.selection.size,
                        ),
                        confirmText = stringResource(R.string.oxide_sec_files_delete_confirm),
                        dismissText = stringResource(R.string.generic_cancel),
                        onConfirm = {
                            deleteArmed = false
                            viewModel.deleteSelected(toTrash = true)
                        },
                        onDismiss = { deleteArmed = false },
                    )
                    Spacer(Modifier.height(metrics.cardGap))
                }

                state.taskProgress
                    ?.takeIf { it.kind.shouldShowProgressDialog }
                    ?.let { progress ->
                        OxideFilesProgressRow(
                            metrics = metrics,
                            completed = progress.completed,
                            total = progress.total,
                            bytesDone = progress.bytesDone,
                            bytesTotal = progress.bytesTotal,
                            onCancel = { viewModel.cancelCurrentTask() },
                        )
                        Spacer(Modifier.height(metrics.cardGap))
                    }

                if (trash != null) {
                    OxideFilesTrash(
                        metrics = metrics,
                        viewModel = viewModel,
                        opened = trash,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onClose = {
                            viewModel.closeTrash()
                            deleteArmed = false
                        },
                    )
                } else {
                    OxideFilesBrowser(
                        metrics = metrics,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onNewFolder = { createName = "" },
                        onRequestDelete = { deleteArmed = true },
                        onOpenTrash = { viewModel.loadTrashList() },
                        onOpenEditor = { path -> editorPath = path },
                    )
                }
            }
        }
    }
}

/**
 * 拿到 [FileManagerViewModel]
 *
 * 它是需要 `KEY_ROOT_PATH` 的 Hilt ViewModel，而这一版的 `hiltViewModel` 不接受
 * CreationExtras，所以这里用 `viewModel(...)` 的 extras 重载：
 * 先取宿主默认的 extras（里面已经带好 SavedStateHandle 与 ViewModelStore），
 * 再补上根目录。key 里带上根目录，换一个根目录就换一个实例。
 */
@Composable
private fun rememberOxideFilesViewModel(rootPath: String): FileManagerViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val factory = rememberHiltViewModelFactory(owner.defaultViewModelProviderFactory)
    val extras = remember(rootPath, owner) {
        MutableCreationExtras(owner.defaultViewModelCreationExtras).apply {
            set(
                DEFAULT_ARGS_KEY,
                Bundle().apply { putString(FileManagerViewModel.KEY_ROOT_PATH, rootPath) }
            )
        }
    }
    return viewModel(
        viewModelStoreOwner = owner,
        key = "oxideFiles:$rootPath",
        factory = factory,
        extras = extras,
    )
}

/** 顶部那一行：返回 + 标题 + 计数 */
@Composable
private fun OxideFilesHeader(
    metrics: OxideMetrics,
    subtitle: String,
    onDismiss: () -> Unit,
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
            Spacer(Modifier.width(6.dp))
            OxidePageTitle(
                text = stringResource(R.string.oxide_sec_files_title),
                modifier = Modifier.weight(1f),
            )
        }
        OxideSectionLabel(text = subtitle)
        Spacer(Modifier.height(metrics.sectionGap))
    }
}

/** 正在跑的长任务：真实进度 + 一个真的能取消的按钮 */
@Composable
private fun OxideFilesProgressRow(
    metrics: OxideMetrics,
    completed: Int,
    total: Int,
    bytesDone: Long,
    bytesTotal: Long,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.pagePaddingH)
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusControl)
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.oxide_sec_files_task_title),
                color = Oxide.FgStrong,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.oxide_sec_files_task_counts,
                    completed,
                    total,
                    formatFileSize(bytesDone),
                    formatFileSize(bytesTotal),
                ),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (total > 0) {
                Spacer(Modifier.height(4.dp))
                OxideProgressBar(progress = completed.toFloat() / total.toFloat())
            }
        }
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.oxide_sec_files_task_cancel),
            onClick = onCancel,
            tone = OxideButtonTone.Secondary,
        )
    }
}

// ---------------------------------------------------------------------------
// 浏览
// ---------------------------------------------------------------------------

@Composable
private fun OxideFilesBrowser(
    metrics: OxideMetrics,
    viewModel: FileManagerViewModel,
    modifier: Modifier = Modifier,
    onNewFolder: () -> Unit,
    onRequestDelete: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenEditor: (Path) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val raw = state.rawList
    var multiSelect by remember { mutableStateOf(false) }
    // 换目录之后不再留着上一次的模式，避免"看不见的选中"继续生效
    LaunchedEffect(raw?.currentDir) { multiSelect = false }

    Column(modifier = modifier.padding(horizontal = metrics.pagePaddingH)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OxideFilesNavButton(
                        glyph = "←",
                        description = stringResource(R.string.oxide_sec_files_back),
                        enabled = state.canNavigateBack,
                        onClick = { viewModel.back() },
                    )
                    OxideFilesNavButton(
                        glyph = "→",
                        description = stringResource(R.string.oxide_sec_files_forward),
                        enabled = state.canNavigateForward,
                        onClick = { viewModel.forward() },
                    )
                    OxideFilesNavButton(
                        glyph = "↑",
                        description = stringResource(R.string.oxide_sec_files_up),
                        enabled = state.canBack,
                        onClick = { viewModel.goParent() },
                    )
                    Spacer(Modifier.width(metrics.secRowGap))
                    Text(
                        text = raw?.currentDir?.toString()
                            ?: stringResource(R.string.oxide_sec_files_breadcrumb_root),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(metrics.secRowGap))
                    OxideIconButton(
                        onClick = { viewModel.setSortConfig(cycleSort(state.sortConfig)) },
                        glyph = "⇅",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_sec_files_sort)
                        ),
                    )
                    OxideIconButton(
                        onClick = { viewModel.refresh() },
                        glyph = "↻",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_sec_files_refresh)
                        ),
                    )
                }

                Spacer(Modifier.height(metrics.secRowGap))
                OxideSecDivider()
                Spacer(Modifier.height(metrics.secRowGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OxideIconButton(
                        onClick = onOpenTrash,
                        glyph = "⌫",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.fm_trash_title)
                        ),
                    )
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_files_new_folder),
                        onClick = onNewFolder,
                        enabled = raw?.writable == true,
                        modifier = Modifier.weight(1f),
                    )
                    OxideSecPickerRow(
                        label = stringResource(R.string.oxide_sec_files_select_all),
                        selected = multiSelect,
                        onClick = {
                            multiSelect = !multiSelect
                            if (!multiSelect) viewModel.clearSelection()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.selection.isNotEmpty()) {
                        OxideButton(
                            text = stringResource(
                                R.string.oxide_sec_files_selected,
                                state.selection.size,
                            ),
                            onClick = onRequestDelete,
                            tone = OxideButtonTone.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 排序当前值以文字给出，符号本身不作为唯一信息
                Text(
                    text = sortLabel(state.sortConfig),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (raw?.writable == false) {
                    Text(
                        text = stringResource(R.string.oxide_sec_files_read_only_dir),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        if (multiSelect) {
                            R.string.oxide_sec_files_select_on_hint
                        } else {
                            R.string.oxide_sec_files_select_hint
                        }
                    ),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        OxideSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            if (state.visibleEntries.isEmpty()) {
                OxideEmptyState(
                    title = if (state.refreshing) {
                        stringResource(R.string.oxide_sec_files_loading)
                    } else {
                        stringResource(R.string.oxide_sec_files_empty)
                    },
                )
                return@OxideSurface
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(
                    items = state.visibleEntries,
                    // 路径字符串才是稳定键：Path 的 hashCode 会随实例变化
                    key = { entry -> entryPathKey(entry) },
                ) { entry ->
                    OxideFilesEntryRow(
                        metrics = metrics,
                        entry = entry,
                        selected = entryPathKey(entry) in state.selection,
                        multiSelect = multiSelect,
                        onOpen = {
                            when {
                                multiSelect -> viewModel.toggleSelection(entry)
                                entry.isDirectory -> viewModel.enterDirectory(entry)
                                else -> onOpenEditor(entry.path)
                            }
                        },
                        onStageDelete = {
                            viewModel.stageSingleDelete(entry)
                            onRequestDelete()
                        },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
    }
}

/** 列表里的一行 */
@Composable
private fun OxideFilesEntryRow(
    metrics: OxideMetrics,
    entry: FmEntry,
    selected: Boolean,
    multiSelect: Boolean,
    onOpen: () -> Unit,
    onStageDelete: () -> Unit,
) {
    val kindLabel = stringResource(
        if (entry.isDirectory) R.string.oxide_sec_files_folder else R.string.oxide_sec_files_file
    )
    val sizeLabel = remember(entry) {
        if (entry.isDirectory) "" else formatFileSize(entry.size)
    }
    val modifiedLabel = remember(entry) { formatDate(entry.modifiedMs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            // 多选模式下整行是一个勾选框，单选模式下退化成普通点击，
            // 因此同一个 modifier 就够用，选中态由语义而不是底色单独承担
            .toggleable(
                value = selected,
                role = if (multiSelect) Role.Checkbox else Role.Button,
                onValueChange = { onOpen() },
            )
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect) {
            OxideSecCheckbox(selected = selected)
            Spacer(Modifier.width(metrics.secRowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (sizeLabel.isEmpty()) {
                    kindLabel
                } else {
                    "$kindLabel · $sizeLabel · " +
                        stringResource(R.string.oxide_sec_files_modified, modifiedLabel)
                },
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (multiSelect) {
            Spacer(Modifier.width(metrics.secRowGap))
            OxideIconButton(
                onClick = onStageDelete,
                glyph = "✕",
                enabled = entry.writable,
                modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_delete)),
            )
        }
    }
}

/** 一个 10dp 的勾选框：选中时中间那块才是实心，因此不只靠边框区分 */
@Composable
private fun OxideSecCheckbox(selected: Boolean) {
    Box(
        modifier = Modifier
            .width(10.dp)
            .height(10.dp)
            .clip(Oxide.RadiusBadge)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.FgMuted else Oxide.Line2),
                Oxide.RadiusBadge,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(4.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )
        }
    }
}

/** 顶栏上的导航小按钮 */
@Composable
private fun OxideFilesNavButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OxideIconButton(
        onClick = onClick,
        glyph = glyph,
        enabled = enabled,
        modifier = Modifier.oxideIconDescription(description),
    )
}

/** 排序按钮在三种字段之间轮转，并把升 / 降也一起翻过来 */
private fun cycleSort(current: SortConfig): SortConfig = current.copy(
    field = when (current.field) {
        FmConfig.SortField.NAME -> FmConfig.SortField.SIZE
        FmConfig.SortField.SIZE -> FmConfig.SortField.MODIFIED
        FmConfig.SortField.MODIFIED -> FmConfig.SortField.NAME
    },
    ascending = !current.ascending,
)

/** 排序的当前取值，以文字写出来；按钮上的符号只是提示，不是唯一信息 */
@Composable
private fun sortLabel(config: SortConfig): String {
    val field = when (config.field) {
        FmConfig.SortField.NAME -> stringResource(R.string.fm_sort_name)
        FmConfig.SortField.SIZE -> stringResource(R.string.fm_sort_size)
        FmConfig.SortField.MODIFIED -> stringResource(R.string.fm_sort_modified)
    }
    val direction = stringResource(
        if (config.ascending) R.string.fm_sort_ascending else R.string.oxide_sec_files_sort_desc
    )
    return "$field · $direction"
}

// ---------------------------------------------------------------------------
// 回收站
// ---------------------------------------------------------------------------

@Composable
private fun OxideFilesTrash(
    metrics: OxideMetrics,
    viewModel: FileManagerViewModel,
    opened: TrashViewState.Opened,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val items = opened.trashListView.items
    val selection = opened.trashListView.selection

    // 打开回收站时把真实条目读进来；关闭由宿主调用 closeTrash
    LaunchedEffect(Unit) { viewModel.loadTrashList() }

    // 视图模型给的是 id 与大小，真正的操作要 [TrashItem]，
    // 因此按 uuid 回到真实条目上；条目已经不在了就说明这一行已经过期
    val rawByUuid = remember(opened.rawItems) { opened.rawItems.associateBy { it.uuid } }

    Column(modifier = modifier.padding(horizontal = metrics.pagePaddingH)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fm_trash_title),
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.Title.fontSize,
                        lineHeight = Oxide.Type.Title.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.oxide_sec_files_trash_items,
                            items.size,
                            formatFileSize(opened.trashListView.totalSize),
                        ),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OxideButton(
                    text = stringResource(R.string.oxide_sec_files_select_all),
                    onClick = { viewModel.selectAllTrash() },
                    tone = OxideButtonTone.Ghost,
                )
                Spacer(Modifier.width(metrics.secRowGap))
                OxideButton(
                    text = stringResource(R.string.oxide_sec_files_trash_close),
                    onClick = onClose,
                    tone = OxideButtonTone.Primary,
                )
            }
        }

        Spacer(Modifier.height(metrics.cardGap))

        OxideSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            if (items.isEmpty()) {
                OxideEmptyState(title = stringResource(R.string.oxide_sec_files_trash_empty))
                return@OxideSurface
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(items = items, key = { item -> item.uuid }) { item ->
                    val raw: TrashItem? = rawByUuid[item.uuid]
                    val selected = item.uuid in selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Oxide.RadiusControl)
                            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
                            .border(
                                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                                Oxide.RadiusControl,
                            )
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.toggleTrashSelection(item.uuid) },
                            )
                            .padding(
                                horizontal = metrics.secControlPadding,
                                vertical = metrics.secRowGap,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OxideSecCheckbox(selected = selected)
                        Spacer(Modifier.width(metrics.secRowGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = Oxide.Fg,
                                fontSize = Oxide.Type.BodyStrong.fontSize,
                                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatFileSize(item.size) + " · " +
                                    formatDate(item.deletedAt),
                                color = Oxide.FgMuted,
                                fontSize = Oxide.Type.MicroLabel.fontSize,
                                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 条目已经不在真实列表里时，两枚动作按钮都不给，
                        // 而不是让它们点上去毫无反应
                        if (raw != null) {
                            Spacer(Modifier.width(metrics.secRowGap))
                            OxideIconButton(
                                onClick = { viewModel.restoreTrashItem(raw) },
                                glyph = "↩",
                                enabled = !item.corrupted,
                                modifier = Modifier.oxideIconDescription(
                                    stringResource(R.string.oxide_sec_files_restore)
                                ),
                            )
                            OxideIconButton(
                                onClick = { viewModel.trashPurge(listOf(raw)) },
                                glyph = "✕",
                                modifier = Modifier.oxideIconDescription(
                                    stringResource(R.string.oxide_sec_files_purge)
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 文本编辑器
// ---------------------------------------------------------------------------

@Composable
private fun OxideFilesEditor(
    metrics: OxideMetrics,
    viewModel: FileManagerViewModel,
    editorUi: EditorUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isLauncherInDarkTheme()
    val fallbackScheme = remember(isDark) {
        if (isDark) SchemeIDEADark() else SchemeIDEALight()
    }
    var language by remember { mutableStateOf<Language?>(null) }
    var scheme by remember { mutableStateOf<EditorColorScheme?>(null) }
    val fileName = editorUi.path?.fileName?.toString().orEmpty()

    LaunchedEffect(fileName, isDark) {
        language = TextMateRegistry.editorLanguageFor(fileName, context)
        scheme = TextMateRegistry.colorScheme(isDark, context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.pagePaddingH,
                    vertical = metrics.pagePaddingV,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideIconButton(
                onClick = onBack,
                glyph = "←",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_sec_topbar_back)
                ),
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Title.fontSize,
                    lineHeight = Oxide.Type.Title.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 只读 / 有未保存修改 / 正在保存，三个状态都以文字给出，
                // 不靠一个颜色去暗示
                Text(
                    text = when {
                        editorUi.saving -> stringResource(R.string.oxide_sec_files_editor_saving)
                        !editorUi.writable ->
                            stringResource(R.string.oxide_sec_files_editor_read_only)
                        editorUi.dirty -> stringResource(R.string.oxide_sec_files_editor_dirty)
                        else -> stringResource(R.string.generic_saved)
                    },
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(metrics.secRowGap))
            if (editorUi.writable) {
                OxideButton(
                    text = stringResource(R.string.oxide_sec_files_editor_save),
                    onClick = { viewModel.editorSave() },
                    enabled = editorUi.dirty && !editorUi.saving,
                    tone = OxideButtonTone.Primary,
                )
            }
        }

        val editorState: EditorState? = when {
            editorUi.error != null -> null
            editorUi.state is EditorState.Success -> editorUi.state
            else -> EditorState.Loading
        }

        if (editorState == null) {
            OxideSecErrorRow(
                metrics = metrics,
                modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                title = stringResource(R.string.oxide_sec_files_editor_error),
                detail = editorUi.error.orEmpty(),
                dismissText = stringResource(R.string.oxide_sec_topbar_back),
                onDismiss = onBack,
            )
        } else {
            SoraEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = editorState,
                scheme = scheme ?: fallbackScheme,
                language = language,
                isReadOnly = !editorUi.writable,
                onSaveClick = { viewModel.editorSave() },
                onTextChange = { viewModel.editorTextChanged() },
                // 空的 FAB：保存已经在顶栏这一行里，
                // 这里再挂一个 Material 圆钮会让整套语言出现第二种配色
                floatingActionButton = {},
                containerColor = Oxide.Bg,
                contentColor = Oxide.Fg,
            )
        }
    }
}