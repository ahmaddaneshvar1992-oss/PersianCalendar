# تقویم فارسی 📅

اپلیکیشن کامل اندروید تقویم شمسی (جلالی) با Kotlin و Jetpack Compose — نسخه **۱.۱۱.۱**

## ✨ امکانات

- تقویم جلالی + هجری قمری + میلادی، مناسبت‌های رسمی (آفلاین) و تعطیلات
- **اوقات شرعی ۳۹۶ شهر/شهرستان** (روش ژئوفیزیک تهران) با انتخاب استانی/شهرستانی و مکان‌یاب + کارت اوقات شرعی در پایین صفحه اصلی، پیش از مناسبت‌های ماه
- **موتور یادآور عمومی** (آب / دارو / ورزش / کارهای شخصی): چند نوبت ساعت، روزهای هفته، بازه تاریخ شمسی، حالت یک‌باره/روزانه/هفتگی، ذخیره در Room، آلارم دقیق AlarmManager، اعلان با اکشن‌های «انجام شد» و «بعداً یادآوری کن»، بازیابی پس از ری‌استارت
- پیشرفت آب روزانه با هدف لیوان + «برنامه امروز» با ثبت انجام‌شدن
- قبله‌نما با GPS و قطب‌نمای زنده، اذان دلخواه با صدا، ویجت ماهانه
- رویدادها با یادآوری، حساب کاربری و عضویت ویژه (Google Play Billing + کد فعال‌سازی آفلاین)
- پنل مدیریت تبلیغات (بنر/اینترستیشال AdMob + بنر تصویری اختصاصی)، پشتیبان‌گیری محلی، تم روشن/تاریک

## 📲 نصب مستقیم روی گوشی (APK آماده)

۱. به صفحه [**Releases**](../../releases) بروید.
۲. در آخرین نسخه، فایل `PersianCalendar-vX.Y.Z-install.apk` را دانلود کنید.
۳. روی گوشی فایل را باز کنید؛ اگر پرسید، اجازه «نصب از منابع ناشناس» را بدهید.
۴. تمام! اپلیکیشن نصب می‌شود.

> APK بیلدشده با GitHub Actions **امضای دیباگ** دارد و فقط برای استفاده شخصی و آزمایش است. برای انتشار در گوگل‌پلی/کافه‌بازار باید با کلید خودتان امضا کنید — راهنما: `docs/PUBLISHING-FA.md` و `docs/README-FA.md`.

## 🤖 بیلد خودکار با GitHub Actions

با هر push (یا از تب Actions با دکمه **Run workflow**) یک بیلد کامل انجام می‌شود:

- تب **Actions** → آخرین اجرای «Build Android APK» → بخش **Artifacts** → دانلود `PersianCalendar-debug-apk`
- اگر پوش جدید دارای **تگ** `v…` باشد (مثل `v1.11.1`)، APK به‌صورت خودکار به [Releases](../../releases) همان نسخه هم پیوست می‌شود.

## 🛠 بیلد محلی با Android Studio

۱. Android Studio (Ladybug یا جدیدتر با JDK 17) را نصب کنید.
۲. پروژه را با **File → Open** باز کنید و صبر کنید **Gradle Sync** تمام شود.
۳. دستگاه/شبیه‌ساز را وصل کنید و **Run ▶** را بزنید.

راهنمای کامل ساخت و امضا: [`docs/README-FA.md`](docs/README-FA.md)

## 🌐 پیش‌نمایش وب (بدون نصب)

طراحی و منطق اپ به‌صورت تک‌فایل HTML هم قابل استفاده است:
[اجرای پیش‌نمایش وب](demo/PersianCalendar-WebDemo.html) — (پس از فعال‌شدن GitHub Pages: از همین آدرس در تب Pages سایت پروژه)

## 📚 مستندات

| فایل | موضوع |
|---|---|
| [`docs/README-FA.md`](docs/README-FA.md) | ساخت، امضا و آماده‌سازی انتشار |
| [`docs/PUBLISHING-FA.md`](docs/PUBLISHING-FA.md) | انتشار در گوگل‌پلی و کافه‌بازار |
| [`docs/ADMOB-SETUP-FA.md`](docs/ADMOB-SETUP-FA.md) | راه‌اندازی AdMob |
| [`docs/MEMBERSHIP-FA.md`](docs/MEMBERSHIP-FA.md) | عضویت ویژه و درآمدزایی |
| [`docs/UPDATE-HOLIDAYS-FA.md`](docs/UPDATE-HOLIDAYS-FA.md) | به‌روزرسانی مناسبت‌های هر سال |
| [`docs/WHATS-NEW-v1.11.1-FA.md`](docs/WHATS-NEW-v1.11.1-FA.md) | تغییرات آخرین نسخه |
| [`docs/PRIVACY-POLICY-FA.md`](docs/PRIVACY-POLICY-FA.md) | سیاست حریم خصوصی |

## 🧱 فناوری

Kotlin · Jetpack Compose · Room · DataStore · AlarmManager exact · WorkManager-free chain scheduling · Billing Library 7 · AdMob · minSdk 24 · Target SDK 35

---

ایمیل پشتیبانی: Ahmad.daneshvar1992@gmail.com
