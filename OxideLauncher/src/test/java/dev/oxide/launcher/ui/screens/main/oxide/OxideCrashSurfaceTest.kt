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

import dev.oxide.launcher.ui.activities.CrashType
import dev.oxide.launcher.ui.screens.main.OxideCrashReport
import dev.oxide.launcher.ui.screens.main.oxideCrashCauseChain
import dev.oxide.launcher.ui.screens.main.oxideCrashCauseLabel
import dev.oxide.launcher.ui.screens.main.oxideCrashReport
import dev.oxide.launcher.ui.screens.main.oxideCrashTraceLineCount
import dev.oxide.launcher.ui.screens.main.oxideCrashTypeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crash surface's model: the root cause, the whole exception chain, the whole trace.
 *
 * These are the three things the user reported losing on the old crash screen, and all
 * three are facts about a **pure function** — `oxideCrashReport` reads nothing but its
 * arguments, composes nothing and touches no Android API. So they can be pinned here
 * without a device, a Compose runtime or a single assertion on a golden.
 *
 * The old surface failed in a specific way that is worth stating, because it is what
 * the assertions below are aimed at: the trace was one `Text` with
 * `MaterialTheme.typography.bodyMedium` in a `Column` with no height bound, so a long
 * trace simply pushed the page past the bottom of the screen and the frames the user
 * actually needed were the ones that went missing. Truncating it was never an option
 * either — a crash report that drops the tail is a crash report nobody can act on.
 */
class OxideCrashSurfaceTest {

    // -----------------------------------------------------------------------
    // The whole chain survives
    // -----------------------------------------------------------------------

    @Test
    fun `the chain is walked all the way to the deepest cause`() {
        val root = IllegalStateException("surface is closed")
        val middle = RuntimeException("cannot bind the config watcher", root)
        val top = IllegalArgumentException("bad launch token", middle)

        val chain = oxideCrashCauseChain(top)

        assertEquals(
            "every level must be kept, outermost first",
            listOf(top, middle, root),
            chain,
        )
    }

    @Test
    fun `a chain of one is still a chain`() {
        val only = RuntimeException("alone")

        assertEquals(listOf(only), oxideCrashCauseChain(only))
        // one link is not a chain, so no "N in chain" note
        assertEquals("RuntimeException: alone", oxideCrashCauseLabel(oxideCrashCauseChain(only)))
    }

    @Test
    fun `no throwable means no chain and no cause line`() {
        // A game crash is the JVM being taken away: there is no serialisable exception,
        // so the cause line has to disappear entirely rather than show a placeholder
        assertTrue(oxideCrashCauseChain(null).isEmpty())
        assertEquals("", oxideCrashCauseLabel(emptyList()))
    }

    /**
     * The reported root cause is the **deepest** one, not the outermost wrapper.
     *
     * This is the whole point of the cause line: `IllegalArgumentException: bad launch
     * token` tells the reader nothing, while `IllegalStateException: surface is closed`
     * tells them exactly which file to open.
     */
    @Test
    fun `the cause names the deepest throwable, not the wrapper`() {
        val root = IllegalStateException("surface is closed")
        val top = IllegalArgumentException("bad launch token", root)

        val report = report(throwable = top)

        assertEquals("IllegalStateException: surface is closed", report.cause)
        assertFalse(
            "the wrapper must not be what the cause line names",
            report.cause.contains("bad launch token"),
        )
        assertTrue("the chain length is reported alongside", report.cause.contains("2 in chain"))
    }

    @Test
    fun `a cause with no message still names its type`() {
        // `new RuntimeException()` leaves `message` null; an empty line reads like a
        // rendering bug, so the type alone has to carry it
        val report = report(throwable = RuntimeException())

        assertEquals("RuntimeException", report.cause)
    }

    @Test
    fun `a blank message is treated as no message`() {
        val report = report(throwable = RuntimeException("   "))

        assertEquals("RuntimeException", report.cause)
    }

    @Test
    fun `anonymous throwables fall back to the qualified name`() {
        // `simpleName` is the empty string for an anonymous class; leaving the type
        // blank there would be worse than printing something long
        val anonymous = object : RuntimeException("anonymous boom") {}

        assertEquals(
            anonymous.javaClass.name,
            oxideCrashTypeName(anonymous),
        )
        assertTrue(
            "the fallback must be non-blank",
            oxideCrashTypeName(anonymous).isNotBlank(),
        )
    }

