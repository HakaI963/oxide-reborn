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

package dev.oxide.launcher.ui.screens.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 游戏内日志框的正文颜色守卫
 *
 * 这一条是"读源码"的事实而不是行为，所以只能读源码：`Text` 不传 `color` 时，
 * Material3 去读 `LocalContentColor`，那个值只由 `Surface` 递下来；而
 * `GameScreen` 与 `LogBox` 之间一个 `Surface` 都没有，于是落到 Compose 默认的
 * `Color.Black`，在深色 `Oxide.PopoverBg`（0xFA0D0D0D）上是 1.1:1。没有 Compose
 * 运行环境时，唯一的办法就是把参数列表读出来。
 *
 * 为什么一个看起来无害的参数能活过两个版本：`Color.Black` 在浅色底幕上是
 * 21:1，浅色主题下日志完全正常，只有深色主题塌掉；而游戏内日志框几乎总是
 * 在深色游戏画面上开的，所以它平时没人看。浅色主题下"测不出来"这件事本身，
 * 由 `LogPaletteContrastTest` 里的数值钉住。
 *
 * 同样读源码的还有着色器那一处：`LogHighlighter.defaultColor` 的兜底路径会
 * 给整行日志上色，它必须由调用方给出，因此这里既要求 `LogBox` 传了，也要求
 * 构造参数不再有默认值——默认值就是当初那个在浅色底幕上不可见的 `Color.White`。
 *
 * 断言跑在剥掉注释与字符串字面量之后的源码上：本文件与被测文件里的注释都
 * 故意提到旧名字来说明来龙去脉，那段历史不该满足一个"必须存在"的断言。
 */
class LogBoxContrastTest {

    private val logBox = codeOf(locate("ui/screens/game/elements/LogBox.kt").readText())
    private val highlighter = codeOf(locate("ui/screens/game/elements/log_parser/Highlighter.kt").readText())

    // -----------------------------------------------------------------------
    // 正文
    // -----------------------------------------------------------------------

    /**
     * 日志正文那个 `Text` 必须显式给颜色。
     *
     * 断言的是 `text = log` 所在的那个调用，而不是整份文件：这份文件里还有
     * `stringResource` 与别的 `Text` 用法，整份文件一查就会把"这个文件里有
     * color="当成"日志正文有颜色"。
     */
    @Test
    fun `the log body text passes an explicit colour`() {
        val body = textArgument(logBox, "text = log,")

        assertTrue(
            "the log body Text must pass a colour, or Material3 falls back to Color.Black: $body",
            body.contains("color ="),
        )
        assertTrue(
            "and that colour has to be the resolved foreground, not a literal that ignores the theme: $body",
            body.contains("color = foreground"),
        )
        assertFalse(
            "a hardcoded colour cannot track PopoverBg, which flips with the theme: $body",
            COLOR_LITERAL.containsMatchIn(body),
        )
    }

    /** 那一行还必须继续挂在 `LogBox` 的底幕上——换了底色，前景色就选错了 */
    @Test
    fun `the log body still sits on the Oxide popover background`() {
        assertTrue(
            "the log body has to stay on Oxide.PopoverBg",
            logBox.contains("background(Oxide.PopoverBg)"),
        )
    }

    /**
     * 前景色来自 `Oxide.Fg`，也就是那个跟底幕配对翻面的角色。
     *
     * 不用字面量是因为底色两套主题两个值（0xFA0D0D0D 与 0xFFFFFFFF）：写死白字
     * 能治好深色、把浅色弄瞎，写死黑字则正好相反。
     */
    @Test
    fun `the log foreground comes from the Oxide token, not from a literal`() {
        assertTrue(
            "LogBox must resolve the foreground once from Oxide.Fg",
            logBox.contains("val foreground = Oxide.Fg"),
        )
        assertFalse(
            "the whole point is that the colour follows the theme",
            COLOR_LITERAL.containsMatchIn(logBox),
        )
    }

    // -----------------------------------------------------------------------
    // 着色器的兜底色
    // -----------------------------------------------------------------------

    /**
     * 着色器拿到的就是这个前景色，而且换主题要能换掉它。
     *
     * `remember(foreground)` 而不是 `remember {}`：颜色是组合阶段读的派生值，
     * 不进 key 就意味着切深浅色以后着色器还拿着旧的兜底色。
     */
    @Test
    fun `the highlighter is rebuilt with the current foreground colour`() {
        assertTrue(
            "the highlighter must be keyed on the foreground colour",
            logBox.contains("remember(foreground) { LogHighlighter(defaultColor = foreground) }"),
        )
    }

