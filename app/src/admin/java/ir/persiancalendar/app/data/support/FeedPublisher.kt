package ir.persiancalendar.app.data.support

import android.content.Context
import android.util.Base64
import ir.persiancalendar.app.core.ads.AdPolicy
import ir.persiancalendar.app.core.ads.AdsFeedPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * انتشار خوراک برای کاربران — فقط در فلاور «admin» (نسخهٔ شخصی مدیر).
 *
 * نسخهٔ ۱.۱۱.۳۰ (بازرسی امنیتی مرحلهٔ دوم: «githubToken هنوز در اپ هست»):
 * توابع انتشار از AnnouncementFeed و MainAdFeed (کد مشترک نسخهٔ عمومی) به این
 * فایل منتقل شدند — نسخهٔ عمومی نه توکن دارد، نه کد PUT گیت‌هاب.
 *
 * نکتهٔ امضا: پس از هر انتشار موفق، فایل JSON روی مخزن عوض می‌شود ولی امضای
 * .sig آن هنوز قدیمی است؛ تا وورکفلوی «sign-feed» امضای تازه بسازد (چند ثانیه
 * بعد)، اپ‌های ۱.۱۱.۳۰ به بعد همان لحظه منبع را «امضای نامعتبر» می‌دانند و به
 * کش می‌روند — طبیعی و بی‌خطر. این تابع بعد از PUT، وورکفلوی امضا را با
 * workflow_dispatch راه می‌اندازد؛ اگر توکن اجازهٔ Actions نداشته باشد، پیام
 * راهنما نشان می‌شود (انتشار از رایانه همیشه جایگزین است).
 */
object FeedPublisher {

    private const val REPO = "ahmaddaneshvar1992-oss/PersianCalendar"

    // ---------- انتشار اطلاعیه‌ها (از AnnouncementFeed.publish — ۱.۱۱.۳۰ منتقل شد) ----------

    fun publishAnnouncements(context: Context, token: String, list: List<Announcement>): String {
        if (token.isBlank()) return "اول توکن گیت‌هاب را وارد و ذخیره کنید"

        val payload = JSONArray()
        var embeddedImages = 0
        var failedImages = 0
        list.filter { it.on }.forEach { a ->
            val image = when {
                a.image.startsWith("http") -> a.image
                a.image.isNotBlank() -> {
                    // نسخهٔ ۱.۱۱.۲۵: عکس گالری هم جاسازی می‌شود (فشردهٔ خودکار تا ۲۰۰ کیلوبایت)
                    val uri = ImageEmbed.dataUriFromFile(a.image)
                    if (uri.isNotBlank()) embeddedImages++ else failedImages++
                    uri
                }
                else -> ""
            }
            payload.put(
                JSONObject()
                    .put("id", a.id)
                    .put("type", a.type)
                    .put("title", a.title)
                    .put("body", a.body)
                    .put("link", a.link)
                    .put("image", image)
                    .put("on", true)
                    .put("at", a.at)
            )
        }
        val msg = publishJson(
            token = token,
            path = "feed/announcements.json",
            commitMessage = "اطلاعیه‌های بادخزان — انتشار از پنل مدیریت",
            payload = payload.toString(2)
        )
        return if (msg.startsWith("✓")) {
            val img = when {
                embeddedImages > 0 && failedImages == 0 ->
                    " — تصویر $embeddedImages اطلاعیه جاسازی شد"
                embeddedImages > 0 ->
                    " — تصویر $embeddedImages اطلاعیه جاسازی شد، $failedImages تصویر منتشر نشد"
                else -> ""
            }
            "برای کاربران منتشر شد ✓$img — امضای خوراک چند ثانیه بعد خودکار ساخته می‌شود؛ با باز کردن بعدی برنامه یا تیک روزانه، اعلان می‌گیرند"
        } else msg
    }

    // ---------- انتشار فید تبلیغات مدیریت‌شده — نسخهٔ ۱.۱۱.۳۵ ----------

