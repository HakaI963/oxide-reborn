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

package dev.oxide.launcher.game.renderer.renderers

import dev.oxide.launcher.game.renderer.RendererInterface

/**
 * LTW (Large Thin Wrapper) builtin renderer.
 *
 * LTW is a thin OpenGL core-to-OpenGL ES wrapper by MojoLauncher (LGPL-3.0).
 * The native library is vendored from the LTW `output-aar` CI artifact
 * (see THIRD_PARTY.md). Wiring mirrors MojoLauncher's `LTWRenderSpec`:
 * GLES 3, EGL provided by the system (no renderer EGL override), and no
 * version floor upstream — device support is a GLES 3 runtime check, not a
 * Minecraft version gate.
 *
 * The renderer id and the `OXIDE_RENDERER_FLAVOR` variable live in an
 * Oxide-specific namespace so that no other launcher selects this pipeline.
 * That namespacing is a selection contract, not a lockout.
 */
object LTWRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_oxide_ltw"

    override fun getUniqueIdentifier(): String = "179855cc-0cf7-47e0-86c4-866ab9ca6d0d"

    override fun getRendererName(): String = "LTW"

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = "26.3"

    // 展示版本号默认继承 getMaxMCVersion()，两处必须同源。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3",
            "OXIDE_RENDERER_FLAVOR" to "ltw"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libltw.so"
}
