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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.game.elements

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.theme.Oxide
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 游戏内浮层的共享控件
 *
 * 这一组和 [GameMenuControls] 是同一套语言，因此菜单、悬浮球、日志框与三张面板
 * 读起来是同一种界面：全部由 [Oxide] 的记号（颜色、圆角、字号）直接拼出来，
 * 没有一处 `MaterialTheme`。这些浮层都画在一块**正在运行的游戏**上，
 * Material 的默认配色与它那套 28dp 起步的按钮在这块近黑语言里会明显跳出来。
 *
 * 三条在这个场景下才算数的规则：
 *
 * 1. **不做每帧的工作**。轨道、内存条与进度条各是一个 `Canvas`，不是一串 `Box`；
 *    会跑马灯的长文本一律改成截断——跑马灯每帧都要重新测量一次。
 * 2. **不抢游戏的触摸**。面板根部挂 [consumeTouches]，
 *    每一行自己消费自己收到的事件（子节点先于父节点拿到事件），
 *    因此落在浮层上的手势不会顺手漏给游戏。
 * 3. **状态不靠颜色说话**。单选除了方块还带 `Role.RadioButton`，
 *    按钮带 `Role.Button`；进度与内存的数值本身就是文字。
 */

// ---------------------------------------------------------------------------
// 宿主窗口
// ---------------------------------------------------------------------------

/** 顺着 [ContextWrapper] 链找到承载游戏的那个 Activity */
private fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * 承载游戏的那个窗口有多大（像素）
 *
 * 弹窗是**另一个窗口**：`MATCH_PARENT` 的弹窗窗口铺的是整块显示区，
 * 因此在分屏与自由窗口下，一块只有屏幕十分之一的游戏窗口上方会盖着一张
 * 满屏的弹窗。这份尺寸把上限拉回游戏真正所在的那块窗口，
 * 面板因此始终压在那块游戏画面上，而不是跑到屏幕的另一边去。
 *
 * 读 `decorView` 的实测尺寸；还是 0（没布局过）时返回 [IntSize.Zero]，
 * 调用方退回自己所在窗口的尺寸。
 */
@Composable
internal fun rememberHostWindowSizePx(): IntSize {
    val context = LocalContext.current
    val decor = remember(context) { context.findHostActivity()?.window?.decorView }

    var size by remember(decor) {
        mutableStateOf(decor?.let { IntSize(it.width, it.height) } ?: IntSize.Zero)
    }

    // 挂上时先量一次：布局还没跑过的话，第一次回调可能很久才来
    LaunchedEffect(decor) {
        decor?.let { view ->
            val now = IntSize(view.width, view.height)
            if (now != size) size = now
        }
    }

    DisposableEffect(decor) {
        val view = decor ?: return@DisposableEffect onDispose { }
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val next = IntSize(v.width, v.height)
            if (next != size) size = next
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }

    return size
}

/**
 * 面板尺寸：被**承载游戏的那个窗口**夹住
 *
 * [BoxWithConstraints] 给的是当前这块内容自己的范围（装在弹窗窗口里时是满屏的
 * 弹窗窗口，浮在游戏层里时就是游戏窗口本身），[rememberHostWindowSizePx] 给的是
 * 游戏窗口。两者取小才是真正的天花板。
 *
 * 宿主尺寸还是 0（`decorView` 还没量过）时退回 [BoxWithConstraints] 自己，
 * 也就是和改造前一样按当前窗口算——不会因此把面板算成 0。
 */
@Composable
internal fun BoxWithConstraintsScope.rememberGameOverlayBounds(): GameOverlayBounds {
    val hostPx = rememberHostWindowSizePx()
    val density = LocalDensity.current
    val guiScalePercent = AllSettings.launcherGuiScale.state
    return remember(maxWidth, maxHeight, hostPx, density.density, guiScalePercent) {
        val hostWidth = with(density) { hostPx.width.toDp().value.roundToInt() }
        val hostHeight = with(density) { hostPx.height.toDp().value.roundToInt() }
        gameOverlayBoundsFor(
            windowWidthDp = when {
                hostWidth > 0 -> min(maxWidth.value.roundToInt(), hostWidth)
                else -> maxWidth.value.roundToInt()
            },
            windowHeightDp = when {
                hostHeight > 0 -> min(maxHeight.value.roundToInt(), hostHeight)
                else -> maxHeight.value.roundToInt()
            },
            guiScalePercent = guiScalePercent,
        )
    }
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

/**
 * 一块游戏内浮层面板
 *
 * **不透明**（[Oxide.BgElevated]）：底下压着的是游戏画面，
 * 9% 的 `SurfaceBase` 在这里会直接看穿。圆角取 [Oxide.RadiusPanel]，
 * 描边是 1px 的 [Oxide.Line2]。
 *
 * 宽高都只是上限，面板因此按内容高度摆；超过上限时内容区自己滚
 * （见 [GameOverlayScrollArea]）。面板根部消费全部触摸，
 * 落在它空白处的那一下点击不会同时被游戏看见。
 */
@Composable
internal fun GameOverlayPanel(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = bounds.panelMaxWidth)
            .heightIn(max = bounds.panelMaxHeight)
            .clip(Oxide.RadiusPanel)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel)
            .consumeTouches(),
        content = content,
    )
}

