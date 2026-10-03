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

import dev.oxide.launcher.R
import dev.oxide.launcher.game.version.installed.VersionFolders

/**
 * 一个实例目录下的五类内容
 *
 * 这五块在旧界面里是五块各自独立的整页；新界面把它们收进同一块表面，
 * 用一条分类列切换。因此分类本身必须是**数据**：文件夹、扩展名、显示名
 * 都由这里给出，别处不再自己抄一份。
 *
 * [folder] 就是旧界面各自 `VersionFolders.X.getDir(version.getGameDir())` 得到的目录，
 * 所以指向的仍然是同一批真实文件。
 */
enum class OxideContentCategory(
    val titleRes: Int,
    val folder: VersionFolders,
    /** 导入时接受的扩展名；[isDirectory] 为 true 时这一项为空 */
    val acceptExtension: String,
    /** 这类内容是文件夹而不是压缩包/单个文件 */
    val isDirectory: Boolean = false,
) {
    Mods(R.string.oxide_mgr_cat_mods, VersionFolders.MOD, "jar"),
    ResourcePacks(R.string.oxide_mgr_cat_resource_packs, VersionFolders.RESOURCE_PACK, "zip"),
    Shaders(R.string.oxide_mgr_cat_shaders, VersionFolders.SHADERS, "zip"),
    Saves(R.string.oxide_mgr_cat_saves, VersionFolders.SAVES, "", isDirectory = true),
    Screenshots(R.string.oxide_mgr_cat_screenshots, VersionFolders.SCREENSHOTS, "png"),
    ;

    /**
     * 这个分类里哪些能力是真的
     *
     * 旧界面每一块做的事情并不相同：模组有启用/禁用与更新，资源包可以改名与只看有效，
     * 存档可以快速开档与备份，截图只有查看。凡是这里为 false 的能力，
     * 界面上就不出现那个控件——宁可少一个按钮，也不要留一个点了没反应的。
     */
    val canEnable: Boolean get() = this == Mods

    val canUpdate: Boolean get() = this == Mods

    val canRename: Boolean
        get() = this == ResourcePacks || this == Shaders || this == Saves

    val canQuickPlay: Boolean get() = this == Saves

    val hasValidity: Boolean get() = this == ResourcePacks || this == Saves

    companion object {
        /** 分类列的默认顺序，与参考稿的抽屉标签一致 */
        val defaultOrder: List<OxideContentCategory> = entries.toList()
    }
}

/** 排序依据；与旧界面的 `SortByEnum` 一一对应，只是去掉了这里用不到的那两个 */
enum class OxideContentSort {
    /** 显示名 */
    Name,

    /** 文件名 */
    FileName,

    /** 文件最后修改时间 */
    FileModified,

    /** 存档的上次游玩时间 */
    LastPlayed,
}

/** 模组的状态筛选；其余分类只有全部 */
enum class OxideContentState {
    All,
    Enabled,
    Disabled,
}

/**
 * 列表里的一行
 *
 * 五类内容被压成同一种形状，是为了让筛选、排序、选中这三件事只写一次。
 * [title] 是必有的；其余字段拿不到就留 null，界面上那一行就不画，
 * 绝不拿占位文字补齐。
 */
data class OxideContentEntry(
    /** 稳定键：真实文件名，同一目录里唯一 */
    val key: String,
    val fileName: String,
    val displayName: String,
    /** 次要说明：文件名 + 大小 + 修改时间，或者存档的兼容性说明 */
    val detail: String?,
    /** 右上角的小徽章：加载器、文件名等纯文本 */
    val badge: String?,
    /** 徽章也可以是一句现成的翻译（游戏模式名），这里给出它的资源 id */
    val badgeRes: Int? = null,
    /** 模组的启用态；其余分类恒为 true */
    val enabled: Boolean,
    /** 是否能被勾选。模组里只有能对上远端的才允许，否则其余一律可以 */
    val selectable: Boolean,
    /** 资源包与存档的有效性；其余分类恒为 true */
    val valid: Boolean,
    /** 存档与当前 Minecraft 版本不兼容 */
    val incompatible: Boolean = false,
    /** 极限模式存档；它没有 gameMode，所以单独一个标记而不是复用徽章 */
    val hardcore: Boolean = false,
    /** 文件夹类型的资源包与存档没有可靠的大小，就不给 */
    val size: Long? = null,
    /** 上次游玩时间戳，只有存档有 */
    val lastPlayed: Long? = null,
    /** 文件最后修改时间 */
    val modifiedAt: Long = 0L,
    /** 这一行真正能做的改名；只有有效资源包、zip 光影包与有效存档有 */
    val canRename: Boolean = false,
    /** 远端信息还在读 */
    val loading: Boolean = false,
)

/**
 * 名称搜索
 *
 * 显示名与文件名都参与匹配，和旧界面的行为一致：用户记得的是包名，
 * 但很多资源包显示名和文件名根本不是一回事。
 */
fun oxideContentMatches(entry: OxideContentEntry, query: String): Boolean {
    if (query.isEmpty()) return true
    return entry.displayName.contains(query, ignoreCase = true) ||
        entry.fileName.contains(query, ignoreCase = true) ||
        entry.detail.orEmpty().contains(query, ignoreCase = true)
}

/** 名称搜索 + 状态筛选，两件事都在这里做，界面上不再各写一遍 */
fun filterOxideContentEntries(
    entries: List<OxideContentEntry>,
    query: String,
    state: OxideContentState,
): List<OxideContentEntry> = entries.filter { entry ->
    val nameMatched = oxideContentMatches(entry, query)
    val stateMatched = when (state) {
        OxideContentState.All -> true
        OxideContentState.Enabled -> entry.enabled
        OxideContentState.Disabled -> !entry.enabled
    }
    nameMatched && stateMatched
}

