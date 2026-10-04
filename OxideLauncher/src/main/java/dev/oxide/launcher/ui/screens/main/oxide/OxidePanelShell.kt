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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 面板尺寸策略
 *
 * 账号与联机这两块表面不是整页：它们是一块**居中的紧凑面板**，盖在被盖住的页面之上。
 * 所以它们共用下面这一个外壳，而尺寸只有一个来源——[oxidePanelBoundsFor]。
 *
 * 这条策略是纯函数：输入只有真实的可用宽高与 [OxideMetrics]，不读组合期状态，
 * 因此可以在单元测试里把每一档窗口的数值逐条钉死，也可以保证三件事：
 *
 * 1. 面板永远不超过窗口（宽高都是**硬上限**，不是"大概这么宽"）；
 * 2. 面板永远是有界的有限值——先由 [heightIn] 把面板夹住，**之后**才允许里面滚动。
 *    这正是 v1.5.0 那个 P0 崩溃的顺序：`verticalScroll` 收到 `maxHeight == Infinity`
 *    就直接抛异常，而只要面板自己先被夹住，里面的滚动拿到的就永远有限；
 * 3. 上限来自 metrics 而不是写死的 dp，因此界面缩放与宽度档一起生效，
 *    同一个面板在 640×360 与在 1280×720 上是同一套比例。
 */
object OxidePanelSize {

    /**
     * 宽度上限：几个"卡片最小宽度"
     *
     * 用 [OxideMetrics.cardMinWidth] 而不是绝对 dp，因为它是宽度档与界面缩放共同决定的：
     * 紧凑档它更小、放大后它更大，于是这一栏永远是"两列多一点的卡片宽度"，
     * 而不是某一台设备的像素值。
     */
    const val WIDTH_IN_CARD_UNITS: Float = 2.4f

    /**
     * 高度上限：几个"导航项高度"
     *
     * 约等于十六行导航项，也就是一屏能读完的那一屏再多一点。
     * 取导航项高度是为了跟着界面缩放一起变：放大界面时面板跟着长高，
     * 但窗口装不下时仍然被 [oxidePanelBoundsFor] 的硬上限截住。
     */
    const val HEIGHT_IN_NAV_ITEMS: Float = 16f

    /**
     * 并排两列时，每一列至少要几个"卡片最小宽度"
     *
     * 1.1 而不是 1.0：行里那排小图标按钮先吃掉固定宽度，列刚好一张卡片宽时
     * 名字就没地方放了。取 1.1 是"再宽一点就还读得出来"的那一档。
     */
    const val TWO_COLUMN_WIDTH_UNITS: Float = 2.2f
}

/** 一块居中面板最终量出来的宽高与它四周留出的边距 */
@Immutable
data class OxidePanelBounds(
    val width: Dp,
    val height: Dp,
    /** 面板与窗口边缘之间保留的留白，宽高两个方向共用同一个值 */
    val gutter: Dp,
)

/**
 * 由**真实可用尺寸**算出一块居中面板的宽高
 *
 * 宽高各取三样里的最小值：一个随 metrics 走的紧凑上限、窗口扣掉留白之后剩下的空间、
 * 以及窗口本身。第三样是硬保证，所以任何窗口尺寸下面板都不可能超出窗口，
 * 也不会出现 `Dp.Infinity` 或负值——`verticalScroll` 因此永远拿得到有限的 `maxHeight`。
 *
 * [windowWidth] / [windowHeight] 传的是 `BoxWithConstraints` 量到的真实可用区域，
 * 也就是宿主已经扣掉侧栏与顶栏之后剩下给这块表面的那块，不是物理屏幕尺寸。
 *
 * 纯函数：可以直接单测，也可以由界面在任何重组里安全地重复调用。
 */
