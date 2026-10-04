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

package dev.oxide.launcher.ui.screens.main.control_editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.colorpicker.components.TransparentChecker
import dev.oxide.colorpicker.rememberColorPickerController
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.ColorPickerDialog
import dev.oxide.launcher.ui.screens.main.oxide.Oxide

/**
 * 编辑器的信息行
 *
 * 这一族是"设置项"的形状：一个标题、一段可选的提示、右侧的值或控件。全部由
 * [Oxide] 的记号直接拼出来，没有一处 `MaterialTheme`。
 *
 * 与 `ui/components/Menu.kt` 里 `MenuTextButton` 那一族的关系：那边是**菜单项**
 * （整块卡片式、居中、给标题用），这边是**设置行**（左对齐、密集、给数值与开关用）。
 * 两者用同一套记号，但密度不同，因此各自保留一份。
 *
 * 数字一律走行内输入：[InfoLayoutSliderItem] 点数值就在轨道下方展开一个输入行，
 * 不再弹 `SliderValueEditDialog`——编辑器是压在正在接收游戏输入的画布上的，
 * 盖起来的那层 Material 对话框既难读，又抢走整块画面。
 *
 * 签名基本保持原样，因此编辑器目录以外的调用点（鼠标热区、账号页、键盘页）
 * 一行都不用改。
 */

// ---------------------------------------------------------------------------
// 容器
// ---------------------------------------------------------------------------

/**
 * 一行的底板
 *
 * 选中态除了强调色还多一条左侧指示与更亮的边框（见 `EditorSelectedMark`），
 * 因此不靠颜色单独表达。[content] 是整行的内容，通常由上面几个 `InfoLayout…` 组合出来。
 */
@Composable
fun InfoLayoutItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    /**
     * 旧的底色。保留在签名里是为了让现有调用点一行都不用改，
     * 但**不再参与渲染**——这一族现在只由 [Oxide] 的记号决定颜色，
     * 否则同一个区块里会出现两套底色互相打架。
     */
    @Suppress("UNUSED_PARAMETER") color: Color = Color.Transparent,
    @Suppress("UNUSED_PARAMETER") contentColor: Color = Color.Transparent,
    content: @Composable RowScope.() -> Unit
) {
    val metrics = LocalEditorMetrics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.rowHeight.coerceAtLeast(26.dp))
            .clip(Oxide.RadiusControl)
            .background(if (selected) Oxide.BgTabActive else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) Oxide.Line2 else Oxide.Line),
                Oxide.RadiusControl,
            )
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// 文本
// ---------------------------------------------------------------------------

/**
 * 一个可以点开的设置行，末尾带一个尖角
 *
 * 尖角画成两条斜线，正反两个方向都读得出来。`showArrow = false` 的那些是
 * "点一下就地展开一份选择"，尖角反而会误导成"会跳到另一个界面"。
 */
@Composable
fun InfoLayoutTextItem(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    selected: Boolean = false,
    enabled: Boolean = true,
    /**
     * 旧的底色。保留在签名里是为了让现有调用点一行都不用改，
     * 但**不再参与渲染**（理由同 [InfoLayoutItem]）。
     */
    @Suppress("UNUSED_PARAMETER") color: Color = Color.Transparent,
    @Suppress("UNUSED_PARAMETER") contentColor: Color = Color.Transparent,
) {
    InfoLayoutTextItem(
        modifier = modifier,
        title = title,
        icon = {
            if (showArrow) EditorChevron(expanded = false, enabled = enabled)
        },
        onClick = onClick,
        selected = selected,
        enabled = enabled,
    )
}

