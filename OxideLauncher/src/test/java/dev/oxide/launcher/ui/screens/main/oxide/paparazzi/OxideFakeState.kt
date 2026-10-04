/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide.paparazzi

import dev.oxide.launcher.coroutine.Task
import dev.oxide.launcher.coroutine.TaskStage
import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.AccountType
import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformFilterCode
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchData
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.utils.device.VulkanCapabilities

/**
 * Fake state for the screenshot tests.
 *
 * Everything here is built by hand, so no golden depends on a network call, an installed
 * Minecraft directory, an account database, MMKV, sensor hardware, or the clock. Values are
 * literals rather than `stringResource(...)` results on purpose: the chrome around them *should*
 * follow the device locale — that is a real thing worth a golden — while the payload a test seeds
 * should be readable straight out of the PNG when that golden changes.
 *
 * What these builders deliberately do not fake is identity. `Account`'s default `uniqueUUID` calls
 * `UUID.randomUUID()`, so it is always spelled out here: a golden that changed on every run would
 * be worse than no golden at all.
 */
object OxideFake {

    // -----------------------------------------------------------------------
    // Accounts
    // -----------------------------------------------------------------------

    /**
     * An account with a fixed identity.
     *
     * `profileId` is passed explicitly because its default is derived from the username, and one
     * more derived value is one more thing that can drift under us.
     */
    fun account(
        username: String,
        uuid: String = "00000000-0000-4000-8000-000000000001",
        microsoft: Boolean = false,
    ): Account = Account(
        uniqueUUID = uuid,
        username = username,
        profileId = uuid,
        accessToken = "0",
        expiresAt = 0L,
        clientToken = "0",
        refreshToken = "0",
        accountType = if (microsoft) AccountType.MICROSOFT.tag else AccountType.LOCAL.tag,
        skinModelType = SkinModelType.NONE,
    )

    /**
     * Two offline accounts, returned in a fixed order.
     *
     * The pair is what the account list test needs to show both row states at once: one selected
     * (the darker surface plus the radio mark) and one not.
     */
    fun twoAccounts(): List<Account> = listOf(
        account("Steve", uuid = "00000000-0000-4000-8000-000000000001"),
        account("Alex", uuid = "00000000-0000-4000-8000-000000000002"),
    )

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    /**
     * A [Task] that never runs.
     *
     * `Task.runTask` only *constructs* a task; `TaskSystem.submitTask` is what launches it, and the
     * screenshot tests never call that. So this yields a task with a known id, stage, progress and
     * message, with no coroutine, no dispatcher and no `TaskSystem` bookkeeping. The body is an
     * empty suspend lambda that is never invoked, so there is nothing here that could reach the
     * network even by accident.
     */
    fun task(
        id: String,
        progress: Float = 0.42f,
        stage: TaskStage = TaskStage.RUNNING,
        message: String? = null,
        bytesPerSec: Long? = null,
    ): Task = Task.runTask(id = id, task = {}).apply {
        updateProgress(progress)
        updateStage(stage)
        message?.let { updateMessage(androidText(it)) }
        bytesPerSec?.let { updateSpeed(it) }
    }

    // -----------------------------------------------------------------------
    // Vulkan
    // -----------------------------------------------------------------------

    /**
     * A Vulkan capability report with plausible but entirely invented numbers.
     *
     * This is the plain data the checker carries *before* it calls into the native library, so the
     * result dialog renders with no Vulkan driver, no GPU name table and no
     * `libvulkan_checker` shared object. The extension strings are real ones on purpose: the result
     * body groups them against `VulkanRequirements`, and inventing names there would produce a
     * golden for a layout that can never occur.
     *
     * [features] drives the pass/fail halves of the rows; `false` is the interesting case because
     * a report where everything is supported exercises none of the failure styling.
     */
    fun vulkanCapabilities(
        features: Map<String, Boolean> = mapOf(
            "shaderDrawParameters" to true,
            "geometryShader" to false,
            "textureCompressionASTC_LDR" to false,
            "dynamicRendering" to true,
        ),
    ): VulkanCapabilities = VulkanCapabilities(
        apiVersionMajor = 1,
        apiVersionMinor = 3,
        apiVersionPatch = 268,
        extensions = listOf(
            "VK_KHR_swapchain",
            "VK_EXT_queue_family_foreign",
            "VK_KHR_surface",
            "VK_KANDROID_swapchain",
        ),
        features = features,
    )

    // -----------------------------------------------------------------------
    // Discover
    // -----------------------------------------------------------------------

    /**
     * A [PlatformSearchData] backed by nothing.
     *
     * `DiscoverResultCard` asks the platform model for an icon URL, an author and a download count.
     * A *null* icon URL is the honest version of "there is no image": it keeps the golden from
     * depending on how many times Coil decides to retry a request that can never succeed, which
     * would otherwise make the result grid flicker between runs.
     */
    fun searchData(
        id: String,
        title: String,
        author: String = "someone",
        description: String = "",
        downloads: Long = 12_345_678L,
        platform: Platform = Platform.CURSEFORGE,
    ): PlatformSearchData = object : PlatformSearchData {
        override fun platform(): Platform = platform
        override fun platformId(): String = id
        override fun platformTitle(): String = title
        override fun platformDescription(): String = description
        override fun platformAuthor(): String = author
        override fun platformIconUrl(): String? = null
        override fun platformDownloadCount(): Long = downloads
        override fun platformFollows(): Long? = null
        override fun platformModLoaders(): List<PlatformDisplayLabel>? = null
        override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? = null
    }

    /** The five fixed result titles the Discover goldens use. Order is part of the golden. */
    fun modTitles(): List<String> = listOf(
        "Sodium",
        "Lithium",
        "Fabric API",
        "Sodium Extra",
        "ImmediatelyFast",
    )

    /** Same titles across five ids, one per platform kind, so each row has a distinct identity. */
    fun modProjectIds(): List<String> =
        modTitles().mapIndexed { index, _ -> "project-${index + 1}" }
}