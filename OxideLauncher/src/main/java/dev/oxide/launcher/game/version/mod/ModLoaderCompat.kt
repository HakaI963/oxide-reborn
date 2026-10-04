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

package dev.oxide.launcher.game.version.mod

import dev.oxide.launcher.game.addons.modloader.ModLoader
import dev.oxide.launcher.game.download.assets.platform.ModLoaderDisplayLabel

/**
 * 模组文件与目标实例之间的加载器判定
 *
 * 存在的理由：`ModData.checkUpdate` 曾经在这里"放宽"过判定——当模组文件自己
 * 标注的加载器里没有实例的那个加载器时，它改成**沿用模组文件自己的加载器通道**
 * 去平台找新版本。于是 Fabric 实例里的一个 NeoForge 模组被判为"可更新"，
 * 下载回来的也是 NeoForge 构建，随后 `ModUpdater.ReplaceMod` 直接把它覆盖进
 * `mods/`。用户在设备上看到的正是"NeoForge 模组分进了 Fabric 实例"。
 *
 * 因此判定只有一处，并且是**纯函数**：谁在判定、用什么顺序判定都不能影响结果。
 * 下载前的守卫、界面上那一行"与本实例是否兼容"的说明，走的都是这里。
 *
 * 判定依据只有两样真实信息：
 *  - [VersionInfo.loaderInfos] 解析出来的实例加载器（显示名）
 *  - [ModFile.loaders] 平台给模组文件标注的加载器（显示名）
 *
 * 两侧都用显示名比对，因为 CurseForge 与 Modrinth 各自枚举了一份加载器，
 * 但显示名与 [ModLoader.displayName] 是同一套字面量（见 `CurseForgeModLoader`
 * 与 `ModrinthModLoaderCategory` 的定义）。
 */

/**
 * 一次判定的结论
 *
 * 只有 [Mismatch] 是"确定不兼容"。另外三种都表示**无法证明不兼容**，
 * 因此都不阻止下载：把"读不出来"当成"不兼容"会让一批本来正常的模组永远更新不了。
 */
enum class ModLoaderVerdict {
    /** 实例的某个加载器出现在模组文件标注的加载器里 */
    Compatible,

    /** 模组文件没有标注任何加载器（平台只给了游戏版本），无从判断 */
    Loaderless,

    /** 实例自己没有任何可识别的加载器，无从判断 */
    NoInstanceLoader,

    /** 模组文件标注了加载器，其中没有任何一个是实例的加载器 */
    Mismatch,
    ;

    /** 这一条判定是否允许就地更新/安装这个模组文件 */
    val loadable: Boolean
        get() = this != Mismatch
}

/**
 * 本地加载器的显示名里，剔除占位值
 *
 * [ModLoader.UNKNOWN] 的显示名是空串；`CurseForgeModLoader.ANY` 的显示名也是空串。
 * 空串不是"声明了某个加载器"，把它当声明会让"原版文件 + ANY 标签"看起来像兼容。
 */
private fun loaderNamesOf(sources: Iterable<String>): Set<String> =
    sources.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/**
 * 目标实例的加载器 + 模组文件标注的加载器 → 兼容性判定
 *
 * 纯函数，不碰磁盘、不发网络请求，因此这一条规则可以被单元测试逐条钉死。
 *
 * 规则（顺序不可调换）：
 *
 *  1. 模组文件没标注加载器 → [ModLoaderVerdict.Loaderless]，放行。
 *  2. 实例没有任何可识别的加载器 → [ModLoaderVerdict.NoInstanceLoader]，放行。
 *  3. 模组文件标注的加载器里命中实例任意一个加载器（忽略大小写）→ [ModLoaderVerdict.Compatible]。
 *  4. 否则 → [ModLoaderVerdict.Mismatch]，**不放行**。
 *
 * 多加载器模组（例如同时提供 Fabric 与 Forge 构建）走第 3 条的"任一命中"，
 * 不要求实例的加载器出现在标注列表的唯一位置。
 */
fun modLoaderVerdict(
    instanceLoaders: Collection<ModLoader>,
    declaredLoaders: Collection<ModLoaderDisplayLabel>,
): ModLoaderVerdict = modLoaderVerdictByName(
    instanceNames = instanceLoaders.map { it.displayName },
    declaredNames = declaredLoaders.map { it.getDisplayName() },
)

/**
 * 同一条判定，只是输入已经是显示名
 *
 * 单独暴露出来是为了让"忽略大小写比对"这一条能被单测直接覆盖：把它藏在
 * 枚举取名的那一层里，测试就得先造一个假的 `ModLoaderDisplayLabel`
 * （而它是 `Parcelable`），那不是一个纯 JVM 测试该干的事。
 */
fun modLoaderVerdictByName(
    instanceNames: Collection<String>,
    declaredNames: Collection<String>,
): ModLoaderVerdict {
    val declared = loaderNamesOf(declaredNames)
    if (declared.isEmpty()) return ModLoaderVerdict.Loaderless

    val instance = loaderNamesOf(instanceNames)
    if (instance.isEmpty()) return ModLoaderVerdict.NoInstanceLoader

    val compatible = instance.any { loader ->
        declared.any { declaredLoader -> declaredLoader.equals(loader, ignoreCase = true) }
    }
    return if (compatible) ModLoaderVerdict.Compatible else ModLoaderVerdict.Mismatch
}

/**
 * 一次判定之后，应当去平台筛哪几个加载器通道
 *
 * 判定为 [ModLoaderVerdict.Mismatch] 时返回空集合，调用方**必须**在拿到空集合时
 * 停止下载而不是当作"不限加载器"。
 *
 * 实例读不出加载器（[ModLoaderVerdict.NoInstanceLoader]）时退回模组文件自己标注的通道：
 * 这种实例本身就没有可遵守的加载器约束，按实例筛会把所有版本都筛掉。
 */
fun resolveTargetLoaderNames(
    instanceLoaders: Collection<ModLoader>,
    declaredLoaders: Collection<ModLoaderDisplayLabel>,
): Set<String> {
    if (!modLoaderVerdict(instanceLoaders, declaredLoaders).loadable) return emptySet()
    val instance = loaderNamesOf(instanceLoaders.map { it.displayName })
    return if (instance.isEmpty()) {
        loaderNamesOf(declaredLoaders.map { it.getDisplayName() })
    } else {
        instance
    }
}