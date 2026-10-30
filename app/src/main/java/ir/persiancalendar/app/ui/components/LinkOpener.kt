package ir.persiancalendar.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * باز کردن امن لینک‌های اطلاعیه و بنر — نسخه ۱.۱۱.۲۰
 * (گزارش کاربر: «واسه بنر و اطلاعیه لینک‌هایی که می‌ذارم اجرا نمی‌کنه و به
 * سایت مورد نظر هدایت نمیشه»)
 *
 * علت خرابی قبلی: لینک‌هایی که مدیر بدون «https://» ثبت می‌کرد (مثل
 * aparat.com/xxx یا www.example.com) با ACTION_VIEW هیچ برنامه‌ای پیدا
 * نمی‌کرد و خطای ActivityNotFoundException بی‌صدا داخل runCatching رد
 * می‌شد — انگار هیچ اتفاقی نیفتاده بود.
 *
 * راه‌حل:
 *  - اگر لینک scheme نداشته باشد خودکار «https://» به سرش اضافه می‌شود؛
 *  - اگر مرورگر پیش‌فرض پیدا نشود، انتخابگر برنامه‌ها باز می‌شود؛
 *  - اگر هیچ راهی نشد، false برمی‌گردد تا فراخوان پیام خطا نشان دهد.
 */
object LinkOpener {

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /**
     * افزودن خودکار https:// به لینک‌های بدون scheme — برای ذخیره و باز کردن.
     * نسخهٔ ۱.۱۱.۲۹ (بازرسی امنیتی: «لینک‌ها رو فقط با https:// بپذیر»):
     * فقط http و https مجازند؛ هر scheme دیگری (intent، javascript، file و…)
     * کنار گذاشته می‌شود و به‌جای آن https:// به ابتدای متن می‌نشیند.
     */
    fun normalize(url: String): String {
        val t = url.trim()
        if (t.isEmpty()) return t
        val m = SCHEME.find(t) ?: return "https://$t"
        val scheme = m.value.substringBefore("://").lowercase()
        return if (scheme == "http" || scheme == "https") t
        else "https://" + t.substring(m.value.length)
    }

    /**
     * باز کردن لینک در مرورگر — خروجی: موفقیت باز شدن
     * (فراخوان در صورت false پیام «مرورگر پیدا نشد» نشان می‌دهد)
     */
    fun open(context: Context, url: String): Boolean {
        val link = normalize(url)
        if (link.isBlank()) return false
        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return false
        if (uri.scheme.isNullOrBlank()) return false

        val base = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // تلاش ۱: مرورگر/برنامهٔ پیش‌فرض لینک
        val direct = runCatching {
            context.startActivity(base)
            true
        }.getOrDefault(false)
        if (direct) return true

        // تلاش ۲: انتخابگر برنامه‌ها (وقتی برنامهٔ پیش‌فرضی ثبت نشده)
        val viaChooser = runCatching {
            context.startActivity(
                Intent.createChooser(base, "باز کردن لینک")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
        return viaChooser
    }

    /** باز کردن + پیام خطا در صورت شکست — برای لمس بنر و دکمهٔ «مشاهده» */
    fun openOrToast(context: Context, url: String) {
        if (!open(context, url)) {
            runCatching {
                Toast.makeText(
                    context,
                    "برنامه‌ای برای باز کردن این لینک پیدا نشد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
