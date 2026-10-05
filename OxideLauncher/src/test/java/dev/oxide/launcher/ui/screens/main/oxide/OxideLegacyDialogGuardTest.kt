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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two legacy surfaces that were still reachable in v1.7.0, pinned shut.
 *
 * Both are "read the source" facts rather than behaviour, which is exactly why they
 * belong here instead of in a Compose test:
 *
 * 1. **`Activity.openLink`** built a `MaterialAlertDialogBuilder`. That is the grey
 *    rounded card with the salmon buttons in the Settings > Downloads screenshot. Its
 *    caller is `OxideNativeInvoker.openLink`, a `@Keep @JvmStatic` entry point called
 *    from native/plugin code — so the compiler cannot see the call edge and a unit test
 *    cannot reach it either. The only way to hold the line is to read the source.
 * 2. **The crash screen** was still the old one end to end: `BackgroundCard` +
 *    `ScalingActionButton` in landscape, `Scaffold` + `TopAppBar` + `DropdownMenu` in
 *    portrait. Here the point is not just the components but the *trace*: it must be
 *    monospace, inside a scroll, and carry no `maxLines`, because a truncated crash
 *    report is worse than no crash report — the user has nothing to send.
 *
 * Assertions run on the source with comments and string literals stripped: the
 * comments here and in the files under test mention the old names on purpose, to
 * explain the replacement, and that history must not satisfy a liveness assertion.
 */
class OxideLegacyDialogGuardTest {

    private val network = codeOf(locate("utils/network/NetWorkUtils.kt").readText())
    private val errorScreen = codeOf(locate("ui/screens/main/ErrorScreen.kt").readText())
    private val errorActivity = codeOf(locate("ui/activities/ErrorActivity.kt").readText())

    // -----------------------------------------------------------------------
    // FIX 1 — the "Open in Browser" dialog
    // -----------------------------------------------------------------------

    @Test
    fun `openLink no longer builds a Material dialog`() {
        for (gone in listOf(
            "MaterialAlertDialogBuilder",
            ".setPositiveButton(",
            ".setNegativeButton(",
            ".setNeutralButton(",
            ".showThemed()",
        )) {
            assertFalse(
                "Activity.openLink must not reach the legacy builder through $gone",
                network.contains(gone),
            )
        }
    }

    @Test
    fun `openLink goes through the Oxide link dialog`() {
        assertTrue(
            "the confirmation has to be the Oxide panel, or nothing changed",
            network.contains("showOxideLinkDialog("),
        )
        assertTrue(
            "the Oxide dialog helper has to be imported for that to compile",
            network.contains("import dev.oxide.launcher.ui.screens.main.oxide.showOxideLinkDialog"),
        )
    }

    /**
     * The three labels and the clipboard label constant are unchanged.
     *
     * This is the part a redesign is most likely to quietly lose: `COPY_LABEL_LINK` is
     * what shows up as the clipboard entry name on the user's device, so a change of
     * constant is a change of behaviour that no screenshot would ever reveal.
     */
    @Test
    fun `the link dialog keeps the same three labels and the same clipboard label`() {
        for (label in listOf(
            "R.string.generic_open_link",
            "R.string.generic_confirm",
            "R.string.generic_cancel",
            "R.string.generic_copy",
        )) {
            assertTrue("openLink must still show $label", network.contains(label))
        }
        assertTrue(
            "the clipboard entry name is part of the contract",
            network.contains("copyText(COPY_LABEL_LINK, copied, this)"),
        )
    }

    @Test
    fun `the public signature of openLink is untouched`() {
        // Dozens of call sites depend on both overloads, including the JNI bridge
        assertTrue(network.contains("fun Activity.openLink(link: String)"))
        assertTrue(network.contains("fun Activity.openLink(link: String, dataType: String?)"))
        // And the actual opening still goes through the same helper
        assertTrue(network.contains("openLinkInternal(opened, dataType)"))
    }

    @Test
    fun `an empty link still short-circuits before any dialog`() {
        assertTrue(network.contains("if (link.isEmptyOrBlank())"))
    }

    // -----------------------------------------------------------------------
    // FIX 2 — the crash screen
    // -----------------------------------------------------------------------

