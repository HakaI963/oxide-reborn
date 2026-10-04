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
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogOption
import dev.oxide.launcher.ui.screens.main.oxide.OxideListDialog

/** 菜单里每一行的稳定标识：回调按它走，列表也用它当 itemKey */
private const val KEY_VIEW = "view"
private const val KEY_SHARE = "share"
private const val KEY_UPLOAD = "upload"

sealed interface LogShareMenuOperation {
    data object None : LogShareMenuOperation
    /** 打开日志操作菜单 */
    data object ShowMenu : LogShareMenuOperation
}

/**
 * 实例设置里"分享日志"点开的那张菜单
 *
 * 这一块过去是四个竖着的 Material 按钮撑出来的一张 Surface，底色只有 `cardColor`
 * 那一档透明度。现在它就是 [OxideListDialog] 的动作模式（`selectable = false`）：
 * 行为不变——点一行立刻干活并收起菜单，只是行不再画单选标记，也不画图标，
 * 每个动作靠自己的文字说清自己是什么。
 *
 * 关闭不再是一行：面板自带标题栏的 ✕、遮罩点击和返回键，三条路都还在，
 * 而"关闭"作为一个菜单项本来就在重复面板自己的行为。分享链接不可用时那一行
 * 被标成不可选，并在第二行写明原因，而不是像旧版那样灰着仍然可点。
 */
@Composable
fun LogShareMenu(
    operation: LogShareMenuOperation,
    onChange: (LogShareMenuOperation) -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    canUpload: Boolean,
    onUpload: () -> Unit
) {
    when (operation) {
        is LogShareMenuOperation.None -> {}
        is LogShareMenuOperation.ShowMenu -> {
            OxideListDialog(
                title = stringResource(R.string.crash_share_logs),
                options = listOf(
                    OxideDialogOption(
                        key = KEY_VIEW,
                        label = stringResource(R.string.generic_view),
                    ),
                    OxideDialogOption(
                        key = KEY_SHARE,
                        label = stringResource(R.string.crash_share_logs),
                    ),
                    OxideDialogOption(
                        key = KEY_UPLOAD,
                        label = stringResource(R.string.crash_link_share_button),
                        enabled = canUpload,
                    ),
                ),
                onOptionSelected = { key ->
                    when (key) {
                        KEY_VIEW -> onView()
                        KEY_SHARE -> onShare()
                        KEY_UPLOAD -> onUpload()
                    }
                    onChange(LogShareMenuOperation.None)
                },
                onDismiss = { onChange(LogShareMenuOperation.None) },
                selectable = false,
            )
        }
    }
}