/**
 * 排序
 *
 * [ascending] 为 false 时整体取反，因此"降序"永远是"升序的倒过来"，
 * 不会出现第二次点同一个键就换一套无关的顺序。
 */
fun sortOxideContentEntries(
    entries: List<OxideContentEntry>,
    sort: OxideContentSort,
    ascending: Boolean,
): List<OxideContentEntry> {
    // 类型参数必须写出来：`compareBy` 只在后面那个 lambda 里用到元素类型，
    // 而 when 分支不提供期望类型，Kotlin 无从推断 `it` 是哪一行
    val comparator = when (sort) {
        OxideContentSort.Name ->
            compareBy<OxideContentEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }

        OxideContentSort.FileName ->
            compareBy<OxideContentEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.fileName }

        OxideContentSort.FileModified -> compareBy<OxideContentEntry> { it.modifiedAt }
        OxideContentSort.LastPlayed -> compareBy<OxideContentEntry> { it.lastPlayed ?: it.modifiedAt }
    }
    return entries.sortedWith(if (ascending) comparator else comparator.reversed())
}

/**
 * 这个分类支持哪几个排序键
 *
 * 每一类只给出自己真的排得出来的键：存档多一个"上次游玩"，其余没有。
 * [nextOxideContentSort] 因此不会在界面上排第三个键、列表却按第二个键排。
 */
fun oxideContentSortOptions(category: OxideContentCategory): List<OxideContentSort> =
    when (category) {
        OxideContentCategory.Mods,
        OxideContentCategory.Shaders,
        OxideContentCategory.Screenshots,
        -> listOf(OxideContentSort.Name, OxideContentSort.FileModified)

        OxideContentCategory.ResourcePacks ->
            listOf(OxideContentSort.Name, OxideContentSort.FileModified)

        OxideContentCategory.Saves -> listOf(
            OxideContentSort.Name,
            OxideContentSort.FileName,
            OxideContentSort.LastPlayed,
        )
    }

/**
 * 分类的默认排序键
 *
 * 与旧界面完全一致：模组与光影按文件名升序，资源包与存档按显示名升序。
 * 截图是**唯一的降序**默认值（旧界面 `isAscending = false`），因为用户想看的是
 * 最近拍的那几张。
 */
fun oxideContentDefaultSort(category: OxideContentCategory): Pair<OxideContentSort, Boolean> =
    when (category) {
        OxideContentCategory.Mods,
        OxideContentCategory.Shaders,
        -> OxideContentSort.FileName to true

        OxideContentCategory.Screenshots -> OxideContentSort.Name to false

        OxideContentCategory.ResourcePacks,
        OxideContentCategory.Saves,
        -> OxideContentSort.Name to true
    }

/**
 * 当前排序在某一档下的下一档
 *
 * 在这一分类支持的键之间循环，并且每换一个键就从升序重新开始：因此不会出现
 * 界面上排的是第三个键、列表却按第二个键排的错位。
 */
fun nextOxideContentSort(
    category: OxideContentCategory,
    sort: OxideContentSort,
    ascending: Boolean,
): Pair<OxideContentSort, Boolean> {
    val options = oxideContentSortOptions(category)
    if (options.isEmpty()) return sort to ascending
    val index = options.indexOf(sort)
    // 找不到当前键时把这一档当成"还没设过"，直接从第一个升序开始
    val current = if (index < 0) -1 else index * 2 + if (ascending) 0 else 1
    val next = ((current + 1) % (options.size * 2) + options.size * 2) % (options.size * 2)
    return options[next / 2] to (next % 2 == 0)
}

/** 已启用 / 已禁用的数量，用于筛选器的计数 */
data class OxideContentStateCounts(val all: Int, val enabled: Int, val disabled: Int)

fun oxideContentStateCounts(entries: List<OxideContentEntry>): OxideContentStateCounts =
    OxideContentStateCounts(
        all = entries.size,
        enabled = entries.count { it.enabled },
        disabled = entries.count { !it.enabled },
    )

/**
 * 能进入更新流程的选中项
 *
 * 与旧界面的 `canUpdate` 完全一致：只有勾了、并且自己带得上远端校验的模组才可更新。
 * 禁用的模组同样可以更新——旧界面就是允许的，这里不偷偷改变这条语义。
 * 其余分类返回空列表，因此界面上不会出现一个点了没反应的更新按钮。
 */
fun oxideContentUpdatableSelection(
    category: OxideContentCategory,
    selected: List<OxideContentEntry>,
): List<OxideContentEntry> {
    if (!category.canUpdate) return emptyList()
    return selected.filter { entry -> entry.selectable }
}

/**
 * 存档的行摘要
 *
 * 真正用来判断兼容性的 [dev.oxide.launcher.game.version.saves.SaveData.isCompatible]
 * 需要一个 `SaveData`；这里给出的是**已经算好之后**的那一行，
 * 因此渲染这一行不需要再回到磁盘。
 */
fun oxideContentLastPlayedStamp(entry: OxideContentEntry): Long =
    entry.lastPlayed?.takeIf { it > 0L } ?: entry.modifiedAt

/**
 * 选中项 → 要删除的真实文件名
 *
 * 删除是不可逆的，确认条上写出来的必须是**真实路径末段**而不是行标题：
 * 存档与资源包的显示名经常和文件夹名不是一回事。
 */
fun oxideContentDeleteTargets(selected: List<OxideContentEntry>): List<String> =
    selected.map { it.fileName }.filter { it.isNotBlank() }.distinct()