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

package dev.oxide.launcher.ui.screens.content.elements

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.SingletonImageLoader
import dev.oxide.launcher.R
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.plugin.ApkPlugin
import dev.oxide.launcher.game.plugin.natives.NativePluginManager
import dev.oxide.launcher.game.plugin.renderer.RendererPluginManager
import dev.oxide.launcher.game.renderer.RendererInterface
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.hasVulkanBackend
import dev.oxide.launcher.game.version.installed.utils.isBiggerVer
import dev.oxide.launcher.game.version.installed.utils.isLowerVer
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.BackgroundBlur
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.VideoPlayer
import dev.oxide.launcher.ui.screens.content.FirstLoginMenu
import dev.oxide.launcher.utils.canHandlePermission
import dev.oxide.launcher.utils.file.InvalidFilenameException
import dev.oxide.launcher.utils.file.checkFilenameValidity
import dev.oxide.launcher.utils.hasStoragePermission
import dev.oxide.launcher.utils.image.isGifFile
import dev.oxide.launcher.viewmodel.BackgroundViewModel
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.LaunchGameViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
sealed interface QuickPlay : Parcelable {
    /** 快速启动游玩存档  仅支持 1.20+ 23w14a+ */
    @Parcelize
    data class Save(val saveName: String): QuickPlay

    /** 快速启动游玩服务器 */
    @Parcelize
    data class Server(val serverAddress: String): QuickPlay
}

sealed interface LaunchGameOperation {
    data object None : LaunchGameOperation
    /** 没有安装版本/没有选中有效版本 */
    data object NoVersion : LaunchGameOperation
    /** 版本名称非法时 */
    data class InvalidVersionName(val th: InvalidFilenameException) : LaunchGameOperation
    /** 没有可用账号 */
    data object NoAccount : LaunchGameOperation

    /** 渲染器可配置，但需要用到文件管理权限 */
    data class RendererNoStoragePermission(
        val renderer: RendererInterface,
        val version: Version,
        val quickPlay: QuickPlay?
    ) : LaunchGameOperation

    /** 当前渲染器不支持选中版本 */
    data class UnsupportedRenderer(
        val renderer: RendererInterface,
        val version: Version,
        val quickPlay: QuickPlay?
    ): LaunchGameOperation

    /** 当前已加载的插件不支持选中的版本 */
    data class UnsupportedPlugins(
        val plugins: List<ApkPlugin>,
        val version: Version,
        val quickPlay: QuickPlay?
    ): LaunchGameOperation

    /** 尝试启动：启动前检查一些东西 */
    data class TryLaunch(
        val version: Version?,
        val quickPlay: QuickPlay? = null
    ) : LaunchGameOperation

    /** 账号凭据已被服务端拒绝，需要重新登录 */
    data class AccountRelogin(
        val account: Account,
        val version: Version,
        val quickPlay: QuickPlay?,
        val logging: Boolean = false,
        val error: Throwable? = null
    ) : LaunchGameOperation

    /** 账号刷新失败，可选择跳过刷新继续启动 */
    data class AccountRefreshFailed(
        val account: Account,
        val error: Throwable,
        val version: Version,
        val quickPlay: QuickPlay?
    ) : LaunchGameOperation

    /** 正式启动 */
    data class RealLaunch(
        val version: Version,
        val quickPlay: QuickPlay?,
        val skipAccountRefresh: Boolean = false
    ) : LaunchGameOperation
}

/**
 * 启动游戏的状态机
 *
 * 这里只负责**推进**流程：检查跑完就换下一个 operation，阶段跑完就交给
 * [dev.oxide.launcher.viewmodel.LaunchGameViewModel]。
 * 需要用户拿主意的那几步（版本名非法、渲染器或插件不支持、缺文件管理权限、
 * 账号重新登录、账号刷新失败）现在由 Oxide 启动页就地承接，
 * 对应 [dev.oxide.launcher.ui.screens.main.oxide.OxideLaunchPreflight]；
 * 因此这里不再有任何弹窗，两种呈现不可能同时出现。
 */
