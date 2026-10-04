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

import dev.oxide.launcher.game.version.mod.ModLoaderVerdict

/**
 * 模组列表的纯逻辑
 *
 * 这个文件刻意不 import 任何 Compose、Android 或磁盘的东西。原因是这里每一条
 * 都是"上一次真实缺陷"固化下来的规则，因此必须能在普通 JVM 单测里逐条钉死：
 *
 *  - **启用/禁用之后必须能再启用回来**：键取 [OxideModRow.key]，也就是去掉
 *    `.disabled` 之后的文件名。任何拿"上一次读到的文件名"当键的地方，
 *    都会在第一次改名之后对不上自己——那正是"禁用能成、再启用没反应"的成因。
 *  - **删除必须打到磁盘上此刻真实的那个路径**：[OxideModRow.path] 在每一次
 *    重新扫描之后从文件系统重新读出来，而不是从某一份行快照里抄。
 *  - **哪些字段在有值时出现、在没值时不出现**：[oxideModsMeta]。
 *
 * 行本身是**快照**：组合期只读它，不再碰磁盘。需要新的一批快照时由 ViewModel
 * 在 IO 上重扫一次。
 */

// ---------------------------------------------------------------------------
// 一行
// ---------------------------------------------------------------------------

/**
 * 模组列表里的一行
 *
 * 字段全部来自真实后端，没有任何一个是界面上拼出来的占位值；拿不到就是 null，
 * 界面上那一条整个不画。
 *
 * @param key 稳定键：去掉 `.disabled` 之后的文件名。同一个模组在启用与禁用两种
 *   状态下共用一个键，因此改名（启用/禁用就是改名）不会换掉这一行的身份。
 * @param path 磁盘上此刻真实的绝对路径。删除与改名的目标都必须是它。
 * @param fileName 此刻真实的文件名；禁用态带 `.disabled` 后缀。
 */
data class OxideModRow(
    val key: String,
    val path: String,
    val fileName: String,
    val displayName: String,
    val modId: String = "",
    val modVersion: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val enabled: Boolean = true,
    /** 这个文件根本不是模组（压缩包读不出模组元数据） */
    val notMod: Boolean = false,
    /** 从模组自己的元数据里读出来的加载器显示名 */
    val localLoader: String? = null,
    /** 平台给这个文件标注的加载器显示名 */
    val declaredLoaders: List<String> = emptyList(),
    /** 目标实例自己的 Minecraft 版本 */
    val instanceMinecraftVersion: String? = null,
    /** 目标实例自己的加载器显示名 */
    val instanceLoaders: List<String> = emptyList(),
    val verdict: ModLoaderVerdict = ModLoaderVerdict.NoInstanceLoader,
    // ---- 远端信息（`RemoteMod.load` 之后才有）--------------------------------
    val platform: String? = null,
    val projectId: String? = null,
    val projectTitle: String? = null,
    val projectSlug: String? = null,
    val projectIconUrl: String? = null,
    /** 平台上的文件（版本）id；详情层靠它取回依赖信息 */
    val remoteVersionId: String? = null,
    val remoteDatePublished: String? = null,
    /** 本地文件里的图标字节（`LocalMod.icon`） */
    val iconBytes: ByteArray? = null,
    /** 远端项目信息还在读 */
    val loadingRemote: Boolean = false,
    /** 这个模组带得上远端校验，因此可进入更新流程 */
    val checkRemote: Boolean = true,
) {
    /** 图标优先用项目的封面，没有项目封面才用本地元数据里的图标 */
    val hasIcon: Boolean
        get() = !projectIconUrl.isNullOrBlank() || iconBytes != null

    /** 真正能进更新流程的行 */
    val updatable: Boolean
        get() = checkRemote && !projectId.isNullOrBlank() && verdict.loadable
}

// ---------------------------------------------------------------------------
// 元数据行
// ---------------------------------------------------------------------------

/** 元数据行上出现的字段种类 */
enum class OxideModMetaField {
    FileName,
    FileSize,
    Version,
    Author,
    Loader,
    GameVersion,
    Compatibility,
}

