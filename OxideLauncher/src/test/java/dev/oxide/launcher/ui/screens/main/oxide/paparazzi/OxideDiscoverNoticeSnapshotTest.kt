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

package dev.oxide.launcher.ui.screens.main.oxide.paparazzi

import androidx.compose.runtime.Composable
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverDependenciesState
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverDependencySection
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverInstallNotice
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverNoticeRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * The two states the discover page could not render under layoutlib before this pass.
 *
 * **The install notice.** `DiscoverInstallNotice` used to take `DiscoverInstall`, a private sealed
 * type that reaches straight into the platform HTTP layer (`PlatformVersion` inside
 * `DiscoverInstallSheet`). Nothing on that path can be constructed on the JVM, so the one screen
 * where a user most obviously watches for a wrong message — "Queued for download" sitting there
 * forever — had no golden at all. `DiscoverNoticeRow` is the extracted presentational shape: two
 * strings, one `AndroidStringText`, one optional fraction. That is all the composable draws, so it
 * is all a test has to supply.
 *
 * **The honest dependency state.** `DiscoverDependenciesState.NoFileForTarget` is new this pass and
 * is the fix for "the tab says there are dependencies, Install says there are none": when the target
 * instance has no compatible file, the panel now says *that* rather than "This file has no
 * dependencies." Widening `DiscoverDependenciesState` to `internal` was enough — its rows are plain
 * data (a dependency card, a `DependencyRequest`, a selection flag), with no `PlatformVersion`
 * anywhere, which is why this seam was open while `DiscoverInstallSheetHost` was not.
 *
 * `DiscoverInstallSheetHost` is still excluded for the reason recorded in
 * `OxideDiscoverSnapshotTest`: its state carries a raw `PlatformVersion` plus a resolved
 * `DiscoverTarget`, so faking it would mean asserting a layout against data the production parser
 * can never produce.
 *
 * The fractions here are the values the real `Task.progress` StateFlow reports: `0.42` is a literal
 * download fraction, and the indeterminate case is `null` because `-1f` means "the pipeline stopped
 * publishing a fraction while it copies the file into the instance". The bar renders an empty track
 * for `null` rather than inventing a number.
 */
class OxideDiscoverNoticeSnapshotTest {

    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    @Composable
    private fun Notice(metrics: OxideMetrics, notice: DiscoverNoticeRow) {
        DiscoverInstallNotice(
            notice = notice,
            action = null,
            onDismiss = {},
            metrics = metrics,
        )
    }

    /** Handed to the task system, nothing downloaded yet: the bar is there but empty. */
    @Test
    fun Discover_InstallNotice_Queued() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_InstallNotice_Queued", device) { metrics ->
            Notice(
                metrics,
                DiscoverNoticeRow(
                    title = "Queued for download",
                    detail = androidText("The download was handed to the task system."),
                    progress = 0f,
                ),
            )
        }
    }

    /**
     * Mid-download, at a real fraction.
     *
     * `0.42` is the value the reducer carries verbatim from `Task.progress`; this golden is what
     * would catch someone substituting a smoothed or simulated number for it.
     */
    @Test
    fun Discover_InstallNotice_Downloading() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_InstallNotice_Downloading", device) { metrics ->
            Notice(
                metrics,
                DiscoverNoticeRow(
                    title = "Queued for download",
                    detail = androidText(
                        "Downloading and installing assets\nsodium-fabric-0.6.0.jar\n (6.2 MB / 14.8 MB)"
                    ),
                    progress = 0.42f,
                ),
            )
        }
    }

    /**
     * A dependency with no compatible version for the selected instance.
     *
     * Before this pass this rendered `Empty`, i.e. "This file has no dependencies." — which is a
     * different claim, and the reason the install sheet and the details tab could contradict each
     * other for the same project.
     */
    @Test
    fun Discover_Dependencies_NoFileForTarget() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_Dependencies_NoFileForTarget", device) { metrics ->
            DiscoverDependencySection(
                state = DiscoverDependenciesState.NoFileForTarget(
                    androidText("No file of this project supports 1.21.1.")
                ),
                showDownloadAll = false,
                busy = false,
                onToggle = { _, _ -> },
                onRetry = {},
                onDownloadAll = {},
                metrics = metrics,
            )
        }
    }

    /** The compact width is where the notice runs out of room for a long detail line first. */
    @Test
    fun Discover_InstallNotice_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Discover_InstallNotice_Compact", device) { metrics ->
            Notice(
                metrics,
                DiscoverNoticeRow(
                    title = "Queued for download",
                    detail = androidText(
                        "Downloading and installing assets\nsodium-fabric-0.6.0.jar\n (6.2 MB / 14.8 MB)"
                    ),
                    progress = 0.42f,
                ),
            )
        }
    }

    /**
     * The failure notice.
     *
     * A failed download resolves the queue just like a finished one, so the message the user reads
     * is the installer's own error rather than a permanently stuck "Queued for download".
     */
    @Test
    fun Discover_InstallNotice_Failed() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_InstallNotice_Failed", device) { metrics ->
            Notice(
                metrics,
                DiscoverNoticeRow(
                    title = "Install failed",
                    detail = androidText("The CurseForge API key was rejected (HTTP 403)"),
                    progress = null,
                ),
            )
        }
    }
}
