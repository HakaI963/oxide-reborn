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
 * HARD RULE: Silica NEVER loads libcopperoxide.so / libmobileglues.so and never
 * forwards GL calls to them. Its native backend is libsilica.so built from
 * OxideLauncher/src/main/cpp/silica/ (own EGL/context, entry layer, state
 * tracking, shader pipeline, program cache, framebuffers, textures, buffers,
 * uniforms, sync, presentation). Copper Oxide / MobileGlues source in
 * cpp/copperoxide/ is REFERENCE ONLY for studying the Minecraft contract.
 * The upstream 26.2 dev black-screen fix (MobileGlues-plugin 5974f49 ->
 * MobileGlues fcdf914 + 8bcf28a) is ANALYZED in SILICA.md and its equivalent
 * behavior is implemented in Silica's own code, never copied. Until the Silica
 * native is functional, selecting Silica fails loudly at load with a clear log
 * instead of silently falling back to another backend. Copper Oxide code,
 * binaries and LGPL notices MUST stay until no derived code remains.
 *
 * GL vendor/renderer strings inside Minecraft come from the driver and are
 * never spoofed. No FPS numbers are claimed without on-device measurement.
 */
object SilicaIdentity {
    const val RENDERER_ID: String = "opengles3_oxide_silica"
    const val UNIQUE_ID: String = "7c1e5a2b-9d3f-4e11-8a2c-silica000001"
    const val NAME: String = "Silica"
    const val FLAVOR: String = "silica"
    // Own native backend. No fallback to any other renderer's library, ever.
    const val NATIVE_LIBRARY: String = "libsilica.so"
    const val EGL_LIBRARY: String = "libsilica.so"
    const val MIN_MC_VERSION: String = "1.17"
    const val MAX_MC_VERSION: String = "26.3"
    const val DATA_DIR_NAME: String = "silica"

    fun isSilicaId(rendererId: String): Boolean = rendererId == RENDERER_ID

    fun summary(): String =
        "Silica: Oxide-integrated OpenGL renderer (own native backend, under construction). " +
            "Capabilities detected, never spoofed."

    fun logLine(): String = NAME + " (" + FLAVOR + " / " + RENDERER_ID + ")"
}
