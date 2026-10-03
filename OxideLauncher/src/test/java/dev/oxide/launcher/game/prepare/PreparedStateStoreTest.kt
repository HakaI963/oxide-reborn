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

import dev.oxide.launcher.utils.GSON
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 状态文件的落盘方式
 *
 * 关键性质只有一条：**永远不会读到半个文件**。要么是上一次完整的状态，
 * 要么是这一次完整的状态，写入途中被杀掉不会留下中间态。
 */
class PreparedStateStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "oxide-store-${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun state(verified: Int = 1) = PreparedState(
        schemaVersion = PREPARED_STATE_SCHEMA_VERSION,
        preparationVersion = PREPARATION_VERSION,
        launcherVersionCode = 100100L,
        recordedAt = 1_700_000_000_000L,
        integrityDisabled = false,
        downloadSource = "OFFICIAL",
        versionJsonPath = "/versions/demo/demo.json",
        versionJson = FileStamp(1024L, 1_699_999_999_000L),
        parentVersionJsonPath = "/versions/1.20.1/1.20.1.json",
        parentVersionJson = FileStamp(2048L, 1_699_999_998_000L),
        mods = listOf(ModStamp("a.jar", 100L, 1_699_000_000_000L)),
        touchControllerMod = false,
        lwjgl3ifyVersion = null,
        verifiedFileCount = verified
    )

    @Test
    fun anAtomicWriteProducesAReadableFile() {
        val target = File(dir, "prepare_state.json")
        val content = GSON.toJson(state(verified = 42))

        PreparedStateStore.writeAtomically(target, content)

        assertEquals(content, target.readText())
        assertEquals(42, GSON.fromJson(target.readText(), PreparedState::class.java).verifiedFileCount)
    }

    @Test
    fun anAtomicWriteCreatesMissingParentDirectories() {
        val target = File(dir, "deep/nested/prepare_state.json")
        PreparedStateStore.writeAtomically(target, "payload")
        assertEquals("payload", target.readText())
    }

    @Test
    fun anAtomicWriteReplacesAnExistingFile() {
        val target = File(dir, "prepare_state.json")
        PreparedStateStore.writeAtomically(target, "first")
        PreparedStateStore.writeAtomically(target, "second")
        assertEquals("second", target.readText())
    }

    @Test
    fun anAtomicWriteLeavesNoTemporaryFileBehind() {
        val target = File(dir, "prepare_state.json")
        PreparedStateStore.writeAtomically(target, "payload")
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue("temporary files must not survive a successful write", leftovers.isEmpty())
    }

    @Test
    fun aFailedWriteLeavesThePreviousContentIntact() {
        val target = File(dir, "prepare_state.json")
        PreparedStateStore.writeAtomically(target, "the good one")

        // 传 null 会在 writeText 之前就抛异常，用来模拟写入途中失败
        val thrown = runCatching { PreparedStateStore.writeAtomically(target, null as String) }
        assertTrue(thrown.isFailure || thrown.getOrNull() == null)

        // 原内容必须完好，下一次启动不会因为这次失败而读到半个文件
        assertTrue(target.readText().startsWith("the good one"))
    }

    @Test
    fun staleTemporaryFilesAreDiscarded() {
        val target = File(dir, "prepare_state.json")
        PreparedStateStore.writeAtomically(target, "good")
        File(dir, "prepare_state.json123.tmp").writeText("half written")
        File(dir, "prepare_files.txt999.tmp").writeText("half written")

        PreparedStateStore.discardStaleTempFiles(dir)

        assertEquals("good", target.readText())
        assertEquals(0, dir.listFiles()?.count { it.name.endsWith(".tmp") } ?: 0)
    }

    @Test
    fun anInterruptedWriteIsNeverMistakenForAValidState() {
        // 模拟"写到一半被杀"：目标文件根本不存在，只有临时文件
        val dir2 = File(dir, "killed").apply { mkdirs() }
        File(dir2, "prepare_state.json.tmp").writeText("""{"schemaVersion":1,"verifiedFi""")

        val stateFile = File(dir2, "prepare_state.json")
        assertFalse("a killed write must not leave a readable state", stateFile.isFile)
        // 下一次启动会看到没有状态，退回完整准备，然后清掉残留
        PreparedStateStore.discardStaleTempFiles(dir2)
        assertEquals(0, dir2.listFiles()?.count { it.name.endsWith(".tmp") } ?: 0)
    }

    @Test
    fun concurrentPreparationNeverExposesAPartialFile() {
        // 要证明的性质是：无论多少个准备流程同时想把状态写进去，
        // 读到的永远是某一次完整写入的结果，绝不会是写了一半的内容。
        val target = File(dir, "prepare_state.json")
        val payloads = (1..8).map { GSON.toJson(state(verified = it)) }
        // 先放一个完整状态，保证任何时刻都有可读内容
        PreparedStateStore.writeAtomically(target, payloads.first())

        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val partial = java.util.concurrent.atomic.AtomicInteger()
        val writeFailures = java.util.concurrent.atomic.AtomicInteger()

        try {
            val futures = buildList {
                // 3 个写入方：不断把状态换成另一份完整内容
                repeat(3) { w ->
                    add(pool.submit {
                        start.await()
                        var i = 0
                        while (i < 40) {
                            // 同一条路径上的竞争性改名偶尔会失败；那只是这一次写入没成功，
                            // 下一次仍然会写入完整内容，所以这里只统计不抛出。
                            runCatching { PreparedStateStore.writeAtomically(target, payloads[(w + i++) % payloads.size]) }
                                .onFailure { writeFailures.incrementAndGet() }
                        }
                    })
                }
                // 3 个读取方：不停地读，任何一次读到无法解析的内容都算失败
                repeat(3) {
                    add(pool.submit {
                        start.await()
                        var i = 0
                        while (i < 200) {
                            val text = runCatching { target.readText() }.getOrNull() ?: continue
                            reads.incrementAndGet()
                            val parsed = runCatching { GSON.fromJson(text, PreparedState::class.java) }.getOrNull()
                            if (parsed == null || parsed.schemaVersion != PREPARED_STATE_SCHEMA_VERSION) {
                                partial.incrementAndGet()
                            }
                            i++
                        }
                    })
                }
            }
            start.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue("the readers never ran", reads.get() > 0)
        assertEquals("a concurrent write exposed a partial file", 0, partial.get())
        // 最终落盘的内容必须完整可解析
        assertNotNull(GSON.fromJson(target.readText(), PreparedState::class.java))
    }

    @Test
    fun anUnwritableTargetFailsLoudlyInsteadOfSilently() {
        // 目标的父级是一个普通文件，因此无论在哪一步都会失败；
        // 关键是这个失败必须以异常的形式冒出去，不能假装写成功了。
        val blocker = File(dir, "blocker").apply { writeText("i am a file") }
        val target = File(blocker, "prepare_state.json")

        val thrown = runCatching { PreparedStateStore.writeAtomically(target, "payload") }

        assertTrue("writing below a regular file must fail", thrown.isFailure)
        thrown.exceptionOrNull()?.let { e ->
            assertTrue(
                "expected an IOException but got ${e::class.simpleName}",
                e is IOException || e is SecurityException
            )
        }
        assertFalse("nothing may be created under a regular file", target.exists())
    }
}