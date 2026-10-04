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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.oxide.launcher.context.getFileName
import dev.oxide.launcher.contract.extensionToMimeType
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.launch.executeJarWithUri
import dev.oxide.launcher.game.multirt.Runtime
import dev.oxide.launcher.game.multirt.RuntimesManager
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.utils.file.checkExtensionOrThrow
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.getMessageOrToString
import kotlinx.coroutines.Dispatchers
import java.io.IOException

private const val TAG = "OxideJavaRuntime"

/** 运行时压缩包的后缀：[RuntimesManager.installRuntime] 只吃 tar.xz */
internal const val OXIDE_RUNTIME_ARCHIVE_EXTENSION = "xz"

/** jar 的后缀：交给 [executeJarWithUri] 之前只用来过滤系统选择器 */
internal const val OXIDE_JAR_EXTENSION = "jar"

/**
 * "交给后端按设置挑"这一档的键
 *
 * 它不能与任何运行时的名字相同：运行时名就是文件名，而 `zxz` / `jre` 之类的
 * 名字是可能的，因此这里用一个带分隔符的保留键。
 */
internal const val OXIDE_JAR_RUNTIME_AUTO = ":auto"

/**
 * 这一档运行时能不能删
 *
 * 启动器自带的运行时（[Runtime.isProvidedByLauncher]）不能删：它不来自用户，
 * 删掉之后"选一个 Java 环境"这一整块就没有任何可选项了，而且没有任何地方
 * 能把它装回来。旧界面对同一件事也是禁用的。
 *
 * 纯函数（不碰磁盘也不碰 MMKV），可以直接单测。
 */
internal fun oxideJavaRuntimeDeletable(runtime: Runtime): Boolean =
    !runtime.isProvidedByLauncher

/**
 * "删除"这一枚是否出现
 *
 * 与 [oxideJavaRuntimeDeletable] 分开，是为了让界面能顺带说出**为什么**不给：
 * 一枚变灰的删除按钮在横屏里既占位置又读不出原因。
 */
internal fun oxideJavaRuntimeDeleteVisible(runtime: Runtime): Boolean =
    oxideJavaRuntimeDeletable(runtime)

/**
 * 打开系统选择器，挑一个 tar.xz 装成自定义运行时
 *
 * 落到 [RuntimesManager.installRuntime]：它在 IO 上解包、补 freetype 与 libawt，
 * 并把这一档登记进运行时表。整段跑在任务系统里，因此进度与取消都是真的。
 * 名字取自所选文件名（去掉 `.xz`），与旧界面的 `progressRuntimeUri` 一致。
 * 取消时把已经落盘的半成品删掉——否则取消一次就留下一个坏掉的运行时目录。
 */
@Composable
internal fun oxideJavaRuntimeImportPicker(
    onImported: () -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.firstOrNull()?.let { uri ->
            importJavaRuntime(context, uri, onImported, onError)
        }
    }
    return { picker.launch(arrayOf(OXIDE_RUNTIME_ARCHIVE_EXTENSION.extensionToMimeType())) }
}

private fun importJavaRuntime(
    context: Context,
    uri: Uri,
    onImported: () -> Unit,
    onError: (String) -> Unit,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }.onFailure { Logger.warning(TAG, "takePersistableUriPermission failed for $uri", it) }

    val fileName = context.getFileName(uri)
    if (fileName.isNullOrBlank()) {
        onError(context.getString(R.string.multirt_runtime_import_failed_file_name))
        return
    }
    TaskSystem.submitTask(
        Task.runTask(
            id = fileName,
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(androidText(fileName))
                fileName.checkExtensionOrThrow(listOf(OXIDE_RUNTIME_ARCHIVE_EXTENSION))
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Failed to read the selected file")
                stream.use {
                    RuntimesManager.installRuntime(
                        nativeLibDir = PathManager.DIR_NATIVE_LIB,
                        inputStream = it,
                        name = fileName,
                        updateProgress = { textRes, textArg ->
                            task.updateMessage(androidText(textRes, *textArg))
                        },
                    )
                }
            },
            onError = { error -> onError(error.getMessageOrToString()) },
            onFinally = onImported,
            onCancel = {
                runCatching { RuntimesManager.removeRuntime(fileName) }
                    .onFailure {
                        Logger.warning(TAG, "Failed to clean up a cancelled runtime import", it)
                    }
            },
        )
    )
}

/**
 * 挑一个 .jar 并用选定的运行时跑它
 *
 * 运行时由调用点选（[runtimeProvider] 返回 null = 交给后端按设置挑）。
 * 它必须是**一个调用**而不是一个值：选择器回调发生时，界面已经
 * 重新组合过，当时拿到的是旧闭包。这里用 [rememberUpdatedState] 读最新的那一个。
 *
 * 剩下的就是挑文件与调用 [executeJarWithUri]——后者自己会把它复制到缓存再起 JVM，
 * 失败时弹自己的错误对话框。
 */
@Composable
internal fun oxideJavaJarRunner(
    runtimeProvider: () -> Runtime?,
): (() -> Unit)? {
    val context = LocalContext.current
    val activity = context as? Activity
    val latestRuntime by rememberUpdatedState(runtimeProvider)
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
        val host = activity ?: run {
            // executeJarWithUri 需要一个 Activity 窗口来弹错误对话框
            Logger.error(TAG, "Running a jar needs an Activity window")
            return@rememberLauncherForActivityResult
        }
        executeJarWithUri(host, uri, latestRuntime()?.name)
    }
    return if (activity == null) {
        // 宿主不是 Activity 时这一枚按钮整个不给，而不是点了什么都不发生
        null
    } else {
        { picker.launch(arrayOf(OXIDE_JAR_EXTENSION.extensionToMimeType())) }
    }
}
