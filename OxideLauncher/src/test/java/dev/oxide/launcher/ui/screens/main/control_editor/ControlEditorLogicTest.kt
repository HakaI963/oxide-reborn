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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑器几何的钉死测试
 *
 * 这些断言都是"如果这条变了，界面就会在某一档上坏掉"的具体形式：
 * 640x360 的下限、界面放大到 150%、屏幕特别窄、控件特别多。
 * 它们不启动 Compose，也不碰 Android——被测的那些函数全是纯的。
 */
class ControlEditorMetricsTest {

    @Test
    fun `下限窗口 640x360 下 面板不会吃掉整块画布`() {
        val metrics = controlEditorMetricsFor(640, 360)
        // 面板最多占窗口的一半多一点，剩下的必须留给画布
        assertTrue(
            "面板 ${metrics.dockWidth} 比可用宽度 640 的 60% 还宽",
            metrics.dockWidth.value <= 640f * 0.6f
        )
        assertTrue("面板必须比它的高度窄", metrics.dockWidth.value < metrics.dockHeight.value)
        assertTrue("面板加上左右留白仍在窗口内", metrics.dockWidth.value + metrics.dockMargin.value * 2 <= 640f)
    }

    @Test
    fun `下限窗口下行高与触摸目标仍是可用的`() {
        val metrics = controlEditorMetricsFor(640, 360)
        // 一行、格子、输入框、小按钮都不得低于 20dp，否则在触屏上点不准
        assertTrue("行高 ${metrics.rowHeight}", metrics.rowHeight.value >= 26f)
        assertTrue("格子高 ${metrics.controlCellHeight}", metrics.controlCellHeight.value >= 26f)
        assertTrue("输入框高 ${metrics.fieldHeight}", metrics.fieldHeight.value >= 20f)
        assertTrue("小按钮 ${metrics.miniButtonSize}", metrics.miniButtonSize.value >= 20f)
        assertTrue("悬浮球 ${metrics.ballSize}", metrics.ballSize.value >= 20f)
    }

    @Test
    fun `窄窗口下 面板宽度被窗口夹住而不是溢出`() {
        val metrics = controlEditorMetricsFor(220, 180)
        assertTrue(
            "面板 ${metrics.dockWidth} 溢出了 220 宽的窗口",
            metrics.dockWidth.value <= 220f
        )
        assertTrue("面板高度不能超过窗口", metrics.dockHeight.value <= 180f)
    }

    @Test
    fun `极小尺寸下不抛异常也不给出负的尺寸`() {
        // 以前这一类输入会让 coerceIn 因为"下界比上界大"而抛 IllegalArgumentException
        listOf(1 to 1, 3 to 5, 0 to 0, -10 to -10).forEach { (w, h) ->
            val metrics = controlEditorMetricsFor(w, h)
            listOf(
                metrics.dockWidth,
                metrics.dockHeight,
                metrics.dockMargin,
                metrics.rowHeight,
                metrics.padHeight,
                metrics.fieldHeight,
                metrics.miniButtonSize,
                metrics.controlCellHeight
            ).forEach { size ->
                assertTrue("$w x $h 下出现了 $size", size.value >= 0f)
            }
            // 行高永远不能高过整块面板，否则面板里一行都放不下
            assertTrue(
                "$w x $h 下的行高 ${metrics.rowHeight} 高过面板高 ${metrics.dockHeight}",
                metrics.rowHeight.value <= metrics.dockHeight.value + 0.001f
            )
        }
    }

    @Test
    fun `定位板不会高到把面板挤爆`() {
        val metrics = controlEditorMetricsFor(640, 360)
        assertTrue(
            "定位板 ${metrics.padHeight} 加两行就超过面板了",
            metrics.padHeight.value + metrics.rowHeight.value * 2 <= metrics.dockHeight.value + 0.001f
        )
    }

    @Test
    fun `放大到 150% 时框与字同一个系数且面板不溢出窗口`() {
        val small = controlEditorMetricsFor(640, 360, guiScalePercent = 100)
        val large = controlEditorMetricsFor(640, 360, guiScalePercent = 150)
        assertEquals(1.5f, large.guiScale, 0.0001f)
        // 与窗口无关的那一半正好乘 1.5
        assertEquals(small.rowHeight.value * 1.5f, large.rowHeight.value, 0.01f)
        assertEquals(small.ballSize.value * 1.5f, large.ballSize.value, 0.01f)
        // 面板本身被窗口夹住，不会顶出屏幕
        assertTrue(
            "放大后面板 ${large.dockWidth} 溢出了窗口",
            large.dockWidth.value + large.dockMargin.value * 2 <= 640f + 0.01f
        )
        assertTrue(
            "放大后行高 ${large.rowHeight} 高过面板 ${large.dockHeight}",
            large.rowHeight.value <= large.dockHeight.value + 0.001f
        )
    }

