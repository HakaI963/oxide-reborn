import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
    id("kotlinx-serialization")
    id("kotlin-parcelize")
    id("com.movtery.buildkeys")
    // Screenshot testing. Paparazzi runs Compose through layoutlib on the JVM, so these
    // tests need no emulator and no device.
    //
    // Version rationale, from this project's actual toolchain (see gradle/libs.versions.toml):
    //   Gradle 9.5.0 / AGP 9.3.0 / Kotlin 2.4.20 / Compose BOM 2026.09.00.
    // Paparazzi 2.0.0-alpha05.1 is the newest release at all, and the first one whose notes
    // say "[Gradle Plugin] Android Gradle Plugin 9.0.0". Its own build uses Gradle 9.3.1,
    // Kotlin 2.3.0 and layoutlib 16.2.3. The deltas against us are all forward-compatible
    // directions rather than breaking ones:
    //   - AGP 9.0.0 -> 9.3.0 is a minor bump inside AGP 9. Every AGP API the plugin touches
    //     was verified to still exist in 9.3.0's gradle-api: AndroidComponentsExtension,
    //     HasUnitTest.unitTest, Component.instrumentation, FramesComputationMode,
    //     InstrumentationScope, Sources.kotlin/res/assets, and TestOptions.targetSdk
    //     (which is what the plugin reads to derive the render SDK).
    //   - Gradle 9.3.1 -> 9.5.0 is untested-forward, but the plugin only reaches into
    //     org.gradle internals for UnzipTransform and AbstractTestTask.setTestReporter.
    //   - Kotlin 2.3.0 -> 2.4.20 does not matter for test compilation: Paparazzi's runtime
    //     artifact declares no Compose dependency and no compiler, so test sources are
    //     compiled by *our* Kotlin plugin against the Compose BOM we already ship.
    // Anything older than alpha05 (1.3.5, alpha01..alpha04) targets AGP 8.x and will not
    // configure against AGP 9 at all, so this is a floor rather than a choice.
    alias(libs.plugins.paparazzi)
}

val oxidePackageName = "dev.oxide.launcher"
val launcherAPPName = project.findProperty("launcher_app_name") as? String ?: error("The \"launcher_app_name\" property is not set in gradle.properties.")
val launcherDisplayVersion = project.findProperty("launcher_display_version") as? String ?: error("The \"launcher_display_version\" property is not set in gradle.properties.")
val launcherName = project.findProperty("launcher_name") as? String ?: error("The \"launcher_name\" property is not set in gradle.properties.")
val launcherShortName = project.findProperty("launcher_short_name") as? String ?: error("The \"launcher_short_name\" property is not set in gradle.properties.")
val launcherUrl = project.findProperty("url_home") as? String ?: error("The \"url_home\" property is not set in gradle.properties.")

val launcherVersionCode = (project.findProperty("launcher_version_code") as? String)?.toIntOrNull() ?: error("The \"launcher_version_code\" property is not set as an integer in gradle.properties.")
val launcherVersionName = project.findProperty("launcher_version_name") as? String ?: error("The \"launcher_version_name\" property is not set in gradle.properties.")

val defaultOAuthClientID = project.findProperty("oauth_client_id") as? String
val defaultStorePassword = project.findProperty("default_store_password") as? String
val defaultKeyPassword = project.findProperty("default_key_password") as? String
val defaultCurseForgeApiKey = project.findProperty("curseforge_api_key") as? String

val projectArch: String = System.getProperty("arch", "all")

/**
 * 按 环境变量 → 仓库根目录下的文件 → Gradle 属性 的顺序解析一个构建期密钥。
 *
 * 每个来源在 **为空白** 时都算“没有提供”，而不只是为 null 时。这点很关键：CI 用
 * `${{ secrets.NAME }}` 注入密钥，而未配置的 secret 会展开成 **空字符串**（不是“不存在”），
 * 所以 `System.getenv` 返回的是 `""` 而不是 null。如果只用 `?:` 串起来，这个空串会在第一级
 * 就短路掉，后面的文件和 Gradle 属性根本不会被读到，构建便悄无声息地编进一个空值——哪怕
 * gradle.properties 里就摆着一份完全可用的默认值。
 *
 * 这正是 CurseForge 只在 release 里失效的原因：ci.yml 从不设置 CURSEFORGE_API_KEY，
 * 于是 debug 回落到 gradle.properties 拿到真密钥；而 release.yml 总会设置它，未配置的
 * secret 变成空串，debug 能用的密钥在 release 里变成了空字符串，最终连 x-api-key 头都不会带。
 */
