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

package dev.oxide.launcher.ui.screens.game.elements

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.context.copyLocalFile
import dev.oxide.launcher.context.getFileName
import dev.oxide.launcher.ui.screens.main.oxide.OxideConfirmDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideTaskDialog
import dev.oxide.launcher.ui.screens.main.oxide.showOxideMessageDialog
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.string.getMessageOrToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.Date

sealed interface OpenFolderOperation {
    data object None : OpenFolderOperation

    /** 开始浏览目录 */
    data class OpenFolder(val initialPath: File) : OpenFolderOperation
}

/**
 * 游戏内打开的浏览目录面板
 *
 * 它不是弹窗，而是画在游戏层上的一层浮层（`VMActivity` 里
 * `OpenFolderLayer(modifier = Modifier.fillMaxSize())`），因此这里量到的
 * `maxWidth` / `maxHeight` **就是游戏窗口**——分屏或自由窗口下它可能只有屏幕的
 * 一小块。面板的宽高全部由它推出，不写死任何与窗口有关的 dp。
 *
 * 行为一个没少：
 *
 * - 点遮罩关掉这一层（[requestClose]），关的动作立刻生效、不等动画；
 * - 目录内容仍然在 `Dispatchers.IO` 上读，仍然用同一套排序键
 *   （目录在前、再按小写名、再按原名、最后按绝对路径）；
 * - 删除仍然是"确认 → 在 [lifecycleScope] 的 IO 上 `deleteQuietly` →
 *   重新读一次目录"，删除期间也仍然有一块进度面板挡在上面；
 * - 导入仍然用 `GetMultipleContents` 挑文件，仍然逐个 `copyLocalFile`，
 *   某一个失败时报一次错并继续下一个，最后才回调完成。
 *
 * 换掉的是外壳与条目：
 *
 * - 原来是 `BackgroundCard` + `CardTitleLayout` + `Surface(onClick)`：
 *   一层毛玻璃、一层半透明标题条、一个 Material 卡片。现在是一块不透明的
 *   [GameOverlayPanel] 加一条发丝线。
 * - 原来的条目借 `BaseFileItem`，字号来自 `MaterialTheme.typography`。
 *   名字、修改时间与大小这三个**真实读数**照旧，只是排版换了；
 *   名字改成单行截断而不是无限跑马灯——跑马灯每帧重新测量一次文本，
 *   而这块面板可能整晚开着。条目上那个 `Animatable` 缩放进场也一并去掉：
 *   几百个条目就是几百个动画对象，而列表本来就是从上往下读的。
 * - 删除按钮带上一句"删除哪个文件"的朗读文本。原来只有一句"删除"，
 *   读屏用户不知道删的是哪一个。
 * - 长目录在**被夹住的**滚动区里，因此面板不会因为目录里有几万个条目
 *   而被顶出屏幕。
 */
