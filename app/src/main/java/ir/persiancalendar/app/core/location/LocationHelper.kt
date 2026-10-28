package ir.persiancalendar.app.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * دریافت یک‌باره موقعیت کاربر — نسخه ۱.۱۱.۴ (رفع مشکل «GPS موقعیت را پیدا نمی‌کند»)
 *
 * مشکلات روش قبلی که باگ می‌ساخت:
 *  ۱) requestSingleUpdate فقط از GPS_PROVIDER استفاده می‌کرد — در فضای بسته هرگز فیکس نمی‌شد.
 *  ۲) SecurityException با مجوز coarse-only بی‌صدا خورده می‌شد و هیچ اتفاقی نمی‌افتاد.
 *  ۳) روشن بودن سرویس موقعیت گوشی چک نمی‌شد — اگر خاموش بود، بی‌صدا شکست می‌خورد.
 *  ۴) تایم‌اوت وجود نداشت — کاربر تا ابد در «منتظر سیگنال» می‌ماند.
 *
 * روش جدید:
 *  ۱) چک مجوز (fine یا coarse کافی است) و چک روشن بودن سرویس موقعیت با پیام روشن.
 *  ۲) ابتدا آخرین موقعیت شناخته‌شده همه providerها (GPS/شبکه/منفعل) — اگر کمتر از ۱۰ دقیقه کهنه باشد کافی است.
 *  ۳) در غیر این صورت درخواست به‌روزرسانی زنده از «همه» providerهای فعال به‌طور هم‌زمان؛
 *     اولین فیکس برمی‌گردد و بقیه لغو می‌شوند.
 *  ۴) تایم‌اوت ۲۰ ثانیه: در پایان، آخرین موقعیت (حتی کهنه) اگر موجود باشد برگردانده می‌شود؛
 *     وگرنه پیام خطای قابل‌فهم.
 *
 * همه callbacks روی نخ اصلی اجرا می‌شوند. خروجی fetchOnce تابعِ لغو است
 * (برای بستن صفحه / توقف گوش‌دادن).
 */
object LocationHelper {

    /** نتیجه نسخه suspend */
    sealed interface Result {
        data class Success(val lat: Double, val lng: Double) : Result
        data class Error(val message: String) : Result
    }

    /**
     * نسخه callback محور. خروجی: تابع لغو (idempotent).
     */
    fun fetchOnce(
        context: Context,
        timeoutMs: Long = 20_000L,
        onResult: (Double, Double) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val main = Handler(Looper.getMainLooper())

        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            main.post { onError("اجازه دسترسی به موقعیت داده نشده است") }
            return {}
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val permitted = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }
        if (permitted.isEmpty()) {
            main.post { onError("هیچ سرویس موقعیتی در دسترس نیست") }
            return {}
        }

        val enabled = permitted.filter { p ->
            runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
        }
        if (enabled.none { it != LocationManager.PASSIVE_PROVIDER }) {
            main.post {
                onError("سرویس موقعیت (GPS) گوشی خاموش است — از تنظیمات اندروید روشنش کنید")
            }
            return {}
        }

        var finished = false
        val listeners = mutableListOf<LocationListener>()
        var timeout: Runnable? = null

        fun cleanup() {
            for (l in listeners) runCatching { lm.removeUpdates(l) }
            listeners.clear()
            timeout?.let { main.removeCallbacks(it) }
        }

        fun finish(loc: Location) {
            if (finished) return
            finished = true
            cleanup()
            main.post { onResult(loc.latitude, loc.longitude) }
        }

        fun fail(msg: String) {
            if (finished) return
            finished = true
            cleanup()
            main.post { onError(msg) }
        }

        // ---------- ۱) آخرین موقعیت شناخته‌شده — تازه‌ترین provider ----------
        var best: Location? = null
        for (p in permitted) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        val fresh = best?.takeIf { System.currentTimeMillis() - it.time < 10 * 60_000L }
        if (fresh != null) {
            main.post { onResult(fresh.latitude, fresh.longitude) }
            return {}
        }

        // ---------- ۲) درخواست زنده از همه providerهای فعال ----------
        for (p in enabled) {
            try {
                val listener = LocationListener { loc -> finish(loc) }
                listeners.add(listener)
                lm.requestLocationUpdates(p, 1_000L, 0f, listener, main.looper)
            } catch (_: SecurityException) {
                // مجوز این provider فقط coarse است — بی‌خیال همین provider
            } catch (_: Exception) {
                // provider در این لحظه در دسترس نیست
            }
        }
        if (listeners.isEmpty()) {
            // حتی موقعیت کهنه بهتر از هیچ است
            best?.let { main.post { onResult(it.latitude, it.longitude) } }
                ?: main.post { onError("شروع سرویس موقعیت ممکن نشد") }
            return {}
        }

        // ---------- ۳) تایم‌اوت ----------
        timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            cleanup()
            // آخرین چاره: موقعیت کهنه اگر وجود داشته باشد
            best?.let { main.post { onResult(it.latitude, it.longitude) } }
                ?: main.post {
                    onError("سیگنال موقعیت دریافت نشد — به فضای باز یا کنار پنجره بروید و دوباره تلاش کنید")
                }
        }
        main.postDelayed(timeout, timeoutMs)

        return {
            finished = true
            cleanup()
        }
    }

    /**
     * نسخه suspend — برای استفاده داخل coroutineها.
     */
    suspend fun fetchSuspend(
        context: Context,
        timeoutMs: Long = 20_000L
    ): Result = suspendCancellableCoroutine { cont ->
        val cancel = fetchOnce(
            context,
            timeoutMs = timeoutMs,
            onResult = { la, lo ->
                if (cont.isActive) cont.resumeWith(
                    kotlin.Result.success(Result.Success(la, lo))
                )
            },
            onError = { msg ->
                if (cont.isActive) cont.resumeWith(
                    kotlin.Result.success(Result.Error(msg))
                )
            }
        )
        cont.invokeOnCancellation { cancel() }
    }
}
