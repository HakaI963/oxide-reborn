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
import dev.oxide.launcher.ui.screens.main.oxide.OxideBrowserClosesWhenSignInEnds
import dev.oxide.launcher.ui.screens.main.oxide.OxideBrowserPanel
import dev.oxide.launcher.ui.screens.main.oxide.OxideDestinationBackdrop
import dev.oxide.launcher.ui.screens.main.oxide.OxideLicencePanel
import dev.oxide.launcher.ui.screens.main.oxide.OxideDownloadCategory
import dev.oxide.launcher.ui.screens.main.oxide.OxideAboutPanel
import dev.oxide.launcher.ui.screens.main.oxide.OxideControlLayoutsPanel
import dev.oxide.launcher.ui.screens.main.oxide.globalOxideBrowser
import dev.oxide.launcher.ui.screens.main.oxide.oxideDestinationPanelInput
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import androidx.activity.compose.BackHandler
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.oxide.launcher.ui.components.TextRailItem
import dev.oxide.launcher.ui.screens.BackStackNavKey
import dev.oxide.launcher.ui.screens.NestedNavKey
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.TitledNavKey
import dev.oxide.launcher.ui.screens.content.AccountManageScreen
import dev.oxide.launcher.ui.screens.content.DownloadScreen
import dev.oxide.launcher.ui.screens.content.FileSelectorScreen
import dev.oxide.launcher.ui.screens.content.LauncherScreen
import dev.oxide.launcher.ui.screens.content.LogViewScreen
import dev.oxide.launcher.ui.screens.content.MultiplayerScreen
import dev.oxide.launcher.ui.screens.content.SettingsScreen
import dev.oxide.launcher.ui.screens.content.VersionExportScreen
import dev.oxide.launcher.ui.screens.content.VersionSettingsScreen
import dev.oxide.launcher.ui.screens.content.VersionsManageScreen
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

    // ---- Oxide 自有的两层盖板 ---------------------------------------------
    //
    // 协议全文与内置浏览器曾经各占一条旧导航条目（NormalNavKey.License 与
    // NormalNavKey.WebScreen），都是一整页旧 Zalith 界面：页顶是它自己上游的图标栏，
    // 底下那页照原样透上来，没有可见的关闭按钮。现在两者都是压在这棵导航树**之上**
    // 的一层，由这里统一承接——因此无论下面停在哪一页（Oxide 外壳、旧设置栈、
    // 旧下载页……），它们都是最上面那一层。
    //
    // 协议面板的状态用 remember 而不是 rememberSaveable，理由与外壳里那些整块面板
    // 一样（见 entry<NormalNavKey.LauncherMain> 里的说明）：它压在外面一层，
    // 旋转之后回到下面那一页比回到一块盖住的旧界面更符合预期。
    //
    // 内置浏览器是唯一的例外：设备码是一次性的授权，那一块状态挂在进程上
    // （见 globalOxideBrowser），因此旋转时授权页仍在，登录也不会被当成用户走开。
    var oxideLicenceRaw by remember { mutableStateOf<Int?>(null) }

    // 浏览器状态在进程上、界面在这里：读的是同一份状态，但只有这一处负责把它画出来
    val browserUrl by globalOxideBrowser.openUrl.collectAsStateWithLifecycle()
    val browserForSignIn by globalOxideBrowser.openedForSignIn.collectAsStateWithLifecycle()

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
                    tasks = tasks,
                    tasksExpanded = isTaskMenuExpanded,
                    onToggleTasks = ::changeTasksExpandedState,
                    onOpenLicence = { raw -> oxideLicenceRaw = raw },
                )

                // 两层盖板都声明在导航树之后，因此画在它上面；它们各自先铺一层
                // 完全不透明的面（OxideDestinationBackdrop），底下那一页一丝也透不过来
                OxideCapSurfaces(
                    licenceRaw = oxideLicenceRaw,
                    browserUrl = browserUrl,
                    browserForSignIn = browserForSignIn,
                    onDismissLicence = { oxideLicenceRaw = null },
                    onDismissBrowser = { globalOxideBrowser.close() },
                    openLink = { url ->
                        eventViewModel.sendEvent(EventViewModel.Event.OpenLink(url))
                    },
                )
            }
        }
    }
}

/**
 * Oxide 自有的两层盖板：协议全文与内置浏览器
 *
 * 放在宿主这一层而不是某个导航条目上，因此它们压得住**任何**下面那一页——包括旧
 * 设置栈的"关于"页面与旧下载页这些仍然可达、却还是旧 Zalith 界面的地方。
 *
 * 浏览器优先于协议面板：设备码登录是一段真实的等待，等待期间用户看的东西不该被
 * 另一块面板换掉。两块同时开着只可能是协议面板还没关又点了别的入口，那种情况下
 * 登录那一侧更重要。
 *
 * 返回键：浏览器开着就收浏览器（面板自己还带着返回键，它在组合里更靠后，因此优先），
 * 否则收协议面板。收掉之后这一次返回才轮到导航栈。
 */
