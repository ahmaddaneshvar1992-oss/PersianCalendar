package ir.persiancalendar.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.City

/** کارت بخش با گوشه‌های گرد مطابق طراحی اپلیکیشن (نسخه ۱.۱۱.۵: نوار تبلیغ پایین صفحه حذف شد) */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        content()
    }
}

/**
 * انتخاب تاریخ شمسی با سه فهرست کشویی (سال/ماه/روز)
 */
@Composable
fun JalaliDatePickerDialog(
    initial: JalaliDate,
    onDismiss: () -> Unit,
    onSelected: (JalaliDate) -> Unit
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.day.coerceAtMost(CalendarConverter.jalaliMonthLength(initial.year, initial.month))) }

    val years = (initial.year - 40)..(initial.year + 40)
    val daysCount = CalendarConverter.jalaliMonthLength(year, month)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSelected(JalaliDate(year, month, day)) }) {
                Text("تأیید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
        title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SimpleDropdown(
                    modifier = Modifier.weight(1.2f),
                    label = "سال",
                    items = years.map { PersianText.toPersianDigits(it.toString()) },
                    selectedIndex = years.indexOf(year).coerceAtLeast(0),
                    onSelected = { idx ->
                        val y = years.toList()[idx]
                        year = y
                        day = day.coerceAtMost(CalendarConverter.jalaliMonthLength(y, month))
                    }
                )
                SimpleDropdown(
                    modifier = Modifier.weight(1.5f).padding(start = 6.dp),
                    label = "ماه",
                    items = PersianText.jalaliMonths.toList(),
                    selectedIndex = month - 1,
                    onSelected = { idx ->
                        month = idx + 1
                        day = day.coerceAtMost(CalendarConverter.jalaliMonthLength(year, month))
                    }
                )
                SimpleDropdown(
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    label = "روز",
                    items = (1..daysCount).map { PersianText.toPersianDigits(it.toString()) },
                    selectedIndex = (day - 1).coerceIn(0, daysCount - 1),
                    onSelected = { idx -> day = idx + 1 }
                )
            }
        }
    )
}

/**
 * فهرست کشویی سبک (DropdownMenu) — بدون وابستگی به ExposedDropdownMenuBox
 */
@Composable
fun SimpleDropdown(
    modifier: Modifier = Modifier,
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.material3.OutlinedButton(
        modifier = modifier,
        onClick = { expanded = true }
    ) {
        Text(
            text = if (items.isNotEmpty() && selectedIndex in items.indices)
                items[selectedIndex] else label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }

    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        items.forEachIndexed { idx, item ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(item, style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    expanded = false
                    onSelected(idx)
                }
            )
        }
    }
}

/**
 * انتخاب شهر با جست‌وجوی زنده — برای فهرست کامل شهرستان‌های ایران (۳۵۰+ شهر).
 */
@Composable
fun CityPickerDialog(
    cities: List<City>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, selectedIndex) {
        val q = query.trim()
        val all = cities.mapIndexed { i, c -> i to c }
        if (q.isEmpty()) all
        else all.filter {
            it.second.name.contains(q) || it.second.province.contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        title = { Text("انتخاب شهر") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("جست‌وجوی نام شهر یا استان…") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    itemsIndexed(filtered) { _, pair ->
                        val (idx, city) = pair
                        TextButton(
                            onClick = {
                                onSelected(idx)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    city.name + (if (idx == selectedIndex) "  ✓" else ""),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (idx == selectedIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (city.province.isNotEmpty()) {
                                    Text(
                                        city.province,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

/** دیالوگ تأیید ساده */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "تأیید",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
        title = { Text(title) },
        text = { Text(message) }
    )
}

/**
 * راه‌انداز انتخاب فایل برای بکاپ (خروجی/ورودی) — در صفحه تنظیمات استفاده می‌شود.
 */
@Composable
fun rememberFileWriters(
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit
): Pair<(String) -> Unit, () -> Unit> {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExport(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImport(uri) }

    return Pair(
        { suggestedName: String -> exportLauncher.launch(suggestedName) },
        { importLauncher.launch(arrayOf("application/json", "text/*")) }
    )
}
