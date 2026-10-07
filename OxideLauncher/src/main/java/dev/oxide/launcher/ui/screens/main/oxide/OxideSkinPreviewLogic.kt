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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.ui.screens.content.elements.ChangeSkin
import java.io.File

/**
 * 皮肤菜单里那块 3D 预览该显示什么
 *
 * 三个字段和 [dev.oxide.launcher.ui.components.SkinPreview3D] 的入参一一对应，
 * 所以这里决定的就是那一格里真正会出现的东西。
 */
internal data class OxideSkinPreviewSource(
    /** 要显示的皮肤 PNG；null 表示交给预览渲染默认的 Steve */
    val skinFile: File?,
    /** 要显示的披风 PNG；null 表示这一格不画披风 */
    val capeFile: File?,
    /** 手臂型号；[SkinModelType.NONE] 表示让预览自己从像素判断 */
    val modelType: SkinModelType,
)

/**
 * 预览该显示哪一份皮肤与披风
 *
 * **待应用的那一份优先于已保存的那一份。** 用户导入 PNG 之后、按下 Apply 之前，
 * 缓存文件已经落盘但账号的皮肤文件还没换；这时候预览如果跟着已保存的那份走，
 * 用户看到的恰好是他刚导入之前的样子——也就是"导入没有任何反馈"。
 * 手臂型号同理：它属于待应用的那一份，所以 [SkinModelType] 也跟着它走，
 * 点"细臂"时预览立刻重画成细臂，而不是等 Apply 之后才变。
 *
 * 披风没有"待应用"这一态：微软账号是下载完写进账号文件才出现在列表里，
 * 本地与 Ely.by 账号是直接写进账号文件的（见 `AccountManageViewModel`），
 * 所以披风永远读落盘的那一份。
 *
 * 两个 `*Exists` 由调用方在 IO 线程上量好再传进来，因此这个函数本身不碰磁盘、
 * 不读组合期状态，可以在单元测试里逐个状态钉死。
 */
internal fun oxideSkinPreviewSource(
    pendingSkin: ChangeSkin?,
    savedSkinFile: File?,
    savedSkinModel: SkinModelType,
    savedSkinExists: Boolean,
    savedCapeFile: File?,
    savedCapeExists: Boolean,
): OxideSkinPreviewSource {
    val pending = pendingSkin as? ChangeSkin.ChangeSkinData
    return OxideSkinPreviewSource(
        skinFile = pending?.cacheFile ?: savedSkinFile?.takeIf { savedSkinExists },
        capeFile = savedCapeFile?.takeIf { savedCapeExists },
        modelType = pending?.skinModel ?: savedSkinModel,
    )
}

/** 预览的高度下限：再矮就看不出是个玩家模型 */
const val OxideSkinPreviewMinHeightDp: Int = 150

/** 预览的高度上限：再高就把导入行、手臂型号与 Apply 一起挤出视野 */
const val OxideSkinPreviewMaxHeightDp: Int = 420

/** 相机到模型的默认距离：与 skinview.js 里 setAzimuthAndPitch 的缺省值一致 */
const val OxideSkinPreviewDistanceDefault: Int = 60

/** 相机能推到的最近距离：与 skinview.js 里 OrbitControls 的 minDistance 一致 */
const val OxideSkinPreviewDistanceMin: Int = 25

/** 相机能拉到的最远距离：与 skinview.js 里 OrbitControls 的 maxDistance 一致 */
const val OxideSkinPreviewDistanceMax: Int = 120

/** 缩放按钮每按一次走的距离：太大一格就飞出去，太小按了像没按 */
const val OxideSkinPreviewDistanceStep: Int = 10

/**
 * 双栏里预览格占的宽度份额
 *
 * 换肤菜单是固定的左右两栏：左边只放 3D 预览，右边放导入与披风。
 * 预览格要比控制格宽，模型才有地方转。
 */
const val OxideSkinTwoPanePreviewWeight: Float = 0.55f

/** 双栏里控制格占的宽度份额：与预览格相加正好是整行 */
const val OxideSkinTwoPaneControlsWeight: Float = 0.45f

/**
 * 缩放距离夹取
 *
 * JS 侧的 setDistance 与 OrbitControls 的 min/max 是同一组数，
 * 这里先夹一次，按了缩放按钮之后送过去的值一定合法。
 * 纯函数，可以直接单测。
 */
internal fun oxideSkinPreviewDistanceClamped(distance: Int): Int =
    distance.coerceIn(OxideSkinPreviewDistanceMin, OxideSkinPreviewDistanceMax)

/**
 * 放大一格：相机往模型跟前推一步，到头就停住而不是弹回
 *
 * 纯函数，可以直接单测。
 */
internal fun oxideSkinPreviewZoomIn(distance: Int): Int =
    (distance - OxideSkinPreviewDistanceStep).coerceAtLeast(OxideSkinPreviewDistanceMin)

/**
 * 缩小一格：相机往后拉一步，到头就停住而不是飞出去
 *
 * 纯函数，可以直接单测。
 */
internal fun oxideSkinPreviewZoomOut(distance: Int): Int =
    (distance + OxideSkinPreviewDistanceStep).coerceAtMost(OxideSkinPreviewDistanceMax)

/**
 * 披风在预览里画还是不画
 *
 * "藏起来"是预览自己的开关，不是删文件：返回 null 时调用方把
 * capeFile 按 null 送给预览，落盘的那一份原封不动。
 * 有没有文件还是由 `*Exists` 说了算，这里只叠加开关。
 * 纯函数，可以直接单测。
 */
internal fun oxideSkinPreviewCapeFile(
    savedCapeFile: File?,
    savedCapeExists: Boolean,
    capeHidden: Boolean,
): File? =
    if (capeHidden) {
        null
    } else {
        savedCapeFile?.takeIf { savedCapeExists }
    }

/**
 * 预览该占多高
 *
 * 只吃一个参数：一张卡片的最小宽度。这个数已经是"当前可用宽度 + 界面缩放"算出来的
 * （见 `oxideMetricsFor`），所以预览跟着宽度档和用户缩放一起变，而不会在宽屏上
 * 长成一个把整张菜单推到很下面的巨块——上限就是为此存在的。
 * 比例取 1.2 而不是 1：一个玩家模型在正方形格里会显得太小。
 *
 * 纯函数，参数只有卡片宽度，因此可以直接单测。
 */
internal fun oxideSkinPreviewHeight(cardMinWidthDp: Int): Int =
    (cardMinWidthDp * 1.2f).toInt()
        .coerceIn(OxideSkinPreviewMinHeightDp, OxideSkinPreviewMaxHeightDp)