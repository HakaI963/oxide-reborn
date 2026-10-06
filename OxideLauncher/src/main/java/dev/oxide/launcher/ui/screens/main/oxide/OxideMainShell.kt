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

import androidx.compose.foundation.background
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskHistory
import dev.oxide.launcher.coroutine.TaskSystem
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.viewmodel.LaunchGameViewModel

/**
 * 页面可以调用的宿主动作
 *
 * 页面本身不碰导航栈，只需要描述"我想打开实例设置"或"我想打开某个链接"，
 * 具体怎么走由宿主决定。这样页面签名保持稳定，而所有真实回调仍然是唯一的。
 */
class OxideHostActions(
    val navigateTo: (OxidePage) -> Unit,
    /** 打开 Oxide 自己的实例设置整块标签页宿主（概览 / 配置 / 五类内容） */
    val openInstanceSettings: (Version) -> Unit,
    /**
     * 打开 Oxide 自己的"修改实例"表面（目标版本 → 加载器 → 改名与图标 → 执行）
     *
     * 这一块现在由本文件渲染，因此不再推进旧的 `NestedNavKey.VersionSettings`
     * 嵌套栈；接口形状保持不变，页面只描述意图。
     */
    val openVersionModify: (Version) -> Unit,
    /** 打开实例的五类内容（模组、资源包、光影、存档、截图），落在指定的那一类上 */
    val openInstanceContent: (Version, OxideContentCategory) -> Unit,
    val openLink: (String) -> Unit,
    val openSettingsSection: (OxideSettingsSection) -> Unit,
    val openAccountManager: () -> Unit,
    val openDownloadCategory: (OxideDownloadCategory) -> Unit,
    /**
     * 打开发现页，并落到 [request] 说的那个项目（或那次搜索）上
     *
     * 实例内容管理器里的"在发现页查看"走这里：**类别跟着内容类别走**，
     * 所以资源包落到资源包那一栏、光影落到光影那一栏，而不是一律落到模组。
     * 请求是纯数据，因此外壳只需要把它递给发现页，不必知道发现页内部怎么用。
     */
    val openDiscoverProject: (OxideDiscoverRequest) -> Unit,
    /** 打开 Oxide 自己的文件页，根目录是给定的绝对路径 */
    val openFiles: (String) -> Unit,
    /** 打开 Oxide 自己的日志页；[initialLogPath] 非空时直接选中那一份 */
    val openLog: (String?) -> Unit,
    /** 用 Oxide 自己的三步向导导出这个实例的整合包 */
    val openVersionExport: (Version) -> Unit,
    /**
     * 打开任务面板
     *
     * 这不是"切换"而是"打开"：调用方（发现页右下角那条安装提示）没有面板状态可读，
     * 它要的只是把面板叫出来。真正的展开态仍然只有设置里那一个
     * （`AllSettings.launcherTaskMenuExpanded`），因此这一条与顶栏按钮写的是同一个值，
     * 不存在两处真相。
     */
    val openTaskPanel: () -> Unit,
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
        openInstanceSettings = {},
        openVersionModify = {},
        openInstanceContent = { _, _ -> },
        openLink = {},
        openSettingsSection = {},
        openAccountManager = {},
        openDownloadCategory = {},
        openDiscoverProject = {},
        openFiles = {},
        openLog = {},
        openVersionExport = {},
        openTaskPanel = {},
    )
}

/**
 * 侧栏四个页面之外的目的地
 *
 * 账号、联机、文件、日志都不在侧栏里：它们是从顶栏动作或页面内入口打开的
 * 整块表面，而不是第五、第六个侧栏页。实例设置与实例的五个内容管理页同理。
 * 因此它们盖在页面区之上，硬件返回先关掉当前这一块，再退回首页。
 */
sealed interface OxideDestination {
    data object Account : OxideDestination
    data object Multiplayer : OxideDestination
    data class Files(val rootPath: String) : OxideDestination
    data class Log(val initialLogPath: String?) : OxideDestination

