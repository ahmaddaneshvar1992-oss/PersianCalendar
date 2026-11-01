package ir.persiancalendar.app.data.support

import android.content.Context
import ir.persiancalendar.app.core.ads.AdsFeedPolicy
import org.json.JSONObject
import java.io.File

/**
 * خوراک «تبلیغات مدیریت‌شده» — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۱)
 *
 * جایگزین مدل قابل‌حذفِ ۱.۱۱.۳۴ در صفحه اصلی: دو نوع آیتم tile (کارت شبکه‌ای)
 * و banner (بنر تمام‌عرض) که کاربر هیچ راهی برای پنهان‌کردنشان ندارد و فقط
 * مدیر با تغییر فید راه‌دور آنها را اضافه/ویرایش/حذف می‌کند.
 *
 * فایل خوراک: feed/main_ads.json — از این نسخه «شیء» است (آرایهٔ قدیمی نسخه‌های
 * قبلی بازنشسته شد؛ نسخه‌های قدیمی اپ آن را آرایه نمی‌بینند و به کش خود می‌روند —
 * بی‌خطر و بدون کرش):
 *
 *   { "sequence": 1, "issuedAt": "2026-11-01T00:00:00Z", "adsEnabled": true,
 *     "maxCacheDays": 7, "items": [ { id, type, enabled, title, iconUrl,
 *     imageUrl, badge, targetType, target, order, startAt, endAt,
 *     minVersionCode, description } ] }
 *
 * امنیت (همه الزامی):
 *  - امضای Ed25519 کل فایل پیش از پارس (FeedFetcher.fetchSignedBody با
 *    FeedSecurity — هر دو کلید عمومی سنجاق‌شده)؛ منبع بی‌امضا/جعلی رد می‌شود.
 *  - ضدمقاومت بازپخش: sequence کوچک‌تر یا مساویِ آخرین فید معتبر رد می‌شود
 *    (AdsFeedPolicy.sequenceAccepts) تا امضای قدیمیِ فیدِ حذف‌شده آیتم را
 *    برنگرداند. گارد نصب تازه هم فیدِ خیلی قدیمی را رد می‌کند.
 *  - فهرست آیتم‌ها با هر فید معتبر «کامل جایگزین» می‌شود (ادغام نمی‌شود) —
 *    نمایش همیشه از روی کشِ آخرین فید معتبر ساخته می‌شود.
 *  - دریافت ناموفق → آخرین کش معتبر؛ کش قدیمی‌تر از maxCacheDays (پیش‌فرض ۷)
 *    → هیچ تبلیغی نمایش داده نمی‌شود.
 *  - اپ فقط می‌خواند: هیچ توکن/کلید خصوصی/پنل نوشتن در نسخهٔ عمومی نیست.
 *
 * کش و وضعیت روی دستگاه:
 *  - ads_feed_cache.json  = بایت‌های دقیق آخرین فید تأییدشدهٔ امضایی
 *  - ads_feed_state.json  = { sequence, fetchedAtMs } برای ضد rollback و تازگی
 */
object AdsFeed {

    private const val REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
    private const val PATH = "feed/main_ads.json"
    private const val CACHE_FILE = "ads_feed_cache.json"
    private const val STATE_FILE = "ads_feed_state.json"

    /** نام منبع آخرین دریافت موفق — برای نمایش در پنل مدیریت */
    @Volatile
    var lastSource: String = ""
        private set

    /** شرح خطاهای آخرین دریافت — برای عیب‌یابی در پنل مدیریت */
    @Volatile
    var lastError: String = ""
        private set

    // ---------- مدل ----------

    /**
     * یک آیتم تبلیغ فید — همهٔ متن‌ها هنگام پارس clamp شده‌اند و همهٔ URLها
     * امن (https) هستند؛ آیتم نامعتبر کلاً حذف می‌شود.
     */
    data class FeedItem(
        val id: String,
        val type: String,                // tile | banner
        val enabled: Boolean,
        val title: String,
        val badge: String,               // اختیاری — مثلاً «جدید»
        val iconUrl: String,             // فقط tile — https
        val imageUrl: String,            // فقط banner — https
        val targetType: String,          // external | internal
        val target: String,              // https یا کلید لیست‌سفید
        val order: Int,
        val startAt: Long,
        val endAt: Long,
        val minVersionCode: Int,
        val description: String          // متن TalkBack — اختیاری
    )

    /** سند فید پس از پارس — sequence/issuedAt خام برای لایهٔ ضد rollback */
    data class Doc(
        val sequence: Long,
        val issuedAtMs: Long,
        val adsEnabled: Boolean,
        val maxCacheDays: Int,
        val items: List<FeedItem>
    )

