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
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchFilter
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeCategory
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import dev.oxide.launcher.utils.string.isNotEmptyOrBlank

/**
 * CurseForge 平台的 API 链接
 * [CurseForge REST API](https://docs.curseforge.com/rest-api/?shell#base-url)
 */
const val CURSEFORGE_API = "https://api.curseforge.com/v1"

/**
 * MCIM 镜像：CurseForge 平台的 API 链接
 * [MCIM CurseForge API](https://github.com/mcmod-info-mirror/mcim-rust-api?tab=readme-ov-file#curseforge)
 */
const val MCIM_CURSEFORGE_API = "https://mod.mcimirror.top/curseforge/v1"

/**
 * CurseForge 服务端强制的分页上限。
 *
 * 服务端会校验这两个值，越界直接返回 HTTP 400：
 *   PageSize must be between 1 and 50.
 *   Index must be between 0 and 10000. / Index + PageSize cannot exceed 10000
 *
 * 这些约束只写在服务端的校验器里，客户端不照着文档实现就一定会漏掉，
 * 所以分页参数必须在这里收敛，而不是把界面状态直接透传出去。
 */
object CurseForgePaging {
    /** 单页最少条目数 */
    const val MIN_PAGE_SIZE = 1

    /** 单页最多条目数，超过即 400（注意不是 100） */
    const val MAX_PAGE_SIZE = 50

    /** 索引允许的最大值（不含），超过即 400 */
    const val MAX_INDEX = 10000

    /**
     * 收敛单页条目数
     * @return [MIN_PAGE_SIZE]..[MAX_PAGE_SIZE] 区间内的值
     */
    fun pageSize(requested: Int): Int = requested.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)

    /**
     * 收敛起始索引，并保证 `index + pageSize` 不越过 [MAX_INDEX]
     * @param requested 调用方请求的起始索引
     * @param pageSize 实际使用的单页条目数
     */
    fun index(requested: Int, pageSize: Int = MAX_PAGE_SIZE): Int {
        val size = pageSize.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val maxIndex = (MAX_INDEX - size).coerceAtLeast(0)
        return requested.coerceIn(0, maxIndex)
    }
}

/**
 * CurseForge 各接口的路径拼接。
 *
 * 抽成纯函数是为了能离线断言拼出来的 URL：接口前缀、路径以及 `/v1` 这一段一旦写错
 * （例如漏掉 `/v1`），线上只会表现为一个语义不明的 404；官方与镜像源的前缀不同，
 * 拼接错误在镜像上更难被发现。
 */
object CurseForgeEndpoints {
    /** 拼接前去掉结尾斜杠，避免拼出 `//mods` */
    private fun String.normalized() = trimEnd('/')

    /** 搜索资源 */
    fun search(api: String): String = "${api.normalized()}/mods/search"

    /** 单个项目详情 */
    fun project(api: String, projectId: String): String = "${api.normalized()}/mods/$projectId"

    /** 项目的版本文件列表 */
    fun projectFiles(api: String, projectId: String): String =
        "${api.normalized()}/mods/$projectId/files"

    /** 项目的某个版本文件 */
    fun projectFile(api: String, projectId: String, fileId: String): String =
        "${api.normalized()}/mods/$projectId/files/$fileId"

    /** 指纹批量匹配 */
    fun fingerprints(api: String): String = "${api.normalized()}/fingerprints"
}

fun PlatformSearchFilter.toCurseForgeRequest(
    query: String,
    platformClasses: PlatformClasses
): CurseForgeSearchRequest {
    val curseforgeCategories = categories.map { category ->
        category as? CurseForgeCategory
    }.toTypedArray()

    return CurseForgeSearchRequest(
        classId = platformClasses.curseforge.classID,
        categories = setOfNotNull(
            *curseforgeCategories
        ),
        searchFilter = query,
        gameVersion = gameVersion.takeIf { it.isNotEmptyOrBlank() }?.trim(),
        sortField = sortField,
        modLoader = modloader as? CurseForgeModLoader,
        index = index,
        pageSize = limit
    )
}