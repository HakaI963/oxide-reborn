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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.launch

import dev.oxide.launcher.utils.string.splitPreservingQuotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Regression tests for the missing in-game Oxide branding.
//
// The branded versionType (for example "release (Fabric) / Oxide Launcher")
// is a multi-word value, and vanilla renders the title-screen suffix from the
// --versionType game argument as one argv element. The old assembly joined the
// template list into a single string and re-split it on spaces, which
// shattered the branded value into stray tokens: the game then saw only its
// first word, and a leading "release" hides itself on the title screen, so
// the menu showed a bare "Minecraft 26.3 (Modded)". These tests pin the fixed
// assembly: placeholders are substituted in place and every template entry
// stays exactly one argv element, for the modern list template, for the
// legacy single-string template, and for every loader (the fix sits after the
// loader manifests are merged, so Fabric, Forge, NeoForge and Quilt share it).
//
// Everything here is pure Kotlin over lists and strings, so no Robolectric
// and no Android runtime is needed.
class OxideVersionTypeArgsTest {

    private val versionTypePlaceholder = "${'$'}{version_type}"
    private val brand = "Oxide Launcher"

    @Test
    fun brandedVersionTypeWithSpacesStaysASingleArgvElement() {
        val template = listOf("--versionType", versionTypePlaceholder)
        val result = substituteClientGameArgs(
            template,
            mapOf("version_type" to "release (Fabric) / " + brand)
        )
        assertEquals(
            listOf("--versionType", "release (Fabric) / " + brand),
            result
        )
    }

    @Test
    fun shatteredStraysNeverAppearInTheFinalArgList() {
        val template = listOf("--versionType", versionTypePlaceholder)
        val result = substituteClientGameArgs(
            template,
            mapOf("version_type" to "release (Fabric) / " + brand)
        )
        assertEquals(2, result.size)
        assertFalse(result.contains("(Fabric)"))
        assertFalse(result.contains("/"))
        assertFalse(result.contains(brand))
    }

    @Test
    fun loaderMergedTemplateKeepsBrandedTypeAfterTheFlag() {
        // Vanilla 1.13+ template as it survives the loader merge: Fabric and
        // Quilt contribute no game args at all, and Forge style extra flags
        // are additive, so the vanilla --versionType placeholder is what
        // reaches substitution for every loader.
        val template = listOf(
            "--username", "${'$'}{auth_player_name}",
            "--version", "${'$'}{version_name}",
            "--gameDir", "${'$'}{game_directory}",
            "--assetsDir", "${'$'}{assets_root}",
            "--assetIndex", "${'$'}{assets_index_name}",
            "--uuid", "${'$'}{auth_uuid}",
            "--accessToken", "${'$'}{auth_access_token}",
            "--userType", "${'$'}{user_type}",
            "--versionType", versionTypePlaceholder
        )
        val result = substituteClientGameArgs(
            template,
            mapOf(
                "auth_player_name" to "Steve Jobs",
                "version_name" to "26.3",
                "game_directory" to "/games/oxide",
                "assets_root" to "/games/assets",
                "assets_index_name" to "26",
                "auth_uuid" to "abc123",
                "auth_access_token" to "token",
                "user_type" to "msa",
                "version_type" to "release (Fabric) / " + brand
            )
        )
        assertEquals(template.size, result.size)
        val flagIndex = result.indexOf("--versionType")
        assertTrue(flagIndex >= 0)
        assertEquals("release (Fabric) / " + brand, result[flagIndex + 1])
        // A spaced player name is the same bug class and must survive too.
        assertEquals("Steve Jobs", result[result.indexOf("--username") + 1])
    }

    @Test
    fun legacySingleStringTemplateSplitsQuoteAwareThenSubstitutes() {
        val legacy = "--username \"Steve Jobs\" --versionType " + versionTypePlaceholder
        val template = legacy.splitPreservingQuotes()
        assertEquals(listOf("--username", "Steve Jobs", "--versionType", versionTypePlaceholder), template)
        val result = substituteClientGameArgs(
            template,
            mapOf("version_type" to "MyPack (Forge) / " + brand)
        )
        assertEquals(
            listOf("--username", "Steve Jobs", "--versionType", "MyPack (Forge) / " + brand),
            result
        )
    }

    @Test
    fun unmappedPlaceholdersAreLeftVerbatim() {
        val token = "${'$'}{not_a_key}"
        assertEquals(
            listOf("--demo", token),
            substituteClientGameArgs(listOf("--demo", token), emptyMap())
        )
    }

    @Test
    fun clientArgsAssemblyNeverJoinsAndResplits() {
        val body = getMinecraftClientArgsBody()
        assertTrue(
            "already-single template entries must pass through untouched",
            body.contains("filterIsInstance")
        )
        assertTrue(
            "only the legacy single-string form may be split, quote-aware",
            body.contains("splitPreservingQuotes")
        )
        assertTrue(
            "substitution must go through the single-element helper",
            body.contains("substituteClientGameArgs")
        )
        assertFalse(
            "joining the list into one string is what shattered the branded value",
            body.contains("joinToString")
        )
        assertFalse(
            "re-splitting on spaces is what shattered the branded value",
            body.contains("splitAndFilterEmpty")
        )
    }

    @Test
    fun substitutionHelperPreservesOneEntryPerElement() {
        val source = launchArgsSource()
        val start = source.indexOf("fun substituteClientGameArgs")
        assertTrue("the substitution helper must exist", start >= 0)
        val end = source.indexOf("const val OXIDE_BRAND_ARG_PREFIX", start)
        assertTrue("the brand helper block must follow it", end > start)
        val block = source.substring(start, end)
        assertTrue(
            "the helper must substitute placeholders in place",
            block.contains("insertJSONValueList")
        )
        assertFalse(
            "the helper must never join the entries",
            block.contains("joinToString")
        )
        assertFalse(
            "the helper must never split the entries",
            block.contains("split(")
        )
    }

    @Test
    fun brandFlagsCommentStatesTelemetryOnly() {
        val body = getJavaArgsBody()
        assertTrue(
            "the brand flags are telemetry-only and the comment must say so",
            body.contains("telemetry")
        )
        assertFalse(
            "the flags were never read for the title screen, so no such claim may remain",
            body.contains("F3 version line")
        )
    }

    private companion object {

        fun launchArgsSource(): String = locate("game/launch/LaunchArgs.kt").readText()

        fun getMinecraftClientArgsBody(): String {
            val source = launchArgsSource()
            val start = source.indexOf("private fun getMinecraftClientArgs()")
            assertTrue("getMinecraftClientArgs must exist in LaunchArgs.kt", start >= 0)
            val end = source.indexOf("private fun setLauncherInfo", start)
            assertTrue("setLauncherInfo must follow getMinecraftClientArgs", end > start)
            return source.substring(start, end)
        }

        fun getJavaArgsBody(): String {
            val source = launchArgsSource()
            val start = source.indexOf("private fun getJavaArgs()")
            assertTrue("getJavaArgs must exist in LaunchArgs.kt", start >= 0)
            val end = source.indexOf("private fun getMinecraftJVMArgs()", start)
            assertTrue("getMinecraftJVMArgs must follow getJavaArgs", end > start)
            return source.substring(start, end)
        }

        fun locate(relativePath: String): File {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/" + relativePath)
                if (candidate != null && candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            error(
                "could not locate src/main/java/dev/oxide/launcher/" + relativePath +
                    " from " + File("").absolutePath
            )
        }
    }
}
