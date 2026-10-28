package ir.persiancalendar.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * بازیابی همه هشدارها در رویدادهای سیستمی:
 *  - BOOT_COMPLETED: پس از خاموش‌وشدن گوشی (هشدارهای AlarmManager پاک می‌شوند)
 *  - TIME_SET / TIMEZONE_CHANGED: تغییر ساعت یا منطقه زمانی (چک‌لیست نسخه ۱.۱۱)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        val isTimeChange = action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (!isBoot && !isTimeChange) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAllEvents(context)
                AlarmScheduler.scheduleDailyTick(context)
                val s = AppContainer.settingsRepo(context).settings.first()
                AlarmScheduler.schedulePrayerAlarms(
                    context,
                    s.prayerNotifications,
                    cityIndex = s.cityIndex,
                    method = s.prayerMethod
                )
                // بازیابی موتور یادآور عمومی — نسخه ۱.۱۱
                ReminderEngine.rescheduleAll(context)
                // اعلان همیشگی تاریخ پس از ری‌استارت (نسخه ۱.۳)
                OngoingDateNotifier.update(context)
            } finally {
                pending.finish()
            }
        }
    }
}
