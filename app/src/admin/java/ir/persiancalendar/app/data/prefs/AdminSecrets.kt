package ir.persiancalendar.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * رمز پنل مدیریت + توکن گیت‌هاب — فقط در فلاور «admin» (نسخهٔ شخصی مدیر).
 *
 * نسخهٔ ۱.۱۱.۳۰ (بازرسی امنیتی مرحلهٔ دوم: «githubToken هنوز در اپ هست» و
 * «پنل ادمین هنوز داخل اپ است»): این کد و کلیدهای DataStore آن از نسخهٔ
 * عمومی به کل حذف شده‌اند — APK عمومی (فلاور «public») هیچ اثری از
 * admin_pin/github_token ندارد، چون این فایل فقط در فلاور admin کامپایل می‌شود.
 *
 * نسخهٔ ۱.۱۱.۴۵ — بازرسی امنیتی مرحلهٔ سوم (کارهای ۱ و ۲):
 *
 *  توکن گیت‌هاب:
 *   - دیگر متن ساده نیست: با AES-256-GCM کلیدِ غیرقابل export در AndroidKeyStore
 *     رمز می‌شود و فقط «iv + ciphertext» در کلید github_token_enc می‌ماند.
 *   - مهاجرت: اگر نسخهٔ متنیِ قدیمی github_token موجود باشد، در نخستین خواندن
 *     (ورود به پنل یا انتشار) رمز و جایگزین می‌شود و کلید متنی حذف می‌گردد.
 *   - UI فقط از طریق githubTokenMasked توکن را می‌بیند — همیشه فقط ۴ کاراکتر آخر
 *     («••••x7qf»)؛ کل توکن هرگز به UI/لاگ/پیام خطا نمی‌رود.
 *   - «خروج و حذف توکن» در کارت امنیت پنل: بلاب رمز و نسخهٔ قدیمی را هر دو پاک می‌کند.
 *
 *  رمز پنل (admin_pin):
 *   - از متن ساده به PBKDF2WithHmacSHA256 با salt تصادفی ۱۶ بایتی و ۱۵۰٬۰۰۰ دور
 *     مهاجرت می‌شود (AdminPinCrypto؛ اندروید ۲۴/۲۵: fallback PBKDF2WithHmacSHA1
 *     مثل الگوی AccountRepository)؛ مقایسه با MessageDigest.isEqual (زمان‌ثابت).
 *     مهاجرت هنگام نخستین ورود موفق با رمز قدیمی انجام می‌شود؛
 *   - قفل موقت نمایی پس از ۵ تلاش نادرست: ۳۰s، ۲m، ۱۰m، … (سقف ۲۴ ساعت) با
 *     شمارندهٔ تلاش پایدار در DataStore (attempts/lock_until)؛
 *   - نشست پنل بعد از ۵ دقیقه بی‌تعاملی یا پس‌زمینه‌رفتن اپ دوباره PIN می‌خواهد.
 *
 * هشدار امنیتی (همان راهنمای قبلی):
 *  - توکن فقط روی دستگاه مدیر می‌ماند و هیچ‌جا ارسال نمی‌شود؛
 *  - برای توکن Fine-grained انقضای کوتاه بگذارید؛
 *  - اگر گوشی گم شد یا مشکوک به لو رفتن هستید، اول در گیت‌هاب Revoke کنید
 *    و بعد «خروج و حذف توکن» را بزنید.
 */
class AdminSecrets(private val context: Context) {

    private object Keys {
        // legacy (متن ساده تا ۱.۱۱.۴۴) — فقط برای مهاجرت خوانده و سپس پاک می‌شوند
        val LEGACY_TOKEN = stringPreferencesKey("github_token")
        val LEGACY_PIN = stringPreferencesKey("admin_pin")

        // توکن رمزشده (۱.۱۱.۴۵) — v1.<iv>.<ct> با کلید غیرقابل export کی‌استور
        val TOKEN_ENC = stringPreferencesKey("github_token_enc")

        // هش رمز پنل (۱.۱۱.۴۵ — کار ۲): PBKDF2 + salt ۱۶ بایتی
        val PIN_HASH = stringPreferencesKey("admin_pin_hash_v2")
        val PIN_SALT = stringPreferencesKey("admin_pin_salt_v2")
        val PIN_ITERS = intPreferencesKey("admin_pin_iters_v2")

        // قفل موقت — شمارندهٔ تلاش پایدار
        val PIN_ATTEMPTS = intPreferencesKey("admin_pin_attempts")
        val PIN_LOCK_UNTIL = longPreferencesKey("admin_pin_lock_until")
    }

    // ================= رمز پنل (۱.۱۱.۴۵ — کار ۲: PBKDF2 + قفل موقت) =================

    /** وضعیت رمز برای UI — چرخهٔ مهاجرت: legacy → هش */
    data class PinUiState(
        val needsSetup: Boolean,   // نه رمز قدیمی نه هش — اولین ورود (اجبار به تنظیم)
        val isHashed: Boolean,     // هش PBKDF2 ذخیره شده (پس از مهاجرت/تنظیم)
        val legacyExists: Boolean  // رمز متنی قدیمی هنوز هست (بنر پیشنهاد تغییر رمز)
    )

