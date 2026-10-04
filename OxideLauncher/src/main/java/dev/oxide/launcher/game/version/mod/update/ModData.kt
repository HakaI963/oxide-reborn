/*
 * Zalith Launcher 2
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

package dev.oxide.launcher.game.version.mod.update

import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.getVersions
import dev.oxide.launcher.game.download.assets.utils.ModTranslations
import dev.oxide.launcher.game.version.mod.ModFile
import dev.oxide.launcher.game.version.mod.ModLoaderVerdict
import dev.oxide.launcher.game.version.mod.ModProject
import dev.oxide.launcher.game.version.mod.modLoaderVerdict
import dev.oxide.launcher.game.version.mod.resolveTargetLoaderNames
import dev.oxide.launcher.ui.screens.content.download.assets.elements.initAll
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.parseInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ModData"

/**
 * 需要更新的模组的数据类，记录模组文件和模组所属的项目
 * @param modFile 模组在模组平台上对应的文件
 * @param project 模组在模组平台上所属的项目
 * @param mcMod 模组翻译信息
 */
data class ModData(
    val file: File,
    val modFile: ModFile,
    val project: ModProject,
    val mcMod: ModTranslations.McMod?
) {
    /**
     * 当前模组的版本号，用于新旧对比
     */
    var currentVersion: String? = null
        private set

    /**
     * 检查模组更新
     * @param minecraftVer MC版本，用于筛选版本
     * @param modLoader 模组加载器信息，用于筛选版本
     */
    suspend fun checkUpdate(
        minecraftVer: String,
        modLoader: ModLoader
    ): PlatformVersion? = checkUpdate(minecraftVer, listOf(modLoader))

    /**
     * 检查模组更新
     *
     * 这里有一道下载前的守卫：[ModLoaderVerdict.Mismatch] 直接返回 null，
     * 一个字都不下载。守卫之前不存在过：判定曾经是
     * `当前加载器 ∉ 文件加载器 → 沿用文件自己的加载器通道`，
     * 于是 Fabric 实例里的 NeoForge 模组被判成"可更新"，
     * 平台上的 NeoForge 构建被下载下来覆盖掉原文件——
     * 这就是"NeoForge 模组分进了 Fabric 实例"在设备上的来源。
     *
     * 判定本身在 [modLoaderVerdict] 里，是纯函数；这里只负责不再放宽它。
     *
     * @param minecraftVer MC版本，用于筛选版本
     * @param modLoaders 目标实例**自己的**加载器
     */
    suspend fun checkUpdate(
        minecraftVer: String,
        modLoaders: Collection<ModLoader>
    ): PlatformVersion? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val verdict = modLoaderVerdict(modLoaders, modFile.loaders.toList())
                if (!verdict.loadable) {
                    Logger.info(
                        TAG,
                        "Skipping update for ${file.name}: the instance has " +
                                "[${modLoaders.joinToString { it.displayName }}] but this file only " +
                                "declares [${modFile.loaders.joinToString { it.getDisplayName() }}]."
                    )
                    return@runCatching null
                }

                val datePublished = parseInstant(modFile.datePublished)
                val projectId = project.id
                // 守卫之后只按**实例自己的**加载器筛通道。多加载器模组在 Fabric 实例里
                // 因此只会取到 Fabric 构建，不会顺手把 NeoForge 构建也当成候选。
                val targetLoaders = resolveTargetLoaderNames(modLoaders, modFile.loaders.toList())
                    .map { it.lowercase() }
                    .toSet()

                // 获取所有版本并初始化
                val versions = getVersions(
                    projectId,
                    project.platform
                ).initAll(projectId)
                    .filter { version ->
                        if (version.platformId() == modFile.id) {
                            // 当前版本，设置版本号
                            currentVersion = version.platformVersion()
                        }
                        val loaderNames = version.platformLoaders()
                            .map { it.getDisplayName().lowercase() }
                            .toSet()
                        // 是否支持当前MC版本
                        minecraftVer in version.platformGameVersion() &&
                        // 是否匹配实例自己的加载器通道
                        (targetLoaders.isEmpty() || loaderNames.any { it in targetLoaders }) &&
                        // 是否比当前版本更新
                        version.platformDatePublished() > datePublished
                    }

                // 获取最新的版本
                versions.firstOrNull()?.also { version ->
                    Logger.info(TAG, "Detected update for mod ${file.name}: $currentVersion -> ${version.platformVersion()}")
                }
            }.onFailure { th ->
                Logger.warning(TAG, "An error occurred while fetching all versions of the mod.", th)
            }.getOrNull()
        }
    }
}
