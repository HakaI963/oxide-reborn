/*
 * Zalith Launcher 2
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

package dev.oxide.launcher.ui.screens.main

import dev.oxide.launcher.ui.screens.main.oxide.OxideSettingsSection
import dev.oxide.launcher.ui.screens.main.oxide.OxideMainShell
import dev.oxide.launcher.ui.screens.main.oxide.OxideDownloadCategory
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import dev.oxide.launcher.ui.theme.Oxide
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.BackgroundCard
import dev.oxide.launcher.ui.components.CardTitleLayout
import dev.oxide.launcher.ui.components.TextRailItem
import dev.oxide.launcher.ui.guide.sendStartGuideOnce
import dev.oxide.launcher.ui.screens.BackStackNavKey
import dev.oxide.launcher.ui.screens.NestedNavKey
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.TitledNavKey
import dev.oxide.launcher.ui.screens.content.AccountManageScreen
import dev.oxide.launcher.ui.screens.content.DownloadScreen
import dev.oxide.launcher.ui.screens.content.FileSelectorScreen
import dev.oxide.launcher.ui.screens.content.LauncherScreen
import dev.oxide.launcher.ui.screens.content.LicenseScreen
import dev.oxide.launcher.ui.screens.content.LogViewScreen
import dev.oxide.launcher.ui.screens.content.MultiplayerScreen
import dev.oxide.launcher.ui.screens.content.SettingsScreen
import dev.oxide.launcher.ui.screens.content.VersionExportScreen
import dev.oxide.launcher.ui.screens.content.VersionSettingsScreen
import dev.oxide.launcher.ui.screens.content.VersionsManageScreen
import dev.oxide.launcher.ui.screens.content.WebViewScreen
import dev.oxide.launcher.ui.screens.content.assetinfo.AssetInfoScreen
import dev.oxide.launcher.ui.screens.content.navigateToDownload
import dev.oxide.launcher.ui.screens.navigateTo
import dev.oxide.launcher.ui.screens.onBack
import dev.oxide.launcher.ui.screens.rememberTransitionSpec
import dev.oxide.launcher.ui.theme.backgroundColor
import dev.oxide.launcher.ui.theme.cardColor
import dev.oxide.launcher.ui.theme.festivals.FestivalTitleText
import dev.oxide.launcher.ui.theme.onBackgroundColor
import dev.oxide.launcher.ui.theme.onCardColor
import dev.oxide.launcher.utils.animation.getAnimateTween
import dev.oxide.launcher.utils.festival.LocalFestivals
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.LocalBackgroundViewModel
import dev.oxide.launcher.viewmodel.ModifyVersionViewModel
import dev.oxide.launcher.viewmodel.ModpackImportViewModel
import dev.oxide.launcher.viewmodel.ScreenBackStackViewModel
import dev.oxide.launcher.viewmodel.sendKeepScreen

@Composable
fun MainScreen(
    screenBackStackModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    modpackImportViewModel: ModpackImportViewModel,
    modifyVersionViewModel: ModifyVersionViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()

    //监控当前是否有任务正在进行
    LaunchedEffect(tasks) {
        if (tasks.isEmpty()) {
            eventViewModel.sendKeepScreen(false)
        } else {
            //有任务正在进行，避免熄屏
            eventViewModel.sendKeepScreen(true)
        }
    }

    val isTaskMenuExpanded = AllSettings.launcherTaskMenuExpanded.state

    fun changeTasksExpandedState() {
        AllSettings.launcherTaskMenuExpanded.save(!isTaskMenuExpanded)
    }

    /** 回到主页面通用函数 */
    val toMainScreen: () -> Unit = {
        screenBackStackModel.mainScreen.clearWith(NormalNavKey.LauncherMain)
    }

    val mainScreenKey = screenBackStackModel.mainScreen.currentKey
    val inLauncherScreen = mainScreenKey == null || mainScreenKey is NormalNavKey.LauncherMain

    val isBackgroundValid = LocalBackgroundViewModel.current?.isValid == true
    val launcherBackgroundOpacity = AllSettings.launcherBackgroundOpacity.state.toFloat() / 100f

    val backgroundColor = if (isBackgroundValid) {
        backgroundColor().copy(alpha = launcherBackgroundOpacity)
    } else backgroundColor()


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor,
        contentColor = onBackgroundColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 四个主页面的外壳自带顶栏；深层页面仍然是栈上的独立条目，保留原来的顶栏
            if (!inLauncherScreen) {
                TopBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    mainScreenKey = mainScreenKey,
                        inLauncherScreen = inLauncherScreen,
                    taskRunning = tasks.isEmpty(),
                    isTasksExpanded = isTaskMenuExpanded,
                    contentColor = onBackgroundColor(),
                    onScreenBack = {
                        screenBackStackModel.mainScreen.backStack.removeFirstOrNull()
                    },
                    toMainScreen = toMainScreen,
                    toSettingsScreen = {
                        screenBackStackModel.mainScreen.removeAndNavigateTo(
                            removes = screenBackStackModel.clearBeforeNavKeys,
                            screenKey = screenBackStackModel.settingsScreen
                        )
                    },
                    toDownloadScreen = {
                        screenBackStackModel.navigateToDownload()
                    },
                    toMultiplayerScreen = {
                        screenBackStackModel.mainScreen.removeAndNavigateTo(
                            removes = screenBackStackModel.clearBeforeNavKeys,
                            screenKey = NormalNavKey.Multiplayer
                        )
                    },
                    openFileManager = {
                        eventViewModel.sendEvent(
                            EventViewModel.Event.OpenFileManager(
                                rootPath = PathManager.DIR_FILES_EXTERNAL.absolutePath
                            )
                        )
                    },
                    changeExpandedState = {
                        changeTasksExpandedState()
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                NavigationUI(
                    modifier = Modifier.fillMaxSize(),
                    screenBackStackModel = screenBackStackModel,
                    toMainScreen = toMainScreen,
                    eventViewModel = eventViewModel,
                    modpackImportViewModel = modpackImportViewModel,
                    modifyVersionViewModel = modifyVersionViewModel,
                    submitError = submitError,
                    tasksRunning = tasks.isNotEmpty(),
                    tasksExpanded = isTaskMenuExpanded,
                    onToggleTasks = ::changeTasksExpandedState,
                )

                TaskMenu(
                    tasks = tasks,
                    isExpanded = isTaskMenuExpanded,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.3f)
                        .align(Alignment.CenterStart)
                        .padding(all = 6.dp)
                ) {
                    changeTasksExpandedState()
                }
            }
        }
    }
}

