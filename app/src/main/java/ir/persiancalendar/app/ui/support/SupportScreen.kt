package ir.persiancalendar.app.ui.support

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.support.SupportBox
import ir.persiancalendar.app.data.support.SupportMessage
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.SimpleDropdown

/**
 * پشتیبانی و تماس با مدیر — نسخه ۱.۵
 *
 * کاربر می‌تواند درخواست تبلیغ، گزارش مشکل یا پیشنهادش را برای مدیر برنامه بفرستد:
 *  ۱) ثبت در صندوق پشتیبانی پنل مدیریت (محلی — SupportBox)
 *  ۲) یا «ارسال با ایمیل» (باز شدن برنامه ایمیل با متن آماده)
 *
 * در نسخه بعدی می‌توانید به‌جای ذخیره محلی، پیام را به API سرور خودتان POST کنید.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var typeIdx by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val myMessages = remember { mutableStateListOf<SupportMessage>() }

    fun reload() {
        myMessages.clear()
        myMessages.addAll(SupportBox.load(context))
    }
    LaunchedEffect(Unit) { reload() }

    val typeLabels = listOf("درخواست تبلیغ در برنامه", "گزارش مشکل یا خطا", "پیشنهاد و انتقاد")
    val typeKeys = listOf("ad", "bug", "idea")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
            }
            Text("پشتیبانی و تماس با مدیر", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))

        // ---------- فرم پیام ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("پیام به مدیر برنامه", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "اگر مشکل فنی دارید، پیشنهادی دارید یا می‌خواهید در برنامه تبلیغ کنید، همین‌جا پیام بگذارید؛ پیام شما در صندوق پشتیبانیِ پنل مدیریت ذخیره می‌شود و مدیر پاسخ می‌دهد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                SimpleDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "نوع پیام",
                    items = typeLabels,
                    selectedIndex = typeIdx,
                    onSelected = { typeIdx = it }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("نام شما") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    singleLine = true,
                    label = { Text("راه پاسخ‌گویی (ایمیل یا شماره تماس)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    label = { Text("متن پیام") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val n = name.trim()
                            val c = contact.trim()
                            val t = text.trim()
                            when {
                                n.length < 2 ->
                                    Toast.makeText(context, "نام خود را وارد کنید", Toast.LENGTH_SHORT).show()
                                c.length < 4 ->
                                    Toast.makeText(context, "ایمیل یا شماره تماس برای پاسخ‌گویی لازم است", Toast.LENGTH_SHORT).show()
                                t.length < 10 ->
                                    Toast.makeText(context, "متن پیام باید حداقل ۱۰ حرف باشد", Toast.LENGTH_SHORT).show()
                                else -> {
                                    if (SupportBox.add(context, typeKeys[typeIdx], n, c, t)) {
                                        text = ""
                                        reload()
                                        Toast.makeText(
                                            context,
                                            "پیام شما ثبت شد ✓ — در صندوق پشتیبانی مدیر قرار گرفت",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "ثبت پیام ممکن نشد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("ارسال پیام") }
                    OutlinedButton(
                        onClick = {
                            val subject = "پشتیبانی تقویم فارسی — ${typeLabels[typeIdx]}"
                            val body = "$text\n\nنام: $name\nراه پاسخ‌گویی: $contact"
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse(
                                    "mailto:${SupportBox.ADMIN_EMAIL}?subject=" +
                                        Uri.encode(subject) + "&body=" + Uri.encode(body)
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, "ارسال با ایمیل")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                Toast.makeText(context, "برنامه ایمیل پیدا نشد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("ارسال با ایمیل") }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- پیام‌های ارسال‌شده از این دستگاه ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("پیام‌های ارسال‌شده از این دستگاه", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (myMessages.isEmpty()) {
                    Text(
                        "هنوز پیامی ارسال نکرده‌اید",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (m in myMessages.asReversed()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                m.typeLabel + " — " + m.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                m.at + " · پاسخ از طریق: " + m.contact,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "با «ارسال با ایمیل»، پیام از طریق برنامه ایمیل گوشی به آدرس مدیر (${SupportBox.ADMIN_EMAIL}) ارسال می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.width(1.dp))
    }
}
