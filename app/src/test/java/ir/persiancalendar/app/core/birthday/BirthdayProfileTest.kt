package ir.persiancalendar.app.core.birthday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تست واحد پروفایل تولد — نسخهٔ ۱.۱۱.۴۸ (رفع «سرریز ادیتور تولد»)
 *
 * ریشهٔ باگِ بازهٔ سال: پارسر قدیمی سالِ قبل از ۱۳۰۰ را رد می‌کرد در حالی که
 * ادیتور تولد تا ۱۱۰ سال قبل می‌دهد (برای امروزِ ۱۴۰۵ = ۱۲۹۵) — تولدِ
 * ذخیره‌شده در بازهٔ مجاز ۱۲۹۵..۱۲۹۹ در بازِ بعدی برنامه بی‌سروصدا null
 * می‌شد و کارت خاموش می‌افتاد. معیار پذیرش: هر تاریخی که ادیتور می‌دهد باید
 * پارس هم قبول کند و برعکس.
 */
class BirthdayProfileTest {

    @Test
    fun `parses stored format`() {
        assertEquals(BirthdayProfile.Birth(1375, 7, 2), BirthdayProfile.parse("1375/7/2"))
        assertEquals(BirthdayProfile.Birth(0, 12, 30), BirthdayProfile.parse("0/12/30"))
    }

    @Test
    fun `accepts full editor range - 110 years back`() {
        // کف بازهٔ ادیتور برای امروزِ ۱۴۰۵ = ۱۲۹۵ — دقیقاً همان جایی که
        // پارسرِ قدیمی می‌شکست (شهریور/اسفندِ افرادِ ۱۱۰ ساله)
        assertNotNull(BirthdayProfile.parse("1295/6/15"))
        assertNotNull(BirthdayProfile.parse("1290/1/1"))
        assertNotNull(BirthdayProfile.parse("1405/12/29"))
        assertNotNull(BirthdayProfile.validate(1295, 6, 15))
    }

    @Test
    fun `rejects below min and above max year`() {
        assertNull(BirthdayProfile.parse("1289/6/15"))
        assertNull(BirthdayProfile.parse("1501/1/1"))
        assertNull(BirthdayProfile.validate(1289, 3, 3))
    }

    @Test
    fun `zero year means no-year birthday and passes`() {
        val b = BirthdayProfile.parse("0/3/15")
        assertNotNull(b)
        assertEquals(0, b!!.year)
    }

    @Test
    fun `malformed input is never a crash - just null`() {
        assertNull(BirthdayProfile.parse(""))
        assertNull(BirthdayProfile.parse("garbage"))
        assertNull(BirthdayProfile.parse("1375/7"))
        assertNull(BirthdayProfile.parse("1375/7/2/9"))
        assertNull(BirthdayProfile.parse("abcd/7/2"))
        assertNull(BirthdayProfile.parse("1375/x/2"))
        // فاصلهٔ درون قطعات (trim فقط کل رشته است) → null
        assertNull(BirthdayProfile.parse(" 1375 / 7 / 2 "))
    }

    @Test
    fun `month and day bounds`() {
        assertNull(BirthdayProfile.parse("1375/13/1"))
        assertNull(BirthdayProfile.parse("1375/0/10"))
        assertNull(BirthdayProfile.parse("1375/7/32"))
        assertNull(BirthdayProfile.parse("1375/7/0"))
    }

    @Test
    fun `validate mirrors parse rules`() {
        assertEquals(BirthdayProfile.parse("1380/5/5"), BirthdayProfile.validate(1380, 5, 5))
        assertNull(BirthdayProfile.validate(1375, 13, 1))
        assertNull(BirthdayProfile.validate(0, 0, 15))
    }
}
