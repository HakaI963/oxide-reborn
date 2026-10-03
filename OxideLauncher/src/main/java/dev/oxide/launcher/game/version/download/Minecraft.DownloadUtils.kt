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

package dev.oxide.launcher.game.version.download

import dev.oxide.launcher.game.addons.mirror.mapBMCLMirrorUrls
import dev.oxide.launcher.game.versioninfo.models.GameManifest
import dev.oxide.launcher.game.versioninfo.models.OperatingSystem
import dev.oxide.launcher.utils.GSON
import dev.oxide.launcher.utils.file.compareSHA1
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.network.fetchStringFromUrls
import dev.oxide.launcher.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "Minecraft.DownloadUtils"

private const val UTILS_LOG_TAG = "Minecraft.DownloaderUtils"

fun <T> String.parseTo(classOfT: Class<T>): T {
    return runCatching {
        GSON.fromJson(this, classOfT)
    }.getOrElse { e ->
        Logger.error(TAG, "Failed to parse JSON", e)
        throw e
    }
}

suspend fun <T> downloadAndParseJson(
    targetFile: File,
    url: String,
    expectedSHA: String?,
    classOfT: Class<T>
): T {
    suspend fun downloadAndParse(): T {
        val json = withContext(Dispatchers.IO) {
            val string = withRetry(UTILS_LOG_TAG, maxRetries = 2) {
                fetchStringFromUrls(
                    url.mapBMCLMirrorUrls()
                )
            }
            if (string.isBlank()) {
                Logger.error(TAG, "Downloaded string is empty, aborting.")
                throw IllegalStateException("Downloaded string is empty.")
            }
            targetFile.writeText(string)
            string
        }
        return json.parseTo(classOfT)
    }

    if (targetFile.exists()) {
        if (expectedSHA == null) {
            /*
             * 没有声明哈希时无法验证内容，但也不能因此每次启动都删掉重下：
             * 加载器（Fabric/Forge/NeoForge）的清单通常不带 assetIndex，
             * 上游会合成一个 sha1 为 null 的索引，于是这里原本每次启动都会
             * 完整读一遍、算一次 SHA-1、删掉、再联网重下。
             * 既然无法校验，就直接复用磁盘上那份：解析失败仍然会走重下，
             * 真正被破坏的文件不可能通过下面的解析。
             */
            return runCatching {
                targetFile.readText().parseTo(classOfT)
            }.getOrElse {
                Logger.warning(TAG, "Failed to parse existing JSON, re-downloading...")
                downloadAndParse()
            }
        }
        if (compareSHA1(targetFile, expectedSHA)) {
            return runCatching {
                targetFile.readText().parseTo(classOfT)
            }.getOrElse {
                Logger.warning(TAG, "Failed to parse existing JSON, re-downloading...")
                downloadAndParse()
            }
        } else {
            FileUtils.deleteQuietly(targetFile)
        }
    }

    return downloadAndParse()
}

fun artifactToPath(library: GameManifest.Library): String? {
    library.downloads?.artifact?.path?.let { return it }

    val libInfos = library.name.split(":")
    if (libInfos.size < 3) {
        Logger.error(TAG, "Invalid library name format: ${library.name}")
        return null
    }

    val groupId = libInfos[0].replace('.', '/')
    val artifactId = libInfos[1]
    val version = libInfos[2]

    var classifier = if (libInfos.size > 3) "-${libInfos[3]}" else ""
    if (library.isNative) run {
        val natives = library.natives ?: return@run
        if (natives.isNotEmpty()) {
            //Android，在这里使用Linux
            val native = natives[OperatingSystem.Linux] ?: return@run
            classifier = "-$native"
        }
    }

    return "$groupId/$artifactId/$version/$artifactId-$version$classifier.jar"
}

fun processLibraries(libraries: () -> List<GameManifest.Library>) {
    libraries().forEach { library ->
        if (library.filterLibrary()) return@forEach

        val versionSegment = library.name.split(":").getOrNull(2) ?: return
        val versionParts = versionSegment.split(".")

        getLibraryReplacement(library.name, versionParts)?.let { replacement ->
            Logger.debug(TAG, "Library ${library.name} has been changed to version ${replacement.newName.split(":").last()}")
            updateLibrary(library, replacement)
        }
    }
}

private fun updateLibrary(
    library: GameManifest.Library,
    replacement: LibraryReplacement
) {
    createLibraryInfo(library)
    library.name = replacement.newName
    library.downloads.artifact.apply {
        path = replacement.newPath
        sha1 = replacement.newSha1
        url = replacement.newUrl
        //新版本的大小与旧版本必然不同，残留旧值会令下载器提前截断文件
        if (replacement.newSize > 0) size = replacement.newSize
    }
}

private fun createLibraryInfo(library: GameManifest.Library) {
    if (library.downloads?.artifact == null) {
        library.downloads = GameManifest.DownloadsX().apply {
            this.artifact = GameManifest.Artifact()
        }
    }
}
