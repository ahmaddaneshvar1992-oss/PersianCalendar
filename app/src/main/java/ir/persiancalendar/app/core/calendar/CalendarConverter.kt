package ir.persiancalendar.app.core.calendar

/**
 * تبدیل بین گاه‌شماری‌های جلالی، میلادی و قمری.
 *
 * الگوریتم جلالی: پرت دقیق از کتابخانه مرجع jalaali-js (Borkowski)
 * که صحت آن برای بازه ۱۱۷۸ تا ۱۴۳۴ جلالی به‌طور کامل آزموده شده است.
 * الگوریتم قمری: الگوریتم جدولی کویت (دقت حدود یک روز).
 *
 * نکته مهم: تقسیم صحیح Kotlin («/») مانند ~~ در جاوااسکریپت به سمت صفر
 * گرد می‌کند؛ بنابراین پرت عیناً منطبق بر مرجع است.
 */
object CalendarConverter {

    // سال‌های آغاز قاعده ۳۳ ساله
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private fun div(a: Long, b: Long): Long = a / b
    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Long, b: Long): Long = a - b * div(a, b)
    private fun mod(a: Int, b: Int): Int = a - b * div(a, b)

    // ---------- جلالی ----------

    /**
     * محاسبه سال کبیسه و روز اول فروردین در مارس میلادی.
     * @return leap: فاصله از آخرین سال کبیسه (۰ یعنی خود سال کبیسه است)
     */
    private fun jalCal(jy: Int, withoutLeap: Boolean = false): Triple<Int, Int, Int> {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0

        for (i in 1 until bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp

        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1

        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG

        if (withoutLeap) return Triple(0, gy, march)

        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = div(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Triple(leap, gy, march)
    }

    fun isLeapJalaliYear(jy: Int): Boolean = jalCal(jy).first == 0

    fun jalaliMonthLength(jy: Int, jm: Int): Int =
        when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            isLeapJalaliYear(jy) -> 30
            else -> 29
        }

    /** شماره روز جولیَن از تاریخ میلادی */
    fun g2d(gy: Int, gm: Int, gd: Int): Long {
        var d = div((gy + div(gm - 8, 6) + 100100).toLong() * 1461L, 4L) +
                div(153L * mod(gm + 9, 12) + 2, 5L) + gd - 34840408L
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100).toLong() * 3L, 4L) + 752L
        return d
    }

    /** تاریخ میلادی از شماره روز جولیَن */
    fun d2g(jdn: Long): GregorianDate {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return GregorianDate(gy.toInt(), gm.toInt(), gd.toInt())
    }

    /** شماره روز جولیَن از تاریخ جلالی */
    fun j2d(jy: Int, jm: Int, jd: Int): Long {
        val (_, gy, march) = jalCal(jy, withoutLeap = true)
        return g2d(gy, 3, march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    /** تاریخ جلالی از شماره روز جولیَن */
    fun d2j(jdn: Long): JalaliDate {
        val gy = d2g(jdn).year
        var jy = gy - 621
        val (leap, _, march) = jalCal(jy)
        val jdn1f = g2d(gy, 3, march)
        var k = jdn - jdn1f

        if (k >= 0) {
            if (k <= 185) {
                // شش ماه اول سال
                return JalaliDate(jy, 1 + div(k, 31L).toInt(), (mod(k, 31L) + 1).toInt())
            }
            k -= 186
        } else {
            // سال جلالی قبلی
            jy -= 1
            k += 179
            if (leap == 1) k += 1
        }
        return JalaliDate(jy, (7 + div(k, 30L)).toInt(), (mod(k, 30L) + 1).toInt())
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): GregorianDate = d2g(j2d(jy, jm, jd))

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate = d2j(g2d(gy, gm, gd))

    // ---------- قمری (جدولی) ----------

    fun gregorianToHijri(gy: Int, gm: Int, gd: Int): HijriDate {
        val jd = g2d(gy, gm, gd)
        var l = jd - 1948440L + 10632L
        val n = div(l - 1, 10631L)
        l = l - 10631L * n + 354L
        val j = div(10985L - l, 5316L) * div(50L * l, 17719L) + div(l, 5670L) * div(43L * l, 15238L)
        l = l - div(30L - j, 15L) * div(17719L * j, 50L) - div(j, 16L) * div(15238L * j, 43L) + 29L
        val m = div(24L * l, 709L)
        val dd = l - div(709L * m, 24L)
        val y = 30L * n + j - 30L
        return HijriDate(y.toInt(), m.toInt(), dd.toInt())
    }

    fun hijriToGregorian(hy: Int, hm: Int, hd: Int): GregorianDate {
        // جست‌وجوی تقریبی روی شماره روز جولیَن تا رسیدن به تاریخ درست
        var jdn = 1948439L + (hy - 1) * 354L + (hy / 30) * 11L + hd
        for (i in 0 until 80) {
            val h = gregorianToHijri(d2g(jdn))
            if (h.year == hy && h.month == hm && h.day == hd) return d2g(jdn)
            val target = hy * 12L + hm
            val cur = h.year * 12L + h.month
            jdn += when {
                cur < target -> 25
                cur > target -> -25
                h.day < hd -> 1
                h.day > hd -> -1
                else -> 0
            }
        }
        return d2g(jdn)
    }

    fun hijriFromJalali(jy: Int, jm: Int, jd: Int): HijriDate {
        val g = jalaliToGregorian(jy, jm, jd)
        return gregorianToHijri(g.year, g.month, g.day)
    }

    fun jalaliFromHijri(hy: Int, hm: Int, hd: Int): JalaliDate {
        val g = hijriToGregorian(hy, hm, hd)
        return gregorianToJalali(g.year, g.month, g.day)
    }
}
