package ir.persiancalendar.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * موتور یادآور عمومی — نسخه ۱.۱۱
 *
 * یک موتور واحد برای «آب، دارو، ورزش و کارهای شخصی»؛ به‌جای چند سیستم جدا،
 * هر یادآور فقط داده است و موتور زمان‌بندی (ReminderScheduler) آن را اجرا می‌کند.
 *
 * فیلدها مطابق طرح کاربر:
 *  id, title, type, daysOfWeek, times, startDate, endDate, enabled, repeatMode
 */
@Entity(tableName = "reminders", indices = [Index("enabled"), Index("type")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** عنوان یادآور — «لیوان آب»، «قرص فشار خون»، «پیاده‌روی» و… */
    val title: String,
    /** اندیس نوع: ۰=آب ۱=دارو ۲=ورزش ۳=شخصی */
    val type: Int = TYPE_PERSONAL,
    /** اندیس حالت تکرار: ۰=یک‌باره ۱=روزانه ۲=هفتگی (روزهای مشخص) */
    val repeatMode: Int = REPEAT_DAILY,
    /** روزهای هفته برای حالت هفتگی — شنبه=۰ تا جمعه=۶ جدا با کاما: «0,2,4» */
    val daysOfWeek: String = "",
    /** نوبت‌ها به «دقیقه از نیمه‌شب» جدا با کاما: «600,720,840,960» = ۱۰ و ۱۲ و ۱۴ و ۱۶ */
    val times: String = "",
    /** تاریخ شروع (جلالی): jy*10000+jm*100+jd — صفر = امروز */
    val startDate: Int = 0,
    /** تاریخ پایان اختیاری (جلالی) — صفر = بی‌پایان */
    val endDate: Int = 0,
    /** هدف روزانه — فقط آب: تعداد لیوان در روز (صفر = بدون هدف) */
    val targetCount: Int = 0,
    /** یادداشت — مثلا «قرص بعد از غذا» */
    val note: String = "",
    /** فعال یا غیرفعال — غیرفعال = آلارم‌ها لغو می‌شوند */
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {

    // ---------- گشادشده‌های راحت (هرگز ذخیره نمی‌شوند) ----------

    /** نوبت‌ها به شکل فهرست مرتب دقیقه‌های روز */
    val timesList: List<Int>
        get() = times.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()

    /** روزهای هفته انتخابی به شکل مجموعه (۰=شنبه … ۶=جمعه) */
    val daysSet: Set<Int>
        get() = daysOfWeek.split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..6 }.toSet()

    companion object {
        const val TYPE_WATER = 0
        const val TYPE_MEDICINE = 1
        const val TYPE_EXERCISE = 2
        const val TYPE_PERSONAL = 3

        const val REPEAT_ONCE = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_WEEKLY = 2

        val typeLabels = arrayOf("آب", "دارو", "ورزش", "شخصی")

        /** کلید روز جلالی (jy*10000+jm*100+jd) از یک تاریخ جلالی */
        fun dayKey(jy: Int, jm: Int, jd: Int): Int = jy * 10000 + jm * 100 + jd
    }
}

/**
 * ثبت انجام‌شدن هر نوبت (دوز دارو، لیوان آب، تمرین و…).
 * یکتا بر اساس (یادآور، روز، نوبت) تا دوبار ثبت نشود.
 */
@Entity(
    tableName = "dose_logs",
    indices = [Index(value = ["reminderId", "dayKey", "timeMinute"], unique = true)]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** کدام یادآور */
    val reminderId: Long,
    /** کلید روز جلالی انجام‌شدن: jy*10000+jm*100+jd */
    val dayKey: Int,
    /** نوبت مربوطه (دقیقه از نیمه‌شب) — برای نوبت‌های آزاد آب، دقیقه واقعی ثبت */
    val timeMinute: Int,
    /** ۰=انجام‌شده */
    val status: Int = 0,
    val loggedAt: Long = System.currentTimeMillis()
)
