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

package dev.oxide.launcher.game.renderer.silica

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Silica EGL error-propagation contract (device-proven regression test).
 *
 * Failure shape: SDL's eglCreateContext failed on every attempt (requested,
 * no-KHR retry, GLES2 fallback) yet the reported EGL error was EGL_SUCCESS,
 * so RenderPearl/Iris died with "reporting an error of EGL_SUCCESS" and then
 * crashed on missing GLCapabilities. Root cause was Silica's own context
 * wrapper: it read the backend eglGetError for a log line, but Silica does
 * not export eglGetError (the host owns the display path), so the consumed
 * flag was lost and the application's own read saw SUCCESS.
 *
 * Contract pinned here, against the real native source:
 * - the context wrappers never call the backend eglGetError (no consumption);
 * - every refusal logs decoded arguments and states the flag stays queued;
 * - a missing backend entry is reported as missing, never as a refusal.
 */
class SilicaEglContractTest {

    private fun eglSource(): String = locate(
        "src/main/cpp/silica/src/egl/exports.cpp"
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

    /** Strip strings and comments so assertions see code, not prose. */
    private fun code(source: String): String = source
        .replace(Regex("\"(?:[^\"\\]|\\.)*\""), "\"\"")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("//[^\n]*"), "")

    @Test
    fun wrappersNeverConsumeTheBackendErrorFlag() {
        val body = code(eglSource())
        assertFalse(
            "resolving backend eglGetError eats the flag the app must read",
            body.contains("resolve(\"eglGetError\")"),
        )
        assertFalse(
            "rearm-style helpers must not return to this file",
            Regex("\\brearm\\s*\\(").containsMatchIn(body),
        )
        assertFalse(
            "no direct backend eglGetError() call may remain in wrapper paths",
            Regex("\\beglGetError\\s*\\(\\s*\\)").containsMatchIn(body),
        )
    }

    @Test
    fun refusalsLogDecodedArgsAndLeaveTheFlagQueued() {
        // The decoder is code; the queued-flag promise lives inside log
        // strings, so it is checked against the raw source, not the stripped
        // code (stripping removes string contents by design).
        val body = code(eglSource())
        val raw = eglSource()
        assertTrue(
            "context refusals must carry decoded version/profile arguments",
            body.contains("describe_ctx_attribs"),
        )
        assertTrue(
            "refusal logs must state the backend flag stays queued for the app",
            raw.contains("left queued"),
        )
    }

    @Test
    fun missingBackendEntryIsNotReportedAsRefusal() {
        // Phrasing lives in the log strings: check the raw source.
        assertTrue(
            "a null backend entry must be distinguished from a backend refusal",
            eglSource().contains("has no backend entry"),
        )
    }
}
