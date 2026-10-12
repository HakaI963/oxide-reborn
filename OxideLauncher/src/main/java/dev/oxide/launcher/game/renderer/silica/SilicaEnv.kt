/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.silica

import dev.oxide.launcher.path.PathManager
import java.io.File

/**
 * Silica launch environment + win-fg activation package.
 *
 * Translation keys reuse the ONIGAMI schema (libonigami.so reads them) with
 * SILICA_DATA_DIR pointing at the silica dir. Frame generation rides the
 * win-fg Vulkan layer: env for init values, conf.toml (hot-reloaded live by
 * the layer, no restart) for live changes, and a manifest + VK_ADD_LAYER_PATH
 * so the loader can discover the bundled layer.
 */
object SilicaEnv {
    const val KEY_LIBGL_ES = "LIBGL_ES"
    const val KEY_FLAVOR = "OXIDE_RENDERER_FLAVOR"
    const val KEY_DATA_DIR = "SILICA_DATA_DIR"
    const val KEY_MODE = "SILICA_MODE"

    const val KEY_FG_ENABLE = "WIN_FG_ENABLE"
    const val KEY_FG_DISABLE = "WIN_FG_DISABLE"
    const val KEY_FG_MULT = "WIN_FG_MULT"
    const val KEY_FG_MODEL = "WIN_FG_MODEL"
    const val KEY_FG_PERF = "WIN_FG_PERF_PRESET"
    const val KEY_FG_PACING = "WIN_FG_PACING"
    const val KEY_FG_CONF = "WIN_FG_CONF"
    const val KEY_FG_STATS = "WIN_FG_STATS_DIR"
    const val KEY_LAYER_PATH = "VK_ADD_LAYER_PATH"

    const val MANIFEST_NAME = "VkLayer_win_framegen.json"
    const val CONF_NAME = "conf.toml"
    const val LAYERS_DIR = "layers"

    fun dataDir(): File = File(PathManager.DIR_FILES_PRIVATE, SilicaIdentity.DATA_DIR)

    fun layersDir(): File = File(dataDir(), LAYERS_DIR)

    fun confFile(): File = File(dataDir(), CONF_NAME)

    fun baseEnv(mode: String, dir: String): Map<String, String> = mapOf(
        KEY_LIBGL_ES to "3",
        KEY_FLAVOR to SilicaIdentity.FLAVOR,
        KEY_MODE to mode,
        KEY_DATA_DIR to dir,
        // NOTE: libonigami.so reads ONIGAMI_DATA_DIR, not SILICA_DATA_DIR.
        // Same value, mapped key: separate dir, no state collision.
        "ONIGAMI_DATA_DIR" to dir,
    )

    fun fgEnv(dir: String, enabled: Boolean, mult: Int, model: Int, perf: Int, pacing: Boolean): Map<String, String> {
        val m = mapOf(
            KEY_FG_MULT to mult.coerceIn(2, 4).toString(),
            KEY_FG_MODEL to model.coerceIn(3, 4).toString(),
            KEY_FG_PERF to perf.coerceIn(0, 2).toString(),
            KEY_FG_PACING to if (pacing) "on" else "off",
            KEY_FG_CONF to confFile().absolutePath,
            KEY_FG_STATS to dir,
            KEY_LAYER_PATH to layersDir().absolutePath,
        )
        return if (enabled) m + mapOf(KEY_FG_ENABLE to "1") else m + mapOf(KEY_FG_DISABLE to "1")
    }

    fun merged(mode: String, dir: String, enabled: Boolean, mult: Int, model: Int, perf: Int, pacing: Boolean): Map<String, String> =
        baseEnv(mode, dir) + fgEnv(dir, enabled, mult, model, perf, pacing)

    /** Layer manifest with an absolute library path (robust to loader CWD). Pure, testable. */
    fun manifestJson(winfgSoPath: String): String =
        "{\"file_format_version\":\"1.2.0\",\"layer\":{" +
            "\"name\":\"" + SilicaIdentity.WINFG_LAYER + "\"," +
            "\"type\":\"GLOBAL\"," +
            "\"library_path\":\"" + winfgSoPath + "\"," +
            "\"api_version\":\"1.1.0\"," +
            "\"implementation_version\":\"1\"," +
            "\"description\":\"win-fg frame generation for Silica (MIT)\"," +
            "\"library_arch\":\"64\"," +
            "\"functions\":{\"vkNegotiateLoaderLayerInterfaceVersion\":\"vkNegotiateLoaderLayerInterfaceVersion\"}," +
            "\"enable_environment\":{\"WIN_FG_ENABLE\":\"1\"}," +
            "\"disable_environment\":{\"WIN_FG_DISABLE\":\"1\"}}}"

    /** win-fg conf.toml (their TOML-lite key=value). Pure, testable. */
    fun confToml(enabled: Boolean, mult: Int, model: Int, perf: Int, pacing: Boolean): String =
        "enabled=" + (if (enabled) "1" else "0") + "\n" +
            "multiplier=" + mult.coerceIn(2, 4) + "\n" +
            "model=" + model.coerceIn(3, 4) + "\n" +
            "perf_preset=" + perf.coerceIn(0, 2) + "\n" +
            "pacing=" + (if (pacing) "on" else "off") + "\n"
}
