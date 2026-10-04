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

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.oxide.launcher.R
import dev.oxide.launcher.filemanager.logic.compress.CompressFormat
import dev.oxide.launcher.filemanager.logic.compress.CompressOptions
import dev.oxide.launcher.filemanager.logic.entry.ArchiveType
import dev.oxide.launcher.filemanager.logic.ops.ConflictResolution
import dev.oxide.launcher.filemanager.os.FmLog
import dev.oxide.launcher.filemanager.viewmodel.DialogIntent
import dev.oxide.launcher.filemanager.viewmodel.FileManagerViewModel
import dev.oxide.launcher.filemanager.viewmodel.SearchHitView
import dev.oxide.launcher.filemanager.viewmodel.SearchUiState
import dev.oxide.launcher.ui.theme.Oxide
import java.nio.file.Path

private const val DIALOG_TAG = "OxideFilesActions"

/** 输出位置选择里"当前目录"那一档的键 */
private const val OUTPUT_CURRENT = "current"

/** 输出位置选择里"用 SAF 选目录"那一档的键 */
private const val OUTPUT_SAF = "saf"

// ---------------------------------------------------------------------------
// 决策：这一条 / 这一次选中到底能做什么
//
// 可见性全部是前置条件的纯函数，不碰磁盘也不碰协程，因此可以直接单测。
// 每一档都对着后端自己的判据写：界面既不得比后端更宽松（点了没反应），
// 也不得比后端更严（后端能做，界面却不给）。
// ---------------------------------------------------------------------------

/** 单条目上的动作 */
internal enum class OxideFilesRowAction {
    /** 改名。要求条目可写：改的是这一条自己 */
    Rename,

    /** 复制。读得到就能复制，因此连只读目录里的条目也给 */
    Copy,

    /** 剪切。移动要写源目录，因此要求条目可写 */
    Cut,

    /** 压缩。只需要读得到 */
    Compress,

    /** 解压。只有后端 [ArchiveType] 认得的压缩包才给 */
    Extract,

    /** 分享。后端 `EntryController.showShare` 对目录直接返回，所以目录不给 */
    Share,

    /** 移到回收站。要求条目可写 */
    Delete,
}

/**
 * 某一条上出现哪些动作
 *
 * @param archiveType 后端给的压缩包类型；不是压缩包时为 null，此时不给"解压"
 * @param busy 已经有别的长任务在跑：压缩与移动共用同一个任务队列，
 *   这时不再给会立刻被 `FmResult.Rejected` 拒掉的动作
 */
internal fun oxideFilesRowActions(
    isDirectory: Boolean,
    writable: Boolean,
    archiveType: ArchiveType?,
    busy: Boolean,
): List<OxideFilesRowAction> = buildList {
    // 顺序与枚举声明的顺序一致：这样“能做什么”在代码里与在界面上
    // 是同一件事，而不是两份需要对照的清单
    if (writable) add(OxideFilesRowAction.Rename)
    add(OxideFilesRowAction.Copy)
    if (writable) add(OxideFilesRowAction.Cut)
    if (!busy) add(OxideFilesRowAction.Compress)
    if (!isDirectory && archiveType != null) add(OxideFilesRowAction.Extract)
    if (!isDirectory) add(OxideFilesRowAction.Share)
    if (writable) add(OxideFilesRowAction.Delete)
}

/** 多选动作条上的动作 */
internal enum class OxideFilesSelectionAction {
    Copy,
    Cut,
    Compress,
    Delete,
}

/**
 * 多选动作条上出现哪些动作
 *
 * 一个都没选时返回空列表，因此整条动作条都不出现，而不是留一排按不动的灰按钮。
 */
internal fun oxideFilesSelectionActions(
    selectedCount: Int,
    writable: Boolean,
    busy: Boolean,
): List<OxideFilesSelectionAction> = buildList {
    if (selectedCount <= 0) return@buildList
    add(OxideFilesSelectionAction.Copy)
    if (writable) add(OxideFilesSelectionAction.Cut)
    if (!busy) add(OxideFilesSelectionAction.Compress)
    if (writable) add(OxideFilesSelectionAction.Delete)
}

