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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 悬浮球停靠位置的钉死测试
 *
 * v1.7.0 的悬浮球有两条独立的毛病，根因都在这一组算术上：
 *
 * 1. 球的宿主没有对齐，于是渲染在 `TopStart`；而"默认摆到顶边居中"那一行写的是
 *    `x = maxX / 2`，其中 `maxX` 来自一层套在球自身上的 `BoxWithConstraints`——
 *    内部的 `maxWidth` 恒等于球的边长，所以 `maxX == 0`，这行算出来还是 0。
 *    设备截图里那颗卡在左上角、正好压在圆角上的按钮就是这么来的。
 * 2. 拖动增量同样被夹进 `[0, maxX] × [0, maxY]`，也就是 `(0, 0)`，球因此永远拖不动。
 *
 * 现在这两件事都交给 [editorBallSafeBounds] / [editorBallDefaultPosition] /
 * [editorBallClamp] 三个纯函数，因此可以逐条钉死。它们不启动 Compose、不碰 Android。
 *
 * 全部用像素（`Size` / `Rect` / `Offset`）而不是 Dp：组合那一侧本来就要用
 * `LocalDensity` 换算过一次，测试里再放一层 Dp 只会把"哪一步换算错了"变模糊。
 */
class ControlEditorBallBoundsTest {

    /** 参考稿的下限窗口，密度 1.0 下一个 dp 就是一个像素 */
    private val screen = Size(1280f, 760f)

    /** `controlEditorMetricsFor(1280, 760).ballSize` 在 100% 缩放下就是 26dp */
    private val ball = 26f

    @Test
    fun `没有安全边界时安全区就是整块画布`() {
        val bounds = editorBallSafeBounds(available = screen, ball = ball)
        assertEquals(Rect(0f, 0f, 1280f, 760f), bounds)
    }

    @Test
    fun `默认落位是横向居中且贴着顶边`() {
        val bounds = editorBallSafeBounds(available = screen, ball = ball)
        val placed = editorBallDefaultPosition(bounds, ball)
        // (1280 - 26) / 2 = 627
        assertEquals(627f, placed.x, 0.001f)
        assertEquals(0f, placed.y, 0.001f)
        // 居中：左右余量相等，且球完整落在画布内
        assertEquals(placed.x, screen.width - placed.x - ball, 0.001f)
        assertTrue(placed.x >= 0f)
        assertTrue(placed.x + ball <= screen.width)
    }

    @Test
    fun `默认落位在奇数宽度上仍然居中而不是偏左一像素`() {
        // 1255 - 26 = 1229，除下来是 614.5：取整必须落在 [614, 1229 - 614] 之内
        val bounds = editorBallSafeBounds(available = Size(1255f, 700f), ball = ball)
        val placed = editorBallDefaultPosition(bounds, ball)
        val leftGap = placed.x
        val rightGap = 1255f - (placed.x + ball)
        assertTrue("左边 $leftGap 右边 $rightGap 差得太多", abs(leftGap - rightGap) <= ball)
        assertTrue(placed.x >= 0f)
        assertTrue(placed.x + ball <= 1255f + 0.001f)
    }

    @Test
    fun `安全边界按各自那条边收进去`() {
        val bounds = editorBallSafeBounds(
            available = screen,
            ball = ball,
            insets = EditorBallInsets(left = 12f, top = 24f, right = 36f, bottom = 48f),
        )
        assertEquals(12f, bounds.left, 0.001f)
        assertEquals(24f, bounds.top, 0.001f)
        assertEquals(1280f - 36f, bounds.right, 0.001f)
        assertEquals(760f - 48f, bounds.bottom, 0.001f)
    }

    @Test
    fun `默认落位跟着安全边界走因此不会停在圆角或系统栏下`() {
        val bounds = editorBallSafeBounds(
            available = screen,
            ball = ball,
            insets = EditorBallInsets(left = 40f, top = 30f, right = 0f, bottom = 0f),
        )
        val placed = editorBallDefaultPosition(bounds, ball)
        // 左边那 40 已经被边界吃掉了，所以球不再压到那一侧的圆角
        assertTrue("球左边 ${placed.x} 越过了 $bounds", placed.x >= bounds.left)
        // 顶边贴着 inset 的下沿，而不是画布的 0
        assertEquals(30f, placed.y, 0.001f)
        // 居中是相对**安全区**居中的：(1240 - 26) / 2 + 40 = 647
        assertEquals(647f, placed.x, 0.001f)
        // 因此球整体在安全区里居中，而不是在画布里居中
        assertEquals(placed.x - bounds.left, bounds.right - (placed.x + ball), 0.001f)
    }

