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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 内容型表面（文件 / 日志）的共用零件
 *
 * 这两块表面与设置页不同：它们是**内容**而不是设置，因此不能长成
 * `OxideSettingsGroup` 的样子（标签 + 一列紧凑行）。但它们仍然必须满足
 * 整套 Oxide 语言的两条硬要求：
 *
 * 1. **不透明**。[OxideSurface] 的底色是 `Oxide.SurfaceBase`（深色下是
 *    `rgba(10,10,10,.72)`）再叠一层斜向高光，因此它是半透明的。文件页与日志页
 *    压在外壳之上，底下的首页、实例页或者**旧文件浏览器**会从那 28% 里透出来——
 *    这正是设备截图里"两层界面叠在一起"的来源。这里改用 `Oxide.BgElevated`
 *    （不透明）打底，斜向高光仍然叠在上面，所以层次关系不变，但底下什么都看不见。
 * 2. **一套行**。文件、文件夹、回收站条目与日志来源此前各写各的底色、圆角、
 *    描边和两行文字，因此同一屏里"文件夹行"和"文件行"读起来不像同一类东西。
 *    统一走 [OxideContentRow] 之后，同一屏里每一行的读法一致。
 *
 * 自适应尺寸是一组纯函数（[oxideContentLayoutFor]），因此可以直接单测：
 * 文件页用它决定面包屑显示几级，日志页与关于页用它决定要不要并排。
 */

// ---------------------------------------------------------------------------
// 自适应尺寸（纯函数）
// ---------------------------------------------------------------------------

/**
 * 内容表面某一档上的列规则
 *
 * @param sideBySide 列表列与详情列是否并排
 * @param listWidth 并排时列表列的固定宽度
 * @param listWeight 纵向堆叠时列表列占的高度比例
 * @param detailWeight 纵向堆叠时详情列占的高度比例
 * @param crumbs 路径条最多显示几级
 */
@Immutable
internal data class OxideContentLayout(
    val sideBySide: Boolean,
    val listWidth: Dp,
    val listWeight: Float,
    val detailWeight: Float,
    val crumbs: Int,
)

/**
 * 并排的门槛倍数
 *
 * 2.0 而不是更高的倍数：内容页的两列**高度**才是稀缺资源（横屏 360dp 高），
 * 纵向堆叠会把两个可滚动区域各压到一半，日志正文只剩几行。因此宁可让详情列
 * 窄一点，也要并排。反过来窄到连两列都放不下时（2 倍门槛以下）才堆叠，
 * 那时列表列至少还有 [OxideMetrics.cardMinWidth] 那么宽，名字读得出来。
 */
private const val OXIDE_CONTENT_SIDE_BY_SIDE_FACTOR = 2.0f

/** 堆叠时列表列占的高度比例；详情列永远拿到更多，因为正文才是主体 */
private const val OXIDE_CONTENT_LIST_WEIGHT = 0.42f

/** 路径条最多显示几级；再多就只留最后几级，前面用省略号交代还有上层 */
private const val OXIDE_CONTENT_CRUMBS_MAX = 5

/**
 * 内容表面的列规则
 *
 * 纯函数：参数只有可用宽度与卡片最小宽度，不读组合期状态，也不碰磁盘，
 * 因此文件页、日志页与关于页都从这一处取同一套规则，单测可以直接覆盖每一档。
 *
 * [availableWidth] 必须已经扣掉页面左右留白（`metrics.pagePaddingH`），
 * 否则并排判定会多算一段宽度。
 */
