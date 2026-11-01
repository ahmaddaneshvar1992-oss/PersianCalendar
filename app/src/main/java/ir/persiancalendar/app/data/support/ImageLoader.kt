package ir.persiancalendar.app.data.support

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * لودر تصویر بنر با محدودیت اندازه — نسخهٔ ۱.۱۱.۳۴ (الزام امنیتی کاربر)
 *
 * «تصویر با کش و محدودیت اندازه بارگذاری شود»: فقط https، سقف بایت‌های
 * دانلود (Content-Length پیش از خواندن + شمارش increments در حین خواندن)،
 * خواندن روی نخ پس‌زمینه توسط فراخوان (produceState). هیچ کوکی/شناسه‌ای
 * نفرستاده می‌شود و هیچ داده‌ای از کاربر در درخواست نیست — فقط GET ساده.
 */
object ImageLoader {

    /**
     * دانلود محدودشدهٔ تصویر — در صورت عبور از سقف یا خطا، null
     * (فراخوان بنر را پنهان می‌کند؛ هیچ جای خالی نمی‌ماند).
     */
    fun fetchBounded(url: String, maxBytes: Int): ByteArray? = runCatching {
        if (!url.startsWith("https://")) return null
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) return null
            val declared = conn.contentLengthLong // -1 = نامعلوم
            if (declared > maxBytes) return null
            val stream: InputStream = conn.inputStream
            val initial = minOf(declared.takeIf { it > 0 } ?: 64_000L, maxBytes.toLong()).toInt()
            val out = ByteArrayOutputStream(initial)
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
