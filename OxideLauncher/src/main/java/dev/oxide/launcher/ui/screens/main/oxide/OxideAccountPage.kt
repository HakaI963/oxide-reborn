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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oxide.launcher.R
import dev.oxide.launcher.context.COPY_LABEL_ACCOUNT_UUID
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.account.accountUUID
import dev.oxide.launcher.game.account.auth_server.data.AuthServer
import dev.oxide.launcher.game.account.auth_server.models.AuthResult
import dev.oxide.launcher.game.account.getAccountTypeName
import dev.oxide.launcher.game.account.getUUIDFromUserName
import dev.oxide.launcher.game.account.isElyByAccount
import dev.oxide.launcher.game.account.isLocalAccount
import dev.oxide.launcher.game.account.isMicrosoftAccount
import dev.oxide.launcher.game.account.isSkinChangeAllowed
import dev.oxide.launcher.game.account.wardrobe.EmptyCape
import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.game.account.wardrobe.capeLocalRes
import dev.oxide.launcher.game.account.yggdrasil.PlayerProfile
import dev.oxide.launcher.game.account.yggdrasil.findUsing
import dev.oxide.launcher.game.account.yggdrasil.isUsing
import dev.oxide.launcher.path.URL_MINECRAFT_PURCHASE
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.components.ImePanContainer
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.ui.screens.NormalNavKey
import dev.oxide.launcher.ui.screens.content.elements.AccountOperation
import dev.oxide.launcher.ui.screens.content.elements.AccountSkinOperation
import dev.oxide.launcher.ui.screens.content.elements.ChangeSkin
import dev.oxide.launcher.ui.screens.content.elements.ELY_BY_AUTH_SERVER
import dev.oxide.launcher.ui.screens.content.elements.LocalLoginOperation
import dev.oxide.launcher.ui.screens.content.elements.OtherLoginOperation
import dev.oxide.launcher.ui.screens.content.elements.ServerOperation
import dev.oxide.launcher.ui.screens.content.navigateToWeb
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.copyText
import dev.oxide.launcher.utils.settings.SettingsExport
import dev.oxide.launcher.utils.settings.SettingsTransferUtils
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.AccountManageEffect
import dev.oxide.launcher.viewmodel.AccountManageIntent
import dev.oxide.launcher.viewmodel.AccountManageViewModel
import dev.oxide.launcher.viewmodel.ErrorViewModel
import java.io.File

/**
 * 账号页
 *
 * 这一页取代既有的 Zalith 账号管理界面以及主界面上的账号选择弹窗：
 * 账号列表、当前账号、添加（Microsoft / 离线本地 / Authlib-Injector / Ely.by）、
 * 切换、刷新凭据、删除、皮肤与披风、以及账号库的备份与恢复，全部搬进 Oxide 语言。
 *
 * 后端一个都没有换：所有动作仍然发到同一个
 * [dev.oxide.launcher.viewmodel.AccountManageViewModel] 的 [AccountManageIntent]，
 * 失败路径也仍然是它算出来的那一段文本，因此新旧界面的失败表现完全一致。
 * 皮肤与披风能显示什么，由 [AccountsManager] 与 ViewModel 的真实状态决定，
 * 拿不到就不显示那一行，绝不补一个假的状态。
 *
 * 版面：左侧一列固定的"当前账号 / 皮肤披风 / 账号库"，右侧是可滚动的账号列表。
 * 宽度不够时上下叠放：上块占一个固定比例、下块的列表吃掉剩余高度，
 * 因此列表始终可滚，条目再多也不会把页面撑破。
 */