@Composable
fun LaunchGameOperation(
    activity: Activity,
    eventViewModel: EventViewModel,
    launchGameViewModel: LaunchGameViewModel,
    exitActivity: () -> Unit,
    ensureVulkanSupported: suspend (Version) -> Boolean,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    toAccountManageScreen: (FirstLoginMenu) -> Unit = {},
    toVersionManageScreen: () -> Unit = {}
) {
    val launchGameOperation by launchGameViewModel.launchGameOperation.collectAsStateWithLifecycle()

    when (val operation = launchGameOperation) {
        is LaunchGameOperation.None -> {}
        is LaunchGameOperation.NoVersion -> {
            LaunchedEffect(Unit) {
                eventViewModel.sendToast(androidText(R.string.game_launch_no_version))
                toVersionManageScreen()
                launchGameViewModel.updateOperation(LaunchGameOperation.None)
            }
        }
        // 启动页上的一行说明 + 一个取消键，见 OxideLaunchPreflight
        is LaunchGameOperation.InvalidVersionName -> {}
        is LaunchGameOperation.NoAccount -> {
            LaunchedEffect(Unit) {
                eventViewModel.sendToast(androidText(R.string.game_launch_no_account))
                toAccountManageScreen(FirstLoginMenu.NORMAL)
                launchGameViewModel.updateOperation(LaunchGameOperation.None)
            }
        }
        // 启动页上的一组选择（授权 / 仍然启动 / 取消），见 OxideLaunchPreflight
        is LaunchGameOperation.RendererNoStoragePermission -> {}
        is LaunchGameOperation.UnsupportedRenderer -> {}
        is LaunchGameOperation.UnsupportedPlugins -> {}
        is LaunchGameOperation.TryLaunch -> {
            LaunchedEffect(Unit) {
                val version = operation.version ?: run {
                    launchGameViewModel.updateOperation(LaunchGameOperation.NoVersion)
                    return@LaunchedEffect
                }

                try {
                    checkFilenameValidity(version.getVersionName())
                } catch (th: InvalidFilenameException) {
                    launchGameViewModel.updateOperation(LaunchGameOperation.InvalidVersionName(th))
                    return@LaunchedEffect
                }

                val quickPlay = operation.quickPlay

                AccountsManager.currentAccountFlow.value ?: run {
                    launchGameViewModel.updateOperation(LaunchGameOperation.NoAccount)
                    return@LaunchedEffect
                }

                Renderers.setCurrentRenderer(version.getRenderer())
                val currentRenderer = Renderers.getCurrentRenderer()

                val mcVer = version.getVersionInfo()!!.minecraftVersion

                // 设备完全支持 Vulkan 时跳过渲染器的版本支持检查
                if (!version.hasVulkanBackend() || !ensureVulkanSupported(version)) {
                    val isRendererUnsupported =
                        (currentRenderer.getMinMCVersion()?.let { mcVer.isLowerVer(it) } ?: false) ||
                                (currentRenderer.getMaxMCVersion()?.let { mcVer.isBiggerVer(it) } ?: false)

                    if (isRendererUnsupported) {
                        launchGameViewModel.updateOperation(LaunchGameOperation.UnsupportedRenderer(currentRenderer, version, quickPlay))
                        return@LaunchedEffect
                    }
                }

                val unsupportedPlugins = NativePluginManager.getCheckedPlugins().filter { plugin ->
                    (plugin.minMCVer?.let { mcVer.isLowerVer(it) } ?: false) ||
                            (plugin.maxMCVer?.let { mcVer.isBiggerVer(it) } ?: false)
                }
                if (unsupportedPlugins.isNotEmpty()) {
                    launchGameViewModel.updateOperation(LaunchGameOperation.UnsupportedPlugins(unsupportedPlugins, version, quickPlay))
                    return@LaunchedEffect
                }

                //为可配置的渲染器检查文件管理权限
                //前提：系统支持这个设置
                if (
                    canHandlePermission &&  !hasStoragePermission &&
                    RendererPluginManager.isConfigurablePlugin(version.getRenderer())
                ) {
                    launchGameViewModel.updateOperation(LaunchGameOperation.RendererNoStoragePermission(currentRenderer, version, quickPlay))
                    return@LaunchedEffect
                }

                //正式启动游戏
                launchGameViewModel.updateOperation(LaunchGameOperation.RealLaunch(version, quickPlay))
            }
        }
        // 微软账号：没有密码可填，重新走一遍设备码授权；第三方账号：重新填一次密码。
        // 两者都改成启动页上的一组选择 + 一个密码输入框，见 OxideLaunchPreflight
        is LaunchGameOperation.AccountRelogin -> {}
        // 三个结局（重试 / 跳过刷新 / 取消）都在启动页上，见 OxideLaunchPreflight
        is LaunchGameOperation.AccountRefreshFailed -> {}
        is LaunchGameOperation.RealLaunch -> {
            LaunchedEffect(Unit) {
                val version = operation.version
                val quickPlay = operation.quickPlay
                version.apply {
                    offlineAccountLogin = false
                    quickPlaySingle = quickPlay
                }
                launchGameViewModel.start(
                    activity = activity,
                    version = version,
                    exitActivity = exitActivity,
                    submitError = submitError,
                    quickPlay = quickPlay,
                    skipAccountRefresh = operation.skipAccountRefresh
                )
                launchGameViewModel.updateOperation(LaunchGameOperation.None)
            }
        }
    }
}

