package ir.persiancalendar.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** حالت نمایش تم */
enum class ThemeMode(val label: String) {
    SYSTEM("هماهنگ با سیستم"),
    LIGHT("روشن"),
    DARK("تاریک");

    companion object {
        fun fromOrdinal(i: Int) = entries.getOrElse(i) { SYSTEM }
    }
}

/** پالت رنگ اپلیکیشن */
enum class AccentPalette(val label: String) {
    GREEN("سبز کلاسیک"),
    TEAL("فیروزه‌ای"),
    BLUE("آبی"),
    PURPLE("بنفش");

    companion object {
        fun fromOrdinal(i: Int) = entries.getOrElse(i) { GREEN }
    }
}

/** داده‌های تنظیمات به‌صورت یکجا برای UI (نسخه ۱.۱۱.۵: فیلدهای تبلیغات حذف شدند) */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPalette = AccentPalette.GREEN,
    val persianDigits: Boolean = true,
    val showHijri: Boolean = true,
    val cityIndex: Int = Cities.DEFAULT_INDEX,
    val prayerMethod: PrayerMethod = PrayerMethod.TEHRAN,
    val prayerNotifications: Set<Int> = setOf(1, 3, 5), // صبح، ظهر، مغرب
    val useCurrentLocation: Boolean = false,
    // ---------- نسخه ۱.۲: اذان دلخواه + پنل مدیریت (۱.۱۱.۸: اذان‌های واقعی) ----------
    /** صدای اذان: moazenzadeh / rezayian / none (کلیدهای قدیمی در AdhanSounds نرمال می‌شوند) */
    val adhanSound: String = "moazenzadeh",
    /** رمز پنل مدیریت (حروف و/یا اعداد — نسخه ۱.۳) */
    // نسخهٔ ۱.۱۱.۲۹ (گزارش بازرسی امنیتی): پیش‌فرض «۱۲۳۴» حذف شد —
    // مقدار خالی یعنی رمز هنوز تنظیم نشده و پنل در اولین ورود اجبار به تنظیم رمز دارد
    val adminPin: String = "",
    /** اعلان همیشگی تاریخ امروز در نوار وضعیت */
    val ongoingDateNotif: Boolean = true,
    // ---------- نسخه ۱.۱۱.۴: ویزارد راه‌اندازی اولیه ----------
    /** آیا ویزارد ۵ گامی قبل از ورود به برنامه تکمیل شده؟ */
    val onboardingDone: Boolean = false,
    // ---------- نسخه ۱.۱۱.۱۶ (درخواست کاربر مطابق عکس): مشخصات کاربر در راه‌اندازی ----------
    /** جنسیت کاربر: "" / "male" / "female" — گام پنجم راه‌اندازی اولیه */
    val profileGender: String = "",
    /** تاریخ تولد شمسی با قالب "jy/jm/jd" — خالی یعنی انتخاب نشده */
    val profileBirth: String = "",
    // ---------- نسخه ۱.۱۱.۱۶: انتشار اطلاعیه برای کاربران از پنل مدیریت ----------
    /** توکن دسترسی گیت‌هاب (فقط روی دستگاه مدیر؛ برای انتشار خوراک اطلاعیه) */
    val githubToken: String = ""
)

