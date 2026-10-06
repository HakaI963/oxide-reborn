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

package dev.oxide.launcher.ui.screens.game.elements.log_parser

import androidx.compose.ui.graphics.Color
import dev.oxide.launcher.ui.theme.MIN_TEXT_CONTRAST
import dev.oxide.launcher.ui.theme.OxideChrome
import dev.oxide.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏内日志框的配色守卫
 *
 * 用户报的是"游戏里打开日志，字是黑的，和底色一样，看不清"。根因在
 * `LogBox.kt`：正文 `Text` 根本没传颜色，于是 Material3 取
 * `LocalContentColor`，而 `GameScreen` 与 `LogBox` 之间没有任何 `Surface`，
 * 最后落到 Compose 默认的 `Color.Black`——在 `Oxide.PopoverBg` 的深色值
 * `0xFA0D0D0D` 上只有 1.1:1。截图里唯一能看清的是那几个带底色的等级徽章，
 * 因为徽章自己提供了对比度。
 *
 * 这些都是纯数据：`LogLevelRule` 与 `LogHighlighter` 的颜色都是构造参数里
 * 的常量，`LogParseCore` 不碰 Android API，所以这里不需要 Compose，也不需要
 * Robolectric。对比度用 `ui.theme` 里那个 `contrastRatio`（WCAG 定义，
 * `OxideChromeTest` 已经用 WCAG 参考值钉过它本身），阈值沿用同一份
 * `MIN_TEXT_CONTRAST`：日志正文是最小的一档字号，按小字标准 4.5:1 算，
 * 而不是大字标准的 3:1。
 *
 * 断言的是**有效文字颜色**：带徽章的规则看白字压在徽章上，没徽章的规则就得
 * 自己扛下两种底幕。徽章方案之所以更稳，是因为 `Oxide.PopoverBg` 跟着主题
 * 翻面（深色近黑、浅色纯白），而徽章是不透明的，白字在徽章上的对比度与
 * 主题无关。
 */
class LogPaletteContrastTest {

    /** 高亮器可能匹配到的等级，与 `LogParseCore` 里那张表一致 */
    private val levels = listOf("INFO", "ERROR", "DEBUG", "WARN")

    private val darkPopover = OxideChrome.Dark.popoverBg
    private val lightPopover = OxideChrome.Light.popoverBg

    // -----------------------------------------------------------------------
    // 底幕本身
    // -----------------------------------------------------------------------

    /**
     * 两个底幕还是这两个值。
     *
     * 本测试刻意直接引用 `OxideChrome` 而不是把十六进制抄一遍，这样主题那边
     * 换了底色这里会跟着走；但也正因如此，这里得钉住"跟着走的是不是那两个
     * 已知值"，否则一次悄悄的底色调整会让上面所有对比度断言失去意义。
     */
    @Test
    fun `the log overlay surfaces are the two popover backgrounds`() {
        assertEquals(0xFA0D0D0DL, darkPopover.toArgbLong())
        assertEquals(0xFFFFFFFFL, lightPopover.toArgbLong())
    }

