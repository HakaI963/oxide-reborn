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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.oxide.launcher.R
import dev.oxide.launcher.context.readRawContent
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.ui.code_editor.EditorState
import dev.oxide.launcher.ui.code_editor.SoraEditor
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEADark
import dev.oxide.launcher.ui.code_editor.scheme.SchemeIDEALight
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.logging.Logger
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 协议全文读取的日志标签 */
private const val LICENCE_TAG = "OxideLicencePanel"

// ---------------------------------------------------------------------------
// 纯逻辑
// ---------------------------------------------------------------------------

/** 协议全文读不出来时的两种原因，必须能分辨 */
internal enum class OxideLicenceFailure {
    /** 那份文本读不出来 */
    Unreadable,

    /** 读出来了，但里面没有文字 */
    Empty,
}

/** 协议全文面板正在显示的东西 */
internal sealed interface OxideLicenceLoad {

    /** 正在读 */
    data object Loading : OxideLicenceLoad

    /** 读到了，[text] 是全文 */
    data class Ready(val text: String) : OxideLicenceLoad

    /** 读不出来，[reason] 是原因 */
    data class Failed(val reason: OxideLicenceFailure) : OxideLicenceLoad
}

/**
 * 一次读取的结果该显示成什么
 *
 * 三种结果互不相干，而且"读不出来"与"读出来是空的"必须能分辨：前者是这台设备上出了
 * 差错（重试有意义），后者是那份文件本身没有内容（重试没有意义，重试多少次都还是空的）。
 * 旧界面把这两者都变成"一屏编辑器里的错误文字"，用户看不出该做什么。
 *
 * 纯函数：[read] 是已经读完之后的结果，因此不需要 Context，也不需要真的文件。
 */
internal fun oxideLicenceLoadOf(read: Result<String>): OxideLicenceLoad = when {
    read.isFailure -> OxideLicenceLoad.Failed(OxideLicenceFailure.Unreadable)
    read.getOrNull()?.isBlank() != false -> OxideLicenceLoad.Failed(OxideLicenceFailure.Empty)
    else -> OxideLicenceLoad.Ready(read.getOrThrow())
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

/**
 * 协议全文面板
 *
 * 取代 `LicenseScreen`（以及它背后的 `NormalNavKey.License` 路由）：旧界面是一整页旧
 * Zalith 界面，正文交给 `SoraEditor` 的默认配色与默认字号，与新界面的近黑语言不是同
 * 一种东西，而且没有可见的关闭按钮。
 *
 * 关于"关于面板是不是已经能读协议了"这个问题，答案是**不能**，而且这一点是可查的：
 * `OxideAboutPanel` 只**列**协议——`oxideAboutLicenceEntries` 从致谢名单里派生出若干行，
 * 每行一枚"读协议"按钮，点下去把一个 `R.raw` 的 id 交出来。它自己不渲染任何协议正文，
 * `res/raw` 里那十九份文本没有一份被它显示过。新界面里唯一能把 `R.raw` 读出来并渲染的
 * 东西就是这一块面板，因此把它删掉等于让 GPLv3 §4/§5 要求随附的那份协议正文在应用里
 * 彻底无法阅读——那是许可义务，不是界面偏好。
 *
 * 版面沿用既有的零件而不是另起一套：
 *
 * - 面板本体走 [OxideSubWindow]：不透明底幕（[Oxide.PanelBackdrop]）与不透明面板
 *   （[Oxide.BgElevated]）、居中、宽高上限由真实窗口尺寸算出来，因此 640×360 上也只
 *   是需要滚动，不会裁切；
 * - 正文仍然交给 `SoraEditor`，与旧界面、与日志页是同一个渲染器，`isReadOnly`；
 * - 加载中、读不出来、以及重试这三样是旧界面没有的。
 *
 * 读取在 IO 上跑，组合阶段与测量阶段不碰磁盘。
 */
@Composable
fun OxideLicencePanel(
    raw: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isLauncherInDarkTheme()
    val scheme = remember(isDark) {
        if (isDark) SchemeIDEADark() else SchemeIDEALight()
    }

    // 重试就是再读一次：换一个 key 就重跑下面那个 produceState
    var attempt by remember(raw) { mutableIntStateOf(0) }

    val load by produceState<OxideLicenceLoad>(
        initialValue = OxideLicenceLoad.Loading,
        raw,
        attempt,
    ) {
        value = withContext(Dispatchers.IO) {
            val read = runCatching { context.readRawContent(raw) }
            read.exceptionOrNull()?.let { e ->
                Logger.warning(LICENCE_TAG, "Unable to read the licence text of R.raw/$raw", e)
            }
            oxideLicenceLoadOf(read)
        }
    }

    OxideSubWindow(
        title = stringResource(R.string.oxide_cap_licence_title),
        onClose = onDismiss,
        modifier = modifier,
        // 返回与关闭收的是同一件事：这块面板是压在上面的一层，关掉它就回到下面那一层
        // （从"关于"打开就是关于面板，从旧设置栈打开就是旧设置栈）
        onBack = onDismiss,
        // 正文由 SoraEditor 自己滚动，因此外壳这一层不能再套一个滚动容器
        scrollable = false,
    ) {
        when (val current = load) {
            is OxideLicenceLoad.Loading -> OxideLoadingRow(
                text = stringResource(R.string.oxide_common_loading),
            )

            is OxideLicenceLoad.Failed -> OxideEmptyState(
                title = stringResource(
                    when (current.reason) {
                        OxideLicenceFailure.Unreadable -> R.string.oxide_cap_licence_failed
                        OxideLicenceFailure.Empty -> R.string.oxide_cap_licence_empty
                    }
                ),
                detail = stringResource(
                    when (current.reason) {
                        OxideLicenceFailure.Unreadable -> R.string.oxide_cap_licence_failed_detail
                        OxideLicenceFailure.Empty -> R.string.oxide_cap_licence_empty_detail
                    }
                ),
                action = {
                    OxideButton(
                        text = stringResource(R.string.oxide_cap_retry),
                        onClick = { attempt++ },
                        tone = OxideButtonTone.Primary,
                        // 文件本身是空的：再读一次还是空的，那不是一次有意义的重试
                        enabled = current.reason == OxideLicenceFailure.Unreadable,
                    )
                },
            )

            is OxideLicenceLoad.Ready -> SoraEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = EditorState.Success(Content(current.text)),
                scheme = scheme,
                isReadOnly = true,
                onSaveClick = {},
                // 只读，因此没有保存动作，内置的保存圆钮也不需要
                floatingActionButton = {},
                // 面板本体就是 BgElevated：编辑器铺同一个色，两者之间不会出现一条缝
                containerColor = Oxide.BgElevated,
                contentColor = Oxide.Fg,
            )
        }
    }
}