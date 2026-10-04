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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 启用 / 禁用 / 删除：对着真实文件系统跑
 *
 * 这一层不需要 Compose，只需要一个真的目录。之所以要真的改名而不是 mock：
 * "禁用能成、再启用没反应"和"删除点了没反应"这两条，只有在路径真的变了、
 * 状态真的从文件系统读出来的时候才暴露得出来。
 *
 * 三条规则在这里被钉死：
 *
 *  1. 启用的**报告值**来自路径，不来自任何缓存的布尔值；禁用之后同一个
 *     [LocalMod] 对象报告的就是禁用态。
 *  2. 启用与禁用是对称的：重复点同一个方向是彻底的空操作，而不是把文件
 *     搬回自己（那一步在不同文件系统上可能静默成功也可能抛异常）。
 *  3. 删除打的是**此刻**磁盘上的真实路径。禁用态的文件在磁盘上叫
 *     `x.jar.disabled`——按 `x.jar` 去删什么也删不掉，而旧界面正是这么删的。
 */
class LocalModStateTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun modsDir(): File = temporaryFolder.newFolder("mods")

    private fun modFile(dir: File, name: String = "sodium-0.5.jar"): File =
        File(dir, name).apply { writeText("fake jar bytes") }

    private fun localMod(file: File) = LocalMod(
        modFile = file,
        fileSize = file.length(),
        id = "sodium",
        loader = ModLoader.FABRIC,
        name = "Sodium",
        version = "0.5.3",
        authors = listOf("JellySquid"),
    )

    // ---- 启用 / 禁用 --------------------------------------------------------

    @Test
    fun anEnabledFileIsReportedEnabled() {
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        assertTrue(mod.file.isEnabled())
        assertFalse(mod.file.isDisabled())
        assertEquals(true, mod.file.isEnabled())
    }

    @Test
    fun disablingRenamesTheFileAndFlipsTheReportedState() {
        val dir = modsDir()
        val file = modFile(dir)
        val mod = localMod(file)

        assertTrue("a real state change must be reported", mod.disable())

        assertFalse("the original path must be gone", file.exists())
        assertTrue(File(dir, "sodium-0.5.jar.disabled").exists())
        // 报告值读的是路径本身，不是禁用前记下来的布尔值
        assertFalse(mod.file.isEnabled())
        assertEquals("sodium-0.5.jar.disabled", mod.file.name)
    }

    @Test
    fun aDisabledFileCanBeEnabledAgain() {
        // 用户报的那一条：禁用能成，再点一次没反应。
        val dir = modsDir()
        val mod = localMod(modFile(dir))

        assertTrue(mod.disable())
        assertTrue("re-enabling must be a real state change", mod.enable())

        assertTrue(mod.file.isEnabled())
        assertTrue(File(dir, "sodium-0.5.jar").exists())
        assertFalse(File(dir, "sodium-0.5.jar.disabled").exists())
    }

    @Test
    fun enableAndDisableCanBeRepeatedIndefinitely() {
        // 对称性：来回切任意多次，每一次都必须真的改到文件上
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        repeat(4) { round ->
            assertTrue("disable #$round", mod.disable())
            assertFalse("disabled after #$round", mod.file.isEnabled())
            assertTrue("enable #$round", mod.enable())
            assertTrue("enabled after #$round", mod.file.isEnabled())
        }
    }

    @Test
    fun disablingAnAlreadyDisabledFileDoesNothingAtAll() {
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        mod.disable()
        assertFalse("repeating the same direction is a no-op", mod.disable())
        assertFalse(mod.file.isEnabled())
    }

    @Test
    fun enablingAnAlreadyEnabledFileDoesNotMoveTheFileOntoItself() {
        // 旧实现缺这道判断：Files.move(path, path) 在不同文件系统上行为不一致，
        // 抛出的那个被吞掉，于是"什么都没发生、界面上也不动"。
        val dir = modsDir()
        val file = modFile(dir)
        val mod = localMod(file)
        assertFalse(mod.enable())
        assertTrue(file.exists())
        assertEquals("sodium-0.5.jar", mod.file.name)
    }

    @Test
    fun setEnabledTakesTheTargetStateNotTheInverse() {
        val dir = modsDir()
        val mod = localMod(modFile(dir))

        assertTrue(mod.setEnabled(false))
        assertFalse(mod.file.isEnabled())
        // 目标仍然是禁用：这一次必须是空操作，不能反向再搬一次
        assertFalse(mod.setEnabled(false))
        assertFalse(mod.file.isEnabled())
        // 目标是启用
        assertTrue(mod.setEnabled(true))
        assertTrue(mod.file.isEnabled())
    }

    @Test
    fun theReportedStateAlwaysFollowsTheFilesystem() {
        // 另一个人从外面把文件改了之后，本地报告的值必须立刻跟着变：
        // 状态是从路径读出来的，因此不存在"缓存下来的旧状态"。
        val dir = modsDir()
        val file = modFile(dir)
        val mod = localMod(file)

        assertTrue(mod.file.isEnabled())
        assertTrue(file.renameTo(File(dir, "sodium-0.5.jar.disabled")))
        assertFalse(mod.file.isEnabled())

        assertTrue(File(dir, "sodium-0.5.jar.disabled").renameTo(file))
        assertTrue(mod.file.isEnabled())
    }

    @Test
    fun theStableKeyIgnoresTheDisabledSuffix() {
        // 列表的键必须是去掉 .disabled 之后的文件名：否则改一次名这一行就换了身份，
        // 选中态会瞬间跑到另一行上，"再启用"也就找不到对象了。
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        assertEquals("sodium-0.5.jar", mod.file.modBaseName())

        mod.disable()
        assertEquals("sodium-0.5.jar", mod.file.modBaseName())

        mod.enable()
        assertEquals("sodium-0.5.jar", mod.file.modBaseName())
    }

    // ---- 删除 ---------------------------------------------------------------

    @Test
    fun deletingRemovesTheFileFromDisk() {
        val dir = modsDir()
        val file = modFile(dir)
        val mod = localMod(file)

        assertTrue(mod.delete())
        assertFalse(file.exists())
        assertTrue(dir.listFiles()?.isEmpty() ?: false)
    }

    @Test
    fun deletingADisabledModRemovesTheFileThatActuallyExists() {
        // 用户报的那一条：删除点了没反应。禁用态的文件在磁盘上叫 x.jar.disabled，
        // 按 x.jar 去删是删不掉的，而且 deleteQuietly 不抛异常——
        // 一次没发生的删除就被当成了成功汇报出去。
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        mod.disable()

        val disabled = File(dir, "sodium-0.5.jar.disabled")
        assertTrue(disabled.exists())

        assertTrue("the live path is the .disabled one", mod.delete())
        assertFalse(disabled.exists())
        assertFalse(File(dir, "sodium-0.5.jar").exists())
    }

    @Test
    fun deletingAMissingFileIsReportedAsFailureRatherThanSuccess() {
        val dir = modsDir()
        val file = modFile(dir)
        val mod = localMod(file)
        assertTrue(file.delete())
        assertFalse("nothing was deleted, so this must not read as success", mod.delete())
    }

    @Test
    fun aDeletedModCanStillBeEnabledAndIsReportedEnabled() {
        // 删除之后同一个对象还攥着一个已经不存在的路径：它必须报告禁用态之外的
        // 事实，而不是继续拿旧路径上的布尔值说事。
        val dir = modsDir()
        val mod = localMod(modFile(dir))
        mod.disable()
        assertFalse(mod.file.isEnabled())
        assertTrue(mod.delete())
        assertFalse(mod.file.isDisabled())
    }

    // ---- 后缀常量的自洽 ------------------------------------------------------

    @Test
    fun theSuffixConstantIsWhatTheEnabledCheckUses() {
        val file = File("/tmp/mods/x.jar")
        assertTrue(file.isEnabled())
        assertFalse(File("/tmp/mods/x.jar$DISABLED_SUFFIX").isEnabled())
        assertTrue(File("/tmp/mods/x.jar$DISABLED_SUFFIX").isDisabled())
    }

    @Test
    fun theSuffixIsMatchedCaseInsensitively() {
        assertFalse(File("/tmp/mods/x.jar.DISABLED").isEnabled())
        assertEquals("x.jar", File("/tmp/mods/x.jar.DISABLED").modBaseName())
    }

    @Test
    fun enabledModDropsTheSuffixExactlyOnce() {
        val disabled = File("/tmp/mods/x.jar.disabled")
        assertEquals(File("/tmp/mods/x.jar"), enabledMod(disabled))
        // 已启用的文件原样返回，不去造一个同名的兄弟路径
        assertEquals(File("/tmp/mods/x.jar"), enabledMod(File("/tmp/mods/x.jar")))
    }
}