fun getKeyFromLocal(envKey: String, fileName: String? = null, default: String? = null): String {
    fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    return System.getenv(envKey).nonBlankOrNull()
        ?: fileName?.let {
            val file = File(rootDir, it)
            if (file.canRead() && file.isFile) file.readText().nonBlankOrNull() else null
        }
        ?: default.nonBlankOrNull()
        ?: run {
            logger.warn("BUILD: $envKey not set; related features may throw exceptions.")
            ""
        }
}

/**
 * CurseForge 客户端标识（每个 CurseForge 客户端都自带一份），只作为 x-api-key 请求头发往
 * CurseForge 主机，从不记录日志、也从不在界面显示。
 *
 * 提前解析一次，让编译进 BuildKeys 的值和下面 [verifyCurseForgeApiKey] 检查的值**永远是同一个**，
 * 否则断言就可能检查不到真正被打包进去的东西。
 */
val resolvedCurseForgeApiKey = getKeyFromLocal("CURSEFORGE_API_KEY", ".curseforge_api.txt", defaultCurseForgeApiKey)

/**
 * 构建期护栏：release 构建不允许带着空的 CurseForge 客户端标识产出。
 *
 * 空密钥会让 `curseForgeAuthHeaders` 直接返回空列表（它对空值返回空是为了避免误导调用方），
 * 于是每个请求都不带 x-api-key，CurseForge 一律回 403，表现为“CurseForge 在 release 里坏了”。
 * 这个检查必须挂在 pre<Variant>Build 上，而不是只挂在 assembleRelease 上，否则
 * `bundleRelease`、单测等其它 release 入口仍能绕过它。
 */
val verifyCurseForgeApiKey = tasks.register("verifyCurseForgeApiKey") {
    group = "verification"
    description = "Fails a release build if the CurseForge API key resolves to a blank value."
    // 立即捕获，任务执行时不再回头去读构建脚本的局部状态
    val key = resolvedCurseForgeApiKey
    doFirst {
        if (key.isBlank()) {
            throw GradleException(
                "CURSEFORGE_API_KEY resolved to a blank value, so this APK would ship without an " +
                    "x-api-key header and CurseForge would reject every request with HTTP 403. " +
                    "Provide it as the Gradle property 'curseforge_api_key', as the " +
                    "CURSEFORGE_API_KEY environment variable, or as a .curseforge_api.txt file. " +
                    "A blank value in any of those counts as absent and falls back to the next " +
                    "source, so an unset CI secret no longer silently empties the key."
            )
        }
        // 只打印长度，不打印密钥本身
        logger.lifecycle("[verifyCurseForgeApiKey] CurseForge client identifier resolved (${key.length} chars).")
    }
}

/**
 * Oxide Launcher release signing material.
 *
 * The keystore is deliberately **not** committed. CI (or a local release build) materialises
 * `oxide_launcher.jks` before Gradle configures, and the credentials come from the environment
 * (`STORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS`). When no keystore is present the release build
 * type is left unsigned instead of failing the whole configuration.
 */
val releaseKeystore = file("oxide_launcher.jks")
val releaseKeystoreAvailable = releaseKeystore.isFile
val releaseStorePassword = getKeyFromLocal("STORE_PASSWORD", ".store_password.txt", defaultStorePassword)
val releaseKeyPassword = getKeyFromLocal("KEY_PASSWORD", ".key_password.txt", defaultKeyPassword)
val releaseKeyAlias = getKeyFromLocal("KEY_ALIAS", ".key_alias.txt", "oxide")

/**
 * The keystore file name is fixed, so detect the container format from its content instead:
 * a JKS store starts with the magic 0xFEEDFEED, a PKCS#12 store with the ASN.1 SEQUENCE tag 0x30.
 */
fun detectKeystoreType(store: File): String = try {
    val firstByte = store.inputStream().use { it.read() }
    if (firstByte == 0xFEEDFEED.toInt()) "JKS" else "PKCS12"
} catch (_: Throwable) {
    "JKS"
}

