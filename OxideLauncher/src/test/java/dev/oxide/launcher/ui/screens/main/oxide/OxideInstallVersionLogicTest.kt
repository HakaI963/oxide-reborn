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

import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.addons.modloader.cleanroom.CleanroomVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersion
import dev.oxide.launcher.game.versioninfo.MinecraftVersion
import dev.oxide.launcher.game.versioninfo.models.VersionManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 安装流程里被抽出来的那部分纯逻辑
 *
 * 这里钉住的是**能力**，不是外观：版本过滤要能挑出正式版与快照版，
 * 加载器组合必须随 Minecraft 版本变，版本名必须和旧安装页拼得一模一样，
 * "选了加载器却没选 API"这件事要在列表到齐之后才提醒。
 */
class OxideInstallVersionLogicTest {

    private fun version(id: String, type: String) = MinecraftVersion(
        version = VersionManifest.Version(
            id = id,
            type = type,
            url = "https://example.invalid/$id.json",
            time = "2024-01-01T00:00:00Z",
            releaseTime = "2024-01-01T00:00:00Z",
            sha1 = "0".repeat(40),
            complianceLevel = 1,
        ),
        type = when (type) {
            "release" -> MinecraftVersion.Type.Release
            "snapshot" -> MinecraftVersion.Type.Snapshot
            "old_beta" -> MinecraftVersion.Type.OldBeta
            "old_alpha" -> MinecraftVersion.Type.OldAlpha
            else -> MinecraftVersion.Type.Unknown
        },
        summary = null,
    )

    private val allVersions = listOf(
        version("1.21.1", "release"),
        version("1.21", "release"),
        version("24w14potato", "snapshot"),
        version("1.5.2", "old_beta"),
        version("c0.30_01c", "old_alpha"),
    )

    // ---- 版本过滤 ---------------------------------------------------------

    @Test
    fun defaultFilterKeepsReleasesOnly() {
        assertEquals(
            listOf("1.21.1", "1.21"),
            filterOxideVersions(allVersions, OxideVersionFilter()).map { it.version.id },
        )
    }

    @Test
    fun snapshotAndOldAreSeparateSwitches() {
        assertEquals(
            listOf("24w14potato"),
            filterOxideVersions(
                allVersions,
                OxideVersionFilter(release = false, snapshot = true),
            ).map { it.version.id },
        )
        assertEquals(
            listOf("1.5.2", "c0.30_01c"),
            filterOxideVersions(
                allVersions,
                OxideVersionFilter(release = false, old = true),
            ).map { it.version.id },
        )
    }

    @Test
    fun searchMatchesOnTheVersionIdOnly() {
        assertEquals(
            listOf("24w14potato"),
            filterOxideVersions(
                allVersions,
                OxideVersionFilter(release = false, snapshot = true, id = "potato"),
            ).map { it.version.id },
        )
        assertEquals(
            "空搜索框不能把列表收窄",
            3,
            filterOxideVersions(
                allVersions,
                OxideVersionFilter(release = true, snapshot = true, id = "   "),
            ).size,
        )
    }

    @Test
    fun typeFilterAndSearchCombineAsAnd() {
        // 搜索值不存在时，即便类型对得上也必须什么都不剩
        assertEquals(
            emptyList<String>(),
            filterOxideVersions(allVersions, OxideVersionFilter(id = "1.99")).map { it.version.id },
        )
        // 搜索命中的版本若不是当前放行的类型，仍然要落选
        assertEquals(
            emptyList<String>(),
            filterOxideVersions(allVersions, OxideVersionFilter(id = "potato")).map { it.version.id },
        )
        // 只留真正命中的那一个
        assertEquals(
            listOf("1.21.1"),
            filterOxideVersions(allVersions, OxideVersionFilter(id = "21.1")).map { it.version.id },
        )
    }

    // ---- 加载器组合 -------------------------------------------------------

    @Test
    fun modernVersionsOfferFabricAndQuiltButNotLegacyFabric() {
        val plan = oxideAddonPlan("1.20.1").map { it.loader }
        assertTrue(ModLoader.FABRIC in plan)
        assertTrue(ModLoader.QUILT in plan)
        assertFalse("1.13.2 以上没有 Legacy Fabric", ModLoader.LEGACY_FABRIC in plan)
        // 1.20.1 已经越过 1.20，NeoForge 在这里必须可选（边界由 neoForgeOnlyAppearsFrom120 钉住）
        assertTrue("1.20.1 上必须有 NeoForge", ModLoader.NEOFORGE in plan)
    }

