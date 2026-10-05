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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.ui.screens.main.oxide.OxideAboutHero
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentCheckBox
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentHeader
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentKindBadge
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentPathBar
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentSurface
import dev.oxide.launcher.ui.screens.main.oxide.OxideControlPrerequisites
import dev.oxide.launcher.ui.screens.main.oxide.OxideGesturesGroup
import dev.oxide.launcher.ui.screens.main.oxide.OxideHotspotsGroup
import dev.oxide.launcher.ui.screens.main.oxide.OxideLogSource
import dev.oxide.launcher.ui.screens.main.oxide.OxideLogSourceList
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideMouseGroup
import dev.oxide.launcher.ui.screens.main.oxide.OxideMpGuideCard
import dev.oxide.launcher.ui.screens.main.oxide.OxideMpToggleRow
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.OxideSurface
import dev.oxide.launcher.ui.screens.main.oxide.oxidePathCrumbsFor
import dev.oxide.launcher.ui.screens.main.oxide.oxidePathCrumbsFor
import dev.oxide.launcher.ui.screens.main.oxide.visibleControlRows
import dev.oxide.launcher.ui.screens.main.oxide.secRowGap
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * The destination surfaces that are not the four sidebar pages: Files, Logs, Multiplayer, About and
 * the controls editor.
 *
 * These are grouped into one file because they share a seam strategy. Every one of them has a
 * page-level composable bound to a ViewModel or to real state, and a set of inner composables that
 * are already stateless. Where those inner composables were `private` they were widened to
 * `internal`; nothing about their layout, parameters or defaults changed.
 *
 * What is deliberately *not* faked: no filesystem probe, no network, no sensor, no clock. The Files
 * toolbar and the Files browser are absent because `OxideFilesBrowser` takes an
 * `OxideFilesViewModel` that stats real directories, and the Vulkan result is absent here because it
 * lives next to the dialogs family in `OxideVulkanSnapshotTest`.
 */
class OxideSurfacesSnapshotTest {

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
    // Files
    // -----------------------------------------------------------------------

