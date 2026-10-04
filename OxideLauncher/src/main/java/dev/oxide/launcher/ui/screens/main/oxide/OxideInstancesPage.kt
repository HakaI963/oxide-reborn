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

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.ui.activities.MainActivity
import dev.oxide.launcher.ui.components.LocalMainActivity
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.content.elements.VersionsOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.getTimeAgo
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.ScreenBackStackViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt

private const val TAG = "OxideInstancesPage"

/** 参考稿的网格是两行；列数由实测内容宽度决定，行数固定 */
private const val GRID_ROWS = 2

/** 参考稿 `.miniStat` 的 `gap`，也是统计块内部的间距 */
private val STAT_GAP = 6.dp

/** 卡片底部徽章之间的间距，与参考稿的 `gap:4px` 对应 */
private val BADGE_GAP = 4.dp

/** 参考稿 `.instanceCard` 的内边距 */
private val CARD_PADDING_H = 12.dp
private val CARD_PADDING_V = 11.dp

/**
 * 卡片真正的内容高度，用来给行高兜底
 *
 * 顶部一行（22dp 的实例名 + 3dp + 12dp 的副标题）、
 * 统计块（9dp 的小标签 + 3dp + 12dp 的值 + 上下各 6dp 内边距）、
 * 底部一行（28dp 的播放按钮）以及上下内边距。行高一旦小于这个数，
 * 卡片底部就会被裁掉，所以它同时也是 [instanceRowHeight] 的下限来源。
 */
private const val CARD_TOP_ROW_DP = 37f
private const val CARD_STATS_DP = 36f
private const val CARD_BOTTOM_ROW_DP = 28f

/**
 * 错误条的内边距，与参考稿 `.errRow` 对应
 *
 * 这一行是页面上唯一的"操作失败"提示，因此内边距与卡片一致，
 * 视觉上它和旁边的卡片属于同一套网格，而不是漂在外面的一条字。
 */
private val ERROR_ROW_PADDING_H = 10.dp
private val ERROR_ROW_PADDING_V = 7.dp

/** 错误条正文与那颗按钮之间的间距 */
private val ERROR_ROW_GAP = 8.dp

/** 齿轮浮层的宽度，与参考稿 `.popover` 的比例一致但只放一列 */
private val ACTION_MENU_WIDTH = 190.dp

/**
 * 实例页面
 *
 * 数据全部来自真实的已安装版本（[VersionsManager]），没有任何演示数据：
 * 卡片上能看到的每一个值都对应磁盘或设置里的一个真实量，读不到就不显示那一行。
 *
 * 三种交互彼此分明，互不串味：
 * - 点卡片本体 = 把它设为当前实例（走 [VersionsManager.saveVersion]，落盘到 current.json）
 * - 点齿轮 = 展开这一个实例的动作浮层，或直接进配置抽屉
 * - 点播放 = 走 [EventViewModel.Event.Launch.Game] 这条既有的启动链路
 *
 * 装新版本这一条也是真的：头部与空状态里的按钮把 `OxideDownloadCategory.Game`
 * 交给宿主，由它推进既有的 `NestedNavKey.DownloadGame`（选版本 → 选附加内容 →
 * 真实安装），页面自己不碰导航栈。
 */
