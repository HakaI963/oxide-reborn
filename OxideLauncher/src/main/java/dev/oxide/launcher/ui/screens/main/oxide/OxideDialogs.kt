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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import android.app.Activity
import android.app.Dialog as AndroidDialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.screens.content.elements.DisabledAlpha
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.utils.logging.Logger
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "OxideDialogs"

/**
 * Oxide 唯一的一套对话框
 *
 * 之前每个界面各自拼弹窗：Material 的 `AlertDialog` 一套、`ui/components/Dialogs.kt`
 * 里那五个 `Simple*Dialog` 一套、新界面里手写的一套。三套语言在圆角、底色、
 * 按钮高度和关闭方式上互相都不一致，而其中几套的底色只有 9% 不透明度，
 * 叠在游戏截图上会直接看穿（`Oxide.SurfaceBase` 就是 `0x170A0A0A`，
 * 它是给"不透明页面上的卡片"用的，不是给对话框用的）。
 *
 * 这里只有一块面板：[OxideDialogPanel]。确认、输入、列表、进度四个对话框
 * 和升级、崩溃日志那些旧对话框全部由它拼出来，因此：
 *
 * - 面板底色是 [Oxide.BgElevated]（**不透明**），遮罩是 [Oxide.DrawerScrim]；
 * - 圆角 [Oxide.RadiusDrawer]、1px [Oxide.Line2] 描边、标题用 [Oxide.Type.DrawerTitle]；
 * - 宽高上限由**真实窗口尺寸**推出（见 [oxideDialogMaxSize]），居中，640×360 也装得下；
 * - 滚动区的高度**先**被夹住，再在里面滚（见下面关于 v1.5.0 P0 崩溃的说明）。
 *
 * 底下那一整段几何策略（[oxideDialogMaxSize]、[oxideDialogContentMaxHeight]、
 * [oxideDialogListHeight]、[oxideEntryVerdict] …）全是纯函数：不读设置、不碰
 * Android、也不组合，因此可以在单测里把每一档数值钉死。
 */

// ---------------------------------------------------------------------------
// 纯几何策略（无 Compose、无 Android）
// ---------------------------------------------------------------------------

/** 面板与窗口边缘之间留出的空白 */
val OxideDialogEdgeMargin: Dp = 12.dp

/** 面板的理想宽度上限。再宽的窗口也只给这么宽——对话框不是页面 */
val OxideDialogPreferredWidth: Dp = 460.dp

/** 面板的理想高度上限 */
val OxideDialogPreferredHeight: Dp = 620.dp

/**
 * 面板的最小设计尺寸
 *
 * 窗口比它还小时按"窗口减去四周留白"继续收缩，但绝不塌成 0。
 */
val OxideDialogMinPanelSize: Dp = 120.dp

/**
 * 退化窗口（容器量到 0×0，也就是面板刚创建、还没布局的那一帧）的兜底值
 *
 * 宽度为 0 的面板会把标题和按钮一起量成 0 高，朗读与触摸目标检查也跟着失效，
 * 因此宁可给 1dp。
 */
val OxideDialogAbsoluteMinSize: Dp = 1.dp

/**
 * 标题栏与按钮栏这两块**固定**高度
 *
 * 它们不参与滚动，所以内容区能拿到的天花板就是面板上限减去这一块。
 * 与 [OxideDialogPanel] 里实际用的留白一一对应。
 */
val OxideDialogChromeHeight: Dp = 118.dp

/** 滚动内容区的高度下限，保证说明文字和按钮永远看得见 */
val OxideDialogMinContentHeight: Dp = 56.dp

/** 弹窗里图标按钮至少这么大，再小也还是能按到 */
val OxideDialogMinTouchTarget: Dp = 32.dp

/** 单选指示块的边长，由 metrics 推出，不写死 dp */
internal val OxideMetrics.dialogMarkSize: Dp get() = secRowGap * 4f

/** 选中标记里那个实心块的边长 */
internal val OxideMetrics.dialogMarkDotSize: Dp get() = secRowGap * 1.6f