/**
 * 启动器背景图片/视频层
 */
@Composable
fun Background(
    viewModel: BackgroundViewModel,
    modifier: Modifier = Modifier,
    allowVideo: Boolean = true
) {
    val blur = AllSettings.backgroundBlur.state
    val opacity = AllSettings.launcherBackgroundOpacity.state
    val backgroundMode = AllSettings.backgroundBlurType.state == BackgroundBlur.Background
    val backgroundBlurEnabled = backgroundMode && blur > 0 && opacity < 100
    val layerCapture = !backgroundMode && blur > 0 && opacity < 100 && viewModel.isValid
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .then(
                if (backgroundBlurEnabled && Build.VERSION.SDK_INT >= 31) {
                    Modifier.blur(blur.dp)
                } else {
                    Modifier
                }
            )
            .backgroundCapture(
                store = viewModel,
                recordContent = layerCapture,
                blurRadiusPx = blur * density.density,
                whiteOverlayAlpha = if (backgroundBlurEnabled) whiteOverlayAlpha(blur) else 0f
            )
    ) {
        if (viewModel.isValid) {
            when {
                viewModel.isVideo && allowVideo -> {
                    VideoPlayer(
                        videoUri = Uri.fromFile(viewModel.backgroundFile),
                        modifier = Modifier.fillMaxSize(),
                        refreshTrigger = viewModel.refreshTrigger,
                        volume = AllSettings.videoBackgroundVolume.state / 100f
                    )
                }
                viewModel.isImage -> {
                    val blurred = viewModel.blurredBackground
                    if (backgroundBlurEnabled && Build.VERSION.SDK_INT < 31 && blurred != null) {
                        Image(
                            bitmap = blurred,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        BackgroundImage(
                            modifier = Modifier.fillMaxSize(),
                            imageFile = viewModel.backgroundFile,
                            refreshTrigger = viewModel.refreshTrigger
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundImage(
    refreshTrigger: Any,
    imageFile: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Shared application-wide loader instead of one per background image.
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val request = remember(refreshTrigger) {
        ImageRequest.Builder(context)
            .data(imageFile)
            .allowHardware(false)
            .crossfade(false)
            .build()
    }

    //GIF 的动画帧由绘制 → invalidateSelf → 再绘制的自续循环推进，
    //任何一帧失效丢失都会让动画永久冻结，由帧时钟显式逐帧驱动重绘，保证循环自愈
    val isAnimatedState = remember(refreshTrigger) { mutableStateOf(false) }
    LaunchedEffect(refreshTrigger) {
        isAnimatedState.value = withContext(Dispatchers.IO) {
            imageFile.isGifFile()
        }
    }
    val isAnimated = isAnimatedState.value

    val frameTick = remember { mutableIntStateOf(0) }
    if (isAnimated) {
        LaunchedEffect(Unit) {
            while (isActive) {
                withFrameNanos { }
                frameTick.intValue++
            }
        }
    }

    AsyncImage(
        modifier = modifier.then(
            if (isAnimated) {
                Modifier.drawBehind {
                    frameTick.intValue
                }
            } else {
                Modifier
            }
        ),
        model = request,
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}