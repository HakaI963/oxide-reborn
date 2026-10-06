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

package dev.oxide.launcher.ui.screens.game.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.bridge.LoggerBridge
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.screens.game.elements.log_parser.LogHighlighter
import dev.oxide.launcher.ui.theme.Oxide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * 游戏内日志框
 *
 * 收集、缓冲、刷新与自动滚动这条链**一字未改**：`LoggerBridge` 的监听仍然是
 * 同一个单例回调，缓冲区仍然是那个线程安全的列表，刷新间隔仍然取
 * `AllSettings.logBufferFlushInterval`，日志仍然通过同一个通道推给
 * `LazyColumn`。谁在读这份日志（崩溃日志、问题反馈）拿到的仍然是同一份文本。
 *
 * 换掉的是外面那两层壳：
 *
 * - 原来是 `Color.Black.copy(0.5f)` 加 `Color.White`。半透明的黑压在任何一帧
 *   游戏画面上都会让日志时隐时现——雪地场景下白字直接看不见。
 *   现在是 [Oxide.PopoverBg]（98% 不透明）配中性前景色 [Oxide.Fg]。
 *   注意这两者是一对，且都必须真的传进去：底幕与前景都跟着主题翻面，
 *   写死任何一个都会让另一边翻车。正文这次之前**根本没有传颜色**，
 *   `Text` 就落回 Compose 的默认 [androidx.compose.ui.graphics.Color.Black]，
 *   在深色底幕上是 1.1:1——也就是用户截图里那一片看不清的字。
 *   [Oxide.Fg] 现在同时喂给正文和 `LogHighlighter` 的兜底色。
 * - 右边那列按钮原来是 Material 的 `IconButton`，四个图标的
 *   `contentDescription` **全是 null**：读屏软件只会念出"按钮"，用户不知道
 *   哪一个是关闭、哪一个是清空。现在每一个都有一句朗读文本，自动滚动那一枚
 *   还把"跟着/不跟着"作为状态说出去，因此不只靠底色深浅表达。
 *
 * 这一块**不**加 [consumeTouches]：它是一块 HUD 而不是模态面板，
 * 日志开着的时候玩家还要操作游戏。落在日志正文与按钮以外的触摸照旧漏给游戏，
 * 列表自己的拖动与四个按钮各自的事件照旧被消费。
 */
