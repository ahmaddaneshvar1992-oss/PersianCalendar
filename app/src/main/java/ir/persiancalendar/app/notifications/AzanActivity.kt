package ir.persiancalendar.app.notifications

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import ir.persiancalendar.app.MainActivity
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.OngoingDateNotifier.todayText
import ir.persiancalendar.app.ui.theme.PersianCalendarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * صفحهٔ تمام‌صفحهٔ اذان به سبک صفحهٔ «اوقات شرعی» — نسخهٔ ۱.۱۱.۳۳
 *
 * درخواست کاربر: «وقتی وقت اذان می‌رسد و اذان شروع به پخش شدن می‌کند، صفحهٔ
 * اوقات شرعی باید خودکار و هم‌زمان با شروع اذان به‌صورت تمام‌صفحه بالا بیاید،
 * حتی وقتی اپ بسته است، گوشی قفل است یا صفحه خاموش است.»
 *
 * ساختار صفحه (مطابق نمونهٔ ارسالی کاربر، با فونت و منابع همین برنامه):
 *  - نوار بالا: بازگشت، عنوان «اوقات شرعی <شهر>»، آیکون پیام/تنظیمات/منو
 *  - بخش آسمان: خورشید، ابر و نقش اسلیمی + برچسب «اذان ظهر به افق <شهر>»
 *    (نام وعده از وعدهٔ در حال اذان پر می‌شود — PrayerSlot اعتبارسنجی‌شده)
 *  - نوار سبز موج‌دار: هر ۸ وقت با برچسب کوتاه؛ وعدهٔ در حال اذان پررنگ و بولد
 *  - تاریخ روز + ابزارهای کاربردی (اوقات شرعی/قبله‌نما/یادآورها/رویدادها)
 *  - ذکر «یا ذالجلال والاکرام» با دکمهٔ سبز پخش/توقف (وضعیت واقعی پخش)
 *  - دکمهٔ واضح «توقف اذان» در حالت پخش
 *
 * رفتار: بازگشت/خروج از صفحه اذان را قطع نمی‌کند (مگر تنظیم «توقف اذان با
 * خروج از صفحه»)؛ با پایان خودکار اذان صفحه بسته می‌شود. روی قفل صفحه و
 * بالای برنامه‌های دیگر (fullScreenIntent) نمایش داده می‌شود.
 */
class AzanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // نمایش بالای قفل صفحه و روشن کردن نمایشگر (API ۲۷ به بالا؛
        // پایین‌تر از آن با flagهای پنجره — الزام پروپوزال)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // تا وقتی صفحهٔ اذان جلوی چشم است، نمایشگر خاموش نشود (مثل ساعت‌های زنگ‌دار)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // نسخهٔ ۱.۱۱.۴۳ (گزارش کاربر: «موقع پخش اذان صدای اذان با کم کردن ولوم
        // گوشی کم نمیشه») — صدای اذان روی استریم «هشدار» (USAGE_ALARM) پخش
        // می‌شود؛ تا وقتی صفحهٔ اذان جلوی چشم است، دکمه‌های ولوم گوشی همان
        // استریم را کم/زیاد کنند (override صریح onKeyDown هم زیرش هست تا روی
        // ROMهایی که volumeControlStream را نادیده می‌گیرند هم کار کند)
        volumeControlStream = AudioManager.STREAM_ALARM

        // extraها فقط از enum اعتبارسنجی می‌شوند (الزام امنیتی)
        val slot = PrayerSlot.fromIndex(intent?.getIntExtra(EXTRA_PRAYER_INDEX, -1) ?: -1)
        val fromAlarm = intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) == true
        // سازگاری با نسخهٔ قبل: برچسب متنی قدیمی (فقط برای نمایش)
        val legacyLabel = intent?.getStringExtra(EXTRA_LABEL)

        setContent {
            PersianCalendarTheme() {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AzanScreen(
                        slot = slot,
                        legacyLabel = legacyLabel,
                        fromAlarm = fromAlarm
                    )
                }
            }
        }
    }

    /**
     * نسخهٔ ۱.۱۱.۴۳ — دکمه‌های ولوم هنگام باز بودن صفحهٔ اذان، مستقیم ولوم
     * استریم «هشدار» (STREAM_ALARM) را کم/زیاد می‌کنند؛ FLAG_SHOW_UI اسلایدر
     * ولوم سیستم را هم روی همان استریم نشان می‌دهد. (volumeControlStream در
     * onCreate هم ست شده — این override صریح تضمین رفتار یکسان روی همهٔ
     * گوشی‌هاست.)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                adjustAzanVolume(raise = true)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                adjustAzanVolume(raise = false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun adjustAzanVolume(raise: Boolean) = runCatching {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_ALARM,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }.getOrDefault(Unit)

    /** توقف کامل (دکمهٔ «توقف اذان» / دکمهٔ سبز) — صدا + اعلان + صفحه */
    fun stopAzan() {
        AzanPlaybackService.stop(this)
        AdhanSounds.release()
        NotificationHelper.cancelPrayer(this)
    }

    /** خروج از صفحه — طبق تنظیم «توقف اذان با خروج از صفحه» */
    fun exitAccordingToSettings(stopOnExit: Boolean) {
        if (stopOnExit) stopAzan()
        finish()
    }

    companion object {
        const val EXTRA_PRAYER_INDEX = "azan_prayer_index"
        const val EXTRA_FROM_ALARM = "azan_from_alarm"

        /** برچسب متنی قدیمی (نسخهٔ ۱.۱۱.۱۵) — فقط برای سازگاری Intentهای قبلی */
        const val EXTRA_LABEL = "azan_label"
    }
}