/** 1px 发丝线；两块挨着的浮层靠它分前后 */
@Composable
internal fun GameOverlayHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Oxide.Line)
    )
}

/** 小节标题：6sp 大写宽字距 */
@Composable
internal fun GameOverlaySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Oxide.FgDim,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 说明行：次要正文，两行封顶 */
@Composable
internal fun GameOverlayNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Oxide.FgFaint,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------
// 滚动区
// ---------------------------------------------------------------------------

/**
 * 内容区：高度**先**被 [maxHeight] 夹住，再在里面滚
 *
 * 顺序反过来就是那个 P0 崩溃——一个几百行的日志或一份很长的目录清单
 * 会把面板顶到屏幕外面去。夹完之后内容区恒不小于 [GameOverlayMinContentHeight]，
 * 因此列表至少还能画出一项。
 *
 * [maxHeight] 就是 [GameOverlayBounds.contentMaxHeight]：面板上限减去标题栏与
 * 按钮栏之后剩下的全部。
 */
@Composable
internal fun GameOverlayScrollArea(
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(scrollState),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

/** 按钮语气：主操作是接近纯白的实心，次要操作是描边 */
enum class GameOverlayButtonTone { Primary, Secondary }

/**
 * 一个动作按钮
 *
 * 高度取 [GameOverlayBounds.buttonHeight]（40dp 起，界面放大时一起放大），
 * 比启动器页面上的 28dp 大一截：这是**盖在游戏上的模态面板**，
 * 点不准的代价是"这一下没有送到游戏里"，而这一下本来也不该送到游戏里。
 *
 * 主次由实心/描边承担，两种都有文字，因此不靠位置或颜色区分。
 */
@Composable
internal fun GameOverlayButton(
    text: String,
    onClick: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    tone: GameOverlayButtonTone = GameOverlayButtonTone.Secondary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Oxide.RadiusButton
    val background: Color
    val foreground: Color
    val border: Color?
    when (tone) {
        GameOverlayButtonTone.Primary -> {
            background = if (pressed) Oxide.BgButtonHover else Oxide.BgToggleOn
            foreground = Color(0xFF0B0B0B)
            border = null
        }

        GameOverlayButtonTone.Secondary -> {
            background = if (enabled && pressed) Oxide.BgButtonHover else Oxide.BgButton
            foreground = if (enabled) Oxide.Fg else Oxide.FgFaint
            border = Oxide.Line
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 方块按钮：画的是字形而不是图标，所以必须显式给它朗读文本
 *
 * [selected] 给选中态一块更亮的底——它只是第二重线索，调用点还要自己
 * 在 [description] 里把状态说出来。
 */
@Composable
internal fun GameOverlayIconButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Oxide.RadiusBadge)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusBadge
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = when {
                !enabled -> Oxide.FgFaint
                selected -> Oxide.Fg
                else -> Oxide.FgMuted
            },
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}

/**
 * 图标按钮：矢量图标 + 必给的朗读文本
 *
 * Material 的 `IconButton` 默认 48dp，在游戏上会挤掉整条控制栏；
 * 这里取 [size]，由调用点按游戏窗口给。图标本身**不**作为朗读内容——
 * 一枚垃圾桶读出来只是"图片"，因此 [description] 是必须的，不是可选的。
 */
@Composable
internal fun GameOverlayIconButton(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Oxide.RadiusBadge)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusBadge
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        GameOverlayIcon(
            painter = painter,
            contentDescription = null,
            size = size * 0.45f,
            tint = when {
                !enabled -> Oxide.FgFaint
                selected -> Oxide.Fg
                else -> Oxide.FgMuted
            },
        )
    }
}

/**
 * 一张可点的卡片动作（选择房主/房客、退出房间……）
 *
 * 整块都是热区，因此手指不必瞄准文字本身；不可点时整块一起变暗并停止响应，
 * 而不是只把字变淡。
 */
