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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingFacts
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingOverlay
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingState
import dev.oxide.launcher.ui.screens.main.oxide.OxideInstanceErrorRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideLaunchPreflight
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxidePreflightAction
import dev.oxide.launcher.ui.screens.main.oxide.OxidePreflightAsk
import dev.oxide.launcher.ui.screens.main.oxide.OxidePreflightBranch
import dev.oxide.launcher.ui.screens.main.oxide.OxidePreflightMessage
import dev.oxide.launcher.ui.screens.main.oxide.OxidePreflightTexts
import dev.oxide.launcher.ui.screens.main.oxide.OxideTaskDrawer
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.viewmodel.ErrorViewModel
import org.junit.Rule
import org.junit.Test

/**
 * The three transient surfaces that sit on top of a page: the task drawer, the game-waiting card and
 * the launch pre-flight block.
 *
 * All three already had `internal` visibility and take plain data — a `List<Task>`, a facts data
 * class, and an ask/texts pair — so none of them needed a seam. They are grouped together because
 * they share the two properties that make them worth a golden:
 *
 * - **They must not stop the world.** The drawer scrolls an unbounded task list, the pre-flight
 *   block scrolls so its last button survives a 360dp-tall window, and the waiting card is a
 *   fixed-width panel over live game output. Each of those has bitten this launcher before, and
 *   each is a size problem rather than a logic problem.
 *
 * - **They must not lie about progress.** `progress = -1f` means "unknown" everywhere in the task
 *   model, not 0%, so the empty progress slot is a real state and gets its own golden.
 */
class OxideOverlaysSnapshotTest {

