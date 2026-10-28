package ir.persiancalendar.app.core.events

import android.content.Context
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.HijriDate
import ir.persiancalendar.app.core.calendar.JalaliDate
import org.json.JSONObject
import java.time.LocalDate

/**
 * منبع مناسبت‌ها و تعطیلات رسمی ایران — کاملاً آفلاین.
 *
 * ترکیب چهار منبع:
 *  ۱. مناسبت‌های ثابت ملی (تقویم جلالی، هر سال تکرار می‌شود)
 *  ۲. مناسبت‌های ثابت میلادی (مانند روز جهانی کارگر)
 *  ۳. مناسبت‌های مذهبی قمری که به‌صورت محاسباتی برای هر سال به‌دست می‌آیند
 *     (دقت جدولی حدود یک روز — برای سال‌های دارای فایل رسمی، مورد ۴ جایگزین می‌شود)
 *  ۴. فایل assets/holidays.json با تاریخ‌های رسمی اعلام‌شده (۱۴۰۴ تا ۱۴۰۶)
 *     که هر زمان بخواهید می‌توانید آن را ویرایش/به‌روزرسانی کنید.
 *  ۵. فایل assets/qamar_dar_aqrab.json با روزها و ساعت ورود/خروج ماه به برج عقرب
 *     (قمر در عقرب — نسخه ۱.۱۱.۹، درخواست کاربر؛ فعلاً سال ۱۴۰۵ مطابق جدول ارسالی)
 */
class HolidayRepository(context: Context) {

    private data class FixedJalali(val month: Int, val day: Int, val title: String, val holiday: Boolean)
    private data class FixedGregorian(val month: Int, val day: Int, val title: String, val holiday: Boolean)
    private data class FixedHijri(val month: Int, val day: Int, val title: String, val holiday: Boolean, val lastDayFallback: Boolean = false)

    // ---------- ۱) مناسبت‌های ثابت ملی ----------
    private val national = listOf(
        FixedJalali(1, 1, "جشن نوروز - آغاز سال نو", true),
        FixedJalali(1, 2, "عید نوروز", true),
        FixedJalali(1, 3, "عید نوروز", true),
        FixedJalali(1, 4, "عید نوروز", true),
        FixedJalali(1, 12, "روز جمهوری اسلامی", true),
        FixedJalali(1, 13, "سیزده‌بدر - روز طبیعت", true),
        FixedJalali(1, 20, "روز ملی فناوری هسته‌ای", false),
        FixedJalali(2, 1, "بزرگداشت سعدی", false),
        FixedJalali(2, 25, "روز بزرگداشت فردوسی و پاسداشت زبان فارسی", false),
        FixedJalali(2, 28, "روز بزرگداشت حکیم عمر خیام", false),
        FixedJalali(3, 14, "رحلت امام خمینی", true),
        FixedJalali(3, 15, "قیام ۱۵ خرداد", true),
        FixedJalali(4, 10, "روز ملی صنعت و معدن", false),
        FixedJalali(5, 17, "روز خبرنگار", false),
        FixedJalali(6, 1, "روز پزشک - بزرگداشت ابن‌سینا", false),
        FixedJalali(6, 5, "روز داروسازی - بزرگداشت زکریای رازی", false),
        FixedJalali(6, 13, "روز تعاون", false),
        FixedJalali(6, 27, "روز شعر و ادب فارسی - بزرگداشت شهریار", false),
        FixedJalali(7, 8, "روز بزرگداشت مولوی", false),
        FixedJalali(7, 20, "بزرگداشت حافظ", false),
        FixedJalali(8, 13, "روز دانش‌آموز", false),
        FixedJalali(8, 24, "روز کتاب و کتاب‌خوانی", false),
        FixedJalali(9, 5, "روز مهندسی - بزرگداشت خواجه نصیرالدین طوسی", false),
        FixedJalali(9, 16, "روز دانشجو", false),
        FixedJalali(9, 24, "روز پژوهش", false),
        FixedJalali(9, 30, "شب یلدا - بلندترین شب سال", false),
        FixedJalali(11, 8, "روز نیروی هوایی", false),
        FixedJalali(11, 22, "پیروزی انقلاب اسلامی", true),
        FixedJalali(12, 25, "روز بزرگداشت پروین اعتصامی", false),
        FixedJalali(12, 29, "ملی شدن صنعت نفت ایران", true)
    )

