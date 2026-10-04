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

package dev.oxide.launcher.ui.vulkan_checker

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics

/**
 * Vulkan 结果对话框的**有界策略**
 *
 * 检测结果里可能有几百条扩展与功能名（[dev.oxide.launcher.utils.device.VulkanRequirements]
 * 的两张清单都会整个列出来），因此对话框的尺寸不能由内容决定，必须由窗口决定：
 *
 * - [vulkanDialogBounds] 给出对话框与结果列表的宽高上限，**硬上限**是窗口扣掉留白；
 * - [vulkanResultLayout] 与 [vulkanResultListHeight] 决定结果区最终有多高：
 *   放得下就正好按内容高度，放不下就顶到上限并只在这一块里滚动。
 *
 * 这三条是纯函数，不读组合期状态也不碰 Android，因此可以在单元测试里把
 * "任何窗口尺寸、任何界面缩放下对话框都不越界、结果区都还有正高度"逐条钉死。
 *
 * **顺序不能反**：面板先被 [vulkanDialogBounds] 夹成一个有限的绝对值，
 * **之后**才允许里面出现 `verticalScroll`。反过来（先滚动、后夹）时滚动容器
 * 会量到 `maxHeight == Infinity` 并抛
 * `Vertically scrollable component was measured with an infinity maximum height`。
 */

/** 结果对话框占窗口宽度的比例 */
internal const val VULKAN_DIALOG_WIDTH_FRACTION = 0.94f

/** 结果对话框占窗口高度的比例。留出上面这一截，遮罩边缘仍然看得出来是浮层 */
internal const val VULKAN_DIALOG_HEIGHT_FRACTION = 0.88f

/** 结果列表在对话框里最多占的高度比例；剩下的留给标题与确认键 */
internal const val VULKAN_DIALOG_LIST_FRACTION = 0.52f

/** 结果列表的绝对下限（dp） */
internal const val VULKAN_DIALOG_MIN_LIST_HEIGHT_DP = 64

/** 一个对话框的三段尺寸 */
@Immutable
internal data class VulkanDialogBounds(
    /** 对话框宽度 */
    val width: Dp,
    /** 对话框高度；结果那种形态下这是一个**固定值**而不是上限 */
    val height: Dp,
    /** 结果列表的高度**上限** */
    val listMaxHeight: Dp,
    /** 对话框与窗口边缘之间的留白 */
    val gutter: Dp,
) {
    /** 对话框连同留白是否放得进窗口——放大界面时这一条是硬约束 */
    fun fitsWithin(windowWidth: Dp, windowHeight: Dp): Boolean =
        width + gutter * 2 <= windowWidth && height + gutter * 2 <= windowHeight

    /** 结果区永远是正高度，否则"可滚动的列表"会退化成 0dp 的死区 */
    val listPositive: Boolean get() = listMaxHeight > 0.dp

    /** 结果区必须比整块对话框矮，否则标题与确认键就没有地方放 */
    val listInsideDialog: Boolean get() = listMaxHeight < height
}

/**
 * 算出对话框与结果列表的尺寸
 *
 * 宽度走"几个卡片最小宽度"、高度走窗口扣掉留白之后的一个固定比例，
 * 界面缩放因此与窗口一起生效。三处都在同一份尺寸上夹紧，
 * 所以返回值永远是有限的正值——`verticalScroll` 因此永远拿得到有限的 `maxHeight`。
 */
internal fun vulkanDialogBounds(
    windowWidth: Dp,
    windowHeight: Dp,
    metrics: OxideMetrics,
): VulkanDialogBounds {
    val gutter = metrics.pagePaddingH

    val roomWidth = (windowWidth - gutter * 2).coerceAtLeast(0.dp)
    val roomHeight = (windowHeight - gutter * 2).coerceAtLeast(0.dp)

    val width = minOf(
        windowWidth * VULKAN_DIALOG_WIDTH_FRACTION,
        metrics.cardMinWidth * 2.6f,
        roomWidth,
    ).coerceAtLeast(0.dp)

    val height = minOf(
        windowHeight * VULKAN_DIALOG_HEIGHT_FRACTION,
        roomHeight,
    ).coerceAtLeast(0.dp)

    // 标题与确认键各占一行导航项那么高，剩下的才轮到结果
    val listMaxHeight = minOf(
        height * VULKAN_DIALOG_LIST_FRACTION,
        (height - metrics.navItemHeight * 2f).coerceAtLeast(0.dp),
    ).coerceAtLeast(0.dp)

    return VulkanDialogBounds(
        width = width,
        height = height,
        listMaxHeight = listMaxHeight,
        gutter = gutter,
    )
}

/** 结果放不放得下 */
internal enum class VulkanResultLayout {
    /** 全部内容都在可见区域内，不需要滚动 */
    Fits,

    /** 内容超过结果区的上限，只在那一块里滚动 */
    Scrolls,
}

/**
 * 内容比结果区的上限高就该滚动
 *
 * 严格大于才滚：正好等高时多出一像素滚动条只会让人以为还有内容没看完。
 */
internal fun vulkanResultLayout(
    contentHeightDp: Int,
    listMaxHeightDp: Int,
): VulkanResultLayout =
    if (contentHeightDp > listMaxHeightDp) VulkanResultLayout.Scrolls else VulkanResultLayout.Fits

/**
 * 结果区最终有多高
 *
 * - 放得下（[VulkanResultLayout.Fits]）：正好按内容高度，**不**占满上限。
 *   检测不出来时只有一行说明，那一行也不该撑出一个巨大的空盒子。
 * - 放不下（[VulkanResultLayout.Scrolls]）：顶到上限，滚动只发生在这一块里。
 *
 * 下限保证即使内容只有一行也能读得出来；上限至少等于下限，
 * 因此窗口比下限还矮时也不会算出负高度或 0dp 的死区。
 */
internal fun vulkanResultListHeight(
    contentHeightDp: Int,
    maxHeightDp: Int,
    minHeightDp: Int = VULKAN_DIALOG_MIN_LIST_HEIGHT_DP,
): Int {
    val ceiling = maxHeightDp.coerceAtLeast(minHeightDp)
    return when (vulkanResultLayout(contentHeightDp, ceiling)) {
        VulkanResultLayout.Scrolls -> ceiling
        VulkanResultLayout.Fits -> contentHeightDp.coerceIn(minHeightDp, ceiling)
    }
}