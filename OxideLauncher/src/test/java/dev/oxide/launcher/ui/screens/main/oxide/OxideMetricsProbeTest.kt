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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.ui.unit.dp
import org.junit.Test

/**
 * TEMPORARY bisection probe - delete once the IllegalArgumentException in oxideMetricsFor is fixed.
 *
 * Every other test in this package dies at the call to oxideMetricsFor with an
 * IllegalArgumentException and no message. Each case below calls exactly one helper, so the first
 * one that throws names the culprit instead of leaving it to guesswork.
 */
class OxideMetricsProbeTest {

    @Test
    fun `probe 01 width class`() {
        println("PROBE widthClass=" + oxideWidthClassFor(320))
    }

    @Test
    fun `probe 02 sidebar width`() {
        println("PROBE sidebarWidth=" + oxideSidebarWidthFor(oxideWidthClassFor(320)))
    }

    @Test
    fun `probe 03 sidebar padding`() {
        println("PROBE sidebarPaddingH=" + oxideSidebarPaddingHFor(oxideWidthClassFor(320)))
    }

    @Test
    fun `probe 04 brand gap`() {
        println("PROBE brandGap=" + oxideBrandGapFor(oxideWidthClassFor(320)))
    }

    @Test
    fun `probe 05 gui scale factor`() {
        println("PROBE guiScaleFactor=" + oxideGuiScaleFactor(100))
    }

    @Test
    fun `probe 06 nav text line height`() {
        println("PROBE navTextLineHeight=" + oxideNavTextLineHeight(1f))
    }

    @Test
    fun `probe 07 nav item height`() {
        println("PROBE navItemHeight=" + oxideNavItemHeightFor(480, oxideWidthClassFor(320), 100))
    }

    @Test
    fun `probe 09 brand logo scale`() {
        println("PROBE brandLogoScale=" + oxideBrandLogoScale(112.dp))
    }

    @Test
    fun `probe 10 page padding`() {
        println("PROBE pagePaddingH=" + oxidePagePaddingHFor(oxideWidthClassFor(320)))
    }

    @Test
    fun `probe 11 nav step`() {
        println("PROBE navStep=" + oxideNavStep(oxideNavItemHeightFor(480, oxideWidthClassFor(320), 100)))
    }

    @Test
    fun `probe 12 full metrics`() {
        println("PROBE metrics=" + oxideMetricsFor(320, 480, 100))
    }

    @Test
    fun `probe 13 full metrics wide`() {
        println("PROBE metricsWide=" + oxideMetricsFor(1920, 480, 100))
    }

    @Test
    fun `probe 14 grid columns on a zero content width`() {
        val m = oxideMetricsFor(1920, 480, 100)
        println("PROBE gridZero=" + m.gridColumns(0.dp))
        println("PROBE gridNormal=" + m.gridColumns(1000.dp))
    }
}
