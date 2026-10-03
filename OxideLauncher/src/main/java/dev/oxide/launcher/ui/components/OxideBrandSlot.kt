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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 侧栏里 logo 的落点，也是开场动画与侧栏之间的交接信号
 *
 * 开场动画结束时，**那一个** logo 必须停在这里，因此侧栏要把自己品牌槽的实时位置
 * 报上来，而不是让动画去猜一个坐标。没有侧栏时它保持未设置，动画会退回一个默认位置，
 * 动画依然能正常结束，只是落点不是侧栏。
 *
 * 交接靠两个量，而且有先后：
 * - [landingScale]：两侧都量到之后立刻写进来，不用等旅程走完；
 * - [landed]：旅程真的走完才置位，这时开场动画交出 logo，侧栏从这一帧起画它。
 *
 * 侧栏必须照抄 [landingScale]，不能自己另算一遍：开场动画量的是 logo 的真实宽度，
 * 而"图形 37 + 间距 10 + 字标 250dp"这种估计值能差出四成，落地那一帧会明显跳一下。
 */
@Stable
class OxideBrandSlotState {
    /** 槽中心在整屏中的位置 */
    var centerInRoot by mutableStateOf<Offset?>(null)

    /** 槽宽度（像素） */
    var width by mutableFloatStateOf(0f)

    /** 旅程是否已经走完。走完之后 logo 归侧栏常驻渲染 */
    var landed by mutableStateOf(false)

    /** 落位缩放。开场动画算好后写进来，侧栏照抄，两边就不会有尺寸差 */
    var landingScale by mutableFloatStateOf(0f)

    internal fun report(center: Offset, widthPx: Float) {
        centerInRoot = center
        width = widthPx
    }

    /**
     * 一旦两侧都量到就把落位缩放写进来。
     * 必须早于 [markLanded]：交接的那一帧侧栏读到的必须是最终尺寸。
     */
    internal fun shareLandingScale(scale: Float) {
        landingScale = scale
    }

    /** 旅程真的走完时才调用。写进去之前 logo 一直归开场动画所有 */
    internal fun markLanded() {
        landed = true
    }
}

/** 主界面通过它把侧栏品牌槽的实测位置交给开场动画 */
val LocalOxideBrandSlot = compositionLocalOf { OxideBrandSlotState() }

/**
 * 侧栏品牌槽里的常驻 logo
 *
 * 放进侧栏品牌槽里即可，它自己会铺满槽位并居中：
 *
 * ```kotlin
 * Box(
 *     modifier = Modifier.fillMaxWidth().height(Oxide.BrandSlotHeight)
 *         .onGloballyPositioned { brandSlot.report(...) },
 *     contentAlignment = Alignment.Center
 * ) {
 *     OxideBrandSlotLogo()
 * }
 * ```
 *
 * 两件事必须交给它，不要在侧栏里自己写 logo：
 * 1. 尺寸用 [OxideBrandSlotState.landingScale]，也就是开场动画实测算出来的落位缩放。
 *    自己用"37+10+250dp"之类的估计值另算一遍，落地那一帧的宽度能差出四成。
 * 2. 时机用 [OxideBrandSlotState.landed]：旅程走完之前侧栏不画任何东西，
 *    那段时间 logo 在开场动画手里，全屏任何时刻都只有一个 logo。
 *
 * 缩放走 [OxideLogo] 的尺寸参数而不是 graphicsLayer：品牌槽只有 34dp 高，
 * 而 logo 自然高度约 60dp，靠 graphicsLayer 缩放的话布局阶段仍会按 60dp 量，
 * 在窄槽里会被裁切、把图形压成非正方形。
 */
@Composable
fun OxideBrandSlotLogo(modifier: Modifier = Modifier) {
    val slot = LocalOxideBrandSlot.current
    val scale = slot.landingScale

    if (!slot.landed || scale <= 0f) return

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OxideLogo(
            markSize = Oxide.MarkSize * scale,
            wordmarkSize = OxideLogoWordmark * scale,
            gap = OxideLogoGap * scale,
        )
    }
}