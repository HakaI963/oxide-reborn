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

package dev.oxide.launcher.game.launch

import android.content.Context
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskFlowExecutor
import dev.oxide.launcher.coroutine.TitledTask
import dev.oxide.launcher.coroutine.addTask
import dev.oxide.launcher.coroutine.buildPhase
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.account.auth_server.AuthServerHelper
import dev.oxide.launcher.game.account.isLocalAccount
import dev.oxide.launcher.game.account.isMicrosoftAccount
import dev.oxide.launcher.game.account.isReloginRequired
import dev.oxide.launcher.game.account.microsoft.validateAccessToken
import dev.oxide.launcher.game.account.refreshMicrosoft
import dev.oxide.launcher.game.download.game.GameLibDownloader
import dev.oxide.launcher.game.version.download.BaseMinecraftDownloader
import dev.oxide.launcher.game.version.download.DownloadMode
import dev.oxide.launcher.game.version.download.MinecraftDownloader
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionInfoParser
import dev.oxide.launcher.ui.activities.runGame
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.utils.COMPACT_GSON
import dev.oxide.launcher.utils.network.isNetworkAvailable
import dev.oxide.launcher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "GameLaunchFlow"

/** 账号凭据已被服务端拒绝 */
private class LaunchReloginRequired(
    val account: Account
) : RuntimeException()

/** 账号校验或刷新失败时抛出 */
private class LaunchCheckFailed(
    val account: Account,
    cause: Throwable
) : RuntimeException(cause)


/**
 * 游戏启动器
 */
