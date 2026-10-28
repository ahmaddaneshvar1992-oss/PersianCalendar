package ir.persiancalendar.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.data.db.ReminderEntity
import ir.persiancalendar.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * موتور یادآور عمومی — نسخه ۱.۱۱
 *
 * یک موتور واحد برای آب، دارو، ورزش و کارهای شخصی:
 *  - محاسبه «نوبت بعدی» از ترکیب (حالت تکرار × روزهای هفته × نوبت‌های ساعت × بازه تاریخ)
 *  - یک آلارم دقیق در آینده برای هر یادآور؛ پس از هر زنگ، نوبت بعدی زنجیر می‌شود
 *  - لغو کامل آلارم‌های یادآور حذف‌شده/غیرفعال‌شده
 *  - بازیابی همه آلارم‌ها پس از ری‌استارت گوشی (BootReceiver) و هر نیمه‌شب (DailyTickReceiver)
 *  - «به تعویق انداختن» از خود اعلان (اکشن «بعداً یادآوری کن» = ۱۰ دقیقه)
 *
 * نکته‌های زمانی (چک‌لیست انتشار):
 *  - همه محاسبات با LocalDateTime و منطقه زمانی دستگاه انجام می‌شود؛ پس از تغییر
 *    منطقه/ساعت، ACTION_TIME_CHANGED آلارم‌ها را دوباره می‌چیند (Receiver پایین فایل).
 *  - ایران از ۱۴۰۱ ساعت تابستانی ندارد، ولی موتور مستقل از آن با ZoneId دستگاه درست کار می‌کند.
 */
object ReminderEngine {

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SNOOZED = "snoozed"
    const val EXTRA_SLOT_MINUTE = "slot_minute"
    const val SNOOZE_MINUTES = 10

    /** فضای کد درخواست جدا از رویدادها/اوقات شرعی/تیک تا هرگز تداخل نکنند */
    private const val REQUEST_NEXT_BASE = 100_000
    private const val REQUEST_SNOOZE_BASE = 200_000

    // ---------------- محاسبه نوبت بعدی ----------------

    /** این تاریخ روی این یادآور «وقوع» دارد؟ (بر اساس حالت تکرار و روزهای هفته) — عمومی برای UI */
    fun occursOnDate(reminder: ReminderEntity, date: LocalDate): Boolean {
        val start = startDayOf(reminder) ?: return false
        if (date.isBefore(start)) return false
        val end = endDayOf(reminder)
        if (end != null && date.isAfter(end)) return false
        return when (reminder.repeatMode) {
            ReminderEntity.REPEAT_ONCE -> date == start
            ReminderEntity.REPEAT_DAILY -> true
            ReminderEntity.REPEAT_WEEKLY -> reminder.daysSet.contains(jalaliWeekIndex(date))
            else -> false
        }
    }

    private fun startDayOf(reminder: ReminderEntity): LocalDate? {
        val k = reminder.startDate
        if (k <= 0) return LocalDate.now()
        val jd = k % 100
        val jm = (k / 100) % 100
        val jy = k / 10000
        if (jm !in 1..12 || jd !in 1..31) return LocalDate.now()
        val g = CalendarConverter.jalaliToGregorian(jy, jm, jd)
        return LocalDate.of(g.year, g.month, g.day)
    }

    private fun endDayOf(reminder: ReminderEntity): LocalDate? {
        val k = reminder.endDate
        if (k <= 0) return null
        val jd = k % 100
        val jm = (k / 100) % 100
        val jy = k / 10000
        if (jm !in 1..12 || jd !in 1..31) return null
        val g = CalendarConverter.jalaliToGregorian(jy, jm, jd)
        return LocalDate.of(g.year, g.month, g.day)
    }

    /**
     * اندیس روز هفته فارسی: شنبه=۰، یکشنبه=۱، … جمعه=۶.
     * java.time: دوشنبه=۱ … یکشنبه=۷؛ پس شنبه(۷)→۰ و بقیه سر جایشان می‌مانند.
     */
    fun jalaliWeekIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** نزدیک‌ترین وقوع آینده این یادآور از «now» به بعد — null یعنی وقوع دیگری ندارد */
    fun nextTrigger(
        reminder: ReminderEntity,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        val slots = reminder.timesList
        if (slots.isEmpty()) return null
        var d = now.toLocalDate()
        // بازه جست‌وجو: تا ۴۰۰ روز جلو — پوشش کامل برای یک‌باره/هفتگی/پایان‌دار
        for (i in 0..400) {
            if (occursOnDate(reminder, d)) {
                for (m in slots) {
                    val trigger = LocalDateTime.of(d, LocalTime.of(m / 60 % 24, m % 60))
                    if (trigger.isAfter(now)) return trigger
                }
            }
            d = d.plusDays(1)
        }
        return null
    }

    // ---------------- زمان‌بندی/لغو آلارم ----------------

    private fun nextIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (REQUEST_NEXT_BASE + reminderId).toInt(),
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun snoozeIntent(context: Context, reminderId: Long, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (REQUEST_SNOOZE_BASE + reminderId).toInt(),
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_SNOOZED, true)
                .putExtra(EXTRA_SLOT_MINUTE, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** لغو آلارم «نوبت بعدی» و «به‌تعویق» یک یادآور — برای حذف/غیرفعال‌سازی */
    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.cancel(nextIntent(context, reminderId))
        am.cancel(snoozeIntent(context, reminderId, 0))
        NotificationHelper.cancelReminder(context, reminderId)
    }

