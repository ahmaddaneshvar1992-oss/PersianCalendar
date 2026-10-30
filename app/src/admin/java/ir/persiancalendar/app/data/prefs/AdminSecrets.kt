package ir.persiancalendar.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
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
 * هشدار امنیتی (همان راهنمای قبلی):
 *  - توکن فقط روی دستگاه مدیر می‌ماند و هیچ‌جا ارسال نمی‌شود؛
 *  - برای توکن Fine-grained انقضای کوتاه بگذارید؛
 *  - اگر گوشی گم شد یا مشکوک به لو رفتن هستید، اول در گیت‌هاب Revoke کنید
 *    و بعد «پاک کردن توکن» را بزنید.
 */
class AdminSecrets(private val context: Context) {

    private object Keys {
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }

    /** رمز پنل (حروف و/یا اعداد) — خالی یعنی هنوز تنظیم نشده (اولین ورود اجبار به تنظیم) */
    val adminPin: Flow<String> = context.dataStore.data.map { it[Keys.ADMIN_PIN] ?: "" }

    /** توکن دسترسی گیت‌هاب برای انتشار خوراک — فقط روی دستگاه مدیر */
    val githubToken: Flow<String> = context.dataStore.data.map { it[Keys.GITHUB_TOKEN] ?: "" }

    /** خواندن همگام توکن — فقط از نخ پس‌زمینه */
    fun githubTokenBlocking(): String = runBlocking { githubToken.first() }

    suspend fun setAdminPin(pin: String) =
        context.dataStore.edit { it[Keys.ADMIN_PIN] = pin }

    suspend fun setGithubToken(t: String) =
        context.dataStore.edit { it[Keys.GITHUB_TOKEN] = t.trim() }
}
