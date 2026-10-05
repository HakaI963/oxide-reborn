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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import dev.oxide.launcher.R

/**
 * 侧栏的四个主页面
 *
 * 编号是参考稿里的 `01`/`02`/`03`/`04`，同时也是过渡方向的判定依据：
 * 序号增大就是前进，减小就是后退，[OxideNavState] 据此选择位移方向。
 */
enum class OxidePage(val number: String, val labelRes: Int) {
    Home("01", R.string.oxide_nav_home),
    Instances("02", R.string.oxide_nav_instances),
    Discover("03", R.string.oxide_nav_discover),
    Settings("04", R.string.oxide_nav_settings),
    ;

    val label: String
        @Composable get() = stringResource(labelRes)
}

/**
 * 顶层导航状态
 *
 * 只管理"现在是第几页"、"这次切换是前进还是后退"和"已经切了几次页"。页面内部的状态一律留在
 * 页面自己身上，这样切页不会重建 ViewModel，也不会丢掉正在加载的内容。
 */
@Stable
class OxideNavState(initial: OxidePage = OxidePage.Home) {

    /** 当前页面 */
    var page: OxidePage by mutableStateOf(initial)
        private set

    /** 最近一次切换是否为前进，用于决定过渡方向 */
    var forward: Boolean by mutableStateOf(true)
        private set

    /**
     * 导航世代：每真的换一次页就 +1
     *
     * 它不进 saver：旋转屏幕只需要还原停在第几页，重启之后也没有任何"还开着的临时表面"
     * 需要作废，世代从 0 重新数起即可。
     *
     * 有它才谈得上"切页这一下让旧东西失效"：目的地与任务抽屉都记在外壳上，比页面活得久，
     * 换页时它们自己不会被任何东西清掉，于是会盖在新页面上（见 [oxideTransientSurface]）。
     */
    var epoch: Int by mutableStateOf(0)
        private set

    /**
     * 跳到指定页面
     *
     * 切到当前页是空操作，避免重复点击导航项时重新跑一次页面进场动画。
     */
    fun go(target: OxidePage) {
        if (target == page) return
        forward = target.ordinal > page.ordinal
        page = target
        epoch++
    }

    /** 硬件返回键：不是第一个页面就回到第一个，否则交还给上层退出 */
    fun goBack(): Boolean {
        if (page == OxidePage.Home) return false
        forward = false
        page = OxidePage.Home
        epoch++
        return true
    }
}

/**
 * 一块临时表面在当前世代下还算不算开着
 *
 * [surface] 是那一块表面本身（没打开时是 null），[openedAt] 是它被打开时的导航世代，
 * [current] 是 [OxideNavState.epoch] 现在是多少。**世代对得上才算还开着**：换一次页世代就变了，
 * 于是旧世代上打开的抽屉在这一帧就作废——不需要真的去改它的状态，退出动画照旧由
 * `AnimatedVisibility` 跑完，而返回键的优先级也一起回到"上面没有盖板"的状态。
 *
 * 纯函数：不碰任何 Compose 状态，因此可以在不启动 Compose 的前提下测。
 */
internal fun <T> oxideTransientSurface(surface: T?, openedAt: Int, current: Int): T? =
    if (surface != null && openedAt == current) surface else null

/** 创建并记住导航状态；用 rememberSaveable 是为了旋转屏幕后停在原来那一页 */
@Composable
fun rememberOxideNavState(): OxideNavState =
    rememberSaveable(saver = OxideNavStateSaver) { OxideNavState() }

/** 只存页面序号，避免序列化任何复杂对象 */
private val OxideNavStateSaver: Saver<OxideNavState, Any> = listSaver(
    save = { listOf(it.page.ordinal) },
    restore = { saved ->
        OxideNavState(OxidePage.entries.getOrElse(saved.firstOrNull() ?: 0) { OxidePage.Home })
    }
)
