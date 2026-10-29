package ir.persiancalendar.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.persiancalendar.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * سرویس همیشگی تقویم — نسخه ۱.۱۱.۱۴
 *
 * درخواست کاربر (با اسکرین‌شات نوار وضعیت): «وقتی اسکوربار گوشی را پایین می‌کشم
 * هیچ نوتیفیکیشن تقویم فعال نیست و می‌خوام همیشه فعال باشه — مثل برنامه ADM یا
 * VPN؛ این خیلی برایم مهمه».
 *
 * چرا سرویس Foreground؟
 * تا نسخه ۱.۱۱.۱۳ «اعلان همیشگی تاریخ» فقط یک notify() ساده بود که در باز شدن اپ،
 * تیک نیمه‌شب و ری‌استارت گوشی دوباره ساخته می‌شد؛ کاربر می‌توانست آن را با سوایپ
 * حذف کند و تا تیک بعدی (حتی تا نیمه‌شب) هیچ اعلانی از تقویم در نوار وضعیت نمی‌ماند
 * و هر فرایند کشته‌شده هم اعلان را پاک می‌کرد. اکنون — دقیقاً مثل نوتیفیکیشن VPN یا
 * ADM — یک سرویس Foreground واقعی نگه‌دارندهٔ این اعلان است:
 *  - اعلان از نوع ongoing است و با سوایپ برنمی‌گردد (در اندروید ۱۴+ اگر کاربر حذفش
 *    کند، سرویس هر دقیقه دوباره آن را می‌سازد)؛
 *  - با کشته‌شدن پردازه توسط سیستم، START_STICKY سرویس را دوباره بالا می‌آورد؛
 *  - متن اعلان (تاریخ + ساعت جاری) هر دقیقه تازه می‌شود و آیکن «عدد روز» هر روز
 *    نیمه‌شب عوض می‌شود.
 *
 * نوع سرویس روی اندروید ۱۴+ «specialUse» است (مجاز برای ابزارهای همیشه‌فعال مثل
 * تقویم/ساعت) و در مانیفست با PROPERTY_SPECIAL_USE_FGS_SUBTYPE توضیح داده شده است.
 *
 * شروع سرویس: باز شدن برنامه (PersianCalendarApp / MainActivity.onResume)، پایان
 * راه‌اندازی اولیه، ری‌استارت گوشی (BootReceiver) و تیک نیمه‌شب — همه از مسیر
 * OngoingDateNotifier.update(). اگر اندروید اجازهٔ شروع از پس‌زمینه ندهد (اندروید
 * ۱۲ تا ۱۳ هنگام تیک آلارم)، به‌جای آن یک بار اعلان سادهٔ ongoing ساخته می‌شود تا
 * دست کم چیزی در نوار وضعیت بماند.
 */
class CalendarForegroundService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // احترام به کلید تنظیمات «اعلان همیشگی تاریخ»
        val enabled = runCatching {
            AppContainer.settingsRepo(this).settingsBlocking().ongoingDateNotif
        }.getOrDefault(true)
        if (!enabled) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        // حلقهٔ تازه‌سازی: هر دقیقه متن اعلان (ساعت جاری) به‌روز می‌شود؛
        // در Doze این تأخیرها به تعویق می‌افتند که اشکالی ندارد (تاریخ درست می‌ماند).
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s ->
            s.launch {
                while (isActive) {
                    OngoingDateNotifier.postOngoing(this@CalendarForegroundService)
                    val now = System.currentTimeMillis()
                    delay(60_000L - (now % 60_000L) + 250L)
                }
            }
        }
        // START_STICKY: اگر سیستم پردازه را کشت، سرویس دوباره بالا می‌آید
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching {
            ServiceCompat.startForeground(
                this,
                OngoingDateNotifier.NOTIF_ID_ONGOING,
                OngoingDateNotifier.buildOngoingNotification(this),
                type
            )
        }
    }

    companion object {

        /** راه‌اندازی سرویس همیشگی (از هر جای برنامه یا گیرنده‌های بوت/آلارم) */
        fun start(context: Context) {
            val app = context.applicationContext
            val started = runCatching {
                ContextCompat.startForegroundService(
                    app, Intent(app, CalendarForegroundService::class.java)
                )
                true
            }.getOrDefault(false)
            if (!started) {
                // شروع از پس‌زمینه مجاز نبود — دست کم اعلان سادهٔ ongoing بماند
                OngoingDateNotifier.postOngoing(app)
            }
        }

        /** توقف کامل سرویس + حذف اعلان (وقتی کاربر از تنظیمات خاموش می‌کند) */
        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.stopService(Intent(app, CalendarForegroundService::class.java))
            }
            OngoingDateNotifier.cancelNotification(app)
        }
    }
}
