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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.viewmodel

import dev.oxide.layercontroller.layout.loadLayoutFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 随包分发的那份默认控制布局就是用户提供的那一份
 *
 * 用户给的文件（12 颗按键，Gui 一层 + oxide-default 一层）与包里原先那份（9 颗）
 * 是同一条线上的两个版本：两层、uuid、editorVersion 一模一样，多出来的那三颗是
 * 攻击/前进/输入法这些真正要按的东西。把打包的这一份换成它，因此必须钉死——否则下一次
 * "顺手把默认布局整理一下"就会把用户按不到的那几颗又抹掉，而没有任何测试会响。
 *
 * 这些断言盯的是**内容**：层数、层 uuid、按键数与 editorVersion。反过来"文件被整个换掉"
 * 那条由 DefaultControlLayoutTest 的 info 署名负责，两边合起来才是一份完整的钉子。
 *
 * 全部是纯 JVM 的：走 loadLayoutFromString 那条真正的加载路径，既不碰 Android API
 * 也不需要 Robolectric。
 */
class EditorDefaultLayoutAssetTest {

    private val assetText = locate("assets/default_layout.json").readText()
    private val asset = Json.parseToJsonElement(assetText).jsonObject

    /** 两层各自的 uuid，顺序与文件里一致 */
    private val layerUuids: List<String> = layers().map { it.getValue("uuid").jsonPrimitive.content }

    private val totalButtons: Int = layers().sumOf {
        (it["normalButtons"]?.jsonArray?.size ?: 0) +
            (it["textBoxes"]?.jsonArray?.size ?: 0) +
            (it["joystickButtons"]?.jsonArray?.size ?: 0)
    }

    @Test
    fun theBundledAssetIsTheLayoutTheUserSupplied() {
        // 换掉整份文件（哪怕按键数一样）之后，这两条 uuid 会先响
        assertTrue(
            "the bundled default layout must ship both supplied layers, got $layerUuids",
            layerUuids.containsAll(SUPPLIED_LAYER_UUIDS),
        )
        assertEquals(
            "the supplied layout has exactly these two layers, in this order: $layerUuids",
            SUPPLIED_LAYER_UUIDS,
            layerUuids,
        )
    }

    @Test
    fun theBundledAssetCarriesAtLeastTheTwelveSuppliedButtons() {
        // 提供的那份是 12 颗（Gui 一颗 + oxide-default 十一颗）；包里原先那份只有 9 颗。
        // 卡在 12 而不是"等于 12"：以后再补几颗是好事，退回 9 颗才是回归。
        assertTrue(
            "the bundled default layout must ship at least 12 controls, got $totalButtons",
            totalButtons >= 12,
        )
        // 两个 id 是分开的：总数够而某一层是空的，仍然是"打开编辑器看到一层空白"
        assertEquals("all of the supplied controls sit in oxide-default", 11, buttonsOf("048cf5d399b3"))
        assertEquals("Gui keeps its one switch-layer button", 1, buttonsOf("0f1a4c7a0a75"))
    }

    @Test
    fun everyLayerHasSomethingOnIt() {
        // 编辑器选中一层之后网格里立刻要画出东西；一层空白会让新装用户以为编辑坏了
        layers().forEach { layer ->
            val uuid = layer.getValue("uuid").jsonPrimitive.content
            assertTrue("layer $uuid ships no control at all", buttonsOf(uuid) > 0)
        }
    }

    @Test
    fun theAssetIsAtLeastTheEditorVersionItWasExportedWith() {
        val version = asset.getValue("editorVersion").jsonPrimitive.int
        assertTrue("editorVersion $version must be at least 11", version >= 11)
    }

    @Test
    fun theBundledAssetStillLoadsThroughTheRealSchema() {
        // 真正的加载路径：读 editorVersion、跑对应版本的迁移、跑 ButtonPosition 与
        // ButtonSize 每一个 init 块的范围检查。任何一处越界，error() 就是从这一行抛出来的。
        val layout = loadLayoutFromString(assetText)
        assertTrue("the bundled default layout must load at least one layer", layout.layers.isNotEmpty())
        assertEquals(
            "the supplied controls must survive loading, got ${layout.layers.sumOf { it.normalButtons.size }}",
            totalButtons,
            layout.layers.sumOf { it.normalButtons.size },
        )
        assertEquals(
            "the layer uuids must survive loading, got ${layout.layers.map { it.uuid }}",
            layerUuids,
            layout.layers.map { it.uuid },
        )
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun layers() = asset.getValue("layers").jsonArray.map { it.jsonObject }

    private fun buttonsOf(uuid: String): Int = layers()
        .first { it.getValue("uuid").jsonPrimitive.content == uuid }
        .let { layer ->
            (layer["normalButtons"]?.jsonArray?.size ?: 0) +
                (layer["textBoxes"]?.jsonArray?.size ?: 0) +
                (layer["joystickButtons"]?.jsonArray?.size ?: 0)
        }

    /**
     * 在模块的 src/main 下定位一个文件
     *
     * 两种前缀都试一遍：单元测试的工作目录是模块目录（`src/main/…`），从仓库根目录跑时
     * 又是 `OxideLauncher/src/main/…`。定位不到就报错，而不是跳过。
     */
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
        error("could not locate src/main/$relativePath from ${File("").absolutePath}")
    }

    private companion object {
        /** 用户提供的那份布局里的两层，顺序与文件里一致 */
        val SUPPLIED_LAYER_UUIDS = listOf("0f1a4c7a0a75", "048cf5d399b3")
    }
}