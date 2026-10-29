package ir.persiancalendar.app.notifications

import android.content.Context
import android.media.MediaPlayer
import ir.persiancalendar.app.R

/**
 * صدای اذان دلخواه — نسخه ۱.۱۱.۸
 *
 * گزارش کاربر: «اذان هارو جایگزین کن» — سه نمونه ساده‌شده قدیمی (مشهد/حاج‌اصغر/نجف)
 * حذف شدند و دو اذان کامل و واقعی جایگزین شدند:
 *   adhan_moazenzadeh.mp3 — اذان رحیم مؤذن‌زاده اردبیلی (حدود ۴:۲۶)
 *   adhan_rezayian.mp3    — اذان حسن رضائیان (شورای عالی قرآن، حدود ۴:۳۰)
 *
 * هر دو فایل اذان کامل mp3 هستند و با MediaPlayer پخش می‌شوند
 * (هم پیش‌نمایش داخل برنامه و هم سرویس AzanPlaybackService هنگام اعلان اذان).
 */
object AdhanSounds {

    /** کلیدهای مجاز صدا (مقدار DataStore) */
    val KEYS = listOf("moazenzadeh", "rezayian", "none")

    /** برچسب فارسی هر کلید برای نمایش در UI */
    val LABELS = listOf(
        "اذان مؤذن‌زاده اردبیلی",
        "اذان حسن رضائیان",
        "بدون صدا — فقط اعلان"
    )

    /**
     * نگاشت کلیدهای قدیمی (نسخه‌های ۱.۲ تا ۱.۱۱.۷ که نمونه‌های ساده داشتند)
     * به اذان‌های جدید — تا تنظیم ذخیره‌شده کاربران قدیمی بی‌اعتبار نشود:
     *   mashhad (پیش‌فرض قدیم) → مؤذن‌زاده · hajjaghar/najaf → رضائیان
     */
    private val LEGACY_ALIASES = mapOf(
        "mashhad" to "moazenzadeh",
        "hajjaghar" to "rezayian",
        "najaf" to "rezayian"
    )

    /** نرمال‌سازی کلید: کلیدهای قدیمی به اذان جدید معادل تبدیل می‌شوند */
    private fun normalize(key: String): String = LEGACY_ALIASES[key] ?: key

    fun labelOf(key: String): String {
        val k = normalize(key)
        return LABELS[KEYS.indexOf(k).takeIf { it >= 0 } ?: 0]
    }

    /** منبع صوتی هر کلید؛ «none» یعنی بدون پخش صدا */
    fun resIdFor(key: String): Int? = when (normalize(key)) {
        "moazenzadeh" -> R.raw.adhan_moazenzadeh
        "rezayian" -> R.raw.adhan_rezayian
        else -> null
    }

    /** پلیر نگه‌داشته‌شده تا پخش صدا پس از پایان onReceive ادامه یابد */
    @Volatile
    private var player: MediaPlayer? = null

    /** آیا پیش‌نمایش اذان همین حالا در حال پخش است؟ (برای دکمهٔ toggle — نسخه ۱.۱۱.۱۵) */
    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

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