    /**
     * زنجیره‌سازی: آلارم نوبت بعدی این یادآور را می‌بندد.
     * اگر یادآور نبود، خاموش بود یا وقوع دیگری نداشت، همه آلارم‌هایش لغو می‌شود.
     */
    fun scheduleNext(context: Context, reminderId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dao = AppContainer.reminderDao(context)
                val r = dao.getById(reminderId)
                if (r == null) {
                    // یادآور حذف شده → آلارم‌ها و ثبت‌هایش هم پاک شود
                    cancel(context, reminderId)
                    AppContainer.doseLogDao(context).deleteOfReminder(reminderId)
                } else {
                    scheduleNextSync(context, r)
                }
            }
        }
    }

    /** نسخه همگام برای صدا زدن از کوروتین موجود */
    suspend fun scheduleNextSync(context: Context, r: ReminderEntity) {
        if (!r.enabled) {
            cancel(context, r.id)
            return
        }
        val trigger = nextTrigger(r)
        if (trigger == null) {
            cancel(context, r.id)
        } else {
            AlarmScheduler.setExact(
                context,
                trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                nextIntent(context, r.id)
            )
        }
    }

    /** «بعداً یادآوری کن» — آلارم مستقل ۱۰ دقیقه بعد؛ زنجیره اصلی دست‌نخورده می‌ماند */
    fun snooze(context: Context, reminderId: Long, slotMinute: Int) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        AlarmScheduler.setExact(context, at, snoozeIntent(context, reminderId, slotMinute))
    }

    /** بازیابی همه آلارم‌ها — بعد از ری‌استارت گوشی، باز شدن اپ و تیک نیمه‌شب */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppContainer.reminderDao(context)
        // پاک‌سازی ثبت‌های قدیمی‌تر از ۴۰۰ روز (بهداشت دیتابیس؛ هرگز نباید زمان‌بندی را متوقف کند)
        runCatching {
            val past = LocalDate.now().minusDays(400)
            val j = CalendarConverter.gregorianToJalali(past.year, past.monthValue, past.dayOfMonth)
            AppContainer.doseLogDao(context).pruneOlderThan(ReminderEntity.dayKey(j.year, j.month, j.day))
        }
        dao.getEnabled().forEach { scheduleNextSync(context, it) }
        dao.getAll().filter { !it.enabled }.forEach { cancel(context, it.id) }
    }
}

/**
 * دریافت آلارم یادآور → نمایش اعلان با اکشن‌های «انجام شد / بعداً یادآوری کن»
 * و زنجیره‌کردن نوبت بعدی.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderEngine.EXTRA_REMINDER_ID, -1)
        if (id <= 0) return
        val snoozed = intent.getBooleanExtra(ReminderEngine.EXTRA_SNOOZED, false)
        val slot = intent.getIntExtra(ReminderEngine.EXTRA_SLOT_MINUTE, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppContainer.reminderDao(context)
                val r = dao.getById(id) ?: return@launch
                if (!r.enabled) {
                    ReminderEngine.cancel(context, id)
                    return@launch
                }
                val now = java.time.LocalDateTime.now()
                val triggerMinute = if (slot >= 0) slot
                else now.hour * 60 + now.minute
                NotificationHelper.notifyReminder(context, r, triggerMinute)
                if (!snoozed) {
                    // زنجیره: نوبت بعدی همین یادآور بسته شود
                    val next = ReminderEngine.nextTrigger(r, now)
                    if (next == null) {
                        ReminderEngine.cancel(context, id)
                    } else {
                        ReminderEngine.scheduleNextSync(context, r)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * دکمه‌های خود اعلان:
 *  - «انجام شد» → ثبت نوبت در dose_logs + بستن اعلان
 *  - «بعداً یادآوری کن» → آلارم ۱۰ دقیقه بعد + بستن اعلان
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderEngine.EXTRA_REMINDER_ID, -1)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_REMINDER_DONE -> {
                        val slot = intent.getIntExtra(ReminderEngine.EXTRA_SLOT_MINUTE, -1)
                        val dao = AppContainer.doseLogDao(context)
                        val j = CalendarConverter.gregorianToJalali(
                            java.time.LocalDate.now().year,
                            java.time.LocalDate.now().monthValue,
                            java.time.LocalDate.now().dayOfMonth
                        )
                        val minute = if (slot >= 0) slot
                        else java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                        runCatching {
                            dao.insert(
                                ir.persiancalendar.app.data.db.DoseLogEntity(
                                    reminderId = id,
                                    dayKey = ReminderEntity.dayKey(j.year, j.month, j.day),
                                    timeMinute = minute
                                )
                            )
                        }
                        NotificationHelper.cancelReminder(context, id)
                        // نوبت بعدی زنجیره — در صورت نیاز
                        ReminderEngine.scheduleNext(context, id)
                    }

                    NotificationHelper.ACTION_REMINDER_SNOOZE -> {
                        val slot = intent.getIntExtra(ReminderEngine.EXTRA_SLOT_MINUTE, -1)
                        ReminderEngine.snooze(context, id, slot)
                        NotificationHelper.cancelReminder(context, id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
