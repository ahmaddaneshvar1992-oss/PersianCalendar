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
 *
 * نسخهٔ ۱.۱۱.۲۶ (گزارش کاربر: «اطلاعیه که میفرستم برا کاربرا بعد که حذف
 * میکنم از پنل مدیریت واسه کاربرا حذف نمیشه»):
 * تا این نسخه همگام‌سازی فقط «اضافه» می‌کرد؛ اطلاعیه‌ای که مدیر از خوراک
 * حذف می‌کرد تا همیشه روی دستگاه کاربر می‌ماند و اگر خوراک کامل خالی
 * می‌شد، همگام‌سازی همان اول خارج می‌شد و هیچ حذفی انجام نمی‌شد.
 * از این نسخه همگام‌سازی مثل بنرها دوطرفه است: اطلاعیه‌های مدیریت‌شدهٔ
 * خوراک که دیگر در خوراک نیستند (حتی وقتی خوراک کلاً خالی شده) از
 * دستگاه کاربر هم برداشته می‌شوند؛ فقط در دریافت موفق از منبع زنده —
 * نه از کش آفلاین — و اطلاعیه‌های محلیِ خود کاربر/مدیر دست‌نخورده می‌مانند.
 */
object AnnouncementFeed {

    /** خوراک اطلاعیه — فایل JSON روی شاخهٔ main همان مخزن برنامه */
    const val FEED_URL =
        "https://raw.githubusercontent.com/ahmaddaneshvar1992-oss/PersianCalendar/main/feed/announcements.json"

    private const val API_REPO = "ahmaddaneshvar1992-oss/PersianCalendar"
    private const val API_PATH = "feed/announcements.json"
    private const val STATE_FILE = "announcement_feed_state.txt"
    private const val CACHE_FILE = "announcements_cache.json"
    private const val REMOTE_IDS_FILE = "announcement_feed_ids.txt"

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
        val r = FeedFetcher.fetch(API_REPO, API_PATH, cacheFile, jsonArray = true)
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        return if (r.ok) parseFeed(r.body) else emptyList()
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun remoteIdsFile(context: Context) = File(context.filesDir, REMOTE_IDS_FILE)

    /** شناسه‌هایی که از خوراک آمده‌اند — فقط این‌ها با خوراک مدیریت می‌شوند (۱.۱۱.۲۶، همان الگوی بنرها) */
    private fun loadRemoteIds(context: Context): MutableSet<Long> = runCatching {
        remoteIdsFile(context).readText().trim()
            .split(",").filter { it.isNotBlank() }.map { it.toLong() }.toMutableSet()
    }.getOrDefault(mutableSetOf())

