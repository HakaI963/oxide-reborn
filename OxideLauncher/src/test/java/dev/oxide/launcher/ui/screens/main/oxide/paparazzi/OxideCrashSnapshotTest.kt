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

package dev.oxide.launcher.ui.screens.main.oxide.paparazzi

import dev.oxide.launcher.ui.activities.CrashType
import dev.oxide.launcher.ui.screens.main.OxideCrashPanelHost
import dev.oxide.launcher.ui.screens.main.OxideCrashReport
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxideCrashReport
import dev.oxide.launcher.ui.screens.main.oxideCrashTraceLineCount
import dev.oxide.launcher.ui.screens.main.oxideCrashTypeName
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.paparazziFor
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import androidx.compose.runtime.Composable
import app.cash.paparazzi.InstantAnimationsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The crash surface, rebuilt on the Oxide panel system.
 *
 * Three goldens, each aimed at one of the things that were wrong on the old screen.
 *
 * The launcher crash is the common case and the one in the bug report: `Crash Type:
 * Launcher Crash` in the panel title, the full fatal-error headline as the first body
 * line, a monospace cause, a monospace trace, and the three surviving actions. The old
 * version had none of the Oxide structure — a grey rounded text block and three
 * full-width salmon pills.
 *
 * `Crash_LongTrace` is the load-bearing one. It renders a trace far taller than the
 * panel and the golden records that the panel **stops at its own max height and the
 * overflow scrolls inside it**, instead of growing past the bottom of the screen. If a
 * future change swaps the bound for something unbounded, or reintroduces `maxLines`,
 * this golden changes — and so does `OxideCrashSurfaceTest`, which asserts the same thing
 * on the pure model.
 *
 * Every test goes through `OxideCrashPanelHost` with `metrics` handed in by `shot`, so the
 * size rendered is the size asserted; nothing here calls `oxideMetricsFor` itself.
 */
class OxideCrashSnapshotTest {

    /**
     * Animations must land on their end state before the frame is drawn.
     *
     * Paparazzi snapshots at t=0, so without this every fade-in and stagger would be
     * caught mid-flight and the golden would differ by a frame depending on machine
     * speed. See `OxidePaparazzi`.
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    @Test
    fun Crash_Launcher() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Crash_Launcher", device) { metrics ->
            host(launcherCrash(), metrics)
        }
    }

    @Test
    fun Crash_Launcher_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Crash_Launcher_Compact", device) { metrics ->
            host(launcherCrash(), metrics)
        }
    }

    /**
     * A trace far taller than the panel.
     *
     * 400 frames is deliberately more than fits at either device size: the point is that
     * the panel is bounded and the body scrolls, not that a particular number of lines is
     * visible. The golden should look the same at the top whether the trace is 20 lines or
     * 400 — that is the property.
     */
    @Test
    fun Crash_LongTrace() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Crash_LongTrace", device) { metrics ->
            host(launcherCrash(trace = longTrace(400)), metrics)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Composable
    private fun host(report: OxideCrashReport, metrics: OxideMetrics) {
        OxideCrashPanelHost(
            report = report,
            traceLabel = "Launcher crash log",
            metrics = metrics,
            shareLinkText = "Share Link",
            shareLogsText = "Share Logs",
            restartText = "Restart",
            exitText = "Exit",
            rotateDescription = "Expand",
            onShareLinkClick = {},
            onShareLogsClick = {},
            onRestartClick = {},
            onExitClick = {},
            onRotateClick = {},
        )
    }

