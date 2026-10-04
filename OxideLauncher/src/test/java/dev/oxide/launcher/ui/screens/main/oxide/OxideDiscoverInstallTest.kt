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
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformReleaseType
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.cacheKey
import dev.oxide.launcher.ui.AndroidStringText
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安装前确认与依赖的单测
 *
 * 三件事必须钉死：
 *  - 只有模组与整合包会弹这一层（别的类别直接装）；
 *  - 目标 Minecraft 版本默认取当前实例，但用户改过之后以用户的为准；
 *  - "全部下载"这一步里装的是哪些依赖、要不要连游戏版本一起装。
 */
class OxideDiscoverInstallTest {

    // ---- 类别门槛 ---------------------------------------------------------

    @Test
    fun onlyModsAndModpacksOpenTheInstallSheet() {
        assertTrue(discoverUsesInstallSheet(PlatformClasses.MOD))
        assertTrue(discoverUsesInstallSheet(PlatformClasses.MOD_PACK))
    }

    /** 光影、资源包、存档、地图是整包放进固定目录的，没有版本与依赖的概念 */
    @Test
    fun theOtherCategoriesInstallDirectly() {
        assertFalse(discoverUsesInstallSheet(PlatformClasses.SHADERS))
        assertFalse(discoverUsesInstallSheet(PlatformClasses.RESOURCE_PACK))
        assertFalse(discoverUsesInstallSheet(PlatformClasses.SAVES))
    }

    @Test
    fun everyClassIsClassifiedOneWayOrTheOther() {
        PlatformClasses.entries.forEach { classes ->
            val expected = classes == PlatformClasses.MOD || classes == PlatformClasses.MOD_PACK
            assertEquals(classes.name, expected, discoverUsesInstallSheet(classes))
        }
    }

    // ---- 版本：自动检测 vs 用户选择 ------------------------------------------

    @Test
    fun theVersionIsDetectedFromTheCurrentInstanceByDefault() {
        val choice = discoverResolveVersion(chosen = null, detected = "1.20.1")
        assertEquals("1.20.1", choice.name)
        assertTrue(choice.detected)
    }

    @Test
    fun aVersionPickedByTheUserWins() {
        val choice = discoverResolveVersion(chosen = "1.21.1", detected = "1.20.1")
        assertEquals("1.21.1", choice.name)
        assertFalse(choice.detected)
    }

    /** 选了跟自动检测一样的那个，仍然算"没有改" */
    @Test
    fun pickingTheDetectedVersionAgainIsStillDetected() {
        assertTrue(discoverResolveVersion("1.20.1", "1.20.1").detected)
    }

    @Test
    fun thereIsNoTargetWhenNeitherSideHasAVersion() {
        val choice = discoverResolveVersion(chosen = "  ", detected = null)
        assertNull(choice.name)
        assertFalse(choice.detected)
    }

    @Test
    fun anUninstalledTargetVersionNeedsTheGameInstalledFirst() {
        assertTrue(discoverNeedsGameVersion("1.21.1", setOf("1.20.1")))
        assertFalse(discoverNeedsGameVersion("1.20.1", setOf("1.20.1")))
        // 没有目标版本就没有"要不要装游戏"这件事
        assertFalse(discoverNeedsGameVersion(null, setOf("1.20.1")))
    }

    // ---- 依赖：类型与默认选择 -----------------------------------------------

    private val requiredDep = dep(Platform.MODRINTH, "fabric-api", null, PlatformDependencyType.REQUIRED)
    private val toolDep = dep(Platform.MODRINTH, "modmenu", null, PlatformDependencyType.TOOL)
    private val optionalDep = dep(Platform.MODRINTH, "opt", null, PlatformDependencyType.OPTIONAL)
    private val includeDep = dep(Platform.MODRINTH, "inc", null, PlatformDependencyType.INCLUDE)
    private val embeddedDep = dep(Platform.MODRINTH, "embedded-lib", null, PlatformDependencyType.EMBEDDED)
    private val incompatibleDep = dep(Platform.MODRINTH, "old-lib", null, PlatformDependencyType.INCOMPATIBLE)

    private val allDeps =
        listOf(requiredDep, toolDep, optionalDep, includeDep, embeddedDep, incompatibleDep)

    /** 已内嵌的与明确互斥的依赖永远不能被勾上，否则装出来就是坏的 */
    @Test
    fun embeddedAndIncompatibleDependenciesAreNeverInstallable() {
        assertFalse(embeddedDep.installable)
        assertFalse(incompatibleDep.installable)
        assertFalse(PlatformDependencyType.EMBEDDED.discoverInstallable())
        assertFalse(PlatformDependencyType.INCOMPATIBLE.discoverInstallable())
        listOf(
            PlatformDependencyType.REQUIRED,
            PlatformDependencyType.OPTIONAL,
            PlatformDependencyType.TOOL,
            PlatformDependencyType.INCLUDE,
        ).forEach { assertTrue(it.name, it.discoverInstallable()) }
    }

