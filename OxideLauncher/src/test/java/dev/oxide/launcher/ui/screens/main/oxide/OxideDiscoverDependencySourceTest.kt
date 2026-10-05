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

import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.cacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依赖的唯一来源：整张矩阵
 *
 * 用户报的现象是"有些模组点安装时说没有依赖，可打开依赖标签页却显示有依赖"。
 * 成因不是某一处算错，而是**同一件事有三种算法**：依赖属于某一个**文件版本**，
 * 而详情标签页在目标实例没有兼容文件时会退回 `versions.first()` 再读它的依赖，
 * 安装确认层却不退回，于是 `version == null` 变成一份空列表，界面上印出
 * "This file has no dependencies."。
 *
 * 因此 [discoverDependencySource] 是唯一入口，下面逐条钉死：
 *  - 真的有依赖 / 真的没有依赖；
 *  - 可选与互斥这些"列出来了但装不了"的关系；
 *  - 目标实例这一档压根没有兼容文件时，**必须**与"没有依赖"分得开；
 *  - 回归：标签页与确认层对同一个项目 + 同一个目标给出同一批依赖键。
 *
 * 用泛型版本（而不是 `PlatformVersion`）是为了让这里的每一行都只是普通数据：
 * 文件长什么样是平台解析器的事，与"依赖从哪来"这条规则无关。
 */
class OxideDiscoverDependencySourceTest {

    // ---- 目标的两种形态 --------------------------------------------------

    private val target1204 = DiscoverTarget(
        instanceName = "fabric-loader-0.16.9-1.20.4",
        minecraftVersion = "1.20.4",
        loaders = setOf("Fabric"),
        installed = true,
        detected = true,
    )

    private val target121 = DiscoverTarget(
        instanceName = null,
        minecraftVersion = "1.21.1",
        loaders = emptySet(),
        installed = false,
        detected = false,
    )

    /** 一个只为了说清自身事实的文件；刻意不带任何网络语义 */
    private data class File(
        val name: String,
        val gameVersions: List<String>,
        val loaders: List<String> = emptyList(),
        val dependencies: List<DiscoverDependency> = emptyList(),
    ) {
        fun facts(): DiscoverFileFacts = DiscoverFileFacts(gameVersions, loaders)
    }

    private fun source(
        files: List<File>,
        target: DiscoverTarget,
    ): DiscoverDependencySource<File> = discoverDependencySource(
        versions = files,
        target = target,
        factsOf = { it.facts() },
        dependenciesOf = { it.dependencies },
    )

    private fun dep(
        projectId: String,
        type: PlatformDependencyType,
        platform: Platform = Platform.CURSEFORGE,
        versionId: String? = null,
    ) = DiscoverDependency(platform, projectId, versionId, type)

    // ---- 真的没有依赖 ---------------------------------------------------

    @Test
    fun aProjectWithNoFilesSaysItHasNoFilesNotThatItHasNoDependencies() {
        val result = source(emptyList(), target1204)
        assertTrue(result.noFiles)
        assertFalse(result.noFileForTarget)
        assertFalse("项目一个文件都没有时，报的必须是\"没有文件\"而不是\"目标没有文件\"", result.noFileForTarget)
        assertNull(result.version)
        assertNull(result.fallbackVersion)
        assertTrue(result.dependencies.isEmpty())
        // 两种"没有"必须可区分
        assertFalse(result.genuinelyEmpty)
    }

    @Test
    fun theFileTheTargetGetsReallyHasNoDependencies() {
        val plain = File("sodium-1.0.0.jar", gameVersions = listOf("1.20.4"))
        val result = source(listOf(plain), target1204)
        assertFalse(result.noFiles)
        assertFalse(result.noFileForTarget)
        assertSame(plain, result.version)
        assertTrue(result.installable)
        // 只有**确实读到了那个文件**、而它一条关系都没标时才算真的没有依赖
        assertTrue(result.genuinelyEmpty)
        assertNull(result.fallbackVersion)
    }

    // ---- 真的有关系 -----------------------------------------------------

    @Test
    fun oneRequiredDependencyIsListed() {
        val required = dep("fabric-api", PlatformDependencyType.REQUIRED)
        val file = File("mod.jar", listOf("1.20.4"), listOf("Fabric"), listOf(required))
        val result = source(listOf(file), target1204)
        assertEquals(listOf(required.key), result.dependencies.map { it.key })
        assertFalse(result.genuinelyEmpty)
        assertTrue(result.dependencies.single().installable)
        assertTrue(result.dependencies.single().selectedByDefault)
    }

