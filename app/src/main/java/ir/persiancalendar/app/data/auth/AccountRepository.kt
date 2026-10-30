package ir.persiancalendar.app.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.persiancalendar.app.data.db.UserDao
import ir.persiancalendar.app.data.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** نتیجه عملیات ثبت‌نام/ورود */
sealed class AuthResult {
    data class Success(val user: UserEntity) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * مخزن حساب کاربری و عضویت ویژه — کاملاً آفلاین و محلی.
 *
 * - ثبت‌نام/ورود: کاربران در Room با پسورد هش‌شده نگهداری می‌شوند.
 *   نسخهٔ ۱.۱۱.۲۹ (گزارش بازرسی امنیتی: «هش رمز با SHA-256 ساده»): هش از
 *   SHA-256 تکراری ۱۰۰۰ دور به PBKDF2-HmacSHA256 با ۱۲۰٬۰۰۰ دور ارتقا یافت
 *   (روی اندروید‌های ۲۴–۲۵ که PBKDF2-HmacSHA256 ندارند، PBKDF2-HmacSHA1).
 *   حساب‌های قدیمی هنگام ورود موفق، شفاف به قالب جدید مهاجرت می‌کنند.
 * - نشست جاری: شناسه کاربر در DataStore.
 * - عضویت ویژه: خرید درون‌برنامه‌ای (BillingManager).
 *   نسخهٔ ۱.۱۱.۲۹: کد فعال‌سازی آفلاین (کلید HMAC داخل APK) کامل حذف شد —
 *   هر کلیدی داخل اپ با decompile قابل استخراج است؛ کسانی که قبلاً با کد
 *   فعال شده‌اند عضویت‌شان سر جای خود می‌ماند.
 */
class AccountRepository(private val context: Context, private val userDao: UserDao) {

    private object Keys {
        val SESSION_USER = longPreferencesKey("session_user_id")
        val PREMIUM_DEVICE = stringPreferencesKey("premium_device") // "", "billing", "code"
        val PREMIUM_SINCE = longPreferencesKey("premium_since")
        val ACTIVATED_CODE = stringPreferencesKey("activated_code")
    }

    // ---------- نشست ----------

    /** فلو کاربر جاری بر اساس شناسه نشست (null = مهمان) */
    fun sessionUser(): Flow<UserEntity?> =
        context.accountStore.data.map { it[Keys.SESSION_USER] ?: -1L }
            .map { uid -> if (uid > 0) userDao.getById(uid) else null }

    private suspend fun sessionUserId(): Long =
        context.accountStore.data.map { it[Keys.SESSION_USER] ?: -1L }.first()

    // ---------- عضویت ویژه ----------

    /** آیا عضویت ویژه فعال است (سطح دستگاه یا کاربر جاری) */
    val isPremium: Flow<Boolean> = combine(
        context.accountStore.data.map { it[Keys.PREMIUM_DEVICE].orEmpty() },
        context.accountStore.data.map { it[Keys.SESSION_USER] ?: -1L }
    ) { deviceSource, _ -> deviceSource.isNotEmpty() }

    /** منبع ارتقای دستگاه (برای نمایش): "billing" / "code" / "" */
    val devicePremiumSource: Flow<String> =
        context.accountStore.data.map { it[Keys.PREMIUM_DEVICE].orEmpty() }

    /** اعطای عضویت ویژه در سطح دستگاه (خرید یا کد فعال‌سازی) */
    suspend fun grantDevicePremium(source: String) {
        context.accountStore.edit { p ->
            p[Keys.PREMIUM_DEVICE] = source
            p[Keys.PREMIUM_SINCE] = System.currentTimeMillis()
        }
        // اگر کاربر وارد شده، وضعیت ویژه او هم در حساب ثبت شود
        val id = sessionUserId()
        val user = if (id > 0) userDao.getById(id) else null
        if (user != null && !user.premium) {
            userDao.update(
                user.copy(
                    premium = true,
                    premiumSource = source,
                    premiumSince = System.currentTimeMillis()
                )
            )
        }
    }

    /** باطل کردن عضویت سطح دستگاه (فقط برای تست توسعه‌دهنده) */
    suspend fun revokeDevicePremium() {
        context.accountStore.edit { p ->
            p[Keys.PREMIUM_DEVICE] = ""
            p[Keys.PREMIUM_SINCE] = 0L
            p.remove(Keys.ACTIVATED_CODE)
        }
    }

    // ---------- ثبت‌نام و ورود ----------

