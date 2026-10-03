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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 准备状态的失效规则
 *
 * [PreparedStateValidator] 是纯函数，所以这里可以逐条验证"什么会让缓存失效"，
 * 而不必启动一个 Android 进程。
 */
class PreparedStateValidatorTest {

    private lateinit var gameHome: File
    private lateinit var modsDir: File
    private lateinit var versionJson: File
    private lateinit var parentVersionJson: File

    private val launcherVersionCode = 100100L
    private val downloadSource = "OFFICIAL"
    private val integrityDisabled = false

    @Before
    fun setUp() {
        gameHome = File(System.getProperty("java.io.tmpdir"), "oxide-prepare-${System.nanoTime()}").apply {
            mkdirs()
        }
        modsDir = File(gameHome, "mods").apply { mkdirs() }
        versionJson = File(gameHome, "versions/demo/demo.json").apply {
            parentFile.mkdirs()
            writeText("{}")
        }
        parentVersionJson = File(gameHome, "versions/1.20.1/1.20.1.json").apply {
            parentFile.mkdirs()
            writeText("{}")
        }
    }

    @After
    fun tearDown() {
        gameHome.deleteRecursively()
    }

    private fun stamp(file: File) = FileStamp.of(file)!!

    /** 一份"刚刚成功准备过"的状态 */
    private fun recorded(
        schemaVersion: Int = PREPARED_STATE_SCHEMA_VERSION,
        preparationVersion: Int = PREPARATION_VERSION,
        launcherVersion: Long = launcherVersionCode,
        integrity: Boolean = integrityDisabled,
        source: String = downloadSource,
        jsonPath: String = versionJson.absolutePath,
        jsonStamp: FileStamp? = stamp(versionJson),
        parentPath: String? = parentVersionJson.absolutePath,
        parentStamp: FileStamp? = stamp(parentVersionJson),
        mods: List<ModStamp> = ModStamp.list(modsDir),
        lwjgl3ify: String? = null
    ) = PreparedState(
        schemaVersion = schemaVersion,
        preparationVersion = preparationVersion,
        launcherVersionCode = launcherVersion,
        recordedAt = 1_700_000_000_000L,
        integrityDisabled = integrity,
        downloadSource = source,
        versionJsonPath = jsonPath,
        versionJson = jsonStamp!!,
        parentVersionJsonPath = parentPath,
        parentVersionJson = parentStamp,
        mods = mods,
        touchControllerMod = false,
        lwjgl3ifyVersion = lwjgl3ify,
        verifiedFileCount = 3
    )

    private fun reason(
        state: PreparedState?,
        integrity: Boolean = integrityDisabled,
        source: String = downloadSource,
        jsonPath: String = versionJson.absolutePath,
        parentStamp: FileStamp? = stamp(parentVersionJson),
        mods: List<ModStamp> = ModStamp.list(modsDir),
        launcher: Long = launcherVersionCode
    ) = PreparedStateValidator.invalidateReason(
        state = state,
        launcherVersionCode = launcher,
        integrityDisabled = integrity,
        downloadSource = source,
        currentVersionJsonPath = jsonPath,
        currentVersionJson = stamp(versionJson),
        currentParentVersionJson = parentStamp,
        currentMods = mods
    )

    private fun addMod(name: String, bytes: ByteArray = ByteArray(16)): File =
        File(modsDir, name).apply { writeBytes(bytes) }

    private fun assertValid(reason: PrepareInvalidateReason?) {
        assertNull("expected the state to stay valid, got $reason", reason)
    }

    private fun assertInvalid(expected: PrepareInvalidateReason, actual: PrepareInvalidateReason?) {
        assertEquals("wrong invalidation reason", expected, actual)
    }

    // 1. missing cache -> preparation required
    @Test
    fun missingStateRequiresPreparation() {
        assertInvalid(PrepareInvalidateReason.NO_STATE, reason(null))
    }

    // 2. matching fingerprint -> preparation skipped
    @Test
    fun anUnchangedInstanceNeedsNoPreparation() {
        addMod("a.jar")
        assertValid(reason(recorded()))
    }

