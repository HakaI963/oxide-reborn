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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oxide.layercontroller.data.ButtonSize
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.screens.main.oxide.OxideGuiScaleDefaultPercent
import dev.oxide.launcher.ui.screens.main.oxide.oxideGuiScaleFactor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 控制布局编辑器的纯逻辑
 *
 * 这个文件里没有 Composable，也不读任何设置、不碰任何 Android API：编辑器的版面
 * 几何、控件网格的排布、拖动定位的换算、数字解析与可见性判断都是纯函数，
 * 因此可以在不启动 Compose 的情况下逐条钉死
 * （见 `src/test/java/dev/oxide/launcher/ui/screens/main/control_editor/`）。
 *
 * 为什么必须单独抽出来：编辑器的停靠面板是**压在控制布局画布上**的，画布又是
 * 正在接收游戏输入的活表面。面板一旦算错，控件就会被面板盖住；几何算错则会让
 * 拖动手柄与画布上的控件对不上。把这套几何写成纯函数之后，它既能单测，也能保证
 * 面板在 640x360 上也不会把内容挤到屏幕外面。
 */

// ---------------------------------------------------------------------------
// 控件种类
// ---------------------------------------------------------------------------

/** 编辑器里能选中的三种控件 */
enum class EditorWidgetKind {
    /** 普通按键 */
    Button,

    /** 文本框 */
    Text,

    /** 摇杆 */
    Joystick,
}

/**
 * 这个种类可选的尺寸类型
 *
 * 摇杆只有一个正方形的边长，没有"跟随内容"，所以那一项必须从它的候选里去掉——
 * 让用户选到一个 `JoystickData` 明确禁止的类型，只会在保存时炸掉。
 */
fun editorSizeTypesFor(kind: EditorWidgetKind): List<ButtonSize.Type> = when (kind) {
    EditorWidgetKind.Joystick -> listOf(ButtonSize.Type.Dp, ButtonSize.Type.Percentage)
    EditorWidgetKind.Button, EditorWidgetKind.Text -> ButtonSize.Type.entries.toList()
}

/** 包裹内容时宽高由内容决定，因此没有可编辑的宽高 */
fun editorShowsSizeFields(type: ButtonSize.Type): Boolean = type != ButtonSize.Type.WrapContent

/** 绝对值尺寸下才需要宽高两个 dp 输入 */
fun editorShowsAbsoluteSize(type: ButtonSize.Type): Boolean = type == ButtonSize.Type.Dp

/** 百分比尺寸下才需要宽高两个百分比输入 */
fun editorShowsPercentSize(type: ButtonSize.Type): Boolean = type == ButtonSize.Type.Percentage

/** 只有百分比尺寸才有"参考屏幕宽还是高"可选 */
fun editorShowsSizeReference(type: ButtonSize.Type): Boolean = type == ButtonSize.Type.Percentage

/** 摇杆只有一个边长，按键与文本框各有宽高 */
fun editorShowsSeparateWidthHeight(kind: EditorWidgetKind): Boolean =
    kind != EditorWidgetKind.Joystick

// ---------------------------------------------------------------------------
// 能不能做
// ---------------------------------------------------------------------------

/** 新建控件被挡住的四种原因 */
enum class EditorAddBlocker {
    /** 可以新建 */
    None,

    /** 一个控制层都还没有 */
    NoLayers,

    /** 有控制层但没选中目标 */
    NoSelectedLayer,

    /** 预览模式下画布只读 */
    Preview,
}

/**
 * 能不能往当前控制层里新建控件
 *
 * 这里必须和 `EditorViewModel.addWidget` 的三种警告一一对应，否则面板上的
 * "新建"按钮要么在该报警的地方不报警，要么在该禁用的时候还能点。
 */
fun editorAddBlocker(
    layerCount: Int,
    hasSelectedLayer: Boolean,
    isPreviewMode: Boolean,
): EditorAddBlocker = when {
    isPreviewMode -> EditorAddBlocker.Preview
    layerCount <= 0 -> EditorAddBlocker.NoLayers
    !hasSelectedLayer -> EditorAddBlocker.NoSelectedLayer
    else -> EditorAddBlocker.None
}