@Composable
fun LogBox(
    enableLog: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()

    val logList = remember { mutableStateListOf<AnnotatedString>() }
    val buffer = remember { Collections.synchronizedList(mutableListOf<AnnotatedString>()) }
    val scrollChannel = remember { mutableStateOf<Channel<Unit>?>(null) }

    // 正文底幕上的前景色。组合阶段读 [Oxide.Fg] 就是订阅它，深浅色一改，
    // 下面两处（着色器的兜底色与正文）跟着一起换色
    val foreground = Oxide.Fg

    val logHighlighter = remember(foreground) { LogHighlighter(defaultColor = foreground) }
    // 着色器会被换掉，但监听回调是 [LaunchedEffect] 装一次就不再动的：
    // 不读最新值的话，换主题之后新日志还会用旧配色着色，直到开关一次日志
    val currentHighlighter by rememberUpdatedState(logHighlighter)
    var autoScrollDown by remember { mutableStateOf(true) }

    val config = remember {
        object {
            /** 缓冲区刷新间隔，单位：ms */
            val BUFFER_FLUSH_INTERVAL: Long = AllSettings.logBufferFlushInterval.getValue().toLong()
        }
    }

    LaunchedEffect(enableLog) {
        if (enableLog) {
            scrollChannel.value = Channel(capacity = 100)

            LoggerBridge.setListener { log ->
                synchronized(buffer) {
                    val string = currentHighlighter.highlight(log)
                    buffer.add(string)
                }
            }

            launch(Dispatchers.Default) {
                val mutex = Mutex()
                while (isActive) {
                    try {
                        ensureActive()
                        delay(config.BUFFER_FLUSH_INTERVAL.milliseconds)
                        val pending = mutableListOf<AnnotatedString>()

                        mutex.withLock {
                            synchronized(buffer) {
                                if (buffer.isNotEmpty()) {
                                    pending.addAll(buffer)
                                    buffer.clear()
                                }
                            }
                        }
                        if (pending.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                logList.addAll(pending)
                                if (autoScrollDown) {
                                    //尝试进行滚动
                                    scrollChannel.value?.trySend(Unit)
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        break
                    }
                }
            }

            //自动滚动部分
            launch(Dispatchers.Main) {
                scrollChannel.value?.consumeAsFlow()?.collect {
                    runCatching {
                        val targetIndex = logList.lastIndex
                        if (targetIndex >= 0 && targetIndex < logList.size) {
                            scrollState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            }
        } else {
            scrollChannel.value = null
            LoggerBridge.setListener(null)
            logList.clear()
            buffer.clear()
        }
    }

    if (!enableLog) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 按钮的热区跟着游戏窗口与界面缩放走，不写死
        val bounds = rememberGameOverlayBounds()

        Row(modifier = Modifier.fillMaxSize()) {
            //日志正文
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Oxide.PopoverBg)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState
                ) {
                    items(logList) { log ->
                        val textSize = AllSettings.logTextSize.state
                        val fontSize = remember(textSize) {
                            TextUnit(textSize.toFloat(), TextUnitType.Sp)
                        }
                        val lineHeight = remember(textSize) {
                            val height = textSize.toFloat() * 1.1f
                            TextUnit(height, TextUnitType.Sp)
                        }

                        Text(
                            // 高亮的颜色是日志级别的一部分（见 LogLevelRule），不是配色：
                            // 换掉它就分不出 INFO / WARN / ERROR 了
                            text = log,
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            // 正文颜色必须显式给：不给就落回 Color.Black，
                            // 在 0xFA0D0D0D 的底幕上是 1.1:1（见文件头）
                            color = foreground,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                }
            }

            //分隔用的发丝线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Oxide.Line)
            )

            //右侧控制区域
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Oxide.BgElevated)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    //关闭
                    GameOverlayIconButton(
                        painter = painterResource(R.drawable.ic_close),
                        description = stringResource(R.string.oxide_ingame_log_close),
                        onClick = onClose,
                        size = bounds.buttonHeight,
                        enabled = true,
                        selected = false
                    )
                    //清理
                    GameOverlayIconButton(
                        painter = painterResource(R.drawable.ic_delete_outlined),
                        description = stringResource(R.string.oxide_ingame_log_clear),
                        onClick = {
                            synchronized(buffer) {
                                logList.clear()
                                buffer.clear()
                            }
                        },
                        size = bounds.buttonHeight,
                        enabled = true,
                        selected = false
                    )
                    //自动滚动
                    GameOverlayIconButton(
                        painter = painterResource(R.drawable.ic_list_down),
                        description = stringResource(
                            if (autoScrollDown) {
                                R.string.oxide_ingame_log_auto_scroll_on
                            } else {
                                R.string.oxide_ingame_log_auto_scroll_off
                            }
                        ),
                        onClick = {
                            val value = !autoScrollDown
                            autoScrollDown = value
                            if (value) {
                                scrollChannel.value?.trySend(Unit)
                            }
                        },
                        size = bounds.buttonHeight,
                        enabled = true,
                        selected = autoScrollDown,
                    )
                    //滚动到底部
                    GameOverlayIconButton(
                        painter = painterResource(R.drawable.ic_arrow_cool_down),
                        description = stringResource(R.string.oxide_ingame_log_scroll_to_end),
                        onClick = {
                            scrollChannel.value?.trySend(Unit)
                        },
                        size = bounds.buttonHeight,
                        enabled = true,
                        selected = false
                    )
                }
            }
        }
    }
}