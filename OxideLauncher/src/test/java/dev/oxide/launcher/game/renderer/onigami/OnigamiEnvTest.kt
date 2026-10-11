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

import dev.oxide.launcher.game.version.installed.GraphicsApi
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
        assertEquals("ONIGAMI (Vulkan)", OnigamiIdentity.NAME)
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
        val r = OnigamiTuning.resolve(64, true, false)
        val json = OnigamiTuning.toConfigJson(OnigamiPerformanceMode.OPTIMIZED, r)
        assertTrue(json.contains("\"onigami_version\":1"))
        assertTrue(json.contains("\"profile\":\"PERFORMANCE\""))
        assertTrue(json.contains("\"program_vault_mb\":64"))
        assertTrue(json.contains("\"state_coalescing\":1"))
        assertTrue(json.contains("\"diagnostics\":0"))
        assertTrue(!json.contains("maxGlslCacheSize"))
        assertTrue(!json.contains("fsr1Setting"))
        assertTrue(!json.contains("silica_version"))
        assertTrue(json.contains("\"swap_interval\":1"))
        assertTrue(json.contains("\"render_scale\":1.0"))
        assertTrue(json.contains("\"upscale_mode\":0"))
    }

    @Test
    fun qualityKeepsVaultOffSimplePath() {
        // Simple renderer: no forced cache; QUALITY keeps caller value (0 stays 0).
        val r = OnigamiTuning.resolve(0, false, true)
        assertEquals(0, r.vaultMb)
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
    fun renderScaleDefaultsToFullWithBilinearOff() {
        val r = OnigamiTuning.resolve(64, false, true)
        assertEquals(100, r.renderScale)
        assertEquals(0, r.upscaleMode)
        val json = OnigamiTuning.toConfigJson(OnigamiPerformanceMode.OPTIMIZED, r)
        assertTrue(json.contains("\"render_scale\":1.0"))
        assertTrue(json.contains("\"upscale_mode\":0"))
    }

    @Test
    fun vulkanSwitchOfferedOnlyForOnigamiWithoutVulkan() {
        assertTrue(OnigamiVulkan.requiresVulkan(OnigamiIdentity.UNIQUE_ID))
        assertTrue(OnigamiVulkan.needsVulkanSwitch(OnigamiIdentity.UNIQUE_ID, GraphicsApi.DEFAULT_OPENGL))
        assertTrue(OnigamiVulkan.needsVulkanSwitch(OnigamiIdentity.UNIQUE_ID, GraphicsApi.DEFAULT))
        assertTrue(!OnigamiVulkan.needsVulkanSwitch(OnigamiIdentity.UNIQUE_ID, GraphicsApi.VULKAN))
        assertTrue(!OnigamiVulkan.needsVulkanSwitch("other-renderer-id", GraphicsApi.DEFAULT_OPENGL))
    }

    @Test
    fun vulkanDialogStringsMatchSpec() {
        assertEquals("ONIGAMI requires Vulkan. Switch the Graphics API to Vulkan?", OnigamiVulkan.DIALOG_MESSAGE)
        assertEquals("Cancel", OnigamiVulkan.DIALOG_DISMISS)
        assertEquals("Switch to Vulkan", OnigamiVulkan.DIALOG_CONFIRM)
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
