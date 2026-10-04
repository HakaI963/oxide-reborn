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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安装目标解析与文件匹配的单测
 *
 * 线上缺陷的形状：用户装的是 26.3（当时最新正式版），打开一个模组点安装，
 * 启动器报"这个项目不支持 26.3"，而且这一层里根本没有换目标版本的控件——
 * 用户既看不到装到哪一档，也改不了。因此下面三件事必须钉死：
 *
 *  1. 目标只能是**用户选中的实例 / 用户选的版本 / 当前可玩实例**，
 *     模组自己声明的那一档 Minecraft 版本（平台文件版本里的 gameVersion）永远不参与；
 *  2. 实例名永远不会被当成 Minecraft 版本（自定义名、整合包名、加载器 id 都可能是它）；
 *  3. 挑文件用的是**目标实例自己的**加载器，不是搜索栏上的加载器筛选，
 *     否则挑中的文件到了下载那一步会被判成不兼容。
 */
class OxideDiscoverTargetTest {

    private val vanilla263 = DiscoverInstanceTarget(name = "26.3", minecraftVersion = "26.3")
    private val fabric1204 =
        DiscoverInstanceTarget(name = "fabric-loader-0.16.9-1.20.4", minecraftVersion = "1.20.4", loaders = setOf("Fabric"))
    private val forge1201 =
        DiscoverInstanceTarget(name = "1.20.1-forge-48.1.0", minecraftVersion = "1.20.1", loaders = setOf("Forge"))
    private val installed = listOf(vanilla263, fabric1204, forge1201)

    // ---- 目标从哪来 --------------------------------------------------------

    /**
     * 核心优先级：用户明确选中的实例压过他在确认层里选的版本，
     * 也压过模组自己声明的那一档 Minecraft 版本
     */
    @Test
    fun theSelectedInstanceWinsOverEverythingElse() {
        val target = discoverResolveTarget(
            selectedInstance = fabric1204,
            pickedVersionName = "1.21.1",
            playableInstance = vanilla263,
            installedInstances = installed,
            pinnedProjectVersion = "3.2.1 (1.21.1)",
        )
        assertEquals(fabric1204.name, target.instanceName)
        assertEquals("1.20.4", target.minecraftVersion)
        assertEquals(setOf("Fabric"), target.loaders)
        assertTrue(target.installed)
    }

