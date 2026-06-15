####################################
# Firebase (CRITICAL)
####################################
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.internal.firebase-auth-api.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

####################################
# App package (LOGIN CRASH FIX)
####################################
-keep class com.wall.mob.** { *; }

####################################
# Gson / JSON
####################################
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements java.io.Serializable

####################################
# Volley
####################################
-keep class com.android.volley.** { *; }
-dontwarn org.apache.http.**

####################################
# Unity Ads
####################################
-keep class com.unity3d.ads.** { *; }

####################################
# Glide
####################################
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * implements com.bumptech.glide.module.GlideModule
####################################
# SLF4J (FIX R8 MISSING CLASS ERROR)
####################################
-dontwarn org.slf4j.**