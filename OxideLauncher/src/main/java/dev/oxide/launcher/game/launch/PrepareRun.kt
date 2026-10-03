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

package dev.oxide.launcher.game.launch

import dev.oxide.launcher.game.prepare.PrepareCache
import dev.oxide.launcher.game.prepare.PrepareDecision
import dev.oxide.launcher.game.prepare.PreparedStateStore
import dev.oxide.launcher.game.prepare.TrustedFiles
import dev.oxide.launcher.game.support.lwjgl3ify.findEnabledLwjgl3ifyVersion
import dev.oxide.launcher.game.support.lwjgl3ify.patchLwjgl3ifyIfNeeded
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.mod.AllModReader
import dev.oxide.launcher.game.version.mod.isEnabled
import dev.oxide.launcher.game.versioninfo.models.GameManifest
import dev.oxide.launcher.utils.logging.Logger
import com.google.gson.JsonParser
import java.io.File

private const val TAG = "PrepareRun"

/**
 * 一次启动的准备上下文
 *
 * 它把"要不要跳过昂贵的准备工作"这个决定，从模组扫描阶段带到文件校验阶段，
 * 并在两个阶段都成功之后把新的准备状态写回去。
 *
 * 三种结果：
 * - 命中：模组扫描被跳过（一次典型的实例有几十个 jar，每个都要反复打开 zip），
 *   文件校验也只对每个文件做两次 stat 而不是完整哈希。
 * - 未命中：与改动前的流程完全一致，额外只是把新状态记下来。
 * - 有 lwjgl3ify：始终完整准备，因为打补丁需要真实的模组对象。
 */
class PrepareRun(
    private val version: Version,
    val modsDir: File = VersionFolders.MOD.getDir(version.getGameDir())
) {
    /** 命中时可复用的文件身份；未命中时为 null */
    var trustedFiles: TrustedFiles? = null
        private set

    private var touchControllerMod: Boolean? = null
    private var lwjgl3ifyVersion: String? = null
    private var parentVersionJsonPath: String? = null
    private var evaluated = false

    /**
     * 上一次准备时算出的合并清单
     *
     * 它是版本 JSON、父版本 JSON 与模组状态的纯函数。命中准备状态缓存时这些输入都没变，
     * 于是可以直接复用，省掉一次清单构建与一次序列化。
     */
    var cachedLaunchManifest: String? = null
        private set

    /**
     * 扫描模组并在需要时打补丁
     *
     * @return 经过补丁的游戏清单；为 null 表示没有补丁，清单需要正常构建
     */
    suspend fun scanModsAndPatch(): GameManifest? {
        val decision = PrepareCache.evaluate(version, modsDir)
        evaluated = true

        return when (decision) {
            is PrepareDecision.Hit -> {
                touchControllerMod = decision.state.touchControllerMod
                lwjgl3ifyVersion = decision.state.lwjgl3ifyVersion
                parentVersionJsonPath = decision.state.parentVersionJsonPath
                trustedFiles = decision.trusted
                cachedLaunchManifest = readCachedManifest()
                if (touchControllerMod == true) version.enableTouchProxy = true

                Logger.info(
                    TAG,
                    "PREPARE CACHE HIT: ${decision.trusted.size} file(s) and ${decision.mods.size} mod(s) " +
                            "unchanged, skipping mod scan and full verification"
                )
                // 命中意味着记录里就没有 lwjgl3ify，因此不需要、也不可能有补丁
                null
            }

            is PrepareDecision.Miss -> {
                Logger.info(TAG, "PREPARE CACHE MISS: ${decision.reason.logReason}")
                val mods = AllModReader(modsDir).readAllLocals()

                val touchController = mods.any { it.id == "touchcontroller" && it.file.isEnabled() }
                touchControllerMod = touchController
                if (touchController) version.enableTouchProxy = true

                lwjgl3ifyVersion = findEnabledLwjgl3ifyVersion(mods)
                patchLwjgl3ifyIfNeeded(version, mods)
            }
        }
    }

    /**
     * 清单构建完成后确定父版本清单的路径
     *
     * 必须从**原始**版本 JSON 里读 inheritsFrom：合并后的清单继承的是原版清单对象，
     * 它的 inheritsFrom 恒为 null，那样父版本清单就完全落在指纹之外，
     * 改动原版清单将无法让缓存失效。
     */
    fun onManifestBuilt() {
        val parent = readInheritsFrom()
        parentVersionJsonPath = if (parent.isNullOrBlank()) {
            null
        } else {
            File(version.getGameHome(), "versions").resolve("$parent/$parent.json").absolutePath
        }
    }

    private fun readInheritsFrom(): String? = runCatching {
        val raw = File(version.getVersionPath(), "${version.getVersionName()}.json")
        if (!raw.isFile) return null
        JsonParser.parseString(raw.readText())
            .asJsonObject
            .get("inheritsFrom")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
    }.getOrElse { e ->
        Logger.warning(TAG, "Cannot read inheritsFrom from the version JSON", e)
        null
    }

    /** 准备完全成功后记录状态；只有走到这里才会写，失败或取消都不会留下有效状态 */
    fun recordPrepared(verifiedFiles: List<File>, launchManifest: String) {
        if (!evaluated) {
            Logger.warning(TAG, "Preparation finished without being evaluated, not recording the prepared state")
            return
        }
        val touchController = touchControllerMod ?: false
        PrepareCache.record(
            version = version,
            parentVersionJsonPath = parentVersionJsonPath,
            modsDir = modsDir,
            touchControllerMod = touchController,
            lwjgl3ifyVersion = lwjgl3ifyVersion,
            verifiedFiles = verifiedFiles,
            launchManifest = launchManifest
        )
    }

    /** 记录本次算出的合并清单，供下一次命中时直接复用 */
    fun storeManifest(text: String) {
        writeCachedManifest(text)
    }

    private fun cachedManifestFile() =
        File(PrepareCache.stateDir(version), PreparedStateStore.MANIFEST_FILE)

    private fun readCachedManifest(): String? = runCatching {
        cachedManifestFile().takeIf { it.isFile && it.length() in 1..MAX_CACHED_MANIFEST_BYTES }
            ?.readText()
    }.getOrNull()

    private fun writeCachedManifest(text: String) {
        if (text.length > MAX_CACHED_MANIFEST_BYTES) {
            Logger.info(TAG, "The merged manifest is too large to cache, rebuilding it every launch")
            return
        }
        runCatching { PreparedStateStore.writeAtomically(cachedManifestFile(), text) }
    }

    private companion object {
        /** 合并清单的合理上限，防止异常大的清单被写进缓存 */
        const val MAX_CACHED_MANIFEST_BYTES = 8L * 1024 * 1024
    }
}