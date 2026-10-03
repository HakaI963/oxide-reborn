-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# SDL
-keep class org.libsdl.app.** { *; }

# Launcher
-keep class org.lwjgl.glfw.CallbackBridge {
    *;
}
-keep class com.oracle.dalvik.VMLauncher {
    *;
}

#
## Hilt
#-keep class dagger.hilt.** { *; }
#-keep class javax.inject.** { *; }
#-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
#-keepclasseswithmembers class * {
#    @dagger.hilt.* <methods>;
#}

# Prevent R8 from over-optimizing constructors (causes StackOverflow with Hilt + proguard-android-optimize.txt)
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.internal.GeneratedEntryPoint <init>(...);
}
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *


-keep class dev.oxide.launcher.bridge.** { *; }
-keep class dev.oxide.launcher.utils.device.VulkanChecker {
    *;
}
-keep class dev.oxide.launcher.utils.device.VulkanCapabilities {
    *;
}
-keep interface dev.oxide.launcher.utils.device.VulkanLogCallback {
    *;
}
-keep class dev.oxide.launcher.game.input.CriticalNativeTest {
    *;
}
// input_bridge_v3.c 中有 Java_dev_oxide_launcher_game_sdl_SdlBridge_* 符号
-keep class dev.oxide.launcher.game.sdl.SdlBridge {
    *;
}

# Libraries
-keep class com.github.steveice10.opennbt.** { *; }

# SoraEditor language-textmate
-keep class org.jcodings.** { *; }