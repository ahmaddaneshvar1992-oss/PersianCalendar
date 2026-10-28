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

/** داده‌های تنظیمات به‌صورت یکجا برای UI */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPalette = AccentPalette.GREEN,
    val persianDigits: Boolean = true,
    val showHijri: Boolean = true,
    val cityIndex: Int = Cities.DEFAULT_INDEX,
    val prayerMethod: PrayerMethod = PrayerMethod.TEHRAN,
    val prayerNotifications: Set<Int> = setOf(1, 3, 5), // صبح، ظهر، مغرب
    val useCurrentLocation: Boolean = false,
    val interstitialCounter: Int = 0,
    // ---------- نسخه ۱.۲: اذان دلخواه + پنل مدیریت تبلیغات ----------
    /** صدای اذان: mashhad / hajjaghar / najaf / none */
    val adhanSound: String = "mashhad",
    /** کلید تبلیغات روشن باشد؟ (پنل مدیریت) */
    val adsEnabled: Boolean = true,
    /** اینترستیشال فعال باشد؟ */
    val interstitialEnabled: Boolean = true,
    /** هر چند باز شدن جزئیات روز یک‌بار */
    val interstitialEvery: Int = 3,
    /** شناسه‌های تست گوگل (پیش از انتشار خاموش شود) */
    val adTestMode: Boolean = true,
    /** شناسه واقعی بنر (در حالت غیرآزمایشی) */
    val bannerUnitId: String = "",
    /** شناسه واقعی اینترستیشال (در حالت غیرآزمایشی) */
    val interstitialUnitId: String = "",
    /** رمز پنل مدیریت (حروف و/یا اعداد — نسخه ۱.۳) */
    val adminPin: String = "1234",
    // ---------- نسخه ۱.۳: بنر تصویری + اعلان همیشگی ----------
    /** مسیر عکس بنر اختصاصی (در filesDir) — خالی = بنر AdMob */
    val bannerImagePath: String = "",
    /** لینک بنر اختصاصی (با لمس باز می‌شود) */
    val bannerLink: String = "",
    /** اعلان همیشگی تاریخ امروز در نوار وضعیت */
    val ongoingDateNotif: Boolean = true,
    // ---------- نسخه ۱.۴: بنرهای چرخشی (تا ۳ بنر) ----------
    /** مسیر عکس بنر دوم و سوم + لینک هر بنر */
    val bannerImagePath2: String = "",
    val bannerImagePath3: String = "",
    val bannerLink2: String = "",
    val bannerLink3: String = "",
    // ---------- نسخه ۱.۵: بنرهای تصویری بدون محدودیت (JSON) ----------
    /** فهرست بنرها به‌صورت JSON: [{"p":مسیر,"l":لینک,"on":روشن}] */
    val bannerListJson: String = ""
)

/** یک بنر تصویری اختصاصی (نسخه ۱.۵) */
data class BannerEntry(val path: String, val link: String, val on: Boolean)

/**
 * فهرست بنرهای تصویری از تنظیمات — نسخه ۱.۵
 * اگر JSON جدید خالی باشد، از اسلات‌های ۳تایی نسخه ۱.۳/۱.۴ مهاجرت می‌کند (بدون از دست رفتن بنرها).
 */
