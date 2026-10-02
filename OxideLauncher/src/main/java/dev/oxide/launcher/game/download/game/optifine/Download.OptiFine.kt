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

package dev.oxide.launcher.game.download.game.optifine

import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.game.addons.mirror.MirrorSource
import dev.oxide.launcher.game.addons.mirror.SourceType
import dev.oxide.launcher.game.addons.mirror.orderedByGameSourcePreference
import dev.oxide.launcher.game.addons.mirror.runMirrorable
import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersion
import dev.oxide.launcher.game.addons.modloader.optifine.OptiFineVersions
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.utils.isChinaMainland
import dev.oxide.launcher.utils.network.downloadFile
import dev.oxide.launcher.utils.network.withSpeedReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val OPTIFINE_DOWNLOAD_ID = "Download.OptiFine"

/**
 * OptiFine installer 的临时下载路径
 */
fun targetTempOptiFineInstaller(tempGameDir: File): File {
    return File(tempGameDir, ".temp/OptiFine.jar")
}

fun getOptiFineDownloadTask(
    targetTempInstaller: File,
    optifine: OptiFineVersion
): Task {
    return Task.runTask(
        id = OPTIFINE_DOWNLOAD_ID,
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(
                R.string.download_game_install_optifine_fetch_download_url, optifine.realVersion
            ))
            val optifineUrl = getOFUrlMirrorable(optifine)

            task.updateProgress(-1f)
            task.updateMessage(androidText(
                R.string.download_game_install_base_download_file, ModLoader.OPTIFINE.displayName, optifine.realVersion
            ))
            withSpeedReport(
                onSpeedReport = { bytes ->
                    task.updateSpeed(bytes)
                },
                onClear = {
                    task.clearSpeed()
                }
            ) {
                downloadFile(
                    url = optifineUrl,
                    outputFile = targetTempInstaller
                )
            }
        }
    )
}

fun getOptiFineModsDownloadTask(
    optifine: OptiFineVersion,
    tempModsDir: File
): Task {
    return Task.runTask(
        id = OPTIFINE_DOWNLOAD_ID,
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(
                R.string.download_game_install_optifine_fetch_download_url, optifine.realVersion
            ))
            val optifineUrl = getOFUrlMirrorable(optifine)

            //开始下载为 Mod
            task.updateProgress(-1f)
            task.updateMessage(androidText(
                R.string.download_game_install_base_download_file, ModLoader.OPTIFINE.displayName, optifine.realVersion
            ))
            withSpeedReport(
                onSpeedReport = { bytes ->
                    task.updateSpeed(bytes)
                },
                onClear = {
                    task.clearSpeed()
                }
            ) { report ->
                downloadFile(
                    url = optifineUrl,
                    outputFile = File(tempModsDir, optifine.fileName),
                    sizeCallback = report
                )
            }
        }
    )
}

private suspend fun getOFUrlMirrorable(
    optifine: OptiFineVersion
): String {
    return if (isChinaMainland()) {
        runMirrorable(
            listOf(
                fetchOfficialOptiFineUrlSource(optifine),
                fetchBMCLOptiFineUrlSource(optifine)
            ).orderedByGameSourcePreference()
        )!!
    } else {
        fetchOptiFineDownloadUrl(optifine)
    }
}

/**
 * 从官方源获取 OptiFine 主文件下载链接
 */
private fun fetchOfficialOptiFineUrlSource(optifine: OptiFineVersion): MirrorSource<String> =
    MirrorSource(SourceType.OFFICIAL) { fetchOptiFineDownloadUrl(optifine) }

private fun fetchBMCLOptiFineUrlSource(optifine: OptiFineVersion): MirrorSource<String> =
    MirrorSource(SourceType.BMCLAPI) { getDownloadUrlWithBMCLAPI(optifine) }

private suspend fun fetchOptiFineDownloadUrl(
    optifine: OptiFineVersion
) = withContext(Dispatchers.IO) {
    OptiFineVersions.fetchOptiFineDownloadUrl(optifine.fileName) ?: throw CantFetchingOptiFineUrlException()
}

private fun getDownloadUrlWithBMCLAPI(optifine: OptiFineVersion): String {
    val inherit = if (optifine.inherit == "1.8" || optifine.inherit == "1.9") "${optifine.inherit}.0" else optifine.inherit
    val displayNameStripped = optifine.displayName.removePrefix("${optifine.inherit} ")

    val suffix = if (optifine.isPreview) {
        "HD_U_${displayNameStripped.replace(" ", "/")}"
    } else {
        "HD_U/$displayNameStripped"
    }

    return "https://bmclapi2.bangbang93.com/optifine/$inherit/$suffix"
}