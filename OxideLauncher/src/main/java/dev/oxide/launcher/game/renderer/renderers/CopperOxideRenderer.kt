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
 * Copper Oxide, the default builtin renderer.
 *
 * This is Oxide's tuned build of MobileGlues, the OpenGL-on-OpenGL-ES
 * implementation by MobileGL-Dev (LGPL-2.1). Oxide does not own this project.
 * The native library is vendored from the MobileGlues-plugin dev CI artifact
 * (see THIRD_PARTY.md); the environment below mirrors the upstream plugin
 * manifest's `pojavEnv` (`LIBGL_ES`, `POJAVEXEC_EGL`/`LIBGL_EGL`,
 * `MG_COUNT_LAUNCH`). `POJAV_RENDERER` is intentionally not repeated here:
 * GameLauncher always sets it to [getRendererId].
 *
 * The renderer id and the `OXIDE_RENDERER_FLAVOR` variable live in an
 * Oxide-specific namespace so that no other launcher selects this pipeline.
 * That namespacing is a selection contract, not a lockout: the underlying
 * library stays LGPL-2.1 and the plugin ABI is untouched.
 */
object CopperOxideRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_oxide_copper"

    override fun getUniqueIdentifier(): String = "52a0f58e-1694-4d47-9ce6-5fa0894413a7"

    override fun getRendererName(): String = "Copper Oxide"

    override fun getMinMCVersion(): String = "1.17"

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = "26.3"

    // 展示版本号默认继承 getMaxMCVersion()，两处必须同源。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3",
            "LIBGL_EGL" to "libmobileglues.so",
            "MG_COUNT_LAUNCH" to "1",
            "OXIDE_RENDERER_FLAVOR" to "copper-oxide"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libmobileglues.so"

    override fun getRendererEGL(): String = "libmobileglues.so"
}
