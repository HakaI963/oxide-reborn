/*
 * Zalith Launcher 2
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.activities.CrashType
import dev.oxide.launcher.ui.screens.main.oxide.OxideButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideButtonTone
import dev.oxide.launcher.ui.screens.main.oxide.OxideIconButton
import dev.oxide.launcher.ui.screens.main.oxide.OxideMetrics
import dev.oxide.launcher.ui.screens.main.oxide.OxidePanelBounds
import dev.oxide.launcher.ui.screens.main.oxide.OxidePanelShell
import dev.oxide.launcher.ui.screens.main.oxide.OxideSecDivider
import dev.oxide.launcher.ui.screens.main.oxide.oxideIconDescription
import dev.oxide.launcher.ui.screens.main.oxide.oxidePanelBoundsFor
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import dev.oxide.launcher.ui.theme.Oxide
import java.util.IdentityHashMap

/**
 * 旋转按钮上的那个符号
 *
 * 放在代码里而不是从资源里画：这一枚按钮只出现在崩溃页，为它单开一枚 drawable
 * 并不比一个转圈箭头更清楚。
 */
private const val ROTATE_GLYPH = "\u27F3"

// ---------------------------------------------------------------------------
// 崩溃报告模型（纯数据 + 纯函数：不碰 Compose，也不碰 Android）
// ---------------------------------------------------------------------------

/**
 * 崩溃页要显示的全部内容
 *
 * 之前这一页只是把 `ErrorMessage.message` 与 `messageBody` 两个 `Text` 塞进一个
 * `ColumnScope.() -> Unit` 交给调用方，于是"怎么排"整个留在了 `ErrorActivity`
 * 里，而那里唯一拿得到的是 `MaterialTheme.typography.bodyMedium`：一段正文字体的
 * 堆栈跟踪既不滚动，也没有被有界地夹住，一万行会把页面顶穿。现在"有什么内容"
 * 由下面那个纯函数算出来，"怎么排"收在这一页，两者第一次分开了。
 *
 * 这几个字段是为了守住用户明确提过的那几条：
 *
 * - [title] 与 [kind]：崩溃标题与 `Crash Type: ...` 一字不改；
 * - [cause]：真正的根因——整条异常链里最深的那一个，而不是最外层那个包装异常；
 * - [trace]：**完整**堆栈，逐字符不变，不截断、不折叠、不限行数。
 */
@Immutable
internal data class OxideCrashReport(
    val crashType: CrashType,
    /** 崩溃标题：启动器崩溃时是 `%s has encountered a fatal error`，游戏崩溃时是应用名 */
    val title: String,
    /** `Crash Type: ...` 那一行的完整文本 */
    val kind: String,
    /** 一句话说明（`crash_launcher_message` / `crash_exit_message`） */
    val summary: String,
    /** 根因摘要；没有异常时是空串，空串意味着这一行整个不画 */
    val cause: String,
    /** 完整堆栈，逐字符不变 */
    val trace: String,
    /** [trace] 的行数。只供断言与排版参考，任何地方都不拿它去裁剪正文 */
    val traceLineCount: Int,
    val canShareLink: Boolean,
    val canShareLogs: Boolean,
    val canRestart: Boolean,
    val canExit: Boolean,
)

/**
 * 走 `cause` 链最多看多少层
 *
 * `Throwable.cause` 在理论上可以成环：`initCause` 只挡得住"自己是自己的原因"，
 * 挡不住两个异常互相指认。崩溃页不需要无限深——真机上的栈也就几十层——但它也
 * 绝不能因为一个环就把 `onCreate` 挂住，所以这条上限是硬约束。
 */
internal const val OXIDE_CRASH_CAUSE_LIMIT: Int = 64