@Composable
fun OxideAccountPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()
    val backStack = rememberOxideScreenBackStack()

    // 与旧账号管理页取到的是同一个实例（同一个 ViewModelStore、同一个 key），
    // 因此两处共享的操作状态不会各说各话
    val viewModel: AccountManageViewModel = hiltViewModel { factory: AccountManageViewModel.Factory ->
        factory.create(eventViewModel)
    }

    val loginUiState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val profileUiState by viewModel.profileUiState.collectAsStateWithLifecycle()
    val operationUiState by viewModel.operationUiState.collectAsStateWithLifecycle()

    var failure by remember { mutableStateOf<ErrorViewModel.ThrowableMessage?>(null) }
    var sheet by remember { mutableStateOf<AccountSheet>(AccountSheet.None) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    // 备份对象中转站：ViewModel 造好之后交给系统文件选择器决定写到哪儿
    var pendingExport by remember { mutableStateOf<SettingsExport?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(SettingsTransferUtils.encode(export).toByteArray(Charsets.UTF_8))
            } ?: error("Could not open the chosen document for writing")
        }.isSuccess
        viewModel.onIntent(AccountManageIntent.BackupFinished(ok))
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
        if (text == null) {
            viewModel.onIntent(AccountManageIntent.BackupFinished(false))
        } else {
            viewModel.onIntent(AccountManageIntent.ImportBackup(text))
        }
    }

    // ViewModel 抛出的错误就地展示，而不是依赖另一个 Activity 头上的弹窗
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AccountManageEffect.ShowError ->
                    failure = ErrorViewModel.ThrowableMessage(effect.title, effect.message)
            }
        }
    }

    // 这些都是 ViewModel 的一次性状态：把失败就地说出来之后立刻复位，
    // 否则同一个失败会被反复消费
    LaunchedEffect(operationUiState.accountOp) {
        when (val op = operationUiState.accountOp) {
            is AccountOperation.OnFailed -> {
                failure = ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.account_logging_in_failed),
                    message = viewModel.formatAccountError(op.th),
                )
                viewModel.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            }

            is AccountOperation.OnRelogin -> {
                if (op.account.isMicrosoftAccount()) {
                    // 微软账号没有密码可填，只能重新走一遍设备码授权
                    viewModel.onIntent(AccountManageIntent.PerformMicrosoftLogin(
                        toWeb = { url -> backStack?.mainScreen?.backStack?.navigateToWeb(url) },
                        backToMain = {
                            backStack?.mainScreen?.clearWith(NormalNavKey.LauncherMain)
                        },
                        checkIfInWebScreen = {
                            backStack?.mainScreen?.currentKey is NormalNavKey.WebScreen
                        },
                    ))
                    viewModel.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                } else {
                    sheet = AccountSheet.Relogin(op.account)
                }
            }

            is AccountOperation.Delete -> {
                // 旧账号管理界面与这一页共用同一个 ViewModel，它把删除请求派发成
                // AccountOperation.Delete；这里交给本页自己的确认条承接，
                // 确认之后才真正删除，用完立即复位
                deleteTarget = op.account
                viewModel.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            }

            AccountOperation.None -> Unit
        }
    }

    LaunchedEffect(operationUiState.serverOp) {
        val op = operationUiState.serverOp
        if (op is ServerOperation.OnThrowable) {
            failure = ErrorViewModel.ThrowableMessage(
                title = androidText(R.string.account_other_login_adding_failure),
                message = androidText(op.throwable.getMessageOrToString()),
            )
            viewModel.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
        }
    }

    LaunchedEffect(loginUiState.otherOp) {
        when (val op = loginUiState.otherOp) {
            is OtherLoginOperation.OnFailed -> {
                failure = ErrorViewModel.ThrowableMessage(
                    title = androidText(R.string.account_logging_in_failed),
                    message = viewModel.formatAccountError(op.th),
                )
                viewModel.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                sheet = AccountSheet.None
            }

            is OtherLoginOperation.SelectRole -> sheet = AccountSheet.PickRole(op)

            is OtherLoginOperation.OnLogin -> Unit
            OtherLoginOperation.None -> Unit
        }
    }

    // 备份对象一旦备好就交给系统选择器，中间不留在界面上
    LaunchedEffect(operationUiState.pendingBackup) {
        val export = operationUiState.pendingBackup ?: return@LaunchedEffect
        pendingExport = export
        viewModel.onIntent(AccountManageIntent.BackupPrepared)
        exportLauncher.launch(SettingsTransferUtils.BACKUP_FILE_NAME)
    }

    val accounts = profileUiState.accounts
    val current = profileUiState.currentAccount

    val startMicrosoftLogin = {
        sheet = AccountSheet.None
        viewModel.onIntent(AccountManageIntent.PerformMicrosoftLogin(
            toWeb = { url -> backStack?.mainScreen?.backStack?.navigateToWeb(url) },
            backToMain = { backStack?.mainScreen?.clearWith(NormalNavKey.LauncherMain) },
            checkIfInWebScreen = {
                backStack?.mainScreen?.currentKey is NormalNavKey.WebScreen
            },
        ))
    }
    val startRestore = {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    val skinSheet: AccountSheet.Skin? = sheet as? AccountSheet.Skin

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sideBySide = maxWidth >= metrics.cardMinWidth * 1.35f

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OxideIconButton(
                        onClick = onDismiss,
                        glyph = "←",
                        modifier = Modifier.oxideIconDescription(
                            stringResource(R.string.oxide_sec_topbar_back)
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    OxidePageTitle(
                        text = stringResource(R.string.oxide_sec_accounts_title),
                        modifier = Modifier.weight(1f),
                        trailing = {
                            OxideButton(
                                text = stringResource(R.string.oxide_sec_accounts_add),
                                onClick = { sheet = AccountSheet.Menu },
                                tone = OxideButtonTone.Primary,
                            )
                        },
                    )
                }
                OxideSectionLabel(
                    text = stringResource(
                        if (accounts.size == 1) {
                            R.string.oxide_sec_accounts_subtitle_one
                        } else {
                            R.string.oxide_sec_accounts_subtitle_other
                        },
                        accounts.size,
                    )
                )

                Spacer(Modifier.height(metrics.sectionGap))

                failure?.let { message ->
                    OxideSecErrorRow(
                        metrics = metrics,
                        title = resolveAndroidString(message.title).text,
                        detail = resolveAndroidString(message.message).text,
                        dismissText = stringResource(R.string.oxide_sec_accounts_dismiss),
                        onDismiss = { failure = null },
                    )
                    Spacer(Modifier.height(metrics.cardGap))
                }

                deleteTarget?.let { target ->
                    OxideSecConfirmBar(
                        metrics = metrics,
                        text = stringResource(
                            R.string.oxide_sec_accounts_delete_message,
                            target.username,
                        ),
                        confirmText = stringResource(R.string.oxide_sec_accounts_delete_confirm),
                        dismissText = stringResource(R.string.generic_cancel),
                        onConfirm = {
                            deleteTarget = null
                            viewModel.onIntent(AccountManageIntent.DeleteAccount(target))
                        },
                        onDismiss = { deleteTarget = null },
                    )
                    Spacer(Modifier.height(metrics.cardGap))
                }

                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(metrics.cardMinWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                        ) {
                            OxideAccountCurrentCard(
                                metrics = metrics,
                                current = current,
                                accountsCount = accounts.size,
                                onAdd = { sheet = AccountSheet.Menu },
                            )
                            OxideAccountWardrobeCard(
                                metrics = metrics,
                                account = current,
                                capes = current?.let {
                                    profileUiState.accountCapeOpMap[it.uniqueUUID]
                                }.orEmpty(),
                                onOpenSkin = { account -> sheet = AccountSheet.Skin(account) },
                                onFetchCapes = { account ->
                                    viewModel.onIntent(AccountManageIntent.FetchMicrosoftCapes(account))
                                },
                            )
                            OxideAccountStoreCard(
                                metrics = metrics,
                                onBackup = { viewModel.onIntent(AccountManageIntent.PrepareBackup) },
                                onRestore = startRestore,
                            )
                        }

                        OxideAccountListCard(
                            metrics = metrics,
                            accounts = accounts,
                            current = current,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onUse = { account -> AccountsManager.setCurrentAccount(account) },
                            onRefresh = { account ->
                                viewModel.onIntent(AccountManageIntent.RefreshAccount(account))
                            },
                            onCopyUuid = { account ->
                                copyText(COPY_LABEL_ACCOUNT_UUID, account.profileId, context, true)
                            },
                            onDelete = { account -> deleteTarget = account },
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.46f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                        ) {
                            OxideAccountCurrentCard(
                                metrics = metrics,
                                current = current,
                                accountsCount = accounts.size,
                                onAdd = { sheet = AccountSheet.Menu },
                            )
                            OxideAccountStoreCard(
                                metrics = metrics,
                                onBackup = { viewModel.onIntent(AccountManageIntent.PrepareBackup) },
                                onRestore = startRestore,
                            )
                        }

                        OxideAccountListCard(
                            metrics = metrics,
                            accounts = accounts,
                            current = current,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onUse = { account -> AccountsManager.setCurrentAccount(account) },
                            onRefresh = { account ->
                                viewModel.onIntent(AccountManageIntent.RefreshAccount(account))
                            },
                            onCopyUuid = { account ->
                                copyText(COPY_LABEL_ACCOUNT_UUID, account.profileId, context, true)
                            },
                            onDelete = { account -> deleteTarget = account },
                        )
                    }
                }
            }
        }

        if (sheet != AccountSheet.None) {
            OxideAccountSheetHost(
                metrics = metrics,
                sheet = sheet,
                authServers = profileUiState.authServers,
                pendingSkin = operationUiState.accountSkinDialogState.pendingSkinData,
                importingSkin = operationUiState.accountSkinDialogState.importingSkin,
                capes = skinSheet?.let { profileUiState.accountCapeOpMap[it.account.uniqueUUID] }
                    .orEmpty(),
                onNavigate = { target -> sheet = target },
                onDismiss = {
                    viewModel.onIntent(AccountManageIntent.ResetAccountSkinDialogState)
                    viewModel.onIntent(
                        AccountManageIntent.UpdateAccountSkinOp(AccountSkinOperation.None)
                    )
                    viewModel.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                    viewModel.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
                    viewModel.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
                    viewModel.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                    sheet = AccountSheet.None
                },
                onMicrosoft = startMicrosoftLogin,
                onOffline = { name, uuid ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(AccountManageIntent.CreateLocalAccount(name, uuid))
                },
                onServerLogin = { server, email, password ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(
                        AccountManageIntent.LoginWithOtherServer(server, email, password)
                    )
                },
                onAddServer = { url ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(AccountManageIntent.AddServer(url))
                },
                onDeleteServer = { server ->
                    viewModel.onIntent(AccountManageIntent.DeleteServer(server))
                },
                onSkinPicked = { uri ->
                    viewModel.onIntent(AccountManageIntent.OnSkinPicked(uri))
                },
                onSkinModel = { skin ->
                    viewModel.onIntent(AccountManageIntent.UpdatePendingSkinData(skin))
                },
                onApplySkin = { account, file, model ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(AccountManageIntent.ApplySkin(account, file, model))
                },
                onResetSkin = { account ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(AccountManageIntent.ResetSkin(account))
                },
                onFetchCapes = { account ->
                    viewModel.onIntent(AccountManageIntent.FetchMicrosoftCapes(account))
                },
                onApplyCape = { account, cape ->
                    viewModel.onIntent(AccountManageIntent.ApplyMicrosoftCape(account, cape))
                },
                onImportLocalCape = { account, uri ->
                    viewModel.onIntent(AccountManageIntent.ImportLocalCape(account, uri))
                },
                onRelogin = { account, password ->
                    sheet = AccountSheet.None
                    viewModel.onIntent(AccountManageIntent.ReloginOtherAccount(account, password))
                },
                onPickRole = { operation, profile ->
                    sheet = AccountSheet.None
                    operation.selected(profile)
                },
                openLink = LocalOxideHostActions.current.openLink,
            )
        }
    }
}

