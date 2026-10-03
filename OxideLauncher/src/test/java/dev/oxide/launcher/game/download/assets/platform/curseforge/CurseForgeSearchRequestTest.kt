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

import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchFilter
import dev.oxide.launcher.game.download.assets.platform.PlatformSortField
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModCategory
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索请求的 URL 与查询参数构造
 *
 * 这一层出错时线上表现为"筛选不起作用"或者一个语义不明的 400，
 * 所以离线把每个参数的拼法钉死。
 */
class CurseForgeSearchRequestTest {

    private fun params(request: CurseForgeSearchRequest): Parameters = request.toParameters()

    @Test
    fun `endpoints keep the v1 prefix`() {
        assertEquals("https://api.curseforge.com/v1/mods/search", CurseForgeEndpoints.search(CURSEFORGE_API))
        assertEquals("https://api.curseforge.com/v1/mods/394468", CurseForgeEndpoints.project(CURSEFORGE_API, "394468"))
        assertEquals(
            "https://api.curseforge.com/v1/mods/394468/files",
            CurseForgeEndpoints.projectFiles(CURSEFORGE_API, "394468")
        )
        assertEquals(
            "https://api.curseforge.com/v1/mods/394468/files/8888038",
            CurseForgeEndpoints.projectFile(CURSEFORGE_API, "394468", "8888038")
        )
        assertEquals("https://api.curseforge.com/v1/fingerprints", CurseForgeEndpoints.fingerprints(CURSEFORGE_API))
    }

    @Test
    fun `the mirror base is built the same way as the official one`() {
        // 镜像源前缀不同，最容易在这里漏掉 /curseforge/v1 或者多拼出一个斜杠
        assertEquals(
            "https://mod.mcimirror.top/curseforge/v1/mods/search",
            CurseForgeEndpoints.search(MCIM_CURSEFORGE_API)
        )
        assertEquals(
            "https://mod.mcimirror.top/curseforge/v1/fingerprints",
            CurseForgeEndpoints.fingerprints(MCIM_CURSEFORGE_API)
        )
        // 结尾多一个斜杠也不能拼出 //
        assertEquals(
            "https://api.curseforge.com/v1/mods/search",
            CurseForgeEndpoints.search("$CURSEFORGE_API/")
        )
    }

    @Test
    fun `game id and class id come from the requested classes`() {
        val request = CurseForgeSearchRequest(classId = PlatformClasses.MOD_PACK.curseforge.classID)
        assertEquals("432", params(request)["gameId"])
        assertEquals("4471", params(request)["classId"])
    }

    @Test
    fun `the query is sent as searchFilter and pagination as index and pageSize`() {
        val p = params(CurseForgeSearchRequest(searchFilter = "sodium", index = 40, pageSize = 20))
        assertEquals("sodium", p["searchFilter"])
        assertEquals("40", p["index"])
        assertEquals("20", p["pageSize"])
    }

    @Test
    fun `an empty query is not sent as searchFilter`() {
        // 空值会被服务端当成一次"按空串过滤"，与不传该参数不是一回事
        assertNull(params(CurseForgeSearchRequest(searchFilter = ""))["searchFilter"])
        assertNull(params(CurseForgeSearchRequest(searchFilter = "   "))["searchFilter"])
        assertNull(params(CurseForgeSearchRequest(searchFilter = null))["searchFilter"])
    }

    @Test
    fun `an out of range page size is clamped rather than sent as is`() {
        assertEquals("50", params(CurseForgeSearchRequest(pageSize = 100))["pageSize"])
        assertEquals(50, CurseForgeSearchRequest(pageSize = 100).effectivePageSize)
    }

    @Test
    fun `an out of range index is clamped rather than sent as is`() {
        assertEquals("9980", params(CurseForgeSearchRequest(index = 9999, pageSize = 20))["index"])
        assertEquals("0", params(CurseForgeSearchRequest(index = -5, pageSize = 20))["index"])
    }

    @Test
    fun `a single category uses categoryId`() {
        val p = params(CurseForgeSearchRequest(categories = setOf(CurseForgeModCategory.WORLDGEN)))
        assertEquals("406", p["categoryId"])
        assertNull(p["categoryIds"])
    }

    @Test
    fun `several categories use the bracketed categoryIds form`() {
        val p = params(
            CurseForgeSearchRequest(
                categories = setOf(CurseForgeModCategory.WORLDGEN, CurseForgeModCategory.BIOMES)
            )
        )
        assertEquals("[406,407]", p["categoryIds"])
        assertNull(p["categoryId"])
    }