    /**
     * Animations must be at their end state before the frame is drawn.
     *
     * Oxide's shell is built out of `OxideReveal`, a staggered fade-and-rise, and several
     * overlays open with `AnimatedVisibility`. Paparazzi snapshots at t=0, so without this
     * rule every one of those would be captured mid-flight and the golden would differ by a
     * frame depending on how fast the machine is. See `OxidePaparazzi`.
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    // -----------------------------------------------------------------------
    // Task drawer
    // -----------------------------------------------------------------------

    /**
     * Three tasks: one running with a known rate, one running with indeterminate progress, one
     * already finished. The middle one is the interesting row: `Task.updateProgress(-1f)` keeps the
     * negative value, and `oxideTaskProgressPercent` maps it to `null` so no percentage is printed.
     */
    @Test
    fun TaskDrawer_Running() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TaskDrawer_Running", device) { metrics ->
            OxideTaskDrawer(
                tasks = listOf(
                    OxideFake.task(
                        id = "download-mods",
                        progress = 0.42f,
                        stage = TaskStage.RUNNING,
                        message = "Sodium 0.5.8",
                        bytesPerSec = 1_048_576L,
                    ),
                    OxideFake.task(
                        id = "resolve-files",
                        progress = -1f,
                        stage = TaskStage.PREPARING,
                        message = "Asking Modrinth which versions match",
                    ),
                    OxideFake.task(
                        id = "import-world",
                        progress = 1f,
                        stage = TaskStage.COMPLETED,
                        message = "Imported world 'Kelp Harbour'",
                    ),
                ),
                metrics = metrics,
                onDismiss = {},
            )
        }
    }

    @Test
    fun TaskDrawer_Empty() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TaskDrawer_Empty", device) { metrics ->
            OxideTaskDrawer(
                tasks = emptyList(),
                metrics = metrics,
                onDismiss = {},
            )
        }
    }

    /** The drawer at the compact width: `drawerWidth` is min(520dp, 44vw), so 640dp gives ~282dp. */
    @Test
    fun TaskDrawer_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("TaskDrawer_Compact", device) { metrics ->
            OxideTaskDrawer(
                tasks = listOf(
                    OxideFake.task(
                        id = "download-mods",
                        progress = 0.42f,
                        message = "Sodium 0.5.8",
                        bytesPerSec = 1_048_576L,
                    ),
                ),
                metrics = metrics,
                onDismiss = {},
            )
        }
    }

    // -----------------------------------------------------------------------
    // Game waiting
    // -----------------------------------------------------------------------

    /**
     * Waiting for the first frame.
     *
     * `startedAtMillis = null` on purpose: a non-null value starts a `LaunchedEffect` that ticks
     * once a second off `System.currentTimeMillis()`, which would put a running clock in the golden.
     * With `null` the elapsed row is absent entirely, which is the documented meaning of "the host
     * gave us no start time" rather than a fabricated zero.
     */
    @Test
    fun GameWaiting_Waiting() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("GameWaiting_Waiting", device) { metrics ->
            OxideGameWaitingOverlay(
                metrics = metrics,
                facts = OxideGameWaitingFacts(
                    state = OxideGameWaitingState.Waiting,
                    instanceName = "1.20.1-forge-47.3.0",
                    minecraftVersion = "1.20.1",
                    loaderLabel = "Forge 47.3.0",
                    startedAtMillis = null,
                    nowMillis = 0L,
                ),
                visible = true,
                onClose = {},
            )
        }
    }

    /**
     * First frame seen, and this time the host can end the process.
     *
     * The extra Cancel button only exists because `onCancel` was supplied — the card never renders a
     * key that would do nothing, so both the one-button and two-button forms are worth pinning.
     */
    @Test
    fun GameWaiting_FirstFrame_Cancellable() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("GameWaiting_FirstFrame_Cancellable", device) { metrics ->
            OxideGameWaitingOverlay(
                metrics = metrics,
                facts = OxideGameWaitingFacts(
                    state = OxideGameWaitingState.FirstFrame,
                    instanceName = "1.20.1-forge-47.3.0",
                    minecraftVersion = "1.20.1",
                    loaderLabel = "Forge 47.3.0",
                    startedAtMillis = null,
                    nowMillis = 0L,
                ),
                visible = true,
                onClose = {},
                onCancel = {},
            )
        }
    }

    /** Version manifest not read yet: both nullable fact rows drop out and the card shrinks. */
    @Test
    fun GameWaiting_UnknownVersion() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("GameWaiting_UnknownVersion", device) { metrics ->
            OxideGameWaitingOverlay(
                metrics = metrics,
                facts = OxideGameWaitingFacts(
                    state = OxideGameWaitingState.Waiting,
                    instanceName = "brand-new-instance",
                    minecraftVersion = null,
                    loaderLabel = null,
                    startedAtMillis = null,
                    nowMillis = 0L,
                ),
                visible = true,
                onClose = {},
            )
        }
    }

    // -----------------------------------------------------------------------
    // Launch pre-flight
    // -----------------------------------------------------------------------

    /**
     * "Launch anyway".
     *
     * The block is `fillMaxSize` on the asking branch and wraps in a vertical scroll, so it is given
     * the whole window here — that is how `OxideLaunchPage` hosts it.
     */
    @Test
    fun Launch_Preflight_UnsupportedRenderer() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Launch_Preflight_UnsupportedRenderer", device) { metrics ->
            OxideLaunchPreflight(
                metrics = metrics,
                ask = OxidePreflightAsk(
                    branch = OxidePreflightBranch.UnsupportedRenderer,
                    message = OxidePreflightMessage.UnsupportedRenderer,
                    actions = listOf(OxidePreflightAction.LaunchAnyway, OxidePreflightAction.Abort),
                    requiresPasswordInput = false,
                ),
                texts = OxidePreflightTexts(
                    title = "VirGL does not support Minecraft 1.21",
                    detail = "This version needs a newer graphics driver than VirGL provides.",
                ),
                onAction = {},
            )
        }
    }

    /**
     * The relogin branch, which is the only one that asks for input.
     *
     * Three buttons here (submit / launch anyway / cancel) is the case the scroll exists for: on a
     * 640x360 window the third button used to fall off the bottom.
     */
    @Test
    fun Launch_Preflight_ReloginPassword() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Launch_Preflight_ReloginPassword", device) { metrics ->
            OxideLaunchPreflight(
                metrics = metrics,
                ask = OxidePreflightAsk(
                    branch = OxidePreflightBranch.AccountReloginPassword,
                    message = OxidePreflightMessage.ReloginPassword,
                    actions = listOf(
                        OxidePreflightAction.SubmitPassword,
                        OxidePreflightAction.LaunchAnyway,
                        OxidePreflightAction.Abort,
                    ),
                    requiresPasswordInput = true,
                ),
                texts = OxidePreflightTexts(
                    title = "Sign in again to launch",
                    detail = "The token for 'Alex' on example.test expired.",
                    failure = "Mojang rejected the stored refresh token.",
                ),
                onAction = {},
                password = "",
                onPasswordChange = {},
            )
        }
    }

    /**
     * The notice-only branch.
     *
     * `oxidePreflightIsNoticeOnly` is true when the only action is Abort, so the whole button row
     * collapses into a single error row with its own dismiss action. Rendering a button row here
     * would be a regression, not a styling choice.
     */
    @Test
    fun Launch_Preflight_NoticeOnly() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Launch_Preflight_NoticeOnly", device) { metrics ->
            OxideLaunchPreflight(
                metrics = metrics,
                ask = OxidePreflightAsk(
                    branch = OxidePreflightBranch.InvalidVersionName,
                    message = OxidePreflightMessage.InvalidVersionName,
                    actions = listOf(OxidePreflightAction.Abort),
                    requiresPasswordInput = false,
                ),
                texts = OxidePreflightTexts(
                    title = "This instance name cannot be used",
                    detail = "An instance folder cannot contain any of /\\\\:*?\"<>|",
                ),
                onAction = {},
            )
        }
    }

    /**
     * Busy state.
     *
     * `texts.busy` is what disables the input *and* every button while a sign-in is in flight, so it
     * is a distinct rendering rather than the same one with a spinner.
     */
    @Test
    fun Launch_Preflight_Busy() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Launch_Preflight_Busy", device) { metrics ->
            OxideLaunchPreflight(
                metrics = metrics,
                ask = OxidePreflightAsk(
                    branch = OxidePreflightBranch.AccountReloginPassword,
                    message = OxidePreflightMessage.ReloginPassword,
                    actions = listOf(
                        OxidePreflightAction.SubmitPassword,
                        OxidePreflightAction.Abort,
                    ),
                    requiresPasswordInput = true,
                ),
                texts = OxidePreflightTexts(
                    title = "Sign in again to launch",
                    detail = "The token for 'Alex' on example.test expired.",
                    busy = true,
                ),
                onAction = {},
                password = "hunter2-placeholder",
                onPasswordChange = {},
            )
        }
    }

    // -----------------------------------------------------------------------
    // Instances: the in-page error row
    // -----------------------------------------------------------------------

    /**
     * The row the instances page shows when a rename/delete/duplicate fails.
     *
     * This is the only part of the instances grid that is renderable without a real install: the
     * cards themselves need a `Version`, which means an actual Minecraft directory on disk plus a
     * parsed `version.json`, an MMKV-backed pinned flag and a probe pass over the mods folder. The
     * error row is the piece that regressed in review most often, because it is the only thing on
     * that page with two lines of text and a dismiss control.
     */
    @Test
    fun Instances_InstallError() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Instances_InstallError", device) { metrics ->
            ErrorRow(metrics)
        }
    }

    @Test
    fun Instances_InstallError_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Instances_InstallError_Compact", device) { metrics ->
            ErrorRow(metrics)
        }
    }

    @Composable
    private fun ErrorRow(metrics: OxideMetrics) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.cardGap),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OxideInstanceErrorRow(
                message = ErrorViewModel.ThrowableMessage(
                    title = androidText("Could not delete the instance"),
                    message = androidText("EACCES: /storage/emulated/0/games/1.20.1-forge"),
                ),
                onDismiss = {},
            )
        }
    }
}