/** 账号页上可能出现的浮层 */
private sealed interface AccountSheet {
    data object None : AccountSheet

    /** 登录方式清单 */
    data object Menu : AccountSheet

    /** 输入用户名（可展开自定义 UUID）创建离线账号 */
    data object Offline : AccountSheet

    /** 在某个 Authlib-Injector 服务器上登录 */
    data class ServerLogin(val server: AuthServer) : AccountSheet

    /** 添加一个新的 Authlib-Injector 服务器 */
    data object AddServer : AccountSheet

    /** 服务器返回多个角色时选一个 */
    data class PickRole(val operation: OtherLoginOperation.SelectRole) : AccountSheet

    /** 凭据被拒后重新输入密码 */
    data class Relogin(val account: Account) : AccountSheet

    /** 皮肤与披风 */
    data class Skin(val account: Account) : AccountSheet
}

// ---------------------------------------------------------------------------
// 左侧固定的几块
// ---------------------------------------------------------------------------

/** 当前账号：真实的 currentAccountFlow，没有账号时如实说明 */
@Composable
private fun OxideAccountCurrentCard(
    metrics: OxideMetrics,
    current: Account?,
    accountsCount: Int,
    onAdd: () -> Unit,
) {
    OxideSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_sec_accounts_current),
            trailing = if (current != null) {
                {
                    OxideBadge(
                        text = stringResource(R.string.oxide_sec_accounts_current_badge),
                        tone = OxideBadgeTone.Active,
                    )
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))
        if (current == null) {
            OxideEmptyState(
                title = stringResource(R.string.oxide_sec_accounts_none),
                detail = stringResource(R.string.oxide_sec_accounts_none_detail),
                action = {
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_accounts_add),
                        onClick = onAdd,
                        tone = OxideButtonTone.Primary,
                    )
                },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OxideSecAvatar(
                    initial = current.username.trim().take(1).uppercase(),
                    description = current.username,
                    size = Oxide.MarkSize,
                    selected = true,
                )
                Spacer(Modifier.width(metrics.cardGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.username,
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.Title.fontSize,
                        lineHeight = Oxide.Type.Title.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = getAccountTypeName(current),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(metrics.secRowGap))
            OxideSecDivider()
            Spacer(Modifier.height(metrics.secRowGap))
            OxideSettingRow(
                label = stringResource(R.string.oxide_sec_accounts_list),
                value = stringResource(
                    if (accountsCount == 1) {
                        R.string.oxide_sec_accounts_subtitle_one
                    } else {
                        R.string.oxide_sec_accounts_subtitle_other
                    },
                    accountsCount,
                ),
            )
        }
    }
}

