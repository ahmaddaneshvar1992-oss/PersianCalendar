package ir.persiancalendar.app.weather

import android.content.Context
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * مخزن هواشناسی — نسخه ۱.۱۱.۱۳ (درخواست کاربر: «یک ویجت هواشناسی برام بساز مطابق شهر انتخاب شده»)
 *
 *  - داده از سرویس رایگان و بی‌کلید Open-Meteo (api.open-meteo.com) گرفته می‌شود؛
 *    مختصات از شهر انتخابی کاربر (Cities.list[cityIndex]) خوانده می‌شود.
 *  - کش فایلی در filesDir با عمر ۳۰ دقیقه؛ با تغییر شهر کش باطل می‌شود.
 *  - هیچ‌وقت استثنا به بیرون پرتاب نمی‌کند؛ در نبود اینترنت null برمی‌گردد
 *    تا ویجت از کش (هرچند کهنه) استفاده کند.
 */
object WeatherRepository {

    /** عمر مجاز کش: ۳۰ دقیقه (هم‌راستا با دوره به‌روزرسانی ویجت) */
    private const val MAX_AGE_MS = 30 * 60_000L

    /** داده هوای یک شهر — همه اعداد گردشده و آماده نمایش */
    data class WeatherData(
        val cityIndex: Int,
        val temperature: Int,      // دمای فعلی (سلسیوس)
        val apparent: Int,         // دمای احساسی
        val humidity: Int,         // رطوبت درصد
        val windKmh: Int,          // باد کیلومتر/ساعت
        val code: Int,             // کد WMO فعلی
        val isDay: Boolean,
        val tMax: Int,             // بیشترین امروز
        val tMin: Int,             // کمترین امروز
        val tomorrowCode: Int,     // کد فردا
        val tomorrowMax: Int,      // Int.MIN_VALUE = موجود نیست
        val tomorrowMin: Int,
        val fetchedAt: Long        // میلی‌ثانیه — برای «آخرین به‌روزرسانی»
    )

    // ---------------- کش ----------------

    private fun cacheFile(context: Context) = File(context.filesDir, "weather_cache.json")

    /** کش باطل شود (مثلاً بعد از تغییر شهر) */
    fun invalidate(context: Context) {
        runCatching { cacheFile(context).delete() }
    }

    /** آخرین داده موجود — حتی کهنه (برای رندر فوری ویجت) */
    fun cached(context: Context): WeatherData? = readCache(context)?.second

    /** داده هوا؛ از کش تازه استفاده می‌کند، وگرنه دریافت می‌کند (شبکه فقط در IO) */
    suspend fun current(context: Context, cityIndex: Int, force: Boolean = false): WeatherData? =
        withContext(Dispatchers.IO) {
            val cached = readCache(context)
            val age = cached?.first ?: Long.MAX_VALUE
            val data = cached?.second
            val sameCity = data != null && data.cityIndex == cityIndex
            if (!force && sameCity && age < MAX_AGE_MS) return@withContext data

            val fresh = fetch(cityIndex)
            if (fresh != null) {
                writeCache(context, fresh)
                fresh
            } else {
                // در نبود اینترنت، کش کهنهٔ همان شهر بهتر از هیچ است
                if (sameCity) data else null
            }
        }

    private fun readCache(context: Context): Pair<Long, WeatherData>? = runCatching {
        val f = cacheFile(context)
        if (!f.exists()) return@runCatching null
        val o = JSONObject(f.readText())
        val data = fromJson(o.getJSONObject("payload"), o.getLong("fetchedAt"))
            ?: return@runCatching null
        System.currentTimeMillis() - o.getLong("fetchedAt") to data
    }.getOrNull()

    private fun writeCache(context: Context, d: WeatherData) = runCatching {
        val o = JSONObject()
        o.put("fetchedAt", d.fetchedAt)
        o.put("payload", toJson(d))
        cacheFile(context).writeText(o.toString())
    }

    // ---------------- دریافت از Open-Meteo ----------------

    private fun fetch(cityIndex: Int): WeatherData? = runCatching {
        val city = Cities.list[cityIndex.coerceIn(0, Cities.list.size - 1)]
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${city.lat}&longitude=${city.lng}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=2&timezone=auto"
        val conn = (java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BadKhazan-Calendar/1.11.13")
        }
        val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        parse(body, cityIndex)
    }.getOrNull()