    @Test
    fun `缩小到 75% 时也是同一个系数`() {
        val base = controlEditorMetricsFor(1280, 720)
        val small = controlEditorMetricsFor(1280, 720, guiScalePercent = 75)
        assertEquals(0.75f, small.guiScale, 0.0001f)
        assertEquals(base.rowHeight.value * 0.75f, small.rowHeight.value, 0.01f)
    }

    @Test
    fun `越界的缩放百分比被夹回区间而不是给出荒唐的尺寸`() {
        val low = controlEditorMetricsFor(640, 360, guiScalePercent = 0)
        val high = controlEditorMetricsFor(640, 360, guiScalePercent = 9999)
        assertEquals(0.75f, low.guiScale, 0.0001f)
        assertEquals(1.5f, high.guiScale, 0.0001f)
        assertTrue(high.dockWidth.value + high.dockMargin.value * 2 <= 640f + 0.01f)
    }

    @Test
    fun `内容宽度就是面板宽度减去两侧内边距`() {
        val metrics = controlEditorMetricsFor(640, 360)
        assertEquals(
            metrics.dockWidth.value - metrics.dockPadding.value * 2,
            metrics.contentWidth.value,
            0.001f
        )
        assertTrue("内容宽度必须还是正的", metrics.contentWidth.value > 0f)
    }
}

/**
 * 控件网格的钉死测试
 *
 * 网格是这一版新增的东西：过去只能一个一个在画布上点选，控件多了就没法快速找到
 * 那一个。这里的断言盯住"列数怎么算""下标怎么切成行"。
 */
class ControlEditorGridTest {

    private val metrics = controlEditorMetricsFor(640, 360)

    @Test
    fun `下限窗口的内容宽度放得下三列`() {
        val columns = controlEditorGridColumns(metrics.contentWidth, metrics)
        assertTrue(
            "内容宽 ${metrics.contentWidth} 只放得下 $columns 列",
            columns in metrics.controlColumnsMin..metrics.controlColumnsMax
        )
        // 三列时每列必须真的放得下，否则格子会被裁掉半截
        val needed = (metrics.controlCellMinWidth + metrics.cellGap) * columns - metrics.cellGap
        if (columns == 3) {
            assertTrue(
                "三列需要 $needed，但只有 ${metrics.contentWidth}",
                metrics.contentWidth.value >= needed.value - 0.001f
            )
        }
    }

    @Test
    fun `列数永远落在夹紧区间里`() {
        listOf(0.dp, 1.dp, 40.dp, 200.dp, 300.dp, 900.dp, 5000.dp).forEach { width ->
            val columns = controlEditorGridColumns(width, metrics)
            assertTrue(
                "$width 给出 $columns 列",
                columns in metrics.controlColumnsMin..metrics.controlColumnsMax
            )
        }
    }

    @Test
    fun `内容越窄列数只会减少不会减少到零`() {
        val wide = controlEditorGridColumns(320.dp, metrics)
        val narrow = controlEditorGridColumns(120.dp, metrics)
        assertTrue("窄面板不该比宽面板列数多", narrow <= wide)
        assertEquals(1, narrow)
    }

    @Test
    fun `放大后同样宽度装不下同样多的列`() {
        val base = controlEditorMetricsFor(640, 360, guiScalePercent = 100)
        val large = controlEditorMetricsFor(640, 360, guiScalePercent = 150)
        val baseColumns = controlEditorGridColumns(base.contentWidth, base)
        val largeColumns = controlEditorGridColumns(large.contentWidth, large)
        assertTrue(
            "放大后列数反而多了：$baseColumns -> $largeColumns",
            largeColumns <= baseColumns
        )
    }

    @Test
    fun `行数向上取整`() {
        assertEquals(0, controlEditorGridRowCount(0, 3))
        assertEquals(1, controlEditorGridRowCount(1, 3))
        assertEquals(1, controlEditorGridRowCount(3, 3))
        assertEquals(2, controlEditorGridRowCount(4, 3))
        assertEquals(2, controlEditorGridRowCount(6, 3))
        assertEquals(5, controlEditorGridRowCount(13, 3))
    }

