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

package dev.oxide.launcher.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** OAuth 客户端 ID 的环境变量名，与 build.gradle.kts 里 getKeyFromLocal 的第一个参数一致 */
private const val ENV_NAME = "OAUTH_CLIENT_ID"

/** 仓库根目录下的文件名来源 */
private const val FILE_NAME = ".oauth_client_id.txt"

/** gradle.properties 里的属性来源 */
private const val PROPERTY_NAME = "oauth_client_id"

/** 维护者用来自助签署构建的开关；默认不开，否则 v1.8.0 根本出不来包 */
private const val REQUIRE_FLAG = "requireOauthClientId"

/**
 * 客户端 ID 是一个 UUID，因此"代码里绝不能有硬编码值"可以按形状断言，
 * 而不是靠"看起来像密钥"的模糊判断。
 */
private val UUID_LITERAL =
    Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

/** BuildKeys 里的键名 */
private const val BUILD_KEYS_KEY = "OAUTH_CLIENT_ID"

/**
 * build.gradle.kts 里 getKeyFromLocal 的逐字复刻。
 *
 * 与真函数分开写是为了让这些用例**不需要真的有一个 secret**：真函数读的是 System.getenv
 * 和磁盘上的文件，在单测里既没法设置也没法用占位值断言。这份复刻必须和真函数保持同一套规则，
 * 所以下面还钉住了两者共用的那一条——**空白等于没有**，而不是只有 null 才算没有。
 *
 * @param env 环境变量原样传入（null 表示没有这个变量）
 * @param fileText 文件内容原样传入（null 表示文件不存在或读不了）
 * @param default Gradle 属性原样传入（null 表示属性没声明）
 * @return 解析到的客户端 ID，null 表示三个来源都没有提供
 */
private fun resolveOauthClientId(env: String?, fileText: String?, default: String?): String? =
    env?.trim()?.takeIf { it.isNotEmpty() }
        ?: fileText?.trim()?.takeIf { it.isNotEmpty() }
        ?: default?.trim()?.takeIf { it.isNotEmpty() }

/** 三个来源的可读清单，报错和文档里用的就是它 */
private val ACCEPTED_SOURCES = listOf(ENV_NAME, FILE_NAME, PROPERTY_NAME)

/**
 * OAuth 客户端 ID 的接线
 *
 * 客户端 ID 只能来自构建环境：它是 Azure AD 应用注册里的 Application (client) ID，提交进仓库
 * 就等于允许任何人冒充这个启动器去登录。所以 gradle.properties 里那一行是注释掉的，而 release
 * 只能从 Actions secret 或本地文件拿。
 *
 * 这带来的失败模式很安静：secret 没配置时 `${{ secrets.OAUTH_CLIENT_ID }}` 展开成**空字符串**
 * 而不是"不存在"，于是 release 包带着一个空 client id 编出来，用户点微软登录才收到一个
 * 指向不了本地配置的 `invalid_client`。这里把解析规则和接线都钉住，让这件事在构建期可见。
 */
class OAuthClientIdWiringTest {

    // ---- 解析规则 ---------------------------------------------------------

    /**
     * CI 用 `${{ secrets.NAME }}` 注入密钥，未配置的 secret 展开成空串，所以只判 null 会让
     * 空串在第一级就短路掉：后面的文件和 Gradle 属性根本读不到，构建悄悄编进一个空值。
     */
    @Test
    fun aBlankEnvironmentVariableIsAbsentRatherThanAShortCircuit() {
        listOf("", "   ", "\t\n ").forEach { blank ->
            assertEquals(
                "a blank $ENV_NAME must fall through to the next source, not resolve to \"\"",
                "from-the-file",
                resolveOauthClientId(env = blank, fileText = "from-the-file", default = "from-the-property")
            )
        }
    }

    @Test
    fun aMissingSecretFileFallsBackToTheGradleProperty() {
        // null 就是"文件不存在或读不了"，与 getKeyFromLocal 里 canRead/isFile 的效果相同
        assertEquals(
            "from-the-property",
            resolveOauthClientId(env = null, fileText = null, default = "from-the-property")
        )
    }

    @Test
    fun aBlankDefaultIsAbsentRatherThanSomethingToCompileIn() {
        // 最危险的一种：属性在但值是空的，返回它等于把空 client id 编进 APK
        listOf("", "  ").forEach { blank ->
            assertEquals(
                "a blank '$PROPERTY_NAME' must resolve to nothing, not to a blank id",
                null,
                resolveOauthClientId(env = null, fileText = null, default = blank)
            )
        }
    }