@Composable
private fun OxideCapSurfaces(
    licenceRaw: Int?,
    browserUrl: String?,
    browserForSignIn: Boolean,
    onDismissLicence: () -> Unit,
    onDismissBrowser: () -> Unit,
    openLink: (String) -> Unit,
) {
    // 登录跑完（成功、失败，或者用户自己走开）就自己收起浏览器：旧实现里那一步是
    // backToMain() 把导航栈清回主界面，顺带把网页那一项弹掉
    OxideBrowserClosesWhenSignInEnds(
        openUrl = browserUrl,
        openedForSignIn = browserForSignIn,
        state = globalOxideBrowser,
    )
    BackHandler(enabled = licenceRaw != null && browserUrl == null) {
        onDismissLicence()
    }

    // 面板自带底色与调色板：外壳里的 ProvideOxideChrome 只覆盖它自己的子树
    ProvideOxideChrome {
        val url = browserUrl
        val raw = licenceRaw
        // 底幕吞掉落在面板外面的点击，但**不**因此关掉面板。这两件事必须分开：
        // 设备码登录正在轮询时，"浏览器不在了"被当作用户自己走开，于是这一次授权
        // 立刻被取消——一次误触不该有这么大后果。因此点外面什么也不发生，
        // 关掉它只有两条路：面板右上角的 ✕ 与硬件返回键。
        val scrim = Modifier.oxideDestinationPanelInput()
        when {
            url != null -> OxideDestinationBackdrop(modifier = scrim) {
                OxideBrowserPanel(
                    url = url,
                    forSignIn = browserForSignIn,
                    onDismiss = onDismissBrowser,
                    openLink = openLink,
                )
            }

            raw != null -> OxideDestinationBackdrop(modifier = scrim) {
                OxideLicencePanel(raw = raw, onDismiss = onDismissLicence)
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
    tasks: List<Task>,
    tasksExpanded: Boolean,
    onToggleTasks: () -> Unit,
    /**
     * 把任务面板打开（不是切换）
     *
     * 走的是同一个设置，所以"顶栏按钮关掉之后页面再叫一次"仍然是打开的。
     */
    onOpenTasks: () -> Unit,
    /**
     * 打开一份协议全文
     *
     * 协议全文曾经是 `NormalNavKey.License` 那条整页旧界面，现在由宿主那一层的
     * OxideLicencePanel 承接。这里只描述"要打开哪一份"，不自己决定打开在哪里。
     */
    onOpenLicence: (raw: Int) -> Unit,
) {
    val backStack = screenBackStackModel.mainScreen.backStack
    val currentKey = backStack.lastOrNull()

    LaunchedEffect(currentKey) {
        screenBackStackModel.mainScreen.currentKey = currentKey
    }

    // 旧启动器主界面的首次引导重播已经移除。它只被 LauncherScreen 的
    // LaunchedEffect 调过一次，而 LauncherScreen 自身在
    // `entry<NormalNavKey.LauncherMain>` 换成 OxideMainShell 之后就已经不再
    // 被任何地方渲染——它是一段只会指向旧主界面节点的引导，留着只能让
    // "重播引导"这个入口看起来还能用，实际点开是一串对不上位置的卡片。
    // 编辑器自己的引导与它无关，仍然保留。

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
                    // 由 Oxide 自己承接的整块设置面板（关于、控制布局管理）。
                    // 状态挂在 LauncherMain 这一条目上：它本来就在栈底，
                    // 因此面板一直开着直到用户主动关掉，返回键先关它。
                    // 用 remember 而不是 rememberSaveable：面板是压在外壳之上的一层，
                    // 与外壳自己那些整块表面（destination）一样属于临时状态，
                    // 旋转之后回到设置页比回到一块盖住的旧界面更符合预期
                    var oxidePanel by remember { mutableStateOf<OxideSettingsSection?>(null) }
                    BackHandler(enabled = oxidePanel != null) { oxidePanel = null }
                    // 面板由外壳同源的尺寸档位驱动，因此换档时它跟着一起缩放
                    val oxideMetrics = rememberOxideMetrics()

                    Box(modifier = Modifier.fillMaxSize()) {
                        OxideMainShell(
                            openLink = {
                                eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it))
                            },
                            openSettingsSection = { section ->
                                val key = section.navKey()
                                val panel = section.oxidePanel()
                                if (panel != null) {
                                    // Oxide 已经为这一类准备了整块面板：在外壳里盖上去，
                                    // 而不是把旧的 Zalith 设置栈推进来——那套界面带着它自己
                                    // 上游的图标顶栏与粉色强调轨，和新界面不是同一种语言。
                                    oxidePanel = panel
                                } else {
                                    oxidePanel = null
                                    if (key != null) {
                                        screenBackStackModel.mainScreen.removeAndNavigateTo(
                                            removes = screenBackStackModel.clearBeforeNavKeys,
                                            screenKey = screenBackStackModel.settingsScreen
                                        )
                                        screenBackStackModel.settingsScreen.navigateOnce(key)
                                    }
                                    // key 与 panel 都为 null 的几类（游戏、启动器，以及
                                    // Java、渲染器、控制、手柄）都已经由设置页自己那一栏
                                    // 展开，没有独立的整块面板，因此这里什么都不做
                                }
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
                            tasks = tasks,
                            tasksExpanded = tasksExpanded,
                            onToggleTasks = onToggleTasks,
                            // 页面（发现页那条安装提示）没有面板状态可读，因此给它的是
                            // "打开"而不是"切换"：两者写的是同一个设置
                            onOpenTasks = { AllSettings.launcherTaskMenuExpanded.save(true) },
                        )

                        // 面板盖在整块外壳之上，所以它自带底色与调色板：
                        // 外壳里的 ProvideOxideChrome 只覆盖它自己的子树
                        ProvideOxideChrome {
                            when (oxidePanel) {
                                OxideSettingsSection.About -> OxideAboutPanel(
                                    metrics = oxideMetrics,
                                    onDismiss = { oxidePanel = null },
                                    // 协议全文走 Oxide 自己的 [OxideLicencePanel]：
                                    // 关于面板只负责**列**协议（每一行一枚"读协议"按钮），
                                    // 它自己不渲染正文。旧实现推到 NormalNavKey.License
                                    // 那整页旧界面上去，而那一页已经不存在了
                                    openLicense = { raw -> onOpenLicence(raw) },
                                )

                                OxideSettingsSection.ControlManager -> OxideControlLayoutsPanel(
                                    metrics = oxideMetrics,
                                    onDismiss = { oxidePanel = null },
                                )

                                else -> {}
                            }
                        }
                    }
                }
                entry<NestedNavKey.Settings> { key ->
                    SettingsScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        // 旧设置栈的"关于"页面（AboutInfoScreen）仍然可达：旧下载页、
                        // 旧版本管理页的顶栏都能走进来。它那一枚"读协议"按钮同样不再
                        // 推开旧整页，而是打开 Oxide 自己的协议面板
                        openLicenseScreen = { raw ->
                            onOpenLicence(raw)
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
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

/**
 * 设置分类到已有导航键的映射
 *
 * 返回 null 的那几类不由旧的 Zalith 设置嵌套栈承接：一类是自己有面板（见
 * [oxidePanel]），另一类是已经由 Oxide 自己的设置页某一栏就地展开（Java、渲染器、
 * 控制与手柄）。不推进旧栈——那套界面带的是它自己上游的图标顶栏与粉色强调轨，
 * 与新界面不搭，而且它描述的是旧产品，不是这个。返回 null 就是"不要推进旧栈"
 * 这条决定本身，写在这里而不是在调用点判断，是为了让新增的分类必须当场表态。
 */
private fun OxideSettingsSection.navKey(): NormalNavKey.Settings? = when (this) {
    // 这四类现在都由 Oxide 自己的设置页承接：Java 与渲染器各有抽屉（见
    // OxideSettingsPage 的分类），控制与手柄整块在控制面板里展开
    // （OxideControlsPanel）。它们不需要独立的整块面板，因此这里一律返回 null，
    // 推进旧栈只会把用户从新设置页丢回旧界面
    OxideSettingsSection.Renderer -> null
    OxideSettingsSection.JavaManager -> null
    OxideSettingsSection.Control -> null
    OxideSettingsSection.Gamepad -> null
    // Oxide 自己有面板：关于、控制布局管理
    OxideSettingsSection.About -> null
    OxideSettingsSection.ControlManager -> null
    // 游戏与启动器两类就是设置页自己左栏里的分类，没有单独的整块面板；
    // 它们的每一项都已经是设置页里的行，不必也不该再开一块旧界面
    OxideSettingsSection.Game -> null
    OxideSettingsSection.Launcher -> null
}

/**
 * 由 Oxide 自己那块面板承接的分类
 *
 * 与 [navKey] 一样是一张写死的表：新增分类必须在这里或那里表态，
 * 不会出现"既没有旧栈也没有面板"的第三种结果被悄悄忽略。
 */
private fun OxideSettingsSection.oxidePanel(): OxideSettingsSection? = when (this) {
    OxideSettingsSection.About, OxideSettingsSection.ControlManager -> this
    else -> null
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
