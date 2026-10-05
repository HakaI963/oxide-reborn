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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.control

import dev.oxide.layercontroller.data.MIN_SIZE_DP
import dev.oxide.layercontroller.data.POSITION_RANGE
import dev.oxide.layercontroller.data.SIZE_PERCENTAGE
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 一段非负整数的字面量，用来区分 "383" 和 383.0 / "383" */
private val NON_NEGATIVE_INT = "\\d+".toRegex()

/** 点击事件类型里那些会指向别的层的类型 */
private val LAYER_EVENT_TYPES = setOf("switch_layer", "show_layer", "hide_layer")

/** 启动器事件键的声明处，单测从源码里读，避免在测试里抄第二份清单 */
private val LAUNCHER_EVENT_DECLARATIONS = locate("java/dev/oxide/launcher/ui/control/event/Events.kt")
    .readLines()
    .map { it.trim() }
    .filter { it.startsWith("const val ") && "launcher.event." in it }
    .mapNotNull { line -> "\"([^\"]+)\"".toRegex().find(line)?.groupValues?.get(1) }
    .toSet()

/**
 * 从测试工作目录往上定位仓库里 src/main 下的某个文件。
 *
 * 单元测试的工作目录是模块目录，仓库文件在它上面；两种相对路径都试一遍：从模块目录出发时
 * 前者是 src/main，从仓库根目录出发时是 OxideLauncher/src/main。
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

/**
 * 随包分发的默认控制布局
 *
 * 这份 assets 里的 json 会由 ControlManager 解压进用户的布局目录，是新装用户拿到的**第一份**
 * 布局。编译期发现不了的两类错都出在它身上：
 *
 * 一是它自己不合当前 LayerController 的 schema。字段名、枚举的序列化名（`switch_layer` 而不是
 * `SwitchLayer`）、以及 ButtonSize 初始化块里那几条范围检查，任何一条对不上，解压出来的文件都会
 * 在 refresh() 里被当成坏文件跳过——而那时用户目录里可能只剩它一份，编辑器于是打不开。
 *
 * 二是它被悄悄换回旧版。这条是纯回归：把 name/author/description 钉成用户提供的那份，任何一次
 * "顺手把默认布局换一份别的"都会在这里立刻失败，而不是等装机的人发现按键位置变了。
 *
 * 全部断言都是纯 JVM 的：走 loadLayoutFromString 那条真正的加载路径，但既不碰 Android API 也
 * 不需要 Robolectric。
 */
class DefaultControlLayoutTest {

    private val assetText = locate("assets/default_layout.json").readText()
    private val asset = Json.parseToJsonElement(assetText).jsonObject

    // ---- 能被真正加载 -----------------------------------------------------

    @Test
    fun theBundledAssetLoadsThroughTheRealSchema() {
        // loadLayoutFromString 会读 editorVersion、跑对应版本的迁移，并触发 ControlLayer /
        // ButtonPosition / ButtonSize / ButtonStyle / ButtonShape 每一个 init 块的范围检查。
        // 任何一个数值越界，ButtonSize 里的 error() 就是从这一行抛出来的。
        val layout = loadLayoutFromString(assetText)
        assertTrue("the bundled default layout must ship at least one layer", layout.layers.isNotEmpty())
        assertTrue(
            "every layer must carry at least one button, or the editor opens an empty screen",
            layout.layers.all { it.normalButtons.isNotEmpty() }
        )
    }

    @Test
    fun theAssetDeclaresTheFourTopLevelSections() {
        for (key in listOf("info", "layers", "styles", "editorVersion")) {
            assertTrue("the asset must declare \"$key\"", key in asset)
        }
    }

    @Test
    fun theEditorVersionIsOneTheCurrentEditorUnderstands() {
        // EDITOR_VERSION 是 LayerController 的 internal const，单测看不到；EmptyControlLayout
        // 正是拿它构造的，所以这一个字段就是当前编辑器版本，不必在测试里抄第二份数字。
        val current = EmptyControlLayout.editorVersion
        val declared = asset.getValue("editorVersion").jsonPrimitive.int
        assertTrue("editorVersion $declared must be positive", declared > 0)
        assertTrue(
            "editorVersion $declared is newer than the current editor ($current); " +
                "loadLayoutFromString throws IllegalArgumentException on it",
            declared <= current
        )
    }

