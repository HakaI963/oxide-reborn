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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Oxide 调色板的对比度契约
 *
 * Oxide 的说明文字只有 6sp，分类标签 8sp，都是 WCAG 意义上的小字，
 * 所以每一档文字都必须在**同一套里最亮的那块底色**上仍然达到 4.5:1。
 * 这条约束以前只写在注释里，v1.4.0 的分类栏就是 2.45:1，
 * 因此这里把它变成会失败的测试，而不是靠人眼判断。
 */
class OxideChromeTest {

    /** 文字落在卡片/面板上时的实际底色：面板叠在页面底色之上 */
    private val darkSurface = compositeOver(OxideChrome.Dark.surfaceBase, OxideChrome.Dark.bg)
    private val lightSurface = compositeOver(OxideChrome.Light.surfaceBase, OxideChrome.Light.bg)

    private fun compositeOver(foreground: Color, background: Color): Color {
        val alpha = foreground.alpha
        return Color(
            red = foreground.red * alpha + background.red * (1f - alpha),
            green = foreground.green * alpha + background.green * (1f - alpha),
            blue = foreground.blue * alpha + background.blue * (1f - alpha),
            alpha = 1f,
        )
    }

    private fun textRamp(chrome: OxideChrome): List<Pair<String, Color>> = listOf(
        "fg" to chrome.fg,
        "fgStrong" to chrome.fgStrong,
        "fgMuted" to chrome.fgMuted,
        "fgDim" to chrome.fgDim,
        "fgFaint" to chrome.fgFaint,
        "fgGhost" to chrome.fgGhost,
    )

