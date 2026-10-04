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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.vulkan_checker.VULKAN_DIALOG_MIN_LIST_HEIGHT_DP
import dev.oxide.launcher.ui.vulkan_checker.VulkanResultLayout
import dev.oxide.launcher.ui.vulkan_checker.vulkanDialogBounds
import dev.oxide.launcher.ui.vulkan_checker.vulkanResultLayout
import dev.oxide.launcher.ui.vulkan_checker.vulkanResultListHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vulkan 对话框的有界策略
 *
 * 检测结果可能列到几百条扩展与功能名（`VulkanRequirements.EXTENSIONS` 与
 * `FEATURES` 都会整个列出来），所以对话框的尺寸必须由**窗口**决定而不是由内容决定。
 * 这一段钉的就是那条策略：面板先被夹成有限的绝对值，结果列表再被夹进其中一块，
 * 放不下时只在这一块里滚。
 *
 * 它同时是那类崩溃的护栏：面板一旦不封顶，里面的 `verticalScroll` 就会量到
 * `maxHeight == Infinity` 并抛异常。
 */
class VulkanDialogBoundsTest {

    private fun bounds(
        widthDp: Int,
        heightDp: Int,
        guiScalePercent: Int = 100,
    ) = vulkanDialogBounds(
        windowWidth = widthDp.dp,
        windowHeight = heightDp.dp,
        metrics = oxideMetricsFor(widthDp, heightDp, guiScalePercent),
    )

    /** 横屏手机那一档：Vulkan 检测最常遇到的最紧窗口 */
    private val phones = listOf(640 to 360, 731 to 412, 873 to 480)

    /** 平板与桌面 */
    private val tablets = listOf(1280 to 720, 1920 to 1080)

    private val scales = listOf(75, 100, 150)

    @Test
    fun theDialogFitsEveryPhoneWindowAtEveryScale() {
        phones.forEach { (width, height) ->
            scales.forEach { scale ->
                val b = bounds(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%: ${b.width}x${b.height} + ${b.gutter * 2}",
                    b.fitsWithin(width.dp, height.dp),
                )
            }
        }
    }

    @Test
    fun theDialogFitsEveryTabletWindowAtEveryScale() {
        tablets.forEach { (width, height) ->
            scales.forEach { scale ->
                val b = bounds(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%: ${b.width}x${b.height} + ${b.gutter * 2}",
                    b.fitsWithin(width.dp, height.dp),
                )
            }
        }
    }

