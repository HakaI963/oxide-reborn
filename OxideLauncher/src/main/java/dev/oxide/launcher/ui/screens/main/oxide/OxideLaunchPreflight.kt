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

import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.account.accountErrorText
import dev.oxide.launcher.game.account.auth_server.AuthServerHelper
import dev.oxide.launcher.game.account.isMicrosoftAccount
import dev.oxide.launcher.game.account.microsoftLogin
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.content.elements.LaunchGameOperation
import dev.oxide.launcher.ui.screens.content.elements.getInvalidSummary
import dev.oxide.launcher.ui.screens.content.navigateToWeb
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.checkStoragePermissions
import dev.oxide.launcher.viewmodel.ErrorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.LaunchGameViewModel
import dev.oxide.launcher.viewmodel.ScreenBackStackViewModel
import dev.oxide.launcher.viewmodel.sendToast

/**
 * 启动前的检查：检查本身一个字都没有改，搬过来的只是**问法**
 *
 * 旧的做法是每一条检查各弹一个 Modal：`InvalidVersionName` / `UnsupportedRenderer` /
 * `UnsupportedPlugins` 三个 `SimpleAlertDialog`，存储权限一个 Material 提示，
 * 微软与第三方重新登录两个 Material `Dialog`，刷新失败又一个 Material `Dialog`。
 * 横屏里它们一张张盖下来，用的人既看不到自己刚才按了什么，也看不到启动器现在处在哪一步。
 *
 * 现在这一段属于启动页自己：原因与选择都就地写在启动页上，
 * 因此"按 Play 之后看到什么"只有一种答案——启动页。
 *
 * 下面这些纯函数是这个决定过程的全部内容：给定检查读到的状态，说出**停在哪一条**、
 * **问什么**、**给出哪些选择**。它们不碰 Compose、不碰 Context、不读磁盘，
 * 所以顺序与措辞都能在单元测试里逐条钉死。
 */

// ---------------------------------------------------------------------------
// 纯逻辑
// ---------------------------------------------------------------------------

/**
 * 启动前检查可能停在的地方
 *
 * 名字与 [LaunchGameOperation] 一一对应，因此"哪一条检查"这件事不需要再翻译一次。
 */
internal enum class OxidePreflightBranch {
    /** 什么都不用问，直接启动 */
    RealLaunch,

    /** 没有可用的实例 */
    NoVersion,

    /** 实例名非法 */
    InvalidVersionName,

    /** 没有可用账号 */
    NoAccount,

    /** 渲染器可配置，但缺文件管理权限 */
    RendererNoStoragePermission,

    /** 当前渲染器不支持选中的版本 */
    UnsupportedRenderer,

    /** 已加载的插件不支持选中的版本 */
    UnsupportedPlugins,

    /** 微软账号的凭据被拒，只能重新走一遍设备码授权 */
    AccountReloginMicrosoft,

    /** 第三方账号需要重新输入密码 */
    AccountReloginPassword,

    /** 账号刷新失败，可以跳过刷新继续启动 */
    AccountRefreshFailed,
}

/**
 * 启动前检查读到的全部输入
 *
 * [rendererSupported] 已经把"设备完全支持 Vulkan 时跳过渲染器版本检查"这一层折进来了：
 * 那一步是挂起调用（`VulkanChecker.ensureSupported`），它能不能返回属于宿主的活，
 * 因此这里只收"折完之后"的那个结论。
 */
internal data class OxidePreflightState(
    /** 有没有选中一个能启动的实例 */
    val hasVersion: Boolean,
    /** 实例名能不能通过 `checkFilenameValidity` */
    val versionNameValid: Boolean,
    /** 有没有可用账号 */
    val hasAccount: Boolean,
    /** 当前渲染器支不支持这个 Minecraft 版本 */
    val rendererSupported: Boolean,
    /** 不支持这个版本的插件名，非空就是有问题 */
    val unsupportedPluginNames: List<String>,
    /** 可配置的渲染器需要文件管理权限，而系统支持这个申请 */
    val needsStoragePermission: Boolean,
) {
    companion object {
        /** 一切都就绪：这就是"按 Play 之后什么都不用问"的那一种状态 */
        val Ready = OxidePreflightState(
            hasVersion = true,
            versionNameValid = true,
            hasAccount = true,
            rendererSupported = true,
            unsupportedPluginNames = emptyList(),
            needsStoragePermission = false,
        )
    }
}