    /** 正文前景色在**自己那一套**主题的底幕上要读得清 */
    @Test
    fun `the log body colour clears the small text threshold in both themes`() {
        val themes = listOf(
            "dark" to (OxideChrome.Dark.fg to darkPopover),
            "light" to (OxideChrome.Light.fg to lightPopover),
        )
        for ((theme, pair) in themes) {
            val (fg, bg) = pair
            assertEquals("fg must be opaque", 1f, fg.alpha, 0f)
            assertTrue(
                "$theme: fg on the log popover is only ${contrastRatio(fg, bg)}:1",
                contrastRatio(fg, bg) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    /**
     * 这就是 v1.7.0/v1.8.0 那个 bug 的数值，也是这个测试存在的理由。
     *
     * 不传 `color` 的 `Text` 走的就是 `Color.Black`，而它只在深色底幕上塌掉；
     * 浅色底幕上它是 21:1（纯黑对纯白），看起来一切正常——这正是它能一路
     * 活到 v1.8.0 的原因。
     */
    @Test
    fun `the unspecified text colour that used to be resolved is black on the dark popover`() {
        assertTrue(
            "an unspecified Text colour lands on Color.Black, which is unreadable in dark",
            contrastRatio(Color.Black, darkPopover) < MIN_TEXT_CONTRAST,
        )
        assertTrue(
            "which is why the same omission looked fine in light",
            contrastRatio(Color.Black, lightPopover) >= MIN_TEXT_CONTRAST,
        )
    }

    // -----------------------------------------------------------------------
    // 等级徽章
    // -----------------------------------------------------------------------

    /** 四条规则都还认得出来，徽章才是后面那些断言的前提 */
    @Test
    fun `every level the highlighter can match still resolves to a rule`() {
        for (level in levels) {
            assertNotNull("no rule matches $level", LogParseCore.findLevelRule(level))
        }
    }

    /**
     * 不允许再有"没有徽章"的等级规则。
     *
     * `ERROR` 原来就是 `backgroundColor = null` 加一个绿的字色，那个绿在浅色
     * 底幕上只有 2.7:1。它同时和 `stringColor` 撞色，于是既读不清也分不出
     * "字符串"与"错误"。徽章一给，两件事一起解决。
     */
    @Test
    fun `every level rule carries an opaque badge`() {
        for (level in levels) {
            val badge = ruleFor(level).backgroundColor
            assertNotNull(
                "$level has no badge, so its text colour has to carry the contrast " +
                    "against a theme-dependent surface on its own",
                badge,
            )
            assertEquals("$level badge must be opaque", 1f, badge!!.alpha, 0f)
        }
    }

    /** 白字压在徽章上要读得清——与主题无关，所以一条规则只需过一次 */
    @Test
    fun `level text clears the small text threshold on its own badge`() {
        for (level in levels) {
            val rule = ruleFor(level)
            assertEquals("$level text must be opaque", 1f, rule.textColor.alpha, 0f)
            assertTrue(
                "$level: ${rule.textColor} on badge ${rule.backgroundColor} is only " +
                    "${contrastRatio(rule.textColor, rule.backgroundColor!!)}:1",
                contrastRatio(rule.textColor, rule.backgroundColor!!) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    /**
     * 徽章自己也要能从底幕上认出来。
     *
     * 3:1 是 WCAG 对非文字图形（这里是色块）的标准，比正文宽松一档。
     * 顺带也说明为什么徽章方案的结论对两种主题都成立：深色底幕上白字+徽章
     * 赢在徽章上，浅色底幕上白字+徽章赢在徽章上，中间那个 98% 不透明底幕
     * 始终不是读出来的那个东西。
     */
    @Test
    fun `every badge stays visible on both popover backgrounds`() {
        for (level in levels) {
            val badge = ruleFor(level).backgroundColor!!
            for ((theme, surface) in listOf("dark" to darkPopover, "light" to lightPopover)) {
                assertTrue(
                    "$level badge on the $theme popover is only ${contrastRatio(badge, surface)}:1",
                    contrastRatio(badge, surface) >= 3f,
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 着色器
    // -----------------------------------------------------------------------

    /**
     * 着色器兜底路径的颜色由调用方给，所以这里测的是调用方给的东西。
     *
     * 深色主题给深色主题的前景色，浅色给浅色的，各自在自己的底幕上都要
     * 4.5:1 以上。旧代码写死的 `Color.White` 在浅色底幕上是 1.0:1——正好
     * 被这条断言挡住。
     */
    @Test
    fun `the highlighter fallback colour clears the threshold in both themes`() {
        val themes = listOf(
            "dark" to (LogHighlighter(defaultColor = OxideChrome.Dark.fg) to darkPopover),
            "light" to (LogHighlighter(defaultColor = OxideChrome.Light.fg) to lightPopover),
        )
        for ((theme, pair) in themes) {
            val (highlighter, surface) = pair
            assertTrue(
                "$theme: highlighter default is only ${contrastRatio(highlighter.defaultColor, surface)}:1",
                contrastRatio(highlighter.defaultColor, surface) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    /** 旧的两个字面量兜底色各只在一种主题上成立——这正是它们被换掉的原因 */
    @Test
    fun `the old hardcoded white fallback only ever worked on the dark popover`() {
        assertTrue(
            "Color.White was correct on the dark popover",
            contrastRatio(Color.White, darkPopover) >= MIN_TEXT_CONTRAST,
        )
        assertTrue(
            "and invisible on the light one, which is why it cannot be the default",
            contrastRatio(Color.White, lightPopover) < MIN_TEXT_CONTRAST,
        )
    }

    /**
     * 其余几个语法着色在深色底幕上都要留在 4.5:1 以上。
     *
     * 只钉深色那一面：这些是从 Zalith 继承的固定调色板，与主题无关，本次
     * 没有动它们（字符串 7.1:1 / 数字、包名、链接 6.5:1 / 时间只有 4.5:1，
     * 是这一组里最紧的一个）。
     * 浅色那一面它们仍在 4.5:1 以下（字符串 2.7:1、数字 3.0:1），那是既有状态、
     * 不在本次修复范围内，所以这里不写成断言——写成了就会在有人把它修好时炸掉。
     * 正文 16.8:1 与等级徽章 5.2:1 以上已经过线，浅色主题下日志整体是可读的。
     *
     * 这里也不要求语法色和正文色互相区分：那是一行里相邻两段文字的关系，
     * WCAG 管的是文字与它**背后那个面**的对比度，不是两段字彼此的对比度。
     */
    @Test
    fun `the syntax colours stay readable on the dark popover`() {
        val highlighter = LogHighlighter(defaultColor = OxideChrome.Dark.fg)
        val syntax = listOf(
            "time" to highlighter.timeColor,
            "string" to highlighter.stringColor,
            "number" to highlighter.numberColor,
            "package" to highlighter.packageColor,
            "link" to highlighter.linkColor,
        )
        for ((name, color) in syntax) {
            assertEquals("$name must be opaque", 1f, color.alpha, 0f)
            assertTrue(
                "$name (${color}) on the dark popover is only ${contrastRatio(color, darkPopover)}:1",
                contrastRatio(color, darkPopover) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun ruleFor(level: String): LogLevelRule =
        LogParseCore.findLevelRule(level) ?: error("no rule matches $level")

    /**
     * 打包成 ARGB 整数，只为了让上面那两条断言能和字面量比。
     *
     * `red` / `green` / `blue` / `alpha` 就是 sRGB 分量，所以这里是照着
     * ARGB 的位序手工拼的，不依赖任何转换函数。
     */
    private fun Color.toArgbLong(): Long =
        ((alpha * 255f).toLong() shl 24) or
            ((red * 255f).toLong() shl 16) or
            ((green * 255f).toLong() shl 8) or
            (blue * 255f).toLong()
}