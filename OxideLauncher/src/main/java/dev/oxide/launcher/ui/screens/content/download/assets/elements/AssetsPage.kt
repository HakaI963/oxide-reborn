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

package dev.oxide.launcher.ui.screens.content.download.assets.elements

import dev.oxide.launcher.game.download.assets.platform.PlatformSearchData
import dev.oxide.launcher.game.download.assets.utils.ModTranslations

/**
 * 资源搜索结果页面信息
 * @param pageNumber 第几页
 * @param pageIndex 页面索引
 * @param totalPage 总页数
 * @param isLastPage 是否为最后一页
 * @param data 搜索结果缓存
 */
data class AssetsPage(
    val pageNumber: Int,
    val pageIndex: Int,
    val totalPage: Int,
    val isLastPage: Boolean,
    val data: List<Pair<PlatformSearchData, ModTranslations.McMod?>>
)

/**
 * 搜索结果页码运算，与界面状态无关的纯计算。
 *
 * 抽出来是为了能离线断言"页码随 index 递进、末页判定正确"，
 * 而不必把整个搜索页拉进单元测试。
 */
object AssetsPaging {

    /** 页大小非法时用它兜底，避免除零 */
    const val FALLBACK_PAGE_SIZE: Int = 20

    /**
     * 请求下一页时的起始索引
     *
     * index 在两个平台上都是"跳过多少条"，所以下一页是当前 index 加上一页大小，
     * 而不是页码加一。
     */
    fun nextIndex(currentIndex: Int, pageSize: Int): Int = currentIndex + pageSize(pageSize)

    /**
     * 请求上一页时的起始索引，下界为 0
     */
    fun previousIndex(currentIndex: Int, pageSize: Int): Int =
        (currentIndex - pageSize(pageSize)).coerceAtLeast(0)

    /**
     * 跳到指定页码时的起始索引
     * @param pageNumber 目标页码，从 1 开始
     */
    fun indexOfPage(pageNumber: Int, pageSize: Int): Int =
        (pageNumber.coerceAtLeast(1) - 1) * pageSize(pageSize)

    /**
     * 页大小，非法值退回 [FALLBACK_PAGE_SIZE]
     *
     * 页大小为 0 会让页码运算退化成永远停在第 1 页，所以必须挡在这里。
     */
    fun pageSize(requested: Int): Int =
        if (requested > 0) requested else FALLBACK_PAGE_SIZE

    /**
     * 是否为最后一页
     * @param index 当前页起始索引
     * @param pageSize 单页条数
     * @param resultCount 当前页实际返回条数
     * @param totalCount 结果总数；服务端会封顶，所以取不到总数时按不满页处理
     */
    fun isLastPage(index: Int, pageSize: Int, resultCount: Int, totalCount: Long): Boolean {
        val size = pageSize(pageSize)
        if (resultCount < size) return true
        return totalCount <= 0L || (index.toLong() + resultCount) >= totalCount
    }
}