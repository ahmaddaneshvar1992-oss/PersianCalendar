# -*- coding: utf-8 -*-
"""
بازسازی AdminScreen — نسخهٔ ۱.۱۱.۵۱ (درخواست کاربر: «پنل مدیریت تبلیغات رو
پیشرفته‌تر کن و امکانات بیشتری بده»)

ساختار تازه:
  - پنل تب‌بندی‌شده: تبلیغات / اطلاعیه‌ها / پشتیبانی / کاربران / امنیت
  - فرم بنر پیشرفته: بج، اولویت، مدت نمایش، حداقل نسخه + پیش‌نمایش زندهٔ بنر
    با همان رندرکنندهٔ صفحهٔ اصلی (AdBannerItem)
  - ویرایش و تکثیر بنر + آمار لمس (AdClickStats) + خلاصهٔ پیش از انتشار
  - فیلتر و کپی راه تماس در صندوق پشتیبانی

بلوک «ورود/رمز» عیناً از فایل فعلی برداشته می‌شود (AUTH) — بدون تغییر.
"""
from pathlib import Path

SRC = Path("app/src/admin/java/ir/persiancalendar/app/ui/settings/AdminScreen.kt")
src = SRC.read_text(encoding="utf-8")

# ---------- استخراج عینِ بلوک ورود (از if (!authed) تا else) ----------
# نکته: substring ساده داخل خطوط با تورفتگی بیشتر هم مچ می‌شود؛ پس خط‌محور می‌جستیم
_lines = src.split("\n")
_i0 = next(i for i, l in enumerate(_lines) if l.strip() == "if (!authed) {")
_i1 = next(i for i in range(_i0 + 1, len(_lines)) if _lines[i] == "        } else {")
AUTH = "\n".join(_lines[_i0:_i1]) + "\n"
assert "تنظیم رمز پنل مدیریت" in AUTH and "ورود به پنل" in AUTH