    // ---- 层级与按钮 -------------------------------------------------------

    @Test
    fun everyLayerIsIdentifiedAndNamedUniquely() {
        val layers = asset.getValue("layers").jsonArray
        assertTrue("the layout must ship at least one layer", layers.isNotEmpty())
        val names = mutableListOf<String>()
        layers.forEach { element ->
            val layer = element.jsonObject
            val name = layer.getValue("name").jsonPrimitive.content
            assertTrue("every layer needs a name, got $layer", name.isNotBlank())
            assertTrue(
                "every layer needs a uuid, got $layer",
                layer.getValue("uuid").jsonPrimitive.content.isNotBlank()
            )
            names += name
        }
        assertEquals("layer names must be unique, got $names", names.size, names.toSet().size)
    }

    @Test
    fun everyButtonHasAUuidAndThoseUuidsAreUnique() {
        val uuids = allButtons().map { it.second.getValue("uuid").jsonPrimitive.content }
        assertTrue("the layout must ship at least one button", uuids.isNotEmpty())
        assertEquals(
            "button uuids must be unique, the editor keys its widgets by them; got $uuids",
            uuids.size,
            uuids.toSet().size
        )
    }

    @Test
    fun everyButtonSitsInsideTheScreen() {
        allButtons().forEach { (layerName, button) ->
            val position = button.getValue("position").jsonObject
            for (axis in listOf("x", "y")) {
                val primitive = position.getValue(axis).jsonPrimitive
                // ButtonPosition.x / y 是 Int：写成 383.0 或 "383" 都会在反序列化时炸掉
                assertTrue(
                    "position.$axis of a button in layer $layerName must be a JSON number, got $primitive",
                    !primitive.isString
                )
                assertTrue(
                    "position.$axis of a button in layer $layerName must be a non-negative integer, got $primitive",
                    NON_NEGATIVE_INT.matches(primitive.content)
                )
                val coordinate = primitive.int
                assertTrue(
                    "position.$axis = $coordinate of a button in layer $layerName is outside $POSITION_RANGE",
                    coordinate in POSITION_RANGE
                )
            }
        }
    }

    @Test
    fun everyButtonSizeIsInsideTheRangesButtonSizeValidates() {
        allButtons().forEach { (layerName, button) ->
            val uuid = button.getValue("uuid").jsonPrimitive.content
            val size = button.getValue("buttonSize").jsonObject
            for (axis in listOf("widthDp", "heightDp")) {
                val value = size.getValue(axis).jsonPrimitive.float
                assertTrue(
                    "button $uuid in layer $layerName has $axis = $value below MIN_SIZE_DP $MIN_SIZE_DP",
                    value >= MIN_SIZE_DP
                )
            }
            // 这两个字段在 ButtonSize 里是 Int，镜像它自己的 checkInRange 写法
            for (axis in listOf("widthPercentage", "heightPercentage")) {
                val value = size.getValue(axis).jsonPrimitive.int
                assertTrue(
                    "button $uuid in layer $layerName has $axis = $value outside SIZE_PERCENTAGE $SIZE_PERCENTAGE",
                    value.toFloat() in SIZE_PERCENTAGE
                )
            }
        }
    }

    // ---- 点击事件 ---------------------------------------------------------

    @Test
    fun everyClickEventUsesATypeTheCurrentEditorKnows() {
        val known = knownClickEventTypeNames()
        var checked = 0
        allButtons().forEach { (_, button) ->
            clickEventsOf(button).forEach { event ->
                val type = event.getValue("type").jsonPrimitive.content
                assertTrue(
                    "\"$type\" is not a ClickEvent.Type serial name, so this file cannot be " +
                        "deserialized; known types: $known",
                    type in known
                )
                checked++
            }
        }
        assertTrue("the default layout must bind at least one click event", checked > 0)
    }

