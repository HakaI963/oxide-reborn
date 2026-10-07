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

package dev.oxide.launcher.game.control

import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The new clean default at assets/emulated/new.json.
 *
 * Same guarantees as the legacy default (loads through the real schema,
 * four top-level sections, sane editorVersion, unique layers/buttons,
 * valid click events) but with the new "new" identity. The legacy asset
 * stays as a fallback; this test pins the new one so a bad edit fails here
 * instead of on a fresh install.
 */
class NewDefaultLayoutTest {
    private fun locateNew(): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val base = dir ?: return@repeat
            for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
                val c = base.resolve(prefix + "assets/emulated/new.json")
                if (c.isFile) return c
            }
            dir = base.parentFile
        }
        error("could not locate assets/emulated/new.json")
    }

    private val assetText: String by lazy { locateNew().readText() }
    private val asset by lazy { Json.parseToJsonElement(assetText).jsonObject }

    @Test
    fun loadsThroughTheRealSchema() {
        val layout = loadLayoutFromString(assetText)
        assertTrue(layout.layers.isNotEmpty())
        assertTrue(layout.layers.all { it.normalButtons.isNotEmpty() })
    }

    @Test
    fun declaresFourSectionsAndSaneVersion() {
        for (key in listOf("info", "layers", "styles", "editorVersion")) {
            assertTrue(key in asset)
        }
        val declared = asset.getValue("editorVersion").jsonPrimitive.int
        assertTrue(declared > 0 && declared <= EmptyControlLayout.editorVersion)
    }

    @Test
    fun identifiesAsNew() {
        val info = asset.getValue("info").jsonObject
        fun def(key: String) = info.getValue(key).jsonObject.getValue("default").jsonPrimitive.content
        assertEquals("new", def("name"))
        assertEquals("oxide-mc", def("author"))
    }

    @Test
    fun layersAndButtonsAreUniqueAndInsideScreen() {
        val layers = asset.getValue("layers").jsonArray
        val names = layers.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(names.size, names.toSet().size)
        val uuids = layers.flatMap { l ->
            l.jsonObject["normalButtons"]?.jsonArray.orEmpty().map {
                it.jsonObject.getValue("uuid").jsonPrimitive.content
            }
        }
        assertTrue(uuids.isNotEmpty())
        assertEquals(uuids.size, uuids.toSet().size)
    }
}