/** مخزن تنظیمات مبتنی بر DataStore */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = intPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_palette")
        val PERSIAN_DIGITS = booleanPreferencesKey("persian_digits")
        val SHOW_HIJRI = booleanPreferencesKey("show_hijri")
        val CITY = intPreferencesKey("city_index")
        val PRAYER_METHOD = intPreferencesKey("prayer_method")
        val PRAYER_NOTIF = intPreferencesKey("prayer_notif_mask")
        val USE_LOCATION = booleanPreferencesKey("use_current_location")
        // ---------- نسخه ۱.۲ ----------
        val ADHAN_SOUND = stringPreferencesKey("adhan_sound")
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
        // ---------- نسخه ۱.۳ ----------
        val ONGOING_DATE = booleanPreferencesKey("ongoing_date_notif")
        // ---------- نسخه ۱.۱۱.۴: ویزارد راه‌اندازی اولیه ----------
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        // ---------- نسخه ۱.۱۱.۱۶: مشخصات کاربر (جنسیت و تاریخ تولد) ----------
        val PROFILE_GENDER = stringPreferencesKey("profile_gender")
        val PROFILE_BIRTH = stringPreferencesKey("profile_birth")
        // ---------- نسخه ۱.۱۱.۱۶: توکن انتشار اطلاعیه (دستگاه مدیر) ----------
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        // (کلیدهای تبلیغات نسخه‌های قدیمی در DataStore بی‌اثر مانده‌اند — نسخه ۱.۱۱.۵)
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.fromOrdinal(p[Keys.THEME] ?: 0),
            accent = AccentPalette.fromOrdinal(p[Keys.ACCENT] ?: 0),
            persianDigits = p[Keys.PERSIAN_DIGITS] ?: true,
            showHijri = p[Keys.SHOW_HIJRI] ?: true,
            cityIndex = p[Keys.CITY] ?: Cities.DEFAULT_INDEX,
            prayerMethod = PrayerMethod.fromOrdinal(p[Keys.PRAYER_METHOD] ?: 0),
            // ماسک بیت‌ها: ۱=صبح ۲=طلوع ۴=ظهر ۸=عصر ۱۶=غروب ۳۲=مغرب ۶۴=عشا
            prayerNotifications = decodeMask(p[Keys.PRAYER_NOTIF] ?: (1 or 4 or 32)),
            useCurrentLocation = p[Keys.USE_LOCATION] ?: false,
            adhanSound = p[Keys.ADHAN_SOUND] ?: "moazenzadeh",
            adminPin = p[Keys.ADMIN_PIN] ?: "",
            ongoingDateNotif = p[Keys.ONGOING_DATE] ?: true,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            profileGender = p[Keys.PROFILE_GENDER] ?: "",
            profileBirth = p[Keys.PROFILE_BIRTH] ?: "",
            githubToken = p[Keys.GITHUB_TOKEN] ?: ""
        )
    }

    /** خواندن همگام تنظیمات — فقط از نخ پس‌زمینه (BroadcastReceiver / coroutine IO) */
    fun settingsBlocking(): AppSettings = kotlinx.coroutines.runBlocking {
        settings.first()
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.ordinal }
    suspend fun setAccent(accent: AccentPalette) = context.dataStore.edit { it[Keys.ACCENT] = accent.ordinal }
    suspend fun setPersianDigits(b: Boolean) = context.dataStore.edit { it[Keys.PERSIAN_DIGITS] = b }
    suspend fun setShowHijri(b: Boolean) = context.dataStore.edit { it[Keys.SHOW_HIJRI] = b }
    suspend fun setCityIndex(i: Int) {
        context.dataStore.edit { it[Keys.CITY] = i }
        // ویجت هواشناسی — نسخه ۱.۱۱.۱۳: با تغییر شهر، کش باطل و ویجت تازه شود
        // (هرگز نباید ذخیره‌سازی تنظیمات را مختل کند)
        runCatching { ir.persiancalendar.app.widget.WeatherWidgetProvider.onCityChanged(context) }
    }
    suspend fun setPrayerMethod(m: PrayerMethod) = context.dataStore.edit { it[Keys.PRAYER_METHOD] = m.ordinal }
    suspend fun setPrayerNotifications(s: Set<Int>) =
        context.dataStore.edit { it[Keys.PRAYER_NOTIF] = encodeMask(s) }
    suspend fun setUseCurrentLocation(b: Boolean) = context.dataStore.edit { it[Keys.USE_LOCATION] = b }

    // ---------- نسخه ۱.۲: اذان دلخواه + پنل مدیریت ----------
    suspend fun setAdhanSound(key: String) = context.dataStore.edit { it[Keys.ADHAN_SOUND] = key }

    suspend fun setAdminPin(pin: String) = context.dataStore.edit { it[Keys.ADMIN_PIN] = pin }

    // ---------- نسخه ۱.۳: اعلان همیشگی ----------
    suspend fun setOngoingDateNotif(b: Boolean) =
        context.dataStore.edit { it[Keys.ONGOING_DATE] = b }

    // ---------- نسخه ۱.۱۱.۴: ویزارد راه‌اندازی اولیه ----------
    suspend fun setOnboardingDone(b: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = b }

    // ---------- نسخه ۱.۱۱.۱۶: مشخصات کاربر (گام پنجم راه‌اندازی — درخواست کاربر) ----------
    suspend fun setProfileGender(g: String) =
        context.dataStore.edit { it[Keys.PROFILE_GENDER] = g }

    suspend fun setProfileBirth(jy: Int, jm: Int, jd: Int) =
        context.dataStore.edit { it[Keys.PROFILE_BIRTH] = "$jy/$jm/$jd" }

    // ---------- نسخه ۱.۱۱.۱۶: توکن انتشار اطلاعیه (پنل مدیریت) ----------
    suspend fun setGithubToken(t: String) =
        context.dataStore.edit { it[Keys.GITHUB_TOKEN] = t.trim() }

    companion object {
        fun encodeMask(s: Set<Int>): Int = s.fold(0) { acc, i -> acc or (1 shl i) }
        fun decodeMask(mask: Int): Set<Int> =
            (0..6).filter { mask and (1 shl it) != 0 }.toSet()
    }
}
