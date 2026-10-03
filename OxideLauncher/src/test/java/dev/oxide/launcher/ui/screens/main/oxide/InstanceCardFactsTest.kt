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
 * 卡片上只显示真实读到的量
 *
 * 这些值全部来自磁盘或 MMKV，读不到的时候那一格必须整个不出现。
 * 编一个占位值（比如把没跑过的实例显示成"0 分钟前"）比少一行糟糕得多。
 */
class InstanceCardStatsTest {

    @Test
    fun bothStatsShowWhenBothAreReal() {
        assertEquals(
            listOf(InstanceStatKind.LastPlayed, InstanceStatKind.Memory),
            instanceCardStats(lastRunAtMillis = 1_700_000_000_000L, ramMb = 4096),
        )
    }

    @Test
    fun neverPlayedOmitsTheLastPlayedBlock() {
        // 没有日志时 lastModified() 是 0，绝不能显示成"0 分钟前"
        val stats = instanceCardStats(lastRunAtMillis = 0L, ramMb = 4096)
        assertFalse(InstanceStatKind.LastPlayed in stats)
        assertEquals(listOf(InstanceStatKind.Memory), stats)
    }

    @Test
    fun negativeTimestampIsAlsoTreatedAsUnknown() {
        val stats = instanceCardStats(lastRunAtMillis = -1L, ramMb = 4096)
        assertFalse(InstanceStatKind.LastPlayed in stats)
    }

    @Test
    fun zeroMemoryIsTreatedAsFollowGlobal() {
        // 版本配置没写过时 ramAllocation 读出来是 0，意思是"跟随全局"，
        // 不是"分到了 0 MB"，所以这一格也不能画
        val stats = instanceCardStats(lastRunAtMillis = 1_700_000_000_000L, ramMb = 0)
        assertFalse(InstanceStatKind.Memory in stats)
        assertEquals(listOf(InstanceStatKind.LastPlayed), stats)
    }

    @Test
    fun nothingIsInventedWhenNothingIsKnown() {
        assertEquals(emptyList<InstanceStatKind>(), instanceCardStats(lastRunAtMillis = 0L, ramMb = -1))
    }

    @Test
    fun orderIsStableSoTheRowNeverJumps() {
        // 参考稿里 Last played 在左、Memory 在右；顺序不能随数据变化
        assertEquals(
            listOf(InstanceStatKind.LastPlayed, InstanceStatKind.Memory),
            instanceCardStats(lastRunAtMillis = 1L, ramMb = 512),
        )
    }
}

/**
 * 卡片徽章
 *
 * 参考稿的徽章行最多两个，因此它的行高对所有卡片都一样；
 * 但"哪些是真的"不能因为空间不够就乱改，无效标记必须永远排在最前面。
 */
class InstanceCardBadgesTest {

    private fun facts(
        valid: Boolean = true,
        isolated: Boolean = false,
        pinned: Boolean = false,
        modsCount: Int = 0,
    ) = InstanceBadgeFacts(valid, isolated, pinned, modsCount)

    @Test
    fun invalidAlwaysComesFirst() {
        val badges = instanceCardBadges(facts(valid = false, modsCount = 12, pinned = true))
        assertEquals(InstanceBadgeKind.Invalid, badges.first())
    }

    @Test
    fun modsAppearOnlyWhenThereAreMods() {
        assertFalse(InstanceBadgeKind.Mods in instanceCardBadges(facts(modsCount = 0)))
        assertTrue(InstanceBadgeKind.Mods in instanceCardBadges(facts(modsCount = 12)))
    }

    @Test
    fun pinnedAppearsOnlyWhenPinned() {
        assertFalse(InstanceBadgeKind.Pinned in instanceCardBadges(facts(pinned = false)))
        assertTrue(InstanceBadgeKind.Pinned in instanceCardBadges(facts(pinned = true)))
    }

    @Test
    fun isolationIsReportedAsItsOwnKind() {
        // 隔离与共享是两种真实状态，都要能说出来
        assertTrue(InstanceBadgeKind.Isolated in instanceCardBadges(facts(isolated = true)))
        assertTrue(InstanceBadgeKind.Shared in instanceCardBadges(facts(isolated = false)))
        assertFalse(
            InstanceBadgeKind.Isolated in instanceCardBadges(facts(isolated = false))
        )
    }

    @Test
    fun isolationAndSharedAreNeverBothPresent() {
        for (isolated in listOf(true, false)) {
            val badges = instanceCardBadges(facts(isolated = isolated))
            assertFalse(
                "isolation state must be reported once",
                InstanceBadgeKind.Isolated in badges && InstanceBadgeKind.Shared in badges,
            )
        }
    }

    @Test
    fun orderingIsInvalidModsPinnedIsolation() {
        val badges = instanceCardBadges(
            facts(valid = false, isolated = true, pinned = true, modsCount = 3)
        )
        assertEquals(
            listOf(
                InstanceBadgeKind.Invalid,
                InstanceBadgeKind.Mods,
                InstanceBadgeKind.Pinned,
                InstanceBadgeKind.Isolated,
            ),
            badges,
        )
    }

    @Test
    fun truncationNeverDropsTheInvalidBadge() {
        // 徽章行最多两个，被截掉的必须是排在最后的那个
        val badges = instanceCardBadges(
            facts(valid = false, isolated = true, pinned = true, modsCount = 3)
        ).take(instanceSelectionPresentation(selected = true).badgeSlots)
        assertTrue(InstanceBadgeKind.Invalid in badges)
    }
}
