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

package dev.oxide.launcher.game.renderer.copperoxide

/**
 * Honest capability detection for Copper Oxide.
 *
 * Nothing here claims support for a feature that is not implemented.
 * Extension checks are substring-safe (no prefix false-positives), GL ES
 * version resolution prefers the renderer's own id and only falls back to
 * the EGL probe, and the EGL probe result is cached per process so the
 * driver is initialized once instead of once per launch.
 */
object CopperOxideCapabilities {
    /**
     * Substring-safe extension check (mirrors the EGL/GL convention).
     * A match must end at the string end or at a space, so "GL_EXT_foo"
     * never matches "GL_EXT_foobar".
     */
    fun hasExtension(extensions: String, name: String): Boolean {
        if (extensions.isEmpty() || name.isEmpty()) return false
        var start = extensions.indexOf(name)
        while (start >= 0) {
            val end = start + name.length
            if (end == extensions.length || extensions[end] == ' ') return true
            start = extensions.indexOf(name, end)
        }
        return false
    }

    /**
     * Resolve the GLES major version without spoofing.
     *
     * - "opengles2..." pins 2 (Holy GL4ES compat path).
     * - "opengles3..." (incl. Copper Oxide) pins 3.
     * - Anything else uses the EGL probe; <3 falls back to 2 (app minimum),
     *   probe failures (<0) fall back to 3 for GLES-capable backends.
     * Pure and unit-tested; the EGL probe itself lives in GameLauncher.
     */
    fun resolveGlesMajor(rendererId: String, detected: Int): Int {
        if (rendererId.startsWith("opengles2")) return 2
        if (rendererId.startsWith("opengles")) {
            // Suffix forms like "opengles3_oxide_copper": the leading digit
            // after the prefix is authoritative when present.
            val suffix = rendererId.removePrefix("opengles")
            val digit = suffix.firstOrNull { it.isDigit() }
            if (digit != null) return digit.digitToInt().coerceIn(2, 3)
            return 3
        }
        if (detected < 0) return 3
        return if (detected < 3) 2 else 3
    }

    /** String form for LIBGL_ES. */
    fun resolveLibGlEs(rendererId: String, detected: Int): String =
        resolveGlesMajor(rendererId, detected).toString()

    /**
     * Per-process cache for the expensive EGL probe (eglInitialize +
     * eglGetConfigs + eglTerminate loads the driver). First launch probes,
     * later launches reuse. Reset only in tests.
     */
    object EglProbeCache {
        @Volatile private var cached: Int? = null

        fun getOrProbe(probe: () -> Int): Int {
            cached?.let { return it }
            val value = probe()
            cached = value
            return value
        }

        fun resetForTest() {
            cached = null
        }

        fun peekForTest(): Int? = cached
    }

    /**
     * Human-readable capability line for logs. Reports what was detected,
     * never what is wished for.
     */
    fun reportLine(rendererId: String, detected: Int): String {
        val resolved = resolveGlesMajor(rendererId, detected)
        return "Copper Oxide caps: id=" + rendererId +
            " eglProbe=" + detected + " gles=" + resolved
    }
}
