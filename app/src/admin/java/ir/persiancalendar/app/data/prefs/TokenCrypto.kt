package ir.persiancalendar.app.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * رمزنگاری توکن گیت‌هاب با AndroidKeyStore — نسخهٔ ۱.۱۱.۴۵ (کار ۱ از بازرسی امنیتی)
 *
 * تا ۱.۱۱.۴۴ توکن در DataStore به‌صورت متن ساده ذخیره می‌شد. از این نسخه:
 *
 *  - کلید AES-256-GCM در AndroidKeyStore ساخته می‌شود — «غیرقابل export»:
 *    بایت‌های کلید هرگز از سخت‌افزار/کی‌استور بیرون نمی‌آیند (بدون setUserAuthentication
 *   Requirement تا توکن بدون قفل صفحه هم قابل رمزگشایی باشد — دستگاه خودِ مدیر است).
 *  - برای هر رمزنگاری، خود Keystore یک IV تصادفی ۱۲ بایتی می‌سازد
 *    (setRandomizedEncryptionRequired)؛ خروجی «v1.<iv>.<ciphertext>» با Base64 است.
 *  - DataStore فقط iv + ciphertext را نگه می‌دارد؛ توکن خام هرگز روی دیسک نمی‌رود.
 *  - اگر کلید سخت‌افزاری نامعتبر شود (پاک‌شدن کی‌استور، بازگردانی بک‌آپ روی
 *    دستگاه دیگر — البته allowBackup=false است)، رمزگشایی null می‌دهد و بلاب
 *    بی‌اعتبار پاک می‌شود؛ کاربر توکن را دوباره می‌گذارد. هیچ استثنایی به بیرون
 *    نمی‌رسد و هیچ متنی از محتوای توکن در پیام‌ها/لاگ نمی‌آید.
 *
 * قالب بلاب:  "v1.<Base64(iv 12B)>.<Base64(ciphertext+tag 128bit)>"
 */
object TokenCrypto {

    private const val KEY_ALIAS = "badkhazan_admin_github_token"
    private const val BLOB_PREFIX = "v1."
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** رمزنگاری متن توکن — خروجی بلاب امن برای DataStore (هرگز استثنا نمی‌دهد) */
    fun encrypt(plain: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv ?: ByteArray(IV_BYTES)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = java.util.Base64.getEncoder()
        BLOB_PREFIX + b64.encodeToString(iv) + "." + b64.encodeToString(ct)
    }.getOrDefault("")

    /**
     * رمزگشایی بلاب — null یعنی بلاب نامعتبر/کلید سخت‌افزاری نبوده
     * (فراخوان باید بلاب را پاک و از کاربر توکن تازه بخواهد).
     */
    fun decrypt(blob: String): String? = runCatching {
        if (!blob.startsWith(BLOB_PREFIX)) return null
        val parts = blob.removePrefix(BLOB_PREFIX).split('.')
        if (parts.size != 2) return null
        val b64 = java.util.Base64.getDecoder()
        val iv = b64.decode(parts[0])
        val ct = b64.decode(parts[1])
        if (iv.size != IV_BYTES || ct.isEmpty()) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    /**
     * نمایش ایمن توکن — فقط ۴ کاراکتر آخر؛ هیچ‌وقت کل توکن در UI/لاگ/پیام نمی‌آید.
     */
    fun mask(token: String): String {
        val t = token.trim()
        if (t.isEmpty()) return ""
        // توکنِ کوتاه هرگز هیچ بخشی‌اش نشان داده نمی‌شود — توکنِ ۴ کاراکتری با
        // takeLast(4) یعنی لو رفتنِ کامل؛ زیرِ ۸ کاراکتر همهٔ آن ماسک است
        val tail = if (t.length >= 8) t.takeLast(4) else ""
        return "••••$tail"
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }
}
