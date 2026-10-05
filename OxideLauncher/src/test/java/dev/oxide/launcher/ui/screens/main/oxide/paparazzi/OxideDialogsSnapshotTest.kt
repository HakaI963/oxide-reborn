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

import dev.oxide.launcher.ui.screens.main.oxide.OxideConfirmDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogOption
import dev.oxide.launcher.ui.screens.main.oxide.OxideHostedLinkPanel
import dev.oxide.launcher.ui.screens.main.oxide.OxideListDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideTaskDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideTextEntryDialog
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import app.cash.paparazzi.InstantAnimationsRule
import org.junit.Rule
import org.junit.Test

/**
 * The dialog family, and the one place where the *panel* rather than the screen is the subject.
 *
 * These are the composables the launcher uses for every modal it owns — confirmation, a dropdown
 * of choices, text entry, and a progress dialog — so a regression here hits half the UI at once.
 *
 * Two things are deliberate:
 *
 * - **Every dialog gets `metrics` explicitly.** All four have `metrics: OxideMetrics =
 *   rememberOxideMetrics()` as their last parameter, which reads `LocalConfiguration`. Passing the
 *   metrics that match the device is what keeps "the size under test is the size asserted" true:
 *   the panel's max width and max height are derived from `metrics.guiScale`, so leaving the
 *   default in place would mix a hand-picked device with a derived scale.
 *
 * - **`OxideListDialog` is covered in both of its modes.** `selectable = true` with a `confirmText`
 *   keeps the selection local until confirm, so no row is highlighted on first render;
 *   `selectable = false` renders an action menu with no radio marks. A disabled option is included
 *   because "why can't I press this" is carried entirely by its second line, and that line is
 *   exactly the sort of thing a screenshot test is good at catching.
 *
 * Paparazzi 2.0.0-alpha05.1 gives `Dialog` its own real `ViewRootImpl`, which is what lets a
 * Compose `Dialog` render at all here — in the 1.x line a dialog composable simply did not appear.
 */
class OxideDialogsSnapshotTest {

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