@Composable
fun OxideInstancesPage(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()
    val hostActions = LocalOxideHostActions.current

    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val isRefreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()

    // 用户刚点过的那一个；null 表示"跟随后端记录的当前版本"
    var pickedKey by remember { mutableStateOf<String?>(null) }
    // 打开配置抽屉的版本路径，null 表示抽屉关闭
    var drawerKey by remember { mutableStateOf<String?>(null) }
    // 展开了动作浮层的版本路径，同一时刻最多一个
    var menuKey by remember { mutableStateOf<String?>(null) }
    // 重命名/复制/删除的真实对话框，与既有版本管理页共用同一套
    var operation by remember { mutableStateOf<VersionsOperation>(VersionsOperation.None) }
    // 动作失败时在页面里就地报出来，而不是依赖另一个 Activity 上的错误弹窗
    var operationError by remember { mutableStateOf<ErrorViewModel.ThrowableMessage?>(null) }

    val submitError: (ErrorViewModel.ThrowableMessage) -> Unit = { message ->
        operationError = message
    }

    // 高亮落点：用户点的 → 后端记录的当前版本 → 第一个真实存在的版本
    val activeKey: String? = remember(pickedKey, versions, currentVersion) {
        resolveActiveInstance(
            pickedKey = pickedKey,
            installedNames = versions.map { version -> version.getVersionName() },
            backendCurrentName = currentVersion?.getVersionName(),
        )
    }

    // 一切要碰磁盘或 MMKV 的读取都放到 IO 上探测，组合期只读这里的结果
    val probes = remember { mutableStateMapOf<String, InstanceProbe>() }
    val probePaths = remember(versions) { versions.map { it.getVersionPath().absolutePath } }
    LaunchedEffect(probePaths) {
        if (versions.isEmpty()) {
            probes.clear()
            return@LaunchedEffect
        }
        val measured = withContext(Dispatchers.IO) {
            versions.associate { version ->
                val mods = VersionFolders.MOD.getDir(version.getGameDir())
                    .listFiles()
                    ?.count { file -> file.isFile }
                    ?: 0
                version.getVersionPath().absolutePath to InstanceProbe(
                    valid = version.isValid(),
                    isolated = version.isIsolation(),
                    ramMb = version.getRamAllocation(),
                    modsCount = mods,
                    lastRunAt = version.getLatestLog().lastModified(),
                )
            }
        }
        probes.keys.toList().filter { key -> key !in measured }.forEach { key ->
            probes.remove(key)
        }
        probes.putAll(measured)
    }

    // 唯一的选中断言：真的写进后端，重启之后仍然是同一个
    val select: (Version) -> Unit = { version ->
        if (VersionsManager.saveVersion(version)) {
            pickedKey = version.getVersionName()
        } else {
            // 无效版本不能被设为当前版本，与版本管理页保持一致
            eventViewModel.sendToast(androidText(R.string.oxide_ins_toast_invalid))
        }
    }
    val launch: (Version) -> Unit = { version ->
        eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(version))
    }
    // 装一个新 Minecraft 版本：交给宿主进既有的下载嵌套栈
    val installVersion: () -> Unit = {
        menuKey = null
        hostActions.openDownloadCategory(OxideDownloadCategory.Game)
    }
    val runAction: (InstanceAction, Version) -> Unit = { action, version ->
        menuKey = null
        when (action) {
            InstanceAction.Configure -> drawerKey = version.getVersionPath().absolutePath
            InstanceAction.Rename -> operation = VersionsOperation.Rename(version)
            InstanceAction.Copy -> operation = VersionsOperation.Copy(version)
            InstanceAction.ExportModPack ->
                // 导出走 Oxide 自己的三步向导，不再推进旧的 VersionExport 嵌套栈
                hostActions.openVersionExport(version)

            InstanceAction.SetPinned -> setPinned(version, true, submitError)
            InstanceAction.ClearPinned -> setPinned(version, false, submitError)
            InstanceAction.OpenFolder -> eventViewModel.sendEvent(
                EventViewModel.Event.OpenFileManager(
                    rootPath = version.getVersionPath().absolutePath
                )
            )

            InstanceAction.Delete -> operation = VersionsOperation.Delete(version)
        }
    }

    // 页头两个动作各自的实测宽度。标题会不会被截断由它们决定，因此必须量而不是猜。
