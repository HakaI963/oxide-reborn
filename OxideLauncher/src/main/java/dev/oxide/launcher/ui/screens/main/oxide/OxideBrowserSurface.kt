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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.oxide.launcher.R
import dev.oxide.launcher.coroutine.TaskSystem
import dev.oxide.launcher.game.account.isMicrosoftLogging
import dev.oxide.launcher.ui.theme.Oxide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.apache.commons.io.FileUtils

/** WebView 拆解前先让它停在空白页，而不是留在登录中的那一页上 */
private const val BROWSER_BLANK = "about:blank"

/** WebView 自己的数据目录；清掉它是为了下一次授权不会沿用上一次的会话 */
private const val BROWSER_DATA_DIR = "webview"

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/**
 * 内置浏览器状态
 *
 * 这一块曾经是 `NormalNavKey.WebScreen` 这条旧导航条目：整页推开，页顶是旧 Zalith
 * 的图标栏，没有可见的关闭按钮。而"浏览器现在开着吗"这件事只能靠
 * `currentKey is NormalNavKey.WebScreen` 去猜——设备码登录的轮询逻辑正是拿这个猜测
 * 来判断"用户是不是自己把网页关掉了"（`AccountUtils.microsoftLogin` 里的
 * `checkIfInWebScreen`）。那条链上任何一处对不齐，登录就会静默地被当成用户放弃。
 *
 * 现在浏览器是 Oxide 自己的一块盖板，[openUrl] 就是那一份真实状态。两条要求决定了
 * 它必须长成现在这样：
 *
 * 1. **读写都不需要组合作用域，也不需要 Context**。打开浏览器的是设备码登录那条
 *    任务，它在 `Dispatchers.IO` 上调 `toWeb`；问"开着吗"的是同一条任务的轮询循环，
 *    同样在 IO 上。因此这里用 [StateFlow] 而不是 Compose 状态：从任何线程读到的都是
 *    当时的值，不会读到一份还没提交的快照。
 * 2. **状态活得比界面久**。设备码是一次性的授权，授权页在旋转中消失就等于把登录作废，
 *    因此状态挂在进程上，而不是挂在某个界面上。
 */
class OxideBrowserState {

    private val _openUrl = MutableStateFlow<String?>(null)

    /** 浏览器当前挂着的地址；null 表示没有浏览器。实例固定，不要每次现造一个 */
    val openUrl: StateFlow<String?> = _openUrl.asStateFlow()

    private val _openedForSignIn = MutableStateFlow(false)

    /** 这一次打开是不是设备码登录拉起来的 */
    val openedForSignIn: StateFlow<Boolean> = _openedForSignIn.asStateFlow()

    /**
     * 打开浏览器
     *
     * 空地址一律当作"没有可打开的东西"：那不是一次打开，那是一次空操作。否则面板会
     * 顶着一个空白地址占住整个界面，而它下面什么都没有。
     *
     * @param forSignIn 这一次打开属于设备码登录；登录跑完之后面板会自己收起来
     */
    fun open(url: String, forSignIn: Boolean = false) {
        if (!oxideBrowserCanOpen(url)) {
            close()
            return
        }
        _openUrl.value = url
        _openedForSignIn.value = forSignIn
    }

    /** 收起浏览器。这与用户按返回键、点面板右上角的 ✕ 是同一件事 */
    fun close() {
        _openUrl.value = null
        _openedForSignIn.value = false
    }

    /**
     * 浏览器现在开着吗
     *
     * 任何线程都能问——登录轮询就是在 IO 上问的。
     */
    fun isOpen(): Boolean = oxideBrowserIsOpen(_openUrl.value)
}

/**
 * 应用级的那一份
 *
 * 打开浏览器的是设备码登录的任务回调，它手上只有一条网址，既没有 Context 也拿不到
 * 任何 ViewModel，因此这块状态只能是进程级的。真正的面板由宿主在组合里读
 * [OxideBrowserState.openUrl] 渲染——状态与界面各归各的，任务回调只管写状态。
 */
val globalOxideBrowser: OxideBrowserState = OxideBrowserState()

/**
 * 打开内置浏览器，并把"这是不是一次设备码登录"一并记下
 *
 * [isMicrosoftLogging] 在这里问得出来：`toWeb` 是设备码登录那条任务在 IO 线程上调的，
 * 那条任务此刻正在 [TaskSystem] 里。因此调用点不必自己声明这一次打开属于登录——声明了
 * 就得由调用点记得声明，而漏一处就等于登录永远等不到"用户已经走开"这个信号。
 */
fun openOxideBrowser(url: String) {
    globalOxideBrowser.open(url, forSignIn = isMicrosoftLogging())
}

/**
 * 一个地址能不能拿去打开浏览器
 *
 * 空串与 `about:blank` 都不是地址。旧界面把这一层判在渲染里，于是"开了个空的"会
 * 渲染出一整页没有地址的网页；这里把它提到打开之前，那种情况根本不会打开任何东西。
 */
