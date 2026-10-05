/*
 * Oxide Launcher
 * Copyright (C) 2025 Star1xr and contributors
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

package dev.oxide.launcher.game.account.microsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 微软登录整条链路的接线检查
 *
 * 这一组测试守的是"请求打到了哪个端点、带了哪些参数"——链路里最容易坏、又最难在
 * 设备上看出来的部分：端点选错了，登录流程会一路走到最后才失败，或者更糟，
 * 令牌过期后被误判成凭据失效。
 */
class MicrosoftAuthChainTest {

    // ---------------------------------------------------------------- 客户端 id

    @Test
    fun aBlankClientIdIsAbsentRatherThanAnEmptyCredential() {
        assertNull(microsoftAuthClientIdOrNull(""))
        assertNull(microsoftAuthClientIdOrNull("   "))
        assertNull(microsoftAuthClientIdOrNull("\n\t "))
    }

    @Test
    fun aConfiguredClientIdIsPassedThroughUntouched() {
        assertEquals("abc", microsoftAuthClientIdOrNull("abc"))
        assertEquals("00000000402b5328", microsoftAuthClientIdOrNull("00000000402b5328"))
    }

    @Test
    fun theMissingClientIdHasItsOwnExceptionWithAnActionableMessage() {
        val error = MicrosoftAuthNotConfiguredException()
        assertTrue(
            "the message must name what is missing",
            error.message!!.contains("client", ignoreCase = true)
        )
        assertFalse("it must not read like a network failure", error.message!!.contains("timeout"))
    }

    @Test
    fun everyRequestGoesThroughTheClientIdGuardInsteadOfReadingBuildKeysDirectly() {
        val code = codeOf(AUTHENTICATOR)
        // 三处请求：设备码、轮询换令牌、刷新令牌
        assertEquals(
            "all three request sites must go through the guard",
            3,
            Regex("append\\(\"client_id\",\\s*microsoftAuthClientId\\(\\)\\)").findAll(code).count()
        )
        assertFalse(
            "no request may read BuildKeys directly, a blank id must raise instead",
            code.contains("append(\"client_id\", BuildKeys.OAUTH_CLIENT_ID)")
        )
    }

    // ---------------------------------------------------------------- 租户与作用域

    @Test
    fun theTenantIsConsumersSoPersonalMicrosoftAccountsWork() {
        assertTrue(codeOf(AUTHENTICATOR).contains("private const val TENANT = \"/consumers\""))
        assertFalse(
            "/common would route personal accounts to a multi-tenant lookup that rejects them",
            codeOf(AUTHENTICATOR).contains("\"/common\"")
        )
    }

    @Test
    fun everyEndpointSitsUnderTheConsumersTenant() {
        val code = codeOf(AUTHENTICATOR)
        assertTrue(
            "device code endpoint",
            code.contains("\"$MICROSOFT_AUTH_URL\$TENANT/oauth2/v2.0/devicecode\"")
        )
        assertTrue(
            "device code polling endpoint",
            code.contains("\"$MICROSOFT_AUTH_URL\$TENANT/oauth2/v2.0/token\"")
        )
    }

    @Test
    fun theScopesAskForXboxLiveSigninOfflineAccessAndTheIdentityScopes() {
        assertTrue(
            codeOf(AUTHENTICATOR).contains(
                "listOf(\"XboxLive.signin\", \"offline_access\", \"openid\", \"profile\", \"email\")"
            )
        )
    }

    @Test
    fun theXboxLiveScopeIsRequestedOnBothTheGrantAndTheRefresh() {
        val code = codeOf(AUTHENTICATOR)
        assertEquals(
            "both the device-code request and the refresh must carry the scope",
            2,
            Regex("append\\(\"scope\", SCOPES\\.joinToString\\(\" \"\\)\\)").findAll(code).count()
        )
    }

    @Test
    fun theTokenRequestCarriesNoStrayTenantParameter() {
        // v2.0 的 tenant 由 URL 决定；再塞一个 tenant 表单字段只会让人误以为端点是可配的
        assertFalse(codeOf(AUTHENTICATOR).contains("append(\"tenant\""))
    }

    // ---------------------------------------------------------------- 刷新链路

