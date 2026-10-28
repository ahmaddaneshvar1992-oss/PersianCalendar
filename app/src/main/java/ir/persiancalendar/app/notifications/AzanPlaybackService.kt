package ir.persiancalendar.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ir.persiancalendar.app.R

/**
 * سرویس پخش اذان — نسخه ۱.۱۱.۷ (گزارش کاربر: «وقتی نوتیف اذان داده می‌شود
 * می‌خوام اذان پخش بشه»)
 *
 * مشکل قبلی: صدای اذان با MediaPlayer داخل خود BroadcastReceiver پخش می‌شد؛
 * اندروید بعد از پایان onReceive اجازه می‌داد پردازه اپ کشته شود و صدای اذان
 * قطع می‌شد یا اصلاً پخش نمی‌شد. راه‌حل: پخش در یک سرویس Foreground از نوع
 * mediaPlayback انجام می‌شود تا سیستم پردازه را در حین پخش نگه دارد.
 *
 * دریافت هشدار اوقات شرعی (PrayerAlarmReceiver) اکنون این سرویس را با کلید
 * صدای انتخابی کاربر راه می‌اندازد؛ سرویس یک اعلان کوچک «در حال پخش اذان»
 * نشان می‌دهد و پس از پایان اذان خودش می‌ایستد. اگر کاربر «بدون صدا» انتخاب
 * کرده باشد، سرویس بلافاصله بدون پخش متوقف می‌شود (فقط اعلان).
 */
class AzanPlaybackService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getStringExtra(EXTRA_SOUND_KEY) ?: "none"

        // اعلان کوچک سرویس Foreground — ضروری برای mediaPlayback
        val notification = NotificationCompat.Builder(
            this, NotificationHelper.CHANNEL_AZAN_PLAYBACK
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("پخش اذان")
            .setContentText(AdhanSounds.labelOf(key))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIF_ID_AZAN_PLAYBACK,
            notification,
            if (Build.VERSION.SDK_INT >= 29)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else 0
        )

        val resId = AdhanSounds.resIdFor(key)
        if (resId == null) {
            // «بدون صدا» یا کلید نامعتبر — فقط اعلان اذان نمایش داده شده است
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // اگر پخش قبلی هنوز در جریان است، قطعش کن و از نو شروع کن
        AdhanSounds.release()
        player = runCatching {
            // MediaPlayer.create خودش آماده‌سازی (prepare) را انجام می‌دهد؛
            // بعد از آن نباید setAudioAttributes صدا زده شود
            MediaPlayer.create(this, resId)?.apply {
                setOnCompletionListener {
                    stopForegroundCompat()
                    stopSelf()
                }
                start()
            }
        }.getOrNull()

        if (player == null) {
            stopForegroundCompat()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { player?.release() }
        player = null
        AdhanSounds.release()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val EXTRA_SOUND_KEY = "sound_key"

        /** راه‌اندازی امن سرویس پخش اذان از هر جای برنامه/گیرنده */
        fun start(context: Context, soundKey: String) {
            val intent = Intent(context, AzanPlaybackService::class.java)
                .putExtra(EXTRA_SOUND_KEY, soundKey)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
