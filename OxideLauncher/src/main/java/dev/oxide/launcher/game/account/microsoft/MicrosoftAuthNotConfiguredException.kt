/*
 * Oxide Launcher
 * Copyright (C) 2025 Star1xr and contributors
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

package dev.oxide.launcher.game.account.microsoft

import dev.oxide.launcher.R
import dev.oxide.launcher.utils.AndroidStringText
import dev.oxide.launcher.utils.androidText

/**
 * 这个构建里没有配置微软应用（客户端）id
 *
 * 缺的是**配置**，不是网络，也不是用户输错了什么：设备码端点会以 `invalid_client`
 * 拒绝这次请求，而那条错误对用户毫无意义——他们没法在手机里修好它。
 * 所以单独一个异常类型，把它和"网络失败""账号被封"这类可以重试的情况分开，
 * 并且直接告诉用户该怎么修。
 */
class MicrosoftAuthNotConfiguredException : RuntimeException(
    "This build has no Microsoft application (client) id"
) {
    /**
     * 面向用户的说明：说清楚缺了什么、去哪里补
     */
    fun toLocal(): AndroidStringText = androidText(R.string.account_microsoft_not_configured)
}