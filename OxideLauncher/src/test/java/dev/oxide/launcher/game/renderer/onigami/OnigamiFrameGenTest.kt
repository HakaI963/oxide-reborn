package dev.oxide.launcher.game.renderer.onigami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnigamiFrameGenTest {
    @Test
    fun rates_usesCounterDelta_notMultiplier() {
        val prev = FrameGenStats(true, 2, 100L, 100L, 0L, 0L)
        val cur = FrameGenStats(true, 2, 140L, 175L, 35L, 1L)
        val (native, displayed) = OnigamiFrameGen.rates(prev, cur, 1.0)
        assertEquals(40.0, native, 1e-9)
        assertEquals(75.0, displayed, 1e-9)
    }

    @Test
    fun rates_clampsNegativeDelta() {
        val prev = FrameGenStats(true, 2, 200L, 200L, 0L, 0L)
        val cur = FrameGenStats(true, 2, 100L, 100L, 0L, 0L)
        val (native, displayed) = OnigamiFrameGen.rates(prev, cur, 1.0)
        assertEquals(0.0, native, 1e-9)
        assertEquals(0.0, displayed, 1e-9)
    }

    @Test
    fun rates_zeroDt_isZero() {
        val s = FrameGenStats(false, 2, 10L, 10L, 0L, 0L)
        val (native, displayed) = OnigamiFrameGen.rates(s, s, 0.0)
        assertEquals(0.0, native, 1e-9)
        assertEquals(0.0, displayed, 1e-9)
    }

    @Test
    fun supportedMultipliers_only2x() {
        assertTrue(OnigamiFrameGen.SUPPORTED_MULTIPLIERS.contains(2))
        assertTrue(OnigamiFrameGen.SUPPORTED_MULTIPLIERS.contains(3))
        assertTrue(OnigamiFrameGen.SUPPORTED_MULTIPLIERS.contains(4))
        assertFalse(OnigamiFrameGen.SUPPORTED_MULTIPLIERS.contains(5))
    }
}