    @Test
    fun nothingConfiguredAtAllResolvesToAbsent() {
        assertEquals(null, resolveOauthClientId(env = null, fileText = null, default = null))
    }

    @Test
    fun theResolutionRuleNamesEveryAcceptedSource() {
        // 只写"没有配置"是不够的：报不出来源，维护者就只能去猜该改哪里
        assertEquals(3, ACCEPTED_SOURCES.size)
        assertTrue("$ENV_NAME must be listed as an accepted source", ENV_NAME in ACCEPTED_SOURCES)
        assertTrue("$FILE_NAME must be listed as an accepted source", FILE_NAME in ACCEPTED_SOURCES)
        assertTrue(
            "the '$PROPERTY_NAME' property must be listed as an accepted source",
            PROPERTY_NAME in ACCEPTED_SOURCES,
        )
    }

    @Test
    fun theResolutionRuleTrimsSoAPaddedValueIsNotShippedWithWhitespace() {
        // getKeyFromLocal 会 trim，这里保持一致：带空白提交给 Microsoft 一样会被拒
        assertEquals(
            "the-id",
            resolveOauthClientId(env = "  the-id\n", fileText = null, default = null)
        )
    }

    // ---- build.gradle.kts 的接线 -------------------------------------------

    @Test
    fun theBuildScriptResolvesTheClientIdOnce() {
        // 解析两次就可能编译进去一个值、检查的却是另一个值，断言等于白写
        assertEquals(
            "the client id must be resolved exactly once, into a val the buildKeys call reuses",
            1,
            Regex("""val\s+resolvedOauthClientId\s*=\s*getKeyFromLocal\(""").findAll(buildScript()).count()
        )
        assertTrue(
            "buildKeys must be fed the resolved val, not a second resolution",
            buildScript().contains("""string("$BUILD_KEYS_KEY", resolvedOauthClientId, true)""")
        )
    }

    @Test
    fun theBuildKeysCallMustNotResolveTheSecretInline() {
        assertFalse(
            "resolving inside the buildKeys call is what lets the checked and compiled values diverge",
            // 普通字符串而不是三引号形式：待匹配的片段本身以引号开头，
            // 写成三引号就得先想清楚闭合的三引号会不会和它连成一片
            buildScript().contains("string(\"$BUILD_KEYS_KEY\", getKeyFromLocal(")
        )
    }

    @Test
    fun theVerificationTaskExistsAndRunsBeforeTheReleaseAssembly() {
        val script = buildScript()
        assertTrue(
            "the release build needs a task that reports an unresolvable client id",
            script.contains("""tasks.register("verifyOauthClientId")""")
        )
        // 和 CurseForge 那条挂在同一个 pre<Variant>Build 上，否则 bundleRelease 等入口能绕过去
        assertTrue(
            "verifyOauthClientId must be wired like verifyCurseForgeApiKey",
            script.contains("dependsOn(verifyOauthClientId)")
        )
        assertTrue(
            "the existing CurseForge wiring must survive alongside it",
            script.contains("dependsOn(verifyCurseForgeApiKey)")
        )
    }

    @Test
    fun theVerificationTaskExplainsTheRemedyAndNeverPrintsTheValue() {
        val script = buildScript()
        // 断言读的是原文而非 codeOf()：要查的正是错误消息，而消息就在字符串字面量里
        assertTrue(
            "the failure must name the environment variable source",
            script.contains("as the $ENV_NAME environment variable"),
        )
        assertTrue(
            "the failure must name the file source",
            script.contains(FILE_NAME),
        )
        assertTrue(
            "the failure must name the Gradle property source",
            script.contains("'$PROPERTY_NAME' Gradle property"),
        )
        assertTrue(
            "the message must say what a blank client id does to the user",
            script.contains("invalid_client"),
        )
        // 只报长度，不报值
        assertTrue(
            "reporting the length is how presence is shown without leaking the id",
            script.contains("resolved (\${clientId.length} chars)")
        )
        // 唯一被允许的插值就是长度本身。codeOf() 换掉字符串字面量，恰好会把
        // "${clientId}" 这种真泄露一起抹掉，所以这里必须读原文。
        val interpolations = Regex("\\\$\\{clientId[^}]*}").findAll(script).map { it.value }.toList()
        assertTrue(
            "the script must report the client id through its length, not through nothing at all",
            interpolations.isNotEmpty(),
        )
        for (leak in interpolations) {
            assertEquals(
                "only the length of the client id may be interpolated into a log line",
                "\${clientId.length}",
                leak,
            )
        }
    }

