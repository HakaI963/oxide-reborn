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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 列表上的筛选、排序与改名
 *
 * 这一层是五类内容共用的：错一条就会在某一类上排序错、或者把改名改成删掉原文件。
 * 因此逐条钉死，包括"改名成自己的原名不算冲突"这一条。
 */
class OxideContentEntryLogicTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun entry(
        name: String,
        displayName: String = name,
        enabled: Boolean = true,
        selectable: Boolean = true,
        valid: Boolean = true,
        modifiedAt: Long = 0L,
        lastPlayed: Long? = null,
    ) = OxideContentEntry(
        key = name,
        fileName = name,
        displayName = displayName,
        detail = null,
        badge = null,
        enabled = enabled,
        selectable = selectable,
        valid = valid,
        modifiedAt = modifiedAt,
        lastPlayed = lastPlayed,
    )

    private fun names(entries: List<OxideContentEntry>) = entries.map { it.fileName }

    // ---- 搜索 ----------------------------------------------------------------

    @Test
    fun emptyQueryKeepsEverything() {
        val all = listOf(entry("a.jar"), entry("b.jar"))
        assertEquals(all, filterOxideContentEntries(all, "", OxideContentState.All))
    }

    @Test
    fun queryMatchesTheDisplayName() {
        val all = listOf(entry("fancy-1.2.jar", displayName = "Fancy Overhaul"))
        assertEquals(all, filterOxideContentEntries(all, "overhaul", OxideContentState.All))
    }

    @Test
    fun queryAlsoMatchesTheFileName() {
        // 显示名和文件名经常不是一回事，两个都得参与，否则用户搜不到自己认识的那个
        val all = listOf(entry("fancy-1.2.jar", displayName = "Fancy Overhaul"))
        assertEquals(all, filterOxideContentEntries(all, "fancy-1", OxideContentState.All))
    }

    @Test
    fun queryIsCaseInsensitive() {
        val all = listOf(entry("Sodium-0.5.jar"))
        assertEquals(all, filterOxideContentEntries(all, "sodium", OxideContentState.All))
    }

    @Test
    fun queryThatMatchesNothingReturnsNothing() {
        val all = listOf(entry("a.jar"), entry("b.jar"))
        assertTrue(filterOxideContentEntries(all, "zzz", OxideContentState.All).isEmpty())
    }

    // ---- 状态筛选 ------------------------------------------------------------

    @Test
    fun stateFilterSplitsEnabledAndDisabled() {
        val all = listOf(
            entry("on.jar", enabled = true),
            entry("off.jar", enabled = false),
        )
        assertEquals(
            listOf("on.jar"),
            names(filterOxideContentEntries(all, "", OxideContentState.Enabled)),
        )
        assertEquals(
            listOf("off.jar"),
            names(filterOxideContentEntries(all, "", OxideContentState.Disabled)),
        )
        assertEquals(2, filterOxideContentEntries(all, "", OxideContentState.All).size)
    }

    @Test
    fun stateAndNameFiltersBothApply() {
        val all = listOf(
            entry("alpha.jar", enabled = true),
            entry("beta.jar", enabled = false),
            entry("gamma.jar", enabled = true),
        )
        assertEquals(
            listOf("gamma.jar"),
            names(filterOxideContentEntries(all, "gam", OxideContentState.Enabled)),
        )
    }

    @Test
    fun countsAddUpToTheListSize() {
        val all = listOf(
            entry("a", enabled = true),
            entry("b", enabled = false),
            entry("c", enabled = false),
        )
        val counts = oxideContentStateCounts(all)
        assertEquals(3, counts.all)
        assertEquals(1, counts.enabled)
        assertEquals(2, counts.disabled)
        assertEquals(counts.all, counts.enabled + counts.disabled)
    }

    @Test
    fun countsOfAnEmptyListAreAllZero() {
        val counts = oxideContentStateCounts(emptyList())
        assertEquals(0, counts.all)
        assertEquals(0, counts.enabled)
        assertEquals(0, counts.disabled)
    }

    // ---- 排序 ----------------------------------------------------------------

    @Test
    fun nameSortIsCaseInsensitive() {
        val all = listOf(entry("b.jar"), entry("A.jar"), entry("c.jar"))
        assertEquals(
            listOf("A.jar", "b.jar", "c.jar"),
            names(sortOxideContentEntries(all, OxideContentSort.Name, true)),
        )
    }

    @Test
    fun descendingIsTheReverseOfAscending() {
        val all = listOf(
            entry("a.jar", modifiedAt = 10L),
            entry("b.jar", modifiedAt = 30L),
            entry("c.jar", modifiedAt = 20L),
        )
        val asc = names(sortOxideContentEntries(all, OxideContentSort.FileModified, true))
        val desc = names(sortOxideContentEntries(all, OxideContentSort.FileModified, false))
        assertEquals(asc.reversed(), desc)
        assertEquals(listOf("b.jar", "c.jar", "a.jar"), desc)
    }

    @Test
    fun lastPlayedFallsBackToTheModifiedTime() {
        // 存档没有 lastPlayed 时必须退回文件时间，否则它会被排到最后而不是正确位置。
        // 排序键是 `lastPlayed ?: modifiedAt`：old=5（真实游玩时间）、
        // mid=50、new=100（都没有 lastPlayed，只能用文件时间），
        // 升序就是最久没玩的在前 —— 与同一条链路上 FileModified 的升序语义一致。
        val all = listOf(
            entry("new.jar", modifiedAt = 100L),
            entry("old.jar", modifiedAt = 10L, lastPlayed = 5L),
            entry("mid.jar", modifiedAt = 50L),
        )
        assertEquals(
            listOf("old.jar", "mid.jar", "new.jar"),
            names(sortOxideContentEntries(all, OxideContentSort.LastPlayed, true)),
        )
        // 降序必须正好是升序的倒过来，最近玩过的排最前
        assertEquals(
            listOf("new.jar", "mid.jar", "old.jar"),
            names(sortOxideContentEntries(all, OxideContentSort.LastPlayed, false)),
        )
    }

    @Test
    fun aZeroLastPlayedIsTreatedAsAbsent() {
        val stamp = oxideContentLastPlayedStamp(entry("a.jar", modifiedAt = 42L, lastPlayed = 0L))
        assertEquals(42L, stamp)
    }

    @Test
    fun aRealLastPlayedWinsOverTheFileTime() {
        val stamp = oxideContentLastPlayedStamp(entry("a.jar", modifiedAt = 42L, lastPlayed = 7L))
        assertEquals(7L, stamp)
    }

    // ---- 排序键轮换 ----------------------------------------------------------

    @Test
    fun sortCyclingOnlyUsesKeysTheCategorySupports() {
        var state = oxideContentDefaultSort(OxideContentCategory.Saves)
        repeat(6) {
            val next = nextOxideContentSort(OxideContentCategory.Saves, state.first, state.second)
            assertTrue(next.first in oxideContentSortOptions(OxideContentCategory.Saves))
            state = next
        }
    }

    @Test
    fun sortCyclingReturnsToWhereItStarted() {
        var state = oxideContentDefaultSort(OxideContentCategory.Saves)
        val start = state
        val steps = oxideContentSortOptions(OxideContentCategory.Saves).size * 2
        repeat(steps) {
            state = nextOxideContentSort(OxideContentCategory.Saves, state.first, state.second)
        }
        assertEquals(start, state)
    }

    @Test
    fun sortCyclingNeverLandsOnADirectionTheCategoryCannotShow() {
        val options = oxideContentSortOptions(OxideContentCategory.Screenshots)
        var state = oxideContentDefaultSort(OxideContentCategory.Screenshots)
        repeat(8) {
            state = nextOxideContentSort(OxideContentCategory.Screenshots, state.first, state.second)
            assertTrue(state.first in options)
        }
    }

    @Test
    fun anUnknownKeyFallsBackToTheFirstAscendingSort() {
        // 存档只支持 Name / FileName / LastPlayed：拿 FileModified 来轮换才是真正的"未知键"
        assertFalse(
            "前提：FileModified 不是存档支持的排序键",
            OxideContentSort.FileModified in oxideContentSortOptions(OxideContentCategory.Saves),
        )
        val next = nextOxideContentSort(OxideContentCategory.Saves, OxideContentSort.FileModified, false)
        assertEquals(oxideContentSortOptions(OxideContentCategory.Saves).first(), next.first)
        assertTrue(next.second)
    }

    @Test
    fun screenshotsDefaultToNewestFirst() {
        // 旧界面里截图的默认顺序就是降序：用户想看的是最近拍的那几张
        val (sort, ascending) = oxideContentDefaultSort(OxideContentCategory.Screenshots)
        assertEquals(OxideContentSort.Name, sort)
        assertFalse(ascending)
    }

    @Test
    fun sortOptionsAreExactlyWhatEachCategoryNeeds() {
        assertEquals(
            listOf(OxideContentSort.Name, OxideContentSort.FileModified),
            oxideContentSortOptions(OxideContentCategory.Mods),
        )
        assertEquals(
            listOf(OxideContentSort.Name, OxideContentSort.FileModified),
            oxideContentSortOptions(OxideContentCategory.Shaders),
        )
        assertEquals(
            listOf(OxideContentSort.Name, OxideContentSort.FileModified),
            oxideContentSortOptions(OxideContentCategory.Screenshots),
        )
        assertEquals(
            listOf(
                OxideContentSort.Name,
                OxideContentSort.FileName,
                OxideContentSort.LastPlayed,
            ),
            oxideContentSortOptions(OxideContentCategory.Saves),
        )
    }

    @Test
    fun sortLabelsCarryBothTheKeyAndTheDirection() {
        assertTrue(sortLabelKey(OxideContentSort.Name, true).contains("asc"))
        assertTrue(sortLabelKey(OxideContentSort.Name, false).contains("desc"))
        assertTrue(sortLabelKey(OxideContentSort.Name, true).contains("Name"))
    }

    // ---- 选中项 --------------------------------------------------------------

    @Test
    fun deleteTargetsAreFileNamesNotDisplayNames() {
        // 删除是不可逆的，确认条上写的必须是真实目录末段
        val targets = oxideContentDeleteTargets(
            listOf(entry("New World", displayName = "§aMy §bWorld"))
        )
        assertEquals(listOf("New World"), targets)
    }

    @Test
    fun deleteTargetsAreDeduplicated() {
        val targets = oxideContentDeleteTargets(listOf(entry("a.jar"), entry("a.jar")))
        assertEquals(listOf("a.jar"), targets)
    }

    @Test
    fun nothingSelectedMeansNothingToDelete() {
        assertTrue(oxideContentDeleteTargets(emptyList()).isEmpty())
    }

    @Test
    fun onlyModsCanBeUpdated() {
        val selected = listOf(entry("a.jar"), entry("b.jar"))
        assertEquals(
            2,
            oxideContentUpdatableSelection(OxideContentCategory.Mods, selected).size,
        )
        OxideContentCategory.entries.filter { it != OxideContentCategory.Mods }.forEach { category ->
            assertTrue(
                oxideContentUpdatableSelection(category, selected).isEmpty()
            )
        }
    }

    @Test
    fun aModWithoutRemoteLookupIsNotUpdatable() {
        val selected = listOf(
            entry("local.jar", selectable = false),
            entry("remote.jar", selectable = true),
        )
        assertEquals(
            listOf("remote.jar"),
            names(
                oxideContentUpdatableSelection(OxideContentCategory.Mods, selected)
            ),
        )
    }

    // ---- 改名 ----------------------------------------------------------------

    @Test
    fun renamingStripsTheExtension() {
        assertEquals(
            "Complementary",
            oxideRenameInitial(
                OxideContentCategory.Shaders,
                entry("Complementary-Reimagined.zip"),
            ),
        )
    }

    @Test
    fun renamingWithoutAnExtensionKeepsTheWholeName() {
        assertEquals(
            "ShaderPack",
            oxideRenameInitial(OxideContentCategory.Shaders, entry("ShaderPack")),
        )
    }

    @Test
    fun savesKeepTheirFolderNameAsIs() {
        // 存档是文件夹：既不去扩展名，也不加回去
        assertEquals(
            "New World",
            oxideRenameInitial(OxideContentCategory.Saves, entry("New World")),
        )
        assertEquals("", oxideRenameExtension(OxideContentCategory.Saves, entry("New World")))
    }

    @Test
    fun packRenamesPutTheExtensionBack() {
        assertEquals(
            ".zip",
            oxideRenameExtension(
                OxideContentCategory.Shaders,
                entry("Complementary.zip"),
            ),
        )
        assertEquals(
            ".jar",
            oxideRenameExtension(OxideContentCategory.Mods, entry("sodium-fabric.jar")),
        )
    }

    @Test
    fun aDotfileHasNoExtensionToPutBack() {
        assertEquals("", oxideRenameExtension(OxideContentCategory.Shaders, entry(".hidden")))
    }

    @Test
    fun resourcePacksStartFromTheirDisplayName() {
        assertEquals(
            "Fresh Animations",
            oxideRenameInitial(
                OxideContentCategory.ResourcePacks,
                entry("fresh-animations.zip", displayName = "Fresh Animations"),
            ),
        )
    }

    @Test
    fun renamingOntoAnExistingDifferentNameConflicts() {
        val dir = temporaryFolder.newFolder("packs")
        File(dir, "b.zip").writeText("x")
        assertTrue(
            oxideRenameWouldConflict(
                dir = dir,
                extension = ".zip",
                currentName = "a.zip",
                newName = "b",
            )
        )
    }

    @Test
    fun renamingToItsOwnNameIsNotAConflict() {
        // 用户把名字原样再输一遍是合法操作，不该被"已存在"挡住
        val dir = temporaryFolder.newFolder("same")
        File(dir, "a.zip").writeText("x")
        assertFalse(
            oxideRenameWouldConflict(
                dir = dir,
                extension = ".zip",
                currentName = "a.zip",
                newName = "a",
            )
        )
    }

    @Test
    fun renamingToAFreeNameNeverConflicts() {
        val dir = temporaryFolder.newFolder("free")
        File(dir, "a.zip").writeText("x")
        assertFalse(
            oxideRenameWouldConflict(
                dir = dir,
                extension = ".zip",
                currentName = "a.zip",
                newName = "brand-new",
            )
        )
    }

    @Test
    fun aConflictIsCaseInsensitiveOnCaseInsensitiveSystems() {
        // 目标与原名只差大小写时不算冲突，否则在 Android 上永远改不回原来的名字
        val dir = temporaryFolder.newFolder("case")
        File(dir, "A.zip").writeText("x")
        assertFalse(
            oxideRenameWouldConflict(
                dir = dir,
                extension = ".zip",
                currentName = "A.zip",
                newName = "a",
            )
        )
    }
}