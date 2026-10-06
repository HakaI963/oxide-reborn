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

package dev.oxide.launcher.ui.screens.main.oxide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The v1.8.0 crash on opening any external link, pinned shut.
 *
 * The reported symptom was `IllegalStateException: ViewTreeLifecycleOwner not found
 * from vf1{...}` out of `ComposeView.onAttachedToWindow`, reached from
 * `Activity.openLink` -> [showOxideLinkDialog]. The mechanism is a bare
 * `android.app.Dialog`: its decor view is not an Activity, so it carries no
 * `ViewTreeLifecycleOwner`, no `ViewTreeSavedStateRegistryOwner` and no
 * `ViewTreeViewModelStoreOwner` — and `AbstractComposeView.onAttachedToWindow`
 * requires all three before it will create a composition at all.
 *
 * `ViewCompositionStrategy.DisposeOnDetachedFromWindow` looks like the fix and is
 * not: the strategy only decides *when* to dispose, which happens strictly after
 * `onAttachedToWindow` got far enough to throw. That wrong assumption is what the
 * two v1.8.0 comments asserted, so the fix has to be checked on the owners, not on
 * the strategy.
 *
 * There is no Robolectric in this repo and no way to inflate a dialog window in a
 * plain JVM test, so these are source facts — the same approach as
 * [OxideLegacyDialogGuardTest], for the same reason: the defect lives in a window
 * attachment that only a device can perform. The last test is deliberately
 * repository-wide, because the class of bug is not per-file: any future `ComposeView`
 * built for its own window will crash the same way.
 */
class OxideDialogWindowOwnerGuardTest {

    private val dialogs = codeOf(locate("ui/screens/main/oxide/OxideDialogs.kt").readText())

    // -----------------------------------------------------------------------
    // Both dialog sites go through the helper
    // -----------------------------------------------------------------------

    /**
     * Every `AndroidDialog(` site must install the helper's `ComposeView`.
     *
     * The failure being guarded is exactly a site that builds its own `ComposeView`
     * inline and forgets the owners, so the assertion is structural: within the
     * statement that follows each `AndroidDialog(`, there has to be a call to
     * [oxideDialogComposeView] and no construction of a bare `ComposeView(`.
     */
    @Test
    fun `every dialog site installs a compose view that carries the window owners`() {
        val sites = dialogs.windowSitesOf("AndroidDialog(")
        assertTrue("no AndroidDialog( site found at all; the file moved", sites.isNotEmpty())
        for (site in sites) {
            assertTrue(
                "the AndroidDialog( site at offset ${site.offset} must install the helper's " +
                    "ComposeView: ${site.text}",
                site.text.contains("oxideDialogComposeView("),
            )
            // The helper's own name ends in ComposeView(, so the helper call is discounted
            // before looking for a bare construction left behind next to it
            assertFalse(
                "no AndroidDialog( site may build a bare ComposeView( inline; it would have " +
                    "no view-tree owners: ${site.text}",
                site.text.replace(HELPER_CALL, "").contains("ComposeView("),
            )
        }
    }

    /**
     * The helper sets all three owners, and sets the owners *before* the strategy.
     *
     * Order matters for the same reason the strategy choice does: `setContent` on a
     * `ComposeView` is safe to call before attachment, but the owners must already be
     * in the view tree by the time the window traverses. Putting the strategy last
     * is what makes "dispose when the view tree's lifecycle dies" a statement the
     * helper can actually keep.
     */
    @Test
    fun `the helper sets all three view-tree owners and the lifecycle strategy`() {
        val helper = dialogs.callOf("private fun oxideDialogComposeView(")
        for (owner in listOf(
            "setViewTreeLifecycleOwner(",
            "setViewTreeSavedStateRegistryOwner(",
            "setViewTreeViewModelStoreOwner(",
        )) {
            assertTrue("the dialog ComposeView must set $owner: $helper", helper.contains(owner))
        }
        assertTrue(
            "with owners present the strategy can be the view-tree-lifecycle one: $helper",
            helper.contains("ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed"),
        )
        assertTrue(
            "the helper has to call setContent, or the dialog body never composes: $helper",
            helper.contains("setContent("),
        )
    }

    /**
     * `DisposeOnDetachedFromWindow` is gone from the file.
     *
     * Not merely because it is useless here: leaving it in place is what invites
     * the next person to read it as the fix for this crash, which is the exact
     * misreading that shipped v1.8.0.
     */
    @Test
    fun `no dialog site goes back to DisposeOnDetachedFromWindow`() {
        assertFalse(
            "DisposeOnDetachedFromWindow does not avoid the missing-owner crash; " +
                "it decides disposal after onAttachedToWindow has already thrown",
            dialogs.contains("DisposeOnDetachedFromWindow"),
        )
    }