class GameLaunchFlow(scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    /**
     * 启动游戏
     * @param version 指定版本
     * @param skipAccountRefresh 跳过启动前的账号校验，直接使用现有凭据
     */
    fun launch(
        context: Context,
        version: Version,
        skipAccountRefresh: Boolean = false,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
        onReloginRequired: (Account) -> Unit = {},
        onRefreshFailed: (Account, Throwable) -> Unit = { _, _ -> },
        isRunning: () -> Unit = {},
        onComplete: () -> Unit,
    ) {
        if (taskExecutor.isRunning()) {
            //正在启动中，阻止这次启动请求
            isRunning()
            return
        }

        val account = AccountsManager.currentAccountFlow.value ?: return

        taskExecutor.executePhasesAsync(
            onStart = {
                taskExecutor.addPhases(
                    listOf(
                        buildLaunchPhases(
                            context = context,
                            version = version,
                            account = account,
                            skipAccountRefresh = skipAccountRefresh,
                            exitActivity = exitActivity,
                            submitError = submitError
                        )
                    )
                )
            },
            onComplete = onComplete,
            onError = { th ->
                when (th) {
                    is LaunchReloginRequired -> onReloginRequired(th.account)
                    is LaunchCheckFailed -> onRefreshFailed(th.account, th.cause ?: th)
                    else -> {}
                }
            },
        )
    }

    /**
     * 取消当前的启动流程
     */
    fun cancel() {
        taskExecutor.cancel()
    }

    private fun buildLaunchPhases(
        context: Context,
        version: Version,
        account: Account,
        skipAccountRefresh: Boolean,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): TaskFlowExecutor.TaskPhase {
        //检查是否联网，根据这个条件决定是否校验账号
        //以及，没有联网时，让微软账号、外置账号作为离线账号登录
        val hasNetwork = isNetworkAvailable(context)
        if (!hasNetwork && !account.isLocalAccount()) {
            version.offlineAccountLogin = true
        }

        // 准备状态在模组扫描阶段判定，在文件校验阶段使用，最后统一写回
        val prepare = PrepareRun(version)

        return buildPhase {
            if (hasNetwork && !skipAccountRefresh && AccountsManager.isLaunchCheckNeeded(account)) {
                //账号管理页正在刷新该账号时，直接使用现有凭据启动
                addTask(
                    icon = R.drawable.ic_login,
                    title = androidText(R.string.account_logging_in, account.username),
                    dispatcher = Dispatchers.IO
                ) { task ->
                    try {
                        if (account.isMicrosoftAccount()) {
                            checkMicrosoftAccount(task, account)
                        } else {
                            checkOtherAccount(task, context, account)
                        }
                        AccountsManager.markSessionValidated(account)
                        AccountsManager.suspendSaveAccount(account)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (e.isReloginRequired()) throw LaunchReloginRequired(account)
                        throw LaunchCheckFailed(account, e)
                    }
                }
            }

            //扫描模组列表，开启对应功能
            addTask(
                icon = R.drawable.ic_extension_outlined,
                title = androidText(R.string.launch_check_mods),
                dispatcher = Dispatchers.IO
            ) { task ->
                val patchedManifest = prepare.scanModsAndPatch()
                val manifest = patchedManifest ?: VersionInfoParser(version).setInheriting().build()
                // 这份字符串会跨进程传递，缩进只会平白放大体积
                val manifestString = COMPACT_GSON.toJson(manifest)

                version.launchManifest = manifestString
                prepare.onManifestBuilt(manifest)

                // 如果打了补丁，此处需要重新检索一下依赖库并下载
                patchedManifest?.let {
                    val libDownloader = GameLibDownloader(
                        downloader = BaseMinecraftDownloader(version.getGameHome()),
                        gameJson = manifestString
                    )
                    libDownloader.schedule(task)
                    libDownloader.download(task)
                }

                // 没有文件校验这一步时，准备到模组扫描为止就结束了
                if (version.skipGameIntegrityCheck()) {
                    prepare.recordPrepared(emptyList())
                }
            }

            if (!version.skipGameIntegrityCheck()) {
                //校验并修复游戏文件
                addTask(
                    icon = R.drawable.ic_assignment_filled,
                    title = androidText(R.string.minecraft_download_stat_verify_task),
                    task = createGameDownloadTask(
                        context = context,
                        version = version,
                        prepare = prepare,
                        submitError = submitError
                    )
                )
            }

            //启动游戏
            addTask(
                icon = R.drawable.ic_rocket_launch_filled,
                title = androidText(R.string.main_launch_game)
            ) { _ ->
                runGame(context, version, account)
                exitActivity()
            }
        }
    }

    /**
     * 微软账号：向服务端校验缓存的凭据，被拒绝或已临近过期时静默刷新
     */
    private suspend fun checkMicrosoftAccount(task: Task, account: Account) {
        val expired = System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        if (expired || !validateAccessToken(account)) {
            account.refreshMicrosoft(task, currentCoroutineContext())
        }
    }

    /**
     * 外置账号：依次尝试 validate 与 refresh，均被服务端拒绝时再用账号密码重新登录
     */
    private suspend fun checkOtherAccount(task: Task, context: Context, account: Account) {
        task.updateMessage(androidText(R.string.account_logging_in, account.username))
        val helper = AuthServerHelper(
            baseUrl = account.otherBaseUrl!!,
            serverName = account.accountType!!,
            email = account.otherAccount!!,
            password = account.otherPassword!!
        )
        if (!helper.validateOrRefresh(context, account)) {
            helper.passwordLogin(context, account)
        }
    }

    private fun createGameDownloadTask(
        context: Context,
        version: Version,
        prepare: PrepareRun,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): Task {
        return MinecraftDownloader(
            context = context,
            version = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName(),
            customName = version.getVersionName(),
            gameHome = version.getGameHome(),
            mode = DownloadMode.VERIFY_AND_REPAIR,
            trustedFiles = prepare.trustedFiles,
            onPrepared = { verified -> prepare.recordPrepared(verified) },
            onError = { message ->
                submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.minecraft_download_failed),
                        message = androidText(message)
                    )
                )
            }
        ).getDownloadTask()
    }
}