    // ---------------- JSON ----------------

    private fun parse(body: String, cityIndex: Int): WeatherData? = runCatching {
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val tMaxArr = daily.getJSONArray("temperature_2m_max")
        val tMinArr = daily.getJSONArray("temperature_2m_min")
        val codeArr = daily.getJSONArray("weather_code")
        WeatherData(
            cityIndex = cityIndex,
            temperature = Math.round(cur.getDouble("temperature_2m")).toInt(),
            apparent = Math.round(cur.getDouble("apparent_temperature")).toInt(),
            humidity = cur.optInt("relative_humidity_2m", 0),
            windKmh = Math.round(cur.getDouble("wind_speed_10m")).toInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            tMax = Math.round(tMaxArr.getDouble(0)).toInt(),
            tMin = Math.round(tMinArr.getDouble(0)).toInt(),
            tomorrowCode = if (codeArr.length() > 1) codeArr.getInt(1) else 0,
            tomorrowMax = if (tMaxArr.length() > 1) Math.round(tMaxArr.getDouble(1)).toInt() else Int.MIN_VALUE,
            tomorrowMin = if (tMinArr.length() > 1) Math.round(tMinArr.getDouble(1)).toInt() else Int.MIN_VALUE,
            fetchedAt = System.currentTimeMillis()
        )
    }.getOrNull()

    private fun toJson(d: WeatherData): JSONObject = JSONObject().apply {
        put("cityIndex", d.cityIndex)
        put("temperature", d.temperature)
        put("apparent", d.apparent)
        put("humidity", d.humidity)
        put("windKmh", d.windKmh)
        put("code", d.code)
        put("isDay", d.isDay)
        put("tMax", d.tMax)
        put("tMin", d.tMin)
        put("tomorrowCode", d.tomorrowCode)
        put("tomorrowMax", d.tomorrowMax)
        put("tomorrowMin", d.tomorrowMin)
    }

    private fun fromJson(o: JSONObject, fetchedAt: Long): WeatherData? = runCatching {
        WeatherData(
            cityIndex = o.getInt("cityIndex"),
            temperature = o.getInt("temperature"),
            apparent = o.getInt("apparent"),
            humidity = o.getInt("humidity"),
            windKmh = o.getInt("windKmh"),
            code = o.getInt("code"),
            isDay = o.getBoolean("isDay"),
            tMax = o.getInt("tMax"),
            tMin = o.getInt("tMin"),
            tomorrowCode = o.optInt("tomorrowCode", 0),
            tomorrowMax = o.optInt("tomorrowMax", Int.MIN_VALUE),
            tomorrowMin = o.optInt("tomorrowMin", Int.MIN_VALUE),
            fetchedAt = fetchedAt
        )
    }.getOrNull()

    // ---------------- کدهای WMO → فارسی ----------------

    /** شرح فارسی وضعیت هوا بر اساس کد WMO */
    fun labelOf(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "صاف" else "صاف و پرستاره"
        1 -> "عمدتاً صاف"
        2 -> "نیمه‌ابری"
        3 -> "ابری"
        45, 48 -> "مه‌آلود"
        51, 53, 55 -> "نم‌نم باران"
        56, 57 -> "باران یخ‌زده"
        61 -> "باران کم"
        63 -> "باران"
        65 -> "باران شدید"
        66, 67 -> "باران یخ‌زده"
        71 -> "بارش کم برف"
        73 -> "برف"
        75 -> "برف سنگین"
        77 -> "دانه‌های برف"
        80, 81 -> "رگبار باران"
        82 -> "رگبار باران شدید"
        85, 86 -> "رگبار برف"
        95 -> "رعد و برق"
        96, 99 -> "رعد و برق با تگرگ"
        else -> "نامشخص"
    }

    /** ایموجی متناسب با وضعیت (در ویجت به‌جای تصویر استفاده می‌شود) */
    fun emojiOf(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "🌨️"
        in 80..82 -> "🌦️"
        85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** ساعت آخرین به‌روزرسانی به شکل «۱۴:۳۰» با ارقام فارسی */
    fun updatedTimeText(d: WeatherData): String {
        val t = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(d.fetchedAt),
            ZoneId.systemDefault()
        ).toLocalTime()
        return PersianText.toPersianDigits(t.format(DateTimeFormatter.ofPattern("HH:mm")))
    }
}
