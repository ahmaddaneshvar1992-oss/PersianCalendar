package ir.persiancalendar.app.data.support

import ir.persiancalendar.app.core.ads.AdsFeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست واحد تجزیه و اعتبارسنجی فید تبلیغات مدیریت‌شده — نسخهٔ ۱.۱۱.۳۵ (کار ۱)
 * معیار پذیرش ۱ و ۳: آیتم درست نمایش داده می‌شود، فید خراب/ناامن هرگز کرش
 * نمی‌کند و طرح‌های غیرhttps رد می‌شوند.
 * (org.json واقعی — به testImplementation اضافه شده در ۱.۱۱.۳۴)
 */
class AdsFeedParseTest {

    private fun doc(
        sequence: Long = 1,
        items: String = "[]",
        issuedAt: String = "\"2026-11-01T00:00:00Z\"",
        adsEnabled: Boolean = true
    ): String = """
        {"sequence":$sequence,"issuedAt":$issuedAt,"adsEnabled":$adsEnabled,
         "maxCacheDays":7,"items":$items}
    """.trimIndent()

    private fun tile(
        id: String = "t1",
        icon: String = "https://cdn.example.com/i.png",
        target: String = "\"reminders\"",
        targetType: String = "internal"
    ): String = """
        {"id":"$id","type":"tile","enabled":true,"title":"ختم قرآن",
         "iconUrl":"$icon","badge":"جدید","targetType":"$targetType",
         "target":$target,"order":1,
         "description":"برای باز کردن لمس کنید"}
    """.trimIndent()

    private fun banner(
        id: String = "b1",
        image: String = "https://cdn.example.com/b.png",
        target: String = "https://shop.example.com"
    ): String = """
        {"id":"$id","type":"banner","enabled":true,"title":"فروش ویژه",
         "imageUrl":"$image","targetType":"external",
         "target":"$target","order":2}
    """.trimIndent()

    // ---------- سند ----------

    @Test
    fun `valid doc parses with sequence and items`() {
        val d = AdsFeed.parseDoc(
            doc(items = "[${tile()},${banner()}]")
        )
        assertNotNull(d)
        assertEquals(1L, d!!.sequence)
        assertEquals(2, d.items.size)
        assertTrue(d.adsEnabled)
        assertEquals(7, d.maxCacheDays)
        // issuedAt: 2026-11-01T00:00:00Z = 1793491200000
        assertEquals(1793491200000L, d.issuedAtMs)
    }

    @Test
    fun `structurally invalid doc returns null without crash`() {
        assertNull(AdsFeed.parseDoc("not json at all"))
        assertNull(AdsFeed.parseDoc("[]")) // فرمت آرایهٔ قدیمی → رد
        assertNull(AdsFeed.parseDoc("""{"issuedAt":"2026-11-01T00:00:00Z","items":[]}""")) // بدون sequence
        assertNull(AdsFeed.parseDoc("""{"sequence":0,"items":[]}""")) // sequence < ۱
        assertNull(AdsFeed.parseDoc("""{"sequence":3}""")) // بدون items
        assertNull(AdsFeed.parseDoc("""{"sequence":3,"items":{}}""")) // items آرایه نیست
    }

    // ---------- آیتم‌های نامعتبر حذف می‌شوند ----------

    @Test
    fun `invalid items are dropped safely`() {
        val items = """
            [
             {"type":"tile","title":"بدون id","iconUrl":"https://a.co/i.png"},
             {"id":"t2","type":"tile","title":"","iconUrl":"https://a.co/i.png"},
             {"id":"t3","type":"banner","title":"بنر بدون تصویر"},
             {"id":"t4","type":"sticker","title":"نوع ناشناس"},
             {"id":"t5","type":"tile","title":"آیکون http","iconUrl":"http://a.co/i.png"},
             {"id":"t6","type":"tile","title":"آیکون جعلی","iconUrl":"javascript:alert(1)"},
             ${tile(id = "t7")},
             ${banner(id = "b7")}
            ]
        """.trimIndent()
        val d = AdsFeed.parseDoc(doc(items = items))
        assertNotNull(d)
        assertEquals(listOf("t7", "b7"), d!!.items.map { it.id })
    }

    @Test
    fun `internal target outside whitelist becomes non-clickable`() {
        val bad = """
            [{"id":"t9","type":"tile","title":"x","iconUrl":"https://a.co/i.png",
              "targetType":"internal","target":"del_all_data","order":1}]
        """.trimIndent()
        val d = AdsFeed.parseDoc(doc(items = bad))
        assertEquals("", d!!.items[0].targetType)
        assertEquals("", d.items[0].target)
    }