    @Test
    fun anOptionalDependencyIsListedButNotCheckedByDefault() {
        val optional = dep("iris", PlatformDependencyType.OPTIONAL)
        val file = File("sodium.jar", listOf("1.20.4"), listOf("Fabric"), listOf(optional))
        val result = source(listOf(file), target1204)
        assertEquals(listOf(optional.key), result.dependencies.map { it.key })
        assertTrue("可选依赖装得上", result.dependencies.single().installable)
        assertFalse("可选依赖默认不勾，让用户自己决定", result.dependencies.single().selectedByDefault)
    }

    @Test
    fun severalDependenciesAreAllListedInOrder() {
        val deps = listOf(
            dep("fabric-api", PlatformDependencyType.REQUIRED),
            dep("iris", PlatformDependencyType.OPTIONAL),
            dep("modmenu", PlatformDependencyType.TOOL),
        )
        val file = File("sodium.jar", listOf("1.20.4"), listOf("Fabric"), deps)
        val result = source(listOf(file), target1204)
        assertEquals(deps.map { it.key }, result.dependencies.map { it.key })
        assertEquals(
            setOf("fabric-api", "modmenu"),
            discoverDefaultSelection(result.dependencies).map { key ->
                result.dependencies.first { it.key == key }.projectId
            }.toSet()
        )
    }

    @Test
    fun anIncompatibleDependencyIsListedButNotInstallable() {
        val conflicts = dep("optifine", PlatformDependencyType.INCOMPATIBLE)
        val embedded = dep("fabric-language-kotlin", PlatformDependencyType.EMBEDDED)
        val file = File("sodium.jar", listOf("1.20.4"), listOf("Fabric"), listOf(conflicts, embedded))
        val result = source(listOf(file), target1204)
        assertEquals(2, result.dependencies.size)
        assertFalse("明确互斥的不能勾", result.dependencies[0].installable)
        assertFalse("已内嵌的不能勾", result.dependencies[1].installable)
        assertTrue(discoverDefaultSelection(result.dependencies).isEmpty())
    }

    /**
     * 已经装在目标实例里的那条依赖
     *
     * 界面这一层**不查本地文件系统**：那正是安装链路自己的活
     * （`matchInstalledMods` + `_Download.Dependency.Tasks` 里的 `isInstalled`）。
     * 这里能钉死的是它必须与那条链路对"同一个依赖"的判断完全一致——
     * 也就是键一致，因此安装链路跳过的那一条就是界面上那一条，
     * 不会出现"界面上是一条、安装时当成另一条"而被重装。
     */
    @Test
    fun anAlreadyInstalledDependencyKeepsTheIdentityTheDownloaderUses() {
        val installed = dep("fabric-api", PlatformDependencyType.REQUIRED)
        val file = File("sodium.jar", listOf("1.20.4"), listOf("Fabric"), listOf(installed))
        val result = source(listOf(file), target1204)
        val listed = result.dependencies.single()
        assertEquals(
            PlatformVersion.PlatformDependency(
                Platform.CURSEFORGE,
                "fabric-api",
                null,
                PlatformDependencyType.REQUIRED,
            ).cacheKey(),
            listed.key
        )
        // 仍然列出来、仍然装得上（是否已装由安装链路判断，不在这一层）
        assertTrue(listed.installable)
    }

    // ---- 目标这一档没有兼容文件 ------------------------------------------

    /**
     * 这是整个修复的核心那一格：与"没有依赖"必须分得开
     *
     * 之前确认层在这一格给出空依赖列表，界面上于是印出 "This file has no dependencies."，
     * 而详情标签页在同一个项目上退回 `versions.first()`、明明列出了依赖。
     * 用户看到的就是"点安装说没有依赖，可依赖页说有"。
     */
    @Test
    fun aTargetWithoutACompatibleFileIsNotTheSameAsNoDependencies() {
        // 这个文件只支持 1.20.1，而目标是 1.20.4
        val forOtherVersion = File(
            "sodium-old.jar",
            gameVersions = listOf("1.20.1"),
            loaders = listOf("Forge"),
            dependencies = listOf(dep("fabric-api", PlatformDependencyType.REQUIRED)),
        )
        val result = source(listOf(forOtherVersion), target1204)
        assertTrue("目标这一档确实没有兼容文件", result.noFileForTarget)
        assertFalse(result.noFiles)
        assertNull(result.version)
        assertFalse(result.installable)
        assertTrue(
            "这不是\"这个文件没有依赖\"，因此不能报成真的没有依赖",
            !result.genuinelyEmpty,
        )
        // 依赖属于文件而不是项目：这一档的文件都没有，依赖就不列
        assertTrue(result.dependencies.isEmpty())
        // 展示用的那个文件仍然给出来，让用户看见"项目是有文件的，只是没有一份支持你这一档"
        assertSame(forOtherVersion, result.fallbackVersion)
    }

