package ir.persiancalendar.app.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.persiancalendar.app.core.premium.ActivationCode
import ir.persiancalendar.app.data.db.UserDao
import ir.persiancalendar.app.data.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
 * - ثبت‌نام/ورود: کاربران در Room با پسورد هش‌شده (SHA-256 + نمک × ۱۰۰۰ دور) نگهداری می‌شوند.
 * - نشست جاری: شناسه کاربر در DataStore.
 * - عضویت ویژه: دو مسیر — خرید درون‌برنامه‌ای (BillingManager) یا کد فعال‌سازی.
 *   در هر دو حالت تبلیغات به‌طور کامل غیرفعال می‌شود.
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
        return if (hashPassword(password, user.salt) == user.passwordHash) {
            context.accountStore.edit { it[Keys.SESSION_USER] = user.id }
            // اگر حساب ویژه است، سطح دستگاه هم فعال شود
            if (user.premium) grantDevicePremium(user.premiumSource.ifEmpty { "code" })
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

    // ---------- کد فعال‌سازی ----------

    /**
     * اعتبارسنجی کد فعال‌سازی و اعطای عضویت ویژه.
     * خروجی: (موفق بودن، پیام برای نمایش)
     */
    suspend fun activateWithCode(rawCode: String): Pair<Boolean, String> {
        val code = ActivationCode.normalize(rawCode)
        if (!ActivationCode.isValidFormat(code)) {
            return false to "قالب کد صحیح نیست (مثال: PF-XXXX-XXXX-XXXX)"
        }
        val serial = ActivationCode.findSerial(code)
            ?: return false to "کد فعال‌سازی معتبر نیست"
        grantDevicePremium("code")
        context.accountStore.edit { it[Keys.ACTIVATED_CODE] = code }
        return true to "عضویت ویژه با موفقیت فعال شد"
    }

    val activatedCode: Flow<String> =
        context.accountStore.data.map { it[Keys.ACTIVATED_CODE].orEmpty() }

    companion object {
        private const val HASH_ITERATIONS = 1000

        private fun newSalt(): String {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            return Base64.encodeToString(b, Base64.NO_WRAP)
        }

        fun hashPassword(password: String, salt: String): String {
            var digest = (salt + password).toByteArray(Charsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-256")
            repeat(HASH_ITERATIONS) { digest = md.digest(digest) }
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }
    }
}
