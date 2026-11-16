package ir.persiancalendar.app.core.ads

import ir.persiancalendar.app.data.support.AdsFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست واحد اعتبارسنجی تبلیغ‌ها پیش از نمایش — نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۵)
 *
 * معیار پذیرش کاربر:
 *  - لینک کلیک فقط https و فقط scheme امن؛ intent:/javascript:/file:/content: ممنوع؛
 *  - imageUrl فقط https (یا data:image رستری جاسازی‌شده) با سقف ۵۰۰ کیلوبایت،
 *    سقف طول و سقف تعداد آیتم؛
 *  - startAt/endAt با ساعت دستگاه و خطای قابل‌قبول (±۵ دقیقه) اعتبارسنجی شود.
 */
class AdsValidationTest {

    // ---------- مقصد لمس: فقط https یا کلید داخلی ----------

    @Test
    fun `click target accepts only https`() {
        assertTrue(AdsFeedPolicy.isSafeHttpsUrl("https://example.com/promo"))
        assertTrue(AdsFeedPolicy.isSafeHttpsUrl("https://shop.example.ir/a?b=1"))
        // طرح‌های ممنوع بازرسی — هیچ‌کدام هرگز مقصد لمس نمی‌شوند
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("http://example.com/insecure"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("intent://evil#Intent;scheme=https;end"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("javascript:alert(1)"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("file:///system/etc/hosts"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("content://com.android.contacts/"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("data:text/html;base64,PHNjcmlwdD4="))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("market://details?id=x"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("//cdn.example.com/proto-relative"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl(""))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl(null))
    }

    @Test
    fun `feed images accept only https or inline raster data uri`() {
        assertTrue(AdsFeedPolicy.isSafeFeedImage("https://cdn.example.com/banner.jpg"))
        assertTrue(
            AdsFeedPolicy.isSafeFeedImage(
                "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAA"
            )
        )
        // svg و هر MIME دیگری — حتی داخل data — رد می‌شود
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:text/html;base64,PHNjcmlwdD4="))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("http://cdn.example.com/banner.jpg"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("intent://evil#Intent;end"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("javascript:alert(1)"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("file:///sdcard/x.png"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("content://media/image/1"))
    }

    @Test
    fun `url length cap and item count cap`() {
        val longUrl = "https://example.com/" + "a".repeat(3000)
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl(longUrl))
        assertTrue(AdsFeedPolicy.isSafeHttpsUrl("https://example.com/" + "a".repeat(2000 - 20)))
        assertTrue(AdsFeedPolicy.MAX_ITEMS <= 12)
        // سقف بایت تصویر = ۵۰۰ کیلوبایت (بازرسی ۱.۱۱.۴۵)
        assertTrue(AdsFeedPolicy.MAX_IMAGE_BYTES == 500_000)
    }

    @Test
    fun `insane time windows drop the whole item`() {
        val ok = 1_790_000_000_000L // ~۲۰۲۶-۰۹
        assertTrue(AdsFeedPolicy.saneAdWindow(0L, 0L))          // بدون محدودیت
        assertTrue(AdsFeedPolicy.saneAdWindow(ok, ok + 86_400_000L))
        assertTrue(AdsFeedPolicy.saneAdWindow(ok, 0L))          // فقط شروع
        // پایان پیش از آغاز
        assertFalse(AdsFeedPolicy.saneAdWindow(ok, ok - 1))
        // تاریخ آشفته: قبل از ۲۰۲۰
        assertFalse(AdsFeedPolicy.saneAdWindow(1L, 0L))
        assertFalse(AdsFeedPolicy.saneAdWindow(0L, 1_000_000L))
        // پنجرهٔ بلندتر از یک سال
        assertFalse(AdsFeedPolicy.saneAdWindow(ok, ok + AdsFeedPolicy.MAX_WINDOW_MS + 1))
    }

    @Test
    fun `parse drops item whose window is insane`() {
        val body = "{\"sequence\":9,\"items\":[" +
                "{\"id\":\"ok\",\"type\":\"tile\",\"title\":\"سالم\"," +
                "\"iconUrl\":\"https://cdn.example.com/i.png\"," +
                "\"startAt\":1790000000000,\"endAt\":1790086400000}," +
                "{\"id\":\"bad\",\"type\":\"tile\",\"title\":\"آشفته\"," +
                "\"iconUrl\":\"https://cdn.example.com/i.png\"," +
                "\"startAt\":1790000000000,\"endAt\":1780000000000}]}"
        val doc = AdsFeed.parseDoc(body)
        assertTrue(doc != null)
        assertEquals(1, doc!!.items.size)
        assertEquals("ok", doc.items[0].id)
    }

    @Test
    fun `display window tolerates small device clock skew`() {
        val start = 1_790_000_000_000L
        val end = start + 3_600_000L
        // ۴ دقیقه پیش از شروع: با رواداری ±۵ دقیقه دیده می‌شود
        assertTrue(AdsFeedPolicy.inTimeWindow(start, end, start - 4 * 60_000L, AdsFeedPolicy.SKEW_MS))
        // ۱۰ دقیقه پیش از شروع: حتی با رواداری هم پنهان است
        assertFalse(AdsFeedPolicy.inTimeWindow(start, end, start - 10 * 60_000L, AdsFeedPolicy.SKEW_MS))
        // ۴ دقیقه پس از پایان: هنوز دیده می‌شود (رواداری)
        assertTrue(AdsFeedPolicy.inTimeWindow(start, end, end + 4 * 60_000L, AdsFeedPolicy.SKEW_MS))
        // ۱۰ دقیقه پس از پایان: پنهان
        assertFalse(AdsFeedPolicy.inTimeWindow(start, end, end + 10 * 60_000L, AdsFeedPolicy.SKEW_MS))
        // بدون skew (رفتار قبلی) مرزها سخت‌گیرانه‌اند
        assertFalse(AdsFeedPolicy.inTimeWindow(start, end, start - 1, 0L))
        assertFalse(AdsFeedPolicy.inTimeWindow(start, end, end, 0L))
    }

    @Test
    fun `internal targets are whitelist-only`() {
        assertTrue(AdsFeedPolicy.isSafeInternalTarget("calendar"))
        assertTrue(AdsFeedPolicy.isSafeInternalTarget("support"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("settings://admin"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("content://x"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("CalendarScreen"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget(""))
    }
}