@Composable
fun OpenFolderLayer(
    operation: OpenFolderOperation,
    requestClose: () -> Unit,
    lifecycleScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var internalPath by remember { mutableStateOf<File?>(null) }
    var refreshFiles by remember { mutableStateOf(false) }
    // 一次性保存不可变列表：之前在 IO 线程上 clear()/addAll() 修改快照列表，
    // 每次导航都会触发跨线程的全局快照应用与通知。
    var files by remember { mutableStateOf<List<File>>(emptyList()) }

    var deleteFile by remember { mutableStateOf<File?>(null) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(operation) {
        internalPath = when (operation) {
            is OpenFolderOperation.None -> null
            is OpenFolderOperation.OpenFolder -> operation.initialPath
        }
    }

    LaunchedEffect(internalPath, refreshFiles) {
        val loaded = withContext(Dispatchers.IO) {
            val entries = internalPath?.listFiles()?.toList() ?: emptyList()
            // 排序键预先算好，避免排序过程中反复 stat
            entries.sortedWith(
                compareBy(
                    { it.isFile },
                    { it.name.lowercase() },
                    { it.name },
                    { it.absolutePath }
                )
            )
        }
        files = loaded
    }

    val browsing = operation is OpenFolderOperation.OpenFolder

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        if (browsing) {
            //这里不给动画，尽快恢复触控
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null, //禁用水波纹点击效果
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = requestClose
                    )
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.55f)
        ) {
            AnimatedVisibility(
                visible = browsing,
                enter = fadeIn() + slideInHorizontally(
                    animationSpec = tween(Oxide.Motion.PopoverMs)
                ) {
                    if (isRtl) -40 else 40
                },
                exit = fadeOut() + slideOutHorizontally(
                    animationSpec = tween(Oxide.Motion.PopoverMs)
                ) {
                    if (isRtl) -40 else 40
                }
            ) {
                val bounds = rememberGameOverlayBounds()
                val path = internalPath

                GameOverlayPanel(
                    bounds = bounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bounds.edgeMargin),
                ) {
                    GameOverlayHeader(
                        title = stringResource(R.string.files_browse_folder),
                        bounds = bounds,
                        closeDescription = stringResource(R.string.generic_close),
                        onClose = requestClose,
                    )
                    GameOverlayHairline()

                    //当前路径：玩家判断"删的是不是对的文件"的唯一依据
                    Text(
                        text = path?.absolutePath ?: stringResource(R.string.generic_loading),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Mono.fontSize,
                        lineHeight = Oxide.Type.Mono.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = bounds.padding,
                            end = bounds.padding,
                            top = bounds.rowGap,
                            bottom = bounds.rowGap,
                        ),
                    )

                    GameOverlayHairline()

                    //文件浏览区域。
                    //这里刻意**不**再套一层 verticalScroll：纵向滚动的容器会把高度
                    //上限交给子节点，LazyColumn 拿到无穷大的 maxHeight 就直接崩。
                    //正确的做法就是这一种——高度先由 [gameOverlayListHeight] 夹住，
                    //LazyColumn 自己滚。因此一个有几十万个条目的目录也不会把面板撑高。
                    if (files.isEmpty()) {
                        GameOverlayNote(
                            text = stringResource(R.string.oxide_ingame_folder_empty),
                            modifier = Modifier.padding(
                                start = bounds.padding,
                                end = bounds.padding,
                                top = bounds.rowGap,
                            ),
                        )
                    } else {
                        val rowHeight = remember(bounds) { bounds.buttonHeight + 6.dp }
                        val listHeight = remember(files.size, bounds) {
                            gameOverlayListHeight(files.size, bounds.contentMaxHeight, rowHeight)
                        }
                        val overflows = remember(files.size, bounds) {
                            gameOverlayListOverflows(files.size, bounds.contentMaxHeight, rowHeight)
                        }
                        if (overflows) {
                            val scrollState = rememberLazyListState()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = listHeight)
                                    .padding(
                                        start = bounds.padding,
                                        end = bounds.padding,
                                        top = bounds.rowGap,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
                                state = scrollState,
                            ) {
                                items(files, key = { it.absolutePath }) { file ->
                                    FileItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        bounds = bounds,
                                        file = file,
                                        onDelete = { deleteFile = file },
                                    )
                                }
                            }
                        } else {
                            // 装得下：目录里就那么几个文件，不必挂一个懒列表
                            Column(
                                modifier = Modifier.padding(
                                    start = bounds.padding,
                                    end = bounds.padding,
                                    top = bounds.rowGap,
                                ),
                                verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
                            ) {
                                files.forEach { file ->
                                    FileItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        bounds = bounds,
                                        file = file,
                                        onDelete = { deleteFile = file },
                                    )
                                }
                            }
                        }
                    }

                    if (path != null) {
                        GameOverlayHairline()
                        FolderFooter(
                            bounds = bounds,
                            targetDir = path,
                            onImported = { refreshFiles = !refreshFiles },
                            onRequestClose = requestClose,
                        )
                    }
                }
            }
        }
    }

    //删除文件对话框
    deleteFile?.let { target ->
        OxideConfirmDialog(
            title = stringResource(R.string.generic_delete),
            message = stringResource(R.string.files_delete_file, target.name),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = {
                deleteJob?.cancel()
                deleteJob = lifecycleScope.launch(Dispatchers.IO) {
                    FileUtils.deleteQuietly(target)
                    //组合期状态只能在主线程写：原来这两个赋值跑在 IO 上，
                    //改目录的瞬间正好撞上一次重组就会抛
                    withContext(Dispatchers.Main) {
                        refreshFiles = !refreshFiles
                        deleteJob = null
                    }
                }
                deleteFile = null
            },
            onDismiss = {
                deleteFile = null
            },
        )
    }

    //开始执行删除任务
    if (deleteJob != null) {
        OxideTaskDialog(
            title = stringResource(R.string.generic_in_progress),
            progress = null,
        )
    }
}

