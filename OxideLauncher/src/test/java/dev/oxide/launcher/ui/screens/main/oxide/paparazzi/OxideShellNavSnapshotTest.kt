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

package dev.oxide.launcher.ui.screens.main.oxide.paparazzi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverCategory
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverFeed
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverFeedPhase
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverItem
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverResultsGrid
import dev.oxide.launcher.ui.screens.main.oxide.DiscoverResultsHeader
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeCompactBody
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeEnvRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideHomeWideBody
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideNavState
import dev.oxide.launcher.ui.screens.main.oxide.OxidePage
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.OxideShell
import dev.oxide.launcher.ui.screens.main.oxide.OxideTaskDrawer
import dev.oxide.launcher.ui.screens.main.oxide.OxideWidthClass
import dev.oxide.launcher.ui.screens.main.oxide.oxideTransientSurface
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * The seam between the primary rail and the surfaces that sit on top of a page.
 *
 * The defect this pins is a stale overlay: open a drawer (Multiplayer, Files, Account, an instance
 * Configure drawer, an instance Mods drawer, the task drawer) and then tap another item in the left
 * rail — the drawer stayed painted over the new page and had to be closed by hand. The state that
 * owns those drawers is remembered one level *above* the page deck, so a page change never disposed
 * of it.
 *
 * The fix is state, not a cover: `OxideNavState.epoch` advances on every real page change and
 * `oxideTransientSurface` drops anything opened under an older epoch. This golden renders the two
 * ends of that decision — drawer open, and the same composition after `nav.go(...)` — so a change
 * that puts the drawer back on top of the new page shows up as a pixel diff.
 *
 * ## Why the harness is a seam and not `OxideMainShell`
 *
 * `OxideMainShell` reads `viewModel()`, `VersionsManager` and `BackHandler`, none of which exist
 * under layoutlib. [OxideShell] takes `nav` / `metrics` / `content` and is the whole chrome, so it
 * renders as-is. What is re-stated here is exactly the shape of the fix and nothing else: the page
 * body inside the deck, and one gate in front of the drawer. The gate call is the production one,
 * with the same argument order and the same `Boolean` shape the task drawer uses, so the golden
 * fails if the helper or its arguments change meaning.
 *
 * [OxideNavState] is constructed directly rather than through `rememberOxideNavState`: Paparazzi
 * renders a single frame, and a saveable holder would only add restore machinery this test never
 * exercises.
 */
class OxideShellNavSnapshotTest {

    /**
     * Animations must be at their end state before the frame is drawn.
     *
     * The rail's selection pill slides with `animateFloatAsState` and the drawer fades in and out,
     * so without this rule every golden would be captured mid-flight and would differ by a frame
     * depending on how fast the machine is. See `OxidePaparazzi`.
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    private val tasks = listOf(
        OxideFake.task(
            id = "download-mods",
            progress = 0.42f,
            message = "Sodium 0.5.8",
            bytesPerSec = 1_048_576L,
        ),
        OxideFake.task(
            id = "import-world",
            progress = 1f,
            message = "Imported world 'Kelp Harbour'",
        ),
    )

    private val environment = listOf(
        OxideHomeEnvRow("Loader", "Forge 47.3.0"),
        OxideHomeEnvRow("Renderer", "VirGL"),
        OxideHomeEnvRow("Java", "OpenJDK 21"),
        OxideHomeEnvRow("Memory", "4096 MB"),
        OxideHomeEnvRow("Android", "14 - API 34"),
    )

    private val instanceName = "1.20.1-forge-47.3.0"

    /** The five fixed mod titles [OxideDiscoverSnapshotTest] uses, so the grid is the same picture. */
    private val discoverFeed = DiscoverFeed(
        phase = DiscoverFeedPhase.Ready,
        items = OxideFake.modTitles().mapIndexed { index, title ->
            DiscoverItem(
                data = OxideFake.searchData(
                    id = OxideFake.modProjectIds()[index],
                    title = title,
                    author = "mod-author-${index + 1}",
                ),
                title = title,
                classes = PlatformClasses.MOD,
                loaderLabel = if (index % 2 == 0) "Fabric" else "Forge",
            )
        },
        index = 0,
        endOfResults = false,
        pages = 1,
    )

    // -----------------------------------------------------------------------
    // Snapshots
    // -----------------------------------------------------------------------

    /** A drawer open over Home: the state the recording was taken in. */
    @Test
    fun ShellNav_DrawerOpen() {
        val device = OxidePaparazzi.STANDARD
        val nav = OxideNavState(OxidePage.Home)
        paparazzi.shot("ShellNav_DrawerOpen", device) { metrics ->
            ShellSeam(nav = nav, metrics = metrics, drawerOpenedAt = nav.epoch)
        }
    }

    /**
     * The same composition after the rail moves to Discover: the drawer is gone in the same frame and
     * Discover is what is painted.
     */
    @Test
    fun ShellNav_AfterNavigation() {
        val device = OxidePaparazzi.STANDARD
        val nav = OxideNavState(OxidePage.Home)
        // The drawer is opened under epoch 0 and the rail then pushes the epoch along: that
        // ordering is exactly what the recording does.
        val drawerOpenedAt = nav.epoch
        nav.go(OxidePage.Discover)
        paparazzi.shot("ShellNav_AfterNavigation", device) { metrics ->
            ShellSeam(nav = nav, metrics = metrics, drawerOpenedAt = drawerOpenedAt)
        }
    }