    @Test
    fun requiredAndToolDependenciesAreSelectedByDefault() {
        assertTrue(PlatformDependencyType.REQUIRED.discoverSelectedByDefault())
        assertTrue(PlatformDependencyType.TOOL.discoverSelectedByDefault())
        assertFalse(PlatformDependencyType.OPTIONAL.discoverSelectedByDefault())
        assertFalse(PlatformDependencyType.INCLUDE.discoverSelectedByDefault())
    }

    @Test
    fun theDefaultSelectionIsExactlyRequiredAndTool() {
        val selected = discoverDefaultSelection(allDeps)
        assertEquals(setOf(requiredDep.key, toolDep.key), selected)
    }

    @Test
    fun theUserCanAddAndRemoveOptionalDependencies() {
        var selected = discoverDefaultSelection(allDeps)
        selected = discoverToggleSelection(selected, optionalDep, true)
        assertTrue(optionalDep.key in selected)
        selected = discoverToggleSelection(selected, optionalDep, false)
        assertFalse(optionalDep.key in selected)
        assertEquals(setOf(requiredDep.key, toolDep.key), selected)
    }

    /** 点一个灰掉的依赖不应该悄悄改掉已装依赖的选择 */
    @Test
    fun anUninstallableDependencyIgnoresTheToggle() {
        val selected = discoverDefaultSelection(allDeps)
        assertEquals(selected, discoverToggleSelection(selected, embeddedDep, true))
        assertEquals(selected, discoverToggleSelection(selected, incompatibleDep, true))
        assertFalse(embeddedDep.key in selected)
        assertFalse(incompatibleDep.key in selected)
    }

    @Test
    fun dependenciesAreReadFromTheRealPlatformRelations() {
        val dependencies = discoverDependenciesOf(
            fakeVersion(
                dependencies = listOf(
                    PlatformVersion.PlatformDependency(Platform.MODRINTH, "fabric-api", null, PlatformDependencyType.REQUIRED),
                    PlatformVersion.PlatformDependency(Platform.MODRINTH, "modmenu", "v1", PlatformDependencyType.OPTIONAL),
                ),
            ),
        )
        assertEquals(2, dependencies.size)
        assertEquals("fabric-api", dependencies[0].projectId)
        assertEquals("v1", dependencies[1].versionId)
    }

    /** 平台把同一条关系重复给出时只留一条，否则勾选状态会对不上 */
    @Test
    fun repeatedDependencyRelationsAreDeduplicated() {
        val dependencies = discoverDependenciesOf(
            fakeVersion(
                dependencies = listOf(
                    PlatformVersion.PlatformDependency(Platform.MODRINTH, "fabric-api", null, PlatformDependencyType.REQUIRED),
                    PlatformVersion.PlatformDependency(Platform.MODRINTH, "fabric-api", null, PlatformDependencyType.REQUIRED),
                ),
            ),
        )
        assertEquals(1, dependencies.size)
    }

    /** 选择键必须与依赖下载链路自己的去重键一致，否则界面勾了两条只会装一个 */
    @Test
    fun theSelectionKeyMatchesTheDependencyPipelineKey() {
        val key = requiredDep.key
        val pipeline = PlatformVersion.PlatformDependency(
            Platform.MODRINTH,
            requiredDep.projectId,
            requiredDep.versionId,
            requiredDep.type,
        ).cacheKey()
        assertEquals(pipeline, key)
    }

    // ---- "全部下载"计划 ------------------------------------------------------

    @Test
    fun thePlanInstallsTheSelectedDependenciesOnly() {
        val plan = discoverBuildPlan(
            classes = PlatformClasses.MOD,
            detectedVersion = "1.20.1",
            chosenVersion = null,
            installedVersions = setOf("1.20.1"),
            dependencies = allDeps,
            selectedDependencyKeys = setOf(requiredDep.key, optionalDep.key, embeddedDep.key),
        )
        assertEquals(listOf(requiredDep.key, optionalDep.key), plan.dependencyKeys)
        assertTrue(plan.hasDependencies)
        assertEquals("1.20.1", plan.targetVersionName)
        assertFalse(plan.installGameVersion)
    }

    @Test
    fun aModPlanAsksToInstallTheGameWhenTheTargetIsMissing() {
        val plan = discoverBuildPlan(
            classes = PlatformClasses.MOD,
            detectedVersion = "1.20.1",
            chosenVersion = "1.21.1",
            installedVersions = setOf("1.20.1"),
            dependencies = emptyList(),
            selectedDependencyKeys = emptySet(),
        )
        assertEquals("1.21.1", plan.targetVersionName)
        assertTrue(plan.installGameVersion)
        assertFalse(plan.hasDependencies)
    }

