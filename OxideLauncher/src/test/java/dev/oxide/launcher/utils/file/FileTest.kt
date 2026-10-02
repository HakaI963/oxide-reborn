/*
 * Oxide Reborn
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

package dev.oxide.launcher.utils.file

import org.jackhuang.hmcl.util.DigestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class FileTest {

    /** SHA-1 of the ASCII string "abc". */
    private val abcSha1 = "a9993e364706816aba3e25717850c26c9cd0d89d"

    /** SHA-1 of an empty file. */
    private val emptySha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709"

    @Test
    fun calculateFileSha1OfKnownContent() {
        val dir = createTempDir()
        try {
            val abc = dir.resolve("abc.txt").apply { writeText("abc") }
            assertEquals(abcSha1, runBlockingIo { calculateFileSha1(abc) })

            val empty = dir.resolve("empty.bin").apply { writeBytes(ByteArray(0)) }
            assertEquals(emptySha1, runBlockingIo { calculateFileSha1(empty) })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun calculateFileSha1IsIndependentOfReadBufferBoundaries() {
        val dir = createTempDir()
        try {
            // 8192 bytes is the internal buffer size, so these sizes straddle it and verify the
            // read loop does not drop or duplicate bytes at the boundary.
            val random = Random(seed = 7)
            for (size in intArrayOf(8191, 8192, 8193, 20_000)) {
                val file = dir.resolve("blob-$size.bin")
                file.writeBytes(ByteArray(size) { random.nextInt(256).toByte() })

                assertEquals(
                    "size $size",
                    DigestUtils.digestToString("SHA-1", file.toPath()),
                    runBlockingIo { calculateFileSha1(file) }
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun compareSha1MatchesComputedDigest() {
        val dir = createTempDir()
        try {
            val file = dir.resolve("compare.txt").apply { writeText("oxide") }
            val digest = runBlockingIo { calculateFileSha1(file) }

            assertTrue(compareSHA1(file, digest))
            assertTrue(compareSHA1(file, digest.uppercase()))
            assertFalse(compareSHA1(file, emptySha1))
            // A null expected digest falls back to the caller's default instead of matching.
            assertFalse(compareSHA1(file, null))
            assertTrue(compareSHA1(file, null, default = true))
            // A missing file is never considered valid, whatever the default is.
            assertFalse(compareSHA1(dir.resolve("missing.txt"), digest))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "oxide-file-test-${System.nanoTime()}")
        assertTrue("could not create the temporary directory $dir", dir.mkdirs())
        return dir
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }
}