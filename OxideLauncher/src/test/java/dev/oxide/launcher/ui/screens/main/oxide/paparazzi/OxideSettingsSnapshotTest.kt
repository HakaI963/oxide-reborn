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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import dev.oxide.launcher.ui.screens.main.oxide.OxideLauncherBridge
import dev.oxide.launcher.ui.screens.main.oxide.OxidePage
import dev.oxide.launcher.ui.screens.main.oxide.OxidePageColumn
import dev.oxide.launcher.ui.screens.main.oxide.OxideSettingsCategory
import dev.oxide.launcher.ui.screens.main.oxide.OxideSettingsPanel
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Settings (page 04): the right-hand panel, one golden per category that renders inside it.
 *
 * The seam is [OxideSettingsPanel], which dispatches on [OxideSettingsCategory] and is otherwise
 * pure: every category below reads `AllSettings.<unit>.state`, and `AbstractSettingUnit.state` is a
 * plain `mutableStateOf(defaultValue)` that never touches MMKV — `getValue()` is only called from
 * `init()`. So the panel renders with the shipped defaults and nothing has to be faked at all.
 *
 * `OxideSettingsPanel` had `private` visibility along with the category enum and the drawer request
 * type that appear in its signature; all three were widened to `internal` and nothing else changed.
 *
 * Two categories are deliberately absent:
 * - **Appearance** calls `rememberLauncherForActivityResult(MediaPickerContract(...))` and reads
 *   `LocalBackgroundViewModel`. Paparazzi supplies a lifecycle owner and a saved-state owner but no
 *   `ActivityResultRegistryOwner`, so that branch cannot compose on the JVM at all.
 * - **Controls** is covered separately in `OxideControlsSnapshotTest`, because its top-level
 *   composable probes the device's gyroscope through `SensorManager`.
 *
 * `Onboarding` does not exist as a category; the full list is General, Game, Java, Renderer,
 * Graphics, Controls, Downloads, Appearance, Accounts, Storage, Advanced.
 */
class OxideSettingsSnapshotTest {

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
     * A bridge whose callbacks all do nothing.
     *
     * `OxideLauncherBridge` is a plain class of lambdas, so this needs no fake ViewModel and no
     * fake Activity. Nothing here is invoked during composition — the callbacks only fire on click —
     * which is exactly why it can be handed a set of no-ops.
     */
    private val bridge = OxideLauncherBridge(
        openSettingsSection = {},
        openAccountManager = {},
        openLogView = {},
        checkUpdate = {},
        openLink = {},
        openFileManager = {},
        showToast = {},
        startEditor = { _: File -> },
    )

    @Composable
    private fun Settings(device: DeviceConfig, category: OxideSettingsCategory) {
        val metrics = metrics
        OxidePageColumn(metrics = metrics) {
            OxideSettingsPanel(
                // No extra padding here: OxidePageColumn has already applied pagePaddingH, and
                // adding it again would shrink the panel relative to what the page really shows.
                modifier = Modifier.fillMaxSize(),
                metrics = metrics,
                category = category,
                bridge = bridge,
                onOpenDrawer = {},
                onOpenCustomColor = {},
                onNavigate = { _: OxidePage -> },
            )
        }
    }

    /** General: launcher identity, motion, quick actions — three grouped blocks. */
    @Test
    fun Settings_Main() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Main", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.General)
        }
    }

    /** Game: versions, memory, JVM arguments. */
    @Test
    fun Settings_Game() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Game", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Game)
        }
    }

    /** Downloads: threads, mirrors and the native-library plugin toggles. */
    @Test
    fun Settings_Downloads() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Downloads", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Downloads)
        }
    }

    /**
     * A drawer-backed category.
     *
     * Java, Renderer, Graphics, Storage and Advanced render a one-paragraph summary plus an
     * "open the drawer" button instead of the settings themselves — a completely different panel
     * from the in-place categories, and the one where a missing `drawer()` entry would silently
     * disable the only button on the screen.
     */
    @Test
    fun Settings_Java() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Java", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Java)
        }
    }

    @Test
    fun Settings_Renderer() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Renderer", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Renderer)
        }
    }

    /** Storage is drawer-backed too, but unlike Java it also offers "open instances". */
    @Test
    fun Settings_Storage() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Storage", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Storage)
        }
    }

    @Test
    fun Settings_Accounts() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("Settings_Accounts", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.Accounts)
        }
    }

    /**
     * General at 640x360.
     *
     * `OxideSettingsPanel` is a `Column` with a `verticalScroll`, so at the compact width the tail
     * groups simply move below the fold. Nothing errors and nothing is clipped; the golden exists so
     * that "the scroll region covers the whole panel" stays true rather than the last group being
     * cut off by a fixed height.
     */
    @Test
    fun Settings_Main_Compact() {
        val device = OxidePaparazzi.COMPACT
        paparazzi.shot("Settings_Main_Compact", device) { metrics ->
            Settings(metrics, OxideSettingsCategory.General)
        }
    }
}