package ir.persiancalendar.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerMethod
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.model.EventRepetition
import ir.persiancalendar.app.data.model.RepeatType
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.widget.CalendarWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * زمان‌بندی هشدارها با AlarmManager:
 *  - یادآوری رویدادها (دقیق)
 *  - اعلان روزانه اوقات شرعی
 *  - تیک روزانه نیمه‌شب (زمان‌بندی مجدد اوقات شرعی + به‌روزرسانی ویجت)
 */
object AlarmScheduler {

    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_PRAYER_INDEX = "prayer_index"

    private const val REQUEST_TICK = 901
    private const val REQUEST_PRAYER_BASE = 910 // + 0..6

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** آیا آلارم دقیق مجاز است؟ (اندروید ۱۲ به بالا نیاز به مجوز SCHEDULE_EXACT_ALARM دارد) */
    fun canExact(context: Context): Boolean {
        val am = alarmManager(context)
        return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }

    /** آلارم دقیق؛ در نبود مجوز، پنجره ۱۰ دقیقه‌ای (بدون کشتن باتری) */
    fun setExact(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, TimeUnit.MINUTES.toMillis(10), pi)
        }
    }

    // ---------------- رویدادها ----------------

    /** زمان بعدی وقوع رویداد (با احتساب یادآوری) از این لحظه به بعد */
    fun nextEventTrigger(event: EventEntity, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val anchorG = LocalDate.of(event.gy, event.gm, event.gd)
        val anchorJ = CalendarConverter.gregorianToJalali(event.gy, event.gm, event.gd)
        val repeat = RepeatType.fromOrdinal(event.repeatType)

        var d = now.toLocalDate()
        for (i in 0..400) {
            val j = CalendarConverter.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
            if (EventRepetition.occursOn(anchorG, anchorJ, repeat, d)) {
                val time = if (event.hour >= 0) LocalTime.of(event.hour, event.minute) else LocalTime.of(9, 0)
                val trigger = LocalDateTime.of(d, time).minusMinutes(event.reminderMinutesBefore.coerceAtLeast(0).toLong())
                if (trigger.isAfter(now)) return trigger
            }
            d = d.plusDays(1)
        }
        return null
    }

    fun scheduleEventReminder(context: Context, event: EventEntity) {
        cancelEventReminder(context, event.id)
        if (event.reminderMinutesBefore < 0) return
        val trigger = nextEventTrigger(event) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_EVENT_ID, event.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setExact(context, trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    fun cancelEventReminder(context: Context, eventId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) alarmManager(context).cancel(pi)
    }

    suspend fun rescheduleAllEvents(context: Context) {
        val dao = AppContainer.eventDao(context)
        dao.getAll().forEach { scheduleEventReminder(context, it) }
    }

    // ---------------- تیک روزانه ----------------

    fun scheduleDailyTick(context: Context) {
        val next = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(0, 3))
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_TICK,
            Intent(context, DailyTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setExact(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    // ---------------- اوقات شرعی ----------------

    /**
     * زمان‌بندی اعلان اوقات شرعی امروز؛ فقط اوقاتی که هنوز نرسیده‌اند.
     * @param notifPrayerIndexes اندیس‌های اوقات انتخابی کاربر (۰ تا ۶)
     * @param overrideLat/mLng برای موقعیت مکان فعلی کاربر (null = شهر انتخابی)
     */
    fun schedulePrayerAlarms(
        context: Context,
        notifPrayerIndexes: Set<Int>,
        lat: Double? = null,
        lng: Double? = null,
        cityIndex: Int = Cities.DEFAULT_INDEX,
        method: PrayerMethod = PrayerMethod.TEHRAN
    ) {
        // لغو همه اعلان‌های قبلی اوقات شرعی
        for (i in 0..7) cancelPrayerAlarm(context, i)
        if (notifPrayerIndexes.isEmpty()) return

        val city = Cities.list[cityIndex.coerceIn(0, Cities.list.size - 1)]
        val times = PrayerTimesCalculator.compute(
            LocalDate.now(),
            lat ?: city.lat,
            lng ?: city.lng,
            Cities.IRAN_TZ_HOURS,
            method
        )
        val values = doubleArrayOf(
            times.fajr, times.sunrise, times.dhuhr, times.asr,
            times.sunset, times.maghrib, times.isha, times.midnight
        )
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        for (i in 0..7) {
            if (i !in notifPrayerIndexes) continue
            val hours = values[i].toInt()
            val minutes = Math.round((values[i] - hours) * 60).toInt().let { m ->
                if (m >= 60) 59 else m
            }
            val trigger = LocalDateTime.of(today, LocalTime.of(hours, minutes))
                .atZone(zone).toInstant().toEpochMilli()
            if (trigger <= now) continue
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_PRAYER_BASE + i,
                Intent(context, PrayerAlarmReceiver::class.java).putExtra(EXTRA_PRAYER_INDEX, i),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setExact(context, trigger, pi)
        }
    }

    fun cancelPrayerAlarm(context: Context, index: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_PRAYER_BASE + index,
            Intent(context, PrayerAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) alarmManager(context).cancel(pi)
    }
}

/**
 * دریافت هشدار یادآوری رویداد → نمایش اعلان و زمان‌بندی وقوع بعدی.
 *
 * نسخه ۱.۱۱.۱۲ (گزارش کاربر: «نوفیکیش‌های رویداد واسم نمیاد») — باگ جدی:
 * قبلاً نمایش اعلان به شرط «وجود وقوع بعدی» گره خورده بود:
 *     if (trigger != null || event.repeatType != 0) notifyEvent(...)
 * اما هنگام شلیک آلارم، وقوعِ فعلی همین الان گذشته است؛ برای رویداد
 * «بدون تکرار» (repeatType=0) تابع nextEventTrigger همیشه null برمی‌گرداند
 * و در نتیجه اعلان رویدادهای یک‌باره هرگز نشان داده نمی‌شد (رویدادهای
 * تکرارشونده به‌خاطر وقوع بعدی، اعلان می‌گرفتند). اکنون چون آلارم دقیقاً
 * سرِ وقتِ یادآوری شلیک شده و رویداد هم از دیتابیس خوانده شده، اعلان
 * همیشه و بدون شرط نمایش داده می‌شود و سپس زنجیره به وقوع بعدی وصل می‌شود.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1)
        if (eventId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppContainer.eventDao(context)
                val event = dao.getById(eventId)
                if (event != null) {
                    // متن اعلان: عنوان رویداد + یادداشت (در صورت وجود)
                    val text = if (event.note.isNotBlank()) "${event.title} — ${event.note}" else event.title
                    NotificationHelper.notifyEvent(
                        context,
                        NotificationHelper.NOTIF_ID_EVENT_BASE + eventId.toInt(),
                        "یادآوری رویداد",
                        text
                    )
                    // وقوع بعدی برای رویدادهای تکرارشونده (برای یک‌باره لغو می‌شود)
                    AlarmScheduler.scheduleEventReminder(context, event)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * دریافت هشدار اوقات شرعی → نمایش اعلان + پخش صدای اذان دلخواه (نسخه ۱.۲).
 * نسخه ۱.۱۱.۷ (گزارش کاربر): پخش اذان به سرویس Foreground منتقل شد —
 * قبلاً MediaPlayer داخل همین گیرنده بود و اندروید پردازه را می‌کشت و
 * صدای اذان پخش نمی‌شد؛ اکنون AzanPlaybackService پخش را کامل می‌کند.
 * اگر کاربر «بدون صدا» انتخاب کرده باشد فقط اعلان نمایش داده می‌شود.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(AlarmScheduler.EXTRA_PRAYER_INDEX, -1)
        if (index !in 0..7) return
        val labels = arrayOf(
            "اذان صبح", "طلوع آفتاب", "اذان ظهر", "اذان عصر",
            "غروب آفتاب", "اذان مغرب", "اذان عشاء", "نیمه‌شب شرعی"
        )
        NotificationHelper.notifyPrayer(
            context, labels[index],
            "اوقات شرعی امروز — ${labels[index]} (با صدای اذان دلخواه شما)"
        )

        // پخش صدای اذان انتخابی کاربر با سرویس Foreground (نسخه ۱.۱۱.۷)
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = AppContainer.settingsRepo(appContext).settings.first()
                AzanPlaybackService.start(appContext, s.adhanSound)
            } catch (e: Exception) {
                // پخش صدا هرگز نباید اپ را منفجر کند
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * تیک روزانه نیمه‌شب: زمان‌بندی مجدد اوقات شرعی + به‌روزرسانی ویجت.
 */
class DailyTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleDailyTick(context)
                val settingsRepo = AppContainer.settingsRepo(context)
                val s = settingsRepo.settings.first()
                AlarmScheduler.schedulePrayerAlarms(
                    context,
                    s.prayerNotifications,
                    cityIndex = s.cityIndex,
                    method = s.prayerMethod
                )
                // به‌روزرسانی اعلان همیشگی تاریخ (نسخه ۱.۳)
                OngoingDateNotifier.update(context)
                // زمان‌بندی مجدد موتور یادآور عمومی (نسخه ۱.۱۱) — زنجیره آلارم‌ها همیشه زنده می‌ماند
                ReminderEngine.rescheduleAll(context)
                CalendarWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
