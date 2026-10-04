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

import dev.oxide.launcher.filemanager.logic.compress.CompressFormat
import dev.oxide.launcher.filemanager.logic.entry.ArchiveType
import dev.oxide.launcher.filemanager.logic.ops.ConflictResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * 文件页的动作可见性
 *
 * 这些用例把一条纪律钉死：**界面上出现的东西必须真能做事**。
 * 后端 `EntryController.showShare` 对目录直接返回，`EntryController.rename` 要条目
 * 可写，`ExtractController.showExtract` 只认 `ArchiveType`——界面只要比这宽松一点，
 * 就会留下一个按下去毫无反应的按钮。
 */
class OxideFilesRowActionsTest {

    @Test
    fun writableFileGetsEveryAction() {
        val actions = oxideFilesRowActions(
            isDirectory = false,
            writable = true,
            archiveType = ArchiveType.ZIP,
            busy = false,
        )
        assertEquals(OxideFilesRowAction.entries.toList(), actions)
    }

    @Test
    fun readOnlyEntryStillGetsCopyAndCompressButNotTheWrites() {
        val actions = oxideFilesRowActions(
            isDirectory = false,
            writable = false,
            archiveType = null,
            busy = false,
        )
        assertEquals(
            listOf(OxideFilesRowAction.Copy, OxideFilesRowAction.Compress, OxideFilesRowAction.Share),
            actions,
        )
    }

    @Test
    fun directoryNeverOffersExtractOrShare() {
        // 后端对目录既不解压也不分享：ArchiveType 只会是 null，而分享要一个文件
        val actions = oxideFilesRowActions(
            isDirectory = true,
            writable = true,
            archiveType = null,
            busy = false,
        )
        assertFalse(actions.contains(OxideFilesRowAction.Extract))
        assertFalse(actions.contains(OxideFilesRowAction.Share))
    }

    @Test
    fun extractIsOnlyOfferedForAnArchiveTheBackendRecognises() {
        val plain = oxideFilesRowActions(
            isDirectory = false,
            writable = true,
            archiveType = null,
            busy = false,
        )
        val archive = oxideFilesRowActions(
            isDirectory = false,
            writable = true,
            archiveType = ArchiveType.TAR,
            busy = false,
        )
        assertFalse(plain.contains(OxideFilesRowAction.Extract))
        assertTrue(archive.contains(OxideFilesRowAction.Extract))
    }

    @Test
    fun busyQueueDropsOnlyTheTaskBackedActions() {
        // 压缩与复制/移动共一个任务队列，队列忙时后端会直接拒绝；
        // 删除走的是另一条链路，仍然要给——它只是先要一句确认
        val actions = oxideFilesRowActions(
            isDirectory = false,
            writable = true,
            archiveType = ArchiveType.SEVEN_Z,
            busy = true,
        )
        assertFalse(actions.contains(OxideFilesRowAction.Compress))
        assertTrue(actions.contains(OxideFilesRowAction.Delete))
        assertTrue(actions.contains(OxideFilesRowAction.Extract))
    }

    @Test
    fun everyActionHasItsOwnLabelResource() {
        val labels = OxideFilesRowAction.entries.map { it.labelRes() }
        assertEquals(
            "two actions sharing one label means the user cannot tell them apart",
            labels.size,
            labels.distinct().size,
        )
    }
}

/** 多选动作条 */
class OxideFilesSelectionActionsTest {

    @Test
    fun nothingSelectedMeansNoBarAtAll() {
        assertEquals(
            emptyList<OxideFilesSelectionAction>(),
            oxideFilesSelectionActions(selectedCount = 0, writable = true, busy = false),
        )
    }

    @Test
    fun negativeCountIsTreatedAsNothing() {
        assertTrue(oxideFilesSelectionActions(-3, writable = true, busy = false).isEmpty())
    }

    @Test
    fun writableSelectionOffersEveryAction() {
        assertEquals(
            OxideFilesSelectionAction.entries.toList(),
            oxideFilesSelectionActions(selectedCount = 4, writable = true, busy = false),
        )
    }

