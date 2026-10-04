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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件页与日志页的自适应尺寸
 *
 * 这两块表面都是"内容"：栏数、每栏占多少、路径条显示几级，都必须只由**可用宽度**
 * 决定。写死宽度或者只在一两个断点上试过，都会得到"小横屏挤成一团、大屏空一半"。
 *
 * 期望值不是拍出来的：`oxideMetricsFor` 就是页面自己取尺寸的那个纯函数，
 * 所以这里用它的输出当 [cardMinWidth]，测的正是页面真正会看到的数。
 */
class OxideContentLayoutTest {

    private fun metricsAt(widthDp: Int) = oxideMetricsFor(widthDp, 360)

    // ---- 并排还是堆叠 -------------------------------------------------------

    @Test
    fun aSmallLandscapeKeepsBothColumnsSideBySide() {
        // 640x360 是这套界面必须活下来的最小横屏。横屏里**高度**才是稀缺资源，
        // 把来源清单和日志正文纵向堆叠，等于把正文压到只剩几行。
        val metrics = metricsAt(640)
        val content = 640.dp - metrics.pagePaddingH * 2
        assertTrue(
            "a 640dp landscape must keep the log source list beside the log body",
            oxideContentLayoutFor(content, metrics.cardMinWidth).sideBySide,
        )
    }

    @Test
    fun tooNarrowForTwoColumnsAndTheyStackInstead() {
        val metrics = metricsAt(480)
        val content = 480.dp - metrics.pagePaddingH * 2
        val layout = oxideContentLayoutFor(content, metrics.cardMinWidth)
        assertFalse(
            "480dp minus the gutters cannot hold two cards, so the columns must stack",
            layout.sideBySide,
        )
    }

    @Test
    fun theSideBySideDecisionFollowsTheCardWidthRatherThanAFixedThreshold() {
        // 阈值必须跟着 [cardMinWidth] 走：界面放大之后 cardMinWidth 变大，
        // 同样的可用宽度就该落到堆叠那一侧。写死一个像素值会让放大后的
        // 两列各拿到一半宽度，全是省略号。
        val wide = 900.dp
        assertEquals(
            oxideContentLayoutFor(wide, 220.dp).sideBySide,
            oxideContentLayoutFor(wide, 400.dp).sideBySide,
        )
        assertTrue(
            "a wider card minimum must not make a narrow window side-by-side",
            oxideContentLayoutFor(wide, 220.dp).sideBySide &&
                !oxideContentLayoutFor(wide, 520.dp).sideBySide,
        )
    }

    // ---- 堆叠时各自占多少 ---------------------------------------------------

    @Test
    fun theStackedWeightsAlwaysGiveTheBodyMoreRoomThanTheList() {
        for (widthDp in listOf(360, 480, 560, 640, 900, 1280, 2560)) {
            val metrics = metricsAt(widthDp)
            val layout = oxideContentLayoutFor(
                availableWidth = widthDp.dp - metrics.pagePaddingH * 2,
                cardMinWidth = metrics.cardMinWidth,
            )
            val name = "widthDp=$widthDp"
            assertTrue("$name: list weight must be positive", layout.listWeight > 0f)
            assertTrue("$name: body weight must be positive", layout.detailWeight > 0f)
            assertTrue(
                "$name: the log body must get at least as much room as the list",
                layout.detailWeight >= layout.listWeight,
            )
            assertEquals(
                "$name: the two weights must fill the column exactly",
                1f,
                layout.listWeight + layout.detailWeight,
                1e-6f,
            )
        }
    }

    // ---- 路径条显示几级 -----------------------------------------------------

    @Test
    fun thePathBarAlwaysKeepsAtLeastTheCurrentDirectory() {
        for (widthDp in listOf(320, 480, 640, 1280)) {
            val metrics = metricsAt(widthDp)
            val crumbs = oxideContentLayoutFor(
                availableWidth = widthDp.dp - metrics.pagePaddingH * 2,
                cardMinWidth = metrics.cardMinWidth,
            ).crumbs
            assertTrue("widthDp=$widthDp: at least one crumb", crumbs >= 1)
        }
    }

    @Test
    fun aWiderWindowShowsMoreOfThePathAndNeverMoreThanTheCap() {
        val narrow = oxideContentLayoutFor(560.dp, 285.dp).crumbs
        val wide = oxideContentLayoutFor(2400.dp, 285.dp).crumbs
        assertTrue("a wider window must reveal more levels, got $narrow then $wide", wide > narrow)
        assertTrue("the crumb count must stay bounded", wide <= 5)
    }

    @Test
    fun thePathBarKeepsTheTailAndReplacesTheRestWithAnEllipsis() {
        val labels = listOf("0", "1", "2", "3", "4", "5")
        val crumbs = oxidePathCrumbsFor(labels, maxCrumbs = 3)

        assertEquals(3, crumbs.size)
        // 被丢掉的那几级用一个不可点的省略号交代
        assertEquals(OxidePathEllipsis, crumbs.first().label)
        assertEquals(-1, crumbs.first().chainIndex)
        // 末尾永远是当前目录，而且它不可点
        assertEquals("5", crumbs.last().label)
        assertTrue(crumbs.last().isCurrent)
        // 每一段都带着自己在层级链里的下标，点哪一段都对应得到真实目录
        assertEquals(listOf(-1, 4, 5), crumbs.map { it.chainIndex })
    }

    @Test
    fun aShortPathIsShownWholeAndTheLastCrumbIsAlwaysTheCurrentOne() {
        val labels = listOf("games", "1.20.1")
        val crumbs = oxidePathCrumbsFor(labels, maxCrumbs = 5)

        assertEquals(listOf("games", "1.20.1"), crumbs.map { it.label })
        assertFalse("an ancestor is not the current directory", crumbs.first().isCurrent)
        assertTrue(crumbs.last().isCurrent)
        assertEquals(listOf(0, 1), crumbs.map { it.chainIndex })
    }

    @Test
    fun aSingleCrumbLimitStillShowsTheCurrentDirectory() {
        // 上限被压到 1 时只剩当前目录——宁可少一级，也不能什么都不显示，
        // 否则用户不知道自己在哪一层
        val crumbs = oxidePathCrumbsFor(listOf("a", "b", "c"), maxCrumbs = 1)
        assertEquals(1, crumbs.size)
        assertEquals("c", crumbs.single().label)
        assertTrue(crumbs.single().isCurrent)
    }

    @Test
    fun anUnknownDirectoryChainRendersNoPathBarAtAll() {
        // 组合还没拿到第一次扫描结果时层级链是空的；那一条空的路径条
        // 只会留下一排分隔符，因此返回空列表
        assertTrue(oxidePathCrumbsFor(emptyList(), maxCrumbs = 5).isEmpty())
    }

    @Test
    fun aNonsensicalCrumbLimitCannotProduceAnUnreadablePathBar() {
        // 0 或负数是配置错误，不是"显示零级"：仍然至少留当前目录
        for (limit in listOf(0, -3)) {
            val crumbs = oxidePathCrumbsFor(listOf("a", "b"), maxCrumbs = limit)
            assertTrue("limit=$limit must still show something", crumbs.isNotEmpty())
            assertTrue(crumbs.last().isCurrent)
        }
    }
}