fun editorAllowsAddingControls(blocker: EditorAddBlocker): Boolean = blocker == EditorAddBlocker.None

/** 预览模式下控制层不能改名、不能换序、不能删 */
fun editorAllowsLayerEditing(isPreviewMode: Boolean): Boolean = !isPreviewMode

/** 没有选中控件时没有位置与尺寸可改 */
fun editorShowsGeometrySection(kind: EditorWidgetKind?): Boolean = kind != null

/** 预览模式下位置与尺寸被锁住——画布只读，改了也看不见 */
fun editorAllowsGeometryEditing(
    isPreviewMode: Boolean,
    blocker: EditorAddBlocker,
): Boolean = !isPreviewMode && blocker == EditorAddBlocker.None

// ---------------------------------------------------------------------------
// 位置：存储值与比例
// ---------------------------------------------------------------------------

/**
 * 位置与尺寸的存储上限
 *
 * 与 `ButtonPosition` 的 `0..10000` 一致。编辑器内部一律用这个刻度做算术，
 * 只在显示和输入的时候才换算成百分比或 dp。
 */
const val EditorStoredMax: Int = 10000

/** 归一化比例（0..1）→ 存储刻度（0..[EditorStoredMax]），越界夹回 */
fun editorStoredFromFraction(fraction: Float): Int {
    if (fraction.isNaN()) return 0
    return (fraction * EditorStoredMax).roundToInt().coerceIn(0, EditorStoredMax)
}

/** 存储刻度 → 归一化比例（0..1），越界夹回 */
fun editorFractionFromStored(stored: Int): Float =
    (stored.toFloat() / EditorStoredMax).coerceIn(0f, 1f)

/** 一对存储位置 */
@Immutable
data class EditorStoredPosition(val x: Int, val y: Int)

/** 一对归一化比例 */
@Immutable
data class EditorPadPoint(val x: Float, val y: Float)

// ---------------------------------------------------------------------------
// 拖动定位板
// ---------------------------------------------------------------------------

/**
 * 定位板上的一个点，纯像素
 *
 * 定位板把整块屏幕缩成一个矩形，旋钮在板内的位置就是控件中心在屏幕上的比例。
 * 把坐标留在板内（而不是 0..1 的比例）是为了让板极小的时候旋钮也不会画出边界。
 */
fun editorPadKnob(
    fractionX: Float,
    fractionY: Float,
    width: Float,
    height: Float,
    knobRadius: Float,
): EditorPadPoint {
    // 让整个旋钮留在板内，也就是把中心夹在 [radius, 边长 - radius] 之间。
    // 板比旋钮还窄时这两个界会反过来，交给 coerceIn 会抛异常，
    // 因此这里先把上界压到不小于下界——退化成一条缝的板也只是把旋钮摆在中间。
    val radius = knobRadius.coerceAtLeast(0f)
    val maxX = (width - radius).coerceAtLeast(0f)
    val minX = radius.coerceAtMost(maxX)
    val maxY = (height - radius).coerceAtLeast(0f)
    val minY = radius.coerceAtMost(maxY)
    return EditorPadPoint(
        x = (fractionX.coerceIn(0f, 1f) * width).coerceIn(minX, maxX),
        y = (fractionY.coerceIn(0f, 1f) * height).coerceIn(minY, maxY),
    )
}

/**
 * 板内像素 → 存储位置
 *
 * 定位板**不**把旋钮半径算进去：手指按在旋钮中心时应当得到它按下的那个比例，
 * 而不是被旋钮半径推偏一点。边界由 [editorPadKnob] 在绘制那一侧负责。
 */
fun editorPositionFromPadPoint(
    px: Float,
    py: Float,
    width: Float,
    height: Float,
): EditorStoredPosition {
    val fx = if (width > 0f) (px / width).coerceIn(0f, 1f) else 0f
    val fy = if (height > 0f) (py / height).coerceIn(0f, 1f) else 0f
    return EditorStoredPosition(
        x = editorStoredFromFraction(fx),
        y = editorStoredFromFraction(fy),
    )
}

