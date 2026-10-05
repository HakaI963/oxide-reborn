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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverCategory
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverFeed
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverFeedPhase
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverItem
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverMobileDataDialog
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverResultsGrid
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverResultsHeader
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * Discover (page 03): every state the results area can be in.
 *
 * The seam is [DiscoverResultsGrid] plus [DiscoverResultsHeader]. `OxideDiscoverPage` itself owns an
 * `OxideDiscoverViewModel` and a `DownloadModViewModel`, drives `MinecraftVersions.refreshVersions`
 * and a mod-fingerprint scan of the current instance, and issues a platform search from a
 * `LaunchedEffect` — none of which can run under layoutlib. `DiscoverResultsGrid` was already the
 * pure decision point: it takes a [DiscoverFeed] plus the visible slice and switches on
 * `feed.view()`, which is exactly the branch we want to pin. Widening it (and
 * [DiscoverCategory], which appears in its signature) from `private` to `internal` was enough.
 *
 * `DiscoverResultsHeader` is included above the grid because the two are always stacked and the
 * header is where the "12 results" / "page 3" / "scanning installed" badges live.
 *
 * Not covered here, and why:
 * - `DiscoverCategoryRail` — as of this commit it embeds the modpack importer, which calls
 *   `rememberLauncherForActivityResult`. Paparazzi provides a lifecycle owner and a saved-state
 *   owner but no `ActivityResultRegistryOwner`, so that branch cannot render on the JVM at all.
 * - `DiscoverInstallSheetHost` — its state type carries a `PlatformVersion` straight off the
 *   platform HTTP layer, and a resolved `DiscoverTarget`. Faking those would mean asserting a
 *   layout against data the production parser can never produce.
 * - `DiscoverFilterBar` — its four dropdowns read the ViewModel's live query state.
 */
class OxideDiscoverSnapshotTest {

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

    private val ids = OxideFake.modProjectIds()
    private val titles = OxideFake.modTitles()

    private val mods: List<DiscoverItem> = titles.mapIndexed { index, title ->
        DiscoverItem(
            data = OxideFake.searchData(id = ids[index], title = title, author = "mod-author-${index + 1}"),
            title = title,
            classes = PlatformClasses.MOD,
            loaderLabel = if (index % 2 == 0) "Fabric" else "Forge",
        )
    }

    private val ready = DiscoverFeed(
        phase = DiscoverFeedPhase.Ready,
        items = mods,
        index = 0,
        pageSize = 20,
        endOfResults = false,
        pages = 1,
    )

    @Composable
    private fun Results(
        metrics: OxideMetrics,
        feed: DiscoverFeed,
        visible: List<DiscoverItem> = feed.items,
        onlyInstalled: Boolean = false,
        scanningInstalled: Boolean = false,
    ) {
        val metrics = metrics
        OxidePageColumn(metrics = metrics) {
            Column(modifier = Modifier.fillMaxSize()) {
                DiscoverResultsHeader(
                    count = visible.size,
                    pages = feed.pages,
                    scanningInstalled = scanningInstalled,
                    onlyInstalled = onlyInstalled,
                )
                DiscoverResultsGrid(
                    modifier = Modifier.fillMaxWidth(),
                    metrics = metrics,
                    feed = feed,
                    visible = visible,
                    category = DiscoverCategory.MODS,
                    installedIds = emptySet(),
                    onlyInstalled = onlyInstalled,
                    busy = false,
                    onRetry = {},
                    onLoadMore = {},
                    onOpen = {},
                    onInstall = {},
                    // Favorites are tracked by a monotonically increasing tick rather than by the
                    // item itself, so a fixed value means "nothing has been favourited yet" and the
                    // hearts render in their un-starred state on every run.
                    favoritesTick = 0,
                    onToggleFavorite = {},
                )
            }
        }
    }

