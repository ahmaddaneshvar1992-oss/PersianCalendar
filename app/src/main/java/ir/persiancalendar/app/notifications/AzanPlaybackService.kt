package ir.persiancalendar.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
 *
 * نسخهٔ ۱.۱۱.۴۰ (گزارش کاربر: «نوفیکیشن اذان بالا می‌آید ولی صدای اذان پخش
 * نمی‌شود») — دو ریشهٔ واقعی و رفع هر دو:
 *  ۱) صدای اذان قبلاً روی استریم «رسانه» (USAGE_MEDIA) پخش می‌شد؛ اگر ولوم
 *     رسانهٔ گوشی صفر/م‌لود باشد (خیلی رایج است) نوتیف می‌آید ولی هیچ صدایی
 *     شنیده نمی‌شود — اذان حالا مثل زنگ ساعت روی استریم «هشدار»
 *     (USAGE_ALARM) پخش می‌شود که ولوم مستقل خودش را دارد و با ولوم رسانه
 *     کاری ندارد.
 *  ۲) اگر سیستم شروع سرویس Foreground را در پس‌زمینه مسدود کند
 *     (ForegroundServiceStartNotAllowedException روی بعضی لایه‌های اندروید)،
 *     خطا بی‌صدا قورت داده می‌شد و هیچ صدایی پخش نمی‌شد — حالا در این حالت
 *     پخش فالبکی داخل خود گیرنده آغاز می‌شود تا حداقل شروع اذان شنیده شود.
 *
 * نسخهٔ ۱.۱۱.۴۷ (گزارش کاربر: «وقتی اذان میگه من ولوم صداشو کم میکنم صدای
 * اذان کم نمیشه») — آینهٔ ولوم: در حین پخش اذان، گیرندهٔ تغییر ولوم ثبت
 * می‌شود؛ تغییر ولوم استریم‌های رسانه/زنگ/اعلان به‌صورت متناسب روی استریم
 * «هشدار» اعمال می‌شود (AzanVolumePolicy) — پس دکمه‌های ولوم در هر صفحه‌ای
 * مستقیماً صدای اذان را کم و زیاد می‌کنند. با پایان پخش، گیرنده برداشته
 * می‌شود و رفتار ولوم گوشی عادی برمی‌گردد.
 */
class AzanPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var currentKey: String = "none"

    // ---------- Audio Focus — نسخهٔ ۱.۱۱.۳۳ ----------
    // AudioFocusRequest از API ۲۶؛ فیلد به‌صورت Any? نگه داشته می‌شود تا
    // لود شدن کلاس روی اندروید ۷ (API ۲۴/۲۵) به مشکل نخورد
    private var focusRequest26: Any? = null

    // ---------- آینهٔ ولوم — نسخهٔ ۱.۱۱.۴۷ ----------
    private var volumeReceiverRegistered = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isPlaying) return
            val stream = intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) ?: return
            if (!AzanVolumePolicy.shouldMirror(stream)) return
            val value = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
            if (value < 0) return
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val srcMax = runCatching { am.getStreamMaxVolume(stream) }.getOrDefault(0)
            val alarmMax = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_ALARM) }
                .getOrDefault(0)
            val current = runCatching { am.getStreamVolume(AudioManager.STREAM_ALARM) }
                .getOrDefault(-1)
            val target = AzanVolumePolicy.mirroredAlarmVolume(value, srcMax, alarmMax)
            // فقط اگر واقعاً تغییر کند اعمال شود (جلوگیری از حلقهٔ رویداد)
            if (AzanVolumePolicy.needsApply(target, current))
                runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
        }
    }

    /** ثبت گیرندهٔ تغییر ولوم — فقط تا پایان پخش (پرچم برای جلوگیری از ثبت دوباره) */
    private fun registerVolumeMirror() {
        if (volumeReceiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                this, volumeReceiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            volumeReceiverRegistered = true
        }
    }

    private fun unregisterVolumeMirror() {
        if (!volumeReceiverRegistered) return
        runCatching { unregisterReceiver(volumeReceiver) }
        volumeReceiverRegistered = false
    }

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
        player = buildAlarmStreamPlayer(resId)

        isPlaying = player != null && runCatching { player?.isPlaying == true }.getOrDefault(false)

        // نسخهٔ ۱.۱۱.۴۷: تا پایان پخش، ولومِ کاربر روی اذان آینه می‌شود
        if (isPlaying) registerVolumeMirror()

        if (player == null) stopPlayback()
        return START_NOT_STICKY
    }

    /**
     * ساخت پلیر با استریم «هشدار» (USAGE_ALARM) — نسخهٔ ۱.۱۱.۴۰.
     * MediaPlayer.create اجازهٔ ست‌کردن AudioAttributes نمی‌دهد (بعد از prepare
     * ممنوع است)؛ پس پلیر دستی ساخته می‌شود: attributes → dataSource → prepare.
     * صدای اذان حالا از ولوم «هشدار» گوشی می‌آید نه «رسانه» — حتی با ولوم رسانهٔ
 * صفر هم اذان شنیده می‌شود (ریشهٔ اصلی گزارش کاربر).
     */
    private fun buildAlarmStreamPlayer(resId: Int): MediaPlayer? = runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM) // استریم هشدار — مثل زنگ ساعت
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val p = MediaPlayer()
        p.setAudioAttributes(attrs)
        val afd = resources.openRawResourceFd(resId)
        p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        p.prepare()
        p.setOnCompletionListener { stopPlayback() }
        p.start()
        p
    }.getOrNull()

    override fun onDestroy() {
        isPlaying = false
        runCatching { player?.release() }
        player = null
        AdhanSounds.release()
        unregisterVolumeMirror()
        super.onDestroy()
    }

    /** توقف کامل: قطع صدا، آزادکردن فوکوس، برداشتن اعلان‌ها و توقف خود سرویس */
    private fun stopPlayback() {
        isPlaying = false
        runCatching { player?.release() }
        player = null
        AdhanSounds.release()
        abandonAudioFocus()
        unregisterVolumeMirror()
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
            .setUsage(AudioAttributes.USAGE_ALARM) // هم‌استریم با پخش اذان — نسخهٔ ۱.۱۱.۴۰
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
            am.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
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

        // نسخهٔ ۱.۱۱.۴۷: پخش سیستمِ تغییر ولوم (ثابت آن مخفی است) — رشتهٔ صریح
        // برای سازگاری کامل روی همهٔ لایه‌ها
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"

        /** اندیس وعدهٔ در حال اذان (۰ تا ۷) — برای صفحهٔ اذان و اعلان — نسخهٔ ۱.۱۱.۳۳ */
        const val EXTRA_PRAYER_INDEX = "azan_prayer_index"

        /** اکشن توقف پخش اذان — نسخه ۱.۱۱.۱۴ */
        const val ACTION_STOP = "ir.persiancalendar.app.action.AZAN_STOP"
        private const val REQUEST_STOP = 9002

        /** آیا اکنون صدای اذان در حال پخش است؟ (برای توقف خودکار هنگام باز شدن اپ) */
        @Volatile
        var isPlaying: Boolean = false
            private set

        /** راه‌اندازی امن سرویس پخش اذان از هر جای برنامه/گیرنده —
         *  نسخهٔ ۱.۱۱.۴۰: اگر سیستم شروع سرویس در پس‌زمینه را مسدود کند
         *  (ForegroundServiceStartNotAllowedException — بعضی لایه‌ها/ROMها)،
         *  پخش فالبکی با AdhanSounds داخل خود گیرنده آغاز می‌شود تا اذان
         *  به‌جای سکوت کامل، حداقل شروعش شنیده شود (goAsync فرصت ~۱۰ ثانیه‌ای
         *  به گیرنده می‌دهد — برای شروع پخش کافی است).
         */
        fun start(context: Context, soundKey: String, prayerIndex: Int = -1) {
            val intent = Intent(context, AzanPlaybackService::class.java)
                .putExtra(EXTRA_SOUND_KEY, soundKey)
                .putExtra(EXTRA_PRAYER_INDEX, prayerIndex)
            val delivered = runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)
            if (!delivered) {
                // فالبک: پخش مستقیم در پردازهٔ گیرنده — بهتر از هیچ صدایی
                runCatching { AdhanSounds.play(context, soundKey) }
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
