package ir.persiancalendar.app.core.calendar

import java.time.LocalDate

/**
 * ابزارهای متن فارسی: ارقام فارسی، نام ماه‌ها، روزهای هفته و قالب‌بندی تاریخ.
 */
object PersianText {

    private val faDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val arDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /** تبدیل ارقام لاتین رشته به ارقام فارسی */
    fun toPersianDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in '0'..'9') sb.append(faDigits[c - '0']) else sb.append(c)
        }
        return sb.toString()
    }

    /** تبدیل ارقام لاتین به ارقام عربی (برای نمایش تاریخ قمری) */
    fun toArabicDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in '0'..'9') sb.append(arDigits[c - '0']) else sb.append(c)
        }
        return sb.toString()
    }

    /** بسته به حالت انتخابی کاربر، ارقام را تبدیل می‌کند */
    fun formatDigits(s: String, persian: Boolean, hijri: Boolean = false): String =
        when {
            persian -> toPersianDigits(s)
            hijri -> toArabicDigits(s)
            else -> s
        }

    val jalaliMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val gregorianMonths = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    /** ماه‌های قمری — اندیس ۰ = محرم */
    val hijriMonths = arrayOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    /**
     * نام روزهای هفته با اندیس ۰ = شنبه تا ۶ = جمعه
     * (چینش ستون‌های تقویم از شنبه شروع می‌شود)
     */
    val weekDays = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    val weekDaysShort = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    /** اندیس روز هفته بر پایه شنبه = ۰ (برای LocalDate) */
    fun weekdayIndexSat0(date: LocalDate): Int =
        when (date.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> 0
            java.time.DayOfWeek.SUNDAY -> 1
            java.time.DayOfWeek.MONDAY -> 2
            java.time.DayOfWeek.TUESDAY -> 3
            java.time.DayOfWeek.WEDNESDAY -> 4
            java.time.DayOfWeek.THURSDAY -> 5
            java.time.DayOfWeek.FRIDAY -> 6
        }

    fun jalaliDateFull(d: JalaliDate, persianDigits: Boolean = true): String {
        val s = "${jalaliMonths[d.month - 1]} ${d.day}، ${d.year}"
        return if (persianDigits) toPersianDigits(s) else s
    }

    fun jalaliDateShort(d: JalaliDate, persianDigits: Boolean = true): String {
        val s = String.format("%d/%02d/%02d", d.year, d.month, d.day)
        return if (persianDigits) toPersianDigits(s) else s
    }

    fun gregorianDateFull(g: GregorianDate, persianDigits: Boolean = true): String {
        val s = "${gregorianMonths[g.month - 1]} ${g.day}, ${g.year}"
        return if (persianDigits) toPersianDigits(s) else s
    }

    fun hijriDateFull(h: HijriDate, persianDigits: Boolean = true): String {
        val s = "${hijriMonths[(h.month - 1).coerceIn(0, 11)]} ${h.day}، ${h.year} هجری قمری"
        return if (persianDigits) toPersianDigits(s) else toArabicDigits(s)
    }

    /** قالب ساعت HH:MM با ارقام دلخواه */
    fun timeHM(hour: Int, minute: Int, persianDigits: Boolean = true): String {
        val s = String.format("%02d:%02d", hour, minute)
        return if (persianDigits) toPersianDigits(s) else s
    }

    /** «ماه سال» برای سرصفحه تقویم */
    fun monthYear(jy: Int, jm: Int, persianDigits: Boolean = true): String {
        val s = "${jalaliMonths[jm - 1]} $jy"
        return if (persianDigits) toPersianDigits(s) else s
    }
}
