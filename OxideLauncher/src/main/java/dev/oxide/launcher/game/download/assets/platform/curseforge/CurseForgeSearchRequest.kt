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

import dev.oxide.launcher.game.download.assets.platform.PlatformSortField
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeCategory
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeClassID
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import io.ktor.http.Parameters

/**
 * [CurseForge api](https://docs.curseforge.com/rest-api/?shell#search-mods)
 */
data class CurseForgeSearchRequest(
    /** 游戏 ID */
    val gameId: Int = 432, //Minecraft 游戏 ID

    val classId: Int = CurseForgeClassID.MOD.classID,

    /** 搜索的资源的类别 */
    val categories: Set<CurseForgeCategory>? = null,

    /** 资源名称过滤搜索 */
    val searchFilter: String? = null,

    /** 游戏版本过滤 */
    val gameVersion: String? = null,

    /** 排序方式 */
    val sortField: PlatformSortField = PlatformSortField.RELEVANCE,

    val sortOrder: String = "desc",

    /** 模组加载器过滤 */
    val modLoader: CurseForgeModLoader? = null,

    /**
     * 要跳过的结果数量（用于分页），取值 [0, CurseForgePaging.MAX_INDEX)
     *
     * 越界时按 [CurseForgePaging.index] 收敛，因为服务端会直接返回 400
     */
    val index: Int = 0,

    /**
     * 要返回的结果条数，取值 [CurseForgePaging.MIN_PAGE_SIZE]..[CurseForgePaging.MAX_PAGE_SIZE]
     *
     * 服务端校验这个区间，越界返回 400，因此真正发出去的参数是 [effectivePageSize]
     */
    val pageSize: Int = 20,
) {
    /** 实际发往服务端的单页条数 */
    val effectivePageSize: Int get() = CurseForgePaging.pageSize(pageSize)

    /** 实际发往服务端的起始索引，保证 `index + pageSize` 不越过服务端上限 */
    val effectiveIndex: Int get() = CurseForgePaging.index(index, effectivePageSize)

    /**
     * 转换为 GET 参数
     */
    fun toParameters(): Parameters = Parameters.build {
        append("gameId", gameId.toString())
        append("classId", classId.toString())
        categories.mutableParameters(
            singleName = "categoryId",
            mutableName = "categoryIds",
            toString = { it.describe() },
        ) { name, value ->
            append(name, value)
        }
        // 空关键词不下发：服务端把 searchFilter= 与不传该参数当成两种不同的查询，
        // 带上空值会让"浏览列表"变成一次无意义的过滤
        searchFilter?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("searchFilter", it)
        }
        gameVersion?.takeIf { it.isNotBlank() }?.let {
            append("gameVersion", it)
        }
        modLoader?.let {
            append("modLoaderType", it.code.toString())
        }
        append("sortField", sortField.curseforge)
        append("sortOrder", sortOrder)
        append("index", effectiveIndex.toString())
        append("pageSize", effectivePageSize.toString())
    }

    /**
     * 分情况选择不同的参数名，处理参数列表为字符串值
     * 目前只有 categoryIds 能够正常使用
     */
    private fun <E> Collection<E>?.mutableParameters(
        singleName: String,
        mutableName: String,
        toString: E.(E) -> String?,
        append: (name: String, value: String) -> Unit
    ) {
        this?.takeIf { it.isNotEmpty() }?.let { c1 ->
            if (c1.size > 1) {
                c1.mapNotNull { it.toString(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { categoryStrings ->
                        //多过滤器的配置方式：name=[aaa,bbb,...]
                        append(mutableName, categoryStrings.joinToString(separator = ",", prefix = "[", postfix = "]") { it })
                    }
            } else {
                val value = c1.first()
                value.toString(value)
                    .takeIf { !it.isNullOrEmpty() }
                    ?.let { categoryString ->
                        append(singleName, categoryString)
                    }
            }
        }
    }
}