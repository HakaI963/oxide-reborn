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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadge
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentCategory
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentCheckBox
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentImportRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentRow
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentSelectionRail
import dev.oxide.launcher.ui.screens.main.oxide.OxideContentSurface
import dev.oxide.launcher.ui.screens.main.oxide.OxideIconButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideModsBulkRail
import dev.oxide.launcher.ui.screens.main.oxide.OxideModsPanelMaxWidth
import dev.oxide.launcher.ui.screens.main.oxide.OxideSecChip
import dev.oxide.launcher.ui.screens.main.oxide.OxideSecInput
import dev.oxide.launcher.ui.screens.main.oxide.OxideSettingsGroup
import dev.oxide.launcher.ui.screens.main.oxide.OxideSubWindow
import dev.oxide.launcher.ui.screens.main.oxide.OxideSubWindowWideHeightFraction
import dev.oxide.launcher.ui.screens.main.oxide.OxideSubWindowWideWidthFraction
import dev.oxide.launcher.ui.screens.main.oxide.oxideIconDescription
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.ui.screens.main.oxide.secRowGap
import dev.oxide.launcher.ui.theme.Oxide
import org.junit.Rule
import org.junit.Test

/**
 * The content managers of an instance: mods, plus the four that share `OxideContentPanel`.
 *
 * **Why these are composed here rather than through `OxideModsSurface` / `OxideContentPanel`.**
 * Both of those take a real `Version`: one reaches for an `OxideModsViewModel` (which rescans a real
 * `mods` directory on IO), the other reaches for the disk inside `readContentEntries`. Paparazzi has
 * no such directory, and `Version` cannot be built without one, so rendering either entry point here
 * would produce a golden of a loading spinner — a picture that changes the moment a real directory
 * exists, which is worse than no golden at all. What is under test in this pass is the **panel
 * layout**, and every part of that layout is reachable without a `Version`:
 *
 *  - `OxideSubWindow` with `widthFraction` / `heightFraction` / `fillHeight` — the real shell, so the
 *    panel's size in the PNG is the real one,
 *  - `OxideModsBulkRail` and `OxideContentSelectionRail` — the two new rails, with their labels read
 *    from `strings.xml` rather than typed in here,
 *  - `OxideSecInput`, `OxideSecChip`, `OxideButton`, `OxideBadge`, `OxideIconButton`,
 *    `OxideSettingsGroup` and `OxideContentImportRow` — the real controls the actions were moved
 *    out of,
 *  - `OxideContentRow` / `OxideContentCheckBox` — the shared content row. The mods row itself
 *    (`OxideModRowItem`) is private, so the shared row stands in for it. That is a known gap: these
 *    goldens pin the **panel**, not the mod row's own internals.
 *
 * The list body is a plain `Column` rather than a `LazyColumn`, for the reason
 * `OxideLogsSnapshotTest` gives for its body: a lazy list under layoutlib renders whatever happens
 * to fit and makes the golden depend on how much room layoutlib gave it. A plain column renders all
 * the rows every time, which is what makes "the list did not get shorter" visible by counting.
 *
 * What these five goldens are for, one line each: the panel is bigger than it was; the actions moved
 * to the side instead of sitting on top of the list; the list keeps its height when a row is
 * selected; the rails do not overflow at 640x360.
 */
class OxideContentManagerSnapshotTest {

    /**
     * Animations must be at their end state before the frame is drawn. See `OxidePaparazzi`.
     */
    @get:Rule
    val instantAnimations: InstantAnimationsRule = oxideInstantAnimations

    @get:Rule
    val paparazzi = paparazziFor(OxidePaparazzi.STANDARD)

    /** Fixed mod names, so a golden that changes is a layout change and not a data change */
    private val mods = listOf(
        "Sodium",
        "Lithium",
        "Fabric API",
        "Iris",
        "ImmediatelyFast",
        "Mod Menu",
    )

    // -----------------------------------------------------------------------
    // Mods
    // -----------------------------------------------------------------------

