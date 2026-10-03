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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset

/**
 * 侧栏里 logo 的落点
 *
 * 开场动画结束时，**那一个** logo 必须停在这里，因此侧栏要把自己品牌槽的实时位置
 * 报上来，而不是让动画去猜一个坐标。没有侧栏时它保持未设置，动画会退回一个默认位置，
 * 动画依然能正常结束，只是落点不是侧栏。
 */
@Stable
class OxideBrandSlotState {
    /** 槽中心在整屏中的位置 */
    var centerInRoot by mutableStateOf<Offset?>(null)

    /** 槽宽度（像素） */
    var width by mutableStateOf(0f)

    internal fun report(center: Offset, widthPx: Float) {
        centerInRoot = center
        width = widthPx
    }
}