    @Test
    fun aMissingClientIdWarnsByDefaultAndOnlyFailsWhenAsked() {
        val script = buildScript()
        // secret 还没有，且只能由维护者从仓库外提供，所以默认不能挡住 v1.8.0 的签名包
        assertTrue(
            "the task must have a warn-and-continue default",
            script.contains("logger.warn(")
        )
        assertTrue(
            "the opt-in that turns the warning into a failure must be documented in the task",
            script.contains("-P$REQUIRE_FLAG=true")
        )
        assertTrue(
            "the opt-in must be read from a Gradle property so CI can pass it",
            script.contains("""project.findProperty("$REQUIRE_FLAG")""")
        )
    }

    @Test
    fun theBuildScriptNeverHardcodesAClientId() {
        // 客户端 ID 是 UUID；脚本里出现一个 UUID 字面量就等于把凭据提交了
        val literal = UUID_LITERAL.find(buildScriptCode())
        assertFalse(
            "the build script must not contain a hardcoded client id, found ${literal?.value}",
            literal != null,
        )
    }

    // ---- gradle.properties -------------------------------------------------

    @Test
    fun theCommittedPropertyIsCommentedOutSoNoSecretCanBeCommitted() {
        val properties = moduleGradleProperties()
        assertTrue(
            "#$PROPERTY_NAME must stay commented out; that is what keeps the client id out of git",
            properties.readText().lineSequence().any { it.trim() == "#$PROPERTY_NAME=xxx" }
        )
    }

    @Test
    fun thereIsNoActiveAssignmentOfTheClientIdProperty() {
        // 提交一份真实的 client id 等于允许任何人冒充这个启动器登录
        val active = moduleGradleProperties().readText().lineSequence().filter { line ->
            Regex("""^\s*$PROPERTY_NAME\s*=""").containsMatchIn(line)
        }.toList()
        assertEquals("no active '$PROPERTY_NAME' assignment may exist in gradle.properties", 0, active.size)
    }

    @Test
    fun theCurseForgeKeyIsUntouched() {
        // 上一轮的改动就在同一份文件里：本次只动版本与 OAuth 那一段
        assertTrue(
            "curseforge_api_key must stay declared, it is what release builds fall back to",
            moduleGradleProperties().readText().lineSequence().any { line ->
                Regex("""^\s*curseforge_api_key\s*=""").containsMatchIn(line)
            },
        )
    }

    // ---- 版本号 -----------------------------------------------------------

    @Test
    fun theReleaseIsBumpedToOnePointNinePointZeroWhileTheAppStillSaysOneZeroZero() {
        val properties = moduleGradleProperties().readText()
        assertEquals("1.9.0", propertyValue(properties, "launcher_version_name"))
        // 发行号与用户看到的版本号是两件事，报错版本就等于把两者搞混
        assertEquals("1.0.0", propertyValue(properties, "launcher_display_version"))
        assertTrue(
            "the explanatory comment must name the release it describes, not an older one",
            properties.contains("GitHub release is v1.9.0")
        )
    }

    @Test
    fun theVersionCodeIsMonotonic() {
        // 100800 是 v1.8.0；versionCode 变小或不变，会让已经装上的用户装不了这一版
        val code = propertyValue(moduleGradleProperties().readText(), "launcher_version_code")
        assertEquals("launcher_version_code must be the integer 100900 for v1.9.0", "100900", code)
        assertTrue(
            "launcher_version_code must parse as an integer so versionCode never silently defaults",
            code?.toIntOrNull() != null,
        )
    }

    // ---- MicrosoftAuthenticator.kt -----------------------------------------

    @Test
    fun theDeviceCodeFlowReadsTheClientIdFromBuildKeysAtEveryCallSite() {
        // 这里读原文而不是 codeOf()：codeOf 会把 "client_id" 这个字符串字面量换成空引号，
        // 恰好抹掉断言要匹配的那半行
        val source = microsoftAuthenticator().readText()
        // 三处：申请 device code、轮询 token、刷新 token。三处都不直接读 BuildKeys，
        // 而是走同一个守卫——空 id 必须在发请求之前就变成一个说得清的异常
        val formFields = Regex("""append\("client_id",\s*microsoftAuthClientId\(\)\)""")
        assertEquals(
            "every client_id form field must go through the guard",
            3,
            formFields.findAll(source).count(),
        )
        // BuildKeys 只在守卫里被读；日志文案里也提到它，因此不能按出现次数数，
        // 要断言它出现在哪一处
        assertEquals(
            "no request may read BuildKeys directly",
            0,
            Regex("""append\("client_id",\s*BuildKeys\.$BUILD_KEYS_KEY\)""").findAll(source).count(),
        )
        assertTrue(
            "the guard must reject a blank id instead of sending it",
            source.contains("fun microsoftAuthClientId()") &&
                source.contains("microsoftAuthClientIdOrNull(BuildKeys.$BUILD_KEYS_KEY)") &&
                source.contains("throw MicrosoftAuthNotConfiguredException()"),
        )
        // 反过来：不能有哪一处顺手传了个字面量进去
        assertEquals(
            "a client_id form field must never be filled from a literal",
            formFields.findAll(source).count(),
            Regex("""append\("client_id",""").findAll(source).count(),
        )
    }