    /**
     * انتشار فید «تبلیغات مدیریت‌شده» (tile + بنر بدون ×) — نسخهٔ ۱.۱۱.۳۵،
     * اصلاح ۱.۱۱.۳۶ (گزارش کاربر: «تبلیغ که می‌ذارم نمایش داده نمی‌شود»).
     *
     * علت گزارش (بازرسی): در ۱.۱۱.۳۵ هر بنری که تصویرش URL https نبود (یعنی
     * همهٔ عکس‌های انتخابی گالری!) بی‌صدا حذف می‌شد و فید «خالی» منتشر می‌شد —
     * sequence ۳ زندهٔ مخزن دقیقاً همین است: items: [].
     *
     * اصلاح:
     *  - عکس گالری/فایل داخلی مثل اطلاعیه‌ها «جاسازی» می‌شود
     *    (ImageEmbed.dataUriFromFile — فشردهٔ خودکار تا ۲۰۰ کیلوبایت JPEG)؛
     *    اپ ۱.۱۱.۳۶ این تصویر درون‌خطی را محلی decode می‌کند (بدون شبکه).
     *  - URL تصویر همچنان فقط https پذیرفته می‌شود.
     *  - اگر «هیچ» آیتمی قابل انتشار نباشد، هیچ‌چیز منتشر نمی‌شود — پیام
     *    روشن دلیل هر بنر ردشده را می‌گوید (نه فید خالیِ بی‌صدا).
     *
     *  - sequence: از فید فعلی مخزن خوانده و یکی زیاد می‌شود (ضد rollback اپ
     *    فید با sequence کوچک‌تر یا مساوی را رد می‌کند)؛ فرمت آرایهٔ قدیمی = ۰.
     *  - لینک https = external؛ کلید لیست‌سفید = internal؛ غیر از این = بدون لمس.
     */
    fun publishAdsFeed(context: Context, token: String, list: List<MainAd>): String {
        if (token.isBlank()) return "اول توکن گیت‌هاب را وارد و ذخیره کنید"

        // ۱) فید فعلی مخزن — برای sequence (بدون این، انتشار عمدی انجام نمی‌شود)
        val current = fetchFeedFile(token)
        val currentSeq = current?.let { AdsFeed.parseDoc(it)?.sequence } ?: 0L
        if (current == null) return "فید فعلی مخزن خوانده نشد — sequence قابل تعیین نیست؛ اتصال اینترنت را بررسی کنید یا از رایانه با tools/ads_feed.py منتشر کنید"

        // ۲) ساخت آیتم‌ها از پیش‌نویس پنل — تصویر https یا جاسازی خودکار گالری
        val items = JSONArray()
        val reasons = mutableListOf<String>() // دلیل ردشدن هر بنر — در پیام نهایی
        var embeddedImages = 0
        list.forEachIndexed { idx, a ->
            val name = a.title.ifBlank { "بدون عنوان" }
            if (a.title.isBlank()) {
                reasons.add("بنر ${idx + 1}: عنوان ندارد")
                return@forEachIndexed
            }
            // تصویر: URL https → همان؛ فایل محلی (گالری) → جاسازی data URI
            val image = when {
                a.image.startsWith("https://") -> a.image.trim()
                a.image.isNotBlank() -> {
                    val uri = ImageEmbed.dataUriFromFile(a.image)
                    if (uri.isNotBlank()) {
                        embeddedImages++
                        uri
                    } else {
                        reasons.add("«$name»: تصویرش خوانده نشد — دوباره از گالری انتخاب کنید")
                        return@forEachIndexed
                    }
                }
                else -> {
                    reasons.add("«$name»: تصویر ندارد (بنر تبلیغاتی باید تصویر داشته باشد)")
                    return@forEachIndexed
                }
            }
            val (targetType, target) = when {
                AdPolicy.isSafeLink(a.link) -> "external" to a.link.trim()
                AdsFeedPolicy.isSafeInternalTarget(a.link) -> "internal" to AdsFeedPolicy.clamp(
                    a.link.trim(), AdsFeedPolicy.MAX_ID
                )
                else -> "" to "" // بدون مقصد معتبر = کارت فقط نمایشی
            }
            items.put(
                JSONObject()
                    .put("id", "banner-${a.id}")
                    .put("type", "banner")
                    .put("enabled", true)
                    .put("title", AdsFeedPolicy.clamp(a.title, AdsFeedPolicy.MAX_TITLE))
                    .put("imageUrl", image)
                    .put("targetType", targetType)
                    .put("target", target)
                    .put("order", idx)
                    .put("startAt", a.startAt)
                    .put("endAt", a.endAt)
                    .put("minVersionCode", a.minVersionCode)
                    .put("description", AdsFeedPolicy.clamp(a.body, AdsFeedPolicy.MAX_DESC))
            )
        }

        // ۳) تصمیم انتشار — نسخهٔ ۱.۱۱.۳۸ (گزارش کاربر: «حذف که می‌کنم نه برای
        // مدیر حذف می‌شود نه برای کاربر»): گارد ۱.۱۱.۳۶ فیدِ بدون آیتم را همیشه
        // رد می‌کرد و «حذف عمدی» را هم غیرممکن کرده بود — فید بدون آیتم یعنی
        // حذف همهٔ تبلیغ‌ها. اکنون سه حالت تفکیک می‌شود (AdsFeedPolicy.publishDecision):
        //   • پیش‌نویس خالی/خاموش → فید خالیِ معتبر با sequence بالاتر منتشر می‌شود (حذف عمدی)
        //   • پیش‌نویس پر ولی همهٔ بنرها ردشده → هیچ‌چیز منتشر نمی‌شود + دلیل‌ها
        //     (همان حفاظ ۱.۱۱.۳۶ برابر حذف بی‌صدای تصادفی)
        when (AdsFeedPolicy.publishDecision(draftCount = list.size, publishableCount = items.length())) {
            AdsFeedPolicy.PublishDecision.PUBLISH_EMPTY_CLEAR -> {
                val doc = JSONObject()
                    .put("sequence", currentSeq + 1)
                    .put("issuedAt", java.time.Instant.now().toString())
                    .put("adsEnabled", true)
                    .put("maxCacheDays", AdsFeedPolicy.DEFAULT_MAX_CACHE_DAYS)
                    .put("items", JSONArray())
                val msg = publishJson(
                    token = token,
                    path = "feed/main_ads.json",
                    commitMessage = "فید تبلیغات مدیریت‌شده (sequence ${currentSeq + 1}) — حذف همهٔ تبلیغ‌ها — انتشار از پنل مدیریت",
                    payload = doc.toString(2)
                )
                return if (msg.startsWith("✓")) {
                    "فید خالی منتشر شد (sequence ${currentSeq + 1}) — همهٔ تبلیغ‌ها برای همهٔ کاربران و خود شما حذف شد ✓ — " +
                            "امضای خوراک حدود ۱ تا ۳ دقیقه بعد خودکار ساخته می‌شود؛ پنل را ببندید و چند دقیقه صبر کنید " +
                            "(اپ تا ~۸ دقیقه خودش چند بار بررسی می‌کند و حذف همین‌جا هم اعمال می‌شود)"
                } else msg
            }
            AdsFeedPolicy.PublishDecision.REFUSE_ALL_FAILED ->
                return "هیچ بنری قابل انتشار نبود و فید دست‌نخورده ماند:\n" +
                        reasons.joinToString("\n") { "• $it" } +
                        "\nتصویر گالری را دوباره انتخاب کنید یا URL تصویر https بدهید؛ " +
                        "اگر می‌خواهید همهٔ تبلیغ‌ها حذف شوند، بنرها را از فهرست بالا پاک یا خاموش کنید و دوباره انتشار بزنید."
            AdsFeedPolicy.PublishDecision.PUBLISH_ITEMS -> Unit // پایین منتشر می‌شود
        }

        val doc = JSONObject()
            .put("sequence", currentSeq + 1)
            .put("issuedAt", java.time.Instant.now().toString())
            .put("adsEnabled", true)
            .put("maxCacheDays", AdsFeedPolicy.DEFAULT_MAX_CACHE_DAYS)
            .put("items", items)

        val msg = publishJson(
            token = token,
            path = "feed/main_ads.json",
            commitMessage = "فید تبلیغات مدیریت‌شده (sequence ${currentSeq + 1}) — انتشار از پنل مدیریت",
            payload = doc.toString(2)
        )
        return if (msg.startsWith("✓")) {
            val extra = buildString {
                if (embeddedImages > 0) append(" — تصویر $embeddedImages بنر از گالری داخل فید جاسازی شد (مثل اطلاعیه‌ها)")
                if (reasons.isNotEmpty()) append(" — ${reasons.size} بنر دیگر منتشر نشد: ${reasons.joinToString("؛ ")}")
            }
            "فید تبلیغات (sequence ${currentSeq + 1}) با ${items.length()} آیتم برای همهٔ کاربران منتشر شد ✓$extra — " +
                    "امضای خوراک حدود ۱ تا ۳ دقیقه بعد خودکار ساخته می‌شود؛ پنل را ببندید و چند دقیقه صبر کنید " +
                    "(اپ تا ~۸ دقیقه خودش چند بار بررسی می‌کند و تبلیغ تازه همین‌جا هم اعمال می‌شود)"
        } else msg
    }

