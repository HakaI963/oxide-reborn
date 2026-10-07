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

package dev.oxide.launcher.ui.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The in-game virtual keyboard wears the Oxide palette now, nothing else changed.
 *
 * The screenshot behind this (the fullscreen Esc / F1-F12 / QWERTY board behind
 * the in-game "send custom keybind / send custom keycode" menu) was taupe-brown
 * Material cards next to an otherwise Oxide menu. The fix is presentation-only:
 * the dialog panel and every key button read Oxide tokens, while layout, rows,
 * weights, gestures, toggles, close behaviour and every callback stay identical.
 *
 * These are "read the source" facts, not behaviour, so they live here instead of
 * in a Compose test: the board renders inside a real Dialog window, which a pure
 * JVM test cannot host, and the rows and buttons it is built from are private.
 *
 * Assertions run on the source with comments and string literals stripped, so the
 * history notes in the file under test (which name the old tokens on purpose)
 * can never satisfy a liveness assertion.
 *
 * Every helper below is function-local. A top-level val that throws once turns
 * every test in this class into ExceptionInInitializerError and buries the cause.
 */
class KeyboardOxideGuardTest {

    // -----------------------------------------------------------------------
    // The old look is gone
    // -----------------------------------------------------------------------

    /**
     * None of the brown-Material colour, shape or type tokens may remain.
     *
     * Each name is exact: cardColor and itemColor are the launcher palette
     * helpers that resolve to surfaceBright / surfaceVariant (the taupe cards),
     * cardTitleColor is the tab strip ground, and the three MaterialTheme reads
     * are the primary-ink border, the dialog radius and the key label style.
     */
    @Test
    fun `keyboard no longer uses the old brown Material tokens`() {
        val code = keyboardCode()
        for (gone in listOf(
            "cardColor(",
            "onCardColor()",
            "cardTitleColor(",
            "itemColor(",
            "onItemColor()",
            "MaterialTheme",
        )) {
            assertFalse(
                "Keyboard must not paint itself with $gone any more",
                code.contains(gone),
            )
        }
    }

    /**
     * The panel and the keys read the Oxide system, and nothing else.
     *
     * BgElevated is the opaque dialog ground (the same ground OxideDialogPanel
     * paints), BgButton plus BgTabActive are the idle and held-or-bound key
     * fills (the OxideButton and OxideSurface selected recipes), Accent is the
     * pressed border, Fg and FgGhost are the selected and idle tab inks,
     * RadiusDrawer and RadiusBlock are the panel and key radii, and Type Body
     * is the key label face.
     */
    @Test
    fun `keyboard paints the Oxide tokens`() {
        val code = keyboardCode()
        for (token in listOf(
            "Oxide.BgElevated",
            "Oxide.BgButton",
            "Oxide.BgTabActive",
            "Oxide.Accent",
            "Oxide.FgGhost",
            "Oxide.Line2",
            "Oxide.RadiusDrawer",
            "Oxide.RadiusBlock",
            "Oxide.Type.Body",
        )) {
            assertTrue("Keyboard must paint itself with $token", code.contains(token))
        }
        assertTrue(
            "the Oxide system has to be imported for any of that to compile",
            code.contains("import dev.oxide.launcher.ui.theme.Oxide"),
        )
    }

    // -----------------------------------------------------------------------
    // The behaviour is untouched
    // -----------------------------------------------------------------------

    /** Both public entries keep their exact signatures, so every call site stays. */
    @Test
    fun `every public callback signature is untouched`() {
        val code = keyboardCode()
        for (token in listOf(
            "fun Keyboard(",
            "onDismissRequest: () -> Unit",
            "isTapMode: Boolean = false",
            "onSwitch: (key: String, pressed: Boolean) -> Unit",
            "onTap: (key: String) -> Unit",
            "fun GamepadBindingKeyboard(",
            "selectedKeys: List<String>",
            "onKeyAdd: (String) -> Unit",
            "onKeyRemove: (String) -> Unit",
            "fun GamepadSpecialArea(",
        )) {
            assertTrue("the keyboard contract must still contain $token", code.contains(token))
        }
    }

    /**
     * The gesture and toggle wiring is byte-identical: tap still fans out to
     * onTap in tap mode and to the press toggle plus onSwitch otherwise, the
     * pressed border still animates between 2dp and off, and the tab pager
     * still syncs both ways.
     */
    @Test
    fun `key gesture and toggle wiring is untouched`() {
        val code = keyboardCode()
        for (token in listOf(
            "detectTapGestures(",
            "pressed = !pressed",
            "currentOnSwitch(identifier, pressed)",
            "currentOnTap(identifier)",
            "animateDpAsState(",
            "rememberPagerState(",
            "animateScrollToPage(selectedTabIndex)",
            "pagerState.isScrollInProgress",
        )) {
            assertTrue("the key wiring must still contain $token", code.contains(token))
        }
    }

