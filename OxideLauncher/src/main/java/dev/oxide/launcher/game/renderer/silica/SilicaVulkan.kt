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

import dev.oxide.launcher.game.version.installed.GraphicsApi

/**
 * Silica selection policy.
 *
 * Pure launcher-side rules so every renderer picker applies the same
 * behavior: Silica frame generation needs the Vulkan graphics API (the
 * win-fg layer hooks the game's own Vulkan swapchain, present only when
 * Minecraft itself renders Vulkan, i.e. 26.2+). Translation works regardless.
 */
object SilicaVulkan {
    val DIALOG_TITLE: String = SilicaIdentity.NAME
    const val DIALOG_MESSAGE: String = "Silica frame generation needs Vulkan (Minecraft 26.2+ renders its own Vulkan swapchain there). Switch the Graphics API to Vulkan?"
    const val DIALOG_CONFIRM: String = "Switch to Vulkan"
    const val DIALOG_DISMISS: String = "Cancel"

    fun isSilica(uniqueId: String): Boolean = uniqueId == SilicaIdentity.UNIQUE_ID

    fun requiresVulkan(uniqueId: String): Boolean = isSilica(uniqueId)

    fun needsVulkanSwitch(uniqueId: String, current: GraphicsApi): Boolean =
        requiresVulkan(uniqueId) && current != GraphicsApi.VULKAN
}
