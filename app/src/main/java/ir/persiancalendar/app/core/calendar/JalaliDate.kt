package ir.persiancalendar.app.core.calendar

/**
 * مدل تاریخ‌ها در سه گاه‌شماری.
 * همه فیلدها اعداد صحیح تقویمی هستند (ماه ۱ تا ۱۲، روز ۱ تا ۳۱).
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    fun isValid(): Boolean =
        year in -61..3177 &&
                month in 1..12 &&
                day in 1..CalendarConverter.jalaliMonthLength(year, month)

    override fun toString(): String = "$year/$month/$day"
}

data class GregorianDate(val year: Int, val month: Int, val day: Int)

data class HijriDate(val year: Int, val month: Int, val day: Int)