    @Test
    fun theRefreshTokenGoesBackToTheEndpointThatIssuedIt() {
        val refresh = blockOf(AUTHENTICATOR, "private suspend fun refreshAccessToken(")
        assertTrue(
            "a v2.0 refresh token must be redeemed at the v2.0 endpoint, " +
                "login.live.com/oauth20_token.srf belongs to the Live Connect token family",
            refresh.contains("\"$MICROSOFT_AUTH_URL\$TENANT/oauth2/v2.0/token\"")
        )
        assertFalse(
            "the Live Connect token endpoint cannot refresh a v2.0 token; it answers invalid_grant " +
                "and the account then looks signed out for no reason",
            refresh.contains("oauth20_token.srf")
        )
    }

    @Test
    fun theRefreshSendsTheGrantTypeItActuallyUses() {
        val refresh = blockOf(AUTHENTICATOR, "private suspend fun refreshAccessToken(")
        assertTrue(refresh.contains("append(\"grant_type\", \"refresh_token\")"))
        assertTrue(refresh.contains("append(\"refresh_token\", refreshToken)"))
    }

    @Test
    fun anInvalidGrantIsStillReportedAsExpiredCredentials() {
        val refresh = blockOf(AUTHENTICATOR, "private suspend fun refreshAccessToken(")
        assertTrue(
            "a genuinely revoked refresh token must still end the session cleanly",
            refresh.contains("CredentialsExpiredException")
        )
    }

    // ---------------------------------------------------------------- Xbox Live / XSTS

    @Test
    fun theXboxLiveRpsTicketIsSubmittedBareFirstAndTheDottedFormIsTheFallback() {
        val xbl = blockOf(AUTHENTICATOR, "private suspend fun authenticateXBL(")
        val bare = xbl.indexOf("requestXblToken(accessToken)")
        val dotted = xbl.indexOf("requestXblToken(\"d=\$accessToken\")")
        assertTrue("the bare RpsTicket must be tried first for a v2.0 token", bare >= 0)
        assertTrue("the d= prefix must remain as a fallback", dotted > bare)
    }

    @Test
    fun theRpsTicketFallbackOnlyTriggersOnABadRequest() {
        val xbl = blockOf(AUTHENTICATOR, "private suspend fun authenticateXBL(")
        assertTrue(
            "retrying on any status would mask real failures such as a revoked token",
            xbl.contains("if (e.response.status.value == 400)")
        )
    }

    @Test
    fun theChainKeepsTheMinecraftRetailRelyingParty() {
        val xsts = blockOf(AUTHENTICATOR, "private suspend fun authenticateXSTS(")
        assertTrue(xsts.contains("relyingParty = \"rp://api.minecraftservices.com/\""))
        assertTrue(xsts.contains("sandboxId = \"RETAIL\""))
    }

    @Test
    fun theChainEndsAtMinecraftServicesWithTheXboxTokenAndChecksOwnership() {
        val code = codeOf(AUTHENTICATOR)
        assertTrue(code.contains("/authentication/login_with_xbox"))
        assertTrue(code.contains("\"XBL3.0 x=\${"))
        assertTrue(code.contains("/entitlements/mcstore"))
    }

    @Test
    fun theXboxAccountFailuresAreStillMappedToTheirOwnMessages() {
        val xsts = blockOf(AUTHENTICATOR, "private suspend fun authenticateXSTS(")
        for (xErr in listOf("2148916227", "2148916229", "2148916233", "2148916238")) {
            assertTrue("XErr $xErr must stay mapped", xsts.contains("\"$xErr\""))
        }
    }

    @Test
    fun theMissingClientIdIsSurfacedToTheUserRatherThanSwallowed() {
        val utils = codeOf(ACCOUNT_UTILS)
        assertTrue(
            "the login error handler must render the actionable message",
            utils.contains("is MicrosoftAuthNotConfiguredException -> th.toLocal()")
        )
    }

