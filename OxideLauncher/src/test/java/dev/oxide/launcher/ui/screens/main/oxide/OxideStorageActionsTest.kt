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

import dev.oxide.launcher.game.multirt.Runtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存储抽屉：把 SAF 选中的目录换算成真实路径
 *
 * [dev.oxide.launcher.game.path.GamePathManager] 存的是真实路径，后面全部按 `File` 处理，
 * 所以换算不出来的那几种必须**明确拒绝**，而不是悄悄存一个打不开的字符串。
 */
class OxideGamePathFromRelativeTest {

    private val root = "/storage/emulated/0"

    @Test
    fun thePrimaryStorageDocumentIdIsRecognised() {
        assertTrue(isPrimaryStorageDocument("primary:Games/MyPack"))
    }

    @Test
    fun anotherProviderIsNotPrimaryStorage() {
        // SD 卡、云盘、MTP 各自的 id 空间没有对应的文件系统路径
        assertFalse(isPrimaryStorageDocument("1A2B-3C4D:games"))
        assertFalse(isPrimaryStorageDocument("msf:1000000123"))
        assertFalse(isPrimaryStorageDocument("primary"))
    }

    @Test
    fun aRelativePathBecomesAnAbsoluteOne() {
        assertEquals(
            "$root/Games/MyPack",
            oxideGamePathFromRelative("Games/MyPack", storageRoot = root),
        )
    }

    @Test
    fun theStorageRootItselfIsTheDefaultFolder() {
        assertEquals(root, oxideGamePathFromRelative("", storageRoot = root))
    }

    @Test
    fun aTrailingSlashOnTheRootDoesNotDoubleUp() {
        assertEquals(
            "$root/Games",
            oxideGamePathFromRelative("Games", storageRoot = "$root/"),
        )
    }

    @Test
    fun surroundingSlashesAndSpacesAreTrimmed() {
        assertEquals(
            "$root/Games/My Pack",
            oxideGamePathFromRelative("  /Games/My Pack/  ", storageRoot = root),
        )
    }

    @Test
    fun parentSegmentsAreRefused() {
        // document id 是系统给的，但游戏目录随后会被当成真实路径使用：
        // 路径穿越不是这里该冒的风险
        assertNull(oxideGamePathFromRelative("../secrets", storageRoot = root))
        assertNull(oxideGamePathFromRelative("Games/../../secrets", storageRoot = root))
    }

    @Test
    fun aDotSegmentIsNotAParentSegment() {
        assertEquals("$root/./Games", oxideGamePathFromRelative("./Games", storageRoot = root))
    }

    @Test
    fun anUnknownStorageRootRefusesRatherThanGuessing() {
        // 拿不到外部存储根时给的是空串，绝不能拼出一个 "/Games"
        assertNull(oxideGamePathFromRelative("Games", storageRoot = ""))
    }
}

/** 存储抽屉里那两枚动作的可见性 */
class OxideStorageActionsVisibilityTest {

    @Test
    fun addIsHiddenWhileTheFlowIsAlreadyRunning() {
        // 连按两下会打开两个 SAF 选择器，第二个用户已经不知道自己在给谁起名了
        assertFalse(oxideStorageAddVisible(adding = true))
        assertTrue(oxideStorageAddVisible(adding = false))
    }

    @Test
    fun cleanupIsRefusedWhenNoVersionIsInstalled() {
        // 一次都没有装过时，"所有版本需要的资源文件"是空集，
        // GameAssetCleaner 会把整个 assets 目录当成冗余删掉。后端没有这道保护。
        assertFalse(oxideStorageCleanupEnabled(installedCount = 0))
        assertTrue(oxideStorageCleanupEnabled(installedCount = 1))
    }
}

/** 自定义运行时的删除可见性 */
class OxideJavaRuntimeDeleteTest {

    @Test
    fun aLauncherProvidedRuntimeCannotBeDeleted() {
        val builtIn = Runtime(
            name = "jre17",
            versionString = "17.0.8",
            arch = "arm64-v8a",
            javaVersion = 17,
            isProvidedByLauncher = true,
            isJDK8 = false,
        )
        assertFalse(oxideJavaRuntimeDeletable(builtIn))
        assertFalse(oxideJavaRuntimeDeleteVisible(builtIn))
    }

    @Test
    fun anImportedRuntimeCanBeDeleted() {
        val imported = Runtime(
            name = "temurin-21.0.2",
            versionString = "21.0.2",
            arch = "arm64-v8a",
            javaVersion = 21,
            isProvidedByLauncher = false,
            isJDK8 = false,
        )
        assertTrue(oxideJavaRuntimeDeletable(imported))
        assertTrue(oxideJavaRuntimeDeleteVisible(imported))
    }

    @Test
    fun aBrokenImportedRuntimeIsStillDeletable() {
        // 扫不出版本号的运行时正是最该被删掉的那一个：它占着地方又跑不起来
        val broken = Runtime("broken")   // 其余字段全部走默认值：javaVersion 0、无版本号
        assertEquals(0, broken.javaVersion)
        assertTrue(oxideJavaRuntimeDeletable(broken))
    }
}
