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

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.notification.NotificationManager
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.path.URL_EASYTIER
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.terracotta.Terracotta
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.viewmodel.sendToast

/**
 * 联机页
 *
 * 取代既有的 Zalith 联机界面。这一页只承载启动器这一侧真实存在的功能：
 * 开关 Terracotta、自定义 EasyTier 节点、分享联机核心日志、以及房主 / 房客两套说明。
 *
 * 真正的房间流程——扫码等待、成为房主、复制邀请码、加入房间——
 * 活在游戏进程里的 `ui/screens/game/multiplayer/`，它依赖 [dev.oxide.launcher.game.launch.handler.GameHandler]
 * 才能拿到 Activity 与游戏上下文，因此**不能**在启动器这一侧重建；
 * 这一页负责把它需要的开关与说明准备好。
 *
 * 所有取值都直接读写 [AllSettings] 里既有的设置项，
 * 用户须知与通知权限这两段前置流程与旧界面逐条一致。
 */
@Composable
fun OxideMultiplayerPage(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val eventViewModel = rememberOxideEventViewModel()
    val hostActions = LocalOxideHostActions.current

    var notice by remember { mutableStateOf(MultiplayerNotice.None) }

    // 分享联机核心日志：文件不存在时如实告诉用户，而不是静默失败
    val shareCoreLog = {
        val logFile = PathManager.FILE_TERRACOTTA_LOG
        if (logFile.exists()) {
            shareFile(context, logFile)
        } else {
            eventViewModel.sendToast(androidText(R.string.oxide_sec_mp_share_log_missing))
        }
    }

    val enableTerracotta: (Boolean) -> Unit = { value ->
        AllSettings.enableTerracotta.save(value)
        // 打开之前必须先让人读过用户须知，与旧界面的前置流程一致
        if (value &&
            AllSettings.terracottaNoticeVer.getValue() <
            Terracotta.TERRACOTTA_USER_NOTICE_VERSION
        ) {
            notice = MultiplayerNotice.UserNotice
        }
    }

    val enabled = AllSettings.enableTerracotta.state

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= metrics.cardMinWidth * 1.2f

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
                    text = stringResource(R.string.oxide_sec_mp_title),
                    modifier = Modifier.weight(1f),
                )
            }
            OxideSectionLabel(text = stringResource(R.string.oxide_sec_mp_subtitle))

            Spacer(Modifier.height(metrics.sectionGap))

            when (notice) {
                MultiplayerNotice.None -> Unit

                MultiplayerNotice.UserNotice -> OxideSecConfirmBar(
                    metrics = metrics,
                    text = stringResource(R.string.terracotta_status_uninitialized_desc),
                    confirmText = stringResource(R.string.oxide_sec_mp_notice_accept),
                    dismissText = stringResource(R.string.oxide_sec_mp_notice_decline),
                    onConfirm = {
                        AllSettings.terracottaNoticeVer.save(
                            Terracotta.TERRACOTTA_USER_NOTICE_VERSION
                        )
                        notice = if (!NotificationManager.checkNotificationEnabled(context)) {
                            MultiplayerNotice.NotificationPermission
                        } else {
                            MultiplayerNotice.None
                        }
                    },
                    onDismiss = {
                        AllSettings.enableTerracotta.save(false)
                        notice = MultiplayerNotice.None
                    },
                )

                MultiplayerNotice.NotificationPermission -> OxideMpNotificationBar(
                    metrics = metrics,
                    onHandled = { notice = MultiplayerNotice.None },
                )
            }

            if (notice != MultiplayerNotice.None) {
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
                        OxideMpRuntimeCard(
                            metrics = metrics,
                            enabled = enabled,
                            nodesEnabled = AllSettings.enableTerracottaNodes.state,
                            nodes = AllSettings.terracottaNodes.state,
                            onEnable = enableTerracotta,
                            onNodesEnabled = { value ->
                                AllSettings.enableTerracottaNodes.save(value)
                            },
                            onNodes = { value -> AllSettings.terracottaNodes.save(value) },
                            onShareLog = shareCoreLog,
                            onOpenEasyTier = { hostActions.openLink(URL_EASYTIER) },
                        )
                    }

                    OxideMpGuideCard(
                        metrics = metrics,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(metrics.cardGap),
                ) {
                    OxideMpGuideCard(
                        metrics = metrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    OxideMpRuntimeCard(
                        metrics = metrics,
                        enabled = enabled,
                        nodesEnabled = AllSettings.enableTerracottaNodes.state,
                        nodes = AllSettings.terracottaNodes.state,
                        onEnable = enableTerracotta,
                        onNodesEnabled = { value ->
                            AllSettings.enableTerracottaNodes.save(value)
                        },
                        onNodes = { value -> AllSettings.terracottaNodes.save(value) },
                        onShareLog = shareCoreLog,
                        onOpenEasyTier = { hostActions.openLink(URL_EASYTIER) },
                    )
                }
            }
        }
    }
}

