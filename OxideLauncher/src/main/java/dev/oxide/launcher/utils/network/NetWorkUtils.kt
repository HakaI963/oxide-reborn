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

package dev.oxide.launcher.utils.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import dev.oxide.launcher.R
import dev.oxide.launcher.context.COPY_LABEL_LINK
import dev.oxide.launcher.game.download.engine.DownloadEngine
import dev.oxide.launcher.game.download.engine.DownloadRequest
import dev.oxide.launcher.path.DOWNLOAD_OKHTTP_CLIENT
import dev.oxide.launcher.path.TIME_OUT
import dev.oxide.launcher.path.createRequestBuilder
import dev.oxide.launcher.ui.screens.main.oxide.showOxideLinkDialog
import dev.oxide.launcher.utils.copyText
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.utils.string.isEmptyOrBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "NetWorkUtils"

/** 单个文件下载的最大允许时间 */
private const val DOWNLOAD_PER_FILE_TIMEOUT = 3 * 60 * 1000L

/** 多候选源下载的最大允许时间 */
private const val DOWNLOAD_SOURCES_TIMEOUT = 5 * 60 * 1000L

/**
 * @return 当前网络是否可用
 */
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

/**
 * @return 当前是否正在使用移动网络
 */
fun isUsingMobileData(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

/**
 * 下载单个文件到本地（动态分块、自动换源、降级重试）
 * @throws TimeoutException 整体超时
 */
suspend fun downloadFile(
    url: String,
    outputFile: File,
    sha1: String? = null,
    sizeCallback: (Long) -> Unit = {}
): Unit = withTimeout(DOWNLOAD_PER_FILE_TIMEOUT.milliseconds) {
    DownloadEngine.download(DownloadRequest(listOf(url), outputFile, sha1), sizeCallback = sizeCallback)
}

/**
 * 按优先级从多个候选源下载单个文件，失败时自动沿源列表轮转
 * @throws TimeoutException 整体超时
 */
suspend fun downloadFileFromSources(
    urls: List<String>,
    outputFile: File,
    sha1: String? = null,
    sizeCallback: (Long) -> Unit = {}
): Unit = withTimeout(DOWNLOAD_SOURCES_TIMEOUT.milliseconds) {
    DownloadEngine.download(DownloadRequest(urls, outputFile, sha1), sizeCallback = sizeCallback)
}

/**
 * 速率监测报告
 * @param onSpeedReport 在1秒延迟后汇报期间的数据量，单位：bytes
 */
suspend fun <T> withSpeedReport(
    onSpeedReport: (Long) -> Unit,
    onClear: () -> Unit = {},
    block: suspend (onBytesWritten: (Long) -> Unit) -> T
): T = coroutineScope {
    val bytesWritten = AtomicLong(0L)

    withSpeedReport(
        onTimeReport = {
            val currentBytes = bytesWritten.getAndSet(0L)
            onSpeedReport(currentBytes)
        },
        onClear = {
            bytesWritten.set(0L)
            onClear()
        },
        block = {
            block { bytes ->
                bytesWritten.addAndGet(bytes)
            }
        }
    )
}

/**
 * 速率监测报告
 * @param onTimeReport 在1秒延迟后调用，可在此期间汇报
 */
suspend fun <T> withSpeedReport(
    onTimeReport: () -> Unit,
    onClear: () -> Unit,
    block: suspend () -> T
): T = coroutineScope {
    var reportJob: Job? = null

    try {
        onClear()
        reportJob = launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L.milliseconds)
                onTimeReport()
            }
        }

        block()
    } finally {
        reportJob?.cancelAndJoin()
        onClear()
    }
}

/**
 * 同步获取 URL 返回的字符串内容
 * @param url 要请求的URL地址
 * @return 服务器返回的字符串内容
 * @throws IllegalArgumentException 当URL无效时
 * @throws IOException 当网络请求失败或响应解析失败时
 */
