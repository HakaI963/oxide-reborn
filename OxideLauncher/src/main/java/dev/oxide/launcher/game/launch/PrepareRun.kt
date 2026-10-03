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
import dev.oxide.launcher.game.prepare.TrustedFiles
import dev.oxide.launcher.game.support.lwjgl3ify.findEnabledLwjgl3ifyVersion
import dev.oxide.launcher.game.support.lwjgl3ify.patchLwjgl3ifyIfNeeded
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.mod.AllModReader
import dev.oxide.launcher.game.version.mod.isEnabled
import dev.oxide.launcher.game.versioninfo.models.GameManifest
import dev.oxide.launcher.utils.logging.Logger
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

    private var touchControllerMod = false
    private var lwjgl3ifyVersion: String? = null
    private var parentVersionJsonPath: String? = null
    private var evaluated = false

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
                if (touchControllerMod) version.enableTouchProxy = true

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

                touchControllerMod = mods.any { it.id == "touchcontroller" && it.file.isEnabled() }
                if (touchControllerMod) version.enableTouchProxy = true

                lwjgl3ifyVersion = findEnabledLwjgl3ifyVersion(mods)
                patchLwjgl3ifyIfNeeded(version, mods)
            }
        }
    }

    /** 清单构建完成后，父版本清单的路径才能确定 */
    fun onManifestBuilt(manifest: GameManifest) {
        val parent = manifest.inheritsFrom
        parentVersionJsonPath = if (parent.isNullOrBlank()) {
            null
        } else {
            File(version.getGameHome(), "versions").resolve("$parent/$parent.json").absolutePath
        }
    }

    /** 准备完全成功后记录状态；只有走到这里才会写，失败与取消都不会留下有效状态 */
    fun recordPrepared(verifiedFiles: List<File>) {
        if (!evaluated) {
            Logger.warning(TAG, "Preparation finished without being evaluated, not recording the prepared state")
            return
        }
        PrepareCache.record(
            version = version,
            parentVersionJsonPath = parentVersionJsonPath,
            modsDir = modsDir,
            touchControllerMod = touchControllerMod,
            lwjgl3ifyVersion = lwjgl3ifyVersion,
            verifiedFiles = verifiedFiles
        )
    }
}