/** 打开联机之前的两个前置流程 */
private enum class MultiplayerNotice { None, UserNotice, NotificationPermission }

/**
 * 通知权限
 *
 * 与 [dev.oxide.launcher.ui.components.NotificationCheck] 完全同一套判定：
 * 13 以下跳系统设置让用户自己开，13 及以上直接申请。
 * 只是把 Material 弹窗换成就地确认条，留在 Oxide 的语言里。
 */
@Composable
private fun OxideMpNotificationBar(
    metrics: OxideMetrics,
    onHandled: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onHandled() }

    OxideSecConfirmBar(
        metrics = metrics,
        text = stringResource(R.string.notification_data_terracotta_message),
        confirmText = stringResource(R.string.notification_request),
        dismissText = stringResource(R.string.generic_ignore),
        onConfirm = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManager.openNotificationSettings(context)
                onHandled()
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onDismiss = { onHandled() },
    )
}

/**
 * 运行时设置
 *
 * 三件事全部落到真实的设置项上：联机总开关、自定义节点列表（含它自己的开关）、
 * 分享联机核心日志。自定义节点只有在联机开着**且**它自己的开关也开着时才可编辑，
 * 否则那一整块输入框会消失，而不是留下一排不能动的框。
 */
@Composable
private fun OxideMpRuntimeCard(
    metrics: OxideMetrics,
    enabled: Boolean,
    nodesEnabled: Boolean,
    nodes: String,
    onEnable: (Boolean) -> Unit,
    onNodesEnabled: (Boolean) -> Unit,
    onNodes: (String) -> Unit,
    onShareLog: () -> Unit,
    onOpenEasyTier: () -> Unit,
) {
    OxideSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        OxideSectionLabel(text = stringResource(R.string.oxide_sec_mp_section_runtime))
        Spacer(Modifier.height(metrics.secRowGap))
        Column {
            OxideMpToggleRow(
                label = stringResource(R.string.oxide_sec_mp_enable),
                hint = stringResource(R.string.oxide_sec_mp_enable_hint),
                checked = enabled,
                onCheckedChange = onEnable,
            )
            OxideSecDivider()
            OxideMpToggleRow(
                label = stringResource(R.string.oxide_sec_mp_nodes),
                hint = stringResource(R.string.oxide_sec_mp_nodes_hint),
                checked = nodesEnabled,
                enabled = enabled,
                onCheckedChange = onNodesEnabled,
            )
            if (enabled && nodesEnabled) {
                OxideSecDivider()
                OxideSecInput(
                    metrics = metrics,
                    value = nodes,
                    onValueChange = onNodes,
                    placeholder = stringResource(R.string.oxide_sec_mp_nodes_placeholder),
                    singleLine = false,
                )
            }
            OxideSecDivider()
            OxideSettingRow(
                label = stringResource(R.string.oxide_sec_mp_share_log),
                hint = stringResource(R.string.oxide_sec_mp_share_log_hint),
                enabled = enabled,
                onClick = onShareLog,
            )
            OxideSettingRow(
                label = stringResource(R.string.oxide_sec_mp_easytier),
                hint = stringResource(R.string.oxide_sec_mp_easytier_hint),
                onClick = onOpenEasyTier,
                trailing = {
                    Text(
                        text = "›",
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

/**
 * 开关行
 *
 * 整行可点、开关状态由整行的 `Role.Switch` 与 checked 暴露给无障碍服务；
 * 开关本身不再重复朗读，避免同一信息被念两遍。
 */
@Composable
private fun OxideMpToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    OxideSettingRow(
        label = label,
        hint = hint,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = {
            Box(modifier = Modifier.clearAndSetSemantics { }) {
                OxideToggle(checked = checked, onCheckedChange = onCheckedChange)
            }
        },
    )
}

/** 说明：用户须知 / 房主 / 房客，三个页签 */
@Composable
private fun OxideMpGuideCard(
    metrics: OxideMetrics,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        stringResource(R.string.oxide_sec_mp_tab_notice),
        stringResource(R.string.oxide_sec_mp_tab_host),
        stringResource(R.string.oxide_sec_mp_tab_guest),
    )
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    // 页签换了就把内容滚回顶部，否则会停在上一个页签读到的位置
    LaunchedEffect(tabIndex) { scrollState.scrollTo(0) }

    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(all = metrics.cardGap),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                tabs.forEachIndexed { index, tab ->
                    OxideSecChip(
                        label = tab,
                        selected = index == tabIndex,
                        metrics = metrics,
                        onClick = { tabIndex = index },
                    )
                }
            }
            Spacer(Modifier.height(metrics.secRowGap))
            OxideSecDivider()
            Spacer(Modifier.height(metrics.secRowGap))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                when (tabIndex) {
                    1 -> {
                        OxideMpGuideBlock(
                            title = stringResource(R.string.terracotta_tutorial_host_tip),
                            lines = listOf(
                                R.string.terracotta_tutorial_step_enable_multiplayer,
                                R.string.terracotta_tutorial_step_open_multiplayer_menu,
                                R.string.terracotta_tutorial_host_step_become_host,
                                R.string.terracotta_tutorial_host_step_open_lan,
                                R.string.terracotta_tutorial_step_vpn_permission,
                                R.string.terracotta_tutorial_host_step_copy_invite,
                                R.string.terracotta_tutorial_host_step_send_invite,
                            ),
                        )
                        OxideMpGuideBlock(
                            title = stringResource(R.string.terracotta_tutorial_note_title),
                            lines = listOf(
                                R.string.terracotta_tutorial_step_offline_account_support,
                                R.string.terracotta_tutorial_step_interoperability,
                            ),
                        )
                    }

                    2 -> {
                        OxideMpGuideBlock(
                            title = stringResource(R.string.terracotta_tutorial_guest_tip),
                            lines = listOf(
                                R.string.terracotta_tutorial_step_enable_multiplayer,
                                R.string.terracotta_tutorial_step_open_multiplayer_menu,
                                R.string.terracotta_tutorial_guest_step_become_guest,
                                R.string.terracotta_tutorial_step_vpn_permission,
                                R.string.terracotta_tutorial_guest_step_join_room,
                            ),
                        )
                        OxideMpGuideBlock(
                            title = stringResource(R.string.terracotta_tutorial_note_title),
                            lines = listOf(
                                R.string.terracotta_tutorial_step_offline_account_support,
                                R.string.terracotta_tutorial_step_interoperability,
                                R.string.terracotta_tutorial_guest_step_alternate_server,
                            ),
                        )
                    }

                    else -> OxideMpGuideBlock(
                        title = stringResource(R.string.terracotta_confirm_title),
                        lines = listOf(
                            R.string.terracotta_confirm_software,
                            R.string.terracotta_confirm_p2p,
                            R.string.terracotta_confirm_law,
                        ),
                    )
                }
            }
        }
    }
}

/** 说明里的一个小节：标题 + 若干步 */
@Composable
private fun OxideMpGuideBlock(title: String, lines: List<Int>) {
    Column {
        Text(
            text = title,
            color = Oxide.FgStrong,
            fontSize = Oxide.Type.Title.fontSize,
            lineHeight = Oxide.Type.Title.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        lines.forEach { lineRes ->
            Text(
                text = "· " + stringResource(lineRes),
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
            )
        }
        Spacer(Modifier.height(5.dp))
    }
}