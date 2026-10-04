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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.oxide.launcher.filemanager.viewmodel.FileManagerUiState
import dev.oxide.launcher.filemanager.viewmodel.FileManagerViewModel
import dev.oxide.launcher.filemanager.viewmodel.FmInitState
import dev.oxide.launcher.filemanager.viewmodel.RawList
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
 * 版面自上而下三段，层级不再互相打架：
 * 1. [OxideContentHeader]：返回 + **一层**大标题 + 计数副标题。
 *    页面内部不再出现第二份同权重标题——此前回收站用 `Title` 字号在面板里
 *    又写了一遍标题，与页头的大标题抢层级。
 * 2. 路径条：[OxideContentPathBar] 按级列出祖先目录，每一级都点得回去，
 *    当前目录那一级高亮但不可点。此前这里是把整条绝对路径塞进一个两行省略的
 *    文本，既读不出自己在哪一层，也点不到任何上层。
 * 3. 动作条 + 列表：动作条上的每一件东西**只由前置条件决定**——目录不可写就
 *    不出现"新建文件夹"，没有选中项就不出现"移到回收站"，而不是把它们画成
 *    一排点不动的灰按钮。
 *
 * 页面底色是 [Oxide.Bg]（不透明），面板走 [OxideContentSurface]（同样不透明），
 * 因此这一页压在外壳之上时，底下那一层界面不会从半透明的卡片里透出来。
 *
 * 后端完全是既有的那个 [FileManagerViewModel]，一处都没有换：
 * 浏览、选择、回收站与编辑器仍由它的控制器负责 IO 与协程，
 * 这一页只负责把这些能力画出来，并把进度、失败与确认都留在界面上。
 *
 * 多选状态只有 [FileManagerUiState] 一处真相：此前页面自己另存了一份
 * `multiSelect`，换目录时只清掉本地那份，于是"看不见的选中"继续生效。
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

    // 不透明：这一页是压在外壳之上的一整块表面，底下那一层不允许透出来
    Box(modifier = modifier.fillMaxSize().background(Oxide.Bg)) {
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

            else -> BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.pagePaddingH)
            ) {
                // 列规则与日志页、关于页共用一处，段数（面包屑显示几级）
                // 也从同一个函数出来
                val layout = oxideContentLayoutFor(
                    availableWidth = maxWidth,
                    cardMinWidth = metrics.cardMinWidth,
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    OxideContentHeader(
                        metrics = metrics,
                        title = stringResource(R.string.oxide_sec_files_title),
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
                            modifier = Modifier.padding(vertical = metrics.rowGap),
                            title = stringResource(R.string.generic_error),
                            detail = message,
                            dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                            onDismiss = { errorText = null },
                        )
                    }

                    createName?.let { name ->
                        OxideContentSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = metrics.rowGap),
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
                    }

                    if (deleteArmed && state.selection.isNotEmpty()) {
                        OxideSecConfirmBar(
                            metrics = metrics,
                            modifier = Modifier.padding(vertical = metrics.rowGap),
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
                        }

                    if (trash != null) {
                        OxideFilesTrash(
                            metrics = metrics,
                            viewModel = viewModel,
                            opened = trash,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = metrics.rowGap),
                            onClose = {
                                viewModel.closeTrash()
                                deleteArmed = false
                            },
                        )
                    } else {
                        OxideFilesBrowser(
                            metrics = metrics,
                            viewModel = viewModel,
                            crumbLimit = layout.crumbs,
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
    OxideContentSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.rowGap),
        contentPadding = PaddingValues(
            horizontal = metrics.secControlPadding,
            vertical = metrics.secRowGap,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.height(metrics.secRowGap))
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
}

// ---------------------------------------------------------------------------
// 浏览
// ---------------------------------------------------------------------------