    /** A populated mod result grid — the everyday case, and the one that shows the card layout. */
    @Test
    fun Discover_ModResults() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_ModResults", device) { metrics ->
            Results(metrics, ready)
        }
    }

    /**
     * Three cards at the compact width.
     *
     * `discoverResultColumns` picks its column count from the measured content width, so the same
     * five results go from two columns here to three at 1280dp. That column count is derived from
     * real measured constraints rather than a constant, which is precisely the kind of thing a
     * single-width golden would have hidden.
     */
    @Test
    fun Discover_ModResults_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Discover_ModResults_Compact", device) { metrics ->
            Results(metrics, ready)
        }
    }

    @Test
    fun Discover_ModResults_Wide() {
        val device = OxidePaparazzi.WIDE
        paparazzi.shot("Discover_ModResults_Wide", device) { metrics ->
            Results(metrics, ready)
        }
    }

    /**
     * First page in flight: no cards, no spinner, just the low-key loading row.
     *
     * `DiscoverFeedPhase.Idle` and `Loading` deliberately share this branch — an idle feed is not
     * "no results", and conflating the two is the bug this state exists to catch.
     */
    @Test
    fun Discover_Loading() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_Loading", device) { metrics ->
            Results(metrics, DiscoverFeed(phase = DiscoverFeedPhase.Loading))
        }
    }

    /** The platform really returned zero rows. Distinct from [Discover_Loading] and from the error. */
    @Test
    fun Discover_Empty() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_Empty", device) { metrics ->
            Results(metrics, DiscoverFeed(phase = DiscoverFeedPhase.Empty))
        }
    }

    /**
     * The request failed.
     *
     * `DiscoverFeed.view()` maps `Failed` with an empty item list to `Error` rather than
     * `NoResults`. If a change ever routes this to the empty state, CurseForge outages would look
     * like "no such mod exists", and this golden is what would catch it.
     */
    @Test
    fun Discover_Error() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_Error", device) { metrics ->
            Results(
                metrics,
                DiscoverFeed(
                    phase = DiscoverFeedPhase.Failed,
                    error = androidText("The CurseForge API key was rejected (HTTP 403)"),
                ),
            )
        }
    }

    /**
     * Paging on to page two with the existing results still usable.
     *
     * `PagingNext` renders the list *plus* a footer row; that is the whole difference from `Ready`,
     * and losing the footer is easy because nothing above it changes.
     */
    @Test
    fun Discover_LoadingMore() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_LoadingMore", device) { metrics ->
            Results(device, ready.copy(phase = DiscoverFeedPhase.PagingNext, index = 20, pages = 1))
        }
    }

    /**
     * The "installed only" filter with nothing matching.
     *
     * A different empty state with different wording and a different action from the plain empty
     * one, because "nothing is installed" and "nothing matched your search" are different facts.
     */
    @Test
    fun Discover_InstalledOnly_Empty() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_InstalledOnly_Empty", device) { metrics ->
            Results(metrics, DiscoverFeed(phase = DiscoverFeedPhase.Empty), onlyInstalled = true)
        }
    }

    /** The same filter, mid-scan: the header badge is the only difference from the empty case. */
    @Test
    fun Discover_InstalledOnly_Scanning() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_InstalledOnly_Scanning", device) { metrics ->
            Results(
                metrics = metrics,
                feed = ready,
                visible = mods.take(2),
                onlyInstalled = true,
                scanningInstalled = true,
            )
        }
    }

    /**
     * A later page failed while earlier results are still on screen.
     *
     * `Failed` plus a non-empty list is `ListWithError`, so the grid stays usable and only the
     * footer reports the problem. Collapsing this into a full-screen error would throw away results
     * the user can already act on.
     */
    @Test
    fun Discover_PageError() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_PageError", device) { metrics ->
            Results(
                metrics,
                ready.copy(
                    phase = DiscoverFeedPhase.Failed,
                    index = 20,
                    pages = 1,
                    error = androidText("The connection dropped while loading page 2"),
                ),
            )
        }
    }

    /** The mobile-data confirmation the install flow asks for before spending someone's data. */
    @Test
    fun Discover_MobileDataConfirm() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Discover_MobileDataConfirm", device) { metrics ->
            DiscoverMobileDataDialog(
                metrics = metrics,
                onDeny = {},
                onAllow = {},
            )
        }
    }

    @Test
    fun Discover_MobileDataConfirm_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Discover_MobileDataConfirm_Compact", device) { metrics ->
            DiscoverMobileDataDialog(
                metrics = metrics,
                onDeny = {},
                onAllow = {},
            )
        }
    }
}