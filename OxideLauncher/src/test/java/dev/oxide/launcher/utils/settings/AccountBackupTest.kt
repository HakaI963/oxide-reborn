/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * Adapted from Zalith Launcher 2+ (https://github.com/Star1xr/ZalithLauncher2Plus),
 * Copyright (C) 2026 Star1xr <166748405+Star1xr@users.noreply.github.com>.
 * Original commit: 5cb5f327d480d41bcb45c40e1010459b78716061.
 * That repository is archived; the code is redistributed here under GPL-3.0.
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

package dev.oxide.launcher.utils.settings

import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountType
import dev.oxide.launcher.game.account.auth_server.data.AuthServer
import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.utils.settings.SettingsTransferUtils.decode
import dev.oxide.launcher.utils.settings.SettingsTransferUtils.encode
import dev.oxide.launcher.utils.settings.SettingsTransferUtils.toAccount
import dev.oxide.launcher.utils.settings.SettingsTransferUtils.toAuthServer
import dev.oxide.launcher.utils.settings.SettingsTransferUtils.toBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBackupTest {

    private val microsoft = Account(
        uniqueUUID = "aaaaaaaa-0000-0000-0000-000000000001",
        accessToken = "MICROSOFT_ACCESS_TOKEN",
        expiresAt = 1_700_000_000_000L,
        clientToken = "MICROSOFT_CLIENT_TOKEN",
        username = "Steve",
        profileId = "00000000-0000-4000-a000-000000000001",
        refreshToken = "MICROSOFT_REFRESH_TOKEN",
        xUid = "MICROSOFT_XUID",
        accountType = AccountType.MICROSOFT.tag,
        skinModelType = SkinModelType.ALEX
    )

    private val thirdParty = Account(
        uniqueUUID = "bbbbbbbb-0000-0000-0000-000000000002",
        accessToken = "YGGDRASIL_ACCESS_TOKEN",
        clientToken = "YGGDRASIL_CLIENT_TOKEN",
        username = "player",
        profileId = "00000000-0000-4000-a000-000000000002",
        otherBaseUrl = "https://auth.example.com/api/yggdrasil",
        otherAccount = "player@example.com",
        otherPassword = "PLAINTEXT_THIRD_PARTY_PASSWORD",
        accountType = "Example",
        skinModelType = SkinModelType.STEVE
    )

    private val export = SettingsExport(
        settings = mapOf("someFlag" to "true", "someNumber" to "42"),
        accounts = listOf(microsoft.toBackup(), thirdParty.toBackup()),
        authServers = listOf(
            AuthServer(
                baseUrl = "https://auth.example.com/api/yggdrasil",
                serverName = "Example",
                register = "https://example.com/register"
            ).toBackup()
        )
    )

    @Test
    fun accountsSurviveAJsonRoundTrip() {
        val restored = decode(encode(export))

        assertEquals(SettingsTransferUtils.FORMAT_VERSION, restored.formatVersion)
        assertEquals(export.settings, restored.settings)
        assertEquals(2, restored.accounts.size)
        assertEquals(export.accounts, restored.accounts)
        assertEquals(export.authServers, restored.authServers)
    }

    /**
     * Upstream Plus serialises the whole Account entity, which writes the renewable Microsoft
     * refresh token and the plaintext third-party password into a JSON file under shared storage.
     * Nothing here may put a credential on disk.
     */
    @Test
    fun noCredentialEverReachesTheBackup() {
        val text = encode(export)

        for (secret in listOf(
            "MICROSOFT_ACCESS_TOKEN",
            "MICROSOFT_REFRESH_TOKEN",
            "MICROSOFT_CLIENT_TOKEN",
            "MICROSOFT_XUID",
            "YGGDRASIL_ACCESS_TOKEN",
            "YGGDRASIL_CLIENT_TOKEN",
            "PLAINTEXT_THIRD_PARTY_PASSWORD"
        )) {
            assertFalse("backup leaked $secret", text.contains(secret))
        }

        for (field in listOf("accessToken", "refreshToken", "clientToken", "otherPassword", "xUid")) {
            assertFalse("backup contains the field $field", text.contains("\"$field\""))
        }
    }

    /**
     * The real protection is structural: AccountBackup simply has no credential property, so no
     * call site can leak one by passing the wrong argument. A reflection check keeps that true if
     * somebody later adds a field.
     */
    @Test
    fun theBackupSchemaHasNoCredentialProperty() {
        val credentialNames = setOf(
            "accessToken", "refreshToken", "clientToken", "expiresAt", "xUid",
            "otherPassword", "password"
        )
        val declared = AccountBackup::class.java.declaredFields.map { it.name }.toSet()
        for (name in credentialNames) {
            assertFalse("AccountBackup must not declare $name", name in declared)
        }
    }

    @Test
    fun aRestoredAccountKeepsItsIdentityButNotItsSession() {
        val restored = microsoft.toBackup().toAccount()

        assertEquals(microsoft.uniqueUUID, restored.uniqueUUID)
        assertEquals(microsoft.username, restored.username)
        assertEquals(microsoft.profileId, restored.profileId)
        assertEquals(microsoft.accountType, restored.accountType)
        assertEquals(microsoft.skinModelType, restored.skinModelType)

        assertEquals("0", restored.accessToken)
        assertEquals("0", restored.refreshToken)
        assertEquals("0", restored.clientToken)
        assertEquals(0L, restored.expiresAt)
        assertNull(restored.xUid)
    }

    @Test
    fun aRestoredThirdPartyAccountHasToSignInAgain() {
        val restored = thirdParty.toBackup().toAccount()

        assertEquals("https://auth.example.com/api/yggdrasil", restored.otherBaseUrl)
        assertEquals("player@example.com", restored.otherAccount)
        assertEquals("Example", restored.accountType)
        // the password is gone, so a rejected token has to prompt for it again
        assertNull(restored.otherPassword)
        assertEquals("0", restored.accessToken)
    }

    @Test
    fun anUnknownSkinModelFallsBackInsteadOfCrashing() {
        val broken = microsoft.copy().toBackup().copy(skinModelType = "NOT_A_MODEL")
        assertEquals(SkinModelType.NONE, broken.toAccount().skinModelType)
    }

    @Test
    fun unknownKeysFromNewerBackupsAreIgnored() {
        val future = """
            {
              "formatVersion": 99,
              "settings": { "a": "1" },
              "accounts": [],
              "authServers": [],
              "somethingFromTheFuture": { "nested": true }
            }
        """.trimIndent()
        val restored = decode(future)
        assertEquals(99, restored.formatVersion)
        assertEquals(mapOf("a" to "1"), restored.settings)
    }

    @Test
    fun malformedBackupsAreRejected() {
        assertTrue(runCatching { decode("not json at all") }.isFailure)
        assertTrue(runCatching { decode("""{"accounts": "not a list"}""") }.isFailure)
    }

    @Test
    fun authServersRoundTrip() {
        val server = AuthServer(
            baseUrl = "https://auth.example.com/api/yggdrasil",
            serverName = "Example",
            register = null
        )
        assertEquals(server, server.toBackup().toAuthServer())
    }
}