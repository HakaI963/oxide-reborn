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
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.download.assets

import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import dev.oxide.launcher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依赖安装链上的加载器相容判定
 *
 * 钉的是 [platformLoadersCompatible] 的三条规则，重点在最后一条：一个只支持别的加载器
 * 的文件**必须**被拒。原来那条 "versionLoaders.isEmpty() || ..." 把"没看懂加载器名"变成
 * 成了"兼容"，于是 NeoForge-only 的模组能被装进 Fabric 实例，到游戏里必然加载失败。
 *
 * 界面挑文件走的是另一份判定：
 * [dev.oxide.launcher.ui.screens.main.oxide.discoverCompatibleFile]，
 * 它对"目标没有加载器"是刻意放行的，并由 OxideDiscoverTargetTest 单独钉住。
 */
class OxideModLoaderCompatibilityTest {

    private fun cf(vararg loaders: CurseForgeModLoader): Set<PlatformDisplayLabel> = loaders.toSet()

    private fun compatible(
        target: Set<PlatformDisplayLabel>,
        file: Set<PlatformDisplayLabel>,
        classes: PlatformClasses = PlatformClasses.MOD,
    ): Boolean = platformLoadersCompatible(classes, target, file)

    @Test
    fun theSameLoaderIsCompatible() {
        assertTrue(compatible(cf(CurseForgeModLoader.FABRIC), cf(CurseForgeModLoader.FABRIC)))
        assertTrue(compatible(cf(CurseForgeModLoader.FORGE), cf(CurseForgeModLoader.FORGE)))
        assertTrue(compatible(cf(CurseForgeModLoader.NEOFORGE), cf(CurseForgeModLoader.NEOFORGE)))
    }

    /** 只支持别的加载器的文件与目标实例不相容——这一条是本次修复的核心 */
    @Test
    fun aFileBuiltForAnotherLoaderIsRejected() {
        assertFalse(compatible(cf(CurseForgeModLoader.FABRIC), cf(CurseForgeModLoader.NEOFORGE)))
        assertFalse(compatible(cf(CurseForgeModLoader.NEOFORGE), cf(CurseForgeModLoader.FABRIC)))
        assertFalse(compatible(cf(CurseForgeModLoader.FORGE), cf(CurseForgeModLoader.FABRIC)))
        assertFalse(compatible(cf(CurseForgeModLoader.FABRIC), cf(CurseForgeModLoader.QUILT)))
    }

    /** 多加载器项目：目标要求其中任意一个即可 */
    @Test
    fun anyOfTheTargetsLoadersIsEnough() {
        val multi = cf(CurseForgeModLoader.FABRIC, CurseForgeModLoader.FORGE)
        assertTrue(compatible(cf(CurseForgeModLoader.FORGE), multi))
        assertTrue(compatible(cf(CurseForgeModLoader.FABRIC), multi))
        assertFalse(compatible(cf(CurseForgeModLoader.NEOFORGE), multi))
    }

    /**
     * 文件的加载器名一个都识别不出来时判为不相容
     *
     * 平台写了本构建没有枚举项的加载器名时就会落到这里。空列表不是"没有要求"，是"没看懂"，
     * 当成兼容就等于把文件装进去、再让它在游戏里加载失败。
     */
    @Test
    fun anUnreadableLoaderTagIsNotCompatibility() {
        assertFalse(compatible(cf(CurseForgeModLoader.FABRIC), emptySet()))
        assertFalse(compatible(cf(CurseForgeModLoader.NEOFORGE), emptySet()))
    }

    /** 目标没有可识别的加载器要求时放行：这时我们并不知道目标要什么 */
    @Test
    fun aTargetWithoutALoaderRequirementLetsEverythingThrough() {
        assertTrue(compatible(emptySet(), cf(CurseForgeModLoader.NEOFORGE)))
        assertTrue(compatible(emptySet(), emptySet()))
    }

    /** 不是模组就不看加载器：整合包、资源包、光影、存档都与加载器无关 */
    @Test
    fun otherClassesIgnoreLoadersEntirely() {
        for (classes in listOf(
            PlatformClasses.MOD_PACK,
            PlatformClasses.RESOURCE_PACK,
            PlatformClasses.SAVES,
            PlatformClasses.SHADERS,
        )) {
            val label = classes.name
            assertTrue(label, compatible(emptySet(), emptySet(), classes))
            assertTrue(label, compatible(cf(CurseForgeModLoader.FABRIC), emptySet(), classes))
        }
    }

    /** 两个平台的加载器枚举不是同一套类型，因此不能互相顶替 */
    @Test
    fun modrinthAndCurseForgeLabelsAreNotInterchangeable() {
        assertFalse(
            compatible(cf(CurseForgeModLoader.FABRIC), setOf(ModrinthModLoaderCategory.FABRIC))
        )
    }
}