/** 列表里一行的最小高度：两行文字加上下留白 */
internal val OxideMetrics.dialogOptionRowHeight: Dp get() = secInputHeight + secRowGap * 2

/** 面板实际允许的宽高上限 */
@Immutable
data class OxideDialogSize(
    val maxWidth: Dp,
    val maxHeight: Dp,
)

/** 面板所在窗口的尺寸，单位 dp */
@Immutable
data class OxideDialogWindow(
    val widthDp: Int,
    val heightDp: Int,
)

/**
 * 缩放系数兜底：NaN / 0 / 负数一律按 100% 处理
 *
 * `Dp` 的 `coerceAtLeast` 碰到 NaN 不会抛异常，但也不会换掉它，NaN 会一路
 * 传到 `heightIn`，而布局阶段会在一个没法比较的高度上直接崩掉。
 */
private fun oxideDialogSafeScale(guiScale: Float): Float =
    if (guiScale.isFinite() && guiScale > 0f) guiScale else 1f

/**
 * 自适应面板尺寸：从**真实窗口尺寸**推出宽度与高度上限
 *
 * 三个约束从上往下取最小值：理想上限、窗口减去四周留白（且不小于
 * [OxideDialogMinPanelSize]）、以及窗口本身。第三个约束是"面板永远不超过窗口"
 * 这条不变式的来源——没有它，在 200dp 宽的窗口上 [OxideDialogMinPanelSize]
 * 会把面板撑到比窗口还宽。
 *
 * 退化窗口（0 或负数）返回 [OxideDialogAbsoluteMinSize]：恒为正、恒有限，
 * 但也不会试图画出一个比窗口还大的面板。
 *
 * 纯函数，可直接单测。
 */
fun oxideDialogMaxSize(
    windowWidthDp: Int,
    windowHeightDp: Int,
    guiScale: Float = 1f,
): OxideDialogSize {
    val scale = oxideDialogSafeScale(guiScale)

    fun axis(windowDp: Int, preferred: Dp): Dp {
        val window = windowDp.dp
        if (window <= 0.dp) return OxideDialogAbsoluteMinSize
        val available = (window - OxideDialogEdgeMargin * scale * 2)
            .coerceAtLeast(OxideDialogMinPanelSize * scale)
        return minOf(preferred * scale, available, window)
    }

    return OxideDialogSize(
        maxWidth = axis(windowWidthDp, OxideDialogPreferredWidth),
        maxHeight = axis(windowHeightDp, OxideDialogPreferredHeight),
    )
}

/**
 * 面板里滚动内容区的高度上限
 *
 * = 面板高度上限 − 标题栏与按钮栏。结果**至少**是 [OxideDialogMinContentHeight]：
 * 内容区被压到 0 时，里面的 `verticalScroll` 会拿到 `maxHeight = 0`，
 * 而 `LazyColumn` 在 0 高度下连一个项都不会画，更谈不上滚动。
 *
 * 纯函数，可直接单测。
 */
fun oxideDialogContentMaxHeight(
    panelMaxHeight: Dp,
    guiScale: Float = 1f,
): Dp {
    val scale = oxideDialogSafeScale(guiScale)
    return (panelMaxHeight - OxideDialogChromeHeight * scale)
        .coerceAtLeast(OxideDialogMinContentHeight)
}

/**
 * 内容区实际拿到的高度：自然高度与上限取小
 *
 * 上限本身也会先被兜住（未指定或非正 → [OxideDialogAbsoluteMinSize]），
 * 因此返回值恒为有限值，`verticalScroll` 永远不会拿到 `Infinity`。
 *
 * 纯函数，可直接单测。
 */
fun oxideDialogContentHeight(naturalHeight: Dp, maxHeight: Dp): Dp {
    val cap = if (maxHeight.isUsableBound()) maxHeight else OxideDialogAbsoluteMinSize
    val natural = if (naturalHeight.isFiniteNumber()) naturalHeight else cap
    return natural.coerceIn(0.dp, cap)
}

/** 一个能当作高度上限的数：正的、有限的、不是 `Dp.Infinity` / `Dp.Unspecified` */
private fun Dp.isUsableBound(): Boolean =
    this > 0.dp && this != Dp.Infinity && this != Dp.Unspecified

