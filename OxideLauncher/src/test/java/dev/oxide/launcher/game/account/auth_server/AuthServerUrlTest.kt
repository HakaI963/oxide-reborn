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

package dev.oxide.launcher.game.account.auth_server

import dev.oxide.launcher.game.account.auth_server.data.AuthServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An authlib-injector account reaches the game through
 * `-javaagent:authlib-injector.jar=<otherBaseUrl>`, so the stored URL has to be exactly the one the
 * server publishes. Ely.by detection compares it against a built-in constant, which makes trailing
 * slash handling part of the contract rather than cosmetics.
 */
class AuthServerUrlTest {

    @Test
    fun theTrailingSlashIsStrippedFromTheApiBaseUrl() {
        assertEquals(
            "https://auth.example.com/api/yggdrasil",
            AuthServerApi("https://auth.example.com/api/yggdrasil/").formatUrl(
                "https://auth.example.com/api/yggdrasil/"
            )
        )
        assertEquals(
            "https://auth.example.com/api/yggdrasil",
            AuthServerApi("https://auth.example.com/api/yggdrasil").formatUrl(
                "https://auth.example.com/api/yggdrasil"
            )
        )
    }

    @Test
    fun theStoredUrlKeepsTheServerPath() {
        // The path is significant: dropping it would point the agent at the wrong Yggdrasil root.
        assertEquals(
            ELY_BY_AUTH_SERVER_URL,
            AuthServerApi(ELY_BY_AUTH_SERVER_URL).formatUrl(ELY_BY_AUTH_SERVER_URL)
        )
    }

    @Test
    fun anAuthServerIsIdentifiedByItsBaseUrl() {
        val server = AuthServer(
            baseUrl = "https://auth.example.com/api/yggdrasil",
            serverName = "Example",
            register = "https://example.com/register"
        )
        assertEquals("Example", server.serverName)
        assertEquals("https://example.com/register", server.register)
        // the name is only a label; the URL is the primary key
        assertEquals("https://auth.example.com/api/yggdrasil", server.baseUrl)
    }

    @Test
    fun twoServersWithTheSameNameAreStillDistinct() {
        val a = AuthServer("https://a.example.com/api", "Same Name")
        val b = AuthServer("https://b.example.com/api", "Same Name")
        assertEquals(a.serverName, b.serverName)
        assertNotEqualsAuthServer(a, b)
    }

    private fun assertNotEqualsAuthServer(a: AuthServer, b: AuthServer) {
        assertFalse(a == b)
    }

    @Test
    fun theBuiltInElyByEndpointIsTheDocumentedOne() {
        assertEquals("https://authserver.ely.by/api/authlib-injector", ELY_BY_AUTH_SERVER_URL)
        assertTrue(ELY_BY_AUTH_SERVER_URL.startsWith("https://"))
    }
}