package dev.oxide.launcher.game.renderer.onigami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnigamiFrameGenDiagTest {
    @Test
    fun diagnostics_reportsCountersAndReason() {
        val s = FrameGenStats(true, 3, 100L, 250L, 150L, 2L, 42L, "active")
        val t = OnigamiFrameGen.diagnosticsText("cfg=ON(3x),guard=on", s)
        assertTrue(t.contains("native=100"))
        assertTrue(t.contains("displayed=250"))
        assertTrue(t.contains("gen=150"))
        assertTrue(t.contains("drop=2"))
        assertTrue(t.contains("[active]"))
    }

    @Test
    fun diagnostics_absentStats_saysSoPlainly() {
        val t = OnigamiFrameGen.diagnosticsText("cfg=OFF,guard=on", null)
        assertTrue(t.contains("no stats file yet"))
    }

    @Test
    fun diagnostics_neverMultiplies() {
        // displayed must come from counters, not native x multiplier
        val s = FrameGenStats(true, 4, 100L, 130L, 30L, 0L, 10L, "active")
        val t = OnigamiFrameGen.diagnosticsText("cfg=ON(4x),guard=on", s)
        assertTrue(t.contains("displayed=130"))
        assertEquals(-1, t.indexOf("displayed=400"))
    }
}