/**
 * 异常链：由最外层一路走到最深处的根因
 *
 * 按**同一性**去重（[IdentityHashMap]，不是 `equals`）并受 [OXIDE_CRASH_CAUSE_LIMIT]
 * 约束，因此环与自指只会让遍历提前停下：不会死循环，也不会把栈撑爆。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideCrashCauseChain(throwable: Throwable?): List<Throwable> {
    if (throwable == null) return emptyList()

    val chain = mutableListOf<Throwable>()
    val seen = IdentityHashMap<Throwable, Boolean>()
    var current: Throwable? = throwable
    while (current != null && chain.size < OXIDE_CRASH_CAUSE_LIMIT) {
        if (seen.put(current, true) != null) break
        chain.add(current)
        current = current.cause
    }
    return chain
}

/**
 * 异常类型名
 *
 * 匿名类的 `simpleName` 是空串。这种情况下退回全限定名，而不是留一行空白。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideCrashTypeName(throwable: Throwable): String {
    val simple = throwable.javaClass.simpleName
    return if (simple.isNotBlank()) simple else throwable.javaClass.name
}

/**
 * 根因摘要：`类型: 消息`，后面跟上链长与被吞掉的异常数
 *
 * 分隔符只用 ASCII：这一行走的是等宽字体，而栈跟踪里本来就有中文与 emoji，
 * 摘要里再加花哨符号只会更难读。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideCrashCauseLabel(chain: List<Throwable>): String {
    val root = chain.lastOrNull() ?: return ""

    val message = root.message?.takeIf { it.isNotBlank() }
    val head = if (message == null) {
        oxideCrashTypeName(root)
    } else {
        "${oxideCrashTypeName(root)}: $message"
    }

    val notes = mutableListOf<String>()
    if (chain.size > 1) notes += "${chain.size} in chain"
    if (root.suppressed.isNotEmpty()) notes += "${root.suppressed.size} suppressed"
    return if (notes.isEmpty()) head else "$head (${notes.joinToString(", ")})"
}

/**
 * 堆栈行数
 *
 * 只有统计，没有任何一处拿它去裁剪正文。末行没有换行符时也算一行，所以
 * `"a\nb\nc"` 是 3 而不是 2；空串是 0 行。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideCrashTraceLineCount(trace: String): Int {
    if (trace.isEmpty()) return 0
    var lines = 1
    for (char in trace) {
        if (char == '\n') lines++
    }
    return lines
}

/**
 * 由（崩溃类型，标题，类型标签，说明，异常，完整堆栈）算出崩溃页要显示的全部内容
 *
 * [trace] 是**已经算好的完整堆栈**，调用方直接交来 `throwableToString(throwable)`
 * 的结果，而不是让这里重算一次：那一行必须留在 `ErrorActivity` 里，报告才是从
 * 抛出的那一个异常本身生成的，中间没有任何人替换过它。
 *
 * [throwable] 只用来挑根因那一行。游戏崩溃（进程被 JVM 带走）没有可序列化的
 * 异常，传 null。
 *
 * 三个动作的可见性就是调用方给的三个开关，一个也没有在这里被改写：分享日志看
 * 日志文件在不在，重启看能不能重启，分享链接看上传通道有没有起来。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideCrashReport(
    crashType: CrashType,
    title: String,
    kind: String,
    summary: String,
    throwable: Throwable?,
    trace: String,
    shareLogs: Boolean,
    canUpload: Boolean,
    canRestart: Boolean,
): OxideCrashReport {
    return OxideCrashReport(
        crashType = crashType,
        title = title,
        kind = kind,
        summary = summary,
        cause = oxideCrashCauseLabel(oxideCrashCauseChain(throwable)),
        // 原样返回：不 trim、不截断、不限行数、不折叠
        trace = trace,
        traceLineCount = oxideCrashTraceLineCount(trace),
        canShareLink = canUpload,
        canShareLogs = shareLogs,
        canRestart = canRestart,
        // 退出是唯一没有条件的动作：崩溃页本身就是一个死局
        canExit = true,
    )
}

/**
 * 堆栈正文那一节的标题
 *
 * 复用日志页早就有的那两个来源名（"Launcher crash log" / "Latest game log"），
 * 语义正好对得上这两类崩溃，也就不用为一个只在崩溃页出现的小标题去动字符串表。
 */
@StringRes
internal fun oxideCrashTraceLabelRes(crashType: CrashType): Int = when (crashType) {
    CrashType.LAUNCHER_CRASH -> R.string.oxide_sec_log_crash
    CrashType.GAME_CRASH -> R.string.oxide_sec_log_game
}

// ---------------------------------------------------------------------------
// 界面
// ---------------------------------------------------------------------------

