package ir.persiancalendar.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ir.persiancalendar.app.MainActivity
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * اعلان همیشگی تاریخ — نسخه ۱.۳
 *
 * یک اعلان دائمی (ongoing) در نوار وضعیت که همیشه تاریخ امروز را نشان می‌دهد؛
 * مانند اپ‌های مشهور تقویم. دو دکمه سریع دارد:
 *  - «لیست اعلان‌ها» → باز شدن صفحه رویدادها
 *  - «تنظیم یادآورها» → باز شدن صفحه یادآورها (آب، دارو، ورزش، شخصی — نسخه ۱.۱۱)
 *
 * به‌روزرسانی: هنگام باز شدن اپ (PersianCalendarApp)، تیک نیمه‌شب (DailyTickReceiver)
 * و پس از ری‌استارت گوشی (BootReceiver). قابل خاموش کردن از تنظیمات.
 *
 * نسخه ۱.۱۱.۱۳ (درخواست کاربر «مطابق عکس»): آیکن نوار وضعیت این اعلان
 * دیگر لوگوی برنامه نیست؛ عدد روزِ ماه شمسی به‌صورت پویا رسم می‌شود
 * (مثلاً امروز ۷ آبان است → یک «۷» در نوار وضعیت دیده می‌شود — مثل
 * آیکن تقویم آیفون). متن اعلان همچنان تاریخ کامل است.
 *
 * نسخه ۱.۱۱.۱۴ (درخواست کاربر: «مثل برنامه ADM یا VPN همیشه فعال باشه»):
 * این اعلان دیگر یک notify() ساده نیست؛ توسط سرویس Foreground همیشگی
 * (CalendarForegroundService) نگه‌داری می‌شود تا هیچ‌وقت از نوار وضعیت پاک
 * نشود — حتی اگر کاربر سوایپش کند یا سیستم پردازه را بکشد. تاریخ و ساعت
 * هر دقیقه تازه می‌شود.
 *
 * نسخه ۱.۱۱.۳۱ (درخواست کاربر با اسکرین‌شات: «میخوام نوار اعلان شبیه باد صبا باشه
 * تاریخ میلادی داشته باشه، فونت درشت، اون تاریخ که رنگ آبی داره»):
 *  - عنوانِ تاریخ شمسی با AbsoluteSizeSpan واقعاً درشت می‌شود (۳۰dp، پهن)
 *  - خط دوم آبی‌رنگ: تاریخ میلادی با ماه فارسی («۳۱ اکتبر ۲۰۲۶») + تاریخ قمری
 *    («۲۰ جمادی‌الاول ۱۴۴۸» — از ۱.۱۱.۳۲ هم‌تراز تقویم رسمی) — همان‌چینش باد صبا؛ رنگ آبی روی زمینهٔ تیره و روشن
 *    هر دو خواناست (Material Blue 300)
 *  - خط ساعت در متن باز‌شدهٔ اعلان (BigText) حفظ شده است
 */
object OngoingDateNotifier {

    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_EVENTS = "events"
    const val TAB_PRAYER = "prayer"
    const val TAB_REMINDERS = "reminders" // موتور یادآور عمومی — نسخه ۱.۱۱
    const val TAB_QIBLA = "qibla" // نسخهٔ ۱.۱۱.۳۳ — ابزارهای صفحهٔ اذان
    const val NOTIF_ID_ONGOING = 19998

    private val weekDayNames = mapOf(
        DayOfWeek.SATURDAY to "شنبه",
        DayOfWeek.SUNDAY to "یکشنبه",
        DayOfWeek.MONDAY to "دوشنبه",
        DayOfWeek.TUESDAY to "سه‌شنبه",
        DayOfWeek.WEDNESDAY to "چهارشنبه",
        DayOfWeek.THURSDAY to "پنجشنبه",
        DayOfWeek.FRIDAY to "جمعه"
    )

    /** متن تاریخ امروز به شمسی: «سه‌شنبه ۵ آبان ۱۴۰۵» */
    fun todayText(): String {
        val today = LocalDate.now()
        val j = CalendarConverter.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
        val wd = weekDayNames[today.dayOfWeek] ?: ""
        return "$wd ${PersianText.toPersianDigits(j.day.toString())} " +
            "${PersianText.jalaliMonths[j.month - 1]} ${PersianText.toPersianDigits(j.year.toString())}"
    }

    /**
     * خط دوم اعلان — نسخهٔ ۱.۱۱.۳۱ (سبک باد صبا):
     * میلادی با ماه فارسی + قمری: «۳۱ اکتبر ۲۰۲۶ ، ۲۰ جمادی‌الاول ۱۴۴۸»
     * (قمری جدولی کویتی + هم‌ترازی رسمی HIJRI_ADJUST_DAYS — نسخهٔ ۱.۱۱.۳۲)
     */
    fun secondaryDateText(): String {
        val today = LocalDate.now()
        val g = "${PersianText.toPersianDigits(today.dayOfMonth.toString())} " +
            "${PersianText.gregorianMonthsFa[today.monthValue - 1]} " +
            PersianText.toPersianDigits(today.year.toString())
        val h = CalendarConverter.gregorianToHijri(today.year, today.monthValue, today.dayOfMonth)
        val hj = "${PersianText.toPersianDigits(h.day.toString())} " +
            "${PersianText.hijriMonths[h.month - 1]} ${PersianText.toPersianDigits(h.year.toString())}"
        return "$g ، $hj"
    }

