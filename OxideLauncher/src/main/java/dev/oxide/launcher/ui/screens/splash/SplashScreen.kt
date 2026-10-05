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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.R
import dev.oxide.launcher.components.InstallableItem
import dev.oxide.launcher.ui.components.OxideLogo
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import dev.oxide.launcher.ui.screens.rememberTransitionSpec
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.viewmodel.SplashBackStackViewModel

/**
 * 首启 / 依赖未就绪时的那一屏
 *
 * 它原本是上游那个居中的 Material 顶栏加一张 `BackgroundCard`，是新外壳里最后一处旧界面，
 * 而且它是**启动器的第一屏**：用户每次升级组件之后第一次看到的就是它。现在整屏都是 Oxide 的
 * ——品牌条由 [OxideLogo] 画，下面挂导航内容。
 *
 * 导航本身（[NormalNavKey.UnpackDeps] + [NavDisplay] + 进出场动画）一个字没动：那一层负责的
 * 是"这一步可见时把内容滑进来"，与长什么样无关。
 *
 * @param startAllTask 开启全部的解压任务
 * @param unpackItems 解压任务列表
 */
@Composable
fun SplashScreen(
    startAllTask: () -> Unit,
    unpackItems: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel,
) {
    val metrics = rememberOxideMetrics()
    ProvideOxideChrome {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Oxide.Bg)
        ) {
            BrandBar(metrics)
            NavigationUI(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                startAllTask = startAllTask,
                unpackItems = unpackItems,
                screenViewModel = screenViewModel
            )
        }
    }
}

/**
 * 品牌条
 *
 * 字标由 [OxideLogo] 画。GPLv3 §7(c) 要求修改版在启动界面标明自己不是官方版本，末尾那句
 * `launcher_modified_build_notice` 就是为此存在的——它是声明，因此放在字标之后当副标题，
 * 而不是像原来那样和顶栏标题并排成一句读不通的"OxideLauncher Oxide Launcher"。
 */
@Composable
private fun BrandBar(metrics: OxideMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.pagePaddingH, vertical = metrics.pagePaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
    ) {
        OxideLogo(markSize = metrics.topBarHeight * 0.72f)
        Text(
            text = stringResource(R.string.launcher_modified_build_notice),
            color = Oxide.FgGhost,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = BuildKeys.LAUNCHER_NAME,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Oxide.Line)
    )
}

@Composable
private fun NavigationUI(
    modifier: Modifier = Modifier,
    startAllTask: () -> Unit,
    unpackItems: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel,
) {
    val backStack = screenViewModel.splashScreen.backStack

    val currentKey = backStack.lastOrNull()
    LaunchedEffect(currentKey) {
        screenViewModel.splashScreen.currentKey = currentKey
    }

    if (backStack.isNotEmpty()) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<NormalNavKey.UnpackDeps> {
                    UnpackScreen(unpackItems, screenViewModel) {
                        startAllTask()
                    }
                }
            }
        )
    } else {
        Box(modifier)
    }
}