@Composable
internal fun GameOverlayCardButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconSize: Dp = 14.dp,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(Oxide.RadiusBlock)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            GameOverlayIcon(
                painter = icon,
                contentDescription = null,
                size = iconSize,
                tint = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 紧凑的整行动作：标题一行、说明一行
 *
 * 与 [GameOverlayCardButton] 的区别只是没有边框与图标，用于同一块面板里
 * 成组出现的次级动作（复制邀请码、退出、看日志）。
 */
@Composable
internal fun GameOverlayRowButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconSize: Dp = 13.dp,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(Oxide.RadiusControl)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            GameOverlayIcon(
                painter = icon,
                contentDescription = null,
                size = iconSize,
                tint = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 单选行
 *
 * 整行都是热区；选中态由左侧那块方块承担，同时整行带 `Role.RadioButton`
 * 与 selected，因此朗读时也知道"现在选的是哪一个"，不靠颜色说话。
 */
@Composable
internal fun GameOverlayChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(Oxide.RadiusControl)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameOverlayMark(selected = selected)
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (selected) Oxide.Fg else Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 选中标记：一个方框，选中时中间多一个实心块
 *
 * 形状而不是圆点——方块是这一套语言里"选中"的形状（与 `GameMenuOptionRow` 一致），
 * 而且它是**填充**与**空心**的区别，不只是颜色深浅。
 */
@Composable
private fun GameOverlayMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(Oxide.RadiusBadge)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.FgMuted else Oxide.Line2),
                Oxide.RadiusBadge,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 标题栏与底栏
// ---------------------------------------------------------------------------

/**
 * 面板标题栏：标题 + 关闭
 *
 * [onClose] 为 null 时不画关闭按钮——那表示这块面板不是靠关闭按钮收回的
 * （例如它由别人的返回键收回），而不是"忘了加"。
 */
@Composable
internal fun GameOverlayHeader(
    title: String,
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    closeDescription: String? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = bounds.padding,
                end = bounds.padding / 2f,
                top = bounds.rowGap,
                bottom = bounds.rowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Oxide.Fg,
            fontSize = Oxide.Type.DrawerTitle.fontSize,
            lineHeight = Oxide.Type.DrawerTitle.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null && closeDescription != null) {
            Spacer(Modifier.width(bounds.rowGap))
            GameOverlayIconButton(
                glyph = "✕",
                description = closeDescription,
                onClick = onClose,
                size = bounds.buttonHeight,
            )
        }
    }
}

/** 底栏：按钮从右往左排，与启动器其余面板一致 */
@Composable
internal fun GameOverlayFooter(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = bounds.padding,
                end = bounds.padding,
                top = bounds.rowGap,
                bottom = bounds.rowGap,
            ),
        horizontalArrangement = Arrangement.spacedBy(bounds.rowGap, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 图标与指示
// ---------------------------------------------------------------------------

/**
 * 一个着色过的矢量图标
 *
 * 用 [Image] 而不是 Material 的 `Icon`：后者在 [contentDescription] 为 null 时
 * 仍然会往语义树里塞一个节点，而这里有一半图标是纯装饰。
 */
@Composable
internal fun GameOverlayIcon(
    painter: Painter,
    contentDescription: String?,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * 细进度条
 *
 * [progress] 为 null 表示**进度不可知**（扫描中、连接中）：这时只画一条空槽
 * 加起点的一小段标记，刻意不做循环动画——无尽动画既不提供新信息，
 * 又会让这块面板永远停不下来。调用点必须同时给一句文字说明正在做什么。
 *
 * 一个 `Canvas` 画完，因此每秒刷新与展开动画都不会重新排版。
 */
@Composable
internal fun GameOverlayProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Oxide.Line,
            cornerRadius = CornerRadius(radius, radius),
        )
        val fraction = (progress ?: 0f).coerceIn(0f, 1f)
        if (fraction > 0f) {
            drawRoundRect(
                color = Oxide.FgMuted,
                size = size.copy(width = size.width * fraction),
                cornerRadius = CornerRadius(radius, radius),
            )
        } else if (progress == null) {
            // 不可知：起点放一小段，明确表示"有东西在动，但不知道有多少"
            drawRoundRect(
                color = Oxide.FgGhost,
                size = size.copy(width = size.width / 3f),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

/** 不知道进度时的那句话，与启动器其余对话框同一句 */
@Composable
internal fun GameOverlayWorkingText(): String = stringResource(R.string.oxide_dlg_working)

/**
 * 一层遮罩 + 居中内容
 *
 * 遮罩与面板是**兄弟**而不是父子：父子时点面板外的空白会落到面板自己身上，
 * 而兄弟关系下后面绘制的面板先被命中，空白自然落到遮罩上。
 *
 * [dismissByDialog] 为 false 时遮罩不可点：任务进行中、上传中这类面板
 * 不能让用户随手点掉。
 */
@Composable
internal fun GameOverlayScrimLayer(
    dismissByDialog: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Oxide.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissByDialog,
                    onClick = onDismissRequest,
                )
        )
        content()
    }
}