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
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.launcher.R
import dev.oxide.launcher.components.InstallableItem
import dev.oxide.launcher.ui.base.BaseScreen
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadge
import dev.oxide.launcher.ui.screens.main.oxide.OxideBadgeTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideSectionLabel
import dev.oxide.launcher.ui.screens.main.oxide.OxideSurface
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.animation.swapAnimateDpAsState
import dev.oxide.launcher.viewmodel.SplashBackStackViewModel

/**
 * 需要解压的组件清单
 *
 * 原来这里是 `BackgroundCard` + `Surface` + `CircularProgressIndicator` 一整套 Material，
 * 右边那一栏还挂着一个 `MarqueeText` 按钮——旧界面里最显眼的一种。现在两边都是 Oxide 的
 * 原语：左边一张面板列出每一项和它的状态，右边是说明与一个主按钮。
 *
 * 7:3 的两栏比例保持不变。横屏下左边留给清单、右边留给说明；竖屏时 7:3 仍然读得下去，
 * 因此不再为窄窗口另写一套。
 */
@Composable
fun UnpackScreen(
    items: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel,
    onAgreeClick: () -> Unit = {},
) {
    val metrics = rememberOxideMetrics()
    BaseScreen(
        screenKey = NormalNavKey.UnpackDeps,
        currentKey = screenViewModel.splashScreen.currentKey
    ) { isVisible ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = metrics.pagePaddingH,
                    end = metrics.pagePaddingH,
                    bottom = metrics.pagePaddingH,
                ),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardGap)
        ) {
            UnpackTaskList(
                isVisible = isVisible,
                items = items,
                metrics = metrics,
                modifier = Modifier
                    .weight(7f)
                    .fillMaxHeight()
            )

            UnpackActionPanel(
                isVisible = isVisible,
                metrics = metrics,
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                onAgreeClick = onAgreeClick
            )
        }
    }
}

/** 右栏：这一屏在做什么，以及唯一一个动作 */
@Composable
private fun UnpackActionPanel(
    isVisible: Boolean,
    metrics: OxideMetrics,
    onAgreeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var installing by remember { mutableStateOf(false) }

    val xOffset by swapAnimateDpAsState(
        targetValue = 40.dp,
        swapIn = isVisible,
        isHorizontal = true
    )

    OxideSurface(
        modifier = modifier.offset { IntOffset(x = xOffset.roundToPx(), y = 0) },
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_splash_preparing))
        Spacer(Modifier.height(metrics.cardGap * 0.5f))
        Text(
            modifier = Modifier.weight(1f),
            text = if (installing) {
                stringResource(R.string.splash_screen_installing)
            } else {
                stringResource(R.string.splash_screen_unpack_desc)
            },
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
        )
        Spacer(Modifier.height(metrics.cardGap))
        OxideButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.splash_screen_agree),
            enabled = !installing,
            tone = OxideButtonTone.Primary,
            onClick = {
                installing = true
                onAgreeClick()
            },
        )
    }
}

/** 左栏：每一项现在是什么状态 */
@Composable
private fun UnpackTaskList(
    isVisible: Boolean,
    items: List<InstallableItem>,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
) {
    val yOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )

    OxideSurface(
        modifier = modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap * 0.5f,
            vertical = metrics.cardGap * 0.5f,
        ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = metrics.cardGap * 0.25f),
        ) {
            items(items, key = { item -> item.name }) { item ->
                TaskItem(
                    item = item,
                    metrics = metrics,
                    modifier = Modifier.padding(vertical = metrics.cardGap * 0.25f)
                )
            }
        }
    }
}

/**
 * 一项组件
 *
 * 状态用一枚徽标而不是一个图标：图标要画四张矢量图，徽标是一行字，而且在紧凑宽度下
 * 不会被挤没。"正在装"那一档额外把任务自己的消息显示出来——那一句是进度，不是状态。
 */
@Composable
private fun TaskItem(
    item: InstallableItem,
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
) {
    val state by item.state.collectAsStateWithLifecycle()
    val message by item.task.taskMessage.collectAsStateWithLifecycle()

    OxideSurface(
        modifier = modifier.fillMaxWidth(),
        shape = Oxide.RadiusBlock,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap * 0.75f,
            vertical = metrics.cardGap * 0.6f,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when (state) {
                    InstallableItem.State.RUNNING -> message
                    else -> item.summary
                }
                detail?.let {
                    Text(
                        text = it,
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(metrics.cardGap * 0.5f))
            OxideBadge(
                text = stringResource(item.state.labelRes()),
                tone = item.state.badgeTone(),
            )
        }
    }
}

/** 状态徽标的文案：五种状态是五件不同的事，因此分开写，不共用一句"进行中" */
private fun InstallableItem.State.labelRes(): Int = when (this) {
    InstallableItem.State.NOT_STARTED -> R.string.oxide_splash_state_not_installed
    InstallableItem.State.PENDING -> R.string.oxide_splash_state_pending
    InstallableItem.State.RUNNING -> R.string.oxide_splash_state_running
    InstallableItem.State.FINISHED -> R.string.oxide_splash_state_finished
    InstallableItem.State.NOT_EXISTS -> R.string.oxide_splash_state_missing
}

/** 只有"正在装"与"拿不到"值得吸引视线；其余保持安静 */
private fun InstallableItem.State.badgeTone(): OxideBadgeTone = when (this) {
    InstallableItem.State.RUNNING -> OxideBadgeTone.Active
    InstallableItem.State.NOT_EXISTS -> OxideBadgeTone.Warn
    else -> OxideBadgeTone.Neutral
}