/**
 * 按 `TryLaunch` 里的**原顺序**决定停在哪一条
 *
 * 顺序是这份逻辑的全部意义：实例 → 名字 → 账号 → 渲染器 → 插件 → 权限。
 * 例如"没有账号"和"渲染器不支持"同时成立时，旧实现先说没有账号，
 * 因为 `TryLaunch` 就是先 `AccountsManager.currentAccountFlow` 再去看渲染器。
 * 把这条顺序写在一个纯函数里（而不是散在 Composable 的分支里），
 * 它才是可以被逐条钉死的东西——界面上读到的是哪个 operation 就渲染哪一条，
 * 但"哪条检查先说"这件事由这里说了算。
 */
internal fun oxidePreflightBranch(state: OxidePreflightState): OxidePreflightBranch = when {
    !state.hasVersion -> OxidePreflightBranch.NoVersion
    !state.versionNameValid -> OxidePreflightBranch.InvalidVersionName
    !state.hasAccount -> OxidePreflightBranch.NoAccount
    !state.rendererSupported -> OxidePreflightBranch.UnsupportedRenderer
    state.unsupportedPluginNames.isNotEmpty() -> OxidePreflightBranch.UnsupportedPlugins
    state.needsStoragePermission -> OxidePreflightBranch.RendererNoStoragePermission
    else -> OxidePreflightBranch.RealLaunch
}

/**
 * 凭据被服务端拒绝之后的样子
 *
 * 与 [OxidePreflightBranch] 里的前六个不同：这三个不是"按下 Play 时的检查"，
 * 而是**真的跑起来了**之后由 [dev.oxide.launcher.game.launch.GameLaunchFlow] 回调回来的，
 * 那时阶段列表已经被取消，因此它们同样要由启动页承接。
 */
internal enum class OxideTokenState {
    /** 凭据仍然可用 */
    Fresh,

    /** 服务端拒绝了凭据，需要重新登录 */
    Rejected,

    /** 连不上认证服务器，可以选择跳过刷新 */
    RefreshFailed,
}

/** 用哪一套凭据 */
internal enum class OxideAccountKind {
    /** 微软账号：没有密码可填，只能重新走设备码 */
    Microsoft,

    /** 第三方认证服务器账号：重新填一次密码 */
    ThirdParty,
}

/**
 * 凭据这一段落在哪一条
 *
 * 与 [oxidePreflightBranch] 覆盖的六条不同：这三个不是"按下 Play 时的检查"，
 * 而是**真的跑起来了**之后由 [dev.oxide.launcher.game.launch.GameLaunchFlow] 回调回来的
 * （`onReloginRequired` / `onRefreshFailed`），那时阶段列表已经被取消，
 * 因此它们同样要由启动页承接。
 *
 * 微软账号与第三方账号在这里被分开：前者没有密码可填，问法只能是"重新登录"；
 * 后者要一行输入框。
 */
internal fun oxideTokenBranch(
    token: OxideTokenState,
    account: OxideAccountKind,
): OxidePreflightBranch = when (token) {
    OxideTokenState.Fresh -> OxidePreflightBranch.RealLaunch
    OxideTokenState.Rejected -> when (account) {
        OxideAccountKind.Microsoft -> OxidePreflightBranch.AccountReloginMicrosoft
        OxideAccountKind.ThirdParty -> OxidePreflightBranch.AccountReloginPassword
    }

    OxideTokenState.RefreshFailed -> OxidePreflightBranch.AccountRefreshFailed
}

/** 用户在这一条上能做的事 */
internal enum class OxidePreflightAction {
    /** 放弃这次启动，回到启动器（operation 置 None） */
    Abort,

    /** 知情继续启动（operation 置 RealLaunch） */
    LaunchAnyway,