/** 元数据行上的一行：[field] 是字段名（界面去查标签），[value] 是它的值 */
data class OxideModMeta(
    val field: OxideModMetaField,
    val value: String,
)

/**
 * 元数据行的字段名
 *
 * 由界面在组合期读一次再传进来，因此这个函数本身是纯的、可单测的。
 */
data class OxideModMetaLabels(
    val fileName: String,
    val fileSize: String,
    val version: String,
    val author: String,
    val loader: String,
    val gameVersion: String,
    val compatible: String,
    val loaderless: String,
    val noInstanceLoader: String,
    val mismatched: String,
    val unknownFile: String,
)

/**
 * 这一行要展示哪些元数据
 *
 * 规则只有一条：**拿不到就不画**。空版本、不认识的作者、一个读不出模组元数据的
 * jar（`notMod`）都只会少掉对应的那几行，而不是留下一句"未知"或者一个假的大小。
 *
 * 兼容性那一行由 [ModLoaderVerdict] 决定，四种结论各有各的说法，
 * 因此"没法判断"和"确定不兼容"在界面上是两种不同的说法，不是一句"不兼容"。
 */
fun oxideModsMeta(row: OxideModRow, labels: OxideModMetaLabels): List<OxideModMeta> = buildList {
    add(OxideModMeta(OxideModMetaField.FileName, row.fileName))

    if (row.sizeBytes > 0L) {
        add(OxideModMeta(OxideModMetaField.FileSize, formatModBytes(row.sizeBytes)))
    }

    row.modVersion?.takeIf { it.isNotBlank() }?.let {
        add(OxideModMeta(OxideModMetaField.Version, it))
    }

    row.authors.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
        add(OxideModMeta(OxideModMetaField.Author, it.joinToString(", ")))
    }

    // 平台标注优先，本地元数据里的加载器只在平台没标注时兜底。
    // 两个都没有时这一行整个不画：ModLoader.UNKNOWN 的显示名是空串，
    // 画出来也只是一句"未知"。
    val loaders = oxideModsLoaderLabels(row)
    if (loaders.isNotEmpty()) {
        add(OxideModMeta(OxideModMetaField.Loader, loaders.joinToString(" + ")))
    }

    row.instanceMinecraftVersion?.takeIf { it.isNotBlank() }?.let {
        add(OxideModMeta(OxideModMetaField.GameVersion, it))
    }

    // 兼容性与"这不是模组"共用同一个字段：两者都是对这一行的一句判断，
    // 而不是从模组里读出来的一个量。
    if (row.notMod) {
        add(OxideModMeta(OxideModMetaField.Compatibility, labels.unknownFile))
    } else {
        oxideModsCompatibilityText(row, labels)?.let {
            add(OxideModMeta(OxideModMetaField.Compatibility, it))
        }
    }
}

/**
 * 这一行要展示的加载器标签
 *
 * 平台标注优先（那是平台上的事实），本地读不到时才退回 jar 自己的声明。
 * 两者都没有时给空列表——**不给一个"未知"占位**，因为
 * `LocalMod.loader` 对读不出元数据的文件就是 `ModLoader.UNKNOWN`。
 */
fun oxideModsLoaderLabels(row: OxideModRow): List<String> =
    row.declaredLoaders.filter { it.isNotBlank() }
        .ifEmpty { listOfNotNull(row.localLoader?.takeIf { it.isNotBlank() }) }

/**
 * 兼容性那一行的文字；判定为"兼容"时不画任何一行
 */
fun oxideModsCompatibilityText(
    row: OxideModRow,
    labels: OxideModMetaLabels,
): String? = when (row.verdict) {
    ModLoaderVerdict.Compatible -> null
    ModLoaderVerdict.Loaderless -> labels.loaderless
    ModLoaderVerdict.NoInstanceLoader -> labels.noInstanceLoader
    ModLoaderVerdict.Mismatch -> labels.mismatched
}

/** 文件大小的展示文本；单位与旧界面的 `formatFileSize` 一致 */
fun formatModBytes(bytes: Long): String = when {
    bytes < 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "${bytes / (1024L * 1024L * 1024L)} GB"
}