/**
 * 皮肤与披风
 *
 * 只显示真实存在的东西：皮肤文件在不在、披风文件在不在、微软账号抓回来的披风列表。
 * 组合期只读已经解析好的路径，不做 IO；换账号时重新读一次。
 */
@Composable
private fun OxideAccountWardrobeCard(
    metrics: OxideMetrics,
    account: Account?,
    capes: List<PlayerProfile.Cape>,
    onOpenSkin: (Account) -> Unit,
    onFetchCapes: (Account) -> Unit,
) {
    val skinPresent = remember(account) { account?.hasSkinFile == true }
    val capePresent = remember(account) { account?.getCapeFile()?.exists() == true }
    val usingCape = remember(capes) { capes.findUsing() }

    OxideSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_accounts_wardrobe))
        Spacer(Modifier.height(metrics.secRowGap))
        if (account == null) {
            Text(
                text = stringResource(R.string.oxide_sec_accounts_none),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
            )
            return@OxideSurface
        }
        OxideSettingRow(
            label = stringResource(R.string.account_change_skin),
            value = stringResource(
                if (skinPresent) {
                    R.string.oxide_sec_accounts_skin_present
                } else {
                    R.string.oxide_sec_accounts_skin_default
                }
            ),
        )
        OxideSettingRow(
            label = stringResource(R.string.account_change_cape),
            value = if (usingCape != null) {
                capeName(usingCape)
            } else {
                stringResource(
                    if (capePresent) {
                        R.string.oxide_sec_accounts_cape_present
                    } else {
                        R.string.oxide_sec_accounts_cape_none
                    }
                )
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
            OxideButton(
                text = stringResource(R.string.oxide_sec_accounts_change_skin),
                onClick = { onOpenSkin(account) },
                enabled = account.isSkinChangeAllowed(),
                modifier = Modifier.weight(1f),
            )
            if (account.isMicrosoftAccount()) {
                OxideButton(
                    text = stringResource(R.string.oxide_sec_accounts_fetch_capes),
                    onClick = { onFetchCapes(account) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 账号库的备份与恢复：与旧账号管理页里的两个按钮是同一条链路 */
@Composable
private fun OxideAccountStoreCard(
    metrics: OxideMetrics,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    OxideSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_accounts_backup_section))
        Spacer(Modifier.height(metrics.secRowGap))
        Text(
            text = stringResource(R.string.settings_export_accounts_warning),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metrics.secRowGap))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
            OxideButton(
                text = stringResource(R.string.oxide_sec_accounts_backup),
                onClick = onBackup,
                modifier = Modifier.weight(1f),
            )
            OxideButton(
                text = stringResource(R.string.oxide_sec_accounts_restore),
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 右侧账号列表
// ---------------------------------------------------------------------------

@Composable
private fun OxideAccountListCard(
    metrics: OxideMetrics,
    accounts: List<Account>,
    current: Account?,
    onUse: (Account) -> Unit,
    onRefresh: (Account) -> Unit,
    onCopyUuid: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.cardGap,
            vertical = metrics.secRowGap,
        ),
    ) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_sec_accounts_list),
            trailing = if (accounts.isNotEmpty()) {
                { OxideBadge(text = accounts.size.toString()) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(metrics.secRowGap))
        if (accounts.isEmpty()) {
            OxideEmptyState(
                title = stringResource(R.string.oxide_sec_accounts_list_empty),
                detail = stringResource(R.string.oxide_sec_accounts_list_empty_detail),
            )
            return@OxideSurface
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(items = accounts, key = { account -> account.uniqueUUID }) { account ->
                OxideAccountRow(
                    metrics = metrics,
                    account = account,
                    selected = current?.uniqueUUID == account.uniqueUUID,
                    onUse = { onUse(account) },
                    onRefresh = { onRefresh(account) },
                    onCopyUuid = { onCopyUuid(account) },
                    onDelete = { onDelete(account) },
                )
                Spacer(Modifier.height(metrics.secRowGap))
            }
        }
    }
}

/**
 * 列表里的一行
 *
 * 整行点 = 设为当前账号；右侧三个小按钮分别是刷新凭据、复制 UUID、删除。
 * 离线账号没有服务端凭据可刷新，那一枚按钮因此禁用而不是被悄悄移除，
 * 这样布局在切换账号时不会跳动。
 */
@Composable
private fun OxideAccountRow(
    metrics: OxideMetrics,
    account: Account,
    selected: Boolean,
    onUse: () -> Unit,
    onRefresh: () -> Unit,
    onCopyUuid: () -> Unit,
    onDelete: () -> Unit,
) {
    val typeName = getAccountTypeName(account)
    val uuidDescription = stringResource(R.string.account_local_uuid_copy)
    val refreshDescription = stringResource(R.string.generic_refresh)
    val deleteDescription = stringResource(R.string.generic_delete)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Oxide.BgButton)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            // selectable 同时给出"这是列表里的一项"与"它是不是当前账号"，
            // 因此当前账号不只靠底色区分
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onUse,
            )
            .padding(
                horizontal = metrics.secControlPadding,
                vertical = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OxideSecAvatar(
            initial = account.username.trim().take(1).uppercase(),
            description = account.username,
            size = Oxide.MarkSize,
            selected = selected,
        )
        Spacer(Modifier.width(metrics.cardGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.username,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typeName,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(metrics.secRowGap))
        OxideIconButton(
            onClick = onRefresh,
            glyph = "↻",
            enabled = !account.isLocalAccount(),
            modifier = Modifier.oxideIconDescription(refreshDescription),
        )
        OxideIconButton(
            onClick = onCopyUuid,
            glyph = "⧉",
            modifier = Modifier.oxideIconDescription(uuidDescription),
        )
        OxideIconButton(
            onClick = onDelete,
            glyph = "✕",
            modifier = Modifier.oxideIconDescription(deleteDescription),
        )
    }
}

