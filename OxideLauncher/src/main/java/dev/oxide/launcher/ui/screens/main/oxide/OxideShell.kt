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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.BuildConfig
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.LocalOxideBrandSlot
import dev.oxide.launcher.ui.components.OxideBrandSlotLogo
import dev.oxide.launcher.ui.components.OxideBrandSlotState
import dev.oxide.launcher.ui.components.OxideLogo
import dev.oxide.launcher.ui.theme.Oxide
import kotlin.math.roundToInt

/** 选中底上沿那一道 1px 高光，参考稿 `inset 0 1px rgba(255,255,255,.045)` */
private val RailTopHighlight = Color(0x0BFFFFFF)

/** 侧栏导航项与选中底共用的圆角，参考稿 10px */
private val NavItemShape = RoundedCornerShape(Oxide.NavItemRadius)

/**
 * Oxide 的应用外壳
 *
 * 结构与参考稿一致：左侧固定侧栏、右侧顶栏 + 内容区，列宽按可用宽度落档。
 * 四个主页面在内容区里用 [AnimatedContent] 切换，而不是重新压入导航栈，
 * 这样切页不会重建页面内的 ViewModel，正在加载的列表也不会闪一下。
 *
 * 侧栏常驻渲染品牌 logo，同时把品牌槽的实测中心与宽度通过 [LocalOxideBrandSlot] 上报；
 * 开场动画里飞行的**那一个** logo 就落在那里，落地时两者完全重合，看不出切换。
 *
 * [nav.page] 只在下面几个子组合里读，因此换页只会让侧栏、顶栏和页面容器各自重组，
 * 不会拖着整棵树重跑。
 */
@Composable
fun OxideShell(
    nav: OxideNavState,
    modifier: Modifier = Modifier,
    metrics: OxideMetrics = rememberOxideMetrics(),
    /** 顶栏右侧的自定义内容 */
    topBarTrailing: @Composable () -> Unit = {},
    /** 侧栏底部的自定义内容，例如任务状态；可回调跳页。默认是参考稿的品牌落款 */
    sidebarFooter: @Composable ((OxidePage) -> Unit) -> Unit = { _ -> OxideSidebarBrandFooter() },
    /** 页面容器。四个页面都在这里渲染，由 [OxideNavState] 决定当前是哪一个 */
    content: @Composable (OxidePage) -> Unit,
) {
    val brandSlot = LocalOxideBrandSlot.current

    Box(modifier = modifier.fillMaxSize().background(Oxide.Bg)) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ---- 侧栏 ----
            OxideSidebarHost(
                nav = nav,
                metrics = metrics,
                brandSlot = brandSlot,
                footer = { sidebarFooter(nav::go) },
            )

            // ---- 右侧：顶栏 + 内容 ----
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OxideTopBarHost(
                    nav = nav,
                    metrics = metrics,
                    trailing = topBarTrailing,
                )
                // 参考稿 `.main{overflow:hidden}`：进场中的页面不允许画到侧栏上
                Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    OxidePageDeck(nav = nav, content = content)
                }
            }
        }
    }
}

/** 把 [nav.page] 的读限制在侧栏里，页面换页不会带着外壳一起重组 */
@Composable
private fun OxideSidebarHost(
    nav: OxideNavState,
    metrics: OxideMetrics,
    brandSlot: OxideBrandSlotState,
    footer: @Composable () -> Unit,
) {
    OxideSidebar(
        current = nav.page,
        metrics = metrics,
        onSelect = nav::go,
        brandSlot = brandSlot,
        footer = footer,
    )
}

/**
 * 侧栏
 *
 * 几何全部来自参考稿：宽度按可用宽度落档，内边距 20/15/15，
 * 顶部 4px 内缩处是 34px 高的品牌槽，与第一项导航之间留 36px（最窄一档 34px），
 * 导航项 38px 高、彼此 3px，右侧一条 1px 分隔线。
 *
 * 选中态不是 Material 胶囊，而是一整块 38px 高的半透明圆角底，
 * 按导航项步进（41px）逐格移动，640ms 到位。
 */