// 两者分开记：折走之后不再绘制"刷新"，但它的宽度仍然留着，
// 于是判据不会因为"现在画了什么"而变来变去。
    var refreshButtonWidth by remember { mutableStateOf(0f) }
    var installButtonWidth by remember { mutableStateOf(0f) }

    val gridState = rememberLazyGridState()
    // 滚动时收起浮层：它的锚点是卡片，滚走之后浮层会停在原地不动
    val firstVisible by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) {
        if (menuKey != null) menuKey = null
    }

    // 重命名或删除之后这个实例已经不在真实列表里，抽屉与浮层都跟着收起，
    // 免得继续往一个已经改名的目录上写配置
    val livePaths = remember(versions) {
        versions.map { version -> version.getVersionPath().absolutePath }.toSet()
    }
    LaunchedEffect(livePaths, drawerKey) {
        if (drawerKey != null && drawerKey !in livePaths) drawerKey = null
    }
    LaunchedEffect(livePaths, menuKey) {
        if (menuKey != null && menuKey !in livePaths) menuKey = null
    }
    val drawerVersion: Version? = drawerKey?.let { key ->
        versions.firstOrNull { version -> version.getVersionPath().absolutePath == key }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 列数按实测内容宽度算，窗口被切分或折叠屏展开时会自己变
        val contentWidth = maxWidth - metrics.pagePaddingH * 2
        val columns = metrics.gridColumns(contentWidth)

        // 标题至少要保住这么宽，否则宁可把"刷新"折到副标题那一行：
        // 页面名字被截断是最不该发生的一种截断
        val minTitleWidth = metrics.navItemHeight * 5f

        OxidePageColumn(metrics = metrics) {
            // 窄屏上"刷新"被折进副标题：标题行只留主操作，两个动作不挤在同一行。
            // 判据只看实测宽度与 metrics，因此不会因为"此刻画了什么"而来回抖
            val refreshInline = instancesHeaderKeepsRefreshInline(
                contentWidthDp = contentWidth.value,
                installWidthDp = installButtonWidth,
                refreshWidthDp = refreshButtonWidth,
                gapDp = metrics.secRowGap.value,
                minTitleWidthDp = minTitleWidth.value,
            )

            OxidePageTitle(
                text = stringResource(R.string.oxide_ins_page_title),
                trailing = {
                    if (refreshInline) {
                        Box(
                            modifier = Modifier.onSizeChanged { size ->
                                refreshButtonWidth = with(density) {
                                    size.width.toDp().value
                                }
                            },
                        ) {
                            OxideButton(
                                text = stringResource(R.string.oxide_ins_refresh),
                                onClick = {
                                    VersionsManager.refresh("OxideInstancesPage.refresh")
                                },
                                enabled = !isRefreshing,
                                tone = OxideButtonTone.Ghost,
                            )
                        }
                        Spacer(Modifier.width(metrics.secRowGap))
                    }
                    Box(
                        modifier = Modifier.onSizeChanged { size ->
                            installButtonWidth = with(density) {
                                size.width.toDp().value
                            }
                        },
                    ) {
                        OxideButton(
                            text = stringResource(R.string.oxide_ins_install_version),
                            onClick = installVersion,
                            tone = OxideButtonTone.Primary,
                        )
                    }
                },
            )
            OxideSectionLabel(
                text = stringResource(R.string.oxide_ins_page_subtitle_count, versions.size),
                trailing = {
                    if (!refreshInline) {
                        OxideButton(
                            text = stringResource(R.string.oxide_ins_refresh),
                            onClick = {
                                VersionsManager.refresh("OxideInstancesPage.refresh")
                            },
                            enabled = !isRefreshing,
                            tone = OxideButtonTone.Ghost,
                        )
                    }
                },
            )

            Spacer(Modifier.height(metrics.sectionGap))

            operationError?.let { failure ->
                OxideInstanceErrorRow(
                    message = failure,
                    onDismiss = { operationError = null },
                )
                Spacer(Modifier.height(metrics.cardGap))
            }

            when {
                // 空状态不是一整屏：第一屏还没有实例时它就是全部内容，
                // 因此按内容高度摆在一块面板里，而不是让标题和说明各自拉成两行撑满高度。
                // 面板宽度也有上限：说明句在宽屏上不会拉成一整行难读的长句
                versions.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isRefreshing) {
                        // 没有任何实例可显示时，刷新中才占一行；否则这一行会把空状态顶走
                        OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        OxideSurface(
                            modifier = Modifier.widthIn(max = metrics.cardMinWidth * 1.5f),
                            contentPadding = PaddingValues(all = metrics.cardGap),
                        ) {
                            OxideEmptyState(
                                title = stringResource(R.string.oxide_ins_empty_title),
                                detail = stringResource(R.string.oxide_ins_install_version_detail),
                                action = {
                                    // 两个动作排成一行：宽度不够时由这一行自己滚动，
                                    // 而不是把第二个按钮挤出面板
                                    Row(
                                        modifier = Modifier.horizontalScroll(
                                            rememberScrollState()
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            metrics.secRowGap
                                        ),
                                    ) {
                                        OxideButton(
                                            text = stringResource(
                                                R.string.oxide_ins_install_version
                                            ),
                                            onClick = installVersion,
                                            tone = OxideButtonTone.Primary,
                                        )
                                        OxideButton(
                                            text = stringResource(
                                                R.string.oxide_ins_browse_modpacks
                                            ),
                                            onClick = { onNavigate(OxidePage.Discover) },
                                            tone = OxideButtonTone.Secondary,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 参考稿是两行网格：行高先按实测剩余高度平分，
                        // 太矮时退到卡片真实内容算出来的下限，宁可滚动也不裁切。
                        // 行数由真实存在的卡片决定：只有一张时不必占满两行的高度，
                        // 否则卡片下面会留下一整条空白
                        val visibleRows = instanceGridRows(
                            itemCount = versions.size,
                            columns = columns,
                            maxRows = GRID_ROWS,
                        )
                        val minRow = CARD_TOP_ROW_DP + CARD_STATS_DP + CARD_BOTTOM_ROW_DP +
                            CARD_PADDING_V.value * 2f + metrics.cardGap.value
                        val rowHeight = instanceRowHeight(
                            availableHeightDp = maxHeight.value,
                            cardGapDp = metrics.cardGap.value,
                            rows = visibleRows,
                            minRowHeightDp = minRow,
                        ).dp

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                            verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = versions,
                                key = { version -> version.getVersionPath().absolutePath },
                            ) { version ->
                                val path = version.getVersionPath().absolutePath
                                OxideInstanceCard(
                                    version = version,
                                    metrics = metrics,
                                    presentation = instanceSelectionPresentation(
                                        version.getVersionName() == activeKey
                                    ),
                                    probe = probes[path],
                                    context = context,
                                    menuOpen = menuKey == path,
                                    menuUsable = probes[path]?.valid != false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                    onSelect = { select(version) },
                                    onMenuToggle = {
                                        menuKey = reduceInstanceMenu(
                                            openKey = menuKey,
                                            key = path,
                                            action = InstanceMenuAction.Toggle,
                                        )
                                    },
                                    onMenuAction = { action -> runAction(action, version) },
                                    onLaunch = { launch(version) },
                                )
                            }
                        }
                    }
                }
            }
        }

        drawerVersion?.let { version ->
            OxideInstanceDrawer(
                version = version,
                metrics = metrics,
                onDismiss = { drawerKey = null },
                onLaunch = {
                    drawerKey = null
                    launch(version)
                },
            )
        }
    }

    // 重命名/复制/删除的对话框与版本管理页共用，落到 VersionsManager 上
    VersionsOperation(
        versionsOperation = operation,
        updateVersionsOperation = { operation = it },
        submitError = submitError,
    )
}

