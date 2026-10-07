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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Regression tests for the v1-10-0 dock outage.
//
// What the device showed: with 2 layers and normal editing (not preview),
// every layer row, the Create row and the Add picker looked greyed and no
// tap landed anywhere in the dock, while the dialog sheets kept working.
//
// The true cause was not the touch path at all. The dock passed a negated
// flag into a helper that already negates:
// editorAllowsLayerEditing takes isPreviewMode and returns its negation,
// but three dock call sites passed not-isPreviewMode, so the rows were
// disabled exactly when editing was allowed and enabled exactly in preview.
// With rows disabled the selection could never land, selectedLayer stayed
// null, and editorAddBlocker then reported NoSelectedLayer, which dimmed
// the Add picker too. One inverted gate, whole dock dead.
//
// These tests pin the failing path: the gate polarity itself, the blocker
// cascade it caused, the call-site source shape, the touch consumer order
// and the row measurement on a landscape-phone proportion.
class EditorDockLayerGatePolarityTest {

    @Test
    fun `normal editing allows layer rows and preview locks them`() {
        // Direct polarity of the exact helper the dock rows call.
        assertTrue(editorAllowsLayerEditing(false))
        assertFalse(editorAllowsLayerEditing(true))
    }

    @Test
    fun `two layers with a selection unblock adding on the failing path`() {
        // The exact blocker inputs from the recording: 2 layers, not preview.
        // Selected -> None -> the Add picker is enabled.
        val open = editorAddBlocker(layerCount = 2, hasSelectedLayer = true, isPreviewMode = false)
        assertEquals(EditorAddBlocker.None, open)
        assertTrue(editorAllowsAddingControls(open))
    }

    @Test
    fun `lost selection blocks adding which is why dead rows dimmed the picker`() {
        // Rows disabled means taps never select, so hasSelectedLayer stays
        // false and the Add picker reports NoSelectedLayer. Gates unchanged.
        val blocked = editorAddBlocker(layerCount = 2, hasSelectedLayer = false, isPreviewMode = false)
        assertEquals(EditorAddBlocker.NoSelectedLayer, blocked)
        assertFalse(editorAllowsAddingControls(blocked))
    }

    @Test
    fun `preview and empty layouts still disable with reason`() {
        assertEquals(
            EditorAddBlocker.Preview,
            editorAddBlocker(layerCount = 2, hasSelectedLayer = true, isPreviewMode = true)
        )
        assertEquals(
            EditorAddBlocker.NoLayers,
            editorAddBlocker(layerCount = 0, hasSelectedLayer = false, isPreviewMode = false)
        )
        assertFalse(editorAllowsLayerEditing(true))
    }
}

// Source shape guards: the dock must keep passing isPreviewMode straight
// through, never a pre-negated copy, and the touch consumer order must stay
// rows-only (no fullscreen consumer at the dock root).
class EditorDockLayerGateSourceTest {

    private val dock = codeOf(locateDock("OxideEditorDock.kt").readText())
    private val controls = codeOf(locateDock("OxideEditorDockControls.kt").readText())

    @Test
    fun `dock never passes a negated flag into the layer gate`() {
        assertFalse(
            "double negation disables rows during normal editing",
            dock.contains("editorAllowsLayerEditing(!isPreviewMode)")
        )
    }

    @Test
    fun `all four dock call sites use the same unnegated polarity`() {
        // Layer rows, reorder actions, Create row, layer sheet canEdit.
        assertEquals(4, countOf(dock, "editorAllowsLayerEditing(isPreviewMode)"))
    }

    @Test
    fun `only rows consume presses never a fullscreen dock overlay`() {
        // The v1-10-0 rewrite removed the fullscreen box; rows own their taps
        // via combinedClickable and clickable. A new overlay consumer here
        // would eat presses ahead of the rows again.
        assertFalse(dock.contains("pointerInput"))
        assertFalse(dock.contains("awaitFirstDown"))
        assertTrue(controls.contains("combinedClickable"))
    }

    @Test
    fun `layer rows keep a real touch height`() {
        // Rows were visibly full height on the 2772x1280 recording, so this
        // pins that against a future zero-height regression of the same look.
        assertTrue(controls.contains("coerceAtLeast(44.dp)"))
    }

    private fun countOf(source: String, token: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val hit = source.indexOf(token, from)
            if (hit < 0) return count
            count++
            from = hit + token.length
        }
    }

    private fun locateDock(name: String): File {
        val relative = "java/dev/oxide/launcher/ui/screens/main/control_editor/" + name
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val base = dir ?: return@repeat
            for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
                val candidate = base.resolve(prefix + relative)
                if (candidate.isFile) return candidate
            }
            dir = base.parentFile
        }
        error("could not locate src/main/" + relative)
    }

    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}

// Measurement on a landscape-phone proportion: 2772x1280 px at roughly 350
// dpi is about 792x366 dp, checked alongside the 640x360 floor. Either way
// the dock must fit the window with positive non-zero rows.
class EditorDockPhoneMetricsTest {

    @Test
    fun `landscape phone proportion keeps dock and rows measurable`() {
        listOf(792 to 366, 640 to 360).forEach { (w, h) ->
            val metrics = controlEditorMetricsFor(w, h)
            assertTrue("$w x $h dock overflows", metrics.dockWidth.value <= w.toFloat())
            assertTrue("$w x $h dock has no height", metrics.dockHeight.value > 0f)
            assertTrue("$w x $h row is zero height", metrics.rowHeight.value >= 26f)
            assertTrue(
                "$w x $h row taller than dock",
                metrics.rowHeight.value <= metrics.dockHeight.value + 0.001f
            )
            assertTrue("$w x $h content gone", metrics.contentWidth.value > 0f)
        }
    }
}
