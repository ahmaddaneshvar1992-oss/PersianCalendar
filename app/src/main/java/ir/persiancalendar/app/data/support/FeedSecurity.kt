package ir.persiancalendar.app.data.support

import android.util.Base64
import com.google.crypto.tink.subtle.Ed25519Verify

/**
 * تأیید امضای Ed25519 خوراک‌ها — نسخهٔ ۱.۱۱.۳۰ (بازرسی امنیتی مرحلهٔ دوم:
 * «فید بدون امضاست؛ اگر مخزن یا توکن لو بره، محتوای مخرب به همه می‌رسه»)
 *
 * از این نسخه هر فایل خوراک (announcements.json / main_ads.json / config.json)
 * یک فایل امضای هم‌نام با پسوند .sig دارد (امضای Ed25519 روی بایت‌های دقیق فایل).
 *
 *  - امضا با کلید خصوصی ساخته می‌شود که فقط نزد مدیر است (در Secrets مخزن
 *    گیت‌هاب و روی رایانهٔ مدیر — هرگز داخل اپ نیست)؛
 *  - اینجا فقط «کلید عمومی» هست — داشتن کلید عمومی برای ساختن امضای معتبر به هیچ دردی نمی‌خورد؛
 *  - FeedFetcher هر منبع را فقط وقتی می‌پذیرد که امضای فایل با همین کلید درست باشد؛
 *    پاسخ بدون امضا یا با امضای نامعتبر = منبع ناسالم و منبع بعدی آزموده می‌شود؛
 *  - در کش محلی هم فقط محتوای تأییدشده ذخیره می‌شود.
 *
 * نتیجه: حتی اگر مخزن یا توکن نوشتن لو برود، مهاجم بدون کلید خصوصی نمی‌تواند
 * اطلاعیه/بنر/تنظیمات جعلی به کاربران برساند.
 */
object FeedSecurity {

    /** کلید عمومی Ed25519 (۳۲ بایت خام، Base64) — عمومی بودنش طبیعی و بی‌خطر است */
    const val PUBLIC_KEY_B64 = "lpVtz80eClDZSxdm+GmxmBXuu3VN7ObOvjuTpQiQ6HI="

    /**
     * کلید عمومی دوم — چرخش ۲۰۲۶-۱۱-۰۱ (نسخهٔ ۱.۱۱.۳۵).
     * بازرسی v1.11.34 نشان داد امضاهای Secret مخزن با هیچ‌کدام از کلیدهای داخل اپ
     * تأیید نمی‌شوند و خوراک به کاربران نمی‌رسد. با این چرخش، اپ «هر دو» کلید
     * عمومی را می‌پذیرد: امضای فعلی مخزن با کلید چرخش ساخته می‌شود و اگر مدیر بعداً
     * Secret را به کلید اصلی برگرداند هم امضاها معتبر می‌مانند.
     * پذیرش دو کلید سنجاق‌شده = روش استاندارد دورهٔ گذار چرخش کلید؛ هر کلید خصوصی
     * بیرون از این دو، همچنان بی‌اعتبار است.
     */
    const val PUBLIC_KEY_B64_ROTATED = "Z5mIWsYEhgULQ6B3tGimJkkQCUNPN0ipslFBY8uArPw="

    @Volatile private var verifiers: List<Ed25519Verify>? = null

    private fun getVerifiers(): List<Ed25519Verify> {
        verifiers?.let { return it }
        synchronized(this) {
            verifiers?.let { return it }
            val v = listOf(
                Ed25519Verify(Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)),
                Ed25519Verify(Base64.decode(PUBLIC_KEY_B64_ROTATED, Base64.DEFAULT))
            )
            verifiers = v
            return v
        }
    }

    /**
     * تأیید امضای خوراک: content باید «بایت‌های دقیق» فایل جیسون باشد و
     * signatureB64 مقدار Base64 فایل .sig. خروجی true فقط وقتی امضا با یکی از
     * کلیدهای عمومی سنجاق‌شده معتبر باشد.
     * (Ed25519Verify.verify فقط در خطا استثنا پرتاب می‌کند و Unit برمی‌گرداند —
     * پس معیار پذیرش، «نبودن استثنا» است.)
     */
    fun verify(content: ByteArray, signatureB64: String): Boolean = runCatching {
        val sig = Base64.decode(signatureB64.trim(), Base64.DEFAULT)
        getVerifiers().any { v -> runCatching { v.verify(sig, content) }.isSuccess }
    }.getOrDefault(false)
}