/** 一个能参与比较的数（0 是合法的：空列表的内容区就是 0 高） */
private fun Dp.isFiniteNumber(): Boolean =
    this >= 0.dp && this != Dp.Infinity && this != Dp.Unspecified

/** 列表里 N 行需要的自然高度 */
fun oxideDialogListContentHeight(itemCount: Int, rowHeight: Dp): Dp =
    rowHeight * itemCount.coerceAtLeast(0)

/**
 * 列表实际分配到的高度：N 行的高度与内容区上限取小
 *
 * 这就是"有界滚动"的策略本身——项目再多，内容区的高度也不会超过上限，
 * 多出来的部分在里面滚掉，而不是把面板撑高。
 *
 * 纯函数，可直接单测。
 */
fun oxideDialogListHeight(
    itemCount: Int,
    maxHeight: Dp,
    rowHeight: Dp,
): Dp = oxideDialogContentHeight(oxideDialogListContentHeight(itemCount, rowHeight), maxHeight)

/**
 * 这个列表会不会溢出、也就是需不需要挂滚动
 *
 * 定义成"分到的高度比自然高度小"，而不是直接比自然高度和上限：
 * 那样的话上限退化时也会被算成溢出。
 */
fun oxideDialogListOverflows(
    itemCount: Int,
    maxHeight: Dp,
    rowHeight: Dp,
): Boolean =
    oxideDialogListHeight(itemCount, maxHeight, rowHeight) <
        oxideDialogListContentHeight(itemCount, rowHeight)

/** 列表对话框里的一个选项 */
@Immutable
data class OxideDialogOption(
    /** 稳定且唯一；选中态与回调都按它走，因此不要拿会变的文案当 key */
    val key: String,
    val label: String,
    val detail: String? = null,
    /** 不可选的项必须在这里说清楚原因，文字不能只靠变暗表示 */
    val enabled: Boolean = true,
)

/**
 * 选项的第二行文案
 *
 * 不可选时用 [unavailableText] 顶掉原来的说明——把一行整体变暗等于什么都没说，
 * 读屏软件只会念出那个文件名，用户不知道点不动是为什么。
 *
 * 纯函数，可直接单测。
 */
fun oxideDialogOptionDetail(option: OxideDialogOption, unavailableText: String): String? =
    if (option.enabled) option.detail else unavailableText

/** 文本输入的校验结论 */
enum class OxideEntryVerdict {
    /** 可以确认 */
    Accept,

    /** 空着（或只有空白） */
    Blank,

    /** 超过长度上限 */
    TooLong,
}

/**
 * 默认校验规则：长度上限 + 是否允许空
 *
 * [maxLength] 为 0 表示不限长度。判断顺序是先长度后空值，因此一个既超长又空的
 * 输入报的是 [TooLong]——先让人把超长的删掉更有用。
 *
 * 纯函数，可直接单测。
 */
fun oxideEntryVerdict(
    text: String,
    maxLength: Int = 0,
    allowBlank: Boolean = false,
): OxideEntryVerdict = when {
    maxLength > 0 && text.length > maxLength -> OxideEntryVerdict.TooLong
    !allowBlank && text.isBlank() -> OxideEntryVerdict.Blank
    else -> OxideEntryVerdict.Accept
}

/** 确认按钮是否可点。规则更复杂的调用点自己给 [OxideTextEntryDialog.isValid] */
fun oxideEntryConfirmEnabled(
    text: String,
    maxLength: Int = 0,
    allowBlank: Boolean = false,
): Boolean = oxideEntryVerdict(text, maxLength, allowBlank) == OxideEntryVerdict.Accept

// ---------------------------------------------------------------------------
// 窗口尺寸
// ---------------------------------------------------------------------------

/**
 * 弹窗所在**窗口**的尺寸
 *
 * 读 [LocalWindowInfo] 而不是只用 [LocalConfiguration]：后者给的是整个 Activity
 * 的可用尺寸，而弹窗自己就是一个窗口；分屏、多窗口和输入法弹出时两者会不一致，
 * 而只有前者是这块面板真正的天花板。
 *
 * 两者取较大值：面板刚创建的那一帧容器可能还是 0×0（还没量过），
 * 与其让面板在那一帧塌掉，不如用一定量得到的整块窗口尺寸。
 * 最后仍然会被 [oxideDialogMaxSize] 夹住，所以面板不会因此超出窗口。
 */
