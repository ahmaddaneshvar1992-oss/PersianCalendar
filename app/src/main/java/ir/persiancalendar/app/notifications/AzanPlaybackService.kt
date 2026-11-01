package ir.persiancalendar.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
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
 * نسخه ۱.۱۱.۱۴ (گزارش کاربر: «وقتی اذان پخش میشه توقف نداره، حتماً باید
 * برنامه رو بکشی تا صدا متوقف بشه»):
 *  - اعلان پخش اذان دکمهٔ «توقف اذان» دارد (اکشن ACTION_STOP)؛
 *  - اعلان اوقات شرعی هم دکمهٔ «توقف صدا» دارد؛
 *  - باز کردن برنامه هنگام پخش اذان آن را متوقف می‌کند
 *    (MainActivity.onResume → AzanPlaybackService.stop).
 * دیگر نیازی به بستن اجباری برنامه نیست.
 *
 * نسخهٔ ۱.۱۱.۱۹ (درخواست کاربر: «وقتی نوفیکیش اذان پخش میشه میخوام با رد
 * کردن یا پاک کردن نوفیکیش اذان صدا اذان قطع بشه»):
 *  - اعلان اوقات شرعی deleteIntent دارد: کشیدن اعلان به کنار یا «پاک
 *    کردن همه» صدای اذان را قطع می‌کند؛
 *  - اعلان خود سرویس پخش هم deleteIntent دارد (اندروید ۱۴ به بعد که
 *    اجازهٔ رد کردن اعلان سرویس را می‌دهد، همان‌جا صدا قطع می‌شود).
 *
 * دریافت هشدار اوقات شرعی (PrayerAlarmReceiver) این سرویس را با کلید
 * صدای انتخابی کاربر راه می‌اندازد؛ سرویس یک اعلان کوچک «در حال پخش اذان»
 * نشان می‌دهد و پس از پایان اذان خودش می‌ایستد. اگر کاربر «بدون صدا» انتخاب
 * کرده باشد، سرویس بلافاصله بدون پخش متوقف می‌شود (فقط اعلان).
 */
class AzanPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var currentKey: String = "none"

    // ---------- Audio Focus — نسخهٔ ۱.۱۱.۳۳ ----------
    // AudioFocusRequest از API ۲۶؛ فیلد به‌صورت Any? نگه داشته می‌شود تا
    // لود شدن کلاس روی اندروید ۷ (API ۲۴/۲۵) به مشکل نخورد
    private var focusRequest26: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ---------- «توقف اذان» از دکمهٔ اعلان / باز شدن برنامه (نسخه ۱.۱۱.۱۴) ----------
        if (intent?.action == ACTION_STOP) {
            // اگر سرویس همین حالا با startForegroundService راه افتاده باشد،
            // فراخوانی startForeground الزامی است؛ قبل از توقف انجامش می‌دهیم.
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIF_ID_AZAN_PLAYBACK,
                    playingNotification(currentKey),
                    playbackType()
                )
            }
            // اعلان اوقات شرعی هم بسته شود (کاربر صدا را خواسته متوقف کند)
            NotificationHelper.cancelPrayer(this)
            stopPlayback()
            return START_NOT_STICKY
        }

        val key = intent?.getStringExtra(EXTRA_SOUND_KEY) ?: "none"
        currentKey = key

        // اعلان کوچک سرویس Foreground — ضروری برای mediaPlayback
        runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIF_ID_AZAN_PLAYBACK,
                playingNotification(key),
                playbackType()
            )
        }

        val resId = AdhanSounds.resIdFor(key)
        if (resId == null) {
            // «بدون صدا» یا کلید نامعتبر — فقط اعلان اذان نمایش داده شده است
            stopPlayback()
            return START_NOT_STICKY
        }

        // نسخهٔ ۱.۱۱.۳۳: درخواست Audio Focus (USAGE_MEDIA — استریم رسانه، مطابق
        // تنظیم فعلی اپ)؛ اگر تماس یا پخش دیگری جا را گرفت، اذان متوقف می‌شود
        if (!requestAudioFocus()) {
            stopPlayback()
            return START_NOT_STICKY
        }
        // اگر پخش قبلی هنوز در جریان است، قطعش کن و از نو شروع کن
        AdhanSounds.release()
        player = runCatching {
            // MediaPlayer.create خودش آماده‌سازی (prepare) را انجام می‌دهد؛
            // بعد از آن نباید setAudioAttributes صدا زده شود
            MediaPlayer.create(this, resId)?.apply {
                setOnCompletionListener { stopPlayback() }
                start()
            }
        }.getOrNull()

        isPlaying = player != null && runCatching { player?.isPlaying == true }.getOrDefault(false)

        if (player == null) stopPlayback()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isPlaying = false
        runCatching { player?.release() }
        player = null
        AdhanSounds.release()
        super.onDestroy()
    }

    /** توقف کامل: قطع صدا، آزادکردن فوکوس، برداشتن اعلان‌ها و توقف خود سرویس */
    private fun stopPlayback() {
        isPlaying = false
        runCatching { player?.release() }
        player = null
        AdhanSounds.release()
        abandonAudioFocus()
        // نسخهٔ ۱.۱۱.۳۳ (الزام کاربر): وقتی «توقف» زده شود یا اذان تمام شود،
        // نوتیفیکیشن اوقات شرعی هم حذف شود — نه فقط اعلان سرویس
        NotificationHelper.cancelPrayer(this)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        runCatching { stopSelf() }
    }

    private fun playbackType(): Int =
        if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else 0

    // ---------- Audio Focus (نسخهٔ ۱.۱۱.۳۳) ----------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // از دست دادن فوکوس (تماس ورودی، پخش دیگر) = توقف اذان
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) stopPlayback()
    }

    private fun requestAudioFocus(): Boolean = runCatching {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA) // مطابق تنظیم فعلی اپ (استریم رسانه)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val granted = if (Build.VERSION.SDK_INT >= 26) {
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest26 = req
            am.requestAudioFocus(req as android.media.AudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }.getOrDefault(false)

    private fun abandonAudioFocus() = runCatching {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = focusRequest26
        if (Build.VERSION.SDK_INT >= 26 && req != null)
            am.abandonAudioFocusRequest(req as android.media.AudioFocusRequest)
        else @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener)
        focusRequest26 = null
    }

    /** اعلان «در حال پخش اذان» با دکمهٔ «توقف اذان» (نسخه ۱.۱۱.۱۴) */
    private fun playingNotification(key: String): Notification {
        val stopPi = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, AzanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_AZAN_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("پخش اذان")
            .setContentText(AdhanSounds.labelOf(key))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // دکمهٔ توقف — دیگر لازم نیست برنامه بسته شود
            .addAction(0, "توقف اذان", stopPi)
            // نسخهٔ ۱.۱۱.۱۹: رد کردن اعلان پخش (اندروید ۱۴+) = توقف صدای اذان
            .setDeleteIntent(stopPi)
            .build()
    }

    companion object {
        const val EXTRA_SOUND_KEY = "sound_key"

        /** اندیس وعدهٔ در حال اذان (۰ تا ۷) — برای صفحهٔ اذان و اعلان — نسخهٔ ۱.۱۱.۳۳ */
        const val EXTRA_PRAYER_INDEX = "azan_prayer_index"

        /** اکشن توقف پخش اذان — نسخه ۱.۱۱.۱۴ */
        const val ACTION_STOP = "ir.persiancalendar.app.action.AZAN_STOP"
        private const val REQUEST_STOP = 9002

        /** آیا اکنون صدای اذان در حال پخش است؟ (برای توقف خودکار هنگام باز شدن اپ) */
        @Volatile
        var isPlaying: Boolean = false
            private set

        /** راه‌اندازی امن سرویس پخش اذان از هر جای برنامه/گیرنده */
        fun start(context: Context, soundKey: String, prayerIndex: Int = -1) {
            val intent = Intent(context, AzanPlaybackService::class.java)
                .putExtra(EXTRA_SOUND_KEY, soundKey)
                .putExtra(EXTRA_PRAYER_INDEX, prayerIndex)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        /**
         * توقف پخش اذان — از دکمهٔ اعلان/صفحهٔ اذان (نسخه ۱.۱۱.۱۴).
         * نسخهٔ ۱.۱۱.۳۳: باز شدن خود برنامه دیگر اذان را قطع نمی‌کند؛
         * اذان تا پایان یا تا لمس «توقف» ادامه دارد (الزام کاربر).
         */
        fun stop(context: Context) {
            val app = context.applicationContext
            isPlaying = false
            val delivered = runCatching {
                app.startService(Intent(app, AzanPlaybackService::class.java).setAction(ACTION_STOP))
                true
            }.getOrDefault(false)
            if (!delivered) {
                // اگر شروع سرویس مجاز نبود، مستقیم سرویس را ببند (همیشه مجاز است)
                runCatching {
                    app.stopService(Intent(app, AzanPlaybackService::class.java))
                }
            }
        }
    }
}
