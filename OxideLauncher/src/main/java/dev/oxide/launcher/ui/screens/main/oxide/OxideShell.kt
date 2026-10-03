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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.ui.components.LocalOxideBrandSlot
import dev.oxide.launcher.ui.theme.Oxide

/**
 * Oxide 的应用外壳
 *
 * 结构与参考稿一致：左侧固定侧栏、右侧顶栏 + 内容区。
 * 四个主页面在内容区里用 [AnimatedContent] 切换，而不是重新压入导航栈，
 * 这样切页不会重建页面内的 ViewModel，正在加载的列表也不会闪一下。
 *
 * 侧栏品牌槽的实测位置会通过 [LocalOxideBrandSlot] 上报，
 * 开场动画里那一个 logo 就落在那里——全屏只有那一个 logo。
 */
@Composable
fun OxideShell(
    nav: OxideNavState,
    modifier: Modifier = Modifier,
    metrics: OxideMetrics = rememberOxideMetrics(),
    /** 顶栏右侧的自定义内容 */
    topBarTrailing: @Composable () -> Unit = {},
    /** 侧栏底部的自定义内容，例如任务状态；可回调跳页 */
    sidebarFooter: @Composable ((OxidePage) -> Unit) -> Unit = {},
    /** 页面容器。四个页面都在这里渲染，由 [OxideNavState] 决定当前是哪一个 */
    content: @Composable (OxidePage) -> Unit,
) {
    val brandSlot = LocalOxideBrandSlot.current

    Box(modifier = modifier.fillMaxSize().background(Oxide.Bg)) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ---- 侧栏 ----
            OxideSidebar(
                current = nav.page,
                metrics = metrics,
                onSelect = nav::go,
                brandSlot = brandSlot,
                footer = { sidebarFooter(nav::go) },
            )

            // ---- 右侧：顶栏 + 内容 ----
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OxideTopBar(
                    page = nav.page,
                    metrics = metrics,
                    trailing = topBarTrailing,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    OxidePageDeck(nav = nav, content = content)
                }
            }
        }
    }
}

/**
 * 侧栏
 *
 * 固定宽度、极暗的底、右侧一条 1px 分隔线。选中态不是 Material 的胶囊，
 * 而是一条按导航项步进逐格移动的竖向指示条。
 */
@Composable
private fun OxideSidebar(
    current: OxidePage,
    metrics: OxideMetrics,
    onSelect: (OxidePage) -> Unit,
    brandSlot: dev.oxide.launcher.ui.components.OxideBrandSlotState,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(metrics.sidebarWidth)
            .fillMaxHeight()
            .background(Oxide.Sidebar)
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // 开场动画的落点：这里只上报几何，不再渲染第二份 logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Oxide.BrandSlotHeight)
                .onGloballyPositioned { coords ->
                    val size = coords.size
                    val origin = coords.positionInRoot()
                    brandSlot.report(
                        center = Offset(origin.x + size.width / 2f, origin.y + size.height / 2f),
                        widthPx = size.width.toFloat()
                    )
                }
        )

        Spacer(Modifier.height(18.dp))

        // 选中指示条：按索引偏移，速度比页面进场略慢，收尾更稳
        val railOffset by animateFloatAsState(
            targetValue = current.ordinal.toFloat(),
            animationSpec = tween(Oxide.Motion.RailMs),
            label = "oxideRail"
        )
        val steps = OxidePage.entries.size
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.navStep * steps)
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 0.dp)
                    .offset(y = metrics.navStep * railOffset)
                    .width(2.dp)
                    .height(metrics.navItemHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Oxide.RailBrush)
            )
        }

        OxidePage.entries.forEachIndexed { index, page ->
            OxideSidebarItem(
                page = page,
                selected = page == current,
                metrics = metrics,
                onClick = { onSelect(page) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))

        Box(modifier = Modifier.fillMaxWidth()) {
            footer()
        }
    }
}

/** 侧栏里的一项：两位编号 + 名称 */
@Composable
private fun OxideSidebarItem(
    page: OxidePage,
    selected: Boolean,
    metrics: OxideMetrics,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.navItemHeight)
            .clip(Oxide.RadiusControl)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = page.number,
            color = if (selected) Oxide.FgNumActive else Oxide.FgNum,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = page.label,
            color = if (selected) Oxide.Fg else Oxide.FgGhost,
            fontSize = Oxide.Type.Nav.fontSize,
            lineHeight = Oxide.Type.Nav.lineHeight,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 顶栏：46dp，只有页面名和右侧自定义内容 */
@Composable
private fun OxideTopBar(
    page: OxidePage,
    metrics: OxideMetrics,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarHeight)
            .padding(horizontal = metrics.pagePaddingH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = page.label.uppercase(),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * 页面容器
 *
 * 前进时新页面从右侧 18dp 进来、旧页面向左退 12dp；
 * 后退时方向相反。进场比离场慢，于是两者会短暂重叠，
 * 观感上是"滑过去"而不是"替换掉"。没有任何缩放、回弹或过冲。
 */
@Composable
private fun OxidePageDeck(
    nav: OxideNavState,
    content: @Composable (OxidePage) -> Unit,
) {
    AnimatedContent(
        targetState = nav.page,
        transitionSpec = {
            val forward = nav.forward
            val enterOffset = if (forward) Oxide.Motion.PageEnterOffset else -Oxide.Motion.PageEnterOffset
            val leaveOffset = if (forward) -Oxide.Motion.PageLeaveOffset else Oxide.Motion.PageLeaveOffset

            (
                slideInHorizontally(
                    animationSpec = tween(Oxide.Motion.PageEnterMs),
                    initialOffsetX = { width -> (enterOffset / 100f * width).toInt() }
                ) + fadeIn(tween(Oxide.Motion.PageEnterMs))
                ) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(Oxide.Motion.PageLeaveMs),
                    targetOffsetX = { width -> (leaveOffset / 100f * width).toInt() }
                ) + fadeOut(tween(Oxide.Motion.PageLeaveMs))
                )
        },
        label = "oxidePageDeck",
    ) { page ->
        content(page)
    }
}

