package ir.persiancalendar.app.notifications

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.OngoingDateNotifier.todayText
import ir.persiancalendar.app.ui.theme.PersianCalendarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * صفحهٔ تمام‌صفحهٔ اذان — نسخه ۱.۱۱.۱۵
 *
 * درخواست کاربر: «می‌خوام وقتی اذان می‌گه نوفیکیشن با یه عکس تمام‌صفحه باشه
 * و اذان پخش بشه و وقتی برگشت می‌زنه اذان قطع بشه».
 *
 * اعلان اوقات شرعی هنگام رسیدن وقت، از fullScreenIntent استفاده می‌کند؛ اگر
 * صفحه خاموش یا قفل باشد این اکتیویتی تمام‌صفحه بالای قفل باز می‌شود
 * (showWhenLocked/turnScreenOn) و اگر کاربر مشغول کار باشد به‌صورت سرصفحهٔ
 * برجسته می‌آید و با لمسش باز می‌شود.
 *
 * صحنه: آسمان شب سبز تیره با ستاره‌ها، ماه و تصویر مسجد (گنبد و دو مناره با
 * پنجره‌های درخشان) — همه با Canvas رسم می‌شود؛ هم‌سبک گرافیک کعبه در قبله‌نما.
 *
 * توقف: دکمهٔ «توقف اذان» یا دکمهٔ بازگشت گوشی — هر دو صدا را قطع می‌کنند و
 * صفحه بسته می‌شود. وقتی پخش اذان خودش تمام شود هم صفحه خودکار بسته می‌شود.
 */
class AzanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // نمایش بالای قفل صفحه و روشن کردن نمایشگر (اندروید ۸.۱ به بالا؛
        // روی نسخه‌های قدیمی‌تر ویژگی نادیده گرفته می‌شود و اعلان عادی می‌ماند)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "اذان"

        setContent {
            PersianCalendarTheme() {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        androidx.compose.ui.unit.LayoutDirection.Rtl
                ) {
                    AzanScreen(label = label)
                }
            }
        }
    }

    /** توقف اذان + بستن صفحه — از دکمه و بازگشت */
    fun stopAndFinish() {
        AzanPlaybackService.stop(this)
        AdhanSounds.release()
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "azan_label"
    }
}

