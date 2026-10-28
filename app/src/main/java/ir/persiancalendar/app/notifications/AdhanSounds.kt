package ir.persiancalendar.app.notifications

import android.content.Context
import android.media.MediaPlayer
import ir.persiancalendar.app.R

/**
 * صدای اذان دلخواه — نسخه ۱.۲
 *
 * سه فایل نمونه در res/raw موجود است:
 *   adhan_mashhad.wav · adhan_hajjaghar.wav · adhan_najaf.wav
 *
 * ⚠️ این فایل‌ها «نمونه صوتی ساده‌شده» هستند. برای تجربه واقعی، فایل اذان دلخواه خود را
 * (mp3 یا wav) با همان نام در res/raw جایگزین کنید — نیازی به تغییر کد نیست.
 * آموزش کامل در docs/WHATS-NEW-v1.2-FA.md آمده است.
 */
object AdhanSounds {

    /** کلیدهای مجاز صدا (مقدار DataStore) */
    val KEYS = listOf("mashhad", "hajjaghar", "najaf", "none")

    /** برچسب فارسی هر کلید برای نمایش در UI */
    val LABELS = listOf(
        "اذان مشهد",
        "اذان حاج‌اصغر (تهران)",
        "اذان نجف اشراف",
        "بدون صدا — فقط اعلان"
    )

    fun labelOf(key: String): String =
        LABELS[KEYS.indexOf(key).takeIf { it >= 0 } ?: 0]

    /** منبع صوتی هر کلید؛ «none» یعنی بدون پخش صدا */
    fun resIdFor(key: String): Int? = when (key) {
        "hajjaghar" -> R.raw.adhan_hajjaghar
        "najaf" -> R.raw.adhan_najaf
        "mashhad" -> R.raw.adhan_mashhad
        else -> null
    }

    /** پلیر نگه‌داشته‌شده تا پخش صدا پس از پایان onReceive ادامه یابد */
    @Volatile
    private var player: MediaPlayer? = null

    /** پخش صدای اذان (پیش‌نمایش در صفحه اوقات شرعی یا هنگام هشدار اذان) */
    fun play(context: Context, key: String): Boolean {
        val resId = resIdFor(key) ?: return false
        return runCatching {
            release()
            val p = MediaPlayer.create(context, resId) ?: return false
            player = p
            p.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            p.start()
            true
        }.getOrDefault(false)
    }

    /** آزادسازی پلیر (در صورت نیاز) */
    fun release() {
        runCatching {
            player?.release()
        }
        player = null
    }
}
