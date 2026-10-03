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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.path

/** CurseForge REST API 主机 */
const val HOST_CURSEFORGE_API = "api.curseforge.com"

/** CurseForge 文件 CDN 域名后缀 */
const val CURSEFORGE_CDN_SUFFIX = "forgecdn.net"

/** CurseForge 要求的鉴权请求头名称 */
const val CURSEFORGE_API_KEY_HEADER = "x-api-key"

/**
 * 该主机是否属于 CurseForge。
 *
 * 只认 API 主机本身和 CDN 域名的子域，`mod.mcimirror.top` 之类的镜像源不在此列，
 * 镜像源本来也不需要这个头。
 */
fun isCurseForgeHost(host: String): Boolean =
    host == HOST_CURSEFORGE_API ||
            host == CURSEFORGE_CDN_SUFFIX ||
            host.endsWith(".$CURSEFORGE_CDN_SUFFIX")

/**
 * 计算发往 [host] 时需要附加的 CurseForge 鉴权头。
 *
 * 只在目标是 CurseForge 主机且密钥非空时返回鉴权头，其余情况返回空列表：
 * 密钥绝不能泄漏到第三方主机上；密钥为空时返回空列表，
 * 这样调用方可以明确知道"请求不会带上密钥"，而不是以为已经带上。
 */
fun curseForgeAuthHeaders(host: String, apiKey: String): List<Pair<String, String>> =
    if (isCurseForgeHost(host) && apiKey.isNotBlank()) {
        listOf(CURSEFORGE_API_KEY_HEADER to apiKey)
    } else {
        emptyList()
    }
