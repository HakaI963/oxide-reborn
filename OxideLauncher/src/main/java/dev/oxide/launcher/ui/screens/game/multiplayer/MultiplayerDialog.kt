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

package dev.oxide.launcher.ui.screens.game.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.oxide.launcher.R
import dev.oxide.launcher.terracotta.TerracottaState
import dev.oxide.launcher.terracotta.profile.TerracottaProfile
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayBounds
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayButton
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayButtonTone
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayCardButton
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayFooter
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayHairline
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayHeader
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayIcon
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayNote
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayPanel
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayProgressBar
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayRowButton
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayScrollArea
import dev.oxide.launcher.ui.screens.game.elements.GameOverlayWorkingText
import dev.oxide.launcher.ui.screens.game.elements.gameOverlayBoundsFor
import dev.oxide.launcher.ui.screens.game.elements.multiplayerLogToggle
import dev.oxide.launcher.ui.screens.game.elements.rememberGameOverlayBounds
import dev.oxide.launcher.ui.theme.Oxide

sealed interface TerracottaLogOperation {
    /** 正常情况下，不展示日志内容，显示对话框 UI */
    data object None : TerracottaLogOperation

    /** 正在收集日志 */
    data object CollectingLog : TerracottaLogOperation

    /** 切换到展示日志的模式 */
    data class EnableLog(val logString: String) : TerracottaLogOperation
}

/**
 * 多人联机面板
 *
 * 八种联机状态一个没少，连接链路也没有动：
 * `onHostRoleClick` / `onGuestPositive` / `onHostCopyCode` / `onGuestCopyUrl`
 * 仍然分别交给 `TerracottaViewModel`，日志仍然由
 * [TerracottaLogOperation.CollectingLog] 与 `EnableLog` 这一对状态驱动。
 *
 * 换掉的是那张 Material 卡片与它周围的一整套排版：
 *
 * - 面板改成 [GameOverlayPanel]：**不透明**的 `BgElevated`、14dp 圆角、
 *   1px 发丝线。原来是 `cardColor(false)` 配 `shadowElevation = 6dp`——
 *   一层半透明的卡压在一块正在跑的游戏画面上。
 * - 尺寸由**承载游戏的那个窗口**夹住（见
 *   [rememberGameOverlayBounds][dev.oxide.launcher.ui.screens.game.elements.BoxWithConstraintsScope.rememberGameOverlayBounds]）。
 *   原来这一层是 `fillMaxWidth(0.7f)` 加 `rememberDialogMaxHeight()`，
 *   而弹窗自己的窗口 `MATCH_PARENT` 铺的是整块显示区：分屏或自由窗口下，
 *   一块只有屏幕十分之一的游戏上方会盖着一张按整屏算出来的面板。
 * - 每一片内容都在**被夹住的**滚动区里。联机日志动辄上千行，玩家列表也可能是
 *   二十个人，原来那些 `verticalScroll` 挂在一个 `weight(1f, fill = false)`
 *   上，高度上限只来自"面板高度 − 标题"，而面板高度又是按显示区算的——
 *   这就是那个 P0 崩溃的路子。现在每一块内容都先被 `contentMaxHeight` 夹住，
 *   再在**自己这一块**里滚。
 * - `LoadingIndicator` 与 `LinearProgressIndicator` 换成了不发光的进度条：
 *   Material 的 `LoadingIndicator` 是一段永不停歇的动画，叠在一块正在跑的游戏上
 *   既吵又耗电，而且**不提供任何新信息**——这一层只知道"在等"，不知道等多久。
 * - 玩家名与厂商名原来是无限跑马灯。跑马灯每帧重新测量一次文本，
 *   而这块面板可能整晚开着；现在是单行截断。
 *
 * 底栏那两个按钮的行为一字未改：看日志 / 刷新换字，收集期间不可点
 * （见 [multiplayerLogToggle]）。
 */
