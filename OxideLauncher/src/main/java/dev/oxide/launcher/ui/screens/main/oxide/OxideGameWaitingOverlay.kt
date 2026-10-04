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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.theme.Oxide
import kotlinx.coroutines.delay

/**
 * 游戏等待表面：Oxide 表面
 *
 * 旧的那一块是 [dev.oxide.launcher.ui.screens.game.GameScreen] 里的
 * `GameInfoBox`：`BackgroundCard` + `MaterialTheme.typography.bodyLarge` + `IconButton`
 * 组成的浮层，写着 "The game is running. Waiting for the game screen to appear…" 与版本信息。
 * 它用的是 Material 的字体与图标，跟启动器其余部分不是同一种语言，
 * 读起来像一条与当前界面无关的旧通知。
 *
 * 现在这一块是 Oxide 表面：紧凑的居中卡片，写清**游戏版本、加载器、启动状态**，
 * 可选地带上**已经过去多久**，以及**真正被支持的那些动作**——
 * 收起这一块永远可以，结束游戏只有宿主真的接了回调时才出现。
 *
 * 它盖在游戏画面之上但**不加遮罩**：这时游戏已经起来了，玩家还要看见它。
 */

/** 等待表面所处的状态 */
internal enum class OxideGameWaitingState {
    /** 游戏进程已经起来了，但还没有画出第一帧 */
    Waiting,

    /** 第一帧已经画出来 */
    FirstFrame,
}

/**
 * 已经过去多少秒
 *
 * 没有起始时刻就是 null（那一行整个不出现）；宿主给的时刻在将来也只算 0，
 * 不写负数——那只会让人以为程序坏了。
 */
internal fun oxideGameWaitingElapsedSeconds(
    startedAtMillis: Long?,
    nowMillis: Long,
): Int? = startedAtMillis?.let { start ->
    ((nowMillis - start).coerceAtLeast(0L) / 1000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

/**
 * 等待表面要显示的那些真实读数
 *
 * 全部来自实例与宿主已经知道的东西：没有任何一项是为了好看而编出来的。
 * [startedAtMillis] 为 null 表示宿主没有给出起始时刻，这时**整行耗时都不出现**，
 * 而不是显示一个从 0 开始假跑的计时器。
 */
@Immutable
internal data class OxideGameWaitingFacts(
    val state: OxideGameWaitingState,
    /** 实例名 */
    val instanceName: String,
    /** Minecraft 版本；null 表示版本清单还没读出来 */
    val minecraftVersion: String?,
    /** 加载器；null 表示原版或还没读出来 */
    val loaderLabel: String?,
    /** 游戏进程起跑的时刻；null 表示没有可用的起始时刻 */
    val startedAtMillis: Long?,
    /** 宿主给出的当前时刻 */
    val nowMillis: Long = 0L,
) {
    /** 已经过去多少秒；null 表示这一行不出现 */
    val elapsedSeconds: Int?
        get() = oxideGameWaitingElapsedSeconds(startedAtMillis, nowMillis)
}

/** 这一块上真正能按的动作 */
internal enum class OxideGameWaitingAction {
    /** 收起这一块，回到游戏画面 */
    Close,

    /** 结束这次游戏进程 */
    Cancel,
}

/**
 * 这一块上出现哪些按钮
 *
 * 收起永远给得出；结束游戏只有宿主真的接了回调时才出现。因此在
 * [dev.oxide.launcher.game.launch.handler.GameHandler] 现在这条链路上
 * 只有 [OxideGameWaitingAction.Close] 一个按钮——**不会出现一个按了没反应的键**。
 */
internal fun oxideGameWaitingActions(
    canClose: Boolean,
    cancelSupported: Boolean,
): List<OxideGameWaitingAction> = buildList {
    if (cancelSupported) add(OxideGameWaitingAction.Cancel)
    if (canClose) add(OxideGameWaitingAction.Close)
}

/**
 * 把秒数写成 `m:ss`
 *
 * 纯函数，只有两种写法：不到一小时是 `m:ss`，到了一小时是 `h:mm:ss`。
 * 负数按 0 处理（宿主给的时钟可能对不齐）。
 */
internal fun formatOxideElapsedSeconds(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, rest)
    } else {
        "%d:%02d".format(minutes, rest)
    }
}

/** 这一块卡片有多大 */
@Immutable
internal data class OxideGameWaitingBounds(
    val width: Dp,
    /** 卡片的绝对上限；这一块按内容高度摆，因此这里只是上限 */
    val maxHeight: Dp,
    val gutter: Dp,
) {
    /** 连同留白是否放得进窗口 */
    fun fitsWithin(windowWidth: Dp, windowHeight: Dp): Boolean =
        width + gutter * 2 <= windowWidth && maxHeight + gutter * 2 <= windowHeight
}

/**
 * 算出等待表面的大小
 *
 * 这一块是**按内容高度**摆的，因此高度只是一个"绝不超过窗口"的上限；
 * 宽度走"几个卡片最小宽度"，于是紧凑档更窄、放大后更宽，
 * 与启动器其余面板是同一套比例。
 */
