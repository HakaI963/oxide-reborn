/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.download.assets.platform.curseforge

import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchResult
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeData
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgePagination
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.isApproved
import dev.oxide.launcher.game.download.assets.platform.searchRankWithChineseBias
import dev.oxide.launcher.game.download.assets.utils.getTranslations
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsPage
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsPaging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgeSearchResult(
    /**
     * 响应数据
     *
     * 设为可见（而非 private）是为了让解析结果可以被单元测试直接断言
     */
    @SerialName("data")
    val data: Array<CurseForgeData>,

    /**
     * 响应分页信息
     *
     * 设为可见（而非 private）是为了让页码运算可以被单元测试直接断言
     */
    @SerialName("pagination")
    val pagination: CurseForgePagination
): PlatformSearchResult {
    override fun getAssetsPage(classes: PlatformClasses): AssetsPage {
        val mcmodData = data.mapNotNull { data0 ->
            if (!data0.isApproved()) return@mapNotNull null
            data0 to classes.getTranslations().getModBySlugId(data0.slug)
        }

        return AssetsPage(
            pageNumber = pageNumber(),
            pageIndex = pagination.index,
            totalPage = totalPage(),
            isLastPage = isLastPage(),
            data = mcmodData
        )
    }

    /**
     * 可见的项目列表：只保留服务端标记为公开的项目
     *
     * 单独抽出来是为了能离线断言"未过审的项目不会出现在列表里"，
     * 不必为了这一点把整个搜索页拉进单元测试。
     */
    fun approvedData(): Array<CurseForgeData> = data.filter { it.isApproved() }.toTypedArray()

    /** 当前页码，从 1 开始 */
    fun pageNumber(): Int {
        val pageSize = AssetsPaging.pageSize(pagination.pageSize)
        return pagination.index / pageSize + 1
    }

    /**
     * 是否为最后一页
     *
     * 服务端把 totalCount 封顶在 10000，所以最后一页由"取不满一页"或
     * "已到达封顶总数"任一条件判定，两者都来自同一份响应，不会互相矛盾。
     */
    fun isLastPage(): Boolean = AssetsPaging.isLastPage(
        index = pagination.index,
        pageSize = pagination.pageSize,
        resultCount = pagination.resultCount,
        totalCount = pagination.totalCount
    )

    /**
     * 总页数
     *
     * 服务端把 totalCount 封顶在 10000，所以这里算出的总页数就是可翻页的上限，
     * 与 [isLastPage] 用的是同一个来源，翻到最后一页就会停下。
     */
    fun totalPage(): Int {
        val pageSize = AssetsPaging.pageSize(pagination.pageSize)
        val count = pagination.totalCount.coerceAtLeast(0L)
        return ((count + pageSize - 1) / pageSize).toInt()
    }

    override fun processChineseSearchResults(
        searchFilter: String,
        classes: PlatformClasses
    ): PlatformSearchResult {
        val newData = data.filter { it.isApproved() }
            .searchRankWithChineseBias(searchFilter, classes) { it.slug }
            .toTypedArray()
        return CurseForgeSearchResult(newData, pagination)
    }
}