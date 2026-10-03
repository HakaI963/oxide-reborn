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

package dev.oxide.launcher.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鉴权头的作用域
 *
 * 密钥是随应用分发的客户端标识，只能出现在发往 CurseForge 的请求上。
 * 这些用例把"必须带"和"绝不能带"两边都钉住。
 */
class CurseForgeAuthTest {

    private val key = "test-client-identifier"

    @Test
    fun `api host gets the key`() {
        assertEquals(
            listOf(CURSEFORGE_API_KEY_HEADER to key),
            curseForgeAuthHeaders(HOST_CURSEFORGE_API, key)
        )
    }

    @Test
    fun `cdn hosts get the key so direct file downloads are authenticated`() {
        listOf(
            "edge.forgecdn.net",
            "mediafilez.forgecdn.net",
            "media.forgecdn.net"
        ).forEach { host ->
            assertEquals(
                "expected $host to be treated as a CurseForge host",
                listOf(CURSEFORGE_API_KEY_HEADER to key),
                curseForgeAuthHeaders(host, key)
            )
        }
    }

    @Test
    fun `the key never leaks to other hosts`() {
        listOf(
            "api.modrinth.com",
            "cdn.modrinth.com",
            "mod.mcimirror.top",
            "api.github.com",
            "piston-meta.mojang.com",
            "localhost",
            // 结尾相同但不是子域，必须拒绝
            "notforgecdn.net",
            "evil-forgecdn.net",
            // 前缀相同但主机名不同，必须拒绝
            "api.curseforge.com.evil.example",
            "api.curseforge.com.attacker.net"
        ).forEach { host ->
            assertFalse(
                "expected $host to NOT be treated as a CurseForge host",
                isCurseForgeHost(host)
            )
            assertEquals(
                "expected no auth header for $host",
                emptyList<Pair<String, String>>(),
                curseForgeAuthHeaders(host, key)
            )
        }
    }

    @Test
    fun `an empty key produces no header`() {
        listOf("", "   ").forEach { blank ->
            assertEquals(
                emptyList<Pair<String, String>>(),
                curseForgeAuthHeaders(HOST_CURSEFORGE_API, blank)
            )
        }
    }

    @Test
    fun `the key value is passed through unchanged`() {
        // 真实密钥含 $ 与 / 之类的字符，必须原样送达，不能被转义或截断
        val awkward = "\$2a\$10\$abc/def+ghi="
        val headers = curseForgeAuthHeaders(HOST_CURSEFORGE_API, awkward)
        assertEquals(CURSEFORGE_API_KEY_HEADER, headers.single().first)
        assertEquals(awkward, headers.single().second)
    }

    @Test
    fun `the header name is the one the api requires`() {
        assertEquals("x-api-key", CURSEFORGE_API_KEY_HEADER)
    }

    @Test
    fun `host matching is case sensitive on purpose`() {
        // OkHttp/Ktor 都已经把 host 规范化为小写，这里只确认函数不会自行放宽
        assertTrue(isCurseForgeHost("api.curseforge.com"))
        assertFalse(isCurseForgeHost("API.CURSEFORGE.COM"))
    }
}
