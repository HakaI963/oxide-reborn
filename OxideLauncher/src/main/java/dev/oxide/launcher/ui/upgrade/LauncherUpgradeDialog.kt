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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.MarkdownView
import dev.oxide.launcher.ui.components.defaultRichTextStyle
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.upgrade.RemoteData
import dev.oxide.launcher.upgrade.findCurrentBody
import dev.oxide.launcher.upgrade.getCurrentCouldDrive
import dev.oxide.launcher.utils.formatDate
import java.util.Locale

/**
 * 启动器有新版本时的提示
 *
 * 这是一次冷启动就会看到的那一张对话框，因此它必须和其它对话框是同一块面板：
 * [OxideDialogShell] 就是 `ui/components/Dialogs.kt` 里那套 Material 弹窗的替代品。
 *
 * 行为与旧版完全一致——点"查看"选安装包、点"忽略"记下版本号、点网盘直接打开链接。
 * 只有呈现变了：面板底色不透明、标题走 `Oxide.Type.DrawerTitle`、三个动作都有文字，
 * 因此不必靠左右位置分辨。
 */
@Composable
fun UpgradeDialog(
    data: RemoteData,
    onDismissRequest: () -> Unit,
    onFilesClick: () -> Unit,
    onIgnored: () -> Unit,
    onLinkClick: (String) -> Unit,
    onCloudDriveClick: (RemoteData.CloudDrive) -> Unit
) {
    val body = remember(data) {
        data.findCurrentBody(Locale.getDefault()) ?: data.defaultBody
    }
    val cloudDrive = remember(data) {
        data.getCurrentCouldDrive(Locale.getDefault())
    }

    OxideDialogShell(
        title = stringResource(R.string.upgrade_new),
        onDismissRequest = onDismissRequest,
        body = { contentMaxHeight ->
            //版本号与更新时间单独用 Oxide 的字号画出来，而不是塞进 markdown 里当正文：
            //它们是这条信息的第一层，字号应当与正文分得开
            val versionStr = stringResource(R.string.upgrade_version_change, data.version)
            val dateStr = stringResource(
                R.string.upgrade_version_create_at,
                formatDate(
                    input = data.createdAt,
                    pattern = stringResource(R.string.date_format)
                )
            )

            //先夹住高度，再让更新日志在里面滚。面板本体已经夹过一次，这里再夹一次
            //是刻意的：markdown 渲染出来的高度完全由远端文本决定，不能让它去撑面板。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = contentMaxHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = versionStr,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                )
                Text(
                    text = dateStr,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                )
                Spacer(Modifier.height(4.dp))
                CompositionLocalProvider(
                    LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            onLinkClick(uri)
                        }
                    }
                ) {
                    MarkdownView(
                        content = body.markdown,
                        modifier = Modifier.fillMaxWidth(),
                        richTextStyle = defaultRichTextStyle(),
                    )
                }
            }
        },
        actions = {
            OxideDialogActions(
                cloudDrive = cloudDrive,
                onLinkClick = onLinkClick,
                onCloudDriveClick = onCloudDriveClick,
                onIgnoredClick = {
                    onIgnored()
                    onDismissRequest()
                },
                onFilesClick = onFilesClick,
            )
        },
    )
}

/**
 * 三个动作：网盘 / 忽略 / 查看
 *
 * 单独拆出来是因为它是这一张对话框里唯一的按钮栏，而 [OxideDialogShell] 要的是
 * 一个 `RowScope` 里的内容；直接写在调用处会让那行 lambda 缩进到看不清。
 */
@Composable
private fun OxideDialogActions(
    cloudDrive: RemoteData.CloudDrive?,
    onLinkClick: (String) -> Unit,
    onCloudDriveClick: (RemoteData.CloudDrive) -> Unit,
    onIgnoredClick: () -> Unit,
    onFilesClick: () -> Unit,
) {
    OxideButton(
        text = stringResource(R.string.upgrade_more),
        onClick = onFilesClick,
        tone = OxideButtonTone.Primary,
    )
    OxideButton(
        text = stringResource(R.string.generic_ignore),
        onClick = onIgnoredClick,
        tone = OxideButtonTone.Secondary,
    )
    if (cloudDrive != null) {
        OxideButton(
            text = stringResource(R.string.upgrade_cloud_drive),
            onClick = {
                when {
                    //未配置多网盘链接，使用默认链接（旧版兼容，必定会有）
                    cloudDrive.links.isEmpty() -> onLinkClick(cloudDrive.link)
                    //只有一个网盘链接，则直接访问链接
                    cloudDrive.links.size == 1 -> onLinkClick(cloudDrive.links[0].link)
                    else -> onCloudDriveClick(cloudDrive)
                }
            },
            tone = OxideButtonTone.Secondary,
        )
    }
}