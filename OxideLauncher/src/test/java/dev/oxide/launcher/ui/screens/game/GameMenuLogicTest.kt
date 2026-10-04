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

package dev.oxide.launcher.ui.screens.game.elements

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import dev.oxide.launcher.setting.enums.ResolutionRule
import dev.oxide.launcher.utils.computeGameRenderSize
import dev.oxide.launcher.utils.customResolutionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏内菜单的纯逻辑
 *
 * 菜单画在一块正在运行的游戏上，所以这几件事必须能被钉死：
 *
 * - 自定义分辨率的合法范围是**当前窗口**的函数（`customResolutionRange`）。
 *   它以前被 `remember(context)` 缓存着，旋转之后夹紧区间还是旧屏幕那一套；
 *   现在范围每次都从测量到的窗口重新推导，因此这里把"换窗口就换范围"写成断言。
 * - 规则 → 实际渲染分辨率（`computeGameRenderSize`）这条链没被改坏：
 *   百分比按比例缩放，自定义夹在范围内，未初始化的 0 退回屏幕尺寸。
 * - 面板几何全部来自窗口尺寸，并且在任何窗口、任何界面缩放下都不会超出窗口。
 */
class GameMenuLogicTest {

    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals(expected, actual.value, 0.01f)
    }

    // ---- 分区 ---------------------------------------------------------------

    @Test
    fun `section index maps to a real section and never throws`() {
        assertEquals(GameMenuSection.Game, GameMenuSection.fromIndex(0))
        assertEquals(GameMenuSection.Controls, GameMenuSection.fromIndex(1))
        assertEquals(GameMenuSection.Mouse, GameMenuSection.fromIndex(2))
        assertEquals(GameMenuSection.Gamepad, GameMenuSection.fromIndex(3))
        assertEquals(GameMenuSection.Gestures, GameMenuSection.fromIndex(4))
        assertEquals(GameMenuSection.Gyroscope, GameMenuSection.fromIndex(5))

        // 越界退回游戏本体，而不是崩在一块没被组合过的分区上
        assertEquals(GameMenuSection.Game, GameMenuSection.fromIndex(-1))
        assertEquals(GameMenuSection.Game, GameMenuSection.fromIndex(99))
        assertEquals(6, GameMenuSection.count)
    }

    // ---- 可见性 -------------------------------------------------------------

    @Test
    fun `only the percentage rule shows the scale slider`() {
        assertTrue(gameMenuShowsResolutionScale(ResolutionRule.PERCENTAGE))
        assertFalse(gameMenuShowsResolutionScale(ResolutionRule.CUSTOM))
    }

    @Test
    fun `only the custom rule shows the width and height inputs`() {
        assertTrue(gameMenuShowsCustomResolution(ResolutionRule.CUSTOM))
        assertFalse(gameMenuShowsCustomResolution(ResolutionRule.PERCENTAGE))
    }

    // ---- 自定义分辨率的合法范围 ---------------------------------------------

    @Test
    fun `custom resolution range is twenty to three hundred percent of the given side`() {
        assertEquals(216..3240, customResolutionRange(1080))
        assertEquals(80..1200, customResolutionRange(400))
    }

    @Test
    fun `custom resolution range rounds the sides it derives down to even numbers`() {
        // 321 * 0.2 = 64.2 -> 64（偶数），321 * 3 = 963
        assertEquals(64..963, customResolutionRange(321))
    }

    @Test
    fun `a tiny side still yields a usable range instead of an empty one`() {
        val tiny = customResolutionRange(2)
        assertEquals(2, tiny.first)
        assertTrue("max must never be below min", tiny.last >= tiny.first)
    }

    @Test
    fun `rotating the window re-derives the clamp range`() {
        // 旋转前后的窗口尺寸换了，夹紧区间必须跟着换。
        // 以前这里缓存的是 getRealScreenSize(context)，旋转之后一直是旧值。
        val landscape = customResolutionRange(2400)
        val portrait = customResolutionRange(1080)

        assertEquals(480..7200, landscape)
        assertEquals(216..3240, portrait)
        assertFalse("a rotated window must not reuse the previous clamp range", landscape == portrait)
    }

    // ---- 规则 -> 实际渲染分辨率 ----------------------------------------------

    @Test
    fun `percentage rule scales the window down`() {
        assertEquals(
            IntSize(800, 450),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.PERCENTAGE, 50, 0, 0)
        )
        assertEquals(
            IntSize(1600, 900),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.PERCENTAGE, 100, 0, 0)
        )
    }

    @Test
    fun `percentage rule still snaps to even sides`() {
        // 900 * 0.25 = 225 是奇数，取 224
        assertEquals(
            IntSize(400, 224),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.PERCENTAGE, 25, 0, 0)
        )
    }

    @Test
    fun `custom rule uses the exact size and snaps it to even`() {
        assertEquals(
            IntSize(1280, 720),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.CUSTOM, 100, 1280, 720)
        )
        assertEquals(
            IntSize(1280, 720),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.CUSTOM, 100, 1281, 721)
        )
    }

    @Test
    fun `custom rule falls back to the window when the stored size is not initialized`() {
        assertEquals(
            IntSize(1600, 900),
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.CUSTOM, 100, 0, 0)
        )
    }

    @Test
    fun `custom rule clamps a size outside the range into it`() {
        val range = customResolutionRange(1600)

        assertEquals(
            range.last,
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.CUSTOM, 100, 10000, 720).width
        )
        assertEquals(
            range.first,
            computeGameRenderSize(IntSize(1600, 900), ResolutionRule.CUSTOM, 100, 100, 720).width
        )
    }

    // ---- 面板几何 -----------------------------------------------------------

    @Test
    fun `a normal landscape phone gets a capped panel with a comfortable margin`() {
        // 2400x1080 px，density 3 -> 800x360 dp
        val metrics = gameMenuMetricsFor(windowWidthPx = 2400, windowHeightPx = 1080, density = 3f)

        assertDp(340f, metrics.panelWidth)
        assertDp(12.6f, metrics.edgeMargin)
        assertDp(334.8f, metrics.panelHeight)
        assertDp(26f, metrics.controlHeight)
        assertDp(10.8f, metrics.contentPadding)
    }

    @Test
    fun `a very small window still fits the panel inside it`() {
        // 160x120 dp：面板宽度按比例只有 99dp，于是被下限抬到 220，
        // 抬完又必须缩回窗口以内
        val metrics = gameMenuMetricsFor(windowWidthPx = 160, windowHeightPx = 120, density = 1f)

        assertDp(148f, metrics.panelWidth)
        assertDp(6f, metrics.edgeMargin)
        assertDp(108f, metrics.panelHeight)
        assertTrue(metrics.panelWidth.value + metrics.edgeMargin.value * 2 <= 160f)
    }

    @Test
    fun `an extremely short window neither crashes nor inverts the clamp`() {
        // 高度只剩 28dp：以前这种窗口会让 coerceIn 拿到一个空区间
        val metrics = gameMenuMetricsFor(windowWidthPx = 100, windowHeightPx = 40, density = 1f)

        assertDp(88f, metrics.panelWidth)
        assertDp(28f, metrics.panelHeight)
    }

    @Test
    fun `the panel never overflows the window at any gui scale`() {
        for (percent in listOf(75, 100, 125, 150)) {
            for (window in listOf(100 to 40, 160 to 120, 320 to 240, 640 to 360, 1280 to 720, 2560 to 1600)) {
                val metrics = gameMenuMetricsFor(
                    windowWidthPx = window.first,
                    windowHeightPx = window.second,
                    density = 1f,
                    guiScalePercent = percent,
                )
                assertTrue(
                    "panel ${metrics.panelWidth} + margins must fit $window at $percent%",
                    metrics.panelWidth.value + metrics.edgeMargin.value * 2 <= window.first + 0.01f
                )
                assertTrue(
                    "panel height ${metrics.panelHeight} must fit $window at $percent%",
                    metrics.panelHeight.value + metrics.edgeMargin.value * 2 <= window.second + 0.01f
                )
                assertTrue(metrics.contentPadding.value > 0f)
                assertTrue(metrics.optionListMaxHeight.value > 0f)
            }
        }
    }

    @Test
    fun `a degenerate window does not divide by zero`() {
        val metrics = gameMenuMetricsFor(windowWidthPx = 0, windowHeightPx = 0, density = 0f)

        assertTrue(metrics.panelWidth.value.isFinite() && metrics.panelWidth.value > 0f)
        assertTrue(metrics.panelHeight.value.isFinite() && metrics.panelHeight.value > 0f)
    }

    // ---- 滑杆 ---------------------------------------------------------------

    @Test
    fun `slider fraction is the position inside the range`() {
        assertEquals(0f, gameMenuSliderFraction(0f, 0f..100f), 1e-5f)
        assertEquals(0.5f, gameMenuSliderFraction(50f, 0f..100f), 1e-5f)
        assertEquals(1f, gameMenuSliderFraction(100f, 0f..100f), 1e-5f)
        // 超出范围的一律夹回端点
        assertEquals(0f, gameMenuSliderFraction(-10f, 0f..100f), 1e-5f)
        assertEquals(1f, gameMenuSliderFraction(150f, 0f..100f), 1e-5f)
    }

    @Test
    fun `a degenerate range never divides by zero`() {
        assertEquals(0f, gameMenuSliderFraction(5f, 5f..5f), 1e-5f)
        assertEquals(5f, gameMenuSliderValue(0.5f, 5f..5f), 1e-5f)
    }

    @Test
    fun `slider value is the inverse of the fraction`() {
        assertEquals(25f, gameMenuSliderValue(0f, 25f..300f), 1e-5f)
        assertEquals(300f, gameMenuSliderValue(1f, 25f..300f), 1e-5f)
        // 比例被夹在 0..1
        assertEquals(25f, gameMenuSliderValue(-2f, 25f..300f), 1e-5f)
        assertEquals(300f, gameMenuSliderValue(4f, 25f..300f), 1e-5f)

        val value = 37f
        val range = 0f..100f
        assertEquals(value, gameMenuSliderValue(gameMenuSliderFraction(value, range), range), 1e-3f)
    }

    @Test
    fun `options only get their own scroll once they stop fitting`() {
        assertFalse(gameMenuChoiceUsesOwnScroll(0))
        assertFalse(gameMenuChoiceUsesOwnScroll(GameMenuInlineOptionRows))
        assertTrue(gameMenuChoiceUsesOwnScroll(GameMenuInlineOptionRows + 1))
    }

    // ---- 数字 ---------------------------------------------------------------

    @Test
    fun `slider values keep the integer display they had before`() {
        assertEquals("100%", formatGameMenuValue(100f, "%"))
        assertEquals("1280", formatGameMenuValue(1280f, null))
        assertEquals("4", formatGameMenuValue(4f, null))
        assertEquals("12ms", formatGameMenuValue(12f, "ms"))
        assertEquals("24Dp", formatGameMenuValue(24f, "Dp"))
    }

    @Test
    fun `fractional values are formatted without a formatter per frame`() {
        assertEquals("1.5%", formatGameMenuValue(1.5f, "%", decimals = 1))
        assertEquals("0.25", formatGameMenuValue(0.25f, null, decimals = 2))
        assertEquals("2.3", formatGameMenuValue(2.345f, null, decimals = 1))
        assertEquals("-1.5", formatGameMenuValue(-1.5f, null, decimals = 1))
        assertEquals("10.00", formatGameMenuValue(10f, null, decimals = 2))
    }

    @Test
    fun `a valid number inside the range is accepted`() {
        assertEquals(100f, gameMenuNumberIn("100", 25f..300f, integerOnly = true))
        assertEquals(50.5f, gameMenuNumberIn("50.5", 0f..100f, integerOnly = false))
        assertNull(gameMenuNumberError("100", 25f..300f, integerOnly = true))
    }

    @Test
    fun `text that is not a number is refused and named as such`() {
        assertNull(gameMenuNumberIn("", 25f..300f, integerOnly = true))
        assertNull(gameMenuNumberIn("abc", 25f..300f, integerOnly = true))
        assertNull(gameMenuNumberIn("NaN", 25f..300f, integerOnly = false))
        assertEquals(
            GameMenuNumberError.NotANumber,
            gameMenuNumberError("abc", 25f..300f, integerOnly = true)
        )
    }

    @Test
    fun `an integer field refuses decimals`() {
        assertNull(gameMenuNumberIn("12.5", 0f..100f, integerOnly = true))
        assertEquals(
            GameMenuNumberError.NotANumber,
            gameMenuNumberError("12.5", 0f..100f, integerOnly = true)
        )
    }

    @Test
    fun `a number outside the range is refused and named as too small or too large`() {
        assertNull(gameMenuNumberIn("24", 25f..300f, integerOnly = true))
        assertNull(gameMenuNumberIn("301", 25f..300f, integerOnly = true))
        assertEquals(
            GameMenuNumberError.TooSmall,
            gameMenuNumberError("24", 25f..300f, integerOnly = true)
        )
        assertEquals(
            GameMenuNumberError.TooLarge,
            gameMenuNumberError("301", 25f..300f, integerOnly = true)
        )
    }

    @Test
    fun `the range itself parses back into what the custom resolution field allows`() {
        // 行内输入框显示的合法区间与真正落盘时夹的区间必须是同一套数字
        val permitted = customResolutionRange(1080)
        val asFloats = permitted.first.toFloat()..permitted.last.toFloat()

        assertEquals(permitted.first.toFloat(), gameMenuNumberIn("${permitted.first}", asFloats, true))
        assertEquals(permitted.last.toFloat(), gameMenuNumberIn("${permitted.last}", asFloats, true))
        assertNull(gameMenuNumberIn("${permitted.first - 2}", asFloats, true))
    }
}