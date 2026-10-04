/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.download.assets.platform.curseforge.models

import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformFilterCode
import dev.oxide.launcher.game.download.assets.platform.PlatformProject
import dev.oxide.launcher.game.download.assets.platform.UnsupportedClassesException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgeProject(
    @SerialName("data")
    val data: CurseForgeData
): PlatformProject {
    override fun platform(): Platform = Platform.CURSEFORGE

    override fun platformId(): String = data.id.toString()

    override fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses {
        return data.getPlatformClassesOrNull() ?: defaultClasses
    }

    override fun platformSlug(): String = data.slug

    override fun platformIconUrl(): String? = data.logo?.url

    override fun platformTitle(): String = data.name

    override fun platformSummary(): String = data.summary

    // 接口约定返回可空：作者列表为空的项目在 CurseForge 上是存在的，
    // 取 [0] 会直接抛 IndexOutOfBounds，而不是像约定那样返回 null
    override fun platformAuthor(): String? = data.authors.firstOrNull()?.name

    override fun platformAuthors(): List<String> = data.platformAuthors()

    override fun platformDownloadCount(): Long = data.downloadCount

    override fun platformFollows(): Long? = null

    override fun platformAvailable(): Boolean = data.isApproved()

    override fun platformModLoaders(): List<PlatformDisplayLabel>? {
        return data.platformModLoaders()
    }

    override fun checkClasses() {
        val classId = data.classId
        if (CurseForgeClassID.entries.none { id -> classId == id.classID }) throw UnsupportedClassesException(classId)
    }

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
        return data.platformCategories(classes)
    }

    override fun platformUrls(defaultClasses: PlatformClasses): PlatformProject.Urls {
        val classes = data.getPlatformClassesOrNull() ?: defaultClasses
        return PlatformProject.Urls(
            projectUrl = "https://www.curseforge.com/minecraft/${classes.curseforge.slug}/${data.slug}",
            sourceUrl = data.links.sourceUrl,
            issuesUrl = data.links.issuesUrl,
            wikiUrl = data.links.wikiUrl
        )
    }

    override fun platformScreenshots(): List<PlatformProject.Screenshot> {
        return data.screenshots.map { asset ->
            PlatformProject.Screenshot(
                imageUrl = asset.url,
                title = asset.title,
                description = asset.description
            )
        }
    }
}

/**
 * @return 该项目是否可见
 */
fun CurseForgeProject.isApproved(): Boolean = this.data.isApproved()