    @Test
    fun theActionableMessageExistsAndNamesTheSecret() {
        val strings = locate("res/values/strings.xml").readText()
        val entry = Regex("<string name=\"account_microsoft_not_configured\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
        assertTrue("the string must exist", entry != null)
        val value = entry!!.groupValues[1]
        assertTrue("it must name the secret to register", value.contains("OAUTH_CLIENT_ID"))
        assertTrue("it must not blame the user's account", value.contains("account"))
    }

    // ---------------------------------------------------------------- 工具

    private fun codeOf(source: File): String = strip(source.readText())

    /**
     * 去掉注释，保留字符串字面量
     *
     * 端点本身就是字符串字面量（`"rp://api.minecraftservices.com/"` 里还有 `//`），
     * 所以不能拿正则去抠 `//`——那会把端点从中间截断，再"顺手"删掉后面半个文件。
     * 这里走一个真正的词法状态机：字符串、原始字符串、字符字面量、块注释、行注释各自成态。
     */
    private fun strip(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else ' '
            when {
                // 原始字符串 """ ... """ —— Kotlin 里字符串可以嵌套，因此按深度数
                c == '"' && next == '"' && source.startsWith("\"\"\"", i) -> {
                    val end = rawStringEnd(source, i)
                    out.append(source, i, end)
                    i = end
                }
                // 普通字符串：转义要一起吃掉，避免 \" 被当成收尾
                c == '"' -> {
                    var j = i + 1
                    while (j < source.length) {
                        if (source[j] == '\\') j += 2 else if (source[j] == '"') {
                            j++
                            break
                        } else j++
                    }
                    out.append(source, i, j)
                    i = j
                }
                c == '\'' -> {
                    var j = i + 1
                    while (j < source.length) {
                        if (source[j] == '\\') j += 2 else if (source[j] == '\'') {
                            j++
                            break
                        } else j++
                    }
                    out.append(source, i, j)
                    i = j
                }
                c == '/' && next == '*' -> {
                    i = blockCommentEnd(source, i)
                }
                c == '/' && next == '/' -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    i = end
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * 原始字符串的结束位置（开头的 `"""` 之后）
     */
    private fun rawStringEnd(source: String, start: Int): Int {
        var i = start + 3
        var depth = 1
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> {
                    depth++
                    i += 3
                }
                source[i] == '"' && depth > 1 -> {
                    // 嵌套的普通字符串，先跳过它
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                }
                source[i] == '"' -> depth--
                source[i] == '$' && i + 1 < source.length &&
                    (source[i + 1] == '{' || source[i + 1] == '(') -> {
                    // 插值里的代码照常输出，其中若出现注释也不该被误吞
                    val open = source[i + 1]
                    val close = if (open == '{') '}' else ')'
                    var depthOfTemplate = 1
                    i += 2
                    while (i < source.length && depthOfTemplate > 0) {
                        when {
                            source[i] == open -> depthOfTemplate++
                            source[i] == close -> depthOfTemplate--
                            source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it } - 1
                            source.startsWith("/*", i) -> i = blockCommentEnd(source, i) - 1
                        }
                        i++
                    }
                }
            }
            if (depth == 0) return i + 1
            i++
        }
        return source.length
    }

    /**
     * 块注释的结束位置（跳过开头的两个斜杠星号与结尾的星号斜杠）
     * 注释里出现字符串字面量不影响结束位置，所以直接找收尾那对符号即可
     */
    private fun blockCommentEnd(source: String, start: Int): Int {
        var i = start + 2
        while (i < source.length - 1) {
            if (source[i] == '*' && source[i + 1] == '/') return i + 2
            i++
        }
        return source.length
    }

    /**
     * 取出某个顶层声明的花括号主体
     *
     * 括号计数必须跳过字符串：`"XBL3.0 x=${a};${b}"` 里有两个 `{`，
     * 天真的计数会一路数偏，最后报告"括号不平衡"。
     */
    private fun blockOf(source: File, signature: String): String {
        val text = strip(source.readText())
        val start = text.indexOf(signature)
        assertTrue("could not find $signature", start >= 0)
        val from = text.indexOf('{', start)
        var depth = 0
        var i = from
        while (i < text.length) {
            when {
                text[i] == '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                }
                text[i] == '{' -> depth++
                text[i] == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(from, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces in $signature")
    }

    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            for (prefix in listOf("src/main/", "")) {
                val candidate = File(dir, prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = dir?.parentFile
        }
        error("could not locate $relativePath from ${File("").absolutePath}")
    }

    private companion object {
        val AUTHENTICATOR = File(
            "src/main/java/dev/oxide/launcher/game/account/microsoft/MicrosoftAuthenticator.kt"
        )
        val ACCOUNT_UTILS = File(
            "src/main/java/dev/oxide/launcher/game/account/AccountUtils.kt"
        )
    }
}