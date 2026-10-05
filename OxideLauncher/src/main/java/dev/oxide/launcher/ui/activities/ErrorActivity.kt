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

package dev.oxide.launcher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jakewharton.processphoenix.ProcessPhoenix
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.R
import dev.oxide.launcher.context.COPY_LABEL_LINK
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.ui.base.BaseAppCompatActivity
import dev.oxide.launcher.ui.screens.main.ErrorScreen
import dev.oxide.launcher.ui.screens.main.crashlogs.ShareLinkOperation
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.OxideTheme
import dev.oxide.launcher.utils.copyText
import dev.oxide.launcher.utils.file.shareFile
import dev.oxide.launcher.utils.getParcelableSafely
import dev.oxide.launcher.utils.getSerializableSafely
import dev.oxide.launcher.utils.network.openLink
import dev.oxide.launcher.utils.string.throwableToString
import dev.oxide.launcher.viewmodel.LogsUploadViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import java.io.File

private const val BUNDLE_EXIT_TYPE = "BUNDLE_EXIT_TYPE"
private const val BUNDLE_THROWABLE = "BUNDLE_THROWABLE"
private const val BUNDLE_JVM_CRASH = "BUNDLE_JVM_CRASH"
private const val BUNDLE_CAN_RESTART = "BUNDLE_CAN_RESTART"
private const val EXIT_JVM = "EXIT_JVM"
private const val EXIT_LAUNCHER = "EXIT_LAUNCHER"

fun showExitMessage(
    context: Context,
    code: Int,
    isSignal: Boolean,
    logPath: String
) {
    val intent = Intent(context, ErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_EXIT_TYPE, EXIT_JVM)
        putExtra(BUNDLE_JVM_CRASH, JvmCrash(code, isSignal, logPath))
    }
    context.startActivity(intent)
}

@Parcelize
private data class JvmCrash(
    val code: Int,
    val isSignal: Boolean,
    val logPath: String
): Parcelable

@AndroidEntryPoint
class ErrorActivity : BaseAppCompatActivity() {

    /**
     * 游戏崩溃日志上传逻辑管理 ViewModel
     */
    private val viewModel: LogsUploadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras ?: return runFinish()
        extras.classLoader = javaClass.classLoader

        val exitType = extras.getString(BUNDLE_EXIT_TYPE, EXIT_LAUNCHER)

        val errorMessage = when (exitType) {
            EXIT_JVM -> {
                val jvmCrash = extras.getParcelableSafely(BUNDLE_JVM_CRASH, JvmCrash::class.java) ?: return runFinish()
                val messageResId = if (jvmCrash.isSignal) R.string.crash_singnal_message else R.string.crash_exit_message
                val message = getString(messageResId, jvmCrash.code)
                val messageBody = getString(R.string.crash_exit_note)
                ErrorMessage(
                    message = message,
                    messageBody = messageBody,
                    crashType = CrashType.GAME_CRASH,
                    logFile = File(jvmCrash.logPath).also { file ->
                        //检查日志文件是否适合上传
                        viewModel.check(file)
                    },
                    // JVM 带走的进程没有可序列化的异常
                    throwable = null,
                )
            }
            else -> {
                val throwable = extras.getSerializableSafely(BUNDLE_THROWABLE, Throwable::class.java) ?: return runFinish()
                ErrorMessage(
                    message = getString(R.string.crash_launcher_message),
                    // 完整堆栈逐字符保留：页面那边一行都不会截断
                    messageBody = throwableToString(throwable),
                    crashType = CrashType.LAUNCHER_CRASH,
                    logFile = PathManager.FILE_CRASH_REPORT,
                    throwable = throwable,
                )
            }
        }

        val logFile = errorMessage.logFile
        val canRestart: Boolean = extras.getBoolean(BUNDLE_CAN_RESTART, true)
        val logExists = logFile.exists() && logFile.isFile

        setContent {
            OxideTheme {
                ShareLinkOperation(
                    operation = viewModel.operation,
                    onChange = { viewModel.operation = it },
                    onUploadChancel = { viewModel.cancel() },
                    onUpload = {
                        viewModel.upload(logFile) { link ->
                            openLink(link)
                            copyText(COPY_LABEL_LINK, link, this@ErrorActivity)
                        }
                    }
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Oxide.Bg,
                    contentColor = Oxide.Fg
                ) {
                    ErrorScreen(
                        crashType = errorMessage.crashType,
                        // 标题与类型标签都从资源表里原样取出来再传下去，因此这一页
                        // 只负责排版，不再自己拼文案
                        crashTitle = if (errorMessage.crashType == CrashType.LAUNCHER_CRASH) {
                            getString(R.string.crash_launcher_title, BuildKeys.LAUNCHER_NAME)
                        } else {
                            BuildKeys.LAUNCHER_NAME
                        },
                        crashTypeLabel = getString(
                            R.string.crash_type,
                            getString(errorMessage.crashType.textRes),
                        ),
                        crashSummary = errorMessage.message,
                        // 完整堆栈逐字符传下去，中间不做任何截断
                        crashTrace = errorMessage.messageBody,
                        crashThrowable = errorMessage.throwable,
                        shareLogs = logExists,
                        canUpload = viewModel.canUpload,
                        canRestart = canRestart,
                        onShareLogsClick = {
                            if (logExists) {
                                shareFile(this@ErrorActivity, logFile)
                            }
                        },
                        onUploadClick = {
                            viewModel.operation = ShareLinkOperation.Tip
                        },
                        onRestartClick = {
                            ProcessPhoenix.triggerRebirth(this@ErrorActivity)
                        },
                        onExitClick = { finish() },
                        onOrientationChanged = {
                            this@ErrorActivity.requestedOrientation = it
                        },
                    )
                }
            }
        }
    }

    private data class ErrorMessage(
        val message: String,
        val messageBody: String,
        val crashType: CrashType,
        val logFile: File,
        /**
         * 启动器崩溃时那一个原始异常
         *
         * 崩溃页要单独挑出**根因**（异常链最深处的那个）来显示，因此需要原始的
         * 异常对象，而不只是已经打印好的那一大段字符串。游戏崩溃是 JVM 进程被带
         * 走的，没有可序列化的异常，这里是 null。
         */
        val throwable: Throwable?
    )
}

/**
 * 崩溃类型
 */
enum class CrashType(val textRes: Int) {
    /**
     * 启动器崩溃
     */
    LAUNCHER_CRASH(R.string.crash_type_launcher),

    /**
     * 游戏运行崩溃
     */
    GAME_CRASH(R.string.crash_type_game)
}

/**
 * 启动软件崩溃信息页面
 */
fun showLauncherCrash(context: Context, throwable: Throwable, canRestart: Boolean = true) {
    val intent = Intent(context, ErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_EXIT_TYPE, EXIT_LAUNCHER)
        putExtra(BUNDLE_THROWABLE, throwable)
        putExtra(BUNDLE_CAN_RESTART, canRestart)
    }
    context.startActivity(intent)
}