@Composable
private fun <E: TitledNavKey> TopBar(
    mainScreenKey: E?,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    isTasksExpanded: Boolean,
    modifier: Modifier = Modifier,
    contentColor: Color,
    onScreenBack: () -> Unit,
    toMainScreen: () -> Unit,
    toSettingsScreen: () -> Unit,
    toDownloadScreen: () -> Unit,
    toMultiplayerScreen: () -> Unit,
    openFileManager: () -> Unit,
    changeExpandedState: () -> Unit,
) {
    val festivals = LocalFestivals.current

    val inMultiplayerScreen = mainScreenKey is NormalNavKey.Multiplayer
    val inDownloadScreen = mainScreenKey is NestedNavKey.Download
    val inSettingsScreen = mainScreenKey is NestedNavKey.Settings

    CompositionLocalProvider(
        LocalContentColor provides contentColor
    ) {
        ConstraintLayout(modifier = modifier) {
            val (backCenter, title, endButtons) = createRefs()

            val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

            Row(
                modifier = Modifier
                    .constrainAs(backCenter) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    }
                    .fillMaxHeight()
            ) {
                AnimatedVisibility(
                    visible = !inLauncherScreen
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.width(12.dp))

                        IconButton(
                            modifier = Modifier.fillMaxHeight(),
                            onClick = {
                                if (!inLauncherScreen) {
                                    //不在主屏幕时才允许返回
                                    backDispatcher?.onBackPressed() ?: run {
                                        onScreenBack()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.generic_back)
                            )
                        }

                        IconButton(
                            modifier = Modifier.fillMaxHeight(),
                            onClick = {
                                if (!inLauncherScreen) {
                                    //不在主屏幕时才允许回到主页面
                                    toMainScreen()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_home_filled),
                                contentDescription = stringResource(R.string.generic_main_menu)
                            )
                        }
                    }
                }
            }
            val parentRes = mainScreenKey?.title
            val childRes = (mainScreenKey as? BackStackNavKey<*>)?.currentKey?.title

            Crossfade(
                modifier = Modifier.constrainAs(title) {
                    centerVerticallyTo(parent)
                    start.linkTo(backCenter.end, margin = 16.dp)
                },
                targetState = parentRes to childRes
            ) { (parent, child) ->
                val style = MaterialTheme.typography.titleMedium
                val softWarp = false
                val maxLines = 1

                if (parent == null) {
                    if (festivals.isEmpty()) {
                        Text(
                            text = BuildKeys.LAUNCHER_NAME,
                            style = style,
                            softWrap = softWarp,
                            maxLines = maxLines
                        )
                    } else {
                        FestivalTitleText(
                            festivals = festivals,
                            style = style,
                            maxLines = maxLines
                        )
                    }
                } else {
                    val titleText = if (child != null) {
                        androidText(parent, androidText(" - "), child)
                    } else {
                        parent
                    }

                    AndroidStringText(
                        text = titleText,
                        style = style,
                        softWrap = softWarp,
                        maxLines = maxLines
                    )
                }
            }

            Row(
                modifier = Modifier
                    .constrainAs(endButtons) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end, margin = 12.dp)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = !(isTasksExpanded || taskRunning),
                    enter = slideInVertically(
                        initialOffsetY = { -50 }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { -50 }
                    ) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .clip(shape = MaterialTheme.shapes.large)
                            .clickable { changeExpandedState() }
                            .padding(all = 8.dp)
                            .width(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(R.drawable.ic_assignment_filled),
                            contentDescription = stringResource(R.string.main_task_menu)
                        )
                    }
                }

                IconButton(
                    onClick = openFileManager
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_filled),
                        contentDescription = null
                    )
                }

                TopBarRailItem(
                    selected = inMultiplayerScreen,
                    painter = painterResource(R.drawable.ic_group_filled),
                    text = stringResource(R.string.terracotta),
                    onClick = {
                        if (!inMultiplayerScreen) toMultiplayerScreen()
                    },
                )

                TopBarRailItem(
                    selected = inDownloadScreen,
                    painter = painterResource(R.drawable.ic_download_2_filled),
                    text = stringResource(R.string.generic_download),
                    onClick = {
                        if (!inDownloadScreen) toDownloadScreen()
                    },
                )

                TopBarRailItem(
                    selected = inSettingsScreen,
                    painter = painterResource(R.drawable.ic_settings_filled),
                    text = stringResource(R.string.generic_setting),
                    onClick = {
                        if (!inSettingsScreen) toSettingsScreen()
                    },
                )
            }
        }
    }
}

