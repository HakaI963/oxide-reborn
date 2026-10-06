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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.game.download.assets.platform.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「已安装」限定在哪个实例上
 *
 * 用户的原话是"in discover/installed add short option that specify installed mods by version
 * name /instenses"。判定本身是一个纯函数——输入是"本地那份记录"加"这次扫描扫的是哪个
 * 实例"，输出是一个布尔——所以这里不需要磁盘、不需要协程，也不需要 Compose。
 *
 * **为什么实例这一维必须由外面补进来**：本地记录 [dev.oxide.launcher.game.version.mod.InstalledMod]
 * 的字段只有 `platform` / `projectId` / `versionId` / `versionName` / `notFound`，一个
 * Minecraft 版本、一个实例 id 都没有。指纹扫描一次只扫一个实例的 mods 目录
 * （`DownloadModViewViewModel.scan` 收一个 `Version`），因此"这份记录属于谁"这个事实
 * 只存在于发起扫描的那一层。这就是 [DiscoverInstalledRecord.instanceName] 存在的全部理由。
 *
 * [localInstalledModRecordsCarryNoVersionNorInstance] 把这个缺口写成一条断言：真的哪天给
 * 本地记录加上了实例 id，这一整段绕法就该被拿掉，而不是继续留在注释里。
 */
class OxideDiscoverInstalledScopeTest {

    // -----------------------------------------------------------------------
    // 判定
    // -----------------------------------------------------------------------

    /** 同一个平台、同一个实例：算装在范围内 */
    @Test
    fun aRecordFromTheSameInstanceCountsAsInstalled() {
        assertTrue(discoverProjectInstalled(record(SCOPE), Platform.MODRINTH))
    }

    /**
     * 两个平台的 id 会撞车，所以平台必须一起比
     *
     * Modrinth 与 CurseForge 上都存在数字 id，"1" 在两边是两个不同的项目。
     */
    @Test
    fun aRecordFromAnotherPlatformNeverCounts() {
        assertFalse(discoverProjectInstalled(record(SCOPE), Platform.CURSEFORGE))
    }

    /** 换了实例之后，上一份扫描的结果一律不算数 */
    @Test
    fun aRecordFromAnotherInstanceNeverCounts() {
        assertFalse(
            discoverProjectInstalled(
                record(instance = "1.20.1"),
                Platform.MODRINTH,
                scopeInstanceName = "1.21.1",
            )
        )
    }

    /**
     * 扫描还没落定时一份都不算数
     *
     * 换实例与换筛选范围都会让旧记录过期几帧（扫描是挂起的）。这一段空窗里宁可
     * 不给「Installed」标记，也不要把上一份扫描的结果挂在新的范围名下。
     */
    @Test
    fun aRecordWithoutASettledScanTargetNeverCounts() {
        assertFalse(discoverProjectInstalled(record(instance = null), Platform.MODRINTH))
        assertFalse(discoverProjectInstalled(record(instance = ""), Platform.MODRINTH))
        assertFalse(discoverProjectInstalled(record(instance = "   "), Platform.MODRINTH))
    }

    /** 空的项目 id 也一样：那不是一条装好的东西 */
    @Test
    fun aRecordWithoutAProjectIdNeverCounts() {
        assertFalse(discoverProjectInstalled(record(SCOPE, projectId = ""), Platform.MODRINTH))
        assertFalse(discoverProjectInstalled(record(SCOPE, projectId = "  "), Platform.MODRINTH))
    }

    /**
     * 范围留空表示"跟着当前选中的实例"
     *
     * 此时记录自己的实例名就是判定依据——注意不是"全部实例"：范围为空且扫描没落定时
     * 上面那条已经返回 false 了，所以这里走到 `?: return true` 的记录一定是扫过的。
     */
    @Test
    fun anEmptyScopeFollowsTheRecordItself() {
        assertTrue(discoverProjectInstalled(record(SCOPE), Platform.MODRINTH, scopeInstanceName = null))
        assertTrue(discoverProjectInstalled(record(SCOPE), Platform.MODRINTH, scopeInstanceName = "  "))
    }

    /** 用户输入的前后空格不参与比较 */
    @Test
    fun theScopeIsTrimmedBeforeItIsCompared() {
        assertTrue(
            discoverProjectInstalled(
                record(SCOPE),
                Platform.MODRINTH,
                scopeInstanceName = "  $SCOPE  ",
            )
        )
    }

    // -----------------------------------------------------------------------
    // 批量取 id
    // -----------------------------------------------------------------------

    /** 一个项目装两次只留一个 id，顺序按记录到达的顺序 */
    @Test
    fun duplicateProjectIdsCollapseToOne() {
        val ids = discoverInstalledProjectIds(
            records = listOf(
                record(SCOPE, projectId = "sodium"),
                record(SCOPE, projectId = "lithium"),
                // 同一个项目的另一个文件版本，仍然是同一个项目
                record(SCOPE, projectId = "sodium", versionName = "mc1.20.1-0.6.0"),
            ),
            platform = Platform.MODRINTH,
        )

        assertEquals(listOf("sodium", "lithium"), ids.toList())
    }