// ---------------- پالت صفحه (برگرفته از نمونهٔ ارسالی کاربر) ----------------
private val TopBarBlue = Color(0xFF4C93D4)
private val SkyTop = Color(0xFF5FB2E2)
private val SkyBottom = Color(0xFF9ED8F2)
private val PillBlue = Color(0xFF2F80BE)
private val PillText = Color(0xFF0B7A62)
private val WaveGreen = Color(0xFF2FA268)
private val ActiveTime = Color(0xFF053E27)
private val DateBarGray = Color(0xFFEFEFEF)
private val CardGray = Color(0xFFF4F5F6)
private val ZikrGreen = Color(0xFF2E9E68)
private val ZikrDark = Color(0xFF143D28)

@Composable
private fun AzanScreen(slot: PrayerSlot?, legacyLabel: String?, fromAlarm: Boolean) {
    val context = LocalContext.current
    val activity = context as? AzanActivity

    // ---------- تنظیمات و شهر (خواندن یک‌باره در پس‌زمینه) ----------
    var settings by remember { mutableStateOf<AppSettings?>(null) }
    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) {
            runCatching { AppContainer.settingsRepo(context).settingsBlocking() }.getOrNull()
        }
    }
    val s = settings
    val city = Cities.list[(s?.cityIndex ?: Cities.DEFAULT_INDEX)
        .coerceIn(0, Cities.list.size - 1)]
    val times = remember(s) {
        runCatching {
            PrayerTimesCalculator.compute(
                LocalDate.now(), city.lat, city.lng, Cities.IRAN_TZ_HOURS,
                s?.prayerMethod ?: ir.persiancalendar.app.core.prayer.PrayerMethod.TEHRAN
            )
        }.getOrNull()
    }
    val timeValues = times?.let {
        listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.sunset, it.maghrib, it.isha, it.midnight)
    }
    // وعدهٔ هایلایت: وعدهٔ در حال اذان؛ اگر صفحه بیرون از اذان باز شده باشد، نزدیک‌ترین وقت
    val activeIndex = slot?.ordinal ?: run {
        val nowH = LocalTime.now().let { it.hour + it.minute / 60.0 }
        (timeValues ?: listOf<Double>()).indexOfFirst { it > nowH }.takeIf { it >= 0 } ?: 0
    }
    val titleText = slot?.skyLabel(city.name)
        ?: legacyLabel?.takeIf { it.isNotBlank() }?.let { "$it به افق ${city.name}" }
        ?: "اذان به افق ${city.name}"

    // ---------- وضعیت پخش — هر نیم ثانیه تازه می‌شود ----------
    var playing by remember { mutableStateOf(AzanPlaybackService.isPlaying) }
    LaunchedEffect(Unit) {
        while (isActive) {
            playing = AzanPlaybackService.isPlaying
            delay(500)
        }
    }

    // ساعت زندهٔ صفحه
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = LocalTime.now()
            delay(1000)
        }
    }

    // پایان خودکار: صفحه‌ای که با اذان باز شده، پس از پایان پخش بسته می‌شود
    LaunchedEffect(fromAlarm) {
        if (!fromAlarm) return@LaunchedEffect
        delay(5000) // مهلت شروع پخش
        while (isActive && AzanPlaybackService.isPlaying) delay(500)
        delay(1500)
        activity?.finish()
    }

    BackHandler {
        // نسخهٔ ۱.۱۱.۳۳: خروج از صفحه اذان را قطع نمی‌کند (مگر تنظیم کاربر)
        activity?.exitAccordingToSettings(s?.azanStopOnExit ?: false)
    }

    // پالس «در حال پخش اذان…»
    val pulse = rememberInfiniteTransitionPulse()

    Surface(color = Color.White) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ---------- نوار بالا ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(TopBarBlue)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    activity?.exitAccordingToSettings(s?.azanStopOnExit ?: false)
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = Color.White
                    )
                }
                Text(
                    "اوقات شرعی ${city.name}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // آیکون پیام (نمونهٔ کاربر: پاکت با نشان قرمز)
                Box {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = "پیام‌ها",
                        tint = Color.White
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5393B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("۱", color = Color.White, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "تنظیمات",
                    tint = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "منو",
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
            }

            // ---------- بخش آسمان (خورشید، ابر، اسلیمی) ----------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
            ) {
                AzanSkyCanvas(Modifier.fillMaxSize())
                // برچسب «اذان ظهر به افق <شهر>»
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PillBlue.copy(alpha = 0.42f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 104.dp)
                ) {
                    Text(
                        titleText,
                        color = PillText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            // ---------- نوار سبز موج‌دار اوقات ----------
            Box(Modifier.fillMaxWidth().height(118.dp)) {
                AzanWaveCanvas(Modifier.fillMaxSize())
                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 22.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrayerSlot.entries.forEachIndexed { i, sl ->
                        val active = i == activeIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(92.dp)
                                .zIndex(1f)
                        ) {
                            Text(
                                sl.short,
                                fontSize = 12.sp,
                                color = if (active) ActiveTime else Color.White,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                timeValues?.getOrNull(i)?.let { formatHours(it) } ?: "—",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) ActiveTime else Color.White
                            )
                        }
                        if (i < PrayerSlot.entries.size - 1) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }

            // ---------- تاریخ روز ----------
            Surface(color = DateBarGray, modifier = Modifier.fillMaxWidth()) {
                Text(
                    todayText(),
                    fontSize = 15.sp,
                    color = Color(0xFF3C4043),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }

            // ---------- ابزارهای کاربردی ----------
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    "ابزارهای کاربردی",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF202124)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AzanToolCard("اوقات شرعی", Icons.Filled.EventNote, Modifier.weight(1f)) {
                        openTab(context, OngoingDateNotifier.TAB_PRAYER)
                    }
                    AzanToolCard("قبله‌نما", Icons.Filled.Explore, Modifier.weight(1f)) {
                        openTab(context, OngoingDateNotifier.TAB_QIBLA)
                    }
                    AzanToolCard("یادآورها", Icons.Filled.Notifications, Modifier.weight(1f)) {
                        openTab(context, OngoingDateNotifier.TAB_REMINDERS)
                    }
                    AzanToolCard("رویدادها", Icons.Filled.CalendarMonth, Modifier.weight(1f)) {
                        openTab(context, OngoingDateNotifier.TAB_EVENTS)
                    }
                }
            }

            // ---------- ذکر «یا ذالجلال والاکرام» + دکمهٔ سبز ----------
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                AzanSlimiBackdrop(Modifier.fillMaxSize())
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // دکمهٔ سبز پخش/توقف — وضعیت واقعی پخش اذان
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ZikrGreen)
                            .clickable {
                                if (playing) {
                                    activity?.stopAzan()
                                } else {
                                    val key = s?.adhanSound ?: "none"
                                    if (AdhanSounds.resIdFor(key) != null) {
                                        AzanPlaybackService.start(context, key, slot?.ordinal ?: -1)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            contentDescription = if (playing) "توقف اذان" else "پخش اذان",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "یا ذالجلال والاکرام",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZikrDark
                        )
                        Text(
                            "ای صاحب جلال و اکرام",
                            fontSize = 12.sp,
                            color = Color(0xFF8A8A8A)
                        )
                    }
                }
            }

            // ---------- توقف اذان + وضعیت ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (playing) {
                    Text(
                        "در حال پخش اذان…",
                        color = WaveGreen.copy(alpha = pulse),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            activity?.stopAzan()
                            activity?.finish()
                        },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E8E5A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("توقف اذان", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اگر صفحه را ببندید، اذان تا پایان ادامه می‌یابد",
                        fontSize = 12.sp,
                        color = Color(0xFF9AA0A6)
                    )
                } else if (fromAlarm) {
                    Text(
                        "اذان در حال آماده‌سازی است یا پایان یافته",
                        fontSize = 13.sp,
                        color = Color(0xFF9AA0A6)
                    )
                }
                if (slot != null && !slot.isAdhan) {
                    Text(
                        "این وقت اذان نیست؛ نوار سبز همهٔ اوقات امروز را نشان می‌دهد",
                        fontSize = 12.sp,
                        color = Color(0xFF9AA0A6),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    PersianText.toPersianDigits(clock.format(DateTimeFormatter.ofPattern("HH:mm"))),
                    fontSize = 13.sp,
                    color = Color(0xFFB0B5BA)
                )
            }
        }
    }
}

