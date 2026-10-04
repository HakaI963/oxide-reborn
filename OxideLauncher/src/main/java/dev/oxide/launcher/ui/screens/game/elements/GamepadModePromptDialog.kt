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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.GamepadInputMode
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 手柄输入模式选择对话框
 *
 * 手柄第一次接上时弹出，玩的是"在选完之前游戏一根按键都收不到"这件事
 * （见 `GameHandler.shouldIgnoreKeyEvent`：选择完成前所有手柄按键都被吞掉）。
 * 因此这块面板**必须**继续挡在返回键前面——它仍然是一个
 * [Dialog]，而不是画在游戏层上的一层浮层；换掉它就等于把 ESC 漏给了游戏。
 *
 * 换掉的是那张 Material 卡片：
 *
 * - `MaterialTheme.shapes.extraLarge` 的 28dp 圆角、`headlineSmall` 的 24sp 标题，
 *   在这块近黑语言里都偏大偏圆；现在是 [Oxide.RadiusPanel] 与
 *   [Oxide.Type.DrawerTitle]，与游戏内菜单同一套。
 * - `RadioButton` 换成一整行可点 + 方块标记，并带 `Role.RadioButton`：
 *   原来只有那个 20dp 的小圆点能点，手柄或手指都得瞄它；
 *   选中态也不再只靠颜色——标记是**填充**与**空心**的区别。
 * - 面板高度由真实窗口夹住（见 [BoxWithConstraintsScope.rememberGameOverlayBounds]），
 *   三种模式的说明在很小的游戏窗口里会在**被夹住的**内容区里滚。
 *
 * 没有关闭按钮，这是**照旧**：这张面板除了确认没有别的出路，
 * 原来也没有（`onDismissRequest = {}` 加 `dismissOnBackPress = false`）。
 * 加一个 ✕ 等于允许玩家在没选的情况下走开，那样 [dev.oxide.launcher.viewmodel.GamepadViewModel]
 * 会停在等待选择的状态上。
 */
@Composable
fun GamepadModePromptDialog(
    visible: Boolean,
    onConfirm: (GamepadInputMode) -> Unit
) {
    if (!visible) return
    var selected by remember {
        mutableStateOf(AllSettings.gamepadInputMode.state)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val bounds = rememberGameOverlayBounds()
            GameOverlayPanel(
                bounds = bounds,
                modifier = Modifier.padding(bounds.edgeMargin),
            ) {
                GameOverlayHeader(
                    title = stringResource(R.string.settings_gamepad_input_mode_title),
                    bounds = bounds,
                )
                GameOverlayHairline()

                GameOverlayScrollArea(
                    maxHeight = bounds.contentMaxHeight,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(
                            start = bounds.padding,
                            end = bounds.padding,
                            top = bounds.rowGap,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.gamepad_mode_prompt_description),
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        modifier = Modifier.padding(bottom = bounds.rowGap),
                    )

                    GamepadInputMode.entries.forEach { mode ->
                        GameOverlayChoiceRow(
                            title = stringResource(mode.titleRes),
                            description = stringResource(mode.summaryRes),
                            selected = selected == mode,
                            onSelect = { selected = mode },
                            minHeight = bounds.buttonHeight,
                        )
                    }

                    GameOverlayNote(
                        text = stringResource(R.string.gamepad_mode_prompt_hint),
                        modifier = Modifier.padding(top = bounds.rowGap),
                    )
                }

                Spacer(Modifier.height(bounds.rowGap))
                GameOverlayHairline()
                GameOverlayFooter(bounds = bounds) {
                    GameOverlayButton(
                        text = stringResource(R.string.generic_confirm),
                        onClick = { onConfirm(selected) },
                        minHeight = bounds.buttonHeight,
                        tone = GameOverlayButtonTone.Primary,
                        //确认按钮不参与焦点导航，手柄输入也已在更上游被吞掉，避免误触
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        }
    }
}