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

import dev.oxide.launcher.BuildConfig
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.utils.GSON
import dev.oxide.launcher.utils.logging.Logger
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

private const val TAG = "PrepareCache"

/** 缓存文件自身的格式版本，结构不兼容时递增 */
const val PREPARED_STATE_SCHEMA_VERSION = 1

/**
 * 准备算法的版本
 *
 * 只要"准备阶段做了什么"发生变化——新增或移除一步准备、改动了清单处理、
 * 改动了文件校验方式——就必须递增，否则旧缓存会被误判为仍然有效。
 */
const val PREPARATION_VERSION = 1

/** 评估结果 */
sealed interface PrepareDecision {
    /** 缓存有效，可以跳过昂贵的准备工作 */
    data class Hit(
        val state: PreparedState,
        val trusted: TrustedFiles,
        val mods: List<ModStamp>
    ) : PrepareDecision

    /** 缓存无效，必须正常准备 */
    data class Miss(val reason: PrepareInvalidateReason) : PrepareDecision
}

/**
 * 启动前的准备状态缓存
 *
 * 准备阶段最贵的一步是对 client jar、全部依赖库和全部资源对象做完整校验，
 * 一次要读几百 MB；模组扫描则要为每个 jar 反复打开 zip 读取元数据。
 * 两者都是纯粹的"读输入、确认输出已在位"，所以只要输入没变就可以跳过。
 *
 * 这里因此为每个实例保存一份**准备状态**：记录准备阶段的全部输入身份，
 * 以及上一次成功准备时逐个通过校验的文件身份。下一次启动逐项比对，
 * 全部一致才跳过，任何一项对不上就退回完整流程。
 *
 * 缓存是**完全可丢弃**的：删掉它只会让下一次启动多做一次准备，不会损坏实例。
 * 任何读取异常都被当成"没有缓存"，因此损坏的缓存不会让实例无法启动。
 */
object PrepareCache {

    /** 每个实例的缓存目录，与 version.config、latest_game.log 同处一地 */
    fun stateDir(version: Version): File = version.getOxideVersionPath()

    private fun stateFile(version: Version) = File(stateDir(version), PreparedStateStore.STATE_FILE)

    private fun filesFile(version: Version) = File(stateDir(version), PreparedStateStore.FILES_FILE)

    private fun lockFile(version: Version) = File(stateDir(version), PreparedStateStore.LOCK_FILE)

    /** 当前生效的下载源名称，换源会改变文件内容，必须计入指纹 */
    private fun currentDownloadSource(): String =
        runCatching { AllSettings.gameDownloadSource.getValue().name }.getOrDefault("UNKNOWN")

    fun versionJsonPath(version: Version): String =
        File(File(version.getVersionPath()), "${version.getVersionName()}.json").absolutePath

    /**
     * 判断本次启动能否复用上一次的准备结果
     *
     * 这里只做 stat，不做任何内容读取：几千次 stat 与几百 MB 的哈希之间
     * 相差两个数量级，所以即使最后判定为未命中，也仅仅是白花了几毫秒。
     */
    fun evaluate(version: Version, modsDir: File): PrepareDecision {
        val dir = stateDir(version)
        val stateFile = stateFile(version)
        if (!stateFile.isFile) {
            return PrepareDecision.Miss(PrepareInvalidateReason.NO_STATE)
        }

        val state = runCatching { GSON.fromJson(stateFile.readText(), PreparedState::class.java) }
            .getOrNull()
            ?: return PrepareDecision.Miss(PrepareInvalidateReason.UNREADABLE)

        val jsonPath = versionJsonPath(version)
        val mods = ModStamp.list(modsDir)

        val reason = PreparedStateValidator.invalidateReason(
            state = state,
            launcherVersionCode = BuildConfig.VERSION_CODE.toLong(),
            integrityDisabled = version.skipGameIntegrityCheck(),
            downloadSource = currentDownloadSource(),
            currentVersionJsonPath = jsonPath,
            currentVersionJson = FileStamp.of(File(jsonPath)),
            currentParentVersionJson = state.parentVersionJsonPath?.let { FileStamp.of(File(it)) },
            currentMods = mods
        )
        if (reason != null) {
            return PrepareDecision.Miss(reason)
        }

        val files = filesFile(version)
        if (!files.isFile) {
            return PrepareDecision.Miss(PrepareInvalidateReason.NO_STATE)
        }
        val trusted = runCatching {
            TrustedFiles.parse(files.bufferedReader().use { it.lineSequence() })
        }.getOrElse { e ->
            Logger.warning(TAG, "Cannot read the prepared file list, falling back to full preparation", e)
            return PrepareDecision.Miss(PrepareInvalidateReason.UNREADABLE)
        }

        // 两份文件必须描述同一批文件，否则说明它们不是同一次准备留下的
        if (state.verifiedFileCount != trusted.size) {
            return PrepareDecision.Miss(PrepareInvalidateReason.UNREADABLE)
        }

        return PrepareDecision.Hit(state, trusted, mods)
    }