    /**
     * Suppressed exceptions are counted.
     *
     * They are part of `printStackTrace` output and frequently where the real cause is
     * hiding, so the report has to admit that it saw some.
     */
    @Test
    fun `suppressed exceptions are counted in the cause line`() {
        val root = IllegalStateException("surface is closed")
        root.addSuppressed(IllegalArgumentException("cleanup failed too"))
        root.addSuppressed(IllegalArgumentException("and again"))

        val report = report(throwable = RuntimeException("wrapper", root))

        assertTrue(
            "expected the suppressed count in: ${report.cause}",
            report.cause.contains("2 suppressed"),
        )
    }

    @Test
    fun `a cause with neither a chain nor suppressed exceptions carries no parenthesis`() {
        val report = report(throwable = IllegalStateException("plain"))

        assertEquals("IllegalStateException: plain", report.cause)
        assertFalse(report.cause.contains("("))
    }

    // -----------------------------------------------------------------------
    // A cause chain must never hang the crash screen
    // -----------------------------------------------------------------------

    /**
     * A cycle in `cause` stops instead of spinning.
     *
     * `initCause` refuses to make a throwable its own cause, but nothing stops two of them
     * pointing at each other — and this code runs while the app is already on its way down,
     * where a hang is strictly worse than a short chain.
     */
    @Test
    fun `a cycle in the cause chain terminates`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        val chain = oxideCrashCauseChain(first)

