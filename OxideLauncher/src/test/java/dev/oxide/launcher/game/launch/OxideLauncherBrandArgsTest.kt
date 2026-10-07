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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automatic Oxide Launcher branding of the JVM argument list.
 *
 * The `-Dminecraft.launcher.brand` / `-Dminecraft.launcher.version` flags are
 * telemetry-only (Mojang 23w18a): the main-menu and F3 lines never read them.
 * What the title screen shows comes from the `--versionType` game argument,
 * pinned by OxideVersionTypeArgsTest. The flags stay because versions that do
 * not read them simply ignore unknown -D flags, so setting them unconditionally
 * is safe for old versions, snapshots and every loader with no mod and no jar
 * patch — and these tests pin that neutrality: the helper never looks at the
 * Minecraft version at all, it only appends flags to whatever list it is given.
 *
 * Everything here is pure Kotlin over lists and strings, so no Robolectric and
 * no Android runtime is needed.
 */
class OxideLauncherBrandArgsTest {

    @Test
    fun brandFlagsAreAppendedInBrandThenVersionOrder() {
        val args = mutableListOf<String>()
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(
            listOf("-Dminecraft.launcher.brand=Oxide Launcher", "-Dminecraft.launcher.version=1.0.0"),
            args
        )
    }

    @Test
    fun ensureIsIdempotentWhenThePipelineIsReentered() {
        val args = mutableListOf("-Xmx2G")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(3, args.size)
        assertEquals(1, args.count { it.startsWith(OXIDE_BRAND_ARG_PREFIX) })
        assertEquals(1, args.count { it.startsWith(OXIDE_LAUNCHER_VERSION_ARG_PREFIX) })
    }

    @Test
    fun existingUserArgsKeepTheirOrderAndOnlyTheMissingFlagIsAdded() {
        val args = mutableListOf("-Xmx2G", "-Dfoo=bar", "-Dminecraft.launcher.brand=Custom")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(
            listOf(
                "-Xmx2G",
                "-Dfoo=bar",
                "-Dminecraft.launcher.brand=Custom",
                "-Dminecraft.launcher.version=1.0.0"
            ),
            args
        )
    }

    @Test
    fun anAlreadyBrandedListIsLeftUntouched() {
        val args = mutableListOf("-Dminecraft.launcher.brand=Other", "-Dminecraft.launcher.version=9.9.9")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(
            listOf("-Dminecraft.launcher.brand=Other", "-Dminecraft.launcher.version=9.9.9"),
            args
        )
    }

    @Test
    fun eachFlagIsTrackedIndependently() {
        val args = mutableListOf("-Dminecraft.launcher.version=1.0.0")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(
            listOf("-Dminecraft.launcher.version=1.0.0", "-Dminecraft.launcher.brand=Oxide Launcher"),
            args
        )
    }

    @Test
    fun oldVersionsAndSnapshotsGetTheSameFlagsAndNothingElseChanges() {
        // Old-style argument shapes pass through untouched; the helper appends
        // the two flags and reorders nothing, whatever the Minecraft version is.
        val args = mutableListOf("-cp", "classes", "--session", "tokenabc")
        args.ensureOxideLauncherBrandArgs("Oxide Launcher", "1.0.0")
        assertEquals(6, args.size)
        assertEquals("-cp", args[0])
        assertEquals("classes", args[1])
        assertEquals("--session", args[2])
        assertEquals("tokenabc", args[3])
        assertEquals("-Dminecraft.launcher.brand=Oxide Launcher", args[4])
        assertEquals("-Dminecraft.launcher.version=1.0.0", args[5])
    }

    @Test
    fun flagsAreAssembledInsideTheJvmArgsPipeline() {
        val body = getJavaArgsBody()
        assertTrue(
            "getJavaArgs must route the brand flags through the dedup helper",
            body.contains("ensureOxideLauncherBrandArgs")
        )
        assertTrue(
            "the launcher version flag must use the display version constant",
            body.contains("BuildKeys.LAUNCHER_DISPLAY_VERSION")
        )
    }

    @Test
    fun versionFlagDoesNotUseTheBuildIdentity() {
        assertFalse(
            "getJavaArgs must not read BuildConfig.VERSION_NAME for the brand flags",
            getJavaArgsBody().contains("BuildConfig")
        )
    }

    @Test
    fun displayVersionPropertyCarriesTheRequiredFlagValue() {
        // The -D flag value is the user-facing display version, which lives in
        // gradle.properties and reaches code as BuildKeys.LAUNCHER_DISPLAY_VERSION.
        val properties = moduleRoot().resolve("gradle.properties").readText()
        assertEquals("1.0.0", propertyValue(properties, "launcher_display_version"))
    }

    @Test
    fun newHelpersContainNoVersionNumberLiterals() {
        val source = launchArgsSource()
        val start = source.indexOf("const val OXIDE_BRAND_ARG_PREFIX")
        val end = source.indexOf("fun detectLwjglVersion", start)
        assertTrue("the brand helper block must exist", start >= 0 && end > start)
        val block = source.substring(start, end)
        assertTrue(
            "dedup must be prefix-based so re-entry cannot stack duplicates",
            block.contains("startsWith")
        )
        assertEquals(
            "the brand helpers must not hard-code any version number",
            0,
            Regex("[0-9]+[.][0-9]+").findAll(block).count()
        )
    }

    private companion object {

        fun launchArgsSource(): String = locate("game/launch/LaunchArgs.kt").readText()

        fun getJavaArgsBody(): String {
            val source = launchArgsSource()
            val start = source.indexOf("private fun getJavaArgs()")
            assertTrue("getJavaArgs must exist in LaunchArgs.kt", start >= 0)
            val end = source.indexOf("private fun getMinecraftJVMArgs()", start)
            assertTrue("getMinecraftJVMArgs must follow getJavaArgs", end > start)
            return source.substring(start, end)
        }

        fun moduleRoot(): File {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir ?: return@repeat
                val properties = candidate.resolve("gradle.properties")
                if (candidate.resolve("build.gradle.kts").isFile &&
                    properties.isFile &&
                    properties.readText().contains("launcher_display_version")
                ) {
                    return candidate
                }
                dir = candidate.parentFile
            }
            error("could not locate the OxideLauncher module root from " + File("").absolutePath)
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

        fun propertyValue(properties: String, name: String): String? {
            val prefix = name + "="
            return properties.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith(prefix) }
                .map { it.substring(prefix.length).trim() }
                .firstOrNull()
        }
    }
}
