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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.resource_pack.ResourcePackInfo
import dev.oxide.launcher.game.version.resource_pack.parseResourcePack
import dev.oxide.launcher.game.version.saves.SaveData
import dev.oxide.launcher.game.version.saves.isCompatible
import dev.oxide.launcher.game.version.saves.parseLevelDatFile
import dev.oxide.launcher.ui.screens.content.versions.elements.ShaderPackInfo
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.formatDate
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.utils.string.stripColorCodes
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "OxideContentManagers"

/** 改名 / 备份输入框里的字符上限，避免超长目录名把卡片撑开 */
private const val RENAME_MAX_LENGTH = 120

/**
 * 分类列的宽度
 *
 * 与设置页那一列同一套推导：155px 起步、跟着界面缩放一起放大，再夹在 132..260dp 之间。
 * 窄到这个宽度放不下"分类 + 面板"时就折叠成顶部一排横向标签。
 *
 * 选中动作那一栏（[OxideContentSelectionRail]）用的是**同一个**宽度：它是同一次
 * 会话里第二条窄栏，两条窄栏等宽才读得出它们是一对；而且这一栏上的按钮有
 * "删除选中"这样的长文案，宽度必须跟着界面缩放走，不能写死一个 dp。
 */
internal fun OxideMetrics.contentRailWidth(): Dp = (155f * guiScale).coerceIn(132f, 260f).dp

/**
 * 一个实例的五类内容
 *
 * 一块整页表面：顶部是返回与实例名，下面左侧是分类列、右侧是当前分类的面板。
 * 宽度不够时分类折叠成顶部横向标签，面板占满剩余高度，因此从 640x360 到
 * 1920x1080 都不会裁切或者重叠。
 *
 * [initialCategory] 让抽屉里的五个"Manage"按钮各自落在对应的那一类上，
 * 而不是所有人都回到第一页。
 */
