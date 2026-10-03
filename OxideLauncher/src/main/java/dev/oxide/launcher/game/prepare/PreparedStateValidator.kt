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

/**
 * 判断一份准备状态是否仍然成立
 *
 * 这里刻意做成**纯函数**：输入全部是普通数据，不碰文件也不碰 Android 框架。
 * 这样"什么会让缓存失效"这条规则可以被完整地测掉，而不是只能靠人工推理。
 *
 * 指纹只覆盖**准备阶段真正读取或写入**的东西：
 * - 版本清单（自身与继承的原版）：决定了要准备哪些文件
 * - 模组文件夹：决定 touchcontroller 与 lwjgl3ify 两个补丁
 * - 是否跳过完整性校验、下载源：改变校验与下载结果
 * - 缓存格式版本、准备算法版本、启动器版本：算法或程序变了旧缓存一律作废
 *
 * 刻意**不**纳入指纹的东西：
 * - 账号、渲染器、驱动、运行时/JRE、内存分配、语言、分辨率、控制布局
 *   ——它们不影响准备阶段，重做准备毫无意义
 * - Minecraft 自己会写入的目录：logs、saves、screenshots、crash-reports、
 *   resourcepacks、shaderpacks、.fabric、.mixin.out 等
 *   ——它们本来就不参与准备，把它们算进来只会让缓存永远失效
 */
object PreparedStateValidator {

    /**
     * @return null 表示状态仍然有效；否则返回让它失效的原因
     */
    fun invalidateReason(
        state: PreparedState?,
        launcherVersionCode: Long,
        integrityDisabled: Boolean,
        downloadSource: String,
        currentVersionJsonPath: String,
        currentVersionJson: FileStamp?,
        currentParentVersionJson: FileStamp?,
        currentMods: List<ModStamp>
    ): PrepareInvalidateReason? {
        if (state == null) return PrepareInvalidateReason.NO_STATE
        if (state.schemaVersion != PREPARED_STATE_SCHEMA_VERSION) {
            return PrepareInvalidateReason.SCHEMA_CHANGED
        }
        if (state.preparationVersion != PREPARATION_VERSION) {
            return PrepareInvalidateReason.PREPARATION_CHANGED
        }
        if (state.launcherVersionCode != launcherVersionCode) {
            return PrepareInvalidateReason.LAUNCHER_UPDATED
        }
        if (state.integrityDisabled != integrityDisabled) {
            return PrepareInvalidateReason.INTEGRITY_SETTING_CHANGED
        }
        if (state.downloadSource != downloadSource) {
            return PrepareInvalidateReason.DOWNLOAD_SOURCE_CHANGED
        }
        // 版本被重命名或移动后记录里的路径就不成立了，此时必然要求重建
        if (state.versionJsonPath != currentVersionJsonPath) {
            return PrepareInvalidateReason.VERSION_JSON_CHANGED
        }
        if (state.versionJson != currentVersionJson) {
            return PrepareInvalidateReason.VERSION_JSON_CHANGED
        }
        // 父版本路径取自记录本身：只要自身清单没变，继承关系就不可能变
        if (state.parentVersionJsonPath == null) {
            if (state.parentVersionJson != null) return PrepareInvalidateReason.PARENT_VERSION_JSON_CHANGED
        } else if (state.parentVersionJson != currentParentVersionJson) {
            return PrepareInvalidateReason.PARENT_VERSION_JSON_CHANGED
        }
        // 启用/禁用是一次改名，所以被禁用的条目也在列表里，改名同样会让它失效
        if (state.mods != currentMods) {
            return PrepareInvalidateReason.MODS_CHANGED
        }
        // lwjgl3ify 打补丁需要真实的模组对象，因此这种实例每次都完整准备
        if (state.lwjgl3ifyVersion != null) {
            return PrepareInvalidateReason.LOADER_MOD_PRESENT
        }
        return null
    }
}