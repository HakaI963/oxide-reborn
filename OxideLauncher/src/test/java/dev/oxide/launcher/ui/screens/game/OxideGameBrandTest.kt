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

package dev.oxide.launcher.ui.screens.game.elements

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 游戏画面上的 Oxide 品牌标识，以及它依赖的那几条字符串
 *
 * 这一块压在**正在运行的游戏**上，所以有三件事错了就会在设备上看得见，
 * 而且没有一件是编译期能发现的：
 *
 * 1. **它吃掉了一次触摸。** 标识画在游戏浮层里——如果它带 pointerInput 或者
 *    clickable，那一格就不再参与穿透，玩家按在"右下角"的动作会静默地落空。
 *    标识必须完全不参与命中测试。
 * 2. **它不跟开关走。** 用户在设置里关掉之后它仍然出现，开关就成了摆设。
 * 3. **它在放不下的窗口上裁掉半个 logo。** 分屏与自由窗口都会走到这一支。
 *
 * 这些都是"读源码就能钉死"的事实，因此不写成 Compose 仪器测试，断言跑在
 * **去掉注释与字符串之后**的源码上：注释里提到 pointerInput 是为了解释
 * "为什么这里没有 pointerInput"，那不该反过来满足一条存在性断言。
 *
 * 顺带把这一批新加的字符串钉住：它们由别的功能引入，却没有任何编译期约束
 * （`R.string.foo` 写错才会报错，但删掉一条定义同样只在运行时才炸）。
 */
class OxideGameBrandTest {

    private val brand = code(readSource("ui/screens/game/elements/OxideGameBrand.kt"))
    private val gameScreen = code(readSource("ui/screens/game/GameScreen.kt"))
    // 不剥字符串：设置键本身就是字面量，剥掉就再也断言不到它
    private val allSettings = readSource("setting/AllSettings.kt")
    private val settingsPage = code(readSource("ui/screens/main/oxide/OxideSettingsPage.kt"))
    private val safeInsets = code(readSource("ui/components/_SafeInsets.kt"))
    private val strings = readSource("res/values/strings.xml")

    // -------------------------------------------------------------------------
    // 标识不吃触摸
    // -------------------------------------------------------------------------

    @Test
    fun theBrandNeverConsumesPointerInput() {
        // 这是本文件最重要的一条：标识画在游戏浮层里，它要是参与命中测试，
        // 按在它那一格的触摸就不会再落到游戏 surface 或下面的控制布局控件上。
        // Modifier.padding 与 Modifier.alpha 都不碰指针，因此这两个是允许的。
        for (banned in listOf(
            "pointerInput",
            "clickable",
            "combinedClickable",
            "toggleable",
            "selectable",
            "indication",
            "interactionSource",
            "MutableInteractionSource",
            "draggable",
            "swipeable",
            "detectTapGestures",
            "detectDragGestures",
            "awaitPointerEventScope",
            "forEachGesture",
            "absorbPointerEvent",
        )) {
            assertFalse(
                "the brand must not take part in hit testing: $banned",
                brand.contains(banned),
            )
        }
    }

    @Test
    fun theBrandIsAStaticDrawingOfTheExistingLogo() {
        // 复用既有品牌，而不是重画：图形与字标都来自 OxideLogo，
        // 比例仍然是参考稿那三个数同乘一个系数。
        assertTrue(
            "the brand must be built from the existing OxideLogo",
            brand.contains("OxideLogo("),
        )
        assertTrue(
            "the wordmark is part of the branding",
            brand.contains("showWordmark = true"),
        )
        assertTrue(
            "the mark side and the wordmark must share one scale factor",
            brand.contains("Oxide.MarkSize * GAME_BRAND_SCALE") &&
                brand.contains("OxideLogoWordmark * GAME_BRAND_SCALE") &&
                brand.contains("OxideLogoGap * GAME_BRAND_SCALE"),
        )
        // 只有一个缩放出口：三个尺寸全部由 GAME_BRAND_SCALE 推出来，
        // 于是换形状/换尺寸时不会出现"图形缩了字标没缩"。
        assertEquals(1, Regex("const val GAME_BRAND_SCALE").findAll(brand).count())
    }

    // -------------------------------------------------------------------------
    // 它必须跟开关走
    // -------------------------------------------------------------------------

    @Test
    fun theBrandIsGatedByItsOwnSetting() {
        assertTrue(
            "AllSettings must declare the setting",
            allSettings.contains("""boolSetting("showGameBrand", true)"""),
        )
        assertTrue(
            "GameScreen must read that setting before composing the brand",
            gameScreen.contains("AllSettings.showGameBrand.state"),
        )
        assertTrue(
            "the gate and the composition have to sit on the same branch",
            Regex(
                """if \(!viewModel\.isEditingLayout && AllSettings\.showGameBrand\.state\) \{\s*OxideGameBrand\(""",
            ).containsMatchIn(gameScreen),
        )
        // 开关读在组合里，因此设置一改就重组，不需要重启
        assertTrue(
            "the gate has to be read during composition, not cached",
            gameScreen.contains("if (!viewModel.isEditingLayout && AllSettings.showGameBrand.state) {"),
        )
    }