    /**
     * No file may build a `ComposeView` without the three view-tree owners.
     *
     * Walking the whole of `src/main/java` rather than just this one file is the
     * point: the crash class is "a ComposeView attached to a window that has no
     * owners", and it is silent until someone opens that window on a real device. A
     * new `ComposeView` added anywhere — a native bridge, a plugin host, a
     * service-hosted window — would reproduce it with no other test noticing.
     */
    @Test
    fun `every ComposeView in main sources sits in a file that sets all three owners`() {
        val owners = listOf(
            "setViewTreeLifecycleOwner(",
            "setViewTreeSavedStateRegistryOwner(",
            "setViewTreeViewModelStoreOwner(",
        )
        val offenders = mutableListOf<String>()
        var inspected = 0
        for (file in mainJavaFiles()) {
            val source = codeOf(file.readText())
            if (!source.contains("ComposeView(")) continue
            inspected++
            val missing = owners.filterNot { source.contains(it) }
            if (missing.isNotEmpty()) {
                offenders += "${file.path} (missing ${missing.joinToString()})"
            }
        }
        assertTrue("no ComposeView( found under src/main/java; the walk is broken", inspected > 0)
        assertTrue(
            "every file that builds a ComposeView must also set the three view-tree " +
                "owners, or attaching it crashes with ViewTreeLifecycleOwner not found: $offenders",
            offenders.isEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Every balanced statement containing [anchor].
     *
     * Scoped per site rather than per file because the assertion is about a
     * particular `AndroidDialog(` and its `setContentView`, not about the file: a
     * third, unrelated dialog added later must be covered by this test too, not
     * silently escape it because the first one happened to pass.
     */
    private fun String.windowSitesOf(anchor: String): List<WindowSite> {
        val sites = mutableListOf<WindowSite>()
        var index = indexOf(anchor)
        while (index >= 0) {
            val end = endOfEnclosingBody(index)
            if (end < 0) error("unbalanced function body after offset $index")
            sites += WindowSite(offset = index, text = substring(index, end))
            index = indexOf(anchor, end)
        }
        return sites
    }

    /**
     * The index of the `}` that closes the function body containing [from].
     *
     * At a top-level statement like `val host = AndroidDialog(context)` the brace
     * depth is zero, so the next unmatched `}` is the end of the function. Inside
     * a `setContent { ... }` the depth rises first, which is what lets the caller
     * see the whole body rather than the first lambda in it.
     */
    private fun String.endOfEnclosingBody(from: Int): Int {
        var depth = 0
        var index = from
        while (index < length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    if (depth == 0) return index
                    depth--
                }
            }
            index++
        }
        return -1
    }

    /**
     * The body of the function whose declaration starts at [declarationStart].
     *
     * Takes the text between the declaration's parameter list and its closing brace
     * at depth 0, so the assertions cannot be satisfied by an unrelated call later
     * in the file. The class has no locals with braces of their own at depth 0.
     */
    private fun String.callOf(declarationStart: String): String {
        val start = indexOf(declarationStart)
        assertTrue("could not find `$declarationStart`", start >= 0)
        val bodyStart = balancedEndFrom(indexOf('(', start))
        assertTrue("could not find the declaration's parameter list", bodyStart >= 0)
        var depth = 0
        var index = bodyStart + 1
        while (index < length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(bodyStart + 1, index)
                }
            }
            index++
        }
        error("unbalanced function body after $declarationStart")
    }

    /**
     * The index of the `)` closing the `(` at [open], or -1.
     *
     * String literals and comments have already been blanked by [codeOf], so a `(`
     * inside a URL or a KDoc cannot shift the count.
     */
    private fun balancedEndFrom(open: Int): Int {
        var depth = 0
        var index = open
        while (index < length) {
            when (this[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }

    /**
     * Strip string literals and comments, leaving only what gets compiled.
     *
     * Order matters: strings first, then comments. A `//` inside a string literal
     * would otherwise start a line comment and swallow the rest of the file, which
     * turns a genuine failure into a silent pass. The KDoc above this class names
     * the old strategy on purpose, so the guard has to read code, not prose.
     */
    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /**
     * Find a file by its path under the module's `src/main`.
     *
     * Kotlin lives under `java/dev/oxide/launcher`, resources under `res`, so a
     * source path and a resource path do not share one prefix. A unit test's
     * working directory is not necessarily the module root either, so walk up.
     * Failure is an error rather than a skip — a skipped guard is not a guard.
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

    /**
     * The module's `src/main/java` root, found with the same walk-up as [locate].
     *
     * Eight levels covers any of the module root, the repository root, `app/` and
     * a Gradle test working directory underneath it.
     */
    private fun mainJavaRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java")
            if (candidate != null && candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        error("could not locate src/main/java from ${File("").absolutePath}")
    }

    /** Every `.kt` file under `src/main/java`, sorted so failures are reproducible. */
    private fun mainJavaFiles(): List<File> = mainJavaRoot()
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .toList()

    /** One `AndroidDialog(` and everything up to the `)` that closes it. */
    private class WindowSite(val offset: Int, val text: String)

    private companion object {
        /** The helper call, discounted before scanning a site for a bare `ComposeView(`. */
        const val HELPER_CALL = "oxideDialogComposeView("

        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}