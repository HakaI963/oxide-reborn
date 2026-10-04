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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实例页的层级：页头会不会把标题挤没，以及网格到底该占几行
 *
 * 这两件事都是真机上才看得出来、但完全可以在单元测试里钉死的：
 * 标题被截断是页面上最不该发生的一种截断；网格按固定两行平分高度时，
 * 只有一张卡片会在下面留出一整条空白。
 */
class OxideInstancesLayoutTest {

    // ---- 页头 -------------------------------------------------------------

    @Test
    fun refreshStaysOnTheTitleRowWhileTheTitleStillFits() {
        // 刷新 60 + 主操作 90 + 间距 3 = 153dp，内容区 320dp：
        // 标题还剩 167dp，比要保住的 110dp 还宽，因此"刷新"留在标题行
        assertTrue(
            instancesHeaderKeepsRefreshInline(
                contentWidthDp = 320f,
                installWidthDp = 90f,
                refreshWidthDp = 60f,
                gapDp = 3f,
                minTitleWidthDp = 110f,
            )
        )
    }

    @Test
    fun refreshFoldsToTheSubtitleRowRatherThanTruncatingTheTitle() {
        // 同样两个按钮，内容区只有 240dp：留在标题行时标题只剩 87dp，会被截断。
        // 折到副标题那一行之后，标题独占整行
        assertFalse(
            instancesHeaderKeepsRefreshInline(
                contentWidthDp = 240f,
                installWidthDp = 90f,
                refreshWidthDp = 60f,
                gapDp = 3f,
                minTitleWidthDp = 110f,
            )
        )
        // 折走之后标题行里只剩主操作，240dp 里标题还剩 150dp，绰绰有余
        assertTrue(
            "折走之后标题必须真的放得下",
            240f - 90f >= 110f,
        )
    }

    @Test
    fun theDecisionCannotSelfSustain() {
        // 自维持的循环长这样：量到"折走之后的窄"→于是又能放下→于是又画回去→再量到宽的。
        // 判据只看输入与实测宽度，不看此刻画了什么，所以这里逐步推下去必然收敛
        var inline = true
        val install = 90f
        val refresh = 60f
        val gap = 3f
        val minTitle = 110f
        val content = 240f
        repeat(5) {
            inline = instancesHeaderKeepsRefreshInline(content, install, refresh, gap, minTitle)
        }
        assertFalse("必须稳定在折走那一侧", inline)
    }

    @Test
    fun anUnmeasuredHeaderIsTreatedAsFittingSoTheFirstFrameIsStable() {
        // 首帧还没量到宽度：照常画一帧，量到之后再决定折不折。
        // 反过来会让第一帧少一个按钮然后再跳出来
        assertTrue(
            instancesHeaderKeepsRefreshInline(
                contentWidthDp = 240f,
                installWidthDp = 0f,
                refreshWidthDp = 0f,
                gapDp = 3f,
                minTitleWidthDp = 110f,
            )
        )
    }

    @Test
    fun aDegenerateWindowDoesNotFoldAnythingAway() {
        assertTrue(
            instancesHeaderKeepsRefreshInline(
                contentWidthDp = 0f,
                installWidthDp = 90f,
                refreshWidthDp = 60f,
                gapDp = 3f,
                minTitleWidthDp = 110f,
            )
        )
    }

    // ---- 网格行数 ---------------------------------------------------------

    @Test
    fun aSingleCardOccupiesOneRowSoThereIsNoDeadSpaceUnderIt() {
        // 参考稿是两行网格，但只有一张卡片时按两行平分会把它撑到两行那么高
        assertEquals(1, instanceGridRows(itemCount = 1, columns = 1, maxRows = 2))
    }

    @Test
    fun cardsAreCountedAgainstTheRealColumnCount() {
        assertEquals("单列两张占两行", 2, instanceGridRows(2, columns = 1, maxRows = 2))
        assertEquals("两列两张只占一行", 1, instanceGridRows(2, columns = 2, maxRows = 2))
        assertEquals("两列五张占三行", 3, instanceGridRows(5, columns = 2, maxRows = 2))
    }

    @Test
    fun theGridNeverExceedsTheReferenceRowCount() {
        // 卡片再多也最多两行，否则就成了另一种布局
        assertEquals(2, instanceGridRows(12, columns = 3, maxRows = 2))
    }

    @Test
    fun anEmptyOrDegenerateGridStillAsksForOneRow() {
        // 零张卡片时行高没有意义，但返回 0 会让 instanceRowHeight 拿到 rows<=0 的分支
        assertEquals(1, instanceGridRows(0, columns = 1, maxRows = 2))
        assertEquals(1, instanceGridRows(3, columns = 1, maxRows = 0))
        // 列数为 0 时按一列算，而不是除以零
        assertEquals(2, instanceGridRows(3, columns = 0, maxRows = 2))
    }

    @Test
    fun oneRowGivesTheSingleCardTheWholeAvailableHeight() {
        // 一行时行高等于全部可用高度（减去 0 个间距），不再被除以 2
        val row = instanceRowHeight(
            availableHeightDp = 300f,
            cardGapDp = 9f,
            rows = instanceGridRows(1, columns = 1, maxRows = 2),
            minRowHeightDp = 133f,
        )
        assertEquals(300f, row, 0.01f)
    }
}