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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选中态的呈现模型
 *
 * 用户明确说过不要"一个巨大的选中框"，而之前的实现正是这样：选中的卡片多一圈
 * 更粗的描边、左侧多一条竖条、正文上方又多一个 SELECTED 徽章，于是那张卡片
 * 比邻居高出一截，整张网格被顶歪。
 *
 * 这些用例把"选中不得改变任何几何"钉成一条契约。
 */
class InstanceSelectionPresentationTest {

    @Test
    fun unselectedShowsNoIndicator() {
        val presentation = instanceSelectionPresentation(selected = false)
        assertEquals(InstanceSelectedIndicator.None, presentation.indicator)
        assertFalse(presentation.announceAsCurrent)
    }

    @Test
    fun selectedShowsRestrainedIndicator() {
        val presentation = instanceSelectionPresentation(selected = true)
        assertEquals(InstanceSelectedIndicator.Dot, presentation.indicator)
        assertTrue(presentation.announceAsCurrent)
    }

    @Test
    fun selectionChangesNoGeometryAtAll() {
        val on = instanceSelectionPresentation(selected = true)
        val off = instanceSelectionPresentation(selected = false)

        // 指示槽位宽度必须一致：槽位是每张卡片都画出来的，选中不占额外位置
        assertEquals(
            "the indicator slot must be reserved on every card",
            off.indicatorSlotWidthDp,
            on.indicatorSlotWidthDp,
            0f,
        )
        assertEquals(
            "badges must not be re-laid-out when a card is selected",
            off.badgeSlots,
            on.badgeSlots,
        )

        // 这三个量一旦非零，选中就会改变卡片的尺寸或描边宽度
        assertTrue("selected must not add a top inset", on.geometryStable)
        assertEquals(0f, on.extraTopInsetDp, 0f)
        assertEquals(0f, on.extraBorderDp, 0f)
    }

    @Test
    fun indicatorSlotIsNarrowEnoughToStayRestrained() {
        // 5dp 是参考稿的间距量级；只要它不膨胀成一个占位块就符合预期
        val presentation = instanceSelectionPresentation(selected = true)
        assertTrue(
            "the reserved indicator slot must stay small",
            presentation.indicatorSlotWidthDp in 0f..8f,
        )
    }

    @Test
    fun badgeSlotsStayBoundedSoTheBottomRowHeightIsFixed() {
        // 徽章行的高度取决于最多能排几个；不设上限的话选中态加徽章就会长高
        assertEquals(2, instanceSelectionPresentation(selected = true).badgeSlots)
        assertEquals(2, instanceSelectionPresentation(selected = false).badgeSlots)
    }
}

/**
 * 当前实例落在哪一个
 *
 * 顺序错了会直接表现为"高亮指着一个不存在的实例"，或者"装完新版本之后高亮消失"。
 */
class ResolveActiveInstanceTest {

    private val installed = listOf("1.21.10", "1.20.1", "1.16.5")

    @Test
    fun emptyListHasNoActiveInstance() {
        assertNull(resolveActiveInstance(null, emptyList(), null))
        assertNull(resolveActiveInstance("1.21.10", emptyList(), "1.20.1"))
    }

    @Test
    fun backendCurrentWinsWhenTheUserHasNotPickedYet() {
        // 打开页面时的落点必须来自后端记录，而不是"列表第一个"，
        // 否则重启之后选中的实例会悄悄变成另一个
        assertEquals("1.20.1", resolveActiveInstance(null, installed, "1.20.1"))
    }

    @Test
    fun userPickWinsOverBackend() {
        assertEquals("1.16.5", resolveActiveInstance("1.16.5", installed, "1.20.1"))
    }

    @Test
    fun fallsBackToBackendWhenThePickIsNoLongerInstalled() {
        // 用户刚点的那个被删掉了，或者重命名了：高亮必须落到一个真实存在的实例
        assertEquals("1.20.1", resolveActiveInstance("gone", installed, "1.20.1"))
    }

    @Test
    fun fallsBackToFirstRealInstanceWhenBackendIsStale() {
        // current.json 里记的名字在磁盘上已经没有了（外部删掉了目录）
        assertEquals("1.21.10", resolveActiveInstance(null, installed, "vanished"))
    }

    @Test
    fun lastResortIsTheFirstInstalledInstance() {
        // 两者都没有时也必须有一个落点，否则整页没有高亮
        assertEquals("1.21.10", resolveActiveInstance(null, installed, null))
    }

    @Test
    fun neverReturnsSomethingThatIsNotInstalled() {
        val resolved = resolveActiveInstance("gone", installed, "alsoGone")
        assertTrue("resolved instance must exist", resolved in installed)
    }
}
