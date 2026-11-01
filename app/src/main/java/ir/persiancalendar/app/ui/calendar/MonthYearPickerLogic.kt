package ir.persiancalendar.app.ui.calendar

import ir.persiancalendar.app.core.calendar.CalendarConverter

/**
 * منطق محض «انتخابگر سریع سال و ماه» — نسخهٔ ۱.۱۱.۳۹ (کار ۱)
 *
 * - بازهٔ سال‌ها از خود موتور تقویم گرفته می‌شود (CalendarConverter؛
 *   بازهٔ آزموده‌شدهٔ پرت jalaali-js: ۱۱۷۸ تا ۱۴۳۴) و بیرون از آن غیرفعال است.
 * - نگاشت دوسویه «صفحهٔ Pager ↔ (سال، ماه)» برای جابه‌جایی نرم و انیمیشنی.
 * - پارس ورودی تایپی سال با ارقام فارسی/عربی/لاتین؛ ورودی نامعتبر هرگز
 *   کرش نمی‌کند (null برمی‌گردد و UI پیام خطا نشان می‌دهد).
 *
 * هیچ وابستگی به Compose/Android ندارد تا تست واحد روی JVM ساده اجرا شود.
 */
object MonthYearPickerLogic {

    val MIN_YEAR = CalendarConverter.MIN_SUPPORTED_JALALI_YEAR
    val MAX_YEAR = CalendarConverter.MAX_SUPPORTED_JALALI_YEAR

    const val MONTHS_PER_YEAR = 12

    /** تعداد کل صفحات Pager ماهانه (هر ماه یک صفحه) */
    val PAGE_COUNT: Int = (MAX_YEAR - MIN_YEAR + 1) * MONTHS_PER_YEAR

    fun isYearInRange(jy: Int): Boolean = jy in MIN_YEAR..MAX_YEAR

    /** سال بیرون از بازه بدون کرش به لبهٔ نزدیک گیره می‌شود */
    fun clampYear(jy: Int): Int = jy.coerceIn(MIN_YEAR, MAX_YEAR)

    /** صفحهٔ Pager برای (سال، ماه) — ورودی بیرون از بازه گیره می‌شود */
    fun pageOf(jy: Int, jm: Int): Int =
        (clampYear(jy) - MIN_YEAR) * MONTHS_PER_YEAR + (jm.coerceIn(1, MONTHS_PER_YEAR) - 1)

    fun yearOfPage(page: Int): Int =
        MIN_YEAR + (page.coerceIn(0, PAGE_COUNT - 1) / MONTHS_PER_YEAR)

    fun monthOfPage(page: Int): Int =
        (page.coerceIn(0, PAGE_COUNT - 1) % MONTHS_PER_YEAR) + 1

    /** فاصلهٔ ماهی بین دو (سال، ماه) — برای انیمیشن رفتن به ماه دلخواه */
    fun offsetBetweenMonths(fromJy: Int, fromJm: Int, toJy: Int, toJm: Int): Int =
        ((toJy.toLong() - fromJy) * MONTHS_PER_YEAR + (toJm - fromJm))
            .coerceIn(-(PAGE_COUNT.toLong()), PAGE_COUNT.toLong()).toInt()

    /**
     * پارس ورودی تایپی سال: ارقام فارسی «۰-۹»، عربی «٠-٩» و لاتین پذیرفته
     * می‌شود؛ جداکنندهٔ هزارگان (٬ ، ,) و فاصله نادیده گرفته می‌شود؛ هر کاراکتر
     * دیگر یا رشتهٔ خالی/بلند → null (بدون کرش).
     */
    fun parseYearInput(raw: String): Int? {
        if (raw.isBlank()) return null
        val digits = StringBuilder()
        for (c in raw.trim()) {
            when (c) {
                in '۰'..'۹' -> digits.append(c - '۰')
                in '٠'..'٩' -> digits.append(c - '٠')
                in '0'..'9' -> digits.append(c)
                '٬', ',', ' ' -> { /* جداکنندهٔ هزارگان — نادیده */ }
                else -> return null
            }
        }
        if (digits.isEmpty() || digits.length > 4) return null
        return digits.toString().toIntOrNull()
    }

    /** فلش‌های سال/ماه در لبه‌های بازه غیرفعال می‌شوند */
    fun canGoToPrevYear(jy: Int): Boolean = jy > MIN_YEAR
    fun canGoToNextYear(jy: Int): Boolean = jy < MAX_YEAR
    fun canGoToPrevMonth(jy: Int, jm: Int): Boolean = !(jy <= MIN_YEAR && jm <= 1)
    fun canGoToNextMonth(jy: Int, jm: Int): Boolean = !(jy >= MAX_YEAR && jm >= 12)

    /** آیا این (سال، ماه) همان ماهِ امروز است؟ (هایلایت و نشانگر «امروز») */
    fun isCurrentMonth(jy: Int, jm: Int, todayJy: Int, todayJm: Int): Boolean =
        jy == todayJy && jm == todayJm
}
