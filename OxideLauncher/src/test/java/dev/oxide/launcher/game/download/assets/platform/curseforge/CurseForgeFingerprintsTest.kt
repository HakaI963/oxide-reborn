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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.download.assets.platform.curseforge

import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeFingerprintsMatches
import dev.oxide.launcher.path.GLOBAL_JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指纹批量匹配
 *
 * 匹配结果决定本地模组能不能被认出来（已安装标注、依赖去重、更新检查），
 * 而服务端在没有任何指纹命中时会把 exactMatches 回传为 null，
 * 这个 null 必须收敛成"没有命中"，不能当成解析失败。
 */
class CurseForgeFingerprintsTest {

    private val oneFile = """
        {
          "id": 8888038,
          "gameId": 432,
          "modId": 394468,
          "isAvailable": true,
          "displayName": "sodium-neoforge-0.9.2",
          "fileName": "sodium-neoforge-0.9.2.jar",
          "releaseType": 1,
          "fileStatus": 4,
          "hashes": [
            { "value": "d3b0f1a2c4e5", "algo": 1 },
            { "value": "9f8e7d6c5b4a", "algo": 2 }
          ],
          "fileDate": "2026-01-02T03:04:05.06Z",
          "fileLength": 1208169,
          "downloadCount": 42000,
          "downloadUrl": "https://edge.forgecdn.net/files/8888/38/sodium-neoforge-0.9.2.jar",
          "gameVersions": ["1.21.1", "NeoForge"],
          "sortableGameVersions": [1210100, 1210101],
          "dependencies": [
            { "modId": 306612, "relationType": 3 }
          ],
          "fileFingerprint": 431832863
        }
    """.trimIndent()

    private fun matches(exactMatches: String) = """
        {
          "data": {
            "isCacheBuilt": true,
            "exactMatches": $exactMatches,
            "exactFingerprints": [],
            "partialMatches": null,
            "installedFingerprints": [],
            "unmatchedFingerprints": []
          }
        }
    """.trimIndent()

    private fun parse(json: String) =
        parseExactFingerprintMatches(
            GLOBAL_JSON.decodeFromString(CurseForgeFingerprintsMatches.serializer(), json)
        )

    @Test
    fun `an exact match is keyed by its fingerprint`() {
        val result = parse(
            matches("""[{"id": 1, "file": $oneFile, "latestFiles": [$oneFile]}]""")
        )
        assertEquals(setOf(431832863L), result.keys)
        assertEquals("sodium-neoforge-0.9.2.jar", result.getValue(431832863L).fileName)
    }

    @Test
    fun `several matches are all kept`() {
        val result = parse(
            matches(
                """[
                  {"id": 1, "file": $oneFile, "latestFiles": []},
                  {"id": 2, "file": ${oneFile.replace("431832863", "431832864")}, "latestFiles": []}
                ]"""
            )
        )
        assertEquals(2, result.size)
        assertTrue(result.containsKey(431832863L))
        assertTrue(result.containsKey(431832864L))
    }

    @Test
    fun `a null exact match list means nothing matched and not a failure`() {
        // 服务端对完全没有命中的指纹回传 null，负缓存就是靠这一点建立的
        assertTrue(parse(matches("null")).isEmpty())
    }

    @Test
    fun `an empty exact match list means nothing matched`() {
        assertTrue(parse(matches("[]")).isEmpty())
    }

    @Test
    fun `the request body is the shape the server expects`() {
        val body = GLOBAL_JSON.encodeToString(
            CurseForgeFingerprintsRequest.serializer(),
            CurseForgeFingerprintsRequest(listOf(431832863L, 1L))
        )
        assertEquals("""{"fingerprints":[431832863,1]}""", body)
    }

    @Test
    fun `the endpoint is the versionless fingerprints route`() {
        assertEquals(
            "https://api.curseforge.com/v1/fingerprints",
            CurseForgeEndpoints.fingerprints(CURSEFORGE_API)
        )
    }
}