    // ---------- ۲) مناسبت‌های ثابت میلادی ----------
    private val gregorianFixed = listOf(
        FixedGregorian(5, 1, "روز جهانی کارگر", true),
        FixedGregorian(12, 25, "کریسمس", false)
    )

    // ---------- ۳) مناسبت‌های مذهبی قمری (محاسباتی) ----------
    private val lunar = listOf(
        FixedHijri(1, 9, "تاسوعای حسینی", true),
        FixedHijri(1, 10, "عاشورای حسینی", true),
        FixedHijri(2, 20, "اربعین حسینی", true),
        FixedHijri(2, 28, "رحلت رسول خدا و شهادت امام حسن مجتبی", true),
        FixedHijri(2, 30, "شهادت امام رضا", true, lastDayFallback = true),
        FixedHijri(3, 8, "شهادت امام حسن عسکری", false),
        FixedHijri(3, 17, "میلاد رسول خدا و امام جعفر صادق", true),
        FixedHijri(5, 5, "ولادت حضرت زینب - روز پرستار", false),
        FixedHijri(6, 3, "شهادت حضرت فاطمه زهرا", true),
        FixedHijri(7, 10, "ولادت امام محمد تقی - جواد الائمه", false),
        FixedHijri(7, 13, "ولادت امام علی", true),
        FixedHijri(7, 25, "شهادت امام موسی کاظم", false),
        FixedHijri(7, 27, "مبعث رسول خدا", true),
        FixedHijri(8, 3, "ولادت امام حسین", false),
        FixedHijri(8, 4, "ولادت حضرت ابوالفضل العباس - روز جانباز", false),
        FixedHijri(8, 5, "ولادت امام سجاد", false),
        FixedHijri(8, 15, "نیمه شعبان - میلاد امام زمان", true),
        FixedHijri(9, 1, "آغاز ماه مبارک رمضان", false),
        FixedHijri(9, 15, "ولادت امام حسن مجتبی", false),
        FixedHijri(9, 19, "شب قدر", false),
        FixedHijri(9, 21, "شهادت امام علی - شب قدر", true),
        FixedHijri(9, 23, "شب قدر", false),
        FixedHijri(10, 1, "عید سعید فطر", true),
        FixedHijri(10, 2, "تعطیل به مناسبت عید سعید فطر", true),
        FixedHijri(10, 25, "شهادت امام جعفر صادق", true),
        FixedHijri(11, 18, "عید سعید غدیر خم", true),
        FixedHijri(12, 10, "عید سعید قربان", true)
    )

    // ---------- ۴) تاریخ‌های رسمی از فایل JSON ----------
    private val curated = HashMap<String, MutableList<Occasion>>() // key: "jy-jm-jd"
    private val curatedTitlesByYear = HashMap<Int, MutableSet<String>>()

    // ---------- ۵) قمر در عقرب (نسخه ۱.۱۱.۹ — درخواست کاربر «میخوام روزهای قمر در عقرب در مناسبت های این روز ثبت کنی») ----------
    /** یک دوره قمر در عقرب: ورود ماه به برج عقرب تا خروج از آن */
    private data class QamarPeriod(
        val jy: Int, val enterM: Int, val enterD: Int, val enterTime: String,
        val exitM: Int, val exitD: Int, val exitTime: String
    )

    private val qamarPeriods = mutableListOf<QamarPeriod>()

