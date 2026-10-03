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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.ui.activities.MainActivity
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.getTimeAgo
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 实例页面
 *
 * 数据全部来自真实的已安装版本（[VersionsManager]），没有任何演示数据：
 * 卡片上能看到的每一个值都对应磁盘或设置里的一个真实量，读不到就不显示那一行。
 *
 * 布局与参考稿一致：卡片按列数铺满内容区、每次露出两行，因此行高由 [BoxWithConstraints]
 * 实测剩余高度算出来，列数由实测内容宽度算出来，都不写死。
 */
@Composable
fun OxideInstancesPage(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()

    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val isRefreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()

    // 选中态留在页面里：优先用用户点过的那个，否则落在真实的"上次使用"版本上，再否则是第一个真实版本
    var selectedKey by remember { mutableStateOf<String?>(null) }
    // 打开配置抽屉的版本，null 表示抽屉关闭
    var configuringVersion by remember { mutableStateOf<Version?>(null) }

    val activeName: String? = selectedKey
        ?.takeIf { key -> versions.any { it.getVersionName() == key } }
        ?: currentVersion?.getVersionName()
        ?: versions.firstOrNull()?.getVersionName()

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

    val select: (Version) -> Unit = { version ->
        if (VersionsManager.saveVersion(version)) {
            selectedKey = version.getVersionName()
        } else {
            // 无效版本不能被设为当前版本，与版本管理页保持一致
            eventViewModel.sendToast(androidText(R.string.oxide_ins_toast_invalid))
        }
    }
    val launch: (Version) -> Unit = { version ->
        eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(version))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 列数按实测内容宽度算，窗口被切分或折叠屏展开时会自己变
        val columns = metrics.gridColumns(maxWidth - metrics.pagePaddingH * 2)

        OxidePageColumn(metrics = metrics) {
            OxidePageTitle(
                text = stringResource(R.string.oxide_ins_page_title),
                trailing = {
                    OxideButton(
                        text = stringResource(R.string.oxide_ins_refresh),
                        onClick = { VersionsManager.refresh("OxideInstancesPage.refresh") },
                        enabled = !isRefreshing,
                        tone = OxideButtonTone.Secondary,
                    )
                },
            )
            OxideSectionLabel(
                text = stringResource(R.string.oxide_ins_page_subtitle_count, versions.size),
            )

            Spacer(Modifier.height(metrics.sectionGap))

            when {
                isRefreshing -> OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))

                versions.isEmpty() -> OxideEmptyState(
                    title = stringResource(R.string.oxide_ins_empty_title),
                    detail = stringResource(R.string.oxide_ins_empty_detail),
                    action = {
                        OxideButton(
                            text = stringResource(R.string.oxide_ins_install_new),
                            onClick = { onNavigate(OxidePage.Discover) },
                            tone = OxideButtonTone.Primary,
                        )
                    },
                )

                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 参考稿是两行网格：行高把实测剩余高度平分，不够高时退化为最小行高
                        val rowHeight = ((maxHeight - metrics.cardGap) / 2f)
                            .coerceAtLeast(metrics.topBarHeight * 2f + metrics.cardGap * 2f)

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                            verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = versions,
                                key = { version -> version.getVersionPath().absolutePath },
                            ) { version ->
                                OxideInstanceCard(
                                    version = version,
                                    metrics = metrics,
                                    isSelected = version.getVersionName() == activeName,
                                    probe = probes[version.getVersionPath().absolutePath],
                                    context = context,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                    onSelect = { select(version) },
                                    onConfigure = { configuringVersion = version },
                                    onLaunch = { launch(version) },
                                )
                            }
                        }
                    }
                }
            }
        }

        configuringVersion?.let { version ->
            OxideInstanceDrawer(
                version = version,
                metrics = metrics,
                onDismiss = { configuringVersion = null },
                onLaunch = {
                    configuringVersion = null
                    launch(version)
                },
            )
        }
    }
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

