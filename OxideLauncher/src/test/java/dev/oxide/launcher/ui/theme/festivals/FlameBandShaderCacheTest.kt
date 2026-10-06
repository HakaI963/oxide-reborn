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

package dev.oxide.launcher.ui.theme.festivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 万圣节火焰带的逐帧分配守备。
 *
 * `BatDrawer.drawFlameBand` 在渲染线程上每帧跑一次。改造前它每帧新建 4 个
 * `LinearGradient` 与 1 个 `Matrix`（3 层竖向渐变 + 前层滚动渐变 + 滚动矩阵），
 * 60fps 下就是每秒 300 个小对象，其中 4 个还带着原生着色器。现在渐变按输入
 * （宽高/密度/配色）缓存，逐帧路径只更新缓存渐变的局部矩阵。
 *
 * 这里的断言分两半：
 *
 * 1. 纯函数 [flameBandWaveOffset] 的公式与改造前内联表达式逐项相同。
 * 2. 源码守备：逐帧函数体里不允许再出现着色器/矩阵构造。绘制内容是否逐像素
 *    一致由 Paparazzi 金图守住，这里守的是"分配不再发生"这一半。
 */
class FlameBandShaderCacheTest {

    private val renderer by lazy { codeOf(locate(RENDERER_PATH).readText()) }

    // ------------------------------------------------------------------
    // 纯函数：滚动偏移公式
    // ------------------------------------------------------------------

    @Test
    fun `wave offset keeps the original formula`() {
        // 原内联表达式：val offset = (clock * BAND_WAVE_SPEED_DP * density) % period
        assertEquals(6f, flameBandWaveOffset(clock = 2f, speedDp = 26f, density = 3f, period = 50f), 0f)
        assertEquals(64f, flameBandWaveOffset(clock = 7f, speedDp = 26f, density = 2f, period = 100f), 0f)
        assertEquals(0f, flameBandWaveOffset(clock = 0f, speedDp = 26f, density = 3f, period = 50f), 0f)
    }

    @Test
    fun `wave offset wraps with the period`() {
        val period = 40f
        val offset = flameBandWaveOffset(clock = 5f, speedDp = 10f, density = 2f, period = period)
        assertTrue("offset must stay inside one period", offset >= 0f && offset < period)
    }

    // ------------------------------------------------------------------
    // 源码守备：逐帧路径不再构造着色器/矩阵
    // ------------------------------------------------------------------

    @Test
    fun `flame band draw builds no shader and no matrix`() {
        val body = renderer.functionBody("private fun drawFlameBand(")
        assertFalse(
            "a per frame LinearGradient allocation is back in the flame band",
            body.contains("LinearGradient(")
        )
        assertFalse(
            "a per frame Matrix allocation is back in the flame band",
            body.contains("Matrix()")
        )
        // 画笔状态卫生不变：收尾仍要还回空着色器
        assertTrue(body.contains("paint.shader = null"))
    }

    @Test
    fun `flame band shaders are built in a guarded cache`() {
        val builder = renderer.functionBody("private fun flameBandShaders(")
        assertTrue(
            "the gradient construction must live in the guarded builder",
            builder.contains("LinearGradient(")
        )
        // 缓存守卫必须比较渐变的全部输入：宽高、密度与两种配色。
        // 少比任何一个，尺寸或主题变化后就会复用到过期的渐变
        for (guard in listOf(
            "width == bandShaderWidth",
            "height == bandShaderHeight",
            "density == bandShaderDensity",
            "wispHalo == bandShaderHalo",
            "wispCore == bandShaderCore",
        )) {
            assertTrue("the cache guard must compare $guard", builder.contains(guard))
        }
    }

    @Test
    fun `the wave matrix is reused and reset before translation`() {
        assertTrue(
            "the wave matrix must be a reused field, not a per frame allocation",
            renderer.contains("private val bandWaveMatrix = Matrix()")
        )
        val body = renderer.functionBody("private fun drawFlameBand(")
        val reset = body.indexOf("bandWaveMatrix.reset()")
        val translate = body.indexOf("bandWaveMatrix.postTranslate(")
        assertTrue(
            "reset() must precede postTranslate or stale offsets accumulate",
            reset >= 0 && reset < translate
        )
        assertTrue(body.contains("setLocalMatrix(bandWaveMatrix)"))
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

    /**
     * The balanced `{...}` body of the function whose declaration contains
     * [signature]. Strings and comments are already blanked, so counting
     * braces is enough.
     */
    private fun String.functionBody(signature: String): String {
        val start = indexOf(signature)
        assertTrue("could not find $signature", start >= 0)
        val open = indexOf('{', start + signature.length)
        assertTrue("could not find the body of $signature", open >= 0)
        var depth = 0
        var index = open
        while (index < length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(open, index + 1)
                }
            }
            index++
        }
        error("unbalanced body for $signature")
    }

    private companion object {
        const val RENDERER_PATH = "ui/theme/festivals/EffectsRenderer.kt"
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}
