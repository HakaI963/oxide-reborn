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
import androidx.compose.ui.unit.Dp
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeCompactBody
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeEnvRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeWideBody
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.OxideWidthClass
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import app.cash.paparazzi.InstantAnimationsRule
import org.junit.Rule
import org.junit.Test

/**
 * Home (page 01) at three widths and in each of the states it can actually be in.
 *
 * The seam is [OxideHomeWideBody] / [OxideHomeCompactBody] rather than `OxideHomePage`. The page
 * composable reaches straight into `VersionsManager`, `AccountsManager`, `TaskSystem`, `Renderers`
 * and `Build.VERSION`, and probes the filesystem for the storage figures — none of which can run
 * under layoutlib without an installed Minecraft and a live backend. The two body composables were
 * already stateless (instance name, account and environment rows arrive as plain arguments), so
 * widening them and [OxideHomeEnvRow] from `private` to `internal` was all it took. No production
 * layout, parameter or default was touched.
 *
 * Three widths on purpose: `OxideHomePage` chooses between the two bodies on
 * `metrics.widthClass`, so `Home_Compact` is the only golden that exercises
 * [OxideHomeCompactBody] at all, and the two-column `oxideHomeColumnsFor` rule with its
 * 235dp/270dp rail floor only exists on the wide path.
 *
 * Determinism:
 * - `lastPlayedAt` is always `null`. The strip renders it through `relativeTime`, which calls
 *   `DateUtils.getRelativeTimeSpanString(timeMillis, System.currentTimeMillis(), ...)` — any
 *   non-null value would put a moving number in a golden. `null` renders the "never played" row,
 *   which is a genuine state rather than a stand-in.
 * - `revealed = true` instead of letting the page's `LaunchedEffect(Unit) { revealed = true }` flip
 *   it. See `OxidePaparazzi` for why a reveal has to be settled before the frame is drawn.
 * - The account's UUID is pinned in [OxideFake]; `Account`'s own default is `UUID.randomUUID()`.
 */
class OxideHomeSnapshotTest {

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

    /** The width `OxideHomePage` measures inside its own `BoxWithConstraints`. */
    private fun contentWidth(metrics: OxideMetrics, screenWidthPx: Int): Dp =
        Dp(screenWidthPx.toFloat()) - metrics.pagePaddingH * 2

    private val instanceName = "1.20.1-forge-47.3.0"
    private val instanceDetail = "Forge 47.3.0"

    private val environment = listOf(
        OxideHomeEnvRow("Loader", "Forge 47.3.0"),
        OxideHomeEnvRow("Renderer", "VirGL"),
        OxideHomeEnvRow("Java", "OpenJDK 21"),
        OxideHomeEnvRow("Memory", "4096 MB"),
        OxideHomeEnvRow("Device", "Pixel 8"),
        OxideHomeEnvRow("Android", "14 - API 34"),
        OxideHomeEnvRow("Architecture", "arm64-v8a"),
        OxideHomeEnvRow("Launcher", "1.0.0"),
        OxideHomeEnvRow("Storage", "12.4 GB used, 88.1 GB free"),
    )

    private val kicker = "Oxide Launcher - Ready"

    /**
     * The home body inside the real page container.
     *
     * Which body renders is decided from `metrics.widthClass`, never passed in, so this cannot
     * disagree with the branch production takes.
     */
    @Composable
    private fun Home(
        metrics: OxideMetrics,
        screenWidthPx: Int,
        account: Account? = null,
        envRows: List<OxideHomeEnvRow> = environment,
        versionName: String? = instanceName,
        versionDetail: String? = instanceDetail,
        isRefreshing: Boolean = false,
        modsCount: Int? = 12,
        onPlay: () -> Unit = {},
        onGoInstances: () -> Unit = {},
    ) {
        OxidePageColumn(metrics = metrics) {
            val common = contentWidth(metrics, screenWidthPx)
            if (metrics.widthClass == OxideWidthClass.Compact) {
                OxideHomeCompactBody(
                    metrics = metrics,
                    contentWidth = common,
                    revealed = true,
                    kicker = kicker,
                    playEnabled = !isRefreshing,
                    onPlay = onPlay,
                    account = account,
                    envRows = envRows,
                    versionBadge = versionName,
                    versionName = versionName,
                    versionDetail = versionDetail,
                    isRefreshing = isRefreshing,
                    typeLabel = "Mod loader",
                    lastPlayedAt = null,
                    modsCount = modsCount,
                    onGoInstances = onGoInstances,
                )
            } else {
                OxideHomeWideBody(
                    metrics = metrics,
                    contentWidth = common,
                    revealed = true,
                    kicker = kicker,
                    playEnabled = !isRefreshing,
                    onPlay = onPlay,
                    account = account,
                    envRows = envRows,
                    versionBadge = versionName,
                    versionName = versionName,
                    versionDetail = versionDetail,
                    isRefreshing = isRefreshing,
                    typeLabel = "Mod loader",
                    lastPlayedAt = null,
                    modsCount = modsCount,
                    onGoInstances = onGoInstances,
                )
            }
        }
    }

    @Test
    fun Home_Default() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_Default", device) { metrics ->
            Home(metrics, device.screenWidth)
        }
    }

    @Test
    fun Home_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Home_Compact", device) { metrics ->
            Home(metrics, device.screenWidth)
        }
    }

    @Test
    fun Home_Wide() {
        val device = OxidePaparazzi.WIDE
        paparazzi.shot("Home_Wide", device) { metrics ->
            Home(metrics, device.screenWidth)
        }
    }

    @Test
    fun Home_Light() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_Light", device, dark = false) { metrics ->
            Home(metrics, device.screenWidth)
        }
    }

    @Test
    fun Home_WithAccount() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_WithAccount", device) { metrics ->
            Home(
                metrics = metrics,
                screenWidthPx = device.screenWidth,
                account = OxideFake.account("Steve"),
            )
        }
    }

    /**
     * The settled, fully-populated home: account card filled, instance strip settled, mods counted.
     *
     * Home has no separate "selected" state — the strip's trigger is always marked selected,
     * because it *is* the current instance — so this is the populated counterpart to
     * [Home_NoInstance] rather than another branch of the code.
     */
    @Test
    fun Home_Selected() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_Selected", device) { metrics ->
            Home(
                metrics = metrics,
                screenWidthPx = device.screenWidth,
                account = OxideFake.account("Steve"),
                modsCount = 128,
            )
        }
    }

    @Test
    fun Home_WithEnvironment() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Home_WithEnvironment", device) { metrics ->
            Home(
                metrics = metrics,
                screenWidthPx = device.screenWidth,
                versionName = null,
                modsCount = null,
            )
        }
    }

    @Test
    fun Home_NoInstance() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_NoInstance", device) { metrics ->
            Home(
                metrics = metrics,
                screenWidthPx = device.screenWidth,
                versionName = null,
                versionDetail = null,
                modsCount = null,
            )
        }
    }

    /**
     * Still probing the version list: the strip shows its loading row instead of the "no instance"
     * explanation, and Play is disabled. Easy to regress — the two states differ only in the
     * strip's left half and in one disabled button.
     */
    @Test
    fun Home_Loading() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Home_Loading", device) { metrics ->
            Home(
                metrics = metrics,
                screenWidthPx = device.screenWidth,
                versionName = null,
                isRefreshing = true,
                modsCount = null,
            )
        }
    }
}