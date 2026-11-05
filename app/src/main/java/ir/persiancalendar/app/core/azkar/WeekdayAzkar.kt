package ir.persiancalendar.app.core.azkar

import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.PersianText
import java.time.LocalDate

/**
 * ذکر روزهای هفته — نسخهٔ ۱.۱۱.۴۱ (درخواست کاربر)
 *
 * برای هر روز هفته یک ذکر ثابت در نظر گرفته شده است:
 *
 *   شنبه      → یا رب العالمین
 *   یکشنبه   → یا ذالجلال والاکرام
 *   دوشنبه   → یا قاضی الحاجات
 *   سه‌شنبه  → یا ارحم الراحمین
 *   چهارشنبه → یا حی یا قیوم
 *   پنجشنبه  → لا اله الا الله الملک الحق المبین
 *   جمعه      → اللهم صل علی محمد و آل محمد
 *
 * **متن‌های ذکر عمداً در `app/src/main/res/values/strings.xml` نگهداری
 * می‌شوند (کلیدهای `azkar_saturday` تا `azkar_friday`) تا بازبینی بعدیِ
 * متن‌ها فقط با ویرایش همان فایل انجام شود — بدون تغییر کد.** این شیء
 * فقط نگاشت «روز → شناسهٔ رشته» است و هیچ متنی را تکرار نمی‌کند.
 *
 * اندیس‌گذاری شنبه‌محور است (۰=شنبه … ۶=جمعه) و با
 * `PersianText.weekdayIndexSat0` سازگار — همان قرارداد نوار نمای هفتگی.
 *
 * هیچ وابستگی به Compose/Context ندارد (تست‌پذیری JVM) و داده‌ای به
 * شبکه نمی‌رود.
 */
object WeekdayAzkar {

    /**
     * شناسهٔ رشتهٔ ذکر بر اساس اندیس شنبه‌محور ۰..۶؛
     * ورودی بیرون از بازه گیره می‌شود (بدون کرش).
     */
    fun resOfIndex(index: Int): Int = AZKAR_RES[index.coerceIn(0, 6)]

    /** شناسهٔ رشتهٔ ذکرِ یک تاریخ مشخص (میلادی) — برای پنل جزئیات روز */
    fun resOfDay(date: LocalDate): Int = resOfIndex(PersianText.weekdayIndexSat0(date))

    // شنبه=۰، یکشنبه=۱، دوشنبه=۲، سه‌شنبه=۳، چهارشنبه=۴، پنجشنبه=۵، جمعه=۶
    // (عمداً پیش از ALL تعریف شده — Kotlin پراپرتی‌ها را به ترتیب فایل مقداردهی می‌کند)
    private val AZKAR_RES = listOf(
        R.string.azkar_saturday,
        R.string.azkar_sunday,
        R.string.azkar_monday,
        R.string.azkar_tuesday,
        R.string.azkar_wednesday,
        R.string.azkar_thursday,
        R.string.azkar_friday
    )

    /** هر هفت شناسه به ترتیب شنبه تا جمعه — برای تست تمایز و پوشش */
    val ALL: List<Int> = AZKAR_RES.toList()
}
