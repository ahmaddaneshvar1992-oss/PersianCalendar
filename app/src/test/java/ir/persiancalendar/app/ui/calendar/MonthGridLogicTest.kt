package ir.persiancalendar.app.ui.calendar

import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * تست‌های واحد نمای ماهانهٔ بهبودیافته — نسخهٔ ۱.۱۱.۳۹
 *
 * پوشش درخواست کاربر:
 *  ۱. تبدیل تاریخ (بردارهای رسمی + روز هفته خانه‌ها)
 *  ۲. ماه‌های ۶ ردیفی (و ۵ ردیفی)
 *  ۳. اسفند کبیسه و غیرکبیسه
 *  ۴. محاسبهٔ نقطه‌های رویداد
 *  ۵. منطق نمایش دکمهٔ «امروز»
 *  + شمارهٔ هفتهٔ سال و متن TalkBack خانه‌ها
 *
 * بردارها با پورت وفادار الگوریتم در پایتون
 * (scripts/monthgrid_vectors.py — همان منطق CalendarConverter) تولید و با
 * تقویم رسمی (نوروز ۱۴۰۵ = شنبه ۲۱ مارس ۲۰۲۶ و اسفند کبیسهٔ ۱۴۰۳) سنجیده شده‌اند.
 */
class MonthGridLogicTest {

    // ---------- ۱) تبدیل تاریخ — بردارهای رسمی مبنای جدول ماهانه ----------

    @Test
    fun `known conversion vectors for grid anchors`() {
        // نوروز ۱۴۰۵ = شنبه (اولین ستون جدول)
        assertEquals(JalaliDate(1405, 1, 1), CalendarConverter.gregorianToJalali(2026, 3, 21))
        // ۳۱ اکتبر ۲۰۲۶ = ۹ آبان ۱۴۰۵ (مرجع رسمی کاربر)
        assertEquals(JalaliDate(1405, 8, 9), CalendarConverter.gregorianToJalali(2026, 10, 31))
        // اسفند کبیسهٔ ۱۴۰۳ تا ۳۰ ادامه دارد
        assertEquals(JalaliDate(1403, 12, 30), CalendarConverter.gregorianToJalali(2025, 3, 20))
    }

