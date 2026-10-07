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
import dev.oxide.launcher.path.PathManager

/**
 * Mojo Zink builtin renderer.
 *
 * Zink backend by MojoLauncher (LGPL-3.0): desktop OpenGL on Vulkan via
 * mesa's zink gallium driver. The environment below mirrors MojoLauncher's
 * `MesaRenderSpec.ZinkRenderSpec` (`MESA_LOADER_DRIVER_OVERRIDE=zink` with
 * the 4.6 version overrides and a shader cache dir). In Mojo's own stack
 * LWJGL loads its GL symbols from the preloaded EGL library (their dlopen
 * hook substitutes `libGLMojo.so` with the renderspec EGL handle); this
 * builtin wires the equivalent directly: both the LWJGL library and the EGL
 * library are Mojo's `libEGL_mesa.so`, vendored from MojoLauncher CI
 * (see THIRD_PARTY.md).
 *
 * Native dispatch (`egl_bridge.c`) routes this id through the GL bridge
 * with Vulkan preloaded and `GALLIUM_DRIVER=zink`, mirroring the tree's
 * proven mesa/zink path. It deliberately does NOT use the OSMesa bridge:
 * that path aborts unless the library exports `OSMesa*` symbols, which a
 * mesa EGL build does not.
 *
 * The renderer id and the `OXIDE_RENDERER_FLAVOR` variable live in an
 * Oxide-specific namespace so that no other launcher selects this pipeline.
 * That namespacing is a selection contract, not a lockout.
 */
object MojoZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "oxide_vulkan_zink"

    override fun getUniqueIdentifier(): String = "5941f9a1-e7a7-4b0c-be9d-1119317100e2"

    override fun getRendererName(): String = "Mojo Zink"

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = "26.3"

    // 展示版本号默认继承 getMaxMCVersion()，两处必须同源。
    // Mojo 上游没有版本下限（只有 hasMesa + Vulkan 的运行时检查），这里同样不限。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "MESA_GLSL_CACHE_DIR" to PathManager.DIR_CACHE.absolutePath,
            "OXIDE_RENDERER_FLAVOR" to "mojo-zink"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libEGL_mesa.so"

    override fun getRendererEGL(): String = "libEGL_mesa.so"
}
