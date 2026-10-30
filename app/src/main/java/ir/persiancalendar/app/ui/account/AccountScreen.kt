package ir.persiancalendar.app.ui.account

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.billing.BillingManager
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.auth.AuthResult
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * صفحه حساب کاربری و عضویت ویژه:
 * - ثبت‌نام/ورود کاربر جدید (محلی، آفلاین، پسورد هش‌شده)
 * - ارتقا به عضویت ویژه: خرید درون‌برنامه‌ای (گوگل‌پلی)
 * - نسخه ۱.۱۱.۵: باز شدن صفحه روی همه گوشی‌ها بدون خروج از برنامه (هاردنینگ Billing)
 */
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AppContainer.accountRepo(context)

    val user by repo.sessionUser().collectAsState(initial = null)
    val isPremium by repo.isPremium.collectAsState(initial = false)
    val premiumSource by repo.devicePremiumSource.collectAsState(initial = "")

    var mode by remember { mutableStateOf("register") } // register | login
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // اتصال به فروشگاه فقط در Application انجام می‌شود (نسخه ۱.۱۱.۵ — رفع خروج برنامه
    // هنگام باز کردن صفحه حساب: هر خطای Billing نباید در نخ UI منتشر شود)
    LaunchedEffect(Unit) {
        runCatching {
            BillingManager.setOnPremiumGranted {
                Handler(Looper.getMainLooper()).post {
                    toast("عضویت ویژه شما فعال شد — سپاس از حمایت شما!")
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
        // ---------- سرصفحه ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "بازگشت")
            }
            Text(
                "حساب کاربری و عضویت ویژه",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(10.dp))

        // ============================================================
        if (user == null) {
            // ---------- فرم ثبت‌نام / ورود ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { mode = "register" }) {
                            Text(
                                "ثبت‌نام",
                                color = if (mode == "register") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { mode = "login" }) {
                            Text(
                                "ورود",
                                color = if (mode == "login") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (mode == "register")
                            "حساب بسازید تا وضعیت عضویت و تنظیمات شما روی همین دستگاه ذخیره شود (کاملاً آفلاین — داده‌ها از گوشی شما خارج نمی‌شوند)."
                        else
                            "با شماره/ایمیل و رمزی که هنگام ثبت‌نام ساختید وارد شوید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (mode == "register") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("نام و نام خانوادگی") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("شماره موبایل یا ایمیل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز عبور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                val result = if (mode == "register")
                                    repo.register(name, username, password)
                                else repo.login(username, password)
                                withContext(Dispatchers.Main) {
                                    busy = false
                                    when (result) {
                                        is AuthResult.Success -> toast(
                                            if (mode == "register") "خوش آمدید ${result.user.name}!"
                                            else "خوش آمدید ${result.user.name}!"
                                        )
                                        is AuthResult.Error -> toast(result.message)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (mode == "register") "ایجاد حساب" else "ورود")
                    }
                }
            }
        } else {
            val u = user!! // مرجع ثابت برای جلوگیری از مشکل smart cast روی property delegate
            // ---------- پروفایل ----------
            SectionCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            u.name.trim().take(1),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            u.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "عضو از " + formatJalaliDate(u.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- عضویت ویژه ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (isPremium) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("عضویت ویژه", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (isPremium) {
                            Text(
                                when (premiumSource) {
                                    "billing" -> "فعال — خرید فروشگاهی"
                                    // "code": عضویت‌های قدیمی فعال‌شده با کد — از ۱.۱۱.۲۹ مسیر کد حذف شده ولی وضعیت حفظ می‌شود
                                    "code" -> "فعال"
                                    else -> "فعال"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (isPremium) {
                        Text(
                            "تشکر از حمایت شما! وضعیت ویژه شما روی همین دستگاه و حساب‌تان ثبت شده است.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "با ارتقا به عضویت ویژه از این مزایا بهره‌مند شوید:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "• پشتیبانی سریع‌تر از توسعه‌دهنده\n• حمایت از توسعه بادخزان\n• اولویت دریافت امکانات جدید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        // خرید درون‌برنامه‌ای (گوگل‌پلی)
                        Button(
                            onClick = {
                                val activity = context as? Activity
                                if (activity == null || !BillingManager.launchPremiumPurchase(activity)) {
                                    toast("اتصال به فروشگاه برقرار نیست. دوباره تلاش کنید.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("ارتقا از طریق فروشگاه" + (BillingManager.premiumPrice?.let { " — $it" } ?: ""))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- خروج / حذف ----------
            Row {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { repo.logout() }
                }) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("خروج از حساب")
                }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { repo.deleteCurrentAccount() }
                    Handler(Looper.getMainLooper()).post {
                        toast("حساب شما حذف شد")
                    }
                }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("حذف حساب", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** نمایش تاریخ میلادی به شمسی برای «عضو از تاریخ ...» */
private fun formatJalaliDate(epochMillis: Long): String {
    val d = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val j = CalendarConverter.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
    return PersianText.toPersianDigits("${j.day} ${PersianText.jalaliMonths[j.month - 1]} ${j.year}")
}
