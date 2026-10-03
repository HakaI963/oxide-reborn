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

package dev.oxide.launcher.game.download.assets.platform.curseforge

import dev.oxide.launcher.path.GLOBAL_JSON
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索响应的解析
 *
 * 夹具是真实响应的形状：字段名照抄服务端，包括服务端会省略的 rating、
 * 会回传 null 的 links/logo，以及服务端多返回的未知字段。
 * 所以这些用例同时钉住"字段名对得上"和"缺失/多余/null 字段不会让整页解析失败"。
 */
class CurseForgeSearchResultTest {

    /** 一个真实形状的搜索结果项，未使用的可选字段刻意省略 */
    private val oneProject = """
        {
          "id": 394468,
          "gameId": 432,
          "name": "Sodium",
          "slug": "sodium",
          "links": {
            "websiteUrl": "https://www.curseforge.com/minecraft/mc-mods/sodium",
            "wikiUrl": null,
            "issuesUrl": "https://github.com/CaffeineMC/sodium/issues",
            "sourceUrl": "https://github.com/CaffeineMC/sodium"
          },
          "summary": "A modern rendering engine",
          "status": 4,
          "downloadCount": 52318402,
          "isFeatured": true,
          "primaryCategoryId": 6814,
          "categories": [
            {
              "id": 6814,
              "gameId": 432,
              "name": "Optimization",
              "slug": "optimization",
              "url": "https://www.curseforge.com/minecraft/mc-mods/optimization",
              "iconUrl": "https://media.forgecdn.net/avatars/68/14/x.png",
              "dateModified": "2014-07-04T09:24:14.98Z",
              "isClass": true,
              "classId": 6,
              "parentCategoryId": 6
            }
          ],
          "authors": [
            { "id": 125034413, "name": "jellysquid3", "url": "https://www.curseforge.com/members/jellysquid3" }
          ],
          "logo": {
            "id": 223373,
            "modId": 394468,
            "title": "logo.png",
            "description": "",
            "thumbnailUrl": "https://media.forgecdn.net/avatars/thumbnails/223/373/256/256/logo.png",
            "url": "https://media.forgecdn.net/avatars/223/373/logo.png"
          },
          "screenshots": [],
          "mainFileId": 4705339,
          "latestFiles": [],
          "latestFilesIndexes": [
            {
              "gameVersion": "1.20.1",
              "fileId": 4705339,
              "filename": "sodium-fabric-0.5.8.jar",
              "releaseType": 1,
              "gameVersionTypeId": 75125,
              "modLoader": 4
            }
          ],
          "dateCreated": "2020-04-04T18:04:23.573Z",
          "dateModified": "2024-03-11T09:36:35.26Z",
          "dateReleased": "2020-04-04T00:00:00Z",
          "allowModDistribution": true,
          "gamePopularityRank": 42,
          "isAvailable": true,
          "thumbsUpCount": 1200,
          "featuredProjectTag": null,
          "hasCommentsEnabled": true
        }
    """.trimIndent()

    private fun searchResult(
        index: Int,
        pageSize: Int,
        resultCount: Int,
        totalCount: Long,
        projects: Int = 1
    ): String {
        val data = (0 until projects).joinToString(",") {
            oneProject.replace("\"id\": 394468", "\"id\": ${394468 + it}")
        }
        return """
            {
              "data": [$data],
              "pagination": {
                "index": $index,
                "pageSize": $pageSize,
                "resultCount": $resultCount,
                "totalCount": $totalCount
              }
            }
        """.trimIndent()
    }

    private fun parse(json: String): CurseForgeSearchResult =
        GLOBAL_JSON.decodeFromString(CurseForgeSearchResult.serializer(), json)

    @Test
    fun `a real response parses into one page`() {
        val result = parse(searchResult(index = 0, pageSize = 20, resultCount = 20, totalCount = 10000))
        assertEquals(1, result.data.size)

        val project = result.data.first()
        assertEquals(394468, project.id)
        assertEquals("Sodium", project.name)
        assertEquals("sodium", project.slug)
        assertEquals("A modern rendering engine", project.summary)
        assertEquals(52318402L, project.downloadCount)
        assertTrue(project.isApproved())
    }

