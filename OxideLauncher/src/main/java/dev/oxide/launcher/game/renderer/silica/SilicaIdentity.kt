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
 * Silica is Oxide's own OpenGL/OpenGL-ES renderer project, designed around the
 * Oxide Launcher environment instead of every launcher generically. It is NOT
 * a rename of MobileGlues/Copper Oxide: the pipeline in this package (lifecycle,
 * EGL/context, surface/swapchain, scheduling, framebuffer/program/texture
 * lifetime, sync, memory/cache, resolution/upscaling, pacing, optional frame
 * generation, runtime controls) is new Oxide-owned architecture.
 *
 * Phase 1 (1.13.0) honesty note: the GL translation backend is still the proven
 * libcopperoxide.so binary while the Silica pipeline, safety behavior and
 * controls are built around it. The upstream 26.2 dev black-screen fix
 * (MobileGlues fcdf914 + 8bcf28a: null-safe GL_RENDERER/GL_VERSION probe
 * fallback, EGL failure logging with error rearm, ANGLE half-load guard) is
 * ported into the native tree Silica drives (see SILICA.md). An independent
 * Silica native backend is future work; until it exists Copper Oxide code,
 * binaries and LGPL notices MUST stay (see THIRD_PARTY.md).
 *
 * GL vendor/renderer strings inside Minecraft come from the driver and are
 * never spoofed. No FPS numbers are claimed without on-device measurement.
 */
object SilicaIdentity {
    const val RENDERER_ID: String = "opengles3_oxide_silica"
    const val UNIQUE_ID: String = "7c1e5a2b-9d3f-4e11-8a2c-silica000001"
    const val NAME: String = "Silica"
    const val FLAVOR: String = "silica"
    // Phase 1: proven translation backend. Future: libsilica.so.
    const val NATIVE_LIBRARY: String = "libcopperoxide.so"
    const val EGL_LIBRARY: String = "libcopperoxide.so"
    const val MIN_MC_VERSION: String = "1.17"
    const val MAX_MC_VERSION: String = "26.3"
    const val DATA_DIR_NAME: String = "silica"

    fun isSilicaId(rendererId: String): Boolean = rendererId == RENDERER_ID

    fun summary(): String =
        "Silica: Oxide-integrated OpenGL renderer (phase 1 pipeline + proven backend). " +
            "Capabilities detected, never spoofed."

    fun logLine(): String = NAME + " (" + FLAVOR + " / " + RENDERER_ID + ")"
}
