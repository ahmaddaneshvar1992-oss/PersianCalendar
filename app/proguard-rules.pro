# قوانین ProGuard برای تقویم فارسی

# AdMob / Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.android.ads.** { *; }
-dontwarn com.google.android.gms.**

# Room (پیش‌فرض کافی است، فقط برای اطمینان)
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin metadata برای Compose
-dontwarn org.jetbrains.annotations.**

# حذف لاگ‌ها در نسخه نهایی
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
