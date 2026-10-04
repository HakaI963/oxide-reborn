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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.secControlPadding
import dev.oxide.launcher.ui.screens.main.oxide.secRowGap
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.utils.device.McVersionSpan
import dev.oxide.launcher.utils.device.VulkanCapabilities
import dev.oxide.launcher.utils.device.VulkanDependency
import dev.oxide.launcher.utils.device.VulkanRequirements
import dev.oxide.launcher.utils.device.profileSupport
import dev.oxide.launcher.utils.device.supports

/**
 * Vulkan 检测对话框：Oxide 表面
 *
 * **检测后端一个字都没有改。** `VulkanChecker.checkCapabilities`、[VulkanCapabilities]、
 * [VulkanRequirements]、[profileSupport] 与 [supports] 全部照旧被调用，结论也全部照旧来自它们；
 * 换掉的只有那一层 Material 弹窗——v1.6.0 之前这里是一张
 * `MaterialTheme.shapes.extraLarge` + `FilledTonalButton` 的大 Material 对话框，
 * 在整体近黑的语言里明显是另一种东西。
 *
 * 现在的形态是：一层 [Oxide.PanelBackdrop] 遮罩 + 一块有界的居中面板（[Oxide.DrawerBg]），
 * 标题、结论与确认键都用 Oxide 的字号。检测结果可能列到几百条扩展与功能名，
 * 所以**对话框的尺寸由窗口决定，而不是由内容决定**：面板先被 [vulkanDialogBounds]
 * 夹成一个有限的绝对值，结果列表再被夹进其中一块有界的区域里，
 * 放不下时只在这一块里滚动（判据见 [vulkanResultListHeight]）。
 * 对话框因此在任何内容长度下都是同一个尺寸。
 *
 * 入口签名与旧版完全一致，因此 [dev.oxide.launcher.ui.activities.MainActivity]
 * 那一处调用不需要改参数。
 */
@Composable
fun VulkanChecker(
    operation: VCOperation,
    onChange: (VCOperation) -> Unit,
    startCheck: (Version) -> Unit,
    confirmResult: () -> Unit,
) {
    val metrics = rememberOxideMetrics()

    when (operation) {
        is VCOperation.None -> {}

        is VCOperation.Tip -> OxideVulkanDialog(
            metrics = metrics,
            onConfirm = { startCheck(operation.version) },
        ) { _ ->
            // 提示只有一段说明：面板按内容高度摆，正文那一块自己滚动
            Text(
                text = stringResource(R.string.game_vulkan_check_text),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
            )
        }

        is VCOperation.Result -> {
            val data = operation.data
            OxideVulkanDialog(
                metrics = metrics,
                // 版本号与是否使用 Turnip 进副标题那一行，正文里不再重复：
                // 它们是两个固定值，而正文那一块是留给可能几百条的依赖清单的
                subtitle = buildList {
                    data?.let {
                        add(stringResource(R.string.game_vulkan_check_version, it.versionString))
                    }
                    add(stringResource(R.string.game_vulkan_check_turnip, operation.useTurnip))
                }.joinToString(" · "),
                // 结果形态：面板固定高度，滚动只发生在结果区内部
                boundedResult = true,
                onConfirm = {
                    confirmResult()
                    onChange(VCOperation.None)
                },
            ) { listMaxHeight ->
                OxideVulkanResultList(
                    metrics = metrics,
                    listMaxHeight = listMaxHeight,
                    data = data,
                )
            }
        }
    }
}

/**
 * Vulkan 对话框外壳：遮罩 + 一块有界的居中面板
 *
 * 三段结构与 Oxide 的其它居中面板一致（见
 * [dev.oxide.launcher.ui.screens.main.oxide.OxidePanelShell]）：标题行 → 正文 → 底部确认键。
 *
 * [boundedResult] 决定"谁负责滚动"：
 * - false（提示那种短文案）：面板按内容高度摆、**仍然有有限的上限**，正文那一层滚动；
 * - true（结果那种长清单）：面板是固定高度，正文那一层不滚，由 [OxideVulkanResultList]
 *   自己那一块有界的区域滚。
 *
 * 两种形态里 `verticalScroll` 拿到的 `maxHeight` 都是有限的——这是必须的，
 * 否则 Compose 会抛
 * `Vertically scrollable component was measured with an infinity maximum height`。
 * 两种形态也**绝不**同时出现两层竖向滚动。
 */
