# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Gemini / JSON
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# Billing
-keep class com.android.billingclient.** { *; }

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
