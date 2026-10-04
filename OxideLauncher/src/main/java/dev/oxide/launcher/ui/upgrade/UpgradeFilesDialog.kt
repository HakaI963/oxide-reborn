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

package dev.oxide.launcher.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogOption
import dev.oxide.launcher.ui.screens.main.oxide.OxideListDialog
import dev.oxide.launcher.upgrade.RemoteData
import dev.oxide.launcher.utils.device.Architecture
import dev.oxide.launcher.utils.file.formatFileSize

/**
 * 选择要下载的安装包
 *
 * 架构不匹配的包被标成不可选，而不是像旧版那样灰着还能点、点了什么也不发生。
 * 不可选的原因写在行的第二行上，因此不依赖颜色，朗读时也听得见。
 */
@Composable
fun UpgradeFilesDialog(
    data: RemoteData,
    onDismissRequest: () -> Unit,
    onFileSelected: (RemoteData.RemoteFile) -> Unit
) {
    //当前设备的架构信息
    val currentArch: RemoteData.RemoteFile.Arch = remember(data) {
        when (Architecture.getDeviceArchitecture()) {
            Architecture.ARCH_ARM -> RemoteData.RemoteFile.Arch.ARM
            Architecture.ARCH_ARM64 -> RemoteData.RemoteFile.Arch.ARM64
            Architecture.ARCH_X86 -> RemoteData.RemoteFile.Arch.X86
            Architecture.ARCH_X86_64 -> RemoteData.RemoteFile.Arch.X86_64
            else -> RemoteData.RemoteFile.Arch.ALL
        }
    }

    val current = remember(data) {
        data.files.find { it.arch == currentArch }
    }

    OxideListDialog(
        title = stringResource(R.string.upgrade_files),
        options = upgradeFileOptions(data.files, currentArch),
        onOptionSelected = { key ->
            //按 uri 回取文件，而不是把文件本身塞进 key：
            //key 也要当列表的 itemKey 用，得是稳定且唯一的
            data.files.firstOrNull { it.uri == key }?.let(onFileSelected)
        },
        onDismiss = onDismissRequest,
        currentKey = current?.uri,
        confirmText = stringResource(R.string.generic_download),
        emptyText = stringResource(R.string.oxide_dlg_empty_options),
    )
}

/**
 * 把远端安装包清单映射成对话框的选项
 *
 * `RemoteData` 是数据类，可以直接 remember；但第二行的文案要读字符串资源，
 * 只能在组合里取，因此这一段不放进 remember。
 */
@Composable
private fun upgradeFileOptions(
    files: List<RemoteData.RemoteFile>,
    currentArch: RemoteData.RemoteFile.Arch,
): List<OxideDialogOption> = files.map { file ->
    OxideDialogOption(
        key = file.uri,
        label = file.fileName,
        detail = upgradeFileDetail(file, currentArch),
        //根据设备架构决定哪些安装包不能选择，避免下载到错误架构的安装包（允许选择全架构）
        enabled = file.arch == RemoteData.RemoteFile.Arch.ALL || file.arch == currentArch,
    )
}

/**
 * 一行下面的说明：架构、大小，以及"这台设备推荐"
 *
 * 旧版这里是一个星形图标，图标没有文字，因此读屏软件只会念出文件名。
 * 换成文字之后"为什么推荐这个"才真的说清楚了。
 */
@Composable
private fun upgradeFileDetail(
    file: RemoteData.RemoteFile,
    currentArch: RemoteData.RemoteFile.Arch,
): String {
    val parts = mutableListOf(file.arch.getDisplayString())

    if (currentArch == file.arch) {
        parts += stringResource(R.string.oxide_dlg_option_recommended)
    }

    //远端清单是静态文件，构建之前拿不到真实字节数，
    //所以 size 缺省就是 0——这时候宁可不说，也不要显示"0.00 KB"
    file.size.takeIf { it > 0L }?.let { size ->
        parts += stringResource(R.string.upgrade_version_size, formatFileSize(size))
    }

    return parts.joinToString("  ·  ")
}

@Composable
private fun RemoteData.RemoteFile.Arch.getDisplayString(): String {
    return when (this) {
        RemoteData.RemoteFile.Arch.ALL -> stringResource(R.string.upgrade_files_arch, stringResource(R.string.generic_all))
        RemoteData.RemoteFile.Arch.ARM -> stringResource(R.string.upgrade_files_arch, "arm")
        RemoteData.RemoteFile.Arch.ARM64 -> stringResource(R.string.upgrade_files_arch, "arm64")
        RemoteData.RemoteFile.Arch.X86 -> stringResource(R.string.upgrade_files_arch, "x86")
        RemoteData.RemoteFile.Arch.X86_64 -> stringResource(R.string.upgrade_files_arch, "x86_64")
    }
}