    /**
     * The Files browser, minus the parts that need a directory.
     *
     * The path bar, the row shapes and the multi-select checkboxes are all the real production
     * composables; `OxideContentRow` and `OxideContentPathBar` are shared with Logs, so pinning them
     * here also pins the shared vocabulary (a folder reads as a folder, a file as a file).
     *
     * `oxidePathCrumbsFor` is the real function, not a hand-written crumb list, so the truncation and
     * the ellipsis placeholder are whatever production says they are at this width.
     */
    @Test
    fun Files_Root() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Files_Root", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideContentSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideContentHeader(
                        metrics = metrics,
                        title = "Files",
                        subtitle = "/storage/emulated/0/games/1.20.1-forge",
                        onDismiss = {},
                    )
                    OxideContentPathBar(
                        metrics = metrics,
                        crumbs = oxidePathCrumbsFor(
                            labels = listOf("games", "1.20.1-forge", "mods", "config"),
                            maxCrumbs = 4,
                        ),
                        onNavigate = {},
                    )
                    FilesRows(metrics)
                }
            }
        }
    }

    /**
     * The same browser deep enough that the path bar has to truncate.
     *
     * `oxidePathCrumbsFor` keeps the tail and inserts an ellipsis placeholder in front. That is the
     * behaviour worth a golden: it is the difference between "you can see where you are" and a path
     * that silently starts mid-directory.
     */
    @Test
    fun Files_DeepPath() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Files_DeepPath", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideContentSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideContentHeader(
                        metrics = metrics,
                        title = "Files",
                        subtitle = "kubejs/server_scripts",
                        onDismiss = {},
                    )
                    OxideContentPathBar(
                        metrics = metrics,
                        crumbs = oxidePathCrumbsFor(
                            labels = listOf(
                                "games",
                                "1.20.1-forge",
                                "kubejs",
                                "server_scripts",
                                "startup_scripts",
                            ),
                            maxCrumbs = 3,
                        ),
                        onNavigate = {},
                    )
                    FilesRows(metrics)
                }
            }
        }
    }

    @Composable
    private fun FilesRows(metrics: OxideMetrics) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
            listOf(
                Triple("mods", "12 items, 3.2 MB", "D"),
                Triple("config", "24 items, 96 KB", "D"),
                Triple("resourcepacks", "empty", "D"),
                Triple("server.properties", "2.1 KB", "F"),
                Triple("latest.log", "1.4 MB", "F"),
            ).forEachIndexed { index, (name, detail, kind) ->
                OxideContentRow(
                    title = name,
                    detail = detail,
                    selected = index == 0,
                    role = Role.Tab,
                    leading = {
                        OxideContentKindBadge(
                            glyph = kind,
                            description = if (kind == "D") "Folder" else "File",
                        )
                    },
                    trailing = { OxideContentCheckBox(selected = index == 0) },
                    onClick = {},
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Logs
    // -----------------------------------------------------------------------

    /**
     * The log source list.
     *
     * `rememberLogSources` only ever returns paths whose `isFile` is true — "no log yet" and "the log
     * is empty" are different facts and the list refuses to conflate them — so this feeds it three
     * fixed sources rather than pretending a directory exists.
     */
    @Test
    fun Logs_Sources() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Logs_Sources", device) { metrics ->
            LogsScreen(metrics)
        }
    }

    @Composable
    private fun LogsScreen(metrics: OxideMetrics) {
        OxidePageColumn(metrics = metrics) {
            OxideContentHeader(
                metrics = metrics,
                title = "Logs",
                subtitle = "3 sources",
                onDismiss = {},
            )
            OxideLogSourceList(
                modifier = Modifier.fillMaxWidth(),
                metrics = metrics,
                folderPath = "/data/dev.oxide.launcher/logs",
                sources = listOf(
                    OxideLogSource("/games/1.20.1-forge/logs/latest.log", "Game log", "1.4 MB"),
                    OxideLogSource("/games/1.20.1-forge/crash-reports", "Crash report", "84 KB"),
                    OxideLogSource("/data/dev.oxide.launcher/logs", "Launcher log", "12 KB"),
                ),
                activePath = "/games/1.20.1-forge/logs/latest.log",
                onSelect = {},
                onShare = {},
                onOpenFolder = {},
            )
        }
    }

    @Test
    fun Logs_Sources_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Logs_Sources_Compact", device) { metrics ->
            LogsScreen(metrics)
        }
    }

    // -----------------------------------------------------------------------
    // Multiplayer
    // -----------------------------------------------------------------------

    /**
     * The multiplayer guide, on its first tab.
     *
     * `OxideMpGuideCard` keeps the tab index in `rememberSaveable`. Paparazzi installs a
     * `PaparazziSavedStateRegistryOwner` on the view tree, so that resolves rather than throwing —
     * and the first composition always lands on tab 0 anyway, which is the state worth pinning.
     */
    @Test
    fun Multiplayer_Guide() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Multiplayer_Guide", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideMpGuideCard(metrics = metrics)
            }
        }
    }

    @Test
    fun Multiplayer_Guide_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Multiplayer_Guide_Compact", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideMpGuideCard(metrics = metrics)
            }
        }
    }

    /**
     * Two switch rows in both positions.
     *
     * `OxideMpToggleRow` exposes its state as the whole row's `Role.Switch` plus `checked` rather
     * than repeating it on the switch itself, so a row has exactly one thing to get wrong and this
     * is the golden for it.
     */
    @Test
    fun Multiplayer_Toggles() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Multiplayer_Toggles", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideSurface(modifier = Modifier.fillMaxWidth()) {
                    OxideMpToggleRow(
                        label = "Multiplayer",
                        hint = "Requires Terracotta",
                        checked = true,
                        onCheckedChange = {},
                    )
                    OxideMpToggleRow(
                        label = "Auto-start nodes",
                        hint = "Not available while Multiplayer is off",
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // About
    // -----------------------------------------------------------------------

    /**
     * The About hero.
     *
     * The product name is a parameter here rather than `BuildKeys.LAUNCHER_NAME`, which is what makes
     * this golden able to catch a rename going missing: if the hero ever stops rendering the name it
     * was handed, the PNG goes blank in the one place it must not.
     */
    @Test
    fun About_Hero() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("About_Hero", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideAboutHero(
                    metrics = metrics,
                    launcherName = "Oxide Launcher",
                    productVersion = "1.0.0",
                    summary = "A Minecraft launcher built around an install-free, " +
                        "tap-first interface.",
                    onCheckUpdate = {},
                    onOpenProject = {},
                )
            }
        }
    }

    @Test
    fun About_Hero_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("About_Hero_Compact", device) { metrics ->
            OxidePageColumn(metrics = metrics) {
                OxideAboutHero(
                    metrics = metrics,
                    launcherName = "Oxide Launcher",
                    productVersion = "1.0.0",
                    summary = "A Minecraft launcher built around an install-free, tap-first interface.",
                    onCheckUpdate = {},
                    onOpenProject = {},
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Controls editor
    // -----------------------------------------------------------------------

    /**
     * Mouse, gestures and hotspots — the three settings-only groups of the Controls category.
     *
     * `OxideControlsPanel` itself is not used: it starts with `isGyroscopeAvailable(context)`, which
     * casts `getSystemService(SENSOR_SERVICE)` to `SensorManager`. layoutlib does not provide the
     * sensor service, so that one call would fail the whole panel over a capability probe. The
     * groups below it take a `Set<OxideControlRow>` derived from an explicit
     * [OxideControlPrerequisites], which is the same contract `OxideControlsPanel` uses — so the
     * row-visibility rules under test are the real ones, only with the device probe supplied instead
     * of performed.
     */
    @Test
    fun ControlsEditor_Default() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlsEditor_Default", device) { metrics ->
            Controls(metrics)
        }
    }

    /**
     * Slide mode with gesture control on.
     *
     * Click-only and slide-only rows are mutually exclusive by construction: `HideMouse` only appears
     * in click mode and `EnableMouseClick` only in slide mode. Two goldens with different
     * prerequisites is the only way to notice if that filter ever inverts.
     */
    @Test
    fun ControlsEditor_SlideMode() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("ControlsEditor_SlideMode", device) { metrics ->
            Controls(
                metrics = metrics,
                prerequisites = OxideControlPrerequisites(
                    mouseClickMode = false,
                    mouseSlideMode = true,
                    gestureControl = true,
                    gyroscopeAvailable = false,
                    gyroscopeControl = false,
                    gyroscopeSmoothing = false,
                    gamepadControl = false,
                    gamepadMapped = false,
                ),
            )
        }
    }

    @Test
    fun ControlsEditor_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("ControlsEditor_Compact", device) { metrics ->
            Controls(metrics)
        }
    }

    @Composable
    private fun Controls(
        metrics: OxideMetrics,
        prerequisites: OxideControlPrerequisites = OxideControlPrerequisites(
            mouseClickMode = true,
            mouseSlideMode = false,
            gestureControl = false,
            gyroscopeAvailable = false,
            gyroscopeControl = false,
            gyroscopeSmoothing = false,
            gamepadControl = false,
            gamepadMapped = false,
        ),
    ) {
        val rows = visibleControlRows(prerequisites).toSet()
        OxidePageColumn(metrics = metrics) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.sectionGap)) {
                OxideMouseGroup(metrics = metrics, rows = rows)
                OxideHotspotsGroup(metrics = metrics, onEdit = {})
                OxideGesturesGroup(metrics = metrics, rows = rows)
            }
        }
    }
}