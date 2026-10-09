/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.onigami

/**
 * Retryable GPU probe cache.
 *
 * A live GL query can legitimately come back empty (no context current on
 * this thread yet). Callers therefore retry: [noteProbe] records the first
 * good answers, and the getters hand them out afterwards. Empty or null
 * inputs are never stored and never returned -- an unknown GPU is reported
 * as null by the core getters so auto-tuning cannot act on a fake string.
 * The "<unknown>" text exists only in the UI helpers, never in logic.
 */
object OnigamiProbe {
    @Volatile private var cachedRenderer: String? = null
    @Volatile private var cachedVersion: String? = null
    @Volatile private var fallbackCount: Int = 0

    /** Record a probe; blank inputs are ignored so they cannot poison cache. */
    fun noteProbe(renderer: String?, version: String?) {
        if (!renderer.isNullOrEmpty()) cachedRenderer = renderer
        if (!version.isNullOrEmpty()) cachedVersion = version
    }

    /** Live answer when present, else the cached probe, else null. Never "". */
    fun rendererOrProbe(live: String?): String? {
        if (!live.isNullOrEmpty()) return live
        fallbackCount++
        return cachedRenderer?.takeIf { it.isNotEmpty() }
    }

    /** Live answer when present, else the cached probe, else null. */
    fun versionOrProbe(live: String?): String? {
        if (!live.isNullOrEmpty()) return live
        fallbackCount++
        return cachedVersion?.takeIf { it.isNotEmpty() }
    }

    /** UI-only rendering of [rendererOrProbe]; logic must use the nullable one. */
    fun rendererForUi(live: String?): String = rendererOrProbe(live) ?: "<unknown>"

    /** UI-only rendering of [versionOrProbe]; logic must use the nullable one. */
    fun versionForUi(live: String?): String = versionOrProbe(live) ?: "<unknown>"

    fun isAdreno(renderer: String?): Boolean =
        (renderer ?: cachedRenderer ?: "").contains("adreno", ignoreCase = true)

    fun fallbackCount(): Int = fallbackCount

    /** Test-only reset; the probe is process-scoped in production. */
    fun resetForTest() {
        cachedRenderer = null
        cachedVersion = null
        fallbackCount = 0
    }
}
