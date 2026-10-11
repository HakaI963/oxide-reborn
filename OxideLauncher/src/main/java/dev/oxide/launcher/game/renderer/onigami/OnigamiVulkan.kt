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

import dev.oxide.launcher.game.version.installed.GraphicsApi

/**
 * ONIGAMI (Vulkan) selection policy.
 *
 * Pure launcher-side rules so every renderer picker (global settings and
 * per-instance settings, old and new UI) applies the same behavior and the
 * rules stay unit-testable: ONIGAMI requires the Vulkan graphics API.
 * Renderer and graphics-API storage are untouched here; callers persist.
 */
object OnigamiVulkan {
    /** Dialog title; tracks the renderer display name. */
    val DIALOG_TITLE: String = OnigamiIdentity.NAME
    const val DIALOG_MESSAGE: String = "ONIGAMI requires Vulkan. Switch the Graphics API to Vulkan?"
    const val DIALOG_CONFIRM: String = "Switch to Vulkan"
    const val DIALOG_DISMISS: String = "Cancel"

    fun isOnigami(uniqueId: String): Boolean = uniqueId == OnigamiIdentity.UNIQUE_ID

    /** Whether picking this renderer must also move the graphics API to Vulkan. */
    fun requiresVulkan(uniqueId: String): Boolean = isOnigami(uniqueId)

    /** Whether picking [uniqueId] while [current] is stored must ask to switch. */
    fun needsVulkanSwitch(uniqueId: String, current: GraphicsApi): Boolean =
        requiresVulkan(uniqueId) && current != GraphicsApi.VULKAN
}