    @Test
    fun `an empty result is an empty page and not a failure`() {
        // 真的没有结果时服务端仍然会返回 pagination，这时才是空页
        val result = parse(searchResult(index = 0, pageSize = 20, resultCount = 0, totalCount = 0, projects = 0))
        assertTrue(result.data.isEmpty())
        assertEquals(1, result.pageNumber())
        assertEquals(0, result.totalPage())
        assertTrue(result.isLastPage())
    }

    @Test
    fun `the page number advances with the index`() {
        // 第 1 页是 index 0，不是 index 20
        assertEquals(1, parse(searchResult(0, 20, 20, 10000)).pageNumber())
        assertEquals(2, parse(searchResult(20, 20, 20, 10000)).pageNumber())
        assertEquals(3, parse(searchResult(40, 20, 20, 10000)).pageNumber())
        assertEquals(500, parse(searchResult(9980, 20, 20, 10000)).pageNumber())
    }

    @Test
    fun `the total page count follows the server total`() {
        assertEquals(500, parse(searchResult(0, 20, 20, 10000)).totalPage())
        assertEquals(6, parse(searchResult(0, 20, 19, 109)).totalPage())
        assertEquals(1, parse(searchResult(0, 20, 20, 20)).totalPage())
        assertEquals(0, parse(searchResult(0, 20, 0, 0)).totalPage())
    }

    @Test
    fun `a short page is the last page`() {
        assertTrue(parse(searchResult(0, 20, 19, 10000)).isLastPage())
        assertTrue(parse(searchResult(9980, 20, 20, 10000)).isLastPage())
        assertFalse(parse(searchResult(0, 20, 20, 10000)).isLastPage())
        assertFalse(parse(searchResult(40, 20, 20, 10000)).isLastPage())
    }

    @Test
    fun `unknown fields do not break parsing`() {
        // 服务端会返回 rating 之外的未知字段
        val json = searchResult(0, 20, 1, 1)
            .replace("\"thumbsUpCount\": 1200,", "\"thumbsUpCount\": 1200, \"someNewField\": {\"nested\": [1,2]},")
        assertEquals(1, parse(json).data.size)
    }

    @Test
    fun `a null collection is coerced instead of failing the whole page`() {
        // GLOBAL_JSON 的 coerceInputValues 必须生效，否则服务端偶尔回传 null 就整页失败
        val json = searchResult(0, 20, 1, 1)
            .replace("\"screenshots\": []", "\"screenshots\": null")
            .replace("\"latestFiles\": []", "\"latestFiles\": null")
        assertEquals(1, parse(json).data.size)
    }

    @Test
    fun `a null logo survives parsing`() {
        val json = searchResult(0, 20, 1, 1)
            .replace("\"logo\": {", "\"logo\": null, \"unusedLogo\": {")
        assertNull(parse(json).data.single().logo)
    }

    @Test
    fun `a project that is not approved is kept out of the rendered list`() {
        val json = searchResult(0, 20, 2, 2).replace("\"status\": 4,", "\"status\": 5,")
        val result = parse(json)
        assertEquals(1, result.data.size)
        assertFalse(result.data.first().isApproved())
        assertEquals(0, result.approvedData().size)
    }

    @Test
    fun `a missing pagination block is a parse failure and not an empty page`() {
        // 关键区分：真正没有结果时服务端仍然会返回 pagination，
        // 缺 pagination 说明响应不对，必须失败而不是当成"没有找到"
        val broken = """{"data": [$oneProject]}"""
        assertTrue(
            "a response without pagination must not parse as an empty page",
            runCatching { parse(broken) }.isFailure
        )
    }

    @Test
    fun `the server error body is not mistaken for a search result`() {
        // pageSize 越界时服务端返回的 400 响应体
        val errorBody = """
            {
              "errors": { "PageSize": ["The field PageSize must be between 1 and 50."] },
              "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
              "title": "One or more validation errors occurred.",
              "status": 400,
              "traceId": "0af1c2"
            }
        """.trimIndent()

        assertTrue(
            "an RFC7807 error body must fail parsing, not look like an empty result",
            runCatching { parse(errorBody) }.isFailure
        )
    }

    @Test
    fun `the json configuration used by the launcher tolerates server noise`() {
        assertTrue(GLOBAL_JSON.configuration.ignoreUnknownKeys)
        assertTrue(GLOBAL_JSON.configuration.coerceInputValues)
        // 与解析夹具期望的配置保持一致，防止测试与线上行为分叉
        assertEquals(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                coerceInputValues = true
            }.configuration,
            GLOBAL_JSON.configuration
        )
    }
}
