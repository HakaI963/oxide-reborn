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

import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.game.version.export.ExportInfo
import dev.oxide.launcher.game.version.export.PackType
import dev.oxide.launcher.game.version.export.data.FileSelectionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 整合包导出里被抽出来的那部分纯逻辑
 *
 * 这里钉住的是**规则**，不是外观：换格式时的默认信息、必填项、
 * CurseForge 远端资源的开关条件、默认勾选的黑名单、名称清洗，
 * 以及文件树摊平后的顺序与缩进。这些都直接决定导出会产出什么。
 */
class OxideExportLogicTest {

    private val gamePath = File("/tmp/oxide-instance")

    private fun defaultInfo(packType: PackType) = oxideExportDefaultInfo(
        packType = packType,
        gamePath = gamePath,
        versionName = "My Pack",
        mcVersion = "1.20.1",
        loader = null,
        gameArgs = "--width 854",
        javaArgs = "-Xmx2G",
    )

    // ---- 换格式时的默认信息 -----------------------------------------------

    @Test
    fun defaultInfoCarriesTheInstanceFactsOver() {
        val info = defaultInfo(PackType.Modrinth)
        assertEquals(gamePath, info.gamePath)
        assertEquals("My Pack", info.name)
        assertEquals("1.0", info.version)
        assertEquals("1.20.1", info.mcVersion)
        assertEquals("当前版本配置的游戏参数必须预填", "--width 854", info.gameArgs)
        assertEquals("当前版本配置的 JVM 参数必须预填", "-Xmx2G", info.javaArgs)
    }

    @Test
    fun modrinthTurnsBothRemoteTogglesOn() {
        val info = defaultInfo(PackType.Modrinth)
        assertTrue(info.packModrinth)
        assertTrue(info.packCurseForge)
    }

    @Test
    fun curseForgeOnlyTurnsItsOwnRemoteToggleOn() {
        val info = defaultInfo(PackType.CurseForge)
        assertFalse(info.packModrinth)
        assertTrue(info.packCurseForge)
    }

    @Test
    fun mcbbsAndMultiMcHaveNoRemoteTogglesAtAll() {
        listOf(PackType.MCBBS, PackType.MultiMC).forEach { type ->
            val info = defaultInfo(type)
            assertFalse("$type 不该打包 Modrinth 资源", info.packModrinth)
            assertFalse("$type 不该打包 CurseForge 资源", info.packCurseForge)
            assertFalse("$type 不会去抓远端", oxideExportPacksRemote(info))
        }
    }

    // ---- 必填项 -----------------------------------------------------------

    @Test
    fun nameAndVersionAreAlwaysRequired() {
        val modrinth = defaultInfo(PackType.Modrinth)
        assertTrue(oxideExportCanContinue(modrinth))
        assertFalse(oxideExportCanContinue(modrinth.copy(name = "  ")))
        assertFalse(oxideExportCanContinue(modrinth.copy(version = "")))
    }

    @Test
    fun authorIsRequiredOnlyByTheFormatsThatAskForIt() {
        // Modrinth 不问作者
        assertTrue(oxideExportCanContinue(defaultInfo(PackType.Modrinth).copy(author = "")))
        // MCBBS / CurseForge / MultiMC 都要作者
        listOf(PackType.MCBBS, PackType.CurseForge, PackType.MultiMC).forEach { type ->
            assertFalse(
                "$type 必须填作者",
                oxideExportCanContinue(defaultInfo(type).copy(author = " ")),
            )
            assertTrue(
                type,
                oxideExportCanContinue(defaultInfo(type).copy(author = "me")),
            )
        }
    }

    @Test
    fun theFilesStepOpensUpOnlyOnceTheRequiredFieldsAreThere() {
        assertTrue(oxideExportStepReachable(OxideExportStep.Type, null))
        assertFalse(oxideExportStepReachable(OxideExportStep.Files, null))

        val mcbbs = defaultInfo(PackType.MCBBS).copy(author = "")
        assertFalse("作者没填时不能去挑文件", oxideExportStepReachable(OxideExportStep.Files, mcbbs))
        assertTrue(oxideExportStepReachable(OxideExportStep.Files, mcbbs.copy(author = "me")))
    }

    // ---- CurseForge 远端资源 ----------------------------------------------

    @Test
    fun curseForgeToggleIsAlwaysLiveOnItsOwnFormat() {
        assertTrue(oxideExportCurseForgeEnabled(defaultInfo(PackType.CurseForge).copy(packModrinth = false)))
    }

    @Test
    fun curseForgeToggleNeedsModrinthFirstOnTheModrinthFormat() {
        val modrinth = defaultInfo(PackType.Modrinth).copy(packModrinth = false)
        assertFalse(oxideExportCurseForgeEnabled(modrinth))
        assertTrue(oxideExportCurseForgeEnabled(modrinth.copy(packModrinth = true)))
    }

    @Test
    fun formatsWithoutRemoteTogglesNeverEnableTheCurseForgeRow() {
        listOf(PackType.MCBBS, PackType.MultiMC).forEach { type ->
            assertFalse(type, oxideExportCurseForgeEnabled(defaultInfo(type).copy(packModrinth = true)))
        }
    }

    @Test
    fun theCurseForgeRowReadsAsAnExtraOnlyOnModrinth() {
        assertTrue(oxideExportCurseForgeIsExtra(defaultInfo(PackType.Modrinth)))
        assertFalse(oxideExportCurseForgeIsExtra(defaultInfo(PackType.CurseForge)))
    }

