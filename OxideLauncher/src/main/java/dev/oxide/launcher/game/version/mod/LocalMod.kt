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

package dev.oxide.launcher.game.version.mod

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.utils.logging.Logger
import kotlinx.io.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "LocalMod"

/** 本地模组信息 */
class LocalMod(
    /** 本地模组对应的文件 */
    modFile: File,

    /** 本地模组对应的文件的大小 */
    val fileSize: Long,

    /** 模组ID */
    val id: String,

    /** 模组所属的加载器 */
    val loader: ModLoader,

    /** 模组的显示名称 */
    val name: String,

    /** 模组描述 */
    val description: String? = null,

    /** 模组版本 */
    val version: String? = null,

    /** 模组的作者列表 */
    val authors: List<String>,

    /** 模组的图标 */
    val icon: ByteArray? = null,

    /**
     * 标记是否为非模组
     */
    val notMod: Boolean = false,

    /**
     * 是否从远端获取模组信息
     */
    val checkRemote: Boolean = true,
) {
    var file by mutableStateOf(modFile)
        private set

    /**
     * 禁用模组
     * @return 文件状态确实发生了变化时为 true
     */
    fun disable(): Boolean {
        if (file.isDisabled()) return false

        val newFile = File("${file.absolutePath}$DISABLED_SUFFIX")
        if (!file.renameToSafely(newFile)) return false

        file = newFile
        return true
    }

    /**
     * 启用模组
     *
     * 与 [disable] 完全对称：已经在启用态时立刻返回 false，不去搬动文件。
     * 此前这里缺这道对称判断——`enabledMod(file)` 在文件已启用时返回**同一个** File，
     * 于是 `Files.move(path, path)` 被执行了一次；不同文件系统对"源与目标相同"的
     * 处理并不一致（有的静默成功，有的抛 `FileSystemException`），抛出的那个被
     * `renameToSafely` 吞掉只留一条警告，于是什么都没发生、界面上也不动。
     *
     * @return 文件状态确实发生了变化时为 true
     */
    fun enable(): Boolean {
        if (file.isEnabled()) return false

        val newFile = enabledMod(file)
        if (!file.renameToSafely(newFile)) return false

        file = newFile
        return true
    }

    /**
     * 按目标状态启用或禁用
     *
     * 调用方给出的是**意图**（界面上那个开关此刻应该是什么状态），
     * 而不是"当前状态的反面"。因此重复点同一个方向是彻底的空操作，
     * 而不会因为文件已经在那一边就反向再搬一次。
     *
     * @return 文件状态确实发生了变化时为 true
     */
    fun setEnabled(enabled: Boolean): Boolean =
        if (enabled) enable() else disable()

    /**
     * 删除模组文件
     *
     * 删除的是 [file] **此刻**指向的那个文件，也就是磁盘上真实存在的那个路径。
     * 这一点是删除能工作的关键：禁用态的文件在磁盘上叫 `x.jar.disabled`，
     * 任何按"启用时的名字"去拼路径的删除都会静默地什么也删不掉
     * （`FileUtils.deleteQuietly` 对不存在的路径返回 false 且不抛异常，
     * 调用方于是把一次没发生的删除当成成功汇报出去）。
     *
     * @return 文件确实被删掉时为 true
     */
    fun delete(): Boolean {
        val target = file
        if (!target.exists()) return false
        return try {
            FileUtils.deleteQuietly(target)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to delete file {$target}!", e)
            false
        }
    }

    private fun File.renameToSafely(dest: File): Boolean {
        if (absolutePath == dest.absolutePath) return true
        return try {
            dest.parentFile?.mkdirs()
            Files.move(
                this.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e: IOException) {
            Logger.warning(TAG, "Failed to rename file {$this} to $dest!", e)
            false
        }
    }
}

/** 禁用态文件的后缀；[File.isEnabled] 认的就是它 */
const val DISABLED_SUFFIX = ".disabled"

/**
 * 模组是否启用
 *
 * 直接读**路径**而不是任何缓存的布尔值：启用/禁用是一次改名，
 * 任何缓存下来的"之前是什么状态"在改名之后都是假的。
 */
fun File.isEnabled(): Boolean = !absolutePath.endsWith(DISABLED_SUFFIX, ignoreCase = true)

/**
 * 模组是否禁用
 */
fun File.isDisabled(): Boolean = !this.isEnabled()

/**
 * 去掉 `.disabled` 后缀后的路径；文件已启用时返回它自己
 */
fun enabledMod(file: File): File {
    if (file.isEnabled()) return file

    val currentPath = file.absolutePath
    val newPath = currentPath.dropLast(DISABLED_SUFFIX.length)
    return File(newPath)
}

/**
 * 这个文件在列表里的稳定身份：去掉 `.disabled` 后的文件名
 *
 * 启用/禁用只改后缀，所以同一个模组在两种状态下必须得到**同一个**键；
 * 否则一行刚被改名就换了 key，选中态与展开态会在改名的瞬间对不上另一行。
 */
fun File.modBaseName(): String {
    val name = name
    return if (name.endsWith(DISABLED_SUFFIX, ignoreCase = true)) {
        name.dropLast(DISABLED_SUFFIX.length)
    } else {
        name
    }
}

/**
 * 创建一个非模组文件
 */
fun createNotMod(file: File): LocalMod = LocalMod(
    modFile = file,
    fileSize = FileUtils.sizeOf(file),
    id = "",
    loader = ModLoader.UNKNOWN,
    name = file.name,
    description = null,
    version = null,
    authors = emptyList(),
    icon = null,
    notMod = true
)