    init {
        runCatching {
            val json = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.getJSONArray("holidays")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val jy = o.getInt("jy")
                val jm = o.getInt("jm")
                val jd = o.getInt("jd")
                val title = o.getString("title")
                val holiday = o.optBoolean("holiday", false)
                val key = "$jy-$jm-$jd"
                curated.getOrPut(key) { mutableListOf() }.add(Occasion(title, holiday))
                curatedTitlesByYear.getOrPut(jy) { mutableSetOf() }.add(title)
            }
        }
        runCatching {
            val json = context.assets.open("qamar_dar_aqrab.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONArray("periods")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                qamarPeriods.add(
                    QamarPeriod(
                        o.getInt("jy"), o.getInt("enterM"), o.getInt("enterD"), o.getString("enterTime"),
                        o.getInt("exitM"), o.getInt("exitD"), o.getString("exitTime")
                    )
                )
            }
        }
    }

    /** طول ماه قمری با الگوریتم جدولی */
    private fun hijriMonthLength(hy: Int, hm: Int): Int {
        val next = if (hm == 12) HijriDate(hy + 1, 1, 1) else HijriDate(hy, hm + 1, 1)
        val g1 = CalendarConverter.hijriToGregorian(hy, hm, 1)
        val g2 = CalendarConverter.hijriToGregorian(next.year, next.month, next.day)
        val d1 = CalendarConverter.g2d(g1.year, g1.month, g1.day)
        val d2 = CalendarConverter.g2d(g2.year, g2.month, g2.day)
        return (d2 - d1).toInt()
    }

    /** مناسبت‌های قمر در عقرب این روز (ورود/میانی/خروج — منبع ۵) */
    private fun qamarOccasions(jalali: JalaliDate): List<Occasion> {
        if (qamarPeriods.isEmpty()) return emptyList()
        val v = jalali.month * 100 + jalali.day
        val out = mutableListOf<Occasion>()
        for (p in qamarPeriods) {
            if (p.jy != jalali.year) continue
            val enter = p.enterM * 100 + p.enterD
            val exit = p.exitM * 100 + p.exitD
            if (v < enter || v > exit) continue
            out.add(
                when (v) {
                    enter -> Occasion("قمر در عقرب — ورود ماه به برج عقرب ساعت ${p.enterTime}", false)
                    exit -> Occasion("قمر در عقرب — خروج ماه از برج عقرب ساعت ${p.exitTime}", false)
                    else -> Occasion("قمر در عقرب", false)
                }
            )
        }
        return out
    }

    /** همه مناسبت‌های یک روز جلالی */
    fun occasionsFor(jalali: JalaliDate, gregorian: LocalDate): List<Occasion> {
        val result = mutableListOf<Occasion>()

        // منبع ۴: فایل رسمی
        curated["${jalali.year}-${jalali.month}-${jalali.day}"]?.let { result.addAll(it) }

        // منبع ۱: ثابت‌های ملی
        national.filter { it.month == jalali.month && it.day == jalali.day }.forEach {
            // اگر همین عنوان در فایل رسمی سال موجود است، تکرار نکن
            if (!curatedTitlesByYear[jalali.year].orEmpty().contains(it.title))
                result.add(Occasion(it.title, it.holiday))
        }

        // منبع ۲: ثابت‌های میلادی
        gregorianFixed.filter { it.month == gregorian.monthValue && it.day == gregorian.dayOfMonth }.forEach {
            result.add(Occasion(it.title, it.holiday))
        }

        // منبع ۳: محاسباتی قمری (فقط اگر عنوان مشابهی در فایل رسمی سال نباشد)
        val yearTitles = curatedTitlesByYear[jalali.year].orEmpty()
        val h = CalendarConverter.gregorianToHijri(gregorian.year, gregorian.monthValue, gregorian.dayOfMonth)
        if (h.month in 1..12) {
            val monthLen = hijriMonthLength(h.year, h.month)
            lunar.filter { it.month == h.month }.forEach { occ ->
                val matches = if (occ.lastDayFallback)
                    h.day == monthLen.coerceAtMost(occ.day)
                else
                    h.day == occ.day
                if (matches && !yearTitles.contains(occ.title))
                    result.add(Occasion(occ.title, occ.holiday))
            }
        }

        // منبع ۵: قمر در عقرب (در پایان فهرست؛ تعطیل رسمی نیست)
        result.addAll(qamarOccasions(jalali))

        return result
    }

    /** آیا این روز تعطیل رسمی است؟ (جمعه همیشه تعطیل است) */
    fun isHoliday(jalali: JalaliDate, gregorian: LocalDate): Boolean =
        gregorian.dayOfWeek == java.time.DayOfWeek.FRIDAY ||
                occasionsFor(jalali, gregorian).any { it.isHoliday }
}