    @Test
    fun `列数是零或负数时按一列算而不是崩掉`() {
        assertEquals(4, controlEditorGridRowCount(4, 0))
        assertEquals(4, controlEditorGridRowCount(4, -3))
        assertEquals(
            listOf(0..0, 1..1, 2..2, 3..3),
            controlEditorGridRowRanges(4, 0)
        )
    }

    @Test
    fun `下标被完整地、不重不漏地切成行`() {
        val ranges = controlEditorGridRowRanges(7, 3)
        assertEquals(listOf(0..2, 3..5, 6..6), ranges)
        // 每一行长度不超过列数
        ranges.forEach { assertTrue(it.count() <= 3) }
        // 拼起来正好是 0..count-1，不重不漏
        assertEquals((0 until 7).toList(), ranges.flatMap { it.toList() })
    }

    @Test
    fun `空列表与负数给出空结果`() {
        assertEquals(emptyList<IntRange>(), controlEditorGridRowRanges(0, 3))
        assertEquals(emptyList<IntRange>(), controlEditorGridRowRanges(-5, 3))
    }
}

/**
 * 拖动定位与步进的钉死测试
 *
 * 定位板是这一版新增的第二条改位置的路径：画布上拖得动，但精确不了。这里盯住
 * "板内像素 → 存储刻度"与"步进"这两段算术。
 */
class ControlEditorPadTest {

    @Test
    fun `归一化比例与存储刻度互为逆运算`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val stored = editorStoredFromFraction(fraction)
            assertEquals(fraction, editorFractionFromStored(stored), 0.0001f)
        }
        assertEquals(0, editorStoredFromFraction(0f))
        assertEquals(EditorStoredMax, editorStoredFromFraction(1f))
    }

    @Test
    fun `越界的比例夹回区间而不是产生越界的存储值`() {
        assertEquals(0, editorStoredFromFraction(-3f))
        assertEquals(EditorStoredMax, editorStoredFromFraction(4.2f))
        assertEquals(0f, editorFractionFromStored(-100), 0.0001f)
        assertEquals(1f, editorFractionFromStored(99999), 0.0001f)
        // NaN 不该变成某个随意的位置
        assertEquals(0, editorStoredFromFraction(Float.NaN))
    }

    @Test
    fun `板内像素映射到存储位置`() {
        val position = editorPositionFromPadPoint(px = 50f, py = 25f, width = 200f, height = 100f)
        assertEquals(EditorStoredMax / 2, position.x)
        assertEquals(EditorStoredMax / 4, position.y)
    }

    @Test
    fun `手指落在板外时夹到边上`() {
        val position = editorPositionFromPadPoint(px = -50f, py = 900f, width = 200f, height = 100f)
        assertEquals(0, position.x)
        assertEquals(EditorStoredMax, position.y)
    }

    @Test
    fun `板还没有量到时退到原点而不是除以零`() {
        // 尺寸为 0 时若直接除就会得到 NaN，随后写进 ButtonPosition 会炸
        val position = editorPositionFromPadPoint(px = 10f, py = 10f, width = 0f, height = 0f)
        assertEquals(0, position.x)
        assertEquals(0, position.y)
    }

    @Test
    fun `旋钮夹在板内因此极小的板也不会把它画出去`() {
        val point = editorPadKnob(
            fractionX = 0f,
            fractionY = 1f,
            width = 20f,
            height = 20f,
            knobRadius = 6f
        )
        assertTrue("旋钮跑到了板左外侧 ${point.x}", point.x >= 0f)
        assertTrue("旋钮跑到了板右外侧 ${point.x}", point.x <= 20f)
        assertTrue("旋钮跑到了板下外侧 ${point.y}", point.y <= 20f)
    }

    @Test
    fun `板比旋钮还小时不抛异常`() {
        // 这一类输入以前会交给 coerceIn，而它的下界比上界大时会抛 IllegalArgumentException
        listOf(0f to 0f, 1f to 1f, 4f to 4f).forEach { (w, h) ->
            val point = editorPadKnob(0.5f, 0.5f, w, h, knobRadius = 10f)
            assertTrue("$w x $h 下旋钮 $point 跑出了板外", point.x in 0f..w && point.y in 0f..h)
        }
    }

    @Test
    fun `旋钮中心就是比例乘板宽`() {
        val point = editorPadKnob(0.5f, 0.25f, width = 200f, height = 100f, knobRadius = 4f)
        assertEquals(100f, point.x, 0.001f)
        assertEquals(25f, point.y, 0.001f)
    }

    @Test
    fun `步进按方向移动并夹在区间里`() {
        assertEquals(5100, editorNudge(5000, step = 100, direction = 1))
        assertEquals(4900, editorNudge(5000, step = 100, direction = -1))
        // 方向为 0 就是不动
        assertEquals(5000, editorNudge(5000, step = 100, direction = 0))
        // 到底就停住，不会绕回去
        assertEquals(EditorStoredMax, editorNudge(EditorStoredMax, step = 100, direction = 1))
        assertEquals(0, editorNudge(0, step = 100, direction = -1))
    }

    @Test
    fun `步长是负数时按绝对值处理`() {
        assertEquals(5100, editorNudge(5000, step = -100, direction = 1))
        assertEquals(4900, editorNudge(5000, step = -100, direction = -1))
    }

    @Test
    fun `步进可以用自定义区间`() {
        assertEquals(9, editorNudge(10, step = 5, direction = 1, min = 0, max = 9))
        assertEquals(6, editorNudge(1, step = 5, direction = -1, min = 6, max = 9))
    }

    @Test
    fun `下界比上界大的区间被排好而不是抛异常`() {
        assertEquals(5, editorNudge(5, step = 10, direction = 1, min = 9, max = 2))
    }

    @Test
    fun `编辑器用的步长是半个百分点`() {
        assertEquals(50, EditorNudgeStep)
        // 半个百分点 = 存储刻度 10000 里的 50
        assertEquals(5000, editorNudge(5000, EditorNudgeStep, 1))
        assertEquals(5050, editorNudge(5000, EditorNudgeStep, 1))
    }
}