/** 工具条上的"导入"是否出现：后端要往**当前目录**写，目录不可写时不给 */
internal fun oxideFilesImportVisible(writable: Boolean): Boolean = writable

/** 工具条上的"粘贴"是否出现：与选中无关，只看剪贴板里有没有东西 */
internal fun oxideFilesPasteVisible(hasClipboard: Boolean): Boolean = hasClipboard

/**
 * 压缩包最终的文件名
 *
 * 用户在输入框里已经写了 `.zip` 时不能再拼一个，因此这一个函数同时负责
 * "剥掉输入里已有的那一个后缀"与"拼上选中的那一个"。多后缀（`.tar.gz`）
 * 从最长的开始剥，否则会剩一个孤零零的 `.gz`。
 */
internal fun oxideFilesCompressFileName(name: String, format: CompressFormat): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return format.suffix
    val lower = trimmed.lowercase()
    val matched = CompressFormat.entries
        .map { it.suffix }
        .filter { lower.endsWith(it) }
        .maxByOrNull { it.length }
    val stem = if (matched != null) trimmed.dropLast(matched.length) else trimmed
    return stem + format.suffix
}

/** 这一档格式支不支持加密包：TAR 不支持，因此不给出密码输入 */
internal fun oxideFilesCompressSupportsPassword(format: CompressFormat): Boolean =
    format != CompressFormat.TAR

/** 冲突对话框上写出来的那一条的名字：优先"要写进去的那个" */
internal fun oxideFilesConflictName(source: Path?, existing: Path?): String =
    source?.fileName?.toString() ?: existing?.fileName?.toString() ?: "—"

/** 冲突处理的三种选择，键与后端 [ConflictResolution] 一一对应 */
internal val OXIDE_FILES_CONFLICT_KEYS: Map<String, ConflictResolution> = mapOf(
    "skip" to ConflictResolution.SKIP,
    "overwrite" to ConflictResolution.OVERWRITE,
    "keep_both" to ConflictResolution.KEEP_BOTH,
)

// ---------------------------------------------------------------------------
// 对话框宿主
// ---------------------------------------------------------------------------

/**
 * 文件页自己的对话框宿主
 *
 * 后端把"现在该问什么"表达成 [DialogIntent]，这一段把它画成 Oxide 的对话框，
 * 并把回答交回 [FileManagerViewModel] 上对应的控制器。粘贴、压缩、解压的冲突处理
 * 一处都没有重新实现——那些决策都在 `PasteController` / `CompressController` /
 * `ExtractController` / `TrashController` 里，这里只负责问与答。
 *
 * SAF 的四类选择（导入文件、导入目录、压缩输出目录、解压输出目录）都先拿
 * `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`，与旧文件管理器 `FmMainPage` 一致：
 * 没有长期权限，任务在后台跑一半时系统会把读权限收回去。
 */