    @Test
    fun neoForgeOnlyAppearsFrom120() {
        assertFalse(ModLoader.NEOFORGE in oxideAddonPlan("1.19.4").map { it.loader })
        assertTrue(ModLoader.NEOFORGE in oxideAddonPlan("1.20.1").map { it.loader })
    }

    @Test
    fun cleanroomOnlyAppearsOn1122() {
        assertTrue(ModLoader.CLEANROOM in oxideAddonPlan("1.12.2").map { it.loader })
        assertFalse(ModLoader.CLEANROOM in oxideAddonPlan("1.12.1").map { it.loader })
    }

    @Test
    fun legacyFabricOnlyAppearsBelow1132() {
        val plan = oxideAddonPlan("1.12.2").map { it.loader }
        assertTrue(ModLoader.LEGACY_FABRIC in plan)
        assertTrue(ModLoader.LEGACY_FABRIC_API in plan)
        assertFalse(ModLoader.FABRIC in plan)
        assertFalse(ModLoader.QUILT in plan)
    }

    @Test
    fun anApiModAlwaysFollowsItsOwnLoader() {
        val plan = oxideAddonPlan("1.20.1")
        val fabricAt = plan.indexOfFirst { it.loader == ModLoader.FABRIC }
        val fabricApiAt = plan.indexOfFirst { it.loader == ModLoader.FABRIC_API }
        assertTrue("1.20.1 上必须有 Fabric", fabricAt >= 0)
        assertEquals("Fabric API 必须紧跟在 Fabric 后面", fabricAt + 1, fabricApiAt)
        assertEquals(
            "API 必须声明自己依附的是哪个加载器",
            ModLoader.FABRIC,
            plan[fabricApiAt].apiOf,
        )
    }

    @Test
    fun optifineAndForgeAreAlwaysOffered() {
        val plan = oxideAddonPlan("1.7.10").map { it.loader }
        assertTrue(ModLoader.OPTIFINE in plan)
        assertTrue(ModLoader.FORGE in plan)
    }

    // ---- 版本名 -----------------------------------------------------------

    /**
     * 列表里真实的 OptiFine 条目
     *
     * [OptiFineVersion.displayName] 是解析之后的样子：`OptiFine_1.12.2_HD_U_C1`
     * 去掉 `OptiFine ` 与 `HD U ` 之后就是 [OPTIFINE_DISPLAY]，
     * 所以 [OptiFineVersion.realVersion]（`removePrefix(inherit).trim()`）正好是 `C1`。
     */
    private const val OPTIFINE_DISPLAY = "1.12.2 C1"

    private fun optifine(displayName: String, forgeRequirement: String?): OptiFineVersion =
        OptiFineVersion(
            displayName = displayName,
            fileName = "OptiFine_${displayName.replace(' ', '_')}.jar",
            version = displayName,
            inherit = "1.12.2",
            releaseDate = "2020/01/01",
            forgeVersion = forgeRequirement,
            isPreview = false,
        )

    private fun forge(versionName: String, category: String = "installer") = ForgeVersion(
        versionName = versionName,
        branch = null,
        inherit = "1.12.2",
        releaseTime = "2020/01/01 00:00",
        hash = null,
        isRecommended = false,
        category = category,
        fileVersion = versionName,
    )

    @Test
    fun withoutAnyLoaderTheNameIsJustTheVersion() {
        assertEquals(
            "1.20.1",
            oxideInstallVersionName(
                gameVersion = "1.20.1",
                optifine = null,
                forge = null,
                neoforge = null,
                fabric = null,
                legacyFabric = null,
                quilt = null,
                cleanroom = null,
            ),
        )
    }

    @Test
    fun loaderVersionIsAppendedToTheGameVersion() {
        assertEquals(
            "1.20.1 NeoForge 20.4.190",
            oxideInstallVersionName(
                gameVersion = "1.20.1",
                optifine = null,
                forge = null,
                neoforge = NeoForgeVersion(rawVersion = "20.4.190", isLegacyForge = false),
                fabric = null,
                legacyFabric = null,
                quilt = null,
                cleanroom = null,
            ),
        )
    }

    @Test
    fun cleanroomUsesItsOwnLoaderName() {
        assertEquals(
            "1.12.2 Cleanroom 1.0",
            oxideInstallVersionName(
                gameVersion = "1.12.2",
                optifine = null,
                forge = null,
                neoforge = null,
                fabric = null,
                legacyFabric = null,
                quilt = null,
                cleanroom = CleanroomVersion(version = "1.0", createdAt = Instant.EPOCH),
            ),
        )
    }

