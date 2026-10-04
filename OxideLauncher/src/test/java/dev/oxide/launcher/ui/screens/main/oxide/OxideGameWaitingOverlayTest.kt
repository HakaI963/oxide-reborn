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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * 游戏等待表面
 *
 * 旧的那一块是 GameScreen 里的 `GameInfoBox`：`BackgroundCard` +
 * `MaterialTheme.typography.bodyLarge` + `IconButton`，写的是
 * "The game is running. Waiting for the game screen to appear…"。
 * 它只有版本名与版本信息两行，用的是 Material 字体与图标，
 * 因此读起来像一条与当前界面无关的旧通知。
 *
 * 这里钉的是新表面的三件事：耗时只在真的有起始时刻时出现、
 * 秒数的写法只有两种、按钮只在真的被支持时才出现。
 */
class OxideGameWaitingOverlayTest {
    private fun facts(
        state: OxideGameWaitingState = OxideGameWaitingState.Waiting,
        instanceName: String = "1.21.1 Fabric",
        minecraftVersion: String? = "1.21.1",
        loaderLabel: String? = "Fabric 0.16.9",
        startedAtMillis: Long? = 1_000L,
        nowMillis: Long = 1_000L,
    ) = OxideGameWaitingFacts(
        state = state,
        instanceName = instanceName,
        minecraftVersion = minecraftVersion,
        loaderLabel = loaderLabel,
        startedAtMillis = startedAtMillis,
        nowMillis = nowMillis,
    )
    // -----------------------------------------------------------------------
    // 耗时
    // -----------------------------------------------------------------------
    /**
     * 没有起始时刻就不显示耗时
     *
     * 宿主给不出起跑时刻时显示一个从 0 开始自己走的数字，那是在编数据。
     */
    @Test
    fun thereIsNoElapsedTimeWithoutAStartStamp() {
        assertNull(oxideGameWaitingElapsedSeconds(startedAtMillis = null, nowMillis = 99_000L))
        assertNull(facts(startedAtMillis = null).elapsedSeconds)
    }
    @Test
    fun theElapsedSecondsComeStraightFromTheTwoStamps() {
        assertEquals(0, oxideGameWaitingElapsedSeconds(1_000L, 1_000L))
        // 999ms 还不满一秒：耗时按整秒**截断**，不进位。
        // （这里原本写 1，与下一行的 1999ms → 1 相互矛盾：同一条单调规则不可能
        //   同时把 999ms 记成 1 秒、把 1999ms 记成 1 秒；四舍五入会让 1999 → 2，
        //   而"还差 1ms 就到两秒"显示成 2 秒是超前。）
        assertEquals(0, oxideGameWaitingElapsedSeconds(1_000L, 1_999L))
        assertEquals(1, oxideGameWaitingElapsedSeconds(1_000L, 2_000L))
        assertEquals(1, oxideGameWaitingElapsedSeconds(1_000L, 2_999L))
        assertEquals(90, oxideGameWaitingElapsedSeconds(1_000L, 91_000L))
        assertEquals(90, facts(nowMillis = 91_000L).elapsedSeconds)
    }
    /** 宿主给的时刻在将来时只算 0，不写负数——负数会让人以为程序坏了 */
    @Test
    fun aFutureStartStampCountsAsZero() {
        assertEquals(0, oxideGameWaitingElapsedSeconds(10_000L, 5_000L))
        assertEquals(0, facts(startedAtMillis = 10_000L, nowMillis = 5_000L).elapsedSeconds)
    }
    @Test
    fun theElapsedFormatIsMinutesAndSecondsBelowAnHour() {
        assertEquals("0:00", formatOxideElapsedSeconds(0))
        assertEquals("0:07", formatOxideElapsedSeconds(7))
        assertEquals("0:59", formatOxideElapsedSeconds(59))
        assertEquals("1:00", formatOxideElapsedSeconds(60))
        assertEquals("1:23", formatOxideElapsedSeconds(83))
        assertEquals("59:59", formatOxideElapsedSeconds(3599))
    }
    @Test
    fun theElapsedFormatGainsAnHourFieldOnlyWhenNeeded() {
        assertEquals("1:00:00", formatOxideElapsedSeconds(3600))
        assertEquals("1:01:01", formatOxideElapsedSeconds(3661))
        assertEquals("2:00:00", formatOxideElapsedSeconds(7200))
    }
    /** 负数按 0 处理：时钟对不齐时不该打印出一个负的耗时 */
    @Test
    fun aNegativeSecondCountIsWrittenAsZero() {
        assertEquals("0:00", formatOxideElapsedSeconds(-5))
    }
    // -----------------------------------------------------------------------
    // 按钮
    // -----------------------------------------------------------------------
    /**
     * 收起永远给得出，结束游戏只在宿主接了回调时才有
     *
     * 现在这条链路上只有收起：一个按了没反应的"关闭游戏"键比没有更糟。
     */
    @Test
    fun closeIsTheOnlyActionTheCurrentChainSupports() {
        assertEquals(
            listOf(OxideGameWaitingAction.Close),
            oxideGameWaitingActions(canClose = true, cancelSupported = false),
        )
    }
    @Test
    fun cancelAppearsOnlyWhenTheHostSupportsIt() {
        assertEquals(
            listOf(OxideGameWaitingAction.Cancel, OxideGameWaitingAction.Close),
            oxideGameWaitingActions(canClose = true, cancelSupported = true),
        )
    }
    @Test
    fun aSurfaceWithNoActionAtAllIsAllowedByThePolicy() {
        assertTrue(oxideGameWaitingActions(canClose = false, cancelSupported = false).isEmpty())
        assertEquals(
            listOf(OxideGameWaitingAction.Cancel),
            oxideGameWaitingActions(canClose = false, cancelSupported = true),
        )
    }
    /** 取消排在收起之前：它是更重的一个动作，因此更靠左、更远离顺手点掉的那个 */
    @Test
    fun cancelIsOrderedBeforeClose() {
        assertEquals(
            OxideGameWaitingAction.Cancel,
            oxideGameWaitingActions(canClose = true, cancelSupported = true).first(),
        )
    }
    // -----------------------------------------------------------------------
    // 字段与尺寸
    // -----------------------------------------------------------------------
    /** 原版实例没有加载器：那一行整个不出现，而不是显示一个空的或"未知"的词 */
    @Test
    fun aVanillaInstanceHasNoLoaderField() {
        assertNull(facts(loaderLabel = null).loaderLabel)
    }
    @Test
    fun bothWaitingStatesAreReachableAndDistinct() {
        assertEquals(2, OxideGameWaitingState.entries.toSet().size)
        assertFalse(
            facts(state = OxideGameWaitingState.Waiting) ==
                facts(state = OxideGameWaitingState.FirstFrame)
        )
    }
    /** 这一块按内容高度摆，因此高度只是"绝不超过窗口"的上限 */
    @Test
    fun theSurfaceFitsEveryWindowAtEveryScale() {
        listOf(640 to 360, 731 to 412, 1280 to 720, 1920 to 1080).forEach { (width, height) ->
            listOf(75, 100, 150).forEach { scale ->
                val bounds = oxideGameWaitingBounds(
                    windowWidth = width.dp,
                    windowHeight = height.dp,
                    metrics = oxideMetricsFor(width, height, scale),
                )
                assertTrue(
                    "${width}x$height @${scale}%: ${bounds.width} + ${bounds.gutter * 2}",
                    bounds.fitsWithin(width.dp, height.dp),
                )
                assertTrue("${width}x$height @${scale}% maxHeight", bounds.maxHeight > 0.dp)
            }
        }
    }
    /** 放大界面时这一块跟着变宽，与启动器其余面板是同一套比例 */
    @Test
    fun theSurfaceGrowsWithTheGuiScale() {
        val normal = oxideGameWaitingBounds(
            windowWidth = 1280.dp,
            windowHeight = 720.dp,
            metrics = oxideMetricsFor(1280, 720, 100),
        )
        val large = oxideGameWaitingBounds(
            windowWidth = 1280.dp,
            windowHeight = 720.dp,
            metrics = oxideMetricsFor(1280, 720, 150),
        )
        assertTrue("${normal.width} vs ${large.width}", large.width > normal.width)
    }
    /** 窗口比面板的宽度上限还窄时，面板缩到"扣掉留白之后剩下的"而不是溢出 */
    @Test
    fun aVeryNarrowWindowShrinksTheSurfaceInsteadOfOverflowing() {
        val metrics = oxideMetricsFor(320, 200, 100)
        val bounds = oxideGameWaitingBounds(320.dp, 200.dp, metrics)
        assertTrue(bounds.fitsWithin(320.dp, 200.dp))
        assertTrue("width ${bounds.width}", bounds.width > 0.dp)
    }
}
