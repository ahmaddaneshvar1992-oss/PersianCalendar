package ir.persiancalendar.app.ui.qibla

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.location.LocationHelper
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.ui.components.SectionCard
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262
private val NeedleRed = Color(0xFFD64545)

/** نام جهت بر اساس زاویه از شمال جغرافیایی */
private fun dirLabel(deg: Double): String = when (((deg + 22.5) % 360 / 45).toInt()) {
    0 -> "شمال"
    1 -> "شمال‌شرق"
    2 -> "شرق"
    3 -> "جنوب‌شرق"
    4 -> "جنوب"
    5 -> "جنوب‌غرب"
    6 -> "غرب"
    else -> "شمال‌غرب"
}

/**
 * قبله‌نما — نسخه ۱.۴
 *
 *  - جهت قبله با فرمول دایره عظیمه به کعبه (مختصات مکه) محاسبه می‌شود.
 *  - «مکان‌یابی با GPS»: موقعیت کاربر از LocationManager گرفته می‌شود (با اجازه کاربر)
 *    و نزدیک‌ترین شهرستان برای نمایش هم پیدا می‌شود.
 *  - صفحه قطب‌نما با حسگر Rotation Vector گوشی می‌چرخد؛ سرِ قرمز (کعبه) زمانی
 *    که گوشی رو به قبله باشد مستقیماً به بالا اشاره می‌کند.
 *  - در نبود حسگر یا اجازه موقعیت، زاویه ایستا نسبت به شمال جغرافیایی نشان داده می‌شود.
 */