/**
 * 一次磁盘探测的结果
 *
 * `isValid()` / `isIsolation()` / `getRamAllocation()` 都要碰到文件系统或 MMKV，
 * 于是和模组数量、上次运行日志的时间戳一起在 IO 上一次性取回来。
 */
private data class InstanceProbe(
    val valid: Boolean,
    val isolated: Boolean,
    val ramMb: Int,
    val modsCount: Int,
    val lastRunAt: Long,
)

/** 动作失败时的一行提示，就地显示，不阻塞页面 */
@Composable
private fun OxideInstanceErrorRow(
    message: ErrorViewModel.ThrowableMessage,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusControl)
            .padding(
                horizontal = ERROR_ROW_PADDING_H,
                vertical = ERROR_ROW_PADDING_V,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // resolveAndroidString 而不是 androidText：后者的返回值是 AndroidStringText，
        // 它只适合交给事件流，不能直接画进 Text
        val title = resolveAndroidString(message.title)
        val detail = resolveAndroidString(message.message)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(ERROR_ROW_GAP))
        OxideButton(
            // 这一颗只是把这条提示收掉，并没有任何东西被取消；
            // 写成"Cancel"会让用户以为刚才那个操作被撤回来了
            text = stringResource(R.string.oxide_ins_dismiss),
            onClick = onDismiss,
            tone = OxideButtonTone.Ghost,
        )
    }
}

/** 写回真实的置顶状态，失败时把异常交给调用方 */
private fun setPinned(
    version: Version,
    pinned: Boolean,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
) {
    runCatching { version.setPinnedAndSave(pinned) }.onFailure { e ->
        Logger.error(TAG, "Failed to save the pinned state.", e)
        submitError(
            ErrorViewModel.ThrowableMessage(
                title = androidText(R.string.oxide_ins_save_failed_title),
                message = androidText(e.getMessageOrToString()),
            )
        )
    }
}

/**
 * 单张实例卡
 *
 * 结构与参考稿的 `.instanceCard` 一致：顶部实例名与齿轮、中部两个统计块、
 * 最下面一排徽章和播放按钮。
 *
 * 选中态只有三处，且都不参与布局：表面与描边浓淡（[OxideSurface] 的 selected）、
 * 固定槽位里的一个小圆点、以及给读屏软件的 stateDescription。指示槽位在每一张
 * 卡片上都存在，所以选中哪一张都不会改变任何一张卡片的几何。
 */