/**
 * 崩溃页
 *
 * 此前这一页是旧 Zalith 的那一块：横屏是一整块灰色圆角卡片（`BackgroundCard`）
 * 加右边四个占满整列的鲑鱼色大按钮（`ScalingActionButton`），竖屏则换成
 * `Scaffold` + `TopAppBar` + 一个 `DropdownMenu` 把动作全收进去。报告里那三枚
 * 铺满宽度的按钮就是它们。
 *
 * 现在两个方向共用同一块 [OxidePanelShell]：标题行 + [OxideSecDivider] + 内容 +
 * 一条分隔线 + 按钮栏，尺寸全部来自 [OxideMetrics]。横竖屏的差别只剩下旋转按钮
 * 把 Activity 切到哪一边——面板本身是自适应的，账号页与联机页早就是这么做的，
 * 因此竖屏那一版并没有被丢掉，只是它不再需要一整套自己的排版。
 *
 * **堆栈正文一行都不截断。** 面板内容区先被 [OxidePanelShell] 自己的
 * `heightIn(max = bounds.height)` 夹住，再在里面滚（顺序反过来就是 v1.5.0 那个
 * P0 崩溃），因此一万行堆栈也只会把面板撑到上限，多出来的部分在那个有界滚动
 * 容器里滚掉。正文那个 `Text` 因此既没有 `maxLines` 也没有省略号。
 *
 * @param crashTitle 崩溃标题：`crash_launcher_title` 格式化后的文本，游戏崩溃时是应用名
 * @param crashTypeLabel `Crash Type: ...` 那一行的完整文本
 * @param crashSummary 一句话说明
 * @param crashTrace 完整堆栈，逐字符不变
 * @param crashThrowable 原始异常，只用来挑根因那一行；游戏崩溃传 null
 */
@Composable
fun ErrorScreen(
    crashType: CrashType,
    crashTitle: String,
    crashTypeLabel: String,
    crashSummary: String,
    crashTrace: String,
    crashThrowable: Throwable? = null,
    shareLogs: Boolean = true,
    canUpload: Boolean = false,
    canRestart: Boolean = true,
    onShareLogsClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onRestartClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onOrientationChanged: (Int) -> Unit = {},
    metrics: OxideMetrics = rememberOxideMetrics(),
) {
    // 方向只决定旋转按钮把 Activity 切到哪一边，版面本身不因此分叉
    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    val report = remember(
        crashType,
        crashTitle,
        crashTypeLabel,
        crashSummary,
        crashTrace,
        crashThrowable,
        shareLogs,
        canUpload,
        canRestart,
    ) {
        oxideCrashReport(
            crashType = crashType,
            title = crashTitle,
            kind = crashTypeLabel,
            summary = crashSummary,
            throwable = crashThrowable,
            trace = crashTrace,
            shareLogs = shareLogs,
            canUpload = canUpload,
            canRestart = canRestart,
        )
    }

    OxideCrashPanelHost(
        report = report,
        traceLabel = stringResource(oxideCrashTraceLabelRes(crashType)),
        metrics = metrics,
        shareLinkText = stringResource(R.string.crash_link_share_button),
        shareLogsText = stringResource(R.string.crash_share_logs),
        restartText = stringResource(R.string.crash_restart),
        exitText = stringResource(R.string.crash_exit),
        rotateDescription = stringResource(R.string.crash_rotate),
        onShareLinkClick = onUploadClick,
        onShareLogsClick = onShareLogsClick,
        onRestartClick = onRestartClick,
        onExitClick = onExitClick,
        onRotateClick = {
            onOrientationChanged(
                if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            )
        },
    )
}

/**
 * 页面与快照测试共用的入口
 *
 * `metrics` 是显式传进来的而不是在内部读 `LocalConfiguration`：这是 Oxide 那套
 * 快照测试的硬约定（见 `OxidePaparazzi`）——"被测的尺寸就是断言的尺寸"这条性质
 * 只在调用方把尺寸一起带进来时才成立。面板的宽高仍然由宿主量到的真实可用区域
 * 推出，因此换设备时它自己跟着变。
 */
@Composable
internal fun OxideCrashPanelHost(
    report: OxideCrashReport,
    traceLabel: String,
    metrics: OxideMetrics,
    shareLinkText: String,
    shareLogsText: String,
    restartText: String,
    exitText: String,
    rotateDescription: String,
    onShareLinkClick: () -> Unit,
    onShareLogsClick: () -> Unit,
    onRestartClick: () -> Unit,
    onExitClick: () -> Unit,
    onRotateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        OxideCrashPanel(
            report = report,
            traceLabel = traceLabel,
            metrics = metrics,
            // 面板的宽高来自这一块表面的真实可用区域，而不是物理屏幕：
            // 分屏、多窗口与折叠屏展开时窗口都小于整块屏幕
            bounds = oxidePanelBoundsFor(maxWidth, maxHeight, metrics),
            shareLinkText = shareLinkText,
            shareLogsText = shareLogsText,
            restartText = restartText,
            exitText = exitText,
            rotateDescription = rotateDescription,
            onShareLinkClick = onShareLinkClick,
            onShareLogsClick = onShareLogsClick,
            onRestartClick = onRestartClick,
            onExitClick = onExitClick,
            onRotateClick = onRotateClick,
        )
    }
}