@Composable
internal fun rememberOxideDialogWindow(): OxideDialogWindow {
    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    val measured = with(density) {
        OxideDialogWindow(
            widthDp = windowInfo.containerSize.width.toFloat().toDp().value.roundToInt(),
            heightDp = windowInfo.containerSize.height.toFloat().toDp().value.roundToInt(),
        )
    }
    val widthDp = max(measured.widthDp, configuration.screenWidthDp)
    val heightDp = max(measured.heightDp, configuration.screenHeightDp)
    return remember(widthDp, heightDp) { OxideDialogWindow(widthDp, heightDp) }
}

/** 当前窗口下面板允许的宽高上限 */
@Composable
internal fun rememberOxideDialogSize(metrics: OxideMetrics): OxideDialogSize {
    val window = rememberOxideDialogWindow()
    return remember(window, metrics.guiScale) {
        oxideDialogMaxSize(window.widthDp, window.heightDp, metrics.guiScale)
    }
}

// ---------------------------------------------------------------------------
// 遮罩与面板本体
// ---------------------------------------------------------------------------

/**
 * 遮罩 + 居中的内容
 *
 * 遮罩与面板是**兄弟**而不是父子：父子时点面板外的空白会落到面板自己身上，
 * 而兄弟关系下后面绘制的面板先被命中，空白自然落到遮罩上。
 *
 * [dismissByDialog] 为 false 时遮罩不可点、也不接受点击动画——
 * 任务进行中、上传中这类对话框不能让用户随手点掉。
 */
@Composable
internal fun OxideDialogScrimLayer(
    dismissByDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Oxide.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissByDialog,
                    onClick = onDismissRequest,
                )
        )
        content()
    }
}

/**
 * 一块 Oxide 对话框面板
 *
 * 弹窗家族里所有东西最终都落在这块面板上。它做的事只有四件：
 * 夹住宽高、画一块不透明的底、画标题栏和按钮栏、把剩下的高度交给内容。
 *
 * @param body 内容区。参数是内容区的高度上限——滚动必须用它夹住自己，
 *   而不是反过来（见 [OxideDialogScrimLayer] 上方关于 v1.5.0 P0 崩溃的说明）。
 * @param footer 按钮栏；null 表示没有按钮
 * @param onClose 关闭按钮；null 表示这一块没有关闭按钮
 */
