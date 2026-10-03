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
import dev.oxide.launcher.game.addons.modloader.fabriclike.fabric.FabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersion
import dev.oxide.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.forge.ForgeVersion
import dev.oxide.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersion
import dev.oxide.launcher.ui.screens.content.download.game.CurrentAddon
import dev.oxide.launcher.viewmodel.ModifyDiffs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 修改版本流程里被抽出来的那部分纯逻辑
 *
 * 这里钉住的是**能力**，不是外观：
 * - 这一页能改哪些加载器（永远不含 API 模组，因为旧向导的请求里一个 API 字段都没写）；
 * - 变更内容什么时候算得出来、什么时候必须什么都不算（这是最容易被改坏的一处）；
 * - 改名放不放行；
 * - 步骤可不可达；
 * - 最终组装出去的修改请求是不是逐字段等价于旧向导。
 */
class OxideModifyVersionLogicTest {

    // ---- 小工具 -----------------------------------------------------------

    private fun installed(loader: ModLoader, version: String) = OxideModLoader(loader, version)

    private fun selection(
        version: String?,
        matched: Boolean = true,
        unmatched: Boolean = false,
    ) = OxideModSelection(version = version, matched = matched, unmatched = unmatched)

    /** 默认的"是不是同一个版本"：只有字符串相等才算 */
    private val sameVersion: (OxideModLoader, String) -> Boolean = { loader, selected ->
        loader.version == selected
    }

    private fun state(
        original: String = "1.20.1",
        target: String = original,
        installedLoaders: List<OxideModLoader> = emptyList(),
        selections: Map<ModLoader, OxideModSelection> = emptyMap(),
    ) = OxideModState(
        originalGameVersion = original,
        targetGameVersion = target,
        installed = installedLoaders,
        selections = selections,
    )

    private fun kindsOf(diffs: ModifyDiffs?): List<String> = diffs?.list.orEmpty().map { diff ->
        when (diff) {
            is ModifyDiffs.McChange -> "mc:${diff.original}->${diff.updateTo}"
            is ModifyDiffs.LoaderChange -> "change:${diff.modloader.displayName}:${diff.original}->${diff.updateTo}"
            is ModifyDiffs.LoaderRemove -> "remove:${diff.modloader.displayName}"
            is ModifyDiffs.LoaderInstall -> "install:${diff.modloader.displayName}:${diff.version}"
        }
    }

    // ---- 这一页能改哪些加载器 ---------------------------------------------

    @Test
    fun modifyPlanNeverCarriesApiMods() {
        listOf("1.21.1", "1.12.2", "1.7.10").forEach { mcVer ->
            val plan = oxideModPlan(mcVer)
            assertTrue(
                "$mcVer 上不该出现 API 模组",
                plan.none { slot -> slot.apiOf != null },
            )
            assertTrue(
                "$mcVer 上不该出现 API 模组",
                plan.none { slot -> slot.loader.isApiMod },
            )
        }
    }

    @Test
    fun modifyPlanListsOnlyTheLoadersThatVersionSupports() {
        assertEquals(
            listOf(
                ModLoader.OPTIFINE, ModLoader.FORGE, ModLoader.NEOFORGE,
                ModLoader.FABRIC, ModLoader.QUILT,
            ),
            oxideModPlan("1.20.1").map { slot -> slot.loader },
        )
        assertEquals(
            listOf(
                ModLoader.OPTIFINE, ModLoader.FORGE, ModLoader.CLEANROOM,
                ModLoader.LEGACY_FABRIC,
            ),
            oxideModPlan("1.12.2").map { slot -> slot.loader },
        )
    }

    @Test
    fun everyLoaderInThePlanAlsoAppearsInTheChangeOrder() {
        listOf("1.21.1", "1.20.1", "1.12.2", "1.7.10").forEach { mcVer ->
            val planLoaders = oxideModPlan(mcVer).map { slot -> slot.loader }.toSet()
            assertTrue(
                "$mcVer 上有加载器不会参与变更判定",
                planLoaders.all { loader -> loader in oxideModLoaderOrder },
            )
        }
    }

    @Test
    fun reloadableLoadersAreExactlyThePlannedOnes() {
        assertEquals(
            oxideModPlan("1.20.1").map { slot -> slot.loader }.toSet(),
            oxideModReloadableLoaders("1.20.1"),
        )
        assertEquals(
            setOf(ModLoader.OPTIFINE, ModLoader.FORGE, ModLoader.CLEANROOM, ModLoader.LEGACY_FABRIC),
            oxideModReloadableLoaders("1.12.2"),
        )
    }