/**
 * 单张实例卡
 *
 * 结构与参考稿的 `.instanceCard` 一致：顶部版本号加齿轮、底部两个统计块、
 * 最下面一排徽章和播放按钮。选中态除了变色，还有左侧指示条、`SELECTED` 徽章
 * 和一层更亮的描边，不依赖颜色也能分辨。
 */
@Composable
private fun OxideInstanceCard(
    version: Version,
    metrics: OxideMetrics,
    isSelected: Boolean,
    probe: InstanceProbe?,
    context: Context,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onConfigure: () -> Unit,
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

    // 探测结果还没回来之前一律当作不可用，绝不用猜的值先渲染出去
    val valid = probe?.valid == true
    val configureDesc = stringResource(R.string.oxide_ins_configure, version.getVersionName())
    val playDesc = stringResource(R.string.oxide_ins_play, version.getVersionName())

    val memoryStat: Pair<String, String>? = probe?.let { probed ->
        stringResource(R.string.oxide_ins_stat_memory) to
                context.getString(R.string.oxide_ins_mb, probed.ramMb)
    }
    val playedStat: Pair<String, String>? = probe?.takeIf { it.lastRunAt > 0L }?.let { probed ->
        stringResource(R.string.oxide_ins_stat_last_played) to
                getTimeAgo(context, Instant.ofEpochMilli(probed.lastRunAt))
    }
    val stats = listOfNotNull(memoryStat, playedStat)

    val invalidBadge: Pair<String, OxideBadgeTone>? = probe?.takeIf { !it.valid }
        ?.let { stringResource(R.string.oxide_ins_badge_invalid) to OxideBadgeTone.Warn }
    val isolationBadge: Pair<String, OxideBadgeTone>? = probe?.let { probed ->
        if (probed.isolated) {
            stringResource(R.string.oxide_ins_badge_isolated) to OxideBadgeTone.Active
        } else {
            stringResource(R.string.oxide_ins_badge_shared) to OxideBadgeTone.Neutral
        }
    }
    val modsBadge: Pair<String, OxideBadgeTone>? = probe?.takeIf { it.modsCount > 0 }
        ?.let { stringResource(R.string.oxide_ins_badge_mods, it.modsCount) to OxideBadgeTone.Neutral }
    val pinnedBadge: Pair<String, OxideBadgeTone>? = if (version.pinnedState) {
        stringResource(R.string.oxide_ins_badge_pinned) to OxideBadgeTone.Neutral
    } else {
        null
    }
    val badges = listOfNotNull(invalidBadge, isolationBadge, modsBadge, pinnedBadge).take(3)

    OxideSurface(
        modifier = modifier.semantics { selected = isSelected },
        selected = isSelected,
        onClick = onSelect,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (isSelected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Oxide.RailBrush)
                )
                Spacer(Modifier.width(5.dp))
                OxideBadge(
                    text = stringResource(R.string.oxide_ins_selected_badge),
                    tone = OxideBadgeTone.Active,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.getVersionName(),
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Title.fontSize,
                    lineHeight = Oxide.Type.Title.lineHeight,
                    fontWeight = Oxide.Type.Title.fontWeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { text ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = text,
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(metrics.cardGap))
            OxideIconButton(
                onClick = onConfigure,
                glyph = "\u2699",
                enabled = valid,
                size = 26.dp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = configureDesc },
            )
        }

        Spacer(Modifier.weight(1f))

        // 统计块：读不到真实值就不渲染，而不是编一个
        if (stats.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
 * 拿 MainActivity 上那个 EventViewModel
 *
 * 页面签名里没有它，而 MainActivity 已经 `by viewModels()` 持有了一份并订阅了事件流，
 * 因此优先直接取活动上的实例，取不到再退回 Compose 的 `viewModel()`。
 * MainActivity 收到 `Event.Launch.Game` 后会走既有的 `tryLaunch`，快速启动的埋点也都在那条链路上。
 */
@Composable
internal fun rememberOxideEventViewModel(): EventViewModel {
    val context = LocalContext.current
    val fromActivity = remember(context) { (context as? MainActivity)?.eventViewModel }
    val fromStore: EventViewModel = viewModel()
    return fromActivity ?: fromStore
}