@Composable
fun MultiplayerDialog(
    onClose: () -> Unit,
    dialogState: TerracottaState.Ready?,
    logOperation: TerracottaLogOperation,
    onShowLog: () -> Unit,
    onHideLog: () -> Unit,
    isWaitingInteractive: Boolean,
    terracottaVer: String?,
    easyTierVer: String?,
    profiles: List<TerracottaProfile>,
    onHostRoleClick: () -> Unit,
    onHostCopyCode: (TerracottaState.HostOK) -> Unit,
    onGuestPositive: (roomCode: String) -> Unit,
    onGuestCopyUrl: (TerracottaState.GuestOK) -> Unit,
    onBack: () -> Unit,
    onShowToast: (AndroidStringText) -> Unit = {}
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val bounds = rememberGameOverlayBounds()
            GameOverlayPanel(bounds = bounds) {
                GameOverlayHeader(
                    title = stringResource(R.string.terracotta_menu),
                    bounds = bounds,
                )
                GameOverlayHairline()

                // 面板高度按内容摆、只被 panelMaxHeight 夹住，因此这里不用 weight：
// 每一片内容自己按 contentMaxHeight 夹住，超出的在**自己这一块**里滚。
// 面板那点总高 = contentMaxHeight + 标题栏 + 底栏，正好不超过上限。
                val contentModifier = Modifier.padding(
                    start = bounds.padding,
                    end = bounds.padding,
                    top = bounds.rowGap,
                )

                when (logOperation) {
                    is TerracottaLogOperation.None, TerracottaLogOperation.CollectingLog -> {
                        when (dialogState) {
                            null -> WaitingCoreRow(
                                bounds = bounds,
                                modifier = contentModifier,
                            )

                            is TerracottaState.Waiting -> WaitingUI(
                                bounds = bounds,
                                modifier = contentModifier,
                                onHostClick = onHostRoleClick,
                                onGuestPositive = onGuestPositive,
                                isInteractive = isWaitingInteractive,
                                onShowToast = onShowToast
                            )

                            is TerracottaState.HostScanning -> CommonProgressLayout(
                                bounds = bounds,
                                modifier = contentModifier,
                                progress = stringResource(R.string.terracotta_status_host_scanning),
                                text = {
                                    GameOverlayNote(
                                        text = stringResource(
                                            R.string.terracotta_status_host_scanning_desc
                                        )
                                    )
                                },
                                backDescription = stringResource(
                                    R.string.terracotta_status_host_scanning_back
                                ),
                                onBack = onBack
                            )

                            is TerracottaState.HostStarting -> CommonProgressLayout(
                                bounds = bounds,
                                modifier = contentModifier,
                                progress = stringResource(R.string.terracotta_status_host_starting),
                                backDescription = stringResource(
                                    R.string.terracotta_status_host_starting_back
                                ),
                                onBack = onBack
                            )

                            is TerracottaState.HostOK -> OkRoomUI(
                                bounds = bounds,
                                modifier = contentModifier,
                                code = dialogState.code ?: "",//不会为null
                                profiles = profiles,
                                onCopy = {
                                    onHostCopyCode(dialogState)
                                },
                                onExit = onBack,
                                okText = stringResource(R.string.terracotta_status_host_ok),
                                codeLabel = stringResource(R.string.terracotta_status_host_ok_code),
                                copyTitle = stringResource(R.string.terracotta_status_host_ok_code_copy),
                                copyDesc = stringResource(R.string.terracotta_status_host_ok_code_desc),
                                backDesc = stringResource(R.string.terracotta_status_host_ok_back)
                            )

                            is TerracottaState.GuestConnecting -> CommonProgressLayout(
                                bounds = bounds,
                                modifier = contentModifier,
                                progress = stringResource(R.string.terracotta_status_guest_starting),
                                backDescription = stringResource(
                                    R.string.terracotta_status_guest_starting_back
                                ),
                                onBack = onBack
                            )

                            is TerracottaState.GuestStarting -> GuestStartingUI(
                                bounds = bounds,
                                modifier = contentModifier,
                                difficulty = dialogState.difficulty,
                                onBack = onBack
                            )

                            is TerracottaState.GuestOK -> OkRoomUI(
                                bounds = bounds,
                                modifier = contentModifier,
                                code = dialogState.url ?: "",
                                profiles = profiles,
                                onCopy = {
                                    onGuestCopyUrl(dialogState)
                                },
                                onExit = onBack,
                                okText = stringResource(R.string.terracotta_status_guest_ok),
                                codeLabel = stringResource(R.string.terracotta_status_guest_ok_address),
                                copyTitle = stringResource(R.string.terracotta_status_guest_ok_address_copy),
                                copyDesc = stringResource(R.string.terracotta_status_guest_ok_address_desc),
                                backDesc = stringResource(R.string.terracotta_status_guest_ok_back)
                            )

                            is TerracottaState.Exception -> ExceptionUI(
                                bounds = bounds,
                                modifier = contentModifier,
                                title = stringResource(dialogState.getEnumType().textRes),
                                onExit = onBack
                            )
                        }
                    }

                    is TerracottaLogOperation.EnableLog -> LogUI(
                        bounds = bounds,
                        modifier = contentModifier,
                        logString = logOperation.logString,
                        onExit = onHideLog
                    )
                }

                Spacer(Modifier.height(bounds.rowGap))
                GameOverlayHairline()
                GameOverlayFooter(bounds = bounds) {
                    //版本号
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        val terracottaVer0 = terracottaVer ?: stringResource(R.string.generic_loading)
                        val easyTierVer0 = easyTierVer ?: stringResource(R.string.generic_loading)
                        GameOverlayNote(text = stringResource(R.string.terracotta_metadata_ver, terracottaVer0))
                        GameOverlayNote(text = stringResource(R.string.terracotta_metadata_easytier_ver, easyTierVer0))
                    }

                    //查看日志 / 刷新
                    val toggle = multiplayerLogToggle(
                        showingLog = logOperation is TerracottaLogOperation.EnableLog,
                        collectingLog = logOperation is TerracottaLogOperation.CollectingLog,
                    )
                    GameOverlayButton(
                        text = if (toggle.isRefresh) {
                            stringResource(R.string.generic_refresh)
                        } else {
                            stringResource(R.string.terracotta_log)
                        },
                        onClick = onShowLog,
                        minHeight = bounds.buttonHeight,
                        enabled = toggle.enabled,
                    )

                    //关闭
                    GameOverlayButton(
                        text = stringResource(R.string.generic_close),
                        onClick = onClose,
                        minHeight = bounds.buttonHeight,
                        tone = GameOverlayButtonTone.Primary,
                    )
                }
            }
        }
    }
}

