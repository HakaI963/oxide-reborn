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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 开场动画
 *
 * 界面从第一帧就已经就位并渲染在下面，只有一块黑色幕布盖住它，
 * 幕布之上是**唯一一个 logo**：它从屏幕中心出发，落在侧栏品牌槽的中心，然后就留在那里。
 * 落点由侧栏实测上报，不是写死的坐标，所以换屏幕尺寸也不会跑偏。
 *
 * 三件事必须同时成立，少一件就只会看到一块黑幕，然后界面凭空出现、logo 从头到尾没出现：
 * 1. logo 必须画在幕布**之上**——参考稿里 `#oxide` 的 z-index 高于 `#introCurtain`，
 *    幕布只负责挡住界面，不挡 logo；
 * 2. 缩放必须绕整屏中心，logo 才会沿着"屏幕中心 → 槽心"那条直线走；
 * 3. 旅程走完**不能**无条件删掉 overlay。要么 logo 继续停在槽位上，
 *    要么在同一帧交给侧栏常驻渲染（[OxideBrandSlotLogo] 靠 [OxideBrandSlotState.landed] 判断）。
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
    val frame = remember {
        IntroFrame(
            progress = if (skipAnimation) 1f else 0f,
            curtain = if (skipAnimation) 0f else 1f,
        )
    }

    LaunchedEffect(Unit) {
        if (skipAnimation) return@LaunchedEffect
        // 幕布的淡出比旅程长 80ms（参考稿 .78s 是从 2450ms 起算的），
        // 让它自己跑完。停在旅程末尾会把幕布从 0.10 直接掐到 0，闪一下
        val totalMs = maxOf(
            Oxide.Motion.IntroJourneyMs,
            Oxide.Motion.IntroCurtainRevealMs + Oxide.Motion.IntroCurtainFadeMs,
        )
        // 先空等一帧，保证"纯黑 + 居中 logo"这一帧真的被画出去，再开始移动
        val started = withFrameNanos { it }
        while (true) {
            val elapsed = (withFrameNanos { it } - started) / 1_000_000f
            if (elapsed >= totalMs) break
            // 旅程走完之后进度停在 1，logo 就这么停在槽位上等幕布淡完
            frame.progress = (elapsed / Oxide.Motion.IntroJourneyMs).coerceIn(0f, 1f)

            val curtainT =
                ((elapsed - Oxide.Motion.IntroCurtainRevealMs) / Oxide.Motion.IntroCurtainFadeMs)
                    .coerceIn(0f, 1f)
            frame.curtain = 1f - curtainT

            frame.lineVisible = when {
                elapsed < Oxide.Motion.IntroLineInDelayMs -> 0f
                elapsed < Oxide.Motion.IntroLineInDelayMs + Oxide.Motion.IntroLineInMs ->
                    (elapsed - Oxide.Motion.IntroLineInDelayMs) / Oxide.Motion.IntroLineInMs

                elapsed < Oxide.Motion.IntroLineOutDelayMs -> 1f
                elapsed < Oxide.Motion.IntroLineOutDelayMs + Oxide.Motion.IntroLineOutMs ->
                    1f - (elapsed - Oxide.Motion.IntroLineOutDelayMs) / Oxide.Motion.IntroLineOutMs

                else -> 0f
            }
        }
        frame.progress = 1f
        frame.curtain = 0f
        frame.lineVisible = 0f
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        // overlay 单独一个可重组作用域：逐帧变化的只有它，主界面不会每帧被重组一次
        IntroOverlay(frame = frame, slot = slot, maxSlotWidth = maxSlotWidth)
    }
}

/**
 * 逐帧状态
 *
 * 单独放在一个对象里，是为了让"帧变化"只失效 [IntroOverlay] 的重组作用域。
 * 直接在 [OxideIntro] 里读这些字段的话，每一帧都会连带重组整个主界面。
 */
@Stable
private class IntroFrame(
    progress: Float = 0f,
    curtain: Float = 1f,
    lineVisible: Float = 0f,
) {
    var progress by mutableFloatStateOf(progress)
    var curtain by mutableFloatStateOf(curtain)
    var lineVisible by mutableFloatStateOf(lineVisible)

    /** logo 在自然尺寸下的真实宽度（像素）。落位缩放由它算出来，不去猜字标有多宽 */
    var logoWidth: Float by mutableFloatStateOf(0f)
}

/**
 * 幕布 + 飞行的 logo + 下划线
 *
 * 绘制顺序就是叠放顺序：界面 → 幕布 → logo → 下划线。
 */
