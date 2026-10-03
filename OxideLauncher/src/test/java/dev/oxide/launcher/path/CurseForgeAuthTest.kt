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
import java.io.File

/**
 * gradle.properties 里声明的 curseforge_api_key：release 构建回落到的那一份来源。
 * 文件不存在或这一行缺失都会让测试失败，而不是悄悄放过去。
 */
private val DECLARED_KEY = Regex("^\\s*curseforge_api_key\\s*[=:](.*)$")

/**
 * 读取仓库里声明的 curseforge_api_key。
 *
 * 单元测试的工作目录是模块目录，所以 gradle.properties 在当前目录或它的上一级。
 * 注意 getKeyFromLocal() 会 trim，这里保持一致：带空白的密钥不可能是有效请求头。
 */
private fun declaredCurseForgeApiKey(): String {
    // Assert.fail 返回 Unit 而不是 Nothing，所以这里显式抛 AssertionError
    val properties = sequenceOf(File("gradle.properties"), File("../gradle.properties"))
        .firstOrNull { it.isFile }
        ?: throw AssertionError(
            "could not find gradle.properties relative to ${File(".").absolutePath}; " +
                "the release build resolves curseforge_api_key from there"
        )
    val line = properties.readLines().map { it.trim() }.firstOrNull { DECLARED_KEY.matches(it) }
    return line?.let { DECLARED_KEY.matchEntire(it)!!.groupValues[1].trim() }.orEmpty()
}

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

    /**
     * `curseforge_api_key` 是 release 构建回落到的那个来源。
     *
     * 它被清空或整行删掉时，release 会安静地打出一个不带 x-api-key 的包——因为未配置的 CI
     * secret 会变成空字符串，而不是"未设置"——而 debug 看上去完全正常（ci.yml 从不设置
     * CURSEFORGE_API_KEY，所以 debug 一路回落到这里并且拿到了值）。把这一行钉住，
     * 源头被改动时立刻失败，而不是等到用户装上 APK 才发现 CurseForge 用不了。
     */
    @Test
    fun `the committed gradle property resolves to a usable key`() {
        val declared = declaredCurseForgeApiKey()
        assertTrue(
            "curseforge_api_key must be declared and non-blank in gradle.properties",
            declared.isNotBlank()
        )
        // 头值里不能有空白，否则 OkHttp/Ktor 要么拒绝要么截断这个头
        assertTrue(
            "curseforge_api_key must not contain whitespace",
            declared.none { it.isWhitespace() }
        )
        // 走 release 真正会走的那条路径：拿到值 → 构造鉴权头
        val headers = curseForgeAuthHeaders(HOST_CURSEFORGE_API, declared)
        assertEquals(1, headers.size)
        assertEquals(CURSEFORGE_API_KEY_HEADER, headers.single().first)
        // 用 assertTrue 而不是 assertEquals，避免失败时把密钥写进测试报告
        assertTrue(
            "the key must be sent through unchanged",
            declared == headers.single().second
        )
        // 拿到真密钥也不能因此被发往第三方主机
        assertEquals(
            emptyList<Pair<String, String>>(),
            curseForgeAuthHeaders("api.modrinth.com", declared)
        )
    }
}
