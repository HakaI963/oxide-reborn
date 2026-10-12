package dev.oxide.launcher.ui.screens.game

import dev.oxide.launcher.ui.screens.game.elements.fpsAdvancedReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsAdvancedLogicTest {
    @Test
    fun advanced_showsBothValuesAndFgOn() {
        val r = fpsAdvancedReadout(42, 78, true, true, 2)
        assertEquals("Native: 42", r.nativeText)
        assertEquals("Displayed: 78", r.displayedText)
        assertEquals("Frame Generation: ON (2x)", r.frameGenText)
        assertFalse(r.displayedIsEstimate)
    }

    @Test
    fun advanced_off_hidesMultiplier() {
        val r = fpsAdvancedReadout(60, 60, true, false, 2)
        assertEquals("Frame Generation: OFF", r.frameGenText)
    }

    @Test
    fun advanced_unmeasured_showsMeasuring_notFabricated() {
        // Even with native=42 and 2x selected, displayed must NOT read 84.
        val r = fpsAdvancedReadout(42, null, false, true, 2)
        assertEquals("Displayed: measuring", r.displayedText)
        assertTrue(r.displayedIsEstimate)
    }
}
