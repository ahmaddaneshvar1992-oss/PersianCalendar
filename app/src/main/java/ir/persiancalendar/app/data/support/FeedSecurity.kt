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

    @Volatile private var verifier: Ed25519Verify? = null

    private fun getVerifier(): Ed25519Verify {
        verifier?.let { return it }
        synchronized(this) {
            verifier?.let { return it }
            val v = Ed25519Verify(Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT))
            verifier = v
            return v
        }
    }

    /**
     * تأیید امضای خوراک: content باید «بایت‌های دقیق» فایل جیسون باشد و
     * signatureB64 مقدار Base64 فایل .sig. خروجی true فقط وقتی امضا معتبر است.
     */
    fun verify(content: ByteArray, signatureB64: String): Boolean = runCatching {
        val sig = Base64.decode(signatureB64.trim(), Base64.DEFAULT)
        getVerifier().verify(sig, content)
        true
    }.getOrDefault(false)
}