// ---------------------------------------------------------------------------
// 浮层
// ---------------------------------------------------------------------------

@Composable
private fun OxideAccountSheetHost(
    metrics: OxideMetrics,
    sheet: AccountSheet,
    authServers: List<AuthServer>,
    pendingSkin: ChangeSkin,
    importingSkin: Boolean,
    capes: List<PlayerProfile.Cape>,
    onNavigate: (AccountSheet) -> Unit,
    onDismiss: () -> Unit,
    onMicrosoft: () -> Unit,
    onOffline: (String, String?) -> Unit,
    onServerLogin: (AuthServer, String, String) -> Unit,
    onAddServer: (String) -> Unit,
    onDeleteServer: (AuthServer) -> Unit,
    onSkinPicked: (Uri) -> Unit,
    onSkinModel: (ChangeSkin) -> Unit,
    onApplySkin: (Account, File, SkinModelType) -> Unit,
    onResetSkin: (Account) -> Unit,
    onFetchCapes: (Account) -> Unit,
    onApplyCape: (Account, PlayerProfile.Cape) -> Unit,
    onImportLocalCape: (Account, Uri) -> Unit,
    onRelogin: (Account, String) -> Unit,
    onPickRole: (OtherLoginOperation.SelectRole, AuthResult.AvailableProfiles) -> Unit,
    openLink: (String) -> Unit,
) {
    val dismissDescription = stringResource(R.string.oxide_sec_accounts_dismiss)
    // 抽屉标题：登录服务器那一页用服务器名，其余各页用各自的固定文案
    val sheetTitle: String = when (sheet) {
        AccountSheet.Menu -> stringResource(R.string.oxide_sec_accounts_sign_in_title)
        AccountSheet.Offline -> stringResource(R.string.oxide_sec_accounts_sign_in_offline)
        is AccountSheet.ServerLogin -> sheet.server.serverName
        AccountSheet.AddServer -> stringResource(R.string.oxide_sec_accounts_add_server)
        is AccountSheet.PickRole -> stringResource(R.string.oxide_sec_accounts_pick_role)
        is AccountSheet.Relogin -> stringResource(R.string.oxide_sec_accounts_relogin_title)
        is AccountSheet.Skin -> stringResource(R.string.oxide_sec_accounts_change_skin)
        AccountSheet.None -> stringResource(R.string.generic_close)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ImePanContainer(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = maxHeight * 0.92f)
                    .clip(Oxide.RadiusDrawer)
                    .background(Oxide.DrawerBg)
                    .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusDrawer),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sheetTitle,
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.DrawerTitle.fontSize,
                        lineHeight = Oxide.Type.DrawerTitle.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    OxideIconButton(
                        onClick = onDismiss,
                        glyph = "✕",
                        modifier = Modifier.oxideIconDescription(dismissDescription),
                    )
                }
                OxideSecDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(all = metrics.cardGap),
                    verticalArrangement = Arrangement.spacedBy(metrics.secGroupGap),
                ) {
                    when (val target = sheet) {
                        AccountSheet.None -> Unit

                        AccountSheet.Menu -> OxideAccountMenuSheet(
                            metrics = metrics,
                            authServers = authServers,
                            onMicrosoft = onMicrosoft,
                            onNavigate = onNavigate,
                            onDeleteServer = onDeleteServer,
                        )

                        AccountSheet.Offline -> OxideOfflineAccountSheet(
                            metrics = metrics,
                            onDismiss = onDismiss,
                            onCreate = onOffline,
                            openLink = openLink,
                        )

                        is AccountSheet.ServerLogin -> OxideServerLoginSheet(
                            metrics = metrics,
                            server = target.server,
                            onDismiss = onDismiss,
                            onLogin = { email, password ->
                                onServerLogin(target.server, email, password)
                            },
                            openLink = openLink,
                        )

                        AccountSheet.AddServer -> OxideAddServerSheet(
                            metrics = metrics,
                            onDismiss = onDismiss,
                            onAdd = onAddServer,
                        )

                        is AccountSheet.PickRole -> OxidePickRoleSheet(
                            operation = target.operation,
                            onPick = { profile -> onPickRole(target.operation, profile) },
                            onDismiss = onDismiss,
                        )

                        is AccountSheet.Relogin -> OxideReloginSheet(
                            metrics = metrics,
                            account = target.account,
                            onDismiss = onDismiss,
                            onRelogin = { password -> onRelogin(target.account, password) },
                        )

                        is AccountSheet.Skin -> OxideSkinSheet(
                            metrics = metrics,
                            account = target.account,
                            pendingSkin = pendingSkin,
                            importingSkin = importingSkin,
                            capes = capes,
                            onDismiss = onDismiss,
                            onSkinPicked = onSkinPicked,
                            onSkinModel = onSkinModel,
                            onApplySkin = { file, model -> onApplySkin(target.account, file, model) },
                            onResetSkin = { onResetSkin(target.account) },
                            onFetchCapes = { onFetchCapes(target.account) },
                            onApplyCape = { cape -> onApplyCape(target.account, cape) },
                            onImportLocalCape = { uri -> onImportLocalCape(target.account, uri) },
                        )
                    }
                }
            }
        }
    }
}