    @Test
    fun theSettingsPageSurfacesTheToggleExactlyOnce() {
        assertEquals(
            "the row must be built once, in one place",
            1,
            Regex("AllSettings\\.showGameBrand\\.save").findAll(settingsPage).count(),
        )
        assertTrue(
            "the row has to show the stored value",
            settingsPage.contains("checked = AllSettings.showGameBrand.state"),
        )
        assertTrue(
            "the row has to use the shared Oxide toggle row",
            settingsPage.contains("OxideToggleRow("),
        )
        // 行挂在既有的"游戏内浮层"分组下，不另起一个分组名
        assertTrue(
            "the row belongs to the existing in-game overlay section",
            settingsPage.contains("R.string.oxide_set_section_overlay"),
        )
    }

    // -------------------------------------------------------------------------
    // 位置：右下角，且不吃到默认控件
    // -------------------------------------------------------------------------

    @Test
    fun theBrandIsAlignedToTheBottomEndCorner() {
        assertTrue(
            "the brand has to be composed at the bottom end of the overlay",
            gameScreen.contains("modifier = Modifier.align(Alignment.BottomEnd)"),
        )
        assertTrue(gameScreen.contains("import androidx.compose.ui.Alignment"))
    }

    @Test
    fun theBrandStaysClearOfTheDefaultLayoutControls() {
        // 默认控制布局（assets/default_layout.json）里离右下角最近的是"▢"：
        // 位置 (8233, 7765)、边长为屏高的 10.15%。按钮位置是"上沿 = (屏高 − 按钮高) × y%"，
        // 于是它的下沿落在 (1 − 0.1015) × 0.7765 + 0.1015 = 0.7992 屏高处，
        // 屏幕最下面那 20.08% 是一条空带。标识必须待在那条带子里。
        val expectedFreeBand = 1f - ((1f - 0.1015f) * 0.7765f + 0.1015f)
        assertEquals(
            "the free band below the default control comes from default_layout.json",
            expectedFreeBand.toDouble(),
            DefaultControlFreeBandRatio.toDouble(),
            0.0001,
        )
        // 高度刚够的那一档：装得下就画，装不下就不画。这两行是
        // "不会压在默认控件上面"这条的钉子。用 ±1dp 而不是正好等于临界值——
        // 临界值是"除一遍再乘回来"，浮点上不保证还原成同一个数。
        val justTallEnough = (GameBrandBand.value / DefaultControlFreeBandRatio + 1f).dp
        assertTrue(gameBrandFitsIn(DpSize(GameBrandMinAvailableWidth, justTallEnough)))
        assertFalse(gameBrandFitsIn(DpSize(GameBrandMinAvailableWidth, justTallEnough - 2.dp)))
        // 而一个真实的横屏手机（最短边约 360dp）远远够用——这条不许被收紧
        assertTrue(gameBrandFitsIn(DpSize(360.dp, 800.dp)))
        assertTrue(gameBrandFitsIn(DpSize(800.dp, 360.dp)))
    }

    @Test
    fun theBrandIsLowEmphasis() {
        // 半透明：读得出是启动器，同时退到画面之后，不会让人觉得那一格能按。
        // 更高会开始和 Minecraft 自己的按键抢注意力，更低就读不出来了。
        assertTrue(
            "the alpha has to stay low enough to stay unobtrusive",
            GameBrandAlpha in 0.3f..0.7f,
        )
        assertTrue(
            "the alpha has to be applied to the drawn content",
            brand.contains(".alpha(GameBrandAlpha)"),
        )
    }

    // -------------------------------------------------------------------------
    // 放不下就不画
    // -------------------------------------------------------------------------

    @Test
    fun aWindowTooSmallForTheBrandDrawsNothing() {
        // 分屏与自由窗口：宁可没有，也不能出现半个被裁掉的 logo
        assertFalse(gameBrandFitsIn(DpSize(GameBrandMinAvailableWidth - 1.dp, 800.dp)))
        assertFalse(gameBrandFitsIn(DpSize(0.dp, 0.dp)))
        // 高度不够 → 装不进默认控件下面那条空带 → 不画
        assertFalse(gameBrandFitsIn(DpSize(800.dp, GameBrandBand)))
        assertTrue(gameBrandFitsIn(DpSize(800.dp, 360.dp)))
    }

