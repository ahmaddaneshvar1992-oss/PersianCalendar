package ir.persiancalendar.app.data.support

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
 *
 * نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۴، fail-closed):
 *  - رمزگشایی Base64 به java.util.Base64 منتقل شد (desugar داریم؛ هم روی
 *    اندروید و هم روی JVM تست واحد کار می‌کند) با گارد صریح «طول امضا = ۶۴
 *    بایت» — امضای کوتاه‌تر/بلندتر هرگز به تأییدکننده نمی‌رسد؛
 *  - verifyWith: نسخهٔ تزریق‌پذیر کلیدها برای تست واحد (تولید کلید تازه در
 *    تست و امضا با کلید اشتباه) — مسیر اجرای واقعی دقیقاً همان است که تست می‌شود؛
 *  - هیچ مسیری «امضا نیست پس می‌پذیرم» ندارد؛ خروجی پیش‌فرض همهٔ مسیرها false است.
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

    /** امضای Ed25519 دقیقاً ۶۴ بایت است — هر چیز دیگری زودتر از تأیید رد می‌شود */
    const val SIGNATURE_BYTES = 64

    @Volatile private var verifiers: List<Ed25519Verify>? = null

    private fun getVerifiers(): List<Ed25519Verify> {
        verifiers?.let { return it }
        synchronized(this) {
            verifiers?.let { return it }
            val v = listOf(
                Ed25519Verify(java.util.Base64.getMimeDecoder().decode(PUBLIC_KEY_B64)),
                Ed25519Verify(java.util.Base64.getMimeDecoder().decode(PUBLIC_KEY_B64_ROTATED))
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
    fun verify(content: ByteArray, signatureB64: String): Boolean =
        verifyWith(content, signatureB64, listOf(PUBLIC_KEY_B64, PUBLIC_KEY_B64_ROTATED))

    /**
     * تأیید با کلیدهای داده‌شده — همان مسیر کدِ verify برای تست واحد.
     * fail-closed: ورودی خالی، Base64 خراب، طول ≠ ۶۴ بایت، کلید خراب یا
     * تأییدکنندهٔ استثناگر — همه فقط false می‌دهند.
     */
    fun verifyWith(content: ByteArray, signatureB64: String, publicKeysB64: List<String>): Boolean {
        if (content.isEmpty()) return false
        val sig = runCatching {
            java.util.Base64.getMimeDecoder().decode(signatureB64.trim())
        }.getOrDefault(ByteArray(0))
        // امضای Ed25519 دقیقاً ۶۴ بایت است — کوتاه‌تر/بلندتر = رد زودهنگام
        if (sig.size != SIGNATURE_BYTES) return false
        return publicKeysB64.any { keyB64 ->
            runCatching {
                val v = Ed25519Verify(java.util.Base64.getMimeDecoder().decode(keyB64.trim()))
                v.verify(sig, content)
                true
            }.getOrDefault(false)
        }
    }
}