@Composable
internal fun OxideVulkanDialog(
    metrics: OxideMetrics,
    subtitle: String? = null,
    boundedResult: Boolean = false,
    onConfirm: () -> Unit,
    content: @Composable (listMaxHeight: Dp) -> Unit,
) {
    Dialog(
        // 这张对话框不可点外面关掉：确认之后要走的是 MainActivity 那条 `resumeCont()`，
        // 因此它必须由用户点确认键来推进
        onDismissRequest = {},
        properties = DialogProperties(
            // 自己算宽度：平台默认宽度是按 Material 的对话框算的，这里不适用
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        // 这一层由 MainActivity 挂在 Oxide 外壳之外，所以自己递一次调色板
        // （与设置页同一套做法），换主题时它也跟着变
        ProvideOxideChrome {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val bounds = remember(metrics, maxWidth, maxHeight) {
                    // 以 Dialog 自己量到的窗口为准，不用 LocalConfiguration：
                    // Dialog 有自己的窗口，两者的可用区域不一定相同
                    vulkanDialogBounds(
                        windowWidth = maxWidth,
                        windowHeight = maxHeight,
                        metrics = metrics,
                    )
                }

                // 遮罩：盖住整块窗口，让底下的页面不与对话框争视觉
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Oxide.PanelBackdrop)
                        // 不可点外面关闭，但仍然要吃掉点击，
                        // 否则遮罩之下的页面仍然能被点到
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(bounds.width)
                        .then(
                            if (boundedResult) {
                                Modifier.height(bounds.height)
                            } else {
                                Modifier.heightIn(max = bounds.height)
                            }
                        )
                        .clip(Oxide.RadiusPanel)
                        .background(Oxide.DrawerBg)
                        .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel),
                ) {
                    Text(
                        text = stringResource(R.string.game_vulkan_check_title),
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.DrawerTitle.fontSize,
                        lineHeight = Oxide.Type.DrawerTitle.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = metrics.cardGap,
                            end = metrics.cardGap,
                            top = metrics.secRowGap,
                        ),
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                start = metrics.cardGap,
                                end = metrics.cardGap,
                                top = 2.dp,
                            ),
                        )
                    }

                    val bodyModifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (boundedResult) {
                                // 结果形态：这一层不滚，滚的是里面那块有界的结果区
                                Modifier.padding(
                                    start = metrics.cardGap,
                                    end = metrics.cardGap,
                                    top = metrics.secRowGap,
                                )
                            } else {
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        start = metrics.cardGap,
                                        end = metrics.cardGap,
                                        top = metrics.secRowGap,
                                        bottom = metrics.secRowGap,
                                    )
                            }
                        )

                    Column(modifier = bodyModifier) {
                        content(bounds.listMaxHeight)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = metrics.cardGap,
                                end = metrics.cardGap,
                                bottom = metrics.secRowGap,
                            ),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OxideButton(
                            text = stringResource(R.string.generic_confirm),
                            onClick = onConfirm,
                            tone = OxideButtonTone.Primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 结果区：先被 [listMaxHeight] 夹住，超出的部分只在这一块里滚
 *
 * 最终高度由 [vulkanResultListHeight] 决定：内容放得下就正好按内容高度，
 * 放不下就顶到上限。内容高度由 `onSizeChanged` 量出来——量的是滚动层**内部**那一列，
 * 它在竖向没有约束，因此报出来的是内容的真实高度而不是被裁过的高度。
 *
 * 检测不出来（[VulkanCapabilities] 为 null）时这一块只有一行说明，高度随之收窄，
 * 但对话框本身仍然是一个固定尺寸。
 */
@Composable
internal fun OxideVulkanResultList(
    metrics: OxideMetrics,
    listMaxHeight: Dp,
    data: VulkanCapabilities?,
) {
    // 首帧按上限画：内容通常远高于上限，先给上限再等一次量测，
    // 这样绝大多数情况下界面上不会有一次高度跳动
    val density = LocalDensity.current.density
    var contentHeightDp by remember { mutableIntStateOf(listMaxHeight.value.toInt()) }
    val heightDp = vulkanResultListHeight(
        contentHeightDp = contentHeightDp,
        maxHeightDp = listMaxHeight.value.toInt(),
        minHeightDp = metrics.navItemHeight.value.toInt().coerceAtLeast(1),
    )

    Box(modifier = Modifier.height(heightDp.dp)) {
        if (data == null) {
            Text(
                text = stringResource(R.string.game_vulkan_check_failed),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { contentHeightDp = (it.height / density).toInt() },
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                // 有限的高度已经在外面给过，这一层拿到的 maxHeight 因此是有限的
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { contentHeightDp = (it.height / density).toInt() },
            ) {
                OxideVulkanResultBody(metrics = metrics, data = data)
            }
        }
    }
}

/**
 * 检测结果的正文
 *
 * 读的还是后端那几个函数：[profileSupport] 给总体结论与版本区间，
 * [supports] 给每一条扩展/功能的缺失情况，[VulkanRequirements] 给清单本身。
 * 一个字都没有改，只是把它们排在一条**有界**的滚动区里。
 */
@Composable
private fun OxideVulkanResultBody(
    metrics: OxideMetrics,
    data: VulkanCapabilities,
) {
    val profiles = data.profileSupport()

    // 总体结论
    Text(
        text = when {
            profiles.all { it.supported } ->
                stringResource(R.string.game_vulkan_check_supp, profiles.first().since)

            profiles.none { it.supported } ->
                stringResource(R.string.game_vulkan_check_unsupp)

            else -> stringResource(R.string.game_vulkan_check_partial)
        },
        color = Oxide.FgStrong,
        fontSize = Oxide.Type.BodyStrong.fontSize,
        lineHeight = Oxide.Type.BodyStrong.lineHeight,
    )

    // 仅在部分版本区间受支持时，展示各版本区间的支持情况
    if (profiles.distinctBy { it.supported }.size > 1) {
        OxideVulkanGroup(
            metrics = metrics,
            title = stringResource(R.string.game_vulkan_check_versions),
        ) {
            profiles.forEach { profile ->
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
                    Text(
                        text = "Minecraft ${profile.versionRangeText}",
                        color = Oxide.FgDim,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (profile.supported) {
                            stringResource(R.string.game_vulkan_check_profile_supp)
                        } else {
                            stringResource(R.string.game_vulkan_check_profile_unsupp)
                        },
                        color = if (profile.supported) Oxide.FgMuted else Oxide.Fg,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    // 各功能/扩展的支持情况与版本依赖标注
    OxideVulkanGroup(metrics = metrics, title = stringResource(R.string.game_vulkan_check_extensions)) {
        VulkanRequirements.EXTENSIONS.forEach { dependency ->
            OxideVulkanDependencyRow(
                metrics = metrics,
                dependency = dependency,
                supported = data.supports(dependency),
            )
        }
    }
    OxideVulkanGroup(metrics = metrics, title = stringResource(R.string.game_vulkan_check_features)) {
        VulkanRequirements.FEATURES.forEach { dependency ->
            OxideVulkanDependencyRow(
                metrics = metrics,
                dependency = dependency,
                supported = data.supports(dependency),
            )
        }
    }
}

/** 一个分组：标题 + 缩进的内容 */
@Composable
private fun OxideVulkanGroup(
    metrics: OxideMetrics,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.secRowGap))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = metrics.secControlPadding),
            content = content,
        )
    }
}

