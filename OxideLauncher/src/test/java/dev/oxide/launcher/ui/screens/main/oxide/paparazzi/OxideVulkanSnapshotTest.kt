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

import androidx.compose.material3.Text
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.OxidePaparazzi.shot
import dev.oxide.launcher.ui.screens.main.oxide.paparazzi.oxideInstantAnimations
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.vulkan_checker.OxideVulkanDialog
import dev.oxide.launcher.ui.vulkan_checker.OxideVulkanResultList
import dev.oxide.launcher.utils.device.VulkanCapabilities
import app.cash.paparazzi.InstantAnimationsRule
import org.junit.Rule
import org.junit.Test

/**
 * The Vulkan check dialog, in both of its shapes.
 *
 * [OxideVulkanDialog] and [OxideVulkanResultList] take a `VulkanCapabilities?` and nothing else —
 * the whole native probe has already happened by the time either of them composes. They were
 * `private`; widening them to `internal` was the only change. [OxideFake.vulkanCapabilities] supplies
 * the report, so no driver, no GPU name table and no `libvulkan_checker` shared object is needed.
 *
 * Both shapes are covered because they differ in *who scrolls*, and that difference has caused a
 * crash before: the tip form lets the body scroll under a content-height panel, while the result
 * form fixes the panel height and gives the scroll to a bounded inner list instead. Two nested
 * vertical scroll containers, or a `maxHeight = Infinity` reaching `verticalScroll`, are the two ways
 * that goes wrong — and a golden at each width is what makes either visible.
 */
class OxideVulkanSnapshotTest {

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
     * The result form.
     *
     * `boundedResult = true` is the important argument here: it is the flag that switches the panel
     * from `heightIn(max = …)` to a fixed height, which is what gives the inner list a finite bound.
     */
    @Test
    fun VulkanCheck_Result() {
        val device = OxidePaparazzi.STANDARD
        val caps = OxideFake.vulkanCapabilities()
        paparazzi.shot("VulkanCheck_Result", device) { metrics ->
            OxideVulkanDialog(
                metrics = metrics,
                subtitle = "1.3.268 - Turnip: true",
                boundedResult = true,
                onConfirm = {},
            ) { listMaxHeight ->
                OxideVulkanResultList(
                    metrics = metrics,
                    listMaxHeight = listMaxHeight,
                    data = caps,
                )
            }
        }
    }

    /**
     * The same report on the compact width.
     *
     * `vulkanDialogBounds` derives the panel from the *dialog's own* measured window rather than from
     * `LocalConfiguration`, so at 640x360 it lands on a different panel size — and a dialog that only
     * ever gets one size tested is a dialog whose height arithmetic is untested.
     */
    @Test
    fun VulkanCheck_Result_Compact() {
        val device = OxidePaparazzi.COMPACT
        val caps = OxideFake.vulkanCapabilities()
        paparazzi.shot("VulkanCheck_Result_Compact", device) { metrics ->
            OxideVulkanDialog(
                metrics = metrics,
                subtitle = "1.3.268 - Turnip: true",
                boundedResult = true,
                onConfirm = {},
            ) { listMaxHeight ->
                OxideVulkanResultList(
                    metrics = metrics,
                    listMaxHeight = listMaxHeight,
                    data = caps,
                )
            }
        }
    }

    /**
     * A report where every feature is missing.
     *
     * The body switches its headline on `profiles.all { it.supported }` versus
     * `profiles.none { it.supported }`, and the unsupported rows carry a different foreground colour.
     * A golden built only from a healthy report would never render the "unsupported" wording.
     */
    @Test
    fun VulkanCheck_Result_Unsupported() {
        val device = OxidePaparazzi.STANDARD
        val caps: VulkanCapabilities = OxideFake.vulkanCapabilities(
            features = mapOf(
                "shaderDrawParameters" to false,
                "geometryShader" to false,
                "textureCompressionASTC_LDR" to false,
                "dynamicRendering" to false,
            ),
        )
        paparazzi.shot("VulkanCheck_Result_Unsupported", device) { metrics ->
            OxideVulkanDialog(
                metrics = metrics,
                subtitle = "1.3.268 - Turnip: false",
                boundedResult = true,
                onConfirm = {},
            ) { listMaxHeight ->
                OxideVulkanResultList(
                    metrics = metrics,
                    listMaxHeight = listMaxHeight,
                    data = caps,
                )
            }
        }
    }

    /**
     * The probe failed outright.
     *
     * `data = null` is not an empty report: the panel keeps its fixed height and the list collapses
     * to a single line saying the check could not run. Conflating the two would tell the user their
     * GPU is unsupported when in fact nothing was measured.
     */
    @Test
    fun VulkanCheck_Failed() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("VulkanCheck_Failed", device) { metrics ->
            OxideVulkanDialog(
                metrics = metrics,
                subtitle = "Turnip: true",
                boundedResult = true,
                onConfirm = {},
            ) { listMaxHeight ->
                OxideVulkanResultList(
                    metrics = metrics,
                    listMaxHeight = listMaxHeight,
                    data = null,
                )
            }
        }
    }

    /**
     * The tip form, where the panel sizes to its content and the body scrolls instead.
     *
     * The body text here is authored in the test rather than read from resources, because the tip is
     * the one dialog whose content is a single static paragraph and its purpose is the panel's
     * height behaviour, not its wording.
     */
    @Test
    fun VulkanCheck_Tip() {
        val device = OxidePaparazzi.STANDARD
        paparazzi.shot("VulkanCheck_Tip", device) { metrics ->
            OxideVulkanDialog(
                metrics = metrics,
                onConfirm = {},
            ) {
                Text(
                    text = "Minecraft needs Vulkan. Oxide can check whether this device's " +
                        "driver exposes everything the selected version requires.",
                    color = Oxide.FgMuted,
                )
            }
        }
    }
}