internal fun oxideContentLayoutFor(
    availableWidth: Dp,
    cardMinWidth: Dp,
): OxideContentLayout {
    val sideBySide = availableWidth >= cardMinWidth * OXIDE_CONTENT_SIDE_BY_SIDE_FACTOR
    return OxideContentLayout(
        sideBySide = sideBySide,
        listWidth = cardMinWidth,
        listWeight = OXIDE_CONTENT_LIST_WEIGHT,
        detailWeight = 1f - OXIDE_CONTENT_LIST_WEIGHT,
        // 每多出一张卡片的宽度就多显示一级，最后一级始终是当前目录，
        // 所以下限是 1：再窄也要看得见"自己在哪"
        crumbs = (availableWidth / cardMinWidth).toInt()
            .coerceIn(1, OXIDE_CONTENT_CRUMBS_MAX),
    )
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

/**
 * 不透明的内容面板
 *
 * 与 [OxideSurface] 的唯一区别是底色换成不透明的 [Oxide.BgElevated]；
 * 斜向高光与 1px 描边照旧，因此它在视觉上仍然是参考稿里的那张卡，
 * 只是不再把底下的界面透出来。`selected` 用同样不透明的 [Oxide.BgTabActive]。
 */
@Composable
internal fun OxideContentSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = Oxide.RadiusCard,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgElevated)
            .background(Oxide.SurfaceBrush)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 内容页的顶部：返回 + 大标题 + 副标题 + 右侧动作
 *
 * 大标题只有一层（[OxidePageTitle]），副标题降一级用 [OxideSectionLabel]，
 * 页面内部不再出现第二份标题——此前文件页与日志页各自用 `Title` 字号在
 * 面板里又写了一遍标题，同一屏里出现两级同权重的标题。
 */
@Composable
internal fun OxideContentHeader(
    metrics: OxideMetrics,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideIconButton(
                onClick = onDismiss,
                glyph = "←",
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_sec_topbar_back)
                ),
            )
            Spacer(Modifier.width(metrics.secRowGap * 2))
            OxidePageTitle(text = title, modifier = Modifier.weight(1f))
            trailing?.let {
                Spacer(Modifier.width(metrics.secRowGap))
                it()
            }
        }
        OxideSectionLabel(text = subtitle)
        Spacer(Modifier.height(metrics.sectionGap))
    }
}

// ---------------------------------------------------------------------------
// 行
// ---------------------------------------------------------------------------

/**
 * 内容型列表里的一行
 *
 * 文件条目、文件夹条目、回收站条目与日志来源共用它，所以同一屏里
 * "文件夹" 与 "文件" 的读法一致：标题一行（正文字号）、说明一行（小标签），
 * 底色与描边只由 `selected` 决定。
 *
 * 选中态同时以 `selected` 与 [role] 暴露给无障碍服务，不只靠底色区分。
 */
@Composable
internal fun OxideContentRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = role,
                onClick = onClick,
            )
            .padding(
                horizontal = OxideContentRowPadding,
                vertical = OxideContentRowPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(OxideContentRowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Oxide.Fg else Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(OxideContentRowGap))
            it()
        }
    }
}

/** 行内横向留白；内容行的密度与紧凑设置行一致 */
private val OxideContentRowPadding: Dp = 9.dp

/** 行内纵向留白 */
private val OxideContentRowPaddingV: Dp = 6.dp

/** 行内元素之间的间距 */
private val OxideContentRowGap: Dp = 6.dp

/**
 * 勾选框：选中时中间那一块才是实心，因此不只靠边框区分
 *
 * 尺寸沿用文件页此前那一枚（10dp 见方），导出页的三态勾选框与此同尺寸。
 */
@Composable
internal fun OxideContentCheckBox(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(10.dp)
            .height(10.dp)
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
                    .width(4.dp)
                    .height(4.dp)
                    .clip(Oxide.RadiusBadge)
                    .background(Oxide.FgMuted)
            )
        }
    }
}

/**
 * 种类徽章：文件夹 / 文件 / 日志来源共用一枚方块
 *
 * 只画一个符号而不是图标资源，所以必须补上无障碍描述；
 * 符号本身仍然不是唯一信息——行的说明里也写着它是文件夹还是文件。
 */
@Composable
internal fun OxideContentKindBadge(
    glyph: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Oxide.RadiusBadge)
            .background(Oxide.BgChip)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBadge)
            .semanticsLabel(description)
            .width(16.dp)
            .height(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
    }
}