/** 带自定义图标的设置行 */
@Composable
fun InfoLayoutTextItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") color: Color = Color.Transparent,
    @Suppress("UNUSED_PARAMETER") contentColor: Color = Color.Transparent,
) {
    InfoLayoutItem(
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        enabled = enabled,
    ) {
        EditorSelectedMark(selected = selected)
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = if (enabled) Oxide.Fg else Oxide.FgFaint,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            EditorBadge(text = stringResource(R.string.generic_selected))
        } else {
            // 图标跟着选中态一起走：选中的那一项要先让人看见"它在被选着"
            CompositionLocalProvider(LocalContentColor provides Oxide.Fg) {
                icon()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 开关
// ---------------------------------------------------------------------------

/** 开关行：整行都是热区，滑块的位置只是第二重线索 */
@Composable
fun InfoLayoutSwitchItem(
    modifier: Modifier = Modifier,
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    EditorSwitchRow(
        modifier = modifier,
        label = title,
        checked = value,
        onCheckedChange = onValueChange,
        enabled = enabled,
    )
}

// ---------------------------------------------------------------------------
// 数值
// ---------------------------------------------------------------------------

/**
 * 滑杆 + 行内数字
 *
 * 拖轨道改值，点右边的数值就展开一行行内输入，可以直接敲精确的数字。
 * [decimalFormat] 保留旧的形参（`"#0.00"` / `"#0"`），由 [decimalsFromPattern]
 * 折算成小数位数——外面那些调用点一行都不用改，显示却与旧的一致。
 *
 * [fineTuningControl] / [fineTuningStep] 原本驱动 Material 滑杆自带的 `−/+` 微调。
 * 行内输入能一格一格改到同样的精度，因此它们不再参与渲染，但保留在签名里。
 */
@Composable
fun InfoLayoutSliderItem(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    decimalFormat: String = "#0.00",
    suffix: String? = null,
    @Suppress("UNUSED_PARAMETER") fineTuningControl: Boolean = true,
    @Suppress("UNUSED_PARAMETER") fineTuningStep: Float = 0.5f,
    enabled: Boolean = true,
) {
    val decimals = remember(decimalFormat) { decimalsFromPattern(decimalFormat) }
    EditorSliderRow(
        modifier = modifier,
        label = title,
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        // 抬手与提交都走这里：落盘与刷新只发生一次，不在拖动的每一帧上
        onValueChangeFinished = { onValueChangeFinished?.invoke() },
        enabled = enabled,
        suffix = suffix,
        decimals = decimals,
        // 折算出的位数是 0 就说明这一项只收整数
        integerOnly = decimals == 0,
    )
}

// ---------------------------------------------------------------------------
// 单选
// ---------------------------------------------------------------------------

/**
 * 点开就地列出候选项的设置行
 *
 * 候选项多到铺不下时，[EditorChoiceRow] 才给这一块自己的滚动，并且高度被夹住，
 * 因此不存在无界的嵌套滚动。原先的 [maxListHeight] 形参因此不再参与渲染，
 * 但保留在签名里。
 */
@Composable
fun <E> InfoLayoutListItem(
    modifier: Modifier = Modifier,
    title: String,
    items: List<E>,
    selectedItem: E,
    onItemSelected: (E) -> Unit,
    getItemText: @Composable (E) -> String,
    maxListHeight: Dp = 200.dp
) {
    EditorChoiceRow(
        modifier = modifier,
        label = title,
        options = items,
        selected = selectedItem,
        optionText = getItemText,
        onSelect = onItemSelected,
    )
}

/**
 * 2 到 4 个短选项时就地铺开的分段行
 *
 * [label] 给的是图标而不是文字，因此每一格里原样画出来；朗读由格子的
 * `Role.RadioButton` 与 selected 承担。
 */
@Composable
fun <E> InfoLayoutSelectItem(
    modifier: Modifier = Modifier,
    title: String,
    options: List<E>,
    current: E,
    onClick: (E) -> Unit,
    label: @Composable (E) -> Unit,
) {
    EditorSegmentRow(
        modifier = modifier,
        label = title,
        options = options,
        selected = current,
        optionText = { _ -> "" },
        onSelect = onClick,
        optionContent = label,
    )
}

// ---------------------------------------------------------------------------
// 颜色
// ---------------------------------------------------------------------------

/**
 * 颜色行：左边一块色样，右边一个尖角
 *
 * 取色器本身仍是 `ColorPickerDialog`——它是一块完整的第三方面板，重画它不在这
 * 一轮的范围里；但**这一行**的形状、边框与色样底板都是 Oxide 的。
 */
@Composable
fun InfoLayoutColorItem(
    modifier: Modifier = Modifier,
    title: String,
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var showColorDialog by remember { mutableStateOf(false) }

    InfoLayoutTextItem(
        modifier = modifier,
        title = title,
        icon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(16.dp)) {
                    TransparentChecker(
                        modifier = Modifier.fillMaxWidth().size(16.dp),
                        gridSize = 12f
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(16.dp)
                            .background(color = color)
                    )
                }
                EditorChevron(expanded = false)
            }
        },
        onClick = {
            showColorDialog = true
        }
    )

    if (showColorDialog) {
        var tempColor by remember { mutableStateOf(color) }
        val colorController = rememberColorPickerController(initialColor = tempColor)

        val currentColor by remember(colorController) { colorController.color }

        LaunchedEffect(currentColor) {
            onColorChanged(currentColor)
        }

        ColorPickerDialog(
            colorController = colorController,
            onCancel = {
                onColorChanged(colorController.getOriginalColor())
                showColorDialog = false
            },
            onConfirm = { picked ->
                showColorDialog = false
                onColorChanged(picked)
            }
        )
    }
}