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
 *
 * نسخهٔ ۱.۱۱.۳۰ (بازرسی امنیتی: «فید بدون امضاست») — هر منبع فقط وقتی پذیرفته
 * می‌شود که امضای Ed25519 فایل (فایل .sig کنار آن) با کلید عمومی داخل اپ درست
 * باشد؛ منبع بدون امضا یا با امضای نامعتبر رد و منبع بعدی آزموده می‌شود و در
 * کش محلی هم فقط محتوای تأییدشده ذخیره می‌شود. جزئیات: FeedSecurity.
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
        val error: String,    // شرح خطای منبع‌ها — برای عیب‌یابی
        val fromCache: Boolean = false // نسخهٔ ۱.۱۱.۲۶: true = از کش محلی آمده (نه منبع زنده)؛
        // در این حالت همگام‌سازی چیزی اضافه/حذف نمی‌کند تا محتوای آفلاین
        // اشتباهی به‌عنوان فهرست رسمی مدیر تفسیر شود
    )

    /**
     * نتیجهٔ ساختارمند «بررسی خوراک» پنل مدیریت — نسخهٔ ۱.۱۱.۲۷.
     * (گزارش کاربر: «میگه دریافت خوراک ناموفق») تا این نسخه خوراکِ خالی —
     * که بعد از حذف همهٔ اطلاعیه‌ها/بنرها و انتشار، حالت درست است — با
     * «دریافت ناموفق» اشتباه گرفته می‌شد. اکنون چهار حالت از هم جدا می‌شوند:
     * خوراک پر، خوراک خالیِ موفق، نسخهٔ کش‌شدهٔ آفلاین و دریافت ناموفق واقعی.
     */
    data class CheckResult(
        val ok: Boolean,        // دریافت موفق (از منبع زنده یا کش)
        val count: Int,         // تعداد اقلام معتبر خوراک (۰ = خوراک خالی)
        val source: String,     // نام منبع موفق
        val fromCache: Boolean, // true = از کش محلی، نه منبع زنده
        val error: String       // شرح خطا وقتی ok=false (و جزئیات تلاش‌ها در حالت کش)
    )

    /**
     * دریافت فایل خوراک از چند منبع. در موفقیت، متن در فایل cache ذخیره
     * می‌شود تا در قطعی‌های بعدی هم چیزی برای نمایش باشد.
     *
     * نسخهٔ ۱.۱۱.۲۷ — پارامتر jsonArray: خوراک اطلاعیه/بنر همیشه آرایهٔ JSON
     * است؛ اگر منبعی (مثلاً به‌خاطر پورتال captive یا صفحهٔ خطای HTML با کد ۲۰۰)
     * پاسخ غیرآرایه بدهد، آن منبع رد می‌شود و منبع بعدی آزموده می‌شود. پیش از این
     * چنین پاسخی «موفق» شمرده و حتی کش محلی را با HTML آلوده می‌کرد.
     *
     * نسخهٔ ۱.۱۱.۳۰ — تأیید امضای Ed25519 هر منبع پیش از پذیرش (FeedSecurity).
     */
    fun fetch(repo: String, path: String, cache: File?, jsonArray: Boolean = false): Result {
        val sources = mutableListOf<Pair<String, String>>()
        val base = serverBase(repo, cache)
        if (base.isNotBlank()) sources.add("سرور شخصی" to "$base/$path")
        sources.add("raw.githubusercontent.com" to "https://raw.githubusercontent.com/$repo/main/$path")
        sources.add("jsDelivr CDN" to "https://cdn.jsdelivr.net/gh/$repo@main/$path")
        sources.add("api.github.com" to "https://api.github.com/repos/$repo/contents/$path")
        val errors = StringBuilder()
        for ((name, url) in sources) {
            val res = runCatching { fetchSignedBody(url) }
            val body = res.getOrNull()
            if (body == null) {
                errors.append(name).append(": ")
                    .append(res.exceptionOrNull()?.message ?: "پاسخ خالی")
                    .append(" | ")
                continue
            }
            val healthy = body.isNotBlank() && (!jsonArray || body.trim().startsWith("["))
            if (healthy) {
                if (cache != null) runCatching { cache.writeText(body) }
                return Result(true, body, name, "")
            }
            errors.append(name).append(": ")
                .append(if (body.isBlank()) "محتوا تجزیه نشد" else "پاسخ آرایهٔ JSON نبود")
                .append(" | ")
        }
        // ۴) کش محلی — آخرین دریافت موفق (همیشه نسخهٔ تأییدشدهٔ امضایی است)
        if (cache != null) {
            val cached = runCatching { cache.readText() }.getOrDefault("")
            if (cached.isNotBlank())
                return Result(true, cached, "کش محلی (آخرین دریافت موفق)", errors.toString(), true)
        }
        return Result(false, "", "", errors.toString())
    }

    /**
     * نسخهٔ ۱.۱۱.۳۰ — دریافت فایل و امضای .sig آن از «همان یک منبع» و تأیید
     * Ed25519 روی بایت‌های دقیق فایل. اگر امضا نباشد یا نامعتبر باشد IOException
     * با شرح دقیق پرتاب می‌شود تا منبع بعدی آزموده شود. برای منبع api.github.com
     * هر دو فایل با Contents API خوانده و از کدگذاری Base64 دیکد می‌شوند.
     */
    private fun fetchSignedBody(url: String): String {
        val isApi = url.contains("api.github.com")
        val rawBytes = httpGetBytes(url)
        val contentBytes = if (isApi) {
            // پاسخ Contents API: JSON با فیلد content به صورت Base64
            val b64 = JSONObject(String(rawBytes, Charsets.UTF_8)).optString("content")
                .replace("\n", "").replace("\r", "")
            Base64.decode(b64, Base64.DEFAULT)
        } else rawBytes
        val body = String(contentBytes, Charsets.UTF_8)
        val sigB64 = runCatching {
            val sigRaw = httpGetBytes("$url.sig")
            if (isApi) {
                val b64 = JSONObject(String(sigRaw, Charsets.UTF_8)).optString("content")
                    .replace("\n", "").replace("\r", "")
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            } else String(sigRaw, Charsets.UTF_8)
        }.getOrElse {
            throw java.io.IOException("فایل امضا (.sig) کنار خوراک خوانده نشد")
        }
        if (!FeedSecurity.verify(contentBytes, sigB64))
            throw java.io.IOException("امضای Ed25519 معتبر نیست (فایل بی‌امضا یا جعلی)")
        return body
    }

    /**
     * آدرس میزبان شخصی خوراک از feed/config.json مخزن خوانده می‌شود (منبع صفر).
     * تا وقتی feedBaseUrl خالی باشد فقط منابع گیت‌هاب استفاده می‌شوند؛ وقتی مدیر
     * آدرس سرور خود را ثبت کند، اپ‌های ۱.۱۱.۲۴ به بعد بدون به‌روزرسانی به سرور او
     * می‌روند و گیت‌هاب فقط پشتیبان می‌ماند. نتیجه در حافظه (۳۰ دقیقه) و روی
     * دستگاه کش می‌شود تا در قطعی گیت‌هاب هم آخرین آدرس شناخته‌شده به کار رود.
     *
     * نسخهٔ ۱.۱۱.۳۰ — تنظیمات سرور هم مثل خوراک‌ها باید امضای معتبر داشته باشد؛
     * منبعی که config.json بدون امضا یا با امضای جعلی بدهد رد می‌شود.
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
            val text = runCatching { fetchSignedBody(url) }.getOrNull()
            if (!text.isNullOrBlank()) {
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

    /**
     * اعتبارسنجی آدرس سرور: فقط https و بدون / انتهایی
     * (نسخهٔ ۱.۱۱.۲۹: http حذف شد — با networkSecurityConfig کلیرتکست مسدود است؛
     * برای سرور شخصی گواهی رایگان Let's Encrypt بگیرید)
     */
    private fun parseBase(json: String): String = runCatching {
        var b = JSONObject(json).optString("feedBaseUrl").trim()
        if (b.endsWith("/")) b = b.dropLast(1)
        if (b.startsWith("https://")) b else ""
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

    private fun httpGetBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            val code = conn.responseCode
            // نسخهٔ ۱.۱۱.۲۷: شرح کدهای پرتکرار برای عیب‌یابی سریع در پنل مدیریت
            if (code != 200) throw java.io.IOException(
                when (code) {
                    404 -> "کد ۴۰۴ (فایل در این منبع نیست)"
                    403 -> "کد ۴۰۳ (محدودیت یا مسدودسازی)"
                    else -> "کد $code"
                }
            )
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