@Composable
private fun OxideFilesBrowser(
    metrics: OxideMetrics,
    viewModel: FileManagerViewModel,
    crumbLimit: Int,
    modifier: Modifier = Modifier,
    onNewFolder: () -> Unit,
    onRequestDelete: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenEditor: (Path) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val raw = state.rawList
    // 多选状态只有 [FileManagerUiState] 这一处真相
    val multiSelect = state.multiSelect

    Column(modifier = modifier) {
        OxideFilesToolbar(
            metrics = metrics,
            state = state,
            raw = raw,
            multiSelect = multiSelect,
            crumbLimit = crumbLimit,
            onBack = { viewModel.back() },
            onForward = { viewModel.forward() },
            onUp = { viewModel.goParent() },
            onNavigate = { viewModel.navigateTo(it) },
            onSort = { viewModel.setSortConfig(cycleSort(state.sortConfig)) },
            onRefresh = { viewModel.refresh() },
            onToggleHidden = { viewModel.toggleHidden() },
            onOpenTrash = onOpenTrash,
            onNewFolder = onNewFolder,
            onStartSelect = { viewModel.selectAll() },
            onClearSelect = { viewModel.clearSelection() },
            onRequestDelete = onRequestDelete,
        )

        Spacer(Modifier.height(metrics.rowGap))

        OxideContentSurface(
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
                    // 一个目录看起来是空的，有两种很不一样的原因：真的没有，
                    // 或者只有隐藏项。哪一种必须说清楚，否则用户会以为浏览坏了。
                    // 切换隐藏项的那一枚按钮在上面工具条里常驻，这里不再重复一枚。
                    detail = if (!state.refreshing && !state.showHidden &&
                        raw?.entries?.isNotEmpty() == true
                    ) {
                        stringResource(R.string.oxide_files_only_hidden)
                    } else {
                        null
                    },
                )
                return@OxideContentSurface
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
                            // 只走状态流，不走 FileManagerViewModel 的 stageSingleDelete：
                            // 后者把 key 加进 store 自己的集合却不推状态，于是确认条上
                            // 的条数会与真正被删掉的条数对不上，取消之后那一条还留在
                            // 集合里。点叉的语义就是"把这一条也纳入这次删除"。
                            if (!selected) viewModel.toggleSelection(entry)
                            onRequestDelete()
                        },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
    }
}

/**
 * 路径条与动作条
 *
 * 可见性全部是前置条件的纯函数：目录不可写就没有"新建文件夹"，
 * 没有选中项就没有"移到回收站"，还没进多选就只有"全选"这一件与选择有关的事。
 * 没有一处是"画出来但点不动"的灰行——导航那三枚也一样：不能回就不出现，
 * 而不是留一枚永远按不动的箭头。
 */
