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

package dev.oxide.launcher.game.download.assets

import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.utils.file.ensureParentDirectory
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.network.downloadFileFromSources
import dev.oxide.launcher.utils.network.toLocal
import dev.oxide.launcher.utils.network.withSpeedReport
import dev.oxide.launcher.viewmodel.ErrorViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import okio.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DownloadSingle"

/**
 * 为一些版本下载单独的资源文件
 * @param version 要下载单独资源版本信息
 * @param versions 为哪些游戏版本下载
 * @param folder 版本游戏目录下的相对路径
 * @param onFileCopied 文件已成功复制到版本游戏目录后 单独回调
 * @param onFileCancelled 文件安装已取消 单独回调
 * @param onTaskCreated 任务已建好并交给任务系统后回调，参数就是那个任务本身
 * @param onEnded 这一个任务收尾时回调（成功、失败、取消都会走到），参数是任务 id 与**是否失败**
 * @return 交给任务系统的那个任务；没有真的开始时为 null
 */
fun downloadSingleForVersions(
    version: PlatformVersion,
    versions: List<Version>,
    folder: String,
    onFileCopied: suspend (zip: File, folder: File) -> Unit = { _, _ -> },
    onFileCancelled: (zip: File, folder: File) -> Unit = { _, _ -> },
    onTaskCreated: (Task) -> Unit = {},
    onEnded: (taskId: String, failed: Boolean) -> Unit = { _, _ -> },
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
): Task? {
    val fileKey = version.platformSha1() ?: version.platformFileName()
    val cacheFile = File(File(PathManager.DIR_CACHE, "assets"), fileKey)
    val taskId = downloadTaskId(fileKey, versions)

    // 任务系统只在真正跑完（含报错）之后才走到 onTaskEnded 的那条路，
    // 而 onError 先于它发生，所以这里记一下"这次是不是失败了"
    val failed = AtomicBoolean(false)

    return downloadSingleFile(
        version = version,
        taskId = taskId,
        file = cacheFile,
        onDownloaded = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_assets_install_progress_installing, version.platformFileName()))
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, version.platformFileName())
                if (targetFile.exists() && !targetFile.delete()) throw IOException("Failed to properly delete the existing target file.")
                cacheFile.copyTo(targetFile)
                onFileCopied(targetFile, targetFolder) //文件已复制回调
            }
        },
        onError = { e ->
            failed.set(true)
            Logger.warning(TAG, "An error occurred while downloading the resource files.", e)

            submitError(
                ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.download_assets_install_failed),
                    message = mapExceptionToMessage(e)
                )
            )
        },
        onCancel = {
            FileUtils.deleteQuietly(cacheFile)
            versions.forEach { ver ->
                val targetFolder = File(ver.getGameDir(), folder)
                val targetFile = File(targetFolder, version.platformFileName())
                if (targetFile.exists()) FileUtils.deleteQuietly(targetFile)
                onFileCancelled(targetFile, targetFolder) //文件已取消回调
            }
        },
        onFinally = {
            Logger.info(TAG, "Attempting to clear cached resource files.")
            FileUtils.deleteQuietly(cacheFile)
        },
        onCreated = onTaskCreated,
        // 监听器在提交之前挂上，因此不存在"任务已经跑完、监听器还没挂上"的窗口
        onEnded = { onEnded(taskId, failed.get()) }
    )
}

/**
 * 下载任务的Id
 * 同一文件安装到不同的游戏版本时属于不同的任务，避免被误判为重复任务而丢弃目标版本
 */
private fun downloadTaskId(fileKey: String, versions: List<Version>): String {
    if (versions.isEmpty()) return fileKey
    return "$fileKey|${versions.map { it.getVersionName() }.sorted().joinToString(",")}"
}

private fun downloadSingleFile(
    version: PlatformVersion,
    taskId: String,
    file: File,
    onDownloaded: suspend (Task) -> Unit,
    onError: (Throwable) -> Unit = {},
    onCancel: () -> Unit = {},
    onFinally: () -> Unit = {},
    onCreated: (Task) -> Unit = {},
    onEnded: () -> Unit = {}
): Task? {
    val task = Task.runTask(
        id = taskId,
        task = { running ->
            val totalFileSize = version.platformFileSize()
            var downloadedSize = 0L

            //更新下载任务进度
            fun updateProgress() {
                running.updateProgress(
                    (downloadedSize.toDouble() / totalFileSize.toDouble()).toFloat()
                )
                running.updateMessage(
                    androidText(
                        R.string.download_assets_install_progress_downloading,
                        version.platformFileName(),
                        formatFileSize(downloadedSize),
                        formatFileSize(totalFileSize),
                    )
                )
            }
            updateProgress()

            withSpeedReport(
                onSpeedReport = { bytes ->
                    running.updateSpeed(bytes)
                },
                onClear = {
                    running.clearSpeed()
                }
            ) { report ->
                downloadFileFromSources(
                    urls = version
                        .platformDownloadUrl()
                        .mapMCIMMirrorUrls(),
                    sha1 = version.platformSha1(),
                    outputFile = file.ensureParentDirectory(),
                    sizeCallback = { size ->
                        downloadedSize += size
                        updateProgress()
                        report(size)
                    }
                )
            }

            onDownloaded(running)
        },
        onError = onError,
        onCancel = onCancel,
        onFinally = onFinally
    )
    // 注册监听器在提交之前，因此不存在"任务已经跑完、监听器还没挂上"的窗口
    TaskSystem.submitTask(task) { onEnded() }
    onCreated(task)
    return task
}

fun mapExceptionToMessage(e: Throwable): AndroidStringText {
    return when (e) {
        is HttpRequestTimeoutException -> androidText(R.string.error_timeout)
        is UnknownHostException, is UnresolvedAddressException -> androidText(R.string.error_network_unreachable)
        is ConnectException -> androidText(R.string.error_connection_failed)
        is ResponseException -> e.toLocal()
        else -> {
            androidText(e.localizedMessage ?: e::class.simpleName ?: "Unknown error")
        }
    }
}