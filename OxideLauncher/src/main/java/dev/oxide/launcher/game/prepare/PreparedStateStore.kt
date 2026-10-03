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

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 上一次成功准备时，逐个文件记录下的 (大小, 修改时间)
 *
 * 这是整个缓存里唯一能替代完整 SHA-1 校验的东西，所以它必须精确：
 * 只有当磁盘上的大小与修改时间都与记录完全一致时才认为可信，
 * 任何一点不同都退回真实的校验流程。
 */
class TrustedFiles private constructor(
    private val stamps: Map<String, LongArray>
) {
    /** 记录过的文件数 */
    val size: Int get() = stamps.size

    /**
     * 判断一个文件是否可以直接信任
     *
     * @param file 目标文件
     * @return true 表示它与上一次成功准备时完全一致，可以跳过完整校验
     */
    fun isTrusted(file: File): Boolean {
        val recorded = stamps[file.absolutePath] ?: return false
        // isFile 会先做一次 stat，长度与修改时间又会各做一次；
        // 与读取整个文件做 SHA-1 相比仍然是两个数量级的差距。
        if (!file.isFile) return false
        return file.length() == recorded[0] && file.lastModified() == recorded[1]
    }

    companion object {
        val EMPTY = TrustedFiles(emptyMap())

        /** 从 "<size>\t<mtime>\t<path>" 每行一条的文本构建 */
        fun parse(lines: Sequence<String>): TrustedFiles {
            val map = HashMap<String, LongArray>()
            for (line in lines) {
                val first = line.indexOf('\t')
                if (first <= 0) continue
                val second = line.indexOf('\t', first + 1)
                if (second <= first) continue
                val size = line.substring(0, first).toLongOrNull() ?: continue
                val mtime = line.substring(first + 1, second).toLongOrNull() ?: continue
                map[line.substring(second + 1)] = longArrayOf(size, mtime)
            }
            return TrustedFiles(map)
        }

        fun of(stamps: Map<String, LongArray>): TrustedFiles = TrustedFiles(stamps)

        /** 序列化为 "<size>\t<mtime>\t<path>" 每行一条 */
        fun render(files: List<File>): String = buildString {
            for (file in files) {
                if (!file.isFile) continue
                append(file.length()).append('\t')
                append(file.lastModified()).append('\t')
                append(file.absolutePath).append('\n')
            }
        }
    }
}

/**
 * 准备状态的落盘
 *
 * 状态写在版本自己的 `OxideLauncher/` 目录下，也就是 `version.config` 和
 * `latest_game.log` 已经所在的位置。复用这个目录意味着删除版本、复制版本、
 * 重命名版本、取消安装等操作会自动把它一起处理掉，不需要额外的清理逻辑。
 *
 * 两份文件：
 * - `prepare_state.json` —— 体积小、结构化的状态
 * - `prepare_files.txt` —— 逐文件的 (大小, 修改时间)，可能有几千行
 *
 * 写入一律是"临时文件 → 完整写完 → 原子改名"，因此进程在准备途中被杀时，
 * 旧状态要么原样保留、要么已经变成新的完整状态，不存在写到一半的中间态。
 */
object PreparedStateStore {

    const val STATE_FILE = "prepare_state.json"
    const val FILES_FILE = "prepare_files.txt"
    const val LOCK_FILE = "prepare.lock"

    private const val TEMP_SUFFIX = ".tmp"

    /** 原子写入：先写临时文件，再改名替换 */
    fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: throw IOException("No parent directory for $target")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create $parent")
        }
        val temp = File.createTempFile(target.name, TEMP_SUFFIX, parent)
        try {
            temp.writeText(content)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    /** 删除可能残留的临时文件；读取失败一律按"没有缓存"处理 */
    fun discardStaleTempFiles(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }
}