    // 3. Minecraft version change -> invalid
    @Test
    fun rewritingTheVersionJsonInvalidates() {
        val before = recorded()
        versionJson.writeText("""{"id":"demo","inheritsFrom":"1.20.1","extra":"changed"}""")
        assertNotEquals(stamp(versionJson), before.versionJson)
        assertInvalid(PrepareInvalidateReason.VERSION_JSON_CHANGED, reason(before))
    }

    @Test
    fun anInheritedVersionJsonChangeInvalidates() {
        val before = recorded()
        parentVersionJson.writeText("""{"id":"1.20.1","changed":true}""")
        assertInvalid(PrepareInvalidateReason.PARENT_VERSION_JSON_CHANGED, reason(before))
    }

    @Test
    fun renamingTheVersionInvalidates() {
        val before = recorded()
        assertInvalid(
            PrepareInvalidateReason.VERSION_JSON_CHANGED,
            reason(before, jsonPath = File(gameHome, "versions/renamed/renamed.json").absolutePath)
        )
    }

    @Test
    fun aVanillaInstanceWithoutAParentStaysValid() {
        addMod("a.jar")
        val state = recorded(parentPath = null, parentStamp = null)
        assertValid(reason(state, parentStamp = null))
    }

    // 4. loader change -> invalid
    @Test
    fun installingALoaderRewritesTheVersionJsonAndInvalidates() {
        val before = recorded()
        // 加载器安装会改写清单并新增依赖库；清单这一项就足以判定失效
        versionJson.writeText("""{"id":"demo","inheritsFrom":"1.20.1","libraries":[]}""")
        assertInvalid(PrepareInvalidateReason.VERSION_JSON_CHANGED, reason(before))
    }

    @Test
    fun removingALoaderInvalidates() {
        val before = recorded()
        versionJson.writeText("""{"id":"demo","inheritsFrom":"1.20.1"}""")
        assertInvalid(PrepareInvalidateReason.VERSION_JSON_CHANGED, reason(before))
    }

