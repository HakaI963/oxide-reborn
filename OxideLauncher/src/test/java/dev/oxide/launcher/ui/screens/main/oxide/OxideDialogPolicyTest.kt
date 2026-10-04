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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Oxide 对话框的自适应尺寸、有界滚动与输入校验
 *
 * 这些都是纯函数：不组合、不读设置、不碰 Android，因此每一条期望值都可以逐个钉死。
 * 被钉住的是两条硬约束：
 *
 * 1. 面板永远不会比窗口大，也永远不会变成 `Dp.Infinity` / `Dp.Unspecified`——
 *    后者会让 `verticalScroll` 拿到 `maxHeight = Infinity` 并直接崩在布局阶段
 *    （v1.5.0 的 P0 就是这么崩的）；
 * 2. 列表里有多少项都不会把内容区撑高，超出的部分在里面滚掉。
 */
class OxideDialogPolicyTest {

    /** 会用到的窗口尺寸：横屏手机、常见平板、大屏、以及两条要求里点名的 640×360 */
    private val windows = listOf(
        640 to 360,
        800 to 480,
        1024 to 600,
        1280 to 720,
        1920 to 1080,
        2560 to 1440,
        731 to 411,
        480 to 320,
    )

    // ---- 面板尺寸 -----------------------------------------------------------

    @Test
    fun panelNeverExceedsItsWindow() {
        windows.forEach { (width, height) ->
            val size = oxideDialogMaxSize(width, height)
            assertTrue(
                "宽度 ${size.maxWidth} 超过了窗口 $width",
                size.maxWidth <= width.dp,
            )
            assertTrue(
                "高度 ${size.maxHeight} 超过了窗口 $height",
                size.maxHeight <= height.dp,
            )
        }
    }

    /**
     * 面板永远是有限值
     *
     * 这一条是那个 P0 崩溃真正的守门人：`Dp.Infinity` 能通过 `<=` 比较，
     * 但它一路传到 `heightIn` / `verticalScroll` 就是崩。
     */
    @Test
    fun panelIsNeverUnbounded() {
        windows.forEach { (width, height) ->
            val size = oxideDialogMaxSize(width, height)
            assertFinite(size.maxWidth)
            assertFinite(size.maxHeight)
            assertTrue("宽度必须为正", size.maxWidth > 0.dp)
            assertTrue("高度必须为正", size.maxHeight > 0.dp)
        }
    }

    /** 大屏上到理想上限就停住：对话框不是页面，不能把更新日志铺满整个桌面 */
    @Test
    fun panelStopsAtThePreferredSizeOnLargeWindows() {
        val size = oxideDialogMaxSize(2560, 1440)
        assertEquals(OxideDialogPreferredWidth, size.maxWidth)
        assertEquals(OxideDialogPreferredHeight, size.maxHeight)
    }

    /**
     * 640×360 是这次要求里点名的尺寸
     *
     * 宽度上理想上限（460）还装得下，于是保持 460；
     * 高度上理想上限（620）装不下，于是退回"360 减去四周留白"。
     */
    @Test
    fun panelShrinksToFitSmallWindows() {
        val size = oxideDialogMaxSize(640, 360)
        assertEquals(OxideDialogPreferredWidth, size.maxWidth)
        assertEquals(
            (OxideDialogEdgeMargin * 2 + size.maxHeight).value,
            360f,
            0.001f,
        )
        assertTrue(size.maxHeight < OxideDialogPreferredHeight)
    }

    /**
     * 放大界面时面板跟着放大，但仍然不超过窗口
     *
     * 放大到 150% 后理想宽度是 690dp，比 640dp 的窗口还宽——
     * 这时必须由窗口而不是理想值说了算。
     */
    @Test
    fun guiScaleGrowsThePanelButNeverPastTheWindow() {
        listOf(75, 100, 125, 150).forEach { percent ->
            val scale = oxideGuiScaleFactor(percent)
            windows.forEach { (width, height) ->
                val size = oxideDialogMaxSize(width, height, scale)
                assertTrue("缩放 $percent% 下宽度超窗", size.maxWidth <= width.dp)
                assertTrue("缩放 $percent% 下高度超窗", size.maxHeight <= height.dp)
                assertFinite(size.maxWidth)
                assertFinite(size.maxHeight)
            }
        }
    }

    @Test
    fun guiScaleGrowsThePanelOnLargeWindows() {
        assertEquals(
            OxideDialogPreferredWidth * 1.5f,
            oxideDialogMaxSize(2560, 1440, 1.5f).maxWidth,
        )
    }