internal fun oxideBrowserCanOpen(url: String?): Boolean =
    !url.isNullOrBlank() && url != BROWSER_BLANK

/**
 * 浏览器现在开着吗
 *
 * 三个调用点（账号页两处、启动前置检查一处）问的就是这句话。
 *
 * 旧答案是"栈顶那个键是不是 `NormalNavKey.WebScreen`"。它在新界面里永远为假，
 * 于是登录轮询每一次都判定用户已经走开：授权页还开着，登录却被取消。现在的答案
 * 直接来自浏览器自己的状态。
 */
internal fun oxideBrowserIsOpen(openUrl: String?): Boolean = oxideBrowserCanOpen(openUrl)

/**
 * 浏览器面板要不要自己收起来
 *
 * 旧实现在登录跑完之后调 `backToMain()`，把导航栈清回主界面，顺带把网页那一项弹掉，
 * 所以设备码授权完成时浏览器会自己消失。现在网页不再是一条栈上的条目，那一步什么也
 * 弹不掉，于是要在这里补上——否则授权成功之后，那块盖住整个界面的面板会一直留在屏幕
 * 上，而游戏已经在它背后开始启动了。
 *
 * 只在"这一次打开属于登录"时才自动收：用户自己点开某个网页时任务表是空的，
 * 自动收会把网页立刻关掉。
 */
internal fun oxideBrowserShouldAutoClose(
    isOpen: Boolean,
    openedForSignIn: Boolean,
    signInRunning: Boolean,
): Boolean = isOpen && openedForSignIn && !signInRunning

/**
 * 登录跑完就收起浏览器
 *
 * 挂在任务表上而不是自己轮询：设备码那条任务是 [TaskSystem] 里的一个任务，它结束时
 * 任务表会发一次新的列表（见 `TaskSystem.removeTask`），因此这里是等一次事件。
 *
 * [state] 是应用级那一份而不是新建的：面板与这个等待必须看同一份状态，否则登录
 * 结束时收掉的是另一个对象。
 */
