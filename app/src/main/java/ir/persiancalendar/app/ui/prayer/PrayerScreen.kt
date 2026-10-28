package ir.persiancalendar.app.ui.prayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerMethod
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.ui.components.CityPickerDialog
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalDate

/**
 * صفحه اوقات شرعی — نسخه ۱.۱۰:
 * انتخاب استان/شهرستان و روش محاسبه، نوار «نزدیک‌ترین وقت شرعی»، اعلان هوشمند اوقات،
 * اذان دلخواه و جهت قبله. کارت نوار افقی ۸ وقت (مطابق عکس کاربر) به درخواست او به
 * «پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه» منتقل شد (PrayerTimesCard.kt).
 */
@Composable
fun PrayerScreen(
    settings: AppSettings,
    persianDigits: Boolean,
    onSettingsChange: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useLocation by remember { mutableStateOf(false) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    // دیالوگ انتخاب شهر با جست‌وجو
    var cityPickerVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            locateNearestCity(context) { la, lo ->
                val (idx, near) = Cities.nearest(la, lo)
                lat = null; lng = null; useLocation = false
                scope.launch { AppContainer.settingsRepo(context).setCityIndex(idx) }
                onSettingsChange()
                android.widget.Toast.makeText(
                    context,
                    "نزدیک‌ترین شهر: ${near.name} — ${near.province}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        today = LocalDate.now()
        // زمان‌بندی اعلان‌های امروز (اگر کاربر اعلان اوقات شرعی را فعال کرده)
        val s = AppContainer.settingsRepo(context).settings.first()
        AlarmScheduler.schedulePrayerAlarms(
            context, s.prayerNotifications, lat, lng, s.cityIndex, s.prayerMethod
        )
    }

    val city = Cities.list[settings.cityIndex.coerceIn(0, Cities.list.size - 1)]
    val effLat = lat ?: city.lat
    val effLng = lng ?: city.lng
    val times = PrayerTimesCalculator.compute(
        today, effLat, effLng, Cities.IRAN_TZ_HOURS, settings.prayerMethod
    )

    // ---------- انتخاب استانی/شهرستانی آبشاری (نسخه ۱.۲) ----------
    val provinces = Cities.provinces
    val provIdx = provinces.indexOf(city.province).let { if (it < 0) 0 else it }
    val cityChoices = remember(provIdx) { Cities.indexesOfProvince(provinces[provIdx]) }
    val cityChoiceIdx = cityChoices.indexOf(settings.cityIndex)

    fun pickCity(idx: Int) {
        useLocation = false; lat = null; lng = null
        scope.launch { AppContainer.settingsRepo(context).setCityIndex(idx) }
        onSettingsChange()
    }

    val timeLabels = listOf(
        "اذان صبح", "طلوع آفتاب", "اذان ظهر", "اذان عصر",
        "غروب آفتاب", "اذان مغرب", "اذان عشاء", "نیمه‌شب شرعی"
    )
    val timeValues = listOf(
        times.fajr, times.sunrise, times.dhuhr, times.asr,
        times.sunset, times.maghrib, times.isha, times.midnight
    )

    // وقت بعدی
    val nowH = LocalDateTime.now().let {
        it.hour + it.minute / 60.0
    }
    val nextIndex = timeValues.indexOfFirst { it > nowH }

    // نوار «نزدیک‌ترین وقت شرعی» مطابق طراحی کارت — نسخه ۱.۵
    val nextLabel = if (nextIndex >= 0) timeLabels[nextIndex] else "اذان صبح (فردا)"
    val nextDiffMin = if (nextIndex >= 0) {
        ((timeValues[nextIndex] - nowH) * 60).toInt()
    } else {
        (((24.0 - nowH) + times.fajr) * 60).toInt()
    }
    val nextDiffText = if (nextDiffMin >= 60)
        "${nextDiffMin / 60} ساعت و ${nextDiffMin % 60} دقیقه"
    else "$nextDiffMin دقیقه"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ---------- سرصفحه: شهر و روش ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("اوقات شرعی", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("استان و شهرستان", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (useLocation) "موقعیت فعلی شما" else "${city.name} — ${city.province}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            locateNearestCity(context) { la, lo ->
                                val (idx, near) = Cities.nearest(la, lo)
                                lat = null; lng = null; useLocation = false
                                scope.launch { AppContainer.settingsRepo(context).setCityIndex(idx) }
                                onSettingsChange()
                                android.widget.Toast.makeText(
                                    context,
                                    "نزدیک‌ترین شهر: ${near.name} — ${near.province}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }) {
                        Icon(Icons.Filled.LocationOn, contentDescription = "مکان‌یاب — نزدیک‌ترین شهر", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // استان (کشویی اول)
                SimpleDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "انتخاب استان",
                    items = provinces,
                    selectedIndex = provIdx,
                    onSelected = { pIdx ->
                        pickCity(Cities.firstIndexOfProvince(provinces[pIdx]))
                    }
                )
                Spacer(Modifier.height(8.dp))
                // شهرستان (کشویی دوم — وابسته به استان)
                SimpleDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "انتخاب شهرستان",
                    items = cityChoices.map { Cities.list[it].name },
                    selectedIndex = cityChoiceIdx,
                    onSelected = { cIdx ->
                        if (cIdx in cityChoices.indices) pickCity(cityChoices[cIdx])
                    }
                )
                Spacer(Modifier.height(6.dp))
                // جست‌وجوی سریع در میان همه شهرستان‌ها (اختیاری)
                androidx.compose.material3.TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { cityPickerVisible = true }
                ) {
                    Text("جست‌وجو در میان همه شهرها…", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                SimpleDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "روش محاسبه",
                    items = PrayerMethod.entries.map { it.label },
                    selectedIndex = PrayerMethod.entries.indexOf(settings.prayerMethod),
                    onSelected = { idx ->
                        val repo = AppContainer.settingsRepo(context)
                        scope.launch { repo.setPrayerMethod(PrayerMethod.entries[idx]) }
                        onSettingsChange()
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- نوار نزدیک‌ترین وقت شرعی (نسخه ۱.۵) ----------
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "نزدیک‌ترین وقت شرعی — ${city.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        nextLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "مانده تا شروع",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        nextDiffText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- اعلان اوقات ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("اعلان هوشمند اوقات", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "اعلان‌های انتخابی هر روز به‌طور خودکار زمان‌بندی می‌شوند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                timeLabels.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = i in settings.prayerNotifications,
                            onCheckedChange = { checked ->
                                val newSet = settings.prayerNotifications.toMutableSet()
                                if (checked) newSet.add(i) else newSet.remove(i)
                                val repo = AppContainer.settingsRepo(context)
                                scope.launch { repo.setPrayerNotifications(newSet) }
                                AlarmScheduler.schedulePrayerAlarms(
                                    context, newSet, lat, lng, settings.cityIndex, settings.prayerMethod
                                )
                                onSettingsChange()
                            }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- اذان دلخواه (نسخه ۱.۲) ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("اذان دلخواه", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "وقتی اعلان اوقات فعال است، هنگام اذان این صدا پخش می‌شود",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SimpleDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "انتخاب صدای اذان",
                    items = ir.persiancalendar.app.notifications.AdhanSounds.LABELS,
                    selectedIndex = ir.persiancalendar.app.notifications.AdhanSounds.KEYS
                        .indexOf(settings.adhanSound).let { if (it < 0) 0 else it },
                    onSelected = { idx ->
                        val key = ir.persiancalendar.app.notifications.AdhanSounds.KEYS[idx]
                        scope.launch { AppContainer.settingsRepo(context).setAdhanSound(key) }
                        onSettingsChange()
                    }
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = {
                    // پیش‌نمایش کامل: اعلان اذان + پخش صدای انتخابی (نسخه ۱.۳)
                    ir.persiancalendar.app.notifications.NotificationHelper.notifyPrayer(
                        context,
                        "اذان (نمونه)",
                        "پیش‌نمایش اعلان اذان — هنگام وقت واقعی، اعلان دقیق با AlarmManager می‌آید"
                    )
                    ir.persiancalendar.app.notifications.AdhanSounds.play(
                        context, settings.adhanSound
                    )
                }) {
                    Text("آزمایش اعلان اذان")
                }
                Text(
                    "فایل‌های اذان نمونه در res/raw قرار دارند و می‌توانید آنها را با اذان واقعی دلخواه جایگزین کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- قبله ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("جهت قبله", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val qibla = PrayerTimesCalculator.qiblaDirection(effLat, effLng)
                Text(
                    text = "از شمال جغرافیایی: ${PersianText.toPersianDigits(String.format("%.1f", qibla))} درجه ساعتگرد" +
                            " (${qiblaLabel(qibla)})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "قطب‌نمای گوشی را روی محور شمال بگیرید و در جهت ذکرشده بایستید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        // بنر از نسخه ۱.۴ به نوار پایین اسکفولد منتقل شد (محدوده تبلیغات — جای منوی قبلی)
    }

    // ---------- دیالوگ انتخاب شهر ----------
    if (cityPickerVisible) {
        CityPickerDialog(
            cities = Cities.list,
            selectedIndex = settings.cityIndex,
            onDismiss = { cityPickerVisible = false },
            onSelected = { idx ->
                useLocation = false; lat = null; lng = null
                val repo = AppContainer.settingsRepo(context)
                scope.launch { repo.setCityIndex(idx) }
                onSettingsChange()
            }
        )
    }
}

private fun qiblaLabel(deg: Double): String = when {
    deg < 22.5 || deg >= 337.5 -> "شمال"
    deg < 67.5 -> "شمال‌شرقی"
    deg < 112.5 -> "شرق"
    deg < 157.5 -> "جنوب‌شرقی"
    deg < 202.5 -> "جنوب"
    deg < 247.5 -> "جنوب‌غربی"
    deg < 292.5 -> "غرب"
    else -> "شمال‌غربی"
}

/** گرفتن مختصات فعلی از LocationManager (بدون وابستگی گوگل) — سپس نزدیک‌ترین شهر یافت می‌شود */
private fun locateNearestCity(context: Context, onResult: (Double, Double) -> Unit) {
    runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOfNotNull(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (p in providers) {
            if (ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED) {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) {
                    onResult(loc.latitude, loc.longitude)
                    return
                }
            }
        }
        // اگر موقعیت ذخیره‌شده نبود، یک بار به‌روزرسانی درخواست کن
        if (ContextCompat.checkSelfPermission(context, LocationManager.NETWORK_PROVIDER) == PackageManager.PERMISSION_GRANTED) {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, LocationListener { l ->
                onResult(l.latitude, l.longitude)
            }, null)
        }
    }
}