    @Test
    fun theRemotePlatformListFollowsTheTogglesInOrder() {
        val info = defaultInfo(PackType.Modrinth)
        assertEquals(listOf("Modrinth", "CurseForge"), oxideExportRemotePlatforms(info))
        assertEquals(
            listOf("CurseForge"),
            oxideExportRemotePlatforms(info.copy(packModrinth = false)),
        )
        assertEquals(
            emptyList<String>(),
            oxideExportRemotePlatforms(info.copy(packModrinth = false, packCurseForge = false)),
        )
    }

    // ---- 默认勾选的黑名单 -------------------------------------------------

    @Test
    fun blacklistedEntriesAreNotSelectedByDefault() {
        val off = listOf(
            "options.txt", "saves", "logs", "crash-report.log",
            "natives", "downloads", ".fabric", "usercache.json", "realms_persistence.json",
            BuildKeys.LAUNCHER_IDENTIFIER, "fclversion.cfg", "PCL",
        )
        off.forEach { name ->
            assertFalse("$name 默认不该打包", oxideExportDefaultSelected(name, oxideExportSelectBlackList))
        }
    }

    @Test
    fun ordinaryContentIsSelectedByDefault() {
        // 这里给一份显式的黑名单：断言的是规则本身，而不是构建期那个启动器标识符
        val blackList = listOf("saves", "logs", "natives", "options.txt")
        listOf("mods", "config", "resourcepacks", "shaderpacks", "1.20.1.jar")
            .forEach { name ->
                assertTrue("$name 默认该打包", oxideExportDefaultSelected(name, blackList))
            }
    }

    @Test
    fun theBlacklistMatchesOnSubstringsJustLikeTheOldOne() {
        // 旧实现用的是 contains，因此 "logs-2024" 同样会被排除
        assertFalse(oxideExportDefaultSelected("logs-2024", listOf("logs")))
        assertTrue(oxideExportDefaultSelected("mods", listOf("logs")))
    }

    // ---- 名称清洗 ---------------------------------------------------------

    @Test
    fun nameCleaningStripsIllegalPathCharacters() {
        assertEquals("MyPack", oxideExportSanitizeName("My/Pack"))
        assertEquals("abc", oxideExportSanitizeName("a\\b\"c?d*e<f>g|h"))
        assertEquals("ab c", oxideExportSanitizeName("ab\nc"))
    }

    @Test
    fun nameCleaningKeepsOrdinaryTextUntouched() {
        assertEquals("My Pack 2", oxideExportSanitizeName("My Pack 2"))
        assertEquals("", oxideExportSanitizeName(""))
    }

    // ---- 文件树摊平 -------------------------------------------------------

    private fun fileNode(name: String) = FileSelectionData(
        file = File("/tmp/oxide-instance/$name"),
        alias = null,
        child = null,
    )

    private fun dirNode(name: String, child: List<FileSelectionData>) = FileSelectionData(
        file = File("/tmp/oxide-instance/$name"),
        alias = null,
        child = child,
    )

    @Test
    fun collapsedDirectoriesOnlyShowTheFolderItself() {
        val nodes = oxideExportVisibleNodes(listOf(dirNode("mods", listOf(fileNode("a.jar")))))
        assertEquals(1, nodes.size)
        assertEquals(0, nodes[0].indentation)
    }

    @Test
    fun expandedDirectoriesPutChildrenAfterThemselfAtDeeperIndentation() {
        val dir = dirNode("mods", listOf(fileNode("a.jar"), fileNode("b.jar")))
        dir.expandDirs(true)

        val nodes = oxideExportVisibleNodes(listOf(dir))
        assertEquals(3, nodes.size)
        assertEquals(0, nodes[0].indentation)
        assertEquals(1, nodes[1].indentation)
        assertEquals(1, nodes[2].indentation)
        assertEquals(
            "目录自己必须排在它的子项前面",
            listOf("mods", "a.jar", "b.jar"),
            nodes.map { (it as OxideExportNode.Entry).data.file.name },
        )
    }

    @Test
    fun aCollapsedDirectoryHidesItsChildren() {
        val dir = dirNode("mods", listOf(fileNode("a.jar"), fileNode("b.jar")))
        assertEquals(1, oxideExportVisibleNodes(listOf(dir)).size)
    }

    @Test
    fun anExpandedEmptyFolderGetsAHintInsteadOfACheckslotsBox() {
        val dir = dirNode("empty", emptyList())
        dir.expandDirs(true)
        val nodes = oxideExportVisibleNodes(listOf(dir))
        assertEquals(2, nodes.size)
        assertTrue(nodes[1] is OxideExportNode.EmptyHint)
        assertEquals("空目录提示行的 key 必须唯一且带缩进", 1, nodes[1].indentation)
    }

    @Test
    fun everyNodeKeyIsUniqueSoLazyListsStayStable() {
        val dir = dirNode("mods", listOf(fileNode("a.jar"), fileNode("b.jar")))
        dir.expandDirs(true)
        val keys = oxideExportVisibleNodes(listOf(dir)).map { it.key }
        assertEquals("key 必须互不相同：$keys", keys.size, keys.toSet().size)
    }

    @Test
    fun rootOrderIsPreserved() {
        val first = fileNode("z.jar")
        val second = fileNode("a.jar")
        val nodes = oxideExportVisibleNodes(listOf(first, second))
        assertEquals(
            listOf("z.jar", "a.jar"),
            nodes.map { (it as OxideExportNode.Entry).data.file.name },
        )
    }
}