@Composable
internal fun OxideBrowserClosesWhenSignInEnds(
    openUrl: String?,
    openedForSignIn: Boolean,
    state: OxideBrowserState,
) {
    LaunchedEffect(openUrl, openedForSignIn) {
        if (!openedForSignIn) return@LaunchedEffect
        // 任务从任务表里消失的那一刻，就是"这一次登录结束了"这个事实本身：
        // 这里等的是那一次事件，而不是自己一遍遍去问
        TaskSystem.tasksFlow.first { !isMicrosoftLogging() }
        if (oxideBrowserShouldAutoClose(
                isOpen = state.isOpen(),
                openedForSignIn = openedForSignIn,
                signInRunning = isMicrosoftLogging(),
            )
        ) {
            state.close()
        }
    }
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

/**
 * 内置浏览器面板
 *
 * 设备码授权真的需要一个 WebView，这一点没有任何界面手段可以替代；能换的只是它放在
 * 哪里。旧实现把它做成一整页导航条目（`NormalNavKey.WebScreen`），于是页顶是旧
 * Zalith 的图标栏、底下那页照原样透上来、没有可见的关闭按钮、失败时只有一屏白、
 * 也没有重试。
 *
 * 现在它是 Oxide 自家的一块盖板（[OxideSubWindow]）：不透明底幕、居中的紧凑面板、
 * 右上角固定 ✕、硬件返回键先退网页自己的历史再退面板。三段结构：
 *
 * - **标题行**：标题、"重载"，以及加载中的状态；
 * - **地址行**：当前地址（跟随重定向更新），点它把这一页交给系统浏览器；
 * - **主体**：WebView，上面盖着加载中与出错两种状态。
 *
 * WebView **始终留在组合里**，出错时只是被一层不透明的 [Oxide.BgElevated] 盖住，
 * 因此重试是对同一个 WebView 调 `reload()`：不必重新造一个，也就不会漏掉上一个。
 *
 * 拆解（停加载、退回空白页、清历史、销毁）与"这一次登录留下的 Cookie 与缓存"都在
 * [DisposableEffect] 的 `onDispose` 里做——组合阶段与测量阶段不碰磁盘，也不碰网络。
 */
@Composable
fun OxideBrowserPanel(
    url: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 把当前地址交给系统浏览器 */
    openLink: (String) -> Unit = {},
    /** 这一次打开属于设备码登录，因此面板顶上写一句该做什么 */
    forSignIn: Boolean = false,
) {
    val context = LocalContext.current
    val unknownFailure = stringResource(R.string.oxide_cap_browser_failed_unknown)

    // 会跟着重定向走的地址与"这一次打开的地址"是两样东西：
    // 重组不得把用户已经走到哪一步的页面重置掉
    var pageUrl by remember(url) { mutableStateOf(url) }
    var loading by remember(url) { mutableStateOf(true) }
    var loadedOnce by remember(url) { mutableStateOf(false) }
    var failure by remember(url) { mutableStateOf<String?>(null) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    val holder = remember(url) { mutableStateOf<WebView?>(null) }

    /** 重载当前这一页：一次新的导航，因此先清掉上一次失败 */
    val reload: () -> Unit = {
        failure = null
        loading = true
        holder.value?.reload()
    }

    // 返回键：页面自己还有历史就退回去，没有就收起面板。
    // 后注册的那一路优先，因此"有历史"时赢的是 goBack
    BackHandler(enabled = !canGoBack) { onDismiss() }
    BackHandler(enabled = canGoBack) { holder.value?.goBack() }

    DisposableEffect(url) {
        onDispose {
            holder.value?.apply {
                stopLoading()
                loadUrl(BROWSER_BLANK)
                clearHistory()
                removeAllViews()
                destroy()
            }
            holder.value = null
            // 设备码是一次性授权：留着上一次会话的 Cookie 与缓存，下一次授权会静默地
            // 沿用上一个身份，用户看到的却是"我明明重新登录了"
            CookieManager.getInstance().removeAllCookies(null)
            FileUtils.deleteQuietly(context.getDir(BROWSER_DATA_DIR, 0))
        }
    }

    OxideSubWindow(
        title = stringResource(R.string.oxide_cap_browser_title),
        onClose = onDismiss,
        modifier = modifier,
        // 面板是盖在整块界面之上的一层，它下面没有"上一层"：✕ 与硬件返回键收的是
        // 同一件事，因此不再画一个含义重复的 ←
        onBack = null,
        scrollable = false,
        trailing = {
            if (loading) {
                OxideBadge(
                    text = stringResource(R.string.oxide_common_loading),
                    tone = OxideBadgeTone.Warn,
                )
            } else {
                OxideIconButton(
                    onClick = reload,
                    glyph = "↻",
                    modifier = Modifier.oxideIconDescription(
                        stringResource(R.string.oxide_cap_browser_reload)
                    ),
                )
            }
        },
    ) {
        if (forSignIn) {
            // 设备码已经被复制到剪贴板了，因此把该做什么说清楚，而不是让用户对着
            // 一个空白网页猜
            Text(
                text = stringResource(R.string.oxide_cap_browser_sign_in_hint),
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pageUrl,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            OxideIconButton(
                onClick = { openLink(pageUrl) },
                glyph = "↗",
                enabled = pageUrl.isNotBlank(),
                modifier = Modifier.oxideIconDescription(
                    stringResource(R.string.oxide_cap_browser_open_externally)
                ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            OxideBrowserWebView(
                context = context,
                initialUrl = url,
                unknownFailure = unknownFailure,
                onHolder = { holder.value = it },
                onPageUrl = { pageUrl = it },
                onLoading = {
                    loading = it
                    // 一次新导航开始，上一次失败就此作废：这一次可能是好的
                    if (it) failure = null
                },
                onFirstContent = { loadedOnce = true },
                onCanGoBack = { canGoBack = it },
                onFailure = { failure = it },
            )

            val problem = failure
            if (problem != null) {
                // 出错时用一层不透明的面盖住 WebView，而不是把它换掉
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Oxide.BgElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    OxideEmptyState(
                        title = stringResource(R.string.oxide_cap_browser_failed),
                        detail = problem,
                        action = {
                            OxideButton(
                                text = stringResource(R.string.oxide_cap_retry),
                                onClick = reload,
                                tone = OxideButtonTone.Primary,
                            )
                        },
                    )
                }
            } else if (loading && !loadedOnce) {
                // 第一次还没画出任何东西：给一行字，而不是一个空白框
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Oxide.BgElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    OxideLoadingRow(text = stringResource(R.string.oxide_common_loading))
                }
            }
        }
    }
}

/**
 * WebView 本体
 *
 * 只负责把 WebView 的回调翻译成界面状态，不决定任何布局。
 *
 * 设置与旧实现逐字一致（JavaScript 必须开、缓存不走）：能让设备码授权跑通的那份
 * 配置就是这两行，顺手加别的东西等于换一套没被验证过的登录流程。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OxideBrowserWebView(
    context: Context,
    initialUrl: String,
    /** WebView 没给出失败说明时用这一句，而不是把空 detail 摆在那里 */
    unknownFailure: String,
    onHolder: (WebView?) -> Unit,
    onPageUrl: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onFirstContent: () -> Unit,
    onCanGoBack: (Boolean) -> Unit,
    onFailure: (String?) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let(onPageUrl)
                        onLoading(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let(onPageUrl)
                        onLoading(false)
                        onFirstContent()
                        onCanGoBack(view?.canGoBack() == true)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        // 子资源（一张图、一个脚本）失败与"这一页打不开"是两回事，
                        // 只有主文档失败才是面板该说出来的事
                        if (request?.isForMainFrame != true) return
                        onLoading(false)
                        onFailure(error?.description?.toString() ?: unknownFailure)
                    }
                }
                settings.javaScriptEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                loadUrl(initialUrl)
            }.also(onHolder)
        },
        // 不在这里重复 loadUrl：地址跟着重定向变了也不该由一次重组驱动一次导航
        update = { view -> onHolder(view) },
    )
}