@Composable
private fun OxideInstanceCard(
    version: Version,
    metrics: OxideMetrics,
    presentation: InstanceSelectionPresentation,
    probe: InstanceProbe?,
    context: Context,
    /** 这个齿轮的动作菜单是否处于展开状态（逻辑态） */
    menuOpen: Boolean,
    /** 菜单里依赖版本有效性的那几项是否可用 */
    menuUsable: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuAction: (InstanceAction) -> Unit,
    onLaunch: () -> Unit,
) {
    val versionInfo = remember(version) { version.getVersionInfo() }

    // 版本信息是解析 JSON 得到的纯内存对象，读它不需要碰磁盘
    val subtitle: String? = remember(version) {
        val minecraft = versionInfo?.minecraftVersion?.takeIf { it.isNotBlank() }
        val loaders = versionInfo?.loaderInfos
            ?.map { loader -> "${loader.loader.displayName} ${loader.version}".trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        listOfNotNull(
            minecraft,
            loaders.takeIf { list -> list.isNotEmpty() }?.joinToString(" + "),
        ).joinToString(" \u00b7 ").takeIf { it.isNotEmpty() }
    }

    val name = version.getVersionName()
    // 探测结果还没回来之前一律当作不可用，绝不用猜的值先渲染出去
    val valid = probe?.valid == true
    val currentLabel = stringResource(R.string.oxide_ins_state_current)
    val configureDesc = stringResource(R.string.oxide_ins_configure, name)
    val playDesc = stringResource(R.string.oxide_ins_play, name)

    // 统计块：只有真实读到的量才出现，读不到就不画这一格
    val stats: List<Pair<String, String>> = probe?.let { probed ->
        instanceCardStats(lastRunAtMillis = probed.lastRunAt, ramMb = probed.ramMb)
            .mapNotNull { kind ->
                when (kind) {
                    InstanceStatKind.LastPlayed -> if (probed.lastRunAt > 0L) {
                        stringResource(R.string.oxide_ins_stat_last_played) to getTimeAgo(
                            context,
                            Instant.ofEpochMilli(probed.lastRunAt)
                        )
                    } else {
                        null
                    }

                    InstanceStatKind.Memory -> if (probed.ramMb > 0) {
                        stringResource(R.string.oxide_ins_stat_memory) to
                            context.getString(R.string.oxide_ins_mb, probed.ramMb)
                    } else {
                        null
                    }
                }
            }
    }.orEmpty()

    val badges: List<Pair<String, OxideBadgeTone>> = probe?.let { probed ->
        instanceCardBadges(
            InstanceBadgeFacts(
                valid = probed.valid,
                isolated = probed.isolated,
                pinned = version.pinnedState,
                modsCount = probed.modsCount,
            )
        )
            .take(presentation.badgeSlots)
            .map { kind ->
                when (kind) {
                    InstanceBadgeKind.Invalid ->
                        stringResource(R.string.oxide_ins_badge_invalid) to OxideBadgeTone.Warn

                    InstanceBadgeKind.Mods ->
                        stringResource(R.string.oxide_ins_badge_mods, probed.modsCount) to
                            OxideBadgeTone.Neutral

                    InstanceBadgeKind.Pinned ->
                        stringResource(R.string.oxide_ins_badge_pinned) to OxideBadgeTone.Neutral

                    InstanceBadgeKind.Isolated ->
                        stringResource(R.string.oxide_ins_badge_isolated) to OxideBadgeTone.Active

                    InstanceBadgeKind.Shared ->
                        stringResource(R.string.oxide_ins_badge_shared) to OxideBadgeTone.Neutral
                }
            }
    }.orEmpty()

    // 齿轮的实测矩形：动作浮层要挂在它下面，而浮层走独立窗口，
    // 位置必须自己算，不能指望框架把网格里那个锚点传进来
    var gearBounds by remember { mutableStateOf(IntRect.Zero) }
    // 记住一份读取器：onGloballyPositioned 每次滚动都会更新 gearBounds，
    // 而定位器只在插入时创建一次，它读到的必须是当前值
    val anchorBounds = remember { { gearBounds } }

    // 菜单的生死与显隐分开：收起时先把 Popup 留着跑完退场动画，再摘掉，
    // 否则菜单会"啪"地一下消失，那正是用户记得的那个生硬手感
    var menuMounted by remember { mutableStateOf(menuOpen) }
    var menuExpanding by remember { mutableStateOf(menuOpen) }
    LaunchedEffect(menuOpen) {
        if (menuOpen) {
            menuMounted = true
            menuExpanding = true
        } else if (menuMounted) {
            menuExpanding = false
            delay(Oxide.Motion.PopoverMs.toLong())
            menuMounted = false
        }
    }

    Box(modifier = modifier) {
        OxideSurface(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    selected = presentation.announceAsCurrent
                    if (presentation.announceAsCurrent) stateDescription = currentLabel
                },
            selected = presentation.announceAsCurrent,
            onClick = onSelect,
            contentPadding = PaddingValues(
                horizontal = CARD_PADDING_H,
                vertical = CARD_PADDING_V,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // 固定槽位：选中与否宽度完全一样，高度为 0，圆点用 offset 画出去，
                // 因此这一格永远不会把卡片顶高
                Box(
                    modifier = Modifier
                        .width(presentation.indicatorSlotWidthDp.dp)
                        .height(0.dp)
                ) {
                    if (presentation.indicator == InstanceSelectedIndicator.Dot) {
                        Box(
                            modifier = Modifier
                                .offset(y = 6.dp)
                                .size(3.dp)
                                .clip(Oxide.RadiusToggle)
                                .background(Oxide.FgMuted)
                        )
                    }
                }
                Spacer(Modifier.width(3.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.InstanceVersion.fontSize,
                        lineHeight = Oxide.Type.InstanceVersion.lineHeight,
                        fontWeight = Oxide.Type.InstanceVersion.fontWeight,
                        letterSpacing = Oxide.Type.InstanceVersion.letterSpacing,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let { text ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = text,
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.Body.fontSize,
                            lineHeight = Oxide.Type.Body.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(metrics.cardGap))
                OxideInstanceGearButton(
                    onClick = onMenuToggle,
                    enabled = valid,
                    contentDescription = configureDesc,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        gearBounds = coords.toWindowIntRect()
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            if (stats.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STAT_GAP),
                ) {
                    stats.forEach { (label, value) ->
                        OxideInstanceMiniStat(
                            label = label,
                            value = value,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(metrics.cardGap))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 徽章行自己滚动，播放按钮宽度固定：徽章再多也不会把播放挤掉，
                // 窄卡片上先看到播放（唯一的主操作），其余靠滑动
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
                ) {
                    badges.forEach { (text, tone) ->
                        OxideBadge(text = text, tone = tone)
                    }
                }
                Spacer(Modifier.width(metrics.cardGap))
                OxideButton(
                    text = stringResource(R.string.oxide_ins_play_short),
                    onClick = onLaunch,
                    enabled = valid,
                    tone = OxideButtonTone.Primary,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = playDesc },
                )
            }
        }

        if (menuMounted) {
            OxideInstanceActionMenu(
                version = version,
                usable = menuUsable,
                pinned = version.pinnedState,
                expanding = menuExpanding,
                anchorBounds = anchorBounds,
                onDismiss = onMenuToggle,
                onAction = onMenuAction,
            )
        }
    }
}

/** 齿轮的实测矩形转成窗口像素坐标下的 IntRect */
private fun LayoutCoordinates.toWindowIntRect(): IntRect {
    val origin = positionInWindow()
    return IntRect(
        left = origin.x.roundToInt(),
        top = origin.y.roundToInt(),
        right = (origin.x + size.width).roundToInt(),
        bottom = (origin.y + size.height).roundToInt(),
    )
}

/** 卡片里的一个统计小方块，对应参考稿的 `.miniStat` */
@Composable
private fun OxideInstanceMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.LineFaint), Oxide.RadiusControl)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = Oxide.FgGhost,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 卡片上的齿轮
 *
 * 参考稿的齿轮是一个 29px 的深色方块：常态 `#101010` 底、`#292929` 边、`#777` 字，
 * 按下时转 14°、字变亮、边变亮，`.34s` 的 `var(--ease)`。这里照抄这一组数值，
 * 只保留旋转与两层颜色变化——没有回弹、没有缩放、没有发光。
 *
 * 交互契约与 [OxideIconButton] 一致（同样的圆角、同样的无涟漪点击），
 * 单独写是为了能加上参考稿那一点旋转与底板。
 */
