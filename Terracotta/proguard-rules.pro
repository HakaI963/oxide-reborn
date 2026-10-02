-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class dev.oxide.terracotta.TerracottaAndroidAPI {
    native <methods>;
    private static int onVpnServiceStateChanged(...);
}

-keep class dev.oxide.terracotta.TerracottaAndroidAPI$Metadata {
    *;
}
-keep interface dev.oxide.terracotta.TerracottaAndroidAPI$VpnServiceCallback {
    *;
}
-keep interface dev.oxide.terracotta.TerracottaAndroidAPI$VpnServiceRequest {
    *;
}
