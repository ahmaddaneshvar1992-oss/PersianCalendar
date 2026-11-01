package ir.persiancalendar.app.core.birthday

import ir.persiancalendar.app.core.calendar.CalendarConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * تست‌های محاسبهٔ «روز تولد شما» — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 * پوشش معیار پذیرش ۵: سال کبیسه شمسی، قاعدهٔ ۳۰ اسفند، روز تولدِ امروز،
 * تفکیک درست روز/ساعت/دقیقه، پیشرفت ۰ تا ۱ — هم‌زنجیر با تست قمری/شمسی CI.
 */
class BirthdayCalculatorTest {

    private val tehran: ZoneId = ZoneId.of("Asia/Tehran")

    private fun at(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): ZonedDateTime {
        val g = CalendarConverter.jalaliToGregorian(jy, jm, jd)
        return ZonedDateTime.of(g.year, g.month, g.day, hour, minute, 0, 0, tehran)
    }

    // ---------- قاعدهٔ ۳۰ اسفند و سال کبیسه ----------

    @Test
    fun `esfand 30 in leap year stays esfand 30`() {
        // ۱۴۰۳ کبیسه است: ۳۰ اسفند ۱۴۰۳ = ۲۰ مارس ۲۰۲۵ وجود دارد
        assertTrue(CalendarConverter.jalaliMonthLength(1403, 12) == 30)
        val s = BirthdayCalculator.compute(at(1403, 10, 1, 8, 0), 12, 30, 0)
        assertNotNull(s)
        assertEquals(30, s!!.nextDay)
        assertEquals(1403, s.nextYear)
        val g = CalendarConverter.jalaliToGregorian(1403, 12, 30)
        assertEquals(LocalDate.of(2025, 3, 20), LocalDate.of(g.year, g.month, g.day))
    }

    @Test
    fun `esfand 30 in non-leap year falls to esfand 29`() {
        // ۱۴۰۴ غیرکبیسه است — اسفند ۲۹ روز دارد
        assertTrue(CalendarConverter.jalaliMonthLength(1404, 12) == 29)
        val s = BirthdayCalculator.compute(at(1404, 9, 1, 8, 0), 12, 30, 0)
        assertNotNull(s)
        assertEquals(29, s!!.nextDay) // قاعدهٔ ۳۰ اسفند ← ۲۹ اسفند
        assertEquals(1404, s.nextYear)
    }

    @Test
    fun `next birthday rolls to next year when passed`() {
        // امروز ۱۰ بهمن ۱۴۰۴؛ تولد ۵ بهمن — تولد بعدی باید ۵ بهمن ۱۴۰۵ باشد
        val s = BirthdayCalculator.compute(at(1404, 11, 10, 9, 0), 11, 5, 0)
        assertEquals(1405, s!!.nextYear)
        assertEquals(5, s.nextDay)
        assertTrue(s.days > 0)
    }

    // ---------- روز تولدِ امروز (پیام ویژه) ----------

    @Test
    fun `today is birthday means zeros and full progress`() {
        val s = BirthdayCalculator.compute(at(1404, 7, 15, 21, 30), 7, 15, 0)
        assertTrue(s!!.isToday)
        assertEquals(0L, s.days)
        assertEquals(0L, s.hours)
        assertEquals(0L, s.minutes)
        assertEquals(1f, s.progress)
    }

    // ---------- تفکیک روز/ساعت/دقیقه (ارقام درست) ----------

    @Test
    fun `countdown decomposes days hours minutes correctly`() {
        // از ۲۵ اسفند ۱۴۰۴ ساعت ۱۰:۳۰ تا ۲۹ اسفند ۱۴۰۴ ۰۰:۰۰ (تولد ۳۰/۱۲ ← ۲۹/۱۲)
        // فاصله: ۳ روز و ۱۳ ساعت و ۳۰ دقیقه
        val s = BirthdayCalculator.compute(at(1404, 12, 25, 10, 30), 12, 30, 0)
        assertEquals(29, s!!.nextDay)
        assertEquals(3L, s.days)
        assertEquals(13L, s.hours)
        assertEquals(30L, s.minutes)
    }

    @Test
    fun `countdown near midnight boundary`() {
        // ۲۸ اسفند ساعت ۲۳:۵۹ تا ۲۹ اسفند ۰۰:۰۰ = یک دقیقه مانده
        val s = BirthdayCalculator.compute(at(1404, 12, 28, 23, 59), 12, 30, 0)
        assertEquals(0L, s!!.days)
        assertEquals(0L, s.hours)
        assertEquals(1L, s.minutes)
    }

    // ---------- نوار پیشرفت (معیار پذیرش ۶) ----------

    @Test
    fun `progress is between 0 and 1 and grows with time`() {
        val s1 = BirthdayCalculator.compute(at(1404, 5, 1, 12, 0), 7, 15, 0)
        val s2 = BirthdayCalculator.compute(at(1404, 6, 20, 12, 0), 7, 15, 0)
        assertTrue(s1!!.progress in 0f..1f)
        assertTrue(s2!!.progress in 0f..1f)
        assertTrue("پیشرفت باید با گذشت زمان زیاد شود", s2.progress > s1.progress)
        // وسط دوره تقریباً نیمه است (سال عادی ۳۶۵ روزه — تولد ۷/۱۵ ← فاصله ۳۶۵ روز)
        val mid = BirthdayCalculator.compute(at(1404, 1, 16, 12, 0), 7, 15, 0) // نیمهٔ سال
        assertTrue(mid!!.progress > 0.25f && mid.progress < 0.75f)
    }

    // ---------- سن و ورودی خراب ----------

    @Test
    fun `turning age uses birth year when provided`() {
        val s = BirthdayCalculator.compute(at(1404, 5, 1, 12, 0), 7, 15, 1380)
        assertEquals(24, s!!.turningAge) // ۱۴۰۵ - ۱۳۸۰
        val noYear = BirthdayCalculator.compute(at(1404, 5, 1, 12, 0), 7, 15, 0)
        assertNull(noYear!!.turningAge)
    }

    @Test
    fun `invalid input returns null without crash`() {
        assertNull(BirthdayCalculator.compute(at(1404, 5, 1, 12, 0), 13, 5, 0))
        assertNull(BirthdayCalculator.compute(at(1404, 5, 1, 12, 0), 0, 32, 0))
    }
}