    /** 去申请文件管理权限 */
    AuthorizeStorage,

    /** 重新走一遍微软设备码登录 */
    MicrosoftSignIn,

    /** 提交第三方账号密码重新登录 */
    SubmitPassword,

    /** 跳过账号刷新继续启动 */
    SkipRefresh,

    /** 重试账号刷新继续启动 */
    RetryRefresh,
}

/** 面板上那一句话是"哪种说法"：纯逻辑因此不需要 Context，措辞留给组合期 */
internal enum class OxidePreflightMessage {
    /** 不用问：这一条只会带着进度往前走 */
    None,

    InvalidVersionName,
    NoVersion,
    NoAccount,
    StoragePermission,
    UnsupportedRenderer,
    UnsupportedPlugins,
    ReloginMicrosoft,
    ReloginPassword,
    RefreshFailed,
}

/** 停在某一条上时，启动页要问用户的东西 */
internal data class OxidePreflightAsk(
    val branch: OxidePreflightBranch,
    val message: OxidePreflightMessage,
    /** 顺序就是按钮从上到下的顺序；空表示不用问 */
    val actions: List<OxidePreflightAction>,
    /** 是否要先读一行密码才能提交 */
    val requiresPasswordInput: Boolean,
)

/** 每一条检查在启动页上的问法 */
internal fun oxidePreflightAsk(branch: OxidePreflightBranch): OxidePreflightAsk = when (branch) {
    OxidePreflightBranch.RealLaunch -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.None,
        actions = emptyList(),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.NoVersion -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.NoVersion,
        actions = listOf(OxidePreflightAction.Abort),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.InvalidVersionName -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.InvalidVersionName,
        actions = listOf(OxidePreflightAction.Abort),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.NoAccount -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.NoAccount,
        actions = listOf(OxidePreflightAction.Abort),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.RendererNoStoragePermission -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.StoragePermission,
        // 申请与"仍然启动"都留着：旧的 Material 提示也是这两个按钮
        actions = listOf(
            OxidePreflightAction.AuthorizeStorage,
            OxidePreflightAction.LaunchAnyway,
            OxidePreflightAction.Abort,
        ),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.UnsupportedRenderer -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.UnsupportedRenderer,
        actions = listOf(OxidePreflightAction.LaunchAnyway, OxidePreflightAction.Abort),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.UnsupportedPlugins -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.UnsupportedPlugins,
        actions = listOf(OxidePreflightAction.LaunchAnyway, OxidePreflightAction.Abort),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.AccountReloginMicrosoft -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.ReloginMicrosoft,
        actions = listOf(
            OxidePreflightAction.MicrosoftSignIn,
            OxidePreflightAction.Abort,
        ),
        requiresPasswordInput = false,
    )

    OxidePreflightBranch.AccountReloginPassword -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.ReloginPassword,
        actions = listOf(OxidePreflightAction.SubmitPassword, OxidePreflightAction.Abort),
        requiresPasswordInput = true,
    )

    OxidePreflightBranch.AccountRefreshFailed -> OxidePreflightAsk(
        branch = branch,
        message = OxidePreflightMessage.RefreshFailed,
        // 三个结局：重试 / 跳过刷新 / 取消
        actions = listOf(
            OxidePreflightAction.RetryRefresh,
            OxidePreflightAction.SkipRefresh,
            OxidePreflightAction.Abort,
        ),
        requiresPasswordInput = false,
    )
}

/**
 * 只有一个"知道了"的动作时不摆按钮行
 *
 * 版本名非法这一条没有任何选择——旧实现给它的那个 `SimpleAlertDialog` 也只有一个
 * "取消"按钮。因此这里直接用既有的错误条，不为了一个按钮占掉一整行。
 */
internal fun oxidePreflightIsNoticeOnly(ask: OxidePreflightAsk): Boolean =
    !ask.requiresPasswordInput && ask.actions == listOf(OxidePreflightAction.Abort)