@Composable
private fun OxideInstanceGearButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (pressed && enabled) GEAR_ROTATION_DEG else 0f,
        animationSpec = tween(GEAR_MS, easing = OxideEasing),
        label = "oxideGear",
    )

    Box(
        modifier = modifier
            .size(GEAR_SIZE)
            .rotate(rotation)
            .clip(Oxide.RadiusControl)
            .background(if (pressed) Oxide.BgButtonHover else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (pressed) Oxide.Line2 else Oxide.Border),
                Oxide.RadiusControl,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = GEAR_GLYPH,
            color = if (enabled && pressed) Oxide.Fg else Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

/**
 * 齿轮展开的动作浮层
 *
 * 参考稿的动作菜单是 `position:fixed` 的 `.popover`：从锚点下方 6px、缩放 .988
 * 升到 1，配合 180ms 的淡入。这里用 [Popup] 而不是把面板塞进卡片的布局里：
 * Popup 走独立窗口，所以既不会被卡片的圆角裁掉，也不会把网格撑开；
 * 参考稿把菜单放在 fixed 的 portal 层，理由完全一样。
 *
 * [expanding] 走的是反向的那一段：面板先在原地收回去，[OxideInstanceCard] 再把它
 * 从组合里摘掉，否则退场动画根本没机会播——菜单会"啪"地一下消失，
 * 那正是用户记得的那个"生硬"的来源。
 */
@Composable
private fun OxideInstanceActionMenu(
    version: Version,
    usable: Boolean,
    pinned: Boolean,
    expanding: Boolean,
    onDismiss: () -> Unit,
    anchorBounds: () -> IntRect,
    onAction: (InstanceAction) -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = remember(density) { with(density) { ACTION_MENU_MARGIN.roundToPx() } }

    Popup(
        popupPositionProvider = remember(anchorBounds, marginPx) {
            OxideActionMenuPositionProvider(anchorBounds, marginPx)
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
        onDismissRequest = onDismiss,
    ) {
        val progress by animateFloatAsState(
            targetValue = if (expanding) 1f else ACTION_MENU_SCALE_FROM,
            animationSpec = tween(Oxide.Motion.PopoverMs, easing = OxideEasing),
            label = "oxideActionMenu",
        )
        val fade by animateFloatAsState(
            targetValue = if (expanding) 1f else 0f,
            animationSpec = tween(Oxide.Motion.PopoverFadeMs, easing = OxideEasing),
            label = "oxideActionMenuFade",
        )

        val name = version.getVersionName()
        val actions = instanceMenuActions(usable = usable, pinned = pinned)
        val menuDesc = stringResource(R.string.oxide_ins_actions_of, name)

        Column(
            modifier = Modifier
                .width(ACTION_MENU_WIDTH)
                .heightIn(max = ACTION_MENU_MAX_HEIGHT)
                .graphicsLayer {
                    alpha = fade
                    scaleX = progress
                    scaleY = progress
                    // 参考稿 .popover 的 transform-origin 是左上角
                    transformOrigin = TransformOrigin(1f, 0f)
                    translationY = ((1f - progress) * POPOVER_ENTER_OFFSET_DP).dp.toPx()
                }
                .clip(Oxide.RadiusPopover)
                .background(Oxide.PopoverBg)
                .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPopover)
                .padding(vertical = 4.dp)
                // Popup 是独立窗口，整块面板作为一个可读节点，
                // 下面的标题与每一行就不再各自重复念一遍
                .clearAndSetSemantics { contentDescription = menuDesc },
        ) {
            Text(
                text = name,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Oxide.Line)
            )

            actions.forEach { action ->
                OxideInstanceActionRow(
                    action = action,
                    onClick = { onAction(action) },
                )
            }
        }
    }
}

