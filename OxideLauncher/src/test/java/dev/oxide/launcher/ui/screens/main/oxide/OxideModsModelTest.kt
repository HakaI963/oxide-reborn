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

import dev.oxide.launcher.game.version.mod.ModLoaderVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 与 `dev.oxide.launcher.game.version.mod.DISABLED_SUFFIX` 相同；这里写死是为了不把后端拖进这个纯逻辑测试 */
private const val DISABLED = ".disabled"

/**
 * 模组表面的纯逻辑
 *
 * 四组规则，每一组都对应一次真实故障：
 *
 *  - **元数据行**：哪些字段在有值时出现、在没值时不出现。缺值留一个"未知"
 *    占位符比不画更糟——用户分不清"确实没有"和"读不出来"。
 *  - **搜索 / 状态 / 排序**：与旧界面的行为一致，且降序永远是升序的倒过来。
 *  - **选中与批量**：键必须是去掉 `.disabled` 的文件名；批量启用只动状态与
 *    目标相反的那些；删除目标必须是磁盘上此刻真实的路径。
 */
class OxideModsModelTest {

    private val labels = OxideModMetaLabels(
        fileName = "File name",
        fileSize = "File size",
        localName = "Name in the file",
        version = "Mod version",
        author = "Authors",
        loader = "Loader",
        gameVersion = "Minecraft",
        compatible = "Compatibility",
        loaderless = "no loader declared",
        noInstanceLoader = "instance has no loader",
        mismatched = "not for this instance",
        unknownFile = "not a mod",
    )

    private fun row(
        key: String = "sodium-0.5.jar",
        enabled: Boolean = true,
        fileName: String = if (enabled) key else "$key$DISABLED",
        path: String = "/tmp/mods/$fileName",
        displayName: String = "Sodium",
        modVersion: String? = "0.5.3",
        authors: List<String> = listOf("JellySquid"),
        description: String? = "A modern rendering engine",
        sizeBytes: Long = 1024L * 512L,
        modifiedAt: Long = 100L,
        localLoader: String? = "Fabric",
        declaredLoaders: List<String> = listOf("Fabric"),
        instanceMinecraftVersion: String? = "1.20.1",
        instanceLoaders: List<String> = listOf("Fabric"),
        verdict: ModLoaderVerdict = ModLoaderVerdict.Compatible,
        notMod: Boolean = false,
    ) = OxideModRow(
        key = key,
        path = path,
        fileName = fileName,
        displayName = displayName,
        modVersion = modVersion,
        authors = authors,
        description = description,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        enabled = enabled,
        notMod = notMod,
        localLoader = localLoader,
        declaredLoaders = declaredLoaders,
        instanceMinecraftVersion = instanceMinecraftVersion,
        instanceLoaders = instanceLoaders,
        verdict = verdict,
    )

    // ---- 元数据行 -----------------------------------------------------------

    @Test
    fun theLocalNameOnlyShowsWhenItDiffersFromThePlatformTitle() {
        // 标题优先用平台上的项目名；jar 自己声明的名字不同时，两个都值得看
        assertFalse(
            OxideModMetaField.LocalName in oxideModsMeta(row(), labels).map { it.field }
        )
        val renamed = row().copy(projectTitle = "Sodium", localName = "Sodium Extra")
        assertEquals(
            "Sodium Extra",
            oxideModsMeta(renamed, labels).first { it.field == OxideModMetaField.LocalName }.value,
        )
    }

    @Test
    fun everyFieldThatHasAValueIsShown() {
        val fields = oxideModsMeta(row(), labels).map { it.field }
        assertEquals(
            listOf(
                OxideModMetaField.FileName,
                OxideModMetaField.FileSize,
                OxideModMetaField.Version,
                OxideModMetaField.Author,
                OxideModMetaField.Loader,
                OxideModMetaField.GameVersion,
            ),
            fields,
        )
    }

    @Test
    fun theFileNameLineShowsTheNameThatIsActuallyOnDisk() {
        val meta = oxideModsMeta(row(enabled = false), labels)
        assertEquals(
            "sodium-0.5.jar.disabled",
            meta.first { it.field == OxideModMetaField.FileName }.value,
        )
    }

    @Test
    fun aMissingVersionJustDropsThatLine() {
        val fields = oxideModsMeta(row(modVersion = null), labels).map { it.field }
        assertFalse(OxideModMetaField.Version in fields)
        assertTrue(OxideModMetaField.Author in fields)
    }

    @Test
    fun aBlankVersionIsTreatedAsMissing() {
        assertFalse(
            OxideModMetaField.Version in oxideModsMeta(row(modVersion = "   "), labels).map { it.field }
        )
    }