fun oxidePanelBoundsFor(
    windowWidth: Dp,
    windowHeight: Dp,
    metrics: OxideMetrics,
): OxidePanelBounds {
    val gutter = metrics.pagePaddingH

    // 扣掉两侧留白之后剩下的空间。窗口比留白还窄时也不能是负数：
    // 负的上限会让面板自己变成负尺寸，而 Dp 是浮点数，不会像 Int 那样自动夹住。
    val roomWidth = (windowWidth - gutter * 2).coerceAtLeast(0.dp)
    val roomHeight = (windowHeight - gutter * 2).coerceAtLeast(0.dp)

    val preferredWidth = metrics.cardMinWidth * OxidePanelSize.WIDTH_IN_CARD_UNITS
    val preferredHeight = metrics.navItemHeight * OxidePanelSize.HEIGHT_IN_NAV_ITEMS

    return OxidePanelBounds(
        width = minOf(preferredWidth, roomWidth, windowWidth).coerceAtLeast(0.dp),
        height = minOf(preferredHeight, roomHeight, windowHeight).coerceAtLeast(0.dp),
        gutter = gutter,
    )
}

/**
 * 面板里要不要并排放两列
 *
 * 判据是"两列都还剩得下一张多一点的卡片"：行里那一排小图标按钮（刷新、复制、删除）
 * 会先吃掉固定宽度，列再窄下去就只剩一个读不了的名字。因此每列要留
 * [TWO_COLUMN_WIDTH_UNITS] 个卡片宽——1.1 而不是 1.0，就是那多出来的 0.1。
 *
 * 全部来自 metrics，面板自己变宽变窄时列数自动跟着变；紧凑的小横屏因此自动退回一列。
 *
 * 纯函数，所以可以在单元测试里逐档断言。
 */
fun oxidePanelTwoColumns(
    panelWidth: Dp,
    metrics: OxideMetrics,
): Boolean =
    panelWidth >= metrics.cardMinWidth * OxidePanelSize.TWO_COLUMN_WIDTH_UNITS + metrics.cardGap

/**
 * 居中面板外壳
 *
 * 三段结构，与参考稿的面板一致：
 *
 * - 标题行：标题、可选的一行副标题、右侧的关闭按钮；
 * - 内容：**先**被 [OxidePanelBounds.height] 这个 `heightIn` 夹住，**再**在里面滚动，
 *   所以滚动容器量到的 `maxHeight` 一定有限（见文件头的说明）；
 * - 底部动作区：一行按钮，永远在面板底部，内容再长也不会把它顶走。
 *
 * 面板底板用 [Oxide.DrawerBg] 与边框 [Oxide.Line2]：和抽屉、浮层同一块底，
 * 层级因此只由那张二级面板底幕（`Oxide.PanelBackdrop`）与面板本身区分。
 */
@Composable
internal fun OxidePanelShell(
    title: String,
    metrics: OxideMetrics,
    bounds: OxidePanelBounds,
    onClose: () -> Unit,
    /** 标题、可选副标题与关闭按钮 */
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** 底部动作区。为 null 时这一段整个不画，面板只有标题与内容 */
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(bounds.width)
                // 关键的一步：面板先在这里被夹住。上面没有任何 weight 或
                // fillMaxHeight，因此这个 maxHeight 就是有限的绝对值，
                // 里面的 verticalScroll 拿到的也就不是 Infinity。
                .heightIn(max = bounds.height)
                .clip(Oxide.RadiusPanel)
                .background(Oxide.DrawerBg)
                .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel),
        ) {
            OxidePanelTitleRow(
                title = title,
                subtitle = subtitle,
                metrics = metrics,
                onClose = onClose,
            )

            OxideSecDivider()

            Column(
                modifier = Modifier
                    // weight 分配的是"父级还剩多少"，而父级的 maxHeight 已经有限，
                    // 所以这里拿到的 maxHeight 永远不是 Infinity
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                verticalArrangement = Arrangement.spacedBy(metrics.secGroupGap),
                content = content,
            )

            footer?.let { actions ->
                OxideSecDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = metrics.cardGap,
                            vertical = metrics.secRowGap,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = metrics.secRowGap,
                        alignment = Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** 面板标题行：标题 + 副标题 + 关闭按钮 */
@Composable
private fun OxidePanelTitleRow(
    title: String,
    subtitle: String?,
    metrics: OxideMetrics,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = metrics.cardGap,
                end = metrics.secRowGap,
                top = metrics.secRowGap,
                bottom = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Oxide.Fg,
                fontSize = Oxide.Type.DrawerTitle.fontSize,
                lineHeight = Oxide.Type.DrawerTitle.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OxideIconButton(
            onClick = onClose,
            glyph = "✕",
            modifier = Modifier.oxideIconDescription(
                stringResource(R.string.generic_close)
            ),
        )
    }
}