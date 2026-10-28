package ir.persiancalendar.app.ui.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.ads.AdManager
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.UserEntity
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.prefs.BannerEntry
import ir.persiancalendar.app.data.prefs.bannerEntries
import ir.persiancalendar.app.data.support.Announcement
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.data.support.SupportBox
import ir.persiancalendar.app.data.support.SupportMessage
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * پنل مدیریت تبلیغات — نسخه ۱.۵
 *
 * صفحه‌ای مخصوص توسعه‌دهنده (با رمز) برای کنترل زنده تبلیغات:
 *  - روشن/خاموش کردن بنر و اینترستیشال
 *  - تناوب نمایش اینترستیشال
 *  - بنرهای تصویری اختصاصی «بدون محدودیت تعداد» + لینک هر بنر — نسخه ۱.۵
 *  - صندوق پشتیبانی: پیام‌های کاربران (تبلیغ/مشکل/پیشنهاد) — نسخه ۱.۵
 *  - جابه‌جایی بین شناسه‌های تست گوگل و شناسه‌های واقعی
 *  - مشاهده آمار و فهرست کاربران ثبت‌نام‌شده
 *  - تغییر رمز پنل — رمز می‌تواند حروف و اعداد با هم داشته باشد — نسخه ۱.۳
 *
 * تنظیمات در DataStore ذخیره می‌شود و بلافاصله روی بنر/اینترستیشال اثر می‌گذارد.
 */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AppContainer.settingsRepo(context)
    val settings by repo.settings.collectAsState(initial = AppSettings())

    var authed by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var bannerIdText by remember(settings.bannerUnitId) { mutableStateOf(settings.bannerUnitId) }
    var interIdText by remember(settings.interstitialUnitId) { mutableStateOf(settings.interstitialUnitId) }
    var users by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    // صندوق پشتیبانی (نسخه ۱.۵)
    var supMsgs by remember { mutableStateOf<List<SupportMessage>>(emptyList()) }
    // اطلاعیه‌ها — زنگ کنار منوی سه‌نقطه (نسخه ۱.۶)
    var anns by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var annType by remember { mutableStateOf("news") }
    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    var annLink by remember { mutableStateOf("") }

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
        }
    }

    // انتخاب عکس بنر از گالری/فایل‌ها — بنر جدید یا تعویض یک بنر موجود (نسخه ۱.۵)
    // آندیس هدف: -1 = افزودن بنر جدید؛ در غیر این صورت اندیس بنر در فهرست برای تعویض
    var uploadTarget by remember { mutableStateOf(-1) }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = uploadTarget
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.size > 5_000_000) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, "حجم تصویر باید کمتر از ۵ مگابایت باشد", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val f = File(context.filesDir, "admin_banner_b" + System.currentTimeMillis() + ".jpg")
                f.writeBytes(bytes)
                val r = AppContainer.settingsRepo(context)
                val list = r.settingsBlocking().bannerEntries().toMutableList()
                if (target < 0) {
                    list.add(BannerEntry(f.absolutePath, "", true))
                } else if (target < list.size) {
                    val old = list[target]
                    // فایل قبلی بنر تعویض‌شده حذف شود تا حافظه اشغال نماند
                    runCatching { File(old.path).delete() }
                    list[target] = BannerEntry(f.absolutePath, old.link, old.on)
                }
                r.setBannerListJson(entriesToJson(list))
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "بنر ذخیره شد ✓ — در چرخش پایین صفحه", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خواندن تصویر ممکن نشد", Toast.LENGTH_SHORT).show()
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
            // ---------- ورود با رمز ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("ورود توسعه‌دهنده", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "این پنل برای مدیریت تبلیغات و مشاهده کاربران است. رمز پیش‌فرض ۱۲۳۴ است؛ حتماً قبل از انتشار عوضش کنید.",
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
                            if (pin == settings.adminPin) {
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
        } else {
            // ---------- مدیریت تبلیغات ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("تبلیغات AdMob", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "نمایش بنر تبلیغات",
                        subtitle = "نوار تبلیغ در پایین صفحه‌ها",
                        checked = settings.adsEnabled
                    ) { v -> scope.launch { repo.setAdsEnabled(v) } }
                    SwitchRow(
                        title = "اینترستیشال",
                        subtitle = "تبلیغ تمام‌صفحه هنگام باز کردن جزئیات روز",
                        checked = settings.interstitialEnabled
                    ) { v -> scope.launch { repo.setInterstitialEnabled(v) } }
                    Spacer(Modifier.height(6.dp))
                    SimpleDropdown(
                        modifier = Modifier.fillMaxWidth(),
                        label = "تناوب نمایش اینترستیشال",
                        items = listOf("هر بار", "هر ۲ بار", "هر ۳ بار", "هر ۴ بار", "هر ۵ بار", "هر ۱۰ بار"),
                        selectedIndex = intEveryToIndex(settings.interstitialEvery),
                        onSelected = { idx ->
                            scope.launch { repo.setInterstitialEvery(intEveryFromIndex(idx)) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "حالت آزمایشی (شناسه تست گوگل)",
                        subtitle = "پیش از انتشار خاموش کنید تا شناسه‌های واقعی استفاده شوند",
                        checked = settings.adTestMode
                    ) { v -> scope.launch { repo.setAdTestMode(v) } }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bannerIdText,
                        onValueChange = { bannerIdText = it },
                        singleLine = true,
                        label = { Text("شناسه واقعی بنر (ca-app-pub-…)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = interIdText,
                        onValueChange = { interIdText = it },
                        singleLine = true,
                        label = { Text("شناسه واقعی اینترستیشال (ca-app-pub-…)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repo.setBannerUnitId(bannerIdText)
                                repo.setInterstitialUnitId(interIdText)
                                Toast.makeText(context, "شناسه‌ها ذخیره شد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره شناسه‌ها") }

                    // ---------- بنرهای تصویری چرخشی — بدون محدودیت (نسخه ۱.۵) ----------
                    Spacer(Modifier.height(14.dp))
                    Text("بنرهای تصویری چرخشی (چندین تبلیغ — بدون محدودیت)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "هر تعداد بنر بخواهید اضافه کنید؛ بنرهای «روشن» به‌صورت چرخشی (هر ۶ ثانیه) در محدوده تبلیغات پایین نمایش داده می‌شوند؛ اگر بنری نباشد فضای AdMob نشان داده می‌شود. بنرهای نسخه‌های قبلی به‌صورت خودکار به این فهرست منتقل می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val banners = settings.bannerEntries()
                    if (banners.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "هنوز بنری اضافه نشده — با دکمه «افزودن بنر جدید» اولین تبلیغ خود را بسازید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    banners.forEachIndexed { i, b ->
                        val imgFile = File(b.path)
                        val exists = imgFile.exists()
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "بنر " + PersianText.toPersianDigits((i + 1).toString()),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (b.on) "روشن — در چرخش نمایش" else "خاموش — نمایش داده نمی‌شود",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = b.on,
                                onCheckedChange = { v ->
                                    scope.launch {
                                        repo.setBannerListJson(
                                            entriesToJson(banners.mapIndexed { j, e -> if (j == i) e.copy(on = v) else e })
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = {
                                uploadTarget = i
                                imgPicker.launch("image/*")
                            }) { Text("تعویض") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching { imgFile.delete() }
                                    repo.setBannerListJson(
                                        entriesToJson(banners.filterIndexed { j, _ -> j != i })
                                    )
                                }
                                Toast.makeText(context, "بنر حذف شد", Toast.LENGTH_SHORT).show()
                            }) { Text("حذف") }
                        }
                        if (exists) {
                            val bmp = remember(b.path) {
                                runCatching { BitmapFactory.decodeFile(imgFile.absolutePath) }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "پیش‌نمایش بنر " + (i + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        var linkText by remember(b.link) { mutableStateOf(b.link) }
                        OutlinedTextField(
                            value = linkText,
                            onValueChange = { linkText = it },
                            singleLine = true,
                            label = { Text("لینک بنر " + (i + 1) + " (اختیاری — با لمس باز می‌شود)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repo.setBannerListJson(
                                        entriesToJson(banners.mapIndexed { j, e -> if (j == i) e.copy(link = linkText.trim()) else e })
                                    )
                                    Toast.makeText(context, "لینک بنر ذخیره شد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ذخیره لینک") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            uploadTarget = -1
                            imgPicker.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ افزودن بنر جدید") }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- آمار ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("آمار", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "تعداد باز شدن جزئیات روز (شمارنده اینترستیشال): ${settings.interstitialCounter}",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        "درآمد واقعی در کنسول AdMob دیده می‌شود؛ این شمارنده‌ها فقط برای تنظیم تناوب‌اند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        "خبر یا تبلیغی که اینجا ثبت کنید، با زنگ و نقطه قرمز کنار منوی سه‌نقطه به کاربران نشان داده می‌شود. لینک اختیاری است.",
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
                                            if (a.on) "نمایش به کاربران" else "خاموش",
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
                                    link = annLink
                                )
                                val fresh = AnnouncementBox.load(context)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    anns = fresh
                                    annTitle = ""; annBody = ""; annLink = ""
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
                        "رمز باید هم حرف داشته باشد و هم عدد — مثلاً admin1403",
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
                                    context, "رمز باید هم حرف و هم عدد داشته باشد — مثلاً abc1234", Toast.LENGTH_SHORT
                                ).show()
                                else -> scope.launch {
                                    repo.setAdminPin(newPin)
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

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** نگاشت تناوب ۱،۲،۳،۴،۵،۱۰ به اندیس فهرست */
private fun intEveryToIndex(n: Int): Int = when (n) {
    1 -> 0
    2 -> 1
    3 -> 2
    4 -> 3
    5 -> 4
    else -> 5
}

/** فهرست بنرها → JSON برای ذخیره در DataStore (نسخه ۱.۵) */
private fun entriesToJson(list: List<BannerEntry>): String {
    val arr = org.json.JSONArray()
    list.forEach { e ->
        arr.put(
            org.json.JSONObject()
                .put("p", e.path)
                .put("l", e.link)
                .put("on", e.on)
        )
    }
    return arr.toString()
}

private fun intEveryFromIndex(i: Int): Int = when (i) {
    0 -> 1
    1 -> 2
    2 -> 3
    3 -> 4
    4 -> 5
    else -> 10
}
