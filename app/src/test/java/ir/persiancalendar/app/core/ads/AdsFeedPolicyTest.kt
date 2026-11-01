package ir.persiancalendar.app.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست واحد سیاست تبلیغات مدیریت‌شده — نسخهٔ ۱.۱۱.۳۵ (کار ۱)
 * پوشش معیارهای پذیرش ۲ و ۳: ضد rollback، فیلتر زمانی، لیست‌سفید داخلی و
 * اعتبارسنجی URL (فقط https).
 */
class AdsFeedPolicyTest {

    // ---------- ضدمقاومت بازپخش (rollback) ----------

    @Test
    fun `sequence greater than stored is accepted`() {
        assertTrue(AdsFeedPolicy.sequenceAccepts(stored = 4L, incoming = 5L))
        assertTrue(AdsFeedPolicy.sequenceAccepts(stored = 0L, incoming = 1L))
        assertTrue(AdsFeedPolicy.sequenceAccepts(stored = 999L, incoming = 100_000L))
    }

    @Test
    fun `sequence equal or smaller is rejected (anti-rollback)`() {
        // معیار ۲: امضای قدیمی فید حذف‌شده نباید آیتم را برگرداند
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5L, incoming = 5L))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5L, incoming = 4L))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5L, incoming = 1L))
    }

    @Test
    fun `sequence zero or negative is never accepted`() {
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 0L, incoming = 0L))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 0L, incoming = -3L))
    }

    // ---------- گارد اولین پذیرش (نصب تازه) ----------

    @Test
    fun `first accept rejects feed older than 90 days`() {
        val now = 1_800_000_000_000L
        val fresh = now - 1L * 24 * 60 * 60 * 1000
        val old = now - 91L * 24 * 60 * 60 * 1000
        assertTrue(AdsFeedPolicy.firstAcceptFreshEnough(fresh, now))
        assertFalse(AdsFeedPolicy.firstAcceptFreshEnough(old, now))
        // issuedAt نامعلوم → سخت‌گیرانه رد
        assertFalse(AdsFeedPolicy.firstAcceptFreshEnough(0L, now))
        // تاریخ از آینده → رد
        assertFalse(AdsFeedPolicy.firstAcceptFreshEnough(now + 1000, now))
    }

    // ---------- تازگی کش (maxCacheDays) ----------

    @Test
    fun `cache freshness respects maxCacheDays`() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        // پیش‌فرض ۷ روز
        assertTrue(AdsFeedPolicy.cacheIsFresh(now - 6 * day, now, 7))
        assertFalse(AdsFeedPolicy.cacheIsFresh(now - 8 * day, now, 7))
        // ۰ = کش بی‌اعتبار
        assertFalse(AdsFeedPolicy.cacheIsFresh(now, now, 0))
        // سقف ۳۰ روز: مقدار بزرگ‌تر clamp می‌شود
        assertTrue(AdsFeedPolicy.cacheIsFresh(now - 10 * day, now, 999))
        // fetchedAt نامعلوم
        assertFalse(AdsFeedPolicy.cacheIsFresh(0L, now, 7))
    }

    // ---------- فیلتر زمانی آیتم‌ها ----------

    @Test
    fun `time window filtering`() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        // بدون بازه = همیشه مجاز
        assertTrue(AdsFeedPolicy.inTimeWindow(0L, 0L, now))
        // شروع در آینده → پنهان
        assertFalse(AdsFeedPolicy.inTimeWindow(now + day, 0L, now))
        // پایان گذشته → پنهان
        assertFalse(AdsFeedPolicy.inTimeWindow(0L, now - day, now))
        // داخل بازه → مجاز
        assertTrue(AdsFeedPolicy.inTimeWindow(now - day, now + day, now))
        // بازهٔ نامعتبر (پایان ≤ شروع) → پنهان
        assertFalse(AdsFeedPolicy.inTimeWindow(now, now, now))
    }

    @Test
    fun `eligibility respects enabled minVersionCode and feed kill switch`() {
        val now = 1_800_000_000_000L
        val rec = AdsFeedPolicy.ItemRecord(
            id = "t1", type = "tile", enabled = true, order = 1,
            startAt = 0L, endAt = 0L, minVersionCode = 48
        )
        // minVersionCode بالاتر از نسخهٔ کاربر → پنهان
        assertFalse(AdsFeedPolicy.isEligible(rec, now, versionCode = 47, feedAdsEnabled = true))
        // نسخهٔ کافی → نمایش
        assertTrue(AdsFeedPolicy.isEligible(rec, now, versionCode = 48, feedAdsEnabled = true))
        // آیتم خاموش فید → پنهان
        assertFalse(
            AdsFeedPolicy.isEligible(rec.copy(enabled = false), now, 48, feedAdsEnabled = true)
        )
        // کلید قطع سراسری فید → همه پنهان
        assertFalse(AdsFeedPolicy.isEligible(rec, now, 48, feedAdsEnabled = false))
    }

    @Test
    fun `selection caps count to max`() {
        val now = 1_800_000_000_000L
        data class R(val id: String, val order: Int)
        val items = (1..20).map { R("i$it", it) }
        val out = AdsFeedPolicy.select(
            items = items,
            record = { AdsFeedPolicy.ItemRecord(it.id, "tile", true, it.order) },
            nowMs = now,
            versionCode = 47,
            feedAdsEnabled = true,
            max = 5
        )
        assertEquals(5, out.size)
        assertEquals((1..5).map { "i$it" }, out.map { it.id })
    }

    @Test
    fun `selection orders by order field then id`() {
        val now = 1_800_000_000_000L
        data class R(val id: String, val order: Int)
        val items = listOf(R("x3", 3), R("x1", 1), R("x2b", 2), R("x2a", 2))
        val out = AdsFeedPolicy.select(
            items = items,
            record = { AdsFeedPolicy.ItemRecord(it.id, "tile", true, it.order) },
            nowMs = now,
            versionCode = 47,
            feedAdsEnabled = true
        )
        assertEquals(listOf("x1", "x2a", "x2b", "x3"), out.map { it.id })
    }

    // ---------- لیست‌سفید مقصدهای داخلی ----------

    @Test
    fun `internal targets whitelist only`() {
        // کلیدهای مجاز — از جمله دو نمونهٔ کاربر
        assertTrue(AdsFeedPolicy.isSafeInternalTarget("reminders"))
        assertTrue(AdsFeedPolicy.isSafeInternalTarget("calendar"))
        assertTrue(AdsFeedPolicy.isSafeInternalTarget("announcements"))
        // هر چیز دیگری — حتی شبیه کلید — رد می‌شود (هیچ intent خامی از فید اجرا نمی‌شود)
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("unknown_screen"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("reminders;rm -rf"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("https://evil.example.com"))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget(""))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget(null))
        assertFalse(AdsFeedPolicy.isSafeInternalTarget("reminders\njavascript:alert(1)"))
    }

    // ---------- اعتبارسنجی URL (فقط https) ----------

    @Test
    fun `only https urls accepted`() {
        assertTrue(AdsFeedPolicy.isSafeHttpsUrl("https://cdn.example.com/a.png"))
        assertTrue(AdsFeedPolicy.isSafeHttpsUrl("HTTPS://EXAMPLE.com/x"))
        // طرح‌های ممنوع (معیار ۳)
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("http://cdn.example.com/a.png"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("javascript:alert(1)"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("intent://x#Intent;end"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("file:///data/evil"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("content://x"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("data:image/png;base64,AAAA"))
        // شکل‌های دور زدن
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("https://"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("https:///path"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("https://evil example.com/x"))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl(null))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl(""))
        assertFalse(AdsFeedPolicy.isSafeHttpsUrl("https://" + "a".repeat(3000)))
    }

    // ---------- تصویر درون‌خطی فید (نسخهٔ ۱.۱۱.۳۶) ----------

    @Test
    fun `feed image accepts https and strict raster data uris`() {
        assertTrue(AdsFeedPolicy.isSafeFeedImage("https://cdn.example.com/a.png"))
        assertTrue(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg;base64,/9j/4AAQ"))
        assertTrue(AdsFeedPolicy.isSafeFeedImage("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(AdsFeedPolicy.isSafeFeedImage("data:image/webp;base64,UklGRg=="))
    }

    @Test
    fun `feed image rejects svg other mimes whitespace and oversize`() {
        // svg می‌تواند اسکریپت داشته باشد — هرگز
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/svg+xml;base64,PHN2Zz4="))
        // MIME دیگر و شکل‌های جعلی
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:text/html;base64,PGI+"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg,raw-not-base64"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg;utf8,abc"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("DATA:IMAGE/JPEG;BASE64,AAAA")) // فقط حروف کوچک
        // فاصله در base64 (wrap) رد می‌شود — هم‌قرارداد با ابزار پایتونی
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg;base64,AAAA BBBB"))
        // سر‌خود یا فقط پیشوند
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg;base64,"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("data:image/jpeg;base64"))
        // طرح‌های ممنوعِ قبلی همچنان رد
        assertFalse(AdsFeedPolicy.isSafeFeedImage("http://a.co/i.png"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("javascript:alert(1)"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage("file:///x.jpg"))
        assertFalse(AdsFeedPolicy.isSafeFeedImage(""))
        assertFalse(AdsFeedPolicy.isSafeFeedImage(null))
        // سقف کاراکتر
        val huge = "data:image/jpeg;base64," + "A".repeat(AdsFeedPolicy.MAX_DATAURI_CHARS)
        assertFalse(AdsFeedPolicy.isSafeFeedImage(huge))
    }

    // ---------- clamp متن ----------

    @Test
    fun `text clamping strips newlines and caps length`() {
        assertEquals("سلام دنیا", AdsFeedPolicy.clamp("  سلام\nدنیا\r\n  ", 40))
        assertEquals(40, AdsFeedPolicy.clamp("ع".repeat(100), 40).length)
        assertEquals("", AdsFeedPolicy.clamp("\n\r", 10))
    }

    // ---------- هندسهٔ بنر — نسخهٔ ۱.۱۱.۳۷ (گزارش کاربر: «بنر مشکل داره») ----------

    @Test
    fun `classic 3x1 banner renders exactly like before`() {
        // بنر عریض کلاسیک در پهنای ۳۶۰dp → ارتفاع طبیعی ۱۲۰dp داخل باند است
        // → جعبه دقیقاً ۱۲۰dp و پرشدن لبه‌به‌لبه (رفتار قدیمی حفظ می‌شود)
        val h = AdsFeedPolicy.bannerHeightDp(1200, 400, boxWidthDp = 360f)
        assertEquals(120f, h, 0.001f)
        assertTrue(AdsFeedPolicy.bannerFillsExactly(1200, 400, 360f))
    }

    @Test
    fun `portrait logo is never cropped - height caps at max and fits`() {
        // ابعاد واقعی لوگوی مدیر (۷۸۴×۱۱۶۸) در پهنای ۳۶۰dp → طبیعی ≈ ۵۳۶dp
        // → جعبه روی سقف ۲۸۰dp و تصویر Fit — کل تصویر دیده می‌شود، هیچ برشی نیست
        val h = AdsFeedPolicy.bannerHeightDp(784, 1168, boxWidthDp = 360f)
        assertEquals(280f, h, 0.001f)
        assertFalse(AdsFeedPolicy.bannerFillsExactly(784, 1168, 360f))
    }

    @Test
    fun `ultra wide banner height floors at min`() {
        // تصویر ۵:۱ در پهنای ۳۶۰dp → طبیعی ۷۲dp زیر کف → جعبه ۱۱۰dp و Fit
        val h = AdsFeedPolicy.bannerHeightDp(1800, 360, boxWidthDp = 360f)
        assertEquals(110f, h, 0.001f)
        assertFalse(AdsFeedPolicy.bannerFillsExactly(1800, 360, 360f))
    }

    @Test
    fun `square image height equals width and fills`() {
        // مربع ۸۰۰×۸۰۰ در پهنای ۳۶۰dp → ۳۶۰dp بالای سقف → ۲۸۰dp و Fit
        val h = AdsFeedPolicy.bannerHeightDp(800, 800, boxWidthDp = 360f)
        assertEquals(280f, h, 0.001f)
        // اما مربع کوچک‌نمایی‌شده داخل باند دقیقاً پر می‌کند: ۷۸۴px در پهنای ۲۸۰dp
        val h2 = AdsFeedPolicy.bannerHeightDp(784, 784, boxWidthDp = 280f)
        assertEquals(280f, h2, 0.001f)
        assertTrue(AdsFeedPolicy.bannerFillsExactly(784, 784, 280f))
    }

    @Test
    fun `degenerate image dims fall back to placeholder signal`() {
        // ورودی بی‌معنا → 0f و false (فراخوان جای‌نگه‌دار ۳:۱ می‌گذارد)
        assertEquals(0f, AdsFeedPolicy.bannerHeightDp(0, 100, 360f), 0.001f)
        assertEquals(0f, AdsFeedPolicy.bannerHeightDp(100, 0, 360f), 0.001f)
        assertEquals(0f, AdsFeedPolicy.bannerHeightDp(100, 100, 0f), 0.001f)
        assertFalse(AdsFeedPolicy.bannerFillsExactly(0, 100, 360f))
    }

    @Test
    fun `banner band constants are sane`() {
        // کف باید دست‌کم ناحیهٔ لمسِ راحت (۴۸dp+) باشد و سقف کمتر از نیم صفحهٔ معمول
        assertTrue(AdsFeedPolicy.MIN_BANNER_HEIGHT_DP >= 96)
        assertTrue(AdsFeedPolicy.MAX_BANNER_HEIGHT_DP <= 320)
        assertTrue(AdsFeedPolicy.MIN_BANNER_HEIGHT_DP < AdsFeedPolicy.MAX_BANNER_HEIGHT_DP)
    }

    // ---------- تصمیم انتشار پنل — نسخهٔ ۱.۱۱.۳۸ (گزارش کاربر: حذف کار نمی‌کرد) ----------

    @Test
    fun `publish decision - items present publishes items`() {
        assertEquals(
            AdsFeedPolicy.PublishDecision.PUBLISH_ITEMS,
            AdsFeedPolicy.publishDecision(draftCount = 2, publishableCount = 2)
        )
        // یکی رد شده ولی دیگری سالم است → باز هم انتشار با آیتم‌های سالم
        assertEquals(
            AdsFeedPolicy.PublishDecision.PUBLISH_ITEMS,
            AdsFeedPolicy.publishDecision(draftCount = 2, publishableCount = 1)
        )
    }

    @Test
    fun `publish decision - empty or all-off draft is an intentional clear`() {
        // گزارش کاربر: قبلاً گارد ۱.۱۱.۳۶ حذف عمدی را هم مسدود می‌کرد —
        // فید خالی = حذف همهٔ تبلیغ‌ها و باید منتشر شود (با sequence بالاتر)
        assertEquals(
            AdsFeedPolicy.PublishDecision.PUBLISH_EMPTY_CLEAR,
            AdsFeedPolicy.publishDecision(draftCount = 0, publishableCount = 0)
        )
    }

    @Test
    fun `publish decision - all drafts failed validation is refused`() {
        // حفاظ ۱.۱۱.۳۶ فقط برای «رد شدن تصادفی همهٔ بنرها» می‌ماند
        // (مثلاً تصویر گالری خوانده نشد) — حذف بی‌صدا ممنوع
        assertEquals(
            AdsFeedPolicy.PublishDecision.REFUSE_ALL_FAILED,
            AdsFeedPolicy.publishDecision(draftCount = 3, publishableCount = 0)
        )
        assertEquals(
            AdsFeedPolicy.PublishDecision.REFUSE_ALL_FAILED,
            AdsFeedPolicy.publishDecision(draftCount = 1, publishableCount = 0)
        )
    }
}