    /** وضعیت ذخیره‌شدهٔ ضد rollback */
    data class State(val sequence: Long, val fetchedAtMs: Long)

    // ---------- پارس ----------

    /**
     * پارس سند فید. خروجی null = ساختار کلاً نامعتبر (شیء نیست، sequence
     * ندارد یا items آرایه نیست) → کل فایل رد می‌شود.
     * آیتم‌های نامعتبر (بدون id/title، URL ناامن، type ناشناس، مقصد داخلی
     * خارج از لیست‌سفید) بی‌صدا حذف می‌شوند — فید خراب هرگز کرش نمی‌کند.
     */
    fun parseDoc(json: String): Doc? = runCatching {
        val o = JSONObject(json)
        val sequence = o.optLong("sequence", 0L)
        if (sequence < 1L) return null
        val itemsArr = o.optJSONArray("items") ?: return null
        val adsEnabled = o.optBoolean("adsEnabled", true)
        val issuedAtMs = MainAdFeed.parseWhen(o.opt("issuedAt"))
        val maxCacheDays = o.optInt("maxCacheDays", AdsFeedPolicy.DEFAULT_MAX_CACHE_DAYS)
            .coerceIn(0, AdsFeedPolicy.MAX_CACHE_DAYS)
        val items = (0 until itemsArr.length())
            .mapNotNull { idx -> parseItem(itemsArr.optJSONObject(idx)) }
            .take(AdsFeedPolicy.MAX_ITEMS)
        Doc(sequence, issuedAtMs, adsEnabled, maxCacheDays, items)
    }.getOrNull()

    /** پارس یک آیتم — نامعتبر = null (حذف از فهرست) */
    fun parseItem(o: JSONObject?): FeedItem? {
        o ?: return null
        val id = AdsFeedPolicy.clamp(o.optString("id"), AdsFeedPolicy.MAX_ID)
        if (id.isEmpty()) return null
        val type = when (o.optString("type").trim()) {
            AdsFeedPolicy.TYPE_TILE -> AdsFeedPolicy.TYPE_TILE
            AdsFeedPolicy.TYPE_BANNER -> AdsFeedPolicy.TYPE_BANNER
            else -> return null
        }
        val title = AdsFeedPolicy.clamp(o.optString("title"), AdsFeedPolicy.MAX_TITLE)
        if (title.isEmpty()) return null

        // الزام امنیتی: فقط https — طرح‌های دیگر (http/intent/file/javascript/content/data) رد
        val iconUrl = o.optString("iconUrl").trim().take(AdsFeedPolicy.MAX_URL)
        val imageUrl = o.optString("imageUrl").trim().take(AdsFeedPolicy.MAX_URL)
        if (type == AdsFeedPolicy.TYPE_TILE && !AdsFeedPolicy.isSafeHttpsUrl(iconUrl)) return null
        if (type == AdsFeedPolicy.TYPE_BANNER && !AdsFeedPolicy.isSafeHttpsUrl(imageUrl)) return null

        // مقصد: external فقط https؛ internal فقط کلید لیست‌سفید؛ نامعتبر = بدون لمس
        val rawTargetType = o.optString("targetType").trim().lowercase()
        val rawTarget = o.optString("target").trim()
        val (targetType, target) = when (rawTargetType) {
            "internal" -> if (AdsFeedPolicy.isSafeInternalTarget(rawTarget)) {
                "internal" to AdsFeedPolicy.clamp(rawTarget, AdsFeedPolicy.MAX_ID)
            } else "" to ""
            else -> if (AdsFeedPolicy.isSafeHttpsUrl(rawTarget)) {
                "external" to rawTarget.take(AdsFeedPolicy.MAX_URL)
            } else "" to ""
        }

        return FeedItem(
            id = id,
            type = type,
            enabled = o.optBoolean("enabled", true),
            title = title,
            badge = AdsFeedPolicy.clamp(o.optString("badge"), AdsFeedPolicy.MAX_BADGE),
            iconUrl = iconUrl,
            imageUrl = imageUrl,
            targetType = targetType,
            target = target,
            order = o.optInt("order", 0),
            startAt = MainAdFeed.parseWhen(o.opt("startAt")),
            endAt = MainAdFeed.parseWhen(o.opt("endAt")),
            minVersionCode = o.optInt("minVersionCode", 0).coerceAtLeast(0),
            description = AdsFeedPolicy.clamp(o.optString("description"), AdsFeedPolicy.MAX_DESC)
        )
    }

