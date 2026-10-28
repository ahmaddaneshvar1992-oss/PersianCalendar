package ir.persiancalendar.app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
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
 * کلاس اپلیکیشن: راه‌اندازی کانال‌های اعلان، AdMob، Billing و زمان‌بندی‌های اولیه.
 */
class PersianCalendarApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppContainer.init(this)
        NotificationHelper.createChannels(this)

        // راه‌اندازی AdMob در نخ پس‌زمینه (توصیه گوگل)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { MobileAds.initialize(this@PersianCalendarApp) }
                .onFailure { Log.w("AdMob", "initialize failed", it) }
        }

        // اتصال به فروشگاه برای خرید/بازگردانی «عضویت ویژه»
        // (روی گوشی‌های بدون گوگل‌پلی بی‌صدا ناموفق است)
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
}
