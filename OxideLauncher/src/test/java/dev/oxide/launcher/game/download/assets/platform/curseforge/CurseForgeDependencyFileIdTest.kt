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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.download.assets.platform.curseforge

import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CurseForgeDependencyFileIdTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 启动器把 CurseForge 的响应缓存在磁盘上。fileId 是后加的字段，
     * 早先缓存下来的 JSON 里没有它——如果字段没有默认值，反序列化会直接抛，
     * 于是所有用旧缓存的用户一进依赖列表就崩。
     */
    @Test
    fun dependencyWithoutFileIdStillDecodes() {
        val decoded = json.decodeFromString<CurseForgeFile.Dependency>(
            """{"modId":238222,"relationType":"required"}"""
        )
        assertEquals(238222, decoded.modId)
        assertEquals(0, decoded.fileId)
        assertEquals(PlatformDependencyType.REQUIRED, decoded.relationType)
    }

    @Test
    fun dependencyWithFileIdDecodes() {
        val decoded = json.decodeFromString<CurseForgeFile.Dependency>(
            """{"modId":238222,"fileId":4711,"relationType":"required"}"""
        )
        assertEquals(238222, decoded.modId)
        assertEquals(4711, decoded.fileId)
    }

    /**
     * fileId <= 0 一律当作"作者没指定"，退回按游戏版本挑最新版。
     * 上游偶尔会给 0，映射成 versionId="0" 会去查一个不存在的文件。
     */
    @Test
    fun nonPositiveFileIdMeansUnspecified() {
        listOf(0, -1).forEach { raw ->
            val d = json.decodeFromString<CurseForgeFile.Dependency>(
                """{"modId":1,"fileId":$raw,"relationType":"optional"}"""
            )
            assertEquals(
                "fileId=$raw must not become a versionId",
                null,
                d.fileId.takeIf { it > 0 }?.toString(),
            )
        }
    }
}