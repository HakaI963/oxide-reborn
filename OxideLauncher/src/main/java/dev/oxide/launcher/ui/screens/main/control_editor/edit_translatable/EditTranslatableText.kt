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

package dev.oxide.launcher.ui.screens.main.control_editor.edit_translatable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.oxide.layercontroller.observable.ObservableLocalizedString
import dev.oxide.layercontroller.observable.ObservableTranslatableString
import dev.oxide.layercontroller.utils.toSimpleLangTag
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.OwnOutlinedTextField
import dev.oxide.launcher.ui.components.SingleLineTextCheck
import dev.oxide.launcher.ui.screens.main.control_editor.editorMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideDialogShell
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.utils.string.isEmptyOrBlank

/**
 * 编辑可翻译文本
 *
 * 旧实现是 `Dialog` + `ImePanContainer` + Material `Surface` 卡片 + Material `Button`/
 * `FilledTonalButton`，翻译项本身还嵌在一块 Material 的 item 卡里。现在面板由
 * [OxideDialogShell] 承载，翻译项那一层也换成 Oxide 的描边块。
 *
 * **能力一个都没少**，包括容易被换皮时悄悄弄丢的那几处：
 *
 * - `singleLine` 仍然既决定默认文本输入框是否单行，也决定每一条翻译的值是否单行；
 * - `take` 仍然按字符截断（语言标签固定 8），并且长度计数仍然写在输入框下面；
 * - `allowEmpty = false` 时空值仍然报"不能为空"，**而且不影响确认**——旧版就没有确认
 *   按钮，因此这一处没有新增任何禁用逻辑；
 * - 底部仍然是"添加其它语言"与"关闭"两枚，顺序与语气不变；
 * - [onDismissRequest] 给 null 时（当前唯一的调用点就是这样）旧版按返回键什么也不发生，
 *   新的面板仍然不会因此丢掉任何东西：关闭按钮与面板右上角的 ✕ 都走 [onClose]，
 *   而 [onClose] 本来就是"关闭"那一枚的回调。
 *
 * **明确保留的旧东西**：三个输入框仍然是 `ui/components` 的 `OwnOutlinedTextField`
 * （Material `OutlinedTextField` 的壳）。它被别的界面共用，改它是一次输入控件的重做，
 * 因此这一轮不动。
 *
 * @param onDismissRequest 由 Dialog 主动调用的关闭请求回调
 * @param onClose 由用户主动点击关闭按钮调用的关闭请求回调
 * @param title 对话框标题
 * @param take 限制输入文本的字数
 */
