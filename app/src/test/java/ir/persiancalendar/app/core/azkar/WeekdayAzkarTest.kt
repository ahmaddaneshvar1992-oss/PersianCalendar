package ir.persiancalendar.app.core.azkar

import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.PersianText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * تست‌های واحد ذکر روزهای هفته — نسخهٔ ۱.۱۱.۴۱
 *
 * بردارها با پایتون راستی‌آزمایی شده‌اند:
 *   ۹ آبان ۱۴۰۵    = شنبه ۳۱ اکتبر ۲۰۲۶  (مرجع رسمی کاربر)
 *   نوروز ۱۴۰۵     = شنبه ۲۱ مارس ۲۰۲۶
 *   ۲۹ اسفند ۱۴۰۵ = شنبه ۲۰ مارس ۲۰۲۷
 * فرمول پایتون: (python_weekday + 2) % 7 با دوشنبه=۰ … یکشنبه=۶
 */
class WeekdayAzkarTest {

    @Test
    fun `official vector 9 aban 1405 is saturday`() {
        // ۳۱ اکتبر ۲۰۲۶ = ۹ آبان ۱۴۰۵ = شنبه (اندیس ۰)
        assertEquals(0, PersianText.weekdayIndexSat0(LocalDate.of(2026, 10, 31)))
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfDay(LocalDate.of(2026, 10, 31)))
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfIndex(0))
    }

    @Test
    fun `nowruz 1405 is saturday`() {
        // نوروز ۱۴۰۵ = ۲۱ مارس ۲۰۲۶ = شنبه
        assertEquals(0, PersianText.weekdayIndexSat0(LocalDate.of(2026, 3, 21)))
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfDay(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun `29 esfand 1405 is saturday 2027-03-20`() {
        // پایان سال ۱۴۰۵ (سال غیرکبیسه، ۲۹ اسفند) = ۲۰ مارس ۲۰۲۷ = شنبه
        assertEquals(0, PersianText.weekdayIndexSat0(LocalDate.of(2027, 3, 20)))
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfDay(LocalDate.of(2027, 3, 20)))
    }

    @Test
    fun `full week maps saturday zero to friday six`() {
        // هفتهٔ کامل از شنبهٔ مرجع: ۳۱ اکتبر ۲۰۲۶ تا ۶ نوامبر ۲۰۲۶ → اندیس ۰..۶
        val saturday = LocalDate.of(2026, 10, 31)
        for (i in 0..6) {
            val day = saturday.plusDays(i.toLong())
            assertEquals("day+$i must map to index $i", i, PersianText.weekdayIndexSat0(day))
            assertEquals(WeekdayAzkar.ALL[i], WeekdayAzkar.resOfDay(day))
        }
        // شناسهٔ هفت ذکر دقیقاً به ترتیب شنبه تا جمعه
        assertEquals(
            listOf(
                R.string.azkar_saturday,
                R.string.azkar_sunday,
                R.string.azkar_monday,
                R.string.azkar_tuesday,
                R.string.azkar_wednesday,
                R.string.azkar_thursday,
                R.string.azkar_friday
            ),
            WeekdayAzkar.ALL
        )
    }

    @Test
    fun `seven azkar resources are distinct and clamped`() {
        // هفت ذکر باید هفت شناسهٔ متمایز باشند
        assertEquals(7, WeekdayAzkar.ALL.toSet().size)
        // ورودی بیرون از بازه گیره می‌شود — بدون کرش
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfIndex(-3))
        assertEquals(R.string.azkar_saturday, WeekdayAzkar.resOfIndex(0))
        assertEquals(R.string.azkar_friday, WeekdayAzkar.resOfIndex(6))
        assertEquals(R.string.azkar_friday, WeekdayAzkar.resOfIndex(9))
    }

    @Test
    fun `consistent with weekdayIndexSat0 for every day of 2026`() {
        // سازگاری نگاشت روز → ذکر با تابع مرجع اندیس هفته، روی هر ۳۶۵ روز ۲۰۲۶
        var d = LocalDate.of(2026, 1, 1)
        var count = 0
        while (d.year == 2026) {
            assertEquals(
                "mismatch on $d",
                WeekdayAzkar.ALL[PersianText.weekdayIndexSat0(d)],
                WeekdayAzkar.resOfDay(d)
            )
            d = d.plusDays(1)
            count++
        }
        assertEquals(365, count)
    }
}
