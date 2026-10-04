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

package dev.oxide.launcher.ui.screens.main.control_editor

import dev.oxide.layercontroller.data.ButtonSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数字解析与显示的钉死测试
 *
 * 这一族是这一版换掉 `SliderValueEditDialog` 之后新写的：行内输入框收进来的文本
 * 必须被同样严格地检查——非法的输入留在原地等人改，而不是悄悄夹到边界上，
 * 否则用户以为自己填了 12%，实际存进去的是 0。
 */
class ControlEditorNumberTest {

    @Test
    fun `整数输入不接受小数`() {
        assertEquals(12f, editorNumberIn("12", 0f..100f, integerOnly = true))
        assertNull(editorNumberIn("12.5", 0f..100f, integerOnly = true))
    }

    @Test
    fun `小数输入接受整数与小数`() {
        assertEquals(12f, editorNumberIn("12", 0f..100f, integerOnly = false))
        assertEquals(12.5f, editorNumberIn("12.5", 0f..100f, integerOnly = false))
    }

    @Test
    fun `空串与非数字都不允许提交`() {
        listOf("", "   ", "abc", "1a", "-", "+").forEach { text ->
            assertNull("`$text` 被接受了", editorNumberIn(text, 0f..100f, integerOnly = false))
        }
    }

    @Test
    fun `越界的值不允许提交而不是被夹到边界`() {
        assertNull(editorNumberIn("-1", 0f..100f, integerOnly = false))
        assertNull(editorNumberIn("101", 0f..100f, integerOnly = false))
        // 边界本身是合法的
        assertEquals(0f, editorNumberIn("0", 0f..100f, integerOnly = false))
        assertEquals(100f, editorNumberIn("100", 0f..100f, integerOnly = false))
    }

    @Test
    fun `无穷大不算合法输入`() {
        assertNull(editorNumberIn("Infinity", 0f..100f, integerOnly = false))
        assertNull(editorNumberIn("NaN", 0f..100f, integerOnly = false))
    }

    @Test
    fun `错误的分类与提交的判定一致`() {
        assertEquals(EditorNumberError.NotANumber, editorNumberError("", 0f..100f, false))
        assertEquals(EditorNumberError.NotANumber, editorNumberError("abc", 0f..100f, false))
        assertEquals(EditorNumberError.TooSmall, editorNumberError("-1", 0f..100f, false))
        assertEquals(EditorNumberError.TooLarge, editorNumberError("101", 0f..100f, false))
        assertNull(editorNumberError("50", 0f..100f, false))
    }

    @Test
    fun `整数模式下小数被归为不是数字`() {
        assertEquals(EditorNumberError.NotANumber, editorNumberError("12.5", 0f..100f, true))
    }

    @Test
    fun `格式串折算出小数位数`() {
        assertEquals(0, decimalsFromPattern("#0"))
        assertEquals(2, decimalsFromPattern("#0.00"))
        assertEquals(3, decimalsFromPattern("#0.000"))
        // 没有小数点就是整数
        assertEquals(0, decimalsFromPattern("0"))
        assertEquals(0, decimalsFromPattern(""))
        assertEquals(0, decimalsFromPattern("#"))
    }

    @Test
    fun `小数位数不超过上限`() {
        // 一行只有那么宽，显示二十位小数既读不出来也放不下
        assertEquals(EditorMaxDecimals, decimalsFromPattern("#0.0000000000"))
    }

    @Test
    fun `显示值按位数拼并带上单位`() {
        assertEquals("50%", formatEditorValue(50f, suffix = "%", decimals = 0))
        assertEquals("49.83%", formatEditorValue(49.83f, suffix = "%", decimals = 2))
        assertEquals("49.8%", formatEditorValue(49.83f, suffix = "%", decimals = 1))
        assertEquals("50dp", formatEditorValue(50f, suffix = "dp", decimals = 0))
        assertEquals("50", formatEditorValue(50f, suffix = null, decimals = 2))
    }

    @Test
    fun `负数显示时带负号且小数部分补零`() {
        assertEquals("-3.50", formatEditorValue(-3.5f, suffix = null, decimals = 2))
        assertEquals("-3.05", formatEditorValue(-3.5f, suffix = null, decimals = 1))
    }

