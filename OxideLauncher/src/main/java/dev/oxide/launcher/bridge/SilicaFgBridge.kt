/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.bridge

/**
 * JNI bridge to the Silica own-FG engine (libsilica_fg.so).
 *
 * All configuration is explicit (no env dependence): the game process calls
 * these at startup and on every settings change. Best-effort everywhere;
 * false means "not active", never a crash.
 */
object SilicaFgBridge {
    private fun load(): Boolean =
        runCatching { System.loadLibrary("silica_fg"); true }.getOrDefault(false)

    private external fun nativeConfigure(
        dataDir: String, enabled: Boolean, mult: Int, guard: Boolean
    ): Boolean

    private external fun nativeReloadTranslation(dataDir: String): Boolean

    /** Push FG state into the engine. Runs in any process; inert without swaps. */
    fun configure(dataDir: String, enabled: Boolean, mult: Int, guard: Boolean): Boolean =
        runCatching { load() && nativeConfigure(dataDir, enabled, mult, guard) }
            .getOrDefault(false)

    /**
     * Reload libonigami translation config from dataDir/onigami.json.
     * The launcher otherwise never calls the native lifecycle, which used to
     * leave every native knob dormant at defaults.
     */
    fun reloadTranslation(dataDir: String): Boolean =
        runCatching { load() && nativeReloadTranslation(dataDir) }.getOrDefault(false)

    /**
     * Export env into THIS process (game process heals what the launcher
     * process env never reaches: bridge gating, native dirs, win-fg layer).
     * Returns how many entries stuck.
     */
    fun exportEnv(env: Map<String, String>): Int {
        var n = 0
        for ((k, v) in env) {
            if (runCatching { android.system.Os.setenv(k, v, true); true }.getOrDefault(false)) n++
        }
        return n
    }
}
