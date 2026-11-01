package ir.persiancalendar.app.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * تست واحد ریاضی رسم «عدد روز در آیکون نوار وضعیت» — نسخهٔ ۱.۱۱.۳۵ (کار ۲)
 * معیار پذیرش ۵: عدد درشت تا حداکثر فضای دایره، برای ۱ و ۲ رقم.
 * (نقاشی واقعی android.graphics در دستگاه اجرا می‌شود؛ این‌جا منطق اندازه‌گذاری
 * با اندازه‌گیری مصنوعی قطعی تست می‌شود.)
 */
class DayIconMathTest {

    /** اندازه‌گیری مصنوعی: عرض متن = تعداد رقم × ۰٫۵۶ × سایز (رقم فارسی) */
    private val width = { text: String, size: Float -> text.length * size * 0.56f }
    private val height = { size: Float -> size * 0.72f }

    @Test
    fun `circle fills bitmap minus tiny inset`() {
        val d = DayIconMath.circleDiameter(96)
        assertEquals(96f * 0.97f, d, 0.01f)
        // دایره هرگز از بوم بیرون نمی‌زند
        assertTrue(d < 96f)
        assertTrue(d > 90f)
    }

    @Test
    fun `one digit gets bigger font than two digits`() {
        val diameter = DayIconMath.circleDiameter(96)
        val one = DayIconMath.fitFontSize(
            "۵", DayIconMath.glyphMaxWidth(diameter), DayIconMath.glyphMaxHeight(diameter), width, height
        )
        val two = DayIconMath.fitFontSize(
            "۱۷", DayIconMath.glyphMaxWidth(diameter), DayIconMath.glyphMaxHeight(diameter), width, height
        )
        assertTrue("۱ رقم باید درشت‌تر از ۲ رقم باشد: one=$one two=$two", one > two)
    }

    @Test
    fun `chosen size respects width and height budget`() {
        val diameter = DayIconMath.circleDiameter(96)
        val maxW = DayIconMath.glyphMaxWidth(diameter)
        val maxH = DayIconMath.glyphMaxHeight(diameter)
        for (digits in 1..3) {
            val text = "۵".repeat(digits)
            val size = DayIconMath.fitFontSize(text, maxW, maxH, width, height)
            assertTrue("عرض باید در بودجه باشد", width(text, size) <= maxW + 0.01f)
            assertTrue("ارتفاع باید در بودجه باشد", height(size) <= maxH + 0.01f)
            // و تا جای ممکن بزرگ (کاشفیت: بزرگ‌تر کردن کمی → نقض بودجه)
            assertTrue(size * 1.05f * 0.56f * digits > maxW * 0.97f || height(size * 1.05f) > maxH)
        }
    }

    @Test
    fun `font is nearly as big as possible for one digit`() {
        val diameter = DayIconMath.circleDiameter(96)
        val maxH = DayIconMath.glyphMaxHeight(diameter)
        val size = DayIconMath.fitFontSize(
            "۷", DayIconMath.glyphMaxWidth(diameter), maxH, width, height
        )
        // برای ۱ رقم، محدودکننده ارتفاع است → فونت باید نزدیک سقف ارتفاع باشد
        assertTrue("size=$size maxH=$maxH", abs(size - maxH / 0.72f) < maxH * 0.05f)
    }

    @Test
    fun `estimate helper consistent for 1 and 2 digits`() {
        val diameter = DayIconMath.circleDiameter(96)
        val one = DayIconMath.dayGlyphFontSize("۵", diameter)
        val two = DayIconMath.dayGlyphFontSize("۱۷", diameter)
        assertTrue(one > two)
        assertTrue(one > 0f && two > 0f)
    }

    @Test
    fun `degenerate inputs are safe`() {
        // متن خالی / بودجهٔ صفر → بدون تقسیم بر صفر و بدون کرش
        assertTrue(DayIconMath.fitFontSize("", 100f, 100f, width, height) > 0f)
        assertTrue(DayIconMath.fitFontSize("۵", 0f, 0f, width, height) > 0f)
        assertEquals(DayIconMath.circleDiameter(0), 0f)
    }

    @Test
    fun `glyph box stays inside circle chord`() {
        // جعبهٔ متن باید در باریکهٔ میانی دایره جا شود: مربع (w، h) داخل دایره است
        // اگر (w/2)² + (h/2)² ≤ (d/2)²
        val diameter = DayIconMath.circleDiameter(96)
        val w = DayIconMath.glyphMaxWidth(diameter)
        val h = DayIconMath.glyphMaxHeight(diameter)
        assertTrue((w / 2f) * (w / 2f) + (h / 2f) * (h / 2f) <= (diameter / 2f) * (diameter / 2f) + 0.01f)
    }
}
