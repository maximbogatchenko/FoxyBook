# ─── FoxyBook ProGuard Rules ───

# ─── Application ───
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Kotlin Serialization ───
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.foxybook.app.core.models.**$$serializer { *; }
-keepclassmembers class com.foxybook.app.core.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.foxybook.app.core.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── DataStore ───
-keepclassmembers class * extends com.foxybook.app.core.datastore.** {
    <init>(...);
}

# ─── Jsoup ───
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ─── OkHttp ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── Coil ───
-dontwarn coil3.**
-keep class coil3.** { *; }

# ─── Compose ───
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ─── AndroidX ───
-keep class androidx.datastore.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.lifecycle.** { *; }

# ─── WebView JavaScript Interface ───
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ─── General Android ───
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver

# ─── Enum ───
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Parcelable ───
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ─── Remove logging in release ───
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
