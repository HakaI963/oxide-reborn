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
 * 实例列表的名称搜索与排序
 *
 * 这一层刻意与 [dev.oxide.launcher.game.version.installed.Version] 无关：排序与搜索
 * 规则因此可以在没有磁盘、没有 Hilt、没有协程的单测里被逐条钉住。
 */
class OxideInstanceQueryTest {

    private data class Row(val name: String, val lastRun: Long)

    private val installed = listOf(
        Row("1.21.10", lastRun = 300L),
        Row("1.20.1-shaders", lastRun = 100L),
        Row("Skyblock 2", lastRun = 200L),
    )

    private fun search(
        query: String,
        sort: OxideInstanceSort = OxideInstanceSort.Name,
        ascending: Boolean = true,
    ): List<String> = filterOxideInstances(
        items = installed,
        query = query,
        sort = sort,
        ascending = ascending,
        nameOf = { row -> row.name },
        lastRunOf = { row -> row.lastRun },
    ).map { row -> row.name }

    @Test
    fun aBlankQueryNeverHidesAnything() {
        assertEquals(3, search("").size)
        assertEquals(3, search("   ").size)
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesSubstrings() {
        assertEquals(listOf("1.21.10"), search("21.1"))
        assertEquals(listOf("Skyblock 2"), search("skyblock"))
    }

    @Test
    fun aQueryThatMatchesNothingYieldsAnEmptyListRatherThanEverything() {
        // 最容易出的错：搜索无结果时退回完整列表，于是用户以为筛选没生效
        assertTrue(search("nonexistent").isEmpty())
    }

    @Test
    fun nameSortIgnoresCase() {
        assertEquals(
            listOf("1.20.1-shaders", "1.21.10", "Skyblock 2"),
            search("", ascending = true),
        )
    }

    @Test
    fun descendingIsAlwaysTheReverseOfAscending() {
        val ascending = search("", ascending = true)
        val descending = search("", ascending = false)
        assertEquals(ascending.reversed(), descending)
    }

    @Test
    fun recentActivitySortPutsTheNewestRunFirst() {
        assertEquals(
            listOf("1.21.10", "Skyblock 2", "1.20.1-shaders"),
            search("", sort = OxideInstanceSort.RecentActivity, ascending = false),
        )
    }

    @Test
    fun anInstanceThatHasNeverRunSortsAsTheOldest() {
        val rows = listOf(Row("fresh", lastRun = 0L), Row("played", lastRun = 5L))
        val sorted = filterOxideInstances(
            items = rows,
            query = "",
            sort = OxideInstanceSort.RecentActivity,
            ascending = false,
            nameOf = { row -> row.name },
            lastRunOf = { row -> row.lastRun },
        ).map { row -> row.name }
        assertEquals(listOf("played", "fresh"), sorted)
    }

    @Test
    fun filteringHappensBeforeSortingSoBothCanBeActiveAtOnce() {
        assertEquals(
            listOf("1.21.10", "1.20.1-shaders"),
            search("1.2", sort = OxideInstanceSort.RecentActivity, ascending = false),
        )
    }

    @Test
    fun theSortCycleWalksEveryKeyAndBothDirections() {
        var sort = OxideInstanceSort.Name
        var ascending = true
        val seen = mutableListOf<Pair<OxideInstanceSort, Boolean>>()
        repeat(OxideInstanceSort.entries.size * 2) {
            seen += sort to ascending
            val next = nextOxideInstanceSort(sort, ascending)
            sort = next.first
            ascending = next.second
        }
        // 两张多少尝试：“每一档 + 每一个方向”都只出现一次
        assertEquals(
            OxideInstanceSort.entries.size * 2,
            seen.distinct().size,
        )
    }

    @Test
    fun noInstancesMeansNoSearchBoxAtAll() {
        // 一个实例都没有时，那一枚输入框与排序按钮改不了任何东西
        assertFalse(oxideInstanceQueryVisible(0))
        assertTrue(oxideInstanceQueryVisible(1))
    }
}