    /**
     * A launcher crash shaped like the one in the report.
     *
     * The strings are the resolved forms of the `crash_*` resources, spelled out here
     * because a golden must not depend on the string table resolving the same way on
     * every machine — Paparazzi pins the locale precisely for that reason.
     */
    private fun launcherCrash(trace: String = realTrace()): OxideCrashReport =
        oxideCrashReport(
            crashType = CrashType.LAUNCHER_CRASH,
            title = "Oxide Launcher has encountered a fatal error",
            kind = "Crash Type: Launcher Crash",
            summary = "The application crashed unexpectedly. " +
                "The error details below will help developers improve the app.",
            throwable = IllegalStateException("surface is closed"),
            trace = trace,
            shareLogs = true,
            // Only a game crash has a log worth uploading
            canUpload = false,
            canRestart = true,
        )

    /** The exception chain and trace from the bug report, in the shape `printStackTrace` emits. */
    private fun realTrace(): String = buildString {
        append("android.app.RemoteServiceException\$ForegroundServiceDidNotStartInTimeException: ")
        append("Context.startForegroundService() did not then call Service.startForeground(): ")
        append("ServiceRecord{10a68cc u0 dev.oxide.launcher.debug/")
        append("dev.oxide.launcher.keepalive.TaskKeepAliveService c:dev.oxide.launcher.debug}\n")
        append("\tat android.app.ActivityThread.generateForegroundServiceDidNotStartInTimeException(ActivityThread.java:2649)\n")
        append("\tat android.app.ActivityThread.throwRemoteServiceException(ActivityThread.java:2617)\n")
        append("\tat android.app.ActivityThread.-\$Nest\$mthrowRemoteServiceException(Unknown Source:0)\n")
        append("\tat android.app.ActivityThread\$H.handleMessage(ActivityThread.java:3003)\n")
        append("\tat android.os.Handler.dispatchMessage(Handler.java:110)\n")
        append("\tat android.os.Looper.loopOnce(Looper.java:265)\n")
        append("\tat android.os.Looper.loop(Looper.java:358)\n")
        append("\tat android.app.ActivityThread.main(ActivityThread.java:10049)\n")
        append("\tat java.lang.reflect.Method.invoke(Native Method)\n")
        append("\tat com.android.internal.os.RuntimeInit\$MethodAndArgsCaller.run(RuntimeInit.java:616)\n")
        append("\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1115)\n")
        append("Caused by: java.lang.IllegalStateException: surface is closed\n")
        append("\tat dev.oxide.launcher.keepalive.TaskKeepAliveService.onStartCommand(TaskKeepAliveService.kt:88)\n")
        append("\tat dev.oxide.launcher.keepalive.TaskKeepAliveService.onCreate(TaskKeepAliveService.kt:41)\n")
        append("\tSuppressed: java.lang.NullPointerException: cleanup failed too\n")
        append("\t\tat dev.oxide.launcher.keepalive.TaskKeepAliveService.dispose(TaskKeepAliveService.kt:120)\n")
    }

    /** A trace of [frames] frames, for the scrollability golden. */
    private fun longTrace(frames: Int): String = buildString {
        append("java.lang.IllegalStateException: surface is closed\n")
        for (index in 0 until frames) {
            append("\tat dev.oxide.launcher.frame.Frame$index(Frame.kt:$index)\n")
        }
    }

    /**
     * Referenced so the monospace helper set stays honest about what it is for.
     *
     * The trace face is the one thing these goldens cannot check on their own — a wrong
     * face is a wrong font, not a wrong shape — and this is the type the panel uses for
     * the trace and the cause. Kept as an assertion rather than a comment because a
     * comment asserting "this is monospace" is worth nothing.
     */
    @Test
    fun theCauseNamesATypeTheTraceFaceCanRender() {
        val cause = oxideCrashTypeName(IllegalStateException("surface is closed"))

        assertEquals("IllegalStateException", cause)
        // Latin-1 only: the monospace face carries no CJK, and a cause line that renders
        // as tofu is not a cause line
        assertTrue("the type name must be plain ASCII: $cause", cause.all { it.code < 128 })
    }

    @Test
    fun theLongTraceReallyIsLong() {
        val trace = longTrace(400)

        assertEquals(401, oxideCrashTraceLineCount(trace))
    }
}