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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航世代的单测
 *
 * 线上缺陷的形状：打开联机 / 文件 / 账号 / 实例配置这一类抽屉或"模组"抽屉，
 * 然后点侧栏里另一个主页面，抽屉仍然盖在新页面上，必须手动关一次。
 * 成因不是画错了，而是 `destination` 记在外壳上、比页面活得久：
 * 换页时没有任何东西会清掉它，而它在页面区的 `Box` 里是第二个子节点，于是继续画。
 *
 * 因此这一层必须能被"换页"这一个动作作废，而不能靠把新页面盖在它上面——
 * 世代就是这个作废信号，下面四件事必须钉死：
 *
 *  1. 世代只在**真的**换页时前进，重选当前页不动它（否则点一下当前项就会把抽屉吃掉）；
 *  2. `goBack()` 真的退回首页时前进，停在首页返回 false 时不动它；
 *  3. 世代只增不减，所以"旧世代上的那一块"永远不会再变有效；
 *  4. [forward] 的语义不受影响：仍然是按页号判定方向，两个方向都要成立。
 */
class OxideNavTest {

    // ---- 世代只跟着真的换页前进 --------------------------------------------

    /** 换一次页，世代就 +1：新页面上不应该还有旧页面的东西 */
    @Test
    fun everyRealPageChangeMovesTheEpoch() {
        val nav = OxideNavState(OxidePage.Home)
        assertEquals(0, nav.epoch)

        nav.go(OxidePage.Instances)
        assertEquals(OxidePage.Instances, nav.page)
        assertEquals(1, nav.epoch)

        nav.go(OxidePage.Discover)
        assertEquals(OxidePage.Discover, nav.page)
        assertEquals(2, nav.epoch)

        nav.go(OxidePage.Settings)
        assertEquals(OxidePage.Settings, nav.page)
        assertEquals(3, nav.epoch)
    }

    /**
     * 重选当前页仍然是空操作
     *
     * 这一条是"重选会误伤"的那一半：重复点导航项不应该重新跑页面进场动画，
     * 也不应该把刚打开的抽屉吃掉——`go` 本来就在 `target == page` 时直接返回。
     */
    @Test
    fun reselectingTheCurrentPageIsStillANoOp() {
        val nav = OxideNavState(OxidePage.Home)
        val epoch = nav.epoch

        nav.go(OxidePage.Home)
        nav.go(OxidePage.Home)

        assertEquals(OxidePage.Home, nav.page)
        assertEquals(epoch, nav.epoch)
    }

    /** 停在首页时返回交还给上层退出，世代不能因此前进 */
    @Test
    fun goingBackAtHomeDoesNothing() {
        val nav = OxideNavState(OxidePage.Home)
        val epoch = nav.epoch

        assertFalse(nav.goBack())

        assertEquals(OxidePage.Home, nav.page)
        assertEquals(epoch, nav.epoch)
    }

    /** 返回键真的退回首页时也是一次换页，因此世代照样 +1 */
    @Test
    fun goingBackMovesTheEpochWhenItNavigates() {
        val nav = OxideNavState(OxidePage.Settings)
        val epoch = nav.epoch

        assertTrue(nav.goBack())

        assertEquals(OxidePage.Home, nav.page)
        assertEquals(epoch + 1, nav.epoch)
    }

    // ---- 临时表面只在本世代内有效 ------------------------------------------

    /**
     * 打开那一代上打开的东西，改一次页就作废
     *
     * 这一条就是缺陷本身：`destination` 那一类临时表面记在外壳上，它自己不会被清掉，
     * 所以读取的那一侧必须靠世代把它判成没打开。返回 null 而不是"留着不动"，
     * 因为留着的后果就是盖在新页面上。
     */
    @Test
    fun aSurfaceFromAnOlderEpochIsGone() {
        val nav = OxideNavState(OxidePage.Home)
        val drawer = OxideDestination.Multiplayer

        // 抽屉在世代 0 上打开：换页之前它一直有效
        assertSame(drawer, oxideTransientSurface(drawer, openedAt = nav.epoch, current = nav.epoch))

        nav.go(OxidePage.Discover)

        assertNull(oxideTransientSurface(drawer, openedAt = 0, current = nav.epoch))
    }

    /** 当前世代上的那一块原样放行，否则抽屉根本画不出来 */
    @Test
    fun aSurfaceOnTheCurrentEpochIsKept() {
        val nav = OxideNavState(OxidePage.Home)
        val destination = OxideDestination.Files("/storage/emulated/0/Download")

        assertSame(
            destination,
            oxideTransientSurface(destination, openedAt = nav.epoch, current = nav.epoch),
        )
    }

    /**
     * 世代只增不减：作废是单向的
     *
     * 这一点是"作废"与"重新变有效"的区别。如果世代会回来（比如按页号而不是按计数），
     * 那么切走再切回来时旧抽屉会自己复活，那比盖住新页面更难解释。
     */
    @Test
    fun anInvalidatedSurfaceNeverComesBack() {
        val nav = OxideNavState(OxidePage.Home)
        val drawer = OxideDestination.Account
        val openedAt = nav.epoch

        nav.go(OxidePage.Instances)
        nav.go(OxidePage.Home)

        // 又回到了打开抽屉的那一页，但世代已经不是当时那一代，因此它不会自己复活
        assertEquals(OxidePage.Home, nav.page)
        assertEquals(openedAt + 2, nav.epoch)
        assertNull(oxideTransientSurface(drawer, openedAt = openedAt, current = nav.epoch))
    }

    /** 没打开就是没打开：null 与世代无关，任何一代都还是 null */
    @Test
    fun nothingOpenedIsNothingToInvalidate() {
        val nav = OxideNavState(OxidePage.Home)

        assertNull(oxideTransientSurface<OxideDestination>(null, openedAt = nav.epoch, current = nav.epoch))
        nav.go(OxidePage.Settings)
        assertNull(oxideTransientSurface<OxideDestination>(null, openedAt = 0, current = nav.epoch))
    }

    /** 抽屉的展开与否是 Boolean，判定规则与目的地完全一致：同一个世代门 */
    @Test
    fun theEpochGateAlsoCarriesTheTaskDrawer() {
        val nav = OxideNavState(OxidePage.Home)
        val expanded = true
        val tasksEpoch = nav.epoch

        assertTrue(oxideTransientSurface(expanded, openedAt = tasksEpoch, current = nav.epoch) == true)

        nav.go(OxidePage.Settings)

        assertNull(oxideTransientSurface(expanded, openedAt = tasksEpoch, current = nav.epoch))
    }

    // ---- forward 的语义没被动过 --------------------------------------------

    /** 页号变大是前进、变小是后退，两者都仍然成立 */
    @Test
    fun forwardStillFollowsPageOrderInBothDirections() {
        val nav = OxideNavState(OxidePage.Home)
        assertTrue(nav.forward)

        nav.go(OxidePage.Discover)
        assertTrue(nav.forward)

        nav.go(OxidePage.Instances)
        assertFalse(nav.forward)

        nav.go(OxidePage.Settings)
        assertTrue(nav.forward)
    }

    /** 返回键永远是后退方向，不管刚刚是从哪一页退回来的 */
    @Test
    fun goingBackIsAlwaysBackward() {
        val nav = OxideNavState(OxidePage.Discover)

        nav.go(OxidePage.Settings)
        assertTrue(nav.forward)

        assertTrue(nav.goBack())
        assertEquals(OxidePage.Home, nav.page)
        assertFalse(nav.forward)
    }
}