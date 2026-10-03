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
 * 齿轮动作菜单的状态机
 *
 * 三种交互必须彼此分明：卡片本体选中、齿轮配置、播放启动。菜单如果能同时展开两份，
 * 屏幕上就会出现两片浮层互相压住，而且用户分不清动作属于哪张卡片。
 */
class ReduceInstanceMenuTest {

    @Test
    fun toggleOpensWhenNothingIsOpen() {
        assertEquals("a", reduceInstanceMenu(null, "a", InstanceMenuAction.Toggle))
    }

    @Test
    fun toggleClosesTheSameCard() {
        assertEquals("a", reduceInstanceMenu("a", "a", InstanceMenuAction.Toggle))
        assertNull(reduceInstanceMenu("a", "a", InstanceMenuAction.Toggle))
    }

    @Test
    fun toggleOnAnotherCardMovesTheMenu() {
        // 不能出现两份浮层：开 b 的同时必须关掉 a
        assertEquals("b", reduceInstanceMenu("a", "b", InstanceMenuAction.Toggle))
    }

    @Test
    fun atMostOneCardIsOpenAtATime() {
        var open = reduceInstanceMenu(null, "a", InstanceMenuAction.Open)
        open = reduceInstanceMenu(open, "b", InstanceMenuAction.Open)
        open = reduceInstanceMenu(open, "c", InstanceMenuAction.Open)
        assertEquals("c", open)
    }

    @Test
    fun closeAlwaysCloses() {
        assertNull(reduceInstanceMenu("a", "b", InstanceMenuAction.Close))
        assertNull(reduceInstanceMenu(null, "b", InstanceMenuAction.Close))
    }

    @Test
    fun openIsIdempotent() {
        assertEquals("a", reduceInstanceMenu("a", "a", InstanceMenuAction.Open))
    }
}

/**
 * 菜单里有哪些动作
 *
 * 这一组用例是"不能把任何一个既有功能弄丢"的清单：
 * 重命名、复制、导出整合包、置顶、打开目录、删除，一个都不能少。
 */
class InstanceMenuActionsTest {

    @Test
    fun usableInstanceOffersEveryAction() {
        val actions = instanceMenuActions(usable = true, pinned = false)
        assertTrue(InstanceAction.Configure in actions)
        assertTrue(InstanceAction.Rename in actions)
        assertTrue(InstanceAction.Copy in actions)
        assertTrue(InstanceAction.ExportModPack in actions)
        assertTrue(InstanceAction.SetPinned in actions)
        assertTrue(InstanceAction.OpenFolder in actions)
        assertTrue(InstanceAction.Delete in actions)
    }

    @Test
    fun brokenInstanceStillOffersDelete() {
        // 删除是清理一个已经失效的版本目录的唯一途径，因此它不受 usable 限制
        val actions = instanceMenuActions(usable = false, pinned = false)
        assertTrue("delete must stay available on a broken instance", InstanceAction.Delete in actions)
        assertTrue(InstanceAction.OpenFolder in actions)
        assertFalse(InstanceAction.Rename in actions)
        assertFalse(InstanceAction.Copy in actions)
        assertFalse(InstanceAction.ExportModPack in actions)
    }

    @Test
    fun pinLabelFollowsTheRealPinnedState() {
        // 菜单标题跟着实例真实的置顶态走，不会出现"已置顶却还能再置顶一次"
        assertTrue(InstanceAction.SetPinned in instanceMenuActions(usable = true, pinned = false))
        assertTrue(InstanceAction.ClearPinned in instanceMenuActions(usable = true, pinned = true))
        assertFalse(InstanceAction.SetPinned in instanceMenuActions(usable = true, pinned = true))
    }

    @Test
    fun brokenInstanceStillOffersTogglePin() {
        val actions = instanceMenuActions(usable = false, pinned = false)
        assertTrue(
            "pinning is a setting write, it does not need the version files",
            InstanceAction.SetPinned in actions
        )
    }

    @Test
    fun everyActionIsReachableForAUsableInstance() {
        val all = InstanceAction.entries
        val offered = instanceMenuActions(usable = true, pinned = false) +
            instanceMenuActions(usable = true, pinned = true)
        assertEquals(
            "no action may be unreachable",
            all.toSet(),
            offered.toSet(),
        )
    }

    @Test
    fun configureIsAlwaysFirst() {
        // 齿轮最直接的作用就是进配置，它排在第一位
        assertEquals(
            InstanceAction.Configure,
            instanceMenuActions(usable = true, pinned = false).first(),
        )
        assertEquals(
            InstanceAction.Configure,
            instanceMenuActions(usable = false, pinned = false).first(),
        )
    }

    @Test
    fun menuNeverRendersDuplicateActions() {
        // Unpin 与 Pin 是互斥的，同一份菜单里不能同时出现
        for (pinned in listOf(true, false)) {
            for (usable in listOf(true, false)) {
                val actions = instanceMenuActions(usable = usable, pinned = pinned)
                assertEquals(actions.size, actions.toSet().size)
            }
        }
    }
}
