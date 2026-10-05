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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.game.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.oxide.launcher.ui.components.OxideLogo
import dev.oxide.launcher.ui.components.OxideLogoGap
import dev.oxide.launcher.ui.components.OxideLogoWordmark
import dev.oxide.launcher.ui.theme.Oxide

/**
 * 游戏画面上的 Oxide 品牌标识
 *
 * 画的是**已经存在**的那个 logo——[OxideLogo] 本身，一个字都没有重画：
 * 图形仍然是 [dev.oxide.launcher.ui.components.OxideMark] 的纯 Canvas 描边，
 * 字标仍然是"OX 重 + IDE 轻"，比例仍然是参考稿那三个数（37dp 图形 / 10dp 间距 /
 * 60sp 字标）同乘一个系数 [GAME_BRAND_SCALE]。侧栏与开场动画用的就是同一个组件，
 * 因此这一块和启动器其余部分读起来是同一个 logo，而不是又一块新贴纸。
 *
 * ## 为什么只能是启动器这一侧的浮层
 *
 * 用户的原话是"在 Minecraft 主菜单的右下角加 Oxide 标识"。这里必须说清楚
 * **做不到**的那一半：主菜单那一屏是 Minecraft 自己的 `TitleScreen`，它在游戏
 * 进程里；这个项目既没有 mixin/coremod 也没有 javaagent，那个类还被混淆映射
 * 并且逐版本变，**启动器这一侧拿不到任何能判断"当前是不是主菜单"的类**。
 * 想按场景出现就得往游戏进程里注入，代价与风险都远大于这一小块标识本身。
 *
 * 于是这里的做法是：把标识压在游戏画面的右下角，任何时候都在。它不重画
 * Minecraft 的主菜单，不动它的任何一个按钮，只在角落那一小格里多一个读得出来
 * 但不抢戏的记号。浮层由 [dev.oxide.launcher.ui.screens.game.GameScreen] 组合在
 * VMActivity 的游戏 surface 之上，因此它天生压在任意一帧游戏画面上。
 *
 * ## 为什么不挡 Minecraft 的按键
 *
 * 三件事各自独立地保证这一点：
 *
 * 1. **不接收指针事件。** 本文件里没有任何指针输入的东西：没有 pointerInput、
 *    没有 clickable、没有手势修饰符。于是这一块在命中测试里根本不参与，
 *    按在它上面的触摸照旧落到下面的游戏 surface 与控制布局控件上——即使玩家
 *    把某颗控件拖到这一块正下方，按的仍然是那颗控件，而不是标识。
 * 2. **位置在角落，尺寸极小。** 整块约 46dp 宽、12dp 高。Minecraft 主菜单的
 *    按键全部排在画面中部，右下角本来就是空的。
 * 3. **压低到半透明。** [GameBrandAlpha] 让它读得出是启动器，同时退到画面之后，
 *    不会在按键附近形成一片看着像可点区域的东西。
 *
 * ## 怎么挑的底部留白
 *
 * 玩家自己可以把控件拖到任何地方，因此这里守的不是"玩家怎么摆都不撞"，
 * 而是**默认布局下不撞**。assets/default_layout.json 里离右下角最近的是"▢"
 * （游戏内按钮，位置 x=8233 y=7765，边长为屏高的 1015/10000）。按钮位置是
 * "上沿 = (屏高 − 按钮高) × y%"，于是它的下沿落在
 * (1 − 0.1015) × 0.7765 + 0.1015 = 0.7992 屏高处，也就是屏幕最下面
 * [DefaultControlFreeBandRatio] = 20.08% 是一条空带。
 *
 * 标识从屏幕底边往上占 [GameBrandBand] = 留白 + 自身高度这一条。
 * [gameBrandFitsIn] 要求这一条装得进那条空带才画——于是"空带不够高"这件事
 * 变成了"这个窗口太矮，标识不出现"，而不是"标识压在按钮上面"。
 */
internal const val GAME_BRAND_SCALE = 0.20f

/** 图形边长，与 [Oxide.MarkSize] 同乘 [GAME_BRAND_SCALE] */
internal val GameBrandMarkSize: Dp = Oxide.MarkSize * GAME_BRAND_SCALE

/** 字标字号，与 [OxideLogoWordmark] 同乘 [GAME_BRAND_SCALE] */
internal val GameBrandWordmark: TextUnit = OxideLogoWordmark * GAME_BRAND_SCALE

/** 图形与字标之间的固定间距，与 [OxideLogoGap] 同乘 [GAME_BRAND_SCALE] */
internal val GameBrandGap: Dp = OxideLogoGap * GAME_BRAND_SCALE

/** 标识到屏幕边（含系统栏与刘海）之间的留白 */
internal val GameBrandMargin: Dp = 10.dp

