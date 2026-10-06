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

package dev.oxide.launcher.ui.components

import androidx.compose.ui.graphics.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 * `fadeEdge` 默认渐隐样式的缓存守备。
 *
 * `fadeEdge` 在组合阶段读取 `ScrollState.value` / `LazyListState.layoutInfo`，
 * 所以滚动期间调用点逐帧重组，默认参数也逐帧求值。改造前默认值是
 * `createDefaultFadeStyle(direction)`，每一次求值都新建一组
 * FadeStyle + Brush + 色标数组（约 5 个小对象），全模块二十余处调用点在滚动时
 * 持续制造这份 churn。现在两个方向各共享一份实例，滚动不再分配。
 *
 * `FadeStyle` 与 `Brush` 都不可变，共享实例不改变任何绘制结果；
 * 渐变的具体长相由 Paparazzi 金图守住。
 */
class FadeStyleCacheTest {

    // ------------------------------------------------------------------
    // 行为断言：纯 Kotlin 对象，不碰任何 android 运行时
    // ------------------------------------------------------------------

    @Test
    fun `the default fade style is a shared instance per direction`() {
        assertSame(
            "scrolling must not allocate a FadeStyle per recomposition",
            defaultFadeStyle(EdgeDirection.Vertical),
            defaultFadeStyle(EdgeDirection.Vertical)
        )
        assertSame(
            defaultFadeStyle(EdgeDirection.Horizontal),
            defaultFadeStyle(EdgeDirection.Horizontal)
        )
        assertNotSame(
            "the two directions are different styles",
            defaultFadeStyle(EdgeDirection.Vertical),
            defaultFadeStyle(EdgeDirection.Horizontal)
        )
    }

    @Test
    fun `the shared styles keep the original definition`() {
        assertEquals(BlendMode.DstOut, defaultFadeStyle(EdgeDirection.Vertical).blendMode)
        assertEquals(BlendMode.DstOut, defaultFadeStyle(EdgeDirection.Horizontal).blendMode)
        // 公共工厂语义不变：调用方仍能拿到只属于自己的新实例
        assertNotSame(
            createDefaultFadeStyle(EdgeDirection.Vertical),
            createDefaultFadeStyle(EdgeDirection.Vertical)
        )
    }

    // ------------------------------------------------------------------
    // 源码守备：两处重载的默认值都走共享实例
    // ------------------------------------------------------------------

    @Test
    fun `both fadeEdge overloads default to the shared styles`() {
        val source = codeOf(locate("ui/components/_Scroll.kt").readText())
        assertEquals(
            "both overloads must default to the shared style",
            2,
            source.occurrencesOf("style: FadeStyle = defaultFadeStyle(direction)")
        )
        assertFalse(
            "the default argument must not build a gradient while composing",
            source.contains("= createDefaultFadeStyle(direction)")
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Find a file by its path under the module's `src/main`.
     *
     * Same walk-up pattern as the other source guards: a unit test's working
     * directory is not necessarily the module root.
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

    /**
     * Strip string literals and comments, leaving only what gets compiled.
     *
     * Order matters: strings first, then comments. A `//` inside a string
     * literal would otherwise start a line comment and swallow the file.
     */
    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /** How many times [token] occurs in this source text. */
    private fun String.occurrencesOf(token: String): Int {
        var count = 0
        var index = 0
        while (true) {
            val found = indexOf(token, index)
            if (found < 0) return count
            count++
            index = found + token.length
        }
    }

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}