    /**
     * 监听回调必须读最新的那一个着色器。
     *
     * 回调是 `LaunchedEffect(enableLog)` 装一次就不再动的，它闭包里捕获的
     * 着色器在切主题后就是旧的。不读最新值的话，新来的日志会继续用旧配色
     * 着色，直到用户开关一次日志才恢复。
     */
    @Test
    fun `the log listener highlights with the latest highlighter`() {
        assertTrue(
            "the listener has to read the newest highlighter, not the one it captured",
            logBox.contains("rememberUpdatedState(logHighlighter)"),
        )
        assertTrue(
            "and it has to be the one that actually colours the line",
            logBox.contains("currentHighlighter.highlight(log)"),
        )
    }

    /**
     * `defaultColor` 不允许再有默认值。
     *
     * 旧的默认值 `Color.White` 是浅色主题下不可见的那一个。只要它还在，任何
     * 新的 `LogHighlighter()` 调用都会安静地重新引入同一个 bug——让编译器来挡
     * 比让这个断言来挡可靠。
     */
    @Test
    fun `the highlighter default colour has no built-in value`() {
        assertTrue(
            "defaultColor must be a required parameter: $highlighter",
            highlighter.contains("val defaultColor: Color,"),
        )
        assertFalse(
            "Color.White as the default is invisible on the light popover",
            highlighter.contains("defaultColor: Color = Color.White"),
        )
        assertFalse(
            "Color.Black as the default is invisible on the dark popover",
            highlighter.contains("defaultColor: Color = Color.Black"),
        )
    }

    /**
     * 没有全局可变颜色。
     *
     * 兜底色要跟着主题变，如果写成一个可变的全局量，任何一个忘记读的界面都能
     * 把它改成别的颜色，而这次修复会被悄悄拆掉。
     */
    @Test
    fun `no global mutable colour was introduced`() {
        for (forbidden in listOf(
            "var defaultColor",
            "lateinit var",
            "mutableStateOf<Color>",
            "Color.White",
            "Color.Black",
        )) {
            assertFalse(
                "the highlighter must not keep its colour in $forbidden",
                highlighter.contains(forbidden),
            )
        }
    }

    // -----------------------------------------------------------------------
    // 等级徽章
    // -----------------------------------------------------------------------

    /**
     * 四条等级规则都不允许再出现没有徽章的写法。
     *
     * 没有徽章的等级只能靠字色扛对比度，而字色要同时在近黑和纯白两种底幕上
     * 成立——写死的 `ERROR` 绿（0xFF6AAB73）在浅色底幕上只有 2.7:1，就是这么
     * 漏进来的。
     */
    @Test
    fun `no level rule is left without a badge`() {
        val rules = codeOf(locate("ui/screens/game/elements/log_parser/LogLevelRule.kt").readText())

        assertFalse(
            "a badge-less level rule cannot clear both popover backgrounds",
            rules.contains("backgroundColor = null"),
        )
        assertTrue(
            "the rules still have to be able to opt out at the type level",
            rules.contains("val backgroundColor: Color? = null"),
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * 参数列表里包含 [anchor] 的那个调用。
     *
     * 字符串字面量已经被 [codeOf] 抹掉，所以数括号就够——调用里的 `(` 不会来自
     * 任何 URL 或 KDoc。
     */
    private fun textArgument(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        assertTrue("could not find `$anchor`", start >= 0)
        val callStart = source.lastIndexOf("Text(", start)
        assertTrue("could not find the enclosing Text( call", callStart >= 0)
        return balanced(source, callStart)
    }

    /** 从 [open] 开始的那一组配对括号里的源码。 */
    private fun balanced(source: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        error("unbalanced call starting at $open")
    }

    /**
     * 剥掉字符串字面量与注释，只留下会被编译的东西。
     *
     * 顺序要紧：先字符串后注释。字符串字面量里的 `//`（一个 URL 什么的）否则会
     * 开一段行注释，把整个文件后面都吃掉，于是真正的失败会变成安静的通过。
     */
    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /**
     * 在 [source] 下按路径找文件。
     *
     * 单测的工作目录不保证是模块根目录，所以向上找几层。找不到是报错而不是跳过：
     * 跳过的守卫不算守卫。
     */
    private fun locate(relativePath: String): File {
        val root = "src/main/java/dev/oxide/launcher/$relativePath"
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(root)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate $root from ${File("").absolutePath}")
    }

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /** 写死的颜色字面量，比如 `Color(0xFFEEEEEE)` */
        val COLOR_LITERAL = Regex("""Color\(\s*0x""")
    }
}