    val pinState: Flow<PinUiState> = context.dataStore.data.map { p ->
        val legacy = !p[Keys.LEGACY_PIN].isNullOrBlank()
        val hashed = !p[Keys.PIN_HASH].isNullOrBlank() && !p[Keys.PIN_SALT].isNullOrBlank()
        PinUiState(
            needsSetup = !legacy && !hashed,
            isHashed = hashed,
            legacyExists = legacy
        )
    }

    /**
     * بررسی رمز — هش تازه یا رمز متنی قدیمی، هر دو با مقایسهٔ زمان‌ثابت.
     * ورود موفق با رمز قدیمی = مهاجرت یک‌باره به هش PBKDF2 و پاک‌شدن متن خام.
     * (شمارش تلاش ناموفق و قفل، در AdminScreen انجام می‌شود.)
     */
    suspend fun verifyAdminPin(input: String): Boolean {
        val p = context.dataStore.data.first()
        val hashB64 = p[Keys.PIN_HASH]
        val saltB64 = p[Keys.PIN_SALT]
        if (!hashB64.isNullOrBlank() && !saltB64.isNullOrBlank()) {
            val b64 = java.util.Base64.getDecoder()
            val expected = runCatching { b64.decode(hashB64) }.getOrNull() ?: return false
            val salt = runCatching { b64.decode(saltB64) }.getOrNull() ?: return false
            val iters = p[Keys.PIN_ITERS] ?: AdminPinCrypto.ITERATIONS
            return AdminPinCrypto.verify(input, salt, iters, expected)
        }
        // مسیر مهاجرت: رمز متنی قدیمی — مقایسهٔ زمان‌ثابت بایت‌به‌بایت
        val legacy = p[Keys.LEGACY_PIN].orEmpty()
        if (legacy.isEmpty()) return false
        val ok = java.security.MessageDigest.isEqual(
            input.toByteArray(Charsets.UTF_8),
            legacy.toByteArray(Charsets.UTF_8)
        )
        if (ok) {
            storeHashedPin(input)
            context.dataStore.edit { it.remove(Keys.LEGACY_PIN) }
            resetAttempts()
        }
        return ok
    }

    /** ذخیرهٔ هش رمز (تنظیم/تغییر) — متن خام هرگز ذخیره نمی‌شود */
    suspend fun setAdminPin(pin: String) {
        storeHashedPin(pin)
        context.dataStore.edit { it.remove(Keys.LEGACY_PIN) }
        resetAttempts()
    }

    private suspend fun storeHashedPin(pin: String) {
        val salt = AdminPinCrypto.newSalt()
        val iters = AdminPinCrypto.ITERATIONS
        val hash = AdminPinCrypto.hash(pin, salt, iters)
        val b64 = java.util.Base64.getEncoder()
        context.dataStore.edit {
            it[Keys.PIN_HASH] = b64.encodeToString(hash)
            it[Keys.PIN_SALT] = b64.encodeToString(salt)
            it[Keys.PIN_ITERS] = iters
        }
    }

    // ---------- قفل موقت پس از تلاش‌های نادرست (شمارندهٔ پایدار) ----------

    /**
     * یک تلاش نادرست ثبت می‌شود؛ از تلاش پنجم قفل نمایی فعال می‌شود:
     * ۳۰ ثانیه، ۲ دقیقه، ۱۰ دقیقه، ۴۰ دقیقه، … (×۴ هر بار، سقف ۲۴ ساعت).
     * @return میلی‌ثانیه تا پایان قفل (۰ = قفل نشد)
     */
    suspend fun registerFailedAttempt(): Long {
        val now = System.currentTimeMillis()
        var lockUntil = 0L
        context.dataStore.edit { p ->
            val attempts = (p[Keys.PIN_ATTEMPTS] ?: 0) + 1
            p[Keys.PIN_ATTEMPTS] = attempts
            if (attempts >= LOCKOUT_AFTER) {
                val k = (attempts - LOCKOUT_AFTER).coerceAtMost(11)
                val secs = (LOCKOUT_BASE_SECONDS * (1L shl k)).coerceAtMost(MAX_LOCK_SECONDS)
                lockUntil = now + secs * 1000L
                p[Keys.PIN_LOCK_UNTIL] = lockUntil
            }
        }
        return lockUntil
    }

    /** پس از ورود موفق یا تنظیم رمز تازه — شمارنده و قفل صفر می‌شوند */
    suspend fun resetAttempts() {
        context.dataStore.edit {
            it.remove(Keys.PIN_ATTEMPTS)
            it.remove(Keys.PIN_LOCK_UNTIL)
        }
    }

