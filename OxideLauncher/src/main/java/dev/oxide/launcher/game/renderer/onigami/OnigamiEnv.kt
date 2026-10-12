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
 * Onigami launch environment.
 *
 * Only keys that libonigami.so itself reads are emitted here. Until the
 * native backend renders, the values are still recorded in the launch log so
 * setup can be verified without a working frame.
 */
object OnigamiEnv {
    const val KEY_LIBGL_ES = "LIBGL_ES"
    const val KEY_FLAVOR = "OXIDE_RENDERER_FLAVOR"
    const val KEY_DATA_DIR = "ONIGAMI_DATA_DIR"
    const val KEY_MODE = "ONIGAMI_MODE"
    const val KEY_FRAME_GEN = "ONIGAMI_FRAME_GEN"
    const val KEY_FRAME_GEN_MULT = "ONIGAMI_FRAME_GEN_MULT"
    const val KEY_LSFG_MULT = "LSFG_MULTIPLIER"
    const val KEY_HOOK = "ONIGAMI_PRESENT_HOOK"

    fun baseEnv(mode: String): Map<String, String> = mapOf(
        KEY_LIBGL_ES to "3",
        KEY_FLAVOR to OnigamiIdentity.FLAVOR,
        KEY_MODE to mode,
    )

    fun dataDirEnv(privateDir: String): Map<String, String> {
        if (privateDir.isBlank()) return emptyMap()
        return mapOf(KEY_DATA_DIR to privateDir)
    }

    fun frameGenEnv(enabled: Boolean, multiplier: Int): Map<String, String> {
        if (!enabled) return emptyMap()
        val m = if (multiplier == 2) "2" else "2"
        return mapOf(KEY_FRAME_GEN to "1", KEY_FRAME_GEN_MULT to m, KEY_LSFG_MULT to m)
    }

    /** Present hook: always on for ONIGAMI so swaps are counted even with FG off. */
    fun hookEnv(): Map<String, String> = mapOf(KEY_HOOK to "1")

    fun merged(mode: String, privateDir: String): Map<String, String> =
        baseEnv(mode) + dataDirEnv(privateDir) + hookEnv()

    fun merged(mode: String, privateDir: String, frameGen: Boolean, multiplier: Int): Map<String, String> =
        merged(mode, privateDir) + frameGenEnv(frameGen, multiplier)
}
