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

package dev.oxide.launcher.ui.screens.main.control_editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simple (default) vs advanced editor dock gating.
 *
 * Simple keeps every essential action (select layer, grid, add, inspector,
 * preview switch, save) and hides only the power-user blocks. Advanced
 * restores the full panel. Nothing is removed, only deferred.
 */
class ControlEditorSimpleModeTest {
    @Test
    fun simpleHidesAdvancedBlocks() {
        assertFalse(editorShowsAdvancedBlock(false))
        assertFalse(editorShowsSnapBlock(false))
        assertFalse(editorShowsStylesBlock(false))
        assertFalse(editorShowsLayerFocus(false, false))
        assertFalse(editorShowsLayerReorder(false, false))
        assertFalse(editorShowsPreviewDevice(false, true))
    }

    @Test
    fun advancedRestoresEverythingOutsidePreview() {
        assertTrue(editorShowsAdvancedBlock(true))
        assertTrue(editorShowsSnapBlock(true))
        assertTrue(editorShowsStylesBlock(true))
        assertTrue(editorShowsLayerFocus(true, false))
        assertTrue(editorShowsLayerReorder(true, false))
    }

    @Test
    fun previewLocksEditingInBothModes() {
        assertFalse(editorShowsLayerFocus(true, true))
        assertFalse(editorShowsLayerReorder(true, true))
        // Device selector needs both advanced and preview.
        assertTrue(editorShowsPreviewDevice(true, true))
        assertFalse(editorShowsPreviewDevice(true, false))
    }
}