    private fun saveRemoteIds(context: Context, ids: Set<Long>) {
        runCatching { remoteIdsFile(context).writeText(ids.joinToString(",")) }
    }

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
     * همگام‌سازی کاربر — نسخهٔ ۱.۱۱.۲۶ دوطرفه شد:
     *  ۱) اطلاعیه‌های تازهٔ خوراک به فهرست محلی (زنگ) اضافه می‌شوند؛
     *  ۲) اطلاعیه‌های مدیریت‌شدهٔ قبلی اگر در خوراک عوض شده باشند به‌روز می‌شوند؛
     *  ۳) اطلاعیه‌های مدیریت‌شده‌ای که از خوراک حذف شده‌اند — حتی وقتی مدیر
     *     همهٔ اطلاعیه‌ها را حذف کرده و خوراک خالی [] است — از دستگاه کاربر
     *     هم برداشته می‌شوند (گزارش کاربر: «حذف میکنم از پنل واسه کاربرا
     *     حذف نمیشه»). اطلاعیه‌های محلیِ خود دستگاه دست‌نخورده می‌مانند.
     * حذف/افزودن فقط با دریافت موفق از منبع زنده انجام می‌شود؛ در فالبک کش
     * آفلاین کاری انجام نمی‌شود تا محتوای قدیمی، فهرست رسمی مدیر تلقی نشود.
     * برای اطلاعیه‌های تازه‌تر از آخرین اعلان، اعلان سیستمی ساخته می‌شود.
     *
     * نسخهٔ ۱.۱۱.۲۸ (گزارش کاربر: «اطلاعیه رو برا کاربرا میفرستم وقتی پاک
     * میکنم واسه کاربرها پاک میشه چک کن»): دو رفع تکمیلی —
     *  ۱) خروجی از «تعداد تازه‌ها» به «کل تغییرها» (افزودن/به‌روزرسانی/حذف)
     *     تغییر کرد؛ قبلاً بعد از حذف همگانی (بدون اطلاعیهٔ تازه) خروجی صفر
     *     بود و صفحهٔ اصلی کاربر فهرست کهنه را نشان می‌داد تا دفعهٔ بعد که
     *     شیت اطلاعیه یا تنظیمات باز می‌شد.
     *  ۲) اگر اعلان سینی سیستم (نوار وضعیت) متعلق به اطلاعیه‌ای باشد که الان
     *     از خوراک حذف شده و اطلاعیهٔ جایگزینی باقی نمانده، همان اعلان هم از
     *     نوار وضعیت کاربر برداشته می‌شود.
     * خروجی: تعداد کل تغییرها (0 = چیزی عوض نشد). فقط از نخ پس‌زمینه صدا زده شود.
     */
    fun sync(context: Context): Int {
        val r = runCatching {
            FeedFetcher.fetch(API_REPO, API_PATH, cacheFile(context), jsonArray = true)
        }.getOrElse { return 0 }
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        if (!r.ok || r.fromCache) return 0
        // خوراک همیشه آرایهٔ JSON است؛ اگر منبعی پاسخ نامعتبر (مثلاً HTML)
        // داد، به‌عنوان دریافت ناموفق در نظر می‌گیریم تا اطلاعیه‌ها اشتباهی پاک نشوند
        if (!r.body.trim().startsWith("[")) return 0
        val feed = parseFeed(r.body)

        val all = AnnouncementBox.load(context).toMutableList()
        val remoteIds = loadRemoteIds(context)
        var changes = 0
        val fresh = mutableListOf<Announcement>()

        // ۱) اطلاعیهٔ تازه ← افزودن | اطلاعیهٔ مدیریت‌شدهٔ موجود ← به‌روزرسانی
        feed.forEach { f ->
            val idx = all.indexOfFirst { it.id == f.id }
            if (idx < 0) {
                all.add(f)
                remoteIds.add(f.id)
                fresh.add(f)
                changes++
            } else if (f.id in remoteIds && all[idx] != f) {
                all[idx] = f
                changes++
            }
        }

        // ۲) اطلاعیه‌های مدیریت‌شده که از خوراک حذف شده‌اند ← برداشتن از دستگاه
        //    (وقتی خوراک کامل خالی است یعنی همهٔ اطلاعیه‌های منتشرشده باید بروند)
        val feedIds = feed.map { it.id }.toHashSet()
        val gone = remoteIds.filterNot { it in feedIds }
        val lastNotified = lastNotifiedAt(context)
        // نسخهٔ ۱.۱۱.۲۸: آیا اعلان سینی سیستم متعلق به اطلاعیه‌ای است که الان
        // حذف می‌شود؟ (اعلان سینی همیشه آخرین اطلاعیهٔ اعلان‌شده است؛ اگر همان
        // حذف شده باشد و اطلاعیهٔ هم‌سن یا تازه‌تری باقی نمانده، اعلان هم برداشته می‌شود)
        val trayGoes = gone.isNotEmpty() &&
            all.any { it.id in gone && it.at >= lastNotified } &&
            all.none { it.id !in gone && it.at >= lastNotified }
        if (gone.isNotEmpty()) {
            all.removeAll { it.id in gone }
            remoteIds.removeAll(gone.toSet())
            changes += gone.size
        }

        if (changes > 0) {
            AnnouncementBox.save(context, all)
            saveRemoteIds(context, remoteIds)
        }

        // ۳) نسخهٔ ۱.۱۱.۲۸: برداشتن اعلان سینی متعلق به اطلاعیهٔ حذف‌شده
        if (trayGoes) runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(ir.persiancalendar.app.notifications.NotificationHelper.NOTIF_ID_ANNOUNCE)
        }

        // ۴) اعلان سیستمی برای اطلاعیه‌های تازه (منطق قبلی)
        if (fresh.isNotEmpty()) {
            val candidates = fresh.filter { it.at > lastNotified }
            if (candidates.isNotEmpty()) {
                val newest = candidates.maxByOrNull { it.at }
                if (newest != null) {
                    ir.persiancalendar.app.notifications.NotificationHelper.notifyAnnouncement(
                        context,
                        title = if (candidates.size == 1) newest.title
                        else "${newest.title} (+${candidates.size - 1} اطلاعیهٔ دیگر)",
                        body = newest.body,
                        link = newest.link
                    )
                    setLastNotifiedAt(context, newest.at)
                }
            }
        }
        // نسخهٔ ۱.۱۱.۲۸: کل تغییرها برگردانده می‌شود تا رابط کاربری پس از حذف
        // همگانی هم بلافاصله تازه شود (قبلاً فقط تازه‌ها شمرده می‌شد)
        return changes
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

    /**
     * بررسی خوراک از سمت مدیر (تست) — نتیجهٔ ساختارمند برای نمایش دقیق در پنل.
     * نسخهٔ ۱.۱۱.۲۷ (گزارش کاربر: «میگه دریافت خوراک ناموفق»): تا این نسخه
     * خوراکِ خالی با دریافت ناموفق اشتباه گرفته می‌شد — یعنی بعد از حذف همهٔ
     * اطلاعیه‌ها و انتشارِ فهرست خالی [] (رفتار درست از ۱.۱۱.۲۶)، دکمهٔ بررسی
     * پیام «دریافت خوراک ناموفق» می‌داد و مدیر فکر می‌کرد چیزی شکسته است.
     * اکنون چهار حالت جداگانه گزارش می‌شود: خوراک پر / خوراک خالیِ موفق /
     * نسخهٔ کش‌شدهٔ آفلاین / دریافت ناموفق واقعی با جزئیات منابع.
     */
    fun check(context: Context? = null): FeedFetcher.CheckResult {
        val r = FeedFetcher.fetch(API_REPO, API_PATH, context?.let { cacheFile(it) }, jsonArray = true)
        lastSource = if (r.ok) r.source else ""
        lastError = r.error
        val n = if (r.ok) parseFeed(r.body).size else 0
        return FeedFetcher.CheckResult(
            ok = r.ok, count = n, source = r.source,
            fromCache = r.fromCache, error = r.error
        )
    }
}
