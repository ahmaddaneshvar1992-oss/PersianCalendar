package ir.persiancalendar.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.persiancalendar.app.MainActivity
import ir.persiancalendar.app.R
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
 */
object OngoingDateNotifier {

    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_EVENTS = "events"
    const val TAB_PRAYER = "prayer"
    const val TAB_REMINDERS = "reminders" // موتور یادآور عمومی — نسخه ۱.۱۱
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

    /** ساخت/به‌روزرسانی اعلان همیشگی (اگر از تنظیمات فعال باشد) */
    fun update(context: Context) {
        val enabled = try {
            ir.persiancalendar.app.di.AppContainer.settingsRepo(context)
                .settingsBlocking().ongoingDateNotif
        } catch (e: Exception) {
            true
        }
        if (!enabled) {
            cancel(context)
            return
        }

        val timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تقویم فارسی")
            .setContentText(todayText())
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${todayText()} — ساعت ${PersianText.toPersianDigits(timeText)} · همیشه در دسترس")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tabIntent(context, TAB_EVENTS, 7001))
            .addAction(0, "لیست اعلان‌ها", tabIntent(context, TAB_EVENTS, 7002))
            .addAction(0, "تنظیم یادآورها", tabIntent(context, TAB_REMINDERS, 7003))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ONGOING, notification)
        }
    }

    /** حذف اعلان همیشگی (وقتی کاربر از تنظیمات خاموشش می‌کند) */
    fun cancel(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID_ONGOING)
        }
    }
}
