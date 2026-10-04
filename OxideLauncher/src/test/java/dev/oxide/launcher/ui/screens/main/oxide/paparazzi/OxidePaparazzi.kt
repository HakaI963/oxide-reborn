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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.oxideMetricsFor
import dev.oxide.launcher.ui.theme.AppTypography
import dev.oxide.launcher.ui.theme.ProvideOxideChrome

/**
 * Paparazzi harness for the Oxide GUI.
 *
 * Three things make these goldens reproducible, and all three live here rather than in each test
 * so that a test only has to name a screen and a state.
 *
 * **1. The size under test is the size asserted.**
 * Every device below is `Density.MEDIUM` (160 dpi, density factor 1.0) at `xdpi/ydpi = 160`, so
 * one dp is exactly one pixel and `screenWidth` *is* the width in dp. That is what lets a test
 * pair a `DeviceConfig` with `oxideMetricsFor(width, height)` and get the same number on both
 * sides: the device says the window is 1280x760dp, and the metrics are computed for 1280x760
 * rather than read back from `LocalConfiguration`. A screen that asked for
 * `rememberOxideMetrics()` inside a golden would silently re-derive the size from the device and
 * the "the size under test is the size asserted" property would quietly stop holding.
 *
 * Density.MEDIUM is also the honest choice for *this* design system. `Oxide`'s type and spacing
 * scale is a 1px = 1dp translation of a CSS reference (see the comment on `Oxide.Type`), so
 * rendering at 160 dpi keeps the golden the same shape the designer signed off on instead of
 * re-rounding every 0.35dp radius at xxhdpi.
 *
 * **2. No clock, no network, no filesystem, no randomness.**
 * Nothing in this file reads `System.currentTimeMillis()`, opens a socket, stats a real Minecraft
 * install or generates an id. Screen state is handed to the composables as plain data by the
 * tests themselves; see `OxideFakeState.kt`. The one place production code reaches for the clock
 * during composition (`relativeTime`, via `OxideHomeInstanceStrip`) is fed `lastPlayedAt = null`
 * so that row renders "never played" instead of "2 min. ago".
 *
 * **3. Animations land on their end state.**
 * [instantAnimations] sets `ValueAnimator.setDurationScale(0)`, which is exactly what Compose's
 * `MotionDurationScale` reads on Android. Without it a snapshot taken at t=0 catches every
 * fade-in and stagger mid-flight, so the golden would differ by a frame depending on how fast
 * the machine is. That matters here more than usual: the whole Oxide shell is built out of
 * `OxideReveal`, which is a staggered fade-and-rise. It is also why tests pass `revealed = true`
 * rather than letting a `LaunchedEffect` flip it between composition and the draw pass.
 */
object OxidePaparazzi {

    /**
     * 640x360 — `OxideWidthClass.Compact`, the smallest landscape still supported, and the size
     * several real overflow bugs only showed up on (the hero title's width-scaled clamp, the home
     * instance strip's three weighted columns, the dialog panel's max width).
     */
    val COMPACT: DeviceConfig = landscape(640, 360)

    /** 1280x760 — `OxideWidthClass.Expanded`, the reference design's own canvas. */
    val STANDARD: DeviceConfig = landscape(1280, 760)

    /** 1920x1080 — `OxideWidthClass.Large`: a tablet, or a desktop-mode window on a phone. */
    val WIDE: DeviceConfig = landscape(1920, 1080)

    /**
     * A landscape device of exactly [widthDp] x [heightDp] at density 1.0.
     *
     * The resource qualifiers are pinned instead of left to Paparazzi's defaults (which describe
     * a *portrait* Nexus 5). Leaving `ratio` at its `NOTLONG` default while the width exceeds the
     * height makes the folder configuration disagree with the real window, and any future
     * `layout-w*dp` / `values-w*dp` resource would then resolve against the wrong bucket — a
     * golden that quietly changes behaviour the day someone adds a resource folder.
     */
    fun landscape(widthDp: Int, heightDp: Int, dark: Boolean = true): DeviceConfig = DeviceConfig(
        screenWidth = widthDp,
        screenHeight = heightDp,
        xdpi = 160,
        ydpi = 160,
        orientation = ScreenOrientation.LANDSCAPE,
        density = Density.MEDIUM,
        nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT,
        // Pinned rather than inherited from `detectLocaleProperty()`, which reads the JVM's
        // user.language. A CI runner with a different default locale would otherwise re-render
        // every string in every chrome string and fail every golden for a reason that has
        // nothing to do with the UI.
        locale = "en",
        ratio = ScreenRatio.LONG,
        size = ScreenSize.NORMAL,
        navigation = Navigation.NONAV,
        softButtons = false,
    )

