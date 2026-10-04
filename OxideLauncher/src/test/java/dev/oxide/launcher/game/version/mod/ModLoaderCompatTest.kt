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

package dev.oxide.launcher.game.version.mod

import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.download.assets.platform.ModLoaderDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import dev.oxide.launcher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载前的加载器守卫
 *
 * 这是"NeoForge 模组分进了 Fabric 实例"那条规则的唯一实现。判定曾经不存在，
 * 取而代之的是"模组文件不支持实例的加载器时，就沿用模组文件自己的加载器通道去找新版本"，
 * 于是 Fabric 实例里的 NeoForge 模组被判成可更新、NeoForge 构建被下载下来覆盖原文件。
 *
 * 因此这里逐条钉死五种情形，外加两种"实例读不出加载器"的退化情形。
 */
class ModLoaderCompatTest {

    private fun modrinth(vararg loaders: ModrinthModLoaderCategory): List<ModLoaderDisplayLabel> =
        loaders.toList()

    private fun curseForge(vararg loaders: CurseForgeModLoader): List<ModLoaderDisplayLabel> =
        loaders.toList()

    // ---- 用户在设备上遇到的那五种 --------------------------------------------

    @Test
    fun neoforgeModInAFabricInstanceIsAMismatch() {
        // 用户报的那一条：Fabric 实例里的 NeoForge 模组。
        // 守卫必须让它无路可走——既不提供候选版本，也就不下载。
        val verdict = modLoaderVerdict(
            instanceLoaders = listOf(ModLoader.FABRIC),
            declaredLoaders = modrinth(ModrinthModLoaderCategory.NEOFORGE),
        )
        assertEquals(ModLoaderVerdict.Mismatch, verdict)
        assertFalse(verdict.loadable)
    }