    /**
     * 模组自己声明的版本永远不成为目标。
     *
     * 这是"不支持 26.3"那一类缺陷的根：目标槽位上如果放进的是模组/加载器/版本文件的
     * 那一档字符串，平台上必然一个文件都匹配不上，于是报出一个自己造出来的理由。
     */
    @Test
    fun theProjectsOwnVersionNeverBecomesTheTarget() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = null,
            playableInstance = null,
            installedInstances = installed,
            pinnedProjectVersion = "3.2.1 (1.21.1)",
        )
        assertNull(target.instanceName)
        assertNull(target.minecraftVersion)
        assertFalse(target.hasTarget)
    }

    /** 一个字节都没选时：目标就是当前选中的可玩实例 */
    @Test
    fun withNothingPickedThePlayableInstanceIsTheTarget() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = null,
            playableInstance = fabric1204,
            installedInstances = installed,
        )
        assertEquals(fabric1204.name, target.instanceName)
        assertEquals("1.20.4", target.minecraftVersion)
        assertTrue(target.installed)
        assertTrue(target.detected)
    }

    /** 重新选中当前那个实例，仍然算"没有改" */
    @Test
    fun pickingThePlayableInstanceAgainIsStillDetected() {
        val target = discoverResolveTarget(
            selectedInstance = fabric1204,
            pickedVersionName = fabric1204.name,
            playableInstance = fabric1204,
            installedInstances = installed,
        )
        assertEquals(fabric1204.name, target.instanceName)
        assertTrue(target.detected)
    }

    /** 选了别的实例就是改过了 */
    @Test
    fun pickingAnotherInstanceIsNotDetected() {
        val target = discoverResolveTarget(
            selectedInstance = forge1201,
            pickedVersionName = forge1201.name,
            playableInstance = fabric1204,
            installedInstances = installed,
        )
        assertEquals(forge1201.name, target.instanceName)
        assertFalse(target.detected)
    }

    /** 选了版本表里、本地还没装的一档：目标是那一档 Minecraft 版本，并说清要连游戏版本一起装 */
    @Test
    fun aPickedVersionThatIsNotInstalledNeedsTheGameVersion() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = "1.20.4",
            playableInstance = vanilla263,
            installedInstances = installed,
        )
        assertNull(target.instanceName)
        assertEquals("1.20.4", target.minecraftVersion)
        // 还没装，因此确认层要问用户；此时目标就是一个纯版本，没有实例的加载器
        assertFalse(target.installed)
        assertTrue(target.loaders.isEmpty())
        assertTrue(target.hasTarget)
        // 当前可玩实例是 26.3，所以这一档确实算"改过了"
        assertFalse(target.detected)
    }

    /**
     * 选中的条目本身就是一个已存在的实例时（实例名而不是版本号），
     * 它连同自己的 Minecraft 版本与加载器一起生效——不是"当前可玩实例"的加载器
     */
    @Test
    fun aPickedEntryThatIsAnInstalledInstanceKeepsItsOwnLoader() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = forge1201.name,
            playableInstance = fabric1204,
            installedInstances = installed,
        )
        assertEquals(forge1201.name, target.instanceName)
        assertEquals("1.20.1", target.minecraftVersion)
        assertEquals(setOf("Forge"), target.loaders)
        assertTrue(target.installed)
        assertFalse(target.detected)
    }

    /** 什么都没选也没有实例：真的没有目标，界面据此引导去实例页 */
    @Test
    fun noInstanceAndNoPickIsNoTarget() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = null,
            playableInstance = null,
            installedInstances = emptyList(),
        )
        assertFalse(target.hasTarget)
        assertEquals(EMPTY_DISCOVER_TARGET, target)
    }

    /** 空串与空白都当"没选" */
    @Test
    fun blankPicksAreNoPicks() {
        listOf(null, "", "   ").forEach { picked ->
            val target = discoverResolveTarget(
                selectedInstance = null,
                pickedVersionName = picked,
                playableInstance = vanilla263,
                installedInstances = installed,
            )
            assertEquals(vanilla263.name, target.instanceName)
            assertTrue(target.detected)
        }
    }

    /**
     * 实例名永远不会被拿去当 Minecraft 版本
     *
     * 自定义名（"My Modpack 1.2"）、加载器 id（"fabric-loader-0.16.9-1.20.4"）
     * 都是文件夹名，不是 Minecraft 版本。版本 JSON 解析不出 Minecraft 版本时，
     * 目标就是"还不知道"（null），而不是那个字符串。
     */
    @Test
    fun anInstanceNameIsNeverUsedAsAMinecraftVersion() {
        val unparsed = DiscoverInstanceTarget(name = "My Modpack 1.2", minecraftVersion = null)
        val target = discoverResolveTarget(
            selectedInstance = unparsed,
            pickedVersionName = null,
            playableInstance = unparsed,
            installedInstances = listOf(unparsed),
        )
        assertEquals("My Modpack 1.2", target.instanceName)
        assertNull(target.minecraftVersion)
    }

    // ---- 挑文件 ------------------------------------------------------------

    /** 测试用的"文件版本"：只带挑文件需要的两个字段，与平台模型无关 */
    private data class FakeFile(
        val name: String,
        val gameVersions: List<String>,
        val loaders: List<String> = emptyList(),
    ) {
        fun facts(): DiscoverFileFacts = DiscoverFileFacts(gameVersions = gameVersions, loaders = loaders)
    }

    /** initAll 已经按发布时间倒序排好，所以列表第一个就是最新的 */
    private val files = listOf(
        FakeFile("fabric-1.21.1", listOf("1.21.1"), listOf("Fabric")),
        FakeFile("forge-1.21.1", listOf("1.21.1"), listOf("Forge")),
        FakeFile("fabric-1.20.4", listOf("1.20.4", "1.20.2"), listOf("Fabric")),
        FakeFile("vanilla-1.20.4", listOf("1.20.4", "1.20.2")),
        FakeFile("vanilla-1.19.2", listOf("1.19.2")),
    )

    private fun pick(
        minecraftVersion: String?,
        loaders: Set<String> = emptySet(),
        from: List<FakeFile> = files,
    ) = discoverCompatibleFile(from, minecraftVersion, loaders) { it.facts() }

    @Test
    fun theNewestFileForTheTargetVersionWins() {
        assertEquals("fabric-1.21.1", pick("1.21.1")?.name)
        assertEquals("vanilla-1.19.2", pick("1.19.2")?.name)
    }

    /**
     * 目标实例是 Fabric 就不能挑到 Forge 的文件：
     * 文件搜索必须听目标实例的，而不是搜索栏上那个"加载器"筛选
     */
    @Test
    fun theTargetsOwnLoaderDecidesWhichFileFits() {
        assertEquals("fabric-1.21.1", pick("1.21.1", setOf("Fabric"))?.name)
        assertEquals("forge-1.21.1", pick("1.21.1", setOf("Forge"))?.name)
    }

    /** 目标带多个加载器时任一匹配即可（与下载链路自己的判定一致） */
    @Test
    fun anyOfTheTargetsLoadersIsEnough() {
        assertEquals("forge-1.21.1", pick("1.21.1", setOf("Quilt", "Forge"))?.name)
    }

    /** 加载器名按平台给的大小写匹配，不挑错文件 */
    @Test
    fun loaderMatchingIgnoresCase() {
        assertEquals("fabric-1.20.4", pick("1.20.4", setOf("fabric"))?.name)
    }

    /** 目标这一档没有任何文件时如实返回 null，界面据此说"没有文件"并让用户改版本 */
    @Test
    fun noFileForTheTargetVersionIsNull() {
        // 26.3 是当时最新的正式版，而没有模组支持它——这正是线上那条报错的成因
        assertNull(pick("26.3"))
        assertNull(pick("26.3", setOf("Fabric")))
    }

    /**
     * 目标 Minecraft 版本还不知道时不按版本过滤，只按加载器挑
     *
     * 这是"解析不出 Minecraft 版本"的唯一合法行为：宁可不筛版本，
     * 也不能拿实例名或别的字符串填进这个槽位。
     */
    @Test
    fun anUnknownTargetVersionOnlyFiltersByLoader() {
        assertEquals("fabric-1.21.1", pick(null, setOf("Fabric"))?.name)
        assertEquals("forge-1.21.1", pick(null, setOf("Forge"))?.name)
        assertEquals("fabric-1.21.1", pick(null)?.name)
    }

    /**
     * 纯原版目标（没有加载器）不按加载器排除文件
     *
     * 与依赖下载链路自己的兼容性判定保持一致：目标没有加载器时一律放行。
     * 反过来做（像旧的资源页那样判成不适配）会让这里挑中的文件在下载那一步被拒收。
     */
    @Test
    fun aTargetWithoutALoaderDoesNotRejectTaggedFiles() {
        assertSame(files[0], pick("1.21.1"))
    }

    /** 空列表与空白目标都不该抛异常，也不该凭空造出结果 */
    @Test
    fun degenerateInputsAreHandled() {
        assertNull(pick("1.21.1", from = emptyList()))
        // 全空的目标等于"还不知道"，于是只按发布时间取最新的那一个
        assertEquals("fabric-1.21.1", pick("  ", setOf("  "))?.name)
        assertEquals("fabric-1.21.1", pick("1.21.1", setOf("  "))?.name)
    }

    /** 目标从实例解析出来之后，文件匹配用的就是它自己的那一档 */
    @Test
    fun aTargetBuiltFromAnInstancePicksThatInstancesFile() {
        val target = discoverResolveTarget(
            selectedInstance = null,
            pickedVersionName = null,
            playableInstance = fabric1204,
            installedInstances = installed,
        )
        assertEquals("fabric-1.20.4", pick(target.minecraftVersion, target.loaders)?.name)
    }
}
