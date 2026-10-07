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

package dev.oxide.launcher.game.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Copper Oxide `config.json` 序列化与落盘
 *
 * 中立性证明钉在这里：调优关着时序列化返回 null、落盘返回 false 且不建任何
 * 文件，启动目录与此前逐字节一致；开着时写出的文件只含已验证键、可解析回
 * 同样的值。源码守卫钉住写文件的引号键只能是已验证集合，禁用的键名
 * （错别字、已证伪与明确拒绝的）一个都不能出现。
 */
class CopperOxideConfigTest {

    @Test
    fun disabledTuningSerializesToNullSoNoFileIsWritten() {
        assertNull(
            "tuning off must serialize to null so the caller writes nothing",
            CopperOxideTuning().toConfigJson(),
        )
        assertNull(
            "tuning off with stray values must still serialize to null",
            CopperOxideTuning(enabled = false, fsr = 4, angle = 3).toConfigJson(),
        )
    }

    @Test
    fun disabledTuningAuthoringTouchesNothingOnDisk() {
        val dir = Files.createTempDirectory("copper-config-test").toFile()
        try {
            val target = File(dir, "nested")
            assertFalse(
                "tuning off must report that nothing was written",
                authorCopperOxideConfig(target, CopperOxideTuning()),
            )
            assertFalse(
                "tuning off must not even create the directory",
                target.exists(),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun enabledDefaultsRoundTripThroughTheParser() {
        val tuning = CopperOxideTuning(enabled = true)
        val json = tuning.toConfigJson() ?: error("tuning on must produce a body")
        val parsed = parseCopperOxideConfigJson(json)
        assertEquals(0L, parsed["enableANGLE"])
        assertEquals(0L, parsed["enableNoError"])
        assertEquals(1L, parsed["enableExtComputeShader"])
        assertEquals(1L, parsed["enableExtTimerQuery"])
        assertEquals(1L, parsed["enableExtDirectStateAccess"])
        assertEquals(64L * 1048576L, parsed["maxGlslCacheSize"])
        assertEquals(0L, parsed["fsr1Setting"])
        assertEquals(
            "only the verified keys may be present",
            COPPER_OXIDE_CONFIG_VERIFIED_KEYS,
            parsed.keys,
        )
    }

    @Test
    fun nonDefaultValuesRoundTrip() {
        val tuning = CopperOxideTuning(
            enabled = true,
            fsr = 4,
            glslCacheMb = 128,
            angle = 3,
            noError = 1,
            extCompute = false,
            extTimerQuery = false,
            extDsa = false,
        )
        val parsed = parseCopperOxideConfigJson(tuning.toConfigJson() ?: error("must produce a body"))
        assertEquals(4L, parsed["fsr1Setting"])
        assertEquals(128L * 1048576L, parsed["maxGlslCacheSize"])
        assertEquals(3L, parsed["enableANGLE"])
        assertEquals(1L, parsed["enableNoError"])
        assertEquals(0L, parsed["enableExtComputeShader"])
        assertEquals(0L, parsed["enableExtTimerQuery"])
        assertEquals(0L, parsed["enableExtDirectStateAccess"])
    }

    @Test
    fun cacheSizeConvertsFromMbToBytesAndZeroDisables() {
        assertEquals(
            0L,
            parseCopperOxideConfigJson(
                CopperOxideTuning(enabled = true, glslCacheMb = 0).toConfigJson()
                    ?: error("must produce a body"),
            )["maxGlslCacheSize"],
        )
        assertEquals(
            "negative cache sizes must disable the cache, not underflow it",
            0L,
            parseCopperOxideConfigJson(
                CopperOxideTuning(enabled = true, glslCacheMb = -50).toConfigJson()
                    ?: error("must produce a body"),
            )["maxGlslCacheSize"],
        )
    }

    @Test
    fun outOfRangeInputsAreClampedIntoVerifiedRanges() {
        val parsed = parseCopperOxideConfigJson(
            CopperOxideTuning(
                enabled = true,
                fsr = 99,
                angle = -7,
                noError = 99,
                glslCacheMb = 999999,
            ).toConfigJson() ?: error("must produce a body"),
        )
        assertEquals(4L, parsed["fsr1Setting"])
        assertEquals(0L, parsed["enableANGLE"])
        assertEquals(3L, parsed["enableNoError"])
        assertEquals(
            "the cache clamp follows the setting range, not an invented ceiling",
            512L * 1048576L,
            parsed["maxGlslCacheSize"],
        )
    }

    @Test
    fun unknownKeysAreRejected() {
        for (key in listOf(
            "customGLVersion",
            "hideMGEnvLevel",
            "multidrawOrder",
            "multidrawMode",
            "bufferCoherentAsFlush",
            "enableAngle",
            "ignoreError",
            "angleDepthClearFixMode",
        )) {
            try {
                parseCopperOxideConfigJson("{\"" + key + "\":1,\"fsr1Setting\":0}")
                fail("unknown key must be rejected: " + key)
            } catch (e: IllegalArgumentException) {
                assertTrue(
                    "the rejection must name the key",
                    (e.message ?: "").contains(key),
                )
            }
        }
    }

    @Test
    fun emptyBodiesAreRejected() {
        try {
            parseCopperOxideConfigJson("{}")
            fail("an empty body carries no verified keys and must be rejected")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun writerSourceMentionsOnlyVerifiedKeys() {
        val raw = readMainSource("game/renderer/CopperOxideConfig.kt").replace("\\", "")
        val quoted = Regex("\"([A-Za-z0-9]+)\":").findAll(raw)
            .map { it.groupValues[1] }
            .toSet()
        assertFalse("expected the writer to emit quoted keys", quoted.isEmpty())
        assertEquals(
            "the writer must emit exactly the verified keys, found extra " +
                (quoted - COPPER_OXIDE_CONFIG_VERIFIED_KEYS).joinToString(),
            COPPER_OXIDE_CONFIG_VERIFIED_KEYS,
            quoted,
        )
    }

    @Test
    fun forbiddenKeyNamesAppearNowhereInTheWriter() {
        // Documentation names the rejected keys on purpose, so strip comments
        // first: the guard is about emitted code, not prose.
        val source = readMainSource("game/renderer/CopperOxideConfig.kt")
        val code = source.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
            .lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
        for (name in listOf(
            "customGLVersion",
            "hideMGEnvLevel",
            "multidrawOrder",
            "multidrawMode",
            "bufferCoherentAsFlush",
            "enableAngle",
            "ignoreError",
            "angleDepthClearFixMode",
        )) {
            assertFalse(
                name + " must not appear in the writer code",
                code.contains(name),
            )
        }
    }

    @Test
    fun tuningForcesThePrivateDirWhenTheDataDirSwitchIsOff() {
        val private = File("/files/mobileglues")
        assertEquals(
            "tuning on must resolve to the private dir even with the switch off",
            private,
            resolveCopperOxideDataDir(
                usePrivateDir = false,
                tuningEnabled = true,
                privateDir = private,
            ),
        )
        assertEquals(
            "the switch alone still resolves to the private dir",
            private,
            resolveCopperOxideDataDir(
                usePrivateDir = true,
                tuningEnabled = false,
                privateDir = private,
            ),
        )
    }

    @Test
    fun bothSwitchesOffResolvesToNoDir() {
        assertNull(
            "both off means no dir and no file, exactly like today",
            resolveCopperOxideDataDir(
                usePrivateDir = false,
                tuningEnabled = false,
                privateDir = File("/files/mobileglues"),
            ),
        )
    }

    @Test
    fun resolutionNeverPointsAtSharedStorage() {
        val private = File("/data/data/dev.oxide.launcher/files/mobileglues")
        val resolved = resolveCopperOxideDataDir(
            usePrivateDir = false,
            tuningEnabled = true,
            privateDir = private,
        )
        assertEquals(private, resolved)
        assertFalse(
            "the resolver only ever returns the passed private dir",
            (resolved?.absolutePath ?: "").startsWith("/sdcard"),
        )
    }

    @Test
    fun authorWritesAParseableFileOnlyWhenTuningIsOn() {
        val dir = Files.createTempDirectory("copper-config-test").toFile()
        try {
            val target = File(dir, "nested")
            assertTrue(
                "tuning on must report the write",
                authorCopperOxideConfig(target, CopperOxideTuning(enabled = true, fsr = 2)),
            )
            val body = File(target, COPPER_OXIDE_CONFIG_FILE_NAME).readText()
            assertEquals(2L, parseCopperOxideConfigJson(body)["fsr1Setting"])
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun readMainSource(relativePath: String): String = locate(
        "src/main/java/dev/oxide/launcher/" + relativePath,
    ).readText()

    private fun locate(suffix: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(suffix)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate " + suffix + " from " + File("").absolutePath)
    }
}