@Composable
fun OxideContentManagerScreen(
    version: Version,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: OxideContentCategory = OxideContentCategory.Mods,
) {
    val eventViewModel = rememberOxideEventViewModel()
    val errorViewModel: ErrorViewModel = rememberOxideErrorViewModel()

    var selected by remember { mutableStateOf(initialCategory) }

    Column(modifier = modifier.fillMaxSize()) {
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
                    text = version.getVersionName(),
                    modifier = Modifier.weight(1f),
                )
            }
            OxideSectionLabel(text = stringResource(R.string.oxide_mgr_page_subtitle))
            Spacer(Modifier.height(metrics.sectionGap))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 并列门槛完全由 metrics 推导：分类列放得下标签，面板还得剩下一张卡
            val sideBySide = maxWidth >= metrics.cardMinWidth * 1.6f

            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideContentRail(
                        metrics = metrics,
                        current = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.width(metrics.contentRailWidth()).fillMaxHeight(),
                    )
                    OxideContentPanel(
                        metrics = metrics,
                        version = version,
                        category = selected,
                        eventViewModel = eventViewModel,
                        submitError = { errorViewModel.showError(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        // 模组那一块自己带一层子窗口；它的 ✕ 与顶部的 ← 一样是关掉整页
                        onCloseMods = onDismiss,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideContentChips(
                        metrics = metrics,
                        current = selected,
                        onSelect = { selected = it },
                    )
                    OxideContentPanel(
                        metrics = metrics,
                        version = version,
                        category = selected,
                        eventViewModel = eventViewModel,
                        submitError = { errorViewModel.showError(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 分类列表
// ---------------------------------------------------------------------------

/** 宽屏：左侧竖排分类 */
@Composable
private fun OxideContentRail(
    metrics: OxideMetrics,
    current: OxideContentCategory,
    onSelect: (OxideContentCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideContentCategory.entries.forEach { category ->
            OxideContentCategoryItem(
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
private fun OxideContentChips(
    metrics: OxideMetrics,
    current: OxideContentCategory,
    onSelect: (OxideContentCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        OxideContentCategory.entries.forEach { category ->
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
private fun OxideContentCategoryItem(
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
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------
// 版面（纯函数）
//
// 这一块面板在列表上方占掉多少高度、以及选中动作那一栏有多宽，都可以脱离组合算出来，
// 因此"勾选任何东西都不会让下面的列表变矮"这句话能钉在普通 JVM 单测里，
// 而不是靠人眼在设备上数行。做法与 [oxideModsPanelLayout] 同一套：把组合里的
// 算术搬成纯函数，UI 与单测共用同一个数。
//
// 文字的行高不读 `Oxide.Type`：那几份跟着设置变，在没有 MMKV 的 JVM 单测里读不到。
// 这里把基准行高各存一份（与 `OxideModsSurface` / `OxideLogPage` 里的做法相同），
// 再乘 [OxideMetrics.guiScale]——几何与排版因此仍然是同一个比例。
// ---------------------------------------------------------------------------

/**
 * 分类面板的版面
 *
 * @param chromeHeight 列表上方那一整块占掉的高度，见 [oxideContentChromeHeight]
 * @param railWidth 选中动作栏的宽度；没有选中时是 0.dp（那一栏整个不画）
 * @param selectionRowHeight 选中动作在纵向流里占掉的高度。横过来之后恒为 0.dp
 */
@Immutable
internal data class OxideContentPanelLayout(
    val chromeHeight: Dp,
    val railWidth: Dp,
    val selectionRowHeight: Dp,
)

/**
 * 分类面板的版面
 *
 * [selected] 只影响 [OxideContentPanelLayout.railWidth]：选中动作现在住在与搜索/筛选
 * 并排的那一条窄栏里，是横向的，因此**不占纵向空间**。
 * [hasImportRow] 只决定"从设备导入"那一行在不在（截图不支持导入）。
 *
 * 两个参数都不会改变 [OxideContentPanelLayout.chromeHeight] 与
 * [OxideContentPanelLayout.selectionRowHeight]：这正是本次要修的那条回归。
 */
internal fun oxideContentPanelLayout(
    metrics: OxideMetrics,
    category: OxideContentCategory,
    hasImportRow: Boolean = true,
    selected: Boolean = false,
): OxideContentPanelLayout = OxideContentPanelLayout(
    chromeHeight = oxideContentChromeHeight(metrics, category, hasImportRow),
    railWidth = if (selected) metrics.contentRailWidth() else 0.dp,
    // 此前"删除选中 / 取消选择"是压在列表上面的一整行 28dp 加一段间距，
    // 那一行现在住进右边的窄栏，因此在纵向流里不再占任何高度
    selectionRowHeight = 0.dp,
)

/**
 * 列表上方那一整块占多高
 *
 * 与 [OxideSettingsGroup] 内部那一列的子项一一对应，顺序也一样：
 *
 *  - 小节标题那一行（[OxideSection]）与它下方的 6dp 留白；
 *  - [OxideSurface] 的上下各 [OxideMetrics.rowGap] 内边距；
 *  - 搜索框（[OxideMetrics.secInputHeight]）与它后面那一段显式的间距；
 *  - "排序 + 刷新 + 只看有效 + 全选"那一行——"全选"此前独占一整行，
 *    现在并进本来就有的一行，因此它不再多花掉任何纵向空间；
 *  - 模组之外那三类没有状态筛选；[OxideContentCategory.canEnable] 说了算；
 *  - "从设备导入"那一行（[hasImportRow] 为 false 时没有）。
 *  - 分组底下与列表之间的那一段 [OxideMetrics.cardGap]。
 *
 * 不计入的只有**一次性反馈条**（错误、提示、就地确认与改名输入框）：它们出现时本来就会
 * 占位置，但那是一次性的状态，不是这一块的常态版面。选中动作那一栏同样不在这里——
 * 它是横向的，见 [oxideContentPanelLayout]。
 */
internal fun oxideContentChromeHeight(
    metrics: OxideMetrics,
    category: OxideContentCategory,
    hasImportRow: Boolean,
): Dp {
    // 与组合里那一列的子项逐项对应：显式的 Spacer 也算一个子项，
    // 因为 Column 自己的 spacedBy 会在它们之间**再**加一段
    val children = buildList {
        add(metrics.secInputHeight)
        add(metrics.secRowGap)
        add(contentControlRowHeight(metrics))
        if (category.canEnable) {
            add(metrics.secRowGap)
            add(contentChipRowHeight(metrics))
        }
        add(metrics.secRowGap)
        if (hasImportRow) {
            add(contentControlRowHeight(metrics))
            add(metrics.secRowGap)
        }
    }
    var body = 0.dp
    children.forEach { body += it }
    return contentSectionTitleHeight(metrics) +
        metrics.rowGap * 2 +
        body +
        metrics.rowGap * (children.size - 1) +
        metrics.cardGap
}

/**
 * "排序 + 刷新 + 只看有效 + 全选"与"从设备导入"这两行有多高
 *
 * 取两者里更高的那个：[OxideButton] 固定 28dp，而"只看有效"走的是
 * [OxideSettingRow]，高度是它的上下内边距加一行正文。
 */
private fun contentControlRowHeight(metrics: OxideMetrics): Dp = maxOf(
    CONTENT_BUTTON_HEIGHT,
    contentSettingRowHeight(metrics),
)

/** 筛选芯片那一行有多高：[OxideSecChip] 的上下各一段内边距加一行正文 */
private fun contentChipRowHeight(metrics: OxideMetrics): Dp =
    metrics.secRowGap * 2 + oxideContentLineHeight(CONTENT_BODY_BASE, metrics)

/** [OxideSettingRow] 那一行有多高：上下内边距加一行正文 */
private fun contentSettingRowHeight(metrics: OxideMetrics): Dp =
    CONTENT_SETTING_ROW_PADDING_V * 2 + oxideContentLineHeight(CONTENT_BODY_BASE, metrics)

/** 小节标题那一行有多高：`Oxide.Type.MicroLabel` 加 `OxideSection` 的下留白 */
private fun contentSectionTitleHeight(metrics: OxideMetrics): Dp =
    oxideContentLineHeight(CONTENT_MICRO_LABEL_BASE, metrics) + CONTENT_SECTION_TITLE_GAP

/** `OxideSettingRow` 的上下内边距，"只看有效"那一行由它决定高度 */
private val CONTENT_SETTING_ROW_PADDING_V: Dp = 7.dp

/** `OxideButton` 的固定高度 */
private val CONTENT_BUTTON_HEIGHT: Dp = 28.dp

/** `OxideSection` 标题行下的留白 */
private val CONTENT_SECTION_TITLE_GAP: Dp = 6.dp

/** 正文的基准行高，与 `Oxide.Type.Body` 一致 */
private val CONTENT_BODY_BASE = TextStyle(fontSize = 8.sp, lineHeight = 12.sp)

/** 小标签的基准行高，与 `Oxide.Type.MicroLabel` 一致 */
private val CONTENT_MICRO_LABEL_BASE = TextStyle(fontSize = 6.sp, lineHeight = 9.sp)

/** 文字的基准行高乘上界面缩放，与 [OxideMetrics] 里的几何同一个系数 */
private fun oxideContentLineHeight(base: TextStyle, metrics: OxideMetrics): Dp =
    oxideScaledTextStyle(base, metrics.guiScale).lineHeight.value.dp

// ---------------------------------------------------------------------------
// 分类面板
// ---------------------------------------------------------------------------

/**
 * 当前分类的那一块面板
 *
 * 数据全部来自真实的版本目录：模组走 `AllModReader`，资源包走 `parseResourcePack`，
 * 光影走 `shaderpacks` 目录，存档走 `parseLevelDatFile`，截图走 `screenshots` 目录。
 * 读取与所有磁盘写操作都在 IO 上、并且挂在可取消的协程里；组合期只读已经拿回来的列表。
 *
 * 整块面板是**一个** LazyColumn：控件区是它的前几项，下面才是列表。
 * 因此在 360dp 高的屏幕上长列表能正常滚动，而不会被套在另一个纵向滚动里
 * （同一个方向上嵌套两个可滚容器会在测量时直接抛异常）。
 *
 * v1.7.0 设备截图里的缺陷（菜单太小、选中一个之后就没地方选第二个、按钮堆在列表上面）
 * 在这里由 [oxideContentPanelLayout] 这一笔账决定：
 *
 *  - 选中动作不再压在列表上面，而是横过来放进控件那一项右侧的窄栏
 *    （[OxideContentSelectionRail]）。它是**横向**的一栏，不吃纵向空间，
 *    因此勾选任何一个条目都不会让下面的列表少一行。
 *  - 全选那枚从独占一整行并进本来就有的一行，因此上方这一块的纵向高度是常数。
 *
 * 窄栏能放进控件那一项而不是做成列表的兄弟节点，正是因为整块面板只有那一个滚动容器：
 * 做成兄弟节点就得把面板拆成不滚的控件区加一个 LazyColumn，那样搜索框与筛选会从
 * 随列表一起滚走变成常驻——那是另一处行为变更，不该和这一次的高度修复混在一起。
 * 代价是这一栏会随控件区一起滚走；换来的是列表高度与选中状态彻底无关。
 *
 * 实例设置那一页的五个内容标签直接复用这一块，因此这里不是 private。
 */
@Composable
internal fun OxideContentPanel(
    metrics: OxideMetrics,
    version: Version,
    category: OxideContentCategory,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 模组那一块子窗口的关闭按钮
     *
     * 只有走 [OxideContentManagerScreen] 那一路时才有：实例设置页自己已经有
     * 顶栏与返回，再给模组加一层子窗口就是两层关闭。
     */
    onCloseMods: (() -> Unit)? = null,
) {
    // 模组这一块是重建过的：图标、兼容性判定、详情与依赖面板都只有它有，
    // 因此走 [OxideModsSurface] 而不是这一块共用的行模型。
    if (category == OxideContentCategory.Mods) {
        OxideModsSurface(
            version = version,
            metrics = metrics,
            onClose = onCloseMods,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gameDir = remember(version) { version.getGameDir() }
    val folderDir = remember(version, category) { category.folder.getDir(gameDir) }

    var loading by remember(category) { mutableStateOf(true) }
    var rawEntries by remember(category) { mutableStateOf<List<OxideContentEntry>>(emptyList()) }

    var query by remember(category) { mutableStateOf("") }
    var stateFilter by remember(category) { mutableStateOf(OxideContentState.All) }
    var onlyValid by remember(category) { mutableStateOf(false) }
    val defaultSort = remember(category) { oxideContentDefaultSort(category) }
    var sort by remember(category) { mutableStateOf(defaultSort.first) }
    var ascending by remember(category) { mutableStateOf(defaultSort.second) }

    val selection = remember(category) { mutableStateListOf<String>() }
    var refreshKey by remember(category) { mutableIntStateOf(0) }

    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleteArmed by remember { mutableStateOf<List<String>?>(null) }
    var renameTarget by remember { mutableStateOf<OxideContentEntry?>(null) }
    var backupTarget by remember { mutableStateOf<OxideContentEntry?>(null) }

    val versionInfo = remember(version) { version.getVersionInfo() }
    val minecraftVersion = versionInfo?.minecraftVersion.orEmpty()
    val canQuickPlay = versionInfo?.quickPlay?.isQuickPlaySingleplayer == true

    // 下面这些在非组合的回调里也要用，因此先读一次：stringResource 是 @Composable，
    // 不能在 onClick / suspend lambda 里调用。
    val doneText = stringResource(R.string.oxide_mgr_done)
    val nameExistsText = stringResource(R.string.oxide_mgr_name_exists)
    val refreshLabel = stringResource(R.string.generic_refresh)
    val cancelLabel = stringResource(R.string.generic_cancel)
    val errorLabel = stringResource(R.string.generic_error)
    val deleteLabel = stringResource(R.string.generic_delete)
    val searchLabel = stringResource(R.string.generic_search)
    val onlyValidLabel = stringResource(R.string.manage_only_show_valid)
    val loadingLabel = stringResource(R.string.oxide_mgr_loading)
    val noMatchingLabel = stringResource(R.string.generic_no_matching_items)
    val renameLabel = stringResource(R.string.generic_rename)
    val backupLabel = stringResource(R.string.oxide_mgr_action_backup)
    val selectAllLabel = stringResource(R.string.oxide_mgr_action_select_all)
    val clearLabel = stringResource(R.string.oxide_mgr_action_clear_selection)
    val categoryTitle = stringResource(category.titleRes)

    // 读取：换分类、换版本或手动刷新都重新走一遍，全部在 IO 上且可取消
    LaunchedEffect(version, category, refreshKey) {
        loading = true
        errorMessage = null
        selection.clear()
        val read = withContext(Dispatchers.IO) {
            runCatching { readContentEntries(version, category) }
        }
        loading = false
        read.onSuccess { result ->
            rawEntries = result.entries
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Logger.error(TAG, "Failed to read ${category.folder.folderName}.", e)
            rawEntries = emptyList()
            errorMessage = e.getMessageOrToString()
        }
    }

    val visible = remember(rawEntries, query, stateFilter, onlyValid, sort, ascending) {
        val base = if (onlyValid) rawEntries.filter { it.valid } else rawEntries
        sortOxideContentEntries(
            entries = filterOxideContentEntries(base, query, stateFilter),
            sort = sort,
            ascending = ascending,
        )
    }
    val selectedEntries = remember(visible, selection) {
        visible.filter { entry -> entry.key in selection }
    }
    val counts = remember(rawEntries) { oxideContentStateCounts(rawEntries) }
    val everythingSelected = visible.isNotEmpty() && selection.size == visible.size

    fun submitFailure(e: Throwable) {
        Logger.error(TAG, "Content operation failed.", e)
        errorMessage = e.getMessageOrToString()
    }

    /**
     * 所有磁盘写操作都从这里走
     *
     * [after] 在 IO 之后、主线程之上跑。模组那一块不走这里：它有自己的状态
     * 持有者与"每次写完重扫目录"的纪律。
     */
    fun runIo(refresh: Boolean = true, after: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess {
                    notice = doneText
                    after()
                    if (refresh) refreshKey++
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    submitFailure(e)
                }
            busy = false
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = metrics.pagePaddingH),
    ) {
        item(key = "feedback") {
            Column {
                errorMessage?.let { detail ->
                    OxideSecErrorRow(
                        metrics = metrics,
                        title = errorLabel,
                        detail = detail,
                        dismissText = cancelLabel,
                        onDismiss = { errorMessage = null },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
                notice?.let { text ->
                    OxideNoticeRow(
                        text = text,
                        metrics = metrics,
                        onDismiss = { notice = null },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
                deleteArmed?.let { targets ->
                    OxideSecConfirmBar(
                        metrics = metrics,
                        text = stringResource(R.string.oxide_mgr_delete_confirm, targets.size),
                        confirmText = deleteLabel,
                        dismissText = cancelLabel,
                        onConfirm = {
                            val names = targets
                            deleteArmed = null
                            runIo {
                                names.forEach { name -> FileUtils.deleteQuietly(File(folderDir, name)) }
                            }
                        },
                        onDismiss = { deleteArmed = null },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
                renameTarget?.let { target ->
                    val extension = oxideRenameExtension(category, target)
                    OxideRenameForm(
                        metrics = metrics,
                        title = renameLabel,
                        initial = oxideRenameInitial(category, target),
                        // 改名成自己原来的名字不算冲突
                        conflictOf = { value ->
                            if (oxideRenameWouldConflict(folderDir, extension, target.fileName, value)) {
                                nameExistsText
                            } else {
                                null
                            }
                        },
                        onCancel = { renameTarget = null },
                        onConfirm = { value ->
                            renameTarget = null
                            runIo {
                                File(folderDir, target.fileName).renameTo(
                                    File(folderDir, "$value$extension")
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
                backupTarget?.let { target ->
                    OxideRenameForm(
                        metrics = metrics,
                        title = backupLabel,
                        initial = "${target.fileName}_backup",
                        conflictOf = { value ->
                            if (File(folderDir, value).exists()) nameExistsText else null
                        },
                        onCancel = { backupTarget = null },
                        onConfirm = { value ->
                            backupTarget = null
                            runIo {
                                FileUtils.copyDirectory(
                                    File(folderDir, target.fileName),
                                    File(folderDir, value),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                }
            }
        }

        item(key = "controls") {
            // 控件与选中动作并排。窄栏是横向的，不吃纵向空间，因此勾选任何条目
            // 都不会把下面的列表压矮——这正是 v1.7.0 截图里"选中第一个之后就没地方
            // 选第二个"的成因。整块面板仍然只有这一个 LazyColumn。
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    OxideSettingsGroup(
                        title = categoryTitle,
                        metrics = metrics,
                        trailing = {
                            // 计数只在真正读到东西之后才给，读的时候不给一个假数字
                            if (!loading) {
                                OxideBadge(text = stringResource(R.string.oxide_mgr_count, rawEntries.size))
                            }
                        },
                    ) {
                        OxideSecInput(
                            metrics = metrics,
                            value = query,
                            onValueChange = { query = it },
                            placeholder = searchLabel,
                        )
                        Spacer(Modifier.height(metrics.secRowGap))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 排序当前值以文字给出，符号本身不作为唯一信息
                            OxideButton(
                                text = stringResource(
                                    R.string.oxide_mgr_sort_label,
                                    sortLabelKey(sort, ascending),
                                ),
                                onClick = {
                                    val next = nextOxideContentSort(category, sort, ascending)
                                    sort = next.first
                                    ascending = next.second
                                },
                            )
                            OxideIconButton(
                                onClick = { refreshKey++ },
                                enabled = !loading && !busy,
                                glyph = "↻",
                                modifier = Modifier.oxideIconDescription(refreshLabel),
                            )
                            if (category.hasValidity) {
                                OxideSecPickerRow(
                                    label = onlyValidLabel,
                                    selected = onlyValid,
                                    onClick = { onlyValid = !onlyValid },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            // 全选此前独占一整行 28dp 加一段间距；并进本来就有的一行之后，
                            // 它自己并不随选中状态出现或消失，上方这一块的高度因此是常数
                            OxideButton(
                                text = if (everythingSelected) clearLabel else selectAllLabel,
                                onClick = {
                                    if (everythingSelected) {
                                        selection.clear()
                                    } else {
                                        visible.forEach { entry ->
                                            if (entry.selectable && entry.key !in selection) {
                                                selection.add(entry.key)
                                            }
                                        }
                                    }
                                },
                                enabled = visible.isNotEmpty(),
                            )
                        }

                        if (category.canEnable) {
                            Spacer(Modifier.height(metrics.secRowGap))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                            ) {
                                listOf(
                                    OxideContentState.All to counts.all,
                                    OxideContentState.Enabled to counts.enabled,
                                    OxideContentState.Disabled to counts.disabled,
                                ).forEach { (filter, count) ->
                                    OxideSecChip(
                                        label = stringResource(
                                            R.string.oxide_mgr_filter_count,
                                            filterLabel(filter),
                                            count,
                                        ),
                                        selected = stateFilter == filter,
                                        metrics = metrics,
                                        onClick = { stateFilter = filter },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(metrics.secRowGap))

                        // 从设备导入：这一类不支持时连这一行都不出现（截图）
                        val importAction = oxideContentImportAction(
                            version = version,
                            category = category,
                            onImported = { refreshKey++ },
                        )
                        if (importAction != null) {
                            OxideContentImportRow(
                                metrics = metrics,
                                category = category,
                                enabled = !busy && !loading,
                                onPick = importAction,
                            )
                            Spacer(Modifier.height(metrics.secRowGap))
                        }
                    }
                    Spacer(Modifier.height(metrics.cardGap))
                }

                // 没有选中时这一栏整个不出现，控件区因此拿回全部宽度
                if (selection.isNotEmpty()) {
                    Spacer(Modifier.width(metrics.secRowGap))
                    OxideContentSelectionRail(
                        metrics = metrics,
                        busy = busy,
                        onDeleteSelected = {
                            deleteArmed = oxideContentDeleteTargets(selectedEntries)
                        },
                        onClearSelection = { selection.clear() },
                    )
                }
            }
        }

        when {
            loading -> item(key = "loading") {
                OxideSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideLoadingRow(text = loadingLabel)
                }
            }

            rawEntries.isEmpty() -> item(key = "empty") {
                OxideSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideEmptyState(
                        title = stringResource(oxideContentEmptyTitle(category)),
                        detail = stringResource(oxideContentEmptyDetail(category)),
                    )
                }
            }

            visible.isEmpty() -> item(key = "no-match") {
                OxideSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideEmptyState(title = noMatchingLabel)
                }
            }

            else -> items(visible, key = { entry -> entry.key }) { entry ->
                OxideSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideContentRow(
                        metrics = metrics,
                        category = category,
                        entry = entry,
                        selected = entry.key in selection,
                        selectionMode = selection.isNotEmpty(),
                        canQuickPlay = canQuickPlay,
                        minecraftVersion = minecraftVersion,
                        onToggle = {
                            if (entry.key in selection) {
                                selection.remove(entry.key)
                            } else {
                                selection.add(entry.key)
                            }
                        },
                        onRename = { renameTarget = entry },
                        onBackup = { backupTarget = entry },
                        onQuickPlay = {
                            eventViewModel.sendEvent(
                                EventViewModel.Event.Launch.PlaySave(
                                    version = version,
                                    saveName = entry.fileName,
                                )
                            )
                        },
                        onDelete = { deleteArmed = listOf(entry.fileName) },
                        onOpen = {
                            val file = File(folderDir, entry.fileName)
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file,
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/png")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                )
                            }.onFailure { e -> submitFailure(e) }
                        },
                    )
                }
                Spacer(Modifier.height(metrics.secRowGap))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 选中动作栏
// ---------------------------------------------------------------------------

/**
 * 控件右侧那条选中动作栏
 *
 * v1.7.0 的选中动作是**压在列表上面的一整行**：一勾选就把列表的高度砍掉约一行，
 * 于是"选中第一个之后就没地方选第二个"。这里把同样这两个动作竖着放进控件区右侧的
 * 窄栏：宽度取 [OxideMetrics.contentRailWidth]——与左边那条分类列同一份推导，
 * 不是写死的 dp——高度由内容决定，与控件区并排而不是压在列表上面。
 *
 * 每个动作的文案、启用条件与回调与此前逐字相同，变的只有摆放的位置。
 * 它是横向的一栏，因此**不吃纵向空间**：这是 [oxideContentPanelLayout] 里
 * `selected` 只影响 `railWidth` 的原因。没有选中时调用方整个不画它。
 */
@Composable
internal fun OxideContentSelectionRail(
    metrics: OxideMetrics,
    busy: Boolean,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideContentSurface(
        modifier = modifier.width(metrics.contentRailWidth()),
        shape = Oxide.RadiusControl,
        contentPadding = PaddingValues(
            horizontal = metrics.secControlPadding,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideButton(
            text = stringResource(R.string.oxide_mgr_action_delete_selected),
            onClick = onDeleteSelected,
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
// 列表里的一行
// ---------------------------------------------------------------------------

@Composable
private fun OxideContentRow(
    metrics: OxideMetrics,
    category: OxideContentCategory,
    entry: OxideContentEntry,
    selected: Boolean,
    selectionMode: Boolean,
    canQuickPlay: Boolean,
    minecraftVersion: String,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onBackup: () -> Unit,
    onQuickPlay: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val badge = entry.badge ?: entry.badgeRes?.let { res -> stringResource(res) }

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
                enabled = entry.selectable,
                role = if (selectionMode) Role.Checkbox else Role.Button,
                onValueChange = { onToggle() },
            )
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            OxideRowCheckbox(selected = selected)
            Spacer(Modifier.width(metrics.secRowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                color = if (entry.enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.detail?.let { detail ->
                Text(
                    text = detail,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!entry.valid) {
                Text(
                    text = stringResource(R.string.oxide_mgr_invalid),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.hardcore) {
                Text(
                    text = stringResource(R.string.saves_manage_hardcore),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.incompatible) {
                // 不兼容不只靠颜色：这里有一行文案
                Text(
                    text = stringResource(R.string.oxide_mgr_incompatible, minecraftVersion),
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        badge?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.width(metrics.secRowGap))
            OxideBadge(text = text)
        }

        if (entry.loading) {
            Spacer(Modifier.width(metrics.secRowGap))
            OxideBadge(text = stringResource(R.string.oxide_mgr_reading))
        }

        Spacer(Modifier.width(metrics.secRowGap))

        if (category.canQuickPlay) {
            OxideIconButton(
                onClick = onQuickPlay,
                glyph = "▶",
                enabled = entry.valid && canQuickPlay,
                size = metrics.secControlHeight,
                modifier = Modifier.oxideIconDescription(
                    stringResource(
                        if (canQuickPlay) {
                            R.string.oxide_mgr_action_quick_play
                        } else {
                            R.string.oxide_mgr_quick_play_unsupported
                        }
                    )
                ),
            )
        }
        if (category == OxideContentCategory.Screenshots) {
            OxideIconButton(
                onClick = onOpen,
                glyph = "⊕",
                size = metrics.secControlHeight,
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_mgr_action_open_image)
                ),
            )
        }
        if (entry.canRename) {
            OxideIconButton(
                onClick = onRename,
                glyph = "✎",
                size = metrics.secControlHeight,
                modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_rename)),
            )
        }
        if (category.isDirectory) {
            // 备份是"把整个文件夹复制一份"，只有存档这种目录类型才有意义
            OxideIconButton(
                onClick = onBackup,
                glyph = "⧉",
                enabled = entry.valid,
                size = metrics.secControlHeight,
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_mgr_action_backup)
                ),
            )
        }
        OxideIconButton(
            onClick = onDelete,
            glyph = "✕",
            size = metrics.secControlHeight,
            modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_delete)),
        )
    }
}

/**
 * 一个 10dp 的勾选框
 *
 * 选中时中间那块才是实心，因此不只靠边框区分；整行的选中语义由外层的
 * `toggleable` 给出，这里只是把它画出来。
 */
@Composable
private fun OxideRowCheckbox(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
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
                    .size(4.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 就地确认与输入
// ---------------------------------------------------------------------------

/** 一条低调的完成提示，和错误条成对出现 */
@Composable
private fun OxideNoticeRow(text: String, metrics: OxideMetrics, onDismiss: () -> Unit) {
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            tone = OxideButtonTone.Ghost,
        )
    }
}

/** 改名 / 备份共用的就地输入：不用 Material 的对话框，横屏里也不会盖掉整块内容 */
@Composable
private fun OxideRenameForm(
    metrics: OxideMetrics,
    title: String,
    initial: String,
    conflictOf: (String) -> String?,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial.take(RENAME_MAX_LENGTH)) }
    val conflict = remember(value) { if (value.isBlank()) null else conflictOf(value.trim()) }

    OxideSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSecInput(
            metrics = metrics,
            value = value,
            onValueChange = { value = it.take(RENAME_MAX_LENGTH) },
            placeholder = title,
            label = title,
            isError = conflict != null,
        )
        if (conflict != null) {
            Text(
                text = conflict,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(metrics.secRowGap))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            OxideButton(
                text = stringResource(R.string.generic_save),
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank() && conflict == null,
                tone = OxideButtonTone.Primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 读取与映射
// ---------------------------------------------------------------------------

/**
 * 一次读取的结果
 *
 * 模组不在这里：它由 [OxideModsSurface] 自己读、自己渲染。
 */
private class ContentRead(
    val entries: List<OxideContentEntry>,
)

/**
 * 在 IO 上读一个分类的真实内容
 *
 * 每一类都走旧界面同样的入口，因此读到的就是同一批文件：
 * 资源包 `parseResourcePack`、光影只收 zip、存档 `parseLevelDatFile`、截图只收 png。
 */
private suspend fun readContentEntries(
    version: Version,
    category: OxideContentCategory,
): ContentRead = when (category) {
    OxideContentCategory.Mods -> ContentRead(entries = emptyList())
    OxideContentCategory.ResourcePacks -> readResourcePacks(version)
    OxideContentCategory.Shaders -> readShaders(version)
    OxideContentCategory.Saves -> readSaves(version)
    OxideContentCategory.Screenshots -> readScreenshots(version)
}

private suspend fun readResourcePacks(version: Version): ContentRead {
    val dir = VersionFolders.RESOURCE_PACK.getDir(version.getGameDir())
    val entries = mutableListOf<OxideContentEntry>()
    dir.listFiles()?.forEach { file ->
        val pack = parseResourcePack(file) ?: return@forEach
        entries += pack.toContentEntry()
    }
    return ContentRead(entries = entries)
}

private fun ResourcePackInfo.toContentEntry(): OxideContentEntry {
    val detail = buildList {
        add(file.name)
        fileSize?.let { add(formatFileSize(it)) }
        packFormat?.let { add("format $it") }
    }.joinToString(" · ")
    return OxideContentEntry(
        key = file.name,
        fileName = file.name,
        // rawName 已经是去掉颜色占位符的显示名，颜色由 Oxide 自己的层级承担
        displayName = rawName,
        detail = detail,
        badge = null,
        enabled = true,
        selectable = true,
        valid = isValid,
        size = fileSize,
        modifiedAt = file.lastModified(),
        canRename = isValid,
    )
}

private fun readShaders(version: Version): ContentRead {
    val dir = VersionFolders.SHADERS.getDir(version.getGameDir())
    // 光影包只能是后缀为 zip 的压缩包，与旧界面一致
    val packs = dir.listFiles()
        ?.filter { file -> file.isFile && file.extension.equals("zip", true) }
        ?.map { file -> ShaderPackInfo(file = file, fileSize = FileUtils.sizeOf(file)) }
        .orEmpty()
    return ContentRead(entries = packs.map { info -> info.toContentEntry() })
}

private fun ShaderPackInfo.toContentEntry(): OxideContentEntry {
    return OxideContentEntry(
        key = file.name,
        fileName = file.name,
        displayName = file.nameWithoutExtension,
        detail = "${file.name} · ${formatFileSize(fileSize)}",
        badge = null,
        enabled = true,
        selectable = true,
        valid = true,
        size = fileSize,
        modifiedAt = file.lastModified(),
        canRename = true,
    )
}

private suspend fun readSaves(version: Version): ContentRead {
    val dir = VersionFolders.SAVES.getDir(version.getGameDir())
    val entries = mutableListOf<OxideContentEntry>()
    val minecraftVersion = version.getVersionInfo()?.minecraftVersion.orEmpty()

    dir.listFiles()?.filter { file -> file.isDirectory }?.forEach { saveDir ->
        val data = parseLevelDatFile(
            saveFile = saveDir,
            levelDatFile = File(saveDir, "level.dat"),
            // 26.1+ 把世界生成设置搬到了这里
            worldGenDatFile = File(saveDir, "data/minecraft/world_gen_settings.dat")
                .takeIf { it.isFile && it.exists() },
        )
        entries += data.toContentEntry(minecraftVersion)
    }
    return ContentRead(entries = entries)
}

private fun SaveData.toContentEntry(minecraftVersion: String): OxideContentEntry {
    val title = levelName?.takeIf { it.isNotBlank() } ?: saveFile.name
    val lastPlayedStamp = lastPlayed?.takeIf { it > 0L } ?: saveFile.lastModified()
    return OxideContentEntry(
        key = saveFile.name,
        fileName = saveFile.name,
        // 存档名经常带颜色占位符，列表里去掉，颜色由 Oxide 自己的层级承担
        displayName = title.stripColorCodes(),
        detail = buildList {
            add(saveFile.name)
            levelMCVersion?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(formatDate(lastPlayedStamp))
        }.joinToString(" · "),
        // 极限模式没有 gameMode，所以它单独一个标记，界面上另起一行给出
        badge = null,
        badgeRes = gameMode?.nameRes,
        enabled = true,
        selectable = true,
        valid = isValid,
        incompatible = isValid && minecraftVersion.isNotBlank() && !isCompatible(minecraftVersion),
        hardcore = isValid && hardcoreMode == true,
        modifiedAt = saveFile.lastModified(),
        lastPlayed = lastPlayedStamp,
        canRename = isValid,
    )
}

private fun readScreenshots(version: Version): ContentRead {
    val dir = VersionFolders.SCREENSHOTS.getDir(version.getGameDir())
    val files = if (dir.exists() && dir.isDirectory) {
        dir.listFiles { file -> file.isFile && file.extension.lowercase() == "png" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    } else {
        emptyList()
    }
    return ContentRead(
        entries = files.map { file ->
            OxideContentEntry(
                key = file.name,
                fileName = file.name,
                displayName = file.nameWithoutExtension,
                detail = "${file.name} · ${formatFileSize(file.length())}",
                badge = null,
                enabled = true,
                selectable = true,
                valid = true,
                size = file.length(),
                modifiedAt = file.lastModified(),
            )
        }
    )
}

// ---------------------------------------------------------------------------
// 纯逻辑辅助
// ---------------------------------------------------------------------------

/** 改名框里的初值：与旧界面一致——资源包用去掉了颜色占位符的显示名，其余去掉扩展名 */
internal fun oxideRenameInitial(
    category: OxideContentCategory,
    entry: OxideContentEntry,
): String = when (category) {
    OxideContentCategory.ResourcePacks -> entry.displayName.take(RENAME_MAX_LENGTH)
    OxideContentCategory.Saves -> entry.fileName.take(RENAME_MAX_LENGTH)
    else -> entry.fileName.substringBeforeLast('.', entry.fileName).take(RENAME_MAX_LENGTH)
}

/** 改名后要补回的扩展名；文件夹类型的内容（存档）不加扩展名 */
internal fun oxideRenameExtension(
    category: OxideContentCategory,
    entry: OxideContentEntry,
): String {
    if (category.isDirectory) return ""
    val dot = entry.fileName.lastIndexOf('.')
    if (dot <= 0) return ""
    return entry.fileName.substring(dot)
}

/**
 * 改名会不会撞上已有的东西
 *
 * 目标与原名相同时不算冲突：用户把名字原样再输一遍是合法操作，
 * 不该被一条"已存在"挡住。纯函数，不碰磁盘。
 */
internal fun oxideRenameWouldConflict(
    dir: File,
    extension: String,
    currentName: String,
    newName: String,
): Boolean {
    val target = File(dir, "$newName$extension")
    if (!target.exists()) return false
    return !target.absolutePath.equals(File(dir, currentName).absolutePath, ignoreCase = true)
}

/** 排序按钮上的当前值：键与方向都以文字给出 */
internal fun sortLabelKey(sort: OxideContentSort, ascending: Boolean): String =
    "${sort.name}·${if (ascending) "asc" else "desc"}"

private fun filterLabel(state: OxideContentState): String = when (state) {
    OxideContentState.All -> "All"
    OxideContentState.Enabled -> "Enabled"
    OxideContentState.Disabled -> "Disabled"
}

internal fun oxideContentEmptyTitle(category: OxideContentCategory): Int = when (category) {
    OxideContentCategory.Mods -> R.string.oxide_mgr_empty_mods
    OxideContentCategory.ResourcePacks -> R.string.oxide_mgr_empty_resource_packs
    OxideContentCategory.Shaders -> R.string.oxide_mgr_empty_shaders
    OxideContentCategory.Saves -> R.string.oxide_mgr_empty_saves
    OxideContentCategory.Screenshots -> R.string.oxide_mgr_empty_screenshots
}

internal fun oxideContentEmptyDetail(category: OxideContentCategory): Int = when (category) {
    OxideContentCategory.Mods -> R.string.oxide_mgr_empty_mods_detail
    OxideContentCategory.ResourcePacks -> R.string.oxide_mgr_empty_resource_packs_detail
    OxideContentCategory.Shaders -> R.string.oxide_mgr_empty_shaders_detail
    OxideContentCategory.Saves -> R.string.oxide_mgr_empty_saves_detail
    OxideContentCategory.Screenshots -> R.string.oxide_mgr_empty_screenshots_detail
}