    /** 没有目标版本时不能编一个"要装游戏"出来 */
    @Test
    fun aPlanWithoutATargetNeverAsksForAGameInstall() {
        val plan = discoverBuildPlan(
            classes = PlatformClasses.MOD,
            detectedVersion = null,
            chosenVersion = "",
            installedVersions = emptySet(),
            dependencies = allDeps,
            selectedDependencyKeys = discoverDefaultSelection(allDeps),
        )
        assertNull(plan.targetVersionName)
        assertFalse(plan.hasTarget)
        assertFalse(plan.installGameVersion)
        // 依赖选择仍然照旧，只是不再有目标
        assertEquals(2, plan.dependencyKeys.size)
    }

    /**
     * 整合包装成独立实例，自带游戏版本与加载器，
     * 而且整合包对应的安装目录是 NONE，依赖下载链路会直接拒收——所以两样都不带
     */
    @Test
    fun aModpackPlanOnlyCarriesTheNewInstanceName() {
        val plan = discoverBuildPlan(
            classes = PlatformClasses.MOD_PACK,
            detectedVersion = "1.20.1",
            chosenVersion = "1.21.1",
            installedVersions = emptySet(),
            dependencies = allDeps,
            selectedDependencyKeys = discoverDefaultSelection(allDeps),
            instanceName = "All The Modpacks",
        )
        assertNull(plan.targetVersionName)
        assertFalse(plan.installGameVersion)
        assertTrue(plan.dependencyKeys.isEmpty())
        assertEquals("All The Modpacks", plan.instanceName)
    }

    @Test
    fun aModpackWithoutANameHasNothingToInstall() {
        val plan = discoverBuildPlan(
            classes = PlatformClasses.MOD_PACK,
            detectedVersion = null,
            chosenVersion = null,
            installedVersions = emptySet(),
            dependencies = emptyList(),
            selectedDependencyKeys = emptySet(),
            instanceName = "   ",
        )
        assertNull(plan.instanceName)
        assertNull(plan.targetVersionName)
    }

    // ---- 每个依赖自己的状态 --------------------------------------------------

    @Test
    fun onlyAFailedDependencyCanBeRetriedOnItsOwn() {
        assertTrue(DiscoverDependencyState.Failed(AndroidStringText.Text("x")).retryable())
        assertFalse(DiscoverDependencyState.Resolving.retryable())
        assertFalse(DiscoverDependencyState.Resolved("1.0", "1.20.1").retryable())
        assertFalse(DiscoverDependencyState.NoCompatibleVersion.retryable())
    }

    /** 一个依赖失败不能把其余依赖一起判死，所以状态是逐条持有的 */
    @Test
    fun dependencyStatesAreIndependent() {
        val states = listOf(
            "a" to DiscoverDependencyState.Resolved("1.0", "1.20.1"),
            "b" to DiscoverDependencyState.Failed(AndroidStringText.Text("timeout")),
            "c" to DiscoverDependencyState.NoCompatibleVersion,
        )
        assertTrue(states.first { it.first == "a" }.second.retryable().not())
        assertTrue(states.first { it.first == "b" }.second.retryable())
        assertEquals(3, states.size)
    }

    // ---- 工具 ---------------------------------------------------------------

    private fun dep(
        platform: Platform,
        projectId: String?,
        versionId: String?,
        type: PlatformDependencyType,
    ) = DiscoverDependency(platform, projectId, versionId, type)

    /** 只提供依赖列表的假文件版本，其余字段在这组测试里没有意义 */
    private fun fakeVersion(
        dependencies: List<PlatformVersion.PlatformDependency> = emptyList(),
    ): PlatformVersion = object : PlatformVersion {
        override suspend fun initFile(currentProjectId: String): Boolean = true
        override fun platform(): Platform = Platform.MODRINTH
        override fun platformId(): String = "file-1"
        override fun platformProjectId(): String = "project-1"
        override fun platformDisplayName(): String = "file 1"
        override fun platformFileName(): String = "file.jar"
        override fun platformGameVersion(): Array<String> = arrayOf("1.20.1")
        override fun platformLoaders(): List<PlatformDisplayLabel> = emptyList()
        override fun platformReleaseType(): PlatformReleaseType = PlatformReleaseType.RELEASE
        override fun platformDependencies(): List<PlatformVersion.PlatformDependency> = dependencies
        override fun platformDownloadCount(): Long = 0
        override fun platformDownloadUrl(): String = ""
        override fun platformDatePublished(): Instant = Instant.EPOCH
        override fun platformSha1(): String? = null
        override fun platformFileSize(): Long = 0
        override fun platformVersion(): String = "1.0.0"
    }
}