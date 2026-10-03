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

package dev.oxide.launcher.utils.logging

private const val REDACTED = "***"

/**
 * 常见的凭证字段名
 *
 * 同时覆盖 JSON 写法（`"accessToken": "..."`）、表单写法（`access_token=...`）
 * 和命令行写法（`--password ...`）。
 */
private const val SENSITIVE_KEYS =
    "access_?token|refresh_?token|client_?token|identity_?token|rps_?ticket|" +
            "password|passwd|pwd|authorization|x-api-key|device_?code|user_?code|token"

/** 引号包裹或任意非空白取值 */
private const val VALUE = "(\"[^\"]*\"|'[^']*'|[^\\s,;&}\"]+)"

/** `key: value` / `key=value`，只替换取值部分 */
private val KEY_VALUE_PATTERN: Regex =
    Regex("(?i)([\"']?(?:" + SENSITIVE_KEYS + ")[\"']?\\s*[:=]\\s*)" + VALUE)

/** `--key value`，只替换取值部分 */
private val FLAG_PATTERN: Regex =
    Regex("(?i)(--(?:" + SENSITIVE_KEYS + ")[=\\s]+)" + VALUE)

/** `Authorization: Bearer <token>` 形式的取值 */
private val BEARER_PATTERN: Regex = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+")

/** 控制字符，避免服务端内容伪造换行或日志分隔符 */
private val CONTROL_CHARS_PATTERN: Regex = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")

/**
 * 移除文本中可能出现的凭证内容，并限制长度
 *
 * HTTP 异常的 message 往往带有服务端响应体，认证服务器的响应体也可能回显用户输入，
 * 因此写入日志或展示给用户之前必须先脱敏，同时限制长度，避免日志被服务端内容淹没。
 *
 * @param raw 原始文本
 * @param maxLength 保留的最大长度，超出部分以省略号结尾
 */
fun redactSensitive(raw: String?, maxLength: Int = 200): String {
    if (raw.isNullOrEmpty()) return ""

    // 顺序很重要：先处理 `Authorization: Bearer <token>`，否则 key=value 规则会先把 Bearer
    // 当成取值替换掉，导致真正的令牌留在文本里。
    var text = FLAG_PATTERN.replace(raw, "$1$REDACTED")
    text = BEARER_PATTERN.replace(text, "$1$REDACTED")
    text = KEY_VALUE_PATTERN.replace(text, "$1$REDACTED")
    text = CONTROL_CHARS_PATTERN.replace(text, "")

    return if (text.length > maxLength) text.take(maxLength) + "…" else text
}

/** 只保留异常类型名与脱敏后的 message（原始 message 可能带有响应体） */
fun Throwable.redactedMessage(maxLength: Int = 200): String =
    "${this::class.simpleName}: ${redactSensitive(this.message, maxLength)}"