/**
 * 步进：把一个存储值沿 [direction] 移动 [step]，并夹在 [min]..[max]
 *
 * 触屏上没有"精确拖到 49.83%"这回事，因此每个坐标除了拖动与直接输入，
 * 还要有一对 ± 的步进按钮。方向只有 -1 / 0 / +1 三种，别的值一律当作不动。
 */
fun editorNudge(
    stored: Int,
    step: Int,
    direction: Int,
    min: Int = 0,
    max: Int = EditorStoredMax,
): Int {
    val low = min.coerceAtMost(max)
    val high = max.coerceAtLeast(min)
    val amount = when {
        direction < 0 -> -abs(step)
        direction > 0 -> abs(step)
        else -> 0
    }
    return (stored + amount).coerceIn(low, high)
}

// ---------------------------------------------------------------------------
// 九宫格对齐
// ---------------------------------------------------------------------------

/**
 * 九宫格上的九个对齐点
 *
 * 拖动与直接输入都不适合"把控件摆到正中"这种需求，因此位置这一块还给出九个
 * 一次到位的按钮。列与行的下标就是它自己在 3x3 里的位置，因此新增一处只需要
 * 多写一个枚举值，格子布局由 [editorAnchorPosition] 推出来。
 */
enum class EditorAnchor(val column: Int, val row: Int) {
    TopStart(0, 0),
    TopCenter(1, 0),
    TopEnd(2, 0),
    CenterStart(0, 1),
    Center(1, 1),
    CenterEnd(2, 1),
    BottomStart(0, 2),
    BottomCenter(1, 2),
    BottomEnd(2, 2),
    ;

    companion object {
        /** 行数，布局用 */
        const val RowCount: Int = 3

        /** 列数，布局用 */
        const val ColumnCount: Int = 3
    }
}

/** 落点按三等分算，因此"居中"就是 5000，四角就是 0 与 10000 */
private fun editorAnchorAxis(index: Int): Int =
    (EditorStoredMax.toFloat() * index.coerceIn(0, 2) / 2f).roundToInt()

fun editorAnchorPosition(anchor: EditorAnchor): EditorStoredPosition =
    EditorStoredPosition(
        x = editorAnchorAxis(anchor.column),
        y = editorAnchorAxis(anchor.row),
    )

// ---------------------------------------------------------------------------
// 控件网格
// ---------------------------------------------------------------------------

/**
 * 网格的列数
 *
 * 用"内容宽度 / 单元格最小宽度"而不是写死几列，于是窗口被切分、折叠屏展开、
 * 或者用户把界面放大到 150% 时列数都会自己退让，而不是把单元格裁掉半截。
 * [contentWidth] 必须已经扣掉停靠面板自己的内边距。
 */
fun controlEditorGridColumns(contentWidth: Dp, metrics: ControlEditorMetrics): Int {
    if (contentWidth <= 0.dp) return metrics.controlColumnsMin
    val fit = ((contentWidth + metrics.cellGap) / (metrics.controlCellMinWidth + metrics.cellGap)).toInt()
    return fit.coerceIn(metrics.controlColumnsMin, metrics.controlColumnsMax)
}

/** 网格有几行 */
fun controlEditorGridRowCount(itemCount: Int, columns: Int): Int {
    val safeColumns = columns.coerceAtLeast(1)
    return ((itemCount.coerceAtLeast(0) + safeColumns - 1) / safeColumns)
}

/**
 * 把控件下标切成一行行的区间
 *
 * 网格**不自己滚动**：外层列表已经在滚，再嵌一个同方向的滚动容器会在很窄的
 * 停靠面板里打架，也没法保证控件数量多时仍然只有一次布局。因此这里只把下标
 * 分组成行，由外层列表一行一项地铺出去。
 *
 * [EditorAnchor.entries] 的声明顺序是逐行的（先上后中再下），因此枚举本身就是
 * 九宫格的铺开顺序，界面上不必再排一次。
 */