    @Test
    fun anEmptyAuthorListJustDropsThatLine() {
        val fields = oxideModsMeta(row(authors = emptyList()), labels).map { it.field }
        assertFalse(OxideModMetaField.Author in fields)
    }

    @Test
    fun blankAuthorsAreFilteredOutInsteadOfRenderedAsEmptyNames() {
        val meta = oxideModsMeta(row(authors = listOf("JellySquid", "", "  ")), labels)
        assertEquals("JellySquid", meta.first { it.field == OxideModMetaField.Author }.value)
    }

    @Test
    fun aZeroSizeIsNotShownAsZeroBytes() {
        // 读不出大小时画一个"0 B"是在编造事实
        val fields = oxideModsMeta(row(sizeBytes = 0L), labels).map { it.field }
        assertFalse(OxideModMetaField.FileSize in fields)
    }

    @Test
    fun theLoaderLinePrefersThePlatformDeclaration() {
        val meta = oxideModsMeta(
            row(localLoader = "Fabric", declaredLoaders = listOf("Fabric", "NeoForge")),
            labels,
        )
        assertEquals("Fabric + NeoForge", meta.first { it.field == OxideModMetaField.Loader }.value)
    }

    @Test
    fun theLoaderLineFallsBackToTheLocalDeclaration() {
        val meta = oxideModsMeta(
            row(localLoader = "Forge", declaredLoaders = emptyList()),
            labels,
        )
        assertEquals("Forge", meta.first { it.field == OxideModMetaField.Loader }.value)
    }

    @Test
    fun noLoaderAtAllMeansNoLoaderLineRatherThanUnknown() {
        // ModLoader.UNKNOWN 的显示名是空串，画出来也只是一句"未知"
        assertFalse(
            OxideModMetaField.Loader in
                oxideModsMeta(row(localLoader = null, declaredLoaders = emptyList()), labels)
                    .map { it.field }
        )
    }

    @Test
    fun noMinecraftVersionMeansNoGameVersionLine() {
        assertFalse(
            OxideModMetaField.GameVersion in
                oxideModsMeta(row(instanceMinecraftVersion = null), labels).map { it.field }
        )
    }

    @Test
    fun aCompatibleModGetsNoCompatibilityLine() {
        assertNull(oxideModsCompatibilityText(row(), labels))
    }

    @Test
    fun theFourVerdictsAreFourDifferentSentences() {
        // "没法判断"和"确定不兼容"必须说成两句话，否则用户会把读不出来当成装不上
        val texts = listOf(
            ModLoaderVerdict.Compatible,
            ModLoaderVerdict.Loaderless,
            ModLoaderVerdict.NoInstanceLoader,
            ModLoaderVerdict.Mismatch,
        ).map { oxideModsCompatibilityText(row(verdict = it), labels) }
        assertNull(texts[0])
        assertEquals(
            listOf("no loader declared", "instance has no loader", "not for this instance"),
            texts.drop(1),
        )
    }

    @Test
    fun aFileThatIsNotAModSaysSo() {
        val meta = oxideModsMeta(
            row(
                notMod = true,
                modVersion = null,
                authors = emptyList(),
                localLoader = null,
                declaredLoaders = emptyList(),
            ),
            labels,
        )
        assertEquals(
            listOf(
                OxideModMetaField.FileName,
                OxideModMetaField.FileSize,
                OxideModMetaField.GameVersion,
                OxideModMetaField.Compatibility,
            ),
            meta.map { it.field },
        )
        assertEquals("not a mod", meta.last().value)
    }

    @Test
    fun notBeingAModOutranksTheLoaderVerdict() {
        // 一个读不出元数据的 jar 的加载器判定毫无意义，不能因此说它不兼容
        val meta = oxideModsMeta(row(notMod = true, verdict = ModLoaderVerdict.Mismatch), labels)
        assertEquals("not a mod", meta.first { it.field == OxideModMetaField.Compatibility }.value)
    }

    // ---- 搜索 ---------------------------------------------------------------

    @Test
    fun anEmptyQueryKeepsEverything() {
        val all = listOf(row(key = "a.jar"), row(key = "b.jar"))
        assertEquals(all, filterOxideMods(all, "", OxideModState.All))
    }

    @Test
    fun theQueryMatchesNameFileIdVersionAuthorAndLoader() {
        val all = listOf(row())
        listOf("Sodium", "sodium-0.5", "sodium", "0.5.3", "jellys", "fabric").forEach { needle ->
            assertEquals("query \"$needle\" must match", all, filterOxideMods(all, needle, OxideModState.All))
        }
    }

    @Test
    fun aQueryThatMatchesNothingReturnsNothing() {
        assertTrue(filterOxideMods(listOf(row()), "zzz", OxideModState.All).isEmpty())
    }