@Composable
fun EditTranslatableTextDialog(
    text: ObservableTranslatableString,
    onClose: () -> Unit,
    singleLine: Boolean = true,
    allowEmpty: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    title: String = stringResource(R.string.control_editor_edit_text),
    closeText: String = stringResource(R.string.generic_close),
    take: Int? = null
) {
    val blankError = stringResource(R.string.control_manage_create_new_field_blank)

    var fieldError by remember { mutableStateOf<String?>(null) }
    val isFieldError = remember(text.default) {
        fieldError = when {
            !allowEmpty && text.default.isEmptyOrBlank() -> blankError
            else -> null
        }
        fieldError != null
    }

    val metrics = editorMetrics()
    val locale = LocalConfiguration.current.locales[0]

    OxideDialogShell(
        title = title,
        // 旧版 `Dialog(onDismissRequest = { onDismissRequest?.invoke() })`：给了回调就走
        // 回调，没给就什么都不发生。面板右上角那枚 ✕ 同样走这一条，因此它永远不会
        // 是一个按了没反应的按钮——没给回调时它落到 [onClose]，与"关闭"那一枚一致。
        onDismissRequest = { if (onDismissRequest != null) onDismissRequest() else onClose() },
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(
                        R.string.control_editor_edit_translatable_other_tip,
                        locale.toSimpleLangTag()
                    ),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    textAlign = TextAlign.Center
                )

                val scrollState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        // 先夹住高度，再在里面滚
                        .heightIn(max = contentMaxHeight)
                        .fillMaxWidth(),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (singleLine) {
                                SingleLineTextCheck(
                                    text = text.default,
                                    onSingleLined = { text.default = it }
                                )
                            }

                            //默认文本
                            OwnOutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = text.default,
                                onValueChange = { string ->
                                    val new = take.take(string)
                                    text.default = new
                                },
                                label = {
                                    Text(stringResource(R.string.control_editor_edit_translatable_default))
                                },
                                isError = isFieldError,
                                supportingText = {
                                    fieldError?.let { Text(it) } ?: run {
                                        if (take != null) {
                                            Text(stringResource(R.string.generic_input_length, text.default.length, take))
                                        }
                                    }
                                },
                                singleLine = singleLine,
                                shape = RoundedCornerShape(16.dp),
                            )
                        }
                    }

                    items(text.matchQueue) { string ->
                        LocalizedStringItem(
                            modifier = Modifier.fillMaxWidth(),
                            string = string,
                            onDelete = {
                                text.deleteLocalizedString(string)
                            },
                            singleLine = singleLine,
                            allowEmpty = allowEmpty,
                            take = take
                        )
                    }
                }
            }
        },
        actions = {
            OxideButton(
                text = stringResource(R.string.control_editor_edit_translatable_other_add),
                onClick = {
                    text.addLocalizedString()
                },
                tone = OxideButtonTone.Secondary,
            )
            OxideButton(
                text = closeText,
                onClick = onClose,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 一条翻译
 *
 * 旧版是一块 Material 的 item 卡；现在是一块 Oxide 的描边块，因此语言标签与数值两行
 * 输入、以及那一整行删除，仍然都在，位置与顺序也没变。
 */
@Composable
private fun LocalizedStringItem(
    modifier: Modifier = Modifier,
    string: ObservableLocalizedString,
    onDelete: () -> Unit,
    singleLine: Boolean = true,
    allowEmpty: Boolean = true,
    take: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimpleEditBox(
            modifier = Modifier.fillMaxWidth(),
            value = string.languageTag,
            onValueChange = { tag ->
                string.languageTag = tag
            },
            label = stringResource(R.string.control_editor_edit_translatable_other_tag),
            take = 8
        )
        SimpleEditBox(
            modifier = Modifier.fillMaxWidth(),
            value = string.value,
            onValueChange = { value ->
                string.value = value
            },
            label = stringResource(R.string.control_editor_edit_translatable_other_value),
            singleLine = singleLine,
            allowEmpty = allowEmpty,
            take = take
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Oxide.RadiusControl)
                // 整行都是热区，与旧版一致（旧版也是把删除那一行整体 clickable）
                .clickable(onClick = onDelete)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outlined),
                contentDescription = stringResource(R.string.generic_delete),
                tint = Oxide.FgMuted
            )
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.generic_delete),
                color = Oxide.Fg,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SimpleEditBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    allowEmpty: Boolean = true,
    take: Int? = null
) {
    val blankError = stringResource(R.string.control_manage_create_new_field_blank)

    var fieldError by remember { mutableStateOf<String?>(null) }
    val isFieldError = remember(value) {
        fieldError = when {
            !allowEmpty && value.isEmptyOrBlank() -> blankError
            else -> null
        }
        fieldError != null
    }

    if (singleLine) {
        SingleLineTextCheck(
            text = value,
            onSingleLined = onValueChange
        )
    }

    OwnOutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { string ->
            val new = take.take(string)
            onValueChange(new)
        },
        label = {
            Text(text = label)
        },
        isError = isFieldError,
        supportingText = {
            fieldError?.let { Text(it) } ?: run {
                if (take != null) {
                    Text(stringResource(R.string.generic_input_length, value.length, take))
                }
            }
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
    )
}

private fun Int?.take(value: String) = this?.let { takes -> value.take(takes) } ?: value