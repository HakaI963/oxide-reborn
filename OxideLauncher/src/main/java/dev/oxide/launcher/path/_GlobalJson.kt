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

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 配置。
 *
 * 单独放在一个文件里，是为了让"用同一份配置解析响应"这件事能被单元测试直接引用，
 * 而不必连带初始化 [GLOBAL_CLIENT] 所在的那个文件。
 *
 * `coerceInputValues` 很关键：平台偶尔会把本该是数组的字段回传为 null，
 * 开启后这类 null 会被强制成默认值，而不是让整页结果解析失败。
 */
val GLOBAL_JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    coerceInputValues = true
}
