/*
 * Oxide Launcher
 * Copyright (C) 2025 Star1xr and contributors
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 屏幕安全边距（系统栏 + 刘海/显示切口），单位是像素
 *
 * 为什么绕开 Compose 的 `WindowInsets`：这里要的是**物理**边距——右下角就是右下角，
 * 镜像布局也一样——而 `WindowInsets` 取左右值在不同 Compose 版本里签名不同
 * （有的吃 `Density`，有的吃 `LayoutDirection`），每次升级都要重新对一次 API，
 * 对错只在编译期才暴露。`ViewCompat.getRootWindowInsets` 这条路是稳定的，
 * 并且已经在本仓库的 Activity 里用着。
 *
 * 拿不到时返回 [Insets.NONE]：那等价于"没有边距"，也就是回到这个特性之前的行为，
 * 而不是把某个控件藏起来。
 */
@Composable
internal fun rememberSafeScreenInsets(): Insets {
    val view = LocalView.current
    return remember(view) {
        val rootInsets = ViewCompat.getRootWindowInsets(view)
        if (rootInsets == null) {
            Insets.NONE
        } else {
            rootInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
        }
    }
}