    @Test
    fun `weekday index of first day of month matches gregorian calendar`() {
        // اندیس ۰ = شنبه — ستون‌بندی جدول ماهانه
        // فروردین ۱۴۰۵ = ۲۱ مارس ۲۰۲۶ = شنبه
        assertEquals(0, PersianText.weekdayIndexSat0(LocalDate.of(2026, 3, 21)))
        // آبان ۱۴۰۵ = ۲۳ اکتبر ۲۰۲۶ = جمعه
        assertEquals(6, PersianText.weekdayIndexSat0(LocalDate.of(2026, 10, 23)))
        // ۹ آبان ۱۴۰۵ = شنبه (مرجع رسمی)
        assertEquals(0, PersianText.weekdayIndexSat0(LocalDate.of(2026, 10, 31)))
        // ۱۰ آبان ۱۴۰۵ = یکشنبه
        assertEquals(1, PersianText.weekdayIndexSat0(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `roundtrip jalali to gregorian and back for every month of 1405`() {
        for (m in 1..12) {
            val g = CalendarConverter.jalaliToGregorian(1405, m, 15)
            val j = CalendarConverter.gregorianToJalali(g.year, g.month, g.day)
            assertEquals("roundtrip 1405/$m/15", JalaliDate(1405, m, 15), j)
        }
    }

    // ---------- ۲) ماه‌های ۶ ردیفی و ۵ ردیفی ----------

    @Test
    fun `required rows for known months`() {
        // (طول ماه، اندیس شنبه‌ای هفتهٔ اول) → ردیف
        // خرداد ۱۴۰۵: ۳۱ روز، اولِ ماه جمعه (اندیس ۶) → ۶ ردیف
        assertEquals(6, MonthGridLogic.requiredRows(31, 6))
        // مرداد ۱۴۰۵: ۳۱ روز، اولِ ماه پنجشنبه (اندیس ۵) → ۶ ردیف
        assertEquals(6, MonthGridLogic.requiredRows(31, 5))
        // آبان ۱۴۰۵: ۳۰ روز، اولِ ماه جمعه (اندیس ۶) → ۶ ردیف
        assertEquals(6, MonthGridLogic.requiredRows(30, 6))
        // فروردین ۱۴۰۵: ۳۱ روز، اولِ ماه شنبه (اندیس ۰) → ۵ ردیف
        assertEquals(5, MonthGridLogic.requiredRows(31, 0))
        // دی ۱۴۰۵: ۳۰ روز، اندیس ۳ → ۵ ردیف
        assertEquals(5, MonthGridLogic.requiredRows(30, 3))
        // اسفند ۱۴۰۵: ۲۹ روز، اندیس ۰ → ۵ ردیف
        assertEquals(5, MonthGridLogic.requiredRows(29, 0))
        // اسفند ۱۴۰۴: ۲۹ روز، اندیس ۶ → دقیقاً ۳۵ خانه = ۵ ردیف (بدون سرریز)
        assertEquals(5, MonthGridLogic.requiredRows(29, 6))
    }

    @Test
    fun `row count of every month of 1405 from real engine`() {
        // بردارهای محاسبه‌شده با پورت وفادار موتور (scripts/monthgrid_vectors.py)
        val expectedRows = intArrayOf(5, 5, 6, 5, 6, 5, 5, 6, 5, 5, 5, 5)
        for (m in 1..12) {
            val len = CalendarConverter.jalaliMonthLength(1405, m)
            val firstG = CalendarConverter.jalaliToGregorian(1405, m, 1)
            val firstIndex = PersianText.weekdayIndexSat0(LocalDate.of(firstG.year, firstG.month, firstG.day))
            assertEquals("rows of 1405/$m", expectedRows[m - 1], MonthGridLogic.requiredRows(len, firstIndex))
        }
    }

    @Test
    fun `six row months near 1404 to 1407`() {
        // ماه‌های ۶ ردیفی واقعی این بازه — بردار پایتون
        val sixRowMonths = listOf(
            1404 to 1, 1404 to 3, 1405 to 3, 1405 to 5, 1405 to 8,
            1406 to 5, 1406 to 11, 1407 to 2, 1407 to 7
        )
        for ((jy, jm) in sixRowMonths) {
            val len = CalendarConverter.jalaliMonthLength(jy, jm)
            val firstG = CalendarConverter.jalaliToGregorian(jy, jm, 1)
            val firstIndex = PersianText.weekdayIndexSat0(LocalDate.of(firstG.year, firstG.month, firstG.day))
            assertEquals("$jy/$jm باید ۶ ردیفی باشد", 6, MonthGridLogic.requiredRows(len, firstIndex))
        }
    }

    @Test
    fun `fixed row count stays six so page height never jumps`() {
        assertEquals(6, MonthGridLogic.FIXED_ROW_COUNT)
        // حتی کوتاه‌ترین ماه هم در ۶ ردیف ثابت جا می‌گیرد
        assertTrue(MonthGridLogic.FIXED_ROW_COUNT >= MonthGridLogic.requiredRows(29, 0))
    }

    // ---------- ۳) اسفند کبیسه و غیرکبیسه ----------

    @Test
    fun `esfand length in leap and non leap years`() {
        // کبیسه‌های رسمی نزدیک: ۱۴۰۳ و ۱۴۰۸ — اسفند ۳۰ روز
        assertTrue(CalendarConverter.isLeapJalaliYear(1403))
        assertEquals(30, CalendarConverter.jalaliMonthLength(1403, 12))
        assertTrue(CalendarConverter.isLeapJalaliYear(1408))
        assertEquals(30, CalendarConverter.jalaliMonthLength(1408, 12))
        // سال‌های غیرکبیسه — اسفند ۲۹ روز
        for (y in intArrayOf(1404, 1405, 1406, 1407)) {
            assertFalse(CalendarConverter.isLeapJalaliYear(y))
            assertEquals("اسفند $y", 29, CalendarConverter.jalaliMonthLength(y, 12))
        }
    }

    @Test
    fun `esfand 30 of leap year 1403 exists and esfand 30 of 1405 does not`() {
        // ۳۰ اسفند ۱۴۰۳ = ۲۰ مارس ۲۰۲۵ (تقویم رسمی)
        assertEquals(Triple(2025, 3, 20), toTriple(CalendarConverter.jalaliToGregorian(1403, 12, 30)))
        // آخرین روز اسفند ۱۴۰۵ (غیرکبیسه) = ۲۹ = ۲۰ مارس ۲۰۲۷
        // (۲۰ مارس ۲۰۲۶ آخرین روز سال ۱۴۰۴ است — ۲۹ اسفند ۱۴۰۴)
        assertEquals(Triple(2027, 3, 20), toTriple(CalendarConverter.jalaliToGregorian(1405, 12, 29)))
    }

    @Test
    fun `esfand month grid rows in leap and non leap years`() {
        // ۱۴۰۳/۱۲: ۳۰ روز، اول ماه سه‌شنبه (اندیس ۴) → ۳۴ خانه → ۵ ردیف
        assertEquals(5, MonthGridLogic.requiredRows(30, 4))
        // ۱۴۰۸/۱۲: ۳۰ روز، اول ماه چهارشنبه (اندیس ۳) → ۳۳ خانه → ۵ ردیف
        assertEquals(5, MonthGridLogic.requiredRows(30, 3))
        // ۱۴۰۴/۱۲: ۲۹ روز، اول ماه جمعه (اندیس ۶) → ۳۵ خانه → ۵ ردیف (دقیقاً پر)
        assertEquals(5, MonthGridLogic.requiredRows(29, 6))
    }

    // ---------- ۴) محاسبهٔ نقطه‌های رویداد ----------

    @Test
    fun `event dot plan for up to three colors`() {
        assertEquals(MonthGridLogic.EventDotPlan(0, false, false), MonthGridLogic.eventDotPlan(0))
        assertEquals(MonthGridLogic.EventDotPlan(1, false, false), MonthGridLogic.eventDotPlan(1))
        assertEquals(MonthGridLogic.EventDotPlan(2, false, false), MonthGridLogic.eventDotPlan(2))
        assertEquals(MonthGridLogic.EventDotPlan(3, false, false), MonthGridLogic.eventDotPlan(3))
    }

    @Test
    fun `event dot plan dims third dot beyond three colors`() {
        val plan = MonthGridLogic.eventDotPlan(4)
        assertEquals(3, plan.dotCount)
        assertTrue(plan.lastDotDimmed)
        // ازدحام بیشتر هم همان رفتار را دارد
        val plan6 = MonthGridLogic.eventDotPlan(6)
        assertEquals(3, plan6.dotCount)
        assertTrue(plan6.lastDotDimmed)
        // ورودی نامعتبر منفی → بدون نقطه و بدون کرش
        assertEquals(MonthGridLogic.EventDotPlan(0, false, false), MonthGridLogic.eventDotPlan(-2))
    }

    @Test
    fun `max event dots constant`() {
        assertEquals(3, MonthGridLogic.MAX_EVENT_DOTS)
    }

    // ---------- ۵) منطق نمایش دکمهٔ «امروز» ----------

    @Test
    fun `today fab hidden inside current month`() {
        assertFalse(MonthGridLogic.isAwayFromCurrentMonth(1405, 8, 1405, 8))
        assertFalse(MonthGridLogic.isAwayFromCurrentMonth(1405, 1, 1405, 1))
    }

    @Test
    fun `today fab visible when month or year differs`() {
        // ماه بعد/قبل در همان سال
        assertTrue(MonthGridLogic.isAwayFromCurrentMonth(1405, 9, 1405, 8))
        assertTrue(MonthGridLogic.isAwayFromCurrentMonth(1405, 7, 1405, 8))
        // گذر از مرز سال — اسفند ۱۴۰۵ در برابر فروردین ۱۴۰۶
        assertTrue(MonthGridLogic.isAwayFromCurrentMonth(1405, 12, 1406, 1))
        // سال کاملاً متفاوت
        assertTrue(MonthGridLogic.isAwayFromCurrentMonth(1406, 8, 1405, 8))
        assertTrue(MonthGridLogic.isAwayFromCurrentMonth(1178, 1, 1405, 8))
    }

    // ---------- شمارهٔ هفتهٔ سال ----------

    @Test
    fun `week numbers per row from real engine`() {
        // فروردین ۱۴۰۵ (اول سال، شنبه‌شروع): ردیف‌ها = هفتهٔ ۱ تا ۶
        for (row in 0..5) {
            assertEquals(row + 1, MonthGridLogic.weekNumber(1405, 1, 0, row))
        }
        // آبان ۱۴۰۵ (اندیس ۶): هفته‌های ۳۱ تا ۳۶
        assertEquals(31, MonthGridLogic.weekNumber(1405, 8, 6, 0))
        assertEquals(36, MonthGridLogic.weekNumber(1405, 8, 6, 5))
        // اسفند ۱۴۰۵ (اندیس ۰): هفته‌های ۴۹ تا ۵۴
        assertEquals(49, MonthGridLogic.weekNumber(1405, 12, 0, 0))
        assertEquals(54, MonthGridLogic.weekNumber(1405, 12, 0, 5))
        // اسفند کبیسهٔ ۱۴۰۳ (اندیس ۴): از هفتهٔ ۴۸
        assertEquals(48, MonthGridLogic.weekNumber(1403, 12, 4, 0))
        assertEquals(53, MonthGridLogic.weekNumber(1403, 12, 4, 5))
    }

    // ---------- متن TalkBack خانه‌ها ----------

    @Test
    fun `day content description matches acceptance example`() {
        val cd = MonthGridLogic.dayContentDescription(
            monthName = "آبان", day = 10, weekdayName = "یکشنبه",
            isHoliday = true, isFriday = false, isToday = false, isSelected = false,
            eventCount = 2
        )
        assertEquals("آبان، ۱۰، یکشنبه، تعطیل رسمی، ۲ رویداد", cd)
    }

    @Test
    fun `day content description with today marker`() {
        val cd = MonthGridLogic.dayContentDescription(
            monthName = "آبان", day = 13, weekdayName = "چهارشنبه",
            isHoliday = false, isFriday = false, isToday = true, isSelected = false,
            eventCount = 0
        )
        assertEquals("آبان، ۱۳، چهارشنبه، امروز، بدون رویداد", cd)
    }

    @Test
    fun `day content description friday selected gregorian hijri`() {
        val cd = MonthGridLogic.dayContentDescription(
            monthName = "آبان", day = 1, weekdayName = "جمعه",
            isHoliday = false, isFriday = true, isToday = false, isSelected = true,
            eventCount = 1,
            gregorianLabel = "۲۳ اکتبر", hijriLabel = "۱۲ ربیع‌الاول"
        )
        assertEquals(
            "آبان، ۱، جمعه، روز انتخاب‌شده، میلادی ۲۳ اکتبر، قمری ۱۲ ربیع‌الاول، ۱ رویداد",
            cd
        )
    }

    // ---------- جای‌گذاری خانه‌ها — رگرسیون ۱.۱۱.۴۴ (باگ «۱۵ آبان روی شنبه») ----------

    @Test
    fun `aban 1405 grid places day 1 on friday column`() {
        // آبان ۱۴۰۵: روز ۱ = جمعه (اندیس ۶)، ۳۰ روز — گزارش کاربر ۱۴۰۵/۰۸/۱۵
        val len = CalendarConverter.jalaliMonthLength(1405, 8)
        assertEquals(30, len)
        // خانه‌های ۰ تا ۵ (شنبه تا پنجشنبهٔ ردیف ۱) خالی‌اند
        for (idx in 0..5) {
            assertNull("خانهٔ $idx باید خالی باشد", MonthGridLogic.dayOffsetForCellIndex(idx, 6, len))
        }
        // روز ۱ دقیقاً زیر ستون جمعه (اندیس ۶)
        assertEquals(0, MonthGridLogic.dayOffsetForCellIndex(6, 6, len))
        // ۱۵ آبان زیر ستون جمعهٔ ردیف ۳ (خانهٔ ۲۰)
        assertEquals(14, MonthGridLogic.dayOffsetForCellIndex(20, 6, len))
        // ۳۰ آبان در ردیف ۵ ستون شنبه (خانهٔ ۳۵)
        assertEquals(29, MonthGridLogic.dayOffsetForCellIndex(35, 6, len))
        // خانهٔ ۳۶ (دومین ستون ردیف ۶) و بعدش خالی
        assertNull(MonthGridLogic.dayOffsetForCellIndex(36, 6, len))
        assertNull(MonthGridLogic.dayOffsetForCellIndex(41, 6, len))
    }

    @Test
    fun `cell column always matches weekday for every month 1400 to 1410`() {
        // رگرسیون باگ «۱۵ آبان روی شنبه»: برای همهٔ ماه‌های ۱۴۰۰..۱۴۱۰،
        // ستون هر خانهٔ پر باید با روز هفتهٔ واقعی همان روز برابر باشد.
        // با نگاشت قدیمی (idx بدون offset) این تست برای هر ماه غیر شنبه‌شروع می‌شکست.
        for (jy in 1400..1410) {
            for (jm in 1..12) {
                val len = CalendarConverter.jalaliMonthLength(jy, jm)
                val firstG = CalendarConverter.jalaliToGregorian(jy, jm, 1)
                val firstDate = LocalDate.of(firstG.year, firstG.month, firstG.day)
                val firstIndex = PersianText.weekdayIndexSat0(firstDate)
                for (idx in 0 until 42) {
                    val offset = MonthGridLogic.dayOffsetForCellIndex(idx, firstIndex, len) ?: continue
                    val g = firstDate.plusDays(offset.toLong())
                    assertEquals(
                        "$jy/$jm خانهٔ $idx باید در ستون روز هفتهٔ خودش باشد",
                        idx % 7,
                        PersianText.weekdayIndexSat0(g)
                    )
                }
            }
        }
    }

    @Test
    fun `day offset rejects invalid inputs without crash`() {
        assertNull(MonthGridLogic.dayOffsetForCellIndex(0, 6, 0))
        assertNull(MonthGridLogic.dayOffsetForCellIndex(0, 6, -3))
        // آخرین خانهٔ پر آبان ۳۰روزه/جمعه‌شروع = ۳۵؛ خانهٔ ۴۰ و بعدش خالی
        assertNull(MonthGridLogic.dayOffsetForCellIndex(40, 6, 30))
        // اندیس روز هفتهٔ خارج بازهٔ ۰..۶ هم بی‌خطر مهار می‌شود (۹→۶)
        assertNull(MonthGridLogic.dayOffsetForCellIndex(5, 9, 30))
        assertEquals(0, MonthGridLogic.dayOffsetForCellIndex(6, 9, 30))
        assertEquals(1, MonthGridLogic.dayOffsetForCellIndex(7, 7, 30))
    }

    // ---------- ابزار کمکی مقایسهٔ میلادی ----------

    private fun toTriple(g: ir.persiancalendar.app.core.calendar.GregorianDate) =
        Triple(g.year, g.month, g.day)
}