/**
 * 崩溃面板本体
 *
 * 排布照账号页/联机页那一块 [OxidePanelShell] 原样来，只是内容换成了崩溃报告：
 *
 * - 标题是 `Crash Type: ...`。它短，面板标题行那一行永远装得下；崩溃标题
 *   （`... has encountered a fatal error`）整句放进正文第一行，用
 *   [Oxide.Type.DrawerTitle] 且不截断——标题行是 `maxLines = 1` 的，拿去放长句
 *   就等于把它省略掉，那不叫"保留"。
 * - 正文依次是：崩溃标题、一句话说明、根因（[Oxide.Type.Mono]）、
 *   [OxideSecDivider]、堆栈那一节的标签、完整堆栈（[Oxide.Type.Mono]，
 *   **没有** `maxLines`）。
 * - 按钮栏保留原来的四个动作与它们各自的显示条件，多出来的只有最左边那枚旋转
 *   按钮：竖屏那一版原先把它塞进顶栏的 `DropdownMenu`，横屏那一版是顶栏右上角
 *   的一枚图标按钮，现在两者合成按钮栏最左边这一枚，两个方向都还在。
 */
@Composable
internal fun OxideCrashPanel(
    report: OxideCrashReport,
    traceLabel: String,
    metrics: OxideMetrics,
    bounds: OxidePanelBounds,
    shareLinkText: String,
    shareLogsText: String,
    restartText: String,
    exitText: String,
    rotateDescription: String,
    onShareLinkClick: () -> Unit,
    onShareLogsClick: () -> Unit,
    onRestartClick: () -> Unit,
    onExitClick: () -> Unit,
    onRotateClick: () -> Unit,
) {
    OxidePanelShell(
        title = report.kind,
        metrics = metrics,
        bounds = bounds,
        // 崩溃页没有"关掉但留着"这一说：✕ 与"退出"是同一件事
        onClose = onExitClick,
        footer = {
            OxideIconButton(
                onClick = onRotateClick,
                glyph = ROTATE_GLYPH,
                modifier = Modifier.oxideIconDescription(rotateDescription),
            )
            if (report.canShareLink) {
                OxideButton(
                    text = shareLinkText,
                    onClick = onShareLinkClick,
                    tone = OxideButtonTone.Secondary,
                )
            }
            if (report.canShareLogs) {
                OxideButton(
                    text = shareLogsText,
                    onClick = onShareLogsClick,
                    tone = OxideButtonTone.Secondary,
                )
            }
            if (report.canRestart) {
                OxideButton(
                    text = restartText,
                    onClick = onRestartClick,
                    tone = OxideButtonTone.Secondary,
                )
            }
            if (report.canExit) {
                OxideButton(
                    text = exitText,
                    onClick = onExitClick,
                    tone = OxideButtonTone.Primary,
                )
            }
        },
    ) {
        Text(
            text = report.title,
            color = Oxide.Fg,
            fontSize = Oxide.Type.DrawerTitle.fontSize,
            lineHeight = Oxide.Type.DrawerTitle.lineHeight,
        )

        if (report.summary.isNotBlank()) {
            Text(
                text = report.summary,
                color = Oxide.FgMuted,
                fontSize = Oxide.Type.Body.fontSize,
                lineHeight = Oxide.Type.Body.lineHeight,
            )
        }

        if (report.cause.isNotBlank()) {
            Text(
                text = report.cause,
                color = Oxide.Fg,
                fontSize = Oxide.Type.Mono.fontSize,
                lineHeight = Oxide.Type.Mono.lineHeight,
            )
        }

        OxideSecDivider()

        Text(
            text = traceLabel,
            color = Oxide.FgDim,
            fontSize = Oxide.Type.MicroLabel.fontSize,
            lineHeight = Oxide.Type.MicroLabel.lineHeight,
        )

        // 完整堆栈。刻意不给 maxLines、也不给省略号：这一段是崩溃页存在的理由，
        // 被省略掉的部分正是用户唯一能拿去修这个 bug 的信息。可滚的是外层面板
        // 那个内容区，它已经被 heightIn 夹成有界的了
        //
        // 那块底是 [Oxide.BgElevated] 而不是面板自己的底：堆栈是这一页唯一需要
        // 被逐行读的东西，给它一块更靠前的底色，读到哪一行都不与面板背景混在一起
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Oxide.RadiusPanel)
                .background(Oxide.BgElevated)
                .border(BorderStroke(1.dp, Oxide.Line2), Oxide.RadiusPanel)
                .padding(metrics.cardGap),
        ) {
            Text(
                text = report.trace,
                color = Oxide.FgDim,
                fontSize = Oxide.Type.Mono.fontSize,
                lineHeight = Oxide.Type.Mono.lineHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}