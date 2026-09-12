# TDLib proguard rules
-keep class org.drinkless.tdlib.** { *; }
-keep class com.telestudy.tv.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Media3 proguard rules
-keep class androidx.media3.** { *; }

# Coil proguard rules
-keep class coil.** { *; }