// ---------------- ابزارهای کمکی UI ----------------

/** باز کردن یک بخش از برنامه (ابزارها) — برنامه در همان‌جا باز می‌شود */
private fun openTab(context: android.content.Context, tab: String) {
    runCatching {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(OngoingDateNotifier.EXTRA_OPEN_TAB, tab)
            }
        )
    }
}

/** ساعت «۱۱:۵۴» با ارقام فارسی */
private fun formatHours(v: Double): String {
    val h = v.toInt().coerceIn(0, 23)
    val m = ((v - h) * 60).roundToInt().let { if (it >= 60) 59 else it }
    return PersianText.toPersianDigits(String.format("%02d:%02d", h, m))
}

/** پالس درخشش متن وضعیت پخش */
@Composable
private fun rememberInfiniteTransitionPulse(): Float =
    rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    ).value

/**
 * بخش آسمان: گرادیان آبی، نقش اسلیمی کم‌رنگ، خورشید با هاله، دو ابر
 */
@Composable
private fun AzanSkyCanvas(modifier: Modifier = Modifier) {
    // ستاره‌ها نه — صفحهٔ روز؛ نقاط تصادفی اسلیمی با seed ثابت
    val petals = remember {
        val rnd = Random(1405)
        List(8) { i -> Triple(i, 0.55f + rnd.nextFloat() * 0.1f, 0.5f) }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // آسمان
        drawRect(Brush.verticalGradient(0f to SkyTop, 1f to SkyBottom))

        // ---------- نقش اسلیمی (گل هشت‌پر کم‌رنگ پشت خورشید) ----------
        val cx = w * 0.5f
        val cy = h * 0.34f
        val rosette = Color.White.copy(alpha = 0.16f)
        petals.forEach { (i, _, _) ->
            val angle = (i * 45f) * Math.PI.toFloat() / 180f
            val px = cx + angle.let { kotlin.math.cos(it) } * w * 0.13f
            val py = cy + kotlin.math.sin(angle) * w * 0.13f
            drawCircle(rosette, radius = w * 0.085f, center = Offset(px, py))
        }
        drawCircle(rosette, radius = w * 0.12f, center = Offset(cx, cy))

        // ---------- خورشید ----------
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF6C9).copy(alpha = 0.85f), Color.Transparent),
                center = Offset(cx, cy), radius = w * 0.22f
            ),
            radius = w * 0.22f, center = Offset(cx, cy)
        )
        drawCircle(Color(0xFFF7DF56), radius = w * 0.085f, center = Offset(cx, cy))

        // ---------- ابرها ----------
        fun cloud(x: Float, y: Float, scale: Float) {
            val c = Color.White.copy(alpha = 0.95f)
            drawCircle(c, radius = 16.dp.toPx() * scale, center = Offset(x, y))
            drawCircle(c, radius = 22.dp.toPx() * scale, center = Offset(x + 20.dp.toPx() * scale, y - 8.dp.toPx() * scale))
            drawCircle(c, radius = 15.dp.toPx() * scale, center = Offset(x + 40.dp.toPx() * scale, y))
            drawRoundRect(
                c,
                topLeft = Offset(x - 16.dp.toPx() * scale, y),
                size = Size(58.dp.toPx() * scale, 15.dp.toPx() * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
            )
        }
        cloud(w * 0.06f, h * 0.62f, 1.0f)
        cloud(w * 0.72f, h * 0.70f, 0.85f)
        cloud(w * 0.55f, h * 0.12f, 0.6f)
    }
}