    /**
     * None of the four legacy components may appear.
     *
     * `DropdownMenu` and `TopAppBar` were only ever on the portrait branch, and the
     * portrait branch is genuinely reachable — `ErrorActivity` is declared
     * `sensorLandscape` but the panel's rotate button sets `requestedOrientation` to
     * `SCREEN_ORIENTATION_PORTRAIT` — so a change that only cleaned up landscape would
     * have left a second old screen on the same crash.
     */
    @Test
    fun `the crash screen uses no legacy component`() {
        for (gone in listOf(
            "ScalingActionButton",
            "BackgroundCard",
            "Scaffold(",
            "TopAppBar",
            "DropdownMenu",
            "MarqueeText",
            "verticalScrollWithBar",
        )) {
            assertFalse(
                "the crash screen must not build itself out of $gone",
                errorScreen.contains(gone),
            )
        }
    }

    @Test
    fun `the crash screen is built from the Oxide panel`() {
        for (oxide in listOf(
            "OxidePanelShell(",
            "OxideSecDivider()",
            "OxideButton(",
            "OxideButtonTone.Primary",
            "OxideButtonTone.Secondary",
            "oxidePanelBoundsFor(",
        )) {
            assertTrue("the crash panel must use $oxide", errorScreen.contains(oxide))
        }
        for (token in listOf(
            "Oxide.BgElevated",
            "Oxide.Fg",
            "Oxide.FgMuted",
            "Oxide.FgDim",
            "Oxide.Type.Body",
            "Oxide.Type.Mono",
            "Oxide.Type.DrawerTitle",
            "Oxide.Type.MicroLabel",
        )) {
            assertTrue("the crash panel must use $token", errorScreen.contains(token))
        }
    }

    /**
     * The trace is monospace and the panel is what bounds it.
     *
     * `heightIn` is the load-bearing half. A scroll on its own is the v1.5.0 P0 crash
     * (`verticalScroll` handed `maxHeight = Infinity`); a bound on its own leaves a long
     * trace pushing the panel off-screen. `OxidePanelShell` clamps the panel first and
     * scrolls inside, which is why the assertion is on the shell and not on a hand-rolled
     * `verticalScroll` here.
     */
    @Test
    fun `the trace is monospace and sits inside the panel's bounded scroll`() {
        assertTrue(
            "the trace must be set in the monospace face",
            errorScreen.contains("Oxide.Type.Mono.fontSize"),
        )
        assertTrue(
            "the trace has to be the panel content, which is the bounded scroll region",
            errorScreen.contains("OxidePanelShell("),
        )
        assertTrue(
            "the bounds come from the shared policy, not from a constant here",
            errorScreen.contains("bounds: OxidePanelBounds"),
        )
    }

    /**
     * No `maxLines` may be applied to the trace.
     *
     * This is the assertion that encodes the user's report. The old surface had no
     * `maxLines` but also no bound, so the tail simply went off-screen; the tempting fix
     * in that direction is `maxLines = 20`, which would look finished and destroy the
     * only thing on this screen that anyone can act on.
     */
    @Test
    fun `the trace text is never truncated`() {
        val traceText = traceTextArgument(errorScreen)

        assertTrue("could not find the trace Text: $traceText", traceText.contains("report.trace"))
        for (banned in listOf("maxLines", "TextOverflow", "Ellipsis", ".take(", ".substring(")) {
            assertFalse(
                "the trace Text must not truncate with $banned: $traceText",
                traceText.contains(banned),
            )
        }
    }

    @Test
    fun `the cause line is monospace as well`() {
        // The cause is the one thing a reader acts on, so it gets the same face as the
        // trace it was extracted from
        val causeText = argumentOf(errorScreen, "text = report.cause")

        assertTrue("could not find the cause Text", causeText.contains("Oxide.Type.Mono.fontSize"))
    }

    /**
     * `ErrorActivity` still hands over the complete trace.
     *
     * `throwableToString(throwable)` is the whole point: it is `printStackTrace`, so it
     * contains the `Caused by:` chain and every `Suppressed:` block. If this ever becomes
     * `throwable.message` or `stackTrace.firstOrNull()`, everything below it is fine and
     * the screen is still useless.
     */
    @Test
    fun `the activity still passes the full stack trace`() {
        assertTrue(
            "the trace must come from printStackTrace, not from the exception's message",
            errorActivity.contains("throwableToString(throwable)"),
        )
        assertFalse(
            "a single-frame or message-only summary is not a crash report",
            errorActivity.contains("stackTrace.firstOrNull()"),
        )
    }

