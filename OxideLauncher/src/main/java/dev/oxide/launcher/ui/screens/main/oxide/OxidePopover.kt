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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.oxide.launcher.ui.theme.Oxide
import kotlin.math.roundToInt

/**
 * 下拉选择器
 *
 * 用 [Popup] 而不是把面板直接放在调用方的布局里：
 * Popup 走的是独立的窗口层，因此**永远不会被卡片或容器的裁剪吃掉**，
 * 也不会因为祖先用了 `clip` 而少掉圆角外的边。
 *
 * 位置按锚点实测坐标计算，并且会把面板夹在窗口内，
 * 所以贴近屏幕右缘或下缘的按钮弹出的面板也不会跑到屏幕外面。
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
) {
    var expanded by remember { mutableStateOf(false) }

    val current = options.getOrNull(selectedIndex)?.takeIf { it.isNotBlank() } ?: placeholder

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(Oxide.RadiusControl)
                .background(Oxide.BgButton)
                .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
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

    if (expanded) {
        // clippingEnabled = false 是关键：面板走独立窗口，不会被卡片或祖先的 clip 裁掉
        Popup(
            popupPositionProvider = OxideDropdownPositionProvider(),
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
            onDismissRequest = { expanded = false },
        ) {
            OxideDropdownPanel(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = {
                    onSelect(it)
                    expanded = false
                },
                modifier = Modifier,
            )
        }
    }
}

/**
 * 下拉面板的定位
 *
 * 默认落在锚点正下方、与锚点右边缘对齐——这样从右往左读比较自然；
 * 如果下方空间不够就翻到锚点上方，如果右侧会超出窗口就改为左对齐。
 * 两侧各留 6dp 余量，因此贴着屏幕边缘的按钮弹出的面板也不会被切掉。
 */
private class OxideDropdownPositionProvider(
    private val marginPx: Int = 6,
    private val maxHeightPx: Int = 260,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val spaceBelow = windowSize.height - anchorBounds.bottom - marginPx
        val flipUp = spaceBelow < popupContentSize.height && anchorBounds.top > popupContentSize.height

        val x = if (anchorBounds.right + marginPx + popupContentSize.width > windowSize.width) {
            (windowSize.width - marginPx - popupContentSize.width).coerceAtLeast(marginPx)
        } else {
            (anchorBounds.right - popupContentSize.width).coerceAtLeast(marginPx)
        }

        val y = if (flipUp) {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(marginPx)
        } else {
            (anchorBounds.bottom + marginPx)
                .coerceAtMost((windowSize.height - marginPx).coerceAtLeast(marginPx))
        }

        return IntOffset(x, y)
    }
}

@Composable
private fun OxideDropdownPanel(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 120.dp, max = 320.dp)
            .heightIn(max = 260.dp)
            .clip(Oxide.RadiusPopover)
            .background(Oxide.PopoverBg)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPopover)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
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
                        .verticalScroll(rememberScrollState())
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

/** 小图标按钮，抽屉和卡片上的齿轮、关闭都用它 */
@Composable
fun OxideIconButton(
    onClick: () -> Unit,
    glyph: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(Oxide.RadiusControl)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
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
