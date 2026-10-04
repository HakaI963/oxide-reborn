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
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.ui.screens.main.oxide.OxideAccountCurrentCard
import dev.oxide.launcher.ui.screens.main.oxide.OxideAccountListCard
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test

/**
 * The account surface: current account, the account list, and the states those two can be in.
 *
 * `OxideAccountPage` reaches into `AccountsManager.currentAccountFlow`, the Room `AccountDao`, the
 * Microsoft wardrobe and `LaunchGameViewModel`, so the seam is the pair of cards it composes:
 * [OxideAccountCurrentCard] and [OxideAccountListCard]. Both already took plain data — an `Account?`
 * and a `List<Account>` — so widening them from `private` to `internal` was all that changed.
 *
 * `Account` is a `@Parcelize` data class whose default `uniqueUUID` is `UUID.randomUUID()` and
 * whose default `profileId` is derived from the username, so [OxideFake.account] pins both. A
 * golden that changed its UUID every run would be useless.
 *
 * [OxideAccountWardrobeCard] is deliberately absent: it answers "is the skin file on disk" from a
 * `LaunchedEffect` on `Dispatchers.IO`, and there is no disk here. Its "value not known yet" state
 * is the honest one, and a golden of it would only record that the row is blank.
 */
class OxideAccountSnapshotTest {

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

    private val accounts: List<Account> = OxideFake.twoAccounts()

    @Composable
    private fun AccountScreen(
        metrics: OxideMetrics,
        current: Account?,
        accounts: List<Account>,
        accountsCount: Int = accounts.size,
    ) {
        val metrics = metrics
        OxidePageColumn(metrics = metrics) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(metrics.cardGap),
                verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
            ) {
                OxideAccountCurrentCard(
                    metrics = metrics,
                    current = current,
                    accountsCount = accountsCount,
                    onAdd = {},
                )
                OxideAccountListCard(
                    modifier = Modifier.fillMaxSize(),
                    metrics = metrics,
                    accounts = accounts,
                    current = current,
                    onUse = {},
                    onRefresh = {},
                    onCopyUuid = {},
                    onDelete = {},
                )
            }
        }
    }

    /**
     * One account, and it is the current one.
     *
     * Both halves of the page therefore agree: the current card has the "current" badge and the
     * list row is selected. They are separate widgets on purpose (the badge is not colour-only,
     * the row exposes `Role.RadioButton`), so the golden pins both at once.
     */
    @Test
    fun Account_Active() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Account_Active", device) { metrics ->
            AccountScreen(metrics, current = accounts.first(), accounts = accounts)
        }
    }

    /**
     * Two offline accounts with the second one selected.
     *
     * The point of this golden is the contrast between the two rows: the selected one gets
     * `BgTabActive` plus the radio mark, the other stays on `BgButton`. Offline accounts have no
     * server credential, so their refresh button is disabled — a layout that only ever renders
     * Microsoft accounts would never notice that row disappearing.
     */
    @Test
    fun Account_Offline() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Account_Offline", device) { metrics ->
            AccountScreen(metrics, current = accounts[1], accounts = accounts)
        }
    }

    /** A Microsoft account, which is the only kind whose refresh control is enabled. */
    @Test
    fun Account_Microsoft() {
        val device = OxidePaparazzi.STANDARD
        val microsoft = OxideFake.account(
            username = "Alex",
            uuid = "00000000-0000-4000-8000-000000000003",
            microsoft = true,
        )
        paparazzi.shot("Account_Microsoft", device) { metrics ->
            AccountScreen(metrics, current = microsoft, accounts = listOf(microsoft))
        }
    }

    /**
     * Nothing configured.
     *
     * Two empty states, not one: the current card offers an "Add" action, while the list explains
     * that there is nothing to list. Collapsing them into a single message would leave the page
     * with no visible way forward.
     */
    @Test
    fun Account_None() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Account_None", device) { metrics ->
            AccountScreen(metrics, current = null, accounts = emptyList(), accountsCount = 0)
        }
    }

    @Test
    fun Account_List_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Account_List_Compact", device) { metrics ->
            AccountScreen(metrics, current = accounts.first(), accounts = accounts)
        }
    }

    @Test
    fun Account_List_Wide() {
        val device = OxidePaparazzi.WIDE
        paparazzi.shot("Account_List_Wide", device) { metrics ->
            AccountScreen(metrics, current = accounts.first(), accounts = accounts)
        }
    }
}