    @Test
    fun neoforgeModInAFabricInstanceHasNoTargetLoaderAtAll() {
        // 上一条的下游后果：目标通道必须是空的。返回"空集合"如果被当成
        // "不限加载器"，过滤器就等于没写，守卫又被绕过去了。
        assertTrue(
            resolveTargetLoaderNames(
                instanceLoaders = listOf(ModLoader.FABRIC),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.NEOFORGE),
            ).isEmpty()
        )
    }

    @Test
    fun neoforgeModInAFabricInstanceIsAlsoAMismatchOnCurseForge() {
        assertEquals(
            ModLoaderVerdict.Mismatch,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.FABRIC),
                declaredLoaders = curseForge(CurseForgeModLoader.NEOFORGE),
            ),
        )
    }

    @Test
    fun fabricModInAFabricInstanceIsCompatible() {
        val verdict = modLoaderVerdict(
            instanceLoaders = listOf(ModLoader.FABRIC),
            declaredLoaders = modrinth(ModrinthModLoaderCategory.FABRIC),
        )
        assertEquals(ModLoaderVerdict.Compatible, verdict)
        assertTrue(verdict.loadable)
    }

    @Test
    fun forgeModInAFabricInstanceIsAMismatch() {
        assertEquals(
            ModLoaderVerdict.Mismatch,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.FABRIC),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.FORGE),
            ),
        )
    }

    @Test
    fun aLoaderlessModIsNotProvablyIncompatible() {
        // 平台上有些文件只给了游戏版本、没给加载器。这读不出来不等于不兼容，
        // 因此放行——把"读不出来"当成"不兼容"会让一批正常模组永远更新不了。
        val verdict = modLoaderVerdict(
            instanceLoaders = listOf(ModLoader.FABRIC),
            declaredLoaders = emptyList(),
        )
        assertEquals(ModLoaderVerdict.Loaderless, verdict)
        assertTrue(verdict.loadable)
    }

    @Test
    fun aMultiLoaderModMatchesOnAnySharedLoader() {
        // 同一个项目同时发 Fabric 与 Forge 构建：Fabric 实例应当能用它，
        // 而且只按 Fabric 那条通道去找新版本。
        val declared = modrinth(
            ModrinthModLoaderCategory.FABRIC,
            ModrinthModLoaderCategory.FORGE,
        )
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(instanceLoaders = listOf(ModLoader.FABRIC), declaredLoaders = declared),
        )
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(instanceLoaders = listOf(ModLoader.FORGE), declaredLoaders = declared),
        )
        assertEquals(
            setOf("Fabric"),
            resolveTargetLoaderNames(listOf(ModLoader.FABRIC), declared),
        )
    }

    @Test
    fun aMultiLoaderModStillMissesAnInstanceOutsideIt() {
        assertEquals(
            ModLoaderVerdict.Mismatch,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.NEOFORGE),
                declaredLoaders = modrinth(
                    ModrinthModLoaderCategory.FABRIC,
                    ModrinthModLoaderCategory.FORGE,
                ),
            ),
        )
    }

    // ---- 实例加载器读不出来时的退化 ------------------------------------------

    @Test
    fun anInstanceWithNoLoaderCannotRuleAnythingOut() {
        // 原版实例：没有任何可遵守的加载器约束。此时不拦，
        // 但也不能按实例筛通道——那会把所有候选版本都筛掉。
        val declared = modrinth(ModrinthModLoaderCategory.FABRIC)
        val verdict = modLoaderVerdict(instanceLoaders = emptyList(), declaredLoaders = declared)
        assertEquals(ModLoaderVerdict.NoInstanceLoader, verdict)
        assertTrue(verdict.loadable)
        assertEquals(
            setOf("Fabric"),
            resolveTargetLoaderNames(emptyList(), declared),
        )
    }

    @Test
    fun theUnknownLoaderCountsAsNoLoaderAtAll() {
        // ModLoader.UNKNOWN 的显示名是空串，不能当成一个叫""的加载器去比对
        assertEquals(
            ModLoaderVerdict.NoInstanceLoader,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.UNKNOWN),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.FORGE),
            ),
        )
    }

    @Test
    fun anInstanceWithSeveralLoadersMatchesAnyOfThem() {
        // Forge 实例同时装了 OptiFine、Fabric 之类的组合很常见
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.FORGE, ModLoader.FABRIC),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.FABRIC),
            ),
        )
    }

    @Test
    fun loaderNamesAreComparedCaseInsensitively() {
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdictByName(
                instanceNames = listOf("Fabric"),
                declaredNames = listOf("fAbRiC"),
            ),
        )
    }

    @Test
    fun curseForgeAnyIsNotALoaderClaim() {
        // CurseForgeModLoader.ANY 的显示名也是空串；"原版文件 + ANY" 不该看起来像兼容声明
        assertEquals(
            ModLoaderVerdict.Loaderless,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.FABRIC),
                declaredLoaders = curseForge(CurseForgeModLoader.ANY),
            ),
        )
    }

    @Test
    fun cleanroomIsNotOnEitherPlatformSoItNeverMatches() {
        // Cleanroom 在 CurseForge 与 Modrinth 的加载器枚举里都没有对应项，
        // 因此标注了加载器的模组在 Cleanroom 实例上一定是不兼容。
        assertEquals(
            ModLoaderVerdict.Mismatch,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.CLEANROOM),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.FABRIC),
            ),
        )
    }

    @Test
    fun legacyFabricAndBabricMatchTheirOwnEntries() {
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.LEGACY_FABRIC),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.LEGACY_FABRIC),
            ),
        )
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.BABRIC),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.BABRIC),
            ),
        )
    }

    @Test
    fun liteLoaderMatchesItsOwnEntry() {
        assertEquals(
            ModLoaderVerdict.Compatible,
            modLoaderVerdict(
                instanceLoaders = listOf(ModLoader.LITE_LOADER),
                declaredLoaders = modrinth(ModrinthModLoaderCategory.LITELOADER),
            ),
        )
    }
}