    @Test
    fun `位数为零时按整数四舍五入`() {
        assertEquals("50", formatEditorValue(49.6f, suffix = null, decimals = 0))
        assertEquals("-50", formatEditorValue(-49.6f, suffix = null, decimals = 0))
    }

    @Test
    fun `滑杆的比例与反算互为逆运算`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val value = editorSliderValue(fraction, 0f..100f)
            assertEquals(fraction, editorSliderFraction(value, 0f..100f), 0.0001f)
        }
    }

    @Test
    fun `比例与反算都夹在区间内`() {
        assertEquals(0f, editorSliderFraction(-50f, 0f..100f), 0.0001f)
        assertEquals(1f, editorSliderFraction(500f, 0f..100f), 0.0001f)
        assertEquals(0f, editorSliderValue(-1f, 0f..100f), 0.0001f)
        assertEquals(100f, editorSliderValue(9f, 0f..100f), 0.0001f)
    }

    @Test
    fun `区间退化成一点时比例是零而不是除以零`() {
        assertEquals(0f, editorSliderFraction(5f, 5f..5f), 0.0001f)
    }
}

/**
 * 可见性判断的钉死测试
 *
 * 这些判断决定"哪一行出现"。判断错一个方向，用户就看到一个在当前状态下根本
 * 不能用的控件——例如摇杆的"包裹内容"，或者预览模式下还能拖的位置。
 */
class ControlEditorVisibilityTest {

    @Test
    fun `摇杆没有包裹内容`() {
        val joystick = editorSizeTypesFor(EditorWidgetKind.Joystick)
        assertFalse(
            "摇杆选了包裹内容只会在保存时炸掉",
            joystick.contains(ButtonSize.Type.WrapContent)
        )
        assertEquals(2, joystick.size)
    }

    @Test
    fun `按键与文本框三种尺寸类型都有`() {
        listOf(EditorWidgetKind.Button, EditorWidgetKind.Text).forEach { kind ->
            assertEquals(ButtonSize.Type.entries.size, editorSizeTypesFor(kind).size)
        }
    }

    @Test
    fun `包裹内容时没有宽高可改`() {
        assertFalse(editorShowsSizeFields(ButtonSize.Type.WrapContent))
        assertTrue(editorShowsSizeFields(ButtonSize.Type.Dp))
        assertTrue(editorShowsSizeFields(ButtonSize.Type.Percentage))
    }

    @Test
    fun `绝对值与百分比各自只在自己的类型下出现`() {
        assertTrue(editorShowsAbsoluteSize(ButtonSize.Type.Dp))
        assertFalse(editorShowsAbsoluteSize(ButtonSize.Type.Percentage))
        assertFalse(editorShowsAbsoluteSize(ButtonSize.Type.WrapContent))

        assertTrue(editorShowsPercentSize(ButtonSize.Type.Percentage))
        assertFalse(editorShowsPercentSize(ButtonSize.Type.Dp))
        assertFalse(editorShowsPercentSize(ButtonSize.Type.WrapContent))
    }

    @Test
    fun `参考边只在百分比类型下出现`() {
        assertTrue(editorShowsSizeReference(ButtonSize.Type.Percentage))
        assertFalse(editorShowsSizeReference(ButtonSize.Type.Dp))
        assertFalse(editorShowsSizeReference(ButtonSize.Type.WrapContent))
    }

    @Test
    fun `只有摇杆没有分开的宽高`() {
        assertFalse(editorShowsSeparateWidthHeight(EditorWidgetKind.Joystick))
        assertTrue(editorShowsSeparateWidthHeight(EditorWidgetKind.Button))
        assertTrue(editorShowsSeparateWidthHeight(EditorWidgetKind.Text))
    }

    @Test
    fun `一个层都没有时不能新建`() {
        assertEquals(
            EditorAddBlocker.NoLayers,
            editorAddBlocker(layerCount = 0, hasSelectedLayer = false, isPreviewMode = false)
        )
        assertFalse(editorAllowsAddingControls(EditorAddBlocker.NoLayers))
    }

    @Test
    fun `有层但没选中时不能新建`() {
        assertEquals(
            EditorAddBlocker.NoSelectedLayer,
            editorAddBlocker(layerCount = 3, hasSelectedLayer = false, isPreviewMode = false)
        )
        assertFalse(editorAllowsAddingControls(EditorAddBlocker.NoSelectedLayer))
    }

