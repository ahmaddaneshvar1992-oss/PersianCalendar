package ir.persiancalendar.app

import android.app.Activity
import android.app.Application
import ir.persiancalendar.app.billing.BillingManager
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.notifications.NotificationHelper
import ir.persiancalendar.app.notifications.OngoingDateNotifier
import ir.persiancalendar.app.notifications.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * کلاس اپلیکیشن: راه‌اندازی کانال‌های اعلان، Billing و زمان‌بندی‌های اولیه.
 * (نسخه ۱.۱۱.۵: راه‌اندازی AdMob حذف شد — تبلیغات کامل از برنامه برداشته شدند)
 */
class PersianCalendarApp : Application(), Application.ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()

        AppContainer.init(this)
        NotificationHelper.createChannels(this)

        // ردیابی وضعیت پیش‌زمینه — نسخهٔ ۱.۱۱.۳۳: تصمیم fullScreenIntent اذان
        // («اپ دیگری روی صفحه است؟») به آن نیاز دارد
        registerActivityLifecycleCallbacks(this)

        // اتصال به فروشگاه برای خرید/بازگردانی «عضویت ویژه»
        // (هیچ‌وقت استثنا نمی‌فرستد — روی گوشی‌های بدون گوگل‌پلی هم امن است)
        runCatching { BillingManager.start(this) }

        // زمان‌بندی‌های اولیه: تیک روزانه، اوقات شرعی، یادآوری رویدادها و موتور یادآور عمومی
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                AlarmScheduler.scheduleDailyTick(this@PersianCalendarApp)
                val s = AppContainer.settingsRepo(this@PersianCalendarApp).settings.first()
                AlarmScheduler.schedulePrayerAlarms(
                    this@PersianCalendarApp,
                    s.prayerNotifications,
                    cityIndex = s.cityIndex,
                    method = s.prayerMethod
                )
                AlarmScheduler.rescheduleAllEvents(this@PersianCalendarApp)
                // بازیابی زنجیره آلارم‌های یادآور عمومی (آب/دارو/ورزش/شخصی) — نسخه ۱.۱۱
                ReminderEngine.rescheduleAll(this@PersianCalendarApp)
                // اعلان همیشگی تاریخ امروز در نوار وضعیت (نسخه ۱.۳)
                OngoingDateNotifier.update(this@PersianCalendarApp)
            }
        }
    }

    // ---------- وضعیت پیش‌زمینه (نسخهٔ ۱.۱۱.۳۳) ----------

    companion object {
        @Volatile
        private var foregroundActivities: Int = 0

        /** آیا خود برنامه جلوی صفحه است؟ (تصمیم fullScreenIntent اذان) */
        val isForeground: Boolean get() = foregroundActivities > 0
    }

    override fun onActivityStarted(activity: Activity) {
        foregroundActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
