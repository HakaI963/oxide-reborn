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

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle
import org.junit.Test

/**
 * TEMPORARY bisection probe, round 2 - delete once the IllegalArgumentException is fixed.
 *
 * Round 1 narrowed every failure to oxideNavTextLineHeight, which is just
 * oxideScaledTextStyle(NAV_TEXT_BASE, factor).lineHeight.value.dp. These cases peel that apart one
 * operation at a time, and also check whether merely touching object Oxide / Oxide.Motion is enough
 * to throw.
 */
class OxideTextScaleProbeTest {

    @Test
    fun `probe a text unit times float`() {
        println("PROBE spTimesFloat=" + (14.sp * 1f))
    }

    @Test
    fun `probe b build a bare TextStyle`() {
        val style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)
        println("PROBE bareStyle lineHeight=" + style.lineHeight)
    }

    @Test
    fun `probe c copy with multiplied members`() {
        val base = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)
        val copied = base.copy(
            fontSize = base.fontSize * 1f,
            lineHeight = base.lineHeight * 1f,
            letterSpacing = base.letterSpacing * 1f,
        )
        println("PROBE copied lineHeight=" + copied.lineHeight)
    }

    @Test
    fun `probe d the real helper`() {
        val base = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)
        println("PROBE helper=" + oxideScaledTextStyle(base, 1f).lineHeight)
    }

    @Test
    fun `probe e text unit value to dp`() {
        println("PROBE toDp=" + (14.sp * 1f).value.dp)
    }

    @Test
    fun `probe f touch the Motion object`() {
        println("PROBE motion=" + Oxide.Motion.IntroScaleMin)
    }

    @Test
    fun `probe g touch a size token on the Oxide object`() {
        println("PROBE navItemHeight=" + Oxide.NavItemHeight)
    }

    @Test
    fun `probe h touch a chrome colour`() {
        println("PROBE fg=" + Oxide.Fg)
    }

    @Test
    fun `probe i touch a surface brush`() {
        println("PROBE brush=" + Oxide.SurfaceBrush)
    }

    @Test
    fun `probe j read the whole nav text line height again`() {
        println("PROBE navTextLineHeight=" + oxideNavTextLineHeight(1f))
    }
}
