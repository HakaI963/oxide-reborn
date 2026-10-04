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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 目的地的底板
 *
 * ## 为什么每一块目的地都必须先铺它
 *
 * `OxideMainShell` 把当前页面与 [OxideDestination] 放在**同一个** `Box` 里，
 * 目的地是第二个子节点：Compose 的 `Box` 按声明顺序绘制，因此目的地画在页面之上，
 * 但**只要目的地自己不铺底，页面就会透过它继续可见**。
 *
 * 于是两块各有自己大标题的表面会叠在同一处：下面那块的标题不会因为上面盖了一层
 * 就消失，两行字各画各的，看上去就是"标题互相压住了"——真机上表现为两个标题叠在一起。
 * 越是半透明的底（面板的底板本身只有 ~9% 不透明度）叠得越明显。
 *
 * 因此：**任何目的地都必须以本函数为根**。这是那类碰撞唯一可靠的堵法，
 * 它对新目的地同样成立，不依赖谁记得给标题加偏移。
 *
 * ## 为什么是两层底
 *
 * 先铺完全不透明的 [Oxide.Bg]（页面底色，跟随主题），再叠近不透明的
 * [Oxide.PanelBackdrop]（二级面板底幕）。第一层保证底下那个页面一丝也透不过来——
 * [Oxide.PanelBackdrop] 自己是 `0xF2` alpha，单独铺仍会漏出约 5%，而页面标题是
 * 22sp 粗体，5% 在真机上足以看出第二行字。第二层只负责色调：让目的地比页面高一档。
 *
 * 两层加起来仍然完全落在既有 token 上，因此换深浅主题时这块底也跟着换。
 *
 * ## 点外面关闭
 *
 * [onDismiss] 非空时整块底板吞掉未被子节点吃掉的点击，于是"点面板外面关掉它"
 * 与硬件返回键是同一条路。面板本身要拦住自己范围内的点击，见 [oxideDestinationPanelInput]。
 */
@Composable
fun OxideDestinationBackdrop(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 顺序要紧：先铺**不透明**的页面底色，再叠近不透明的 [Oxide.PanelBackdrop]。
            // PanelBackdrop 自身是 0xF2 alpha，单独铺它仍会让底下那页透出约 5%——
            // 22sp 的页面标题那么粗，5% 在真机上仍然看得见，于是两块标题又会"重影"。
            // 底下先有一层完全不透明的 Bg 兜住，这层就只剩色调，碰撞从此不可能发生。
            .background(Oxide.Bg)
            .background(Oxide.PanelBackdrop)
            .then(
                if (onDismiss != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onDismiss,
                    )
                } else {
                    Modifier
                }
            ),
        content = content,
    )
}

/**
 * 面板自己的输入层：把落在面板范围内的点击吃掉
 *
 * 底板用"点外面关闭"实现，而 `Box` 的命中测试是先子后己的——所以面板上任何一个
 * 没有自己的点击处理器的区域（比如面板的留白、标题行）都会把点击漏到底板上，
 * 于是"在面板里点一下"变成了关闭。给面板挂上这个空点击处理器，漏下去的点击就停在面板上。
 */
@Composable
fun Modifier.oxideDestinationPanelInput(): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {},
)