    /** میلی‌ثانیه تا پایان قفل (۰ = قفل نیست) — از DataStore پایدار می‌خواند */
    suspend fun remainingLockMs(): Long {
        val until = context.dataStore.data.first()[Keys.PIN_LOCK_UNTIL] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    // ================= توکن گیت‌هاب =================

    /**
     * توکن رمزگشایی‌شده — فقط از نخ پس‌زمینه صدا زده شود؛ مقدار در حافظه می‌ماند
     * و هرگز به UI/لاگ نمی‌رود. مهاجرت متن سادهٔ قدیمی هم این‌جا انجام می‌شود.
     */
    fun githubTokenBlocking(): String = runBlocking { resolveGithubToken() }

    /** نسخهٔ suspend همان خواندن (برای coroutineها) */
    suspend fun githubToken(): String = resolveGithubToken()

    private suspend fun resolveGithubToken(): String {
        val p = context.dataStore.data.first()
        val enc = p[Keys.TOKEN_ENC]
        if (!enc.isNullOrBlank()) {
            val t = runCatching { TokenCrypto.decrypt(enc) }.getOrNull()
            if (t != null) return t
            // کلید سخت‌افزاری دیگر بلاب را باز نمی‌کند (مثلاً پاک‌شدن کی‌استور)
            // → بلاب بی‌اعتبار پاک می‌شود و کاربر توکن تازه می‌گذارد
            context.dataStore.edit { it.remove(Keys.TOKEN_ENC) }
            return ""
        }
        // مهاجرت یک‌باره از متن سادهٔ قدیمی: رمز، جایگزین و متن خام پاک می‌شود
        val legacy = p[Keys.LEGACY_TOKEN].orEmpty().trim()
        if (legacy.isNotBlank()) {
            val blob = runCatching { TokenCrypto.encrypt(legacy) }.getOrDefault("")
            if (blob.isNotBlank()) {
                context.dataStore.edit {
                    it[Keys.TOKEN_ENC] = blob
                    it.remove(Keys.LEGACY_TOKEN)
                }
            }
            return legacy
        }
        return ""
    }

    /**
     * مهاجرت فعال متن سادهٔ قدیمی به بلاب رمز — در ورود به پنل صدا زده می‌شود تا
     * کلید legacy هرگز روی دیسک باقی نماند، حتی اگر هیچ‌وقت منتشر نشود.
     */
    suspend fun migrateLegacyToken() {
        val p = context.dataStore.data.first()
        val legacy = p[Keys.LEGACY_TOKEN].orEmpty().trim()
        if (legacy.isBlank()) return
        if (p[Keys.TOKEN_ENC].isNullOrBlank()) {
            val blob = runCatching { TokenCrypto.encrypt(legacy) }.getOrDefault("")
            if (blob.isNotBlank()) {
                context.dataStore.edit {
                    it[Keys.TOKEN_ENC] = blob
                    it.remove(Keys.LEGACY_TOKEN)
                }
                return
            }
        }
        context.dataStore.edit { it.remove(Keys.LEGACY_TOKEN) }
    }

    /** ذخیرهٔ توکن تازه — فوراً رمز می‌شود؛ متن خام هرگز در DataStore نمی‌نشیند */
    suspend fun saveGithubToken(plain: String) {
        val t = plain.trim()
        if (t.isBlank()) return
        val blob = TokenCrypto.encrypt(t)
        if (blob.isNotBlank()) {
            context.dataStore.edit {
                it[Keys.TOKEN_ENC] = blob
                it.remove(Keys.LEGACY_TOKEN)
            }
        }
    }

    /** «خروج و حذف توکن» — بلاب رمز و نسخهٔ قدیمی هر دو پاک می‌شوند */
    suspend fun wipeGithubToken() {
        context.dataStore.edit {
            it.remove(Keys.TOKEN_ENC)
            it.remove(Keys.LEGACY_TOKEN)
        }
    }

    /**
     * نمایش ایمن توکن برای UI — همیشه فقط ۴ کاراکتر آخر («••••x7qf»).
     * این تنها مسیری است که UI توکن را می‌بیند؛ کل توکن هرگز به UI نمی‌رسد.
     */
    val githubTokenMasked: Flow<String> = context.dataStore.data.map { p ->
        val enc = p[Keys.TOKEN_ENC]
        if (!enc.isNullOrBlank()) {
            runCatching { TokenCrypto.mask(TokenCrypto.decrypt(enc) ?: "") }
                .getOrDefault("")
        } else {
            // متن خام قدیمی (پیش از مهاجرت) هم فقط ماسک‌شده دیده می‌شود
            TokenCrypto.mask(p[Keys.LEGACY_TOKEN].orEmpty())
        }
    }

    companion object {
        /** آستانهٔ قفل — پس از این تعداد تلاش نادرست پشت‌سرهم */
        const val LOCKOUT_AFTER = 5

        /** قفل پایه: ۳۰ ثانیه — سپس هر تلاش نادرست ×۴ (۳۰s، ۲m، ۱۰m، ۴۰m، …) */
        const val LOCKOUT_BASE_SECONDS = 30L

        /** سقف قفل — ۲۴ ساعت */
        const val MAX_LOCK_SECONDS = 24L * 60L * 60L

        /** سقف نشست باز پنل بدون تعامل — ۵ دقیقه (بازرسی امنیتی — کار ۲) */
        const val SESSION_IDLE_MS = 5L * 60L * 1000L
    }
}