    @Test
    fun optifineAloneKeepsOnlyItsRealVersionPart() {
        assertEquals(
            "1.12.2 OptiFine C1",
            oxideInstallVersionName(
                gameVersion = "1.12.2",
                optifine = optifine(OPTIFINE_DISPLAY, null),
                forge = null,
                neoforge = null,
                fabric = null,
                legacyFabric = null,
                quilt = null,
                cleanroom = null,
            ),
        )
    }

    @Test
    fun optifineAndForgeKeepTheOldDashOrder() {
        assertEquals(
            "1.12.2 Forge 14.23.5.2859-OptiFine C1",
            oxideInstallVersionName(
                gameVersion = "1.12.2",
                optifine = optifine(OPTIFINE_DISPLAY, "14.23.5.2859"),
                forge = forge("14.23.5.2859"),
                neoforge = null,
                fabric = null,
                legacyFabric = null,
                quilt = null,
                cleanroom = null,
            ),
        )
    }

    // ---- OptiFine / Forge 互相过滤 ---------------------------------------

    @Test
    fun withoutASelectionNothingIsFilteredAway() {
        val items = listOf(optifine("a", "14.23.5.2859"), optifine("b", null))
        // 没选 Forge：一条都不能少，哪怕这一条根本没声明需要哪个 Forge
        assertEquals(2, filterOptiFineAgainstForge(items, null).size)
        // 反过来也一样：没选 OptiFine 时 Forge 列表原样保留
        val forges = listOf(forge("14.23.5.2859"), forge("14.23.5.2858"))
        assertEquals(
            listOf("14.23.5.2859", "14.23.5.2858"),
            filterForgeAgainstOptiFine(forges, null).map { it.versionName },
        )
    }

    @Test
    fun optifineWithoutADeclaredForgeRequirementIsDropped() {
        val items = listOf(optifine("declared", "14.23.5.2859"), optifine("undeclared", null))
        assertEquals(
            listOf("declared"),
            filterOptiFineAgainstForge(items, forge("14.23.5.2859")).map { it.displayName },
        )
    }

    @Test
    fun optifineWithAnEmptyRequirementMatchesEveryForge() {
        val items = listOf(optifine("any", ""))
        assertEquals(
            "空字符串表示兼容所有 Forge",
            1,
            filterOptiFineAgainstForge(items, forge("14.23.5.2859")).size,
        )
    }

    @Test
    fun forgeListDropsVersionsThatCannotBeInstalledAutomatically() {
        val items = listOf(
            forge("14.23.5.2859", category = "installer"),
            forge("14.23.5.2858", category = "installer"),
            forge("universal-1", category = "universal"),
            forge("client-1", category = "client"),
        )
        assertTrue(forgeHasUninstallableEntries(items))
        assertEquals(
            listOf("14.23.5.2859", "14.23.5.2858"),
            forgeInstallableVersions(items).map { it.versionName },
        )
        assertFalse(forgeHasUninstallableEntries(forgeInstallableVersions(items)))
        assertFalse("列表为空时不谎报有装不了的版本", forgeHasUninstallableEntries(null))
    }

    // ---- API 提醒 ---------------------------------------------------------

    @Test
    fun apiWarningNeedsALoaderAnEmptyChoiceAndALoadedList() {
        assertTrue(
            oxideNeedsApiWarning(
                loaderChosen = true,
                apiChosen = false,
                apiListLoaded = true,
                apiListEmpty = false,
            )
        )
    }

    @Test
    fun apiWarningStaysSilentInEveryOtherCase() {
        val cases = listOf(
            // 没选加载器：不构成"忘了选 API"
            oxideNeedsApiWarning(false, false, true, false),
            // 已经选了 API
            oxideNeedsApiWarning(true, true, true, false),
            // 列表还没到齐，说了也选不了
            oxideNeedsApiWarning(true, false, false, false),
            // 这个版本根本没有 API
            oxideNeedsApiWarning(true, false, true, true),
        )
        assertTrue("这些组合都不该提醒：$cases", cases.none { it })
    }

    // ---- 步骤可达性 -------------------------------------------------------

    @Test
    fun loadersAndInstallAreUnreachableWithoutAVersion() {
        assertTrue(oxideInstallStepReachable(OxideInstallStep.Version, null))
        assertFalse(oxideInstallStepReachable(OxideInstallStep.Loader, null))
        assertFalse(oxideInstallStepReachable(OxideInstallStep.Install, null))
        assertFalse(oxideInstallStepReachable(OxideInstallStep.Loader, ""))
    }

    @Test
    fun loadersAndInstallOpenUpOnceAVersionIsChosen() {
        assertTrue(oxideInstallStepReachable(OxideInstallStep.Loader, "1.20.1"))
        assertTrue(oxideInstallStepReachable(OxideInstallStep.Install, "1.20.1"))
    }
}