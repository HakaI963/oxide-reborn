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

package dev.oxide.launcher.ui.screens.game.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import dev.oxide.launcher.ui.components.FloatingBall
import dev.oxide.launcher.ui.theme.Oxide
import kotlin.math.roundToInt

/**
 * 游戏内悬浮球
 *
 * 它同时是三样东西：菜单开关、帧率读数、内存读数。因此这一块必须**读得清**——
 * 它压在任意一帧游戏画面上，底下可能是雪地也可能是纯黑。
 *
 * 换掉的是外壳与配色，行为一字未改：
 *
 * - 拖动、抬手保存位置、非拖动判定成点击，全部仍由 [FloatingBall] 负责，
 *   球的初始位置与夹取范围也没有动过，因此玩家已经放好的位置不会跑。
 * - 菜单图标那一块仍然是 28dp，图标 24dp。改小改大都会让已经拖到位的球
 *   变成另一个手感。
 * - 帧率与内存的**真实值**照旧每秒刷新，图表模式照旧画 15 个时间节点的折线。
 *   显示哪几块由 [gameBallReadouts] 一处决定，不再散在组合里。
 *
 * 原来这块是用户可见的默认界面（`AllSettings.showFPS` 默认开），却整块用的是
 * `MaterialTheme`：`primary` 跟着动态取色走，`surfaceVariant` 在近黑面板上糊成
 * 一块灰，25% 的黑底在雪地场景下基本看不见。现在换成 [Oxide.PopoverBg]
 * （98% 不透明）与中性前景色，因此在任何画面上都读得出来。
 */
@Composable
fun DraggableGameBall(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    onSavePos: () -> Unit,
    gameFps: Int?,
    fpsDisplayMode: FpsDisplayMode,
    fpsHistory: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    showMemory: Boolean,
    opened: Boolean,
    alpha: Float = 1f,
    onClick: () -> Unit = {},
) {
    val description = stringResource(R.string.oxide_ingame_ball_menu)
    val openedText = stringResource(R.string.oxide_ingame_ball_opened)
    val closedText = stringResource(R.string.oxide_ingame_ball_closed)

    // 图的尺寸要跟着游戏窗口收，而窗口就是这一层的范围
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val chartSize = remember(maxWidth, maxHeight) {
            gameFpsChartSize(maxWidth.value.roundToInt(), maxHeight.value.roundToInt())
        }

        FloatingBall(
            modifier = Modifier
                .focusProperties { canFocus = false }
                .semantics {
                    // 整个球是一个按钮；帧率与内存是各自独立的节点，
                    // 因此这里只说明"这是什么、开了没有"，不把读数吞进来
                    role = Role.Button
                    contentDescription = description
                    stateDescription = if (opened) openedText else closedText
                },
            position = position,
            onPositionChanged = onPositionChanged,
            onSavePos = onSavePos,
            onClick = onClick,
            alpha = alpha,
            // 98% 不透明：底下压的是任意一帧游戏画面
            color = Oxide.PopoverBg,
            contentColor = Oxide.Fg,
            shape = Oxide.RadiusBlock,
        ) {
            GameBallContent(
                gameFps = gameFps,
                fpsDisplayMode = fpsDisplayMode,
                fpsHistory = fpsHistory,
                fpsMax = fpsMax,
                fpsMin = fpsMin,
                showMemory = showMemory,
                opened = opened,
                chartSize = chartSize,
            )
        }
    }
}

@Composable
private fun GameBallContent(
    gameFps: Int?,
    fpsDisplayMode: FpsDisplayMode,
    fpsHistory: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    showMemory: Boolean,
    opened: Boolean,
    chartSize: GameFpsChartSize,
) {
    // 显示哪几块只有这一个出处，组合里不再散着判断
    val readouts = gameBallReadouts(gameFps, fpsDisplayMode, showMemory)

    Row(
        modifier = Modifier.padding(all = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = readouts.showMenuIcon
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(opened, label = "gameBallMenuIcon") { state ->
                    GameOverlayIcon(
                        painter = painterResource(
                            if (state) R.drawable.ic_menu_open else R.drawable.ic_menu
                        ),
                        // 图标只是装饰：球的按钮语义已经在外面那块上说明了
                        contentDescription = null,
                        size = 24.dp,
                        tint = Oxide.Fg,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = readouts.hasReadout
        ) {
            Spacer(Modifier.width(4.dp))
        }

        //实际内容
        Column(
            modifier = Modifier
                .wrapContentSize()
                .animateContentSize()
                .width(IntrinsicSize.Max)
        ) {
            BallVisibility(visible = readouts.hasReadout) {
                Spacer(Modifier.height(4.dp))
            }
            //帧率显示
            BallVisibility(visible = readouts.showFps) {
                if (readouts.showFpsChart) {
                    //帧率图表
                    FpsChart(
                        modifier = Modifier.padding(end = 4.dp),
                        history = fpsHistory,
                        fpsMax = fpsMax,
                        fpsMin = fpsMin,
                        chartSize = chartSize,
                    )
                } else {
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = "FPS: ${gameFps ?: 0}",
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        color = Oxide.Fg,
                        maxLines = 1,
                    )
                }
            }
            BallVisibility(visible = readouts.showFps && readouts.showMemory) {
                Spacer(Modifier.height(4.dp))
            }
            //内存显示
            BallVisibility(visible = readouts.showMemory) {
                GameMemoryReadout(
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            BallVisibility(visible = readouts.hasReadout) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** 读数出现/收起：从左侧展开，收起也收回左侧，视线不用来回跳 */
@Composable
private fun ColumnScope.BallVisibility(
    visible: Boolean,
    content: @Composable (AnimatedVisibilityScope.() -> Unit)
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandIn(expandFrom = Alignment.CenterStart) + fadeIn(),
        exit = shrinkOut(shrinkTowards = Alignment.CenterStart) + fadeOut(),
        content = content
    )
}