    @Test
    fun `every text tier clears the small text threshold in dark mode`() {
        val chrome = OxideChrome.Dark
        for ((name, color) in textRamp(chrome)) {
            for ((surfaceName, surface) in listOf("bg" to chrome.bg, "surface" to darkSurface)) {
                assertTrue(
                    "dark $name on $surfaceName is only " +
                        "${contrastRatio(color, surface)}:1, below $MIN_TEXT_CONTRAST:1",
                    contrastRatio(color, surface) >= MIN_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun `every text tier clears the small text threshold in light mode`() {
        val chrome = OxideChrome.Light
        for ((name, color) in textRamp(chrome)) {
            for ((surfaceName, surface) in listOf("bg" to chrome.bg, "surface" to lightSurface)) {
                assertTrue(
                    "light $name on $surfaceName is only " +
                        "${contrastRatio(color, surface)}:1, below $MIN_TEXT_CONTRAST:1",
                    contrastRatio(color, surface) >= MIN_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun `text tiers stay strictly ordered from most to least prominent`() {
        for (chrome in listOf(OxideChrome.Dark, OxideChrome.Light)) {
            val ramp = textRamp(chrome)
            for (index in 0 until ramp.lastIndex) {
                val (higherName, higher) = ramp[index]
                val (lowerName, lower) = ramp[index + 1]
                assertTrue(
                    "$higherName must read stronger than $lowerName",
                    contrastRatio(higher, chrome.bg) > contrastRatio(lower, chrome.bg)
                )
            }
        }
    }

    @Test
    fun `the inactive page number stays readable against the page background`() {
        for (chrome in listOf(OxideChrome.Dark, OxideChrome.Light)) {
            assertTrue(
                "fgNum is only ${contrastRatio(chrome.fgNum, chrome.bg)}:1",
                contrastRatio(chrome.fgNum, chrome.bg) >= MIN_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `the toggle knob stays readable on the on track in both themes`() {
        // [OxideToggle] 的滑块颜色写死成近黑，轨道必须始终保持浅色
        val knob = Color(0xFF1A1A1A)
        for (chrome in listOf(OxideChrome.Dark, OxideChrome.Light)) {
            assertTrue(
                "knob on bgToggleOn is only ${contrastRatio(knob, chrome.bgToggleOn)}:1",
                contrastRatio(knob, chrome.bgToggleOn) >= MIN_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `a theme accent is pulled into the readable band instead of being dropped`() {
        // 动态取色给出的浅黄落在近白底上只有 1.5:1，必须被压暗而不是原样用上
        val paleYellow = Color(0xFFE8C74A)
        val resolved = OxideChrome.of(dark = false, accent = paleYellow)

        assertTrue(
            "accent is only ${contrastRatio(resolved.accent, resolved.bg)}:1 on the light page",
            contrastRatio(resolved.accent, resolved.bg) >= MIN_TEXT_CONTRAST
        )
        assertTrue(
            "the accent should have been pulled towards the background",
            relativeLuminance(resolved.accent) < relativeLuminance(paleYellow)
        )
        // 色相不变：三个通道按同一个系数收缩，通道之间的比例保持不变
        val keepRatio = paleYellow.green / paleYellow.red
        assertEquals(keepRatio, resolved.accent.green / resolved.accent.red, 0.01f)
    }

    @Test
    fun `an accent that is already readable is used untouched`() {
        val accent = Color(0xFF6750A4)
        assertEquals(
            accent,
            readableAccent(accent, dark = false)
        )
    }

    @Test
    fun `the same accent is resolved differently for light and dark pages`() {
        val accent = Color(0xFF3F7FE0)
        val onDark = OxideChrome.of(dark = true, accent = accent).accent
        val onLight = OxideChrome.of(dark = false, accent = accent).accent

        assertNotEquals(onDark, onLight)
        assertTrue(contrastRatio(onDark, OxideChrome.Dark.bg) >= MIN_TEXT_CONTRAST)
        assertTrue(contrastRatio(onLight, OxideChrome.Light.bg) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun `every role flips with the theme except the ones that pair with a fixed ink`() {
        // 这两个必须留在深色：OxideComponents 里滑块与主按钮的文字是写死的近黑色，
        // 它们一旦跟着主题翻转，按钮上的字就看不见了
        val deliberatelyShared = setOf("bgToggleOn", "drawerScrim")

        val dark = OxideChrome.Dark.roles().toMap()
        val light = OxideChrome.Light.roles().toMap()

        assertEquals(dark.keys, light.keys)
        for ((role, darkValue) in dark) {
            if (role in deliberatelyShared) {
                assertEquals(
                    "$role is expected to stay put across themes",
                    darkValue,
                    light.getValue(role)
                )
            } else {
                assertNotEquals("$role did not follow the theme", darkValue, light.getValue(role))
            }
        }
    }

    @Test
    fun `contrast ratio matches the wcag reference values`() {
        // 拿 WCAG 文档里的两组基准值钉死实现，防止把公式改错却全绿
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.01f)
        // #777 on #FFF is 4.48:1, just short of AA
        assertEquals(4.48f, contrastRatio(Color(0xFF777777), Color.White), 0.02f)
    }

    private fun OxideChrome.roles(): List<Pair<String, Color>> = listOf(
        "bg" to bg,
        "sidebar" to sidebar,
        "surfaceBase" to surfaceBase,
        "line" to line,
        "line2" to line2,
        "lineFaint" to lineFaint,
        "border" to border,
        "bgElevated" to bgElevated,
        "bgButton" to bgButton,
        "bgButtonHover" to bgButtonHover,
        "bgChip" to bgChip,
        "bgTabActive" to bgTabActive,
        "bgToggleOff" to bgToggleOff,
        "bgToggleOn" to bgToggleOn,
        "drawerBg" to drawerBg,
        "drawerScrim" to drawerScrim,
        "popoverBg" to popoverBg,
        "toastBg" to toastBg,
        "fg" to fg,
        "fgStrong" to fgStrong,
        "fgMuted" to fgMuted,
        "fgDim" to fgDim,
        "fgFaint" to fgFaint,
        "fgGhost" to fgGhost,
        "fgNum" to fgNum,
        "wordmarkTail" to wordmarkTail,
        "markBorder" to markBorder,
        "markInner" to markInner,
        "accent" to accent,
    )
}