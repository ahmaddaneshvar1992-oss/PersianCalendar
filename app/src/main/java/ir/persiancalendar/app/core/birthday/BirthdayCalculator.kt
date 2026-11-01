package ir.persiancalendar.app.core.birthday

import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * محاسبهٔ شمارش معکوس «روز تولد شما» — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 *
 * کاملاً خالص و تست‌پذیر (BirthdayCalculatorTest در CI)؛ هیچ وابستگی به
 * اندروید ندارد و هرگز چیزی به شبکه نمی‌فرستد (الزام حریم خصوصی).
 *
 * قواعد:
 *  - تولد بعدی بر پایهٔ تقویم شمسی محاسبه می‌شود.
 *  - «۳۰ اسفند» در سال غیرکبیسه به «۲۹ اسفند» می‌افتد (معیار پذیرش ۵)؛
 *    به‌طور کلی روزی که در طول آن ماه وجود ندارد به آخر همان ماه clamp می‌شود.
 *  - اگر امروز روز تولد باشد isToday=true و همهٔ شمارنده‌ها صفرند و
 *    پیشرفت ۱۰۰٪ است (پیام ویژه «تولدتان مبارک» در UI).
 *  - ساعت مبنا timezone فعلی دستگاه است (ورودی ZonedDateTime).
 *  - birthYear = 0 یعنی «بدون سال» — فقط روز و ماه ملاک است.
 */
object BirthdayCalculator {

    /** حالت کامل کارت تولد برای یک لحظهٔ زمانی */
    data class BirthdayState(
        val isToday: Boolean,          // امروز روز تولد است؟
        val days: Long,                // روزهای باقی‌مانده
        val hours: Long,               // ساعات باقی‌مانده (پس از روزها)
        val minutes: Long,             // دقیقه‌های باقی‌مانده (پس از ساعت‌ها)
        val progress: Float,           // ۰ تا ۱: چقدر از فاصلهٔ دو تولد گذشته؟
        val nextYear: Int,             // سال تولد بعدی (شمسی)
        val nextMonth: Int,
        val nextDay: Int,              // بعد از قاعدهٔ ۳۰ اسفند
        val turningAge: Int?,          // سن روز تولد بعدی — فقط وقتی سال شناخته است
        val birthdayIso: String        // تاریخ میلادی تولد بعدی — برای نمایش/دیباگ
    )

    /**
     * روز مؤثر تولد در سال jy — قاعدهٔ ۳۰ اسفند و clamp روزهای ناموجود.
     */
    fun effectiveDay(birthMonth: Int, birthDay: Int, jy: Int): Int {
        val month = birthMonth.coerceIn(1, 12)
        val len = CalendarConverter.jalaliMonthLength(jy, month)
        return birthDay.coerceAtMost(len)
    }

    /**
     * محاسبهٔ حالت کارت.
     * @param now لحظهٔ فعلی با timezone دستگاه
     * @param birthMonth/birthDay روز تولد شمسی (۱..۱۲ و ۱..۳۱)
     * @param birthYear سال تولد شمسی؛ ۰ = بدون سال
     */
    fun compute(now: ZonedDateTime, birthMonth: Int, birthDay: Int, birthYear: Int): BirthdayState? {
        if (birthMonth !in 1..12 || birthDay !in 1..31) return null
        val zone: ZoneId = now.zone
        val todayG: LocalDate = now.toLocalDate()
        val todayJ = CalendarConverter.gregorianToJalali(todayG.year, todayG.monthValue, todayG.dayOfMonth)
        val todayJdn = CalendarConverter.j2d(todayJ.year, todayJ.month, todayJ.day)

        // ---------- یافتن تولد بعدی ----------
        var year = todayJ.year
        var day = effectiveDay(birthMonth, birthDay, year)
        var candJdn = CalendarConverter.j2d(year, birthMonth, day)
        if (candJdn < todayJdn) {
            year += 1
            day = effectiveDay(birthMonth, birthDay, year)
            candJdn = CalendarConverter.j2d(year, birthMonth, day)
        }
        val isToday = candJdn == todayJdn

        // ---------- لحظهٔ دقیق تولد بعدی و قبلی در timezone دستگاه ----------
        val nextG = CalendarConverter.jalaliToGregorian(year, birthMonth, day)
        val nextDate = LocalDate.of(nextG.year, nextG.month, nextG.day)
        val end = nextDate.atStartOfDay(zone).toInstant()
        val prevYear = year - 1
        val prevDay = effectiveDay(birthMonth, birthDay, prevYear)
        val prevG = CalendarConverter.jalaliToGregorian(prevYear, birthMonth, prevDay)
        val prevDate = LocalDate.of(prevG.year, prevG.month, prevG.day)
        val start = prevDate.atStartOfDay(zone).toInstant()

        // ---------- شمارش معکوس ----------
        val nowInstant = now.toInstant()
        val remaining = Duration.between(nowInstant, end)
        val total = Duration.between(start, end).toMillis().coerceAtLeast(1L)
        val progress = when {
            isToday -> 1f
            remaining.isNegative -> 1f
            else -> (((nowInstant.toEpochMilli() - start).toDouble() / total).coerceIn(0.0, 1.0)).toFloat()
        }
        val remMs = if (remaining.isNegative) 0L else remaining.toMillis()
        val days = remMs / 86_400_000L
        val hours = (remMs % 86_400_000L) / 3_600_000L
        val minutes = (remMs % 3_600_000L) / 60_000L

        return BirthdayState(
            isToday = isToday,
            days = days,
            hours = hours,
            minutes = minutes,
            progress = progress,
            nextYear = year,
            nextMonth = birthMonth,
            nextDay = day,
            turningAge = if (birthYear > 0) (year - birthYear).coerceAtLeast(0) else null,
            birthdayIso = nextDate.toString()
        )
    }
}
