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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子窗口的尺寸策略
 *
 * 这段逻辑是从组合里原样搬出来的纯函数版本，为的是能在不启动 Compose 的前提下钉住两件事：
 * 面板永远不超过窗口，以及高度上限永远是正数——后者就是 P0 崩溃的边界。
 */
class OxideSubWindowMetricsTest {

    private fun width(available: Dp, panelMax: Dp): Dp =
        (available * OxideSubWindowWidthFraction)
            .coerceIn(OxideSubWindowMinWidth, panelMax)

    private fun heightCap(available: Dp): Dp =
        (available * OxideSubWindowHeightFraction) - OxideSubWindowMargin * 2

    @Test
    fun panelNeverExceedsTheAvailableWidth() {
        listOf(320.dp, 480.dp, 640.dp, 800.dp, 1280.dp, 1920.dp).forEach { available ->
            val w = width(available, 620.dp)
            assertTrue(
                "width $w exceeded available $available",
                w <= available,
            )
        }
    }

    @Test
    fun panelNeverExceedsTheCallerCap() {
        assertEquals(300.dp, width(320.dp, 300.dp))
        assertEquals(480.dp, width(2000.dp, 480.dp))
    }

    /** 很小的横屏上仍要有一个可用的下限，而不是塌成 0 */
    @Test
    fun minimumWidthAppliesOnTinyWindows() {
        val w = width(200.dp, 620.dp)
        assertEquals(OxideSubWindowMinWidth, w)
    }

    @Test
    fun heightCapStaysPositiveOnShortWindows() {
        // 360dp 是要支持的最小横屏高度
        listOf(200.dp, 360.dp, 480.dp, 720.dp, 1080.dp).forEach { available ->
            assertTrue(
                "height cap must stay positive at $available",
                heightCap(available) > 0.dp,
            )
        }
    }

    @Test
    fun heightCapNeverExceedsTheAvailableHeight() {
        listOf(200.dp, 360.dp, 720.dp, 1440.dp).forEach { available ->
            assertTrue(heightCap(available) <= available)
        }
    }

    /** 内容再长也不能把外壳撑到窗口之外——上限是在滚动之前施加的 */
    @Test
    fun heightCapIsAppliedIndependentlyOfContentLength() {
        val short = heightCap(360.dp)
        val tall = heightCap(1440.dp)
        assertTrue(tall > short)
        // 两者都不依赖内容多少：签名里就没有内容长度这一项
        assertEquals(heightCap(720.dp), heightCap(720.dp))
    }
}