    @Test
    fun optifineAndForgeAreAlwaysReloadableButLiteLoaderNeverIs() {
        listOf("1.21.1", "1.20.1", "1.12.2", "1.7.10").forEach { mcVer ->
            val reloadable = oxideModReloadableLoaders(mcVer)
            assertTrue("$mcVer 上必须能拉 OptiFine", ModLoader.OPTIFINE in reloadable)
            assertTrue("$mcVer 上必须能拉 Forge", ModLoader.FORGE in reloadable)
            assertFalse(
                "$mcVer 上 LiteLoader 没有版本列表，不能预选",
                ModLoader.LITE_LOADER in reloadable,
            )
        }
    }

    // ---- 变更内容 ---------------------------------------------------------

    @Test
    fun nothingChangedMeansNoDiffs() {
        assertNull(
            oxideModDiffs(
                state(
                    installedLoaders = listOf(installed(ModLoader.FABRIC, "0.15.11")),
                    selections = mapOf(ModLoader.FABRIC to selection("0.15.11")),
                ),
                equivalent = sameVersion,
            ),
        )
    }

    @Test
    fun changingTheMinecraftVersionIsOneDiff() {
        assertEquals(
            listOf("mc:1.20.1->1.21.1"),
            kindsOf(
                oxideModDiffs(
                    state(original = "1.20.1", target = "1.21.1"),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun clearingAMatchedLoaderRemovesIt() {
        assertEquals(
            listOf("remove:Fabric"),
            kindsOf(
                oxideModDiffs(
                    state(
                        installedLoaders = listOf(installed(ModLoader.FABRIC, "0.15.11")),
                        selections = mapOf(ModLoader.FABRIC to selection(null)),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun pickingAnotherVersionOfAnInstalledLoaderChangesIt() {
        assertEquals(
            listOf("change:Fabric:0.15.11->0.16.0"),
            kindsOf(
                oxideModDiffs(
                    state(
                        installedLoaders = listOf(installed(ModLoader.FABRIC, "0.15.11")),
                        selections = mapOf(ModLoader.FABRIC to selection("0.16.0")),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun aLoaderThatWasNeverInstalledBecomesAnInstall() {
        assertEquals(
            listOf("install:Fabric:0.16.0"),
            kindsOf(
                oxideModDiffs(
                    state(
                        selections = mapOf(ModLoader.FABRIC to selection("0.16.0")),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun anUnrecognisedLoaderIsKeptAsIsByDefault() {
        // LiteLoader 没有版本列表，永远预选不出来，因此默认原样保留
        assertNull(
            oxideModDiffs(
                state(
                    installedLoaders = listOf(installed(ModLoader.LITE_LOADER, "1.16.2")),
                    selections = mapOf(
                        ModLoader.LITE_LOADER to selection(version = null, unmatched = true)
                    ),
                ),
                equivalent = sameVersion,
            ),
        )
    }

    @Test
    fun anUnrecognisedLoaderIsRemovedOnceTheMinecraftVersionChanges() {
        // 换了 Minecraft 版本之后旧加载器几乎必然匹配不上，未选择即视为移除
        assertEquals(
            listOf("mc:1.16.5->1.20.1", "remove:LiteLoader"),
            kindsOf(
                oxideModDiffs(
                    state(
                        original = "1.16.5",
                        target = "1.20.1",
                        installedLoaders = listOf(installed(ModLoader.LITE_LOADER, "1.16.2")),
                        selections = mapOf(
                            ModLoader.LITE_LOADER to selection(version = null, unmatched = true)
                        ),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun pickingAVersionForAnUnrecognisedLoaderIsAChange() {
        assertEquals(
            listOf("change:LiteLoader:1.16.2->1.17.1"),
            kindsOf(
                oxideModDiffs(
                    state(
                        installedLoaders = listOf(installed(ModLoader.LITE_LOADER, "1.16.2")),
                        selections = mapOf(
                            ModLoader.LITE_LOADER to selection("1.17.1", unmatched = true)
                        ),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    @Test
    fun aLoaderWhoseListHasNotArrivedIsLeftAlone() {
        // 列表还没到齐时不给结论，否则每次进来都会凭空生成一次"移除"
        assertNull(
            oxideModDiffs(
                state(
                    installedLoaders = listOf(installed(ModLoader.FABRIC, "0.15.11")),
                    selections = mapOf(
                        ModLoader.FABRIC to selection(version = null, matched = false)
                    ),
                ),
                equivalent = sameVersion,
            ),
        )
    }

    @Test
    fun equivalentSpellingsOfTheSameOptifineAreNotAChange() {
        // 已装信息可能是 Maven 坐标，装着的这一版在列表里也能等价匹配上
        val mavenVersion: (OxideModLoader, String) -> Boolean = { loader, selected ->
            selected == "1.12.2_HD_U_C1"
        }
        assertNull(
            oxideModDiffs(
                state(
                    installedLoaders = listOf(installed(ModLoader.OPTIFINE, "1.12.2_HD_U_C1")),
                    selections = mapOf(ModLoader.OPTIFINE to selection("1.12.2_HD_U_C1")),
                ),
                equivalent = mavenVersion,
            ),
        )
    }

    @Test
    fun apiModSelectionsNeverProduceADiff() {
        assertNull(
            oxideModDiffs(
                state(
                    selections = mapOf(
                        ModLoader.FABRIC_API to selection("0.76.0+1.20.1"),
                        ModLoader.QUILT_API to selection("6.0.0"),
                    ),
                ),
                equivalent = sameVersion,
            ),
        )
    }

    @Test
    fun aVersionChangeAndALoaderChangeAreBothReported() {
        assertEquals(
            listOf("mc:1.12.2->1.20.1", "change:Fabric:0.14.21->0.15.11"),
            kindsOf(
                oxideModDiffs(
                    state(
                        original = "1.12.2",
                        target = "1.20.1",
                        installedLoaders = listOf(installed(ModLoader.FABRIC, "0.14.21")),
                        selections = mapOf(ModLoader.FABRIC to selection("0.15.11")),
                    ),
                    equivalent = sameVersion,
                ),
            ),
        )
    }

    // ---- 步骤可达性 -------------------------------------------------------

    @Test
    fun versionStepIsAlwaysReachable() {
        assertTrue(oxideModStepReachable(OxideModStep.Version, targetGameVersion = null, instanceReadable = false))
        assertTrue(oxideModStepReachable(OxideModStep.Version, targetGameVersion = "", instanceReadable = false))
        assertTrue(oxideModStepReachable(OxideModStep.Version, targetGameVersion = "1.20.1", instanceReadable = true))
    }

    @Test
    fun everyOtherStepNeedsATargetVersion() {
        listOf(OxideModStep.Loader, OxideModStep.Instance, OxideModStep.Apply).forEach { step ->
            assertFalse(step.name, oxideModStepReachable(step, targetGameVersion = null, instanceReadable = true))
            assertFalse(step.name, oxideModStepReachable(step, targetGameVersion = "", instanceReadable = true))
            assertTrue(step.name, oxideModStepReachable(step, targetGameVersion = "1.20.1", instanceReadable = true))
        }
    }

    @Test
    fun nothingIsReachableWithoutReadableInstanceMetadata() {
        listOf(OxideModStep.Loader, OxideModStep.Instance, OxideModStep.Apply).forEach { step ->
            assertFalse(
                step.name,
                oxideModStepReachable(step, targetGameVersion = "1.20.1", instanceReadable = false),
            )
        }
    }

    // ---- 改名 -------------------------------------------------------------

    @Test
    fun anUnchangedNameIsAlwaysAllowed() {
        val verdict = oxideModNameVerdict(
            name = "1.20.1 Fabric",
            currentName = "1.20.1 Fabric",
            // 探测结果在这条分支上根本不该被看
            conflict = true,
            filenameInvalid = true,
        )
        assertEquals(OxideModNameVerdict.Kept, verdict)
        assertTrue(oxideModNameAllowsRename(verdict))
    }

    @Test
    fun everyWayOfRenamingBadlyIsRefused() {
        assertEquals(
            OxideModNameVerdict.Empty,
            oxideModNameVerdict("1.21", "1.20.1", conflict = false, filenameInvalid = false),
        )
        assertEquals(
            OxideModNameVerdict.Invalid,
            oxideModNameVerdict("1.21", "1.20.1", conflict = false, filenameInvalid = true),
        )
        assertEquals(
            OxideModNameVerdict.Conflict,
            oxideModNameVerdict("1.21", "1.20.1", conflict = true, filenameInvalid = false),
        )
    }

    @Test
    fun anEmptyNameLosesToNothingAndAValidRenamePasses() {
        assertFalse(
            oxideModNameAllowsRename(
                oxideModNameVerdict("", "1.20.1", conflict = false, filenameInvalid = false)
            ),
        )
        assertTrue(
            oxideModNameAllowsRename(
                oxideModNameVerdict("1.21", "1.20.1", conflict = false, filenameInvalid = false)
            ),
        )
    }

    // ---- 组装出去的修改请求 -----------------------------------------------

    private fun optifine(displayName: String, forgeRequirement: String? = null) = OptiFineVersion(
        displayName = displayName,
        fileName = "OptiFine_$displayName.jar",
        version = displayName,
        inherit = "1.12.2",
        releaseDate = "2020/01/01",
        forgeVersion = forgeRequirement,
        isPreview = false,
    )

    private fun forge(versionName: String) = ForgeVersion(
        versionName = versionName,
        branch = null,
        inherit = "1.12.2",
        releaseTime = "2020/01/01 00:00",
        hash = null,
        isRecommended = false,
        category = "installer",
        fileVersion = versionName,
    )

    @Test
    fun theModifyRequestKeepsTheOldInstanceName() {
        // 改名发生在修改成功之后，由 ModifyVersionViewModel 负责；请求里必须是旧名字
        val info = oxideModGameDownloadInfo(
            targetGameVersion = "1.20.1",
            currentVersionName = "My Instance",
            supports = oxideLoaderVerSupports("1.20.1"),
            current = CurrentAddon(),
        )
        assertEquals("1.20.1", info.gameVersion)
        assertEquals("My Instance", info.customVersionName)
    }

    @Test
    fun theModifyRequestNeverCarriesApiMods() {
        val addon = CurrentAddon()
        addon.fabricAPIVersion.value = null
        val info = oxideModGameDownloadInfo(
            targetGameVersion = "1.20.1",
            currentVersionName = "1.20.1 Fabric",
            supports = oxideLoaderVerSupports("1.20.1"),
            current = addon,
        )
        assertNull("旧向导一个 API 字段都没写", info.fabricAPI)
        assertNull(info.legacyFabricAPI)
        assertNull(info.quiltAPI)
    }

    @Test
    fun loadersTheTargetVersionDoesNotSupportAreDropped() {
        val addon = CurrentAddon()
        // 1.12.2 上不存在 Fabric，但选择里仍然可能有它
        addon.fabricVersion.value = FabricVersion(inherit = "1.12.2", version = "0.15.11", stable = true)
        addon.legacyFabricVersion.value =
            LegacyFabricVersion(inherit = "1.12.2", version = "0.2.0", stable = true)

        val info = oxideModGameDownloadInfo(
            targetGameVersion = "1.12.2",
            currentVersionName = "1.12.2",
            supports = oxideLoaderVerSupports("1.12.2"),
            current = addon,
        )
        assertNull("1.12.2 上不可能组装出一个带 Fabric 的请求", info.fabric)
        assertEquals("0.2.0", info.legacyFabric?.version)
    }

    @Test
    fun everySupportedLoaderIsPassedThrough() {
        val addon = CurrentAddon()
        addon.optifineVersion.value = optifine("1.12.2_HD_U_C1")
        addon.forgeVersion.value = forge("14.23.5.2859")
        addon.neoforgeVersion.value = NeoForgeVersion(rawVersion = "20.4.190", isLegacyForge = false)
        addon.fabricVersion.value = FabricVersion(inherit = "1.20.1", version = "0.15.11", stable = true)
        addon.quiltVersion.value = QuiltVersion(inherit = "1.20.1", version = "0.20.0")

        val info = oxideModGameDownloadInfo(
            targetGameVersion = "1.20.1",
            currentVersionName = "1.20.1",
            supports = oxideLoaderVerSupports("1.20.1"),
            current = addon,
        )
        assertEquals("1.12.2_HD_U_C1", info.optifine?.version)
        assertEquals("14.23.5.2859", info.forge?.versionName)
        assertEquals("20.4.190", info.neoforge?.versionName)
        assertEquals("0.15.11", info.fabric?.version)
        assertEquals("0.20.0", info.quilt?.version)
        assertNull("1.20.1 上不存在 Cleanroom", info.cleanroom)
        assertNull("1.20.1 上不存在 Legacy Fabric", info.legacyFabric)
    }

    @Test
    fun theCleanroomCombinationSurvivesOnItsOwnVersion() {
        val addon = CurrentAddon()
        addon.cleanroomVersion.value = CleanroomVersion(version = "1.0", createdAt = Instant.EPOCH)
        addon.neoforgeVersion.value = NeoForgeVersion(rawVersion = "20.4.190", isLegacyForge = false)

        val info = oxideModGameDownloadInfo(
            targetGameVersion = "1.12.2",
            currentVersionName = "1.12.2 Cleanroom",
            supports = oxideLoaderVerSupports("1.12.2"),
            current = addon,
        )
        assertEquals("1.0", info.cleanroom?.version)
        assertNull("1.12.2 上不存在 NeoForge", info.neoforge)
    }
}