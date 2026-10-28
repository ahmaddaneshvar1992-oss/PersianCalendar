package ir.persiancalendar.app.core.premium

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * سیستم کد فعال‌سازی عضویت ویژه (آفلاین).
 *
 * الگوریتم: کد = ۱۲ کاراکتر هگز اول از HMAC-SHA256(SECRET, "PF-PREMIUM-<شماره سریال>")
 * با قالب نمایشی: PF-XXXX-XXXX-XXXX
 *
 * - توسعه‌دهنده با اسکریپت scripts/gen_activation_codes.py برای شماره‌های سریال ۱ تا ۲۰۰٬۰۰۰
 *   کد تولید می‌کند و پس از فروش (مثلاً از طریق وب‌سایت/تلگرام) به کاربر می‌دهد.
 * - اپ هنگام ورود کد، شماره سریال ۱ تا ۲۰۰٬۰۰۰ را جست‌وجو می‌کند (زیر نیم‌ثانیه).
 *
 * ⚠️ نکته امنیتی: SECRET در سورس قرار دارد و با decompile قابل استخراج است؛
 * این روش حفاظت «منطقی» برای اپ‌های آفلاین است، نه رمزنگاری نظامی. اگر به سطح بالاتر
 * نیاز دارید، اعتبارسنجی را به سرور خود منتقل کنید.
 */
object ActivationCode {

    // کلید امضا — پیش از انتشار می‌توانید آن را تغییر دهید (با اسکریپت سازگار باشد!)
    const val SECRET = "PF2026#Tehran#Geo#x9k2m#Premium#7f4q"
    const val PREFIX = "PF"
    const val MAX_SERIAL = 200_000

    /** تولید کد برای شماره سریال (مطابق اسکریپت پایتونی) */
    fun codeFor(serial: Int): String {
        val hex = hmacHex(SECRET, "PF-PREMIUM-$serial").take(12).uppercase()
        return PREFIX + "-" + hex.chunked(4).joinToString("-")
    }

    /** نرمال‌سازی ورودی کاربر: حذف فاصله/خط تیره، یکدست‌سازی حروف */
    fun normalize(raw: String): String =
        raw.trim()
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
            .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
            .replace("۸", "8").replace("۹", "9")
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .let { if (it.startsWith(PREFIX)) it.substring(PREFIX.length) else it }

    fun isValidFormat(normalized: String): Boolean =
        normalized.length == 12 && normalized.all { it in "0123456789ABCDEF" }

    /**
     * جست‌وجوی شماره سریال متناظر با کد (۱ تا ۲۰۰٬۰۰۰).
     * در دستگاه واقعی زیر نیم‌ثانیه طول می‌کشد؛ آن را روی Dispatchers.IO صدا بزنید.
     */
    fun findSerial(code: String): Int? {
        if (!isValidFormat(code)) return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val target = code.uppercase()
        for (serial in 1..MAX_SERIAL) {
            val hex = mac.doFinal("PF-PREMIUM-$serial".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            if (hex.take(12).uppercase() == target) return serial
        }
        return null
    }
}
