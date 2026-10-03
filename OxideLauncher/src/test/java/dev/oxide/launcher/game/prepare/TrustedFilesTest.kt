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

package dev.oxide.launcher.game.prepare

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 缓存唯一用来替代完整 SHA-1 校验的东西就是"文件身份"。
 * 这些测试不碰 Android 框架，只针对这一层纯逻辑。
 */
class TrustedFilesTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "oxide-trusted-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeBytes(bytes) }

    private fun trusted(vararg files: File): TrustedFiles =
        TrustedFiles.of(files.associate { it.absolutePath to longArrayOf(it.length(), it.lastModified()) })

    @Test
    fun aFileRecordedAtTheSameSizeAndTimeIsTrusted() {
        val a = file("a.jar", ByteArray(64) { 7 })
        assertTrue(trusted(a).isTrusted(a))
    }

    @Test
    fun aFileThatWasNeverRecordedIsNotTrusted() {
        val recorded = file("a.jar", ByteArray(8))
        val other = file("b.jar", ByteArray(8))
        assertFalse(trusted(recorded).isTrusted(other))
    }

    @Test
    fun aChangedSizeInvalidatesTrust() {
        val a = file("a.jar", ByteArray(8))
        val t = trusted(a)
        a.writeBytes(ByteArray(16))
        assertFalse("a resized file must not be trusted", t.isTrusted(a))
    }

    @Test
    fun aChangedModificationTimeInvalidatesTrust() {
        val a = file("a.jar", ByteArray(8))
        val t = trusted(a)
        assertTrue(a.setLastModified(a.lastModified() + 5_000L))
        assertFalse("a touched file must not be trusted", t.isTrusted(a))
    }

    @Test
    fun sameSizeButNewerTimeStillInvalidatesTrust() {
        // 覆盖"内容被改写但长度不变"这一最常见的情形
        val a = file("a.jar", ByteArray(8))
        val t = trusted(a)
        val stamp = a.lastModified()
        a.writeBytes(ByteArray(8) { 3 })
        assertTrue(a.setLastModified(stamp + 2_000L))
        assertEquals(8L, a.length())
        assertFalse("a rewritten file of the same length must not be trusted", t.isTrusted(a))
    }

    @Test
    fun aDeletedFileIsNotTrusted() {
        val a = file("a.jar", ByteArray(8))
        val t = trusted(a)
        assertTrue(a.delete())
        assertFalse(t.isTrusted(a))
    }

    @Test
    fun theRenderAndParseRoundTripKeepsEveryStamp() {
        val files = listOf(
            file("one.jar", ByteArray(3)),
            file("nested/two.jar", ByteArray(5)),
            file("nested/deeper/three.jar", ByteArray(7))
        )
        val parsed = TrustedFiles.parse(TrustedFiles.render(files).lineSequence())

        assertEquals(files.size, parsed.size)
        for (f in files) assertTrue("$f should survive the round trip", parsed.isTrusted(f))
    }

    @Test
    fun renderingSkipsEntriesThatAreNotFiles() {
        val f = file("a.jar", ByteArray(4))
        val sub = File(dir, "sub").apply { mkdirs() }

        val parsed = TrustedFiles.parse(TrustedFiles.render(listOf(f, sub)).lineSequence())

        assertEquals(1, parsed.size)
        assertTrue(parsed.isTrusted(f))
    }

    @Test
    fun malformedLinesAreIgnoredRatherThanFatal() {
        val parsed = TrustedFiles.parse(
            sequenceOf(
                "",
                "garbage",
                "8\tmissing-second-tab\t/path",
                "notanumber\t1\t/other",
                "12\t34\t/good/path"
            )
        )
        assertEquals(1, parsed.size)
        assertFalse(parsed.isTrusted(File(dir, "other")))
        assertFalse(parsed.isTrusted(File(dir, "path")))
        // 唯一被接受的那行指向一个真实存在的文件
        val good = File(dir, "good").apply { writeText("x") }
        assertTrue(
            TrustedFiles.parse("12\t34\t${good.absolutePath}".lineSequence()).isTrusted(good)
        )
    }

    @Test
    fun aPathWithSpacesAndTabsSurvivesParsing() {
        // 路径里可能含有空格；制表符不可能出现在文件名里，所以用制表符分隔是安全的
        val a = file("a file with spaces.jar", ByteArray(9))
        val parsed = TrustedFiles.parse(TrustedFiles.render(listOf(a)).lineSequence())
        assertEquals(1, parsed.size)
        assertTrue(parsed.isTrusted(a))
    }

    @Test
    fun theEmptySetTrustsNothing() {
        assertEquals(0, TrustedFiles.EMPTY.size)
        assertFalse(TrustedFiles.EMPTY.isTrusted(File(dir, "nope.jar")))
    }
}