    /**
     * The same post-navigation frame at the compact width.
     *
     * `drawerWidth` is min(520dp, 44vw), so at 640dp the drawer would be ~282dp wide and would hide
     * a different slice of the Discover page than it does at 1280dp. A stale drawer is just as wrong
     * here as at the reference size, and this is the width where the worst of it would be visible.
     */
    @Test
    fun ShellNav_AfterNavigation_Compact() {
        val device = OxidePaparazzi.COMPACT
        val nav = OxideNavState(OxidePage.Home)
        val drawerOpenedAt = nav.epoch
        nav.go(OxidePage.Discover)
        paparazzi.shot("ShellNav_AfterNavigation_Compact", device) { metrics ->
            ShellSeam(nav = nav, metrics = metrics, drawerOpenedAt = drawerOpenedAt)
        }
    }

    // -----------------------------------------------------------------------
    // The seam
    // -----------------------------------------------------------------------

    /**
     * The shell, a page body and one epoch gate in front of the drawer.
     *
     * [drawerOpenedAt] is the epoch the drawer was opened under, which is the whole state the fix
     * added; everything else here mirrors what `OxideMainShell` already had. The gate is called
     * exactly as the task drawer's is called in production, so the golden covers the argument order
     * and the "same epoch means still open" reading rather than a paraphrase of them.
     *
     * The drawer sits inside the page deck, which is where `OxideMainShell` hosts every
     * `OxideDestination` -- and those are what the recording shows. `OxideDrawerHost` anchors the
     * drawer to the end, and the page area already ends at the window edge, so the drawer itself lands
     * in the same place it would as a sibling of the shell; only the scrim stops at the sidebar,
     * exactly as it does in production for a destination.
     */
    @Composable
    private fun ShellSeam(
        nav: OxideNavState,
        metrics: OxideMetrics,
        drawerOpenedAt: Int,
    ) {
        val tasksExpanded = true

        OxideShell(nav = nav, modifier = Modifier.fillMaxSize(), metrics = metrics) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                PageBody(page = page, metrics = metrics)

                if (oxideTransientSurface(tasksExpanded, drawerOpenedAt, nav.epoch) == true) {
                    OxideTaskDrawer(
                        tasks = tasks,
                        metrics = metrics,
                        onDismiss = {},
                    )
                }
            }
        }
    }

    /**
     * The page bodies, chosen by [OxidePage] from [OxideMetrics.widthClass] exactly as the pages
     * themselves choose.
     *
     * `OxideHomePage` and `OxideDiscoverPage` cannot render here — the first reaches into
     * `VersionsManager` and `AccountsManager`, the second owns two ViewModels and issues a platform
     * search. Their stateless bodies are already the seam the other goldens use: `OxideHomeWideBody`
     * / `OxideHomeCompactBody` in `OxideHomeSnapshotTest`, `DiscoverResultsGrid` in
     * `OxideDiscoverSnapshotTest`. Which page is painted is therefore read off the same rail
     * selection and breadcrumb the user would see.
     *
     * `lastPlayedAt = null` for the reason `OxidePaparazzi` gives: a non-null value renders through
     * `relativeTime`, which reads `System.currentTimeMillis()`.
     */
    @Composable
    private fun PageBody(page: OxidePage, metrics: OxideMetrics) {
        when (page) {
            // `OxideHomePage` is itself one `BoxWithConstraints` with no page padding of its own, so
            // the body is emitted exactly as it is there. The width therefore comes from the shell's
            // own measurement -- the page area sits to the right of the sidebar, so it is neither the
            // screen width nor the screen width minus padding.
            OxidePage.Home -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val contentWidth = maxWidth
                if (metrics.widthClass == OxideWidthClass.Compact) {
                    OxideHomeCompactBody(
                        metrics = metrics,
                        contentWidth = contentWidth,
                        revealed = true,
                        kicker = "Oxide Launcher · Ready",
                        playEnabled = true,
                        onPlay = {},
                        account = OxideFake.account("Steve"),
                        envRows = environment,
                        versionBadge = instanceName,
                        versionName = instanceName,
                        versionDetail = "Forge 47.3.0",
                        isRefreshing = false,
                        typeLabel = "Mod loader",
                        lastPlayedAt = null,
                        modsCount = 12,
                        onGoInstances = {},
                    )
                } else {
                    OxideHomeWideBody(
                        metrics = metrics,
                        contentWidth = contentWidth,
                        revealed = true,
                        kicker = "Oxide Launcher · Ready",
                        playEnabled = true,
                        onPlay = {},
                        account = OxideFake.account("Steve"),
                        envRows = environment,
                        versionBadge = instanceName,
                        versionName = instanceName,
                        versionDetail = "Forge 47.3.0",
                        isRefreshing = false,
                        typeLabel = "Mod loader",
                        lastPlayedAt = null,
                        modsCount = 12,
                        onGoInstances = {},
                    )
                }
            }

            OxidePage.Discover -> OxidePageColumn(metrics = metrics) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DiscoverResultsHeader(
                        count = discoverFeed.items.size,
                        pages = discoverFeed.pages,
                        scanningInstalled = false,
                        onlyInstalled = false,
                    )
                    DiscoverResultsGrid(
                        modifier = Modifier.fillMaxWidth(),
                        metrics = metrics,
                        feed = discoverFeed,
                        visible = discoverFeed.items,
                        category = DiscoverCategory.MODS,
                        installedIds = emptySet(),
                        onlyInstalled = false,
                        busy = false,
                        onRetry = {},
                        onLoadMore = {},
                        onOpen = {},
                        onInstall = {},
                        // Favorites are tracked by an increasing tick rather than by the item, so a
                        // fixed value means "nothing favourited yet" on every run.
                        favoritesTick = 0,
                        onToggleFavorite = {},
                    )
                }
            }

            // These two goldens only ever rest on 01 and 03; the other two pages are outside the
            // seam under test.
            OxidePage.Instances, OxidePage.Settings -> OxidePageColumn(metrics = metrics) {}
        }
    }
}