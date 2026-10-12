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

    fun baseEnv(mode: String): Map<String, String> = mapOf(
        KEY_LIBGL_ES to "3",
        KEY_FLAVOR to OnigamiIdentity.FLAVOR,
        KEY_MODE to mode,
    )

    fun dataDirEnv(privateDir: String): Map<String, String> {
        if (privateDir.isBlank()) return emptyMap()
        return mapOf(KEY_DATA_DIR to privateDir)
    }

    fun merged(mode: String, privateDir: String): Map<String, String> =
        baseEnv(mode) + dataDirEnv(privateDir)
}
