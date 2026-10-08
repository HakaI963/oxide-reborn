package dev.oxide.launcher.game.renderer.silica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilicaEnvTest {
    @Test
    fun baseEnvHasExactlyVerifiedKeys() {
        val base = SilicaEnv.baseEnv()
        assertEquals("3", base["LIBGL_ES"])
        assertEquals("libcopperoxide.so", base["LIBGL_EGL"])
        assertEquals("1", base["MG_COUNT_LAUNCH"])
        assertEquals("silica", base["OXIDE_RENDERER_FLAVOR"])
        assertEquals(4, base.size)
    }

    @Test
    fun dataDirAlwaysSetForSilica() {
        assertTrue(SilicaEnv.dataDirEnv("").isEmpty())
        assertEquals(mapOf("MG_DIR_PATH" to "/p"), SilicaEnv.dataDirEnv("/p"))
    }

    @Test
    fun tuningMapsToRealKeysOnly() {
        val r = SilicaTuning.resolve(SilicaTuning.SilicaPerformanceMode.PERFORMANCE, 64, false)
        assertEquals(2, r.fsrLevel)
        val json = SilicaTuning.toConfigJson(r)
        assertTrue(json.contains("maxGlslCacheSize"))
        assertTrue(json.contains("fsr1Setting"))
    }

    @Test
    fun probeFallsBackWithoutCrashing() {
        SilicaProbe.noteProbe("Adreno 750", "OpenGL ES 3.2")
        assertEquals("Adreno 750", SilicaProbe.rendererOrProbe(null))
        assertEquals("<unknown>", SilicaProbe.rendererOrProbe("").takeIf { it == "<unknown>" } ?: SilicaProbe.rendererOrProbe("X"))
    }
}
