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
 * Onigami product identity.
 *
 * Onigami is an Oxide-owned OpenGL-ES translation renderer with its own native
 * backend (libonigami.so); it does not load, wrap, or fall back to any other
 * renderer's native library.
 *
 * Strings shown inside Minecraft (vendor/renderer/version) always come from
 * the driver. This module never rewrites them, and it makes no frame-rate
 * claims without on-device measurement.
 */
object OnigamiIdentity {
    const val RENDERER_ID: String = "opengles3_oxide_onigami"
    const val UNIQUE_ID: String = "8d2f6b1a-4c7e-4a90-9e5f-onigami000001"
    const val NAME: String = "Onigami"
    const val FLAVOR: String = "onigami"

    /** Sole native backend. No fallback to any other library, ever. */
    const val NATIVE_LIBRARY: String = "libonigami.so"

    const val MIN_MC: String = "1.17"
    const val MAX_MC: String = "26.3"

    /** Data dir name under the launcher private files dir. */
    const val DATA_DIR: String = "onigami"

    fun isOnigamiId(rendererId: String): Boolean = rendererId == RENDERER_ID

    fun summary(): String =
        "Onigami: Oxide-integrated OpenGL renderer (own native backend, under construction). " +
            "Capabilities detected, never spoofed."

    fun logLine(): String = NAME + " (" + FLAVOR + " / " + RENDERER_ID + ")"
}
