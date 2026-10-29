package ir.persiancalendar.app.data.support

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * توزیع همگانی بنرهای تبلیغات صفحه اصلی — نسخه ۱.۱۱.۲۱
 * (درخواست کاربر: «بعد از انتشار چطور پنل تبلیغات را مدیریت کنم؟»)
 *
 * تا این نسخه بنرهای «پنل مدیریت ← تبلیغات صفحه اصلی» فقط روی دستگاهِ مدیر
 * ذخیره می‌شدند و کاربران نمی‌دیدند. از این نسخه بنرها مثل اطلاعیه‌ها روی
 * مخزن گیت‌هاب منتشر می‌شوند (feed/main_ads.json) و همهٔ کاربران در باز شدن
 * برنامه آن را می‌گیرند.
 *
 *  - مدیر: پنل مدیریت ← «انتشار برای کاربران» ← «انتشار بنرهای صفحه اصلی»
 *    (با همان توکن گیت‌هاب اطلاعیه‌ها — فقط روی دستگاه مدیر)
 *  - کاربران: در هر باز شدن برنامه خوراک بررسی می‌شود؛ بنر تازه اضافه،
 *    بنرِ به‌روزشده به‌روز و بنرِ حذف‌شده از خوراک، از دستگاه کاربر هم حذف
 *    می‌شود (کنترل کامل مدیر با روشن/خاموش و حذف).
 *
 * تصویر بنر: اگر URL اینترنتی باشد همان‌طور منتشر می‌شود؛ اگر از گالری
 * انتخاب شده باشد (فایل داخلی) تا ۲۰۰ کیلوبایت به data URI تبدیل و داخل
 * خود خوراک جاسازی می‌شود تا همهٔ کاربران عکس را ببینند.
 */
object MainAdFeed {

    /** خوراک بنرها — فایل JSON روی شاخهٔ main همان مخزن برنامه */
    const val FEED_URL =
        "https://raw.githubusercontent.com/ahmaddaneshvar1992-oss/PersianCalendar/main/feed/main_ads.json"

    private const val API_REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
    private const val API_PATH = "feed/main_ads.json"
    private const val REMOTE_IDS_FILE = "main_ads_feed_ids.txt"

    /** سقف حجم عکس گالری برای جاسازی در خوراک (بایت) */
    private const val MAX_IMAGE_BYTES = 200_000

    private fun remoteIdsFile(context: Context) = File(context.filesDir, REMOTE_IDS_FILE)

    /** شناسه‌هایی که از خوراک آمده‌اند — فقط این‌ها با خوراک مدیریت می‌شوند */
    private fun loadRemoteIds(context: Context): MutableSet<Long> = runCatching {
        remoteIdsFile(context).readText().trim()
            .split(",").filter { it.isNotBlank() }.map { it.toLong() }.toMutableSet()
    }.getOrDefault(mutableSetOf())

    private fun saveRemoteIds(context: Context, ids: Set<Long>) {
        runCatching { remoteIdsFile(context).writeText(ids.joinToString(",")) }
    }

    /** دریافت خوراک از گیت‌هاب — در خطا فهرست خالی برمی‌گردد */
    fun fetchFeed(): List<MainAd> {
        val conn = URL(FEED_URL).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode != 200) emptyList()
            else {
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                parseFeed(body)
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /** تجزیهٔ JSON خوراک بنرها */
    fun parseFeed(json: String): List<MainAd> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val id = o.optLong("id", 0L)
            val title = o.optString("title").trim()
            if (id == 0L || title.isEmpty()) null
            else MainAd(
                id = id,
                title = title,
                body = o.optString("body"),
                cta = o.optString("cta", "مشاهده"),
                link = o.optString("link"),
                image = o.optString("image"),
                colorIndex = o.optInt("colorIndex", 0).coerceIn(0, 7),
                on = true,
                at = o.optLong("at", 0L)
            )
        }
    }.getOrDefault(emptyList())

    /**
     * همگام‌سازی کاربر: بنرهای تازه اضافه، بنرهای مدیریت‌شدهٔ قبلی به‌روز و
     * بنرهایی که از خوراک حذف شده‌اند (مدیر خاموش/حذف کرده) برداشته می‌شوند.
     * بنرهایی که کاربر/مدیر خودش محلی ساخته دست‌نخورده می‌مانند.
     * خروجی: تعداد تغییرات (0 = چیزی عوض نشد). فقط از نخ پس‌زمینه صدا زده شود.
     */
    fun sync(context: Context): Int {
        val feed = runCatching { fetchFeed() }.getOrElse { return 0 }
        if (feed.isEmpty()) return 0

        val all = MainAdBox.load(context).toMutableList()
        val remoteIds = loadRemoteIds(context)
        var changes = 0

        // ۱) بنر تازه ← افزودن | بنر مدیریت‌شدهٔ موجود ← به‌روزرسانی
        feed.forEach { f ->
            val idx = all.indexOfFirst { it.id == f.id }
            if (idx < 0) {
                all.add(f)
                remoteIds.add(f.id)
                changes++
            } else if (f.id in remoteIds && all[idx] != f) {
                all[idx] = f
                changes++
            }
        }

        // ۲) بنرهای مدیریت‌شده که از خوراک حذف شده‌اند ← برداشتن از دستگاه
        val feedIds = feed.map { it.id }.toHashSet()
        val gone = remoteIds.filterNot { it in feedIds }
        if (gone.isNotEmpty()) {
            all.removeAll { it.id in gone }
            remoteIds.removeAll(gone.toSet())
            changes += gone.size
        }

        if (changes > 0) {
            MainAdBox.save(context, all)
            saveRemoteIds(context, remoteIds)
        }
        return changes
    }

    /** عکس فایل داخلی ← data URI (تا ۲۰۰ کیلوبایت)؛ در غیر این صورت خالی */
    private fun localImageAsDataUri(path: String): String = runCatching {
        val f = File(path)
        if (!f.exists() || f.length() > MAX_IMAGE_BYTES) return ""
        "data:image/jpeg;base64," +
                Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }.getOrDefault("")

    /**
     * انتشار فهرست بنرها برای همهٔ کاربران — گیت‌هاب Contents API.
     * خروجی: پیام نتیجه برای نمایش در پنل مدیریت. فقط از نخ پس‌زمینه صدا زده شود.
     */
    fun publish(context: Context, token: String, list: List<MainAd>): String {
        if (token.isBlank()) return "اول توکن گیت‌هاب را وارد و ذخیره کنید"

        val payload = JSONArray()
        list.filter { it.on }.forEach { a ->
            val image = when {
                a.image.startsWith("http") -> a.image
                a.image.isNotBlank() -> localImageAsDataUri(a.image)
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

        val bodyJson = JSONObject()
            .put("message", "بنرهای بادخزان — انتشار از پنل مدیریت")
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
                in 200..299 ->
                    "بنرها برای همهٔ کاربران منتشر شد ✓ (${payload.length()} بنر) — با باز کردن بعدی برنامه می‌بینند"
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

    /** بررسی خوراک از سمت مدیر (تست) — تعداد بنرهای منتشرشده را می‌خواند */
    fun checkFeed(): Int = fetchFeed().size
}
