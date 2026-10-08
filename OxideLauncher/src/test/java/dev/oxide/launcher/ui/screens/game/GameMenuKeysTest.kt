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

package dev.oxide.launcher.ui.screens.game.elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 游戏内菜单 Lazy 列表键的唯一性回归测试。
 *
 * 背景：Silica 面板复用了图形分组的标题资源，导致同一 LazyColumn 里出现两个
 * key 都是 "group:2131888323" 的 header，Compose 在测量时抛
 * IllegalArgumentException（Key was already used）。标题文字可以重复，
 * key 绝不可以。
 *
 * 规则（与 GameMenuSubscreen.kt 里 group() 的实现逐字对应）：
 * - group(R.string.X) 的运行 key 是 "group:X"；
 * - group(R.string.X, "section") 的运行 key 是 "group:X:section"，
 *   section 是固定的归属段标识，不许是随机数，也不许是列表下标；
 * - 行 key（action/switch/intSlider/choice/number 的首参字面量与
 *   item(key = "...")）与分组 key 共享同一个 Lazy 命名空间，必须全局唯一。
 *
 * 本测试读源码而不是跑 Compose：JVM 单测里没有 LazyColumn，但 key 的推导是纯
 * 文本规则；任何看不懂的 group(/key 写法都判失败，而不是悄悄跳过——
 * 漏网的动态 key 正是这类崩溃的来源。
 */
class GameMenuKeysTest {

    private fun menuSource(): String = locate(
        "java/dev/oxide/launcher/ui/screens/game/elements/GameMenuSubscreen.kt"
    ).readText()

    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val base = dir ?: return@repeat
            for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
                val candidate = base.resolve(prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = base.parentFile
        }
        error("could not locate src/main/$relativePath from " + File("").absolutePath)
    }

    /** 运行 key 推导（与 group() 实现一致）：资源名 + 可选的固定段后缀。 */
    private fun groupKey(res: String, section: String?): String =
        if (section == null) "group:$res" else "group:$res:$section"

    private fun collectKeys(source: String): List<String> {
        val keys = mutableListOf<String>()
        source.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            val no = index + 1
            if ("group(" in line && "fun " !in line) {
                val m = Regex("^group\\(R\\.string\\.(\\w+)(?:\\s*,\\s*\"([^\"]+)\")?\\)").find(line)
                    ?: fail("line $no: unrecognized group() shape, key cannot be proven unique: $line")
                keys += groupKey(m.groupValues[1], m.groupValues[2].ifEmpty { null })
            }
            val row = Regex("^(action|switch|intSlider|choice|number)\\(\\s*\"([^\"]+)\"").find(line)
            if (row != null) keys += row.groupValues[2]
            val item = Regex("^item\\(key\\s*=\\s*\"([^\"]+)\"").find(line)
            if (item != null) keys += item.groupValues[1]
            val dyn = Regex("key\\s*=\\s*(?!\"[^\"]*\"|^key\\b)([^,)]+)").find(line)
            if (dyn != null && "fun " !in line) {
                fail("line $no: non-literal Lazy key escapes the uniqueness guard: $line")
            }
        }
        return keys
    }

    @Test
    fun everyLazyKeyInTheGameMenuIsUnique() {
        val keys = collectKeys(menuSource())
        assertTrue("expected game menu rows, parsed none", keys.isNotEmpty())
        val dup = keys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(
            "duplicate Lazy keys would crash measurement: " + dup.entries.joinToString(),
            dup.isEmpty(),
        )
    }

    @Test
    fun sharedHeaderResourcesCarryTheirOwningSection() {
        // 本次崩溃的精确形态：图形分组标题同时出现在主区域与 Silica 面板。
        // 两个 occurrence 必须带不同的固定段后缀，而不是共享裸 key。
        val source = menuSource()
        val graphicsGroups = Regex("group\\(R\\.string\\.oxide_set_section_graphics(?:\\s*,\\s*\"([^\"]+)\")?\\)")
            .findAll(source).toList()
        assertTrue(
            "expected the graphics header in both the main section and the Silica panel",
            graphicsGroups.size >= 2,
        )
        val keys = graphicsGroups.map { groupKey("oxide_set_section_graphics", it.groupValues[1].ifEmpty { null }) }
        assertEquals(
            "each graphics header occurrence needs its own section-scoped key",
            keys.size, keys.toSet().size,
        )
        assertTrue(
            "the Silica panel must own its header occurrence via a fixed section id",
            keys.any { it.endsWith(":silica") },
        )
    }
}
