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

import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.BuildConfig
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountsManager
import dev.oxide.launcher.game.account.getAccountTypeName
import dev.oxide.launcher.game.path.getGameHome
import dev.oxide.launcher.game.renderer.Renderers
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.installed.VersionFolders
import dev.oxide.launcher.game.version.installed.VersionType
import dev.oxide.launcher.game.version.installed.VersionsManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.file.formatFileSize
import dev.oxide.launcher.viewmodel.EventViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页
 *
 * 结构与参考稿的 HOME 一致：左侧是一块撑满剩余高度的 hero（问候语 + Play + 配置入口），
 * 右侧竖排账号卡与环境面板，底部横贯一条当前实例条。
 *
 * 紧凑档没有并排放两张卡的宽度，于是改成单列并允许整页滚动——
 * 比把信息直接藏起来更诚实，也比在小屏上裁切更可靠。
 *
 * 页面上的每个数值都来自真实来源：版本与账号来自 [VersionsManager] / [AccountsManager]，
 * 设备与安装信息来自系统，磁盘用量在 IO 线程上统计。
 * **拿不到就不显示这一行**，界面上不会出现占位符或假数据。
 */

/** 环境面板里的一行：左边的弱化标签与右边的值 */
@Immutable
private data class OxideHomeEnvRow(val label: String, val value: String)

/**
 * 需要读磁盘或问包管理器才能得到的几个值
 *
 * 统计一律放到 IO 线程；算不到就是 null，对应的行直接不渲染。
 */
@Immutable
private data class OxideHomeSnapshot(
    val installSource: String? = null,
    val storageUsed: String? = null,
    val storageFree: String? = null,
    val modsCount: Int? = null,
    val lastPlayedAt: Long? = null,
)

