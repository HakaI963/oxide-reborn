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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.path

import dev.oxide.launcher.BuildKeys
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** 记录所有收到的请求，并统一回一个空的 200 */
private class RecordingDispatcher : Dispatcher() {
    private val recorded = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())

    val requests: List<RecordedRequest> get() = synchronized(recorded) { recorded.toList() }

    override fun dispatch(request: RecordedRequest): MockResponse {
        synchronized(recorded) { recorded.add(request) }
        return MockResponse.Builder().code(200).build()
    }
}

/** 把任意域名都解析到回环地址，让请求带着真实的 Host 头发出去却不真的出网 */
private object LoopbackDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getLoopbackAddress())
}

/**
 * 鉴权头是否真的被发出去。
 *
 * [CurseForgeAuthTest] 断言的是纯函数，这里断言的是接上真实网络栈之后的结果：
 * [createOkHttpClientBuilder] 装的那个拦截器必须用**打包进这个变体的那份**
 * [BuildKeys.CURSEFORGE_API] 给 CurseForge 主机补上 x-api-key，且绝不给别的主机补。
 *
 * 这条链路正是 release 里断掉的那一环：密钥一旦解析为空，
 * `curseForgeAuthHeaders` 会返回空列表（刻意如此，避免让调用方以为已经带上了），
 * 于是请求不带 x-api-key，CurseForge 一律回 403。
 */
class CurseForgeHeaderInjectionTest {

    private val dispatcher = RecordingDispatcher()

    private val server = MockWebServer().also {
        it.dispatcher = dispatcher
        it.start()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    // action 是普通参数而不是带接收者的 lambda，所以这里必须拿到那个 Builder 实例
    private fun client(): OkHttpClient = createOkHttpClientBuilder { builder ->
        // createOkHttpClientBuilder 先装 ResilientDns，这里再换成回环解析
        builder.dns(LoopbackDns)
    }.build()

    private fun get(host: String, path: String) {
        val url = "http://$host:${server.port}$path"
        client().newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.body?.close()
        }
    }

    /**
     * 本次回归的直接护栏：打包进变体的密钥必须非空。
     *
     * 构建期还有一个对 release 生效的对应断言（:verifyCurseForgeApiKey），
     * 两者分别覆盖被编译进去的值和运行时的取值。
     */
    @Test
    fun `the key compiled into this variant is not blank`() {
        assertTrue(
            "BuildKeys.CURSEFORGE_API must not be blank: a blank key means no x-api-key header, " +
                "so CurseForge answers 403 for every request",
            BuildKeys.CURSEFORGE_API.isNotBlank()
        )
    }

    @Test
    fun `the api host receives the key`() {
        get(HOST_CURSEFORGE_API, "/v1/mods/search")
        val sent = dispatcher.requests.last().headers[CURSEFORGE_API_KEY_HEADER]
        // 用 assertTrue 而不是 assertEquals，避免失败时把密钥写进测试报告
        assertTrue(
            "expected x-api-key to be sent to $HOST_CURSEFORGE_API",
            sent != null && sent == BuildKeys.CURSEFORGE_API
        )
    }

    @Test
    fun `the cdn hosts receive the key so direct file downloads authenticate`() {
        listOf("edge.forgecdn.net", "mediafilez.forgecdn.net", "media.forgecdn.net").forEach { host ->
            get(host, "/mod/file")
            assertNotNull(
                "expected x-api-key to be sent to $host",
                dispatcher.requests.last().headers[CURSEFORGE_API_KEY_HEADER]
            )
        }
    }

    @Test
    fun `no other host ever receives the key`() {
        listOf(
            "api.modrinth.com",
            "cdn.modrinth.com",
            "mod.mcimirror.top",
            "api.github.com",
            "piston-meta.mojang.com"
        ).forEach { host ->
            get(host, "/anything")
            assertNull(
                "expected no x-api-key for $host",
                dispatcher.requests.last().headers[CURSEFORGE_API_KEY_HEADER]
            )
        }
    }
}