/**
 * 标识自身的高度
 *
 * 字标的行高就等于字号（[OxideLogoWordmark] 把 lineHeight 写成 fontSize），
 * 所以这一条是字标那一行，不是图形。字体放大时会略微超出这一条，
 * 下面的空带判定留了余量，因此放大之后仍然不会撞上默认控件。
 */
internal val GameBrandHeight: Dp = 12.dp

/** 标识从屏幕底边往上占的那一条：留白加自身高度 */
internal val GameBrandBand: Dp = GameBrandMargin + GameBrandHeight

/**
 * 默认控制布局里那颗最靠下的游戏内控件（"▢"）下沿留在屏高的这一段
 *
 * 0.2008 = 1 − ((1 − 0.1015) × 0.7765 + 0.1015)，算式与出处见上面的说明。
 * 取成常量是因为它来自一份固定的资源文件，而单测要守住的就是这个数。
 */
internal const val DefaultControlFreeBandRatio = 0.2008f

/**
 * 标识的不透明度
 *
 * 底下压的是任意一帧游戏画面：洞穴里接近纯黑，雪原上接近纯白。图形自己的
 * 渐变两端（近白 / 饱和橙）在明暗两种底色上都是高对比的，字标取 Oxide 的
 * 前景色；这里要的只是"退到画面之后"，所以半透明正好——既读得出是启动器，
 * 又不会让人以为那一格能按。
 */
internal const val GameBrandAlpha = 0.5f

/**
 * 可用宽度至少要有这么多
 *
 * 图形（7.4dp）加间距（2dp）加"OXIDE"字标（约 34dp）合计约 44dp；96dp 是它的
 * 两倍多，字体放大到 2 倍也放得下。窄于此说明窗口已经不成其为"游戏画面"，
 * 此时画一块会被右边裁掉的标识比不画更糟。
 */
internal val GameBrandMinAvailableWidth: Dp = 96.dp

/**
 * 放不下就不画
 *
 * 两个条件缺一不可，而它们防的是两件不同的事：
 *
 * - 宽度：防止标识被右边缘裁掉。
 * - 高度：把 [GameBrandBand] 与 [DefaultControlFreeBandRatio] 那条空带比一比，
 *   防止标识压在默认控件上面。窗口越矮，这条空带在绝对尺寸上越短，
 *   于是同一个条件在分屏与自由窗口下自动把标识收掉。
 *
 * 纯函数，因此这两条都可以脱离 Compose 单测。
 *
 * @param available 外层量到的可用区，这一层就是游戏 surface 的范围
 */
internal fun gameBrandFitsIn(available: DpSize): Boolean =
    available.width >= GameBrandMinAvailableWidth &&
        GameBrandBand.value <= available.height.value * DefaultControlFreeBandRatio

/**
 * 游戏画面右下角的 Oxide 标识
 *
 * 由 [dev.oxide.launcher.ui.screens.game.GameScreen] 在它自己的
 * `BoxWithConstraints` 里以 [androidx.compose.ui.Alignment.BottomEnd] 组合，
 * 与控制布局层、快捷栏触发层同属这一层浮层。开关由调用方读
 * `AllSettings.showGameBrand` 之后决定是否组合本组件，与悬浮球、帧率读数
 * 的做法一致。
 *
 * @param availableWidth 外层量到的可用宽度
 * @param availableHeight 外层量到的可用高度
 * @param modifier 调用方给的对齐方式
 */
@Composable
internal fun OxideGameBrand(
    availableWidth: Dp,
    availableHeight: Dp,
    modifier: Modifier = Modifier,
) {
    if (!gameBrandFitsIn(DpSize(availableWidth, availableHeight))) return

    val density = LocalDensity.current
    // 游戏是全屏的（VMActivity 用的是 windowFullscreen 主题），系统栏与刘海
    // 这两项通常都是 0；但它们一旦非 0——分屏、显示切口模式——标识就会被压在
    // 系统栏或刘海底下。safeDrawing 与控制布局编辑器的停靠球读的是同一份，
    // 因此这里不另开一个 inset 来源，也不退回硬编码的边距。
    val safeDrawing = WindowInsets.safeDrawing
    val endInset = with(density) { safeDrawing.getRight(density).toDp() }
    val bottomInset = with(density) { safeDrawing.getBottom(density).toDp() }

    Box(
        modifier = modifier
            .padding(
                end = endInset + GameBrandMargin,
                bottom = bottomInset + GameBrandMargin,
            )
            .alpha(GameBrandAlpha)
    ) {
        OxideLogo(
            gap = GameBrandGap,
            showWordmark = true,
            markSize = GameBrandMarkSize,
            wordmarkSize = GameBrandWordmark,
        )
    }
}