    /**
     * Geometry is untouched: the dialog still spans three quarters of the
     * window, both areas keep their 12dp inset, rows keep their 4dp rhythm and
     * intrinsic height, spacers keep their weights, and keys keep their
     * weight-driven widths and aspect ratios.
     */
    @Test
    fun `layout rows weights and dialog width are untouched`() {
        val code = keyboardCode()
        for (token in listOf(
            "fillMaxWidth(0.75f)",
            "MAIN_LAYOUT",
            "EDIT_LAYOUT",
            "Arrangement.spacedBy(4.dp)",
            "IntrinsicSize.Min",
            "aspectRatio(aspectRatio)",
            "Modifier.weight(key.weight)",
            "weight = 7f",
            "weight = 2.1f",
        )) {
            assertTrue("the keyboard geometry must still contain $token", code.contains(token))
        }
    }

    /**
     * The gamepad special page still lists the same five mouse and wheel rows
     * under the same string keys through the same row component.
     */
    @Test
    fun `gamepad special keys keep their string keys and rows`() {
        val code = keyboardCode()
        for (token in listOf(
            "InfoLayoutTextItem(",
            "control_editor_edit_event_launcher_mouse_left",
            "control_editor_edit_event_launcher_mouse_middle",
            "control_editor_edit_event_launcher_mouse_right",
            "control_editor_edit_event_launcher_mouse_scroll_up_single",
            "control_editor_edit_event_launcher_mouse_scroll_down_single",
            "SPECIAL_KEY_MOUSE_SCROLL_UP",
            "SPECIAL_KEY_MOUSE_SCROLL_DOWN",
            "GLFW_MOUSE_BUTTON_LEFT",
            "GLFW_MOUSE_BUTTON_MIDDLE",
            "GLFW_MOUSE_BUTTON_RIGHT",
        )) {
            assertTrue("the special page must still contain $token", code.contains(token))
        }
        assertTrue(
            "the special page must still list exactly five rows",
            countOf(code, "InfoLayoutTextItem(") == 5,
        )
    }

    // -----------------------------------------------------------------------
    // What deliberately stayed Material, and why
    // -----------------------------------------------------------------------

    /**
     * Dialog, Surface, SecondaryTabRow, Tab and the scroll indicator stay
     * Material, loudly and on purpose: Oxide owns no dialog window, no
     * weight-driven key-grid surface and no tab row with an animated indicator,
     * so replacing them would change window, indicator and touch feel instead
     * of just the paint. They stay, but they must stay Oxide-coloured, which
     * the token test above already pins.
     */
    @Test
    fun `kept Material shells stay and stay Oxide coloured`() {
        val code = keyboardCode()
        for (kept in listOf(
            "androidx.compose.ui.window.Dialog",
            "usePlatformDefaultWidth = false",
            "androidx.compose.material3.Surface",
            "androidx.compose.material3.SecondaryTabRow",
            "androidx.compose.material3.Tab",
            "nonInteractiveScrollbar(",
        )) {
            assertTrue("Keyboard must keep $kept: Oxide has no equivalent", code.contains(kept))
        }
        assertTrue(
            "the dialog ground must be opaque: it sits over live game output",
            code.contains("Oxide.BgElevated"),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers, all local: no top-level state in this file
    // -----------------------------------------------------------------------

    private fun keyboardCode(): String =
        codeOf(locateKeyboard().readText())

    private fun locateKeyboard(): File = locateSource(
        "java/dev/oxide/launcher/ui/control/Keyboard.kt"
    )

    private fun locateSource(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val base = dir ?: return@repeat
            for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
                val candidate = base.resolve(prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = base.parentFile
        }
        error("could not locate src/main/$relativePath")
    }

    private fun codeOf(source: String): String = source
        .replace(rawString(), "\"\"")
        .replace(plainString(), "\"\"")
        .replace(blockComment(), " ")
        .replace(lineComment(), "")

    private fun countOf(source: String, token: String): Int {
        var count = 0
        var index = source.indexOf(token)
        while (index >= 0) {
            count++
            index = source.indexOf(token, index + token.length)
        }
        return count
    }

    private fun rawString(): Regex = Regex("\"\"\"[\\s\\S]*?\"\"\"")
    private fun plainString(): Regex = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
    private fun blockComment(): Regex = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private fun lineComment(): Regex = Regex("""//[^\n]*""")
}