/**
 * 联机核心还没起来：只有一条说不清进度的槽，和一句"正在做什么"
 *
 * 原来是 Material 的 `LoadingIndicator`，一段无意义的永动动画。
 * 这里刻意不循环：[GameOverlayWorkingText] 已经说明正在加载，
 * 而一个停不下来的动画只会让这块面板一直亮着。
 */
@Composable
private fun WaitingCoreRow(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        GameOverlayProgressBar(progress = null)
        GameOverlayNote(text = GameOverlayWorkingText())
    }
}

/**
 * 等待选择角色
 */
@Composable
private fun WaitingUI(
    bounds: GameOverlayBounds,
    isInteractive: Boolean,
    onHostClick: () -> Unit,
    onGuestPositive: (roomCode: String) -> Unit,
    modifier: Modifier = Modifier,
    onShowToast: (AndroidStringText) -> Unit = {},
) {
    var guestOperation by remember { mutableStateOf<GuestWaitingOperation>(GuestWaitingOperation.None) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        GameOverlayScrollArea(maxHeight = bounds.contentMaxHeight) {
            //房主
            GameOverlayCardButton(
                icon = painterResource(R.drawable.ic_home_filled),
                title = stringResource(R.string.terracotta_status_waiting_host_title),
                description = stringResource(R.string.terracotta_status_waiting_host_desc),
                onClick = onHostClick,
                minHeight = bounds.buttonHeight,
                enabled = isInteractive
            )

            //房客
            GameOverlayCardButton(
                icon = painterResource(R.drawable.ic_group_filled),
                title = stringResource(R.string.terracotta_status_waiting_guest_title),
                description = stringResource(R.string.terracotta_status_waiting_guest_desc),
                onClick = {
                    guestOperation = GuestWaitingOperation.OnClick
                },
                minHeight = bounds.buttonHeight,
                enabled = isInteractive
            )
        }

        //禁止交互时，提示用户正在加载中。
        //原来是一枚盖在两张卡片正中的 Material LoadingIndicator：既看不见
        //「正在做什么」，又一直停不下来。现在是槽加一句说明，卡片本身也变灰了。
        if (!isInteractive) {
            GameOverlayProgressBar(progress = null)
            GameOverlayNote(text = GameOverlayWorkingText())
        }
    }

    GuestWaitingOperation(
        operation = guestOperation,
        onChange = { guestOperation = it },
        onPositive = onGuestPositive,
        onShowToast = onShowToast
    )
}