@Composable
fun OxideHomePage(
    metrics: OxideMetrics,
    onNavigate: (OxidePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // MainActivity 用 by viewModels() 持有同一个 EventViewModel，viewModel() 取到的就是同一实例，
    // 因此这里发出的启动事件仍然会走 MainActivity 里那条完整的启动流程
    // （包含无版本 / 无账号时的提示与跳转，以及快速加载插桩）。
    val eventViewModel: EventViewModel = viewModel()

    val currentVersion by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val isRefreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()

    // 错峰进场只在首次组合时触发一次
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val snapshot = rememberHomeSnapshot(currentVersion)

    // 当前实例的真实信息。全部走 remember：这些取值都只读内存里已经解析好的数据，
    // 不在组合阶段碰文件系统。
    val versionName = currentVersion?.getVersionName()
    val versionSummary = remember(currentVersion) {
        currentVersion?.let { runCatching { it.getVersionSummary() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
    }
    val loaderName = remember(currentVersion) {
        currentVersion?.getVersionInfo()?.primaryLoader
            ?.takeIf { it.loader.displayName.isNotBlank() }
            ?.let { "${it.loader.displayName} ${it.version}".trim() }
    }
    val rendererName = remember(currentVersion) {
        currentVersion?.let { version ->
            Renderers.getRenderers()
                .find { it.getUniqueIdentifier() == version.getRenderer() }
                ?.getRendererName()
                ?.takeIf { it.isNotBlank() }
        }
    }
    val javaName = remember(currentVersion) {
        val perVersion = currentVersion?.getJavaRuntime().orEmpty()
        perVersion.ifBlank { AllSettings.javaRuntime.getValue() }.takeIf { it.isNotBlank() }
    }
    val ramMb = remember(currentVersion) { currentVersion?.getRamAllocation() }
    val typeLabel = when (currentVersion?.versionType) {
        VersionType.VANILLA -> stringResource(R.string.oxide_home_type_vanilla)
        VersionType.MODLOADERS -> stringResource(R.string.oxide_home_type_loader)
        VersionType.UNKNOWN, null -> stringResource(R.string.oxide_home_type_unknown)
    }
    val deviceModel = rememberDeviceModel()

    // 环境面板：只塞真实拿到的值，缺哪项就少哪一行
    val envRows = buildList {
        loaderName?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_loader), it))
        }
        rendererName?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_renderer), it))
        }
        javaName?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_java), it))
        }
        ramMb?.let {
            add(
                OxideHomeEnvRow(
                    label = stringResource(R.string.oxide_home_env_memory),
                    value = stringResource(R.string.oxide_home_memory_value, it)
                )
            )
        }
        deviceModel?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_device), it))
        }
        Build.VERSION.RELEASE.takeIf { it.isNotBlank() }?.let { release ->
            add(
                OxideHomeEnvRow(
                    label = stringResource(R.string.oxide_home_env_android),
                    value = stringResource(
                        R.string.oxide_home_android_version_value,
                        release,
                        Build.VERSION.SDK_INT
                    )
                )
            )
        }
        Build.SUPPORTED_ABIS.firstOrNull()?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_architecture), it))
        }
        BuildConfig.VERSION_NAME.takeIf { it.isNotBlank() }?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_launcher), it))
        }
        snapshot.installSource?.let {
            add(OxideHomeEnvRow(stringResource(R.string.oxide_home_env_source), it))
        }
        snapshot.storageUsed?.let { used ->
            val free = snapshot.storageFree
            add(
                OxideHomeEnvRow(
                    label = stringResource(R.string.oxide_home_env_storage),
                    value = if (free != null) {
                        stringResource(R.string.oxide_home_env_storage_value, used, free)
                    } else {
                        used
                    }
                )
            )
        }
    }

    val kickerStatus = if (tasks.isEmpty()) {
        stringResource(R.string.oxide_home_status_ready)
    } else {
        stringResource(R.string.oxide_home_status_busy, tasks.size)
    }
    val kicker = stringResource(R.string.oxide_home_kicker) + " · " + kickerStatus

    // 没有当前版本时也照样发事件：MainActivity 会把它变成既有的
    // LaunchGameOperation.NoVersion 提示与跳转，而不是这里自己编一套。
    val playCurrent = {
        eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(currentVersion))
    }
    // 实例选择器与实例配置抽屉还没接上，先把人送到实例页。
    val goInstances = { onNavigate(OxidePage.Instances) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentWidth = maxWidth

        if (metrics.widthClass == OxideWidthClass.Compact) {
            OxideHomeCompactBody(
                metrics = metrics,
                contentWidth = contentWidth,
                revealed = revealed,
                kicker = kicker,
                playEnabled = !isRefreshing,
                onPlay = playCurrent,
                account = account,
                envRows = envRows,
                versionBadge = versionName,
                versionName = versionName,
                versionDetail = loaderName ?: versionSummary,
                isRefreshing = isRefreshing,
                typeLabel = typeLabel,
                lastPlayedAt = snapshot.lastPlayedAt,
                modsCount = snapshot.modsCount,
                onGoInstances = goInstances,
            )
        } else {
            OxideHomeWideBody(
                metrics = metrics,
                revealed = revealed,
                kicker = kicker,
                playEnabled = !isRefreshing,
                onPlay = playCurrent,
                account = account,
                envRows = envRows,
                versionBadge = versionName,
                versionName = versionName,
                versionDetail = loaderName ?: versionSummary,
                isRefreshing = isRefreshing,
                typeLabel = typeLabel,
                lastPlayedAt = snapshot.lastPlayedAt,
                modsCount = snapshot.modsCount,
                onGoInstances = goInstances,
            )
        }
    }
}