/** نوار سبز با لبهٔ موجی بالا */
@Composable
private fun AzanWaveCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.30f)
            cubicTo(w * 0.25f, h * 0.06f, w * 0.4f, h * 0.5f, w * 0.6f, h * 0.28f)
            cubicTo(w * 0.78f, h * 0.12f, w * 0.9f, h * 0.34f, w, h * 0.22f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, WaveGreen)
    }
}

/** پس‌زمینهٔ کم‌رنگ اسلیمی پشت ذکر */
@Composable
private fun AzanSlimiBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val faint = Color(0xFF2E9E68).copy(alpha = 0.10f)
        val cx = w * 0.5f
        val cy = h * 0.5f
        repeat(8) { i ->
            val angle = (i * 45f) * Math.PI.toFloat() / 180f
            drawCircle(
                faint,
                radius = w * 0.06f,
                center = Offset(
                    cx + kotlin.math.cos(angle) * w * 0.17f,
                    cy + kotlin.math.sin(angle) * w * 0.17f
                )
            )
        }
        drawCircle(faint, radius = w * 0.10f, center = Offset(cx, cy))
    }
}

/** کارت ابزار — هم‌سبک کارت‌های خاکستری نمونهٔ کاربر */
@Composable
private fun AzanToolCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardGray,
        modifier = modifier
            .aspectRatio(0.92f)
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(icon, contentDescription = label, tint = Color(0xFF5F6368), modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.5.sp, color = Color(0xFF3C4043), textAlign = TextAlign.Center)
        }
    }
}