    @Test
    fun readOnlyDirectoryKeepsCopyButDropsTheMoves() {
        val actions = oxideFilesSelectionActions(selectedCount = 2, writable = false, busy = false)
        assertTrue(actions.contains(OxideFilesSelectionAction.Copy))
        assertFalse(actions.contains(OxideFilesSelectionAction.Cut))
        assertFalse(actions.contains(OxideFilesSelectionAction.Delete))
    }

    @Test
    fun aBusyQueueStillLeavesCopyAndDelete() {
        val actions = oxideFilesSelectionActions(selectedCount = 1, writable = true, busy = true)
        assertEquals(
            listOf(
                OxideFilesSelectionAction.Copy,
                OxideFilesSelectionAction.Cut,
                OxideFilesSelectionAction.Delete,
            ),
            actions,
        )
    }
}

/** 目录级的那一栏：导入与粘贴 */
class OxideFilesDirectoryActionsTest {

    @Test
    fun importNeedsAWritableDirectory() {
        // 后端把导入落到当前目录，目录不可写时点了必然失败
        assertTrue(oxideFilesImportVisible(writable = true))
        assertFalse(oxideFilesImportVisible(writable = false))
    }

    @Test
    fun pasteFollowsTheClipboardAndNothingElse() {
        assertTrue(oxideFilesPasteVisible(hasClipboard = true))
        assertFalse(oxideFilesPasteVisible(hasClipboard = false))
    }
}

/** 压缩包最终的文件名 */
class OxideFilesCompressNameTest {

    @Test
    fun aBareNameGetsTheChosenSuffix() {
        assertEquals("assets.zip", oxideFilesCompressFileName("assets", CompressFormat.ZIP))
    }

    @Test
    fun aNameThatAlreadyCarriesTheSuffixIsNotDoubled() {
        assertEquals("assets.zip", oxideFilesCompressFileName("assets.zip", CompressFormat.ZIP))
    }

    @Test
    fun switchingFormatReplacesTheOldSuffix() {
        assertEquals("assets.7z", oxideFilesCompressFileName("assets.zip", CompressFormat.SEVEN_Z))
        assertEquals("assets.tar", oxideFilesCompressFileName("assets.zip", CompressFormat.TAR))
    }

    @Test
    fun theLongestMatchingSuffixIsStrippedFirst() {
        // ".tar" 也是 ".tar" 的一部分，先剥长的才不会剩下一个孤零零的 ".tar"
        assertEquals(
            "logs.tar",
            oxideFilesCompressFileName("logs.tar", CompressFormat.TAR),
        )
    }

    @Test
    fun whitespaceIsTrimmedAndABlankNameStillProducesAUsableFileName() {
        assertEquals("a.zip", oxideFilesCompressFileName("  a  ", CompressFormat.ZIP))
        assertEquals(".zip", oxideFilesCompressFileName("   ", CompressFormat.ZIP))
    }

    @Test
    fun onlyZipAndSevenZAcceptAPassword() {
        assertTrue(oxideFilesCompressSupportsPassword(CompressFormat.ZIP))
        assertTrue(oxideFilesCompressSupportsPassword(CompressFormat.SEVEN_Z))
        assertFalse(oxideFilesCompressSupportsPassword(CompressFormat.TAR))
    }
}

/** 冲突对话框上写出来的那一条 */
class OxideFilesConflictTest {

    @Test
    fun theIncomingNameWins() {
        assertEquals(
            "new.zip",
            oxideFilesConflictName(Paths.get("/a/new.zip"), Paths.get("/a/old.zip")),
        )
    }

    @Test
    fun theExistingNameIsTheFallback() {
        assertEquals(
            "old.zip",
            oxideFilesConflictName(null, Paths.get("/a/old.zip")),
        )
    }

    @Test
    fun nothingKnownStillYieldsOneLineRatherThanAnEmptyRow() {
        assertEquals("—", oxideFilesConflictName(null, null))
    }

    @Test
    fun theThreeChoicesMapOntoTheBackendsThreeResolutions() {
        assertEquals(
            ConflictResolution.entries.toSet(),
            OXIDE_FILES_CONFLICT_KEYS.values.toSet(),
        )
        assertEquals(3, OXIDE_FILES_CONFLICT_KEYS.size)
    }
}