@Composable
private fun OxideSidebar(
    current: OxidePage,
    metrics: OxideMetrics,
    onSelect: (OxidePage) -> Unit,
    brandSlot: OxideBrandSlotState,
    footer: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(metrics.sidebarWidth)
            .fillMaxHeight()
            .background(Oxide.Sidebar),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    start = metrics.sidebarPaddingH,
                    end = metrics.sidebarPaddingH,
                    top = Oxide.SidebarPaddingTop,
                    bottom = Oxide.SidebarPaddingBottom,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            OxideBrandSlot(
                metrics = metrics,
                brandSlot = brandSlot,
            )

            Spacer(Modifier.height(metrics.brandGap))

            OxideSidebarNav(
                current = current,
                metrics = metrics,
                onSelect = onSelect,
            )

            Spacer(Modifier.weight(1f))

            OxideSidebarFooterBlock(footer = footer)
        }

        // 参考稿的 1px 右边线算在侧栏宽度里，因此它贴在内容列右边而不是叠上去
        Box(
            modifier = Modifier
                .width(Oxide.SidebarBorder)
                .fillMaxHeight()
                .background(Oxide.Line)
        )
    }
}

/**
 * 侧栏里的品牌槽：logo 就落在槽中心
 *
 * 槽的宽高与位置只由 [OxideMetrics] 决定，并把实测中心与宽度上报给开场动画；
 * 常驻的 logo 与开场飞行的 logo 用的是同一个 [OxideLogo] 与同一个静止缩放，
 * 因此动画落地与常驻 logo 之间没有任何跳变。
 */
@Composable
private fun OxideBrandSlot(
    metrics: OxideMetrics,
    brandSlot: OxideBrandSlotState,
) {
    // 参考稿 `.brandSlot{margin:0 4px}`：槽相对导航列左右各内缩 4px，
    // 被测量的就是这个盒子本身（开场动画量的也是它的矩形）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Oxide.BrandInsetH),
    ) {
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
                },
            contentAlignment = Alignment.Center,
        ) {
            // The resting logo comes from the intro itself, so its size is whatever the flight
            // actually measured rather than a second, independently derived guess. Before this,
            // the sidebar scaled by metrics.brandLogoScale, which divides by a 297dp assumed
            // start width while the real logo measures about 200dp -- so the hand-off changed the
            // logo's size by roughly 40 percent.
            OxideBrandSlotLogo()
        }
    }
}

/** 四个导航项 + 逐格移动的选中底 */
@Composable
private fun OxideSidebarNav(
    current: OxidePage,
    metrics: OxideMetrics,
    onSelect: (OxidePage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        // 选中底：按索引偏移，速度比页面进场略慢，收尾更稳
        val railOffset by animateFloatAsState(
            targetValue = current.ordinal.toFloat(),
            animationSpec = tween(Oxide.Motion.RailMs),
            label = "oxideRail"
        )
        // 参考稿的 .navRail 是 position:absolute，自己不占高度。
        // 这里若让它按 navTravel(164dp) 参与布局，导航列就会变成 164+161=325dp，
        // 640x340 的小横屏上侧栏需要 20+34+34+325+55+15=483dp，底部区块会被挤出屏幕。
        // 所以轨道用 matchParentSize 叠在导航项之上，导航项本身才是唯一占高度的东西。
        Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .offset(y = metrics.navStep * railOffset)
                    .fillMaxWidth()
                    .height(metrics.navItemHeight)
                    .clip(NavItemShape)
                    .background(Oxide.RailBrush)
                    // 参考稿 `inset 0 1px rgba(255,255,255,.045)`
                    .drawBehind {
                        drawLine(
                            color = RailTopHighlight,
                            start = Offset(0f, 0.5f),
                            end = Offset(size.width, 0.5f),
                            strokeWidth = 1f
                        )
                    }
            )
        }

        OxidePage.entries.forEach { page ->
            OxideSidebarItem(
                page = page,
                selected = page == current,
                metrics = metrics,
                onClick = { onSelect(page) },
            )
        }
        }
    }
}

