package ir.persiancalendar.app.notifications

/**
 * سیاست صفحهٔ اذان — نسخهٔ ۱.۱۱.۳۳ (درخواست کاربر: «وقتی اذان پخش می‌شود،
 * صفحهٔ اوقات شرعی باید خودکار تمام‌صفحه بالا بیاید، حتی با اپ بسته یا
 * گوشی قفل یا صفحهٔ خاموش»)
 *
 * همهٔ تصمیم‌های رفتاری اذان در یک‌جا جمع شده‌اند تا بدون دستگاه اندرویدی
 * هم با تست واحد قابل راستی‌آزمایی باشند (AzanPolicyTest در CI).
 */

/** وعده‌های شرعی — هر اندیس همان جدول AlarmScheduler (۰ تا ۷) است */
enum class PrayerSlot(val label: String, val short: String, val isAdhan: Boolean) {
    FAJR("اذان صبح", "صبح", true),
    SUNRISE("طلوع آفتاب", "طلوع", false),
    DHUHR("اذان ظهر", "ظهر", true),
    ASR("اذان عصر", "عصر", false),
    SUNSET("غروب آفتاب", "غروب", false),
    MAGHRIB("اذان مغرب", "مغرب", true),
    ISHA("اذان عشاء", "عشاء", true),
    MIDNIGHT("نیمه‌شب شرعی", "نیمه‌شب", false);

    /** برچسب وسط بخش آسمان: «اذان ظهر به افق <شهر>» */
    fun skyLabel(cityName: String): String = "$label به افق $cityName"

    companion object {
        /** فقط اندیس‌های مجاز پذیرفته می‌شوند (اعتبارسنجی extraهای Intent) */
        fun fromIndex(index: Int): PrayerSlot? = entries.getOrNull(index)
    }
}

/** حالت زنگ گوشی — بدون وابستگی به اندروید تا تست‌پذیر باشد */
enum class RingerKind { NORMAL, VIBRATE, SILENT }

object AzanPolicy {

    // مقادیر NotificationManager.interruptionFilter (ثابت‌های خالص برای تست)
    const val DND_UNKNOWN = 0
    const val DND_ALL = 1
    const val DND_PRIORITY = 2
    const val DND_NONE = 3
    const val DND_ALARMS = 4

    /**
     * آیا صدای اذان پخش شود؟
     * در حالت بی‌صدا/ویبره یا مزاحم نشوید (DND) صدایی پخش نمی‌شود و فقط
     * اعلانِ بدون صدا نمایش داده می‌شود (الزام معیار پذیرش ۳).
     * DND_UNKNOWN یعنی وضعیت خوانده نشد — محافظه‌کارانه اجازهٔ پخش می‌دهیم.
     */
    fun shouldPlaySound(ringer: RingerKind, interruptionFilter: Int): Boolean =
        ringer == RingerKind.NORMAL &&
            (interruptionFilter == DND_ALL || interruptionFilter == DND_UNKNOWN)

    /**
     * آیا صفحهٔ اذان با fullScreenIntent تمام‌صفحه بالا بیاید؟
     *  - تنظیم «باز شدن خودکار صفحه اذان» خاموش → هرگز
     *  - دسترسی Full-Screen Intent داده نشده → هرگز (فقط heads-up — معیار ۴)
     *  - صفحهٔ خاموش یا قفل → همیشه (هستهٔ درخواست کاربر)
     *  - صفحه روشن و گوشی باز و اپ دیگری جلوی صفحه است → فقط اگر کاربر
     *    «نمایش تمام‌صفحه» را فعال کرده باشد؛ وگرنه heads-up با اکشن «باز کردن»
     *  - اگر خود برنامهٔ ما جلوی صفحه باشد، پرش طبیعی است
     */
    fun shouldUseFullScreen(
        autoOpen: Boolean,
        alwaysFullScreen: Boolean,
        fsiAllowed: Boolean,
        screenOn: Boolean,
        deviceLocked: Boolean,
        ourAppForeground: Boolean
    ): Boolean =
        autoOpen && fsiAllowed &&
            (alwaysFullScreen || !screenOn || deviceLocked || ourAppForeground)
}
