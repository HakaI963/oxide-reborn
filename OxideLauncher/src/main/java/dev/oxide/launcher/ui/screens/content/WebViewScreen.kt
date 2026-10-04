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

package dev.oxide.launcher.ui.screens.content

import androidx.navigation3.runtime.NavBackStack
import dev.oxide.launcher.ui.screens.TitledNavKey
import dev.oxide.launcher.ui.screens.main.oxide.openOxideBrowser

/**
 * 应用内浏览器：这个文件曾经是它本身，现在只留下这一座桥
 *
 * ## 为什么桥还在
 *
 * 调用点有三处，两处在 Oxide 自己的账号页与启动前置检查里，第三处是旧的
 * `AccountManageScreen`。前两处不需要改（它们要的仍然是"打开这一页"），第三处不在
 * 本次改动的范围里，但它那份源码靠这个扩展函数编译。留着这四行，旧界面的调用点
 * 也就自动指向 Oxide 的浏览器面板，而不是推进一条已经不存在的旧条目。
 *
 * ## 为什么接收者不再被使用
 *
 * 旧实现把网址推进导航栈（`NormalNavKey.WebScreen`），一整页旧 Zalith 界面换掉底下
 * 那一页：自己的图标顶栏、底下那页照原样透上来、没有可见的关闭按钮、失败时只有一屏
 * 白、也没有重试。更要紧的是"浏览器开着吗"这件事只能靠
 * `currentKey is NormalNavKey.WebScreen` 去猜，而设备码登录的轮询逻辑正是拿这个猜测
 * 判断用户是不是自己走了——那条链一旦断开，登录会在用户还好好看着网页的时候被取消。
 *
 * 现在网址交给 [openOxideBrowser]：状态在进程上（`globalOxideBrowser`），面板由宿主
 * 那一层渲染成 Oxide 自家的一块盖板。扩展函数保留下来只是为了不改调用点的写法，
 * 接收者留着不用是刻意的——它的意义只剩"从导航栈的语境里打开浏览器"。
 */
fun NavBackStack<TitledNavKey>.navigateToWeb(webUrl: String) {
    openOxideBrowser(webUrl)
}