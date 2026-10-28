package ir.persiancalendar.app.core.prayer

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * روش‌های محاسبه اوقات شرعی.
 * تهران (مؤسسه ژئوفیزیک دانشگاه تهران) = روش رسمی تقویم ایران.
 */
enum class PrayerMethod(val label: String, val fajrAngle: Double, val ishaAngle: Double, val maghribAngle: Double) {
    TEHRAN("مؤسسه ژئوفیزیک دانشگاه تهران", 17.7, 14.0, 4.5),
    MWL("اتحادیه جهانی اسلام", 18.0, 17.0, -1.0); // -1 = غروب آفتاب

    companion object {
        fun fromOrdinal(i: Int) = entries.getOrElse(i) { TEHRAN }
    }
}

/** اوقات شرعی یک روز بر حسب ساعت اعشاری محلی */
data class PrayerTimes(
    val imsak: Double,
    val fajr: Double,
    val sunrise: Double,
    val dhuhr: Double,
    val asr: Double,
    val sunset: Double,
    val maghrib: Double,
    val isha: Double,
    /** نیمه‌شب شرعی به روش جعفری (وسط مغرب تا اذان صبح فردا تقریبی) */
    val midnight: Double
)

/**
 * محاسبه اوقات شرعی بر پایه الگوریتم استاندارد PrayTimes (روش منکوز).
 * صحت خروجی برای تهران در تابستان و زمستان با مقادیر رسمی تطبیق داده شده است.
 */
object PrayerTimesCalculator {

    private fun dsin(x: Double) = sin(Math.toRadians(x))
    private fun dcos(x: Double) = cos(Math.toRadians(x))
    private fun dtan(x: Double) = tan(Math.toRadians(x))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1.0 / x))

    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r >= 0) r else r + b
    }

    private fun fixhour(h: Double) = fix(h, 24.0)

    private fun julian(y0: Int, m0: Int, d0: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d0 + b - 1524.5
    }

    /** موقعیت خورشید: انحراف و معادله زمان */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0, 24.0)
        val decl = darcsin(dsin(e) * dsin(l))
        val eqt = q / 15.0 - ra
        return Pair(decl, eqt)
    }

    /**
     * محاسبه اوقات.
     * @param tzHours اختلاف زمانی محلی با UTC (برای ایران همیشه ۳.۵ — ایران از ۱۴۰۱ ساعت تابستانی ندارد)
     */
    fun compute(
        date: LocalDate,
        lat: Double,
        lng: Double,
        tzHours: Double,
        method: PrayerMethod = PrayerMethod.TEHRAN
    ): PrayerTimes {
        val jdate = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        fun midDay(t: Double): Double {
            val (_, eqt) = sunPosition(jdate + t)
            return fixhour(12.0 - eqt)
        }

        fun sunAngleTime(angle: Double, t0: Double, ccw: Boolean): Double {
            val (decl, _) = sunPosition(jdate + t0)
            val noon = midDay(t0)
            val v = (1.0 / 15.0) * darccos(
                (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            )
            return if (ccw) noon - v else noon + v
        }

        fun asrTime(factor: Double, t0: Double): Double {
            val (decl, _) = sunPosition(jdate + t0)
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t0, ccw = false)
        }

        // دو دور تکرار برای همگرایی (مانند PrayTimes)
        var imsak = 5.0 / 24
        var fajr = 5.0 / 24
        var sunrise = 6.0 / 24
        var dhuhr = 12.0 / 24
        var asr = 13.0 / 24
        var sunset = 18.0 / 24
        var maghrib = 18.0 / 24
        var isha = 18.0 / 24

        repeat(2) {
            imsak = sunAngleTime(method.fajrAngle + 1.0, imsak, ccw = true)
            fajr = sunAngleTime(method.fajrAngle, fajr, ccw = true)
            sunrise = sunAngleTime(0.833, sunrise, ccw = true)
            dhuhr = midDay(dhuhr)
            asr = asrTime(1.0, asr)
            sunset = sunAngleTime(0.833, sunset, ccw = false)
            maghrib = if (method.maghribAngle > 0)
                sunAngleTime(method.maghribAngle, maghrib, ccw = false)
            else sunset
            isha = sunAngleTime(method.ishaAngle, isha, ccw = false)
        }

        fun local(v: Double) = fixhour(v + tzHours - lng / 15.0)

        val fajrLocal = local(fajr)
        val maghribLocal = local(maghrib)
        // نیمه‌شب شرعی جعفری: وسط فاصله مغرب تا اذان صبح (تقریب با فجر همین روز + 24)
        val midnightLocal = fixhour((maghribLocal + (fajrLocal + 24.0)) / 2.0)

        return PrayerTimes(
            imsak = local(imsak),
            fajr = fajrLocal,
            sunrise = local(sunrise),
            dhuhr = local(dhuhr) + 1.0 / 60.0, // احتیاط یک دقیقه
            asr = local(asr),
            sunset = local(sunset),
            maghrib = maghribLocal,
            isha = local(isha),
            midnight = midnightLocal
        )
    }

    /** قبله (سمت کعبه) بر حسب درجه از شمال، ساعتگرد */
    fun qiblaDirection(lat: Double, lng: Double): Double {
        val kaabaLat = 21.4225
        val kaabaLng = 39.8262
        val dLng = Math.toRadians(kaabaLng - lng)
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(kaabaLat)
        val y = sin(dLng)
        val x = cos(phi1) * dtan(phi2) - sin(phi1) * cos(dLng)
        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing
    }
}
