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

import dev.oxide.launcher.game.renderer.silica.SilicaEnv
import dev.oxide.launcher.setting.AllSettings

/**
 * JNI side of the Silica own-FG engine (linked into libpojavexec).
 *
 * Direction is pull, not push: native calls these getters (~1Hz) from the
 * game thread, so settings apply in every process layout with no timing or
 * env dependence. Best-effort everywhere; defaults mean "off".
 */
object SilicaFgBridge {
    private external fun nativeBind(): Boolean

    /**
     * Bind JNI getters for the engine. Must run in the game process
     * (GameScreen). Idempotent.
     */
    fun bind(): Boolean = runCatching {
        System.loadLibrary("pojavexec")
        nativeBind()
    }.getOrDefault(false)

    @JvmStatic
    fun engineDataDir(): String =
        runCatching { SilicaEnv.dataDir().absolutePath }.getOrDefault("")

    @JvmStatic
    fun engineEnabled(): Boolean =
        runCatching { AllSettings.silicaFgEnabled.getValue() }.getOrDefault(false)

    @JvmStatic
    fun engineMult(): Int =
        runCatching { AllSettings.silicaFgMult.getValue() }.getOrDefault(2)

    /**
     * Export env into THIS process (heals what launcher env never reaches,
     * e.g. for the win-fg Vulkan path). Returns how many entries stuck.
     */
    fun exportEnv(env: Map<String, String>): Int {
        var n = 0
        for ((k, v) in env) {
            if (runCatching { android.system.Os.setenv(k, v, true); true }.getOrDefault(false)) n++
        }
        return n
    }
}
