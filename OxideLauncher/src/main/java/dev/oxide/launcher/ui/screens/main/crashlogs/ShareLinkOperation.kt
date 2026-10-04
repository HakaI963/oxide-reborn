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

package dev.oxide.launcher.ui.screens.main.crashlogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.oxide.launcher.R
import dev.oxide.launcher.crashlogs.LinkNotFoundException
import dev.oxide.launcher.ui.screens.main.oxide.OxideConfirmDialog
import dev.oxide.launcher.ui.screens.main.oxide.OxideTaskDialog
import dev.oxide.launcher.utils.string.getMessageOrToString

/**
 * 上传游戏崩溃日志操作流程
 *
 * 三个状态各自对应弹窗家族里的一张：确认（上传前提示）、进度（上传中）、确认（失败）。
 * 上传中这一张以前是 Material 的 `AlertDialog` 加一个永远转下去的波浪进度条；
 * 现在换成 [OxideTaskDialog]，进度不可知时就画一条空槽加一行"处理中"，
 * 不再让一个无尽动画一直转着。取消按钮与取消上传的行为都还在。
 */
sealed interface ShareLinkOperation {
    data object None : ShareLinkOperation
    /** 提示对话框 */
    data object Tip : ShareLinkOperation
    /**
     * 上传日志中
     * @param apiRoot API 站点链接，仅作透明化展示
     */
    data class Uploading(val apiRoot: String) : ShareLinkOperation
    /** 发生错误，展示对话框 */
    data class Error(val error: Throwable) : ShareLinkOperation
}

@Composable
fun ShareLinkOperation(
    operation: ShareLinkOperation,
    onChange: (ShareLinkOperation) -> Unit,
    onUpload: () -> Unit,
    onUploadChancel: () -> Unit
) {
    when (operation) {
        is ShareLinkOperation.None -> {}
        is ShareLinkOperation.Tip -> {
            OxideConfirmDialog(
                title = stringResource(R.string.crash_link_share_button),
                message = stringResource(R.string.crash_link_share_tip),
                confirmText = stringResource(R.string.generic_confirm),
                cancelText = stringResource(R.string.generic_cancel),
                //分享日志会把日志传到公开平台上，不能随手点掉
                dismissByDialog = false,
                onConfirm = onUpload,
                onDismiss = { onChange(ShareLinkOperation.None) },
            )
        }
        is ShareLinkOperation.Uploading -> {
            OxideTaskDialog(
                title = stringResource(R.string.crash_link_share_button),
                message = stringResource(
                    R.string.crash_link_share_uploading,
                    operation.apiRoot,
                ),
                //上传本身没有可知的进度：给的是"进度不可知"而不是假的 0%
                progress = null,
                onCancel = onUploadChancel,
                cancelText = stringResource(R.string.generic_cancel),
            )
        }
        is ShareLinkOperation.Error -> {
            OxideConfirmDialog(
                title = stringResource(R.string.crash_link_share_failed),
                confirmText = stringResource(R.string.generic_confirm),
                message = when (val error = operation.error) {
                    is LinkNotFoundException -> {
                        stringResource(R.string.crash_link_share_failed_link_not_found)
                    }
                    else -> {
                        error.getMessageOrToString()
                    }
                },
                //只有一个"知道了"：上传已经结束了，没有可回退的动作
                cancelText = "",
                dismissByDialog = false,
                onConfirm = { onChange(ShareLinkOperation.None) },
                onDismiss = { onChange(ShareLinkOperation.None) },
            )
        }
    }
}