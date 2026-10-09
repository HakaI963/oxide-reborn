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
 * Oxide <-> Onigami pipeline seams.
 *
 * Ownership split: the launcher owns process lifetime, surfaces and work
 * submission; Onigami owns how desktop GL calls are carried out on top of
 * OpenGL ES. These interfaces pin the boundary so the native backend can be
 * built in stages. Anything without a measured backend stays a contract, not
 * a toggle.
 *
 * Shader-heavy mod pipelines (e.g. Iris-style packs) keep working because
 * the path stays GL-on-ES end to end; there is no Vulkan-only leg. Stages
 * that cost more than they save stay off until frame-time data says otherwise.
 */
interface OnigamiGameLifecycle {
    fun onProcessStart()
    fun onSurfaceAvailable()
    fun onSurfaceLost()
    fun onProcessEnd()
}

interface OnigamiEglContext {
    /** Build the backend context; failures must be logged, never swallowed. */
    fun createContext(display: Any?, attributes: Map<String, Int>): Result<Any?>
    fun makeCurrent(display: Any?, draw: Any?, read: Any?, context: Any?): Boolean
    fun lastErrorRearmed(): Int
}

interface OnigamiSwapchain {
    fun onSurfaceChanged(width: Int, height: Int)
    fun beginFrame(): Boolean
    fun endFrame(): Boolean
}

interface OnigamiRenderScheduler {
    fun submit(draw: () -> Unit)
    fun flush()
}

/** Explicit lifetime for GPU objects; nothing survives a lost context. */
interface OnigamiResources {
    fun acquireFramebuffer(id: Int)
    fun releaseFramebuffer(id: Int)
    fun acquireProgram(id: Int)
    fun releaseProgram(id: Int)
    fun acquireTexture(id: Int)
    fun releaseTexture(id: Int)
    fun onContextLost()
}

/** Bounded program/shader binary store with enforced eviction. */
interface OnigamiMemoryCache {
    fun maxBytes(): Long
    fun evictIfNeeded()
    fun clear()
}

/** Optional below-native rendering plus spatial upscale, cost-gated. */
interface OnigamiResolution {
    /** 1.0 means native. Anything lower must cut total GPU cost or stay off. */
    fun renderScale(): Float
    fun upscaleEnabled(): Boolean
}

/** Presentation smoothing. Reports pacing only; invents no FPS. */
interface OnigamiFramePacer {
    fun targetFrameTimeMs(): Float?
}

/** Synthetic frames: capability-gated, optional, off unless proven faster. */
interface OnigamiFrameGeneration {
    fun supported(): Boolean
    fun reason(): String
    fun enabled(): Boolean
}

/** Whether a runtime key applies live or needs a restart. */
enum class OnigamiApplyPolicy { LIVE, RESTART_REQUIRED }

interface OnigamiControls {
    fun policy(key: String): OnigamiApplyPolicy
}