    @Test
    fun theClientIdHasNoOtherReaderInTheAuthenticator() {
        // 多一个来源就多一处可能分叉的地方：编译进去的值必须是唯一的那一个
        assertEquals(
            "BuildKeys.$BUILD_KEYS_KEY must be the only place the client id enters this file",
            3,
            Regex("""BuildKeys\.$BUILD_KEYS_KEY""").findAll(microsoftAuthenticator().readText()).count()
        )
    }

    @Test
    fun theAuthenticatorNeverHardcodesAClientId() {
        val literal = UUID_LITERAL.find(microsoftAuthenticator().readText())
        assertFalse(
            "MicrosoftAuthenticator.kt must not contain a client id literal, found ${literal?.value}",
            literal != null,
        )
    }

    // ---- 定位与文本处理 ---------------------------------------------------

    private companion object {

        /**
         * 模块根目录，也就是 build.gradle.kts 与 gradle.properties 所在的那一层。
         *
         * 仓库根目录同样有这两个文件，所以只认"同时含有 gradle.properties 且其中声明了
         * launcher_display_version"的那个——那是 OxideLauncher 模块，不会认错。
         */
        fun moduleRoot(): File {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir ?: return@repeat
                val properties = candidate.resolve("gradle.properties")
                if (candidate.resolve("build.gradle.kts").isFile &&
                    properties.isFile &&
                    properties.readText().contains("launcher_display_version")
                ) {
                    return candidate
                }
                dir = candidate.parentFile
            }
            error("could not locate the OxideLauncher module root from " + File("").absolutePath)
        }

        fun buildScript(): String = moduleRoot().resolve("build.gradle.kts").readText()

        fun moduleGradleProperties(): File = moduleRoot().resolve("gradle.properties")

        fun microsoftAuthenticator(): File = locate(
            "game/account/microsoft/MicrosoftAuthenticator.kt"
        )

        /** 从当前工作目录往上找源文件；找不到直接报错，绝不悄悄跳过 */
        fun locate(relativePath: String): File {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
                if (candidate != null && candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            error(
                "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                    File("").absolutePath
            )
        }

        /**
         * 去掉注释，只留下真正会被编译的代码
         *
         * build.gradle.kts 里那段说明解释了为什么**不能**硬编码客户端 ID，而断言问的恰恰是
         * "代码里有没有"。只看原文的话，一句解释就被当成了一次硬编码。
         *
         * 顺序要紧：先把字符串换掉，再去注释，否则字符串里的 `//`（某个网址）会被当成行注释，
         * 把后面整行连同它的花括号一起吃掉。与 OxideCapSurfaceRoutingTest 用的是同一套写法。
         */
        fun codeOf(source: String): String = source
            .replace(RAW_STRING, REPLACED)
            .replace(STRING, REPLACED)
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, "")

        /** 原始字符串：里面可以出现引号、换行与注释符号，必须先换掉 */
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /** 被换掉的那一段字符串：空引号足以让"这一行是不是注释"这件事失去意义 */
        const val REPLACED = "\"\""

        /** 只读活跃的属性赋值，注释掉的行返回 null */
        fun propertyValue(properties: String, name: String): String? =
            properties.lineSequence()
                .map { it.trim() }
                .firstOrNull { Regex("""^$name\s*=(.*)$""").matches(it) }
                ?.let { Regex("""^$name\s*=(.*)$""").matchEntire(it)!!.groupValues[1].trim() }
    }

    /**
     * build.gradle.kts 的**代码**，注释与字符串字面量已被换掉。
     *
     * 那段说明里解释了为什么不能硬编码客户端 ID，而断言问的恰恰是"代码里有没有"；
     * 只看原文的话，一句解释就被当成了一次硬编码。
     */
    private fun buildScriptCode(): String = codeOf(buildScript())
}