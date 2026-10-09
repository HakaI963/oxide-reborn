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
        "cpp/silica/src/egl/exports.cpp"
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
    private fun code(source: String): String {
        // Plain scanner, no regex escapes: strings, // lines and /* blocks
        // are blanked so assertions see code, not prose.
        val out = StringBuilder()
        var i = 0
        var inStr = false
        var inLine = false
        var inBlock = 0
        while (i < source.length) {
            val c = source[i]
            if (inLine) {
                if (c == '\n') { inLine = false; out.append(c) }
            } else if (inBlock > 0) {
                if (c == '*' && i + 1 < source.length && source[i + 1] == '/') { inBlock--; i++ }
            } else if (inStr) {
                if (c == '\\' && i + 1 < source.length) i++
                else if (c == '"') inStr = false
            } else if (c == '/' && i + 1 < source.length && source[i + 1] == '/') {
                inLine = true
            } else if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                inBlock = 1
            } else if (c == '"') {
                inStr = true
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    @Test
    fun theBackendErrorIsCapturedOnceIntoAQueueTheAppActuallyReads() {
        // The application resolves eglGetError through POJAVEXEC_EGL, which is
        // libsilica for this renderer, so the exported read below is the one the
        // app performs. That is why a read that only logged and dropped the code
        // reported EGL_SUCCESS forever: the real code went nowhere the app looked.
        val body = code(eglSource())
        val raw = eglSource()
        assertTrue(
            "a real backend code must be captured after a failed call",
            body.contains("capture_backend_error"),
        )
        assertTrue(
            "the queue must be per-thread, as EGL error state is",
            Regex("thread_local\\s+EGLint\\s+g_frontend_error").containsMatchIn(raw),
        )
        assertTrue(
            "eglGetError must be exported, or the app never sees the queued code",
            Regex("S_API\\s+EGLint\\s+eglGetError\\s*\\(").containsMatchIn(raw),
        )
        assertTrue(
            "the exported read must serve the queued code to the application",
            body.contains("g_frontend_error"),
        )
    }

    @Test
    fun refusalsLogDecodedArgumentsAndTheRealCode() {
        val body = code(eglSource())
        val raw = eglSource()
        assertTrue(
            "context refusals must carry decoded version/profile arguments",
            body.contains("describe_ctx_attribs"),
        )
        assertTrue(
            "a refusal must report the real backend code, not a generic one",
            raw.contains("egl_error_name"),
        )
        assertTrue(
            "both the requested and the sent attribute lists must be logged",
            raw.contains("requested=") && raw.contains("sent="),
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

    private fun hookSource(): String = locate(
        "jni/sdl_hook.c"
    ).readText()

    @Test
    fun hookProxyNeverReadsTheBackendErrorFlag() {
        // The SDL retry proxy sits between the app and the backend on the
        // context path. A single eglGetError here would consume the flag and
        // forge EGL_SUCCESS for the application's own read, exactly like the
        // wrapper bug above. Attrib tracing must carry the diagnostics alone.
        val body = code(hookSource())
        org.junit.Assert.assertFalse(
            "proxyEglCreateContext must not call eglGetError",
            body.contains("eglGetError"),
        )
    }

    @Test
    fun hookProxyTracesOriginAndAttempts() {
        val raw = hookSource()
        org.junit.Assert.assertTrue(
            "origin of the backend entry must be logged via dladdr",
            raw.contains("SILICA_EGL_DIAG origin"),
        )
        org.junit.Assert.assertTrue(
            "requested attempt must log decoded attribs",
            raw.contains("SILICA_EGL_DIAG entry"),
        )
        org.junit.Assert.assertTrue(
            "retries must log decoded attribs and results",
            raw.contains("SILICA_EGL_DIAG attempt="),
        )
    }

    @Test
    fun desktopContextRequestsAreTranslatedToEsBeforeReachingTheBackend() {
        // Device-proven cause: RenderPearl/Iris ask for a desktop core-profile
        // context (PROFILE_MASK + FLAGS present) on a GLES-only device, and the
        // backend refuses it. Dropping only the KHR version attributes did not
        // help, which is why the retries all failed identically.
        val body = code(eglSource())
        assertTrue(
            "a desktop request must be detected from profile mask / desktop flags",
            body.contains("analyze_ctx"),
        )
        assertTrue(
            "a desktop request must be rewritten as an ES request",
            body.contains("build_es_request"),
        )
        assertTrue(
            "the ES API must be bound before the context is created",
            Regex("eglBindAPI|bind\\(EGL_OPENGL_ES_API\\)").containsMatchIn(body),
        )
        // Ordering is the whole point: binding after creation is too late.
        val raw = eglSource()
        val bindAt = raw.indexOf("EGL_OPENGL_ES_API")
        val createAt = raw.indexOf("f(dpy, cfg, share, backend_attr)")
        assertTrue(
            "eglBindAPI(EGL_OPENGL_ES_API) must precede the backend create call",
            bindAt > 0 && createAt > 0 && bindAt < createAt,
        )
    }

    @Test
    fun esContextRequestsArePassedThroughUnchanged() {
        // The vanilla path must not change: only genuinely desktop requests are
        // rewritten, so a plain EGL_CONTEXT_CLIENT_VERSION list still goes
        // through byte-for-byte.
        val body = code(eglSource())
        assertTrue(
            "the sent attributes must default to what the caller asked for",
            body.contains("backend_attr = attr"),
        )
        assertTrue(
            "only a desktop classification may switch to the translated list",
            body.contains("if (req.desktop)"),
        )
    }

    @Test
    fun desktopRenderableTypeMapsToEs3NotEs2() {
        // Root cause of the Iris EGL_BAD_MATCH, proven against the working
        // reference: it maps a desktop EGL_OPENGL_BIT request onto
        // EGL_OPENGL_ES3_BIT (0x0040), never ES2. Downgrading to ES2_BIT here
        // produced an ES2-only config, and the CLIENT_VERSION=3 context that
        // followed was refused against it -- as was the plain ES2 retry, because
        // the config was the thing that could not match.
        val body = code(hookSource())
        assertFalse(
            "the compat retry must not hand SDL an ES2-only config",
            Regex("\\|\\s*EGL_OPENGL_ES2_BIT\\s*;").containsMatchIn(body),
        )
        assertTrue(
            "a desktop renderable request must become ES3",
            Regex("\\|\\s*EGL_OPENGL_ES3_BIT\\s*;").containsMatchIn(body),
        )
    }

    @Test
    fun aConfigWithoutEs3IsReAskedRatherThanAccepted() {
        val body = code(hookSource())
        assertTrue(
            "config ES3 support must be read from the config, not assumed",
            body.contains("configSupportsEs3"),
        )
        assertTrue(
            "the read must use EGL_RENDERABLE_TYPE",
            Regex("0x3040").containsMatchIn(body),
        )
        assertTrue(
            "an ES2-only config must trigger a re-ask, not silent acceptance",
            body.contains("ES3 re-ask") || body.contains("re-asked for an ES3-capable config"),
        )
    }
}
