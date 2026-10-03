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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 开场动画
 *
 * 界面从第一帧就已经就位并渲染在下面，只有一块黑色幕布盖住它。
 * **只有一个 logo**：它从屏幕中心出发，落在侧栏品牌槽的中心，然后就留在那里。
 * 落点由侧栏实测上报，不是写死的坐标，所以换屏幕尺寸也不会跑偏。
 *
 * 时间轴与参考稿一致：
 * | 进度 | 状态 |
 * |---|---|
 * | 0% | 屏幕中心，缩放 1 |
 * | 13% | 原地极轻微放大到 1.035 |
 * | 24% | 回到 1.0 |
 * | 70% | 开始移动 |
 * | 100% | 落在侧栏品牌槽，缩放到落地尺寸 |
 *
 * 幕布在 2450ms 开始淡出，也就是旅程走到约 78% 时——界面此时已经可见，logo 还在路上，
 * 因此不会出现"黑屏等动画结束"的停顿。下划线在 280ms 扫入、1650ms 收回，
 * 与移动段完全不重叠。
 *
 * @param slot 侧栏上报的品牌槽；未上报时 logo 会落在屏幕左上角的默认位置
 */
@Composable
fun OxideIntro(
    modifier: Modifier = Modifier,
    slot: OxideBrandSlotState,
    /** 品牌槽宽度上限，与参考稿一致 */
    maxSlotWidth: Dp = Oxide.Motion.BrandSlotMaxWidthDp.dp,
    /** 跳过动画，直接呈现终态 */
    skipAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    var rootWidth by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(if (skipAnimation) 1f else 0f) }
    var curtain by remember { mutableFloatStateOf(if (skipAnimation) 0f else 1f) }
    var lineVisible by remember { mutableFloatStateOf(0f) }

    // 侧栏可能晚于第一帧才完成布局，所以终点每帧都重新读，动画不会因此停在错误位置
    LaunchedEffect(Unit) {
        if (skipAnimation) return@LaunchedEffect
        val started = withFrameNanos { it }
        while (true) {
            val elapsed = (withFrameNanos { it } - started) / 1_000_000f
            if (elapsed >= Oxide.Motion.IntroJourneyMs) break
            progress = (elapsed / Oxide.Motion.IntroJourneyMs).coerceIn(0f, 1f)

            val curtainT =
                ((elapsed - Oxide.Motion.IntroCurtainRevealMs) / Oxide.Motion.IntroCurtainFadeMs)
                    .coerceIn(0f, 1f)
            curtain = 1f - curtainT

            lineVisible = when {
                elapsed < Oxide.Motion.IntroLineInDelayMs -> 0f
                elapsed < Oxide.Motion.IntroLineInDelayMs + Oxide.Motion.IntroLineInMs ->
                    (elapsed - Oxide.Motion.IntroLineInDelayMs) / Oxide.Motion.IntroLineInMs

                elapsed < Oxide.Motion.IntroLineOutDelayMs -> 1f
                elapsed < Oxide.Motion.IntroLineOutDelayMs + Oxide.Motion.IntroLineOutMs ->
                    1f - (elapsed - Oxide.Motion.IntroLineOutDelayMs) / Oxide.Motion.IntroLineOutMs

                else -> 0f
            }
        }
        progress = 1f
        curtain = 0f
        lineVisible = 0f
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged {
        rootWidth = it.width.toFloat()
        rootHeight = it.height.toFloat()
    }) {
        content()

        if (curtain <= 0f && progress >= 1f) return@Box

        val d = density
        // 起始宽度 = 图形 37 + 间距 10 + 字标（字标宽度在 60sp 下基本固定）
        val startWidthPx = with(d) { (37f + 10f + 250f).dp.toPx() }
        val slotWidthPx = slot.width
        // 侧栏还没上报时退回一个保守的默认位置，动画照常结束
        val destX = slot.centerInRoot?.x ?: with(d) { Oxide.SidebarWidth.toPx() / 2f }
        val destY = slot.centerInRoot?.y ?: with(d) { Oxide.BrandSlotHeight.toPx() / 2f }
        val targetWidthDp = minOf(
            with(d) { if (slotWidthPx > 0f) slotWidthPx.toDp() else Oxide.Motion.BrandSlotMaxWidthDp.dp },
            maxSlotWidth
        )
        val destScale = with(d) { targetWidthDp.toPx() / startWidthPx }
            .coerceIn(Oxide.Motion.IntroScaleMin, Oxide.Motion.IntroScaleMax)

        val journey = introGeometry(
            progress = progress,
            centerX = rootWidth / 2f,
            centerY = rootHeight / 2f,
            destX = destX,
            destY = destY,
            destScale = destScale,
        )

        // 落位之后 logo 留在侧栏槽里，因此它必须画在界面之上
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = journey.x
                    translationY = journey.y
                    scaleX = journey.scale
                    scaleY = journey.scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                },
            contentAlignment = Alignment.Center
        ) {
            OxideLogo()
        }

        // 下划线：贴在字标底部，扫入后向左收回
        if (lineVisible > 0f) {
            val lineWidth = startWidthPx
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = rootWidth / 2f - lineWidth / 2f
                        translationY = rootHeight / 2f + with(d) { Oxide.MarkSize.toPx() / 2f + 11.dp.toPx() }
                    },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .width(with(d) { lineWidth.toDp() })
                        .height(1.dp)
                        .graphicsLayer {
                            scaleX = lineVisible.coerceAtLeast(0.12f)
                            alpha = 0.55f * lineVisible
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(Oxide.FgMuted)
                )
            }
        }

        // 黑色幕布：盖住全部内容，最后淡出
        if (curtain > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = curtain }
                    .background(Oxide.Bg)
            )
        }
    }
}

internal data class IntroGeometry(val x: Float, val y: Float, val scale: Float)

/**
 * 把时间轴进度换算成"相对中心的位移"与缩放
 *
 * 四段关键帧各自套用同一条"起步慢、收尾快"的缓动，因此 logo 会真正在中心停住一段时间。
 */
internal fun introGeometry(
    progress: Float,
    centerX: Float,
    centerY: Float,
    destX: Float,
    destY: Float,
    destScale: Float,
): IntroGeometry {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p <= Oxide.Motion.IntroPulseAt -> {
            val t = introEase(p / Oxide.Motion.IntroPulseAt)
            IntroGeometry(0f, 0f, 1f + (Oxide.Motion.IntroPulseScale - 1f) * t)
        }

        p <= Oxide.Motion.IntroSettleAt -> {
            val t = introEase(
                (p - Oxide.Motion.IntroPulseAt) / (Oxide.Motion.IntroSettleAt - Oxide.Motion.IntroPulseAt)
            )
            IntroGeometry(0f, 0f, Oxide.Motion.IntroPulseScale + (1f - Oxide.Motion.IntroPulseScale) * t)
        }

        p <= Oxide.Motion.IntroTravelAt -> IntroGeometry(0f, 0f, 1f)

        else -> {
            val t = introEase((p - Oxide.Motion.IntroTravelAt) / (1f - Oxide.Motion.IntroTravelAt))
            IntroGeometry(
                x = (destX - centerX) * t,
                y = (destY - centerY) * t,
                scale = 1f + (destScale - 1f) * t
            )
        }
    }
}

/**
 * 起步几乎不动、末尾迅速停住的缓动
 *
 * 与参考稿的 `cubic-bezier(.18,.78,.16,1)` 同一性格：前段慢、后段快、总长不变。
 * 用三次多项式近似，代价是零而且单调，不会像弹簧那样过冲。
 */
internal fun introEase(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x) * (1f - 0.16f * (1f - x))
}