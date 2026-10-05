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

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.oxide.launcher.ui.theme.Oxide
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 下拉面板的几何：全部由这里定义，面板与锚点、窗口之间的关系只有一套规则
// ---------------------------------------------------------------------------

/** 面板与控件之间、以及面板与窗口边缘之间的统一余量 */
private val OxidePopoverMargin = 6.dp

/** 面板宽度上限：再宽就会在窄窗口上被切掉 */
private val OxidePopoverPanelWidthMax = 320.dp

/** 面板宽度下限 */
private val OxidePopoverPanelWidthMin = 120.dp

/** 面板高度上限：超出的部分在面板内部滚动，不会把面板撑过屏幕 */
private val OxidePopoverPanelHeightMax = 260.dp

/** 单个选项的高度，[OxideDropdownPanel] 里每一行都是这个高度 */
private val OxidePopoverItemHeight = 26.dp

/** 面板上下内边距 */
private val OxidePopoverPanelPadding = 4.dp

/** 面板描边宽度：[Modifier.border] 会占掉布局尺寸，算面板高度时必须算进去 */
private val OxidePopoverBorderWidth = 1.dp

/** 面板进场时从对齐的那个角缩放到 1，避免"啪"地一下出现 */
private const val OxidePopoverScaleFrom = 0.94f

/**
 * 面板与控件的水平对齐方式
 *
 * 只有两种，且各自成对使用：面板的一条边缘与控件的同一条边缘对齐。
 * 本启动器里所有下拉都是左对齐控件，所以默认是 [START]。
 */
enum class OxidePopoverAlignment {
    /** 面板左边缘与控件左边缘对齐 */
    START,

    /** 面板右边缘与控件右边缘对齐 */
    END
}

/**
 * 下拉面板的位置计算
 *
 * 纯函数：只认「锚点矩形 + 窗口尺寸 + 面板尺寸 + 余量」，不碰任何 Compose 状态，
 * 因此密度、屏幕尺寸、窗口是否被切分都只体现为传入数值的不同，行为完全一致。
 *
 * 规则只有三条：
 *  1. 水平方向按 [alignment] 与控件的同侧边缘对齐；
 *  2. 垂直方向默认落在控件下方余量 [margin] 处，下方不够且上方更宽裕时翻到上方；
 *  3. 最后一律夹进窗口，四周各留 [margin]，所以贴着任意一条屏幕边的控件
 *     弹出的面板也一定是完整可见的。
 *
 * 面板尺寸允许是 0（首帧还没量出来）：此时面板不占地方，位置退化成
 * 「贴着控件的左边缘、落在控件下方」，而不是像以前那样退化成控件的右边缘。
 */
internal fun calculateOxidePopoverPosition(
    anchor: IntRect,
    window: IntSize,
    panel: IntSize,
    margin: Int,
    alignment: OxidePopoverAlignment = OxidePopoverAlignment.START,
): IntOffset {
    val safeMargin = margin.coerceAtLeast(0)
    // 面板不可能比可用区域还大，先夹住，否则后面的边界会反转
    val usableWidth = (window.width - 2 * safeMargin).coerceAtLeast(0)
    val usableHeight = (window.height - 2 * safeMargin).coerceAtLeast(0)
    val panelWidth = panel.width.coerceIn(0, usableWidth)
    val panelHeight = panel.height.coerceIn(0, usableHeight)

    // 1. 水平：与控件同侧边缘对齐
    val preferredX = when (alignment) {
        OxidePopoverAlignment.START -> anchor.left
        OxidePopoverAlignment.END -> anchor.right - panelWidth
    }
    // 2. 夹在窗口内（下界永远是 margin，面板因此必然完整可见）
    val maxX = safeMargin + usableWidth - panelWidth
    val x = preferredX.coerceIn(safeMargin, maxX)

    // 3. 垂直：默认下方，下方不够且上方更宽裕时翻上去
    val spaceBelow = window.height - anchor.bottom - safeMargin
    val spaceAbove = anchor.top - safeMargin
    val flipAbove = panelHeight > spaceBelow && spaceAbove > spaceBelow
    val preferredY = if (flipAbove) {
        anchor.top - safeMargin - panelHeight
    } else {
        anchor.bottom + safeMargin
    }
    val maxY = safeMargin + usableHeight - panelHeight
    val y = preferredY.coerceIn(safeMargin, maxY)

    return IntOffset(x, y)
}

/** 控件实测矩形，窗口像素坐标 */
private fun LayoutCoordinates.toAnchorIntRect(): IntRect {
    val origin = positionInWindow()
    val left = origin.x.roundToInt()
    val top = origin.y.roundToInt()
    return IntRect(
        left = left,
        top = top,
        right = left + size.width,
        bottom = top + size.height
    )
}

