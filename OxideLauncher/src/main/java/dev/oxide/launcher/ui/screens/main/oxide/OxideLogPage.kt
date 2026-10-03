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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.code_editor.EditorState
import dev.oxide.launcher.ui.code_editor.SoraEditor
import dev.oxide.launcher.ui.code_editor.TextMateRegistry
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEADark
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEALight
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.logging.Logger
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val LOG_PACK_TASK_ID = "OXIDE_LOG_PACK"
private const val LOG_TAG = "OxideLogPage"

/** 日志查看器加载的日志体积上限，超过则只读取末尾部分，与旧日志查看器一致 */
private const val MAX_LOG_VIEW_SIZE: Long = 8L * 1024 * 1024

/**
 * 日志页
 *
 * 取代 `LogViewScreen`（以及它背后的 `NormalNavKey.LogView` 路由）与
 * 崩溃日志分享菜单：左边是真实的日志来源清单，右边是所选日志的内容。
 *
 * 保留旧日志查看器的**尾部截断**行为：超过 8 MiB 的日志只读末尾那一段，
 * 并且从第一个完整换行开始，避免截断处留下半个字符把首行读成乱码。
 *
 * 来源全部来自真实文件：当前实例最新的游戏日志、启动器崩溃日志、联机核心日志，
 * 以及整个启动器日志目录。哪一个还不存在就只显示哪一条，其余的不会出现。
 * "打包并分享所有启动器日志"走的是 [Logger.pack]，与设置页里的同名操作完全一致。
 */
