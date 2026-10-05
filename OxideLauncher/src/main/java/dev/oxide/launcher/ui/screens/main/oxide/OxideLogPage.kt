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

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle
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
 *
 * 版面与文件页同一套语言：页头只有一层大标题，副标题降一级；面板走
 * [OxideContentSurface]（不透明），因此这一页压在外壳之上时底下那一层界面
 * 不会透出来。来源行与文件页的条目行是同一枚 [OxideContentRow]。
 *
 * 两栏的排布（并排还是纵向堆叠、堆叠时各自占多少高度）来自
 * [oxideContentLayoutFor]——和文件页、关于页共用一条规则。
 */
@Composable
fun OxideLogPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    initialLogPath: String? = null,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    // 打开文件夹走既有桥接，它内部就是 host.openFiles，也就是 Oxide 自己的文件页，
    // 不是旧的 FileManagerActivity
    val bridge = rememberOxideLauncherBridge()

    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()

    // 目录本身不会在页面活着的时候变，但版本会换，因此按版本重算一次来源
    val sources = rememberLogSources(version?.getLatestLog())

    var selectedPath by remember { mutableStateOf(initialLogPath) }
    // 初始路径可能来自崩溃分享菜单；如果那一条已经不在清单里，就退回第一条真实的
    val activePath = remember(sources, selectedPath) {
        selectedPath?.takeIf { path -> sources.any { it.path == path } }
            ?: sources.firstOrNull()?.path
    }
    val activeLabel = remember(sources, activePath) {
        sources.firstOrNull { it.path == activePath }?.label.orEmpty()
    }

    // 不透明：这一页是压在外壳之上的一整块表面
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Oxide.Bg)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // 上下留白与其他内容页同一份（见 [OxidePageColumn]）：此前这一页只留了
                // 左右，标题贴着顶栏、卡片又顶到窗口下沿，于是终端那一栏连滚动条都被裁掉
                .padding(
                    start = metrics.pagePaddingH,
                    end = metrics.pagePaddingH,
                    top = metrics.pagePaddingV,
                    bottom = Oxide.PagePaddingB,
                )
        ) {
            val layout = oxideContentLayoutFor(
                availableWidth = maxWidth,
                cardMinWidth = metrics.cardMinWidth,
            )
            // 先把高度取出来：在 Row/Column 的内容 lambda 里，隐式接收者是 RowScope/ColumnScope，
            // BoxWithConstraintsScope 的 maxHeight 在那里不是一个可用的隐式接收者
            val availableHeight = maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
                OxideContentHeader(
                    metrics = metrics,
                    title = stringResource(R.string.oxide_sec_log_title),
                    subtitle = stringResource(R.string.oxide_sec_log_subtitle),
                    onDismiss = onDismiss,
                    trailing = {
                        OxideButton(
                            text = stringResource(R.string.oxide_sec_log_pack),
                            onClick = { packLauncherLogs(context) },
                            tone = OxideButtonTone.Ghost,
                        )
                    },
                )

                // 剩下的高度，而不是整页高度：页头是不带权重的，所以这一行拿到的是
                // 页头之下那块。写 fillMaxSize 会把整页高度都算给这一行，终端卡片因此
                // 溢出窗口底部，而外壳那一层的 clipToBounds 会把溢出部分连同滚动条裁掉。
                if (layout.sideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = metrics.rowGap),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                        // 来源那一栏只有几行，多出来的高度全部让给终端那一栏
                        verticalAlignment = Alignment.Top,
                    ) {
                        OxideLogSourceList(
                            metrics = metrics,
                            sources = sources,
                            folderPath = PathManager.DIR_LAUNCHER_LOGS.absolutePath,
                            activePath = activePath,
                            modifier = Modifier
                                .width(layout.listWidth),
                            onSelect = { path -> selectedPath = path },
                            onShare = { source ->
                                shareFile(context, File(source.path))
                            },
                            onOpenFolder = {
                                bridge.openFileManager(PathManager.DIR_LAUNCHER_LOGS.absolutePath)
                            },
                            availableHeight = availableHeight,
                        )

                        OxideLogViewer(
                            metrics = metrics,
                            path = activePath,
                            title = activeLabel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = metrics.rowGap),
                        verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        OxideLogSourceList(
                            metrics = metrics,
                            sources = sources,
                            folderPath = PathManager.DIR_LAUNCHER_LOGS.absolutePath,
                            activePath = activePath,
                            modifier = Modifier
                                .fillMaxWidth(),
                            onSelect = { path -> selectedPath = path },
                            onShare = { source ->
                                shareFile(context, File(source.path))
                            },
                            onOpenFolder = {
                                bridge.openFileManager(
                                    PathManager.DIR_LAUNCHER_LOGS.absolutePath
                                )
                            },
                            availableHeight = availableHeight,
                        )

                        OxideLogViewer(
                            metrics = metrics,
                            path = activePath,
                            title = activeLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 日志来源的一行：文件本身，不含任何推测；`detail` 读不到就留空 */
@Immutable
internal data class OxideLogSource(val path: String, val label: String, val detail: String)

// ---------------------------------------------------------------------------
// 版面高度（纯函数）
//
// 这一页踩过的坑是版面而不是数据：那一行两栏写的是 fillMaxSize，于是被以整页
// 高度测量，终端卡片溢出窗口底部、滚动条被外壳那一层的 clipToBounds 裁掉；
// 来源那一栏又拿 weight(1f)，把两三行摊开到整栏高度上。
//
// 修法本身只有三处修饰符，但"高度怎么分"仍然是可以脱离组合算出来的：
// 页头多高、一行来源多高、剩下的高度里来源清单最多能占多少、终端还剩多少。
// 下面这几笔账写成纯函数，单测据此钉住"终端那一栏永远拿到剩余高度"。
//
// 文字的行高不读 `Oxide.Type`：那几份是跟着设置变的状态，在没有 MMKV 的 JVM 单测里
// 读不到。这里把基准行高各存一份（与 `OxideMetrics` 里的 `NAV_TEXT_BASE` 同一做法），
// 再乘 [OxideMetrics.guiScale]——几何与排版因此仍然是同一个比例。
// ---------------------------------------------------------------------------

/**
 * 页头多高
 *
 * 页头是 [OxideContentHeader]：返回按钮与大标题同一行（取两者里高的那个），
 * 下面接一行副标题标签，再接 [OxideMetrics.sectionGap] 的间距。
 *
 * 返回按钮是 24dp、动作按钮是 28dp（`OxideButton` 的固定高度），大标题用
 * `Oxide.Type.PageTitle` 的行高；两行文字的行高都按界面缩放放大，与 [OxideMetrics]
 * 里的几何同一个系数。
 */
internal fun logPageHeaderHeight(metrics: OxideMetrics): Dp {
    val scale = metrics.guiScale
    val titleLine = oxideScaledTextStyle(LOG_TITLE_BASE, scale).lineHeight.value.dp
    val subtitleLine = oxideScaledTextStyle(LOG_LABEL_BASE, scale).lineHeight.value.dp
    return maxOf(LOG_BACK_BUTTON, LOG_ACTION_BUTTON, titleLine) + subtitleLine + metrics.sectionGap
}

/** 一行日志来源多高
 *
 * 来源行就是文件页那一枚 [OxideContentRow]：一列文字（标题 + 体积时间）、
 * 左侧 16dp 的种类徽章与右侧 24dp 的分享按钮并排，取最高的那一个，
 * 再加行上下各 6dp 的留白与它自己后面的一段行间距。
 *
 * `LOG_ROW_PADDING_V`、`LOG_ROW_BADGE`、`LOG_ROW_ACTION` 是那三处的固定 dp，
 * 这里照抄一份是因为它们在 `OxideContentSurface.kt` 里是私有的；抄一份而不是把它
 * 改成 internal，是为了让这个文件不牵动别处的可见性。文字行高则乘界面缩放，与
 * [OxideMetrics] 里的几何同一个系数。
 *
 * 取最高的那一个不是吹毛求疵：分享按钮的 24dp 在 100% 下就比两行文字的 21dp 高，
 * 少算这 3dp 会让 [logSourceListContentHeight] 比真实内容矮，最后一行的下沿会被
 * [logSourceListHeight] 给出的上限裁掉。
 */
internal fun logSourceRowHeight(metrics: OxideMetrics): Dp {
    val scale = metrics.guiScale
    val lines = oxideScaledTextStyle(LOG_ROW_TITLE_BASE, scale).lineHeight.value.dp +
        oxideScaledTextStyle(LOG_LABEL_BASE, scale).lineHeight.value.dp
    return maxOf(LOG_ROW_BADGE, lines, LOG_ROW_ACTION) + LOG_ROW_PADDING_V * 2 + metrics.secRowGap
}

/**
 * 来源清单包住内容时有多高
 *
 * 就是 [sourceCount] × [logSourceRowHeight]——那个行高已经把行与行之间那一段间距
 * 算进去了（清单里每一项后面都跟着一个 `Spacer`），因此这里不再另加。
 *
 * 这一栏之所以要按内容算高度：来源永远只有 [rememberLogSources] 给出的那几条
 * 真实文件（游戏日志、崩溃日志、联机核心日志），最多三条。给清单 `weight(1f)`
 * 就是把这三行摊开到整栏高度上——设备截图里那 500 多 dp 的空白就是这么来的，
 * 而终端那一栏同时被挤到窗口底下、滚动条被裁掉。
 */
internal fun logSourceListContentHeight(sourceCount: Int, metrics: OxideMetrics): Dp =
    logSourceRowHeight(metrics) * sourceCount.coerceAtLeast(0)

/**
 * 来源清单最多能占多高
 *
 * 上限是"可用高度减去页头与它下面的间距"，而且至少留出 [logTerminalMinHeight]
 * 给终端那一栏——终端是这一页的主体，来源清单宁可自己滚也不许把它挤没。
 *
 * 之所以要这个上限：正常情况下内容只有三行，永远碰不到上限；但上限让"来源变多"
 * 这件事有个确定的结局——清单自己滚，分隔线与"打开日志目录"那一行仍然留在卡片里。
 */
internal fun logSourceListMaxHeight(availableHeight: Dp, metrics: OxideMetrics): Dp {
    val forHeader = availableHeight - logPageHeaderHeight(metrics) - metrics.rowGap
    val headroom = forHeader - logTerminalMinHeight(metrics)
    // 再挤也留一行来源：清单被压成 0 就等于"这一页上没有任何来源可选"，
    // 那比终端那一栏少几行更糟。
    return maxOf(headroom, logSourceRowHeight(metrics)).coerceAtLeast(0.dp)
}

/**
 * 来源清单最终能占多高：包住内容，但不超过 [logSourceListMaxHeight]
 *
 * 取两者的较小值，因此**有内容时就是内容高度**——上限只在清单真的装不下时才生效，
 * 那一档它会开始滚，而不是把下面的分隔线与目录行挤出卡片。
 */
internal fun logSourceListHeight(
    sourceCount: Int,
    availableHeight: Dp,
    metrics: OxideMetrics,
): Dp = minOf(
    logSourceListContentHeight(sourceCount, metrics),
    logSourceListMaxHeight(availableHeight, metrics),
)

/**
 * 终端那一栏能拿到多高
 *
 * 就是可用高度减去页面上下留白、页头与它下面的间距，**与来源条数无关**：
 * 来源清单包住内容，因此终端那一栏拿到的是剩下的全部。
 *
 * 这一条正是本次修的 bug 的可测形式：此前那一行写的是 `fillMaxSize()`，
 * 于是终端卡片被以整页高度测量，超出窗口底边，横向滚动条随卡片一起被裁掉。
 *
 * [stackedSourcesHeight] 只在纵向堆叠时要给：那一档两栏上下排，来源那一栏占掉的
 * 高度要从终端那一栏里扣掉。并排（[oxideContentLayoutFor] 的默认那一档）两栏共用
 * 同一行高度，来源那一栏不吃纵向空间，因此传 0.dp。
 */
internal fun logTerminalHeight(
    windowHeight: Dp,
    metrics: OxideMetrics,
    stackedSourcesHeight: Dp = 0.dp,
): Dp {
    val forPage =
        windowHeight - metrics.topBarHeight - metrics.pagePaddingV - Oxide.PagePaddingB
    val belowHeader = forPage - logPageHeaderHeight(metrics) - metrics.rowGap
    return (belowHeader - stackedSourcesHeight.coerceAtLeast(0.dp)).coerceAtLeast(0.dp)
}

/**
 * 终端那一栏至少要留多高
 *
 * 卡片留白 + 标题行 + 下面几行正文。给的是"还能看出这是一份日志"的下限，
 * 而不是"够看多少行"——后者随所选文件变化，不该出现在版面算术里。
 */
internal fun logTerminalMinHeight(metrics: OxideMetrics): Dp {
    val scale = metrics.guiScale
    val title = oxideScaledTextStyle(LOG_LABEL_BASE, scale).lineHeight.value.dp
    val line = oxideScaledTextStyle(LOG_ROW_TITLE_BASE, scale).lineHeight.value.dp
    return metrics.secRowGap * 2 + title + metrics.secRowGap + line * LOG_TERMINAL_MIN_LINES
}

/** 页头大标题的基准行高，与 `Oxide.Type.PageTitle` 一致；单独存一份是为了纯函数化 */
private val LOG_TITLE_BASE = TextStyle(fontSize = 22.sp, lineHeight = 22.sp)

/** 行标题的基准行高，与 `Oxide.Type.Body` 一致 */
private val LOG_ROW_TITLE_BASE = TextStyle(fontSize = 8.sp, lineHeight = 12.sp)

/** 小节标签的基准行高，与 `Oxide.Type.MicroLabel` 一致 */
private val LOG_LABEL_BASE = TextStyle(fontSize = 6.sp, lineHeight = 9.sp)

/** 返回按钮的边长，`OxideIconButton` 的默认值 */
private val LOG_BACK_BUTTON: Dp = 24.dp

/** 动作按钮的高度，`OxideButton` 的固定高度 */
private val LOG_ACTION_BUTTON: Dp = 28.dp

/** 内容行上下留白，与 `OxideContentRow` 内部那一处一致 */
private val LOG_ROW_PADDING_V: Dp = 6.dp

/** 来源行左侧种类徽章的边长，与 `OxideContentKindBadge` 一致 */
private val LOG_ROW_BADGE: Dp = 16.dp

/** 来源行右侧分享按钮的边长，`OxideIconButton` 的默认值 */
private val LOG_ROW_ACTION: Dp = 24.dp

/** 终端那一栏至少要放得下的正文行数 */
private const val LOG_TERMINAL_MIN_LINES: Int = 6

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
internal fun OxideLogSourceList(
    metrics: OxideMetrics,
    sources: List<OxideLogSource>,
    /**
     * "打开日志目录"那一行指向的目录
     *
     * 由调用方给，而不是这一层去读 [PathManager]：它的字段是 `lateinit`，只有
     * `OxideApplication.onCreate` 填过。目录行自己的说明文字也从这份值生成，
     * 所以它只有一处来源。
     */
    folderPath: String,
    activePath: String?,
    onSelect: (String) -> Unit,
    onShare: (OxideLogSource) -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 这一栏之外还剩多少高度
     *
     * 只有页面上那两处调用知道这个数（它们从 [BoxWithConstraints] 量出来），
     * 因此由调用方传进来而不是这一层去读配置。`null` 表示"不封顶"：
     * 来源清单包住内容，卡片高度就是内容高度。
     */
    availableHeight: Dp? = null,
) {
    val files = sources.filter { it.path != folderPath }
    // 包住内容，但不超过上限：三行来源在两个窗口尺寸下都远低于上限，因此这一栏
    // 就是它自己的高度；上限只在清单真的装不下时才生效，那一档它开始滚。
    val sourcesHeight = availableHeight?.let {
        logSourceListHeight(files.size, it, metrics)
    } ?: Dp.Infinity

    OxideContentSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        // 小节标题降一级：页头的大标题仍然是这一屏唯一的标题
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_log_sources))
        Spacer(Modifier.height(metrics.secRowGap))
        if (files.isEmpty()) {
            OxideEmptyState(
                title = stringResource(R.string.oxide_sec_log_empty),
                detail = stringResource(R.string.oxide_sec_log_empty_detail),
            )
        } else {
            // 只有 [rememberLogSources] 给出的那几条真实文件（游戏日志、崩溃日志、
            // 联机核心日志），最多三条，因此这一段包住内容即可：给它 weight(1f)
            // 就是把那三行摊开到整栏高度上，而下面的目录行被顶到栏底，右边终端
            // 那一栏却被挤掉一截。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sourcesHeight),
            ) {
                items(items = files, key = { source -> source.path }) { source ->
                    val selected = source.path == activePath
                    OxideContentRow(
                        title = source.label,
                        // 体积与时间读不到就留空，而不是补一个假的
                        detail = source.detail.takeIf { it.isNotBlank() },
                        selected = selected,
                        role = Role.Tab,
                        leading = {
                            OxideContentKindBadge(
                                glyph = "≡",
                                description = source.label,
                            )
                        },
                        trailing = {
                            OxideIconButton(
                                onClick = { onShare(source) },
                                glyph = "↗",
                                modifier = Modifier.oxideIconDescription(
                                    stringResource(R.string.oxide_sec_log_share)
                                ),
                            )
                        },
                        onClick = { onSelect(source.path) },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }
        OxideSecDivider()
        Spacer(Modifier.height(metrics.secRowGap))
        // 目录那一行的说明就是它自己的路径：说明写"把这一份发给别的应用"，
        // 而这一行做的是打开目录，两件事对不上
        OxideSettingRow(
            label = stringResource(R.string.oxide_set_action_open_logs_folder),
            hint = folderPath,
            onClick = onOpenFolder,
        )
    }
}

/**
 * 终端卡片的外壳
 *
 * 卡片本身、标题标签与"已截断"提示条都在这一层；正文由 [body] 交给调用方。
 *
 * 分出来只有一个理由：[SoraEditor] 是 `AndroidView`，layoutlib 下画不出真编辑器，
 * 因此外壳必须能与正文分开测。外壳测的是"卡片留白 + 标题降一级 + 截断提示"这套
 * 排版，正文仍然是生产里那一个 `SoraEditor`，两边的接缝就是 [body]。
 */
@Composable
internal fun OxideLogTerminalCard(
    metrics: OxideMetrics,
    title: String,
    modifier: Modifier = Modifier,
    /** 正文读到的内容是不是被截断过；只决定那条提示条出不出现 */
    tailed: Boolean = false,
    body: @Composable ColumnScope.() -> Unit,
) {
    OxideContentSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        if (title.isNotBlank()) {
            OxideSectionLabel(text = title)
            Spacer(Modifier.height(metrics.secRowGap))
        }
        if (tailed) {
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
        body()
    }
}

/**
 * 右侧的日志内容
 *
 * 读取放在 IO 上并随时可取消；超过 [MAX_LOG_VIEW_SIZE] 的文件只取末尾那一段，
 * 截断位置从首个换行之后开始，因此首行不会因为半个多字节字符而变成乱码。
 *
 * [title] 是所选来源的名字：堆叠时左列被压到很窄，右边这一栏若没有标题，
 * 用户会不知道自己在看哪一份日志。
 */
@Composable
private fun OxideLogViewer(
    metrics: OxideMetrics,
    path: String?,
    title: String,
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

    OxideLogTerminalCard(
        metrics = metrics,
        modifier = modifier,
        title = title,
        tailed = loaded.value?.tailed == true,
    ) {
        when {
            path == null -> OxideEmptyState(
                title = stringResource(R.string.oxide_sec_log_empty),
                detail = stringResource(R.string.oxide_sec_log_empty_detail),
            )

            loaded.value == null -> OxideLoadingRow(stringResource(R.string.oxide_common_loading))

            else -> {
                val content = loaded.value ?: return@OxideLogTerminalCard
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
        val text = String(bytes, Charsets.UTF_8).trimStart('�')
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