    /** 三段尺寸都是有限的正值：`verticalScroll` 因此永远拿得到有限的 maxHeight */
    @Test
    fun everyDimensionIsFiniteAndPositive() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                val b = bounds(width, height, scale)
                listOf(
                    "width" to b.width,
                    "height" to b.height,
                    "listMaxHeight" to b.listMaxHeight,
                ).forEach { (name, value: Dp) ->
                    assertTrue(
                        "${width}x$height @${scale}% $name = $value",
                        value.value.isFinite() && value.value > 0f,
                    )
                }
            }
        }
    }

    /** 结果区必须比整块对话框矮，否则标题与确认键就没有地方放 */
    @Test
    fun theResultAreaIsAlwaysShorterThanTheDialog() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                val b = bounds(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%: ${b.listMaxHeight} vs ${b.height}",
                    b.listInsideDialog,
                )
            }
        }
    }

    /**
     * 结果区永远是正高度
     *
     * 矮屏上如果只按比例算，会算出几 dp 的"列表"，那与没有列表一样。
     */
    @Test
    fun theResultAreaNeverCollapsesToZero() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                assertTrue(
                    "${width}x$height @${scale}%",
                    bounds(width, height, scale).listPositive,
                )
            }
        }
    }

    /** 窗口比留白还小时也不能算出负宽度——Dp 是浮点数，不会像 Int 那样自动夹住 */
    @Test
    fun aWindowSmallerThanTheGutterDoesNotProduceNegativeValues() {
        val metrics = oxideMetricsFor(48, 48, 100)
        val b = vulkanDialogBounds(48.dp, 48.dp, metrics)
        listOf(b.width, b.height, b.listMaxHeight).forEach {
            assertTrue("$it", it.value >= 0f && it.value.isFinite())
        }
    }

    // -----------------------------------------------------------------------
    // 放不下时才滚
    // -----------------------------------------------------------------------

    /** 正好等高时不需要滚动：多出一像素滚动条只会让人以为还有内容没看完 */
    @Test
    fun contentThatExactlyFitsDoesNotScroll() {
        assertEquals(
            VulkanResultLayout.Fits,
            vulkanResultLayout(contentHeightDp = 100, listMaxHeightDp = 100),
        )
    }

    @Test
    fun contentTallerThanTheBoundScrolls() {
        assertEquals(
            VulkanResultLayout.Scrolls,
            vulkanResultLayout(contentHeightDp = 101, listMaxHeightDp = 100),
        )
    }

    /**
     * 检测不出来时只有一行说明：结果区收到那一行的高度，不撑出一个巨大的空盒子
     *
     * 这里的用例原本用 40dp 的内容去断言"收到 40"，但 40dp 比下限
     * [VULKAN_DIALOG_MIN_LIST_HEIGHT_DP]（64dp）还小——下限是同一份契约的另一半，
     * 由 theResultAreaNeverCollapsesBelowTheFloor 钉死（内容 1dp 也拿到 64dp）。
     * 因此"按内容高度收缩"只能用在**高于下限**的内容上：
     * 120dp 的内容放进 200dp 的结果区，拿到的是 120 而不是 200。
     */
    @Test
    fun contentThatFitsShrinksTheResultArea() {
        assertEquals(120, vulkanResultListHeight(contentHeightDp = 120, maxHeightDp = 200))
        // 比下限还矮的内容收到下限，而不是收到内容高度
        assertEquals(
            VULKAN_DIALOG_MIN_LIST_HEIGHT_DP,
            vulkanResultListHeight(contentHeightDp = 40, maxHeightDp = 200),
        )
    }

    /** 放不下时顶到上限，滚动只发生在这一块里 */
    @Test
    fun contentThatOverflowsFillsTheResultArea() {
        assertEquals(200, vulkanResultListHeight(contentHeightDp = 900, maxHeightDp = 200))
    }

    /**
     * 一行的说明也要读得出来
     *
     * 下限保证内容只有一行时不会算出一个几 dp 的死区；同时下限被上限夹住，
     * 因此窗口比下限还矮时也不会算出 0。
     */
    @Test
    fun theResultAreaNeverCollapsesBelowTheFloor() {
        assertEquals(
            VULKAN_DIALOG_MIN_LIST_HEIGHT_DP,
            vulkanResultListHeight(contentHeightDp = 1, maxHeightDp = 200),
        )
        assertEquals(
            VULKAN_DIALOG_MIN_LIST_HEIGHT_DP,
            vulkanResultListHeight(contentHeightDp = 1, maxHeightDp = 1),
        )
        assertEquals(
            VULKAN_DIALOG_MIN_LIST_HEIGHT_DP,
            vulkanResultListHeight(contentHeightDp = 1, maxHeightDp = 0),
        )
    }

    /** 上限永远压住下限，因此函数对任何输入都返回一个正整数 */
    @Test
    fun theResultAreaHeightIsAPositiveIntegerForEveryInput() {
        listOf(0, 1, 63, 64, 65, 200, 10_000, -1).forEach { content ->
            listOf(0, 1, 63, 64, 200, 10_000).forEach { max ->
                val height = vulkanResultListHeight(
                    contentHeightDp = content,
                    maxHeightDp = max,
                    minHeightDp = 10,
                )
                assertTrue("content=$content max=$max -> $height", height > 0)
            }
        }
    }

    /** 结果区永远不超过对话框给它的上限——这就是"有界"的定义 */
    @Test
    fun theResultAreaHeightNeverExceedsTheBound() {
        listOf(0, 1, 40, 100, 500, 5_000).forEach { content ->
            listOf(20, 64, 100, 300).forEach { max ->
                val height = vulkanResultListHeight(
                    contentHeightDp = content,
                    maxHeightDp = max,
                    minHeightDp = 10,
                )
                assertTrue(
                    "content=$content max=$max -> $height",
                    height <= maxOf(max, 10),
                )
            }
        }
    }
}