/**
 * 下拉选择器
 *
 * 用 [Popup] 而不是把面板直接放在调用方的布局里：
 * Popup 走的是独立的窗口层，因此**永远不会被卡片或容器的裁剪吃掉**，
 * 也不会因为祖先用了 `clip` 而少掉圆角外的边。
 *
 * 位置**不依赖 Popup 首帧量出来的内容尺寸**（那时候它是 0，用它算会让面板贴在控件的
 * 右边缘上）：锚点用 [onGloballyPositioned] 实测的窗口坐标，面板尺寸由选项条数直接算出来，
 * 窗口尺寸用框架给的 `windowSize`——也就是面板偏移量所处的那个坐标系。三者都确定，
 * 所以第一次弹出的位置就是最终位置，不会先歪一下再跳回来。
 */
@Composable
fun OxideDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    alignment: OxidePopoverAlignment = OxidePopoverAlignment.START,
) {
    var expanded by remember { mutableStateOf(false) }
    // mounted 管 Popup 的生死，expanded 管面板的显隐：
    // 收起时先跑完退出动画再摘掉 Popup，否则退场动画根本没机会播
    var mounted by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }

    val current = options.getOrNull(selectedIndex)?.takeIf { it.isNotBlank() } ?: placeholder

    LaunchedEffect(expanded) {
        if (expanded) {
            mounted = true
        } else {
            delay(Oxide.Motion.PopoverMs.toLong())
            mounted = false
        }
    }
    // 控件被禁用时选项来源可能已经变了，继续挂着旧面板只会误导
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val marginPx = remember(density) { with(density) { OxidePopoverMargin.roundToPx() } }

    // 面板永远不会高过屏幕：上限同时受"设计上最多这么高"和"窗口还剩多少"约束
    val panelMaxHeight = remember(configuration.screenHeightDp, density) {
        with(density) {
            minOf(
                OxidePopoverPanelHeightMax,
                (configuration.screenHeightDp - OxidePopoverMargin.value * 2f).dp.coerceAtLeast(0.dp)
            )
        }
    }

    // 面板至少和控件一样宽：这样它看起来是控件伸出来的一部分，而不是飘在旁边的一块
    val panelMinWidth = remember(anchorBounds.width, density) {
        with(density) {
            max(
                OxidePopoverPanelWidthMin,
                minOf(anchorBounds.width.toDp(), OxidePopoverPanelWidthMax)
            )
        }
    }

    // 面板尺寸自己算，不去猜 Popup 量出来多少：
    // 宽度由 widthIn 的上限与选项行的 fillMaxWidth 决定，高度由选项条数决定，都是确定的
    val declaredPanelSize = remember(options.size, density, panelMaxHeight, panelMinWidth) {
        with(density) {
            val contentHeight = (
                options.size * OxidePopoverItemHeight.value +
                    OxidePopoverPanelPadding.value * 2f +
                    OxidePopoverBorderWidth.value * 2f
                ).dp
            IntSize(
                // 面板不会比 max 窄（选项行是 fillMaxWidth），
                // 也不会比 min 宽：两者相同时面板恰好与控件等宽
                width = maxOf(panelMinWidth, OxidePopoverPanelWidthMax)
                    .coerceAtMost(OxidePopoverPanelWidthMax).roundToPx(),
                height = minOf(contentHeight, panelMaxHeight).roundToPx()
            )
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { anchorBounds = it.toAnchorIntRect() }
                .fillMaxWidth()
                .height(28.dp)
                .clip(Oxide.RadiusControl)
                .background(Oxide.BgButton)
                .border(BorderStroke(OxidePopoverBorderWidth, Oxide.Line), Oxide.RadiusControl)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = current,
                color = if (current.isBlank()) Oxide.FgFaint else Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OxideChevron()
        }
    }

    if (mounted) {
        // clippingEnabled = false 是关键：面板走独立窗口，不会被卡片或祖先的 clip 裁掉
        Popup(
            popupPositionProvider = remember(anchorBounds, declaredPanelSize, marginPx, alignment) {
                OxideDropdownPositionProvider(
                    anchorBoundsOf = { anchorBounds },
                    panelSize = declaredPanelSize,
                    marginPx = marginPx,
                    alignment = alignment
                )
            },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
            onDismissRequest = { expanded = false },
        ) {
            // 变换慢一点、淡入快一点：位移与缩放 280ms，淡入 180ms
            val scale by animateFloatAsState(
                targetValue = if (expanded) 1f else OxidePopoverScaleFrom,
                animationSpec = tween(Oxide.Motion.PopoverMs, easing = FastOutSlowInEasing),
                label = "oxidePopoverScale"
            )
            val fade by animateFloatAsState(
                targetValue = if (expanded) 1f else 0f,
                animationSpec = tween(Oxide.Motion.PopoverFadeMs, easing = FastOutSlowInEasing),
                label = "oxidePopoverFade"
            )
            // 从与控件对齐的那个角长出来：左对齐就从左上角，右对齐就从右上角
            val origin = if (alignment == OxidePopoverAlignment.START) {
                TransformOrigin(0f, 0f)
            } else {
                TransformOrigin(1f, 0f)
            }

            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = fade
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = origin
                }
            ) {
                OxideDropdownPanel(
                    options = options,
                    selectedIndex = selectedIndex,
                    minWidth = panelMinWidth,
                    maxHeight = panelMaxHeight,
                    onSelect = {
                        onSelect(it)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * 下拉面板的定位
 *
 * 框架传进来的 [anchorBounds] 与 [popupContentSize] 都**不能直接拿来算**：
 * 内容尺寸在首帧还没量出来，是 0，而旧实现正是把它减进了 x，
 * 于是 x 塌成了控件的右边缘——面板看起来就从控件右边飞了出去。
 *
 * 所以这里只用框架给的 [windowSize]（面板偏移量的坐标系），锚点用 [anchorBounds]
 * 实测的窗口坐标，面板尺寸用控件自己声明的确定值。
 */
private class OxideDropdownPositionProvider(
    private val anchorBoundsOf: () -> IntRect,
    private val panelSize: IntSize,
    private val marginPx: Int,
    private val alignment: OxidePopoverAlignment,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = calculateOxidePopoverPosition(
        anchor = anchorBoundsOf(),
        window = windowSize,
        panel = panelSize,
        margin = marginPx,
        alignment = alignment
    )
}

@Composable
private fun OxideDropdownPanel(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    minWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = minWidth, max = OxidePopoverPanelWidthMax)
            .heightIn(max = maxHeight)
            .clip(Oxide.RadiusPopover)
            .background(Oxide.PopoverBg)
            .border(BorderStroke(OxidePopoverBorderWidth, Oxide.Line2), Oxide.RadiusPopover)
            .verticalScroll(rememberScrollState())
            .padding(vertical = OxidePopoverPanelPadding),
    ) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OxidePopoverItemHeight)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option,
                    color = if (index == selectedIndex) Oxide.Fg else Oxide.FgMuted,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (index == selectedIndex) {
                    Text(
                        text = "\u2022",
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun OxideChevron() {
    Text(
        text = "▾",
        color = Oxide.FgFaint,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
    )
}

/**
 * 右侧抽屉
 *
 * 覆盖在当前页之上，遮罩是半透明黑，抽屉从右侧滑入并超出屏幕右缘一小段，
 * 与参考稿一致。高度铺满，宽��按 [metrics] 自适应。
 */
@Composable
fun OxideDrawerHost(
    visible: Boolean,
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 抽屉里顶部的标签页 */
    title: String,
    onClose: () -> Unit = onDismiss,
    /**
     * 内容超出时是否由抽屉自己纵向滚动
     *
     * 默认 true，所以绝大多数抽屉一行都不用改。传 false 的场合只有一个：内容自带滚动
     * 容器（`LazyColumn` 之类）。`verticalScroll` 会把子内容的最大高度变成无穷大，
     * 而 `LazyColumn` 拿到无穷大的最大高度会在测量期抛
     * `IllegalStateException: Vertically scrollable component was measured with an infinity
     * maximum height constraints` —— 与 v1.6.0 修掉的 Home 环境卡片同一个成因。
     */
    scrollable: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (!visible) return

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩：点它关闭
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(Oxide.Motion.ScrimMs)),
            exit = fadeOut(tween(Oxide.Motion.ScrimMs)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Oxide.DrawerScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // 抽屉本体
        AnimatedVisibility(
            visible = true,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(Oxide.Motion.ScrimMs)) +
                androidx.compose.animation.slideInHorizontally(
                    animationSpec = tween(Oxide.Motion.DrawerMs),
                    initialOffsetX = { full -> full }
                ),
            exit = fadeOut(tween(Oxide.Motion.ScrimMs)) +
                androidx.compose.animation.slideOutHorizontally(
                    animationSpec = tween(Oxide.Motion.DrawerMs),
                    targetOffsetX = { full -> full }
                ),
        ) {
            Column(
                modifier = Modifier
                    .width(metrics.drawerWidth)
                    .fillMaxHeight()
                    .padding(
                        top = Oxide.Motion.DrawerOverhang.dp,
                        bottom = Oxide.Motion.DrawerOverhang.dp,
                        end = Oxide.Motion.DrawerOverhang.dp
                    )
                    .clip(Oxide.RadiusDrawer)
                    .background(Oxide.DrawerBg)
                    .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusDrawer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
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
                    OxideIconButton(onClick = onClose, glyph = "✕")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Oxide.Line)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // scrollable = false 时纵向空间原样交给内容：内容里的
                        // LazyColumn 自己滚动，而且它需要的是一个**有限**的最大高度。
                        .then(
                            if (scrollable) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    content = content,
                )
            }
        }
    }
}

/** 抽屉里的标签页 */
@Composable
fun OxideDrawerTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(Oxide.RadiusSmall)
                    .background(if (selected) Oxide.BgTabActive else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab,
                    color = if (selected) Oxide.Fg else Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 小图标按钮，抽屉和卡片上的齿轮、关闭都用它
 *
 * [contentDescription] 不是可选项的摆设：图标按钮本身没有可见文字，
 * 不给无障碍标签，读屏只会念出一个没有意义的符号。
 */
@Composable
fun OxideIconButton(
    onClick: () -> Unit,
    glyph: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .width(size)
            .height(size)
            .clip(Oxide.RadiusControl)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (enabled) Oxide.FgMuted else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
        )
    }
}