    @Test
    fun theDisabledFilterFindsDisabledModsByKeyNotByFileName() {
        // 禁用态的 fileName 带后缀，键不带；筛选必须走键，否则筛"禁用"时一条都没有
        val off = row(key = "sodium-0.5.jar", enabled = false, fileName = "sodium-0.5.jar.disabled")
        assertEquals(listOf(off), filterOxideMods(listOf(off), "", OxideModState.Disabled))
        assertEquals(emptyList<OxideModRow>(), filterOxideMods(listOf(off), "", OxideModState.Enabled))
    }

    @Test
    fun theStateCountsAlwaysAddUp() {
        val all = listOf(
            row(key = "a.jar"),
            row(key = "b.jar", enabled = false, fileName = "b.jar.disabled"),
            row(key = "c.jar", enabled = false, fileName = "c.jar.disabled"),
        )
        val counts = oxideModsStateCounts(all)
        assertEquals(3, counts.all)
        assertEquals(1, counts.enabled)
        assertEquals(2, counts.disabled)
        assertEquals(counts.all, counts.enabled + counts.disabled)
    }

    // ---- 排序 ---------------------------------------------------------------

    @Test
    fun sortingIsByTheBaseNameSoTheSuffixNeverChangesTheOrder() {
        val all = listOf(
            row(key = "b.jar", enabled = false, fileName = "b.jar.disabled"),
            row(key = "a.jar", enabled = false, fileName = "a.jar.disabled"),
        )
        assertEquals(
            listOf("a.jar", "b.jar"),
            sortOxideMods(all, OxideModSort.FileName, true).map { it.key },
        )
    }

    @Test
    fun descendingIsTheReverseOfAscending() {
        val all = listOf(
            row(key = "a.jar", sizeBytes = 10L),
            row(key = "b.jar", sizeBytes = 30L),
            row(key = "c.jar", sizeBytes = 20L),
        )
        val asc = sortOxideMods(all, OxideModSort.FileSize, true).map { it.key }
        val desc = sortOxideMods(all, OxideModSort.FileSize, false).map { it.key }
        assertEquals(asc.reversed(), desc)
        assertEquals(listOf("b.jar", "c.jar", "a.jar"), desc)
    }

    @Test
    fun sortCyclingReturnsToWhereItStarted() {
        var state = oxideModsDefaultSort()
        repeat(oxideModSortOptions().size * 2) {
            state = nextOxideModSort(state.first, state.second)
        }
        assertEquals(oxideModsDefaultSort(), state)
    }

    @Test
    fun theSortLabelCarriesBothTheKeyAndTheDirection() {
        assertTrue(oxideModSortLabel(OxideModSort.Loader, true).contains("Loader"))
        assertTrue(oxideModSortLabel(OxideModSort.Loader, false).contains("desc"))
    }

    // ---- 选中与批量 ---------------------------------------------------------

    @Test
    fun togglingAddsThenRemovesTheSameKey() {
        assertEquals(listOf("a.jar"), oxideModsToggleSelection(emptyList(), "a.jar"))
        assertEquals(emptyList<String>(), oxideModsToggleSelection(listOf("a.jar"), "a.jar"))
    }

    @Test
    fun selectAllAddsExactlyTheVisibleKeysAndIsIdempotent() {
        val visible = listOf(row(key = "a.jar"), row(key = "b.jar"))
        val once = oxideModsSelectAll(visible, emptyList())
        assertEquals(listOf("a.jar", "b.jar"), once)
        assertEquals(once, oxideModsSelectAll(visible, once))
    }

    @Test
    fun selectAllKeepsAlreadySelectedRowsThatAreFilteredOut() {
        // 被筛选条件藏起来的那些正在被选中：全选不能把它们悄悄丢掉
        val visible = listOf(row(key = "a.jar"))
        val selected = listOf("hidden.jar")
        assertEquals(listOf("hidden.jar", "a.jar"), oxideModsSelectAll(visible, selected))
    }

    @Test
    fun clearingOnlyClearsTheVisibleRows() {
        val visible = listOf(row(key = "a.jar"))
        val selected = listOf("hidden.jar", "a.jar")
        assertEquals(listOf("hidden.jar"), oxideModsClearVisibleSelection(visible, selected))
    }

    @Test
    fun everythingSelectedIsOnlyTrueWhenEveryVisibleRowIsIn() {
        val visible = listOf(row(key = "a.jar"), row(key = "b.jar"))
        assertFalse(oxideModsEverythingSelected(visible, emptyList()))
        assertFalse(oxideModsEverythingSelected(visible, listOf("a.jar")))
        assertTrue(oxideModsEverythingSelected(visible, listOf("a.jar", "b.jar")))
        assertTrue("extra hidden keys do not break it", oxideModsEverythingSelected(visible, listOf("a.jar", "b.jar", "c.jar")))
        assertFalse("an empty list is never everything", oxideModsEverythingSelected(emptyList(), emptyList()))
    }

