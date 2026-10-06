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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 对话框里的那条标签栏
 *
 * 三个旧对话框原来用 Material 的 `SecondaryTabRow` + `Tab`（控件样式、摇杆样式、
 * 摇杆的启动器事件各一条）。它们是这一轮里剩下的最后几处 Material 外观，因此换成
 * 这一条与 [EditorSegmentRow]、对话框面板同一套记号的分段。
 *
 * 与 Material 那一版的行为完全对应：
 *
 * - 标签仍然按顺序铺开、等宽；
 * - 点标签仍然是**立刻**切页——调用方仍然自己 `animateScrollToPage`，这一条不碰滚动；
 * - 选中态既有更亮的底与边框，也由 `Role.Tab` 与 selected 交给无障碍服务，
 *   因此不只靠颜色。
 *
 * 单文件一份，供 `edit_style`、`edit_joystick` 与 `edit_widget` 三处共用。
 */
@Composable
internal fun EditorDialogTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = editorMetrics().rowHeight.coerceAtLeast(26.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight)
                    .clip(Oxide.RadiusChip)
                    .background(if (selected) Oxide.BgTabActive else Color.Transparent)
                    .border(
                        BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                        Oxide.RadiusChip,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = if (selected) Oxide.Fg else Oxide.FgMuted,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}