/**
 * 这两条本来就只弹一次 toast 并立刻复位
 *
 * 没有实例与没有账号在旧实现里走的是 toast 加一次跳转，operation 随即回到 None。
 * 因此启动页不该为它们闪一行出来——那一帧只会让人以为还有别的事没做完。
 */
internal fun oxidePreflightToastOnly(branch: OxidePreflightBranch): Boolean =
    branch == OxidePreflightBranch.NoVersion || branch == OxidePreflightBranch.NoAccount

/**
 * 启动流程此刻处在哪一段
 *
 * 这是 [LaunchGameOperation] 的一个**无副作用的投影**：
 * 全部信息都来自那个 sealed 类型的判别式，因此界面可以在不知道 `Version` / `Account`
 * 长什么样的前提下决定"现在该讲什么"。
 */
internal enum class OxidePreflightOperation {
    /** 没有人按下 Play，或者这一次启动已经结束 */
    Idle,

    /** 按下了 Play，检查正在跑，还没有得出任何结论 */
    Checking,

    /** 检查全部通过，真正开始启动 */
    RealLaunch,

    NoVersion,
    InvalidVersionName,
    NoAccount,
    RendererNoStoragePermission,
    UnsupportedRenderer,
    UnsupportedPlugins,
    AccountReloginMicrosoft,
    AccountReloginPassword,
    AccountRefreshFailed,
}

internal fun oxidePreflightOperationOf(operation: LaunchGameOperation): OxidePreflightOperation =
    when (operation) {
        is LaunchGameOperation.None -> OxidePreflightOperation.Idle
        is LaunchGameOperation.TryLaunch -> OxidePreflightOperation.Checking
        is LaunchGameOperation.RealLaunch -> OxidePreflightOperation.RealLaunch
        is LaunchGameOperation.NoVersion -> OxidePreflightOperation.NoVersion
        is LaunchGameOperation.InvalidVersionName -> OxidePreflightOperation.InvalidVersionName
        is LaunchGameOperation.NoAccount -> OxidePreflightOperation.NoAccount
        is LaunchGameOperation.RendererNoStoragePermission ->
            OxidePreflightOperation.RendererNoStoragePermission

        is LaunchGameOperation.UnsupportedRenderer ->
            OxidePreflightOperation.UnsupportedRenderer

        is LaunchGameOperation.UnsupportedPlugins -> OxidePreflightOperation.UnsupportedPlugins
        // 微软账号没有密码可填，第三方账号有：这一条决定的是"给不给输入框"
        is LaunchGameOperation.AccountRelogin -> if (operation.account.isMicrosoftAccount()) {
            OxidePreflightOperation.AccountReloginMicrosoft
        } else {
            OxidePreflightOperation.AccountReloginPassword
        }

        is LaunchGameOperation.AccountRefreshFailed ->
            OxidePreflightOperation.AccountRefreshFailed
    }

/** 只有这些需要问用户；其余是纯进度，不该出现任何选择 */
internal fun oxidePreflightBranchOf(operation: OxidePreflightOperation): OxidePreflightBranch? =
    when (operation) {
        OxidePreflightOperation.Idle,
        OxidePreflightOperation.Checking,
        OxidePreflightOperation.RealLaunch -> null

        OxidePreflightOperation.NoVersion -> OxidePreflightBranch.NoVersion
        OxidePreflightOperation.InvalidVersionName -> OxidePreflightBranch.InvalidVersionName
        OxidePreflightOperation.NoAccount -> OxidePreflightBranch.NoAccount
        OxidePreflightOperation.RendererNoStoragePermission ->
            OxidePreflightBranch.RendererNoStoragePermission

        OxidePreflightOperation.UnsupportedRenderer -> OxidePreflightBranch.UnsupportedRenderer
        OxidePreflightOperation.UnsupportedPlugins -> OxidePreflightBranch.UnsupportedPlugins
        OxidePreflightOperation.AccountReloginMicrosoft ->
            OxidePreflightBranch.AccountReloginMicrosoft

        OxidePreflightOperation.AccountReloginPassword ->
            OxidePreflightBranch.AccountReloginPassword

        OxidePreflightOperation.AccountRefreshFailed -> OxidePreflightBranch.AccountRefreshFailed
    }