@Preview(showBackground = true)
@Composable
private fun WaitingUIPreview() {
    WaitingUI(
        bounds = gameOverlayBoundsFor(420, 320),
        isInteractive = true,
        onHostClick = {},
        onGuestPositive = {}
    )
}

/**
 * 房客开始中
 */
@Composable
private fun GuestStartingUI(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    difficulty: TerracottaState.GuestStarting.Difficulty,
    onBack: () -> Unit
) {
    CommonProgressLayout(
        bounds = bounds,
        modifier = modifier,
        progress = stringResource(R.string.terracotta_status_guest_starting),
        text = if (difficulty != TerracottaState.GuestStarting.Difficulty.UNKNOWN) (@Composable {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameOverlayIcon(
                    painter = when (difficulty) {
                        TerracottaState.GuestStarting.Difficulty.EASIEST,
                        TerracottaState.GuestStarting.Difficulty.SIMPLE ->
                            painterResource(R.drawable.ic_info_filled)
                        else -> painterResource(R.drawable.ic_warning_filled)
                    },
                    contentDescription = null,
                    size = 13.dp,
                    tint = Oxide.FgMuted,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(difficulty.textRes),
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )
                    GameOverlayNote(
                        text = stringResource(R.string.terracotta_difficulty_estimate_only)
                    )
                }
            }
        }) else null,
        backDescription = stringResource(R.string.terracotta_status_guest_starting_back),
        onBack = onBack
    )
}

@Preview(showBackground = true)
@Composable
private fun GuestStartingUIPreview() {
    GuestStartingUI(
        bounds = gameOverlayBoundsFor(420, 320),
        difficulty = TerracottaState.GuestStarting.Difficulty.UNKNOWN,
        onBack = {}
    )
}

/**
 * 已进入房间
 */
@Composable
private fun OkRoomUI(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    code: String,
    profiles: List<TerracottaProfile>,
    onCopy: () -> Unit,
    onExit: () -> Unit,
    okText: String,
    codeLabel: String,
    copyTitle: String,
    copyDesc: String,
    backTitle: String = stringResource(R.string.terracotta_back),
    backDesc: String,
    profilesLabel: String = stringResource(R.string.terracotta_player_list)
) {
    // 整块内容是一列：邀请码、两个动作、玩家名单。
    // 它是**一个 LazyColumn，而不是 Column + verticalScroll**——纵向滚动的容器把高度
    // 上限交给子节点，里面再放一个 LazyColumn 就会拿到无穷大的 maxHeight 而崩。
    // 高度先被 [GameOverlayBounds.contentMaxHeight] 夹住，再由这一个列表自己滚：
    // 因此面板的高度与房间人数、与日志长度都无关。
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = bounds.contentMaxHeight),
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        item(key = "code") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = okText,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                )
                GameOverlayHairline()
                GameOverlayNote(text = codeLabel)
                //邀请码 / 地址：等宽，这一行换行与否都不影响可读性
                Text(
                    text = code,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Mono.fontSize,
                    lineHeight = Oxide.Type.Mono.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "copy") {
            //复制按钮
            GameOverlayRowButton(
                icon = painterResource(R.drawable.ic_copy_all_filled),
                title = copyTitle,
                description = copyDesc,
                onClick = onCopy,
                minHeight = bounds.buttonHeight
            )
        }
        item(key = "back") {
            //退出按钮
            GameOverlayRowButton(
                icon = painterResource(R.drawable.ic_arrow_back),
                title = backTitle,
                description = backDesc,
                onClick = onExit,
                minHeight = bounds.buttonHeight
            )
        }
        item(key = "profilesHeader") {
            ProfileListHeader(title = profilesLabel, count = profiles.size)
        }
        if (profiles.isEmpty()) {
            item(key = "profilesEmpty") {
                GameOverlayNote(text = stringResource(R.string.oxide_ingame_mp_no_players))
            }
        } else {
            items(items = profiles, key = { it.toString() }) { profile ->
                TerracottaProfileLayout(
                    modifier = Modifier.fillMaxWidth(),
                    profile = profile
                )
            }
        }
    }
}