    /**
     * The "Open in Browser" confirmation.
     *
     * `showOxideLinkDialog` itself needs a real Activity window, so this snapshots
     * `OxideHostedLinkPanel` — the exact panel body and footer that function hosts — the
     * same way `OxideConfirmDialog` and the rest of the family are shot. What matters
     * visually is that the link is monospace, that Copy and Cancel are outlined while
     * Confirm is solid, and that the panel carries Oxide's opaque background, radius and
     * hairline border rather than Material's translucent grey card with salmon text
     * buttons.
     */
    @Test
    fun OpenLink_Confirm() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("OpenLink_Confirm", device) {
            OxideHostedLinkPanel(
                title = "Open in Browser",
                link = "https://github.com/ZalithLauncher/NativeLibPlugin/releases",
                confirmText = "Confirm",
                cancelText = "Cancel",
                copyText = "Copy",
                closeDescription = "Close",
                onOpen = {},
                onCopy = {},
                onDismiss = {},
            )
        }
    }

    /**
     * The same dialog on the smallest supported landscape.
     *
     * 640x360 is where the three-button footer is most likely to crowd the link, and it is
     * the size the panel's max width is derived from — so this golden is what would catch
     * the footer wrapping, or the link being pushed out of the content area.
     */
    @Test
    fun OpenLink_Confirm_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("OpenLink_Confirm_Compact", device) {
            OxideHostedLinkPanel(
                title = "Open in Browser",
                link = "https://github.com/ZalithLauncher/NativeLibPlugin/releases",
                confirmText = "Confirm",
                cancelText = "Cancel",
                copyText = "Copy",
                closeDescription = "Close",
                onOpen = {},
                onCopy = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun Confirm_Default() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Confirm_Default", device) { metrics ->
            OxideConfirmDialog(
                title = "Delete instance?",
                message = "This removes 1.20.1-forge-47.3.0 and everything inside it. " +
                    "Saves and worlds are not kept anywhere else.",
                confirmText = "Delete",
                cancelText = "Keep",
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    /** The single-button form: the notice-only variant used for "there is nothing to decide". */
    @Test
    fun Confirm_NoticeOnly() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Confirm_NoticeOnly", device) { metrics ->
            OxideConfirmDialog(
                title = "Invalid instance name",
                message = "An instance folder cannot contain any of /\\\\:*?\"<>|",
                confirmText = "OK",
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    @Test
    fun Confirm_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Confirm_Compact", device) { metrics ->
            OxideConfirmDialog(
                title = "Delete instance?",
                message = "This removes 1.20.1-forge-47.3.0 and everything inside it.",
                confirmText = "Delete",
                cancelText = "Keep",
                onConfirm = {},
                onDismiss = {},
                metrics = metrics,
            )
        }
    }

    /**
     * The dropdown, in selectable mode.
     *
     * `currentKey = null` with a `confirmText` means nothing is pre-selected and Confirm stays
     * disabled — the "pick one before you may continue" state, which is a different golden from
     * the one where a value is already chosen.
     */
    @Test
    fun Dropdown_Selectable() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Dropdown_Selectable", device) { metrics ->
            OxideListDialog(
                title = "Target version",
                options = listOf(
                    OxideDialogOption("1.20.1", "1.20.1", "Released 2023"),
                    OxideDialogOption("1.20.4", "1.20.4", "Released 2024"),
                    OxideDialogOption("1.21.1", "1.21.1", "Released 2024"),
                    OxideDialogOption("1.12.2", "1.12.2", "No file for this loader", enabled = false),
                ),
                onOptionSelected = {},
                onDismiss = {},
                currentKey = null,
                confirmText = "Install",
                metrics = metrics,
            )
        }
    }

    /** The same dropdown with a value already chosen: Confirm is live and the row is marked. */
    @Test
    fun Dropdown_Selected() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Dropdown_Selected", device) { metrics ->
            OxideListDialog(
                title = "Target version",
                options = listOf(
                    OxideDialogOption("1.20.1", "1.20.1", "Released 2023"),
                    OxideDialogOption("1.20.4", "1.20.4", "Released 2024"),
                    OxideDialogOption("1.21.1", "1.21.1", "Released 2024"),
                    OxideDialogOption("1.12.2", "1.12.2", "No file for this loader", enabled = false),
                ),
                onOptionSelected = {},
                onDismiss = {},
                currentKey = "1.20.4",
                confirmText = "Install",
                metrics = metrics,
            )
        }
    }

    /** An action menu (`selectable = false`): no radio marks, no Confirm, every row acts at once. */
    @Test
    fun Dropdown_Actions() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Dropdown_Actions", device) { metrics ->
            OxideListDialog(
                title = "Instance actions",
                options = listOf(
                    OxideDialogOption("rename", "Rename"),
                    OxideDialogOption("copy", "Duplicate"),
                    OxideDialogOption("export", "Export modpack"),
                    OxideDialogOption("delete", "Delete", enabled = false),
                ),
                onOptionSelected = {},
                onDismiss = {},
                selectable = false,
                metrics = metrics,
            )
        }
    }

    @Test
    fun TextEntry_Valid() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TextEntry_Valid", device) { metrics ->
            OxideTextEntryDialog(
                title = "New instance",
                label = "Instance name",
                value = "all-the-mods",
                onValueChange = {},
                confirmText = "Create",
                cancelText = "Cancel",
                onConfirm = {},
                onDismiss = {},
                supportText = "12 / 64",
                metrics = metrics,
            )
        }
    }

    /**
     * The rejected form.
     *
     * Both signals have to be visible here: the confirm button greys out *and* the message under the
     * field explains why. If a change ever drops one of them, the user is left with a dead button
     * and no reason.
     */
    @Test
    fun TextEntry_Error() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TextEntry_Error", device) { metrics ->
            OxideTextEntryDialog(
                title = "New instance",
                label = "Instance name",
                value = "in/valid",
                onValueChange = {},
                confirmText = "Create",
                cancelText = "Cancel",
                onConfirm = {},
                onDismiss = {},
                errorText = "An instance folder cannot contain any of /\\\\:*?\"<>|",
                maxLength = 64,
                metrics = metrics,
            )
        }
    }

    @Test
    fun TaskDialog_KnownProgress() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TaskDialog_KnownProgress", device) { metrics ->
            OxideTaskDialog(
                title = "Downloading mods",
                message = "Sodium 0.5.8",
                progress = 0.42f,
                onCancel = {},
                metrics = metrics,
            )
        }
    }

    /**
     * Indeterminate progress.
     *
     * `progress = null` is not 0%: it means "unknown", and the bar shows an empty slot with a
     * "working" label instead of a misleading 0%. Deliberately looping here would make the golden
     * depend on frame timing, so the production code does not animate it at all.
     */
    @Test
    fun TaskDialog_UnknownProgress() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("TaskDialog_UnknownProgress", device) { metrics ->
            OxideTaskDialog(
                title = "Resolving files",
                message = "Asking CurseForge which versions match",
                progress = null,
                onCancel = {},
                metrics = metrics,
            )
        }
    }

    /** Non-interruptible: no cancel button, and dismissing by tapping the scrim is disabled. */
    @Test
    fun TaskDialog_NoCancel() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("TaskDialog_NoCancel", device) { metrics ->
            OxideTaskDialog(
                title = "Installing",
                progress = 0.9f,
                onCancel = null,
                metrics = metrics,
            )
        }
    }
}