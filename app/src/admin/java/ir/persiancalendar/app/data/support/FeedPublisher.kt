package ir.persiancalendar.app.data.support

import android.content.Context
import android.util.Base64
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
            payload = payload
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

    // ---------- انتشار بنرها (از MainAdFeed.publish — ۱.۱۱.۳۰ منتقل شد) ----------

    fun publishMainAds(context: Context, token: String, list: List<MainAd>): String {
        if (token.isBlank()) return "اول توکن گیت‌هاب را وارد و ذخیره کنید"

        val payload = JSONArray()
        var embeddedImages = 0
        var failedImages = 0
        list.filter { it.on }.forEach { a ->
            val image = when {
                a.image.startsWith("http") -> a.image
                a.image.isNotBlank() -> {
                    val uri = ImageEmbed.dataUriFromFile(a.image)
                    if (uri.isNotBlank()) embeddedImages++ else failedImages++
                    uri
                }
                else -> ""
            }
            payload.put(
                JSONObject()
                    .put("id", a.id)
                    .put("title", a.title)
                    .put("body", a.body)
                    .put("cta", a.cta)
                    .put("link", a.link)
                    .put("image", image)
                    .put("colorIndex", a.colorIndex)
                    .put("on", true)
                    .put("at", a.at)
            )
        }
        val msg = publishJson(
            token = token,
            path = "feed/main_ads.json",
            commitMessage = "بنرهای بادخزان — انتشار از پنل مدیریت",
            payload = payload
        )
        return if (msg.startsWith("✓")) {
            val img = when {
                embeddedImages > 0 && failedImages == 0 ->
                    " — تصویر $embeddedImages بنر داخل خوراک جاسازی شد"
                embeddedImages > 0 ->
                    " — تصویر $embeddedImages بنر جاسازی شد، $failedImages تصویر منتشر نشد"
                else -> ""
            }
            "بنرها برای همهٔ کاربران منتشر شد ✓ (${payload.length()} بنر)$img — امضای خوراک چند ثانیه بعد خودکار ساخته می‌شود"
        } else msg
    }

    /**
     * PUT فایل جیسون در مخزن (Contents API) + پاک‌کردن کش jsDelivr + راه‌اندازی
     * وورکفلوی امضا. پیام موفقیت با «✓» شروع می‌شود تا تماس‌گیرنده پیام تکمیلی بگذارد.
     */
    private fun publishJson(token: String, path: String, commitMessage: String, payload: JSONArray): String {
        val bodyJson = JSONObject()
            .put("message", commitMessage)
            .put(
                "content",
                Base64.encodeToString(payload.toString(2).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
