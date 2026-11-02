package ir.persiancalendar.app.ui.calendar

import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText

/**
 * منطق محض «نمای ماهانهٔ بهبودیافته» — نسخهٔ ۱.۱۱.۳۹ (کار ۲)
 *
 * همهٔ قواعد نمایشی که باید تست‌پذیر باشند این‌جاست تا هیچ محاسبه‌ای دوباره
 * نوشته نشود و هیچ چیز روی نخ اصلی سنگین نشود:
 *  - نقشهٔ نقطه‌های رویداد (حداکثر ۳ نقطه؛ نقطهٔ سوم کم‌رنگ + نشانهٔ «+»)
 *  - ردیف‌های ماه (۶ ردیف ثابت؛ ماه‌های ۵ و ۶ ردیفی بدون پرش ارتفاع)
 *  - شمارهٔ هفتهٔ سال شمسی برای هر ردیف (ستون اختیاری تنظیمات)
 *  - منطق نمایش دکمهٔ شناور «امروز» (فقط وقتی از ماه جاری دور شده‌ایم)
 *  - متن TalkBack کامل هر خانه («آبان، ۱۰، یکشنبه، تعطیل رسمی، ۲ رویداد»)
 *
 * هیچ وابستگی به Compose ندارد تا تست واحد روی JVM ساده اجرا شود.
 */
object MonthGridLogic {

    /** همیشه ۶ ردیف کشیده می‌شود تا ارتفاع صفحهٔ تقویم بین ماه‌ها ثابت بماند */
    const val FIXED_ROW_COUNT = 6

    /** حداکثر نقطهٔ رنگی رویداد زیر عدد روز */
    const val MAX_EVENT_DOTS = 3

    /** حداقل ارتفاع ناحیهٔ لمسی هر خانه (الزام دسترس‌پذیری ۴۸dp) */
    const val MIN_CELL_HEIGHT_DP = 48

    // ---------- حالت تراکم — نسخهٔ ۱.۱۱.۴۰ (کار ۵) ----------

    /**
     * سطح تراکم روز بر اساس تعداد رویدادها — چهار سطح ۰ تا ۳:
     *  ۰ = بدون رویداد (بدون رنگ)، ۱ = ۱..۲ رویداد (کم‌رنگ)،
     *  ۲ = ۳..۴ رویداد (میانه)، ۳ = ۵ رویداد و بیشتر (پررنگ).
     * ورودی منفی هم ۰ (بدون کرش).
     */
    fun densityLevel(eventCount: Int): Int = when {
        eventCount <= 0 -> 0
        eventCount <= 2 -> 1
        eventCount <= 4 -> 2
        else -> 3
    }

    /** شدت رنگ پس‌زمینهٔ هر سطح — عدد روز با رنگ معمولی خودش می‌ماند تا خوانا بماند */
    fun densityAlpha(level: Int): Float = when (level.coerceIn(0, 3)) {
        1 -> 0.08f
        2 -> 0.18f
        3 -> 0.30f
        else -> 0f
    }

    /**
     * نقشهٔ نقطه‌های رویداد از روی «تعداد رنگ متمایز» دسته‌بندی‌ها.
     * تا ۳ نقطهٔ پررنگ؛ بیشتر از ۳ → نقطهٔ سوم کم‌رنگ‌تر و نشانهٔ «+».
     */
    data class EventDotPlan(val dotCount: Int, val lastDotDimmed: Boolean, val showPlus: Boolean)

    fun eventDotPlan(distinctColorCount: Int): EventDotPlan = when {
        distinctColorCount <= 0 -> EventDotPlan(0, lastDotDimmed = false, showPlus = false)
        distinctColorCount <= MAX_EVENT_DOTS ->
            EventDotPlan(distinctColorCount, lastDotDimmed = false, showPlus = false)
        else -> EventDotPlan(MAX_EVENT_DOTS, lastDotDimmed = true, showPlus = true)
    }

    /**
     * چند ردیف واقعاً برای این ماه لازم است؟ (ماه‌های ۵ یا ۶ ردیفی)
     * نمای ماهانه همیشه FIXED_ROW_COUNT ردیف می‌کشد؛ این تابع فقط برای
     * تست و راستی‌آزمایی بردارهاست.
     */
    fun requiredRows(monthLength: Int, firstWeekdayIndexSat0: Int): Int {
        if (monthLength <= 0) return 0
        val cells = firstWeekdayIndexSat0.coerceAtLeast(0) + monthLength
        return (cells + 6) / 7
    }

    /**
     * شمارهٔ هفتهٔ سال شمسی برای ردیف [row] (شنبهٔ آغازگر ردیف).
     * مبنا: فاصلهٔ شنبهٔ آن ردیف از شنبهٔ ۱ فروردین بر حسب هفته.
     */
    fun weekNumber(jy: Int, jm: Int, firstWeekdayIndexSat0: Int, row: Int): Int {
        val firstJdn = CalendarConverter.j2d(jy, jm, 1)
        val yearStartJdn = CalendarConverter.j2d(jy, 1, 1)
        val rowSaturdayJdn = firstJdn - firstWeekdayIndexSat0.toLong() + row.toLong() * 7L
        return ((rowSaturdayJdn - yearStartJdn) / 7L).toInt() + 1
    }

    /**
     * منطق نمایش دکمهٔ شناور «امروز»: فقط وقتی کاربر از «ماه جاری» دور شده
     * باشد نمایان می‌شود (همان ماهِ امروز → پنهان).
     */
    fun isAwayFromCurrentMonth(viewJy: Int, viewJm: Int, todayJy: Int, todayJm: Int): Boolean =
        viewJy != todayJy || viewJm != todayJm

    /**
     * متن TalkBack کامل هر خانه — معیار دسترس‌پذیری:
     * «آبان، ۱۰، یکشنبه، تعطیل رسمی، ۲ رویداد»
     * (+ «جمعه»، + «امروز» یا «روز انتخاب‌شده»، + میلادی/قمری وقتی روشن‌اند)
     */
    fun dayContentDescription(
        monthName: String,
        day: Int,
        weekdayName: String,
        isHoliday: Boolean,
        isFriday: Boolean,
        isToday: Boolean,
        isSelected: Boolean,
        eventCount: Int,
        gregorianLabel: String? = null,
        hijriLabel: String? = null
    ): String {
        val fa = PersianText.toPersianDigits(day.toString())
        val parts = mutableListOf(monthName, fa, weekdayName)
        // نام روز هفته از قبل بخش پایه است؛ «جمعه» فقط وقتی تکرار می‌شود که
        // weekdayName آن را نگفته باشد (جلوگیری از «جمعه، جمعه» در TalkBack)
        if (isFriday && weekdayName != "جمعه") parts.add("جمعه")
        if (isHoliday) parts.add("تعطیل رسمی")
        if (isToday) parts.add("امروز")
        if (isSelected && !isToday) parts.add("روز انتخاب‌شده")
        if (!gregorianLabel.isNullOrBlank()) parts.add("میلادی $gregorianLabel")
        if (!hijriLabel.isNullOrBlank()) parts.add("قمری $hijriLabel")
        parts.add(
            when {
                eventCount <= 0 -> "بدون رویداد"
                else -> "${PersianText.toPersianDigits(eventCount.toString())} رویداد"
            }
        )
        return parts.joinToString("، ")
    }
}