    /**
     * 换范围之后，上一次的结果不会被带过来
     *
     * 这是这个筛选最容易出错的地方：把同一份记录按名字反查回去，两个实例可能同名
     * （改名之后），命中的是另一条，卡片上就出现一个其实并没有装在那儿的标记。
     */
    @Test
    fun changingTheScopeDropsThePreviousInstancesIds() {
        val records = listOf(
            record("1.20.1", projectId = "sodium"),
            record("1.21.1", projectId = "lithium"),
        )

        assertEquals(
            setOf("sodium"),
            discoverInstalledProjectIds(records, Platform.MODRINTH, scopeInstanceName = "1.20.1"),
        )
        assertEquals(
            setOf("lithium"),
            discoverInstalledProjectIds(records, Platform.MODRINTH, scopeInstanceName = "1.21.1"),
        )
        // 范围收空回到"跟着当前选中的实例"，而不是回到两个实例的并集
        assertEquals(
            setOf("sodium", "lithium"),
            discoverInstalledProjectIds(records, Platform.MODRINTH, scopeInstanceName = null),
        )
    }

    /** 平台这一维在批量这一层也一样不能忘 */
    @Test
    fun theBatchKeepsPlatformApart() {
        val records = listOf(
            record(SCOPE, projectId = "1", platform = Platform.MODRINTH),
            record(SCOPE, projectId = "1", platform = Platform.CURSEFORGE),
        )

        assertEquals(setOf("1"), discoverInstalledProjectIds(records, Platform.MODRINTH))
        assertEquals(setOf("1"), discoverInstalledProjectIds(records, Platform.CURSEFORGE))
    }

    // -----------------------------------------------------------------------
    // 接线
    // -----------------------------------------------------------------------

    /**
     * 本地记录不带 Minecraft 版本，也不带实例 id
     *
     * 这是**如实记录一个缺口**，不是给这个缺口发许可证：正因为没有这一维，
     * "按版本名 / 按实例"这个筛选才必须换掉扫描目标，而不是把同一份数据切两半。
     * 哪天这里开始失败，说明本地记录补上了这一维，上面那一整套绕法该被拿掉。
     */
    @Test
    fun localInstalledModRecordsCarryNoVersionNorInstance() {
        val source = readSource("game/version/mod/InstalledMod.kt")
        val model = code(source)

        assertTrue(
            "the local record must still carry the platform",
            model.contains("val platform: Platform"),
        )
        assertTrue(
            "and the platform version it matched",
            model.contains("val versionId: String"),
        )
        for (absent in listOf("val instanceId", "val versionFolder", "val gameVersion", "val minecraftVersion")) {
            assertFalse(
                "the local record grew a `$absent`; revisit how the installed scope is resolved",
                model.contains(absent),
            )
        }
    }

    /**
     * 筛选栏里真的有那一个下拉，而不是只在注释里
     *
     * 复用既有的 `OxideDropdown` 与既有的那条筛选栏：新增一个控件只会让这一行
     * 在窄屏上多换一次行。用户要的是"一个短的选项"，因此只有一个下拉，
     * 且它只在左栏「Installed」被选中时才有意义——其余时候是关着的，不悄悄生效。
     */
    @Test
    fun theFilterBarCarriesExactlyOneInstalledScopeDropdown() {
        val bar = code(readSource("ui/screens/main/oxide/OxideDiscoverPage.kt"))
            .blockOf("private fun DiscoverFilterBar(")

        assertEquals(
            "the installed scope must reuse the shared dropdown, not invent a control",
            1,
            Regex("label = stringResource\\(R\\.string\\.oxide_dis_tab_installed\\)").findAll(bar).count(),
        )
        assertTrue(
            "the dropdown must be disabled unless the Installed tab is the active one",
            bar.contains("enabled = viewModel.onlyInstalled"),
        )
        assertTrue(
            "selecting an option must reach the state layer",
            bar.contains("viewModel.selectInstalledScope("),
        )
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun record(
        instance: String?,
        projectId: String = "sodium",
        versionName: String = "mc1.21.1-0.6.0",
        platform: Platform = Platform.MODRINTH,
    ): DiscoverInstalledRecord = DiscoverInstalledRecord(
        platform = platform,
        projectId = projectId,
        versionName = versionName,
        instanceName = instance,
    )

    private companion object {
        const val SCOPE = "1.21.1"

        /** 去掉字符串与注释，只留下真正会被编译的代码 */
        fun code(source: String): String = source
            .replace(RAW_STRING, "\"\"")
            .replace(STRING, "\"\"")
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, "")

        fun readSource(name: String): String = locate(name).readText()

        /**
         * 从当前工作目录往上找源文件
         *
         * 单元测试的工作目录不一定是模块根目录，所以逐级上溯，
         * 找不到就直接报错——绝不能悄悄跳过，那样的测试等于没有。
         */
        fun locate(relativePath: String): File {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
                if (candidate != null && candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            error(
                "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                    File("").absolutePath
            )
        }

        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }

    /**
     * 从 [needle] 那一处声明起、到它那个大括号块结束的整段源码
     *
     * 索引源码文本的函数一律是 String 的扩展：写成顶层函数再在这里调用它，
     * `source` 既不是参数也不是接收者，编译不通过（CI 上已经因此挂过一次）。
     */
    private fun String.blockOf(needle: String): String {
        val start = indexOf(needle)
        assertTrue("expected to find `$needle`", start >= 0)
        val brace = indexOf('{', start)
        assertTrue("expected `$needle` to have a body", brace >= 0)
        return substring(start, balancedEndFrom(brace) + 1)
    }

    private fun String.balancedEndFrom(start: Int): Int {
        var depth = 0
        var index = start
        while (index < length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }

                '"', '\'' -> {
                    val quote = this[index]
                    index++
                    while (index < length && this[index] != quote) {
                        if (this[index] == '\\') index++
                        index++
                    }
                }
            }
            index++
        }
        error("unbalanced braces starting at offset $start")
    }
}