// ---------------------------------------------------------------------------
// 搜索 / 状态 / 排序
// ---------------------------------------------------------------------------

/** 模组的状态筛选 */
enum class OxideModState {
    All,
    Enabled,
    Disabled,
}

/** 模组的排序键；每一档都是这一类模组真的排得出来的量 */
enum class OxideModSort {
    Name,
    FileName,
    FileModified,
    FileSize,
    Loader,
}

/** 搜索命中的范围 */
fun oxideModsMatches(row: OxideModRow, query: String): Boolean {
    if (query.isBlank()) return true
    val needles = listOf(
        row.displayName,
        row.fileName,
        row.key,
        row.modId,
        row.modVersion.orEmpty(),
    ) + row.authors + oxideModsLoaderLabels(row)
    return needles.any { it.contains(query.trim(), ignoreCase = true) }
}

/** 搜索 + 状态筛选 */
fun filterOxideMods(
    rows: List<OxideModRow>,
    query: String,
    state: OxideModState,
): List<OxideModRow> = rows.filter { row ->
    val nameMatched = oxideModsMatches(row, query)
    val stateMatched = when (state) {
        OxideModState.All -> true
        OxideModState.Enabled -> row.enabled
        OxideModState.Disabled -> !row.enabled
    }
    nameMatched && stateMatched
}

/**
 * 排序
 *
 * `ascending` 为 false 时整体取反，所以"降序"永远是"升序的倒过来"，
 * 不会出现第二次点同一个键就换一套无关顺序。
 */
fun sortOxideMods(
    rows: List<OxideModRow>,
    sort: OxideModSort,
    ascending: Boolean,
): List<OxideModRow> {
    // 类型参数必须写出来：`compareBy` 只在后面的 lambda 里用到元素类型，
    // 而 when 分支不提供期望类型，Kotlin 无从推断 `it` 是哪一行
    val comparator = when (sort) {
        OxideModSort.Name ->
            compareBy<OxideModRow, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }

        OxideModSort.FileName ->
            compareBy<OxideModRow, String>(String.CASE_INSENSITIVE_ORDER) { it.key }

        OxideModSort.FileModified -> compareBy<OxideModRow> { it.modifiedAt }
        OxideModSort.FileSize -> compareBy<OxideModRow> { it.sizeBytes }
        OxideModSort.Loader -> compareBy<OxideModRow, String>(String.CASE_INSENSITIVE_ORDER) {
            oxideModsLoaderLabels(it).firstOrNull().orEmpty()
        }
    }
    return rows.sortedWith(if (ascending) comparator else comparator.reversed())
}

/** 模组默认按文件名升序，与旧界面一致 */
fun oxideModsDefaultSort(): Pair<OxideModSort, Boolean> = OxideModSort.FileName to true

/** 支持的排序键，顺序即界面上的轮换顺序 */
fun oxideModSortOptions(): List<OxideModSort> = listOf(
    OxideModSort.Name,
    OxideModSort.FileName,
    OxideModSort.FileModified,
    OxideModSort.FileSize,
    OxideModSort.Loader,
)

/** 当前排序的下一档；每换一个键都从升序重新开始 */
fun nextOxideModSort(
    sort: OxideModSort,
    ascending: Boolean,
): Pair<OxideModSort, Boolean> {
    val options = oxideModSortOptions()
    val index = options.indexOf(sort)
    val current = if (index < 0) -1 else index * 2 + if (ascending) 0 else 1
    val next = ((current + 1) % (options.size * 2) + options.size * 2) % (options.size * 2)
    return options[next / 2] to (next % 2 == 0)
}

/** 排序按钮上的当前值：键与方向都以文字给出 */
fun oxideModSortLabel(sort: OxideModSort, ascending: Boolean): String =
    "${sort.name}·${if (ascending) "asc" else "desc"}"

/** 三个状态筛选各自的计数 */
data class OxideModStateCounts(val all: Int, val enabled: Int, val disabled: Int)