PART1 = '''package ir.persiancalendar.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.db.UserEntity
import ir.persiancalendar.app.data.prefs.AdminSecrets
import ir.persiancalendar.app.data.support.AdClickStats
import ir.persiancalendar.app.data.support.AdsFeed
import ir.persiancalendar.app.data.support.Announcement
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.data.support.ImageEmbed
import ir.persiancalendar.app.data.support.MainAd
import ir.persiancalendar.app.data.support.MainAdBox
import ir.persiancalendar.app.data.support.SupportBox
import ir.persiancalendar.app.data.support.SupportMessage
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.AdBannerItem
import ir.persiancalendar.app.ui.components.AdColorNames
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * پنل مدیریت — نسخه ۱.۱۱.۵۱ (درخواست کاربر: «پنل مدیریت تبلیغات رو پیشرفته‌تر کن»)
 *
 * پنل تب‌بندی‌شده شد تا در یک نگاه مدیریت شود:
 *  - تب «تبلیغات»: آمار (با لمس‌های بنر) + فهرست بنرها (ویرایش/تکثیر/زمان‌بندی/
 *    آمار لمس) + فرم پیشرفتهٔ بنر (بج، اولویت، مدت نمایش، حداقل نسخه) +
 *    پیش‌نمایش زنده با همان رندرکنندهٔ صفحهٔ اصلی + انتشار فید با خلاصهٔ تأیید
 *  - تب «اطلاعیه‌ها»: فرم و فهرست اطلاعیه‌ها + مدیریت توکن + انتشار برای کاربران
 *  - تب «پشتیبانی»: صندوق پیام‌ها با فیلتر نوع + کپی راه تماس
 *  - تب «کاربران»: فهرست ثبت‌نام‌شده‌ها
 *  - تب «امنیت»: تغییر رمز، خروج، خروج و حذف توکن
 *
 * تاریخچه: بنرهای صفحه اصلی (۱.۱۱.۶) — اطلاعیه‌ها (۱.۶) — صندوق پشتیبانی (۱.۵) —
 * انتشار همگانی (۱.۱۱.۱۶) — فید مدیریت‌شدهٔ ضدحذف (۱.۱۱.۳۵) — امنیت پنل
 * (۱.۱۱.۲۹/۴۵: رمز PBKDF2، قفل موقت، نشست ۵ دقیقه‌ای، توکن AES-256-GCM).
 * SDK تبلیغاتی بیرونی همچنان حذف است؛ تبلیغ فقط بنرهای خود مدیر است.
 */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // نسخهٔ ۱.۱۱.۳۰: رمز پنل و توکن از AdminSecrets فلاور admin می‌آیند
    // (در نسخهٔ عمومی اصلاً این صفحه وجود ندارد)
    val secrets = remember { AdminSecrets(context) }
    // نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۲): وضعیت رمز برای چرخهٔ مهاجرت legacy→هش
    val pinState by secrets.pinState.collectAsState(
        initial = AdminSecrets.PinUiState(needsSetup = false, isHashed = false, legacyExists = false)
    )
    // نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۱): UI فقط ماسک توکن را می‌بیند («••••x7qf»)
    val savedTokenMasked by secrets.githubTokenMasked.collectAsState(initial = "")
    // نسخهٔ ۱.۱۱.۵۱: کپی راه تماس کاربر در صندوق پشتیبانی
    val clipboardManager = LocalClipboardManager.current

    var authed by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    // صندوق پشتیبانی (نسخهٔ ۱.۵) + فیلتر نوع پیام (۱.۱۱.۵۱)
    var supMsgs by remember { mutableStateOf<List<SupportMessage>>(emptyList()) }
    var supFilterIdx by remember { mutableStateOf(0) } // ۰ همه / ۱ تبلیغ / ۲ مشکل / ۳ پیشنهاد
    // اطلاعیه‌ها — زنگ کنار منوی سه‌نقطه (نسخهٔ ۱.۶)
    var anns by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var annType by remember { mutableStateOf("news") }
    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    var annLink by remember { mutableStateOf("") }
    var annPickedImage by remember { mutableStateOf("") }
    var annImageUrl by remember { mutableStateOf("") }
    var ghToken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { secrets.migrateLegacyToken() }
        }
    }
    var pubStatus by remember { mutableStateOf("") }
    var pubBusy by remember { mutableStateOf(false) }
    var adPubStatus by remember { mutableStateOf("") }
    var adPubBusy by remember { mutableStateOf(false) }
    // بنرهای تبلیغات صفحه اصلی — نسخه ۱.۱۱.۶
    var ads by remember { mutableStateOf<List<MainAd>>(emptyList()) }
    var adTitle by remember { mutableStateOf("") }
    var adBody by remember { mutableStateOf("") }
    var adCta by remember { mutableStateOf("") }
    var adLink by remember { mutableStateOf("") }
    var adColorIndex by remember { mutableStateOf(0) }
    var adPickedImage by remember { mutableStateOf("") }
    var adImageUrl by remember { mutableStateOf("") }

    // ---------- نسخهٔ ۱.۱۱.۵۱ (درخواست کاربر: پنل پیشرفته‌تر) ----------
    // تب فعال پنل — ۰ تبلیغات / ۱ اطلاعیه‌ها / ۲ پشتیبانی / ۳ کاربران / ۴ امنیت
    var panelTab by remember { mutableStateOf(0) }
    // ویرایش بنر — شناسهٔ بنری که در فرم باز است (null = افزودن بنر تازه)
    var editingAdId by remember { mutableStateOf<Long?>(null) }
    // فیلدهای پیشرفتهٔ فرم بنر
    var adBadge by remember { mutableStateOf("") }
    var adPriorityIdx by remember { mutableStateOf(0) } // ۰ عادی / ۱ مهم / ۲ فوری
    var adDurationIdx by remember { mutableStateOf(0) } // ۰ بی‌پایان / ۳/۷/۱۴/۳۰ روز
    var adMinVerIdx by remember { mutableStateOf(0) }   // ۰ همهٔ نسخه‌ها / ۱: نسخهٔ ۶۲+
    // آمار لمس بنرها (دستگاه مدیر) — پس از ورود و پس از پاک‌کردن
    var adClicks by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // دیالوگ خلاصهٔ پیش از انتشار فید تبلیغات
    var confirmAdsPublish by remember { mutableStateOf(false) }

    val TAB_LABELS = listOf("تبلیغات", "اطلاعیه‌ها", "پشتیبانی", "کاربران", "امنیت")
    val AD_PRIORITY_LABELS = listOf("عادی", "مهم", "فوری")
    val AD_DURATION_LABELS = listOf("بدون محدودیت زمانی", "۳ روز", "۷ روز", "۱۴ روز", "۳۰ روز")
    val AD_DURATION_DAYS = listOf(0, 3, 7, 14, 30)
    val AD_MINVER_LABELS = listOf("همهٔ نسخه‌ها", "نسخهٔ ۱.۱۱.۵۰ و بالاتر")
    val SUP_FILTER_LABELS = listOf("همهٔ پیام‌ها", "درخواست تبلیغ", "گزارش مشکل", "پیشنهاد")
    val SUP_FILTER_KEYS = listOf(null, "ad", "bug", "idea")

    /** پایان بازهٔ نمایش بر اساس گزینهٔ مدت — ۰ = بدون محدودیت */
    fun currentEndAt(): Long =
        if (AD_DURATION_DAYS[adDurationIdx] == 0) 0L
        else System.currentTimeMillis() + AD_DURATION_DAYS[adDurationIdx] * 86_400_000L

    /** وضعیت زمان‌بندی بنر برای نمایش در فهرست */
    fun adScheduleText(a: MainAd): String = when {
        a.endAt <= 0L -> ""
        a.endAt <= System.currentTimeMillis() -> " · بازهٔ نمایش تمام شده"
        else -> " · نمایش تا " +
            (((a.endAt - System.currentTimeMillis()) + 86_399_999L) / 86_400_000L).toInt() +
            " روز دیگر"
    }

    fun resetAdForm() {
        editingAdId = null
        adTitle = ""; adBody = ""; adCta = ""; adLink = ""
        adPickedImage = ""; adImageUrl = ""; adColorIndex = 0
        adBadge = ""; adPriorityIdx = 0; adDurationIdx = 0; adMinVerIdx = 0
    }

    /** بارگذاری بنر در فرم برای ویرایش — id حفظ می‌شود */
    fun fillAdForm(a: MainAd) {
        editingAdId = a.id
        adTitle = a.title; adBody = a.body; adCta = a.cta; adLink = a.link
        adPickedImage = if (a.image.startsWith("http")) "" else a.image
        adImageUrl = if (a.image.startsWith("http")) a.image else ""
        adColorIndex = a.colorIndex.coerceIn(0, 7)
        adBadge = a.badge
        adPriorityIdx = a.priority.coerceIn(0, 2)
        adDurationIdx = if (a.endAt <= 0L) 0 else {
            val daysLeft = ((a.endAt - System.currentTimeMillis()) / 86_400_000L).toInt()
            when {
                daysLeft <= 3 -> 1
                daysLeft <= 7 -> 2
                daysLeft <= 14 -> 3
                else -> 4
            }
        }
        adMinVerIdx = if (a.minVersionCode >= 62) 1 else 0
    }

    // بارگذاری فهرست کاربران، پیام‌های پشتیبانی، بنرها و آمار لمس پس از ورود
    LaunchedEffect(authed) {
        if (authed) {
            users = AppContainer.userDao(context).getAll()
            supMsgs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                SupportBox.load(context)
            }
            anns = kotlinx.coroutines.withContext(Dispatchers.IO) {
                AnnouncementBox.load(context)
            }
            ads = kotlinx.coroutines.withContext(Dispatchers.IO) {
                MainAdBox.load(context)
            }
            adClicks = kotlinx.coroutines.withContext(Dispatchers.IO) {
                AdClickStats.all(context)
            }
        }
    }

    var pinCheckBusy by remember { mutableStateOf(false) }
    var lockMsLeft by remember { mutableStateOf(0L) }
    var lastActivityMs by remember { mutableStateOf(0L) }
    // ---------- نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۲): قفل موقت و نشست ----------
    // بازشماری شمارش‌معکوس قفل روی صفحهٔ ورود (هر ثانیه)
    LaunchedEffect(authed, pinCheckBusy) {
        while (!authed) {
            lockMsLeft = runCatching { secrets.remainingLockMs() }.getOrDefault(0L)
            kotlinx.coroutines.delay(1000)
        }
    }
    // نشست باز: هر ۱۵ ثانیه بررسی بی‌تعاملی — بعد از ۵ دقیقه قفل دوباره
    LaunchedEffect(authed) {
        while (authed) {
            kotlinx.coroutines.delay(15_000)
            if (System.currentTimeMillis() - lastActivityMs > AdminSecrets.SESSION_IDLE_MS) {
                authed = false
                pin = ""
            }
        }
    }
    // رفتن اپ به پس‌زمینه = قفل فوری پنل (بازرسی امنیتی — کار ۲)
    val hostActivity = context as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(hostActivity) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && authed) {
                authed = false
                pin = ""
            }
        }
        hostActivity?.lifecycle?.addObserver(obs)
        onDispose { hostActivity?.lifecycle?.removeObserver(obs) }
    }

    // انتخاب تصویر بنر از گالری — کپی به filesDir/ads (نسخهٔ ۱.۱۱.۶)
    val adImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = MainAdBox.saveImageFromUri(context, uri)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (saved.isNotBlank()) {
                        adPickedImage = saved
                        Toast.makeText(context, "تصویر بنر انتخاب شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خواندن تصویر ممکن نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // انتخاب تصویر اطلاعیه از گالری — کپی به filesDir/announcements (نسخهٔ ۱.۱۱.۷)
    val annImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = AnnouncementBox.saveImageFromUri(context, uri)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (saved.isNotBlank()) {
                        annPickedImage = saved
                        Toast.makeText(context, "تصویر اطلاعیه انتخاب شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خواندن تصویر ممکن نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ---------- پیش‌نمایش زندهٔ بنر (۱.۱۱.۵۱) ----------
    // تصویر پیش‌نمایش: https یا data URI فایل گالری — با همان ImageLoader صفحهٔ اصلی
    val previewImage by produceState("", adPickedImage, adImageUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = adPickedImage.ifBlank { adImageUrl.trim() }
                when {
                    raw.isBlank() -> ""
                    raw.startsWith("https://") -> raw
                    else -> ImageEmbed.dataUriFromFile(raw)
                }
            }.getOrDefault("")
        }
    }
    val previewItem = AdsFeed.FeedItem(
        id = "preview",
        type = "banner",
        enabled = true,
        title = adTitle.trim().ifBlank { "پیش‌نمایش بنر" },
        badge = adBadge.trim(),
        iconUrl = "",
        imageUrl = previewImage,
        // پیش‌نمایش: بدون مقصد — لمس هیچ کاری نمی‌کند و در آمار هم ثبت نمی‌شود
        targetType = "",
        target = "",
        order = 0,
        startAt = 0L,
        endAt = 0L,
        minVersionCode = 0,
        description = adBody.trim()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding() // نسخه ۱.۱۱.۲: سرصفحه زیر نوار وضعیت سیستم نمی‌رود
            .padding(12.dp)
            // نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۲): هر لمس، نشست ۵ دقیقه‌ای را تازه می‌کند
            .pointerInput(Unit) {
                detectTapGestures {
                    lastActivityMs = System.currentTimeMillis()
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
            Spacer(Modifier.weight(1f))
            Text("پنل مدیریت", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))

#AUTH_BLOCK#
        } else {
            // ---------- تب‌های پنل — نسخهٔ ۱.۱۱.۵۱ ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TAB_LABELS.forEachIndexed { i, label ->
                    FilterChip(
                        selected = panelTab == i,
                        onClick = { panelTab = i },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when (panelTab) {
                // =====================================================
                // تب ۰ — تبلیغات
                // =====================================================
                0 -> {
'''

