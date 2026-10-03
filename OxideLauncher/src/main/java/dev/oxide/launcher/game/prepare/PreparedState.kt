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

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * 单个文件的身份标记
 *
 * 只记录大小与修改时间，不记录内容：准备阶段的完整校验（SHA-1 或压缩包 CRC）成本极高，
 * 而 (size, mtime) 足以判断"这个文件自上次成功准备以来有没有被动过"。
 *
 * 唯一的理论缺口是有人刻意改写文件并同时还原大小与修改时间；这需要主动构造，
 * 因此这里选择了速度。任何一次内容变化导致的大小或修改时间变化都会让缓存失效。
 */
@Keep
data class FileStamp(
    @SerializedName("size") val size: Long,
    @SerializedName("mtime") val mtime: Long
) {
    companion object {
        /** 读取磁盘上的真实标记，文件不存在时返回 null */
        fun of(file: File): FileStamp? =
            if (file.isFile) FileStamp(file.length(), file.lastModified()) else null
    }
}

/** 模组文件夹中的一个条目 */
@Keep
data class ModStamp(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mtime") val mtime: Long
) {
    companion object {
        /**
         * 列出模组文件夹内的全部条目
         *
         * 只取文件，不取子目录；启用/禁用是一次改名，所以 `.disabled` 也在内。
         * 条目很多时这个列表会有几百项，但每次只是 listFiles + 两次 stat。
         */
        fun list(modsDir: File): List<ModStamp> =
            modsDir.listFiles()
                ?.filter { it.isFile }
                ?.map { ModStamp(it.name, it.length(), it.lastModified()) }
                ?.sortedBy { it.name }
                ?: emptyList()
    }
}

/**
 * 一个实例的"已准备"状态
 *
 * 回答的问题是：**这个实例是否仍然处于当初那次成功完成准备时的状态？**
 * 因此这里只记录准备阶段的输入，不记录账号、渲染器、运行时、内存、语言这类
 * 不参与准备的设置——那些变了也不需要重做准备工作。
 */
@Keep
data class PreparedState(
    /** 缓存文件自身的格式版本；结构不兼容时直接重建 */
    @SerializedName("schemaVersion") val schemaVersion: Int,
    /** 准备算法版本；准备流程本身发生变化时必须递增，否则旧缓存会被误认为有效 */
    @SerializedName("preparationVersion") val preparationVersion: Int,
    /** 写入这份状态时的启动器版本号；升级后一律重建 */
    @SerializedName("launcherVersionCode") val launcherVersionCode: Long,
    @SerializedName("recordedAt") val recordedAt: Long,

    /** 写入时是否跳过了游戏文件完整性校验 */
    @SerializedName("integrityDisabled") val integrityDisabled: Boolean,
    /** 写入时使用的下载源，换源会改变文件内容 */
    @SerializedName("downloadSource") val downloadSource: String,

    /** 自身版本清单的路径与身份 */
    @SerializedName("versionJsonPath") val versionJsonPath: String,
    @SerializedName("versionJson") val versionJson: FileStamp,

    /**
     * 继承自原版时父版本清单的路径与身份
     *
     * 记录路径而不是每次重新解析版本 JSON 去取 inheritsFrom：
     * 只要自身清单没变，父版本名就不可能变，因此可以直接复查记录的路径。
     */
    @SerializedName("parentVersionJsonPath") val parentVersionJsonPath: String?,
    @SerializedName("parentVersionJson") val parentVersionJson: FileStamp?,

    /** 模组文件夹内全部条目（含被禁用的） */
    @SerializedName("mods") val mods: List<ModStamp>,
    /** 是否存在已启用的 touchcontroller 模组 */
    @SerializedName("touchControllerMod") val touchControllerMod: Boolean,
    /** 已启用的 lwjgl3ify 版本；为 null 表示没有该模组 */
    @SerializedName("lwjgl3ifyVersion") val lwjgl3ifyVersion: String?,

    /** 这次准备实际通过校验的文件数 */
    @SerializedName("verifiedFileCount") val verifiedFileCount: Int
)

/** 缓存不可用的原因，只用于日志 */
enum class PrepareInvalidateReason(val logReason: String) {
    NO_STATE("no prepared state"),
    UNREADABLE("prepared state unreadable"),
    SCHEMA_CHANGED("cache schema changed"),
    PREPARATION_CHANGED("preparation algorithm changed"),
    LAUNCHER_UPDATED("launcher was updated"),
    INTEGRITY_SETTING_CHANGED("integrity check setting changed"),
    DOWNLOAD_SOURCE_CHANGED("download source changed"),
    VERSION_JSON_CHANGED("Minecraft version json changed"),
    PARENT_VERSION_JSON_CHANGED("inherited version json changed"),
    MODS_CHANGED("mods changed"),
    LOADER_MOD_PRESENT("lwjgl3ify present, full preparation required")
}