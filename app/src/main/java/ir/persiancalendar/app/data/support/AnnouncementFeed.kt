package ir.persiancalendar.app.data.support

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * توزیع همگانی اطلاعیه‌ها — نسخه ۱.۱۱.۱۶ (درخواست کاربر:
 * «میخوام وقتی اطلاعیه میدم نوفیکیشنم برای کاربران نمایش داده بشه»)
 *
 * پیش از این، اطلاعیه‌های پنل مدیریت فقط روی دستگاهِ مدیر ذخیره می‌شدند.
 * از این نسخه خوراک اطلاعیه روی همان مخزن گیت‌هاب برنامه منتشر می‌شود:
 *
 *  - مدیر: پنل مدیریت ← «انتشار برای کاربران» (با توکن گیت‌هاب که فقط روی دستگاه خودش است)
 *  - کاربران: در هر باز شدن برنامه و در تیک نیمه‌شب، خوراک بررسی می‌شود؛
 *    اطلاعیهٔ تازه هم به زنگ (نقطهٔ قرمز) اضافه می‌شود و هم اعلان سیستمی می‌گیرد.
 *    لمس اعلان، برنامه را روی پنل اطلاعیه‌ها باز می‌کند.
 *
 * تصویر اطلاعیه: URL اینترنتی همان‌طور منتشر می‌شود.
 * نسخهٔ ۱.۱۱.۲۵ (گزارش کاربر: «عکس‌ها واسه کاربرها نمایش داده نمیشه»):
 * از این نسخه عکس گالری هم با فشرده‌سازی خودکار به data URI تبدیل و داخل
 * خوراک جاسازی می‌شود — قبلاً عکس گالری هرگز منتشر نمی‌شد.
 *
 * نسخهٔ ۱.۱۱.۲۲: دریافت خوراک چندمنبعی شد (FeedFetcher) — raw، jsDelivr،
 * api.github و در بدترین حالت کش محلی — تا در شبکه‌هایی که به
 * raw.githubusercontent.com دسترسی ندارند هم اطلاعیه‌ها برسند.
 */
object AnnouncementFeed {

    /** خوراک اطلاعیه — فایل JSON روی شاخهٔ main همان مخزن برنامه */
    const val FEED_URL =
        "https://raw.githubusercontent.com/ahmaddaneshvar1992-oss/PersianCalendar/main/feed/announcements.json"

    private const val API_REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
    private const val API_PATH = "feed/announcements.json"
    private const val STATE_FILE = "announcement_feed_state.txt"
    private const val CACHE_FILE = "announcements_cache.json"

    /** نام منبع آخرین دریافت موفق — برای نمایش در پنل مدیریت */
    @Volatile
    var lastSource: String = ""
        private set

    /** شرح خطاهای آخرین دریافت — برای عیب‌یابی در پنل مدیریت */
    @Volatile
    var lastError: String = ""
        private set

    private fun stateFile(context: Context) = File(context.filesDir, STATE_FILE)

    /** آخرین زمانی که برای کاربر اعلان اطلاعیه ساخته شد */
    private fun lastNotifiedAt(context: Context): Long =
        runCatching { stateFile(context).readText().trim().toLong() }.getOrDefault(0L)

    private fun setLastNotifiedAt(context: Context, t: Long) {
        runCatching { stateFile(context).writeText(t.toString()) }
    }

    /** دریافت خوراک از گیت‌هاب (چندمنبعی + کش) — فهرست اطلاعیه‌ها؛ در خطا خالی */
    fun fetchFeed(cacheFile: File? = null): List<Announcement> {
        val r = FeedFetcher.fetch(API_REPO, API_PATH, cacheFile)
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        return if (r.ok) parseFeed(r.body) else emptyList()
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    /** تجزیهٔ JSON خوراک */
    fun parseFeed(json: String): List<Announcement> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val id = o.optLong("id", 0L)
            val title = o.optString("title").trim()
            if (id == 0L || title.isEmpty()) null
            else Announcement(
                id = id,
                type = if (o.optString("type") == "ad") "ad" else "news",
                title = title,
                body = o.optString("body"),
                link = o.optString("link"),
                image = o.optString("image"),
                on = o.optBoolean("on", true),
                at = o.optLong("at", 0L)
            )
        }
    }.getOrDefault(emptyList())

    /**
     * همگام‌سازی کاربر: خوراک خوانده می‌شود، اطلاعیه‌های تازه به فهرست محلی
     * (زنگ) اضافه می‌شوند و برای اطلاعیه‌های تازه‌تر از آخرین اعلان، اعلان
     * سیستمی ساخته می‌شود. خروجی: تعداد اطلاعیه‌های تازه (0 = چیزی نبود).
     * فقط از نخ پس‌زمینه صدا زده شود.
     */
    fun sync(context: Context): Int {
        val feed = runCatching { fetchFeed(cacheFile(context)) }.getOrElse { return 0 }
        if (feed.isEmpty()) return 0
        val fresh = AnnouncementBox.mergeFromFeed(context, feed)
        if (fresh.isEmpty()) return 0

        val candidates = fresh.filter { it.at > lastNotifiedAt(context) }
        if (candidates.isNotEmpty()) {
            val newest = candidates.maxByOrNull { it.at } ?: return 0
            ir.persiancalendar.app.notifications.NotificationHelper.notifyAnnouncement(
                context,
                title = if (candidates.size == 1) newest.title
                else "${newest.title} (+${candidates.size - 1} اطلاعیهٔ دیگر)",
                body = newest.body,
                link = newest.link
            )
            setLastNotifiedAt(context, newest.at)
        }
        return fresh.size
    }

    /**
     * انتشار فهرست اطلاعیه‌ها برای همهٔ کاربران — گیت‌هاب Contents API.
     * خروجی: پیام نتیجه (موفقیت یا متن خطا) برای نمایش در پنل مدیریت.
     * فقط از نخ پس‌زمینه صدا زده شود.
     */
    fun publish(context: Context, token: String, list: List<Announcement>): String {
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

        val bodyJson = JSONObject()
            .put("message", "اطلاعیه‌های بادخزان — انتشار از پنل مدیریت")
            .put(
                "content",
                Base64.encodeToString(payload.toString(2).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            )

        // ۱) اگر فایل از قبل هست، sha آن لازم است (وگرنه فایل ساخته می‌شود)
        val sha = runCatching {
            val conn = URL("https://api.github.com/repos/$API_REPO/contents/$API_PATH")
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
        val conn = URL("https://api.github.com/repos/$API_REPO/contents/$API_PATH")
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
                    FeedFetcher.purgeCdn(API_REPO, API_PATH)
                    val img = when {
                        embeddedImages > 0 && failedImages == 0 ->
                            " — تصویر $embeddedImages اطلاعیه جاسازی شد"
                        embeddedImages > 0 ->
                            " — تصویر $embeddedImages اطلاعیه جاسازی شد، $failedImages تصویر منتشر نشد"
                        else -> ""
                    }
                    "برای کاربران منتشر شد ✓$img — با باز کردن بعدی برنامه یا تیک روزانه، اعلان می‌گیرند"
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

    /** بررسی خوراک از سمت مدیر (تست) — تعداد اطلاعیه‌های منتشرشده را می‌خواند */
    fun checkFeed(context: Context? = null): Int =
        fetchFeed(context?.let { cacheFile(it) }).size
}
