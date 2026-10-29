package ir.persiancalendar.app.data.support

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * دریافت مقاوم خوراک‌ها از چند منبع — نسخهٔ ۱.۱۱.۲۲
 *
 * مشکل گزارش‌شده (بازرسی): انتشار از پنل مدیریت روی مخزن درست کار می‌کند
 * (کامیت‌های «انتشار از پنل مدیریت» روی مخزن ثبت می‌شوند) اما بعضی کاربران
 * خوراک را نمی‌گیرند. علت، وابستگی دریافت به تنها منبع raw.githubusercontent.com
 * است که در بعضی شبکه‌های ایران (اینترنت ملی، DNS، قطعی‌ها) پایدار نیست.
 *
 * اکنون منابع به‌ترتیب آزموده می‌شوند و اولین پاسخ سالم برمی‌گردد:
 *
 *  ۱) raw.githubusercontent.com — همیشه تازه‌ترین (کش CDN حدود ۵ دقیقه)
 *  ۲) cdn.jsdelivr.net — آینهٔ گیت‌هاب که در اکثر شبکه‌های ایران باز است
 *     (کش تا ۱۲ ساعت — یعنی حداکثر چند ساعت تأخیر در رسیدن تغییرها)
 *  ۳) api.github.com — خود API گیت‌هاب بدون توکن (سقف ۶۰ درخواست در ساعت)
 *  ۴) کش محلی — آخرین دریافت موفق روی دستگاه، تا کاربر در قطعی شبکه هم
 *     بنرها/اطلاعیه‌های قبلی را از دست ندهد
 */
object FeedFetcher {

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
        val sources = listOf(
            "raw.githubusercontent.com" to "https://raw.githubusercontent.com/$repo/main/$path",
            "jsDelivr CDN" to "https://cdn.jsdelivr.net/gh/$repo@main/$path",
            "api.github.com" to "https://api.github.com/repos/$repo/contents/$path"
        )
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
