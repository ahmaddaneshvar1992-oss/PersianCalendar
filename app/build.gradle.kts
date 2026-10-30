plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ir.persiancalendar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.persiancalendar.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 41
        versionName = "1.11.29"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ====================================================================
    // امضای ثابت نسخهٔ ۱.۱۱.۱۸ (رفع باگ «به‌روزرسانی نصب نمی‌شود» — درخواست کاربر)
    //
    // مشکل: CI قبلاً assembleDebug می‌ساخت که با کلیدِ debug موقتیِ رانِنر
    // امضا می‌شد؛ چون هر اجرای CI کلید جدیدی تولید می‌کرد، هر APK جدید
    // امضای متفاوتی داشت و اندروید با خطای «package conflicts with an
    // existing package» اجازهٔ نصب روی نسخهٔ قبلی را نمی‌داد — کاربر
    // مجبور بود هر بار برنامه را حذف و دوباره نصب کند.
    //
    // راه‌حل: keystore ثابت داخل مخزن (signing/badkhan-release.jks) و
    // امضای «هر دو» نوع debug و release با همان کلید — از این به بعد همهٔ
    // نسخه‌ها امضای یکسان دارند و به‌روزرسانی مستقیم روی نسخهٔ قبلی نصب
    // می‌شود (بدون حذف برنامه).
    // ====================================================================
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/badkhan-release.jks")
            storePassword = "BadkhanRelease2026"
            keyAlias = "badkhan"
            keyPassword = "BadkhanRelease2026"
        }
    }

    buildTypes {
        release {
            // برای انتشار: minify روشن باشد تا حجم کم شود
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // امضای debug هم با کلید ثابت — تا APKهای CI همیشه قابل
            // به‌روزرسانی روی هم باشند (حتی اگر روزی assembleDebug ساخته شود)
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // برای استفاده از java.time (LocalDate و...) در اندروید ۷ و ۸
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // نسخه ۱.۱۱.۱۸ — ارتقای fragment به ۱.۳+ برای ActivityResult
    // (ترنزیتیوس قدیمی fragment:1.1.0 خطای lint «InvalidFragmentVersionForActivityResult»
    //  در build release می‌ساخت و apk منتشر نمی‌شد)
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Room (دیتابیس رویدادها)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (تنظیمات)
    implementation(libs.androidx.datastore.preferences)

    // نسخه ۱.۱۱.۵: وابستگی AdMob (play-services-ads) حذف شد — تبلیغات کامل از برنامه برداشته شدند

    // خرید درون‌برنامه‌ای (عضویت ویژه — Google Play)
    implementation(libs.billing.ktx)
}
