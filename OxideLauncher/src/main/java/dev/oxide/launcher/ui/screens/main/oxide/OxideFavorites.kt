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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.oxide.launcher.R
import dev.oxide.launcher.game.download.assets.favorites.FavoriteEntry
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 收藏列表里的一行
 *
 * 字段在收藏落地时就算好，因此绘制这一行不碰网络也不读 MMKV。
 *
 * [key] 与搜索结果用的是同一种身份（`平台/项目 id`），因此同一个项目在两处
 * 一定是同一枚星，不会出现"搜索里显示未收藏、收藏里却有"的错位。
 */
internal data class OxideFavoriteRow(
    val key: String,
    val platform: Platform,
    val platformName: String,
    val projectId: String,
    val classes: PlatformClasses,
    val title: String,
    val author: String?,
    val description: String?,
    val invalid: Boolean,
)

/**
 * 收藏条目 → 可绘制的行
 *
 * 排序：最近收藏的在前；同一天收藏的按名字排，避免翻页顺序抖动。
 * 已失效的条目排到最后但**不隐藏**——用户需要看见"我当初收藏的那个已经没了"，
 * 否则他会以为收藏丢了。纯函数，可直接单测。
 */
internal fun oxideFavoriteRows(
    entries: Collection<FavoriteEntry>,
    keyOf: (Platform, String) -> String,
    platformNameOf: (Platform) -> String,
): List<OxideFavoriteRow> = entries
    .map { entry ->
        OxideFavoriteRow(
            key = keyOf(entry.platform, entry.project.projectId),
            platform = entry.platform,
            platformName = platformNameOf(entry.platform),
            projectId = entry.project.projectId,
            classes = entry.project.classes,
            title = entry.project.title,
            author = entry.project.authors.joinToString(", ").takeIf { it.isNotBlank() },
            description = entry.project.description.takeIf { it.isNotBlank() },
            invalid = entry.invalid,
        )
    }
    .sortedWith(
        compareBy<OxideFavoriteRow> { row -> if (row.invalid) 1 else 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> row.title }
    )

/** 名称 / 作者 / 说明里搜 */
internal fun oxideFavoriteMatches(row: OxideFavoriteRow, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return row.title.contains(trimmed, ignoreCase = true) ||
        row.author.orEmpty().contains(trimmed, ignoreCase = true) ||
        row.description.orEmpty().contains(trimmed, ignoreCase = true)
}

/**
 * 收藏里的这一项能不能直接"安装"
 *
 * 只有整包放进固定目录的那四类（资源包、光影、存档、地图）能直接装：
 * 模组与整合包要走确认层，而确认层需要搜索结果里的那份 `PlatformSearchData`，
 * 收藏只存了缓存的元数据。因此这两类不给安装按钮——宁可少一个按钮，
 * 也不要一个跳过确认、直接开下载的入口。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideFavoriteInstallable(classes: PlatformClasses): Boolean = when (classes) {
    PlatformClasses.MOD -> false
    PlatformClasses.MOD_PACK -> false
    else -> true
}

/** 收藏列表 */
@Composable
internal fun OxideFavoritesPanel(
    metrics: OxideMetrics,
    rows: List<OxideFavoriteRow>,
    loading: Boolean,
    busy: Boolean,
    onInstall: (OxideFavoriteRow) -> Unit,
    onRemove: (OxideFavoriteRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
        return
    }
    if (rows.isEmpty()) {
        OxideEmptyState(
            title = stringResource(R.string.oxide_cap_dis_fav_empty),
            detail = stringResource(R.string.oxide_cap_dis_fav_empty_detail),
        )
        return
    }
    LazyColumn(modifier = modifier) {
        items(rows, key = { row -> row.key }) { row ->
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(all = metrics.cardGap),
            ) {
                Text(
                    text = row.title,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(metrics.secRowGap))
                // 来源平台与类别以文字给出；失效时那一行直接说明它已经拿不到
                Text(
                    text = if (row.invalid) {
                        stringResource(R.string.oxide_cap_dis_fav_invalid, row.platformName)
                    } else {
                        listOfNotNull(
                            row.author?.let { stringResource(R.string.oxide_dis_by, it) },
                            row.platformName,
                            stringResource(R.string.download_category_modpack)
                                .takeIf { row.classes == PlatformClasses.MOD_PACK },
                        ).joinToString(" · ")
                    },
                    color = if (row.invalid) Oxide.FgMuted else Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                row.description
                    ?.takeIf { it.isNotBlank() && !row.invalid }
                    ?.let { description ->
                        Spacer(Modifier.height(metrics.secRowGap))
                        Text(
                            text = description,
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                Spacer(Modifier.height(metrics.secRowGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                ) {
                    // 失效的条目只剩"移出收藏"：它已经没法从平台上装回来了
                    if (!row.invalid && oxideFavoriteInstallable(row.classes)) {
                        OxideButton(
                            text = stringResource(R.string.oxide_dis_action_install),
                            onClick = { onInstall(row) },
                            enabled = !busy,
                            tone = OxideButtonTone.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OxideButton(
                        text = stringResource(R.string.oxide_cap_dis_fav_remove),
                        onClick = { onRemove(row) },
                        tone = OxideButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(metrics.secRowGap))
        }
    }
}