    @Test
    fun theBrandAppliesTheExistingSafeAreaInsets() {
        // 系统栏与刘海：全屏时是 0，分屏与显示切口模式下非 0。
        // 标识与控制布局编辑器停靠球读的是同一个 rememberSafeScreenInsets()，
        // 不另开一个 inset 来源，也不退回硬编码的边距。
        assertTrue(
            "the brand must inset itself by the shared safe area",
            brand.contains("rememberSafeScreenInsets()"),
        )
        assertTrue(
            "the shared helper must cover system bars and the display cutout",
            safeInsets.contains("WindowInsetsCompat.Type.systemBars()") &&
                safeInsets.contains("WindowInsetsCompat.Type.displayCutout()"),
        )
        assertTrue(
            "the trailing and the bottom edge must both be inset",
            brand.contains("safeInsets.right.toDp()") &&
                brand.contains("safeInsets.bottom.toDp()"),
        )
        assertTrue(
            "absolutePadding takes physical left/right, not start/end",
            brand.contains("absolutePadding(") && !brand.contains("end = "),
        )
    }

    // -------------------------------------------------------------------------
    // 这一批新增的字符串
    // -------------------------------------------------------------------------

    @Test
    fun everyNewStringExistsIsNonEmptyAndIsUnique() {
        for (name in NEW_STRINGS) {
            assertTrue(
                "$name must be present in the default string table",
                strings.contains("name=\"$name\""),
            )
            val value = stringValue(strings, name)
            assertTrue(
                "$name must carry a non-empty value",
                value.isNotBlank(),
            )
            assertEquals(
                "$name must be declared exactly once",
                1,
                Regex("<string name=\"" + Regex.escape(name) + "\"").findAll(strings).count(),
            )
        }
    }

    @Test
    fun theSettingRowStringsAreReadByTheSettingsPage() {
        for (name in listOf(
            "oxide_set_show_game_brand",
            "oxide_set_show_game_brand_detail",
        )) {
            assertTrue(
                "the settings page must read $name",
                settingsPage.contains("R.string.$name"),
            )
        }
    }

    @Test
    fun theControlEditorStringsFollowTheEventNamingScheme() {
        // 三条都在 control_editor_edit_event_ 这一组里，紧挨着 key_value 与
        // toggleable——它们描述的就是同一张事件编辑表单上的行。
        val keyDelay = stringValue(strings, "control_editor_edit_event_key_delay")
        val macro = stringValue(strings, "control_editor_edit_event_macro")
        val macroInterval = stringValue(strings, "control_editor_edit_event_macro_interval")
        assertTrue(
            "the delay row names the same thing as the key it belongs to",
            keyDelay.contains("Trigger Key"),
        )
        assertTrue(
            "the macro toggle says what holding the control does",
            macro.contains("Hold") && macro.contains("Repeat"),
        )
        assertTrue(
            "the macro interval row names the interval",
            macroInterval.contains("Interval"),
        )
        assertTrue(
            "the macro interval row must not be confused with the per-key delay",
            macroInterval != keyDelay,
        )
    }

    @Test
    fun theCrashRotateLabelSaysWhatTheButtonDoes() {
        // 崩溃面板上那个键切的是 requestedOrientation（PORTRAIT / SENSOR_LANDSCAPE），
        // 因此它是一句"旋转屏幕"，而不是"展开"——后者此前是 generic_expand 被借用过来的。
        val rotate = stringValue(strings, "crash_rotate")
        assertTrue(
            "the rotate label must say it rotates",
            rotate.contains("Rotate"),
        )
        assertTrue(
            "the rotate label must name the screen it rotates",
            rotate.contains("screen", ignoreCase = true),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** The value of `<string name="name">value</string>`. */
    private fun stringValue(table: String, name: String): String {
        val start = table.indexOf("name=\"$name\"")
        assertTrue("could not find $name", start >= 0)
        val open = table.indexOf('>', start)
        val close = table.indexOf("</string>", open)
        assertTrue("$name has no closing tag", close >= 0)
        return table.substring(open + 1, close).trim()
    }

    /**
     * Strip string literals and comments, leaving only what gets compiled.
     *
     * Order matters: strings first, then comments. A `//` inside a string literal
     * would otherwise start a line comment and swallow the rest of the file,
     * which turns a genuine failure into a silent pass.
     */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /**
     * Read a file under `src/main`, walking up from the working directory.
     *
     * Two roots, because a source path and a resource path do not share one prefix.
     * Failure is an error rather than a skip — a skipped guard is not a guard.
     */
    private fun readSource(relativePath: String): String {
        val root = if (relativePath.startsWith("res/")) {
            "src/main/$relativePath"
        } else {
            "src/main/java/dev/oxide/launcher/$relativePath"
        }
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(root)
            if (candidate != null && candidate.isFile) return candidate.readText()
            dir = dir?.parentFile
        }
        error("could not locate $root from ${File("").absolutePath}")
    }

    private companion object {
        /** 本次新增、由本文件钉住的那几条字符串 */
        val NEW_STRINGS = listOf(
            "crash_rotate",
            "control_editor_edit_event_key_delay",
            "control_editor_edit_event_macro",
            "control_editor_edit_event_macro_interval",
            "oxide_set_show_game_brand",
            "oxide_set_show_game_brand_detail",
        )

        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}