    /**
     * The mods panel with nothing selected: the widest and tallest sub-window, and the widest list.
     *
     * With no selection there is no rail at all, so the list takes the whole panel. That is the
     * "before" of the pair with `Mods_SelectedItem`, and the two are only meaningful together: the
     * selected one must show the **same number of rows** in a visibly narrower list.
     */
    @Test
    fun Mods_List() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Mods_List", device) { metrics ->
            ModsPanel(metrics = metrics, mods = mods, selected = emptySet())
        }
    }

    /**
     * One mod selected.
     *
     * The bulk rail appears on the right carrying the five bulk actions, and the list is the same
     * height it was without it — that is the whole regression. `Mods_List` and `Mods_MultiSelection`
     * show that the row count does not move as the rail appears, nor as the selection grows.
     */
    @Test
    fun Mods_SelectedItem() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Mods_SelectedItem", device) { metrics ->
            ModsPanel(metrics = metrics, mods = mods, selected = setOf(mods[1]))
        }
    }

    /**
     * Three mods selected, which is the state the user reported as "no room left to pick another".
     *
     * One of the three is deliberately left out of [updatable], so the rail's `Update` button is
     * drawn against a real count rather than a constant. What this golden pins is that three
     * checkboxes and the rail together still leave the rows underneath visible.
     */
    @Test
    fun Mods_MultiSelection() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Mods_MultiSelection", device) { metrics ->
            ModsPanel(
                metrics = metrics,
                mods = mods,
                selected = setOf(mods[0], mods[1], mods[2]),
                updatable = setOf(mods[0], mods[2]),
            )
        }
    }

    /**
     * The same panel at 640x360, where a rail is most likely to overflow or to crowd out the list.
     *
     * 360dp of height is the tightest window this layout has to survive. The bulk rail stacks five
     * 28dp buttons, so it needs five button heights plus four gaps plus the surface's own padding;
     * a rail taller than the panel would be clipped, and a rail wide enough to squeeze the list to
     * nothing would be caught here first.
     */
    @Test
    fun Mods_Actions_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Mods_Actions_Compact", device) { metrics ->
            ModsPanel(
                metrics = metrics,
                mods = mods.take(3),
                selected = setOf(mods[0], mods[1]),
                updatable = setOf(mods[0]),
            )
        }
    }

    // -----------------------------------------------------------------------
    // The other four content managers
    // -----------------------------------------------------------------------

    /**
     * Resource packs with two packs selected, at 640x360.
     *
     * This is `OxideContentPanel` with its rail: the real `OxideSettingsGroup` (search, then sort,
     * refresh, select-all all on one row, then the import row) with the real
     * `OxideContentSelectionRail` on its right, and the shared content rows underneath. The thing to
     * read off this golden is the same as for the mods: the two selection actions are **beside** the
     * controls, not on top of the list, so the list starts at the same height with and without a
     * selection.
     */
    @Test
    fun Content_Rail_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Content_Rail_Compact", device) { metrics ->
            ResourcePacksPanel(metrics = metrics, selected = setOf("Faithful 32x", "Skyblock"))
        }
    }

    // -----------------------------------------------------------------------
    // Compositions
    // -----------------------------------------------------------------------

    /**
     * The mods panel, laid out the way `OxideModsContent` lays it out.
     *
     * The shell parameters and the rail are the production ones; the chrome around them is written
     * out here with the same production components in the same order. The order is the point:
     * header, then search + sort, then filters + select-all, then `weight(1f)` for the list and the
     * rail. A change to that order moves the list's top edge in all four mods goldens at once.
     */
    @Composable
    private fun ModsPanel(
        metrics: OxideMetrics,
        mods: List<String>,
        selected: Set<String>,
        updatable: Set<String> = selected,
    ) {
        OxideSubWindow(
            title = "Mods · 1.20.1 Fabric",
            onClose = {},
            maxPanelWidth = OxideModsPanelMaxWidth,
            widthFraction = OxideSubWindowWideWidthFraction,
            heightFraction = OxideSubWindowWideHeightFraction,
            fillHeight = true,
            scrollable = false,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- 标题 + 计数 + 刷新 ----------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.oxide_mod_section),
                            color = Oxide.FgStrong,
                            fontSize = Oxide.Type.BodyStrong.fontSize,
                            lineHeight = Oxide.Type.BodyStrong.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.oxide_mod_instance_facts,
                                "1.20.1",
                                "Fabric",
                            ),
                            color = Oxide.FgDim,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OxideBadge(text = stringResource(R.string.oxide_mgr_count, mods.size))
                    Spacer(Modifier.width(metrics.secRowGap))
                    RefreshButton()
                }
                Spacer(Modifier.height(metrics.secRowGap))

                // ---- 搜索 + 排序 ------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OxideSecInput(
                        metrics = metrics,
                        value = "",
                        onValueChange = {},
                        placeholder = stringResource(R.string.generic_search),
                        modifier = Modifier.weight(1f),
                    )
                    SortButton()
                }
                Spacer(Modifier.height(metrics.secRowGap))

                // ---- 状态筛选 + 全选 --------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("All" to mods.size, "Enabled" to mods.size - 1, "Disabled" to 1)
                        .forEach { (label, count) ->
                            OxideSecChip(
                                label = "$label $count",
                                selected = label == "All",
                                metrics = metrics,
                                onClick = {},
                                modifier = Modifier.weight(1f),
                            )
                        }
                    SelectAllButton(everythingSelected = false, enabled = mods.isNotEmpty())
                }
                Spacer(Modifier.height(metrics.secRowGap))

                // ---- 列表 + 批量栏 ----------------------------------------------
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                    ) {
                        mods.forEach { mod ->
                            OxideContentRow(
                                title = mod,
                                detail = "1.20.1 · Fabric · 2.1 MB",
                                selected = mod in selected,
                                role = if (selected.isEmpty()) Role.Button else Role.Checkbox,
                                leading = if (selected.isEmpty()) {
                                    null
                                } else {
                                    { OxideContentCheckBox(selected = mod in selected) }
                                },
                                trailing = { RefreshButton() },
                                onClick = {},
                            )
                        }
                    }
                    // 没有选中时这一栏整个不出现，列表因此拿回全部宽度
                    if (selected.isNotEmpty()) {
                        Spacer(Modifier.width(metrics.secRowGap))
                        OxideModsBulkRail(
                            metrics = metrics,
                            updatableCount = selected.count { it in updatable },
                            busy = false,
                            updating = false,
                            onEnable = {},
                            onDisable = {},
                            onUpdate = {},
                            onDelete = {},
                            onClearSelection = {},
                        )
                    }
                }
            }
        }
    }

    /**
     * The non-mod content manager with its rail, at the tightest supported window.
     *
     * Everything inside the group is a production component, and the arrangement is the one
     * `OxideContentPanel` uses: the controls column takes the remaining width and the rail sits
     * beside it, only while something is selected. The rows underneath are the shared
     * `OxideContentRow` in the same surface the panel wraps each entry in.
     */
    @Composable
    private fun ResourcePacksPanel(metrics: OxideMetrics, selected: Set<String>) {
        val packs = listOf(
            "Faithful 32x" to "Faithful-32x.zip · 12.4 MB",
            "Skyblock" to "Skyblock.zip · 2.8 MB",
            "Distant Horizons" to "DistantHorizons.zip · 4.1 MB",
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = metrics.pagePaddingH)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OxideSettingsGroup(
                    title = stringResource(OxideContentCategory.ResourcePacks.titleRes),
                    metrics = metrics,
                    modifier = Modifier.weight(1f),
                ) {
                    OxideSecInput(
                        metrics = metrics,
                        value = "",
                        onValueChange = {},
                        placeholder = stringResource(R.string.generic_search),
                    )
                    Spacer(Modifier.height(metrics.secRowGap))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SortButton()
                        RefreshButton()
                        Spacer(Modifier.weight(1f))
                        SelectAllButton(
                            everythingSelected = false,
                            enabled = packs.isNotEmpty(),
                        )
                    }
                    Spacer(Modifier.height(metrics.secRowGap))
                    OxideContentImportRow(
                        metrics = metrics,
                        category = OxideContentCategory.ResourcePacks,
                        enabled = true,
                        onPick = {},
                    )
                }
                // 没有选中时这一栏整个不出现，控件区因此拿回全部宽度
                if (selected.isNotEmpty()) {
                    Spacer(Modifier.width(metrics.secRowGap))
                    OxideContentSelectionRail(
                        metrics = metrics,
                        busy = false,
                        onDeleteSelected = {},
                        onClearSelection = {},
                    )
                }
            }
            packs.forEach { (name, detail) ->
                Spacer(Modifier.height(metrics.cardGap))
                OxideContentSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideContentRow(
                        title = name,
                        detail = detail,
                        selected = name in selected,
                        role = if (selected.isEmpty()) Role.Button else Role.Checkbox,
                        leading = if (selected.isEmpty()) {
                            null
                        } else {
                            { OxideContentCheckBox(selected = name in selected) }
                        },
                        onClick = {},
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // The controls, written once so both panels use the real ones
    // -----------------------------------------------------------------------

    @Composable
    private fun RefreshButton() {
        OxideIconButton(
            onClick = {},
            enabled = true,
            glyph = "↻",
            modifier = Modifier.oxideIconDescription(stringResource(R.string.generic_refresh)),
        )
    }

    @Composable
    private fun SortButton() {
        OxideButton(
            text = stringResource(R.string.oxide_mgr_sort_label, "FileModified·desc"),
            onClick = {},
        )
    }

    @Composable
    private fun SelectAllButton(everythingSelected: Boolean, enabled: Boolean) {
        OxideButton(
            text = stringResource(
                if (everythingSelected) {
                    R.string.oxide_mgr_action_clear_selection
                } else {
                    R.string.oxide_mgr_action_select_all
                }
            ),
            onClick = {},
            enabled = enabled,
        )
    }
}