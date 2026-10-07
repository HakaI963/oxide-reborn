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
 * Holy GL4ES builtin renderer.
 *
 * Holy GL4ES is a GL4ES fork maintained by FCL-Team (MIT). No Holy-GL4ES
 * source exists under the MojoLauncher organization, so the vendored binary
 * comes from FCL's own renderer plugin build while the source of record is
 * FCL-Team/Holy-GL4ES (see THIRD_PARTY.md for the substitution).
 *
 * Wiring mirrors the GL4ES-class builtins this replaces: GLES 2 with the
 * classic `LIBGL_*` compatibility set, EGL provided by the system, GL served
 * through the `opengles` native bridge. The compatibility variables are set
 * here explicitly so the wiring does not depend on id-prefix special cases
 * elsewhere in the launch path.
 *
 * Note: upstream FCL caps this backend at 1.21.4. Oxide admits 26.3 per the
 * release plan; launching newer versions on this backend is unverified.
 *
 * The renderer id and the `OXIDE_RENDERER_FLAVOR` variable live in an
 * Oxide-specific namespace so that no other launcher selects this pipeline.
 * That namespacing is a selection contract, not a lockout.
 */
object HolyGL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles2_oxide_holy"

    override fun getUniqueIdentifier(): String = "93083c36-6e9c-479f-a8f8-c5cb59c032f9"

    override fun getRendererName(): String = "Holy GL4ES"

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = "26.3"

    // 展示版本号默认继承 getMaxMCVersion()，两处必须同源。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NORMALIZE" to "1",
            "OXIDE_RENDERER_FLAVOR" to "holy-gl4es"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libholy_gl4es.so"
}
