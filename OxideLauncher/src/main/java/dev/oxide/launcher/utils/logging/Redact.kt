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

package dev.oxide.launcher.utils.logging

private const val REDACTED = "***"

/** 常见的凭证字段名，日志脱敏时按键名匹配 */
private val SENSITIVE_KEYS = listOf(
    "access_token", "accesstoken", "refresh_token", "refreshtoken", "client_token",
    "clienttoken", "password", "passwd", "pwd", "authorization", "x-api-key",
    "device_code", "devicecode", "user_code", "usercode", "session", "identitytoken",
    "rps_ticket", "rpsticket", "token"
)

/**
 * 移除文本中可能出现的凭证内容
 *
 * HTTP 异常的 message 往往带有服务端响应体，认证服务器的响应体也可能回显用户输入，
 * 因此写入日志或展示给用户之前必须先脱敏，同时限制长度，避免日志被服务端内容淹没。
 *
 * @param raw 原始文本
 * @param maxLength 保留的最大长度，超出部分以省略号结尾
 */
fun redactSensitive(raw: String?, maxLength: Int = 200): String {
    if (raw.isNullOrEmpty()) return ""

    var text = raw
    for (key in SENSITIVE_KEYS) {
        text = text.replace(Regex("(?i)(\"$key\"\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,;&\\s}]+)") { m ->
            m.groupValues[1] + REDACTED
        })
        text = text.replace(Regex("(?i)(--$key[= ])([^\\s]+)"), "$1$REDACTED")
    }

    // 去掉控制字符，避免服务端内容伪造终端换行或日志分隔符
    text = text.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

    return if (text.length > maxLength) text.take(maxLength) + "…" else text
}

/** 只保留异常类型名，不包含 message（异常 message 可能带有响应体） */
fun Throwable.redactedMessage(maxLength: Int = 200): String =
    "${this::class.simpleName}: ${redactSensitive(this.message, maxLength)}"
