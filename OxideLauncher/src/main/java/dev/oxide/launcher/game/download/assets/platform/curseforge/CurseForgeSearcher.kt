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

import dev.oxide.launcher.game.download.assets.platform.AbstractPlatformSearcher
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchFilter
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeFile
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeFingerprintsMatches
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeProject
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeVersion
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeVersions
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.isApproved
import dev.oxide.launcher.game.version.mod.CURSEFORGE_FINGERPRINT_SKIP_BYTES
import dev.oxide.launcher.path.GLOBAL_CLIENT
import dev.oxide.launcher.utils.file.MurmurHash2Incremental
import dev.oxide.launcher.utils.network.decodeJson
import dev.oxide.launcher.utils.network.httpGetJson
import dev.oxide.launcher.utils.network.httpPostJson
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class CurseForgeSearcher(
    val api: String = CURSEFORGE_API,
    source: String = "Official CurseForge"
): AbstractPlatformSearcher(
    platform = Platform.CURSEFORGE,
    source = source
) {
    override suspend fun searchAssets(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): CurseForgeSearchResult {
        val response = GLOBAL_CLIENT.get(
            CurseForgeEndpoints.search(api)
        ) {
            searchFilter.toCurseForgeRequest(
                query = query,
                platformClasses = platformClasses
            ).toParameters().forEach { name, values ->
                url.parameters.appendAll(name, values)
            }
        }
        // 走 decodeJson 而不是 body<T>()：服务端在分页参数越界时返回 400，
        // 响应体是 RFC7807 错误对象而不是平台 JSON。这种情况必须以异常冒出去，
        // 不能被当成"搜索成功但没有结果"。
        return response.decodeJson()
    }

    override suspend fun getProject(projectID: String): CurseForgeProject {
        val project = httpGetJson<CurseForgeProject>(
            url = CurseForgeEndpoints.project(api, projectID)
        )
        if (!project.isApproved()) throw NotFoundException("The project {$projectID} is not in a publicly available state.")
        return project
    }

    /**
     * 在 CurseForge 平台获取某项目的某个文件
     */
    suspend fun getVersion(
        projectID: String,
        fileID: String,
    ): CurseForgeVersion {
        return httpGetJson(
            url = CurseForgeEndpoints.projectFile(api, projectID, fileID)
        )
    }

    /**
     * 在 CurseForge 平台根据分页获取项目的版本列表
     *
     * 分页参数经 [CurseForgePaging] 收敛：服务端对 index/pageSize 有硬校验，
     * 越界返回 400，而该接口的 400 响应体是空的，只能表现为"加载失败"。
     * @param index 开始处
     * @param pageSize 每页请求数量
     */
    suspend fun getVersions(
        projectID: String,
        index: Int = 0,
        pageSize: Int = CurseForgePaging.MAX_PAGE_SIZE
    ): CurseForgeVersions = httpGetJson(
        url = CurseForgeEndpoints.projectFiles(api, projectID),
        parameters = Parameters.build {
            append("index", CurseForgePaging.index(index, pageSize).toString())
            append("pageSize", CurseForgePaging.pageSize(pageSize).toString())
        }
    )

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<CurseForgeFile> {
        return getAllVersions(
            pageSize = CurseForgePaging.MAX_PAGE_SIZE,
            chunkSize = 20,
            maxConcurrent = 10,
            pageCallback = pageCallback,
            checkNotEmpty = { versions ->
                versions.data.isNotEmpty()
            },
            asyncVersions = { index, pageSize ->
                getVersions(
                    projectID = projectID,
                    index = index,
                    pageSize = pageSize,
                )
            },
            processVersions = { versions ->
                val files = versions?.data ?: emptyArray()
                files.toList() to files.size
            }
        )
    }

    override suspend fun getVersionByLocalFile(
        file: File,
        sha1: String
    ): CurseForgeFile? {
        val hash = MurmurHash2Incremental.computeHash(file, byteToSkip = CURSEFORGE_FINGERPRINT_SKIP_BYTES)
        return getFilesByFingerprints(listOf(hash)).values.firstOrNull()
    }

    /**
     * 通过多个本地文件的 CurseForge 指纹批量获取对应的文件信息
     * @return 键为文件指纹，值为匹配到的文件，未命中的指纹不在结果中
     */
    suspend fun getFilesByFingerprints(
        fingerprints: List<Long>
    ): Map<Long, CurseForgeFile> {
        if (fingerprints.isEmpty()) return emptyMap()
        val matches = httpPostJson<CurseForgeFingerprintsMatches>(
            url = CurseForgeEndpoints.fingerprints(api),
            body = CurseForgeFingerprintsRequest(fingerprints = fingerprints)
        )
        return parseExactFingerprintMatches(matches)
    }
}

