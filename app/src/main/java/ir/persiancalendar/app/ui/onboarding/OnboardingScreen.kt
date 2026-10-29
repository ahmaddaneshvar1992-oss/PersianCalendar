package ir.persiancalendar.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.location.LocationHelper
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.ui.components.CityPickerDialog
import ir.persiancalendar.app.ui.components.JalaliDatePickerDialog
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.rememberGpsServiceEnabler
import ir.persiancalendar.app.ui.reminder.isIgnoringBattery
import ir.persiancalendar.app.ui.reminder.requestIgnoreBattery
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * ویزارد راه‌اندازی اولیه — نسخه ۱.۱۱.۴ (درخواست کاربر)
 *
 * قبل از ورود به برنامه، ۵ گام مثل اپ‌های مشابه پرسیده می‌شود:
 *  ۱) گام اول: تنظیم افق — انتخاب شهر (با جست‌وجو) یا مکان‌یاب GPS
 *  ۲) گام دوم: تنظیم تاریخ و زمان — نمایش تاریخ شمسی/میلادی/قمری و ساعت
 *  ۳) گام سوم: اذان‌ها — انتخاب کدام اوقات اعلان اذان داده شود (صبح، ظهر، عصر، مغرب، عشا)
 *  ۴) گام چهارم: اعلان‌ها — چک‌لیست کامل مجوزها (نسخه ۱.۱۱.۱۱، اجباری از نسخه ۱.۱۱.۱۲):
 *     مجوز اعلان اندروید ۱۳+ + آلارم دقیق اندروید ۱۲+ + استثنا از بهینه‌سازی باتری
 *     با وضعیت زنده (بعد از برگشت از تنظیمات خودکار به‌روز می‌شود)
 *     نسخه ۱.۱۱.۱۲ (درخواست کاربر): هیچ راه فراری نیست — دکمه‌های
 *     «رد کردن همه گام‌ها» و «فعلاً نه — بعداً تنظیم می‌کنم» حذف شدند و
 *     دکمه «مرحله بعد» تا فعال‌شدن مجوز اعلان (اندروید ۱۳+) قفل است.
 *     هنگام رسیدن به گام چهارم هم دیالوگ مجوز خودکار یک بار باز می‌شود.
 *  ۵) گام پنجم: حساب کاربری — معرفی عضویت ویژه (اختیاری؛ می‌توان بعداً ثبت‌نام کرد)
 *
 * نتیجه در DataStore ذخیره می‌شود (onboardingDone=true) و MainScreen جایگزین می‌شود.
 * اجرای دوباره: تنظیمات → «اجرای دوباره راه‌اندازی اولیه».
 */
private val stepTitles = listOf(
    "گام اول: تنظیم افق",
    "گام دوم: تاریخ و زمان",
    "گام سوم: اذان‌ها",
    "گام چهارم: اعلان‌ها",
    "گام پنجم: حساب کاربری"
)

/** بیت‌های ماسک اعلان اوقات — فقط پنج وقت نماز (طلوع/غروب اذان ندارند) */
private val azanOptions = listOf(
    Triple(1, "اذان صبح", "نزدیک اذان صبح شهر شما"),
    Triple(4, "اذان ظهر", "نزدیک اذان ظهر شهر شما"),
    Triple(8, "اذان عصر", "نزدیک اذان عصر شهر شما"),
    Triple(32, "اذان مغرب", "نزدیک اذان مغرب شهر شما"),
    Triple(64, "اذان عشا", "نزدیک اذان عشا شهر شما")
)

