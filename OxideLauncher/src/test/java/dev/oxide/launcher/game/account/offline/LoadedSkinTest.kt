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

package dev.oxide.launcher.game.account.offline

import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline Yggdrasil server serves both skins and capes over GET /textures/{hash}. Its
 * Character carries capeHash/capeBytes, which used to be declared but never populated, so a
 * local account with a cape never showed one in game.
 */
class LoadedSkinTest {

    private val skin = byteArrayOf(1, 2, 3)
    private val cape = byteArrayOf(4, 5, 6)

    @Test
    fun aSkinOnlyCharacterHasNoCape() {
        val loaded = LoadedSkin(skinHash = "skin-hash", skinBytes = skin)

        assertEquals("skin-hash", loaded.skinHash)
        assertNull(loaded.capeHash)
        assertNull(loaded.capeBytes)
    }

    @Test
    fun theCapeTravelsWithTheSkin() {
        val loaded = LoadedSkin(
            skinHash = "skin-hash",
            skinBytes = skin,
            capeHash = "cape-hash",
            capeBytes = cape,
            model = SkinModelType.ALEX
        )

        assertEquals("cape-hash", loaded.capeHash)
        assertTrue(loaded.capeBytes!!.contentEquals(cape))
        assertEquals(SkinModelType.ALEX, loaded.model)
    }

    @Test
    fun aCapeOnlyCharacterIsRepresentable() {
        // d98a271d: the offline server is also started for an account that has only a cape
        val loaded = LoadedSkin(capeHash = "cape-hash", capeBytes = cape)

        assertNull(loaded.skinHash)
        assertNull(loaded.skinBytes)
        assertEquals("cape-hash", loaded.capeHash)
    }

    /**
     * Byte arrays need contentEquals/contentHashCode, otherwise two characters with different cape
     * bytes would compare equal and the server would serve the wrong texture.
     */
    @Test
    fun equalityComparesTextureBytesByContent() {
        val a = LoadedSkin("h1", byteArrayOf(1, 2), "h2", byteArrayOf(3, 4))
        val b = LoadedSkin("h1", byteArrayOf(1, 2), "h2", byteArrayOf(3, 4))
        val different = LoadedSkin("h1", byteArrayOf(1, 2), "h2", byteArrayOf(3, 5))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, different)
    }

    @Test
    fun equalityRejectsADifferentModel() {
        assertNotEquals(
            LoadedSkin("h", skin, null, null, SkinModelType.STEVE),
            LoadedSkin("h", skin, null, null, SkinModelType.ALEX)
        )
    }

    @Test
    fun theDefaultModelIsClassic() {
        assertEquals(SkinModelType.NONE, LoadedSkin().model)
    }
}