        // first -> second -> first, and the repeat is what the guard has to catch
        assertEquals(2, chain.size)
        assertEquals(first, chain[0])
        assertEquals(second, chain[1])
    }

    /**
     * A throwable that reports itself as its own cause yields a chain of one.
     *
     * `initCause` refuses that outright, so the only way in is a subclass overriding
     * `getCause` — a proxy exception, or one rebuilt by a serialisation round trip. The
     * guard has to hold there too, since the walk is on a live crash path.
     */
    @Test
    fun `a self-reported cause yields a chain of one`() {
        val selfish = object : RuntimeException("self") {
            override val cause: Throwable get() = this
        }

        assertEquals(listOf(selfish), oxideCrashCauseChain(selfish))
    }

    @Test
    fun `a very deep chain is cut at the documented limit`() {
        var current: Throwable = IllegalStateException("root")
        repeat(200) {
            current = RuntimeException("level $it", current)
        }

        val chain = oxideCrashCauseChain(current)

        assertEquals(
            "the walk must stop at the limit rather than grow without bound",
            OXIDE_CRASH_CAUSE_LIMIT,
            chain.size,
        )
    }

    // -----------------------------------------------------------------------
    // The trace comes back verbatim
    // -----------------------------------------------------------------------

    @Test
    fun `a very long trace is returned verbatim`() {
        // 10_000 lines is the case the user actually reported
        val lines = (0 until 10_000).map { "at dev.oxide.launcher.Frame$it(Frame.kt:$it)" }
        val trace = lines.joinToString("\n")

        val report = report(trace = trace)

        assertEquals("the trace must not be re-flowed", trace, report.trace)
        assertEquals(10_000, report.traceLineCount)
    }

    @Test
    fun `every line of a long trace is still there`() {
        val lines = (0 until 10_000).map { "frame $it" }
        val trace = lines.joinToString("\n")

        val report = report(trace = trace)

        // A truncation bug shows up as a missing tail long before it shows up as a
        // length mismatch, so assert the last line is reachable
        assertTrue(report.trace.contains("frame 9999"))
        assertTrue(report.trace.contains("frame 5000"))
        assertTrue(report.trace.contains("frame 0"))
    }

    @Test
    fun `a trailing newline does not invent an extra line`() {
        // Three real lines and a terminator is three lines, not four
        assertEquals(3, oxideCrashTraceLineCount("a\nb\nc\n"))
        assertEquals(3, oxideCrashTraceLineCount("a\nb\nc"))
        assertEquals(1, oxideCrashTraceLineCount("only"))
        assertEquals(0, oxideCrashTraceLineCount(""))
    }

    @Test
    fun `blank trace lines are preserved rather than squeezed out`() {
        val trace = "java.lang.RuntimeException: boom\n" +
            "\tat a.B.c(B.java:1)\n" +
            "\n" +
            "\tat d.E.f(E.java:2)"

        val report = report(trace = trace)

        assertEquals(trace, report.trace)
        assertEquals(4, report.traceLineCount)
    }

    @Test
    fun `indentation and tabs in the trace survive`() {
        val trace = "\tat a.B.c(B.java:1)\n\t    Suppressed: nested\nCaused by: x"

        assertEquals(trace, report(trace = trace).trace)
    }

    // -----------------------------------------------------------------------
    // Title, kind and the actions
    // -----------------------------------------------------------------------

    /**
     * The title and the `Crash Type: ...` line are passed through untouched.
     *
     * Both are things the user named explicitly, so neither is derived, shortened or
     * reconstructed here — whatever the caller resolved from the string resources is
     * what the panel shows.
     */
    @Test
    fun `title and kind are carried verbatim`() {
        val title = "Oxide Launcher has encountered a fatal error"
        val kind = "Crash Type: Launcher Crash"

        val report = report(title = title, kind = kind)

        assertEquals(title, report.title)
        assertEquals(kind, report.kind)
    }

    @Test
    fun `the summary is carried verbatim too`() {
        val summary = "The application crashed unexpectedly. " +
            "The error details below will help developers improve the app."

        assertEquals(summary, report(summary = summary).summary)
    }

    @Test
    fun `the crash type travels with the report`() {
        assertEquals(
            CrashType.LAUNCHER_CRASH,
            report(crashType = CrashType.LAUNCHER_CRASH).crashType,
        )
        assertEquals(
            CrashType.GAME_CRASH,
            report(crashType = CrashType.GAME_CRASH).crashType,
        )
    }

    /**
     * Action availability follows the flags exactly.
     *
     * Share Logs used to be gated on the crash log file existing and Restart on whether
     * a restart was even possible; both conditions reach the panel as arguments and
     * must arrive at the buttons unaltered. Exit is the one action with no condition —
     * the crash screen is a dead end either way.
     */
    @Test
    fun `action availability matches the flags`() {
        val all = report(shareLogs = true, canUpload = true, canRestart = true)
        assertTrue(all.canShareLogs)
        assertTrue(all.canShareLink)
        assertTrue(all.canRestart)
        assertTrue(all.canExit)

        val none = report(shareLogs = false, canUpload = false, canRestart = false)
        assertFalse(none.canShareLogs)
        assertFalse(none.canShareLink)
        assertFalse(none.canRestart)
        assertTrue("exit is never conditional", none.canExit)
    }

    @Test
    fun `the flags are independent of each other`() {
        // Each gate has its own cause: a missing log file must not also hide Restart
        val noLog = report(shareLogs = false, canUpload = false, canRestart = true)
        assertFalse(noLog.canShareLogs)
        assertFalse(noLog.canShareLink)
        assertTrue(noLog.canRestart)

        val noRestart = report(shareLogs = true, canUpload = true, canRestart = false)
        assertTrue(noRestart.canShareLogs)
        assertTrue(noRestart.canShareLink)
        assertFalse(noRestart.canRestart)
    }

    @Test
    fun `a game crash with no throwable still produces a full report`() {
        val trace = "For details on the cause, please check the log file."

        val report = oxideCrashReport(
            crashType = CrashType.GAME_CRASH,
            title = "Oxide Launcher",
            kind = "Crash Type: Game Crash",
            summary = "JVM exited with code 1.",
            throwable = null,
            trace = trace,
            shareLogs = true,
            canUpload = false,
            canRestart = true,
        )

        assertEquals(CrashType.GAME_CRASH, report.crashType)
        assertEquals("Oxide Launcher", report.title)
        assertEquals("Crash Type: Game Crash", report.kind)
        assertEquals("JVM exited with code 1.", report.summary)
        assertEquals("", report.cause)
        assertEquals(trace, report.trace)
        assertEquals(1, report.traceLineCount)
        assertFalse(report.canShareLink)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A report with sensible defaults, so each assertion states only what it is about. */
    private fun report(
        crashType: CrashType = CrashType.LAUNCHER_CRASH,
        title: String = "Oxide Launcher has encountered a fatal error",
        kind: String = "Crash Type: Launcher Crash",
        summary: String = "The application crashed unexpectedly.",
        throwable: Throwable? = IllegalStateException("surface is closed"),
        trace: String = "java.lang.IllegalStateException: surface is closed\n\tat dev.oxide",
        shareLogs: Boolean = true,
        canUpload: Boolean = true,
        canRestart: Boolean = true,
    ): OxideCrashReport = oxideCrashReport(
        crashType = crashType,
        title = title,
        kind = kind,
        summary = summary,
        throwable = throwable,
        trace = trace,
        shareLogs = shareLogs,
        canUpload = canUpload,
        canRestart = canRestart,
    )
}