@Composable
private fun OxideFilesToolbar(
    metrics: OxideMetrics,
    state: FileManagerUiState,
    raw: RawList?,
    multiSelect: Boolean,
    crumbLimit: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onNavigate: (Path) -> Unit,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpenTrash: () -> Unit,
    onNewFolder: () -> Unit,
    onStartSelect: () -> Unit,
    onClearSelect: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    // 层级链由 rawList 给：根 → … → 当前目录。段数来自共用的列规则，
    // 窄屏只留最后几级，前面用省略号交代"上面还有"
    val chain = raw?.let { it.ancestors + it.currentDir }.orEmpty()
    val crumbs = remember(chain, crumbLimit) {
        oxidePathCrumbsFor(
            labels = chain.map { dir -> dir.fileName?.toString() ?: dir.toString() },
            maxCrumbs = crumbLimit,
        )
    }

    OxideContentSurface(
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
            if (state.canNavigateBack) {
                OxideFilesNavButton(
                    glyph = "←",
                    description = stringResource(R.string.oxide_sec_files_back),
                    onClick = onBack,
                )
            }
            if (state.canNavigateForward) {
                OxideFilesNavButton(
                    glyph = "→",
                    description = stringResource(R.string.oxide_sec_files_forward),
                    onClick = onForward,
                )
            }
            if (state.canBack) {
                OxideFilesNavButton(
                    glyph = "↑",
                    description = stringResource(R.string.oxide_sec_files_up),
                    onClick = onUp,
                )
            }
            Spacer(Modifier.width(metrics.secRowGap))
            OxideFilesSortButton(
                metrics = metrics,
                label = sortLabel(state.sortConfig),
                description = stringResource(R.string.oxide_sec_files_sort),
                onClick = onSort,
                modifier = Modifier.weight(1f),
            )
            // 隐藏项是一个状态，不是一个动作：当前取值以文字写在下面那一行里，
            // 因此这里只有一枚切换按钮，且它永远可点（切换只是过滤，不写盘）
            OxideFilesNavButton(
                glyph = if (state.showHidden) "◉" else "○",
                description = stringResource(
                    if (state.showHidden) {
                        R.string.oxide_files_hide_hidden
                    } else {
                        R.string.oxide_files_show_hidden
                    }
                ),
                onClick = onToggleHidden,
            )
            OxideFilesNavButton(
                glyph = "↻",
                description = stringResource(R.string.oxide_sec_files_refresh),
                onClick = onRefresh,
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        // 路径条独占一行：目录名再长也不会把上面的导航与排序挤没
        if (crumbs.isNotEmpty()) {
            OxideContentPathBar(
                metrics = metrics,
                crumbs = crumbs,
                onNavigate = { index -> chain.getOrNull(index)?.let(onNavigate) },
            )
        }

        Spacer(Modifier.height(metrics.secRowGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideFilesNavButton(
                glyph = "⌫",
                description = stringResource(R.string.fm_trash_title),
                onClick = onOpenTrash,
            )
            // 目录不可写时"新建文件夹"整个不出现：这一栏的动作都由前置条件决定
            if (raw?.writable == true) {
                OxideButton(
                    text = stringResource(R.string.oxide_sec_files_new_folder),
                    onClick = onNewFolder,
                    modifier = Modifier.weight(1f),
                )
            }
            OxideSecPickerRow(
                label = if (multiSelect) {
                    stringResource(R.string.oxide_files_finish_selecting)
                } else {
                    stringResource(R.string.oxide_sec_files_select_all)
                },
                selected = multiSelect,
                onClick = { if (multiSelect) onClearSelect() else onStartSelect() },
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

        // 只读目录与"正在显示隐藏项"都以文字给出，不靠一个符号或颜色暗示
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
        if (state.showHidden) {
            Text(
                text = stringResource(R.string.oxide_files_hidden_on),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(
                if (multiSelect) {
                    R.string.oxide_files_select_on_hint
                } else {
                    R.string.oxide_files_select_hint
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

/** 列表里的一行：文件夹与文件共用同一枚行，因此两者读起来一致 */
@Composable
private fun OxideFilesEntryRow(
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

    OxideContentRow(
        title = entry.name,
        // 文件的说明写全"文件 · 大小 · 修改时间"，文件夹的写清它是文件夹：
        // 只有一条描述时，文件夹那一档不能靠"少一个体积"来暗示它是什么
        detail = if (sizeLabel.isEmpty()) {
            kindLabel
        } else {
            "$kindLabel · $sizeLabel · " +
                stringResource(R.string.oxide_sec_files_modified, modifiedLabel)
        },
        selected = selected,
        role = if (multiSelect) Role.Checkbox else Role.Button,
        leading = {
            if (multiSelect) {
                OxideContentCheckBox(selected = selected)
            } else {
                OxideContentKindBadge(
                    glyph = if (entry.isDirectory) "▸" else "≡",
                    description = kindLabel,
                )
            }
        },
        // 条目不可写时删除这一枚整个不出现，而不是留一枚点不动的叉；
        // 单选与多选下都在：否则不进多选就删不掉单独一条
        trailing = if (entry.writable) {
            {
                OxideIconButton(
                    onClick = onStageDelete,
                    glyph = "✕",
                    modifier = Modifier.oxideIconDescription(
                        stringResource(R.string.generic_delete)
                    ),
                )
            }
        } else {
            null
        },
        onClick = onOpen,
    )
}

/** 顶栏上的导航小按钮 */
@Composable
private fun OxideFilesNavButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    OxideIconButton(
        onClick = onClick,
        glyph = glyph,
        modifier = Modifier.oxideIconDescription(description),
    )
}

/**
 * 排序按钮：符号加一行文字
 *
 * 当前排序以文字写在按钮上，符号只是提示——因此不再在下面另起一行重复一遍。
 */
@Composable
private fun OxideFilesSortButton(
    metrics: OxideMetrics,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = metrics.secControlPadding)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⇅",
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
        Spacer(Modifier.width(metrics.secRowGap))
        Text(
            text = label,
            color = Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
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

    // 视图模型给的是 id 与大小，真正的操作要 [TrashItem]，
    // 因此按 uuid 回到真实条目上；条目已经不在了就说明这一行已经过期
    val rawByUuid = remember(opened.rawItems) { opened.rawItems.associateBy { it.uuid } }

    Column(modifier = modifier) {
        OxideContentSurface(
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
                    // 回收站也用小节标题那一档：页头的大标题仍然是这一屏
                    // 唯一的标题，这一行不再用 Title 字号与它抢层级
                    OxideSectionLabel(text = stringResource(R.string.fm_trash_title))
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
                // 一个都没选时"全选"没有意义，因此不出现
                if (items.isNotEmpty()) {
                    OxideButton(
                        text = stringResource(
                            if (selection.isEmpty()) {
                                R.string.oxide_sec_files_select_all
                            } else {
                                R.string.oxide_files_clear_select
                            }
                        ),
                        onClick = {
                            if (selection.isEmpty()) {
                                viewModel.selectAllTrash()
                            } else {
                                viewModel.clearTrashSelection()
                            }
                        },
                        tone = OxideButtonTone.Ghost,
                    )
                    Spacer(Modifier.width(metrics.secRowGap))
                }
                OxideButton(
                    text = stringResource(R.string.oxide_sec_files_trash_close),
                    onClick = onClose,
                    tone = OxideButtonTone.Primary,
                )
            }
        }

        Spacer(Modifier.height(metrics.rowGap))

        OxideContentSurface(
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
                return@OxideContentSurface
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(items = items, key = { item -> item.uuid }) { item ->
                    val raw: TrashItem? = rawByUuid[item.uuid]
                    val selected = item.uuid in selection
                    val kindLabel = stringResource(
                        if (item.isFolder) R.string.oxide_sec_files_folder else R.string.oxide_sec_files_file
                    )
                    OxideContentRow(
                        title = item.name,
                        detail = formatFileSize(item.size) + " · " +
                            formatDate(item.deletedAt) + " · " + kindLabel,
                        selected = selected,
                        role = Role.Checkbox,
                        leading = { OxideContentCheckBox(selected = selected) },
                        // 条目已经不在真实列表里时，两枚动作按钮都不给，
                        // 而不是让它们点上去毫无反应；恢复不了的那一条（损坏）
                        // 只给"彻底删除"，不留一枚永远按不动的箭头
                        trailing = if (raw != null) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!item.corrupted) {
                                        OxideIconButton(
                                            onClick = { viewModel.restoreTrashItem(raw) },
                                            glyph = "↩",
                                            modifier = Modifier.oxideIconDescription(
                                                stringResource(R.string.oxide_sec_files_restore)
                                            ),
                                        )
                                        Spacer(Modifier.width(metrics.secRowGap))
                                    }
                                    OxideIconButton(
                                        onClick = { viewModel.trashPurge(listOf(raw)) },
                                        glyph = "✕",
                                        modifier = Modifier.oxideIconDescription(
                                            stringResource(R.string.oxide_sec_files_purge)
                                        ),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        onClick = { viewModel.toggleTrashSelection(item.uuid) },
                    )
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
            Spacer(Modifier.width(metrics.secRowGap * 2))
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
            // 不可写就没有保存这一说，因此那一枚按钮整个不出现
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