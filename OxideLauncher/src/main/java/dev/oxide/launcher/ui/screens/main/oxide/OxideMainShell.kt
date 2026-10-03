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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version

/**
 * 页面可以调用的宿主动作
 *
 * 页面本身不碰导航栈，只需要描述"我想打开实例设置"或"我想打开某个链接"，
 * 具体怎么走由宿主决定。这样页面签名保持稳定，而所有真实回调仍然是唯一的。
 */
class OxideHostActions(
    val navigateTo: (OxidePage) -> Unit,
    val openVersionSettings: (Version) -> Unit,
    val openLink: (String) -> Unit,
    val openSettingsSection: (OxideSettingsSection) -> Unit,
    val openAccountManager: () -> Unit,
    val openDownloadCategory: (OxideDownloadCategory) -> Unit,
)

/** 设置页里可以直接打开的深层分类 */
enum class OxideSettingsSection {
    Renderer, Game, Control, Gamepad, Launcher, JavaManager, ControlManager, About,
}

/** Discover 里可以直接打开的下载分类，对应已有的下载嵌套栈 */
enum class OxideDownloadCategory {
    Game, ModPack, Mod, ResourcePack, Saves, Shaders, Favorites, SearchId,
}

val LocalOxideHostActions = staticCompositionLocalOf {
    OxideHostActions(
        navigateTo = {},
        openVersionSettings = {},
        openLink = {},
        openSettingsSection = {},
        openAccountManager = {},
        openDownloadCategory = {},
    )
}

/**
 * 把四个页面装进外壳的那一层
 *
 * 外壳本身不知道业务，这里负责：
 * - 持有导航状态，并在硬件返回时先退回第一个页面
 * - 通过 [LocalOxideHostActions] 把真实的后端回调交给页面
 * - 在顶栏右侧放回任务进度入口、文件管理器和联机入口
 *
 * 页面切换不经过 Navigation3 栈，所以切页不会重建 ViewModel；
 * 而账号管理、实例设置、关于等深层页面仍然是栈上的独立条目，
 * 原来怎么去现在还怎么去，功能没有减少。
 */
@Composable
fun OxideMainShell(
    openVersionSettings: (Version) -> Unit,
    openLink: (String) -> Unit,
    openSettingsSection: (OxideSettingsSection) -> Unit,
    openAccountManager: () -> Unit,
    openDownloadCategory: (OxideDownloadCategory) -> Unit,
    tasksRunning: Boolean,
    tasksExpanded: Boolean,
    onToggleTasks: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenMultiplayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = rememberOxideNavState()
    val metrics = rememberOxideMetrics()

    // 不在第一个页面时，返回键先退回首页；已经在首页就交还给上层
    BackHandler(enabled = nav.page != OxidePage.Home) {
        nav.goBack()
    }

    val actions = OxideHostActions(
        navigateTo = nav::go,
        openVersionSettings = openVersionSettings,
        openLink = openLink,
        openSettingsSection = openSettingsSection,
        openAccountManager = openAccountManager,
        openDownloadCategory = openDownloadCategory,
    )

    OxideShell(
        nav = nav,
        modifier = modifier.fillMaxSize(),
        metrics = metrics,
        topBarTrailing = {
            Row {
                if (tasksRunning || tasksExpanded) {
                    OxideButton(
                        text = stringResource(
                            if (tasksExpanded) R.string.oxide_topbar_hide_tasks
                            else R.string.oxide_topbar_tasks
                        ),
                        onClick = onToggleTasks,
                        tone = if (tasksExpanded) OxideButtonTone.Secondary else OxideButtonTone.Ghost,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                OxideButton(
                    text = stringResource(R.string.oxide_topbar_files),
                    onClick = onOpenFileManager,
                    tone = OxideButtonTone.Ghost,
                )
                Spacer(Modifier.width(6.dp))
                OxideButton(
                    text = stringResource(R.string.oxide_topbar_multiplayer),
                    onClick = onOpenMultiplayer,
                    tone = OxideButtonTone.Ghost,
                )
            }
        },
    ) { page ->
        CompositionLocalProvider(LocalOxideHostActions provides actions) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    OxidePage.Home ->
                        OxideHomePage(metrics = metrics, onNavigate = nav::go)

                    OxidePage.Instances ->
                        OxideInstancesPage(metrics = metrics, onNavigate = nav::go)

                    OxidePage.Discover ->
                        OxideDiscoverPage(metrics = metrics, onNavigate = nav::go)

                    OxidePage.Settings ->
                        OxideSettingsPage(metrics = metrics, onNavigate = nav::go)
                }
            }
        }
    }
}
