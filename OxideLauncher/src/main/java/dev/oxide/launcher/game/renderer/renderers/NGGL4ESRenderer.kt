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

package dev.oxide.launcher.game.renderer.renderers

import dev.oxide.launcher.game.renderer.RendererInterface

object NGGL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3"

    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"

    override fun getRendererName(): String = "Krypton Wrapper"

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = "26.3"

    // 展示版本号默认继承 getMaxMCVersion()，这里曾经钉死在 "26.2"，于是设置页会显示
    // "<= 26.2" 而闸门其实放行 26.3——两处必须同源。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        buildMap {
            put("LIBGL_USE_MC_COLOR", "1")
            put("LIBGL_GL", "31")
            put("LIBGL_ES", "3")
            put("LIBGL_NORMALIZE", "1")
            put("LIBGL_NOERROR", "1")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"
}
