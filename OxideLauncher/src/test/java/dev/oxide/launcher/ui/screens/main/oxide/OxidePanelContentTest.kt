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

import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.multiplayer.ServerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账号 / 联机 / 存储三块表面上的内容判据
 *
 * 这些判据的失败方式都是同一个：判据错了就会出现"点得动、却没有任何效果"的控件，
 * 或者反过来把还能用的控件藏起来。所以每一条都是纯函数，逐个状态都能钉死。
 */
class OxidePanelContentTest {

    // ---- 账号 -------------------------------------------------------------

    /** 一个账号要说单数，其余说复数 */
    @Test
    fun accountCountUsesTheSingularFormForExactlyOne() {
        assertEquals(R.string.oxide_sec_accounts_subtitle_one, oxideAccountCountLabelRes(1))
        assertEquals(R.string.oxide_sec_accounts_subtitle_other, oxideAccountCountLabelRes(0))
        assertEquals(R.string.oxide_sec_accounts_subtitle_other, oxideAccountCountLabelRes(2))
        assertEquals(R.string.oxide_sec_accounts_subtitle_other, oxideAccountCountLabelRes(17))
    }

    /**
     * 刷新凭据这一格什么时候能按
     *
     * 没有当前账号时按了没有任何对象，离线账号也没有服务端凭据可刷新。
     * 两种情况都是**禁用**而不是消失：底部那一行的宽度不能随着账号类型来回跳。
     */
    @Test
    fun refreshCredentialsNeedAnOnlineCapableAccount() {
        assertFalse(oxideAccountRefreshEnabled(hasAccount = false, isLocalAccount = false))
        assertFalse(oxideAccountRefreshEnabled(hasAccount = true, isLocalAccount = true))
        assertTrue(oxideAccountRefreshEnabled(hasAccount = true, isLocalAccount = false))
    }

    // ---- 联机 -------------------------------------------------------------

    /** Ping 进行中与 Ping 失败是两个不同的状态，不能都显示成"未知" */
    @Test
    fun serverOperationMapsToItsOwnStatus() {
        assertEquals(
            OxideServerStatus.Checking,
            oxideServerStatusOf(ServerData.Operation.Loading),
        )
        assertEquals(
            OxideServerStatus.Unreachable,
            oxideServerStatusOf(ServerData.Operation.Failed),
        )
    }

    /** 四个状态各有各的文案，状态因此不只靠颜色说话 */
    @Test
    fun everyServerStatusHasItsOwnLabel() {
        val labels = OxideServerStatus.entries.map { oxideServerStatusLabelRes(it) }
        assertEquals(OxideServerStatus.entries.size, labels.distinct().size)
        assertTrue(labels.all { it != 0 })
    }

    /** 在线与连不上语气不同，其余中性；颜色只是第二重信息 */
    @Test
    fun serverStatusToneSeparatesOnlineFromUnreachable() {
        assertEquals(OxideBadgeTone.Active, oxideServerStatusTone(OxideServerStatus.Online))
        assertEquals(OxideBadgeTone.Warn, oxideServerStatusTone(OxideServerStatus.Unreachable))
        assertEquals(OxideBadgeTone.Neutral, oxideServerStatusTone(OxideServerStatus.Checking))
        assertEquals(OxideBadgeTone.Neutral, oxideServerStatusTone(OxideServerStatus.Unpinged))
    }

    /**
     * 在线人数那一行只用协议里真的有的值
     *
     * `online < 0` 是原版"服务器没有定义人数"的写法，`max <= 0` 同理：
     * 这两种都不能显示成 "-1/20" 或者 "3/0"。
     */
    @Test
    fun playerSummaryNeverShowsNegativeOrZeroSlots() {
        assertEquals("3/20", oxideServerPlayerSummary(online = 3, max = 20))
        assertEquals("0/20", oxideServerPlayerSummary(online = 0, max = 20))
        assertEquals("", oxideServerPlayerSummary(online = -1, max = 20))
        assertEquals("7", oxideServerPlayerSummary(online = 7, max = 0))
        assertEquals("", oxideServerPlayerSummary(online = -1, max = -1))
    }

    // ---- 存储 -------------------------------------------------------------

    /**
     * 默认那一个游戏目录不能改名也不能删
     *
     * 它不是数据库里的一项，删掉它等于让"默认游戏目录"这一档从此不存在；
     * 其余各项都要能改名与删除，否则旧界面上那两件事在 Oxide 里就没了。
     */
    @Test
    fun theDefaultGameFolderIsNotEditable() {
        assertFalse(oxideStoragePathEditable("default"))
        assertTrue(oxideStoragePathEditable("6f1a2b3c-0000-4000-8000-000000000001"))
        assertTrue(oxideStoragePathEditable(""))
    }

    /**
     * 「打开游戏目录」这一行只在真的有一个目录时出现
     *
     * 目录还没配出来时那一档是空路径，留着一行点下去只会让文件页报"打不开"。
     */
    @Test
    fun theGameFolderActionNeedsAPath() {
        assertFalse(oxideStorageFolderActionVisible(""))
        assertFalse(oxideStorageFolderActionVisible("   "))
        assertTrue(oxideStorageFolderActionVisible("/storage/emulated/0/.minecraft"))
    }
}