/** 九宫格对齐的钉死测试 */
class ControlEditorAnchorTest {

    @Test
    fun `九个落点都落在存储刻度里`() {
        EditorAnchor.entries.forEach { anchor ->
            val position = editorAnchorPosition(anchor)
            assertTrue("$anchor 越界 ${position.x}", position.x in 0..EditorStoredMax)
            assertTrue("$anchor 越界 ${position.y}", position.y in 0..EditorStoredMax)
        }
    }

    @Test
    fun `四角与中心就是刻度的两端与中点`() {
        assertEquals(EditorStoredPosition(0, 0), editorAnchorPosition(EditorAnchor.TopStart))
        assertEquals(EditorStoredPosition(EditorStoredMax, 0), editorAnchorPosition(EditorAnchor.TopEnd))
        assertEquals(
            EditorStoredPosition(EditorStoredMax, EditorStoredMax),
            editorAnchorPosition(EditorAnchor.BottomEnd)
        )
        // 居中就是 5000，也就是新建控件的默认位置
        assertEquals(EditorStoredPosition(5000, 5000), editorAnchorPosition(EditorAnchor.Center))
    }

    @Test
    fun `每条边上居中是一半高或一半宽`() {
        assertEquals(EditorStoredPosition(5000, 0), editorAnchorPosition(EditorAnchor.TopCenter))
        assertEquals(EditorStoredPosition(0, 5000), editorAnchorPosition(EditorAnchor.CenterStart))
        assertEquals(EditorStoredPosition(EditorStoredMax, 5000), editorAnchorPosition(EditorAnchor.CenterEnd))
        assertEquals(
            EditorStoredPosition(5000, EditorStoredMax),
            editorAnchorPosition(EditorAnchor.BottomCenter)
        )
    }

    @Test
    fun `九个落点两两不同`() {
        val all = EditorAnchor.entries.map { editorAnchorPosition(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `枚举的顺序就是九宫格逐行铺开的顺序`() {
        assertEquals(
            listOf(
                EditorAnchor.TopStart, EditorAnchor.TopCenter, EditorAnchor.TopEnd,
                EditorAnchor.CenterStart, EditorAnchor.Center, EditorAnchor.CenterEnd,
                EditorAnchor.BottomStart, EditorAnchor.BottomCenter, EditorAnchor.BottomEnd
            ),
            EditorAnchor.entries.toList()
        )
    }

    @Test
    fun `格子数与九宫格的行列数对得上`() {
        assertEquals(3, EditorAnchor.ColumnCount)
        assertEquals(3, EditorAnchor.RowCount)
        assertEquals(3 * 3, EditorAnchor.entries.size)
    }
}