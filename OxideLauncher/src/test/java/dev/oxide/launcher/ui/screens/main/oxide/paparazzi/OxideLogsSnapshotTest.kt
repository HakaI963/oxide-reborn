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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide.paparazzi

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.ui.screens.main.oxide.OxideLogTerminalCard
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * The terminal pane's card chrome, at the sizes a log is actually read at.
 *
 * **`SoraEditor` cannot appear in any of these.** It is an `AndroidView`, and layoutlib has no real
 * `View` host to attach one to, so a golden that included it would either come out blank or fail the
 * whole render — which is why no existing golden renders one. So what is under test here is the
 * seam `OxideLogTerminalCard` was extracted for: the opaque card, its padding, the demoted title
 * label, and the "this log was truncated" notice. The body slot is filled with a plain monospace
 * `Text`, which is the closest thing layoutlib can draw to what the editor produces.
 *
 * What these goldens are actually for is the fix in v1.7.0: the terminal card used to be measured
 * with `fillMaxSize()`, so its bottom — including the horizontal scrollbar — was clipped off the
 * window edge, while the sources card on the left stretched its two or three rows over ~580dp of
 * nothing. With the pane taking the remaining height, the terminal card now runs from the header to
 * the bottom padding. `Logs_Pane_Normal` and `Logs_Pane_Compact` are that; `Logs_Pane_Large` is the
 * same card with a long tailed log, which is the case where the truncation notice has to appear
 * *above* the body without stealing the body's room.
 */
class OxideLogsSnapshotTest {

    /**
     * Animations must be at their end state before the frame is drawn. See `OxidePaparazzi`.
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    /**
     * The reference canvas, a log short enough to see all of.
     *
     * 24 lines is roughly what fits without the body scrolling vertically, so this golden is the one
     * that shows the card's own chrome in full: title label on top, log body filling the rest, no
     * truncation notice.
     */
    @Test
    fun Logs_Pane_Normal() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Logs_Pane_Normal", device) { metrics ->
            LogsPane(metrics, OxideFake.logLines(24), tailed = false)
        }
    }

    /**
     * The same card with a long log that had to be tailed.
     *
     * `readLogTail` keeps only the last 8 MiB, and when it does it says so — a one-line notice above
     * the body, at micro-label size. This golden pins that notice in place and pins the body
     * scrolling underneath it: 400 lines into a pane that fits a couple of dozen, so only the head
     * is visible and the remainder has to scroll. It is also the widest golden, because the
     * stack-trace frames are longer than the card, which is exactly the horizontal overflow the
     * editor scrolls for.
     */
    @Test
    fun Logs_Pane_Large() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Logs_Pane_Large", device) { metrics ->
            LogsPane(metrics, OxideFake.logLines(400), tailed = true)
        }
    }

    /**
     * The smallest supported landscape window.
     *
     * 640x360 is where this layout is most likely to break: even after the page padding the card is
     * only ~333dp tall, so any modifier that measures the card against the whole 360dp window
     * instead of what is left of it pushes the bottom — the scrollbar — straight off the edge. That
     * is the regression `Logs_Pane_Compact` exists to catch.
     */
    @Test
    fun Logs_Pane_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Logs_Pane_Compact", device) { metrics ->
            LogsPane(metrics, OxideFake.logLines(24), tailed = false)
        }
    }

    /**
     * The terminal card chrome with a stand-in body, placed as the page places it.
     *
     * `OxidePageColumn` is the real page padding and `weight(1f)` is the real pane height, so the
     * card gets exactly the height the page hands it: a regression that measures it against the
     * window instead of the remaining space moves its bottom edge in the PNG. That is precisely the
     * v1.7.0 bug, and precisely why these three goldens are worth having. The `fillMaxWidth()` is
     * the stacked-layout modifier, which is the one that fully determines the card's bounds.
     */
    @Composable
    private fun LogsPane(
        metrics: OxideMetrics,
        lines: List<String>,
        tailed: Boolean,
    ) {
        OxidePageColumn(metrics = metrics) {
            OxideLogTerminalCard(
                metrics = metrics,
                title = "Latest game log",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                tailed = tailed,
            ) {
                Text(
                    text = lines.joinToString("\n"),
                    // Monospace so the column of stack frames stays aligned, as it does in the editor
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The editor scrolls; this stands in for that, and is what makes 400 lines
                        // render a viewport rather than a 400-row column
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}