package ir.persiancalendar.app.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * تست خودکار تبدیل تاریخ — نسخهٔ ۱.۱۱.۳۱ (درخواست کاربر:
 * «تست خودکار تبدیل تاریخ، چون پایه اعتماد کاربره و سریع پیاده می‌شه»)
 *
 * همین تست‌ها روز اول اجرا، یک باگ واقعی در jalCal پیدا کردند:
 * «div» به‌جای «mod» در محاسبهٔ leap — که باعث تشخیص غلط سال کبیسه
 * (مثلاً ۱۴۰۳ که کبیسهٔ رسمی است) و خطای یک‌روزه در دی/بهمن/اسفندِ
 * بعد از ۱ ژانویهٔ سال‌های درگیر می‌شد (۳۹۴ روز در بازهٔ ۲۰۲۰..۲۰۳۵).
 *
 * بردارهای مرجع با پرت عین الگوریتم (scripts/verify_converter_vectors.py)
 * در برابر کتابخانهٔ مرجع jalaali و تقویم رسمی راستی‌آزمایی شده‌اند.
 * این تست‌ها در CI (android-build.yml) پیش از ساخت APK اجرا می‌شوند؛
 * با شکست هر تست، نسخهٔ جدید ساخته و منتشر نمی‌شود.
 */
class CalendarConverterTest {

    // ---------- جفت‌های شناخته‌شده (مقایسه‌شده با تقویم رسمی) ----------

    @Test
    fun `known gregorian to jalali pairs`() {
        // جفت اسکرین‌شات کاربر: ۳۱ اکتبر ۲۰۲۶ = ۹ آبان ۱۴۰۵ (شنبه)
        assertEquals(JalaliDate(1405, 8, 9), CalendarConverter.gregorianToJalali(2026, 10, 31))
        // شب یلدا — آخر آذر (بلندترین شب سال، شبِ ۳۰ آذر به ۱ دی)
        assertEquals(JalaliDate(1404, 9, 30), CalendarConverter.gregorianToJalali(2025, 12, 21))
        // تحویل سال ۱۴۰۴ و ۱۴۰۵
        assertEquals(JalaliDate(1404, 1, 1), CalendarConverter.gregorianToJalali(2025, 3, 21))
        assertEquals(JalaliDate(1405, 1, 1), CalendarConverter.gregorianToJalali(2026, 3, 21))
        // آخرین روز سال کبیسهٔ ۱۴۰۳ — اسفند ۳۰ = ۲۰ مارس ۲۰۲۵ (تقویم رسمی)
        assertEquals(JalaliDate(1403, 12, 30), CalendarConverter.gregorianToJalali(2025, 3, 20))
        // نوروز ۱۳۹۸ و ۱۴۰۳
        assertEquals(JalaliDate(1398, 1, 1), CalendarConverter.gregorianToJalali(2019, 3, 21))
        assertEquals(JalaliDate(1403, 1, 1), CalendarConverter.gregorianToJalali(2024, 3, 20))
    }

    @Test
    fun `known jalali to gregorian pairs`() {
        assertEquals(GregorianDate(2026, 10, 31), CalendarConverter.jalaliToGregorian(1405, 8, 9))
        assertEquals(GregorianDate(2025, 3, 20), CalendarConverter.jalaliToGregorian(1403, 12, 30))
        assertEquals(GregorianDate(2025, 3, 21), CalendarConverter.jalaliToGregorian(1404, 1, 1))
        assertEquals(GregorianDate(2026, 3, 21), CalendarConverter.jalaliToGregorian(1405, 1, 1))
        assertEquals(GregorianDate(2019, 3, 21), CalendarConverter.jalaliToGregorian(1398, 1, 1))
    }

    // ---------- سال کبیسه و طول ماه‌ها ----------

    @Test
    fun `leap years match official calendar`() {
        // کبیسه‌های رسمی نزدیک امروز: ۱۳۹۹ ، ۱۴۰۳ ، ۱۴۰۸ (قاعدهٔ ۳۳ ساله)
        assertTrue(CalendarConverter.isLeapJalaliYear(1399))
        assertTrue(CalendarConverter.isLeapJalaliYear(1403))
        assertTrue(CalendarConverter.isLeapJalaliYear(1408))
        assertFalse(CalendarConverter.isLeapJalaliYear(1398))
        assertFalse(CalendarConverter.isLeapJalaliYear(1400))
        assertFalse(CalendarConverter.isLeapJalaliYear(1404))
        assertFalse(CalendarConverter.isLeapJalaliYear(1405))
    }