    /** 装一个 Minecraft 版本：选版本 → 选加载器与 API → 命名并安装 */
    data object InstallVersion : OxideDestination

    /** 把某个实例导出成整合包：格式 → 元数据 → 挑文件 */
    data class ExportModpack(val versionPath: String) : OxideDestination

    /** 修改一个已安装的实例：目标版本 → 加载器 → 改名与图标 → 执行 */
    data class ModifyVersion(val versionPath: String) : OxideDestination

    /** 实例的概览 / 配置 / 五类内容那一整块标签页宿主 */
    data class InstanceSettings(val versionPath: String) : OxideDestination

    /** 实例的某一类内容，[category] 决定落在哪一栏 */
    data class InstanceContent(
        val versionPath: String,
        val category: OxideContentCategory,
    ) : OxideDestination
}

/**
 * 把四个页面装进外壳的那一层
 *
 * 外壳本身不知道业务，这里负责：
 * - 持有导航状态，并在硬件返回时先退回第一个页面
 * - 通过 [LocalOxideHostActions] 把真实的后端回调交给页面
 * - 在顶栏右侧放回任务进度入口、文件管理器、联机入口与账号入口
 * - 承载 [OxideDestination] 那几块整页表面，并在启动流程跑起来时
 *   把 [OxideLaunchPage] 压在最上面
 *
 * 目的地与任务抽屉都记在这一层，比页面活得久：换页时它们自己不会被清掉，于是会盖在
 * 新页面上。因此这两块的可见性按 [OxideNavState.epoch] 判——见 [oxideTransientSurface]。
 *
 * 页面切换不经过 Navigation3 栈，所以切页不会重建 ViewModel。
 * 账号管理、实例设置、关于等仍然是栈上的独立条目，但**账号**这一项现在
 * 进的是 Oxide 自己的账号页，因此旧的 Zalith 账号界面从新界面不再可达。
 */
