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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.layercontroller.layout

import dev.oxide.layercontroller.EDITOR_VERSION
import dev.oxide.layercontroller.data.ButtonStyle
import dev.oxide.layercontroller.data.JoystickStyle
import dev.oxide.layercontroller.data.lang.EmptyTranslatableString
import dev.oxide.layercontroller.data.lang.TranslatableString
import dev.oxide.layercontroller.data.legacy.LegacySpecial
import dev.oxide.layercontroller.layout.ControlLayout.Info
import dev.oxide.layercontroller.observable.Modifiable
import dev.oxide.layercontroller.observable.isModified
import dev.oxide.layercontroller.updateLayoutToNew
import dev.oxide.layercontroller.utils.layoutJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 描述一个控制布局结构
 * @param info 控制布局基本信息
 * @param layers 控制层级列表
 * @param editorVersion 使用编辑器版本
 */
@Serializable
data class ControlLayout(
    @SerialName("info")
    val info: Info,
    @SerialName("layers")
    val layers: List<ControlLayer> = emptyList(),
    @SerialName("styles")
    val styles: List<ButtonStyle> = emptyList(),
    @SerialName("joystickStyles")
    val joystickStyles: List<JoystickStyle> = emptyList(),
    @SerialName("editorVersion")
    val editorVersion: Int
): Modifiable<ControlLayout> {
    @Serializable
    data class Info(
        @SerialName("name")
        val name: TranslatableString,
        @SerialName("author")
        val author: TranslatableString,
        @SerialName("description")
        val description: TranslatableString,
        @SerialName("versionCode")
        val versionCode: Int,
        @SerialName("versionName")
        val versionName: String
    ): Modifiable<Info> {
        override fun isModified(other: Info): Boolean {
            return other.name.isModified(this.name) ||
                    other.author.isModified(this.author) ||
                    other.description.isModified(this.description) ||
                    this.versionCode != other.versionCode ||
                    this.versionName != other.versionName
        }
    }

    override fun isModified(other: ControlLayout): Boolean {
        return this.info.isModified(other.info) ||
                this.layers.isModified(other.layers) ||
                this.styles.isModified(other.styles) ||
                this.joystickStyles.isModified(other.joystickStyles) ||
                this.editorVersion != other.editorVersion
    }
}

val EmptyLayoutInfo = Info(
    name = EmptyTranslatableString,
    author = EmptyTranslatableString,
    description = EmptyTranslatableString,
    versionCode = 0,
    versionName = ""
)

val EmptyControlLayout = ControlLayout(
    editorVersion = EDITOR_VERSION,
    info = EmptyLayoutInfo,
    layers = emptyList(),
    styles = emptyList(),
    joystickStyles = emptyList(),
)

/**
 * 从文件加载控制布局配置（检查版本号，大于编辑器版本则抛出`IllegalArgumentException`）
 */
fun loadLayoutFromFile(file: File): ControlLayout {
    val jsonString = file.readText()
    return loadLayoutFromString(jsonString)
}

fun loadLayoutFromString(jsonString: String): ControlLayout {
    val jsonObject = layoutJson.decodeFromString<JsonObject>(jsonString)
    if (jsonObject["editorVersion"] == null) throw SerializationException("The file does not contain the key \"editorVersion\".")
    val version = jsonObject["editorVersion"]!!.jsonPrimitive.int
    if (version <= EDITOR_VERSION) {
        val legacyJoystickStyle = if (version < 12) {
            jsonObject["special"]?.let { specialElement ->
                //在反序列化前尝试提取旧版 special 字段
                try {
                    layoutJson.decodeFromJsonElement(LegacySpecial.serializer(), specialElement).joystickStyle?.toJoystickStyle()
                } catch (_: Exception) {
                    null
                }
            }
        } else null

        var layout = layoutJson.decodeFromString<ControlLayout>(jsonString)
        if (version < EDITOR_VERSION) layout = updateLayoutToNew(layout)

        if (legacyJoystickStyle != null) {
            //迁移至新版摇杆样式列表
            layout = layout.copy(
                joystickStyles = layout.joystickStyles + legacyJoystickStyle
            )
        }

        return layout
    } else {
        throw IllegalArgumentException("Control layout versions are not supported!")
    }
}

/**
 * 从文件加载控制布局配置（不检查版本号）
 */
fun loadLayoutFromFileUncheck(file: File): ControlLayout {
    val jsonString = file.readText()
    return layoutJson.decodeFromString<ControlLayout>(jsonString)
}