@Composable
internal fun OxideDialogPanel(
    size: OxideDialogSize,
    metrics: OxideMetrics,
    title: String,
    closeDescription: String,
    onClose: (() -> Unit)?,
    body: @Composable ColumnScope.(contentMaxHeight: Dp) -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    val contentMaxHeight = remember(size, metrics.guiScale) {
        oxideDialogContentMaxHeight(size.maxHeight, metrics.guiScale)
    }
    val padH = metrics.secControlPadding
    val padV = metrics.secRowGap
    val closeSize = metrics.navItemHeight
        .coerceAtLeast(OxideDialogMinTouchTarget * metrics.guiScale)

    Column(
        modifier = Modifier
            .widthIn(max = size.maxWidth)
            .heightIn(max = size.maxHeight)
            .clip(Oxide.RadiusDrawer)
            // BgElevated 是**不透明**的：对话框底下压着的是游戏画面或壁纸，
            // 9% 的 SurfaceBase 在这里会直接看穿
            .background(Oxide.BgElevated)
            .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusDrawer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = padH + padV, end = padH, top = padV, bottom = padV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Oxide.Fg,
                style = Oxide.Type.DrawerTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClose != null) {
                Spacer(Modifier.width(padV))
                OxideIconButton(
                    onClick = onClose,
                    glyph = "✕",
                    size = closeSize,
                    modifier = Modifier.oxideIconDescription(closeDescription),
                )
            }
        }
        OxideSecDivider()

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                // 高度先夹住，内容再在里面滚。顺序反过来就是那个 P0 崩溃。
                .heightIn(max = contentMaxHeight)
                .padding(horizontal = padH + padV, vertical = padV),
        ) {
            body(contentMaxHeight)
        }

        if (footer != null) {
            OxideSecDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = padH + padV, vertical = padV),
                horizontalArrangement = Arrangement.spacedBy(padV, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 对话框家族
// ---------------------------------------------------------------------------

/**
 * 所有 Oxide 对话框的外壳
 *
 * 四个具体对话框都由它拼出来；已经有自己内容排版的旧对话框（更新说明、
 * 崩溃日志菜单）也直接用它，这样它们和新的那几张共享同一块面板。
 *
 * @param body 内容区，参数是内容区的高度上限
 * @param actions 按钮栏；null 表示没有按钮
 */
@Composable
fun OxideDialogShell(
    title: String,
    onDismissRequest: () -> Unit,
    body: @Composable ColumnScope.(contentMaxHeight: Dp) -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    dismissByDialog: Boolean = true,
    closeDescription: String = stringResource(R.string.oxide_dlg_close),
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    val size = rememberOxideDialogSize(metrics)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            // 自己控制宽度：平台默认宽度会把面板拉成 Material 那一套固定尺寸
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissByDialog,
            // 遮罩是我们自己画的（见 [OxideDialogScrimLayer]），点外面由它负责，
            // 所以这里必须关掉，否则平台会把"整个窗口"当成面板内部
            dismissOnClickOutside = false,
        ),
    ) {
        OxideDialogScrimLayer(
            dismissByDialog = dismissByDialog,
            onDismissRequest = onDismissRequest,
        ) {
            OxideDialogPanel(
                size = size,
                metrics = metrics,
                title = title,
                closeDescription = closeDescription,
                onClose = onDismissRequest,
                body = body,
                footer = actions,
            )
        }
    }
}

/**
 * 确认对话框
 *
 * [cancelText] 传空串表示这是一个只有确认的对话框（例如"知道了"）。
 * 确认与取消**都有文字**，因此不靠左右位置区分；主次由实心/描边两种语气承担。
 *
 * @param message 说明文字，可长；长了在内容区里滚，面板不会变高
 */
@Composable
fun OxideConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String = "",
    dismissByDialog: Boolean = true,
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    OxideDialogShell(
        title = title,
        onDismissRequest = onDismiss,
        dismissByDialog = dismissByDialog,
        metrics = metrics,
        body = { contentMaxHeight ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = message,
                    color = Oxide.FgMuted,
                    fontSize = Oxide.Type.Body.fontSize,
                    lineHeight = Oxide.Type.Body.lineHeight,
                )
            }
        },
        actions = {
            if (cancelText.isNotBlank()) {
                OxideButton(
                    text = cancelText,
                    onClick = onDismiss,
                    tone = OxideButtonTone.Secondary,
                )
            }
            OxideButton(
                text = confirmText,
                onClick = onConfirm,
                tone = OxideButtonTone.Primary,
            )
        },
    )
}

/**
 * 文本输入对话框
 *
 * 输入框复用 [OxideSecInput]，因此在这一套语言里它和页面上的输入框一模一样。
 * 校验不通过时确认按钮不可点，同时错误/提示写在输入框下面并带一个 ⚠，
 * **不只**靠描边变色表示。
 *
 * @param maxLength 0 表示不限长度
 * @param isValid 确认按钮是否可点。默认按 [maxLength] 与 [allowBlank] 判断；
 *   规则更复杂的调用点（例如"不能与已有配置重名"）自己给
 * @param errorText 校验不通过时的说明；不给就用一句通用的
 * @param supportText 正常状态下的提示（例如长度计数），有值就一直在
 */
