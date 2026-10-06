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

package dev.oxide.launcher.ui.screens.game.elements.log_parser

import androidx.compose.ui.graphics.Color

/**
 * 日志等级识别规则
 * @param identifiers 所有可识别的标识符
 * @param color 文本颜色
 * @param backgroundColor 背景颜色，可不设置
 *
 * 四条规则一律是**白字压在实色徽章上**，这是刻意保持的一致：
 * 等级颜色是徽章给的，不是字给的。一旦某条规则不带徽章，它的字就必须自己
 * 在 [dev.oxide.launcher.ui.theme.Oxide.PopoverBg] 上读得清，而那个底色跟着主题
 * 翻面（深色 0xFA0D0D0D，浅色 0xFFFFFFFF），写字面颜色就等于赌主题——
 * 原来的 `ERROR` 就是这样写的：`0xFF6AAB73` 无徽章，在浅色底幕上只有 2.7:1，
 * 在深色底幕上却有 7.1:1，于是同一份日志在两种主题里是两个可读性。
 * 徽章是不透明的，白字在徽章上的对比度与主题无关，两边都对。
 */
data class LogLevelRule(
    val identifiers: List<String>,
    val textColor: Color,
    val backgroundColor: Color? = null
)

val INFO = LogLevelRule(
    identifiers = listOf("INFO", "Info"),
    textColor = Color.White,
    backgroundColor = Color(0xFF447152)
)

val ERROR = LogLevelRule(
    identifiers = listOf("ERROR", "Error"),
    // 原来这里是 `0xFF6AAB73` 且不带徽章：它和 `stringColor` 是同一个绿，
    // 于是既分不出"字符串"与"错误"，又在浅色底幕上只剩 2.7:1。
    // 换成与另外三条同构的白字实色徽章后是 5.4:1，且不再依赖主题；
    // 亮度取在白字能过 4.5:1、徽章自己在近黑底幕上又能过 3:1 的那一段里。
    textColor = Color.White,
    backgroundColor = Color(0xFFC43438)
)

val DEBUG = LogLevelRule(
    identifiers = listOf("DEBUG", "Debug"),
    textColor = Color.White,
    backgroundColor = Color(0xFF43698D)
)

val WARN = LogLevelRule(
    identifiers = listOf("WARN", "Warn"),
    textColor = Color.White,
    backgroundColor = Color(0xFF656E76)
)