/** 玩家名单的标题行：名单名 + 真实人数 */
@Composable
private fun ProfileListHeader(title: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Oxide.Fg,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 人数是真实读数，写出来而不是靠"列表有多长"去猜
            Text(
                text = count.toString(),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
            )
        }
        GameOverlayHairline()
    }
}

@Composable
private fun TerracottaProfileLayout(
    profile: TerracottaProfile,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        //玩家名字与身份：名字是玩家自己取的，可能很长，因此截断而不是跑马灯
        Text(
            text = profile.name ?: stringResource(R.string.terracotta_player_anonymous),
            color = Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        //身份/类别
        Text(
            text = stringResource(profile.type.textRes),
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        //厂商
        Text(
            text = profile.vendor,
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 出现错误
 */
@Composable
private fun ExceptionUI(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    title: String,
    onExit: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        GameOverlayScrollArea(maxHeight = bounds.contentMaxHeight) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                )
                GameOverlayHairline()
                GameOverlayNote(text = stringResource(R.string.terracotta_export_log))
            }
        }
        //退出按钮
        GameOverlayRowButton(
            icon = painterResource(R.drawable.ic_arrow_back),
            title = stringResource(R.string.terracotta_back),
            description = stringResource(R.string.terracotta_status_exception_back),
            onClick = onExit,
            minHeight = bounds.buttonHeight
        )
    }
}

/**
 * 展示日志
 *
 * 联机核心的日志动辄上千行。它在**被夹住的**滚动区里，
 * 所以面板的高度与日志长度无关。
 */
@Composable
private fun LogUI(
    bounds: GameOverlayBounds,
    logString: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        GameOverlayScrollArea(maxHeight = bounds.contentMaxHeight) {
            // 等宽：日志是对齐的，换成比例字体会把时间戳那一列推歪
            Text(
                text = logString,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Mono.fontSize,
                lineHeight = Oxide.Type.Mono.lineHeight,
            )
        }
        //退出按钮
        GameOverlayRowButton(
            icon = painterResource(R.drawable.ic_arrow_back),
            title = stringResource(R.string.terracotta_back),
            description = stringResource(R.string.terracotta_log_exit),
            onClick = onExit,
            minHeight = bounds.buttonHeight
        )
    }
}

@Composable
private fun CommonProgressLayout(
    bounds: GameOverlayBounds,
    modifier: Modifier = Modifier,
    progress: String,
    backTitle: String = stringResource(R.string.terracotta_back),
    backDescription: String,
    onBack: () -> Unit,
    text: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(bounds.rowGap),
    ) {
        GameOverlayScrollArea(maxHeight = bounds.contentMaxHeight) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = progress,
                    color = Oxide.Fg,
                    fontSize = Oxide.Type.BodyStrong.fontSize,
                    lineHeight = Oxide.Type.BodyStrong.lineHeight,
                )
                // 这些状态都只知道"在等"，不知道等多久：进度因此不可知
                GameOverlayProgressBar(progress = null)
                text?.invoke()
            }
        }
        //退出按钮
        GameOverlayCardButton(
            icon = painterResource(R.drawable.ic_arrow_left_rounded),
            title = backTitle,
            description = backDescription,
            onClick = onBack,
            minHeight = bounds.buttonHeight
        )
    }
}