android {
    namespace = oxidePackageName
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    signingConfigs {
        if (releaseKeystoreAvailable) {
            create("releaseBuild") {
                storeFile = releaseKeystore
                storeType = detectKeystoreType(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        } else {
            logger.lifecycle("BUILD: no ${releaseKeystore.name} found, release builds are produced unsigned.")
        }
    }

    defaultConfig {
        applicationId = oxidePackageName
        minSdk = 26
        targetSdk = 34
        versionCode = launcherVersionCode
        versionName = launcherVersionName
        manifestPlaceholders["launcher_name"] = launcherAPPName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("releaseBuild")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        val arch = projectArch.takeIf { it != "all" } ?: return@splits
        abi {
            isEnable = true
            reset()
            when (arch) {
                "arm" -> include("armeabi-v7a")
                "arm64" -> include("arm64-v8a")
                "x86" -> include("x86")
                "x86_64" -> include("x86_64")
            }
        }
    }

    ndkVersion = "25.2.9519653"

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libbytehook.so")
        }
    }

    compileOptions {
        // sora-editor language-textmate
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            //让 android.util.Log 等框架方法在本地单测中返回默认值而非抛出异常
            isReturnDefaultValues = true
            // Paparazzi is happy with both of the above and neither needs changing for it:
            // it installs layoutlib's android.* ahead of the mockable jar rather than instead
            // of it, and it reads resources through its own PrepareResourcesTask pipeline
            // rather than through AGP's merged-res path. The one thing Paparazzi *does* add
            // on its own is preparePaparazzi<Variant>Resources, which is wired to the unit test
            // task by the plugin.
        }
    }

    /*
     * Android Lint runs on every CI build and aborts the build on a *new* finding.
     *
     * lint-baseline.xml records findings that are knowingly accepted rather than fixed blind: the
     * bulk are inherited from the upstream baseline (MissingPermission in the vendored SDL/HID
     * code, unused-resource noise in the vendored HMCL/LWJGL trees), plus MissingTranslation for
     * strings that upstream's Weblate translators have not caught up with yet. Regenerate it
     * deliberately with the "Lint baseline" workflow after reviewing a new lint report. The full
     * report is still published as a CI artifact so every finding stays visible.
     */
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        checkTestSources = true
        sarifReport = true
        htmlReport = true
        xmlReport = true
    }
}

androidComponents {
    onVariants { variant ->
        // release 变体必须能解析出非空的 CurseForge 密钥。用 matching/configureEach 而不是
        // tasks.named，避免在 onVariants 回调过早求值任务名。
        if (variant.buildType == "release") {
            val variantNameCap = variant.name.replaceFirstChar { it.uppercaseChar() }
            tasks.matching { it.name == "pre${variantNameCap}Build" }.configureEach {
                dependsOn(verifyCurseForgeApiKey)
            }
        }
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
                afterEvaluate {
                    val task = tasks.named("merge${variantName}Assets").get() as MergeSourceSetFolders
                    task.inputs.property("lwjglArch", projectArch)
                    task.doLast {
                        val assetsDir = task.outputDir.get().asFile
                        val tag = "JREAssetsCleanup"
                        logger.lifecycle("[$tag] arch: $projectArch")
                        val jreList = listOf("jre-8", "jre-17", "jre-21", "jre-25")
                        jreList.forEach { jreVersion ->
                            val runtimeDir = File("$assetsDir/runtimes/$jreVersion")
                            logger.lifecycle("[$tag] runtimeDir: ${runtimeDir.absolutePath}")
                            runtimeDir.listFiles()?.forEach {
                                if (projectArch != "all" && it.name != "version" && !it.name.contains("universal") && it.name != "bin-$projectArch.tar.xz") {
                                    logger.lifecycle("[$tag] delete: $it : ${it.delete()}")
                                }
                            }
                        }

                        if (projectArch == "all") return@doLast
                        val abi = when (projectArch) {
                            "arm" -> "armeabi-v7a"
                            "arm64" -> "arm64-v8a"
                            "x86" -> "x86"
                            "x86_64" -> "x86_64"
                            else -> return@doLast
                        }
                        val lwjglVersions = file("libs").listFiles { f ->
                            f.name.matches(Regex("lwjgl-\\d+\\.\\d+\\.\\d+-natives-release\\.aar"))
                        }
                            ?.map { Regex("lwjgl-(\\d+\\.\\d+\\.\\d+)-natives-release\\.aar").find(it.name)!!.groupValues[1] }
                            ?: emptyList()
                        lwjglVersions.forEach { version ->
                            val nativesDir = File(assetsDir, "app_runtime/lwjgl/$version/natives")
                            if (nativesDir.isDirectory) {
                                nativesDir.listFiles()?.forEach { dir ->
                                    if (dir.isDirectory && dir.name != abi) {
                                        logger.lifecycle("Removing non-target-arch natives: $dir")
                                        dir.deleteRecursively()
                                    }
                                }
                            }
                        }
                    }
                }

                (output.getFilter(ABI)?.identifier ?: "all").let { abi ->
                    val baseName = "$launcherName-${if (variant.buildType == "release") launcherVersionName else "Debug-$launcherVersionName"}"
                    output.outputFileName = if (abi == "all") "$baseName.apk" else "$baseName-$abi.apk"
                }
            }
        }
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

