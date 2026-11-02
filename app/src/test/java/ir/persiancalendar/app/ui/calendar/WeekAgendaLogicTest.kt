package ir.persiancalendar.app.ui.calendar

import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.data.db.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * تست‌های واحد نسخهٔ ۱.۱۱.۴۰ — کارهای ۳ تا ۶
 *
 *  ۱. حالت تراکم: سطح‌بندی ۴سطحی تعداد رویدادها + شدت رنگ هر سطح
 *  ۲. نگاشت Pager هفته: صفحات ۷روزه بر پایهٔ JDN موتور تقویم،
 *     شروع هفته شنبه (پیش‌فرض) و یکشنبه، پوشش کامل بازهٔ موتور
 *  ۳. مرتب‌سازی agenda: تمام‌روزها بالای فهرست، زمان‌دارها به ترتیب ساعت
 *
 * بردارها با پورت وفادار الگوریتم در پایتون راستی‌آزمایی شده‌اند
 * (۹ آبان ۱۴۰۵ = شنبه ۳۱ اکتبر ۲۰۲۶ — مرجع رسمی کاربر).
 */
class WeekAgendaLogicTest {

    // ---------- ۱) حالت تراکم (کار ۵) ----------

    @Test
    fun `density level buckets`() {
        assertEquals(0, MonthGridLogic.densityLevel(0))
        assertEquals(1, MonthGridLogic.densityLevel(1))
        assertEquals(1, MonthGridLogic.densityLevel(2))
        assertEquals(2, MonthGridLogic.densityLevel(3))
        assertEquals(2, MonthGridLogic.densityLevel(4))
        assertEquals(3, MonthGridLogic.densityLevel(5))
        assertEquals(3, MonthGridLogic.densityLevel(50))
        // ورودی نامعتبر — بدون کرش
        assertEquals(0, MonthGridLogic.densityLevel(-3))
    }

    @Test
    fun `density alpha rises with level and clamps`() {
        assertEquals(0f, MonthGridLogic.densityAlpha(0), 1e-6f)
        assertEquals(0.08f, MonthGridLogic.densityAlpha(1), 1e-6f)
        assertEquals(0.18f, MonthGridLogic.densityAlpha(2), 1e-6f)
        assertEquals(0.30f, MonthGridLogic.densityAlpha(3), 1e-6f)
        // بیرون از بازه گیره می‌شود — بدون کرش
        assertEquals(0f, MonthGridLogic.densityAlpha(-2), 1e-6f)
        assertEquals(0.30f, MonthGridLogic.densityAlpha(9), 1e-6f)
    }

    @Test
    fun `density level is monotonic`() {
        var prev = -1
        for (n in 0..20) {
            val lvl = MonthGridLogic.densityLevel(n)
            assertTrue("level($n)=$lvl must not decrease", lvl >= prev)
            prev = lvl
        }
    }

    // ---------- ۲) نگاشت Pager هفته (کار ۴) ----------

    @Test
    fun `first page starts at engine range anchor`() {
        // ۱ فروردین ۱۱۷۸ = پنجشنبه ۲۱ مارس ۱۷۹۹ (بردار پایتونِ وفادار)
        val gStart = CalendarConverter.jalaliToGregorian(
            WeekAgendaLogic.MIN_YEAR, 1, 1
        )
        val startDate = LocalDate.of(gStart.year, gStart.month, gStart.day)
        assertEquals(LocalDate.of(1799, 3, 21), startDate)
        // صفحهٔ صفر شنبه‌محور از شنبهٔ قبل از روز اول موتور شروع می‌شود (۱۶ مارس ۱۷۹۹)
        assertEquals(LocalDate.of(1799, 3, 16), WeekAgendaLogic.daysOfPage(0, true).first())
        // با شروع یکشنبه، همان صفحه یک روز بعد (۱۷ مارس ۱۷۹۹) را نشان می‌دهد
        assertEquals(LocalDate.of(1799, 3, 17), WeekAgendaLogic.daysOfPage(0, false).first())
        // و روز اول موتور داخل صفحهٔ صفر هر دو حالت است
        val startJdn = WeekAgendaLogic.jdnOf(WeekAgendaLogic.MIN_YEAR, 1, 1)
        assertTrue(WeekAgendaLogic.isJdnInPage(startJdn, 0, true))
        assertTrue(WeekAgendaLogic.isJdnInPage(startJdn, 0, false))
    }

    @Test
    fun `page days are seven consecutive days`() {
        for (page in intArrayOf(0, 1, 500, WeekAgendaLogic.pageCount(true) - 1)) {
            val days = WeekAgendaLogic.daysOfPage(page, true)
            assertEquals(7, days.size)
            for (i in 1 until days.size) {
                assertEquals(days[i - 1].plusDays(1), days[i])
            }
        }
    }

