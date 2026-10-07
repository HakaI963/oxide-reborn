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

package dev.oxide.launcher.game.version.installed

import dev.oxide.launcher.game.addons.modloader.ModLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automatic Oxide Launcher branding of the versionType value.
 *
 * Loader identity reuses the existing detection (VersionInfo.primaryLoader) and
 * the loader display name, so no new detector and no per-loader or per-version
 * hard-coding is needed. The precedence rule is that user text is never silently
 * discarded: a custom info is kept verbatim as the head of the result while the
 * loader tag and the brand are appended, and an already-branded value is
 * returned unchanged so retries never double the suffix.
 *
 * Everything here is pure Kotlin over strings and the ModLoader enum, so no
 * Robolectric and no Android runtime is needed.
 */
class OxideVersionBrandingTest {

    private val brand = "Oxide Launcher"

    @Test
    fun loaderTagMappingForVanillaAndMajorLoadersAndUnknown() {
        assertEquals(null, oxideLoaderVersionTag(null))
        assertEquals("(Fabric)", oxideLoaderVersionTag(ModLoader.FABRIC))
        assertEquals("(Forge)", oxideLoaderVersionTag(ModLoader.FORGE))
        assertEquals("(NeoForge)", oxideLoaderVersionTag(ModLoader.NEOFORGE))
        assertEquals("(Quilt)", oxideLoaderVersionTag(ModLoader.QUILT))
        assertEquals(null, oxideLoaderVersionTag(ModLoader.UNKNOWN))
    }

    @Test
    fun optiFineOnlyCountsAsVanilla() {
        assertEquals(null, oxideLoaderVersionTag(ModLoader.OPTIFINE))
        assertEquals(
            "release / Oxide Launcher",
            resolveBrandedVersionType("", "release", ModLoader.OPTIFINE, brand)
        )
    }

    @Test
    fun remainingLoadersReuseTheirDisplayName() {
        assertEquals("(Legacy Fabric)", oxideLoaderVersionTag(ModLoader.LEGACY_FABRIC))
        assertEquals("(Babric)", oxideLoaderVersionTag(ModLoader.BABRIC))
        assertEquals("(Cleanroom)", oxideLoaderVersionTag(ModLoader.CLEANROOM))
        assertEquals("(LiteLoader)", oxideLoaderVersionTag(ModLoader.LITE_LOADER))
    }

    @Test
    fun blankCustomInfoFallsBackToManifestTypeAndBrands() {
        assertEquals(
            "release / Oxide Launcher",
            resolveBrandedVersionType("", "release", null, brand)
        )
    }

    @Test
    fun loaderTagIsAppendedForBlankCustomInfo() {
        assertEquals(
            "release (Fabric) / Oxide Launcher",
            resolveBrandedVersionType("", "release", ModLoader.FABRIC, brand)
        )
        assertEquals(
            "release (NeoForge) / Oxide Launcher",
            resolveBrandedVersionType("", "release", ModLoader.NEOFORGE, brand)
        )
    }

    @Test
    fun snapshotAndOldManifestTypesArePreserved() {
        assertEquals(
            "snapshot / Oxide Launcher",
            resolveBrandedVersionType("", "snapshot", null, brand)
        )
        assertEquals(
            "old_alpha (Quilt) / Oxide Launcher",
            resolveBrandedVersionType("", "old_alpha", ModLoader.QUILT, brand)
        )
        assertEquals(
            "old_beta (Forge) / Oxide Launcher",
            resolveBrandedVersionType("", "old_beta", ModLoader.FORGE, brand)
        )
    }

    @Test
    fun userValueIsPreservedAndExtended() {
        assertEquals(
            "MyPack / Oxide Launcher",
            resolveBrandedVersionType("MyPack", "release", null, brand)
        )
        assertEquals(
            "MyPack (Forge) / Oxide Launcher",
            resolveBrandedVersionType("MyPack", "release", ModLoader.FORGE, brand)
        )
    }

    @Test
    fun userValueContainingTheLoaderTagIsNotDuplicated() {
        assertEquals(
            "Fabric pack (Fabric) / Oxide Launcher",
            resolveBrandedVersionType("Fabric pack (Fabric)", "release", ModLoader.FABRIC, brand)
        )
    }

    @Test
    fun alreadyBrandedValueIsNeverDoubled() {
        assertEquals(
            "Oxide Launcher",
            resolveBrandedVersionType("Oxide Launcher", "release", null, brand)
        )
        assertEquals(
            "MyPack / Oxide Launcher",
            resolveBrandedVersionType("MyPack / Oxide Launcher", "release", ModLoader.FORGE, brand)
        )
        assertEquals(
            "release (Fabric) / Oxide Launcher",
            resolveBrandedVersionType("release (Fabric) / Oxide Launcher", "release", ModLoader.FABRIC, brand)
        )
    }

    @Test
    fun resolutionIsStableWhenThePipelineIsReentered() {
        val inputs = listOf(
            Triple("", "release", null),
            Triple("", "snapshot", ModLoader.FABRIC),
            Triple("", "old_alpha", ModLoader.QUILT),
            Triple("MyPack", "release", ModLoader.FORGE),
            Triple("Oxide Launcher", "release", ModLoader.FABRIC),
            Triple("", null, ModLoader.NEOFORGE)
        )
        for (input in inputs) {
            val once = resolveBrandedVersionType(input.first, input.second, input.third, brand)
            val twice = resolveBrandedVersionType(once, input.second, input.third, brand)
            assertEquals("re-entry must be stable for " + input.first, once, twice)
            assertTrue("brand must always be present for " + input.first, twice.contains(brand))
        }
    }

    @Test
    fun degenerateBlankInputsYieldBrandAlone() {
        assertEquals(
            "Oxide Launcher",
            resolveBrandedVersionType("", "", null, brand)
        )
        assertEquals(
            "(NeoForge) / Oxide Launcher",
            resolveBrandedVersionType("", null, ModLoader.NEOFORGE, brand)
        )
    }

    @Test
    fun brandingLivesInTheVersionTypePath() {
        val versionSource = locate("game/version/installed/Version.kt").readText()
        assertTrue(
            "Version.kt must expose the branded versionType resolver",
            versionSource.contains("fun getBrandedVersionType")
        )
        assertTrue(
            "loader identity must reuse the existing primaryLoader detection",
            versionSource.contains("versionInfo?.primaryLoader")
        )
        assertTrue(
            "the brand must come from the launcher name constant",
            versionSource.contains("BuildKeys.LAUNCHER_NAME")
        )
        val launchArgsSource = locate("game/launch/LaunchArgs.kt").readText()
        assertTrue(
            "version_type must flow through the branded resolver",
            launchArgsSource.contains("getBrandedVersionType(gameManifest.type)")
        )
    }

    @Test
    fun newBrandingCodeHasNoHardcodedNamesOrVersions() {
        val source = locate("game/version/installed/Version.kt").readText()
        val start = source.indexOf("fun oxideLoaderVersionTag")
        assertTrue("the loader tag helper must exist", start >= 0)
        val block = source.substring(start)
        for (literal in listOf("(Fabric)", "(Forge)", "(NeoForge)", "(Quilt)")) {
            assertTrue(
                "the tag must come from the loader display name, not a literal " + literal,
                !block.contains(literal)
            )
        }
        assertEquals(
            "the branding code must not hard-code any version number",
            0,
            Regex("[0-9]+[.][0-9]+").findAll(block).count()
        )
    }

    private companion object {

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
