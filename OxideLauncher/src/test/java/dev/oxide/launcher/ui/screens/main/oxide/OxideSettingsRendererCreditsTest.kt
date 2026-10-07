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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 新渲染器来源的致谢文案守卫
 *
 * 三行致谢的文字（标题与说明）由字符串资源承载，面板装配由
 * OxideAboutPanels 的所有者按本文件钉住的键名接线：
 * oxide_about_ack_copper_oxide_title/detail、
 * oxide_about_ack_ltw_title/detail、oxide_about_ack_mojo_gl_title/detail。
 *
 * 三条硬规则：每一行都点名项目、点名许可证、给出源码仓库链接（链接在
 * 条目装配处，不在字符串里）；Copper Oxide 必须写明是 Oxide 基于
 * MobileGlues 的调优构建，而不是 Oxide 自己的东西；Copper Oxide 与 LTW
 * 是 LGPL，Holy GL4ES 是 MIT，文案里不许出现公有领域或无条件免费使用的说法
 *（v1.11.0 起 Mojo Zink 已移除，mojo-gl 那一行只剩 Holy GL4ES）。
 */
class OxideSettingsRendererCreditsTest {

    private val strings: String by lazy { readStringsXml() }

    private val page: String by lazy {
        readMainSource("ui/screens/main/oxide/OxideSettingsPage.kt")
    }

    private val drawers: String by lazy {
        readMainSource("ui/screens/main/oxide/OxideSupportDrawers.kt")
    }

    @Test
    fun theCopperOxideRowNamesTheProjectTheLicenceAndTheMaker() {
        val title = stringValue("oxide_about_ack_copper_oxide_title")
        val detail = stringValue("oxide_about_ack_copper_oxide_detail")
        assertTrue("title must name Copper Oxide", title.contains("Copper Oxide"))
        assertTrue("title must name MobileGlues", title.contains("MobileGlues"))
        assertTrue("detail must say tuned build of MobileGlues", detail.contains("tuned build of MobileGlues"))
        assertTrue("detail must name LGPL-2.1", detail.contains("LGPL-2.1"))
        assertTrue("detail must name MobileGL-Dev", detail.contains("MobileGL-Dev"))
    }

    @Test
    fun theLtwRowNamesTheProjectTheLicenceAndTheMaker() {
        val title = stringValue("oxide_about_ack_ltw_title")
        val detail = stringValue("oxide_about_ack_ltw_detail")
        assertTrue("title must name LTW", title.contains("LTW"))
        assertTrue("detail must name MojoLauncher", detail.contains("MojoLauncher"))
        assertTrue("detail must name LGPL-3.0", detail.contains("LGPL-3.0"))
    }

    @Test
    fun theMojoGlRowNamesTheBackendTheLicenceAndTheMaker() {
        // v1.11.0 移除了 Mojo Zink：这一行现在只剩 FCL-Team 的 Holy GL4ES（MIT）。
        val title = stringValue("oxide_about_ack_mojo_gl_title")
        val detail = stringValue("oxide_about_ack_mojo_gl_detail")
        assertTrue("title must name Holy GL4ES", title.contains("Holy GL4ES"))
        assertFalse("Zink is gone and must not be advertised", title.contains("Zink") || detail.contains("Zink"))
        assertTrue("detail must name FCL-Team", detail.contains("FCL-Team"))
        assertTrue("detail must name MIT", detail.contains("MIT"))
    }

    @Test
    fun theNewCreditRowsClaimNoOwnershipAndOfferNoFreeUseTerms() {
        val details = listOf(
            stringValue("oxide_about_ack_copper_oxide_detail"),
            stringValue("oxide_about_ack_ltw_detail"),
            stringValue("oxide_about_ack_mojo_gl_detail"),
        )
        for (detail in details) {
            assertFalse(
                "LGPL libraries are not public domain: " + detail,
                detail.contains("public domain", ignoreCase = true),
            )
            assertFalse(
                "LGPL libraries are not free of terms: " + detail,
                detail.contains("free to use", ignoreCase = true),
            )
        }
        assertTrue(
            "Copper Oxide must be disowned explicitly",
            details[0].contains("does not own"),
        )
    }

    @Test
    fun noOwnedFileRendersRowsForTheDeletedBuiltinRenderers() {
        // 被删的六个内置渲染器：NG-GL4ES/Krypton、GL4ES 1.14、Kopper-Zink、
        // VirGL、Freedreno、Panfrost。本次拥有的文件里一行都不该点名它们；
        // 剩下引用它们的非拥有文件（_Libraries.kt、THIRD_PARTY.md、
        // Renderers.kt 及各渲染器实现）由渲染器改动的所有者处理，见报告。
        for (name in listOf(
            "NG-GL4ES",
            "Krypton",
            "Kopper",
            "VirGL",
            "Freedreno",
            "Panfrost",
        )) {
            assertFalse(
                name + " must not be named in the settings page",
                page.contains(name),
            )
            assertFalse(
                name + " must not be named in the support drawers",
                drawers.contains(name),
            )
            assertFalse(
                name + " must not be named in strings",
                strings.contains(name),
            )
        }
        assertFalse(
            "GL4ES 1.14 must not be named in strings",
            strings.contains("1.14"),
        )
    }

    private fun stringValue(name: String): String {
        val open = strings.indexOf("name=\"" + name + "\"")
        assertTrue("string " + name + " must exist", open >= 0)
        val start = strings.indexOf('>', open) + 1
        val end = strings.indexOf("</string>", start)
        assertTrue("string " + name + " must be well formed", start > 0 && end > start)
        return strings.substring(start, end)
    }

    private fun readMainSource(relativePath: String): String = locate(
        "src/main/java/dev/oxide/launcher/" + relativePath
    ).readText()

    private fun readStringsXml(): String = locate(
        "src/main/res/values/strings.xml"
    ).readText()

    private fun locate(suffix: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(suffix)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate " + suffix + " from " + File("").absolutePath)
    }
}