@Composable
fun OxideTextEntryDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    supportText: String? = null,
    errorText: String? = null,
    maxLength: Int = 0,
    allowBlank: Boolean = false,
    isValid: (String) -> Boolean = { oxideEntryConfirmEnabled(it, maxLength, allowBlank) },
    singleLine: Boolean = true,
    numeric: Boolean = false,
    password: Boolean = false,
    dismissByDialog: Boolean = true,
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    val valid = isValid(value)
    val failed = !valid
    // 校验没过就必须有一句说明：否则"描边变红 + 确认按钮灰着"是纯颜色信息，
    // 读屏软件只会念出一个空的输入框
    val note = when {
        !failed -> supportText
        !errorText.isNullOrBlank() -> errorText
        else -> stringResource(R.string.oxide_dlg_entry_invalid)
    }

    OxideDialogShell(
        title = title,
        onDismissRequest = onDismiss,
        dismissByDialog = dismissByDialog,
        metrics = metrics,
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                OxideSecInput(
                    metrics = metrics,
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "",
                    label = label,
                    password = password,
                    numeric = numeric,
                    singleLine = singleLine,
                    isError = failed,
                    imeAction = ImeAction.Done,
                    onDone = { if (valid) onConfirm() },
                )
                if (!note.isNullOrBlank()) {
                    Text(
                        text = if (failed) "⚠ $note" else note,
                        color = if (failed) Oxide.FgMuted else Oxide.FgFaint,
                        fontSize = Oxide.Type.MicroLabel.fontSize,
                        lineHeight = Oxide.Type.MicroLabel.lineHeight,
                    )
                }
            }
        },
        actions = {
            if (cancelText.isNotBlank()) {
                OxideButton(
                    text = cancelText,
                    onClick = onDismiss,
                    tone = OxideButtonTone.Secondary,
                )
            }
            OxideButton(
                text = confirmText,
                onClick = onConfirm,
                tone = OxideButtonTone.Primary,
                enabled = valid,
            )
        },
    )
}

/**
 * 列表对话框
 *
 * 两种用法：
 *
 * - **选择**（默认，[selectable] = true）：[confirmText] 给一个"确认"文案，
 *   点一行只改选中态，再点确认才回调；也可以不给 [confirmText]，那就是点哪行选哪行。
 * - **动作菜单**（[selectable] = false）：每行是一个动作，点一下立刻回调。
 *   此时不画单选标记，行也按按钮而不是单选项暴露给无障碍服务。
 *
 * 列表用 [LazyColumn] 并且高度被上限夹住，因此项目数量再多也不会把面板撑高；
 * 上限由 [oxideDialogListHeight] 定，与这段组合里的 `heightIn` 是同一个数。
 */
@Composable
fun OxideListDialog(
    title: String,
    options: List<OxideDialogOption>,
    onOptionSelected: (key: String) -> Unit,
    onDismiss: () -> Unit,
    currentKey: String? = null,
    confirmText: String? = null,
    emptyText: String? = null,
    unavailableText: String = stringResource(R.string.oxide_dlg_option_unavailable),
    selectable: Boolean = true,
    dismissByDialog: Boolean = true,
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    // 有确认按钮时才需要本地选中态；点哪行选哪行时选中即刻生效
    val needsLocalSelection = selectable && confirmText != null
    var selected by remember(options, currentKey, needsLocalSelection) {
        mutableStateOf(if (needsLocalSelection) currentKey else null)
    }

    // 类型写在这里而不是靠 `if` 的期望类型推断：lambda 分支加 `null` 分支
    // 在推断时容易退化成 `Any`
    val actions: (@Composable RowScope.() -> Unit)? = if (confirmText != null) {
        {
            if (needsLocalSelection) {
                OxideButton(
                    text = stringResource(R.string.generic_cancel),
                    onClick = onDismiss,
                    tone = OxideButtonTone.Secondary,
                )
            }
            OxideButton(
                text = confirmText,
                onClick = {
                    val key = selected
                    if (key != null) {
                        onOptionSelected(key)
                    }
                    onDismiss()
                },
                tone = OxideButtonTone.Primary,
                enabled = !needsLocalSelection || selected != null,
            )
        }
    } else {
        null
    }

    OxideDialogShell(
        title = title,
        onDismissRequest = onDismiss,
        dismissByDialog = dismissByDialog,
        metrics = metrics,
        body = { contentMaxHeight ->
            if (options.isEmpty()) {
                if (!emptyText.isNullOrBlank()) {
                    Text(
                        text = emptyText,
                        color = Oxide.FgFaint,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )
                }
            } else {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    // 先夹住高度，再让列表在里面滚
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth(),
                state = listState,
            ) {
                items(options, key = { it.key }) { option ->
                    OxideDialogOptionRow(
                        option = option,
                        selected = option.key == selected,
                        selectable = selectable,
                        metrics = metrics,
                        unavailableText = unavailableText,
                        onClick = {
                            if (!needsLocalSelection) {
                                onOptionSelected(option.key)
                                onDismiss()
                            } else {
                                selected = option.key
                            }
                        },
                    )
                }
            }
            }
        },
        actions = actions,
    )
}

