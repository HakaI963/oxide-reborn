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

import dev.oxide.launcher.game.version.installed.VersionFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容分类的映射
 *
 * 分类不只是界面上的五个名字：它同时决定了读哪一个真实目录、导入时收哪个扩展名，
 * 以及这一类到底能做哪些操作。这三件事错一条，用户点到的就是别人的目录或者一个
 * 点了没反应的按钮，因此逐条钉死。
 */
class OxideContentCategoryTest {

    @Test
    fun everyCategoryPointsAtItsOwnFolder() {
        assertEquals(VersionFolders.MOD, OxideContentCategory.Mods.folder)
        assertEquals(
            VersionFolders.RESOURCE_PACK,
            OxideContentCategory.ResourcePacks.folder,
        )
        assertEquals(VersionFolders.SHADERS, OxideContentCategory.Shaders.folder)
        assertEquals(VersionFolders.SAVES, OxideContentCategory.Saves.folder)
        assertEquals(VersionFolders.SCREENSHOTS, OxideContentCategory.Screenshots.folder)
    }

    @Test
    fun folderNamesMatchTheRealGameDirectories() {
        // 名字必须与游戏自己的目录一致，否则读到的会是空目录
        assertEquals("mods", OxideContentCategory.Mods.folder.folderName)
        assertEquals("resourcepacks", OxideContentCategory.ResourcePacks.folder.folderName)
        assertEquals("shaderpacks", OxideContentCategory.Shaders.folder.folderName)
        assertEquals("saves", OxideContentCategory.Saves.folder.folderName)
        assertEquals("screenshots", OxideContentCategory.Screenshots.folder.folderName)
    }

    @Test
    fun foldersAreNotSharedBetweenCategories() {
        val folders = OxideContentCategory.entries.map { it.folder }.toSet()
        assertEquals(OxideContentCategory.entries.size, folders.size)
    }

    @Test
    fun importExtensionsMatchTheRealPackaging() {
        assertEquals("jar", OxideContentCategory.Mods.acceptExtension)
        assertEquals("zip", OxideContentCategory.ResourcePacks.acceptExtension)
        assertEquals("zip", OxideContentCategory.Shaders.acceptExtension)
        assertEquals("png", OxideContentCategory.Screenshots.acceptExtension)
        // 存档是文件夹，没有可接受的扩展名
        assertTrue(OxideContentCategory.Saves.acceptExtension.isEmpty())
        assertTrue(OxideContentCategory.Saves.isDirectory)
        assertFalse(OxideContentCategory.Mods.isDirectory)
    }

    @Test
    fun onlyModsCanBeEnabledOrUpdated() {
        assertTrue(OxideContentCategory.Mods.canEnable)
        assertTrue(OxideContentCategory.Mods.canUpdate)
        OxideContentCategory.entries.filter { it != OxideContentCategory.Mods }.forEach { category ->
            assertFalse("$category must not offer enable", category.canEnable)
            assertFalse("$category must not offer update", category.canUpdate)
        }
    }

    @Test
    fun onlyPacksAndSavesCanBeRenamed() {
        assertTrue(OxideContentCategory.ResourcePacks.canRename)
        assertTrue(OxideContentCategory.Shaders.canRename)
        assertTrue(OxideContentCategory.Saves.canRename)
        assertFalse(OxideContentCategory.Mods.canRename)
        assertFalse(OxideContentCategory.Screenshots.canRename)
    }

    @Test
    fun onlySavesCanQuickPlayAndOnlyTheyHaveValidityToFilter() {
        assertTrue(OxideContentCategory.Saves.canQuickPlay)
        OxideContentCategory.entries.filter { it != OxideContentCategory.Saves }.forEach { category ->
            assertFalse("$category must not offer quick play", category.canQuickPlay)
        }
        assertTrue(OxideContentCategory.ResourcePacks.hasValidity)
        assertTrue(OxideContentCategory.Saves.hasValidity)
        assertFalse(OxideContentCategory.Mods.hasValidity)
        assertFalse(OxideContentCategory.Shaders.hasValidity)
        assertFalse(OxideContentCategory.Screenshots.hasValidity)
    }

    @Test
    fun theRailOrderIsTheDrawerOrder() {
        assertEquals(
            listOf(
                OxideContentCategory.Mods,
                OxideContentCategory.ResourcePacks,
                OxideContentCategory.Shaders,
                OxideContentCategory.Saves,
                OxideContentCategory.Screenshots,
            ),
            OxideContentCategory.defaultOrder,
        )
    }

    @Test
    fun everyTabButTheTwoNonContentOnesMapsToACategory() {
        OxideInstanceTab.entries.forEach { tab ->
            val mapped = oxideContentCategoryForTab(tab)
            if (tab == OxideInstanceTab.Overview || tab == OxideInstanceTab.Config) {
                assertNull("$tab is not a content page", mapped)
            } else {
                assertEquals(tab.contentCategory, mapped)
                assertTrue("$tab must map to a category", mapped != null)
            }
        }
    }

    @Test
    fun contentTabsAreAllReachableExactlyOnce() {
        val mapped = OxideInstanceTab.entries.mapNotNull { oxideContentCategoryForTab(it) }
        assertEquals(OxideContentCategory.entries.toSet(), mapped.toSet())
        assertEquals(OxideContentCategory.entries.size, mapped.size)
    }

    @Test
    fun everyCategoryResolvesBackToItsOwnTab() {
        // 概览里的计数行点下去必须落在对应的那一栏，不能全落到第一栏
        OxideContentCategory.entries.forEach { category ->
            val tab = oxideInstanceTabForCategory(category)
            assertEquals(category, tab?.contentCategory)
            assertEquals(category, oxideContentCategoryForTab(tab!!))
        }
    }

    @Test
    fun theNonContentTabsHaveNoCategory() {
        assertNull(oxideContentCategoryForTab(OxideInstanceTab.Overview))
        assertNull(oxideContentCategoryForTab(OxideInstanceTab.Config))
    }
}