/** 宽屏布局：左 hero + 右竖排栏，下面一条横贯的实例条 */
@Composable
private fun OxideHomeWideBody(
    metrics: OxideMetrics,
    revealed: Boolean,
    kicker: String,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    account: Account?,
    envRows: List<OxideHomeEnvRow>,
    versionBadge: String?,
    versionName: String?,
    versionDetail: String?,
    isRefreshing: Boolean,
    typeLabel: String,
    lastPlayedAt: Long?,
    modsCount: Int?,
    onGoInstances: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            OxideReveal(
                visible = revealed,
                index = 0,
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
            ) {
                OxideHomeHero(
                    metrics = metrics,
                    kicker = kicker,
                    playEnabled = playEnabled,
                    onPlay = onPlay,
                    onConfigure = onGoInstances,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(metrics.cardGap))
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                OxideReveal(
                    visible = revealed,
                    index = 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OxideHomeAccountCard(
                        onManage = LocalOxideHostActions.current.openAccountManager,
                        metrics = metrics,
                        account = account,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(metrics.cardGap))
                OxideReveal(
                    visible = revealed,
                    index = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    OxideHomeEnvironmentCard(
                        metrics = metrics,
                        rows = envRows,
                        versionBadge = versionBadge,
                        fillHeight = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(metrics.cardGap))
        OxideReveal(
            visible = revealed,
            index = 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            OxideHomeInstanceStrip(
                metrics = metrics,
                versionName = versionName,
                versionDetail = versionDetail,
                isRefreshing = isRefreshing,
                typeLabel = typeLabel,
                lastPlayedAt = lastPlayedAt,
                modsCount = modsCount,
                onGoInstances = onGoInstances,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 紧凑档布局：单列并允许整页滚动
 *
 * 账号卡与环境面板按 [OxideMetrics.gridColumns] 决定并排还是叠放，
 * 因此这一栏在 560dp 的小屏和 660dp 的大屏横屏上都各得其所。
 */
@Composable
private fun OxideHomeCompactBody(
    metrics: OxideMetrics,
    contentWidth: Dp,
    revealed: Boolean,
    kicker: String,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    account: Account?,
    envRows: List<OxideHomeEnvRow>,
    versionBadge: String?,
    versionName: String?,
    versionDetail: String?,
    isRefreshing: Boolean,
    typeLabel: String,
    lastPlayedAt: Long?,
    modsCount: Int?,
    onGoInstances: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        OxideReveal(visible = revealed, index = 0) {
            OxideHomeHero(
                metrics = metrics,
                kicker = kicker,
                playEnabled = playEnabled,
                onPlay = onPlay,
                onConfigure = onGoInstances,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.heroTitleDp.dp * 3f)
            )
        }
        Spacer(Modifier.height(metrics.sectionGap))
        OxideReveal(visible = revealed, index = 1) {
            OxideHomeInstanceStrip(
                metrics = metrics,
                versionName = versionName,
                versionDetail = versionDetail,
                isRefreshing = isRefreshing,
                typeLabel = typeLabel,
                lastPlayedAt = lastPlayedAt,
                modsCount = modsCount,
                onGoInstances = onGoInstances,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(metrics.sectionGap))
        if (metrics.gridColumns(contentWidth) >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap)) {
                OxideReveal(
                    visible = revealed,
                    index = 2,
                    modifier = Modifier.weight(1f)
                ) {
                    OxideHomeAccountCard(
                        onManage = LocalOxideHostActions.current.openAccountManager,
                        metrics = metrics,
                        account = account,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OxideReveal(
                    visible = revealed,
                    index = 3,
                    modifier = Modifier.weight(1f)
                ) {
                    OxideHomeEnvironmentCard(
                        metrics = metrics,
                        rows = envRows,
                        versionBadge = versionBadge,
                        fillHeight = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            OxideReveal(visible = revealed, index = 2) {
                OxideHomeAccountCard(
                        onManage = LocalOxideHostActions.current.openAccountManager,
                    metrics = metrics,
                    account = account,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(metrics.sectionGap))
            OxideReveal(visible = revealed, index = 3) {
                OxideHomeEnvironmentCard(
                    metrics = metrics,
                    rows = envRows,
                    versionBadge = versionBadge,
                    fillHeight = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Hero：问候语 + Play + 配置入口
 *
 * 参考稿右上角有一团径向高光并配了 10 秒的呼吸动画。这里保留高光但**不做动画**——
 * 常驻的无限动画会在横屏里一直占用合成线程，而这一屏本来就不该有持续运动。
 */
@Composable
private fun OxideHomeHero(
    metrics: OxideMetrics,
    kicker: String,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = metrics.heroTitleDp.dp * 8f
    val microGap = metrics.cardGap * 0.3f
    val configureDescription = stringResource(R.string.oxide_home_configure_description)

    Box(
        modifier = modifier
            .clip(Oxide.RadiusCard)
            .background(Oxide.SurfaceBase)
            .background(Oxide.SurfaceBrush)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusCard)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -glow * 0.38f, y = -glow * 0.5f)
                .size(glow)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(
                        0f to Color(0x1AFFFFFF),
                        1f to Color.Transparent
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = metrics.cardGap * 2f,
                    end = metrics.cardGap * 2f,
                    top = metrics.cardGap * 2f,
                    bottom = metrics.cardGap * 2f
                )
        ) {
            Text(
                text = kicker,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(metrics.cardGap))
            Text(
                text = stringResource(R.string.oxide_home_title),
                color = Oxide.Fg,
                fontSize = metrics.heroTitleDp.sp,
                lineHeight = metrics.heroTitleDp.sp * 0.96f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.6).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(microGap * 2f))
            Text(
                text = stringResource(R.string.oxide_home_subtitle),
                color = Oxide.FgFaint,
                fontSize = metrics.scaled(Oxide.Type.Body.fontSize.value).sp,
                lineHeight = metrics.scaled(Oxide.Type.Body.lineHeight.value).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
            Spacer(Modifier.height(metrics.cardGap * 1.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.cardGap)) {
                OxideButton(
                    text = stringResource(R.string.oxide_home_play),
                    onClick = onPlay,
                    enabled = playEnabled,
                    tone = OxideButtonTone.Primary
                )
                OxideIconButton(
                    onClick = onConfigure,
                    glyph = "≡",
                    // mergeDescendants：图标按钮的 modifier 排在它内部链的最前面，
                    // 只有自己合并子树，无障碍朗读才不会把它当成一个没有动作的孤立节点
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = configureDescription
                    }
                )
            }
        }
    }
}

/** 账号卡：真实的当前账号；没有账号时如实说明，不放没有去处的按钮 */
@Composable
private fun OxideHomeAccountCard(
    metrics: OxideMetrics,
    account: Account?,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(all = metrics.cardGap * 1.3f)
    ) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_home_account_label),
            trailing = if (account == null) {
                {
                    OxideBadge(
                        text = stringResource(R.string.oxide_home_account_required),
                        tone = OxideBadgeTone.Warn
                    )
                }
            } else {
                {
                    OxideButton(
                        text = stringResource(R.string.page_title_account_list),
                        onClick = onManage,
                        tone = OxideButtonTone.Ghost,
                    )
                }
            }
        )
        Spacer(Modifier.height(metrics.cardGap))
        if (account != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OxideHomeAvatar(
                    initial = account.username.trim().take(1).uppercase(),
                    modifier = Modifier.size(Oxide.MarkSize)
                )
                Spacer(Modifier.width(metrics.cardGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        color = Oxide.Fg,
                        fontSize = Oxide.Type.Title.fontSize,
                        lineHeight = Oxide.Type.Title.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(metrics.cardGap * 0.15f))
                    Text(
                        text = getAccountTypeName(account),
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            OxideEmptyState(
                title = stringResource(R.string.oxide_home_account_none),
                detail = stringResource(R.string.oxide_home_account_none_detail),
                action = {
                    OxideButton(
                        text = stringResource(R.string.account_login),
                        onClick = onManage,
                        tone = OxideButtonTone.Primary,
                    )
                },
            )
        }
    }
}

/** 参考稿的头像：斜向渐变方块加首字母。不读皮肤文件，因此组合阶段不碰磁盘。 */
@Composable
private fun OxideHomeAvatar(
    initial: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Oxide.RadiusBlock)
            .background(Brush.linearGradient(listOf(Oxide.FgMuted, Oxide.FgNum)))
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusBlock),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color(0xFF111111),
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * 环境面板
 *
 * [rows] 里只有真实拿到的值。行高跟着 [OxideMetrics.navItemHeight] 走；
 * 行数超出面板高度时在面板内部滚动，而不是把卡片撑破。
 */
@Composable
private fun OxideHomeEnvironmentCard(
    metrics: OxideMetrics,
    rows: List<OxideHomeEnvRow>,
    versionBadge: String?,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier,
        contentPadding = PaddingValues(all = metrics.cardGap * 1.3f)
    ) {
        OxideSectionLabel(
            text = stringResource(R.string.oxide_home_environment_label),
            trailing = if (versionBadge != null) {
                { OxideBadge(text = versionBadge) }
            } else {
                null
            }
        )
        Spacer(Modifier.height(metrics.cardGap * 0.6f))
        if (rows.isEmpty()) {
            OxideLoadingRow(stringResource(R.string.oxide_common_loading))
        } else {
            // 只有在父级给了确定高度时才允许 weight：可滚动容器里的 height 约束是无穷大，
            // 那种情况下 weight 会把这一块压成 0。
            val listModifier = if (fillHeight) {
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.verticalScroll(rememberScrollState())
            }
            Column(modifier = listModifier) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Oxide.LineFaint)
                        )
                    }
                    OxideSettingRow(
                        label = row.label,
                        value = row.value,
                        modifier = Modifier.heightIn(min = metrics.navItemHeight)
                    )
                }
            }
        }
    }
}

/** 底部横贯的当前实例条 */
@Composable
private fun OxideHomeInstanceStrip(
    metrics: OxideMetrics,
    versionName: String?,
    versionDetail: String?,
    isRefreshing: Boolean,
    typeLabel: String,
    lastPlayedAt: Long?,
    modsCount: Int?,
    onGoInstances: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val triggerHeight = metrics.navItemHeight + metrics.cardGap
    val labelAllowance = Oxide.Type.MicroLabel.lineHeight.value.dp
    val microGap = metrics.cardGap * 0.3f
    // 条带高度按内容反推：上下留白 + 小标题 + 标题与触发器之间的间距 + 触发器本身，
    // 多留一个 microGap 的余量，字体缩放变大时也不会把触发器挤扁
    val stripHeight = triggerHeight + labelAllowance + microGap * 2f + metrics.cardGap * 2f
    val valueFontSize = metrics.scaled(Oxide.Type.Body.fontSize.value).sp
    val valueLineHeight = metrics.scaled(Oxide.Type.Body.lineHeight.value).sp

    OxideSurface(
        modifier = modifier.height(stripHeight),
        contentPadding = PaddingValues(all = metrics.cardGap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (versionName == null) {
                // 没有可用版本时不编造：如实说明，并把人送到实例页
                if (isRefreshing) {
                    OxideLoadingRow(
                        text = stringResource(R.string.oxide_common_loading),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = metrics.cardGap),
                        verticalArrangement = Arrangement.spacedBy(microGap)
                    ) {
                        Text(
                            text = stringResource(R.string.oxide_home_instance_none),
                            color = Oxide.FgMuted,
                            fontSize = Oxide.Type.BodyStrong.fontSize,
                            lineHeight = Oxide.Type.BodyStrong.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.oxide_home_instance_none_detail),
                            color = Oxide.FgFaint,
                            fontSize = Oxide.Type.MicroLabel.fontSize,
                            lineHeight = Oxide.Type.MicroLabel.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OxideButton(
                        text = stringResource(R.string.oxide_home_instance_open),
                        onClick = onGoInstances,
                        tone = OxideButtonTone.Secondary
                    )
                }
                return@Row
            }

            Column(
                modifier = Modifier.weight(1.34f),
                verticalArrangement = Arrangement.Center
            ) {
                OxideSectionLabel(stringResource(R.string.oxide_home_instance_label))
                Spacer(Modifier.height(microGap * 1.2f))
                OxideHomeInstanceTrigger(
                    metrics = metrics,
                    title = versionName,
                    subtitle = versionDetail ?: typeLabel,
                    height = triggerHeight,
                    onClick = onGoInstances,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(metrics.cardGap))
            Column(
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.Center
            ) {
                OxideSectionLabel(stringResource(R.string.oxide_home_last_played))
                Spacer(Modifier.height(microGap * 1.2f))
                Text(
                    text = lastPlayedAt?.let { relativeTime(it) }
                        ?: stringResource(R.string.oxide_home_never),
                    color = Oxide.FgMuted,
                    fontSize = valueFontSize,
                    lineHeight = valueLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeLabel,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(metrics.cardGap))
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.Center
            ) {
                OxideSectionLabel(stringResource(R.string.oxide_home_content))
                Spacer(Modifier.height(microGap * 1.2f))
                Text(
                    text = when (modsCount) {
                        null -> stringResource(R.string.oxide_common_loading)
                        0 -> stringResource(R.string.oxide_home_content_no_mods)
                        else -> stringResource(R.string.oxide_home_content_mods, modsCount)
                    },
                    color = Oxide.FgMuted,
                    fontSize = valueFontSize,
                    lineHeight = valueLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeLabel,
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(metrics.cardGap))
            OxideButton(
                text = stringResource(R.string.oxide_home_configure),
                onClick = onGoInstances
            )
        }
    }
}

/**
 * 实例触发器
 *
 * 语义上标了 `selected`：当前实例不能只靠底色区分，朗读时也要知道它是"被选中的那一个"。
 * 接入实例选择器之前，点击先把人送到实例页。
 */
@Composable
private fun OxideHomeInstanceTrigger(
    metrics: OxideMetrics,
    title: String,
    subtitle: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgButton)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusControl)
            .clickable(onClick = onClick)
            // 放在 clickable 之后，语义会被可点击节点合并进去，
            // 同时朗读时也能知道当前实例是被选中的那一个
            .semantics(mergeDescendants = true) { selected = true }
            .padding(horizontal = metrics.cardGap * 0.7f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Oxide.Fg,
                fontSize = metrics.scaled(Oxide.Type.BodyStrong.fontSize.value).sp,
                lineHeight = metrics.scaled(Oxide.Type.BodyStrong.lineHeight.value).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(metrics.cardGap * 0.4f))
        Text(
            text = "⌄",
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 1
        )
    }
}