    @Test
    fun `official vector 9 aban 1405 is saturday and maps roundtrip`() {
        // مرجع رسمی: ۳۱ اکتبر ۲۰۲۶ = ۹ آبان ۱۴۰۵ = شنبه
        assertEquals(JalaliDate(1405, 8, 9),
            CalendarConverter.gregorianToJalali(2026, 10, 31))
        val page = WeekAgendaLogic.pageOf(1405, 8, 9, true)
        val days = WeekAgendaLogic.daysOfPage(page, true)
        assertTrue(days.contains(LocalDate.of(2026, 10, 31)))
        // شنبه اولین روز هفتهٔ شنبه‌محور است
        assertEquals(LocalDate.of(2026, 10, 31), days.first())
        // رفت‌وبرگشت: JDN روز → صفحه → هفته شامل همان روز
        val jdn = WeekAgendaLogic.jdnOf(1405, 8, 9)
        assertTrue(WeekAgendaLogic.isJdnInPage(jdn, page, true))
        assertFalse(WeekAgendaLogic.isJdnInPage(jdn, page + 1, true))
        assertFalse(WeekAgendaLogic.isJdnInPage(jdn, page - 1, true))
    }

    @Test
    fun `week start sunday shifts window by one day`() {
        // ۳۱ اکتبر ۲۰۲۶ = شنبه (اندیس ۰)
        val satJdn = WeekAgendaLogic.jdnOf(1405, 8, 9)
        // شنبه‌محور: هفته از خود شنبه شروع می‌شود
        assertEquals(satJdn, WeekAgendaLogic.weekStartJdnOf(satJdn, true))
        // یکشنبه‌محور: شنبه آخرین روز هفته است → شروع = شنبه منهای ۶ (یکشنبهٔ قبل)
        assertEquals(satJdn - 6, WeekAgendaLogic.weekStartJdnOf(satJdn, false))
        // یکشنبه خودش آغازگر هفتهٔ خودش است
        val sunJdn = satJdn + 1
        assertEquals(sunJdn, WeekAgendaLogic.weekStartJdnOf(sunJdn, false))
    }

    @Test
    fun `page count covers engine end for both week starts`() {
        val lastDay = CalendarConverter.jalaliMonthLength(WeekAgendaLogic.MAX_YEAR, 12)
        val endJdn = WeekAgendaLogic.jdnOf(WeekAgendaLogic.MAX_YEAR, 12, lastDay)
        assertTrue(
            WeekAgendaLogic.isJdnInPage(endJdn, WeekAgendaLogic.pageCount(true) - 1, true)
        )
        assertTrue(
            WeekAgendaLogic.isJdnInPage(endJdn, WeekAgendaLogic.pageCount(false) - 1, false)
        )
        // آغاز موتور هم باید در صفحهٔ صفر باشد
        val startJdn = WeekAgendaLogic.jdnOf(WeekAgendaLogic.MIN_YEAR, 1, 1)
        assertTrue(WeekAgendaLogic.isJdnInPage(startJdn, 0, true))
        assertTrue(WeekAgendaLogic.isJdnInPage(startJdn, 0, false))
    }

    @Test
    fun `page mapping roundtrip for every month of 1405`() {
        for (m in 1..12) {
            val page = WeekAgendaLogic.pageOf(1405, m, 15, true)
            val days = WeekAgendaLogic.daysOfPage(page, true)
            val g15 = CalendarConverter.jalaliToGregorian(1405, m, 15)
            assertTrue(
                "1405/$m/15 must be inside its week page",
                days.contains(LocalDate.of(g15.year, g15.month, g15.day))
            )
        }
    }

    // ---------- ۳) مرتب‌سازی agenda (کار ۴) ----------

    private fun ev(
        title: String,
        hour: Int,
        minute: Int = 0,
        colorIndex: Int = 0
    ): EventEntity = EventEntity(
        title = title,
        jy = 1405, jm = 8, jd = 10,
        gy = 2026, gm = 11, gd = 1,
        hour = hour, minute = minute,
        colorIndex = colorIndex
    )

    @Test
    fun `agenda puts all day events first then timed ascending`() {
        val morning = ev("صبح", 9, 0)
        val evening = ev("شب", 21, 15)
        val noon = ev("ظهر", 12, 30)
        val allDay1 = ev("تمام‌روز الف", -1)
        val allDay2 = ev("تمام‌روز ب", -1)
        val sorted = WeekAgendaLogic.sortForAgenda(
            listOf(evening, allDay1, morning, allDay2, noon)
        )
        assertEquals(
            listOf(allDay1, allDay2, morning, noon, evening),
            sorted
        )
    }

    @Test
    fun `agenda sorts by minute within same hour`() {
        val a = ev("ده و چهل", 10, 40)
        val b = ev("ده و پنج", 10, 5)
        val sorted = WeekAgendaLogic.sortForAgenda(listOf(a, b))
        assertEquals(listOf(b, a), sorted)
    }

    @Test
    fun `agenda sort of empty and single lists is safe`() {
        assertEquals(0, WeekAgendaLogic.sortForAgenda(emptyList()).size)
        val one = listOf(ev("تنها", -1))
        assertEquals(one, WeekAgendaLogic.sortForAgenda(one))
    }
}
