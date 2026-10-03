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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.components.imePanAnchor
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 新界面的共用零件
 *
 * 账号、联机、文件、日志、启动这五块表面共用下面这些东西：
 * 由 [OxideMetrics] 推导出来的尺寸、紧凑输入框、就地确认条、错误条与方块头像。
 *
 * 之所以集中在这里而不是各自写一份，是因为它们全都必须是"无 Material 外观"的：
 * 一旦某一块表面自己拼一个 Material 输入框或 Material 弹窗，
 * 这一块表面就会在整体近黑的语言里露出另一种配色的边。
 *
 * 所有尺寸都由 [OxideMetrics] 的现有字段推导，页面不写死 dp。
 */

// ---------------------------------------------------------------------------
// 由 metrics 推导出来的尺寸
// ---------------------------------------------------------------------------

/** 一组紧凑行之间的间距 */
internal val OxideMetrics.secRowGap: Dp get() = cardGap / 4

/** 分组之间额外留出的间距 */
internal val OxideMetrics.secGroupGap: Dp get() = sectionGap / 2

/** 紧凑控件（标签页、筛选按钮）的高度 */
internal val OxideMetrics.secControlHeight: Dp get() = navItemHeight * 0.78f

/** 输入框的高度：比控件高一点，放得下一个光标而不显空 */
internal val OxideMetrics.secInputHeight: Dp get() = navItemHeight * 0.92f

/** 紧凑控件的横向内边距 */
internal val OxideMetrics.secControlPadding: Dp get() = cardGap * 0.45f

// ---------------------------------------------------------------------------
// 控件
// ---------------------------------------------------------------------------

/**
 * 紧凑输入框
 *
 * 用 [BasicTextField] 而不是 Material 的 `OutlinedTextField`：Material 的输入框
 * 自带配色、圆角与浮动标签，在近黑语言里既跳色又占掉一倍高度。
 * 这里只画一块底 + 一条描边 + 光标，出错时描边抬到正文色，状态不只靠这一点表达。
 */
@Composable
internal fun OxideSecInput(
    metrics: OxideMetrics,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    password: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        label?.let {
            Text(
                text = it,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                letterSpacing = Oxide.Type.MicroLabel.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.secInputHeight)
                .clip(Oxide.RadiusControl)
                .background(Oxide.BgButton)
                .border(
                    BorderStroke(
                        1.dp,
                        when {
                            isError -> Oxide.FgMuted
                            enabled -> Oxide.Line
                            else -> Oxide.LineFaint
                        }
                    ),
                    Oxide.RadiusControl,
                )
                .imePanAnchor()
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = Oxide.Type.Body.copy(
                    color = if (enabled) Oxide.Fg else Oxide.FgFaint
                ),
                cursorBrush = SolidColor(Oxide.FgMuted),
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = imeAction,
                    keyboardType = when {
                        numeric -> KeyboardType.Number
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(true)
                        onDone()
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Oxide.FgFaint,
                                fontSize = Oxide.Type.Body.fontSize,
                                lineHeight = Oxide.Type.Body.lineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/**
 * 就地确认条
 *
 * 不用 Material 的 `AlertDialog`：横屏里弹窗会盖掉整块内容，而且它自带的
 * 圆角与配色会在这套近黑语言里明显跳出来。确认条就贴在触发它的内容下面，
 * 视线不需要离开正在做的事。
 */
@Composable
internal fun OxideSecConfirmBar(
    metrics: OxideMetrics,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusControl)
            .padding(
                start = metrics.secControlPadding,
                end = metrics.secRowGap,
                top = metrics.secRowGap,
                bottom = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Oxide.FgMuted,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(
            text = dismissText,
            onClick = onDismiss,
            tone = OxideButtonTone.Ghost,
        )
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(
            text = confirmText,
            onClick = onConfirm,
            tone = OxideButtonTone.Primary,
            enabled = enabled,
        )
    }
}

/**
 * 错误条
 *
 * 失败就在原地说出来，而不是依赖另一个 Activity 头上的弹窗：
 * 这样即使错误来自后台任务，用的人也仍然看得见它发生在哪一块表面上。
 */
@Composable
internal fun OxideSecErrorRow(
    metrics: OxideMetrics,
    title: String,
    detail: String,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Oxide.RadiusControl)
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusControl)
            .padding(
                start = metrics.secControlPadding,
                end = metrics.secRowGap,
                top = metrics.secRowGap,
                bottom = metrics.secRowGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Oxide.FgStrong,
                fontSize = Oxide.Type.BodyStrong.fontSize,
                lineHeight = Oxide.Type.BodyStrong.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(metrics.secRowGap))
        OxideButton(
            text = dismissText,
            onClick = onDismiss,
            tone = OxideButtonTone.Ghost,
        )
    }
}

/**
 * 方块头像
 *
 * 只画首字母，不读皮肤文件——组合阶段不碰磁盘；
 * 皮肤与披风的真实存在与否由"皮肤 / 披风"那一组行如实说明。
 */
@Composable
internal fun OxideSecAvatar(
    initial: String,
    description: String,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Oxide.RadiusBlock)
            .background(Brush.linearGradient(listOf(Oxide.BgButtonHover, Oxide.BgElevated)))
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusBlock,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Oxide.Fg,
            fontSize = Oxide.Type.BodyStrong.fontSize,
            lineHeight = Oxide.Type.BodyStrong.lineHeight,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 1px 分隔线 */
@Composable
internal fun OxideSecDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Oxide.LineFaint)
    )
}

/**
 * 一个可选中的紧凑标签
 *
 * 用 [selectable] 而不是 `clickable`：选中态会同时以 `Role.Tab` 与 selected
 * 暴露给无障碍服务，标签不会只靠底色区分。
 */
@Composable
internal fun OxideSecChip(
    label: String,
    selected: Boolean,
    metrics: OxideMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OxideSurface(
        modifier = modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        selected = selected,
        contentPadding = PaddingValues(
            horizontal = metrics.secControlPadding,
            vertical = metrics.secRowGap,
        ),
    ) {
        Text(
            text = label,
            color = if (selected) Oxide.Fg else Oxide.FgGhost,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 一行单选项
 *
 * 选中态由左侧那个方块承担，同时整行带 `Role.RadioButton` 与 selected，
 * 因此朗读时也知道"现在选的是哪一个"，不靠颜色说话。
 */
@Composable
internal fun OxideSecPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    hint: String? = null,
) {
    OxideSettingRow(
        label = label,
        value = value,
        hint = hint,
        enabled = true,
        modifier = modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        trailing = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(Oxide.RadiusBadge)
                    .border(
                        BorderStroke(1.dp, if (selected) Oxide.FgMuted else Oxide.Line2),
                        Oxide.RadiusBadge,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(Oxide.RadiusBadge)
                            .background(Oxide.FgMuted)
                    )
                }
            }
        },
    )
}

/**
 * 给纯字形的小图标按钮补上朗读文本
 *
 * [OxideIconButton] 画的是一个字符而不是矢量图标，因此必须显式描述它；
 * `mergeDescendants` 让它成为自己子树合并后的单一节点，朗读时才不会
 * 被当成一个没有动作的孤立文字。
 */
internal fun Modifier.oxideIconDescription(description: String): Modifier =
    semantics(mergeDescendants = true) { contentDescription = description }