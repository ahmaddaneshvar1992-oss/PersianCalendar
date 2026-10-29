package ir.persiancalendar.app.core.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * دریافت یک‌باره موقعیت کاربر — نسخه ۱.۱۱.۵ (رفع باگ «سرویس در دسترس نیست»)
 *
 * باگ نسخه ۱.۱۱.۴: نام provider (مثل "gps") به‌اشتباه به checkSelfPermission داده
 * می‌شد که همیشه DENIED برمی‌گرداند و کاربر همیشه پیام «هیچ سرویس موقعیتی در
 * دسترس نیست» می‌دید. اکنون فهرست providerها ثابت است و فقط مجوز کلی + روشن
 * بودن providerها چک می‌شود.
 *
 * مشکلات روش قدیمی‌تر (پیش از ۱.۱۱.۴) که باگ می‌ساخت:
 *  ۱) requestSingleUpdate فقط از GPS_PROVIDER استفاده می‌کرد — در فضای بسته هرگز فیکس نمی‌شد.
 *  ۲) SecurityException با مجوز coarse-only بی‌صدا خورده می‌شد و هیچ اتفاقی نمی‌افتاد.
 *  ۳) روشن بودن سرویس موقعیت گوشی چک نمی‌شد — اگر خاموش بود، بی‌صدا شکست می‌خورد.
 *  ۴) تایم‌اوت وجود نداشت — کاربر تا ابد در «منتظر سیگنال» می‌ماند.
 *
 * روش فعلی:
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
        // نسخه ۱.۱۱.۵ — رفع باگ «هیچ سرویس موقعیتی در دسترس نیست»:
        // در نسخه ۱.۱۱.۴ نام provider (مثل "gps") به‌اشتباه به checkSelfPermission داده می‌شد
        // که همیشه PERMISSION_DENIED برمی‌گرداند و پیام خطا همیشه نمایش داده می‌شد؛
        // مجوز کلی (fine/coarse) بالاتر بررسی شد — نام provider مجوز نیست.
        val permitted = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

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

    // ====================================================================
    // نسخه ۱.۱۱.۱۷ — روشن‌کردن خودکار سرویس موقعیت (درخواست کاربر مطابق عکس)
    //
    // قبلاً اگر سرویس موقعیت گوشی خاموش بود فقط پیام «از تنظیمات اندروید
    // روشنش کنید» می‌آمد و کاربر باید مسیر تنظیمات را خودش پیدا می‌کرد.
    // اکنون با لمس دکمهٔ GPS، دیالوگ سیستمی یک‌لمسی «فعال‌سازی موقعیت»
    // (پلی‌سرویس گوگل) باز می‌شود و با یک تأیید سرویس روشن می‌شود؛ روی
    // گوشی‌های بدون پلی‌سرویس مستقیم صفحهٔ تنظیمات موقعیت باز می‌شود و
    // به‌محض بازگشت کاربر، جست‌وجوی موقعیت خودکار ادامه می‌یابد.
    // ====================================================================

    /** آیا سرویس موقعیت گوشی (GPS یا شبکه) روشن است؟ */
    fun isServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
        val net = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
            .getOrDefault(false)
        return gps || net
    }

    /**
     * تلاش برای بازکردن دیالوگ سیستمی یک‌لمسی «فعال‌سازی سرویس موقعیت».
     *
     * با پلی‌سرویس گوگل، درخواست تنظیمات موقعیت (HIGH_ACCURACY) چک می‌شود؛
     * اگر سرویس خاموش باشد نتیجه ResolvableApiException است که همان دیالوگ
     * معروف «فعال کردن موقعیت؟» با دکمهٔ تأیید را در اختیار می‌گذارد.
     * برای افزودن وابستگی گوگل به برنامه از بازتاب (Reflection) استفاده شده،
     * پس روی گوشی‌های بدون پلی‌سرویس هم بدون خطا کار می‌کند.
     *
     * نتیجه از طریق callback برمی‌گردد:
     *  — sender != null : تماس‌دهنده آن را با StartIntentSenderForResult اجرا
     *    کند؛ نتیجهٔ RESULT_OK یعنی سرویس روشن شده و می‌توان موقعیت را گرفت.
     *  — sender == null  : پلی‌سرویس موجود نیست (یا خطا)؛ تماس‌دهنده صفحهٔ
     *    تنظیمات موقعیت (ACTION_LOCATION_SOURCE_SETTINGS) را باز کند.
     */
    fun tryEnableService(activity: Activity, onResolution: (IntentSender?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        try {
            val loader = activity.classLoader
            val servicesCls = Class.forName(
                "com.google.android.gms.location.LocationServices", false, loader
            )
            val clientCls = Class.forName(
                "com.google.android.gms.location.SettingsClient", false, loader
            )
            val taskCls = Class.forName("com.google.android.gms.tasks.Task", false, loader)
            val onSuccessCls = Class.forName(
                "com.google.android.gms.tasks.OnSuccessListener", false, loader
            )
            val onFailureCls = Class.forName(
                "com.google.android.gms.tasks.OnFailureListener", false, loader
            )
            val locReqCls = Class.forName(
                "com.google.android.gms.location.LocationRequest", false, loader
            )
            val reqBuilderCls = Class.forName(
                "com.google.android.gms.location.LocationRequest\$Builder", false, loader
            )
            val lsReqCls = Class.forName(
                "com.google.android.gms.location.LocationSettingsRequest", false, loader
            )
            val lsBuilderCls = Class.forName(
                "com.google.android.gms.location.LocationSettingsRequest\$Builder", false, loader
            )

            val client = servicesCls
                .getMethod("getSettingsClient", Context::class.java)
                .invoke(null, activity)

            // LocationRequest — روش جدید (پلی‌سرویس ۲۱+) با Builder، روش قدیمی با create()
            val locRequest: Any = try {
                val b = reqBuilderCls
                    .getConstructor(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .newInstance(100 /* Priority.PRIORITY_HIGH_ACCURACY */, 0L)
                reqBuilderCls.getMethod("build").invoke(b)
            } catch (_: Throwable) {
                locReqCls.getMethod("create").invoke(null)
            }

            // LocationSettingsRequest.Builder().addLocationRequest(lr).setAlwaysShow(true).build()
            val lsBuilder = lsBuilderCls.getDeclaredConstructor()
                .also { it.isAccessible = true }
                .newInstance()
            lsBuilderCls.getMethod("addLocationRequest", locReqCls).invoke(lsBuilder, locRequest)
            lsBuilderCls.getMethod("setAlwaysShow", Boolean::class.javaPrimitiveType)
                .invoke(lsBuilder, true)
            val settingsRequest = lsBuilderCls.getMethod("build").invoke(lsBuilder)

            val task = clientCls
                .getMethod("checkLocationSettings", lsReqCls)
                .invoke(client, settingsRequest)

            // اگر سرویس از قبل روشن باشد (نباید رخ دهد — تماس‌دهنده اول چک می‌کند)
            val successListener = java.lang.reflect.Proxy.newProxyInstance(
                loader, arrayOf(onSuccessCls)
            ) { _, _, _ -> }

            // اگر نیاز به فعال‌سازی باشد: استثنای ResolvableApiException حامل
            // PendingIntent دیالوگ سیستمی است.
            val failureListener = java.lang.reflect.Proxy.newProxyInstance(
                loader, arrayOf(onFailureCls)
            ) { _, _, args ->
                val sender = runCatching {
                    val ex = args.getOrNull(0) as? Throwable
                    val pi = ex?.javaClass?.getMethod("getResolution")?.invoke(ex)
                        ?: ex?.javaClass?.getMethod("getStatus")?.invoke(ex)?.let { st ->
                            st.javaClass.getMethod("getResolution").invoke(st)
                        }
                    pi as? android.app.PendingIntent
                }.getOrNull()?.intentSender
                main.post { onResolution(sender) }
            }

            taskCls.getMethod("addOnSuccessListener", onSuccessCls).invoke(task, successListener)
            taskCls.getMethod("addOnFailureListener", onFailureCls).invoke(task, failureListener)
        } catch (_: Throwable) {
            // پلی‌سرویس موجود نیست یا هر خطای دیگر — مسیر جایگزین: صفحهٔ تنظیمات
            main.post { onResolution(null) }
        }
    }
}