/** 浮层里的一项，对应参考稿的 `.popItem` */
@Composable
private fun OxideInstanceActionRow(
    action: InstanceAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .height(ACTION_ROW_HEIGHT)
            .clip(Oxide.RadiusControl)
            .background(if (pressed) Oxide.BgButtonHover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = actionGlyph(action),
            color = if (action == InstanceAction.Delete) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(actionLabelRes(action)),
            color = if (action == InstanceAction.Delete) Oxide.FgMuted else Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 菜单项的图标，只是用来扫一眼的记号，真正的名字是那一行文字 */
private fun actionGlyph(action: InstanceAction): String = when (action) {
    InstanceAction.Configure -> ACTION_GLYPH_CONFIGURE
    InstanceAction.Rename -> ACTION_GLYPH_RENAME
    InstanceAction.Copy -> ACTION_GLYPH_COPY
    InstanceAction.ExportModPack -> ACTION_GLYPH_EXPORT
    InstanceAction.SetPinned -> ACTION_GLYPH_PIN
    InstanceAction.ClearPinned -> ACTION_GLYPH_UNPIN
    InstanceAction.OpenFolder -> ACTION_GLYPH_FOLDER
    InstanceAction.Delete -> ACTION_GLYPH_DELETE
}

/** 菜单项的文案，全部走字符串资源 */
private fun actionLabelRes(action: InstanceAction): Int = when (action) {
    InstanceAction.Configure -> R.string.oxide_ins_action_configure
    InstanceAction.Rename -> R.string.oxide_ins_action_rename
    InstanceAction.Copy -> R.string.oxide_ins_action_copy
    InstanceAction.ExportModPack -> R.string.oxide_ins_action_export
    InstanceAction.SetPinned -> R.string.oxide_ins_action_pin
    InstanceAction.ClearPinned -> R.string.oxide_ins_action_unpin
    InstanceAction.OpenFolder -> R.string.oxide_ins_action_open_folder
    InstanceAction.Delete -> R.string.oxide_ins_action_delete
}

/**
 * 动作浮层的位置
 *
 * 复用下拉面板那套几何规则，而不是另写一份：右边缘对齐（浮层挂在卡片右上角的
 * 齿轮下面，从右往左读比较自然）、下方放不下就翻到上方、最后夹在窗口内，
 * 因此贴着窗口右缘或下缘的卡片弹出的浮层也是完整可见的。
 */
private class OxideActionMenuPositionProvider(
    private val anchorBoundsOf: () -> IntRect,
    private val marginPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = calculateOxidePopoverPosition(
        // 用实测锚点而不是框架给的 anchorBounds：齿轮在 LazyVerticalGrid 的
        // 滚动容器里，两者坐标系一致时实测值才等于屏幕上真实的位置
        anchor = anchorBoundsOf(),
        window = windowSize,
        panel = popupContentSize,
        margin = marginPx,
        alignment = OxidePopoverAlignment.END,
    )
}

/** 参考稿的两条贝塞尔曲线：`cubic-bezier(.2,.82,.18,1)` */
private val OxideEasing = CubicBezierEasing(0.2f, 0.82f, 0.18f, 1f)

/** 参考稿 `.gear` 的 29px 与 `rotate(14deg)` */
private val GEAR_SIZE = 26.dp
private const val GEAR_ROTATION_DEG = 14f
private const val GEAR_MS = 340

private const val GEAR_GLYPH = "\u2699"

/** 参考稿 `.popover` 的进入位移 */
private const val POPOVER_ENTER_OFFSET_DP = 6f

/** 参考稿 `.popover` 起始缩放 .988，这里收成 .94，与下拉面板同一档 */
private const val ACTION_MENU_SCALE_FROM = 0.94f

/** 面板与锚点、面板与窗口边缘之间的余量，与下拉面板一致 */
private val ACTION_MENU_MARGIN = 6.dp

/** 参考稿 `.popItem` 是 42px，这里按启动器的密度收窄到 30dp */
private val ACTION_ROW_HEIGHT = 30.dp
private val ACTION_MENU_MAX_HEIGHT = 320.dp

private const val ACTION_GLYPH_CONFIGURE = "\u2699"
private const val ACTION_GLYPH_RENAME = "\u270E"
private const val ACTION_GLYPH_COPY = "\u29C9"
private const val ACTION_GLYPH_EXPORT = "\u2193"
private const val ACTION_GLYPH_PIN = "\u2605"
private const val ACTION_GLYPH_UNPIN = "\u2606"
private const val ACTION_GLYPH_FOLDER = "\u2197"
private const val ACTION_GLYPH_DELETE = "\u2715"

/**
 * 拿 MainActivity 上那一个 ScreenBackStackViewModel
 *
 * Compose 的 `viewModel()` 解析到的是当前 NavDisplay 条目自己的 ViewModelStore，
 * 那是一个新的实例，往它上面推导航不会有任何反应——真正渲染 MainScreen 的
 * 是 MainActivity 用 `by viewModels()` 持有的那一份。这里显式取活动的
 * ViewModelStore，键与 `by viewModels()` 完全一致，因此拿到的就是同一个对象。
 * 取不到时返回 null，调用方自己决定怎么退化（导出整合包会提示失败，其余动作不受影响）。
 */
@Composable
internal fun rememberOxideScreenBackStack(): ScreenBackStackViewModel? {
    val activity = LocalMainActivity.current ?: return null
    return remember(activity) {
        runCatching { ViewModelProvider(activity)[ScreenBackStackViewModel::class.java] }.getOrNull()
    }
}

/**
 * 拿 MainActivity 上那个 EventViewModel
 *
 * 页面签名里没有它，而 MainActivity 已经 `by viewModels()` 持有了一份并订阅了事件流，
 * 因此优先直接取活动上的实例，取不到再退回 Compose 的 `viewModel()`。
 * MainActivity 收到 `Event.Launch.Game` 后会走既有的 `tryLaunch`，快速启动的埋点也都在那条链路上。
 */
@Composable
internal fun rememberOxideEventViewModel(): EventViewModel {
    val context = LocalContext.current
    // 组合局部只能在 composable 作用域里读，remember 的计算块不是作用域，所以先取出来。
    val activity = LocalMainActivity.current
    val fromActivity = remember(activity) { activity?.eventViewModel }
    val fromStore: EventViewModel = viewModel()
    return fromActivity ?: fromStore
}
