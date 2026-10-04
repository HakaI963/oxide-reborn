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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动面板的几何
 *
 * 面板的高度必须是**固定值**而不是上限：里面有一个 `verticalScroll`，
 * 高度一旦不封顶，那一层就会被以 `maxHeight == Infinity` 测量并抛
 * `Vertically scrollable component was measured with an infinity maximum height`
 * （这正是 v1.5.0 那个 P0 的成因）。所以这里钉的是三件事：
 *
 * 1. 面板连同留白在任何窗口、任何界面缩放下都放得进可用区域；
 * 2. 阶段列表那一块永远是**正的**有限高度；
 * 3. 放大界面时列表至少还有一行 —— 否则当前那一条阶段会整个看不见。
 */
class OxideLaunchPanelGeometryTest {

    private fun geometry(
        widthDp: Int,
        heightDp: Int,
        guiScalePercent: Int = OxideGuiScaleDefaultPercent,
    ) = oxideLaunchPanelGeometry(
        metrics = oxideMetricsFor(widthDp, heightDp, guiScalePercent),
        availableWidthDp = widthDp,
        availableHeightDp = heightDp,
    )

    /** 横屏手机那一档，也是这类表面最常遇到的最紧窗口 */
    private val phones = listOf(
        640 to 360,
        731 to 412,
        800 to 480,
        873 to 480,
    )

    /** 平板与桌面 */
    private val tablets = listOf(
        1280 to 720,
        1920 to 1080,
        2560 to 1600,
    )

    private val scales = listOf(
        OxideGuiScaleMinPercent,
        OxideGuiScaleDefaultPercent,
        OxideGuiScaleMaxPercent,
    )

    @Test
    fun thePanelFitsEveryPhoneWindowAtEveryScale() {
        phones.forEach { (width, height) ->
            scales.forEach { scale ->
                val geometry = geometry(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%: ${geometry.widthDp}x${geometry.heightDp} + margin ${geometry.marginDp}",
                    geometry.fitsWithin(width, height),
                )
            }
        }
    }

    @Test
    fun thePanelFitsEveryTabletWindowAtEveryScale() {
        tablets.forEach { (width, height) ->
            scales.forEach { scale ->
                val geometry = geometry(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%: ${geometry.widthDp}x${geometry.heightDp} + margin ${geometry.marginDp}",
                    geometry.fitsWithin(width, height),
                )
            }
        }
    }

    /** 阶段列表那一块永远是正高度，否则滚动容器量到 0 就没有"可滚"可言 */
    @Test
    fun theStageViewportIsAlwaysPositive() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                assertTrue(
                    "${width}x$height @${scale}%",
                    geometry(width, height, scale).stageViewportHeightDp > 0,
                )
            }
        }
    }

    /**
     * 放大到极限时列表仍然至少有一行
     *
     * 这一条是"当前那一条阶段永远看得见"的可测形式：行高跟着 navItemHeight 走，
     * 而 navItemHeight 在装不下时会被 `oxideNavItemHeightFor` 压回一个下限，
     * 因此两者相除不会掉到 0。
     */
    @Test
    fun theStageViewportAlwaysShowsAtLeastOneRow() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                val geometry = geometry(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}% -> ${geometry.maxVisibleStageRows} rows",
                    geometry.maxVisibleStageRows >= 1,
                )
            }
        }
    }

    /** 尺寸全都是有限正值，绝不能出现 0 或负数把面板压没 */
    @Test
    fun everyDimensionIsAFinitePositiveInteger() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                val geometry = geometry(width, height, scale)
                listOf(
                    "marginDp" to geometry.marginDp,
                    "widthDp" to geometry.widthDp,
                    "heightDp" to geometry.heightDp,
                    "stageViewportHeightDp" to geometry.stageViewportHeightDp,
                    "maxVisibleStageRows" to geometry.maxVisibleStageRows,
                ).forEach { (name, value) ->
                    assertTrue("${width}x$height @${scale}% $name = $value", value > 0)
                }
            }
        }
    }

    /** 阶段列表必须比整块面板矮，否则标题与进度条就没有地方放 */
    @Test
    fun theStageViewportIsShorterThanThePanel() {
        (phones + tablets).forEach { (width, height) ->
            scales.forEach { scale ->
                val geometry = geometry(width, height, scale)
                assertTrue(
                    "${width}x$height @${scale}%",
                    geometry.stageViewportHeightDp < geometry.heightDp,
                )
            }
        }
    }

    /** 放大界面时面板确实跟着变大（而不是三种缩放都画出同一块板） */
    @Test
    fun thePanelGrowsWithTheGuiScale() {
        val normal = geometry(1280, 720, 100)
        val large = geometry(1280, 720, 150)
        assertTrue(
            "100%: ${normal.widthDp}x${normal.heightDp}, 150%: ${large.widthDp}x${large.heightDp}",
            large.heightDp > normal.heightDp,
        )
    }

    /** 同一套缩放下，宽屏的面板不该比 640 宽的手机还小 —— 否则它就不是居中的一块板了 */
    @Test
    fun aWiderWindowGetsAProportionallyWiderPanel() {
        val phone = geometry(640, 360, 100)
        val tablet = geometry(1280, 720, 100)
        assertTrue(
            "phone ${phone.widthDp}dp vs tablet ${tablet.widthDp}dp",
            tablet.widthDp > phone.widthDp,
        )
    }

    /** 640x360 这一档上面板不超过窗口的八成宽（居中的一块板，不是一整屏） */
    @Test
    fun thePanelStaysCompactOnTheSmallestSupportedWindow() {
        val geometry = geometry(640, 360, 100)
        assertTrue(
            "width ${geometry.widthDp}dp of 640",
            geometry.widthDp + geometry.marginDp * 2 <= 640 * 0.8f,
        )
    }
}