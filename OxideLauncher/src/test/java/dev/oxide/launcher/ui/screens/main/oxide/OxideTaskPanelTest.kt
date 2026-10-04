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

import dev.oxide.launcher.coroutine.TaskStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OxideTaskPanelTest {

    /**
     * Task.updateProgress 约定负值代表"进度不可知"，并且会把 -1f 原样留着。
     * 如果把它当成 0 或者当成一个真百分比，用户看到的就是假的进度条。
     */
    @Test
    fun negativeProgressMeansUnknown() {
        assertNull(oxideTaskProgressPercent(-1f))
        assertNull(oxideTaskProgressPercent(-0.5f))
    }

    @Test
    fun progressPercentIsTruncatedNotRounded() {
        assertEquals(0, oxideTaskProgressPercent(0f))
        assertEquals(49, oxideTaskProgressPercent(0.499f))
        assertEquals(50, oxideTaskProgressPercent(0.5f))
        assertEquals(99, oxideTaskProgressPercent(0.999f))
        assertEquals(100, oxideTaskProgressPercent(1f))
    }

    @Test
    fun progressPercentIsClamped() {
        // updateProgress 本身会夹取，但面板不该依赖调用方一定先夹过
        assertEquals(100, oxideTaskProgressPercent(1.4f))
        assertEquals(0, oxideTaskProgressPercent(0f))
    }

    @Test
    fun rateIsHiddenWhenUnknownOrZero() {
        assertNull(oxideTaskRateText(null))
        assertNull(oxideTaskRateText(0L))
        assertNull(oxideTaskRateText(-1L))
    }

    @Test
    fun rateIsFormattedAsPerSecond() {
        assertEquals("2.00 KB/s", oxideTaskRateText(2048L))
        assertEquals("1.00 MB/s", oxideTaskRateText(1024L * 1024L))
    }

    @Test
    fun everyStageHasItsOwnLabel() {
        val labels = listOf(
            oxideTaskStageLabel(TaskStage.PREPARING),
            oxideTaskStageLabel(TaskStage.RUNNING),
            oxideTaskStageLabel(TaskStage.COMPLETED),
        )
        assertEquals(labels.size, labels.distinct().size)
    }
}