    @Test
    fun selectedRowsResolveByKeyNotByFileName() {
        // 选中的是键；被外部删掉的键解析不出来就跳过，不留一个悬空项
        val rows = listOf(
            row(key = "a.jar"),
            row(key = "b.jar", enabled = false, fileName = "b.jar.disabled"),
        )
        assertEquals(
            listOf("a.jar", "b.jar"),
            oxideModsSelectedRows(rows, listOf("a.jar", "b.jar")).map { it.key },
        )
        assertEquals(
            listOf("a.jar"),
            oxideModsSelectedRows(rows, listOf("a.jar", "gone.jar")).map { it.key },
        )
    }

    @Test
    fun bulkEnableOnlyTouchesRowsThatAreActuallyDisabled() {
        // 重复点同一个方向不该再动一次文件：那会把每个文件重命名两次
        val rows = listOf(
            row(key = "on.jar"),
            row(key = "off.jar", enabled = false, fileName = "off.jar.disabled"),
        )
        assertEquals(
            listOf("off.jar"),
            oxideModsRowsToToggle(rows, enable = true).map { it.key },
        )
        assertEquals(
            listOf("on.jar"),
            oxideModsRowsToToggle(rows, enable = false).map { it.key },
        )
    }

    @Test
    fun bulkEnableOfAnAlreadyEnabledSelectionHasNothingToDo() {
        val rows = listOf(row(key = "a.jar"), row(key = "b.jar"))
        assertTrue(oxideModsRowsToToggle(rows, enable = true).isEmpty())
    }

    @Test
    fun bulkOutcomeCountsWhatActuallyMoved() {
        // 没改成的那些必须被点名，而不是整批报成功
        val outcome = oxideModBulkOutcome(attempted = 3, changed = 2)
        assertEquals(2, outcome.changed)
        assertEquals(1, outcome.skipped)
        assertTrue(outcome.touchedAnything)
        assertFalse(oxideModBulkOutcome(attempted = 0, changed = 0).touchedAnything)
    }

    @Test
    fun deleteTargetsAreTheLivePathsIncludingTheDisabledSuffix() {
        // 用户报的那一条：删除按 fileName 拼路径，禁用态的文件因此一个也删不掉，
        // 而 deleteQuietly 不抛异常，于是"删除点了没反应"。
        val rows = listOf(
            row(key = "on.jar", path = "/tmp/mods/on.jar"),
            row(
                key = "off.jar",
                enabled = false,
                fileName = "off.jar.disabled",
                path = "/tmp/mods/off.jar.disabled",
            ),
        )
        assertEquals(
            listOf("/tmp/mods/on.jar", "/tmp/mods/off.jar.disabled"),
            oxideModsDeletePaths(rows),
        )
    }

    @Test
    fun deletePathsAreDeduplicated() {
        val rows = listOf(row(key = "a.jar", path = "/tmp/mods/a.jar"), row(key = "a.jar", path = "/tmp/mods/a.jar"))
        assertEquals(listOf("/tmp/mods/a.jar"), oxideModsDeletePaths(rows))
    }

    @Test
    fun theConfirmationShowsTheRealFileNamesNotTheDisplayNames() {
        val rows = listOf(
            row(
                key = "sodium.jar",
                enabled = false,
                fileName = "sodium.jar.disabled",
                displayName = "Sodium",
            )
        )
        // 删除不可逆，屏幕上必须出现真实文件名，而且禁用态那一条要带后缀
        assertEquals(listOf("sodium.jar.disabled"), oxideModsDeleteLabels(rows))
    }

    @Test
    fun anIconIsOnlyClaimedWhenThereIsSomethingToShow() {
        assertFalse(row().hasIcon)
        assertTrue(row().copy(projectIconUrl = "https://example.invalid/i.png").hasIcon)
        assertTrue(row().copy(iconBytes = byteArrayOf(1, 2, 3)).hasIcon)
        // 空白的项目封面不算封面
        assertFalse(row().copy(projectIconUrl = "   ").hasIcon)
    }

    @Test
    fun onlyAModThatCanBeMatchedToThePlatformEntersTheUpdateFlow() {
        assertFalse(row().copy(checkRemote = false).updatable)
        assertFalse("还没有远端项目信息", row().updatable)
        assertTrue(row().copy(projectId = "AANobbMI").updatable)
    }

    @Test
    fun aModThatFailsTheLoaderGuardCannotBeUpdatedInPlace() {
        // 这正是修掉的那个缺陷在界面上的体现：不兼容的模组连更新按钮都点不动
        val mismatch = row(verdict = ModLoaderVerdict.Mismatch).copy(projectId = "AANobbMI")
        assertFalse(mismatch.updatable)
    }
}