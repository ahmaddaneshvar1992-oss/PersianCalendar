package ir.persiancalendar.app.data.support

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * دریافت مقاوم خوراک‌ها از چند منبع — نسخهٔ ۱.۱۱.۲۲، سرور شخصی از ۱.۱۱.۲۴
 *
 * مشکل گزارش‌شده (بازرسی): انتشار از پنل مدیریت روی مخزن درست کار می‌کند
 * (کامیت‌های «انتشار از پنل مدیریت» روی مخزن ثبت می‌شوند) اما بعضی کاربران
 * خوراک را نمی‌گیرند. علت، وابستگی دریافت به تنها منبع raw.githubusercontent.com
 * است که در بعضی شبکه‌های ایران (اینترنت ملی، DNS، قطعی‌ها) پایدار نیست.
 *
 * اکنون منابع به‌ترتیب آزموده می‌شوند و اولین پاسخ سالم برمی‌گردد:
 *
 *  ۰) سرور شخصی — اگر مدیر آدرس سرور خود را در feed/config.json مخزن ثبت
 *     کرده باشد (فیلد feedBaseUrl)، اپ اول از همان سرور می‌گیرد؛ با این کار
 *     بدون به‌روزرسانی اپ می‌توان میزبان خوراک را عوض کرد. اگر سرور در دسترس
 *     نباشد به‌صورت خودکار به منابع گیت‌هاب برمی‌گردیم.
 *  ۱) raw.githubusercontent.com — همیشه تازه‌ترین (کش CDN حدود ۵ دقیقه)
 *  ۲) cdn.jsdelivr.net — آینهٔ گیت‌هاب که در اکثر شبکه‌های ایران باز است
 *     (کش تا ۱۲ ساعت — یعنی حداکثر چند ساعت تأخیر در رسیدن تغییرها)
 *  ۳) api.github.com — خود API گیت‌هاب بدون توکن (سقف ۶۰ درخواست در ساعت)
 *  ۴) کش محلی — آخرین دریافت موفق روی دستگاه، تا کاربر در قطعی شبکه هم
 *     بنرها/اطلاعیه‌های قبلی را از دست ندهد
 */
object FeedFetcher {

    /** مسیر فایل تنظیمات میزبان خوراک در مخزن */
    private const val CONFIG_PATH = "feed/config.json"

    /** عمر اعتبار تنظیمات در حافظه — پس از آن یک‌بار دیگر خوانده می‌شود */
    private const val CONFIG_TTL_MS = 30 * 60 * 1000L

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedBaseAt = 0L

    data class Result(
        val ok: Boolean,      // آیا محتوایی به دست آمد
        val body: String,     // متن JSON خوراک
        val source: String,   // نام منبع موفق — برای نمایش در پنل مدیریت
        val error: String     // شرح خطای منبع‌ها — برای عیب‌یابی
    )

    /**
     * دریافت فایل خوراک از چند منبع. در موفقیت، متن در فایل cache ذخیره
     * می‌شود تا در قطعی‌های بعدی هم چیزی برای نمایش باشد.
     */
    fun fetch(repo: String, path: String, cache: File?): Result {
        val sources = mutableListOf<Pair<String, String>>()
        val base = serverBase(repo, cache)
        if (base.isNotBlank()) sources.add("سرور شخصی" to "$base/$path")
        sources.add("raw.githubusercontent.com" to "https://raw.githubusercontent.com/$repo/main/$path")
        sources.add("jsDelivr CDN" to "https://cdn.jsdelivr.net/gh/$repo@main/$path")
        sources.add("api.github.com" to "https://api.github.com/repos/$repo/contents/$path")
        val errors = StringBuilder()
        for ((name, url) in sources) {
            val res = runCatching { httpGet(url) }
            val raw = res.getOrNull()
            if (raw.isNullOrBlank()) {
                errors.append(name).append(": ")
                    .append(res.exceptionOrNull()?.message ?: "پاسخ خالی")
                    .append(" | ")
                continue
            }
            val body = if (url.contains("api.github.com")) decodeContents(raw) else raw
            if (body.isNotBlank()) {
                if (cache != null) runCatching { cache.writeText(body) }
                return Result(true, body, name, "")
            }
            errors.append(name).append(": محتوا تجزیه نشد | ")
        }
        // ۴) کش محلی — آخرین دریافت موفق
        if (cache != null) {
            val cached = runCatching { cache.readText() }.getOrDefault("")
            if (cached.isNotBlank())
                return Result(true, cached, "کش محلی (آخرین دریافت موفق)", errors.toString())
        }
        return Result(false, "", "", errors.toString())
    }