@Composable
private fun AzanScreen(label: String) {
    val context = LocalContext.current
    val activity = context as? AzanActivity

    // ساعت زندهٔ صفحه — هر ثانیه تازه می‌شود
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = LocalTime.now()
            delay(1000)
        }
    }

    // پایان خودکار: پس از مهلت ۴.۵ ثانیه‌ایِ شروع پخش، هر وقت اذان تمام شد
    // (یا پخشی در کار نبود) صفحه خودکار بسته می‌شود
    LaunchedEffect(Unit) {
        delay(4500)
        while (isActive && AzanPlaybackService.isPlaying) delay(500)
        activity?.finish()
    }

    BackHandler {
        activity?.stopAndFinish()
    }

    // پالس «در حال پخش اذان…»
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val cityName = remember {
        runCatching {
            val s: AppSettings = AppContainer.settingsRepo(context).settingsBlocking()
            Cities.list[s.cityIndex.coerceIn(0, Cities.list.size - 1)].name
        }.getOrDefault("")
    }

    // ستاره‌های ثابت آسمان — تصادفیِ با seed تا هر بار جابه‌جا نشوند
    val stars = remember {
        val rnd = Random(1405)
        List(46) {
            Triple(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.15f + rnd.nextFloat() * 0.65f)
        }
    }

    Surface(color = Color(0xFF04241B)) {
        Box(Modifier.fillMaxSize()) {
            // ---------- صحنهٔ شب ----------
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // آسمان: گرادیان سبز تیره (۲۵٪ بالایی یکدست برای ماه بی‌درز)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF04241B),
                        0.25f to Color(0xFF04241B),
                        0.72f to Color(0xFF0A3D2C),
                        1f to Color(0xFF062A20)
                    )
                )

                // ستاره‌ها
                stars.forEach { (fx, fy, fa) ->
                    drawCircle(
                        Color.White.copy(alpha = fa * 0.8f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(fx * w, fy * h)
                    )
                }

                // ماه درخشان (بالای صفحه، سمت چپ در RTL)
                val moonC = Offset(w * 0.22f, h * 0.16f)
                val moonR = 24.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFF3C4).copy(alpha = 0.35f), Color.Transparent),
                        center = moonC, radius = moonR * 3.2f
                    ),
                    radius = moonR * 3.2f, center = moonC
                )
                drawCircle(Color(0xFFFFF6D8), radius = moonR, center = moonC)
                // هلال: دایرهٔ برش با رنگ همان نوارِ یکدست آسمان
                drawCircle(
                    Color(0xFF04241B),
                    radius = moonR * 0.86f,
                    center = moonC + Offset(moonR * 0.42f, -moonR * 0.16f)
                )

                // ---------- تصویر مسجد ----------
                val sil = Color(0xFF031711)
                val cx = w / 2f
                val baseY = h * 0.80f

                // سکوی مسجد
                drawRect(sil, topLeft = Offset(0f, baseY), size = Size(w, h - baseY))

                // گنبد اصلی + بدنه
                drawCircle(sil, radius = w * 0.155f, center = Offset(cx, baseY - h * 0.085f))
                drawRect(
                    sil,
                    topLeft = Offset(cx - w * 0.15f, baseY - h * 0.085f),
                    size = Size(w * 0.30f, h * 0.085f)
                )
                // نوک گنبد با ماه طلایی
                drawRect(
                    sil,
                    topLeft = Offset(cx - 2.dp.toPx(), baseY - h * 0.085f - w * 0.155f - 10.dp.toPx()),
                    size = Size(4.dp.toPx(), 12.dp.toPx())
                )
                drawCircle(
                    Color(0xFFE8B540),
                    radius = 3.dp.toPx(),
                    center = Offset(cx, baseY - h * 0.085f - w * 0.155f - 13.dp.toPx())
                )

                // دو گنبد کوچک طرفین
                listOf(-1f, 1f).forEach { dir ->
                    val dx = cx + dir * w * 0.27f
                    drawCircle(sil, radius = w * 0.07f, center = Offset(dx, baseY - h * 0.055f))
                    drawRect(
                        sil,
                        topLeft = Offset(dx - w * 0.065f, baseY - h * 0.055f),
                        size = Size(w * 0.13f, h * 0.055f)
                    )
                }

                // دو مناره
                val mw = 13.dp.toPx()
                listOf(cx - w * 0.40f, cx + w * 0.40f - mw).forEach { x ->
                    drawRect(sil, topLeft = Offset(x, baseY - h * 0.30f), size = Size(mw, h * 0.30f))
                    drawCircle(sil, radius = mw * 0.95f, center = Offset(x + mw / 2f, baseY - h * 0.30f))
                    drawCircle(
                        Color(0xFFE8B540),
                        radius = 2.2f.dp.toPx(),
                        center = Offset(x + mw / 2f, baseY - h * 0.30f - mw * 1.5f)
                    )
                }

                // پنجره‌ها و درِ درخشان مسجد
                val glow = Color(0xFFFFC35C).copy(alpha = 0.55f)
                drawRoundRect(
                    glow,
                    topLeft = Offset(cx - 9.dp.toPx(), baseY - 26.dp.toPx()),
                    size = Size(18.dp.toPx(), 26.dp.toPx()),
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                )
                listOf(-1f, 1f).forEach { dir ->
                    drawRoundRect(
                        glow.copy(alpha = 0.4f),
                        topLeft = Offset(cx + dir * w * 0.10f - 5.dp.toPx(), baseY - 18.dp.toPx()),
                        size = Size(10.dp.toPx(), 18.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    drawRoundRect(
                        glow.copy(alpha = 0.35f),
                        topLeft = Offset(cx + dir * w * 0.27f - 4.dp.toPx(), baseY - 15.dp.toPx()),
                        size = Size(8.dp.toPx(), 15.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            // ---------- متن‌ها ----------
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                if (cityName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cityName,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    todayText(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    PersianText.toPersianDigits(clock.format(DateTimeFormatter.ofPattern("HH:mm"))),
                    color = Color(0xFFE8B540),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ---------- توقف ----------
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "در حال پخش اذان…",
                    color = Color.White.copy(alpha = pulse.value),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { activity?.stopAndFinish() },
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE8B540),
                        contentColor = Color(0xFF062A20)
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Filled.MusicOff, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "توقف اذان",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "برای قطع صدا، دکمهٔ بازگشت گوشی را هم بزنید",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