    @Test
    fun everyLayerSwitchAndLauncherEventPointsAtSomethingThatExists() {
        val layerUuids = asset.getValue("layers").jsonArray
            .map { it.jsonObject.getValue("uuid").jsonPrimitive.content }
            .toSet()

        allButtons().forEach { (layerName, button) ->
            clickEventsOf(button).forEach { event ->
                val type = event.getValue("type").jsonPrimitive.content
                val key = event.getValue("key").jsonPrimitive.content
                assertTrue("every click event needs a key, got $event", key.isNotBlank())
                if (type in LAYER_EVENT_TYPES) {
                    assertTrue(
                        "a button in layer $layerName points at layer \"$key\", which does not " +
                            "exist, so the press does nothing",
                        key in layerUuids
                    )
                }
                if (type == "launcher_event") {
                    assertTrue(
                        "\"$key\" is not a launcher event key; declared keys: " +
                            "$LAUNCHER_EVENT_DECLARATIONS",
                        key in LAUNCHER_EVENT_DECLARATIONS
                    )
                }
            }
        }
    }

    // ---- 不得被悄悄换回别的布局 -------------------------------------------

    @Test
    fun theAssetIdentifiesItselfAsTheSuppliedDefault() {
        // 用户提供的那份文件自带署名。钉住它，任何一次"把默认布局换一份别的"都会在这里失败，
        // 而不是让装机的人拿到一份自己没要求过的按键布局。
        val info = asset.getValue("info").jsonObject
        assertEquals("oxide-default", defaultOf(info, "name"))
        assertEquals("oxide-mc", defaultOf(info, "author"))
        assertEquals("least working default control layout", defaultOf(info, "description"))
    }

    // ---- 内置兜底布局 -----------------------------------------------------

    @Test
    fun theEmbeddedFallbackLayoutIsALoadableLayoutWithSomethingOnIt() {
        val text = EMBEDDED_FALLBACK_CONTROL_LAYOUT
        val layout = loadLayoutFromString(text)
        assertTrue("the fallback must have at least one layer", layout.layers.isNotEmpty())
        assertTrue(
            "the fallback must have at least one button: an empty layout is exactly the state " +
                "the fallback exists to avoid",
            layout.layers.sumOf { it.normalButtons.size } > 0
        )
        val version = Json.parseToJsonElement(text)
            .jsonObject.getValue("editorVersion").jsonPrimitive.int
        assertTrue(
            "the fallback declares editorVersion $version, which the current editor " +
                "(${EmptyControlLayout.editorVersion}) would reject",
            version in 1..EmptyControlLayout.editorVersion
        )
    }

    // ---- helpers ---------------------------------------------------------

    /** 所有层里的按钮，元素是 (所属层名, 按钮对象) */
    private fun allButtons(): List<Pair<String, JsonObject>> =
        asset.getValue("layers").jsonArray.flatMap { element ->
            val layer = element.jsonObject
            val layerName = layer.getValue("name").jsonPrimitive.content
            val buttons = layer["normalButtons"]?.jsonArray.orEmpty()
            buttons.map { layerName to it.jsonObject }
        }

    private fun clickEventsOf(button: JsonObject) =
        button["clickEvents"]?.jsonArray.orEmpty().map { it.jsonObject }

    private fun defaultOf(info: JsonObject, key: String) =
        info.getValue(key).jsonObject.getValue("default").jsonPrimitive.content

    /**
     * ClickEvent.Type 的序列化名，全部从真正的序列化器上问出来，而不是在测试里抄一份枚举名：
     * json 里该写的是 @SerialName("switch_layer")，不是 SwitchLayer。
     */
    private fun knownClickEventTypeNames(): Set<String> =
        ClickEvent.Type.entries
            .map { Json.encodeToString(ClickEvent.Type.serializer(), it).trim('"') }
            .toSet()
}