    /**
     * آدرس میزبان شخصی خوراک از feed/config.json مخزن خوانده می‌شود (منبع صفر).
     * تا وقتی feedBaseUrl خالی باشد فقط منابع گیت‌هاب استفاده می‌شوند؛ وقتی مدیر
     * آدرس سرور خود را ثبت کند، اپ‌های ۱.۱۱.۲۴ به بعد بدون به‌روزرسانی به سرور او
     * می‌روند و گیت‌هاب فقط پشتیبان می‌ماند. نتیجه در حافظه (۳۰ دقیقه) و روی
     * دستگاه کش می‌شود تا در قطعی گیت‌هاب هم آخرین آدرس شناخته‌شده به کار رود.
     */
    private fun serverBase(repo: String, cache: File?): String {
        val now = System.currentTimeMillis()
        cachedBase?.let { if (now - cachedBaseAt < CONFIG_TTL_MS) return it }
        val cfgFile = cache?.parentFile?.let { File(it, "feed_config_cache.json") }
        var base = ""
        val urls = listOf(
            "https://raw.githubusercontent.com/$repo/main/$CONFIG_PATH",
            "https://cdn.jsdelivr.net/gh/$repo@main/$CONFIG_PATH",
            "https://api.github.com/repos/$repo/contents/$CONFIG_PATH"
        )
        for (url in urls) {
            val raw = runCatching { httpGet(url) }.getOrNull()
            if (raw.isNullOrBlank()) continue
            val text = if (url.contains("api.github.com")) decodeContents(raw) else raw
            if (text.isNotBlank()) {
                if (cfgFile != null) runCatching { cfgFile.writeText(text) }
                base = parseBase(text)
                break
            }
        }
        if (base.isBlank() && cfgFile != null)
            base = runCatching { parseBase(cfgFile.readText()) }.getOrDefault("")
        cachedBase = base
        cachedBaseAt = now
        return base
    }

    /** اعتبارسنجی آدرس سرور: فقط http/https و بدون / انتهایی */
    private fun parseBase(json: String): String = runCatching {
        var b = JSONObject(json).optString("feedBaseUrl").trim()
        if (b.endsWith("/")) b = b.dropLast(1)
        if (b.startsWith("https://") || b.startsWith("http://")) b else ""
    }.getOrDefault("")

    /**
     * پاک کردن کش CDN جی‌اس‌دلیور پس از انتشار (نسخهٔ ۱.۱۱.۲۵).
     * علت: آینهٔ jsDelivr فایل شاخهٔ main را تا ۱۲ ساعت کش می‌کند؛ بدون پاکسازی،
     * کاربرانی که از این منبع می‌گیرند تا نیم‌روز آگهی‌های قدیمی می‌دیدند.
     * در خطا بی‌صدا رد می‌شود — انتشار خود مانع نمی‌شود.
     */
    fun purgeCdn(repo: String, path: String) {
        runCatching {
            val conn = URL("https://purge.jsdelivr.net/").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.outputStream.use {
                    it.write("""{"path":["$repo@main/$path"]}""".toByteArray(Charsets.UTF_8))
                }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("کد $code")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    /** پاسخ Contents API گیت‌هاب: JSON با فیلد content به صورت Base64 */
    private fun decodeContents(json: String): String = runCatching {
        val b64 = JSONObject(json).optString("content")
            .replace("\n", "").replace("\r", "")
        String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrDefault("")
}
