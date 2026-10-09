/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.onigami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnigamiEnvTest {

    @Before
    fun resetProbe() {
        OnigamiProbe.resetForTest()
    }

    @Test
    fun baseEnvHasOnlyOwnKeys() {
        val base = OnigamiEnv.baseEnv("AUTO")
        assertEquals("3", base["LIBGL_ES"])
        assertEquals("onigami", base["OXIDE_RENDERER_FLAVOR"])
        assertEquals("AUTO", base["ONIGAMI_MODE"])
        assertEquals(3, base.size)
    }

    @Test
    fun noForeignKeysLeak() {
        val merged = OnigamiEnv.merged("BALANCED", "/p")
        for (k in merged.keys) {
            assertTrue(
                "Onigami must not emit foreign-backend keys, found " + k,
                !k.startsWith("MG_") && k != "LIBGL_EGL" &&
                    !k.startsWith("SILICA_") && !k.startsWith("COPPER"),
            )
        }
        assertEquals("/p", merged["ONIGAMI_DATA_DIR"])
    }

    @Test
    fun blankDataDirEmitsNoKey() {
        assertTrue(OnigamiEnv.dataDirEnv("").isEmpty())
        assertTrue(OnigamiEnv.dataDirEnv("   ").isEmpty())
    }

    @Test
    fun identityPointsAtOwnLibraryOnly() {
        assertEquals("opengles3_oxide_onigami", OnigamiIdentity.RENDERER_ID)
        assertEquals("8d2f6b1a-4c7e-4a90-9e5f-onigami000001", OnigamiIdentity.UNIQUE_ID)
        assertEquals("Onigami", OnigamiIdentity.NAME)
        assertEquals("onigami", OnigamiIdentity.FLAVOR)
        assertEquals("libonigami.so", OnigamiIdentity.NATIVE_LIBRARY)
        assertEquals("1.17", OnigamiIdentity.MIN_MC)
        assertEquals("26.3", OnigamiIdentity.MAX_MC)
        assertEquals("onigami", OnigamiIdentity.DATA_DIR)
        assertTrue(!OnigamiIdentity.NATIVE_LIBRARY.contains("copper"))
        assertTrue(!OnigamiIdentity.NATIVE_LIBRARY.contains("mobileglues"))
        assertTrue(!OnigamiIdentity.NATIVE_LIBRARY.contains("silica"))
        assertTrue(!OnigamiIdentity.NATIVE_LIBRARY.contains("ltw"))
    }

    @Test
    fun tuningWritesOwnSchema() {
        val r = OnigamiTuning.resolve(OnigamiPerformanceMode.PERFORMANCE, 64, true, false)
        val json = OnigamiTuning.toConfigJson(OnigamiPerformanceMode.PERFORMANCE, r)
        assertTrue(json.contains("\"onigami_version\":1"))
        assertTrue(json.contains("\"profile\":\"PERFORMANCE\""))
        assertTrue(json.contains("\"program_vault_mb\":64"))
        assertTrue(json.contains("\"state_coalescing\":1"))
        assertTrue(json.contains("\"diagnostics\":0"))
        assertTrue(!json.contains("maxGlslCacheSize"))
        assertTrue(!json.contains("fsr1Setting"))
        assertTrue(!json.contains("silica_version"))
    }

    @Test
    fun qualityFloorKeepsVaultOn() {
        val r = OnigamiTuning.resolve(OnigamiPerformanceMode.QUALITY, 0, true, false)
        assertEquals(64, r.vaultMb)
    }

    @Test
    fun probeNeverReturnsEmptyStrings() {
        assertNull(OnigamiProbe.rendererOrProbe(null))
        assertNull(OnigamiProbe.versionOrProbe(""))
        OnigamiProbe.noteProbe("", null)
        assertNull(OnigamiProbe.rendererOrProbe(null))
        assertEquals("<unknown>", OnigamiProbe.rendererForUi(null))
        assertEquals("<unknown>", OnigamiProbe.versionForUi(null))
    }

    @Test
    fun probeIsRetryable() {
        assertNull(OnigamiProbe.rendererOrProbe(null))
        val misses = OnigamiProbe.fallbackCount()
        assertTrue(misses > 0)
        OnigamiProbe.noteProbe("Adreno 825", "OpenGL ES 3.2")
        assertEquals("Adreno 825", OnigamiProbe.rendererOrProbe(null))
        assertEquals("OpenGL ES 3.2", OnigamiProbe.versionOrProbe(null))
        assertTrue(OnigamiProbe.isAdreno("Adreno 825"))
        assertEquals("Other GPU", OnigamiProbe.rendererOrProbe("Other GPU"))
    }
}