    /**
     * خواندن متن فعلی feed/main_ads.json مخزن (Contents API) — برای sequence.
     * خطا = null.
     */
    private fun fetchFeedFile(token: String): String? = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/contents/feed/main_ads.json")
            .openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return null
            val o = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val b64 = o.optString("content").replace("\n", "").replace("\r", "")
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * PUT فایل جیسون در مخزن (Contents API) + پاک‌کردن کش jsDelivr + راه‌اندازی
     * وورکفلوی امضا. پیام موفقیت با «✓» شروع می‌شود تا تماس‌گیرنده پیام تکمیلی بگذارد.
     */
    private fun publishJson(token: String, path: String, commitMessage: String, payload: String): String {
        val bodyJson = JSONObject()
            .put("message", commitMessage)
            .put(
                "content",
                Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            )

        // ۱) اگر فایل از قبل هست، sha آن لازم است (وگرنه فایل ساخته می‌شود)
        val sha = runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/contents/$path")
                .openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                if (conn.responseCode == 200) {
                    JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                        .optString("sha")
                } else ""
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
        if (sha.isNotBlank()) bodyJson.put("sha", sha)

        // ۲) PUT فایل
        val conn = URL("https://api.github.com/repos/$REPO/contents/$path")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    // نسخهٔ ۱.۱۱.۲۵: کش CDN جی‌اس‌دلیور (تا ۱۲ ساعت!) همان لحظه پاک می‌شود
                    FeedFetcher.purgeCdn(REPO, path)
                    val wf = triggerSignWorkflow(token)
                    "✓" + when {
                        wf -> ""
                        else -> " — توجه: وورکفلوی امضا راه نیفتاد (توکن اجازهٔ Actions ندارد یا خطای شبکه). امضا را از رایانه با scripts/sign_feed.py بسازید؛ تا آن موقع اپ‌های جدید خوراک تازه را «بی‌امضا» می‌دانند. "
                    }
                }
                401 -> "توکن نامعتبر است (کد ۴۰۱) — توکن را دوباره بررسی کنید"
                403 -> "توکن اجازهٔ نوشتن در مخزن را ندارد (کد ۴۰۳) — دسترسی Contents: write لازم است"
                404 -> "مخزن پیدا نشد (کد ۴۰۴)"
                else -> "خطا (کد ${conn.responseCode}): " + runCatching {
                    conn.errorStream?.use { s -> s.readBytes().toString(Charsets.UTF_8) }?.take(180)
                }.getOrNull().orEmpty()
            }
        } catch (e: Exception) {
            "ارتباط برقرار نشد: ${e.message ?: "خطای شبکه"}"
        } finally {
            conn.disconnect()
        }
    }

    /**
     * راه‌اندازی وورکفلوی sign-feed (workflow_dispatch) تا امضای .sig تازه
     * با کلید خصوصیِ Secret مخزن ساخته شود — کلید خصوصی هرگز روی دستگاه نیست.
     * توکن باید «Actions: Read and write» هم داشته باشد؛ در خطا بی‌صدا false.
     */
    private fun triggerSignWorkflow(token: String): Boolean = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/actions/workflows/sign-feed.yml/dispatches")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.outputStream.use {
                it.write("""{"ref":"main"}""".toByteArray(Charsets.UTF_8))
            }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