    @Test
    fun `预览模式下不能新建`() {
        assertEquals(
            EditorAddBlocker.Preview,
            editorAddBlocker(layerCount = 3, hasSelectedLayer = true, isPreviewMode = true)
        )
        assertFalse(editorAllowsAddingControls(EditorAddBlocker.Preview))
    }

    @Test
    fun `预览的提示盖过其他两种原因`() {
        // 预览时画布只读，"先建一个层"这条提示会让用户去找一个并不存在的问题
        assertEquals(
            EditorAddBlocker.Preview,
            editorAddBlocker(layerCount = 0, hasSelectedLayer = false, isPreviewMode = true)
        )
    }

    @Test
    fun `层与选中都在且不在预览时可以新建`() {
        assertEquals(
            EditorAddBlocker.None,
            editorAddBlocker(layerCount = 1, hasSelectedLayer = true, isPreviewMode = false)
        )
        assertTrue(editorAllowsAddingControls(EditorAddBlocker.None))
    }

    @Test
    fun `预览模式下层不可编辑`() {
        assertFalse(editorAllowsLayerEditing(isPreviewMode = true))
        assertTrue(editorAllowsLayerEditing(isPreviewMode = false))
    }

    @Test
    fun `没选中控件时没有位置与尺寸可改`() {
        assertFalse(editorShowsGeometrySection(null))
        listOf(EditorWidgetKind.Button, EditorWidgetKind.Text, EditorWidgetKind.Joystick).forEach {
            assertTrue(editorShowsGeometrySection(it))
        }
    }

    @Test
    fun `预览模式下位置与尺寸被锁住`() {
        val canEdit = editorAllowsGeometryEditing(isPreviewMode = false, blocker = EditorAddBlocker.None)
        assertTrue(canEdit)
        assertFalse(
            editorAllowsGeometryEditing(isPreviewMode = true, blocker = EditorAddBlocker.None)
        )
    }
}

/**
 * 尺寸取值范围的钉死测试
 *
 * 这些范围直接喂给行内输入框与滑杆，因此"下界比上界大"这一类输入必须在纯函数
 * 这一层就被排掉——滑杆在最小的窗口上打不开，是一个只在真机上才看得见的故障。
 */
class ControlEditorSizeRangeTest {

    @Test
    fun `按键的 dp 下界是 5`() {
        val range = editorSizeDpRange(ButtonSize.Type.Dp, EditorWidgetKind.Button, 640f)
        assertEquals(5f, range.start, 0.001f)
        assertEquals(640f, range.endInclusive, 0.001f)
    }

    @Test
    fun `摇杆的 dp 下界是 20`() {
        val range = editorSizeDpRange(ButtonSize.Type.Dp, EditorWidgetKind.Joystick, 640f)
        assertEquals(20f, range.start, 0.001f)
    }

    @Test
    fun `屏幕比下界还窄时区间不会反过来`() {
        // 这一档以前会抛 IllegalArgumentException：coerceIn 的下界比上界大
        listOf(1f, 3f, 5f, 19.9f).forEach { screen ->
            val range = editorSizeDpRange(ButtonSize.Type.Dp, EditorWidgetKind.Joystick, screen)
            assertTrue(
                "$screen 下区间 ${range.start}..${range.endInclusive} 反过来了",
                range.start <= range.endInclusive
            )
        }
    }

    @Test
    fun `百分比区间按编辑器口径给`() {
        val range = editorSizePercentRange(EditorWidgetKind.Button)
        assertEquals(1f, range.start, 0.001f)
        assertEquals(100f, range.endInclusive, 0.001f)
    }

    @Test
    fun `摇杆的百分比下界更高`() {
        val range = editorSizePercentRange(EditorWidgetKind.Joystick)
        assertEquals(20f, range.start, 0.001f)
        assertEquals(100f, range.endInclusive, 0.001f)
    }

    @Test
    fun `尺寸区间里的值都能被行内输入接受`() {
        val range = editorSizeDpRange(ButtonSize.Type.Dp, EditorWidgetKind.Button, 640f)
        assertEquals(range.start, editorNumberIn("5", range, integerOnly = false))
        assertEquals(range.endInclusive, editorNumberIn("640", range, integerOnly = false))
        assertNull(editorNumberIn("641", range, integerOnly = false))
    }
}