@Composable
private fun TopBarRailItem(
    selected: Boolean,
    painter: Painter,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    textStyle: TextStyle = MaterialTheme.typography.labelMedium
) {
    TextRailItem(
        modifier = modifier,
        onClick = onClick,
        text = {
            AnimatedVisibility(visible = selected) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        style = textStyle
                    )
                }
            }
        },
        icon = {
            Icon(
                painter = painter,
                contentDescription = text
            )
        },
        selected = selected,
        selectedPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        unSelectedPadding = PaddingValues(all = 8.dp),
    )
}

@Composable
private fun NavigationUI(
    modifier: Modifier = Modifier,
    screenBackStackModel: ScreenBackStackViewModel,
    toMainScreen: () -> Unit,
    eventViewModel: EventViewModel,
    modpackImportViewModel: ModpackImportViewModel,
    modifyVersionViewModel: ModifyVersionViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    tasksRunning: Boolean,
    tasksExpanded: Boolean,
    onToggleTasks: () -> Unit,
) {
    val backStack = screenBackStackModel.mainScreen.backStack
    val currentKey = backStack.lastOrNull()

    LaunchedEffect(currentKey) {
        screenBackStackModel.mainScreen.currentKey = currentKey
    }

    if (backStack.isNotEmpty()) {
        /** 导航至版本详细信息屏幕；只有既有的版本管理页还会用到它 */
        val navigateToVersions: (Version) -> Unit = { version ->
            screenBackStackModel.mainScreen.navigateTo(
                screenKey = NestedNavKey.VersionSettings(version),
                useClassEquality = true
            )
        }
        /** 导航至整合包导出屏幕 */
        val navigateToExport: (Version) -> Unit = { version ->
            screenBackStackModel.mainScreen.removeAndNavigateTo(
                remove = NestedNavKey.VersionSettings::class,
                screenKey = NestedNavKey.VersionExport(version),
                useClassEquality = true
            )
        }

        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = {
                onBack(backStack)
            },
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<NormalNavKey.LauncherMain> {
                    OxideMainShell(
                        openLink = {
                            eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it))
                        },
                        openSettingsSection = { section ->
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                removes = screenBackStackModel.clearBeforeNavKeys,
                                screenKey = screenBackStackModel.settingsScreen
                            )
                            screenBackStackModel.settingsScreen.navigateOnce(section.navKey())
                        },
                        openDownloadCategory = { category ->
                            if (category == OxideDownloadCategory.SearchId) {
                                screenBackStackModel.navigateToDownload(NormalNavKey.SearchId)
                            } else {
                                screenBackStackModel.mainScreen.removeAndNavigateTo(
                                    removes = screenBackStackModel.clearBeforeNavKeys,
                                    screenKey = category.outerKey(screenBackStackModel),
                                    useClassEquality = true
                                )
                            }
                        },
                        tasksRunning = tasksRunning,
                        tasksExpanded = tasksExpanded,
                        onToggleTasks = onToggleTasks,
                    )
                }
                entry<NestedNavKey.Settings> { key ->
                    SettingsScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        openLicenseScreen = { raw ->
                            backStack.navigateTo(NormalNavKey.License(raw))
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.License> { key ->
                    LicenseScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel
                    )
                }
                entry<NormalNavKey.AccountManager> { key ->
                    AccountManageScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        backToMainScreen = toMainScreen,
                        openLink = { url ->
                            eventViewModel.sendEvent(EventViewModel.Event.OpenLink(url))
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.WebScreen> { key ->
                    WebViewScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel
                    )
                }
                entry<NormalNavKey.VersionsManager> {
                    VersionsManageScreen(
                        backScreenViewModel = screenBackStackModel,
                        navigateToVersions = navigateToVersions,
                        navigateToExport = navigateToExport,
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.FileSelector> { key ->
                    FileSelectorScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel
                    ) {
                        backStack.removeLastOrNull()
                    }
                }
                entry<NestedNavKey.VersionSettings> { key ->
                    VersionSettingsScreen(
                        key = key,
                        modifyViewModel = modifyVersionViewModel,
                        backScreenViewModel = screenBackStackModel,
                        backToMainScreen = toMainScreen,
                        onExportModpack = {
                            navigateToExport(key.version)
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NestedNavKey.VersionExport> { key ->
                    VersionExportScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        backToMainScreen = toMainScreen
                    )
                }
                entry<NestedNavKey.Download> { key ->
                    DownloadScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        modpackImportViewModel = modpackImportViewModel,
                        submitError = submitError
                    )
                }
                entry<NestedNavKey.AssetInfo> { key ->
                    AssetInfoScreen(
                        key = key,
                        mainScreenKey = screenBackStackModel.mainScreen.currentKey,
                        assetInfoScreenKey = key.currentKey,
                        eventViewModel = eventViewModel,
                        submitError = submitError,
                    )
                }
                entry<NormalNavKey.Multiplayer> {
                    MultiplayerScreen(
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel
                    )
                }
                entry<NormalNavKey.LogView> { key ->
                    LogViewScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                    )
                }
            }
        )
    } else {
        Box(modifier)
    }
}

