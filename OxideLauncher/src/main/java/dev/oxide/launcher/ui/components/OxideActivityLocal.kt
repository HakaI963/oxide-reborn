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

package dev.oxide.launcher.ui.components

import androidx.compose.runtime.compositionLocalOf
import dev.oxide.launcher.ui.activities.MainActivity

/**
 * 当前承载界面的 [MainActivity]
 *
 * 启动流程要拿到 Activity 上按 `by viewModels()` 持有的那几个实例：Compose 的
 * `viewModel()` 在 NavDisplay 条目里解析到的是条目自己的 store，会拿到**另一个**对象，
 * 于是深层跳转会静默失效。
 *
 * 之前这里靠 `LocalContext.current as? MainActivity` 向下转型来拿。对 Context 做这种转型在预览、
 * 被包装的 Context 或多 Context 场景下都会得到 null，而且持有 Activity 会延长它的生命周期，
 * 所以改成由 [MainActivity] 自己提供。取不到时调用方回退到 `viewModel()`，行为不变。
 */
val LocalMainActivity = compositionLocalOf<MainActivity?> { null }
