package ir.persiancalendar.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد انتخابگر سریع سال و ماه — نسخهٔ ۱.۱۱.۳۹
 *
 * پوشش درخواست کاربر:
 *  - بازهٔ سال‌ها از موتور تقویم (۱۱۷۸ تا ۱۴۳۴) و غیرفعالی بیرون از آن
 *  - ورودی تایپی نامعتبر هرگز کرش نمی‌کند (ارقام فارسی/عربی/لاتین + خطا)
 *  - نگاشت دوسویه صفحهٔ Pager ↔ (سال، ماه) برای انیمیشن انتخاب ماه
 *  - فلش‌های سال/ماه در لبه‌های بازه غیرفعال می‌شوند
 */
class MonthYearPickerLogicTest {

    // ---------- بازهٔ موتور تقویم ----------

    @Test
    fun `supported range comes from calendar engine`() {
        assertEquals(1178, MonthYearPickerLogic.MIN_YEAR)
        assertEquals(1434, MonthYearPickerLogic.MAX_YEAR)
        assertEquals(
            1178, ir.persiancalendar.app.core.calendar.CalendarConverter.MIN_SUPPORTED_JALALI_YEAR
        )
        assertEquals(
            1434, ir.persiancalendar.app.core.calendar.CalendarConverter.MAX_SUPPORTED_JALALI_YEAR
        )
        // ۲۵۷ سال × ۱۲ ماه
        assertEquals(3084, MonthYearPickerLogic.PAGE_COUNT)
    }

    @Test
    fun `year in range check`() {
        assertTrue(MonthYearPickerLogic.isYearInRange(1178))
        assertTrue(MonthYearPickerLogic.isYearInRange(1405))
        assertTrue(MonthYearPickerLogic.isYearInRange(1434))
        assertFalse(MonthYearPickerLogic.isYearInRange(1177))
        assertFalse(MonthYearPickerLogic.isYearInRange(1435))
        assertFalse(MonthYearPickerLogic.isYearInRange(-5))
        assertFalse(MonthYearPickerLogic.isYearInRange(9999))
    }

    @Test
    fun `clamp year never crashes on absurd input`() {
        assertEquals(1178, MonthYearPickerLogic.clampYear(-1000))
        assertEquals(1178, MonthYearPickerLogic.clampYear(1177))
        assertEquals(1405, MonthYearPickerLogic.clampYear(1405))
        assertEquals(1434, MonthYearPickerLogic.clampYear(9999))
        assertEquals(1434, MonthYearPickerLogic.clampYear(Int.MAX_VALUE))
        assertEquals(1178, MonthYearPickerLogic.clampYear(Int.MIN_VALUE))
    }

    // ---------- نگاشت Pager ↔ (سال، ماه) ----------

    @Test
    fun `page of first month is zero`() {
        assertEquals(0, MonthYearPickerLogic.pageOf(1178, 1))
        assertEquals(11, MonthYearPickerLogic.pageOf(1178, 12))
        assertEquals(12, MonthYearPickerLogic.pageOf(1179, 1))
    }

    @Test
    fun `page of current month 1405-8`() {
        // (1405-1178) × ۱۲ + ۷
        assertEquals(227 * 12 + 7, MonthYearPickerLogic.pageOf(1405, 8))
        assertEquals(2731, MonthYearPickerLogic.pageOf(1405, 8))
    }

    @Test
    fun `last page is 1434-12`() {
        assertEquals(MonthYearPickerLogic.PAGE_COUNT - 1, MonthYearPickerLogic.pageOf(1434, 12))
        assertEquals(3083, MonthYearPickerLogic.pageOf(1434, 12))
    }

    @Test
    fun `page mapping roundtrip across sampled months`() {
        val samples = listOf(
            1178 to 1, 1178 to 12, 1179 to 1, 1400 to 6, 1405 to 8,
            1405 to 12, 1406 to 1, 1408 to 12, 1434 to 1, 1434 to 12
        )
        for ((jy, jm) in samples) {
            val page = MonthYearPickerLogic.pageOf(jy, jm)
            assertEquals(jy, MonthYearPickerLogic.yearOfPage(page))
            assertEquals(jm, MonthYearPickerLogic.monthOfPage(page))
        }
    }

    @Test
    fun `page mapping clamps out of range input without crash`() {
        // سال بیرون از بازه به لبه گیره می‌شود
        assertEquals(0, MonthYearPickerLogic.pageOf(0, 1))
        assertEquals(MonthYearPickerLogic.PAGE_COUNT - 1, MonthYearPickerLogic.pageOf(9999, 12))
        // ماه بیرون از ۱..۱۲ هم گیره می‌شود
        assertEquals(0, MonthYearPickerLogic.pageOf(1178, -3))
        assertEquals(11, MonthYearPickerLogic.pageOf(1178, 99))
        // صفحات بیرون از بازه برای خواندن هم امن‌اند
        assertEquals(1178, MonthYearPickerLogic.yearOfPage(-100))
        assertEquals(1434, MonthYearPickerLogic.yearOfPage(999_999))
        assertEquals(1, MonthYearPickerLogic.monthOfPage(-100))
        assertEquals(12, MonthYearPickerLogic.monthOfPage(999_999))
    }