    @Test
    fun theFallbackFileIsOnlyForDisplayAndNeverInstalled() {
        val newest = File("newest.jar", listOf("1.21.1"))
        val other = File("other.jar", listOf("1.20.1"))
        val result = source(listOf(newest, other), target1204)
        assertTrue(result.noFileForTarget)
        assertNull("退回的那个文件绝不能被当成这次要装的文件", result.version)
        assertSame("退回的是最新的那一个，且只用于展示", newest, result.fallbackVersion)
    }

    @Test
    fun aTargetWithoutAMinecraftVersionOnlyFiltersByLoader() {
        // 目标只知道加载器，不知道 Minecraft 版本：这时按加载器挑，而不是报"没有文件"
        val forge = File("forge-only.jar", listOf("1.16.5"), listOf("Forge"))
        val result = source(listOf(forge), target121.copy(loaders = emptySet()))
        assertFalse(result.noFileForTarget)
        assertSame(forge, result.version)
    }

    // ---- 回归：标签页与确认层必须同答 -------------------------------------

    /**
     * 同一个项目 + 同一个目标，两处问出来的依赖键必须逐条相同
     *
     * 这就是用户那句话的可测形式。此前两处各挑各的文件，因此可能一个挑到了
     * 支持目标那一档、另一个退回 `versions.first()`，于是各答各的。
     */
    @Test
    fun theTabAndTheSheetProduceTheSameDependencyKeys() {
        val files = listOf(
            // 最新的那一个只支持 1.21.1，目标 1.20.4 装不上它
            File(
                "newest.jar",
                listOf("1.21.1"),
                listOf("Fabric"),
                listOf(dep("only-for-121", PlatformDependencyType.REQUIRED)),
            ),
            // 目标这一档真正装得上的那一个
            File(
                "for-1204.jar",
                listOf("1.20.4"),
                listOf("Fabric"),
                listOf(
                    dep("fabric-api", PlatformDependencyType.REQUIRED),
                    dep("modmenu", PlatformDependencyType.TOOL),
                ),
            ),
        )
        // 详情标签页问一次
        val tab = source(files, target1204)
        // 安装确认层问一次：同一份列表、同一个目标
        val sheet = source(files, target1204)

        assertSame(tab.version, sheet.version)
        assertEquals(tab.dependencies.map { it.key }, sheet.dependencies.map { it.key })
        assertEquals(
            listOf("fabric-api", "modmenu"),
            tab.dependencies.map { it.projectId },
        )
        assertEquals(tab.noFileForTarget, sheet.noFileForTarget)
    }

    @Test
    fun aProjectWhoseNewestFileFitsTheTargetListsThatFilesDependencies() {
        val files = listOf(
            File("newest.jar", listOf("1.21.1"), listOf("Fabric"), listOf(dep("only-for-121", PlatformDependencyType.REQUIRED))),
            File("for-121.jar", listOf("1.21.1"), listOf("Fabric"), listOf(dep("fabric-api", PlatformDependencyType.REQUIRED))),
        )
        val result = source(files, target121.copy(loaders = setOf("Fabric")))
        assertSame("initAll 之后列表已按发布时间倒序，取第一个装得上的", files[0], result.version)
        assertEquals(listOf("only-for-121"), result.dependencies.map { it.projectId })
    }

    // ---- 缓存 ------------------------------------------------------------

    @Test
    fun theFileListCacheReusesASuccessfulReadAndNeverStoresAFailure() {
        val cache = DiscoverFilesCache<String>()
        assertNull(cache["a"])
        cache["a"] = listOf("v1", "v2")
        assertEquals(listOf("v1", "v2"), cache["a"])
        assertEquals(1, cache.size)
        // 失败不写进去：否则"重试"拿到的还是上一次那个结果
        cache.clear()
        assertNull(cache["a"])
        assertEquals(0, cache.size)
    }

    @Test
    fun theFileListCacheIsBoundedAndEvictsTheLeastRecentlyUsed() {
        val cache = DiscoverFilesCache<String>(capacity = 2)
        cache["a"] = listOf("a")
        cache["b"] = listOf("b")
        cache["a"]
        cache["c"] = listOf("c")
        assertEquals(2, cache.size)
        assertNotNull(cache["a"])
        assertNull("最久没用的那个被挤掉了", cache["b"])
        assertNotNull(cache["c"])
    }
}
