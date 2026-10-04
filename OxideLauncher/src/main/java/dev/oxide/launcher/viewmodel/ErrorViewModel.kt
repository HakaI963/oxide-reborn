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

package dev.oxide.launcher.viewmodel

import dev.oxide.launcher.ui.theme.Oxide
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.screens.main.oxide.showOxideMessageDialog
import dev.oxide.launcher.ui.toAndroidString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ErrorViewModel : ViewModel() {
    private val _errorEvents = MutableSharedFlow<ThrowableMessage>()
    val errorEvents: SharedFlow<ThrowableMessage> = _errorEvents

    fun showError(message: ThrowableMessage) {
        viewModelScope.launch {
            _errorEvents.emit(message)
        }
    }

    /**
     * 通用的错误信息展示对话框
     *
     * 每个 Oxide 页面最终都把错误报到这儿，因此这一处的呈现就是整个界面的错误样式。
     * 它以前是 `MaterialAlertDialogBuilder`：一块 Material 的圆角卡片，底色与
     * `Oxide.BgElevated` 不同，文字也不是 Oxide 的字号，在近黑的界面里明显跳出来。
     * 现在换成与其它对话框同一块面板（见 `ui/screens/main/oxide/OxideDialogs.kt`），
     * 底色不透明、圆角与描边一致、说明文字在有界的内容区里滚。
     *
     * 仍然是"一次性"的：只有一个确认按钮，点它或关闭都收掉这一次展示。
     * 公开签名与出错时机都没有变，所以 `rememberOxideErrorViewModel()` 的调用点
     * 一行都不用改。
     */
    suspend fun showErrorDialog(
        context: Context,
        tm: ThrowableMessage
    ) {
        withContext(Dispatchers.Main) {
            showOxideMessageDialog(
                context = context,
                title = tm.title.toAndroidString(context),
                message = tm.message.toAndroidString(context),
            )
        }
    }

    data class ThrowableMessage(val title: AndroidStringText, val message: AndroidStringText)
}