    @Test
    fun `month lengths`() {
        assertEquals(31, CalendarConverter.jalaliMonthLength(1405, 1))
        assertEquals(31, CalendarConverter.jalaliMonthLength(1405, 6))
        assertEquals(30, CalendarConverter.jalaliMonthLength(1405, 7))
        assertEquals(30, CalendarConverter.jalaliMonthLength(1405, 8))
        // اسفند: کبیسه ۳۰ روز، عادی ۲۹ روز
        assertEquals(30, CalendarConverter.jalaliMonthLength(1403, 12))
        assertEquals(29, CalendarConverter.jalaliMonthLength(1404, 12))
        assertEquals(29, CalendarConverter.jalaliMonthLength(1405, 12))
    }

    @Test
    fun `sum of month lengths equals year length for 1300 to 1500`() {
        for (jy in 1300..1500) {
            val expected = if (CalendarConverter.isLeapJalaliYear(jy)) 366 else 365
            val sum = (1..12).sumOf { CalendarConverter.jalaliMonthLength(jy, it) }
            assertEquals("سال $jy", expected, sum)
        }
    }

    // ---------- سازگاری دورِ کامل (هزاران تاریخ) ----------

    @Test
    fun `jalali round trip via julian day for 1300 to 1500`() {
        for (jy in 1300..1500) {
            for (jm in 1..12) {
                for (jd in 1..CalendarConverter.jalaliMonthLength(jy, jm)) {
                    assertEquals(
                        "j2d/d2j برای $jy/$jm/$jd",
                        Triple(jy, jm, jd),
                        CalendarConverter.d2j(CalendarConverter.j2d(jy, jm, jd)).let {
                            Triple(it.year, it.month, it.day)
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `gregorian to jalali and back for every day of 2015 to 2035`() {
        var d = LocalDate.of(2015, 1, 1)
        val end = LocalDate.of(2035, 12, 31)
        while (!d.isAfter(end)) {
            val j = CalendarConverter.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
            val back = CalendarConverter.jalaliToGregorian(j.year, j.month, j.day)
            assertEquals("تاریخ $d", GregorianDate(d.year, d.monthValue, d.dayOfMonth), back)
            // خروجی g2j باید تاریخ جلالی معتبر باشد
            assertTrue(
                "اعتبار جلالی $d → $j",
                JalaliDate(j.year, j.month, j.day).isValid()
            )
            d = d.plusDays(1)
        }
    }

    @Test
    fun `julian day of same date from both calendars agrees`() {
        // j2d و g2d برای همان روز تاریخی باید یک شمارهٔ جولیَن بدهند
        for (jy in 1390..1430) {
            val g = CalendarConverter.jalaliToGregorian(jy, 1, 1)
            assertEquals(
                "نوروز $jy",
                CalendarConverter.g2d(g.year, g.month, g.day),
                CalendarConverter.j2d(jy, 1, 1)
            )
        }
    }

    // ---------- قمری (الگوریتم جدولی کویتی — دقت حدود یک روز) ----------

    @Test
    fun `hijri known pairs`() {
        // جفت اسکرین‌شات باد صبا: ۳۱ اکتبر ۲۰۲۶ — الگوریتم کویتی خود برنامه: ۱۹ جمادی‌الاول ۱۴۴۸
        assertEquals(HijriDate(1448, 5, 19), CalendarConverter.gregorianToHijri(2026, 10, 31))
        assertEquals(HijriDate(1448, 6, 4), CalendarConverter.gregorianToHijri(2026, 11, 15))
        assertEquals(GregorianDate(2025, 3, 30), CalendarConverter.hijriToGregorian(1446, 9, 30))
    }

    @Test
    fun `hijri round trip for sample dates`() {
        val samples = listOf(
            Triple(1448, 1, 1), Triple(1448, 5, 19), Triple(1446, 9, 30),
            Triple(1447, 12, 29), Triple(1450, 3, 15)
        )
        for (h in samples) {
            val g = CalendarConverter.hijriToGregorian(h.first, h.second, h.third)
            val back = CalendarConverter.gregorianToHijri(g.year, g.month, g.day)
            assertEquals("قمری $h", h, Triple(back.year, back.month, back.day))
        }
    }

    @Test
    fun `hijri from jalali consistency`() {
        // مسیر مستقیم و دومرحله‌ای باید یکی باشند
        for (jy in 1404..1410) {
            val direct = CalendarConverter.hijriFromJalali(jy, 7, 15)
            val g = CalendarConverter.jalaliToGregorian(jy, 7, 15)
            val viaGregorian = CalendarConverter.gregorianToHijri(g.year, g.month, g.day)
            assertEquals(
                "سال $jy",
                Triple(direct.year, direct.month, direct.day),
                Triple(viaGregorian.year, viaGregorian.month, viaGregorian.day)
            )
        }
    }
}
