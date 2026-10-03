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

import dev.oxide.launcher.game.download.engine.BatchDownloader
import dev.oxide.launcher.game.download.engine.DownloadRequest
import dev.oxide.launcher.utils.file.check7z
import dev.oxide.launcher.utils.file.checkZip
import dev.oxide.launcher.utils.file.compareSHA1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * 一个待下载文件的完整描述
 * 候选源、目标位置与校验方式
 * 批量执行交给 [BatchDownloader]，单个文件也可以通过 [dev.oxide.launcher.game.download.engine.DownloadEngine] 直接驱动
 */
class DownloadTask(
    val urls: List<String>,
    private val verifyIntegrity: Boolean,
    val targetFile: File,
    val sha1: String? = null,
    /** 已知大小，用于进度统计与预分配；未知传 -1 */
    val size: Long = -1L,
    /**
     * 是否本身是可以被下载的，如果不可下载，则只允许以目标文件已存在的形式满足，
     * 若强行下载会以 404 失败
     */
    val isDownloadable: Boolean = true
) {
    init {
        require(urls.isNotEmpty()) { "DownloadTask requires at least one url" }
    }

    var fileDownloadedTask: (suspend () -> Unit)? = null

    /**
     * 本次运行中这个文件被确认可用的依据：[大小, 修改时间]
     *
     * 只有在拿到**正面证据**时才会被写入——要么完整校验通过，要么与上一次成功准备时
     * 记录的身份完全一致。没有它就说明这个文件没有被确认过。
     */
    internal var verifiedStamp: LongArray? = null

    internal fun toRequest(): DownloadRequest = DownloadRequest(
        urls = urls,
        targetFile = targetFile,
        sha1 = sha1,
        expectedSize = size,
        tag = this
    )

    internal suspend fun runFileDownloadedTask() {
        withContext(Dispatchers.IO) {
            fileDownloadedTask?.invoke()
        }
    }

    /** 目标已存在且校验可用时返回 true */
    fun existingFileValid(trusted: dev.oxide.launcher.game.prepare.TrustedFiles? = null): Boolean {
        val file = targetFile
        if (!file.exists()) return false
        if (!verifyIntegrity) return true

        // 与上一次成功准备时记录的身份完全一致，可以跳过完整校验。
        // 大小或修改时间只要有一点不同，就退回真实的校验流程。
        if (trusted != null && trusted.isTrusted(file)) {
            verifiedStamp = longArrayOf(file.length(), file.lastModified())
            return true
        }

        if (sha1.isNullOrBlank()) {
            //排除目标无法被下载的情况，比如Forge的client
            if (!isDownloadable) {
                markVerified(file)
                return true
            }
            val valid = archiveOrPlainValid(file)
            if (valid) markVerified(file)
            return valid
        }

        if (compareSHA1(file, sha1)) {
            markVerified(file)
            return true
        }
        FileUtils.deleteQuietly(file)
        return false
    }

    private fun markVerified(file: File) {
        verifiedStamp = longArrayOf(file.length(), file.lastModified())
    }

    private fun archiveOrPlainValid(file: File): Boolean = when (file.extension.lowercase()) {
        "zip", "jar" -> checkZip(file)
        "7z" -> check7z(file)
        else -> true
    }
}
