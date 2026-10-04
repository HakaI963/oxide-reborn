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

import dev.oxide.launcher.game.download.assets.favorites.FavoriteEntry
import dev.oxide.launcher.game.download.assets.favorites.FavoriteProject
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏列表的形状
 *
 * 关键的两条：同一个项目在搜索结果与收藏里必须是**同一枚星**（键一致），
 * 以及平台已经下架的那一项**不能消失**——用户会以为收藏丢了。
 */
class OxideFavoritesTest {

    private fun entry(
        platform: Platform,
        id: String,
        title: String,
        classes: PlatformClasses = PlatformClasses.MOD,
        authors: List<String> = emptyList(),
        invalid: Boolean = false,
    ) = FavoriteEntry(
        platform = platform,
        project = FavoriteProject(
            projectId = id,
            title = title,
            description = "about $title",
            authors = authors,
            classes = classes,
            followTime = 0L,
        ),
        invalid = invalid,
    )

    private fun rows(entries: List<FavoriteEntry>): List<OxideFavoriteRow> = oxideFavoriteRows(
        entries = entries,
        keyOf = { platform, projectId -> "$platform/$projectId" },
        platformNameOf = { platform -> platform.name },
    )

    @Test
    fun theKeyIsTheSameIdentityTheSearchResultsUse() {
        val built = rows(
            listOf(entry(Platform.CURSEFORGE, "abc", "Sodium"))
        )
        assertEquals("CURSEFORGE/abc", built.single().key)
        assertEquals("CURSEFORGE/abc", discoverProjectKey(Platform.CURSEFORGE, "abc"))
    }

    @Test
    fun theSameProjectNeverAppearsTwice() {
        val built = rows(
            listOf(
                entry(Platform.CURSEFORGE, "abc", "Sodium"),
                entry(Platform.MODRINTH, "abc", "Sodium"),
            )
        )
        assertEquals(2, built.size)
        assertEquals(2, built.map { row -> row.key }.distinct().size)
    }

    @Test
    fun sortingIgnoresCase() {
        val built = rows(
            listOf(
                entry(Platform.CURSEFORGE, "b", "beta"),
                entry(Platform.CURSEFORGE, "a", "Alpha"),
            )
        )
        assertEquals(listOf("Alpha", "beta"), built.map { row -> row.title })
    }

    @Test
    fun aProjectThePlatformDroppedStaysListedAtTheEnd() {
        val built = rows(
            listOf(
                entry(Platform.CURSEFORGE, "a", "alpha", invalid = true),
                entry(Platform.CURSEFORGE, "b", "beta"),
            )
        )
        assertEquals(listOf("beta", "alpha"), built.map { row -> row.title })
        assertFalse(built.first().invalid)
        assertTrue(built.last().invalid)
    }

    @Test
    fun emptyAuthorsBecomeNoAuthorLineRatherThanAnEmptyOne() {
        val built = rows(listOf(entry(Platform.CURSEFORGE, "a", "alpha")))
        assertEquals(null, built.single().author)
    }

    @Test
    fun severalAuthorsAreJoinedIntoOneLine() {
        val built = rows(
            listOf(entry(Platform.CURSEFORGE, "a", "alpha", authors = listOf("ann", "bob")))
        )
        assertEquals("ann, bob", built.single().author)
    }

    @Test
    fun searchMatchesTitleAuthorAndDescription() {
        val row = rows(
            listOf(
                entry(Platform.CURSEFORGE, "a", "Alpha", authors = listOf("ann"))
            )
        ).single()
        assertTrue(oxideFavoriteMatches(row, "alph"))
        assertTrue(oxideFavoriteMatches(row, "ANN"))
        assertTrue(oxideFavoriteMatches(row, "about"))
        assertFalse(oxideFavoriteMatches(row, "zzz"))
    }

    @Test
    fun aBlankSearchKeepsEverything() {
        val row = rows(listOf(entry(Platform.CURSEFORGE, "a", "alpha"))).single()
        assertTrue(oxideFavoriteMatches(row, ""))
        assertTrue(oxideFavoriteMatches(row, "   "))
    }

    @Test
    fun onlyTheWholePackageClassesInstallDirectly() {
        // 模组与整合包要走确认层，而确认层需要搜索结果里的那份 PlatformSearchData；
        // 收藏只存了缓存的元数据，因此这两类不给安装按钮
        assertFalse(oxideFavoriteInstallable(PlatformClasses.MOD))
        assertFalse(oxideFavoriteInstallable(PlatformClasses.MOD_PACK))
        assertTrue(oxideFavoriteInstallable(PlatformClasses.RESOURCE_PACK))
        assertTrue(oxideFavoriteInstallable(PlatformClasses.SHADERS))
        assertTrue(oxideFavoriteInstallable(PlatformClasses.SAVES))
    }
}