/**
 * 一条扩展或功能
 *
 * 缺失的条目除了颜色之外还有一行 " (missing)"，因此不会只靠颜色区分；
 * 版本依赖标注同样带前缀（`Required:` / `Optional:`），不靠缩进猜。
 */
@Composable
private fun OxideVulkanDependencyRow(
    metrics: OxideMetrics,
    dependency: VulkanDependency,
    supported: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dependency.name,
                color = if (supported) Oxide.FgMuted else Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!supported) {
                Spacer(Modifier.width(metrics.secRowGap))
                Text(
                    text = stringResource(R.string.game_vulkan_check_item_missing),
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                    maxLines = 1,
                )
            }
        }

        if (dependency.requiredIn.isNotEmpty()) {
            OxideVulkanSpanRow(
                metrics = metrics,
                label = stringResource(R.string.game_vulkan_check_dep_required),
                spans = dependency.requiredIn,
            )
        }
        if (dependency.optionalIn.isNotEmpty()) {
            OxideVulkanSpanRow(
                metrics = metrics,
                label = stringResource(R.string.game_vulkan_check_dep_optional),
                spans = dependency.optionalIn,
            )
        }
    }
}

/** `Required: 1.20.5-1.20.6, 1.21+` 那一行 */
@Composable
private fun OxideVulkanSpanRow(
    metrics: OxideMetrics,
    label: String,
    spans: List<McVersionSpan>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = metrics.secRowGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
    ) {
        Text(
            text = label,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
        Text(
            text = spans.joinToString(", ") { it.displayText },
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}