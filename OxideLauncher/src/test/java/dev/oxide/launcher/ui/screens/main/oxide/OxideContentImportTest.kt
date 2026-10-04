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

import dev.oxide.launcher.game.download.modpack.install.UnsupportedPackReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容管理器从设备导入的那一档
 *
 * 两条纪律：**每一类支持导入的都必须说清楚收什么后缀**，
 * 以及**截图不给导入**——截图是游戏自己写出来的产物。
 */
class OxideContentImportTest {

    @Test
    fun modsTakeJarFiles() {
        assertEquals("jar", oxideContentImportExtension(OxideContentCategory.Mods))
    }

    @Test
    fun packsAndShadersTakeZipFiles() {
        assertEquals("zip", oxideContentImportExtension(OxideContentCategory.ResourcePacks))
        assertEquals("zip", oxideContentImportExtension(OxideContentCategory.Shaders))
    }

    @Test
    fun savesTakeAZipThatIsThenUnpacked() {
        assertEquals("zip", oxideContentImportExtension(OxideContentCategory.Saves))
        assertTrue(oxideContentImportUnpacks(OxideContentCategory.Saves))
    }

    @Test
    fun savesAreTheOnlyCategoryThatNeedsUnpacking() {
        // 其余三类拷进去就是最终形态
        assertFalse(oxideContentImportUnpacks(OxideContentCategory.Mods))
        assertFalse(oxideContentImportUnpacks(OxideContentCategory.ResourcePacks))
        assertFalse(oxideContentImportUnpacks(OxideContentCategory.Shaders))
    }

    @Test
    fun screenshotsAreNotImportableAtAll() {
        assertEquals(null, oxideContentImportExtension(OxideContentCategory.Screenshots))
    }

    @Test
    fun everyCategoryDecidesSomethingAndTheExtensionsAreNeverBlank() {
        // 空后缀会被 rememberMultipleUriImportTaskBuilder 当成"什么都收"，
        // 于是弹出的系统选择器里出现整个文件系统
        OxideContentCategory.entries.forEach { category ->
            val extension = oxideContentImportExtension(category)
            assertTrue(
                "a category must either take a real extension or take nothing: $category",
                extension == null || extension.isNotBlank(),
            )
        }
    }
}

/** 整合包导入：不支持时的说法 */
class OxideModpackImportDetailTest {

    @Test
    fun everyUnsupportedReasonCarriesItsOwnExplanation() {
        UnsupportedPackReason.entries.forEach { reason ->
            val lines = unsupportedLines(reason)
            assertEquals(1, lines.size)
            assertTrue(
                "an unsupported pack must explain itself: $reason",
                lines.single().isNotBlank(),
            )
        }
    }

    @Test
    fun twoReasonsNeverShareTheSameWording() {
        val texts = UnsupportedPackReason.entries.map { reason ->
            unsupportedLines(reason).single()
        }
        assertEquals(texts.size, texts.distinct().size)
    }
}