PART2_MARKER = "# === PART2 ==="
PART3_MARKER = "# === PART3 ==="
OUT = Path("scripts/_adminscreen_part1.kt.tmp")
OUT.write_text(PART1.replace("#AUTH_BLOCK#", AUTH), encoding="utf-8")
print("part1 written", len(PART1))

# ============================================================
# بخش ۲ — تب ۰: آمار + فهرست بنرها + فرم پیشرفته + پیش‌نمایش + فید
# ============================================================
PART2 = r'''                    // ---------- آمار کلی — با لمس‌های بنر (۱.۱۱.۵۱) ----------
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("آمار", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "کاربران ثبت‌نام‌شده در این دستگاه: ${users.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "پیام‌های صندوق پشتیبانی: ${supMsgs.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "اطلاعیه‌های فعال (زنگ): ${anns.count { it.on }} از ${anns.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "بنرهای تبلیغ صفحه اصلی: ${ads.count { it.on }} از ${ads.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // نسخهٔ ۱.۱۱.۵۱: شمارندهٔ لمس بنرها (دستگاه مدیر)
                            Text(
                                "لمس‌های ثبت‌شدهٔ بنرها روی این دستگاه: ${adClicks.values.sum()} " +
                                    "— (لمسِ هر بنرِ دارای مقصد؛ آمار دستگاه کاربران به مخزن ایستا برنمی‌گردد)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    AdClickStats.reset(context)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        adClicks = emptyMap()
                                        Toast.makeText(context, "آمار لمس پاک شد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Text("پاک کردن آمار لمس") }
                            Text(
                                "آمار کل نصب‌ها را در گیت‌هاب ← Insights ← Traffic ببینید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ---------- بنرهای تبلیغات صفحه اصلی (۱.۱۱.۶ — ارتقا ۱.۱۱.۵۱) ----------
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("تبلیغات صفحه اصلی", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "بنرهای تبلیغ خودتان را بسازید و برای همهٔ کاربران منتشر کنید. " +
                                    "بنرهای فعال به‌ترتیب از بالا به پایین نمایش داده می‌شوند؛ تصویر اجباری است و لینک اختیاری. " +
                                    "از این نسخه: بج برچسبی، اولویت، مدت نمایش (زمان‌بندی)، حداقل نسخه، ویرایش و تکثیر بنر و آمار لمس.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            if (ads.isEmpty()) {
                                Text(
                                    "هنوز بنری نساخته‌اید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                ads.forEachIndexed { idx, ad ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(ad.title, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    (if (ad.on) "فعال" else "خاموش") +
                                                        (if (ad.image.isNotBlank()) " · تصویردار" else "") +
                                                        (if (ad.link.isNotBlank()) " · لینک‌دار" else "") +
                                                        adScheduleText(ad) +
                                                        (if (ad.badge.isNotBlank()) " · بج: ${ad.badge}" else "") +
                                                        (if (ad.priority > 0) " · ${AD_PRIORITY_LABELS[ad.priority.coerceIn(0, 2)]}" else ""),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                // نسخهٔ ۱.۱۱.۵۱: آمار لمس هر بنر
                                                val clicks = adClicks["banner-${ad.id}"] ?: 0
                                                if (clicks > 0) {
                                                    Text(
                                                        "لمس‌شده: $clicks بار روی این دستگاه",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            TextButton(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        MainAdBox.move(context, ad.id, -1)
                                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                            ads = MainAdBox.load(context)
                                                        }
                                                    }
                                                },
                                                enabled = idx > 0
                                            ) { Text("▲") }
                                            TextButton(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        MainAdBox.move(context, ad.id, 1)
                                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                            ads = MainAdBox.load(context)
                                                        }
                                                    }
                                                },
                                                enabled = idx < ads.size - 1
                                            ) { Text("▼") }
                                            Switch(
                                                checked = ad.on,
                                                onCheckedChange = { on ->
                                                    scope.launch(Dispatchers.IO) {
                                                        MainAdBox.setEnabled(context, ad.id, on)
                                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                            ads = MainAdBox.load(context)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        if (ad.body.isNotBlank()) {
                                            Text(ad.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row {
                                            // نسخهٔ ۱.۱۱.۵۱: ویرایش بنر — فرم با محتوای بنر پر می‌شود
                                            TextButton(onClick = { fillAdForm(ad) }) { Text("ویرایش") }
                                            Spacer(Modifier.width(6.dp))
                                            // نسخهٔ ۱.۱۱.۵۱: تکثیر — کپیِ غیرفعال زیر بنر اصلی
                                            TextButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    MainAdBox.duplicate(context, ad.id)
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        ads = MainAdBox.load(context)
                                                        Toast.makeText(
                                                            context,
                                                            "کپیِ خاموش ساخته شد — ویرایشش کنید و روشنش کنید",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }) { Text("تکثیر") }
                                            Spacer(Modifier.width(6.dp))
                                            TextButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    MainAdBox.delete(context, ad.id)
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        ads = MainAdBox.load(context)
                                                        if (editingAdId == ad.id) resetAdForm()
                                                        adPubStatus = "بنر حذف شد ✓ — برای اینکه حذف به دستگاه کاربران هم برسد، دکمهٔ «انتشار فید تبلیغات» را بزنید (حذف محلی به‌تنهایی روی گوشی کاربران اثر ندارد)"
                                                        Toast.makeText(
                                                            context,
                                                            "حذف شد — برای رسیدن حذف به کاربران، «انتشار فید تبلیغات» را بزنید",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }) { Text("حذف") }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // ---------- فرم افزودن/ویرایش بنر — ارتقا ۱.۱۱.۵۱ ----------
                            Text(
                                if (editingAdId != null) "ویرایش بنر (شناسهٔ $editingAdId)" else "افزودن بنر جدید",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (editingAdId != null) {
                                Text(
                                    "در حال ویرایش — جای بنر در فهرست و شناسه‌اش حفظ می‌شود. «انصراف» فرم را به حالت افزودن برمی‌گرداند.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { resetAdForm() }) { Text("انصراف از ویرایش") }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adTitle,
                                onValueChange = { adTitle = it },
                                label = { Text("عنوان بنر (مثلاً: تخفیف ویژه فروشگاه)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adBody,
                                onValueChange = { adBody = it },
                                label = { Text("توضیح کوتاه (اختیاری)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adCta,
                                onValueChange = { adCta = it },
                                label = { Text("متن دکمه (اختیاری — پیش‌فرض: مشاهده)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adLink,
                                onValueChange = { adLink = it },
                                label = { Text("لینک (اختیاری — https یا کلید داخلی)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            // نسخهٔ ۱.۱۱.۵۱: بج/برچسب کوتاه روی بنر («جدید»، «٪۲۰ تخفیف»)
                            OutlinedTextField(
                                value = adBadge,
                                onValueChange = { adBadge = it.take(12) },
                                singleLine = true,
                                label = { Text("بج برچسبی (اختیاری — حداکثر ۱۲ نویسه، مثلاً: جدید)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "رنگ بنر",
                                items = AdColorNames,
                                selectedIndex = adColorIndex,
                                onSelected = { idx -> adColorIndex = idx }
                            )
                            Spacer(Modifier.height(6.dp))
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "اولویت نمایش (۱.۱۱.۵۱)",
                                items = AD_PRIORITY_LABELS,
                                selectedIndex = adPriorityIdx,
                                onSelected = { idx -> adPriorityIdx = idx }
                            )
                            Spacer(Modifier.height(6.dp))
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "مدت نمایش (۱.۱۱.۵۱ — پس از آن بنر خودکار حذف می‌شود)",
                                items = AD_DURATION_LABELS,
                                selectedIndex = adDurationIdx,
                                onSelected = { idx -> adDurationIdx = idx }
                            )
                            Spacer(Modifier.height(6.dp))
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "حداقل نسخهٔ برنامه (۱.۱۱.۵۱)",
                                items = AD_MINVER_LABELS,
                                selectedIndex = adMinVerIdx,
                                onSelected = { idx -> adMinVerIdx = idx }
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { adImagePicker.launch("image/*") }) {
                                    Text("انتخاب تصویر از گالری")
                                }
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                if (adPickedImage.isNotBlank()) {
                                    Text(
                                        "تصویر انتخاب شد ✓",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { adPickedImage = "" }) { Text("حذف") }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adImageUrl,
                                onValueChange = { adImageUrl = it },
                                label = { Text("یا آدرس اینترنتی تصویر (URL — اختیاری)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            // ---------- پیش‌نمایش زندهٔ بنر (۱.۱۱.۵۱) — همان رندرکنندهٔ صفحهٔ اصلی ----------
                            if (previewImage.isNotBlank()) {
                                Text("پیش‌نمایش زنده (دقیقاً همان چیزی که کاربر می‌بیند):", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                AdBannerItem(item = previewItem, onOpenInternal = {})
                                Text(
                                    "پیش‌نمایش قابل لمس نیست و در آمار ثبت نمی‌شود؛ نسبت تصویر در صفحهٔ اصلی حفظ می‌شود (عریض: تمام‌عرض، عمودی: کارت تا ۲۸۰dp).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "برای پیش‌نمایش زنده، تصویر بنر را انتخاب کنید (گالری یا URL).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (adTitle.trim().length < 2) {
                                        Toast.makeText(context, "عنوان بنر را وارد کنید", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val finalImage = adPickedImage.ifBlank { adImageUrl.trim() }
                                    val editingNow = editingAdId
                                    scope.launch(Dispatchers.IO) {
                                        if (editingNow != null) {
                                            // ---------- ویرایش بنر موجود (۱.۱۱.۵۱) ----------
                                            val old = MainAdBox.load(context).firstOrNull { it.id == editingNow }
                                            if (old != null) {
                                                MainAdBox.update(
                                                    context,
                                                    old.copy(
                                                        title = adTitle.trim(),
                                                        body = adBody.trim(),
                                                        cta = adCta.trim().ifBlank { "مشاهده" },
                                                        link = ir.persiancalendar.app.ui.components.LinkOpener.normalize(adLink),
                                                        image = finalImage,
                                                        colorIndex = adColorIndex.coerceIn(0, 7),
                                                        badge = adBadge.trim(),
                                                        priority = adPriorityIdx.coerceIn(0, 2),
                                                        startAt = old.startAt,
                                                        endAt = currentEndAt(),
                                                        minVersionCode = if (adMinVerIdx == 1) 62 else 0
                                                    )
                                                )
                                            }
                                        } else {
                                            MainAdBox.add(
                                                context,
                                                title = adTitle,
                                                body = adBody,
                                                cta = adCta,
                                                link = ir.persiancalendar.app.ui.components.LinkOpener.normalize(adLink),
                                                image = finalImage,
                                                colorIndex = adColorIndex,
                                                badge = adBadge,
                                                priority = adPriorityIdx,
                                                startAt = 0L,
                                                endAt = currentEndAt(),
                                                minVersionCode = if (adMinVerIdx == 1) 62 else 0
                                            )
                                        }
                                        val fresh = MainAdBox.load(context)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            ads = fresh
                                            val wasEditing = editingNow != null
                                            resetAdForm()
                                            Toast.makeText(
                                                context,
                                                if (wasEditing) "تغییرات بنر ذخیره شد — برای رسیدن به کاربران، «انتشار فید تبلیغات» را بزنید"
                                                else "بنر ساخته شد — برای نمایش برای کاربران، «انتشار فید تبلیغات» را بزنید",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (editingAdId != null) "✓ ذخیرهٔ تغییرات بنر" else "+ افزودن بنر جدید")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ---------- انتشار فید تبلیغات مدیریت‌شده — ۱.۱۱.۳۵/۳۶/۳۸ + خلاصهٔ ۱.۱۱.۵۱ ----------
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("انتشار فید تبلیغات برای کاربران", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "بنرهایی که بالا می‌سازید اول فقط روی همین دستگاه‌اند. با «انتشار فید تبلیغات»، فهرستِ فید روی مخزن گیت‌هاب منتشر می‌شود و همهٔ کاربران آن را می‌گیرند. " +
                                    "تبلیغات صفحه اصلی «بدون ضربدر» است و کاربر راهی برای پنهان‌کردن ندارد؛ فقط همین انتشار (یا فایل feed/main_ads.json با tools/ads_feed.py) آیتم‌ها را اضافه/حذف می‌کند. " +
                                    "هر انتشار شمارهٔ sequence را یکی زیاد می‌کند و اپ‌ها فهرست را کامل جایگزین می‌کنند؛ عکس گالری خودکار فشرده و داخل فید جاسازی می‌شود. " +
                                    "اگر همهٔ بنرها را خالی یا خاموش کنید، فید خالی منتشر می‌شود که یعنی «حذف همهٔ تبلیغ‌ها برای همه». " +
                                    "بعد از هر انتشار، امضای CI حدود ۱ تا ۳ دقیقه طول می‌کشد؛ اپ تا ~۸ دقیقه خودش چند بار بررسی می‌کند و تغییر بدون باز کردن دوبارهٔ برنامه اعمال می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "نحوهٔ نمایش بنر: تصویر با «نسبت واقعی خودش» و بدون برش — عریض (۳:۱) تمام‌عرض، عمودی/لوگو در کارتی تا ۲۸۰dp کامل دیده می‌شود. لینک داخلی فقط با کلیدهای لیست‌سفید: calendar، prayer، qibla، events، reminders، announcements، support، account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            // نسخهٔ ۱.۱۱.۵۱: وضعیت توکن از تب تبلیغات هم دیده می‌شود
                            Text(
                                if (savedTokenMasked.isNotBlank())
                                    "توکن ذخیره‌شده: ${savedTokenMasked} — مدیریت توکن در تب «اطلاعیه‌ها»"
                                else
                                    "توکنی ذخیره نشده — مدیریت توکن در تب «اطلاعیه‌ها»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { panelTab = 1 }) { Text("مدیریت توکن (تب اطلاعیه‌ها)") }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val cr = ir.persiancalendar.app.data.support.AdsFeed.check(context)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            adPubStatus = when {
                                                !cr.ok ->
                                                    "دریافت فید ناموفق: ${cr.error.take(160)} — اتصال اینترنت را بررسی کنید؛ منابع به‌ترتیب آزموده می‌شوند: سرور شخصی، raw، jsDelivr، api.github"
                                                cr.count > 0 ->
                                                    "فید فعلی کاربران: ${cr.count} آیتم — ${cr.error} — دریافت موفق از ${cr.source}"
                                                else ->
                                                    "فید تبلیغات کاربران در حال حاضر خالی است (۰ آیتم) — ${cr.error} — دریافت موفق از ${cr.source}؛ برای نمایش تبلیغ تازه، آن را بسازید و «انتشار فید تبلیغات» را بزنید"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("بررسی فید تبلیغات کاربران") }
                            Spacer(Modifier.height(6.dp))
                            // نسخهٔ ۱.۱۱.۵۱: انتشار با دیالوگ خلاصهٔ پیش از انتشار
                            Button(
                                onClick = { confirmAdsPublish = true },
                                enabled = !adPubBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (adPubBusy) "در حال انتشار فید تبلیغات…" else "انتشار فید تبلیغات (sequence +۱)")
                            }
                            if (adPubStatus.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    adPubStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
'''
print("part2 built", len(PART2))

