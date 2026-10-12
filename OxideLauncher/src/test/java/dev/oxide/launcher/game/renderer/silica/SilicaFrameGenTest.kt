package dev.oxide.launcher.game.renderer.silica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilicaFrameGenTest {
    @Test
    fun rates_usesCounterDelta_notMultiplier() {
        val prev = FrameGenStats(true, 2, 100L, 100L, 0L, 0L, "")
        val cur = FrameGenStats(true, 2, 140L, 175L, 35L, 1L, "active")
        val (native, displayed) = SilicaFrameGen.rates(prev, cur, 1.0)
        assertEquals(40.0, native, 1e-9)
        assertEquals(75.0, displayed, 1e-9)
    }

    @Test
    fun supportedMultipliers_matchWinfg() {
        assertTrue(SilicaFrameGen.SUPPORTED_MULTIPLIERS.contains(2))
        assertTrue(SilicaFrameGen.SUPPORTED_MULTIPLIERS.contains(3))
        assertTrue(SilicaFrameGen.SUPPORTED_MULTIPLIERS.contains(4))
        assertFalse(SilicaFrameGen.SUPPORTED_MULTIPLIERS.contains(5))
    }

    @Test
    fun diagnostics_reportsCountersAndReason() {
        val s = FrameGenStats(true, 3, 100L, 250L, 150L, 2L, "active")
        val t = SilicaFrameGen.diagnosticsText("cfg=ON(3x)", s, true, true)
        assertTrue(t.contains("native=100"))
        assertTrue(t.contains("displayed=250"))
        assertTrue(t.contains("gen=150"))
        assertTrue(t.contains("[active]"))
    }

    @Test
    fun diagnostics_absentStats_saysSoPlainly() {
        val t = SilicaFrameGen.diagnosticsText("cfg=OFF", null, true, true)
        assertTrue(t.contains("layer idle"))
    }

    @Test
    fun diagnostics_neverMultiplies() {
        val s = FrameGenStats(true, 4, 100L, 130L, 30L, 0L, "active")
        val t = SilicaFrameGen.diagnosticsText("cfg=ON(4x)", s, true, true)
        assertTrue(t.contains("displayed=130"))
        assertEquals(-1, t.indexOf("displayed=400"))
    }
}