fun AppSettings.bannerEntries(): List<BannerEntry> {
    if (bannerListJson.isNotBlank()) {
        return runCatching {
            val arr = org.json.JSONArray(bannerListJson)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                BannerEntry(o.optString("p"), o.optString("l"), o.optBoolean("on", true))
            }.filter { it.path.isNotBlank() }
        }.getOrDefault(emptyList())
    }
    return listOf(
        bannerImagePath to bannerLink,
        bannerImagePath2 to bannerLink2,
        bannerImagePath3 to bannerLink3
    ).filter { it.first.isNotBlank() }.map { BannerEntry(it.first, it.second, true) }
}

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
        val INTERSTITIAL = intPreferencesKey("interstitial_counter")
        // ---------- نسخه ۱.۲ ----------
        val ADHAN_SOUND = stringPreferencesKey("adhan_sound")
        val ADS_ENABLED = booleanPreferencesKey("ads_enabled")
        val INTERSTITIAL_ENABLED = booleanPreferencesKey("interstitial_enabled")
        val INTERSTITIAL_EVERY = intPreferencesKey("interstitial_every")
        val AD_TEST_MODE = booleanPreferencesKey("ad_test_mode")
        val BANNER_UNIT = stringPreferencesKey("banner_unit_id")
        val INTERSTITIAL_UNIT = stringPreferencesKey("interstitial_unit_id")
        val ADMIN_PIN = stringPreferencesKey("admin_pin"),
        // ---------- نسخه ۱.۳ ----------
        val BANNER_IMG = stringPreferencesKey("banner_image_path"),
        val BANNER_LINK = stringPreferencesKey("banner_link"),
        val ONGOING_DATE = booleanPreferencesKey("ongoing_date_notif"),
        // ---------- نسخه ۱.۴ ----------
        val BANNER_IMG2 = stringPreferencesKey("banner_image_path2"),
        val BANNER_IMG3 = stringPreferencesKey("banner_image_path3"),
        val BANNER_LINK2 = stringPreferencesKey("banner_link2"),
        val BANNER_LINK3 = stringPreferencesKey("banner_link3"),
        // ---------- نسخه ۱.۵ ----------
        val BANNER_LIST_JSON = stringPreferencesKey("banner_list_json")
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
            interstitialCounter = p[Keys.INTERSTITIAL] ?: 0,
            adhanSound = p[Keys.ADHAN_SOUND] ?: "mashhad",
            adsEnabled = p[Keys.ADS_ENABLED] ?: true,
            interstitialEnabled = p[Keys.INTERSTITIAL_ENABLED] ?: true,
            interstitialEvery = (p[Keys.INTERSTITIAL_EVERY] ?: 3).coerceIn(1, 10),
            adTestMode = p[Keys.AD_TEST_MODE] ?: true,
            bannerUnitId = p[Keys.BANNER_UNIT] ?: "",
            interstitialUnitId = p[Keys.INTERSTITIAL_UNIT] ?: "",
            adminPin = p[Keys.ADMIN_PIN] ?: "1234",
            bannerImagePath = p[Keys.BANNER_IMG] ?: "",
            bannerLink = p[Keys.BANNER_LINK] ?: "",
            ongoingDateNotif = p[Keys.ONGOING_DATE] ?: true,
            bannerImagePath2 = p[Keys.BANNER_IMG2] ?: "",
            bannerImagePath3 = p[Keys.BANNER_IMG3] ?: "",
            bannerLink2 = p[Keys.BANNER_LINK2] ?: "",
            bannerLink3 = p[Keys.BANNER_LINK3] ?: "",
            bannerListJson = p[Keys.BANNER_LIST_JSON] ?: ""
        )
    }

    /** خواندن همگام تنظیمات — فقط از نخ پس‌زمینه (BroadcastReceiver / coroutine IO) */
    fun settingsBlocking(): AppSettings = kotlinx.coroutines.runBlocking {
        context.dataStore.data.first()
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.ordinal }
    suspend fun setAccent(accent: AccentPalette) = context.dataStore.edit { it[Keys.ACCENT] = accent.ordinal }
    suspend fun setPersianDigits(b: Boolean) = context.dataStore.edit { it[Keys.PERSIAN_DIGITS] = b }
    suspend fun setShowHijri(b: Boolean) = context.dataStore.edit { it[Keys.SHOW_HIJRI] = b }
    suspend fun setCityIndex(i: Int) = context.dataStore.edit { it[Keys.CITY] = i }
    suspend fun setPrayerMethod(m: PrayerMethod) = context.dataStore.edit { it[Keys.PRAYER_METHOD] = m.ordinal }
    suspend fun setPrayerNotifications(s: Set<Int>) =
        context.dataStore.edit { it[Keys.PRAYER_NOTIF] = encodeMask(s) }
    suspend fun setUseCurrentLocation(b: Boolean) = context.dataStore.edit { it[Keys.USE_LOCATION] = b }

    suspend fun incrementInterstitialCounter() {
        context.dataStore.edit { it[Keys.INTERSTITIAL] = (it[Keys.INTERSTITIAL] ?: 0) + 1 }
    }

    // ---------- نسخه ۱.۲: اذان دلخواه + پنل مدیریت تبلیغات ----------
    suspend fun setAdhanSound(key: String) = context.dataStore.edit { it[Keys.ADHAN_SOUND] = key }

    suspend fun setAdsEnabled(b: Boolean) = context.dataStore.edit { it[Keys.ADS_ENABLED] = b }

    suspend fun setInterstitialEnabled(b: Boolean) =
        context.dataStore.edit { it[Keys.INTERSTITIAL_ENABLED] = b }

    suspend fun setInterstitialEvery(n: Int) =
        context.dataStore.edit { it[Keys.INTERSTITIAL_EVERY] = n.coerceIn(1, 10) }

    suspend fun setAdTestMode(b: Boolean) = context.dataStore.edit { it[Keys.AD_TEST_MODE] = b }

    suspend fun setBannerUnitId(id: String) = context.dataStore.edit { it[Keys.BANNER_UNIT] = id.trim() }

    suspend fun setInterstitialUnitId(id: String) =
        context.dataStore.edit { it[Keys.INTERSTITIAL_UNIT] = id.trim() }

    suspend fun setAdminPin(pin: String) = context.dataStore.edit { it[Keys.ADMIN_PIN] = pin }

    // ---------- نسخه ۱.۳: بنر تصویری + اعلان همیشگی ----------
    suspend fun setBannerImagePath(path: String) =
        context.dataStore.edit { it[Keys.BANNER_IMG] = path.trim() }

    suspend fun setBannerLink(link: String) =
        context.dataStore.edit { it[Keys.BANNER_LINK] = link.trim() }

    suspend fun setOngoingDateNotif(b: Boolean) =
        context.dataStore.edit { it[Keys.ONGOING_DATE] = b }

    // ---------- نسخه ۱.۴: بنرهای چرخشی ----------
    suspend fun setBannerImagePath2(path: String) =
        context.dataStore.edit { it[Keys.BANNER_IMG2] = path.trim() }

    suspend fun setBannerImagePath3(path: String) =
        context.dataStore.edit { it[Keys.BANNER_IMG3] = path.trim() }

    suspend fun setBannerLink2(link: String) =
        context.dataStore.edit { it[Keys.BANNER_LINK2] = link.trim() }

    suspend fun setBannerLink3(link: String) =
        context.dataStore.edit { it[Keys.BANNER_LINK3] = link.trim() }

    // ---------- نسخه ۱.۵: بنرهای تصویری بدون محدودیت ----------
    suspend fun setBannerListJson(json: String) =
        context.dataStore.edit { it[Keys.BANNER_LIST_JSON] = json.trim() }

    companion object {
        fun encodeMask(s: Set<Int>): Int = s.fold(0) { acc, i -> acc or (1 shl i) }
        fun decodeMask(mask: Int): Set<Int> =
            (0..6).filter { mask and (1 shl it) != 0 }.toSet()
    }
}