fun controlEditorGridRowRanges(itemCount: Int, columns: Int): List<IntRange> {
    val safeColumns = columns.coerceAtLeast(1)
    val count = itemCount.coerceAtLeast(0)
    val rows = controlEditorGridRowCount(count, safeColumns)
    return List(rows) { row ->
        val start = row * safeColumns
        (start until minOf(start + safeColumns, count))
    }
}

// ---------------------------------------------------------------------------
// 数字
// ---------------------------------------------------------------------------

/** 行内数字输入框可能给出的三种错误 */
enum class EditorNumberError { NotANumber, TooSmall, TooLarge }

/** 显示最多几位小数，再多就超出了滑杆本身的分辨率 */
const val EditorMaxDecimals: Int = 3

/**
 * 从十进制格式串里取出小数位数
 *
 * 旧的 `InfoLayoutSliderItem` 收的是 `"#0.00"` / `"#0"` 这种格式串，
 * 把它换成"几位小数"而不是改掉所有调用点，外部调用方（鼠标热区编辑、账号页）
 * 一行都不用动，行为也完全一致。
 */
fun decimalsFromPattern(pattern: String): Int {
    val dot = pattern.indexOf('.')
    if (dot < 0) return 0
    var count = 0
    for (index in dot + 1 until pattern.length) {
        if (pattern[index] != '0') break
        count++
    }
    return count.coerceIn(0, EditorMaxDecimals)
}

/**
 * 解析行内数字输入的文本
 *
 * [integerOnly] 为真时只接受整数（dp 尺寸、存储刻度都是整数）；
 * 返回 null 表示文本为空、不是数字或越界，此时**不允许提交**——
 * 非法输入留在原地等人改，而不是悄悄夹到边界上。
 */
fun editorNumberIn(
    text: String,
    range: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
): Float? {
    val parsed = (if (integerOnly) text.toIntOrNull()?.toFloat() else text.toFloatOrNull())
        ?: return null
    if (parsed.isNaN()) return null
    if (parsed < range.start) return null
    if (parsed > range.endInclusive) return null
    return parsed
}

/** [editorNumberIn] 的错误分类，供行内输入框显示提示 */
fun editorNumberError(
    text: String,
    range: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
): EditorNumberError? {
    val parsed = (if (integerOnly) text.toIntOrNull()?.toFloat() else text.toFloatOrNull())
        ?: return EditorNumberError.NotANumber
    if (parsed.isNaN()) return EditorNumberError.NotANumber
    if (parsed < range.start) return EditorNumberError.TooSmall
    if (parsed > range.endInclusive) return EditorNumberError.TooLarge
    return null
}

/** 归一化比例：值在区间里的位置，区间退化成一点时返回 0 */
fun editorSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/** 反算：归一化比例回到值，比例被夹在 0..1 */
fun editorSliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return range.start + fraction.coerceIn(0f, 1f) * span
}

/**
 * 把滑杆或输入框里的值拼成要显示的文本
 *
 * 自己按十进制逐位拼，不走 [java.text.DecimalFormat] 也不走 [String.format]：
 * 后两者每次都要构造格式化器，而拖动滑杆时这个函数每帧都会被调到。
 */
fun formatEditorValue(value: Float, suffix: String?, decimals: Int = 0): String {
    if (decimals <= 0) {
        return value.roundToInt().toString() + suffix.orEmpty()
    }
    return formatEditorFixed(value, decimals) + suffix.orEmpty()
}

