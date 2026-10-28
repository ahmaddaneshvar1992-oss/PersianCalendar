package ir.persiancalendar.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * مدیریت تبلیغات AdMob.
 *
 * ⚠️ مهم: شناسه‌های زیر «شناسه تست» گوگل هستند و فقط برای توسعه!
 * پیش از انتشار، شناسه‌های واقعی خود را از https://apps.admob.com جایگزین کنید
 * (آموزش کامل در docs/ADMOB-SETUP-FA.md).
 * همچنین شناسه اپلیکیشن در AndroidManifest.xml باید تغییر کند.
 */
object AdManager {

    // شناسه تست بنر گوگل
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    // شناسه تست اینترستیشال گوگل
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // برای سازگاری با کدهای قدیمی
    const val BANNER_UNIT_ID = TEST_BANNER_UNIT_ID
    const val INTERSTITIAL_UNIT_ID = TEST_INTERSTITIAL_UNIT_ID

    // ---------- نسخه ۱.۲: پیکربندی زنده از پنل مدیریت ----------
    /** کلید فعال بودن تبلیغات (پنل مدیریت) */
    @Volatile var adsEnabled: Boolean = true
    /** کلید فعال بودن اینترستیشال */
    @Volatile var interstitialEnabled: Boolean = true
    /** حالت آزمایشی: شناسه تست گوگل به‌جای شناسه واقعی */
    @Volatile var testMode: Boolean = true
    /** شناسه واقعی بنر ذخیره‌شده در پنل */
    @Volatile var realBannerUnitId: String = ""
    /** شناسه واقعی اینترستیشال ذخیره‌شده در پنل */
    @Volatile var realInterstitialUnitId: String = ""

    /** شناسه بنر فعال بر اساس حالت آزمایشی */
    val activeBannerUnitId: String
        get() = if (testMode || realBannerUnitId.isBlank()) TEST_BANNER_UNIT_ID else realBannerUnitId

    /** شناسه اینترستیشال فعال بر اساس حالت آزمایشی */
    val activeInterstitialUnitId: String
        get() = if (testMode || realInterstitialUnitId.isBlank()) TEST_INTERSTITIAL_UNIT_ID else realInterstitialUnitId

    private var interstitial: InterstitialAd? = null
    private var isLoading = false

    /**
     * همگام‌سازی پیکربندی با تنظیمات (از پنل مدیریت / DataStore).
     * در MainScreen و پیش از هر نمایش فراخوانی شود.
     */
    fun syncConfig(
        adsEnabled: Boolean,
        interstitialEnabled: Boolean,
        testMode: Boolean,
        bannerUnitId: String,
        interstitialUnitId: String
    ) {
        this.adsEnabled = adsEnabled
        this.interstitialEnabled = interstitialEnabled
        this.testMode = testMode
        this.realBannerUnitId = bannerUnitId
        this.realInterstitialUnitId = interstitialUnitId
    }

    fun loadInterstitial(context: Context) {
        if (!adsEnabled || !interstitialEnabled) return
        if (interstitial != null || isLoading) return
        isLoading = true
        InterstitialAd.load(
            context,
            activeInterstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    isLoading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitial = null
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    isLoading = false
                }
            }
        )
    }

    /** نمایش اینترستیشال اگر آماده باشد؛ خروجی: نمایش داده شد یا خیر */
    fun showInterstitialIfReady(activity: Activity): Boolean {
        val ad = interstitial ?: return false
        ad.show(activity)
        return true
    }

    fun isInterstitialReady(): Boolean = interstitial != null
}
