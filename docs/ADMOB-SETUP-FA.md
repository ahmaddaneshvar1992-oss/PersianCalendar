# راهنمای راه‌اندازی AdMob

اپلیکیشن شما با مدل **رایگان + تبلیغات** منتشر می‌شود. دو نوع تبلیغ فعال است:
بنر (پایین صفحه تقویم و اوقات شرعی) و اینترستیشال (هر ۳ بار باز کردن جزئیات روز).

## مرحله ۱: ساخت حساب AdMob

1. به [apps.admob.com](https://apps.admob.com) بروید و با اکانت گوگل ثبت‌نام کنید.
2. **Apps → Add app → Android** و اپلیکیشن را ثبت کنید (اگر هنوز منتشر نشده، گزینه «not listed yet» را انتخاب کنید).
3. **App ID** را کپی کنید — شکلی شبیه `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY` دارد.

## مرحله ۲: ساخت واحد تبلیغات

در بخش **Ad units** دو واحد بسازید:
- **Banner** → نام: `banner_main`
- **Interstitial** → نام: `interstitial_day`

هر دو شناسه شبیه `ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY` دارند.

## مرحله ۳: جایگزینی شناسه‌ها در کد

### ۱) شناسه اپلیکیشن در `app/src/main/AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
```

### ۲) شناسه واحدها در `app/src/main/java/ir/persiancalendar/app/ads/AdManager.kt`:

```kotlin
const val BANNER_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"      // بنر
const val INTERSTITIAL_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY" // اینترستیشال
```

> ⚠️ هشدار مهم: اپلیکیشن را **هرگز با شناسه واقعی بدون تغییر دیگری تست نکنید** — کلیک روی تبلیغات خودتان منجر به مسدود شدن حساب AdMob می‌شود. در دوران تست، شناسه‌های تست گوگل را نگه دارید. شناسه‌های تست فعلی:
> - اپ: `ca-app-pub-3940256099942544~3347511713`
> - بنر: `ca-app-pub-3940256099942544/6300978111`
> - اینترستیشال: `ca-app-pub-3940256099942544/1033173712`

## مرحله ۴: اتصال به گوگل‌پلی

پس از انتشار اپ در گوگل‌پلی، در AdMob: **App settings → App store ID** را وارد و «Link» کنید تا گزارش درآمد دقیق شود.

## نکات درآمدزایی

- **تعادل تجربه کاربر:** اینترستیشال هر ۳ باز شدن جزئیات روز نمایش داده می‌شود (متغیر `count % 3` در `MainScreen.kt`). اگر نرخ حذف نصب بالا رفت، به ۴ یا ۵ تغییر دهید.
- **اولویت کافه‌بازار:** سرویس AdMob در ایران ممکن است برای همه کاربران نمایش داده نشود (تحریم/فیلترینگ). برای بازار ایران در آینده می‌توانید شبکه‌های داخلی (مثل تپسل) را علاوه بر AdMob اضافه کنید — الگوی کد در `ads/AdManager.kt` آماده توسعه است.
- **قوانین:** مطابق سیاست گوگل، تبلیغات نباید روی کلیک تصادفی باشند؛ بنر در پایین صفحه و با فاصله از دکمه‌ها قرار گرفته است.