    /**
     * 退化输入
     *
     * 面板刚创建时容器可能还是 0×0。这里不能返回 0（那会把标题和按钮一起量成 0 高），
     * 也不能返回 NaN，但也不能为了"非零"而画出一个比窗口还大的面板。
     */
    @Test
    fun degenerateWindowStillProducesAFinitePositiveBound() {
        listOf(0, -1, Int.MIN_VALUE).forEach { window ->
            val size = oxideDialogMaxSize(window, window)
            assertEquals(OxideDialogAbsoluteMinSize, size.maxWidth)
            assertEquals(OxideDialogAbsoluteMinSize, size.maxHeight)
        }
    }

    /** 缩放系数本身被夹过，不会再乘出 NaN 或负尺寸 */
    @Test
    fun bogusGuiScaleFallsBackToFullSize() {
        val full = oxideDialogMaxSize(1280, 720)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { bogus ->
            assertEquals(full, oxideDialogMaxSize(1280, 720, bogus))
        }
    }

    // ---- 有界滚动 -----------------------------------------------------------

    /**
     * 内容区的高度上限永远有限
     *
     * 这是"先把面板夹住，再在里面滚"里被夹住的那一半。
     */
    @Test
    fun contentAreaIsAlwaysBounded() {
        windows.forEach { (width, height) ->
            val size = oxideDialogMaxSize(width, height)
            val content = oxideDialogContentMaxHeight(size.maxHeight)
            assertFinite(content)
            assertTrue("内容区必须为正", content > 0.dp)
            assertTrue(
                "内容区 ${content} 不该超过面板 ${size.maxHeight}",
                content <= size.maxHeight,
            )
        }
    }

    /**
     * 极矮的窗口也不会把内容区压成 0
     *
     * 0 高度的滚动容器画不出任何东西，`LazyColumn` 甚至不会进入滚动。
     */
    @Test
    fun contentAreaHasAFloor() {
        val size = oxideDialogMaxSize(240, 100)
        assertEquals(
            OxideDialogMinContentHeight,
            oxideDialogContentMaxHeight(size.maxHeight),
        )
    }

    /**
     * 给定 N 项，内容区的高度是被夹住的
     *
     * 这一条直接对应"有界滚动"策略：项数从 1 涨到 10000，
     * 分配出去的高度最多只到上限，超出的部分在列表里滚掉。
     */
    @Test
    fun listHeightIsCappedHoweverManyItemsThereAre() {
        val metrics = oxideMetricsFor(1280, 720)
        val size = oxideDialogMaxSize(1280, 720)
        val cap = oxideDialogContentMaxHeight(size.maxHeight)
        val row = metrics.dialogOptionRowHeight

        // 项数越多，高度越接近上限，但**永远**不超过上限
        listOf(0, 1, 3, 10, 64, 512, 10_000).forEach { count ->
            val allocated = oxideDialogListHeight(count, cap, row)
            assertTrue("项数 $count 时高度 ${allocated} 超过了上限 $cap", allocated <= cap)
            assertFinite(allocated)
            assertTrue(allocated >= 0.dp)
        }
    }

    @Test
    fun listHeightIsTheNaturalHeightWhileItFits() {
        val metrics = oxideMetricsFor(1280, 720)
        val cap = oxideDialogContentMaxHeight(oxideDialogMaxSize(1280, 720).maxHeight)
        val row = metrics.dialogOptionRowHeight

        val fits = 3
        assertEquals(
            row * fits,
            oxideDialogListHeight(fits, cap, row),
        )
        assertFalse(oxideDialogListOverflows(fits, cap, row))
    }

    @Test
    fun listOverflowsOnceItNoLongerFits() {
        val metrics = oxideMetricsFor(1280, 720)
        val cap = oxideDialogContentMaxHeight(oxideDialogMaxSize(1280, 720).maxHeight)
        val row = metrics.dialogOptionRowHeight

        val overflows = cap.value.toInt() + 10
        assertTrue(oxideDialogListOverflows(overflows, cap, row))
        assertEquals(cap, oxideDialogListHeight(overflows, cap, row))
    }

    /** 负的项数（理论上不该出现）按 0 算，而不是算出一个负高度 */
    @Test
    fun negativeItemCountIsTreatedAsEmpty() {
        assertEquals(0f, oxideDialogListContentHeight(-5, 40.dp).value, 0.001f)
    }

    /**
     * 上限本身不可用时退回一个仍然是有限的数
     *
     * `Dp.Infinity` 如果能一路传下去，滚动容器就会被量成无限高。
     */
    @Test
    fun unusableBoundsAreReplaced() {
        listOf(Dp.Infinity, Dp.Unspecified, 0.dp, (-10).dp).forEach { bad ->
            assertEquals(OxideDialogAbsoluteMinSize, oxideDialogContentHeight(100.dp, bad))
            assertEquals(OxideDialogAbsoluteMinSize, oxideDialogListHeight(50, bad, 40.dp))
        }
    }