/**
 * 启动演示层该不该盖上来
 *
 * 只看 [LaunchGameViewModel.launchFlow] 是不够的：按下 Play 到第一条阶段出现之间
 * 有一段实打实的空窗（[dev.oxide.launcher.game.launch.GameLaunchFlow] 之前的那一堆检查，
 * 其中 `ensureVulkanSupported` 还是挂起调用）。旧实现正是在这段空窗里弹出了
 * 那些 Modal，于是"按 Play 之后看到什么"取决于哪条检查先返回。
 *
 * 因此判据是**整段 pre-flight**：只要 operation 已经不是 None，或者阶段已经跑起来，
 * 启动页就该在——直到流程结束或被取消为止。
 */
internal fun oxideLaunchVisible(
    flowActive: Boolean,
    operation: OxidePreflightOperation,
): Boolean = flowActive || operation != OxidePreflightOperation.Idle

// ---------------------------------------------------------------------------
// 措辞
// ---------------------------------------------------------------------------

/** 面板上要写的三行字：标题、原因、以及可能存在的上一次失败 */
internal data class OxidePreflightTexts(
    val title: String,
    val detail: String,
    /** 上一次尝试的失败原因；没有就为 null */
    val failure: String? = null,
    /** 正在提交，因此输入与按钮都要禁用 */
    val busy: Boolean = false,
)

/**
 * 把一条检查翻译成面板上的话
 *
 * 标题一律用 Oxide 自己的 `oxide_launch_op_*`，而不是旧实现那个 `generic_warning`：
 * 「警告」不告诉人到底缺了什么，而「%1$s does not support this version」说的是同一件事。
 * 说明文字仍然用后端那些文案（`renderer_version_unsupported_warning` 等），
 * 也就是说检查为什么成立、用户被要求确认什么，仍然是后端在说话，界面只是不再弹窗。
 */
@Composable
internal fun launchPreflightTexts(operation: LaunchGameOperation): OxidePreflightTexts? =
    when (operation) {
        is LaunchGameOperation.InvalidVersionName -> OxidePreflightTexts(
            title = stringResource(R.string.oxide_launch_op_invalid_name),
            detail = operation.th.getInvalidSummary(),
        )

        is LaunchGameOperation.NoVersion -> OxidePreflightTexts(
            title = stringResource(R.string.oxide_launch_op_no_version),
            detail = stringResource(R.string.game_launch_no_version),
        )

        is LaunchGameOperation.NoAccount -> OxidePreflightTexts(
            title = stringResource(R.string.oxide_launch_op_no_account),
            detail = stringResource(R.string.game_launch_no_account),
        )

        is LaunchGameOperation.RendererNoStoragePermission -> OxidePreflightTexts(
            title = stringResource(R.string.oxide_launch_op_storage_permission),
            detail = stringResource(
                R.string.oxide_launch_storage_detail,
                operation.renderer.getRendererName(),
            ),
        )

        is LaunchGameOperation.UnsupportedRenderer -> OxidePreflightTexts(
            title = stringResource(
                R.string.oxide_launch_op_unsupported_renderer,
                operation.renderer.getRendererName(),
            ),
            detail = stringResource(
                R.string.renderer_version_unsupported_warning,
                operation.renderer.getRendererName(),
            ),
        )

        is LaunchGameOperation.UnsupportedPlugins -> OxidePreflightTexts(
            title = stringResource(R.string.oxide_launch_op_unsupported_plugins),
            detail = stringResource(
                R.string.plugin_unsupported_warning,
                operation.plugins.joinToString(", ") { it.appName },
            ),
        )

        is LaunchGameOperation.AccountRelogin -> if (operation.account.isMicrosoftAccount()) {
            OxidePreflightTexts(
                title = stringResource(R.string.oxide_launch_op_relogin, operation.account.username),
                detail = stringResource(R.string.account_relogin_microsoft_message),
            )
        } else {
            OxidePreflightTexts(
                title = stringResource(R.string.oxide_launch_op_relogin, operation.account.username),
                detail = stringResource(
                    R.string.account_relogin_password_message,
                    operation.account.username,
                ),
                failure = operation.error?.let { resolveAndroidString(accountErrorText(it)).text },
                busy = operation.logging,
            )
        }

        is LaunchGameOperation.AccountRefreshFailed -> OxidePreflightTexts(
            title = stringResource(
                R.string.oxide_launch_op_refresh_failed,
                operation.account.username,
            ),
            detail = stringResource(R.string.account_refresh_failed_skip_message),
            failure = resolveAndroidString(accountErrorText(operation.error)).text,
        )

        is LaunchGameOperation.None,
        is LaunchGameOperation.TryLaunch,
        is LaunchGameOperation.RealLaunch -> null
    }