/**
 * 侧栏里的一项：`22px 1fr auto` 三列 —— 两位编号 + 名称
 *
 * 编号是纯视觉排版，读屏时会被 [clearAndSetSemantics] 摘掉，
 * 选中态由 [selectable] 以 `Role.Tab` + selected 暴露，不靠颜色说话。
 */
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
            .clip(NavItemShape)
            .selectable(
                selected = selected,
                enabled = true,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Oxide.NavItemPaddingH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = page.number,
            color = if (selected) Oxide.FgNumActive else Oxide.FgNum,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            modifier = Modifier
                .width(Oxide.NavNumColumnWidth)
                .clearAndSetSemantics { },
        )
        Text(
            text = page.label,
            color = if (selected) Oxide.Fg else Oxide.FgDim,
            fontSize = Oxide.Type.Nav.fontSize,
            lineHeight = Oxide.Type.Nav.lineHeight,
            letterSpacing = Oxide.Type.Nav.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 侧栏底部块
 *
 * 参考稿 `.sidebarBottom`：贴着内容列再内缩 5dp、底部 15dp，
 * 顶上是一条 1px 分隔线，线下留 12dp。
 */
@Composable
private fun OxideSidebarFooterBlock(
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Oxide.SidebarFooterInsetH),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Oxide.SidebarBorder)
                .background(Oxide.Line)
        )
        Spacer(Modifier.height(Oxide.SidebarFooterPaddingTop))
        footer()
    }
}

/** 侧栏默认落款，对应参考稿 `.sidebarBottom` 的三行 */
@Composable
private fun OxideSidebarBrandFooter() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.oxide_shell_footer_title),
            color = Oxide.FgDim,
            fontSize = 8.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.oxide_shell_footer_env),
            color = Oxide.FgGhost,
            fontSize = 8.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.oxide_shell_footer_version, BuildConfig.VERSION_NAME),
            color = Oxide.FgGhost,
            fontSize = 8.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 把顶栏的当前页读限制在顶栏里 */
@Composable
private fun OxideTopBarHost(
    nav: OxideNavState,
    metrics: OxideMetrics,
    trailing: @Composable () -> Unit,
) {
    OxideTopBar(page = nav.page, metrics = metrics, trailing = trailing)
}

/**
 * 顶栏：46dp，左边是面包屑，右边是宿主放的自定义内容
 *
 * 面包屑参考稿 `.crumb`：`#555`、8px、字距 .18em、大写；
 * 左右留白与页面留白同宽，因此标题与页面内容左对齐。
 */
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
            text = oxideCrumb(page),
            color = Oxide.FgFaint,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            letterSpacing = 1.44.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 面包屑文案，对应参考稿每个页面的 `data-title` */
@Composable
private fun oxideCrumb(page: OxidePage): String = stringResource(
    when (page) {
        OxidePage.Home -> R.string.oxide_shell_crumb_home
        OxidePage.Instances -> R.string.oxide_shell_crumb_instances
        OxidePage.Discover -> R.string.oxide_shell_crumb_discover
        OxidePage.Settings -> R.string.oxide_shell_crumb_settings
    }
)

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
    val density = LocalDensity.current
    // 位移是绝对 dp，不是容器宽度的百分比，否则大屏上会滑掉小半屏
    val enterPx = with(density) { Oxide.Motion.PageEnterOffset.dp.toPx() }.roundToInt()
    val leavePx = with(density) { Oxide.Motion.PageLeaveOffset.dp.toPx() }.roundToInt()

    AnimatedContent(
        targetState = nav.page,
        transitionSpec = {
            val forward = nav.forward
            val enter = if (forward) enterPx else -enterPx
            val leave = if (forward) -leavePx else leavePx

            (
                slideInHorizontally(
                    animationSpec = tween(Oxide.Motion.PageEnterMs),
                    initialOffsetX = { enter }
                ) + fadeIn(tween(Oxide.Motion.PageEnterMs))
                ) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(Oxide.Motion.PageLeaveMs),
                    targetOffsetX = { leave }
                ) + fadeOut(tween(Oxide.Motion.PageLeaveMs))
                )
        },
        label = "oxidePageDeck",
    ) { page ->
        content(page)
    }
}