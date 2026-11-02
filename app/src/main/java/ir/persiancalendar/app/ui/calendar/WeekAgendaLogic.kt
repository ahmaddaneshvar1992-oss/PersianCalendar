package ir.persiancalendar.app.ui.calendar

import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.EventEntity
import java.time.LocalDate

/**
 * منطق محض «نمای هفتگی» — نسخهٔ ۱.۱۱.۴۰ (کار ۴)
 *
 *  - نگاشت دوسویه «صفحهٔ Pager هفته ↔ (سال، ماه، روز)» بر پایهٔ JDN
 *    (شمارهٔ روز مطلق) از موتور تقویم — هیچ محاسبهٔ تکراری‌ای وجود ندارد.
 *  - شروع هفته بر اساس تنظیمات: شنبه (پیش‌فرض) یا یکشنبه.
 *  - مرتب‌سازی agenda: رویدادهای تمام‌روز (ساعت = -۱) بالای فهرست و
 *    بعد از آن‌ها رویدادهای زمان‌دار به ترتیب ساعت/دقیقه.
 *
 * هیچ وابستگی به Compose/Android ندارد تا تست واحد روی JVM ساده اجرا شود.
 */
object WeekAgendaLogic {

    val MIN_YEAR = CalendarConverter.MIN_SUPPORTED_JALALI_YEAR
    val MAX_YEAR = CalendarConverter.MAX_SUPPORTED_JALALI_YEAR

    /**
     * شنبهٔ آغازگر صفحهٔ ۰: شنبهٔ قبل یا مساوی ۱ فروردین MIN_YEAR.
     * همهٔ صفحات از این لنگر ۷روزه قدم می‌خورند؛ با شروع یکشنبه، همان
     * صفحه از روز بعدش (یکشنبه) نمایش داده می‌شود.
     */
    val FIRST_PAGE_ANCHOR_JDN: Long = run {
        val start = CalendarConverter.j2d(MIN_YEAR, 1, 1)
        val g = CalendarConverter.d2g(start)
        val idx = PersianText.weekdayIndexSat0(LocalDate.of(g.year, g.month, g.day))
        start - idx // idx=0 یعنی خودش شنبه است
    }

    /** پایان موتور تقویم: JDN آخرین روز اسفند MAX_YEAR (کبیسه یا غیرکبیسه) */
    private val ENGINE_END_JDN: Long = run {
        val lastDay = CalendarConverter.jalaliMonthLength(MAX_YEAR, 12)
        CalendarConverter.j2d(MAX_YEAR, 12, lastDay)
    }

    /** تعداد کل صفحات Pager هفتگی برای هر حالت شروع هفته */
    fun pageCount(startSaturday: Boolean): Int = pageOfJdn(ENGINE_END_JDN, startSaturday) + 1

    /**
     * شنبه (یا یکشنبه) آغازگر هفتهٔ شامل JDN داده‌شده.
     * شنبه‌محور: شنبه = اندیس ۰ weekdayIndexSat0؛ یکشنبه‌محور: هفته یک روز بعد شروع می‌شود.
     */
    fun weekStartJdnOf(jdn: Long, startSaturday: Boolean): Long =
        if (startSaturday) {
            val idx = sat0IndexOf(jdn)
            jdn - idx
        } else {
            val idx = sat0IndexOf(jdn) // ۰=شنبه ۱=یکشنبه … ۶=جمعه
            // آغاز هفته = یکشنبه؛ برای شنبه (idx=0) هفتهٔ آن، یکشنبهٔ قبل است
            jdn - ((idx - 1 + 7) % 7)
        }

    private fun sat0IndexOf(jdn: Long): Int {
        val g = CalendarConverter.d2g(jdn)
        return PersianText.weekdayIndexSat0(LocalDate.of(g.year, g.month, g.day))
    }

    /** صفحهٔ Pager برای JDN — هفته‌ها از لنگر ۷روزه شمرده می‌شوند */
    fun pageOfJdn(jdn: Long, startSaturday: Boolean): Int {
        val ws = weekStartJdnOf(jdn, startSaturday)
        // در حالت یکشنبه، آغاز صفحه همان شنبهٔ قبل از یکشنبه است
        val anchor = if (startSaturday) ws else ws - 1
        return ((anchor - FIRST_PAGE_ANCHOR_JDN) / 7L).toInt()
    }

    fun pageOf(jy: Int, jm: Int, jd: Int, startSaturday: Boolean): Int =
        pageOfJdn(
            CalendarConverter.j2d(
                jy.coerceIn(MIN_YEAR, MAX_YEAR), jm.coerceIn(1, 12), jd.coerceIn(1, 31)
            ),
            startSaturday
        )

    /** JDN آغازگر صفحهٔ p (همیشه شنبه — حتی وقتی نمایش از یکشنبه شروع می‌شود) */
    fun pageAnchorJdn(page: Int): Long =
        FIRST_PAGE_ANCHOR_JDN + page.toLong().coerceAtLeast(0) * 7L

    /** روزهای هفت‌گانهٔ صفحهٔ p بر حسب تنظیم شروع هفته */
    fun daysOfPage(page: Int, startSaturday: Boolean): List<LocalDate> {
        val anchor = pageAnchorJdn(page)
        val start = if (startSaturday) anchor else anchor + 1
        return (0..6).map { off ->
            val g = CalendarConverter.d2g(start + off)
            LocalDate.of(g.year, g.month, g.day)
        }
    }

    /** آیا (JDN) روز در هفتهٔ صفحهٔ p است؟ */
    fun isJdnInPage(jdn: Long, page: Int, startSaturday: Boolean): Boolean {
        val anchor = pageAnchorJdn(page)
        val start = if (startSaturday) anchor else anchor + 1
        return jdn in start..(start + 6)
    }

    fun jdnOf(jy: Int, jm: Int, jd: Int): Long = CalendarConverter.j2d(jy, jm, jd)

    // ---------- agenda ----------

    /**
     * مرتب‌سازی رویدادهای یک روز برای agenda:
     *  ۱) تمام‌روزها (ساعت = -۱) بالای فهرست — به ترتیب عنوان برای ثبات
     *  ۲) رویدادهای زمان‌دار صعودی بر حسب ساعت، بعد دقیقه، بعد عنوان
     * sort مرتب‌سازی Kotlin پایدار است — ترتیب مساوی‌ها حفظ می‌شود.
     */
    fun sortForAgenda(events: List<EventEntity>): List<EventEntity> =
        events.sortedWith(
            compareBy(
                { it.hour >= 0 },          // تمام‌روز (hour<0) اول
                { it.hour.coerceAtLeast(0) },
                { it.minute },
                { it.title }
            )
        )
}
