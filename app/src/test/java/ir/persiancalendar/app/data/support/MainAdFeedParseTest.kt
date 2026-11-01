package ir.persiancalendar.app.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست تجزیهٔ خوراک تبلیغ — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 * با org.json واقعی در JVM (android stub پرتاب خطا می‌کند) — پوشش:
 * فیلدهای جدید، پارس ISO-8601/epoch، پیش‌فرض‌ها، clamp متن، حذف آیتم خراب.
 * فرمت فید آرایه است و امضای Ed25519 پیش از این تابع در FeedFetcher
 * enforced می‌شود — این لایه فقط محتوای تأییدشده را تجزیه می‌کند.
 */
class MainAdFeedParseTest {

    @Test
    fun `parses new fields with defaults`() {
        val json = """
            [
              {"id": 101, "title": "کمپین آگاه", "link": "https://agah.example.com/x",
               "image": "https://cdn.example.com/b.jpg", "priority": 5,
               "startAt": "2026-11-01T00:00:00Z", "endAt": "2026-11-30T23:59:59Z",
               "dismissDays": 3, "minVersionCode": 46}
            ]
        """.trimIndent()
        val ads = MainAdFeed.parseFeed(json)
        assertEquals(1, ads.size)
        val ad = ads[0]
        assertEquals(101L, ad.id)
        assertEquals(5, ad.priority)
        assertEquals(3, ad.dismissDays)
        assertEquals(46, ad.minVersionCode)
        assertEquals(1_793_491_200_000L, ad.startAt) // 2026-11-01T00:00:00Z
        assertTrue(ad.endAt > ad.startAt)
    }

    @Test
    fun `missing optional fields get spec defaults`() {
        val json = """[{"id": 7, "title": "بنر ساده"}]"""
        val ads = MainAdFeed.parseFeed(json)
        assertEquals(1, ads.size)
        val ad = ads[0]
        assertEquals(0, ad.priority)
        assertEquals(7, ad.dismissDays)  // پیش‌فرض ۷ روز طبق مشخصات
        assertEquals(0L, ad.startAt)
        assertEquals(0L, ad.endAt)
        assertEquals(0, ad.minVersionCode)
    }

    @Test
    fun `epoch millis accepted for start and end`() {
        val json = """[{"id": 1, "title": "t", "startAt": 1000, "endAt": 2000}]"""
        val ad = MainAdFeed.parseFeed(json)[0]
        assertEquals(1000L, ad.startAt)
        assertEquals(2000L, ad.endAt)
    }

    @Test
    fun `invalid time values become zero - no crash`() {
        val json = """[{"id": 1, "title": "t", "startAt": "not-a-date", "endAt": true}]"""
        val ad = MainAdFeed.parseFeed(json)[0]
        assertEquals(0L, ad.startAt)
        assertEquals(0L, ad.endAt)
    }

    @Test
    fun `text fields are clamped and item without title dropped`() {
        val long = "x".repeat(500)
        val json = """
            [
              {"id": 1, "title": "$long", "body": "$long", "cta": "$long"},
              {"id": 0, "title": "بدون شناسه"},
              {"id": 2}
            ]
        """.trimIndent()
        val ads = MainAdFeed.parseFeed(json)
        assertEquals(1, ads.size)
        assertEquals(120, ads[0].title.length) // AdPolicy.MAX_TITLE
        assertEquals(200, ads[0].body.length)  // AdPolicy.MAX_BODY
        assertEquals(30, ads[0].cta.length)    // AdPolicy.MAX_CTA
    }

    @Test
    fun `garbage input returns empty list - offline and malicious safe`() {
        assertEquals(0, MainAdFeed.parseFeed("not json at all").size)
        assertEquals(0, MainAdFeed.parseFeed("<html>error</html>").size)
        assertEquals(0, MainAdFeed.parseFeed("[]").size)
    }

    @Test
    fun `new fields survive encode round trip`() {
        val ad = MainAd(
            id = 55L, title = "تیتر", body = "بدنه", cta = "باز کن",
            link = "https://x.example.com", image = "", colorIndex = 2,
            priority = 9, startAt = 111L, endAt = 222L, dismissDays = 0, minVersionCode = 46
        )
        val encoded = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("id", ad.id).put("title", ad.title).put("body", ad.body)
                .put("cta", ad.cta).put("link", ad.link).put("image", ad.image)
                .put("colorIndex", ad.colorIndex).put("on", ad.on).put("at", ad.at)
                .put("priority", ad.priority).put("startAt", ad.startAt).put("endAt", ad.endAt)
                .put("dismissDays", ad.dismissDays).put("minVersionCode", ad.minVersionCode)
        ).toString()
        val parsed = MainAdFeed.parseFeed(encoded)[0]
        assertEquals(9, parsed.priority)
        assertEquals(111L, parsed.startAt)
        assertEquals(222L, parsed.endAt)
        assertEquals(0, parsed.dismissDays)
        assertEquals(46, parsed.minVersionCode)
    }
}