// ---------------------------------------------------------------------------
// 后端：与被搬走的弹窗逐字一致
// ---------------------------------------------------------------------------

/**
 * 执行用户在启动页上做的那个决定
 *
 * 每一个分支都对应旧实现里被删掉的那一段：同样的 `updateOperation` 目标、
 * 同样的 `AuthServerHelper` 参数、同样的 `TaskSystem.submitTask`、
 * 同样的 `microsoftLogin` 回调。换掉的只有触发它的是一个按钮而不是一个弹窗。
 *
 * [password] 只有第三方账号那条分支会用到。
 */
internal fun performOxidePreflightAction(
    action: OxidePreflightAction,
    operation: LaunchGameOperation,
    password: String,
    activity: Activity?,
    eventViewModel: EventViewModel,
    errorViewModel: ErrorViewModel,
    launchGameViewModel: LaunchGameViewModel,
    backStack: ScreenBackStackViewModel?,
) {
    when (action) {
        OxidePreflightAction.Abort ->
            launchGameViewModel.updateOperation(LaunchGameOperation.None)

        OxidePreflightAction.LaunchAnyway -> when (operation) {
            is LaunchGameOperation.UnsupportedRenderer -> launchGameViewModel.updateOperation(
                LaunchGameOperation.RealLaunch(operation.version, operation.quickPlay),
            )

            is LaunchGameOperation.UnsupportedPlugins -> launchGameViewModel.updateOperation(
                LaunchGameOperation.RealLaunch(operation.version, operation.quickPlay),
            )

            // 用户拒绝授权但仍然允许启动：配置读不到，游戏照样起
            is LaunchGameOperation.RendererNoStoragePermission -> launchGameViewModel.updateOperation(
                LaunchGameOperation.RealLaunch(operation.version, operation.quickPlay),
            )

            else -> launchGameViewModel.updateOperation(LaunchGameOperation.None)
        }

        OxidePreflightAction.AuthorizeStorage -> {
            val pending = operation as? LaunchGameOperation.RendererNoStoragePermission ?: return
            val host = activity ?: return
            val rendererName = pending.renderer.getRendererName()
            // 权限申请本身仍然是系统那一条通道（utils/StorageUtils），这里只是把
            // "为什么要申请"这句提示从 Modal 搬到了启动页上
            //
            // 两条回调都继续启动。用户点"授权"就是要让这次启动跑完，
            // 旧实现在 checkStoragePermissions 之后无条件 updateOperation(None)，
            // 于是授权成功与失败都不启动，用户只能再按一次 Play，且没有任何提示。
            //
            // 这里刻意不在调用后无条件清空：StorageUtils 没有为
            // REQUEST_CODE_PERMISSIONS 注册 onActivityResult，安卓 11 及以上
            // 从系统设置页返回时 hasPermission 与 onDialogCancel 都不会触发。
            // 保留这一状态最坏结果是面板还在，用户可以改选"仍然启动"或"取消"；
            // 无条件清空则会让这一次操作彻底没有反馈。
            val continueLaunch = {
                launchGameViewModel.updateOperation(
                    LaunchGameOperation.RealLaunch(pending.version, pending.quickPlay),
                )
            }
            checkStoragePermissions(
                activity = host,
                message = host.getString(
                    R.string.renderer_version_storage_permissions,
                    rendererName,
                ),
                messageSdk30 = host.getString(
                    R.string.renderer_version_storage_permissions_sdk30,
                    rendererName,
                ),
                hasPermission = continueLaunch,
                onDialogCancel = continueLaunch,
            )
        }

        OxidePreflightAction.MicrosoftSignIn -> {
            val pending = operation as? LaunchGameOperation.AccountRelogin ?: return
            val host = activity ?: return
            launchGameViewModel.updateOperation(LaunchGameOperation.None)
            microsoftLogin(
                context = host,
                toWeb = { url -> backStack?.mainScreen?.backStack?.navigateToWeb(url) },
                backToMain = {
                    backStack?.mainScreen?.clearWith(NormalNavKey.LauncherMain)
                },
                checkIfInWebScreen = {
                    // 问浏览器自己，不再问"栈顶那个键是不是 WebScreen"：
                    // 旧答案在这里永远为假，设备码轮询每一次都判定用户已经走开，
                    // 授权页还开着，登录却被取消
                    globalOxideBrowser.isOpen()
                },
                updateOperation = {},
                showToast = { text, duration -> eventViewModel.sendToast(text, duration) },
                submitError = { message -> errorViewModel.showError(message) },
            ) {
                host.runOnUiThread {
                    launchGameViewModel.updateOperation(
                        LaunchGameOperation.RealLaunch(pending.version, pending.quickPlay),
                    )
                }
            }
        }

        OxidePreflightAction.SubmitPassword -> {
            val pending = operation as? LaunchGameOperation.AccountRelogin ?: return
            val host = activity ?: return
            launchGameViewModel.updateOperation(pending.copy(logging = true, error = null))
            AuthServerHelper(
                baseUrl = pending.account.otherBaseUrl!!,
                serverName = pending.account.accountType!!,
                email = pending.account.otherAccount!!,
                password = password,
                onSuccess = { account, _ ->
                    AccountsManager.markSessionValidated(account)
                    AccountsManager.suspendSaveAccount(account)
                    host.runOnUiThread {
                        launchGameViewModel.updateOperation(
                            LaunchGameOperation.RealLaunch(pending.version, pending.quickPlay),
                        )
                    }
                },
                onFailed = { th ->
                    host.runOnUiThread {
                        launchGameViewModel.updateOperation(
                            pending.copy(logging = false, error = th),
                        )
                    }
                },
            ).let { helper ->
                TaskSystem.submitTask(helper.justLogin(host, pending.account))
            }
        }

        OxidePreflightAction.SkipRefresh -> {
            val pending = operation as? LaunchGameOperation.AccountRefreshFailed ?: return
            launchGameViewModel.updateOperation(
                LaunchGameOperation.RealLaunch(
                    pending.version,
                    pending.quickPlay,
                    skipAccountRefresh = true,
                ),
            )
        }

        OxidePreflightAction.RetryRefresh -> {
            val pending = operation as? LaunchGameOperation.AccountRefreshFailed ?: return
            launchGameViewModel.updateOperation(
                LaunchGameOperation.RealLaunch(pending.version, pending.quickPlay),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 启动页上的那一块
// ---------------------------------------------------------------------------

/**
 * 启动前的说明与选择
 *
 * 不是模态弹窗：它就贴在启动面板的内容区里（宿主是 [OxideLaunchPage] 那一块居中面板），
 * 用户看得见自己按下的 Play、也看得见还没通过的检查是什么。全部尺寸都从 [OxideMetrics] 推导，
 * 按钮用 [OxideButton]，输入用既有的 [OxideSecInput]，因此和账号页那一套输入
 * 是同一个控件（同一个光标、同一套禁用态）。
 *
 * 三种结局的刷新失败与需要输入的重新登录都能装下：整块允许竖向滚动，
 * 640x360 上不会把最后一个按钮挤出屏幕——面板本身是固定高度的（见
 * [OxideLaunchPanelGeometry]），所以这里的滚动容器拿到的 `maxHeight` 一定有限。
 *
 * [modifier] 由调用方决定怎么摆；只有"要问"的那一种分支会把它铺满并允许滚动，
 * 只有一个"知道了"的分支按内容高度摆，因此它不会被拉成一整条空板。
 */
@Composable
internal fun OxideLaunchPreflight(
    metrics: OxideMetrics,
    ask: OxidePreflightAsk,
    texts: OxidePreflightTexts,
    onAction: (OxidePreflightAction) -> Unit,
    modifier: Modifier = Modifier,
    password: String = "",
    onPasswordChange: (String) -> Unit = {},
) {
    // 只有一个"知道了"的分支不摆按钮行：错误条自己就带一个关闭动作
    if (oxidePreflightIsNoticeOnly(ask)) {
        OxideSecErrorRow(
            metrics = metrics,
            title = texts.title,
            detail = texts.detail,
            dismissText = preflightActionLabel(OxidePreflightAction.Abort),
            onDismiss = { onAction(OxidePreflightAction.Abort) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            // 先按容器高度定尺寸，再让内容在里面滚动：这样按钮多于一行时
            // 出现的是滚动条，而不是被挤出屏幕的第三个按钮
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
    ) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(all = metrics.cardGap),
        ) {
            OxideSectionLabel(text = stringResource(R.string.oxide_launch_preflight_title))
            Spacer(Modifier.height(metrics.secRowGap))
            Text(
                text = texts.title,
                color = Oxide.FgStrong,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.secRowGap))
            Text(
                text = texts.detail,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            texts.failure?.let { failure ->
                Spacer(Modifier.height(metrics.secRowGap))
                Text(
                    text = failure,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ask.requiresPasswordInput) {
                Spacer(Modifier.height(metrics.cardGap))
                OxideSecInput(
                    metrics = metrics,
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(R.string.oxide_launch_relogin_password_hint),
                    label = stringResource(R.string.account_label_password),
                    password = true,
                    enabled = !texts.busy,
                    onDone = {
                        if (password.isNotEmpty() && !texts.busy) {
                            onAction(OxidePreflightAction.SubmitPassword)
                        }
                    },
                )
            }
        }

        ask.actions.forEach { action ->
            OxideButton(
                text = preflightActionLabel(action, busy = texts.busy),
                onClick = { onAction(action) },
                tone = preflightActionTone(action),
                enabled = !texts.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 按钮上写什么 */
@Composable
// busy 只影响 SubmitPassword 那一条文案，其余分支与它无关，所以给默认值
private fun preflightActionLabel(action: OxidePreflightAction, busy: Boolean = false): String = when (action) {
    OxidePreflightAction.Abort -> stringResource(R.string.generic_cancel)
    OxidePreflightAction.LaunchAnyway -> stringResource(R.string.generic_anyway)
    OxidePreflightAction.AuthorizeStorage -> stringResource(R.string.generic_authorization)
    OxidePreflightAction.MicrosoftSignIn -> stringResource(R.string.account_relogin)
    OxidePreflightAction.SkipRefresh -> stringResource(R.string.account_refresh_failed_skip)
    OxidePreflightAction.RetryRefresh -> stringResource(R.string.account_refresh_failed_retry)
    // 正在提交时按钮自己写明"正在登录"，因此不必再靠颜色区分能不能按
    OxidePreflightAction.SubmitPassword -> stringResource(
        if (busy) R.string.oxide_launch_relogin_busy else R.string.generic_confirm,
    )
}

/** 按钮是什么语气：主动作实心，顺手的动作描边，退路是纯文字 */
private fun preflightActionTone(action: OxidePreflightAction): OxideButtonTone = when (action) {
    OxidePreflightAction.RetryRefresh,
    OxidePreflightAction.MicrosoftSignIn,
    OxidePreflightAction.SubmitPassword -> OxideButtonTone.Primary

    OxidePreflightAction.LaunchAnyway,
    OxidePreflightAction.AuthorizeStorage,
    OxidePreflightAction.SkipRefresh -> OxideButtonTone.Secondary

    OxidePreflightAction.Abort -> OxideButtonTone.Ghost
}