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

package dev.oxide.launcher.ui.screens.main.control_editor.edit_style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.oxide.layercontroller.layout.RendererStyleBox
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutItem
import dev.oxide.launcher.ui.screens.main.control_editor.InfoLayoutTextItem
import dev.oxide.launcher.ui.screens.main.control_editor.editorMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell
import dev.oxide.launcher.ui.screens.main.oxide.OxideIconButton
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.string.isNotEmptyOrBlank

/**
 * 控件外观列表对话框
 *
 * 旧样式的 `Dialog` + Material `Surface` 卡片 + `Button`/`FilledTonalButton`，也就是设备
 * 截图 `controlpop2.jpg` 里那副"Control Appearance List"：灰卡片、鲑鱼色的 Create 与
 * Close。现在整块面板改由 [OxideDialogShell] 承载。
 *
 * 行为与 `JoystickStyleListDialog` 完全对应：返回键与点遮罩都关不掉、空列表那一行点一下
 * 就是新建、每一行仍然是预览 + 名称 + 复制 + 删除、点行本体才是编辑。字符串、校验与
 * 回调一个都没动，变的只有那一层壳。
 */
@Composable
fun StyleListDialog(
    styles: List<ObservableButtonStyle>,
    onEditStyle: (ObservableButtonStyle) -> Unit,
    onCreate: () -> Unit,
    onClone: (ObservableButtonStyle) -> Unit,
    onDelete: (ObservableButtonStyle) -> Unit,
    onClose: () -> Unit
) {
    val metrics = editorMetrics()

    OxideDialogShell(
        title = stringResource(R.string.control_editor_edit_style_config),
        onDismissRequest = onClose,
        dismissByDialog = false,
        body = { contentMaxHeight ->
            if (styles.isEmpty()) {
                InfoLayoutTextItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_style_config_empty),
                    onClick = onCreate,
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = contentMaxHeight)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
                ) {
                    // 不给 key：旧版就是 items(styles)，理由同摇杆那张——重复 uuid 不该变成崩溃
                    items(styles) { style ->
                        StyleItem(
                            modifier = Modifier.fillMaxWidth(),
                            style = style,
                            onClick = { onEditStyle(style) },
                            onClone = { onClone(style) },
                            onDelete = { onDelete(style) }
                        )
                    }
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.control_manage_create_new),
                onClick = onCreate,
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = stringResource(R.string.generic_close),
                onClick = onClose,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

@Composable
private fun StyleItem(
    style: ObservableButtonStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    val metrics = editorMetrics()
    val copyDescription = stringResource(R.string.generic_copy)
    val deleteDescription = stringResource(R.string.generic_delete)

    InfoLayoutItem(
        modifier = modifier,
        onClick = onClick
    ) {
        RendererStyleBox(
            modifier = Modifier.size(50.dp),
            style = style,
            text = "abc",
            isPressed = false,
            isDark = isLauncherInDarkTheme()
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = style.name.takeIf { it.isNotEmptyOrBlank() }
                ?: stringResource(R.string.generic_unspecified),
            color = Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        OxideIconButton(
            onClick = onClone,
            glyph = "⧉",
            size = metrics.miniButtonSize,
            contentDescription = copyDescription,
        )
        OxideIconButton(
            onClick = onDelete,
            glyph = "✕",
            size = metrics.miniButtonSize,
            contentDescription = deleteDescription,
        )
    }
}