    /**
     * 在一次成功的准备之后写入新的状态
     *
     * 只在准备完全成功后才调用；失败或被取消时旧状态原样保留。
     *
     * @param verifiedFiles 本次真正通过校验的文件
     */
    fun record(
        version: Version,
        parentVersionJsonPath: String?,
        modsDir: File,
        touchControllerMod: Boolean,
        lwjgl3ifyVersion: String?,
        verifiedFiles: List<File>
    ) {
        withInstanceLock(version) {
            val dir = stateDir(version)
            // 上一次留下的临时文件没有任何价值，先清掉
            runCatching { PreparedStateStore.discardStaleTempFiles(dir) }

            val jsonPath = versionJsonPath(version)
            val jsonStamp = FileStamp.of(File(jsonPath))
            if (jsonStamp == null) {
                Logger.warning(
                    TAG,
                    "The version JSON disappeared during preparation, not recording the prepared state"
                )
                return@withInstanceLock
            }

            val state = PreparedState(
                schemaVersion = PREPARED_STATE_SCHEMA_VERSION,
                preparationVersion = PREPARATION_VERSION,
                launcherVersionCode = BuildConfig.VERSION_CODE.toLong(),
                recordedAt = System.currentTimeMillis(),
                integrityDisabled = version.skipGameIntegrityCheck(),
                downloadSource = currentDownloadSource(),
                versionJsonPath = jsonPath,
                versionJson = jsonStamp,
                parentVersionJsonPath = parentVersionJsonPath,
                parentVersionJson = parentVersionJsonPath?.let { FileStamp.of(File(it)) },
                mods = ModStamp.list(modsDir),
                touchControllerMod = touchControllerMod,
                lwjgl3ifyVersion = lwjgl3ifyVersion,
                verifiedFileCount = verifiedFiles.size
            )

            runCatching {
                // 先写逐文件列表，最后写状态文件：
                // 只有两者都在时下一次启动才可能命中，任何一步失败都只是下次多做一次准备。
                PreparedStateStore.writeAtomically(filesFile(version), TrustedFiles.render(verifiedFiles))
                PreparedStateStore.writeAtomically(stateFile(version), GSON.toJson(state))
            }.onSuccess {
                Logger.info(
                    TAG,
                    "PREPARE CACHE REBUILT: ${verifiedFiles.size} verified file(s), " +
                            "mods=${state.mods.size}, lwjgl3ify=${lwjgl3ifyVersion ?: "none"}"
                )
            }.onFailure { e ->
                // 写不进去不是致命错误：实例本身是完好的，只是下次要多准备一次
                Logger.error(TAG, "Failed to record the prepared state", e)
            }
        }
    }

    /** 作废某个实例的准备状态；实例本身不受影响，下一次启动重新准备 */
    fun invalidate(version: Version) {
        runCatching { stateFile(version).delete() }
        runCatching { filesFile(version).delete() }
        Logger.info(TAG, "PREPARE CACHE INVALIDATED")
    }

    /**
     * 每个实例一把锁，只保护"写入新状态"这一段。
     *
     * 拿不到锁就直接跳过写入：宁可下次多做一次准备，也不要两个任务互相覆盖。
     * 锁是保险，不是正确性的前提——即使完全拿不到，原子改名也保证不会写出半个文件。
     */
    private inline fun withInstanceLock(version: Version, block: () -> Unit) {
        val lockFile = lockFile(version)
        var raf: RandomAccessFile? = null
        var lock: FileLock? = null
        var locked = false
        try {
            lockFile.parentFile?.mkdirs()
            raf = RandomAccessFile(lockFile, "rw")
            lock = raf.channel.tryLock()
            if (lock == null) {
                Logger.info(TAG, "PREPARE CACHE WRITE SKIPPED: another preparation holds the instance lock")
                return
            }
            locked = true
        } catch (e: OverlappingFileLockException) {
            Logger.info(TAG, "PREPARE CACHE WRITE SKIPPED: the instance lock is held in this process")
            runCatching { raf?.close() }
            return
        } catch (e: IOException) {
            Logger.warning(TAG, "Cannot acquire the instance preparation lock, writing without it", e)
            // 拿不到锁也要继续写
        }
        try {
            block()
        } finally {
            if (locked) runCatching { lock?.release() }
            runCatching { raf?.close() }
        }
    }
}