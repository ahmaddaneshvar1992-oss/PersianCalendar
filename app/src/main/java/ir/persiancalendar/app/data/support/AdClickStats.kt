package ir.persiancalendar.app.data.support

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * شمارندهٔ لمس بنرها — نسخهٔ ۱.۱۱.۵۱ (درخواست کاربر: پنل مدیریت پیشرفته‌تر)
 *
 * هر لمسِ بنر/کارت تبلیغ با مقصد معتبر (خارجی یا داخلی) در همین دستگاه ثبت
 * می‌شود و مدیر در پنل (تب «تبلیغات») کنار هر بنر و در کارت آمار می‌بیند.
 * خوراکِ راه‌دور ایستاست (مخزن گیت‌هاب) و آمار کاربران را نمی‌تواند به مدیر
 * برگرداند — این شمارنده فقط لمس‌های دستگاه مدیر است و چراغ سلامت بنر.
 *
 * فایل: ads_click_stats.json = { "banner-12": 7, ... }
 * سقفِ امن: شناسه‌ها clamp شده و تعداد کلیدها حداکثر ۵۰۰ (محافظ دیسک).
 */
object AdClickStats {

    private const val FILE_NAME = "ads_click_stats.json"
    private const val MAX_KEYS = 500

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): MutableMap<String, Int> = runCatching {
        val f = file(context)
        if (!f.exists()) return mutableMapOf()
        val o = JSONObject(f.readText())
        val m = mutableMapOf<String, Int>()
        o.keys().forEach { k ->
            if (k.isNotBlank() && k.length <= 80 && m.size < MAX_KEYS)
                m[k] = o.optInt(k, 0).coerceIn(0, Int.MAX_VALUE / 2)
        }
        m
    }.getOrDefault(mutableMapOf())

    private fun write(context: Context, map: Map<String, Int>) {
        runCatching {
            val o = JSONObject()
            map.entries.take(MAX_KEYS).forEach { o.put(it.key, it.value) }
            file(context).writeText(o.toString())
        }
    }

    /** یک لمس ثبت می‌شود — شناسهٔ نامعتبر نادیده گرفته می‌شود */
    fun record(context: Context, id: String) {
        if (id.isBlank() || id.length > 80) return
        val m = read(context)
        m[id] = (m[id] ?: 0) + 1
        write(context, m)
    }

    /** همهٔ شمارنده‌ها — برای نمایش در پنل مدیریت */
    fun all(context: Context): Map<String, Int> = read(context)

    /** صفر کردن همه — دکمهٔ «پاک کردن آمار لمس» در پنل */
    fun reset(context: Context) {
        runCatching { file(context).delete() }
    }
}
