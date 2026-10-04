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

package dev.oxide.launcher.ui.screens.game.multiplayer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.oxide.launcher.R
import dev.oxide.launcher.terracotta.Terracotta
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.main.oxide.OxideTextEntryDialog
import dev.oxide.launcher.utils.string.isEmptyOrBlank
import net.burningtnt.terracotta.TerracottaAndroidAPI

/** 等待中：房客操作状态 */
sealed interface GuestWaitingOperation {
    data object None : GuestWaitingOperation
    /** 被点击，开始输入邀请码 */
    data object OnClick : GuestWaitingOperation
}

@Composable
fun GuestWaitingOperation(
    operation: GuestWaitingOperation,
    onChange: (GuestWaitingOperation) -> Unit,
    onPositive: (roomCode: String) -> Unit,
    onShowToast: (AndroidStringText) -> Unit
) {
    when (operation) {
        is GuestWaitingOperation.None -> {}
        is GuestWaitingOperation.OnClick -> {
            InviteCodeInputDialog(
                onPositive = onPositive,
                onDismiss = {
                    onChange(GuestWaitingOperation.None)
                },
                onShowToast = onShowToast
            )
        }
    }
}

/**
 * 房客输入邀请码对话框
 */
@Composable
private fun InviteCodeInputDialog(
    onPositive: (roomCode: String) -> Unit,
    onDismiss: () -> Unit,
    onShowToast: (AndroidStringText) -> Unit
) {
    var code by remember { mutableStateOf("") }

    /** 验证不通过时 */
    var isError by remember { mutableStateOf(false) }
    /** 格式提示。只会用到无参数的字符串资源，所以这里直接留 id */
    val supportingText: Int? = remember(code) {
        if (code.isEmpty()) {
            //还未填写内容
            isError = false
            return@remember null
        }

        val type = Terracotta.parseRoomCode(code)
        when (type) {
            TerracottaAndroidAPI.RoomType.TERRACOTTA_LEGACY -> R.string.terracotta_status_waiting_guest_prompt_terracotta_legacy
            TerracottaAndroidAPI.RoomType.PCL2CE -> R.string.terracotta_status_waiting_guest_prompt_pcl2ce
            TerracottaAndroidAPI.RoomType.SCAFFOLDING -> R.string.terracotta_status_waiting_guest_prompt_scaffolding
            else -> null
        }.also { text ->
            //根据是否检测出对应格式判断
            isError = text == null
        } ?: R.string.terracotta_status_waiting_guest_prompt_invalid
    }

    OxideTextEntryDialog(
        title = stringResource(R.string.terracotta_status_waiting_guest_prompt_title),
        label = "U/XXXX-XXXX-XXXX-XXXX",
        value = code,
        onValueChange = { value ->
            code = value
        },
        // 旧实现把“认得出格式”的提示放在 supportingText、把“认不出来”显示成错误色。
        // 这里保持同样的分工：认不出来才走 errorText，其余走普通提示。
        errorText = if (isError) supportingText?.let { stringResource(it) } else null,
        supportText = if (!isError) supportingText?.let { stringResource(it) } else null,
        singleLine = true,
        isValid = { !isError && it.isNotBlank() && Terracotta.parseRoomCode(it) != null },
        confirmText = stringResource(R.string.generic_confirm),
        cancelText = stringResource(R.string.generic_cancel),
        onConfirm = {
            if (isError || code.isEmptyOrBlank() || Terracotta.parseRoomCode(code) == null) {
                onShowToast(androidText(R.string.terracotta_status_waiting_guest_prompt_invalid))
            } else {
                onPositive(code)
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}