internal fun oxideGameWaitingBounds(
    windowWidth: Dp,
    windowHeight: Dp,
    metrics: OxideMetrics,
): OxideGameWaitingBounds {
    val gutter = metrics.pagePaddingH
    return OxideGameWaitingBounds(
        width = minOf(
            metrics.cardMinWidth * OXIDE_GAME_WAITING_WIDTH_IN_CARD_UNITS,
            windowWidth - gutter * 2,
        ).coerceAtLeast(0.dp),
        maxHeight = (windowHeight - gutter * 2).coerceAtLeast(0.dp),
        gutter = gutter,
    )
}

/** 宽度上限：几个卡片最小宽度 */
private const val OXIDE_GAME_WAITING_WIDTH_IN_CARD_UNITS = 1.6f

/**
 * 游戏等待表面
 *
 * [facts.startedAtMillis] 非空时才开始走秒，而且计时器只在宿主不再需要这一块时
 * （离开组合）才停——它跟着的是这一块的生命周期，不是一个常驻动画。
 * 传 null 就完全没有计时器，面板上也就不会出现那个会自己动的数字。
 */
@Composable
internal fun OxideGameWaitingOverlay(
    metrics: OxideMetrics,
    facts: OxideGameWaitingFacts,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 只有宿主真的能结束游戏进程时才给 */
    onCancel: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Oxide.Motion.PopoverFadeMs)),
        exit = fadeOut(tween(Oxide.Motion.PopoverFadeMs)),
        modifier = modifier,
    ) {
        OxideGameWaitingCard(
            metrics = metrics,
            facts = facts,
            onClose = onClose,
            onCancel = onCancel,
        )
    }
}

/** 卡片本体；[AnimatedVisibility] 之外没有别的动画 */
@Composable
private fun OxideGameWaitingCard(
    metrics: OxideMetrics,
    facts: OxideGameWaitingFacts,
    onClose: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val startedAt = facts.startedAtMillis
    var elapsed by remember(startedAt) { mutableIntStateOf(0) }

    if (startedAt != null) {
        LaunchedEffect(startedAt) {
            while (true) {
                elapsed = oxideGameWaitingElapsedSeconds(startedAt, System.currentTimeMillis()) ?: 0
                delay(ELAPSED_TICK_MS)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val bounds = oxideGameWaitingBounds(maxWidth, maxHeight, metrics)

        Column(
            modifier = Modifier
                .width(bounds.width)
                .heightIn(max = bounds.maxHeight)
                .clip(Oxide.RadiusPanel)
                .background(Oxide.DrawerBg)
                .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel)
                .padding(metrics.cardGap),
            verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        ) {
            Text(
                text = stringResource(R.string.oxide_launch_game_title),
                color = Oxide.Fg,
                fontSize = Oxide.Type.DrawerTitle.fontSize,
                lineHeight = Oxide.Type.DrawerTitle.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = facts.instanceName,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            facts.minecraftVersion?.let {
                OxideGameWaitingFactRow(
                    label = stringResource(R.string.oxide_launch_game_version),
                    value = it,
                )
            }
            facts.loaderLabel?.let {
                OxideGameWaitingFactRow(
                    label = stringResource(R.string.oxide_launch_game_loader),
                    value = it,
                )
            }
            OxideGameWaitingFactRow(
                label = stringResource(R.string.oxide_launch_game_state),
                value = stringResource(
                    when (facts.state) {
                        OxideGameWaitingState.Waiting ->
                            R.string.oxide_launch_game_state_waiting

                        OxideGameWaitingState.FirstFrame ->
                            R.string.oxide_launch_game_state_first_frame
                    }
                ),
            )

            // 没有起始时刻就整行不出现，而不是显示一个从 0 假跑的计时器
            if (startedAt != null) {
                OxideGameWaitingFactRow(
                    label = stringResource(R.string.oxide_launch_game_elapsed),
                    value = formatOxideElapsedSeconds(elapsed),
                )
            }

            val actions = oxideGameWaitingActions(
                canClose = true,
                cancelSupported = onCancel != null,
            )
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(metrics.secRowGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = metrics.secRowGap,
                        alignment = Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        OxideButton(
                            text = when (action) {
                                OxideGameWaitingAction.Close ->
                                    stringResource(R.string.generic_close)

                                OxideGameWaitingAction.Cancel ->
                                    stringResource(R.string.oxide_launch_game_cancel)
                            },
                            onClick = when (action) {
                                OxideGameWaitingAction.Close -> onClose
                                OxideGameWaitingAction.Cancel -> onCancel ?: {}
                            },
                            tone = if (action == OxideGameWaitingAction.Cancel) {
                                OxideButtonTone.Secondary
                            } else {
                                OxideButtonTone.Primary
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 计时器的步进：一秒。取值本身就是"已经过去多少秒"的可观测量，不需要更细 */
private const val ELAPSED_TICK_MS = 1000L

/** 一行「标签 + 值」 */
@Composable
private fun OxideGameWaitingFactRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
        )
        Text(
            text = value,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}