/** 小数部分逐位拼出来的定点文本，负数带负号 */
private fun formatEditorFixed(value: Float, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / factor
    val fraction = (magnitude % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}

// ---------------------------------------------------------------------------
// 版面几何
// ---------------------------------------------------------------------------

/** 停靠面板宽度占窗口宽度的比例 */
const val EditorDockWidthFraction: Float = 0.46f

/** 停靠面板宽度的夹紧区间（未乘界面缩放，单位 dp） */
const val EditorDockMinWidth: Float = 210f
const val EditorDockMaxWidth: Float = 380f

/** 停靠面板与窗口四边的距离占最短边的比例 */
const val EditorDockMarginFraction: Float = 0.035f

/** 停靠面板留白的夹紧区间（未乘界面缩放，单位 dp） */
const val EditorDockMarginMin: Float = 4f
const val EditorDockMarginMax: Float = 12f

/** 行高占窗口高度的比例，让密度跟着屏幕走而不是写死 */
const val EditorRowHeightFraction: Float = 0.078f

/** 行高与定位板高度的夹紧区间（未乘界面缩放，单位 dp） */
const val EditorRowHeightMin: Float = 26f
const val EditorRowHeightMax: Float = 34f

/** 定位板高度占窗口高度的比例 */
const val EditorPadHeightFraction: Float = 0.3f

/** 定位板高度的夹紧区间（未乘界面缩放，单位 dp） */
const val EditorPadHeightMin: Float = 84f
const val EditorPadHeightMax: Float = 150f

/** 控件格子的最小宽度，除它之外还要减去单元格间距 */
const val EditorControlCellMinWidth: Float = 86f

/** 控件网格最多给几列 */
const val EditorControlColumnsMax: Int = 3

/**
 * 一组已经算好的编辑器尺寸
 *
 * 全部由**可用宽高**推导，停靠面板与定位板里不写死任何一个与窗口有关的 dp。
 * 乘过界面缩放的是与窗口无关的那一半（行高、轨道、旋钮），面板本身则始终被夹在
 * 窗口以内——放大到 150% 时面板会贴住边，而不是溢出屏幕。
 */
@Immutable
data class ControlEditorMetrics(
    /** 面板与窗口四边的距离 */
    val dockMargin: Dp,
    val dockWidth: Dp,
    val dockHeight: Dp,
    val dockPadding: Dp,
    val headerHeight: Dp,
    val footerHeight: Dp,
    val rowHeight: Dp,
    val rowGap: Dp,
    val groupLabelHeight: Dp,
    val cellGap: Dp,
    val controlCellMinWidth: Dp,
    val controlCellHeight: Dp,
    val controlColumnsMin: Int,
    val controlColumnsMax: Int,
    /** 画布上那排快捷操作的高度 */
    val actionStripHeight: Dp,
    /** 定位板高度，横竖屏各自按比例算 */
    val padHeight: Dp,
    /** 行内数字输入框的高度 */
    val fieldHeight: Dp,
    /** 输入框后面的确认/取消小方块 */
    val miniButtonSize: Dp,
    /** 滑杆旋钮半径 */
    val trackKnobRadius: Dp,
    /** 滑杆轨道粗细 */
    val trackHeight: Dp,
    /** 悬浮球直径 */
    val ballSize: Dp,
    val guiScale: Float,
) {
    /** 面板里的内容宽度，网格的列数由它算出来 */
    val contentWidth: Dp get() = dockWidth - dockPadding * 2

    /** 面板里一块内容（网格、输入行）能占的最大宽度 */
    val panelWidth: Dp get() = dockWidth
}

/**
 * 由可用尺寸算出编辑器尺寸
 *
 * 纯函数：不读 [android.content.Context]，也不碰组合期状态，因此每一档都能在
 * 单元测试里逐条钉死。
 *
 * [guiScalePercent] 是用户的界面缩放（`AllSettings.launcherGuiScale`），
 * 与启动器其余界面共用同一个系数（见 `oxideGuiScaleFactor`），
 * 于是框和字永远是同一个比例。
 */
fun controlEditorMetricsFor(
    widthDp: Int,
    heightDp: Int,
    guiScalePercent: Int = OxideGuiScaleDefaultPercent,
): ControlEditorMetrics {
    val scale = oxideGuiScaleFactor(guiScalePercent)
    val safeWidth = widthDp.coerceAtLeast(1).toFloat()
    val safeHeight = heightDp.coerceAtLeast(1).toFloat()

    // 先按 100% 的尺寸算出几何，再统一乘系数：系数只在一个地方生效，
    // 新加尺寸的人不会漏乘，而 100% 时乘 1f 是精确恒等。
    fun Dp.scaled(): Dp = this * scale

    // 留白跟着窗口的最短边收缩：很小的窗口里写死 12dp 会把内容挤没
    val dockMargin =
        (minOf(safeWidth, safeHeight) * EditorDockMarginFraction)
            .coerceIn(EditorDockMarginMin, EditorDockMarginMax).dp.scaled()

    // 宽度先按比例给，再夹进夹紧区间，最后无论如何都不超过窗口
    val availableWidth = (safeWidth.dp - dockMargin * 2f).coerceAtLeast(1.dp)
    val dockWidth = (
        (safeWidth * EditorDockWidthFraction)
            .coerceIn(EditorDockMinWidth, EditorDockMaxWidth).dp.scaled()
        ).coerceAtMost(availableWidth)
    val dockHeight = (safeHeight.dp - dockMargin * 2f).coerceAtLeast(1.dp)

    // 高度特别矮时不能出现"下限比上限还大"的区间，那会让 coerceIn 直接抛异常
    val rowHeight = (
        (safeHeight * EditorRowHeightFraction)
            .coerceIn(EditorRowHeightMin, EditorRowHeightMax).dp.scaled()
        ).coerceAtMost(dockHeight)

    // 定位板最多占掉面板里两行之外的全部。极小窗口上这两行就吃光了，
    // 于是差值会是负的——那一档下定位板塌成最小的一块，而不是给出负的高度
    val padBudget = (dockHeight - rowHeight * 2f).coerceAtLeast(1.dp)
    val padHeight = (
        (safeHeight * EditorPadHeightFraction)
            .coerceIn(EditorPadHeightMin, EditorPadHeightMax).dp.scaled()
        ).coerceIn(1.dp, padBudget)

    return ControlEditorMetrics(
        dockMargin = dockMargin,
        dockWidth = dockWidth,
        dockHeight = dockHeight,
        dockPadding = 7.dp.scaled(),
        headerHeight = rowHeight,
        footerHeight = rowHeight,
        rowHeight = rowHeight,
        rowGap = 4.dp.scaled(),
        groupLabelHeight = 13.dp.scaled(),
        cellGap = 4.dp.scaled(),
        controlCellMinWidth = EditorControlCellMinWidth.dp.scaled(),
        controlCellHeight = (rowHeight + 8.dp).coerceAtMost(dockHeight / 3f),
        controlColumnsMin = 1,
        controlColumnsMax = EditorControlColumnsMax,
        actionStripHeight = rowHeight,
        padHeight = padHeight,
        // 输入框比行高矮一点正好放一行字，再高就与行不齐了
        fieldHeight = (rowHeight - 6.dp).coerceAtLeast(20.dp.scaled()),
        miniButtonSize = (rowHeight - 6.dp).coerceAtLeast(20.dp.scaled()),
        trackKnobRadius = 5.dp.scaled(),
        trackHeight = 3.dp.scaled(),
        ballSize = 26.dp.scaled(),
        guiScale = scale,
    )
}

/**
 * 由当前可用尺寸算出编辑器尺寸
 *
 * 读 [LocalConfiguration] 而不是屏幕物理尺寸：分屏、多窗口和折叠屏展开时
 * 可用区域会小于整块屏幕，用物理尺寸会导致内容被裁掉。
 *
 * 界面缩放读的是 `AllSettings.launcherGuiScale.state`：在组合期读它就是订阅它，
 * 所以设置一改尺寸立刻重算，不需要重启。
 */
@Composable
fun rememberEditorMetrics(): ControlEditorMetrics {
    val configuration = LocalConfiguration.current
    val guiScalePercent = AllSettings.launcherGuiScale.state
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, guiScalePercent) {
        controlEditorMetricsFor(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            guiScalePercent = guiScalePercent,
        )
    }
}