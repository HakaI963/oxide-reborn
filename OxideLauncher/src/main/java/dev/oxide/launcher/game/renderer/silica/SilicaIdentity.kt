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

/**
 * Silica product identity.
 *
 * Silica is ONIGAMI-class translation (shared libonigami.so core, separate
 * silica data dir so configs never collide) plus win-fg frame generation
 * (vendored Vulkan layer libwin_fg.so, MIT) on the game's own Vulkan
 * swapchain path (Minecraft 26.2+). Where no Vulkan swapchain exists Silica
 * runs as pure translation and says so.
 */
object SilicaIdentity {
    const val RENDERER_ID: String = "opengles3_oxide_silica"
    const val UNIQUE_ID: String = "9c4e1b7a-3f2d-4a6e-8d1c-silica000001"
    const val NAME: String = "Silica (Vulkan FG)"
    const val FLAVOR: String = "silica"

    /** Shared translation core (refcounted; silica/data-dir separation avoids state collision). */
    const val NATIVE_LIBRARY: String = "libonigami.so"

    /** win-fg Vulkan implicit layer (own .so, own manifest, own env). */
    const val WINFG_LIBRARY: String = "libwin_fg.so"
    const val WINFG_LAYER: String = "VK_LAYER_WIN_framegen"

    const val MIN_MC: String = "1.17"
    const val MAX_MC: String = "26.3"

    /** Data dir name under the launcher private files dir. */
    const val DATA_DIR: String = "silica"

    fun summary(): String =
        "Silica (Vulkan FG): ONIGAMI-class translation plus win-fg optical-flow " +
            "frame generation on the game's Vulkan swapchain (Minecraft 26.2+). " +
            "Without a Vulkan swapchain it runs as pure translation."

    fun logLine(): String = NAME + " (" + FLAVOR + " / " + RENDERER_ID + ")"
}
