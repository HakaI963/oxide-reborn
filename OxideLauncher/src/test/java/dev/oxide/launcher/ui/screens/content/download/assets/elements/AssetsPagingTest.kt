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

package dev.oxide.launcher.ui.screens.content.download.assets.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页码运算
 *
 * index 在两个平台上都是"跳过多少条"，所以下一页是 index 加上一页大小，
 * 而不是页码加一——把这两者弄混会让翻页永远停在同一页，
 * 或者在第一页就请求到服务端不接受的下标。
 */
class AssetsPagingTest {

    @Test
    fun `next index advances by one page`() {
        assertEquals(20, AssetsPaging.nextIndex(0, 20))
        assertEquals(40, AssetsPaging.nextIndex(20, 20))
        assertEquals(60, AssetsPaging.nextIndex(40, 20))
        assertEquals(50, AssetsPaging.nextIndex(0, 50))
        assertEquals(20, AssetsPaging.nextIndex(0, 20))
    }

    @Test
    fun `previous index goes back one page and stops at zero`() {
        assertEquals(20, AssetsPaging.previousIndex(40, 20))
        assertEquals(0, AssetsPaging.previousIndex(20, 20))
        assertEquals(0, AssetsPaging.previousIndex(10, 20))
        assertEquals(0, AssetsPaging.previousIndex(0, 20))
    }

    @Test
    fun `jumping to a page uses its own start offset`() {
        assertEquals(0, AssetsPaging.indexOfPage(1, 20))
        assertEquals(20, AssetsPaging.indexOfPage(2, 20))
        assertEquals(500, AssetsPaging.indexOfPage(26, 20))
        assertEquals(40, AssetsPaging.indexOfPage(3, 20))
    }

    @Test
    fun `walking pages forward and back returns to the same index`() {
        val start = 60
        val forward = AssetsPaging.nextIndex(AssetsPaging.nextIndex(start, 20), 20)
        assertEquals(100, forward)
        assertEquals(start, AssetsPaging.previousIndex(AssetsPaging.previousIndex(forward, 20), 20))
    }

    @Test
    fun `a page size of zero never happens`() {
        // 页大小为 0 会让页码运算退化成永远停在第 1 页
        assertEquals(AssetsPaging.FALLBACK_PAGE_SIZE, AssetsPaging.pageSize(0))
        assertEquals(AssetsPaging.FALLBACK_PAGE_SIZE, AssetsPaging.pageSize(-1))
        assertEquals(20, AssetsPaging.pageSize(20))
        assertEquals(50, AssetsPaging.pageSize(50))
    }

    @Test
    fun `page arithmetic survives a zero page size`() {
        assertTrue(AssetsPaging.nextIndex(0, 0) > 0)
        assertEquals(0, AssetsPaging.previousIndex(0, 0))
        assertEquals(0, AssetsPaging.indexOfPage(1, 0))
    }

    @Test
    fun `a short page means the last page`() {
        assertTrue(AssetsPaging.isLastPage(index = 0, pageSize = 20, resultCount = 19, totalCount = 10000))
        assertTrue(AssetsPaging.isLastPage(index = 0, pageSize = 20, resultCount = 0, totalCount = 0))
    }

    @Test
    fun `a full page is the last page only when the total is reached`() {
        assertFalse(AssetsPaging.isLastPage(index = 0, pageSize = 20, resultCount = 20, totalCount = 10000))
        assertFalse(AssetsPaging.isLastPage(index = 40, pageSize = 20, resultCount = 20, totalCount = 10000))
        // 9980 + 20 已经到达服务端封顶的 10000
        assertTrue(AssetsPaging.isLastPage(index = 9980, pageSize = 20, resultCount = 20, totalCount = 10000))
        assertTrue(AssetsPaging.isLastPage(index = 0, pageSize = 20, resultCount = 20, totalCount = 20))
    }

    @Test
    fun `a missing total count ends the paging`() {
        // 服务端把 totalCount 封顶在 10000；取不到总数时不能无限往下翻
        assertTrue(AssetsPaging.isLastPage(index = 0, pageSize = 20, resultCount = 20, totalCount = 0))
    }
}
