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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 内置浏览器那块状态，以及"浏览器开着吗"这句话的答案
 *
 * 这句话有三个调用点（账号页两处、启动前置检查一处），而问它的是设备码登录的轮询
 * 循环——`AccountUtils.microsoftLogin` 里 `(!checkIfInWebScreen())` 为真就当作
 * "用户自己把网页关掉了"，随即取消这一次登录。
 *
 * 因此答案错一次的后果是**静默**的：旧答案是"栈顶那个键是不是
 * `NormalNavKey.WebScreen`"，旧链一断它就永远为假，于是授权页还开着，登录先没了。
 * 这里逐条钉住新答案，包括两个容易被忽略的前提：
 *
 * 1. 读写都不是组合作用域里的事——写在 IO 线程、读也在 IO 线程；
 * 2. 空地址不算打开，否则面板会顶着一个空地址占住整个界面。
 */
class OxideBrowserStateTest {

    /** 设备码授权页，真实的地址形态 */
    private val verificationUrl =
        "https://login.live.com/oauth20/device.srf?code=ABC123"

    @Test
    fun `there is no browser until one is opened`() {
        val state = OxideBrowserState()
        assertNull("nothing is open yet", state.openUrl.value)
        assertFalse(state.isOpen())
        assertFalse(state.openedForSignIn.value)
    }

    @Test
    fun `opening a url is the one thing that makes it open`() {
        val state = OxideBrowserState()
        state.open(verificationUrl, forSignIn = true)
        assertEquals(verificationUrl, state.openUrl.value)
        assertTrue(state.isOpen())
        assertTrue(
            "a device-code browser has to remember that it belongs to a sign-in",
            state.openedForSignIn.value,
        )
    }

    @Test
    fun `opening for something else is not a sign-in`() {
        val state = OxideBrowserState()
        state.open(verificationUrl)
        assertFalse(state.openedForSignIn.value)
    }

    @Test
    fun `closing forgets both the url and the sign-in`() {
        val state = OxideBrowserState()
        state.open(verificationUrl, forSignIn = true)
        state.close()
        assertNull(state.openUrl.value)
        assertFalse(state.isOpen())
        assertFalse(
            "a stale sign-in flag would close the next browser the moment it opens",
            state.openedForSignIn.value,
        )
    }

    @Test
    fun `an address that is not an address never opens a browser`() {
        for (blank in listOf("", "   ", "\n\t ", "about:blank")) {
            val state = OxideBrowserState()
            state.open(blank)
            assertFalse("\"$blank\" must not open a browser", state.isOpen())
            assertNull(state.openUrl.value)
        }
    }

    @Test
    fun `a browser already open is closed by an address that is not one`() {
        // 反过来的顺序也要钉住：先开着、随后来一个空的，不能留下一块顶在界面上的面板
        val state = OxideBrowserState()
        state.open(verificationUrl, forSignIn = true)
        state.open("   ")
        assertFalse(state.isOpen())
        assertNull(state.openUrl.value)
    }

    @Test
    fun `reopening replaces both the url and the sign-in flag`() {
        val state = OxideBrowserState()
        state.open(verificationUrl, forSignIn = true)
        state.open("https://example.invalid/other", forSignIn = false)
        assertEquals("https://example.invalid/other", state.openUrl.value)
        assertFalse(
            "the second open did not come from a sign-in",
            state.openedForSignIn.value,
        )
    }

    @Test
    fun `the url survives being read from another thread`() {
        // 写的那条链是设备码任务（Dispatchers.IO），读的那条链是它的轮询循环，
        // 两者都不是主线程。读不到就等于登录被立刻取消。
        val state = OxideBrowserState()
        val opened = AtomicReference<Boolean?>(null)
        val read = AtomicReference<Boolean?>(null)

        thread(name = "device-code") {
            state.open(verificationUrl, forSignIn = true)
            opened.set(state.isOpen())
        }.join()
        thread(name = "token-poll") { read.set(state.isOpen()) }.join()

        assertEquals(true, opened.get())
        assertEquals(
            "the polling loop must see the browser from its own thread",
            true,
            read.get(),
        )
    }

    @Test
    fun `the open flow is the only one that closes itself`() {
        // 登录还在跑：不能收，否则授权页刚打开就没了
        assertFalse(
            oxideBrowserShouldAutoClose(
                isOpen = true,
                openedForSignIn = true,
                signInRunning = true,
            )
        )
        // 登录跑完了：收。旧实现在这一步靠 backToMain() 把网页那一项弹掉，
        // 现在网页不是栈上的条目，不补这一步面板就会一直盖着已经启动的游戏
        assertTrue(
            oxideBrowserShouldAutoClose(
                isOpen = true,
                openedForSignIn = true,
                signInRunning = false,
            )
        )
    }

    @Test
    fun `a browser the user opened is never closed behind their back`() {
        // 用户自己点开的网页：任务表是空的，自动收会把网页立刻关掉
        assertFalse(
            oxideBrowserShouldAutoClose(
                isOpen = true,
                openedForSignIn = false,
                signInRunning = false,
            )
        )
        // 已经关掉了就没什么可收的
        assertFalse(
            oxideBrowserShouldAutoClose(
                isOpen = false,
                openedForSignIn = true,
                signInRunning = false,
            )
        )
    }

    @Test
    fun `open means open, whatever the address looks like`() {
        assertTrue(oxideBrowserIsOpen(verificationUrl))
        assertTrue(oxideBrowserIsOpen("https://example.invalid/path?a=b#c"))
        assertFalse("about:blank is where the browser parks, not a page", oxideBrowserIsOpen("about:blank"))
        assertFalse(oxideBrowserIsOpen(""))
        assertFalse(oxideBrowserIsOpen("  "))
        assertFalse(oxideBrowserIsOpen(null))
    }
}