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

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.launcher.R
import dev.oxide.launcher.game.download.modpack.install.UnsupportedPackReason
import dev.oxide.launcher.ui.resolveAndroidString
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import dev.oxide.launcher.viewmodel.ConfirmMobileDataOperation
import dev.oxide.launcher.viewmodel.ModpackImportOperation
import dev.oxide.launcher.viewmodel.ModpackImportViewModel
import dev.oxide.launcher.viewmodel.VersionNameOperation

private const val TAG = "OxideModpackImport"

/**
 * 整合包导入接受的后缀
 *
 * 系统选择器按 mime 过滤，而不同提供方对 `.mrpack` 的映射并不一致，
 * 因此这里给 `*/*`：漏掉一个真实存在的整合包比多显示几个无关文件糟糕得多。
 * 真正能不能导入由 [dev.oxide.launcher.game.download.modpack.install.ModpackImporter] 判定，
 * 它报得出**为什么**不支持；界面上猜出来的拒绝只会让用户不知道该换什么文件。
 */
private val OXIDE_MODPACK_MIME_TYPES: Array<String> = arrayOf("*/*")


/** 导入器报出的"为什么不支持"，逐条以文字给出，而不是只丢一个标题 */
internal fun unsupportedLines(reason: UnsupportedPackReason): List<String> =
    listOf(reason.reasonText)

/**
 * 从这台设备导入一个整合包
 *
 * 后端完全是既有的 `ModpackImportViewModel` → `ModpackImporter` 这一条链路：
 * 实例名、移动网络确认、任务流水线、进度与取消全部由它负责。这一段只负责
 * 挑文件，以及把它的五种状态画成 Oxide 的对话框与抽屉。
 *
 * 导入完成之后 `ModpackImporter` 自己会 `VersionsManager.refresh`，因此这一段
 * 不再重复刷新——刷新两次会让实例页先闪一次再出现新实例。
 *
 * @param onFinished 导入成功：把结果告诉调用点（例如提示去看实例页）
 */
@Composable
internal fun OxideModpackImport(
    metrics: OxideMetrics,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importViewModel: ModpackImportViewModel = viewModel(key = "OxideModpackImport") {
        ModpackImportViewModel()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Logger.warning(TAG, "takePersistableUriPermission failed for $uri", it) }
        importViewModel.import(context = context, uri = uri)
    }

    val operation = importViewModel.importOperation
    val importer = importViewModel.importer

    // 真的在跑的那几条任务标题，而不是编出来的百分比
    val tasks = importer?.taskFlow?.collectAsStateWithLifecycle()?.value.orEmpty()
    val logLines = importer?.logOutput
        ?.collectAsStateWithLifecycle()
        ?.value
        ?.lines
        ?.collectAsStateWithLifecycle()
        ?.value
        .orEmpty()

    OxideActionRow(
        label = stringResource(R.string.oxide_cap_dis_import_modpack),
        hint = stringResource(R.string.oxide_cap_dis_import_modpack_detail),
        enabled = operation == ModpackImportOperation.None,
        onClick = { picker.launch(OXIDE_MODPACK_MIME_TYPES) },
    )

    // 任务阶段：与 Discover 里"从平台安装整合包"共用同一块抽屉形状
    if (operation is ModpackImportOperation.Import && tasks.isNotEmpty()) {
        OxideDrawerHost(
            visible = true,
            metrics = metrics,
            onDismiss = { importViewModel.cancel() },
            title = stringResource(R.string.import_modpack),
        ) {
            OxideSectionLabel(text = stringResource(R.string.oxide_dis_install_drawer_tasks))
            Spacer(Modifier.height(metrics.cardGap))
            tasks.forEach { task ->
                OxideSettingRow(
                    label = resolveAndroidString(task.title).text,
                    enabled = false,
                )
            }
            Spacer(Modifier.height(metrics.cardGap))
            // 最后几行 JVM 日志：导入失败时这是唯一能看出"卡在哪"的东西
            if (logLines.isNotEmpty()) {
                OxideSectionLabel(text = stringResource(R.string.oxide_cap_dis_import_modpack_log))
                logLines.takeLast(MAX_LOG_LINES).forEach { line ->
                    OxideSettingRow(label = line, enabled = false)
                }
                Spacer(Modifier.height(metrics.cardGap))
            }
            OxideButton(
                text = stringResource(R.string.generic_cancel),
                onClick = { importViewModel.cancel() },
                tone = OxideButtonTone.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // 导入器要一个实例名：起名由界面问，答案交回导入器的挂起点
    when (val naming = importViewModel.versionNameOperation) {
        VersionNameOperation.None -> Unit
        is VersionNameOperation.Waiting -> {
            var draft by remember(naming.name) { mutableStateOf(naming.name) }
            OxideTextEntryDialog(
                title = stringResource(R.string.import_modpack),
                label = stringResource(R.string.oxide_cap_dis_import_modpack_instance),
                value = draft,
                onValueChange = { draft = it },
                confirmText = stringResource(R.string.generic_confirm),
                cancelText = stringResource(R.string.generic_cancel),
                onConfirm = { importViewModel.confirmVersionName(draft) },
                onDismiss = { importViewModel.cancel() },
                metrics = metrics,
            )
        }
    }

    when (importViewModel.confirmMobileDataOperation) {
        ConfirmMobileDataOperation.None -> Unit
        ConfirmMobileDataOperation.Waiting -> OxideConfirmDialog(
            title = stringResource(R.string.generic_warning),
            message = stringResource(R.string.oxide_cap_dis_import_modpack_mobile),
            confirmText = stringResource(R.string.generic_anyway),
            cancelText = stringResource(R.string.generic_cancel),
            onConfirm = { importViewModel.confirmUseMobileData(true) },
            onDismiss = { importViewModel.confirmUseMobileData(false) },
            metrics = metrics,
        )
    }

    when (val current = operation) {
        ModpackImportOperation.None -> Unit

        ModpackImportOperation.Import -> Unit // 抽屉已经在了

        is ModpackImportOperation.NotSupport -> OxideConfirmDialog(
            title = stringResource(R.string.import_modpack_not_supported_title),
            message = stringResource(R.string.import_modpack_not_supported_formats) +
                "\n\n" + unsupportedLines(current.reason).joinToString("\n"),
            confirmText = stringResource(R.string.generic_close),
            onConfirm = { importViewModel.cancel() },
            onDismiss = { importViewModel.cancel() },
            metrics = metrics,
        )

        // 导入完成不能在组合期间直接回调：这里只记下一个应用事件，
        // 实际回调放在 LaunchedEffect 里，否则每一帧都会跳一次
        ModpackImportOperation.Finished -> {
            LaunchedEffect(Unit) {
                onFinished()
                importViewModel.cancel()
            }
        }

        is ModpackImportOperation.Error -> OxideConfirmDialog(
            title = stringResource(R.string.import_modpack_failed_title),
            message = stringResource(R.string.import_modpack_failed_text) +
                "\n\n" + current.th.getMessageOrToString(),
            confirmText = stringResource(R.string.generic_close),
            onConfirm = { importViewModel.cancel() },
            onDismiss = { importViewModel.cancel() },
            metrics = metrics,
        )
    }
}

/** 抽屉里最多显示多少行日志：再多就把面板撑高，360dp 高的屏幕上会挤掉取消按钮 */
private const val MAX_LOG_LINES = 40
