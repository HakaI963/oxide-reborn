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

package dev.oxide.launcher.game.download.assets.platform.curseforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分页参数收敛
 *
 * 服务端对 index / pageSize 做硬校验，越界直接 400：
 *   PageSize must be between 1 and 50.
 *   Index must be between 0 and 10000. / Index + PageSize cannot exceed 10000
 * 这些约束只存在于服务端校验器里，客户端必须自己保证绝不越界。
 */
class CurseForgePagingTest {

    @Test
    fun `page size is clamped into the range the server accepts`() {
        assertEquals(1, CurseForgePaging.pageSize(0))
        assertEquals(1, CurseForgePaging.pageSize(-7))
        assertEquals(20, CurseForgePaging.pageSize(20))
        assertEquals(50, CurseForgePaging.pageSize(50))
        // 服务端上限是 50（不是 100），越界即 400
        assertEquals(50, CurseForgePaging.pageSize(51))
        assertEquals(50, CurseForgePaging.pageSize(100))
        assertEquals(50, CurseForgePaging.pageSize(Int.MAX_VALUE))
    }

    @Test
    fun `index plus page size never crosses the server ceiling`() {
        assertEquals(0, CurseForgePaging.index(0, 20))
        assertEquals(20, CurseForgePaging.index(20, 20))
        assertEquals(9980, CurseForgePaging.index(9980, 20))
        assertEquals(9950, CurseForgePaging.index(9999, 50))
        assertEquals(50, CurseForgePaging.index(50, 50))
    }

    @Test
    fun `a negative index is pulled back to zero`() {
        assertEquals(0, CurseForgePaging.index(-1, 20))
        assertEquals(0, CurseForgePaging.index(-100000, 20))
    }

    @Test
    fun `index stays legal even when page size is out of range`() {
        // pageSize 先被收敛，再拿去算 index 上限，两步都要生效
        assertTrue(CurseForgePaging.index(Int.MAX_VALUE, 0) >= 0)
        assertTrue(CurseForgePaging.index(Int.MAX_VALUE, 100) >= 0)
        assertTrue(
            CurseForgePaging.index(CurseForgePaging.MAX_INDEX, 20) +
                CurseForgePaging.pageSize(20) <= CurseForgePaging.MAX_INDEX
        )
    }

    @Test
    fun `max version page index never produces an out of range index`() {
        val maxPage = maxVersionPageIndex(50)
        val highestIndex = maxPage * 50
        assertTrue(
            "highest requested index $highestIndex must stay under ${CurseForgePaging.MAX_INDEX}",
            highestIndex < CurseForgePaging.MAX_INDEX
        )
        assertEquals(199, maxPage)
    }
}