@Composable
internal fun OxideFilesDialogs(
    metrics: OxideMetrics,
    viewModel: FileManagerViewModel,
    intent: DialogIntent?,
    searchUi: SearchUiState,
) {
    // SAF 的回调在系统选择器关闭之后才跑，那时 dialogIntent 可能已经变了，
    // 因此留一份总是最新的意图，回调按它分派
    val latestIntent by rememberUpdatedState(intent)

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val current = latestIntent
        if (uri != null) {
            runCatching {
                viewModel.appContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                FmLog.warn(DIALOG_TAG, "takePersistableUriPermission failed for $uri", it)
            }
            when (current) {
                DialogIntent.CompressOutputPick -> viewModel.onCompressOutputPicked(uri)
                DialogIntent.ExtractOutputPick -> viewModel.onExtractOutputPicked(uri)
                DialogIntent.ImportDir -> viewModel.onImportDir(uri)
                else -> Unit
            }
        } else {
            when (current) {
                DialogIntent.CompressOutputPick -> viewModel.onCompressOutputPickedCancelled()
                DialogIntent.ExtractOutputPick -> viewModel.onExtractOutputPickedCancelled()
                DialogIntent.ImportDir -> viewModel.onImportCancelled()
                else -> Unit
            }
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = ArrayList<Uri>()
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index)?.uri?.let(uris::add)
            }
        }
        if (uris.isEmpty()) data?.data?.let(uris::add)
        if (uris.isEmpty()) {
            viewModel.onImportCancelled()
        } else {
            val resolver = viewModel.appContext().contentResolver
            uris.forEach { uri ->
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.onFailure {
                    FmLog.warn(DIALOG_TAG, "takePersistableUriPermission failed for $uri", it)
                }
            }
            viewModel.onImportFiles(uris)
        }
    }

    // SAF 只在进入对应意图的那一次唤起；意图没变就绝不重复弹系统选择器
    LaunchedEffect(intent) {
        when (intent) {
            DialogIntent.CompressOutputPick,
            DialogIntent.ExtractOutputPick,
            DialogIntent.ImportDir,
            -> dirLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
            )

            DialogIntent.ImportFiles -> filesLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            else -> Unit
        }
    }

    when (intent) {
        null -> Unit

        // 只用来唤起 SAF，界面上没有东西可画
        DialogIntent.ImportFiles,
        DialogIntent.ImportDir,
        DialogIntent.CompressOutputPick,
        DialogIntent.ExtractOutputPick,
        -> Unit

        DialogIntent.Search -> OxideFilesSearchSetupDialog(
            metrics = metrics,
            initialKeyword = searchUi.lastKeyword,
            onDismiss = viewModel::dismissDialog,
            onSearch = viewModel::submitSearch,
        )

        DialogIntent.SearchTask -> OxideTaskDialog(
            title = stringResource(R.string.fm_progress_title),
            message = searchUi.currentDir?.toString(),
            // 搜索没有可知的总量：给一条空进度与一句说明，而不是编一个百分比
            progress = null,
            onCancel = viewModel::cancelCurrentTask,
            metrics = metrics,
        )

        DialogIntent.SearchResult -> OxideFilesSearchResultDialog(
            metrics = metrics,
            searchUi = searchUi,
            onOpen = { hit -> viewModel.navigateToSearchHit(hit) },
            onSearchAgain = viewModel::backToSearchSetup,
            onClear = viewModel::clearSearch,
            onDismiss = viewModel::dismissDialog,
        )

        is DialogIntent.CompressSetup -> OxideFilesCompressSetupDialog(
            metrics = metrics,
            defaultName = intent.defaultName,
            onDismiss = viewModel::dismissDialog,
            onConfirm = { name, options ->
                viewModel.onCompressSetupConfirmed(
                    name = name,
                    sources = intent.sources,
                    options = options,
                )
            },
        )

        DialogIntent.CompressOutputChoice -> OxideFilesOutputChoiceDialog(
            title = stringResource(R.string.fm_compress_output_choice_title),
            metrics = metrics,
            onPickCurrent = viewModel::onCompressOutputChoiceCurrent,
            onPickSaf = viewModel::onCompressOutputChoiceSaf,
            onDismiss = viewModel::dismissDialog,
        )

        is DialogIntent.CompressConflict -> OxideFilesConflictDialog(
            metrics = metrics,
            name = intent.fileName,
            onResolve = viewModel::resolveCompressConflict,
            onDismiss = viewModel::dismissDialog,
        )

        is DialogIntent.ExtractSetup -> OxideFilesExtractSetupDialog(
            metrics = metrics,
            archiveName = intent.archiveName,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::onExtractSetupConfirmed,
        )

        DialogIntent.ExtractOutputChoice -> OxideFilesOutputChoiceDialog(
            title = stringResource(R.string.fm_compress_output_choice_title),
            metrics = metrics,
            onPickCurrent = viewModel::onExtractOutputChoiceCurrent,
            onPickSaf = viewModel::onExtractOutputChoiceSaf,
            onDismiss = viewModel::dismissDialog,
        )

        is DialogIntent.ExtractConflict -> OxideFilesConflictDialog(
            metrics = metrics,
            name = intent.name,
            onResolve = viewModel::resolveExtractConflict,
            onDismiss = viewModel::dismissDialog,
        )

        is DialogIntent.ExtractPassword -> OxideFilesExtractPasswordDialog(
            metrics = metrics,
            errorText = intent.errorText,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::onExtractPasswordConfirmed,
        )

        is DialogIntent.PasteConflict -> {
            val conflict = intent.request.conflicts.getOrNull(intent.currentIndex)
            OxideFilesConflictDialog(
                metrics = metrics,
                name = oxideFilesConflictName(conflict?.source, conflict?.existing),
                onResolve = viewModel::resolvePasteConflict,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is DialogIntent.TrashRestoreConflict -> {
            val item = intent.conflictItems.getOrNull(intent.pendingIndex)?.first
            OxideFilesConflictDialog(
                metrics = metrics,
                name = item?.name.orEmpty(),
                onResolve = viewModel::resolveTrashRestoreConflict,
                onDismiss = viewModel::dismissDialog,
            )
        }
    }
}

/**
 * 输出位置选择
 *
 * 两档都在列表里点一下就生效，因此用动作菜单而不是单选 + 确认。
 */
@Composable
private fun OxideFilesOutputChoiceDialog(
    title: String,
    metrics: OxideMetrics,
    onPickCurrent: () -> Unit,
    onPickSaf: () -> Unit,
    onDismiss: () -> Unit,
) {
    OxideListDialog(
        title = title,
        options = listOf(
            OxideDialogOption(
                key = OUTPUT_CURRENT,
                label = stringResource(R.string.fm_compress_output_current_dir),
            ),
            OxideDialogOption(
                key = OUTPUT_SAF,
                label = stringResource(R.string.fm_compress_output_saf),
            ),
        ),
        selectable = false,
        metrics = metrics,
        onOptionSelected = { key ->
            if (key == OUTPUT_SAF) onPickSaf() else onPickCurrent()
        },
        onDismiss = onDismiss,
    )
}

/**
 * 冲突处理
 *
 * 三种处理方式与后端 [ConflictResolution] 一一对应。取消 = 不决策，
 * 因此它只收起对话框，不替用户选一个。
 */
@Composable
private fun OxideFilesConflictDialog(
    metrics: OxideMetrics,
    name: String,
    onResolve: (ConflictResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    OxideListDialog(
        title = stringResource(R.string.fm_conflict_title),
        options = listOf(
            OxideDialogOption(
                key = "skip",
                label = stringResource(R.string.generic_ignore),
                detail = name,
            ),
            OxideDialogOption(
                key = "overwrite",
                label = stringResource(R.string.fm_conflict_overwrite),
                detail = name,
            ),
            OxideDialogOption(
                key = "keep_both",
                label = stringResource(R.string.fm_conflict_keep_both),
                detail = name,
            ),
        ),
        selectable = false,
        metrics = metrics,
        onOptionSelected = { key -> OXIDE_FILES_CONFLICT_KEYS[key]?.let(onResolve) },
        onDismiss = onDismiss,
    )
}

/** 搜索设置：关键词 + 是否区分大小写 */
@Composable
private fun OxideFilesSearchSetupDialog(
    metrics: OxideMetrics,
    initialKeyword: String,
    onDismiss: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var caseSensitive by remember { mutableStateOf(false) }

    OxideDialogShell(
        title = stringResource(R.string.generic_search),
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSecInput(
                    metrics = metrics,
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = stringResource(R.string.fm_search_hint),
                    label = stringResource(R.string.fm_search_hint),
                )
                OxideSecPickerRow(
                    label = stringResource(R.string.fm_search_case_sensitive),
                    selected = caseSensitive,
                    onClick = { caseSensitive = !caseSensitive },
                )
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onDismiss,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_search),
                onClick = { onSearch(keyword, caseSensitive) },
                enabled = keyword.isNotBlank(),
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 搜索结果
 *
 * 命中列表的高度与溢出判定都走 [oxideDialogListHeight] / [oxideDialogListOverflows]，
 * 与 `OxideListDialog` 是同一套算法；这里自己拼这一张是因为它需要两个页脚按钮
 * （换关键词 / 清空），而 `OxideListDialog` 的页脚是留给"确认"的。
 */
@Composable
private fun OxideFilesSearchResultDialog(
    metrics: OxideMetrics,
    searchUi: SearchUiState,
    onOpen: (SearchHitView) -> Unit,
    onSearchAgain: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hits = searchUi.hits
    val running = searchUi.running
    val rowHeight = metrics.navItemHeight * 1.35f

    OxideDialogShell(
        title = stringResource(R.string.fm_search_result_count, hits.size),
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = { contentMaxHeight ->
            val listHeight = oxideDialogListHeight(
                itemCount = hits.size,
                maxHeight = contentMaxHeight,
                rowHeight = rowHeight,
            )
            val overflows = oxideDialogListOverflows(
                itemCount = hits.size,
                maxHeight = contentMaxHeight,
                rowHeight = rowHeight,
            )
            if (hits.isEmpty()) {
                Text(
                    text = if (running) {
                        stringResource(R.string.oxide_cap_files_searching)
                    } else {
                        stringResource(R.string.fm_search_empty)
                    },
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                )
            } else {
                LazyColumn(modifier = Modifier.height(listHeight)) {
                    items(hits, key = { it.path.toString() }) { hit ->
                        OxideSettingRow(
                            label = hit.name,
                            hint = hit.path.parent?.toString(),
                            onClick = { onOpen(hit) },
                        )
                        if (overflows) Spacer(Modifier.height(metrics.secRowGap * 0.5f))
                    }
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_search),
                onClick = onSearchAgain,
                enabled = !running,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_clear),
                onClick = onClear,
                enabled = !running && hits.isNotEmpty(),
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_close),
                onClick = onDismiss,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/** 压缩设置：包名 + 格式 + 可选密码 */
@Composable
private fun OxideFilesCompressSetupDialog(
    metrics: OxideMetrics,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, CompressOptions) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var format by remember { mutableStateOf(CompressFormat.ZIP) }
    var password by remember { mutableStateOf("") }

    OxideDialogShell(
        title = stringResource(R.string.fm_archive),
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSecInput(
                    metrics = metrics,
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.fm_new_name),
                    label = stringResource(R.string.fm_new_name),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                ) {
                    CompressFormat.entries.forEach { option ->
                        OxideSecChip(
                            label = option.extension.uppercase(),
                            selected = format == option,
                            metrics = metrics,
                            onClick = {
                                format = option
                                // TAR 不支持加密：换格式时把上一档的密码一起清掉，
                                // 否则会留下一个谁都解不开的包
                                if (!oxideFilesCompressSupportsPassword(option)) password = ""
                            },
                        )
                    }
                }
                if (oxideFilesCompressSupportsPassword(format)) {
                    OxideSecInput(
                        metrics = metrics,
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.fm_compress_password),
                        label = stringResource(R.string.fm_compress_password),
                        password = true,
                    )
                }
                // 最终文件名以文字给出：换格式时后缀跟着变，只给一个输入框读不出来
                Text(
                    text = oxideFilesCompressFileName(name, format),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onDismiss,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_confirm),
                onClick = {
                    onConfirm(
                        oxideFilesCompressFileName(name, format),
                        CompressOptions(
                            format = format,
                            password = password.takeIf { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = name.isNotBlank(),
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/** 解压设置：是否解到独立文件夹 */
@Composable
private fun OxideFilesExtractSetupDialog(
    metrics: OxideMetrics,
    archiveName: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var independent by remember { mutableStateOf(true) }

    OxideDialogShell(
        title = stringResource(R.string.fm_extract),
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                Text(
                    text = stringResource(R.string.fm_extract_body, archiveName),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                )
                OxideSecPickerRow(
                    label = stringResource(R.string.fm_extract_independent_folder),
                    selected = independent,
                    onClick = { independent = !independent },
                )
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onDismiss,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.fm_extract),
                onClick = { onConfirm(independent) },
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 解压密码
 *
 * 控制器只把"要密码 / 密码错了"这句话递进来，密码本身由这一段自己持有：
 * 一次密码错就重新输入，退出对话框就整体作废，不留半句残缺的值。
 */
@Composable
private fun OxideFilesExtractPasswordDialog(
    metrics: OxideMetrics,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    OxideDialogShell(
        title = stringResource(R.string.fm_extract_password_title),
        onDismissRequest = onDismiss,
        metrics = metrics,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                OxideSecInput(
                    metrics = metrics,
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.fm_compress_password),
                    label = stringResource(R.string.fm_compress_password),
                    password = true,
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = "⚠ $errorText",
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    )
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onDismiss,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_confirm),
                onClick = { onConfirm(password) },
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

// ---------------------------------------------------------------------------
// 动作条
// ---------------------------------------------------------------------------

/**
 * 单条目的上下文动作条
 *
 * 只画 [oxideFilesRowActions] 给出的那些动作，因此不可能出现"点了没反应"的按钮。
 * 宽度不够时这一行自己横向滚，而不是把按钮挤出卡片。
 */
@Composable
internal fun OxideFilesRowActionBar(
    metrics: OxideMetrics,
    entryName: String,
    actions: List<OxideFilesRowAction>,
    onAction: (OxideFilesRowAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    OxideContentSurface(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = metrics.navItemHeight * 1.6f),
            ) {
                Text(
                    text = entryName,
                    color = Oxide.FgStrong,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.oxide_cap_files_actions_hint),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(metrics.secRowGap))
            OxideIconButton(
                onClick = onClose,
                glyph = "✕",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_cap_files_actions_close)
                ),
            )
        }
        Spacer(Modifier.height(metrics.secRowGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        ) {
            actions.forEach { action ->
                OxideButton(
                    text = stringResource(action.labelRes()),
                    onClick = { onAction(action) },
                    tone = if (action == OxideFilesRowAction.Delete) {
                        OxideButtonTone.Primary
                    } else {
                        OxideButtonTone.Secondary
                    },
                )
            }
        }
    }
}

/**
 * 多选动作条
 *
 * 选中态由列表里的复选框承担，这里只放"对选中项做的事"，
 * 因此宽度不够时这一行横向滚，而不是把"移到回收站"折掉。
 */
@Composable
internal fun OxideFilesSelectionBar(
    metrics: OxideMetrics,
    selectedCount: Int,
    actions: List<OxideFilesSelectionAction>,
    onAction: (OxideFilesSelectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    OxideContentSurface(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        Text(
            text = stringResource(R.string.fm_count_selected, selectedCount),
            color = Oxide.FgStrong,
            fontSize = Oxide.Type.BodyStrong.fontSize,
            lineHeight = Oxide.Type.BodyStrong.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.secRowGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        ) {
            actions.forEach { action ->
                OxideButton(
                    text = stringResource(action.labelRes()),
                    onClick = { onAction(action) },
                    tone = if (action == OxideFilesSelectionAction.Delete) {
                        OxideButtonTone.Primary
                    } else {
                        OxideButtonTone.Secondary
                    },
                )
            }
        }
    }
}

/** 动作对应的文案。按钮是文字按钮，因此每一枚都必须有自己的可翻译标签 */
internal fun OxideFilesRowAction.labelRes(): Int = when (this) {
    OxideFilesRowAction.Rename -> R.string.generic_rename
    OxideFilesRowAction.Copy -> R.string.generic_copy
    OxideFilesRowAction.Cut -> R.string.fm_cut
    OxideFilesRowAction.Compress -> R.string.fm_archive
    OxideFilesRowAction.Extract -> R.string.fm_extract
    OxideFilesRowAction.Share -> R.string.generic_share
    OxideFilesRowAction.Delete -> R.string.fm_move_to_trash
}

internal fun OxideFilesSelectionAction.labelRes(): Int = when (this) {
    OxideFilesSelectionAction.Copy -> R.string.generic_copy
    OxideFilesSelectionAction.Cut -> R.string.fm_cut
    OxideFilesSelectionAction.Compress -> R.string.fm_archive
    OxideFilesSelectionAction.Delete -> R.string.fm_move_to_trash
}
