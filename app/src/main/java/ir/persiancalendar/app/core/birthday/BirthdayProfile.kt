package ir.persiancalendar.app.core.birthday

/**
 * پروفایل تولد — نسخهٔ ۱.۱۱.۴۸ (رفع «سرریز ادیتور تولد»)
 *
 * ریشهٔ باگِ دامنهٔ سال: ادیتور تولد بازهٔ «تا ۱۱۰ سال قبل» می‌دهد
 * (BirthdayCard.kt → minYear = سالِ امروز − ۱۱۰؛ برای امروزِ ۱۴۰۵ = ۱۲۹۵)
 * ولی پارسر قدیمیBirthdayCard.kt#parseBirth سالِ قبل از ۱۳۰۰ را رد می‌کرد —
 * یعنی تولدی که کاربر در بازهٔ مجاز ۱۲۹۵..۱۲۹۹ انتخاب و ذخیره می‌کرد، در
 * بازِ بعدی برنامه بی‌سروصدا null می‌شد و کارت تولد «ثبت‌نشده» می‌افتاد.
 *
 * همهٔ قواعد پارس و اعتبارسنجی حالا در این کلاس خالص است تا با تست واحد JVM
 * راستی‌آزمایی شود (BirthdayProfileTest) و بازهٔ ادیتور و پارسر دیگر از هم
 * جدا نیفتند:
 *  - فرمت ذخیره: "jy/jm/jd" — سالِ ۰ یعنی «بدون سال» (شمارش فقط روز/ماه)
 *  - بازهٔ سال: ۱۲۹۰ تا ۱۵۰۰ (کفِ ۱۲۹۰ زیرِ کفِ ادیتورِ امروز یعنی ۱۲۹۵
 *    حاشیهٔ امن دارد تا تغییر نرخ سال هم بازه را نشکند)
 *  - ورودی خراب/خارج از بازه = null — هرگز کرش نیست
 */
object BirthdayProfile {

    /** کف و سقف سال تولد مجاز — باید با minYear ادیتور (امروز − ۱۱۰) سازگار بماند */
    const val MIN_YEAR = 1290
    const val MAX_YEAR = 1500

    /** سه‌تایی سال/ماه/روز تولد — سال ۰ یعنی «بدون سال» */
    data class Birth(val year: Int, val month: Int, val day: Int)

    /**
     * پارس رشتهٔ ذخیره‌شدهٔ پروفایل ("jy/jm/jd").
     * خروجی null یعنی «ثبت‌نشده/خراب» — caller کارت را خاموش نشان می‌دهد.
     */
    fun parse(raw: String): Birth? {
        val parts = raw.trim().split("/")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return validate(y, m, d)
    }

    /** اعتبارسنجی سه‌تایی خام — همان قواعدِ پارس، برای ورودی مستقیم ادیتور */
    fun validate(year: Int, month: Int, day: Int): Birth? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year != 0 && year !in MIN_YEAR..MAX_YEAR) return null
        return Birth(year, month, day)
    }
}