    @Test
    fun `no category means neither category parameter is sent`() {
        val p = params(CurseForgeSearchRequest(categories = null))
        assertNull(p["categoryId"])
        assertNull(p["categoryIds"])
    }

    @Test
    fun `mod loader and game version are only sent when set`() {
        val bare = params(CurseForgeSearchRequest(modLoader = null, gameVersion = null))
        assertNull(bare["modLoaderType"])
        assertNull(bare["gameVersion"])

        val filtered = params(
            CurseForgeSearchRequest(modLoader = CurseForgeModLoader.FABRIC, gameVersion = "1.20.1")
        )
        assertEquals("4", filtered["modLoaderType"])
        assertEquals("1.20.1", filtered["gameVersion"])
    }

    @Test
    fun `sorting is always explicit so the server never falls back to its own default`() {
        val p = params(CurseForgeSearchRequest())
        assertEquals(PlatformSortField.RELEVANCE.curseforge, p["sortField"])
        assertEquals("desc", p["sortOrder"])

        listOf(
            PlatformSortField.RELEVANCE,
            PlatformSortField.DOWNLOADS,
            PlatformSortField.POPULARITY,
            PlatformSortField.NEWEST,
            PlatformSortField.UPDATED
        ).forEach { sortField ->
            assertEquals(sortField.curseforge, params(CurseForgeSearchRequest(sortField = sortField))["sortField"])
        }
    }

    @Test
    fun `the first page is index 0 and not page 1`() {
        // index 的语义是"跳过多少条"，第 1 页必须是 0
        val first = PlatformSearchFilter().toCurseForgeRequest("", PlatformClasses.MOD)
        assertEquals("0", params(first)["index"])
        assertEquals("20", params(first)["pageSize"])
    }

    @Test
    fun `the ui filter is mapped onto the request without losing pagination`() {
        val filter = PlatformSearchFilter(
            searchName = "sodium",
            gameVersion = "1.20.1",
            sortField = PlatformSortField.DOWNLOADS,
            categories = listOf(CurseForgeModCategory.PERFORMANCE),
            modloader = CurseForgeModLoader.NEOFORGE,
            index = 40,
            limit = 20
        )
        val p = params(filter.toCurseForgeRequest(filter.searchName, PlatformClasses.MOD))

        assertEquals("sodium", p["searchFilter"])
        assertEquals("1.20.1", p["gameVersion"])
        assertEquals(PlatformSortField.DOWNLOADS.curseforge, p["sortField"])
        assertEquals("6814", p["categoryId"])
        assertEquals("6", p["modLoaderType"])
        assertEquals("40", p["index"])
        assertEquals("20", p["pageSize"])
    }

    @Test
    fun `a blank game version from the ui is not forwarded`() {
        val filter = PlatformSearchFilter(gameVersion = "   ")
        assertNull(params(filter.toCurseForgeRequest("", PlatformClasses.MOD))["gameVersion"])
    }

    @Test
    fun `every request built from a filter stays inside the server contract`() {
        listOf(0, 20, 40, 5000, 9980, -1, 99999).forEach { index ->
            listOf(1, 20, 50, 100, 0).forEach { limit ->
                val p = params(
                    PlatformSearchFilter(index = index, limit = limit)
                        .toCurseForgeRequest("x", PlatformClasses.MOD)
                )
                val size = p["pageSize"]!!.toInt()
                val start = p["index"]!!.toInt()

                assertTrue("pageSize $size out of range", size in 1..50)
                assertTrue("index $start out of range", start >= 0)
                assertTrue(
                    "index $start + pageSize $size exceeds 10000",
                    start.toLong() + size <= CurseForgePaging.MAX_INDEX
                )
            }
        }
    }

    @Test
    fun `the query string survives ktor url encoding`() {
        val request = CurseForgeSearchRequest(
            searchFilter = "just enough items",
            categories = setOf(CurseForgeModCategory.WORLDGEN, CurseForgeModCategory.BIOMES)
        )

        // Parameters.toString() does not encode; Ktor encodes when the request URL is built.
        // Build it the same way CurseForgeSearcher does so this test covers the real path.
        val builder = URLBuilder(CurseForgeEndpoints.search(CURSEFORGE_API))
        params(request).forEach { name, values -> builder.parameters.appendAll(name, values) }
        val url = builder.buildString()

        assertFalse("a raw space would truncate the value server side", url.contains(" "))

        // Ktor may emit "+" or "%20" for a space; both decode back to the original.
        val value = url.substringAfter('?').split('&')
            .first { it.startsWith("searchFilter=") }
            .removePrefix("searchFilter=")
        assertEquals("just enough items", URLDecoder.decode(value, Charsets.UTF_8.name()))
    }
}