private fun Modifier.semanticsLabel(description: String): Modifier =
    semantics(mergeDescendants = true) { contentDescription = description }

// ---------------------------------------------------------------------------
// 路径条
// ---------------------------------------------------------------------------

/**
 * 路径条上的一段
 *
 * @param label 这一段的文字
 * @param isCurrent 是不是当前目录；当前目录不可点（已经在那里了），
 *   所以它既没有 `onClick`，朗读时也不会被当成一个按钮
 * @param chainIndex 对应层级链里的下标。省略号占位的那一段没有对应项，为 -1
 */
@Immutable
internal data class OxidePathCrumb(
    val label: String,
    val isCurrent: Boolean,
    val chainIndex: Int,
)

/**
 * 路径条要显示哪几级
 *
 * 层级链是「根 → … → 当前目录」，末端必须留下；前面按 [maxCrumbs] 截断，
 * 被截掉的那几级用 `…` 交代，表示"上面还有"。空链返回空列表——那一屏会退化成
 * 只有导航按钮与排序按钮，不会留下一条空的路径条。
 *
 * 每一段都带着自己在层级链里的下标，因此点哪一段都对应得到真实目录，
 * 不需要调用方再猜省略号占掉了几个位置。
 */
internal fun oxidePathCrumbsFor(
    labels: List<String>,
    maxCrumbs: Int,
): List<OxidePathCrumb> {
    val limit = maxCrumbs.coerceAtLeast(1)
    if (labels.isEmpty()) return emptyList()
    if (labels.size <= limit) {
        return labels.mapIndexed { index, label ->
            OxidePathCrumb(
                label = label,
                isCurrent = index == labels.lastIndex,
                chainIndex = index,
            )
        }
    }
    // 只放得下一级时不给省略号腾位置：那样就只剩一个"…"，
    // 用户既看不到自己在哪一层，也点不到任何上层
    if (limit == 1) {
        return listOf(
            OxidePathCrumb(
                label = labels.last(),
                isCurrent = true,
                chainIndex = labels.lastIndex,
            )
        )
    }
    val keptFrom = labels.size - (limit - 1)
    return buildList {
        // 被丢掉的那几级用一个省略号占位，省略号自己不可点
        add(
            OxidePathCrumb(
                label = OxidePathEllipsis,
                isCurrent = false,
                chainIndex = -1,
            )
        )
        labels.forEachIndexed { index, label ->
            if (index >= keptFrom) {
                add(
                    OxidePathCrumb(
                        label = label,
                        isCurrent = index == labels.lastIndex,
                        chainIndex = index,
                    )
                )
            }
        }
    }
}

/** 路径条里代表"上面还有几级被省略了"的那一段 */
internal const val OxidePathEllipsis: String = "…"

/**
 * 路径条
 *
 * 此前文件页的"路径"是把 `currentDir` 整条路径塞进一个两行省略的文本里：
 * 既读不出自己在哪一层，也点不到任何上层。这里的每一级都是一个可选中的段，
 * 祖先段点下去就是回到那一层，当前目录那一段高亮但不可点。
 */
@Composable
internal fun OxideContentPathBar(
    metrics: OxideMetrics,
    crumbs: List<OxidePathCrumb>,
    modifier: Modifier = Modifier,
    /** 传入 [OxidePathCrumb.chainIndex]；省略号那一段不会触发它 */
    onNavigate: (chainIndex: Int) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    text = OxidePathSeparator,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                )
            }
            if (crumb.isCurrent || crumb.chainIndex < 0) {
                Text(
                    text = crumb.label,
                    color = if (crumb.isCurrent) Oxide.Fg else Oxide.FgFaint,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Text(
                    text = crumb.label,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(Oxide.RadiusBadge)
                        .selectable(
                            selected = false,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onNavigate(crumb.chainIndex) },
                        ),
                )
            }
        }
    }
}

/** 路径段之间的分隔符 */
internal const val OxidePathSeparator: String = "/"