    @Test
    fun `offset between months for animated jump`() {
        assertEquals(0, MonthYearPickerLogic.offsetBetweenMonths(1405, 8, 1405, 8))
        assertEquals(1, MonthYearPickerLogic.offsetBetweenMonths(1405, 8, 1405, 9))
        assertEquals(-1, MonthYearPickerLogic.offsetBetweenMonths(1405, 8, 1405, 7))
        // گذر از مرز سال
        assertEquals(1, MonthYearPickerLogic.offsetBetweenMonths(1405, 12, 1406, 1))
        assertEquals(-1, MonthYearPickerLogic.offsetBetweenMonths(1406, 1, 1405, 12))
        // پرش بزرگ انتخابگر (مثلاً از ۱۴۰۵/۸ به ۱۳۹۰/۳)
        assertEquals(-185, MonthYearPickerLogic.offsetBetweenMonths(1405, 8, 1390, 3))
    }

    // ---------- پارس ورودی تایپی سال — بدون کرش ----------

    @Test
    fun `parse year input accepts persian digits`() {
        assertEquals(1405, MonthYearPickerLogic.parseYearInput("۱۴۰۵"))
        assertEquals(1178, MonthYearPickerLogic.parseYearInput("۱۱۷۸"))
        assertEquals(1434, MonthYearPickerLogic.parseYearInput("۱۴۳۴"))
    }

    @Test
    fun `parse year input accepts latin and arabic digits and separators`() {
        assertEquals(1405, MonthYearPickerLogic.parseYearInput("1405"))
        assertEquals(1405, MonthYearPickerLogic.parseYearInput("١٤٠٥")) // ارقام عربی
        assertEquals(1405, MonthYearPickerLogic.parseYearInput(" ۱۴۰۵ "))
        assertEquals(1405, MonthYearPickerLogic.parseYearInput("۱٬۴۰۵"))
        assertEquals(1405, MonthYearPickerLogic.parseYearInput("1,405"))
    }

    @Test
    fun `parse year input rejects invalid input with null`() {
        assertNull(MonthYearPickerLogic.parseYearInput(""))
        assertNull(MonthYearPickerLogic.parseYearInput("   "))
        assertNull(MonthYearPickerLogic.parseYearInput("۱۴۰۵/"))
        assertNull(MonthYearPickerLogic.parseYearInput("سال ۱۴۰۵"))
        assertNull(MonthYearPickerLogic.parseYearInput("abc"))
        assertNull(MonthYearPickerLogic.parseYearInput("-۵"))
        assertNull(MonthYearPickerLogic.parseYearInput("۱۴۰۵۶")) // بیش از ۴ رقم
        assertNull(MonthYearPickerLogic.parseYearInput("۱۴۰.۵"))
        assertNull(MonthYearPickerLogic.parseYearInput("٥٥٥٥٥٥٥٥"))
    }

    // ---------- فلش‌های سال و ماه در لبه‌های بازه ----------

    @Test
    fun `year arrows disabled at bounds`() {
        assertFalse(MonthYearPickerLogic.canGoToPrevYear(1178))
        assertTrue(MonthYearPickerLogic.canGoToPrevYear(1179))
        assertTrue(MonthYearPickerLogic.canGoToPrevYear(1405))
        assertFalse(MonthYearPickerLogic.canGoToNextYear(1434))
        assertTrue(MonthYearPickerLogic.canGoToNextYear(1433))
        assertTrue(MonthYearPickerLogic.canGoToNextYear(1405))
    }

    @Test
    fun `month arrows disabled only at very first and very last month`() {
        assertFalse(MonthYearPickerLogic.canGoToPrevMonth(1178, 1))
        assertTrue(MonthYearPickerLogic.canGoToPrevMonth(1178, 2))
        assertTrue(MonthYearPickerLogic.canGoToPrevMonth(1179, 1))
        assertFalse(MonthYearPickerLogic.canGoToNextMonth(1434, 12))
        assertTrue(MonthYearPickerLogic.canGoToNextMonth(1434, 11))
        assertTrue(MonthYearPickerLogic.canGoToNextMonth(1433, 12))
        // حالت عادی
        assertTrue(MonthYearPickerLogic.canGoToPrevMonth(1405, 8))
        assertTrue(MonthYearPickerLogic.canGoToNextMonth(1405, 8))
    }

    @Test
    fun `current month detection for highlight and today marker`() {
        assertTrue(MonthYearPickerLogic.isCurrentMonth(1405, 8, 1405, 8))
        assertFalse(MonthYearPickerLogic.isCurrentMonth(1405, 8, 1405, 9))
        assertFalse(MonthYearPickerLogic.isCurrentMonth(1406, 8, 1405, 8))
    }
}