    /**
     * Every action keeps its original gate.
     *
     * Share Logs was gated on the crash log existing (`logExists`) and Restart on
     * `canRestart`; Share Link on `canUpload`. Those three booleans are still threaded
     * from the Activity, and the model passes them straight through.
     */
    @Test
    fun `the action gates are unchanged`() {
        for (gate in listOf("shareLogs = logExists", "canUpload = viewModel.canUpload", "canRestart = canRestart")) {
            assertTrue("ErrorActivity must still pass $gate", errorActivity.contains(gate))
        }
        assertTrue("shareLogs", errorActivity.contains("logFile.exists() && logFile.isFile"))
        assertTrue(
            "the report must carry shareLogs through without rewriting it",
            errorScreen.contains("shareLogs = shareLogs"),
        )
        assertTrue(errorScreen.contains("canUpload = canUpload"))
        assertTrue(errorScreen.contains("canRestart = canRestart"))
    }

    /** The three action strings must still exist and still be referenced by the panel. */
    @Test
    fun `the crash action strings still exist and are still referenced`() {
        val strings = locate("res/values/strings.xml").readText()
        val surface = errorActivity + errorScreen
        for (name in listOf(
            "crash_share_logs",
            "crash_restart",
            "crash_exit",
            "crash_link_share_button",
            "crash_type",
            "crash_type_launcher",
            "crash_type_game",
            "crash_launcher_title",
            "crash_launcher_message",
        )) {
            assertTrue("$name must not be removed from the string table", strings.contains("name=\"$name\""))
            assertTrue(
                "$name must still be referenced by the crash surface",
                surface.contains("R.string.$name"),
            )
        }
    }

    @Test
    fun `the rotate action is still wired to requestedOrientation`() {
        // Both orientations are reachable: the panel's rotate button is the only thing
        // that takes the crash screen out of the manifest's sensorLandscape
        assertTrue(errorActivity.contains("this@ErrorActivity.requestedOrientation = it"))
        assertTrue(errorScreen.contains("onOrientationChanged"))
        assertTrue(errorScreen.contains("SCREEN_ORIENTATION_PORTRAIT"))
        assertTrue(errorScreen.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE"))
    }

    @Test
    fun `the crash page paints the opaque Oxide background`() {
        // The old surface sat on the theme background colour, which is a wallpaper-aware
        // value; the panel system expects the opaque Oxide token underneath it
        assertTrue(errorActivity.contains("color = Oxide.Bg"))
        assertFalse(
            "the theme background can be translucent and the panel must not float on it",
            errorActivity.contains("backgroundColor()"),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * The `Text(...)` call whose argument list is `text = report.trace`.
     *
     * Scoped to a single call rather than the whole file on purpose: `OxidePanelShell`
     * draws its own title with `maxLines = 2`, and a file-wide ban would forbid correct
     * behaviour in a component this file does not own.
     */
    private fun traceTextArgument(source: String): String {
        val anchor = "text = report.trace"
        val start = source.indexOf(anchor)
        assertTrue("could not find `$anchor`", start >= 0)
        val callStart = source.lastIndexOf("Text(", start)
        assertTrue("could not find the enclosing Text( call", callStart >= 0)
        return balanced(source, callStart)
    }

    /** The argument list of the first call mentioning [anchor]. */
    private fun argumentOf(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        assertTrue("could not find `$anchor`", start >= 0)
        val callStart = source.lastIndexOf("(", start)
        return balanced(source, callStart)
    }

    /**
     * The source text of one balanced `(...)` group starting at [open].
     *
     * String literals have already been blanked by [codeOf], so counting delimiters is
     * enough here — a `(` inside a URL or a KDoc cannot shift the result.
     */
    private fun balanced(source: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        error("unbalanced call starting at $open")
    }

    /**
     * Strip string literals and comments, leaving only what gets compiled.
     *
     * Order matters: strings first, then comments. A `//` inside a string literal — a
     * URL, say — would otherwise start a line comment and swallow the rest of the file,
     * which turns a genuine failure into a silent pass.
     */
    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /**
     * Find a file by its path under the module's `src/main`.
     *
     * Two roots, because a source path and a resource path do not share one prefix:
     * Kotlin lives under `java/dev/oxide/launcher`, resources under `res`. A unit test's
     * working directory is not necessarily the module root either, so walk up. Failure
     * is an error rather than a skip — a skipped guard is not a guard.
     */
    private fun locate(relativePath: String): File {
        val root = if (relativePath.startsWith("res/")) {
            "src/main/$relativePath"
        } else {
            "src/main/java/dev/oxide/launcher/$relativePath"
        }
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(root)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate $root from ${File("").absolutePath}")
    }

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}