/** 底栏：关闭 + 导入。导入仍然匹配任意 MIME 类型，逐个文件拷贝 */
@Composable
private fun FolderFooter(
    bounds: GameOverlayBounds,
    targetDir: File,
    onImported: () -> Unit,
    onRequestClose: () -> Unit,
) {
    val context = LocalContext.current
    var importOperation by remember { mutableStateOf<ImportFileOperation>(ImportFileOperation.None) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.takeIf { it.isNotEmpty() }?.let { uris0 ->
            importOperation = ImportFileOperation.Import(uris0, targetDir)
        }
    }

    ImportFileOperation(
        context = context,
        operation = importOperation,
        onImported = onImported,
        onFinished = { importOperation = ImportFileOperation.None }
    )

    GameOverlayFooter(bounds = bounds) {
        //关闭按钮
        GameOverlayButton(
            text = stringResource(R.string.generic_close),
            onClick = onRequestClose,
            minHeight = bounds.buttonHeight,
        )
        //导入按钮
        GameOverlayButton(
            text = stringResource(R.string.generic_import),
            onClick = {
                launcher.launch("*/*")
            },
            minHeight = bounds.buttonHeight,
            tone = GameOverlayButtonTone.Primary,
        )
    }
}

/**
 * 目录里的一个条目
 *
 * 三个读数——名字、修改时间、大小——一个不少，都是真实的。
 * 名字单行截断而不是无限跑马灯：跑马灯每帧重新测量一次文本，
 * 而这块面板可能整晚开着。
 */
@Composable
private fun FileItem(
    modifier: Modifier = Modifier,
    bounds: GameOverlayBounds,
    file: File,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = bounds.buttonHeight)
            .clip(Oxide.RadiusBlock)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameOverlayIcon(
            painter = painterResource(
                if (file.isDirectory) {
                    R.drawable.ic_folder_outlined
                } else {
                    R.drawable.ic_description_outlined
                }
            ),
            //文件类型由旁边的名字说明，不必再念一遍"文件夹"
            contentDescription = null,
            size = 14.dp,
            tint = Oxide.FgGhost,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = formatDate(
                        date = Date(file.lastModified()),
                        pattern = stringResource(R.string.date_format)
                    ),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
                if (file.isFile) {
                    Text(
                        text = formatFileSize(FileUtils.sizeOf(file)),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        GameOverlayIconButton(
            painter = painterResource(R.drawable.ic_delete_outlined),
            // 光说"删除"会让人不知道删的是哪一个文件，因此带上名字
            description = stringResource(R.string.oxide_ingame_folder_delete, file.name),
            onClick = onDelete,
            size = bounds.buttonHeight,
        )
    }
}

private sealed interface ImportFileOperation {
    data object None : ImportFileOperation

    /** 正式开始导入文件 */
    data class Import(val uris: List<Uri>, val targetDir: File) : ImportFileOperation
}

/**
 * 简单的导入文件任务
 *
 * 导入本身一行没改：仍然逐个 `copyLocalFile`，某一个失败就报一次错、
 * 然后继续下一个，最后才回调 [onFinished]。
 * 换掉的只有两处呈现：进度提示与错误说明都改用启动器那套 Oxide 面板
 * （[OxideTaskDialog] 与 [showOxideMessageDialog]），不再是
 * `ProgressDialog` 与 `MaterialAlertDialogBuilder`。
 */
@Composable
private fun ImportFileOperation(
    context: Context,
    operation: ImportFileOperation,
    onImported: () -> Unit = {},
    onFinished: () -> Unit = {}
) {
    when (operation) {
        is ImportFileOperation.Import -> {
            // 文案在组合期取一次；协程里不能调 stringResource
            val errorTitle = stringResource(R.string.generic_error)
            val errorMessage = stringResource(R.string.error_import_file)

            val uris = operation.uris
            val targetDir = operation.targetDir

            LaunchedEffect(uris, targetDir) {
                launch(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            val fileName = context.getFileName(uri)
                                ?: throw IOException("Failed to get file name")
                            val outputFile = File(targetDir, fileName)
                            context.copyLocalFile(uri, outputFile)
                            onImported()
                        } catch (e: Exception) {
                            val eString = e.getMessageOrToString()
                            val messageString = errorMessage + "\n" + eString

                            withContext(Dispatchers.Main) {
                                showOxideMessageDialog(
                                    context = context,
                                    title = errorTitle,
                                    message = messageString,
                                )
                            }
                        }
                    }
                    onFinished()
                }
            }

            OxideTaskDialog(
                title = stringResource(R.string.files_importing),
                progress = null,
            )
        }

        is ImportFileOperation.None -> {}
    }
}