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

package dev.oxide.launcher.ui.screens.main.oxide

/**
 * 实例列表的排序依据
 *
 * 只有两档，因为后端能给出的可靠时间戳只有"上次运行时间"（最新那份日志的修改时间）：
 * 安装时间需要额外的记账，磁盘修改时间会被装模组改掉，两者都不该拿来排序。
 */
internal enum class OxideInstanceSort {
    /** 名称 */
    Name,

    /** 上次运行时间 */
    RecentActivity,
}

/**
 * 名称搜索
 *
 * 只看名称：用户在启动器里记得住的是"那个 1.20 光影实例叫什么"，不是路径也不是
 * 版本号——后者本来就在名称里。空查询一律匹配，因此清空输入框一定回到完整列表。
 */
internal fun oxideInstanceMatches(name: String, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return name.contains(trimmed, ignoreCase = true)
}

/**
 * 搜索 + 排序
 *
 * 类型参数走投影而不是直接吃 [dev.oxide.launcher.game.version.installed.Version]：
 * 排序规则因此可以在没有磁盘、没有 Hilt、没有协程的单元测试里直接断言。
 *
 * @param lastRunOf 上次运行时间戳；没有日志的返回 0
 */
internal fun <T> filterOxideInstances(
    items: List<T>,
    query: String,
    sort: OxideInstanceSort,
    ascending: Boolean,
    nameOf: (T) -> String,
    lastRunOf: (T) -> Long,
): List<T> {
    val matched = items.filter { item -> oxideInstanceMatches(nameOf(item), query) }
    // 类型必须写出来：when 分支不提供期望类型，Kotlin 无从推断 `it` 是哪一行
    val comparator = when (sort) {
        OxideInstanceSort.Name ->
            compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { item -> nameOf(item) }

        OxideInstanceSort.RecentActivity -> compareBy<T, Long> { item -> lastRunOf(item) }
    }
    return matched.sortedWith(if (ascending) comparator else comparator.reversed())
}

/**
 * 排序的当前取值，以文字写出来
 *
 * 两个键都写出来，并在升降序之间轮转：界面上排的是哪一档必须读得出来，
 * 只用一个箭头符号就分不清"按名字"还是"按最近"。
 */
internal fun nextOxideInstanceSort(
    current: OxideInstanceSort,
    ascending: Boolean,
): Pair<OxideInstanceSort, Boolean> {
    val keys = OxideInstanceSort.entries
    val index = keys.indexOf(current)
    val step = index * 2 + if (ascending) 0 else 1
    val next = ((step + 1) % (keys.size * 2) + keys.size * 2) % (keys.size * 2)
    return keys[next / 2] to (next % 2 == 0)
}

/**
 * 这一行搜索/排序控件是否出现
 *
 * 一个实例都没有时两者都不出现：那时的输入框与排序按钮改不了任何东西，
 * 而空状态那一块已经有一枚"装一个新版本"的主操作了。
 */
internal fun oxideInstanceQueryVisible(installedCount: Int): Boolean = installedCount > 0
