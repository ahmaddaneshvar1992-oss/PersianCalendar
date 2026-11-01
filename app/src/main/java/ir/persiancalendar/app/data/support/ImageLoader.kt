package ir.persiancalendar.app.data.support

import android.util.Base64
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
 *
 * نسخهٔ ۱.۱۱.۳۶: تصویر درون‌خطی data:image/{jpeg,png,webp};base64 هم decode
 * می‌شود (الگوی جاسازی اطلاعیه‌ها — عکس گالری مدیر بدون هاست بیرونی).
 * کاملاً محلی است: هیچ تماس شبکه‌ای در کار نیست و بایت‌ها فقط با
 * BitmapFactory (رستری) تفسیر می‌شوند؛ svg/xml/اسکریپت بی‌معنا هستند.
 * سقف maxBytes روی بایت‌های decodeشده هم اعتبار دارد.
 */
object ImageLoader {

    /** پیشوندهای مجاز — هم‌قرارداد با AdsFeedPolicy.DATA_IMAGE_PREFIXES */
    private val DATA_IMAGE_PREFIXES = listOf(
        "data:image/jpeg;base64,",
        "data:image/png;base64,",
        "data:image/webp;base64,"
    )

    /**
     * دانلود محدودشدهٔ تصویر — در صورت عبور از سقف یا خطا، null
     * (فراخوان بنر را پنهان می‌کند؛ هیچ جای خالی نمی‌ماند).
     */
    fun fetchBounded(url: String, maxBytes: Int): ByteArray? = runCatching {
        if (url.startsWith("data:image/")) return@runCatching decodeDataUri(url, maxBytes)
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

    /**
     * decode محلی data URI تصویر — فقط سه پیشوند رستری با base64 خالص
     * (بدون فاصله/پارامتر اضافه)؛ سقف بایت و خطای decode = null.
     */
    private fun decodeDataUri(url: String, maxBytes: Int): ByteArray? {
        val prefix = DATA_IMAGE_PREFIXES.firstOrNull { url.startsWith(it) } ?: return null
        val b64 = url.substring(prefix.length)
        if (b64.isEmpty() || b64.any { it.isWhitespace() }) return null
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        return if (bytes.isNotEmpty() && bytes.size <= maxBytes) bytes else null
    }
}