@Composable
private fun TaskMenu(
    tasks: List<Task>,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    changeExpandedState: () -> Unit = {}
) {
    val show = isExpanded && tasks.isNotEmpty()

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedVisibility(
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { if (isRtl) it else -it },
            animationSpec = getAnimateTween()
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { if (isRtl) it else -it },
            animationSpec = getAnimateTween()
        ) + fadeOut(),
        visible = show
    ) {
        BackgroundCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 6.dp),
            influencedByBackground = false,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor(),
                contentColor = onBackgroundColor()
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Column {
                CardTitleLayout(blur = 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        IconButton(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.CenterStart),
                            onClick = changeExpandedState
                        ) {
                            Icon(
                                modifier = Modifier.size(28.dp),
                                painter = painterResource(R.drawable.ic_arrow_left_rounded),
                                contentDescription = stringResource(R.string.generic_collapse)
                            )
                        }

                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = stringResource(R.string.main_task_menu)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    items(tasks) { task ->
                        val taskProgress by task.progress.collectAsStateWithLifecycle()
                        val taskMessage by task.message.collectAsStateWithLifecycle()
                        val rateBytesPerSec by task.rateBytesPerSec.collectAsStateWithLifecycle()

                        TaskItem(
                            taskProgress = taskProgress,
                            taskMessage = taskMessage,
                            rateBytesPerSec = rateBytesPerSec,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            //取消任务
                            TaskSystem.cancelTask(task.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(
    taskProgress: Float,
    taskMessage: AndroidStringText?,
    rateBytesPerSec: Long?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = cardColor(false),
    contentColor: Color = onCardColor(),
    onCancelClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(all = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterVertically),
                onClick = onCancelClick
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.generic_cancel)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                taskMessage?.let { message ->
                    AndroidStringText(
                        text = message,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (taskProgress < 0) { //负数则代表不确定
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { taskProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    taskProgress.takeIf { it >= 0f }?.let { progress ->
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    rateBytesPerSec?.let { bytes ->
                        val text = remember(bytes) { "${formatFileSize(bytes)}/s" }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
/** 设置分类到已有导航键的映射：外壳不新增深层页面，只是把已有的接上 */
private fun OxideSettingsSection.navKey(): NormalNavKey.Settings = when (this) {
    OxideSettingsSection.Renderer -> NormalNavKey.Settings.Renderer
    OxideSettingsSection.Game -> NormalNavKey.Settings.Game
    OxideSettingsSection.Control -> NormalNavKey.Settings.Control
    OxideSettingsSection.Gamepad -> NormalNavKey.Settings.Gamepad
    OxideSettingsSection.Launcher -> NormalNavKey.Settings.Launcher
    OxideSettingsSection.JavaManager -> NormalNavKey.Settings.JavaManager
    OxideSettingsSection.ControlManager -> NormalNavKey.Settings.ControlManager
    OxideSettingsSection.About -> NormalNavKey.Settings.AboutInfo
}

/** 下载分类到已有的下载嵌套栈的映射，复用既有的分类入口 */
private fun OxideDownloadCategory.outerKey(model: ScreenBackStackViewModel): TitledNavKey = when (this) {
    OxideDownloadCategory.Game -> model.downloadGameScreen
    OxideDownloadCategory.ModPack -> model.downloadModPackScreen
    OxideDownloadCategory.Mod -> model.downloadModScreen
    OxideDownloadCategory.ResourcePack -> model.downloadResourcePackScreen
    OxideDownloadCategory.Saves -> model.downloadSavesScreen
    OxideDownloadCategory.Shaders -> model.downloadShadersScreen
    OxideDownloadCategory.Favorites -> model.downloadFavoritesScreen
    // SearchId 由上面的 navigateToDownload 处理，这里不会走到
    OxideDownloadCategory.SearchId -> model.downloadScreen
}