/** 登录方式清单：Microsoft / 离线本地 / 已保存的 Authlib-Injector 服务器 / Ely.by 快捷入口 */
@Composable
private fun OxideAccountMenuSheet(
    metrics: OxideMetrics,
    authServers: List<AuthServer>,
    onMicrosoft: () -> Unit,
    onNavigate: (AccountSheet) -> Unit,
    onDeleteServer: (AuthServer) -> Unit,
) {
    val deleteDescription = stringResource(R.string.generic_delete)

    OxideSection(title = stringResource(R.string.account_type_microsoft)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            OxideSettingRow(
                label = stringResource(R.string.account_type_microsoft),
                hint = stringResource(R.string.account_supporting_microsoft_tip_hint_t1),
                onClick = onMicrosoft,
            )
        }
    }

    OxideSection(title = stringResource(R.string.account_type_local)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            OxideSettingRow(
                label = stringResource(R.string.account_type_local),
                hint = stringResource(R.string.oxide_sec_accounts_sign_in_offline_detail),
                onClick = { onNavigate(AccountSheet.Offline) },
            )
        }
    }

    OxideSection(title = stringResource(R.string.oxide_sec_accounts_servers)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            Column {
                OxideSettingRow(
                    label = stringResource(R.string.oxide_sec_accounts_add_server),
                    onClick = { onNavigate(AccountSheet.AddServer) },
                )
                OxideSettingRow(
                    label = stringResource(R.string.oxide_sec_accounts_ely_by),
                    hint = stringResource(R.string.oxide_sec_accounts_ely_by_detail),
                    onClick = { onNavigate(AccountSheet.ServerLogin(ELY_BY_AUTH_SERVER)) },
                )
                if (authServers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.oxide_sec_accounts_servers_empty_detail),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = metrics.secControlPadding,
                            vertical = metrics.secRowGap,
                        ),
                    )
                } else {
                    authServers.forEachIndexed { index, server ->
                        if (index > 0) OxideSecDivider()
                        OxideSettingRow(
                            label = server.serverName,
                            onClick = { onNavigate(AccountSheet.ServerLogin(server)) },
                            trailing = {
                                OxideIconButton(
                                    onClick = { onDeleteServer(server) },
                                    glyph = "✕",
                                    modifier = Modifier.oxideIconDescription(deleteDescription),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 离线账号：用户名 + 可选的自定义 UUID，校验规则与旧界面完全一致 */
@Composable
private fun OxideOfflineAccountSheet(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
    openLink: (String) -> Unit,
) {
    var userName by remember { mutableStateOf("") }
    var userUUID by remember { mutableStateOf("") }
    var userEditedUUID by remember { mutableStateOf(false) }
    var showUuid by remember { mutableStateOf(false) }
    var forceInvalidName by remember { mutableStateOf(false) }

    // 没手改过 UUID 时，它跟着用户名推导出来的那个走
    val pendingUuid = remember(userName, userEditedUUID, userUUID) {
        if (userEditedUUID) {
            userUUID
        } else {
            runCatching { accountUUID(getUUIDFromUserName(userName)) }
                .getOrElse { "" }
        }
    }

    val nameError = when {
        userName.isEmpty() -> stringResource(R.string.account_supporting_username_invalid_empty)
        userName.length <= 2 -> stringResource(R.string.account_supporting_username_invalid_short)
        userName.length > 16 -> stringResource(R.string.account_supporting_username_invalid_long)
        ILLEGAL_NAME.containsMatchIn(userName) ->
            stringResource(R.string.account_supporting_username_invalid_illegal_characters)
        else -> ""
    }

    val uuidInvalid = remember(userUUID) {
        userUUID.isNotEmpty() && runCatching { accountUUID(userUUID) }.isFailure
    }

    val canCreate = userName.isNotEmpty() && !uuidInvalid

    OxideSecInput(
        metrics = metrics,
        value = userName,
        onValueChange = { userName = it },
        placeholder = stringResource(R.string.account_label_username),
        label = stringResource(R.string.oxide_sec_accounts_name_label),
        isError = nameError.isNotEmpty(),
        onDone = { if (canCreate) onCreate(userName, pendingUuid.takeIf { it.isNotEmpty() }) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = nameError.ifEmpty {
            stringResource(R.string.oxide_sec_accounts_name_hint)
        },
        color = Oxide.FgFaint,
        fontSize = Oxide.Type.MicroLabel.fontSize,
        lineHeight = Oxide.Type.MicroLabel.lineHeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.oxide_sec_accounts_advanced),
            onClick = { showUuid = !showUuid },
            tone = OxideButtonTone.Ghost,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.generic_open_link),
            onClick = { openLink(URL_MINECRAFT_PURCHASE) },
            tone = OxideButtonTone.Ghost,
            modifier = Modifier.weight(1f),
        )
    }

    if (showUuid) {
        OxideSecInput(
            metrics = metrics,
            value = userUUID,
            onValueChange = {
                userUUID = it
                userEditedUUID = true
            },
            placeholder = stringResource(R.string.account_local_uuid),
            label = stringResource(R.string.oxide_sec_accounts_uuid_label),
            isError = uuidInvalid,
        )
        Text(
            text = if (uuidInvalid) {
                stringResource(R.string.account_local_uuid_invalid)
            } else {
                stringResource(R.string.oxide_sec_accounts_uuid_hint)
            },
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.oxide_sec_accounts_create),
            onClick = {
                if (nameError.isNotEmpty()) {
                    forceInvalidName = true
                } else {
                    onCreate(userName, pendingUuid.takeIf { it.isNotEmpty() })
                }
            },
            enabled = canCreate,
            tone = OxideButtonTone.Primary,
            modifier = Modifier.weight(1f),
        )
    }

    // 用户名不合规时仍然可以硬着头皮用下去，与旧界面的强制确认一致
    if (forceInvalidName) {
        OxideSecConfirmBar(
            metrics = metrics,
            text = stringResource(
                R.string.account_supporting_username_invalid_local_message_hint1
            ),
            confirmText = stringResource(
                R.string.account_supporting_username_invalid_still_use
            ),
            dismissText = stringResource(R.string.generic_cancel),
            onConfirm = {
                forceInvalidName = false
                onCreate(userName, pendingUuid.takeIf { it.isNotEmpty() })
            },
            onDismiss = { forceInvalidName = false },
        )
    }
}

/** Authlib-Injector 服务器登录：邮箱 + 密码，可跳到注册页 */
@Composable
private fun OxideServerLoginSheet(
    metrics: OxideMetrics,
    server: AuthServer,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    openLink: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val registerUrl = server.register
    val canSubmit = email.isNotEmpty() && password.isNotEmpty()

    OxideSecInput(
        metrics = metrics,
        value = email,
        onValueChange = { email = it },
        placeholder = stringResource(R.string.account_label_email),
        label = stringResource(R.string.account_label_email),
        isError = email.isEmpty(),
    )
    OxideSecInput(
        metrics = metrics,
        value = password,
        onValueChange = { password = it },
        placeholder = stringResource(R.string.account_label_password),
        label = stringResource(R.string.account_label_password),
        password = true,
        isError = password.isEmpty(),
        onDone = { if (canSubmit) onLogin(email, password) },
    )
    if (!registerUrl.isNullOrEmpty()) {
        OxideButton(
            text = stringResource(R.string.account_other_login_register),
            onClick = { openLink(registerUrl) },
            tone = OxideButtonTone.Ghost,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.generic_confirm),
            onClick = { onLogin(email, password) },
            enabled = canSubmit,
            tone = OxideButtonTone.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 添加 Authlib-Injector 服务器 */
@Composable
private fun OxideAddServerSheet(
    metrics: OxideMetrics,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val canSubmit = url.isNotEmpty()

    OxideSecInput(
        metrics = metrics,
        value = url,
        onValueChange = { url = it.trim() },
        placeholder = stringResource(R.string.account_label_server_url),
        label = stringResource(R.string.oxide_sec_accounts_server_url),
        onDone = { if (canSubmit) onAdd(url) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.generic_confirm),
            onClick = { onAdd(url) },
            enabled = canSubmit,
            tone = OxideButtonTone.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 服务器返回多个角色时选一个 */
@Composable
private fun OxidePickRoleSheet(
    operation: OtherLoginOperation.SelectRole,
    onPick: (AuthResult.AvailableProfiles) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        operation.profiles.forEachIndexed { index, profile ->
            if (index > 0) OxideSecDivider()
            OxideSettingRow(
                label = profile.name,
                value = profile.id,
                onClick = { onPick(profile) },
            )
        }
    }
    OxideButton(
        text = stringResource(R.string.generic_cancel),
        onClick = onDismiss,
        tone = OxideButtonTone.Ghost,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 凭据被拒后重新输入密码 */
@Composable
private fun OxideReloginSheet(
    metrics: OxideMetrics,
    account: Account,
    onDismiss: () -> Unit,
    onRelogin: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val canSubmit = password.isNotEmpty()

    Text(
        text = stringResource(R.string.oxide_sec_accounts_relogin_hint, account.username),
        color = Oxide.FgMuted,
        fontSize = Oxide.Type.Body.fontSize,
        lineHeight = Oxide.Type.Body.lineHeight,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    OxideSecInput(
        metrics = metrics,
        value = password,
        onValueChange = { password = it },
        placeholder = stringResource(R.string.account_label_password),
        password = true,
        onDone = { if (canSubmit) onRelogin(password) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap)) {
        OxideButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        OxideButton(
            text = stringResource(R.string.oxide_sec_accounts_relogin_retry),
            onClick = { onRelogin(password) },
            enabled = canSubmit,
            tone = OxideButtonTone.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 皮肤与披风
 *
 * 导入 PNG、选手臂型号、抓取微软披风、导入本地披风、重置皮肤，
 * 全部走 [AccountManageViewModel] 的同一个意图，因此文件校验、
 * 推荐型号与上传逻辑与旧界面完全一致。
 */
@Composable
private fun OxideSkinSheet(
    metrics: OxideMetrics,
    account: Account,
    pendingSkin: ChangeSkin,
    importingSkin: Boolean,
    capes: List<PlayerProfile.Cape>,
    onDismiss: () -> Unit,
    onSkinPicked: (Uri) -> Unit,
    onSkinModel: (ChangeSkin) -> Unit,
    onApplySkin: (File, SkinModelType) -> Unit,
    onResetSkin: () -> Unit,
    onFetchCapes: (Account) -> Unit,
    onApplyCape: (PlayerProfile.Cape) -> Unit,
    onImportLocalCape: (Uri) -> Unit,
) {
    val skinPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onSkinPicked) }
    val localCapePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportLocalCape) }

    // 微软账号第一次进来还没有披风列表，先抓一次
    val fetchingCapes = account.isMicrosoftAccount() && capes.isEmpty()
    LaunchedEffect(account.uniqueUUID, fetchingCapes) {
        if (fetchingCapes) onFetchCapes(account)
    }

    if (!account.isSkinChangeAllowed()) {
        Text(
            text = stringResource(R.string.oxide_sec_accounts_skin_unsupported),
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    OxideSection(title = stringResource(R.string.account_change_skin)) {
        OxideSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = metrics.cardGap,
                vertical = metrics.secRowGap,
            ),
        ) {
            Column {
                OxideSettingRow(
                    label = if (importingSkin) {
                        stringResource(R.string.oxide_sec_accounts_skin_importing)
                    } else {
                        stringResource(R.string.oxide_sec_accounts_import_skin)
                    },
                    hint = (pendingSkin as? ChangeSkin.ChangeSkinData)?.cacheFile?.name,
                    enabled = !importingSkin,
                    onClick = { skinPicker.launch(arrayOf("image/png")) },
                )
                if (pendingSkin is ChangeSkin.ChangeSkinData) {
                    OxideSecDivider()
                    Text(
                        text = stringResource(R.string.oxide_sec_accounts_arm_style),
                        color = Oxide.FgDim,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        modifier = Modifier.padding(horizontal = metrics.secControlPadding),
                    )
                    OxideSecPickerRow(
                        label = stringResource(R.string.account_change_skin_arm_wide),
                        selected = pendingSkin.skinModel == SkinModelType.STEVE,
                        onClick = {
                            onSkinModel(pendingSkin.copy(skinModel = SkinModelType.STEVE))
                        },
                    )
                    OxideSecPickerRow(
                        label = stringResource(R.string.account_change_skin_arm_slim),
                        selected = pendingSkin.skinModel == SkinModelType.ALEX,
                        onClick = {
                            onSkinModel(pendingSkin.copy(skinModel = SkinModelType.ALEX))
                        },
                    )
                    OxideSecDivider()
                    OxideButton(
                        text = stringResource(R.string.oxide_sec_accounts_apply_skin),
                        onClick = { onApplySkin(pendingSkin.cacheFile, pendingSkin.skinModel) },
                        tone = OxideButtonTone.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (account.isLocalAccount() && account.hasSkinFile) {
                    OxideSecDivider()
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_sec_accounts_reset_skin),
                        onClick = onResetSkin,
                    )
                }
            }
        }
    }

    // 披风：微软账号从服务端选；本地与 Ely.by 账号走本地文件
    when {
        account.isMicrosoftAccount() -> OxideSection(
            title = stringResource(R.string.account_change_cape),
        ) {
            OxideSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = metrics.cardGap,
                    vertical = metrics.secRowGap,
                ),
            ) {
                Column {
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_sec_accounts_fetch_capes),
                        hint = if (fetchingCapes) {
                            stringResource(R.string.oxide_sec_accounts_capes_fetching)
                        } else {
                            stringResource(R.string.account_change_cape_fetch_all)
                        },
                        enabled = !fetchingCapes,
                        onClick = { onFetchCapes(account) },
                    )
                    if (capes.isEmpty() && !fetchingCapes) {
                        Text(
                            text = stringResource(R.string.oxide_sec_accounts_capes_empty),
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = metrics.secControlPadding),
                        )
                    } else {
                        OxideSecPickerRow(
                            label = stringResource(R.string.oxide_sec_accounts_cape_none_option),
                            selected = capes.findUsing() == null,
                            onClick = { onApplyCape(EmptyCape) },
                        )
                        capes.forEach { cape ->
                            OxideSecPickerRow(
                                label = capeName(cape),
                                selected = cape.isUsing(),
                                onClick = { onApplyCape(cape) },
                            )
                        }
                    }
                }
            }
        }

        account.isLocalAccount() || account.isElyByAccount() ->
            OxideSection(title = stringResource(R.string.account_change_cape)) {
                OxideSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = metrics.cardGap,
                        vertical = metrics.secRowGap,
                    ),
                ) {
                    OxideSettingRow(
                        label = stringResource(R.string.oxide_sec_accounts_import_cape),
                        hint = stringResource(R.string.account_change_cape_import),
                        onClick = { localCapePicker.launch(arrayOf("image/png")) },
                    )
                }
            }

        else -> Unit
    }

    OxideButton(
        text = stringResource(R.string.generic_close),
        onClick = onDismiss,
        tone = OxideButtonTone.Ghost,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 披风名称：能对上内置资源就用内置资源，否则用服务端给的别名 */
@Composable
private fun capeName(cape: PlayerProfile.Cape): String {
    val localRes = remember(cape) { cape.capeLocalRes() }
    return if (localRes != null) stringResource(localRes) else cape.alias
}

/** 离线用户名里不允许出现的字符，与旧账号界面的正则一致 */
private val ILLEGAL_NAME = Regex("[^a-zA-Z0-9_]")