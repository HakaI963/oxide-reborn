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

package dev.oxide.launcher.ui.screens.main.control_editor.edit_joystick

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
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
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
 * 摇杆样式列表展示对话框
 *
 * 过去这一张是旧样式的 `Dialog` + Material `Surface` 卡片 + `Button`/`FilledTonalButton`：
 * 灰卡片加鲑鱼色按钮，正是设备截图 `controlpop.jpg` 里那副样子。现在它由
 * [OxideDialogShell] 承载，与启动器里其余的对话框共用同一块面板——不透明底、
 * 同一套圆角与 1px 描边、同一套按钮语气。
 *
 * 行为、字符串、校验与回调一字未改：
 *
 * - 面板仍然**不可**靠返回键或点遮罩关掉（[OxideDialogShell] 的 `dismissByDialog = false`
 *   同时关掉 `dismissOnBackPress` 与遮罩点击），要离开只能点"关闭"；
 * - 空列表时正文那一行仍然可点，点了就是新建，文案仍是同一条
 *   `control_editor_edit_joystick_style_list_empty`；
 * - 每一行仍然带着预览、名称、复制、删除四个动作，点行本体才是编辑；
 * - 底部两枚按钮的顺序与语气不变：新建是描边、关闭是实心。
 *
 * 变的只有那一层壳：列表滚动区现在由面板给的 `contentMaxHeight` 夹住（旧版是
 * `fillMaxWidth(0.6f)` 加 `rememberDialogMaxHeight`），面板宽度也不再硬写六成，
 * 而是由 `oxideDialogMaxSize` 从真实窗口尺寸推出。
 */
@Composable
fun JoystickStyleListDialog(
    styles: List<ObservableJoystickStyle>,
    onEditStyle: (ObservableJoystickStyle) -> Unit,
    onCreate: () -> Unit,
    onClone: (ObservableJoystickStyle) -> Unit,
    onDelete: (ObservableJoystickStyle) -> Unit,
    onClose: () -> Unit
) {
    val metrics = editorMetrics()

    OxideDialogShell(
        title = stringResource(R.string.control_editor_edit_joystick_style_list),
        // 关闭仍然只由"关闭"这一条路走：返回键与点遮罩都收不到
        onDismissRequest = onClose,
        dismissByDialog = false,
        body = { contentMaxHeight ->
            if (styles.isEmpty()) {
                // 空列表：旧版是一行可点的提示，点它就是新建，文案不变
                InfoLayoutTextItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_joystick_style_list_empty),
                    onClick = onCreate,
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        // 先夹住高度，再在里面滚——旧版是把整个 Box 撑满再让列表缩
                        .heightIn(max = contentMaxHeight)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
                ) {
                    // 不给 key：旧版就是 items(styles)，样式文件里万一出现重复 uuid，显式 key
                    // 会直接把 LazyColumn 变成一次崩溃，而那不是换皮该带来的风险
                    items(styles) { style ->
                        JoystickStyleItem(
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
private fun JoystickStyleItem(
    style: ObservableJoystickStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isLauncherInDarkTheme()
    val config = resolveThemeConfig(style, isDark)

    val metrics = editorMetrics()
    val copyDescription = stringResource(R.string.generic_copy)
    val deleteDescription = stringResource(R.string.generic_delete)

    InfoLayoutItem(
        modifier = modifier,
        onClick = onClick
    ) {
        JoystickStylePreview(
            modifier = Modifier.size(50.dp),
            config = config
        )
        Spacer(modifier = Modifier.width(8.dp))

        // 旧版是 MarqueeText：名字长时横向滚动。这里换成单行省略号——面板里
        // 名字后面还跟着两枚按钮，横向滚动会和它们抢手势
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