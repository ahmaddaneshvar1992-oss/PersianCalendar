package ir.persiancalendar.app.data.model

/** نوع تکرار رویداد */
enum class RepeatType(val label: String) {
    NONE("بدون تکرار"),
    DAILY("هر روز"),
    WEEKLY("هر هفته"),
    MONTHLY("هر ماه (میلادی)"),
    YEARLY("هر سال (شمسی)");

    companion object {
        fun fromOrdinal(i: Int) = entries.getOrElse(i) { NONE }
    }
}

/** فاصله یادآوری قبل از زمان رویداد (دقیقه)؛ null = بدون یادآوری */
enum class ReminderOption(val label: String, val minutesBefore: Int) {
    NONE("بدون یادآوری", -1),
    AT_TIME("به‌موقع", 0),
    MIN_10("۱۰ دقیقه قبل", 10),
    MIN_30("۳۰ دقیقه قبل", 30),
    HOUR_1("۱ ساعت قبل", 60),
    DAY_1("۱ روز قبل", 24 * 60);

    companion object {
        fun fromMinutes(m: Int) = entries.firstOrNull { it.minutesBefore == m } ?: NONE
    }
}

/**
 * محاسبه زمان بعدی وقوع یک رویداد (بر اساس تاریخ میلادی لنگرگاه و نوع تکرار).
 * منطق تکرار:
 *  - روزانه/هفتگی: بر اساس میلادی
 *  - ماهانه: روز معینی از ماه میلادی
 *  - سالانه: همان روز و ماه شمسی (مناسب تولدها و سالگردهای فارسی)
 */
object EventRepetition {

    /** آیا رویداد در تاریخ میلادی داده‌شده رخ می‌دهد؟ */
    fun occursOn(
        anchorGregorian: java.time.LocalDate,
        anchorJalali: ir.persiancalendar.app.core.calendar.JalaliDate,
        repeat: RepeatType,
        target: java.time.LocalDate
    ): Boolean {
        if (target.isBefore(anchorGregorian)) return false
        return when (repeat) {
            RepeatType.NONE -> target == anchorGregorian
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> target.dayOfWeek == anchorGregorian.dayOfWeek
            RepeatType.MONTHLY -> target.dayOfMonth == anchorGregorian.dayOfMonth
            RepeatType.YEARLY -> {
                val tj = ir.persiancalendar.app.core.calendar.CalendarConverter
                    .gregorianToJalali(target.year, target.monthValue, target.dayOfMonth)
                tj.month == anchorJalali.month && tj.day == anchorJalali.day
            }
        }
    }
}
