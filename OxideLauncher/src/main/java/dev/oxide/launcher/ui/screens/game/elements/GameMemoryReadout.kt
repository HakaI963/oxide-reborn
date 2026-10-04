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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.platform.bytesToMB
import dev.oxide.launcher.utils.platform.getTotalMemory
import dev.oxide.launcher.utils.platform.getUsedMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 内存读数每秒刷新一次；和改造前 `MemoryPreview` 的默认间隔一致 */
private const val MEMORY_POLL_INTERVAL_MS = 1000L

/**
 * 悬浮球上的内存读数
 *
 * 替换掉原来那份 `MemoryPreview`：它用 `MaterialTheme` 的 `primary` /
 * `surfaceVariant` 配色与 `labelMedium`，在运行中的游戏上会跳出一块 Material 卡片，
 * 而且把数字画在填充块里面——那块填充跟着系统动态取色走，文字随时会读不出来。
 *
 * 读数与刷新间隔一字未改：`getTotalMemory` / `getUsedMemory` 仍然每秒在
 * `Dispatchers.Default` 上问一次，文案仍然是 `已用MB/总MB`。
 * 换掉的只有画法：一条发丝线边框的槽，已用部分按比例填上，
 * 数字放在槽的前端而不是填色里面，因此永远读得出来。
 *
 * 状态不只靠颜色：比例画成实心块，数值本身是文字，
 * 读屏软件念到的也是同一句。
 */
@Composable
internal fun GameMemoryReadout(
    modifier: Modifier = Modifier,
    minWidth: Dp = 168.dp,
    intervalMs: Long = MEMORY_POLL_INTERVAL_MS,
) {
    val context = LocalContext.current
    var totalMb by remember { mutableIntStateOf(0) }
    var usedMb by remember { mutableIntStateOf(0) }

    LaunchedEffect(context, intervalMs) {
        withContext(Dispatchers.Default) {
            while (isActive) {
                val total = getTotalMemory(context).bytesToMB().toInt()
                val used = getUsedMemory(context).bytesToMB().toInt()
                withContext(Dispatchers.Main) {
                    totalMb = total
                    usedMb = used
                }
                delay(intervalMs)
                ensureActive()
            }
        }
    }

    val text = remember(usedMb, totalMb) { formatGameOverlayMemory(usedMb, totalMb) }
    val fraction = remember(usedMb, totalMb) { gameOverlayMemoryFraction(usedMb, totalMb) }

    Row(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(14.dp)
            .clip(Oxide.RadiusSmall)
            .background(Oxide.BgChip)
            .border(BorderStroke(1.dp, Oxide.Line), Oxide.RadiusSmall)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 已用部分：实心的一块，宽度就是比例
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Oxide.FgGhost)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Mono.fontSize,
            lineHeight = Oxide.Type.Mono.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}