buildKeys {
    string("OAUTH_CLIENT_ID", getKeyFromLocal("OAUTH_CLIENT_ID", ".oauth_client_id.txt", defaultOAuthClientID), true)
    string("LAUNCHER_NAME", launcherAPPName, true)
    string("LAUNCHER_IDENTIFIER", launcherName, true)
    string("LAUNCHER_SHORT_NAME", launcherShortName, true)
    string("URL_HOME", launcherUrl, true)
    string("CURSEFORGE_API", resolvedCurseForgeApiKey, true)
    string("LAUNCHER_DISPLAY_VERSION", launcherDisplayVersion, true)
    string("BUILD_ARCH", projectArch)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.nav3)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.ktor3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.material.color.utilities)
    implementation(libs.materialKolor)
    implementation(libs.reorderable)
    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui)
    implementation(libs.richtext.ui.material3)
    implementation(platform(libs.editor.bom))
    implementation(libs.editor)
    implementation(libs.editor.language.textmate)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    //Project
    implementation(project(":LayerController"))
    implementation(project(":ColorPicker"))
    implementation(project(":CardGrid"))
    implementation(project(":Terracotta"))
    implementation(project(":InputMap"))
    implementation(project(":Guide"))
    //Utils
    implementation(libs.bytehook)
    implementation(libs.gson)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.zip4j)
    implementation(libs.okio)
    implementation(libs.okhttp)
    implementation(libs.ktor.http)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.minidns.hla)
    implementation(libs.toml4j)
    implementation(libs.maven.artifact)
    implementation(libs.mmkv)
    implementation(libs.fishnet)
    implementation(libs.process.phoenix)
    implementation(libs.lunarcalendar)
    // 本地预置库：libs/ 下的 aar/jar。注意排除 *-sources.jar，避免把源码包打进 APK
    implementation(
        fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"), "exclude" to listOf("*-sources.jar")))
    )
    //Safe
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    //Support
    implementation(libs.proxy.client.android)
    //Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    //Test
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    // Screenshot tests (src/test/java/dev/oxide/launcher/ui/screens/main/oxide/paparazzi).
    // Paparazzi is a JUnit 4 TestRule, so the junit line above is a hard requirement here.
    testImplementation(libs.paparazzi)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

/*
 * Paparazzi configuration
 * -----------------------
 * Paparazzi 2.x deliberately has **no `paparazzi { }` extension block** any more. Reading
 * paparazzi-gradle-plugin 2.0.0-alpha05.1, the plugin's entire configuration surface is a set of
 * `app.cash.paparazzi.*` Gradle properties which it forwards onto the unit test task as system
 * properties (providers.gradlePropertiesPrefixedBy("app.cash.paparazzi") ->
 * test.systemProperties.putAll(...)). The knobs are:
 *
 *   app.cash.paparazzi.reportType               legacy (default) | native
 *   app.cash.paparazzi.nativeReportFrameworks   junit4 (default) | junit5 | both
 *   app.cash.paparazzi.maxPercentDifference     tolerance used by verify
 *   app.cash.paparazzi.overwriteOnMaxPercentDifference
 *
 * They are therefore declared in OxideLauncher/gradle.properties, next to this module's other
 * properties, and *not* here: a block set on a non-existent extension would silently do nothing,
 * and a plausible-looking no-op is worse than a comment.
 *
 * The plugin also injects app.cash.paparazzi:paparazzi into this module's testImplementation by
 * itself. The explicit testImplementation(libs.paparazzi) above is redundant on purpose — it
 * keeps the version pinned in libs.versions.toml rather than inside the plugin's VERSION constant.
 *
 * Recorded goldens land in OxideLauncher/src/test/snapshots/images (PaparazziPlugin.snapshotDir
 * derives that from the unit test Kotlin source root) and the HTML report lands in
 * OxideLauncher/build/reports/paparazzi/debug.
 */

/**
 * The vendored HMCL `GameVersionNumber` reads `assets/game/versions.txt` and
 * `assets/game/version-alias.csv` from the classpath while its static initialiser runs, because it
 * needs the full release history to order legacy snapshots against releases. Those files live in
 * `src/main/assets` and are only merged into the APK, so put the directory itself on the unit test
 * classpath; otherwise the tables would silently stay empty and every comparison between a legacy
 * snapshot and a release would be wrong.
 */
tasks.withType<Test>().configureEach {
    val mainAssetsDir = layout.projectDirectory.dir("src/main/assets").asFile
    if (mainAssetsDir.isDirectory) {
        // AGP assembles the unit test classpath during its own configuration, so the entry has to
        // be appended when the task runs; adding it in configureEach alone is silently discarded.
        doFirst {
            classpath = classpath.plus(files(mainAssetsDir))
        }
    }
}
