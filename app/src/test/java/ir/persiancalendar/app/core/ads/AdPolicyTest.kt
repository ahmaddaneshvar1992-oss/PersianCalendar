package ir.persiancalendar.app.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های سیاست بنرهای تبلیغاتی — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 * پوشش: فیلتر تاریخ/اولویت، منطق پنهان‌سازی (dismissDays)، اعتبارسنجی URL،
 * دروازهٔ minVersionCode و کلید قطع اضطراری — هم‌زنجیر با تست‌های CI قبلی.
 */
class AdPolicyTest {

    private val now = 1_770_000_000_000L // یک لحظهٔ ثابت آزمایش
    private val day = 24L * 60 * 60 * 1000

    private fun rec(
        id: Long,
        priority: Int = 0,
        startAt: Long = 0,
        endAt: Long = 0,
        dismissDays: Int = 7,
        minVersionCode: Int = 0
    ) = AdPolicy.AdRecord(id, priority, startAt, endAt, dismissDays, minVersionCode)

    // ---------- فیلتر بر اساس تاریخ و اولویت ----------

    @Test
    fun `time window filters before start and after end`() {
        assertTrue(AdPolicy.inTimeWindow(0, 0, now))                       // بدون بازه
        assertFalse(AdPolicy.inTimeWindow(now + 1, 0, now))                // هنوز شروع نشده
        assertTrue(AdPolicy.inTimeWindow(now - day, 0, now))               // شروع گذشته
        assertFalse(AdPolicy.inTimeWindow(0, now, now))                    // endAt انقضا شده (≥)
        assertTrue(AdPolicy.inTimeWindow(0, now + 1, now))                 // هنوز نرسیده به پایان
        assertFalse(AdPolicy.inTimeWindow(now, now - 1, now))              // بازهٔ وارونه = پنهان
    }

    @Test
    fun `selects max two by priority then newest id`() {
        val ads = listOf(
            rec(id = 1, priority = 1),
            rec(id = 2, priority = 9),
            rec(id = 3, priority = 5),
            rec(id = 4, priority = 5)
        )
        val picked = AdPolicy.select(ads, { it }, now, 46, true, true, emptyMap())
        assertEquals(listOf(2L, 3L), picked.map { it.id }) // ۴ حذف: سقف ۲ — تساوی priority → id بزرگ‌تر
    }

    @Test
    fun `kill switches disable everything`() {
        val ads = listOf(rec(1), rec(2))
        assertEquals(0, AdPolicy.select(ads, { it }, now, 46, feedAdsEnabled = false, userAdsEnabled = true, dismissedUntil = emptyMap()).size)
        assertEquals(0, AdPolicy.select(ads, { it }, now, 46, feedAdsEnabled = true, userAdsEnabled = false, dismissedUntil = emptyMap()).size)
    }

    // ---------- منطق پنهان‌سازی (معیار پذیرش ۲) ----------

    @Test
    fun `dismiss days mapping - seven days, zero forever, capped`() {
        assertEquals(now + 7 * day, AdPolicy.dismissedUntilMs(7, now))
        assertEquals(Long.MAX_VALUE, AdPolicy.dismissedUntilMs(0, now))        // ۰ = برای همیشه
        assertEquals(now + AdPolicy.MAX_DISMISS_DAYS * day, AdPolicy.dismissedUntilMs(9999, now)) // سقف
    }

    @Test
    fun `dismissed ad is hidden until expiry then comes back`() {
        val ads = listOf(rec(id = 10, dismissDays = 7))
        val dismissed = mapOf(10L to now + 3 * day)
        assertEquals(0, AdPolicy.select(ads, { it }, now, 46, true, true, dismissed).size)      // هنوز پنهان
        assertEquals(1, AdPolicy.select(ads, { it }, now + 3 * day + 1, 46, true, true, dismissed).size) // پس از انقضا
    }

    @Test
    fun `new ad id shows again after permanent dismissal of old id`() {
        val ads = listOf(rec(id = 20, dismissDays = 0))
        val dismissed = mapOf(19L to Long.MAX_VALUE) // بنر قدیمی برای همیشه بسته شده
        assertEquals(1, AdPolicy.select(ads, { it }, now, 46, true, true, dismissed).size)
    }

    // ---------- دروازهٔ نسخه ----------

    @Test
    fun `minVersionCode gates old builds`() {
        val ads = listOf(rec(id = 1, minVersionCode = 50))
        assertEquals(0, AdPolicy.select(ads, { it }, now, versionCode = 46, true, true, emptyMap()).size)
        assertEquals(1, AdPolicy.select(ads, { it }, now, versionCode = 50, true, true, emptyMap()).size)
    }

    // ---------- اعتبارسنجی URL (معیار پذیرش ۳) ----------

    @Test
    fun `only https links accepted`() {
        assertTrue(AdPolicy.isSafeLink("https://agah.example.com/campaign"))
        assertTrue(AdPolicy.isSafeLink("HTTPS://Example.COM/x")) // حروف بزرگ هم اوکی
        for (bad in listOf(
            "http://insecure.example.com", "javascript:alert(1)", "intent://x#Intent",
            "file:///etc/passwd", "content://media/x", "ftp://x.com", "https://", "https:///path",
            "https://no-space example.com", "", "   ", "//cdn.example.com",
            "data:text/html;base64,xxx"
        )) {
            assertFalse("باید رد می‌شد: $bad", AdPolicy.isSafeLink(bad))
        }
    }

    @Test
    fun `image accepts https and data-image and local path only`() {
        assertTrue(AdPolicy.isSafeImage("https://cdn.example.com/banner.jpg"))
        assertTrue(AdPolicy.isSafeImage("data:image/png;base64,iVBORw0KGgo=")) // جاسازی گالری مدیر
        assertTrue(AdPolicy.isSafeImage("/data/user/0/ir.persiancalendar.app/files/ads/x.jpg"))
        for (bad in listOf(
            "http://insecure.example.com/b.jpg", "javascript:alert(1)", "file:///etc/passwd",
            "content://media/x", "data:text/html;base64,xxx", "intent://x", "", "https://"
        )) {
            assertFalse("باید رد می‌شد: $bad", AdPolicy.isSafeImage(bad))
        }
    }

    @Test
    fun `text clamps length and strips newlines`() {
        val long = "x".repeat(500)
        assertEquals(AdPolicy.MAX_TITLE, AdPolicy.clamp(long, AdPolicy.MAX_TITLE).length)
        assertEquals("a b", AdPolicy.clamp("a\nb", 100))
        assertEquals("", AdPolicy.clamp("   \n  ", 100))
    }
}