@Composable
private fun IntroOverlay(
    frame: IntroFrame,
    slot: OxideBrandSlotState,
    maxSlotWidth: Dp,
) {
    val density = LocalDensity.current
    var rootWidth by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableFloatStateOf(0f) }

    val slotWidthPx = slot.width
    val logoWidthPx = frame.logoWidth

    // 参考稿：落地宽度取实测槽宽与 122px 上限的较小值，
    // 再按 clamp(落地宽度 / logo 原始宽度, .28, .42) 得到缩放。
    // logo 原始宽度是实测的，因此换字号、换字体都不用改这里。
    val landingScale = with(density) {
        val target = if (slotWidthPx > 0f) minOf(slotWidthPx.toDp(), maxSlotWidth) else maxSlotWidth
        if (logoWidthPx > 0f) {
            (target.toPx() / logoWidthPx)
                .coerceIn(Oxide.Motion.IntroScaleMin, Oxide.Motion.IntroScaleMax)
        } else {
            Oxide.Motion.IntroScaleMax
        }
    }

    // 落位缩放一量到就共享给侧栏，但**不能**因此就宣布落位：
    // 宣布落位的时刻必须是旅程走完的那一帧，否则侧栏会提前接管，动画就没了
    SideEffect {
        if (logoWidthPx <= 0f || slotWidthPx <= 0f) return@SideEffect
        if (slot.landingScale != landingScale) slot.shareLandingScale(landingScale)
        // 交接必须等到幕布彻底淡完：侧栏的 logo 画在幕布之下，
        // 早交出会被残存的幕布压暗，然后随着幕布消失突然亮一下
        if (frame.progress >= 1f && frame.curtain <= 0f && !slot.landed) slot.markLanded()
    }

    // 旅程走完就把 logo 交出去：侧栏从这一帧起常驻渲染它（见 [OxideBrandSlotLogo]）。
    // 交接前侧栏是不画 logo 的，所以任何时刻屏幕上只有一个
    val handedOff = slot.landed

    // 侧栏还没上报时退回一个保守的默认位置，动画照常结束
    val dest = slot.centerInRoot
    val destX = dest?.x ?: with(density) { Oxide.SidebarWidth.toPx() / 2f }
    val destY = dest?.y ?: with(density) { Oxide.BrandSlotHeight.toPx() / 2f }

    val journey = introGeometry(
        progress = frame.progress,
        centerX = rootWidth / 2f,
        centerY = rootHeight / 2f,
        destX = destX,
        destY = destY,
        destScale = landingScale,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                rootWidth = it.width.toFloat()
                rootHeight = it.height.toFloat()
            }
    ) {
        // ---- 黑色幕布：只盖界面，logo 压在它上面 ----
        val curtain = frame.curtain
        if (curtain > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = curtain }
                    .background(Oxide.Bg)
            )
        }

        // ---- 唯一的那个 logo ----
        if (!handedOff) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = journey.x
                        translationY = journey.y
                        scaleX = journey.scale
                        scaleY = journey.scale
                        // 绕整屏中心缩放：绕左上角缩放会让 logo 偏离中心→槽心那条直线
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                contentAlignment = Alignment.Center
            ) {
                OxideLogo(
                    modifier = Modifier.onSizeChanged { frame.logoWidth = it.width.toFloat() }
                )
            }
        }

        // ---- 下划线：贴在字标底部，扫入后向左收回 ----
        val lineVisible = frame.lineVisible
        if (lineVisible > 0f && logoWidthPx > 0f) {
            val markPx = with(density) { Oxide.MarkSize.toPx() }
            val gapPx = with(density) { OxideLogoGap.toPx() }
            val wordHeightPx = with(density) { OxideLogoWordmark.value.dp.toPx() }
            // 参考稿里下划线只横跨字标，而字标中心比整个 logo 中心偏右 (图形+间距)/2
            val lineWidth = (logoWidthPx - markPx - gapPx).coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = rootWidth / 2f + journey.x + (markPx + gapPx) / 2f -
                            lineWidth / 2f
                        translationY = rootHeight / 2f + journey.y + wordHeightPx / 2f +
                            with(density) { 11.dp.toPx() }
                    },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { lineWidth.toDp() })
                        .height(1.dp)
                        .graphicsLayer {
                            scaleX = lineVisible.coerceAtLeast(0.12f)
                            alpha = 0.55f * lineVisible
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(Oxide.FgMuted)
                )
            }
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
 *
 * 多项式本身在 t=0 处取值 `TAIL`，所以整段曲线平移到 f(0)=0、f(1)=1 之后再用。
 * 不做这一步的话，logo 在第一帧就已经是 1.006 倍大，参考稿的 0% 是精确的 1。
 */
internal fun introEase(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val one = 1f - x
    return (1f - one * one * one * (1f - EASE_TAIL * one) - EASE_TAIL) / (1f - EASE_TAIL)
}

/** 三次多项式在 t=0 处的值，也就是需要被平移掉的那一段 */
private const val EASE_TAIL = 0.16f