@Throws(IOException::class, IllegalArgumentException::class)
suspend fun fetchStringFromUrl(url: String): String = withContext(Dispatchers.IO) {
    try {
        withTimeout(TIME_OUT.milliseconds) {
            runInterruptible {
                DOWNLOAD_OKHTTP_CLIENT.newCall(createRequestBuilder(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} - ${response.message}")
                    }

                    response.body.use { it.string() }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        throw TimeoutException("Request timed out after ${TIME_OUT}ms: $url")
    }
}

/**
 * 同步获取 URL 返回的字符串内容
 * @param urls 要请求的URL源地址
 * @return 服务器返回的字符串内容
 * @throws IllegalArgumentException 当URL无效时
 * @throws IOException 当网络请求失败或响应解析失败时
 */
@Throws(IOException::class, IllegalArgumentException::class)
suspend fun fetchStringFromUrls(urls: List<String>): String = withContext(Dispatchers.IO) {
    var result: String? = null
    var succeed = false
    var lastException: Throwable? = null

    loop@ for (url in urls) {
        runCatching {
            result = fetchStringFromUrl(url)
            succeed = true
            break@loop
        }.onFailure { th ->
            if (th is CancellationException || th.isInterruptedIOException()) throw th
            Logger.debug(TAG, "Source $url failed!", th)
            lastException = th
        }
    }

    if (!succeed || result == null) throw lastException ?: IOException("Failed to retrieve information from the source!")

    result
}

/**
 * 展示一个提示弹窗，告知用户接下来将要在浏览器内访问的链接，用户可以选择不进行访问
 * @param link 要访问的链接
 */
fun Activity.openLink(link: String) {
    this.openLink(link, null)
}

/**
 * 展示一个提示弹窗，告知用户接下来将要在浏览器内访问的链接，用户可以选择不进行访问
 *
 * 此前这里是一个 Material 的 `MaterialAlertDialogBuilder`：灰卡片 + 鲑鱼色文字按钮，
 * 是 Oxide 界面上唯一还在用的那套 Material 外观，而且它从设置页的"下载"一路可达
 * （`OxideNativeInvoker.openLink`）。现在走 [showOxideLinkDialog]，面板、正文与
 * 三枚按钮都由 Oxide 那套对话框拼出来。
 *
 * 三行文案、剪贴板标签与打开行为都没有变，公开签名也保持原样——依赖它的调用点很多。
 *
 * **不要再在这里引回 `MaterialAlertDialogBuilder`。** 它的 import 已被移除，因此
 * 编译器会直接挡住回退；`OxideLegacyDialogGuardTest` 另外从源码上钉住这一点，
 * 因为这个文件的调用链要穿过 JNI 桥接，编译器管不到调用方那一侧。
 *
 * @param link 要访问的链接
 * @param dataType 设置 intent 的数据以及显式 MIME 数据类型
 */
fun Activity.openLink(link: String, dataType: String?) {
    if (link.isEmptyOrBlank()) {
        return
    }

    showOxideLinkDialog(
        context = this,
        title = getString(R.string.generic_open_link),
        link = link,
        confirmText = getString(R.string.generic_confirm),
        cancelText = getString(R.string.generic_cancel),
        copyText = getString(R.string.generic_copy),
        onOpen = { opened ->
            openLinkInternal(opened, dataType)
        },
        onCopy = { copied ->
            copyText(COPY_LABEL_LINK, copied, this)
        },
    )
}

/**
 * 直接在浏览器打开指定链接
 */
fun Activity.openLinkInternal(link: String, dataType: String? = null) {
    try {
        val uri = link.toUri()
        val browserIntent = if (dataType != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, dataType)
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        startActivity(browserIntent)
    } catch (e: Exception) {
        Logger.warning(TAG, "Failed to open link: $link", e)
    }
}

/**
 * 检查是不是单纯的中断异常，而不是网络超时导致的中断
 */
fun Throwable.isInterruptedIOException(): Boolean {
    return this is InterruptedIOException && this !is SocketTimeoutException
}