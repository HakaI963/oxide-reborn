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

import dev.oxide.launcher.game.account.auth_server.ELY_BY_AUTH_SERVER_URL
import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account types must stay distinguishable at every point where the launcher branches on them:
 * account ordering, the offline launch path, the authlib-injector path and the skin/cape UI.
 */
class AccountTypeDiscriminationTest {

    private val microsoft = Account(
        username = "Steve",
        profileId = "0000000000004000a000000000000000",
        accountType = AccountType.MICROSOFT.tag
    )

    private val local = Account(
        username = "Herobrine",
        profileId = "1111111111114000a000000000000000",
        accountType = AccountType.LOCAL.tag
    )

    private val elyBy = Account(
        username = "player",
        profileId = "2222222222224000a000000000000000",
        otherBaseUrl = ELY_BY_AUTH_SERVER_URL,
        otherAccount = "player@example.com",
        otherPassword = "hunter2",
        accountType = "Ely.by"
    )

    private val otherServer = Account(
        username = "player",
        profileId = "3333333333334000a000000000000000",
        otherBaseUrl = "https://auth.example.com/api/yggdrasil",
        otherAccount = "player@example.com",
        otherPassword = "hunter2",
        accountType = "Example"
    )

    @Test
    fun microsoftAccountsAreRecognised() {
        assertTrue(microsoft.isMicrosoftAccount())
        assertFalse(microsoft.isLocalAccount())
        assertFalse(microsoft.isAuthServerAccount())
        assertTrue(microsoft.isSkinChangeAllowed())
    }

    @Test
    fun offlineAccountsAreRecognised() {
        assertTrue(local.isLocalAccount())
        assertFalse(local.isMicrosoftAccount())
        assertFalse(local.isAuthServerAccount())
        assertTrue(local.isSkinChangeAllowed())
    }

    @Test
    fun thirdPartyAccountsAreRecognised() {
        assertTrue(otherServer.isAuthServerAccount())
        assertFalse(otherServer.isMicrosoftAccount())
        assertFalse(otherServer.isLocalAccount())
        // A generic authlib-injector server serves its own textures, so local skins stay locked.
        assertFalse(otherServer.isSkinChangeAllowed())
        assertFalse(otherServer.isElyByAccount())
    }

    @Test
    fun anOfflineAccountNeedsNoLogin() {
        assertTrue(local.isNoLoginRequired())
        assertTrue((null as Account?).isNoLoginRequired())
        assertFalse(microsoft.isNoLoginRequired())
        assertFalse(otherServer.isNoLoginRequired())
    }

    /**
     * tryGetFullServerUrl always returns a slash-terminated URL and that value is what gets stored
     * in otherBaseUrl, while the built-in Ely.by constant has no trailing slash. Upstream Plus
     * compares them with ==, which can therefore never be true for a server added through the UI,
     * leaving both the cape unlock and the local cape injection unreachable.
     */
    @Test
    fun elyByIsDetectedRegardlessOfTrailingSlash() {
        assertTrue(elyBy.isElyByAccount())
        assertTrue(elyBy.isSkinChangeAllowed())

        elyBy.otherBaseUrl = "$ELY_BY_AUTH_SERVER_URL/"
        assertTrue("trailing slash must not hide an Ely.by account", elyBy.isElyByAccount())

        elyBy.otherBaseUrl = "$ELY_BY_AUTH_SERVER_URL//"
        assertTrue(elyBy.isElyByAccount())
    }

    @Test
    fun aSimilarHostIsNotMistakenForElyBy() {
        val impostor = Account(
            otherBaseUrl = "https://authserver.ely.by.example.com/api/authlib-injector",
            accountType = "Impostor"
        )
        assertFalse(impostor.isElyByAccount())
        assertFalse(impostor.isSkinChangeAllowed())
    }

    @Test
    fun anAccountWithoutABaseUrlIsNotAnAuthServerAccount() {
        val noUrl = Account(accountType = "Something")
        assertFalse(noUrl.isAuthServerAccount())

        val legacyZeroUrl = Account(otherBaseUrl = "0", accountType = "Something")
        assertFalse(legacyZeroUrl.isAuthServerAccount())
    }

    /**
     * The account list is ordered Microsoft first, then auth-server, then local, and accounts with
     * no type last.
     */
    @Test
    fun accountOrderingIsStable() {
        assertEquals(0, microsoft.accountTypePriority())
        assertEquals(1, otherServer.accountTypePriority())
        assertEquals(1, elyBy.accountTypePriority())
        assertEquals(1, local.accountTypePriority())
        assertEquals(Int.MAX_VALUE, Account().accountTypePriority())
    }

    @Test
    fun everyAccountTypeIsDistinctFromTheOthers() {
        val all = listOf(microsoft, local, elyBy, otherServer)
        all.forEach { a ->
            assertEquals(
                "account ${a.username} matched more than one type",
                1,
                listOf(
                    a.isMicrosoftAccount(),
                    a.isLocalAccount(),
                    a.isAuthServerAccount()
                ).count { it }
            )
        }
    }

    /**
     * An offline account must never carry a Microsoft token: the launcher passes accessToken
     * straight into the game as auth_session, so a placeholder has to stay a placeholder.
     */
    @Test
    fun offlineAccountsCarryNoMicrosoftToken() {
        assertEquals("0", local.accessToken)
        assertEquals("0", local.refreshToken)
        assertEquals("0", local.clientToken)
        assertEquals(0L, local.expiresAt)
        assertNull(local.xUid)
        assertNull(local.otherBaseUrl)
    }

    @Test
    fun differentAccountsKeepTheirOwnIdentity() {
        assertNotEquals(microsoft.uniqueUUID, local.uniqueUUID)
        assertNotEquals(microsoft.profileId, local.profileId)
        assertEquals(microsoft, microsoft.copy())
        assertNotEquals<Account>(microsoft, microsoft.copy(username = "Alex"))
    }

    @Test
    fun skinModelTypeIsPartOfTheStoredIdentity() {
        assertEquals(SkinModelType.NONE, Account().skinModelType)
        assertEquals(SkinModelType.ALEX, Account(skinModelType = SkinModelType.ALEX).skinModelType)
    }
}