@Composable
fun OxideLogPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    initialLogPath: String? = null,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val hostActions = LocalOxideHostActions.current

    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()

    // 目录本身不会在页面活着的时候变，但版本会换，因此按版本重算一次来源
    val sources = rememberLogSources(version?.getLatestLog())

    var selectedPath by remember { mutableStateOf(initialLogPath) }
    // 初始路径可能来自崩溃分享菜单；如果那一条已经不在清单里，就退回第一条真实的
    val activePath = remember(sources, selectedPath) {
        selectedPath?.takeIf { path -> sources.any { it.path == path } }
            ?: sources.firstOrNull()?.path
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= metrics.cardMinWidth * 1.3f

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
                Spacer(Modifier.width(6.dp))
                OxidePageTitle(
                    text = stringResource(R.string.oxide_sec_log_title),
                    modifier = Modifier.weight(1f),
                    trailing = {
                        OxideButton(
                            text = stringResource(R.string.oxide_sec_log_pack),
                            onClick = { packLauncherLogs(context) },
                            tone = OxideButtonTone.Ghost,
                        )
                    },
                )
            }
            OxideSectionLabel(
                modifier = Modifier.padding(horizontal = metrics.pagePaddingH),
                text = stringResource(R.string.oxide_sec_log_subtitle),
            )

            Spacer(Modifier.height(metrics.sectionGap))

            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideLogSourceList(
                        metrics = metrics,
                        sources = sources,
                        activePath = activePath,
                        modifier = Modifier
                            .width(metrics.cardMinWidth)
                            .fillMaxHeight(),
                        onSelect = { path -> selectedPath = path },
                        onShare = { source ->
                            shareFile(context, source.path)
                        },
                        onOpenFolder = {
                            hostActions.openFileManager(PathManager.DIR_LAUNCHER_LOGS.absolutePath)
                        },
                    )

                    OxideLogViewer(
                        metrics = metrics,
                        path = activePath,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.pagePaddingH),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideLogSourceList(
                        metrics = metrics,
                        sources = sources,
                        activePath = activePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.42f),
                        onSelect = { path -> selectedPath = path },
                        onShare = { source -> shareFile(context, source.path) },
                        onOpenFolder = {
                            hostActions.openFileManager(
                                PathManager.DIR_LAUNCHER_LOGS.absolutePath
                            )
                        },
                    )

                    OxideLogViewer(
                        metrics = metrics,
                        path = activePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/** 日志来源的一行：文件本身，不含任何推测；`detail` 读不到就留空 */
private data class OxideLogSource(val path: String, val label: String, val detail: String)

/**
 * 收集真实的日志来源
 *
 * 只有 `isFile` 的那几条才会出现：还没写出来的日志不会占一行"（暂无）"，
 * 因为"没有日志"和"日志为空"是两回事，界面上必须能分辨。
 * 文件存在性、体积与时间都在 IO 上判定，组合期只读这里的结果。
 */
@Composable
private fun rememberLogSources(gameLog: File?): List<OxideLogSource> {
    val gameLabel = stringResource(R.string.oxide_sec_log_game)
    val crashLabel = stringResource(R.string.oxide_sec_log_crash)
    val terracottaLabel = stringResource(R.string.oxide_sec_log_terracotta)
    val folderLabel = stringResource(R.string.oxide_sec_log_folder)

    val candidates = remember(gameLabel, crashLabel, terracottaLabel) {
        listOf(
            gameLog?.let { it.absolutePath to gameLabel },
            PathManager.FILE_CRASH_REPORT.absolutePath to crashLabel,
            PathManager.FILE_TERRACOTTA_LOG.absolutePath to terracottaLabel,
        ).filterNotNull()
    }

    val probed = produceState(initialValue = emptyList<OxideLogSource>(), candidates) {
        value = withContext(Dispatchers.IO) {
            candidates.mapNotNull { (path, label) ->
                val file = File(path)
                if (!file.isFile) {
                    null
                } else {
                    OxideLogSource(
                        path = path,
                        label = label,
                        detail = formatFileSize(file.length()) +
                            " · " + formatDate(file.lastModified()),
                    )
                }
            }
        }
    }

    return remember(probed.value, folderLabel) {
        buildList {
            addAll(probed.value)
            add(
                OxideLogSource(
                    path = PathManager.DIR_LAUNCHER_LOGS.absolutePath,
                    label = folderLabel,
                    detail = "",
                )
            )
        }
    }
}

/** 左侧的来源清单 */
@Composable
private fun OxideLogSourceList(
    metrics: OxideMetrics,
    sources: List<OxideLogSource>,
    activePath: String?,
    onSelect: (String) -> Unit,
    onShare: (OxideLogSource) -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val files = sources.filter { it.path != PathManager.DIR_LAUNCHER_LOGS.absolutePath }

    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_log_sources))
        Spacer(Modifier.height(metrics.secRowGap))
        if (files.isEmpty()) {
            OxideEmptyState(
                title = stringResource(R.string.oxide_sec_log_empty),
                detail = stringResource(R.string.oxide_sec_log_empty_detail),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(items = files, key = { source -> source.path }) { source ->
                    val selected = source.path == activePath
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
                                role = Role.Tab,
                                onClick = { onSelect(source.path) },
                            )
                            .padding(
                                horizontal = metrics.secControlPadding,
                                vertical = metrics.secRowGap,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.label,
                                color = Oxide.Fg,
                                fontSize = Oxide.Type.BodyStrong.fontSize,
                                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detail = source.detail
                            if (detail.isNotEmpty()) {
                                Text(
                                    text = detail,
                                    color = Oxide.FgMuted,
                                    fontSize = Oxide.Type.MicroLabel.fontSize,
                                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(metrics.secRowGap))
                        OxideIconButton(
                            onClick = { onShare(source) },
                            glyph = "↗",
                            modifier = Modifier.oxideIconDescription(
                                stringResource(R.string.oxide_sec_log_share)
                            ),
                        )
                    }
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
        OxideSecDivider()
        Spacer(Modifier.height(metrics.secRowGap))
        OxideSettingRow(
            label = stringResource(R.string.oxide_sec_log_folder),
            hint = stringResource(R.string.oxide_sec_log_share_hint),
            onClick = onOpenFolder,
        )
    }
}

/**
 * 右侧的日志内容
 *
 * 读取放在 IO 上并随时可取消；超过 [MAX_LOG_VIEW_SIZE] 的文件只取末尾那一段，
 * 截断位置从首个换行之后开始，因此首行不会因为半个多字节字符而变成乱码。
 */
@Composable
private fun OxideLogViewer(
    metrics: OxideMetrics,
    path: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isLauncherInDarkTheme()
    val fallbackScheme = remember(isDark) {
        if (isDark) SchemeIDEADark() else SchemeIDEALight()
    }
    var language by remember { mutableStateOf<Language?>(null) }
    var scheme by remember { mutableStateOf<EditorColorScheme?>(null) }
    LaunchedEffect(isDark) {
        language = TextMateRegistry.languageFor("text.log", context)
        scheme = TextMateRegistry.colorScheme(isDark, context)
    }

    val loaded = produceState<OxideLogContent?>(initialValue = null, path) {
        if (path == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val file = File(path)
            runCatching { readLogTail(file) }
                .getOrElse { error ->
                    Logger.warning(LOG_TAG, "Unable to read the log file!", error)
                    OxideLogContent(error.message.orEmpty(), tailed = false)
                }
        }
    }

    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        when {
            path == null -> OxideEmptyState(
                title = stringResource(R.string.oxide_sec_log_empty),
                detail = stringResource(R.string.oxide_sec_log_empty_detail),
            )

            loaded.value == null -> OxideLoadingRow(stringResource(R.string.oxide_common_loading))

            else -> {
                val content = loaded.value ?: return@OxideSurface
                if (content.tailed) {
                    Text(
                        text = stringResource(
                            R.string.oxide_sec_log_tailed,
                            formatFileSize(MAX_LOG_VIEW_SIZE),
                        ),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
                SoraEditor(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = EditorState.Success(Content(content.text)),
                    scheme = scheme ?: fallbackScheme,
                    language = language,
                    isReadOnly = true,
                    onSaveClick = {},
                    // 只读，因此没有保存动作，内置的保存圆钮也不需要
                    floatingActionButton = {},
                    containerColor = Oxide.Bg,
                    contentColor = Oxide.Fg,
                )
            }
        }
    }
}

/** 读出来的日志内容，以及它是不是被截断过 */
private data class OxideLogContent(val text: String, val tailed: Boolean)

/**
 * 读取日志，超出上限时只取末尾
 *
 * 与旧日志查看器 `readLog` 的行为逐字一致：先按字节定位到 `size - 上限`，
 * 读满这一段后去掉开头可能残缺的替换字符，再从首个换行之后开始；
 * 窗口里一个换行都没有（超长单行）时保留整段，不做二次截断。
 */
private fun readLogTail(file: File): OxideLogContent {
    val size = file.length()
    if (size <= MAX_LOG_VIEW_SIZE) {
        return OxideLogContent(file.readText(), tailed = false)
    }
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(size - MAX_LOG_VIEW_SIZE)
        val bytes = ByteArray(MAX_LOG_VIEW_SIZE.toInt())
        raf.readFully(bytes)
        val text = String(bytes, Charsets.UTF_8).trimStart('\uFFFD')
        val firstNewline = text.indexOf('\n')
        val body = if (firstNewline >= 0 && firstNewline < text.length - 1) {
            text.substring(firstNewline + 1)
        } else {
            text
        }
        return OxideLogContent(body, tailed = true)
    }
}

/**
 * 打包并分享整个启动器日志目录
 *
 * 与设置页里的同名操作完全一致：打成一个 zip，再交给系统的分享面板。
 * 打包在 IO 上跑，因此在后台被切走时不会丢。
 */
private fun packLauncherLogs(context: android.content.Context) {
    TaskSystem.submitTask(
        Task.runTask(
            id = LOG_PACK_TASK_ID,
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(androidText(R.string.oxide_sec_log_packing))
                val archive = File(PathManager.DIR_CACHE, "logs.zip")
                Logger.pack(archive)
                task.updateProgress(1f)
                task.updateMessage(null)
                // 分享面板要起 Activity，必须回到主线程
                withContext(Dispatchers.Main) {
                    shareFile(context = context, file = archive)
                }
            },
            onError = { e -> Logger.error(LOG_TAG, "Failed to package the log files.", e) },
        )
    )
}