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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 协议全文读完之后该显示成什么
 *
 * `res/raw` 里那十九份协议是 GPLv3 要求随附的正文，删掉这个渲染等于让它们在应用里
 * 无法阅读。因此"读不出来"这件事必须真的能被说出来，而不是留下一块空白面板。
 *
 * 三种结果里只有两种需要区分：读不出来（重试有意义）与读出来是空的（重试没有意义）。
 * 旧界面把两者都变成"一屏编辑器里的错误文字"，用户既看不出出了什么事，也不知该做什么。
 */
class OxideLicenceLoadTest {

    @Test
    fun `a read licence shows exactly what the file holds`() {
        val text = "GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007\n"
        val load = oxideLicenceLoadOf(Result.success(text))
        assertTrue(load is OxideLicenceLoad.Ready)
        assertEquals(text, (load as OxideLicenceLoad.Ready).text)
    }

    @Test
    fun `the whole licence body survives the trip`() {
        // GPL 全文有好几十 KB：任何一处截断或改写都是把一份许可文本悄悄改掉了
        val text = buildString { repeat(4000) { append("clause $it of the licence text\n") } }
        val load = oxideLicenceLoadOf(Result.success(text)) as OxideLicenceLoad.Ready
        assertEquals(text, load.text)
        assertTrue("a real licence body is long", load.text.length > 50_000)
    }

    @Test
    fun `a file that could not be read says so, and says why it matters`() {
        val load = oxideLicenceLoadOf(Result.failure(IOException("no such raw resource")))
        assertEquals(
            OxideLicenceLoad.Failed(OxideLicenceFailure.Unreadable),
            load,
        )
    }

    @Test
    fun `a file with no text in it is not the same as one that could not be read`() {
        // 两种失败必须能分辨：前者重试没有意义，重试多少次还是空的
        for (empty in listOf("", "   ", "\n\n", " \t\r\n ")) {
            assertEquals(
                OxideLicenceLoad.Failed(OxideLicenceFailure.Empty),
                oxideLicenceLoadOf(Result.success(empty)),
            )
        }
    }

    @Test
    fun `the mapper never answers loading, that state belongs to the panel`() {
        // 读取这一层只回答"读到了什么"；"正在读"由面板自己的状态表达。
        // 两者混在一处就会出现第三种组合：既没有文本也没有错误，面板永远停在加载中。
        for (read in listOf(
            Result.success("text"),
            Result.success("  "),
            Result.failure(IOException("boom")),
        )) {
            assertTrue(
                "the mapper must not answer Loading: it has no third state to report",
                oxideLicenceLoadOf(read) !is OxideLicenceLoad.Loading,
            )
        }
    }
}