    @Test
    fun `拖动永远走不出安全区`() {
        val bounds = editorBallSafeBounds(
            available = screen,
            ball = ball,
            insets = EditorBallInsets(left = 16f, top = 20f, right = 16f, bottom = 20f),
        )
        val candidates = listOf(
            Offset(-9999f, -9999f),
            Offset(9999f, 9999f),
            Offset(0f, 0f),
            Offset(1280f, 760f),
            Offset(640f, 380f),
            Offset(16.4f, 20.4f),
        )
        candidates.forEach { start ->
            // 模拟一次从 start 出发、增量巨大的拖动的终点
            val clamped = editorBallClamp(bounds, ball, start)
            assertTrue("起点 $start -> $clamped 越过了左边界", clamped.x >= bounds.left - 0.001f)
            assertTrue("起点 $start -> $clamped 越过了上边界", clamped.y >= bounds.top - 0.001f)
            assertTrue("起点 $start -> $clamped 越过了右边界", clamped.x + ball <= bounds.right + 0.001f)
            assertTrue("起点 $start -> $clamped 越过了下边界", clamped.y + ball <= bounds.bottom + 0.001f)
        }
    }

    @Test
    fun `拖到边界上就停住而不是绕回去`() {
        val bounds = editorBallSafeBounds(available = screen, ball = ball)
        val max = editorBallClamp(bounds, ball, Offset(5000f, 5000f))
        assertEquals(1280f - ball, max.x, 0.001f)
        assertEquals(760f - ball, max.y, 0.001f)
        val min = editorBallClamp(bounds, ball, Offset(-5000f, -5000f))
        assertEquals(0f, min.x, 0.001f)
        assertEquals(0f, min.y, 0.001f)
    }

    @Test
    fun `容器比球还小时不抛异常而是停在安全区起点`() {
        // 以前这一类输入会交给 coerceIn，而下界比上界大时它抛 IllegalArgumentException。
        // 折叠屏未展开、字体放大到 150% 之后容器都可能小于球的边长
        listOf(0f to 0f, 1f to 1f, 10f to 26f, 26f to 10f, 25.9f to 25.9f).forEach { (w, h) ->
            val bounds = editorBallSafeBounds(available = Size(w, h), ball = ball)
            // 无论算出什么，右下角都不小于左上角：这就是 coerceIn 不会抛的前提
            assertTrue("${w}x$h 给出 $bounds", bounds.right >= bounds.left)
            assertTrue("${w}x$h 给出 $bounds", bounds.bottom >= bounds.top)
            val placed = editorBallDefaultPosition(bounds, ball)
            val clamped = editorBallClamp(bounds, ball, placed)
            assertTrue("${w}x$h 的 $placed 越界", clamped.x >= bounds.left - 0.001f)
            assertTrue("${w}x$h 的 $placed 越界", clamped.y >= bounds.top - 0.001f)
        }
    }

    @Test
    fun `安全边界比容器还大时上界压回下界而不是留在容器外`() {
        // 转屏那一瞬 inset 可能先于尺寸更新到达，于是"容器减去 inset"会变成负数
        val bounds = editorBallSafeBounds(
            available = Size(200f, 100f),
            ball = ball,
            insets = EditorBallInsets(left = 500f, top = 500f, right = 500f, bottom = 500f),
        )
        assertTrue("$bounds", bounds.right >= bounds.left)
        assertTrue("$bounds", bounds.bottom >= bounds.top)
        val clamped = editorBallClamp(bounds, ball, Offset(10f, 10f))
        assertEquals(bounds.left, clamped.x, 0.001f)
        assertEquals(bounds.top, clamped.y, 0.001f)
    }

    @Test
    fun `负的边界值按零处理`() {
        val bounds = editorBallSafeBounds(
            available = screen,
            ball = ball,
            insets = EditorBallInsets(left = -10f, top = -10f, right = -10f, bottom = -10f),
        )
        // 负的 inset 只是噪声，因此按 0 收：结果与完全没有 inset 时逐位相同
        assertEquals(
            editorBallSafeBounds(available = screen, ball = ball),
            bounds,
        )
    }

    @Test
    fun `球的边长为零时也排得出合法区间`() {
        val bounds = editorBallSafeBounds(available = screen, ball = 0f)
        val clamped = editorBallClamp(bounds, 0f, Offset(9999f, 9999f))
        assertEquals(1280f, clamped.x, 0.001f)
        assertEquals(760f, clamped.y, 0.001f)
    }

    @Test
    fun `EditorBallInsets.None 就是四边都不收`() {
        assertEquals(
            editorBallSafeBounds(available = screen, ball = ball, insets = EditorBallInsets.None),
            editorBallSafeBounds(available = screen, ball = ball),
        )
    }

    @Test
    fun `竖屏也得到居中的落位`() {
        val bounds = editorBallSafeBounds(available = Size(760f, 1280f), ball = ball)
        val placed = editorBallDefaultPosition(bounds, ball)
        assertEquals((760f - ball) / 2f, placed.x, 0.001f)
        assertEquals(0f, placed.y, 0.001f)
    }
}