# ============================================================
# بخش ۳ — تب‌های ۱..۴ + دیالوگ خلاصهٔ انتشار + مونتاژ و نوشتن فایل
# ============================================================
PART3 = r'''                // =====================================================
                // تب ۱ — اطلاعیه‌ها (خبر و تبلیغ زنگ) + مدیریت توکن + انتشار
                // =====================================================
                1 -> {
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("اطلاعیه‌ها (خبر و تبلیغ)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "خبر یا تبلیغی که اینجا ثبت کنید، با زنگ و نقطه قرمز به کاربران نشان داده می‌شود. لینک و تصویر اختیاری‌اند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            if (anns.isEmpty()) {
                                Text(
                                    "هنوز اطلاعیه‌ای نساخته‌اید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                anns.asReversed().forEach { a ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    a.typeLabel + " — " + a.title,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    (if (a.on) "نمایش به کاربران" else "خاموش") +
                                                        (if (a.image.isNotBlank()) " · تصویردار" else ""),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = a.on,
                                                onCheckedChange = { on ->
                                                    scope.launch(Dispatchers.IO) {
                                                        AnnouncementBox.setEnabled(context, a.id, on)
                                                        val fresh = AnnouncementBox.load(context)
                                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                            anns = fresh
                                                        }
                                                    }
                                                }
                                            )
                                            TextButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    AnnouncementBox.delete(context, a.id)
                                                    val fresh = AnnouncementBox.load(context)
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        anns = fresh
                                                        pubStatus = "اطلاعیه حذف شد ✓ — برای اینکه حذف به دستگاه کاربران هم برسد، دکمهٔ «انتشار برای کاربران» را بزنید"
                                                        Toast.makeText(
                                                            context,
                                                            "حذف شد — برای رسیدن حذف به کاربران، دوباره «انتشار برای کاربران» را بزنید",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }) { Text("حذف") }
                                        }
                                        Text(a.body, style = MaterialTheme.typography.bodySmall)
                                        if (a.link.isNotBlank()) {
                                            Text(
                                                "لینک: " + a.link,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // فرم افزودن اطلاعیه
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "نوع اطلاعیه",
                                items = listOf("خبر", "تبلیغ"),
                                selectedIndex = if (annType == "ad") 1 else 0,
                                onSelected = { idx -> annType = if (idx == 1) "ad" else "news" }
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = annTitle,
                                onValueChange = { annTitle = it },
                                label = { Text("عنوان اطلاعیه") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = annBody,
                                onValueChange = { annBody = it },
                                label = { Text("متن اطلاعیه") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = annLink,
                                onValueChange = { annLink = it },
                                label = { Text("لینک (اختیاری)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { annImagePicker.launch("image/*") }) {
                                    Text("انتخاب عکس اطلاعیه از گالری")
                                }
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                if (annPickedImage.isNotBlank()) {
                                    Text(
                                        "عکس انتخاب شد ✓",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { annPickedImage = "" }) { Text("حذف") }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = annImageUrl,
                                onValueChange = { annImageUrl = it },
                                label = { Text("یا آدرس اینترنتی عکس (URL — اختیاری)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (annTitle.trim().length < 3) {
                                        Toast.makeText(context, "عنوان اطلاعیه را وارد کنید", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (annBody.trim().length < 5) {
                                        Toast.makeText(context, "متن اطلاعیه را وارد کنید", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        AnnouncementBox.add(
                                            context,
                                            type = annType,
                                            title = annTitle,
                                            body = annBody,
                                            link = ir.persiancalendar.app.ui.components.LinkOpener.normalize(annLink),
                                            image = annPickedImage.ifBlank { annImageUrl.trim() }
                                        )
                                        val fresh = AnnouncementBox.load(context)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            anns = fresh
                                            annTitle = ""; annBody = ""; annLink = ""
                                            annPickedImage = ""; annImageUrl = ""
                                            Toast.makeText(
                                                context,
                                                "اطلاعیه ثبت شد — زنگ بالای برنامه قرمز می‌شود",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("+ افزودن اطلاعیه") }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ---------- انتشار برای کاربران + مدیریت توکن ----------
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("انتشار برای کاربران (اعلان سیستمی)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "با «انتشار برای کاربران»، فهرست اطلاعیه‌ها روی مخزن گیت‌هاب منتشر می‌شود و همهٔ کاربران هم به زنگ برنامه هم اعلان سیستمی می‌گیرند. " +
                                    "عکس گالری با فشرده‌سازی خودکار داخل خوراک جاسازی می‌شود. مهم: بعد از حذف یا خاموش‌کردن اطلاعیه، دوباره همین دکمهٔ انتشار را بزنید تا از دستگاه کاربران هم برداشته شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (savedTokenMasked.isNotBlank())
                                    "توکن ذخیره‌شده روی دستگاه: ${savedTokenMasked} (رمزنگاری AES-256-GCM کلیدِ غیرقابل export کی‌استور)"
                                else "توکنی روی دستگاه ذخیره نشده است",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = ghToken,
                                onValueChange = { ghToken = it.take(120) },
                                singleLine = true,
                                label = { Text("توکن تازه (برای جایگزینی — خالی بگذارید اگر نمی‌خواهید تغییر کند)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "راهنما: در گیت‌هاب ← Settings ← Developer settings ← Personal access tokens ← " +
                                    "Fine-grained token با دسترسی فقط به مخزن خوراک PersianCalendar-feed بسازید (نه مخزن اصلی برنامه!) با Contents: Read and write و Actions: Read and write — انتشار خوراک و امضای آن فقط به مخزن خوراک دسترسی می‌خواهد و توکن با دامنهٔ کوچک‌تر حتی اگر لو برود کاری در مخزن اصلی برنامه نمی‌تواند بکند. " +
                                    "توکن پیش از ذخیره با AES-256-GCM و کلیدِ غیرقابل export خودِ گوشی رمز می‌شود؛ " +
                                    "اگر گوشی گم شد یا مشکوک به لو رفتن هستید، اول توکن را در گیت‌هاب Revoke کنید و بعد «پاک کردن توکن» را بزنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            if (ghToken.isBlank()) {
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "فیلد توکن تازه خالی است", Toast.LENGTH_SHORT).show()
                                                }
                                                return@launch
                                            }
                                            runCatching { secrets.saveGithubToken(ghToken) }
                                                .onSuccess {
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        ghToken = ""
                                                        Toast.makeText(context, "توکن رمزنگاری‌شده ذخیره شد — بعد از خروج هم می‌ماند", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .onFailure {
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "خطا در ذخیرهٔ توکن", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ذخیره توکن") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            runCatching { secrets.wipeGithubToken() }
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                ghToken = ""
                                                Toast.makeText(context, "توکن از این دستگاه پاک شد", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("پاک کردن توکن") }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val cr = ir.persiancalendar.app.data.support.AnnouncementFeed.check(context)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            pubStatus = when {
                                                !cr.ok ->
                                                    "دریافت خوراک ناموفق: ${cr.error.take(160)} — اتصال اینترنت را بررسی کنید؛ منابع به‌ترتیب آزموده می‌شوند: سرور شخصی، raw، jsDelivr، api.github"
                                                cr.fromCache && cr.count > 0 ->
                                                    "الان به منبع زنده دسترسی نبود — آخرین نسخهٔ کش‌شدهٔ همین دستگاه: ${cr.count} اطلاعیه. جزئیات تلاش‌ها: ${cr.error.take(120)}"
                                                cr.fromCache ->
                                                    "الان به منبع زنده دسترسی نبود و کش همین دستگاه هم خالی است (۰ اطلاعیه). با اینترنت وصل دوباره «بررسی خوراک کاربران» را بزنید. جزئیات: ${cr.error.take(120)}"
                                                cr.count > 0 ->
                                                    "خوراک فعلی کاربران: ${cr.count} اطلاعیه — دریافت موفق از ${cr.source}"
                                                else ->
                                                    "خوراک کاربران در حال حاضر خالی است (۰ اطلاعیه) — دریافت موفق از ${cr.source}؛ برای رسیدن اطلاعیهٔ تازه، آن را ثبت و دکمهٔ «انتشار برای کاربران» را بزنید"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("بررسی خوراک کاربران") }
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    pubBusy = true
                                    scope.launch(Dispatchers.IO) {
                                        val token = secrets.githubTokenBlocking()
                                        val list = AnnouncementBox.active(context)
                                        val msg = if (token.isBlank()) {
                                            "اول توکن گیت‌هاب را در فیلد بالا وارد و «ذخیره توکن» را بزنید"
                                        } else {
                                            ir.persiancalendar.app.data.support.FeedPublisher.publishAnnouncements(
                                                context, token, list
                                            )
                                        }
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            pubBusy = false
                                            pubStatus = msg
                                            Toast.makeText(context, msg.take(60), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !pubBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (pubBusy) "در حال انتشار…" else "انتشار برای کاربران")
                            }
                            if (pubStatus.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    pubStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }

                // =====================================================
                // تب ۲ — پشتیبانی: فیلتر نوع پیام + کپی راه تماس (۱.۱۱.۵۱)
                // =====================================================
                2 -> {
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("صندوق پشتیبانی", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "پیام‌هایی که کاربران از بخش پشتیبانی (درخواست تبلیغ / گزارش مشکل / پیشنهاد) ارسال کرده‌اند. با فیلتر بالا نوع پیام‌ها را جدا کنید و «کپی راه تماس» برای پاسخ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            SimpleDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = "فیلتر پیام‌ها",
                                items = SUP_FILTER_LABELS,
                                selectedIndex = supFilterIdx,
                                onSelected = { idx -> supFilterIdx = idx }
                            )
                            Spacer(Modifier.height(8.dp))
                            val filtered = SUP_FILTER_KEYS[supFilterIdx]?.let { key ->
                                supMsgs.asReversed().filter { it.type == key }
                            } ?: supMsgs.asReversed()
                            if (filtered.isEmpty()) {
                                Text(
                                    if (supMsgs.isEmpty()) "صندوق پشتیبانی خالی است"
                                    else "پیامی با این فیلتر نیست",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                filtered.forEach { m ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    m.typeLabel + " — " + m.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    m.contact + " · " + m.at,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            // نسخهٔ ۱.۱۱.۵۱: کپی راه تماس برای پاسخ سریع
                                            TextButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(m.contact))
                                                Toast.makeText(context, "راه تماس کپی شد", Toast.LENGTH_SHORT).show()
                                            }) { Text("کپی") }
                                            TextButton(onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    SupportBox.delete(context, m.id)
                                                    val fresh = SupportBox.load(context)
                                                    kotlinx.coroutines.withContext(Dispatchers.Main) { supMsgs = fresh }
                                                }
                                            }) { Text("حذف") }
                                        }
                                        Text(m.text, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }

                // =====================================================
                // تب ۳ — کاربران
                // =====================================================
                3 -> {
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("کاربران ثبت‌نام‌شده", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            if (users.isEmpty()) {
                                Text(
                                    "هنوز کاربری ثبت‌نام نکرده است",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                users.forEach { u ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(u.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                u.username,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            if (u.premium) "عضو ویژه" else "رایگان",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (u.premium) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }

                // =====================================================
                // تب ۴ — امنیت
                // =====================================================
                4 -> {
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("امنیت", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { newPin = it.take(24) },
                                singleLine = true,
                                label = { Text("رمز جدید (حروف + اعداد، حداقل ۶ کاراکتر)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "رمز باید هم حرف داشته باشد و هم عدد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val hasLetter = newPin.any { it.isLetter() }
                                    val hasDigit = newPin.any { it.isDigit() }
                                    when {
                                        newPin.length < 6 -> Toast.makeText(
                                            context, "رمز باید حداقل ۶ کاراکتر باشد", Toast.LENGTH_SHORT
                                        ).show()
                                        !hasLetter || !hasDigit -> Toast.makeText(
                                            context, "رمز باید هم حرف و هم عدد داشته باشد", Toast.LENGTH_SHORT
                                        ).show()
                                        else -> scope.launch {
                                            secrets.setAdminPin(newPin)
                                            newPin = ""
                                            Toast.makeText(context, "رمز پنل تغییر کرد", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("تغییر رمز پنل") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    authed = false
                                    onBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("خروج از پنل") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { secrets.wipeGithubToken() }
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            ghToken = ""
                                            authed = false
                                            Toast.makeText(
                                                context,
                                                "توکن از دستگاه حذف شد — از پنل خارج شدید",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onBack()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("خروج و حذف توکن") }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---------- دیالوگ خلاصهٔ پیش از انتشار فید تبلیغات — نسخهٔ ۱.۱۱.۵۱ ----------
        if (confirmAdsPublish) {
            val drafts = MainAdBox.active(context)
            val noImage = drafts.count { it.image.isBlank() }
            val nowMs = System.currentTimeMillis()
            val expired = drafts.count { it.endAt in 1..nowMs }
            val scheduled = drafts.count { it.endAt > nowMs }
            AlertDialog(
                onDismissRequest = { confirmAdsPublish = false },
                title = { Text("خلاصهٔ انتشار فید تبلیغات") },
                text = {
                    Column {
                        Text("بنرهای فعال آمادهٔ انتشار: ${drafts.size}")
                        if (noImage > 0) {
                            Text(
                                "• $noImage بنر تصویر ندارد و در انتشار رد می‌شود",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (expired > 0) {
                            Text("• $expired بنر بازهٔ نمایشش تمام شده (منتشر می‌شود ولی نمایش داده نمی‌شود)")
                        }
                        if (scheduled > 0) {
                            Text("• $scheduled بنر زمان‌دار است — پایان بازه در فید ثبت می‌شود و بعد از آن خودکار پنهان می‌شود")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "هر انتشار sequence را یکی زیاد می‌کند و فهرستِ همهٔ کاربران را کامل جایگزین می‌کند؛ امضای CI حدود ۱ تا ۳ دقیقه طول می‌کشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        confirmAdsPublish = false
                        adPubBusy = true
                        scope.launch(Dispatchers.IO) {
                            val token = secrets.githubTokenBlocking()
                            val msg = if (token.isBlank()) {
                                "اول توکن گیت‌هاب را ذخیره کنید (تب اطلاعیه‌ها ← مدیریت توکن)"
                            } else {
                                ir.persiancalendar.app.data.support.FeedPublisher.publishAdsFeed(
                                    context, token, MainAdBox.active(context)
                                )
                            }
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                adPubBusy = false
                                adPubStatus = msg
                                Toast.makeText(context, msg.take(60), Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("انتشار") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmAdsPublish = false }) { Text("انصراف") }
                }
            )
        }
    }
}
'''

