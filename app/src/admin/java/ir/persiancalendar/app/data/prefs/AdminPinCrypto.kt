package ir.persiancalendar.app.data.prefs

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * هش رمز پنل مدیریت — نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۲)
 *
 * تا ۱.۱۱.۴۴ رمز پنل متن ساده در DataStore بود. از این نسخه:
 *
 *  - PBKDF2WithHmacSHA256 با salt تصادفی ۱۶ بایتی و ۱۵۰٬۰۰۰ دور
 *    (حداقل تعیین‌شده در بازرسی) و خروجی ۲۵۶ بیتی؛
 *  - مقایسهٔ زمان‌ثابت با MessageDigest.isEqual — هیچ مسیر زودهنگامی که
 *    زمان پاسخ را به تعداد بایت‌های درست گره بزند وجود ندارد؛
 *  - مهاجرت: رمز متنی قدیمی («admin_pin») در نخستین ورود موفق هش و جایگزین
 *    می‌شود و متن خام پاک می‌گردد (AdminSecrets.verifyAdminPin) — رمز فعلی
 *    مدیر بدون تغییر می‌ماند و بعداً می‌تواند رمز ۶+ تازه بگذارد؛
 *  - سازگاری اندروید ۲۴–۲۵: آنجا SecretKeyFactory با HmacSHA256 موجود نیست
 *    (از API 26) — همان الگوی AccountRepository ۱.۱۱.۲۹، fallback به
 *    PBKDF2WithHmacSHA1 با همین دور؛ هر دو روی JVM آزمون دارند.
 *
 * این کلاس «خالص» است (بدون اندروید) تا با تست واحد JVM راستی‌آزمایی شود.
 */
object AdminPinCrypto {

    /** دورهای هش — حداقل بازرسی: ۱۵۰٬۰۰۰ */
    const val ITERATIONS = 150_000

    /** طول salt تصادفی — ۱۶ بایت */
    const val SALT_BYTES = 16

    /** طول خروجی هش — ۲۵۶ بیتی */
    const val KEY_BITS = 256

    /** طول کمینهٔ رمز تازه — همان قاعدهٔ ۱.۱۱.۲۹ (۶ کاراکتر، حرف + عدد) */
    const val MIN_NEW_PIN_CHARS = 6

    private const val ALG_SHA256 = "PBKDF2WithHmacSHA256"
    private const val ALG_SHA1 = "PBKDF2WithHmacSHA1"

    /** salt تصادفی تازه — SecureRandom */
    fun newSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * هش PBKDF2 — روی هر دستگاه بهترین الگوریتم موجود انتخاب می‌شود
     * (SHA256؛ روی اندروید ۲۴/۲۵ که Conscrypt آن را ندارد، SHA1 با همین دور).
     */
    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val alg = algorithmAvailable()
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(alg).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * بررسی رمز — مقایسهٔ زمان‌ثابت با MessageDigest.isEqual.
     * هر استثنایی (ورودی خراب/الگوریتم ناموجود) = false، نه کرش.
     */
    fun verify(pin: String, salt: ByteArray, iterations: Int, expectedHash: ByteArray): Boolean =
        runCatching {
            val computed = hash(pin, salt, iterations)
            MessageDigest.isEqual(computed, expectedHash)
        }.getOrDefault(false)

    /**
     * الگوریتم موجود روی این دستگاه — اول SHA256، در نبودش SHA1
     * (فقط اندروید ۲۴/۲۵؛ JVM و اندروید ۲۶+ همیشه SHA256).
     */
    private fun algorithmAvailable(): String = runCatching {
        SecretKeyFactory.getInstance(ALG_SHA256)
        ALG_SHA256
    }.getOrDefault(ALG_SHA1)
}