fun oxideModsStateCounts(rows: List<OxideModRow>): OxideModStateCounts = OxideModStateCounts(
    all = rows.size,
    enabled = rows.count { it.enabled },
    disabled = rows.count { !it.enabled },
)

// ---------------------------------------------------------------------------
// 选中与批量
// ---------------------------------------------------------------------------

/**
 * 选中项的集合运算
 *
 * 键一律是 [OxideModRow.key]（去掉 `.disabled` 的文件名），不是 `fileName`。
 * 用 `fileName` 的话：一次禁用之后 `fileName` 就变了，
 * 第二次点同一行既选不上、也匹配不回它的模组对象。
 */
fun oxideModsToggleSelection(selected: List<String>, key: String): List<String> =
    if (key in selected) selected - key else selected + key

/** 全选：把当前**可见**的那些并进已选，不清掉已经在选中的隐藏项 */
fun oxideModsSelectAll(visible: List<OxideModRow>, selected: List<String>): List<String> =
    (selected + visible.map { it.key }).distinct()

/** 取消选择：只清掉当前可见的那些，与旧界面的 `clearSelected` 一致 */
fun oxideModsClearVisibleSelection(visible: List<OxideModRow>, selected: List<String>): List<String> =
    selected - visible.map { it.key }.toSet()

/** 全选按钮此刻该写什么：全都在选中了就变成"取消选择" */
fun oxideModsEverythingSelected(visible: List<OxideModRow>, selected: List<String>): Boolean =
    visible.isNotEmpty() && visible.all { it.key in selected }

/** 按键取出已选中的行；已选中的键如果不在列表里了（文件已被外部删掉）就跳过 */
fun oxideModsSelectedRows(rows: List<OxideModRow>, selected: List<String>): List<OxideModRow> =
    rows.filter { it.key in selected }

/**
 * 批量启用/禁用的目标
 *
 * 只挑**当前状态与目标相反**的那些行。因此对一个已经全启用的集合点"全部启用"
 * 不会去动任何文件，而不是把每个文件重命名两次。
 */
fun oxideModsRowsToToggle(rows: List<OxideModRow>, enable: Boolean): List<OxideModRow> =
    rows.filter { it.enabled != enable }

/**
 * 批量删除的目标
 *
 * 返回的是 [OxideModRow.path]——磁盘上此刻真实的绝对路径。
 * 返回 `fileName` 曾经就是"删除点了没反应"的成因：
 * 禁用态的文件在磁盘上叫 `x.jar.disabled`，按 `x.jar` 去删什么也删不掉，
 * 而 `deleteQuietly` 不抛异常，于是界面把一次没发生的删除报成了成功。
 */
fun oxideModsDeletePaths(rows: List<OxideModRow>): List<String> =
    rows.map { it.path }.filter { it.isNotBlank() }.distinct()

/**
 * 确认层上要写出来的名字
 *
 * 用 [OxideModRow.fileName] 而不是显示名：删除不可逆，
 * 屏幕上必须出现**真实文件名**，而且禁用态那一条要带上 `.disabled` 后缀，
 * 否则用户根本看不出自己删的是哪个文件。
 */
fun oxideModsDeleteLabels(rows: List<OxideModRow>): List<String> =
    rows.map { it.fileName }.filter { it.isNotBlank() }.distinct()

/** 一次批量动作的结果，用来决定界面上写"完成"还是写"没有可改动的" */
data class OxideModBulkOutcome(
    val changed: Int,
    val skipped: Int,
) {
    val touchedAnything: Boolean get() = changed > 0
}

/**
 * 统计一次批量启用的结果
 *
 * [attempted] 是被选中的行数，[changed] 是文件状态真的变了的行数。
 * 两者不等就说明有文件没被搬动（多半是权限或文件被占用），
 * 界面上必须如实说出来，而不是一律报"完成"。
 */
fun oxideModBulkOutcome(attempted: List<OxideModRow>, changed: Int): OxideModBulkOutcome =
    OxideModBulkOutcome(changed = changed.coerceAtLeast(0), skipped = (attempted.size - changed).coerceAtLeast(0))