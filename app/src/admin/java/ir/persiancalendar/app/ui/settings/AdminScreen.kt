package ir.persiancalendar.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.db.UserEntity
import ir.persiancalendar.app.data.prefs.AdminSecrets
import ir.persiancalendar.app.data.support.Announcement
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.data.support.MainAd
import ir.persiancalendar.app.data.support.MainAdBox
import ir.persiancalendar.app.data.support.SupportBox
import ir.persiancalendar.app.data.support.SupportMessage
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.AdColorNames
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * پنل مدیریت — نسخه ۱.۱۱.۶
 *
 * صفحه‌ای مخصوص توسعه‌دهنده (با رمز) برای:
 *  - تبلیغات صفحه اصلی: بنرهای اختصاصی مدیر، هر چند تا که بخواهد — نسخه ۱.۱۱.۶
 *    (تصویر از گالری یا URL، رنگ، دکمه و لینک اختیاری + ترتیب نمایش + روشن/خاموش)
 *  - اطلاعیه‌ها: خبر و تبلیغ زنگ کنار منوی سه‌نقطه — نسخه ۱.۶
 *  - صندوق پشتیبانی: پیام‌های کاربران (تبلیغ/مشکل/پیشنهاد) — نسخه ۱.۵
 *  - مشاهده آمار و فهرست کاربران ثبت‌نام‌شده
 *  - تغییر رمز پنل — رمز می‌تواند حروف و اعداد با هم داشته باشد — نسخه ۱.۳
 *
 * یادداشت: SDK تبلیغاتی بیرونی (AdMob) و اینترستیشال همچنان کاملاً حذف است؛
 * بنرهای صفحه اصلی فقط تبلیغ‌های خود مدیر هستند (نسخه ۱.۱۱.۶).
 */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // نسخهٔ ۱.۱۱.۳۰: رمز پنل و توکن از AdminSecrets فلاور admin می‌آیند
    // (در نسخهٔ عمومی اصلاً این صفحه وجود ندارد)
    val secrets = remember { AdminSecrets(context) }
    val savedPin by secrets.adminPin.collectAsState(initial = "")
    val savedToken by secrets.githubToken.collectAsState(initial = "")

    var authed by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    // صندوق پشتیبانی (نسخه ۱.۵)
    var supMsgs by remember { mutableStateOf<List<SupportMessage>>(emptyList()) }
    // اطلاعیه‌ها — زنگ کنار منوی سه‌نقطه (نسخه ۱.۶)
    var anns by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var annType by remember { mutableStateOf("news") }
    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    var annLink by remember { mutableStateOf("") }
    // تصویر اطلاعیه — گالری یا URL (نسخه ۱.۱۱.۷)
    var annPickedImage by remember { mutableStateOf("") }
    var annImageUrl by remember { mutableStateOf("") }
    // انتشار همگانی اطلاعیه برای کاربران — نسخه ۱.۱۱.۱۶ (درخواست کاربر)
    var ghToken by remember { mutableStateOf("") }
    // نسخهٔ ۱.۱۱.۲۳ (گزارش کاربر: «توکن ذخیره می‌کنم ولی بعد از برگشت دوباره باید بگذارم»):
    // collectAsState با مقدار اولیهٔ خالی شروع می‌شود و فیلد در فریم اول خالی قفل
    // می‌شد؛ حالا وقتی تنظیمات واقعی از DataStore رسید، توکن ذخیره‌شده در فیلد
    // می‌نشیند — بدون بازنویسی چیزی که کاربر در حال تایپ آن است.
    LaunchedEffect(savedToken) {
        if (savedToken.isNotBlank() && ghToken.isBlank()) {
            ghToken = savedToken
        }
    }
    var pubStatus by remember { mutableStateOf("") }
    var pubBusy by remember { mutableStateOf(false) }
    // انتشار همگانی بنرهای صفحه اصلی — نسخهٔ ۱.۱۱.۲۱ (درخواست کاربر)
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

    // بارگذاری فهرست کاربران و پیام‌های پشتیبانی پس از ورود
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
        }
    }

    // انتخاب تصویر بنر از گالری — کپی به filesDir/ads (نسخه ۱.۱۱.۶)
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

    // انتخاب تصویر اطلاعیه از گالری — کپی به filesDir/announcements (نسخه ۱.۱۱.۷)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding() // نسخه ۱.۱۱.۲: سرصفحه زیر نوار وضعیت سیستم نمی‌رود
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
            Spacer(Modifier.weight(1f))
            Text("پنل مدیریت", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))

        if (!authed) {
            if (savedPin.isBlank()) {
                // ---------- نسخهٔ ۱.۱۱.۲۹ (بازرسی امنیتی): پیش‌فرض «۱۲۳۴» حذف شد —
                // اولین ورود اجبار به تنظیم رمز (حداقل ۶ کاراکتر، حرف + عدد) دارد
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("تنظیم رمز پنل مدیریت", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "این پنل فقط روی همین گوشی کار می‌کند و برای مدیریت بنرها، اطلاعیه‌ها و انتشار است. " +
                                "هنوز رمزی تنظیم نشده — یک رمز (حداقل ۶ کاراکتر، شامل حرف و عدد) تعیین کنید. این رمز فقط روی همین دستگاه ذخیره می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.take(24) },
                            singleLine = true,
                            label = { Text("رمز جدید (حداقل ۶ کاراکتر، حرف + عدد)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
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
                                        authed = true
                                        Toast.makeText(context, "رمز تنظیم شد — وارد پنل شدید", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تنظیم رمز و ورود") }
                    }
                }
            } else {
                // ---------- ورود با رمز ----------
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("ورود توسعه‌دهنده", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "این پنل برای مدیریت بنرهای تبلیغ صفحه اصلی، اطلاعیه‌ها و انتشار برای کاربران است. رمزی که در اولین ورود تنظیم کرده‌اید را وارد کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.take(24) },
                            singleLine = true,
                            label = { Text("رمز مدیریت (حروف یا عدد)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (pin == savedPin) {
                                    authed = true
                                    pin = ""
                                } else {
                                    Toast.makeText(context, "رمز اشتباه است", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ورود به پنل") }
                    }
                }
            }
        } else {
            // ---------- تبلیغات صفحه اصلی — بنرهای اختصاصی مدیر (نسخه ۱.۱۱.۶) ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("تبلیغات صفحه اصلی", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "بنرهای تبلیغ خودتان را به صفحه اصلی تقویم اضافه کنید — هر چند تا که بخواهید. " +
                                "بنرهای فعال به‌ترتیب از بالا به پایین، پیش از کارت اوقات شرعی نمایش داده می‌شوند. " +
                                "تصویر و لینک اختیاری‌اند؛ اگر بنری نسازید صفحه اصلی بدون هیچ تبلیغی می‌ماند.",
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
                                                    (if (ad.link.isNotBlank()) " · لینک‌دار" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                    TextButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            MainAdBox.delete(context, ad.id)
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                ads = MainAdBox.load(context)
                                                // نسخهٔ ۱.۱۱.۲۶ — یادآوری حذف همگانی: حذف محلی به تنهایی
                                                // از دستگاه کاربران برداشته نمی‌شود؛ انتشار دوباره لازم است
                                                adPubStatus = "بنر حذف شد ✓ — برای اینکه حذف به دستگاه کاربران هم برسد، دکمهٔ «انتشار بنرها برای کاربران» را بزنید (از نسخهٔ ۱.۱۱.۲۶ کاربران بنرِ حذف‌شده را حتی وقتی خوراک کامل خالی شده هم برمی‌دارند)"
                                                Toast.makeText(
                                                    context,
                                                    "حذف شد — برای رسیدن حذف به کاربران، دوباره «انتشار بنرها» را بزنید",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }) { Text("حذف") }
                                }
                                if (ad.body.isNotBlank()) {
                                    Text(ad.body, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // ---------- فرم افزودن بنر جدید ----------
                    Text("افزودن بنر جدید", style = MaterialTheme.typography.titleSmall)
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
                        label = { Text("لینک (اختیاری)") },
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
                    Button(
                        onClick = {
                            if (adTitle.trim().length < 2) {
                                Toast.makeText(context, "عنوان بنر را وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val finalImage = adPickedImage.ifBlank { adImageUrl.trim() }
                            scope.launch(Dispatchers.IO) {
                                MainAdBox.add(
                                    context,
                                    title = adTitle,
                                    body = adBody,
                                    cta = adCta,
                                    link = ir.persiancalendar.app.ui.components.LinkOpener.normalize(adLink),
                                    image = finalImage,
                                    colorIndex = adColorIndex
                                )
                                val fresh = MainAdBox.load(context)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    ads = fresh
                                    adTitle = ""; adBody = ""; adCta = ""; adLink = ""
                                    adPickedImage = ""; adImageUrl = ""; adColorIndex = 0
                                    Toast.makeText(
                                        context,
                                        "بنر به صفحه اصلی اضافه شد",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ افزودن بنر به صفحه اصلی") }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- آمار ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("آمار", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "کاربران ثبت‌نام‌شده در این دستگاه: ${users.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "حساب‌ها آفلاین و روی هر گوشی جداگانه ذخیره می‌شوند؛ آمار کل نصب‌ها را در سایت گیت‌هاب ← Insights ← Traffic (Clones) ببینید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- کاربران ----------
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

            // ---------- صندوق پشتیبانی (نسخه ۱.۵) ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("صندوق پشتیبانی", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "پیام‌هایی که کاربران از بخش پشتیبانی (درخواست تبلیغ / گزارش مشکل / پیشنهاد) ارسال کرده‌اند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (supMsgs.isEmpty()) {
                        Text(
                            "صندوق پشتیبانی خالی است",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        supMsgs.asReversed().forEach { m ->
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

            // ---------- اطلاعیه‌ها — زنگ کنار منوی سه‌نقطه (نسخه ۱.۶) ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("اطلاعیه‌ها (خبر و تبلیغ)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "خبر یا تبلیغی که اینجا ثبت کنید، با زنگ و نقطه قرمز کنار منوی سه‌نقطه به کاربران نشان داده می‌شود. لینک و تصویر اختیاری‌اند (نسخه ۱.۱۱.۷: عکس اطلاعیه).",
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
                                                // نسخهٔ ۱.۱۱.۲۶ — یادآوری حذف همگانی (گزارش کاربر:
                                                // «حذف میکنم از پنل مدیریت واسه کاربرا حذف نمیشه»)
                                                pubStatus = "اطلاعیه حذف شد ✓ — برای اینکه حذف به دستگاه کاربران هم برسد، دکمهٔ «انتشار برای کاربران» را بزنید (از نسخهٔ ۱.۱۱.۲۶ کاربران اطلاعیهٔ حذف‌شده را حتی وقتی خوراک کامل خالی شده هم برمی‌دارند)"
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
                    // ---------- تصویر اطلاعیه — گالری یا URL (نسخه ۱.۱۱.۷) ----------
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

            // ---------- انتشار همگانی اطلاعیه برای کاربران — نسخه ۱.۱۱.۱۶ ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("انتشار برای کاربران (اعلان سیستمی)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "اطلاعیه‌هایی که بالاتر ثبت می‌کنید فقط روی همین دستگاه بودند. با «انتشار برای کاربران»، " +
                            "فهرست اطلاعیه‌ها روی مخزن گیت‌هاب برنامه منتشر می‌شود و همهٔ کاربران هم به زنگ برنامه " +
                            "هم اعلان سیستمی می‌گیرند (بررسی در هر باز شدن برنامه و هر نیمه‌شب)." +
                            " عکس گالری با فشرده‌سازی خودکار تا ۲۰۰ کیلوبایت داخل خوراک جاسازی می‌شود و عکس URL مستقیم منتشر می‌شود (از نسخهٔ ۱.۱۱.۲۵)." +
                            " مهم: بعد از حذف یا خاموش‌کردن اطلاعیه، دوباره همین دکمهٔ انتشار را بزنید تا از دستگاه کاربران هم برداشته شود (نسخهٔ ۱.۱۱.۲۶).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ghToken,
                        onValueChange = { ghToken = it.take(120) },
                        singleLine = true,
                        label = { Text("توکن گیت‌هاب (فقط روی این دستگاه ذخیره می‌شود)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "راهنما: در گیت‌هاب ← Settings ← Developer settings ← Personal access tokens ← " +
                            "Fine-grained token با دسترسی Contents: Read and write و Actions: Read and write فقط برای مخزن PersianCalendar بسازید (Actions برای ساخته‌شدن خودکار امضای خوراک لازم است). " +
                            "توکن فقط روی همین دستگاه می‌ماند و هرگز جایی ارسال نمی‌شود؛ برای امنیت بیشتر تاریخ انقضای کوتاه بگذارید و " +
                            "اگر گوشی گم شد یا مشکوک به لو رفتن هستید، اول توکن را در گیت‌هاب Revoke کنید و بعد «پاک کردن توکن» را بزنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    // نسخهٔ ۱.۱۱.۲۳: نتیجهٔ واقعی ذخیره گزارش می‌شود، نه پیام همیشگی
                                    runCatching { secrets.setGithubToken(ghToken) }
                                        .onSuccess {
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "توکن ذخیره شد — بعد از خروج هم می‌ماند", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .onFailure { e ->
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "خطا در ذخیرهٔ توکن: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ذخیره توکن") }
                        // نسخهٔ ۱.۱۱.۲۹ (بازرسی امنیتی): پاک‌کردن فوری توکن از دستگاه
                        // — اگر گوشی گم شد یا توکن لو رفت، اول در گیت‌هاب Revoke بعد اینجا پاک کنید
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching { secrets.setGithubToken("") }
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
                                // نسخهٔ ۱.۱۱.۲۷: خوراک خالی دیگر «ناموفق» نیست — چهار حالت جدا گزارش می‌شود
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
                                            "خوراک فعلی کاربران: ${cr.count} اطلاعیه — دریافت موفق از ${cr.source} (نسخهٔ ۱.۱۱.۲۲ به بعد از چند منبع می‌گیرد)"
                                        else ->
                                            "خوراک کاربران در حال حاضر خالی است (۰ اطلاعیه) — دریافت موفق از ${cr.source}. یعنی فهرست خالی برای همه منتشر شده و مشکلی نیست؛ برای رسیدن اطلاعیهٔ تازه، آن را ثبت و دکمهٔ «انتشار برای کاربران» را بزنید. (تا نسخهٔ ۱.۱۱.۲۶ این حالت به‌اشتباه «دریافت خوراک ناموفق» نمایش داده می‌شد)"
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
                                secrets.setGithubToken(ghToken)
                                val list = AnnouncementBox.active(context)
                                val msg = ir.persiancalendar.app.data.support.FeedPublisher.publishAnnouncements(
                                    context, ghToken, list
                                )
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

                    Spacer(Modifier.height(14.dp))
                    // ---------- انتشار بنرهای صفحه اصلی — نسخهٔ ۱.۱۱.۲۱ (درخواست کاربر) ----------
                    Text("بنرهای تبلیغات صفحه اصلی", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "بنرهایی که در بخش «تبلیغات صفحه اصلی» می‌سازید تا این نسخه فقط روی همین دستگاه دیده می‌شدند. " +
                            "با این دکمه همهٔ بنرهای فعال برای همهٔ کاربران منتشر می‌شود؛ بعد از هر تغییر (افزودن، حذف، روشن/خاموش، ترتیب) " +
                            "دوباره همین دکمه را بزنید تا به همه برسد. عکس گالری تا ۲۰۰ کیلوبایت داخل خوراک جاسازی می‌شود و عکس URL مستقیم منتشر می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "نکتهٔ مهم: دریافت بنرها فقط در نسخهٔ ۱.۱۱.۲۱ به بعد برنامه وجود دارد؛ کاربران روی نسخه‌های قدیمی‌تر باید برنامه را به‌روز کنند. " +
                            "از نسخهٔ ۱.۱۱.۲۲ دریافت از سه منبع (raw و jsDelivr و api.github) انجام می‌شود تا در شبکه‌های مختلف ایران هم باز شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                // نسخهٔ ۱.۱۱.۲۷: خوراک خالی دیگر «ناموفق» نیست — چهار حالت جدا گزارش می‌شود
                                val cr = ir.persiancalendar.app.data.support.MainAdFeed.check(context)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    adPubStatus = when {
                                        !cr.ok ->
                                            "دریافت خوراک ناموفق: ${cr.error.take(160)} — اتصال اینترنت را بررسی کنید؛ منابع به‌ترتیب آزموده می‌شوند: سرور شخصی، raw، jsDelivr، api.github"
                                        cr.fromCache && cr.count > 0 ->
                                            "الان به منبع زنده دسترسی نبود — آخرین نسخهٔ کش‌شدهٔ همین دستگاه: ${cr.count} بنر. جزئیات تلاش‌ها: ${cr.error.take(120)}"
                                        cr.fromCache ->
                                            "الان به منبع زنده دسترسی نبود و کش همین دستگاه هم خالی است (۰ بنر). با اینترنت وصل دوباره «بررسی خوراک بنرها» را بزنید. جزئیات: ${cr.error.take(120)}"
                                        cr.count > 0 ->
                                            "خوراک فعلی کاربران: ${cr.count} بنر — دریافت موفق از ${cr.source}"
                                        else ->
                                            "خوراک بنرهای کاربران در حال حاضر خالی است (۰ بنر) — دریافت موفق از ${cr.source}. یعنی فهرست خالی برای همه منتشر شده و مشکلی نیست؛ برای نمایش بنر تازه، آن را بسازید و دکمهٔ «انتشار بنرها برای کاربران» را بزنید. (تا نسخهٔ ۱.۱۱.۲۶ این حالت به‌اشتباه «دریافت خوراک ناموفق» نمایش داده می‌شد)"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بررسی خوراک بنرهای کاربران") }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            adPubBusy = true
                            scope.launch(Dispatchers.IO) {
                                secrets.setGithubToken(ghToken)
                                val msg = ir.persiancalendar.app.data.support.FeedPublisher.publishMainAds(
                                    context, ghToken, MainAdBox.active(context)
                                )
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    adPubBusy = false
                                    adPubStatus = msg
                                    Toast.makeText(context, msg.take(60), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !adPubBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (adPubBusy) "در حال انتشار بنرها…" else "انتشار بنرها برای کاربران")
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

            // ---------- امنیت ----------
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
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

