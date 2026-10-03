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

package dev.oxide.launcher.game.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account is a data class, so its generated toString() would print the Microsoft access token, the
 * renewable refresh token and the plaintext third-party password. It is overridden so that any
 * future log, toast or crash report that interpolates an account stays safe.
 */
class AccountToStringTest {

    private val account = Account(
        uniqueUUID = "cccccccc-0000-0000-0000-000000000003",
        accessToken = "ACCESS_TOKEN_VALUE",
        expiresAt = 1_700_000_000_000L,
        clientToken = "CLIENT_TOKEN_VALUE",
        username = "Steve",
        profileId = "00000000-0000-4000-a000-000000000003",
        refreshToken = "REFRESH_TOKEN_VALUE",
        xUid = "XUID_VALUE",
        otherBaseUrl = "https://auth.example.com/api/yggdrasil",
        otherAccount = "player@example.com",
        otherPassword = "PASSWORD_VALUE",
        accountType = AccountType.MICROSOFT.tag
    )

    @Test
    fun credentialsNeverAppearInToString() {
        val text = account.toString()

        for (secret in listOf(
            "ACCESS_TOKEN_VALUE",
            "REFRESH_TOKEN_VALUE",
            "CLIENT_TOKEN_VALUE",
            "XUID_VALUE",
            "PASSWORD_VALUE"
        )) {
            assertFalse("toString() leaked $secret", text.contains(secret))
        }
    }

    @Test
    fun identityIsStillUsefulForDiagnostics() {
        val text = account.toString()
        assertTrue(text.contains("Steve"))
        assertTrue(text.contains(account.uniqueUUID))
        assertTrue(text.contains(AccountType.MICROSOFT.tag))
    }

    @Test
    fun templateRenderingIsSafe() {
        assertFalse("Account in a string template leaked a credential", "$account".contains("PASSWORD_VALUE"))
    }

    /**
     * Overriding toString() must not change value equality: Room compares columns, and the
     * account list relies on the generated equals()/hashCode().
     */
    @Test
    fun valueSemanticsAreUnchanged() {
        val same = account.copy()
        assertEquals(account, same)
        assertEquals(account.hashCode(), same.hashCode())

        val renamed = account.copy(username = "Alex")
        assertNotEquals(account as Any, renamed as Any)
    }
}