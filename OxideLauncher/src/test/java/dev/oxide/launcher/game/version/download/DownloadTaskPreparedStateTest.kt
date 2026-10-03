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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.version.download

import dev.oxide.launcher.game.prepare.TrustedFiles
import org.apache.commons.codec.digest.DigestUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 准备状态真正生效的那一步：文件校验能不能被"已验证过"的身份替代
 *
 * 这是整件事的核心机制——[DownloadTask.verifiedStamp] 只在拿到正面证据时才会被写入，
 * 而准备状态正是靠这些标记生成的。一个没有通过校验的文件永远不会被记成已准备。
 */
class DownloadTaskPreparedStateTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "oxide-dltask-${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** 与 FileUtils.compareSHA1 使用同一种十六进制小写表示 */
    private fun sha1Of(bytes: ByteArray): String = DigestUtils.sha1Hex(bytes)

    private fun task(
        file: File,
        sha1: String? = null,
        size: Long = -1L,
        isDownloadable: Boolean = true,
        verifyIntegrity: Boolean = true
    ) = DownloadTask(
        urls = listOf("https://example.invalid/a.jar"),
        verifyIntegrity = verifyIntegrity,
        targetFile = file,
        sha1 = sha1,
        size = size,
        isDownloadable = isDownloadable
    )

    @Test
    fun aCorrectFileIsVerifiedAndRecorded() {
        val bytes = ByteArray(256) { (it % 7).toByte() }
        val file = File(dir, "good.jar").apply { writeBytes(bytes) }

        val t = task(file, sha1Of(bytes))
        assertTrue(t.existingFileValid())
        assertNotNull("a verified file must carry a stamp", t.verifiedStamp)
        assertEquals(file.length(), t.verifiedStamp!![0])
        assertEquals(file.lastModified(), t.verifiedStamp!![1])
    }

    @Test
    fun aCorruptedFileIsNotRecordedAndIsRemoved() {
        val file = File(dir, "bad.jar").apply { writeBytes(ByteArray(256) { 1 }) }
        val t = task(file, sha1Of(ByteArray(256) { 2 }))

        assertFalse("a file with the wrong hash must not be valid", t.existingFileValid())
        assertNull("a failed verification must not leave a stamp", t.verifiedStamp)
        assertFalse("the mismatching file is removed so it can be re-downloaded", file.exists())
    }

    @Test
    fun aMissingFileIsNotRecorded() {
        val file = File(dir, "absent.jar")
        val t = task(file, sha1Of(ByteArray(4)))
        assertFalse(t.existingFileValid())
        assertNull(t.verifiedStamp)
    }

    @Test
    fun aTrustedFileSkipsVerificationAndIsRecorded() {
        val bytes = ByteArray(256) { 3 }
        val file = File(dir, "trusted.jar").apply { writeBytes(bytes) }
        val trusted = TrustedFiles.of(
            mapOf(file.absolutePath to longArrayOf(file.length(), file.lastModified()))
        )

        // 给一个错误的哈希：如果真的走了完整校验，它一定会被判为无效
        val t = task(file, sha1Of(ByteArray(256) { 9 }), size = bytes.size.toLong())
        assertTrue("a trusted file must not be re-hashed", t.existingFileValid(trusted))
        assertNotNull(t.verifiedStamp)
        assertEquals(file.length(), t.verifiedStamp!![0])
    }

    @Test
    fun aFileThatStoppedMatchingItsTrustedIdentityFallsBackToFullVerification() {
        val bytes = ByteArray(128) { 5 }
        val file = File(dir, "drifted.jar").apply { writeBytes(bytes) }
        val trusted = TrustedFiles.of(
            mapOf(file.absolutePath to longArrayOf(file.length() + 1L, file.lastModified()))
        )

        val t = task(file, sha1Of(bytes))
        assertTrue("the real hash is still correct", t.existingFileValid(trusted))
        assertNotNull(t.verifiedStamp)
        assertEquals(file.length(), t.verifiedStamp!![0])
    }

    @Test
    fun aTrustedFileWithACorruptedBodyIsStillAccepted() {
        // 这是 size+mtime 方案已知的、也是唯一已知的缺口：
        // 有人刻意改写文件并同时还原大小与修改时间。这里把它固定成显式行为。
        val file = File(dir, "tampered.jar").apply { writeBytes(ByteArray(64)) }
        val stamp = file.lastModified()
        val trusted = TrustedFiles.of(
            mapOf(file.absolutePath to longArrayOf(file.length(), stamp))
        )
        file.writeBytes(ByteArray(64) { 42 })
        assertTrue(file.setLastModified(stamp))

        val t = task(file, sha1Of(ByteArray(64)))
        assertTrue(t.existingFileValid(trusted))
    }

    @Test
    fun aMissingFileIsNotTrustedEvenWhenItWasRecorded() {
        val file = File(dir, "gone.jar").apply { writeBytes(ByteArray(8)) }
        val trusted = TrustedFiles.of(
            mapOf(file.absolutePath to longArrayOf(file.length(), file.lastModified()))
        )
        assertTrue(file.delete())

        val t = task(file, sha1Of(ByteArray(8)))
        assertFalse(t.existingFileValid(trusted))
        assertNull(t.verifiedStamp)
    }

    @Test
    fun anUndownloadableTargetOnlyNeedsToExist() {
        // Forge 的 client 就是这种情况：不允许下载，只要文件在就算数
        val file = File(dir, "forge-client.jar").apply { writeBytes(ByteArray(32)) }
        val t = task(file, sha1 = null, isDownloadable = false)
        assertTrue(t.existingFileValid())
        assertNotNull("an existing undownloadable target is still a positive result", t.verifiedStamp)
    }

    @Test
    fun integrityChecksCanBeTurnedOffEntirely() {
        val file = File(dir, "unchecked.jar").apply { writeBytes(ByteArray(16) { 1 }) }
        val t = task(file, sha1Of(ByteArray(16) { 2 }), verifyIntegrity = false)
        assertTrue(t.existingFileValid())
        assertNotNull(t.verifiedStamp)
    }

    @Test
    fun everyVerifiedTaskProducesTheStampsThePreparedStateNeeds() {
        val files = (1..5).map { i ->
            val bytes = ByteArray(32) { i.toByte() }
            File(dir, "f$i.jar").apply { writeBytes(bytes) } to sha1Of(bytes)
        }

        val stamps = files.map { (file, sha1) ->
            task(file, sha1).also { assertTrue(it.existingFileValid()) }.verifiedStamp
        }

        assertEquals(5, stamps.count { it != null })
        assertTrue("stamps must carry size and mtime", stamps.all { it != null && it.size == 2 })
    }
}