@Composable
fun OxideMainShell(
    openLink: (String) -> Unit,
    openSettingsSection: (OxideSettingsSection) -> Unit,
    openDownloadCategory: (OxideDownloadCategory) -> Unit,
    tasks: List<Task>,
    tasksExpanded: Boolean,
    onToggleTasks: () -> Unit,
    /**
     * 把任务面板打开（不是切换）
     *
     * 宿主把它递进来是为了让页面也能叫出面板——见 [OxideHostActions.openTaskPanel]。
     * 它与顶栏按钮的关系是"同一件事的两个入口"：两者写的都是同一个设置，
     * 所以按顶栏按钮关掉之后，页面再叫一次仍然是打开的。
     */
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = rememberOxideNavState()
    val metrics = rememberOxideMetrics()

    // MainActivity 在启动前弹窗里需要跳到某个主页（例如没有可用实例时去"实例"页），
    // 但它拿不到外壳的导航状态，所以走事件流，不把导航对象泄漏进 Activity。
    val navEventViewModel = rememberOxideEventViewModel()
    LaunchedEffect(navEventViewModel) {
        navEventViewModel.events.collect { event ->
            if (event is EventViewModel.Event.ShowLauncherPage) {
                OxidePage.entries.getOrNull(event.page)?.let(nav::go)
            }
        }
    }

    // MainActivity 持有同一个 LaunchGameViewModel；这里取到的就是那一个，
    // 因此按 Play 之后这一屏显示的是真实启动阶段，取消按钮也作用在同一条链路上
    val launchViewModel: LaunchGameViewModel = viewModel()
    val launchFlow by launchViewModel.launchFlow.collectAsStateWithLifecycle()
    // 启动前置检查发生在 launchFlow 出现**之前**：按下 Play 到第一个阶段之间有一段真实窗口
    // （检查链里有挂起的 ensureVulkanSupported），旧对话框就正好在那段窗口里弹出来。
    // 所以这一屏不能只看 launchFlow，否则前置阶段会掉回旧界面。
    val launchOperation by launchViewModel.launchGameOperation.collectAsStateWithLifecycle()

    // 目的地属于"打开它的那一页"：这里连同打开时的导航世代一起记下来。
    // 只记目的地是不够的——它记在外壳上，比页面活得久，换一次页既不会有人清掉它，
    // 它又会作为页面区的第二个子节点继续画在新页面上（见下面 AnimatedVisibility 那一处）。
    // 于是读取方一律看 [liveDestination] 而不是 [destination]。
    var destination by remember { mutableStateOf<OxideDestination?>(null) }
    var destinationEpoch by remember { mutableStateOf(nav.epoch) }

    // 待处理的「在发现页查看」请求；发现页消费掉之后就被清成 null，
    // 于是它不会被下一次重组重放，也不需要任何额外的去重标记
    var discoverRequest by remember { mutableStateOf<OxideDiscoverRequest?>(null) }

    /** 打开一块目的地：世代与目的地一起写，读的那一侧只认 [liveDestination] */
    val openDestination: (OxideDestination) -> Unit = { target ->
        destinationEpoch = nav.epoch
        destination = target
    }

    // 换页同一帧就作废：世代对不上就是没打开，返回键优先级与动画可见性一起跟着回去
    val liveDestination = oxideTransientSurface(destination, destinationEpoch, nav.epoch)

    // 任务历史住在任务系统这个进程级单例上，因此关掉抽屉、点掉发现页那条提示、
    // 换页、旋转都不会让它消失。面板自己一份历史也不留：它一旦从组合里消失，
    // 记在它内部的 remember 也就没了，所以这一份只能由这里读回来再喂给它。
    val taskHistory: List<TaskHistory> by TaskSystem.historyFlow.collectAsStateWithLifecycle()

    // 实例相关的目的地按路径记住，而不是按 Version 对象：
    // 版本改名之后路径会变，下面的 LaunchedEffect 会顺手把那一块收掉
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()

    // 不在第一个页面、也没有打开任何目的地时，返回键先退回首页；
    // 后注册的这一层优先，因此打开目的地时返回先关目的地
    BackHandler(enabled = nav.page != OxidePage.Home && liveDestination == null) {
        nav.goBack()
    }
    BackHandler(enabled = liveDestination != null) {
        destination = null
    }

    // 版本被改名或删除之后，目的地引用的那条路径已经不存在了，收起那一块
    val livePaths = remember(versions) {
        versions.map { version -> version.getVersionPath().absolutePath }.toSet()
    }
    LaunchedEffect(livePaths, liveDestination) {
        val path = when (val current = liveDestination) {
            is OxideDestination.InstanceSettings -> current.versionPath
            is OxideDestination.InstanceContent -> current.versionPath
            is OxideDestination.ExportModpack -> current.versionPath
            is OxideDestination.ModifyVersion -> current.versionPath
            else -> null
        }
        // 列表还没探完时"一个都看不到"不代表这个版本被删了，因此要等它非空
        if (path != null && livePaths.isNotEmpty() && path !in livePaths) {
            destination = null
        }
    }

    fun versionAt(path: String): Version? =
        versions.firstOrNull { version -> version.getVersionPath().absolutePath == path }

    val actions = OxideHostActions(
        navigateTo = nav::go,
        // 「完整实例设置」现在是 Oxide 自己那一屏，而不是旧的标签页宿主
        openInstanceSettings = { version ->
            openDestination(
                OxideDestination.InstanceSettings(
                    versionPath = version.getVersionPath().absolutePath,
                )
            )
        },
        // 「修改版本 / 选择版本」现在由 Oxide 自己那一屏负责，改名成功之后那一屏
        // 会因为路径消失而自己退回来，这里只需要换掉目的地
        openVersionModify = { version ->
            openDestination(
                OxideDestination.ModifyVersion(
                    versionPath = version.getVersionPath().absolutePath,
                )
            )
        },
        openInstanceContent = { version, category ->
            openDestination(
                OxideDestination.InstanceContent(
                    versionPath = version.getVersionPath().absolutePath,
                    category = category,
                )
            )
        },
        openLink = openLink,
        openSettingsSection = openSettingsSection,
        openAccountManager = { openDestination(OxideDestination.Account) },
        // 装 Minecraft 版本已经有 Oxide 自己的三步向导，因此这一类不再推进旧的下载嵌套栈；
        // 其余分类原样交给宿主，路径与旧界面完全一致
        openDownloadCategory = { category ->
            if (category == OxideDownloadCategory.Game) {
                openDestination(OxideDestination.InstallVersion)
            } else {
                openDownloadCategory(category)
            }
        },
        // 「在发现页查看」：先把请求放好再切页，发现页一组合起来就消费它。
        // 请求记在这一层（比发现页活得久）而不是塞进导航参数，因此换页/旋转都不会丢。
        openDiscoverProject = { request ->
            discoverRequest = request
            nav.go(OxidePage.Discover)
        },
        openFiles = { path -> openDestination(OxideDestination.Files(path)) },
        openLog = { path -> openDestination(OxideDestination.Log(path)) },
        openVersionExport = { version ->
            openDestination(
                OxideDestination.ExportModpack(
                    versionPath = version.getVersionPath().absolutePath,
                )
            )
        },
        openTaskPanel = onOpenTasks,
    )

    // ProvideOxideChrome 必须包在整棵 Oxide 树的根上：
    // 颜色是一次性递进来的，不递的话设置里换主题时界面不会跟着变
    ProvideOxideChrome {
        OxideShell(
            nav = nav,
            modifier = modifier.fillMaxSize(),
            metrics = metrics,
            topBarTrailing = {
                Row {
                    if (tasks.isNotEmpty() || tasksExpanded) {
                        OxideButton(
                            text = stringResource(
                                if (tasksExpanded) {
                                    R.string.oxide_topbar_hide_tasks
                                } else {
                                    R.string.oxide_topbar_tasks
                                }
                            ),
                            onClick = onToggleTasks,
                            tone = if (tasksExpanded) {
                                OxideButtonTone.Secondary
                            } else {
                                OxideButtonTone.Ghost
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    OxideButton(
                        text = stringResource(R.string.oxide_topbar_files),
                        onClick = { openDestination(OxideDestination.Files(DEVICES_ROOT)) },
                        tone = OxideButtonTone.Ghost,
                    )
                    Spacer(Modifier.width(6.dp))
                    OxideButton(
                        text = stringResource(R.string.oxide_topbar_multiplayer),
                        onClick = { openDestination(OxideDestination.Multiplayer) },
                        tone = OxideButtonTone.Ghost,
                    )
                    Spacer(Modifier.width(6.dp))
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_topbar_account),
                        onClick = { openDestination(OxideDestination.Account) },
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
                            OxideDiscoverPage(
                                metrics = metrics,
                                onNavigate = nav::go,
                                hostRequest = discoverRequest,
                                onHostRequestConsumed = { discoverRequest = null },
                            )

                        OxidePage.Settings ->
                            OxideSettingsPage(metrics = metrics, onNavigate = nav::go)
                    }

                    // 目的地：整块盖在页面区之上，退出时只淡出，不做位移。
                    // 可见性读的是 [liveDestination]：换页后它在同一帧就是"没打开"，
                    // 于是走的仍是原来那条淡出，新页面不会被盖住。
                    AnimatedVisibility(
                        visible = liveDestination != null,
                        enter = fadeIn(tween(Oxide.Motion.PopoverFadeMs)),
                        exit = fadeOut(tween(Oxide.Motion.PopoverFadeMs)),
                    ) {
                        val target = liveDestination ?: return@AnimatedVisibility
                        // 底幕统一走 OxideDestinationBackdrop：它先铺一层完全不透明的
                        // Oxide.Bg，再叠近不透明的 PanelBackdrop。单独铺 PanelBackdrop 还剩
                        // 约 5% 透出，页面标题那么粗，5% 在真机上仍然看得见，两块标题会重影。
                        OxideDestinationBackdrop {
                            when (target) {
                                is OxideDestination.Account -> OxideAccountPage(
                                    metrics = metrics,
                                    onDismiss = { destination = null },
                                )

                                is OxideDestination.Multiplayer -> OxideMultiplayerPage(
                                    metrics = metrics,
                                    onDismiss = { destination = null },
                                )

                                is OxideDestination.Files -> OxideFilesPage(
                                    metrics = metrics,
                                    rootPath = target.rootPath,
                                    onDismiss = { destination = null },
                                )

                                is OxideDestination.Log -> OxideLogPage(
                                    metrics = metrics,
                                    initialLogPath = target.initialLogPath,
                                    onDismiss = { destination = null },
                                )

                                is OxideDestination.InstallVersion -> OxideInstallVersionPage(
                                    metrics = metrics,
                                    onDismiss = { destination = null },
                                )

                                is OxideDestination.ExportModpack ->
                                    versionAt(target.versionPath)?.let { version ->
                                        OxideExportPage(
                                            metrics = metrics,
                                            version = version,
                                            onDismiss = { destination = null },
                                        )
                                    }

                                is OxideDestination.ModifyVersion ->
                                    versionAt(target.versionPath)?.let { version ->
                                        OxideModifyVersionPage(
                                            metrics = metrics,
                                            version = version,
                                            onDismiss = { destination = null },
                                        )
                                    }

                                is OxideDestination.InstanceSettings ->
                                    versionAt(target.versionPath)?.let { version ->
                                        OxideVersionSettingsPage(
                                            version = version,
                                            metrics = metrics,
                                            onDismiss = { destination = null },
                                            openModifyVersion = actions.openVersionModify,
                                        )
                                    }

                                is OxideDestination.InstanceContent ->
                                    versionAt(target.versionPath)?.let { version ->
                                        OxideContentManagerScreen(
                                            version = version,
                                            metrics = metrics,
                                            initialCategory = target.category,
                                            onDismiss = { destination = null },
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }

        // 任务抽屉：以前是 MainActivity 上那个 30% 宽的旧 Zalith 侧滑卡片，
        // 直接压在外壳上面。现在是 Oxide 自己的抽屉，和目的地同一层级。
        //
        // 展开与否本身是记在 AllSettings 里的，因此这里不写它：只按"它是哪一代导航下
        // 展开的"判一次。`remember(tasksExpanded)` 记下的就是展开那一刻的世代，
        // 换页之后世代对不上，抽屉这一帧就不再画，而下次按顶栏按钮它仍然是展开的。
        val tasksEpoch = remember(tasksExpanded) { nav.epoch }
        if (oxideTransientSurface(tasksExpanded, tasksEpoch, nav.epoch) == true) {
            CompositionLocalProvider(LocalOxideHostActions provides actions) {
                OxideTaskDrawer(
                    tasks = tasks,
                    metrics = metrics,
                    onDismiss = onToggleTasks,
                    history = taskHistory,
                )
            }
        }

        // 启动演示层：前置检查阶段或真的有一条启动流程时出现，压在所有目的地之上，
        // 因此无论当时停在哪个页面，按下 Play 之后看到的一定是这一屏
        if (oxideLaunchVisible(
                flowActive = launchFlow != null,
                operation = oxidePreflightOperationOf(launchOperation),
            )
        ) {
            CompositionLocalProvider(LocalOxideHostActions provides actions) {
                OxideLaunchPage(metrics = metrics)
            }
        }
    }
}

/**
 * 顶栏 "Files" 的默认根目录
 *
 * 与旧界面里那个图标按钮指向的目录完全一致：启动器自己的外部文件目录，
 * 也就是文件页"可访问范围"的上界。
 */
private val DEVICES_ROOT: String get() = PathManager.DIR_FILES_EXTERNAL.absolutePath