@Composable
fun QiblaScreen(settings: AppSettings) {
    val context = LocalContext.current

    var heading by remember { mutableStateOf<Float?>(null) }
    var sensorOk by remember { mutableStateOf(true) }
    var gps by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsCity by remember { mutableStateOf("") }

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationVector = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    DisposableEffect(Unit) {
        if (rotationVector == null) {
            sensorOk = false
            onDispose { }
        } else {
            val rot = FloatArray(9)
            val ori = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, event.values)
                    SensorManager.getOrientation(rot, ori)
                    var deg = Math.toDegrees(ori[0].toDouble())
                    if (deg < 0) deg += 360.0
                    heading = deg.toFloat()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    val city = Cities.list[settings.cityIndex.coerceIn(0, Cities.list.lastIndex)]
    val lat = gps?.first ?: city.lat
    val lng = gps?.second ?: city.lng
    val bearing = PrayerTimesCalculator.qiblaDirection(lat, lng)
    val distance = Cities.distanceKm(lat, lng, KAABA_LAT, KAABA_LNG)

    // نشانگر «رو به قبله» — وقتی اختلاف جهت گوشی و قبله کمتر از ۵ درجه باشد (نسخه ۱.۱۱.۴)
    val aligned = heading?.let { hd ->
        abs(((bearing - hd + 540.0) % 360.0) - 180.0) <= 5.0
    } ?: false

    fun fetchGps() {
        // نسخه ۱.۱۱.۴ — رفع باگ «GPS پیدا نمی‌کند»:
        // همه providerها (GPS+شبکه) با هم، آخرین موقعیت شناخته‌شده، پیام خطای روشن و تایم‌اوت
        Toast.makeText(context, "در حال یافتن موقعیت…", Toast.LENGTH_SHORT).show()
        LocationHelper.fetchOnce(
            context,
            onResult = { la, lo ->
                gps = la to lo
                gpsCity = Cities.nearest(la, lo).second.name
                Toast.makeText(context, "موقعیت یافت شد: " + gpsCity, Toast.LENGTH_SHORT).show()
            },
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) fetchGps()
        else Toast.makeText(context, "دسترسی به موقعیت داده نشد", Toast.LENGTH_SHORT).show()
    }

    fun locate() {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchGps()
        else permLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text("قبله‌نما", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        SectionCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val primary = MaterialTheme.colorScheme.primary
                val onPrimary = MaterialTheme.colorScheme.onPrimary
                Canvas(modifier = Modifier.size(236.dp)) {
                    drawCompass(
                        heading = heading,
                        bearing = bearing.toFloat(),
                        primary = primary,
                        onPrimary = onPrimary,
                        aligned = aligned
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = PersianText.toPersianDigits(bearing.roundToInt().toString()) + "°",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (aligned) {
                    // نشانگر سبز هم‌راستایی (نسخه ۱.۱۱.۴)
                    Text(
                        "رو به قبله ✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1EA84D)
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (heading != null)
                        "گوشی را صاف نگه دارید و بچرخانید تا کعبه روی نشانگر سبز بیفتد — جهت قبله: " + dirLabel(bearing)
                    else
                        "از " + (gps?.let { gpsCity } ?: city.name) +
                            " حدوداً به سمت " + dirLabel(bearing) +
                            " — زاویه از شمال جغرافیایی (ساعتگرد)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { locate() }, modifier = Modifier.weight(1f)) {
                Text("مکان‌یابی با GPS")
            }
            OutlinedButton(
                onClick = {
                    gps = null
                    gpsCity = ""
                    Toast.makeText(
                        context,
                        "محاسبه بر اساس شهر اوقات شرعی: " + city.name,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("شهر اوقات شرعی") }
        }

        Spacer(Modifier.height(10.dp))

        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "زاویه از شمال جغرافیایی: " + dirLabel(bearing) + " — " +
                        PersianText.toPersianDigits(bearing.roundToInt().toString()) + " درجه ساعتگرد",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "فاصله تا کعبه: حدود " + PersianText.toPersianDigits(
                        String.format("%,d", distance.roundToInt()).replace(',', '٬')
                    ) + " کیلومتر",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "موقعیت مبنا: " + (if (gps != null) "GPS — " else "") +
                        (gps?.let { gpsCity } ?: city.name),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "حسگر قطب‌نما: " + if (sensorOk && heading != null) "فعال ✓"
                    else if (!sensorOk) "روی این دستگاه موجود نیست" else "در انتظار داده",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "نکته: برای دقت بیشتر گوشی را صاف نگه دارید و چند بار به شکل ۸ بچرخانید تا حسگر کالیبره شود. " +
                        "در نسخه‌های جدیدتر اندروید اجازه موقعیت فقط هنگام استفاده پرسیده می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** رسم صفحه قطب‌نما: حلقه + تیک‌ها + برچسب‌های جهات + سوزن قبله با گرافیک کعبه (نسخه ۱.۱۱.۴) */
private fun DrawScope.drawCompass(
    heading: Float?,
    bearing: Float,
    primary: Color,
    onPrimary: Color,
    aligned: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.width / 2f - 8f
    val outline = Color(0xFF9AA79D)

    drawCircle(color = outline, radius = r, style = Stroke(width = 3f))
    drawCircle(color = outline.copy(alpha = 0.4f), radius = r * 0.82f, style = Stroke(width = 1.5f))

    for (i in 0 until 24) {
        val a = Math.toRadians((i * 15).toDouble())
        val len = if (i % 6 == 0) 14f else 7f
        drawLine(
            color = outline,
            start = Offset((cx + (r - 2f) * sin(a)).toFloat(), (cy - (r - 2f) * cos(a)).toFloat()),
            end = Offset((cx + (r - 2f - len) * sin(a)).toFloat(), (cy - (r - 2f - len) * cos(a)).toFloat()),
            strokeWidth = if (i % 6 == 0) 4f else 2f
        )
    }

    // صفحه با جهت گوشی می‌چرخد — سرِ قرمز زمانی به بالا اشاره می‌کند که گوشی رو به قبله باشد
    rotate(degrees = -(heading ?: 0f), pivot = Offset(cx, cy)) {
        val nc = drawContext.canvas.nativeCanvas
        val redPaint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            textSize = 14.sp.toPx()
            color = android.graphics.Color.parseColor("#D64545")
        }
        val grayPaint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            textSize = 12.sp.toPx()
            color = android.graphics.Color.parseColor("#77837A")
        }

        nc.drawText("شمال", cx, cy - r + 34f, redPaint)
        nc.drawText("شرق", cx + r - 34f, cy + 5f, grayPaint)
        nc.drawText("جنوب", cx, cy + r - 24f, grayPaint)
        nc.drawText("غرب", cx - r + 34f, cy + 5f, grayPaint)

        rotate(degrees = bearing, pivot = Offset(cx, cy)) {
            val tipY = cy - r * 0.66f
            // سوزن — هم‌راستا با قبله سبز می‌شود (نسخه ۱.۱۱.۴)
            val needle = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx - 9.dp.toPx(), cy)
                lineTo(cx + 9.dp.toPx(), cy)
                close()
            }
            drawPath(needle, if (aligned) Color(0xFF1EA84D) else NeedleRed, style = Fill)
            // گرافیک کعبه روی نوک سوزن — مکّه (نسخه ۱.۱۱.۴: کعبه بزرگ‌تر و خواناتر)
            drawKaaba(cx = cx, tipY = tipY, w = 24.dp.toPx())
        }
    }

    // مرکز صفحه
    drawCircle(color = primary, radius = 9.dp.toPx(), style = Fill)
    drawCircle(color = onPrimary, radius = 3.5.dp.toPx(), style = Fill)
}

/**
 * گرافیک کعبه — بدنه مشکی، حزام و در طلایی (نسخه ۱.۱۱.۴)
 * روی نوک سوزن قبله رسم می‌شود و با صفحه قطب‌نما می‌چرخد؛
 * وقتی کعبه به بالای صفحه و نشانگر سبز برسد، گوشی رو به قبله است.
 */
private fun DrawScope.drawKaaba(cx: Float, tipY: Float, w: Float) {
    val h = w * 0.78f
    val left = cx - w / 2f
    val top = tipY - h
    // بدنه
    drawRoundRect(
        color = Color(0xFF1B1410),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(2.5f, 2.5f)
    )
    // حزام طلایی
    drawRect(
        color = Color(0xFFC9A227),
        topLeft = Offset(left, top + h * 0.38f),
        size = Size(w, h * 0.2f)
    )
    // در طلایی
    drawRect(
        color = Color(0xFFC9A227),
        topLeft = Offset(cx - w * 0.14f, tipY - h * 0.3f),
        size = Size(w * 0.28f, h * 0.3f)
    )
    // قاب دور برای دیده‌شدن روی پس‌زمینه روشن
    drawRoundRect(
        color = Color(0xFF8A7A55),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(2.5f, 2.5f),
        style = Stroke(width = 1.6f)
    )
}
