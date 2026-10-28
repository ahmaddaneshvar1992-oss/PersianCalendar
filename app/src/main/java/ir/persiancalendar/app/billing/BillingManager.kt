package ir.persiancalendar.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import ir.persiancalendar.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * خرید درون‌برنامه‌ای «عضویت ویژه» با Google Play Billing.
 *
 * - شناسه محصول: premium_unlock (خرید یک‌بار برای همیشه — در Play Console بسازید)
 * - روی گوشی‌های بدون گوگل‌پلی (کافه‌بازار) اتصال ناموفق است و پیام مناسب نمایش می‌شود؛
 *   برای بازار از کد فعال‌سازی یا کتابخانه Poolakey استفاده کنید (docs/MEMBERSHIP-FA.md).
 *
 * نسخه ۱.۱۱.۵ — هاردنینگ کامل (رفع خروج برنامه هنگام باز کردن صفحه حساب):
 *  - هیچ متدی از این کلاس استثنا به بیرون نمی‌فرستد — همه مسیرها با runCatching
 *    محافظت شده‌اند؛ روی گوشی‌های بدون سرویس گوگل هم هیچ کرشی رخ نمی‌دهد.
 *  - کال‌بک‌های بایندر (onBillingSetupFinished، queryProductDetailsAsync،
 *    queryPurchasesAsync، PurchasesUpdatedListener) هم محافظت شده‌اند؛
 *    استثنا در این نخ‌ها کل برنامه را می‌بست.
 *
 * پس از خرید موفق، AppContainer.accountRepo().grantDevicePremium("billing") فراخوانی
 * می‌شود و مزایای ویژه فعال می‌شوند.
 */
object BillingManager {

    private const val TAG = "Billing"

    /** شناسه محصول عضویت ویژه در Google Play Console */
    const val PREMIUM_PRODUCT_ID = "premium_unlock"

    private var billingClient: BillingClient? = null
    private var premiumDetails: ProductDetails? = null

    /** قیمت نمایشی محصول بعد از پرس‌وجو (مثل «۲۹۹٬۰۰۰ ﷼» یا «$2.99») */
    @Volatile
    var premiumPrice: String? = null
        private set

    private var onPremiumGranted: (() -> Unit)? = null

    fun isReady(): Boolean =
        billingClient?.isReady == true

    fun hasPrice(): Boolean = premiumPrice != null

    /**
     * اتصال به فروشگاه. در پایان connect، خریدهای قبلی هم بازگردانی می‌شوند.
     * هیچ‌وقت استثنا نمی‌فرستد (نسخه ۱.۱۱.۵ — روی گوشی‌های بدون گوگل‌پلی هم امن است).
     */
    fun start(context: Context) {
        if (billingClient != null) return
        runCatching {
            val client = BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
                .build()
            billingClient = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    // این کال‌بک روی نخ بایندر گوگل اجرا می‌شود — هر استثنا برنامه را می‌بندد
                    runCatching {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Billing connected")
                            queryProductDetails()
                            restorePurchases()
                        } else {
                            Log.w(TAG, "Billing connect failed: ${result.responseCode} ${result.debugMessage}")
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing disconnected")
                }
            })
        }.onFailure { e ->
            billingClient = null
            Log.w(TAG, "Billing start failed — فروشگاه در دسترس نیست", e)
        }
    }

    /** ثبت کالبک برای لحظه اعطای موفق عضویت (برای نمایش پیام در UI) */
    fun setOnPremiumGranted(callback: (() -> Unit)?) {
        onPremiumGranted = callback
    }

    private fun queryProductDetails() {
        runCatching {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PREMIUM_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
                runCatching {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        premiumDetails = detailsList.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
                        premiumPrice = premiumDetails?.oneTimePurchaseOfferDetails?.formattedPrice
                        Log.d(TAG, "Product price: $premiumPrice")
                    }
                }
            }
        }
    }

    /** بازگردانی خرید قبلی (مثلاً بعد از نصب مجدد اپ) */
    fun restorePurchases() {
        runCatching {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient?.queryPurchasesAsync(params) { result, purchases ->
                runCatching {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases.forEach { handlePurchase(it, grantUiCallback = false) }
                    }
                }
            }
        }
    }

    /** شروع جریان خرید؛ خروجی: آیا جریان راه‌اندازی شد */
    fun launchPremiumPurchase(activity: Activity): Boolean {
        if (!isReady()) return false
        return runCatching {
            val details = premiumDetails ?: return@runCatching false
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            val result = billingClient?.launchBillingFlow(activity, flowParams)
            result?.responseCode == BillingClient.BillingResponseCode.OK
        }.getOrDefault(false)
    }

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        // نخ بایندر گوگل — استثنا اینجا برنامه را می‌بندد (نسخه ۱.۱۱.۵)
        runCatching {
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Purchase flow ended: ${result.responseCode}")
                return@PurchasesUpdatedListener
            }
            purchases?.forEach { handlePurchase(it, grantUiCallback = true) }
        }
    }

    private fun handlePurchase(purchase: Purchase, grantUiCallback: Boolean) {
        runCatching {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
            if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return

            val appContext = AppContainer.appContext
            if (appContext == null) {
                Log.w(TAG, "AppContext not set — cannot grant premium")
                return
            }
            val repo = AppContainer.accountRepo(appContext)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    // تأیید خرید (acknowledge) اگر نشده باشد
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(ackParams) { ackResult ->
                            runCatching { Log.d(TAG, "Acknowledge: ${ackResult.responseCode}") }
                        }
                    }
                    if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                        repo.grantDevicePremium("billing")
                        if (grantUiCallback) onPremiumGranted?.invoke()
                    }
                }
            }
        }
    }
}
