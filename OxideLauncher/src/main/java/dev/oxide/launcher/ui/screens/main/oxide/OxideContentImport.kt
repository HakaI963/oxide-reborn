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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.game.version.saves.unpackSaveZip
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.content.elements.rememberMultipleUriImportTaskBuilder
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.logging.Logger
import java.io.File

private const val TAG = "OxideContentImport"

/**
 * 某一类内容接受从设备导入的后缀
 *
 * - 模组：`jar`，与旧界面的 `ImportMultipleFileButton(extension = "jar")` 一致
 * - 资源包与光影：`zip`
 * - 存档：用户手上通常是一个存档压缩包，导入之后再解包成一个存档文件夹
 * - 截图：**不支持**。截图是游戏自己写出来的产物，没有"从设备拿一个进来"这件事；
 *   给它一枚导入按钮只能让用户往这个实例里塞一堆和它无关的图
 *
 * 纯函数，可直接单测。
 */
internal fun oxideContentImportExtension(category: OxideContentCategory): String? =
    when (category) {
        OxideContentCategory.Mods -> "jar"
        OxideContentCategory.ResourcePacks -> "zip"
        OxideContentCategory.Shaders -> "zip"
        OxideContentCategory.Saves -> "zip"
        OxideContentCategory.Screenshots -> null
    }

/**
 * 导入之后要不要再解包
 *
 * 只有存档需要：模组、资源包、光影拷进去就是最终形态，而存档必须把压缩包
 * 展开成 `saves/<世界名>/`，否则列表里只会多出一个读不出 `level.dat` 的文件夹。
 */
internal fun oxideContentImportUnpacks(category: OxideContentCategory): Boolean =
    category == OxideContentCategory.Saves

/**
 * 从设备导入这一类内容：返回"打开系统选择器"那枚按钮的动作
 *
 * 这一块不支持导入时（截图）返回 null，因此调用点连按钮都不用画。
 *
 * 复制走既有的 [rememberMultipleUriImportTaskBuilder]：它在 IO 上跑、汇报真实进度、
 * 把扩展名对不上的文件逐个报错、并把控制权交回任务系统——因此这里不另写一份
 * 复制逻辑。选择器用 `OpenMultipleDocuments` 并在回调里取长期读权限：
 * 一次性读权限在任务还没跑完时会被系统收回，复制到一半就会失败。
 *
 * @param onImported 复制完成之后由调用方重扫这一类目录
 */
@Composable
internal fun oxideContentImportAction(
    version: Version,
    category: OxideContentCategory,
    onImported: () -> Unit,
): (() -> Unit)? {
    val extension = oxideContentImportExtension(category) ?: return null
    val context = LocalContext.current
    val folderDir: File = category.folder.getDir(version.getGameDir())

    // 闭包里要用的文案在组合期读一次：stringResource 是 @Composable，不能在回调里调
    val errorTitle = stringResource(R.string.generic_error)
    val errorMessage = stringResource(R.string.oxide_cap_mgr_import_failed)

    val taskBuilder = rememberMultipleUriImportTaskBuilder(
        id = "OxideContent.$category.Import",
        targetDir = folderDir,
        errorTitle = errorTitle,
        errorMessage = errorMessage,
        checkExtension = listOf(extension),
        onFileCopied = { task, file ->
            if (oxideContentImportUnpacks(category)) {
                task.updateProgress(-1f)
                task.updateMessage(androidText(R.string.saves_manage_import_unpacking, file.name))
                unpackSaveZip(file, folderDir)
            }
        },
        onImported = onImported,
    )

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        uris.forEach { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                Logger.warning(TAG, "takePersistableUriPermission failed for $uri", it)
            }
        }
        TaskSystem.submitTask(taskBuilder(uris))
    }

    return { picker.launch(arrayOf(extension)) }
}

/**
 * 一行"从设备导入"
 *
 * 后缀以文字写在旁边：系统选择器只会按它过滤，用户得看得见自己在选什么。
 */
@Composable
internal fun OxideContentImportRow(
    metrics: OxideMetrics,
    category: OxideContentCategory,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extension = oxideContentImportExtension(category) ?: return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.secRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OxideButton(
            text = stringResource(R.string.oxide_cap_mgr_import),
            onClick = onPick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(metrics.secRowGap))
        Text(
            text = stringResource(R.string.oxide_cap_mgr_import_detail, extension.uppercase()),
            color = Oxide.FgFaint,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
