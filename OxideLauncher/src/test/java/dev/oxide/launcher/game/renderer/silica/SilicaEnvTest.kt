package dev.oxide.launcher.game.renderer.silica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilicaEnvTest {
    @Test
    fun baseEnvHasOnlyOwnKeys() {
        val base = SilicaEnv.baseEnv("AUTO")
        assertEquals("3", base["LIBGL_ES"])
        assertEquals("silica", base["OXIDE_RENDERER_FLAVOR"])
        assertEquals("AUTO", base["SILICA_MODE"])
        assertEquals(3, base.size)
    }

    @Test
    fun noMobileGluesKeysLeak() {
        val merged = SilicaEnv.merged("BALANCED", "/p")
        for (k in merged.keys) {
            assertTrue("Silica must not emit MobileGlues keys, found " + k,
                !k.startsWith("MG_") && k != "LIBGL_EGL")
        }
        assertEquals("/p", merged["SILICA_DATA_DIR"])
    }

    @Test
    fun identityPointsAtOwnLibraryOnly() {
        assertEquals("libsilica.so", SilicaIdentity.NATIVE_LIBRARY)
        assertEquals("libsilica.so", SilicaIdentity.EGL_LIBRARY)
        assertTrue(!SilicaIdentity.NATIVE_LIBRARY.contains("copper"))
        assertTrue(!SilicaIdentity.NATIVE_LIBRARY.contains("mobileglues"))
    }

    @Test
    fun tuningWritesOwnSchema() { // + no upscale key without a backend
        val r = SilicaTuning.resolve(SilicaPerformanceMode.PERFORMANCE, 64, true, false)
        val json = SilicaTuning.toConfigJson(SilicaPerformanceMode.PERFORMANCE, r)
        assertTrue(json.contains("silica_version"))
        assertTrue(json.contains("program_vault_mb"))
        assertTrue(json.contains("state_coalescing"))
        assertTrue(!json.contains("maxGlslCacheSize"))
        assertTrue(!json.contains("fsr1Setting"))
    }

    @Test
    fun probeFallsBackWithoutCrashing() {
        SilicaProbe.noteProbe("Adreno 750", "OpenGL ES 3.2")
        assertEquals("Adreno 750", SilicaProbe.rendererOrProbe(null))
        assertTrue(SilicaProbe.isAdreno("Adreno 750"))
    }
}