    // ---- 输入校验 -----------------------------------------------------------

    @Test
    fun blankIsRejectedUnlessExplicitlyAllowed() {
        assertEquals(OxideEntryVerdict.Blank, oxideEntryVerdict(""))
        assertEquals(OxideEntryVerdict.Blank, oxideEntryVerdict("   "))
        assertFalse(oxideEntryConfirmEnabled(""))

        assertEquals(OxideEntryVerdict.Accept, oxideEntryVerdict("", allowBlank = true))
        assertTrue(oxideEntryConfirmEnabled("", allowBlank = true))
    }

    @Test
    fun overlongIsRejected() {
        assertEquals(OxideEntryVerdict.Accept, oxideEntryVerdict("abc", maxLength = 3))
        assertEquals(OxideEntryVerdict.TooLong, oxideEntryVerdict("abcd", maxLength = 3))
        assertFalse(oxideEntryConfirmEnabled("abcd", maxLength = 3))
    }

    /** maxLength = 0 表示不限长度，旧调用点（没有长度限制的那些）就靠这一条 */
    @Test
    fun zeroMaxLengthMeansUnlimited() {
        val long = "x".repeat(10_000)
        assertEquals(OxideEntryVerdict.Accept, oxideEntryVerdict(long, maxLength = 0))
        assertTrue(oxideEntryConfirmEnabled(long, maxLength = 0))
    }

    /**
     * 又超长又空时报的是超长
     *
     * 先让人把超长的部分删掉更有用——报"空着"的话他删光了还是过不了。
     */
    @Test
    fun tooLongWinsOverBlank() {
        assertEquals(
            OxideEntryVerdict.TooLong,
            oxideEntryVerdict("abcd", maxLength = 3, allowBlank = false),
        )
    }

    // ---- 选项文案 -----------------------------------------------------------

    @Test
    fun enabledOptionsShowTheirOwnDetail() {
        val option = OxideDialogOption(key = "k", label = "Label", detail = "Detail")
        assertEquals("Detail", oxideDialogOptionDetail(option, "Unavailable"))
    }

    @Test
    fun enabledOptionsWithoutDetailShowNothing() {
        val option = OxideDialogOption(key = "k", label = "Label")
        assertNull(oxideDialogOptionDetail(option, "Unavailable"))
    }

    /**
     * 不可选的项必须说明原因
     *
     * 只把整行压暗等于什么都没说：读屏软件只会念出那个文件名，
     * 用户也不知道为什么点不动。
     */
    @Test
    fun disabledOptionsSayWhyTheyAreDisabled() {
        val option = OxideDialogOption(
            key = "k",
            label = "Label",
            detail = "Detail",
            enabled = false,
        )
        assertEquals("Unavailable", oxideDialogOptionDetail(option, "Unavailable"))
    }

    /**
     * key 由身份决定，不由文案决定
     *
     * 网盘清单里两个条目可能同名，而列表用它当 itemKey、回调也按它取值；
     * 因此同名但不同链接必须仍然是两项，且回调能区分。
     */
    @Test
    fun identicalLabelsKeepDistinctKeys() {
        val options = listOf(
            OxideDialogOption(key = "https://a", label = "Baidu"),
            OxideDialogOption(key = "https://b", label = "Baidu"),
        )
        assertEquals(options.size, options.map { it.key }.distinct().size)
        assertEquals(listOf("https://a", "https://b"), options.map { it.key })
    }

    // ---- 派生尺寸 -----------------------------------------------------------

    /** 标记与行高都由 metrics 推出，因此放大界面时它们一起放大 */
    @Test
    fun dialogPartsScaleWithTheGui() {
        val normal = oxideMetricsFor(1280, 720, 100)
        val large = oxideMetricsFor(1280, 720, 150)

        assertTrue(large.dialogOptionRowHeight > normal.dialogOptionRowHeight)
        assertTrue(large.dialogMarkSize > normal.dialogMarkSize)
        assertTrue(large.dialogMarkDotSize > normal.dialogMarkDotSize)
        // 标记必须是实心的方块：内块小于外块
        listOf(normal, large).forEach { metrics ->
            assertTrue(metrics.dialogMarkDotSize < metrics.dialogMarkSize)
            assertTrue(metrics.dialogMarkDotSize > 0.dp)
        }
    }

    private fun assertFinite(value: Dp) {
        assertTrue("`$value` 必须有限", value != Dp.Infinity && value != Dp.Unspecified)
        assertTrue("`$value` 必须是 NaN 之外的数", !value.value.isNaN())
    }
}