    /**
     * آیکن «عدد روز ماه» برای نوار وضعیت — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۲).
     * نسخهٔ ۱.۱۱.۱۳: عدد فارسی روز ماه به‌صورت پویا رسم می‌شد.
     * نسخهٔ ۱.۱۱.۳۵: پیش‌فرض «دایرهٔ پر سفید با عدد cutout» (فونت ضخیم وزیرمتن،
     * حداکثر درشتی با اندازه‌گیری واقعی فونت) — سبک سادهٔ قبلی هم در تنظیمات هست
     * (StatusBarDayIcon). در هر خطا فالبک به آیکون vector برنامه — بدون کرش.
     */
    private fun dayIcon(context: Context): IconCompat {
        val style = runCatching {
            ir.persiancalendar.app.di.AppContainer.settingsRepo(context)
                .settingsBlocking().statusBarIconStyle
        }.getOrDefault(ir.persiancalendar.app.notifications.StatusBarDayIcon.STYLE_CIRCLE)
        val bmp = runCatching {
            ir.persiancalendar.app.notifications.StatusBarDayIcon.bitmapFor(context, style)
        }.getOrNull()
        return if (bmp != null) {
            IconCompat.createWithBitmap(bmp)
        } else {
            // فالبک امن: آیکون برنامه (هیچ راهی برای کرش کردن اعلان همیشگی نیست)
            IconCompat.createWithResource(context, ir.persiancalendar.app.R.mipmap.ic_launcher)
        }
    }

    /** اینتنت باز کردن اپ روی تب مشخص (برای دکمه‌های اعلان) */
    private fun tabIntent(context: Context, tab: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, tab)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** ساخت/به‌روزرسانی اعلان همیشگی (اگر از تنظیمات فعال باشد) — نسخه ۱.۱۱.۱۴: با سرویس Foreground */
    fun update(context: Context) {
        val enabled = try {
            ir.persiancalendar.app.di.AppContainer.settingsRepo(context)
                .settingsBlocking().ongoingDateNotif
        } catch (e: Exception) {
            true
        }
        if (enabled) {
            CalendarForegroundService.start(context)
        } else {
            cancel(context)
        }
    }

    /** ساخت اعلان همیشگی تاریخ — هم برای سرویس Foreground و هم برای جایگزین ساده */
    fun buildOngoingNotification(context: Context): android.app.Notification {
        val timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        // نسخهٔ ۱.۱۱.۳۱ (درخواست کاربر با اسکرین‌شات باد صبا): عنوانِ درشت + خط آبی میلادی/قمری.
        // SpannableString در اعلان‌ها رندر می‌شود (AbsoluteSizeSpan/StyleSpan/ForegroundColorSpan)؛
        // اگر رابط کاربری شرکتی آن‌ها را ساده کند، فقط به متن معمولی برمی‌گردد — افت‌پذیری امن.
        val title = SpannableString(todayText())
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.setSpan(AbsoluteSizeSpan(30, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val sub = SpannableString(secondaryDateText())
        sub.setSpan(ForegroundColorSpan(0xFF64B5F6.toInt()), 0, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sub.setSpan(AbsoluteSizeSpan(16, true), 0, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val big = SpannableStringBuilder(sub)
        big.append("\n")
        big.append("بادخزان — ساعت ${PersianText.toPersianDigits(timeText)} · همیشه در دسترس")

        return NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ONGOING)
            // نسخهٔ ۱.۱۱.۳۵: آیکون دایرهٔ سفید درشت با عدد cutout (کار ۲) — فالبک vector
            .setSmallIcon(dayIcon(context))
            .setContentTitle(title)
            .setContentText(sub)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(calendarIntent(context))
            .addAction(0, "لیست اعلان‌ها", tabIntent(context, TAB_EVENTS, 7002))
            .addAction(0, "تنظیم یادآورها", tabIntent(context, TAB_REMINDERS, 7003))
            .build()
    }

    /** لمس اعلان → باز شدن برنامه روی تقویم اصلی (بدون extra — نسخه ۱.۱۱.۱۵) */
    private fun calendarIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** نمایش/به‌روزرسانی مستقیم اعلان (حلقهٔ دقیقه‌ای سرویس + جایگزین بدون سرویس) */
    fun postOngoing(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID_ONGOING, buildOngoingNotification(context))
        }
    }

    /** فقط حذف اعلان (داخلی) */
    fun cancelNotification(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID_ONGOING)
        }
    }

    /** خاموش کردن کامل: توقف سرویس همیشگی + حذف اعلان (وقتی کاربر از تنظیمات خاموشش می‌کند) */
    fun cancel(context: Context) {
        CalendarForegroundService.stop(context)
    }
}