/**
 * 列表里的一行
 *
 * 选中态由左侧那个方块里的实心块**加上**行尾的 ✓ 承担，读屏软件同时能拿到
 * `Role.RadioButton` 与 selected，因此不依赖颜色。不可选的行整体压暗，并且
 * 第二行换成了 [unavailableText]，说清楚为什么点不动。
 */
@Composable
private fun OxideDialogOptionRow(
    option: OxideDialogOption,
    selected: Boolean,
    selectable: Boolean,
    metrics: OxideMetrics,
    unavailableText: String,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = metrics.dialogOptionRowHeight)
        .alpha(if (option.enabled) 1f else DisabledAlpha)
        .clip(Oxide.RadiusControl)
        .then(
            if (!option.enabled) {
                Modifier
            } else if (selectable) {
                Modifier.selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            }
        )
        .padding(horizontal = metrics.secControlPadding, vertical = metrics.secRowGap)

    if (!selectable) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxideDialogOptionText(
                label = option.label,
                detail = oxideDialogOptionDetail(option, unavailableText),
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.dialogMarkSize)
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
                        .size(metrics.dialogMarkDotSize)
                        .clip(Oxide.RadiusBadge)
                        .background(Oxide.FgMuted)
                )
            }
        }
        Spacer(Modifier.width(metrics.secControlPadding))
        OxideDialogOptionText(
            label = option.label,
            detail = oxideDialogOptionDetail(option, unavailableText),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(metrics.secControlPadding))
            Text(
                text = "✓",
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
                maxLines = 1,
            )
        }
    }
}

/** 列表行里的两行文字：主标题 + 说明 */
@Composable
private fun OxideDialogOptionText(
    label: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Oxide.Fg,
            fontSize = Oxide.Type.Body.fontSize,
            lineHeight = Oxide.Type.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                color = Oxide.FgFaint,
                fontSize = Oxide.Type.MicroLabel.fontSize,
                lineHeight = Oxide.Type.MicroLabel.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // detail 为空时这里不留一个空行，行高仍然由 heightIn 保证
    }
}

/**
 * 进度 / 任务对话框
 *
 * [progress] 为 null（或负数）表示**进度不可知**，这时只画一条空槽并说明正在
 * 处理——刻意不做循环动画：无尽动画在这里既不提供新信息，又会让弹窗永远停不下来。
 *
 * @param onCancel 给了才画取消按钮；任务不可中断时不传
 */
@Composable
fun OxideTaskDialog(
    title: String,
    message: String? = null,
    progress: Float? = null,
    onCancel: (() -> Unit)? = null,
    cancelText: String = stringResource(R.string.generic_cancel),
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    // 与任务抽屉同一套约定：负进度 = 不知道，别显示成 0%
    val percent = progress?.let { oxideTaskProgressPercent(it) }

    // 与 [OxideDialogShell] 同理：显式写出类型，别让 if 的两个分支去推断
    val actions: (@Composable RowScope.() -> Unit)? = if (onCancel != null) {
        {
            OxideButton(
                text = cancelText,
                onClick = onCancel,
                tone = OxideButtonTone.Secondary,
            )
        }
    } else {
        null
    }

    OxideDialogShell(
        title = title,
        // 任务进行中不允许随手点掉，和旧实现一致
        onDismissRequest = {},
        dismissByDialog = false,
        metrics = metrics,
        body = { contentMaxHeight ->
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(metrics.secRowGap),
            ) {
                if (!message.isNullOrBlank()) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = message,
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                        modifier = Modifier
                            .heightIn(max = contentMaxHeight)
                            .verticalScroll(scrollState),
                    )
                }
                OxideProgressBar(progress = percent?.let { it / 100f } ?: 0f)
                Text(
                    text = percent?.let { "$it%" }
                        ?: stringResource(R.string.oxide_dlg_working),
                    color = Oxide.FgFaint,
                    fontSize = Oxide.Type.MicroLabel.fontSize,
                    lineHeight = Oxide.Type.MicroLabel.lineHeight,
                )
            }
        },
        actions = actions,
    )
}