/**
 * 从指纹匹配响应里取出精确命中的文件
 * @return 键为文件指纹，值为匹配到的文件，未命中的指纹不在结果中
 */
fun parseExactFingerprintMatches(matches: CurseForgeFingerprintsMatches): Map<Long, CurseForgeFile> =
    matches.data.exactMatches.orEmpty()
        .associate { it.file.fileFingerprint to it.file }

/**
 * 批量获取文件指纹匹配的请求体
 */
@Serializable
data class CurseForgeFingerprintsRequest(
    @SerialName("fingerprints")
    val fingerprints: List<Long>
)

/**
 * 服务端允许的最大页码（从 0 开始），超过它的页一律不能请求。
 *
 * 服务端的索引上限是硬校验，越界返回 400，而该接口的 400 响应体是空的，
 * 线上只能表现为"版本列表加载失败"，日志里也看不出是分页越界。
 * 翻页必须在这里停住，绝不能把越界索引发出去。
 */
fun maxVersionPageIndex(pageSize: Int): Int {
    val size = CurseForgePaging.pageSize(pageSize)
    // 最大的合法起始索引，再折算回页码
    return CurseForgePaging.index(CurseForgePaging.MAX_INDEX, size) / size
}

/**
 * 持续分页获取项目的所有版本文件，直到全部加载完成
 * @param pageSize 每页请求数量
 * @param chunkSize 一个区间的最大页数
 * @param maxConcurrent 同时最多允许的请求数
 * @param pageCallback 加载每一页时都通过此函数回调
 * @param checkNotEmpty 检查请求内容返回结果不为空
 * @param asyncVersions 异步获取单区块的版本数据
 * @param processVersions 加工返回数据，同时需要返回当前结果实际的页面大小
 */
private suspend fun <E, T> getAllVersions(
    pageSize: Int = CurseForgePaging.MAX_PAGE_SIZE,
    chunkSize: Int = 10,
    maxConcurrent: Int = 5,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _ , _ -> },
    checkNotEmpty: (E) -> Boolean,
    asyncVersions: suspend (index: Int, pageSize: Int) -> E,
    processVersions: suspend (E?) -> Pair<List<T>, Int>
): List<T> = withContext(Dispatchers.IO) {
    coroutineScope {
        val allVersions = mutableListOf<T>()
        /** 当前区间编号 */
        var currentChunk = 1
        /** 起始页码 */
        var startPage = 0
        /** 是否已经到达过最后一页，控制是否进入下一区间 */
        var reachedEnd = false

        val semaphore = Semaphore(maxConcurrent)

        //任何一页都不能越过服务端上限，越界一律视为已到末尾
        val lastPageIndex = maxVersionPageIndex(pageSize)

        while (!reachedEnd) {
            //创建当前区间的任务列表，越界的页根本不发请求
            val pageIndexes = (0 until chunkSize)
                .map { startPage + it }
                .takeWhile { it <= lastPageIndex }

            //区间内所有页都越界，说明已经翻到能翻的最后一页
            if (pageIndexes.isEmpty()) {
                reachedEnd = true
                break
            }

            val jobs = pageIndexes.map { pageIndex ->
                val index = pageIndex * pageSize

                async {
                    semaphore.withPermit {
                        val response = asyncVersions(index, pageSize)
                        //检查当前页返回的结果是否正常
                        //如果是最后一页之后的内容，则这里的列表是空的
                        if (checkNotEmpty(response)) {
                            //有东西，回调即可
                            pageCallback(currentChunk, pageIndex + 1)
                            response
                        } else null
                    }
                }
            }

            for ((i, job) in jobs.withIndex()) {
                val (files, realSize) = processVersions(job.await())
                files.takeIf { it.isNotEmpty() }?.let { list ->
                    allVersions.addAll(list)
                }

                //少于pageSize，已经是最后一页
                if (realSize < pageSize) {
                    reachedEnd = true
                    //取消后续页
                    for (j in (i + 1) until jobs.size) {
                        jobs[j].cancel()
                    }
                    break
                }
            }

            //如果没发现最后一页，则进入下一区间
            if (!reachedEnd) {
                startPage += chunkSize
                currentChunk++
            }
        }

        return@coroutineScope allVersions
    }
}