    /**
     * [OxideMetrics] for the same dp size as [device].
     *
     * Derived rather than restated so a test physically cannot claim one size and render another.
     */
    fun metricsFor(device: DeviceConfig, guiScalePercent: Int = 100): OxideMetrics =
        oxideMetricsFor(device.screenWidth, device.screenHeight, guiScalePercent)

    /**
     * The theme every golden is rendered under.
     *
     * `ProvideOxideChrome` is mandatory, not decorative: it is what pushes Material's `primary`
     * into the Oxide palette's accent slot and syncs the palette's light/dark decision with the
     * composition's `LocalConfiguration`. Omit it and the accent silently falls back to the
     * palette default, so the golden would record a colour the app never actually shows.
     *
     * The Material `ColorScheme` is pinned here rather than delegating to `OxideTheme`, because
     * `OxideTheme` additionally resolves the user's colour theme, the background `ViewModel` and
     * the festival overlays — precisely the ambient state a golden must not depend on. Oxide's own
     * palette ignores Material's neutrals anyway; only `primary` crosses over, and
     * `readableAccent` clamps it to a legible luminance either way.
     */
    @Composable
    fun Theme(dark: Boolean = true, content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = if (dark) OXIDE_DARK else OXIDE_LIGHT,
            typography = AppTypography,
        ) {
            ProvideOxideChrome { content() }
        }
    }

    /**
     * Re-point this Paparazzi at [device], then snapshot [content] there.
     *
     * A JUnit 4 `@Rule` field is created before the test body runs, so one rule instance is pinned
     * to one device and cannot be swapped per test. `unsafeUpdateConfig` is Paparazzi's documented
     * way to render one screen at several sizes inside a single class: it rebuilds the render
     * session with the new `DeviceConfig` and keeps the already-prepared resource pipeline, which is
     * what makes the multi-width goldens possible without triplicating every test class.
     *
     * **[content] receives the [OxideMetrics] for [device] rather than being handed the device and
     * computing its own.** That is deliberate and it is the single most important property of this
     * harness. A test cannot assert one size and render another, because it never gets the chance to
     * state a second number. Letting each test call `oxideMetricsFor` itself would put the pairing
     * one typo away from being silently wrong — and a golden rendered at 1280dp of metrics on a
     * 640dp device is not a small mistake, it is a wrong picture that looks plausible.
     */
    fun Paparazzi.shot(
        name: String,
        device: DeviceConfig,
        dark: Boolean = true,
        guiScalePercent: Int = 100,
        content: @Composable (OxideMetrics) -> Unit,
    ) {
        val metrics = metricsFor(device, guiScalePercent)
        unsafeUpdateConfig(deviceConfig = device)
        snapshot(name) {
            Theme(dark = dark) {
                Box(modifier = Modifier.fillMaxSize()) { content(metrics) }
            }
        }
    }
}

/**
 * The animation rule. A single shared instance is fine: `InstantAnimationsRule` only saves and
 * restores the global `ValueAnimator` duration scale around the test body, and JUnit runs one
 * test at a time per class.
 */
val oxideInstantAnimations: InstantAnimationsRule = InstantAnimationsRule()

/**
 * [Paparazzi] on [device]. Paparazzi 2.x has no `paparazzi { }` DSL, so tests build the rule by hand.
 *
 * Named `paparazziFor` rather than `paparazzi` so it cannot be confused with the `@Rule` field of the
 * same name inside a test class.
 */
fun paparazziFor(device: DeviceConfig, maxPercentDifference: Double = 0.1): Paparazzi = Paparazzi(
    deviceConfig = device,
    maxPercentDifference = maxPercentDifference,
)

/**
 * Two fixed Material schemes.
 *
 * Deliberately not `ColorScheme()` and not the dynamic-*-family: the default M3 baseline moves
 * between Compose releases, and dynamic colour needs a system wallpaper that does not exist under
 * layoutlib. Both are pinned to values that will not change under us.
 */
private val OXIDE_DARK = darkColorScheme(
    primary = Color(0xFFDDDDDD),
    onPrimary = Color(0xFF0B0B0B),
    background = Color(0xFF050505),
    surface = Color(0xFF0D0D0D),
)

private val OXIDE_LIGHT = lightColorScheme(
    primary = Color(0xFF2B2B2B),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F6F6),
    surface = Color(0xFFFFFFFF),
)