    // ---------- ذخیره‌سازی محلی ----------

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)
    private fun stateFile(context: Context) = File(context.filesDir, STATE_FILE)

    fun loadState(context: Context): State = runCatching {
        val o = JSONObject(stateFile(context).readText())
        State(o.optLong("sequence", 0L), o.optLong("fetchedAtMs", 0L))
    }.getOrDefault(State(0L, 0L))

    private fun saveState(context: Context, s: State) {
        runCatching {
            stateFile(context).writeText(
                JSONObject().put("sequence", s.sequence).put("fetchedAtMs", s.fetchedAtMs).toString()
            )
        }
    }

    // ---------- همگام‌سازی ----------

    /**
     * دریافت فید از منابع امضادار (سرور شخصی → raw → jsDelivr → api.github) و
     * پذیرش مشروط: امضای معتبر (داخل FeedFetcher) + sequence بالاتر از ذخیره‌شده
     * + گارد اولین پذیرش. در پذیرش، کش و وضعیت «کامل جایگزین» می‌شوند.
     * خروجی: true = فید تازه‌ای پذیرفته شد (UI باید رفرش شود).
     * در هر شکست چیزی عوض نمی‌شود — نمایش از کش قبلی ادامه می‌یابد.
     * فقط از نخ پس‌زمینه صدا زده شود.
     *
     * نکته: کش عمداً این‌جا و فقط «پس از» تأیید sequence نوشته می‌شود؛ FeedFetcher
     * با cache=null صدا زده می‌شود تا بدنهٔ ردشده (rollback) هرگز جای کش معتبر
     * را نگیرد.
     */
    fun sync(context: Context): Boolean {
        val r = FeedFetcher.fetch(REPO, PATH, cache = null, jsonArray = false)
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        if (!r.ok) return false
        val doc = parseDoc(r.body) ?: return false
        val state = loadState(context)
        if (!AdsFeedPolicy.sequenceAccepts(stored = state.sequence, incoming = doc.sequence)) {
            return false // بازپخش فید قدیمی‌تر یا همان فعلی — رد
        }
        if (state.sequence == 0L &&
            !AdsFeedPolicy.firstAcceptFreshEnough(doc.issuedAtMs, System.currentTimeMillis())
        ) {
            return false // گارد نصب تازه: فید خیلی قدیمی پذیرفته نمی‌شود
        }
        runCatching { cacheFile(context).writeText(r.body) }
        saveState(context, State(doc.sequence, System.currentTimeMillis()))
        return true
    }

    // ---------- نمایش ----------

    /**
     * آیتم‌های نمایشی امروز: از آخرین کش معتبر؛ اگر کش غایب یا قدیمی‌تر از
     * maxCacheDays باشد یا کلید adsEnabled فید خاموش باشد → فهرست خالی.
     * مرتب بر اساس order فید؛ آیتم‌های غیرفعال/خارج از بازه/پایین‌تر از
     * minVersionCode حذف می‌شوند.
     */
    fun loadDisplay(context: Context, nowMs: Long, versionCode: Int): List<FeedItem> {
        val body = runCatching { cacheFile(context).readText() }.getOrDefault("")
        if (body.isBlank()) return emptyList()
        val doc = parseDoc(body) ?: return emptyList()
        val state = loadState(context)
        if (!AdsFeedPolicy.cacheIsFresh(state.fetchedAtMs, nowMs, doc.maxCacheDays)) return emptyList()
        return AdsFeedPolicy.select(
            items = doc.items,
            record = { it.toRecord() },
            nowMs = nowMs,
            versionCode = versionCode,
            feedAdsEnabled = doc.adsEnabled
        )
    }

    private fun FeedItem.toRecord() = AdsFeedPolicy.ItemRecord(
        id = id, type = type, enabled = enabled, order = order,
        startAt = startAt, endAt = endAt, minVersionCode = minVersionCode
    )

    // ---------- بررسی از سمت مدیر ----------

    /**
     * بررسی خوراک از سمت مدیر (پنل) — نتیجهٔ ساختارمند: تعداد آیتم + شمارهٔ
     * sequence فید زنده. خوراکِ خالیِ معتبر دیگر «ناموفق» تلقی نمی‌شود.
     */
    fun check(context: Context? = null): FeedFetcher.CheckResult {
        val r = FeedFetcher.fetch(REPO, PATH, cache = null, jsonArray = false)
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        val doc = if (r.ok) parseDoc(r.body) else null
        return FeedFetcher.CheckResult(
            ok = r.ok && doc != null,
            count = doc?.items?.size ?: 0,
            source = r.source,
            fromCache = false,
            error = when {
                !r.ok -> r.error
                doc == null -> "پاسخ سند فید معتبر نبود (sequence یا items گم است — فرمت آرایهٔ قدیمی؟)"
                else -> "sequence=${doc.sequence} · adsEnabled=${doc.adsEnabled}"
            }
        )
    }
}