    @Test
    fun `external target must be https`() {
        val bad = """
            [{"id":"t10","type":"tile","title":"x","iconUrl":"https://a.co/i.png",
              "targetType":"external","target":"http://evil.example.com","order":1}]
        """.trimIndent()
        val d = AdsFeed.parseDoc(doc(items = bad))
        assertEquals("", d!!.items[0].target)
        // data هرگز مقصد لمس نیست (فقط تصویر درون‌خطی مجاز است)
        val bad2 = """
            [{"id":"t11","type":"tile","title":"x","iconUrl":"https://a.co/i.png",
              "targetType":"external","target":"data:image/jpeg;base64,AAAA","order":1}]
        """.trimIndent()
        val d2 = AdsFeed.parseDoc(doc(items = bad2))
        assertEquals("", d2!!.items[0].target)
    }

    // ---------- تصویر درون‌خطی (جاسازی اطلاعیه‌ها — ۱.۱۱.۳۶) ----------

    @Test
    fun `banner with embedded jpeg data uri is accepted`() {
        val d = AdsFeed.parseDoc(
            doc(items = """
                [{"id":"b10","type":"banner","title":"بنر گالری",
                  "imageUrl":"data:image/jpeg;base64,/9j/4AAQSkZJRg==",
                  "targetType":"internal","target":"announcements","order":1}]
            """.trimIndent())
        )
        assertNotNull(d)
        assertEquals(1, d!!.items.size)
        assertTrue(d.items[0].imageUrl.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `tile with embedded png icon is accepted`() {
        val d = AdsFeed.parseDoc(
            doc(items = """
                [{"id":"t20","type":"tile","title":"کارت گالری",
                  "iconUrl":"data:image/png;base64,iVBORw0KGgo=","order":1}]
            """.trimIndent())
        )
        assertNotNull(d)
        assertEquals("t20", d!!.items[0].id)
    }

    @Test
    fun `svg data uri and other mimes are dropped`() {
        val d = AdsFeed.parseDoc(
            doc(items = """
                [{"id":"b30","type":"banner","title":"svg جعلی",
                  "imageUrl":"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=","order":1},
                 {"id":"b31","type":"banner","title":"html جعلی",
                  "imageUrl":"data:text/html;base64,PGI+","order":2},
                 ${banner(id = "b32")}]
            """.trimIndent())
        )
        assertNotNull(d)
        assertEquals(listOf("b32"), d!!.items.map { it.id })
    }

    @Test
    fun `texts are clamped`() {
        val long = "ع".repeat(300)
        val d = AdsFeed.parseDoc(
            doc(
                items = """
                [{"id":"${"x".repeat(200)}","type":"tile","title":"$long",
                  "iconUrl":"https://a.co/i.png","badge":"$long",
                  "description":"$long","order":1}]
                """.trimIndent()
            )
        )
        assertNotNull(d)
        val it0 = d!!.items[0]
        assertEquals(AdsFeedPolicy.MAX_ID, it0.id.length)
        assertEquals(AdsFeedPolicy.MAX_TITLE, it0.title.length)
        assertEquals(AdsFeedPolicy.MAX_BADGE, it0.badge.length)
        assertEquals(AdsFeedPolicy.MAX_DESC, it0.description.length)
    }

    @Test
    fun `items array capped to max`() {
        val many = (1..30).joinToString(",") {
            """{"id":"n$it","type":"tile","title":"t","iconUrl":"https://a.co/$it.png","order":$it}"""
        }
        val d = AdsFeed.parseDoc(doc(items = "[$many]"))
        assertEquals(AdsFeedPolicy.MAX_ITEMS, d!!.items.size)
    }

    // ---------- فیلترهای نمایش (بر پایهٔ سند پارس‌شده) ----------

    @Test
    fun `disabled item is filtered by policy`() {
        val d = AdsFeed.parseDoc(
            doc(
                items = """
                [{"id":"off","type":"tile","enabled":false,"title":"x",
                  "iconUrl":"https://a.co/i.png","order":1}]
                """.trimIndent()
            )
        )
        val out = AdsFeedPolicy.select(
            items = d!!.items,
            record = {
                AdsFeedPolicy.ItemRecord(it.id, it.type, it.enabled, it.order, it.startAt, it.endAt, it.minVersionCode)
            },
            nowMs = 1_800_000_000_000L,
            versionCode = 47,
            feedAdsEnabled = true
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `adsEnabled false hides everything (acceptance 3)`() {
        val d = AdsFeed.parseDoc(doc(adsEnabled = false, items = "[${tile()}]"))
        assertFalse(d!!.adsEnabled)
    }

    @Test
    fun `start and end times parse from iso and numbers`() {
        assertEquals(0L, MainAdFeed.parseWhen(null))
        assertEquals(0L, MainAdFeed.parseWhen(""))
        assertEquals(1793491200000L, MainAdFeed.parseWhen("2026-11-01T00:00:00Z"))
        assertEquals(1793491200000L, MainAdFeed.parseWhen(1793491200000L))
        assertEquals(1793491200000L, MainAdFeed.parseWhen("1793491200000"))
        // بدنهٔ نامعتبر → ۰ (بدون محدودیت) — نه کرش
        assertEquals(0L, MainAdFeed.parseWhen("yesterday"))
    }
}
