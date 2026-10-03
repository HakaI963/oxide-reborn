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

import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.game.account.wardrobe.getLocalUUIDWithSkinModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * An offline account has no Microsoft identity at all, so its UUID has to be derived locally and
 * must stay stable across launcher restarts, otherwise the game would see a different player after
 * every launch.
 */
class OfflineAccountTest {

    @Test
    fun theDerivedUuidIsStable() {
        val first = getLocalUUIDWithSkinModel("Herobrine", SkinModelType.NONE)
        val second = getLocalUUIDWithSkinModel("Herobrine", SkinModelType.NONE)
        assertEquals(first, second)
    }

    @Test
    fun differentNamesGetDifferentUuids() {
        assertNotEquals(
            getLocalUUIDWithSkinModel("Herobrine", SkinModelType.NONE),
            getLocalUUIDWithSkinModel("Herobren", SkinModelType.NONE)
        )
        assertNotEquals(
            getLocalUUIDWithSkinModel("Steve", SkinModelType.NONE),
            getLocalUUIDWithSkinModel("steve", SkinModelType.NONE)
        )
    }

    @Test
    fun theDerivedUuidIsAWellFormed32CharacterHexString() {
        val uuid = getLocalUUIDWithSkinModel("Notch", SkinModelType.NONE)
        // only NONE returns the lowercase base id; the arm models use an uppercase suffix
        assertEquals(32, uuid.length)
        assertTrue(uuid.all { it.isDigit() || it in 'a'..'f' })
        // version 3 marker, so it parses back as a UUID once the dashes are removed
        assertEquals('3', uuid[12])
        assertEquals('9', uuid[16])
        assertEquals(uuid, accountUUID(accountUUID(uuid)).replace("-", ""))
    }

    /**
     * The default account profile id is derived from the username, which is what makes an offline
     * account persist: the id has to be recomputed identically on the next launch.
     */
    @Test
    fun theDefaultProfileIdFollowsTheUsername() {
        assertEquals(
            getLocalUUIDWithSkinModel("Steve", SkinModelType.NONE),
            Account().profileId
        )
        assertEquals(
            getLocalUUIDWithSkinModel("Alex", SkinModelType.NONE),
            Account(username = "Alex").profileId
        )
    }

    /**
     * A slim-model offline player needs a different id, otherwise a player who switches arm model
     * would collide with their own classic-model profile.
     */
    @Test
    fun armModelChangesTheProfileId() {
        val none = getLocalUUIDWithSkinModel("Steve", SkinModelType.NONE)
        val steve = getLocalUUIDWithSkinModel("Steve", SkinModelType.STEVE)
        val alex = getLocalUUIDWithSkinModel("Steve", SkinModelType.ALEX)

        // NONE keeps the base id; the two arm models differ only in the parity-corrected suffix
        assertEquals(32, none.length)
        assertEquals(32, steve.length)
        assertEquals(none.take(27), steve.take(27))
        assertEquals(none.take(27), alex.take(27))
        assertNotEquals("a classic and a slim player must not share a profile", steve, alex)
        // the suffix is chosen so that xor-ing the four parity nibbles matches the target
        assertEquals(0, steve.nibbleParity())
        assertEquals(1, alex.nibbleParity())
    }

    private fun String.nibbleParity(): Int {
        val a = this[7].digitToInt(16)
        val b = this[15].digitToInt(16)
        val c = this[23].digitToInt(16)
        val d = this.substring(27).toLong(16).toInt() and 0xF
        return (a xor b xor c xor d) % 2
    }

    /**
     * The username field must keep the values Minecraft accepts: 3-16 characters of [A-Za-z0-9_].
     */
    @Test
    fun everyValidUsernameProducesAUsableId() {
        val valid = listOf(
            "abc", "Steve", "alex", "player_1", "A" + "b".repeat(14) + "c",
            "___", "123", "a_b_c_d_e_f_g_h_i"
        )
        for (name in valid) {
            val uuid = getLocalUUIDWithSkinModel(name, SkinModelType.NONE)
            assertEquals("bad uuid length for $name", 32, uuid.length)
        }
    }

    @Test
    fun theSuggestedOfflineUuidUsesTheVanillaScheme() {
        val uuid = getUUIDFromUserName("Herobrine")
        assertEquals(
            UUID.nameUUIDFromBytes("OfflinePlayer:Herobrine".toByteArray(Charsets.UTF_8)),
            uuid
        )
        // the suggested id is a dashed UUID, unlike the derived one
        assertEquals(36, uuid.toString().length)
        assertTrue(uuid.toString().contains('-'))
    }

    @Test
    fun dashlessAndDashedFormsDescribeTheSameAccount() {
        val dashed = "00000000-0000-4000-a000-000000000001"
        // accountUUID(String) parses a bare 32-character id, accountUUID(UUID) renders it back
        assertEquals(dashed, accountUUID(accountUUID(dashed.replace("-", ""))))
        assertEquals(dashed.replace("-", ""), accountUUID(accountUUID(dashed.replace("-", ""))))
    }
}