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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import dev.oxide.layercontroller.data.MACRO_MAX_INTERVAL_MS
import dev.oxide.layercontroller.data.MACRO_MIN_INTERVAL_MS
import dev.oxide.layercontroller.data.clampMacroIntervalMs
import dev.oxide.layercontroller.data.macroIntervalMsIn
import dev.oxide.layercontroller.event.MAX_CLICK_EVENT_DELAY_MS
import dev.oxide.layercontroller.event.clampClickEventDelayMs
import dev.oxide.layercontroller.event.clickEventDelayMsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新菜单的纯逻辑
 *
 * 停靠面板只负责选东西，做决定的小页一次只打开一张（[EditorMenuSheet]），
 * 新建选择器里三个选项共用同一道闸门，宏重复与按键延迟走数据层同一套夹紧。
 * 全部是纯函数，不启动 Compose 也不碰 Android。
 */
class EditorMenuSheetStateTest {

    @Test
    fun `小页一次只有一张`() {
        assertEquals(
            listOf(
                EditorMenuSheet.None,
                EditorMenuSheet.AddPicker,
                EditorMenuSheet.LayerActions,
            ),
            EditorMenuSheet.entries.toList(),
        )
    }

    @Test
    fun `选择器的闸门与新建按钮是同一道`() {
        val cases = listOf(
            Triple(0, false, false) to EditorAddBlocker.NoLayers,
            Triple(2, false, false) to EditorAddBlocker.NoSelectedLayer,
            Triple(2, true, true) to EditorAddBlocker.Preview,
            Triple(2, true, false) to EditorAddBlocker.None,
            Triple(0, false, true) to EditorAddBlocker.Preview,
        )
        cases.forEach { (input, expected) ->
            val (count, selected, preview) = input
            assertEquals(
                expected,
                editorAddPickerBlocker(
                    layerCount = count,
                    hasSelectedLayer = selected,
                    isPreviewMode = preview,
                ),
            )
            assertEquals(
                expected,
                editorAddBlocker(
                    layerCount = count,
                    hasSelectedLayer = selected,
                    isPreviewMode = preview,
                ),
            )
        }
    }

    @Test
    fun `选择器能打开时三个选项都能点`() {
        assertTrue(editorAddPickerAllows(EditorAddBlocker.None))
        assertFalse(editorAddPickerAllows(EditorAddBlocker.NoLayers))
        assertFalse(editorAddPickerAllows(EditorAddBlocker.NoSelectedLayer))
        assertFalse(editorAddPickerAllows(EditorAddBlocker.Preview))
    }
}

/**
 * 宏重复开关的纯换算
 *
 * 数据层用同一个字段同时表达开关与间隔：0 就是关，大于 0 就是开。
 */
class EditorMacroStateTest {

    @Test
    fun `零表示关闭正数表示打开`() {
        assertFalse(editorMacroEnabled(0L))
        assertFalse(editorMacroEnabled(-10L))
        assertTrue(editorMacroEnabled(1L))
        assertTrue(editorMacroEnabled(MACRO_MIN_INTERVAL_MS))
        assertTrue(editorMacroEnabled(MACRO_MAX_INTERVAL_MS))
    }

    @Test
    fun `打开给下界关闭回零`() {
        assertEquals(MACRO_MIN_INTERVAL_MS, editorMacroToggledValue(true, MACRO_MIN_INTERVAL_MS))
        assertEquals(0L, editorMacroToggledValue(false, MACRO_MIN_INTERVAL_MS))
        // 打开时留在零等于开了一个空档，因此下界再小也不能是零
        assertTrue(editorMacroToggledValue(true, MACRO_MIN_INTERVAL_MS) > 0L)
    }

    @Test
    fun `宏间隔的滑杆区间就是数据层的上下界`() {
        val range = editorMacroIntervalRange(MACRO_MIN_INTERVAL_MS, MACRO_MAX_INTERVAL_MS)
        assertEquals(MACRO_MIN_INTERVAL_MS.toFloat(), range.start)
        assertEquals(MACRO_MAX_INTERVAL_MS.toFloat(), range.endInclusive)
    }

    @Test
    fun `宏间隔的夹紧与旧的行为一致`() {
        // 关不掉的间隔不能被夹成下界：零与负数仍然是关闭
        assertEquals(0L, clampMacroIntervalMs(0L))
        assertEquals(0L, clampMacroIntervalMs(-5L))
        assertEquals(MACRO_MIN_INTERVAL_MS, clampMacroIntervalMs(1L))
        assertEquals(MACRO_MAX_INTERVAL_MS, clampMacroIntervalMs(999_999L))
        assertEquals(250L, clampMacroIntervalMs(250L))
        // 浮点入口供滑杆用，非数字按关闭处理
        assertEquals(0L, clampMacroIntervalMs(Float.NaN))
        assertEquals(500L, clampMacroIntervalMs(500f))
    }

    @Test
    fun `宏间隔的行内输入只收区间内的整数`() {
        assertEquals(250L, macroIntervalMsIn("250"))
        assertNull(macroIntervalMsIn(""))
        assertNull(macroIntervalMsIn("abc"))
        assertNull(macroIntervalMsIn("0"))
        assertNull(macroIntervalMsIn((MACRO_MIN_INTERVAL_MS - 1).toString()))
        assertNull(macroIntervalMsIn((MACRO_MAX_INTERVAL_MS + 1).toString()))
    }
}

/**
 * 按键延迟的纯换算
 *
 * 延迟是每个按键自己的属性：0 表示立即派发，上限 5000 毫秒。
 */
class EditorClickDelayStateTest {

    @Test
    fun `延迟的滑杆区间从零到上限`() {
        val range = editorClickDelayRange(MAX_CLICK_EVENT_DELAY_MS)
        assertEquals(0f, range.start)
        assertEquals(MAX_CLICK_EVENT_DELAY_MS.toFloat(), range.endInclusive)
    }

    @Test
    fun `延迟的夹紧与派发时走的是同一套`() {
        assertEquals(0, clampClickEventDelayMs(-20))
        assertEquals(0, clampClickEventDelayMs(0))
        assertEquals(200, clampClickEventDelayMs(200))
        assertEquals(MAX_CLICK_EVENT_DELAY_MS, clampClickEventDelayMs(99_999))
        assertEquals(0, clampClickEventDelayMs(Float.NaN))
    }

    @Test
    fun `延迟的行内输入只收区间内的整数`() {
        assertEquals(0, clickEventDelayMsIn("0"))
        assertEquals(200, clickEventDelayMsIn("200"))
        assertEquals(MAX_CLICK_EVENT_DELAY_MS, clickEventDelayMsIn(MAX_CLICK_EVENT_DELAY_MS.toString()))
        assertNull(clickEventDelayMsIn(""))
        assertNull(clickEventDelayMsIn("abc"))
        assertNull(clickEventDelayMsIn("-1"))
        assertNull(clickEventDelayMsIn((MAX_CLICK_EVENT_DELAY_MS + 1).toString()))
    }
}