/** 设备型号，取不到就返回 null，让这一行整个消失 */
@Composable
private fun rememberDeviceModel(): String? = remember {
    listOf(Build.MANUFACTURER, Build.MODEL)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
        .takeIf { it.isNotEmpty() }
}

/**
 * 收集需要 IO 的几个值
 *
 * 只在当前实例变化时重算。统计过程随时可取消，因此切换实例不会留下跑飞的任务。
 */
@Composable
private fun rememberHomeSnapshot(version: Version?): OxideHomeSnapshot {
    val context = LocalContext.current.applicationContext
    val gameDir = remember(version) {
        runCatching { version?.getGameDir() }.getOrNull() ?: File(getGameHome())
    }
    val logFile = remember(version) { version?.getLatestLog() }

    return produceState(initialValue = OxideHomeSnapshot(), gameDir, logFile) {
        value = withContext(Dispatchers.IO) {
            val modsDir = VersionFolders.MOD.getDir(gameDir)
            OxideHomeSnapshot(
                installSource = readInstallSource(context.packageName, context.packageManager),
                storageUsed = safeOrNull { formatFileSize(directorySize(gameDir)) },
                storageFree = safeOrNull { formatFileSize(gameDir.usableSpace) },
                // 模组目录不存在就是 0 个，而不是"还没算出来"
                modsCount = if (modsDir.isDirectory) {
                    safeOrNull {
                        modsDir.listFiles()?.count { it.isFile && it.name.endsWith(".jar", true) }
                    }
                } else {
                    0
                },
                lastPlayedAt = logFile?.takeIf { it.exists() }?.lastModified()?.takeIf { it > 0L }
            )
        }
    }.value
}

/**
 * 失败就返回 null，但**不吞掉取消**
 *
 * 直接用 [runCatching] 会把协程取消也当成失败吃掉，于是结构化并发就失效了；
 * 这里把 [kotlinx.coroutines.CancellationException] 重新抛出去。
 */
private inline fun <T> safeOrNull(block: () -> T): T? = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Throwable) {
    null
}

/** 包管理器里记录的安装来源；侧载或读取失败时返回 null */
@Suppress("DEPRECATION")
private fun readInstallSource(pkg: String, packageManager: PackageManager): String? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(pkg).installingPackageName
        } else {
            packageManager.getInstallerPackageName(pkg)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

/** 递归统计目录占用；栈式遍历，深目录不会把调用栈压爆 */
private suspend fun directorySize(dir: File): Long {
    var total = 0L
    val pending = ArrayDeque<File>()
    pending.addLast(dir)
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val children = pending.removeLast().listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) pending.addLast(child) else total += child.length()
        }
    }
    return total
}

/** 平台自带的相对时间，顺带跟着系统语言走 */
private fun relativeTime(timeMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timeMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()