@Composable
fun OnboardingScreen(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AppContainer.settingsRepo(context) }

    var step by remember { mutableStateOf(0) }

    // ---------- بازخوانی وضعیت مجوزها بعد از برگشت از تنظیمات (نسخه ۱.۱۱.۱۱) ----------
    val activity = context as? androidx.activity.ComponentActivity
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        activity?.lifecycle?.addObserver(obs)
        onDispose { activity?.lifecycle?.removeObserver(obs) }
    }
    val notifOk = remember(resumeTick) { notifGranted(context) }
    val exactOk = remember(resumeTick) { AlarmScheduler.canExact(context) }
    val batteryOk = remember(resumeTick) { isIgnoringBattery(context) }

    // ---------- گام ۱: شهر ----------
    var cityIndex by remember { mutableStateOf(settings.cityIndex) }
    var cityPicker by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>(null) }

    // ---------- گام ۳: اذان‌ها ----------
    var azanSet by remember { mutableStateOf(settings.prayerNotifications) }

    // ---------- گام ۴: اعلان‌ها ----------
    var notifState by remember { mutableStateOf(notifGranted(context)) }

    // ---------- گام ۵: مشخصات کاربر — جنسیت و تاریخ تولد (نسخه ۱.۱۱.۱۶ مطابق عکس کاربر) ----------
    var gender by remember { mutableStateOf(settings.profileGender) }
    // تاریخ تولد ذخیره‌شده با قالب "jy/jm/jd"
    val savedBirth = remember {
        settings.profileBirth.split("/").mapNotNull { it.toIntOrNull() }
            .takeIf { it.size == 3 }
            ?.let { (y, m, d) -> JalaliDate(y, m, d) }
    }
    var birthJ by remember { mutableStateOf(savedBirth) }
    var birthPicker by remember { mutableStateOf(false) }
    val currentJy = remember {
        LocalDate.now().let {
            CalendarConverter.gregorianToJalali(it.year, it.monthValue, it.dayOfMonth).year
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifState = granted || notifGranted(context)
        if (granted) Toast.makeText(context, "اعلان‌ها فعال شد ✓", Toast.LENGTH_SHORT).show()
        else Toast.makeText(
            context,
            "بدون مجوز اعلان، یادآورها و اذان کار نمی‌کنند — لطفاً فعال کنید",
            Toast.LENGTH_LONG
        ).show()
    }

    // نسخه ۱.۱۱.۱۲: با رسیدن به گام چهارم، دیالوگ مجوز اعلان خودکار یک بار باز می‌شود
    var autoAskedNotif by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step == 3 && !autoAskedNotif && Build.VERSION.SDK_INT >= 33 && !notifGranted(context)) {
            autoAskedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // مکان‌یاب گام اول — با لانچر استاندارد مجوز موقعیت (نسخه ۱.۱۱.۴)
    val gpsStatusSet: (Int, String) -> Unit = { idx, name ->
        cityIndex = idx
        gpsStatus = "نزدیک‌ترین شهر به موقعیت شما: $name"
    }

    // روشن‌کردن خودکار سرویس موقعیت (GPS) — نسخه ۱.۱۱۷ (درخواست کاربر مطابق عکس):
    // دیگر لازم نیست کاربر خودش به تنظیمات برود؛ دیالوگ سیستمی یک‌لمسی
    // «فعال‌سازی موقعیت» باز می‌شود و بعد از تأیید، مکان‌یاب خودکار ادامه می‌یابد؛
    // روی گوشی‌های بدون پلی‌سرویس مستقیم صفحهٔ تنظیمات موقعیت باز می‌شود و
    // بعد از روشن‌کردن کلید و بازگشت، جست‌وجو خودکار ادامه می‌یابد.
    val enableLocationService = rememberGpsServiceEnabler(
        onServiceReady = { findGps(context, gpsStatusSet) },
        onFailed = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    )

    fun startGpsFlow() {
        if (LocationHelper.isServiceEnabled(context)) findGps(context, gpsStatusSet)
        else enableLocationService()
    }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startGpsFlow()
        else Toast.makeText(context, "دسترسی به موقعیت داده نشد", Toast.LENGTH_SHORT).show()
    }

    fun persistAndFinish() {
        scope.launch {
            repo.setCityIndex(cityIndex)
            repo.setPrayerNotifications(azanSet)
            // مشخصات اختیاری کاربر — نسخه ۱.۱۱.۱۶ (مطابق عکس کاربر)
            repo.setProfileGender(gender)
            birthJ?.let { repo.setProfileBirth(it.year, it.month, it.day) }
            repo.setOnboardingDone(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // ---------- سرصفحه: شماره گام + نوار پیشرفت ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp)
            ) {
                Text(
                    "${PersianText.toPersianDigits((step + 1).toString())}/${PersianText.toPersianDigits("5")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.size(8.dp))
            Column {
                Text(stepTitles[step], style = MaterialTheme.typography.titleLarge)
                Text(
                    "راه‌اندازی اولیه بادخزان",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        // ============================================================
        when (step) {
            // ---------------- گام ۱: افق (شهر) ----------------
            0 -> {
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("شهر خود را انتخاب کنید", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اوقات شرعی و اذان بر اساس این شهر نمایش داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        val c = Cities.list[cityIndex.coerceIn(0, Cities.list.lastIndex)]
                        Text(
                            "افق فعلی: " + c.name + if (c.province.isNotBlank()) " — " + c.province else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (gpsStatus != null) {
                            Text(
                                gpsStatus!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cityPicker = true }, modifier = Modifier.weight(1f)) {
                                Text("انتخاب دستی شهر")
                            }
                            OutlinedButton(
                                onClick = {
                                    val ok = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED ||
                                            ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                    if (ok) startGpsFlow()
                                    else locLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("مکان‌یاب با GPS") }
                        }
                    }
                }
            }

            // ---------------- گام ۲: تاریخ و زمان ----------------
            1 -> {
                val now = remember { LocalDate.now() }
                val j = remember {
                    CalendarConverter.gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
                }
                val h = remember {
                    CalendarConverter.gregorianToHijri(now.year, now.monthValue, now.dayOfMonth)
                }
                val t = remember { LocalTime.now() }
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("درستی تاریخ و ساعت را بررسی کنید", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "برای درست کار کردن تقویم و اوقات شرعی، تاریخ و ساعت گوشی باید درست باشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "تاریخ امروز: " + PersianText.jalaliDateFull(
                                    JalaliDate(j.year, j.month, j.day), true
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "ساعت: " + PersianText.toPersianDigits(
                                    String.format("%02d:%02d", t.hour, t.minute)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            PersianText.hijriDateFull(h, true),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "میلادی: " + PersianText.toPersianDigits(
                                "${now.year}/${now.monthValue}/${now.dayOfMonth}"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- گام ۳: اذان‌ها ----------------
            2 -> {
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("اذان‌های مورد نظر خود را فعال کنید", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "برای وقت‌های انتخاب‌شده، نزدیک اوقات شرعی شهر شما اعلان اذان می‌آید. بعداً هم از صفحه اوقات شرعی قابل تغییر است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        azanOptions.forEach { (bit, name, desc) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = bit in azanSet,
                                    onCheckedChange = { checked ->
                                        azanSet = if (checked) azanSet + bit else azanSet - bit
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- گام ۴: اعلان‌ها — چک‌لیست کامل مجوزها (نسخه ۱.۱۱.۱۱) ----------------
            3 -> {
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("اجازه اعلان‌ها", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "برای یادآورها (آب، دارو، ورزش و کارهای شخصی)، اذان و اعلان همیشگی تاریخ، " +
                                "برنامه به هر سه اجازه زیر نیاز دارد تا همه اعلان‌ها سرِ وقت برسند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        // --- ۱) مجوز اعلان (اندروید ۱۳+) ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (notifOk) "✓" else "✗",
                                color = if (notifOk) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (notifOk) "اعلان‌ها فعال است"
                                else "مجوز اعلان داده نشده — بخشی از امکانات کار نمی‌کند",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!notifOk) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else notifState = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("فعال‌سازی اعلان‌ها") }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("باز کردن تنظیمات اعلان‌های گوشی") }
                        }

                        Spacer(Modifier.height(10.dp))

                        // --- ۲) آلارم دقیق (اندروید ۱۲+) ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (exactOk) "✓" else "✗",
                                color = if (exactOk) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (exactOk) "آلارم دقیق فعال است"
                                else "آلارم دقیق غیرفعال — یادآوری‌ها ممکن است تأخیر داشته باشند",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!exactOk) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("اجازه آلارم دقیق") }
                        }

                        Spacer(Modifier.height(10.dp))

                        // --- ۳) بهینه‌سازی باتری ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (batteryOk) "✓" else "✗",
                                color = if (batteryOk) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (batteryOk) "از بهینه‌سازی باتری مستثنا است"
                                else "بهینه‌سازی باتری — بدون استثنا، اعلان‌ها روی بعضی گوشی‌ها نمی‌رسند",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!batteryOk) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { requestIgnoreBattery(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("استثنا کردن از بهینه‌سازی باتری") }
                        }
                    }
                }
            }

            // ---------------- گام ۵: مشخصات و حساب کاربری ----------------
            // نسخه ۱.۱۱.۱۶ (درخواست کاربر مطابق عکس): جنسیت و تاریخ تولد مثل نمونهٔ «بادسبا»
            4 -> {
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("مشخصات شما", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "کاربر گرامی، تکمیل اطلاعات به ارائهٔ خدمات بهتر به شما کمک می‌کند." +
                                " (اختیاری — می‌توانید خالی بگذارید)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        // ---------- جنسیت: مرد / زن ----------
                        Text("جنسیت", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = gender == "male",
                                onClick = { gender = if (gender == "male") "" else "male" },
                                label = { Text("مرد") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            FilterChip(
                                selected = gender == "female",
                                onClick = { gender = if (gender == "female") "" else "female" },
                                label = { Text("زن") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        // ---------- تاریخ تولد — انتخابگر تاریخ شمسی (مطابق عکس) ----------
                        Text("تاریخ تولد", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { birthPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (birthJ != null)
                                    PersianText.jalaliDateFull(birthJ!!, true)
                                else "انتخاب تاریخ"
                            )
                        }
                        if (birthJ != null) {
                            TextButton(onClick = { birthJ = null }) {
                                Text("پاک کردن تاریخ تولد", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("حساب کاربری و عضویت ویژه", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "با ساخت حساب کاربری می‌توانید عضویت ویژه بگیرید و از توسعه بادخزان حمایت کنید؛ وضعیت ویژه روی همین دستگاه ثبت می‌شود. ثبت‌نام کاملاً روی همین دستگاه انجام می‌شود و اجباری نیست.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "می‌توانید همین حالا از دکمه پایین وارد شوید یا بعداً از منوی سه‌نقطه ← تنظیمات ← حساب کاربری این کار را انجام دهید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---------- دکمه‌های پایین ----------
        // نسخه ۱.۱۱.۱۲: در گام چهارم، «مرحله بعد» تا فعال‌شدن مجوز اعلان قفل است
        // (اندروید ۱۲ به پایین همیشه مجاز است و قفل معنا ندارد) — بدون هیچ دکمه رد کردن.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                    Text("مرحله قبل")
                }
            }
            if (step < 4) {
                Button(
                    onClick = { step++ },
                    enabled = step != 3 || notifOk,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مرحله بعد")
                }
            } else {
                Button(onClick = { persistAndFinish() }, modifier = Modifier.weight(1f)) {
                    Text("شروع تقویم")
                }
            }
        }
        if (step == 3 && !notifOk) {
            Spacer(Modifier.height(6.dp))
            Text(
                "برای ادامه، اعلان‌ها را فعال کنید — بدون آن، یادآورها، اذان و اعلان تاریخ نمی‌رسند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (cityPicker) {
        CityPickerDialog(
            cities = Cities.list,
            selectedIndex = cityIndex,
            onDismiss = { cityPicker = false },
            onSelected = { idx ->
                cityIndex = idx
                gpsStatus = null
            }
        )
    }

    // انتخابگر تاریخ تولد — نسخه ۱.۱۱.۱۶ (بازهٔ سال ۱۳۰۰ تا امسال)
    if (birthPicker) {
        JalaliDatePickerDialog(
            initial = birthJ ?: JalaliDate(currentJy - 39, 1, 1),
            minYear = 1300,
            maxYear = currentJy,
            title = "انتخاب تاریخ تولد",
            onDismiss = { birthPicker = false },
            onSelected = {
                birthJ = it
                birthPicker = false
            }
        )
    }
}

/** وضعیت مجوز اعلان — اندروید ۱۲ به پایین همیشه مجاز */
private fun notifGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

/** مکان‌یاب: نزدیک‌ترین شهر را برمی‌گرداند (رفع باگ GPS — نسخه ۱.۱۱.۴) */
private fun findGps(context: Context, onCity: (Int, String) -> Unit) {
    LocationHelper.fetchOnce(
        context,
        onResult = { la, lo ->
            val (idx, city) = Cities.nearest(la, lo)
            onCity(idx, city.name)
        },
        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    )
}