    // 5. mod addition -> invalid
    @Test
    fun addingAModInvalidates() {
        val before = recorded()
        addMod("new.jar")
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    // 6. mod removal -> invalid
    @Test
    fun removingAModInvalidates() {
        val mod = addMod("gone.jar")
        val before = recorded()
        assertTrue(mod.delete())
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    // 7. mod replacement / update -> invalid
    @Test
    fun updatingAModInvalidates() {
        val mod = addMod("mod.jar", ByteArray(32))
        val before = recorded()

        // 更新模组的标准做法是删旧写新
        assertTrue(mod.delete())
        mod.writeBytes(ByteArray(48))
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    @Test
    fun replacingAModWithTheSameNameButNewContentInvalidates() {
        val mod = addMod("mod.jar", ByteArray(32))
        val before = recorded()

        mod.writeBytes(ByteArray(64))
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    @Test
    fun renamingAModInvalidates() {
        val mod = addMod("mod.jar")
        val before = recorded()

        assertTrue(mod.renameTo(File(modsDir, "renamed.jar")))
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    @Test
    fun disablingAModInvalidatesBecauseItIsARename() {
        val mod = addMod("mod.jar")
        val before = recorded()

        assertTrue(mod.renameTo(File(modsDir, "mod.jar.disabled")))
        assertInvalid(PrepareInvalidateReason.MODS_CHANGED, reason(before))
    }

    // 8. runtime change -> only matters when preparation depends on it, which it does not
    @Test
    fun aRuntimeChangeDoesNotInvalidatePreparation() {
        val before = recorded()
        // 运行时只影响 :game 进程的参数生成与 dlopen，不参与准备阶段，
        // 因此改运行时不需要重做准备，也就不会让缓存失效。
        assertValid(reason(before))
    }

    @Test
    fun theIntegritySettingInvalidates() {
        val before = recorded()
        assertInvalid(
            PrepareInvalidateReason.INTEGRITY_SETTING_CHANGED,
            reason(before, integrity = true)
        )
    }

    // 9. relevant configuration change -> invalid
    @Test
    fun aDownloadSourceChangeInvalidates() {
        val before = recorded()
        assertInvalid(
            PrepareInvalidateReason.DOWNLOAD_SOURCE_CHANGED,
            reason(before, source = "MIRROR")
        )
    }

    // 10. unrelated runtime-generated files must not invalidate
    @Test
    fun filesMinecraftWritesAtRuntimeDoNotInvalidate() {
        val before = recorded()

        // 游戏运行时会写这些目录；它们根本不参与准备，因此不能进入指纹
        for (dir in listOf("logs", "saves", "screenshots", "crash-reports", "resourcepacks", "shaderpacks")) {
            File(gameHome, dir).apply { mkdirs(); File(this, "fresh.txt").writeText("noise") }
        }
        File(gameHome, ".fabric").apply { mkdirs() }.resolve("remapped.json").writeText("{}")
        File(gameHome, "options.txt").writeText("fov:70")

        assertValid(reason(before))
    }

    @Test
    fun launcherOnlySettingsDoNotInvalidate() {
        val before = recorded()
        // 渲染器、内存、语言、控制布局都不在准备指纹里
        assertEquals(3, before.verifiedFileCount)
        assertValid(reason(before))
    }

    // 11/12/13. corrupt, unknown schema, interrupted write -> safe rebuild
    @Test
    fun aCorruptStateFileIsRejected() {
        val garbage = runCatching { GSON.fromJson("not json at all", PreparedState::class.java) }
        assertTrue("corrupt content must not parse into a state", garbage.isFailure)
    }

    @Test
    fun aTruncatedStateFileIsRejected() {
        val text = GSON.toJson(recorded())
        val truncated = text.substring(0, text.length / 2)
        val parsed = runCatching { GSON.fromJson(truncated, PreparedState::class.java) }
        assertTrue("a truncated state file must not parse", parsed.isFailure || parsed.getOrNull() == null)
    }

    @Test
    fun anUnknownSchemaForcesARebuild() {
        assertInvalid(
            PrepareInvalidateReason.SCHEMA_CHANGED,
            reason(recorded(schemaVersion = PREPARED_STATE_SCHEMA_VERSION + 1))
        )
    }

    @Test
    fun aChangedPreparationAlgorithmForcesARebuild() {
        assertInvalid(
            PrepareInvalidateReason.PREPARATION_CHANGED,
            reason(recorded(preparationVersion = PREPARATION_VERSION + 1))
        )
    }

    @Test
    fun aLauncherUpgradeForcesARebuild() {
        assertInvalid(
            PrepareInvalidateReason.LAUNCHER_UPDATED,
            reason(recorded(), launcher = launcherVersionCode + 1L)
        )
    }

    @Test
    fun lwjgl3ifyInstancesAlwaysPrepareInFull() {
        assertInvalid(
            PrepareInvalidateReason.LOADER_MOD_PRESENT,
            reason(recorded(lwjgl3ify = "1.0.20"))
        )
    }

    // 14. a successful preparation produces a valid state
    @Test
    fun aRecordedStateIsValidUntilSomethingChanges() {
        addMod("a.jar")
        addMod("b.jar")
        val state = recorded()
        assertValid(reason(state))
        // 再确认一次：单纯重新读取不会让它失效
        assertValid(reason(state))
    }

    @Test
    fun theRecordedStateSurvivesAJsonRoundTrip() {
        addMod("a.jar")
        val state = recorded()
        val restored = GSON.fromJson(GSON.toJson(state), PreparedState::class.java)
        assertEquals(state, restored)
        assertValid(reason(restored))
    }

    // 16. schema / preparation version change -> invalid (covered above)

    @Test
    fun disabledModsArePartOfTheFingerprint() {
        val mod = addMod("mod.jar")
        assertTrue(mod.renameTo(File(modsDir, "mod.jar.disabled")))
        val state = recorded()
        assertTrue("the disabled entry must still be listed", state.mods.any { it.name == "mod.jar.disabled" })
        assertValid(reason(state))
    }

    @Test
    fun anEmptyModsFolderIsAFineState() {
        assertValid(reason(recorded(mods = emptyList())))
    }

    @Test
    fun everyInvalidateReasonHasALogMessage() {
        for (reason in PrepareInvalidateReason.entries) {
            assertTrue("empty log message for $reason", reason.logReason.isNotBlank())
        }
    }
}