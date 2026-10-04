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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.ui.components.LocalMainActivity
import dev.oxide.launcher.viewmodel.ErrorViewModel

/**
 * MainActivity 上那个 ErrorViewModel
 *
 * 裸 `viewModel()` 在 Nav3 条目里解析到的是**条目自己的** ViewModelStore，
 * 和 MainActivity 用 `by viewModels()` 持有的那一个不是同一个对象。
 * ErrorViewModel 用 SharedFlow 派发错误，而那个"另一个"实例上没有任何收集者，
 * 于是 `showError(...)` 发出去就消失——用户看不到任何提示。
 *
 * 同一类问题也出现在 ScreenBackStackViewModel 上：拿不到活动那一份，
 * 深层跳转会静默失效。见 `rememberOxideScreenBackStack()`。
 */
@Composable
internal fun rememberOxideErrorViewModel(): ErrorViewModel =
    LocalMainActivity.current?.errorViewModel ?: viewModel()
