package ir.persiancalendar.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.persiancalendar.app.MainActivity
import ir.persiancalendar.app.R
import ir.persiancalendar.app.data.db.ReminderEntity

/**
 * ساخت کانال‌های اعلان و نمایش اعلان‌ها.
 */
object NotificationHelper {

    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_PRAYERS = "prayers"
    const val CHANNEL_ONGOING = "ongoing_date"
    const val CHANNEL_REMINDERS = "reminders" // موتور یادآور عمومی — نسخه ۱.۱۱
    const val CHANNEL_AZAN_PLAYBACK = "azan_playback" // سرویس پخش اذان — نسخه ۱.۱۱.۷
    const val NOTIF_ID_EVENT_BASE = 20000
    const val NOTIF_ID_PRAYER = 19999
    const val NOTIF_ID_REMINDER_BASE = 30000
    const val NOTIF_ID_AZAN_PLAYBACK = 29999

    // اکشن‌های اعلان یادآور — «انجام شد» و «بعداً یادآوری کن»
    const val ACTION_REMINDER_DONE = "ir.persiancalendar.app.action.REMINDER_DONE"
    const val ACTION_REMINDER_SNOOZE = "ir.persiancalendar.app.action.REMINDER_SNOOZE"

    fun createChannels(context: Context) {
        // NotificationChannel از API ۲۶ به بعد موجود است؛ در اندروید ۷ اعلان بدون کانال کار می‌کند
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val events = NotificationChannel(
            CHANNEL_EVENTS,
            "یادآوری رویدادها",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "یادآوری رویدادها و قرارهای ثبت‌شده در تقویم"
        }

        val prayers = NotificationChannel(
            CHANNEL_PRAYERS,
            "اوقات شرعی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان اذان صبح، ظهر، مغرب و دیگر اوقات شرعی — با صدای اذان دلخواه"
            // صدای اذان از MediaPlayer پخش می‌شود؛ صدای کانال خاموش تا دو صدا روی هم نیفتد
            setSound(null, null)
            enableVibration(true)
        }

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "اعلان همیشگی تاریخ",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نمایش دائمی تاریخ امروز در نوار وضعیت — بدون صدا"
            setShowBadge(false)
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "یادآورها (آب، دارو، ورزش، شخصی)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "یادآوری نوبت‌های نوشیدن آب، مصرف دارو، ورزش و کارهای شخصی"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 250, 350)
        }

        // کانال سرویس پخش اذان — نسخه ۱.۱۱.۷ (بی‌صدا و کم‌اهمیت؛ فقط نشانگر سرویس)
        val azanPlayback = NotificationChannel(
            CHANNEL_AZAN_PLAYBACK,
            "پخش اذان",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نشانگر پخش صدای اذان هنگام رسیدن وقت شرعی"
            setSound(null, null)
            setShowBadge(false)
        }

        nm.createNotificationChannel(events)
        nm.createNotificationChannel(prayers)
        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(reminders)
        nm.createNotificationChannel(azanPlayback)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** دکمهٔ «توقف صدا» اعلان اوقات شرعی — نسخه ۱.۱۱.۱۴ (گزارش: «اذان توقف نداره») */
    private fun azanStopIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context, 9003,
            Intent(context, AzanPlaybackService::class.java)
                .setAction(AzanPlaybackService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** بستن اعلان اوقات شرعی (بعد از توقف صدا) — نسخه ۱.۱۱.۱۴ */
    fun cancelPrayer(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_PRAYER)
        }
    }

    fun notifyEvent(context: Context, notifId: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    /** شناسه اعلان یک یادآور — نتیجه همیشه در بازه ۳۰۰۰۰ تا ۳۴۹۹۹ می‌ماند */
    fun reminderNotifId(reminderId: Long): Int =
        NOTIF_ID_REMINDER_BASE + (reminderId % 5000).toInt()

    private fun openRemindersIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(OngoingDateNotifier.EXTRA_OPEN_TAB, OngoingDateNotifier.TAB_REMINDERS)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun actionIntent(context: Context, action: String, reminderId: Long, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action.hashCode() + reminderId.toInt()),
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderEngine.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderEngine.EXTRA_SLOT_MINUTE, slot)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * اعلان یادآور عمومی (آب/دارو/ورزش/شخصی) — نسخه ۱.۱۱
     * دو اکشن: «انجام شد» (ثبت نوبت) و «بعداً یادآوری کن» (۱۰ دقیقه بعد)
     */
    fun notifyReminder(context: Context, reminder: ir.persiancalendar.app.data.db.ReminderEntity, slotMinute: Int) {
        val typeLabel = ReminderEntity.typeLabels.getOrElse(reminder.type) { "یادآور" }
        val hour = (slotMinute / 60).coerceIn(0, 23)
        val minute = slotMinute % 60
        val timeText = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val title = "یادآور $typeLabel — ${reminder.title}"
        var text = "نوبت ساعت ${timeText} رسیده است."
        if (reminder.note.isNotBlank()) text += " (${reminder.note})"

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 350, 250, 350))
            .setAutoCancel(true)
            .setContentIntent(openRemindersIntent(context))
            .addAction(0, "انجام شد", actionIntent(context, ACTION_REMINDER_DONE, reminder.id, slotMinute))
            .addAction(0, "بعداً یادآوری کن", actionIntent(context, ACTION_REMINDER_SNOOZE, reminder.id, slotMinute))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(reminderNotifId(reminder.id), notification)
        }
    }

    /** بستن اعلان یک یادآور (پس از انجام/تعویق) */
    fun cancelReminder(context: Context, reminderId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(reminderNotifId(reminderId))
        }
    }

    /**
     * اعلان اذان/اوقات شرعی — نسخه ۱.۳
     * دسته ALARM + اولویت بالا تا روی صفحه بیاید؛ صدای اذان جداگانه با MediaPlayer پخش می‌شود.
     * نسخه ۱.۱۱.۱۴: دکمهٔ «توقف صدا» اضافه شد تا کاربر برای قطع صدای اذان
     * مجبور به بستن اجباری برنامه نباشد.
     */
    fun notifyPrayer(context: Context, label: String, timeText: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PRAYERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(timeText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$label — $timeText"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 250, 400))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "توقف صدا", azanStopIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_PRAYER, notification)
        }
    }
}