# ---------- مونتاژ نهایی ----------
# جایگذاری بلوک ورود (عینِ فایل فعلی)
PART1 = PART1.replace("#AUTH_BLOCK#", AUTH)
assert "#AUTH_BLOCK#" not in PART1
# ایمپورت width (برای Spacer(Modifier.width(...)) در ردیف ویرایش/تکثیر)
PART1 = PART1.replace(
    "import androidx.compose.foundation.layout.height\n",
    "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.width\n"
)
assert "layout.width" in PART1

full = PART1 + PART2 + PART3
assert "#AUTH_BLOCK#" not in full, "AUTH جایگزین نشده"
assert "# === PART" not in full

# بررسی توازن آکولاد/پرانتز (بدون رشته و کامنت)
import re
t = re.sub(r'"(?:\\.|[^"\\])*"', 'x', full)
t = re.sub(r'//[^\n]*', '', t)
t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
assert t.count("{") == t.count("}"), (t.count("{"), t.count("}"))
assert t.count("(") == t.count(")"), (t.count("("), t.count(")"))

for must in ["FilterChip", "AlertDialog", "AdBannerItem", "AdClickStats", "produceState",
             "fillAdForm", "resetAdForm", "currentEndAt", "adScheduleText",
             "تکثیر", "ویرایش بنر", "پیش‌نمایش زنده", "کپی راه تماس" if False else "کپی",
             "خلاصهٔ انتشار فید تبلیغات", "مدت نمایش", "بج برچسبی", "حداقل نسخه"]:
    assert must in full, must

# اقلام اجباری نسخهٔ قبل که باید حفظ شده باشند
for keep in ["تنظیم رمز پنل مدیریت", "ورود توسعه‌دهنده", "مهاجرت امنیتی", "خروج و حذف توکن",
             "بررسی فید تبلیغات کاربران", "بررسی خوراک کاربران", "بررسی فید تبلیغات کاربران",
             "انتخاب عکس اطلاعیه از گالری", "Fine-grained token", "پاک کردن آمار لمس",
             "بررسی فید تبلیغات کاربران"]:
    assert keep in full, "missing keep: " + keep

SRC.write_text(full, encoding="utf-8")
print("AdminScreen.kt written:", len(full), "chars,", full.count("\n"), "lines")
Path("scripts/_adminscreen_part1.kt.tmp").unlink(missing_ok=True)