// ---------------------------------------------------------------------------
// 给"没有组合可挂"的调用方
// ---------------------------------------------------------------------------

/**
 * 在一个自己的窗口里展示 Oxide 面板
 *
 * 只有 [dev.oxide.launcher.viewmodel.ErrorViewModel] 需要它：错误是从 Activity 的
 * 协程里报出来的，那一刻没有一块可以承载组合的界面，Material 的
 * `MaterialAlertDialogBuilder` 是当时唯一能弹东西的办法。面板本体与弹窗里那一份
 * 是同一个 [OxideDialogPanel]，因此颜色、圆角、按钮与读屏行为完全一致，
 * 不同的只是宿主窗口不是 Compose 的 `Dialog`。
 *
 * @param onDismiss 窗口关掉之后回调一次
 */
fun showOxideMessageDialog(
    context: Context,
    title: String,
    message: String,
    confirmText: String = context.getString(R.string.generic_confirm),
    onDismiss: () -> Unit = {},
) {
    if (context !is Activity) {
        // 没有窗口可以承载它。写进日志而不是静默返回：调用方只有
        // MainActivity 与 VMActivity 两处，都传的是 Activity
        Logger.error(TAG, "Cannot present an Oxide dialog without an Activity window")
        return
    }

    val host = AndroidDialog(context)
    host.requestWindowFeature(Window.FEATURE_NO_TITLE)
    host.setCancelable(false)
    host.setCanceledOnTouchOutside(false)
    host.setOnDismissListener { onDismiss() }

    host.setContentView(
        ComposeView(context).apply {
            // 对话框的窗口没有 ViewTreeLifecycleOwner，
            // 所以用"脱离窗口即销毁"这一条而不是 DisposeOnViewTreeLifecycleDestroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ProvideOxideChrome {
                    OxideHostedMessagePanel(
                        title = title,
                        message = message,
                        confirmText = confirmText,
                        closeDescription = context.getString(R.string.oxide_dlg_close),
                        onClose = { host.dismiss() },
                    )
                }
            }
        }
    )

    host.window?.apply {
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        // 与 Compose 的 Dialog(usePlatformDefaultWidth = false) 一样占满窗口，
        // 遮罩因此能盖住整块屏幕
        setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    host.show()
}

/**
 * [showOxideMessageDialog] 里那块面板
 *
 * 单独拆出来只为了一件事：[rememberOxideMetrics] 必须在一个组合里算一次，
 * 而 `setContent` 的内容不能提到外面去。
 */
@Composable
private fun OxideHostedMessagePanel(
    title: String,
    message: String,
    confirmText: String,
    closeDescription: String,
    onClose: () -> Unit,
) {
    val metrics = rememberOxideMetrics()
    OxideDialogScrimLayer(
        dismissByDialog = false,
        onDismissRequest = onClose,
    ) {
        OxideDialogPanel(
            size = rememberOxideDialogSize(metrics),
            metrics = metrics,
            title = title,
            closeDescription = closeDescription,
            onClose = onClose,
            body = { contentMaxHeight ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = contentMaxHeight)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = message,
                        color = Oxide.FgMuted,
                        fontSize = Oxide.Type.Body.fontSize,
                        lineHeight = Oxide.Type.Body.lineHeight,
                    )
                }
            },
            footer = {
                OxideButton(
                    text = confirmText,
                    onClick = onClose,
                    tone = OxideButtonTone.Primary,
                )
            },
        )
    }
}