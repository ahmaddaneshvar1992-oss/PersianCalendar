package ir.persiancalendar.app.ui.settings

import android.widget.Toast
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
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.support.Announcement
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.data.support.SupportBox
import ir.persiancalendar.app.data.support.SupportMessage
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * پنل مدیریت — نسخه ۱.۱۱.۵
 *
 * صفحه‌ای مخصوص توسعه‌دهنده (با رمز) برای:
 *  - اطلاعیه‌ها: خبر و تبلیغ زنگ کنار منوی سه‌نقطه — نسخه ۱.۶
 *  - صندوق پشتیبانی: پیام‌های کاربران (تبلیغ/مشکل/پیشنهاد) — نسخه ۱.۵
 *  - مشاهده آمار و فهرست کاربران ثبت‌نام‌شده
 *  - تغییر رمز پنل — رمز می‌تواند حروف و اعداد با هم داشته باشد — نسخه ۱.۳
 *
 * نسخه ۱.۱۱.۵: تبلیغات بنری و اینترستیشال به‌طور کامل از برنامه حذف شد؛
 * سکشن‌های AdMob و بنرهای تصویری چرخشی از این پنل برداشته شدند.
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
                        "این پنل برای مدیریت اطلاعیه‌ها و مشاهده کاربران است. رمز پیش‌فرض ۱۲۳۴ است؛ حتماً قبل از انتشار عوضش کنید.",
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
            // ---------- نسخه ۱.۱۱.۵: تبلیغات حذف شد — کارت اطلاع‌رسانی ----------
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Text("تبلیغات حذف شد", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "از نسخه ۱٫۱۱٫۵ بنر تبلیغاتی و اینترستیشال به‌طور کامل از برنامه حذف شده است؛ این پنل برای اطلاعیه‌ها، صندوق پشتیبانی، آمار و کاربران است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        "پیام‌های صندوق پشتیبانی: ${supMsgs.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "اطلاعیه‌های فعال (زنگ): ${anns.count { it.on }} از ${anns.size}",
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

