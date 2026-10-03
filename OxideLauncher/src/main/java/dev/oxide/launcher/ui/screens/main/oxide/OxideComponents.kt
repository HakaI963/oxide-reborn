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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * Oxide 的共享控件库
 *
 * 页面不允许自己拼按钮、卡片或开关——统一用这里的实现，
 * 这样四个页面在圆角、边框、字号、按压反馈上完全一致，
 * 也避免每个页面各写一份渐变和动画。
 */

/** 卡片 / 面板的底板：暗色渐变 + 1px 低透明度白边 */
@Composable
fun OxideSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = Oxide.RadiusCard,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(if (selected) Oxide.BgTabActive else Oxide.SurfaceBase)
        .background(Oxide.SurfaceBrush)
        .border(
            BorderStroke(
                1.dp,
                if (selected) Oxide.Line2 else Oxide.Line
            ),
            shape
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Column(modifier = base.padding(contentPadding), content = content)
}

/** 小节标题：6px 大写宽字距的弱化文字 */
@Composable
fun OxideSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** 页面大标题 */
@Composable
fun OxidePageTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Oxide.Fg,
            style = Oxide.Type.PageTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
    }
}

/**
 * 按钮
 *
 * 三种语气：主操作是接近纯白的实心，次要操作是描边，第三种是纯文字。
 * 高度只有 28dp，比 Material 的按钮小很多，这是参考稿的密度。
 */
@Composable
fun OxideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    /** 主 / 次 / 文字 */
    tone: OxideButtonTone = OxideButtonTone.Secondary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val shape = Oxide.RadiusButton
    val bg: Color
    val fg: Color
    val border: Color?
    when (tone) {
        OxideButtonTone.Primary -> {
            bg = if (pressed) Color(0xFFD2D2D2) else Oxide.BgToggleOn
            fg = Color(0xFF0B0B0B)
            border = null
        }

        OxideButtonTone.Secondary -> {
            bg = if (pressed) Oxide.BgButtonHover else Oxide.BgButton
            fg = if (enabled) Oxide.Fg else Oxide.FgFaint
            border = Oxide.Line
        }

        OxideButtonTone.Ghost -> {
            bg = Color.Transparent
            fg = if (enabled) Oxide.FgMuted else Oxide.FgFaint
            border = null
        }
    }

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(if (tone == OxideButtonTone.Ghost) Color.Transparent else bg)
            .then(
                if (border != null) Modifier.border(BorderStroke(1.dp, border), shape)
                else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leadingIcon?.let {
            androidx.compose.material3.Icon(
                imageVector = it,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            color = fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

enum class OxideButtonTone { Primary, Secondary, Ghost }

/** 紧凑开关 */
@Composable
fun OxideToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(180),
        label = "oxideToggle"
    )
    val width = 28.dp
    val height = 15.dp
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        lerpColor(Oxide.BgToggleOff, Oxide.BgToggleOn, track),
                        lerpColor(Oxide.BgToggleOff, Oxide.BgToggleOn, track),
                    )
                )
            )
            .border(BorderStroke(1.dp, Oxide.Line), RoundedCornerShape(50))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(11.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) Color(0xFF1A1A1A) else Oxide.FgFaint)
        )
    }
}

/** 状态徽章 */
@Composable
fun OxideBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: OxideBadgeTone = OxideBadgeTone.Neutral,
) {
    val (fg, bg) = when (tone) {
        OxideBadgeTone.Neutral -> Oxide.FgDim to Color(0x14FFFFFF)
        OxideBadgeTone.Active -> Color(0xFFEDEDED) to Color(0x2EFFFFFF)
        OxideBadgeTone.Warn -> Oxide.FgMuted to Color(0x1AFFFFFF)
    }
    Text(
        text = text,
        color = fg,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(Oxide.RadiusBadge)
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

enum class OxideBadgeTone { Neutral, Active, Warn }

/**
 * 一行"标签 + 值"
 *
 * 设置页和实例配置抽屉的主力行。点击整行都会回调，方便接弹出选择器。
 */
@Composable
fun OxideSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            hint?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        value?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = it,
                color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp),
                textAlign = TextAlign.End,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** 空状态：图标 + 一句话，不伪造任何数据 */
@Composable
fun OxideEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Title.fontSize,
            lineHeight = Oxide.Type.Title.lineHeight,
            textAlign = TextAlign.Center,
        )
        detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(5.dp))
            Text(
                text = it,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                textAlign = TextAlign.Center,
            )
        }
        action?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}

/** 加载中：一行低调的进度，不使用会一直转圈的巨型指示器 */
@Composable
fun OxideLoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
        )
    }
}

/** 分区容器：标题 + 内容 */
@Composable
fun OxideSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
        content()
    }
}

/** 进度条：细，只在有确定进度时使用 */
@Composable
fun OxideProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(50))
            .background(Oxide.Line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(Oxide.FgMuted)
        )
    }
}

/** 内容渐进的错峰进场：延迟逐级递增，位移只有 3dp */
@Composable
fun OxideReveal(
    visible: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = Oxide.Motion.RevealMs,
                delayMillis = Oxide.Motion.RevealDelayFirstMs + index * Oxide.Motion.RevealDelayStepMs
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = Oxide.Motion.RevealMs,
                delayMillis = Oxide.Motion.RevealDelayFirstMs + index * Oxide.Motion.RevealDelayStepMs
            )
        ) { full -> (full * 3f / 1000f).toInt().coerceAtLeast(1) },
    ) {
        content()
    }
}

internal fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

/** 供页面使用的统一竖向留白 */
@Composable
fun OxidePageColumn(
    modifier: Modifier = Modifier,
    metrics: OxideMetrics,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = metrics.pagePaddingH,
                end = metrics.pagePaddingH,
                top = metrics.pagePaddingV,
                bottom = Oxide.PagePaddingB
            ),
        content = content,
    )
}
