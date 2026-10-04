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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R

/**
 * 子窗口外壳
 *
 * 之前每个二级表面都是各自 `fillMaxSize()` 的一整页，而且**没有一个可见的关闭按钮**——
 * 只能靠系统返回键或者点外面关掉。底下一页于是原样透上来，读起来像"旧页面从弹窗后面漏出来"。
 *
 * 这里把三件事收进一个壳，二级表面统一走它：
 *
 * 1. **不透明**：底幕 [Oxide.PanelBackdrop]，面板本体 [Oxide.BgElevated]（全不透明）。
 *    刻意不用 `Oxide.SurfaceBase`——那个 token 只有 9% alpha，是给**卡片**用的；
 *    卡片浮在不透明页面上没问题，拿来做面板就正好变成"能看穿"。
 * 2. **有界、居中、自适应**：宽高上限都由真实窗口尺寸算出来，不写死机型。
 *    超出就在**面板内部**滚动——顺序不能反：先把高度夹住，再在里面滚。
 *    反过来就是 v1.5.0 那个 P0 崩溃（滚动容器拿到无穷大的 maxHeight）。
 * 3. **有真的关闭与返回**：标题栏右侧固定 ✕，有上一层时左侧再来 ←。
 *    不再指望用户知道要按返回键。
 */

/** 子窗口最大宽度占可用宽度的比例，再夹进 [OxideSubWindowMinWidth] 与 [maxWidth] 之间 */
internal const val OxideSubWindowWidthFraction = 0.82f
internal val OxideSubWindowMinWidth = 300.dp

/** 子窗口最大高度占可用高度的比例 */
internal const val OxideSubWindowHeightFraction = 0.86f

/** 子窗口离窗口边缘的留白 */
val OxideSubWindowMargin: Dp = 14.dp

/** 子窗口标题栏与内容的内边距 */
val OxideSubWindowContentPadding: Dp = 16.dp

/**
 * @param title 标题栏文字
 * @param onClose 关闭。右上角 ✕ 永远存在
 * @param onBack 有上一层时才显示的返回箭头；为 null 时不显示
 * @param trailing 标题栏右侧的额外内容，例如计数或状态
 * @param maxPanelWidth 面板宽度上限；实际宽度取可用宽度按比例、再夹进区间、再夹进这个上限
 * @param scrollable 内容超出时是否在面板内部滚动
 */
@Composable
fun OxideSubWindow(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    maxPanelWidth: Dp = 620.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Oxide.PanelBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        // 宽度：可用宽度按比例 -> 夹进区间 -> 不超过调用方给的上面板宽度上限
        val width = (maxWidth * OxideSubWindowWidthFraction)
            .coerceIn(OxideSubWindowMinWidth, maxPanelWidth)
        // 高度：可用高度按比例，减去两侧留白
        val heightCap = (maxHeight * OxideSubWindowHeightFraction) - OxideSubWindowMargin * 2

        Column(
            modifier = Modifier
                .widthIn(max = width)
                .heightIn(max = heightCap.coerceAtLeast(1.dp))
                .clip(Oxide.RadiusDrawer)
                .background(Oxide.BgElevated)
                .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusDrawer),
        ) {
            OxideSubWindowTitleBar(
                title = title,
                onClose = onClose,
                onBack = onBack,
                trailing = trailing,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Oxide.Line)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollable) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
                    .padding(OxideSubWindowContentPadding),
                content = content,
            )
        }
    }
}

/** 标题栏：可选返回、标题、可选尾部、固定关闭 */
@Composable
private fun OxideSubWindowTitleBar(
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) {
            OxideIconButton(
                onClick = onBack,
                glyph = "←",
                contentDescription = stringResource(R.string.generic_back),
            )
        }
        Text(
            text = title,
            color = Oxide.Fg,
            fontSize = Oxide.Type.DrawerTitle.fontSize,
            lineHeight = Oxide.Type.DrawerTitle.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(2.dp))
        }
        OxideIconButton(
            onClick = onClose,
            glyph = "✕",
            contentDescription = stringResource(R.string.generic_close),
        )
    }
}