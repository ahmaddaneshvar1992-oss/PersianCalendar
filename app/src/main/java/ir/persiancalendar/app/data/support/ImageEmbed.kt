package ir.persiancalendar.app.data.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * فشرده‌سازی خودکار تصویر بنرها و اطلاعیه‌ها — نسخهٔ ۱.۱۱.۲۵
 *
 * گزارش کاربر: «تبلیغات که میزارم عکس‌ها واسه کاربرها نمایش داده نمیشه».
 * علت (بازرسی): عکس گالری که مدیر انتخاب می‌کرد همان‌طور و بدون فشرده‌سازی
 * ذخیره می‌شد (عکس‌های دوربین ۲ تا ۸ مگابایت!) و در لحظهٔ انتشار، چون از
 * سقف ۲۰۰ کیلوبایتِ جاسازی در خوراک بزرگ‌تر بود، بی‌صدا حذف می‌شد و بنر
 * بدون عکس منتشر می‌شد؛ عکس اطلاعیهٔ گالری هم اصلاً منتشر نمی‌شد.
 *
 * اکنون:
 *  - هنگام انتخاب عکس از گالری، تصویر خودکار به حداکثر ۱۶۰۰ پیکسل کوچک و
 *    با کیفیت ۸۸ به JPEG تبدیل و ذخیره می‌شود (حجم چند صد کیلوبایت)
 *  - هنگام انتشار، اگر فایل باز هم بزرگ‌تر از سقف خوراک بود، در حافظه
 *    کوچک‌تر و با کیفیت کاهش‌یافته فشرده می‌شود (فایل اصلی مدیر دست
 *    نمی‌خورد) تا همیشه عکس داخل خوراک جاسازی شود — نه حذف بی‌صدا
 */
object ImageEmbed {

    /** بزرگ‌ترین ضلع هنگام ذخیرهٔ محلی عکس انتخابی مدیر */
    private const val SAVE_MAX_DIM = 1600

    /** بزرگ‌ترین ضلع هنگام جاسازی در خوراک */
    private const val FEED_MAX_DIM = 1080

    /** سقف حجم عکس جاسازی‌شده در خوراک (بایت) — همان سقف قبلی ۲۰۰ کیلوبایت */
    const val FEED_MAX_BYTES = 200_000

    /**
     * ذخیرهٔ فشردهٔ تصویر انتخاب‌شده از گالری — خروجی: مسیر فایل ذخیره‌شده
     * یا رشتهٔ خالی در صورت خطا. جایگزین ذخیرهٔ خام بدون فشرده‌سازی.
     */
    fun saveSlimFromUri(context: Context, uri: Uri, dir: File, prefix: String): String =
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, SAVE_MAX_DIM)
            }
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return ""
            dir.mkdirs()
            val out = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            out.absolutePath
        }.getOrDefault("")

    /**
     * data URI جاسازی‌شده در خوراک از فایل تصویر.
     * اگر فایل از سقف کوچک‌تر باشد همان‌طور جاسازی می‌شود؛ وگرنه در حافظه
     * کوچک و فشرده می‌شود (فایل اصلی دستگاه مدیر تغییر نمی‌کند).
     * در هر حالت خطا رشتهٔ خالی برمی‌گردد.
     */
    fun dataUriFromFile(path: String, maxBytes: Int = FEED_MAX_BYTES): String = runCatching {
        val f = File(path)
        if (!f.exists() || f.length() <= 0L) return ""
        if (f.length() <= maxBytes)
            return "data:image/jpeg;base64," +
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""
        val bmp = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, FEED_MAX_DIM)
            }
        ) ?: return ""

        var quality = 85
        var bytes: ByteArray
        while (true) {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            bytes = bos.toByteArray()
            if (bytes.size <= maxBytes || quality <= 40) break
            quality -= 15
        }
        bmp.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrDefault("")

    /** inSampleSize مناسب برای رسیدن به حداکثر ضلع خواسته‌شده */
    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var s = 1
        var max = maxOf(w, h)
        while (max / 2 >= maxDim) {
            s *= 2
            max /= 2
        }
        return s
    }
}