    suspend fun register(name: String, username: String, password: String): AuthResult {
        val n = name.trim()
        val u = username.trim().lowercase()
        if (n.length < 2) return AuthResult.Error("نام باید حداقل ۲ حرف باشد")
        if (u.length < 4) return AuthResult.Error("شماره موبایل یا ایمیل باید حداقل ۴ کاراکتر باشد")
        if (password.length < 4) return AuthResult.Error("رمز عبور باید حداقل ۴ کاراکتر باشد")
        val exists = userDao.findByUsername(u)
        if (exists != null) return AuthResult.Error("این شماره/ایمیل قبلاً ثبت‌نام کرده است")
        val salt = newSalt()
        val hash = hashPassword(password, salt)
        return try {
            val id = userDao.insert(
                UserEntity(name = n, username = u, passwordHash = hash, salt = salt)
            )
            context.accountStore.edit { it[Keys.SESSION_USER] = id }
            val user = userDao.getById(id)
            if (user != null) AuthResult.Success(user)
            else AuthResult.Error("خطای نامشخص در ثبت‌نام")
        } catch (e: Exception) {
            AuthResult.Error("ثبت‌نام ناموفق بود: ${e.message ?: ""}")
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        val u = username.trim().lowercase()
        val user = userDao.findByUsername(u)
            ?: return AuthResult.Error("کاربری با این شماره/ایمیل یافت نشد")
        return if (verifyPassword(password, user.salt, user.passwordHash)) {
            context.accountStore.edit { it[Keys.SESSION_USER] = user.id }
            // نسخهٔ ۱.۱۱.۲۹: مهاجرت شفاف حساب‌های قدیمی (SHA-256 تکراری) به PBKDF2
            // در اولین ورود موفق — بدون هیچ کاری از سمت کاربر
            if (!user.passwordHash.startsWith("pbkdf2$")) {
                runCatching {
                    userDao.update(
                        user.copy(passwordHash = hashPassword(password, user.salt))
                    )
                }
            }
            // اگر حساب ویژه است، سطح دستگاه هم فعال شود
            if (user.premium) grantDevicePremium(user.premiumSource.ifEmpty { "billing" })
            AuthResult.Success(user)
        } else AuthResult.Error("رمز عبور اشتباه است")
    }

    suspend fun logout() {
        context.accountStore.edit { it.remove(Keys.SESSION_USER) }
    }

    /** حذف کامل حساب جاری از دستگاه */
    suspend fun deleteCurrentAccount() {
        val uid = sessionUserId()
        if (uid > 0) userDao.deleteById(uid)
        logout()
    }

    // ---------- کد فعال‌سازی (نسخهٔ ۱.۱۱.۲۹ حذف شد) ----------
    // سیستم کد آفلاین با کلید HMAC داخل APK به‌کلی برداشته شد — راه امن‌تر:
    // خرید درون‌برنامه‌ای (BillingManager) و در آینده اعتبارسنجی سمت سرور.
    // عضویت فعال‌شدهٔ قبلی با کد در DataStore دست‌نخورده می‌ماند.

    companion object {
        // ---------- نسخهٔ ۱.۱۱.۲۹ (بازرسی امنیتی): PBKDF2 جای SHA-256 تکراری ----------
        // قالب ذخیره در ستون passwordHash:
        //   جدید:  "pbkdf2$<algo>$<iters>$<hashBase64>"  (نمک جدا در ستون salt)
        //   قدیمی: Base64 خام (SHA-256 تکراری ۱۰۰۰ دور — فقط برای مهاجرت ورود)
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_BITS = 256
        private const val PREFIX = "pbkdf2"
        private const val LEGACY_ITERATIONS = 1000

        private fun newSalt(): String {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            return Base64.encodeToString(b, Base64.NO_WRAP)
        }

        /** PBKDF2 با بهترین الگوریتم موجود — HmacSHA256 از API ۲۶، قبل‌تر HmacSHA1 */
        private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            val algo = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (e: Exception) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            }
            val spec = PBEKeySpec(password, salt, iterations, PBKDF2_KEY_BITS)
            return algo.generateSecret(spec).encoded
        }

        /** هش قالب جدید برای ثبت‌نام و مهاجرت */
        fun hashPassword(password: String, salt: String): String {
            val algo = if (runCatching {
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                }.isSuccess
            ) "HmacSHA256" else "HmacSHA1"
            val hash = pbkdf2(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), PBKDF2_ITERATIONS)
            return "$PREFIX\$$algo\$$PBKDF2_ITERATIONS\$" +
                Base64.encodeToString(hash, Base64.NO_WRAP)
        }

        /** تأیید رمز — قالب جدید یا قدیمی؛ ورود موفق با قالب قدیمی مهاجرت داده می‌شود */
        fun verifyPassword(password: String, salt: String, stored: String): Boolean {
            return if (stored.startsWith("$PREFIX$")) {
                val parts = stored.split("$")
                if (parts.size != 4) return false
                val iters = parts[2].toIntOrNull() ?: return false
                if (iters < 1 || iters > 10_000_000) return false
                val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull()
                    ?: return false
                val algo = if (parts[1] == "HmacSHA1") "PBKDF2WithHmacSHA1" else "PBKDF2WithHmacSHA256"
                runCatching {
                    val f = SecretKeyFactory.getInstance(algo)
                    val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), iters, PBKDF2_KEY_BITS)
                    f.generateSecret(spec).encoded.contentEquals(expected)
                }.getOrDefault(false)
            } else {
                // قالب قدیمی — SHA-256 تکراری ۱۰۰۰ دور
                var digest = (salt + password).toByteArray(Charsets.UTF_8)
                val md = MessageDigest.getInstance("SHA-256")
                repeat(LEGACY_ITERATIONS) { digest = md.digest(digest) }
                Base64.encodeToString(digest, Base64.NO_WRAP) == stored
            }
        }
    }
}
